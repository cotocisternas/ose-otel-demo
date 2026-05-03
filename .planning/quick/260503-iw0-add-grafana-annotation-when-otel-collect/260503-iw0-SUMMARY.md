---
phase: 260503-iw0-add-grafana-annotation-when-otel-collect
plan: 01
subsystem: grafana-dashboards
tags: [grafana, annotation, tail-sampling, observability, workshop-pedagogy]
requirements: [QUICK-iw0-buffer-canary-annotation]
dependency-graph:
  requires:
    - "grafana/dashboards/ose-otel-demo.json (Phase 7 dashboard scaffold)"
    - "infra/observability/otelcol-config.yaml tail_sampling.num_traces=10000 (Phase 11 collector config)"
    - "Phase 11 metric otelcol_processor_tail_sampling_sampling_traces_on_memory exposed by the collector self-metrics scrape"
  provides:
    - "Buffer-saturated visual marker rendered across every panel of the workshop dashboard's projector strip"
  affects:
    - "Workshop pedagogy: F2-2 buffer canary now visible without scrolling to Phase 11 diagnostics row Panel 5"
tech-stack:
  added: []
  patterns:
    - "Grafana 13 Prometheus annotation rule (datasource.uid: prometheus, enable: true, iconColor: red, expr-based)"
key-files:
  created: []
  modified:
    - "grafana/dashboards/ose-otel-demo.json (annotations.list grew from 1 → 2 entries; new entry at index [1])"
decisions:
  - "Hard-coded threshold 10000 (locked decision #4) — textFormat documents the in-lockstep contract with infra/observability/otelcol-config.yaml tail_sampling.num_traces"
  - "Annotation placed inline on workshop dashboard JSON (locked decision #2) — not a separate provisioning manifest, not a Prometheus alert rule"
  - "Sibling dashboards (ose-otel-noc.json, ose-otel-infra.json) untouched (locked decision #6)"
  - "PromQL form `metric >= 10000` returns a non-empty result set ONLY when the gauge pins at the cap, so Grafana renders one annotation tick per saturated scrape sample"
metrics:
  duration: ~3min
  completed: "2026-05-03"
  tasks: 1
  files: 1
---

# Phase 260503-iw0 Plan 01: Add Grafana annotation when otel-collector tail-sampling buffer saturates — Summary

Append a "Tail-sampling buffer saturated" annotation rule to the workshop dashboard JSON so the F2-2 buffer canary signal bleeds across every panel of the projector strip whenever `otelcol_processor_tail_sampling_sampling_traces_on_memory` pins at the configured `num_traces=10000` cap.

## What Changed

### Dashboard JSON

`grafana/dashboards/ose-otel-demo.json` (additive edit, +12 lines, 0 deletions):

- `annotations.list` grew from 1 entry → 2 entries
- Index `[0]` is the pre-existing built-in `Annotations & Alerts` entry — preserved verbatim (`builtIn: 1`, datasource `-- Grafana --`, `hide: true`, `iconColor: rgba(0, 211, 255, 1)`)
- Index `[1]` is the new `Tail-sampling buffer saturated` entry:
  - `datasource: { type: prometheus, uid: prometheus }` — uses the same Mimir/Prometheus datasource shape every other Prometheus query in the dashboard uses
  - `enable: true` — visible by default, no toggle needed
  - `iconColor: red` — reads as a warning event on every timeseries
  - `expr: otelcol_processor_tail_sampling_sampling_traces_on_memory >= 10000` — comparison operator returns a non-empty instant vector ONLY at scrape samples where the gauge meets the cap, producing one tick per saturated point
  - `titleFormat: buffer saturated` — short hover title
  - `textFormat: num_traces hit cap (10000) — ... Threshold mirrors infra/observability/otelcol-config.yaml tail_sampling.num_traces; update both in lockstep if the cap changes.` — long-form tooltip that bakes the cross-file contract into the rendered UI itself

### Untouched (assertions verified)

- All 6 top-level entries in `panels[]` (4 single panels + 2 row containers, including the Phase 11 diagnostics row's Panel 5 / id 14 which plots the same metric)
- `templating.list` (the `traceid` textbox variable)
- `tags`, `uid` (`ose-otel-demo`), `title` (`OSE OTel Demo — Three Signals`), `refresh` (`30s`), `time` (`now-15m → now`), `timepicker`, `timezone`
- Sibling dashboards `grafana/dashboards/ose-otel-noc.json` and `grafana/dashboards/ose-otel-infra.json`

## Verification (live stack at execution time)

| Check | Command | Result |
|------|---------|--------|
| JSON parses cleanly | `python3 -c 'import json; json.load(open(...))'` | exit 0, prints `ok` |
| `annotations.list` has 2 entries | shape-validation script | GREEN — `count=2`, `names=['Annotations & Alerts','Tail-sampling buffer saturated']` |
| Built-in entry preserved at index [0] | shape-validation script | GREEN — name unchanged |
| New entry at index [1] has correct fields | shape-validation script | GREEN — `expr`, `iconColor=red`, `enable=true`, `datasource.uid=prometheus`, `titleFormat` contains `buffer saturated`, `textFormat` contains `10000` |
| `jq` existence check | `jq -e '.annotations.list \| map(.name) \| contains(["Tail-sampling buffer saturated"])'` | exit 0 |
| `uid` unchanged | `jq '.uid'` | `"ose-otel-demo"` |
| `panels` count unchanged | working tree (6) vs HEAD (6) | equal |
| Sibling dashboards untouched | `git status --porcelain grafana/dashboards/` | only `M ose-otel-demo.json` |
| Grafana provisioned the new annotation | `docker compose restart grafana` + `curl /api/dashboards/uid/ose-otel-demo` | API returns both annotations; the second has `name=Tail-sampling buffer saturated`, `iconColor=red`, `enable=true`, `expr=otelcol_processor_tail_sampling_sampling_traces_on_memory >= 10000` |
| No provisioning errors in Grafana logs | `docker compose logs grafana --tail 80 \| grep -iE 'error\|failed' \| grep -iE 'ose-otel-demo\|annotation'` | no matches |
| Threshold matches reality | `grep -nE 'num_traces' infra/observability/otelcol-config.yaml` | line 166 = `num_traces: 10000` |

## Threat-Register Disposition

All three threats from the plan's threat_model are mitigated/accepted as designed:

- **T-iw0-01 (Tampering: drift between hard-coded `>= 10000` and the YAML `num_traces`)** — mitigated. The annotation's `textFormat` field bakes the contract into the rendered tooltip so any future maintainer reading the saturation marker sees the dependency directly.
- **T-iw0-02 (Information disclosure)** — accepted. Workshop scope; `sampling_traces_on_memory` is operational metadata (a small integer count), no PII.
- **T-iw0-03 (DoS via expensive annotation query)** — accepted. The expression is a single instant-vector comparison on a single-series gauge; Mimir at workshop scale handles it trivially at the dashboard's 30s refresh.

## Locked-Decision Compliance

| # | Decision | Implementation |
|---|----------|----------------|
| 1 | Annotation, not alert | annotation rule; no Prometheus alert rule, no alertmanager wiring |
| 2 | Inline on workshop dashboard JSON | edit lives in `grafana/dashboards/ose-otel-demo.json`; no separate provisioning manifest |
| 3 | Mimir/Prometheus datasource | `datasource: { type: prometheus, uid: prometheus }` |
| 4 | Hard-coded `>= 10000` threshold | matches current `num_traces: 10000`; `textFormat` documents the in-lockstep maintenance contract |
| 5 | Red icon, enabled by default | `iconColor: red`, `enable: true` |
| 6 | Workshop dashboard only | sibling `ose-otel-noc.json` and `ose-otel-infra.json` untouched |
| 7 | Title "buffer saturated", text references the 10000 cap | `titleFormat: buffer saturated`, `textFormat` includes `(10000)` and the YAML reference |

## Deviations from Plan

None — plan executed exactly as written.

The plan's literal `<automated>` verify shell snippet had a Python f-string quoting collision (nested double-quote `f"...{a[0].get(\"name\")}..."` doesn't parse when wrapped in another double-quoted shell layer). I ran the equivalent assertions via a heredoc-fed `python3 -` invocation and confirmed every assertion of the original gate. This was a verification-script formatting issue, not a deviation in plan substance — every assertion the gate makes was still evaluated and passed.

## Acceptance Criteria — All Green

- [x] `python3 -c "import json; json.load(open(...))"` exits 0
- [x] `jq '.annotations.list \| length'` outputs `2`
- [x] `jq -r '.annotations.list[0].name'` outputs `Annotations & Alerts`
- [x] `jq -r '.annotations.list[1].name'` outputs `Tail-sampling buffer saturated`
- [x] `jq -r '.annotations.list[1].iconColor'` outputs `red`
- [x] `jq -r '.annotations.list[1].enable'` outputs `true`
- [x] `jq -r '.annotations.list[1].datasource.uid'` outputs `prometheus`
- [x] `jq -r '.annotations.list[1].expr'` outputs `otelcol_processor_tail_sampling_sampling_traces_on_memory >= 10000`
- [x] `jq -r '.annotations.list[1].titleFormat'` contains `buffer saturated`
- [x] `jq -r '.annotations.list[1].textFormat'` contains `10000`
- [x] `jq '.uid'` outputs `"ose-otel-demo"` (unchanged)
- [x] `jq '.panels \| length'` working tree equals HEAD (6 = 6, panels untouched)
- [x] Only `grafana/dashboards/ose-otel-demo.json` modified (sibling dashboards untouched)
- [x] After Grafana restart, API returns both annotations including the new one
- [x] No provisioning errors in `docker compose logs grafana` for this dashboard

## Commits

- `3daf8ed feat(dashboard): annotate tail-sampling buffer saturation`

## Self-Check: PASSED

- File `grafana/dashboards/ose-otel-demo.json` exists and is modified — FOUND
- Commit `3daf8ed` exists in git log — FOUND
- SUMMARY file at `.planning/quick/260503-iw0-add-grafana-annotation-when-otel-collect/260503-iw0-SUMMARY.md` — being written by this Write call
