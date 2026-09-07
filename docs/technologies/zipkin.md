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

| Service/module | Role | Sprint | Status |
|---|---|---|---|
| `customer-service`, `restaurant-service`, `order-service`, `delivery-service`, `notification-service` | Emit spans via Micrometer Tracing (Brave), report to Zipkin | Sprint 6 (pulled forward) | Done, verified live |
| `api-gateway` | Same, once built | Sprint 6 | Not built yet |

Sprint 6 exit criteria (SPRINTS.md): "a single order can be traced end-to-end in Zipkin across all
five services it touches." **Met** — a real order placement produces one trace across all five
domain services that exist today (`order-service`, `customer-service`, `restaurant-service`,
`delivery-service`, `notification-service`), via both the Feign hops and the RabbitMQ hop.
`api-gateway` isn't built yet, so it can't participate.

## How it's implemented in FDP
- Dependency: `org.springframework.boot:spring-boot-starter-zipkin` on every service (**not**
  `micrometer-tracing-bridge-brave`/`zipkin-reporter-brave` declared directly — see the gotcha
  below), plus `io.github.openfeign:feign-micrometer` on any service with Feign clients
  (`order-service`), per RULES.md §13.
- Config keys: `management.tracing.sampling.probability` and
  `management.zipkin.tracing.endpoint` on every service; `spring.rabbitmq.template.observation-enabled`
  (publish side — `order-service`, and `delivery-service` since it both publishes and consumes) and
  `spring.rabbitmq.listener.simple.observation-enabled` (consume side — `notification-service`, and
  `delivery-service`) for the RabbitMQ hops specifically. Hardcoded to `localhost` per-service
  today, matching every other service's not-yet-wired `config-server` scope cut — not yet sourced
  through `config-server` (RULES.md §1 factor 3 is a documented gap here, same as elsewhere).
- No manual span creation is required for the standard flow: Micrometer Tracing auto-instruments
  Spring MVC request handling, OpenFeign calls (once `feign-micrometer` is present), and RabbitMQ
  publish/listen hops (once `observation-enabled` is set) — RULES.md §13. Two non-obvious
  dependency/config requirements were needed to actually get there — see `Getting started` below.
- Micrometer Tracing also populates the trace/span ID into MDC automatically (keys `traceId`/
  `spanId`) — `common`'s `AbstractGlobalExceptionHandler.traceId()` already read `MDC.get("traceId")`
  ahead of this being wired up; it now returns a real value instead of always `null`, confirmed live.
- Docker Compose service name: `zipkin`. RULES.md §10 requires it defined in `docker-compose.yml`
  with a health check — done; port `9411` (Zipkin's own convention), overridable via `ZIPKIN_PORT`.

## Getting started

**Status today:** Live and in real use — the tracing half of Sprint 6, pulled forward the same way
order-service's async publish was pulled forward into Sprint 3, and extended to `delivery-service`
the same day it was built. All five domain services
(`customer-service`, `restaurant-service`, `order-service`, `delivery-service`,
`notification-service`) report real spans. Verified live: placing a real order produces **one
trace spanning every hop** — `order-service`'s own HTTP handling, its Feign calls into
`customer-service` and `restaurant-service`, the RabbitMQ publish, `delivery-service`'s consumption
of that message (auto-creating a delivery assignment), and `notification-service`'s consumption of
the same message — confirmed via `GET /api/v2/trace/{traceId}` showing a single `traceId` shared
across every span, including `restaurant-service`'s own Redis cache lookup:
```
order-service -> customer-service
order-service -> restaurant-service -> redis
order-service -> rabbitmq -> delivery-service
order-service -> rabbitmq -> notification-service
```
Also confirmed: a client-facing error response's `traceId` field (previously always `null` — a
real, user-reported gap) is now a real, directly-lookup-able Zipkin trace ID.

Two real gotchas surfaced getting this working, both confirmed by empirical testing against a live
Zipkin instance rather than assumed from documentation:
- **`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` alone report nothing.** Boot 4.1
  split tracing autoconfiguration into its own starter, `spring-boot-starter-zipkin` — it's the one
  that actually pulls in the autoconfiguration modules (`spring-boot-micrometer-tracing-brave`,
  `spring-boot-zipkin`) that wire the `Tracer`/reporter beans. Declaring the bridge/reporter
  libraries directly gets them onto the classpath but wires nothing — confirmed by placing a real
  order and finding zero services registered in Zipkin (`GET /api/v2/services` returned `[]`)
  until the starter was used instead.
- **A hand-constructed `RabbitTemplate` bean bypasses `spring.rabbitmq.template.observation-enabled`
  entirely.** `order-service`'s `RabbitConfig` builds its own `RabbitTemplate` (to set a custom
  `MessageConverter`), which meant Boot's own `RabbitTemplateConfigurer` — the thing that actually
  reads `spring.rabbitmq.template.*` properties, including `observation-enabled` — never ran
  against it. The publish call carried no trace-propagation headers, and RabbitMQ never appeared as
  a hop in the trace at all. Fixed by injecting `RabbitTemplateConfigurer` (auto-configured by
  `spring-boot-amqp`) and calling `configure(template, connectionFactory)` before setting the
  custom converter — the idiomatic fix for "I need a custom `RabbitTemplate` but still want Boot's
  own property-driven configuration."

### How to start it
From the repo root:
```
docker compose up -d zipkin
```
This alone (no `.env` file needed) starts a single Zipkin container using its default in-memory
storage — fine for local dev, since traces are meant to be ephemeral here, not a permanent audit
trail (that's `notification-service`'s job, over MongoDB, for actual domain events).

### How to access it
- **UI/API:** `http://localhost:9411` (override via `ZIPKIN_PORT` in a repo-root `.env` file — see
  `.env.example`). Search for a service, or open a specific trace ID directly at
  `http://localhost:9411/zipkin/traces/{traceId}`.
- **Health:** `docker compose ps zipkin` shows `healthy` once `GET /health` reports `"status":"UP"`.

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| `GET /` | Zipkin dashboard (HTML) | Live |
| `GET /api/v2/services` | List every service that has reported a span | Live, verified — returns all five domain services |
| `GET /api/v2/traces?serviceName=...` | Query traces matching search criteria | Live, verified |
| `GET /api/v2/trace/{traceId}` | Fetch a single trace by ID | Live, verified — confirmed one trace spans every hop of a real order placement, across all five domain services plus RabbitMQ plus Redis |
| `GET /api/v2/dependencies` | Aggregate service-to-service call graph derived from recent traces | Live, verified — matches the real architecture exactly |
| `GET /health` | Liveness/readiness | Live |
| `POST /api/v2/spans` | Span ingestion (called by reporting services, not by hand) | Live |

### Installation & dependencies
- Docker image: `openzipkin/zipkin:3` (pinned in `docker-compose.yml`).
- Every domain service (`customer-service`, `restaurant-service`, `order-service`,
  `delivery-service`, `notification-service`) declares
  `org.springframework.boot:spring-boot-starter-zipkin` — **not**
  `micrometer-tracing-bridge-brave`/`zipkin-reporter-brave` directly (see the gotcha above). Version
  managed by Boot's own parent BOM (RULES.md §4).
- `order-service` additionally declares `io.github.openfeign:feign-micrometer` — without it, an
  outbound Feign call carries no trace-propagation headers at all, and the downstream service starts
  a disconnected trace of its own instead of continuing the caller's (confirmed empirically the same
  way as the RabbitMQ gotcha above). Version managed by `spring-cloud-dependencies` (RULES.md §4).
- Config: `management.tracing.sampling.probability=1.0` (trace everything — a deliberate
  local-dev-only choice, RULES.md §1 factor 3 would want a sampled fraction in production) and
  `management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans` on every service;
  `order-service` and `delivery-service` set `spring.rabbitmq.template.observation-enabled=true`
  (publish side) and `notification-service`/`delivery-service` set
  `spring.rabbitmq.listener.simple.observation-enabled=true` (consume side; `delivery-service` does
  both, since it's a consumer and a publisher) — required for each RabbitMQ hop to join the trace
  rather than starting a new one, verified by inspecting `RabbitProperties.Template`/
  `RabbitProperties.BaseContainer` directly (`javap`) before writing the property names down.

### For newcomers
Run `docker compose up -d zipkin`, start all five domain services, place a real order (see
`docs/services/order-service.md` or any checked-in Postman collection), then open
`http://localhost:9411`, search for `order-service`, and open the most recent trace for
`http post /api/orders/me` — one trace, five services plus RabbitMQ plus Redis, latency broken down
per hop. `GET /api/v2/dependencies` gives the same story as a call graph instead of a timeline. See
`./resilience4j.md` and `./rabbitmq.md` for the sync/async mechanics this trace is actually showing
you the shape of.

## Related
- `RULES.md §13` (observability — tracing), `RULES.md §14` (API error contract — `traceId`),
  `RULES.md §10` (containerization)
- `SPRINTS.md` Sprint 6 (Observability) — tracing half done, ELK/Prometheus/Grafana still open
- `./elasticsearch.md`, `./kibana.md`, `./resilience4j.md`, `./rabbitmq.md`
