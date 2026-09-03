# api-gateway

## Responsibility
Single entry point for all external traffic: routing, JWT validation, and rate limiting (RULES.md
§2). It is designed to route `/api/customers/**`, `/api/restaurants/**`, `/api/orders/**`, and
`/api/deliveries/**` to the corresponding backend services via Eureka load-balanced (`lb://`)
URIs (RULES.md §6; ReadMe.md Epic 3, user story 3.2).

## Why it's a separate service
Centralized routing, authentication, and rate limiting are cross-cutting edge concerns that apply
to every client request regardless of which domain it targets — they don't belong inside any one
domain service, and folding them into a domain service would couple that service's lifecycle to
the platform's entire external traffic surface. As its own service it can scale and deploy
independently of the services it fronts, and it is the only component clients talk to directly
(ReadMe.md target architecture).

## Database
None. `api-gateway` has no datastore of its own (RULES.md §2). It uses Redis only as a backing
service for rate-limit counters (RULES.md §12), not as a system of record — cache entries there
carry an explicit TTL and are namespaced (`gateway:rate-limit:{clientId}`).

## API surface (planned)
Not a domain API — it is a routing and security edge:
- Routes `/api/customers/**`, `/api/restaurants/**`, `/api/orders/**` (added Sprint 4), and later
  `/api/deliveries/**` (added Sprint 5) to their respective backend services via Eureka `lb://`
  URIs (RULES.md §6, SPRINTS.md Sprint 4 and Sprint 5).
- JWT validation filter at the edge: verifies signature, expiry, and issuer against
  `identity-service`'s JWKS before routing any request through (RULES.md §8).
- Redis-backed `RequestRateLimiter` applied to the order-placement route (RULES.md §12,
  SPRINTS.md Sprint 4).
- Ties to ReadMe.md Epic 3, user story 3.2 (single entry point, centralized routing,
  authentication, rate limiting).

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka, to resolve `lb://` targets), `config-server` (its
  own externalized config), `identity-service`'s JWKS endpoint (to validate JWT signatures at the
  edge, RULES.md §8), and Redis (rate-limit counters, RULES.md §12). It has no Feign clients of
  its own and publishes/consumes no RabbitMQ events.
- **Depended on by:** every external client (browser, Postman) — it is the sole entry point per
  RULES.md §8 and ReadMe.md's target architecture; no client is expected to call a domain service
  directly.

## Delivered in
Sprint 4 — "API Gateway & security edge" (SPRINTS.md): routing to customers/restaurants/orders,
JWT validation filter, and Redis-backed rate limiting on order placement. The `/api/deliveries/**`
route is added incrementally in Sprint 5 once `delivery-service` exists.

## Related
- RULES.md §6 (Communication rules), §8 (Security), §12 (Caching/Redis)
- SPRINTS.md — Sprint 4, Sprint 5
- [`./identity-service.md`](./identity-service.md) — sole JWT issuer and JWKS source the gateway
  validates against
- [`./discovery-server.md`](./discovery-server.md) — service registry the gateway resolves routes
  through
