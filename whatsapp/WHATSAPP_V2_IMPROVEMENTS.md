# WhatsApp / FB Messenger — V2 Improvements & Missing Design

> Covers every gap in the V1 baseline that an SDE2 interviewer will probe.
> Each section explains: what's missing, why V1 fails, and the correct fix.

---

## Summary Table

| Gap | V1 Status | V2 Fix |
|---|---|---|
| Message ordering | Partial — no ID generation spec | Snowflake IDs |
| Exactly-once delivery | Mentioned, not designed | Client-side message_id dedup |
| Group read receipts | Undefined | Per-member receipt table in Cassandra |
| Reconnect thundering herd | Not addressed | Exponential backoff + jitter on client |
| Multi-device support | Not supported | Set of handler IDs per user in Redis |
| Offline push notifications | Entirely missing | Push Gateway → APNs / FCM |
| Cassandra tombstone problem | Acknowledged, unresolved | TTL-based expiry instead of deletes |
| Conversation inbox | Missing | `user_inbox` Cassandra table |
| Rate limiting / spam | Missing | In-process token bucket per user on handler |
| Typing indicators | Missing | Ephemeral WS event, no persistence |

---

## Gap 1 — Message Ordering Not Guaranteed

### Problem
Two messages sent milliseconds apart can arrive at the recipient out of order due to network jitter, parallel handler threads, or Cassandra write race conditions. V1 has no strictly ordered ID generation.

### Why V1 Fails
`message_id` is used as a clustering key but how it is generated is never defined. Without a time-ordered globally unique ID, two writes in the same millisecond have no guaranteed order. The client would render them randomly.

### Fix — Snowflake IDs
- 41-bit timestamp (ms) + 10-bit machine ID + 12-bit sequence.
- Time-ordered globally, unique across all nodes.
- Cassandra clustering on `message_id DESC` now gives strict chronological order per conversation.
- Client renders by `message_id` score — no ambiguity.
- Deployed as a sidecar on each Message Service instance — no network hop.

---

## Gap 2 — No Exactly-Once Delivery Guarantee

### Problem
Kafka at-least-once delivery + WS Handler retries mean a message can be delivered to a device more than once. V1 mentions deduplication but never designs it.

### Fix — Client-Side Dedup Layer
- Device maintains a local set of recently seen `message_id`s (last 1,000 entries or 24h window).
- On receiving any message: check set before rendering.
  - Already present → silently discard.
  - New → render + add to set.
- Server side: WS Handlers treat duplicate delivery of the same `message_id` to the same device as a no-op.
- This is standard in all real messaging clients (Signal, WhatsApp, iMessage).

---

## Gap 3 — Group Read Receipts Undefined

### Problem
V1 defines SENT / DELIVERED / READ for 1:1 but never specifies what these mean for groups. The semantics are a non-trivial product and engineering decision.

### WhatsApp's Actual Model
- **Single tick**: saved to server.
- **Double tick**: delivered to at least one member device.
- **Blue tick (1:1)**: read by the recipient.
- **Blue tick (group)**: read by all members — shown as per-member in group receipt info screen.

### Fix — Per-Member Receipt Table

```
message_receipts (
  message_id    BIGINT,
  user_id       UUID,
  status        TEXT,        -- DELIVERED / READ
  updated_at    TIMESTAMP,
  PRIMARY KEY (message_id, user_id)
)
```

- Group Message Handler writes one receipt row per member on fan-out (status = DELIVERED).
- Each member's device ACK updates their row to READ.
- Sender queries: "are all rows for this message_id in READ status?" → blue tick.
- Stored in Cassandra — high write volume, simple partition key per message.

---

## Gap 4 — WS Handler Crash → Thundering Herd

### Problem
If a WS Handler crashes, all its connected users reconnect simultaneously — potentially thousands of concurrent reconnects flooding WS Manager and Message Service in one spike.

### Fix

**Client-side exponential backoff with jitter:**
- Base retry: 1s → 2s → 4s → 8s → max 30s.
- Jitter: ±30% random spread on each delay.
- Spreads reconnect attempts across time — eliminates the spike.

**Circuit breaker on WS Manager:**
- If Redis is overloaded, handlers fall back to stale local routing cache temporarily rather than queuing more reads.
- Prevents cascade failure from one node crash propagating to WS Manager.

---

## Gap 5 — Multi-Device Support Missing

### Problem
`user:{user_id} → handler_id` stores a single string in Redis. If the same account is online on phone + laptop + tablet, only the last registered handler is tracked. Messages only reach one device.

### Fix — Store a Set of Handlers Per User

```
user:{user_id}:handlers   →   Set{ handler_id_1, handler_id_2, handler_id_3 }
```

- On connect: `SADD user:{user_id}:handlers handler_id`.
- On disconnect: `SREM user:{user_id}:handlers handler_id`.
- WS Manager returns the full set. Message Service fans out to all handlers simultaneously.
- Each device independently ACKs delivery and read.
- READ receipt semantics: product decision — send blue tick when primary device reads, or all devices. Stored as per-device in `message_receipts`.

---

## Gap 6 — Push Notifications for Offline Users Missing

### Problem
When a user is offline, messages pile up silently in Cassandra. The user has no idea. V1 never designs the mechanism that triggers the user to open the app. Without it, offline delivery only works if the user happens to open the app organically.

### Fix — Push Notification Gateway

```
Message Service
      |
      | (recipient has no registered handler)
      v
Push Gateway Service
      |
      +--→ APNs  (iOS devices)
      +--→ FCM   (Android devices)
      +--→ Web Push (browsers via service workers)
```

- When WS Manager returns no handler for a recipient, Message Service calls Push Gateway.
- Push Gateway sends a **silent push** with `message_id` and sender preview.
- Device wakes, establishes WS connection, pulls unread messages normally.
- Push Gateway maintains `user_id → [push_token, platform]` in MySQL (updated on each app session start).
- Push tokens expire — Gateway retries with refreshed token on delivery failure.

---

## Gap 7 — Cassandra Tombstone Problem (WhatsApp Delete Model)

### Problem
V1 says "delete message after delivery ACK" but Cassandra deletes generate **tombstones** — markers that indicate deleted data. At 750K deletes/sec, tombstone accumulation causes read slowdowns, GC pressure, and can trigger read timeouts within 48 hours of sustained load.

### Fix — TTL Instead of Explicit Deletes

Write messages with a TTL at insert time:

```
INSERT INTO messages (...) VALUES (...) USING TTL 86400;  -- expires in 24h
```

- Cassandra automatically expires rows after TTL. No explicit DELETE. Zero tombstones generated for TTL expiry.
- For WhatsApp model: set a short TTL (e.g., 24h after delivery ACK updates status).
- For Facebook model: TTL = 0 (no expiry, store permanently).
- Solves the tombstone problem entirely without changing the write path.

---

## Gap 8 — Conversation Inbox / Home Screen Not Designed

### Problem
When a user opens WhatsApp, the home screen shows all recent conversations with the last message and unread count. V1 has no data structure to support this. Reconstructing it from the `messages` table would require scanning all conversations a user has ever had — a full partition scan per conversation per user.

### Fix — User Inbox Table

```
user_inbox (
  user_id           UUID,
  conversation_id   UUID,
  last_message_id   BIGINT,
  last_message_text TEXT,
  unread_count      INT,
  updated_at        TIMESTAMP,
  PRIMARY KEY (user_id, updated_at)
) WITH CLUSTERING ORDER BY (updated_at DESC)
```

- One row per conversation per user.
- Updated atomically on every new message (increment `unread_count`, update `last_message_text`).
- Reset `unread_count = 0` when user opens the conversation (READ ACK).
- Query for home screen: `SELECT * FROM user_inbox WHERE user_id = ? LIMIT 20` — instant single-partition read.

---

## Gap 9 — No Rate Limiting / Spam Protection

### Problem
A compromised account or bot can send millions of messages per second, saturating Message Service and WS Handlers. V1 has zero protection at the handler layer.

### Fix — In-Process Token Bucket Per User

Implemented directly on WS Handler (no Redis, no network hop):
- Each handler maintains an in-memory token bucket per connected `user_id`.
- Default: 60 messages/minute. Burst allowance: 10 messages in 5 seconds.
- Exceeding limit: handler drops the message and sends an error frame back to client.
- Cost: sub-microsecond check per message — negligible at any scale.
- Resets to zero when user disconnects (bucket destroyed).

---

## Gap 10 — Typing Indicators Not Designed

### Problem
"User is typing..." is a fundamental WhatsApp UX feature not addressed in V1.

### Fix — Ephemeral WebSocket Event (No Persistence)

Typing indicators are transient — they serve no purpose once the user stops typing.

**Flow:**
1. Client sends `TYPING_START` frame through WebSocket.
2. WS Handler looks up recipient's handler via local routing cache (or WS Manager).
3. Forwards `TYPING_START` event directly to recipient's handler → pushed to device.
4. No Message Service call. No Cassandra write.
5. Auto-expires: if no subsequent `TYPING_START` received within 5 seconds, recipient device hides the indicator.
6. `TYPING_STOP` event explicitly clears it.

**Why no persistence:** Typing state is meaningless after the fact. Storing it would waste ~750K extra writes/sec (one per typed character) with zero benefit.

---

## Revised Architecture After V2 Improvements

```
+-----------------------------------------------+
|               USER DEVICES                    |
|  Multi-device: phone + laptop + tablet        |
+------------------+----------------------------+
                   | WebSocket + backoff/jitter on reconnect
                   v
     +----------------------------+
     |   Load Balancer / API GW   |
     +---+--------------------+---+
         |
         v
+---------------------------+      +------------------+
| WebSocket Handlers        |      | Message Service  |
| - token bucket per user   |      | Cassandra        |
| - ephemeral typing events |      | TTL-based expiry |
| - per-device delivery     |      +--------+---------+
+------------+--------------+               |
             |                      Kafka (groups)
             v                              |
+---------------------------+    +----------v----------+
| WebSocket Manager         |    | Group Message       |
| Redis:                    |    | Handler             |
| user → Set[handler_ids]   |    | (fan-out + per-     |
| (multi-device support)    |    |  member receipts)   |
+---------------------------+    +---------------------+

+------------------+   +------------------+   +------------------+
| Push Gateway     |   | User Inbox       |   | message_receipts |
| APNs / FCM /     |   | Cassandra table  |   | Cassandra table  |
| Web Push         |   | (home screen)    |   | (group receipts) |
+------------------+   +------------------+   +------------------+
```

