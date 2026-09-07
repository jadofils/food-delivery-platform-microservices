# MongoDB

## What it is
MongoDB is a document-oriented NoSQL database. In FDP it is the sole datastore for
`notification-service`, distinct from the relational databases used everywhere else.

## Why FDP uses it
- `notification-service` persists notification/audit records — who was notified, over which
  channel, and delivery status — which is domain data with a variable, event-shaped structure
  rather than a fixed relational schema (RULES.md §2, RULES.md §5).
- Keeps this data explicitly separate from operational/application logs, which are ephemeral,
  aggregated from stdout into Elasticsearch, and never written to a service's own database
  (RULES.md §5). Notification/audit records are permanent business data, queryable through
  `notification-service`'s own API — MongoDB backs that query surface, ELK does not.
- Database-per-service still applies: `notification_db` belongs only to `notification-service`,
  with no other service connecting to it directly (RULES.md §5).

## Where it's used

| Service | Database | Sprint introduced |
|---|---|---|
| `notification-service` | `notification_db` | Sprint 5 |

Sprint 5 is also when `notification-service` starts consuming domain events over RabbitMQ and
persisting the resulting audit record — done for `OrderPlacedEvent`/`OrderCancelledEvent`
(SPRINTS.md, Sprint 5); `DeliveryStatusUpdatedEvent` remains unconsumed since `delivery-service`
doesn't exist yet.

## How it's implemented in FDP
- `notification-service` declares `spring-boot-starter-data-mongodb` in its own `pom.xml`
  (RULES.md §4) — this dependency is unique to this service; no Postgres-backed service carries
  it.
- Connection is configured via `spring.data.mongodb.uri` (or host/port/database/credentials) in
  `notification-service`'s `application-{profile}.yml`, with the `docker` profile pointing at the
  Docker Compose service hostname rather than `localhost` (RULES.md §10).
- MongoDB is listed as one of the required backing infrastructure resources started by
  `docker-compose.yml` alongside PostgreSQL, RabbitMQ, and Redis (RULES.md §2, RULES.md §10).
- Notification/audit documents are written by the RabbitMQ consumer(s) in `notification-service`
  as domain events arrive; the collection is exclusively owned and queried by this service's own
  API, per the database-per-service rule (RULES.md §5).

## Getting started

**Status today:** Live and in real use. `notification-service` declares
`spring-boot-starter-data-mongodb`, connects to `notification_db`, and its RabbitMQ consumer
(`OrderEventListener`) persists a real `NotificationRecord` document on every
`OrderPlacedEvent`/`OrderCancelledEvent` it processes. Verified live: a real order placement and
cancellation, through a real running `order-service`, each produced exactly one document, visible
via `notification-service`'s own `GET /api/notifications/me`.

One gotcha worth knowing before connecting manually: `docker-compose.yml`'s
`MONGO_INITDB_ROOT_*` env vars create the root user in Mongo's **`admin`** authentication
database, not in `notification_db` itself — so any connection string needs
`?authSource=admin` even though the target database is `notification_db`. Omitting it produces an
authentication failure that looks like a wrong password, not a hint about the auth database.

### How to start it
From the repo root:
```
docker compose up -d mongodb
```
This alone (no `.env` file needed) starts a single MongoDB 7 container with the default root
credentials below. `notification_db` and its `notifications` collection are created lazily by
`notification-service` itself on first write — there's no init script for Mongo, unlike Postgres's
`docker/postgres/init-databases.sql` (Mongo has no equivalent multi-database bootstrap need here,
since exactly one service uses it).

### How to access it
- **Host/port:** `localhost:27017` (override via `MONGO_PORT` in a repo-root `.env` file — see
  `.env.example`).
- **Default credentials (local dev only):** root user `fdp`, password `fdp` — same values
  `docker-compose.yml` falls back to if no `.env` is present. Never used for anything but local
  development; production credentials come from environment injection (RULES.md §1 factor 3, §8).
- **From the host machine**, with `mongosh` installed:
  ```
  mongosh "mongodb://fdp:fdp@localhost:27017/notification_db?authSource=admin"
  ```
- **From inside the Docker network** (i.e. from another container), `notification-service`'s own
  `application-docker.yml` will point at the Docker service hostname, not `localhost`:
  `mongodb://mongodb:27017` (RULES.md §10).
- **Health:** `docker compose ps mongodb` shows `healthy` once `mongosh --quiet --eval
  "db.adminCommand('ping')"` succeeds.

### Endpoints it exposes
Not applicable in the REST sense — MongoDB exposes the standard Mongo wire protocol on port
`27017`, not HTTP. `notification-service` exposes its own REST API over this data (see
`docs/services/notification-service.md`) — MongoDB itself never does.

### Installation & dependencies
- Docker image: `mongo:7` (pinned in `docker-compose.yml`).
- `notification-service` declares `spring-boot-starter-data-mongodb` in its own `pom.xml`
  (RULES.md §4), plus `spring.data.mongodb.auto-index-creation=true` — without that property,
  `@Indexed(unique = true)` on `NotificationRecord.eventId` is never actually enforced by MongoDB
  itself (see `docs/services/notification-service.md`'s "Idempotent consumption" section for the
  real bug this was hiding).
- No local tool install is required to *run* MongoDB (it's fully containerized); installing the
  `mongosh` CLI on the host is optional, only useful for manual inspection.

### For newcomers
Run `docker compose up -d mongodb`, then connect with
`mongosh "mongodb://fdp:fdp@localhost:27017/notification_db?authSource=admin"` to confirm it's up
and browse `db.notifications.find()` for real notification records once you've placed an order
through `order-service` with `notification-service` also running. See `./postgresql.md` for the
platform's other datastore, and `./rabbitmq.md` for the events `notification-service` consumes and
persists here.

## Related
- RULES.md §2, RULES.md §5, RULES.md §6, RULES.md §10
- SPRINTS.md Sprint 5
- `./postgresql.md`
