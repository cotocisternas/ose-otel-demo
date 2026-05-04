---
phase: 13-log-based-metrics-loki-recording-rules
plan: "01"
subsystem: observability-infrastructure
tags: [loki, recording-rules, mimir, grafana, dashboard, mise]
dependency_graph:
  requires: [phase-10-loki-ruler-pre-enabled, phase-12-exemplars-dashboard-panels]
  provides: [LMET-01-verification-gate, LMET-02-recording-rule, LMET-03-dashboard-panel]
  affects: [grafana/dashboards/ose-otel-demo.json, infra/observability/loki-rules/, mise.toml]
tech_stack:
  added: []
  patterns:
    - Loki ruler recording rule with fake/ tenant subdirectory (auth_enabled: false pattern)
    - LogQL rate() window >= 2x evaluation_interval (F4-2 aliasing mitigation)
    - log: metric name prefix to prevent collision with SDK-emitted metrics (F4-1)
    - Two-tier mise verify task (rules API + query API with retry loops)
key_files:
  created:
    - infra/observability/loki-rules/fake/order-errors.yaml
  modified:
    - grafana/dashboards/ose-otel-demo.json
    - mise.toml
decisions:
  - "fake/ subdirectory required as Loki tenant ID when auth_enabled: false — ruler silently ignores files not in a tenant subdirectory"
  - "[2m] rate window chosen as 2x evaluation_interval=1m to prevent aliasing (rate returning 0 with logs present)"
  - "log: metric name prefix prevents namespace collision with SDK-emitted metrics in Mimir"
  - "Two-tier verify:log-metrics: tier-1 checks Loki rules API, tier-2 queries Mimir for data — fail-fast with diagnostic output"
  - "Row panel id=17 set collapsed=false so Phase 13 teaching content is headline-visible on dashboard load"
  - "Panel id=18 uses unit=ops (events/sec) since both SDK rate() query and log recording rule return rates"
metrics:
  duration: "2 minutes"
  completed_date: "2026-05-04"
  tasks_completed: 2
  files_changed: 3
---

# Phase 13 Plan 01: Loki Recording Rule, Dashboard Panel, and Verification Task Summary

**One-liner:** Loki LogQL recording rule (log:order_errors:rate2m) in fake/ tenant dir, Grafana dashboard overlay of SDK counter vs log-derived error rate, and two-tier mise verify task.

## What Was Built

### Task 1: Loki Recording Rule + verify:log-metrics

**File: `infra/observability/loki-rules/fake/order-errors.yaml`**

Created the Loki recording rule that derives an error rate metric from logs using LogQL. Key design decisions:
- Placed in `fake/` subdirectory — Loki's ruler requires `<directory>/<tenant_id>/<file>.yaml`; with `auth_enabled: false` the implicit tenant is "fake" (F4-3 mitigation)
- `rate({service_name=~"order-.+"} |= "ERROR" [2m])` — matches both order-producer and order-consumer services with a 2-minute window
- `[2m]` window is exactly 2x the `evaluation_interval=1m` from loki-config.yaml (F4-2 aliasing mitigation)
- `sum by (service_name)` limits cardinality to service_name label only (T-13-02 / F4-4 mitigation)
- `log:` metric name prefix prevents collision with SDK-emitted metrics like `orders_created_total` (F4-1)
- `interval: 1m` override in the rule group makes the file self-documenting

File has 5 `# WHY` comments following the workshop's teaching-comment convention.

**File: `mise.toml` — verify:log-metrics task**

Appended after the existing `verify:exemplars` block. Two-tier verification:
- Tier 1: `GET /loki/api/v1/rules` — asserts ruler loaded `order-error-rules` group and `log:order_errors:rate2m` rule
- Tier 2: `GET /prometheus/api/v1/query?query=log:order_errors:rate2m` on Mimir — asserts data exists
- Each tier has 6 retry attempts with 10s sleep, matching the verify:exemplars/verify:tail-sampling pattern
- Diagnostic echo guidance on failure (file path check, docker restart, timing note)

### Task 2: Grafana Dashboard Panel

**File: `grafana/dashboards/ose-otel-demo.json`**

Added two panels after the existing id=16 Exemplars panel:
- **Panel id=17** (row): "Log-Based Metrics (Phase 13)" at y=50, `collapsed: false` — open by default so the teaching content is immediately visible
- **Panel id=18** (timeseries): "Log-Based Error Rate vs SDK Counter" at y=51, h=9

Panel id=18 overlays two series on a shared ops (events/sec) axis:
- `sum by (service_name) (rate(orders_created_total[2m]))` — SDK-emitted counter rate (target A)
- `log:order_errors:rate2m` — log-derived error rate from the recording rule (target B), no wrapping rate() needed since it is already a rate

The panel description surfaces the teaching callout: "The gap between the SDK-emitted creation rate and the log-derived error rate is your success rate."

No existing panels (id 1-16) were modified.

## Deviations from Plan

None — plan executed exactly as written. All files match the exact content specified in the plan's `<action>` blocks. No bugs discovered, no missing functionality, no blocking issues.

## Known Stubs

None. Both files are fully functional:
- The recording rule YAML is complete and ready for the Loki ruler to evaluate on container restart
- The dashboard panel has real PromQL queries targeting live Mimir data
- The verification task has real API endpoints with actual assertions

## Threat Surface Scan

No new security-relevant surfaces introduced. Files delivered:
- `loki-rules/fake/order-errors.yaml` — static YAML bind-mounted read-only; no network exposure (T-13-01 accepted)
- `ose-otel-demo.json` — dashboard config; no new network endpoints
- `mise.toml` — developer task; no new services

All threats in the plan's `<threat_model>` are within the `accept` or pre-mitigated `mitigate` dispositions. No unplanned threat surface detected.

## Self-Check: PASSED

- `infra/observability/loki-rules/fake/order-errors.yaml`: FOUND
- `grafana/dashboards/ose-otel-demo.json`: FOUND (valid JSON confirmed)
- `mise.toml`: FOUND (verify:log-metrics task confirmed)
- Commit 4dc25f7: FOUND (Task 1)
- Commit 9352c4e: FOUND (Task 2)
