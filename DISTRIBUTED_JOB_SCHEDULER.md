# Distributed Job Scheduler — System Design (SDE 2 Interview Version)

**Goal:** Design a large-scale distributed job scheduler supporting cron-triggered and manual/UI-triggered jobs with fire-and-forget remote execution, real-time status streaming, and exactly-once guarantees at 10M jobs/day scale.

---

## Interview Pacing Guide

| Phase | Time | Section |
|---|---|---|
| Requirements & Scope | 0–5 min | §1 |
| Back-of-Envelope Estimation | 5–10 min | §2 |
| High-Level Architecture | 10–20 min | §3 |
| Deep Dive A — Exactly-Once Execution | 20–28 min | §4.1 |
| Deep Dive B — DAG Dependency Scheduling | 28–36 min | §4.2 |
| Deep Dive C — Failure Recovery | 36–44 min | §4.3 |
| Real-Time Status Streaming | 44–48 min | §5 |
| Tradeoffs & Decisions | 48–55 min | §6 |
| Wrap-up & Follow-ups | 55–60 min | §7 |

---

## §1 — Requirements & Scope (5 min)

### Functional Requirements

1. **Job definition** — users define jobs with: name, handler endpoint (URL), schedule (cron expression or manual), payload, timeout, retry policy, priority, and dependencies.
2. **Cron scheduling** — trigger jobs on a cron expression (e.g., `0 9 * * MON`); support second-level, minute-level, and daily granularities.
3. **Manual / UI trigger** — user can trigger any job immediately from dashboard; supports one-off and re-run of failed executions.
4. **Real-time status streaming** — user watching a job in the UI receives live status updates (QUEUED → RUNNING → SUCCESS/FAILED) via WebSocket/SSE without polling.
5. **Job dependencies (DAG)** — jobs can declare upstream dependencies; a job only runs after all its dependencies succeed.
6. **Retry & backoff** — configurable max retries, retry backoff strategy (fixed/exponential/jitter), and retry deadline.
7. **Cancellation** — user can cancel a queued or running job; cancellation propagates to the executing worker.
8. **Job history & audit** — full history of all executions with timestamps, status, logs, and error messages; paginated query API.
9. **Priority lanes** — jobs have priorities (LOW / NORMAL / HIGH / CRITICAL); high-priority jobs preempt low-priority in the queue.

### Non-Functional Requirements

| Property | Target |
|---|---|
| Availability | 99.99% scheduler uptime (< 1 hr downtime/year) |
| Trigger latency | Cron job fires within ≤ 5s of scheduled time (p99) |
| Manual trigger latency | Job queued within < 500 ms of user click (p99) |
| Exactly-once execution | Each job executes exactly once per trigger (no duplicates) |
| Durability | All job state transitions persisted to ≥ 3 replicas before ACK |
| Throughput | 10M jobs/day = ~115 jobs/sec avg; 1,000 jobs/sec peak |
| Execution SLA | P99 job starts within 2s of being queued |
| Status freshness | UI reflects execution state within 1s of worker update |
| Auditability | Immutable execution event trail per job per run |

### Out of Scope
- Job handler code execution environment (workers call out to user-owned endpoints).
- Billing and quota management per tenant.
- Authentication/AuthZ internals (assumed at API gateway).
- ML workload scheduling (specialized; different resource model).
- Log aggregation and APM for job handlers (treated as external observability).

---

## §2 — Back-of-Envelope Estimation (5 min)

### Traffic

```
Jobs/day (avg):                    10,000,000
Jobs/sec (avg):                    ~115
Jobs/sec (peak, batch wave):       ~1,000

Cron jobs triggered/day:           8,000,000  (80%)
Manually triggered/day:            2,000,000  (20%)

Avg job duration:                  30 seconds
Concurrent running jobs (peak):    1,000 x 30s = ~30,000

Workers in fleet:                  5,000
Jobs per worker per minute:        ~1 (30s avg execution + overhead)

Cron expressions evaluated/sec:    ~50,000 (scanner checks all active crons every sec)
```

### Storage

```
Job definition row:                500 B
Execution record row:              800 B
Event log entry:                   400 B

Executions/day:                    10M
Execution records/day:             10M x 800 B = 8 GB/day
Event log/day:                     10M x 5 events x 400 B = 20 GB/day
Total raw storage/day:             ~28 GB/day
With 3x replication:               ~85 GB/day
90-day hot retention:              ~7.5 TB
1-year archive (compressed):       ~20 TB
```

### Latency Budget (cron trigger to job executing, p99)

```
Cron scanner fires:                 0 ms  (background loop, fires within 5s)
Insert execution record + lock:    30 ms  (JobDB write with row lock)
Publish to Kafka priority topic:   40 ms  (produce + ack)
Worker polls and dequeues:         50 ms  (consumer lag at 1k jobs/sec)
Worker calls handler endpoint:    100 ms  (network to user service)
-------------------------------------------
Total                             ~220 ms  (well within 2s SLA target)
```

---

## §3 — High-Level Architecture (10 min)

### 3.1 Primary Architecture Diagram

```
+--------------------------------------------------------------------------+
|                          CLIENTS & INTERFACES                            |
|   Web Dashboard (React)  |  REST API Clients  |  CI/CD Integrations     |
|   (job management UI)    |  (programmatic)    |  (GitHub Actions, etc.) |
+----------------------------------+---------------------------------------+
                                   | HTTPS / WebSocket (WSS)
                                   v
                      +----------------------------+
                      |  API Gateway               |
                      |  auth, rate limit,         |
                      |  idempotency key check,    |
                      |  WebSocket upgrade routing |
                      +--------------+-------------+
                                     |
         +---------------------------+---------------------------+
         v                           v                          v
+------------------+      +---------------------+   +------------------+
|   Job API        |      |  Scheduler Service  |   |  Status Stream   |
|   (CRUD)         |      |                     |   |  Service         |
|                  |      |  Cron Scanner       |   |                  |
|  define job      |      |  Manual trigger     |   |  WebSocket hub   |
|  update/delete   |      |  DAG resolver       |   |  SSE endpoint    |
|  list/history    |      |  Leader election    |   |  fan-out events  |
|  manual trigger  |      |  Tick loop (1s)     |   |  per-job rooms   |
+--------+---------+      +----------+----------+   +--------+---------+
         |                           |                        |
         v                           v                        |
+------------------+      +---------------------+            |
|   Job DB         |      |  Execution Lock     |            |
|  (PostgreSQL)    |      |  Store (Redis)      |            |
|                  |      |                     |            |
|  job definitions |      |  distributed locks  |            |
|  execution rows  |      |  per execution_id   |            |
|  DAG edges       |      |  fencing tokens     |            |
|  retry state     |      |  leader lease key   |            |
+--------+---------+      +---------------------+            |
         |                                                    |
         +--------------------+-------------------------------+
                              |
                              v
                   +---------------------+
                   |  Job Queue (Kafka)  |
                   |                     |
                   |  jobs.critical      |
                   |  jobs.high          |
                   |  jobs.normal        |
                   |  jobs.low           |
                   |  jobs.dead_letter   |
                   +----------+----------+
                              |
           +------------------+------------------+
           v                  v                  v
  +--------------+   +--------------+   +--------------+
  |   Worker 0   |   |   Worker 1   |   |   Worker N   |
  |              |   |              |   |              |
  |  dequeue     |   |  dequeue     |   |  dequeue     |
  |  heartbeat   |   |  heartbeat   |   |  heartbeat   |
  |  HTTP POST   |   |  HTTP POST   |   |  HTTP POST   |
  |  to handler  |   |  to handler  |   |  to handler  |
  |  report      |   |  report      |   |  report      |
  |  result      |   |  result      |   |  result      |
  +------+-------+   +------+-------+   +------+-------+
         |                  |                  |
         +------------------+------------------+
                            |
                            v
               +-------------------------+
               |  Execution Tracker      |
               |                         |
               |  write result to DB     |
               |  publish status event   |
               |  trigger downstream DAG |
               |  retry if needed        |
               +----------+--------------+
                          |
         +----------------+----------------+
         v                v                v
+----------------+  +-----------+  +------------------+
|  Job DB        |  | Event Bus |  |  Status Stream   |
|  (update       |  | (Kafka)   |  |  Service         |
|   execution    |  |           |  |  (push to UI via |
|   record)      |  | job_events|  |   WebSocket/SSE) |
+----------------+  +-----------+  +------------------+

Downstream consumers:
  Alerting stack  |  Analytics lake  |  Audit log
```

### 3.2 Component Responsibilities

| Component | Responsibility | Type |
|---|---|---|
| `JobAPI` | CRUD for job definitions; manual trigger; history queries | Stateless |
| `SchedulerService` | Cron scanner tick loop; DAG resolver; leader-elected singleton | Stateful (leader) |
| `StatusStreamService` | WebSocket/SSE hub; fan-out status events to connected clients | Stateful (connections) |
| `JobQueue` | Priority-partitioned Kafka topics per priority lane | Kafka cluster |
| `Worker` | Dequeue job, call handler endpoint via HTTP POST, report result | Stateless |
| `ExecutionTracker` | Write execution result, trigger downstream DAG jobs, retry dispatch | Stateless |
| `JobDB` | Source of truth for job definitions, execution records, DAG edges | PostgreSQL |
| `ExecutionLockStore` | Distributed locks + fencing tokens (exactly-once); leader lease | Redis |
| `EventBus` | Publish status change events consumed by StatusStreamService | Kafka |

### 3.3 Core Data Stores

| Store | Tech | Consistency | Purpose |
|---|---|---|---|
| `JobDB` | PostgreSQL | Strong ACID | Job definitions, execution records, DAG edges, retry state |
| `ExecutionLockStore` | Redis | Strong within TTL | Distributed fencing tokens, leader lease |
| `JobQueue` | Kafka (4 priority topics) | At-least-once | Durable job dispatch per priority lane |
| `EventBus` | Kafka | At-least-once | Status change events for UI streaming and audit |
| `HistoryArchive` | S3 + Athena | Eventual | Long-term execution history, logs, analytics |

### 3.4 Two Entry Paths: Cron vs. Manual

```
CRON PATH:
  SchedulerService tick (every 1s)
  -> scan job definitions WHERE next_run_at <= now
  -> for each due job: create execution record + acquire Redis lock
  -> publish to Kafka priority topic
  -> worker dequeues + fires HTTP POST to handler endpoint

MANUAL PATH:
  User clicks "Run Now" in dashboard
  -> JobAPI POST /jobs/{id}/trigger (with Idempotency-Key header)
  -> create execution record + acquire lock (idempotency_key = request_id)
  -> publish to Kafka jobs.high topic (manual triggers = elevated priority)
  -> StatusStreamService sends first event: state=QUEUED
  -> worker picks up -> real-time RUNNING event -> SUCCESS/FAILED event
```

---

## §4 — Deep Dives (24 min total, ~8 min each)

---

### §4.1 — Exactly-Once Execution

#### The Problem
- Cron scanner runs on 3 replicas — without coordination, all 3 trigger the same job at 10:00:00.
- Worker crashes mid-execution; Kafka redelivers the message to a second worker -> duplicate execution.
- User double-clicks "Run Now" -> two HTTP requests -> two executions of the same job.

#### Solution: Leader Election + Fencing Tokens

**Step 1 — Leader Election (for Cron Scanner)**

```
All SchedulerService replicas compete for a Redis key:
  SET scheduler:leader {node_id} NX PX 10000
  (only one wins: NX = only if not exists, PX = 10s TTL)

Winner  -> runs the cron tick loop
Losers  -> watch the key; if it expires, re-contest immediately

Heartbeat: winner refreshes TTL every 3s
Failover:  if winner crashes -> key expires -> new leader in less than 10s
```

**Step 2 — Execution Lock with Fencing Token**

```
Before creating execution record:

  1. Acquire Redis lock:
     SET exec_lock:{job_id}:{scheduled_at} {token} NX PX 60000
     token = UUID (the fencing token)

  2. If acquired:
     - INSERT execution_records(job_id, scheduled_at, state=QUEUED, token=token)
     - PUBLISH to Kafka with token in message header
     - Other replicas: SET returns nil -> skip this tick entirely

  3. Worker dequeues job, receives token in payload
     - Before calling handler: UPDATE execution SET state=RUNNING WHERE token=?
     - After handler returns:  UPDATE execution SET state=SUCCESS WHERE token=?
       (wrong token = stale/zombie worker -> 0 rows updated -> reject)
```

#### Exactly-Once Flow Diagram

```
Cron tick fires: job_id=42, scheduled_at=10:00:00
                |
       +--------+--------+
       |                 |
  Replica A          Replica B
       |                 |
       v                 v
SET exec_lock:42:10:00 (NX PX 60000)
       |                 |
    WINS              LOSES (nil returned)
       |                 |
       v                 v
  INSERT execution    Skip this trigger
  record state=QUEUED  entirely
       |
       v
  Publish to Kafka
  {job_id:42, token:"abc123", scheduled_at:"10:00:00"}
       |
       v
  Worker-7 dequeues:
  UPDATE execution SET state=RUNNING WHERE token="abc123" (1 row)
  -> calls POST https://user-handler/job

  [Scenario A: Worker-7 CRASHES mid-execution]
  Kafka redelivers to Worker-12
  Worker-12: UPDATE SET state=RUNNING WHERE token="abc123" AND state=QUEUED
  -> 0 rows (already RUNNING) -> Worker-12 SKIPS

  [Scenario B: Worker-7 COMPLETES]
  Worker-12 dequeues same message (Kafka at-least-once)
  Worker-12: UPDATE SET state=RUNNING WHERE token="abc123" AND state=QUEUED
  -> 0 rows (already SUCCESS) -> Worker-12 SKIPS
```

#### Idempotency for Manual Triggers

```
POST /jobs/42/trigger
Headers: Idempotency-Key: user-click-uuid-789

JobAPI:
  1. SELECT * FROM execution_records WHERE idempotency_key = "uuid-789"
  2. Found?     -> return existing execution_id (no new record created)
  3. Not found? -> INSERT + proceed normally

UNIQUE constraint on (job_id, idempotency_key) prevents race condition
between two concurrent requests with the same key.
```

#### Guarantee Summary

| Scenario | Protection Mechanism |
|---|---|
| Multiple scheduler replicas | Redis leader election; only leader runs cron tick |
| Same cron fires twice at same second | exec_lock:{job_id}:{scheduled_at} NX lock |
| Worker crash + Kafka re-delivery | Fencing token; stale worker gets 0 rows on UPDATE |
| Double-click manual trigger | Idempotency key UNIQUE constraint at DB level |
| Zombie worker tries to commit | UPDATE WHERE token=? AND state=QUEUED -> 0 rows |

---

### §4.2 — DAG Dependency Scheduling

#### The Problem
- Job C must run only after both Job A and Job B succeed.
- If Job A fails, Job C must NOT run; all downstream jobs must be blocked (not silently skipped).
- At 10M jobs/day, dependency evaluation must be fast and off the critical ingestion path.

#### Data Model

```sql
jobs (
  job_id    UUID PRIMARY KEY,
  name      TEXT,
  cron_expr TEXT,
  allow_concurrent BOOLEAN DEFAULT false
)

-- Directed edges: downstream_job depends on upstream_job
job_dependencies (
  downstream_job_id  UUID REFERENCES jobs(job_id),
  upstream_job_id    UUID REFERENCES jobs(job_id),
  PRIMARY KEY (downstream_job_id, upstream_job_id)
)

-- C depends on A and B: rows (C,A) and (C,B)

execution_records (
  execution_id    UUID PRIMARY KEY,
  job_id          UUID,
  run_id          UUID,     -- groups all executions in one DAG run
  state           TEXT,     -- BLOCKED | QUEUED | RUNNING | SUCCESS | FAILED | SKIPPED
  scheduled_at    TIMESTAMPTZ,
  started_at      TIMESTAMPTZ,
  finished_at     TIMESTAMPTZ,
  retry_count     INT DEFAULT 0,
  fencing_token   UUID,
  idempotency_key TEXT UNIQUE
)

-- Index for fast downstream lookup (used by ExecutionTracker)
CREATE INDEX idx_deps_upstream ON job_dependencies(upstream_job_id);
```

#### DAG Execution Flow

```
DAG definition:
  A -------> C ------> D
  B -------> C
  (C depends on A and B; D depends on C)

Trigger DAG run (cron fires or user clicks "Run DAG"):
  SchedulerService:
  1. Topological sort: [A, B, C, D]
  2. For each node with no upstream deps (A, B): create state=QUEUED -> publish
  3. For nodes with deps (C, D): create state=BLOCKED

Initial execution states:
  A: QUEUED   (no deps -> immediately queued)
  B: QUEUED   (no deps -> immediately queued)
  C: BLOCKED  (waiting for A and B)
  D: BLOCKED  (waiting for C)

A completes (state=SUCCESS):
  ExecutionTracker:
  1. UPDATE execution WHERE execution_id=A -> SUCCESS
  2. SELECT downstream_job_id FROM job_dependencies WHERE upstream_job_id=A -> {C}
  3. Check C's upstreams: A=SUCCESS, B=QUEUED (still running)
     -> C stays BLOCKED

B completes (state=SUCCESS):
  ExecutionTracker:
  1. UPDATE execution WHERE execution_id=B -> SUCCESS
  2. Downstream of B -> {C}
  3. Check C's upstreams:
     A = SUCCESS  (check)
     B = SUCCESS  (check)
     ALL CLEAR -> UNBLOCK C
  4. UPDATE C -> QUEUED; publish C to Kafka queue
  5. C runs -> SUCCESS -> unblock D -> D runs

Visual state transitions:
  t=0:   A=QUEUED  B=QUEUED  C=BLOCKED  D=BLOCKED
  t=10:  A=RUNNING B=RUNNING C=BLOCKED  D=BLOCKED
  t=15:  A=SUCCESS B=RUNNING C=BLOCKED  D=BLOCKED
  t=20:  A=SUCCESS B=SUCCESS C=QUEUED   D=BLOCKED
  t=22:  A=SUCCESS B=SUCCESS C=RUNNING  D=BLOCKED
  t=30:  A=SUCCESS B=SUCCESS C=SUCCESS  D=QUEUED
  t=32:  A=SUCCESS B=SUCCESS C=SUCCESS  D=RUNNING
  t=40:  A=SUCCESS B=SUCCESS C=SUCCESS  D=SUCCESS
```

#### Failure Cascade

```
If A FAILS:
  ExecutionTracker:
  1. UPDATE A -> FAILED
  2. DFS from A: find all reachable downstream nodes -> {C, D}
  3. UPDATE all reachable nodes -> SKIPPED (not re-queued)
  4. Emit SKIPPED events -> UI shows grey skipped chain
  5. DAG run marked FAILED, user notified

State after A failure:
  A=FAILED  B=SUCCESS  C=SKIPPED  D=SKIPPED

Partial DAG retry (user re-runs from A):
  1. Reset A -> QUEUED
  2. Reset C, D -> BLOCKED
  3. B stays SUCCESS (no need to re-run)
  -> DAG continues from the failed node only
```

#### Cycle Detection (at definition time)

```
User adds dependency edge: X depends on Y

JobAPI:
  DFS from Y following existing edges
  If X is visited during DFS -> CYCLE DETECTED -> 400 BAD REQUEST
  Else -> insert edge (X, Y)

This guarantees no cycles exist in the graph at trigger time.
```

---

### §4.3 — Failure Recovery

#### Failure Taxonomy

| Failure Type | Detection Method | Recovery Action |
|---|---|---|
| Worker crashes mid-execution | Heartbeat timeout (60s) | Re-deliver from Kafka; fencing token prevents duplicate |
| Scheduler leader crashes | Redis lease expires (<10s) | New leader elected; re-scans missed cron ticks |
| Handler returns 5xx | HTTP response code | Retry with exponential + jitter backoff; up to max_retries |
| Handler unreachable (timeout) | TCP connect timeout | Same retry path as 5xx |
| Zombie job (no heartbeat, no result) | Zombie detector (every 30s) | Force FAILED, release lock, re-queue or dead-letter |
| Kafka partition leader fails | Kafka ISR failover | Consumer reconnects; at-least-once; fencing handles dup |
| DB temporarily unavailable | Circuit breaker | Buffer new jobs in Redis; replay into DB on recovery |

#### Worker Heartbeat + Zombie Detection

```
Worker lifecycle:
  1. Dequeue job from Kafka
  2. UPDATE execution SET state=RUNNING, worker_id=?, started_at=now
     WHERE execution_id=? AND fencing_token=?
  3. Start heartbeat goroutine (every 15s):
     UPDATE execution SET last_heartbeat=now WHERE execution_id=?
  4. Call handler: POST {handler_url} with job payload + timeout
  5. On result: UPDATE execution SET state=SUCCESS/FAILED, finished_at=now
  6. Commit Kafka offset; stop heartbeat

Zombie Detector (background service, runs every 30s):
  SELECT execution_id FROM execution_records
    WHERE state = 'RUNNING'
    AND last_heartbeat < now - interval '60 seconds'

  For each zombie execution:
    1. Force state -> FAILED (reason: ZOMBIE_TIMEOUT)
    2. DEL exec_lock:{job_id}:{execution_id} from Redis (release fence)
    3. Increment retry_count
    4. If retry_count < max_retries:
         Re-publish to Kafka with fresh fencing_token
       Else:
         UPDATE execution -> DEAD_LETTER
         Publish to jobs.dead_letter topic
         Emit alert to ops team
```

#### Retry Backoff Strategies

```
FIXED:        wait_sec = retry_interval  (e.g., always 30s)
EXPONENTIAL:  wait_sec = base * 2^retry  (e.g., 30s, 60s, 120s, 240s)
JITTER:       wait_sec = exponential + random(0, base)  <- recommended
              (prevents thundering herd after mass failure)

Default recommended policy:
  max_retries:      3
  strategy:         JITTER
  base_sec:         30
  max_wait_sec:     300  (5 min cap regardless of formula)
  retry_deadline:   24h  (abandon if job was created > 24h ago)
```

#### Missed Cron Tick Recovery (Scheduler Failover)

```
Normal flow:   Leader fires tick at t=10:00:00 -> job A scheduled
Leader crashes at t=10:00:05 (5s into the minute)

New leader elected at t=10:00:10

New leader startup scan:
  SELECT j.job_id, j.next_run_at
    FROM jobs j
    WHERE j.next_run_at BETWEEN (now - interval '30 seconds') AND now
      AND NOT EXISTS (
        SELECT 1 FROM execution_records e
          WHERE e.job_id = j.job_id
            AND e.scheduled_at = j.next_run_at
      )

  -> finds job A: next_run_at=10:00:00, no execution record exists
  -> fires IMMEDIATELY with scheduled_at = 10:00:00 (original missed time)
  -> updates next_run_at to 10:01:00 for next cycle

Result: job fires ~10s late instead of being silently missed.
```

#### Dead-Letter Queue Handling

```
jobs.dead_letter Kafka topic:
  Consumed by DeadLetterProcessor service:
  - Writes to dead_letter_jobs table with full retry history
  - Emits alert to PagerDuty / Slack
  - UI shows job in DEAD state with audit trail

Ops actions from dashboard:
  - Force re-trigger (with adjusted payload if needed)
  - Mark as permanently skipped (acknowledge dead)
  - Archive to history (no retry)
```

---

## §5 — Real-Time Job Status Streaming (4 min)

### Why WebSocket / SSE over Polling

```
Polling (every 1s) from 10k active dashboard users:
  -> 10,000 requests/sec to JobAPI for status checks
  -> Unnecessary load; stale data between polls

WebSocket / SSE approach:
  -> Server pushes ONLY when state changes
  -> Typical job: 3-4 transitions (QUEUED -> RUNNING -> SUCCESS)
  -> 3-4 pushes per execution regardless of duration
  -> 10k users x 4 events = 40k pushes total per execution batch (much lower)
```

### Architecture

```
User clicks "Run Now" on job-42
         |
         v
  JobAPI: POST /jobs/42/trigger
  returns: { execution_id: "exec-999", state: "QUEUED" }
         |
         v
  UI immediately opens WebSocket:
  WSS /stream/executions/exec-999
         |
  StatusStreamService:
  - adds client conn to in-memory room "exec-999"
  - subscribes to Kafka consumer filtering on execution_id=exec-999
         |
  [Worker picks up job]
  ExecutionTracker publishes event to Kafka job_events:
  { execution_id: "exec-999", state: "RUNNING", worker_id: "worker-17" }
         |
  StatusStreamService Kafka consumer receives event:
  - looks up room "exec-999" -> finds client connection
  - pushes JSON over WebSocket to browser
         |
  [Handler returns 200]
  ExecutionTracker publishes:
  { execution_id: "exec-999", state: "SUCCESS", finished_at: "..." }
         |
  StatusStreamService pushes final event to client
  Client closes WebSocket (or server sends close frame)
```

### WebSocket Event Payload

```json
{
  "execution_id": "exec-999",
  "job_id": "job-42",
  "job_name": "nightly-report",
  "state": "RUNNING",
  "worker_id": "worker-17",
  "started_at": "2026-05-19T10:00:02Z",
  "updated_at": "2026-05-19T10:00:03Z",
  "retry_count": 0,
  "message": "Job picked up by worker-17, calling handler"
}
```

### State Sequence (every message the UI receives)

```
1. state=QUEUED   (immediately on trigger)
2. state=RUNNING  (worker dequeues and starts)
3. state=SUCCESS  (handler returns 200)
   OR
   state=FAILED   (handler returns 5xx or timeout)
   state=RETRYING (if retry_count < max_retries)
   state=DEAD     (exhausted retries)
```

### Scaling StatusStreamService

```
Challenge: WebSocket connections are stateful (held per pod)

Solution:
  - ALB sticky sessions at connection level -> same pod handles WS lifecycle
  - Kafka consumer per pod -> filters job_events by execution_id
  - If pod crashes -> client reconnects to any pod
  - New pod: sends current state snapshot from JobDB as first message
    (client never misses state even after reconnect)

Scale:
  - 10k concurrent WS connections per pod (Node.js or Go)
  - 5 pods handle 50k concurrent users comfortably
  - Add pods behind ALB as user count grows
```

---

## §6 — Tradeoffs & Decisions (7 min)

| Decision | Why | Tradeoff |
|---|---|---|
| **Leader election for cron scanner** | Simplest way to prevent duplicate triggers across replicas | Single active scanner is a bottleneck; shard by `job_id % N` when > 10M jobs/day |
| **Fencing tokens for exactly-once** | Prevents stale zombie workers from committing results | Extra Redis round-trip per execution; worth it for correctness guarantee |
| **Kafka 4 priority topics** | Prevents low-priority jobs starving critical ones | More consumer groups and partition management overhead |
| **PostgreSQL for JobDB** | Strong ACID for execution state machine and DAG edges | Not horizontally writable; shard by `tenant_id` if multi-tenant at scale |
| **Redis for execution locks** | Sub-millisecond lock vs. DB advisory locks; simpler TTL management | Redis failure = lock store unavailable; use Redis Sentinel / Cluster for HA |
| **Pull workers (HTTP fire-and-forget)** | Workers own their consumption rate; handler endpoints are user-controlled | Handler endpoints must be reachable; need retry for transient network errors |
| **WebSocket per execution** | Real-time UX without polling overhead | Stateful connections on server; requires sticky sessions or reconnect snapshot logic |
| **Zombie detector as separate service** | Decoupled from hot path; runs every 30s on a schedule | Up to 90s before a stuck job is detected and re-queued |

---

## §7 — Wrap-up & Common Follow-ups (5 min)

**Q: How does the cron scanner avoid missing ticks when the leader restarts?**
- On startup: scan for jobs with `next_run_at` in last 30s and no matching execution record.
- Fire those immediately with `scheduled_at = original_missed_time`.
- Leader failover takes < 10s; at most one tick window is at risk.

**Q: How do you scale to 100M jobs/day (10x current)?**
- Shard the cron scanner: `job_id % N` partitioned across N scanner leaders (one Redis lock per shard).
- Scale Kafka partitions: 32 -> 256; add worker consumer groups proportionally.
- Workers are stateless -> scale horizontally: 5k -> 50k workers with no design change.
- Shard JobDB by `tenant_id` across multiple PostgreSQL instances.

**Q: What happens if a handler endpoint is down for 1 hour?**
- Worker gets 5xx or TCP timeout -> execution marked FAILED.
- Retry with exponential + jitter backoff up to max_retries (default 3).
- Exhausted retries -> DEAD_LETTER -> ops alert.
- Subsequent cron triggers fire independently; each is its own execution attempt.

**Q: How do you handle a DAG with 1,000 nodes without blocking the scheduler?**
- On each node completion: only look up DIRECT downstream dependents via `job_dependencies` index.
- `CREATE INDEX idx_deps_upstream ON job_dependencies(upstream_job_id)` -> O(1) lookup.
- Each unblock decision is made by ExecutionTracker independently; no recursive locking.

**Q: Can two manually triggered runs of the same job run concurrently?**
- Controlled by `allow_concurrent` flag on the job definition.
- If `false`: check for QUEUED or RUNNING execution before accepting; return `409 CONFLICT`.
- If `true`: each trigger gets its own `execution_id` and runs fully independently.

**Q: How do you handle very long-running jobs (hours)?**
- Worker heartbeat TTL extended: `last_heartbeat` checked against job-level `heartbeat_timeout_min`.
- Zombie detector uses per-job timeout, not a global 60s threshold.
- Very long jobs (> 1h) should checkpoint their progress; handler responsibility, not scheduler's.

---

## 10-Minute Summary (Under Time Pressure)

**"I'd design the distributed job scheduler with 5 layers:"**

1. **Trigger (Cron + Manual)**
   - Cron: leader-elected SchedulerService scans every 1s for due jobs.
   - Manual: JobAPI immediately creates execution + publishes to high-priority queue.
   - Both paths: idempotency keys + Redis fencing tokens for exactly-once.

2. **Queue (Kafka, 4 priority lanes)**
   - `jobs.critical`, `jobs.high`, `jobs.normal`, `jobs.low` topics.
   - Workers consume highest-priority first.
   - 1-day retention allows re-delivery after worker failure.

3. **Execution (Stateless Workers)**
   - Pull job, acquire fencing token, fire HTTP POST to handler.
   - Heartbeat every 15s; zombie detector force-fails after 60s silence.
   - Report result to ExecutionTracker.

4. **Result + DAG Unblocking (ExecutionTracker)**
   - Write result to JobDB.
   - Look up direct downstream dependents; check if all upstreams SUCCESS.
   - If yes: unblock and publish to queue. If upstream FAILED: cascade SKIPPED.

5. **Status Streaming (Real-Time UI)**
   - Every state change published to Kafka `job_events`.
   - StatusStreamService fans events out to connected WebSocket/SSE clients.
   - Reconnecting clients get current state snapshot from JobDB as first message.

**Key tradeoffs to mention:**
- Leader election vs. sharded scanning: leader is simpler; shard when > 10M jobs/day.
- Fencing tokens: one extra Redis round-trip per execution; worth it for exactly-once.
- Zombie detector delay: up to 90s; acceptable for batch workloads; tune per job for real-time.
- DAG fan-out: evaluate only direct descendants on each completion for O(1) local decisions.

---

## Quick Reference: Key API Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/jobs` | POST | Create job definition |
| `/jobs/{id}` | PUT | Update job definition |
| `/jobs/{id}` | DELETE | Delete job and cancel future executions |
| `/jobs/{id}/trigger` | POST | Manually trigger immediate execution (idempotency-key required) |
| `/jobs/{id}/executions` | GET | Paginated execution history |
| `/executions/{id}` | GET | Snapshot status (polling fallback) |
| `/executions/{id}/cancel` | POST | Cancel queued or running execution |
| `/stream/executions/{id}` | WSS | Real-time status stream over WebSocket |
| `/jobs/{id}/dependencies` | POST | Add upstream DAG dependency edge |
| `/dead-letter` | GET | List dead-letter executions for ops review |
| `/dead-letter/{id}/retry` | POST | Force re-trigger from dead-letter queue |

---

## Quick Reference: Data Stores

| Store | Tech | Purpose |
|---|---|---|
| `JobDB` | PostgreSQL | Job definitions, execution records, DAG edges, retry state |
| `ExecutionLockStore` | Redis (Sentinel) | Fencing tokens, leader lease, idempotency key cache |
| `PriorityQueues` | Kafka (4 topics) | Durable job dispatch per priority lane |
| `EventBus` | Kafka | Status change events for UI streaming and audit |
| `HistoryArchive` | S3 + Athena | Long-term execution history, analytics, compliance |

---

## References & Inspiration
- **Quartz Scheduler**: Clustered cron with JDBC-backed job store
- **AWS EventBridge Scheduler**: Cron + one-time triggers at cloud scale
- **Apache Airflow**: DAG-based dependency scheduling, task state machines
- **Google Cloud Scheduler**: Exactly-once guarantees with HTTP targets
- **Fencing tokens**: Martin Kleppmann — *Designing Data-Intensive Applications*, Ch. 8

