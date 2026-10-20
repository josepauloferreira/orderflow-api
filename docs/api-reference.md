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
| GET | `/api/v1/products/{id}` | Public; response includes strong ETag | 200 |
| POST | `/api/v1/products` | ADMIN; validated product body | 201 + Location |
| PUT | `/api/v1/products/{id}` | ADMIN; strong If-Match required; SKU immutable | 200 / 412 / 428 |
| POST | `/api/v1/products/{id}/deactivate` | ADMIN; strong If-Match required | 204 / 412 / 428 |
| GET | `/api/v1/products/{productId}/stock` | ADMIN | 200 |
| POST | `/api/v1/products/{productId}/stock/adjustments` | ADMIN; signed delta and reason | 200 |
| POST | `/api/v1/orders` | CUSTOMER; Idempotency-Key | 201 original / 200 replay |
| GET | `/api/v1/orders/{id}` | Owner or ADMIN | 200 |
| POST | `/api/v1/orders/{id}/cancel` | Owner or ADMIN; idempotent | 204 |

### Conditional product edits (optimistic locking)

The `GET /api/v1/products/{id}` response contains a strong `ETag` header such as `"0"`. `POST /api/v1/products` also returns an ETag. Supply that exact value when changing or deactivating the product:

```http
PUT /api/v1/products/<product-uuid>
Authorization: Bearer <admin-token>
If-Match: "0"
Content-Type: application/json

{"name":"New name","description":null,"price":119.90}
```

A successful PUT returns `200 OK` and a **new ETag**. An old version fails with `412 STALE_PRODUCT_VERSION`, a missing header with `428 PRECONDITION_REQUIRED` and a weak/malformed value with `400 INVALID_IF_MATCH`. Deactivation is idempotent when repeated with the *current* ETag, and does not change version if already inactive. An outdated ETag still fails with 412, as required by conditional-request semantics. PostgreSQL `V7` stores a numeric optimistic-lock column; Hibernate `@Version` guards against races between the version check and transaction flush.

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

Domain and common MVC errors are handled by one RFC 9457 Problem Details advice with a stable `code` extension: `DUPLICATE_SKU`, `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `INSUFFICIENT_STOCK`, `PRODUCT_INACTIVE`, `IDEMPOTENCY_CONFLICT`, `VALIDATION_ERROR`, `INVALID_PAGINATION`. Security failures return `UNAUTHORIZED` (401) or `FORBIDDEN` (403). Common framework errors including malformed JSON, invalid path types, unsupported methods/media types, and missing headers now use Problem Details. Unknown/unhandled failures still follow Spring's default 500 path and are deliberately not wrapped by a broad catch-all.

## Automatic contract checks

`OpenApiContractIntegrationTests` requests the generated JSON in an actual Spring MVC context and checks endpoints, response codes, bearer security and the idempotency header. The Docker Compose CI smoke script also fetches the generated contract from the live stack using real Keycloak JWTs.

Never edit the OpenAPI document separately from controller behavior. Update annotations and tests together. See the [engineering audit](engineering-review.md) for known limitations.
