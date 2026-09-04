# Eureka

## What it is
Eureka is Spring Cloud Netflix's service registry and discovery server. Services register
themselves on startup and periodically send heartbeats; clients (including the gateway and other
services' Feign clients) look up healthy instances by logical service name instead of a fixed
host/port.

## Why FDP uses it
- FDP is eight independently deployable services (RULES.md §2); none of them can hardcode where its
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
| All eight services | Register with Eureka on startup | Sprint 1 onward (each service registers as it's built) |
| `api-gateway` | Resolves routes via Eureka-backed `lb://` URIs | Sprint 4 |
| `order-service`, others with Feign clients | Resolve peer services via Eureka instead of hardcoded hosts | Sprint 3 onward |

## How it's implemented in FDP
- `discovery-server` module: `spring-cloud-starter-netflix-eureka-server` dependency, annotated
  with `@EnableEurekaServer`, dashboard reachable at port `8761` (RULES.md §2; SPRINTS.md Sprint 1).
  Standalone mode — `eureka.client.register-with-eureka=false` and `fetch-registry=false`, since
  this instance *is* the registry, not a client of itself. Verified live: dashboard and
  `/actuator/health` both respond, correctly showing "No instances available" until a real client
  exists.
- **Version note, relevant to every future service that adds the Eureka *client* starter too:**
  no released Spring Cloud train is binary-compatible with Boot 4.1.1 yet — even the newest
  milestone (`2025.0.0-RC1`) still references a Boot package path
  (`org.springframework.boot.web.context.WebServerInitializedEvent`) that moved in Boot 4.1
  (to `org.springframework.boot.web.server.context`), and fails at context startup with a
  `NoClassDefFoundError`. The root aggregator's `spring-cloud.version` is pinned to a `2025.1.x`
  snapshot instead — see the version note directly in the root `pom.xml` for why, and bump it to
  a real release (dropping the snapshot repository alongside it) the moment one exists.
- Every other service (all eight, per RULES.md §2 service inventory) depends on
  `spring-cloud-starter-netflix-eureka-client` and registers with `discovery-server` on startup.
- `api-gateway` route definitions use `lb://<service-name>` URIs resolved through Eureka rather
  than static hosts (RULES.md §2, §6).
- Config (Eureka server URL) is externalized via `config-server` plus `application-{profile}.yml`
  — never hardcoded per service (RULES.md §1 factor 3).
- Docker Compose service name: `discovery-server`, matching the module name; other services'
  `depends_on: condition: service_healthy` ensures `discovery-server` is ready before dependents
  start (RULES.md §10).

## Getting started

**Status today:** Live and verified — `discovery-server` is a fully working Eureka registry.
Every other service (`api-gateway`, `customer-service`, `restaurant-service`, `order-service`,
`delivery-service`, `notification-service`) is still a bare skeleton (`spring-boot-starter` +
`spring-boot-starter-test` only, per each module's own `pom.xml`) — none of them carries the
Eureka *client* starter yet, so "all eight services register on startup" (see `Where it's used`
above) is what Sprint 1 onward builds toward, not today's reality. The first real client is
`customer-service`, Sprint 2.

### How to start it
From the repo root:
```
./mvnw -pl discovery-server -am spring-boot:run
```
or package and run the jar directly:
```
./mvnw -pl discovery-server -am package -DskipTests
java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar
```
No external dependency is required — `discovery-server` needs no database, no broker, and no
other running service. It's one of the two things (with `config-server`) everything else in the
platform depends on existing first (SPRINTS.md Sprint 1).

### How to access it
- **Dashboard:** `http://localhost:8761` — the stock Eureka web UI: registered instances, their
  status, and renewal (heartbeat) info. Right now it correctly shows "No instances available"
  (empty), because no client has registered yet — that's expected, not broken.
- **Health:** `http://localhost:8761/actuator/health` → `{"status":"UP"}` once started.
- **Registry API** (what a real client talks to — not something a person normally calls by hand):
  `curl http://localhost:8761/eureka/apps` returns the full registry as XML, a quick way to check
  what's registered without opening the dashboard.

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| `GET /` | Eureka dashboard (HTML) | Live |
| `GET /actuator/health` | Liveness/readiness | Live |
| `GET /eureka/apps` | Full registry, machine-readable | Live |
| `POST /eureka/apps/{appName}` | Client instance registration (called by Eureka clients, not by hand) | Live, but nothing registers yet |

`discovery-server` exposes no business/domain endpoints of its own — it's infrastructure, not a
service in the RULES.md §2 sense with its own API surface.

### Installation & dependencies
- Maven: `org.springframework.cloud:spring-cloud-starter-netflix-eureka-server` (server side,
  `discovery-server` only, already added) / `spring-cloud-starter-netflix-eureka-client` (every
  other service, added when that service is built) — version managed by the root aggregator's
  `spring-cloud-dependencies` BOM import, never pinned per-service (RULES.md §4).
- No local tool install needed beyond a JDK 25 + the vendored Maven wrapper (`./mvnw`) to run it
  directly, or Docker once its container image exists (Sprint 7).

### For newcomers
Start here if you're new to the platform's service-discovery piece: run the commands above, open
`http://localhost:8761` in a browser, and you'll see an empty dashboard. The `lb://<service-name>`
addressing scheme every Feign client and gateway route will eventually use (RULES.md §6) only
works once a service actually registers here — which is exactly why Sprint 1 stands this up
before any domain service exists. See `./spring-cloud-config.md` for the other half of the
platform's spine, and `docs/services/discovery-server.md` for this service's own reference doc.

## Related
- `RULES.md §2` (service inventory, port 8761), `RULES.md §6` (communication rules — `lb://`
  resolution), `RULES.md §1` factors 4 & 8
- `SPRINTS.md` Sprint 1 (discovery-server stood up), Sprint 4 (gateway load-balanced routing)
- `./spring-cloud-gateway.md`, `./spring-cloud-config.md`
