# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

FDP (Food Delivery Platform) is a planned **microservice system**, not the single Spring Boot
app this repo started as. At this stage the repository contains only planning documents — no
service modules, Dockerfiles, or `docker-compose.yml` exist yet. Do not scaffold services,
containers, or CI pipelines unless the current sprint (see below) calls for it.

Before doing any work here, read, in order:

1. `docs/ReadMe.md` — the base assignment: decompose a four-domain monolith (Customer,
   Restaurant, Order, Delivery) into independently deployable microservices.
2. `docs/RULES.md` — the binding engineering rules for this system: service inventory, repo/module
   structure, dependency management, data ownership, sync/async communication rules, resilience,
   security, testing, containerization, CI/CD branching + auto-merge policy, caching, and
   observability. This is the source of truth for *how* things get built — don't duplicate or
   restate it here.
3. `docs/SPRINTS.md` — the sprint-by-sprint delivery plan. Always check which sprint is current
   before starting work, and don't pull forward scope from a later sprint.

The original single-app scaffold notes (Java 25, `food_delivery.Platform` package, dual
webflux/webmvc starters) described `pom.xml` as it existed before the microservices decision and
no longer reflect the target architecture in `docs/RULES.md` — that root POM will become the
multi-module aggregator described there.

## Working rules specific to this repo

- Branch naming and the CI auto-merge gate (Testcontainers-backed) are defined in
  `docs/RULES.md` §11 — follow them for any change, including documentation-only changes.
- Dependency versions belong in the root POM only; a service's own `pom.xml` never pins a version
  (`docs/RULES.md` §4).
- Never let two services share a database, a table, or a JPA entity (`docs/RULES.md` §5).
