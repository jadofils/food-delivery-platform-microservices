# PostgreSQL

## What it is
PostgreSQL is an open-source relational database. In FDP it is the datastore for every service
whose domain data is naturally relational and transactional.

## Why FDP uses it
- Required by the base assignment for `customer_db`, `restaurant_db`, `order_db`, `delivery_db`
  (ReadMe.md, Technical Requirements / Epic 1) — the monolith-to-microservices migration keeps
  Postgres as the relational store for these four domains.
- Enforces database-per-service isolation: each of `customer-service`, `restaurant-service`,
  `order-service`, and `delivery-service` owns its own database with no cross-service joins and no
  shared tables (RULES.md §5). Keycloak also uses this Postgres instance (`keycloak_db`), but as
  infrastructure it manages itself — not one of FDP's own service databases (RULES.md §8).
- Dev/prod parity requires the real engine at every test level — no H2 or embedded substitute,
  even in unit/integration tests (RULES.md §9, RULES.md §1 factor 10).
- Schema changes are managed exclusively through Flyway migrations against Postgres, never by
  hand-run SQL (RULES.md §1 factor 12, RULES.md §5).

## Where it's used

| Service | Database | Sprint introduced |
|---|---|---|
| `customer-service` | `customer_db` | Sprint 2 |
| `restaurant-service` | `restaurant_db` | Sprint 2 |
| `order-service` | `order_db` | Sprint 3 |
| `delivery-service` | `delivery_db` | Sprint 5 |

The `docker-compose.yml` skeleton with the Postgres container is stood up in Sprint 0, ahead of
any service that depends on it (SPRINTS.md, Sprint 0).

## How it's implemented in FDP
- Each Postgres-backed service declares `spring-boot-starter-data-jpa` in its own `pom.xml`
  (RULES.md §4) — no service outside this list (e.g. `api-gateway`) carries this dependency.
- Connection is configured via `spring.datasource.url` / `username` / `password` in each service's
  `application-{profile}.yml`; credentials and hostnames are never hardcoded (RULES.md §1 factor
  3, factor 4). The `docker` profile (`application-docker.yml`) points at the Docker service
  hostname, e.g. `jdbc:postgresql://postgres:5432/order_db` (RULES.md §10).
- Locally, all five Postgres-backed services share one Postgres **server** container
  (`docker-compose` service name `postgres`) provisioned with five logical databases via init
  scripts, but each service's datasource credentials, connection pool, and Flyway migration
  history stay fully isolated (RULES.md §5). A production topology can split these onto separate
  managed instances without any code change.
- Schema ownership per service is enforced through Flyway migrations under
  `src/main/resources/db/migration` (see `./flyway.md`) — Postgres itself has no cross-service
  credentials or shared schema.
- Health checks are mandatory on the Postgres container so dependent services only start once it
  reports healthy (`depends_on: condition: service_healthy`, RULES.md §10).

## Related
- RULES.md §1 (factor 4, factor 10, factor 12), RULES.md §2, RULES.md §5, RULES.md §9, RULES.md §10
- SPRINTS.md Sprint 0, Sprint 1, Sprint 2, Sprint 3, Sprint 5
- `./flyway.md`
- `./mongodb.md`
