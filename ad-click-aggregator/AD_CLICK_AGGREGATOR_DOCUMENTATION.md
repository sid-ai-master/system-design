# Ad Click Aggregator System Design - Complete Documentation

## 1. Introduction & Context

This is a system design for a **metrics counting platform for advertisements**. The focus is on tracking ad clicks and providing advanced analytics to content creators.

This is essentially a continuation/extension of the Tiny URL system design, where the core difference is handling analytics queries instead of just URL redirects.

---

## 2. Functional Requirements

### Primary Requirement:
- **Track every single ad click** - Every click must be captured and recorded
- **Support arbitrary analytical queries** - Users should be able to run complex analytical queries on the click data
- **Return insights as fast as possible** - Queries should execute with minimal latency

---

## 3. Non-Functional Requirements

### Requirement 1: Time-Series Query Performance
- Must be able to query time-series click metrics on advertisements
- **Granularity**: Per-minute intervals
- **Performance**: Queries should return results extremely quickly (sub-second ideally)
- **Use case**: Show click breakdowns by minute for an ad, campaign, or advertiser

### Requirement 2: Data Liveness
- **Target latency**: Within a few seconds
- **Definition**: Within seconds of a user clicking an ad, that click should be reflected in query results
- **Challenge**: Balancing fast queries with data freshness

---

## 4. Scale & Metrics Calculations

### Traffic Volume:
```
Base metric: 10 billion clicks per month (from Tiny URL problem)

Per-second breakdown:
- 10 billion clicks/month ÷ (30 days × 24 hours × 3600 seconds/hour)
- ≈ 3,858 clicks/second
- Rounded: ~4,000 clicks/second
```

### Data Size Per Click:
```
Fields tracked per click:
- Ad ID
- Ad Campaign ID
- User ID
- IP Address
- User device information
- Page URL (where ad was clicked)
- Timestamp
- Additional metadata

Size estimate: Few hundred bytes (safely capped at 1 KB per click)
```

### Monthly Data Production:
```
Per-second: 4,000 clicks/sec × 1 KB/click = 4 MB/second

Per-month: 4 MB/sec × 2,592,000 seconds/month = 10 TB/month
(where 2,592,000 = 30 days × 24 hours × 3600 seconds)
```

### Storage Capacity:
- Modern servers can comfortably store a few hundred terabytes
- **Conclusion**: 10 TB/month doesn't require object storage; can use database nodes
- **Would need object storage if**: Producing many petabytes per month

---

## 5. High-Level Architecture Flow

```
User clicks ad
    ↓
Load Balancer (Round-Robin)
    ↓
Application Server (Stateless)
    ↓
Duplicate Click Detection (Check transactional DB)
    ↓
If NOT malicious → Kafka (High-throughput message queue)
    ↓
Kafka Consumers (Read batches, convert to Parquet)
    ↓
Data Warehouse (OLAP system for analytics)
    ↓
User Queries (Fast analytical results)
```

### Load Balancer:
- **Strategy**: Round-robin
- **Rationale**: All application servers are completely stateless
- **Benefit**: Simple, even distribution

### Application Servers:
- Stateless design enables horizontal scaling
- Primary responsibilities:
  1. Receive click request
  2. Check for duplicate/malicious clicks
  3. Forward valid clicks to Kafka

---

## 6. Why NOT Traditional Transactional Databases (DynamoDB)

### The Problem:
Many engineers initially propose using transactional databases like DynamoDB for this because:
- Can write clicks as they're created
- Easy to query them back

### Why This Fails:

#### Write Throughput Limitation:
```
DynamoDB single partition capacity: 1 MB/second
Our load: 4 MB/second

Immediately exceeds capacity
```

#### The Celebrity Problem:
```
Assumption: All ads are clicked equally
Reality: If one viral ad (e.g., Karina Cobb's free OnlyFans ad) gets advertised, it could generate majority of traffic

This violates the "equal distribution" assumption
```

#### Partition Strategies & Their Issues:

**Strategy 1: Random Suffix on Ad ID**
```
Example: ad_id_12345 → ad_id_12345_<random_suffix>

Problem: When aggregating data back, must query ALL partitions to get total clicks for an ad
Result: Slow queries despite successful writes
```

**Strategy 2: Round-Robin to Partitions**
```
Write: Click goes to random partition (load balanced)

Problem: Must read from ALL partitions to fetch all clicks for a given ad
Result: Slow queries despite successful writes
```

### Conclusion:
Both solutions create a **poor trade-off**: either poor write scalability or poor read performance.

---

## 7. Solution: Apache Kafka

### Why Kafka Works:

#### 1. Throughput Capability:
```
Kafka topic partition capacity: 50+ MB/second

Our load: 4 MB/second → Only uses ~8% of one partition capacity
No celebrity problem worry (even if all clicks go to one ad, still below limit)
Can easily handle 10x more traffic before hitting partition limits
```

#### 2. Log-Based Architecture:
```
Data structure: Append-only log on disk
Disk head movement: Sequential only (not random)
Result: Extremely fast writes
```

#### 3. Durability Configuration:
```
Kafka Settings for Data Safety:
- min.insync.replicas: 3
  → Data replicated to 3 copies

- Replication requirement: 1 copy before message valid
  → Message considered persisted after writing to 1 replica

- Leader failover protection:
  → Never fail over to replica that isn't completely up-to-date
  → Ensures no data loss during server failures

Performance Impact:
- Replication happens in BATCHES
- Greatly amortizes replication cost
- Write throughput remains high
```

#### 4. Natural Fit for This Problem:
- Consumer groups handle failover automatically
- Offset management tracks processed data
- Scales horizontally with partitions

---

## 8. Data Warehouse (OLAP System)

### What is a Data Warehouse?

Analytical database optimized for expensive SQL queries, not fast transactional reads/writes.

### Key Difference: Columnar vs Row-Based Storage

#### Row-Based Storage (Traditional DB):
```
Table structure:
Row 1: [ad_id] [campaign_id] [user_id] [ip] [device] [timestamp]
Row 2: [ad_id] [campaign_id] [user_id] [ip] [device] [timestamp]
Row 3: [ad_id] [campaign_id] [user_id] [ip] [device] [timestamp]

Storage on disk: Rows stored sequentially
Query for "clicks by ad_id": Must read entire rows, even if need only ad_id + timestamp
```

#### Columnar Storage (Data Warehouse):
```
Column storage layout:
[ad_id_col]        [campaign_id_col] [user_id_col]    [ip_col]          [device_col]  [timestamp_col]
ad_1, ad_2, ad_3   camp_1, camp_2   user_1, user_2   ip_1, ip_2, ip_3  dev_1, dev_2  ts_1, ts_2, ts_3

Query for "clicks by ad_id": Only read ad_id_col + timestamp_col (skip everything else)
```

### Benefits of Columnar Storage:

#### 1. Reduced I/O:
- Analytical queries typically access subset of columns
- Only read columns needed
- Dramatically fewer bytes read from disk
- **Result**: Much faster queries

#### 2. Better Compression:
```
Example:
- Row-based: Diverse values in each column → Poor compression ratio
- Column-based: Similar values in same column
  
Real data example:
timestamp column: [2024-01-01 10:00:00, 2024-01-01 10:00:01, 2024-01-01 10:00:02, ...]
→ High similarity → Excellent compression (2-10x reduction)
```

#### 3. CPU Optimization:
- Operating on column batches → Better CPU cache locality
- SIMD instructions can parallelize operations on similar data types
- **Result**: Faster processing

### Data Warehouse Options:

#### Cloud-Offered Solutions:
- BigQuery (range-based partitioning only)
- Snowflake
- Redshift

#### Open-Source Options:
- ClickHouse
- Apache Druid

#### Storage Options:
- Some use object stores (S3, GCS, Azure Blob) for data
- Others use local discs on query computation nodes

### Alternative: Time-Series Databases
- Great choice for ad click data (inherent time-series nature)
- Caveat: Not all support arbitrary SQL queries
- Using generic OLAP warehouse avoids this limitation

---

## 9. Deduplication Strategy

### The Problem:
Malicious users might click the same ad multiple times from the same site to earn fraudulent revenue.

### The Solution:

#### Fields Used for Deduplication:
```
Hash these fields to create an idempotency key:
1. Page URL (where ad was clicked from)
2. IP Address (user's location)
3. Ad ID (which ad was clicked)
4. Timestamp (when user entered the page)

Hash function: Any consistent hash (MD5, SHA256, etc.)
Result: Unique fingerprint per user-click-page combination
```

#### Where Deduplication Happens:
```
Application Server receives click
    ↓
Compute hash of: [page_url, ip, ad_id, timestamp]
    ↓
Check Duplicate Click Database (Transactional DB)
    ↓
Hash found? → Duplicate, drop click
Hash NOT found? → Insert hash, forward to Kafka
```

#### Why This Approach Works:

**Database Type: Transactional DB**
- Fast lookup by primary key (hash)
- Handles inserts efficiently
- Can scale horizontally

**Hash Distribution:**
- Hashes evenly distributed across keyspace
- No hotspot shards
- Automatic load balancing

**IP-Based Rate Limiting (Extra Protection):**
- If malicious user spam-clicks one ad
- IP-based rate limiter stops them BEFORE reaching duplicate check
- Protects from celebrity problem at source

### Offset Tracking in Warehouse:

To handle Kafka consumer failures safely:
```
Persist with each row:
- Kafka partition number
- Kafka offset

On consumer restart:
- Query data warehouse for highest offset from this partition
- Drop data from Kafka until offset exceeds warehouse offset
- Prevents duplicate insertions
```

---

## 10. Partitioning Strategy

### The Critical Question:
How to partition both Kafka AND data warehouse to optimize queries?

### Key Insight:
**Kafka partitions should match data warehouse partitions**

### Partitioning Based on: Ad ID

```
Example:
ad_id_1     → Kafka partition 0 → Data warehouse partition 0
ad_id_2     → Kafka partition 1 → Data warehouse partition 1
ad_id_3     → Kafka partition 2 → Data warehouse partition 2
ad_id_4     → Kafka partition 0 → Data warehouse partition 0
(ad_id_4 % 3 = 0, so same partition as ad_id_1)

Partitioning method: hash(ad_id) % num_partitions
Alternative methods: Campaign ID, Advertiser ID, Range-based
```

### Why Match Partitions?

#### Problem with Mismatched Partitions:
```
If Kafka has 5 partitions, Data warehouse has 10 partitions:

Kafka consumer 0 → Data warehouse partition 0-9 (all partitions)
Result: Small batches of data to each warehouse partition
→ Small files (less efficient compression, more metadata overhead)
```

#### Solution: Same Partition Count & Strategy:
```
Kafka has 10 partitions, Data warehouse has 10 partitions:
(Both use hash(ad_id) % 10)

Kafka consumer 0 → Warehouse partition 0 only
Kafka consumer 1 → Warehouse partition 1 only
...
Kafka consumer 9 → Warehouse partition 9 only

Result: Each consumer sends ALL data for their partition
→ Large consolidated batches
→ Better compression
→ Fewer files = less overhead
```

### Benefits:

#### 1. Data Pruning for Queries:
```
Query: "Get all clicks for ad_id_12345"

Location of data: partition_hash(ad_id_12345) % num_partitions = partition_3
→ Read ONLY partition_3
→ Skip all other partitions
→ Massive I/O reduction
```

#### 2. Efficient Batch Ingestion:
```
Parquet file sizing:
- One Kafka partition → One data warehouse partition
- Each consumer batches its entire partition load
- Example: 2-5 minute batch intervals
- Result: 256 MB - 1 GB consolidated files (optimal size)
```

### Alternative Partitioning Schemes:

All following same logic:
- Campaign ID-based partitioning
- Advertiser ID-based partitioning
- Range-based partitioning (e.g., ad_ids 1-1000, 1001-2000, etc.)

**Decision factor**: Which queries are most common?
- If "clicks per ad" is most common → Partition on ad_id
- If "clicks per campaign" is most common → Partition on campaign_id
- etc.

---

## 11. Query Optimization: Materialized Views

### The Problem:
As data accumulates over time, running aggregation queries gets slower.

```
Query: "Show me clicks per minute for ad_id_12345 over the last 30 days"
Data scanned: 30 days × 1440 minutes × (all rows per minute)
= Expensive operation as data grows
```

### The Solution: Pre-Aggregation (Materialized Views)

#### Setup:

**Assumption**: Client's minimum granularity request = per-minute intervals

#### Daily Batch Job:
```
Time: 3 AM daily (off-peak)

Process:
1. Read all raw click data from previous day
2. Aggregate clicks into 1-minute buckets
3. Group by ad_id
4. Sort by timestamp within each ad_id
5. Persist to new table: "minute_data"

Table schema:
[ad_id] [timestamp_minute] [click_count] [other_agg_fields]

Example:
ad_1, 2024-01-01 10:00, 1523,  ...
ad_1, 2024-01-01 10:01, 1687,  ...
ad_2, 2024-01-01 10:00, 892,   ...
```

#### Problem with Materialized View Alone:
```
Data liveness issue:
- Only aggregated yesterday's data
- Today's clicks NOT in minute_data table
- User queries see 24-hour-old data
→ Violates 2-second liveness requirement
```

#### Solution: Hybrid Query Approach

```
Query receives request: "clicks per minute for ad_1 from last 24 hours"

Step 1: Query minute_data table
- Fetch yesterday's data (already aggregated, very fast)
- Result: pre-computed aggregations

Step 2: Query raw click data (today only)
- Run ad-hoc aggregation on TODAY'S clicks only
- Scope: Limited to 1 day of data → Very fast
- Result: Current day aggregation

Step 3: Combine results
- Return: Yesterday's pre-agg + Today's ad-hoc
- User sees full 24-hour view with recent clicks included

Performance: Sub-second response (yesterday cached, today limited scope)
```

### Optimization: Clustering/Sorting

```
Warehouse table clustering/sorting:
- Sort by: ad_id, then timestamp
- Benefits:
  1. Range queries by ad_id→timestamp are contiguous on disk
  2. Partition elimination: Skip blocks for other ad_ids
  3. Better compression: Timestamps are sequential
```

---

## 12. Scaling Considerations

### Question: Can We Handle All Data in One Partition?

```
Single partition capacity: 50 MB/sec
Our load: 4 MB/sec
→ Yes, theoretically manageable

But interviewer asks: "How would you scale this out?"
→ Need to prepare scaling strategy
```

### Scaling Kafka Partitioning:

#### Why Partition Kafka?
```
Reasons:
1. Distribute write load across multiple brokers
2. Read parallelization (multiple consumers)
3. Fault tolerance (data spread across machines)
4. Enable consumer group parallelism
```

#### Options for Partitioning:

**Option 1: Round-Robin Partitioning**
```
Each click goes to random partition (load-balanced)
Pros:
- Perfect load balancing
- No hot partitions

Cons:
- To fetch all clicks for one ad, must READ all partitions
- Expensive queries
```

**Option 2: Hash-Based Partitioning (RECOMMENDED)**
```
Partition = hash(ad_id) % num_partitions
Pros:
- All clicks for ad_id_X go to same partition
- Fast aggregation queries (read one partition)
- Better cache locality

Cons:
- Potential hotspots if one ad is viral (mitigated by partition count)
```

**Option 3: Manual Assignment**
```
Application code assigns partition explicitly
Pros:
- Full control

Cons:
- More complex, error-prone
```

#### Our Recommendation: Hash-Based
```
Reasoning:
- Single Kafka partition: 50 MB/sec capacity
- Our load: 4 MB/sec
- Even if entire load goes to one ad:
  - Still 12x under partition limit
  - Could vertically scale brokers if needed
- Benefits of hash-based outweigh risks
- IP-based rate limiting as extra protection
```

### Kafka Consumer Groups (Failover)

#### Setup:
```
Multiple Kafka consumers in same consumer group
Configuration: consumer.group.id = "click-aggregator-group"

Kafka automatically handles:
- Partition assignment to consumers
- Rebalancing when consumer joins/leaves
- Failover if consumer dies
```

#### Failover Process:
```
Initial state:
Consumer_1 → Partitions [0, 1, 2]
Consumer_2 → Partitions [3, 4, 5]

Consumer_1 dies:
Kafka detects failure (via heartbeat timeout)
Partitions [0, 1, 2] unassigned

Rebalancing:
Consumer_2 takes over partitions [0, 1, 2, 3, 4, 5]
OR brings up Consumer_3 to share load

Key point: No partitions drop data, no duplicates (if offset handling correct)
```

#### Offset Management:

```
After successful write to warehouse:
consumer.commitOffset(partition, offset)
Stored in Kafka broker (or external store)

If consumer crashes before commit:
- New consumer reads last committed offset
- Starts from there
- May re-read and re-insert some data (edge case)
```

#### Handling Duplicate Inserts:

```
Scenario:
1. Consumer reads rows from offset 1000-1010, writes to warehouse
2. Consumer crashes before committing offset 1010
3. New consumer starts from last committed offset (999)

Risk: Will re-read and re-insert 1000-1010 (duplicates in warehouse)

Mitigation Strategy:
1. Store Kafka partition & offset with each inserted row
2. On restart, query warehouse: max(offset) for this partition
3. In consumer, drop data until current offset > max(offset) in warehouse
4. Continue processing from there

Result: Prevents duplicate insertions despite failures
```

---

## 13. File Sizing & Batch Processing

### Why Batch Matters:

#### Problem with Small Files:
```
Query engine overhead:
- Must open/close each file
- Update metadata for each file
- Compact small files in background
- More file open/close operations = More CPU/I/O

Compression:
- Small files compress worse than large files
- Compression dictionary less effective
- Higher bytes-on-disk ratio

Network overhead:
- Transferring many small files slower than few large files
```

#### Solution: Consolidate into Large Batches:

```
Benefits:
1. Fewer files → Less metadata overhead
2. Better compression → Smaller storage
3. Fewer compactions → Lower compute cost
4. Faster query planning → Less overhead
```

### Parquet File Size Targets:

```
Industry standard: 256 MB to 1 GB per file

Our calculation:
Write speed: 4.1 kB/sec (4,000 clicks × 1 KB/click)
Wait time: 10 minutes (to allow batch accumulation)

Raw size in 10 min:
4.1 kB/sec × 600 sec = 2.46 MB

After Avro→Parquet conversion:
2-10x compression ratio (typical)
Best case: 2.46 MB / 2 = 1.23 MB
Typical case: 2.46 MB / 5 ≈ 500 KB

Actual target: Flush every 10 minutes
Why: Reasonable balance between:
- File size optimality (not too small, not pushing quota)
- Data liveness (fresh data every 10 min)
```

### The Trade-off:

```
With 10-minute flush interval:
- Users don't see new clicks for up to 10 minutes
- Requires mitigations for data liveness requirement
- Balanced with efficient file sizing and query performance
```

---

## 14. Data Liveness: Multiple Approaches

### Approach 1: Batch Processing (Base Case)

**Implementation**:
- Flush to data warehouse every 10 minutes
- Data available after 10-minute delay

**Pros**:
- Optimal file sizes
- Best compression
- Best query performance
- Simplest implementation

**Cons**:
- 10-minute latency violates 2-second liveness requirement
- Not suitable if real-time data critical

---

### Approach 2: Real-Time Row Insertion

**How it Works** (using Log-Structured Merge Tree):

```
Incoming data from Kafka:
- Rows arrive individually or in small batches
- Format: Row-based (not columnar)
- Buffered in memory or as row file on disk
- Available for querying IMMEDIATELY

Query Engine:
- Queries read columnar files (main data)
- Plus buffered row data (recent data)
- Combines results
- Small buffered dataset doesn't hurt perf much

Compaction (background job):
- Accumulates buffered data
- After threshold (e.g., 10 minutes):
- Convert to columnar parquet format
- Integrate into main table
- Delete buffer file

Example: Apache Hudi table format**
```

**Hudi Workflow Example**:
```
Kafka → Hudi (row-based inserts every few seconds)
                    ↓ (available for queries immediately)
Background job (every 10 min):
                    ↓ (Spark job)
Compact Avro files → Large Parquet file (1 GB)
                    ↓
Add to Hudi table

Benefits:
- Data available in seconds (Hudi rows)
- Queries see recent + old data combined
- Eventually converted to optimal format (Parquet)
```

**Pros**:
- Meets 2-second liveness requirement
- Can incrementally merge into columnar format
- Many modern warehouses support this

**Cons**:
- More complex implementation
- Row-based format slower for analytics queries
- Extra compaction overhead
- Higher ingestion pipeline complexity

---

### Which Approach to Choose?

```
If liveness not critical: Use batching approach (simpler, faster queries)
If liveness critical: Use real-time insertion + compaction (more complex)

In real world:
- Most companies use hybrid: batch for cold data, real-time buffer for hot data
- Depends on SLA requirements and infrastructure maturity
```

---

## 15. Complete Data Flow Summary

```
┌─────────────────────────────────────────────────────────────┐
│ 1. USER CLICKS AD                                           │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. LOAD BALANCER (Round-Robin)                              │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. APPLICATION SERVER (Stateless)                           │
│    - Receive click request                                  │
│    - Extract: page_url, ip, ad_id, timestamp               │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. DUPLICATE CHECK (Transactional DB)                       │
│    - Hash: MD5(page_url + ip + ad_id + timestamp)          │
│    - Look up in duplicate database                          │
│    - If found: Drop (duplicate)                             │
│    - If not found: Insert hash, continue                    │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. KAFKA CLUSTER (Message Queue)                            │
│    - Topic partition determined by: hash(ad_id) % partitions│
│    - Message persisted to 3 replicas                        │
│    - Durability: min.insync.replicas=1                      │
│    - Batches in memory for efficiency                       │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. KAFKA CONSUMER GROUP                                     │
│    - Reads from assigned partition                          │
│    - Batches messages (e.g., every 10 min)                  │
│    - Converts Avro → Parquet (2-10x compression)            │
│    - Prepares batch file (256 MB - 1 GB target)             │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. DATA WAREHOUSE (OLAP)                                    │
│ Option A: Bulk insert Parquet file                          │
│   - Updates metadata                                        │
│   - Data partition: hash(ad_id) % warehouse_partitions      │
│   - Sorted by: ad_id, timestamp (clustering)                │
│                                                              │
│ Option B: Real-time row insertion (Hudi)                    │
│   - Rows available immediately in buffer                    │
│   - Background compaction to Parquet every 10 min           │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 8A. MATERIALIZED VIEWS (Pre-aggregation)                    │
│     - Daily batch job (3 AM)                                │
│     - Aggregate previous day's clicks by minute             │
│     - Group by ad_id                                        │
│     - Persist to "minute_data" table                        │
└────────────────┬────────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────────────────────┐
│ 8B. USER QUERY (e.g., clicks/minute for ad_id_123)          │
│     - Query 1: minute_data (pre-agg yesterday)              │
│     - Query 2: raw_clicks (today only, ad hoc aggregation)  │
│     - Combine results                                       │
│     - Return to user (sub-second)                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 16. Offset Management & Idempotency

### The Kafka Consumer Failure Scenario:

```
Perfect scenario:
1. Consumer reads Kafka messages (offset 1000-1010)
2. Inserts into warehouse
3. Commits offset 1010 to broker
4. All good

But what if consumer crashes before commit?
1. Consumer reads Kafka messages (offset 1000-1010)
2. Inserts into warehouse
3. CRASH (before commit)
4. Rebalance: new consumer assigned partition
5. New consumer reads last committed offset: 999
6. Starts reading from 1000 again
7. Re-inserts rows 1000-1010 into warehouse (DUPLICATES!)
```

### Solution: Kafka Offset & Partition Tracking:

```
When inserting each row into warehouse:
- Store: kafka_partition_id (which partition it came from)
- Store: kafka_offset (the message offset in that partition)

Example warehouse row:
[ad_id] [clicks...] [timestamp] [kafka_partition] [kafka_offset]
  123   [data...]   2024-01-01        5               1005

On consumer restart:
1. Query warehouse: SELECT MAX(kafka_offset) 
                   WHERE kafka_partition = 5
2. Find max_offset = 1010
3. Read from Kafka starting at 1011 (next offset after max)
4. Drop any incoming data until we surpass offset 1010
5. Continue normal processing from 1011 onward

Result: No duplicates, no data loss
```

### Alternative: Upserts (With Trade-offs):

```
If warehouse supports upserts/merges:
- Primary key: (kafka_partition, kafka_offset)
- If row with same key exists: Update (overwrite)
- If row doesn't exist: Insert
- Automatic idempotency

Trade-off:
- Upserts require checking for matching rows first
- Slower than inserts (requires lookup + write)
- Per the document: Better to pay penalty on startup than slow ingestion

Decision: Use offset tracking approach (faster ingestion)
```

---

## 17. Key Design Decisions Summary

| Decision | Choice | Why |
|----------|--------|-----|
| Write Queue | Kafka | 50+ MB/sec > 4 MB/sec load; handles hotspots |
| NoSQL Database | Not used for writes | Can't scale write throughput |
| Data Storage | Columnar Data Warehouse | Fast reads, good compression, OLAP queries |
| Partitioning | hash(ad_id) % num_partitions | Matches Kafka to warehouse; enables data pruning |
| Deduplication | Hash transactional DB before Kafka | Lightweight, scales via hashing |
| Query Optimization | Materialized views + ad-hoc | Balances liveness with query speed |
| Data Liveness | Batch every 10 min (+ real-time option) | Optimal file sizes; optional real-time via Hudi |
| File Sizing | 256 MB - 1 GB Parquet | Industry standard; good compression; few files |
| Idempotency | Offset tracking in warehouse | Faster ingestion than upserts |
| Consumer Failover | Kafka consumer groups | Automatic rebalancing, no manual coordination |

---

## 18. Conclusion

This system design provides:

1. **High write throughput**: Kafka handles 50 MB/sec per partition
2. **Fast analytical queries**: Columnar storage with data pruning
3. **Data liveness**: 2-10 minutes via batching; seconds via real-time insertion
4. **Scalability**: Partitioning strategy scales horizontally
5. **Durability**: 3-replica Kafka + offset tracking
6. **Simplicity**: Relatively straightforward component interactions

The key insight is recognizing that **transactional databases aren't suited for analytics at scale**, and using **batched, columnar storage** optimized for read-heavy analytical workloads is the right architectural choice.
