/**
 * BLACKBOX k6 Load Test — Abuse
 *
 * Single client sends 1000 RPS — simulating a malicious or misconfigured client.
 * Purpose: Verify per-client rate limiting stops abusive traffic while
 * allowing other clients through.
 *
 * Run:
 *   k6 run k6/abuse.js
 *
 * Expected outcome:
 *   - Abusive client gets heavily 429'd (>90% of requests throttled)
 *   - If LEGITIMATE_TOKEN is set, legitimate client stays unthrottled
 *   - Gateway stays healthy (no 5xx)
 *   - Per-client isolation works correctly
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const abuserThrottled = new Rate('abuser_throttled');
const legitimateErrors = new Rate('legitimate_errors');
const totalAbuse = new Counter('total_abuse_requests');
const totalLegitimate = new Counter('total_legitimate_requests');

export const options = {
    scenarios: {
        // Abusive client — hammering at 1000 RPS
        abuser: {
            executor: 'constant-arrival-rate',
            rate: 1000,
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: 200,
            maxVUs: 500,
            exec: 'abuserScenario',
        },
        // Legitimate client — normal 20 RPS
        legitimate: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: 10,
            maxVUs: 30,
            exec: 'legitimateScenario',
        },
    },
    thresholds: {
        'abuser_throttled': ['rate>0.80'],     // Abuser should be mostly blocked
        'legitimate_errors': ['rate<0.05'],     // Legit client should be fine
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const ABUSER_TOKEN = __ENV.ABUSER_TOKEN || '';
const LEGIT_TOKEN = __ENV.LEGITIMATE_TOKEN || '';

export function abuserScenario() {
    const res = http.get(`${BASE_URL}/api/test`, {
        headers: {
            'Authorization': `Bearer ${ABUSER_TOKEN}`,
            'Content-Type': 'application/json',
        },
        timeout: '5s',
    });

    totalAbuse.add(1);
    abuserThrottled.add(res.status === 429);

    check(res, {
        'abuser gets 429 or 200': (r) => r.status === 429 || r.status === 200,
    });
}

export function legitimateScenario() {
    const res = http.get(`${BASE_URL}/api/test`, {
        headers: {
            'Authorization': `Bearer ${LEGIT_TOKEN}`,
            'Content-Type': 'application/json',
        },
        timeout: '5s',
    });

    totalLegitimate.add(1);
    legitimateErrors.add(res.status !== 200);

    check(res, {
        'legitimate client gets 200': (r) => r.status === 200,
        'legitimate not throttled': (r) => r.status !== 429,
    });
}
