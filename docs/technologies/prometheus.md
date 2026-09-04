# Prometheus

## What it is
Prometheus is a metrics collection and time-series storage system. It works on a pull model: it
scrapes metrics from HTTP endpoints on a schedule rather than having applications push data to it.

## Why FDP uses it
- RULES.md §13: every service exposes Actuator's `/actuator/prometheus` endpoint via the
  Micrometer Prometheus registry; Prometheus is the tool that scrapes it.
- RULES.md §13 is explicit that this is a deliberately aggregate, complementary view: "Prometheus/
  Grafana metrics are aggregate, not per-request... a dashboard tells you *that* latency spiked, a
  trace tells you *which* request and why" — it is not part of the per-request "one correlation
  ID, three places" model (Zipkin/Kibana/`traceId`), it sits alongside it.
- SPRINTS.md Sprint 9: added deliberately last, once services, gateway, messaging, tracing, and
  logging are already functioning end to end — Prometheus visualizes metrics Actuator/Micrometer
  have already been exposing since Sprint 6, so nothing earlier is blocked waiting on it.

## Where it's used

| Component | Role | Sprint |
|---|---|---|
| All eight services (RULES.md §2) | Expose `/actuator/prometheus` for Prometheus to scrape | Sprint 6 (endpoint exposed) / Sprint 9 (scraped) |
| Prometheus itself | Added to `docker-compose.yml`, scrapes every service | Sprint 9 |

Sprint 9 exit criteria (SPRINTS.md): dashboards sourced from Prometheus for all eight services,
"with no service needing code changes to be scraped (Actuator + Micrometer already expose
everything Sprint 6 configured)."

## How it's implemented in FDP
- Dependency: the Micrometer Prometheus registry (`micrometer-registry-prometheus`) in every
  service, which is what backs the `/actuator/prometheus` endpoint alongside `/actuator/health`,
  `/actuator/metrics`, and `/actuator/circuitbreakers` (RULES.md §13). This is configured from
  Sprint 6 onward, before Prometheus itself is scraping anything.
- Docker Compose service name: `prometheus`. RULES.md §10 requires it defined in
  `docker-compose.yml` with a mandatory health check; RULES.md/SPRINTS.md do not pin an explicit
  host port — the Prometheus image's conventional port is `9090`.
- Scrape configuration targets each service's `/actuator/prometheus` endpoint (SPRINTS.md Sprint
  9); no service code changes are required to be scraped, since the endpoint already exists from
  Sprint 6.
- Grafana (a separate technology doc, out of scope here) is added in the same sprint as the
  dashboard layer that reads from Prometheus.

## Getting started

**Status today:** Not yet stood up. Prometheus has no entry in `docker-compose.yml` (only
`postgres`, `mongodb`, `rabbitmq`, `redis`, and `keycloak` are defined there today) and no service
carries the Micrometer Prometheus registry dependency yet. This is entirely Sprint 9 work
("Metrics visualization"), the last sprint in `SPRINTS.md`, deliberately added after every other
piece of the platform — services, gateway, messaging, tracing, logging — is functioning end to end.

### How to start it
Nothing to start today — there is no container and no config to point it at. Once Sprint 9 adds a
`prometheus` service to `docker-compose.yml` (RULES.md §10, with the mandatory health check every
compose service requires), it will start the same way every other infra container does:
```
docker compose up -d prometheus
```
RULES.md/SPRINTS.md don't pin an explicit host port for it; the Prometheus image's conventional
port is `9090`, so `9090:9090` is the expected mapping when that entry is written.

### How to access it
Not accessible today — nothing is running. Once stood up, Prometheus's stock web UI/API is
conventionally reachable at `http://localhost:9090`, where you'd be able to browse targets
(`/targets`) and run ad-hoc PromQL queries against whatever it has scraped.

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| `GET /api/v1/query` | Prometheus's own instant-query API (PromQL) | Not deployed yet |
| `GET /targets` | Scrape target health (its own UI page) | Not deployed yet |

Separately, every FDP service is meant to expose `/actuator/prometheus` for Prometheus to scrape
(RULES.md §13) — but that endpoint doesn't exist on any service today either, since no service has
added `spring-boot-starter-actuator` + `micrometer-registry-prometheus` yet (verified: no
`pom.xml` in the repo references either artifact). That pairing is Sprint 6 work (exposing the
endpoint) followed by Sprint 9 (actually scraping it).

### Installation & dependencies
- Nothing to install today. When Sprint 9 arrives, Prometheus itself needs no service-side
  dependency — it's a standalone scraper — but it can only scrape something once each service adds
  `micrometer-registry-prometheus` alongside `spring-boot-starter-actuator` (Sprint 6, RULES.md
  §13), version-managed by the root aggregator POM, never pinned per-service (RULES.md §4).

### For newcomers
There is nothing to run or click through yet for this one — that's expected, not a gap. Read
`RULES.md §13` (Observability) for how metrics fit alongside tracing and logging, and
`SPRINTS.md` Sprint 9 for exactly what gets built and when. The destination: once live, Prometheus
will be scraping request rate/latency, JVM and DB connection pool health, and Resilience4j
circuit-breaker state from every one of the eight services, all sourced from the
`/actuator/prometheus` endpoint each service already exposes since Sprint 6 — Grafana (see
`./grafana.md`) is the dashboard layer built on top of it in the same sprint.

## Related
- `RULES.md §13` (observability — metrics and visualization)
- `SPRINTS.md` Sprint 9 (Metrics visualization)
- `./zipkin.md`, `./kibana.md`
