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

## API surface (planned)
- Customer registration and profile management.
- Delivery address management (create, update, associate with a customer).
- Exposes its own REST API and OpenAPI spec (→ `docs/api-contracts/`), per SPRINTS.md Sprint 2.
- Ties to ReadMe.md Epic 1 (Service Decomposition and Database Separation) — customer profiles and
  addresses are the data this service owns after decomposition from the monolith's Customer
  Management domain.

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka registration), `config-server` (externalized config),
  its own `customer_db` Postgres instance. It re-validates JWTs locally against `identity-service`'s
  cached JWKS as defense-in-depth (RULES.md §8) rather than calling `identity-service`
  per-request.
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
- [`./identity-service.md`](./identity-service.md) — source of the JWT claims this service
  re-validates locally
- [`./api-gateway.md`](./api-gateway.md) — routes `/api/customers/**` to this service
