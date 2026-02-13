# 🔲 BLACKBOX — Adaptive API Gateway with Self-Healing Rate Limiting

> A production-grade API Gateway that dynamically adjusts rate limits based on
> real-time traffic patterns, failures, and abuse signals — without manual intervention.

---

## Why This Exists

Every API gateway has rate limiting. Almost none adjust those limits automatically.

**The problem:** You set rate limits to 100 req/s. Then your database loses a replica.
Your gateway doesn't notice. It keeps forwarding 100 req/s to a backend that can only
handle 50. Static limits protect against what you planned for. Adaptive limits protect
against what actually happens.

BLACKBOX monitors downstream error rates every 10 seconds and automatically adjusts
rate limits through four modes: **NORMAL → CAUTIOUS → TIGHTENED → RECOVERING**.

---

## Why BLACKBOX? (vs Existing Solutions)

### Not Another API Gateway

BLACKBOX occupies a unique niche in the API Gateway landscape:

**vs Resilience4j:**
- Resilience4j is a library you integrate into your application code
- BLACKBOX is a standalone gateway (no code changes to your services needed)
- Resilience4j requires per-service configuration
- BLACKBOX provides centralized policy enforcement

**vs Istio:**
- Istio requires Kubernetes, service mesh knowledge, and complex setup (100s of CRDs)
- BLACKBOX runs with `docker compose up` (ready in 30 seconds)
- Istio is for enterprises with 100+ microservices
- BLACKBOX is for SMBs with 5-50 services

**vs Kong/Tyk:**
- Commercial gateways with paid tiers for advanced features
- BLACKBOX is 100% open source with full observability included
- Kong/Tyk focus on API management (developer portals, billing)
- BLACKBOX focuses on resilience (adaptive limits, circuit breaking)

**vs AWS API Gateway:**
- Vendor lock-in to AWS infrastructure
- BLACKBOX runs anywhere (cloud, on-prem, local)
- AWS charges per million requests
- BLACKBOX is free (pay only for compute)

### Our Unique Value

1. **Educational Excellence**
   - 64 pages of documentation explaining every decision
   - From high-school intern → staff engineer level
   - Real-world analogies and production patterns
   - Perfect for learning distributed systems

2. **Adaptive Protection** (Our Innovation)
   - **Novel combination:** Circuit breaker + adaptive rate limiting
   - Other tools have both features separately, but don't link them
   - BLACKBOX adjusts limits BEFORE circuit opens (better UX)
   - Self-healing without human intervention

3. **Deployment Simplicity**
   - From `git clone` to production in 5 minutes
   - No Kubernetes, no service mesh, no complex configuration
   - Built-in observability (Prometheus + Grafana)
   - Works on $5/month VPS

### Perfect For

✅ **Learning:** CS students, bootcamp grads, interview prep  
✅ **SMBs:** Startups with 5-50 microservices  
✅ **Personal Projects:** Side projects that need resilience  
✅ **On-Prem:** Teams without cloud/k8s infrastructure  

❌ **Not For:** 1,000+ microservice enterprises (use Istio)

### Feature Comparison

| Feature | BLACKBOX | Resilience4j | Istio | Kong | AWS API GW |
|---------|----------|--------------|-------|------|------------|
| **Circuit Breaker** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Rate Limiting** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Adaptive Limits** | ✅ **Unique** | ❌ | ❌ | ❌ | ❌ |
| **Setup Time** | 30 seconds | 1 hour | 1-2 days | 2-4 hours | 10 minutes |
| **Requires K8s** | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Documentation Depth** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| **Built-in Observability** | ✅ | ❌ | ✅ | Paid | ✅ |
| **Cost** | Free | Free | Free | Free/Paid | Pay-per-req |
| **Best For** | Learning, SMBs | Java apps | Enterprise | API Mgmt | AWS users |

---

## Architecture

```
                          ┌─────────────────────────────────┐
     Client Request ─────►  JWT Auth Filter                 │
                          │  Validates token at edge         │
                          │  Extracts ClientIdentity         │
                          └──────────┬──────────────────────┘
                                     │
                          ┌──────────▼──────────────────────┐
                          │  Rate Limit Filter               │
                          │  Redis token bucket              │
                          │  Per-client, per-route limits    │
                          │  Adaptive multiplier applied     │
                          └──────────┬──────────────────────┘
                                     │
                          ┌──────────▼──────────────────────┐
                          │  Request Router                  │
                          │  Circuit breaker check           │
                          │  WebClient forward (5s timeout)  │
                          │  Records success/failure for     │
                          │  adaptive controller             │
                          └──────────┬──────────────────────┘
                                     │
                  ┌──────────────────┼──────────────────┐
                  ▼                  ▼                  ▼
            Backend A          Backend B          Backend N
```

### Adaptive Rate Limiting Loop

```
  ┌──────────────────┐
  │ Every 10 seconds  │
  │ Check error rate  │
  └────────┬─────────┘
           │
    ┌──────▼──────┐     > 50% errors    ┌────────────┐
    │ Error Rate  ├────────────────────► │ TIGHTENED  │ 0.5x multiplier
    │ Evaluation  │                      └────────────┘
    │             │     > 20% errors    ┌────────────┐
    │             ├────────────────────► │ CAUTIOUS   │ 0.8x multiplier
    │             │                      └────────────┘
    │             │     < 5% (2min)     ┌────────────┐
    │             ├────────────────────► │ RECOVERING │ gradual increase
    │             │                      └────────────┘
    │             │     stable          ┌────────────┐
    │             ├────────────────────► │ NORMAL     │ 1.0x multiplier
    └─────────────┘                      └────────────┘
```

---

## Quick Start

### Prerequisites
- Java 17
- Maven 3.8+
- Docker & Docker Compose

### Run the Stack

```bash
# Clone
git clone <repo-url> && cd blackbox

# Start everything (gateway + Redis + PostgreSQL + Prometheus + Grafana + mock backend)
docker compose up --build

# Get a test JWT token
curl -X POST http://localhost:8080/test/token \
  -H "Content-Type: application/json" \
  -d '{"clientId":"my-client","tier":"STANDARD","name":"Test Client"}'

# Use the token to make requests
TOKEN="<paste token from above>"
curl http://localhost:8080/api/test \
  -H "Authorization: Bearer $TOKEN"
```

### Access Points

| Service | URL |
|---------|-----|
| Gateway | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Mock Backend | http://localhost:8081 |

---

## Rate Limit Headers

Every response includes rate limiting information:

| Header | Description | Example |
|--------|-------------|---------|
| `X-RateLimit-Limit` | Maximum requests allowed | `100` |
| `X-RateLimit-Remaining` | Tokens left in bucket | `73` |
| `X-RateLimit-Source` | Active rate limiter | `redis` or `local-fallback` |
| `Retry-After` | Seconds until refill (on 429) | `2` |

---

## Rate Limit Tiers

| Tier | Requests/sec | Burst Size |
|------|-------------|------------|
| STANDARD | 10 | 20 |
| PREMIUM | 50 | 100 |
| INTERNAL | 200 | 500 |

Configured in `application.yml`. JWT claims determine the tier.

---

## Failure Behavior

| Component Down | Gateway Behavior | Client Impact |
|---------------|-----------------|---------------|
| Redis | Local rate limiting at 50% | More conservative throttling |
| PostgreSQL | Audit writes fail silently | None visible |
| Backend (partial) | Circuit breaker for affected route | 503 for that route only |
| Backend (full) | All circuit breakers open | 503 across the board |

**Design principle:** Degrade, don't crash.

---

## Load Testing (k6)

```bash
# Generate tokens
export TOKEN=$(curl -s -X POST http://localhost:8080/test/token \
  -H "Content-Type: application/json" \
  -d '{"clientId":"test-1","tier":"STANDARD","name":"Test"}' | jq -r '.token')

# Baseline: 100 RPS steady-state
JWT_TOKEN=$TOKEN k6 run k6/baseline.js

# Spike: 50 → 500 RPS
JWT_TOKEN=$TOKEN k6 run k6/spike.js

# Abuse: Single client flooding
JWT_TOKEN=$TOKEN k6 run k6/abuse.js

# Chaos: Run this, then `docker compose stop redis` mid-test
JWT_TOKEN=$TOKEN k6 run k6/chaos.js
```

---

## Observability

### Prometheus Metrics

| Metric | Type | Labels | Purpose |
|--------|------|--------|---------|
| `gateway_request_total` | Counter | route, status | Total request count |
| `gateway_request_duration_seconds` | Histogram | route | Latency percentiles |
| `gateway_rate_limit_throttled_total` | Counter | route, tier | 429 responses |
| `gateway_circuit_breaker_state` | Gauge | route | 0=CLOSED, 1=OPEN, 2=HALF_OPEN |
| `gateway_downstream_error_total` | Counter | route | 5xx from backends |
| `gateway_adaptive_adjustment_total` | Counter | direction | tighten/relax events |
| `gateway_fallback_total` | Counter | component | Redis fallback activations |

### Grafana Dashboard

Auto-provisioned at startup. Access at http://localhost:3000 → "BLACKBOX Gateway Dashboard".

7 panels: Request Rate, Latency Percentiles, Throttle Rate, Circuit Breaker State,
Downstream Errors, Adaptive Adjustments, Fallback Rate.

---

## Project Structure

```
blackbox/
├── src/main/java/com/blackbox/gateway/
│   ├── BlackboxApplication.java          # Entry point
│   ├── config/
│   │   ├── GatewayProperties.java        # Type-safe config binding
│   │   ├── RedisConfig.java              # Redis + Lua script setup
│   │   └── WebConfig.java                # Filter registration
│   ├── filter/
│   │   ├── JwtAuthFilter.java            # JWT validation at edge
│   │   └── RateLimitFilter.java          # Rate limit enforcement
│   ├── ratelimit/
│   │   ├── RateLimiter.java              # Interface
│   │   ├── TokenBucketRateLimiter.java   # Redis + local fallback
│   │   └── AdaptiveRateLimitController.java # Self-healing brain
│   ├── circuitbreaker/
│   │   ├── CircuitBreaker.java           # Per-route state machine
│   │   └── CircuitState.java             # CLOSED/OPEN/HALF_OPEN
│   ├── routing/
│   │   ├── RequestRouter.java            # WebClient forwarding
│   │   └── GatewayController.java        # Catch-all /api/**
│   ├── metrics/
│   │   └── GatewayMetrics.java           # Micrometer integration
│   ├── model/
│   │   ├── ClientIdentity.java           # JWT claims model
│   │   ├── RateLimitDecision.java        # Allow/deny result
│   │   ├── AuditLog.java                 # JPA entity
│   │   ├── AuditLogRepository.java       # Spring Data JPA
│   │   └── GatewayErrorResponse.java     # Error model
│   ├── exception/
│   │   └── GatewayErrorHandler.java      # Global error handler
│   └── util/
│       ├── JwtTokenGenerator.java        # Test token creation
│       └── TestTokenController.java      # /test/token endpoint
├── src/main/resources/
│   ├── application.yml                   # All configuration
│   └── scripts/token_bucket.lua          # Atomic Redis operation
├── k6/
│   ├── baseline.js                       # 100 RPS steady-state
│   ├── spike.js                          # 50 → 500 RPS ramp
│   ├── abuse.js                          # Per-client isolation
│   └── chaos.js                          # Redis kill mid-traffic
├── mock-backend/                         # Configurable fake backend
├── monitoring/
│   ├── prometheus.yml                    # Scrape config
│   └── grafana/                          # Dashboard + datasource
├── docs/
│   ├── blog-1-static-rate-limiting.md    # Why static fails
│   └── blog-2-breaking-my-gateway.md     # Load test walkthrough
├── Dockerfile                            # Gateway image
├── docker-compose.yml                    # Full stack
└── pom.xml                               # Dependencies
```

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| JWT at gateway edge | Authenticate before any processing |
| Heuristics over ML | Explainable, debuggable, tunable |
| Redis for rate limiting | Shared state across instances |
| Local fallback at 50% | Fail-safe > fail-open |
| Per-route circuit breakers | Isolate route failures |
| 2-minute recovery cooldown | Prevent oscillation during flapping |

---

## Tech Stack

- **Runtime:** Java 17
- **Framework:** Spring Boot 3.2
- **Rate Limiting:** Redis + Lua scripts
- **Auth:** JWT (JJWT library)
- **Metrics:** Micrometer → Prometheus → Grafana
- **Routing:** WebClient (non-blocking)
- **Testing:** k6 (load tests)
- **Infrastructure:** Docker Compose

---

## Blog Posts

1. [Why Static Rate Limiting Fails in Real Systems](docs/blog-1-static-rate-limiting.md)
2. [Breaking My Own API Gateway](docs/blog-2-breaking-my-gateway.md)

---

## License

MIT
