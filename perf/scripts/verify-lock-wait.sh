#!/usr/bin/env bash
set -euo pipefail

# Deterministic lock-wait observation using a disposable PERF fixture.
# Two psql sessions contend for the same product FOR UPDATE lock. We do not
# mutate application business state; both transactions roll back.
tmp="$(mktemp -d)"
holder_pid=""
contender_pid=""
cleanup() {
  [[ -n "$holder_pid" ]] && kill "$holder_pid" 2>/dev/null || true
  [[ -n "$contender_pid" ]] && kill "$contender_pid" 2>/dev/null || true
  rm -rf "$tmp"
}
trap cleanup EXIT

docker compose --profile app --profile auth exec -T postgres sh -c \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  > "$tmp/holder.log" 2>&1 <<'SQL' &
BEGIN;
SELECT id FROM products WHERE sku LIKE 'PERF-%' LIMIT 1 FOR UPDATE;
\echo HOLDER_READY
SELECT pg_sleep(9);
ROLLBACK;
SQL
holder_pid=$!

for _ in {1..30}; do
  grep -q HOLDER_READY "$tmp/holder.log" && break
  sleep 0.2
done
grep -q HOLDER_READY "$tmp/holder.log" || { echo "Holder never acquired product lock" >&2; exit 1; }

docker compose --profile app --profile auth exec -T postgres sh -c \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  > "$tmp/contender.log" 2>&1 <<'SQL' &
BEGIN;
SET statement_timeout = '15s';
SELECT id FROM products WHERE sku LIKE 'PERF-%' LIMIT 1 FOR UPDATE;
ROLLBACK;
SQL
contender_pid=$!

# PostgreSQL itself identifies blocked sessions; don't infer lock contention
# simply from slow HTTP responses or client-side timing.
observed=0
for _ in {1..25}; do
  count="$(docker compose --profile app --profile auth exec -T postgres sh -c \
    'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = '\''Lock'\'' AND cardinality(pg_blocking_pids(pid)) > 0"' | tr -d '[:space:]')"
  if [[ "$count" =~ ^[0-9]+$ ]] && (( count > 0 )); then
    observed=1
    docker compose --profile app --profile auth exec -T postgres sh -c \
      'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
      < perf/sql/lock-snapshot.sql > perf-results/locks.txt
    break
  fi
  sleep 0.2
done

wait "$holder_pid"
holder_pid=""
wait "$contender_pid"
contender_pid=""
[[ "$observed" == 1 ]] || { echo "Expected blocked PostgreSQL row lock not observed" >&2; exit 1; }
echo "PASS: observed a real PostgreSQL blocking lock wait" | tee perf-results/lock-observation.txt
