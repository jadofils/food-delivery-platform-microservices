# delivery-service

## Responsibility
Owns delivery assignment and tracking (RULES.md §2). It holds the delivery-tracking half of the
data and business logic decomposed from the monolith's Delivery and Notification domain — the
notification-dispatch half of that monolith domain is owned by `notification-service` instead
(RULES.md §5, `delivery_db`).

## Why it's a separate service
Delivery assignment reacts to order placement but must not sit on the critical path of placing an
order — the monolith's synchronous delivery-status flow inside order placement is one of the
problems this decomposition removes (ReadMe.md "Key problems to solve"). Running it as its own
service lets delivery tracking scale, deploy, and fail independently of `order-service`, and per
RULES.md §7 the system keeps functioning — orders can still be placed — even when
`delivery-service` is down (verified: it's a RabbitMQ consumer, not a synchronous dependency of
order placement — nothing about placing an order waits on it). Per RULES.md §5, `delivery-service`
owns `delivery_db` exclusively; no other service reads or writes delivery records directly.

## Database
PostgreSQL, `delivery_db` (RULES.md §2). Schema is owned exclusively by `delivery-service` and
migrated with Flyway under `src/main/resources/db/migration`, additive and forward-only on `main`
(RULES.md §5).

## API surface

**Done and verified live.** Every delivery assignment was created automatically off a real
`OrderPlacedEvent` — there is no `POST` to create one directly. OpenAPI/Swagger UI at
`/swagger-ui/index.html`.

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/deliveries/{id}` | `delivery:read` | Any holder of the permission can view any delivery — the same "broad read permission = full browsing rights" pattern `restaurant-service`'s public browsing already uses; no separate ownership-scoped read permission is seeded to gate this more tightly. |
| `GET /api/deliveries/me` | `delivery:read` | The calling agent's own claimed/in-progress deliveries. |
| `GET /api/deliveries/unassigned` | `delivery:read` | Unclaimed (`PENDING`) deliveries any agent can pick up. |
| `POST /api/deliveries/{id}/claim` | `delivery:status:update` | Self-assigns the caller as the agent. `409 Conflict` if not currently `PENDING`. |
| `POST /api/deliveries/{id}/pickup` | `delivery:status:update` | `ASSIGNED` → `PICKED_UP`. `403 Forbidden` if the caller isn't the assigned agent; `409 Conflict` on the wrong starting status. |
| `POST /api/deliveries/{id}/deliver` | `delivery:status:update` | `PICKED_UP` → `DELIVERED`. Same ownership/status checks as pickup. |
| `GET /api/deliveries/by-order/{orderId}` | none — authenticated only | Self-service: the delivery status for one of the caller's own orders. No `delivery:read` required — a plain `CUSTOMER` holds neither `delivery:read` nor `delivery:status:update` (see below), so this route is authentication-plus-ownership-check instead, the same "no dedicated permission exists" pattern `order-service`'s own `/me` routes and `notification-service`'s `/me` route already use: `404`, not `403`, when the caller isn't the order's customer. `order-service` calls this (relaying the customer's own token) to enrich its own `GET /api/orders/me/{id}` with live order tracking. |

Only `ADMIN` and `DELIVERY_AGENT` hold `delivery:read`/`delivery:status:update`
(`docker/keycloak/fdp-realm.json`) — there is deliberately no customer-facing route that *browses*
deliveries the way an agent does, since no distinct "read own delivery" permission exists to gate
one on without inventing one (the same scope decision `order-service` made for admin-wide order
listing). `GET /api/deliveries/by-order/{orderId}` above is not that route — it's a narrow,
self-service, ownership-checked lookup by order id, purpose-built for `order-service`'s order-
tracking feature, not a general delivery-browsing capability for customers.

A Postman collection covering every row above, plus the event-driven creation/notification story,
is checked in at `postman/FDP-delivery-service.postman_collection.json` (18 requests, 25
assertions, Newman-verified).

## Async: consumer and publisher both (RULES.md §6)

**Consumes** `order-service`'s `OrderPlacedEvent`/`OrderCancelledEvent` off its own queue
(`delivery-service.order-events`, with its own DLQ) bound to `fdp.order-events` — never sharing a
queue with `notification-service`'s own consumer. `OrderPlacedEvent` creates a `PENDING`
`DeliveryAssignment`; `OrderCancelledEvent` marks the matching assignment `CANCELLED` (unless it's
already `DELIVERED`). Idempotent the same way every consumer in this codebase now is:
`DeliveryAssignment.orderId` carries a unique index, the real guard against a redelivered event —
`existsByOrderId` is checked first only as a fast-path optimization, and a losing concurrent insert
is caught as `DataIntegrityViolationException` and swallowed, not left to trigger a pointless
retry/DLQ cycle.

**Publishes** `DeliveryStatusUpdatedEvent` to `fdp.delivery-events` (an exchange this service owns,
per RULES.md §6's "publisher owns the exchange") on every transition — claim, pickup, deliver, not
just the last two — so `notification-service` can tell the customer "a driver has been assigned" as
well as "picked up"/"delivered". `notification-service` consumes this on its own second queue
(`notification-service.delivery-events`, its own DLQ, entirely separate from its
`notification-service.order-events` queue).

## Live verification

A real order placed through `order-service` produced a real `PENDING` `DeliveryAssignment` with no
synchronous call from `order-service` — exactly this sprint's exit criterion. Claim → pickup →
deliver each transitioned the record and published a real event; the customer's own
`GET /api/notifications/me` (on `notification-service`) showed `ORDER_PLACED`, `DELIVERY_ASSIGNED`,
`DELIVERY_PICKED_UP`, and `DELIVERY_DELIVERED` as four separate real notification records for the
same order. A second order, cancelled before being claimed, correctly flipped its assignment to
`CANCELLED` and dropped out of the unclaimed list. Distributed tracing (RULES.md §13) confirmed one
real order-placement trace spanning all five domain services plus RabbitMQ plus Redis in a single
trace ID — `order-service → customer-service`, `order-service → restaurant-service`,
`order-service → rabbitmq → delivery-service`, `order-service → rabbitmq → notification-service`.

Order tracking verified live end to end: a customer's `GET /api/orders/me/{id}` on `order-service`
showed `deliveryStatus` moving through `PENDING` → `ASSIGNED` → `PICKED_UP` → `DELIVERED` in real
time as an agent worked the same delivery via the endpoints above, and correctly degraded to
`null` (order still fully viewable) with `delivery-service` stopped mid-run.

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka client registration — verified live), its own
  `delivery_db` Postgres instance, and RabbitMQ (both consume and publish). **Not yet wired:**
  pulling shared config from `config-server` — the same documented scope cut as every other
  service.
- **Depended on by:** `notification-service` consumes this service's `DeliveryStatusUpdatedEvent` —
  done and verified live. `order-service` calls `GET /api/deliveries/by-order/{orderId}`
  synchronously (via OpenFeign, resolved through Eureka) to enrich its own order-tracking view —
  done and verified live; see `docs/services/order-service.md`'s "Order tracking" section for why
  that call is deliberately non-blocking. `api-gateway` will route `/api/deliveries/**` to it
  (Sprint 4, not built yet).

## Delivered in
Sprint 5 — "Delivery, events, and notifications" (SPRINTS.md), the half that remained after
`notification-service`'s own build. Exit criteria met and verified live: placing an order produces a
delivery record automatically with no synchronous call from `order-service` into
`delivery-service`. The DLQ mechanism is config, not custom code (the same pattern already proven
this way for `notification-service`'s own queue) — a real poison-message-reaches-the-DLQ scenario
hasn't been deliberately triggered end to end for this service specifically.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §7 (Resilience)
- SPRINTS.md — Sprint 5
- [`./order-service.md`](./order-service.md) — publishes the `OrderPlacedEvent`/`OrderCancelledEvent`
  this service consumes
- [`./notification-service.md`](./notification-service.md) — consumes this service's
  `DeliveryStatusUpdatedEvent`
