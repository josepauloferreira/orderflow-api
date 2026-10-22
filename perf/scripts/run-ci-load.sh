#!/usr/bin/env bash
set -euo pipefail

# CI-only, bounded reference load. Never use these numbers as capacity claims.
: "${K6_IMAGE:=grafana/k6:1.8.1}"
mkdir -p perf-results

echo "Running 12-second, 3-iterations/second public catalog probe"
docker run --rm --network host \
  --volume "$PWD/perf/k6:/scripts:ro" \
  --volume "$PWD/perf-results:/results" \
  --env API_BASE_URL=http://127.0.0.1:8080 \
  "$K6_IMAGE" run --summary-export /results/catalog.json /scripts/catalog.js

echo "Running 24 contending checkouts over one real stock unit"
docker run --rm --network host \
  --volume "$PWD/perf/k6:/scripts:ro" \
  --volume "$PWD/perf-results:/results" \
  --env API_BASE_URL=http://127.0.0.1:8080 \
  --env OIDC_BASE_URL=http://127.0.0.1:8081/realms/orderflow \
  --env ALLOW_DESTRUCTIVE_PERF=LOCAL_ONLY \
  "$K6_IMAGE" run --summary-export /results/last-unit.json /scripts/last-unit.js

echo "Capturing PostgreSQL query plans (actual runtime on this tiny CI fixture)"
docker compose --profile app --profile auth exec -T postgres sh -c \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < perf/sql/catalog-plans.sql > perf-results/catalog-plans.txt

echo "Capturing lock-wait snapshot after the bounded load"
docker compose --profile app --profile auth exec -T postgres sh -c \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < perf/sql/lock-snapshot.sql > perf-results/locks.txt

# Ensure the disposable single-unit fixture resulted in exactly one order.
docker compose --profile app --profile auth exec -T postgres sh -c \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "
     SELECT CASE WHEN
       (SELECT count(*) FROM order_items oi JOIN products p ON p.id=oi.product_id
        WHERE p.sku LIKE '\''PERF-%'\'') = 1
       AND (SELECT count(*) FROM stock_movements sm JOIN products p ON p.id=sm.product_id
        WHERE p.sku LIKE '\''PERF-%'\'' AND sm.kind='\''ORDER_CONFIRMED'\'') = 1
       AND (SELECT sum(available_quantity) FROM inventory i JOIN products p ON p.id=i.product_id
        WHERE p.sku LIKE '\''PERF-%'\'') = 0
       THEN '\''PASS'\'' ELSE '\''FAIL'\'' END;"' \
  | tee perf-results/inventory-integrity.txt
grep -qx PASS perf-results/inventory-integrity.txt

echo "PASS: bounded k6 load, one confirmed order, no oversell and SQL evidence captured"
