# OrderFlow API

A production-inspired REST API for order management and inventory control. This repository is intentionally developed in **small, verifiable increments** to demonstrate maintainable Java backend engineering.

> **Current status:** Product catalog implemented and under review (PR #6). Inventory, orders and security remain future milestones. **There is currently no authentication; do not deploy this branch publicly.**

## Stack

- Java 25 LTS; Spring Boot 4.1.1; Maven 3.9.16 (Wrapper).
- Spring MVC, Bean Validation, Spring Data JPA, Hibernate and Actuator.
- PostgreSQL 18, Flyway and Docker Compose.
- JUnit Jupiter, AssertJ and Testcontainers 2 for integration testing.
- Lombok 1.18.48 and MapStruct 1.6.3 for selective code generation; Spotless / google-java-format and GitHub Actions.

## Requirements

- JDK 25 installed and selected (`java -version`).
- Docker Engine/Desktop with Compose. Docker is also required by integration tests.
- Git. A system Maven installation is **not** required.

## Run locally (macOS / Linux / bash)

```bash
cp .env.example .env
docker compose up -d --wait

set -a
. ./.env
set +a

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080/actuator/health` or use the catalog endpoints below after checking out the catalog branch (or after its PR has merged).

## Run locally (Windows PowerShell)

```powershell
Copy-Item .env.example .env
docker compose up -d --wait

$env:DB_URL = "jdbc:postgresql://localhost:5432/orderflow"
$env:DB_USERNAME = "orderflow"
$env:DB_PASSWORD = "change-me-local-only"

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

If you changed the values in `.env`, update the PowerShell variables accordingly. Docker Compose loads `.env` automatically; a locally running Java process **does not**.

## Product catalog (current milestone)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/products` | Create a product (201 with Location) |
| `GET` | `/api/v1/products/{id}` | Read product by ID |
| `GET` | `/api/v1/products?active=true&page=0&size=20` | Filter and page (max 100 per page) |
| `PUT` | `/api/v1/products/{id}` | Replace editable product details; SKU is immutable |
| `POST` | `/api/v1/products/{id}/deactivate` | Idempotent deactivation (204) |

Example:

```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"kb-100","name":"Mechanical Keyboard","description":"Quiet switches","price":129.90}'
```

See [catalog API and persistence design](docs/catalog.md) for detailed constraints, response shapes, examples and trade-offs.

## Checks

```bash
./mvnw -B -ntp verify          # runs tests and formatting validation; requires Docker
./mvnw spotless:apply         # automatically format Java files
docker compose down           # stop database; keep volume
docker compose down -v        # DELETE local database volume/data
```

The integration smoke test starts **its own PostgreSQL container**. Running `docker compose up` is not necessary for the tests, but Docker must be available.

## Configuration and data

- `src/main/resources/application.yml`: common, environment-independent settings.
- `src/main/resources/application-local.yml`: local datasource configured through `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
- `compose.yaml`: database bound to `127.0.0.1` only, with a healthcheck and persistent volume.
- `src/main/resources/db/migration/`: versioned Flyway SQL migrations (first domain migration belongs to the catalog phase).
- `.env.example`: **example development values, not production credentials**. Copy to `.env`, which is ignored by Git.

Hibernate verifies the schema (`ddl-auto=validate`); Flyway will own DDL changes as migrations are introduced.

## Development roadmap

1. **Foundation:** build, configurations, formatting, local PostgreSQL, CI and a database-backed smoke test.
2. **Catalog (PR #6):** product constraints, Flyway migrations, JPA, records/DTOs, MapStruct, Bean Validation and explicit exceptions.
3. **Inventory and ordering:** transaction boundaries, locking, rollback and state transitions.
4. **Security and resilience:** Spring Security OAuth2 resource server, authorization, idempotency and Problem Details.
5. **Operations:** application Dockerfile, metrics, structured logs and performance review.
6. **Engineering review:** API documentation, architecture checks, examples and final validation.

See [ADR 0001](docs/adr/0001-modular-monolith.md) for modular architecture and [ADR 0002](docs/adr/0002-flyway-schema-first.md) for our migration-driven, schema-first persistence policy. No business endpoints are claimed complete until their behavior and tests exist.

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
