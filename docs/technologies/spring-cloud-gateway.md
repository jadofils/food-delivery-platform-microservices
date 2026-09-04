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
  inbound token's signature, expiry, and issuer so downstream services can authorize locally off
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
- Dependency: `spring-cloud-starter-gateway` in `api-gateway`'s `pom.xml` only — `api-gateway` does
  not depend on `spring-boot-starter-data-jpa` or any other service's dependencies (RULES.md §4).
- Runs on port `8080` (RULES.md §2).
- Route predicates map:
  - `/api/customers/**` → `lb://customer-service`
  - `/api/restaurants/**` → `lb://restaurant-service`
  - `/api/orders/**` → `lb://order-service`
  - `/api/deliveries/**` → `lb://delivery-service` (added in Sprint 5)
  (RULES.md §2, §6; SPRINTS.md Sprint 4 and Sprint 5)
- A JWT validation filter (custom `GatewayFilter`) checks signature, expiry, and issuer against
  Keycloak's JWKS endpoint before a request is routed (RULES.md §8; SPRINTS.md Sprint 4).
- `RequestRateLimiter` filter backed by Redis is applied on the order-placement route, using
  namespaced cache keys (e.g. `gateway:rate-limit:{clientId}`) per the shared-Redis-instance
  convention (RULES.md §12; SPRINTS.md Sprint 4).
- Downstream services still re-validate the JWT locally against the cached JWKS as
  defense-in-depth — the gateway's validation is not treated as a trust boundary the rest of the
  system can skip (RULES.md §8).
- Docker Compose service name: `api-gateway`; depends on `discovery-server`, `config-server`, and
  `redis` being healthy before it starts (RULES.md §10).

## Getting started

**Status today:** `api-gateway` is a bare skeleton — its `pom.xml` carries only
`spring-boot-starter` and `spring-boot-starter-test` (test scope), nothing else. No
`spring-cloud-starter-gateway`, no WebFlux, no route config, no Eureka client. Its
`application.properties` sets only `spring.application.name=api-gateway` (port `8080` is just
Spring Boot's own default here, not an explicit setting yet). All of this is Sprint 4 work, not
started.

### How to start it
From the repo root:
```
./mvnw -pl api-gateway -am spring-boot:run
```
This boots today's empty skeleton successfully — no external dependency (Postgres, Eureka,
Keycloak) is required, because it doesn't talk to any of them yet. Starting it today only proves
the module compiles and the embedded server comes up; it does not prove any gateway functionality,
because there isn't any yet.

### How to access it
`http://localhost:8080/<anything>` returns Spring Boot's default whitelabel error page today —
there are no routes configured, so every path is unmatched. This is expected, not broken.

### Endpoints it exposes
None yet. The routes SPRINTS.md Sprint 4-5 and RULES.md §2/§6 plan are:

| Route predicate | Resolves to | Sprint |
|---|---|---|
| `/api/customers/**` | `lb://customer-service` | Sprint 4 |
| `/api/restaurants/**` | `lb://restaurant-service` | Sprint 4 |
| `/api/orders/**` | `lb://order-service` | Sprint 4 |
| `/api/deliveries/**` | `lb://delivery-service` | Sprint 5 |

None of these are configured today — `api-gateway` has no route definitions at all yet.

### Installation & dependencies
- Not present in `api-gateway`'s `pom.xml` today. Planned additions:
  `spring-cloud-starter-gateway` (reactive, WebFlux-based — note this makes `api-gateway` the one
  service *not* on the Servlet/MVC stack the rest of FDP uses, which is why RULES.md §14 has it
  define its own `ServerWebExchange`-flavored exception advice instead of reusing `common`'s
  Servlet-based `AbstractGlobalExceptionHandler`), `spring-cloud-starter-netflix-eureka-client`
  (route resolution via `lb://`), and later `spring-boot-starter-oauth2-resource-server` (edge JWT
  validation) plus `spring-boot-starter-data-redis` (rate limiting) — all Sprint 4, per
  SPRINTS.md, none present yet.
- Versions come from the root aggregator's `spring-cloud-dependencies` BOM, never pinned in
  `api-gateway`'s own `pom.xml` (RULES.md §4) — same version-compatibility caveat noted in
  `./eureka.md` applies here too, since the gateway starter ships from the same Spring Cloud train.

### For newcomers
There's nothing gateway-shaped to explore here yet — running the module today just confirms the
skeleton boots. Read RULES.md §2 (port, single-entry-point role) and §6 (Eureka-resolved routing)
for what this service is *for*, and SPRINTS.md Sprint 4 for the concrete plan (routes, JWT
validation, rate limiting) before expecting to find any of it in the code.

## Related
- `RULES.md §2` (port 8080, routes), `RULES.md §6` (Eureka-resolved routing), `RULES.md §8`
  (JWT validation at the edge), `RULES.md §12` (Redis-backed rate limiting)
- `SPRINTS.md` Sprint 4 (API Gateway & security edge), Sprint 5 (`/api/deliveries/**` route added)
- `./eureka.md`, `./resilience4j.md`
