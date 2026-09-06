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

## Auth endpoints (all Keycloak's own — there is no FDP-owned `/auth/**`)

`identity-service` was retired in favor of Keycloak (RULES.md §8; see `docs/decisions/`) — every
one of these is Keycloak's stock OIDC endpoint, not code this repo owns.

### Login (get a token)
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

### Refresh (get a new access token without re-sending the password)
```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&client_id=fdp-api&refresh_token=<refresh_token from the login response>
```

### Who am I (decode the token server-side, confirm it's still valid)
```
GET http://localhost:8180/realms/fdp/protocol/openid-connect/userinfo
Authorization: Bearer <access_token>
```
Requires the token to carry the `openid` scope (see the login note above).

### Logout (invalidate a session)
```
POST http://localhost:8180/realms/fdp/protocol/openid-connect/logout
Content-Type: application/x-www-form-urlencoded

client_id=fdp-api&refresh_token=<refresh_token from the login response>
```
Returns `204`; the refresh token (and the access token's session) is invalidated immediately —
verified: reusing it afterward correctly fails with `"Session not active"`.

### Register a new customer
**Not available today.** The `fdp` realm's self-service registration is currently disabled
(`registrationAllowed: false`) — only the four seeded accounts above exist. Until that's turned
on (a realm-config change, not something to flip casually since it's identity-provider security
config), a new account can only be created two ways:
- **Keycloak's admin console** — `http://localhost:8180`, log in with
  `KEYCLOAK_ADMIN_USER`/`KEYCLOAK_ADMIN_PASSWORD` from `.env.example` (defaults: `kcadmin` /
  `kcadmin`, local-dev-only), switch to the `fdp` realm, **Users → Add user**.
- **Keycloak's admin REST API**, scriptable (useful for seeding several test accounts without a
  browser) — obtain an admin token against the **master** realm first, then create the user
  against the `fdp` realm:
  ```
  POST http://localhost:8180/realms/master/protocol/openid-connect/token
  Content-Type: application/x-www-form-urlencoded
  grant_type=password&client_id=admin-cli&username=kcadmin&password=kcadmin

  POST http://localhost:8180/admin/realms/fdp/users
  Authorization: Bearer <master admin access_token>
  Content-Type: application/json

  {"username":"new-customer@fdp.test","email":"new-customer@fdp.test","firstName":"New",
   "lastName":"Customer","enabled":true,"emailVerified":true,
   "credentials":[{"type":"password","value":"SomePassword@123","temporary":false}]}
  ```
  Then grant it the right client roles under **Users → (the new user) → Role mapping** (or the
  equivalent admin REST call) — a plain customer needs `order:create`, `order:cancel`,
  `order:read`, `restaurant:menu:read` to match the seeded `CUSTOMER` account's own role set.
- **Turning on self-service registration** (`registrationAllowed: true` on the realm) is the
  option that gives a real "Register" link on Keycloak's own login page — ask before enabling
  this; it's a genuine identity-provider security setting, not a cosmetic toggle.
