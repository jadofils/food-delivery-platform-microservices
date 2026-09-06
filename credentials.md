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

**Self-service registration is enabled** (`registrationAllowed: true`) — Keycloak's own login page
now has a real "Register" link:
```
http://localhost:8180/realms/fdp/protocol/openid-connect/auth?client_id=fdp-api&response_type=code&scope=openid&redirect_uri=http://localhost:8180
```
Open that in a browser, click **Register**, fill in the form — a brand-new account is created
directly in Keycloak, no FDP code involved (verified: the resulting registration page is a real,
working Keycloak form, confirmed live).

**Known gap, not yet fixed:** a self-registered account gets **no FDP permissions by default** —
Keycloak's `default-roles-fdp` realm role (auto-assigned to every new user, self-registered or
admin-created) currently only carries stock account-management roles, none of `fdp-api`'s
permission strings. A brand-new customer can still call `POST /api/customers/me` (self-service
routes only require *authentication*, not a specific permission), but will get `403` on anything
requiring `order:create`/`restaurant:menu:read`/etc. — i.e. can't actually place an order or
browse a menu yet. To fix this, add the seeded `CUSTOMER` account's own permission set
(`order:create`, `order:cancel`, `order:read`, `restaurant:menu:read`) as composites of
`default-roles-fdp`:
- **Console:** `http://localhost:8180` (admin login: `KEYCLOAK_ADMIN_USER`/`KEYCLOAK_ADMIN_PASSWORD`,
  defaults `kcadmin`/`kcadmin`) → `fdp` realm → **Realm roles → default-roles-fdp → Associated
  roles → Assign role → Filter by clients → fdp-api** → check `order:create`, `order:cancel`,
  `order:read`, `restaurant:menu:read` → **Assign**.
- **Admin REST API** (scriptable): `POST /admin/realms/fdp/roles/default-roles-fdp/composites`
  with a JSON array of the four role objects (fetch their `id`s from
  `GET /admin/realms/fdp/clients/{fdp-api-client-uuid}/roles` first).

For creating an account without the browser flow (e.g. seeding several test users), the admin
console/REST path from before still works — obtain an admin token against the **master** realm,
then create the user against `fdp`:
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
(Verified end to end: created, logged in successfully, then deleted.)
