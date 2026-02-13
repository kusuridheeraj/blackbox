/**
 * BLACKBOX k6 Load Test — Sustained 1000 req/s
 *
 * Hold steady at 1000 req/s for 2 minutes.
 * Uses 3 clients @ 500 req/s each = 1500 capacity
 *
 * Run:
 *   k6 run k6/sustained-1000.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const successRate = new Rate('success');

// 3 different tokens to support 1000 req/s (500 each)
const TOKENS = [
    __ENV.JWT_TOKEN_1 || '',
    __ENV.JWT_TOKEN_2 || '',
    __ENV.JWT_TOKEN_3 || ''
];

export const options = {
    scenarios: {
        sustained_1k: {
            executor: 'constant-arrival-rate',
            rate: 1000,          // Exactly 1000 req/s
            timeUnit: '1s',
            duration: '2m',      // Sustain for 2 minutes
            preAllocatedVUs: 100,
            maxVUs: 300,
        },
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
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

    check(res, {
        'success': (r) => r.status === 200,
    });
}

export function handleSummary(data) {
    const total = data.metrics.http_reqs?.values?.count || 0;
    const success = (data.metrics.success?.values?.rate || 0) * total;
    const throttled = (data.metrics.throttled?.values?.rate || 0) * total;
    const errors = (data.metrics.errors?.values?.rate || 0) * total;
    const avgRate = data.metrics.http_reqs?.values?.rate || 0;

    console.log('\n╔═══════════════════════════════════════════╗');
    console.log('║   SUSTAINED 1000 REQ/S TEST RESULTS       ║');
    console.log('╚═══════════════════════════════════════════╝\n');
    console.log(`🎯 Target: 1000 req/s for 2 minutes`);
    console.log(`⚡ Actual: ${avgRate.toFixed(0)} req/s average\n`);
    console.log(`📊 Total Requests: ${total.toLocaleString()}`);
    console.log(`✅ Success (200):  ${success.toFixed(0)} (${((success / total) * 100).toFixed(1)}%)`);
    console.log(`⚠️  Throttled (429): ${throttled.toFixed(0)} (${((throttled / total) * 100).toFixed(1)}%)`);
    console.log(`❌ Errors (5xx):   ${errors.toFixed(0)} (${((errors / total) * 100).toFixed(1)}%)\n`);
    console.log(`⏱️  Latency:`);
    console.log(`   p50: ${data.metrics.http_req_duration?.values['p(50)']?.toFixed(0) || 0}ms`);
    console.log(`   p95: ${data.metrics.http_req_duration?.values['p(95)']?.toFixed(0) || 0}ms`);
    console.log(`   p99: ${data.metrics.http_req_duration?.values['p(99)']?.toFixed(0) || 0}ms\n`);
    console.log('════════════════════════════════════════════\n');

    if (avgRate >= 950) {
        console.log('🎉 SUCCESS! Sustained >950 req/s\n');
    } else if (avgRate >= 800) {
        console.log('⚠️  CLOSE! Almost there...\n');
    } else {
        console.log('❌ BELOW TARGET. Check Grafana for bottlenecks.\n');
    }

    return { 'stdout': '' };
}
