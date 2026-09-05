# customer-service

## Responsibility
Owns customer profiles and delivery addresses (RULES.md §2). It is decomposed from the monolith's
Customer Management domain (ReadMe.md project overview and Epic 1) into its own independently
deployable service with its own database.

## Why it's a separate service
Per RULES.md §5, database-per-service is non-negotiable: `customer-service` owns its schema
exclusively, and no other service is ever allowed to join across into it. In the monolith,
customer data shared one database and could be joined against directly by other domains
(ReadMe.md "Key problems to solve"); decomposing it lets customer profile/address data be
developed, deployed, and scaled independently of restaurant, order, and delivery concerns
(ReadMe.md Epic 1, user story 1.1), with foreign-key relationships to other domains replaced by ID
references and REST calls (ReadMe.md user story 1.2).

## Database
Postgres, database `customer_db`. Schema owned exclusively by `customer-service` and migrated with
Flyway under `src/main/resources/db/migration` (RULES.md §5, §2). It may share a Postgres server
instance with other Postgres-backed services in `docker-compose` for local resource efficiency,
but its datasource credentials, connection pool, and Flyway migration history remain fully
isolated (RULES.md §5).

## API surface

**Done and verified live** — built, tested (Testcontainers Postgres + a full Spring Security
context, see `src/test/java/.../controller/*IT.java`), and exercised end to end against a real
Keycloak token and a real `customer_db`. OpenAPI/Swagger UI is live at `/swagger-ui/index.html`
(no auth required to view the docs themselves) once the service is running.

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /api/customers/me` | Any authenticated user | Completes registration for the caller's own Keycloak identity — `email`/`firstName`/`lastName` come from the token, only `phoneNumber` is client-supplied. 409 if already registered. |
| `GET /api/customers/me` | Any authenticated user | 404 until the caller has registered. |
| `PUT /api/customers/me` | Any authenticated user | Updates `phoneNumber` only — identity fields stay Keycloak's. |
| `GET /api/customers/me/addresses` | Any authenticated user | Self-service, ownership resolved from the token. |
| `POST /api/customers/me/addresses` | Any authenticated user | |
| `GET`/`PUT`/`DELETE /api/customers/me/addresses/{id}` | Any authenticated user | 404 (not 403) if the address belongs to someone else — the ownership check is baked into the repository query, not an application-level `if`. |
| `GET /api/customers/{id}` | `user:read` | Admin-only lookup by id — the seeded `CUSTOMER` demo account does **not** have this permission (see `docker/keycloak/fdp-realm.json`), only `ADMIN` does. |
| `GET /api/customers` | `user:read` | Paginated admin listing. |

`email`/`phoneNumber` are `@Masked` in every response (RULES.md §8) — including on a customer's
own `/me` lookup, per the rule as written. `id` is never masked (it's a structural identifier used
in URLs). A Postman collection covering every row above, including the negative/permission cases,
is checked in at `postman/FDP-customer-service.postman_collection.json` (+ matching environment).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka client registration — registers on startup, verified
  live), its own `customer_db` Postgres instance, and Keycloak's JWKS endpoint to validate tokens
  (`spring-boot-starter-oauth2-resource-server`, `common.security.jwt.KeycloakRoleConverter` for
  mapping `resource_access.fdp-api.roles` into Spring Security authorities — RULES.md §3, §8).
  **Not yet wired:** pulling shared config from `config-server` — this service still configures
  its datasource/security/Eureka settings directly in its own `application.properties` rather than
  via `spring-cloud-starter-config`. That's a deliberate scope cut for this pass, not an oversight;
  revisit once a second Postgres-backed service exists and the duplication actually hurts.
- **Depended on by:** `order-service` calls `customer-service` synchronously via OpenFeign to
  validate customer existence and delivery address before accepting an order placement, resolved
  via Eureka (`lb://customer-service`) and wrapped in a Resilience4j circuit breaker with a typed
  fallback (RULES.md §6, §7; SPRINTS.md Sprint 3; ReadMe.md Epic 1, user story 1.2).

## Delivered in
Sprint 2 — "Customer & Restaurant services" (SPRINTS.md), alongside `restaurant-service`. Exit
criteria: `customer-service` runs independently against its own database, with no shared tables
and no direct database access from any other module.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §6 (Communication rules)
- SPRINTS.md — Sprint 2
- [`../technologies/keycloak.md`](../technologies/keycloak.md) — source of the JWT claims this
  service re-validates locally
- [`./api-gateway.md`](./api-gateway.md) — routes `/api/customers/**` to this service
