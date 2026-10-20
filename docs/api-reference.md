# OrderFlow API reference

The OpenAPI contract is generated from Spring MVC mappings, request/response records and Swagger annotations, rather than a hand-written JSON file.

## Explore

Start the development stack:

```bash
cp .env.example .env
docker compose --profile app --profile auth up -d --build --wait
```

- Interactive Swagger UI: `http://127.0.0.1:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`
- OpenAPI YAML: `http://127.0.0.1:8080/v3/api-docs.yaml`

Documentation is enabled in the `local`, `container` and `test` profiles only. Without these profiles it is disabled by default. The generated contract is scoped to `/api/v1/**`, not Actuator. **The docs endpoints are public when the development profiles enable them; never enable these profiles on an untrusted public network.**

## Auth and roles

Spring Security validates the signed JWT issuer and audience `orderflow-api`. `realm_access.roles` grants only the allowlisted values `CUSTOMER` and `ADMIN`.

- Anonymous callers may read the product catalog; health probes remain public.
- `ADMIN` may mutate catalog/stock and view/cancel any order.
- `CUSTOMER` may create an order and read/cancel only orders matching the token's immutable `sub`.

Use Swagger UI's Authorize feature with a valid bearer token or send `Authorization: Bearer <token>` in your HTTP client. JWT issuance is delegated to Keycloak/OIDC, not handled by this application. For local demo tokens, follow [security setup](security-idempotency.md).

## API paths

| Method | Endpoint | Requirements | Success |
| --- | --- | --- | --- |
| GET | `/api/v1/products` | Public; `active`, `page`, `size` filters | 200 |
| GET | `/api/v1/products/{id}` | Public | 200 |
| POST | `/api/v1/products` | ADMIN; validated product body | 201 + Location |
| PUT | `/api/v1/products/{id}` | ADMIN; editable details, immutable SKU | 200 |
| POST | `/api/v1/products/{id}/deactivate` | ADMIN; idempotent | 204 |
| GET | `/api/v1/products/{productId}/stock` | ADMIN | 200 |
| POST | `/api/v1/products/{productId}/stock/adjustments` | ADMIN; signed delta and reason | 200 |
| POST | `/api/v1/orders` | CUSTOMER; Idempotency-Key | 201 original / 200 replay |
| GET | `/api/v1/orders/{id}` | Owner or ADMIN | 200 |
| POST | `/api/v1/orders/{id}/cancel` | Owner or ADMIN; idempotent | 204 |

### Idempotent order

```http
POST /api/v1/orders
Authorization: Bearer <customer-token>
Idempotency-Key: checkout-8f48c29d
Content-Type: application/json

{"items":[{"productId":"<product-uuid>","quantity":2}]}
```

The initial request confirms a paid-independent order and snapshots product prices/names. A semantically identical retry using the same customer and key returns the same order without a second inventory debit; a different payload with the same key returns 409. A failed/rolled-back attempt releases the key.

### Error conventions

Domain errors generally return RFC 9457 Problem Details with a stable `code` extension: `DUPLICATE_SKU`, `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `INSUFFICIENT_STOCK`, `PRODUCT_INACTIVE`, `IDEMPOTENCY_CONFLICT`, `VALIDATION_ERROR`, `INVALID_PAGINATION`. Security failures return `UNAUTHORIZED` (401) or `FORBIDDEN` (403). **Framework-level errors are not yet guaranteed to have exactly the same representation.**

## Automatic contract checks

`OpenApiContractIntegrationTests` requests the generated JSON in an actual Spring MVC context and checks endpoints, response codes, bearer security and the idempotency header. The Docker Compose CI smoke script also fetches the generated contract from the live stack using real Keycloak JWTs.

Never edit the OpenAPI document separately from controller behavior. Update annotations and tests together. See the [engineering audit](engineering-review.md) for known limitations.
