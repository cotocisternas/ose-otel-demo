---
phase: 11-tail-sampling-at-the-collector
plan: "02"
subsystem: collector-config
tags: [tail-sampling, otelcol, composite-policy, feature-gate, traces-pipeline]
dependency_graph:
  requires: [11-01]
  provides: [tail_sampling-processor, recordpolicy-gate]
  affects: [infra/observability/otelcol-config.yaml, docker-compose.yml]
tech_stack:
  added: []
  patterns: [composite-tail-sampling, alpha-feature-gate-enablement]
key_files:
  created: []
  modified:
    - infra/observability/otelcol-config.yaml
    - docker-compose.yml
decisions:
  - "D-T1: Pipeline order is [memory_limiter, tail_sampling, batch] — canonical OTel pre-batch placement; misleading line-180 forecast comment corrected"
  - "D-T2: Composite policy envelope wraps 3 sub-policies with per-branch rate-limits (production-realistic over flat list)"
  - "Route A confirmed: composite + alpha recordpolicy feature gate (sub-policy attribution via span attributes, not metric labels)"
metrics:
  duration: "~3 minutes"
  completed_date: "2026-05-03"
  tasks_completed: 2
  files_modified: 2
---

# Phase 11 Plan 02: Tail Sampling Processor Configuration Summary

Tail-sampling composite policy with three sub-policies, OR-semantics comment block, corrected pipeline comment, and alpha recordpolicy feature gate for sub-policy span attribution.

## What Was Built

### Task 1 — Insert tail_sampling processor block + fix pipeline comment (47f1425)

Added the `tail_sampling:` processor block to `infra/observability/otelcol-config.yaml` with:
- **Composite envelope** (D-T2): single outer policy `composite-policy` wrapping three sub-policies via `composite_sub_policy`
- **Three sub-policies** (TSAMP-01): `keep-errors` (status_code ERROR), `keep-slow` (latency >1000ms), `probabilistic-fallback` (20%)
- **Per-branch rate-limits** (D-T3): 1000 spans/s global cap; keep-errors=100%, keep-slow=2%, probabilistic-fallback=4%
- **~25-line TSAMP-02 comment block** above composite with verbatim v0.151.0 README "Policy Decision Flow" quote
- **decision_wait: 10s, num_traces: 10000** (F2-2 mitigation: 5x headroom at workshop load)
- **Corrected traces pipeline**: `processors: [memory_limiter, tail_sampling, batch]` (D-T1 canonical order)
- **Fixed line-180 forecast comment**: removed wrong "between batch and the exporter" narrative; replaced with correct "BEFORE batch" explanation with upstream README citation
- Metrics and logs pipelines kept unchanged at `[memory_limiter, batch]`

Verified: Collector restarted cleanly ("Everything is ready. Begin running and processing data.") with no tail_sampling errors.

### Task 2 — Add recordpolicy alpha feature gate to docker-compose.yml (9c5901f)

Extended `otel-collector` service `command:` from single-element to two-element array:
- Preserved `--config=/etc/otelcol-contrib/config.yaml`
- Added `--feature-gates=processor.tailsamplingprocessor.recordpolicy` (Route A / DISCUSSION-LOG.md)
- WHY comment block flags: alpha-gate fragility, version-check reminder (v0.152+), cross-link to `tailsampling.composite_policy` span attribute and `mise run verify:tail-sampling` Tier 2 contract

Verified: Collector recreated cleanly; no feature-gate-related errors in startup logs.

## Deviations from Plan

None — plan executed exactly as written. Route A (composite + alpha gate) was confirmed at plan-time per DISCUSSION-LOG.md amendment 2026-05-03; no architectural deviations arose during execution.

## Key Decisions Made

1. **D-T1 canonical pipeline order** — `[memory_limiter, tail_sampling, batch]` enforced; forecast comment corrected verbatim
2. **D-T2 composite envelope** — locked per plan; composite_sub_policy wraps 3 named sub-policies
3. **Route A confirmed** — alpha `recordpolicy` gate adds span-attribute sub-policy attribution; accepts alpha-gate fragility risk documented in WHY comment

## Known Stubs

None — both files are fully wired. The tail_sampling block is active in the traces pipeline; the feature gate is active in the collector command.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: alpha-feature-gate | docker-compose.yml | `processor.tailsamplingprocessor.recordpolicy` is alpha at v0.151.0 — documented in WHY comment; mitigated by T-11-02-03 disposition (accept, operational metadata only) |

## Self-Check: PASSED

- FOUND: `infra/observability/otelcol-config.yaml` (modified)
- FOUND: `docker-compose.yml` (modified)
- FOUND: `.planning/phases/11-tail-sampling-at-the-collector/11-02-SUMMARY.md` (created)
- FOUND: commit `47f1425` (Task 1 - tail_sampling processor)
- FOUND: commit `9c5901f` (Task 2 - recordpolicy feature gate)
