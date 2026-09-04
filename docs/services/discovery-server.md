# discovery-server

## Responsibility
Runs the Eureka service registry for the platform (RULES.md §2). All eight services are designed
to register with it on startup, and it exposes a dashboard at `:8761` showing every registered
service instance.

## Why it's a separate service
Service discovery is infrastructure that every other service depends on, not domain logic owned
by any one of them. It must be reachable before any domain service starts (SPRINTS.md
sequencing notes: "no domain service should be started before `discovery-server`/
`config-server`/Keycloak exist"), so it has to be independently deployable and independently
startable rather than bundled into another service.

## Database
None. `discovery-server` has no datastore of its own (RULES.md §2) — its registry is in-memory
service metadata, not persisted domain data.

## API surface (planned)
Exposes Eureka's standard service-registry endpoints (instance registration, heartbeat,
deregistration) consumed by every other service's Eureka client, plus the human-facing dashboard
at `http://localhost:8761` (RULES.md §2; ReadMe.md Epic 3, user story 3.1). It does not expose a
domain/business REST API.

## Depends on / depended on by
- **Depends on:** nothing at the platform level — it is one of the two foundational services
  (alongside `config-server`) the rest of the system boots against.
- **Depended on by:** every other service registers with it so services can find each other by
  logical name instead of hardcoded host/IP (RULES.md §1 factor 8; ReadMe.md Epic 3, user story
  3.1). `api-gateway` and Feign-calling services (e.g. `order-service`) resolve peers via Eureka
  `lb://` URIs.

## Delivered in
Sprint 1 — "Identity, discovery, config" (SPRINTS.md). Sprint 1 stands up `discovery-server`
itself; the first FDP service to actually register with it is `customer-service` in Sprint 2
(Keycloak, Sprint 1's identity provider, is external infrastructure, not a Eureka client).

## Related
- RULES.md §2 (Service inventory), §1 factor 8 (Concurrency)
- SPRINTS.md — Sprint 1
- [`./config-server.md`](./config-server.md) — the other half of the platform's spine, stood up in
  the same sprint
- [`./api-gateway.md`](./api-gateway.md) — resolves all downstream routes via Eureka
