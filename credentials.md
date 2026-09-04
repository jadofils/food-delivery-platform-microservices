# Seeded demo credentials

> **Local development and testing only.** These accounts are provisioned by Keycloak's realm
> import (`docker/keycloak/fdp-realm.json`) on first container start, and their passwords are
> intentionally published here — they exist to make the system usable immediately after
> `docker compose up`, before any real user exists. **Never** reuse this pattern (a published
> plaintext password for a seeded account, defined directly in a committed file) for anything
> beyond local dev/test. A staging or production Keycloak realm must never import users this way.

One user per baseline role (RULES.md §8), each with the client roles (permissions) that role
grants on the `fdp-api` client:

| Role | Username / email | Password |
|---|---|---|
| `ADMIN` | `admin@fdp.test` | `Admin@123` |
| `CUSTOMER` | `customer@fdp.test` | `Customer@123` |
| `RESTAURANT_OWNER` | `restaurant-owner@fdp.test` | `Owner@123` |
| `DELIVERY_AGENT` | `delivery-agent@fdp.test` | `Agent@123` |

## Getting a token

Keycloak issues tokens directly — there is no FDP-owned `/auth/login` endpoint (RULES.md §8;
`identity-service` was retired in favor of Keycloak, see `docs/decisions/`):

```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=fdp-api&username=admin@fdp.test&password=Admin@123
```

The response's `access_token` is a standard signed (RS256) OIDC access token, valid for 15
minutes, carrying the user's permissions under `resource_access.fdp-api.roles`. Send it as
`Authorization: Bearer <access_token>` to any service validating against Keycloak.

Keycloak's own admin console (separate from the `fdp` realm's users above) is reachable at
`http://localhost:8180`, logged in with `KEYCLOAK_ADMIN_USER`/`KEYCLOAK_ADMIN_PASSWORD` from
`.env.example` (defaults: `kcadmin` / `kcadmin`, also local-dev-only).
