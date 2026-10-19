# ADR 0005: Operable single service with commit-aware metrics

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

The OrderFlow reference API has transactional order logic, JWT authorization and an external identity provider. It now needs a reproducible deployment artifact and diagnostics without introducing unrelated infrastructure.

## Decision

1. Build a multi-stage Java 25 image. The final image contains a JRE and executable JAR, runs as a fixed unprivileged UID/GID, and receives all database/OIDC configuration from environment variables.
2. Use opt-in Docker Compose profiles for the application and development-only Keycloak. Bind published ports to `127.0.0.1` in the example; it is not a public production deployment manifest.
3. Keep the token issuer `http://localhost:8081/realms/orderflow` in **local Compose only**. Fetch its public JWK set via the internal `http://keycloak:8080` Docker DNS address, avoiding an incorrect `issuer` mismatch. Real deployments must use a trusted HTTPS OIDC issuer and suitable JWK endpoint.
4. Expose Actuator **liveness** without dependencies and **readiness** with PostgreSQL; don't make a live process unhealthy just because a dependency is temporarily unavailable. Compose uses readiness before marking the application healthy.
5. Export Micrometer/Prometheus metrics, but require ADMIN authentication to retrieve `/actuator/prometheus` and `/actuator/info`. In a real monitoring setup, use a dedicated internal scrape identity/network instead of a personal admin token.
6. Use built-in Spring Boot ECS JSON logging in the container profile. A servlet filter propagates a bounded validated `X-Request-ID` through SLF4J MDC and responds with the identifier. Do not log tokens, payloads or customer identifiers.
7. Use low-cardinality counters for order creation, replay and cancellation and manual stock adjustment. Register increments using `TransactionSynchronization.afterCommit` so rolled-back operations are not counted as successful.
8. Add appropriate catalog pagination indexes in Flyway V6. Review query plans with actual representative data before changing fetch strategies; no unjustified cache layer is introduced.
9. Validate operations with two independent CI jobs: Maven/JUnit/Testcontainers and full Docker Compose startup with **real signed OIDC tokens**.

## Trade-offs and limitations

- Built-in HTTP metrics and request logs do not constitute a complete distributed tracing system. Trace propagation and sampling can be added when multiple services genuinely exist.
- These counters count locally confirmed operations in each process. They are not a financially authoritative ledger; PostgreSQL order and stock-movement tables remain authoritative. A process crash after commit but before metric recording can cause a missed observation.
- A plain admin-scoped scrape endpoint is adequate for the restricted local demo, not the final monitoring network policy.
- The Compose Keycloak demo enables insecure development defaults and publicly documented users/passwords. It is neither a production identity system nor an example of safe password grant use for real clients.
- Container image tags should be pinned to approved immutable digests in a production release pipeline; the learning setup uses inspectable stable major-version tags.
