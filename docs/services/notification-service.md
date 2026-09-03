# notification-service

## Responsibility
Consumes domain events and dispatches notifications, persisting the notification/audit log
(RULES.md §2). It is designed to consume `OrderPlacedEvent`, `OrderCancelledEvent`, and
`DeliveryStatusUpdatedEvent` from RabbitMQ and persist a permanent record of what was sent, to
whom, over which channel, and its delivery status (RULES.md §5, `notification_db`).

## Why it's a separate service
This is scope added beyond `ReadMe.md`'s original four services (RULES.md, opening section) —
`ReadMe.md`'s monolith combines delivery and notification into one domain, but RULES.md splits
notification dispatch out into its own service because it has a distinct data shape (audit/log
records, not delivery-tracking state) and a distinct datastore need (document storage suits
variable notification payloads better than a relational schema). Per RULES.md §5, notification/
audit records are explicitly business data, not operational logs — they must never be conflated
with the ELK stack's ephemeral, centrally-aggregated stdout logs. Keeping this as its own service
means it can be queried directly through its own API without coupling to `delivery-service`'s or
`order-service`'s release cycle.

## Database
MongoDB, `notification_db` (RULES.md §2). This is the one non-Postgres, non-Flyway datastore in
the inventory — document storage fits variable notification-channel payloads, and Flyway (used by
every Postgres-backed service per RULES.md §5) does not apply here.

## API surface (planned)
Exposes REST endpoints to query persisted notification/audit records (who was notified, over which
channel, and delivery status), satisfying the "notification records are queryable via
`notification-service`'s API" exit criterion of Sprint 5 (SPRINTS.md). It does not expose an
endpoint for triggering notifications directly — dispatch is driven entirely by consuming domain
events off RabbitMQ, not by synchronous REST calls from other services (RULES.md §6). An OpenAPI
spec for this surface is planned under `docs/api-contracts/` (RULES.md §9).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka registration), `config-server` (externalized
  config), its own `notification_db` MongoDB instance, and RabbitMQ. It consumes
  `OrderPlacedEvent` and `OrderCancelledEvent` from `order-service` and `DeliveryStatusUpdatedEvent`
  from `delivery-service` — idempotent consumers (dedupe on event ID) each with a dead-letter
  queue, per RULES.md §6 and §9 (factor 9).
- **Depended on by:** no other service calls `notification-service` synchronously; it is purely an
  event consumer and a query API for its own audit data.

## Delivered in
Sprint 5 — "Delivery, events, and notifications" (SPRINTS.md). Exit criteria require notification
records to be queryable via `notification-service`'s API.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules)
- SPRINTS.md — Sprint 5
- [`./delivery-service.md`](./delivery-service.md) — publishes `DeliveryStatusUpdatedEvent`, one
  of this service's consumed events
- [`./order-service.md`](./order-service.md) — publishes `OrderPlacedEvent`/`OrderCancelledEvent`,
  also consumed by this service
