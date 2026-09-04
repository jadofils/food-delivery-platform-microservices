# ADR 0001: Retire `identity-service` in favor of Keycloak

**Status:** Accepted

## Context

Sprint 1 originally built `identity-service`: a Spring Boot module owning `User`/`Role`/
`Permission` JPA entities, registration/login REST endpoints, BCrypt password hashing, and — once
"secure all endpoints" and "encrypt the JWT" requirements landed — a hand-built nested-JWT codec
(`common.security.jwt`: sign with RS256, then encrypt with A256GCM), a custom servlet filter, and
a custom `@Public`/`@RequiresPermission` permission-interceptor pipeline, all to avoid taking on
`spring-boot-starter-security` prematurely.

This worked, and was verified working end to end against a real Postgres and real HTTP traffic.
But it meant FDP was maintaining: credential storage and hashing, login-flow logic, a custom JWT
signing-and-encryption codec, and its own key-management story (RSA keypair + AES key, generated
in memory at startup, with no real distribution plan until Config Server existed) — all things a
dedicated Identity and Access Management product already solves, with a security team behind it,
rather than one team's custom crypto.

## Decision

Adopt Keycloak as FDP's identity provider. `identity-service` is retired outright — not kept as a
thinner facade — per the explicit choice made when this was decided.

- Keycloak owns the `fdp` realm: user registration/login, credential storage, and OIDC token
  issuance.
- FDP's permission strings (`order:create`, `restaurant:menu:write`, …) become Keycloak **client
  roles** on a single `fdp-api` client, embedded automatically in every access token under
  `resource_access.fdp-api.roles` — no FDP-owned Role/Permission schema.
- Tokens are standard, **signed-only** OIDC access tokens (RS256) — the custom encryption layer is
  not carried forward. Confidentiality comes from TLS, the conventional approach; the encryption
  layer was protecting against a threat model that doesn't hold up without TLS anyway.
- Every validating service uses stock `spring-boot-starter-oauth2-resource-server` against
  Keycloak's real JWKS endpoint, not a custom decoder.
- The custom `common.security.jwt` package (`JwtClaims`, `JwtEncoder`, `JwtDecoder`,
  `JwtAuthenticationFilter`, `Public`, `RequiresPermission`, `PermissionInterceptor`) is deleted,
  not deprecated in place.
- `identity-service`'s module, its Postgres database, and its seeded-user migrations are removed.
  Demo users move into a Keycloak realm-import file (`docker/keycloak/fdp-realm.json`) instead of
  an FDP Flyway migration.

## Consequences

- **Less code to maintain.** No custom crypto, no custom login flow, no custom permission-schema
  CRUD API — all of that was working, but all of it was also FDP's responsibility to keep secure
  and correct.
- **Real JWKS, immediately.** The in-memory-generated-keys-until-Config-Server-exists problem this
  project had explicitly flagged as a gap disappears entirely; Keycloak's JWKS endpoint is real
  from day one.
- **Adopting Spring Security becomes the obvious next step**, not a deferred one — every service
  that validates tokens now does so via `spring-boot-starter-oauth2-resource-server` and
  `@PreAuthorize`, rather than the lighter, custom filter/interceptor pair built specifically to
  avoid that dependency.
- **A new cross-container hostname caveat exists** (a token's `iss` claim is fixed to whatever
  hostname requested it) that the custom design didn't have, since FDP controlled both the issuer
  and validators directly. Documented in `docs/technologies/keycloak.md` and RULES.md §8; not yet
  resolved, since no service validates tokens yet.
- **Losing fine-grained control** some hand-built systems have — e.g. the previous design's
  15-minute access-token TTL was an FDP-code constant; it's now a Keycloak realm setting. This is
  the expected trade of adopting a platform instead of owning the logic directly.
- Two prior commits' worth of work (the custom identity-service and its JWT kernel) are removed
  from the codebase, not archived in place. Their history remains in git; this ADR is the record
  of why.
