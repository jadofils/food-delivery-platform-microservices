# Seeded demo credentials

> **Local development and testing only.** These accounts and passwords are seeded by
> `identity-service`'s `V3__seed_demo_users.sql` Flyway migration and are intentionally published
> here — they exist to make the system usable immediately after `docker compose up`, before any
> real user has registered. **Never** reuse this pattern (a published plaintext password backing a
> seeded account) for anything beyond local dev/test. A staging or production environment must
> never run this migration, or must run an equivalent that creates its initial admin with a
> generated, one-time, out-of-band-delivered password instead.

Why this file exists at all: once endpoint security landed (RULES.md §8), every endpoint except
`/api/v1/auth/register` and `/api/v1/auth/login` requires a valid token, and the only way to
obtain an `ADMIN`-permissioned token is to already have an `ADMIN` account. One account per
baseline role (RULES.md §8) is seeded so the system is exercisable end to end without that
chicken-and-egg problem.

| Role | Email | Password |
|---|---|---|
| `ADMIN` | `admin@fdp.test` | `Admin@123` |
| `CUSTOMER` | `customer@fdp.test` | `Customer@123` |
| `RESTAURANT_OWNER` | `restaurant-owner@fdp.test` | `Owner@123` |
| `DELIVERY_AGENT` | `delivery-agent@fdp.test` | `Agent@123` |

## Getting a token

```
POST /api/v1/auth/login
Content-Type: application/json

{"email": "admin@fdp.test", "password": "Admin@123"}
```

The response's `accessToken` is a nested JWT (signed, then encrypted — RULES.md §8) valid for 15
minutes. Send it as `Authorization: Bearer <accessToken>` on every other endpoint.
