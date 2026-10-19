#!/usr/bin/env bash
set -euo pipefail

# Real OIDC verification, not a MockMvc mock JWT.
# This script is explicitly for the disposable local/CI Compose environment.
api="http://127.0.0.1:${API_PORT:-8080}"
issuer="http://127.0.0.1:8081/realms/orderflow"
scratch="$(mktemp)"
trap 'rm -f "$scratch"' EXIT

wait_until() {
  local name="$1" url="$2" attempts="${3:-80}"
  for ((i=1; i<=attempts; i++)); do
    if curl --silent --show-error --fail --output /dev/null "$url" 2>/dev/null; then
      echo "$name is ready"
      return 0
    fi
    sleep 3
  done
  echo "$name did not become ready" >&2
  return 1
}

get_token() {
  local user="$1" password="$2" data
  data="$(curl --fail-with-body --silent --show-error --request POST \
    "$issuer/protocol/openid-connect/token" \
    --data-urlencode 'client_id=orderflow-local' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$user" \
    --data-urlencode "password=$password")"
  printf '%s' "$data" | jq --exit-status --raw-output '.access_token'
}

http_status() {
  curl --silent --show-error --output "$scratch" --write-out '%{http_code}' "$@"
}

assert_status() {
  local expected="$1" actual="$2"
  if [[ "$actual" != "$expected" ]]; then
    echo "Expected HTTP $expected, received $actual" >&2
    cat "$scratch" >&2
    return 1
  fi
}

wait_until "Keycloak" "$issuer/.well-known/openid-configuration"
wait_until "OrderFlow readiness" "$api/actuator/health/readiness"

admin="$(get_token demo-admin demo-admin-only)"
customer="$(get_token demo-customer demo-customer-only)"
if [[ -z "$admin" || -z "$customer" ]]; then
  echo "Demo tokens were not issued" >&2
  exit 1
fi

# Health/public catalog work without a token, but sensitive metrics do not.
assert_status 200 "$(http_status "$api/api/v1/products")"
assert_status 401 "$(http_status "$api/actuator/prometheus")"
assert_status 403 "$(http_status --header "Authorization: Bearer $customer" "$api/actuator/prometheus")"
assert_status 200 "$(http_status --header "Authorization: Bearer $admin" "$api/actuator/prometheus")"
grep -q orderflow_orders_created_total "$scratch"

sku="SMOKE-$(date +%s)"
assert_status 201 "$(http_status --request POST "$api/api/v1/products" \
    --header "Authorization: Bearer $admin" \
    --header 'Content-Type: application/json' \
    --data "{\"sku\":\"$sku\",\"name\":\"Smoke product\",\"price\":20.00}")"
product="$(jq --raw-output '.id' < "$scratch")"
[[ "$product" =~ ^[0-9a-f-]{36}$ ]]

assert_status 200 "$(http_status --request POST "$api/api/v1/products/$product/stock/adjustments" \
  --header "Authorization: Bearer $admin" \
  --header 'Content-Type: application/json' \
  --data '{"delta":3,"reason":"CI smoke receipt"}')"
jq --exit-status '.availableQuantity == 3' "$scratch" >/dev/null

order_payload="{\"items\":[{\"productId\":\"$product\",\"quantity\":2}]}"
key="smoke-idempotency-$(date +%s)"
assert_status 201 "$(http_status --request POST "$api/api/v1/orders" \
  --header "Authorization: Bearer $customer" \
  --header "Idempotency-Key: $key" \
  --header 'Content-Type: application/json' \
  --data "$order_payload")"
order="$(jq --raw-output '.id' < "$scratch")"
[[ "$order" =~ ^[0-9a-f-]{36}$ ]]

assert_status 200 "$(http_status --request POST "$api/api/v1/orders" \
  --header "Authorization: Bearer $customer" \
  --header "Idempotency-Key: $key" \
  --header 'Content-Type: application/json' \
  --data "$order_payload")"
jq --exit-status --arg id "$order" '.id == $id' "$scratch" >/dev/null

assert_status 409 "$(http_status --request POST "$api/api/v1/orders" \
  --header "Authorization: Bearer $customer" \
  --header "Idempotency-Key: $key" \
  --header 'Content-Type: application/json' \
  --data "{\"items\":[{\"productId\":\"$product\",\"quantity\":1}]}")"
jq --exit-status '.code == "IDEMPOTENCY_CONFLICT"' "$scratch" >/dev/null

assert_status 200 "$(http_status --header "Authorization: Bearer $admin" \
  "$api/api/v1/products/$product/stock")"
jq --exit-status '.availableQuantity == 1' "$scratch" >/dev/null

assert_status 204 "$(http_status --request POST "$api/api/v1/orders/$order/cancel" \
  --header "Authorization: Bearer $customer")"

assert_status 200 "$(http_status --header "Authorization: Bearer $admin" \
  "$api/api/v1/products/$product/stock")"
jq --exit-status '.availableQuantity == 3' "$scratch" >/dev/null

echo "PASS: real signed OIDC tokens, role enforcement, secured metrics, stock, idempotency and cancellation"
