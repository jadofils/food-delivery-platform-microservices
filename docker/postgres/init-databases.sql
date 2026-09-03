-- Runs once, automatically, on first container start (mounted into
-- /docker-entrypoint-initdb.d/ — see docker-compose.yml's postgres service).
-- Creates the database identity-service needs. Add another CREATE DATABASE line here in
-- whichever sprint stands up the next Postgres-backed service (RULES.md §5's "don't scaffold
-- ahead of the sprint that needs it").
CREATE DATABASE identity_db;
