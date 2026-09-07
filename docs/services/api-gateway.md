# api-gateway

## Responsibility
Single entry point for all external traffic: routing, JWT validation, and rate limiting (RULES.md
§2). It routes `/api/customers/**`, `/api/restaurants/**`, `/api/orders/**`, and
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
carry an explicit TTL and are namespaced by the caller's own JWT subject
(`gateway:rate-limit:{clientId}`, `RateLimiterConfig`'s `KeyResolver`).

## API surface

**Done and verified live.** The one WebFlux/reactive service in FDP (RULES.md §14) — everything
else runs on the Servlet/MVC stack. OpenAPI/Swagger UI is intentionally not added here: this
module has no domain endpoints of its own to document, only routes to services that already
document themselves.

| Route predicate | Resolves to | Notes |
|---|---|---|
| `POST /api/orders/me` | `lb://order-service` | Matched *before* the general order-service route below (first-match-wins route ordering) so the `RequestRateLimiter` filter actually applies to it. |
| `/api/orders/**` | `lb://order-service` | Everything else on order-service. |
| `/api/customers/**` | `lb://customer-service` | |
| `/api/restaurants/**` | `lb://restaurant-service` | |
| `/api/deliveries/**` | `lb://delivery-service` | Originally Sprint 5 scope, blocked on this service not existing yet — added now that it does. |

`/api/notifications/**` is a known, deliberate gap: no route exists yet, since neither RULES.md §2
nor SPRINTS.md ever specified one — `notification-service` is reached directly today.

- **JWT validation at the edge** (`SecurityConfig`, RULES.md §8): every request is authenticated
  before a route is even resolved (except `/actuator/health`) — signature and expiry checked
  against Keycloak's real JWKS endpoint via `NimbusReactiveJwtDecoder`, the WebFlux counterpart of
  every other service's `NimbusJwtDecoder`. A missing or invalid token gets a `401`, shaped as the
  same `ApiErrorResponse` envelope every other service returns (RULES.md §14) via
  `RestServerAuthenticationEntryPoint` — the WebFlux equivalent of `common`'s Servlet-typed
  `RestAuthenticationEntryPoint`. Deliberately authentication-only, not authorization: no
  `hasAuthority(...)` check exists here — fine-grained permission checks stay on each downstream
  service's own `@PreAuthorize`, and those services still re-validate the JWT locally
  (defense-in-depth, RULES.md §8) — this gateway's validation is a first line of defense, not a
  replacement for it.
- **Redis-backed `RequestRateLimiter`** on the order-placement route only (RULES.md §12): 5
  requests/sec sustained, burst up to 10, 1 token per request — generous enough not to trip during
  ordinary use, tight enough to demonstrate a real `429` under a quick burst (verified live: 20
  concurrent placement requests from one customer produced several `429 Too Many Requests`
  responses once the burst was exhausted). Keyed by the caller's own JWT subject
  (`RateLimiterConfig`), so the limit is per customer, not per shared egress IP.
- Ties to ReadMe.md Epic 3, user story 3.2 (single entry point, centralized routing,
  authentication, rate limiting).

**Known gap:** a `429` response body is empty today, not shaped as `ApiErrorResponse` — Spring
Cloud Gateway's `RedisRateLimiter` writes that status directly rather than raising an exception the
WebFlux error-handling chain can intercept. The same is true of an unmatched-route `404` or a
"no healthy instance" `503`/`500` — only the edge's own `401` is currently reshaped into FDP's
standard error envelope. Closing this gap means a custom WebFlux `ErrorAttributes`/
`ErrorWebExceptionHandler`, deliberately deferred rather than guessed at without verifying the
exact Boot 4.1/WebFlux API surface live.

A Postman collection for this module doesn't exist yet — every route above was verified live via
curl against the real running stack (see "Getting started" below); adding one is future work, not
yet done.

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka, to resolve `lb://` targets — every routed service
  additionally needed `eureka.instance.prefer-ip-address=true`; see "Getting started" for why),
  Keycloak's JWKS endpoint (to validate JWT signatures at the edge, RULES.md §8), and Redis
  (rate-limit counters, RULES.md §12). It has no Feign clients of its own and publishes/consumes no
  RabbitMQ events. **Not yet wired:** `config-server` integration — same documented scope cut as
  every other service.
- **Depended on by:** every external client (browser, Postman, curl) — it is the sole intended
  entry point per RULES.md §8 and ReadMe.md's target architecture, though today every domain
  service still also accepts direct calls on its own port (nothing currently enforces
  gateway-only access — RULES.md doesn't call for that, only for the gateway to exist as an
  option).

## Delivered in
Sprint 4 — "API Gateway & security edge" (SPRINTS.md): routing to customers/restaurants/orders,
JWT validation filter, and Redis-backed rate limiting on order placement — done and verified live.
The `/api/deliveries/**` route (originally Sprint 5 scope, blocked on `api-gateway` not existing
yet) is added in this same pass, now that both prerequisites exist.

## Getting started

### How to start it
```
./mvnw -pl api-gateway -am spring-boot:run
```
Needs `discovery-server`, Keycloak, and Redis reachable (`docker compose up -d` for the latter
two) — and at least one of the routed services running and registered with Eureka for a route to
actually resolve to something.

### A real gotcha this module's own first live test hit
On a Docker Desktop/WSL2 host, Eureka's default self-registration advertises each instance's
Windows machine hostname (e.g. `DESKTOP-XXXX.mshome.net`) rather than an IP. Spring Cloud Gateway's
routing filter resolves that hostname via Reactor Netty's own async DNS resolver — which, unlike
the blocking `java.net` resolution Feign/`RestClient` use everywhere else in this codebase, never
consults the OS's own NetBIOS/hosts resolution. The result: every other service's Feign calls to
each other worked fine, but `api-gateway`'s first routed request failed with
`UnknownHostException`, until every routed-to service set
`eureka.instance.prefer-ip-address=true` (now the default across all FDP services) so Eureka
advertises a plain IP instead.

### How to access it
`http://localhost:8080/api/<customers|restaurants|orders|deliveries>/...` — same paths and bodies
each backend service already documents, just fronted by the gateway; add
`Authorization: Bearer <token>` (see `credentials.md`) or get a `401` immediately.

### Endpoints it exposes
See the route table above. `/actuator/health` is the one unauthenticated exception.

### Installation & dependencies
`spring-cloud-starter-gateway-server-webflux` (this Spring Cloud train renamed the older
`spring-cloud-starter-gateway` artifact — a WebMVC-flavored gateway variant now exists as a
sibling), `spring-boot-starter-webflux`, `spring-cloud-starter-netflix-eureka-client`,
`spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-data-redis`,
`spring-boot-starter-actuator`. Versions come from the root aggregator's `spring-cloud-dependencies`
BOM, never pinned in `api-gateway`'s own `pom.xml` (RULES.md §4).

### For newcomers
Start with `SecurityConfig` (the WebFlux/`ServerHttpSecurity` counterpart of every other service's
`SecurityConfig`) and `application.properties`' `spring.cloud.gateway.server.webflux.routes[*]`
block — routes are properties-driven, not Java-configured, matching this project's
properties-over-YAML convention everywhere else. `RateLimiterConfig` is the one other piece of
Java wiring, a single `KeyResolver` bean the rate-limited route references by name
(`args.key-resolver=#{@rateLimitKeyResolver}`).

## Related
- RULES.md §6 (Communication rules), §8 (Security), §12 (Caching/Redis), §14 (Error handling)
- SPRINTS.md — Sprint 4, Sprint 5
- [`../technologies/spring-cloud-gateway.md`](../technologies/spring-cloud-gateway.md) — how this
  is implemented
- [`../technologies/keycloak.md`](../technologies/keycloak.md) — sole JWT issuer and JWKS source
  the gateway validates against
- [`./discovery-server.md`](./discovery-server.md) — service registry the gateway resolves routes
  through
