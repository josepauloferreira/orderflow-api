# Engineering audit and remaining risks

**Review date:** October 20, 2026. **Scope:** Java 25 / Spring Boot 4.1 API, PostgreSQL 18, Flyway, OAuth2 Resource Server, Docker Compose, GitHub Actions and Micrometer.

## Verified properties

- **Schema-first:** database structure lives in Flyway migrations. Hibernate validates instead of generating DDL.
- **Domain invariants:** encapsulated JPA entities, typed DTO records, MapStruct and explicit state transitions.
- **Stock consistency:** product-row pessimistic locks in canonical order and PostgreSQL constraints, including tests for concurrent purchases, first-stock initialization, rollback and idempotent cancellation.
- **Authentication/ownership:** signed JWT verification with issuer and audience restrictions; customer order ownership is checked at the service layer, not entrusted to request data.
- **Idempotency:** unique database key per customer, canonical payload digest and atomic transaction.
- **Operations:** non-root Docker runtime, JSON logs, safe correlation IDs, readiness/liveness, committed-operation metrics, real Keycloak smoke test.
- **Architecture/API governance:** ArchUnit rules constrain package dependencies; OpenAPI is generated and checked against the actual MVC mappings.

## Unresolved work — not production-ready

| Priority | Limitation | Next engineering step |
| --- | --- | --- |
| High | Flyway V5 aborts on existing orders without an owner | Plan and test a real-data ownership backfill before an upgrade |
| High | The local Keycloak demo uses known credentials and password grants | Use managed HTTPS OIDC and Authorization Code + PKCE in actual clients |
| High | Idempotency-key records have no retention lifecycle | Define a retry window and audit-safe cleanup that preserves semantics |
| High | Concurrent admin product edits do not use a defined ETag/If-Match policy | Add optimistic version checks or explicit last-write-wins semantics |
| Medium | Rate limiting, request quotas and load-test evidence do not exist | Add gateway limits and run realistic concurrency/load scenarios |
| Medium | Monitoring lacks SLO targets, alert rules and cross-service tracing | Define SLOs and operational dashboards based on measurable requirements |
| Medium | Framework-generated HTTP errors may differ from domain Problem Details | Standardize error formatting and extend OpenAPI error schemas |
| Medium | Release workflow does not include image-signing, dependency policies or SBOM attestation | Add software supply-chain controls before production releases |
| Medium | Backup/restore exercises and disaster-recovery targets are not demonstrated | Practice database recovery and define RPO/RTO |
| Low | Some application services accept transport-layer request records | Split into application commands only when another adapter requires it |

These are identified risks, **not features that were implemented or tested**. A green CI is not evidence of production performance, legal compliance, complete security or successful database restoration.

## Practices to retain

1. Use concise, feature-oriented packages and avoid generic boilerplate layers unless they provide value.
2. Prefer explicit SQL schema and database-enforced invariants to duplicate annotation-driven DDL.
3. Treat domain rules and transactions as first-class concepts; test failures and concurrent callers against PostgreSQL.
4. Keep the HTTP contract stable and use versioned DTOs rather than serialize framework internals.
5. Capture important decisions as ADRs and encode enforceable rules as unit/integration/architecture tests.
6. Do not add Redis/Kafka/Kubernetes/microservices without an actual measured requirement.
7. Keep the demo Compose manifest separate in intent from a production deployment.

## Acceptance checks

- `./mvnw -B -ntp verify` must pass all previous tests plus the new ArchUnit/OpenAPI cases.
- The CI Compose job must build and start the app and OIDC provider, validate the signed JWT flow and retrieve the OpenAPI contract.
- All known risks must be documented honestly; the PR remains open until both CI jobs succeed.
