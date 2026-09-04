# Spring AOP

## What it is
Spring AOP is Spring's proxy-based aspect-oriented programming support. In FDP it covers both
Spring's own built-in AOP-based mechanisms (`@RestControllerAdvice`, `@Valid`/`@Validated`,
Micrometer Tracing's automatic instrumentation) and custom `@Aspect` classes written where those
built-in mechanisms don't reach.

## Why FDP uses it
- FDP's default is to prefer Spring's existing AOP-based mechanisms — `@RestControllerAdvice` for
  exception translation, `@Valid`/`@Validated` for validation, Micrometer Tracing's auto-
  instrumentation for trace/span propagation — before reaching for a custom `@Aspect`, since these
  already cover most of what a hand-written aspect would otherwise duplicate (RULES.md §16).
- Custom `@Aspect`s are reserved for concerns Spring doesn't already provide: method-level
  audit/performance logging, permission-check logging, and idempotency-key enforcement on RabbitMQ
  listener methods — the last of which is a concrete, named requirement given RabbitMQ's
  at-least-once delivery guarantee (RULES.md §6, §16).
- Idempotent RabbitMQ consumers are a twelve-factor requirement in their own right (a redelivered
  message must not double-charge an order or double-create a delivery), which is exactly the kind
  of enforcement a custom aspect on a listener method is suited for (RULES.md §1 factor 9, §16).
- A logging/audit aspect is required to enrich its output with the trace ID already in MDC
  (populated automatically by Micrometer Tracing) rather than minting its own correlation ID — one
  correlation ID per request, used everywhere (RULES.md §13, §16).

## Where it's used

| Service/module | AOP concern | Sprint context |
|---|---|---|
| `order-service`, `restaurant-service` (via `@RestControllerAdvice`, `@Valid`/`@Validated`) | Exception translation, validation — Spring's built-in AOP mechanisms, not custom aspects | Sprint 0 (`common` baseline), per-service thereafter |
| `delivery-service`, `notification-service` (custom `@Aspect` in each service's own `aop` package) | Idempotency-key enforcement on RabbitMQ listener methods | Sprint 5 (RabbitMQ consumers introduced, required to be idempotent) |
| Every service (Micrometer Tracing auto-instrumentation) | Trace/span propagation, MDC population | Sprint 6 |

Custom, hand-written `@Aspect`s beyond idempotency enforcement (e.g. audit/performance logging,
permission-check logging) are added per-service as each service's needs call for them, per RULES.md
§16 — they live in that service's own `aop` package and are promoted to a shared `common` starter
only once an identical aspect is duplicated in three or more services.

## How it's implemented in FDP
- Spring's built-in AOP-based mechanisms are used first: `@RestControllerAdvice` (§14),
  `@Valid`/`@Validated` (§15), and Micrometer Tracing's automatic instrumentation (§13) — all
  proxy-based, all cross-cutting, none requiring a hand-written `@Aspect` (RULES.md §16).
- Where a custom `@Aspect` is genuinely needed — method-level audit/performance logging,
  permission-check logging, idempotency-key enforcement on RabbitMQ listeners — it lives in that
  service's own `aop` package, not in `common`, until it is duplicated in three or more services
  (RULES.md §16).
- Idempotency-key enforcement aspects wrap RabbitMQ listener methods to dedupe on event ID, since
  RabbitMQ guarantees at-least-once delivery, not exactly-once (RULES.md §6, §1 factor 9).
- A logging/audit aspect reads the active trace ID from MDC (already populated by Micrometer
  Tracing) rather than generating its own correlation ID (RULES.md §13, §16).
- Aspects log and rethrow — they never catch an exception to log it and then swallow it. Translating
  an exception into an HTTP response remains the global handler's job, not an aspect's (RULES.md
  §16, §14).

## Getting started

**Status today:** Partially real, in one specific sense. `common`'s
`AbstractGlobalExceptionHandler` (`common/src/main/java/food_delivery/Platform/common/error/`,
Sprint 0) is the AOP-adjacent shared base that a service's future `@RestControllerAdvice` will
extend — proxy-based AOP infrastructure per RULES.md §16's preference order — and it compiles
today. But no service has extended it into its own `@RestControllerAdvice` subclass yet, since no
service has a controller/endpoint at all (every business service is still a bare skeleton,
`spring-boot-starter` + `spring-boot-starter-test` only). And no *custom* `@Aspect` class exists
anywhere in the repo (verified: `@Aspect` appears only in `RULES.md` and this doc, nowhere in Java
source), nor does any service `pom.xml` declare `spring-boot-starter-aop` (verified: no match
across any `pom.xml`). The idempotency-key aspects and audit/performance-logging aspects described
above are Sprint 5+ work that hasn't started.

### How to see it running
AOP isn't a running process, so "starting" it doesn't apply the way it does for infrastructure.
The closest concrete demonstration, once it exists: a service defines a controller, wires its own
`@RestControllerAdvice`-annotated class extending `AbstractGlobalExceptionHandler`, and throwing a
`DomainException` (or letting `MethodArgumentNotValidException` bubble up) from that controller
shows the proxy-based handling in action — a translated error response instead of a raw stack
trace. That first wiring is `customer-service`, Sprint 2 onward, once it has an endpoint.

### Endpoints it exposes
None — Spring AOP is a cross-cutting code mechanism, not a network-addressable thing. It shapes how
requests are handled once they reach a controller; it doesn't add endpoints of its own.

### Installation & dependencies
- `common` already depends on `spring-web` (not `spring-boot-starter-web`, see `common/pom.xml`),
  which is what supplies `@RestControllerAdvice`/`@ExceptionHandler` without pulling in an embedded
  servlet container — that's live today.
- `spring-boot-starter-aop` is added to a service's own `pom.xml` only if/when that service needs a
  genuinely custom `@Aspect` Spring's built-in mechanisms don't cover (RULES.md §16) — no service
  needs it yet. Per RULES.md §16, a hand-written aspect is promoted to a shared `common` starter
  only once it's duplicated in three or more services — don't pre-abstract before that.

### For newcomers
The one concrete, AOP-adjacent thing that exists and compiles today is `common`'s error package —
look at `common/src/main/java/food_delivery/Platform/common/error/`, especially
`AbstractGlobalExceptionHandler.java`, to see the shared base every service's advice class will
extend. Read RULES.md §16 for the hierarchy FDP follows: prefer Spring's built-in AOP mechanisms
(`@RestControllerAdvice`, `@Valid`/`@Validated`, Micrometer Tracing's auto-instrumentation) first,
and reach for a custom `@Aspect` only for what those don't already cover — audit/performance
logging, permission-check logging, idempotency-key enforcement on RabbitMQ listeners.

## Related
- RULES.md §13, RULES.md §14, RULES.md §15, RULES.md §16, RULES.md §1 factor 9
- SPRINTS.md Sprint 5 (idempotent RabbitMQ consumers), Sprint 6 (Micrometer Tracing/MDC)
- `./bean-validation.md`
