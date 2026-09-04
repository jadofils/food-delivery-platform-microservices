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

## Related
- RULES.md §5, RULES.md §8, RULES.md §10
- SPRINTS.md Sprint 1
- `./jwt.md`
- `docs/decisions/` — why `identity-service` was retired in favor of Keycloak
