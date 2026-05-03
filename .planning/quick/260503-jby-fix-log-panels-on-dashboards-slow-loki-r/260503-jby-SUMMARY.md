---
status: complete
quick_id: 260503-jby
description: "Fix log panels on dashboards — slow Loki response blocks dashboard refresh"
commit: 42a4ad1
date: 2026-05-03
---

# Summary: Fix Loki log panels blocking dashboard refresh

## What changed

Added `maxLines` to all 4 Loki log panel targets across 2 dashboards:

| Dashboard | Panel | maxLines |
|-----------|-------|----------|
| ose-otel-demo.json | 4 (Logs filtered by trace_id) | 100 |
| ose-otel-demo.json | 8 (Raw Logs — post-workshop) | 200 |
| ose-otel-noc.json | 502 (Recent ERROR logs) | 100 |
| ose-otel-noc.json | 503 (Logs for trace_id) | 100 |

## Root cause

All Loki log panels had no `maxLines` limit. Under workshop load (~2 RPS sustained),
the stream selector `{service_name=~"order-.*"}` selected ~7200 log lines per 15-minute
window. Without `maxLines`, Loki returned the full scan result on every 30-second
dashboard auto-refresh, blocking the Grafana rendering pipeline.

## Verification

- All 4 panels confirmed via `jq` validation (file-level) and Grafana API probe (runtime)
- JSON parse: both files valid
- Grafana restarted, health OK, dashboards provisioned with new caps
- Infra dashboard (ose-otel-infra) has no Loki panels — unaffected

## Files changed

- `grafana/dashboards/ose-otel-demo.json` (+2 lines)
- `grafana/dashboards/ose-otel-noc.json` (+2 lines)
