# Phase 10: Prerequisites & Stack Decomposition — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 12 (7 NEW, 5 MODIFIED, 1 DELETED, 1 binary screenshot)
**Analogs found:** 9 / 11 (excluding binary PNG — manual capture per D-13; and the bind-mount layout for `infra/observability/` — net-new and only loosely analogous to the v1.0 prometheus.yaml mount)

> **Pattern philosophy this phase:** Two upstream-doc corrections discovered during live container inspection (RESEARCH §F1-3, §F1-4) override CONTEXT.md/STACK.md keys. **Use `multitenancy_enabled` (not `auth_enabled`) for Mimir 3.0.6, and `otlp_http` (not `otlphttp`) + `otlp_http/loki` (not `loki:` exporter) for collector-contrib v0.151.0.** The PATTERN excerpts below cite the live-verified shapes from RESEARCH.md, not the stale keys in earlier research artifacts.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `infra/observability/otelcol-config.yaml` | config (collector pipelines) | request-response (OTLP ingress → 3 backends) + batch (prometheus scrape) | `grafana/prometheus.yaml` (scrape_configs migration) + RESEARCH §Pattern 1 (verbatim) | role-match (no existing collector-contrib config in-tree); WHY-comment density mirrored from `OtelSdkConfiguration.java` |
| `infra/observability/tempo-config.yaml` | config (trace backend) | streaming (OTLP receive) + batch (metrics_generator → remote_write) | RESEARCH §Pattern 2 (verbatim, upstream tempo example) | no in-tree analog; comment density from `OtelSdkConfiguration.java` |
| `infra/observability/mimir-config.yaml` | config (metric backend) | request-response (PRW receive) + storage (filesystem TSDB) | RESEARCH §Pattern 3 (verbatim, live-verified `multitenancy_enabled`) | no in-tree analog; comment density from `OtelSdkConfiguration.java` |
| `infra/observability/loki-config.yaml` | config (log backend) | request-response (OTLP receive) + batch (ruler remote_write) | RESEARCH §Pattern 4 (verbatim, bundled `/etc/loki/local-config.yaml` + ruler block) | no in-tree analog; comment density from `OtelSdkConfiguration.java` |
| `infra/observability/loki-rules/.gitkeep` | placeholder | n/a (empty marker file) | (none — convention file) | trivial |
| `grafana/datasources.yaml` | config (grafana provisioning) | request-response (Grafana → datasource HTTP API) + cross-signal datalinks | `grafana/dashboards/dashboards.yaml` (provisioning manifest style) + RESEARCH §Pattern 5 (verbatim UIDs from lgtm) | role-match (existing manifest is dashboards-only; this one is datasources); D-01 UIDs reused VERBATIM from inspected lgtm container |
| `docker-compose.yml` (MODIFIED) | config (compose) | n/a (orchestration) | self — `rabbitmq` / `valkey` / `postgres` healthcheck blocks; `lgtm:` block (replaced) | exact (same file, established conventions) |
| `mise.toml` (MODIFIED) | config (task runner) | n/a (CLI tasks) | self — `[tasks."verify:bom"]` block, `[tasks.preflight]` block | exact (same file, established conventions) |
| `producer-service/.../OtelSdkConfiguration.java` (MODIFIED) | config (Spring bean wiring) | request-response (Spring bean lifecycle) | self lines 182-249 (`OpenTelemetryAppender.install(sdk)` slot-in point — LOG-03 lineage) | exact (same file, same `@Bean` factory, same fix shape) |
| `consumer-service/.../OtelSdkConfiguration.java` (MODIFIED) | config (Spring bean wiring) | request-response | self lines 191-246 (mirror of producer); cross-file analog: producer-service file | exact (TRACE-01/DOC-05 — both files edited identically) |
| `grafana/prometheus.yaml` (DELETED, `git rm`) | config (deleted) | n/a | self (its `scrape_configs:` migrate into `otelcol-config.yaml`) | exact deletion target — content migrates to Pattern 1 receiver |
| `README.md` (MODIFIED) | documentation | n/a | existing Step 9 / Step 4 sections (paste verbatim shape per Phase 5 plan-06 precedent) | not a code analog — documentation pattern is "section-mirror" |
| `docs/screenshots/step-04-metrics.png` (NEW, binary) | asset (binary) | n/a | sibling PNGs in `docs/screenshots/` (e.g., `step-03-waterfall.png`) | trivial; capture per D-13 manual procedure |

---

## Pattern Assignments

### `infra/observability/otelcol-config.yaml` (config, OTLP ingress + scrape ingress + 3 export pipelines)

**Analogs:**
1. `grafana/prometheus.yaml` lines 50-76 — the `scrape_configs:` block that **migrates verbatim** into Collector's `receivers.prometheus.config.scrape_configs` (D-05).
2. RESEARCH `10-RESEARCH.md` §Pattern 1 lines 245-268 — verbatim exporter block with `otlp_http/tempo`, `prometheusremotewrite/mimir`, `otlp_http/loki`.
3. `producer-service/.../OtelSdkConfiguration.java` lines 39-85 + 122-148 — comment-density precedent for D-04 teaching-grade WHY comments.

**Source — `grafana/prometheus.yaml:50-76` (the scrape_configs to migrate, D-05):**
```yaml
scrape_configs:
  # Step 9 (quick-260502-j00): infrastructure exporters scraped over the
  # compose default network. Static label `source: infra-exporter` is the
  # disambiguator for the two-lens story (app-emitted vs exporter-scraped).
  - job_name: rabbitmq
    # Scrape /metrics/per-object instead of the default /metrics so per-queue
    # series carry {queue="..."} (and per-connection/per-channel/per-exchange)
    # labels.
    metrics_path: /metrics/per-object
    static_configs:
      - targets: ['rabbitmq:15692']
        labels:
          source: infra-exporter

  - job_name: redis_exporter
    static_configs:
      - targets: ['redis-exporter:9121']
        labels:
          source: infra-exporter

  - job_name: postgres_exporter
    static_configs:
      - targets: ['postgres-exporter:9187']
        labels:
          source: infra-exporter
```

**Source — `10-RESEARCH.md` §Pattern 1 lines 245-268 (canonical exporters block, live-verified):**
```yaml
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

**Anti-patterns (verified rejected by live container parse, RESEARCH §Anti-Patterns):**
- `otlphttp/tempo:` (no underscore) → starts but emits `"otlphttp" alias is deprecated; use "otlp_http" instead`
- `loki:` exporter type → `'exporters' unknown type: "loki" for id: "loki"` and Collector exits

**Comment-density excerpt — `OtelSdkConfiguration.java:39-58` (teaching-grade WHY style, D-04):**
```java
/**
 * Manual OpenTelemetry SDK bootstrap for producer-service (TRACE-01..04 + DOC-03).
 *
 * THIS FILE IS THE WORKSHOP'S TEXTBOOK FOR THE SDK SETUP.
 * Every @Bean below carries an inline comment explaining what each builder
 * call does and why. Read it top-to-bottom in your IDE.
 *
 * <p><b>Why duplicated per service?</b> The IDENTICAL file lives in
 * consumer-service/.../config/OtelSdkConfiguration.java with two changes
 * only: the service.name string ("order-producer" -> "order-consumer") and
 * the tracer scope name ("com.example.producer" -> "com.example.consumer").
 * ...
 */
```
Apply this density to every block in the new YAML — header doc-block + per-block one-line WHY comment.

---

### `infra/observability/tempo-config.yaml` (config, trace storage + metrics_generator → Mimir PRW)

**Analog:** RESEARCH `10-RESEARCH.md` §Pattern 2 lines 286-355 — full single-binary tempo config verbatim, live-verified at `grafana/tempo:2.10.5`.

**Key excerpt — `10-RESEARCH.md:313-339` (D-06 metrics_generator → Mimir; F1-2 mitigation):**
```yaml
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
      dimensions: [service.name, http.route, messaging.destination.name]
    service_graphs:
      {}   # WHY: empty = upstream defaults
```

**Two named volumes per D-09 (bind targets):**
- `tempo-data` → `/var/tempo` (long-term `/var/tempo/blocks`)
- `tempo-wal`  → `/var/tempo/wal` (in-flight WAL + metrics_generator state — F1-2)

---

### `infra/observability/mimir-config.yaml` (config, single-tenant monolithic Mimir 3.0.6)

**Analog:** RESEARCH `10-RESEARCH.md` §Pattern 3 lines 364-420 — verbatim, live-verified config-parse against `grafana/mimir:3.0.6`.

**LOAD-BEARING correction over CONTEXT.md/STACK.md/PITFALLS.md F1-3 — `10-RESEARCH.md:367-369`:**
```yaml
# CORRECTED FROM CONTEXT.md/STACK.md: the key is `multitenancy_enabled` (NOT `auth_enabled`).
# auth_enabled is rejected: `field auth_enabled not found in type mimir.Config`.
multitenancy_enabled: false   # WHY: single-tenant workshop; no X-Scope-OrgID required, no 401s

target: all                   # WHY: monolithic mode — distributor + ingester + querier + ruler + ... all in one process
```

**Storage block excerpt — `10-RESEARCH.md:377-419`:**
```yaml
common:
  storage:
    backend: filesystem
    filesystem:
      dir: /data/mimir        # WHY: mimir-data named volume mount target

blocks_storage:
  storage_prefix: blocks      # WHY: separates time-series blocks from rule files inside /data/mimir
  tsdb:
    dir: /data/mimir/tsdb

ingester:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: memberlist       # WHY: in-process gossip; no external Consul/etcd
    replication_factor: 1     # WHY: single-binary — no replication needed
```

**Single named volume per D-09:** `mimir-data` → `/data/mimir`.

---

### `infra/observability/loki-config.yaml` (config, monolithic Loki + ruler enabled with empty rules dir)

**Analog:** RESEARCH `10-RESEARCH.md` §Pattern 4 lines 430-489 — verbatim, derived from container's bundled `/etc/loki/local-config.yaml` + Grafana docs `clients:` block.

**Ruler block excerpt — `10-RESEARCH.md:464-485` (D-07 pre-enable; F4-3 + ruler bind-mount path collision mitigation):**
```yaml
# D-07 LOKI RULER: pre-enabled with EMPTY rules dir.
# Phase 13 drops `loki-rules/order-errors.yaml` into this dir — no loki-config.yaml change needed then.
ruler:
  storage:
    type: local
    local:
      directory: /loki/rules    # F4-3 MITIGATION: the bind-mount path matches this directory exactly
                                # — `./infra/observability/loki-rules:/loki/rules:ro` (D-07).
  rule_path: /tmp/loki/scratch
  alertmanager_url: http://localhost:9093
  ring:
    kvstore:
      store: inmemory
  enable_api: true
  evaluation_interval: 1m
  remote_write:
    enabled: true
    clients:
      mimir:                    # WHY: client name is the map key; "mimir" is identifier for logs/metrics
        url: http://mimir:9009/api/v1/push
```

**Bind-mount path collision pitfall — `10-RESEARCH.md:730-749`:**
- `common.storage.filesystem.rules_directory: /loki/rules-cache` (Loki INTERNAL cache)
- `ruler.storage.local.directory: /loki/rules` (the bind-mount target)
- DO NOT collapse these to the same path.

**Single named volume per D-09:** `loki-data` → `/loki`. Plus bind-mount `./infra/observability/loki-rules:/loki/rules:ro`.

---

### `infra/observability/loki-rules/.gitkeep` (placeholder, empty marker)

**Pattern:** standard convention. Empty file, exists only so `git`-tracked directory mounts cleanly.

**Why:** Phase 13 lands `loki-rules/order-errors.yaml`; bind-mount target must already exist on first `infra:up`.

---

### `grafana/datasources.yaml` (config, Grafana datasource provisioning + cross-signal datalinks)

**Analogs:**
1. `grafana/dashboards/dashboards.yaml` — existing provisioning manifest style (becomes pattern for new datasources file).
2. RESEARCH `10-RESEARCH.md` §Pattern 5 lines 499-573 — verbatim, UIDs from inspected `grafana/otel-lgtm:0.26.0` container.

**Source — `grafana/dashboards/dashboards.yaml` (existing provisioning manifest style):**
```yaml
# Grafana dashboard provisioning manifest.
# Phase 7 / WORK-02 / D-01 — auto-provisions the OSE OTel Demo dashboard at
# infra:up time so workshop attendees see live three-signal telemetry the
# moment Grafana is healthy (zero clicks).
#
# Schema: https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards
apiVersion: 1

providers:
  - name: 'ose-otel-demo'
    orgId: 1
    folder: ''
    folderUid: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: false
    options:
      path: /otel-lgtm/grafana/conf/provisioning/dashboards
      foldersFromFilesStructure: false
```

**`dashboards.yaml` MODIFICATION required (per RESEARCH F1-8 / Pitfall 8) — `options.path` must change:**
- BEFORE: `options.path: /otel-lgtm/grafana/conf/provisioning/dashboards` (lgtm-internal)
- AFTER:  `options.path: /var/lib/grafana/dashboards` (standalone Grafana 13)

**Source — `10-RESEARCH.md` §Pattern 5 lines 506-573 (verbatim UIDs + datalinks per D-01 + D-02):**
```yaml
# UIDs copied VERBATIM from grafana/otel-lgtm:0.26.0's bundled provisioning so the v1.0
# ose-otel-demo dashboard JSON renders without "Datasource not found" on any panel (D-01).
#
# CRITICAL: the metric backend's UID is `prometheus` (NOT `mimir`!) — the lgtm container
# bundled its Mimir under that UID for backwards-compat with Prometheus-style consumers.
# The dashboard's panels reference "uid": "prometheus" verbatim. Do NOT change to "mimir".
apiVersion: 1

datasources:
  - name: Mimir
    type: prometheus
    uid: prometheus            # ← LOAD-BEARING: matches dashboard JSON's hardcoded uid
    url: http://mimir:9009/prometheus
    editable: true
    jsonData:
      timeInterval: 10s
      # D-02 — exemplar placeholder (Phase 12 turns SDK side ON via ExemplarFilter.traceBased())
      exemplarTraceIdDestinations:
        - name: trace_id
          datasourceUid: tempo
          urlDisplayLabel: "Trace: ${__value.raw}"

  - name: Tempo
    type: tempo
    uid: tempo
    url: http://tempo:3200
    editable: true
    jsonData:
      tracesToLogsV2:
        customQuery: true
        datasourceUid: loki
        query: '{${__tags}} | trace_id = "${__trace.traceId}"'
        tags:
          - key: service.name
            value: service_name
      tracesToMetrics:
        datasourceUid: prometheus
        tags:
          - key: service.name
            value: service
        queries:
          - name: "Request rate"
            query: 'sum(rate(traces_spanmetrics_calls_total{$$__tags}[5m]))'
      serviceMap:
        datasourceUid: prometheus
      nodeGraph:
        enabled: true

  - name: Loki
    type: loki
    uid: loki
    url: http://loki:3100
    editable: true
    jsonData:
      derivedFields:
        - name: trace_id
          matcherType: label
          matcherRegex: trace_id
          url: "${__value.raw}"
          datasourceUid: tempo
          urlDisplayLabel: "Trace: ${__value.raw}"
```

**Mount path:** `./grafana/datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml:ro` (NOT lgtm-internal path — see Pitfall 8 in RESEARCH).

---

### `docker-compose.yml` (MODIFIED) — replace `lgtm:` with 5 new service blocks

**Analogs (all in same file):**
1. `rabbitmq` healthcheck block (lines 6-28) — cadence template for new observability services (D-16).
2. `valkey` healthcheck (lines 31-42) and `postgres` healthcheck (lines 45-60) — supporting cadence references.
3. `lgtm:` block (lines 91-119) — being REPLACED; comment-density precedent for new YAMLs (D-04 teaching-style).
4. RESEARCH `10-RESEARCH.md` §Example 4 lines 952-1000 — exact healthcheck blocks for tempo/mimir/loki/grafana/otel-collector with verified `wget --spider` probes.

**Source — `docker-compose.yml:6-28` (healthcheck cadence template):**
```yaml
  rabbitmq:
    image: rabbitmq:4.3-management-alpine
    container_name: ose-otel-rabbitmq
    environment:
      RABBITMQ_PLUGINS: "rabbitmq_management rabbitmq_prometheus"
    ports:
      - "5672:5672"      # AMQP
      - "15672:15672"    # Management UI (guest/guest)
      - "15692:15692"    # Prometheus plugin /metrics — Step 9
    healthcheck:
      # Stage 1 healthcheck — lightest with lowest false-positive rate.
      # Source: rabbitmq.com/docs/monitoring
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
    restart: unless-stopped
```

**Cadence pattern locked (D-16):**
- `interval: 10s` — every observability service
- `timeout: 5s` — every observability service
- `retries: 10` — every observability service
- `start_period: 15s..30s` — per backend cold-start (Tempo 20s, Mimir 30s, Loki 20s, Grafana 20s, Collector 15s)

**Source — `10-RESEARCH.md:952-1000` (verified healthcheck commands per backend):**
```yaml
  tempo:
    healthcheck:
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:3200/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s

  mimir:
    healthcheck:
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:9009/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  loki:
    healthcheck:
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:3100/ready"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s

  grafana:
    healthcheck:
      # WHY: Grafana's /api/health returns {"database": "ok"} once provisioning finished.
      # NOT /ready (Grafana doesn't expose that path).
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s

  otel-collector:
    healthcheck:
      # WHY: health_check extension exposes :13133 returning 200 OK on readiness.
      test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:13133/"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 15s
```

**Source — `docker-compose.yml:91-119` (`lgtm:` block — being REPLACED; comment-density precedent):**
```yaml
  lgtm:
    # Image's built-in HEALTHCHECK checks all 5 backends — do NOT override.
    image: grafana/otel-lgtm:0.26.0
    container_name: ose-otel-lgtm
    ports:
      - "3000:3000"      # Grafana UI (admin/admin)
      - "4317:4317"      # OTLP gRPC ingest (unused in Phase 1)
      - "4318:4318"      # OTLP HTTP ingest (unused in Phase 1)
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      # Workshop UX (Phase 7 D-01 spirit — "zero clicks, zero Grafana navigation"):
      # anonymous access is enabled with Admin role so attendees and the screenshot
      # capture pipeline (DOC-04) can land directly on dashboards without a login form.
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
      - GF_AUTH_DISABLE_LOGIN_FORM=true
    volumes:
      - lgtm-data:/data
      - ./grafana/dashboards:/otel-lgtm/grafana/conf/provisioning/dashboards:ro
      - ./grafana/prometheus.yaml:/otel-lgtm/prometheus.yaml:ro
    restart: unless-stopped
```

**Lift-and-keep from `lgtm:` for the new `grafana:` service:**
- All `GF_*` env vars (anonymous Admin remains — Phase 7 D-01 carryforward)
- The `./grafana/dashboards:...:ro` bind-mount (path target changes to `/var/lib/grafana/dashboards` per Pitfall 8)
- Add NEW bind-mount `./grafana/datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml:ro`
- Add NEW bind-mount `./grafana/dashboards/dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml:ro`
- Add NEW named volume `grafana-data:/var/lib/grafana`

**Comment-block divider precedent — same file:**
```yaml
  # Phase 8: Valkey (Redis-compatible cache) — producer idempotency
  # Step 9 (quick-260502-j00): redis_exporter scrapes Valkey ...
```
Apply for new section: `# ===== Observability stack =====` separating the 5 data services from the 5 new observability services (D-08).

**Volume section pattern — `docker-compose.yml:121-122` (currently single-volume):**
```yaml
volumes:
  lgtm-data:
```
Replace with five named volumes per D-09:
```yaml
volumes:
  tempo-data:
  tempo-wal:
  mimir-data:
  loki-data:
  grafana-data:
```

**Pinned-tag policy — verified from existing image lines:**
```
rabbitmq:4.3-management-alpine          # exact patch via flavor suffix
postgres:17-alpine                      # exact patch via flavor suffix
valkey/valkey:8.1-alpine                # exact patch via flavor suffix
oliver006/redis_exporter:v1.83.0        # exact patch
prometheuscommunity/postgres-exporter:v0.19.1   # exact patch
```
Five new images follow same convention (verified pulls dated 2026-05-02):
- `otel/opentelemetry-collector-contrib:0.151.0`
- `grafana/tempo:2.10.5`
- `grafana/mimir:3.0.6`
- `grafana/loki:3.7.1`
- `grafana/grafana:13.0.1`

**`depends_on` semantics (D-16) — `service_started` only (no Docker HEALTHCHECKs from these images by default; Collector retries on export):**
- `otel-collector` → `tempo`, `mimir`, `loki`
- `tempo` → `mimir`
- `loki` → `mimir`
- `grafana` → `tempo`, `mimir`, `loki`

---

### `mise.toml` (MODIFIED) — extend preflight; add verify:datasources, verify:images

**Analogs (all in same file):**
1. `[tasks.preflight]` lines 37-67 — port-list shape; D-11 extends it.
2. `[tasks."verify:bom"]` lines 247-290 — structural template for D-03 and D-14 (mvn/curl + grep + sort + awk + fail-on-violation).

**Source — `mise.toml:57-64` (preflight port-list pattern, D-11 extends):**
```toml
echo "--- Port availability (3000, 4317, 4318, 5672, 15672) ---"
for port in 3000 4317 4318 5672 15672; do
  if ss -tln 2>/dev/null | grep -q ":${port} "; then
    echo "ERROR: Port ${port} is in use. Run: lsof -i:${port} to find the process."
    exit 1
  fi
  echo "  ${port}: free"
done
```

**D-11 extension (per RESEARCH §Example 5 lines 1007-1014):**
```toml
echo "--- Port availability (D-11: 13 ports — 5 v1.0 + 8 v2.0 additions) ---"
for port in 3000 4317 4318 5672 15672 15692 6379 5432 3200 9009 3100 13133 8888 8889; do
  if ss -tln 2>/dev/null | grep -q ":${port} "; then
    echo "ERROR: Port ${port} is in use. Run: lsof -i:${port} to find the process."
    exit 1
  fi
  echo "  ${port}: free"
done
```
Note D-11 also corrects `15692` (rabbitmq prometheus plugin) — was port-mapped in compose but absent from v1.0's preflight list.

**Source — `mise.toml:247-290` (verify:bom structural template):**
```toml
[tasks."verify:bom"]
description = "Phase 2 invariant: one version per io.opentelemetry* artifact across the reactor"
run = """
set -e

OUTPUT=$(mvn dependency:tree -Dincludes=io.opentelemetry 2>&1)
VIOLATIONS=$(printf '%s\\n' "$OUTPUT" \\
  | grep -oE 'io\\.opentelemetry[a-z.-]*:[a-z0-9-]+:(jar|pom):[0-9a-zA-Z.-]+' \\
  | sort -u \\
  | awk -F: '{ key=$1":"$2":"$3; ver=$4; if (seen[key] && seen[key] != ver) print key" appears at versions "seen[key]" AND "ver; seen[key]=ver }')

if [ -n "$VIOLATIONS" ]; then
  echo "ERROR: Phase 2 invariant violated — the following OTel artifacts appear at multiple versions across the reactor:"
  printf '%s\\n' "$VIOLATIONS"
  ...
  exit 1
fi
...
echo "Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules."
"""
```

**Pattern to apply (per D-03 and D-14; full bodies in RESEARCH §Example 2 lines 850-887 and §Example 3 lines 895-941):**
- Single bash run-block with `set -e`
- Compute expected from a literal here-string
- Compute actual via `curl` / `grep`
- `diff` or grep-anti-pattern check; non-zero exit on violation
- Echo a clear ERROR message naming the violated invariant + remediation hint
- Echo a final success line so green output is greppable

**`verify:datasources` (D-03) excerpt — `10-RESEARCH.md:850-887`:**
```toml
[tasks."verify:datasources"]
description = "Phase 10 invariant: Grafana provisioned datasource UIDs match the dashboard JSON contract"
run = """
set -e

EXPECTED='loki
prometheus
tempo'

ACTUAL=$(curl -sf http://localhost:3000/api/datasources 2>&1) || {
  echo "ERROR: Grafana not reachable on :3000. Run: mise run infra:up"
  exit 1
}

ACTUAL_UIDS=$(printf '%s\\n' "$ACTUAL" | jq -r '.[].uid' | sort -u)

if ! diff <(printf '%s\\n' "$EXPECTED") <(printf '%s\\n' "$ACTUAL_UIDS") > /tmp/ds-diff 2>&1; then
  echo "ERROR: Grafana datasource UIDs drifted from the dashboard contract."
  ...
  exit 1
fi

echo "Phase 10 datasource contract: 3 UIDs match (loki, prometheus, tempo)."
"""
```

**`verify:images` (D-14) excerpt — `10-RESEARCH.md:895-941`:**
```toml
[tasks."verify:images"]
description = "Phase 10 invariant: every docker-compose image: tag is a fully-qualified patch version (no floating tags)"
run = """
set -e

LINES=$(grep -E '^\\s*image:' docker-compose.yml | grep -v '^\\s*#')

# Floating: no tag, :latest, :NN, :NN.NN
FLOATING=$(printf '%s\\n' "$LINES" | grep -E '(image:\\s*[^:[:space:]]+\\s*$|:latest\\s*$|:[0-9]+\\s*$|:[0-9]+\\.[0-9]+\\s*$)' || true)

if [ -n "$FLOATING" ]; then
  echo "ERROR: Phase 10 invariant violated — docker-compose.yml contains floating image tags:"
  printf '%s\\n' "$FLOATING"
  exit 1
fi

echo "Phase 10 image-pin contract: $COUNT image:s, all pinned to exact patch versions."
"""
```

**Section-divider style precedent — `mise.toml:34-36, 69-71`:**
```toml
# ──────────────────────────────────────────────────────────────────
# Pre-flight: validate the developer's machine before anything starts
# ──────────────────────────────────────────────────────────────────
```
Apply between existing tasks and new `verify:datasources` / `verify:images` blocks (e.g., after `[tasks."verify:bom"]`).

---

### `producer-service/.../OtelSdkConfiguration.java` (MODIFIED) — D-12 minimal LOG-03 mirror

**Analog:** SAME FILE, lines 182-249 — the existing LOG-03 inline-assign at line 246 (`OpenTelemetryAppender.install(sdk)` inside the `@Bean openTelemetry()` factory body) is the slot-in point for `this.openTelemetry = sdk;`.

**Source — `OtelSdkConfiguration.java:182-249` (current state of `@Bean openTelemetry()` factory; LOG-03 install pattern is the structural lineage):**
```java
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(propagators)
            .build();

        // ----- LOG-03 / PITFALL #5: install the OpenTelemetryAppender HERE -----
        //
        // The order-of-operations problem: Logback initializes BEFORE the Spring
        // ApplicationContext is built. Spring Boot's LoggingApplicationListener
        // loads logback-spring.xml from classpath at startup, which constructs an
        // OpenTelemetryAppender instance with its OpenTelemetry reference
        // defaulting to OpenTelemetry.noop().
        //
        // We call install(sdk) HERE — inside the @Bean factory, just before
        // returning — rather than from a @PostConstruct method on this same
        // @Configuration class. The @PostConstruct shape would create a Spring
        // self-cycle: the @Configuration bean would need to autowire the
        // OpenTelemetry it itself produces. Calling install(sdk) right where the
        // SDK is constructed sidesteps the cycle entirely AND tightens the
        // window in which logs can land in the noop replay queue.
        // ...
        OpenTelemetryAppender.install(sdk);

        return sdk;
    }
```

**D-12 fix shape (per RESEARCH §Example 1 lines 793-830) — diff sketch:**
```java
// BEFORE (broken — somewhere on this @Configuration class):
@Configuration
public class OtelSdkConfiguration {
    @Autowired private OpenTelemetry openTelemetry;   // <-- the cycle source

    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        // ... build sdk ...
        OpenTelemetryAppender.install(sdk);
        return sdk;
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
}
```

**Critical inspection note (RESEARCH §Code Examples Example 1 lines 789-790, Open Question 1, and Assumption A1):**
At HEAD on 2026-05-02, the producer-service file already shows the LOG-03 inline-assign for `OpenTelemetryAppender.install(sdk)` at line 246 but does NOT have an `@Autowired OpenTelemetry openTelemetry` field. STATE.md says the cycle is still present at HEAD, so the cycle source may be elsewhere — likely sibling `@Bean` factory parameter injection at `tracer(OpenTelemetry openTelemetry)` line 496 and `meter(OpenTelemetry openTelemetry)` line 516, which inject back into the SAME @Configuration. **The plan's Wave 0 task MUST first run `mvn -pl producer-service spring-boot:run` and grep the stack trace for the exact `BeanCurrentlyInCreationException` bean-name path before authoring the fix.**

**Grep-able verification (per D-12 acceptance gate, RESEARCH lines 832-841):**
```bash
# Expect HIT (fix landed):
grep -E 'this\.openTelemetry\s*=\s*sdk' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
grep -E 'this\.openTelemetry\s*=\s*sdk' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java

# Expect NO HIT (anti-pattern absent):
grep -E '@Autowired\s+(private\s+)?OpenTelemetry' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
grep -E '@Autowired\s+(private\s+)?OpenTelemetry' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
```

---

### `consumer-service/.../OtelSdkConfiguration.java` (MODIFIED) — same shape as producer

**Analog:** producer-service `OtelSdkConfiguration.java` (cross-file). Per TRACE-01 / DOC-05, both files are intentionally per-service duplicated and must receive **identical** edits with same comment text. The consumer file structure (verified via Read at lines 85-214) is byte-symmetric to the producer except `service.name` ("order-consumer") and tracer scope ("com.example.consumer") strings.

**Pattern:** apply EXACTLY the D-12 edits as in the producer file. Resist any IDE/linting suggestion to extract a shared base class — DOC-05 makes the duplication a load-bearing teaching surface.

---

### `grafana/prometheus.yaml` (DELETED via `git rm`)

**Pattern:** `git rm grafana/prometheus.yaml`. Lines 50-76 (the `scrape_configs:` block) migrate verbatim into `infra/observability/otelcol-config.yaml` under `receivers.prometheus.config.scrape_configs` (D-05). The `global:`, `otlp:`, `storage:` blocks (lines 9-48) are NOT migrated — they were Mimir/Prometheus-side ingestion knobs no longer needed once the Collector is the single ingress.

**Corresponding compose deletion:** the `./grafana/prometheus.yaml:/otel-lgtm/prometheus.yaml:ro` bind-mount (line 112) goes away with the entire `lgtm:` block.

---

### `README.md` (MODIFIED) — add Step 10 section

**Analog:** existing `README.md` Step 9 / Step 4 sections (mirror the section anatomy: opening paragraph + framing sentence + 3 bulleted touch-points + closing paragraph with demo command + queries). Section-anatomy precedent per Phase 5 plan-06 SUMMARY (`.planning/milestones/v1.0-phases/05-logs-correlation/05-06-SUMMARY.md`) lines 30-33.

**Per-section content per CONTEXT.md `<code_context>:integration_points` and Deferred §"Backend admin port pedagogy":**
- `step-10-collector-decompose` annotated tag callout
- Before/after compose-service-count narrative (6 services → 10 services)
- Port-map table covering all D-10 ports
- Two or three "interesting" debug curl commands (e.g., `curl localhost:3200/api/search?tags=service.name=order-producer`)
- `mise run verify:images` and `mise run verify:datasources` command callouts
- Workshop-vs-production callouts noted in §Security Domain (anonymous Admin, `tls.insecure: true`, `multitenancy_enabled: false`)
- Service-graph re-priming callout (1-5 minute warmup after `infra:reset`, but NOT after `infra:down`/`up` thanks to D-09's `tempo-wal` named volume)

---

### `docs/screenshots/step-04-metrics.png` (NEW, binary) — D-13 manual capture

**Analog:** sibling PNGs in `docs/screenshots/` — `step-01-empty-tempo.png`, `step-02-disconnected-traces.png`, `step-03-joined-trace.png`, `step-03-waterfall.png`, `step-05-logs-trace-jump.png`, `step-06-test-output.png`. File format and naming convention match.

**Capture procedure (D-13, deferred to README authoring):**
1. After Phase 10 lands (post-decomposition), `mise run infra:up && mise run dev`
2. POST a few orders via `mise run demo:order` to populate panels
3. Open Grafana → ose-otel-demo dashboard → Step 4 metrics panel
4. Manually screenshot the metrics panel area
5. Save as `docs/screenshots/step-04-metrics.png`
6. Commit with the phase-completion commit

**Why manual:** RESEARCH §"PREREQ-02 capture procedure" + Phase 7-07's "Scope Cut" rationale — the automated `docs:screenshots` cycle-tags-in-worktree pipeline can't render polish-layer artifacts at older `step-NN-*` tags. After Phase 10 lands, the dashboard renders correctly on the new decomposed stack at `main` HEAD, so the metrics panel can be screenshot'd directly. The automation pipeline (`scripts/screenshots/capture.mjs`) is **read-only reference** — NOT modified.

---

## Shared Patterns

### Pattern S1: Teaching-grade WHY-comment density on every YAML block (D-04)

**Source:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` lines 39-85 (file-level doc-block) + lines 122-148, 162-181, 189-247 (per-block WHY callouts).

**Apply to:** ALL 5 new YAMLs (`infra/observability/{otelcol,tempo,mimir,loki}-config.yaml` + `grafana/datasources.yaml`).

**Excerpt — `OtelSdkConfiguration.java:39-58` (file-level header doc-block density):**
```java
/**
 * Manual OpenTelemetry SDK bootstrap for producer-service (TRACE-01..04 + DOC-03).
 *
 * THIS FILE IS THE WORKSHOP'S TEXTBOOK FOR THE SDK SETUP.
 * Every @Bean below carries an inline comment explaining what each builder
 * call does and why. Read it top-to-bottom in your IDE.
 *
 * <p><b>Why duplicated per service?</b> ...
 * <p><b>Why no autoconfigure?</b> ...
 * <p><b>Why semconv-incubating?</b> ...
 * <p><b>Why three @Beans (openTelemetry / tracer / meter)?</b> ...
 * <p><b>No Logger @Bean (D-07).</b> ...
 */
```

**Style rule:** every YAML block gets a one-line `# WHY: ...` comment; production-only sections (auth headers, TLS, retry policy) are NOT included. Workshop attendees should be able to read each YAML cold and understand why each block is there. Do NOT exceed the density — match it.

### Pattern S2: Healthcheck cadence (D-16 + RESEARCH §Example 4)

**Source:** `docker-compose.yml` lines 20-27 (`rabbitmq`), 36-41 (`valkey`), 54-59 (`postgres`).

**Apply to:** every new observability service in `docker-compose.yml`.

**Locked cadence:**
```yaml
healthcheck:
  test: ["CMD", "wget", "--spider", "--quiet", "http://localhost:NNNN/<path>"]
  interval: 10s
  timeout: 5s
  retries: 10
  start_period: 15s..30s   # tuned per backend cold-start (RESEARCH §Example 4)
```

Backend-specific paths (live-verified per RESEARCH §Don't Hand-Roll line 596):
- Tempo / Mimir / Loki → `/ready`
- Grafana → `/api/health` (NOT `/ready` — Grafana doesn't expose that path)
- Collector → `/` on `:13133` (requires `extensions: [health_check]` in `otelcol-config.yaml`)

### Pattern S3: `mise run verify:*` family naming + structural template (D-03 + D-14)

**Source:** `mise.toml:247-290` (`verify:bom`).

**Apply to:** new `verify:datasources`, new `verify:images`.

**Locked shape:**
- Task block header `[tasks."verify:NAME"]`
- `description = "Phase 10 invariant: <what is invariant>"`
- `run = """`-style multi-line bash
- `set -e` first
- WHY-comment block at top
- Compute expected; compute actual; diff or anti-pattern check; non-zero exit on violation
- Echo final success line so green output is greppable

### Pattern S4: Pinned image tag policy (D-14 enforcement target)

**Source:** existing `docker-compose.yml` image lines (verified):
- `rabbitmq:4.3-management-alpine`
- `postgres:17-alpine`
- `valkey/valkey:8.1-alpine`
- `oliver006/redis_exporter:v1.83.0`
- `prometheuscommunity/postgres-exporter:v0.19.1`
- `grafana/otel-lgtm:0.26.0`

**Apply to:** all 5 new image lines. Verified pulls dated 2026-05-02 (RESEARCH §Standard Stack):
- `otel/opentelemetry-collector-contrib:0.151.0`
- `grafana/tempo:2.10.5`
- `grafana/mimir:3.0.6`
- `grafana/loki:3.7.1`
- `grafana/grafana:13.0.1`

`verify:images` (D-14) is the policy enforcement task.

### Pattern S5: Comment-block dividers in config files

**Source — `mise.toml:34-36`:**
```toml
# ──────────────────────────────────────────────────────────────────
# Pre-flight: validate the developer's machine before anything starts
# ──────────────────────────────────────────────────────────────────
```

**Source — `docker-compose.yml:30, 44, 62, 81`:**
```yaml
  # Phase 8: Valkey (Redis-compatible cache) — producer idempotency
  # Phase 8: PostgreSQL — consumer persistence
  # Step 9 (quick-260502-j00): redis_exporter scrapes Valkey ...
  # Step 9 (quick-260502-j00): postgres_exporter — connects via ...
```

**Apply to:** between data services and observability services in `docker-compose.yml` (D-08):
```yaml
  # ===== Observability stack =====
```

### Pattern S6: TRACE-01 / DOC-05 — per-service duplication of `OtelSdkConfiguration`

**Source:** producer + consumer both have identically-shaped `OtelSdkConfiguration.java` files (verified via Read on producer lines 1-540, consumer lines 85-214).

**Apply to:** D-12 fix MUST be applied independently in BOTH files with identical shape and identical comment text. Resist any IDE/linting prompt to extract to `otel-bootstrap` — workshop's load-bearing teaching surface.

### Pattern S7: WORK-01 — annotated git tags on `main` for workshop checkpoints

**Source:** Project convention (cited in CONTEXT.md `<code_context>:established_patterns` and STATE.md). Phase 2-06 / 6-06 / 7-07 / 5-06 precedent.

**Apply to:** `step-10-collector-decompose` tag is NOT applied during phase execution; it lands with the orchestrator's atomic merge commit per WORK-01.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Bind-mount layout for `infra/observability/*.yaml` | config (compose volumes) | bind-mount | Closest analog is the `./grafana/prometheus.yaml:/otel-lgtm/prometheus.yaml:ro` bind-mount in the `lgtm:` block — being removed in this same phase. New layout is net-new under `infra/observability/`. RESEARCH §Pattern 1-5 give the in-container target paths verbatim. |
| `infra/observability/loki-rules/.gitkeep` | placeholder | n/a | Standard `git`-tracked-empty-directory convention; no project analog needed. |
| `docs/screenshots/step-04-metrics.png` | binary asset | n/a | Sibling PNGs in `docs/screenshots/` give file-naming convention only; image content is captured manually per D-13 procedure. |
| `grafana/datasources.yaml` cross-signal datalink shapes (`tracesToLogsV2`, `tracesToMetrics`, `derivedFields`, `exemplarTraceIdDestinations`) | provisioning syntax | n/a | Net-new. Closest analogs are the inspected lgtm-internal `grafana-datasources.yaml` (verbatim source per D-01) and Grafana 13 datasource-provisioning schema docs. RESEARCH §Pattern 5 captures every block verbatim. |
| Loki `ruler` config | backend config | n/a | Net-new. RESEARCH §Pattern 4 captures the verbatim shape including the bind-mount path collision mitigation (Pitfall 7). |

---

## Metadata

**Analog search scope:**
- `/home/coto/dev/demo/ose-otel-demo/docker-compose.yml` (full read)
- `/home/coto/dev/demo/ose-otel-demo/mise.toml` (full read)
- `/home/coto/dev/demo/ose-otel-demo/producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (full read, 540 lines)
- `/home/coto/dev/demo/ose-otel-demo/consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` (targeted read lines 85-214 — shape verified byte-symmetric to producer)
- `/home/coto/dev/demo/ose-otel-demo/grafana/dashboards/dashboards.yaml` (full read)
- `/home/coto/dev/demo/ose-otel-demo/grafana/prometheus.yaml` (full read)
- `/home/coto/dev/demo/ose-otel-demo/grafana/dashboards/` (directory listing — confirms `ose-otel-demo.json`, `ose-otel-infra.json`, `ose-otel-noc.json` present and untouched)
- `/home/coto/dev/demo/ose-otel-demo/docs/screenshots/` (directory listing — confirms sibling PNGs)
- `/home/coto/dev/demo/ose-otel-demo/infra/` (does not exist — `infra/observability/` is net-new directory)
- `/home/coto/dev/demo/ose-otel-demo/.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` (full read)
- `/home/coto/dev/demo/ose-otel-demo/.planning/phases/10-prerequisites-stack-decomposition/10-RESEARCH.md` (targeted reads: 1-300, 300-650, 650-945, 945-1208 — full coverage)
- `/home/coto/dev/demo/ose-otel-demo/.planning/milestones/v1.0-phases/05-logs-correlation/05-06-SUMMARY.md` (1-80 — LOG-03 lineage and Phase 5 source-defect documentation)

**Files scanned:** 11 source files + 2 phase artifacts + 1 v1.0 summary

**Pattern extraction date:** 2026-05-02
