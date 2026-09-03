# identity-service

## Responsibility
Owns user accounts, roles, and permissions (RBAC), and is the sole issuer of JWTs (RS256) for the
platform (RULES.md §2). It is designed to expose a JWKS endpoint for public-key distribution and
to embed role/permission claims directly in issued tokens so downstream services can authorize
locally (RULES.md §8).

## Why it's a separate service
This is scope beyond `ReadMe.md`'s original four-service monolith decomposition, where security
configuration with JWT authentication lived inside the monolith itself. RULES.md pulls user
identity, RBAC, and JWT issuance out into a dedicated service because token issuance and the
user/role/permission model are a distinct ownership boundary from any single domain (customers,
restaurants, orders, deliveries) — no other service should own user credentials or signing keys,
and centralizing issuance is what lets `api-gateway` and every downstream service validate tokens
locally against one shared JWKS instead of each reinventing authentication (RULES.md §8). It also
needs its own database and its own deploy/scale lifecycle, independent of the domain services that
merely consume its tokens.

## Database
Postgres, database `identity_db`. Schema owned exclusively by `identity-service` and migrated with
Flyway under `src/main/resources/db/migration` (RULES.md §5, §2). No other service connects to
`identity_db` directly.

## API surface (planned)
- User registration and login, producing an RS256-signed JWT carrying embedded role/permission
  claims (SPRINTS.md Sprint 1 exit criteria).
- Role and permission (RBAC) management: baseline roles `CUSTOMER`, `RESTAURANT_OWNER`,
  `DELIVERY_AGENT`, `ADMIN`, with fine-grained permissions mapped to roles so permission-to-role
  mapping can change without touching every downstream service (RULES.md §8).
- A JWKS endpoint for public-key distribution, consumed by `api-gateway` at the edge and by
  downstream services for local defense-in-depth re-validation (RULES.md §8).
- This is new scope relative to `ReadMe.md`, which only specified "security configuration with JWT
  authentication" as part of the monolith rather than as an independent identity service.

## Depends on / depended on by
- **Depends on:** `discovery-server` (Eureka registration), `config-server` (externalized config),
  its own `identity_db` Postgres instance. Nobody calls `identity-service` per-request to validate
  a token — validation happens locally against the cached JWKS (RULES.md §8), so it has no
  synchronous per-request dependents in the request path beyond initial token issuance.
- **Depended on by:** `api-gateway` validates every inbound token's signature, expiry, and issuer
  against `identity-service`'s JWKS before routing (RULES.md §8). Downstream domain services
  re-validate JWTs locally against the same cached JWKS as defense-in-depth, without calling back
  to `identity-service` (RULES.md §8).

## Delivered in
Sprint 1 — "Identity, discovery, config" (SPRINTS.md), alongside `discovery-server` and
`config-server`. Exit criteria: `identity-service` registers with Eureka, pulls config from
`config-server`, and a client can register, log in, and receive a valid JWT carrying
role/permission claims.

## Related
- RULES.md §2 (Service inventory), §5 (Data ownership), §8 (Security)
- SPRINTS.md — Sprint 1
- [`./api-gateway.md`](./api-gateway.md) — validates tokens at the edge against this service's
  JWKS
- [`./customer-service.md`](./customer-service.md) — a domain service that, like all others, trusts
  role/permission claims embedded by this service rather than calling it per-request
