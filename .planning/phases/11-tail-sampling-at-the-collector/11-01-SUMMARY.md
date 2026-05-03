---
plan: 11-01
phase: 11-tail-sampling-at-the-collector
status: complete
wave: 1
completed: 2026-05-03
---

# Plan 11-01 Summary: OFF Screenshot Baseline

## What Was Built

Captured the pre-Phase-11 Tempo Search screenshot showing 100% head-sampled trace volume
for `order-producer` (no tail_sampling processor active).

## Key Files

- `docs/screenshots/step-11-tail-sampling-OFF.png` — baseline screenshot (committed manually by operator)

## Self-Check: PASSED

- Grafana Explore configured: Tempo datasource, Search tab, Service Name = order-producer, Last 5 minutes
- Load running at ~200 rps (express + standard streams) before screenshot
- Screenshot captured before any otelcol-config.yaml changes (sequencing constraint satisfied)
- Committed to main before plans 11-02..11-06 land

## Notes

Screenshot was captured via browser automation to Grafana Explore at http://localhost:3000/explore
with the query `{resource.service.name="order-producer"}`, time range "Last 5 minutes".
Operator committed the PNG manually.
