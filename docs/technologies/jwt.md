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

**Status today:** Both halves are live. Token *issuance* — Keycloak issues real, verified-working
JWTs (see `./keycloak.md` and `credentials.md`). Token *validation* — `customer-service` (Sprint 2)
carries `spring-boot-starter-oauth2-resource-server` and correctly validates them: a missing/invalid
token gets `401`, a valid token lacking the required permission gets `403`, and a valid token with
the right permission succeeds — all confirmed against real tokens for real seeded demo accounts.
`api-gateway` and the remaining domain services are still bare skeletons with no Resource Server
dependency yet. This file covers the token format and the validation approach, not a separately
running thing — there is no "JWT service" in this system; see `docs/services/customer-service.md`
for the first place validation actually happens.

### How to start it
There's nothing to start for JWT itself — it's a token format, not a running process. To exercise
the full issue-then-validate flow, start both halves:
```
docker compose up -d postgres keycloak
./mvnw -pl customer-service -am spring-boot:run
```
See `./keycloak.md` and `docs/services/customer-service.md` for each half's own startup details.

### How to access it
Obtain a token the same way `credentials.md` documents:
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
Look for `resource_access.fdp-api.roles` — that's the claim path
`common.security.jwt.KeycloakRoleConverter` reads and `@PreAuthorize("hasAuthority('user:read')")`
-style checks act on, both real and running in `customer-service` today. Send that same token as
`Authorization: Bearer <access_token>` to `http://localhost:8082/api/customers/me` to see it
validated live.

### Endpoints it exposes
None of FDP's own — JWT is a token format, not a service with an API surface. The endpoints that
actually issue and publish keys for these tokens belong to Keycloak; see `./keycloak.md`'s
`Endpoints it exposes` table (`POST /realms/fdp/protocol/openid-connect/token`,
`GET /realms/fdp/protocol/openid-connect/certs`) rather than duplicating it here.

### Installation & dependencies
- `customer-service/pom.xml` declares `spring-boot-starter-oauth2-resource-server` — the first
  real consumer. `api-gateway` (Sprint 4, edge validation) and the remaining domain services add
  the same dependency as they're built, per RULES.md §8 and SPRINTS.md.
- `common.security.jwt.KeycloakRoleConverter` — the `Converter<Jwt, ? extends
  AbstractAuthenticationToken>` that maps `resource_access.fdp-api.roles` into Spring Security
  `GrantedAuthority`s — is built and live in `common` (RULES.md §3's shared-vs-local table), wired
  into `customer-service`'s `SecurityConfig`. Every future validating service reuses this same
  class rather than re-implementing the claim path.
- No local tool install needed to obtain/inspect a token beyond a REST client (`curl`, Postman)
  and, optionally, a browser to use jwt.io.

### For newcomers
Get a real, working token from Keycloak (see `./keycloak.md`), then send it to a real, working
validating endpoint: `GET http://localhost:8082/api/customers/me` with
`Authorization: Bearer <access_token>`. Try it with `customer@fdp.test`'s token (succeeds), then
with no header at all (`401`), then with `admin@fdp.test`'s token against
`GET http://localhost:8082/api/customers` — a `user:read`-gated route `customer@fdp.test` gets
`403` on but `admin@fdp.test` doesn't. That's the whole validation story working end to end today,
not a preview of it. `postman/FDP-customer-service.postman_collection.json` automates exactly
this sequence.

## Related
- RULES.md §3, RULES.md §8
- SPRINTS.md Sprint 1
- `./keycloak.md`
