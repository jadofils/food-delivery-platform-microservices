# Spring Cloud Config

## What it is
Spring Cloud Config provides a server (`config-server`) and client library for centralized,
externalized configuration. Client services fetch their configuration from the server at startup
instead of embedding it in their own packaged artifact.

## Why FDP uses it
- Twelve-factor rule 3 (config) is explicit and non-negotiable for FDP: no hostnames, credentials,
  queue names, or feature flags in code — config comes from Spring Cloud Config Server plus
  environment variables / Docker secrets (RULES.md §1 factor 3, §2).
- The same Docker image must be promotable across environments unchanged (factor 5, build/release/
  run); that's only possible if environment-specific values are injected at run time by
  `config-server` rather than baked into the image at build time (RULES.md §1 factor 5).
- A service must be able to point at a different Postgres instance by changing config, not code —
  this is factor 4 (backing services), and `config-server` is the mechanism (RULES.md §1 factor 4).
- Secrets specifically (DB credentials, RabbitMQ credentials) never live in `config-repo/` — that
  directory holds only non-secret structure (RULES.md §1 factor 3); actual credentials are
  injected via environment variables / Docker secrets at the consuming service, layered on top of
  what `config-server` serves.

## Where it's used

| Service/module | Role | Sprint |
|---|---|---|
| `config-server` | Hosts centralized config for every other service | Sprint 1 |
| All eight services | Pull their config from `config-server` on startup | Sprint 1 onward |

## How it's implemented in FDP
- `config-server` module: `spring-cloud-config-server` dependency, annotated with
  `@EnableConfigServer`, running on port `8888` (RULES.md §2; SPRINTS.md Sprint 1). **Done and
  verified live** — native (filesystem-backed) profile, `config-repo/` bundled into the service's
  own jar rather than a separate git repo; `GET /application/{profile}` confirmed serving the
  right layered properties for both `default` and `docker`.
- Every other client service depends on `spring-cloud-starter-config` to pull its configuration
  from `config-server` at startup.
- `application-{profile}.yml` (`default`, `docker`, more added as later sprints need them) selects
  the environment — it contains structure only, never secrets; secrets come from environment
  variables / Docker secrets layered on top (RULES.md §1 factor 3).
- Each service also ships an `application-docker.yml` using Docker service hostnames (e.g.
  `jdbc:postgresql://postgres:5432/order_db`) rather than `localhost`, consumed once running under
  `docker-compose` (RULES.md §10; SPRINTS.md Sprint 7).
- Docker Compose service name: `config-server`; other services' `depends_on: condition:
  service_healthy` ensures it (and `discovery-server`) are ready before dependents start (RULES.md
  §10).

## Getting started

**Status today:** Live and verified — `config-server` correctly serves both `config-repo/`
profiles it currently holds (`default`, `docker`). No other service depends on it as a
Spring Cloud Config *client* yet: `api-gateway` and all five domain services are still bare
skeletons (`spring-boot-starter` + `spring-boot-starter-test` only), so "all eight services pull
their config from `config-server` on startup" is what Sprint 1 onward builds toward, not today's
reality — the first real client is `customer-service`, Sprint 2.

### How to start it
From the repo root:
```
./mvnw -pl config-server -am spring-boot:run
```
or package and run the jar directly:
```
./mvnw -pl config-server -am package -DskipTests
java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar
```
No external dependency is required to start it — like `discovery-server`, it needs no database
and no other running service.

### How to access it
- **Base URL:** `http://localhost:8888`. There is no dashboard — Config Server is a pure REST API,
  not a UI.
- **Config-serving endpoint:** `GET /{application}/{profile}` — `{application}` is the requesting
  service's `spring.application.name` (today, everything falls back to the literal file named
  `application.yml`, since no per-service config file exists yet); `{profile}` is
  `default` or `docker`. Confirmed live:
  ```
  curl http://localhost:8888/application/default
  curl http://localhost:8888/application/docker
  ```
  The `docker` profile response correctly layers `application-docker.yml` over (and overriding)
  `application.yml` — verified by inspecting the `propertySources` array in the response.
- **Health:** `http://localhost:8888/actuator/health` (once Actuator is added — not yet a
  dependency of this module; see the pom.xml note below).

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| `GET /{application}/{profile}` | Serves layered config for a given service + profile | Live |
| `GET /{application}/{profile}/{label}` | Same, pinned to a specific git label — not meaningful under the native/filesystem backend FDP uses (no git label concept) | Not applicable to this backend |
| `GET /actuator/health` | Liveness/readiness | Not yet — `spring-boot-starter-actuator` isn't a dependency of `config-server` yet |

`config-server` exposes no business/domain endpoints — like `discovery-server`, its entire surface
is infrastructure-serving, not a RULES.md §2 service API.

### Installation & dependencies
- Maven, already in `config-server/pom.xml`: `spring-boot-starter-web`,
  `org.springframework.cloud:spring-cloud-config-server` (version managed by the root aggregator's
  `spring-cloud-dependencies` BOM, RULES.md §4). A future client service adds
  `spring-cloud-starter-config` instead.
- No local tool install needed beyond a JDK 25 + the vendored Maven wrapper (`./mvnw`).

### For newcomers
Run the two commands above, then `curl http://localhost:8888/application/default` — you should
get back one JSON object with a `propertySources` array containing the Eureka `defaultZone` key
from `config-repo/application.yml`. Try `.../application/docker` next and compare: same key,
different value, because `application-docker.yml` overrides it. That layering is the entire point
of this service — every future domain service will fetch exactly this shape of response instead
of hardcoding its own `application.yml` values. See `./eureka.md` for the other half of the
platform's spine, and `docs/services/config-server.md` for this service's own reference doc.

## Related
- `RULES.md §1` factor 3 (config) and factor 5 (build, release, run), `RULES.md §2` (port 8888)
- `SPRINTS.md` Sprint 1 (config-server stood up), Sprint 7 (per-environment `application-docker.yml`
  finalized)
- `./eureka.md`
