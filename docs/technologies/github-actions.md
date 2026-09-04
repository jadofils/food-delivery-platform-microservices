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

## Getting started

**Status today:** `.github/workflows/ci.yml` is real and live — a single Sprint 0 baseline workflow
named `CI`, triggered on `pull_request` and `push` to `main`, that checks out the repo, sets up JDK
25 (Temurin), and runs `./mvnw --batch-mode --no-transfer-progress verify` against the whole
reactor. Splitting this into path-filtered, per-service workflows is Sprint 7 scope, not done yet —
every PR today rebuilds and retests every module. Branch protection (required status check, "up to
date" requirement, auto-merge enabled) is described in a comment block at the bottom of `ci.yml` as
a manual, one-time step in GitHub's repo Settings — that has not been verified as actually configured
in this session; only the workflow file's existence and content have been confirmed.

### How to start it
It isn't started manually — it triggers automatically on any `pull_request` or `push` targeting
`main`. To reproduce exactly what it runs, locally, from the repo root:
```
./mvnw --batch-mode --no-transfer-progress verify
```

### How to access it
- **Actions tab** of the GitHub repo:
  `https://github.com/jadofils/food-delivery-platform-microservices/actions` (once pushed).
- **GitHub CLI:** `gh run list` to see recent runs, `gh run view <run-id>` (or `gh run view --log`)
  for details/logs of a specific run.

### Endpoints it exposes
Not applicable — this is a CI pipeline, not a running service.

### Installation & dependencies
- No local install is required for the workflow itself to run — GitHub runs it on `ubuntu-latest`
  runners automatically.
- To reproduce it locally: a JDK 25 and the vendored Maven wrapper (`./mvnw`), both already required
  for any other Maven work in this repo.
- Optional: the `gh` CLI, for inspecting runs from a terminal instead of the Actions tab.

### For newcomers
Opening a pull request against `main` is what triggers this — there's no button to press. Until
Sprint 7 splits this into per-service, path-filtered workflows, every PR rebuilds and retests the
whole reactor, so a change to a single service's file currently still runs every service's tests too
— slower than it will eventually be, but simple and correct in the meantime. See `./jib.md` for the
image build/push step this workflow will gain in Sprint 7, and `./testcontainers.md` for the
integration-test suite that becomes part of `verify` as services add it.

## Related
- RULES.md §9, RULES.md §10, RULES.md §11, RULES.md §1 factor 5
- SPRINTS.md Sprint 0, Sprint 7, "Sequencing notes"
- `./jib.md`, `./testcontainers.md`
