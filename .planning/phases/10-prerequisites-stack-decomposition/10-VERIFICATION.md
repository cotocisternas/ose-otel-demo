---
phase: 10-prerequisites-stack-decomposition
verified: 2026-05-03T03:00:00Z
status: human_needed
score: 6/7 must-haves verified
overrides_applied: 0
gaps: []
human_verification:
  - test: "Open http://localhost:3000/d/ose-otel-demo/ose-otel-demo in a browser and confirm every panel renders without a 'Datasource not found' error banner — top row (Tempo trace search, Service graph, RED metrics, Loki logs) and second row (collapsed by default)."
    expected: "All panels show either live data or an empty-data state. No red 'Datasource not found' banner on any panel."
    why_human: "Panel render correctness requires a running Grafana instance and a visual browser check. The datasource UID contract is verified programmatically (verify:datasources passes), but the final visual assertion — that the dashboard JSON's panel queries actually resolve against the provisioned datasources — requires eyeball inspection. SC #4 (STACK-04) and PREREQ-02 remain open until this check and the screenshot below complete."
  - test: "With the stack running and at least one POST /orders sent, capture a screenshot of the metrics panel (orders_created_total or RED metrics row) in the ose-otel-demo dashboard and save it as docs/screenshots/step-04-metrics.png."
    expected: "File docs/screenshots/step-04-metrics.png exists on disk, `file docs/screenshots/step-04-metrics.png` reports 'PNG image data', and file size is > 5KB."
    why_human: "PREREQ-02 requires a manually-captured screenshot. The docs/screenshots/step-04-metrics.png file does NOT yet exist (confirmed by filesystem check). This is the only remaining unclosed ROADMAP Success Criterion (#4 partial). No programmatic path can substitute for the screenshot capture."
---

# Phase 10: Prerequisites & Stack Decomposition Verification Report

**Phase Goal:** The workshop stack is reset to a production-shaped baseline — five pinned containers replace grafana/otel-lgtm, the runtime circular-reference bug is fixed, and all existing dashboards and signals work identically to v1.0.
**Verified:** 2026-05-03T03:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | Both producer-service and consumer-service start without BeanCurrentlyInCreationException; @Autowired field gone and cycle eliminated | VERIFIED | `this.openTelemetry = sdk` present at producer line 216, consumer line 224; `private OpenTelemetry openTelemetry` (non-@Autowired) at producer line 112, consumer line 120; `@Autowired` text absent from both files; 10-01-SMOKE-RESULTS confirms "Started ProducerApplication" and "Started ConsumerApplication" with ABSENT cycle exception |
| SC2 | `mise run infra:up` starts exactly 5 new containers with no grafana/otel-lgtm present; all 5 image tags are exact patch versions | VERIFIED | `docker compose config --services` returns exactly 10 services (5 data + 5 observability); no `grafana/otel-lgtm` substring in docker-compose.yml (only in comments); all 5 obs images pin to exact patch: `otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, `grafana/loki:3.7.1`, `grafana/grafana:13.0.1`; `mise run verify:images` passed in smoke run |
| SC3 | POST /orders produces trace in Tempo, metric in Mimir, log in Loki; no change to OTEL_EXPORTER_OTLP_ENDPOINT or OtelSdkConfiguration | VERIFIED | 10-05-SMOKE-RESULTS: Tempo returned 2 traces for service.name=order-producer; Mimir returned 2 series for orders_created_total; Loki returned 10 log lines with trace_id label PRESENT; both Java files still contain `http://localhost:4317` as DEFAULT_OTLP_ENDPOINT |
| SC4-a | ose-otel-demo Grafana dashboard loads at :3000 with all panels rendering (no "Datasource not found") | UNCERTAIN — human needed | verify:datasources passes (3 UIDs: loki, prometheus, tempo confirmed provisioned); datasources.yaml contains verbatim lgtm UIDs; dashboard JSONs untouched; direct visual panel render NOT confirmed — deferred human-verify checkpoint from 10-05-PLAN.md Task 2 was not completed by human |
| SC4-b | Deferred step-04-metrics.png screenshot captured and added to docs/screenshots/ (PREREQ-02) | FAILED | File `docs/screenshots/step-04-metrics.png` does NOT exist on disk (filesystem check confirms MISSING); 10-05-SMOKE-RESULTS.md explicitly records "step-04-metrics.png captured: DEFERRED" with an open verification gap |
| SC5 | Mimir container logs show zero 401 errors after POST /orders; metrics appear within ~10s | VERIFIED | 10-05-SMOKE-RESULTS: "Mimir 401 / no-org-id hits in container logs: 0"; mimir-config.yaml uses `multitenancy_enabled: false` (verified in file at line 18); `auth_enabled` absent from mimir-config.yaml |
| STACK-03 invariant | OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 default unchanged in both OtelSdkConfiguration files | VERIFIED | grep confirms `DEFAULT_OTLP_ENDPOINT = "http://localhost:4317"` at producer line 96, consumer line 105; OTEL_EXPORTER_OTLP_ENDPOINT set to http://localhost:4317 in mise.toml [env] block |

**Score:** 6/7 truths verified (SC4-b FAILED/MISSING; SC4-a UNCERTAIN pending human visual check)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `producer-service/.../OtelSdkConfiguration.java` | D-12 non-@Autowired field + inline-assign (PREREQ-01) | VERIFIED | `private OpenTelemetry openTelemetry` at line 112; `this.openTelemetry = sdk` at line 216; no `@Autowired` annotation on field |
| `consumer-service/.../OtelSdkConfiguration.java` | Identical D-12 fix (TRACE-01/DOC-05) | VERIFIED | `private OpenTelemetry openTelemetry` at line 120; `this.openTelemetry = sdk` at line 224; no `@Autowired` annotation on field |
| `infra/observability/otelcol-config.yaml` | OTLP ingress :4317/:4318 + 3 export pipelines + migrated scrape config | VERIFIED | 189 lines; 18 WHY: comments; `otlp_http/tempo`, `otlp_http/loki`, `prometheusremotewrite/mimir` exporters; all 3 scrape jobs migrated (rabbitmq:15692, redis-exporter:9121, postgres-exporter:9187); Docker service-name URLs confirmed |
| `infra/observability/tempo-config.yaml` | Single-binary Tempo with metrics_generator → Mimir PRW | VERIFIED | 90 lines; `http://mimir:9009/api/v1/push`, `send_exemplars: true`, `/var/tempo/wal`, `/var/tempo/blocks`, `http_listen_port: 3200` |
| `infra/observability/mimir-config.yaml` | Single-binary Mimir with multitenancy_enabled: false | VERIFIED | 69 lines; `multitenancy_enabled: false` at line 18; `auth_enabled` ABSENT; `target: all`; `http_listen_port: 9009` |
| `infra/observability/loki-config.yaml` | Single-binary Loki with ruler pre-enabled | VERIFIED | 83 lines; `auth_enabled: false`; `ruler:` block present; `directory: /loki/rules`; `rules_directory: /loki/rules-cache` (Pitfall 7 separation); `evaluation_interval: 1m`; `http://mimir:9009/api/v1/push` |
| `infra/observability/loki-rules/.gitkeep` | Empty placeholder for git-tracked rules dir | VERIFIED | File exists (confirmed by filesystem check) |
| `grafana/datasources.yaml` | 3 datasources with verbatim lgtm UIDs (prometheus/tempo/loki) + D-02 cross-signal datalinks | VERIFIED | 125 lines; `uid: prometheus` (NOT mimir); `uid: tempo`; `uid: loki`; `tracesToLogsV2`, `tracesToMetrics`, `serviceMap`, `derivedFields`, `exemplarTraceIdDestinations` all present |
| `grafana/dashboards/dashboards.yaml` | options.path updated to /var/lib/grafana/dashboards | VERIFIED | `path: /var/lib/grafana/dashboards` at line 40; old lgtm path ABSENT |
| `docker-compose.yml` | 10 services (5 data + 5 obs) + 5 named volumes + exact-patch images | VERIFIED | 280 lines; 10 services confirmed via `docker compose config --services`; 5 named volumes (tempo-data, tempo-wal, mimir-data, loki-data, grafana-data); lgtm-data ABSENT; all D-10 host ports mapped; grafana carries all 5 GF_* env vars |
| `mise.toml` | preflight 14 ports (D-11) + verify:datasources + verify:images | VERIFIED | Port list `3000 4317 4318 5672 15672 15692 6379 5432 3200 9009 3100 13133 8888 8889` at line 65; `[tasks."verify:datasources"]` at line 310 with `ATTEMPTS=6`/`SLEEP_SECS=5` retry loop; `[tasks."verify:images"]` at line 385 |
| `grafana/prometheus.yaml` | DELETED (D-15 — scrape config migrated to otelcol-config.yaml) | VERIFIED | File does not exist on disk; git confirms deletion |
| `README.md` | Step 10 section with tag callout, port table, debug commands, verify-task callouts | VERIFIED | `## Step 10: Stack Decomposition` present; `step-10-collector-decompose` present; port table present; 3 curl debug commands; `mise run verify:images` and `mise run verify:datasources` mentioned; `multitenancy_enabled: false`, `GF_AUTH_ANONYMOUS_ENABLED`, `tempo-wal` all present |
| `docs/screenshots/step-04-metrics.png` | Captured metrics panel screenshot (PREREQ-02) | MISSING | File does NOT exist on disk; 10-05-SMOKE-RESULTS acknowledges "DEFERRED" with open verification gap |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| docker-compose otel-collector | infra/observability/otelcol-config.yaml | bind-mount `./infra/observability/otelcol-config.yaml:/etc/otelcol-contrib/config.yaml:ro` | WIRED | Present at docker-compose.yml line 105 |
| docker-compose tempo | infra/observability/tempo-config.yaml + tempo-data + tempo-wal volumes | bind-mount + 2 named volumes | WIRED | `tempo-config.yaml:/etc/tempo.yaml:ro`; `tempo-data:/var/tempo`; `tempo-wal:/var/tempo/wal` |
| docker-compose grafana | grafana/datasources.yaml + dashboards.yaml + dashboard JSONs | 3 bind-mounts at Pitfall 8 paths | WIRED | `datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml:ro`; `dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml:ro`; `./grafana/dashboards:/var/lib/grafana/dashboards:ro` |
| otelcol-config exporters | tempo, mimir, loki containers | Docker service-name DNS | WIRED | `http://tempo:4318`, `http://mimir:9009/api/v1/push`, `http://loki:3100/otlp` confirmed in otelcol-config.yaml |
| tempo metrics_generator | mimir | remote_write `http://mimir:9009/api/v1/push` | WIRED | Confirmed in tempo-config.yaml |
| loki ruler | mimir | `clients.mimir.url: http://mimir:9009/api/v1/push` | WIRED | Confirmed in loki-config.yaml |
| grafana/datasources.yaml UID=prometheus | dashboard JSONs uid=prometheus | verbatim UID match (D-01) | WIRED | Dashboard JSONs untouched; `uid: prometheus` (not mimir) in datasources.yaml |
| mise.toml verify:datasources | Grafana HTTP API + 3 UID expectations | curl + jq + diff gate | WIRED | verify:datasources task at line 310; 6×5s retry loop; "loki\nprometheus\ntempo" expected; passed in smoke run |
| mise.toml verify:images | docker-compose.yml image: lines | bash regex grep | WIRED | verify:images task at line 385; passed in smoke run (10 images, all pinned) |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| otelcol-config.yaml traces pipeline | OTLP spans from apps | `receivers: [otlp]` → `exporters: [otlp_http/tempo]` | YES — smoke confirmed 2 traces in Tempo | FLOWING |
| otelcol-config.yaml metrics pipeline | OTLP metrics + prometheus scrapes | `receivers: [otlp, prometheus]` → `exporters: [prometheusremotewrite/mimir]` | YES — smoke confirmed 2 series in Mimir | FLOWING |
| otelcol-config.yaml logs pipeline | OTLP logs from apps | `receivers: [otlp]` → `exporters: [otlp_http/loki]` | YES — smoke confirmed 10 log lines in Loki with trace_id label | FLOWING |
| grafana/datasources.yaml | 3 provisioned datasources | Grafana startup provisioning reads YAML | YES — verify:datasources confirmed 3 UIDs match | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Evidence | Status |
|----------|----------|--------|
| 10 services start from docker-compose.yml | `docker compose config --services` returns 10 services | PASS |
| All 5 obs image tags are exact-patch | grep of image: lines: all 5 obs images have full patch versions | PASS |
| OTLP endpoint unchanged (STACK-03) | Both Java files contain `http://localhost:4317` at DEFAULT_OTLP_ENDPOINT | PASS |
| Mimir uses multitenancy_enabled (not auth_enabled) | mimir-config.yaml line 18: `multitenancy_enabled: false`; auth_enabled ABSENT | PASS |
| Datasource UIDs are prometheus/tempo/loki (D-01) | datasources.yaml: uid: prometheus, uid: tempo, uid: loki; no uid: mimir | PASS |
| grafana/prometheus.yaml deleted (D-15) | File does not exist on disk | PASS |
| D-12 cycle fix in both Java files | this.openTelemetry = sdk at producer:216, consumer:224; no @Autowired on field | PASS |
| 3-signal end-to-end smoke (STACK-03) | 10-05-SMOKE-RESULTS: traces ≥1 in Tempo, metrics ≥1 in Mimir, logs ≥1 in Loki | PASS |
| Mimir zero 401 errors (STACK-05) | 10-05-SMOKE-RESULTS: "Mimir 401/no-org-id hits: 0" | PASS |
| docs/screenshots/step-04-metrics.png exists (PREREQ-02) | File does NOT exist on disk | FAIL |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PREREQ-01 | 10-01 | Both services start without BeanCurrentlyInCreationException; @Autowired field gone | SATISFIED | D-12 fix verified in both Java files; smoke confirms boot to "Started" with cycle absent |
| PREREQ-02 | 10-05 | step-04-metrics.png captured and visible in docs/screenshots/ | BLOCKED | File does NOT exist; explicitly deferred in 10-05-SMOKE-RESULTS Task 2 |
| STACK-01 | 10-04 | 5 new containers running (otel-collector, tempo, mimir, loki, grafana); lgtm removed | SATISFIED | docker-compose.yml has exactly 10 services; lgtm absent; 5 obs containers present |
| STACK-02 | 10-04 | All 5 image tags are exact patch versions; verify:images enforces | SATISFIED | All 5 obs image tags are full patch versions; verify:images passed in smoke run |
| STACK-03 | 10-01, 10-04 | OTEL_EXPORTER_OTLP_ENDPOINT unchanged; Collector listens on :4317/:4318 | SATISFIED | Java files unchanged for OTLP endpoint; Collector maps 4317:4317 and 4318:4318 |
| STACK-04 | 10-03, 10-05 | ose-otel-demo dashboard renders all panels without "Datasource not found" | NEEDS HUMAN | verify:datasources confirms UID contract; dashboard JSONs untouched; visual panel render NOT confirmed |
| STACK-05 | 10-02, 10-05 | Mimir runs with multitenancy_enabled: false; zero 401 errors after POST | SATISFIED | mimir-config.yaml line 18; smoke: 0 Mimir 401 errors |

---

### Anti-Patterns Found

| File | Issue | Severity | Impact |
|------|-------|----------|--------|
| `docker-compose.yml:35` | `valkey/valkey:8.1-alpine` is a minor-only floating tag (WR-02 from code review) | Warning | verify:images regex does not catch `:NN.NN-suffix` patterns; Valkey may drift across cohorts |
| `docker-compose.yml:49` | `postgres:17-alpine` is a major-only floating tag (WR-02 from code review) | Warning | verify:images regex does not catch `:NN-suffix` patterns; Postgres may drift across cohorts |
| `docker-compose.yml:111` | Port 8889 mapped but no prometheusexporter pipeline configured in otelcol-config.yaml (WR-03) | Warning (advisory) | `curl localhost:8889/metrics` returns connection refused; README port table is technically incorrect for Phase 10 only |
| `mise.toml:66` | `ss -tln` is Linux-only; macOS silently skips all port checks (WR-04) | Warning (advisory) | preflight always passes on macOS even when ports are occupied |
| `README.md` | Step 10 mentions `step-04-metrics.png` as existing but it does not (WR-05 from code review) | Warning | Workshop attendees following README Step 10 cannot view the referenced screenshot |
| `producer/consumer OtelSdkConfiguration.java` | `@PreDestroy uninstallLogbackAppender()` runs AFTER SDK close() per Spring lifecycle (WR-01) | Warning (advisory, pre-existing) | Stated guarantee about shutdown race protection is incorrect, but does not affect Phase 10 goal (cycle fix + signal flow). Not introduced by Phase 10. |

Note: WR-01, WR-02, WR-03, WR-04 are advisory non-blocking findings from the code review (10-REVIEW.md). Only WR-05 (missing screenshot) directly blocks a ROADMAP Success Criterion. The valkey/postgres floating tags (WR-02) are a STACK-02 invariant concern but do not block Phase 10 since the five *observability* images are all properly pinned.

---

### Human Verification Required

#### 1. Dashboard Panel Render (STACK-04 visual confirmation)

**Test:** With the stack running (`mise run infra:up`), producer and consumer started, and at least one `POST /orders` sent (`mise run demo:order`), open `http://localhost:3000/d/ose-otel-demo/ose-otel-demo` in a browser. Expand both rows of panels.

**Expected:** Every panel renders without a red "Datasource not found" error banner. Panels may show empty data or live data — either is acceptable. If all panels show "Datasource not found", something in the UID contract is broken and STACK-04 has failed.

**Why human:** Panel render correctness requires a live Grafana UI and a visual check. The programmatic `verify:datasources` gate confirms the 3 UIDs are provisioned, but cannot confirm that the dashboard JSON panel queries actually resolve against those datasources in the Grafana 13 rendering engine.

#### 2. PREREQ-02 Screenshot Capture

**Test:** While viewing the ose-otel-demo dashboard (same session as item 1), locate the metrics panel (orders_created_total counter or RED metrics row). Capture a screenshot of that panel only using the OS screenshot tool. Save as `/home/coto/dev/demo/ose-otel-demo/docs/screenshots/step-04-metrics.png`. Verify: `file docs/screenshots/step-04-metrics.png` reports "PNG image data" and file size > 5KB.

**Expected:** `docs/screenshots/step-04-metrics.png` exists on disk as a valid PNG showing the metrics panel with live data.

**Why human:** This is a manual one-shot D-13 capture — the screenshot cannot be automated. The file currently does not exist, which is the only remaining ROADMAP Success Criterion gap in this phase.

---

### Gaps Summary

The automated portion of Phase 10 is fully delivered. All code changes (PREREQ-01 D-12 fix, five backend YAML configs, Grafana provisioning, docker-compose decomposition, mise extensions) are verified in the codebase. The 3-signal end-to-end smoke passed. Five of six ROADMAP Success Criteria are verifiably closed.

The single remaining gap is PREREQ-02 (docs/screenshots/step-04-metrics.png missing). This is acknowledged in 10-05-SMOKE-RESULTS.md as an explicit orchestrator-checkpoint deferred decision. SC4's visual panel render confirmation is a secondary UNCERTAIN item that depends on human Grafana inspection.

Once a human confirms (1) all dashboard panels render without errors and (2) the metrics screenshot is captured and committed, this phase can be declared fully passed.

---

_Verified: 2026-05-03T03:00:00Z_
_Verifier: Claude (gsd-verifier)_
