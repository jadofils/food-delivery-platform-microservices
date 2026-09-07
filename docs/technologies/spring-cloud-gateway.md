# Spring Cloud Gateway

## What it is
Spring Cloud Gateway is a reactive, route-based edge server built on Spring WebFlux. It matches
inbound requests against predicates (path, method, etc.), applies filters (auth, rate limiting,
header rewriting), and forwards the request to a resolved backend.

## Why FDP uses it
- FDP requires a single entry point for external clients — routing, authentication, and rate
  limiting all centralized at the edge instead of duplicated per service (RULES.md §2; ReadMe.md
  Epic 3, user story 3.2).
- JWT validation must happen once, at the edge, before routing — `api-gateway` validates every
  inbound token's signature and expiry so downstream services can authorize locally off
  embedded claims without a network call back to Keycloak (RULES.md §8).
- Order placement needs centralized rate limiting to protect the system from bursty traffic on a
  high-traffic write path — this is the gateway's `RequestRateLimiter`, backed by Redis (RULES.md
  §4, §12).
- Routing must resolve to healthy, horizontally-scaled backend instances rather than fixed
  addresses, which is why gateway routes use Eureka-backed `lb://` URIs (RULES.md §2, §6; RULES.md
  §1 factor 8).

## Where it's used

| Service/module | Role | Sprint |
|---|---|---|
| `api-gateway` | Routes all external traffic, JWT validation filter, rate limiting | Sprint 4 (customers/restaurants/orders), Sprint 5 (deliveries route added) |

## How it's implemented in FDP
- Dependency: `spring-cloud-starter-gateway-server-webflux` in `api-gateway`'s `pom.xml` only —
  `api-gateway` does not depend on `spring-boot-starter-data-jpa` or any other service's
  dependencies (RULES.md §4). This Spring Cloud train renamed the older
  `spring-cloud-starter-gateway` artifact to this WebFlux-specific one — a WebMVC-flavored gateway
  variant now exists as a sibling artifact, so the name had to disambiguate.
- Runs on port `8080` (RULES.md §2).
- Routes are configured via `application.properties`
  (`spring.cloud.gateway.server.webflux.routes[*]`), not Java `RouteLocator` beans — matching this
  project's properties-over-YAML convention everywhere else, and confirmed against this Spring
  Cloud version's own configuration metadata (the property prefix moved to
  `spring.cloud.gateway.server.webflux.*`, not the older `spring.cloud.gateway.*` many
  older examples still show). Route predicates map:
  - `POST /api/orders/me` → `lb://order-service` (listed *before* the general order-service route
    below — first-match-wins route ordering — so `RequestRateLimiter` actually applies to it)
  - `/api/orders/**` → `lb://order-service` (everything else)
  - `/api/customers/**` → `lb://customer-service`
  - `/api/restaurants/**` → `lb://restaurant-service`
  - `/api/deliveries/**` → `lb://delivery-service`
  - `/api/notifications/**` → `lb://notification-service` (not originally scoped by any sprint —
    added once every domain service moved to a dynamic port, since without it
    `notification-service` would have had no stable address left at all)
  (RULES.md §2, §6; SPRINTS.md Sprint 4 and Sprint 5)
- JWT validation is Spring Security's own reactive OAuth2 Resource Server support
  (`NimbusReactiveJwtDecoder`, `SecurityConfig`), not a custom `GatewayFilter` — the same
  `resource_access.fdp-api.roles`-reading `KeycloakRoleConverter` every Servlet-stack service
  already uses is reused as-is (its `Converter<Jwt, AbstractAuthenticationToken>` signature is
  stack-agnostic), wrapped in Spring Security's own `ReactiveJwtAuthenticationConverterAdapter` to
  bridge it into the reactive DSL. A `401` on missing/invalid token is reshaped into FDP's standard
  `ApiErrorResponse` envelope by a custom `RestServerAuthenticationEntryPoint` (RULES.md §8, §14;
  SPRINTS.md Sprint 4).
- `RequestRateLimiter` filter backed by Redis is applied to the order-placement route only, keyed
  by the caller's own JWT subject (`RateLimiterConfig`'s `KeyResolver`,
  `gateway:rate-limit:{clientId}` per the shared-Redis-instance convention) — 5 requests/sec
  sustained, burst up to 10 (RULES.md §12, SPRINTS.md Sprint 4). `RedisRateLimiter` is
  auto-configured as the default `RateLimiter` implementation the moment a reactive Redis
  connection factory is on the classpath (`spring-boot-starter-data-redis` alone — no separate
  "-reactive" artifact needed), so the route's filter definition only has to name the
  `KeyResolver`; the bucket size itself is set globally per route id via
  `spring.cloud.gateway.server.webflux.redis-rate-limiter.config.<routeId>.*`.
- Downstream services still re-validate the JWT locally against the cached JWKS as
  defense-in-depth — the gateway's validation is not treated as a trust boundary the rest of the
  system can skip (RULES.md §8).
- **A real, live-discovered gotcha:** on a Docker Desktop/WSL2 host, Eureka's default
  self-registration advertises each instance's Windows machine hostname (a `*.mshome.net` name)
  rather than an IP. Reactor Netty's async DNS resolver — what Gateway's routing filter actually
  uses to reach a resolved `lb://` target — does not consult the OS's own NetBIOS/hosts resolution
  the way the blocking `java.net` resolution every Feign/`RestClient` call elsewhere in this
  codebase uses does. Every other service's own Feign-to-Feign calls worked fine on this same
  machine; `api-gateway`'s first routed request failed with `UnknownHostException` until every
  service being routed to set `eureka.instance.prefer-ip-address=true`.
- **Every domain service binds to `server.port=0`** (OS-assigned), not a fixed port (RULES.md §2)
  — `api-gateway` is the only stable address left for reaching any of them. This is what makes
  real horizontal scaling possible on one machine: a second instance of the same service registers
  under its own free port with zero config, and Spring Cloud LoadBalancer's default round-robin
  strategy spreads gateway-routed requests across every registered instance automatically (RULES.md
  §1 factor 8) — verified live with two `customer-service` instances running side by side.

## Getting started

**Status: done and verified live** (Sprint 4, plus Sprint 5's deliveries route added
retroactively). `api-gateway`'s `pom.xml` carries `spring-boot-starter-webflux`,
`spring-cloud-starter-gateway-server-webflux`, `spring-cloud-starter-netflix-eureka-client`,
`spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-data-redis`, and
`spring-boot-starter-actuator`.

### How to start it
From the repo root, with `discovery-server`, Keycloak, and Redis reachable, and at least one
routed service registered with Eureka:
```
./mvnw -pl api-gateway -am spring-boot:run
```

### How to access it
`http://localhost:8080/api/<customers|restaurants|orders|deliveries|notifications>/...` — the same
paths and request/response bodies each backend service already documents, fronted by the gateway.
A request with no (or an invalid) `Authorization: Bearer <token>` header gets a `401` immediately,
before any route is even resolved.

### Endpoints it exposes
| Route predicate | Resolves to |
|---|---|
| `POST /api/orders/me` | `lb://order-service` (rate-limited) |
| `/api/orders/**` | `lb://order-service` |
| `/api/customers/**` | `lb://customer-service` |
| `/api/restaurants/**` | `lb://restaurant-service` |
| `/api/deliveries/**` | `lb://delivery-service` |
| `/api/notifications/**` | `lb://notification-service` |

`/actuator/health` is the one unauthenticated exception.

### Installation & dependencies
See `api-gateway`'s own `pom.xml`. Versions come from the root aggregator's
`spring-cloud-dependencies` BOM, never pinned in `api-gateway`'s own `pom.xml` (RULES.md §4) — same
version-compatibility caveat noted in `./eureka.md` applies here too, since the gateway starter
ships from the same Spring Cloud train.

### For newcomers
Read RULES.md §2 (port, single-entry-point role) and §6 (Eureka-resolved routing) for what this
service is *for*. In code, start with `SecurityConfig` (the WebFlux/`ServerHttpSecurity`
counterpart of every other service's own `SecurityConfig`) and `application.properties`' route
block, then `RateLimiterConfig` for the one other piece of Java wiring this module needed.

**A known, documented gap:** a `429` from the rate limiter (and a `404`/`503` from an unmatched or
unresolvable route) is not yet reshaped into FDP's standard `ApiErrorResponse` envelope the way the
edge's own `401` is — those come from Gateway/WebFlux's own default handling today. Closing this
needs a custom WebFlux `ErrorAttributes`/`ErrorWebExceptionHandler`, deliberately left open rather
than guessed at without verifying the exact API surface live against this Boot 4.1/WebFlux version.

## Related
- `RULES.md §2` (port, routes), `RULES.md §6` (Eureka-resolved routing), `RULES.md §8`
  (JWT validation at the edge), `RULES.md §12` (Redis-backed rate limiting), `RULES.md §14`
  (error response shape)
- `SPRINTS.md` Sprint 4 (API Gateway & security edge), Sprint 5 (`/api/deliveries/**` route added)
- [`../services/api-gateway.md`](../services/api-gateway.md)
- `./eureka.md`, `./resilience4j.md`
