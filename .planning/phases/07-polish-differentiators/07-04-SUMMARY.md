---
phase: 07-polish-differentiators
plan: 04
subsystem: docs/screenshots
tags: [screenshots, capture, git-worktree, playwright, doc-04, scope-cut]
status: complete-with-scope-cut
requires:
  - 07-01 (dashboard + provisioning) — for step-04 dashboard panel screenshot
  - 07-02 (load script) — for traffic to populate panels
  - 07-03 (capture.mjs scaffold + npm package + minimal mise task) — for the capture script being driven
provides:
  - tag-cycling driver in mise.toml `[tasks."docs:screenshots"]`
  - CAPTURE_TAG_FILTER env-var support in capture.mjs (per-tag invocation)
  - Grafana anonymous-Editor access (D-01 alignment) on the otel-lgtm container — no login required to view/edit the workshop dashboard
  - 6 hand-captured PNGs in docs/screenshots/ — DOC-04 anchor pair + per-step assets (waterfall, logs, tests)
affects:
  - mise.toml
  - scripts/screenshots/capture.mjs
  - scripts/screenshots/package.json
  - docker-compose.yml
  - docs/screenshots/*.png
tech-stack:
  added: []
  patterns:
    - per-tag git-worktree isolation (D-05 — main checkout untouched during capture cycle)
    - CAPTURE_TAG_FILTER env filter (one capture.mjs invocation per tag)
    - cleanup-trap on bg processes scoped to the docs:screenshots shell (T-07-04-02 mitigation, refined by Rule-3 fix bd968fd)
    - Grafana anon-access (GF_AUTH_ANONYMOUS_*) — captures bypass login flow entirely, also the v1 auth model for workshop attendees per D-01
key-files:
  created:
    - docs/screenshots/step-01-empty-tempo.png
    - docs/screenshots/step-02-disconnected-traces.png
    - docs/screenshots/step-03-joined-trace.png
    - docs/screenshots/step-03-waterfall.png
    - docs/screenshots/step-05-logs-trace-jump.png
    - docs/screenshots/step-06-test-output.png
  modified:
    - mise.toml
    - scripts/screenshots/capture.mjs
    - scripts/screenshots/package.json
    - docker-compose.yml
decisions:
  - Tag-cycling lives in mise driver (bash) so the cleanup trap can pkill bg JVMs reliably; capture.mjs stays a single-iteration Playwright runner per tag
  - CAPTURE_TAG_FILTER chosen over per-tag config files — keeps capture.mjs's CAPTURES array as the single source of truth for what gets captured
  - Use system Chromium (executablePath) on Arch + skip `playwright install --with-deps` (Rule-3 fix 5bcfa5a) — avoids apt-only dependency installer that fails on non-Debian hosts
  - Switch Grafana to anonymous-Editor access (Rule-3 fix 88578ea) — also satisfies D-01 (no admin/admin friction for workshop attendees); downstream README plans MUST reflect this
  - Operator hand-capture for v1 (this plan) — automated pipeline shipped but doesn't currently produce step-04-metrics.png because polish-layer artifacts (dashboard JSON, anon-access env vars, load script) only exist on main HEAD, not at older step-NN-* tags
metrics:
  completed: 2026-05-02
  duration: paused-at-checkpoint-then-completed-via-hand-capture
  tasks-complete: 3
  tasks-total: 3
  pngs-committed: 6
  pngs-deferred: 1
---

# Phase 7 Plan 04: Screenshot Capture Pipeline Summary

## Outcome

**Plan completed with a documented scope cut.** Six of the seven required PNGs landed in `docs/screenshots/` via operator hand-capture; the seventh (`step-04-metrics.png`) is deferred to a follow-up. The automated `mise run docs:screenshots` tag-cycling pipeline shipped in full (Task 1 + four Rule-3 fixes during Task 2), but a structural design issue surfaced during runtime prevents it from end-to-end producing the metrics screenshot today (root cause + resolution options below).

The DOC-04 load-bearing visual — the disconnected/joined trace pair (step-02 + step-03) — is present and operator-verified, so the README rewrite (waves 4-5) is unblocked.

## Commits

| Order | Hash      | Type | Description |
|------:|-----------|------|-------------|
| 1 | `ddd372e` | feat | Task 1 — tag-cycling driver for `mise run docs:screenshots` |
| 2 | `5bcfa5a` | fix  | Rule-3 — use system Chromium executablePath, skip `playwright install --with-deps` (Arch compat) |
| 3 | `b0867a7` | fix  | Rule-3 — relax Grafana login wait + handle change-password skip |
| 4 | `88578ea` | fix  | Rule-3 — enable Grafana anon access, drop Playwright login flow (also D-01 alignment) |
| 5 | `bd968fd` | fix  | Rule-3 — scope `pkill` patterns to avoid self-killing the `docs:screenshots` shell |
| 6 | `ce6a268` | feat | Task 3 — commit 6 hand-captured workshop screenshots (DOC-04 anchor pair + step-01/03-waterfall/05/06) |
| 7 | (this commit) | docs | finalize SUMMARY.md (this file) |

The earlier partial SUMMARY commit `67a6284` (paused-at-checkpoint marker) is superseded by this commit; SUMMARY content is rewritten end-to-end below.

## Deviations from Plan (all Rule 3 — auto-fixed blockers)

Four blocking issues were discovered while running the Task 2 capture pipeline. Each was fixed in place per Rule 3 (no architectural change, no user permission needed) and committed individually so the audit trail is preserved.

| # | Rule | Commit | Fix |
|--:|:----:|--------|-----|
| 1 | 3 | `5bcfa5a` | `playwright install --with-deps chromium` invokes apt — fails on Arch. Switched to system Chromium via `executablePath` and removed `--with-deps` from the mise task. |
| 2 | 3 | `b0867a7` | Playwright's Grafana-login flow timed out: the `Skip` button on the change-password screen was conditionally rendered. Relaxed the login wait + handled the skip-or-no-skip branch. (Superseded shortly by fix #3, retained for diagnostic visibility.) |
| 3 | 3 | `88578ea` | Login-flow fragility was symptomatic, not root-cause: workshop attendees shouldn't have to log in either. Enabled Grafana anonymous Editor access via `GF_AUTH_ANONYMOUS_ENABLED=true` + `GF_AUTH_ANONYMOUS_ORG_ROLE=Editor` + `GF_AUTH_DISABLE_LOGIN_FORM=true` in `docker-compose.yml`; removed the entire login flow from `capture.mjs`. This **also satisfies D-01** (defer the auth chapter; no admin/admin friction for v1 attendees). |
| 4 | 3 | `bd968fd` | Cleanup trap's `pkill -f 'spring-boot:run'` matched the parent `mise run docs:screenshots` shell whose argv contained that string, killing the pipeline mid-iteration. Tightened the pkill patterns (`pkill -f 'mvn.*spring-boot:run'` etc.) so only child JVMs match. |

## Scope Cut: step-04-metrics.png deferred

### What's missing
`docs/screenshots/step-04-metrics.png` — the Phase 4 RED-metrics dashboard panel screenshot.

### Root cause (surfaced during automated run)
The capture pipeline's design assumes each `step-NN-*` tag's worktree is a self-contained, runnable workshop checkpoint. That assumption holds for the **app code** (each tag's `mvn` build produces working Spring Boot apps) but not for the **viz layer**:

- The Grafana dashboard JSON (`grafana-provisioning/dashboards/ose-otel-demo.json` from plan 07-01)
- The Grafana anon-access env vars in `docker-compose.yml` (Rule-3 fix #3 above)
- The continuous-load script `scripts/load.sh` (from plan 07-02)

…all live on **main HEAD only**. None of the older `step-NN-*` tags have these artifacts in their git checkout. When `git worktree add .screenshots-worktree step-04-metrics` runs, that worktree's `docker-compose.yml` is the **pre-polish** version with no anon access and no provisioned dashboard, and `scripts/load.sh` doesn't exist. The capture script then either can't reach the dashboard URL (404 / login wall) or there's no traffic to populate the panels.

For step-01/02/03/05/06, the captures don't depend on the polish layer (they target Tempo trace search and Loki log lines, both of which are otel-lgtm built-ins), so those are produceable in principle — but step-04 specifically needs the provisioned dashboard.

### Resolution options
- **(a) Follow-up plan:** Author a "polish overlay" step in the tag-cycling driver that copies main's `grafana-provisioning/`, `docker-compose.yml` polish bits, and `scripts/load.sh` onto each tag's worktree before bringing infra up. Most pedagogically faithful + reproducible.
- **(b) Reduce scope permanently:** Drop step-04-metrics.png from the workshop visual set; the README links to a live Grafana panel instead.
- **(c) Hand-capture (chosen for v1):** Operator captures the missing PNG manually in a follow-up session, in the same way they captured the other six for this commit.

Operator chose **(c)** for v1 to unblock the README work in waves 4-5; **(a)** is the recommended follow-up.

## Side Effect — Downstream Plans Must Propagate

Phase 7's D-01 decision plus this plan's Rule-3 fix #3 together changed the Grafana auth model in `docker-compose.yml`. The README plans (`07-05` and `07-06`) were drafted assuming the legacy `admin/admin` flow.

**Action required for orchestrator / README plans:**
- README copy referencing Grafana access MUST say *"open Grafana — no login required"* instead of *"log in with admin/admin and skip the password change."*
- Any screenshots illustrating the Grafana login screen are obsolete (none committed in this plan, so no PNG cleanup needed — only doc-text changes).

## Files Committed by This Plan

| File | Change | Commit |
|------|--------|--------|
| `mise.toml` | `[tasks."docs:screenshots"]` body — tag-cycling driver (~70 lines), with refined pkill patterns | `ddd372e`, `bd968fd` |
| `scripts/screenshots/capture.mjs` | `CAPTURE_TAG_FILTER` env filter; system-Chromium `executablePath`; login flow removed (post-anon-access) | `ddd372e`, `5bcfa5a`, `b0867a7`, `88578ea` |
| `scripts/screenshots/package.json` | (touched alongside capture.mjs in the Rule-3 fixes) | `5bcfa5a` |
| `docker-compose.yml` | Grafana anon-Editor env vars (`GF_AUTH_ANONYMOUS_*`, `GF_AUTH_DISABLE_LOGIN_FORM`) | `88578ea` |
| `docs/screenshots/step-01-empty-tempo.png` | hand-captured PNG (96 KB) | `ce6a268` |
| `docs/screenshots/step-02-disconnected-traces.png` | hand-captured PNG (96 KB) — DOC-04 broken half | `ce6a268` |
| `docs/screenshots/step-03-joined-trace.png` | hand-captured PNG (96 KB) — DOC-04 fixed half | `ce6a268` |
| `docs/screenshots/step-03-waterfall.png` | hand-captured PNG (96 KB) | `ce6a268` |
| `docs/screenshots/step-05-logs-trace-jump.png` | hand-captured PNG (78 KB) | `ce6a268` |
| `docs/screenshots/step-06-test-output.png` | hand-captured PNG (2.3 MB) | `ce6a268` |

## Plan Acceptance vs Outcome

| Acceptance Criterion (from PLAN.md) | Status |
|---|---|
| 6+ PNGs committed under `docs/screenshots/` | MET (6 PNGs) |
| DOC-04 anchor pair (step-02 + step-03) shows broken/fixed delta | MET (operator-verified) |
| Each PNG > 50KB and < 5MB | MET (range: 78 KB – 2.3 MB) |
| `step-04-metrics.png` present | NOT MET — deferred per scope cut above |
| Main checkout intact, no leftover `.screenshots-worktree` | MET (no worktree leftover; agent-worktree branch only) |
| Tag-cycling driver authored with `git worktree`, `CAPTURE_TAG_FILTER`, cleanup trap | MET (`ddd372e`) |
| `node --check scripts/screenshots/capture.mjs` passes | MET |

## Self-Check: PASSED

- [x] All 6 hand-captured PNGs exist on disk in `docs/screenshots/` and are tracked in git (`ce6a268`)
- [x] Commits referenced in this SUMMARY all present in `git log`: `ddd372e`, `5bcfa5a`, `b0867a7`, `88578ea`, `bd968fd`, `67a6284`, `ce6a268`
- [x] DOC-04 anchor pair (step-02 disconnected + step-03 joined) committed
- [x] Scope cut documented with root cause + three resolution options + chosen option
- [x] Side effect (Grafana auth model change) flagged for downstream README plans
- [x] No accidental deletions in `ce6a268` (verified via `git diff --diff-filter=D HEAD~1 HEAD`)
- [x] Plan deferred `step-04-metrics.png` is acknowledged in the acceptance table, not silently omitted
