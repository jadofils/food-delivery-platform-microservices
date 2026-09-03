# Spring Cloud Config

## What it is
Spring Cloud Config provides a server (`config-server`) and client library for centralized,
externalized configuration. Client services fetch their configuration from the server at startup
instead of embedding it in their own packaged artifact.

## Why FDP uses it
- Twelve-factor rule 3 (config) is explicit and non-negotiable for FDP: no hostnames, credentials,
  queue names, or feature flags in code — config comes from Spring Cloud Config Server plus
  environment variables / Docker secrets (RULES.md §1 factor 3, §2).
- The same Docker image must be promotable across environments unchanged (factor 5, build/release/
  run); that's only possible if environment-specific values are injected at run time by
  `config-server` rather than baked into the image at build time (RULES.md §1 factor 5).
- A service must be able to point at a different Postgres instance by changing config, not code —
  this is factor 4 (backing services), and `config-server` is the mechanism (RULES.md §1 factor 4).
- Secrets specifically (DB credentials, JWT signing keys, RabbitMQ credentials) are sourced from
  Config Server's encrypted properties rather than a plaintext file in the repo (RULES.md §8).

## Where it's used

| Service/module | Role | Sprint |
|---|---|---|
| `config-server` | Hosts centralized config for every other service | Sprint 1 |
| All nine services | Pull their config from `config-server` on startup | Sprint 1 onward |

## How it's implemented in FDP
- `config-server` module: `spring-cloud-config-server` dependency, annotated with
  `@EnableConfigServer`, running on port `8888` (RULES.md §2; SPRINTS.md Sprint 1).
- Every other client service depends on `spring-cloud-starter-config` to pull its configuration
  from `config-server` at startup.
- `application-{profile}.yml` (`local`, `docker`, `staging`, `prod`) selects the environment — it
  contains structure only, never secrets; secrets come from environment variables / Docker secrets
  layered on top (RULES.md §1 factor 3).
- Each service also ships an `application-docker.yml` using Docker service hostnames (e.g.
  `jdbc:postgresql://postgres:5432/order_db`) rather than `localhost`, consumed once running under
  `docker-compose` (RULES.md §10; SPRINTS.md Sprint 7).
- Docker Compose service name: `config-server`; other services' `depends_on: condition:
  service_healthy` ensures it (and `discovery-server`) are ready before dependents start (RULES.md
  §10).

## Related
- `RULES.md §1` factor 3 (config) and factor 5 (build, release, run), `RULES.md §2` (port 8888),
  `RULES.md §8` (secrets sourced from encrypted Config Server properties)
- `SPRINTS.md` Sprint 1 (config-server stood up), Sprint 7 (per-environment `application-docker.yml`
  finalized)
- `./eureka.md`
