# config-server

## Responsibility
Provides centralized externalized configuration for every other service in the platform
(RULES.md §2). It is designed to serve `application-{profile}.yml` structure to registered
clients on startup so no service hardcodes hostnames, credentials, queue names, or feature flags
in code (RULES.md §1, factor 3).

## Why it's a separate service
Configuration delivery is infrastructure, not domain logic — it has no business data of its own
and every other service depends on it being available before it starts. Keeping it as its own
deployable unit means config can be updated and redistributed without touching or redeploying any
domain service, and it can be scaled or restarted independently of the services that consume it.

## Database
None. `config-server` has no datastore of its own (RULES.md §2) — it serves configuration, it
does not persist domain data.

## API surface
Exposes Spring Cloud Config Server's standard configuration-serving endpoints so each registered
client (`discovery-server`, `api-gateway`, `customer-service`, and the rest of
the eight services) can pull its `application-{profile}.yml` structure at startup. It does not
expose any domain/business REST API — its surface is purely configuration delivery per RULES.md
§1 (factor 3) and §2.

**Verified live:** `GET /application/default` and `GET /application/docker` against the packaged
jar on `:8888` — `default` returns only `config-repo/application.yml` (Eureka `defaultZone`
pointed at `localhost:8761`); `docker` returns `config-repo/application-docker.yml` layered over
(and overriding) `application.yml`, correctly resolving to the `discovery-server` container
hostname.

## Config backend
Native (filesystem-backed) profile: `spring.cloud.config.server.native.search-locations=classpath:/config-repo`,
with the config files bundled directly into this service's own jar under
`src/main/resources/config-repo/` rather than pulled from a separate git repository. Chosen over a
git-backed backend as the simpler option for a monorepo where the config content isn't independently
versioned from the code that consumes it (RULES.md §4's "don't over-engineer ahead of need"). A file
named `application.yml` applies to every client regardless of `spring.application.name`; a
per-service override (`<service-name>.yml` / `<service-name>-<profile>.yml`) gets added in the
sprint that introduces that service's first environment-specific config need.

## Depends on / depended on by
- **Depends on:** nothing at the platform level — it is one of the two foundational services
  (alongside `discovery-server`) that the rest of the system boots against.
- **Depended on by:** every other service in the inventory pulls its externalized config from
  `config-server` on startup (RULES.md §2, §10 — `docker-compose.yml` `depends_on:
  condition: service_healthy` ensures `config-server` is ready before dependents start).

## Delivered in
Sprint 1 — "Identity, discovery, config" (SPRINTS.md). Sprint 1 stands up `config-server` itself
(Keycloak, its identity-provider counterpart in that sprint, is external infrastructure and isn't
an FDP Spring service pulling config) — its first real FDP consumer is `customer-service` in
Sprint 2, which must be functioning before that sprint closes.

## Related
- RULES.md §2 (Service inventory), §1 factor 3 (Config)
- SPRINTS.md — Sprint 1
- [`./discovery-server.md`](./discovery-server.md) — stood up in the same sprint as the other half
  of the platform's spine
