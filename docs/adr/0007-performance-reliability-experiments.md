# ADR 0007 — Measure before optimizing; verify transactional safety under load

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

The OrderFlow modular monolith has PostgreSQL row locks, atomic checkout, optimistic product edits and idempotency. Adding cache layers or asynchronous infrastructure without load measurements would increase complexity without proving improvement. Equally, passing a short integration suite does not establish throughput, tail-latency or disaster-recovery guarantees.

## Decision

- Keep the existing architecture and add **small, reproducible experiments** that exercise public catalog queries and contended checkout using actual signed Keycloak JWTs.
- Prefer k6's open arrival-rate model for catalog response latency at an explicitly fixed test arrival rate; use fixed shared iterations for a deterministic 24-request contention case.
- Treat legitimate stock conflicts as expected outcomes; add a server-side PostgreSQL invariant query to independently establish exactly one consumption.
- Capture `EXPLAIN (ANALYZE, BUFFERS)`, table statistics and a measured PostgreSQL blocking-lock event using `pg_stat_activity`/`pg_blocking_pids`.
- Create an isolated PostgreSQL backup-and-restore exercise using `pg_dump -Fc` and `pg_restore`, preserving the source database and not uploading actual data dumps.
- Keep initial CI load short and resource-bounded. Run larger sustained tests only against a dedicated, authorized environment outside regular pull-request CI.
- Retain benchmark summary JSON and textual plans as short-lived CI artifacts, tagged with the run/commit so observations are not generalized across environments.

## Explicit non-decisions

No new Redis, Kafka, connection pool tuning, isolation-level changes, extra indexes, or query rewrites without representative workloads and measured evidence. The short CI probe is a regression guard, **not** a published capacity benchmark. The restore drill checks a local logical backup on the same PostgreSQL server, **not** a proven off-host disaster-recovery procedure; it does not establish RPO or RTO.
