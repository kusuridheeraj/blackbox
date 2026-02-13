/**
 * BLACKBOX k6 Load Test — High Load (10k RPS)
 *
 * WARNING: This generates 10,000 requests/second.
 * Your laptop may struggle. Start with baseline.js first!
 *
 * Run:
 *   k6 run k6/highload.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const errorRate = new Rate('errors');
const throttleRate = new Rate('throttled');
const throttleCount = new Counter('throttle_count');

export const options = {
    scenarios: {
        high_load: {
            executor: 'constant-arrival-rate',
            rate: 10000,        // 10,000 requests/second
            timeUnit: '1s',
            duration: '30s',    // Only 30 seconds!
            preAllocatedVUs: 500,
            maxVUs: 2000,
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<5000'],  // Very relaxed
    },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.JWT_TOKEN || '';

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
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
    });
}

export function handleSummary(data) {
    console.log('\n=== BLACKBOX High Load Test Results ===');
    console.log(`Total Requests: ${data.metrics.http_reqs?.values?.count || 0}`);
    console.log(`Actual Rate: ${data.metrics.http_reqs?.values?.rate || 0} req/s`);
    console.log(`Error Rate: ${((data.metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%`);
    console.log(`Throttle Rate: ${((data.metrics.throttled?.values?.rate || 0) * 100).toFixed(2)}%`);
    console.log(`p50: ${data.metrics.http_req_duration?.values['p(50)'] || 0}ms`);
    console.log(`p95: ${data.metrics.http_req_duration?.values['p(95)'] || 0}ms`);
    console.log(`p99: ${data.metrics.http_req_duration?.values['p(99)'] || 0}ms`);
    console.log('=======================================\n');

    return {
        'stdout': '',
    };
}
