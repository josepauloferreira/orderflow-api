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
- **Conditional updates (stage 7):** Flyway V7 and Hibernate @Version enforce strong ETag/If-Match requests for catalog mutations; malformed/stale requests and parallel edits are tested.

## Unresolved work — not production-ready

| Priority | Limitation | Next engineering step |
| --- | --- | --- |
| High | Flyway V5 aborts on existing orders without an owner | Plan and test a real-data ownership backfill before an upgrade |
| High | The local Keycloak demo uses known credentials and password grants | Use managed HTTPS OIDC and Authorization Code + PKCE in actual clients |
| High | Idempotency-key records have no retention lifecycle | Define a retry window and audit-safe cleanup that preserves semantics |
| Resolved in stage 7 | Concurrent admin product edits previously had lost-update risk | Flyway V7, Hibernate @Version, ETag/If-Match and concurrency regression tests |
| Partially addressed (stage 8) | No sustained representative load baseline, rate limiting or quotas | Added short k6 catalog/last-unit CI scenarios; follow up with large realistic datasets and gateway policies |
| Medium | Monitoring lacks SLO targets, alert rules and cross-service tracing | Define SLOs and operational dashboards based on measurable requirements |
| Improved in stage 7 | Common MVC failures previously had inconsistent shapes | Consolidated Problem Details advice; broaden OpenAPI error schemas and test remaining edge cases later |
| Partially addressed (stage 9) | Release supply chain lacked a clear policy | Added **opt-in** PR dependency review (currently skipped until Dependency Graph enabled), image SBOM, Trivy critical gate and manual attested JAR candidate; **no deployed image signing, publication or complete release verification yet** |
| Partially addressed (stage 8) | No off-host disaster-recovery evidence or RPO/RTO targets | Added isolated same-server pg_dump/pg_restore drill with aggregate reconciliation; rehearse loss of primary on another host |
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

## Stage-9 release evidence

Candidate workflows intentionally separate **building/attesting** from **publishing/deploying**. The manual workflow must be exercised on `main` before claiming that attestation succeeds. Critical image vulnerability gates block known fixable critical findings; this cannot prove the image has no vulnerabilities, nor does it replace secure registry distribution, image signatures and staging release rehearsals.

## Acceptance checks

- `./mvnw -B -ntp verify` must pass all previous tests plus the new ArchUnit/OpenAPI cases.
- The CI Compose job must build and start the app and OIDC provider, validate the signed JWT flow and retrieve the OpenAPI contract.
- All known risks must be documented honestly; the PR remains open until both CI jobs succeed.
