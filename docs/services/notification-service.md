# notification-service

## Responsibility
Consumes domain events and persists the notification/audit log (RULES.md §2). Today it consumes
`OrderPlacedEvent`/`OrderCancelledEvent` from `order-service` and persists a permanent record of
what was sent, to whom, over which channel, and its delivery status (RULES.md §5,
`notification_db`). `DeliveryStatusUpdatedEvent` (from `delivery-service`, Sprint 5, not built
yet) is planned but not wired — there is no publisher for it yet.

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
every Postgres-backed service per RULES.md §5) does not apply here. A single collection,
`notifications`, holding `NotificationRecord` documents.

## API surface

**Done and verified live.** notification-service exposes no write endpoint of its own — every
record it holds was created by consuming a real RabbitMQ event, never by a direct REST call
(RULES.md §6). OpenAPI/Swagger UI at `/swagger-ui/index.html`.

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/notifications/me` | authenticated (any role) | Self-service — the caller's own notifications only, resolved from their Keycloak subject, never a client-supplied id. No dedicated permission gates it, matching the same "self-service needs only authentication" pattern used elsewhere once no non-admin seeded role carries a more specific permission. |
| `GET /api/notifications` | `notification:read` | Every notification, any recipient. Only `ADMIN` holds this permission (`docker/keycloak/fdp-realm.json`). |

A Postman collection covering both rows above, plus the events that produce the data in the first
place, is checked in at `postman/FDP-notification-service.postman_collection.json` (14 requests,
19 assertions, Newman-verified).

## Async consumption (RULES.md §6, §9 factor 9)

`OrderEventListener` consumes off `notification-service.order-events`, a queue this service
declares and owns exclusively — bound to `order-service`'s `fdp.order-events` topic exchange with
pattern `order.#`, never sharing a queue with any other consumer. A dedicated dead-letter queue
(`notification-service.order-events.dlq`) is wired purely through queue arguments
(`x-dead-letter-exchange`/`x-dead-letter-routing-key`) plus
`spring.rabbitmq.listener.simple.retry.*` — no custom recovery code needed. `order-service`'s
events are the shared wire contract (`common.event.OrderPlacedEvent`/`OrderCancelledEvent`), so
neither side redefines the payload shape independently.

The listener accepts the raw `org.springframework.amqp.core.Message` and converts it explicitly
via the injected `MessageConverter`, rather than declaring a concrete/`Object`-typed parameter for
`@RabbitListener` to convert automatically — a generic `Object` parameter doesn't give Spring's
listener adapter enough type information to know it should convert at all, and it silently hands
the method the untouched `Message` instead. This was caught by this service's own integration test,
not discovered in production.

### Idempotent consumption — a real bug, not just a design note

RabbitMQ guarantees at-least-once delivery, not exactly-once, so a redelivered event must not
produce two notifications. The first implementation checked
`NotificationRecordRepository.findByEventId(eventId).isPresent()` before every save and skipped if
already present — a plain find-then-save with no locking. This is **not** a correctness guarantee
by itself: it's a textbook time-of-check-to-time-of-use gap, since nothing stops two
near-simultaneous deliveries from both passing the check before either has saved.

Chasing exactly this concern down surfaced a second, unrelated issue during diagnosis: this
machine's Docker/Testcontainers setup was carrying MongoDB data forward across what should have
been fresh, isolated containers between separate test runs — polluting a test that counted
"total notifications for a hardcoded customer id" across runs that were never actually independent.
That was a test-hygiene bug (fixed by scoping the assertion to the run's own freshly-random
`eventId` via a new `countByEventId` query instead of a shared literal), not a production
concern — but it delayed finding the real one.

The actual fix, once isolated: `NotificationRecord.eventId` carries a unique index
(`@Indexed(unique = true)`), and `spring.data.mongodb.auto-index-creation=true` is now set
explicitly — without it, Spring Data MongoDB never actually creates that index, and
`@Indexed(unique = true)` is silently unenforced. `OrderEventListener` now treats a losing
concurrent write's `DuplicateKeyException` as "already processed, skip" — the database's own
uniqueness constraint is the real guarantee; the `findByEventId` check that runs first is kept
purely as a fast-path optimization to skip an unnecessary write on the common (non-concurrent)
redelivery case, not relied on for correctness.

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka client registration — verified live), its own
  `notification_db` MongoDB instance, and RabbitMQ (consume only). **Not yet wired:**
  `config-server` integration — same documented scope cut as every other service so far.
- **Depended on by:** no other service calls `notification-service` synchronously; it is purely an
  event consumer and a query API for its own audit data.

## Delivered in
Sprint 5 — "Delivery, events, and notifications" (SPRINTS.md), the notification half only —
`delivery-service` and `DeliveryStatusUpdatedEvent` consumption remain not built. Exit criteria met
for this half: a real order placement and cancellation, through a real running `order-service`,
each produced exactly one notification record, visible via `GET /api/notifications/me` within
about two seconds. 5 Testcontainers-backed tests (MongoDB + RabbitMQ) all pass, including one that
deliberately redelivers the same event twice and asserts exactly one record results.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §9 factor 9
  (idempotent consumers)
- SPRINTS.md — Sprint 5
- [`./order-service.md`](./order-service.md) — publishes `OrderPlacedEvent`/`OrderCancelledEvent`,
  consumed by this service
- [`./delivery-service.md`](./delivery-service.md) — will publish `DeliveryStatusUpdatedEvent`,
  a planned but not-yet-consumed event, once built
- [`../technologies/mongodb.md`](../technologies/mongodb.md), [`../technologies/rabbitmq.md`](../technologies/rabbitmq.md)
