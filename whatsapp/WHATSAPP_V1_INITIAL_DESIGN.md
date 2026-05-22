# WhatsApp / FB Messenger — V1 Initial Design (Baseline)

**Goal:** Design a real-time chat application supporting 1:1 messaging, group chat, media sharing, read receipts, and last-seen status at ~2B users / 65B messages per day.

> This is the V1 baseline design — covering the happy path and core architecture.
> Gaps and improvements are documented in `WHATSAPP_V2_IMPROVEMENTS.md`.

---

## §1 — Requirements & Scope

### Functional Requirements
1. **1:1 Chat** — send text/media to another user.
2. **Group Chat** — message delivered to all group members.
3. **Media messages** — text, images, videos, documents.
4. **Read receipts** — single tick (sent), double tick (delivered), blue tick (read).
5. **Last seen** — last time a user was active.

### Non-Functional Requirements

| Property | Target |
|---|---|
| Latency | Very low — real-time feel |
| Availability | 99.99% |
| Message lag | Near zero |
| Scale | 2B users, 1.6B MAU, 65B messages/day |
| Offline support | Messages delivered on reconnect |

### Out of Scope (V1)
- E2E encryption internals
- Auth/authorization details
- Multi-device support (single device per user assumed)
- Push notifications (APNs/FCM) for offline users
- Analytics pipeline

---

## §2 — Scale Estimation

```
Total users:               2,000,000,000
Monthly active users:      1,600,000,000
Messages per day:          65,000,000,000
Messages per second (avg): ~750,000
Avg message size:          ~1 KB
Storage per day:           ~65 TB
Storage per year:          ~23 PB
```

---

## §3 — High-Level Architecture

### 3.1 Architecture Diagram

```
+-----------------------------------------------+
|               USER DEVICES                    |
|  Mobile Apps   Browsers   Wearables           |
+------------------+----------------------------+
                   | WebSocket (bidirectional TCP)
                   v
     +----------------------------+
     |   Load Balancer / API GW   |
     |   Auth + TLS termination   |
     +---+--------------------+---+
         |                    |
         v                    v
+----------------+     +------------------+
| WebSocket      |     | WebSocket        |
| Handler 1      |     | Handler 2 ...N   |
| (open conns    |     | (distributed     |
|  with users)   |     |  globally)       |
+-------+--------+     +--------+---------+
        +----------+------------+
                   |
     +-------------v-----------+    +------------------+
     | WebSocket Manager       |    | Message Service  |
     | Redis:                  |    | Cassandra        |
     |  user → handler         |    | (message store)  |
     |  handler → [users]      |    +--------+---------+
     +-------------------------+             |
                                     Kafka (group msgs)
                                             |
                                    +------------------+
                                    | Group Message    |
                                    | Handler          |
                                    | (Kafka consumer) |
                                    +--------+---------+
                                             |
                                     Group Service
                                     MySQL + Redis

+------------------+   +------------------+   +------------------+
| User Service     |   | Asset Service    |   | Last Seen        |
| MySQL + Redis    |   | S3 + CDN         |   | Service          |
|                  |   |                  |   | Cassandra        |
+------------------+   +------------------+   +------------------+
```

### 3.2 Key Data Flows

| Flow | Path |
|---|---|
| 1:1 message (online) | Sender → Handler 1 → Message Service + WS Manager → Handler 2 → Recipient |
| 1:1 message (offline) | Sender → Handler 1 → Message Service → waits → delivered on reconnect |
| Read receipt | Recipient ACKs → Handler 2 → WS Manager → Handler 1 → Sender |
| Group message | Sender → Handler → Message Service → Kafka → Group Handler → WS Manager → all handlers → members |
| Media upload | Compress → Asset Service → S3 → asset_id → sent as regular message |
| Last seen | App activity → Last Seen Service → Cassandra |

---

## §4 — Component Deep-Dive

### 4.1 — WebSocket Handlers
- Globally distributed — users connect to nearest node.
- Bidirectional TCP WebSocket — lightweight, no business logic.
- Two local in-memory caches:
  - **Self-cache**: users currently connected to this handler (always fresh).
  - **Routing cache**: `user_id → handler` for recent conversations (TTL 30s). Short TTL because users can reconnect to a different handler at any time.

### 4.2 — WebSocket Manager
- Central registry: which user is connected to which handler.
- Redis-backed:
  - `user:{user_id} → handler_id`
  - `handler:{handler_id} → Set[user_id]`
- Updated on every connect / disconnect / switch.
- Handler-local routing cache significantly reduces calls here.

### 4.3 — Message Service
- Cassandra-backed.
- APIs: `saveMessage()` / `getUnreadMessages()` / `updateMessageStatus()`.
- Status lifecycle: `SENT → DELIVERED → READ`.
- **WhatsApp model**: delete after delivery ACK. **Facebook model**: store permanently.

#### Why Cassandra
- 750K writes/sec — LSM-tree handles natively.
- `conversation_id` as partition key — efficient range reads per conversation.
- Linearly scalable by adding nodes.

### 4.4 — 1:1 Message Flow

**Recipient online:**
1. Handler receives message.
2. Parallel: Message Service saves → returns `message_id`. WS Manager returns recipient's handler.
3. Forwarded to recipient's handler → delivered to device.
4. Device ACKs → receipt routed back to sender via WS Manager.

**Recipient offline:**
1. Message saved with SENT status.
2. WS Manager returns no handler → flow stops.
3. On reconnect: handler queries Message Service for unread → delivers all → receipts flow back.

**Race condition (reconnect + in-flight message):**
- WS Manager returns "no handler" before new reconnect is registered.
- Fix: handlers periodically bulk-poll Message Service — "any SENT-status messages for all my active users?" Catches misses at low frequency without impacting main path latency.

### 4.5 — Offline Device Queue
- Messages composed offline stored locally in SQLite.
- Flushed to server on reconnect. Entirely client-side.

### 4.6 — Group Message Flow
1. Handler → Message Service (save to Cassandra).
2. Message Service → publishes to Kafka: `{ sender, group_id, message_id, content }`.
3. Group Message Handler (Kafka consumer):
   - Validates sender membership.
   - Gets member list from Group Service.
   - Removes sender. Bulk-queries WS Manager for handler map.
   - Sends to each handler in parallel.
4. Receipts flow back per user normally.

**Why Kafka here:** Decouples sender from fan-out. Async. Consumer scales independently.

### 4.7 — Group Service
- `(group_id, user_id, joined_at, role)` in MySQL with read replicas.
- Redis cache for member list — called on every group message.

### 4.8 — Media / Asset Service

**Upload flow:**
1. Client compresses locally.
2. Sends content hash to Asset Service: "already stored?"
3. Hit → returns existing `asset_id`. No upload. Miss → uploads to S3 → returns `asset_id`.
4. `asset_id` sent as regular message. Recipient fetches from S3/CDN directly.

**Dedup:** 5 hash algorithms — all 5 must match to confirm duplicate. Eliminates single-hash collision risk.

**CDN:** Hot assets promoted to edge nodes automatically by request frequency.

### 4.9 — User Service
- Profile data in MySQL + Redis cache (read-heavy, rarely written).

### 4.10 — Last Seen Service
- Updated on every real user action (open, close, interact). Not on background events.
- Cassandra — not MySQL (master bottleneck) or Redis (memory pressure).
- 1.6B × 1 ping/5s = **320M writes/sec** at peak — only Cassandra distributes this safely.

---

## §5 — Data Models

### Messages (Cassandra)
```
messages (
  conversation_id    UUID,        -- sorted(user_id_1, user_id_2) or group_id
  message_id         BIGINT,      -- Snowflake-style time-ordered ID
  sender_id          UUID,
  content_type       TEXT,        -- text / image / video / doc
  content            TEXT,        -- body or asset_id
  status             TEXT,        -- SENT / DELIVERED / READ
  created_at         TIMESTAMP,
  PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
```

### Groups (MySQL)
```sql
CREATE TABLE group_members (
  group_id   BIGINT NOT NULL,
  user_id    BIGINT NOT NULL,
  role       ENUM('admin','member'),
  joined_at  DATETIME NOT NULL,
  PRIMARY KEY (group_id, user_id),
  INDEX idx_user (user_id)
);

CREATE TABLE groups (
  group_id    BIGINT PRIMARY KEY,
  name        VARCHAR(255),
  created_by  BIGINT,
  created_at  DATETIME
);
```

### WebSocket Manager (Redis)
```
user:{user_id}         → handler_id          TTL: session duration
handler:{id}:users     → Set of user_ids
```

### Last Seen (Cassandra)
```
last_seen (
  user_id        UUID PRIMARY KEY,
  last_seen_at   TIMESTAMP,
  status         TEXT    -- online / offline
)
```

---

## §6 — Failure Scenarios

| Scenario | Handling |
|---|---|
| WS Handler crashes | Users reconnect; WS Manager updated; unread messages delivered on reconnect |
| Recipient offline | SENT status persisted; delivered when recipient reconnects |
| Race condition on reconnect | Periodic bulk SENT-status poll catches any missed messages |
| Group fan-out crash | Kafka replays from committed offset; message_id dedup on device handles duplicates |
| Media upload dropped | Client retries; dedup skips re-upload |
| Device offline | Local SQLite queue flushed on reconnect |
| Viral image shared by millions | Hash dedup returns existing asset_id; no re-upload |

---

## §7 — Monitoring & Scaling

**Key metrics:**
- CPU/memory on WS Handlers and Message Service.
- Disk on Cassandra nodes.
- Kafka consumer lag — add Group Message Handler instances if growing.
- Throughput and p99 latency on all services.
- WS connection count per handler — rebalance if skewed.

**Scaling strategy:**
- WS Handlers are stateful (open connections) — scale out by adding nodes; WS Manager handles routing.
- Cassandra and Kafka scale linearly by adding nodes/partitions.
- Auto-scaling via cloud provider hooks on throughput thresholds.

---

## §8 — Key Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| Real-time protocol | WebSocket (TCP) | Only option for true server-push without polling |
| Message store | Cassandra | 750K writes/sec; conversation partition key; linear scale |
| User/Group store | MySQL + Redis | ACID for membership; Redis for fast reads |
| Routing registry | Redis (WS Manager) | Sub-ms lookups; fits entirely in memory |
| Group fan-out | Kafka + consumer | Async; decouples sender; independently scalable |
| Last seen | Cassandra | 320M writes/sec — MySQL master bottleneck; Redis memory limits |
| Media | S3 + CDN | Durable; edge delivery for hot assets |
| Media dedup | 5-hash comparison | Prevents viral re-upload; collision-safe |

