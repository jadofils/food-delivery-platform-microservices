# Flyway

## What it is
Flyway is a schema migration tool for relational databases. In FDP it is the exclusive mechanism
for evolving each Postgres-backed service's schema.

## Why FDP uses it
- Schema migrations are explicitly required to run as one-off, codebase-tracked processes, never
  as manual SQL run by hand against a live database (RULES.md §1 factor 12).
- Every Postgres-backed service owns its schema via Flyway migrations, keeping schema ownership
  aligned with the database-per-service rule — no shared migration history across services even
  when they share a Postgres server container locally (RULES.md §5).
- Migrations are additive and forward-only on `main`; a mistake is fixed with a new migration
  rather than editing a merged one, which keeps schema history reproducible across environments
  the same immutable image is promoted through (RULES.md §5, RULES.md §1 factor 5).

## Where it's used

| Service | Migration path | Sprint introduced |
|---|---|---|
| `identity-service` | `src/main/resources/db/migration` | Sprint 1 |
| `customer-service` | `src/main/resources/db/migration` | Sprint 2 |
| `restaurant-service` | `src/main/resources/db/migration` | Sprint 2 |
| `order-service` | `src/main/resources/db/migration` | Sprint 3 |
| `delivery-service` | `src/main/resources/db/migration` | Sprint 5 |

`identity-service`'s baseline Flyway migration is called out explicitly as part of Sprint 1's
scope (SPRINTS.md, Sprint 1).

## How it's implemented in FDP
- Every Postgres-backed service declares the `flyway-core` dependency in its own `pom.xml`
  (RULES.md §4), versioned via the root aggregator's `dependencyManagement`, never pinned
  per-service.
- Migration scripts live under each service's own `src/main/resources/db/migration` (RULES.md
  §5), following Flyway's versioned naming convention (`V1__..., V2__...`).
- Flyway runs automatically on service startup via Spring Boot's auto-configuration, against the
  same datasource config (`spring.datasource.*`) the service itself uses — satisfying factor 12's
  requirement that migrations run with the same codebase and config as the service (RULES.md §1
  factor 12).
- Migrations are additive/forward-only on `main`; nothing edits a migration that has already
  merged (RULES.md §5).
- Because each service's Flyway migration history is isolated even when services share one
  Postgres server container locally, one service's migrations never touch another service's
  schema (RULES.md §5).

## Related
- RULES.md §1 (factor 5, factor 12), RULES.md §4, RULES.md §5
- SPRINTS.md Sprint 1, Sprint 2, Sprint 3, Sprint 5
- `./postgresql.md`
