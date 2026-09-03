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
  embedded claims without a network call back to `identity-service` (RULES.md §8).
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
  `identity-service`'s JWKS endpoint before a request is routed (RULES.md §8; SPRINTS.md Sprint 4).
- `RequestRateLimiter` filter backed by Redis is applied on the order-placement route, using
  namespaced cache keys (e.g. `gateway:rate-limit:{clientId}`) per the shared-Redis-instance
  convention (RULES.md §12; SPRINTS.md Sprint 4).
- Downstream services still re-validate the JWT locally against the cached JWKS as
  defense-in-depth — the gateway's validation is not treated as a trust boundary the rest of the
  system can skip (RULES.md §8).
- Docker Compose service name: `api-gateway`; depends on `discovery-server`, `config-server`, and
  `redis` being healthy before it starts (RULES.md §10).

## Related
- `RULES.md §2` (port 8080, routes), `RULES.md §6` (Eureka-resolved routing), `RULES.md §8`
  (JWT validation at the edge), `RULES.md §12` (Redis-backed rate limiting)
- `SPRINTS.md` Sprint 4 (API Gateway & security edge), Sprint 5 (`/api/deliveries/**` route added)
- `./eureka.md`, `./resilience4j.md`
