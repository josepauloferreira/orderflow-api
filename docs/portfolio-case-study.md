# OrderFlow — Engineering case study

## Engineering question

**How can a single-service Java backend accept concurrent orders safely without overselling, reject repeated checkout requests, and remain testable and diagnosable?**

OrderFlow is a deliberately constrained learning/reference project: Java 25, Spring Boot 4.1, PostgreSQL 18, Flyway and an external JWT provider. Its purpose is to make backend design decisions inspectable, rather than display the largest possible list of technologies.

## Core design choices

**1. A transaction owns the complete checkout.** Creation locks the relevant product rows, checks stock, inserts the order and immutable price snapshots, debits inventory and records a movement ledger before committing together. Product rows are the locking anchor even before an inventory row exists. Canonical UUID lock order reduces common multi-product deadlock patterns.

**2. Duplicate HTTP requests have database-level semantics.** The `Idempotency-Key` is scoped to the JWT subject and a hash of normalized order contents. A unique PostgreSQL key serializes concurrent replays across app instances; a different payload with the same key returns 409.

**3. Different concurrency problems need different tools.** Product metadata uses optimistic `@Version` plus HTTP `ETag`/`If-Match`, yielding 412 on stale updates. Stock mutation uses pessimistic locking because each checkout must decide based on the current available units.

**4. The schema is source-of-truth SQL.** Flyway migrations manage constraints and indexes; Hibernate validates mapping rather than performing production DDL. API transport DTOs use records; domain entities expose explicit business methods.

**5. Tests verify the architecture, not merely functions.** ArchUnit checks dependencies and encapsulation. PostgreSQL Testcontainers exercises real transactional behavior. A Compose smoke job uses real signed OIDC JWTs to verify role and ownership enforcement.

## Measured evidence — one bounded CI run

From GitHub Actions [run 39815484767](https://github.com/josepauloferreira/orderflow-api/actions/runs/39815484767) (October 22, 2026):

| Observation | Evidence |
| --- | --- |
| Maven verification | 49 tests, no failures/errors/skips |
| Catalog fixture | 5,000 synthetic rows, 37 catalog requests |
| Catalog response latency | p95 **13.92 ms**, 0 failed requests, 0 dropped iterations |
| Last-unit correctness | 24 checkout attempts, **1 confirmed**, 23 expected stock conflicts |
| PostgreSQL consistency | One confirmation movement, no remaining stock, SQL integrity result PASS |
| Query plan | Catalog pagination used `idx_products_created_id` in the sampled plan; filtered count was a sequential scan |
| Lock wait | A blocked PostgreSQL transaction was observed through `pg_stat_activity` |
| Restore exercise | `pg_dump -Fc` and `pg_restore` into a separate disposable DB, aggregate/constraint/history checks PASS |

These are short tests on a GitHub-hosted runner. They do **not** establish sustained throughput, a production SLO, independent backup recovery, or readiness for Internet-facing traffic. Full experiment and its limitations are in [performance-reliability.md](performance-reliability.md).

## What was deliberately not added

No Redis caching, Kafka broker, Kubernetes orchestration, distributed tracing system or microservice split. The existing PostgreSQL design satisfies the reference correctness goals; extra infrastructure would introduce more failure modes without measured justification.

## Production gaps and next decisions

- Migration V5 deliberately fails on legacy orders without a verified customer identity; no fake backfill is allowed.
- Idempotency records are retained until a safe client-visible retry horizon and cleanup contract exist.
- Local Keycloak demo grants and passwords must never be used for public deployments.
- Rate limiting, sustained performance testing, cross-host disaster recovery, managed secrets, secure monitoring networks and production incident/runbook evidence are incomplete.
- Release artifacts and supply-chain checks require verification against fresh vulnerability databases before any publishing decision.

For architecture detail see [architecture.md](architecture.md); for security and operations see [release-process.md](release-process.md) and [engineering-review.md](engineering-review.md).

## Reviewer walkthrough

1. Inspect `V1`–`V7` Flyway migrations and ADRs before reading entities.
2. Review `catalog`, `inventory`, `ordering` and the transaction boundaries.
3. Read the concurrent last-unit test and the idempotent replay test.
4. Inspect the authentication rules and signed-JWT smoke tests.
5. Compare the architecture diagram with the ArchUnit checks.
6. Review real CI artifacts and the risk register rather than assuming every operation was benchmarked.
