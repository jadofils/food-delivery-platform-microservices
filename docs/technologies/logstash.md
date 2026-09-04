# Logstash

## What it is
Logstash is a log/event processing pipeline: it ingests data from a source, optionally
transforms it, and forwards it to a destination. In FDP it tails each service container's stdout
and ships the structured JSON to Elasticsearch.

## Why FDP uses it
- RULES.md §1 factor 11 explicitly assigns this job to the execution environment, not the
  application: "Shipping stdout to Elasticsearch is the execution environment's job
  (Logstash/Filebeat), not the application's." No service is allowed to ship its own logs.
- RULES.md §13 is explicit that the alternative is disallowed: "No service configures a direct
  network log appender — that would couple application code to Logstash's location and violate
  factor 11." Logstash pulls from container stdout; nothing pushes to it from application code.
- This keeps every service's logging code identical regardless of where logs end up — a service
  only has to emit well-formed JSON to stdout, matching factor 11's "dev/prod parity for logs."

## Where it's used
Logstash is infrastructure. It consumes stdout from every service's container; no service
integrates with it directly.

| Component | Role | Sprint |
|---|---|---|
| All eight services (RULES.md §2) | Emit structured JSON to stdout, tailed by Logstash | Sprint 6 |
| Logstash itself | Added to `docker-compose.yml` alongside Elasticsearch and Kibana | Sprint 6 |

## How it's implemented in FDP
- No Spring Boot dependency in any service — by design, a direct log appender pointed at Logstash
  is explicitly disallowed (RULES.md §13).
- Docker Compose service name: `logstash`. RULES.md §10 requires it defined in
  `docker-compose.yml` with a mandatory health check and correct `depends_on` startup ordering
  relative to Elasticsearch. RULES.md/SPRINTS.md do not pin explicit host ports for it.
- Input side: reads each service container's stdout (the JSON structured logs required by RULES.md
  §1 factor 11). Output side: forwards to the `elasticsearch` Docker Compose service.
- Each service's only responsibility toward Logstash is emitting well-formed structured JSON to
  stdout — how that output is shipped is entirely Logstash's/the environment's concern (RULES.md
  §1 factor 11).

## Related
- `RULES.md §1` factor 11 (logs), `RULES.md §13` (observability — logging, direct-appender
  prohibition)
- `SPRINTS.md` Sprint 6 (Observability)
- `./elasticsearch.md`, `./kibana.md`
