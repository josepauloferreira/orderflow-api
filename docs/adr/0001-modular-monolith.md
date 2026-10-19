# ADR 0001: Organize as a modular monolith

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

OrderFlow is a learning/reference project whose primary value is implementing and testing real order, inventory and catalog invariants. Early distribution across independent services would introduce network calls, deployment complexity and distributed consistency concerns before they are justified by domain needs.

## Decision

Use one deployable Spring Boot application and one PostgreSQL database. Organize business features into `catalog`, `inventory`, `ordering` and shared technical support under `common`, adding each package only when behavior exists.

Within a feature, separate HTTP contracts, application orchestration, domain rules and persistence where that separation prevents coupling. Avoid mandatory interfaces, ports or layers without a concrete reason.

## Consequences

- Benefits: local transactions, simple development/debugging, fewer moving parts, easier integration tests and clear ownership by feature.
- Trade-off: the application is deployed as a unit. Boundaries must be kept explicit to prevent cross-feature coupling.
- Revisit this decision only if measured operational or team constraints justify deployment separation.
