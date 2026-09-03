# Eureka

## What it is
Eureka is Spring Cloud Netflix's service registry and discovery server. Services register
themselves on startup and periodically send heartbeats; clients (including the gateway and other
services' Feign clients) look up healthy instances by logical service name instead of a fixed
host/port.

## Why FDP uses it
- FDP is nine independently deployable services (RULES.md §2); none of them can hardcode where its
  peers live without violating factor 4 (backing services reachable only via config) and factor 8
  (scale by running more stateless instances) — Eureka is what makes `lb://` logical addressing
  possible (RULES.md §1 factors 4 & 8).
- `api-gateway` routes to every domain service via Eureka-resolved, load-balanced URIs rather than
  static addresses, so adding or scaling instances requires no gateway config change (RULES.md §2,
  §6).
- Feign clients resolve peer services through Eureka (e.g. `lb://restaurant-service`) instead of a
  hardcoded host, which is a hard requirement for synchronous calls in FDP (RULES.md §6).
- Sprint 1 explicitly stands up `discovery-server` before any domain service, because every later
  sprint assumes it already exists (SPRINTS.md Sprint 1, "Sequencing notes").

## Where it's used

| Service/module | Role | Sprint |
|---|---|---|
| `discovery-server` | Hosts the Eureka registry/dashboard | Sprint 1 |
| All nine services | Register with Eureka on startup | Sprint 1 onward (each service registers as it's built) |
| `api-gateway` | Resolves routes via Eureka-backed `lb://` URIs | Sprint 4 |
| `order-service`, others with Feign clients | Resolve peer services via Eureka instead of hardcoded hosts | Sprint 3 onward |

## How it's implemented in FDP
- `discovery-server` module: `spring-cloud-starter-netflix-eureka-server` dependency, annotated
  with `@EnableEurekaServer`, dashboard reachable at port `8761` (RULES.md §2; SPRINTS.md Sprint 1).
- Every other service (all nine, per RULES.md §2 service inventory) depends on
  `spring-cloud-starter-netflix-eureka-client` and registers with `discovery-server` on startup.
- `api-gateway` route definitions use `lb://<service-name>` URIs resolved through Eureka rather
  than static hosts (RULES.md §2, §6).
- Config (Eureka server URL) is externalized via `config-server` plus `application-{profile}.yml`
  — never hardcoded per service (RULES.md §1 factor 3).
- Docker Compose service name: `discovery-server`, matching the module name; other services'
  `depends_on: condition: service_healthy` ensures `discovery-server` is ready before dependents
  start (RULES.md §10).

## Related
- `RULES.md §2` (service inventory, port 8761), `RULES.md §6` (communication rules — `lb://`
  resolution), `RULES.md §1` factors 4 & 8
- `SPRINTS.md` Sprint 1 (discovery-server stood up), Sprint 4 (gateway load-balanced routing)
- `./spring-cloud-gateway.md`, `./spring-cloud-config.md`
