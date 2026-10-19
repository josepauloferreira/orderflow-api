# ADR 0002: Make Flyway migrations the source of truth for database schema

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

OrderFlow uses PostgreSQL, Hibernate/JPA and Flyway. We want an explicit and reviewable database contract without maintaining two competing sources of DDL. Entities should express persistence mapping and domain behavior rather than generate the schema.

"Database-first" may refer to reverse-engineering Java classes from a pre-existing database. **That is not our plan.** We will follow a **schema-first, migration-driven** process: design the schema as SQL migrations, then implement entities that map to it.

## Decision

1. **Flyway owns the schema.** All tables, columns, constraints, foreign keys, indexes and future schema changes are created with reviewed, versioned SQL migrations in `src/main/resources/db/migration/`.
2. **Hibernate only validates.** Keep `spring.jpa.hibernate.ddl-auto=validate`. Hibernate must not create, update or drop the schema.
3. **JPA entities are mappings, not DDL definitions.** Retain necessary annotations such as `@Entity`, `@Table`, `@Id`, `@Column`, `@JoinColumn`, `@ManyToOne` and generation mappings where needed. Avoid redundant schema-defining or provider-specific DDL annotations merely to reproduce constraints already declared in SQL (for example `columnDefinition`, or duplicating every index).
4. **Constraints live in the database, invariants live in code.** Enforce primary/foreign keys, uniqueness, nullability and applicable `CHECK` constraints in PostgreSQL. Enforce authorization, transitions, inventory checks and other business invariants in explicit domain/application behavior. HTTP request validation remains on request DTOs with Jakarta Bean Validation. These are complementary guarantees, not substitutes.
5. **Keep entities encapsulated.** Use Lombok selectively (`@Getter`, protected no-arg constructors and constructor injection where relevant). Do not use blanket `@Data`, unrestricted `@Setter` or generated equality on mutable JPA entities.
6. **Test against the real engine.** Use PostgreSQL Testcontainers to verify migrations, constraints, mappings and transactions. An in-memory test database is not an adequate substitute.
7. **Migrations are immutable once shared/applied.** Do not edit an applied migration to make a correction; create a new migration. Keep data migration/backfill and rollout compatibility in mind if schema changes reach persistent environments.

## Development sequence for a new persisted feature

1. Define the domain rules, API contract and desired persisted data.
2. Write and review the Flyway SQL migration, including constraints and necessary indexes.
3. Implement minimal, explicit JPA mapping and domain methods to match the schema.
4. Add repositories, DTOs, mappers and services as required.
5. Test schema creation, persistence mapping, invalid data, business behavior and relevant concurrency paths against PostgreSQL.

## Consequences

- **Benefits:** precise PostgreSQL control; reviewable DDL; database protections beyond the Java service; leaner entity classes; reproducible schema evolution.
- **Trade-offs:** SQL and entity mappings still need to stay synchronized; schema changes take planning. `ddl-auto=validate` catches many mismatches but is not a complete verification of all constraints, defaults and indexes, so integration tests and migration reviews remain necessary.
- **Non-goals:** generating JPA entities mechanically from an existing database; removing all mapping annotations; moving all business rules into SQL or triggers.
