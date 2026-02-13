# BLACKBOX — Quick Start Guide

> **Your gateway is running!** Follow these steps to test and monitor it.

---

## ✅ Step 1: Run the Test Script (1 minute)

This validates everything works:

```powershell
.\test-gateway.ps1
```

**What it tests:**
- ✓ Health check
- ✓ JWT token generation  
- ✓ Authenticated API calls
- ✓ Rate limiting
- ✓ Unauthorized request rejection

---

## 📊 Step 2: Open Grafana Dashboard (3 minutes)

**Grafana** shows real-time metrics with beautiful graphs.

1. **Open browser**: http://localhost:3000
2. **Login**: `admin` / `admin` (skip password change if prompted)
3. **Navigate**: Dashboards → BLACKBOX Gateway Dashboard

**What you'll see:**
- Request rate (requests/second)
- Latency percentiles (p50, p95, p99)
- Rate limit throttle rate (429s)
- Circuit breaker state
- Error rates

**Try this:** Run the test script again while watching Grafana — you'll see the graphs spike!

---

## 🔍 Step 3: Test Rate Limiting (2 minutes)

Generate a token and flood the gateway to trigger rate limits:

```powershell
# 1. Get a token
$TOKEN = (curl.exe -s "http://localhost:8080/test/token?clientId=client-1&tier=STANDARD" | ConvertFrom-Json).token

# 2. Send 100 rapid requests
for ($i=0; $i -lt 100; $i++) { 
    $code = curl.exe -s -o $null -w "%{http_code} " http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"
    Write-Host $code -NoNewline
}
```

**Expected:**
- First ~75 requests: `200 200 200...` (burst capacity)
- Then: `429 429 429...` (rate limited!)

**In Grafana:** You'll see the "Rate Limit Throttle Rate" panel spike.

---

## 📈 Step 4: Query Prometheus (2 minutes)

**Prometheus** is the raw metrics database. Grafana pulls data from here.

1. **Open browser**: http://localhost:9090
2. **Try these queries** (paste in the query box, hit Execute):

### Request rate by status code
```promql
rate(gateway_request_total[1m])
```

### 95th percentile latency
```promql
histogram_quantile(0.95, rate(gateway_request_duration_seconds_bucket[5m]))
```

### Rate limit rejections
```promql
rate(gateway_rate_limit_throttled_total[1m])
```

### Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
```promql
gateway_circuit_breaker_state
```

---

## 🔥 Step 5: Test Circuit Breaker (Advanced, 5 minutes)

Make the backend fail to trigger the circuit breaker:

```powershell
# 1. Set mock backend to return errors
docker exec blackbox-mock-backend sh -c "export ERROR_RATE=1.0"

# 2. Send requests until circuit opens (need ~5 failures)
$TOKEN = (curl -s "http://localhost:8080/test/token?clientId=client-1" | ConvertFrom-Json).token
for ($i=0; $i -lt 10; $i++) { 
    curl -s http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"
    Start-Sleep -Milliseconds 500
}
```

**Expected behavior:**
- First 5 requests: `502 Bad Gateway` (backend errors)
- After 5 failures: `503 Service Unavailable` (circuit OPEN)
- Response body: `"Circuit breaker is OPEN"`

**In Prometheus:** Query `gateway_circuit_breaker_state` — it should be `1` (OPEN)

---

## 📺 Step 6: Watch Live Logs (Continuous)

Monitor what's happening in real-time:

```powershell
# Gateway logs
docker logs -f blackbox-gateway

# Mock backend logs
docker logs -f blackbox-mock-backend
```

Press `Ctrl+C` to stop.

---

## 🧪 Step 7: Test Per-Client Isolation (3 minutes)

Prove that different clients have independent rate limits:

```powershell
# Generate two different tokens
$TOKEN1 = (curl -s "http://localhost:8080/test/token?clientId=client-1&tier=STANDARD" | ConvertFrom-Json).token
$TOKEN2 = (curl -s "http://localhost:8080/test/token?clientId=client-2&tier=PREMIUM" | ConvertFrom-Json).token

# Exhaust client-1's tokens
for ($i=0; $i -lt 100; $i++) { 
    curl -s -o $null http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN1"
}

# client-1 should be throttled
curl -v http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN1"
# Expected: 429 Too Many Requests

# But client-2 should still work
curl -v http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN2"
# Expected: 200 OK (independent limits!)
```

---

## 🎯 Step 8: Check All Containers

Verify everything is running:

```powershell
docker compose ps
```

**Should show:**
```
blackbox-gateway        Up (healthy)
blackbox-mock-backend   Up
blackbox-redis          Up (healthy)
blackbox-postgres       Up (healthy)
blackbox-prometheus     Up
blackbox-grafana        Up
```

If any are unhealthy:
```powershell
docker logs blackbox-<service-name>
```

---

## 🛑 Stop Everything

When you're done testing:

```powershell
docker compose down
```

To also wipe data (fresh start next time):

```powershell
docker compose down -v
```

---

## 📚 Next Steps

- **Full testing guide**: See [TESTING_GUIDE.md](docs/TESTING_GUIDE.md) for all 11 test scenarios
- **Architecture deep dive**: Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **Blog posts**: 
  - [Why Static Rate Limiting Fails](docs/blog-1-static-rate-limiting.md)
  - [Breaking My Own API Gateway](docs/blog-2-breaking-my-gateway.md)

---

**Questions?** Check the logs, Grafana dashboards, or re-run `.\test-gateway.ps1` to validate the system is healthy.
