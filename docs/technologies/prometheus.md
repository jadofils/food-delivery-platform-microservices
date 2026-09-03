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
| All nine services (RULES.md §2) | Expose `/actuator/prometheus` for Prometheus to scrape | Sprint 6 (endpoint exposed) / Sprint 9 (scraped) |
| Prometheus itself | Added to `docker-compose.yml`, scrapes every service | Sprint 9 |

Sprint 9 exit criteria (SPRINTS.md): dashboards sourced from Prometheus for all nine services,
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

## Related
- `RULES.md §13` (observability — metrics and visualization)
- `SPRINTS.md` Sprint 9 (Metrics visualization)
- `./zipkin.md`, `./kibana.md`
