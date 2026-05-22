# WhatsApp / FB Messenger — System Design (SDE 2 Interview Version)

**Goal:** Design a real-time chat application (WhatsApp/FB Messenger scale) supporting 1:1 messaging, group chat, media sharing, read receipts, and last-seen status at ~2B users / 65B messages per day.

---

## §1 — Requirements & Scope

### Functional Requirements

1. **1:1 Chat** — user can send a text/media message to another user.
2. **Group Chat** — send a message to a group; all group members receive it.
3. **Media messages** — text, images, videos, documents.
4. **Read receipts** — single tick (sent), double tick (delivered), blue tick (read).
5. **Last seen** — show the last time a user was active on the platform.

### Non-Functional Requirements

| Property | Target |
|---|---|
| Latency | Very low — must feel real-time |
| Availability | 99.99% — system must not go down |
| Message lag | Near zero — no acceptable lag like other async systems |
| Scale | 2B users, 1.6B MAU, 65B messages/day |
| Offline support | Messages must be delivered when recipient comes online |

### Out of Scope

- End-to-end encryption internals (acknowledged but not designed).
- Authentication and authorization details.
- Analytics and content moderation pipelines (briefly mentioned).

---

## §2 — Scale Estimation

```
Total users:               2,000,000,000
Monthly active users:      1,600,000,000
Messages per day:          65,000,000,000
Messages per second (avg): ~750,000

Assume avg message size:   ~1 KB (text + metadata)
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
| (keeps open    |     |                  |
|  connections   |     |                  |
|  with users)   |     |                  |
+-------+--------+     +--------+---------+
        |                       |
        |   +-------------------+
        v   v
+-------------------+        +------------------+
| WebSocket Manager |        | Message Service  |
|                   |        |                  |
| Redis:            |        | Cassandra        |
|  user → handler   |        | (msg store)      |
|  handler → users  |        |                  |
+-------------------+        +------------------+
                                      |
                              Kafka (group msgs)
                                      |
                             +------------------+
                             | Group Message    |
                             | Handler          |
                             | (Kafka consumer) |
                             +--------+---------+
                                      |
                              Group Service
                              (MySQL + Redis)

+------------------+     +------------------+     +------------------+
| User Service     |     | Asset Service    |     | Last Seen        |
| MySQL + Redis    |     | S3 + CDN         |     | Service          |
|                  |     |                  |     | Cassandra        |
+------------------+     +------------------+     +------------------+

+--------------------------------------------------+
| Analytics Pipeline                               |
| Kafka → Spark Streaming → Hadoop                 |
+--------------------------------------------------+
```

---

### 3.2 Key Data Flows Summary

| Flow | Path |
|---|---|
| Send 1:1 message (online recipient) | Sender → WS Handler 1 → Message Service (save) + WS Manager (find handler) → WS Handler 2 → Recipient |
| Send 1:1 message (offline recipient) | Sender → WS Handler 1 → Message Service (save) → no handler found → message waits → delivered on recipient reconnect |
| Read receipt flow | Recipient device ACKs → WS Handler 2 → WS Manager (find sender's handler) → WS Handler 1 → Sender |
| Group message | Sender → WS Handler → Message Service → Kafka → Group Message Handler → WS Manager → all member handlers → members |
| Media upload | Client compress → Asset Service → S3 → returns asset_id → asset_id sent as regular message |
| Last seen update | Every app activity event → Last Seen Service → Cassandra |

---

## §4 — Component Deep-Dive

---

### 4.1 — WebSocket Handlers

- Thousands of stateful servers distributed globally — users connect to the geographically nearest one.
- Maintain open **bidirectional TCP WebSocket connections** with all currently active users.
- Are intentionally **lightweight** — no business logic. Their only job: receive a message from a user and route it to the right destination handler.
- Each handler keeps two local in-memory caches:
  - **Self-cache**: list of all users currently connected to itself (always fresh).
  - **Short-lived routing cache**: `user_id → handler` mapping for recent conversations (TTL: ~30 seconds). Prevents repeat calls to WebSocket Manager for the same conversation. TTL is short because users can reconnect to a different handler at any time (especially across geographies).

---

### 4.2 — WebSocket Manager

- Central registry: **which user is connected to which handler**.
- Backed by **Redis** with two types of entries:
  - `user:{user_id} → handler_id` (for routing messages to a user)
  - `handler:{handler_id} → [user_id, ...]` (for knowing who a handler serves)
- Updated every time a user connects, disconnects, or switches handlers.
- Queried by handlers to find where to forward a message.
- The short-lived handler-local cache reduces load on this service significantly.

---

### 4.3 — Message Service

- Owns all message storage and retrieval.
- Backed by **Cassandra**.
- Key APIs:
  - `saveMessage(from, to, content)` → returns `message_id`
  - `getUnreadMessages(user_id)` → used when a user comes online
  - `updateMessageStatus(message_id, status)` → sent / delivered / read
- Message status lifecycle: `SENT → DELIVERED → READ`
- **WhatsApp model**: delete message from DB once delivery ACK received (messages not stored permanently). Cassandra deletes are slightly inefficient — an alternative store could be used for this pattern.
- **Facebook model**: store messages permanently. Cassandra works well here.
- For group messages: message stored once; status tracked per recipient.

#### Why Cassandra
- 65B messages/day = ~750K writes/sec. Cassandra's LSM-tree write path handles this natively.
- Query pattern (get messages by `user_id` or `message_id`) maps well to Cassandra partition keys.
- Horizontally scalable without complex sharding logic.

---

### 4.4 — 1:1 Message Flow (Detailed)

**Recipient online:**
1. Sender's WS Handler receives the message.
2. Parallel calls:
   - Message Service → saves message to Cassandra, returns `message_id`.
   - WS Manager → returns which handler the recipient is connected to.
3. Sender's handler forwards message to recipient's handler.
4. Recipient's handler delivers to device.
5. Device ACKs (delivered or read) → recipient's handler queries WS Manager for sender's handler → sends receipt back to sender.

**Recipient offline:**
1. Message saved to Cassandra via Message Service.
2. WS Manager returns no handler for recipient → flow stops here.
3. When recipient comes online and connects to a handler:
   - Handler immediately queries Message Service: "any unread messages for this user?"
   - All pending messages delivered.
   - Receipts flow back through normal path.

**Race condition — recipient comes online at the exact moment a message is being saved:**
- WS Manager returns "no handler" (checked before reconnect registered).
- Recipient's new handler pulls unread messages — but the in-flight message may not be saved yet.
- **Solution**: handlers periodically poll Message Service with a bulk query: "any SENT-status messages for all my active users?" — low-frequency sweep catches any missed messages without adding latency to the main path.

---

### 4.5 — Offline Device Handling

- All messages composed while offline are stored in a **local on-device database** (e.g., SQLite on Android).
- When internet is restored, the device flushes the local queue and sends all pending messages through the WebSocket connection.
- This is entirely client-side — no server involvement until connectivity is restored.

---

### 4.6 — Group Message Flow

Group messages use a fan-out-via-Kafka pattern to keep WebSocket Handlers lightweight:

1. Sender's WS Handler receives the group message.
2. Sends it to **Message Service** → saved to Cassandra, `message_id` returned.
3. Message Service publishes event to **Kafka** topic: `{ sender, group_id, message_id, content }`.
4. **Group Message Handler** (Kafka consumer):
   - Validates sender is a member of the group.
   - Queries **Group Service** to get all group member `user_id`s.
   - Removes sender from list.
   - Bulk-queries WS Manager: "which handlers serve these users?"
   - Gets back a map of `handler → [user_ids]`.
   - Sends messages to each handler in parallel.
5. Individual handlers deliver to their connected users.
6. Receipts flow back per user through the normal path.

**Why Kafka here:**
- Decouples the sender's WS Handler from the fan-out blast.
- Group membership can be large — fan-out is async, doesn't block the sender's response.
- Kafka consumer can scale independently.

---

### 4.7 — Group Service

- Maintains `group_id ↔ user_id` many-to-many membership.
- Stores: `(group_id, user_id, joined_at, role)` — role = admin / member.
- Backed by **MySQL** (geographically distributed, read replicas for scale).
- **Redis cache** on top for `GET /groups/{group_id}/members` — the most frequent query (Group Message Handler calls this on every group message).

---

### 4.8 — Media / Asset Service

**Upload flow:**
1. Client compresses image/video locally before upload.
2. Client computes a hash of the content and sends **hash-only** to Asset Service first: "do you already have this asset?"
3. If asset exists (deduplication hit): Asset Service returns existing `asset_id` — no upload needed.
4. If not: client uploads the file to Asset Service → stored in **S3**.
5. Asset Service returns `asset_id`.
6. Client sends `asset_id` as a regular message through WebSocket flow.
7. Recipient receives `asset_id` and fetches media from S3/CDN directly.

**Deduplication strategy:**
- Computing a single hash risks collision.
- Use **5 different hash algorithms** simultaneously — all 5 must match for an asset to be considered a duplicate.
- This makes false deduplication practically impossible.

**CDN optimization:**
- Hot assets (viral images, news event clips shared by millions) are promoted to **CDN edge nodes** automatically based on request frequency.
- CDN can be replicated to multiple regions if traffic is geographically spread.

---

### 4.9 — User Service

- Owns user profile data: `user_id`, `name`, `profile picture`, `preferences`.
- Backed by **clustered MySQL**.
- **Redis cache** in front — profile data is read-heavy, written rarely.

---

### 4.10 — Last Seen Service

- Tracks the last time each user was active on the platform.
- App events (open app, close app, any in-app interaction) trigger an update.
- **Not** triggered by background app activity (e.g., auto-connecting WebSocket on network restore).
- Backed by **Cassandra** — not MySQL or Redis.

#### Why Cassandra here (not MySQL or Redis)
- App pings last-seen every ~5 seconds per active user.
- 1.6B MAU × 1 ping/5s = **320M writes/sec** at peak.
- MySQL: all writes go to master node — will not survive this throughput.
- Redis: memory pres sure at this key volume + high update rate = scaling issues.
- Cassandra: writes are distributed across all nodes via consistent hashing — designed exactly for this write pattern.

---

## §5 — Data Models

### 5.1 — Messages (Cassandra)

```
messages (
  conversation_id    UUID,      -- (user_id_1, user_id_2) sorted, or group_id
  message_id         BIGINT,    -- time-ordered ID (Snowflake or similar)
  sender_id          UUID,
  content_type       TEXT,      -- text / image / video / doc
  content            TEXT,      -- text body or asset_id
  status             TEXT,      -- SENT / DELIVERED / READ
  created_at         TIMESTAMP,
  PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
```

### 5.2 — Groups (MySQL)

```sql
CREATE TABLE group_members (
  group_id    BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  role        ENUM('admin', 'member'),
  joined_at   DATETIME NOT NULL,
  PRIMARY KEY (group_id, user_id),
  INDEX idx_user (user_id)        -- "which groups is this user in?"
);

CREATE TABLE groups (
  group_id    BIGINT PRIMARY KEY,
  name        VARCHAR(255),
  created_by  BIGINT,
  created_at  DATETIME
);
```

### 5.3 — WebSocket Manager (Redis)

```
Key:   user:{user_id}
Type:  String
Value: handler_id (IP/hostname of the WebSocket Handler)
TTL:   session duration (cleared on disconnect)

Key:   handler:{handler_id}:users
Type:  Set
Value: {user_id, user_id, ...}
TTL:   none (managed by connect/disconnect events)
```

### 5.4 — Last Seen (Cassandra)

```
last_seen (
  user_id        UUID PRIMARY KEY,
  last_seen_at   TIMESTAMP,
  status         TEXT    -- online / offline
)
```

---

## §6 — Failure Scenarios & Edge Cases

| Scenario | Handling |
|---|---|
| WS Handler crashes | Users reconnect to a different handler; WS Manager updated; messages waiting in Message Service delivered on reconnect |
| Recipient offline when message sent | Message stored in Cassandra with SENT status; delivered when recipient reconnects and queries unread messages |
| Race condition: message in-flight while recipient reconnects | Periodic bulk poll by handlers: "any SENT-status messages for my active users?" catches missed messages |
| Group message fan-out failure mid-way | Kafka consumer retries from last committed offset; delivery is idempotent (duplicate message → dedup by message_id on device) |
| Media upload dropped mid-way | Client retries upload; dedup check prevents duplicate storage |
| Offline device composes messages | Stored in local SQLite; sent when connectivity restored |
| Same image shared by millions simultaneously | Hash-based dedup: Asset Service detects existing asset, returns `asset_id` without re-uploading |

---

## §7 — Monitoring & Scaling

**Key metrics to track:**
- CPU / memory utilization on WebSocket Handlers and Message Service.
- Disk utilization on Cassandra nodes.
- Kafka consumer lag — if lag increases, add more Group Message Handler instances.
- Throughput and p99 latency on all web services.
- WebSocket connection count per handler — rebalance if skewed.

**Scaling strategy:**
- All services are stateless except WebSocket Handlers (stateful due to open connections). Scale out Handlers by adding nodes; WS Manager handles routing transparently.
- Cassandra scales linearly by adding nodes — no resharding needed.
- Kafka scales by adding partitions + consumers.
- Auto-scaling hooks: monitor throughput → trigger node addition via cloud provider auto-scaling groups.

---

## §8 — Key Design Decisions Summary

| Decision | Choice | Reason |
|---|---|---|
| Real-time messaging protocol | WebSocket (bidirectional TCP) | Only protocol that supports server-push without polling |
| Message store | Cassandra | 750K writes/sec; partition-key access by conversation; linear horizontal scale |
| User/Group store | MySQL + Redis | Low write volume; ACID for group membership; Redis for fast read path |
| Routing registry | Redis (WebSocket Manager) | Sub-ms lookups; simple key-value structure; fits entirely in memory |
| Group fan-out | Kafka + Group Message Handler | Decouples sender from fan-out blast; scales independently; handles large groups |
| Last seen store | Cassandra | ~320M writes/sec from app heartbeats; MySQL master would be overwhelmed; Redis has memory pressure at this scale |
| Media storage | S3 + CDN | Durable, scalable object store; CDN reduces origin load for hot assets |
| Media deduplication | 5-hash comparison | Prevents re-uploading same viral content; multi-hash minimizes collision risk |

