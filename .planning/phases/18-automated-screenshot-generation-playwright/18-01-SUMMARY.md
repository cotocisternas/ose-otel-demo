---
phase: 18-automated-screenshot-generation-playwright
plan: "01"
subsystem: scripts
tags:
  - playwright
  - screenshot-automation
  - otelcol-config
  - bash-driver-replacement
dependency_graph:
  requires:
    - scripts/load.sh
    - infra/observability/otelcol-config.yaml
    - scripts/screenshots/capture.mjs (replaced)
  provides:
    - scripts/capture-screenshots.mjs
    - scripts/package.json
  affects:
    - docs/screenshots/*.png (captured by running the script)
tech_stack:
  added:
    - playwright 1.59.1 (bumped from 1.49.1 in the analog)
  patterns:
    - ESM (.mjs) with node built-ins (fs, path, child_process)
    - backup-restore pattern for OTel Collector config mutation
    - git worktree lifecycle for per-tag app isolation
key_files:
  created:
    - scripts/capture-screenshots.mjs
    - scripts/package.json
  modified: []
decisions:
  - "D-I1: skip-by-default file-exists check; FORCE=1 recaptures all"
  - "D-S4: all logic self-contained in one script; no external bash driver"
  - "P18-3: synchronous copyFileSync in finally + process.on('exit') safety net for config restore"
  - "P18-1: --no-sandbox + headless:true always in chromium.launch args"
  - "D-W1: networkidle goto + waitForSelector per capture kind, not fixed delays"
  - "D-T1: readFileSync/writeFileSync regex replace (not sed CLI) for tail_sampling disable"
metrics:
  duration: 2min
  completed: 2026-05-03
  tasks: 2
  files: 2
---

# Phase 18 Plan 01: Unified Playwright Screenshot Capture Script Summary

**One-liner:** Single ESM capture script (516 lines) with 10-entry CAPTURES array, git worktree tag-cycling for v1.0 captures, tail-sampling OFF/ON toggle with unconditional finally restore, and per-capture `waitForSelector` strategy.

## What Was Built

### scripts/package.json
A minimal package.json placing the Playwright 1.59.1 dependency at `scripts/package.json` (co-located with the capture script). Key differences from the analog (`scripts/screenshots/package.json`):
- name: `ose-otel-demo-capture`
- `scripts.capture`: `node capture-screenshots.mjs`
- playwright: `1.59.1` (bumped from 1.49.1)
- `type: module` and `private: true` preserved

### scripts/capture-screenshots.mjs (516 lines)
A unified ESM automation script replacing `scripts/screenshots/capture.mjs` plus the 99-line bash driver block in `mise.toml`. Key capabilities:

**CAPTURES array (10 entries, D-I2):**
- 6 v1.0 tag-cycling entries (step-01 through step-06, step-05-logs, step-06-tests)
- 4 v2.0 headCapture entries: step-04-metrics, step-11-tail-sampling-OFF, step-11-tail-sampling-ON, step-12-exemplars

**Tail-sampling toggle (SCAP-02 / D-T1 / P18-3):**
- `copyFileSync(OTELCOL_CONFIG, OTELCOL_CONFIG_BAK)` before mutation
- `disableTailSampling()` regex-replaces both the processor block and pipeline entry
- `restartCollector()` + `waitForCollectorHealthy()` + 30s warm-up before OFF-frame capture
- `restoreConfigSync()` called in `finally` block (synchronous — safe on SIGINT)
- `process.on('exit', restoreConfigSync)` as second safety net
- After `withTailSamplingDisabled` returns normally: restart + warm-up for ON-frame capture
- `.bak` file deleted by `unlinkSync` after restore (T-18-02 mitigation)

**Per-capture wait strategy (SCAP-03 / D-W1):**
- `page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 })`
- `page.waitForSelector(capture.waitSelector, { state: 'visible', timeout: capture.waitTimeout })`
- 500ms settle delay before `page.screenshot()`
- `kiosk=tv` in dashboard URLs suppresses Grafana refresh picker (prevents networkidle timeout pitfall)

**Skip-by-default (D-I1):** File-exists check at start of both `captureView()` and `captureTagCycle()`; `FORCE=1` env var re-captures all.

**load.sh lifecycle (D-W2):** `startLoad()` spawns `scripts/load.sh` with `SLOW_RPS=2`; `stopLoad()` kills it; `SIGINT`/`SIGTERM` handlers call `stopLoad()` before `process.exit(1)`.

**Grafana validation:** `assertGrafanaReachable()` calls `/api/health` at startup and emits a clear error + instructions if unreachable.

**Summary table (D-W3):** `printSummary(results)` prints filename | status for all captures; exits non-zero if any required (non-optional) capture failed.

**Chromium (P18-1):** `headless: true` unconditionally; `args: ['--no-sandbox', '--disable-setuid-sandbox']` for CI/Linux compatibility.

## Verification Results

All plan verification checks passed:

| Check | Result |
|-------|--------|
| `node --check scripts/capture-screenshots.mjs` | 0 (no syntax errors) |
| `grep -c '"playwright": "1.59.1"' scripts/package.json` | 1 |
| `grep -c 'headless: true' scripts/capture-screenshots.mjs` | 1 |
| `grep -c "'--no-sandbox'" scripts/capture-screenshots.mjs` | 1 |
| `grep -c "restoreConfigSync" scripts/capture-screenshots.mjs` | 3 (function def + process.on('exit') + finally call) |
| `grep -c "waitForSelector" scripts/capture-screenshots.mjs` | 4 (dashboard, tempo, loki + captureView context) |
| Line count | 516 (well above 200 minimum) |
| CAPTURES array entries | 10 (6 v1.0 tag-cycling + 4 v2.0 headCapture) |

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Write scripts/package.json | d59ad46 | scripts/package.json |
| 2 | Write scripts/capture-screenshots.mjs | 711cb02 | scripts/capture-screenshots.mjs |

## Deviations from Plan

None - plan executed exactly as written.

The script was authored following all specifications in the plan's `<action>` blocks verbatim. No deviations required.

## Known Stubs

None. The capture script is complete and executable. It will not produce screenshots until run against a live stack (`mise run infra:up && mise run dev`), but the script itself has no stub placeholders — every function is fully implemented.

## Threat Flags

No new threat surface beyond what is documented in the plan's `<threat_model>` section:
- T-18-01 (env var injection): mitigated — GRAFANA_URL used only in fetch() URL, not execSync interpolation
- T-18-02 (bak file on disk): mitigated — `unlinkSync` in `restoreConfigSync()` deletes .bak after copy
- T-18-03 (config left disabled): mitigated — synchronous `copyFileSync` in finally + process.on('exit')

## Self-Check: PASSED

Files created:
- scripts/capture-screenshots.mjs: EXISTS (516 lines)
- scripts/package.json: EXISTS (13 lines)

Commits verified:
- d59ad46: chore(18-01): add scripts/package.json with playwright 1.59.1
- 711cb02: feat(18-01): add scripts/capture-screenshots.mjs (unified capture script)
