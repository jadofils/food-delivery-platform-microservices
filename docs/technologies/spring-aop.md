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

## Related
- RULES.md §13, RULES.md §14, RULES.md §15, RULES.md §16, RULES.md §1 factor 9
- SPRINTS.md Sprint 5 (idempotent RabbitMQ consumers), Sprint 6 (Micrometer Tracing/MDC)
- `./bean-validation.md`
