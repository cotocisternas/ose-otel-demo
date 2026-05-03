---
phase: 10-prerequisites-stack-decomposition
plan: "02"
subsystem: infra/observability
tags:
  - otel-collector
  - tempo
  - mimir
  - loki
  - yaml-config
  - stack-decomposition
dependency_graph:
  requires:
    - 10-01-PLAN (research context — no file dependency)
  provides:
    - infra/observability/otelcol-config.yaml (OTLP ingress + 3 signal pipelines + migrated scrape jobs)
    - infra/observability/tempo-config.yaml (trace backend + metrics_generator)
    - infra/observability/mimir-config.yaml (metric backend, single-tenant)
    - infra/observability/loki-config.yaml (log backend + ruler pre-enabled)
    - infra/observability/loki-rules/.gitkeep (empty placeholder for Phase 13 recording rules)
  affects:
    - Plan 10-04 (docker-compose rewrite mounts these files as bind volumes)
    - Plan 10-03 (grafana/datasources.yaml references these backends by service name)
    - Phase 11 (tail sampling inserts between batch and otlp_http/tempo)
    - Phase 13 (loki-rules/ dir receives recording rules; no loki-config.yaml change needed)
tech_stack:
  added:
    - otel/opentelemetry-collector-contrib:0.151.0 (pinned)
    - grafana/tempo:2.10.5 (pinned)
    - grafana/mimir:3.0.6 (pinned)
    - grafana/loki:3.7.1 (pinned)
  patterns:
    - otlp_http/<name> exporter naming (canonical v0.151.0 — with underscore)
    - prometheusremotewrite/<name> exporter for Mimir PRW path
    - multitenancy_enabled: false (Mimir 3.x correct key — not auth_enabled)
    - auth_enabled: false (Loki's correct key — different system from Mimir)
    - ruler.remote_write.clients: map form (not Prometheus flat array)
    - /loki/rules-cache vs /loki/rules path separation (Pitfall 7 mitigation)
key_files:
  created:
    - infra/observability/otelcol-config.yaml (188 lines)
    - infra/observability/tempo-config.yaml (90 lines)
    - infra/observability/mimir-config.yaml (69 lines)
    - infra/observability/loki-config.yaml (83 lines)
    - infra/observability/loki-rules/.gitkeep (empty)
  modified: []
decisions:
  - "Collector telemetry.metrics uses MetricsConfig v0.3.0+ readers/pull/prometheus format — the old address: key is rejected by v0.151.0"
  - "auth_enabled appears only in PEDAGOGICAL COMMENTS warning against use in mimir-config.yaml — correct YAML key is multitenancy_enabled: false"
  - "loki-rules/.gitkeep allows git to track an empty directory for the Phase 13 bind-mount target"
metrics:
  duration: 7 minutes
  completed_date: "2026-05-03"
  tasks_completed: 3
  files_created: 5
  total_lines: 430
---

# Phase 10 Plan 02: Backend YAML Configs Summary

Four backend configuration YAMLs plus the `loki-rules/.gitkeep` placeholder created under `infra/observability/` — all with teaching-grade WHY comments, live-validated against pinned container images, using live-verified key shapes that correct two upstream-doc errors found during research.

## Files Created

| File | Lines | WHY Comments | Validates Against |
|------|-------|--------------|-------------------|
| `infra/observability/otelcol-config.yaml` | 188 | 18 | `otelcol-contrib:0.151.0 validate` — exit 0 |
| `infra/observability/tempo-config.yaml` | 90 | 14 | `grafana/tempo:2.10.5` — no parse error |
| `infra/observability/mimir-config.yaml` | 69 | 12 | `grafana/mimir:3.0.6` — no parse error |
| `infra/observability/loki-config.yaml` | 83 | 10 | `grafana/loki:3.7.1` — no parse error |
| `infra/observability/loki-rules/.gitkeep` | 0 | n/a | empty placeholder |
| **Total** | **430** | **54** | all 4 backends clean |

## Live-Container Validation Results

All four backend configs were validated by starting the pinned Docker images against each config file:

- **Collector**: `otelcol-contrib:0.151.0 validate` subcommand returned exit code 0.
- **Mimir**: `grafana/mimir:3.0.6` started successfully, reached `module=server` and `target=all` log lines — no `field .* not found in type mimir.Config` error.
- **Tempo**: `grafana/tempo:2.10.5` started successfully, reached `Starting Tempo` log line — no `failed to load config` or `invalid config` error.
- **Loki**: `grafana/loki:3.7.1` started successfully, reached `Starting Loki` and loaded configuration file — no `failed to load config`, `cannot unmarshal`, or `field .* not found` error.

## Upstream-Doc Corrections Reflected Verbatim

Two corrections from RESEARCH that directly affect the plan are implemented and verified:

### Correction 1: Mimir 3.0.6 uses `multitenancy_enabled` (not `auth_enabled`)

- **Problem**: Earlier research (CONTEXT.md D-06, STACK.md, PITFALLS.md F1-3) referenced `auth_enabled: false` — the Cortex-era / older-Mimir key.
- **Reality**: Mimir 3.0.6 rejects `auth_enabled` at parse time: `field auth_enabled not found in type mimir.Config`.
- **Fix**: `mimir-config.yaml` uses `multitenancy_enabled: false` at root level.
- **Note**: `auth_enabled` appears only in a PEDAGOGICAL COMMENT at lines 7 and 10 warning readers NOT to use it. The actual YAML config uses the corrected key. `grep -vE '^\s*#' mimir-config.yaml | grep -q 'auth_enabled'` returns false.

### Correction 2: Collector v0.151.0 canonical exporter type is `otlp_http` (with underscore)

- **Problem**: Older docs and research predicted `otlphttp` (no underscore) as the correct name.
- **Reality**: `otel/opentelemetry-collector-contrib:0.151.0` uses `otlp_http` as the canonical type name. `otlphttp` still works but emits a deprecation warning at startup.
- **Fix**: `otelcol-config.yaml` uses `otlp_http/tempo` and `otlp_http/loki` throughout.
- **Note**: A comment at line 109 mentions `otlphttp/tempo works but emits a deprecation warning` — this is pedagogically intentional from the plan's verbatim spec. The actual exporter declarations all use `otlp_http/` (with underscore).

### Correction 3: Collector v0.151.0 MetricsConfig format (deviation from plan verbatim)

- **Problem**: The plan's verbatim `service.telemetry.metrics.address: 0.0.0.0:8888` is rejected by v0.151.0: `'MetricsConfigV030' has invalid keys: address`.
- **Reality**: v0.151.0 uses the MetricsConfig v0.3.0+ format with `readers:` — the old `address:` key no longer exists.
- **Fix**: Used `service.telemetry.metrics.level: detailed` + `readers[0].pull.exporter.prometheus.{host: 0.0.0.0, port: 8888}` format. This exposes Collector self-metrics on :8888 as the plan intended — same observable behavior, new config shape.

## What Plan 04 Needs When Wiring docker-compose Mount Paths

| File | In-container target path | Notes |
|------|--------------------------|-------|
| `infra/observability/otelcol-config.yaml` | `/etc/otelcol-contrib/config.yaml` | Passed via `--config /etc/otelcol-contrib/config.yaml` in compose command |
| `infra/observability/tempo-config.yaml` | `/etc/tempo.yaml` | Passed via `-config.file=/etc/tempo.yaml` in compose command |
| `infra/observability/mimir-config.yaml` | `/etc/mimir.yaml` | Passed via `-config.file=/etc/mimir.yaml` in compose command |
| `infra/observability/loki-config.yaml` | `/etc/loki/local-config.yaml` | Overrides Loki's bundled default config at same path; passed via `-config.file=/etc/loki/local-config.yaml` |
| `infra/observability/loki-rules/` | `/loki/rules` | Bind-mounted as **read-only** (`:ro`); Loki's internal rules cache stays at `/loki/rules-cache` (F4-3/Pitfall 7 mitigation) |

Named volumes Plan 04 must declare:
- `tempo-data` → `/var/tempo` (trace blocks at `/var/tempo/blocks`)
- `tempo-wal` → `/var/tempo/wal` (F1-2: separate WAL volume for metrics_generator persistence)
- `mimir-data` → `/data/mimir` (TSDB blocks + ruler storage + compactor scratch)
- `loki-data` → `/loki` (chunks + cache + ring state)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Collector telemetry.metrics format**

- **Found during:** Task 1 validation
- **Issue:** Plan's verbatim `service.telemetry.metrics.address: 0.0.0.0:8888` is rejected by Collector v0.151.0 — `'MetricsConfigV030' has invalid keys: address`. The MetricsConfig was upgraded to v0.3.0+ format in a recent Collector release.
- **Fix:** Replaced with `level: detailed` + `readers[0].pull.exporter.prometheus.{host: 0.0.0.0, port: 8888}`. Same observable behavior (Prometheus scrape endpoint on :8888), new config shape required by the pinned image version.
- **Files modified:** `infra/observability/otelcol-config.yaml`
- **Commit:** 8614b4f

## Known Stubs

None — all five files are complete configuration artifacts with no placeholder or stub content.

## Threat Flags

No new security surface beyond what the plan's threat model already covers (T-10.02-01 through T-10.02-07). All inter-container endpoints use Docker service names on the internal bridge network; TLS is intentionally disabled per T-10.02-04 (workshop default). The loki-rules bind-mount is read-only per T-10.02-05.

## Self-Check: PASSED
