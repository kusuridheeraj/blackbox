# BLACKBOX Architecture - Design Decisions & Tradeoffs

**Author:** Engineering Team  
**Last Updated:** February 2026  
**Audience:** Engineers, Architects, Technical Decision Makers

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Technology Stack Decisions](#technology-stack-decisions)
3. [Architectural Patterns](#architectural-patterns)
4. [Design Tradeoffs](#design-tradeoffs)
5. [Scalability Considerations](#scalability-considerations)
6. [Security Architecture](#security-architecture)

---

## System Architecture

### High-Level Components

```
┌──────────────────────────────────────────────────────────────┐
│                        CLIENTS                               │
│         (Web Apps, Mobile Apps, Third-Party APIs)            │
└────────────────┬────────────────────────────────┬────────────┘
                 │                                 │
         ┌───────▼────────┐              ┌────────▼───────┐
         │  Load Balancer │              │ Load Balancer  │
         │   (Future)     │              │   (Future)     │
         └───────┬────────┘              └────────┬───────┘
                 │                                 │
         ┌───────▼──────────────────────────────┬─▼───────┐
         │                                      │         │
    ┌────▼─────┐  ┌────────────┐  ┌────────────▼───┐     │
    │ Gateway  │  │  Gateway   │  │   Gateway      │     │
    │Instance 1│  │ Instance 2 │  │  Instance N    │     │
    └────┬─────┘  └─────┬──────┘  └─────┬──────────┘     │
         │              │               │                 │
         └──────────────┼───────────────┘                 │
                        │                                 │
         ┌──────────────▼────────────────┐                │
         │   Shared Infrastructure       │                │
         │  ┌────────┐    ┌──────────┐   │                │
         │  │ Redis  │    │PostgreSQL│   │                │
         │  │(State) │    │  (Audit) │   │                │
         │  └────────┘    └──────────┘   │                │
         └───────────────────────────────┘                │
                        │                                 │
         ┌──────────────▼────────────────┐                │
         │   Monitoring & Observability  │                │
         │  ┌───────────┐  ┌──────────┐  │                │
         │  │Prometheus │  │ Grafana  │  │                │
         │  │(Metrics)  │  │ (Dashboar│  │                │
         │  └───────────┘  └──────────┘  │                │
         └───────────────────────────────┘                │
                        │                                 │
         ┌──────────────▼─────────────────────────────────▼┐
         │              Backend Services                    │
         │  ┌─────────┐  ┌─────────┐  ┌───────────────┐    │
         │  │Payments │  │  Users  │  │  Inventory    │... │
         │  │ Service │  │ Service │  │   Service     │    │
         │  └─────────┘  └─────────┘  └───────────────┘    │
         └──────────────────────────────────────────────────┘
```

### Why This Architecture?

**1. Gateway as Reverse Proxy**
- **Pattern:** Single entry point for all backend services
- **Benefit:** Centralized security, rate limiting, monitoring
- **Alternative:** Direct client-to-service calls
  - ❌ Problem: Each service needs auth, rate limiting, monitoring
  - ❌ Result: Code duplication, inconsistent policies

**2. Shared Redis for State**
- **Pattern:** Distributed cache for rate limit counters
- **Benefit:** Multiple gateway instances share same view
- **Alternative:** In-memory state
  - ❌ Problem: Instance 1 allows 100 req/s, Instance 2 allows 100 req/s
  - ❌ Result: User gets 200 req/s (bypassed limit!)

**3. PostgreSQL for Audit Logs**
- **Pattern:** Append-only log of all significant events
- **Benefit:** Post-incident analysis, compliance, debugging
- **Alternative:** No audit logs
  - ❌ Problem: "Who changed rate limit at 3 AM?"
  - ❌ Result: Can't answer

**4. Prometheus + Grafana**
- **Pattern:** Time-series metrics + visualization
- **Benefit:** Real-time monitoring, alerting, SLA tracking
- **Alternative:** Log-based monitoring
  - ❌ Problem: Logs don't show trends (e.g., "latency increased 50%")
  - ❌ Result: Reactive instead of proactive

---

## Technology Stack Decisions

### Backend: Spring Boot (Java 21)

**Why Java?**

| Criterion | Java | Go | Node.js | Python |
|-----------|------|----|---------| -------|
| **Performance** | ⭐⭐⭐⭐ (JIT optimized) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **Ecosystem** | ⭐⭐⭐⭐⭐ (Massive) | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Type Safety** | ⭐⭐⭐⭐⭐ (Compile-time) | ⭐⭐⭐⭐⭐ | ⭐⭐ (TypeScript) | ⭐ (MyPy) |
| **Enterprise Adoption** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| **Learning Curve** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

**Decision: Java**
- ✅ Enterprise standard (80% of Fortune 500 use Java)
- ✅ Mature ecosystem (Spring Security, Spring Data, Micrometer)
- ✅ Strong typing prevents bugs at compile time
- ✅ Hiring pool (millions of Java developers)
- ❌ Tradeoff: Slower startup than Go (3 seconds vs 0.1 seconds)
- ❌ Tradeoff: Higher memory usage (500MB vs 50MB for Go)

**When to choose Go instead:**
- Building lightweight sidecar proxies
- Need instant cold start (serverless)
- Resource-constrained environments (IoT devices)

**When to choose Node.js instead:**
- Primarily I/O-bound workload (not CPU)
- Frontend team wants same language
- Need real-time websockets (chat, streaming)

---

**Why Spring Boot (not raw Java)?**

**Spring Boot provides:**
- **Embedded Server:** No need to deploy WAR to Tomcat
- **Auto-configuration:** Sensible defaults, minimal XML
- **Dependency Injection:** Testable, modular code
- **Actuator:** Health checks, metrics endpoints
- **Security:** JWT, OAuth2 out-of-box

**Alternative: Micronaut, Quarkus**
- ✅ Faster startup (GraalVM native)
- ✅ Lower memory
- ❌ Smaller ecosystem
- ❌ Less mature

**Decision:** Spring Boot for maturity, Micronaut/Quarkus for future optimization.

---

### Data Store: Redis

**Why Redis for Rate Limiting?**

**Requirements:**
1. Atomic operations (increment counter)
2. Sub-millisecond latency
3. TTL (time-to-live) for auto-expiry
4. Distributed (multi-instance access)

**Options:**

| Option | Latency | Atomic | TTL | Cost |
|--------|---------|--------|-----|------|
| **In-Memory (HashMap)** | 0.001ms | ❌ | ✅ | Free |
| **Redis** | 0.5ms | ✅ | ✅ | $ |
| **PostgreSQL** | 5ms | ✅ | ❌ | $$ |
| **DynamoDB** | 10ms | ✅ | ✅ | $$$ |

**Decision: Redis**
- ✅ Fast enough (0.5ms << 50ms request latency)
- ✅ Atomic Lua scripts (no race conditions)
- ✅ TTL for auto-cleanup
- ✅ Open-source (no vendor lock-in)
- ❌ Tradeoff: Single point of failure (mitigated with Redis Sentinel/Cluster)

**When to use DynamoDB instead:**
- Already on AWS
- Need multi-region replication
- Cost is not a concern

---

### Data Store: PostgreSQL

**Why PostgreSQL for Audit Logs?**

**Requirements:**
1. ACID transactions (don't lose audit records)
2. Complex queries (filter by date, event type, client)
3. Long-term retention (years)

**Alternatives:**

| Option | ACID | Query | Retention | Cost |
|--------|------|-------|-----------|------|
| **PostgreSQL** | ✅ | ✅ SQL | ✅ | $ |
| **MongoDB** | ❌ | ⚠️ NoSQL | ✅ | $ |
| **Elasticsearch** | ❌ | ✅ Full-text | ✅ | $$$ |
| **S3 + Athena** | ❌ | ⚠️ Batch | ✅ | $ |

**Decision: PostgreSQL**
- ✅ ACID (audit logs are critical)
- ✅ SQL for ad-hoc queries
- ✅ JSONB for flexible schema
- ❌ Tradeoff: Slower than Elasticsearch for full-text search

**When to use Elasticsearch instead:**
- Need full-text search (e.g., "find all errors containing 'timeout'")
- Log volume > 1TB/day
- Real-time log aggregation (ELK stack)

---

### Monitoring: Prometheus + Grafana

**Why This Stack?**

**Prometheus:**
- Industry standard for time-series metrics
- Pull-based model (gateway exposes `/metrics` endpoint)
- PromQL query language
- Built-in alerting (Alertmanager)

**Grafana:**
- Visualization layer for Prometheus
- Pre-built dashboards
- Multi-datasource support

**Alternatives:**

| Stack | Pros | Cons |
|-------|------|------|
| **Datadog** | Managed, APM, Logs | $$$$ |
| **New Relic** | Easy setup, AI alerts | $$$ |
| **CloudWatch** | Native AWS | AWS lock-in |
| **Prometheus + Grafana** | Free, flexible | Self-hosted |

**Decision: Prometheus + Grafana**
- ✅ Open-source (no per-host billing)
- ✅ Full control (custom queries)
- ❌ Tradeoff: Need to manage infrastructure

**When to use Datadog instead:**
- Budget > $100k/year
- Want managed service
- Need APM (application performance monitoring)

---

## Architectural Patterns

### 1. Filter Chain Pattern

**Problem:** Request needs multi-step processing.

**Solution:** Chain of Responsibility

```java
Client Request
    ↓
[JwtAuthFilter]
    ↓ (if valid)
[RateLimitFilter]
    ↓ (if not throttled)
[RequestRouter]
    ↓
Backend
```

**Benefits:**
- ✅ Each filter has single responsibility
- ✅ Easy to add new filters (e.g., IP allowlist)
- ✅ Testable in isolation

**Code:**
```java
@Component
@Order(1)
public class JwtAuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ...) {
        // Validate JWT
        if (invalid) {
            response.sendError(401);
            return;  // Stop chain
        }
        chain.doFilter(request, response);  // Continue
    }
}

@Component
@Order(2)
public class RateLimitFilter implements Filter {
    // Similar pattern
}
```

---

### 2. Token Bucket Algorithm

**Problem:** Rate limiting with burst tolerance.

**Why Token Bucket?**

**Comparison:**

| Algorithm | Allows Burst | Smooth Rate | Implementation |
|-----------|--------------|-------------|----------------|
| **Fixed Window** | ❌ | ❌ | Easy |
| **Sliding Window** | ❌ | ✅ | Medium |
| **Leaky Bucket** | ❌ | ✅ | Medium |
| **Token Bucket** | ✅ | ✅ | Medium |

**Token Bucket Mechanics:**
```
Bucket size: 750 tokens (burst)
Refill rate: 500 tokens/second

Second 0: 750 tokens (full)
User makes 100 requests → 650 tokens left
Second 1: 650 + 500 (refill) = 1150 → capped at 750
User makes 700 requests → 50 tokens left
Second 2: 50 + 500 = 550 tokens
```

**Real-world analogy:** Water tank
- Tank capacity: 750 liters
- Faucet adds: 500 liters/second
- User consumes: Variable
- Tank never overflows (capped at 750)

**Implementation (Lua in Redis):**
```lua
local tokens = tonumber(redis.call('get', KEYS[1]) or capacity)
local now = tonumber(ARGV[1])
local lastRefill = tonumber(redis.call('get', KEYS[2]) or now)

-- Calculate refill
local elapsed = now - lastRefill
local addTokens = elapsed * refillRate
tokens = math.min(capacity, tokens + addTokens)

-- Try to consume
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('set', KEYS[1], tokens)
    redis.call('set', KEYS[2], now)
    return 1  -- Allow
else
    return 0  -- Reject
end
```

**Why Lua?**
- Executes atomically on Redis
- No race condition between GET and SET

---

### 3. Circuit Breaker Pattern

**Problem:** Cascading failures

**Without Circuit Breaker:**
```
Payment Service down
→ Gateway keeps trying
→ Each request waits 5 seconds (timeout)
→ Gateway thread pool exhausted
→ Gateway can't handle other requests
→ Entire system appears down!
```

**With Circuit Breaker:**
```
Payment Service fails 5 times
→ Circuit OPENS (fail fast)
→ Return 503 immediately (no 5s wait)
→ Gateway thread pool stays healthy
→ Other routes (users, products) still work!
```

**State Machine:**

```
CLOSED (normal)
  │ 5 consecutive failures
  ▼
OPEN (blocking)
  │ 30 seconds cooldown
  ▼
HALF_OPEN (testing)
  ├─ Success → CLOSED
  └─ Failure → OPEN
```

**Metrics:**
- **Failure threshold:** 5 failures (why not 1? Transient errors exist)
- **Cooldown:** 30 seconds (why not 5 min? Too long for users to wait)
- **Half-open probes:** 1 request (why not 10? Don't overwhelm recovering backend)

**Real-world analogy:** Electrical circuit breaker
- **CLOSED:** Current flows (requests allowed)
- **OPEN:** Current blocked (requests rejected)
- **HALF_OPEN:** Testing if issue resolved

---

### 4. Adaptive Control Loop

**Problem:** Static limits don't adapt to backend capacity.

**Solution:** Feedback loop

```
┌─────────────────────────────────────┐
│   Observe Error Rate Every 10s      │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│   Calculate New Multiplier          │
│   - Error >50%: Halve (0.5x)        │
│   - Error <5%:  Restore (1.0x)      │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│   Apply to All Rate Limits          │
│   PREMIUM: 500 * 0.5 = 250 req/s    │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│   Publish to Redis                  │
│   (Other instances pick up change)  │
└─────────────────────────────────────┘
```

**Why 10 second intervals?**
- Too fast (1s): React to transient spikes
- Too slow (60s): Backend crashes before adjustment
- 10s: Balance responsiveness vs stability

**Why error thresholds (50%, 5%)?**
- 50%: Clearly unhealthy (not transient)
- 5%: Healthy enough to restore
- Gap (5-50%): Prevents oscillation

**Alternative: PID Controller**
- More sophisticated (Proportional-Integral-Derivative)
- Used in industrial control systems
- ❌ Overkill for this use case
- ✅ Simple thresholds work fine

---

## Design Tradeoffs

### Tradeoff 1: JWT vs Sessions

| Aspect | JWT | Session |
|--------|-----|---------|
| **Scalability** | ✅ Stateless | ❌ Needs shared store |
| **Revocation** | ❌ Can't revoke until expiry | ✅ Delete from store |
| **Size** | ❌ ~800 bytes | ✅ ~36 bytes (ID) |
| **Latency** | ✅ No DB lookup | ❌ Redis/DB lookup |

**BLACKBOX Choice: JWT**
- Prioritize: Scalability, latency
- Accept: Can't revoke (mitigate with short expiry: 15 min)

**When to use Sessions:**
- Need instant revocation (e.g., "log out all devices")
- Token size matters (mobile apps on 3G)

---

### Tradeoff 2: Synchronous vs Asynchronous Processing

**Current: Synchronous**
```java
public void route(HttpServletRequest request, ...) {
    // Block until backend responds
    byte[] response = webClient.retrieve()
        .bodyToMono(byte[].class)
        .block();  // ← Synchronous!
}
```

**Alternative: Async (WebFlux)**
```java
public Mono<ResponseEntity> route(...) {
    return webClient.retrieve()
        .bodyToMono(byte[].class)
        .map(ResponseEntity::ok);  // Non-blocking
}
```

| Aspect | Sync (Current) | Async (WebFlux) |
|--------|----------------|-----------------|
| **Throughput** | ⭐⭐⭐ (200 threads) | ⭐⭐⭐⭐⭐ (Event loop) |
| **Latency** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Code Complexity** | ⭐⭐⭐⭐⭐ (Easy) | ⭐⭐ (Reactive harder) |
| **Debugging** | ⭐⭐⭐⭐⭐ | ⭐⭐ (Stack traces hard) |

**BLACKBOX Choice: Synchronous**
- Throughput adequate (500 req/s)
- Code simplicity > marginal gains
- Team familiarity with synchronous code

**When to use Async:**
- Need >10,000 req/s
- I/O-bound (waiting on slow backends)
- Team experienced with reactive programming

---

### Tradeoff 3: Single Gateway vs Microgateway Per Service

**Option 1: Single Gateway (BLACKBOX)**
```
All Clients → BLACKBOX Gateway → All Backends
```
✅ Centralized policy  
✅ Single point of monitoring  
❌ Single point of failure  
❌ All services share rate limit pool  

**Option 2: Microgateway**
```
Clients → Payment Gateway → Payment Service
Clients → User Gateway → User Service
```
✅ Isolated blast radius  
✅ Independent deployment  
❌ Policy duplication  
❌ Monitoring fragmentation  

**BLACKBOX Choice: Single Gateway**
- Org size: Small (<10 teams)
- Deployment: Monorepo OK
- Failure: Mitigated with HA (multiple instances)

**When to use Microgateway:**
- Large org (>50 teams)
- Service autonomy critical
- Different SLAs per service

---

## Scalability Considerations

### Horizontal Scaling

**Current Setup:**
```
Load Balancer
    ├─ Gateway Instance 1
    ├─ Gateway Instance 2
    └─ Gateway Instance N
```

**Shared State:**
- Redis: Rate limit counters
- PostgreSQL: Audit logs

**Bottlenecks:**

1. **Redis Connection Pool**
   - Limit: 100 connections/instance
   - Fix: Increase pool size or use Redis Cluster

2. **PostgreSQL Writes**
   - Limit: ~10,000 writes/second
   - Fix: Batch inserts, use TimescaleDB

3. **Circuit Breaker State**
   - Current: Per-instance (not shared)
   - Risk: Instance 1 opens circuit, Instance 2 doesn't know
   - Fix: Store circuit state in Redis

**Capacity Planning:**

| Metric | Single Instance | 10 Instances |
|--------|-----------------|--------------|
| **Throughput** | 500 req/s | 5,000 req/s |
| **Latency p95** | 60ms | 65ms |
| **Memory** | 500MB | 5GB |
| **CPU** | 1 core | 10 cores |

---

### Vertical Scaling

**Current:** 1 CPU, 1GB RAM

**Limits:**
- CPU: ~2,000 req/s (becomes bottleneck)
- Memory: 500MB used, 1GB sufficient

**When to vertical scale:**
- Horizontal scaling expensive (licensing)
- Single-tenant deployment
- Quick fix before horizontal scaling

**Recommendation:** Horizontal scaling (commodity hardware)

---

## Security Architecture

### Authentication Flow

```
1. Client generates JWT offline (or via /test/token)
   Claims: {sub: "clientId", tier: "PREMIUM", exp: 15min}

2. Client includes in header:
   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...

3. Gateway validates:
   - Signature (HMAC-SHA512)
   - Expiration (not expired)
   - Claims (clientId exists)

4. If valid → proceed
   If invalid → 401 Unauthorized
```

**Security Properties:**
- ✅ Stateless (scales)
- ✅ Tamper-proof (signature verification)
- ✅ Time-limited (15 min expiry)
- ❌ Can't revoke (accept as tradeoff)

**Secret Management:**
```yaml
# application.yml
gateway:
  jwt:
    secret: ${JWT_SECRET:fallback-dev-secret}
```

**Production:**
```bash
# Set via environment variable
export JWT_SECRET=$(openssl rand -base64 32)

# Or use secret manager
kubectl create secret generic jwt-secret \
  --from-literal=JWT_SECRET=<actual-secret>
```

**Never commit secrets to git!**

---

### Rate Limiting Security

**Attack: Bypass via Multiple IPs**
```
Attacker uses 100 IPs
Each IP gets 100 req/s
Total: 10,000 req/s (bypassed limit!)
```

**Current Mitigation:**
- Rate limit by `clientId` (in JWT), not IP
- Attacker needs 100 valid JWTs (harder)

**Future Enhancement:**
- IP-based rate limiting (additional layer)
- CAPTCHA after N failed auth attempts

---

### Audit Logging Security

**Requirements:**
- Immutable (can't edit past logs)
- Complete (all significant events)
- Tamper-evident (detect modifications)

**Implementation:**
```java
AuditLog.builder()
    .eventType("RATE_LIMIT_ADJUST")
    .source("AdaptiveRateLimitController")
    .timestamp(Instant.now())  // UTC
    .details(jsonDetails)
    .build();

repository.save(auditLog);  // Append-only
```

**Postgres Configuration:**
```sql
-- No UPDATE/DELETE allowed
REVOKE UPDATE, DELETE ON audit_log FROM app_user;
GRANT INSERT, SELECT ON audit_log TO app_user;
```

**Compliance:**
- SOC 2: Audit logs for all access
- PCI DSS: Log authentication events
- GDPR: Log personal data access

---

## Summary

**BLACKBOX Architecture Principles:**

1. **Simplicity over Complexity**
   - Simple thresholds > ML models
   - Synchronous > Async (when sufficient)

2. **Proven Technologies**
   - Spring Boot (enterprise standard)
   - Redis (industry standard for caching)
   - Prometheus (CNCF graduated project)

3. **Explicit Tradeoffs**
   - JWT (scalability) over Sessions (revocation)
   - Single gateway (centralization) over Microgateways (isolation)

4. **Observable by Default**
   - Metrics: Prometheus
   - Logs: PostgreSQL audit
   - Dashboards: Grafana

5. **Secure by Design**
   - Authentication required (JWT)
   - Rate limiting enforced
   - Audit trail complete

**Next:** See `DEVELOPMENT.md` for hands-on implementation and `CONTRIBUTING.md` for collaboration guidelines.
