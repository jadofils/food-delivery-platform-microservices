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
| `customer-service` | `src/main/resources/db/migration` | Sprint 2 |
| `restaurant-service` | `src/main/resources/db/migration` | Sprint 2 |
| `order-service` | `src/main/resources/db/migration` | Sprint 3 |
| `delivery-service` | `src/main/resources/db/migration` | Sprint 5 |

Keycloak's own schema (`keycloak_db`, RULES.md §8) is explicitly **not** one of these — Keycloak
manages it internally; no FDP Flyway migration ever touches it.

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

## Getting started

**Status today:** Nothing to run — Flyway isn't a container or standalone service, it's a library
that runs embedded inside each Postgres-backed service's own JVM on startup. `customer-service`,
`restaurant-service`, `order-service`, and `delivery-service` are all still bare skeletons
(`spring-boot-starter` + `spring-boot-starter-test` only, per each module's own `pom.xml`), with no
`spring-boot-starter-data-jpa`, no `flyway-core`, and no migration files under
`src/main/resources/db/migration` in any of them. This is planned — Sprint 2 onward, not yet
implemented.

### How to start it
There's nothing separate to start. Flyway activates automatically as part of a service's own
`spring-boot:run` (or its packaged jar's startup), once that service has both
`spring-boot-starter-data-jpa` and `flyway-core` on its classpath plus actual migration files under
`src/main/resources/db/migration` — none of which exist yet, in any service. It runs against the
`postgres` container documented in `./postgresql.md`, so that container must be up first
(`docker compose up -d postgres`), but there is no separate Flyway process to launch.

### How to access it
There's no separate access point — Flyway isn't a running service you connect to. Once migrations
exist, you inspect its effect via `psql` against the relevant database's `flyway_schema_history`
table, e.g.:
```
psql -h localhost -U fdp -d customer_db -c "select * from flyway_schema_history;"
```
Today that table doesn't exist in any database, because no migration has ever run.

### Endpoints it exposes
None — Flyway is a startup-time library, not a running service. It has no port, no HTTP surface,
and no protocol of its own.

### Installation & dependencies
- Every Postgres-backed service will declare `flyway-core` in its own `pom.xml` once built
  (RULES.md §4), versioned via the root aggregator's `dependencyManagement`, never pinned
  per-service — not present in any service's POM today.
- No local tool install is needed; Flyway ships as a library dependency and runs inside the JVM
  that's already starting the service, via Spring Boot's auto-configuration. No separate Flyway
  CLI is used in FDP.

### For newcomers
There's nothing to run in isolation here — Flyway only makes sense once a service has both
`spring-boot-starter-data-jpa` and real migration files, which is Sprint 2 (`customer-service`,
`restaurant-service`) at the earliest. Once that exists, starting that service via
`./mvnw -pl <module> -am spring-boot:run` is enough to see Flyway apply its migrations
automatically — check the startup logs for `Flyway` lines, or query `flyway_schema_history`
afterward as shown above. See `./postgresql.md` for the database Flyway migrates, and RULES.md §5
for why each service's migration history stays isolated even though today several services will
eventually share one Postgres server container locally.

## Related
- RULES.md §1 (factor 5, factor 12), RULES.md §4, RULES.md §5
- SPRINTS.md Sprint 1, Sprint 2, Sprint 3, Sprint 5
- `./postgresql.md`
