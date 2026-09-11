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
`restaurant-service` over REST rather than joining against their tables, and it never blocks
order *placement* or *cancellation* on `delivery-service` or `notification-service`, which learn
about an order asynchronously instead (RULES.md §6). The one exception is read-side: `GET
/api/orders/me/{id}` optionally enriches its response with a live status fetched from
`delivery-service` (see the API surface section below) — but that call degrades to `null` rather
than failing the request, so an already-placed order's own detail view is never at the mercy of a
downstream dependency being slow or down.

## Database
PostgreSQL, `order_db` (RULES.md §2). Schema is owned exclusively by `order-service` and migrated
with Flyway under `src/main/resources/db/migration`, additive and forward-only on `main`
(RULES.md §5).

## API surface

**Done and verified live** — the first FDP service to demonstrate both communication styles
RULES.md §6 defines: synchronous (OpenFeign, circuit-breaker-protected) and asynchronous
(RabbitMQ). Tested (11 Testcontainers-backed tests: Postgres + RabbitMQ, mocked Feign gateways)
and exercised end to end against real, live `customer-service`/`restaurant-service` instances —
including a real, timed circuit-breaker fallback with `restaurant-service` actually stopped mid-run.
OpenAPI/Swagger UI at `/swagger-ui/index.html`.

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /api/orders/me` | `order:create` | Validates the restaurant, delivery address, and every menu item via real OpenFeign calls (never trusts a client-supplied price); snapshots item name/price into the order; publishes `OrderPlacedEvent`. |
| `GET /api/orders/me` | `order:read` | Paginated summary view (no item breakdown — see why below). |
| `GET /api/orders/me/{id}` | `order:read` | Full view including item breakdown, plus live order tracking: a `deliveryStatus` field (`PENDING`/`ASSIGNED`/`PICKED_UP`/`DELIVERED`, or `null`) fetched from `delivery-service`. 404 if not found or not the caller's own order. |
| `POST /api/orders/me/{id}/cancel` | `order:cancel` | Only from `PLACED`; `409 Conflict` if already cancelled. Publishes `OrderCancelledEvent`. |

Entirely self-service — matches Keycloak's own permission set exactly (`order:create`,
`order:read`, `order:cancel`; RULES.md §8), so there is no separate admin-wide order-listing route
in this build (there's no distinct "view any order" permission to gate it on without inventing
one — a deliberate scope cut, not an oversight).

**Sync validation (RULES.md §6, §7):** `CustomerServiceGateway`/`RestaurantServiceGateway` wrap
`OpenFeign` clients resolved via Eureka (`lb://customer-service`, `lb://restaurant-service`), each
with an explicit Resilience4j circuit breaker + retry + bulkhead (named instances, no library
defaults) and a typed `ServiceUnavailableException` fallback. "Timeout" is Feign's own
connect/read timeout, not Resilience4j's `@TimeLimiter` (see `CustomerServiceGateway`'s class
comment for why). Every call **relays the placing customer's own Bearer token**
(`TokenRelayRequestInterceptor`) rather than using a separate service credential — order-service
only ever does what the caller who placed the order was already allowed to do.

**Async publishing (RULES.md §6):** a durable `fdp.order-events` topic exchange, routing keys
`order.placed`/`order.cancelled`. A temporary `order-events.inspection` queue (bound to `order.*`)
makes published events visible via RabbitMQ's management UI (`http://localhost:15672`) even
without a consumer running — left in place for local debugging even now that `notification-service`
has its own real consumer queue (RULES.md §6: every consumer owns its own queue, never a shared
one), since a shared inspection view is still useful for one-off manual checks.

**Order tracking (`GET /api/orders/me/{id}`'s `deliveryStatus`):** a third Feign client,
`DeliveryServiceClient`, calls `delivery-service`'s self-service `GET
/api/deliveries/by-order/{orderId}` route (relaying the customer's own token, same as the other
two gateways) and is wrapped by `DeliveryServiceGateway` — deliberately *not* the fail-loud
`ServiceUnavailableException` pattern the other two gateways use. This is a read-side enrichment of
an already-placed order, not a placement-time validation, so both "no delivery assignment yet" (a
404 — e.g. `delivery-service`'s own async consumer hasn't caught up with a just-placed order) and
"`delivery-service` unreachable" (circuit open, timeout) degrade to a `null` `deliveryStatus`
rather than failing the whole request (RULES.md §7). Verified live: `deliveryStatus` tracked a real
delivery through `PENDING` → `ASSIGNED` → `PICKED_UP` → `DELIVERED` as a delivery agent worked the
order in `delivery-service`, and stayed `null` (with the order itself still fully viewable) with
`delivery-service` stopped mid-run.

A Postman collection covering every row above, plus the resilience/async demos, is checked in at
`postman/FDP-order-service.postman_collection.json`.

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka client registration — verified live), its own
  `order_db` Postgres instance, RabbitMQ (publish only), and — synchronously via OpenFeign,
  resolved through Eureka and wrapped in a Resilience4j circuit breaker with a typed fallback —
  `customer-service` (validate customer/address) and `restaurant-service` (validate menu items and
  pricing) (RULES.md §6, §7), confirmed live including the failure path (stopped
  `restaurant-service` mid-flow, got a clean `503` in ~7s, confirmed automatic recovery once it
  came back). Also calls `delivery-service` synchronously, but only to enrich `GET
  /api/orders/me/{id}`'s read view with live tracking status — never on the placement/cancellation
  path, and never blocking (see "Order tracking" above).
- **Depended on by:** `notification-service` consumes `OrderPlacedEvent`/`OrderCancelledEvent` to
  persist notification/audit records, and `delivery-service` consumes the same events to
  auto-create/cancel delivery assignments — both done and verified live (see
  `docs/services/notification-service.md`, `docs/services/delivery-service.md`). `api-gateway` will
  route `/api/orders/**` to it (Sprint 4, not built yet).

## Delivered in
Sprint 3 — "Order service & synchronous inter-service calls" (SPRINTS.md): the service itself and
its synchronous Feign calls to `customer-service`/`restaurant-service`, done. The *publishing* half
of Sprint 5's `OrderPlacedEvent`/`OrderCancelledEvent` work was pulled forward into this same
build, since it's the natural pairing with order-service's own sync calls and directly demonstrates
RULES.md §6's async communication rules. The *consuming* side has since landed too, completing
Sprint 5: `notification-service` and `delivery-service` are both done and verified live.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §7 (Resilience)
- SPRINTS.md — Sprint 3, Sprint 5
- [`./restaurant-service.md`](./restaurant-service.md) — synchronous dependency for menu/pricing
  validation
- [`./notification-service.md`](./notification-service.md) — consumes this service's published
  events
- [`./delivery-service.md`](./delivery-service.md) — consumes this service's published events
