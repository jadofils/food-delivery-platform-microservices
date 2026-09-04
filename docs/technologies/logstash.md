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

## Getting started

**Status today:** Not yet stood up. Logstash has no service block in the repo-root
`docker-compose.yml` (which today only defines `postgres`, `mongodb`, `rabbitmq`, `redis`, and
`keycloak` — Sprint 0/1 scope) and no service's `pom.xml` depends on it. This is entirely Sprint 6
("Observability") work that has not started.

### How to start it
There is nothing to start today. Once Sprint 6 adds the `logstash` service block described above
(`How it's implemented in FDP`), it will start the same way every other infra container in this
repo does:
```
docker compose up -d logstash
```
This is planned/future — the command above will not work yet, because the service block does not
exist in `docker-compose.yml` as of now. It will also need `depends_on: condition: service_healthy`
against `elasticsearch` once both exist, since Logstash's output side forwards there.

### How to access it
Logstash has no user-facing UI — it's a pipeline, not something a person opens in a browser, today
or once Sprint 6 lands. It will read each service container's stdout on its input side and forward
to the `elasticsearch` Docker Compose service on its output side; the only thing worth "accessing"
once it exists is confirming its container is healthy (`docker compose ps logstash`) and that its
target indices are appearing in Elasticsearch.

### Endpoints it exposes
Not live in this repo yet, and Logstash exposes no business/domain HTTP API even once running — it
optionally exposes a monitoring API (typically port `9600`) for its own pipeline stats, but no
service or person is expected to call it directly; input comes from container stdout, output goes
to Elasticsearch.

### Installation & dependencies
No service links against Logstash — a direct log appender pointed at Logstash from application
code is explicitly disallowed (RULES.md §13); Logstash only tails container stdout. No service's
`pom.xml` needs any Logstash-related dependency, today or once Sprint 6 lands: `grep -r
micrometer-tracing --include=pom.xml .` across the repo (checking for any observability-related
dependency creeping into a service) returns nothing, consistent with that.

### For newcomers
There is nothing to click on or run for Logstash yet — no container, no pipeline config, no service
depending on it. See `docs/RULES.md` §13 for the plan and `docs/SPRINTS.md` Sprint 6 for when it
lands. Once it exists, the destination is this: Logstash is the quiet middle step that makes "one
correlation ID, three places" (RULES.md §13) possible — it tails each service's stdout JSON (which
already carries the trace/span ID once Micrometer Tracing is wired up) and ships it into
Elasticsearch, so that same ID that appears in a Zipkin trace and in an error response's `traceId`
field can also be found by filtering Kibana's log view.

## Related
- `RULES.md §1` factor 11 (logs), `RULES.md §13` (observability — logging, direct-appender
  prohibition)
- `SPRINTS.md` Sprint 6 (Observability)
- `./elasticsearch.md`, `./kibana.md`
