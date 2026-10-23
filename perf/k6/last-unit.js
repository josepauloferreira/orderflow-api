import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const api = __ENV.API_BASE_URL || 'http://127.0.0.1:8080';
const issuer = __ENV.OIDC_BASE_URL || 'http://127.0.0.1:8081/realms/orderflow';

// Prevent accidental load or data mutations against an external environment.
if (__ENV.ALLOW_DESTRUCTIVE_PERF !== 'LOCAL_ONLY' ||
    !api.startsWith('http://127.0.0.1:')) {
  throw new Error('This fixture-creating test is limited to local loopback. Set ALLOW_DESTRUCTIVE_PERF=LOCAL_ONLY.');
}

const successfulOrders = new Counter('orderflow_successful_orders');
const outcomes = http.expectedStatuses(201, 409);

export const options = {
  scenarios: {
    compete_for_last_unit: {
      executor: 'shared-iterations',
      vus: 12,
      iterations: 24,
      maxDuration: '35s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    orderflow_successful_orders: ['count==1'],
    http_req_duration: ['p(95)<10000'],
  },
};

function token(username, password) {
  const response = http.post(
    issuer + '/protocol/openid-connect/token',
    {
      client_id: 'orderflow-local',
      grant_type: 'password',
      username,
      password,
    },
    { tags: { name: 'OIDC token' } },
  );
  if (response.status !== 200) {
    throw new Error('Demo Keycloak token request failed: ' + response.status);
  }
  return response.json('access_token');
}

export function setup() {
  const admin = token('demo-admin', 'demo-admin-only');
  const customer = token('demo-customer', 'demo-customer-only');
  const sku = 'PERF-' + Date.now();
  const created = http.post(
    api + '/api/v1/products',
    JSON.stringify({ sku, name: 'Last-unit benchmark fixture', price: 5.00 }),
    { headers: { Authorization: 'Bearer ' + admin, 'Content-Type': 'application/json' } },
  );
  if (created.status !== 201) {
    throw new Error('Could not seed product: HTTP ' + created.status);
  }
  const productId = created.json('id');
  const stocked = http.post(
    api + '/api/v1/products/' + productId + '/stock/adjustments',
    JSON.stringify({ delta: 1, reason: 'Disposable last-unit stress fixture' }),
    { headers: { Authorization: 'Bearer ' + admin, 'Content-Type': 'application/json' } },
  );
  if (stocked.status !== 200 || stocked.json('availableQuantity') !== 1) {
    throw new Error('Could not seed exactly one stock unit');
  }
  // Tokens stay inside k6 memory; do not print or export them to the benchmark artifact.
  return { productId, admin, customer };
}

export default function (fixture) {
  // Every competitor represents a different checkout command, not an idempotent replay.
  const key = 'perf-' + __VU + '-' + __ITER + '-' + Date.now();
  const response = http.post(
    api + '/api/v1/orders',
    JSON.stringify({ items: [{ productId: fixture.productId, quantity: 1 }] }),
    {
      headers: {
        Authorization: 'Bearer ' + fixture.customer,
        'Idempotency-Key': key,
        'Content-Type': 'application/json',
      },
      responseCallback: outcomes,
      tags: { endpoint: 'last-unit-checkout' },
    },
  );
  if (response.status === 201) {
    successfulOrders.add(1);
  }
  check(response, {
    'confirmed or out of stock (never 500)': (r) => r.status === 201 ||
      (r.status === 409 && r.json('code') === 'INSUFFICIENT_STOCK'),
  });
}

export function teardown(fixture) {
  const result = http.get(
    api + '/api/v1/products/' + fixture.productId + '/stock',
    { headers: { Authorization: 'Bearer ' + fixture.admin } },
  );
  check(result, {
    'last unit is consumed once': (r) => r.status === 200 &&
      r.json('availableQuantity') === 0,
  });
}
