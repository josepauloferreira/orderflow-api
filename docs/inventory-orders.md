# Inventory and orders

This milestone implements a transactional inventory ledger and synchronous order confirmation without payments.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/products/{productId}/stock` | Read available stock (zero before first adjustment) |
| POST | `/api/v1/products/{productId}/stock/adjustments` | Signed nonzero delta and mandatory reason |
| POST | `/api/v1/orders` | Confirm order, snapshot prices and consume stock (201) |
| GET | `/api/v1/orders/{id}` | Retrieve an order with historical line snapshots |
| POST | `/api/v1/orders/{id}/cancel` | Cancel and restore stock once (204) |

Example stock adjustment: `{"delta":8,"reason":"Initial receipt"}`.

Example order: `{"items":[{"productId":"<uuid>","quantity":2}]}`.

Each order has 1–20 distinct products with quantities of 1–1000. Duplicate lines are rejected rather than combined. Inactive products cannot be ordered. Insufficient stock returns HTTP 409 with `code: INSUFFICIENT_STOCK`.

## Consistency model

The product row is the PostgreSQL locking anchor. All stock mutations lock product rows, ordered ascending by UUID for multi-product transactions. This works even before the first inventory row exists. PostgreSQL CHECK constraints prevent negative inventory; movements are inserted atomically with their effects.

The order stores product SKU/name/unit price snapshots so catalog changes do not rewrite purchase history. Repeated cancellation returns 204 without issuing another credit.

See [ADR 0003](adr/0003-stock-consistency.md) for the detailed trade-offs.

## Limitations

**No authentication/authorization exists yet. Do not expose these endpoints publicly.** This phase has no payments or deliveries. Retrying `POST /api/v1/orders` can still create a second order if enough stock exists; request idempotency is planned for the next milestone.
