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

## Related
- `RULES.md §13` (observability — logging, correlation), `RULES.md §14` (API error contract —
  `traceId`)
- `SPRINTS.md` Sprint 6 (Observability)
- `./elasticsearch.md`, `./logstash.md`, `./zipkin.md`
