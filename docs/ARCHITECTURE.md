# BLACKBOX — Architecture & Threat Model

## Adaptive API Gateway with Self-Healing Rate Limiting

> A production-grade API Gateway that dynamically adjusts rate limits based on real-time
> traffic patterns, failures, and abuse signals — without manual intervention.

---

## 1. The Real Problem

### Why Static Rate Limiting Fails

In every production system I've seen, rate limiting is configured once and forgotten:

```
# Typical static config — set and forget
rate_limit:
  requests_per_second: 100
  burst: 150
```

This creates two failure modes:
1. **Too generous** — During an attack or cascade, 100 RPS per client still lets a botnet through
2. **Too strict** — During a legitimate flash sale, real users get 429'd while the system has spare capacity

**The core tension:** Static limits optimize for ONE traffic pattern. Real traffic has infinite patterns.

### What Actually Happens During Incidents

1. PagerDuty fires at 3 AM
2. On-call engineer wakes up, opens dashboard
3. Identifies abusive traffic pattern
4. Manually adjusts rate limits via config push
5. Waits for deployment to propagate
6. **Total response time: 15–45 minutes**

In those 45 minutes, downstream services are either overloaded or legitimate users are locked out.

**BLACKBOX answers one question:**
> "How can the gateway protect itself and its backends *automatically*, within seconds, not minutes?"

---

## 2. Failure Scenarios (Threat Model)

I assumed failure, not success. These are the 10 scenarios that shaped every design decision:

| # | Scenario | Impact | Probability |
|---|----------|--------|-------------|
| 1 | **Redis goes down** | Rate limit state lost, all clients appear "new" | Medium |
| 2 | **Sudden 10× traffic spike** | Backend overwhelmed, cascading 5xx | High |
| 3 | **Slow downstream** | Gateway threads blocked, connection pool exhaustion | High |
| 4 | **JWT replay attack** | Unauthorized access with stolen token | Medium |
| 5 | **Single abusive client** | One API key consuming 80% capacity | High |
| 6 | **Partial backend failure** | Some instances healthy, some dead | High |
| 7 | **Bad config pushed to Redis** | Corrupted rate limits (0 RPS or infinite) | Low |
| 8 | **Cascading retry storm** | Gateway retries hammer failing backend | High |
| 9 | **Clock skew across instances** | Token bucket windows misaligned | Low |
| 10 | **Redis OOM under sustained attack** | Eviction of rate limit keys, limits reset | Medium |

### Failure Severity Classification

```
CRITICAL (system-wide impact):
  → #2 Traffic spike
  → #3 Slow downstream
  → #8 Retry storm

HIGH (degraded service):
  → #1 Redis down
  → #5 Abusive client
  → #6 Partial backend failure

MODERATE (security/correctness):
  → #4 JWT replay
  → #7 Bad config
  → #10 Redis OOM

LOW (edge case):
  → #9 Clock skew
```

---

## 3. High-Level Architecture

```
                    ┌─────────────────────────────────────────────┐
                    │              BLACKBOX GATEWAY                │
                    │                                             │
  Client ──────────►│  [JWT Filter] → [Rate Limiter] → [Router]  │────► Backend
  (with JWT)       │       │              │               │       │     Services
                    │       │              │               │       │
                    │       ▼              ▼               ▼       │
                    │   Reject 401    Reject 429     Circuit Break │
                    │                                  503         │
                    └──────┬──────────────┬───────────────┬────────┘
                           │              │               │
                           ▼              ▼               ▼
                      ┌─────────┐   ┌──────────┐   ┌────────────┐
                      │PostgreSQL│   │  Redis   │   │ Prometheus │
                      │(configs, │   │(buckets, │   │ (metrics)  │
                      │ audit)   │   │ locks,   │   │            │
                      │          │   │ circuit) │   │            │
                      └─────────┘   └──────────┘   └─────┬──────┘
                                                         │
                                                         ▼
                                                   ┌──────────┐
                                                   │ Grafana  │
                                                   │(dashboards│
                                                   │ alerts)  │
                                                   └──────────┘
```

### Component Responsibilities

| Component | What It Does | What It Does NOT Do |
|-----------|-------------|-------------------|
| **Gateway (Spring Boot)** | Routes requests, validates JWTs, enforces rate limits, breaks circuits | Does NOT transform request/response bodies |
| **Redis** | Stores token bucket state, distributed locks, circuit breaker counters | Does NOT persist data (ephemeral by design) |
| **PostgreSQL** | Stores client configs, rate limit tiers, audit logs | Does NOT serve hot-path decisions |
| **Prometheus** | Scrapes gateway metrics every 15s | Does NOT make control decisions |
| **Grafana** | Visualizes metrics, fires alert rules | Does NOT feed back into the gateway |
| **Mock Backend** | Simulates downstream service (configurable latency/errors) | Is NOT a real service |

### Request Flow (Happy Path)

```
1. Client sends request with JWT in Authorization header
2. JwtAuthFilter validates token signature + expiry
   → Invalid? Return 401 Unauthorized
3. RateLimitFilter checks Redis token bucket for client ID
   → Exhausted? Return 429 Too Many Requests + Retry-After header
4. RequestRouter forwards to backend via WebClient
   → Circuit open? Return 503 Service Unavailable
5. Response returned to client
6. Metrics recorded: latency, status, client tier
```

### Request Flow (Redis Down)

```
1–2. Same as happy path
3. RateLimitFilter: Redis unreachable
   → Fall back to local in-memory rate limiter
   → Use CONSERVATIVE limits (50% of normal)
   → Log degradation event
   → Increment gateway_fallback_total metric
4–6. Same as happy path
```

---

## 4. Core Design Decisions

### Decision 1: Redis-Backed Token Buckets (Not In-Memory)

**Chose:** Centralized Redis token buckets with Lua scripts for atomicity

**Alternatives considered:**
- **In-memory (Guava RateLimiter):** Simple, zero latency. But each gateway instance has independent state. Client can send N × instances requests before being throttled.
- **Database-backed:** Durable, but 5–10ms per check. Unacceptable on the hot path.
- **Distributed in-memory (Hazelcast):** Consistent, but adds operational complexity and a new failure mode.

**Why Redis wins:**
- Atomic Lua scripts = no race conditions
- ~1ms round trip (acceptable overhead)
- Shared state across all gateway instances
- Well-understood failure modes
- Easy to monitor and debug

**Trade-off accepted:** Redis is a single point of failure. Mitigated by local fallback (Decision 3).

---

### Decision 2: Adaptive Rate Adjustment (Not Static Thresholds)

**Chose:** Background controller that monitors error rates and adjusts bucket parameters

**How it works:**
```
Every 10 seconds:
  error_rate = downstream_5xx_count / total_requests (last 60s window)

  IF error_rate > 50%:
    refill_rate = refill_rate * 0.5  (halve — aggressive protection)
    mode = TIGHTENED

  ELSE IF error_rate > 20%:
    refill_rate = refill_rate * 0.8  (reduce gradually)
    mode = CAUTIOUS

  ELSE IF error_rate < 5% for 120 seconds:
    refill_rate = min(refill_rate * 1.2, default_rate)  (gradually restore)
    mode = RECOVERING

  ELSE:
    mode = NORMAL
```

**Why not ML/AI-based prediction?**
- Adds complexity without proportional value
- Hard to debug ("why did the model throttle this client?")
- Simple heuristics are explainable and tunable
- Non-goal: perfection. Goal: fast, safe, understandable.

**Trade-off accepted:** May throttle legitimate users during backend incidents. Protecting the system > perfect fairness.

---

### Decision 3: Graceful Degradation (Not Fail-Open or Fail-Closed)

**Chose:** Layered fallback with conservative defaults

**The spectrum:**
```
Fail-Open:  If Redis is down, allow ALL traffic → Dangerous
Fail-Closed: If Redis is down, block ALL traffic → Availability disaster
Fail-Safe:   If Redis is down, allow traffic with STRICTER local limits → Our choice
```

**Why fail-safe:**
- Maintains protection (local limiter still works)
- Maintains availability (clients aren't locked out)
- Accepts imperfect rate counting during degradation
- Alerts fire immediately so humans can investigate

---

### Decision 4: Circuit Breaker Per Route (Not Global)

**Chose:** Independent circuit breaker per downstream route

**Why not global?**
- If `/api/payments` backend is down but `/api/users` is healthy, we shouldn't block user requests
- Per-route isolation limits blast radius
- Each route has its own failure threshold and cooldown

**State machine:**
```
CLOSED ──(N failures)──► OPEN ──(cooldown)──► HALF_OPEN
   ▲                                              │
   └──────────(M successes)────────────────────────┘
                                                   │
                          (failure in half-open)    │
                     OPEN ◄────────────────────────┘
```

---

## 5. Goals, Non-Goals, and Trade-Offs

### Goals
- ✅ Protect downstream services from traffic spikes and abuse automatically
- ✅ Adapt rate limits based on real-time system health
- ✅ Survive infrastructure failures (Redis, DB, backend) without crashing
- ✅ Provide clear observability into every decision the gateway makes
- ✅ Return meaningful error responses (429 with Retry-After, 503 with reason)

### Non-Goals
- ❌ Perfect rate limit accuracy across distributed instances (consistency vs availability trade-off)
- ❌ Zero added latency (we accept ~1-2ms for Redis round-trip)
- ❌ Request/response transformation (not an API management platform)
- ❌ GUI admin panel (config via YAML/DB, not UI)
- ❌ Multi-region deployment (single-region, free-tier scope)
- ❌ ML-based traffic prediction (explainability > sophistication)

### Explicit Trade-Offs

| We Chose | Over | Because |
|----------|------|---------|
| Redis (external state) | In-memory (local state) | Multi-instance consistency |
| Simple heuristics | ML prediction | Debuggability and explainability |
| Conservative fallback | Fail-open | Safety during degradation |
| Per-route circuit breakers | Global breaker | Blast radius isolation |
| 1ms latency overhead | Zero overhead | Correctness of rate limiting |
| Eventual consistency | Strong consistency | Availability under partition |

---

## 6. Technology Choices

| Technology | Version | Why |
|-----------|---------|-----|
| Java | 17 | Standard thread pool for gateway I/O |
| Spring Boot | 3.2+ | WebFlux for reactive request routing |
| Redis | 7.x | Lua scripting, pub/sub for config propagation |
| PostgreSQL | 16 | JSONB for flexible config, strong audit support |
| Prometheus | Latest | Pull-based metrics, PromQL for alerting |
| Grafana | Latest | Dashboard visualization, alert routing |
| Docker Compose | 3.8+ | Local development orchestration |
| k6 | Latest | Scriptable load testing |
| JUnit 5 + Testcontainers | Latest | Integration tests with real Redis/PostgreSQL |

---

## 7. Security Considerations

### Authentication
- JWT (HMAC-SHA256) validated at gateway edge
- No request reaches backend without valid token
- Token expiry enforced (reject expired tokens, no grace period)

### Rate Limit Bypass Prevention
- Client identity extracted from JWT claims (not IP-based, IPs can be shared/spoofed)
- Rate limit keys include client ID + route
- Burst protection: bucket size limits instantaneous spikes

### Secrets Management
- JWT signing key via environment variable (not hardcoded)
- Redis password via environment variable
- PostgreSQL credentials via environment variable
- In production: would use Vault or cloud KMS

---

## 8. What I Would Change at 10× Scale

| Current Design | At 10× Scale |
|---------------|-------------|
| Single Redis instance | Redis Cluster (sharded by client ID) |
| Sync Redis calls on hot path | Redis pipeline + local cache hybrid |
| Single-region | Multi-region with regional rate limits |
| Prometheus pull | Push-based metrics (OTLP) for lower overhead |
| Docker Compose | Kubernetes with HPA |
| Background adaptive controller | Dedicated control-plane service |
| In-process circuit breaker | Service mesh (Istio) circuit breaking |
| PostgreSQL for audit | Kafka → S3 for high-volume audit streaming |

These were intentionally avoided to keep the system understandable and free-tier compatible.

---

## 9. Blog Notes (for Day 7)

### Why Static Rate Limiting Fails
- The 3 AM PagerDuty story
- Static limits optimize for one traffic pattern
- The fairness vs protection tension
- Why "just set a higher limit" doesn't work

### What Was Intentionally Ignored
- ML/AI prediction (explainability matters more)
- Perfect accuracy (availability > consistency)
- GUI admin panel (YAGNI for this scope)
- Multi-region (free-tier scope, but designed for it)
