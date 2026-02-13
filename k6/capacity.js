/**
 * BLACKBOX k6 Load Test — Multi-Client Capacity Test
 *
 * This test uses MULTIPLE tokens (5 clients) to find the true
 * capacity of the gateway, bypassing per-client rate limits.
 *
 * Run:
 *   k6 run k6/capacity.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const successRate = new Rate('success');

// Generate 5 different client tokens
const TOKENS = [
    __ENV.JWT_TOKEN_1 || '',
    __ENV.JWT_TOKEN_2 || '',
    __ENV.JWT_TOKEN_3 || '',
    __ENV.JWT_TOKEN_4 || '',
    __ENV.JWT_TOKEN_5 || ''
];

export const options = {
    scenarios: {
        capacity_test: {
            executor: 'ramping-arrival-rate',
            startRate: 50,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 500,
            stages: [
                { duration: '30s', target: 100 },   // Ramp to 100 req/s
                { duration: '30s', target: 300 },   // Ramp to 300 req/s
                { duration: '30s', target: 500 },   // Ramp to 500 req/s
                { duration: '30s', target: 1000 },  // Try 1000 req/s
                { duration: '30s', target: 100 },   // Drop back down
            ],
        },
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
    // Rotate through 5 different client tokens
    const token = TOKENS[Math.floor(Math.random() * TOKENS.length)];

    const params = {
        headers: {
            'Authorization': `Bearer ${token}`,
        },
        timeout: '10s',
    };

    const res = http.get(`${BASE_URL}/api/test`, params);

    errorRate.add(res.status >= 500);
    throttleRate.add(res.status === 429);
    successRate.add(res.status === 200);
}

export function handleSummary(data) {
    const total = data.metrics.http_reqs?.values?.count || 0;
    const successCount = data.metrics.success?.values?.count || 0;
    const throttleCount = data.metrics.throttled?.values?.count || 0;
    const errorCount = data.metrics.errors?.values?.count || 0;

    console.log('\n=== BLACKBOX Capacity Test Results ===');
    console.log(`Total Requests: ${total}`);
    console.log(`Success (200): ${successCount} (${((successCount / total) * 100).toFixed(1)}%)`);
    console.log(`Throttled (429): ${throttleCount} (${((throttleCount / total) * 100).toFixed(1)}%)`);
    console.log(`Errors (5xx): ${errorCount} (${((errorCount / total) * 100).toFixed(1)}%)`);
    console.log(`Avg Rate: ${data.metrics.http_reqs?.values?.rate?.toFixed(0) || 0} req/s`);
    console.log(`p95 Latency: ${data.metrics.http_req_duration?.values['p(95)']?.toFixed(0) || 0}ms`);
    console.log('=======================================\n');

    return { 'stdout': '' };
}
