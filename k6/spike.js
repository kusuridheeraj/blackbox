/**
 * BLACKBOX k6 Load Test — Spike
 *
 * Ramps from 50 → 500 RPS in 30 seconds, holds, then drops.
 * Purpose: Test adaptive rate limiting response to sudden traffic spikes.
 *
 * Run:
 *   k6 run k6/spike.js
 *
 * Expected outcome:
 *   - Adaptive controller enters CAUTIOUS then TIGHTENED mode
 *   - Rate limits tighten automatically
 *   - Some requests get 429 (expected)
 *   - System recovers after spike ends
 *   - No 5xx errors from gateway itself
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const throttleCount = new Counter('throttle_count');

export const options = {
    scenarios: {
        traffic_spike: {
            executor: 'ramping-arrival-rate',
            startRate: 50,
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: 500,
            stages: [
                { duration: '1m', target: 50 },    // Warm up at normal rate
                { duration: '30s', target: 500 },   // SPIKE: 10x increase in 30s
                { duration: '2m', target: 500 },    // Hold at peak
                { duration: '30s', target: 50 },    // Drop back down
                { duration: '2m', target: 50 },     // Recovery observation
            ],
        },
    },
    thresholds: {
        'errors': ['rate<0.05'],             // Some errors expected during spike
        'http_req_duration': ['p(95)<2000'],  // Relaxed latency threshold
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

    errorRate.add(res.status >= 500);
    throttleRate.add(res.status === 429);
    if (res.status === 429) {
        throttleCount.add(1);
    }

    check(res, {
        'not a server error': (r) => r.status < 500,
        'response received': (r) => r.status !== 0,
    });

    // Check rate limit headers to observe adaptive behavior
    const remaining = res.headers['X-Ratelimit-Remaining'];
    const source = res.headers['X-Ratelimit-Source'];
    if (remaining !== undefined) {
        // Log periodically for analysis
        if (Math.random() < 0.01) {
            console.log(`Rate limit: remaining=${remaining}, source=${source}, status=${res.status}`);
        }
    }
}
