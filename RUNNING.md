# Running FDP locally

A practical reference for starting **infrastructure** (Postgres, MongoDB, RabbitMQ, Redis,
Keycloak — all Docker containers) and **FDP services** (discovery-server, customer-service, … —
currently run via Maven, not Docker; see [Naming & tagging](#naming--tagging)
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
| `mongodb` | Docker | `fdp-mongodb` | 27017 | Infra — live, in real use by `notification-service` |
| `rabbitmq` | Docker | `fdp-rabbitmq` | 5672 (AMQP), 15672 (mgmt UI) | Infra — live, in real use by `order-service` (publish), `delivery-service` (consume + publish), and `notification-service` (consume) |
| `redis` | Docker | `fdp-redis` | 6379 (or `REDIS_PORT` — see Troubleshooting if 6379 is already taken on your machine) | Infra — live, in real use by `restaurant-service` (caching) |
| `keycloak` | Docker | `fdp-keycloak` | 8180 | Infra — live, identity provider |
| `zipkin` | Docker | `fdp-zipkin` | 9411 | Infra — live, in real use by all five domain services (distributed tracing) |
| `discovery-server` | Maven | `discovery-server` | 8761 (fixed) | **Implemented** |
| `customer-service` | Maven | `customer-service` | dynamic — see below | **Implemented** (needs `postgres` + `keycloak`) |
| `restaurant-service` | Maven | `restaurant-service` | dynamic — see below | **Implemented** (needs `postgres`, `keycloak`, `redis`) |
| `order-service` | Maven | `order-service` | dynamic — see below | **Implemented** (needs `postgres`, `keycloak`, `rabbitmq`, `discovery-server`, `customer-service`, `restaurant-service`) |
| `delivery-service` | Maven | `delivery-service` | dynamic — see below | **Implemented** (needs `postgres`, `keycloak`, `rabbitmq`, `discovery-server`; place an order via `order-service` to generate data) |
| `notification-service` | Maven | `notification-service` | dynamic — see below | **Implemented** (needs `mongodb`, `keycloak`, `rabbitmq`, `discovery-server`; place an order via `order-service` to generate data) |
| `api-gateway` | Maven | `api-gateway` | 8080 (fixed) | **Implemented** (needs `discovery-server`, `keycloak`, `redis`; routes to whichever of the five domain services are actually running) |
| `common` | — | — | — | Shared library, not a runnable service |

**"dynamic — see below":** every domain service sets `server.port=0` (OS-assigned) — the only
stable address for reaching one is `api-gateway` on `8080` (`docs/RULES.md` §2). This is
deliberate, not a gap: it's what lets more than one instance of the same service run on this same
machine at once (see "I want to see horizontal scaling for myself" below), which a fixed port
would make impossible. If you need a domain service's *actual* current port for something the
gateway doesn't cover yet (its own Swagger UI — see that section below), query Eureka directly:
```bash
curl -s http://localhost:8761/eureka/apps/CUSTOMER-SERVICE -H "Accept: application/json" | grep -o '"port":{[^}]*}'
```
(swap the service name; Eureka app names are upper-cased). Startup logs also print the actual
bound port directly (`Tomcat started on port <N>`).

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
Start-Process powershell -ArgumentList '-NoExit','-Command','.\mvnw.cmd -pl customer-service -am spring-boot:run'
Start-Process powershell -ArgumentList '-NoExit','-Command','.\mvnw.cmd -pl restaurant-service -am spring-boot:run'
```
```bash
# bash -- backgrounds each, logs redirected to /tmp so the terminal stays free
(./mvnw -pl discovery-server  -am spring-boot:run > /tmp/discovery-server.log  2>&1 &)
(./mvnw -pl customer-service  -am spring-boot:run > /tmp/customer-service.log  2>&1 &)
(./mvnw -pl restaurant-service -am spring-boot:run > /tmp/restaurant-service.log 2>&1 &)
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
`discovery-server`/`api-gateway` are on their fixed ports; find what's listening,
then stop that process:
```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess
Stop-Process -Id <pid> -Force
```
```bash
netstat -ano | grep ":8080" | grep LISTENING     # last column is the PID
taskkill //F //PID <pid>
```
A domain service's port is dynamic — look it up via Eureka first (see the Quick reference table
above), then use the exact same commands with that port instead. Alternatively, its own startup
log line (`Tomcat started on port <N>`) already has it, if that terminal is still visible.

---

## Swagger / OpenAPI docs

Only services with real business endpoints have Swagger UI — `discovery-server` is pure
infrastructure with no `springdoc-openapi` dependency, so there's nothing to browse there beyond
what's listed below instead.

**A known gap, not an oversight:** `api-gateway` has no route for `/swagger-ui/**` or
`/v3/api-docs/**` (only `/api/**` paths are routed — see `docs/services/api-gateway.md`), and each
domain service's own port is now dynamic (see "Quick reference" above), so there is no fixed URL
to hand out for these anymore. Look the current port up via Eureka first, then browse it directly:
```bash
curl -s http://localhost:8761/eureka/apps/CUSTOMER-SERVICE -H "Accept: application/json" | grep -o '"port":{[^}]*}'
# then: http://localhost:<that port>/swagger-ui/index.html
```

| Service | Docs path (append to `http://localhost:<port>` once looked up) | Auth needed to view docs? |
|---|---|---|
| `customer-service` | `/swagger-ui/index.html`, `/v3/api-docs` | No — the docs page itself is `permitAll()`; you only need a token to click **Try it out** on an endpoint |
| `restaurant-service` | `/swagger-ui/index.html`, `/v3/api-docs` | No, same as above |
| `order-service` | `/swagger-ui/index.html`, `/v3/api-docs` | No, same as above |
| `delivery-service` | `/swagger-ui/index.html`, `/v3/api-docs` | No, same as above |
| `notification-service` | `/swagger-ui/index.html`, `/v3/api-docs` | No, same as above |
| `discovery-server` | — (no Swagger) | Eureka's own dashboard instead: http://localhost:8761 |

To call a real endpoint from Swagger UI once it's open: click **Authorize** (top right, padlock
icon), paste a raw JWT (no `Bearer ` prefix — Swagger adds that itself), **Authorize**, **Close**.
Get a token via `credentials.md`'s curl command or either Postman collection's "Get Tokens" folder.

---

## Keycloak endpoints

All stock Keycloak — no FDP code owns any of these (`identity-service` was retired for exactly
this reason, see `docs/decisions/`). Every row below verified live against the running stack.

| What | Endpoint |
|---|---|
| Login (password grant) | `POST http://localhost:8180/realms/fdp/protocol/openid-connect/token` — body `grant_type=password&client_id=fdp-api&username=<user>&password=<pass>&scope=openid` |
| Refresh | same URL — body `grant_type=refresh_token&client_id=fdp-api&refresh_token=<refresh_token>` |
| Logout | `POST http://localhost:8180/realms/fdp/protocol/openid-connect/logout` — body `client_id=fdp-api&refresh_token=<refresh_token>` |
| Who am I (`/userinfo`) | `GET http://localhost:8180/realms/fdp/protocol/openid-connect/userinfo` — header `Authorization: Bearer <access_token>`; the token must have been requested with `scope=openid` or this 403s |
| **Register** (browser) | `http://localhost:8180/realms/fdp/protocol/openid-connect/auth?client_id=fdp-api&response_type=code&scope=openid&redirect_uri=http://localhost:8180` → click **Register** |
| JWKS (public keys) | `GET http://localhost:8180/realms/fdp/protocol/openid-connect/certs` |
| OIDC discovery document | `GET http://localhost:8180/realms/fdp/.well-known/openid-configuration` |
| Admin console (Keycloak's own UI) | http://localhost:8180 — login `KEYCLOAK_ADMIN_USER`/`KEYCLOAK_ADMIN_PASSWORD` from `.env.example` (defaults `kcadmin`/`kcadmin`), then pick the **fdp** realm from the dropdown |
| Create a user (admin REST API) | `POST http://localhost:8180/admin/realms/fdp/users` with an **admin token from the master realm** — see `credentials.md` "Register a new customer" for the full recipe |

Seeded demo accounts (username/password) and the permission gap on self-registered accounts are
in `credentials.md` — not repeated here to avoid the two files drifting apart.

---

## Distributed tracing (Zipkin)

All five domain services report real spans. Verified live: placing a real order produces one trace
spanning `order-service` → `customer-service`/`restaurant-service` (Feign, the latter also hitting
Redis) → RabbitMQ → `delivery-service` (auto-creates the delivery assignment) → RabbitMQ →
`notification-service` — see `docs/technologies/zipkin.md` for the dependency/config gotchas that
made this actually work.

| What | Where |
|---|---|
| Zipkin UI | http://localhost:9411 |
| Search traces by service | `GET http://localhost:9411/api/v2/traces?serviceName=order-service` |
| Fetch one trace by ID | `GET http://localhost:9411/api/v2/trace/{traceId}` — the `traceId` is on every error response too (see below) |
| Service dependency graph (derived from recent traces) | `GET http://localhost:9411/api/v2/dependencies` |
| A client-reported failure's trace | Copy the `traceId` field off any error response and open `http://localhost:9411/zipkin/traces/{traceId}` directly — no need to ask when the failure happened |

---

## Common recipes

### "I want to test the full order flow end to end (e.g. in Postman)"
```bash
docker compose up -d                                    # postgres, mongodb, keycloak, rabbitmq (+ the rest)
./mvnw -pl discovery-server -am spring-boot:run          # terminal 1 -- order-service needs Eureka
./mvnw -pl customer-service -am spring-boot:run          # terminal 2
./mvnw -pl restaurant-service -am spring-boot:run        # terminal 3
./mvnw -pl order-service -am spring-boot:run             # terminal 4 -- calls both of the above
./mvnw -pl delivery-service -am spring-boot:run          # terminal 5 -- consumes order-service's events
./mvnw -pl notification-service -am spring-boot:run      # terminal 6 -- consumes order-/delivery-service's events
```
Then import all five collections (`FDP-customer-service`, `FDP-restaurant-service`,
`FDP-order-service`, `FDP-delivery-service`, `FDP-notification-service`) plus
`FDP.postman_environment.json`, select the environment, and run each collection's own token/setup
folders first — or just run `FDP-delivery-service`'s collection alone, its own "2. Prerequisite
Setup" folder registers everything it needs and its "3. Trigger Auto-Assignment via Order
Placement" folder places a real order through `order-service` on your behalf.

### "I want everything currently implemented running together"
```bash
docker compose up -d                                                     # all 6 infra containers
./mvnw clean package -DskipTests                                         # build every module once
(java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar   > /tmp/discovery-server.log   2>&1 &)
sleep 6
(java -jar customer-service/target/customer-service-0.0.1-SNAPSHOT.jar  > /tmp/customer-service.log    2>&1 &)
(java -jar restaurant-service/target/restaurant-service-0.0.1-SNAPSHOT.jar > /tmp/restaurant-service.log 2>&1 &)
(java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar        > /tmp/order-service.log       2>&1 &)
(java -jar delivery-service/target/delivery-service-0.0.1-SNAPSHOT.jar  > /tmp/delivery-service.log    2>&1 &)
(java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar > /tmp/notification-service.log 2>&1 &)
sleep 3
(java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar             > /tmp/api-gateway.log       2>&1 &)
```

### "I want to see the resilience/circuit-breaker story for myself"
```bash
curl -s http://localhost:8761/eureka/apps/RESTAURANT-SERVICE -H "Accept: application/json" | grep -o '"port":{[^}]*}'
netstat -ano | grep ":<that port>" | grep LISTENING     # find restaurant-service's pid
taskkill //F //PID <pid>                                # stop it
# now place an order via Postman/curl (through api-gateway, or order-service directly) -- expect a
# clean 503 in a few seconds, not a hang
./mvnw -pl restaurant-service -am spring-boot:run   # bring it back (registers under a new port)
# wait ~10s (the circuit breaker's wait-duration-in-open-state), then place another order --
# it succeeds again on its own, no restart of order-service needed
```

### "I want to see the async event publish for myself"
Place an order via Postman/curl, then open `http://localhost:15672` (login `fdp`/`fdp`) → **Queues
→ order-events.inspection → Get messages** — a real `OrderPlacedEvent` JSON payload is sitting
there.

### "I want to see the async event consume + MongoDB story for myself"
With `notification-service` and `api-gateway` also running, place (and optionally cancel) an order
via Postman/curl, then call `GET http://localhost:8080/api/notifications/me` (through the gateway
— `notification-service`'s own port is dynamic, see "Quick reference" above) with the same
customer's token — a real notification record is there within a couple of seconds, persisted in
MongoDB's `notification_db`
(`mongosh "mongodb://fdp:fdp@localhost:27017/notification_db?authSource=admin"` →
`db.notifications.find()` to see it directly). To see the idempotency guard hold up, publish the
exact same event twice from RabbitMQ's management UI (**Queues →
notification-service.order-events**, re-publish a message you already got via Get Messages) —
exactly one notification results, not two.

### "I want to see the full order-to-delivery story for myself"
Place an order via Postman/curl, then `GET {{deliveryServiceUrl}}/api/deliveries/unassigned` (as
`delivery-agent@fdp.test`) — a real `PENDING` assignment is already there, created with no
synchronous call from `order-service`. `POST /api/deliveries/{id}/claim`, then `.../pickup`, then
`.../deliver` (same token) — each call publishes a real `DeliveryStatusUpdatedEvent`. Now
`GET {{notificationServiceUrl}}/api/notifications/me` as the customer: `ORDER_PLACED`,
`DELIVERY_ASSIGNED`, `DELIVERY_PICKED_UP`, and `DELIVERY_DELIVERED` all show up as separate real
records for the same order. Cancel a *different* order before claiming its delivery, and its
assignment flips straight to `CANCELLED` and drops out of `/unassigned`.

### "I want to see order tracking (order-service + delivery-service) for myself"
Place an order via Postman/curl as the customer, then immediately `GET
{{orderServiceUrl}}/api/orders/me/{id}` with the same customer's token — `deliveryStatus` is either
`null` (delivery-service's consumer hasn't caught up yet) or already `"PENDING"`. As
`delivery-agent@fdp.test`, `claim` → `pickup` → `deliver` that same delivery (see the previous
recipe) and re-`GET` the order after each step — `deliveryStatus` moves `PENDING` → `ASSIGNED` →
`PICKED_UP` → `DELIVERED` in lockstep, with no code in `order-service` polling or subscribing to
anything; it's a live Feign call on every request. Stop `delivery-service` and `GET` the order
again — still `200`, `deliveryStatus` back to `null`, the order itself unaffected (RULES.md §7).
`delivery-service`'s own `GET /api/deliveries/by-order/{orderId}` (same customer token) is the
self-service endpoint `order-service` calls under the hood — call it directly to see the same
thing without going through `order-service` at all.

### "I want to see the distributed trace for myself"
With all four built services and `zipkin` running, place a real order via Postman/curl, then open
`http://localhost:9411`, search for service `order-service`, and open the most recent trace for
`http post /api/orders/me` — one trace, spanning `order-service`'s own HTTP handling, its Feign
calls into `customer-service`/`restaurant-service`, the RabbitMQ publish, and
`notification-service`'s consumption of that message. `GET
http://localhost:9411/api/v2/dependencies` shows the same story as a call graph instead of a
timeline. Trigger any validation error (e.g. place an order with `"items": []`) and check the
response's `traceId` field — it's a real trace ID now, directly open-able in Zipkin.

### "I want to see the distributed cache for myself"
With `restaurant-service` and `api-gateway` running, `GET http://localhost:8080/api/restaurants/{id}`
via Postman/curl twice — both return the same body, but check
`docker exec fdp-redis redis-cli -a fdp keys '*'` (add `-p <port>` if you
overrode `REDIS_PORT`) after the first call and a new key (`restaurant-service:restaurant::{id}`)
is already there before the second call even runs. `redis-cli ... ttl restaurant-service:restaurant::{id}`
shows it counting down from two minutes. `PUT /api/restaurants/me` (as the owning
`RESTAURANT_OWNER`) and check `keys '*'` again — the entry is gone, evicted immediately rather than
waiting out the TTL; the next `GET` repopulates it with the new value.

### "I want to see api-gateway routing and rate limiting for myself"
With `discovery-server`, `keycloak`, `redis`, and at least `customer-service`/`restaurant-service`/
`order-service`/`delivery-service`/`notification-service` running, call the *same* paths through
`api-gateway` on `8080` instead of each service's own (now dynamic, unadvertised) port — `GET
http://localhost:8080/api/customers/me`, `.../api/restaurants`, `.../api/orders/me`,
`.../api/deliveries/by-order/{orderId}`, `.../api/notifications/me`, same bearer token, same
bodies. With no token at all, every one of those gets a `401` immediately, before the gateway even
attempts to resolve a route. To see the rate limiter trip, fire a burst of concurrent
`POST /api/orders/me` calls through the gateway as the same customer (bash: `for i in $(seq 1 20);
do curl ... & done; wait`) — burst capacity is 10, so several of the 20 come back `429 Too Many
Requests` once it's exhausted; check `X-RateLimit-Remaining` on any single successful response to
see the bucket draining in real time.

### "I want to see horizontal scaling for myself"
Start a *second* instance of any domain service alongside the first — same jar, same command, no
config changes needed, since `server.port=0` means the second instance can't collide with the
first's port the way it would have under the old fixed-port setup:
```bash
java -jar customer-service/target/customer-service-0.0.1-SNAPSHOT.jar > /tmp/customer-service-2.log 2>&1 &
```
`GET http://localhost:8761/eureka/apps/CUSTOMER-SERVICE -H "Accept: application/json"` now shows
two `<instance>` entries under one application, each with its own port. Call
`GET http://localhost:8080/api/customers/me` through the gateway repeatedly — Spring Cloud
LoadBalancer's default round-robin strategy spreads requests across both instances (compare each
instance's own log around the same timestamp: only one of the two logs the incoming request per
call). Stop either instance and the gateway keeps working, routing every subsequent request to
whichever instance is still up, with no restart of `api-gateway` itself needed.

### "I only need the databases/broker up, no application code running"
```bash
docker compose up -d
```
That's it — no FDP service needs to be running for this.

---

## Troubleshooting

- **`api-gateway` returns `500` with `UnknownHostException: Failed to resolve '<hostname>.mshome.net'`
  for every routed request** — a Docker Desktop/WSL2-on-Windows-specific gotcha: Eureka's default
  self-registration advertises the machine's own hostname, which Reactor Netty's async DNS
  resolver (what Gateway's routing filter uses) can't resolve, even though every other service's
  Feign-to-Feign calls on the same machine work fine (they use blocking `java.net` resolution,
  which does consult the OS's own NetBIOS/hosts resolution). Every FDP service already sets
  `eureka.instance.prefer-ip-address=true` to register by IP instead — if you see this, check that
  property is actually present in whichever service's `application.properties` you're routing to.
- **A service fails with `database "customer_db"` (or `"restaurant_db"`/`"order_db"`/`"delivery_db"`) `does not exist`** — this
  happens if `postgres`'s data volume already existed *before*
  `docker/postgres/init-databases.sql` was updated to create that database (init scripts only run
  against a brand-new volume). Fix: `docker exec fdp-postgres psql -U fdp -d fdp -c "CREATE DATABASE delivery_db;"`
  (swap the name), or `docker compose down -v && docker compose up -d` for a fully fresh volume.
- **Keycloak container is `starting`, not `healthy`, for a while** — normal on first start; realm
  import can take 20-40 seconds. Wait for `docker compose ps` to show `healthy` before starting
  `customer-service`/`restaurant-service` against it.
- **Testcontainers-backed tests hang or fail to connect** — Docker Desktop must be running; that's
  the one hard requirement even for tests that don't otherwise mention Docker.
- **Port already in use** — another instance of the same service (or something unrelated) is
  already bound to that port; find and stop it with the commands in
  [Stopping a backgrounded/jar-run service](#stopping-a-backgroundedjar-run-service), or override
  the container's host port via a repo-root `.env` file (see `.env.example`). A concrete real
  example: `docker compose up -d redis` failing with `Bind for 0.0.0.0:6379 failed: port is already
  allocated` because an unrelated Redis instance already owns 6379 on the host — set `REDIS_PORT`
  in `.env` to something else, and keep `restaurant-service`'s own `spring.data.redis.port` in sync
  with it.

---

## Related
- `docs/RULES.md` §2 (service inventory, ports), §10 (containerization plan, Sprint 7)
- `docs/SPRINTS.md` (what's actually built vs. still planned)
- `credentials.md` (seeded Keycloak demo accounts)
- `postman/` (collections + shared environment for exercising `customer-service`,
  `restaurant-service`, `order-service`, `delivery-service`, and `notification-service`)
