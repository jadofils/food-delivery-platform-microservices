# restaurant-service

## Responsibility
Owns restaurants, menus, and menu items (RULES.md §2). It is designed to hold the data and
business logic decomposed from the monolith's Restaurant and Menu Management domain — restaurant
records, menu structure, menu item pricing and availability (ReadMe.md Epic 1, `restaurant_db`).

## Why it's a separate service
Restaurant and menu data has its own lifecycle, ownership (`RESTAURANT_OWNER` role, RULES.md §8),
and read/write pattern distinct from ordering or delivery. Splitting it out lets menu browsing
scale and cache independently of order placement, and lets `restaurant-service` be deployed,
migrated, and scaled without any dependency on `order-service`'s release cycle. Per RULES.md §5,
database-per-service means no other service ever joins against restaurant/menu tables directly —
`order-service` must call in over REST to validate a menu item instead of reading it from a shared
schema.

## Database
PostgreSQL, `restaurant_db` (RULES.md §2). Schema is owned exclusively by `restaurant-service` and
migrated with Flyway under `src/main/resources/db/migration`, additive and forward-only on `main`
(RULES.md §5).

## API surface (planned)
Exposes REST endpoints for restaurant and menu management: creating/reading restaurants, and
creating/reading/updating menu items and their pricing and availability, satisfying the "browse
restaurants" step of the end-to-end flow (ReadMe.md Epic 5, user story 5.1) and the restaurant
routing epic (ReadMe.md Epic 3, `/api/restaurants/**`). It also serves the menu-item lookups that
`order-service` needs to validate items and prices before accepting an order (ReadMe.md Epic 1,
user story 1.2). An OpenAPI spec for this surface is planned under `docs/api-contracts/`
(RULES.md §9).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka registration), `config-server` (externalized
  config), its own `restaurant_db` Postgres instance, and Redis for caching read-heavy menu
  lookups (RULES.md §12).
- **Depended on by:** `order-service` calls `restaurant-service` synchronously via OpenFeign
  (`lb://restaurant-service`) to validate menu items and pricing before accepting an order, wrapped
  in a Resilience4j circuit breaker with a typed fallback (RULES.md §6, §7). `api-gateway` routes
  `/api/restaurants/**` to it (ReadMe.md Epic 3).

## Delivered in
Sprint 2 — "Customer & Restaurant services" (SPRINTS.md). Exit criteria require
`restaurant-service` to run independently against its own database, registered with Eureka and
pulling config from `config-server`, with no shared tables and no direct database access from any
other module.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §12 (Caching)
- SPRINTS.md — Sprint 2
- [`./order-service.md`](./order-service.md) — the primary synchronous caller of this service's
  menu validation
