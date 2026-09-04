-- Runs once, automatically, on first container start (mounted into
-- /docker-entrypoint-initdb.d/ — see docker-compose.yml's postgres service).
-- keycloak_db is Keycloak's own internal schema (users, realms, clients, sessions) — Keycloak
-- manages it itself, FDP's own Flyway migrations never touch it (RULES.md §8, §10).
-- Add another CREATE DATABASE line here in whichever sprint stands up the next Postgres-backed
-- FDP service (RULES.md §5's "don't scaffold ahead of the sprint that needs it").
CREATE DATABASE keycloak_db;
