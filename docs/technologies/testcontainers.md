# Testcontainers

## What it is
Testcontainers is a Java library that runs real backing services — Postgres, MongoDB, RabbitMQ —
in disposable Docker containers for the duration of a test run, so integration tests exercise the
actual dependency rather than a substitute.

## Why FDP uses it
- Any test that touches Postgres, MongoDB, or RabbitMQ must run against the real thing —
  H2, embedded Mongo, and in-memory brokers are never acceptable substitutes at any test level
  (RULES.md §9, RULES.md §1 factor 10).
- This is explicitly what makes the CI auto-merge gate trustworthy: required checks include
  Testcontainers integration tests, and all-green plus branch-up-to-date is what triggers GitHub's
  native auto-merge with no manual approval step (RULES.md §9, RULES.md §11).
- Feign clients get consumer-driven contract tests rather than only mocked-response unit tests —
  Testcontainers is the mechanism that lets those tests exercise a real datastore alongside the
  contract assertions where relevant (RULES.md §9).
- Every sprint's Definition of Done is "merged to `main`," and merging requires the Testcontainers
  suite to pass — so from Sprint 1 onward, no service's pipeline is considered complete without it
  (SPRINTS.md, "Each sprint's Definition of Done").

## Where it's used

| Service/module | Backing service under test | Sprint introduced |
|---|---|---|
| `customer-service` | Postgres (`customer_db`) | Sprint 2 |
| `restaurant-service` | Postgres (`restaurant_db`) | Sprint 2 |
| `order-service` | Postgres (`order_db`) | Sprint 3 |
| `delivery-service` | Postgres (`delivery_db`), RabbitMQ | Sprint 5 |
| `notification-service` | MongoDB (`notification_db`), RabbitMQ | Sprint 5 |

Every per-service GitHub Actions pipeline (RULES.md §11) runs its Testcontainers suite as a
required check; this is present from each service's first CI pipeline, not deferred to Sprint 7.

## How it's implemented in FDP
- Each Postgres/MongoDB/RabbitMQ-backed service declares the relevant Testcontainers modules as
  test-scoped dependencies in its own `pom.xml`, versioned via the root aggregator's
  `dependencyManagement` (RULES.md §4) — never pinned per-service.
- Integration test classes spin up the real container for the duration of the test class/run and
  point the service's Spring context at it, in place of any embedded/in-memory alternative
  (RULES.md §9).
- The GitHub Actions workflow for each service (RULES.md §11) runs this suite as one of the
  required checks — compile, unit tests, Testcontainers integration tests, lint — all of which must
  be green, with the branch up to date with `main`, before native auto-merge fires.
- Because each service owns its schema/database exclusively (RULES.md §5), each service's
  Testcontainers setup is independent — no shared container or shared schema across services' test
  suites.

## Getting started

**Status today:** No service's `pom.xml` declares any Testcontainers dependency yet — confirmed by
grepping every module's `pom.xml` for "testcontainers". There is nothing to run today.
Testcontainers is planned from Sprint 2 onward, starting with the first Postgres-backed service test
(`customer-service`/`restaurant-service`).

### How to start it
Not usable yet — no service has Testcontainers-backed tests. Once a service does (Sprint 2 onward),
its integration test suite runs the same way any Maven test run does:
```
./mvnw -pl <service> -am test
```
A real Postgres (or MongoDB/RabbitMQ, depending on the service) container spins up automatically for
the duration of that test run — no manual container start needed, but a local Docker daemon must be
running, since that's what Testcontainers uses under the hood to launch it.

### How to access it
Not applicable in the usual sense — Testcontainers-managed containers are ephemeral, spun up and torn
down automatically around the test run, not something a developer connects to directly. While a test
run is in progress, `docker ps` will show the container it launched, on a randomly-assigned host
port Testcontainers picks itself.

### Endpoints it exposes
Not applicable — Testcontainers is test tooling, not a running service.

### Installation & dependencies
- Maven: the relevant Testcontainers module (e.g. `testcontainers-postgresql`,
  `testcontainers-mongodb`, `testcontainers-rabbitmq`) as a test-scoped dependency in each backed
  service's own `pom.xml`, versioned via the root aggregator's `dependencyManagement` (RULES.md §4)
  — not present in any module today.
- A running Docker daemon locally (or in CI) is the one hard requirement — Testcontainers needs it
  even for test runs that don't otherwise touch Docker Compose.

### For newcomers
The one thing worth knowing before Sprint 2 lands: Testcontainers needs a running Docker daemon on
whatever machine runs the tests, full stop — even though nothing in the test code looks like it's
"using Docker" directly. If `./mvnw test` ever fails with a connection error to the Docker socket
once these tests exist, that's the first thing to check. See `./docker.md` for the infra containers
already running today, and `./github-actions.md` for how this suite becomes a required CI check.

## Related
- RULES.md §9, RULES.md §11, RULES.md §1 factor 10
- SPRINTS.md Sprint 2, Sprint 3, Sprint 5
- `./github-actions.md`, `./docker.md`
