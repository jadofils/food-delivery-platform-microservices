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

**Status today:** The container is live (part of the Sprint 1 `docker-compose.yml` additions) —
but nothing publishes or consumes yet. `order-service`, `delivery-service`, and
`notification-service` are all still bare skeletons (`spring-boot-starter` +
`spring-boot-starter-test` only, per each module's own `pom.xml`), with no `spring-boot-starter-amqp`,
no exchange/queue declarations, and no listeners. No exchanges, queues, or DLQs are provisioned yet
— that's Sprint 5 work (`order-service` publishing, `delivery-service`/`notification-service`
consuming). This is planned — Sprint 5, not yet implemented.

### How to start it
From the repo root:
```
docker compose up -d rabbitmq
```
This alone (no `.env` file needed) starts a single RabbitMQ 4 container (management plugin
included) with the default credentials below. No exchanges or queues are declared on startup —
those get created by service code once it exists (Sprint 5).

### How to access it
- **AMQP (what services will actually connect to):** `localhost:5672` (override via
  `RABBITMQ_PORT` in a repo-root `.env` file — see `.env.example`).
- **Management UI:** `http://localhost:15672` (override via `RABBITMQ_MANAGEMENT_PORT`), login
  `fdp` / `fdp` — the main way to look at this today: you can watch exchanges, queues, and
  connections there once something creates them, but nothing does yet.
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
| AMQP `5672` | Publish/consume protocol | Live, unused |
| `GET /api/overview` (management HTTP API, port `15672`) | Broker overview, stock RabbitMQ management plugin | Live |
| `http://localhost:15672` | Management UI (HTML) | Live |

These are stock RabbitMQ endpoints, not FDP-specific — no service exposes its own API through
RabbitMQ; it's a broker in between, not a service being called.

### Installation & dependencies
- Docker image: `rabbitmq:4-management-alpine` (pinned in `docker-compose.yml`).
- `order-service`, `delivery-service`, and `notification-service` will each declare
  `spring-boot-starter-amqp` in their own `pom.xml` once built (RULES.md §4) — not present in any
  of their POMs today.
- No local tool install is required to *run* RabbitMQ (it's fully containerized); the management
  UI is served by the container itself, no separate client needed.

### For newcomers
Run `docker compose up -d rabbitmq`, then open `http://localhost:15672` and log in with `fdp` /
`fdp` to confirm it's up — you'll see an empty broker, no exchanges or queues, because nothing has
declared any yet. This container being live and healthy is ahead-of-need infrastructure, not a
sign the order → delivery → notification event flow is running. See `./mongodb.md` for where
`notification-service` will persist what it consumes here, and `./resilience4j.md` for how
consumers will be made resilient to broker hiccups.

## Related
- `RULES.md §6` (communication rules), `RULES.md §1` factor 9 (disposability/idempotency),
  `RULES.md §13` (tracing across the RabbitMQ hop)
- `SPRINTS.md` Sprint 5 (Delivery, events, and notifications), Sprint 6 (trace continuity across
  the RabbitMQ hop)
- `./resilience4j.md`
