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

## API surface

**Done and verified live** — built, tested (13 Testcontainers-backed tests against real Postgres
plus a full Spring Security context), and exercised end to end against real Keycloak tokens for
all three relevant demo roles. OpenAPI/Swagger UI is live at `/swagger-ui/index.html` (no auth
required to view the docs themselves) once the service is running.

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /api/restaurants/me` | `restaurant:menu:write` | Completes registration for the caller's own restaurant — one restaurant per owner identity (MVP simplification). 409 if already registered. |
| `GET /api/restaurants/me` | `restaurant:menu:write` | 404 until the caller has registered. |
| `PUT /api/restaurants/me` | `restaurant:menu:write` | Full profile update, including toggling `isOpen`. |
| `GET /api/restaurants/me/menu-items` | `restaurant:menu:write` | Self-service, ownership resolved from the token. |
| `POST /api/restaurants/me/menu-items` | `restaurant:menu:write` | |
| `GET`/`PUT`/`DELETE /api/restaurants/me/menu-items/{id}` | `restaurant:menu:write` | 404 (not 403) if the item belongs to a different restaurant — the ownership check is baked into the repository query. |
| `GET /api/restaurants/{id}` | `restaurant:menu:read` | Public browsing — the seeded `CUSTOMER` demo account holds this permission too, not just owners/admin (see `docker/keycloak/fdp-realm.json`). |
| `GET /api/restaurants` | `restaurant:menu:read` | Paginated public browsing. |
| `GET /api/restaurants/{id}/menu-items` | `restaurant:menu:read` | Public browsing of one restaurant's menu — what `order-service` will also call in Sprint 3 to validate items/pricing. |

Nothing here is `@Masked` — a restaurant's name/address is public storefront information, not the
human-readable PII (email, username, phone) RULES.md §8's masking rule targets. A Postman
collection covering every row above, including the negative/permission cases (a customer can
browse but not write; a delivery agent — which lacks `restaurant:menu:read` entirely — can't even
browse), is checked in at `postman/FDP-restaurant-service.postman_collection.json` (+ the shared
environment also used by `customer-service`'s collection).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka client registration — registers on startup, verified
  live), its own `restaurant_db` Postgres instance, and Keycloak's JWKS endpoint to validate tokens
  (same `common.security.jwt.KeycloakRoleConverter` shared with `customer-service`, RULES.md §3,
  §8). **Not yet wired:** pulling shared config from `config-server`, and Redis caching for
  read-heavy menu lookups (RULES.md §12) — both deliberate scope cuts for this pass, revisited once
  they actually hurt.
- **Depended on by:** `order-service` will call `restaurant-service` synchronously via OpenFeign
  (`lb://restaurant-service`) to validate menu items and pricing before accepting an order, wrapped
  in a Resilience4j circuit breaker with a typed fallback (RULES.md §6, §7; Sprint 3, not built
  yet). `api-gateway` will route `/api/restaurants/**` to it (Sprint 4).

## Delivered in
Sprint 2 — "Customer & Restaurant services" (SPRINTS.md), alongside `customer-service`. **Sprint 2
exit criteria met by both services**: each runs independently against its own database, with no
shared tables and no direct database access from any other module.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules), §12 (Caching)
- SPRINTS.md — Sprint 2
- [`./order-service.md`](./order-service.md) — the primary synchronous caller of this service's
  menu validation
