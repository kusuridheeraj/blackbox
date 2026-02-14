# 🎓 New Developer Onboarding Guide

Welcome to the BLACKBOX team! This guide helps you go from "clueless" to "committing code" in ~3 hours.

---

## 🏁 Day 1 Checklist: The First 10 Tasks

Complete these tasks in order to understand the system mechanics.

### 1. The "Hello World" Run
- [ ] Clone the repo.
- [ ] Run `docker compose up --build`.
- [ ] Hit `curl localhost:8080/actuator/health` and get `{"status":"UP"}`.
- [ ] **Goal**: Verify your machine can run the stack.

### 2. The Token Master
- [ ] Generate a `STANDARD` tier token using `scripts/get_token.sh` (or curl).
- [ ] Decoding it at [jwt.io](https://jwt.io) (Note: don't paste real prod tokens there!).
- [ ] Verify you see `tier: STANDARD` in the payload.
- [ ] **Goal**: Understand how we recognize users without a database lookup.

### 3. Break the Limit
- [ ] Use `k6` to run `k6/baseline.js`.
- [ ] Watch the logs for `RateLimitFilter`.
- [ ] Lower the limit in `application.yml` (STANDARD tier -> 5 rps).
- [ ] Restart and hit the limit manually. Check headers for `retry-after`.
- [ ] **Goal**: See the rate limiter in action.

### 4. findTheBug()
- [ ] Create a new class `src/test/RequestRouterTest.java`.
- [ ] Write a test that mocks `WebClient` throwing a 500 error.
- [ ] Verify that `metrics.recordDownstreamError()` is called.
- [ ] **Goal**: Understand the routing and instrumentation logic.

### 5. Add a "Backdoor" Route
- [ ] Add a new route in `application.yml` pointing to `https://httpbin.org/get`.
- [ ] Call it via the gateway: `localhost:8080/api/httpbin`.
- [ ] **Goal**: Learn how the config-driven routing works.

### 6. The "Chaos" Experiment
- [ ] While running a load test, kill Redis: `docker stop blackbox-redis`.
- [ ] Observe logs. Does the gateway crash? Or does it degrade?
- [ ] Check `X-RateLimit-Source` header. It should switch from `redis` to `local`.
- [ ] **Goal**: See "fail-open" or "fail-safe" behavior in real time.

### 7. Add a Metric
- [ ] In `RateLimitFilter.java`, add a new counter: `gateway.requests.blocked.total`.
- [ ] Trigger a block.
- [ ] Find your new metric in `http://localhost:8080/actuator/prometheus`.
- [ ] **Goal**: Understand the observability pipeline.

### 8. Database Archeology
- [ ] Connect to Postgres.
- [ ] Find the table `audit_log`.
- [ ] Identify the exact SQL query Spring Data JPA generates for `save()`.
- [ ] **Goal**: Understand our persistence layer (hint: check `AuditLogRepository`).

### 9. Documentation Gardener
- [ ] Find a typo or unclear sentence in `README.md`.
- [ ] Fix it and submit a PR (or commit locally).
- [ ] **Goal**: Get used to the contribution flow.

### 10. The Deep Dive
- [ ] Read `AdaptiveRateLimitController.java`.
- [ ] Explain to a rubber duck (or colleague) how the "RECOVERING" mode is triggered.
- [ ] **Goal**: Grok the core business logic.

---

## 📚 Recommended Reading
1.  **Spring Boot Docs**: specifically "Spring Boot Actuator" and "WebClient".
2.  **Redis Commands**: `INCR`, `EXPIRE`, `EVAL` (for Lua scripting).
3.  **Circuit Breaker Pattern**: Martin Fowler's blog post.

---

## 🆘 Need Help?
- Ask via Slack/Discord channel `#gateway-dev`.
- Check `TROUBLESHOOTING.md`.
