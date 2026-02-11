package com.blackbox.mockbackend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock downstream service.
 *
 * Configurable via environment variables:
 * - SIMULATED_DELAY_MS: artificial latency per request (default: 50ms)
 * - ERROR_RATE: probability of returning 500 (0.0 to 1.0, default: 0.0)
 *
 * Endpoints:
 * - GET /api/** → returns echo response with request details
 * - POST /api/** → returns echo response with request details
 * - GET /api/health → always returns 200 (ignores error rate)
 *
 * This service exists to make chaos testing realistic:
 * - Set ERROR_RATE=0.5 to simulate a flaky backend
 * - Set SIMULATED_DELAY_MS=5000 to simulate a slow backend
 * - docker compose stop mock-backend to simulate a dead backend
 */
@RestController
public class MockBackendController {

    @Value("${SIMULATED_DELAY_MS:50}")
    private long simulatedDelayMs;

    @Value("${ERROR_RATE:0.0}")
    private double errorRate;

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "mock-backend",
                "timestamp", Instant.now().toString()));
    }

    @RequestMapping(value = "/api/**", method = { RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH })
    public ResponseEntity<Map<String, Object>> handleRequest(
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> params) throws InterruptedException {

        // Simulate latency
        if (simulatedDelayMs > 0) {
            Thread.sleep(simulatedDelayMs);
        }

        // Simulate errors
        if (ThreadLocalRandom.current().nextDouble() < errorRate) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", 500,
                            "error", "Simulated backend failure",
                            "timestamp", Instant.now().toString()));
        }

        // Normal response
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Success from mock-backend",
                "timestamp", Instant.now().toString(),
                "delay_ms", simulatedDelayMs,
                "params", params));
    }
}
