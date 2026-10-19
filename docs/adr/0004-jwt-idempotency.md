# ADR 0004: OAuth2 Resource Server and transactional order idempotency

- **Status:** Accepted
- **Date:** 2026-10-19

## Decision

- Rely on an external OpenID Connect identity provider instead of issuing JWTs ourselves.
- Use Spring Security as an OAuth2 Resource Server. Enforce trusted issuer, audience `orderflow-api`, token signature and expiry via the resource server decoder; no local token secrets.
- Accept only the allowlisted `ADMIN` and `CUSTOMER` roles from Keycloak-compatible `realm_access.roles`. Other realm roles and arbitrary `scope` claims do not grant those permissions.
- Let anonymous clients read the public product catalog; require `ADMIN` for catalog mutations and inventory, `CUSTOMER` for order creation; allow the owner or `ADMIN` to access/cancel existing orders.
- Persist a stable authenticated `sub` identifier on each order. Never trust a customer ID supplied by the client.
- Require an `Idempotency-Key` header (8–128 ASCII characters) for order creation. Namespace keys by `(customer_subject, key)`; hash the canonical product UUID/quantity pairs using SHA-256 (sorted by UUID, protocol version included). Identical retries return the current order resource without consuming stock again; the same key with different content is HTTP 409.
- Serialize concurrent retries using a PostgreSQL unique key insert with `ON CONFLICT DO NOTHING` and transactional row locking. Insert the key, create the order, debit inventory and attach the order ID **in the same database transaction**. A rolled-back attempt leaves no committed idempotency record.
- The original response returns HTTP **201**, a replay HTTP **200**, both with the same order ID and Location. A replay may show current state (for example, a cancelled order), rather than a frozen historical HTTP response.

## Boundaries

- The token `sub` must be stable and scoped to a trusted issuer; changing identity-provider realms or subject semantics requires a migration plan.
- Keep an administrative retention/cleanup policy for old idempotency keys before allowing indefinite production operation. Purging the key permits a subsequent identical request to create a new order and must be coordinated with clients.
- The committed migration V5 deliberately aborts if legacy orders without owner IDs exist. Real upgrades require explicit verified ownership backfill, **never an arbitrary default customer**.
- API authentication and authorization are tested with mock JWT principals, not a live token-signature handshake. Local Keycloak smoke tests must be performed separately.
- Local Keycloak credentials and Resource Owner Password (direct grants) are **demonstration-only**. Real clients should use Authorization Code with PKCE.

## Tests

Cover unauthenticated endpoints (401), forbidden role access (403), owner/admin access, same-key replay, different-payload conflict, per-user key namespaces and concurrent duplicate submissions.
