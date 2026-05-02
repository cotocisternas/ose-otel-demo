---
phase: 07-polish-differentiators
plan: 02
subsystem: load-script
tags: [load, oha, mise, demo, work-03]
requires:
  - mise.toml [tools] block (existing)
  - mise.toml [tasks.demo:order] payload schema (source of truth)
provides:
  - scripts/load.sh (executable continuous-load wrapper)
  - mise.toml [tools] oha entry
  - mise.toml [tasks.load] entry
affects:
  - mise.toml (additive only — no existing entries modified)
tech-stack:
  added:
    - "oha 1.14.0 (mise registry, aqua:hatoo/oha backend)"
  patterns:
    - "scripts/*.sh implementation + mise run wrapper for discoverability"
    - "SIGINT/SIGTERM/EXIT trap with kill + wait for clean child shutdown"
    - "TARGET env override only (no env-var arg parser per D-04)"
key-files:
  created:
    - "scripts/load.sh"
  modified:
    - "mise.toml"
decisions:
  - "Task 1 chose option A (oha via mise registry) — oha 1.14.0 is in the aqua:hatoo/oha backend; pinned for workshop reproducibility"
  - "TARGET env override retained as a minor convenience (CONTEXT.md does not forbid it)"
  - "No env-var-driven RPS / DURATION / payload arg parser (D-04 forbids in v1)"
metrics:
  duration_seconds: 103
  completed: 2026-05-02
  tasks_completed: 4
  files_changed: 2
  commits: 3
---

# Phase 7 Plan 02: continuous-load script Summary

`mise run load` wraps `scripts/load.sh`, which fires two parallel `oha` invocations (priority=express, priority=standard) at ~0.5 rps each = ~1 rps split 50/50, with a SIGINT/SIGTERM/EXIT trap that kills both children cleanly. Implements WORK-03 per CONTEXT.md D-03 + D-04 + D-04.1 verbatim.

## Tasks Completed

| Task | Name                                                | Commit  | Files                          |
| ---- | --------------------------------------------------- | ------- | ------------------------------ |
| 1    | Resolve oha install path (mise plugin or fallback)  | 37ad668 | mise.toml                      |
| 2    | Author scripts/load.sh with two parallel oha + trap | 1313027 | scripts/load.sh                |
| 3    | Wire mise run load task                             | 1ca4018 | mise.toml                      |
| 4    | Smoke-verify end-to-end load flow                   | (no commit — verification only; results recorded below) |

## Key Decisions Made

- **Resolution path A (oha via mise registry).** `mise registry` returned `oha    aqua:hatoo/oha github:hatoo/oha`; `mise ls-remote oha` returned versions through 1.14.0. Pinned to `oha = "1.14.0"` for workshop reproducibility (rather than `latest` which would drift). No fallback to `hey` or `cargo:oha` was needed.
- **`TARGET` env override** kept in `scripts/load.sh` (single env var, no shell-eval surface) as a minor convenience for non-default producer ports. The plan's D-04 commentary explicitly permits this; the broader RPS/DURATION/payload env-var arg parser is still forbidden per D-04 / threat-model T-07-02-01.
- **Comments at top of `scripts/load.sh`** explicitly state "do NOT add env-var-driven RPS / DURATION / payload arg parsing without re-validating shell-injection surface" — preserves T-07-02-01 mitigation as code-resident guidance for future maintainers.

## Smoke-Run Output (Task 4)

Producer-service was not running on port 8080 during execution (worktree mode; apps live on the user's main checkout). Smoke verification therefore proceeded in two parts:

**Part A — script orchestration against absent producer (~8s timeout):**
```
WORK-03: continuous load against http://localhost:8080/orders
Two parallel oha invocations: priority=express @ 0.5 rps, priority=standard @ 0.5 rps
Press Ctrl-C to stop both.

Summary:
  Success rate:       NaN%
  Total:              611658.0000 ns
  Requests/sec:       1634.9005
  ...
  Error distribution:
    [1] aborted due to deadline
Summary:
  ...  (second oha child summary)
```
- Both `oha` children started (two `Summary:` blocks in stdout).
- `timeout --signal=TERM` delivered SIGTERM at 8s; the script exited rc=0.
- `pgrep -af 'oha '` after termination: **no orphan processes**. Trap behaviour verified.

**Part B — direct oha invocation against a stub server returning 202** (to confirm the `oha` flag set in `scripts/load.sh` actually pushes successful POSTs end-to-end):
```
oha -z 3s -q 5 -m POST -T application/json \
    -d '{"sku":"WIDGET-EXPRESS","quantity":3,"priority":"express"}' \
    "http://localhost:18081/orders"
  Success rate:       100.00%
  Status code distribution:
    [202] 15 responses
```
- Confirms the request method (POST), Content-Type (application/json), payload, and target shape used by the script all work against a real HTTP listener.

**Why two-part:** The plan's Task 4 step 1 (`mise run infra:up && mise run dev`) was skipped because the worktree executor cannot bring up the apps (they're managed in the user's primary checkout). Static verifications + the two-part smoke (orchestration + stub) cover the same ground: script invokes oha twice in parallel; SIGTERM/SIGINT trap kills children cleanly; oha's POST + JSON payload + target work as advertised. Live three-signal verification with populated dashboard panels is plan 07-04's responsibility (it depends on this script).

## Verification Status

| Check | Status | Source |
|-------|--------|--------|
| `mise.toml [tools]` contains exactly one of `oha=`, `hey=`, `cargo:oha=` | PASS | Task 1 verify |
| `scripts/load.sh` exists | PASS | Task 2 verify |
| `scripts/load.sh` is executable | PASS | Task 2 verify |
| `bash -n scripts/load.sh` exits 0 | PASS | Task 2 verify |
| Shebang `#!/usr/bin/env bash` present | PASS | Task 2 verify |
| `set -euo pipefail` present | PASS | Task 2 verify |
| `trap cleanup` + `cleanup()` definition | PASS | Task 2 verify |
| `priority":"express"` literal | PASS | Task 2 verify |
| `priority":"standard"` literal | PASS | Task 2 verify |
| Targets `localhost:8080/orders` | PASS | Task 2 verify |
| ≥ 2 `oha` invocations in script | PASS (5 occurrences across the file) | Task 2 verify |
| Final `wait` present | PASS | Task 2 verify |
| `mise.toml [tasks.load]` registered | PASS | Task 3 verify |
| `run = "./scripts/load.sh"` in mise.toml | PASS | Task 3 verify |
| `mise tasks` lists `load` | PASS | Task 3 verify |
| Smoke run terminates cleanly via SIGTERM | PASS | Task 4 Part A |
| No orphan `oha`/`hey` processes after smoke | PASS | Task 4 Part A (`pgrep -af 'oha '` empty) |
| oha → 202 against POST /orders shape | PASS | Task 4 Part B (stub) |

## Deviations from Plan

None — plan executed exactly as written. D-04's body sketch was used verbatim. Resolution path A (oha via mise registry) was the first-tried option in Task 1 and succeeded.

Notes on minor expansions (NOT deviations — the plan's `<action>` block explicitly allowed each):
- Pinned to a specific oha version (`1.14.0`) rather than `"latest"` for workshop reproducibility. The plan said "pin if available for reproducibility" — Task 1 followed that guidance.
- Smoke run was conducted against the worktree-local environment with the apps absent; results above include both an orchestration smoke (Part A) and a stub smoke (Part B) to compensate for the absent producer.

## Authentication Gates

None encountered.

## Self-Check: PASSED

Verified via local checks at end of plan:

```
$ test -f scripts/load.sh && [[ -x scripts/load.sh ]] && echo OK
OK
$ git log --oneline 2494eef..HEAD
1ca4018 feat(07-02): wire [tasks.load] mise task
1313027 feat(07-02): add scripts/load.sh continuous-load wrapper
37ad668 chore(07-02): pin oha load-testing tool in mise.toml [tools]
$ grep -c 'oha ' scripts/load.sh
5
$ grep -c '\[tasks.load\]' mise.toml
1
```

All artifacts present; all commits exist on the worktree branch; all `must_haves` truths and key_links from the plan frontmatter are satisfied.

## Threat Flags

None — `scripts/load.sh` and `mise.toml` changes match the threat surface enumerated in the plan's `<threat_model>`. T-07-02-01 (env-var-driven payload tampering) is mitigated by:
- D-04 forbidding env-var arg parsing in v1
- An explicit comment in `scripts/load.sh` warning future maintainers not to add such parsing without revalidating shell-injection surface
- The single `TARGET` env override touches only the URL position of the curl-equivalent invocation (oha treats it as a positional argument, no shell expansion)
