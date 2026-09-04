# Kibana

## What it is
Kibana is the query and visualization/dashboard UI over data stored in Elasticsearch. In FDP it is
the read/query layer over the centrally aggregated application logs Logstash ships from every
service.

## Why FDP uses it
- RULES.md §13: "Logstash tails container output and ships to Elasticsearch; Kibana is the
  query/dashboard layer" — Kibana is the tool an engineer actually uses to find and read logs.
- RULES.md §13/§14: Kibana is one of the "three places" the same correlation ID appears — Zipkin,
  every Kibana log line for that request, and the `traceId` field of any error response — so a log
  line for a failed request can always be found by filtering Kibana on the trace ID from a client
  error report.
- Because Micrometer Tracing populates the trace/span ID into MDC automatically (RULES.md §13),
  every JSON log line already carries the trace ID field before it reaches Kibana — no per-service
  log-format or dashboard wiring is needed to make trace-ID filtering work.

## Where it's used
Kibana is infrastructure queried by developers/operators; no service calls it.

| Component | Role | Sprint |
|---|---|---|
| Kibana itself | Added to `docker-compose.yml` alongside Elasticsearch and Logstash | Sprint 6 |

Sprint 6 exit criteria (SPRINTS.md): a single order can be traced end-to-end in Zipkin, "and its
logs can be found in Kibana filtered by that trace ID."

## How it's implemented in FDP
- No application dependency — no service talks to Kibana; it reads from Elasticsearch only.
- Docker Compose service name: `kibana`. RULES.md §10 requires it defined in
  `docker-compose.yml` with a mandatory health check and correct `depends_on` startup ordering
  relative to Elasticsearch. RULES.md/SPRINTS.md do not pin an explicit host port — the Kibana
  image's conventional UI port is `5601`.
- Backing datastore: the `elasticsearch` Docker Compose service.
- Filtering/searching by trace ID works out of the box because every service's stdout JSON log
  line already contains the trace/span ID (populated into MDC by Micrometer Tracing, RULES.md
  §13) by the time Logstash indexes it in Elasticsearch.

## Getting started

**Status today:** Not yet stood up. Kibana has no service block in the repo-root
`docker-compose.yml` (which today only defines `postgres`, `mongodb`, `rabbitmq`, `redis`, and
`keycloak` — Sprint 0/1 scope) and no service's `pom.xml` depends on it. This is entirely Sprint 6
("Observability") work that has not started.

### How to start it
There is nothing to start today. Once Sprint 6 adds the `kibana` service block described above
(`How it's implemented in FDP`), it will start the same way every other infra container in this
repo does:
```
docker compose up -d kibana
```
This is planned/future — the command above will not work yet, because the service block does not
exist in `docker-compose.yml` as of now. It will also need correct `depends_on` startup ordering
relative to `elasticsearch`, since Kibana's only backing datastore is Elasticsearch.

### How to access it
Not reachable yet — nothing is running. Once stood up, Kibana's stock UI conventionally listens on
`http://localhost:5601`, but RULES.md/SPRINTS.md do not pin an explicit host port for FDP's
instance; the actual port will be whatever Sprint 6 assigns when the compose block is added.

### Endpoints it exposes
Not live in this repo yet — the item below is Kibana's own stock interface, for reference once
Sprint 6 stands it up, not something usable today.
| Endpoint | Purpose |
|---|---|
| `GET /` | Kibana web UI (Discover, dashboards) |
| `GET /api/status` | Liveness/readiness of the Kibana server itself |
| `GET /api/saved_objects/_find` | Query saved dashboards/searches (machine-readable) |

Kibana exposes no business/domain endpoints of its own — like Logstash and Elasticsearch, it's
infrastructure, queried by developers/operators, not by any FDP service.

### Installation & dependencies
No service links against Kibana — no service talks to it; it only reads from Elasticsearch. No
service's `pom.xml` needs any Kibana-related dependency, today or once Sprint 6 lands: `grep -r
micrometer-tracing --include=pom.xml .` across the repo (checking for any observability-related
dependency creeping into a service) returns nothing, consistent with that.

### For newcomers
There is nothing to click on or run for Kibana yet — no container, no index pattern, no dashboard.
See `docs/RULES.md` §13 for the plan and `docs/SPRINTS.md` Sprint 6 for when it lands. Once it
exists, the destination is this: Kibana is where a human actually reads "one correlation ID, three
places" (RULES.md §13) — take the trace ID from a client-reported error's `traceId` field or from a
Zipkin trace, paste it into Kibana's search bar, and every log line across every service that
touched that request shows up, because Micrometer Tracing already stamped that same ID into each
line's MDC before Logstash shipped it here via Elasticsearch.

## Related
- `RULES.md §13` (observability — logging, correlation), `RULES.md §14` (API error contract —
  `traceId`)
- `SPRINTS.md` Sprint 6 (Observability)
- `./elasticsearch.md`, `./logstash.md`, `./zipkin.md`
