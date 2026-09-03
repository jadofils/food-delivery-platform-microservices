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

## Related
- RULES.md §10, RULES.md §11, RULES.md §4
- SPRINTS.md Sprint 7 (per-service pipelines finalized, merge-to-`main` image build/push)
- `./docker.md`, `./github-actions.md`
