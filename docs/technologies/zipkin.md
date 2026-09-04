# Zipkin

## What it is
Zipkin is a distributed tracing system. It collects spans emitted by instrumented applications,
stitches them into traces by shared trace/span IDs, and exposes a UI/API for finding a specific
request and seeing the latency breakdown across every hop it took.

## Why FDP uses it
- FDP is decomposed into eight independently deployable services (RULES.md §2); a single order
  flow crosses several of them (order → restaurant/customer validation → delivery → notification).
  Without a distributed tracer there is no way to see that as one request rather than isolated,
  unrelated log lines per service.
- RULES.md §13: Micrometer Tracing (Brave) exports spans to Zipkin from every service, and trace
  IDs propagate across both REST (Feign) and RabbitMQ hops, so the full order flow is visible as
  one trace.
- RULES.md §13/§14: Zipkin is one of the "three places" the same correlation ID appears — Zipkin,
  every Kibana log line for that request, and the `traceId` field of any error response — so a
  client-reported failure can be looked up directly in Zipkin without asking when it happened.

## Where it's used

| Service/module | Role | Sprint |
|---|---|---|
| All eight services (RULES.md §2) | Emit spans via Micrometer Tracing (Brave), report to Zipkin | Sprint 6 |

Sprint 6 exit criteria (SPRINTS.md): "a single order can be traced end-to-end in Zipkin across all
five services it touches."

## How it's implemented in FDP
- Dependencies: `micrometer-tracing-bridge-brave` (bridges Micrometer's tracing API to Brave) plus
  a Zipkin reporter (`io.zipkin.reporter2:zipkin-reporter-brave`), added to every service per
  RULES.md §13.
- Config keys: `management.tracing.sampling.probability` and
  `management.zipkin.tracing.endpoint`, set per `application-{profile}.yml` and sourced through
  `config-server` — never hardcoded, per RULES.md §1 factor 3.
- No manual span creation is required for the standard flow: Micrometer Tracing auto-instruments
  Spring MVC/WebFlux request handling, OpenFeign calls, and RabbitMQ listener/publisher hops
  (RULES.md §13).
- Micrometer Tracing also populates the trace/span ID into MDC automatically, so every service's
  stdout JSON log line (RULES.md §11) is correlated to its Zipkin trace with no manual wiring per
  service (RULES.md §13).
- Docker Compose service name: `zipkin`. RULES.md §10 requires it defined in `docker-compose.yml`
  with a health check; RULES.md/SPRINTS.md do not pin an explicit host port for it — the Zipkin
  image's conventional port is `9411`.

## Getting started

**Status today:** Not yet stood up. Zipkin has no service block in the repo-root
`docker-compose.yml` (which today only defines `postgres`, `mongodb`, `rabbitmq`, `redis`, and
`keycloak` — Sprint 0/1 scope) and no service's `pom.xml` carries a tracing dependency. This is
entirely Sprint 6 ("Observability") work that has not started.

### How to start it
There is nothing to start today. Once Sprint 6 adds the `zipkin` service block described above
(`How it's implemented in FDP`), it will start the same way every other infra container in this
repo does:
```
docker compose up -d zipkin
```
This is planned/future — the command above will not work yet, because the service block does not
exist in `docker-compose.yml` as of now.

### How to access it
Not reachable yet — nothing is running. Once stood up, Zipkin's stock UI conventionally listens on
`http://localhost:9411`, but RULES.md/SPRINTS.md do not pin an explicit host port for FDP's
instance; the actual port will be whatever Sprint 6 assigns when the compose block is added.

### Endpoints it exposes
Not live in this repo yet — the list below is Zipkin's own stock API, for reference once Sprint 6
stands it up, not something callable today.
| Endpoint | Purpose |
|---|---|
| `GET /` | Zipkin dashboard (HTML) |
| `GET /api/v2/traces` | Query traces matching search criteria |
| `GET /api/v2/trace/{traceId}` | Fetch a single trace by ID |
| `GET /health` | Liveness/readiness |
| `POST /api/v2/spans` | Span ingestion (called by reporting services, not by hand) |

### Installation & dependencies
Once wired up, every one of the eight services will need `micrometer-tracing-bridge-brave` plus a
Zipkin reporter (`io.zipkin.reporter2:zipkin-reporter-brave`), per RULES.md §13 — version managed
by the root aggregator's BOM, never pinned per-service (RULES.md §4). None of this is in any
`pom.xml` today: `grep -r micrometer-tracing --include=pom.xml .` across the repo returns nothing.

### For newcomers
There is nothing to click on or run for Zipkin yet — no container, no UI, no dependency in any
service. See `docs/RULES.md` §13 for the plan and `docs/SPRINTS.md` Sprint 6 for when it lands.
Once it exists, the destination is this: a single order request gets one trace ID, and that same
ID shows up in three places — the Zipkin trace itself, every Kibana log line for that request, and
the `traceId` field of any error response the client sees (RULES.md §13) — so a reported failure
can be looked up in Zipkin directly from the trace ID in the error, with no need to ask when it
happened.

## Related
- `RULES.md §13` (observability — tracing), `RULES.md §14` (API error contract — `traceId`),
  `RULES.md §10` (containerization)
- `SPRINTS.md` Sprint 6 (Observability)
- `./elasticsearch.md`, `./kibana.md`
