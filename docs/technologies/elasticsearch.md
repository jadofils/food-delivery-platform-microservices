# Elasticsearch

## What it is
Elasticsearch is a distributed search and analytics engine. In FDP it is used as the storage and
index layer for centrally aggregated application logs, not as an application datastore.

## Why FDP uses it
- RULES.md §1 factor 11: every service writes structured JSON to stdout only and never opens a
  log file or pushes logs over the network itself. Shipping/storing that output is the execution
  environment's job — Elasticsearch is where shipped logs land and get indexed.
- RULES.md §13: Logstash tails container stdout and ships to Elasticsearch; Kibana queries against
  it as the dashboard layer.
- RULES.md §5 draws an explicit line here: operational/application logs (what the ELK stack is
  for) are ephemeral, aggregated centrally from stdout, and their retention is managed by
  Elasticsearch ILM, not application code — as opposed to notification/audit records, which are
  permanent business data owned by `notification-service` in MongoDB, not log output.

## Where it's used
Elasticsearch is infrastructure, not called directly by any service — it receives data indirectly,
shipped by Logstash from every service's container stdout.

| Component | Role | Sprint |
|---|---|---|
| All eight services (RULES.md §2) | Produce stdout JSON logs that end up indexed here (indirectly, via Logstash) | Sprint 6 |
| Elasticsearch itself | Added to `docker-compose.yml` alongside Logstash and Kibana | Sprint 6 |

## How it's implemented in FDP
- No service depends on an Elasticsearch client library and no service opens a connection to it —
  doing so directly would bypass the stdout-only logging rule (RULES.md §1 factor 11, §13).
- Docker Compose service name: `elasticsearch`. RULES.md §10 requires it defined in
  `docker-compose.yml` with a mandatory health check; RULES.md/SPRINTS.md do not pin an explicit
  host port — the Elasticsearch image's conventional REST API port is `9200`.
- Log retention/lifecycle is governed by Elasticsearch ILM policy (RULES.md §5), configured at the
  infrastructure level, not in any service's code or config.

## Getting started

**Status today:** Not yet stood up. Elasticsearch has no service block in the repo-root
`docker-compose.yml` (which today only defines `postgres`, `mongodb`, `rabbitmq`, `redis`, and
`keycloak` — Sprint 0/1 scope) and no service's `pom.xml` depends on it. This is entirely Sprint 6
("Observability") work that has not started.

### How to start it
There is nothing to start today. Once Sprint 6 adds the `elasticsearch` service block described
above (`How it's implemented in FDP`), it will start the same way every other infra container in
this repo does:
```
docker compose up -d elasticsearch
```
This is planned/future — the command above will not work yet, because the service block does not
exist in `docker-compose.yml` as of now.

### How to access it
Not reachable yet — nothing is running. Once stood up, Elasticsearch's stock HTTP API
conventionally listens on `http://localhost:9200`, but RULES.md/SPRINTS.md do not pin an explicit
host port for FDP's instance; the actual port will be whatever Sprint 6 assigns when the compose
block is added.

### Endpoints it exposes
Not live in this repo yet — the list below is Elasticsearch's own stock REST API, for reference
once Sprint 6 stands it up, not something callable today.
| Endpoint | Purpose |
|---|---|
| `GET /_cluster/health` | Cluster health/liveness |
| `GET /_cat/indices` | List indices (e.g. per-day log indices Logstash creates) |
| `GET /<index>/_search` | Query documents in an index |
| `PUT /<index>` | Create/configure an index |

### Installation & dependencies
No service links against Elasticsearch — it is purely infra that receives shipped logs from
Logstash, not something an application depends on (RULES.md §1 factor 11, §13). No service's
`pom.xml` needs any Elasticsearch client library, today or once Sprint 6 lands: `grep -r
micrometer-tracing --include=pom.xml .` across the repo (checking for any observability-related
dependency creeping into a service) returns nothing, consistent with that.

### For newcomers
There is nothing to click on or run for Elasticsearch yet — no container, no index, no service
depending on it. See `docs/RULES.md` §13 for the plan and `docs/SPRINTS.md` Sprint 6 for when it
lands. Once it exists, the destination is this: every service's stdout JSON log line will carry a
trace/span ID (populated by Micrometer Tracing), Logstash ships that line here, and Kibana queries
it — meaning the same correlation ID that shows up in Zipkin also shows up in every Kibana log line
for that request and in the `traceId` field of any error response (RULES.md §13), with
Elasticsearch as the storage/index layer making that search possible.

## Related
- `RULES.md §1` factor 11 (logs), `RULES.md §5` (data ownership — operational vs. audit logs),
  `RULES.md §13` (observability — logging)
- `SPRINTS.md` Sprint 6 (Observability)
- `./logstash.md`, `./kibana.md`
