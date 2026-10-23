# Performance and reliability: reproducible experiments

**Scope:** bounded, disposable local / GitHub Actions infrastructure only. Not a production throughput benchmark.

## Executable measurements

The extended `Container / OIDC smoke` workflow runs the actual Java 25 API, PostgreSQL 18 and local Keycloak. The stage-8 scripts execute after the functional signed-JWT smoke tests.

1. **Catalog load:** The harness first inserts **5,000 synthetic catalog rows** into its disposable database and runs `ANALYZE` (details in `perf/sql/seed-catalog.sql`). `perf/k6/catalog.js` uses `constant-arrival-rate`, requesting 3 iterations/second for 12 seconds (max 12 VUs) against public `GET /api/v1/products?active=true&page=0&size=20`. It requires successful JSON shape, error rate below 1%, no dropped iterations and p95 below 2500 ms. These are **smoke thresholds**, not a production SLO. The seeded SKU prefix is `BMARK-` and is not customer data.
2. **Last-unit contention:** `perf/k6/last-unit.js` uses a local demo JWT to create exactly one stock unit and starts 24 separate checkout attempts across 12 VUs. Expected behavior: **exactly one 201**, the remaining requests **409 INSUFFICIENT_STOCK**; no 500s or overselling. An independent SQL query checks that precisely one order item and one confirmation movement were recorded, with zero remaining units. An expected 409 is not an HTTP failure for this workload.
3. **SQL execution plans:** `perf/sql/catalog-plans.sql` records `EXPLAIN (ANALYZE, BUFFERS)` for paginated filtered/unfiltered product queries and a filtered count query. These plans run against **5,000 synthetic catalog rows plus the smoke fixture**, not a production dataset. Both sequential and index scans can be justified by cost; a sequential scan is not necessarily a problem. For index tuning, repeat with realistic cardinality and data distributions and inspect both planner estimates and actual buffers.
4. **PostgreSQL row-lock observation:** `perf/scripts/verify-lock-wait.sh` opens two explicit transactions with `SELECT ... FOR UPDATE` on the same disposable product. While one holds the lock, the other blocks, and the script requires `pg_stat_activity.wait_event_type = 'Lock'` plus nonempty `pg_blocking_pids`. It records a diagnostic snapshot. This validates that the database can expose contention; it does **not** measure peak lock throughput or claim deadlock freedom.
5. **Recovery drill:** `perf/scripts/restore-drill.sh` creates a PostgreSQL `pg_dump -Fc`, restores into a **new, uniquely named database**, compares basic counts and totals, verifies a schema CHECK constraint and successful Flyway migration history, and removes the disposable database. It does **not** overwrite the running database. The binary dump is kept temporary and is never uploaded as a CI artifact.

All tests run against disposable loopback-only Docker Compose with public development credentials. Do **not** aim them at any public, shared or production service.

## Commands on a Linux Docker host

```bash
cp .env.example .env
docker compose --profile app --profile auth up -d --build --wait
bash infra/scripts/smoke-container.sh
bash perf/scripts/run-ci-load.sh
RESTORE_DRILL_LOCAL_ONLY=YES bash perf/scripts/restore-drill.sh
```

The k6 runner uses Docker `--network host` for `127.0.0.1` access to API and Keycloak. This is known to work on the Linux GitHub Actions runner. Docker Desktop on Windows/macOS may require host networking support or equivalent changes; do not assume these commands work unchanged. A system k6 installation is also an option: run each JS file with the documented environment variables, preserving the loopback-only safety guard.

To inspect fresh measurements:

```bash
cat perf-results/inventory-integrity.txt
cat perf-results/lock-observation.txt
cat perf-results/restore-drill.txt
less perf-results/catalog-plans.txt
```

The full k6 summary JSON files are `perf-results/catalog.json` and `perf-results/last-unit.json`. They report actual request counts, `http_req_duration` distributions, throughput, error rates and VU activity for the specific run. The CI uploads only these non-secret summaries plus text observations as a short-lived `stage8-local-ci-evidence` artifact. It never uploads JWTs or backup bytes.

## How to interpret

- **Throughput:** compare achieved iterations/second with the configured arrival rate. Dropped iterations indicate the generator could not keep up.
- **Latency:** compare p50/p95/p99 for the same workload and dataset. Time spent obtaining login tokens or seeding fixtures is not included in per-iteration request latency.
- **Integrity:** one 201 and 23 expected 409s are a correctness result, not a capacity number.
- **Lock contention:** the deliberate two-session lock hold is a diagnostic proof of visibility. Do not conflate it with actual user-load lock-wait percentiles.
- **Recovery:** elapsed wall time and aggregate comparison on the CI fixture do not define production RPO/RTO. A true DR exercise requires an external backup, simulated source loss, an alternate host and verification of restore and service behavior.
- **Query plans:** table size, cache warmth, available RAM and index statistics affect plans. Never force index scans or add caches based solely on a tiny fixture.

## Next experiments

Populate thousands of varied products on a dedicated test database, record cardinalities, rerun EXPLAIN/latency at increasing rates, and add a sustained checkout scenario with multiple hot SKUs. Decide throughput/error budgets and tuning changes only after a representative baseline. For real recovery guarantees, rehearse restoring on a separate machine and measure application availability and data loss.

See [ADR 0007](adr/0007-performance-reliability-experiments.md) and [operations runbook](operations.md).
