# delivery-service

## Responsibility
Owns delivery assignment and tracking (RULES.md §2). It is designed to hold the delivery-tracking
half of the data and business logic decomposed from the monolith's Delivery and Notification
domain — the notification-dispatch half of that monolith domain is owned by
`notification-service` instead (RULES.md §5, `delivery_db`).

## Why it's a separate service
Delivery assignment reacts to order placement but must not sit on the critical path of placing an
order — the monolith's synchronous delivery-status flow inside order placement is one of the
problems this decomposition removes (ReadMe.md "Key problems to solve"). Running it as its own
service lets delivery tracking scale, deploy, and fail independently of `order-service`, and per
RULES.md §7 the system must keep functioning — orders can still be placed — even when
`delivery-service` is down. Per RULES.md §5, `delivery-service` owns `delivery_db` exclusively;
no other service reads or writes delivery records directly.

## Database
PostgreSQL, `delivery_db` (RULES.md §2). Schema is owned exclusively by `delivery-service` and
migrated with Flyway under `src/main/resources/db/migration`, additive and forward-only on `main`
(RULES.md §5).

## API surface (planned)
Exposes REST endpoints for delivery assignment and status tracking, satisfying the "delivery
assigned → delivery completed" steps of the end-to-end flow (ReadMe.md Epic 5, user story 5.1) and
the delivery routing epic (ReadMe.md Epic 3, `/api/deliveries/**`, added to `api-gateway` in
Sprint 5 per SPRINTS.md). An OpenAPI spec for this surface is planned under
`docs/api-contracts/` (RULES.md §9).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka registration), `config-server` (externalized
  config), its own `delivery_db` Postgres instance, and RabbitMQ. It consumes `OrderPlacedEvent`
  from `order-service`'s topic exchange to auto-create delivery assignments — an idempotent
  consumer (dedupe on event ID) with a dead-letter queue for failed messages, per RULES.md §6 and
  §9 (factor 9). It publishes `DeliveryStatusUpdatedEvent` when a driver picks up or delivers an
  order.
- **Depended on by:** `notification-service` consumes `DeliveryStatusUpdatedEvent` to persist a
  notification/audit record. `api-gateway` routes `/api/deliveries/**` to it.

## Delivered in
Sprint 5 — "Delivery, events, and notifications" (SPRINTS.md). Exit criteria require that placing
an order produces a delivery record automatically with no synchronous call from `order-service`
into `delivery-service`, and that a failed/poisoned message lands in the DLQ instead of blocking
the queue.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §7 (Resilience)
- SPRINTS.md — Sprint 5
- [`./order-service.md`](./order-service.md) — publishes the `OrderPlacedEvent` this service
  consumes
- [`./notification-service.md`](./notification-service.md) — consumes this service's
  `DeliveryStatusUpdatedEvent`
