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
| All nine services (RULES.md §2) | Produce stdout JSON logs that end up indexed here (indirectly, via Logstash) | Sprint 6 |
| Elasticsearch itself | Added to `docker-compose.yml` alongside Logstash and Kibana | Sprint 6 |

## How it's implemented in FDP
- No service depends on an Elasticsearch client library and no service opens a connection to it —
  doing so directly would bypass the stdout-only logging rule (RULES.md §1 factor 11, §13).
- Docker Compose service name: `elasticsearch`. RULES.md §10 requires it defined in
  `docker-compose.yml` with a mandatory health check; RULES.md/SPRINTS.md do not pin an explicit
  host port — the Elasticsearch image's conventional REST API port is `9200`.
- Log retention/lifecycle is governed by Elasticsearch ILM policy (RULES.md §5), configured at the
  infrastructure level, not in any service's code or config.

## Related
- `RULES.md §1` factor 11 (logs), `RULES.md §5` (data ownership — operational vs. audit logs),
  `RULES.md §13` (observability — logging)
- `SPRINTS.md` Sprint 6 (Observability)
- `./logstash.md`, `./kibana.md`
