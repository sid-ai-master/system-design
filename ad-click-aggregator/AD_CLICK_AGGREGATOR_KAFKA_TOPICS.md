# Ad Click Aggregator - Kafka Configuration & Topics

## 1. Overview

Apache Kafka is the central message queue for click events. This document specifies all topics, configurations, and performance tuning.

**Key Metrics**:
- Throughput: 4,000 clicks/second (normal), up to 40,000/second (peak)
- Data size: 1 KB per click (before compression)
- Volume: 10 TB/month
- Retention: 7 days (hot cache), 30 days archive

---

## 2. Kafka Cluster Configuration

### Broker Configuration

```properties
# server.properties (per broker)

# Network
broker.id=1
listeners=PLAINTEXT://kafka-1.internal:9092,SSL://kafka-1.internal:9093
advertised.listeners=PLAINTEXT://kafka-1.internal:9092,SSL://kafka-1.internal:9093
num.network.threads=24
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Log/Data
log.dir=/var/kafka/logs
num.log.partitions=10
default.replication.factor=3
min.insync.replicas=2
log.retention.hours=168  # 7 days hot, older data archived

# Performance
log.segment.bytes=1073741824  # 1 GB segments
log.cleanup.policy=delete
log.retention.check.interval.ms=300000  # Check every 5 min

# Compression
compression.codec=snappy
log.message.format.version=3.0

# Replication
replica.fetch.max.bytes=1048576
replica.socket.receive.buffer.bytes=65536
replica.socket.request.max.bytes=104857600

# Broker rack awareness
broker.rack=us-east-1a

# Metrics & Monitoring
metrics.num.samples=3
metrics.sample.window.ms=30000

# Security
security.inter.broker.protocol.version=3.0
```

### Cluster Topology

```
3 Availability Zones (AZ):
- AZ1: kafka-broker-1 (Rack us-east-1a)
- AZ2: kafka-broker-2 (Rack us-east-1b)
- AZ3: kafka-broker-3 (Rack us-east-1c)

Each broker:
- CPU: 16 cores
- RAM: 64 GB
- Disk: 2 TB NVMe SSD
- Network: 10 Gbps

Replication:
- 3 replicas per partition (one per AZ)
- min.insync.replicas = 2 (quorum writes)
- Rack-aware placement ensures diversity
```

### Zookeeper Configuration

```properties
# zookeeper configuration

server.1=zk-1.internal:2888:3888
server.2=zk-2.internal:2888:3888
server.3=zk-3.internal:2888:3888

dataDir=/var/zookeeper
clientPort=2181
tickTime=2000
autopurge.snapRetainCount=3
autopurge.purgeInterval=1
```

---

## 3. Topic: clicks (Main Event Topic)

### Topic Configuration

```bash
# Create topic
kafka-topics.sh --create \
  --topic clicks \
  --bootstrap-server localhost:9092 \
  --partitions 10 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config segment.ms=3600000 \
  --config compression.type=snappy \
  --config min.insync.replicas=2 \
  --config cleanup.policy=delete \
  --config max.message.bytes=1048576
```

### Detailed Configuration

```properties
# Topic: clicks
name=clicks
partitions=10
replication_factor=3

# Data retention
retention.ms=604800000              # 7 days hot
retention.bytes=null                # No size limit
segment.ms=3600000                  # 1 hour segments

# Compression
compression.type=snappy             # Snappy compression
message.format.version=3.0

# Durability
min.insync.replicas=2               # Quorum writes
unclean.leader.election.enable=false # Never failover to out-of-sync replica

# Performance
max.message.bytes=1048576           # 1 MB max message size
log.flush.interval.ms=null          # Let OS handle flushing
log.flush.interval.messages=null

# Cleanup
cleanup.policy=delete               # Just delete old messages
delete.retention.ms=86400000        # Keep tombstones for 1 day

# ACLs
security.protocol=SSL
```

### Partitioning Strategy

```
Partition assignment: hash(ad_id) % 10

Example:
- ad_id_1 % 10 = 1 → Partition 1
- ad_id_2 % 10 = 2 → Partition 2
- ad_id_3 % 10 = 3 → Partition 3
- ad_id_4 % 10 = 4 → Partition 4
- ad_id_5 % 10 = 5 → Partition 5
- ad_id_6 % 10 = 6 → Partition 6
- ad_id_7 % 10 = 7 → Partition 7
- ad_id_8 % 10 = 8 → Partition 8
- ad_id_9 % 10 = 9 → Partition 9
- ad_id_10 % 10 = 0 → Partition 0

Benefits:
- All clicks for same ad go to same partition
- Enables efficient data warehouse ingestion
- No hot partition (assuming even ad distribution)
```

### Partition Configuration Details

```
10 partitions with replication factor 3:
- Total replicas: 30 (10 partitions × 3 replicas)
- Distributed: 10 replicas per broker
- Throughput: 50 MB/sec per partition
- Total capacity: 500 MB/sec (100x our load)
- Cost: 3 brokers × $0.50/hour = $36/day (AWS estimate)

Scaling decision:
- Can support 2x growth without issues
- At 10x growth, add 10 more partitions
- Repartitioning is slow, better to over-provision
```

### Message Schema: Clicks

```json
{
  "click_id": "uuid (string)",
  "ad_id": "uuid (string)",
  "campaign_id": "uuid (string)",
  "advertiser_id": "uuid (string)",
  "user_id": "string (nullable)",
  
  "timestamp": "ISO-8601 (string)",
  "timestamp_ms": "long (unix timestamp in ms)",
  
  "referrer_url": "string",
  "landing_url": "string (nullable)",
  "page_domain": "string",
  
  "ip_address": "string",
  "ip_version": "4|6 (int)",
  
  "device": {
    "device_type": "MOBILE|DESKTOP|TABLET (string)",
    "os_name": "string",
    "os_version": "string",
    "browser_name": "string",
    "browser_version": "string",
    "user_agent_hash": "string"
  },
  
  "geo": {
    "country": "string",
    "region": "string",
    "city": "string (nullable)",
    "latitude": "double (nullable)",
    "longitude": "double (nullable)"
  },
  
  "session": {
    "session_id": "string (nullable)",
    "device_fingerprint": "string (nullable)"
  },
  
  "custom": {
    "field1": "string (nullable)",
    "field2": "string (nullable)"
  }
}
```

### Message Size Analysis

```
Click message breakdown:
- Static fields (uuids, strings): ~300 bytes
- Device info: ~150 bytes
- Geo info: ~100 bytes
- Custom fields: ~100 bytes
- JSON overhead: ~50 bytes
Total: ~700 bytes

After Snappy compression: ~280-350 bytes (50% compression)

Per-second at 4,000 clicks:
- Uncompressed: 4,000 × 700 = 2.8 MB/sec
- Compressed: 4,000 × 315 = 1.26 MB/sec
- Network: 1.26 MB/sec = 10 Mbps (well within capacity)
```

---

## 4. Topic: duplicate-hashes (Deduplication Topic - Compacted)

### Topic Configuration

```bash
# Create compacted topic for dedup hashes
kafka-topics.sh --create \
  --topic duplicate-hashes \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 3 \
  --config cleanup.policy=compact \
  --config retention.ms=3600000 \
  --config segment.ms=300000 \
  --config min.cleanable.dirty.ratio=0.1 \
  --config delete.retention.ms=3600000 \
  --config compression.type=snappy
```

### Detailed Configuration

```properties
# Topic: duplicate-hashes
name=duplicate-hashes
partitions=1                         # Single partition (all in one order)
replication_factor=3

# Compaction
cleanup.policy=compact               # Compact deleted records
min.insync.replicas=2

# Retention for deleted records
delete.retention.ms=3600000          # Keep deletion logs for 1 hour
file.delete.delay.ms=60000           # Wait 1 min before deleting

# Compaction tuning
min.cleanable.dirty.ratio=0.1        # Trigger compaction at 10% dirty
segment.ms=300000                    # 5 min segments
segment.bytes=268435456              # 256 MB max segment

# Performance
compression.type=snappy
lz4.hash.aggressive=true             # For faster log cleanup
```

### Message Schema: Dedup Hashes

```json
{
  "key": "dedup_hash (string)",
  "value": {
    "hash": "sha256 hash (string)",
    "ad_id": "uuid (string)",
    "page_url": "string",
    "ip_address": "string",
    "timestamp_second": "long",
    "timestamp_ms": "long",
    "is_deleted": "boolean (null for live)"
  }
}
```

### Compacted Topic Behavior

```
Time 0: Hash A inserted
  Kafka log: [A: {hash, data}]

Time 1: Hash B inserted
  Kafka log: [A: {hash, data}, B: {hash, data}]

Time 2: Hash A expires (deletion)
  Kafka log: [A: {hash, null}, B: {hash, data}]

After compaction:
  Kafka log: [B: {hash, data}]  <- Hash A removed

Pattern:
- Live hashes: High retention (1 hour)
- Expired hashes: Compacted away
- Consumer can rebuild in-memory state by replaying
```

---

## 5. Topic: clicks-dlq (Dead Letter Queue)

### Topic Configuration

```bash
# Create DLQ topic for failed messages
kafka-topics.sh --create \
  --topic clicks-dlq \
  --bootstrap-server localhost:9092 \
  --partitions 10 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --config min.insync.replicas=2 \
  --config compression.type=gzip
```

### Message Schema: DLQ

```json
{
  "original_topic": "clicks",
  "original_partition": "0",
  "original_offset": "12345",
  "original_key": "ad_id_123",
  "original_value": {...original click...},
  
  "error_type": "VALIDATION|PROCESSING|TIMEOUT",
  "error_message": "Invalid ad_id format",
  "error_stacktrace": "...",
  
  "attempt_count": "3",
  "first_failure_timestamp": "ISO-8601",
  "last_failure_timestamp": "ISO-8601",
  
  "metadata": {
    "consumer_group": "click-processor",
    "failed_consumer": "click-processor-1",
    "processing_duration_ms": "5000"
  }
}
```

### DLQ Processing Policy

```
1. Auto-sent DLQ triggers:
   - Validation errors: Invalid JSON, missing required fields
   - Schema errors: Field type mismatch
   - Size errors: Message > 1 MB
   
2. Manual DLQ triggers:
   - Processing errors (caught exceptions)
   - Timeout (> 30 seconds to process)
   - Resource exhaustion (DB connection, memory)

3. DLQ retention:
   - Keep for 30 days (debugging window)
   - Alert if DLQ rate > 0.1% (indicate problem)
   - Daily DLQ report to ops team
   
4. DLQ replay:
   - Can replay specific messages to main topic
   - After fixing underlying issue
   - Via admin dashboard
```

---

## 6. Topic: audit-log (Audit & Compliance)

### Topic Configuration

```bash
# Create audit topic for compliance logging
kafka-topics.sh --create \
  --topic audit-log \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=7884000000 \
  --config min.insync.replicas=2
```

### Message Schema: Audit Log

```json
{
  "event_type": "CLICK|QUERY|ADMIN_ACTION",
  "action": "TRACK|DELETE|EXPORT",
  
  "timestamp": "ISO-8601",
  "request_id": "uuid",
  
  "actor": {
    "user_id": "uuid",
    "email": "user@example.com",
    "ip_address": "string"
  },
  
  "resource": {
    "resource_type": "CLICK|CAMPAIGN|AD",
    "resource_id": "uuid"
  },
  
  "operation": {
    "operation": "TRACK|UPDATE|DELETE|EXPORT",
    "outcome": "SUCCESS|FAILURE",
    "details": {}
  },
  
  "compliance": {
    "gdpr_relevant": "boolean",
    "pii_involved": "boolean",
    "cost_impact": "number"
  }
}
```

---

## 7. Topic: metrics (Real-time Metrics Stream)

### Topic Configuration

```bash
# Create metrics topic for real-time monitoring
kafka-topics.sh --create \
  --topic metrics \
  --bootstrap-server localhost:9092 \
  --partitions 5 \
  --replication-factor 2 \
  --config retention.ms=86400000 \
  --config compression.type=snappy
```

### Message Schema: Metrics

```json
{
  "timestamp": "ISO-8601",
  "metric_type": "COUNTER|GAUGE|HISTOGRAM",
  
  "name": "clicks_per_second",
  "value": "3847",
  
  "tags": {
    "partition": "0",
    "ad_id": "ad-123",
    "region": "us-east-1"
  },
  
  "percentiles": {
    "p50": "123",
    "p99": "456"
  }
}
```

---

## 8. Producer Configuration (Application Servers)

```properties
# producer.properties

# Connection
bootstrap.servers=kafka-1:9092,kafka-2:9092,kafka-3:9092
security.protocol=SSL
ssl.truststore.location=/path/to/truststore.jks
ssl.truststore.password=changeit

# Reliability
acks=all                            # Wait for all replicas
retries=3
retry.backoff.ms=100
max.in.flight.requests.per.connection=5

# Idempotence & Transactions
enable.idempotence=true             # Enable idempotent writes
transactional.id=click-producer-1   # Transaction ID for recovery

# Performance
compression.type=snappy
batch.size=32768                    # 32 KB batches
linger.ms=10                        # Wait 10 ms to batch
buffer.memory=134217728             # 128 MB buffer

# Timeouts
request.timeout.ms=30000            # 30 sec timeout
delivery.timeout.ms=120000          # 2 min total timeout

# Metrics
metrics.num.samples=3
metrics.sample.window.ms=30000

# Custom
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=io.confluent.kafka.serializers.KafkaAvroSerializer
schema.registry.url=http://schema-registry:8081
```

### Producer Code Example (Java)

```java
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

public class ClickProducer {
    private final KafkaProducer<String, Click> producer;
    private final String topic = "clicks";
    
    public ClickProducer(Properties props) {
        this.producer = new KafkaProducer<>(props, 
            new StringSerializer(), 
            new AvroSerializer<>());
    }
    
    public void trackClick(Click click) {
        // Idempotent send with retries
        ProducerRecord<String, Click> record = 
            new ProducerRecord<>(
                topic,
                click.getAdId(),     // Key: use ad_id for partitioning
                click
            );
        
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Failed to send click: " + click.getClickId(), exception);
                // Send to DLQ or retry
            } else {
                logger.debug("Click sent to partition " + 
                    metadata.partition() + " offset " + metadata.offset());
            }
        });
    }
    
    public void close() {
        producer.flush();
        producer.close();
    }
}
```

---

## 9. Consumer Configuration (Kafka Consumers → Data Warehouse)

```properties
# consumer.properties

# Connection
bootstrap.servers=kafka-1:9092,kafka-2:9092,kafka-3:9092
group.id=click-processor
security.protocol=SSL

# Reliability
auto.offset.reset=earliest         # Start from beginning if offset lost
enable.auto.commit=false            # Manual offset management
max.poll.records=500                # Batch size
max.poll.interval.ms=300000         # 5 min between polls

# Performance
fetch.min.bytes=1048576             # 1 MB min batch
fetch.max.wait.ms=5000              # 5 sec max wait
compression.type=snappy

# Offset management
auto.commit.interval.ms=null        # Manual only
connections.max.idle.ms=540000      # 9 min idle timeout

# Metrics
metrics.num.samples=3

# Deserialization
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer
schema.registry.url=http://schema-registry:8081
```

### Consumer Code Example (Python)

```python
from confluent_kafka import Consumer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroDeserializer
import json

class ClickConsumer:
    def __init__(self):
        # Schema Registry
        sr_config = {'url': 'http://schema-registry:8081'}
        schema_registry_client = SchemaRegistryClient(sr_config)
        avro_deserializer = AvroDeserializer(schema_registry_client)
        
        # Kafka Consumer
        self.consumer = Consumer({
            'bootstrap.servers': 'kafka-1:9092,kafka-2:9092,kafka-3:9092',
            'group.id': 'click-processor',
            'auto.offset.reset': 'earliest',
            'enable.auto.commit': False,
            'max.poll.records': 500,
            'security.protocol': 'SSL',
            'fetch.min.bytes': 1048576
        })
        
        self.consumer.subscribe(['clicks'])
        self.avro_deserializer = avro_deserializer
        self.warehouse = WarehouseConnection()
    
    def process_batch(self):
        """Process batch of clicks and write to warehouse"""
        messages = self.consumer.poll(timeout=5.0, max_records=500)
        
        if not messages:
            return
        
        clicks = []
        offsets_to_commit = []
        
        for msg in messages:
            if msg.error():
                self.send_to_dlq(msg)
                continue
            
            try:
                # Deserialize Avro
                click = self.avro_deserializer(msg.value(), None)
                clicks.append(click)
                
                # Track offset
                offsets_to_commit.append(
                    TopicPartition(
                        msg.topic(),
                        msg.partition(),
                        msg.offset() + 1
                    )
                )
            except Exception as e:
                logger.error(f"Failed to process message: {e}")
                self.send_to_dlq(msg, error=str(e))
        
        # Batch insert to warehouse
        if clicks:
            self.warehouse.insert_batch(clicks)
            
            # Commit offsets after successful insert
            self.consumer.commit(offsets=offsets_to_commit)
            logger.info(f"Committed {len(clicks)} clicks")
    
    def send_to_dlq(self, message, error=None):
        """Send failed message to DLQ"""
        dlq_producer = Producer({'bootstrap.servers': 'kafka-1:9092'})
        dlq_producer.produce(
            topic='clicks-dlq',
            key=message.key(),
            value=json.dumps({
                'original_topic': message.topic(),
                'original_partition': message.partition(),
                'original_offset': message.offset(),
                'error': error
            })
        )
        dlq_producer.flush()

# Main loop
if __name__ == '__main__':
    consumer = ClickConsumer()
    
    while True:
        try:
            consumer.process_batch()
        except KeyboardInterrupt:
            break
        except Exception as e:
            logger.error(f"Unexpected error: {e}")
    
    consumer.consumer.close()
```

---

## 10. Monitoring & Alerting

### Key Metrics to Monitor

```
Kafka Broker Metrics:
- UnderReplicatedPartitions: Should be 0
- OfflinePartitionsCount: Should be 0
- ActiveControllerCount: Should be 1
- Leadership changes per hour: Should be < 1
- Log flush latency: Should be < 100ms
- Replication lag: Should be < 1000 messages

Topic Metrics (clicks):
- Messages in per second: Target 4,000
- Bytes in per second: Target 4 MB/sec
- Partition lag: Should be < 100k messages
- Message rate by partition: Should be balanced

Consumer Metrics:
- Consumer lag: Should be < 5 minutes
- Messages consumed per second: Should match producer rate
- Processing latency: P99 should be < 30 seconds
- Commit frequency: Should commit every 10 minutes

Error Metrics:
- DLQ message rate: Should be < 0.1% of main topic
- Producer failures: Should be 0
- Consumer failures: Should be rare
```

### Alert Thresholds

```
CRITICAL:
- UnderReplicatedPartitions > 0
- OfflinePartitionsCount > 0
- Consumer lag > 1 hour
- DLQ rate > 1% of main traffic

WARNING:
- Consumer lag > 10 minutes
- DLQ rate > 0.1%
- Log flush latency > 500ms
- Replication lag > 10k messages
- Unbalanced partition load (max/min > 2x)
```

### Monitoring Infrastructure

```
Kafka Metrics → Prometheus
  ↓
Grafana Dashboard (visualize)
  ↓
AlertManager (trigger alerts)
  ↓
PagerDuty/Slack (notify ops)

Dashboard Views:
1. Cluster Health
   - Broker status
   - Partition replication
   - Controller leadership

2. Topic Performance
   - Message rate
   - Bytes per second
   - Partition distribution

3. Consumer Group
   - Lag per partition
   - Processing latency
   - Throughput

4. Errors & DLQ
   - Error rate
   - DLQ messages
   - Failed partitions
```

---

## 11. Performance Tuning

### Optimization 1: Batch Size

```
Current: batch.size=32kb, linger.ms=10

For lower latency:
batch.size=4kb, linger.ms=1

For higher throughput:
batch.size=512kb, linger.ms=100
```

### Optimization 2: Compression

```
snappy:
- Compression ratio: 50%
- Latency: +5-10ms
- Recommended for our use case

lz4:
- Compression ratio: 60%
- Latency: +2-5ms
- Better for CPU-constrained

gzip:
- Compression ratio: 80%
- Latency: +50-100ms
- For long-term archive only
```

### Optimization 3: Network Tuning

```
# Increase socket buffers
socket.send.buffer.bytes=1048576
socket.receive.buffer.bytes=1048576

# TCP tuning
net.ipv4.tcp_window_scaling=1
net.ipv4.tcp_rmem=4096 87380 67108864
net.ipv4.tcp_wmem=4096 65536 67108864

# Network interrupt coalescing
ethtool -C eth0 rx-usecs 75
```

### Optimization 4: Disk I/O

```
# Use SSD with proper alignment
# Schedule log cleanup during low-traffic hours

# Disable page cache writeback
vm.dirty_ratio=5
vm.dirty_background_ratio=2

# Increase file descriptors
ulimit -n 1000000
```

---

## 12. Disaster Recovery

### Backup Strategy

```
Main cluster: 3 brokers in 3 AZs
Backup cluster: 1 broker for restoration testing
Archive: Weekly full topic backups to S3
```

### Failover Procedure

```
1. Broker failure:
   - Kafka automatically rebalances
   - Consumer lag may spike temporarily
   - Typical time: 30-60 seconds

2. Topic/Partition recovery:
   - Restore from weekly backup
   - Repopulate main topic
   - Consumers restart from backup

3. Complete cluster failure:
   - Restore from S3 backup
   - Rebuild ZooKeeper quorum
   - Verify consumer offsets
   - Restart consumers
```

---

## 13. Upgrading Kafka

```
Rolling upgrade process:
1. Update broker 1
   - Stop broker 1
   - Upgrade binary/config
   - Start broker 1
   - Wait for rebalance
   - Monitor lag

2. Repeat for broker 2 and 3

Total downtime: 0 minutes (rolling)
Typical duration: 30 minutes per broker
```

---

## 14. Kafka Connect Integrations (Optional)

### S3 Sink Connector (Archive)

```json
{
  "name": "s3-sink-clicks",
  "config": {
    "connector.class": "io.confluent.connect.s3.S3SinkConnector",
    "topics": "clicks",
    "s3.bucket.name": "kafka-archive-bucket",
    "s3.region": "us-east-1",
    "s3.compression.type": "snappy",
    "format.class": "io.confluent.connect.s3.format.parquet.ParquetFormat",
    "flush.size": 1000000,
    "rotate.interval.ms": 3600000,
    "partition.duration.ms": 86400000,
    "path.format": "year=YYYY/month=MM/day=dd/hour=HH",
    "locale": "en_US",
    "timezone": "UTC",
    "timestamp.extractor": "RecordField",
    "timestamp.field": "timestamp"
  }
}
```

This archives old messages to S3 for cold storage.

---

## 15. Cost Estimation

### Monthly Costs

```
Kafka Cluster (3 brokers, m5.4xlarge):
- Compute: 3 × $800 = $2,400

Storage:
- Hot (10TB): 10× $1.00 = $10
- Network: 4000 clicks/sec × 315 bytes
  = 1.26 MB/sec = 38.6 TB/month
  = 38.6 × $0.02 = $0.77

Data Transfer:
- Egress to consumers: 38.6 TB × $0.02 = $0.77

Total: ~$2,400/month (mainly compute)

At scale (10x traffic):
- Additional 2-3 brokers: ~$800-1200
- Network transfer increases slightly
- Still dominated by compute costs
```

---

## Conclusion

This Kafka configuration provides:
- **Reliability**: 3-replica replication across AZs
- **Performance**: 500 MB/sec total capacity (100x+ our load)
- **Data Integrity**: Compacted topics for dedup, DLQ for errors
- **Operability**: Monitoring, alerting, disaster recovery
- **Cost Efficiency**: Right-sized for current and 10x growth
