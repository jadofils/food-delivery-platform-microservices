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

| Service | Purpose | Sprint introduced | Status |
|---|---|---|---|
| `restaurant-service` | Caches `GET /api/restaurants/{id}` and `GET /api/restaurants/{id}/menu-items` | Pulled forward (RULES.md §12) | Done, verified live |
| `api-gateway` | `RequestRateLimiter` on order placement | Sprint 4 | Not built (`api-gateway` doesn't exist yet) |

Redis itself was first stood up as skeleton infrastructure in Sprint 0 — `restaurant-service`'s
caching pulls its actual use forward, the same way order-service's async publish was pulled
forward into Sprint 3 and notification-service's consumption into the same build as its own
creation.

## How it's implemented in FDP
- `restaurant-service` caches `GET /api/restaurants/{id}` (cache `restaurant-service:restaurant`)
  and `GET /api/restaurants/{id}/menu-items` (cache `restaurant-service:menu`) through Spring's
  cache abstraction (`@Cacheable`) backed by Redis, on `spring-boot-starter-data-redis` +
  `spring-boot-starter-cache`.
- `api-gateway` will use Spring Cloud Gateway's `RequestRateLimiter` filter backed by Redis to
  rate-limit the order placement route once built (Sprint 4, not built yet).
- Cache keys are namespaced per service to allow one shared Redis instance without collisions
  (RULES.md §12) — verified live via `redis-cli keys '*'`: `restaurant-service:restaurant::1`,
  `restaurant-service:menu::1` (the double colon is `RedisCacheManager`'s own separator between
  cache name and key, not something configured by hand).
- Every cache entry carries an explicit TTL (two minutes, `CacheConfig.TTL`) — nothing is cached
  indefinitely (RULES.md §12), confirmed live via `redis-cli ttl`.
- `@CacheEvict` on every write path (`updateOwnProfile`, and `MenuItemService`'s
  add/update/delete-for-owner) invalidates the affected entry immediately rather than waiting out
  the TTL — confirmed live: updating a restaurant's name evicts `restaurant-service:restaurant::1`
  immediately (`redis-cli keys '*'` no longer lists it), and the next read repopulates it with the
  new value.
- **Caches the response DTO, not the JPA entity.** `RestaurantResponse`/`MenuItemResponse` aren't
  `Serializable`, and neither are the `Restaurant`/`MenuItem` entities — caching the entity directly
  would either throw (JDK serialization needs `Serializable`) or risk serializing an uninitialized
  Hibernate lazy proxy (`MenuItem.restaurant`). `@Cacheable`/`@Cacheable`'s sibling annotations live
  at the controller layer specifically so what's stored is the exact DTO shape already returned to
  API clients — confirmed via the real `IllegalStateException`/`SerializationException` this
  surfaced before the fix (see "Two real gotchas" below).
- Redis connection details (host/port/password) come from `application.properties`; not yet sourced
  through `config-server` (same documented scope cut as every other service so far).

### Two real gotchas surfaced getting this working
- **`GenericJackson2JsonRedisSerializer`'s default value serializer requires `Serializable`.**
  `RedisCacheConfiguration.defaultCacheConfig()`'s value serializer is plain JDK serialization —
  confirmed empirically (`IllegalStateException: Cannot serialize value of type
  RestaurantResponse without a serializer`). Fixed by explicitly configuring a JSON value
  serializer (`GenericJackson2JsonRedisSerializer`) instead.
- **That serializer is genuinely classic Jackson 2, not Jackson 3.** Spring Data Redis 4.1.1 hasn't
  been updated for `tools.jackson` (Jackson 3, what this Boot 4.1 app otherwise uses everywhere
  else) — `GenericJackson2JsonRedisSerializer` takes a `com.fasterxml.jackson.databind.ObjectMapper`
  specifically. Its own default constructor has no JSR-310 module registered, so it couldn't
  serialize `Instant` (confirmed: `SerializationException: Java 8 date/time type
  'java.time.Instant' not supported`). Fixed with `new ObjectMapper().findAndRegisterModules()` —
  `jackson-datatype-jsr310` (the Jackson 2 variant) is already on the classpath transitively via
  Boot's own Jackson-2-compatibility layer (`spring-boot-jackson2`), so `findAndRegisterModules()`
  picks it up via `ServiceLoader` without needing to reference the module class directly.

## Getting started

**Status today:** Live and in real use by `restaurant-service`. Verified live end to end: a `GET`
populates the cache (confirmed via `redis-cli keys '*'` and `get`), a repeat `GET` is a cache hit,
`redis-cli ttl` shows the entry counting down from two minutes, and each write path (restaurant
profile update, menu item add/update/delete) evicts exactly the affected key — confirmed by
checking `keys '*'` immediately after each write.

### How to start it
From the repo root:
```
docker compose up -d redis
```
This alone starts a single Redis 7 container, password-protected via `--requirepass` with the
default password below. **One local gotcha:** if another, unrelated Redis instance is already
bound to host port 6379 on your machine, `fdp-redis` will fail to publish its port
(`Bind for 0.0.0.0:6379 failed: port is already allocated`) — override `REDIS_PORT` in a
repo-root `.env` file (see `.env.example`) and keep `restaurant-service`'s own
`spring.data.redis.port` in sync with it.

### How to access it
- **Host/port:** `localhost:6379` by default (override via `REDIS_PORT` in a repo-root `.env`
  file — see `.env.example` and the gotcha above).
- **Default password (local dev only):** `fdp` — same value `docker-compose.yml` falls back to if
  no `.env` is present. Never used for anything but local development; production credentials come
  from environment injection (RULES.md §1 factor 3, §8).
- **From the host machine**, with the `redis-cli` client installed:
  ```
  redis-cli -h localhost -p <port> -a fdp ping
  ```
  (should return `PONG`). Inspect what's cached with `keys '*'`, `get <key>`, and `ttl <key>`.
- **From inside the Docker network** (i.e. from another container), a service's own
  `application-docker.yml` will point at the Docker service hostname, not `localhost`:
  `redis:6379` (RULES.md §10).
- **Health:** `docker compose ps redis` shows `healthy` once `redis-cli -a fdp ping` succeeds.
  There is no dashboard/UI by default.

### Endpoints it exposes
Not applicable in the REST sense — Redis exposes its own wire protocol, not HTTP. No FDP service
exposes an API through Redis directly; `restaurant-service`'s `@Cacheable`/`@CacheEvict` methods use
it internally, transparently to the API client (a cache hit and a cache miss return an identical
response body).

### Installation & dependencies
- Docker image: `redis:7-alpine` (pinned in `docker-compose.yml`).
- `restaurant-service` declares `spring-boot-starter-data-redis` (connection factory) and
  `spring-boot-starter-cache` (the `CacheManager`/`@Cacheable` abstraction Boot's Redis
  autoconfiguration plugs into) in its own `pom.xml` (RULES.md §4). `api-gateway` will pull in the
  reactive Redis integration once built (Sprint 4) — not present in its POM today (it doesn't exist
  yet).
- No local tool install is required to *run* Redis (it's fully containerized); installing the
  `redis-cli` client on the host is optional, only useful for manual inspection.

### For newcomers
Run `docker compose up -d redis`, start `restaurant-service`, then `GET
/api/restaurants/{id}` twice via Postman/curl — both return the same body, but only the first one
hits Postgres (confirmed by `redis-cli keys '*'` showing a new key after the first call). Update
that restaurant's profile and check `keys '*'` again — the entry is gone, evicted immediately
rather than waiting out its TTL. See `docs/services/restaurant-service.md` for the exact endpoints
and `./jwt.md` for the other Sprint 4 piece (security edge, `api-gateway`'s own Redis use) still not
built.

## Related
- RULES.md §1 (factor 4, factor 6, factor 8), RULES.md §12, RULES.md §10
- SPRINTS.md Sprint 4 (`api-gateway`'s rate-limiting use, not built)
- `docs/services/restaurant-service.md`
- `./jwt.md`
