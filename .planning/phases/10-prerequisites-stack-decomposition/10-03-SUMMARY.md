---
phase: 10-prerequisites-stack-decomposition
plan: "03"
subsystem: infra
tags: [grafana, datasource-provisioning, cross-signal-datalinks, otel-lgtm, mimir, tempo, loki]

requires:
  - phase: 10-prerequisites-stack-decomposition/plan-01
    provides: "Mimir, Tempo, Loki, Grafana container configs"
  - phase: 10-prerequisites-stack-decomposition/plan-02
    provides: "OTel Collector config wiring signals to backends"

provides:
  - "grafana/datasources.yaml — standalone Grafana 13 datasource provisioning manifest with 3 datasources (uid=prometheus/tempo/loki) and full D-02 cross-signal datalinks"
  - "grafana/dashboards/dashboards.yaml options.path updated to /var/lib/grafana/dashboards for standalone Grafana 13"

affects: [10-04, 10-05, phase-12-exemplar-wiring]

tech-stack:
  added: []
  patterns:
    - "Verbatim UID reuse from inspected lgtm container (D-01): uid=prometheus for Mimir, uid=tempo for Tempo, uid=loki for Loki"
    - "All D-02 cross-signal datalinks wired in the datasource provisioning YAML: tracesToLogsV2, tracesToMetrics, serviceMap, derivedFields, exemplarTraceIdDestinations"
    - "Single-dollar ${...} Grafana-native variable substitution (not Helm double-dollar)"

key-files:
  created:
    - grafana/datasources.yaml
  modified:
    - grafana/dashboards/dashboards.yaml

key-decisions:
  - "Mimir datasource UID is `prometheus` (NOT `mimir`) — LOAD-BEARING contract with ose-otel-demo.json dashboard panels; renaming would silently break all metric panels (D-01)"
  - "All D-02 cross-signal datalinks land in Phase 10 datasources.yaml so Phase 12 only flips SDK + Collector flags — no datasource re-edit required"
  - "exemplarTraceIdDestinations wired as placeholder now; Phase 12 activates via ExemplarFilter.traceBased() + Collector send_exemplars"
  - "dashboards.yaml options.path changed from /otel-lgtm/grafana/conf/provisioning/dashboards (lgtm-internal) to /var/lib/grafana/dashboards (standalone Grafana 13)"

patterns-established:
  - "Pattern: UID contract preservation across stack migrations — always grep dashboard JSONs before renaming datasource UIDs"
  - "Pattern: WHY-comment documentation in provisioning YAML files (17 WHY comments in datasources.yaml)"

requirements-completed:
  - STACK-04

duration: 15min
completed: "2026-05-02"
---

# Phase 10 Plan 03: Grafana Datasource Provisioning and Dashboard Path Fix Summary

**Standalone Grafana 13.0.1 datasource provisioning manifest with verbatim lgtm UIDs (uid=prometheus/tempo/loki), full D-02 cross-signal datalinks, and corrected dashboards.yaml options.path for standalone container paths**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-02T20:20:00Z
- **Completed:** 2026-05-02T20:37:00Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- Created `grafana/datasources.yaml` (124 lines) with three datasource entries carrying UIDs copied VERBATIM from inspected `grafana/otel-lgtm:0.26.0` container — uid=prometheus (Mimir), uid=tempo, uid=loki — preserving the D-01 contract with all three existing dashboard JSON files
- Wired all five D-02 cross-signal datalinks in a single file: `tracesToLogsV2` (Tempo→Loki), `tracesToMetrics` (Tempo→Mimir), `serviceMap` (Tempo→Mimir), `derivedFields` (Loki→Tempo), `exemplarTraceIdDestinations` (Mimir→Tempo, Phase 12 placeholder)
- Updated `grafana/dashboards/dashboards.yaml` options.path from lgtm-internal `/otel-lgtm/grafana/conf/provisioning/dashboards` to standalone Grafana 13 `/var/lib/grafana/dashboards`; existing dashboard JSON files untouched

## Task Commits

Each task was committed atomically:

1. **Task 1: Create grafana/datasources.yaml** - `6ddfce5` (feat)
2. **Task 2: Update dashboards.yaml options.path** - `a4dee15` (feat)

## Files Created/Modified

- `grafana/datasources.yaml` — NEW: 124-line standalone Grafana 13 datasource provisioning manifest. Header doc-block includes explicit "uid: prometheus (NOT mimir!)" trap warning and the live-verified docker inspection command. Three datasource entries with all D-02 cross-signal datalinks. 17 WHY comments per D-04 teaching convention.
- `grafana/dashboards/dashboards.yaml` — MODIFIED: options.path updated for standalone Grafana 13 paths; comment block replaced to document both mount points (/etc/grafana/provisioning/dashboards for the manifest, /var/lib/grafana/dashboards for the JSON files); dashboard JSON files untouched.

## Cross-Signal Datalink Inventory

| Datalink | From | To | Field | Purpose |
|---|---|---|---|---|
| tracesToLogsV2 | Tempo (uid=tempo) | Loki (uid=loki) | datasourceUid: loki | Click span → land in Loki with trace_id + service.name filter |
| tracesToMetrics | Tempo (uid=tempo) | Mimir (uid=prometheus) | datasourceUid: prometheus | Click span → PromQL chart of request rate for same service |
| serviceMap | Tempo (uid=tempo) | Mimir (uid=prometheus) | datasourceUid: prometheus | Service graph edge metrics from Tempo metrics_generator |
| derivedFields | Loki (uid=loki) | Tempo (uid=tempo) | datasourceUid: tempo | Click trace_id label → Tempo trace view |
| exemplarTraceIdDestinations | Mimir (uid=prometheus) | Tempo (uid=tempo) | datasourceUid: tempo | Phase 12 prep: histogram exemplar → Tempo trace jump |

## Header Doc-Block Excerpt (uid=prometheus Trap Warning)

```
# CRITICAL TRAP — the metric backend's UID is `prometheus` (NOT `mimir`!).
# The lgtm container kept the historical Prometheus UID on its bundled Mimir for
# dashboard backwards-compat. The v1.0 ose-otel-demo.json hardcodes `"uid": "prometheus"`
# in every panel that queries metrics — renaming to `mimir` would break every panel.
# D-01 says: keep the contract.
```

## Git Status: Dashboard JSON Files

Confirmed via `git status --porcelain`:
- `grafana/dashboards/ose-otel-demo.json` — UNTOUCHED
- `grafana/dashboards/ose-otel-infra.json` — UNTOUCHED
- `grafana/dashboards/ose-otel-noc.json` — UNTOUCHED

D-01 UID reuse strategy makes all three JSON files portable across the v1.0→v2.0 stack migration without modification.

## Decisions Made

- **uid=prometheus for Mimir (NOT mimir):** Load-bearing contract with existing dashboard JSON files. All 10 occurrences of `"uid": "prometheus"` in ose-otel-demo.json rely on this UID. This is explicitly documented as the #1 pitfall in RESEARCH.md (Pitfall 1 / F1-1).
- **All D-02 datalinks in Phase 10:** Concentrated all cross-signal wiring in this single file so Phase 12 only requires SDK + Collector config changes — no datasource re-editing at that phase.
- **exemplarTraceIdDestinations as placeholder:** The Mimir→Tempo exemplar click-through is provisioned now but dormant until Phase 12 enables `ExemplarFilter.traceBased()` on the SDK side and `send_exemplars: true` on the Collector side.
- **Single-dollar ${...} substitution:** Grafana-native form for standalone Grafana 13 (not Helm double-dollar form from lgtm-bundled source).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

The worktree was initialized at commit `4d118dc` (phase 6), which predates the grafana/ directory added in later commits. The worktree HEAD check correctly identified merge-base != `6ecf448` and reset to the proper starting commit, making grafana/ available.

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes. All datasource URLs point to docker-compose internal service names (mimir:9009, tempo:3200, loki:3100) — no external network exposure. The `GF_AUTH_ANONYMOUS_ENABLED=true` carryforward (T-10.03-01) is Plan 04's concern (env var wiring), not this plan.

## Next Phase Readiness

- `grafana/datasources.yaml` ready for Plan 04 bind-mount into `grafana/grafana:13.0.1` at `/etc/grafana/provisioning/datasources/datasources.yaml`
- `grafana/dashboards/dashboards.yaml` corrected; Plan 04 bind-mounts it at `/etc/grafana/provisioning/dashboards/dashboards.yaml`
- All three dashboard JSON files portable as-is; Plan 04 bind-mounts `./grafana/dashboards` at `/var/lib/grafana/dashboards`
- Phase 12 can activate exemplar click-through by flipping two SDK lines — no datasource changes required

---
*Phase: 10-prerequisites-stack-decomposition*
*Completed: 2026-05-02*
