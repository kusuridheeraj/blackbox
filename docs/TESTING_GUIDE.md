# BLACKBOX — Testing Guide

> Step-by-step guide to test every feature. Each step includes the exact command,
> what to expect, and 📸 markers for screenshots worth capturing.

---

## Prerequisites

Before testing, make sure you have:

| Tool | Check Command | Required |
|------|--------------|----------|
| Docker Desktop | `docker --version` | ✅ Must be running |
| Maven | `mvn --version` | ✅ For local builds |
| Java 17 | `java -version` | ✅ |
| curl | `curl --version` | ✅ Built into Windows |
| k6 | `k6 version` | Optional (for load tests) |

---

## Step 1: Build & Start the Full Stack

```bash
cd c:\PlayStation\assets\good-as-three\blackbox
docker compose up --build
```

**⏱ Wait time:** 2-5 minutes (first build downloads dependencies)

**What to expect:**
- Maven builds both gateway and mock-backend JARs
- 6 containers start: `blackbox-gateway`, `blackbox-mock-backend`, `blackbox-redis`, `blackbox-postgres`, `blackbox-prometheus`, `blackbox-grafana`
- Gateway logs: `Started BlackboxApplication in X.XXX seconds`
- Mock backend logs: `Started MockBackendApplication`

**Verify all containers are running (in a NEW terminal):**
```bash
docker compose ps
```

Expected output:
```
NAME                    STATUS
blackbox-gateway        Up (healthy)
blackbox-mock-backend   Up
blackbox-redis          Up (healthy)
blackbox-postgres       Up (healthy)
blackbox-prometheus     Up
blackbox-grafana        Up
```

> 📸 **Screenshot 1:** `docker compose ps` showing all 6 containers healthy

---

## Step 2: Generate a JWT Token

The gateway requires JWT authentication. Use the built-in test endpoint:

```bash
curl -X POST http://localhost:8080/test/token -H "Content-Type: application/json" -d "{\"clientId\":\"client-1\",\"tier\":\"STANDARD\",\"name\":\"Test Client\"}"
```

**What to expect:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "clientId": "client-1",
  "tier": "STANDARD",
  "usage": "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
}
```

**Save the token** — you'll use it in all following steps:
```bash
# On PowerShell:
$TOKEN = (curl -s -X POST http://localhost:8080/test/token -H "Content-Type: application/json" -d '{"clientId":"client-1","tier":"STANDARD","name":"Test Client"}' | ConvertFrom-Json).token
```

> 📸 **Screenshot 2:** Token generation response

---

## Step 3: Test Basic Request Forwarding

Send a request through the gateway to the mock backend:

```bash
curl -v http://localhost:8080/api/test -H "Authorization: Bearer %TOKEN%"
```

**What to expect:**
- **Status:** `200 OK`
- **Body:** Response from mock backend (JSON with timestamp, message)
- **Headers you should see:**
  - `X-RateLimit-Limit: 75` (STANDARD tier burst size)
  - `X-RateLimit-Remaining: 74` (one token consumed)
  - `X-RateLimit-Source: redis` (using Redis rate limiter)

> 📸 **Screenshot 3:** Successful request with rate limit headers visible

---

## Step 4: Test Authentication Rejection

### 4a. No token at all:
```bash
curl -v http://localhost:8080/api/test
```

**Expected:** `401 Unauthorized` with JSON error body

### 4b. Invalid token:
```bash
curl -v http://localhost:8080/api/test -H "Authorization: Bearer invalid-garbage-token"
```

**Expected:** `401 Unauthorized`

### 4c. Expired token:
```bash
curl -X POST http://localhost:8080/test/token/expired -H "Content-Type: application/json" -d "{\"clientId\":\"client-1\"}"
```
Use the returned expired token:
```bash
curl -v http://localhost:8080/api/test -H "Authorization: Bearer <expired-token>"
```

**Expected:** `401 Unauthorized`

> 📸 **Screenshot 4:** 401 rejection showing the error JSON

---

## Step 5: Test Rate Limiting (429 Too Many Requests)

Flood the gateway to exhaust your token bucket:

```bash
# Send 100 rapid requests (STANDARD tier allows 50/s, burst of 75)
for ($i=0; $i -lt 100; $i++) { curl -s -o $null -w "%{http_code} " http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN" }
```

**What to expect:**
- First ~75 requests: `200` (using burst capacity)
- Remaining requests: `429` (rate limit exceeded)
- 429 response includes:
  - `Retry-After` header (seconds until refill)
  - `X-RateLimit-Remaining: 0`

**Wait 2-3 seconds, then try again:**
```bash
curl -v http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"
```

**Expected:** `200 OK` (tokens have refilled)

> 📸 **Screenshot 5:** Mix of 200s and 429s showing rate limiting in action

---

## Step 6: Test Per-Client Isolation

Generate a second client token and verify they have independent limits:

```bash
# Generate PREMIUM tier client
curl -X POST http://localhost:8080/test/token -H "Content-Type: application/json" -d "{\"clientId\":\"client-2\",\"tier\":\"PREMIUM\",\"name\":\"Premium Client\"}"
```

**After exhausting client-1's tokens**, send a request as client-2:
```bash
curl -v http://localhost:8080/api/test -H "Authorization: Bearer <client-2-token>"
```

**Expected:** `200 OK` — client-2's rate limits are independent.
- `X-RateLimit-Limit: 300` (PREMIUM burst size, higher than STANDARD's 75)

> 📸 **Screenshot 6:** Client-2 getting 200 while client-1 would get 429

---

## Step 7: Test Circuit Breaker

Increase the mock backend's error rate to trigger the circuit breaker:

### 7a. Set mock backend to 100% errors:
```bash
docker compose exec mock-backend sh -c "export ERROR_RATE=1.0"
```

Or restart mock-backend with high error rate:
```bash
docker compose stop mock-backend
docker compose run -d --name blackbox-mock-backend -e ERROR_RATE=1.0 -e SERVER_PORT=8081 mock-backend
```

### 7b. Send requests until the circuit opens:
```bash
# Send 10 requests (circuit opens after 5 failures)
for ($i=0; $i -lt 10; $i++) { curl -s -w "Status: %{http_code}\n" http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN" }
```

**What to expect:**
- First few: `502 Bad Gateway` (backend errors forwarded)
- After 5 failures: `503 Service Unavailable` (circuit breaker OPEN)
- Response body: `"Circuit breaker is OPEN"`

### 7c. Wait 30 seconds (cooldown), then send 1 request:
```bash
# Wait 30 seconds for circuit to transition to HALF_OPEN
Start-Sleep -Seconds 30
curl -v http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"
```

**Expected:** The circuit tries HALF_OPEN, sends the request through.

> 📸 **Screenshot 7:** 503 response with "circuit breaker is OPEN" message

---

## Step 8: Monitor with Prometheus

### 8a. View raw metrics:
```bash
curl http://localhost:8080/actuator/prometheus | findstr gateway
```

**Key metrics to look for:**
```
gateway_request_total{route="mock-backend",status="200"} 42
gateway_request_total{route="mock-backend",status="429"} 15
gateway_rate_limit_throttled_total{route="mock-backend",tier="STANDARD"} 15
gateway_circuit_breaker_state{route="mock-backend"} 0
gateway_downstream_error_total{route="mock-backend"} 5
gateway_request_duration_seconds_bucket{route="mock-backend",...}
```

### 8b. Query in Prometheus UI:
Open **http://localhost:9090** in your browser.

Try these PromQL queries:
```
rate(gateway_request_total[1m])
histogram_quantile(0.95, rate(gateway_request_duration_seconds_bucket[5m]))
gateway_circuit_breaker_state
```

> 📸 **Screenshot 8:** Prometheus UI showing `gateway_request_total` graph

---

## Step 9: Monitor with Grafana Dashboard

1. Open **http://localhost:3000** in your browser
2. Login: **admin / admin** (skip password change)
3. Go to **Dashboards** → **BLACKBOX Gateway Dashboard**

**What you should see (7 panels):**

| Panel | What it Shows |
|-------|--------------|
| Request Rate | Requests per second by route and status |
| Request Latency | p50, p95, p99 latency lines |
| Rate Limit Throttle Rate | 429s per second (spikes during overload) |
| Circuit Breaker State | 0=CLOSED (green), 1=OPEN (red), 2=HALF_OPEN (yellow) |
| Downstream Error Rate | 5xx errors from backends |
| Adaptive Adjustments | Tighten/relax events over time |
| Fallback Rate | Redis fallback activations |

> 📸 **Screenshot 9:** Full Grafana dashboard with panels populated
> 📸 **Screenshot 10:** Zoomed view of any interesting panel

---

## Step 10: Chaos Test — Kill Redis Mid-Traffic

This is the most impressive test. It proves graceful degradation.

### 10a. Start sending continuous traffic (keep this running):
```bash
while ($true) { $resp = curl -s -D - http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"; $resp | Select-String "X-RateLimit-Source"; Start-Sleep -Milliseconds 200 }
```

**You should see:** `X-RateLimit-Source: redis`

### 10b. In ANOTHER terminal, kill Redis:
```bash
docker compose stop redis
```

### 10c. Watch the first terminal:

**What to expect:**
- Source changes from `redis` → `local-fallback`
- Rate limits become MORE conservative (50% of normal)
- **Zero 500 errors** — the gateway handles it gracefully
- Logs show: `"Redis rate limiter unavailable, using local fallback"`

> 📸 **Screenshot 11:** Terminal showing `X-RateLimit-Source: local-fallback`

### 10d. Bring Redis back:
```bash
docker compose start redis
```

**What to expect:**
- Source changes back from `local-fallback` → `redis`
- Normal rate limits restored automatically
- No restart needed

> 📸 **Screenshot 12:** Terminal showing recovery back to `X-RateLimit-Source: redis`

---

## Step 11: Load Testing with k6 (Optional)

Install k6 first: https://k6.io/docs/get-started/installation/

```bash
# Baseline test (100 RPS for 5 minutes)
$env:JWT_TOKEN=$TOKEN; k6 run k6/baseline.js

# Spike test (50 → 500 RPS)
$env:JWT_TOKEN=$TOKEN; k6 run k6/spike.js

# Abuse test (1 abusive client + 1 legitimate)
$env:ABUSER_TOKEN=$TOKEN; $env:LEGITIMATE_TOKEN=$TOKEN; k6 run k6/abuse.js
```

> 📸 **Screenshot 13:** k6 test results summary

---

## Screenshot Checklist

Use these for the README and blog posts:

| # | What to Capture | For |
|---|----------------|-----|
| 1 | `docker compose ps` — all containers healthy | README |
| 2 | Token generation response | README |
| 3 | Successful request with rate limit headers | README |
| 4 | 401 rejection (no/invalid token) | Blog |
| 5 | Rate limiting: mix of 200s and 429s | README + Blog |
| 6 | Per-client isolation proof | Blog |
| 7 | Circuit breaker 503 response | README + Blog |
| 8 | Prometheus metrics query | README |
| 9 | Full Grafana dashboard | README (hero image) |
| 10 | Grafana panel close-up | Blog |
| 11 | Redis fallback (`local-fallback` header) | README + Blog |
| 12 | Redis recovery (back to `redis` header) | Blog |
| 13 | k6 load test results | Blog |

---

## Cleanup

```bash
# Stop everything
docker compose down

# Remove volumes too (clean slate)
docker compose down -v
```

---

**After you capture the screenshots, share them and I'll build a polished README with proper infographics! 🚀**
