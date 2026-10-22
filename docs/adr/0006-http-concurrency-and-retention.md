# ADR 0006 — Conditional product updates and safe idempotency retention

- **Status:** Accepted
- **Date:** 2026-10-20

## Problem

Concurrent administrator edits previously used unconditional product writes. Two clients could load the same product, edit its name/price, and overwrite each other's changes without detecting a conflict. Framework HTTP errors also produced inconsistent error bodies across feature packages. Committed idempotency keys currently have no retention policy.

## Decision: product concurrency

1. **SQL-first optimistic version column.** Flyway V7 adds `version BIGINT NOT NULL DEFAULT 0` and a nonnegative CHECK constraint to `products`. Existing persisted products begin at version zero. JPA maps this column with `@Version`; Hibernate is forbidden from generating DDL.
2. **Strong HTTP entity tag.** Creation and single-product reads return strong, quoted decimal ETags (e.g. `"0"`). Product DTOs include `version` for observability, but the canonical HTTP concurrency contract is the ETag header.
3. **Require conditional mutations.** PUT and deactivation require one exact strong ETag in `If-Match`. Missing header: HTTP 428; malformed/weak/wildcard: HTTP 400; stale validator: HTTP 412. Successful PUT returns a fresh ETag.
4. **Protect the read/write race.** Check the supplied version in the transactional service, then rely on Hibernate `@Version` during flush to detect another writer committing after that check. Map optimistic locking exceptions to 412. Do not acquire pessimistic product locks just for simple administrative edits.
5. **Deactivation remains logically idempotent.** An already-inactive product does not transition again or increment the version. Clients must supply the **current** ETag, so repeating with an old validator correctly returns 412 even if the resource is inactive.
6. **HTTP errors are unified.** Common application and framework failures use one global `@RestControllerAdvice` to produce Problem Details with stable error codes, instead of duplicated feature handlers. Unexpected runtime errors are not exposed to the client by catching `Exception` wholesale.

## Decision: idempotency retention

The database idempotency key is `(customer_subject, idempotency_key)` and is tied transactionally to an order. **We will not automatically prune this table without a defined and client-visible deduplication horizon.**

Blind deletion would allow a retry of an old key to create an additional order. Until a bounded retry policy and an operational cleanup procedure are jointly specified, keep key records and monitor table growth/age with read-only SQL queries. Production-sized deployments must address retention explicitly.

## Migration upgrade caution

V5 introduces `customer_subject` with a guard that deliberately fails when old orders have no owner. Because applied Flyway migrations are immutable, simply adding V7 cannot repair a database stuck before V5. Production upgrades from pre-ownership schemas require an **explicit tailored migration/backfill path** validated against real order→customer evidence, a rehearsal on a restored copy, and appropriate Flyway migration history management. Never insert a fake/default customer, bypass Flyway checks or drop historical orders to make startup succeed.

## Validation and limits

Unit/integration tests must prove 428/400/412 behavior, successful ETag progression, correct concurrent edit serialization and no changes on rejected writes. Docker Compose smoke must also validate a real authenticated conditional product edit using a signed JWT. This does not prove production upgrade compatibility or a retention cleanup policy; both remain separate work.
