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

## Getting started

**Status today:** The container is live (part of the Sprint 1 `docker-compose.yml` additions) —
but nothing uses it yet. `api-gateway` and `restaurant-service` are both still bare skeletons
(`spring-boot-starter` + `spring-boot-starter-test` only, per each module's own `pom.xml`), with no
Redis-related dependency and no cache/rate-limit config. Sprint 4 (`api-gateway` rate limiting) and
later `restaurant-service` menu caching are the planned first consumers (SPRINTS.md, Sprint 4).
This is planned — Sprint 4, not yet implemented.

### How to start it
From the repo root:
```
docker compose up -d redis
```
This alone (no `.env` file needed) starts a single Redis 7 container, password-protected via
`--requirepass` with the default password below.

### How to access it
- **Host/port:** `localhost:6379` (override via `REDIS_PORT` in a repo-root `.env` file — see
  `.env.example`).
- **Default password (local dev only):** `fdp` — same value `docker-compose.yml` falls back to if
  no `.env` is present. Never used for anything but local development; production credentials come
  from environment injection (RULES.md §1 factor 3, §8).
- **From the host machine**, with the `redis-cli` client installed:
  ```
  redis-cli -h localhost -a fdp ping
  ```
  (should return `PONG`).
- **From inside the Docker network** (i.e. from another container), a service's own
  `application-docker.yml` will point at the Docker service hostname, not `localhost`:
  `redis:6379` (RULES.md §10).
- **Health:** `docker compose ps redis` shows `healthy` once `redis-cli -a fdp ping` succeeds.
  There is no dashboard/UI by default.

### Endpoints it exposes
Not applicable in the REST sense — Redis exposes its own wire protocol on port `6379`, not HTTP.
No FDP service exposes an API through Redis directly; `api-gateway`'s rate-limit filter and
`restaurant-service`'s cache lookups will use it internally, once built.

### Installation & dependencies
- Docker image: `redis:7-alpine` (pinned in `docker-compose.yml`).
- `api-gateway` will pull in the reactive Redis integration alongside
  `spring-cloud-starter-gateway` once built (RULES.md §4, RULES.md §12); `restaurant-service` will
  add Spring's Redis-backed cache starter once built — neither is present in either POM today.
- No local tool install is required to *run* Redis (it's fully containerized); installing the
  `redis-cli` client on the host is optional, only useful for manual inspection.

### For newcomers
Run `docker compose up -d redis`, then check `redis-cli -h localhost -a fdp ping` returns `PONG`
to confirm it's up. There's nothing cached yet: no service reads or writes a single key, since
rate limiting and menu caching don't exist until Sprint 4. This container being live and healthy
is ahead-of-need infrastructure, not a sign anything domain-specific is running. See `./jwt.md`
for the other Sprint 4 piece (security edge) landing alongside it.

## Related
- RULES.md §1 (factor 4, factor 6, factor 8), RULES.md §12, RULES.md §10
- SPRINTS.md Sprint 4
- `./jwt.md`
