# 🏗️ Architecture Design Records (ADR)

This document records the major architectural decisions for the BLACKBOX Gateway, providing context for *why* the system is built this way.

---

## ADR 001: Rate Limiting Algorithm

**Status**: Accepted  
**Context**: We needed a mechanism to throttle requests to prevent downstream overload.  
**Options**:
1.  **Token Bucket**: Allows burstiness, refills at constant rate.
2.  **Leaky Bucket**: Smooths out traffic, no bursts.
3.  **Fixed Window**: Simple, but suffers from "window boundary" spikes (2x load at turnover).

**Decision**: Use **Token Bucket with Redis**.  
**Rationale**: Real-world traffic is bursty. Users don't click at a perfect metronome pace. Token Bucket allows brief spikes (burst capacity) while enforcing a long-term rate, providing the best UX. Shared Redis state enables a cluster of gateways to enforce a single global limit.

---

## ADR 002: Authentication Strategy

**Status**: Accepted  
**Context**: How to authenticate clients and pass context downstream.  
**Options**:
1.  **OAuth2 / OIDC**: Industry standard, but complex to set up for a standalone gateway demo.
2.  **API Keys**: Simple, but hard to encode metadata (like "Tier") without a DB lookup on every request.
3.  **JWT (Stateless)**: Self-contained, signed, carries metadata.

**Decision**: Use **JWT (stateless)** signed with HMAC-SHA256 (`HS256`).  
**Rationale**: JWTs allow us to encode the `ClientTier (STANDARD, PREMIUM)` directly in the token. This avoidance of a database lookup on every request is critical for latency essential in a Gateway. Validation is purely CPU-bound (signature check).

---

## ADR 003: Adaptive "Self-Healing" Logic

**Status**: Accepted  
**Context**: Static rate limits fail when the backend degrades (e.g., DB slow). We needed dynamic adjustments.  
**Options**:
1.  **Machine Learning**: Train a model on "normal" traffic. Overkill, hard to debug, harder to explain.
2.  **PID Controller**: Control theory. Very precise, but hard to tune (oscillations).
3.  **Heuristic State Machine**: Simple rules (Error > 50% → Cut limit by half).

**Decision**: Use **Heuristic State Machine** (Normal -> Cautious -> Tightened).  
**Rationale**: "Explainable AI" is better than a black box. If limits drop, an operator asks "Why?". Providing a log "Because error rate > 50%" is immediate and actionable. ML models drift; PID loops oscillate. Simple if-then rules are robust in chaos.

---

## ADR 004: Synchronous vs Asynchronous I/O

**Status**: Mixed (Pragmatic accepted)  
**Context**: Java options for handling requests.  
**Options**:
1.  **Servlet (Blocking)**: One thread per request. Easy to debug. `ThreadLocal` works. Limits concurrency to thread pool size (~200).
2.  **WebFlux (Reactive)**: Non-blocking event loop. Massive concurrency. Harder to debug/trace.

**Decision**: Hybrid. **Servlet (Spring MVC)** for incoming, **WebFlux (WebClient)** for outgoing.  
**Rationale**: We used Spring Boot Web (Servlet) because it is the "default" most Java developers know. However, for the *relay* (calling backend), we use `WebClient` to ensure we don't block the incoming servlet thread on network I/O more than necessary, and to leverage its robust timeout/retry operators.

---

## ADR 005: Database for Observability

**Status**: Accepted  
**Context**: We need to store audit logs of admin actions and adaptive triggers.  
**Options**:
1.  **NoSQL (Mongo)**: Good for JSON logs.
2.  **SQL (Postgres)**: Relational, rigid, reliable.
3.  **Files**: Hard to query.

**Decision**: **PostgreSQL**.  
**Rationale**: Keep the stack boring. Postgres is reliable. While logs are JSON-like, we might want to join them with future tables (e.g., `Users`, `Billing`). The relational model offers future-proofing for "Enterprise" features.

---

## ADR 006: Circuit Breaker Implementation

**Status**: Custom Implementation  
**Context**: Need to stop calling a dead backend.  
**Options**:
1.  **Resilience4j**: The industry standard library.
2.  **Custom Code**: Write our own state machine.

**Decision**: **Custom Implementation** (Simple Interface).  
**Rationale**: Since this is an *educational* codebase, writing a Circuit Breaker from scratch demonstrates essentially how it works (Open/Closed/Half-Open states). For a pure production app, we would swap this for Resilience4j. But for learning? Custom is better.
