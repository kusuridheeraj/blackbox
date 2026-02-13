# BLACKBOX API Gateway - Intern Onboarding Guide

**Welcome to the BLACKBOX project!** 🎉

You're joining a real-world production-grade API Gateway project. This guide will take you from zero knowledge to understanding every architectural decision, every line of code, and why things are done the way they are.

---

## 📚 Table of Contents

1. [What is BLACKBOX?](#what-is-blackbox)
2. [The Problem We're Solving](#the-problem-were-solving)
3. [Who Benefits From This?](#who-benefits-from-this)
4. [Your Learning Path (30 Days)](#your-learning-path)
5. [Understanding the Codebase](#understanding-the-codebase)
6. [Common Pitfalls to Avoid](#common-pitfalls-to-avoid)
7. [How to Make Your First Contribution](#how-to-make-your-first-contribution)

---

## What is BLACKBOX?

**BLACKBOX is an API Gateway** - think of it as a smart security guard + traffic controller for web services.

### Real-World Analogy

Imagine a shopping mall:
- **Backend Services** = Individual stores (Nike, Apple, Starbucks)
- **API Gateway (BLACKBOX)** = Mall entrance with security
- **Clients** = Shoppers trying to enter

**What BLACKBOX does:**
1. **Authentication** - Checks if you have a valid mall pass (JWT token)
2. **Rate Limiting** - Prevents one person from rushing in 1000 times/second
3. **Routing** - Directs you to the right store
4. **Circuit Breaking** - Closes access to a store if it's on fire (backend down)
5. **Monitoring** - Counts how many people visit which stores

---

## The Problem We're Solving

### Problem 1: Unprotected Backend Services

**Without Gateway:**
```
Client → Backend Service (exposed, vulnerable)
```

**Issues:**
- ❌ No authentication
- ❌ No rate limiting (anyone can spam 10,000 requests)
- ❌ Backend gets overwhelmed
- ❌ One bad actor crashes the system

**With BLACKBOX:**
```
Client → BLACKBOX Gateway → Backend Service (protected)
```

**Benefits:**
- ✅ JWT authentication (only valid users get through)
- ✅ Rate limiting (max 500 req/s per client)
- ✅ Backend never sees malicious traffic
- ✅ System stays stable

---

### Problem 2: Static Rate Limits Don't Work

**Traditional approach:**
```java
if (requests > 100) {
    reject(); // Always reject at 100 req/s
}
```

**Problem:** What if backend is dying?
- Backend can only handle 50 req/s right now
- Gateway still allows 100 req/s
- Backend crashes! 💥

**BLACKBOX's solution: Adaptive Rate Limiting**
```java
if (errorRate > 50%) {
    reduceLimit(100 → 50); // Automatically protect backend
}
if (errorRate < 5%) {
    restoreLimit(50 → 100); // Restore when healthy
}
```

**This is the core innovation!** The gateway "heals itself" by watching error rates.

---

### Problem 3: Cascade Failures

**Scenario:**
1. Payment service goes down
2. Gateway keeps sending requests
3. Requests timeout (5 seconds each)
4. Gateway's thread pool fills up
5. Gateway crashes
6. **Everything is down** 😱

**BLACKBOX's Circuit Breaker:**
```
Payment Service fails 5 times
→ Circuit OPENS (blocks all requests)
→ Wait 30 seconds
→ Send 1 test request (HALF_OPEN)
→ Success? Close circuit. Failure? Stay open.
```

**Result:** Gateway stays alive even when backends die.

---

## Who Benefits From This?

### 1. **E-commerce Companies**
- **Pain:** Black Friday traffic spikes crash the site
- **Solution:** BLACKBOX throttles aggressive clients, protects backend
- **Value:** $1M in lost sales prevented

### 2. **API-as-a-Service Companies** (Stripe, Twilio)
- **Pain:** Free tier users abuse the API (1000s of requests)
- **Solution:** Rate limiting per tier (FREE: 50/s, PREMIUM: 500/s)
- **Value:** Fair usage, paying customers get guaranteed service

### 3. **Microservices Teams**
- **Pain:** When one service fails, everything fails (cascade)
- **Solution:** Circuit breaker isolates failures
- **Value:** 99.9% uptime instead of 95%

### 4. **DevOps Engineers**
- **Pain:** No visibility into traffic patterns
- **Solution:** Grafana dashboard shows real-time metrics
- **Value:** Debug issues in 5 minutes instead of 5 hours

---

## Your Learning Path (30 Days)

### Week 1: Understanding HTTP & APIs

**Goal:** Learn the basics before touching code.

**Day 1-2: What is an API?**
- Read: [What is a REST API (RedHat)](https://www.redhat.com/en/topics/api/what-is-a-rest-api)
- Practice: Use `curl` to make HTTP requests
  ```bash
  curl http://api.github.com/users/octocat
  ```

**Day 3-4: HTTP Status Codes**
- 200 = Success
- 401 = Unauthorized (no valid token)
- 429 = Too Many Requests (rate limited)
- 500 = Server Error
- 503 = Service Unavailable (circuit breaker open)

**Day 5: JWT Tokens**
- Read: [JWT Introduction (jwt.io)](https://jwt.io/introduction)
- Understand: How BLACKBOX validates tokens

**Assignment:** Make 10 curl requests with different status codes. Document what each means.

---

### Week 2: Understanding the Architecture

**Goal:** Read code from the outside-in.

**Day 6: Start with Docker Compose**
- File: `docker-compose.yml`
- **Why start here?** It shows the entire system layout
- Services: gateway, mock-backend, redis, postgres, prometheus, grafana

**Day 7: Trace One Request**
Follow this path:
1. Client sends `GET /api/test` with JWT token
2. `JwtAuthFilter.java` validates token
3. `RateLimitFilter.java` checks rate limit
4. `RequestRouter.java` forwards to backend
5. Response returned to client

**Day 8-9: Read Each Component**
- `JwtAuthFilter.java` - Why JWT? (stateless auth)
- `RateLimitFilter.java` - Why Redis? (distributed state)
- `CircuitBreaker.java` - Why per-route? (isolation)

**Day 10: Read Tests**
- File: `k6/baseline.js`
- Understand: How load tests prove the system works

**Assignment:** Draw a diagram showing the request flow. Use arrows and labels.

---

### Week 3: Understanding Design Decisions

**Goal:** Learn WHY code is written this way.

**Day 11-12: Why Spring Boot?**
- **Alternatives:** Node.js, Go, Python
- **Why Spring?**
  - Industry standard in enterprise
  - Mature ecosystem (Spring Security, Spring Data)
  - Built-in metrics (Micrometer)
  - Hiring advantage (most Java devs know Spring)

**Day 13-14: Why Redis for Rate Limiting?**
- **Problem:** Multiple gateway instances need shared state
- **Alternative 1:** In-memory (doesn't work across instances)
- **Alternative 2:** Database (too slow, 5ms latency)
- **Redis:** 0.5ms latency, distributed, atomic operations

**Day 15-16: Why Adaptive Rate Limiting?**
- Read: `docs/blog-1-static-rate-limiting.md`
- **Problem:** Static limits don't adapt to backend health
- **ML Alternative:** Too complex, requires training data
- **Heuristic Approach:** Simple rules that work immediately

**Assignment:** Write a 1-page doc explaining ONE design decision to a non-technical friend.

---

### Week 4: Hands-On Development

**Goal:** Make your first code change.

**Day 17-18: Setup Development Environment**
- Follow: `DEVELOPMENT.md` (instructions below)
- Install: Java 21, Docker, Maven
- Run: `docker compose up`
- Verify: Gateway starts successfully

**Day 19-20: Make a Small Change**
**Task:** Add a new tier "ENTERPRISE" with 1000 req/s limit

Files to change:
1. `application.yml` - Add tier config
2. `TestTokenController.java` - Allow tier in token generation
3. `k6/enterprise-test.js` - Create test script

**Day 21-23: Test Your Change**
1. Rebuild gateway: `docker compose up gateway --build`
2. Generate token: `curl http://localhost:8080/test/token?tier=ENTERPRISE`
3. Run test: `k6 run k6/enterprise-test.js`
4. Verify: Grafana shows 1000 req/s throughput

**Day 24-25: Code Review Practice**
- Read: Your own code
- Ask: "Would another engineer understand this?"
- Add: Comments explaining WHY (not WHAT)

**Day 26-30: Pick a Real Issue**
- Check: GitHub issues
- Choose: "good first issue" label
- Implement, test, submit PR

---

## Understanding the Codebase

### Architecture Overview

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP Request + JWT Token
       ▼
┌─────────────────────────────────┐
│    BLACKBOX Gateway             │
│  ┌──────────────────────────┐   │
│  │  1. JwtAuthFilter        │   │ ← Validates token
│  └────────────┬─────────────┘   │
│               ▼                  │
│  ┌──────────────────────────┐   │
│  │  2. RateLimitFilter      │   │ ← Checks rate limit (Redis)
│  └────────────┬─────────────┘   │
│               ▼                  │
│  ┌──────────────────────────┐   │
│  │  3. CircuitBreaker       │   │ ← Checks backend health
│  └────────────┬─────────────┘   │
│               ▼                  │
│  ┌──────────────────────────┐   │
│  │  4. RequestRouter        │   │ ← Forwards request
│  └────────────┬─────────────┘   │
└───────────────┼─────────────────┘
                │
                ▼
      ┌─────────────────┐
      │  Backend API    │
      └─────────────────┘
```

---

### Key Components Explained

#### 1. JWT Authentication (`JwtAuthFilter.java`)

**What:** Validates JSON Web Tokens

**Why JWT instead of sessions?**
- **Sessions:** Server stores login state in memory/database
  - Problem: Doesn't scale to 1000s of servers
  - Problem: Database lookup on every request (slow)
- **JWT:** Client sends token, server validates signature
  - Benefit: Stateless (no database lookup)
  - Benefit: Works across multiple gateway instances
  - Tradeoff: Can't "revoke" a token until it expires

**Code walkthrough:**
```java
// 1. Extract token from header
String token = request.getHeader("Authorization");

// 2. Verify signature (proves token wasn't tampered with)
Jwts.parserBuilder()
    .setSigningKey(secretKey)  // Only gateway knows this secret
    .build()
    .parseClaimsJws(token);    // Throws exception if invalid

// 3. Extract user info
String clientId = claims.getSubject();
String tier = claims.get("tier");
```

**Why this matters:**
- No database call = 10x faster (0.1ms vs 5ms)
- Scales to millions of requests/second

---

#### 2. Rate Limiting (`RateLimitFilter.java` + `TokenBucketRateLimiter.java`)

**Algorithm: Token Bucket**

**Analogy:** Imagine a bucket that holds tokens.
- Bucket capacity: 750 tokens (burst size)
- Refill rate: 500 tokens/second (sustained rate)
- Each request costs 1 token
- If bucket empty → 429 Too Many Requests

**Why this algorithm?**
- **Alternative 1: Fixed Window**
  ```
  Window 1: 0 req in first 59 seconds
  Window 1: 1000 req in last 1 second ← Spike!
  Window 2: 1000 req in first 1 second ← Spike!
  = 2000 req in 2 seconds (should be 1000)
  ```
  Problem: Allows "burst at boundary"

- **Alternative 2: Leaky Bucket**
  - Problem: Doesn't allow bursty traffic (bad UX)
  - Example: User clicks "pay" 5 times → only 1st succeeds

- **Token Bucket (BLACKBOX's choice)**
  - Allows bursts (good UX)
  - Enforces sustained rate (protects backend)
  - Industry standard (used by AWS, Google Cloud)

**Why Redis for storage?**
```java
// Problem: Multiple gateway instances
Instance 1: User makes 100 requests
Instance 2: User makes 100 requests
= 200 req/s (should be limited to 100)

// Solution: Shared state in Redis
Both instances check same counter in Redis
```

**Code walkthrough:**
```java
// Use Lua script for atomicity
String luaScript = """
    local tokens = redis.call('get', KEYS[1])
    if tonumber(tokens) >= 1 then
        redis.call('decr', KEYS[1])  -- Take 1 token
        return 1  -- Allow request
    else
        return 0  -- Reject (429)
    end
""";
```

**Why Lua script?**
- Runs atomically on Redis (no race conditions)
- Alternative: GET → DECR → SET (not atomic, can have race)

---

#### 3. Circuit Breaker (`CircuitBreaker.java`)

**Problem it solves:**

```
Backend is slow (5 sec timeout per request)
Gateway has 200 threads
200 threads × 5 sec = 1000 requests stuck
All threads blocked → Gateway can't accept new requests
Gateway appears "hung" even though it's fine!
```

**Solution: Circuit Breaker**

**State Machine:**
```
       5 failures
CLOSED ─────────► OPEN ─────────► HALF_OPEN
   ▲                │ 30s wait         │
   │                └──────────────────┘
   │                 1 success = close
   └────────────────────────────────────
```

**States explained:**
- **CLOSED:** Normal operation, requests flow
- **OPEN:** Backend is down, block all requests (fail fast)
- **HALF_OPEN:** Testing recovery, allow 1 probe request

**Why per-route circuit breakers?**
```
Route 1: /api/payments (backend: payments-service)
Route 2: /api/users    (backend: users-service)

If payments-service is down:
❌ Don't block users-service (they're independent!)
✅ Open circuit for /api/payments only
```

**Code walkthrough:**
```java
public boolean allowRequest(String routeId) {
    CircuitState state = getState(routeId);
    
    switch (state) {
        case CLOSED:
            return true;  // Normal operation
        
        case OPEN:
            long openedAt = openTimestamps.get(routeId);
            if (System.currentTimeMillis() - openedAt >= 30_000) {
                transitionTo(routeId, HALF_OPEN);
                return true;  // Try probe request
            }
            return false;  // Still cooling down
        
        case HALF_OPEN:
            return true;  // Allow probe
    }
}
```

**Why 30 seconds cooldown?**
- Too short (5s): Backend hasn't recovered, more failures
- Too long (5min): Users wait too long for recovery
- 30s: Industry standard (AWS, Netflix use similar)

---

#### 4. Adaptive Rate Limiting (`AdaptiveRateLimitController.java`)

**The Innovation:** Self-healing rate limits

**Problem:**
```
Normal: Backend handles 1000 req/s
Incident: Database slow, backend handles 500 req/s
Gateway: Still allowing 1000 req/s
Result: Backend crashes! 💥
```

**Solution:**
```java
@Scheduled(fixedRate = 10_000)  // Run every 10 seconds
public void evaluateAndAdjust() {
    double errorRate = errors / total;
    
    if (errorRate > 50%) {
        // CRITICAL: Halve all rate limits
        multiplier = 0.5;
        mode = TIGHTENED;
    } else if (errorRate < 5%) {
        // HEALTHY: Restore normal limits
        multiplier = 1.0;
        mode = NORMAL;
    }
}
```

**Modes:**
- **NORMAL (1.0x):** Error rate < 5%, full speed ahead
- **CAUTIOUS (0.8x):** Error rate 5-20%, slightly reduce
- **TIGHTENED (0.5x):** Error rate > 50%, emergency brakes
- **RECOVERING:** Gradually restore from tightened

**Why NOT machine learning?**
- **ML approach:** Train model on historical data
  - Problem: Need months of data
  - Problem: Doesn't work for new systems
  - Problem: Can't explain predictions (black box)
  
- **Heuristic approach (BLACKBOX):**
  - Works immediately (no training data)
  - Explainable (if error > 50%, reduce)
  - Tunable (change thresholds easily)

**Real-world impact:**
```
09:00 AM: Backend healthy, 1000 req/s allowed
09:15 AM: Database slow, error rate jumps to 60%
09:15 AM: BLACKBOX detects, reduces to 500 req/s
09:16 AM: Error rate drops to 10%
09:20 AM: Error rate < 5% for 5 minutes
09:20 AM: BLACKBOX restores to 1000 req/s
```

**Without BLACKBOX:**
```
09:15 AM: Error rate 60%
09:16 AM: All requests failing
09:20 AM: PagerDuty alert
09:25 AM: Engineer wakes up
09:30 AM: Engineer logs in
09:35 AM: Engineer reduces limits manually
Result: 20 minutes of downtime
```

**With BLACKBOX:**
```
09:15 AM: Error rate 60%
09:15 AM: BLACKBOX auto-adjusts
Result: 0 minutes of downtime ✅
```

---

### Observability & Monitoring

#### Why Metrics Matter

**Without metrics:**
```
User: "Site is slow!"
Engineer: "Let me guess... check logs... check database... check network..."
Time to diagnose: 2 hours
```

**With metrics:**
```
User: "Site is slow!"
Engineer: Opens Grafana → sees p95 latency spiked to 5 seconds
         → sees circuit breaker OPEN for payments-service
         → root cause found in 30 seconds
```

#### Metrics Implemented

**1. Request Total (`gateway_request_total`)**
- Why: Track traffic volume, detect spikes
- Use case: "Traffic doubled at 3 PM, what happened?"

**2. Request Duration (`gateway_request_duration_seconds`)**
- Why: Measure latency, find slow endpoints
- Use case: "p95 latency is 500ms, SLA requires <100ms"

**3. Circuit Breaker State (`gateway_circuit_breaker_state`)**
- Why: Know when backends are unhealthy
- Use case: "Payments service circuit has been OPEN for 10 minutes"

**4. Downstream Errors (`gateway_downstream_error_total`)**
- Why: Feed adaptive rate limiter
- Use case: "50% error rate triggered TIGHTENED mode"

**5. Adaptive Adjustments (`gateway_adaptive_adjustment_total`)**
- Why: Track self-healing actions
- Use case: "System auto-adjusted limits 5 times today, investigate backend"

**6. Rate Limit Throttle (`gateway_rate_limit_throttled_total`)**
- Why: Detect abusive clients
- Use case: "Client X is getting throttled 1000 times/min, potential abuse"

**7. Fallback Rate (`gateway_fallback_total`)**
- Why: Detect Redis outages
- Use case: "Using local rate limiter, Redis might be down"

---

## Common Pitfalls to Avoid

### 1. **Don't Modify Core Logic Without Understanding**

**❌ Bad:**
```java
// "Let me just change this threshold..."
private static final int FAILURE_THRESHOLD = 3;  // Changed from 5
```

**Why bad:** Circuit opens too quickly, backend doesn't get chance to recover.

**✅ Good:**
1. Read why 5 was chosen (check git blame, read docs)
2. Test with different values locally
3. Propose change with data: "Tested with 3, circuit opens 2x faster, reducing MTTR by 30%"

---

### 2. **Don't Skip Tests**

**❌ Bad:**
```bash
# Make code change
git add .
git commit -m "fix"
git push
# Hope it works in production 🤞
```

**✅ Good:**
```bash
# Make code change
docker compose up --build          # Build with changes
k6 run k6/baseline.js              # Run load test
# Check Grafana for issues
# If pass → commit
```

---

### 3. **Don't Hardcode Values**

**❌ Bad:**
```java
if (requests > 100) {  // Hardcoded!
    reject();
}
```

**✅ Good:**
```java
@Value("${gateway.ratelimit.threshold}")
private int threshold;  // From application.yml

if (requests > threshold) {
    reject();
}
```

**Why:** Configuration should be changeable without recompiling code.

---

### 4. **Don't Ignore Logs**

**❌ Bad:**
```java
try {
    processRequest();
} catch (Exception e) {
    // Swallow exception 😱
}
```

**✅ Good:**
```java
try {
    processRequest();
} catch (Exception e) {
    log.error("Failed to process request: {}", e.getMessage(), e);
    metrics.recordError("request_processing");
    throw new GatewayException("Request failed", e);
}
```

---

### 5. **Don't Merge Without Code Review**

**❌ Bad:**
```bash
git commit -m "big refactor"
git push origin main  # Directly to main!
```

**✅ Good:**
```bash
git checkout -b feature/my-improvement
git commit -m "feat: add enterprise tier support"
git push origin feature/my-improvement
# Create Pull Request
# Request review from 2 engineers
# Address feedback
# Merge after approval ✅
```

---

## How to Make Your First Contribution

### Step 1: Pick a Task

**Good first issues:**
- Add new rate limit tier
- Improve error messages
- Add more unit tests
- Update documentation
- Add new Grafana panel

**Not recommended for first contribution:**
- Rewrite circuit breaker logic
- Change core routing algorithm
- Modify Lua scripts in Redis

---

### Step 2: Setup Development Environment

See `DEVELOPMENT.md` (created below)

---

### Step 3: Make Changes

**Follow TDD (Test-Driven Development):**
1. Write test first
2. Run test (should fail)
3. Write code to make it pass
4. Refactor
5. Commit

---

### Step 4: Test Locally

```bash
# Build
docker compose up --build

# Generate token
curl http://localhost:8080/test/token?clientId=test&tier=PREMIUM

# Test manually
curl http://localhost:8080/api/test -H "Authorization: Bearer <token>"

# Load test
k6 run k6/baseline.js

# Check metrics in Grafana
open http://localhost:3000
```

---

### Step 5: Submit Pull Request

**PR Template:**
```markdown
## What does this PR do?
[One-line summary]

## Why are we doing this?
[Problem statement]

## How did you test this?
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] Load test with k6
- [ ] Grafana shows correct metrics

## Screenshots
[If UI change, include before/after screenshots]

## Checklist
- [ ] Code follows style guide
- [ ] Added tests
- [ ] Updated documentation
- [ ] No breaking changes (or documented)
```

---

## Summary

**BLACKBOX is production-grade infrastructure.** Every line of code serves a purpose:
- **JWT Auth:** Stateless security at scale
- **Rate Limiting:** Protect backends, ensure fair usage
- **Circuit Breaker:** Isolate failures, prevent cascades
- **Adaptive Limits:** Self-healing under stress
- **Observability:** Debug in seconds, not hours

**Your role as an intern:**
1. **Learn:** Understand why, not just what
2. **Question:** Ask "why this way?" for everything
3. **Contribute:** Start small, grow confidence
4. **Document:** Make it easier for the next intern

**Remember:** Every senior engineer was once in your shoes. The difference is they asked questions, made mistakes, and learned from them.

Welcome to the team! 🚀

---

**Next steps:**
1. Read `ARCHITECTURE.md` for deeper design decisions
2. Follow `DEVELOPMENT.md` for hands-on setup
3. Review `CONTRIBUTING.md` for collaboration guidelines
4. Start with "good first issue" on GitHub

