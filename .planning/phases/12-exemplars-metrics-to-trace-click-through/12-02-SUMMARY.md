---
phase: 12-exemplars-metrics-to-trace-click-through
plan: "02"
subsystem: infra/observability
tags: [mimir, otelcol, exemplars, config, prw]
dependency_graph:
  requires: []
  provides: [mimir-exemplar-storage, otelcol-prw-why-comment]
  affects: [infra/observability/mimir-config.yaml, infra/observability/otelcol-config.yaml]
tech_stack:
  added: []
  patterns: [mimir-limits-block, inline-why-comments]
key_files:
  created: []
  modified:
    - infra/observability/mimir-config.yaml
    - infra/observability/otelcol-config.yaml
decisions:
  - Mimir limits.max_global_exemplars_per_user=100000 is the storage gate; default 0 silently discards exemplars
  - No send_exemplars key in otelcol PRW exporter — it does not exist in collector-contrib v0.151.0; forwarding is unconditional
metrics:
  duration: "2 min"
  completed: "2026-05-03"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 2
---

# Phase 12 Plan 02: Infrastructure Exemplar Plumbing Summary

**One-liner:** Mimir exemplar storage enabled via `limits.max_global_exemplars_per_user: 100000`; PRW exporter WHY comment documents unconditional forwarding in collector-contrib v0.151.0.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add limits.max_global_exemplars_per_user to mimir-config.yaml | 700a916 | infra/observability/mimir-config.yaml |
| 2 | Add WHY comment to otelcol-config.yaml PRW exporter block | 78da478 | infra/observability/otelcol-config.yaml |

## What Was Built

### Task 1 — Mimir limits block

Appended a `limits:` top-level block at the end of `mimir-config.yaml`:

```yaml
limits:
  max_global_exemplars_per_user: 100000  # WHY: 0 (default) = exemplar ingestion disabled; ...
```

This is the real gate for exemplar storage. Mimir's default value of 0 causes the ingester to silently discard every exemplar that the OTel Collector forwards via PRW — `GET /prometheus/api/v1/query_exemplars` always returns `{"data":[]}` without this setting. Setting it to 100000 (the Grafana-recommended starting value) activates storage for the single-tenant workshop setup.

### Task 2 — otelcol-config.yaml WHY comment

Added a `# Phase 12 — exemplar forwarding:` comment block between the existing WHY comment and the `endpoint:` line in the `prometheusremotewrite/mimir` exporter block.

The comment explains:
- NO `send_exemplars: true` key is required or valid in collector-contrib v0.151.0
- The PRW v1 translator forwards exemplars unconditionally (via `getPromExemplars` in `pkg/translator/prometheusremotewrite/helper.go`)
- The real gate is `mimir-config.yaml limits.max_global_exemplars_per_user`
- Teaching artifact: prevents workshop attendees from adding a non-existent config key that would cause a parse error at Collector startup

## Deviations from Plan

None — plan executed exactly as written.

## Verification

All acceptance criteria passed:

- `mimir-config.yaml` contains `max_global_exemplars_per_user: 100000` with inline WHY comment
- `limits:` is a top-level YAML key (zero indentation); exactly 1 `limits:` block in the file
- `otelcol-config.yaml` does NOT contain `send_exemplars` as a YAML key (only in a comment)
- `otelcol-config.yaml` contains `forwards exemplars unconditionally` in a comment line
- `prometheusremotewrite/mimir` block: `endpoint: http://mimir:9009/api/v1/push` and `tls.insecure: true` unchanged
- Both files are valid YAML (python3 yaml.safe_load exits 0)

## Known Stubs

None. These are config-only changes; no placeholder values introduced.

## Threat Flags

No new security-relevant surface introduced. Threat model items T-12-03 (Information Disclosure via query_exemplars) and T-12-04 (Tampering via limits config) are accepted as per plan — single-tenant workshop context, no runtime modification path.

## Self-Check: PASSED

- `infra/observability/mimir-config.yaml` — FOUND, contains `max_global_exemplars_per_user: 100000`
- `infra/observability/otelcol-config.yaml` — FOUND, contains `forwards exemplars unconditionally`
- Commit 700a916 — FOUND
- Commit 78da478 — FOUND
