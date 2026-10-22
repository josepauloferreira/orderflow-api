import http from 'k6/http';
import { check } from 'k6';

const api = __ENV.API_BASE_URL || 'http://127.0.0.1:8080';

// Open workload: fixed arrival rate, regardless of server response latency.
// Conservative CI gate, not a production SLA or an asserted capacity number.
export const options = {
  scenarios: {
    catalog_read: {
      executor: 'constant-arrival-rate',
      rate: 3,
      timeUnit: '1s',
      duration: '12s',
      preAllocatedVUs: 4,
      maxVUs: 12,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2500'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const response = http.get(api + '/api/v1/products?active=true&page=0&size=20', {
    tags: { endpoint: 'catalog' },
  });
  check(response, {
    'catalog responds 200': (r) => r.status === 200,
    'catalog has stable items array': (r) => {
      try {
        return Array.isArray(r.json('items'));
      } catch (_) {
        return false;
      }
    },
  });
}
