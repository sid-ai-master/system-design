# Ad Click Aggregator - Improvements & Alternative Approaches

## 1. Duplicate Detection: Better Approaches

### Current Approach:
```
Hash: MD5(page_url + ip + ad_id + timestamp)
Storage: Transactional database (DynamoDB, SQL)
Lookup: Before every Kafka write
```

### Issue 1: Storage Overhead

**Problem**:
```
If we store every hash forever:
- 4,000 clicks/sec × 86,400 sec/day = 345.6 million clicks/day
- Every click stores: hash (16-32 bytes) + metadata (20-50 bytes)
- Daily storage: 345.6M × 50 bytes ≈ 17.3 GB/day
- Monthly: 520 GB
- Yearly: 6.2 TB just for dedup hashes

Cost issues:
- Database storage increases indefinitely
- Expensive for cloud databases (DynamoDB)
- Requires cleanup/archival strategy
```

### Improvement 1A: Time-Windowed Deduplication

```
Instead of storing forever, use sliding window:

Configuration:
- Keep hashes for only 1 hour
- Older hashes deleted automatically
- Assumption: Duplicate clicks within 1 hour are malicious

Benefits:
- Fixed storage size: ~4.8 GB (1 hour window at peak)
- Prevents short-term fraud
- Much cheaper

Trade-off:
- Same user clicking legitimate ad twice in different hours = counted twice
- May not catch long-term sophisticated fraud
```

**Implementation**:
```
Kafka implementation using compacted topics:
- Topic: "duplicate-hashes"
- Message: {hash, timestamp}
- Compaction: Delete old messages
- Consumer caches recent hashes in memory (Bloom filter)

Result:
- No separate database needed
- Self-cleaning via Kafka compaction
- Faster lookups (in-memory cache)
```

### Improvement 1B: Bloom Filter for Lookups

```
Probabilistic data structure:
- O(1) lookup time
- Small memory footprint (few bytes per hash)
- False positives possible (acceptable for dedup)
- No false negatives (all duplicates detected)

Setup:
- Bloom filter in application memory
- Refresh periodically from Kafka compacted topic
- Reduces database lookups by ~95%

Benefits:
- Extremely fast (in-memory hash checks)
- Reduces transactional DB load
- Cheaper infrastructure

Trade-off:
- Requires refresh mechanism
- Brief window where recent hashes might not be in filter
- Need to handle false positives
```

### Improvement 1C: Probabilistic Deduplication

```
Instead of checking EVERY click:

Strategy:
- Sample clicks at 1% rate for dedup checks
- Assume: if no duplicates in sample, few in total
- Saves 99% of database lookups

Trade-off:
- Some duplicates slip through (99% of them)
- If you accept 1% fraud, this is acceptable
- Significant cost savings

Question: Is 100% dedup necessary?
- If fraudsters exploit this, may be too expensive
- For legitimate dedup, sampling works fine
```

---

## 2. Kafka Throughput: Reconsidering the Scale

### Current Claim:
```
"Single Kafka partition handles 50+ MB/sec"

But is this actually sufficient?
```

### The Reality Check:

```
Metrics:
- 4,000 clicks/sec
- 1 KB per click
- = 4 MB/sec

Kafka partition limit: 50 MB/sec
Utilization: 8% of capacity
Headroom: 900% (can handle 50x more traffic)

Seems fine... but consider:
```

### Issue 1: Peak Traffic Patterns

```
Reality of ad platforms:
- Ads have viral moments (sports events, trending topics)
- Black Friday/Cyber Monday spikes
- Celebrity endorsements can 10x traffic in minutes

Scenario:
- Average: 4 MB/sec
- Peak (5% of day): 40 MB/sec (10x average)
- Extreme peak (1 min): 100 MB/sec (25x average)

With single partition:
- 50 MB/sec limit
- Would reject/drop clicks during extreme peaks
- Data loss!
```

### Improvement 2A: Pre-Partitioning Strategy

```
Instead of 1 partition, start with multiple:
- 10 partitions from day 1
- Even if low traffic, costs minimal
- Scales naturally without repartitioning (hard operation)

Partitioning logic:
- hash(ad_id) % 10
- Can add more partitions later without reshuffling

Benefits:
- Handles spikes better
- No single point of failure for load
- Each partition: 50 MB/sec capacity
- Total: 500 MB/sec (100x+ headroom)

Cost:
- Minimal (Kafka broker resources are cheap)
- Better to over-provision than under-provision
```

### Improvement 2B: Dynamic Partition Scaling

```
Automatic detection:
- Monitor partition lag (how far behind real-time consumer is)
- If lag growing consistently: Add partitions
- Heuristic: lag > 30 seconds OR throughput > 40 MB/sec

Implementation:
- Use Kafka cluster auto-scaling (available in managed services)
- Add 2-4 new partitions automatically
- Re-hash existing data if needed (or accept unbalanced)

Trade-off:
- Adds operational complexity
- Requires monitoring and alerting
- But prevents manual intervention
```

---

## 3. Data Warehouse: Batch Size Optimization

### Current Approach:
```
Flush every 10 minutes
File size target: 256 MB - 1 GB
Data liveness: 10 minutes (not meeting 2-second requirement)
```

### Issues:

#### Issue 1: Liveness Trade-off

```
10-minute batches means:
- Click happens at t=0
- Visible in queries at t=600 seconds (10 min)
- Violates "data liveness within 2-seconds" requirement

Current mitigation:
- Real-time insertion with compaction
- But this adds complexity

Question: Is 10-minute really necessary?
```

### Improvement 3A: Micro-Batching Approach

```
Instead of 10-minute batches, use 30-second batches:

Calculation:
- Write speed: 4.1 kB/sec
- Time: 30 seconds
- Raw data: 4.1 kB × 30 = 123 kB
- Compressed (5x): ~24.6 kB per file

File size: Much smaller than 256 MB minimum
Compression ratio: Worse (smaller files compress less efficiently)

Trade-off evaluation:
- Pro: 30-second liveness (much better than 10 min)
- Con: More files (more metadata overhead)
- Con: Worse compression (more storage)

Math:
- 10-min batch size: ~500 KB compressed
- 30-sec batch size: ~25 KB compressed
- But 20x more files (20 files × 30 sec = 10 min)
- More files = more metadata = more overhead

Is it worth it?
- If liveness is critical: YES
- May accept extra storage cost for real-time analytics
```

### Improvement 3B: Tiered Compression Strategy

```
Different retention policies:

Hot tier (last 1 hour):
- Real-time row insertion (no compression)
- Or 30-second micro-batches
- Available immediately

Warm tier (1-24 hours):
- 10-minute batches
- Moderate compression
- Trade liveness for efficiency

Cold tier (> 1 day):
- 1-hour batches
- Heavy compression
- Optimized for storage cost

Query handling:
- Queries automatically search all tiers
- User doesn't see the difference
- Cost optimized by tier

Example warehouse: ClickHouse natively supports this
```

### Improvement 3C: Streaming Insert + Delta Merge

```
Modern data warehouses support:
- DuckDB's streaming inserts
- Iceberg/Delta Lake table formats
- Hudi (already mentioned in doc)

These allow:
- Sub-second data availability
- Background merging of small files
- Automatic compaction to optimal sizes
- No manual orchestration needed

Benefits:
- Meets liveness requirement naturally
- No complex dual-query approach
- Self-optimizing file sizes

Trade-off:
- Requires modern warehouse choice
- Not available in older systems (Redshift)
- May have higher write costs
```

---

## 4. Deduplication at Different Layers

### Current Approach:
```
Dedup at application layer (before Kafka)
- Check transactional DB
- Drop if duplicate found
```

### Alternative 1: Server-Side Dedup in Kafka

```
Setup:
- Store dedup hashes in Kafka compacted topic
- Consumer (not producer) checks for duplicates
- Only insert non-duplicates to warehouse

Pros:
- Application logic simpler
- Dedup centralized in Kafka
- Easier to change/debug

Cons:
- Some duplicates enter Kafka (wasted throughput)
- Requires consumer-side filtering
- Metadata overhead in Kafka
```

### Alternative 2: Dedup in Data Warehouse

```
Setup:
- Insert ALL clicks to warehouse
- Dedup at warehouse (during compaction)
- Use DISTINCT or GROUP BY in queries

Pros:
- Simpler application code
- Leverage warehouse's dedup capability
- Works with existing upsert functionality

Cons:
- Stores duplicates (wasted storage)
- Warehouse must filter duplicates (compute cost)
- Slower ingestion with upserts

Example:
```sql
SELECT ad_id, COUNT(DISTINCT dedup_hash) as click_count
FROM clicks
WHERE timestamp > now() - interval 1 hour
GROUP BY ad_id
```
```

### Recommendation:

```
Hybrid approach:
1. Application-level: Quick Bloom filter check (99% filtering)
2. Kafka-level: Compacted topic of recent hashes (fallback)
3. Warehouse: Query-time DISTINCT if needed (backup)

Benefits:
- Catches most duplicates early (saves resources)
- Fallback dedup at other layers
- Self-healing even if one layer fails
```

---

## 5. Partitioning Scheme: Reconsidering ad_id

### Current Approach:
```
Partition by: hash(ad_id) % num_partitions
Rationale: Optimize for "clicks per ad" queries
```

### Issue: What if Most Common Query is Different?

```
Scenario 1: Most common query = "clicks by advertiser"
Partitioning by ad_id = BAD
- Must read all partitions for one advertiser's ads
- Inefficient data pruning

Scenario 2: Most common query = "clicks by timestamp"
Partitioning by ad_id = BAD
- Time-range queries must read all partitions
- No temporal locality

Scenario 3: Most common query = "clicks by geography"
Partitioning by ad_id = BAD
- Geographic queries scattered across partitions
```

### Improvement 5A: Query Pattern Analysis

```
Before choosing partition scheme:

1. Analyze actual query logs (if possible)
   - Backend analytics team has query distribution
   - Sample 1000 recent queries
   - Identify top 10 query types

2. Rank by frequency:
   - 40%: clicks by advertiser + date range
   - 25%: clicks by region + ad
   - 20%: clicks by device type
   - 15%: others

3. Choose partition key for #1 (40%)
   - Partition by advertiser_id instead of ad_id
```

### Improvement 5B: Composite Partitioning

```
If multiple query types are common:

Option 1: Multiple warehouse tables
```
Primary table:
- Partitioned by advertiser_id

Secondary table:
- Partitioned by region (materialized view)

Secondary table:
- Partitioned by device_type (materialized view)

Queries check which table fits best
```

Option 2: Bucketing within partitions
```
Main partition: advertiser_id (top query)
Sub-bucket: ad_id (within advertiser)

Storage:
/warehouse/advertiser_1/ad_100
/warehouse/advertiser_1/ad_101
/warehouse/advertiser_2/ad_200

Query: "clicks by ad_id within advertiser_1"
- Read only /warehouse/advertiser_1/*
- Much faster than all partitions
```

Option 3: Separate fact and dimension tables (Star schema)
```
Fact table (clicks):
- Partitioned by timestamp (temporal locality)
- Foreign keys to dimensions

Dimension tables:
- ad_dim (ad_id → ad properties)
- advertiser_dim (advertiser_id → company info)
- geo_dim (region → geography info)

Query execution:
- Join fact + dimensions
- Benefits from both partitioning schemes
```
```

### Recommendation:

```
Don't assume: Analyze actual queries first
Too many teams burn resources optimizing for wrong query pattern
```

---

## 6. Data Warehouse Technology: Royal Flush

### Current Approach:
```
Generic OLAP warehouse: BigQuery, Redshift, Snowflake, ClickHouse
Reason: Flexibility, arbitrary SQL queries
```

### Issue: Time-Series Databases Might be Better

```
Table of characteristics:

                    OLAP Warehouse    |  Time-Series DB
────────────────────────────────────────────────────
Compression         Good             |  Excellent (10-100x)
Query speed         Fast             |  Very Fast (optimized)
Time-range queries  Good             |  Excellent (native)
SQL support         Yes              |  Limited/Some
Aggregations        Flexible         |  Pre-built only
Cardinality         High             |  Very high (OK)
Storage cost        Medium           |  Low (great compression)
────────────────────────────────────────────────────

For ad clicks (inherently time-series):
- Query: clicks last 7 days → TS DB faster
- Query: clicks in NYC last 7 days → TS DB faster
- Query: complex JOIN → OLAP warehouse better
```

### Improvement 6A: Time-Series Database (InfluxDB, VictoriaMetrics)

```
Architecture change:
Kafka → InfluxDB/VictoriaMetrics → Query

Benefits:
- 50-100x better compression than OLAP
- 10x faster time-range queries
- Automatic downsampling (1 day → 1hr buckets)
- 90% storage cost reduction

Trade-offs:
- Can't run arbitrary SQL
- Limited JOIN capability
- Inflexible aggregations
- Requires pre-thinking queries

When to use:
- If queries are mostly time-range based (likely for ad analytics)
- If storage cost is critical
- If query speed is critical
```

### Improvement 6B: Hybrid Warehouse + Time-Series

```
Use both systems:

Time-Series DB (hot data):
- Stores last 30 days
- Fast queries
- Real-time data

OLAP Warehouse (cold data):
- Stores > 30 days
- Historical analysis
- Flexible queries

Query routing:
- If date range in last 30 days: Query TS DB
- If date range > 30 days: Query warehouse
- If both: Query both, combine results

Benefits:
- Fast current quarter analytics (TS DB)
- Flexible year-over-year analysis (warehouse)
- Optimized storage per tier

Cost:
- Dual system management
- Data replication overhead
```

---

## 7. Consumer Group Resiliency: Hidden Risks

### Current Approach:
```
Kafka consumer groups
Automatic failover
Offset management
```

### Risk 1: Graceful Shutdown Doesn't Always Happen

```
Problem:
- Consumer process dies hard (SIGKILL, crash)
- Offset not committed before death
- New consumer starts from old offset
- Re-inserts data to warehouse

Current mitigation:
- Check warehouse for max offset
- Skip data until past it

Issue with mitigation:
- If warehouse also crashes mid-insert
- Some inserts lost, some duplicated
- Offset checking doesn't catch this
- Data inconsistency!
```

### Improvement 7A: Exactly-Once Semantics

```
Implementation: Two-Phase Commit

Before inserting to warehouse:
1. Write data to temporary staging table

After confirming insert:
2. Commit the transaction
3. Update offset in same transaction
4. Either both succeed or both fail

Kafka supports: Transactions (idempotent producer)
Warehouse supports: Distributed transactions (varies)

Benefits:
- Guaranteed exactly-once delivery
- No duplicates even on failure
- No data loss

Trade-off:
- More complex implementation
- Slower (two writes instead of one)
- Not all databases support it well
- DynamoDB doesn't support transactions well
```

### Improvement 7B: Change Data Capture (CDC)

```
Use CDC tool (Debezium, Maxwell):
- Watches transactional DB for changes
- Automatically sends to Kafka
- Handles idempotency

Comparison:
- Current: App writes to Kafka
- CDC: App writes to DB, CDC watches DB, CDC pushes to Kafka

Benefit:
- DB is source of truth
- If CDC fails, can replay from DB
- Better auditability
- Kafka becomes ephemeral (can be rebuilt)

Trade-off:
- Extra hop (App → DB → Kafka)
- CDC tool adds complexity
- Slight latency increase
```

### Improvement 7C: Idempotent Consumer Logic

```
Make consumer code truly idempotent:

Instead of:
```python
for message in kafka:
    insert_to_warehouse(message)
    commit_offset(message.offset)
```

Do this:
```python
for batch in kafka_batches:
    # Merge operation (not insert)
    warehouse_merge(batch, merge_key=(kafka_partition, kafka_offset))
    commit_offset(batch.offset)
```

Merge operation:
- If row exists: Update
- If not exists: Insert
- Atomic, all-or-nothing

Benefit:
- Even if entire batch re-runs, result is same
- True idempotency
- No offset checking needed

Trade-off:
- Merge slower than insert
- But enables simpler recovery
```

---

## 8. Materialized View Updates: Operational Burden

### Current Approach:
```
Daily batch job at 3 AM
Aggregates previous day into minute_data table
Manual scheduling
```

### Issues:

```
Issue 1: Time zones
- 3 AM in UTC ≠ 3 AM in user timezone
- For global company, which timezone?
- Some users get stale data, some get fresh data

Issue 2: What if batch job fails?
- No retry logic mentioned
- If 3 AM job fails, no minute_data for that day
- User queries fail or return old data

Issue 3: What if data warehouses has different update schedule?
- Kafka updates in real-time
- Warehouse updates every 10 min
- minute_data updates once daily
- Three different freshness levels
- User confusion
```

### Improvement 8A: Incremental View Updates

```
Instead of: Recompute whole day daily

Do this: Update as data arrives

Pseudo-code:
```
Every 1 minute:
  - Query raw_clicks for last minute
  - Aggregate into 1-minute bucket
  - INSERT or UPDATE minute_data

Benefit:
- minute_data is always 1 minute behind (not 24 hours)
- Liveness improves dramatically
- Reduces ad-hoc queries on raw data

Trade-off:
- More frequent updates (1440x more)
- Higher compute cost
- But many warehouses can handle 1440 updates/day easily
```

### Improvement 8B: Continuous Aggregation

```
Use built-in features of modern warehouses:

ClickHouse:
- Materialized views with ENGINE=SummingMergeTree
- Automatic aggregation on insert
- Real-time aggregated results

Apache Flink:
- Streaming aggregation
- Windowed aggregations
- Always-up-to-date results

Spark Structured Streaming:
- Streaming + batch unified
- Automatic state management
- Can write aggregations to warehouse in real-time

Benefits:
- No manual scheduling
- Always current
- Scales with data volume
- Built-in fault tolerance

Trade-off:
- Requires supporting tools
- More infrastructure to manage
```

### Improvement 8C: Query-Time Materialization

```
Don't pre-aggregate at all:

Instead: Aggregate on every query

Example:
```sql
SELECT 
    ad_id,
    DATE_TRUNC(timestamp, MINUTE) as minute,
    COUNT(*) as clicks
FROM clicks
WHERE ad_id = ? AND timestamp > now() - interval 7 day
GROUP BY ad_id, minute
```

Modern warehouse optimizations:
- Query plans are smart enough to cache results
- Columnar format = fast aggregations
- Caching layer (Redis) keeps top queries fast
- Execution time: milliseconds even on large data

Benefit:
- No separate table maintenance
- No stale data
- Simple and flexible

Trade-off:
- Depends on query engine being fast
- Not all warehouses can handle this
- Needs proper caching
```

---

## 9. IP-Based Rate Limiting: Limitations

### Current Approach:
```
Rate limit by IP address before Kafka
Stop obvious spammers early
```

### Issues:

```
Issue 1: Distributed attacks
- Attacker uses 10,000 IPs (botnets, proxies)
- Each IP makes 1 click (under limit)
- 10,000 real clicks from fraudster
- IP rate limiting doesn't help

Issue 2: VPN/Proxy usage
- Legitimate users behind VPN share IP
- Multiple users = looks like spam
- Real users get blocked

Issue 3: Data center IPs
- Corporate networks: 5000 people, 1 IP
- All look like spam
- Legitimate traffic gets rate limited
```

### Improvement 9A: Multi-Signal Fraud Detection

```
Instead of just IP:

Combine signals:
1. IP address (but with higher thresholds)
2. User ID (if logged in)
3. Device fingerprint (browser + OS + plugins)
4. Behavior pattern (speed of clicking)
5. GeoIP velocity (NYC → Tokyo in 1 second = fake)
6. Known fraud network (use fraud detection service)

Example:
- Signal 1 alone: 100 clicks/IP/hour
- Signal 1 + Signal 2: 1000 clicks/user/hour (higher)
- Signal 1 + Signal 2 + 3 + 4 + 5: Combined ML score
  - If score > threshold: Flag
  - If score < threshold: Allow

Benefits:
- Catches distributed attacks
- Reduces false positives
- Legitimate users not blocked

Trade-off:
- More infrastructure (ML/fraud service)
- Latency added (fraud check ~10-50ms)
- More data to track
```

### Improvement 9B: Post-Event Fraud Detection

```
Move fraud check from real-time to batch:

Real-time (current):
- Check on every click
- Block immediately if fraudulent
- Fast but limited signals

Post-Event (alternative):
- Accept all clicks into Kafka
- Batch analyze clicks nightly
- Flag fraudulent patterns
- Adjust reports (subtract fraudulent clicks)
- Notify advertiser of fraud

Benefits:
- More data for analysis (see full patterns)
- No blocking legitimate traffic
- Better ML predictions (after-the-fact)
- Catch sophisticated fraud (botnets)

Trade-off:
- 24-hour latency to flag fraud
- Some fraudulent revenue counted initially
- More complex reporting
- Advertisers see wrong numbers for 24 hours
```

### Improvement 9C: Outsource to Fraud Service

```
Use third-party provider:
- Google Safe Browsing
- Stripe Radar (for payments)
- Crowd sourced fraud networks

Benefits:
- Leverage billions of data points
- Better accuracy than self-made solution
- Updated with new frauds automatically
- Don't have to build ML models

Trade-off:
- Cost (per API call)
- Latency (external call)
- Privacy (sending IP/user data outside)
- Dependence on vendor
```

---

## 10. Kafka Durability: Over-Engineered?

### Current Configuration:
```
min.insync.replicas: 3
acks: 1 (all)

Means:
- 3 copies of every message
- Message only valid after 1 replica writes it
```

### Analysis:

```
Trade-off:
- Pro: Very safe, almost zero data loss
- Con: 3x the write latency, 3x storage, higher cost

Alternative configurations:

Configuration A: Balanced
- min.insync.replicas: 2
- acks: 1
- Trade: 66% storage, slightly less safe

Configuration B: Speed-focused
- min.insync.replicas: 1  
- acks: 1
- Trade: 33% storage, lose some safety

Configuration C: Ultra-safe
- min.insync.replicas: 3
- acks: all (wait for all replicas)
- Trade: Slowest, safest

For ad clicks, should we use config C?
```

### Improvement 10A: Risk-Based Durability

```
Recognition: Not all data needs same durability

Idea:
- Clicks < $0.01: Config A (fast)
- Clicks $0.01-$1: Config B (balanced) 
- Clicks > $1: Config C (safe)

Implementation:
- Producer tags each message with value
- Broker routes to different replication configs
- Higher value = more replicas

Benefits:
- Overall faster (most clicks are cheap)
- Important clicks are safe
- Cost optimized

Trade-off:
- More complex configuration
- Need value estimation
```

### Improvement 10B: Lazy Replication

```
Don't replicate immediately:

Setup:
- Write to leader immediately (acks: 1)
- Async replicate in background
- If leader dies: Some data might be lost

Risk:
- Even with 3 replicas, one leader failure = potential loss
- Estimated: 1 message per 100M happens (99.999% safety)
- For ad clicks: Acceptable? Depends on business

Benefit:
- Much faster writes
- Less replication overhead

Trade-off:
- Small risk of data loss
- But acceptable for analytics (not financial transactions)
```

### Improvement 10C: Tiered Replication

```
Change replication based on age:

Recent clicks (< 1 day):
- Needed for real-time reporting
- 3 replicas (safe, important)

Old clicks (> 1 day):
- Archived to cost storage (S3)
- 1 replica left in Kafka (redundancy)
- Can restore from S3 if needed

Benefits:
- Safe for important data (recent)
- Cheap for less important (old)
- Still recoverable

Trade-off:
- More operational overhead
- Backup/restore procedures needed
```

---

## 11. Architecture: Missing Components

### What's NOT mentioned in design:

```
1. API Gateway / Service Mesh
   - How do load balancers know to route to app servers?
   - TLS/SSL termination?
   - Rate limiting at API level?
   - Missing from design

2. Authentication & Authorization
   - Who can query the warehouse?
   - Can advertiser X see advertiser Y's data?
   - How are queries authorized?
   - Not addressed

3. Data Retention & Compliance
   - How long kept? (needs answer for GDPR)
   - How to delete user data?
   - GDPR data deletion at Kafka/warehouse?
   - Not discussed

4. Monitoring & Alerting
   - What if Kafka lag grows?
   - What if warehouse unavailable?
   - What metrics do we track?
   - Not defined

5. Schema Evolution
   - What if we need to add field to click?
   - How do old and new schema coexist?
   - Who manages schemas?
   - Not addressed

6. Dead Letter Queue
   - What if message can't be processed?
   - Where do failed messages go?
   - Retry logic?
   - Not mentioned
```

### Improvement 11A: Complete Architecture

```
Add these components:

┌───────────────────────────────────────────────────┐
│  API Gateway (Kong, AWS API Gateway)              │
│  - TLS termination                                 │
│  - Authentication (OAuth 2.0)                      │
│  - Rate limiting (global)                          │
└──────────────┬──────────────────────────────────────┘
               ↓
┌───────────────────────────────────────────────────┐
│  Service Mesh (Istio)                             │
│  - Observability                                   │
│  - Circuit breakers                               │
│  - Retry logic                                    │
└──────────────┬──────────────────────────────────────┘
               ↓
┌───────────────────────────────────────────────────┐
│  Authorization Layer                              │
│  - Check: Can advertiser X see data?              │
│  - Enforce row-level security                     │
└──────────────┬──────────────────────────────────────┘
               ↓
[Rest of pipeline]
```

### Improvement 11B: Operational Components

```
Add monitoring:

Prometheus metrics:
- Kafka consumer lag (per partition)
- Warehouse insert latency
- Query response times (p50, p99)
- Duplicate click rate
- Data freshness (age of oldest unprocessed click)

Alerting:
- Alert if consumer lag > 5 min
- Alert if warehouse insert time > 2 min
- Alert if query p99 > 10 sec

Dashboards:
- Real-time click volume
- Data pipeline health
- Error rates

Logging:
- All errors logged to centralized system
- Clicktraces for individual clicks
```

### Improvement 11C: Schema Management

```
Schema registry (Confluent or Protobuf):

Click schema evolution:
```
v1: {ad_id, user_id, timestamp}
v2: {ad_id, user_id, timestamp, device}
v3: {ad_id, user_id, timestamp, device, geo}
```

Handling in code:
- Producer publishes v3
- Old consumer might not understand device/geo
- Schema registry handles compatibility
- Backward compatibility: Old reads work
- Forward compatibility: New fields ignored by old code

Enables:
- Non-breaking changes
- Smooth migrations
- Multi-version support
```

---

## 12. Cost Optimization Opportunities

### Current Design Costs:

```
Kafka (50+ MB/sec partition, 1 partition):
- Cost per month: ~$500 (varies by cloud)

Data Warehouse (10 TB/month ingestion):
- Ingestion cost: ~$1000/month
- Storage cost: ~$200/month (monthly data only)
- Query cost: ~$300/month

Dedup Database (DynamoDB):
- Write: ~$200/month
- Storage: ~$100/month

Compute (Application servers, consumers):
- ~$3000/month

Total: ~$5300/month
```

### Improvement 12A: Compression Strategy

```
Current: Parquet defaults (2-5x compression)

Optimization: Aggressive compression

Benefits:
- Store more years of data for same cost
- Faster queries (fewer bytes read)
- Example: 2x compression = 2x money saved

Cost/benefit:
- Trade: CPU for storage (worth it if storage expensive)
- Compression time: Slower ingestion
- Query time: Faster (fewer bytes read, decompression parallel)

Modern option:
- Zstd compression (better than gzip, faster decompression)
- Can achieve 5-10x on similar data

Implementation:
- Warehouse setting: Use Zstd instead of default
- Spark job: Recompress with Zstd=
```

### Improvement 12B: Tiered Storage

```
Hot tier (last 7 days):
- SSD storage ($1/GB/month)
- Fast queries
- Cost: $7000/month

Warm tier (8-90 days):
- HDD storage ($0.1/GB/month)  
- Slower queries (OK for historical)
- Cost: $700/month

Cold tier (> 90 days):
- Object storage ($0.02/GB/month)
- Very slow queries (rare)
- Cost: $140/month

Total storage cost reduction: 80%

Implementation:
- Hive partitioning by date
- Data automatically moves between tiers
- Queries transparent (hit all tiers)
```

### Improvement 12C: Dedup: Sampling Instead of 100%

```
Current: Every click checked against DB

Optimization: Only check 10% of clicks
- 90% accepted as-is (assume honest)
- 10% sampled for dedup
- Fraudsters caught eventually

Cost:
- 90% reduction in dedup DB cost (~$250/month)
- Higher fraud rate (maybe 1% slip through)
- Trade: $250/month for 1% fraud

Only works if:
- Fraud cost < $250/month
- Acceptable to business
```

### Improvement 12D: Query Caching

```
Problem:
- User runs: "clicks for ad_123 last 7 days" → takes 5 sec
- Different user runs same query → takes 5 sec again
- Both hit the warehouse

Solution: Cache layer (Redis)
```
User query → Cache lookup
  ├─ Hit: Return cached result (instant)
  └─ Miss: Query warehouse, cache result

Cost:
- Redis: ~$500/month
- Warehouse time reduced by ~50%
- Savings: $150-300/month

Net: +$200/month, but 10x faster queries
```

---

## 13. Failure Modes Not Addressed

### Scenario 1: Kafka Broker Failure (Unplanned)

```
Current design mentions: 3 replicas ensure no data loss
But what about:
- Broker 1 (replica) dies
- Broker 2 (replica) dies
- Broker 3 (leader) still alive... but catching fire
- All 3 replicas now unreadable

Probability: Very low (3 independent failures)
But: If it happens, all data in that partition is gone
```

**Mitigation**:
```
- Backup Kafka (write same data to backup topic)
- Weekly backup to S3
- Test restore procedure quarterly
```

### Scenario 2: Duplicate Key Detection Fails

```
Malicious user:
- Uses proxy to change IP every click
- Uses browser private mode (device fingerprint changes)
- Hash now: hash(different_page_url + different_ip + ad + ts)
- Each click has different hash
- All pass dedup check
- All recorded as legitimate clicks
```

**Mitigation**:
```
- Combine with behavior-based detection
- Machine learning on click patterns
- Rate limit per user_id (if logged in)
- Require JS (blocks simple bots)
```

### Scenario 3: Data Warehouse Runs Out of Space

```
Unexpected growth:
- Normal: 10 TB/month
- One day: 50 TB/month (viral ads)
- Warehouse diskspace exceeded

What happens?
- Inserts start failing
- Data accumulates in Kafka
- Consumer lag grows
- Eventually Kafka disk full
- Data loss
```

**Mitigation**:
```
- Set disk alarms at 80% usage
- Auto-scaling (add disks/nodes)
- Hard limits on Kafka retention
- Compressed retention (old data → S3)
```

### Scenario 4: Transactional DB Failure (Dedup)

```
Dedup DB down:
- Can't check if click is duplicate
- Option A: Assume all clicks are unique (no dedup)
- Option B: Block all clicks (breaks system)
- Option C: Fall back to Bloom filter (some duplicates slip)
```

**Mitigation**:
```
- Redundancy: Read replicas
- Failover: Replica becomes primary
- Timeout: If DB slow, assume OK (optimistic)
- Bloom filter: Last 1 hour of hashes in memory
  - If DB down, use Bloom filter (some false positives)
  - Resume DB dedup when back online
```

---

## 14. Performance Optimization: Query Speed

### Current Approach:
```
- Columnar storage
- Partitioning by ad_id
- Materialized views for minute aggregates
```

### Optimization 14A: Indexes

```
Modern data warehouses (unlike relational DBs):
- Don't usually use B-tree indexes (too slow)
- Use: Min-max indexes (per block)
- Stored: With column data

Optimization:
- Sort by query columns (ad_id, timestamp)
- Warehouse automatically builds min-max index
- Query pruning: Skip blocks based on index

Example:
```
Block 1: ad_id [100-200], timestamp [2024-01-01 to 2024-01-05]
Block 2: ad_id [300-400], timestamp [2024-01-05 to 2024-01-10]

Query: "clicks for ad_id=150, date=2024-01-07"
- Block 1: ad_id matches (100-200), date doesn't match (not in range)
- Block 2: ad_id doesn't match (300-400)
- Result: Zero blocks to read!
```
```

### Optimization 14B: Pre-sorted Data via Kafka

```
If clicks from Kafka come pre-sorted by ad_id:
- Warehouse stores them already sorted
- No re-sorting needed on insert
- Faster ingestion
- Better compression (sequential ad_ids)

Implementation:
- Producer sends: sorted batches (by ad_id)
- Warehouse receives: already sorted
- Direct ingest without sorting

Trade-off:
- Kafka producer more complex
- Benefit: Faster warehouse ingestion (~10-20%)
```

### Optimization 14C: In-Memory Materialization

```
Problem:
- Even materialized views require disk I/O
- User: "Show me last hour of clicks"
- Warehouse: Read from disk, aggregate, return

Solution: Memory-resident aggregates

Setup:
- Recent aggregations cached in memory
- Only run on "hot" data (last hour)
- Older data from disk

Implementation tools:
- Redis (simple key-value cache)
- DuckDB (in-memory OLAP)
- Apache Druid (in-memory aggregates built-in)

Example with Druid:
```
- Ingests clicks in real-time
- Maintains in-memory aggregations
- Query returns in milliseconds (vs seconds)
- Automatically expires old data
- Disk space for historical?
```

Benefit:
- Subsecond queries for recent data
- Scales differently than warehouse
- Better SLA for critical queries

Trade-off:
- Memory cost (high)
- Operational complexity
- Only for "hot" queries
```

---

## 15. Alternative Architectures Not Considered

### Architecture A: Lambda Architecture (Batch + Streaming)

```
Batch layer:
- Daily batch processes all data
- Builds pre-computed views (1 day old)

Streaming layer:
- Real-time stream processes (1 hour old)

User query:
- Combines batch + streaming results
- Gets best of both worlds

Benefit:
- Simple streaming (minimal processing)
- Complete historical analysis (batch)
- Redundancy (can rebuild from batch)

Trade-off:
- Two completely separate systems
- Maintenance burden
- Complex reconciliation
```

### Architecture B: Kappa Architecture (Stream Only)

```
Single streaming layer processes everything
No batch layer

Benefits:
- Simpler (one system)
- Code reuse (stream processes all)
- Easier to test (one codebase)

Trade-off:
- Must history from stream (need log)
- Harder to reprocess all data
- No batch optimization layer
```

### Architecture C: Event Sourcing

```
Don't store current data, store all events

Key idea:
- Event: "User clicked ad_123"
- Store: Every event in order
- Derive: Current state from events

Benefits:
- Complete audit trail ("what changed and when")
- Can replay from any point in time
- Time travel queries ("what was state on Jan 1?")

Trade-off:
- Storage overhead (all versions)
- Complexity (derive state from events)
- Query complexity (aggregate events first)
```

---

## 16. Summary: Top 5 Improvements

If only 5 changes to make:

```
1. Pre-partition Kafka (10 partitions from day 1)
   Why: Prevents peak traffic bottlenecks, minimal cost

2. Real-time insertion + background compaction (Hudi/Iceberg)
   Why: Meets the 2-second liveness requirement
   Cost: More compute, but worth for SLA

3. Add monitoring & alerting layer
   Why: Nobody notices failures otherwise
   Cost: Minimal infrastructure

4. Use Bloom filter for dedup (Kafka compacted topic)
   Why: 99% fewer DB lookups, no separate system
   Cost: Minimal

5. Tiered storage by age
   Why: 80% storage cost reduction
   Cost: Operational complexity (low)

ROI leaders:
- Improvement 5: Best cost savings
- Improvement 3: Best reliability
- Improvement 1: Best scalability
```

---

## Conclusion

The design is **solid and production-ready**, but:

- **Not optimized for peak traffic** (single partition bottleneck)
- **Doesn't quite meet liveness requirement** (10-min batch vs 2-sec target)
- **Missing operational visibility** (no monitoring defined)
- **Partitioning based on assumption** (should verify with queries)
- **Over-engineered on durability** (3 replicas might be overkill)

**Recommendation**: Implement improvements 1, 2, 3 before production. Others depend on business requirements and budget.
