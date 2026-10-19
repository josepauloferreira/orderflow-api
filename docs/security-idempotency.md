# Security and order idempotency

## Permissions

| Endpoint | Anonymous | CUSTOMER | ADMIN |
| --- | --- | --- | --- |
| GET /api/v1/products, /api/v1/products/{id} | Yes | Yes | Yes |
| POST /api/v1/products, PUT, deactivate | No | No | Yes |
| Stock GET and adjustments | No | No | Yes |
| POST /api/v1/orders | No | Yes | No |
| GET or cancel an order | No | Owner only | Yes |
| GET /actuator/health | Yes | Yes | Yes |

Order ownership comes from the authenticated JWT `sub`, not a request-body field. Roles come from a Keycloak-compatible `realm_access.roles` claim. Only `ADMIN` and `CUSTOMER` are recognized.

JWT validation is configured with an exact issuer and audience `orderflow-api`. In production, provide a **trusted HTTPS issuer** in `JWT_ISSUER_URI`; do not use the local demonstration issuer.

## Local Keycloak demo

This is for local learning only. Two sample users and their credentials are deliberately stored in the public development-only realm file. **Never reuse these credentials or enable direct password grants in production.**

```bash
cp .env.example .env
docker compose --profile auth up -d
# wait until the OIDC discovery endpoint is available
curl -fsS http://localhost:8081/realms/orderflow/.well-known/openid-configuration
set -a; . ./.env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

For a demo customer token (locally only):

```bash
curl -sS -X POST http://localhost:8081/realms/orderflow/protocol/openid-connect/token \
  -d client_id=orderflow-local \
  -d grant_type=password \
  -d username=demo-customer \
  -d password=demo-customer-only
```

The JSON response contains an `access_token`. For an admin token, use `demo-admin` and `demo-admin-only`. Pass the token as `Authorization: Bearer <access_token>`.

The realm import is applied only when the realm is first created. If you intentionally reset the development identity-provider data, recreate its container; the demo uses nonpersistent Keycloak state.

## Idempotent order creation

```http
POST /api/v1/orders
Authorization: Bearer <customer-token>
Idempotency-Key: cart-checkout-3f7ab921
Content-Type: application/json

{"items":[{"productId":"<uuid>","quantity":2}]}
```

- The initial operation returns `201 Created`.
- Repeating it with the **same key and semantically identical products/quantities** (even if item order differs) returns `200 OK` with the same order ID.
- The same customer reusing the key with a different payload receives `409 IDEMPOTENCY_CONFLICT`.
- Another authenticated customer has a separate key namespace.
- A failed transaction does not reserve the key permanently.

The implementation uses a database uniqueness guarantee and transactional synchronization, not an in-memory map or a preflight `existsBy` check, so it also works across multiple API instances connected to the same PostgreSQL cluster.

## Migration caveat

`V5__order_ownership_idempotency.sql` refuses to apply if old orders exist without ownership. Since this is a learning repository without production data, the documented migration is safe for a clean database. Do **not** force this migration through on a populated production database without a verified owner backfill plan.

## Testing limitations

Security tests exercise HTTP filter rules and ownership with mock JWTs; they do not independently demonstrate cryptographic verification against an actual OIDC server. The local Keycloak setup permits a separate end-to-end smoke test.

See [ADR 0004](adr/0004-jwt-idempotency.md).
