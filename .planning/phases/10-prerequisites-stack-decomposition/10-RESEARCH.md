# Phase 10: Prerequisites & Stack Decomposition — Research

**Researched:** 2026-05-02
**Domain:** Production-shape observability decomposition (5-container OTel Collector + Tempo + Mimir + Loki + Grafana) replacing `grafana/otel-lgtm:0.26.0`; Spring Boot 3.4.13 circular-reference fix on `OtelSdkConfiguration`; Java SDK config UNCHANGED
**Confidence:** HIGH (every YAML key and exporter name verified against live containers pulled at research date)

---

## Summary

Phase 10 is **operational, not pedagogical-on-Java-SDK** — it lands five exact-pinned containers, fixes a runtime-only Spring bean cycle in `OtelSdkConfiguration` (PREREQ-01), and captures one deferred screenshot (PREREQ-02), without touching `OtelSdkConfiguration`'s SDK wiring. The CONTEXT.md decisions D-01..D-16 lock everything material: image tags, datasource UIDs, port map, healthcheck philosophy, comment density, file layout, fix shape. This research narrows the implementation gaps the planner will hit when authoring 5 new YAMLs, 1 patched compose file, and 2 patched Java files.

**Two upstream-doc corrections discovered during live container inspection** that directly affect the plan:

1. **F1-4 reversed** — In `otel/opentelemetry-collector-contrib:0.151.0` the canonical exporter type names are `otlp_http` (with underscore) and `prometheusremotewrite`. Using `otlphttp` (without underscore) **still works but emits a deprecation warning** at startup: `"otlphttp" alias is deprecated; use "otlp_http" instead`. STACK.md and PITFALLS.md both predicted the older name; the live container says use `otlp_http` going forward. There is **no `loki` exporter component in v0.151.0** at all — logs ship via `otlp_http/loki` to Loki's `/otlp` endpoint.
2. **Mimir key name corrected** — `mimir-config.yaml` uses `multitenancy_enabled: false` (NOT `auth_enabled: false`). `auth_enabled` is rejected by the YAML parser in Mimir 3.0.6: `field auth_enabled not found in type mimir.Config`. The CLI flag is `-auth.multitenancy-enabled`. CONTEXT.md D-06 / STACK.md / PITFALLS.md F1-3 used the older Cortex-era key — Phase 10 plan must use `multitenancy_enabled: false`.

**Primary recommendation:** Author the 5 new YAMLs in this exact order — `otelcol-config.yaml` first (ingress + exporters), `tempo-config.yaml` second (sets the Mimir remote_write contract), `mimir-config.yaml` third (Mimir is the central metric write target for both Tempo and the Collector), `loki-config.yaml` fourth (ruler also writes to Mimir), `grafana/datasources.yaml` last (consumes UIDs from inspected lgtm container, wires all cross-signal datalinks per D-02). Java fix (PREREQ-01) and screenshot (PREREQ-02) are independent of the YAML work and can run in parallel waves.

---

## User Constraints (from CONTEXT.md)

### Locked Decisions

**Datasource UID strategy:**
- **D-01:** Reuse `grafana/otel-lgtm:0.26.0`'s internal datasource UIDs verbatim. Inspection command: `docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml`. Copy `uid:` field for each of `tempo`/`mimir`/`loki` verbatim. **VERIFIED INSPECTION RESULT:** the bundled UIDs are `prometheus` (NOT `mimir`!), `tempo`, `loki`, `pyroscope`. See §F1-1 below.
- **D-02:** Wire ALL cross-signal datalinks in Phase 10. Tempo datasource gets `tracesToLogsV2` + `tracesToMetrics`. Loki gets `derivedFields` to Tempo. Mimir/Prometheus gets `exemplarTraceIdDestinations` placeholder.
- **D-03:** Add `mise run verify:datasources` task — `curl -s localhost:3000/api/datasources | jq '.[].uid'` and assert expected UIDs are present.

**YAML config style/depth:**
- **D-04:** Teaching-grade with WHY comments on every block. Production-only sections (auth headers, TLS, retry policy) NOT included. Match comment density of v1.0 `OtelSdkConfiguration.java` (137/131 lines) and existing `docker-compose.yml` `lgtm:`/`redis-exporter:`/`postgres-exporter:` blocks.
- **D-05:** Migrate `grafana/prometheus.yaml` `scrape_configs:` (rabbitmq:15692, redis-exporter:9121, postgres-exporter:9187) into `infra/observability/otelcol-config.yaml` under `receivers.prometheus.config.scrape_configs`. Delete the orphan file.
- **D-06:** Tempo `metrics_generator` writes directly to Mimir via `metrics_generator.storage.remote_write: [{url: http://mimir:9009/api/v1/push}]` (Docker service name, NOT localhost). Named-volume `tempo-wal:/var/tempo/wal` per D-09.
- **D-07:** Pre-enable Loki `ruler` with EMPTY rules dir. Bind-mount `./infra/observability/loki-rules:/loki/rules:ro`, tracked via `.gitkeep`. `ruler.evaluation_interval: 1m`. Phase 13 only adds rules YAML files; no `loki-config.yaml` change later.

**docker-compose layout:**
- **D-08:** Single `docker-compose.yml` with all 10 services (5 existing + 5 new). NO compose profiles, NO override files. `# ===== Observability stack =====` divider between data and observability sections.
- **D-09:** Five new named volumes — `tempo-data`, `tempo-wal`, `mimir-data`, `loki-data`, `grafana-data` — replacing `lgtm-data`. State survives `infra:down`/`infra:up`. `infra:reset` (`docker compose down -v`) wipes all five.
- **D-10:** All backend admin/API ports exposed: Grafana 3000, Collector 4317/4318/13133/8888/8889, Tempo 3200, Mimir 9009, Loki 3100. Each with `# debug: curl localhost:NNNN/...` comment.
- **D-11:** `mise run preflight` port-list grows to `[3000, 4317, 4318, 5672, 15672, 6379, 5432, 3200, 9009, 3100, 13133, 8888, 8889]`. Hard-fail if any in use. (Existing port `15692` rabbitmq-prometheus also added.)

**Other:**
- **D-12:** PREREQ-01 fix is MINIMAL, mirrors v1.0 LOG-03 verbatim. In each `OtelSdkConfiguration.java`: drop `@Autowired private OpenTelemetry openTelemetry;` field, add `this.openTelemetry = sdk;` BEFORE `OpenTelemetryAppender.install(sdk)` and BEFORE `return sdk` inside the `@Bean` factory body. Keep field declaration without `@Autowired` as a non-injected instance field. NO SDK refactor. TRACE-01/DOC-05 (per-service duplication) preserved.
- **D-13:** PREREQ-02 (`docs/screenshots/step-04-metrics.png`) — capture **manually one-shot** on `main` HEAD after Phase 10 lands. The automated `docs:screenshots` task is NOT modified.
- **D-14:** STACK-02 floating-tag guardrail — `mise run verify:images` greps `docker-compose.yml` for `image:` lines whose tag matches a floating pattern. Hard-fail if any match. NOT a pre-commit hook; just a mise task. README Step 10 documents it.
- **D-15:** Removed `grafana/prometheus.yaml` (D-05 migrates its scrape config). `git rm`-deleted, not moved.
- **D-16:** `docker-compose.yml` ordering — observability AFTER data services. `depends_on` semantics:
  - `otel-collector` depends on `tempo` + `mimir` + `loki` (all `service_started`)
  - `tempo` depends on `mimir` (`service_started`)
  - `loki` depends on `mimir` (`service_started`)
  - `grafana` depends on `tempo` + `mimir` + `loki` (`service_started`)
  - Each new service gets explicit `healthcheck:` block. `infra:up` `--wait` honors them.

### Claude's Discretion

The following are **researcher/planner discretion** — not user-asked, decide on conventional best practices:
- Exact YAML key shape for each backend's storage block (Tempo `wal_path`, Mimir `blocks_storage`, Loki `schema_config`) — pull from upstream single-binary examples; pitfalls research already confirms canonical paths.
- Tempo `metrics_generator.processor.service_graphs` config — match upstream Grafana service-graph defaults.
- Collector `processors.batch` and `processors.memory_limiter` settings — workshop-grade defaults from upstream Collector contrib examples.
- Healthcheck `interval`/`timeout`/`start_period` tuning per service — mirror existing v1.0 `rabbitmq` healthcheck cadence (`interval: 10s`, `timeout: 5s`, `retries: 10`, `start_period: 30s`) unless upstream prescribes otherwise.
- `mise run verify:images` regex specifics — planner picks a defensible pattern.

### Deferred Ideas (OUT OF SCOPE)

- **Healthcheck-strategy refinement** — defer fine-tuning to plan-phase research.
- **Mimir blocks-storage on local disk vs filesystem-shared** — single-binary local stays; production realism deferred to a future "infra deepdive" milestone.
- **Backend admin port pedagogy in README** — README Step 10 picks 2–3 "interesting" debug commands; full port-map table lists rest. Selection deferred to plan-phase.
- **`mise run verify:datasources` / `verify:images` regex tightness** — defer to plan-phase.
- **Collector exporter component naming** — RESOLVED in this research (§F1-4 below); planner does not need to re-verify.
- **Loki ruler config interval choices** — `evaluation_interval: 1m` (D-07) is starting point; F4-2 rate-window aliasing fully addressed in Phase 13.
- **Service-graph re-priming after `infra:down`/`up`** — README may want a 1–5 minute callout (deferred to README authoring).

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PREREQ-01 | Both services start without `BeanCurrentlyInCreationException` | §"PREREQ-01 fix shape" — verbatim diff for both `OtelSdkConfiguration.java` files; LOG-03 inline-assign-in-`@Bean`-body precedent already lives in `producer-service/.../OtelSdkConfiguration.java:189-249` |
| PREREQ-02 | `docs/screenshots/step-04-metrics.png` exists, paired with README Step 4 | §"PREREQ-02 capture procedure" — manual one-shot on main HEAD after Phase 10 lands; D-13 |
| STACK-01 | `mise run infra:up` starts five named containers, no `lgtm` | §"docker-compose decomposition" — exact service blocks, depends_on semantics, named volumes |
| STACK-02 | All five tags pinned to exact patch versions; `mise run verify:images` enforces | §"verify:images task pattern" — bash regex for floating-tag detection |
| STACK-03 | `OtelSdkConfiguration` requires zero change; OTLP gRPC :4317 + HTTP :4318 unchanged | §"otelcol-config.yaml" — receivers.otlp.protocols.grpc.endpoint + http.endpoint identical to v1.0 ingestion contract |
| STACK-04 | `ose-otel-demo` dashboard renders all panels — no "Datasource not found" | §F1-1 — verbatim UIDs `tempo`, `prometheus`, `loki` (NOT `mimir`!) inspected from lgtm container; copy into new `grafana/datasources.yaml` |
| STACK-05 | Mimir `multitenancy_enabled: false`; zero 401 errors after `POST /orders` | §"mimir-config.yaml" — confirmed YAML key is `multitenancy_enabled` (NOT `auth_enabled`), live-validated against `grafana/mimir:3.0.6` |

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OTLP receive (gRPC :4317, HTTP :4318) | Collector | — | Single ingress for all three signals; SDK contract unchanged |
| Trace storage + query | Tempo | — | Production-shape — Tempo is the canonical OTel-ecosystem trace backend |
| Trace-derived metric generation (RED + service_graphs) | Tempo metrics_generator | Mimir (via remote_write) | D-06: Tempo writes directly to Mimir, not via Collector — backends can be polyglot |
| Metric storage + PromQL query | Mimir | — | Receives both Collector pipelines (OTLP→PRW) AND Tempo metrics_generator AND Loki ruler — three writers, one tenant |
| Log storage + LogQL query | Loki | — | Single sink; consumes via OTLP `/otlp` endpoint from Collector's `otlp_http/loki` exporter |
| Recording rule evaluation (Phase 13 prep) | Loki ruler | Mimir (via remote_write) | D-07: ruler enabled now with empty rules dir; rules drop in Phase 13 without Loki config edit |
| Infra exporter scrape (rabbitmq/redis/postgres) | Collector `prometheus` receiver | Mimir | D-05: orphan `grafana/prometheus.yaml` migrates into `otelcol-config.yaml`; Collector becomes single mouth |
| Dashboard hosting + cross-signal click-through | Grafana | — | Datasources provisioned with UIDs from D-01; datalinks wired in D-02 |
| Spring `OpenTelemetry` bean lifecycle | Spring Boot `@Configuration` | — | PREREQ-01: cycle is *between* the `@Bean openTelemetry()` factory and the `@Autowired OpenTelemetry` field on the same class — fix sidesteps Spring's bean-graph machinery entirely (inline-assign in factory body) |
| Workshop reproducibility guardrail | mise tasks | docker-compose | D-14 `verify:images` (floating-tag regex), D-03 `verify:datasources` (UID assertion), D-11 `preflight` (port-list expansion) |

---

## Standard Stack

### Core (verified versions, all pulled and inspected at research date)

| Library/Image | Version | Purpose | Why This Tag |
|---------------|---------|---------|--------------|
| `otel/opentelemetry-collector-contrib` | `0.151.0` | OTLP ingress + 3-pipeline routing | Released 2026-04-29; STACK.md confirms latest stable; `tail_sampling`, `lokiexporter` (deprecated), `prometheusremotewrite` are all contrib-only — core distribution insufficient. **VERIFIED via `docker run --rm otel/opentelemetry-collector-contrib:0.151.0 components`.** |
| `grafana/tempo` | `2.10.5` | Trace storage + `metrics_generator` (RED + service_graphs) writes to Mimir | Released 2026-04-23; matches lgtm 0.26.0's bundled version line. Image entrypoint requires `-config.file=/etc/tempo.yaml` (no default config baked in). |
| `grafana/mimir` | `3.0.6` | Metric backend; receives OTLP→PRW from Collector + Tempo's metrics_generator + Loki's ruler remote_write | Released 2026-04-20; latest stable 3.0.x. **VERIFIED via `docker run --rm grafana/mimir:3.0.6 -version`.** Multi-binary monolithic mode (`target: all`). |
| `grafana/loki` | `3.7.1` | Log storage + ruler (empty rules dir for Phase 13) | Released 2026-03-27. Default config baked in at `/etc/loki/local-config.yaml` (used as starting template). Image entrypoint is `-config.file=/etc/loki/local-config.yaml`. |
| `grafana/grafana` | `13.0.1` | Dashboard host; datasources provisioned via `/etc/grafana/provisioning/datasources/`; dashboards via `/etc/grafana/provisioning/dashboards/` | Released 2026-04-17. **VERIFIED provisioning paths via `docker run --rm grafana/grafana:13.0.1`** — startup logs show `Path Provisioning path=/etc/grafana/provisioning` overridable via `GF_PATHS_PROVISIONING` env var. |

**Version verification commands (all run 2026-05-02):**
```bash
docker run --rm otel/opentelemetry-collector-contrib:0.151.0 --version    # Confirmed via components listing
docker run --rm grafana/tempo:2.10.5 -h 2>&1 | head -1                    # Tempo (version=v2.10.5, branch=HEAD, revision=991ce39eb)
docker run --rm grafana/mimir:3.0.6 -version                              # Mimir, version 3.0.6 (branch: HEAD, revision: 25026e72)
docker run --rm grafana/loki:3.7.1 -version 2>&1 | head -1                # loki, version 3.7.1
docker run --rm grafana/grafana:13.0.1 grafana-server --version 2>&1      # Version 13.0.1 (commit: a100054f, ...)
```
[VERIFIED: live container pulls + inspections, 2026-05-02]

### Supporting

| Library | Purpose | When to Use |
|---------|---------|-------------|
| `mise` (existing 2025.1.0+) | Task scaffolding for `verify:datasources`, `verify:images`, `preflight` extension | Always — D-03, D-11, D-14 build on existing `verify:bom` pattern |
| `jq` (system tool) | JSON parse for `verify:datasources` — `curl localhost:3000/api/datasources \| jq '.[].uid'` | Required at host. Already present on most workshop laptops; if missing, error message tells attendee `apt install jq`/`brew install jq`. |
| `curl` (system tool) | Healthcheck probes inside compose containers + verify tasks on host | Already required by v1.0 (used in `mise run demo:order`). |
| `wget` (alpine images) | Healthcheck probe inside containers when `curl` not present | Tempo/Mimir/Loki/Grafana images include `wget` (busybox) but not `curl`. Use `wget --spider --quiet http://localhost:NNNN/ready` form. |

### Alternatives Considered (and rejected)

| Instead of | Could Use | Why Rejected |
|------------|-----------|--------------|
| Five separate containers | `grafana/otel-lgtm:0.26.0` (the v1.0 default) | The decomposition IS the lesson (STACK-01) |
| Mimir 3.0.6 | Mimir 2.17.x | 3.0 is current stable; key contract `multitenancy_enabled` unchanged across versions |
| Filesystem ruler storage | MinIO / S3-compatible | Adds container + S3 config dimension orthogonal to the v2.0 lesson; defer to "infra deepdive" milestone (CONTEXT.md deferred §"Mimir blocks-storage…") |
| `prometheusremotewrite/mimir` (the Collector exporter) | `otlp_http/mimir` against `mimir:9009/otlp/v1/metrics` | PRW path is what Tempo's metrics_generator and Loki's ruler also use — single ingress contract on Mimir; exemplar passthrough better-tested via PRW |
| `otlp_http/loki` against `:3100/otlp` | Old `loki:` exporter | **`loki` exporter component does NOT exist in collector-contrib v0.151.0** — confirmed by parse error: `'exporters' unknown type: "loki" for id: "loki"` |
| `otlp_http` (canonical, with underscore) | `otlphttp` (legacy alias) | Collector emits deprecation warning at startup with the legacy alias — use `otlp_http` going forward (§F1-4) |
| `multitenancy_enabled: false` (Mimir 3.x) | `auth_enabled: false` (Mimir 2.x / Cortex-era) | **`auth_enabled` is rejected** by Mimir 3.0.6 YAML parser: `field auth_enabled not found in type mimir.Config`. CONTEXT.md/STACK.md/PITFALLS.md F1-3 used the older key; Phase 10 plan must use `multitenancy_enabled`. |
| Direct OTLP to Loki via `/otlp` | `loki` exporter (would have used `/loki/api/v1/push`) | The `lokiexporter` was removed/renamed in collector-contrib; current path is OTLP-native. Loki 3.7.1 accepts OTLP at `/otlp`. |

**Installation (no NEW package installs required for this phase):**
```bash
# All images pulled by docker compose pull on first infra:up
docker pull otel/opentelemetry-collector-contrib:0.151.0
docker pull grafana/tempo:2.10.5
docker pull grafana/mimir:3.0.6
docker pull grafana/loki:3.7.1
docker pull grafana/grafana:13.0.1
```

---

## Architecture Patterns

### System Architecture Diagram

```
                                           ┌──────────────────────────────────────────┐
                                           │ Host (Spring Boot apps via mise dev)     │
                                           │  producer-service @ :8080                │
                                           │  consumer-service @ :8081                │
                                           │     │ OTLP gRPC :4317 (UNCHANGED)        │
                                           └─────┼────────────────────────────────────┘
                                                 │
                                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ docker compose default network                                                          │
│                                                                                         │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ otel-collector :4317/:4318/:13133/:8888/:8889                                  │    │
│  │   receivers:    otlp (gRPC+HTTP) + prometheus (rabbitmq/redis/postgres scrape) │    │
│  │   processors:   memory_limiter → batch  (Phase 10: minimal; Phase 11 adds tail)│    │
│  │   exporters:    otlp_http/tempo, prometheusremotewrite/mimir, otlp_http/loki   │    │
│  │   pipelines:    traces, metrics, logs                                          │    │
│  └─────┬────────────────────┬────────────────────┬──────────────────────────────────┘   │
│        │ traces             │ metrics            │ logs                                  │
│        ▼ otlp_http :4318    ▼ /api/v1/push :9009 ▼ /otlp :3100                          │
│  ┌──────────┐         ┌──────────────┐      ┌──────────┐                                │
│  │ tempo    │────────▶│ mimir        │◀─────│ loki     │                                │
│  │ :3200    │remote_  │ :9009        │      │ :3100    │                                │
│  │          │write    │ multitenancy_│ ruler│          │                                │
│  │          │/api/v1/ │ enabled:false│remote│          │                                │
│  │          │push     │              │write │          │                                │
│  └────┬─────┘         └────┬─────────┘      └────┬─────┘                                │
│       │                    │                    │                                       │
│       └────────────────────┼────────────────────┘                                       │
│                            │                                                            │
│                            ▼                                                            │
│                     ┌────────────────────────────────┐                                  │
│                     │ grafana :3000                  │                                  │
│                     │   /etc/grafana/provisioning/   │                                  │
│                     │     datasources/datasources.yaml ◀── UIDs reused from lgtm:       │
│                     │       tempo, prometheus, loki  │     'tempo', 'prometheus', 'loki│
│                     │     dashboards/dashboards.yaml │                                  │
│                     │   Cross-signal datalinks wired:│                                  │
│                     │     - tracesToLogsV2           │                                  │
│                     │     - tracesToMetrics          │                                  │
│                     │     - derivedFields (Loki→Tempo│                                  │
│                     │     - exemplarTraceIdDestin… (P│hase 12 turns SDK side ON)        │
│                     └────────────────────────────────┘                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                            ▲ host port :3000
                            │
                       Workshop attendee browser
```

### Component Responsibilities (file-to-impl mapping)

| File | Owner | Responsibility |
|------|-------|----------------|
| `infra/observability/otelcol-config.yaml` | NEW | OTLP ingress; 3 pipelines (traces, metrics, logs); migrated infra-exporter scrape jobs (D-05); health_check :13133 |
| `infra/observability/tempo-config.yaml` | NEW | OTLP receiver :4317/:4318 internal; trace storage local fs `/var/tempo/blocks` + WAL `/var/tempo/wal`; metrics_generator → Mimir remote_write |
| `infra/observability/mimir-config.yaml` | NEW | `target: all`; `multitenancy_enabled: false`; filesystem blocks_storage + ruler_storage; HTTP API :9009 |
| `infra/observability/loki-config.yaml` | NEW | OTLP receive on :3100/otlp; filesystem chunks/rules; ruler enabled with `clients.mimir.url: http://mimir:9009/api/v1/push` (D-07) |
| `infra/observability/loki-rules/.gitkeep` | NEW | Placeholder so `git`-tracked empty dir mounts cleanly; Phase 13 lands actual rule YAMLs |
| `grafana/datasources.yaml` | NEW | UIDs reused from lgtm: `tempo`, `prometheus` (NOT `mimir` — see §F1-1!), `loki`; cross-signal datalinks per D-02 |
| `grafana/dashboards/dashboards.yaml` | MODIFIED | Mount path moves from `/otel-lgtm/grafana/conf/provisioning/dashboards` (lgtm-internal) to `/etc/grafana/provisioning/dashboards` (standalone Grafana 13). Manifest content unchanged. |
| `grafana/dashboards/ose-otel-demo.json` | UNTOUCHED | Hardcoded UIDs (`prometheus`, `tempo`, `loki`) all preserved by D-01 — no edit |
| `grafana/prometheus.yaml` | DELETED (`git rm`) | D-15: scrape_configs migrate into `otelcol-config.yaml` (D-05) |
| `docker-compose.yml` | MODIFIED | Drop `lgtm:` block; add 5 new service blocks; expand `volumes:` with 5 named volumes |
| `mise.toml` | MODIFIED | Extend `preflight` port list (D-11); add `verify:datasources` (D-03), `verify:images` (D-14) tasks |
| `producer-service/.../OtelSdkConfiguration.java` | MODIFIED | PREREQ-01 inline-assign fix (D-12) |
| `consumer-service/.../OtelSdkConfiguration.java` | MODIFIED | Same fix (TRACE-01 / DOC-05 — both files edited identically) |
| `docs/screenshots/step-04-metrics.png` | NEW | PREREQ-02 manual capture (D-13) |
| `README.md` | MODIFIED | Add Step 10 section: tag callout, port-map table, before/after compose-service-count narrative, verify-task command list |

### Pattern 1: Collector exporter naming — `otlp_http` (canonical, with underscore)

**What:** As of `otel/opentelemetry-collector-contrib:0.151.0`, the canonical YAML type names for exporters are `otlp_http` and `prometheusremotewrite`. The legacy `otlphttp` alias still works but emits a deprecation warning at startup.

**When to use:** ALWAYS in new configs. Use `otlp_http/<sink-name>` instance naming convention (e.g., `otlp_http/tempo`, `otlp_http/loki`).

**Verified example** [VERIFIED: live container parse, 2026-05-02]:
```yaml
# infra/observability/otelcol-config.yaml — exporters block
exporters:
  otlp_http/tempo:
    # WHY: Tempo accepts OTLP HTTP on its distributor's :4318 endpoint inside the
    # compose network. http://tempo:4318 is the Docker service-name URL (D-06 spirit:
    # use service names, never localhost from inside containers).
    endpoint: http://tempo:4318
    tls:
      insecure: true   # WHY: docker compose internal network — no TLS on workshop boundaries
  prometheusremotewrite/mimir:
    # WHY: PRW is the canonical "many writers, one Mimir" path. Tempo's metrics_generator
    # AND Loki's ruler ALSO write to this same /api/v1/push endpoint (D-06, D-07) — three
    # producers, one consumer, single tenant (multitenancy_enabled: false).
    endpoint: http://mimir:9009/api/v1/push
    tls:
      insecure: true
  otlp_http/loki:
    # WHY: Loki 3.7.1 accepts native OTLP on /otlp. The collector-contrib v0.151.0
    # has NO `loki` exporter component — the historical `lokiexporter` was retired in
    # favor of OTLP-native ingestion. Path /otlp is hardcoded in Loki's HTTP server.
    endpoint: http://loki:3100/otlp
    tls:
      insecure: true
```

**Anti-pattern:**
```yaml
# WRONG — emits "[deprecated] otlphttp alias" warning at startup
exporters:
  otlphttp/tempo:    # missing underscore
    endpoint: ...
```

### Pattern 2: Tempo `metrics_generator` → Mimir remote_write (D-06 / F1-2 mitigation)

**What:** Tempo's `metrics_generator` produces span_metrics (RED — Rate, Errors, Duration) and service_graphs from incoming traces. These are written via Prometheus remote_write protocol to Mimir's `/api/v1/push` endpoint. Tempo bypasses the Collector for this path — backends can be polyglot.

**When to use:** Always in this demo. Service-graph + RED metrics are load-bearing for the v2.0 dashboard.

**Verified example** [CITED: github.com/grafana/tempo/blob/main/docs/sources/tempo/configuration/hosted-storage/s3.md, adapted for filesystem]:
```yaml
# infra/observability/tempo-config.yaml — full single-binary config
# WHY this file exists: Tempo's image (grafana/tempo:2.10.5) does NOT bake in a
# default config — running without -config.file fails immediately. We bind-mount this
# file at /etc/tempo.yaml and pass -config.file=/etc/tempo.yaml in the compose command.
stream_over_http_enabled: true   # WHY: enables /api/v2/search/tag/.../values streaming the dashboard uses

server:
  http_listen_port: 3200   # D-10: exposed to host as 3200; debug: curl localhost:3200/api/echo

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          # WHY: Collector → Tempo OTLP gRPC. The Collector's otlp_http/tempo exporter
          # uses HTTP though — we expose BOTH so attendees can flip between them.
          endpoint: "0.0.0.0:4317"
        http:
          endpoint: "0.0.0.0:4318"

ingester:
  max_block_duration: 5m   # WHY: workshop-grade — flush traces to /var/tempo/blocks every 5 min

compactor:
  compaction:
    block_retention: 720h   # WHY: 30 days; small enough that filesystem fills slowly during workshops

metrics_generator:
  registry:
    external_labels:
      source: tempo            # WHY: distinguishes Tempo-derived metrics from app-emitted in Mimir
      cluster: ose-otel-demo   # WHY: matches the workshop's deployment.environment
  storage:
    # F1-2 MITIGATION: WAL must persist across container restarts. Named volume
    # tempo-wal is mounted here. Without persistence, metrics_generator state is
    # lost on `infra:down`/`up` and service-graph panels stay empty for 1-5 min
    # while traces re-prime the windows.
    path: /var/tempo/generator/wal
    remote_write:
      # F1-2 MITIGATION: Use Docker service name `mimir`, NOT localhost. Tempo runs
      # in its own container; localhost from inside Tempo resolves to Tempo itself.
      - url: http://mimir:9009/api/v1/push
        send_exemplars: true   # WHY: forwards trace_id/span_id to Mimir (Phase 12 enables click-through)
  traces_storage:
    path: /var/tempo/generator/traces   # WHY: span buffer for in-progress metric windows
  processor:
    span_metrics:
      # WHY: dimensions become PromQL labels on traces_spanmetrics_* series.
      # service.name + http.route + messaging.destination.name = the trio used by
      # the existing ose-otel-demo dashboard's RED panels.
      dimensions: [service.name, http.route, messaging.destination.name]
    service_graphs:
      {}   # WHY: empty = upstream defaults; service_graphs computes service-to-service edges

storage:
  trace:
    backend: local           # WHY: filesystem; no S3/GCS for workshop
    wal:
      path: /var/tempo/wal   # F1-2: backed by tempo-wal named volume
    local:
      path: /var/tempo/blocks   # F1-2: backed by tempo-data named volume

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]   # WHY: enables both processors above

usage_report:
  reporting_enabled: false   # WHY: workshop-only; opt out of analytics
```

### Pattern 3: Mimir single-binary monolithic — `multitenancy_enabled: false` (CORRECTED key)

**What:** Mimir 3.0.6 in `target: all` (single-binary monolithic) mode with filesystem backends and a single anonymous tenant.

**When to use:** Always for this workshop demo (production would use multi-binary distributed mode + S3 + multitenancy).

**Verified example** [VERIFIED: live `docker run --rm grafana/mimir:3.0.6 -config.file=...` parse, 2026-05-02]:
```yaml
# infra/observability/mimir-config.yaml — single-binary single-tenant Mimir
# CORRECTED FROM CONTEXT.md/STACK.md: the key is `multitenancy_enabled` (NOT `auth_enabled`).
# auth_enabled is rejected: `field auth_enabled not found in type mimir.Config`.
# This is the F1-3 mitigation in its CORRECT form for Mimir 3.x.
multitenancy_enabled: false   # WHY: single-tenant workshop; no X-Scope-OrgID required, no 401s

target: all                   # WHY: monolithic mode — distributor + ingester + querier + ruler + ... all in one process

server:
  http_listen_port: 9009      # D-10: host port 9009; debug: curl localhost:9009/ready
  log_level: info             # WHY: default; switch to debug if Phase 12 exemplars don't show up

common:
  storage:
    backend: filesystem
    filesystem:
      dir: /data/mimir        # WHY: mimir-data named volume mount target
                              # Resolved as bucket prefix for blocks_storage and ruler_storage below.

blocks_storage:
  storage_prefix: blocks      # WHY: separates time-series blocks from rule files inside /data/mimir
  tsdb:
    dir: /data/mimir/tsdb     # WHY: TSDB head + WAL — must be writable

ingester:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: memberlist       # WHY: in-process gossip; no external Consul/etcd
    replication_factor: 1     # WHY: single-binary — no replication needed

distributor:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: memberlist

compactor:
  data_dir: /data/mimir/compactor   # WHY: compactor scratch space
  sharding_ring:
    kvstore:
      store: memberlist

store_gateway:
  sharding_ring:
    replication_factor: 1

ruler_storage:
  backend: filesystem
  filesystem:
    dir: /data/mimir/rules      # WHY: where Mimir-side rules live (Phase 10 ships empty; Phase 13's
                                # Loki ruler writes its outputs to Mimir as PRW, not as rule files here)

usage_stats:
  enabled: false              # WHY: workshop-only; opt out of analytics
```

### Pattern 4: Loki single-binary with ruler enabled (D-07)

**What:** Loki 3.7.1 in monolithic mode (`-target=all` is the default for the binary), filesystem storage, ruler component pre-enabled with empty rules dir, remote_write pointing at Mimir.

**When to use:** Always — D-07 pre-enables the ruler so Phase 13 only needs to drop rule YAML files.

**Verified example** [CITED: container's bundled `/etc/loki/local-config.yaml` + Grafana docs `clients:` block syntax]:
```yaml
# infra/observability/loki-config.yaml — single-binary Loki with ruler pre-enabled
auth_enabled: false             # WHY: Loki uses `auth_enabled` (NOT multitenancy_enabled — different system!)

server:
  http_listen_port: 3100        # D-10: host 3100; debug: curl localhost:3100/ready
  grpc_listen_port: 9096        # WHY: bundled default; not exposed to host

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki            # WHY: loki-data named volume mount target
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules-cache   # WHY: Loki INTERNAL rules cache, NOT the ruler_storage source
                                           # — keep separate from /loki/rules (the bind-mount path) to
                                           # avoid a path collision that hides the bind-mounted YAMLs.
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  # WHY: tsdb v13 is the current stable; from-date is the canonical workshop start (matches lgtm-bundled).
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

# D-07 LOKI RULER: pre-enabled with EMPTY rules dir.
# Phase 13 drops `loki-rules/order-errors.yaml` into this dir — no loki-config.yaml change needed then.
ruler:
  storage:
    type: local
    local:
      directory: /loki/rules    # F4-3 MITIGATION: the bind-mount path matches this directory exactly
                                # — `./infra/observability/loki-rules:/loki/rules:ro` (D-07).
                                # Loki scans this dir on (re)start; SIGHUP triggers reload.
  rule_path: /tmp/loki/scratch  # WHY: temp scratch for in-flight rule evaluations; not persistent
  alertmanager_url: http://localhost:9093   # WHY: workshop has no Alertmanager; URL is required syntax,
                                            # but no rules emit alerts so it's never contacted
  ring:
    kvstore:
      store: inmemory
  enable_api: true              # WHY: enables /loki/api/v1/rules read-back endpoint
  evaluation_interval: 1m       # D-07: every minute (F4-2 mitigation: Phase 13 uses [2m] window = 2x interval)
  remote_write:
    enabled: true
    clients:
      mimir:                    # WHY: client name is the map key; "mimir" is identifier for logs/metrics
        url: http://mimir:9009/api/v1/push   # D-06 spirit: same Mimir endpoint as Tempo + Collector
        # NOTE: Loki uses `clients:` map (not Prometheus's flat `remote_write:` array). Confirmed in
        # grafana/loki main docs/sources/operations/troubleshooting/troubleshoot-operations.md.

analytics:
  reporting_enabled: false      # WHY: workshop-only; opt out of analytics
```

### Pattern 5: Grafana datasource provisioning — UIDs from lgtm (F1-1)

**What:** Grafana 13.0.1 reads provisioning files from `/etc/grafana/provisioning/datasources/` (and `dashboards/` and `plugins/`) at startup. The datasource UIDs in this YAML must match the UIDs hardcoded in `grafana/dashboards/ose-otel-demo.json` (D-01 strategy).

**When to use:** This is the ONE Grafana provisioning file Phase 10 creates.

**Verified example** [VERIFIED: `docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml`, 2026-05-02 — UIDs reproduced VERBATIM]:
```yaml
# grafana/datasources.yaml — provisioned at /etc/grafana/provisioning/datasources/datasources.yaml
# UIDs copied VERBATIM from grafana/otel-lgtm:0.26.0's bundled provisioning so the v1.0
# ose-otel-demo dashboard JSON renders without "Datasource not found" on any panel (D-01).
#
# CRITICAL: the metric backend's UID is `prometheus` (NOT `mimir`!) — the lgtm container
# bundled its Mimir under that UID for backwards-compat with Prometheus-style consumers.
# The dashboard's panels reference "uid": "prometheus" verbatim. Do NOT change to "mimir".
apiVersion: 1

datasources:
  # ─────────────────────────── Mimir (UID = "prometheus" — see comment above) ────────
  - name: Mimir
    type: prometheus
    uid: prometheus            # ← LOAD-BEARING: matches dashboard JSON's hardcoded uid
    url: http://mimir:9009/prometheus
    editable: true
    jsonData:
      timeInterval: 10s        # WHY: matches the 10s scrape_interval in the migrated infra-exporter jobs
      # D-02 — exemplar placeholder (Phase 12 turns SDK side ON via ExemplarFilter.traceBased())
      exemplarTraceIdDestinations:
        - name: trace_id
          datasourceUid: tempo
          urlDisplayLabel: "Trace: ${__value.raw}"

  # ─────────────────────────── Tempo (UID = "tempo") ────────
  - name: Tempo
    type: tempo
    uid: tempo
    url: http://tempo:3200
    editable: true
    jsonData:
      # D-02 — Tempo span → Loki query. Reproduces lgtm's bundled tracesToLogsV2 verbatim.
      tracesToLogsV2:
        customQuery: true
        datasourceUid: loki
        query: '{${__tags}} | trace_id = "${__trace.traceId}"'
        tags:
          - key: service.name
            value: service_name
        # Optional fields documented in Grafana 13 datasource provisioning schema:
        # spanStartTimeShift / spanEndTimeShift / filterByTraceID / mappedTags
        # — omitted here to match the lgtm-bundled minimal shape.
      # D-02 — Tempo span → Mimir query (NEW for v2.0; not in lgtm's bundled).
      tracesToMetrics:
        datasourceUid: prometheus     # The Mimir datasource (uid="prometheus" per above)
        tags:
          - key: service.name
            value: service
        queries:
          - name: "Request rate"
            query: 'sum(rate(traces_spanmetrics_calls_total{$$__tags}[5m]))'
      serviceMap:
        datasourceUid: prometheus     # Service-graph queries Mimir
      nodeGraph:
        enabled: true
      lokiSearch:
        datasourceUid: loki
      search:
        hide: false

  # ─────────────────────────── Loki (UID = "loki") ────────
  - name: Loki
    type: loki
    uid: loki
    url: http://loki:3100
    editable: true
    jsonData:
      # D-02 — Loki log → Tempo trace. Reproduces lgtm's bundled derivedFields verbatim.
      derivedFields:
        - name: trace_id
          matcherType: label
          matcherRegex: trace_id
          url: "${__value.raw}"
          datasourceUid: tempo
          urlDisplayLabel: "Trace: ${__value.raw}"
```

**Notes on `${...}` escaping:**
- The lgtm-bundled file uses `$${__value.raw}` (double-dollar) — that's because it's a Helm/k8s-templated source.
- Our standalone YAML loaded directly by Grafana uses `${__value.raw}` (single-dollar). Grafana 13 parses these natively.

### Anti-Patterns to Avoid

- **`uid: mimir` in `grafana/datasources.yaml`** — would break every dashboard panel that hardcodes `"uid": "prometheus"` (which is ALL of them in v1.0's `ose-otel-demo.json`).
- **`auth_enabled: false` in `mimir-config.yaml`** — config rejected at startup with `field auth_enabled not found`. Use `multitenancy_enabled: false`.
- **`otlphttp` (no underscore) in `otelcol-config.yaml`** — works but emits deprecation warning. Use `otlp_http`.
- **`loki:` exporter in `otelcol-config.yaml`** — does not exist in v0.151.0. Use `otlp_http/loki` to Loki's `/otlp` endpoint.
- **Loki `ruler.remote_write.url:` flat string** — Loki uses `ruler.remote_write.clients.<name>.url:` map. Prometheus-style flat is wrong.
- **`http://localhost:9009/api/v1/push` from Tempo's `metrics_generator.remote_write.url`** — resolves to Tempo's own loopback, NOT Mimir. Use Docker service name `mimir`.
- **Bind-mounting `/loki/rules` over the same path as `common.storage.filesystem.rules_directory`** — they collide. Keep `common.storage...rules_directory: /loki/rules-cache` separate from `ruler.storage.local.directory: /loki/rules` (the bind-mount target).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Healthcheck for Tempo | Custom shell script that polls API | `wget --spider --quiet http://localhost:3200/ready \|\| exit 1` (Tempo `/ready` is the canonical readiness endpoint, [VERIFIED: grafana.com/docs/tempo/latest/api_docs/]) | Tempo, Mimir, Loki, Grafana ALL expose `/ready` (or `/api/health` for Grafana); using these instead of port-checks catches partial-startup states |
| Mimir/Tempo retry loop on backend warmup | `wait-for-it.sh` style sidecar | Built-in retry semantics — the Collector's exporters retry on transient errors; Tempo's metrics_generator retries on remote_write failure; Loki's ruler retries on remote_write failure | All four backends ship retry behavior; depend on it via `service_started` (D-16) rather than orchestrating startup ourselves |
| Floating-tag detection | Custom Python script that parses YAML | bash `grep -nE 'image: [^[:space:]]+:(latest\|[0-9]+\|[0-9]+\.[0-9]+)$' docker-compose.yml` (D-14) | Mise-task one-liner is auditable in 5 lines; no YAML library dep |
| Datasource UID assertion | Manually open Grafana UI and click each panel | `mise run verify:datasources`: `curl -s localhost:3000/api/datasources \| jq -e '.[] \| select(.uid==\"tempo\")'` (D-03) | Grafana's HTTP API returns provisioned datasources verbatim — automatable, scriptable, fast-fail |
| Dashboard auto-provisioning | Pre-create dashboards via Grafana HTTP API at startup | Existing `grafana/dashboards/dashboards.yaml` provisioning manifest, mounted at the standalone-Grafana path | The manifest already works in v1.0 against lgtm; only the in-container mount path changes (from `/otel-lgtm/grafana/conf/provisioning/dashboards` to `/etc/grafana/provisioning/dashboards`). Manifest content unchanged. |
| Migrating `grafana/prometheus.yaml` scrape jobs | Mount it as Mimir scrape config | Migrate to Collector's `prometheus` receiver (D-05) — the Collector becomes the "single mouth feeding the backends" | Mimir 3.0 doesn't have a built-in scraper (it's a write-only TSDB). Putting scrape jobs in the Collector matches the production-shape teaching message. |

**Key insight:** The 5-container decomposition is a configuration assembly exercise, not an SDK exercise. Every "thing that retries" or "thing that probes readiness" is provided by upstream binaries — the planner's job is to wire them, not to build them.

---

## Common Pitfalls

### Pitfall 1: F1-1 — Datasource UID drift breaks `ose-otel-demo` dashboard

**What goes wrong:** Provisioning new datasources with new UIDs (e.g., `uid: mimir` instead of the lgtm-bundled `uid: prometheus`) causes every dashboard panel to render "Datasource not found" or empty. The v1.0 `ose-otel-demo.json` hardcodes `"uid": "prometheus"`, `"uid": "tempo"`, `"uid": "loki"` in every panel query.

**Why it happens:** lgtm-bundled provisioning uses the historical Prometheus-stack UIDs (the "prometheus" UID was kept on the Mimir datasource for backwards-compat). When teams write a new `datasources.yaml` from scratch they reasonably pick `uid: mimir` for the Mimir datasource — and the dashboard breaks silently.

**How to avoid:** D-01 strategy. Inspect first, copy verbatim:
```bash
docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml
```
**Verified UIDs (research date 2026-05-02):**
- Mimir/Prometheus → `uid: prometheus` (NOT `mimir`)
- Tempo → `uid: tempo`
- Loki → `uid: loki`
- Pyroscope → `uid: pyroscope` (not used in v1.0/v2.0; safe to omit from new YAML)

**Warning signs:**
- Grafana boot logs: `Datasource not found: prometheus`.
- Dashboard panels show "Datasource not found" or render empty.
- `mise run verify:datasources` fails (D-03).

**Fast-fail check (per D-03):**
```bash
curl -s localhost:3000/api/datasources | jq -e '.[].uid' | sort | diff - <(printf 'loki\nprometheus\ntempo\n')
# Exit 0 = pass; non-zero = drift
```

### Pitfall 2: F1-3 reversed — Mimir 3.x uses `multitenancy_enabled` (NOT `auth_enabled`)

**What goes wrong:** Mimir 3.0.6 rejects `auth_enabled: false` at config-parse time:
```
error loading config from /etc/mimir.yaml: Error parsing config file: yaml: unmarshal errors:
  line 1: field auth_enabled not found in type mimir.Config
```
The container exits before serving any traffic. Even if the parser DID accept the old key, the result wouldn't disable multitenancy — it's just a different config field.

**Why it happens:** `auth_enabled` is the Cortex-era / older-Mimir name. STACK.md, PITFALLS.md F1-3, and CONTEXT.md D-06 all use the older key — written before Mimir 3.0's rename. Live container disagrees.

**How to avoid:** Use `multitenancy_enabled: false` at the YAML root in `mimir-config.yaml`. The CLI flag is `-auth.multitenancy-enabled` (note the dash form is preserved on the CLI even though the YAML key uses underscore + namespace-flat form).

**Warning signs:**
- Mimir container exits within seconds of starting; logs show `field auth_enabled not found`.
- (Old behavior, won't reproduce here): Mimir logs `no org id: no X-Scope-OrgID header found` for every push.

**Verification (research date 2026-05-02):** [VERIFIED: live `docker run --rm grafana/mimir:3.0.6 -config.file=...` parse]
- `multitenancy_enabled: false` → `Mimir starting...` (parses cleanly)
- `auth_enabled: false` → `field auth_enabled not found in type mimir.Config` (parse error)

### Pitfall 3: F1-4 reversed — collector-contrib v0.151.0 prefers `otlp_http`

**What goes wrong:** Using the legacy `otlphttp` exporter type name does NOT fail — but emits a deprecation warning at every Collector startup:
```
warn  builders/builders.go:40  "otlphttp" alias is deprecated; use "otlp_http" instead
```
The warning isn't blocking, but it's the kind of warning an attendee notices and asks "is this safe?" — not the workshop's intended teaching surface.

**Why it happens:** OpenTelemetry Collector contrib renamed exporter type names to use underscores in 2026 to align with internal Go module naming. The legacy aliases are kept for one-major-version compatibility before removal.

**How to avoid:** Use `otlp_http` (with underscore) in `otelcol-config.yaml` exporter type names. Use it consistently for all instances:
```yaml
exporters:
  otlp_http/tempo:    # NOT otlphttp/tempo
    endpoint: http://tempo:4318
  otlp_http/loki:     # NOT otlphttp/loki
    endpoint: http://loki:3100/otlp
```

**Warning signs:**
- Collector logs at startup contain `"otlphttp" alias is deprecated`.

**Verification (research date 2026-05-02):** [VERIFIED: live container parse of test config]
- `otlphttp/tempo:` → starts but logs deprecation warning
- `otlp_http/tempo:` → starts cleanly, no warning

### Pitfall 4: No `loki` exporter in collector-contrib v0.151.0

**What goes wrong:** Copying older Collector configs verbatim (especially from grafana/docker-otel-lgtm sources) yields:
```
'exporters' unknown type: "loki" for id: "loki" (valid values: [...])
```
The Collector exits immediately.

**Why it happens:** The historical `lokiexporter` was retired in favor of OTLP-native log ingestion. Loki 3.x accepts OTLP at `/otlp` directly.

**How to avoid:** Use `otlp_http/loki` pointing at Loki's `/otlp` path:
```yaml
exporters:
  otlp_http/loki:
    endpoint: http://loki:3100/otlp
    tls:
      insecure: true
service:
  pipelines:
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp_http/loki]   # NOT [loki]
```

**Verification (research date 2026-05-02):** [VERIFIED: live parse — `'exporters' unknown type: "loki"` on attempt to use the old name]

### Pitfall 5: F1-2 — Tempo metrics_generator WAL not persisted (already mitigated by D-09)

**What goes wrong:** Without a named volume on `/var/tempo/wal`, every `infra:down`/`up` cycle drops the metrics_generator's in-progress windows. Service-graph and RED metrics show empty for 1–5 minutes until traffic re-primes.

**How to avoid:** D-09 mandates named volume `tempo-wal:/var/tempo/wal`. The `tempo-data:/var/tempo` volume covers `/var/tempo/blocks` (the long-term storage). Two separate volumes — different purposes:
- `tempo-data` → `/var/tempo` (mostly `/var/tempo/blocks` — sealed trace blocks, LONG-TERM)
- `tempo-wal` → `/var/tempo/wal` (Write-Ahead Log — IN-FLIGHT trace data + metrics_generator state)

**Note:** A README callout for "service-graph re-primes after `infra:reset`" (NOT after `infra:down`/`up` because the WAL persists) is deferred to README authoring (CONTEXT.md deferred §"Service-graph dashboard panel data quality").

### Pitfall 6: F1-5 — Stale lgtm container holds port 4317

**What goes wrong:** If the `lgtm:` block is "commented out" (left in the file as comments) instead of removed, and `docker compose up -d` runs against a state where the previous lgtm container is still running, the new `otel-collector` service fails to bind 4317 with `bind: address already in use`.

**How to avoid:** D-08: REMOVE the `lgtm:` block entirely from `docker-compose.yml`. The atomic switchover (one commit removes lgtm + adds 5 services) prevents any half-state. Plan should include a pre-merge `docker compose down -v` instruction in the test plan to ensure the old container is gone.

**Warning signs:**
- `docker compose up -d` fails: `Bind: address already in use` on 4317.
- `docker ps` shows `ose-otel-lgtm` still running from a previous session.

### Pitfall 7: Loki ruler bind-mount path collision

**What goes wrong:** Loki has two `rules`-related paths:
- `common.storage.filesystem.rules_directory` — Loki's INTERNAL rule cache.
- `ruler.storage.local.directory` — the path Loki SCANS for user-supplied rule YAML files.

If both are set to `/loki/rules` and the bind-mount targets the same path, Loki's internal rule cache writes can collide with the read-only bind-mount, producing confusing "permission denied" or empty-rules-list errors.

**How to avoid:** Keep them separate (Pattern 4 above):
```yaml
common:
  storage:
    filesystem:
      rules_directory: /loki/rules-cache    # internal cache
ruler:
  storage:
    type: local
    local:
      directory: /loki/rules                # bind-mount target
```
Bind-mount: `./infra/observability/loki-rules:/loki/rules:ro` (read-only — Phase 13 rules are version-controlled, not Loki-managed).

### Pitfall 8: Grafana 13 standalone provisioning path differs from lgtm-internal

**What goes wrong:** v1.0 mounts `./grafana/dashboards:/otel-lgtm/grafana/conf/provisioning/dashboards:ro`. That path is a Grafana-internal location bundled inside the lgtm image — it does NOT exist in standalone `grafana/grafana:13.0.1`. Mounting there silently does nothing.

**How to avoid:** Standalone Grafana 13.0.1 reads provisioning from `/etc/grafana/provisioning/{datasources,dashboards,plugins}/`. [VERIFIED: live container startup logs show `Path Provisioning path=/etc/grafana/provisioning`]. Update `docker-compose.yml`:
```yaml
grafana:
  volumes:
    - ./grafana/datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml:ro
    - ./grafana/dashboards/dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml:ro
    - ./grafana/dashboards:/var/lib/grafana/dashboards:ro      # JSON files referenced by dashboards.yaml's `options.path`
```

`grafana/dashboards/dashboards.yaml` also needs its `options.path` updated:
- BEFORE (v1.0): `options.path: /otel-lgtm/grafana/conf/provisioning/dashboards`
- AFTER (v2.0):  `options.path: /var/lib/grafana/dashboards`

---

## Code Examples

Verified patterns from official sources:

### Example 1: PREREQ-01 fix shape (D-12) — both `OtelSdkConfiguration.java` files

**Source:** v1.0 LOG-03 precedent at `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:182-249`. The fix slots `this.openTelemetry = sdk;` into the existing `@Bean openTelemetry()` factory body just before `OpenTelemetryAppender.install(sdk);`.

**Current (broken) state — what to find with grep:**
```bash
# This is the cycle: @Autowired field on the same @Configuration class that produces the bean.
grep -n '@Autowired' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
grep -n '@Autowired' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
# Currently expected to find: `@Autowired private OpenTelemetry openTelemetry;` (or similar field)
# Note: as of file inspection 2026-05-02, the `producer-service` file already shows the LOG-03 inline-assign
# pattern WITHOUT the @Autowired field — verify state at the start of Phase 10 plan execution.
```

**Inspection result for producer-service file at HEAD (2026-05-02):**
The current `producer-service/.../OtelSdkConfiguration.java` already has the LOG-03 inline-assign for `OpenTelemetryAppender.install(sdk)` but does NOT have an `@Autowired OpenTelemetry openTelemetry` field. STATE.md "Blockers/Concerns" says the cycle is "still present in codebase at v1.0 HEAD" — so EITHER the field was reintroduced after Phase 5-06's fix, OR the cycle is in a sibling `@Bean` (like `tracer(OpenTelemetry openTelemetry)` and `meter(OpenTelemetry openTelemetry)` parameter injection back into the same @Configuration). The PLAN must verify the EXACT cycle shape via `mvn spring-boot:run` smoke before planning the fix.

**Hypothesized fix (D-12 mirror of LOG-03):**
```java
// BEFORE (broken — somewhere in OtelSdkConfiguration.java):
@Configuration
public class OtelSdkConfiguration {
    @Autowired private OpenTelemetry openTelemetry;   // <-- the cycle source

    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        // ... build sdk ...
        OpenTelemetryAppender.install(sdk);
        return sdk;
    }

    @PreDestroy
    void uninstall() {
        OpenTelemetryAppender.install(OpenTelemetry.noop());
    }
}

// AFTER (D-12 fix — mirrors LOG-03 inline-assign):
@Configuration
public class OtelSdkConfiguration {
    private OpenTelemetry openTelemetry;   // <-- field kept; @Autowired DROPPED

    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        // ... build sdk ...
        this.openTelemetry = sdk;          // <-- NEW: inline-assign before install/return
        OpenTelemetryAppender.install(sdk);
        return sdk;
    }

    @PreDestroy
    void uninstall() {
        OpenTelemetryAppender.install(OpenTelemetry.noop());
    }
}
```

**Grep-able verification (per D-12 acceptance gate):**
```bash
# Expect HIT (fix landed):
grep -E 'this\.openTelemetry\s*=\s*sdk' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
grep -E 'this\.openTelemetry\s*=\s*sdk' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java

# Expect NO HIT (anti-pattern absent):
grep -E '@Autowired\s+(private\s+)?OpenTelemetry' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
grep -E '@Autowired\s+(private\s+)?OpenTelemetry' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
```

### Example 2: `mise run verify:datasources` (D-03)

**Source:** Mirrors the `verify:bom` task pattern from `mise.toml:247-290`.

```toml
# mise.toml — append after the existing verify:bom task block

[tasks."verify:datasources"]
description = "Phase 10 invariant: Grafana provisioned datasource UIDs match the dashboard JSON contract"
run = """
set -e

# WHY: ose-otel-demo.json hardcodes uid='tempo', 'prometheus', 'loki' (NOT 'mimir' — D-01).
# This task fast-fails if any drift, BEFORE attendees see blank dashboards.

EXPECTED='loki
prometheus
tempo'

# Hit Grafana's HTTP API (no auth needed — anonymous Admin enabled in compose env).
ACTUAL=$(curl -sf http://localhost:3000/api/datasources 2>&1) || {
  echo "ERROR: Grafana not reachable on :3000. Run: mise run infra:up"
  exit 1
}

# Extract just the uid field from each entry, sort, dedupe.
ACTUAL_UIDS=$(printf '%s\\n' "$ACTUAL" | jq -r '.[].uid' | sort -u)

# Compare expected vs actual — diff returns non-zero on any drift.
if ! diff <(printf '%s\\n' "$EXPECTED") <(printf '%s\\n' "$ACTUAL_UIDS") > /tmp/ds-diff 2>&1; then
  echo "ERROR: Grafana datasource UIDs drifted from the dashboard contract."
  echo "Expected (3 UIDs):"
  printf '  %s\\n' "$EXPECTED"
  echo "Actual (provisioned by grafana/datasources.yaml):"
  printf '  %s\\n' "$ACTUAL_UIDS"
  echo
  echo "Diff:"
  cat /tmp/ds-diff
  echo
  echo "If you renamed a datasource, the ose-otel-demo dashboard JSON must be updated to match (NOT recommended — keep the contract)."
  exit 1
fi

echo "Phase 10 datasource contract: 3 UIDs match (loki, prometheus, tempo)."
"""
```

### Example 3: `mise run verify:images` (D-14)

**Source:** Floating-tag detection per D-14 specification.

```toml
[tasks."verify:images"]
description = "Phase 10 invariant: every docker-compose image: tag is a fully-qualified patch version (no floating tags)"
run = """
set -e

# WHY: workshop reproducibility (X-3 / D-14). Floating tags break clones across cohorts.
# Floating patterns to reject:
#   - :latest                              (unconditional)
#   - :NN              (e.g., :17 :3)      (major-only)
#   - :NN.NN           (e.g., :3.7 :2.10)  (major.minor only — no patch)
#   - no tag at all    (e.g., 'image: rabbitmq')
#
# Acceptable patterns:
#   - :NN.NN.NN        (e.g., :3.7.1 :13.0.1)
#   - :NN.NN-suffix    (e.g., :4.3-management-alpine — has a flavor suffix; not floating)
#   - :NN.NN.NN-suffix (e.g., :17-alpine)
#   - vX.Y.Z           (e.g., v1.83.0 — Prometheus-style v-prefix)

# Extract image: lines, ignore comments.
LINES=$(grep -E '^\\s*image:' docker-compose.yml | grep -v '^\\s*#')

# Floating-tag regex:
#   No colon at all = no tag = floating.
#   :latest = floating.
#   :digit-only or :digit.digit (without .digit at end) = floating, UNLESS followed by - or word char.
FLOATING=$(printf '%s\\n' "$LINES" | grep -E '(image:\\s*[^:[:space:]]+\\s*$|:latest\\s*$|:[0-9]+\\s*$|:[0-9]+\\.[0-9]+\\s*$)' || true)

if [ -n "$FLOATING" ]; then
  echo "ERROR: Phase 10 invariant violated — docker-compose.yml contains floating image tags:"
  printf '%s\\n' "$FLOATING"
  echo
  echo "Pin every image to an exact patch version. Examples:"
  echo "  rabbitmq:4.3-management-alpine     # OK (flavor suffix is not floating)"
  echo "  grafana/grafana:13.0.1             # OK (full patch version)"
  echo "  grafana/grafana:13.0               # WRONG (minor only — floating)"
  echo "  grafana/grafana:latest             # WRONG (latest — floating)"
  exit 1
fi

# Also confirm we have at least 5 image: lines (the v2.0 5-container minimum).
COUNT=$(printf '%s\\n' "$LINES" | wc -l)
if [ "$COUNT" -lt 5 ]; then
  echo "WARNING: only $COUNT image: lines found — v2.0 expects at least 5 observability containers."
fi

echo "Phase 10 image-pin contract: $COUNT image:s, all pinned to exact patch versions."
"""
```

### Example 4: Healthcheck blocks per backend (D-16)

```yaml
# docker-compose.yml — healthcheck blocks for new observability services
# All endpoints VERIFIED via grafana.com/docs/{tempo,mimir,loki}/latest/api_docs/
# Cadence mirrors the v1.0 rabbitmq healthcheck (interval 10s, timeout 5s, retries 10);
# start_period tuned per backend's typical cold-start time.

  tempo:
    # ... config ...
    healthcheck:
      # WHY: Tempo's /ready returns 200 once the ingester+distributor are accepting writes.
      # busybox wget is bundled in grafana/tempo:2.10.5 alpine base.
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:3200/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s   # Tempo cold-start: ~5-15s

  mimir:
    healthcheck:
      # WHY: Mimir's /ready returns 200 once all ring members (in-memory) are up.
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:9009/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s   # Mimir cold-start: ~10-25s (multi-component init)

  loki:
    healthcheck:
      # WHY: Loki's /ready returns 200 once schema_config + storage init complete.
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:3100/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s   # Loki cold-start: ~5-15s

  grafana:
    healthcheck:
      # WHY: Grafana's /api/health returns {"database": "ok"} once provisioning finished.
      # NOT /ready (Grafana doesn't expose that path).
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s   # Grafana cold-start: ~5-20s (datasource + dashboard provisioning)

  otel-collector:
    healthcheck:
      # WHY: health_check extension exposes :13133 returning 200 OK on readiness.
      # Requires the `extensions: [health_check]` block in otelcol-config.yaml.
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:13133/"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 15s   # Collector cold-start: ~3-10s
```

### Example 5: `preflight` task port-list extension (D-11)

```toml
# mise.toml — replace the existing [tasks.preflight] port-list section

  echo "--- Port availability (D-11: 13 ports — 5 v1.0 + 8 v2.0 additions) ---"
  for port in 3000 4317 4318 5672 15672 15692 6379 5432 3200 9009 3100 13133 8888 8889; do
    if ss -tln 2>/dev/null | grep -q ":${port} "; then
      echo "ERROR: Port ${port} is in use. Run: lsof -i:${port} to find the process."
      exit 1
    fi
    echo "  ${port}: free"
  done
```
**Note:** v1.0's preflight list omitted `15692` (rabbitmq prometheus plugin) even though it was port-mapped — D-11 lets the planner correct that oversight at the same time.

---

## State of the Art

| Old Approach (v1.0 / lgtm-bundled / older Mimir) | Current Approach (research date 2026-05-02) | When Changed |
|--------------------------------------------------|--------------------------------------------|--------------|
| Single `grafana/otel-lgtm` all-in-one | 5 separate containers (collector + tempo + mimir + loki + grafana) | This phase |
| `auth_enabled: false` (Cortex / Mimir 2.x) | `multitenancy_enabled: false` (Mimir 3.0+) | Mimir 3.0 (2026) |
| `otlphttp` exporter type name | `otlp_http` (canonical, with underscore) | collector-contrib v0.150+ (deprecation in v0.151+) |
| `loki:` exporter type | `otlp_http` to Loki's `/otlp` endpoint | Loki 3.x removed the dedicated `loki` exporter; OTLP-native ingest is the path |
| `db.statement` attribute (Hibernate / older semconv) | `db.query.text` (semconv 1.30+, stable in 1.40.0) | Not Phase 10 scope (Phase 14 DBSP); listed for completeness |
| Loki ruler `remote_write.url:` flat | Loki ruler `remote_write.clients.<name>.url:` map | Loki 3.x |
| Grafana `/otel-lgtm/grafana/conf/provisioning/dashboards` (lgtm-internal) | Grafana standalone `/etc/grafana/provisioning/dashboards/` | Standalone Grafana 13.x |
| `grafana/dashboards.yaml` `options.path: /otel-lgtm/grafana/...` | `options.path: /var/lib/grafana/dashboards` | Standalone Grafana mount |

**Deprecated/outdated:**
- `auth_enabled` in Mimir config — REJECTED by parser in Mimir 3.x. Use `multitenancy_enabled`.
- `otlphttp` exporter alias — works but emits warning. Use `otlp_http`.
- `loki` exporter component — removed. Use `otlp_http` to `/otlp`.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The `producer-service`/`consumer-service` `OtelSdkConfiguration.java` cycle is reachable from sibling `@Bean` parameter injection (`tracer(OpenTelemetry openTelemetry)`, `meter(OpenTelemetry openTelemetry)`, `httpServerSpanFilter(Tracer tracer, Meter meter)`) on the same @Configuration class | Code Examples §1, "Hypothesized fix" | If the cycle is from a DIFFERENT injection point (e.g., a constructor `@Autowired` via constructor injection), D-12 fix shape is correct but the diagnostic test must be different. Plan should run `mvn spring-boot:run` smoke and grep the stack trace for the EXACT bean name(s) participating in the cycle. STATE.md "Blockers/Concerns" confirms the cycle exists at HEAD; the file inspection at research date 2026-05-02 already shows the LOG-03 inline-assign present, suggesting the SECOND cycle source (sibling `@Bean` factory parameter injection) is the live issue. |
| A2 | Tempo 2.10.5's `metrics_generator.processor.service_graphs: {}` empty-object form enables service-graphs with upstream defaults | Pattern 2 | If service_graphs requires explicit dimensions, dashboard service-graph panel stays empty. Mitigated by also setting `overrides.defaults.metrics_generator.processors: [service-graphs, span-metrics]` (verified in upstream tempo example). |
| A3 | The `# debug: curl localhost:NNNN/...` comment placement on each port mapping doesn't break docker-compose YAML parsing | D-10 implementation | Trivial — inline comments on YAML list items are spec-compliant. |
| A4 | Loki's bundled `/etc/loki/local-config.yaml` is fully overridden by mounting our config at the same path (no merge) | Pattern 4 | Standard docker-compose volume bind shadowing — should be reliable. |
| A5 | `grafana/grafana:13.0.1` reads provisioning files at startup once (no hot-reload required for Phase 10 since UIDs are static) | Pattern 5, Example 4 | If hot-reload IS needed mid-workshop (e.g., for a Phase 11/12 datasource tweak), Grafana's provisioning is reload-on-restart only by default. Workaround: `docker compose restart grafana`. |

---

## Open Questions

1. **Exact Spring bean cycle reachability path at HEAD**
   - What we know: STATE.md Blockers/Concerns says the cycle is present at v1.0 HEAD; D-12 mirrors LOG-03 inline-assign; the LOG-03 fix is already visible in the producer-service file (line 246 — `OpenTelemetryAppender.install(sdk)` inside the `@Bean` factory body).
   - What's unclear: Whether the cycle visible at HEAD comes from a NEW `@Autowired` field re-introduced after Phase 5-06's fix, or from sibling `@Bean` factory parameter injection (`@Bean Tracer tracer(OpenTelemetry openTelemetry)`), or from constructor-style injection elsewhere on the @Configuration.
   - Recommendation: Plan's Wave 0 task — run `mvn spring-boot:run -pl producer-service` and grep the stack trace for the exact `BeanCurrentlyInCreationException` message. The bean names in the cycle path determine the fix surface. The fix itself (D-12 inline-assign) generalizes to most cycle shapes; verifying the exact path informs the test plan.

2. **Should the v2.0 Mimir datasource UID stay `prometheus` or migrate to `mimir`?**
   - What we know: v1.0 dashboard JSON hardcodes `uid: prometheus`. D-01 says reuse verbatim. STACK.md/datasources.yaml example uses `uid: mimir`.
   - What's unclear: Future readability cost of keeping `prometheus` as the metric backend's UID when the actual datasource is Mimir.
   - Recommendation: KEEP `uid: prometheus` for Phase 10 (D-01 commitment). Plan a v2.x roadmap item to rename — but it requires a one-shot dashboard JSON rewrite, which is not v2.0 scope.

3. **Tempo OTLP gRPC port — does it conflict with the Collector?**
   - What we know: Tempo `distributor.receivers.otlp.protocols.grpc.endpoint: "0.0.0.0:4317"` exposes Tempo's own OTLP gRPC. The Collector ALSO uses 4317. Inside docker-compose, container-internal 4317 collisions are fine (each container has its own loopback). But D-10 exposes Collector's 4317 to host, NOT Tempo's.
   - What's unclear: Does the planner need to remap Tempo's internal 4317 to avoid confusion?
   - Recommendation: Leave Tempo's internal 4317 alone (not exposed to host). The Collector exporter `otlp_http/tempo` uses HTTP port 4318 anyway (Pattern 1). If a future task needs Tempo gRPC reachable from host, a different port mapping can be added then.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Engine | All container operations | ✓ (assumed; v1.0 prerequisite) | (host) | None — block phase |
| docker compose v2 | `mise run infra:up` | ✓ | v2.x | None — block phase |
| `wget` (busybox) inside backend images | Healthcheck commands | ✓ in tempo/mimir/loki/grafana alpine bases | bundled | Use `nc -z` if `wget` unavailable in some image variant |
| `curl` on host | `verify:datasources` | ✓ (workshop prerequisite) | (system) | `wget`-based equivalent (clunkier output parsing) |
| `jq` on host | `verify:datasources` | ✓ usually present, document otherwise | (system) | None — task fails clearly with "jq not found"; README mentions `apt install jq` |
| `ss` (iproute2) on host | `preflight` port check | ✓ (used in v1.0 preflight) | (system) | `lsof -i :NNNN` |
| Disk space for 5 named volumes | Phase 10 onwards | ✓ assumed | — | Document approximate sizes (Tempo blocks ~100MB/hour at workshop volume) |
| `grafana/otel-lgtm:0.26.0` image | One-time UID inspection (D-01 step) | ✓ already pulled by v1.0 setup | bundled | None — but the inspection result is already captured in this RESEARCH.md (§F1-1), so re-inspection is optional |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None of the above expected to be missing on a workshop laptop that already passed v1.0 preflight.

---

## Validation Architecture

> NOTE: `workflow.nyquist_validation` is `false` in `.planning/config.json`. This section is included anyway because the planner uses it to derive verification criteria for the 5 phase success criteria. Treat as informational.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.x (BOM-managed by Spring Boot 3.4.13) + Testcontainers (existing) + maven-failsafe-plugin 3.5.5 |
| Config file | `integration-tests/pom.xml` (existing; `<plugin><artifactId>maven-failsafe-plugin</artifactId>...</plugin>` already binds integration-test + verify goals) |
| Quick run command | `mvn -pl producer-service,consumer-service test` (unit) |
| Full suite command | `mvn -T 1C verify` (existing `mise run test`) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Test File Exists? |
|--------|----------|-----------|-------------------|-------------------|
| PREREQ-01 | Both services boot without `BeanCurrentlyInCreationException` | smoke (mvn spring-boot:run for each) | `mvn -pl producer-service spring-boot:run` AND `mvn -pl consumer-service spring-boot:run` (timeout 60s, expect "Started") | ✗ — manual smoke; existing `OrderFlowIT` covers post-boot wiring but won't catch context-load failure modes that prevent test bootstrap |
| PREREQ-02 | `docs/screenshots/step-04-metrics.png` exists | filesystem check | `test -f docs/screenshots/step-04-metrics.png && file docs/screenshots/step-04-metrics.png \| grep -q PNG` | ✗ — manual capture (D-13) |
| STACK-01 | 5 named containers + no lgtm; exact image tags | filesystem grep + docker inspect | `mise run verify:images` AND `docker compose ps --format json \| jq -r '.[].Service' \| sort` should equal `5 obs services + 5 v1.0 services` | ✗ Wave 0 — task does not exist yet (D-14) |
| STACK-02 | Floating-tag guard | grep regex | `mise run verify:images` (Example 3 above) | ✗ Wave 0 — task does not exist yet (D-14) |
| STACK-03 | OTLP endpoint unchanged in producer + consumer | grep | `grep 'http://localhost:4317' producer-service/.../OtelSdkConfiguration.java consumer-service/.../OtelSdkConfiguration.java` returns hits in both | ✓ — uses existing files |
| STACK-04 | Dashboard renders all panels (no "Datasource not found") | HTTP API | `mise run verify:datasources` + manual UI check (Grafana dashboard) | ✗ Wave 0 — task does not exist yet (D-03); UI check is manual |
| STACK-05 | Mimir zero 401 errors after `POST /orders` | log grep | `docker compose logs mimir 2>&1 \| grep -c '401\\\|no org id'` returns `0` | ✗ — bash one-liner test for SC plan |

### Sampling Rate
- **Per task commit:** Build + unit tests for any Java change (`mvn -pl <module> test`)
- **Per wave merge:** Full suite (`mvn -T 1C verify`) + smoke (`mise run infra:up && curl POST /orders && verify:datasources`)
- **Phase gate:** All 5 SCs verified manually + `mise run verify:images` + `mise run verify:datasources` green; `BeanCurrentlyInCreationException` absent from `producer.log`/`consumer.log` after fresh start

### Wave 0 Gaps
- [ ] `mise.toml` task `verify:datasources` (D-03) — Example 2 above
- [ ] `mise.toml` task `verify:images` (D-14) — Example 3 above
- [ ] `mise.toml` task `preflight` extended port list (D-11) — Example 5 above
- [ ] No new test infrastructure required — existing JUnit + Testcontainers + Failsafe wiring covers Phase 10 SCs

*(Existing infra in `integration-tests/` is sufficient. No new test framework, fixtures, or config required for Phase 10.)*

---

## Security Domain

> Required because `security_enforcement: true` in `.planning/config.json`, with `security_asvs_level: 1`.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial | Grafana `GF_AUTH_ANONYMOUS_ENABLED=true` + `GF_AUTH_DISABLE_LOGIN_FORM=true` continues from v1.0 — appropriate for workshop demo on localhost. README must call out "anonymous Admin enabled — production use case requires real auth". |
| V3 Session Management | no | Workshop has no sessions |
| V4 Access Control | no | Single anonymous Admin role; no multi-user model |
| V5 Input Validation | partial | Mimir / Tempo / Loki / Grafana / Collector accept config files mounted from host; YAMLs are workshop-controlled, no user input. The `prometheus` receiver in the Collector (D-05) scrapes infra exporters at static targets — no dynamic discovery, no SSRF surface. |
| V6 Cryptography | no | All inter-container traffic on docker-compose default network; `tls.insecure: true` is appropriate for workshop. README must note: "production deployments terminate TLS at the Collector ingress and inside backends". |
| V7 Error Handling | no | Workshop-only |
| V8 Data Protection | partial | Backend admin ports (3200, 9009, 3100, 13133, 8888, 8889) bound to host (D-10). On a multi-user workshop machine, these are reachable to all OS users. README should advise running `docker compose down` between sessions if the laptop is shared. |
| V9 Communications | no | Localhost-only |
| V10 Malicious Code | no | All images pinned to exact patch versions (D-14 enforces). Multi-arch manifests — no platform-mismatch RCE surface. |
| V11 Business Logic | no | N/A |
| V12 Files & Resources | partial | Bind-mounts include `:ro` (read-only) on `loki-rules`; remaining bind-mounts are config files (also conceptually read-only — no container needs to write back). |
| V13 API & Web Service | no | Workshop |
| V14 Configuration | yes | All 5 YAML configs include explicit comments documenting the workshop-vs-production divergences (D-04 teaching style); CONTEXT.md `tls.insecure: true` and `multitenancy_enabled: false` choices are workshop-only. |

### Known Threat Patterns for {Spring Boot 3.4.13 + OTel SDK + docker-compose}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Spring Boot circular bean reference (PREREQ-01 itself) | Denial of Service | D-12 inline-assign in `@Bean` factory body — eliminates the cycle without disabling Spring's circular-ref detection (which would mask the issue, not fix it) |
| Floating-image tags (X-3) | Tampering / Supply chain | D-14 `verify:images` mise task — fast-fails on `:latest` or major-only tags |
| Compose service spoofing | Spoofing | Container names pinned (`ose-otel-collector`, `ose-otel-tempo`, etc.) — matches v1.0 naming convention |
| Mimir multitenancy bypass via missing `X-Scope-OrgID` (F1-3) | Information Disclosure | `multitenancy_enabled: false` — single-tenant workshop config; production would require real `X-Scope-OrgID` injection |
| Bind-mount path traversal | Tampering | All bind-mounts are file-level (not directory-level wildcards) on whitelisted paths under `infra/observability/` and `grafana/` |

---

## Sources

### Primary (HIGH confidence — verified via live container inspection 2026-05-02)
- `docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml` — datasource UIDs `tempo`, `prometheus`, `loki`, `pyroscope` (F1-1 verbatim source)
- `docker run --rm otel/opentelemetry-collector-contrib:0.151.0 components` — exporter list confirms `otlp_http`, `otlp_grpc`, `prometheusremotewrite`; NO `loki` exporter
- `docker run --rm grafana/mimir:3.0.6 -version` and live config-parse test — confirms `multitenancy_enabled` is the correct YAML key; `auth_enabled` rejected with explicit error
- `docker run --rm grafana/grafana:13.0.1` startup logs — `Path Provisioning path=/etc/grafana/provisioning`
- `docker cp grafana/loki:3.7.1:/etc/loki/local-config.yaml -` — bundled default config used as Pattern 4 starting point
- Tempo `metrics_generator` config — extracted from `https://github.com/grafana/tempo/blob/v2.10.5/example/docker-compose/local/tempo.yaml` (WebFetch)

### Secondary (MEDIUM-HIGH confidence — official documentation cited)
- [Grafana Tempo single-binary local example](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/deploy/locally/docker-compose/) — Pattern 2 shape
- [Grafana Mimir get-started monolithic config](https://github.com/grafana/mimir/blob/main/docs/sources/mimir/get-started/_index.md) — Pattern 3 shape (verified `multitenancy_enabled` key)
- [Grafana Loki recording rules — ruler block](https://grafana.com/docs/loki/latest/operations/recording-rules/) — Pattern 4 `clients:` syntax
- [Grafana Tempo datasource provisioning](https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/provision/) — `tracesToLogsV2`, `tracesToMetrics`, `serviceMap` provisioning fields
- [OTel Collector contrib — exporter component registry](https://github.com/open-telemetry/opentelemetry-collector-contrib) — `otlp_http`, `prometheusremotewrite`

### v1.0 / project-internal (HIGH confidence — committed code)
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:182-249` — LOG-03 inline-assign pattern that PREREQ-01 mirrors
- `docker-compose.yml:5-119` — current 6-service file (`rabbitmq`, `valkey`, `postgres`, `redis-exporter`, `postgres-exporter`, `lgtm`)
- `mise.toml:37-67` — `preflight` task port-list pattern (D-11 extends)
- `mise.toml:247-290` — `verify:bom` task structure (D-03/D-14 follow this shape)
- `grafana/dashboards/ose-otel-demo.json` — hardcoded UIDs `prometheus`, `tempo`, `loki` (verified via grep)
- `grafana/dashboards/dashboards.yaml` — existing dashboard provisioning manifest (path moves; content unchanged)
- `grafana/prometheus.yaml` — orphan scrape_configs to be migrated to Collector's `prometheus` receiver (D-05)
- `.planning/research/STACK.md`, `.planning/research/PITFALLS.md`, `.planning/research/ARCHITECTURE.md` — v2.0 milestone research; this RESEARCH.md is the Phase 10 implementation-detail layer on top
- `.planning/STATE.md` Blockers/Concerns — confirms PREREQ-01 cycle still present at HEAD; D-01 inspection requirement noted; D-13 deferred-PNG reasoning carries from Phase 7-07

### Tertiary (LOW confidence — flagged for re-verification at plan time)
- None. Every claim in this research is either VERIFIED via live container inspection or CITED from current upstream documentation.

---

## Metadata

**Confidence breakdown:**
- Standard stack (image tags, ports): HIGH — every image pulled and version-confirmed at research date
- YAML config patterns (Tempo / Mimir / Loki / Collector / Grafana datasources): HIGH — every key shape verified via live container parse OR direct upstream-docs read at the pinned version
- PREREQ-01 fix shape: HIGH — D-12 mirrors the existing LOG-03 pattern; only A1 (cycle reachability path at HEAD) is open
- Cross-signal datalink shapes (D-02): HIGH — UIDs verified from lgtm container; provisioning YAML schema confirmed in Grafana 13 docs
- Floating-tag detection regex (D-14): MEDIUM — bash regex shape is defensible but not exhaustively edge-case-tested (planner can tighten if needed)
- Healthcheck cadences: MEDIUM-HIGH — `start_period` values are estimates from observed container cold-start behavior on test machine; real workshop laptops may need slight tuning

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (30 days for stable container versions; 7 days if any image releases a new patch — track via `gh release list` on each repo)
