---
status: partial
phase: 11-tail-sampling-at-the-collector
source: [11-VERIFICATION.md]
started: 2026-05-03T06:41:56Z
updated: 2026-05-03T06:41:56Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live E2E smoke — verify:tail-sampling exits 0 GREEN
expected: After `mise run infra:up && mise run dev && mise run load SLOW_RPS=2` (wait ~60s for decision_wait window to populate), `mise run verify:tail-sampling` exits 0 with a GREEN success message. Tier 1 confirms the composite-policy is registered in :8888/metrics; Tier 2 confirms each sub-policy name (keep-errors, keep-slow, probabilistic-fallback) appears as a `tailsampling.composite_policy` span attribute in Tempo.
result: [pending]

### 2. Visual — Grafana Tail Sampling diagnostics row shows live data
expected: Open `http://localhost:3000/d/ose-otel-demo/ose-otel-demo`, expand the "Tail Sampling diagnostics (Phase 11)" collapsed row. At least Panel 1 (composite-policy sampling decisions) and Panel 2 (not-sample votes) show live series — not "No data". Confirm Panel 4 (decision-loop latency) shows sub-10ms values at workshop scale.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
