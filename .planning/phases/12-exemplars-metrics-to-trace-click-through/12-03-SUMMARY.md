---
phase: 12-exemplars-metrics-to-trace-click-through
plan: "03"
subsystem: grafana-dashboard, mise-tasks
tags: [exemplars, grafana, mimir, histogram, verification]
dependency_graph:
  requires:
    - "12-01: ExemplarFilter.traceBased() on both SdkMeterProviders (EXMP-01)"
    - "12-02: Mimir exemplar storage config (EXMP-02)"
    - "10: Grafana datasource exemplarTraceIdDestinations pre-wired (D-02)"
  provides:
    - "Grafana histogram panel with exemplar: true on p50/p95/p99 targets (EXMP-03)"
    - "mise run verify:exemplars fast-fail gate (EXMP-04)"
  affects:
    - "Grafana dashboard provisioning (ose-otel-demo.json reload on Grafana start)"
tech_stack:
  added: []
  patterns:
    - "Grafana exemplar panel with exemplarLabel: trace_id for Tempo click-through"
    - "Mimir /prometheus/api/v1/query_exemplars verification via curl + jq"
key_files:
  created: []
  modified:
    - grafana/dashboards/ose-otel-demo.json
    - mise.toml
decisions:
  - "Panel id=15 row uses panels: [] (empty array) — uncollapsed rows have sibling panels at top-level array, not nested inside the row object"
  - "Panel id=16 placed at y=41 (immediately below row header at y=40) with w=24 (full-width for histogram readability)"
  - "Three separate targets (refId A/B/C) for p50/p95/p99 each with exemplar: true — one target cannot express multiple quantiles with exemplars in a single query"
  - "verify:exemplars queries last 10 minutes window to match the 5m rate() window in panel queries"
metrics:
  duration: "4min"
  completed: "2026-05-03T20:26:32Z"
  tasks_completed: 2
  files_modified: 2
---

# Phase 12 Plan 03: Grafana Exemplar Panel and verify:exemplars Task Summary

**One-liner:** Grafana histogram panel with `exemplar: true` on p50/p95/p99 targets wires Mimir exemplar dots to Tempo click-through; `verify:exemplars` mise task provides fast-fail pipeline validation via Mimir exemplar API + jq assertion.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add Exemplars row and histogram panel to ose-otel-demo.json | c5d9327 | grafana/dashboards/ose-otel-demo.json |
| 2 | Add verify:exemplars task to mise.toml | 7ad3a75 | mise.toml |

## What Was Built

### Task 1: Exemplars (Phase 12) dashboard panel

Added two new JSON objects to the top-level `"panels"` array in `grafana/dashboards/ose-otel-demo.json`:

- **Row id=15** — `"Exemplars (Phase 12)"` with `"collapsed": false` at `y=40`. Open row with `"panels": []` (empty array; panels are siblings at the top-level array as required by Grafana's uncollapsed row convention).
- **Panel id=16** — `"HTTP Request Duration (with Exemplars)"` timeseries at `y=41`, `w=24` (full-width). Three targets (p50/p95/p99 via `histogram_quantile()`) each with `"exemplar": true`. Options include `"exemplarLabel": "trace_id"` to route exemplar dots to Tempo via the `exemplarTraceIdDestinations` pre-wired in Phase 10.

Dashboard UID `ose-otel-demo`, `schemaVersion: 39`, and all existing panel IDs 1-14 are unchanged.

### Task 2: verify:exemplars mise task

Added `[tasks."verify:exemplars"]` at the end of `mise.toml`, following the same structure as `verify:tail-sampling`. The task:

1. Queries `http://localhost:9009/prometheus/api/v1/query_exemplars?query=http_server_request_duration_seconds_bucket` for the last 10 minutes
2. Asserts via `jq -e '.data | length > 0 and (.[0].exemplars | length > 0) and (.[0].exemplars[0].labels.trace_id != null)'`
3. On failure: exits 1 with actionable diagnostic messages covering all three pipeline layers (SDK ExemplarFilter, HttpServerSpanFilter scope, Mimir limits config)
4. On success: prints GREEN message with navigation hint to Grafana panel

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None — panel queries reference the live `http_server_request_duration_seconds_bucket` metric emitted by the producer service's `HttpServerSpanFilter`. Exemplar dots will appear as soon as load is running and all three pipeline layers are active.

## Threat Flags

None — no new network endpoints or auth paths introduced. The panel queries Mimir via the existing Prometheus datasource. The verify task uses a local curl call to Mimir's existing HTTP API (localhost:9009).

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| grafana/dashboards/ose-otel-demo.json exists | FOUND |
| mise.toml exists | FOUND |
| 12-03-SUMMARY.md exists | FOUND |
| Commit c5d9327 (Task 1) exists | FOUND |
| Commit 7ad3a75 (Task 2) exists | FOUND |
