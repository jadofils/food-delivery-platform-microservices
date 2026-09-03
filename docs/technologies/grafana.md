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
| All nine services in the §2 inventory (source of `/actuator/prometheus` metrics, visualized in Grafana) | Sprint 9 |
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

## Related
- RULES.md §13 (observability, visualization layer)
- SPRINTS.md Sprint 6 (Actuator/metrics exposed), Sprint 9 (Grafana added)
- `./prometheus.md`, `./resilience4j.md`
