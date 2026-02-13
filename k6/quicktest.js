/**
 * Quick metrics test - generate data for Grafana dashboard
 * Runs for 1 minute to populate all panels
 */
import http from 'k6/http';

export const options = {
    vus: 10,
    duration: '1m',
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.JWT_TOKEN || '';

export default function () {
    http.get(`${BASE_URL}/api/test`, {
        headers: { 'Authorization': `Bearer ${TOKEN}` },
    });
}
