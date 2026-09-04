# Bean Validation

## What it is
Jakarta Bean Validation is a declarative constraint-annotation specification for validating Java
objects; FDP uses it via `spring-boot-starter-validation`, backed by Hibernate Validator, to
validate request DTOs.

## Why FDP uses it
- All request DTOs must be validated with Jakarta Bean Validation — `@NotNull`, `@NotBlank`,
  `@Size`, `@Positive`, `@Email`, and custom `@Constraint` validators for domain-shaped rules — and
  a service must never hand-roll a null/blank check that Bean Validation already expresses (RULES.md
  §15).
- Validation is triggered declaratively (`@Valid`, `@Validated`), not by hand — this keeps it
  consistent with the other proxy-based, AOP-driven mechanisms FDP prefers over custom code
  (RULES.md §16).
- Validation failures are translated into the shared error envelope by the same global exception
  handler that every other exception type goes through, with one `errors[]` entry per invalid field
  in a single response (RULES.md §14, §15).
- Sprint 0 explicitly scaffolds a `spring-boot-starter-validation` baseline dependency in `common`
  so every service gets consistent DTO validation from its first controller onward, instead of each
  service improvising its own later (SPRINTS.md Sprint 0).

## Where it's used

| Service/module | Sprint introduced |
|---|---|
| `common` (`spring-boot-starter-validation` baseline dependency) | Sprint 0 |
| Every service exposing request DTOs (`customer-service`, `restaurant-service`, `order-service`,
  `delivery-service`, `notification-service`) | Per-service, from its first controller |

## How it's implemented in FDP
- `spring-boot-starter-validation` (Hibernate Validator) is scaffolded once as a baseline
  dependency in `common` in Sprint 0, so every service starts with consistent validation behavior
  (SPRINTS.md Sprint 0).
- Request DTOs use standard constraint annotations — `@NotNull`, `@NotBlank`, `@Size`, `@Positive`,
  `@Email` — plus custom `@Constraint` validators for domain-shaped rules such as a valid
  delivery-address format (RULES.md §15).
- Validation is triggered with `@Valid` on `@RequestBody` parameters and `@Validated` at the
  controller class level for `@RequestParam`/`@PathVariable` — never a hand-rolled null/blank check
  (RULES.md §15).
- Every `ConstraintValidator` stays side-effect-free — no DB lookups, no Feign calls. Anything
  needing state (e.g. "restaurant ID must exist," "menu item must belong to this restaurant") is
  not a bean constraint; it is a domain rule enforced in the service layer, raised as a
  `DomainException`, and handled per the shared error contract (RULES.md §15, §14).
- `MethodArgumentNotValidException`/`ConstraintViolationException` are translated by each service's
  `@RestControllerAdvice` (built on `common`'s `DomainException` hierarchy and `ApiErrorResponse`
  DTO) into the shared error envelope, with one `errors[]` entry per invalid field (RULES.md §14,
  §15).

## Related
- RULES.md §14, RULES.md §15, RULES.md §16
- SPRINTS.md Sprint 0
- `./spring-aop.md`
