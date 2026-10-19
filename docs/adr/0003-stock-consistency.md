# ADR 0003: Serialize stock mutations with ordered PostgreSQL row locks

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

Concurrent orders may attempt to consume the last stock unit, causing overselling if reads and writes are not synchronized. Inventory rows are lazily initialized, so a stock row may not exist during the first adjustment.

## Decision

- Use one PostgreSQL transaction at READ COMMITTED for order creation and cancellation.
- Acquire a pessimistic write lock on the **product row** before reading or changing stock, including before the inventory row exists.
- Acquire multiple product locks in ascending UUID order to minimize deadlocks. Cancellation acquires an order lock first, then product locks in the same UUID order.
- All order creation effects (order, line snapshots, inventory decrement, movement ledger) commit or roll back together.
- An order may transition only from CONFIRMED to CANCELLED. Repeating cancellation must not create a second stock-restoration movement.
- Database constraints ensure nonnegative stock and prevent duplicate (order, product, movement-kind) entries.
- Internal stock operations require an existing transaction; they are not public standalone mutations.

## Consequences

This design prioritizes correctness over throughput for highly contended products. Locking order reduces but cannot eliminate all possible deadlocks from future queries. No automatic retry is implemented without a defined idempotency policy.

Customer identity, authentication, authorization and HTTP request idempotency are deferred. **Do not deploy these endpoints publicly until security is implemented.**

## Verification

Use PostgreSQL Testcontainers tests for rollback, inactive products, duplicate items, double cancellation, immutable price snapshots and two concurrent orders competing for the final stock unit.
