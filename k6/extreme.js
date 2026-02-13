/**
 * BLACKBOX k6 Load Test — EXTREME (Push to Limits)
 *
 * Uses 10 different clients to push gateway to absolute max.
 * Ramps: 100 → 500 → 1000 → 1500 → 2000 req/s
 *
 * Run:
 *   k6 run k6/extreme.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const successRate = new Rate('success');
const connectionErrors = new Counter('connection_errors');

// 10 different client tokens to maximize throughput
const TOKENS = [
    __ENV.JWT_TOKEN_1 || '', __ENV.JWT_TOKEN_2 || '',
    __ENV.JWT_TOKEN_3 || '', __ENV.JWT_TOKEN_4 || '',
    __ENV.JWT_TOKEN_5 || '', __ENV.JWT_TOKEN_6 || '',
    __ENV.JWT_TOKEN_7 || '', __ENV.JWT_TOKEN_8 || '',
    __ENV.JWT_TOKEN_9 || '', __ENV.JWT_TOKEN_10 || ''
];

export const options = {
    scenarios: {
        extreme_load: {
            executor: 'ramping-arrival-rate',
            startRate: 100,
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: 1000,
            stages: [
                { duration: '30s', target: 100 },   // Warm up
                { duration: '30s', target: 500 },   // Ramp to 500
                { duration: '30s', target: 1000 },  // Ramp to 1000
                { duration: '30s', target: 1500 },  // Ramp to 1500
                { duration: '30s', target: 2000 },  // EXTREME: 2000 req/s
                { duration: '30s', target: 1000 },  // Cool down
                { duration: '30s', target: 100 },   // Recovery
            ],
        },
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
    // Rotate through 10 different client tokens
    const token = TOKENS[Math.floor(Math.random() * TOKENS.length)];

    const params = {
        headers: {
            'Authorization': `Bearer ${token}`,
        },
        timeout: '10s',
    };

    try {
        const res = http.get(`${BASE_URL}/api/test`, params);

        if (res.status === 0) {
            connectionErrors.add(1);
        }

        errorRate.add(res.status >= 500);
        throttleRate.add(res.status === 429);
        successRate.add(res.status === 200);

        check(res, {
            'success or throttled': (r) => r.status === 200 || r.status === 429,
        });
    } catch (e) {
        connectionErrors.add(1);
    }
}

export function handleSummary(data) {
    const total = data.metrics.http_reqs?.values?.count || 0;
    const duration = data.state.testRunDurationMs / 1000;
    const avgRate = total / duration;

    console.log('\n╔════════════════════════════════════════╗');
    console.log('║  BLACKBOX EXTREME LOAD TEST RESULTS    ║');
    console.log('╚════════════════════════════════════════╝\n');
    console.log(`📊 Total Requests: ${total.toLocaleString()}`);
    console.log(`⚡ Average Rate: ${avgRate.toFixed(0)} req/s`);
    console.log(`⏱️  Duration: ${duration.toFixed(0)}s\n`);

    const successPct = ((data.metrics.success?.values?.rate || 0) * 100).toFixed(1);
    const throttlePct = ((data.metrics.throttled?.values?.rate || 0) * 100).toFixed(1);
    const errorPct = ((data.metrics.errors?.values?.rate || 0) * 100).toFixed(1);
    const connErrors = data.metrics.connection_errors?.values?.count || 0;

    console.log(`✅ Success (200):    ${successPct}%`);
    console.log(`⚠️  Throttled (429): ${throttlePct}%`);
    console.log(`❌ Errors (5xx):     ${errorPct}%`);
    console.log(`🔌 Conn Errors:      ${connErrors}\n`);

    console.log(`📈 Latency:`);
    console.log(`   p50: ${data.metrics.http_req_duration?.values['p(50)']?.toFixed(0) || 0}ms`);
    console.log(`   p95: ${data.metrics.http_req_duration?.values['p(95)']?.toFixed(0) || 0}ms`);
    console.log(`   p99: ${data.metrics.http_req_duration?.values['p(99)']?.toFixed(0) || 0}ms\n`);

    console.log('═══════════════════════════════════════════\n');

    return { 'stdout': '' };
}
