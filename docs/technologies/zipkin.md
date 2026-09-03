# Zipkin

## What it is
Zipkin is a distributed tracing system. It collects spans emitted by instrumented applications,
stitches them into traces by shared trace/span IDs, and exposes a UI/API for finding a specific
request and seeing the latency breakdown across every hop it took.

## Why FDP uses it
- FDP is decomposed into nine independently deployable services (RULES.md §2); a single order
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
| All nine services (RULES.md §2) | Emit spans via Micrometer Tracing (Brave), report to Zipkin | Sprint 6 |

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

## Related
- `RULES.md §13` (observability — tracing), `RULES.md §14` (API error contract — `traceId`),
  `RULES.md §10` (containerization)
- `SPRINTS.md` Sprint 6 (Observability)
- `./elasticsearch.md`, `./kibana.md`
