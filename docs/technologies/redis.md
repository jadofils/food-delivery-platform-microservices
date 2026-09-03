# Redis

## What it is
Redis is an in-memory data store. In FDP it is used as a single, centralized cache shared across
services, never as a per-instance local cache.

## Why FDP uses it
- A centralized cache is required so caching doesn't break statelessness across horizontally
  scaled replicas of a service — a local/in-memory cache would violate factor 6 (stateless
  processes) and undermine factor 8 (concurrency via scale-out) (RULES.md §1, RULES.md §12).
- `api-gateway` needs a shared store for rate-limit counters so that rate limiting is consistent
  across gateway instances, not per-instance (RULES.md §12, RULES.md §1 factor 8).
- `restaurant-service` menu lookups are read-heavy; caching them avoids repeated database hits for
  data that changes infrequently relative to how often it's read (RULES.md §12).

## Where it's used

| Service | Purpose | Sprint introduced |
|---|---|---|
| `api-gateway` | `RequestRateLimiter` on order placement | Sprint 4 |
| `restaurant-service` | Menu lookup caching | Sprint 4 (Redis stood up; usage per RULES.md §12) |

Redis itself is first stood up as infrastructure in Sprint 4, alongside the API Gateway and
security edge (SPRINTS.md, Sprint 4).

## How it's implemented in FDP
- `api-gateway` uses Spring Cloud Gateway's `RequestRateLimiter` filter backed by Redis
  (`spring-cloud-starter-gateway` plus the reactive Redis integration) to rate-limit the order
  placement route (RULES.md §4 note on `api-gateway`'s dependency set, RULES.md §12,
  SPRINTS.md Sprint 4).
- `restaurant-service` caches menu lookups through Spring's cache abstraction backed by Redis.
- Cache keys are namespaced per service to allow one shared Redis instance without collisions,
  e.g. `restaurant-service:menu:{id}` and `gateway:rate-limit:{clientId}` (RULES.md §12).
- Every cache entry carries an explicit TTL — nothing is cached indefinitely (RULES.md §12).
- Redis connection details (host/port/credentials) come from each service's
  `application-{profile}.yml`, resolved to the Docker Compose service hostname in the `docker`
  profile rather than `localhost` (RULES.md §10).
- Redis is one of the backing infrastructure resources defined once in `docker-compose.yml`
  (RULES.md §2, RULES.md §10) and shared by both consuming services.

## Related
- RULES.md §1 (factor 4, factor 6, factor 8), RULES.md §12, RULES.md §10
- SPRINTS.md Sprint 4
- `./jwt.md`
