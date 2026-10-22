#!/usr/bin/env bash
set -euo pipefail

# DISPOSABLE CI/LOCAL TEST DATABASE ONLY. This script never restores into
# the running application database; it verifies data in a separate database.
# Never run against production or reuse a real database name for the target.
: "${RESTORE_DRILL_LOCAL_ONLY:?Set RESTORE_DRILL_LOCAL_ONLY=YES to allow a local drill}"
[[ "$RESTORE_DRILL_LOCAL_ONLY" == YES ]] || { echo "Local confirmation required" >&2; exit 2; }

backup="$(mktemp)"
restore_db="orderflow_stage8_drill"
mkdir -p perf-results
cleanup() {
  rm -f "$backup"
  docker compose --profile app --profile auth exec -T postgres sh -c \
    'dropdb -U "$POSTGRES_USER" --if-exists --force orderflow_stage8_drill' >/dev/null 2>&1 || true
}
trap cleanup EXIT

db() {
  docker compose --profile app --profile auth exec -T postgres sh -c \
    'psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -c "$2"' \
    -- "$1" "$2"
}
# Sh positional arguments above are not expanded inside the single-quoted
# shell expression. Use a fixed, safe database name per invocation instead.
query() {
  local target="$1" sql="$2"
  docker compose --profile app --profile auth exec -T postgres sh -c \
    "psql -X -A -t -v ON_ERROR_STOP=1 -U \"\$POSTGRES_USER\" -d \"$target\" -c \"$sql\""
}
fingerprint_sql="SELECT md5(concat_ws('|',
  (SELECT count(*) FROM products),
  (SELECT count(*) FROM inventory),
  (SELECT coalesce(sum(available_quantity),0) FROM inventory),
  (SELECT count(*) FROM customer_orders),
  (SELECT coalesce(sum(total_amount),0) FROM customer_orders),
  (SELECT count(*) FROM order_items),
  (SELECT count(*) FROM order_idempotency),
  (SELECT count(*) FROM stock_movements)))"

source_db="${DB_NAME:-orderflow}"
[[ "$source_db" =~ ^[a-zA-Z_][a-zA-Z_0-9]*$ ]] || { echo "Unsafe database name" >&2; exit 2; }
[[ "$source_db" != "$restore_db" ]] || { echo "Refusing to overwrite source" >&2; exit 2; }

started="$(date +%s)"
source_fingerprint="$(query "$source_db" "$fingerprint_sql")"

docker compose --profile app --profile auth exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' > "$backup"
[[ -s "$backup" ]] || { echo "Empty backup" >&2; exit 1; }

# Only the dedicated disposable target DB may be dropped.
docker compose --profile app --profile auth exec -T postgres sh -c \
  'dropdb -U "$POSTGRES_USER" --if-exists --force orderflow_stage8_drill'
docker compose --profile app --profile auth exec -T postgres sh -c \
  'createdb -U "$POSTGRES_USER" orderflow_stage8_drill'
docker compose --profile app --profile auth exec -T postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d orderflow_stage8_drill --exit-on-error --no-owner --no-acl' < "$backup"

restored_fingerprint="$(query "$restore_db" "$fingerprint_sql")"
[[ "$source_fingerprint" == "$restored_fingerprint" ]] || {
  echo "Restored totals do not match the source" >&2
  exit 1
}

# Check that a schema constraint was actually restored, not only data rows.
constraint="$(query "$restore_db" "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_inventory_nonnegative'")"
[[ "$constraint" == 1 ]] || { echo "Inventory check constraint was not restored" >&2; exit 1; }
flyway="$(query "$restore_db" "SELECT count(*) FROM flyway_schema_history WHERE success = true")"
[[ "$flyway" -ge 7 ]] || { echo "Flyway migration history was not restored" >&2; exit 1; }

elapsed="$(( $(date +%s) - started ))"
{
  echo "Restore drill: PASS"
  echo "Target: isolated disposable PostgreSQL database"
  echo "Compared: counts, sums, stock, historical orders, idempotency and movements"
  echo "Schema constraints: present"
  echo "Flyway migrations: verified"
  echo "Wall time on this runner (seconds): $elapsed"
  echo "Not a measured RPO/RTO or a proof of a real disaster-recovery process"
} | tee perf-results/restore-drill.txt
