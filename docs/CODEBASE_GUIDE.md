# BLACKBOX Codebase Guide

This comprehensive guide provides a deep dive into the BLACKBOX API Gateway codebase, designed to help new developers understand, navigate, and modify the system effectively.

---

## 1. PROJECT OVERVIEW & ARCHITECTURE

### High-Level Architecture
- **Type**: Monolithic Java Spring Boot Application acting as an **API Gateway**.
- **Purpose**: To provide a resilient, self-healing interface between clients and backend microservices, featuring adaptive rate limiting that adjusts to downstream health.
- **Tech Stack**:
  - **Core**: Java 17, Spring Boot 3.2 (WebMvc + WebFlux client)
  - **State**: Redis (Rate limiting counters, distributed state)
  - **Persistence**: PostgreSQL (Audit logs)
  - **Observability**: Micrometer, Prometheus, Grafana

### Architecture Diagram

```ascii
                                      ┌──────────────┐
                                      │  Prometheus  │◄───── Scrapes Metrics
                                      └──────▲───────┘
                                             │
      ┌─────────┐                     ┌──────┴───────┐
      │ Client  │ ──────HTTPS───────► │   BLACKBOX   │
      └─────────┘                     │   GATEWAY    │
                                      └──────┬───────┘
                                             │
                       ┌─────────────────────┼─────────────────────┐
                       │                     │                     │
                ┌──────▼──────┐       ┌──────▼──────┐       ┌──────▼──────┐
                │    Redis    │       │ PostgreSQL  │       │  Downstream │
                │ (Counters)  │       │ (Audit Logs)│       │   Service   │
                └─────────────┘       └─────────────┘       └─────────────┘
```

### Technology Trade-offs

| Technology | Choice | Why Chosen | Alternatives | Trade-off Accepted |
|------------|--------|------------|--------------|-------------------|
| **Language** | Java 17 | Strong typing, massive ecosystem, high performance. | Go, Node.js | Java has higher memory footprint than Go, but better libraries for enterprise patterns. |
| **Framework** | Spring Boot | Opinionated, production-ready, huge community. | Micronaut, Quarkus | Spring Boot is heavier ("magic") but offers faster dev speed for standard apps. |
| **State** | Redis | Atomic counters (Lua), fast, persistent. | Hazelcast, Memcached | Redis introduces an external dependency, but is industry standard for rate limiting. |
| **Routing** | WebClient | Non-blocking I/O for high throughput. | Zuul, HttpClient | Creating a new `WebClient` per request (if not pooled) can be costly, but affords defined timeouts. |
| **Database** | PostgreSQL | Relational reliability for audit trails. | MongoDB, Cassandra | SQL implies rigid schema, but audit logs are structured. |

---

## 2. PROJECT STRUCTURE DEEP DIVE

### Directory Structure

`/src/main/java/com/blackbox/gateway` is the root package.

- **`BlackboxApplication.java`**:
  - **Purpose**: Main entry point. Enables scheduling.
  - **Modifications**: Rarely touched unless adding global startup hooks.

- **`/config`** (`GatewayProperties.java`, `RedisConfig.java`):
  - **Purpose**: Configuration beans and type-safe property binding.
  - **Responsibilities**: Reading `application.yml`, setting up Redis templates.
  - **Modifications**: Add new configurable parameters (e.g., new rate limit tiers) here.

- **`/filter`** (`JwtAuthFilter.java`, `RateLimitFilter.java`):
  - **Purpose**: The "guards" of the gateway.
  - **Responsibilities**: Authentication (JWT) and Rate Limiting enforcement.
  - **Entry Point**: `JwtAuthFilter` is the first code to touch a request.
  - **Modifications**: Change here to support new auth schemes (e.g., OAuth2) or add custom headers.

- **`/ratelimit`** (`AdaptiveRateLimitController.java`, `TokenBucketRateLimiter.java`):
  - **Purpose**: The core logic for traffic control.
  - **Responsibilities**: Calculating token buckets, monitoring health, adjusting limits (Self-Healing).
  - **Modifications**: Modify `AdaptiveRateLimitController` to change the heuristics for "tightening" or "recovering".

- **`/routing`** (`RequestRouter.java`):
  - **Purpose**: Forwarding requests to backends.
  - **Responsibilities**: Constructing downstream requests, handling timeouts, circuit breaking.
  - **Modifications**: Change here to support WebSocket routing or complex load balancing.

- **`/circuitbreaker`** (`CircuitBreaker.java`):
  - **Purpose**: Preventing cascades of failure.
  - **Responsibilities**: Tracking failures per route and "opening" the circuit.

- **`/model`** & **`/repository`**:
  - **Purpose**: Data structures and DB access.
  - **Responsibilities**: Defining `ClientIdentity`, `AuditLog`, `GatewayErrorResponse`.

---

## 3. CORE FUNCTIONALITY BREAKDOWN

### Feature: Adaptive Rate Limiting
**What it does**: Automatically reduces valid traffic throughput when backend errors rise.
**Why it exists**: To prevent "death spirals" where a struggling service is hammered by retries.
**How it works**:
1.  **Monitor**: `AdaptiveRateLimitController` runs every 10s (`@Scheduled`).
2.  **Evaluate**: checks `error_rate = failures / total_requests`.
3.  **Adjust**:
    -   > 50% Error: Enter **TIGHTENED** mode (0.5x multiplier).
    -   > 20% Error: Enter **CAUTIOUS** mode (0.8x multiplier).
    -   < 5% Error (sustained): Enter **RECOVERING** mode (gradual increase).
4.  **Enforce**: `RateLimitFilter` applies `limit * multiplier`.
**Code location**: `src/main/java/com/blackbox/gateway/ratelimit/AdaptiveRateLimitController.java`
**Dependencies**: Redis (to share state across instances, though current impl uses local AtomicLongs for calculation -> **Potential issue in distributed deploy**).

### Feature: Request Routing
**What it does**: Proxies requests from `/api/...` to configured downstream URLs.
**Why it exists**: Decouples clients from backend service locations.
**How it works**:
1.  `GatewayController` (implied standard Spring MVC or Catch-all) receiving request.
2.  `RequestRouter` finds matching route from `GatewayProperties`.
3.  Checks `CircuitBreaker`.
4.  Builds `WebClient` request.
5.  Returns response or maps error to `GatewayErrorResponse`.
**Code location**: `src/main/java/com/blackbox/gateway/routing/RequestRouter.java`

---

## 4. DATA FLOW DOCUMENTATION

### Request/Response Flow

1.  **Entry Point**: Client sends `GET /api/users/123`.
2.  **Filter 1 (Auth)**: `JwtAuthFilter` intercepts.
    -   Checks `Authorization: Bearer <token>`.
    -   Parses JWT, validation signature.
    -   Extracts `ClientIdentity` (Tier: PREMIUM).
    -   *Fail*: Returns 401 Unauthorized.
3.  **Filter 2 (Rate Limit)**: `RateLimitFilter` intercepts.
    -   Consults `AdaptiveRateLimitController` for current multiplier.
    -   Runs Lua script in Redis to decrement bucket.
    -   *Fail*: Returns 429 Too Many Requests (with `Retry-After`).
4.  **Routing**: `RequestRouter` takes over.
    -   Matches URI to backend `http://user-service:8081`.
    -   Checks Circuit Breaker status for this route.
5.  **Forwarding**: `WebClient` sends HTTP request to backend.
6.  **Response**:
    -   Backend returns 200 OK.
    -   `RequestRouter` records stats (success) to `AdaptiveRateLimitController`.
    -   Response piped back to client.

### Database/State Management
-   **Redis**: Stores rate limit buckets. keys: `rate_limit:{route}:{client_id}`. TTL: 1 second.
-   **PostgreSQL**: Stores `AuditLog` entries. Table `audit_log`.
    -   Schema: `id`, `timestamp`, `event_type`, `source`, `details` (JSON).
    -   Migration: `hibernate.ddl-auto: update` (Dev/Test only). **Production recommendation**: Use Flyway/Liquibase.

---

## 5. SETUP & RUNNING GUIDE

### Prerequisites
-   Docker & Docker Compose
-   Java 17 (for local dev)
-   Maven 3.8+

### Installation & Running

```bash
# 1. Clone
git clone <repo>
cd blackbox

# 2. Run Infrastructure (Postgres, Redis, Grafana, App)
docker compose up --build -d

# 3. Check logs to ensure startup
docker compose logs -f gateway
```

### Configuration
-   **Routes**: Defined in `src/main/resources/application.yml` under `gateway.routes`.
-   **Secrets**: `JWT_SECRET` should be set via environment variable `JWT_SECRET` in `docker-compose.yml`.

---

## 6. TESTING GUIDE

### Test Structure
> [!WARNING]
> This project currently lacks standard JUnit unit tests in `src/test/java`.

-   **Primary Testing Strategy**: **Load Testing** via `k6`.
-   **Location**: `/k6` directory.

### Running Tests (k6)
Requires `k6` installed locally or run via docker.

```bash
# 1. Generate a JWT token (required script)
# (Use the curl command from README to get a token)

# 2. Run Baseline Test (100 RPS)
k6 run -e JWT_TOKEN=<token> k6/baseline.js

# 3. Run Spike Test (50 -> 500 RPS)
k6 run -e JWT_TOKEN=<token> k6/spike.js
```

### Writing New Tests
Since unit tests are missing, recommended first steps:
1.  Add `src/test/java/.../GatewayApplicationTests.java` with `@SpringBootTest`.
2.  Use `WebTestClient` to mock requests against the gateway.

---

## 7. COMMON DEVELOPMENT SCENARIOS

### Adding a New Feature: "New API Endpoint"
You rarely add "endpoints" to the gateway code itself. You add **Routes**.
1.  Open `src/main/resources/application.yml`.
2.  Find `gateway.routes`.
3.  Add entry:
    ```yaml
    - id: new-service
      path-prefix: /api/new
      target-url: http://new-service:8082
    ```
4.  Restart Gateway.

### Debugging Issues
-   **Logs**: `docker compose logs -f gateway`. Look for `ADAPTIVE:` logs to see rate limit adjustments.
-   **Metrics**: Open `http://localhost:3000` (Grafana) to see if errors are spiking downstream.

### Modifying Existing Features
-   **Changing Rate Limit Logic**: Modify `src/main/resources/scripts/token_bucket.lua`. This is the atomic counter logic.
-   **Adding a Rate Limit Tier**:
    1.  Update `GatewayProperties.java` (optional, map handles dynamic keys).
    2.  Update `application.yml` under `gateway.rate-limit.tiers`.

---

## 8. DEPENDENCIES & THIRD-PARTY INTEGRATIONS

-   **Spring Boot Web**: The core framework.
-   **Spring Data Redis**: Connectivity to Redis.
-   **JJWT (io.jsonwebtoken)**:
    -   **Purpose**: Parsing and validating JWTs.
    -   **Critical**: Security depends on correct key usage here.
-   **Micrometer + Prometheus**:
    -   **Purpose**: Exporting metrics.
    -   **Removal Impact**: Grafana dashboards will go blank; auto-scaling/monitoring breaks.

---

## 9. SECURITY & BEST PRACTICES

-   **Auth**: **JWT (JSON Web Token)**. Stateless.
    -   Validated at the Edge (`JwtAuthFilter`).
    -   **Security Risk**: Ensure `JWT_SECRET` is strong and not committed to git (currently hardcoded default in `application.yml`).
-   **Rate Limiting**: Acts as DoS protection.
-   **Input Validation**: Gateway does minimal validation; it mostly passes through. **Risk**: Use a WAF in front of Blackbox for SQLi/XSS protection.

---

## 10. PERFORMANCE CONSIDERATIONS

-   **Bottleneck**: **Redis Latency**. Every request hits Redis. If Redis is slow, the Gateway is slow.
-   **Optimization**: Lua scripts reduce network round-trips to Redis (1 trip per request).
-   **Async**: `WebClient` is used for downstream to avoid thread blocking, but `Servlet` stack (Tomcat) is still used for incoming. **Future Optimization**: Migrate to Spring WebFlux (Netty) for fully reactive stack.

---

## 11. ERROR HANDLING & RECOVERY

-   **Global Handler**: `GatewayErrorHandler.java` catches exceptions and formats standardized JSON responses.
-   **Circuit Breaker**: If a backend fails repeatedly, the circuit opens, returning `503 Service Unavailable` immediately without waiting for timeouts.
-   **Recovery**: `AdaptiveRateLimitController` automatically checks for health and "cools down" before restoring full traffic.

---

## 12. DEPLOYMENT & DEVOPS

-   **Docker**: Encapsulates the runtime.
-   **Deployment**: Simply run the docker container.
-   **Environment**: Pass config via `SPRING_APPLICATION_JSON` or individual env vars (e.g., `REDIS_HOST`).
-   **Monitoring**: Critical. Do not deploy without Prometheus/Grafana connected.

---

## 13. REAL-WORLD PROBLEM-SOLVING GUIDE

### "How do I..."
-   **Add a completely new auth mechanism?**
    -   Create `OAuth2Filter.java`.
    -   Implement `OncePerRequestFilter`.
    -   Register it in `WebConfig.java` *before* `RateLimitFilter`.

-   **Debug "Why am I being rate limited?"**
    -   Check headers `X-RateLimit-Remaining` and `X-RateLimit-Source`.
    -   If `Source` is `redis`, check your Tier limits.
    -   If `Source` is `adaptive`, check the Gateway logs for `ADAPTIVE: TIGHTENED` messages – the system is protecting itself!

---

## 14. CRITICAL "IF REMOVED" ANALYSIS

| Component | What Breaks if Removed? | Severity |
|-----------|-------------------------|----------|
| **Redis** | **State Loss**. Rate limiting falls back to local memory (per instance), causing inconsistency in a cluster. Audits fail. | **High** |
| **PostgreSQL** | **Audit Loss**. `AuditLogRepository` throws exceptions. Gateway *might* continue (check try-catch in controller) but history is lost. | **Medium** |
| **JwtAuthFilter** | **Security Breach**. All endpoints become public. Anyone can call any downstream service. | **Critical** |
| **AdaptiveRateLimitController**| **Resilience Loss**. Gateway becomes "dumb". Will keep hammering a dead service until it crashes completely. | **High** |

---

## 15. FOCUS AREAS & IMPROVEMENTS

1.  **Technical Debt**: **Missing Unit Tests**. The logic in `AdaptiveRateLimitController` is complex and crucial; it needs distinct unit tests verifying state transitions.
2.  **Quick Win**: Extract `JWT_SECRET` to a Kubernetes Secret or HashiCorp Vault instead of env var.
3.  **Refactoring**: The `RequestRouter` mixes routing logic with metric recording. Use the Decorator pattern or AOP for metrics to clean up the business logic.
