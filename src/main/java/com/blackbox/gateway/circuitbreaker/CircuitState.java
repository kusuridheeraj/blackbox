package com.blackbox.gateway.circuitbreaker;

/**
 * Circuit breaker states.
 *
 * CLOSED: Normal operation. Requests pass through. Failures counted.
 * OPEN: Backend is unhealthy. Requests rejected immediately (503).
 * HALF_OPEN: Testing recovery. Limited requests pass through.
 * Success → CLOSED. Failure → OPEN.
 */
public enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
