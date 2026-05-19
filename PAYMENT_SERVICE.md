# Payment Service — System Design

> **Interview format:** 1 hour | **Scale target:** 10K TPS avg / 100K TPS peak (Stripe / Adyen level)
> **Type:** E-commerce checkout pay-in flow (Amazon Pay style)

---

## Interview Pacing Guide

| Phase | Time | Section |
|---|---|---|
| Requirements & Scope | 0–5 min | §1 |
| Back-of-Envelope | 5–10 min | §2 |
| High-Level Architecture | 10–20 min | §3 |
| Deep Dives | 20–50 min | §4.1 – §4.7 |
| Failure Scenarios & Trade-offs | 50–55 min | §5 |
| Wrap-up & Next Steps | 55–60 min | §6 |

---

## §1 — Requirements & Scope (5 min)

### Functional Requirements
1. **Initiate payment** — user submits checkout; system charges their card via a PSP and confirms the order.
2. **Query payment status** — real-time and eventual status lookup (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`, `REFUNDED`).
3. **Refunds** — full and partial refunds, reversible within T+30 days.
4. **Payment history** — paginated audit log per user and per merchant.
5. **Retry & recovery** — failed payments can be retried without double-charging.

### Non-Functional Requirements
| Property | Target |
|---|---|
| Availability | 99.99% (< 1 hr downtime/year) |
| Consistency | Strong — zero double-charges, zero lost money |
| Idempotency | Every operation safe to retry |
| Latency | < 500 ms p99 end-to-end (excluding PSP network) |
| Durability | Every committed payment persisted to ≥ 3 replicas |
| Auditability | Immutable audit trail for every state change |
| Compliance | PCI-DSS (card data), SOC 2, GDPR |

### Out of Scope
- Card tokenization vault internals (delegated to PSP)
- Fraud detection ML models (treated as a downstream consumer)
- Authentication & authorization (assumed solved at API gateway)
- Subscription billing / recurring payment scheduling

---

## §2 — Back-of-Envelope Estimation (5 min)

### Traffic

```
Peak TPS:    100,000 payment initiations/sec  (holiday spike)
Average TPS:  10,000 payment initiations/sec
Refund TPS:      500 /sec  (5% of avg)
Status reads:  50,000 /sec (5:1 read/write ratio)
```

### Storage

```
Payment record:   ~1 KB (metadata, status, idempotency key)
Ledger entry:     ~256 B × 2 entries per payment = 512 B
Kafka message:    ~2 KB (full payment event payload)

Daily writes:
  Payments:  10,000 TPS × 86,400 s × 1 KB   = ~864 GB/day
  Ledger:    10,000 TPS × 86,400 s × 512 B   = ~432 GB/day
  Total:     ~1.3 TB/day raw, ~4 TB/day with 3× replication

Annual:      ~1.4 PB raw → requires tiered archival to cold storage after 90 days hot
```

### Latency Budget (p99, single payment)

```
API Gateway + TLS           10 ms
Idempotency key check       5 ms  (Redis)
Payment record write        20 ms (Postgres primary, synchronous replication)
PSP API call               150 ms (Stripe SLA p99)
Ledger write (async)        --    (via Kafka, non-blocking)
Response to client         185 ms p99 baseline
```

### Availability Math

```
99.99% uptime = 52.6 min downtime/year
Achieved via: multi-region active-active + automatic PSP failover
Each region independently handles traffic; no cross-region writes on critical path
```

---

## §3 — High-Level Architecture (10 min)

### Component Map

```
Client (Browser/App)
        │
        ▼
  ┌─────────────┐
  │ API Gateway │  — rate limiting, TLS termination, request routing
  └──────┬──────┘
         │
         ▼
  ┌──────────────────────┐
  │  Payment Orchestrator │  — core Saga engine, owns payment lifecycle
  └──────┬───────────────┘
         │
   ┌─────┼────────────────────────┐
   ▼     ▼                        ▼
┌─────┐ ┌────────────────┐  ┌──────────────┐
│Redis│ │  Payment Store  │  │ Outbox Table │ ─► Kafka Connect ─► Kafka
│     │ │  (PostgreSQL)   │  │  (same DB)   │
│ • idempotency keys      │  └──────────────┘
│ • rate limit counters   │         │
│ • in-flight locks       │         ▼
└─────┘ └────────────────┘   ┌──────────────────────────────┐
                              │            Kafka             │
                              │  payment.initiated           │
                              │  payment.processing          │
                              │  payment.completed           │
                              │  payment.failed              │
                              └──┬───────┬──────────┬────────┘
                                 │       │          │
                                 ▼       ▼          ▼
                           Ledger   Notification  Reconciliation
                           Service  Service       Service
                                 │
                                 ▼
                          ┌─────────────┐
                          │  Ledger DB  │  (append-only, Postgres partitioned
                          │             │   or Cassandra)
                          └─────────────┘

Payment Orchestrator also calls:
  ┌─────────────────────────────────────┐
  │         PSP Abstraction Layer        │
  │  ┌──────────┐  ┌──────┐  ┌───────┐  │
  │  │  Stripe  │  │Adyen │  │  ...  │  │
  │  └──────────┘  └──────┘  └───────┘  │
  └─────────────────────────────────────┘
       PSPs call back via webhooks → Webhook Handler → Kafka
```

### Data Flow: Happy Path

```
1.  Client sends POST /payments  {amount, currency, payment_method_token, idempotency_key}
2.  API Gateway authenticates, rate-limits, routes to Payment Orchestrator
3.  Orchestrator checks idempotency key → Redis miss + DB miss → new payment
4.  Writes payment record (status=PENDING) + outbox event in one DB transaction
5.  Outbox → Kafka: payment.initiated
6.  Orchestrator calls PSP Abstraction Layer → Stripe /v1/charges
7.  Stripe returns success → Orchestrator updates payment to SUCCESS
8.  Outbox event: payment.completed → Kafka
9.  Ledger Service consumes event → writes two ledger entries (debit buyer, credit merchant)
10. Notification Service consumes event → sends confirmation email/push
11. Client receives 200 OK with payment_id and status=SUCCESS
```

---

## §4 — Deep Dives (30 min)

---

### §4.1 — Idempotency & Exactly-Once Semantics (~5 min)

#### The Problem
Network failures cause clients to retry. Without idempotency, a retry after a partial success causes a double-charge — money leaves the buyer's account twice. This is the most critical correctness property in payments.

#### Solution Architecture

**Layer 1 — Client-generated idempotency key**
```
Client generates:  UUID v4  (e.g., "550e8400-e29b-41d4-a716-446655440000")
Sent as header:    Idempotency-Key: <uuid>
Scope:             Per client, per endpoint — same key on /payments and /refunds are independent
TTL:               24 hours (PSP retry window)
```

**Layer 2 — Redis in-flight deduplication (fast path)**
```
On request arrival:
  SET idempotency:<key> <payment_id> NX EX 86400

NX (set if Not eXists):
  → Success (key was absent): proceed normally, this is a new request
  → Fail (key exists):        duplicate detected

If duplicate and payment still PENDING:
  → Return 202 Accepted  (processing in progress)

If duplicate and payment COMPLETED:
  → Return 200 OK with original response (from Redis cache)
```

**Layer 3 — Database unique constraint (durable safety net)**
```sql
CREATE TABLE payments (
    payment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(128) NOT NULL,
    user_id          UUID NOT NULL,
    ...
    UNIQUE (idempotency_key, user_id)   -- prevents race condition if Redis evicts key
);
```
Even if Redis is unavailable or the key has been evicted, the DB constraint prevents two rows with the same `(idempotency_key, user_id)`. The second insert raises a unique violation — the service catches it and returns the existing record.

**Layer 4 — Idempotent PSP calls**
Stripe and Adyen both accept an `Idempotency-Key` header. The Payment Orchestrator passes the same key downstream, so even if the PSP call is retried, Stripe will not create a second charge.

#### Race Condition: Two requests arrive simultaneously
```
Thread A reads Redis → miss
Thread B reads Redis → miss (A hasn't written yet)
Thread A writes Redis (NX succeeds) → proceeds
Thread B writes Redis (NX fails) → 202 Accepted or retry
```
NX atomicity in Redis ensures only one thread proceeds. The DB unique constraint provides a second safety net in case of Redis failure between A's read and A's write.

#### Why not use a distributed lock instead?
Locks block. For a payment that takes 150-300ms (PSP latency), holding a lock that long degrades throughput. The idempotency key approach is non-blocking — duplicates are detected and short-circuited, not queued.

---

### §4.2 — Distributed Transactions: Saga Pattern (~5 min)

#### Why Not 2-Phase Commit (2PC)?
2PC is the classic distributed transaction protocol. It fails in this context for three reasons:

1. **PSP is external** — you cannot enroll Stripe into a 2PC protocol. They have no "prepare" API endpoint. Distributed transactions only work when all participants implement the protocol.
2. **Locks block resources** — 2PC holds database row locks during the "prepare" phase while waiting for all participants to vote. At 100K TPS, this causes lock contention and deadlocks.
3. **Coordinator is a SPOF** — if the transaction coordinator crashes after "prepare" but before "commit", all participants are stuck holding locks indefinitely.

#### Saga Pattern: Orchestration vs. Choreography

| | Orchestration | Choreography |
|---|---|---|
| How | Central orchestrator sends commands to each step | Each service reacts to events and emits the next |
| Observability | Full state visible in one place | Requires tracing across multiple services |
| Error handling | Orchestrator explicitly triggers compensating transactions | Complex event chains, hard to reason about |
| Coupling | Services are "dumb" — they just execute commands | Services must know what events to react to |

**Decision: Orchestration Saga.** At this scale, debuggability and correctness matter more than pure decoupling. The Payment Orchestrator is the single source of truth for a payment's state machine.

#### Saga Steps: Pay-in Flow

```
Step 1: RESERVE_FUNDS
  Action:      Debit customer wallet/credit line (put funds on hold)
  Compensate:  Release hold (RELEASE_FUNDS)

Step 2: CHARGE_PSP
  Action:      Call PSP (Stripe) with amount + token
  Compensate:  Issue PSP refund (POST /v1/refunds)

Step 3: UPDATE_LEDGER
  Action:      Write debit (buyer) + credit (merchant) ledger entries
  Compensate:  Write reverse ledger entries (credit buyer, debit merchant)

Step 4: CONFIRM_ORDER
  Action:      Update payment status to SUCCESS, notify order service
  Compensate:  Update payment status to FAILED, notify order service
```

#### State Machine

```
CREATED ──► FUNDS_RESERVED ──► PSP_CHARGED ──► LEDGER_UPDATED ──► SUCCESS
   │               │                │                │
   │          FAILED (comp 1)  FAILED (comp 2)  FAILED (comp 3)
   │               │                │                │
   └───────────────▼────────────────▼────────────────▼
                               FAILED
```

#### Compensating Transactions: Critical Properties
- **Idempotent**: Compensation can be called multiple times safely (same PSP refund key, same ledger reversal event).
- **Retryable**: If compensation itself fails, it is retried with exponential backoff. Compensation must not assume a clean state.
- **Not rollback**: Saga compensation is forward-progress (new records are created), not a SQL ROLLBACK. The original failed records remain in the DB for audit.

#### Outbox Pattern (Reliable Event Publishing)

The dual-write problem: if you update the DB and then publish to Kafka, a crash between the two leaves the DB updated but Kafka never notified — downstream services never know the payment succeeded.

**Solution: Transactional Outbox**
```sql
-- Both writes happen in a single DB transaction — atomic
BEGIN;
  UPDATE payments SET status = 'SUCCESS' WHERE payment_id = $1;
  INSERT INTO outbox (event_type, payload, created_at)
    VALUES ('payment.completed', $payload, now());
COMMIT;
```

A **Kafka Connect** (Debezium) change data capture (CDC) connector tails the PostgreSQL Write-Ahead Log (WAL) and publishes outbox rows to Kafka. Because CDC reads the WAL, it is guaranteed to see every committed row — no event is ever lost, even if the application crashes immediately after the DB commit.

```
DB WAL ──► Debezium CDC ──► Kafka Connect ──► Kafka Topic
             (reads log,         (exactly-once
              not polling)        delivery to Kafka
                                  via idempotent
                                  producer)
```

---

### §4.3 — Ledger & Double-Entry Bookkeeping (~4 min)

#### Why Double-Entry?
Single-entry: `payments.amount = 100` — you know 100 was charged, but no audit trail of where it went.
Double-entry: every financial event creates a DEBIT on one account and a CREDIT on another. The accounting equation `Assets = Liabilities + Equity` must always balance. If it doesn't, there is a bug.

Double-entry is not optional for a payment system — it is required by accounting standards and enables:
- Balance verification (sum of all debits = sum of all credits across all accounts)
- Dispute resolution (trace money at every step)
- Regulatory reporting (SOX, AML)

#### Account Types

```
CUSTOMER_WALLET      — buyer's available balance or credit hold
MERCHANT_WALLET      — merchant's pending and settled balance
PLATFORM_FEE         — the platform's revenue account
PSP_SETTLEMENT       — funds held with PSP, awaiting settlement (T+1 or T+2)
LIABILITY_ESCROW     — funds held in escrow for disputed payments
```

#### Ledger Schema

```sql
CREATE TABLE ledger_entries (
    entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID NOT NULL REFERENCES payments(payment_id),
    account_id      UUID NOT NULL,
    account_type    TEXT NOT NULL,   -- CUSTOMER_WALLET, MERCHANT_WALLET, etc.
    direction       TEXT NOT NULL,   -- DEBIT | CREDIT
    amount          BIGINT NOT NULL, -- stored in minor units (cents) — NO floats
    currency        CHAR(3) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata        JSONB
    -- NO UPDATE, NO DELETE ever — this table is append-only
);

CREATE INDEX ON ledger_entries (account_id, created_at DESC);
CREATE INDEX ON ledger_entries (payment_id);
```

**Key decision: amounts stored as BIGINT (minor units)**
Floating-point arithmetic loses precision for monetary values. `0.1 + 0.2 = 0.30000000000000004` in IEEE 754. Always store `$19.99` as `1999` cents. Convert to decimal only at the presentation layer.

#### A Payment in Ledger Entries

For a $100 purchase where the platform takes $2.90 + 2.9% fee:

```
Payment $100.00 from buyer to merchant (fee: $5.80 = $2.90 + 2.9%)

Entry 1: DEBIT  CUSTOMER_WALLET    $100.00   (money leaves buyer)
Entry 2: CREDIT PSP_SETTLEMENT     $100.00   (money arrives at PSP)
Entry 3: DEBIT  PSP_SETTLEMENT     $94.20    (after PSP settles net)
Entry 4: CREDIT MERCHANT_WALLET    $94.20    (merchant receives net)
Entry 5: DEBIT  PSP_SETTLEMENT     $5.80     (fee portion)
Entry 6: CREDIT PLATFORM_FEE       $5.80     (platform earns fee)

Verification: sum(DEBITs) == sum(CREDITs) == $100.00 ✓
```

#### Balance Computation: Event Sourcing vs. Cached Balance

**Option A — Recompute from ledger (pure event sourcing)**
```sql
SELECT
    SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END)
FROM ledger_entries
WHERE account_id = $1 AND currency = $2;
```
Pro: always accurate, no stale data. Con: full table scan per balance query; at 10K TPS, `ledger_entries` grows by ~1.7M rows/day — queries become expensive within days.

**Option B — Cached balance + ledger as source of truth (chosen)**
```sql
CREATE TABLE account_balances (
    account_id   UUID PRIMARY KEY,
    currency     CHAR(3),
    balance      BIGINT,        -- updated atomically with each ledger write
    version      BIGINT         -- optimistic locking
);
```
The balance is updated in the same transaction as the ledger entry. If they drift (bug or crash), the ledger is authoritative — a reconciliation job recomputes the balance from ledger entries and corrects it.

#### Immutability Guarantee
Ledger entries are NEVER updated or deleted. A `REFUND` does not delete the original entries — it creates new reversal entries with opposite directions. This provides a complete, tamper-evident audit trail. Database-level: no `UPDATE` or `DELETE` permissions granted to the application service user on `ledger_entries`.

---

### §4.4 — Reconciliation & Failure Recovery (~5 min)

#### Why Reconciliation Is Necessary
Even with idempotency, Sagas, and strong consistency, discrepancies between the internal system and the PSP arise because:
- **Network partitions**: the PSP charged the card but the response never reached the Payment Orchestrator — the internal state says FAILED but PSP says SUCCESS.
- **Asynchronous settlement**: PSPs don't settle money instantly. Visa/Mastercard settle T+1; ACH settles T+2 or T+3. The internal ledger records the transaction immediately; the actual bank movement lags behind.
- **PSP bugs**: rare but real — PSPs occasionally charge the wrong amount, double-charge, or fail to refund.

#### Mismatch Types

| Type | Description | Handling |
|---|---|---|
| **Missing internal** | PSP shows charge, internal DB has no record | Alert + manual review; likely a crash after PSP call |
| **Missing external** | Internal DB shows SUCCESS, PSP has no record | Investigate; possible PSP failure mid-transaction |
| **Amount mismatch** | Same payment_id, different amounts | Automatic alert; never auto-correct money |
| **Status mismatch** | Internal=SUCCESS, PSP=REFUNDED | Trigger internal refund flow |
| **Timing mismatch** | T+2 settlement for ACH — normal, suppress for 3 days | Use settlement_expected_date field to suppress false alerts |

#### Reconciliation Architecture

```
                     ┌─────────────────────┐
  PSP Settlement API ►│  PSP Adapter        │  — polls PSP settlement reports (hourly or via webhook)
  (CSV / REST)        └────────┬────────────┘
                               │
                               ▼
                    ┌─────────────────────────┐
                    │  Reconciliation Service  │
                    │  (batch job, hourly)     │
                    └────────┬────────────────┘
                             │  reads
                    ┌────────▼────────┐    ┌──────────────┐
                    │  Internal Ledger │    │ PSP Records  │
                    │  (PostgreSQL)    │    │ (normalized) │
                    └────────┬────────┘    └──────┬───────┘
                             │                    │
                             └────────┬───────────┘
                                      │ diff
                                      ▼
                             ┌─────────────────┐
                             │ Mismatch Table   │
                             └────────┬────────┘
                                      │
                          ┌───────────┼────────────┐
                          ▼           ▼             ▼
                    Auto-resolve   Alert PagerDuty  Suppress
                    (status sync)  (amount mismatch) (timing window)
```

#### Reconciliation Algorithm (Simplified)

```python
def reconcile(window_start, window_end):
    internal = fetch_internal_payments(window_start, window_end)  # from Postgres
    psp      = fetch_psp_settlements(window_start, window_end)    # from PSP API

    internal_map = {p.idempotency_key: p for p in internal}
    psp_map      = {p.psp_reference: p   for p in psp}

    for key, psp_record in psp_map.items():
        internal_record = internal_map.get(key)

        if not internal_record:
            flag_mismatch(type='MISSING_INTERNAL', psp_ref=key)

        elif internal_record.amount != psp_record.amount:
            flag_mismatch(type='AMOUNT_MISMATCH', diff=psp_record.amount - internal_record.amount)

        elif internal_record.status != normalize_status(psp_record.status):
            if within_settlement_window(psp_record):
                suppress(key)  # T+1/T+2 lag — expected
            else:
                auto_resolve_or_alert(internal_record, psp_record)

    for key in internal_map:
        if key not in psp_map:
            flag_mismatch(type='MISSING_EXTERNAL', internal_ref=key)
```

#### PSP Settlement Windows
- **Card networks (Visa/MC)**: T+1 business day
- **ACH (bank transfer)**: T+2 or T+3
- **Wire (SWIFT)**: T+1 to T+2 (same-currency)
- **Crypto rails**: near-real-time

The reconciliation service stores an `expected_settlement_date` on each payment. Mismatches before that date are auto-suppressed and re-evaluated on T+1. After the window, unresolved mismatches escalate to the ops team.

#### Handling the "PSP Charged but We Don't Know" Case
This is the most dangerous failure mode: PSP charged the user but the Orchestrator crashed before receiving the response.

```
Resolution flow:
  1. Payment is stuck in PROCESSING for > 30 seconds
  2. A watchdog process queries PSP status API using the idempotency key
  3. If PSP says CHARGED → mark payment SUCCESS, write ledger entries
  4. If PSP says NOT_FOUND → mark payment FAILED, release hold
  5. If PSP says PENDING → wait and retry (step 2) up to 5 minutes, then escalate
```

---

### §4.5 — PSP Integration: Abstraction & Failover (~4 min)

#### Why Multiple PSPs?
- **Resilience**: Stripe has outages. In 2021, Stripe experienced a ~2.5-hour partial outage. With a single PSP, that means 99.97% availability max.
- **Cost optimization**: different PSPs charge different fees per region or payment method. Adyen may be cheaper for EU SEPA payments; Stripe better for US cards.
- **Regulatory**: some markets require a local PSP (e.g., Razorpay for India, Pix for Brazil).
- **Authorization rate**: different PSPs have different authorization rates with different issuing banks. Routing smartly increases conversion.

#### PSP Abstraction Layer

```java
interface PSPClient {
    ChargeResponse  charge(ChargeRequest request);
    RefundResponse  refund(RefundRequest request);
    StatusResponse  getStatus(String pspReference);
}

class StripeClient  implements PSPClient { ... }
class AdyenClient   implements PSPClient { ... }
class RazorpayClient implements PSPClient { ... }
```

The Payment Orchestrator only knows about `PSPClient` — it never imports Stripe-specific classes. Swapping or adding a PSP requires no changes to the core flow.

#### PSP Router

```
Routing rules (evaluated in order):
  1. Currency / region  →  BRL payments → Pix; INR payments → Razorpay
  2. Payment method     →  SEPA debit → Adyen; US card → Stripe
  3. Load / health      →  Stripe error rate > 5% in last 60s → route to Adyen
  4. Cost optimization  →  optional A/B routing by fee schedule
```

Routing configuration is stored in a feature-flag service (LaunchDarkly / internal) so routing rules can change without deployment.

#### Circuit Breaker

```
States:  CLOSED ──► OPEN ──► HALF-OPEN ──► CLOSED
                  (trip)   (probe)       (recover)

Trip condition:  > 5% error rate OR > 500ms p99 latency over 60-second sliding window
Open state:      Immediately route to fallback PSP (no attempt to primary)
Half-open:       Allow 1 request/5s through to primary to test recovery
Recover:         Error rate < 1% for 30s → return to CLOSED

Implementation:  Resilience4j CircuitBreaker (Java) or similar
```

#### Webhook Handling (Asynchronous PSP Responses)

PSPs don't always respond synchronously to API calls. Card network authorization can take up to 30 seconds in rare cases. PSPs send a webhook when the final status is determined.

```
Webhook security:
  1. PSP signs payload with HMAC-SHA256 using a shared secret
  2. Webhook handler verifies signature before processing
  3. Respond 200 OK within 5 seconds (PSP will retry on timeout)

Webhook idempotency:
  store processed webhook IDs in a Redis set with 7-day TTL
  if webhook_id already in set → return 200 OK, skip processing

Webhook → Kafka (do not process synchronously):
  Webhook handler writes event to Kafka immediately → return 200 OK
  Kafka consumer processes async → updates payment status
  This decouples PSP retry window from internal processing time
```

#### PSP Retry Policy

```
Strategy: exponential backoff with jitter
  Attempt 1: immediate
  Attempt 2: 1s + jitter(0–500ms)
  Attempt 3: 4s + jitter
  Attempt 4: 16s + jitter
  Attempt 5: give up → mark FAILED, trigger compensation

Timeout per attempt: 3 seconds (PSP SLA p95 < 500ms; 3s gives headroom)
Total timeout budget: ~25 seconds before escalation

Do NOT retry on:
  HTTP 400 (bad request — retrying won't help)
  HTTP 402 (card declined — customer action required)
  HTTP 422 (invalid card data)

DO retry on:
  HTTP 500, 502, 503, 504 (PSP server errors)
  Connection timeout
  Read timeout (response may have gone through — use idempotency key!)
```

---

### §4.6 — Database Design & Strong Consistency (~5 min)

#### Database Selection Rationale

| Store | Technology | Why |
|---|---|---|
| Payment records | **PostgreSQL** | ACID transactions, serializable isolation, foreign key integrity, rich query capabilities |
| Ledger entries | **PostgreSQL (partitioned)** | Append-only, range-partitioned by month for pruning; or Cassandra for extreme write scale |
| Idempotency keys | **Redis** | Sub-millisecond read/write, TTL-based expiry, atomic NX operations |
| PSP routing config | **Feature flag service** | Hot config changes without deployment |
| Event bus | **Kafka** | Durable, replayable, high-throughput, consumer group fan-out |

**Why PostgreSQL over NoSQL for payments?**
Money requires ACID. `UPDATE wallet SET balance = balance - 100 WHERE user_id = $1 AND balance >= 100` — this must be atomic, consistent, isolated, and durable. NoSQL eventual consistency allows two reads of `balance = 500` in parallel, each deducting 400, resulting in a final balance of 100 rather than the correct -300 (prevented via constraint). PostgreSQL with `FOR UPDATE` or serializable isolation prevents this.

#### Core Schema

```sql
-- Payments (sharded by payment_id)
CREATE TABLE payments (
    payment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(128) NOT NULL,
    user_id          UUID NOT NULL,
    merchant_id      UUID NOT NULL,
    order_id         UUID,
    amount           BIGINT NOT NULL,          -- minor units (cents)
    currency         CHAR(3) NOT NULL,
    status           payment_status NOT NULL,   -- PENDING|PROCESSING|SUCCESS|FAILED|REFUNDED
    psp_name         TEXT,
    psp_reference    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (idempotency_key, user_id)
);

-- Append-only event log (for audit + Saga state recovery)
CREATE TABLE payment_events (
    event_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  UUID NOT NULL REFERENCES payments(payment_id),
    event_type  TEXT NOT NULL,      -- PAYMENT_INITIATED, PSP_CHARGED, LEDGER_UPDATED, etc.
    payload     JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactional outbox (read by Debezium CDC → Kafka)
CREATE TABLE outbox (
    outbox_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type   TEXT NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published    BOOLEAN DEFAULT false   -- set true after Debezium publishes; pruned in batch
);

-- Ledger (append-only, partitioned by month)
CREATE TABLE ledger_entries (
    entry_id     UUID DEFAULT gen_random_uuid(),
    payment_id   UUID NOT NULL,
    account_id   UUID NOT NULL,
    account_type TEXT NOT NULL,
    direction    TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount       BIGINT NOT NULL CHECK (amount > 0),
    currency     CHAR(3) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY RANGE (created_at);

-- Example monthly partition
CREATE TABLE ledger_entries_2026_05
    PARTITION OF ledger_entries
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
```

#### Sharding Strategy

```
Shard key: payment_id (UUID, random distribution)

Why NOT shard by user_id:
  Hot users (merchants with high volume) would create hot shards
  Power-law distribution of transactions — top 0.1% of merchants generate 40% of volume

Why payment_id works:
  UUID v4 is uniformly random → uniform shard distribution
  All data for a payment lives on one shard (no cross-shard queries for payment lifecycle)
  Cross-user queries (user history) hit multiple shards — mitigated with read replicas + async aggregation

Shard count: start with 16, scale to 64 at 100K TPS
Shard routing: consistent hashing ring, managed by Vitess (MySQL) or Citus (Postgres)
```

#### Consistency Model: Serializable Isolation

For the critical payment write (debit wallet + write ledger):
```sql
-- Use REPEATABLE READ for most reads (good performance)
-- Use SERIALIZABLE for the payment finalization transaction

BEGIN ISOLATION LEVEL SERIALIZABLE;
  SELECT balance FROM account_balances WHERE account_id = $1 FOR UPDATE;
  -- verify sufficient balance
  UPDATE account_balances SET balance = balance - $amount WHERE account_id = $1;
  INSERT INTO ledger_entries (...) VALUES (...);
  UPDATE payments SET status = 'SUCCESS' WHERE payment_id = $2;
COMMIT;
```

`SERIALIZABLE` in Postgres uses Serializable Snapshot Isolation (SSI) — it detects read-write conflicts without locking the entire table, unlike MySQL's SERIALIZABLE which uses range locking.

#### Read Path: Replicas + Redis Cache

```
Payment status query:
  1. Check Redis cache (TTL 5s for PENDING/PROCESSING; indefinite for terminal states)
  2. Cache miss → query nearest read replica (synchronous replication, lag < 10ms)
  3. Terminal states (SUCCESS/FAILED/REFUNDED) are written to Redis with no expiry on first read

Why cache terminal states indefinitely:
  Status will never change again — safe to cache forever
  Most status queries are for recently completed payments (recency bias)
  Eliminates DB load for the most common read pattern
```

---

### §4.7 — Async Messaging: Kafka & Outbox Pattern (~5 min)

#### Why Kafka Over Direct Service Calls

**Synchronous call chain (fragile):**
```
Payment Orchestrator ──► Ledger Service (sync)
                    ──► Notification Service (sync)
                    ──► Analytics Service (sync)
                    ──► Reconciliation Service (sync)
```
If any downstream service is slow or down, it blocks the payment response to the user. A slow analytics pipeline should never cause a checkout to fail.

**Kafka-based fan-out (resilient):**
```
Payment Orchestrator ──► Kafka (fire-and-forget, durable)
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        Ledger Service  Notification  Analytics
        (own consumer)  Service       Service
        (own pace)      (own pace)    (own pace)
```
The Orchestrator's job ends when the event is durably written to Kafka. Each consumer processes at its own pace and can replay from any offset.

#### Topic Design

```
payment.initiated     — payment created, awaiting PSP charge
payment.processing    — PSP call in flight
payment.completed     — PSP charged successfully
payment.failed        — terminal failure (all retries exhausted)
payment.refund.initiated
payment.refund.completed
payment.reconciliation.mismatch   — emitted by Reconciliation Service
```

**Partitioning:**
```
Partition key: payment_id
  → All events for a given payment land on the same partition
  → Guarantees ordering per payment (initiated before completed)

Partition count: 200 partitions (100K TPS ÷ ~500 msgs/sec/partition)
Replication factor: 3 (RF=3, min ISR=2)
Retention: 7 days (allow reconciliation service to replay recent history)
```

#### Consumer Guarantees: Idempotent Consumers

Kafka guarantees **at-least-once** delivery. Consumers must handle duplicate messages.

```
Ledger Service consumer:
  1. Receive payment.completed event with payment_id
  2. Check: SELECT COUNT(*) FROM ledger_entries WHERE payment_id = $1
  3. If count > 0 → already processed → commit offset, skip
  4. If count = 0 → write entries + commit offset

Notification Service consumer:
  1. Receive event
  2. Check: SELECT * FROM notification_log WHERE payment_id = $1 AND type = 'PAYMENT_CONFIRMED'
  3. If exists → skip (notification already sent)
  4. If not → send notification + write to notification_log
```

Alternatively, use **exactly-once semantics** (Kafka EOS) with a transactional producer + idempotent consumer — but this requires Kafka 2.5+ and adds ~10ms overhead per batch. At this scale, idempotent consumers are simpler and have fewer failure modes.

#### Consumer Lag Monitoring
```
Alert condition: consumer lag > 10,000 messages on payment.completed (Ledger consumer)
  → Indicates ledger writes are falling behind real-time payments
  → Escalate: auto-scale consumer instances (Kubernetes HPA on consumer lag metric)

SLO: Ledger entries written within 5 seconds of payment.completed event
```

---

## §5 — Failure Scenarios & Trade-offs (5 min)

### Scenario 1: PSP Charges but Orchestrator Crashes Before Receiving Response

```
Timeline:
  T=0ms:  Orchestrator sends charge request to Stripe (status=PROCESSING in DB)
  T=140ms: Stripe charges card, sends 200 OK
  T=141ms: Orchestrator crashes (JVM OOM, pod restart, etc.)
  T=142ms: Response lost

Problem: Stripe charged $100, internal status still PROCESSING.
         Without resolution, customer is charged but order is not confirmed.

Resolution:
  Watchdog service polls payments WHERE status='PROCESSING' AND updated_at < now() - 30s
  For each stuck payment → query PSP status API using the stored idempotency_key
  PSP returns: CHARGED → Orchestrator resumes Saga from Step 3 (update ledger)
  PSP returns: NOT_FOUND → mark FAILED, trigger compensation (Step 1 reverse)
  PSP returns: PENDING → wait, retry up to 5 minutes, then alert ops
```

### Scenario 2: Ledger Write Fails After PSP Charge

```
Saga state: PSP_CHARGED (money left buyer, arrived at PSP settlement account)
Failure:    Ledger Service times out, Saga marks step 3 FAILED

Compensation chain:
  Orchestrator calls PSP refund API (Stripe POST /v1/refunds with same idempotency key)
  Ledger Service writes reversal entries (async, retried until success)
  Payment status → FAILED with reason LEDGER_WRITE_FAILURE

Why PSP refund is safe to retry:
  Idempotency key ensures Stripe processes the refund exactly once
  Even if refund API is called 5 times, customer is refunded exactly once
```

### Scenario 3: Kafka Consumer Lag — Ledger Behind by 1 Hour

```
Cause:   Ledger DB shard hot spot, consumer batch processing slow
Impact:  Merchant wallet balance stale by 1 hour (not shown in dashboard)
         Reconciliation sees expected lag — no false alerts

Mitigation:
  Auto-scale Ledger Service consumers (more pods → more partitions consumed in parallel)
  Add read replica for merchant balance queries → serves stale balance with staleness indicator
  SLO breach alert → on-call escalation if lag > 30 min
  Payments are NOT blocked — Kafka durability ensures events will be processed eventually
```

### Scenario 4: Database Primary Fails (Postgres Leader Election)

```
Failure:    Primary Postgres node crashes
Impact:     Writes fail (payments cannot be initiated)
Recovery:
  1. Patroni (HA manager) detects primary failure via heartbeat (10s timeout)
  2. Initiates leader election among replicas — selects replica with highest WAL LSN
  3. Promotes new primary (~15–30s total)
  4. DNS/VIP updated to point to new primary
  5. Payment Orchestrator reconnects (retry loop with 5s max backoff)

During the outage window (~30s):
  New payment initiations fail → client receives 503
  In-flight payments: Saga state persisted in DB — watchdog resumes after recovery
  Kafka events buffered — no events lost during leader election

Mitigations to reduce window:
  Synchronous replication to at least one replica (RPO=0)
  Pre-warm connection pools on standbys
  Implement client-side retry with idempotency key (safe to retry)
```

### Trade-off Summary

| Decision | Chosen | Alternative | Why Chosen |
|---|---|---|---|
| Saga vs 2PC | **Saga (Orchestration)** | 2PC | 2PC incompatible with external PSPs; Saga scales better |
| Sync vs Async ledger write | **Async (Kafka)** | Sync inline | Isolates PSP latency from ledger write latency; ledger is eventually consistent |
| Idempotency store | **Redis + DB unique constraint** | DB only | Redis is the fast path; DB is the safety net for Redis failures |
| Shard key | **payment_id** | user_id | Uniform distribution; avoids hot shards on power merchants |
| Ledger balance | **Cached + event sourced** | Compute on read | Real-time balance without full table scan; ledger is authoritative on conflict |
| PSP abstraction | **Strategy pattern + router** | Single PSP | Multi-PSP failover; regional compliance; cost optimization |
| Kafka delivery | **At-least-once + idempotent consumers** | Exactly-once (EOS) | Simpler failure model; EOS adds latency and operational complexity |

---

## §6 — Wrap-up & What I'd Do Next (5 min)

### What Was Built

A payment service handling 100K TPS peak with:
- **Zero double-charges** — enforced by idempotency keys at 3 layers (Redis, DB, PSP)
- **No money lost** — Saga compensating transactions + watchdog recovery for stuck payments
- **Accurate books** — double-entry immutable ledger, computed balances, nightly reconciliation
- **High availability** — multi-PSP failover with circuit breaker, multi-region Postgres with Patroni HA
- **Full auditability** — every state change captured in `payment_events`, every cent tracked in `ledger_entries`

### What I'd Add Next

1. **Fraud detection pipeline** — consume `payment.initiated` from Kafka, run ML scoring, emit `fraud.decision` event; if REJECT, trigger Saga compensation before PSP charge. Real-time scoring < 50ms p99.

2. **Rate limiting per merchant** — Redis sliding window counters per `merchant_id`; protect platform from a single merchant generating traffic spikes. Configured via the routing service.

3. **Card vault (tokenization)** — integrate with PSP Vault (Stripe Vault / Adyen Tokenization) so raw card numbers never touch internal infrastructure. Reduces PCI-DSS scope from SAQ D to SAQ A.

4. **Multi-currency & cross-border** — FX rate service (lock rate at payment initiation, apply at settlement), multi-currency ledger entries (`fx_rate` + `base_currency_amount` on each row), regional PSP routing (Razorpay/India, Pix/Brazil, Alipay/China), and compliance layer (OFAC screening, AML reporting).

5. **Merchant settlement** — batch job (daily or T+1) reads `MERCHANT_WALLET` ledger balance, generates payout instructions, submits to ACH/SWIFT rails.

6. **Observability** — distributed tracing (Jaeger/OTEL) with `payment_id` as trace ID propagated through all services; dashboards for: payment success rate, PSP latency p50/p95/p99, consumer lag per topic, reconciliation mismatch count.

---

## Quick Reference: Key Numbers

| Metric | Value |
|---|---|
| Avg TPS | 10,000 |
| Peak TPS | 100,000 |
| Payment record size | ~1 KB |
| Ledger entry size | ~256 B |
| DB write throughput (peak) | ~100 MB/s |
| Daily storage (hot) | ~1.3 TB/day |
| Idempotency key TTL | 24 hours |
| Stuck payment watchdog threshold | 30 seconds |
| PSP retry attempts | 5 (max ~25s) |
| Reconciliation frequency | Hourly |
| Ledger consumer SLO | < 5s lag |
| DB leader election time (Patroni) | ~15–30s |
| Availability target | 99.99% |
