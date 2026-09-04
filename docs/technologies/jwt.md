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

## Related
- RULES.md §3, RULES.md §8
- SPRINTS.md Sprint 1
- `./keycloak.md`
