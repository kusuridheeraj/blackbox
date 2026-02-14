# 🔧 BLACKBOX Troubleshooting Guide

Fix common errors and issues when running or developing the BLACKBOX Gateway.

## 🚨 Top 5 Issues

| Symptom | Likely Cause | Quick Fix |
|---------|--------------|-----------|
| **HTTP 500 / Connection Refused** | Docker services not ready | Wait 30s or restart: `docker compose restart` |
| **HTTP 401 Unauthorized** | Missing/Exired Token | Generate a new token with valid `JWT_SECRET` |
| **HTTP 429 Too Many Requests** | Rate limit hit | Wait for refill or use a higher tier token |
| **Grafana/Prometheus Empty** | Metrics not scraping | Ensure containers share the same network |
| **"Circuit Breaker Open"** | Backend is down | Check logs for `mock-backend` container |

---

## 🔍 Detailed Diagnosis

### 1. Gateway Won't Start (`Connection refused`)

**Symptoms:**
- `docker compose up` exits with errors.
- `curl localhost:8080` fails.

**Solutions:**
1.  **Port Conflict**: Check if port 8080 is used.
    ```bash
    lsof -i :8080  # Mac/Linux
    netstat -ano | findstr :8080 # Windows
    ```
    *Fix*: Change `SERVER_PORT` in `.env` or `docker-compose.yml`.

2.  **Database Connection Failed**:
    Check logs: `docker compose logs gateway | grep "Connection to localhost:5432 refused"`
    *Fix*: Ensure `postgres` service is healthy. Wait for it:
    ```bash
    docker compose logs -f postgres
    ```

### 2. Authentication Failures (401)

**Symptoms:**
- Requests return `401 Unauthorized` with message "Invalid Token".

**Solutions:**
1.  **Wrong Secret**: The token was signed with a different secret than `gateway.jwt.secret`.
    *Fix*: Ensure `JWT_SECRET` in `docker-compose.yml` matches the one used to generate the token.
2.  **Expired Token**: Tokens last 1 hour by default.
    *Fix*: Generate a fresh token.

### 3. Rate Limiting Issues (429)

**Symptoms:**
- Legitimate requests are blocked.
- `X-RateLimit-Remaining` is continually 0.

**Solutions:**
1.  **Check Redis**: The gateway relies on Redis for counters. If Redis is down, it falls back to local limits (which might be stricter or desynchronized).
    *Check*: `docker compose ps redis`
2.  **Adaptive Mode Active?**: Check logs for `ADAPTIVE: TIGHTENED`.
    *Fix*: If the backend is healthy but gateway thinks otherwise, check if `mock-backend` is throwing random errors (controlled by `ERROR_RATE` env var).

### 4. Backend Connectivity (503)

**Symptoms:**
- "Service Unavailable" or "Circuit Breaker is OPEN".

**Solutions:**
1.  **DNS Issues**: Gateway can't resolve `mock-backend`.
    *Check*: `docker exec -it blackbox-gateway ping mock-backend`
2.  **Circuit Breaker Stuck**:
    *Fix*: Restart the gateway to reset state, or fix the downstream service. The circuit should self-heal after the backend recovers.

---

## 🛠 Debugging Tools

### View Logs
```bash
# Gateway application logs
docker compose logs -f gateway

# Database logs
docker compose logs -f postgres

# Redis monitor (real-time commands)
docker exec -it blackbox-redis redis-cli monitor
```

### Reset State
If things get weird (e.g., Redis keys stuck):
```bash
# Flush all Redis keys
docker exec -it blackbox-redis redis-cli FLUSHALL
```

### Database Access
Connect to the running Postgres:
```bash
docker exec -it blackbox-postgres psql -U blackbox -d blackbox
# SQL: select * from audit_log order by timestamp desc limit 10;
```
