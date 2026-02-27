# Ad Click Aggregator - API Specifications

## 1. Click Tracking API

### Purpose:
Receives ad click events from frontend/tracking pixels and records them for processing.

### Endpoint: POST /api/v1/clicks

```
Base URL: https://api.adplatform.com
Path: /api/v1/clicks
Method: POST
Content-Type: application/json
```

### Request Schema:

```json
{
  "ad_id": "uuid",
  "campaign_id": "uuid",
  "user_id": "string (optional, null if anonymous)",
  "ip_address": "string",
  "user_agent": "string",
  "device_type": "enum: MOBILE|DESKTOP|TABLET",
  "os_name": "string (e.g., iOS, Android, Windows)",
  "browser_name": "string (e.g., Chrome, Safari)",
  "referrer_url": "string (page where ad was clicked)",
  "landing_url": "string (where user is directed)",
  "geo": {
    "country": "string (ISO-3166-1 alpha-2)",
    "region": "string (state/province)",
    "city": "string",
    "latitude": "float (optional)",
    "longitude": "float (optional)"
  },
  "timestamp": "ISO-8601 datetime (e.g., 2024-01-15T10:30:45Z)",
  "click_id": "uuid (for idempotency)",
  "session_id": "uuid (to group clicks from same session)",
  "custom_fields": {
    "search_term": "string (optional)",
    "ad_format": "string (optional, e.g., banner, native, video)",
    "bid_amount": "float (optional)"
  }
}
```

### Required Fields:
- `ad_id` - Which ad was clicked
- `campaign_id` - Which campaign contains the ad
- `ip_address` - User's IP for fraud detection
- `device_type` - Mobile/desktop/tablet classification
- `referrer_url` - Page where click originated
- `timestamp` - When click occurred
- `click_id` - Unique identifier for idempotency

### Optional Fields:
- `user_id` - If user is logged in
- `user_agent` - Full browser User-Agent string
- `landing_url` - Destination after click
- `geo` - Geographic information (can be derived from IP)
- `custom_fields` - Application-specific data

### Response: Success (200 OK)

```json
{
  "status": "accepted",
  "click_id": "uuid",
  "request_id": "string (for tracing)",
  "timestamp": "ISO-8601 datetime",
  "message": "Click recorded successfully"
}
```

### Response: Duplicate (409 Conflict)

```json
{
  "status": "duplicate",
  "click_id": "uuid",
  "request_id": "string",
  "message": "Duplicate click detected"
}
```

### Response: Invalid (400 Bad Request)

```json
{
  "status": "invalid",
  "request_id": "string",
  "errors": [
    {
      "field": "ad_id",
      "message": "Invalid UUID format"
    }
  ]
}
```

### Response: Rate Limited (429 Too Many Requests)

```json
{
  "status": "rate_limited",
  "request_id": "string",
  "retry_after": 60,
  "message": "Too many requests from this IP"
}
```

### Performance Requirements:
- **Latency**: P99 < 100ms
- **Throughput**: 4,000 clicks/second sustained
- **Peak**: Handle 40,000 clicks/second (10x spike)
- **Availability**: 99.99% uptime

### Rate Limiting Strategy:
```
IP-based rate limiting:
- 100 clicks/IP/second (prevents obvious spam)
- 10,000 clicks/IP/hour (prevents sustained attack)

User-based rate limiting (if logged in):
- 1,000 clicks/user/day (prevent fraudulent inflation)

Ad-based rate limiting (per advertiser):
- Auto-scale based on expected daily volume
```

### Error Handling:
```
RETRYABLE ERRORS (408, 429, 500, 502, 503):
- Client: Retry with exponential backoff
- Max retries: 3 times
- Backoff: 100ms, 200ms, 400ms

NON-RETRYABLE ERRORS (400, 401, 403, 404):
- Client: Don't retry
- Log error for debugging
```

### Idempotence:
```
Mechanism: click_id (UUID)
- Client generates unique click_id
- If same click_id received twice: Return 200 OK, don't re-process
- Dedup database maintains click_id → hash mapping
- Validity window: 1 hour (old duplicates cleaned up)
```

---

## 2. Analytics Query API

### Purpose:
Allows advertisers to query click analytics and metrics.

### Endpoint: POST /api/v1/analytics/query

```
Base URL: https://api.adplatform.com
Path: /api/v1/analytics/query
Method: POST
Content-Type: application/json
Authentication: Bearer <jwt_token>
```

### Request Schema:

```json
{
  "query_type": "enum: TIMESERIES|BREAKDOWN|COMPARISON|CUSTOM",
  "filters": {
    "ad_ids": ["uuid1", "uuid2"],
    "campaign_ids": ["uuid1", "uuid2"],
    "advertiser_id": "uuid",
    "date_range": {
      "start": "2024-01-01",
      "end": "2024-01-31"
    },
    "geo": {
      "countries": ["US", "CA"],
      "regions": ["NY", "CA"]
    },
    "device_types": ["MOBILE", "DESKTOP"],
    "browsers": ["Chrome", "Safari"]
  },
  "group_by": ["date", "ad_id", "device_type"],
  "metrics": ["clicks", "impressions", "ctr", "cost", "revenue"],
  "granularity": "enum: MINUTE|HOUR|DAY|WEEK|MONTH",
  "order_by": {
    "metric": "clicks",
    "direction": "DESC"
  },
  "pagination": {
    "limit": 100,
    "offset": 0
  }
}
```

### Query Types:

#### TIMESERIES
```json
{
  "query_type": "TIMESERIES",
  "filters": {
    "ad_ids": ["ad-123"],
    "date_range": {"start": "2024-01-01", "end": "2024-01-31"}
  },
  "granularity": "DAY",
  "metrics": ["clicks", "ctr"]
}
```

Expected response: Time-indexed data showing metrics over time.

#### BREAKDOWN
```json
{
  "query_type": "BREAKDOWN",
  "filters": {
    "advertiser_id": "adv-456",
    "date_range": {"start": "2024-01-01", "end": "2024-01-31"}
  },
  "group_by": ["ad_id", "device_type"],
  "metrics": ["clicks", "revenue"]
}
```

Expected response: Data grouped by specified dimensions.

#### COMPARISON
```json
{
  "query_type": "COMPARISON",
  "filters": {
    "ad_ids": ["ad-1", "ad-2"],
    "date_range": {"start": "2024-01-01", "end": "2024-01-31"}
  },
  "metrics": ["clicks", "ctr", "avg_position"]
}
```

Expected response: Side-by-side comparison of metrics across ads.

#### CUSTOM
```json
{
  "query_type": "CUSTOM",
  "sql": "SELECT ad_id, COUNT(*) as clicks, DATE_TRUNC(timestamp, DAY) as date FROM clicks WHERE advertiser_id = ? GROUP BY ad_id, date ORDER BY date DESC LIMIT 100"
}
```

Expected response: Raw query results.

### Response Schema: Success (200 OK)

```json
{
  "status": "success",
  "request_id": "string",
  "query_execution_time_ms": 145,
  "data": [
    {
      "date": "2024-01-01",
      "ad_id": "ad-123",
      "device_type": "MOBILE",
      "clicks": 1523,
      "impressions": 50000,
      "ctr": 0.03046,
      "cost": 45.23,
      "revenue": 67.89
    }
  ],
  "pagination": {
    "limit": 100,
    "offset": 0,
    "total_rows": 450,
    "has_more": true
  }
}
```

### Response Schema: Rate Limited (429)

```json
{
  "status": "rate_limited",
  "request_id": "string",
  "message": "Query cancelled: processing took too long",
  "timeout_seconds": 30,
  "retry_after": 60
}
```

### Query Timeout:
```
Regular queries: 30 seconds max
Power user queries: 120 seconds max (after approval)
Custom SQL: 120 seconds max
```

### Caching:
```
Results cached for:
- TIMESERIES on historical data: 24 hours
- BREAKDOWN: 6 hours
- COMPARISON: 1 hour (more volatile)
- CUSTOM: No caching

Cache invalidation:
- Manual via API endpoint
- Or automatic after TTL expires
```

### Performance Requirements:
- **P50 latency**: < 500ms
- **P99 latency**: < 5 seconds
- **Max concurrent queries**: 100 per advertiser
- **Queries per minute**: 1000 per advertiser

---

## 3. Admin/Management APIs

### 3.1 Campaign Management

#### Create Campaign
```
POST /api/v1/admin/campaigns
{
  "advertiser_id": "uuid",
  "name": "Q1 2024 Campaign",
  "description": "Winter promotional campaign",
  "start_date": "2024-01-01",
  "end_date": "2024-03-31",
  "budget": 10000.00,
  "currency": "USD",
  "status": "ACTIVE"
}
```

Response: 201 Created
```json
{
  "campaign_id": "uuid",
  "status": "created"
}
```

#### Update Campaign
```
PUT /api/v1/admin/campaigns/{campaign_id}
{
  "budget": 15000.00,
  "status": "PAUSED"
}
```

#### Delete Campaign
```
DELETE /api/v1/admin/campaigns/{campaign_id}
```

---

### 3.2 Ad Management

#### Create Ad
```
POST /api/v1/admin/ads
{
  "campaign_id": "uuid",
  "name": "Homepage Banner",
  "ad_type": "BANNER_728x90",
  "creative_url": "https://cdn.adplatform.com/banners/ad-123.html",
  "landing_url": "https://advertiser.com/landing",
  "status": "ACTIVE",
  "start_date": "2024-01-01",
  "end_date": "2024-01-31"
}
```

#### Pause Ad
```
PATCH /api/v1/admin/ads/{ad_id}/pause
```

#### Resume Ad
```
PATCH /api/v1/admin/ads/{ad_id}/resume
```

---

### 3.3 Fraud Management

#### Report Suspicious Activity
```
POST /api/v1/admin/fraud-reports
{
  "ad_id": "uuid",
  "fraud_type": "CLICK_SPAM|LOCATION_ANOMALY|DEVICE_ANOMALY",
  "suspicious_ips": ["192.168.1.1", "192.168.1.2"],
  "description": "Unusual click pattern from same IP"
}
```

#### Block IP
```
POST /api/v1/admin/fraud-prevention/block-ip
{
  "ip_address": "192.168.1.1",
  "reason": "Excessive clicks from known botnet",
  "duration_hours": 24
}
```

#### Whitelist IP
```
POST /api/v1/admin/fraud-prevention/whitelist-ip
{
  "ip_address": "203.0.113.5",
  "reason": "Corporate office, legitimate traffic"
}
```

---

### 3.4 System Monitoring

#### Health Check
```
GET /api/v1/health

Response: 200 OK
{
  "status": "healthy",
  "components": {
    "kafka": {"status": "healthy", "lag_ms": 245},
    "warehouse": {"status": "healthy", "query_time_ms": 123},
    "dedup_db": {"status": "healthy", "latency_ms": 45},
    "cache": {"status": "healthy", "hit_rate": 0.87}
  },
  "timestamp": "2024-01-15T10:30:45Z"
}
```

#### System Metrics
```
GET /api/v1/admin/metrics

Response: 200 OK
{
  "clicks_per_second": 3847,
  "clicks_per_hour": 13849200,
  "warehouse_lag_seconds": 245,
  "dedup_db_latency_ms": 47,
  "duplicate_rate_percent": 0.23,
  "fraud_rate_percent": 0.14,
  "cache_hit_rate": 0.87,
  "kafka_consumer_lag": {
    "partition_0": 1200,
    "partition_1": 950,
    "partition_2": 1100
  }
}
```

#### Get System Logs
```
GET /api/v1/admin/logs?
  component=kafka&
  level=ERROR&
  start_time=2024-01-15T00:00:00Z&
  end_time=2024-01-15T23:59:59Z&
  limit=1000

Response: 200 OK
{
  "logs": [
    {
      "timestamp": "2024-01-15T10:30:45Z",
      "level": "ERROR",
      "component": "kafka",
      "message": "Consumer lag exceeded threshold",
      "details": {}
    }
  ]
}
```

---

## 4. Webhook APIs (For Events)

### Purpose:
Send real-time notifications when certain events occur.

### Webhook: Click Confirmed
```
Event fired after click confirmed (not duplicate)
POST https://advertiser-webhook.example.com/webhooks/click-confirmed

Payload:
{
  "event_type": "CLICK_CONFIRMED",
  "click_id": "uuid",
  "ad_id": "uuid",
  "timestamp": "ISO-8601",
  "ip_address": "string",
  "user_id": "string (optional)"
}

Signature: X-Webhook-Signature: sha256=<hmac-sha256>
```

### Webhook: Fraud Detected
```
Event fired when fraud detected
POST https://advertiser-webhook.example.com/webhooks/fraud-detected

Payload:
{
  "event_type": "FRAUD_DETECTED",
  "fraud_id": "uuid",
  "ad_id": "uuid",
  "fraud_type": "CLICK_SPAM|LOCATION_ANOMALY|...",
  "severity": "LOW|MEDIUM|HIGH",
  "affected_clicks": 1523,
  "timestamp": "ISO-8601"
}
```

### Webhook: Campaign Milestone
```
Event fired when campaign reaches milestone
POST https://advertiser-webhook.example.com/webhooks/campaign-milestone

Payload:
{
  "event_type": "CAMPAIGN_MILESTONE",
  "campaign_id": "uuid",
  "milestone": "100000_CLICKS",
  "value": 100000,
  "timestamp": "ISO-8601"
}
```

---

## 5. Authentication & Authorization

### Authentication Method: JWT (JSON Web Tokens)

```
POST /api/v1/auth/login
{
  "email": "advertiser@example.com",
  "password": "securepassword"
}

Response: 200 OK
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

### Token Usage:
```
Authorization: Bearer <access_token>
```

### Token Refresh:
```
POST /api/v1/auth/refresh
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
}

Response: 200 OK
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 3600
}
```

### Permission Model:

```
Roles:
1. ADVERTISER: Can track clicks and query own analytics
2. AGENCY: Can manage multiple advertiser accounts
3. ADMIN: Full system access
4. ANALYST: Read-only access to analytics

Permissions:
- TRACK_CLICK: POST /api/v1/clicks
- QUERY_ANALYTICS: POST /api/v1/analytics/query
- MANAGE_CAMPAIGNS: CRUD on campaigns
- MANAGE_ADS: CRUD on ads
- VIEW_FRAUD_REPORTS: GET fraud data
- MANAGE_FRAUD: Create fraud reports, block IPs
- VIEW_SYSTEM_METRICS: GET /api/v1/admin/metrics
```

---

## 6. Error Response Codes

| Code | Status | Meaning |
|------|--------|---------|
| 200 | OK | Request successful |
| 201 | Created | Resource created successfully |
| 204 | No Content | Request successful, no response body |
| 400 | Bad Request | Invalid request format or missing fields |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Authenticated but permission denied |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Duplicate resource (e.g., duplicate click) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |
| 502 | Bad Gateway | Upstream service unavailable |
| 503 | Service Unavailable | Service temporarily unavailable |
| 504 | Gateway Timeout | Request timeout |

---

## 7. Standard Error Response Format

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "The request is invalid",
    "details": [
      {
        "field": "ad_id",
        "message": "Invalid UUID format"
      }
    ],
    "timestamp": "2024-01-15T10:30:45Z",
    "request_id": "req-abc-123"
  }
}
```

---

## 8. Request/Response Headers

### Request Headers:
```
Host: api.adplatform.com
Content-Type: application/json
Authorization: Bearer <jwt_token>
X-Request-ID: unique-request-identifier
X-Correlation-ID: trace-correlation-id
User-Agent: AdPlatformSDK/1.0
Accept-Encoding: gzip, deflate
```

### Response Headers:
```
Content-Type: application/json
Content-Encoding: gzip
X-Request-ID: unique-request-identifier
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 987
X-RateLimit-Reset: 1705329045
Cache-Control: max-age=3600
ETag: "33a64df551ab"
```

---

## 9. API Versioning Strategy

```
Current version: /api/v1/
Future version: /api/v2/

Backward compatibility:
- v1 APIs will be supported for 12 months after v2 launch
- Deprecated features marked as DEPRECATED in 6 months before removal
- Clients encouraged to migrate 3 months before sunset

Deprecation notice:
```json
{
  "warning": {
    "code": "DEPRECATED_API",
    "message": "/api/v1/clicks deprecated as of 2024-12-01. Use /api/v2/events/click instead",
    "sunset_date": "2025-03-01"
  }
}
```
```

---

## 10. Client SDKs

### Supported Languages:
- JavaScript/TypeScript
- Python
- Java
- Go
- C#
- PHP
- Ruby

### Example: JavaScript SDK
```javascript
import { AdPlatformClient } from '@ad-platform/sdk';

const client = new AdPlatformClient({
  api_key: 'your-api-key',
  api_url: 'https://api.adplatform.com'
});

// Track a click
client.trackClick({
  ad_id: 'ad-123',
  campaign_id: 'camp-456',
  ip_address: '203.0.113.5',
  device_type: 'MOBILE',
  referrer_url: 'https://example.com',
  timestamp: new Date().toISOString()
}).then(response => {
  console.log('Click tracked:', response.click_id);
}).catch(error => {
  console.error('Failed to track click:', error);
});

// Query analytics
client.queryAnalytics({
  query_type: 'TIMESERIES',
  filters: {
    ad_ids: ['ad-123'],
    date_range: {
      start: '2024-01-01',
      end: '2024-01-31'
    }
  },
  granularity: 'DAY',
  metrics: ['clicks', 'ctr']
}).then(response => {
  console.log('Analytics data:', response.data);
});
```

---

## 11. Rate Limiting Details

### Tier-Based Limits:

#### Free Tier:
- 100 clicks/day
- 10 queries/hour
- 1 MB data transfer

#### Professional Tier:
- 1,000,000 clicks/month
- 1,000 queries/hour
- Unlimited data transfer
- Cost: $199/month

#### Enterprise Tier:
- Unlimited clicks
- Custom query limits
- Dedicated support
- SLA: 99.99%

### Rate Limiting Headers:
```
X-RateLimit-Limit: 1000 (requests per hour)
X-RateLimit-Remaining: 987 (remaining in current window)
X-RateLimit-Reset: 1705329045 (Unix timestamp when limit resets)
```

---

## 12. Batch API (For High-Volume Tracking)

### Purpose:
Submit multiple clicks in a single request for better efficiency.

```
POST /api/v1/clicks/batch

{
  "clicks": [
    {
      "click_id": "uuid1",
      "ad_id": "ad-123",
      "campaign_id": "camp-456",
      "ip_address": "203.0.113.5",
      "device_type": "MOBILE",
      "referrer_url": "https://example.com",
      "timestamp": "2024-01-15T10:30:45Z"
    },
    {
      "click_id": "uuid2",
      ...
    }
  ]
}

Response: 200 OK
{
  "status": "success",
  "total": 100,
  "accepted": 95,
  "rejected": 5,
  "results": [
    {
      "click_id": "uuid1",
      "status": "accepted"
    },
    {
      "click_id": "uuid2",
      "status": "duplicate"
    }
  ]
}
```

### Batch Size Limits:
- Max 1000 clicks per request
- Max 10 MB payload size
- Timeout: 30 seconds

---

## 13. API Documentation (OpenAPI/Swagger)

Location: `https://api.adplatform.com/swagger`

Includes:
- Interactive API explorer
- Request/response examples
- Schema definitions
- Authentication flow
- Rate limiting info
- Error codes reference
