# Ad Click Aggregator - Database Entities & Schema Design

## 1. Deduplication Database Schema (Transactional DB)

This database is used for real-time duplicate detection. Choice: DynamoDB, PostgreSQL, or MySQL with strong consistency.

### Table 1: Click Dedup

**Purpose**: Store hashes of recent clicks to detect duplicates in real-time.

```sql
CREATE TABLE click_dedup (
  -- Primary Key
  dedup_hash VARCHAR(64) PRIMARY KEY,
  -- Payload
  ad_id UUID NOT NULL,
  page_url VARCHAR(2048) NOT NULL,
  ip_address VARCHAR(45) NOT NULL,          -- IPv4 or IPv6
  timestamp_seconds BIGINT NOT NULL,        -- Unix timestamp (for TTL)
  timestamp_inserted TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  -- Additional fields
  user_agent_hash VARCHAR(64),
  device_fingerprint_hash VARCHAR(64),
  INDEX idx_timestamp (timestamp_seconds),
  INDEX idx_ad_id (ad_id, timestamp_seconds)
);
```

**Column Details**:
- `dedup_hash`: SHA256(page_url + ip_address + ad_id + timestamp_second)
- `timestamp_seconds`: Rounded to nearest second (for grouping in same-second clicks)
- `timestamp_inserted`: When hash was inserted (for cleanup)

**Indexes**:
1. Primary key on `dedup_hash` (for O(1) lookups)
2. Index on `timestamp_seconds` (for time-windowed cleanup)
3. Index on `ad_id, timestamp_seconds` (for fraud investigation by ad)

**TTL (Time To Live)**:
```
DynamoDB: 1 hour
- Attribute: timestamp_inserted + 3600 seconds
- Automatic deletion via TTL

PostgreSQL: Manual cleanup job
- DELETE FROM click_dedup 
  WHERE timestamp_inserted < NOW() - INTERVAL '1 hour'
- Run every 15 minutes
```

**Storage Estimate**:
```
Peak throughput: 4,000 clicks/sec
1-hour TTL: 4,000 × 3600 = 14.4 million records
Per record: 150 bytes (hash 64 + id 36 + url 256 + ip 15 + metadata 50)
Total: 14.4M × 150B ≈ 2.2 GB
```

**Scaling**:
```
DynamoDB:
- Billing capacity: On-demand (scales automatically)
- Estimated cost: ~$200-300/month

PostgreSQL:
- Single node: Handles 3-5K reads/sec
- Sharding: Partition by hash(ad_id) % 10 partitions
  - Partition 0-9 spread across different DB instances
```

---

### Table 2: Rate Limiting State

**Purpose**: Track IP addresses and rate limits to prevent spam.

```sql
CREATE TABLE rate_limit_state (
  ip_address VARCHAR(45) PRIMARY KEY,
  request_count INT DEFAULT 0,
  blocked_flag BOOLEAN DEFAULT FALSE,
  window_start TIMESTAMP,
  window_end TIMESTAMP,
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(256),
  INDEX idx_blocked_flag (blocked_flag),
  INDEX idx_updated (last_updated)
);
```

**Windows**:
- Per-second window: 1 second
- Per-hour window: 1 hour

**Cleanup**:
```sql
DELETE FROM rate_limit_state 
WHERE last_updated < NOW() - INTERVAL '2 hours'
AND blocked_flag = FALSE;
```

---

### Table 3: Blocked IPs

**Purpose**: Maintain blacklist of known malicious IPs.

```sql
CREATE TABLE blocked_ips (
  ip_address VARCHAR(45) PRIMARY KEY,
  reason VARCHAR(500),
  blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP,  -- NULL = permanent
  blocked_by_user_id UUID,
  severity ENUM('LOW', 'MEDIUM', 'HIGH'),
  INDEX idx_expires_at (expires_at)
);
```

---

## 2. Data Warehouse Schema (Analytical DB)

This is where all click data is stored in columnar format for analytics. Choice: BigQuery, Snowflake, ClickHouse, Redshift.

### Main Table: Clicks

**Purpose**: Daily partitioned table containing all clicks. Partitioned by ad_id and date.

```sql
CREATE TABLE IF NOT EXISTS clicks (
  -- IDs
  click_id UUID,
  ad_id UUID NOT NULL,
  campaign_id UUID NOT NULL,
  advertiser_id UUID NOT NULL,
  user_id STRING,
  
  -- Click details
  timestamp TIMESTAMP NOT NULL,
  page_url STRING NOT NULL,
  landing_url STRING,
  referrer_domain STRING,
  
  -- User/Device info
  ip_address STRING NOT NULL,
  country STRING,
  region STRING,
  city STRING,
  latitude FLOAT64,
  longitude FLOAT64,
  
  device_type STRING,  -- MOBILE, DESKTOP, TABLET
  os_name STRING,
  os_version STRING,
  browser_name STRING,
  browser_version STRING,
  user_agent_hash STRING,
  
  -- Session/Tracking
  session_id STRING,
  kafka_partition INT64,
  kafka_offset INT64,
  
  -- Metadata
  ingestion_timestamp TIMESTAMP,
  
  -- Parquet metrics (not all warehouses support)
  custom_field1 STRING,
  custom_field2 STRING
)
CLUSTER BY ad_id, timestamp
PARTITION BY DATE(timestamp)
OPTIONS (
  description = "All ad clicks with full details",
  labels = [("environment", "production"), ("pii", "yes")]
);
```

**Partitioning Strategy**:
```
BigQuery: PARTITION BY DATE(timestamp)
- Supports time-range pruning
- International time zone handling

ClickHouse: Partition by day, cluster by ad_id
- ORDER BY ad_id, timestamp
- Compression: Zstd

Redshift: Distribution key: ad_id
- Sort key: (ad_id, timestamp)
```

**Storage Estimate**:
```
Monthly ingestion: 10 TB
Year-round: 120 TB
Hot (last 30 days): 10 TB
Warm (31-90 days): 30 TB
Cold (> 90 days): 80 TB
```

---

### Indexes on Clicks Table:

#### Index 1: Primary Clustering (ad_id, timestamp)
```
BigQuery:
- CLUSTER BY ad_id, timestamp
- Benefit: Queries filtered by ad_id scan single block

ClickHouse:
- ORDER BY ad_id, timestamp
- Benefit: Min-max indexes created automatically
```

#### Index 2: Campaign Performance (campaign_id, timestamp)
```sql
CREATE INDEX idx_campaign_timestamp 
ON clicks (campaign_id, timestamp);
```

#### Index 3: Geo Breakdown (country, timestamp)
```sql
CREATE INDEX idx_geo_timestamp 
ON clicks (country, region, timestamp);
```

#### Index 4: Device Breakdown (device_type, timestamp)
```sql
CREATE INDEX idx_device_timestamp 
ON clicks (device_type, browser_name, timestamp);
```

---

### Materialized View 1: Minute Aggregates

**Purpose**: Pre-aggregated data at minute granularity for fast queries.

```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS clicks_minute_aggregates
CLUSTER BY ad_id, timestamp_minute
PARTITION BY DATE(timestamp_minute)
AS
SELECT
  ad_id,
  campaign_id,
  advertiser_id,
  DATE_TRUNC(timestamp, MINUTE) as timestamp_minute,
  
  COUNT(*) as click_count,
  COUNT(DISTINCT user_id) as unique_users,
  COUNT(DISTINCT session_id) as unique_sessions,
  
  -- Device breakdown
  APPROX_QUANTILES(CASE WHEN device_type = 'MOBILE' THEN 1 ELSE 0 END, 100)[OFFSET(50)] as mobile_pct,
  APPROX_QUANTILES(CASE WHEN device_type = 'DESKTOP' THEN 1 ELSE 0 END, 100)[OFFSET(50)] as desktop_pct,
  
  -- Geographic aggregates
  COUNT(DISTINCT country) as unique_countries,
  
  -- Timestamp
  MIN(timestamp) as earliest_click,
  MAX(timestamp) as latest_click
  
FROM clicks
WHERE timestamp >= DATE_SUB(CURRENT_DATE(), INTERVAL 90 DAY)
GROUP BY
  ad_id,
  campaign_id,
  advertiser_id,
  timestamp_minute;
```

**Refresh Schedule**:
```
Incremental refresh: Every 1 minute
Full refresh: Daily at 3 AM UTC
Retention: 90 days
```

---

### Materialized View 2: Daily Summaries

**Purpose**: Highly aggregated daily data for reports.

```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS clicks_daily_summaries
CLUSTER BY advertiser_id, date
PARTITION BY date
AS
SELECT
  advertiser_id,
  campaign_id,
  ad_id,
  DATE(timestamp) as date,
  
  COUNT(*) as total_clicks,
  APPROX_DISTINCT_COUNT(user_id) as unique_users,
  APPROX_DISTINCT_COUNT(ip_address) as unique_ips,
  
  -- Device split
  SUM(CASE WHEN device_type = 'MOBILE' THEN 1 ELSE 0 END) as mobile_clicks,
  SUM(CASE WHEN device_type = 'DESKTOP' THEN 1 ELSE 0 END) as desktop_clicks,
  
  -- Geography
  SUM(CASE WHEN country = 'US' THEN 1 ELSE 0 END) as us_clicks,
  SUM(CASE WHEN country = 'CA' THEN 1 ELSE 0 END) as ca_clicks,
  COUNT(DISTINCT country) as countries,
  
  -- Fraud metrics
  SUM(CASE WHEN is_suspicious THEN 1 ELSE 0 END) as suspicious_clicks
  
FROM clicks
WHERE timestamp >= DATE_SUB(CURRENT_DATE(), INTERVAL 1 YEAR)
GROUP BY
  advertiser_id,
  campaign_id,
  ad_id,
  date;
```

---

### Materialized View 3: Anomaly Detection

**Purpose**: Calculate metrics for fraud detection.

```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS ad_click_anomalies
CLUSTER BY ad_id, check_timestamp
PARTITION BY DATE(check_timestamp)
AS
SELECT
  ad_id,
  campaign_id,
  DATE_TRUNC(timestamp, HOUR) as check_timestamp,
  
  COUNT(*) as hourly_clicks,
  COUNT(DISTINCT ip_address) as unique_ips,
  
  -- Suspicious patterns
  COUNT(*) / COUNT(DISTINCT ip_address) as clicks_per_ip,
  
  -- Standard deviation from mean
  STDDEV(CASE WHEN EXTRACT(MINUTE FROM timestamp) % 5 = 0 THEN 1 ELSE 0 END) as time_distribution_zscore,
  
  -- Bot indicators
  SUM(CASE WHEN user_agent_hash = 'no-user-agent' THEN 1 ELSE 0 END) as no_user_agent_count,
  SUM(CASE WHEN device_type = 'UNKNOWN' THEN 1 ELSE 0 END) as unknown_device_count
  
FROM clicks
WHERE timestamp >= CURRENT_TIMESTAMP() - INTERVAL 7 DAY
GROUP BY
  ad_id,
  campaign_id,
  check_timestamp;
```

---

## 3. Database Functions

### Function 1: Calculate Dedup Hash

**Purpose**: Consistent hash calculation across the system.

```sql
-- PostgreSQL
CREATE OR REPLACE FUNCTION calculate_dedup_hash(
  p_page_url VARCHAR,
  p_ip_address VARCHAR,
  p_ad_id UUID,
  p_timestamp_second BIGINT
)
RETURNS VARCHAR(64) AS $$
BEGIN
  RETURN encode(
    digest(
      CONCAT(p_page_url, '|', p_ip_address, '|', p_ad_id::text, '|', p_timestamp_second::text),
      'sha256'
    ),
    'hex'
  );
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- BigQuery
CREATE TEMP FUNCTION calculate_dedup_hash(
  page_url STRING,
  ip_address STRING,
  ad_id STRING,
  timestamp_second INT64
)
RETURNS STRING
AS (
  TO_HEX(SHA256(CONCAT(
    page_url, '|', ip_address, '|', ad_id, '|', CAST(timestamp_second AS STRING)
  )))
);

-- Usage in queries
SELECT calculate_dedup_hash('https://example.com', '203.0.113.5', 'ad-123', 1705329045);
```

---

### Function 2: Classify Device Type

**Purpose**: Standardize device classification from user agent.

```sql
-- PostgreSQL
CREATE OR REPLACE FUNCTION classify_device(
  p_user_agent VARCHAR
)
RETURNS VARCHAR(16) AS $$
BEGIN
  CASE
    WHEN p_user_agent ILIKE '%Mobile%' OR p_user_agent ILIKE '%Android%' THEN
      RETURN 'MOBILE';
    WHEN p_user_agent ILIKE '%Tablet%' OR p_user_agent ILIKE '%iPad%' THEN
      RETURN 'TABLET';
    ELSE
      RETURN 'DESKTOP';
  END CASE;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- BigQuery
CREATE TEMP FUNCTION classify_device(user_agent STRING)
RETURNS STRING
AS (
  CASE
    WHEN REGEXP_CONTAINS(LOWER(user_agent), r'(mobile|android|iphone)') THEN 'MOBILE'
    WHEN REGEXP_CONTAINS(LOWER(user_agent), r'(tablet|ipad)') THEN 'TABLET'
    ELSE 'DESKTOP'
  END
);
```

---

### Function 3: Detect Suspicious Click Pattern

**Purpose**: Flag potentially fraudulent clicks based on behavior.

```sql
-- PostgreSQL
CREATE OR REPLACE FUNCTION is_suspicious_click(
  p_ip_address VARCHAR,
  p_clicks_from_ip INT,
  p_time_since_last_click INT,
  p_device_changes INT
)
RETURNS BOOLEAN AS $$
BEGIN
  -- IP had 10+ clicks in 1 second = likely bot
  IF p_clicks_from_ip > 10 AND p_time_since_last_click < 1000 THEN
    RETURN TRUE;
  END IF;
  
  -- Device fingerprint changed 5+ times in 10 seconds = likely fraud
  IF p_device_changes > 5 THEN
    RETURN TRUE;
  END IF;
  
  -- Normal click
  RETURN FALSE;
END;
$$ LANGUAGE plpgsql;
```

---

## 4. Stored Procedures

### Procedure 1: Cleanup Old Dedup Hashes

**Purpose**: Remove expired dedup records periodically.

```sql
-- PostgreSQL
CREATE OR REPLACE PROCEDURE cleanup_old_dedup_hashes()
LANGUAGE plpgsql
AS $$
DECLARE
  v_deleted_count INT;
BEGIN
  DELETE FROM click_dedup
  WHERE timestamp_inserted < CURRENT_TIMESTAMP - INTERVAL '1 hour';
  
  GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
  
  RAISE NOTICE 'Cleaned up % old dedup records', v_deleted_count;
  
  ANALYZE click_dedup;  -- Update statistics
END;
$$;

-- Schedule: Every 15 minutes via pg_cron
SELECT cron.schedule(
  'cleanup-dedup-hashes',
  '*/15 * * * *',
  'CALL cleanup_old_dedup_hashes()'
);
```

---

### Procedure 2: Process Batch Clicks

**Purpose**: Insert batch of clicks with deduplication and validation.

```sql
-- PostgreSQL
CREATE OR REPLACE PROCEDURE process_batch_clicks(
  p_clicks JSON
)
LANGUAGE plpgsql
AS $$
DECLARE
  v_click RECORD;
  v_dedup_hash VARCHAR(64);
  v_is_duplicate BOOLEAN;
  v_inserted INT := 0;
  v_rejected INT := 0;
BEGIN
  FOR v_click IN
    SELECT * FROM json_to_recordset(p_clicks) AS 
      t(
        click_id UUID,
        ad_id UUID,
        campaign_id UUID,
        ip_address VARCHAR,
        timestamp TIMESTAMP
      )
  LOOP
    -- Calculate dedup hash
    v_dedup_hash := calculate_dedup_hash(
      'page_url',
      v_click.ip_address,
      v_click.ad_id::text,
      EXTRACT(EPOCH FROM v_click.timestamp)::BIGINT
    );
    
    -- Check if duplicate
    SELECT EXISTS(
      SELECT 1 FROM click_dedup WHERE dedup_hash = v_dedup_hash
    ) INTO v_is_duplicate;
    
    IF NOT v_is_duplicate THEN
      -- Insert into dedup table
      INSERT INTO click_dedup (dedup_hash, ad_id, ip_address, timestamp_seconds)
      VALUES (v_dedup_hash, v_click.ad_id, v_click.ip_address, 
              EXTRACT(EPOCH FROM v_click.timestamp)::BIGINT);
      
      -- Would also send to Kafka here
      v_inserted := v_inserted + 1;
    ELSE
      v_rejected := v_rejected + 1;
    END IF;
  END LOOP;
  
  RAISE NOTICE 'Batch processed: % inserted, % rejected', v_inserted, v_rejected;
END;
$$;
```

---

### Procedure 3: Calculate Hourly Anomalies

**Purpose**: Detect fraud patterns hourly.

```sql
-- PostgreSQL
CREATE OR REPLACE PROCEDURE calculate_hourly_anomalies()
LANGUAGE plpgsql
AS $$
DECLARE
  v_start_hour TIMESTAMP;
  v_end_hour TIMESTAMP;
BEGIN
  v_start_hour := DATE_TRUNC('hour', CURRENT_TIMESTAMP - INTERVAL '1 hour');
  v_end_hour := DATE_TRUNC('hour', CURRENT_TIMESTAMP);
  
  INSERT INTO ad_click_anomalies
  SELECT
    ad_id,
    campaign_id,
    v_start_hour as check_timestamp,
    COUNT(*) as hourly_clicks,
    COUNT(DISTINCT ip_address) as unique_ips,
    COUNT(*)::FLOAT / COUNT(DISTINCT ip_address) as clicks_per_ip,
    STDDEV(EXTRACT(MINUTE FROM timestamp)) as time_distribution_zscore,
    SUM(CASE WHEN user_agent_hash IS NULL THEN 1 ELSE 0 END),
    SUM(CASE WHEN device_type = 'UNKNOWN' THEN 1 ELSE 0 END)
  FROM clicks
  WHERE timestamp >= v_start_hour AND timestamp < v_end_hour
  GROUP BY ad_id, campaign_id;
  
  RAISE NOTICE 'Hourly anomalies calculated for %', v_start_hour;
END;
$$;

-- Schedule: Every hour
SELECT cron.schedule(
  'calculate-hourly-anomalies',
  '0 * * * *',
  'CALL calculate_hourly_anomalies()'
);
```

---

## 5. BigQuery Specific Features

### Bigquery-Native Table Options

```sql
CREATE TABLE clicks (
  click_id STRING,
  ad_id STRING,
  ...
)
PARTITION BY DATE(timestamp)
CLUSTER BY ad_id, campaign_id, device_type
OPTIONS (
  description = 'All ad clicks partitioned by date',
  labels = [('environment', 'prod'), ('pii', 'yes')],
  partition_expiration_ms = 7776000000, -- 90 days
  require_partition_filter = true,  -- Force partition filter in queries
  kms_key_name = 'projects/project-id/locations/us/keyRings/ring/cryptoKeys/key'  -- Encryption
);
```

### BigQuery Snapshots (Time Travel)

```sql
-- Query data as of 24 hours ago
SELECT * FROM `project.dataset.clicks`
FOR SYSTEM_TIME AS OF TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
WHERE ad_id = 'ad-123';
```

---

## 6. ClickHouse Specific Features

### ReplacingMergeTree for Deduplication

```sql
CREATE TABLE click_dedup (
  dedup_hash String,
  ad_id UUID,
  ip_address String,
  timestamp_seconds Int64,
  version Int64,
  is_deleted UInt8
)
ENGINE = ReplacingMergeTree(version, is_deleted)
ORDER BY (dedup_hash, timestamp_seconds)
TTL timestamp_seconds + toIntervalHour(1)
SETTINGS storage_policy = 'tiered';
```

### Aggregating MergeTree for Pre-aggregation

```sql
CREATE TABLE clicks_minute_aggregates (
  ad_id UUID,
  timestamp_minute DateTime,
  click_count AggregateFunction(count),
  unique_users AggregateFunction(uniq, String)
)
ENGINE = AggregatingMergeTree()
ORDER BY (ad_id, timestamp_minute)
PARTITION BY toDate(timestamp_minute);
```

---

## 7. Indexing Strategy Summary

| Table | Index | Type | Purpose | Query Pattern |
|-------|-------|------|---------|---------------|
| clicks | (ad_id, timestamp) | Clustering | Primary lookup | Clicks per ad over time |
| clicks | (campaign_id, timestamp) | Index | Campaign analysis | Campaign performance |
| clicks | (country, device_type, timestamp) | Index | Geo/device breakdown | Geographic analysis |
| clicks_minute | (ad_id, timestamp_minute) | Clustering | Fast aggregates | Minute-level reporting |
| clicks_daily | (advertiser_id, date) | Clustering | Advertiser reports | Advertiser dashboard |
| click_dedup | (dedup_hash) | PK | Duplicate detection | Real-time dedup check |
| click_dedup | (timestamp_seconds) | Index | TTL cleanup | Scheduled cleanup |

---

## 8. Data Retention & Archival

### Hot Storage (0-7 days)
```
Location: NVMe SSD
Cost: ~$1/GB/month
Queries: Interactive (sub-second)
Update: Real-time
```

### Warm Storage (8-90 days)
```
Location: HDD/Object Storage
Cost: ~$0.1/GB/month
Queries: Operational (~10 seconds)
Update: Batch (hourly)
```

### Cold Storage (>90 days)
```
Location: Glacier/Archive
Cost: ~$0.02/GB/month
Queries: Analytical (minutes)
Update: Batch (daily)
Retrieval: On-demand
```

### Archival Policy

```sql
-- BigQuery
ALTER TABLE clicks
SET OPTIONS (
  partition_expiration_ms = 7776000000  -- 90 days
);

-- Then archive to Cloud Storage
EXPORT DATA OPTIONS(
  uri='gs://archive-bucket/clicks/2023/*',
  format='PARQUET',
  overwrite=true
) AS
SELECT * FROM clicks
WHERE DATE(timestamp) < DATE_SUB(CURRENT_DATE(), INTERVAL 90 DAY);
```

---

## 9. Disaster Recovery

### Backup Strategy

```
Daily:
- Full backup of dedup database
- Incremental backup of data warehouse
- Stored in 3 regions

Weekly:
- Full backup of both systems
- Tested restore

Monthly:
- Cross-account backup
```

### Recovery Time Objectives (RTO)

```
Data warehouse:
- RTO: 1 hour (restore from daily backup)
- RPO: 1 hour (data loss acceptable)

Dedup database:
- RTO: 15 minutes (warm standby)
- RPO: 1 minute (high availability)
```

---

## 10. Query Examples

### Example 1: Hourly Click Volume by Ad

```sql
-- BigQuery
SELECT
  ad_id,
  TIMESTAMP_TRUNC(timestamp, HOUR) as hour,
  COUNT(*) as clicks,
  COUNT(DISTINCT ip_address) as unique_ips,
  COUNT(DISTINCT user_id) as unique_users,
  ROUND(100.0 * SUM(CASE WHEN device_type = 'MOBILE' THEN 1 ELSE 0 END) / COUNT(*), 2) as mobile_pct
FROM clicks
WHERE ad_id = 'ad-123'
  AND timestamp >= CURRENT_TIMESTAMP() - INTERVAL 30 DAY
  AND timestamp < CURRENT_TIMESTAMP()
GROUP BY ad_id, hour
ORDER BY hour DESC;
```

### Example 2: Geographic Heatmap

```sql
-- ClickHouse
SELECT
  country,
  region,
  city,
  COUNT() as click_count,
  COUNT(DISTINCT user_id) as unique_users,
  uniq(ip_address) as unique_ips
FROM clicks
WHERE ad_id = 'ad-123'
  AND timestamp >= now() - INTERVAL 7 DAY
GROUP BY country, region, city
ORDER BY click_count DESC
LIMIT 100;
```

### Example 3: Device Performance Comparison

```sql
-- Redshift
SELECT
  device_type,
  browser_name,
  COUNT(*) as clicks,
  AVG(CASE WHEN journey_complete THEN 1.0 ELSE 0.0 END) as conversion_rate,
  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY session_duration) as p95_session_duration
FROM clicks
WHERE ad_id = 'ad-123'
  AND DATE(timestamp) >= CURRENT_DATE - INTERVAL '30 DAYS'
GROUP BY device_type, browser_name
ORDER BY clicks DESC;
```
