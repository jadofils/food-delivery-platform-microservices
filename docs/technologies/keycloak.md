# Keycloak

## What it is
Keycloak is an open-source Identity and Access Management (IAM) server — it owns user
registration, login, credential storage, and OAuth2/OIDC token issuance, exposed via a real
JWKS endpoint for anyone to validate the tokens it issues.

## Why FDP uses it
- FDP originally hand-built its own identity service: a custom `User`/`Role`/`Permission` schema,
  registration/login endpoints, BCrypt hashing, and a bespoke nested-JWT codec (sign, then
  encrypt). That design was retired in favor of Keycloak — see `docs/decisions/` for the full
  reasoning. In short: rolling your own identity provider means maintaining crypto, credential
  storage, and login-flow code that a dedicated, battle-tested IAM tool already gets right.
- Real JWKS distribution, with no work: every validating service points
  `spring-boot-starter-oauth2-resource-server` at Keycloak's `issuer-uri` and gets automatic
  signature verification against Keycloak's own live public keys — no custom key generation, no
  key-distribution problem to solve later via Config Server (RULES.md §8).
- Fine-grained permissions without a custom table: FDP's permission strings
  (`order:create`, `restaurant:menu:write`, …) are modeled directly as Keycloak **client roles**
  on the `fdp-api` client, embedded in every access token automatically. No FDP-owned
  Role/Permission schema to keep in sync.

## Where it's used

| Component | Role | Sprint introduced |
|---|---|---|
| Keycloak (docker-compose service `keycloak`) | Identity provider for the whole platform — the `fdp` realm | Sprint 1 |
| `api-gateway`, every domain service | Validate Keycloak-issued tokens as an OAuth2 Resource Server | Sprint 1 onward |

## How it's implemented in FDP
- **Container:** `quay.io/keycloak/keycloak:26.0`, `docker-compose.yml` service `keycloak`, run
  with `start-dev --import-realm` (dev mode — see the caveats below before using this
  configuration anywhere but local dev). Host port `8180` by default (container port stays the
  Keycloak default `8080`; mapped to a different host port to avoid colliding with `api-gateway`'s
  planned `8080`).
- **Database:** its own schema, `keycloak_db`, in the shared Postgres container (created by
  `docker/postgres/init-databases.sql`). Keycloak owns this schema entirely — FDP's Flyway
  migrations never touch it (RULES.md §5, §10).
- **Realm provisioning:** `docker/keycloak/fdp-realm.json`, mounted read-only into
  `/opt/keycloak/data/import/`, imported automatically on first start (`--import-realm`). Defines:
  - Realm `fdp`.
  - Client `fdp-api` (public client, direct access grants enabled — password-grant login works
    directly against Keycloak's token endpoint, `/realms/fdp/protocol/openid-connect/token`, for
    both real clients and manual/curl testing).
  - Ten client roles on `fdp-api` matching FDP's permission strings (RULES.md §8).
  - Four demo users, one per baseline role, with real credentials — see `credentials.md`, not this
    file, for what they are.
- **Known gotcha, not yet resolved:** a token's `iss` claim is whatever hostname issued it. A
  token requested via `localhost:8180` (host machine) will not validate against an `issuer-uri`
  configured for `http://keycloak:8080/...` (in-network hostname) — Spring Security's issuer check
  is exact-match. Whichever service first wires up Resource Server validation needs to settle this
  (fixed `KC_HOSTNAME`, consistent access pattern), not rediscover it as a confusing 401.
- **Verified working end to end** (not just configured): realm import completes on a fresh
  container start, and all four demo users can obtain a token from Keycloak's token endpoint with
  the correct client-role claims and nothing else — confirmed live, including that a wrong
  password is correctly rejected.

## Getting started

**Status today:** Live and verified — realm, client, roles, and all four demo users import
correctly and issue real tokens. No FDP service validates those tokens yet: every domain service
and `api-gateway` are still bare skeletons with no `spring-boot-starter-oauth2-resource-server`
dependency, so "enforcement via native Spring Security" (§8) is Sprint 2 onward's work, not
today's reality. Today Keycloak works standalone — you can obtain a token directly from it, there
is just nothing in FDP's own code checking it yet.

### How to start it
From the repo root:
```
docker compose up -d postgres keycloak
```
Keycloak depends on Postgres being healthy first (`depends_on: condition: service_healthy` in
`docker-compose.yml`), so starting `postgres` alongside it (or letting Compose resolve the
dependency automatically via `docker compose up -d keycloak`) is required — it will not start
cleanly against a not-yet-ready database. First start imports the `fdp` realm automatically from
`docker/keycloak/fdp-realm.json`; this only happens once, on a fresh `keycloak_db` — a
`docker compose down -v` (dropping volumes) is required to re-trigger it after editing that file.

### How to access it
- **Admin console:** `http://localhost:8180` — log in with `KEYCLOAK_ADMIN_USER` /
  `KEYCLOAK_ADMIN_PASSWORD` (defaults `kcadmin` / `kcadmin`, see `.env.example`). This is
  Keycloak's own master-realm admin login, separate from the `fdp` realm's demo users.
- **Token endpoint** (what real clients and manual testing actually use):
  ```
  POST http://localhost:8180/realms/fdp/protocol/openid-connect/token
  Content-Type: application/x-www-form-urlencoded

  grant_type=password&client_id=fdp-api&username=admin@fdp.test&password=Admin@123
  ```
  See `credentials.md` for all four seeded demo accounts and their passwords.
- **JWKS endpoint** (what a Resource Server config points at, not something a person calls by
  hand): `http://localhost:8180/realms/fdp/protocol/openid-connect/certs`.
- **Health:** `http://localhost:8180/health/ready` on the internal management port `9000` inside
  the container (what the Docker healthcheck actually probes) — not directly reachable from the
  host on the mapped `8180` port, since that maps to Keycloak's main port, not its management port.

### Endpoints it exposes
| Endpoint | Purpose | Status |
|---|---|---|
| `POST /realms/fdp/protocol/openid-connect/token` | Obtain an access token (password grant) | Live, verified for all 4 demo users |
| `GET /realms/fdp/protocol/openid-connect/certs` | JWKS — public keys for signature verification | Live |
| `GET /realms/fdp/.well-known/openid-configuration` | OIDC discovery document | Live (stock Keycloak behavior) |
| `http://localhost:8180` (admin console) | Realm/client/user administration UI | Live |

These are all stock Keycloak endpoints, not FDP code — nothing here is custom-built or specific to
this repo beyond the realm-import configuration.

### Installation & dependencies
- Docker image: `quay.io/keycloak/keycloak:26.0` (pinned in `docker-compose.yml`).
- The FDP-specific configuration is entirely data, not code: `docker/keycloak/fdp-realm.json`
  (realm/client/roles/users) and `docker/postgres/init-databases.sql` (creates the `keycloak_db`
  schema Keycloak owns).
- Once a service validates tokens, it adds `spring-boot-starter-oauth2-resource-server` to its own
  `pom.xml` (RULES.md §8) — not a dependency of any service today.
- No local tool install is required; everything runs in the container. A REST client (`curl`,
  Postman, HTTPie) is enough to exercise the token endpoint manually.

### For newcomers
Run the `docker compose up -d postgres keycloak` command above, wait for `docker compose ps` to
show `healthy`, then run the `curl`/token-endpoint request from `credentials.md` with any of the
four seeded accounts. Decode the returned `access_token` at jwt.io (or `echo <token> | cut -d. -f2
| base64 -d`) and look for `resource_access.fdp-api.roles` — that array is exactly what a future
`@PreAuthorize("hasAuthority('order:create')")` check in FDP code will read. This is the whole
identity story for FDP right now: Keycloak issues real tokens today; FDP code checking them is the
next piece, starting with whichever service in Sprint 2 builds its first protected endpoint. See
`./jwt.md` for how token validation will actually be wired into a service, and
`docs/decisions/0001-retire-identity-service-for-keycloak.md` for why there's no FDP-owned login
endpoint at all.

## Related
- RULES.md §5, RULES.md §8, RULES.md §10
- SPRINTS.md Sprint 1
- `./jwt.md`
- `docs/decisions/` — why `identity-service` was retired in favor of Keycloak
