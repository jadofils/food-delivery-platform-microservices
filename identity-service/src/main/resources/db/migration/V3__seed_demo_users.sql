-- Local development / demo accounts only, one per baseline role (RULES.md §8), so the system is
-- usable immediately after `docker compose up` without a chicken-and-egg problem: every endpoint
-- except /api/v1/auth/register and /api/v1/auth/login now requires a token, and the only way to
-- get an ADMIN-permissioned token is to already have an ADMIN account. This migration is that
-- bootstrap.
--
-- Plaintext credentials are documented in credentials.md at the repo root — NEVER add plaintext
-- passwords here, only bcrypt hashes. These are well-known, deliberately-published dev/test
-- credentials; never seed a real, undocumented admin password this way in a production migration.
--
-- Password hashes below are real bcrypt (verified to match their plaintext in credentials.md
-- before this migration was written), not placeholders.

INSERT INTO users (id, email, password_hash, enabled, account_non_locked, created_at, updated_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'admin@fdp.test',
     '$2a$10$89R637pl/v4IouOUx4746O3tK6uh2vKr.ba/lWQPIOP0RlNBWVXY2', true, true, now(), now()),
    ('22222222-2222-2222-2222-222222222222', 'customer@fdp.test',
     '$2a$10$EUsDIPLeGOWw6cruCGhb/.e9tb3by1KehWpaX.mIdnYAiTRcPM8da', true, true, now(), now()),
    ('33333333-3333-3333-3333-333333333333', 'restaurant-owner@fdp.test',
     '$2a$10$g7roQUNbukhlxAJuKo0lyO9gbLPikKsHkK5rZrWtOCldhBP4BaSDG', true, true, now(), now()),
    ('44444444-4444-4444-4444-444444444444', 'delivery-agent@fdp.test',
     '$2a$10$mPwLSLQt7pUwwwAJiS/0iO5c3PgRaO76OpqRLf889apjcmGv3U88m', true, true, now(), now());

INSERT INTO user_roles (user_id, role_id)
SELECT '11111111-1111-1111-1111-111111111111', id FROM roles WHERE name = 'ADMIN';
INSERT INTO user_roles (user_id, role_id)
SELECT '22222222-2222-2222-2222-222222222222', id FROM roles WHERE name = 'CUSTOMER';
INSERT INTO user_roles (user_id, role_id)
SELECT '33333333-3333-3333-3333-333333333333', id FROM roles WHERE name = 'RESTAURANT_OWNER';
INSERT INTO user_roles (user_id, role_id)
SELECT '44444444-4444-4444-4444-444444444444', id FROM roles WHERE name = 'DELIVERY_AGENT';
