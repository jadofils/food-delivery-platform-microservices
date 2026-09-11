# Credentials & endpoints

> **Local development and testing only.** Every credential on this page is a local-dev default
> (`.env.example`) or a seeded Keycloak demo account (`docker/keycloak/fdp-realm.json`) —
> intentionally published here so the system is usable immediately after `docker compose up`,
> before any real user or secret exists. **Never** reuse this pattern (a published plaintext
> password, defined directly in a committed file) for anything beyond local dev/test. A staging or
> production deployment gets credentials from Config Server / environment injection instead
> (`docs/RULES.md` §3 factor 3, §8), never from a file like this one.

---

## Quick reference — every credential at a glance

| System | URL | Username | Password |
|---|---|---|---|
| Keycloak admin console | `http://localhost:8180` | `kcadmin` | `kcadmin` |
| RabbitMQ management UI | `http://localhost:15672` | `fdp` | `fdp` |
| PostgreSQL | `localhost:5433` | `fdp` | `fdp` |
| MongoDB | `localhost:27017` | `fdp` | `fdp` (`authSource=admin`) |
| Redis | `localhost:6381` | — (no username) | `fdp` |
| Zipkin | `http://localhost:9411` | — | — (no auth) |

Ports above are **this machine's actual overrides** (`.env`), not `.env.example`'s defaults —
Postgres and Redis were bumped off `5432`/`6380` because unrelated containers on this machine
already held those exact host ports (see `RUNNING.md`'s Troubleshooting section for the full
story). If you're on a clean machine with nothing else competing for those ports, `.env.example`'s
defaults (`5432`, `6380`) apply instead — always check your own `.env` (gitignored) first.

---

## Service endpoints

Everything goes through `api-gateway` on a fixed address; every domain service's own port is
dynamic (`server.port=0`) and only discoverable via Eureka — see
[README.md's System architecture](README.md#system-architecture) for why.

| Route (via gateway, `http://localhost:8080`) | Backing service |
|---|---|
| `/api/customers/**` | `customer-service` |
| `/api/restaurants/**` | `restaurant-service` |
| `/api/orders/**` | `order-service` |
| `/api/deliveries/**` | `delivery-service` |
| `/api/notifications/**` | `notification-service` |

| Direct infra address (not through the gateway) | URL |
|---|---|
| Eureka dashboard | `http://localhost:8761` |
| Eureka registry API (a service's current dynamic port) | `GET http://localhost:8761/eureka/apps/<SERVICE-NAME>` (uppercase) |
| Config server | `http://localhost:8888` (implemented, not yet consumed by any client) |
| Zipkin UI | `http://localhost:9411` |
| RabbitMQ management UI | `http://localhost:15672` |

### Swagger UI / OpenAPI — per domain service, at its *current* live port

Ports are dynamic (`server.port=0`) and reassigned on every restart — no fixed URL to bookmark.
Look one up, then browse it:
```bash
curl -s http://localhost:8761/eureka/apps/CUSTOMER-SERVICE -H "Accept: application/json" \
  | grep -o '"port":{[^}]*}'
```
(swap `CUSTOMER-SERVICE` for `RESTAURANT-SERVICE`, `ORDER-SERVICE`, `DELIVERY-SERVICE`, or
`NOTIFICATION-SERVICE`), then open:

| Path | What |
|---|---|
| `http://localhost:<port>/swagger-ui/index.html` | Interactive Swagger UI — click **Authorize** (padlock) and paste a raw access token (no `Bearer ` prefix) to call endpoints from the page |
| `http://localhost:<port>/v3/api-docs` | The raw OpenAPI 3 spec as JSON — same content Swagger UI itself renders |

`discovery-server`/`config-server`/`api-gateway` have none of the above — pure infra or edge
routing only, nothing to document (see [Service inventory](README.md#service-inventory)).

**This machine's ports right now** (re-run the `curl` above once they've restarted — these change
every time):

| Service | Swagger UI |
|---|---|
| customer-service | `http://localhost:56035/swagger-ui/index.html` |
| restaurant-service | `http://localhost:55948/swagger-ui/index.html` |
| order-service | `http://localhost:56022/swagger-ui/index.html` |
| delivery-service | `http://localhost:56066/swagger-ui/index.html` |
| notification-service | `http://localhost:62944/swagger-ui/index.html` |

(Eureka shows a second live instance for customer-/order-/delivery-/notification-service too —
leftover duplicates from earlier testing, harmless, and actually a live demo of the load-balancing
`README.md`'s architecture section describes. Query the same Eureka URL above to see both ports per
service.)

---

## Keycloak

Realm: **`fdp`** · Client: **`fdp-api`** · `identity-service` was retired in favor of Keycloak
(`docs/decisions/0001-retire-identity-service-for-keycloak.md`) — there is no FDP-owned `/auth/**`,
every endpoint below is Keycloak's own stock OIDC/admin API.

### Admin console login (master realm — not an `fdp` realm user)

| | |
|---|---|
| URL | `http://localhost:8180` |
| Username | `kcadmin` (`.env` `KEYCLOAK_ADMIN_USER`) |
| Password | `kcadmin` (`.env` `KEYCLOAK_ADMIN_PASSWORD`) |

After logging in, switch the realm dropdown (top-left) from `master` to **fdp** before doing
anything FDP-related — the admin account itself lives in `master`, not `fdp`.

### Seeded demo accounts (`fdp` realm)

One user per baseline role (`docs/RULES.md` §8), each carrying exactly the `fdp-api` client roles
(permissions) that role grants — nothing more:

| Role | Username / email | Password | Permissions (`fdp-api` client roles) |
|---|---|---|---|
| `ADMIN` | `admin@fdp.test` | `Admin@123` | all ten (see the catalog below) |
| `CUSTOMER` | `customer@fdp.test` | `Customer@123` | `order:create`, `order:cancel`, `order:read`, `restaurant:menu:read` |
| `RESTAURANT_OWNER` | `restaurant-owner@fdp.test` | `Owner@123` | `restaurant:menu:write`, `restaurant:menu:read`, `order:read` |
| `DELIVERY_AGENT` | `delivery-agent@fdp.test` | `Agent@123` | `delivery:status:update`, `delivery:read`, `order:read` |

### The full permission catalog (`fdp-api` client roles)

Every permission string any endpoint in the system checks via `@PreAuthorize` or an equivalent
`SecurityFilterChain` rule — there is no permission beyond this list:

| Permission | Description |
|---|---|
| `user:read` | View user accounts |
| `user:manage` | Create, update, lock/unlock user accounts |
| `order:create` | Place an order |
| `order:cancel` | Cancel an order |
| `order:read` | View orders |
| `restaurant:menu:write` | Create and modify a restaurant's menu |
| `restaurant:menu:read` | View a restaurant's menu |
| `delivery:status:update` | Update a delivery's status |
| `delivery:read` | View delivery records |
| `notification:read` | View notification/audit records |

### Auth endpoints

#### Login (get a token)
```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=fdp-api&username=admin@fdp.test&password=Admin@123&scope=openid
```
The response's `access_token` is a standard signed (RS256) OIDC access token, valid for 15
minutes, carrying the user's permissions under `resource_access.fdp-api.roles`. Send it as
`Authorization: Bearer <access_token>` to any service validating against Keycloak. Include
`&scope=openid` if you also want `/userinfo` (below) to accept the token — omitting it still
issues a perfectly valid access token for calling FDP services, it just won't carry the `openid`
scope `/userinfo` specifically requires.

Same call as a single `curl` one-liner:
```bash
curl -s -X POST http://localhost:8180/realms/fdp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=fdp-api&username=admin@fdp.test&password=Admin@123&scope=openid"
```

#### Refresh (get a new access token without re-sending the password)
```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&client_id=fdp-api&refresh_token=<refresh_token from the login response>
```

#### Who am I (decode the token server-side, confirm it's still valid)
```
GET http://localhost:8180/realms/fdp/protocol/openid-connect/userinfo
Authorization: Bearer <access_token>
```
Requires the token to carry the `openid` scope (see the login note above).

#### Logout (invalidate a session)
```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/logout
Content-Type: application/x-www-form-urlencoded

client_id=fdp-api&refresh_token=<refresh_token from the login response>
```
Returns `204`; the refresh token (and the access token's session) is invalidated immediately —
verified: reusing it afterward correctly fails with `"Session not active"`.

#### JWKS (public signing keys) / OIDC discovery document
```
GET http://localhost:8180/realms/fdp/protocol/openid-connect/certs
GET http://localhost:8180/realms/fdp/.well-known/openid-configuration
```

#### Register a new customer (self-service, browser)
**Self-service registration is enabled** (`registrationAllowed: true`) — Keycloak's own login page
has a real "Register" link:
```
http://localhost:8180/realms/fdp/protocol/openid-connect/auth?client_id=fdp-api&response_type=code&scope=openid&redirect_uri=http://localhost:8180
```
Open that in a browser, click **Register**, fill in the form — a brand-new account is created
directly in Keycloak, no FDP code involved.

**Known gap, not yet fixed:** a self-registered account gets **no FDP permissions by default** —
Keycloak's `default-roles-fdp` realm role (auto-assigned to every new user, self-registered or
admin-created) currently only carries stock account-management roles, none of `fdp-api`'s
permission strings. A brand-new customer can still call `POST /api/customers/me` (self-service
routes only require *authentication*, not a specific permission), but will get `403` on anything
requiring `order:create`/`restaurant:menu:read`/etc. To fix this globally (every future
registration inherits it), add the seeded `CUSTOMER` account's own permission set as composites of
`default-roles-fdp`:
- **Console:** `fdp` realm → **Realm roles → default-roles-fdp → Associated roles → Assign role →
  Filter by clients → fdp-api** → check `order:create`, `order:cancel`, `order:read`,
  `restaurant:menu:read` → **Assign**.
- **Admin REST API:** `POST /admin/realms/fdp/roles/default-roles-fdp/composites` with a JSON
  array of the four role objects (fetch their `id`s the same way the role-assignment sample below
  does).

### Admin REST API — managing users and permissions from the command line

Every call below needs a **master-realm admin token** first (not an `fdp`-realm user token):
```
POST http://localhost:8180/realms/master/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=admin-cli&username=kcadmin&password=kcadmin
```
Use its `access_token` as `Authorization: Bearer <master admin token>` on every request below.
**It's short-lived** (a real `401 Unauthorized` if you sit on it too long between calls, confirmed
live) — if a request in this section suddenly 401s mid-workflow, just fetch a fresh one and
continue; nothing about your progress so far is lost.

#### Create a new user
```
POST http://localhost:8180/admin/realms/fdp/users
Authorization: Bearer <master admin access_token>
Content-Type: application/json

{"username":"new-customer@fdp.test","email":"new-customer@fdp.test","firstName":"New",
 "lastName":"Customer","enabled":true,"emailVerified":true,
 "credentials":[{"type":"password","value":"SomePassword@123","temporary":false}]}
```
Returns `201` with no body; the new user's id is in the response's `Location` header
(`.../admin/realms/fdp/users/<user-id>`). (Verified end to end: created, logged in successfully,
then deleted.)

#### Get a user (by username, or list everyone)
```
GET http://localhost:8180/admin/realms/fdp/users?username=new-customer@fdp.test&exact=true
Authorization: Bearer <master admin access_token>
```
```
GET http://localhost:8180/admin/realms/fdp/users
Authorization: Bearer <master admin access_token>
```
Either returns a JSON array; grab the matching object's `id` field for everything below.

#### See a user's current permissions
```
GET http://localhost:8180/admin/realms/fdp/users/<user-id>/role-mappings/clients/<fdp-api-client-uuid>
Authorization: Bearer <master admin access_token>
```
`<fdp-api-client-uuid>` is `fdp-api`'s own internal Keycloak id (a UUID, not the string
`fdp-api`) — fetch it once via:
```
GET http://localhost:8180/admin/realms/fdp/clients?clientId=fdp-api
Authorization: Bearer <master admin access_token>
```
(the response's single array element's `id` field).

#### Assign a permission (client role) to an existing user
Three steps — fetch the client uuid (above), fetch the exact role object, then assign it:
```
GET http://localhost:8180/admin/realms/fdp/clients/<fdp-api-client-uuid>/roles/order:create
Authorization: Bearer <master admin access_token>
```
Copy that response's `id` + `name` verbatim into the assignment call:
```
POST http://localhost:8180/admin/realms/fdp/users/<user-id>/role-mappings/clients/<fdp-api-client-uuid>
Authorization: Bearer <master admin access_token>
Content-Type: application/json

[{"id":"<role-id from the previous response>","name":"order:create"}]
```
Returns `204`. The array can carry more than one role object to assign several permissions in one
call. To **remove** a permission instead, send the identical body as a `DELETE` to the same URL.

#### List every available permission (to assign from, or double-check spelling)
```
GET http://localhost:8180/admin/realms/fdp/clients/<fdp-api-client-uuid>/roles
Authorization: Bearer <master admin access_token>
```
Returns all ten permission objects from the catalog above, each with its own `id` + `name`.

---

## RabbitMQ

| | |
|---|---|
| Management UI | `http://localhost:15672` |
| Username | `fdp` (`.env` `RABBITMQ_USER`) |
| Password | `fdp` (`.env` `RABBITMQ_PASSWORD`) |
| AMQP port (what services actually connect to) | `5672` |

Same credentials for both the UI and the raw AMQP connection (`spring.rabbitmq.username`/
`password` in every publishing/consuming service's `application.properties`). Full exchange/
queue/binding/DLQ topology and what to look at in the management UI:
[README.md's Asynchronous communication](README.md#asynchronous-communication--rabbitmq).

---

## PostgreSQL

| | |
|---|---|
| Host : port | `localhost:5433` (this machine's override — `.env.example` default is `5432`) |
| Username | `fdp` (`.env` `POSTGRES_USER`) |
| Password | `fdp` (`.env` `POSTGRES_PASSWORD`) |
| Databases | `customer_db`, `restaurant_db`, `order_db`, `delivery_db`, `keycloak_db` — one per owning service (`docs/RULES.md` §5), plus `fdp` itself |

```bash
docker exec -it fdp-postgres psql -U fdp -d customer_db   # swap the db name
```

## MongoDB

| | |
|---|---|
| Host : port | `localhost:27017` |
| Username | `fdp` (`.env` `MONGO_ROOT_USERNAME`) |
| Password | `fdp` (`.env` `MONGO_ROOT_PASSWORD`) |
| Auth database | `admin` — required even though the target database is `notification_db` (the root user is created under `admin` by `MONGO_INITDB_ROOT_*`, not under `notification_db` itself) |
| Owning database | `notification_db` (`notification-service` only) |

```bash
mongosh "mongodb://fdp:fdp@localhost:27017/notification_db?authSource=admin"
```

## Redis

| | |
|---|---|
| Host : port | `localhost:6381` (this machine's override — `.env.example` default is `6380`) |
| Username | — (Redis has no username, password-only `requirepass`) |
| Password | `fdp` (`.env` `REDIS_PASSWORD`) |
| Used by | `restaurant-service` (menu-lookup cache), `api-gateway` (rate-limit counters) — never a system of record for either |

```bash
docker exec -it fdp-redis redis-cli -a fdp
```

## Zipkin

| | |
|---|---|
| Dashboard UI | `http://localhost:9411` |
| Username / password | — none, no auth configured (local dev only) |
| Search recent traces for one service | `GET http://localhost:9411/api/v2/traces?serviceName=order-service` |
| Fetch one trace by ID | `GET http://localhost:9411/api/v2/trace/{traceId}` |
| Service dependency graph | `GET http://localhost:9411/api/v2/dependencies` |

Every domain service (`customer-`/`restaurant-`/`order-`/`delivery-`/`notification-service`)
reports real spans; `api-gateway` doesn't yet (no tracing dependency of its own — see
[Known gaps](README.md#known-gaps--roadmap)). Place one real order through the gateway, then open
the dashboard URL above and search `order-service` — one trace spans all five domain services plus
RabbitMQ plus Redis.

---

## Related
- [`README.md`](README.md) — architecture, request/event flow, RabbitMQ topology, full operational-endpoints reference
- [`RUNNING.md`](RUNNING.md) — how to start everything, troubleshooting (including the port-conflict story behind this machine's Postgres/Redis overrides above)
- `docs/RULES.md` §8 — the security model these permissions/roles come from
- `docker/keycloak/fdp-realm.json` — the actual realm import every credential/permission on this page is sourced from
