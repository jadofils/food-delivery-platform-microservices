# Running FDP locally

A practical reference for starting **infrastructure** (Postgres, MongoDB, RabbitMQ, Redis,
Keycloak — all Docker containers) and **FDP services** (config-server, discovery-server,
customer-service, … — currently run via Maven, not Docker; see [Naming & tagging](#naming--tagging)
for why) — one at a time, a few at a time, or everything that's currently implemented.

See `docs/RULES.md` §2 for the canonical service/port inventory and `docs/SPRINTS.md` for what's
actually built vs. still planned. This file only covers *how to start things*, not what they do —
each service's own `docs/services/<name>.md` and each technology's `docs/technologies/<name>.md`
cover that.

---

## Quick reference

| Component | Kind | Container / process name | Port | Status |
|---|---|---|---|---|
| `postgres` | Docker | `fdp-postgres` | 5432 | Infra — live |
| `mongodb` | Docker | `fdp-mongodb` | 27017 | Infra — live, unused by any service yet |
| `rabbitmq` | Docker | `fdp-rabbitmq` | 5672 (AMQP), 15672 (mgmt UI) | Infra — live, unused by any service yet |
| `redis` | Docker | `fdp-redis` | 6379 | Infra — live, unused by any service yet |
| `keycloak` | Docker | `fdp-keycloak` | 8180 | Infra — live, identity provider |
| `config-server` | Maven (`spring-boot:run`) | `config-server` | 8888 | **Implemented** |
| `discovery-server` | Maven | `discovery-server` | 8761 | **Implemented** |
| `customer-service` | Maven | `customer-service` | 8082 | **Implemented** (needs `postgres` + `keycloak`) |
| `api-gateway` | Maven | `api-gateway` | 8080 | Skeleton — boots, no routes yet |
| `restaurant-service` | Maven | `restaurant-service` | 8083 | Skeleton |
| `order-service` | Maven | `order-service` | 8084 | Skeleton |
| `delivery-service` | Maven | `delivery-service` | 8085 | Skeleton |
| `notification-service` | Maven | `notification-service` | 8086 | Skeleton |
| `common` | — | — | — | Shared library, not a runnable service |

"Skeleton" services start fine (`spring-boot:run` boots successfully) but have no business
endpoints yet — starting one just proves it compiles and boots.

---

## Naming & tagging

**Docker containers (infra) — done, consistent today.** Every container in `docker-compose.yml`
has an explicit `container_name: fdp-<name>` and a pinned version tag (`postgres:17-alpine`,
`mongo:7`, `rabbitmq:4-management-alpine`, `redis:7-alpine`, `quay.io/keycloak/keycloak:26.0`) —
never `:latest`, so `docker ps` / `docker logs` / `docker exec` always target something
unambiguous:
```
docker ps --filter "name=fdp-"
docker logs -f fdp-postgres
docker exec -it fdp-postgres psql -U fdp -d customer_db
```

**FDP services — not containerized yet, by design.** Per `docs/RULES.md` §10 and
`docs/SPRINTS.md` Sprint 7, each service gets its own Docker image (via Jib in CI, plus a
hand-written `Dockerfile` for learning) once Sprint 7 lands — not before, so this doesn't pull
scope forward from a sprint that hasn't started. Until then, a service is identified by:
- Its Maven module name (`customer-service`, matching the folder and the `pom.xml` `artifactId`).
- `spring.application.name` in its `application.properties`, which is what shows up as the
  `[customer-service]` prefix on every log line and what Eureka registers it under.
- Its port (see the table above).

When Sprint 7 containerizes these, the same `fdp-<service-name>` convention above will extend to
them: `container_name: fdp-customer-service`, image tag `fdp/customer-service:<git-sha>` — same
naming shape, applied consistently, not invented fresh at that point.

---

## Prerequisites

- **Docker Desktop** running (needed for every infra container, and for Testcontainers-backed
  tests).
- **JDK 25**. No local Maven install needed — every command below uses the vendored wrapper
  (`./mvnw` / `.\mvnw.cmd`), which downloads the right Maven version itself on first use.

---

## Starting infrastructure (Docker)

All commands run from the repo root.

### All five infra containers at once
```powershell
docker compose up -d
```
```bash
docker compose up -d
```

### A specific subset
List the service names (from `docker-compose.yml`) you want, space-separated. Example — just what
`customer-service` needs:
```powershell
docker compose up -d postgres keycloak
```
```bash
docker compose up -d postgres keycloak
```

### One container only
```powershell
docker compose up -d postgres
```

### Checking status / health
```powershell
docker compose ps
docker inspect --format='{{.State.Health.Status}}' fdp-keycloak
```
Every container has a healthcheck; `docker compose ps` shows `healthy` once it's actually ready to
use, not just started — wait for that before starting a dependent FDP service, especially Keycloak
(its realm import can take 20-40s on first start).

### Stopping
```powershell
docker compose stop                 # stop containers, keep data volumes
docker compose down                 # remove containers, keep data volumes
docker compose down -v              # remove containers AND data volumes (fresh next start —
                                     # re-triggers docker/postgres/init-databases.sql and
                                     # Keycloak's realm import from scratch)
```

---

## Starting FDP services (Maven)

`spring-boot:run` **blocks its terminal** for as long as the service runs — there is no single
Maven command that starts multiple services at once, because each is its own long-running JVM
process. Give each service its own terminal (simplest, see its logs live) or background it
(scripted/headless use).

### One service, in its own terminal — the normal way
Open a terminal, then:
```powershell
.\mvnw.cmd -pl customer-service -am spring-boot:run
```
```bash
./mvnw -pl customer-service -am spring-boot:run
```
`Ctrl+C` stops it. Swap `customer-service` for any module name from the table above.

### Several services at once
Open one terminal **per service** and run the command above in each — this is the most reliable
way to watch each service's own logs. If you'd rather not manage several terminal windows by
hand, background them instead:

```powershell
# PowerShell -- each Start-Process opens its own window you can still see/close individually
Start-Process powershell -ArgumentList '-NoExit','-Command','.\mvnw.cmd -pl discovery-server -am spring-boot:run'
Start-Process powershell -ArgumentList '-NoExit','-Command','.\mvnw.cmd -pl config-server -am spring-boot:run'
Start-Process powershell -ArgumentList '-NoExit','-Command','.\mvnw.cmd -pl customer-service -am spring-boot:run'
```
```bash
# bash -- backgrounds each, logs redirected to /tmp so the terminal stays free
(./mvnw -pl discovery-server -am spring-boot:run > /tmp/discovery-server.log 2>&1 &)
(./mvnw -pl config-server    -am spring-boot:run > /tmp/config-server.log    2>&1 &)
(./mvnw -pl customer-service -am spring-boot:run > /tmp/customer-service.log 2>&1 &)
# tail -f /tmp/customer-service.log   # to watch one of them
```

### Faster restart loop: package once, run jars directly
`spring-boot:run` recompiles via Maven every time; for repeated restarts during manual testing,
package once and just re-run the jar:
```powershell
.\mvnw.cmd clean package -DskipTests               # builds every module's jar
java -jar customer-service\target\customer-service-0.0.1-SNAPSHOT.jar
```
```bash
./mvnw clean package -DskipTests
java -jar customer-service/target/customer-service-0.0.1-SNAPSHOT.jar
```

### Stopping a backgrounded/jar-run service
Find what's listening on its port, then stop that process:
```powershell
Get-NetTCPConnection -LocalPort 8082 | Select-Object -ExpandProperty OwningProcess
Stop-Process -Id <pid> -Force
```
```bash
netstat -ano | grep ":8082" | grep LISTENING     # last column is the PID
taskkill //F //PID <pid>
```

---

## Common recipes

### "I want to test `customer-service` end to end (e.g. in Postman)"
```bash
docker compose up -d postgres keycloak
# wait for both to show "healthy": docker compose ps
./mvnw -pl customer-service -am spring-boot:run
```
Then see `postman/FDP-customer-service.postman_collection.json` +
`postman/FDP.postman_environment.json` (import both, select the environment, run folder 1 first).

### "I want everything currently implemented running together"
```bash
docker compose up -d                                                    # all 5 infra containers
./mvnw clean package -DskipTests                                        # build every module once
(java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar  > /tmp/discovery-server.log 2>&1 &)
(java -jar config-server/target/config-server-0.0.1-SNAPSHOT.jar       > /tmp/config-server.log    2>&1 &)
(java -jar customer-service/target/customer-service-0.0.1-SNAPSHOT.jar > /tmp/customer-service.log 2>&1 &)
```
(`api-gateway`/`restaurant-service`/`order-service`/`delivery-service`/`notification-service` can
be started the same way, but they're skeletons today — nothing to exercise on them yet.)

### "I only need the databases/broker up, no application code running"
```bash
docker compose up -d
```
That's it — no FDP service needs to be running for this.

---

## Troubleshooting

- **`customer-service` fails with `database "customer_db" does not exist`** — this happens if
  `postgres`'s data volume already existed *before* `docker/postgres/init-databases.sql` was
  updated to create `customer_db` (init scripts only run against a brand-new volume). Fix:
  `docker exec fdp-postgres psql -U fdp -d fdp -c "CREATE DATABASE customer_db;"`, or
  `docker compose down -v && docker compose up -d` for a fully fresh volume.
- **Keycloak container is `starting`, not `healthy`, for a while** — normal on first start; realm
  import can take 20-40 seconds. Wait for `docker compose ps` to show `healthy` before starting
  `customer-service` against it.
- **Testcontainers-backed tests hang or fail to connect** — Docker Desktop must be running; that's
  the one hard requirement even for tests that don't otherwise mention Docker.
- **Port already in use** — another instance of the same service (or something unrelated) is
  already bound to that port; find and stop it with the commands in
  [Stopping a backgrounded/jar-run service](#stopping-a-backgroundedjar-run-service), or override
  the container's host port via a repo-root `.env` file (see `.env.example`).

---

## Related
- `docs/RULES.md` §2 (service inventory, ports), §10 (containerization plan, Sprint 7)
- `docs/SPRINTS.md` (what's actually built vs. still planned)
- `credentials.md` (seeded Keycloak demo accounts)
- `postman/` (collection + environment for exercising `customer-service`)
