# FDP Sprint Plan

Delivery plan for the Food Delivery Platform microservice build-out. Governed by
[`RULES.md`](./RULES.md) — this document sequences the work; it does not re-explain the tech
decisions or the rules behind them. Sprints are sized at two weeks each as a default cadence;
adjust to actual team velocity.

Each sprint's Definition of Done is the same, per [`RULES.md` §11](./RULES.md#11-cicd--auto-merge):
every change lands on `main` via a `feature/*` branch whose PR auto-merged after Testcontainers
integration tests passed. A sprint isn't done because code was written — it's done because it's
merged.

---

## Sprint 0 — Foundations & repo governance

**Goal:** a repo that can receive service code under CI control, before any service exists.

- Root aggregator `pom.xml`: `dependencyManagement` (Spring Boot BOM, Spring Cloud BOM), shared
  properties, module list.
- Empty Spring Boot module skeletons for all eight services: `pom.xml` (`RULES.md` §4 — no
  business dependencies yet), one empty `*Application` class, a
  `src/main/resources/application.properties` declaring just `spring.application.name`, and a
  `src/test/java/**/*ApplicationTests.java` with an empty `contextLoads()` test — the same four
  pieces every module gets, so no service starts out structurally different from another.
  `common` is the one exception: no `Application` class, no `resources`, no `test` yet, since it
  has no bootable context and no code to test until §3's shared-vs-local content actually lands
  in it.
- `docs/RULES.md` and `docs/SPRINTS.md` (this document) merged.
- Base GitHub Actions workflow template (build + unit test on PR) and branch protection rules on
  `main` (required checks, auto-merge enabled).
- `docker-compose.yml` skeleton: Postgres, MongoDB, RabbitMQ, Redis containers only — no services
  yet.
- `common` module scaffolded with the shared cross-cutting baseline every service will build on
  (`RULES.md` §14–§16): the `DomainException` hierarchy, `ApiErrorResponse` DTO,
  `AbstractGlobalExceptionHandler` (the shared `@ExceptionHandler` methods a service's own
  `@RestControllerAdvice` extends), and a `spring-boot-starter-validation` baseline dependency —
  so every service gets consistent global error handling and DTO validation from its first
  controller, instead of each one improvising its own later.

**Exit criteria:** `main` is protected, `docker-compose up` starts the four infra containers, a
trivial PR against any module demonstrates the CI gate + auto-merge working end to end, and the
`common` exception/error-envelope baseline compiles and is ready for a service to depend on.

---

## Sprint 1 — Identity, discovery, config

**Goal:** the platform's spine exists before any domain service needs to register with it.

**Superseded scope note:** this sprint originally built a custom `identity-service` — its own
`User`/`Role`/`Permission` schema, registration/login endpoints, and a hand-rolled nested-JWT
codec. That was retired in favor of Keycloak (`docs/decisions/0001-retire-identity-service-for-keycloak.md`);
the bullets below describe what actually ships now, not the original plan.

- `discovery-server` (Eureka) stood up, dashboard reachable at `:8761`. **Done and verified
  live:** `@EnableEurekaServer`, standalone mode (`register-with-eureka=false`,
  `fetch-registry=false` — it's the registry, not a client of itself), dashboard and
  `/actuator/health` both confirmed responding, correctly showing "No instances available" until
  a real client exists (`customer-service`, Sprint 2). Required pulling `spring-cloud-dependencies`
  into the root aggregator's `dependencyManagement` for the first time (RULES.md §4) — see the
  version note directly in `pom.xml`: no Spring Cloud release is binary-compatible with Boot
  4.1.1 yet (even the newest milestone references a Boot package path that moved in 4.1), so this
  runs on a Spring Cloud snapshot as a deliberate, documented, temporary compromise.
- `config-server` stood up serving externalized config to registered clients. **Done and verified
  live:** `@EnableConfigServer`, native (filesystem-backed) profile reading from a `config-repo/`
  bundled into the service's own jar rather than a separate git repo (RULES.md §4's "don't
  over-engineer ahead of need" — see the comment in `config-server/src/main/resources/application.properties`),
  serving on `:8888`. A shared `application.yml` (Eureka `defaultZone` pointed at `localhost` for
  local dev) plus an `application-docker.yml` override (same key, pointed at the `discovery-server`
  container hostname) confirmed against the real REST API: `GET /application/default` returns only
  the local-profile value, `GET /application/docker` returns the docker-profile value correctly
  layered over (and overriding) the default. Required tracking down Spring Cloud Config Server's
  actual `@EnableConfigServer` package on the resolved snapshot jar via `jar tf` — it lives at
  `org.springframework.cloud.config.server.EnableConfigServer`, not the `.config` subpackage its
  Boot-3-era location would suggest.
- **Keycloak** (RULES.md §8) stood up as the platform's identity provider:
  - `docker-compose.yml` service `keycloak`, its own `keycloak_db` schema in the shared Postgres
    (FDP's Flyway migrations never touch it).
  - `fdp` realm, `fdp-api` client, and ten client roles matching FDP's permission strings
    (`order:create`, `restaurant:menu:write`, `delivery:status:update`, …) — never bare role
    names, so downstream services authorize on capability, not identity — all provisioned
    automatically on first start via a realm-import file (`docker/keycloak/fdp-realm.json`).
  - One demo user per baseline role (`CUSTOMER`, `RESTAURANT_OWNER`, `DELIVERY_AGENT`, `ADMIN`),
    same file, so the system is usable immediately — see `credentials.md`.
  - Verified live: every demo user can obtain a token from Keycloak's own token endpoint with
    exactly the right client-role claims and nothing else; a wrong password is correctly rejected.
- **Enforcement moves to native Spring Security**, not a `common`-owned custom filter/interceptor:
  each service that validates tokens does so via `spring-boot-starter-oauth2-resource-server`
  against Keycloak's real JWKS endpoint, with `@PreAuthorize` (or an equivalent
  `SecurityFilterChain` rule) for permission checks. This lands in each service's own sprint
  (Sprint 2 for `customer-`/`restaurant-service`, Sprint 3 for `order-service`, Sprint 5 for
  `delivery-`/`notification-service`) — Sprint 1 only stands up Keycloak itself, it doesn't build
  a service to validate against yet, since there's no `identity-service` anymore to have been
  "first."

**Exit criteria:** `discovery-server` and `config-server` are both reachable (both done and
verified live above); Keycloak's `fdp`
realm imports successfully from a clean `docker compose up`; each of the four demo accounts in
`credentials.md` can obtain a token carrying exactly its role's permissions, confirmed against
Keycloak's token endpoint directly (no FDP service required yet).

---

## Sprint 2 — Customer & Restaurant services

**Goal:** first two domain services decomposed from the monolith, each independently deployable.

- `customer-service` (`customer_db`): customer profiles, delivery addresses. **Done and verified
  live:** `Customer`/`Address` JPA entities (`addresses` LAZY, `@EntityGraph` for the one query
  that needs them loaded — no unnecessary eager loading), Flyway migrations, self-service
  registration/profile/address endpoints (`/api/customers/me/**`, ownership resolved from the
  caller's own Keycloak token, never a client-supplied id) plus `user:read`-gated admin endpoints
  (`/api/customers/{id}`, `/api/customers`), `spring-boot-starter-oauth2-resource-server` wired
  against Keycloak's JWKS via a shared `common.security.jwt.KeycloakRoleConverter`, `@Masked`
  email/phone on every response, Swagger UI at `/swagger-ui/index.html`. Registers with Eureka on
  startup (verified against the real `discovery-server`); pulling shared config from
  `config-server` is a deliberate scope cut for this pass, not yet wired (see
  `docs/services/customer-service.md`). Exercised end to end against real Keycloak-issued tokens
  and a real `customer_db` — 11 Testcontainers-backed test classes plus a 23-request Postman
  collection (`postman/FDP-customer-service.postman_collection.json`), both green. Surfaced and
  fixed one real bug in the process: a malformed numeric path variable (e.g.
  `GET /api/customers/not-a-number`) was falling through to a bare 500 instead of a proper 400 —
  `common`'s `AbstractGlobalExceptionHandler` now handles `MethodArgumentTypeMismatchException`
  explicitly, for every service, not just this one.
- `restaurant-service` (`restaurant_db`): restaurants, menus, menu items. **Done and verified
  live:** `Restaurant`/`MenuItem` JPA entities (`menuItems` LAZY, `@EntityGraph` for the one query
  that needs them loaded), Flyway migrations, self-service registration/profile/menu-item endpoints
  (`/api/restaurants/me/**`, gated by `restaurant:menu:write`, ownership resolved from the token)
  plus public-browsing endpoints (`/api/restaurants/{id}`, `/api/restaurants`,
  `/api/restaurants/{id}/menu-items`, gated by the weaker `restaurant:menu:read` permission the
  seeded `CUSTOMER` demo account also holds — anyone who can browse gets these, not just
  owners/admin). Reuses the same `common.security.jwt.KeycloakRoleConverter` and
  `jwk-set-uri`-based `SecurityConfig` pattern as `customer-service`, Swagger UI at
  `/swagger-ui/index.html`. Registers with Eureka on startup (verified live). Same deliberate scope
  cut as `customer-service`: no `config-server` integration yet. Redis caching for menu lookups
  (RULES.md §12) was also a scope cut originally, but has since been pulled forward and wired up in
  a later pass — done and verified live, see `docs/services/restaurant-service.md`'s "Distributed
  caching" section. Exercised end to end against real tokens for all three
  relevant demo roles (`RESTAURANT_OWNER`, `CUSTOMER`, `DELIVERY_AGENT` — the last used specifically
  to prove it lacks `restaurant:menu:read` and is correctly rejected) — 13 Testcontainers-backed
  test classes plus a 21-request Postman collection
  (`postman/FDP-restaurant-service.postman_collection.json`), both green. One dependency gotcha
  repeated from `customer-service` and caught immediately by the same fix: `flyway-database-postgresql`
  is a separate module from `flyway-core` in Flyway 10+ — missing it produces "Unsupported
  Database: PostgreSQL" at startup, not a compile error, so it's easy to forget per-service.
- Both register with Eureka; pulling config from `config-server` and exposing an OpenAPI spec
  under `docs/api-contracts/` (as opposed to the live `/v3/api-docs` both already serve) remain
  open, not yet done.
- Testcontainers-backed CI pipeline for each — the tests exist and pass locally; a per-service
  GitHub Actions workflow (as opposed to Sprint 0's single reactor-wide one) is Sprint 7 work.

**Exit criteria met:** both services run independently, each against its own database, with no
shared tables and no direct database access from any other module.

---

## Sprint 3 — Order service & synchronous inter-service calls

**Goal:** the first cross-service read dependency, done the right way.

- `order-service` (`order_db`): order placement and lifecycle. **Done and verified live:**
  `Order`/`OrderItem` JPA entities (item name/price snapshotted at placement time, never re-read
  live — order history must stay stable even if the restaurant later changes its menu), Flyway
  migrations, self-service endpoints (`/api/orders/me/**`) matching Keycloak's own permission set
  exactly (`order:create`, `order:read`, `order:cancel`). See `docs/services/order-service.md`.
- OpenFeign clients to `customer-service` (validate customer/address) and `restaurant-service`
  (validate menu items and pricing), resolved via Eureka (`lb://...`) — **done and verified live**
  against real running instances, including a real permission-relayed token on every call
  (`TokenRelayRequestInterceptor`), never a separate service credential.
- Resilience4j circuit breaker + retry + bulkhead on both Feign clients, with typed fallback
  responses — done. "Timeout" is enforced via Feign's own connect/read timeout rather than
  Resilience4j's `@TimeLimiter`, a deliberate choice (see `docs/services/order-service.md` for
  why), so the quartet in RULES.md §7 is satisfied by three Resilience4j annotations plus one
  HTTP-client-level timeout, not four Resilience4j annotations.
- Also pulled forward from Sprint 5, since it's the natural pairing with this same service's sync
  calls: `order-service` publishes real `OrderPlacedEvent`/`OrderCancelledEvent` to a durable
  RabbitMQ topic exchange (`fdp.order-events`) — verified live via RabbitMQ's management API, not
  just unit-tested.
- Testcontainers CI pipeline: done (Postgres + RabbitMQ, 11 tests). **Not done:** genuine
  consumer-driven contract tests for the two Feign clients (RULES.md §9's stated aspiration) — the
  automated suite mocks both gateways at the method level instead, since standing up real
  Spring Cloud Contract infrastructure was out of scope for this pass. The *real* integration is
  proven a different way: live-verified against actually-running `customer-`/`restaurant-service`
  instances, including the failure path (see exit criteria below) — strong evidence, but not the
  same thing as an automated contract test that runs in CI.

**Exit criteria:** an order can be placed end-to-end through real service-to-service calls, and
placing an order still degrades gracefully (clear error, not a hang) if `restaurant-service` is
stopped. **Met and verified live**, twice: (1) a real order placed successfully through real
Eureka-resolved Feign calls to real `customer-service`/`restaurant-service` instances, with the
correct restaurant-supplied price snapshotted; (2) `restaurant-service` stopped mid-run, next order
placement returned a clean `503 SERVICE_UNAVAILABLE` in ~7 seconds (not a hang), and order
placement recovered automatically once `restaurant-service` came back — circuit breaker state
confirmed via `/actuator/circuitbreakers` throughout.

---

## Sprint 4 — API Gateway & security edge

**Goal:** a single, secured entry point for everything built so far.

- `api-gateway`: routes `/api/customers/**`, `/api/restaurants/**`, `/api/orders/**` via Eureka
  load-balanced URIs. **Done and verified live**, plus `/api/deliveries/**` (originally Sprint 5
  scope, blocked until now — see that sprint's own note) added in the same pass. Built on
  `spring-cloud-starter-gateway-server-webflux` (this Spring Cloud train's renamed artifact),
  routes configured via `application.properties` rather than a Java `RouteLocator`, matching this
  project's properties-over-YAML convention.
- JWT validation at the gateway (signature, expiry) via Spring Security's reactive OAuth2
  Resource Server, against Keycloak's real JWKS endpoint (RULES.md §8). **Done and verified live**:
  reuses `common`'s own `KeycloakRoleConverter` (its `Converter<Jwt, AbstractAuthenticationToken>`
  signature is stack-agnostic) wrapped in Spring Security's `ReactiveJwtAuthenticationConverterAdapter`
  for the WebFlux DSL; a `401` is reshaped into FDP's standard `ApiErrorResponse` envelope by a new
  `RestServerAuthenticationEntryPoint`. Explicit issuer validation was not added (matches every
  other service's own `jwk-set-uri`-only posture).
- Redis stood up; `RequestRateLimiter` on order placement backed by Redis. **Done and verified
  live**: 5 requests/sec sustained, burst 10, keyed by the caller's own JWT subject; 20 concurrent
  placement requests from one customer produced real `429` responses once the burst was exhausted.
- Downstream services add local JWT re-validation (defense-in-depth, per `RULES.md` §8) — already
  true of every domain service since its own Sprint (unchanged by this one).

**Exit criteria: met and verified live.** Unauthenticated/malformed-token requests get a clean
`401` at the edge before any route resolves; rate limiting is demonstrably real (burst exhaustion
produces `429`). "All traffic ... flows through the gateway only" is not separately enforced —
every domain service still also accepts direct calls on its own port; RULES.md never asked for
that to be blocked, only for the gateway to exist as the intended path. **Known gap:** a `429`
(and an unmatched-route `404`/unresolvable-instance `503`) is not yet reshaped into
`ApiErrorResponse` the way the edge's own `401` is — deferred rather than guessed at without
verifying the exact WebFlux error-handling API surface live; see
`docs/technologies/spring-cloud-gateway.md`. A real, live-discovered gotcha along the way: Eureka's
default hostname-based self-registration advertises a Docker Desktop/WSL2 host's unresolvable
`*.mshome.net` name to Reactor Netty's async DNS resolver (Gateway's routing filter), even though
every service's own blocking-resolution Feign calls to each other were unaffected —
`eureka.instance.prefer-ip-address=true`, now set on every FDP service, fixed it.

---

## Sprint 5 — Delivery, events, and notifications

**Goal:** replace the last synchronous, blocking flow with events, and stand up the
notification/audit trail.

- RabbitMQ topic exchange(s) with DLQs per consumer queue.
- `order-service` publishes `OrderPlacedEvent` / `OrderCancelledEvent` — **done early, in Sprint 3**
  (see that sprint's notes), since it paired naturally with `order-service`'s own build.
- `notification-service` (`notification_db`, MongoDB) consumes domain events and persists the
  notification/audit record (who was notified, channel, status) — this is domain data, not
  operational logging (`RULES.md` §5). **Done and verified live:** `NotificationRecord` Mongo
  document (`eventId` unique-indexed), `OrderEventListener` consuming `fdp.order-events` off its
  own real queue + DLQ (`notification-service.order-events` / `.dlq`, `x-dead-letter-exchange`
  wiring, no custom recovery code needed), `GET /api/notifications/me` (self-service,
  authenticated-only) and `GET /api/notifications` (`notification:read`, admin-only). Idempotency
  is genuinely enforced at the database level, not just an application-level check — see
  `docs/services/notification-service.md`'s "Idempotent consumption" section for the real bug this
  surfaced and how it was fixed. Exercised end to end against a real, running `order-service`: a
  real order placement and cancellation each produced exactly one notification record, visible via
  the REST API within about two seconds. 5 Testcontainers-backed tests (MongoDB + RabbitMQ) plus a
  14-request Postman collection (`postman/FDP-notification-service.postman_collection.json`), both
  green.
- `delivery-service` (`delivery_db`) consumes `OrderPlacedEvent`, auto-creates delivery
  assignments, publishes `DeliveryStatusUpdatedEvent`. Consumer is idempotent. **Done and verified
  live:** `DeliveryAssignment` Postgres entity (`order_id` unique-indexed — the real idempotency
  guard, same lesson `notification-service`'s own listener already taught this codebase),
  `OrderEventListener` consuming `fdp.order-events` off its own queue + DLQ
  (`delivery-service.order-events` / `.dlq`) to auto-create a `PENDING` assignment on
  `OrderPlacedEvent` and cancel it on `OrderCancelledEvent`; `GET /api/deliveries/{id}`,
  `GET /api/deliveries/me`, `GET /api/deliveries/unassigned` (all `delivery:read`), and
  `POST /api/deliveries/{id}/{claim,pickup,deliver}` (`delivery:status:update`, forward-only status
  transitions, ownership-checked). Publishes `DeliveryStatusUpdatedEvent` to `fdp.delivery-events`
  (an exchange this service owns) on every transition. Exercised end to end against a real running
  `order-service`: a real order placement produced a real `PENDING` assignment with no synchronous
  call, and claim → pickup → deliver each produced a real event — confirmed via
  `notification-service` (extended to consume this new event type too, see below) showing all four
  notifications (`ORDER_PLACED`, `DELIVERY_ASSIGNED`, `DELIVERY_PICKED_UP`, `DELIVERY_DELIVERED`)
  for the same order. A cancelled order correctly cancelled its assignment. 6 Testcontainers-backed
  tests (Postgres + RabbitMQ) plus an 18-request Postman collection
  (`postman/FDP-delivery-service.postman_collection.json`, 25 assertions), both green.
  `notification-service` gained a second listener/queue (`DeliveryEventListener` /
  `notification-service.delivery-events`) to consume this event, completing the consumption side
  `docs/services/notification-service.md` was originally scoped for.
- `api-gateway` route for `/api/deliveries/**` added. Was blocked on `api-gateway` not existing yet
  (Sprint 4 scope); **done retroactively** once Sprint 4 landed — see that sprint's own notes.

**Exit criteria:** placing an order produces a delivery record automatically with no synchronous
call from `order-service` into `delivery-service`; a failed/poisoned message lands in the DLQ
instead of blocking the queue; notification records are queryable via `notification-service`'s
API. **Met and verified live**, including the `/api/deliveries/**` gateway route once Sprint 4
closed that gap. A real poison-message-reaches-the-DLQ scenario hasn't been deliberately triggered
end to end for either consumer — the mechanism is config, not custom code, and is the same pattern
already proven this way in Sprint 3.

---

## Sprint 6 — Observability

**Goal:** the running system is legible without attaching a debugger.

- Micrometer Tracing (Brave) → Zipkin on every service; trace continuity verified across a full
  order → delivery → notification flow, including the RabbitMQ hop. **Done and verified live for
  every domain service that exists today** (`customer-service`, `restaurant-service`,
  `order-service`, `delivery-service`, `notification-service`) — pulled forward the same way
  order-service's async publish was pulled forward into Sprint 3, extended to `delivery-service` the
  same day it was built. A real order placement now produces **one trace spanning all five domain
  services plus RabbitMQ plus Redis** — `order-service -> customer-service`,
  `order-service -> restaurant-service` (which itself hits Redis for its cache),
  `order-service -> rabbitmq -> delivery-service`, `order-service -> rabbitmq ->
  notification-service` — confirmed via `GET /api/v2/trace/{traceId}` showing every hop under one
  shared trace ID. `api-gateway` doesn't yet participate in this trace (it has no distributed-tracing
dependency of its own — RULES.md §13 observability hasn't reached that module; see
`docs/services/api-gateway.md`). Two non-obvious
  gotchas surfaced and fixed along the way — see `docs/technologies/zipkin.md`'s "Getting started"
  section for both. Also closed a real, previously-reported gap as a side effect: every error
  response's `traceId` field was always `null` before this; it's now a real, directly-lookup-able
  Zipkin trace ID.
- Structured JSON logging to stdout (RULES.md §1 factor 11) on every service that has any code at
  all — **done**, `logging.structured.format.console=ecs` (Spring Boot's own native structured
  logging, no extra dependency), verified live: every log line emitted during an active trace
  carries flat `traceId`/`spanId` fields automatically, the same id shown in Zipkin and in any error
  response's `traceId` field (§13's "one correlation ID, three places" — two of three now real, the
  third, Kibana, is the remaining piece below). `/actuator/loggers` also exposed on every service
  with an actuator dependency, so a package's log level can be read and changed at runtime with no
  restart.
- Elasticsearch + Logstash + Kibana added to `docker-compose.yml`; every service's stdout JSON
  logs land in Kibana, correlated by trace ID. **Not done** — the JSON logs themselves exist now
  (see above), just not yet shipped anywhere.
- Actuator health/metrics/circuitbreaker endpoints exposed and verified on every service. Health
  was already exposed and verified per-service as each was built, now with full component detail
  (`management.endpoint.health.show-details=always`); `circuitbreakers` is exposed on
  `order-service` specifically (its only service with circuit breakers, Sprint 3); `info` is now
  exposed everywhere an actuator dependency exists. **Not done:** `/actuator/metrics`/
  `/actuator/prometheus` are not yet exposed anywhere (Prometheus/Grafana are explicitly Sprint 9
  scope, RULES.md §13).

**Exit criteria:** a single order can be traced end-to-end in Zipkin across all five services it
touches, and its logs can be found in Kibana filtered by that trace ID. **Partially met:** the
tracing half is done and verified live across all five domain services — confirmed as one real
trace, not five separate ones. The Kibana/logs half remains open (the JSON logs exist; nothing
ships them to Elasticsearch yet).

---

## Sprint 7 — Full containerization & CI/CD hardening

**Goal:** the complete nine-service system starts with one command, and every service's pipeline
is production-shaped.

- Multi-stage `Dockerfile` for every service; `.dockerignore` per service.
- Full `docker-compose.yml`: all eight services + all infra, health checks, correct
  `depends_on: condition: service_healthy` startup ordering.
- `application-docker.yml` profile per service using container hostnames and env-injected
  secrets.
- Per-service GitHub Actions pipelines finalized with path filters; merge-to-`main` builds and
  pushes a SHA-tagged Docker image.

**Exit criteria:** `docker compose up` brings up the entire platform from a clean checkout with no
manual steps; every service's CI pipeline independently builds, tests, and (on merge) publishes an
image.

---

## Sprint 8 — Fault tolerance, end-to-end testing, documentation

**Goal:** ship-ready: verified, documented, and matched against `ReadMe.md`'s evaluation
criteria.

- Postman collection covering every endpoint through the gateway, including the full flow:
  register → browse restaurants → place order → delivery assigned → delivery completed.
- Fault-tolerance verification: stop each service in turn, confirm the rest of the system degrades
  as specified in `RULES.md` §7, not by accident.
- Architecture diagram (all services, databases, gateway, event flows) in
  `docs/architecture/`.
- Migration decision log — why each service boundary was drawn where it is — in `docs/decisions/`.
- Final pass on each service's OpenAPI spec and root `docs/ReadMe.md` setup instructions.

**Exit criteria:** the system matches every acceptance criterion in `ReadMe.md`'s five epics, plus
the identity/notification/caching/observability scope added in `RULES.md`.

---

## Sprint 9 — Metrics visualization (Prometheus & Grafana)

**Goal:** a dashboard layer over the metrics every service has been exposing since Sprint 6 —
added last, deliberately, once the rest of the system is functioning end to end.

- Prometheus added to `docker-compose.yml`, scraping every service's `/actuator/prometheus`
  endpoint (`RULES.md` §13).
- Grafana added on top of Prometheus; baseline dashboards for request rate/latency, JVM and DB
  connection pool health, and Resilience4j circuit-breaker state per service.
- Cross-check against Zipkin/Kibana: a latency spike visible in Grafana should be traceable down
  to an individual request in Zipkin and its logs in Kibana (`RULES.md` §13's "one correlation ID,
  three places" plus this aggregate view).

**Exit criteria:** Grafana shows live dashboards for all eight services sourced from Prometheus,
with no service needing code changes to be scraped (Actuator + Micrometer already expose
everything Sprint 6 configured).

---

## Sequencing notes

- Sprints 0–1 are a hard prerequisite for everything else — no domain service should be started
  before `discovery-server`/`config-server`/Keycloak exist, or it'll be retrofitted later at real
  cost.
- Sprints 2–5 build the domain services in dependency order (`order-service` needs `customer-` and
  `restaurant-service` to exist first; `delivery-` and `notification-service` need `order-service`
  publishing events first).
- Sprints 6–7 (observability, containerization) could run in parallel with a second workstream if
  the team splits, since neither blocks nor is blocked by remaining domain logic.
- CI/CD auto-merge (`RULES.md` §11) is live from Sprint 0 onward — it is not a Sprint 7 add-on,
  it's the gate every sprint's work already merges through.
- Sprint 9 is intentionally last: Prometheus/Grafana visualize metrics Actuator/Micrometer have
  already been exposing since Sprint 6, so nothing before Sprint 9 is blocked waiting on it.
- Per `RULES.md` §17, each sprint above ships its service's or technology's `docs/services/` or
  `docs/technologies/` reference doc in the same PR as the code that introduces it. The current
  full set of these docs has been back-filled ahead of implementation, alongside this plan.
