# OrderFlow API

A production-inspired REST API for order management and inventory control. This repository is intentionally developed in **small, verifiable increments** to demonstrate maintainable Java backend engineering.

> **Current status:** The original six milestones, business hardening and stage-8 performance/reliability experiments are integrated. Stage 9 adds evidence-backed release candidates and supply-chain controls. **The Compose stack is local/CI only, not a production deployment.**

## What this project demonstrates

OrderFlow is a **Java modular monolith**, not an overbuilt microservice demonstration. Its core engineering claims have executable evidence:

| Engineering concern | Implementation and proof |
| --- | --- |
| Transactions under contention | PostgreSQL row locks, consistent lock order, concurrent last-unit tests |
| Historical order integrity | Snapshotted prices, atomic movement ledger, rollback tests |
| Replay safety | Per-customer transactional idempotency keys and racing-request tests |
| Concurrent administrative editing | `@Version`, strong `ETag`/`If-Match`, parallel edit tests |
| Identity and authorization | JWT issuer/audience checks, roles, ownership, real Keycloak smoke |
| Data & quality contracts | Flyway SQL-first, OpenAPI, ArchUnit, Spotless and 49 Java tests |
| Measured reliability | Bounded k6 workloads, PostgreSQL lock plans, isolated backup/restore drill |

For a reviewer, start with [the engineering case study](docs/portfolio-case-study.md), [architecture diagrams](docs/architecture.md) and [evidence runbook](docs/release-process.md). The project intentionally reports unresolved production risks.

## Stack

- Java 25 LTS; Spring Boot 4.1.1; Maven 3.9.16 (Wrapper).
- Spring MVC, Bean Validation, Spring Data JPA, Hibernate and Actuator.
- PostgreSQL 18, Flyway and Docker Compose, with non-root Java 25 Docker runtime.
- JUnit Jupiter, AssertJ and Testcontainers 2 for integration testing.
- Spring Boot Actuator, Micrometer/Prometheus, ECS structured logs, request correlation and springdoc OpenAPI 3.1.1.
- ArchUnit 1.5.1 for executable architecture constraints.
- Lombok 1.18.48 and MapStruct 1.6.3 for selective code generation; Spotless / google-java-format and GitHub Actions.

## Requirements

- JDK 25 installed and selected (`java -version`).
- Docker Engine/Desktop with Compose. Docker is also required by integration tests.
- Git. A system Maven installation is **not** required.

## Containerized full stack (Docker only)

The easiest way to run all three services and exercise signed JWTs is:

```bash
cp .env.example .env
docker compose --profile app --profile auth up -d --build --wait
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

See [the operations runbook](docs/operations.md), [the API reference](docs/api-reference.md) and [local Keycloak tokens](docs/security-idempotency.md). This development stack publishes ports to loopback only.

## Run locally (macOS / Linux / bash)

```bash
cp .env.example .env
docker compose --profile auth up -d --wait
# confirm OIDC server readiness before launching Spring Boot
curl -fsS http://localhost:8081/realms/orderflow/.well-known/openid-configuration > /dev/null

set -a
. ./.env
set +a

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080/actuator/health`. Public product GET requests need no bearer token; writes require the roles documented below.

## Run locally (Windows PowerShell)

```powershell
Copy-Item .env.example .env
docker compose --profile auth up -d --wait
# Confirm readiness: http://localhost:8081/realms/orderflow/.well-known/openid-configuration

$env:JWT_ISSUER_URI = "http://localhost:8081/realms/orderflow"
$env:DB_URL = "jdbc:postgresql://localhost:5432/orderflow"
$env:DB_USERNAME = "orderflow"
$env:DB_PASSWORD = "change-me-local-only"

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

If you changed the values in `.env`, update the PowerShell variables accordingly. Docker Compose loads `.env` automatically; a locally running Java process **does not**.

## API reference

With the `local`, `container` or `test` profile, the generated API description is at `/v3/api-docs` (JSON), `/v3/api-docs.yaml` (YAML) and `/swagger-ui/index.html` (interactive UI). Documentation endpoints are **disabled by default** in other profiles, and only `/api/v1/**` operations are included. Consult [the API reference](docs/api-reference.md) for authentication, status codes, and idempotency examples.

## Product catalog

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/products` | Create a product (201 with Location) |
| `GET` | `/api/v1/products/{id}` | Read product by ID, including its ETag |
| `GET` | `/api/v1/products?active=true&page=0&size=20` | Filter and page (max 100 per page) |
| `PUT` | `/api/v1/products/{id}` | Conditional edit (`If-Match` required, 428/412); SKU immutable |
| `POST` | `/api/v1/products/{id}/deactivate` | Conditional deactivation (`If-Match` required, 204) |

Example:

```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H 'Authorization: Bearer <admin-access-token>' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"kb-100","name":"Mechanical Keyboard","description":"Quiet switches","price":129.90}'
```

For concurrent administrator edits, first GET the product, copy its strong `ETag`, then send `If-Match: <etag>` with PUT or deactivation. Stale versions receive 412; missing preconditions receive 428. See [hardening ADR](docs/adr/0006-http-concurrency-and-retention.md).

See [catalog design](docs/catalog.md), [inventory/ordering design](docs/inventory-orders.md) and [security/idempotency](docs/security-idempotency.md) for implementation details.

## Authentication and order submission

The API now requires a JWT from a configured issuer for private routes, and a required `Idempotency-Key` on order creation. For optional local development with Keycloak:

```bash
docker compose --profile auth up -d
```

See [local Keycloak instructions](docs/security-idempotency.md) for obtaining a sample token and the full authorization matrix.

## Performance and reliability experiments

Stage 8 provides reproducible k6 catalog and last-unit contention tests, PostgreSQL EXPLAIN/lock evidence and an isolated logical backup/restore exercise. These are **bounded CI experiments**, not production benchmarks or backup guarantees. See [the performance and reliability runbook](docs/performance-reliability.md).

## Release candidate and security checks

PRs run container vulnerability inspection and SBOM generation in addition to Java and Docker integration tests. GitHub's dependency-diff review is configured but currently **skipped until Dependency Graph is enabled**; see the release runbook for that prerequisite. A manual release-candidate workflow can build a versioned JAR, an image SBOM and a signed provenance attestation **without publishing a release or pushing to a registry**. The optional dependency-diff policy would block newly introduced high-severity vulnerable dependencies once enabled, while the active image-scan gate blocks fixable critical findings; high image findings are recorded for review.

Follow [the candidate release procedure](docs/release-process.md) before using it; it requires a reviewed `main` commit. No release has been issued automatically.

## Checks

```bash
./mvnw -B -ntp verify          # runs tests and formatting validation; requires Docker
./mvnw spotless:apply         # automatically format Java files
docker compose down           # stop database; keep volume
docker compose down -v        # DELETE local database volume/data
```

The integration tests start their **own PostgreSQL container** and supply a mock OIDC JWK endpoint; the local Keycloak container is not needed for Maven verification, but Docker must be available.

## Configuration and data

- `src/main/resources/application.yml`: common, environment-independent settings.
- `src/main/resources/application-local.yml`: local datasource configured through `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
- `compose.yaml`: database, optional API container and local Keycloak demo; ports bound to `127.0.0.1` only.
- `src/main/resources/db/migration/`: versioned Flyway SQL migrations, including tuned catalog pagination indexes.
- `.env.example`: **example development values, not production credentials**. Copy to `.env`, which is ignored by Git.

Hibernate verifies the schema (`ddl-auto=validate`); Flyway will own DDL changes as migrations are introduced.

## Development roadmap

1. **Foundation:** build, configurations, formatting, local PostgreSQL, CI and a database-backed smoke test.
2. **Catalog (PR #6):** product constraints, Flyway migrations, JPA, records/DTOs, MapStruct, Bean Validation and explicit exceptions.
3. **Inventory and ordering (PR #7):** transaction boundaries, deterministic row locking, rollback and state transitions.
4. **Security and resilience (PR #8):** Spring Security OAuth2 resource server, authorization, idempotency and Problem Details.
5. **Operations (PR #9):** non-root application Dockerfile, metrics, structured logs, correlation IDs and container smoke tests.
6. **Engineering review (PR #10):** generated OpenAPI, architecture constraints, contract tests, risk register and final validation.

See [ADR 0008](docs/adr/0008-release-candidates-and-provenance.md) for supply-chain controls and [ADR 0001](docs/adr/0001-modular-monolith.md) for modular architecture, [ADR 0002](docs/adr/0002-flyway-schema-first.md) for schema-first persistence and [ADR 0005](docs/adr/0005-operability-observability.md) for operational decisions. No business endpoints are claimed complete until their behavior and tests exist.

## Lombok guidelines

Lombok is configured with an explicit Maven annotation-processor path (required on Java 25). IDEs may need annotation processing enabled.

- Prefer Java **records** for request/response DTOs; do not use Lombok just to replace them.
- Use focused annotations such as `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` and `@RequiredArgsConstructor` where useful.
- Do **not** put `@Data` on JPA entities: the generated setters, equality, hash code and `toString` may undermine invariants or traverse lazy relationships.
- Keep business-state mutations in explicit domain methods. Do not use `@Setter` indiscriminately on entities.
- MapStruct is responsible for entity-to-DTO mapping. Its annotation processor uses `lombok-mapstruct-binding` alongside Lombok.

## Working agreements

- Keep feature code grouped by business domain rather than creating a large horizontal `controller/service/repository` layout.
- Add tests in the same pull request as new behavior.
- Do not commit real credentials, `.env`, generated binaries or `target/`.
- Open small, descriptive pull requests. Record non-obvious architectural trade-offs as ADRs.

## License

No license has been selected yet. Public availability of this source code does not grant reuse rights by itself.
