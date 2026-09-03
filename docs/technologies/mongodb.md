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

Sprint 5 is also when `notification-service` starts consuming domain events (`OrderPlacedEvent`,
`DeliveryStatusUpdatedEvent`, etc.) over RabbitMQ and persisting the resulting audit record
(SPRINTS.md, Sprint 5).

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

## Related
- RULES.md §2, RULES.md §5, RULES.md §6, RULES.md §10
- SPRINTS.md Sprint 5
- `./postgresql.md`
