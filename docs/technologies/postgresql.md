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

## Getting started

**Status today:** The container is live (part of the Sprint 0 `docker-compose.yml` skeleton) — but
no service reads or writes to it yet. `customer-service`, `restaurant-service`, `order-service`,
and `delivery-service` are all still bare skeletons (`spring-boot-starter` +
`spring-boot-starter-test` only) with no `spring-boot-starter-data-jpa`, no datasource config, and
no Flyway migrations. The first real consumer is `customer-service`/`restaurant-service`, Sprint 2.
Keycloak *does* use this container today (`keycloak_db`), but as external infrastructure managing
its own schema — not one of FDP's own service databases (RULES.md §8).

### How to start it
From the repo root:
```
docker compose up -d postgres
```
This alone (no `.env` file needed) starts a single Postgres 17 container with the default
credentials below. On first start, `docker/postgres/init-databases.sql` runs automatically and
creates one logical database per service, plus `keycloak_db` for Keycloak.

### How to access it
- **Host/port:** `localhost:5432` (override via `POSTGRES_PORT` in a repo-root `.env` file — see
  `.env.example`).
- **Default credentials (local dev only):** user `fdp`, password `fdp`, default database `fdp` —
  same values `docker-compose.yml` falls back to if no `.env` is present. Never used for anything
  but local development; production credentials come from environment injection (RULES.md §1
  factor 3, §8).
- **From the host machine**, with the `psql` client installed:
  ```
  psql -h localhost -U fdp -d customer_db
  ```
  (password `fdp` when prompted, or set `PGPASSWORD=fdp` to skip the prompt).
- **From inside the Docker network** (i.e. from another container), a service's own
  `application-docker.yml` points at the Docker service hostname, not `localhost`:
  `jdbc:postgresql://postgres:5432/customer_db` (RULES.md §10).
- **Health:** `docker compose ps postgres` shows `healthy` once `pg_isready` succeeds — every
  dependent service's `depends_on: condition: service_healthy` waits on exactly this.

### Endpoints it exposes
Not applicable in the REST sense — Postgres exposes the standard PostgreSQL wire protocol on port
`5432`, not HTTP. What each service exposes *on top of* its Postgres-backed data (its own REST API)
is documented in that service's own `docs/services/<service-name>.md`, once built.

### Installation & dependencies
- Docker image: `postgres:17-alpine` (pinned in `docker-compose.yml`).
- Each Postgres-backed service will declare `spring-boot-starter-data-jpa` in its own `pom.xml`
  once built (RULES.md §4) — not yet present in any service's POM today. The JDBC driver itself
  comes transitively via `spring-boot-starter-data-jpa`'s Postgres auto-configuration, no separate
  driver dependency needed.
- No local tool install is required to *run* Postgres (it's fully containerized); installing the
  `psql` CLI client on the host is optional, only useful for manual inspection.

### For newcomers
Run `docker compose up -d postgres`, then connect with `psql -h localhost -U fdp -d fdp` (or any
of the per-service databases `init-databases.sql` created — check that file for the exact list)
to confirm it's up and the expected databases exist (`\l` lists them). There's nothing to query
yet: no service has created a single table, since Flyway migrations don't exist until Sprint 2.
This container existing and being healthy is the Sprint 0 exit criterion being satisfied, not a
sign anything domain-specific is running yet. See `./flyway.md` for how schemas will actually get
created, and `./mongodb.md` for the platform's other datastore (`notification-service`).

## Related
- RULES.md §1 (factor 4, factor 10, factor 12), RULES.md §2, RULES.md §5, RULES.md §9, RULES.md §10
- SPRINTS.md Sprint 0, Sprint 1, Sprint 2, Sprint 3, Sprint 5
- `./flyway.md`
- `./mongodb.md`
