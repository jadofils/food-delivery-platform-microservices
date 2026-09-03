# JWT (JSON Web Tokens, via Spring Security)

## What it is
JWT is a signed-token format used to carry authenticated identity and authorization claims
between parties without a shared session store. In FDP it is issued by `identity-service` and
validated by the gateway and, defense-in-depth, by each downstream service, all via Spring
Security.

## Why FDP uses it
- Stateless authorization is a hard requirement (RULES.md §1 factor 6): no service keeps an
  in-memory HTTP session, so authenticated identity has to travel with the request itself.
  Embedding roles/permissions as JWT claims lets downstream services authorize locally
  (`@PreAuthorize`) without a network call back to `identity-service` per request (RULES.md §8).
- Centralizing issuance in one service (`identity-service`, RS256/asymmetric) means only one
  service ever holds the private signing key; every other service only ever needs the public key
  via JWKS to verify, never to issue (RULES.md §8).
- Validating at the edge (`api-gateway`) rejects unauthenticated or malformed tokens before they
  reach any domain service, while local re-validation downstream is defense-in-depth against a
  compromised or misconfigured gateway — both checks stay local and stateless, neither calls
  `identity-service` per request (RULES.md §8).
- Required by the base assignment: JWT-based authentication at the gateway level, with roles
  extended by RULES.md into a full RBAC model (`CUSTOMER`, `RESTAURANT_OWNER`, `DELIVERY_AGENT`,
  `ADMIN`) (ReadMe.md Technical Requirements; RULES.md §8).

## Where it's used

| Service | Role | Sprint introduced |
|---|---|---|
| `identity-service` | Sole JWT issuer (RS256); exposes JWKS endpoint | Sprint 1 |
| `api-gateway` | Validates signature/expiry/issuer at the edge | Sprint 4 |
| Downstream domain services (`customer-`, `restaurant-`, `order-`, `delivery-service`) | Local re-validation against cached JWKS; `@PreAuthorize` authorization on claims | Sprint 4 |

## How it's implemented in FDP
- `identity-service` uses Spring Security to handle user registration/login and issues RS256-signed
  JWTs; it owns the RBAC model (users, roles, permissions) and exposes a JWKS endpoint for public
  key distribution (RULES.md §8, SPRINTS.md Sprint 1). Baseline roles are `CUSTOMER`,
  `RESTAURANT_OWNER`, `DELIVERY_AGENT`, `ADMIN`, with fine-grained permissions mapped to roles
  inside `identity-service` — services authorize on permissions, not role names (RULES.md §8).
- `api-gateway` declares a JWT validation filter that checks signature, expiry, and issuer against
  `identity-service`'s JWKS before routing any request (RULES.md §8, SPRINTS.md Sprint 4).
- Downstream services re-validate the JWT signature locally against a cached copy of the JWKS
  (defense-in-depth) rather than trusting gateway-forwarded identity headers in production; this
  stays a local, stateless check (RULES.md §8, SPRINTS.md Sprint 4). Authorization decisions use
  `@PreAuthorize` against permission claims embedded in the token (RULES.md §8).
- Signing keys and any other JWT-related secrets are never committed; they are injected via
  environment variables / Docker secrets and sourced from Config Server's encrypted properties
  (RULES.md §8).
- Shared JWT-claims parsing utilities may live in the `common` module, since they are
  cross-cutting infrastructure rather than domain code — but `common` must never hold a JPA
  entity or service-specific logic (RULES.md §3).

## Related
- RULES.md §3, RULES.md §8
- SPRINTS.md Sprint 1, Sprint 4
- `./redis.md`
