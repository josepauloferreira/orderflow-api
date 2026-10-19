# Catalog: SQL-first products

The product catalog is the first executable business slice of OrderFlow. It deliberately combines a small HTTP API with strong domain invariants and an independently constrained database schema.

## API

- `POST /api/v1/products`: accepts `sku`, `name`, optional `description`, and `price`; returns **201 Created**, a `Location` header and `ProductResponse`.
- `GET /api/v1/products/{id}`: returns the product or **404**.
- `GET /api/v1/products`: optional `active=true|false`, `page` (default `0`) and `size` (default `20`, range `1..100`). Ordered by creation time descending, then ID; returns a stable `PagedResponse` record instead of serializing Spring Data's `PageImpl` directly.
- `PUT /api/v1/products/{id}`: fully replaces the editable subset (`name`, `description`, `price`). SKU is immutable and excluded from this request contract.
- `POST /api/v1/products/{id}/deactivate`: an idempotent domain command with **204 No Content**.

No authorization has been implemented **yet**. The API is not intended for public deployment until the security milestone.

## Example creation

```http
POST /api/v1/products
Content-Type: application/json

{
  "sku": "kb-100",
  "name": "Mechanical Keyboard",
  "description": "Quiet switches",
  "price": 129.90
}
```

The server normalizes SKU to `KB-100`, trims names/descriptions and returns the generated UUID. A repeated SKU (regardless of letter case) returns **409** with an RFC 9457 Problem Detail and `code: DUPLICATE_SKU`. Request validation failures return **400** with an array of field violations.

## Persistence boundaries

`V1__create_products.sql` is the only schema definition for the product table. PostgreSQL enforces:

- UUID primary key, unique uppercase SKU with restricted syntax;
- non-empty product name;
- positive `NUMERIC(12,2)` price;
- required status and timestamp columns.

Java entity fields map to this schema with minimal JPA annotations and no public setters. The `Product` factory and domain commands validate meaningful invariants; the HTTP records use Jakarta Bean Validation for client-facing errors.

A constraint name is checked when translating a duplicate SKU into HTTP 409. Other database integrity exceptions are **not** mislabeled as duplicate SKU.

The product entity is intentionally **not** a generic CRUD data bag: `create`, `updateDetails` and `deactivate` express legal mutations.

## Explicit choices and trade-offs

1. **No generated DDL:** `ddl-auto=validate` checks mapping, while Flyway owns structure.
2. **No premature generic service/repository layers:** Spring Data already provides the generic persistence contract.
3. **No `@Data` on JPA entities:** Lombok getters and a protected constructor preserve encapsulation.
4. **Mapper contract checked at compile time:** MapStruct uses `unmappedTargetPolicy=ERROR` for response mapping, failing the build if a response property cannot be mapped.
5. **Stable pagination contract:** callers are insulated from internal Spring Data JSON serialization details.
6. **Test real behavior:** PostgreSQL Testcontainers covers migrations, DB constraints and HTTP semantics, complemented by fast pure-domain tests.

## Deferred work

Optimistic concurrency for product editing (for example, an ETag / `If-Match` contract), security, order/inventory rules and full OpenAPI integration are deliberately excluded from this slice. Before allowing concurrent administrative writes in production, a clear lost-update policy must be established.
