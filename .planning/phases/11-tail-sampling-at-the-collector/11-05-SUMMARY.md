---
phase: 11-tail-sampling-at-the-collector
plan: 05
subsystem: infra
tags: [grafana, dashboard, tail-sampling, otelcol, prometheus, timeseries]

# Dependency graph
requires:
  - phase: 11-tail-sampling-at-the-collector
    provides: "Plan 11-02 tail_sampling processor block in otelcol-config.yaml (source of otelcol_processor_tail_sampling_* metrics)"
  - phase: 10-prerequisites-stack-decomposition
    provides: "Phase 10 D-01 datasource.uid: prometheus contract and grafana/dashboards/ provisioning path"
provides:
  - "Collapsed 'Tail Sampling diagnostics (Phase 11)' row in ose-otel-demo.json with 5 panels surfacing canonical v0.151.0 otelcol_processor_tail_sampling_* series"
  - "D-T13 and D-T16 decisions satisfied: JSDoc POLICY-NAMES CONTRACT reminder and recordpolicy alpha gate dependency flagged in row description"
affects:
  - 11-tail-sampling-at-the-collector (plan 11-06 verifies Panel 4 _milliseconds suffix live)
  - Workshop attendees inspecting self-observability of the tail_sampling processor

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Collapsed Grafana row idiom with nested panels for feature-specific diagnostics appended additively below the Deeper-dive row"
    - "POLICY-NAMES CONTRACT JSDoc pattern on row description field — cross-file contract reminder surfaced to dashboard editors"
    - "Route A histogram_quantile pattern: p50/p95/p99 targets on a single timeseries panel using sum by (le)"

key-files:
  created: []
  modified:
    - grafana/dashboards/ose-otel-demo.json

key-decisions:
  - "Included bonus Panel 5 (traces in memory sanity gauge) — plan marked it optional but it provides the F2-2 buffer canary without cost"
  - "Panel IDs 10-14 assigned (not 10-13) because bonus panel id=14 is included; acceptance criteria required ids 9-13 as minimum, 14 is additive"
  - "All panels use inline datasource on both panel-level and target-level per Phase 10 D-01 contract (23 prometheus uid references total)"
  - "D-T16 JSDoc contract reminder embedded verbatim in row description field, citing the alpha recordpolicy feature gate as a Route A dependency"

patterns-established:
  - "Phase 11 diagnostic row pattern: collapsed row with 4+1 panels, row description carries cross-file contract reminder, nested panels use inline datasource"

requirements-completed: [TSAMP-02, TSAMP-03]

# Metrics
duration: 2min
completed: 2026-05-03
---

# Phase 11 Plan 05: Tail Sampling Diagnostics Summary

**Grafana dashboard extended with a collapsed 'Tail Sampling diagnostics (Phase 11)' row containing 5 panels surfacing canonical v0.151.0 `otelcol_processor_tail_sampling_*` metrics for self-observability of the tail_sampling processor**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-03T05:29:17Z
- **Completed:** 2026-05-03T05:31:07Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Appended new collapsed row "Tail Sampling diagnostics (Phase 11)" at gridPos.y=21 (below existing Deeper-dive row at y=10)
- 4 main panels: per-policy sampling decisions (rate of `count_traces_sampled_total`), per-policy not-sample votes (decision=~`not_sampled|dropped` filter), late-arriving spans (`sampling_late_span_age` histogram with p99 quantile), decision-loop latency (`sampling_decision_timer_latency_milliseconds` with p50/p95/p99 quantiles)
- 1 bonus panel: in-memory traces gauge (`sampling_traces_on_memory`) as F2-2 buffer canary
- Row description carries D-T16 JSDoc POLICY-NAMES CONTRACT reminder + `processor.tailsamplingprocessor.recordpolicy` alpha gate dependency
- All 14 panel IDs unique (1-8 existing preserved, 9-14 new); zero edits to existing panels (additive only per Phase 10 D-01 contract)

## Task Commits

Each task was committed atomically:

1. **Task 1: Append Tail Sampling diagnostics row to ose-otel-demo.json** - `14d00dc` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `grafana/dashboards/ose-otel-demo.json` — Added 158 lines: new collapsed row (id=9) with 5 nested panels (ids 10-14); 23 `"uid": "prometheus"` datasource references; all existing panels 1-8 untouched

## Decisions Made
- Included the optional bonus Panel 5 (traces in memory) — adds minimal complexity but provides the F2-2 buffer canary directly in the dashboard alongside the other panels
- Panel IDs run 9-14 (not 9-13) since the bonus panel is included; acceptance criteria required ids 9-13 as the minimum set, id=14 is additive
- Row description carries the D-T16 JSDoc reminder verbatim from the plan, including `verify:tail-sampling` cross-link and the alpha gate dependency note for Route A

## Deviations from Plan

None - plan executed exactly as written. All 5 panels (4 main + 1 bonus) inserted with verbatim PromQL from RESEARCH §3.3 Route A variants.

## Issues Encountered
None. JSON validation passed on first attempt, all acceptance criteria verified in a single run.

## Known Stubs

Panel 4 (decision-loop latency) uses the `_milliseconds` unit suffix in bucket metric names per RESEARCH §3.3 Route A specification. RESEARCH §3.2.1 flagged this as MEDIUM confidence — if the collector-contrib version at runtime emits the metric without the `_milliseconds` suffix, Panel 4 will show "No data." Plan 11-06 executes live verification and corrects the suffix if needed. This is intentional and documented in Panel 4's description field.

## Threat Flags

None. All new surface is a read-only Grafana dashboard extension (no new network endpoints, no auth paths, no file access patterns). Threats T-11-05-01 through T-11-05-04 analyzed in plan threat_model; all accepted or mitigated via existing controls (D-T16 contract reminder, plan 11-04 verify script).

## Next Phase Readiness
- Plan 11-05 complete: Tail Sampling diagnostics row is live in the provisioned dashboard
- Plan 11-06 (belt-and-suspenders metric-name verification) can now proceed — it will check if Panel 4's `_milliseconds` suffix is correct against live Collector metrics and patch if not
- Workshop attendees can expand the row to see which policy fired how often once `mise run load` has populated the series

---
*Phase: 11-tail-sampling-at-the-collector*
*Completed: 2026-05-03*
