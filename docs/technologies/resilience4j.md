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
  library defaults) — configured via each service's own `application.properties`, not hardcoded in
  code (RULES.md §7, §1 factor 3).
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

## Getting started

**Status today:** Live and verified — `order-service` (Sprint 3) wraps both its Feign clients
(`CustomerServiceGateway`, `RestaurantServiceGateway`) with named `@CircuitBreaker`/`@Retry`/
`@Bulkhead` instances and typed `ServiceUnavailableException` fallbacks. Verified against a real
failure, not just a unit test: with `restaurant-service` actually stopped, placing an order
returned a clean `503` in ~7 seconds (not a hang), and order placement recovered automatically once
`restaurant-service` came back — confirmed via `/actuator/circuitbreakers` showing the instance's
recorded failures and its return to `CLOSED`. "Timeout" is enforced via Feign's own
connect/read-timeout config, not Resilience4j's `@TimeLimiter` — see
`docs/services/order-service.md` for why (in short: `@TimeLimiter` requires the guarded method to
return `CompletableFuture`, which would turn a genuinely synchronous call chain async purely to
satisfy the annotation).

### How to start it
`order-service` is the one to run: `./mvnw -pl order-service -am spring-boot:run` (see
`docs/services/order-service.md` for its full prerequisites — `customer-service` and
`restaurant-service` need to be running too, since that's what it calls).

### How to access it
Circuit breaker state is observable via `order-service`'s own Actuator endpoint, same as RULES.md
§7 requires — but like every other endpoint, it needs a valid Bearer token (RULES.md §8's "secure
all endpoints" applies to Actuator too, not just business routes):
```
curl http://localhost:8084/actuator/circuitbreakers -H "Authorization: Bearer <access_token>"
```

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| `GET /actuator/circuitbreakers` | Per-instance state (`CLOSED`/`OPEN`/`HALF_OPEN`), failure counts | Live, verified |

Resilience4j itself exposes nothing standalone — this is `order-service`'s own Actuator surface,
augmented by the library.

### Installation & dependencies
- `order-service/pom.xml`: `io.github.resilience4j:resilience4j-spring-boot3` (version 2.3.0,
  pinned via a `resilience4j-bom` import in the root aggregator's `dependencyManagement`, RULES.md
  §4), `spring-cloud-starter-openfeign`, `spring-boot-starter-actuator`.
- Per-instance config (`sliding-window-size`, `failure-rate-threshold`,
  `wait-duration-in-open-state`, retry `max-attempts`/`wait-duration`, bulkhead
  `max-concurrent-calls`) lives in `application.properties`, one block per named instance
  (`customer-service`, `restaurant-service`) — no library defaults relied on (RULES.md §7).
  `ignore-exceptions` is set to `ResourceNotFoundException` on both the circuit breaker and retry
  instances: a downstream `404` (no such address/restaurant/menu item) is a legitimate business
  outcome, not evidence the dependency is unhealthy, and must never trip the breaker.

### For newcomers
Read `order-service`'s `CustomerServiceGateway`/`RestaurantServiceGateway` classes
(`order-service/src/main/java/.../client/`) alongside their `application.properties` config block
— that pairing is the whole pattern RULES.md §7 asks for, applied for real. To see it fail
gracefully yourself: start the full stack (`docs/services/order-service.md`), place one order
successfully, stop `restaurant-service`, place another — watch the clean `503` instead of a hang,
then restart `restaurant-service` and watch the very next attempt succeed on its own.

## Related
- `RULES.md §6` (Feign clients wrapped), `RULES.md §7` (resilience configuration), `RULES.md §14`
  (`CallNotPermittedException` translation), `RULES.md §13` (breaker state via Actuator)
- `SPRINTS.md` Sprint 3 (circuit breakers on Feign clients), Sprint 8 (fault-tolerance
  verification), Sprint 9 (breaker state in Grafana)
- `./spring-cloud-gateway.md`
