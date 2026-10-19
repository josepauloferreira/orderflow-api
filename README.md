# OrderFlow API

A production-inspired REST API for order management and inventory control. This repository is intentionally developed in **small, verifiable increments** to demonstrate maintainable Java backend engineering.

> **Current status:** Project foundation only. Product, inventory, order and security endpoints are **not implemented yet**.

## Stack

- Java 25 LTS; Spring Boot 4.1.1; Maven 3.9.16 (Wrapper).
- Spring MVC, Bean Validation, Spring Data JPA, Hibernate and Actuator.
- PostgreSQL 18, Flyway and Docker Compose.
- JUnit Jupiter, AssertJ and Testcontainers 2 for integration testing.
- Spotless / google-java-format and GitHub Actions.

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

Open `http://localhost:8080/actuator/health`. The health endpoint is the only HTTP endpoint currently provided.

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
2. **Catalog:** product constraints, Flyway migrations, JPA, records/DTOs, MapStruct, Bean Validation and explicit exceptions.
3. **Inventory and ordering:** transaction boundaries, locking, rollback and state transitions.
4. **Security and resilience:** Spring Security OAuth2 resource server, authorization, idempotency and Problem Details.
5. **Operations:** application Dockerfile, metrics, structured logs and performance review.
6. **Engineering review:** API documentation, architecture checks, examples and final validation.

See [ADR 0001](docs/adr/0001-modular-monolith.md) for architecture trade-offs. No business endpoints are claimed complete until their behavior and tests exist.

## Working agreements

- Keep feature code grouped by business domain rather than creating a large horizontal `controller/service/repository` layout.
- Add tests in the same pull request as new behavior.
- Do not commit real credentials, `.env`, generated binaries or `target/`.
- Open small, descriptive pull requests. Record non-obvious architectural trade-offs as ADRs.

## License

No license has been selected yet. Public availability of this source code does not grant reuse rights by itself.
