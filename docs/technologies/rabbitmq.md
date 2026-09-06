# RabbitMQ

## What it is
RabbitMQ is a message broker implementing AMQP. It routes messages published by producers to
queues consumed by one or more consumers, with support for exchange types (topic, direct, fanout),
per-queue dead-lettering, and at-least-once delivery guarantees.

## Why FDP uses it
- FDP's order-placement flow must not block on delivery assignment or notification dispatch —
  RabbitMQ decouples the caller (`order-service`) from downstream processing (RULES.md §6,
  "Asynchronous (RabbitMQ)").
- A service is never both the synchronous caller and the async publisher for the same fact in the
  same flow; RabbitMQ is the chosen mechanism for the "fire and let downstream react" half of that
  split (RULES.md §6).
- RabbitMQ only guarantees at-least-once delivery, not exactly-once, which is why every consumer in
  FDP must be idempotent (dedupe on event ID) — this is a direct consequence of choosing RabbitMQ,
  not an independent design choice (RULES.md §6, §1 factor 9).
- Dead-lettering is required on every consumer queue so a poisoned message doesn't block the queue
  for every subsequent message (RULES.md §6; SPRINTS.md Sprint 5 exit criteria).

## Where it's used

| Service | Role | Sprint |
|---|---|---|
| `order-service` | Publishes `OrderPlacedEvent`, `OrderCancelledEvent` to a topic exchange | Sprint 5 |
| `delivery-service` | Consumes `OrderPlacedEvent`, publishes `DeliveryStatusUpdatedEvent` | Sprint 5 |
| `notification-service` | Consumes domain events, persists notification/audit record | Sprint 5 |

RabbitMQ itself is stood up as infrastructure in `docker-compose.yml` from Sprint 0 (skeleton
containers only, RULES.md §10, SPRINTS.md Sprint 0); it is not wired into any service's messaging
logic until Sprint 5.

## How it's implemented in FDP
- Dependency: `spring-boot-starter-amqp` in each publishing/consuming service's `pom.xml`
  (`order-service`, `delivery-service`, `notification-service`) per RULES.md §4 — each service
  declares only what it uses.
- Topic exchange(s) with routing keys per event type; events are named
  `<Entity><PastTenseVerb>Event` (`OrderPlacedEvent`, `OrderCancelledEvent`,
  `DeliveryStatusUpdatedEvent`) per RULES.md §6. Event payload classes live in `common` (RULES.md
  §3).
- Every consumer queue is bound alongside a corresponding dead-letter queue; failed/poisoned
  messages land in the DLQ instead of blocking the queue (RULES.md §6; SPRINTS.md Sprint 5 exit
  criteria).
- Consumers dedupe on event ID to guarantee idempotency under at-least-once delivery (RULES.md §6,
  §1 factor 9) — e.g. `delivery-service` must not double-create a delivery assignment on redelivery.
- Connection details (host, port, credentials) are never hardcoded — sourced from `config-server`
  plus environment variables / Docker secrets, matching every other backing service (RULES.md §1
  factor 3, §4).
- Docker Compose service name: `rabbitmq`, container port `5672` (AMQP) per the target architecture
  in ReadMe.md; management UI port if enabled is not specified beyond that in RULES.md/SPRINTS.md.
  Full compose wiring (health checks, `depends_on: condition: service_healthy`) is finalized in
  Sprint 7 (RULES.md §10).
- Trace propagation: Micrometer Tracing propagates trace IDs across the RabbitMQ hop so a full
  order → delivery → notification flow is visible as one trace in Zipkin (RULES.md §13; SPRINTS.md
  Sprint 6).

## Getting started

**Status today:** Live and in real use — `order-service` (Sprint 3, pulled forward from Sprint 5's
publishing half) publishes real `OrderPlacedEvent`/`OrderCancelledEvent` messages to a durable
`fdp.order-events` topic exchange on every order placement/cancellation, confirmed by reading them
back via RabbitMQ's management API. `delivery-service`/`notification-service` (Sprint 5) don't
exist yet, so nothing *consumes* these for real — a temporary `order-events.inspection` queue
(bound to `order.*`) keeps published events visible in the meantime instead of letting them be
silently dropped by the topic exchange.

### How to start it
From the repo root:
```
docker compose up -d rabbitmq
```
This alone (no `.env` file needed) starts a single RabbitMQ 4 container (management plugin
included) with the default credentials below. The exchange/queue/binding are declared by
`order-service` itself on startup (`RabbitConfig`), not by RabbitMQ's own container config — start
`order-service` too (`./mvnw -pl order-service -am spring-boot:run`) to see them appear.

### How to access it
- **AMQP (what services actually connect to):** `localhost:5672` (override via `RABBITMQ_PORT` in
  a repo-root `.env` file — see `.env.example`).
- **Management UI:** `http://localhost:15672` (override via `RABBITMQ_MANAGEMENT_PORT`), login
  `fdp` / `fdp` — once `order-service` has run at least once, **Queues → order-events.inspection →
  Get messages** shows real published event payloads.
- **Default credentials (local dev only):** user `fdp`, password `fdp` — same values
  `docker-compose.yml` falls back to if no `.env` is present. Never used for anything but local
  development; production credentials come from environment injection (RULES.md §1 factor 3, §8).
- **From inside the Docker network** (i.e. from another container), a service's own
  `application-docker.yml` will point at the Docker service hostname, not `localhost`:
  `rabbitmq:5672` (RULES.md §10).
- **Health:** `docker compose ps rabbitmq` shows `healthy` once `rabbitmq-diagnostics -q ping`
  succeeds.

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| AMQP `5672` | Publish/consume protocol | Live, in real use by `order-service` |
| `GET /api/overview` (management HTTP API, port `15672`) | Broker overview, stock RabbitMQ management plugin | Live |
| `GET /api/queues/%2f/order-events.inspection` | This queue's current depth/stats | Live, verified |
| `http://localhost:15672` | Management UI (HTML) | Live |

These are stock RabbitMQ endpoints, not FDP-specific — no service exposes its own API through
RabbitMQ; it's a broker in between, not a service being called.

### Installation & dependencies
- Docker image: `rabbitmq:4-management-alpine` (pinned in `docker-compose.yml`).
- `order-service/pom.xml` declares `spring-boot-starter-amqp` — `delivery-service`/
  `notification-service` will do the same once built (Sprint 5), not present in their POMs today.
- One real gotcha worth flagging: Spring AMQP 4.1 ships **two** JSON message converters —
  `JacksonJsonMessageConverter` (uses `tools.jackson`, Jackson 3 — what Boot 4's own `ObjectMapper`
  actually is) and the legacy `Jackson2JsonMessageConverter` (`com.fasterxml.jackson`, Jackson 2).
  Using the wrong one is the same silent-mismatch trap `common`'s `MaskedFieldSerializer` already
  had to avoid — `order-service`'s `RabbitConfig` uses the former deliberately.
- No local tool install is required to *run* RabbitMQ (it's fully containerized); the management
  UI is served by the container itself, no separate client needed.

### For newcomers
Run `docker compose up -d rabbitmq`, start `order-service`, then place an order (see
`docs/services/order-service.md` or the checked-in Postman collection). Open
`http://localhost:15672`, log in with `fdp`/`fdp`, go to **Queues → order-events.inspection → Get
messages** — a real `OrderPlacedEvent` JSON payload is sitting there, `__TypeId__` header and all.
That queue is temporary scaffolding, not the final architecture: `delivery-service`/
`notification-service` (Sprint 5) will each declare their own real queue with its own DLQ, matching
RULES.md §6's "every consumer queue has a dead-letter queue" — this one exists purely so publishing
is visible before any real consumer does. See `./resilience4j.md` for how the *synchronous* half
of `order-service`'s calls degrades gracefully; this queue is the *asynchronous* half's proof of
life.

## Related
- `RULES.md §6` (communication rules), `RULES.md §1` factor 9 (disposability/idempotency),
  `RULES.md §13` (tracing across the RabbitMQ hop)
- `SPRINTS.md` Sprint 5 (Delivery, events, and notifications), Sprint 6 (trace continuity across
  the RabbitMQ hop)
- `./resilience4j.md`
