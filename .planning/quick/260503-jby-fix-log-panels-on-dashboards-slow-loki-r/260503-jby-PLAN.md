---
quick_id: 260503-jby
description: "Fix log panels on dashboards — slow Loki response blocks dashboard refresh"
status: ready
---

# Quick Task: Fix Loki log panels blocking dashboard refresh

## Problem

All 4 Loki log panels across 2 dashboards (`ose-otel-demo.json`, `ose-otel-noc.json`)
have no `maxLines` limit set. Under workshop load (~2 RPS × 4 log lines/request × 15min window
= ~7200 log lines), Loki returns the full scan result on every 30s dashboard refresh.
This blocks the Grafana rendering pipeline — one slow panel holds up the entire refresh cycle.

## Fix

Add `"maxLines": 100` to each Loki log panel's target object. This is a Grafana-native
Loki datasource parameter that caps the number of log lines returned per query.

### Affected panels

| Dashboard | Panel ID | Title | Row | maxLines |
|-----------|----------|-------|-----|----------|
| ose-otel-demo.json | 4 | Logs (filtered by latest trace_id) | projector strip (visible) | 100 |
| ose-otel-demo.json | 8 | Raw Logs (post-workshop poking) | Deeper-dive (collapsed) | 200 |
| ose-otel-noc.json | 502 | Recent ERROR logs (with stacktrace) | Drill-down (collapsed) | 100 |
| ose-otel-noc.json | 503 | Logs for trace_id | Drill-down (collapsed) | 100 |

Panel 8 gets 200 because it's a raw firehose meant for post-workshop poking (more context = better).

### Verification

1. `jq` parse each file and assert `maxLines` is set on every Loki target
2. Restart Grafana, confirm dashboard loads in <2s
3. Confirm log panels still render content (not broken by the cap)
