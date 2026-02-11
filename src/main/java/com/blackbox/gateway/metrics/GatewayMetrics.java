package com.blackbox.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized gateway metrics.
 *
 * Every metric has a clear reason for existing:
 * - request_total: understand traffic volume and status distribution
 * - request_duration: detect latency degradation before users complain
 * - throttle_total: track how aggressively we're protecting the system
 * - circuit_breaker_state: know when backends are unhealthy
 * - downstream_error_total: feed the adaptive rate limiter
 * - fallback_total: detect when Redis is flaky (gateway is in degraded mode)
 *
 * What we intentionally do NOT track:
 * - Per-request body size (noise, not signal)
 * - Individual client request logs (use audit logs instead)
 */
@Component
public class GatewayMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> throttleCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> downstreamErrorCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> requestTimers = new ConcurrentHashMap<>();

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Pre-register fallback counter
        Counter.builder("gateway_fallback_total")
                .description("Number of requests served by local fallback rate limiter")
                .register(registry);
    }

    /**
     * Record a completed request with its route, status, and duration.
     */
    public void recordRequest(String route, int statusCode, Duration duration) {
        String statusGroup = statusCode / 100 + "xx";

        Timer timer = requestTimers.computeIfAbsent(
                route + ":" + statusGroup,
                k -> Timer.builder("gateway_request_duration_seconds")
                        .description("Request latency")
                        .tag("route", route)
                        .tag("status", statusGroup)
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .register(registry));

        timer.record(duration);

        Counter.builder("gateway_request_total")
                .description("Total requests")
                .tag("route", route)
                .tag("status", statusGroup)
                .tag("status_code", String.valueOf(statusCode))
                .register(registry)
                .increment();
    }

    /**
     * Record a throttled (rate-limited) request.
     */
    public void recordThrottle(String route, String tier) {
        Counter counter = throttleCounters.computeIfAbsent(
                route + ":" + tier,
                k -> Counter.builder("gateway_rate_limit_throttled_total")
                        .description("Total throttled requests")
                        .tag("route", route)
                        .tag("tier", tier)
                        .register(registry));
        counter.increment();
    }

    /**
     * Record a downstream backend error (5xx).
     * This feeds the adaptive rate limiter.
     */
    public void recordDownstreamError(String route) {
        Counter counter = downstreamErrorCounters.computeIfAbsent(
                route,
                k -> Counter.builder("gateway_downstream_error_total")
                        .description("Total downstream 5xx errors")
                        .tag("route", route)
                        .register(registry));
        counter.increment();
    }

    /**
     * Record a fallback event (Redis unavailable).
     */
    public void recordFallback() {
        registry.counter("gateway_fallback_total").increment();
    }

    /**
     * Record circuit breaker state change.
     */
    public void recordCircuitBreakerState(String route, String state) {
        registry.gauge("gateway_circuit_breaker_state",
                io.micrometer.core.instrument.Tags.of("route", route),
                stateToNumber(state));
    }

    /**
     * Record an adaptive rate limit adjustment.
     */
    public void recordAdaptiveAdjustment(String direction) {
        Counter.builder("gateway_adaptive_adjustment_total")
                .description("Adaptive rate limit adjustments")
                .tag("direction", direction)
                .register(registry)
                .increment();
    }

    private double stateToNumber(String state) {
        return switch (state) {
            case "CLOSED" -> 0;
            case "OPEN" -> 1;
            case "HALF_OPEN" -> 2;
            default -> -1;
        };
    }
}
