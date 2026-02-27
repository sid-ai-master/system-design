# Ad Click Aggregator - Implementation Details & Operational Guidelines

## 1. Deployment Architecture

### Infrastructure Stack

```
Development:
- Local: Docker Compose (Kafka, DB, Warehouse)
- Git: GitHub/GitLab

Staging:
- Cloud: AWS/GCP
- Environment: Kubernetes (EKS/GKE)
- Features: All production components, smaller scale

Production:
- Cloud: AWS/GCP (Multi-region)
- Orchestration: Kubernetes (EKS/GKE)
- High availability: Load balancing, auto-scaling
- Disaster recovery: Multi-region failover
```

### Kubernetes Deployment

```yaml
# deployment-click-api.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: click-aggregator-api
  namespace: production
  labels:
    app: click-aggregator
    component: api
spec:
  replicas: 10  # Initial replicas
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2
      maxUnavailable: 1
  selector:
    matchLabels:
      app: click-aggregator
      component: api
  template:
    metadata:
      labels:
        app: click-aggregator
        component: api
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: click-aggregator
      containers:
      - name: click-api
        image: registry.example.com/click-aggregator-api:v1.0.0
        imagePullPolicy: IfNotPresent
        
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: metrics
          containerPort: 8081
          protocol: TCP
        
        # Resource limits
        resources:
          requests:
            cpu: 2000m
            memory: 2Gi
          limits:
            cpu: 4000m
            memory: 4Gi
        
        # Liveness probe
        livenessProbe:
          httpGet:
            path: /health/live
            port: http
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        
        # Readiness probe
        readinessProbe:
          httpGet:
            path: /health/ready
            port: http
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
        
        # Environment variables
        env:
        - name: KAFKA_BROKERS
          value: "kafka-0.kafka.kafka.svc.cluster.local:9092,kafka-1.kafka.kafka.svc.cluster.local:9092,kafka-2.kafka.kafka.svc.cluster.local:9092"
        - name: DEDUP_DB_HOST
          valueFrom:
            configMapKeyRef:
              name: click-aggregator-config
              key: dedup-db-host
        - name: DEDUP_DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: click-aggregator-secrets
              key: dedup-db-password
        - name: LOG_LEVEL
          value: "INFO"
        
        # Logging
        volumeMounts:
        - name: logs
          mountPath: /var/log/click-aggregator
      
      # Affinity for distribution
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - click-aggregator
              topologyKey: kubernetes.io/hostname
      
      # Node selector
      nodeSelector:
        workload: api-tier
      
      volumes:
      - name: logs
        emptyDir: {}

---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: click-aggregator-api
  namespace: production
spec:
  type: LoadBalancer
  selector:
    app: click-aggregator
    component: api
  ports:
  - name: http
    port: 80
    targetPort: 8080
    protocol: TCP
  - name: https
    port: 443
    targetPort: 8443
    protocol: TCP

---
# horizontal-pod-autoscaler.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: click-aggregator-api-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: click-aggregator-api
  minReplicas: 5
  maxReplicas: 100
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1000"
```

---

## 2. CI/CD Pipeline

### GitHub Actions Pipeline

```yaml
# .github/workflows/deploy.yml
name: Build & Deploy Click Aggregator

on:
  push:
    branches: [main, develop]
    paths:
      - 'src/**'
      - 'Dockerfile'
      - 'kubernetes/**'
  pull_request:
    branches: [main, develop]

env:
  REGISTRY: registry.example.com
  IMAGE_NAME: click-aggregator-api

jobs:
  # Test Job
  test:
    runs-on: ubuntu-latest
    services:
      kafka:
        image: confluentinc/cp-kafka:7.0.0
        env:
          KAFKA_BROKER_ID: 1
          KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      zookeeper:
        image: confluentinc/cp-zookeeper:7.0.0
        env:
          ZOOKEEPER_CLIENT_PORT: 2181
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK
      uses: actions/setup-java@v3
      with:
        java-version: '17'
    
    - name: Run tests
      run: |
        mvn clean verify
    
    - name: Upload coverage
      uses: codecov/codecov-action@v3
      with:
        files: ./target/coverage.xml

  # Build Job
  build:
    needs: test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
    - uses: actions/checkout@v3
    
    - name: Build image
      run: |
        docker build . -t ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
        docker tag ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} \
                   ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
    
    - name: Push to registry
      run: |
        docker login -u ${{ secrets.REGISTRY_USER }} -p ${{ secrets.REGISTRY_PASSWORD }} ${{ env.REGISTRY }}
        docker push ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
        docker push ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest

  # Deploy to Staging
  deploy-staging:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'
    steps:
    - uses: actions/checkout@v3
    
    - name: Configure kubectl
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBE_CONFIG_STAGING }}" | base64 -d > $HOME/.kube/config
    
    - name: Deploy to staging
      run: |
        kubectl set image deployment/click-aggregator-api \
          click-api=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} \
          -n staging
        kubectl rollout status deployment/click-aggregator-api -n staging

  # Deploy to Production
  deploy-production:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: production  # Requires approval
    steps:
    - uses: actions/checkout@v3
    
    - name: Configure kubectl
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBE_CONFIG_PROD }}" | base64 -d > $HOME/.kube/config
    
    - name: Blue-Green Deployment
      run: |
        kubectl set image deployment/click-aggregator-api-green \
          click-api=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} \
          -n production
        kubectl rollout status deployment/click-aggregator-api-green -n production
        kubectl patch service click-aggregator-api -n production \
          -p '{"spec":{"selector":{"deployment":"green"}}}'
        kubectl rollout status deployment/click-aggregator-api-blue -n production --timeout=5m
```

---

## 3. Monitoring & Observability

### Prometheus Metrics

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'ad-click-aggregator'
    environment: 'production'

scrape_configs:
  # Kubernetes API server
  - job_name: 'kubernetes-apiservers'
    kubernetes_sd_configs:
    - role: endpoints
    scheme: https
    tls_config:
      ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
  
  # Application metrics
  - job_name: 'click-aggregator-api'
    kubernetes_sd_configs:
    - role: pod
      namespaces:
        names:
        - production
    relabel_configs:
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
      action: keep
      regex: true
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
      action: replace
      target_label: __metrics_path__
      regex: (.+)
    - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
      action: replace
      regex: ([^:]+)(?::\d+)?;(\d+)
      replacement: $1:$2
      target_label: __address__
  
  # Kafka metrics
  - job_name: 'kafka'
    static_configs:
    - targets:
      - kafka-0:9308
      - kafka-1:9308
      - kafka-2:9308
  
  # Database metrics
  - job_name: 'postgres'
    static_configs:
    - targets:
      - postgres-exporter:9187
```

### Key Metrics to Export

```python
# Application metrics
from prometheus_client import Counter, Histogram, Gauge

# Counters
clicks_received = Counter(
    'clicks_received_total',
    'Total clicks received',
    ['source', 'status']  # source: api|batch, status: valid|duplicate|spam
)

clicks_processed = Counter(
    'clicks_processed_total',
    'Total clicks processed',
    ['source', 'destination']  # destination: kafka|dlq
)

database_errors = Counter(
    'database_errors_total',
    'Database operation errors',
    ['operation', 'error_type']
)

# Histograms
api_request_duration = Histogram(
    'api_request_duration_seconds',
    'API request duration',
    ['endpoint', 'method', 'status_code'],
    buckets=(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0)
)

kafka_produce_latency = Histogram(
    'kafka_produce_latency_seconds',
    'Kafka message produce latency',
    buckets=(0.001, 0.01, 0.1, 1.0, 5.0, 10.0)
)

# Gauges
active_connections = Gauge(
    'active_database_connections',
    'Active database connections'
)

kafka_consumer_lag = Gauge(
    'kafka_consumer_lag',
    'Kafka consumer lag in messages',
    ['partition']
)

Processing example:
```

```python
# Track metric
with api_request_duration.labels(
    endpoint='/api/v1/clicks',
    method='POST',
    status_code='200'
).time():
    # Process request
    track_click(click)
```

### Grafana Dashboards

```json
{
  "dashboard": {
    "title": "Click Aggregator Overview",
    "panels": [
      {
        "title": "Clicks Per Second",
        "targets": [
          {
            "expr": "rate(clicks_received_total[1m])"
          }
        ],
        "type": "graph"
      },
      {
        "title": "API Latency P99",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, api_request_duration_seconds)"
          }
        ]
      },
      {
        "title": "Kafka Consumer Lag",
        "targets": [
          {
            "expr": "kafka_consumer_lag"
          }
        ]
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(database_errors_total[5m])"
          }
        ]
      }
    ]
  }
}
```

---

## 4. Testing Strategy

### Unit Tests

```python
# tests/test_click_validator.py
import unittest
from src.click_validator import ClickValidator

class TestClickValidator(unittest.TestCase):
    def setUp(self):
        self.validator = ClickValidator()
    
    def test_valid_click(self):
        click = {
            'click_id': 'uuid-123',
            'ad_id': 'ad-456',
            'ip_address': '203.0.113.5',
            'timestamp': '2024-01-15T10:30:00Z'
        }
        assert self.validator.is_valid(click) == True
    
    def test_missing_required_field(self):
        click = {
            'click_id': 'uuid-123',
            'ad_id': 'ad-456',
            # Missing ip_address
        }
        assert self.validator.is_valid(click) == False
    
    def test_invalid_uuid(self):
        click = {
            'click_id': 'not-uuid',
            'ad_id': 'ad-456',
            'ip_address': '203.0.113.5',
            'timestamp': '2024-01-15T10:30:00Z'
        }
        assert self.validator.is_valid(click) == False
    
    def test_duplicate_detection(self):
        click = {...}
        self.validator.track_click(click)
        assert self.validator.is_duplicate(click) == True
```

### Integration Tests

```python
# tests/integration/test_kafka_producer.py
import unittest
from testcontainers.kafka import KafkaContainer
from src.kafka_producer import KafkaClickProducer

class TestKafkaProducer(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kafka_container = KafkaContainer()
        cls.kafka_container.start()
        cls.producer = KafkaClickProducer(
            bootstrap_servers=cls.kafka_container.get_bootstrap_server()
        )
    
    @classmethod
    def tearDownClass(cls):
        cls.kafka_container.stop()
    
    def test_produce_click(self):
        click = {...}
        offset = self.producer.send_click(click)
        assert offset is not None
    
    def test_batch_produce(self):
        clicks = [{...}, {...}, {...}]
        self.producer.send_batch(clicks)
        # Verify all messages in topic
```

### Load Testing

```yaml
# load_test.yaml (k6 script)
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  vus: 100,           # 100 concurrent users
  duration: '10m',    # 10 minute test
  rps: 4000,          # 4000 requests per second
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01']
  }
};

export default function() {
  let click = {
    ad_id: 'ad-' + Math.floor(Math.random() * 10000),
    campaign_id: 'camp-' + Math.floor(Math.random() * 1000),
    ip_address: `203.0.113.${Math.floor(Math.random() * 255)}`,
    device_type: ['MOBILE', 'DESKTOP', 'TABLET'][Math.floor(Math.random() * 3)],
    referrer_url: 'https://example.com',
    timestamp: new Date().toISOString()
  };

  let response = http.post('https://api.adplatform.com/api/v1/clicks', JSON.stringify(click), {
    headers: { 'Content-Type': 'application/json' }
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
    'click_id returned': (r) => r.json().click_id !== undefined,
    'response time < 200ms': (r) => r.timings.duration < 200
  });
}
```

Run: `k6 run load_test.yaml`

### Chaos Testing

```python
# tests/chaos/test_kafka_failure.py
from chaos_lib import ChaosMonkey

def test_kafka_broker_failure():
    """Test system behavior when Kafka broker fails"""
    
    # Start chaos: Kill Kafka broker
    chaos = ChaosMonkey()
    chaos.kill_pod('kafka-0')
    
    # System should continue working (degraded)
    for i in range(1000):
        click = generate_click()
        response = post_click(click)
        assert response.status_code in [200, 503]  # Either success or temporary error
    
    # Restore
    chaos.restore_pod('kafka-0')
    
    # Wait for recovery
    time.sleep(30)
    
    # System should be fully functional again
    for i in range(1000):
        click = generate_click()
        response = post_click(click)
        assert response.status_code == 200
```

---

## 5. Logging Strategy

### Structured Logging

```python
# src/logger.py
import json
from pythonjsonlogger import jsonlogger

class ClickAggregatorLogger:
    def __init__(self, name):
        self.logger = logging.getLogger(name)
        handler = logging.StreamHandler()
        formatter = jsonlogger.JsonFormatter()
        handler.setFormatter(formatter)
        self.logger.addHandler(handler)
    
    def log_click_received(self, click_id, ad_id, source):
        self.logger.info('Click received', extra={
            'event': 'click_received',
            'click_id': click_id,
            'ad_id': ad_id,
            'source': source,
            'timestamp': datetime.utcnow().isoformat()
        })
    
    def log_duplicate_detected(self, click_id, dedup_hash):
        self.logger.warning('Duplicate click detected', extra={
            'event': 'duplicate_detected',
            'click_id': click_id,
            'dedup_hash': dedup_hash,
            'severity': 'WARNING'
        })
    
    def log_error(self, error_type, message, error_details):
        self.logger.error(message, extra={
            'event': 'error_occurred',
            'error_type': error_type,
            'error_details': error_details,
            'severity': 'ERROR'
        })
```

### Log Aggregation

```yaml
# filebeat.yml (ELK Stack)
filebeat.inputs:
- type: container
  enabled: true
  paths:
  - '/var/lib/docker/containers/*/*.log'

processors:
- add_docker_metadata:
- add_kubernetes_metadata:

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "logs-%{+yyyy.MM.dd}"

logging.level: info
logging.to_files: true
logging.files:
  path: /var/log/filebeat
  name: filebeat
```

---

## 6. Documentation

### API Documentation (Swagger/OpenAPI)

Location: `docs/api/openapi.yaml`

Generated from code using @ApiDoc decorators.

### Architecture Decision Records (ADRs)

```markdown
# ADR-001: Use Kafka for Click Ingestion

## Status: Accepted

## Context:
Need high-throughput click ingestion at 4,000 clicks/second.
DynamoDB can't handle this due to 1MB/sec partition limit.

## Decision:
Use Apache Kafka for click event queue.

## Consequences:
- Pro: Can handle 50+ MB/sec per partition
- Pro: Decouples producers from consumers
- Pro: Built-in durability and replication
- Con: Adds operational complexity
- Con: Requires Kafka expertise on team
```

### Runbooks

```markdown
# Runbook: High Kafka Consumer Lag

## Alert: consumer_lag > 5 minutes

## Investigation:
1. Check Kafka broker health
   ```
   kafka-broker-api-versions.sh --bootstrap-server localhost:9092
   ```

2. Check consumer group status
   ```
   kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group click-processor --describe
   ```

3. Check if warehouse is failing
   ```
   SELECT COUNT(*) FROM clicks 
   WHERE ingestion_time > NOW() - INTERVAL 5 MINUTE
   ```

## Resolution:
1. If broker down: Restart broker
2. If consumer crashed: Restart consumer pod
3. If warehouse slow: Scale warehouse, investigate query
4. If network issue: Check network metrics

## Escalation:
- If issue persists > 10 minutes: Page on-call
```

---

## 7. Security Considerations

### Authentication & Authorization

```yaml
# Kubernetes RBAC
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: production
  name: click-aggregator-role
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: click-aggregator-rolebinding
  namespace: production
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: click-aggregator-role
subjects:
- kind: ServiceAccount
  name: click-aggregator
  namespace: production
```

### TLS/SSL Configuration

```properties
# Kafka broker TLS
listeners=PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093
security.inter.broker.protocol.version=TLSv1.2

ssl.keystore.location=/etc/kafka/secrets/server.keystore.jks
ssl.keystore.password=changeit
ssl.key.password=changeit
ssl.truststore.location=/etc/kafka/secrets/server.truststore.jks
ssl.truststore.password=changeit
```

### Data Privacy

```
PII Handling:
- User ID: Hash (one-way) before storage in warehouse
- IP Address: Mask last 2 octets for IPv4
- User Agent: Hash before storage
- Location: Country/Region only (no city/coordinates unless needed)

Encryption:
- At rest: AES-256 for data warehouse
- In transit: TLS 1.2+ for all network traffic
- Keys: Stored in AWS KMS / Azure Key Vault

Data Retention:
- Click data: 90 days heat, then archive
- PII purged after 30 days
- Audit logs: 1 year
- Backups: 30 days then delete
```

---

## 8. Disaster Recovery Plan

### RTO & RPO Targets

```
Tier 1 (Critical):
- RTO: 15 minutes (recover quickly)
- RPO: 5 minutes (acceptable data loss)
- Examples: API layer, Kafka cluster

Tier 2 (Important):
- RTO: 1 hour
- RPO: 30 minutes
- Examples: Data warehouse, dedup DB

Tier 3 (Nice to have):
- RTO: 4 hours
- RPO: 1 hour
- Examples: Analytics archive
```

### Multi-Region Failover

```
Primary Region: us-east-1
Backup Region: us-west-2

Data Replication:
- Kafka: Multi-region cluster (mirrored)
- Database: Cross-region read replicas
- Backups: Stored in backup region

Failover Procedure:
1. Detect primary region failure (health checks)
2. Promote backup region to primary (DNS switch)
3. Notify customers of temporary degradation
4. Validate data consistency
5. Restore primary region when ready
```

---

## 9. Performance Optimization Checklist

- [ ] API response time P99 < 200ms
- [ ] Kafka consumer lag < 5 minutes
- [ ] Data warehouse query P99 < 10 seconds
- [ ] Dedup database latency < 50ms
- [ ] Cache hit rate > 80%
- [ ] Duplicate rate < 0.5%
- [ ] Error rate < 0.01%
- [ ] CPU utilization < 70%
- [ ] Memory utilization < 80%
- [ ] Disk I/O utilization < 60%

---

## 10. Cost Optimization Checklist

- [ ] Use spot instances for non-critical workloads
- [ ] Implement data tiering (hot/warm/cold)
- [ ] Archive logs after 30 days
- [ ] Compress data in transit
- [ ] Right-size Kafka partitions (avoid over-provisioning)
- [ ] Use reserved instances for 12+ month commitments
- [ ] Implement query result caching
- [ ] Optimize database indexes
- [ ] Monitor and alert on cost anomalies
- [ ] Review and delete unused resources quarterly

---

## Conclusion

This implementation guide covers all aspects of deploying, monitoring, and maintaining the Ad Click Aggregator system at scale. Follow these practices to ensure reliability, security, and cost-efficiency.
