# order-service

## Responsibility
Owns order placement and order lifecycle (RULES.md §2). It is designed to hold the data and
business logic decomposed from the monolith's Order Management domain — creating orders,
tracking their status, and coordinating validation against customer and restaurant data before an
order is accepted (ReadMe.md Epic 1, `order_db`).

## Why it's a separate service
Order placement is the platform's central transactional workflow and has a different scaling
profile, release cadence, and failure-isolation need than restaurant browsing or customer profile
management. Keeping it separate means order throughput can be scaled independently, and a bug or
outage in order processing does not take down restaurant or customer data access. Per RULES.md §5,
`order-service` owns `order_db` exclusively — it reaches into `customer-service` and
`restaurant-service` over REST rather than joining against their tables, and it never blocks on
`delivery-service` or `notification-service` for the same reason (RULES.md §6).

## Database
PostgreSQL, `order_db` (RULES.md §2). Schema is owned exclusively by `order-service` and migrated
with Flyway under `src/main/resources/db/migration`, additive and forward-only on `main`
(RULES.md §5).

## API surface (planned)
Exposes REST endpoints for placing and querying orders, satisfying the "place order" step of the
end-to-end flow (ReadMe.md Epic 5, user story 5.1) and the order routing epic (ReadMe.md Epic 3,
`/api/orders/**`). Before accepting an order it validates the customer and delivery address via
`customer-service` and validates menu items and pricing via `restaurant-service` (ReadMe.md Epic
1, user story 1.2). An OpenAPI spec for this surface is planned under `docs/api-contracts/`
(RULES.md §9).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka registration), `config-server` (externalized
  config), its own `order_db` Postgres instance, and — synchronously via OpenFeign, resolved
  through Eureka and wrapped in a Resilience4j circuit breaker with a typed fallback —
  `customer-service` (validate customer/address) and `restaurant-service` (validate menu items and
  pricing) (RULES.md §6, §7). It publishes `OrderPlacedEvent` and `OrderCancelledEvent` to a
  RabbitMQ topic exchange instead of calling `delivery-service` or `notification-service`
  synchronously (RULES.md §6).
- **Depended on by:** `delivery-service` consumes `OrderPlacedEvent` to auto-create delivery
  assignments; `notification-service` consumes `OrderPlacedEvent`/`OrderCancelledEvent` to persist
  notification/audit records. `api-gateway` routes `/api/orders/**` to it (ReadMe.md Epic 3).

## Delivered in
Sprint 3 — "Order service & synchronous inter-service calls" (SPRINTS.md) delivers the service
itself and its synchronous Feign calls to `customer-service` and `restaurant-service`. Sprint 5 —
"Delivery, events, and notifications" (SPRINTS.md) adds its `OrderPlacedEvent` /
`OrderCancelledEvent` publishing to RabbitMQ.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §7 (Resilience)
- SPRINTS.md — Sprint 3, Sprint 5
- [`./restaurant-service.md`](./restaurant-service.md) — synchronous dependency for menu/pricing
  validation
- [`./delivery-service.md`](./delivery-service.md) — consumes this service's published events
