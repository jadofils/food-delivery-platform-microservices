# Jib

## What it is
Jib is a Maven plugin (`jib-maven-plugin`) that builds a container image directly from a Java
project's compiled output and pushes it to a registry, without requiring a Docker daemon or a
`Dockerfile`.

## Why FDP uses it
- Jib is the mechanism CI actually uses to produce and push each service's image — it needs no
  Docker daemon and no `Dockerfile`, builds reproducible layers straight from the Maven build, and
  is faster in CI than a `docker build` step (RULES.md §10).
- This is the production build path: on merge to `main`, CI runs `mvn jib:build` to build and push
  a Docker image tagged with the git SHA (and a semver tag for releases) directly to the registry —
  the same image is promoted to staging/prod unchanged, never rebuilt per environment (RULES.md
  §11, RULES.md §1 factor 5).
- Locally, `docker-compose.yml` references images built by Jib (`jib:dockerBuild` for a local-only
  image, or the registry tag CI already pushed) — it does not invoke the learning-purpose
  Dockerfiles as part of the standard `docker compose up` flow (RULES.md §10).

## Where it's used

| Service/module | Sprint introduced |
|---|---|
| Every service in the §2 inventory — each configures `jib-maven-plugin` in its own `pom.xml` | Per-service, finalized as part of pipeline hardening in Sprint 7 |

Jib itself is a per-service `pom.xml` concern from whenever that service is first built; the
merge-to-`main` push behavior (`mvn jib:build` in CI, SHA-tagged image) is explicitly finalized in
Sprint 7 alongside the rest of CI/CD hardening (SPRINTS.md Sprint 7).

## How it's implemented in FDP
- `jib-maven-plugin` is configured in each service's own `pom.xml` (RULES.md §10) — not in the
  root aggregator, consistent with the rule that each service's POM should read as an accurate list
  of what that service actually needs (RULES.md §4).
- CI invokes `mvn jib:build` on merge to `main` to build and push the image, tagged with the git
  SHA (and a semver tag for releases), directly to the registry — no local Docker daemon required
  in the runner (RULES.md §10, §11).
- Locally, a developer runs `jib:dockerBuild` to produce a local-only image for
  `docker-compose.yml` to reference, as an alternative to pulling the registry tag CI already
  pushed (RULES.md §10).
- Jib is deliberately kept distinct from the hand-written multi-stage `Dockerfile` each service also
  maintains (RULES.md §10) — the two are not redundant: Jib is the CI/production path, the
  Dockerfile is for learning and manual builds, and the two must be kept in sync when a service's
  runtime dependencies change.

## Getting started

**Status today:** `jib-maven-plugin` is not configured in any module's `pom.xml` yet — confirmed by
grepping every service's `pom.xml` for "jib". There is nothing to build or push with Jib today; it
is planned to be configured per-service as each service is built, and finalized in Sprint 7 for the
CI push step (RULES.md §10; SPRINTS.md Sprint 7).

### How to start it
Not usable yet — no service's `pom.xml` has the plugin. Once configured on a given service, this doc's
`Why FDP uses it` section describes two commands:
```
./mvnw -pl <service> -am jib:dockerBuild
```
Builds a local-only image; requires a running Docker daemon (this is the one Jib mode that needs
one).
```
./mvnw -pl <service> -am jib:build
```
Builds the image and pushes it straight to a registry; needs registry credentials, but **no** local
Docker daemon — which is Jib's main selling point per RULES.md §10, and the command CI itself will
run on merge to `main` (RULES.md §11).

### How to access it
Not applicable yet — no image exists to pull or run. Once Jib is configured, a `jib:dockerBuild`
image is accessible the same way any local Docker image is (`docker images`, `docker run`); a
`jib:build` image is accessible via whichever registry it was pushed to.

### Endpoints it exposes
Not applicable — Jib is build tooling, not a running service.

### Installation & dependencies
- Maven plugin: `jib-maven-plugin`, to be added to each service's own `pom.xml` when that service is
  built (RULES.md §10) — never to the root aggregator, and never version-pinned per-service
  (RULES.md §4).
- No local install beyond the Maven wrapper (`./mvnw`); a Docker daemon is only needed for the
  `jib:dockerBuild` local-image path, not for `jib:build`.

### For newcomers
The one thing worth knowing before this plugin appears on any service: `jib:build` (the CI/push
path) needs no Docker daemon at all — that's the whole reason RULES.md §10 picks Jib over a plain
`docker build` for CI. `jib:dockerBuild` (the local-image variant a developer runs to feed
`docker-compose.yml`) does need one running locally. Both are still purely theoretical today, since
no service's `pom.xml` has the plugin — see `./docker.md` for the parallel, hand-written Dockerfile
path that exists today only for learning/manual builds.

## Related
- RULES.md §10, RULES.md §11, RULES.md §4
- SPRINTS.md Sprint 7 (per-service pipelines finalized, merge-to-`main` image build/push)
- `./docker.md`, `./github-actions.md`
