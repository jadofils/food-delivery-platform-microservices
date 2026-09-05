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

**Status today:** Live and verified — `customer-service` (Sprint 2) has two real migrations,
`V1__create_customers_table.sql` and `V2__create_addresses_table.sql`, under its own
`src/main/resources/db/migration`. Confirmed running against a real Postgres, both via
Testcontainers-backed tests and a live `customer-service` instance (`flyway_schema_history` in
`customer_db` shows both applied). `restaurant-service`, `order-service`, and `delivery-service`
are still bare skeletons (`spring-boot-starter` + `spring-boot-starter-test` only) with no
`spring-boot-starter-data-jpa`, no `flyway-core`, and no migrations of their own yet. One Boot
4.1-specific gotcha worth flagging: `flyway-core` alone does **not** get Flyway to run
automatically anymore — the autoconfiguration moved to a separate `spring-boot-flyway` module,
which `customer-service/pom.xml` now declares explicitly (discovered the same way
`config-server`'s `@EnableConfigServer` import surprise was — see `SPRINTS.md` Sprint 1).

### How to start it
There's nothing separate to start. Flyway activates automatically as part of a service's own
`spring-boot:run` (or its packaged jar's startup) — confirmed live for `customer-service`:
`./mvnw -pl customer-service -am spring-boot:run` (or run its packaged jar) applies both
migrations against `customer_db` on startup, logged as `Migrating schema "public" to version
"1 - create customers table"` / `"2 - create addresses table"`. It runs against the `postgres`
container documented in `./postgresql.md`, so that container must be up first
(`docker compose up -d postgres`).

### How to access it
There's no separate access point — Flyway isn't a running service you connect to. Inspect its
effect via `psql` against the relevant database's `flyway_schema_history` table:
```
psql -h localhost -U fdp -d customer_db -c "select * from flyway_schema_history;"
```
For `customer_db` today this shows two applied rows (versions 1 and 2). Every other
Postgres-backed service's database has no such table yet, since no migration has run against it.

### Endpoints it exposes
None — Flyway is a startup-time library, not a running service. It has no port, no HTTP surface,
and no protocol of its own.

### Installation & dependencies
- `customer-service/pom.xml` declares `flyway-core` **and** `spring-boot-flyway` (see the Boot 4.1
  note above) — every other Postgres-backed service will do the same once built (RULES.md §4),
  versioned via the root aggregator's `dependencyManagement`, never pinned per-service.
- No local tool install is needed; Flyway ships as a library dependency and runs inside the JVM
  that's already starting the service, via Spring Boot's auto-configuration. No separate Flyway
  CLI is used in FDP.

### For newcomers
`customer-service` is the one to look at: its
`src/main/resources/db/migration/V1__create_customers_table.sql` /
`V2__create_addresses_table.sql` are real, applied migrations — read them alongside
`customer-service/src/main/java/.../entity/Customer.java` and `Address.java` to see the schema and
the JPA mapping side by side. Start it via `./mvnw -pl customer-service -am spring-boot:run` and
watch the `Flyway`-prefixed startup log lines, or query `flyway_schema_history` afterward as shown
above. See `./postgresql.md` for the database Flyway migrates, and RULES.md §5 for why each
service's migration history stays isolated even though several services share one Postgres server
container locally.

## Related
- RULES.md §1 (factor 5, factor 12), RULES.md §4, RULES.md §5
- SPRINTS.md Sprint 1, Sprint 2, Sprint 3, Sprint 5
- `./postgresql.md`
