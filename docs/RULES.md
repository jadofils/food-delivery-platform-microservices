# FDP Engineering Rules

**Status:** Planning stage — no service code exists yet. This document governs how the Food
Delivery Platform (FDP) microservice system is designed and built once implementation starts.
It is binding for any contributor, human or AI, working in this repository.

This document assumes familiarity with [`ReadMe.md`](./ReadMe.md) (the base assignment: monolith
→ four services). Everything below either extends that scope (Identity, Notification, Redis,
observability, CI/CD) or makes an implicit assignment requirement explicit and non-negotiable.

---

## 1. Guiding principles — the Twelve Factors, applied to FDP

Every service must satisfy all twelve. These are not aspirational — a service that violates one
of these is not done, regardless of what its acceptance criteria say.

| # | Factor | How it applies here |
|---|--------|----------------------|
| 1 | **Codebase** | One repository, one module per service, tracked in git. A module is independently buildable and independently deployable even though it lives in a shared repo — the monorepo is a convenience for review and CI, not a coupling mechanism. Never let one service's code import another service's package. |
| 2 | **Dependencies** | Explicitly declared, never assumed. See [§4 Dependency management](#4-dependency-management) — the root POM manages *versions*, each service declares its own *dependencies*. |
| 3 | **Config** | No hostnames, credentials, queue names, or feature flags in code. Config comes from Spring Cloud Config Server + environment variables / Docker secrets. `application-{profile}.yml` selects environment (`local`, `docker`, `staging`, `prod`) — it never contains secrets, only structure. |
| 4 | **Backing services** | Postgres, MongoDB, RabbitMQ, Redis, Zipkin, Eureka, and Config Server are all attached resources, reachable only via config (URL + credentials). A service must be able to point at a different Postgres instance by changing config, not code. |
| 5 | **Build, release, run** | CI builds one immutable artifact (Docker image tagged with git SHA) per merge to `main`. That same image is promoted across environments unchanged — config is injected at run time, never baked in at build time. |
| 6 | **Processes** | Services are stateless and share-nothing. No in-memory HTTP sessions, no local file state. Anything that needs to persist across requests goes to Postgres/MongoDB; anything that needs to be shared across instances but is disposable goes to Redis. |
| 7 | **Port binding** | Each service is self-contained and exports HTTP via its own embedded server (Netty/Tomcat) on its assigned port (see [§2](#2-service-inventory)). No service depends on being deployed inside an external servlet container. |
| 8 | **Concurrency** | Scale by running more stateless instances of a service (horizontal scale-out via Eureka + Gateway load balancing), not by adding in-process threading complexity. |
| 9 | **Disposability** | Fast startup, graceful shutdown (`server.shutdown=graceful`), and idempotent RabbitMQ consumers — a message redelivered after a crash must not double-charge an order or double-create a delivery. |
| 10 | **Dev/prod parity** | `docker-compose.yml` runs the same Postgres/MongoDB/RabbitMQ/Redis versions locally as production. No H2, no embedded Mongo, no in-memory broker — ever, not even in tests (see [§9](#9-testing-rules)). |
| 11 | **Logs** | Every service writes structured JSON logs to **stdout only**. It never opens a log file and never pushes logs over the network itself. Shipping stdout to Elasticsearch is the execution environment's job (Logstash/Filebeat), not the application's. |
| 12 | **Admin processes** | Schema migrations (Flyway) and one-off data tasks run as one-off processes against the same codebase and config as the service — never as manual SQL run by hand against a live database. |

---

## 2. Service inventory

| Service | Responsibility | Datastore | Port |
|---|---|---|---|
| `config-server` | Centralized externalized configuration for every other service | — | 8888 |
| `discovery-server` | Eureka service registry | — | 8761 |
| `api-gateway` | Single entry point: routing, JWT validation, rate limiting | — | 8080 |
| `customer-service` | Customer profiles, delivery addresses | `customer_db` (Postgres) | 8082 |
| `restaurant-service` | Restaurants, menus, menu items | `restaurant_db` (Postgres) | 8083 |
| `order-service` | Order placement, order lifecycle | `order_db` (Postgres) | 8084 |
| `delivery-service` | Delivery assignment and tracking | `delivery_db` (Postgres) | 8085 |
| `notification-service` | Consumes domain events, dispatches notifications, persists notification/audit log | `notification_db` (MongoDB) | 8086 |

Port 8081 is retired, not reassigned — it belonged to the now-retired `identity-service` (see
`docs/decisions/`; Keycloak owns identity now, on its own port, listed below). Left as a gap
rather than renumbering everything else, which would just be churn.

Infrastructure (not services, but required backing resources): PostgreSQL, MongoDB, RabbitMQ,
Redis, Keycloak (:8180), Zipkin, Elasticsearch, Logstash, Kibana. All defined in
`docker-compose.yml` (see [§10](#10-containerization)).

This supersedes the port list in `ReadMe.md`, which predates `notification-service` and Keycloak.

---

## 3. Repository structure

```
fdp/
├── pom.xml                    # aggregator: <packaging>pom</packaging>, dependencyManagement only
├── config-server/
├── discovery-server/
├── api-gateway/
├── customer-service/
├── restaurant-service/
├── order-service/
├── delivery-service/
├── notification-service/
├── common/                    # shared kernel — see rule below, keep this module small
├── docker-compose.yml
├── docs/
│   ├── ReadMe.md
│   ├── RULES.md
│   ├── SPRINTS.md
│   ├── architecture/          # diagrams
│   ├── api-contracts/         # one OpenAPI spec per service
│   ├── decisions/             # ADRs — why each service boundary was drawn where it was
│   ├── services/               # one reference doc per service — see §17
│   └── technologies/           # one reference doc per tech/tool — see §17
└── .github/workflows/         # one workflow per service, path-filtered
```

**`common` sits in the same Maven reactor as every service, so there's no version-drift problem —
but there is a fan-out cost:** change a class in `common` and every dependent service needs
rebuilding, retesting, and (for anything behavioral) redeploying together. §11's CI already
path-filters a `common/**` change to trigger every dependent's pipeline because this cost is real.
So the rule isn't "share what's reusable" — it's share only what breaks the system if it's allowed
to drift, and keep everything else local even when that means duplication. Two questions settle it
for any given class:

1. Does the producer and every consumer have to agree on this, atomically, or the system is
   broken (an event's wire shape, the error envelope)? → shared.
2. Does one service clearly own this concept, with others only consuming a partial view of it? →
   the owner keeps it local; each consumer defines its **own** minimal copy of just the fields it
   needs. Duplication here is the point — it's what lets `restaurant-service` add a field to its
   API response without forcing `order-service` to rebuild.

| Belongs in `common` | Reason |
|---|---|
| Event payload DTOs (e.g. `OrderPlacedEvent`, `DeliveryStatusUpdatedEvent`) — data only, no behavior | Publisher and every consumer must agree on the wire shape or a deserialization mismatch ships silently |
| The `DomainException` hierarchy and `ApiErrorResponse` DTO (§14) | The entire point of the shared error envelope is that all eight services return byte-identical shapes |
| A `Converter<Jwt, ? extends AbstractAuthenticationToken>` mapping Keycloak's `resource_access.fdp-api.roles` claim into Spring Security `GrantedAuthority`s (§8) | Every service's OAuth2 Resource Server config needs the exact same claim path read the exact same way — one correct implementation beats eight slightly-different copies |
| Shared header/MDC constant names (`X-Trace-Id`, `X-Correlation-Id`) | A typo in a literal string used by eight services is a real, dumb bug class worth one source of truth |

| Stays a per-service copy | Reason |
|---|---|
| JPA `@Entity`/`@Repository` | Forbidden outright — sharing these recreates cross-service DB coupling even with separate databases |
| **Feign client interfaces** (e.g. `RestaurantServiceClient`) | If `common` owns it, `restaurant-service` can't change its own API without every consumer picking up a `common` change. The Feign client belongs to the **caller** — see §6 |
| REST response DTOs consumed via a Feign client | The caller should declare only the fields it actually uses; an unrelated field added by the producer shouldn't force a rebuild |
| Any domain/business logic, validators, or service classes | Not cross-cutting by definition — it belongs to whichever service owns that rule |

If a class in `common` starts accumulating service-specific logic, that's a sign it should move
into the service that owns it. Treat every addition to `common` as a bigger decision than adding a
class to a single service — the fan-out above is exactly why.

---

## 4. Dependency management

- The root `pom.xml` is a `pom`-packaged aggregator. It declares the module list and a
  `<dependencyManagement>` block (Spring Boot BOM, Spring Cloud BOM, and any shared library
  versions) plus shared `<properties>` (`java.version`, `spring-cloud.version`). It does **not**
  declare a `<dependencies>` block — nothing should be force-inherited onto every service.
- Each service's own `pom.xml` declares the actual `<dependencies>` it uses, without version
  numbers (inherited from the parent's `dependencyManagement`). `api-gateway` does not depend on
  `spring-boot-starter-data-jpa`; `customer-service` does not depend on `spring-cloud-starter-gateway`.
  A service's POM should read as an accurate list of what that service actually needs.
- Never copy a dependency version into a service POM to "pin" it — bump the version in the root
  BOM/properties instead, so every service moves together and drift is impossible.

---

## 5. Data ownership

- **Database per service.** Each service owns its schema/database exclusively. No service ever
  connects to another service's database, and no table is ever joined across service boundaries.
  Cross-domain data needs are satisfied via REST (OpenFeign) or async events, never SQL.
- Postgres services may share a single Postgres **server instance** in `docker-compose` for local
  resource efficiency (one container, five logical databases via init scripts), but each service's
  datasource credentials, connection pool, and Flyway migration history remain fully isolated. A
  production topology may split these onto separate managed instances without any application
  code change — that's the point of factor 4.
- Every Postgres-backed service owns its schema via **Flyway** migrations under
  `src/main/resources/db/migration`. Migrations are additive and forward-only on `main`; a mistake
  is fixed with a new migration, not by editing a merged one.
- **Operational logs vs. notification/audit logs — do not conflate these:**
  - *Operational/application logs* (what the ELK stack is for) are ephemeral, aggregated
    centrally from stdout, and never written to a service's own database. Retention is managed by
    Elasticsearch ILM, not application code.
  - *Notification/audit records* (what "manage notifications in DB" means) are a business
    entity: a permanent record of what was sent, to whom, over which channel, and its delivery
    status. These are owned and persisted by `notification-service` in MongoDB
    (`notification_db`), queryable through its own API. They are domain data, not log output.

---

## 6. Communication rules

- **Synchronous (OpenFeign over REST):** used only when the caller needs an immediate answer to
  proceed — e.g. `order-service` validating a menu item's price with `restaurant-service` before
  accepting an order. Every Feign client is wrapped in a Resilience4j circuit breaker with an
  explicit fallback (see [§7](#7-resilience)). Synchronous calls always go through Eureka
  (`lb://restaurant-service`), never a hardcoded host. The Feign client interface and the response
  DTO it deserializes into live in the **calling** service (`order-service` owns its own view of
  "what I need from `restaurant-service`"), never in `common` — see §3's shared-vs-local table.
- **Asynchronous (RabbitMQ):** used for anything the caller doesn't need to wait on — delivery
  assignment, notifications, audit trail. Domain events are published to topic exchanges, named
  `<Entity><PastTenseVerb>Event` (`OrderPlacedEvent`, `OrderCancelledEvent`,
  `DeliveryStatusUpdatedEvent`). Every consumer queue has a dead-letter queue; consumers are
  idempotent (dedupe on event ID) because RabbitMQ guarantees at-least-once delivery, not
  exactly-once.
- A service is never both the synchronous caller and the async publisher for the same fact in the
  same flow — pick one per interaction and document why in the relevant ADR.

---

## 7. Resilience

- Every outbound Feign/HTTP call is wrapped in Resilience4j: circuit breaker, retry, timeout, and
  bulkhead are all configured explicitly per client — no service relies on Resilience4j defaults.
- Every circuit breaker has a fallback that returns a clear, typed error to the caller (e.g. "menu
  service unavailable, try again") — never a raw timeout or a stack trace.
- Circuit breaker state (`CLOSED`/`OPEN`/`HALF_OPEN`) must be observable via that service's
  Actuator endpoint.
- The system must keep functioning with any one downstream dependency down. In particular: orders
  can still be placed if `delivery-service` is down; browsing still works if `order-service` is
  down.

---

## 8. Security

**Keycloak is FDP's identity provider — there is no custom `identity-service`.** Registration,
login, credential storage, and token issuance are Keycloak's job, not code this repo owns. This
is a deliberate reversal of an earlier design (a hand-rolled `identity-service` issuing nested
signed-and-encrypted JWTs) — see `docs/decisions/` for why. Keycloak still issues JWTs — adopting
it doesn't remove JWTs from the picture, it removes the custom crypto and user-store code that
used to build them.

- **Realm, client, roles.** A single `fdp` realm holds every FDP user. One client, `fdp-api`,
  represents the whole platform (not one client per service) — its **client roles** are FDP's
  permission strings (`order:create`, `restaurant:menu:write`, …), the same fine-grained,
  action-shaped names used throughout this document. Keycloak embeds a user's client roles in the
  access token under `resource_access.fdp-api.roles` by default, with no custom protocol mapper
  needed. Services authorize on these permission strings, never on a role/username directly.
- **Tokens are standard OIDC access tokens** — signed (RS256) by Keycloak, **not** additionally
  encrypted. Confidentiality comes from TLS in transit, the normal industry approach; a bespoke
  encryption layer on top would protect against a threat model (someone reading tokens off the
  wire without also being able to intercept TLS) that doesn't hold up on its own merits. This also
  means every service's validation logic is exactly Spring Security's stock
  `spring-boot-starter-oauth2-resource-server`, not a custom codec — real key distribution is
  Keycloak's own JWKS endpoint (`/realms/fdp/protocol/openid-connect/certs`), auto-discovered from
  `spring.security.oauth2.resourceserver.jwt.issuer-uri`. No shared secret to distribute, no
  custom `common`-owned encoder/decoder.
- **Enforcement is native Spring Security**, once a service actually has endpoints:
  `permitAll()` route matchers for public endpoints (registration/login don't exist in FDP code
  anymore — they're Keycloak's login/token endpoints directly) and
  `@PreAuthorize("hasAuthority('order:create')")` (or an equivalent `SecurityFilterChain`
  authorization rule) for everything else. This is the natural next step now that adopting
  Keycloak means adopting real Spring Security — no custom filter/interceptor pair in `common`
  standing in for it.
- **Local dev bootstrap.** Keycloak's own Postgres schema (`keycloak_db`) is infrastructure, not
  an FDP service database — FDP's Flyway migrations never touch it (§5, §10). The `fdp` realm,
  the `fdp-api` client, its baseline client roles, and one demo user per baseline role
  (`CUSTOMER`, `RESTAURANT_OWNER`, `DELIVERY_AGENT`, `ADMIN`) are provisioned automatically on
  first container start via a realm-import file (`docker/keycloak/fdp-realm.json`) — see
  `credentials.md` for the demo accounts and how to obtain a token from Keycloak directly.
- **Cross-container hostname caveat.** A token's `iss` claim is whatever hostname the client used
  to reach Keycloak's token endpoint. A token requested via `localhost:8180` (from the host
  machine) will not validate against an `issuer-uri` configured as `http://keycloak:8080/...`
  (the in-network hostname), and vice versa — Spring Security's issuer check is exact-match. The
  first service that actually validates tokens needs to settle this (fixed `KC_HOSTNAME` plus a
  consistent access pattern for whoever requests tokens) rather than rediscovering it by a
  confusing 401.
- Secrets (DB credentials, Keycloak admin credentials, RabbitMQ credentials) are never committed.
  They are injected via environment variables / Docker secrets and sourced from Config Server's
  encrypted properties, not from a plaintext file in this repo — `credentials.md`'s seeded demo
  accounts are the sole, explicit, documented exception (RULES.md itself, not a leak).

### PII masking

- Any field that is human-readable PII — email, username, phone number, anything a person would
  recognize as identifying — is annotated `@Masked` (`common.security.masking`) in its response
  DTO. Serialization replaces the value with `first character + fixed-width mask + last
  character` (`PiiMasking`); the mask width never varies with input length, so it doesn't leak how
  long the real value was.
- **A structural identifier a client needs to address a resource with — a primary-key `id` used
  in a URL path — is never masked.** Masking it would break the API's basic addressability (the
  caller that just registered or looked up that exact record can no longer use the id it was just
  given) without adding real protection, since a bare id exposes nothing by itself. Only
  human-readable PII gets this treatment, not routing identifiers.

---

## 9. Testing rules

- Any test that touches Postgres, MongoDB, or RabbitMQ runs against the **real** thing via
  **Testcontainers**. H2, embedded Mongo, and in-memory brokers are not acceptable substitutes at
  any test level — this is what makes the CI auto-merge gate in [§11](#11-cicd--auto-merge)
  trustworthy.
- Feign clients get consumer-driven contract tests, not just mocked-response unit tests.
- End-to-end coverage (Postman collection through the gateway, per `ReadMe.md` §5.1) runs against
  the full `docker-compose` stack, not against services started individually.

---

## 10. Containerization

- Every service builds its image **two ways**, deliberately, not redundantly:
  - **Jib** (`jib-maven-plugin`, configured in each service's own `pom.xml`) is the mechanism CI
    actually uses (§11) to produce and push the image on merge to `main`. Jib needs no Docker
    daemon and no `Dockerfile`, builds reproducible layers straight from the Maven build, and is
    faster in CI — this is the production path.
  - A hand-written **multi-stage `Dockerfile`** (build stage → slim runtime stage) is also
    maintained per service, kept for learning purposes and for anyone who wants to `docker build`
    a service manually without Maven. It is not what CI runs, and the two must not silently
    diverge — if a service's runtime dependencies change, update both.
- `docker-compose.yml` at the repo root starts the complete system: all eight FDP services plus
  Postgres, MongoDB, RabbitMQ, Redis, Keycloak, Zipkin, Elasticsearch, Logstash, Kibana,
  Prometheus, and Grafana (§13). Locally, `docker-compose.yml` references images built by Jib
  (`jib:dockerBuild` for a local-only image, or the registry tag CI already pushed) — it does not
  invoke the learning-purpose Dockerfiles as part of the standard `docker compose up` flow.
- Keycloak is provisioned on first start from a realm-import file
  (`docker/keycloak/fdp-realm.json`, mounted read-only) — the `fdp` realm, `fdp-api` client,
  baseline client roles, and demo users all come from that one file, not manual console setup.
  Its own schema lives in `keycloak_db` (created by `docker/postgres/init-databases.sql`, same as
  any other Postgres-backed database here) — Keycloak owns that schema entirely; FDP's Flyway
  migrations never touch it (§5, §8).
- Health checks are mandatory on every container; `depends_on` uses `condition: service_healthy`
  so `discovery-server` and `config-server` are ready before dependents start, and infra
  (databases, broker) is ready before any service that needs it.
- Each service ships an `application-docker.yml` using Docker service names for hostnames
  (`jdbc:postgresql://postgres:5432/order_db`) and environment variables for secrets — never a
  hardcoded `localhost`.

---

## 11. CI/CD & auto-merge

- **Branching:** `main` is protected and always deployable. All work happens on
  `feature/<service-name>-<short-description>` (e.g. `feature/order-service-place-order-endpoint`).
  Non-feature work uses `fix/`, `chore/`, or `docs/` in place of `feature/`. One branch changes one
  service (or one clearly-scoped cross-cutting concern, e.g. `chore/root-pom-bump-spring-boot`) —
  never bundle unrelated services into one branch.
- **Pipeline:** one GitHub Actions workflow per service, path-filtered so a change to
  `order-service/**` doesn't trigger `restaurant-service`'s pipeline. A change to `common/**`
  triggers every service that depends on it.
- **Required checks** before merge is even considered: compile, unit tests, Testcontainers
  integration tests, lint. All must be green and the branch must be up to date with `main`.
- **Auto-merge:** once required checks pass, the PR is merged into `main` automatically (GitHub's
  native auto-merge, gated on branch protection required-checks). No manual approval step blocks
  this — the Testcontainers suite *is* the gate. There is no force-merge bypass outside a
  documented hotfix procedure.
- **On merge to `main`:** CI runs `mvn jib:build` to build and push a Docker image tagged with the
  git SHA (and a semver tag for releases) directly to the registry, per §10. The same image is
  what gets promoted to staging/prod — never rebuilt per environment.

---

## 12. Caching (Redis)

- Redis is the single centralized cache — no per-instance local/in-memory caches, which would
  break statelessness across horizontally-scaled replicas (factor 6 and factor 8).
- Cache keys are namespaced per service (`restaurant-service:menu:{id}`,
  `gateway:rate-limit:{clientId}`) so services can share one Redis instance without key collisions.
- Every cache entry has an explicit TTL. Nothing is cached indefinitely.
- Primary uses: `api-gateway` rate limiting (Spring Cloud Gateway `RequestRateLimiter` backed by
  Redis), read-heavy `restaurant-service` menu lookups.

---

## 13. Observability

- **Tracing:** Micrometer Tracing (Brave) exports spans to Zipkin from every service. Trace IDs
  propagate across both REST (Feign) and RabbitMQ hops so a full order flow is visible as one
  trace.
- **Logging:** structured JSON to stdout only (factor 11). Logstash tails container output and
  ships to Elasticsearch; Kibana is the query/dashboard layer. No service configures a direct
  network log appender — that would couple application code to Logstash's location and violate
  factor 11. Micrometer Tracing populates the trace/span ID into MDC automatically, so every log
  line is correlated to a trace with no manual wiring per service.
- **Metrics:** every service exposes Actuator (`/actuator/health`, `/actuator/metrics`,
  `/actuator/circuitbreakers`, `/actuator/prometheus` via the Micrometer Prometheus registry).
- **Visualization:** Prometheus scrapes every service's `/actuator/prometheus` endpoint; Grafana
  sits on top as the dashboard layer (request rates, latency, JVM/DB pool metrics, circuit-breaker
  states). This is added once the core system — services, gateway, messaging, tracing, logging —
  is functioning end to end; it is the last piece of the observability stack, not a prerequisite
  for the others (see Sprint 9 in `SPRINTS.md`).
- One correlation ID, three places: the same trace ID appears in Zipkin, in every Kibana log line
  for that request, and in the `traceId` field of any error response the request produced (§14).
  Nobody should ever need to guess which log lines belong to which failed request. Prometheus/
  Grafana metrics are aggregate, not per-request, so they complement this correlation rather than
  participating in it — a dashboard tells you *that* latency spiked, a trace tells you *which*
  request and why.

---

## 14. API error contract & exception handling

- Every service returns errors in one shared envelope shape — `timestamp`, `status`, `error`
  (machine-readable code), `message` (human-readable), `path`, `traceId`, and an optional
  `errors[]` array of `{field, message}` for validation failures. No service invents its own
  shape.
- **Exception taxonomy:**
  - **Unchecked `DomainException`** is the default for anything raised from business/service
    logic, and splits into two families covering every 4xx/5xx a service is realistically expected
    to raise — not 1xx/2xx/3xx, which are never thrown (2xx is a normal return value, 1xx is
    protocol-level, 3xx has no real place in a JSON API). `ClientErrorException` (the request is
    the problem — `BadRequestException` 400 through `TooManyRequestsException` 429) and
    `ServerErrorException` (this service or a dependency is the problem — `InternalServerException`
    500 through `GatewayTimeoutException` 504); the full, current list is documented once, in
    `common`'s `error` package-info, not duplicated here. Each subtype implements the shared
    `ApiError { int status(); String code(); }` contract (a plain `int`, not Spring Web's
    `HttpStatus` — see the implementation note below) so the global handler maps it generically
    instead of hardcoding a growing if/else chain, and the client/server split lets that same
    handler log a `ClientErrorException` at `DEBUG` (expected, frequent, not our bug) and a
    `ServerErrorException` at `ERROR` (our side needs attention) without special-casing every
    individual subtype.
  - **Checked exceptions are reserved narrowly** — only where forcing the caller to acknowledge
    failure at compile time earns its keep, e.g. a Feign fallback method's declared failure mode,
    or a message-listener boundary where an explicit `throws` documents an expected failure path.
    Everywhere else, prefer unchecked: Spring's exception translation, `@Transactional`'s
    rollback-on-unchecked default, and `@RestControllerAdvice` are all built around unchecked
    propagation, and a checked exception silently skips transaction rollback unless `rollbackFor`
    is added explicitly. Treat "should this be checked?" as a deliberate choice per exception
    type, not a default.
  - The same global handler also translates framework exceptions into the shared envelope: Bean
    Validation (`MethodArgumentNotValidException`, `ConstraintViolationException`, see §15), Feign
    (`FeignException` and subclasses), Resilience4j (`CallNotPermittedException` for an open
    circuit), and an unmapped catch-all `Exception` → `500` with a sanitized message. The full
    stack trace is logged server-side with the trace ID attached; it is never returned to the
    client.
- **Implementation:** one `@RestControllerAdvice` per service, built on shared base types that
  live in `common`'s `error` package (`DomainException` hierarchy, `ApiErrorResponse` DTO with
  `of`/`ofValidation`/`ofUnexpected` factory methods) — cross-cutting infrastructure, not domain
  code, so it doesn't conflict with the `common` module rule in §3.
  - `common` also provides `AbstractGlobalExceptionHandler`, a base class carrying the
    `@ExceptionHandler` methods for `DomainException`, `MethodArgumentNotValidException`,
    `ConstraintViolationException`, and the `Exception` catch-all. A service wires it in by
    extending it from its own `@RestControllerAdvice`-annotated class, adding only what that
    service needs beyond the shared set (e.g. `FeignException`/`CallNotPermittedException` for a
    service that makes outbound calls — not in the shared base, since not every service does).
  - This base targets the Servlet (Spring MVC) stack, used by every service except
    `api-gateway`, which is WebFlux and defines its own `ServerWebExchange`-flavored advice — but
    reuses the same `ApiError`/`DomainException`/`ApiErrorResponse` types, since those stay
    stack-agnostic on purpose.
  - **Ordering.** Dispatch order *within* one advice bean is automatic — Spring always picks the
    most specific declared handler for the thrown exception's actual type, regardless of method
    declaration order; nothing to configure. Precedence *across two separate* advice beans in the
    same service is not automatic and needs `@Order` on each advice class if that situation ever
    arises — it doesn't today, since each service has exactly one advice bean, inheriting
    everything from the shared base.
- Every error response carries the current distributed trace ID in `traceId` (§13), so a
  client-reported failure can be looked up directly in Zipkin/Kibana without asking when it
  happened.

---

## 15. Validation

- All request DTOs are validated with Jakarta Bean Validation (`spring-boot-starter-validation`,
  backed by Hibernate Validator) — `@NotNull`, `@NotBlank`, `@Size`, `@Positive`, `@Email`, and
  custom `@Constraint` validators for domain-shaped rules (e.g. a valid delivery-address format).
- Validation is triggered declaratively, not by hand: `@Valid` on `@RequestBody` parameters,
  `@Validated` at controller class level for `@RequestParam`/`@PathVariable`. A service must never
  hand-roll a null/blank check that Bean Validation already expresses.
- A `ConstraintValidator` stays side-effect-free — no DB lookups, no Feign calls. Anything needing
  state (`restaurant ID must exist`, `menu item must belong to this restaurant`) is not a bean
  constraint; it's a domain rule enforced in the service layer, raised as a `DomainException`, and
  handled per §14.
- Validation failures are translated into the shared error envelope by the same global exception
  handler, with one `errors[]` entry per invalid field — a client gets every violation in one
  response, not one-at-a-time.

---

## 16. Cross-cutting concerns (AOP)

- Prefer Spring's existing AOP-based mechanisms before reaching for a custom `@Aspect`:
  `@RestControllerAdvice` for exception translation (§14), `@Valid`/`@Validated` for validation
  (§15), and Micrometer Tracing's automatic instrumentation for trace/span propagation (§13) are
  themselves proxy-based cross-cutting mechanisms — they cover most of what a hand-written aspect
  would otherwise duplicate.
- Custom `@Aspect`s are for concerns Spring doesn't already provide: method-level audit/performance
  logging, permission-check logging, idempotency-key enforcement on RabbitMQ listener methods.
  They live in each service's own `aop` package. Promote one to a shared `common` starter only
  once an identical aspect is duplicated in three or more services — don't pre-abstract.
- A logging/audit aspect enriches its output with the active trace ID already in MDC (populated
  automatically by Micrometer Tracing) rather than minting its own correlation ID — one
  correlation ID per request, used everywhere: logs, traces, and error responses.
- Aspects log and rethrow; they never catch an exception to log it and then swallow it. Translating
  an exception into an HTTP response is the global handler's job (§14), not an aspect's.

---

## 17. Documentation

Beyond this file, `SPRINTS.md`, and `ReadMe.md`, every service and every technology/tool decided
in this document gets its own short reference doc — one file per item, not a shared wall of text.

- **`docs/services/<service-name>.md`** — one per row in the §2 service inventory. Covers:
  responsibility, why it's a separate service (boundary rationale), its database, its planned API
  surface, what it depends on / what depends on it (§6), and which sprint delivers it.
- **`docs/technologies/<tool-name>.md`** — one per technology/tool named anywhere in this file
  (Postgres, MongoDB, RabbitMQ, Redis, Flyway, Keycloak, JWT, Resilience4j, Eureka, Spring Cloud
  Config, Spring Cloud Gateway, Zipkin, Elasticsearch, Logstash, Kibana, Prometheus, Grafana,
  Testcontainers, Jib, Docker, Bean Validation, Spring AOP, GitHub Actions). Covers: what it is,
  *why* FDP uses it (tied to a concrete need, not a generic selling point), *where* it's used
  (which service(s), which sprint introduces it), and *how* it's implemented here (starter/
  dependency name, config keys, container/port if it's infra).
- These docs explain a decision already made in this file or `SPRINTS.md` — they don't make new
  ones. If a tech doc and this file disagree, this file wins and the tech doc is wrong; fix the
  tech doc.
- A sprint that introduces a new service or a new piece of technology ships that service's or
  technology's doc in the same PR — documentation is not a separate, deferred pass.

---

## 18. Non-goals right now

This repository is at the **planning stage**. This rules file, `SPRINTS.md`, and the per-service/
per-technology reference docs under `docs/services/` and `docs/technologies/` (§17) are the only
expected deliverables until sprint execution begins — do not scaffold service modules, write
Dockerfiles, or stand up `docker-compose.yml` unless a sprint explicitly calls for it.
