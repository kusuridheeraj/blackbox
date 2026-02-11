/**
 * BLACKBOX k6 Load Test — Baseline
 *
 * Steady 100 RPS for 5 minutes.
 * Purpose: Establish normal behavior metrics as a comparison baseline.
 *
 * Run:
 *   k6 run k6/baseline.js
 *
 * Expected outcome:
 *   - 0% error rate
 *   - p99 latency < 200ms
 *   - No throttling
 *   - Circuit breaker stays CLOSED
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const latency = new Trend('request_latency', true);

// Test configuration
export const options = {
    scenarios: {
        steady_load: {
            executor: 'constant-arrival-rate',
            rate: 100,           // 100 requests per second
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],
        'errors': ['rate<0.01'],        // Less than 1% errors
        'throttled': ['rate<0.01'],     // Less than 1% throttled
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.JWT_TOKEN || '';

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        timeout: '10s',
    };

    const res = http.get(`${BASE_URL}/api/test`, params);

    // Track metrics
    latency.add(res.timings.duration);
    errorRate.add(res.status >= 500);
    throttleRate.add(res.status === 429);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'not throttled': (r) => r.status !== 429,
        'has rate limit header': (r) => r.headers['X-Ratelimit-Remaining'] !== undefined,
        'latency < 500ms': (r) => r.timings.duration < 500,
    });
}

export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6/results/baseline.json': JSON.stringify(data),
    };
}

function textSummary(data) {
    return `
=== BLACKBOX Baseline Test Results ===
Duration: ${data.state.testRunDurationMs}ms
Total Requests: ${data.metrics.http_reqs.values.count}
Error Rate: ${(data.metrics.errors?.values?.rate * 100 || 0).toFixed(2)}%
Throttle Rate: ${(data.metrics.throttled?.values?.rate * 100 || 0).toFixed(2)}%
p50 Latency: ${data.metrics.http_req_duration.values['p(50)'].toFixed(2)}ms
p95 Latency: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms
p99 Latency: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms
======================================
`;
}
