# GitHub Actions

## What it is
GitHub Actions is the CI/CD platform running FDP's build, test, and image-publish pipelines,
configured as workflow files under `.github/workflows/`.

## Why FDP uses it
- One workflow per service, path-filtered, so a change to `order-service/**` doesn't trigger
  `restaurant-service`'s pipeline, while a change to `common/**` triggers every dependent service —
  this keeps CI cost and feedback time proportional to what actually changed (RULES.md §11).
- Required checks (compile, unit tests, Testcontainers integration tests, lint) plus a branch being
  up to date with `main` are what gate merge — the Testcontainers suite is deliberately treated as
  the trust mechanism for auto-merge, not a human reviewer (RULES.md §9, §11).
- Auto-merge is native GitHub functionality gated on branch protection required checks — no manual
  approval step blocks a merge once checks pass, and there is no force-merge bypass outside a
  documented hotfix procedure (RULES.md §11).
- On merge to `main`, CI runs `mvn jib:build` to build and push a SHA-tagged (and, for releases,
  semver-tagged) image directly to the registry — the same image is what gets promoted to
  staging/prod, never rebuilt per environment (RULES.md §11, RULES.md §1 factor 5).
- CI/CD auto-merge is live from Sprint 0 onward — it is the gate every subsequent sprint's work
  merges through, not a Sprint 7 add-on (SPRINTS.md, "Sequencing notes").

## Where it's used

| Service/module | Sprint introduced |
|---|---|
| Base workflow template (build + unit test on PR) and branch protection on `main` | Sprint 0 |
| `identity-service` CI pipeline with Testcontainers Postgres | Sprint 1 |
| `customer-service`, `restaurant-service` CI pipelines | Sprint 2 |
| `order-service` CI pipeline (adds contract tests for Feign clients) | Sprint 3 |
| `delivery-service`, `notification-service` CI pipelines | Sprint 5 |
| Per-service pipelines finalized with path filters; merge-to-`main` builds and pushes a SHA-tagged image | Sprint 7 |

## How it's implemented in FDP
- `.github/workflows/` holds one workflow file per service (RULES.md §3), each path-filtered to
  that service's own directory, plus `common/**` as an additional trigger path for every service
  that depends on it (RULES.md §11).
- Each workflow runs the required checks — compile, unit tests, Testcontainers integration tests,
  lint — as separate jobs or steps; all must be green, and the branch must be up to date with
  `main`, before GitHub's native auto-merge (configured via branch protection required checks) can
  fire (RULES.md §11).
- On merge to `main`, the workflow runs `mvn jib:build` (see `./jib.md`) to build and push the
  service's image, tagged with the git SHA and, for releases, a semver tag, directly to the
  registry — no separate build step per environment (RULES.md §10, §11).
- Branch protection on `main` requires these checks and forbids direct pushes; all work happens on
  `feature/<service-name>-<short-description>` (or `fix/`, `chore/`, `docs/`) branches scoped to one
  service or one clearly-scoped cross-cutting concern (RULES.md §11).

## Related
- RULES.md §9, RULES.md §10, RULES.md §11, RULES.md §1 factor 5
- SPRINTS.md Sprint 0, Sprint 7, "Sequencing notes"
- `./jib.md`, `./testcontainers.md`
