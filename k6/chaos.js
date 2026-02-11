/**
 * BLACKBOX k6 Load Test — Chaos (Mixed Scenario)
 *
 * Combined test: normal traffic + Redis outage simulation.
 * Purpose: Test graceful degradation when Redis goes down mid-traffic.
 *
 * Run:
 *   # Terminal 1: Start traffic
 *   k6 run k6/chaos.js
 *
 *   # Terminal 2: Kill Redis mid-test (at ~1 minute mark)
 *   docker compose stop redis
 *
 *   # Terminal 3: Bring Redis back (at ~3 minute mark)
 *   docker compose start redis
 *
 * Expected outcome:
 *   Phase 1 (0-1min): Normal operation via Redis
 *   Phase 2 (1-3min): Gateway falls back to local rate limiter
 *     - X-RateLimit-Source header changes to "local-fallback"
 *     - Limits become more conservative (50% of normal)
 *     - gateway_fallback_total metric increases
 *   Phase 3 (3-5min): Redis recovers, gateway returns to Redis-backed limiting
 *     - X-RateLimit-Source header returns to "redis"
 *     - Normal limits restored
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const fallbackRequests = new Counter('fallback_requests');
const redisRequests = new Counter('redis_requests');

export const options = {
    scenarios: {
        steady_with_chaos: {
            executor: 'constant-arrival-rate',
            rate: 80,            // Moderate traffic
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 40,
            maxVUs: 100,
        },
    },
    thresholds: {
        'errors': ['rate<0.10'],          // Accept some errors during Redis outage
        'http_req_duration': ['p(95)<3000'], // More relaxed during chaos
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.JWT_TOKEN || '';

export default function () {
    const res = http.get(`${BASE_URL}/api/test`, {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        timeout: '10s',
    });

    errorRate.add(res.status >= 500);
    throttleRate.add(res.status === 429);

    // Track which rate limiter is being used
    const source = res.headers['X-Ratelimit-Source'];
    if (source === 'local-fallback') {
        fallbackRequests.add(1);
    } else if (source === 'redis') {
        redisRequests.add(1);
    }

    check(res, {
        'response received': (r) => r.status !== 0,
        'not a gateway error': (r) => r.status !== 502,
    });

    // Log source transitions
    if (Math.random() < 0.02) {
        console.log(`[${new Date().toISOString()}] status=${res.status}, source=${source}`);
    }
}
