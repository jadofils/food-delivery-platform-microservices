# Docker

## What it is
Docker is the container runtime and image format FDP's services run in. In this document "Docker"
specifically covers the hand-written, multi-stage `Dockerfile` each service maintains (build stage
→ slim runtime stage) and the `docker-compose.yml` orchestration layer — as distinct from Jib,
which is the separate, CI-driven image-build path (see `./jib.md`).

## Why FDP uses it
- `docker-compose.yml` is what gives FDP dev/prod parity: it runs the same Postgres, MongoDB,
  RabbitMQ, and Redis versions locally as production, with no H2, no embedded Mongo, and no
  in-memory broker substituted anywhere, not even in tests (RULES.md §1 factor 10, RULES.md §9).
- A hand-written multi-stage `Dockerfile` per service is maintained deliberately, alongside Jib, for
  learning purposes and for anyone who wants to `docker build` a service manually without Maven —
  it is not what CI runs (RULES.md §10).
- `docker-compose.yml` is the single command that starts the complete system — all nine services
  plus every backing infrastructure container — which is the concrete deliverable Sprint 7 and the
  base assignment (`ReadMe.md` Epic 2) both require (RULES.md §10; SPRINTS.md Sprint 7; `ReadMe.md`
  Epic 2).
- Health checks and `depends_on: condition: service_healthy` are mandatory so that
  `discovery-server`/`config-server` and infra (databases, broker) are ready before dependents
  start — required for the system to come up correctly from a clean checkout with no manual steps
  (RULES.md §10; SPRINTS.md Sprint 7 exit criteria).

## Where it's used

| Service/module | Sprint introduced |
|---|---|
| `docker-compose.yml` skeleton (Postgres, MongoDB, RabbitMQ, Redis only, no services) | Sprint 0 |
| Multi-stage `Dockerfile` + `.dockerignore` per service; full `docker-compose.yml` (all nine services + all infra, health checks, `depends_on` ordering); `application-docker.yml` per service | Sprint 7 |

## How it's implemented in FDP
- Each service's `Dockerfile` uses a build stage (compiles the Maven module) and a slim runtime
  stage (copies only the built artifact) — kept for learning/manual use, not invoked by
  `docker-compose.yml`'s standard flow, which instead references Jib-built images (RULES.md §10).
- Each service's runtime dependencies changing requires updating both the Jib config and this
  Dockerfile — the two build paths must not silently diverge (RULES.md §10).
- Each service ships an `application-docker.yml` profile using Docker service names for hostnames
  (e.g. `jdbc:postgresql://postgres:5432/order_db`) and environment variables for secrets, never a
  hardcoded `localhost` (RULES.md §10).
- `docker-compose.yml` at the repo root defines all nine services from the §2 inventory plus
  Postgres, MongoDB, RabbitMQ, Redis, Zipkin, Elasticsearch, Logstash, Kibana, Prometheus, and
  Grafana (RULES.md §10, §13), with health checks and `depends_on: condition: service_healthy` on
  every container.
- Postgres-backed services may share one Postgres server container locally (five logical databases
  via init scripts) while keeping datasource credentials, connection pools, and Flyway migration
  history fully isolated per service (RULES.md §5).
- `.dockerignore` files per service exclude build artifacts, added alongside each service's
  Dockerfile in Sprint 7 (SPRINTS.md Sprint 7).

## Related
- RULES.md §1 (factor 5, factor 10), RULES.md §5, RULES.md §9, RULES.md §10, RULES.md §13
- SPRINTS.md Sprint 0, Sprint 7
- `./jib.md`, `./testcontainers.md`
