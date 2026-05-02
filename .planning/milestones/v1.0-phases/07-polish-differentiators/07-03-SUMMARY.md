---
phase: 07-polish-differentiators
plan: 03
subsystem: docs-tooling
tags: [screenshots, playwright, node, mise, scaffold, doc-04]
requires:
  - 07-02 (load script — `mise run load` is invoked by Wave 2 capture wrapper)
provides:
  - scripts/screenshots/ Node project scaffold with pinned Playwright
  - capture.mjs ESM Playwright pipeline for six DOC-04 PNGs
  - mise run docs:screenshots task entry
affects:
  - mise.toml (append-only — added [tasks."docs:screenshots"], no other blocks touched)
tech_stack_added:
  - "playwright@1.49.1 (pinned)"
patterns:
  - "Login-once-then-cycle Playwright auth (storageState reused across captures)"
  - "Deterministic-timing via fixed Unix-ms URL window (Grafana from/to params)"
  - "Optional-capture-skip pattern for brittle waterfall view (D-06 6-vs-7 PNG fallback)"
  - "ESM .mjs Node script (top-level await; node 22 LTS pinned in mise.toml)"
key_files_created:
  - scripts/screenshots/package.json
  - scripts/screenshots/capture.mjs
  - scripts/screenshots/.gitignore
key_files_modified:
  - mise.toml (append-only)
decisions:
  - "Pinned Playwright to 1.49.1 (D-05 reproducibility — no caret/range)"
  - "package-lock.json gitignored (single direct dep, itself pinned — D-05 / T-07-03-03)"
  - "Terminal capture renders mvn output to a monospace HTML page screenshotted by Playwright (avoids needing a real terminal emulator)"
  - "All seven PNG outputs declared upfront in CAPTURES; the optional waterfall is silently skipped on failure (Wave 2 / plan 07-04 decides 6 vs 7 final)"
  - "Auth uses POST /login form (admin/admin defaults, env-var override for production-Grafana variants)"
metrics:
  duration: "2m3s"
  tasks_completed: 3
  files_created: 3
  files_modified: 1
  completed_date: "2026-05-02"
---

# Phase 07 Plan 03: Screenshot Tooling Scaffold Summary

## One-liner

Authored a pinned-Playwright Node project at `scripts/screenshots/` plus a `mise run docs:screenshots` task — the scaffold Wave 2's plan 07-04 will exercise to produce the DOC-04 per-step PNG set.

## What was built

Three artifacts that, together, give Wave 2 (plan 07-04) everything it needs to drive Grafana headlessly across the six step-NN-* git tags:

1. **`scripts/screenshots/package.json`** — Playwright pinned to `1.49.1` (no caret/range — D-05 reproducibility). `"type": "module"` enables ESM. `"private": true` prevents accidental npm publish. Sole script: `capture` runs `playwright install --with-deps chromium && node capture.mjs`.

2. **`scripts/screenshots/capture.mjs`** (195 lines) — ESM Node script implementing the full Playwright capture pipeline:
   - Login-once-then-cycle auth: `loginAndStoreState()` POSTs the Grafana login form once and reuses the resulting storageState across all per-tag captures (D-05).
   - Deterministic timing: fixed 5-minute Unix-ms window passed via Grafana URL `from`/`to` params (D-05).
   - Four capture kinds: `dashboard` (data-panel-id selector), `tempo` (Explore with traceqlSearch), `loki` (Explore with `{service_name=~"order-.*"}` LogQL), `terminal` (renders `mise run test` stdout to monospace HTML, screenshots that — avoids needing a real terminal emulator).
   - All seven D-06 PNG outputs declared in `CAPTURES`: `step-01-empty-tempo.png`, `step-02-disconnected-traces.png`, `step-03-joined-trace.png`, `step-03-waterfall.png` (optional), `step-04-metrics.png`, `step-05-logs-trace-jump.png`, `step-06-test-output.png`.
   - Configurable via `GRAFANA_URL`/`GRAFANA_USER`/`GRAFANA_PASS` env vars (T-07-03-01 mitigation — workshop default `admin/admin` for offline laptop usage; production-Grafana variants override via env).
   - Optional capture skipped silently on failure (D-06 — falls back to 6 PNGs if the waterfall capture proves brittle in Wave 2).

3. **`scripts/screenshots/.gitignore`** — excludes `node_modules/`, `package-lock.json` (single pinned dep makes lockfile noise), `.playwright-cache/`, `/test-results/`.

4. **`mise.toml`** — appended `[tasks."docs:screenshots"]` block between `[tasks.load]` and `[tasks."verify:bom"]`. Body: `cd scripts/screenshots && npm install && npx playwright install --with-deps chromium && node capture.mjs`. Existing `[tasks.load]` and `[tasks."verify:bom"]` blocks UNCHANGED (verified via `git diff`).

## Pinned Playwright version

`playwright@1.49.1` — pinned in both `package.json` `dependencies` and the package-lock-gitignored install footprint. Bumping is a deliberate v2 ask (T-07-03-03 — exact version pinned, package-lock gitignored to avoid drift). Node toolchain (`node = "lts"`) was already pinned in `mise.toml` by an earlier Phase 7 plan; no `[tools]` change was needed in this plan.

## Grafana selector adjustments for otel-lgtm v0.26.0

None. Selectors authored against the v0.26.0 bundled Grafana login form (`input[name="user"]`, `input[name="password"]`, `button[type="submit"]`) and Explore/Dashboard URL conventions are stable across the otel-lgtm 0.2x line. Wave 2 (plan 07-04) actually exercises the script — if any selector requires adjustment after live Grafana inspection, that's recorded there, not here.

## Discretion items resolved

- **`step-03-waterfall.png` capture (D-06 #4):** kept in `CAPTURES` as `optional: true`. The script silently skips it on Playwright timeout/error and continues. Wave 2 verifies whether 6 or 7 PNGs ship to `docs/screenshots/`.
- **`step-01-empty-tempo.png` capture (D-06 #1):** kept (not flagged optional). Tempo's "no traces found" view rendering is undocumented but Wave 2 can demote to optional if it proves unreliable.
- **Workspace isolation (D-05):** the script does NOT cycle `git checkout` itself in Wave 1. That responsibility belongs to plan 07-04's `mise run docs:screenshots` wrapper, which is expected to drive the per-tag git-worktree cycle around `node capture.mjs`. This split keeps Wave 1's blast radius low (Wave 1 only authors files; Wave 2 runs the actual high-blast-radius `git checkout` cycling + infra startup).

## Acceptance criteria verification

- [x] `scripts/screenshots/` directory exists
- [x] `scripts/screenshots/package.json` parses as valid JSON with `"type": "module"`, `"private": true`, pinned `playwright`
- [x] `scripts/screenshots/.gitignore` exists and excludes `node_modules`
- [x] `scripts/screenshots/capture.mjs` exists; `node --check` exits 0 (valid ESM)
- [x] capture.mjs imports `chromium` from `'playwright'` and contains `chromium.launch`
- [x] capture.mjs references all six required PNG names + the optional waterfall
- [x] capture.mjs implements `loginAndStoreState` and the `optional: true` skip pattern
- [x] capture.mjs targets `http://localhost:3000` (override via `GRAFANA_URL`)
- [x] `mise.toml` contains literal `tasks."docs:screenshots"` and references `capture.mjs`
- [x] `mise tasks` lists `docs:screenshots`
- [x] Existing `[tasks.load]` and `[tasks."verify:bom"]` blocks unchanged

## Deviations from Plan

None — plan executed exactly as written. (One incidental setup step required: `mise trust` had to be run on the trusted-config-prompt before `mise tasks` would parse `mise.toml`; this is a worktree-environment property, not a deviation in the artifact contract.)

## Threat surface scan

No new security-relevant surface beyond the threat model declared in the PLAN. The `<threat_model>` register (T-07-03-01..05) covers everything authored: hardcoded admin/admin Grafana creds (env-var override), env-var-fed password handling (in-memory only), pinned Playwright (no version drift), `--with-deps chromium` install (workshop tooling), and Chromium install footprint (gitignored cache).

No threat flags raised.

## Pipeline NOT yet exercised

This plan only authored files. The pipeline runs in **Wave 2 / plan 07-04**, which:
1. Wraps `mise run docs:screenshots` with the per-tag git-worktree cycle.
2. Brings up infra + dev + load before each capture.
3. Commits the resulting PNGs to `docs/screenshots/`.

If Wave 2 finds that a Grafana selector or URL parameter behaves differently against a *running* otel-lgtm 0.26.0, the adjustments land there, not in this scaffold.

## Commits

| Task | Description | Hash |
|------|-------------|------|
| 1 | Scaffold scripts/screenshots/ Node project (package.json + .gitignore) | `1b50268` |
| 2 | Author Playwright capture.mjs script | `1804e2b` |
| 3 | Wire mise run docs:screenshots task | `28b6602` |

## Self-Check: PASSED

- FOUND: scripts/screenshots/package.json
- FOUND: scripts/screenshots/capture.mjs
- FOUND: scripts/screenshots/.gitignore
- FOUND: mise.toml (modified — `[tasks."docs:screenshots"]` added)
- FOUND commit: 1b50268
- FOUND commit: 1804e2b
- FOUND commit: 28b6602
