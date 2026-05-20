# Amazon Fulfillment Center — System Design (SDE 2 Interview Version)

**Goal:** Design an Amazon-scale warehouse execution platform for a single fulfillment center (human + robots) handling inbound receiving, inventory, order allocation, pick-pack-ship, and exception recovery. Ends with Day 2 multi-FC extension.

---

## Interview Pacing Guide

| Phase | Time | Section |
|---|---|---|
| Requirements & Scope | 0–5 min | §1 |
| Back-of-Envelope Estimation | 5–10 min | §2 |
| High-Level Architecture | 10–20 min | §3 |
| Deep Dives (3 topics) | 20–45 min | §4.1–4.3 |
| Delivery Objective Modes | 45–53 min | §5 |
| Tradeoff Summary | 53–57 min | §6 |
| Wrap-up + Day 2 Multi-FC | 57–60 min | §7 |

---

## §1 — Requirements & Scope (5 min)

### Functional Requirements
1. **Inbound receiving** — scan pallets/cartons, reconcile vs. ASN, mark units received, generate putaway tasks.
2. **Inventory management** — track per-SKU qty by bin/location; holds, reservations, commits, releases, no oversell.
3. **Order allocation & reservation** — idempotently reserve inventory per order line; partial allocation and backorder support.
4. **Task orchestration** — create and assign pick/stow/replenishment tasks to humans and AMR robots; rebalance by zone, SLA, capacity.
5. **Pick, pack, ship** — pick confirmation, cartonization, carrier label print, manifest submit, order → SHIPPED.
6. **Exception handling** — missing items, scanner mismatches, robot faults, station downtime, carrier cutoff misses.
7. **Returns processing** — receive, inspect, route to restock/scrap/refurbish; inventory adjustment + reconciliation events.

### Non-Functional Requirements
| Property | Target |
|---|---|
| Availability | 99.95% FC control-plane services |
| Task assignment latency | < 300 ms p99 |
| Scanner event ingestion | < 150 ms p99 end-to-end |
| Reservation consistency | No oversell — strict per-SKU-location |
| Durability | All state transitions to ≥ 3 replicas before ACK |
| Auditability | Immutable event trail per unit, order, and task |
| Peak throughput | Handle 3× average load (peak season) |
| Recovery | RPO < 1 min, RTO < 15 min for FC services |

### Out of Scope
- Customer checkout and order placement UI.
- Carrier last-mile routing internals.
- ML demand forecasting model training.
- ERP/accounting internals (integration events only).
- Authentication/AuthZ internals (assumed at API gateway).

---

## §2 — Back-of-Envelope Estimation (5 min)

### Single FC Scale Assumptions
```
FC size:                      1M sq ft (large FC, ~200 pick zones)
Active SKUs in FC:            1,000,000
Total units in FC:            50,000,000

Orders/day (avg):             500,000
Orders/sec (avg):             ~5.8
Orders/sec (peak, Prime Day): ~60

Order lines per order:        3 (avg)
Order lines/day:              1,500,000

Human workers on shift:       3,000
AMR robots in fleet:          1,000
Pick stations:                500
Packing stations:             200
```

### Event Throughput
```
Scanner events/day:              120,000,000
Robot telemetry + task events:    80,000,000
Workstation events/day:           30,000,000
Total operational events/day:    ~230,000,000

Peak scanner events/sec:          ~2,000
Peak orchestration writes/sec:    ~5,000
Total peak writes/sec:            ~10,000 (incl. idempotency + retries)
```

### Storage Estimates
```
Inventory row (current state):   300 B
Task row:                        500 B
Order line state:                400 B
Event payload (avg):             1 KB

Event log raw/day:               230M × 1 KB = ~230 GB/day
With replication overhead:       ~700 GB/day
90-day hot retention:            ~63 TB
1-year archive (compressed):     ~120 TB
```

### Latency Budget (single pick task assignment, p99)
```
API Gateway + auth validation    20 ms
Order / task validation          30 ms
Inventory reservation check      70 ms   (SELECT FOR UPDATE on InventoryDB)
Task queue publish + ack         80 ms   (Kafka produce + consumer ack)
Response serialization + send    20 ms
─────────────────────────────────────
Total                           ~220 ms  (within 300 ms target)
```

---

## §3 — High-Level Architecture (10 min)

### 3.1 Primary Architecture Diagram (Whiteboard Diagram #1)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           CLIENTS & DEVICES                              │
│   RF Scanners (workers)  │  Robot Fleet Controller  │  Pack Stations     │
│   Ops Dashboard          │  Returns Desks           │  Label Printers    │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ HTTPS / gRPC / MQTT
                                     v
                        ┌────────────────────────┐
                        │  API Gateway           │
                        │  auth, rate limit,     │
                        │  idempotency key check │
                        └───────────┬────────────┘
                                    │
        ┌───────────────────────────┼──────────────────────────┐
        v                           v                          v
┌───────────────────┐    ┌───────────────────────┐   ┌──────────────────┐
│ Order             │    │ Warehouse Exec Svc    │   │ Shipping         │
│ Orchestrator      │    │  (WES)                │   │ Allocator        │
│                   │    │                       │   │                  │
│ intake → reserve  │    │ task lifecycle        │   │ cartonization    │
│ split/merge/hold  │    │ pick/stow/pack        │   │ carrier select   │
│ SLA tracking      │    │ task assignment       │   │ label + manifest │
│ cancel/backorder  │    │ zone management       │   │ cutoff timers    │
└────────┬──────────┘    └──────────┬────────────┘   └────────┬─────────┘
         │                          │                          │
         v                          v                          v
┌───────────────────┐    ┌───────────────────────┐   ┌──────────────────┐
│ Inventory         │    │ Task Allocator        │   │ Label / Doc Svc  │
│ Service           │    │                       │   │                  │
│                   │    │ score + priority      │   │ label print      │
│ qty balance       │    │ human dispatch        │   │ ASN docs         │
│ holds/commits     │    │ robot dispatch        │   │ print queue      │
│ no-oversell       │    │ wave planning         │   │                  │
│ location map      │    │ reassignment          │   │                  │
└────────┬──────────┘    └──────────┬────────────┘   └──────────────────┘
         │                          │
         └──────────────┬───────────┘
                        v
               ┌────────────────────┐          ┌──────────────────┐
               │  Event Bus         │          │ Workflow Engine  │
               │  (Kafka)           │          │ (Saga orchestr.) │
               │                    │          │                  │
               │  order_events      │          │ compensation     │
               │  inventory_events  │          │ timeout/retry    │
               │  task_events       │          │ SLA watch        │
               │  scan_events       │          │ escalation       │
               └─────────┬──────────┘          └──────────┬───────┘
                         │                                 │
     ┌───────────────────┼─────────────────────────────────┤
     v                   v                   v             v
┌──────────┐  ┌──────────────────┐  ┌──────────────┐ ┌──────────────┐
│ OrderDB  │  │ InventoryDB      │  │ TaskStateDB  │ │ Event Store  │
│          │  │                  │  │              │ │              │
│ Postgres │  │ Postgres (part.) │  │ Redis + SQL  │ │ Kafka + S3   │
│ strong   │  │ strong writes    │  │ hot queues   │ │ immutable    │
│ ACID tx  │  │ row-level locks  │  │ + durable    │ │ audit log    │
└──────────┘  └──────────────────┘  └──────────────┘ └──────────────┘

Downstream:
  ERP / Finance  │  Carrier APIs (UPS/FedEx/AMZL)  │  Observability Stack
```

### 3.2 Component Responsibilities

| Component | Responsibility | Type |
|---|---|---|
| `OrderOrchestrator` | Intake orders, split by zone, manage order state machine | Stateless |
| `InventoryService` | Per-SKU-location quantity, holds, commits, releases—no oversell | DB-backed |
| `WarehouseExecSvc` | Create and track all warehouse tasks (pick/stow/pack/replenish) | Stateless |
| `TaskAllocator` | Score and assign tasks to workers and robots | Stateless |
| `ShippingAllocator` | Cartonization, carrier selection, cutoff management, manifest | Stateless |
| `WorkflowEngine` | Saga orchestration, compensation, retry, SLA escalation | DB-backed |
| `EventBus` | Decoupled topic-based command/event propagation | Kafka cluster |

### 3.3 Core Data Stores

| Store | Tech | Consistency | Purpose |
|---|---|---|---|
| `OrderDB` | PostgreSQL | Strong (ACID) | Order/shipment/line state with strict transitions |
| `InventoryDB` | PostgreSQL (partitioned by `location_id`) | Strong (row locks) | Balance, reservation ledger, location state |
| `TaskStateDB` | Redis (hot) + PostgreSQL (durable) | Eventual → strong on commit | Hot task queues + persistent task history |
| `LocationIndex` | DynamoDB | Eventual read | Fast bin lookup for eligible pick/stow locations |
| `EventStore` | Kafka + S3 | Append-only | Immutable operational and audit event stream |
| `AnalyticsLake` | S3 + Athena | Batch refresh | Cost, productivity, SLA analytics |

### 3.4 End-to-End Happy Path (running example used throughout)

```
Order arrives: 3 lines (book, charger, headphones)

1 → OrderOrchestrator:  validate, create PENDING order + 3 lines
2 → InventoryService:   HELD reservations per line × per bin location
3 → WarehouseExecSvc:   3 pick tasks created (one per zone)
4 → TaskAllocator:      worker-7 (book), robot-12 (charger), worker-21 (headphones)
5 → Picks confirmed:    scan events ingested → lines → PICKED
6 → Pack station:       all 3 items arrive → ShippingAllocator fits into 1 carton
7 → LabelSvc:           label printed → carrier manifest submitted
8 → Order → SHIPPED:    event emitted to OMS, ERP, customer promise service
```

---

## §4 — Deep Dives (25 min total, ~8 min each)

---

### §4.1 — Reservation Consistency: No Oversell (Deep Dive Diagram #1)

#### The Problem
- 100 concurrent orders race for the last unit of a high-demand SKU at the same bin.
- Scanner retries (intermittent Wi-Fi) re-submit the same confirmation → risk double-commit.
- Partial cancellation must cleanly release the exact held quantity and nothing more.

#### Data Model

```sql
-- Current balance per SKU × location
inventory_balance (
  sku_id        UUID,
  location_id   UUID,
  on_hand_qty   INT   NOT NULL DEFAULT 0,
  reserved_qty  INT   NOT NULL DEFAULT 0,
  available_qty INT   GENERATED AS (on_hand_qty - reserved_qty),
  version       BIGINT NOT NULL DEFAULT 0,    -- optimistic lock
  updated_at    TIMESTAMPTZ,
  PRIMARY KEY (sku_id, location_id),
  CHECK (reserved_qty <= on_hand_qty),        -- DB-enforced invariant
  CHECK (on_hand_qty  >= 0)
)

-- One row per reservation attempt
reservation_ledger (
  reservation_id  UUID PRIMARY KEY,
  order_id        UUID NOT NULL,
  order_line_id   UUID NOT NULL,
  sku_id          UUID NOT NULL,
  location_id     UUID NOT NULL,
  qty             INT  NOT NULL,
  state           TEXT CHECK (state IN ('HELD','COMMITTED','RELEASED','EXPIRED')),
  idempotency_key TEXT UNIQUE NOT NULL,       -- prevents duplicate reservations
  created_at      TIMESTAMPTZ,
  updated_at      TIMESTAMPTZ
)
```

#### Consistency Flow (Diagram #1)

```
Client: Reserve(order_line_id, sku, qty, idempotency_key)
              │
              v
    ┌──────────────────────────────────┐
    │ Check idempotency_key in ledger  │── exists? ──► return cached result (no-op)
    └─────────────┬────────────────────┘
                  │ new key
                  v
    ┌──────────────────────────────────────────────────────┐
    │  BEGIN TRANSACTION                                   │
    │                                                      │
    │  SELECT * FROM inventory_balance                     │
    │    WHERE sku_id = ? AND location_id = ?              │
    │    FOR UPDATE;              ← row-level lock         │
    │                                                      │
    │  IF available_qty >= qty:                            │
    │    UPDATE inventory_balance                          │
    │      SET reserved_qty = reserved_qty + qty,          │
    │          version      = version + 1;                 │
    │    INSERT INTO reservation_ledger (...) state=HELD;  │
    │    COMMIT ──► return reservation_id                  │
    │                                                      │
    │  ELSE:                                               │
    │    ROLLBACK ──► return INSUFFICIENT_INVENTORY        │
    └──────────────────────────────────────────────────────┘
```

#### Key Invariants
- `available_qty = on_hand_qty - reserved_qty` — DB CHECK constraint enforces at write time.
- `reserved_qty` can never exceed `on_hand_qty` — even with concurrent writers.
- Idempotency key has a `UNIQUE` constraint — duplicate retries return cached result, not a new row.

#### Failure Scenarios

| Scenario | Handling |
|---|---|
| Scanner re-sends pick confirmation | `idempotency_key` check → return prior result, no double-commit |
| Pick times out (worker distracted) | Reservation TTL expires → `state=EXPIRED` → `reserved_qty` released |
| Order cancelled mid-pick | Compensation saga → `UPDATE reservation SET state=RELEASED` + restore qty |
| Crash mid-transaction | DB rollback on reconnect; client retries with same key → idempotent |

---

### §4.2 — Real-Time Task Scheduling: Human + Robot (Deep Dive Diagram #2)

#### The Problem
- 3,000 workers and 1,000 robots compete for ~10,000 active tasks at any moment.
- Tasks have different SLA urgencies, zone constraints, and skill requirements.
- Robots have battery levels, path conflicts, and charging schedules to respect.
- Human workers have zone certifications, ergonomic limits, and shift schedules.

#### Task Scheduling Flow (Diagram #2)

```
                  ┌─────────────────────────────────────┐
                  │         Task Allocator              │
                  │                                     │
                  │  1. Score each (task × worker/robot)│
                  │  2. Assign greedily by max score    │
                  │  3. Re-score and rebalance every 30s│
                  └──────────────┬──────────────────────┘
                                 │
             ┌───────────────────┼──────────────────────┐
             v                   v                      v
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │  High SLA Queue  │  │  Standard Queue  │  │  Replenish Queue │
   │  (same-day cut)  │  │  (next-day+)     │  │  (restock bins)  │
   │  priority = 0.9  │  │  priority = 0.5  │  │  priority = 0.3  │
   └────────┬─────────┘  └────────┬─────────┘  └─────────┬────────┘
            │                     │                       │
       ┌────┴────────┐       ┌────┴────────┐        ┌────┴────────┐
       v             v       v             v        v             v
  ┌─────────┐  ┌──────────┐ ...          ...  ┌──────────┐ ┌──────────┐
  │ Worker  │  │  Robot   │                   │  Worker  │ │  Robot   │
  │Dispatch │  │Dispatch  │                   │Dispatch  │ │Dispatch  │
  │         │  │          │                   │          │ │          │
  │ skill + │  │ battery+ │                   │ zone     │ │ path     │
  │ zone chk│  │ path chk │                   │ cert chk │ │ conflict │
  └─────────┘  └──────────┘                   └──────────┘ └──────────┘
       │              │
       v              v
  Handheld App   AMR Controller
  (task UI)      (mission queue)
```

#### Priority Scoring Formula

```
task_score =
    0.45 × sla_urgency_score         (time_to_carrier_cutoff, normalized 0→1)
  + 0.20 × pick_batch_efficiency     (co-location with other pending tasks)
  + 0.15 × (1 - zone_congestion)     (penalty for crowded zones)
  + 0.10 × robot_battery_suitability (1.0 if battery > 40%, prefer human < 20%)
  + 0.10 × packing_station_readiness (is downstream station waiting?)
```

#### Task State Machine

```
CREATED → QUEUED → ASSIGNED → IN_PROGRESS → COMPLETED
                      │              │
                      │ timeout       │ fault
                      v              v
                   REQUEUED      EXCEPTION ──► ExceptionRouter
```

#### Practical Controls

| Control | Purpose |
|---|---|
| **20-minute wave batches** | Reduces per-pick travel distance ~25% via zone consolidation |
| **Zone WIP cap (50 tasks/zone)** | Prevents worker/robot congestion in same aisle |
| **Robot overflow fallback** | If robot utilization > 90%, assign to nearest available human |
| **Charging schedule enforcement** | Reserve 20% of robot fleet for charging windows at all times |
| **Reassignment on timeout** | Task unconfirmed after 10 min → auto-requeue to next eligible worker |

---

### §4.3 — Exception Recovery & Compensation

#### The Problem
- At scale, ~0.5% of picks hit an exception → 250,000 exceptions/day at 50M units/day.
- Silent failures are the worst: task marked complete but item not moved.
- Every recovery action must be fully auditable and SLA-safe.

#### Exception Recovery Flow

```
Scan or Confirm Event arrives
              │
              v
   ┌──────────────────────────────────┐
   │  Exception Router                │
   │  classify by exception_code      │
   └───┬──────┬──────┬────────────────┘
       │      │      │           │
       v      v      v           v
  PICK_NOT  BARCODE ROBOT_PATH CARRIER_
  _FOUND    _MISMATCH _BLOCKED  CUTOFF
            │                    │
       Manual verify    Rebook next
       (rescan task)    carrier window
            │
            v
      Create cycle-count
      task for that bin
            │
            └─────────┬──────────┘
                      v
         ┌─────────────────────────────────────┐
         │   Compensation Saga (Workflow Eng.) │
         │                                     │
         │   1. Release / rebook reservation   │
         │   2. Create alternate pick task     │
         │   3. Update promise date if needed  │
         │   4. Emit event to OMS              │
         └──────────────┬──────────────────────┘
                        │
                        v
         Notify promise service + ops dashboard
         SLA breach counter incremented
```

#### Exception Lookup Table

| Code | Cause | Auto-Recovery | Escalation Trigger |
|---|---|---|---|
| `PICK_NOT_FOUND` | Item absent from bin | Spawn cycle-count task | 2+ consecutive misses for same bin |
| `BARCODE_MISMATCH` | Wrong item picked | Route to manual verify queue | Mismatch rate > 1% on a shift |
| `ROBOT_PATH_BLOCKED` | Aisle obstruction | Reroute robot or assign human | > 5 blocked robots in one zone |
| `PACK_STATION_DOWN` | Conveyor/scanner fault | Reroute to adjacent station | All stations in zone down |
| `CARRIER_CUTOFF_MISSED` | Missed carrier pickup | Book next carrier window | > 3% same-day orders affected |
| `DAMAGED_ITEM` | Physical damage at pick | Route to damage inspection | Alert quality team immediately |

#### Audit Trail (immutable)
- Every exception, recovery action, and saga step emitted as an event to `EventStore`.
- Events used for: postmortem analysis, worker QA review, process improvement scoring.

---

## §5 — Delivery Objective Modes (8 min)

### 5.1 Mode A — Fastest-Ship Promise Accuracy

**Primary KPI:** % orders delivered on or before customer-shown ETA.

**Architecture changes:**
- Smaller wave sizes (10 min) → lower queueing delay.
- Premium carrier slots pre-booked based on demand forecast.
- High slack at packing stations to absorb peaks without queuing.
- SLA alarm at –45 min to carrier cutoff → auto-escalation.

```
task_score weight shift (Mode A):
  sla_urgency_score       → 0.65  (was 0.45)
  pick_batch_efficiency   → 0.10  (was 0.20)
  zone_congestion         → 0.10
  robot_battery           → 0.05
  station_readiness       → 0.10
```

| Pros | Cons |
|---|---|
| Highest customer satisfaction | Lower labor efficiency (more idle slack) |
| Fewer promise-break penalties | Higher shipping cost (premium carriers) |
| Better brand NPS | More split shipments |

---

### 5.2 Mode B — Throughput / Cost Efficiency

**Primary KPI:** Units shipped per labor-hour and cost per shipped unit.

**Architecture changes:**
- Larger wave batches (40–60 min) → reduces per-pick travel by ~25%.
- Zone consolidation: fewer active pick zones at a time.
- Carrier cost optimization: bin-pack shipments, prefer ground over air.
- High station utilization target (> 85%).

```
task_score weight shift (Mode B):
  sla_urgency_score       → 0.20  (was 0.45)
  pick_batch_efficiency   → 0.45  (was 0.20)
  zone_congestion         → 0.10
  robot_battery           → 0.15
  station_readiness       → 0.10
```

| Pros | Cons |
|---|---|
| Best FC economics | Higher tail latency for low-priority orders |
| Highest throughput per shift | More promise misses during bursts |
| Lowest transport + packaging cost | Harder to absorb exception rate spikes |

---

### 5.3 Mode C — Balanced Strategy (Recommended Default)

**Primary KPI:** Weighted multi-objective score.

```
global_objective =
    0.45 × promise_accuracy_index
  + 0.35 × throughput_efficiency_index
  + 0.20 × cost_efficiency_index
```

**Dynamic mode switching policy:**

| Condition | Trigger | Action |
|---|---|---|
| Carrier cutoff < 45 min | SLA alarm | Shift to Mode A weights temporarily |
| FC utilization < 60% | Load sensor | Shift to Mode B, larger waves |
| Robot fleet outage > 20% | Fleet health alert | Increase human share, accept higher cost |
| Peak season / Prime Day | Calendar trigger | Pre-configure Mode A for 6-hour windows |
| Normal steady-state | — | Mode C balanced weights |

**Why balanced wins in practice:**
- Avoids local optima from single-metric optimization.
- Provides tunable knobs for FC ops managers.
- Natural behavior across seasonal variability with no code changes.

---

## §6 — Tradeoff Summary (4 min)

| Decision | Why | Tradeoff |
|---|---|---|
| **Row-level locks for reservation** | Simplest way to prevent oversell without distributed coordination | Limits reservation TPS per SKU-location (~1k TPS per row) |
| **Idempotency keys at ledger level** | Scanner Wi-Fi is unreliable; must tolerate retries safely | Extra storage per request; UNIQUE constraint overhead |
| **Redis hot queue + SQL durable store** | Task assignment must be < 300ms; Redis < 1ms queuing | Dual-write complexity; Redis loss requires SQL replay |
| **Wave planning in batches** | Reduces pick travel time ~25% | Adds 10–20 min wave latency for orders mid-wave |
| **Saga for exception recovery** | Decoupled recovery; easy to extend for new exception types | Eventual consistency window; distributed flow harder to debug |
| **Human + robot hybrid dispatch** | Robots handle high-volume repetitive tasks; humans handle judgment | Scheduling complexity scales with fleet size |
| **Single FC first** | Simpler correctness model; faster to build and operate | Can't optimize across FCs until Day 2 extension |

---

## §7 — Wrap-up + Day 2: Multi-FC Network Routing (3 min)

### 7.1 Day 2 Extension: FC Selection Engine

Add `FCSelectionEngine` between order intake and reservation:

```
Order arrives
     │
     v
┌────────────────────────────────────────────────────────────────┐
│  FC Selection Engine                                          │
│                                                               │
│  Inputs:                                                      │
│    • Customer delivery address (geo coordinates)             │
│    • Inventory availability by FC (near-real-time, 5–60s lag)│
│    • FC current queue depth / throughput capacity            │
│    • Estimated shipping ETA per FC × carrier                 │
│    • Transfer cost if item must move between FCs             │
│                                                               │
│  Output:                                                      │
│    • FC assignment per order line (single-ship or split)     │
└───────────────────────┬────────────────────────────────────────┘
                        │
             ┌──────────┴───────────┐
             v                      v
      Assigned to FC-1        Assigned to FC-2
      (nearest with stock)    (overflow for one line)
```

### 7.2 FC Selection Score

```
fc_score =
    0.40 × delivery_eta_score           (customer promise date feasibility)
  + 0.25 × fulfillment_cost_score       (pick + pack + ship cost from this FC)
  + 0.20 × fc_capacity_score            (inverse of queue depth / max capacity)
  + 0.15 × inventory_confidence_score   (freshness of inventory signal)
```

### 7.3 New Day 2 Components

| Component | Purpose |
|---|---|
| `GlobalInventoryView` | Aggregated near-real-time inventory signals across all FCs (5–60s refresh) |
| `NetworkRouter` | Applies FC selection score; handles assignment + fallback routing |
| `InterFCTransferPlanner` | Plans inter-FC transfer when no single FC has full stock |
| `RegionalFailoverPolicy` | On FC outage: reroute all unstarted orders to nearest capable FC |

### 7.4 Multi-FC Tradeoffs
- Global optimization improves ETA and cost but adds inventory staleness risk.
- Stale signals can cause false-positive FC assignments → use confidence windows and conservative hold.
- More routing edges (FC-to-FC) increase failure surface → idempotent reassignment required.
- **Start with single-FC correctness. Multi-FC is a Day 2 layer, not Day 1.**

---

## Quick Reference: Data Stores

| Store | Tech | Access Pattern | Why This Choice |
|---|---|---|---|
| `OrderDB` | PostgreSQL | OLTP, strong ACID | Order lifecycle demands strict consistency |
| `InventoryDB` | PostgreSQL (partitioned) | High-write, row-locked | Strong inventory invariants; partition by location |
| `TaskStateDB` | Redis + PostgreSQL | Hot reads < 1ms | Redis for speed, SQL for durability |
| `LocationIndex` | DynamoDB | High-read, point lookup | Fast bin search, near-unlimited scale |
| `EventStore` | Kafka + S3 | Append-only | Audit trail + replay for sagas |
| `AnalyticsLake` | S3 + Athena | Batch, analytical | Cost/SLA reporting; no OLTP needed |

---

## Quick Reference: Key API Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/orders/allocate` | POST | Create FC execution plan and reserve inventory |
| `/inventory/reserve` | POST | Reserve qty by SKU + location (idempotent) |
| `/inventory/commit` | POST | Commit reservation on pick confirmation |
| `/inventory/release` | POST | Release hold on cancel or exception |
| `/tasks/create` | POST | Create pick/stow/pack task |
| `/tasks/assign` | POST | Assign task to worker or robot queue |
| `/events/scan` | POST | Ingest scanner confirmation event |
| `/shipments/manifest` | POST | Generate carrier manifest + label |
| `/exceptions/resolve` | POST | Trigger exception compensation saga |
| `/returns/receive` | POST | Receive and route returned unit |

---

## 10-Minute Summary (Under Time Pressure)

**"I'd design the FC system with 5 core layers:"**

1. **Inbound & Inventory** — receive units, track balances per bin, strict no-oversell via row locks + idempotency.
2. **Order Allocation** — reserve inventory per line idempotently; support partial/backorder with compensation sagas.
3. **Task Orchestration** — create pick/pack tasks; route to humans or robots via scored priority queues; wave-plan for efficiency.
4. **Pack & Ship** — cartonize, carrier-select, print labels, submit manifest; trigger cutoff alarms.
5. **Exception Recovery** — classify, auto-recover, escalate; immutable audit trail for every event.

**Key tradeoffs:**
- Row locks vs. distributed locks: row locks simpler and sufficient at per-SKU-location granularity.
- Waves vs. immediate dispatch: waves reduce travel 25% at cost of 10-20 min queueing latency.
- Single objective vs. balanced: use dynamic weight switching rather than hardcoded mode.
- Single FC vs. multi-FC: prove single-FC correctness first; add network routing in Day 2.

---

## References & Inspiration
- Amazon Robotics FC operating model (public re:Invent concepts)
- WMS/WES architecture patterns: task orchestration, slotting, wave planning
- Saga pattern: compensating transactions in distributed workflows
- Outbox pattern: reliable event publishing from DB transactions
- Prometheus / CloudWatch: observability for FC operational metrics