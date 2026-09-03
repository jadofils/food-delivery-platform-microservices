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

## API surface (planned)
Exposes Spring Cloud Config Server's standard configuration-serving endpoints so each registered
client (`discovery-server`, `api-gateway`, `identity-service`, `customer-service`, and the rest of
the nine services) can pull its `application-{profile}.yml` structure at startup. It does not
expose any domain/business REST API — its surface is purely configuration delivery per RULES.md
§1 (factor 3) and §2.

## Depends on / depended on by
- **Depends on:** nothing at the platform level — it is one of the two foundational services
  (alongside `discovery-server`) that the rest of the system boots against.
- **Depended on by:** every other service in the inventory pulls its externalized config from
  `config-server` on startup (RULES.md §2, §10 — `docker-compose.yml` `depends_on:
  condition: service_healthy` ensures `config-server` is ready before dependents start).

## Delivered in
Sprint 1 — "Identity, discovery, config" (SPRINTS.md). Exit criteria for Sprint 1 require
`identity-service` to pull config from `config-server`, so `config-server` must be functioning
before that sprint closes.

## Related
- RULES.md §2 (Service inventory), §1 factor 3 (Config)
- SPRINTS.md — Sprint 1
- [`./discovery-server.md`](./discovery-server.md) — stood up in the same sprint as the other half
  of the platform's spine
