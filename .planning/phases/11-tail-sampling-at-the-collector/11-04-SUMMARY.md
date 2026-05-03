---
phase: 11-tail-sampling-at-the-collector
plan: "04"
subsystem: infra
tags: [mise, tail-sampling, otelcol, tempo, verification, bash]

# Dependency graph
requires:
  - phase: 11-tail-sampling-at-the-collector
    provides: "plan 11-02 otelcol-config.yaml with composite-policy + recordpolicy feature gate in docker-compose.yml; plan 11-03 scripts/load.sh SLOW_RPS=2 feed"
provides:
  - "mise.toml [tasks.\"verify:tail-sampling\"] block — Route A two-tier runtime contract check (D-T14)"
  - "Tier 1: self-metrics (:8888/metrics) assertion that composite-policy is registered and emitting otelcol_processor_tail_sampling_count_traces_sampled_total"
  - "Tier 2: Tempo (:3200/api/search) assertion that all three sub-policy names appear as tailsampling.composite_policy span attributes"
affects: [11-05, 11-06, verify-family-pattern]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "verify:* two-tier check pattern: Collector self-metrics (Tier 1) + Tempo span attribute (Tier 2) for policy-name drift detection"
    - "6x5s retry loop (30s cold-start tolerance) mirroring verify:datasources convention"

key-files:
  created: []
  modified:
    - "mise.toml — added [tasks.\"verify:tail-sampling\"] block (~94 lines) after verify:images"

key-decisions:
  - "Route A two-tier check: Tier 1 asserts composite-policy self-metrics, Tier 2 asserts recordpolicy gate wires sub-policy names to Tempo span attributes (D-T14)"
  - "EXPECTED_OUTER='composite-policy' and EXPECTED_SUBS='keep-errors keep-slow probabilistic-fallback' are the policy-names contract literals matching otelcol-config.yaml from plan 11-02 (Shared Pattern G)"
  - "Tier 2 is one-shot (no retry) — if Tempo is unreachable the stack is broken, not cold-starting"

patterns-established:
  - "verify:tail-sampling is the third artifact in the policy-names contract (alongside otelcol-config.yaml and the upcoming dashboard JSON in plan 11-05); drift in any one of them fails this task (D-T16 belt-and-suspenders)"

requirements-completed: [TSAMP-01, TSAMP-02]

# Metrics
duration: 8min
completed: 2026-05-03
---

# Phase 11 Plan 04: verify:tail-sampling — Route A Two-Tier Check Summary

**mise.toml gains [tasks."verify:tail-sampling"] that catches policy-name drift via Collector self-metrics (Tier 1) and Tempo span attributes via the alpha recordpolicy feature gate (Tier 2), with 6x5s retry tolerance and actionable error diagnostics**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-03T05:30:00Z
- **Completed:** 2026-05-03T05:38:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Added `[tasks."verify:tail-sampling"]` block (~94 lines) to `mise.toml` immediately after the existing `verify:images` block (current line 450+)
- Tier 1: curls Collector `:8888/metrics` and asserts `otelcol_processor_tail_sampling_count_traces_sampled_total` series exists with `policy="composite-policy"` label — proves processor loaded and making decisions
- Tier 2: curls Tempo `:3200/api/search?tags=tailsampling.composite_policy` and asserts all three sub-policy names (`keep-errors`, `keep-slow`, `probabilistic-fallback`) appear in the response — proves the alpha `recordpolicy` feature gate is wired AND the YAML names match what's running
- 6x5s retry loop in Tier 1 mirrors `verify:datasources` convention (30s cold-start tolerance for Collector decision_wait buffering)
- All 18 acceptance criteria verified green; `mise tasks ls` parses the task successfully

## Task Commits

Each task was committed atomically:

1. **Task 1: Add [tasks."verify:tail-sampling"] block (Route A two-tier) to mise.toml** - `7bdbc8e` (feat)

**Plan metadata:** (to be added after state updates)

## Files Created/Modified

- `mise.toml` — added `[tasks."verify:tail-sampling"]` block after `verify:images` with ASCII-bar header naming Phase 11 + D-T14, two-tier check, and all error diagnostics

## Decisions Made

- Route A (composite envelope) was already confirmed in 11-DISCUSSION-LOG.md lines 235-256 — executed verbatim
- TOML escaping: used `\\{` and `\\"` inside TOML triple-quoted strings for the grep regex patterns (TOML parses `\\` as `\` and `\"` as `"`, giving bash the correct `\{` and `\"` sequences)
- Tier 2 is one-shot without a retry loop: Tempo unreachability is a hard failure (stack broken) not a cold-start race

## Deviations from Plan

None — plan executed exactly as written. The TOML block was inserted verbatim from RESEARCH §3.4 Route A (lines 617-711) at the specified insertion site.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required. The verify task requires the full stack (`mise run infra:up` + `mise run dev` + `mise run load`) to be running for at least 60 seconds before the end-to-end smoke produces GREEN.

## Next Phase Readiness

- `verify:tail-sampling` is the runtime contract gate that backs the dashboard panel queries added in plan 11-05
- The policy-names contract (EXPECTED_OUTER + EXPECTED_SUBS literals) is now the third artifact in the three-way contract: `otelcol-config.yaml` (plan 11-02) → `mise.toml verify:tail-sampling` (this plan) → `ose-otel-demo.json` dashboard row (plan 11-05)
- Plan 11-05 can proceed immediately

## Self-Check: PASSED

- `mise.toml` modified: FOUND (confirmed by `git diff HEAD~1`)
- Commit `7bdbc8e` exists: confirmed by `git rev-parse --short HEAD`
- All 18 acceptance criteria: PASS (verified above)
- `mise tasks ls | grep verify:tail-sampling`: PASS (task listed with correct description)
- Ordering check (verify:images before verify:tail-sampling): PASS

---
*Phase: 11-tail-sampling-at-the-collector*
*Completed: 2026-05-03*
