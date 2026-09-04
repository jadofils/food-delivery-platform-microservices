# Grafana

## What it is
Grafana is a dashboard and visualization layer that queries a metrics backend and renders it as
graphs and panels. In FDP it sits on top of Prometheus, which is what actually scrapes and stores
the metrics.

## Why FDP uses it
- Prometheus/Micrometer expose raw time-series metrics, not a human-readable view — Grafana is the
  "dashboard layer" that turns `/actuator/prometheus` scrapes into request rate/latency, JVM/DB
  pool, and circuit-breaker dashboards (RULES.md §13).
- It is deliberately the last piece of the observability stack, added only once services, gateway,
  messaging, tracing, and logging are already functioning end to end — visualization is not a
  prerequisite for building the rest of the system (RULES.md §13; SPRINTS.md Sprint 9).
- It completes the "one correlation ID, three places" model as a fourth, aggregate view: Grafana
  shows *that* latency spiked, while a trace ID in Zipkin/Kibana/error responses shows *which*
  request and why — the two are complementary, not overlapping (RULES.md §13).
- No service needs code changes for Grafana to have data to show — Actuator and Micrometer have
  already exposed everything since Sprint 6, so Grafana is purely an additive dashboard pass
  (SPRINTS.md Sprint 9 exit criteria).

## Where it's used

| Service/module | Sprint introduced |
|---|---|
| All eight services in the §2 inventory (source of `/actuator/prometheus` metrics, visualized in Grafana) | Sprint 9 |
| `docker-compose.yml` (`grafana` service, sitting on top of `prometheus`) | Sprint 9 |

## How it's implemented in FDP
- Added to `docker-compose.yml` as a service on top of `prometheus`, per RULES.md §10's full
  infrastructure list (Postgres, MongoDB, RabbitMQ, Redis, Zipkin, Elasticsearch, Logstash, Kibana,
  Prometheus, Grafana). RULES.md/SPRINTS.md do not fix a specific host port for Grafana; that is
  decided when the Sprint 9 `docker-compose.yml` entry is written.
- Baseline dashboards cover request rate/latency, JVM and DB connection pool health, and
  Resilience4j circuit-breaker state per service, all sourced from Prometheus's scrape of each
  service's `/actuator/prometheus` endpoint (RULES.md §13; SPRINTS.md Sprint 9).
- Cross-checked against Zipkin/Kibana as a validation step: a latency spike visible in a Grafana
  panel should be traceable down to an individual request in Zipkin and its correlated logs in
  Kibana (RULES.md §13; SPRINTS.md Sprint 9).
- No application-level dependency or config key is added to any service for Grafana itself — it
  consumes Prometheus's scraped data, and every service's Actuator/Micrometer exposure was already
  in place since Sprint 6.

## Getting started

**Status today:** Not yet stood up. Grafana has no entry in `docker-compose.yml` (only `postgres`,
`mongodb`, `rabbitmq`, `redis`, and `keycloak` are defined there today), and since it sits entirely
on top of Prometheus (see `./prometheus.md`), it can't do anything useful before that exists
either. Like Prometheus, this is entirely Sprint 9 work ("Metrics visualization"), the last sprint
in `SPRINTS.md`.

### How to start it
Nothing to start today. Once Sprint 9 adds a `grafana` service to `docker-compose.yml` on top of
`prometheus` (RULES.md §10, with the mandatory health check), it will start the same way every
other infra container does:
```
docker compose up -d grafana
```
RULES.md/SPRINTS.md don't fix a specific host port for it; Grafana's stock image conventionally
listens on `3000`, so `3000:3000` is the expected mapping when that entry is written.

### How to access it
Not accessible today — nothing is running. Once stood up, Grafana's stock web UI is conventionally
reachable at `http://localhost:3000`, where the baseline dashboards described below would live,
sourced from a Prometheus datasource pointed at the `prometheus` container.

### Endpoints it exposes
None, and this stays true even after Sprint 9 — Grafana is purely a dashboard UI that reads from
Prometheus's query API; it exposes no FDP-specific REST endpoint of its own for other services to
call.

### Installation & dependencies
- Nothing to install today. When Sprint 9 arrives, Grafana needs no application-level dependency
  or config key added to any service (RULES.md §13) — it consumes Prometheus's already-scraped
  data, so the only prerequisite is Prometheus itself being live and scraping.

### For newcomers
There is nothing to run or click through yet — that's expected, not a gap. Read `RULES.md §13`
(Observability) for how the visualization layer fits alongside tracing and logging, and
`SPRINTS.md` Sprint 9 for exactly what gets built and when. The destination: once live, Grafana's
baseline dashboards will show request rate/latency, JVM and DB connection pool health, and
Resilience4j circuit-breaker state per service, all sourced from Prometheus — and cross-checkable
against Zipkin/Kibana, so a latency spike visible in a Grafana panel can be traced down to an
individual request and its correlated logs (RULES.md §13's "one correlation ID, three places",
extended by this aggregate view).

## Related
- RULES.md §13 (observability, visualization layer)
- SPRINTS.md Sprint 6 (Actuator/metrics exposed), Sprint 9 (Grafana added)
- `./prometheus.md`, `./resilience4j.md`
