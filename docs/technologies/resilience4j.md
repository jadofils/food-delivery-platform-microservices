# Resilience4j

## What it is
Resilience4j is a lightweight fault-tolerance library providing circuit breaker, retry, timeout,
bulkhead, and rate limiter modules, composable per client call and integrated with Spring Boot via
annotations/AOP.

## Why FDP uses it
- Synchronous calls between services (OpenFeign) are the failure points that can cascade across the
  system if left unprotected — every outbound Feign/HTTP call is wrapped in a circuit breaker with
  an explicit fallback (RULES.md §6, §7).
- The system must keep functioning with any one downstream dependency down — orders can still be
  placed if `delivery-service` is down, and browsing still works if `order-service` is down; this is
  the concrete behavior Resilience4j is required to guarantee (RULES.md §7).
- FDP explicitly rejects relying on Resilience4j defaults — circuit breaker, retry, timeout, and
  bulkhead are all configured explicitly per client, so failure behavior is a deliberate decision,
  not an accident of library defaults (RULES.md §7).
- Circuit breaker state must be observable operationally, not just functionally — the
  `CLOSED`/`OPEN`/`HALF_OPEN` state is exposed via Actuator so it can be watched during fault-
  tolerance verification (RULES.md §7; SPRINTS.md Sprint 8) and visualized later in Grafana
  (SPRINTS.md Sprint 9).
- An open circuit must still produce a clean, typed error through the same error contract as every
  other failure, not a raw exception — `CallNotPermittedException` is translated by the global
  exception handler (RULES.md §14).

## Where it's used

| Service | Feign client(s) wrapped | Sprint |
|---|---|---|
| `order-service` | Calls to `customer-service` (validate customer/address), `restaurant-service` (validate menu items/pricing) | Sprint 3 |
| Any service with an outbound Feign/HTTP call added later | Same pattern applied per RULES.md §7 | As introduced |

## How it's implemented in FDP
- Dependency: `resilience4j-spring-boot3` (with `spring-boot-starter-aop`) in each service that
  makes outbound Feign/HTTP calls — declared per-service per RULES.md §4, not force-inherited from
  the root POM.
- Every Feign client method is annotated with `@CircuitBreaker`, `@Retry`, `@TimeLimiter`, and
  `@Bulkhead`, each with an explicit instance name and explicit configuration (no reliance on
  library defaults) — configured via `config-server`-sourced properties, not hardcoded (RULES.md
  §7, §1 factor 3).
- Each circuit breaker declares a typed fallback method returning a clear, structured error (e.g.
  "menu service unavailable, try again") rather than propagating a timeout or stack trace (RULES.md
  §7).
- `CallNotPermittedException`, raised when a breaker is `OPEN`, is caught and translated by the
  service's shared `@RestControllerAdvice` into the standard error envelope (`timestamp`, `status`,
  `error`, `message`, `path`, `traceId`) alongside Feign and Bean Validation exceptions (RULES.md
  §14).
- Breaker state is exposed via `/actuator/circuitbreakers` (and the broader Actuator surface:
  `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`) per RULES.md §13; verified
  explicitly in Sprint 6 and scraped by Prometheus/visualized in Grafana in Sprint 9.
- Fault-tolerance verification (stopping each service in turn and confirming graceful degradation)
  is a Sprint 8 exit criterion tied directly to this configuration (SPRINTS.md Sprint 8).

## Related
- `RULES.md §6` (Feign clients wrapped), `RULES.md §7` (resilience configuration), `RULES.md §14`
  (`CallNotPermittedException` translation), `RULES.md §13` (breaker state via Actuator)
- `SPRINTS.md` Sprint 3 (circuit breakers on Feign clients), Sprint 8 (fault-tolerance
  verification), Sprint 9 (breaker state in Grafana)
- `./spring-cloud-gateway.md`
