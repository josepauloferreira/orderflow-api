# Running and diagnosing OrderFlow

## Prerequisites

Docker Engine/Desktop with Docker Compose and enough memory for PostgreSQL, Java and the local Keycloak dev server. A JDK is **not** needed when running the containerized application.

The sample Compose binds PostgreSQL, Keycloak and the API only to `127.0.0.1`. Do not expose this development Keycloak realm or its published credentials on a shared network.

## Start the entire local stack

```bash
cp .env.example .env
docker compose --profile app --profile auth config --quiet
docker compose --profile app --profile auth up -d --build --wait
curl --fail-with-body http://127.0.0.1:8080/actuator/health/readiness
```

Read `docs/security-idempotency.md` to get example JWTs for the included Keycloak realm. The local configuration uses the token issuer `http://localhost:8081/realms/orderflow` even inside the API container. Only the **JWK download URL** is overridden to Docker's internal `http://keycloak:8080/...` address. This preserves issuer checks while avoiding a container trying to access its own `localhost`.

If you already run Spring Boot on port 8080, stop it or set `API_PORT` in `.env`. The token issuer relies on Keycloak remaining reachable on host port 8081.

## Individual containers

```bash
docker compose --profile app --profile auth ps
docker compose --profile app --profile auth logs --tail=100 api
docker compose --profile app --profile auth logs --tail=100 postgres
docker compose --profile app --profile auth logs --tail=100 keycloak
```

Stop services while preserving database data:

```bash
docker compose --profile app --profile auth down
```

**Destructive:** `docker compose --profile app --profile auth down -v` removes local database volumes. Never use `-v` for persistent production data.

## Health checks

| Endpoint | Purpose | Access |
| --- | --- | --- |
| `/actuator/health/liveness` | JVM/application is alive | Public |
| `/actuator/health/readiness` | App is ready; PostgreSQL is reachable | Public |
| `/actuator/health` | Aggregate app health; no diagnostic details publicly exposed | Public |
| `/actuator/prometheus` | Micrometer / Prometheus metrics | ADMIN JWT only |
| `/actuator/info` | Application info | ADMIN JWT only |

The Compose healthcheck uses readiness, not liveness. Avoid configuring orchestration that restarts a JVM solely due to transient database unavailability.

## Metrics

The Prometheus scrape endpoint requires an admin bearer token:

```bash
curl --fail-with-body \
  -H 'Authorization: Bearer <admin-access-token>' \
  http://127.0.0.1:8080/actuator/prometheus
```

Custom Micrometer counters (Prometheus suffix `_total`) are:

- `orderflow.orders.created`: first-time order confirmations committed.
- `orderflow.orders.replayed`: successful idempotent replays committed.
- `orderflow.orders.cancelled`: first-time cancellations committed.
- `orderflow.stock.adjusted`: manual inventory adjustments committed.

These metrics deliberately have **no user ID, SKU or order ID labels**. Prometheus is a monitoring tool, not the source of truth; use the order and stock-movement tables for accounting/audit. Spring's HTTP request duration/count metrics complement the business counters.

## Request correlation and structured logs

Pass `X-Request-ID` with a 1–64 character safe ASCII value, or omit it to have the server create a UUID:

```bash
curl -i -H 'X-Request-ID: example-req-42' http://127.0.0.1:8080/api/v1/products
```

The server echoes the validated ID in the response and attaches it to the logging MDC as `requestId`. An invalid/untrusted value is replaced. In the `container` profile, Spring Boot's built-in ECS structured console logging writes JSON, including MDC fields; the `local` profile keeps developer-readable logs.

The filter does not log raw request bodies or bearer tokens. Avoid putting personal data or credentials in request IDs.

## CI checks

`./mvnw -B -ntp verify` runs formatting, builds and JUnit/Testcontainers tests. A separate `Container / OIDC smoke` job builds the multi-stage image, runs the entire Compose stack and performs signed-JWT requests against actual Keycloak, including authorization, order creation and idempotency. Read `.github/workflows/ci.yml` and `infra/scripts/smoke-container.sh`.

## Production readiness limits

This Compose configuration is intended **only for local development and CI**, not production.

Before any production deployment, implement managed secrets and HTTPS/TLS, a durable externally managed identity provider, image-digest pinning and image scanning, isolated metrics access, backups/restores, migration upgrade plans (especially V5 owner backfill), idempotency retention/cleanup, operational alerts, request timeouts, and a documented rollback/recovery procedure.

## Database/performance

`V6__index_product_catalog_pagination.sql` adds indexes aligned with unfiltered and `active`-filtered catalog pagination. Stock mutations already serialize on product row locks. Avoid assuming that more indexes or caches automatically help: evaluate `EXPLAIN (ANALYZE, BUFFERS)` with **realistic data**, and monitor lock wait time and slow queries before tuning.

For orders, order lines are loaded within the service transaction. `open-in-view=false` avoids relying on lazy-loading during HTTP response serialization.
