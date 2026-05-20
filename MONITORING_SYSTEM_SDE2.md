# Monitoring System Design (SDE 2 Interview Version)

**Goal:** Design a Prometheus-like metrics monitoring system that scales to 1M samples/sec, supports pull and push ingestion, and enables threshold-based alerting.

---

## 1. Requirements (2 min)

### Functional
- **Ingestion**: Pull (scrape `/metrics` endpoints) and push (accept metrics from apps)
- **Storage**: Store and compress metrics efficiently
- **Query**: Instant queries (value at timestamp T) and range queries (values from T1 to T2)
- **Alerting**: Evaluate rules periodically, fire webhooks when thresholds triggered

### Non-Functional
- **Latency**: <200ms p99 for queries
- **Throughput**: 1M samples/sec ingestion, 10k queries/sec
- **Retention**: 15 days raw data (live dashboards), 1 year downsampled for historical analysis
- **Availability**: 99.9% uptime (single region with HA)

### Scale Assumptions
- 100M active time-series (metric + labels combination)
- 5 labels per series average (job, instance, region, environment, service)
- 5k alert rules
- 10 metrics per service × 10k services = 100k metric types

---

## 2. Detailed Architecture Overview (8 min)

### 2.1 High-Level Multi-Tier Architecture

```
TIER 1: CLIENT & DISCOVERY
┌──────────────────────────────────────────────────────────────┐
│  Application Servers (1000s)                                │
│  ├─ Expose /metrics endpoint (Prometheus format)            │
│  ├─ Health checks: :8080/health                             │
│  └─ Dashboards & UIs (grafana, custom)                      │
└────────────────────┬─────────────────────────────────────────┘
                     │
TIER 2: INGESTION & ROUTING (AWS Region us-east-1)
┌─────────────────────┴──────────────────────────────────────┐
│                   AWS Application Load Balancer             │
│  (Routes: /metrics → Scraper, /push → PushGateway,         │
│   /query → QueryAPI, /api/v1/* → Apps)                     │
│  ├─ TLS termination (*.monitoring.internal)               │
│  ├─ Target Groups: {Scraper, PushGateway, IngestionGW}     │
│  └─ Cross-zone load balancing, connection draining (30s)   │
└────────┬────────────────────────────────────────────────────┘
         │
    ┌────┴──────────┬──────────────┬──────────────┐
    │               │              │              │
    v               v              v              v
┌──────────┐  ┌──────────────┐  ┌─────────────┐  ┌──────────────┐
│ Scraper  │  │ PushGateway  │  │Ingestion    │  │ (Reserved)   │
│Deployment│  │  Deployment  │  │Gateway      │  │              │
│(Stateless)│  │  (Stateless) │  │(Stateless)  │  │              │
│          │  │              │  │ Deployment  │  │              │
│ 2 replicas  │  3 replicas    │  │ 6 replicas  │  │              │
│ 2 CPU, 4GB  │ 2 CPU, 8GB     │  │ 4 CPU, 16GB │  │              │
│ 100MB SSD   │ 1GB ephemeral  │  │ None        │  │              │
└──────────┘  └──────────────┘  └──────┬───────┘  └──────────────┘
                                        │
TIER 3: MESSAGE BUS & PERSISTENCE
                                        v
┌─────────────────────────────────────────────────────────────┐
│        Apache Kafka Cluster (StatefulSet)                   │
│  ├─ Brokers: kafka-0, kafka-1, kafka-2, kafka-3, kafka-4  │
│  ├─ Replication Factor: 3 (RF=3, ISR≥2)                    │
│  ├─ Topic: raw_metrics                                     │
│  ├─ Partitions: 32 (maps 1:1 to Ingester shards)           │
│  ├─ Retention: 24 hours, compression: snappy               │
│  ├─ Per broker: 8 CPU, 32GB RAM, 500GB SSD (PVC)          │
│  └─ Leader election timeout: 10s                           │
└─────────────────────┬───────────────────────────────────────┘
                      │
    ┌─────────────────┼──────────────────┐
    │                 │                  │
    v                 v                  v
TIER 4: STATEFUL STORAGE & INDEXING (Persistent)
┌────────┐  ┌────────┐  ┌────────┐     ...    ┌────────┐  ┌────────┐
│Ingester│  │Ingester│  │Ingester│           │Ingester│  │Ingester│
│   0    │  │   1    │  │   2    │           │   30   │  │   31   │
├────────┤  ├────────┤  ├────────┤           ├────────┤  ├────────┤
│WAL + Mem  │WAL + Mem  │WAL + Mem           │WAL + Mem  │WAL + Mem│
│32CPU,550GB│32CPU,550GB│32CPU,550GB        │32CPU,550GB│32CPU,550GB
│gRPC:9000  │gRPC:9000  │gRPC:9000          │gRPC:9000  │gRPC:9000│
│Metrics:90 │Metrics:90 │Metrics:90         │Metrics:90 │Metrics:90│
│20GB WAL   │20GB WAL   │20GB WAL           │20GB WAL   │20GB WAL │
│(PVC-SSD)  │(PVC-SSD)  │(PVC-SSD)          │(PVC-SSD)  │(PVC-SSD)│
│50GB cache │50GB cache │50GB cache         │50GB cache │50GB cache│
│(PVC-SSD)  │(PVC-SSD)  │(PVC-SSD)          │(PVC-SSD)  │(PVC-SSD)│
└────────┘  └────────┘  └────────┘           └────────┘  └────────┘
    │           │           │                   │           │
    └───────────┴───────────┴───────────────────┴───────────┘
                        │
TIER 5: LONG-TERM STORAGE & ANALYTICS
                        v
┌──────────────────────────────────────────────────────────────┐
│           AWS S3 (Multi-AZ, Cross-Region Replica)           │
│  Bucket: metrics-prod (versioning disabled, MFA delete off) │
│  ├─ Layout: s3://metrics-prod/{tenant}/{metric}/{shard}/    │
│  ├─ Storage Classes:                                        │
│  │  ├─ STANDARD (0-7d): $0.023/GB-mo (hot queries)         │
│  │  ├─ STANDARD_IA (7-90d): $0.0125/GB-mo (warm,rare)      │
│  │  └─ GLACIER (90d+): $0.004/GB-mo (archive, slow)        │
│  ├─ Lifecycle: auto-transition, deletion at 2yr            │
│  ├─ Encryption: AES-256 (default)                          │
│  ├─ Versioning: Disabled (blocks are immutable)            │
│  └─ Total ≈ 8TB hot + 100GB downsampled                    │
└──────────────────────────────────────────────────────────────┘
                        │
TIER 6: QUERY & ALERTING (Stateless, scales freely)
         ┌──────────────┴──────────────┬────────────────┬─────────────┐
         │                             │                │             │
         v                             v                v             v
    ┌─────────────┐            ┌────────────────┐  ┌──────────┐  ┌──────┐
    │  QueryAPI   │            │ AlertingEngine │  │Dashboard │  │Redis │
    │ Deployment  │            │  Deployment    │  │Service   │  │Cluster
    │            │            │               │  │(Custom)  │  │      │
    │ 10 replicas│            │ 3 replicas    │  │1 replica │  │1 pri
    │ 4CPU, 16GB │            │ 2CPU, 8GB     │  │2CPU,8GB  │  │+2 rep
    │ None       │            │ None          │  │None      │  │16CPU,
    │(stateless) │            │(stateless)    │  │          │  │64GB
    └────────────┘            └────────────────┘  └──────────┘  │PVC:200
                                     │                │         │GB
    AWS ALB (Query Endpoint)         │                │         │      │
    ├─ Path: /query → QueryAPI      WebhooksOut      APIIn    │      │
    ├─ Path: /dashboard → Dashboard │                │        │     │
    └─ TLS + auth (JWT/API key)      │                │        └──────┘
                                     │                │
         ┌───────────────────────────┘                │
         │                                            │
         v                                            v
    Slack/PagerDuty                            AlertManagement
    WebhookReceiver                            Dashboard
```

### 2.2 Component Breakdown by Tier

#### **Tier 1: Client & Observability**
| Component | Type | Count | Purpose |
|---|---|---|---|
| **Application Servers** | External | 10k+ | Expose `/metrics` (Prometheus format); connect to Scraper for pull |
| **Push Clients** | External | 1000s | Batch jobs, Lambda, short-lived containers; POST to PushGateway |
| **Dashboards** | Internal | 1 | Grafana or custom UI; displays QueryAPI results |

#### **Tier 2: Ingestion & Request Routing**
| Component | Type | Count | Specs | Purpose |
|---|---|---|---|---|
| **AWS ALB** | AWS Managed | 1 (HA) | ≤400 req/sec per ALB, 1M connections | Route traffic to Scraper, PushGW, IngestionGW; TLS termination |
| **Scraper** | Pod/Container | 2 | 2 CPU, 4GB RAM, 100MB storage | Discover targets via K8s API; scrape `/metrics` every 15s |
| **PushGateway** | Pod/Container | 3 | 2 CPU, 8GB RAM, 1GB ephemeral | Accept `/metrics/put/{job}` POSTs; deduplicate; hold 24h |
| **IngestionGateway** | Pod/Container | 6 | 4 CPU, 16GB RAM, no storage | Validate samples; shard by `hash(metric+tenant)`; rate limit |

#### **Tier 3: Message Bus**
| Component | Type | Count | Specs | Purpose |
|---|---|---|---|---|
| **Kafka Broker** | StatefulSet Pod | 5 | 8 CPU, 32GB RAM, 500GB SSD (PVC) | Persist raw metrics; partition by shard (32 partitions) |
| **Kafka Topic** | Logical | 1 | 32 partitions, RF=3, 24h retention | Topic: `raw_metrics` |

#### **Tier 4: Stateful Storage**
| Component | Type | Count | Specs | Purpose |
|---|---|---|---|---|
| **Ingester** | StatefulSet Pod | 32 | 32 CPU, 550GB RAM, 20GB WAL + 50GB cache (PVC SSD) | 1:1 per Kafka partition; in-memory TSDB; flush blocks to S3 |
| **Ingester PVC-WAL** | Persistent Volume | 32 | 20GB each (SSD gp3) | Write-Ahead Log; survive crashes; replay on restart |
| **Ingester PVC-Cache** | Persistent Volume | 32 | 50GB each (SSD gp3) | Label index; cardinality tracking; block metadata |

#### **Tier 5: Long-Term Storage**
| Component | Type | Size | Purpose |
|---|---|---|---|
| **S3 Bucket (STANDARD)** | AWS S3 | 1TB (0-7d) | Hot storage: 2h raw blocks; low-latency queries |
| **S3 Bucket (STANDARD_IA)** | AWS S3 | 10TB (7-90d) | Warm storage: 1h downsampled; infrequent queries |
| **S3 Bucket (GLACIER)** | AWS S3 | 40TB (90d+) | Archive: historical; rarely accessed |

#### **Tier 6: Query & Alerting**
| Component | Type | Count | Specs | Purpose |
|---|---|---|---|---|
| **QueryAPI** | Deployment Pod | 10 | 4 CPU, 16GB RAM, no storage | Fan-out queries to Ingester + S3; cache in Redis; aggregations |
| **AlertingEngine** | Deployment Pod | 3 | 2 CPU, 8GB RAM, no storage | Evaluate alert rules every 15s; state in Redis; fire webhooks |
| **Dashboard** | Deployment Pod | 1 | 2 CPU, 8GB RAM | Display metrics; serve dashboards |
| **Redis Master** | StatefulSet Pod | 1 | 8 CPU, 32GB RAM, 200GB PVC | Alert state; query cache; session data |
| **Redis Replica** | StatefulSet Pod | 2 | 8 CPU, 32GB RAM ea | HA failover for Redis; read replica for state queries |
| **AWS ALB** | AWS Managed | 1 (HA) | ≤400 req/sec | Route query + alert endpoints; TLS termination |

### 2.3 Network Topology & Data Flow

```
                        ┌─ Internet Gateway (IGW)
                        │
        ┌───────────────┴────────────────┐
        │                                │
    AZ-1                              AZ-2
(us-east-1a)                    (us-east-1b)
│                                  │
├─ ALB (Active)                 ├─ ALB (Standby)
│  └─ Scraper Pod (1)           │  └─ Scraper Pod (1)
│  └─ PushGW Pod (1-2)          │  └─ PushGW Pod (1-2)
│  └─ IngestionGW (3)           │  └─ IngestionGW (3)
│                                │
├─ Kafka Pod (2-3)              ├─ Kafka Pod (1-2)
│  └─ PVC: 500GB @ gp3          │  └─ PVC: 500GB @ gp3
│                                │
├─ Ingester[0-15] (16 pods)     ├─ Ingester[16-31] (16 pods)
│  └─ Each: 32CPU, 550GB RAM    │  └─ Each: 32CPU, 550GB RAM
│  └─ WAL PVC: 20GB             │  └─ WAL PVC: 20GB
│  └─ Index PVC: 50GB           │  └─ Index PVC: 50GB
│                                │
├─ QueryAPI (5 pods)            ├─ QueryAPI (5 pods)
│  └─ 4 CPU, 16GB ea            │  └─ 4 CPU, 16GB ea
│                                │
└─ Redis Master                 └─ Redis Replica
   16 CPU, 64GB                    (read-only)

CROSS-AZ:
├─ Kafka broadcast: all brokers replicate across AZs
├─ Ingester affinity: spread across nodes/AZs
└─ S3 (multi-AZ): all blocks replicated automatically
```

### 2.4 Service Mesh & Networking

**Internal Services (ClusterIP, no external access):**
```yaml
Service: scraper
  └─ Selector: app=scraper
  └─ Port: 9090 (metrics only)

Service: push-gateway
  └─ Selector: app=push-gateway
  └─ Port: 9091 (HTTP push endpoint)
  └─ Port: 9090 (metrics)
  └─ Type: LoadBalancer (for external push clients)

Service: ingestion-gateway
  └─ Selector: app=ingestion-gateway
  └─ Port: 8080 (internal routing)
  └─ Port: 9090 (metrics)

Service: kafka-headless
  └─ ClusterIP: None (StatefulSet discovery)
  └─ Port: 9092 (broker port)

Service: ingester
  └─ ClusterIP: None (StatefulSet discovery)
  └─ Port: 9000 (gRPC for queries)
  └─ Port: 9090 (metrics)

Service: redis
  └─ Port: 6379 (internal state store)
  └─ Sentinel: 26379 (failover coordination)

Service: query-api
  └─ Selector: app=query-api
  └─ Port: 8080 (query endpoint)
  └─ Port: 9090 (metrics)
  └─ Type: LoadBalancer (external)

Service: alerting-engine
  └─ Selector: app=alerting-engine
  └─ Port: 9090 (metrics)
```

**Network Policies (restrict traffic):**
```yaml
# Scraper can only reach app targets + IngestionGateway
NetworkPolicy: scraper-egress
  → Pods: app=scraper
  → Allowed to: port 9090 (apps), port 8080 (ingestion-gateway)

# IngestionGateway can only reach Kafka
NetworkPolicy: ingestion-gw-egress
  → Pods: app=ingestion-gateway
  → Allowed to: port 9092 (kafka)

# Ingester can only reach S3 + Redis
NetworkPolicy: ingester-egress
  → Pods: app=ingester
  → Allowed to: S3 API, port 6379 (redis)

# QueryAPI can reach Ingester + S3 + Redis
NetworkPolicy: query-api-egress
  → Pods: app=query-api
  → Allowed to: port 9000 (ingester), S3 API, port 6379 (redis)
```

### 2.5 Database & Storage Layer

| Layer | Component | Capacity | Redundancy | Failover |
|---|---|---|---|---|
| **Hot Cache** | Redis (1 master + 2 replicas) | 64GB (state + query cache) | Replication: 1 → 2 | Sentinel auto-promote; <5s |
| **Persistent WAL** | EBS gp3 (Ingester pods) | 20GB × 32 = 640GB total | Local: PVC attachment | Pod restart; re-read from Kafka |
| **Index & Metadata** | EBS gp3 (Ingester pods) | 50GB × 32 = 1.6TB total | Local: PVC attachment | Pod restart; re-scan S3 |
| **Message Queue** | Kafka (5 brokers, RF=3) | 500GB × 5 = 2.5TB; 12.96TB with replication | Replication across AZs | ISR≥2; leader election <10s |
| **Block Storage** | S3 STANDARD | 1TB (0-7d, hot) | Auto cross-AZ replication | Read from replica AZ |
| **Archive** | S3 STANDARD_IA + GLACIER | 50TB+ (70-365d) | Geographic redundancy (optional) | Slow retrieval (hours) |

### 2.6 Load Balancer & Traffic Routing

**AWS Application Load Balancer (ALB) - Primary Region (us-east-1)**

```
ALB Endpoint: monitoring.internal:443 (TLS)
│
├─ Rule 1: Host header = metrics.internal → Target Group: Scraper
│  ├─ Targets: scraper-pod-0, scraper-pod-1
│  ├─ Health Check: GET /health:9090 every 30s
│  ├─ Deregistration delay: 30s
│  └─ Stickiness: off (Scraper is stateless)
│
├─ Rule 2: Path /push/* → Target Group: PushGateway
│  ├─ Targets: push-gateway-pod-0, push-gateway-pod-1, push-gateway-pod-2
│  ├─ Health Check: GET /health:9091 every 30s
│  ├─ Deregistration delay: 30s
│  └─ Stickiness: off (PushGateway is stateless)
│
├─ Rule 3: Path /ingest/* → Target Group: IngestionGateway
│  ├─ Targets: ingestion-gateway-pod-{0..5}
│  ├─ Health Check: GET /health:8080 every 30s
│  ├─ Deregistration delay: 30s
│  └─ Stickiness: off (stateless)
│
├─ Rule 4: Path /query* → Target Group: QueryAPI
│  ├─ Targets: query-api-pod-{0..9}
│  ├─ Health Check: GET /health:8080 every 30s
│  ├─ Deregistration delay: 30s
│  └─ Stickiness: 24h cookie (cache locality)
│
└─ Rule 5: Path /api/v1/alerts → Target Group: AlertingEngine
   ├─ Targets: alerting-engine-pod-{0..2}
   ├─ Health Check: GET /health:9090 every 30s
   └─ Deregistration delay: 30s

ALB Listener: Port 443 (TLS)
├─ Certificate: *.monitoring.internal (ACM)
├─ Security Policy: ELBSecurityPolicy-TLS-1-2-2017-01
├─ Backend Protocol: HTTP (ALB → target)
└─ Connection timeout: 60s
```

---

## 3. Core Components (12 min)

### 3.1 Scraper (Pull Ingestion)
**What it does:** Periodically fetches `/metrics` from application endpoints.

**Key decisions:**
- Discover targets via static config, Kubernetes API, or Consul
- Scrape interval: 15 seconds (configurable per job)
- Handle failures: retry with exponential backoff, mark unhealthy targets down after 5 failures
- Batch samples: collect 1000 samples or 5 seconds worth before pushing

**Why pull?** 
- Apps don't need to know about the monitoring system (low coupling)
- Easier to debug (can curl `/metrics` manually)
- Scraper controls backpressure (don't pull from slow apps)

---

### 3.2 PushGateway (Push Ingestion)
**What it does:** Accepts metrics from batch jobs, short-lived containers, or applications that can't be scraped.

**How it works:**
- Apps POST metrics to `/metrics/put/{job}/{instance}`
- Gateway holds samples, de-duplicates them (drop older value if same timestamp)
- Periodically flushed to ingestion pipeline (every 30 seconds)
- Samples expire after 1 day (if not updated, assume job died)

**Why push?**
- Batch jobs finish before we'd scrape them (need push to capture final metrics)
- Serverless/short-lived containers don't have stable endpoints
- Apps can emit custom application-level metrics easily

---

### 3.3 IngestionGateway (Stateless Router)
**What it does:** Validates and routes metrics to Kafka.

**Pipeline:**
1. Receive batch from Scraper or PushGateway
2. Validate: timestamp not in future, value is a finite number
3. Add context: tenant ID (from header), region, cluster (from config)
4. **Shard by** `hash(metric_name + tenant_id) % num_shards` → determines Kafka partition
5. Rate limit: reject if tenant exceeds 1M samples/sec
6. Publish to Kafka topic `raw_metrics`

**Why Kafka?**
- **Durability**: If Ingester crashes, 1-day Kafka retention lets it replay
- **Decoupling**: Scraper/Gateway don't care if Ingester is slow
- **Load balancing**: Ingesters consume at their own pace

---

### 3.4 Ingester (Core Stateful Service)
**What it does:** Maintains active time-series in memory, writes to durable storage.

**Lifecycle:**
1. Consume samples from assigned Kafka partition (1:1 mapping)
2. **Write-Ahead Log (WAL)**: Append sample to disk immediately (durability)
3. **In-memory index**: Track `{metric, labels} -> time_series`
4. **Memory buffer**: Hold last 2 hours of samples per series
5. Every 2 hours: **Flush block** to object storage (S3)
   - Sort samples by timestamp
   - Compress (delta encoding + gzip)
   - Write metadata + index + chunks
   - Delete from WAL
6. On restart: Replay WAL to rebuild memory state

**Memory footprint:**
- 100M active series × ~500 bytes/series = ~50 GB
- Sample buffer (2h): ~500 GB
- **Total per Ingester: ~550 GB** (plan for 32 Ingesters per region)

**Why in-memory?**
- Queries for recent data (most common) are fast (<100ms)
- Flushing to S3 is expensive (network), so batch every 2 hours
- WAL guarantees durability—memory isn't a risk

---

### 3.5 Object Storage (S3/GCS)
**What it does:** Long-term immutable storage of metric blocks.

**Block structure:**
```
bucket/
  tenant_id/
    metric_name/
      shard_0/
        2024-05-19_00-02_block.tar.gz  (2h raw)
        2024-05-19_day_block.tar.gz    (24h, compressed)
        ...
```

**Retention policy:**
- **0-15 days**: Raw blocks (Level 0), hot storage, query any timestamp
- **15-365 days**: 1-hour downsampled, cold storage, hourly granularity only
- **>365 days**: Delete

**Downsampling (automatic after 15 days):**
- Read raw samples from 15d ago
- Bucket into 1-hour windows
- Compute: sum, min, max, count per window (depends on metric type)
- Store as new block, delete old raw block

**Why tiering?**
- Most queries are last 7 days (recent dashboards) → need low latency
- Historical analysis (year-old data) can tolerate 500ms latency
- Saves 90% storage cost by downsampling old data

---

### 3.6 QueryAPI (Stateless Fan-Out)
**What it does:** Executes instant and range queries.

**Query types:**
```
Instant:  up{job="api"} @ 1715086800
  -> find all series matching {job="api"}
  -> return their value at timestamp T

Range:    up{job="api"}[5m] @ 1715086800
  -> find all series matching {job="api"}
  -> return samples from T-5m to T
```

**Execution:**
1. **Label index lookup**: "Which series IDs match these labels?"
   - Inverted index: {label_name=label_value} → [series_id, series_id, ...]
2. **Fan-out queries:**
   - To **Ingester** (memory): get recent samples <2h
   - To **block scanner** (S3): get persisted blocks overlapping time range
3. **Merge & deduplicate**: Combine Ingester + S3 results, sort by timestamp
4. **Caching**: Cache popular queries in Redis (30-second TTL)

**Why fan-out?**
- Ingester has hot data (recent 2h), S3 has cold data (historical)
- Must query both when range spans both time windows
- Parallel fan-out is faster than sequential

**Example: Query for `rate(http_requests_total[5m])`**
- Actually fetch samples for last 10 minutes (need 2 samples for rate)
- Calculate difference / time elapsed
- Return rate (samples/sec)

---

### 3.7 AlertingEngine (Stateless Rule Evaluator)
**What it does:** Periodically evaluates alert rules, fires webhooks.

**Rule example:**
```yaml
- alert: HighErrorRate
  expr: rate(http_requests_failed_total[5m]) > 0.05
  for: 5m          # must be true for ≥5 min before firing
  annotations:
    summary: "API errors > 5%, see: https://dashboard/api"
```

**State machine:**
```
INACTIVE 
  ↓ (condition true for 5m)
PENDING 
  ↓ (still true)
FIRING → [send webhook] → [keep firing, repeat alerts every 5m] → [condition false] → INACTIVE
```

**Evaluation every 15 seconds:**
```
1. For each alert rule:
   a. Execute query: rate(http_requests_failed_total[5m])
   b. For each result series (per job, per instance):
      - Check: value > 0.05?
      - If yes + duration ≥ 5m: FIRE alert (webhook call)
      - If no + was firing: RESOLVE alert (send resolved webhook)

2. Send to webhook (Slack, PagerDuty, custom):
   {
     "alerts": [
       {
         "status": "firing",
         "labels": { "alertname": "HighErrorRate", "job": "api" },
         "value": 0.087,
         "startsAt": "2024-05-19T10:30:00Z"
       }
     ]
   }
```

**State storage:**
- Store alert state in **Redis** (not in evaluator process)
- Evaluator is stateless → can be restarted anytime
- Any evaluator instance can read/update Redis state
- Multiple evaluators per rule = fault tolerance

**Why Redis?**
- In-memory, fast (10ms latency per state lookup)
- Survives evaluator crashes
- Multiple evaluators can coordinate

---

## 4. Deep Dive: Handling High Cardinality (8 min)

**Problem:** If you have metrics like `http_request_duration{method, path, status, user_id, ...}`, the number of unique combinations can explode:
- 10 HTTP methods × 1000 URI paths × 10 status codes × 1M users = **100B combinations**
- Each series takes memory and storage → system crashes

**Solution: Cardinality Limits**

```yaml
cardinality_limits:
  http_requests_total: 10000        # max 10k unique {label combinations}
  default: 1000000                  # default for all metrics
```

**Enforcement (at ingestion):**
```
on sample arrival:
  1. Compute label_hash = hash({labels})
  2. Is this a new series? (check if label_hash exists)
  3. If yes:
     - Count current series for this metric
     - If count+1 > limit: DROP sample, log "cardinality exceeded"
     - Else: Add to index
  4. Append sample to series
```

**Example:**
- Metric: `http_requests_total` with limit 10k
- Current series count: 10,010
- New request with labels `{method=PATCH, path=/api/v3/new, status=201}`
- Action: **DROP** (already at limit), increment counter `cardinality_limit_exceeded_total`

**How to detect & fix:**
```
Monitoring yourself:
  - Query: cardinality_limit_exceeded_total (count of drops)
  - If high: investigate which metric is problematic
  - Common case: don't use request_id, user_id as labels
  
Design guidance:
  ✓ DO use: job, instance, region, environment, method, status
  ✗ DON'T use: request_id, user_id, arbitrary IDs (causes explosion)
```

---

## 5. Data Flow Examples (3 min)

### Write Path (Ingestion)
```
1. App exposes: http://api:9090/metrics
   cpu_usage{job="api",instance="host1"} 45.2

2. Scraper fetches every 15s
   → POST /ingest/metrics (IngestionGateway)

3. IngestionGateway:
   - Validates timestamp, value
   - Adds tenant: "acme" (from header)
   - Shards: hash("cpu_usage" + "acme") % 32 = partition 5
   - Rate checks: acme tenant uses 500k/M samples, OK
   - Publishes to Kafka partition 5

4. Ingester[5] consumes:
   - Appends to WAL: /data/wal/segment_001.log
   - Updates in-memory: series["cpu_usage{job=api,instance=host1}"] = TimeSeries{...}
   - Adds sample: {ts: 1715086800000, value: 45.2}

5. Every 2 hours:
   - Reads all samples for this series
   - Sorts, compresses, writes to S3
   - Deletes from memory + WAL
```

### Read Path (Query)
```
1. User: GET /query?query=up{job="api"}&time=1715086800

2. QueryAPI:
   - Parses: metric=up, label_filter={job="api"}, time=1715086800
   - Checks Redis cache: miss
   - Looks up label index: "job=api" → [series_id_1, series_id_5, ...]
   
3. Fan-out:
   a. Query Ingester: "give me samples for series_id_1 from time T-5m to T"
      - Ingester returns from memory buffer ✓ (recent data)
   b. Query S3 block scanner: "give me samples for series_id_1 from T-5m to T"
      - Scans blocks, decompresses, returns
   
4. Merge results, deduplicate, sort by timestamp

5. Return JSON:
   {
     "result": [
       {
         "metric": {"__name__": "up", "job": "api", "instance": "host1"},
         "value": [1715086800, "1"]
       }
     ]
   }

6. Cache result in Redis (30s TTL)
```

---

## 6. Key Design Decisions & Tradeoffs (5 min)

| Decision | Why | Tradeoff |
|---|---|---|
| **2-hour blocks** | Good balance between file count and compaction frequency | Each query might touch 7+ blocks (slight I/O overhead) |
| **Kafka partitioning** | Enables horizontal scaling (1 partition = 1 Ingester) | Adds operational complexity (rebalancing) |
| **Cardinality limits** | Prevents runaway memory/storage cost | Users must think about label design (developer friction) |
| **Downsampling after 15d** | Most queries are on recent data; saves 90% long-term storage | Historical queries have lower resolution (1h granularity) |
| **Pull + Push hybrid** | Pull for infrastructure, push for app metrics | Must maintain both systems |
| **Stateless QueryAPI** | Easy to scale horizontally | Extra fan-out logic (query both Ingester + S3) |
| **Stateless AlertEvaluator** | Easy to scale, replication for HA | Adds dependency on Redis for state |

---

## 7. Common Interview Follow-Ups (5 min)

**Q: How do you scale from 1M to 10M samples/sec?**
- Increase Kafka partition count: 32 → 256 partitions
- Add more Ingesters: 1 per new partition
- Add more QueryAPIs behind load balancer (stateless, easy)
- Shard by `hash(metric + tenant)` — not just metric alone, to distribute tenants evenly

**Q: What happens if an Ingester crashes?**
- Kafka retains data for 1 day
- New Ingester process starts, reads from Kafka offset where previous crashed
- Replays samples back into memory
- Worst case: lose last 30 min of data if S3 block upload was slow

**Q: How do you handle out-of-order samples?**
- Ingester allows samples within 1h of latest timestamp
- During block flush: re-sort all samples by timestamp before compressing
- Block can have samples mixed if they arrived late—query merges them correctly

**Q: Why not just store in a database like Cassandra?**
- TSDB write pattern: append-only, monotonic timestamps per series
- Cassandra good for random reads/writes; we do sequential writes + bulk reads
- Object storage (S3) cheaper + immutable (no deletes/updates)
- Can compress better since blocks are finalized

**Q: How do you prevent one metric from consuming all resources?**
- Cardinality limits (hard cap on series count per metric)
- Resource quotas per shard (CPU, memory throttling)
- Monitor `cardinality_limit_exceeded_total` → alert if high

**Q: How does the system do aggregations (sum, avg)?**
- Range query returns all matching series + their samples
- Client (QueryAPI) post-processes: sums values across series, computes average
- Pre-computation via "recording rules" for common aggregations (e.g., `job:requests_per_sec`)

---

## 8. 10-Minute Summary (For Communicating Under Time Pressure)

**"I'd design a monitoring system with 5 core layers:"**

1. **Ingestion (Pull + Push)**
   - Scraper: discover and periodically fetch `/metrics`
   - PushGateway: accept metrics from batch jobs
   - Both route to IngestionGateway

2. **Message Queue (Kafka)**
   - Shards samples by `hash(metric + tenant)`
   - 1-day retention as queue + durability buffer
   - Decouples ingestion from storage

3. **Storage (Ingester → S3)**
   - Ingester: in-memory for hot data (2 hours), WAL for durability
   - Every 2 hours: flush block to S3
   - S3: cold storage, indexed blocks for efficient querying

4. **Query**
   - Fan-out: query Ingester (recent) + S3 blocks (historical)
   - Label index for fast series lookup
   - Cache popular queries in Redis

5. **Alerting**
   - Stateless rule evaluator (state in Redis)
   - Periodic evaluation (every 15s) against QueryAPI
   - State machine: INACTIVE → PENDING → FIRING
   - Fire webhooks (Slack, PagerDuty)

**Key tradeoffs:**
- Pull vs. push: use both (pull for infra, push for apps)
- Retention: 15 days raw (hot), 1 year downsampled (cold)
- Cardinality: enforce limits per metric to prevent explosion
- Scaling: horizontal via Kafka shards + stateless services

---

## 9. What to Build First (MVP)

**Phase 1 (Week 1-2):**
- Scraper + IngestionGateway + local file storage
- Basic QueryAPI (query from local files)
- Result: Can scrape metrics, store, query

**Phase 2 (Week 3-4):**
- Add Kafka in between (durability + replay)
- Build Ingester (WAL + memory buffer)
- Add AlertingEngine (threshold rules only)

**Phase 3 (Month 2):**
- S3 storage + block compaction
- Downsampling + tiered retention
- Multi-tenancy

**Phase 4 (Month 3+):**
- Recording rules (PromQL expressions)
- Advanced aggregations (percentiles, histograms)
- UI/Dashboard

---

## 10. Gotchas & Lessons Learned

1. **Label cardinality is critical.** One high-cardinality metric can crash the system. Default to conservative limits.

2. **Timestamps are monotonic per series.** Native sorting helps; out-of-order is rare but plan for it.

3. **Memory is the bottleneck, not CPU.** 100M series in memory requires careful buffer management.

4. **Kafka retention can be expensive.** Balance between buffer size and cost; 1 day is typical.

5. **Downsampling must preserve monotonicity.** For counters (http_requests_total), downsample via SUM not AVG.

6. **Alert state must be durable.** Don't lose alert state across restarts—use external store (Redis).

7. **Queries can be expensive.** Cache aggressively; warn users about label explosion.

---

## Quick Reference: API Endpoints

| Path | Method | Purpose |
|---|---|---|
| POST `/ingest/metrics` | Batches from Scraper/PushGateway |
| GET `/query` | Instant query (value at timestamp T) |
| GET `/query_range` | Range query (samples from T1 to T2) |
| POST `/api/v1/alerts/fire` | Fire alert webhook (internal) |
| POST `/api/v1/silences` | Create silence (mute alert) |
| GET `/metrics` | Self-metrics (system health) |

---

## References
- **Prometheus**: Pull-based scraping, PromQL, metric types
- **Cortex**: Multi-tenant architecture, distributed components
- **VictoriaMetrics**: Compression efficiency, downsampling strategies

