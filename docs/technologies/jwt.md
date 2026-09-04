# JWT (JSON Web Tokens)

## What it is
JWT is a signed-token format used to carry authenticated identity and authorization claims
between parties without a shared session store. In FDP it is issued by **Keycloak** (see
`./keycloak.md`) and validated by each service via Spring Security's OAuth2 Resource Server
support — FDP owns no custom token-issuance or token-parsing code.

## Why FDP uses it
- Stateless authorization is a hard requirement (RULES.md §1 factor 6): no service keeps an
  in-memory HTTP session, so authenticated identity has to travel with the request itself.
  Keycloak embeds a user's permissions as client-role claims (`resource_access.fdp-api.roles`),
  so any service can authorize locally (`@PreAuthorize`) with no network call back to Keycloak
  per request (RULES.md §8).
- Signed (RS256), not custom-encrypted: an earlier design in this project built a bespoke
  nested-JWT codec (sign, then encrypt) inside a custom `identity-service`. That design was
  retired in favor of Keycloak (see `docs/decisions/`) — standard, signed-only OIDC tokens plus
  TLS in transit is the conventional approach, and it means every service's validation logic is
  exactly `spring-boot-starter-oauth2-resource-server`, not a maintained custom codec.
- Required by the base assignment: JWT-based authentication, with roles extended by RULES.md into
  a full RBAC model (`CUSTOMER`, `RESTAURANT_OWNER`, `DELIVERY_AGENT`, `ADMIN`), now modeled as
  Keycloak client roles rather than an FDP-owned table (ReadMe.md Technical Requirements;
  RULES.md §8).

## Where it's used

| Service | Role | Sprint introduced |
|---|---|---|
| Keycloak | Sole issuer; signs with RS256; exposes a real JWKS endpoint (`/realms/fdp/protocol/openid-connect/certs`) | Sprint 1 |
| `api-gateway` | Validates signature/expiry/issuer at the edge (Spring Security OAuth2 Resource Server) | Sprint 4 |
| Every downstream domain service | Local re-validation, same mechanism, against the same JWKS (defense-in-depth); `@PreAuthorize` authorization on client-role claims | Each service's own sprint |

## How it's implemented in FDP
- No FDP module issues or parses tokens itself. Every validating service depends on
  `spring-boot-starter-oauth2-resource-server` and sets
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` to Keycloak's realm URL — Spring Security
  auto-discovers the JWKS endpoint from there and handles signature/expiry/issuer checks with no
  custom code.
- Client-role claims (`resource_access.fdp-api.roles`) map to Spring Security
  `GrantedAuthority`s via a `Converter<Jwt, ? extends AbstractAuthenticationToken>` — a genuine
  `common`-module candidate (RULES.md §3's shared-vs-local table), since every service needs the
  exact same claim path read the exact same way.
- Signing happens entirely inside Keycloak; FDP code never touches a private key. Keycloak's own
  credentials (its Postgres connection, its bootstrap admin login) are never committed — sourced
  from environment variables / Docker secrets, same as any other secret (RULES.md §8).

## Getting started

**Status today:** Token *issuance* is fully live — Keycloak issues real, verified-working JWTs
today (see `./keycloak.md` and `credentials.md`). Token *validation* in FDP code has not started:
zero services carry `spring-boot-starter-oauth2-resource-server` (every service, `api-gateway`
included, is still a bare `spring-boot-starter` + `spring-boot-starter-test` skeleton). This file
covers the token format and the validation approach FDP will use, not a separately running thing —
there is no "JWT service" in this system.

### How to start it
There's nothing to start — JWT isn't a running process, it's a token format Keycloak issues and
(eventually) FDP services validate. The only thing runnable today is obtaining a real token, which
means starting Keycloak itself:
```
docker compose up -d postgres keycloak
```
See `./keycloak.md` for the full startup sequence.

### How to access it
Obtain a token the same way `credentials.md` documents, since that's the only "JWT" thing that's
actually live right now:
```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=fdp-api&username=admin@fdp.test&password=Admin@123
```
Then decode the returned `access_token` to inspect its claims — either paste it into jwt.io, or
from a shell:
```
echo <access_token> | cut -d. -f2 | base64 -d
```
Look for `resource_access.fdp-api.roles` — that's the claim path every future
`Converter<Jwt, ? extends AbstractAuthenticationToken>` (see below) and `@PreAuthorize` check will
read.

### Endpoints it exposes
None of FDP's own — JWT is a token format, not a service with an API surface. The endpoints that
actually issue and publish keys for these tokens belong to Keycloak; see `./keycloak.md`'s
`Endpoints it exposes` table (`POST /realms/fdp/protocol/openid-connect/token`,
`GET /realms/fdp/protocol/openid-connect/certs`) rather than duplicating it here.

### Installation & dependencies
- Not present in any service's `pom.xml` today. `spring-boot-starter-oauth2-resource-server` is
  planned for `api-gateway` first (Sprint 4, edge validation) and then each domain service that
  exposes protected endpoints (Sprint 2 onward, alongside that service's own build), per RULES.md
  §8 and SPRINTS.md.
- The `Converter<Jwt, ? extends AbstractAuthenticationToken>` that maps
  `resource_access.fdp-api.roles` into Spring Security `GrantedAuthority`s is planned as a
  `common`-module class (RULES.md §3's shared-vs-local table) — not built yet, since no service
  validates a token yet to need it.
- No local tool install needed to obtain/inspect a token today beyond a REST client (`curl`,
  Postman) and, optionally, a browser to use jwt.io.

### For newcomers
You can get a real, working token from Keycloak today — that part of the identity story is done
and verified (see `./keycloak.md`). What doesn't exist yet is anywhere in FDP to *send* that token:
no service checks the `Authorization: Bearer` header, because none has added
`spring-boot-starter-oauth2-resource-server`. That's the next milestone, starting with whichever
service in Sprint 2 builds its first protected endpoint, and continuing through `api-gateway` in
Sprint 4. Start with `./keycloak.md`'s "For newcomers" section to get a token, then come back here
once a real validating endpoint exists to test against.

## Related
- RULES.md §3, RULES.md §8
- SPRINTS.md Sprint 1
- `./keycloak.md`
