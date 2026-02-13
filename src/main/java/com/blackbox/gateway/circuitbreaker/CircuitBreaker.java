package com.blackbox.gateway.circuitbreaker;

import com.blackbox.gateway.metrics.GatewayMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-Route Circuit Breaker.
 *
 * Design decision: One circuit breaker per downstream route, not global.
 * If /api/payments backend is dead but /api/users is healthy, we should
 * NOT block user requests just because payments is failing.
 *
 * State machine:
 * CLOSED ──(5 consecutive failures)──► OPEN
 * OPEN ──(30s cooldown)─────────────► HALF_OPEN
 * HALF_OPEN ──(3 successes)───────────► CLOSED
 * HALF_OPEN ──(any failure)───────────► OPEN
 *
 * Configuration is intentionally NOT externalized to DB/Redis for Day 2.
 * These thresholds will be tunable via config in later iterations.
 */
@Slf4j
@Component
public class CircuitBreaker {

    private static final int FAILURE_THRESHOLD = 5; // Failures before opening
    private static final int SUCCESS_THRESHOLD = 3; // Successes in half-open before closing
    private static final long OPEN_DURATION_MS = 30_000; // 30 seconds in OPEN state

    private final GatewayMetrics metrics;

    // Per-route state
    private final ConcurrentHashMap<String, AtomicReference<CircuitState>> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> openTimestamps = new ConcurrentHashMap<>();

    public CircuitBreaker(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Register a gauge for a new route (call this when route is first discovered).
     */
    public void registerRouteGauge(String routeId) {
        metrics.registerCircuitBreakerGauge(routeId, route -> {
            CircuitState state = getState(route);
            return switch (state) {
                case CLOSED -> 0.0;
                case OPEN -> 1.0;
                case HALF_OPEN -> 2.0;
            };
        });
    }

    /**
     * Check if a request to this route should be allowed.
     *
     * @return true if request can proceed, false if circuit is open
     */
    public boolean allowRequest(String routeId) {
        CircuitState state = getState(routeId);

        switch (state) {
            case CLOSED:
                return true;

            case OPEN:
                // Check if cooldown has elapsed
                long openedAt = openTimestamps.getOrDefault(routeId, 0L);
                if (System.currentTimeMillis() - openedAt >= OPEN_DURATION_MS) {
                    transitionTo(routeId, CircuitState.HALF_OPEN);
                    return true; // Allow probe request
                }
                return false; // Still in cooldown

            case HALF_OPEN:
                return true; // Allow probe requests

            default:
                return true;
        }
    }

    /**
     * Record a successful response from the downstream service.
     */
    public void recordSuccess(String routeId) {
        CircuitState state = getState(routeId);

        if (state == CircuitState.HALF_OPEN) {
            int successes = getSuccessCount(routeId).incrementAndGet();
            if (successes >= SUCCESS_THRESHOLD) {
                transitionTo(routeId, CircuitState.CLOSED);
                log.info("Circuit CLOSED for route: {} after {} successful probes", routeId, successes);
            }
        }

        // Reset failure count on any success in CLOSED state
        if (state == CircuitState.CLOSED) {
            getFailureCount(routeId).set(0);
        }
    }

    /**
     * Record a failed response from the downstream service.
     */
    public void recordFailure(String routeId) {
        CircuitState state = getState(routeId);

        if (state == CircuitState.HALF_OPEN) {
            // Any failure in half-open → back to OPEN
            transitionTo(routeId, CircuitState.OPEN);
            log.warn("Circuit re-OPENED for route: {} after failure in HALF_OPEN", routeId);
            return;
        }

        if (state == CircuitState.CLOSED) {
            int failures = getFailureCount(routeId).incrementAndGet();
            if (failures >= FAILURE_THRESHOLD) {
                transitionTo(routeId, CircuitState.OPEN);
                log.warn("Circuit OPENED for route: {} after {} consecutive failures", routeId, failures);
            }
        }
    }

    /**
     * Get current state for a route.
     */
    public CircuitState getState(String routeId) {
        return states.computeIfAbsent(routeId, k -> new AtomicReference<>(CircuitState.CLOSED)).get();
    }

    private void transitionTo(String routeId, CircuitState newState) {
        states.computeIfAbsent(routeId, k -> new AtomicReference<>(CircuitState.CLOSED)).set(newState);

        if (newState == CircuitState.OPEN) {
            openTimestamps.put(routeId, System.currentTimeMillis());
            getSuccessCount(routeId).set(0);
        }

        if (newState == CircuitState.CLOSED) {
            getFailureCount(routeId).set(0);
            getSuccessCount(routeId).set(0);
        }

        if (newState == CircuitState.HALF_OPEN) {
            getSuccessCount(routeId).set(0);
        }
    }

    private AtomicInteger getFailureCount(String routeId) {
        return failureCounts.computeIfAbsent(routeId, k -> new AtomicInteger(0));
    }

    private AtomicInteger getSuccessCount(String routeId) {
        return successCounts.computeIfAbsent(routeId, k -> new AtomicInteger(0));
    }
}
