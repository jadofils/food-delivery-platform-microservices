# ADR 0002: Retire `config-server`

**Status:** Accepted

## Context

Sprint 1 stood up `config-server` (Spring Cloud Config Server, native/filesystem-backed profile,
`config-repo/` bundled into the service's own jar) as one half of the platform's spine, alongside
`discovery-server` — the intent, per `docs/RULES.md` §1 factor 3 and `docs/technologies/
spring-cloud-config.md`, was for every other service to pull its externalized configuration
(`application-{profile}.yml`) from it at startup via `spring-cloud-starter-config`, rather than
each service embedding its own config in its packaged artifact.

That consumer side never landed. `config-server` itself worked and was verified live — `GET
/application/default` and `GET /application/docker` both correctly served layered properties — but
no service, across five domain services, `api-gateway`, or `discovery-server`, ever added
`spring-cloud-starter-config` or `spring.config.import=configserver:...`. Confirmed by grepping
every service's `application.properties` for `spring.config.import`/`spring.cloud.config`: the only
match was `config-server`'s own file. Every service has always configured itself entirely from its
own local `application.properties`, `config-server` running alongside but never consulted.

This is the same shape of gap Sprint 2 onward repeatedly flagged and never closed ("pulling shared
config from `config-server` is a deliberate scope cut for this pass, not yet wired" — repeated,
service after service, through Sprint 5), and it never blocked anything: every sprint's exit
criteria were met without it.

## Decision

Retire `config-server` outright — removed, not kept running unused.

- The `config-server` module is deleted from the repository; its Maven module entry is removed
  from the root aggregator `pom.xml`.
- `docs/services/config-server.md` and `docs/technologies/spring-cloud-config.md` are deleted, not
  archived in place — same precedent as ADR 0001's `identity-service` removal.
- Port `8888` is retired, not reassigned (same treatment as port `8081` after `identity-service`).
- `docs/RULES.md` §1 (factors 3 and 4) and §2 (service inventory) are updated to describe the
  actual mechanism every service already uses: its own `application-{profile}.properties` plus
  environment variables / Docker secrets for anything sensitive — no dedicated config-serving
  service in the picture at all.
- `docs/SPRINTS.md`'s historical record of Sprint 1 (which built `config-server`) is left intact,
  with a superseded-scope note added pointing here — the same pattern already used for
  `identity-service`'s own retirement in that same sprint's text.

## Consequences

- **Less to run, less to explain.** One fewer JVM process to start before the rest of the system
  comes up, one fewer row in every "how do I run this" doc, one fewer port to potentially conflict
  on a developer's machine.
- **No functional change.** Nothing actually depended on `config-server` at any point in its
  lifetime — every service's real configuration source (its own `application.properties`) is
  unchanged by this removal.
- **The Twelve-Factor "Config" story still holds**, just without a dedicated config-serving
  component: structure lives in each service's own `application-{profile}.properties` (never
  secrets), and secrets are injected via environment variables / Docker secrets — the same
  factor-3 requirement `docs/RULES.md` always stated, satisfied by a simpler mechanism than
  originally planned.
- **If centralized config is ever genuinely needed** (e.g. a value that must change identically
  across all eight services without redeploying each one), reintroducing it is a fresh decision,
  not a revival of this exact module — this ADR is the record of why it was removed, not a
  standing prohibition on the general idea.
- The module's history remains in git (it existed from Sprint 1 through this removal); this ADR is
  the record of why it's gone, same as ADR 0001.

## Related
- ADR 0001 (`identity-service` retirement) — same precedent for outright removal over archiving
- `docs/RULES.md` §1 (factors 3, 4), §2 (service inventory, port retirement)
- `docs/SPRINTS.md` Sprint 1 (where `config-server` was originally built)
