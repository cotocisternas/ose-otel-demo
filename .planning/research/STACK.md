# Technology Stack — v2.0 Production Shapes (Additions Only)

**Project:** OSE OTel Demo v2.0
**Researched:** 2026-05-02
**Scope:** ADDITIONS and CHANGES relative to the locked v1.0 stack. Do not re-research anything already in `.planning/milestones/v1.0-research/STACK.md`.
**Confidence:** HIGH across all sections (every version cross-checked against Docker Hub, GitHub Releases API, Maven Central, and official source configs on the date above)

---

## Executive Recommendation

v2.0 requires changes in two distinct layers:

**Infrastructure layer (docker-compose):** Replace the single `grafana/otel-lgtm:0.26.0` container with five separate pinned containers: `otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, `grafana/loki:3.7.1`, and `grafana/grafana:13.0.1`. These are the exact versions bundled inside otel-lgtm 0.26.0's constituent components (plus patch-level updates released through April 2026). Every feature needed for v2.0 — tail sampling, exemplars, Loki recording rules, Mimir ruler — is present and verified in these versions.

**Java/Maven layer (pom.xml):** Add exactly two new Spring Boot starters (`spring-boot-starter-data-jpa` and `spring-boot-starter-web` is already present; only `data-jpa` is new), both managed by the existing Spring Boot 3.4.13 BOM with no version conflict. No new OTel SDK dependencies are required: `Sampler.parentBased()`, `Sampler.traceIdRatioBased()`, `Baggage`, `ExemplarFilter`, and `TextMapSetter` are all already present in the `opentelemetry-sdk` and `opentelemetry-api` artifacts already in scope. The `opentelemetry-sdk-extension-incubator` is NOT needed for any of the 8 v2.0 features.

---

## Recommended Stack

### New Infrastructure Containers (replace `grafana/otel-lgtm:0.26.0`)

| Container Image | Pinned Tag | Port(s) | v2.0 Feature | Why This Tag |
|-----------------|-----------|---------|--------------|--------------|
| `otel/opentelemetry-collector-contrib` | `0.151.0` | `4317` (gRPC OTLP recv), `4318` (HTTP OTLP recv) | Features 1 (decompose), 2 (tail sampling), 3 (exemplars via `prometheusremotewrite` exporter) | Released 2026-04-29. Latest stable. Contrib distribution required because `tailsamplingprocessor` and `lokiexporter` ship in contrib, not core. `prometheusremotewriteexporter` supports exemplars passthrough to Mimir. Multi-arch (amd64 + arm64) confirmed on Docker Hub. |
| `grafana/tempo` | `2.10.5` | `3200` (HTTP API / Grafana datasource), `4317` (OTLP gRPC recv), `4318` (OTLP HTTP recv) | Feature 1 (decompose), 3 (exemplar click-through) | Released 2026-04-23. Latest stable 2.10.x. OTLP ingestion on ports 4317/4318 confirmed via official single-binary example config. `metrics_generator` with `send_exemplars: true` on remote_write confirmed in tempo-config.yaml. Multi-arch confirmed. |
| `grafana/mimir` | `3.0.6` | `9009` (all endpoints: remote_write `/api/v1/push`, ruler API, Prometheus query API) | Feature 1 (decompose), 4 (recording rules via Mimir Ruler), 3 (exemplar storage `POST /prometheus/api/v1/query_exemplars`) | Released 2026-04-20. Latest stable 3.x line (3.0 is the current major). Supports both Prometheus remote_write (`/api/v1/push`) and OTLP ingest (`/otlp/v1/metrics`). Ruler component is bundled in the single monolithic binary (`-target=all`). Filesystem-backed ruler_storage works for workshop (no object store needed). Exemplar query API confirmed in HTTP API docs. |
| `grafana/loki` | `3.7.1` | `3100` | Feature 1 (decompose), 4 (Loki LogQL recording rules via Loki Ruler) | Released 2026-03-27. Latest 3.7.x. Recording rules (LogQL `record:` expressions that produce metric time series written to a remote Prometheus-compatible store) are stable in Loki 3.x. Filesystem storage + inmemory ring confirmed in docker-otel-lgtm loki-config.yaml. Multi-arch confirmed. |
| `grafana/grafana` | `13.0.1` | `3000` | Feature 1 (decompose), 3 (exemplar datalink `exemplarTraceIdDestinations`), 4 (derived fields / datalinks in Loki) | Released 2026-04-17. Latest stable 13.0.x. The `exemplarTraceIdDestinations` datasource provisioning key (Prometheus → Tempo click-through) is confirmed in the official grafana-datasources.yaml from docker-otel-lgtm and has been stable since Grafana 7.4. `tracesToLogsV2` (Tempo → Loki) confirmed stable in 13.x. Multi-arch confirmed. |

### New Java/Maven Dependencies (add to relevant service pom.xml)

| Library | GroupId:ArtifactId | BOM Source | Version | v2.0 Feature | Why Needed |
|---------|-------------------|------------|---------|--------------|------------|
| Spring Data JPA starter | `org.springframework.boot:spring-boot-starter-data-jpa` | `spring-boot-dependencies:3.4.13` | managed (Hibernate `6.6.39.Final`, HikariCP `5.1.0`, PostgreSQL JDBC `42.7.8`) | Feature 5 (JDBC/JPA spans) | Brings `EntityManager`, `@Repository`, `@Transactional`, Hibernate ORM 6.6 — the surface where attendees wrap operations in manual `SPAN_KIND_CLIENT` spans with `db.*` semconv attributes. Managed entirely by existing SB 3.4.13 BOM — no version pin needed. |
| PostgreSQL JDBC driver | `org.postgresql:postgresql` | `spring-boot-dependencies:3.4.13` | `42.7.8` (managed) | Feature 5 (JDBC/JPA spans) | The JDBC driver for the PostgreSQL container already used in v1.0 Phase 8. Only add if not already present in the consumer service pom after Phase 8 merge. |

No additional OTel SDK dependencies are needed. All SDK capabilities required for v2.0 are already present in the existing BOM-managed artifacts:

| Capability Needed | SDK Class / API | Already In | Feature |
|-------------------|-----------------|-----------|---------|
| Parent-based head sampling | `Sampler.parentBased(Sampler.traceIdRatioBased(ratio))` | `opentelemetry-sdk-trace` (in `opentelemetry-sdk`) | Feature 7 |
| Ratio sampling | `Sampler.traceIdRatioBased(ratio)` | `opentelemetry-sdk-trace` (in `opentelemetry-sdk`) | Feature 7 |
| Baggage create/propagate | `Baggage.builder().put(key, value).build()` | `opentelemetry-api` | Feature 7 |
| Exemplar wiring (SDK side) | `SdkMeterProviderBuilder.setExemplarFilter(ExemplarFilter.traceBased())` | `opentelemetry-sdk-metrics` (in `opentelemetry-sdk`) | Feature 3 |
| Outbound HTTP CLIENT span | `tracer.spanBuilder(...).setSpanKind(SpanKind.CLIENT)` + `TextMapSetter` | `opentelemetry-api` | Feature 6 |
| DB CLIENT span (JPA/JDBC wrap) | `SpanKind.CLIENT` + `DbAttributes.DB_SYSTEM` etc. | `opentelemetry-api` + `opentelemetry-semconv:1.40.0` | Feature 5 |
| AMQP fanout/DLX spans | `MessagingAttributes.*` already in semconv 1.40.0 | `opentelemetry-semconv:1.40.0` | Feature 8 |

---

## Infrastructure Configuration Files

### Collector config (`otel-collector-config.yaml`)

This replaces the `otelcol-config.yaml` embedded in otel-lgtm. The Collector needs three pipelines (traces → Tempo, metrics → Mimir, logs → Loki) plus a fourth traces pipeline fork through the tail_sampling processor.

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    send_batch_size: 512
    timeout: 5s
  memory_limiter:
    limit_mib: 256
    check_interval: 1s
  tail_sampling:
    decision_wait: 10s
    num_traces: 5000
    expected_new_traces_per_sec: 50
    policies:
      - name: keep-on-error
        type: status_code
        status_code: { status_codes: [ERROR] }
      - name: keep-slow
        type: latency
        latency: { threshold_ms: 500 }
      - name: probabilistic-fallback
        type: probabilistic
        probabilistic: { sampling_percentage: 10 }

exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true
  prometheusremotewrite/mimir:
    endpoint: http://mimir:9009/api/v1/push
    tls:
      insecure: true
    # Exemplars are forwarded automatically when present on histogram data points
  loki:
    endpoint: http://loki:3100/loki/api/v1/push
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, tail_sampling, batch]
      exporters: [otlp/tempo]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [prometheusremotewrite/mimir]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [loki]
```

Notes:
- The `tail_sampling` processor is in contrib only — using `otel/opentelemetry-collector-contrib` is the correct image.
- The three policies implement "keep-on-error OR keep-slow OR 10% fallback" — Collector evaluates policies in order and takes the first match.
- `prometheusremotewrite` exporter forwards exemplars attached to histogram data points by default; no additional config key needed beyond the endpoint.

### Tempo config (`tempo.yaml`)

Derived from the official Tempo single-binary example + docker-otel-lgtm tempo-config.yaml, adapted for standalone container:

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: "0.0.0.0:4317"
        http:
          endpoint: "0.0.0.0:4318"

storage:
  trace:
    backend: local
    wal:
      path: /var/tempo/wal
    local:
      path: /var/tempo/blocks

ingester:
  lifecycler:
    address: 127.0.0.1
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1

metrics_generator:
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://mimir:9009/api/v1/push
        send_exemplars: true
  processor:
    span_metrics:
      dimensions: [service.name, http.route, messaging.destination.name]
    service_graphs:
      {}

overrides:
  defaults:
    metrics_generator:
      processors: [span-metrics, service-graphs]

usage_report:
  reporting_enabled: false
```

### Mimir config (`mimir.yaml`)

Single-binary monolithic mode, filesystem backend — appropriate for workshop (no MinIO required):

```yaml
# Single-node monolithic Mimir — workshop/demo only, not for production
target: all

server:
  http_listen_port: 9009

common:
  storage:
    backend: filesystem
    filesystem:
      dir: /data/mimir

blocks_storage:
  storage_prefix: blocks
  tsdb:
    dir: /data/mimir/tsdb

ruler:
  rule_path: /data/mimir/ruler
  ring:
    kvstore:
      store: inmemory

ruler_storage:
  backend: filesystem
  filesystem:
    dir: /data/mimir/rules

ingester:
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory
    replication_factor: 1

store_gateway:
  sharding_ring:
    replication_factor: 1

compactor:
  sharding_ring:
    kvstore:
      store: inmemory

memberlist:
  bind_addr: [127.0.0.1]

# Disable multi-tenancy for workshop simplicity
auth_enabled: false

usage_stats:
  enabled: false
```

### Loki config (`loki.yaml`)

Derived directly from docker-otel-lgtm loki-config.yaml with ruler section enabled for recording rules:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  instance_addr: 127.0.0.1
  path_prefix: /data/loki
  storage:
    filesystem:
      chunks_directory: /data/loki/chunks
      rules_directory: /data/loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

ruler:
  storage:
    type: local
    local:
      directory: /data/loki/rules
  rule_path: /tmp/loki/scratch
  alertmanager_url: http://127.0.0.1:9093
  ring:
    kvstore:
      store: inmemory
  enable_api: true

# Example recording rule (written to /data/loki/rules/fake/rules.yaml):
# groups:
#   - name: ose-otel-errors
#     interval: 1m
#     rules:
#       - record: ose_otel:error_rate_1m
#         expr: |
#           sum(rate({service_name=~"producer|consumer"} |= "ERROR" [1m]))
```

### Grafana datasources (`grafana-datasources.yaml`)

Extends the datasource provisioning from docker-otel-lgtm to wire exemplar click-through and trace-to-log datalinks:

```yaml
apiVersion: 1
datasources:
  - name: Mimir
    type: prometheus
    uid: mimir
    url: http://mimir:9009/prometheus
    editable: true
    jsonData:
      timeInterval: 15s
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
      serviceMap:
        datasourceUid: mimir
      nodeGraph:
        enabled: true
      lokiSearch:
        datasourceUid: loki

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

### Updated `docker-compose.yml` (infra section)

The `lgtm` service is replaced by five separate services. RabbitMQ is unchanged. Postgres and Valkey (from Phase 8) are also unchanged.

```yaml
services:
  collector:
    image: otel/opentelemetry-collector-contrib:0.151.0
    container_name: ose-otel-collector
    volumes:
      - ./otel-collector-config.yaml:/etc/otelcol-contrib/config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC (apps send here)
      - "4318:4318"   # OTLP HTTP
    depends_on:
      - tempo
      - mimir
      - loki

  tempo:
    image: grafana/tempo:2.10.5
    container_name: ose-otel-tempo
    command: ["-config.file=/etc/tempo.yaml"]
    volumes:
      - ./tempo.yaml:/etc/tempo.yaml
      - tempo-data:/var/tempo
    ports:
      - "3200:3200"   # Grafana datasource + trace query API
    # No OTLP ports exposed externally — Collector talks to Tempo on internal network

  mimir:
    image: grafana/mimir:3.0.6
    container_name: ose-otel-mimir
    command: ["-config.file=/etc/mimir.yaml"]
    volumes:
      - ./mimir.yaml:/etc/mimir.yaml
      - mimir-data:/data/mimir
    ports:
      - "9009:9009"   # remote_write + PromQL API + Ruler API

  loki:
    image: grafana/loki:3.7.1
    container_name: ose-otel-loki
    command: ["-config.file=/etc/loki/loki.yaml"]
    volumes:
      - ./loki.yaml:/etc/loki/loki.yaml
      - loki-data:/data/loki
    ports:
      - "3100:3100"

  grafana:
    image: grafana/grafana:13.0.1
    container_name: ose-otel-grafana
    volumes:
      - ./grafana/datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml
      - ./grafana/dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml
      - ./grafana/dashboards:/var/lib/grafana/dashboards
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on:
      - mimir
      - loki
      - tempo

volumes:
  tempo-data:
  mimir-data:
  loki-data:
```

### New Maven dependency (producer-service or consumer-service pom.xml)

Add to the service(s) that exercise JDBC/JPA instrumentation (Feature 5 — likely consumer-service given Phase 8 db-cache pattern):

```xml
<!-- Feature 5: JDBC/JPA spans — Spring Data JPA + Hibernate 6.6 (BOM-managed) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<!-- PostgreSQL JDBC driver — only needed if not already present from Phase 8 -->
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
</dependency>
```

No version tags — both managed by `spring-boot-dependencies:3.4.13`.

### SDK additions for exemplar wiring (Feature 3 — no new dependency, just code change)

The `ExemplarFilter` API is in `opentelemetry-sdk-metrics` which is already included via `opentelemetry-sdk`. The only change is a one-line addition to the `SdkMeterProvider` builder:

```java
// In OtelSdkConfiguration — add to existing SdkMeterProvider builder:
SdkMeterProvider.builder()
    .setExemplarFilter(ExemplarFilter.traceBased())  // NEW for Feature 3
    // ... rest of existing builder ...
    .build();
```

`ExemplarFilter.traceBased()` attaches the active `trace_id` and `span_id` to histogram measurements when a sampled span is active. The Collector's `prometheusremotewrite` exporter then forwards these exemplars to Mimir.

---

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| `otel/opentelemetry-collector-contrib:0.151.0` | `otel/opentelemetry-collector:0.151.0` (core) | Core distribution does not include `tailsamplingprocessor` (contrib-only) or `lokiexporter` (contrib-only). Must use contrib. |
| `grafana/mimir:3.0.6` (3.x) | `grafana/mimir:2.17.10` (2.x) | Mimir 3.0 is the current stable major released Jan 2026. 2.17.x is maintained but 3.0 is the forward path. No breaking differences for workshop use. |
| `grafana/mimir:3.0.6` filesystem ruler storage | Mimir with MinIO (`minio/minio`) | MinIO adds another container and S3-style config complexity — adds no workshop value. Filesystem ruler is documented as valid for single-node deployments. |
| `prometheusremotewrite` exporter to Mimir | `otlp_http/mimir` exporter (`/otlp/v1/metrics`) | Prometheus remote_write is the path verified in official examples. The OTLP ingest path works too but exemplar passthrough is better tested via the remote_write path in current Collector contrib versions. Either works. |
| `grafana/tempo:2.10.5` standalone | Keep traces in otel-lgtm | The decomposition IS the lesson in Feature 1 — standalone Tempo is the goal. |
| `opentelemetry-sdk-extension-incubator:1.61.0-alpha` | (not recommended) | None of the 8 v2.0 features require the incubator. The incubator currently contains only View file configuration and a declarative config loader — not relevant here. Do not add to reduce dependency surface. |
| `opentelemetry-extension-trace-propagators` | (not recommended) | The demo uses W3C TraceContext (W3CTraceContextPropagator is the default in autoconfigure). B3 and Jaeger propagators are not needed for any v2.0 feature. The artifact is already in the BOM but should not be added. |
| `opentelemetry-sdk-extension-jaeger-remote-sampler` | (not recommended) | Tail sampling is done at the Collector level (Feature 2). The Jaeger remote sampler is for SDK-side tail sampling that fetches sampling config from a Jaeger backend — not applicable here. |
| `spring-boot-starter-data-jpa` using Hibernate's OTel integration | Manual SDK spans wrapping `EntityManager` calls | Hibernate 6.6 ships an experimental OTel integration module (`hibernate-micrometer`), but it routes through Micrometer, which is explicitly out of scope per PROJECT.md. The workshop wants attendees to write `SPAN_KIND_CLIENT` spans manually around JPA operations. |

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `otel/opentelemetry-collector:0.151.0` (core image) | Missing `tail_sampling` processor and `loki` exporter — both are contrib-only components | `otel/opentelemetry-collector-contrib:0.151.0` |
| `grafana/otel-lgtm:*` for v2.0 | The entire point of Feature 1 is to decompose this single container. Do not keep or add it for v2.0. | Five separate containers as above |
| `grafana/grafana:latest` | Floating tag breaks reproducibility across workshop sessions | Pin to `grafana/grafana:13.0.1` |
| `grafana/tempo:latest`, `grafana/loki:latest`, `grafana/mimir:latest` | Same floating tag problem | Pin to exact patch versions above |
| `io.opentelemetry:opentelemetry-sdk-extension-incubator` | No v2.0 feature requires it; adds an `-alpha` coordinate to the pom for no benefit | Omit |
| `io.opentelemetry:opentelemetry-extension-trace-propagators` | B3/Jaeger propagators unused in this demo stack | Omit |
| `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` | Auto-instrumentation wrapper — explicitly out of scope per PROJECT.md | Manual SDK setup |
| `io.micrometer:micrometer-tracing-bridge-otel` | Micrometer bridge — explicitly out of scope per PROJECT.md | Direct OTel API |
| `hibernate-micrometer` (Hibernate OTel integration module) | Routes through Micrometer — out of scope | Manual `SpanKind.CLIENT` wrapping with `opentelemetry-api` |
| `org.springframework.boot:spring-boot-starter-webflux` for RestClient | RestClient is in `spring-boot-starter-web` (Spring MVC, already present). WebClient requires WebFlux which adds Reactor — unnecessary complexity for a blocking HTTP demo. | `RestClient` from existing `spring-boot-starter-web` + manual `TextMapSetter` |
| Grafana Alloy (`grafana/alloy`) | alloy is Grafana's next-gen collector product; it would replace the OTel Collector and obscure the standard OTel pipeline config that attendees will use at work | `otel/opentelemetry-collector-contrib:0.151.0` |

---

## Stack Patterns by Variant

**If Tempo remote_write target is Mimir vs Prometheus:**
- `tempo.yaml` `metrics_generator.storage.remote_write.url` points to `http://mimir:9009/api/v1/push`
- If you use standalone Prometheus instead of Mimir, point to `http://prometheus:9090/api/v1/write` and add `--enable-feature=exemplar-storage` to Prometheus startup flags
- Mimir is preferred for v2.0 because it also hosts the recording rules (Ruler) — no second Prometheus container needed

**If recording rules lesson is skipped:**
- Remove the `ruler` section from `mimir.yaml` and `loki.yaml`
- Skip the `POST /prometheus/config/v1/rules/{namespace}` call from workshop instructions
- Mimir and Loki still work fine without the ruler component active

**If tail sampling lesson is standalone (not combined with metrics/logs):**
- Add a separate pipeline `traces/sampled` with `tail_sampling` in the Collector config
- Route to a separate Tempo instance or use the same Tempo with a different tenant header
- For workshop, combining tail sampling in the main traces pipeline is simpler

**If running on Apple Silicon (arm64):**
- All five images publish multi-arch manifests: `otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, `grafana/loki:3.7.1`, `grafana/grafana:13.0.1` — no `--platform linux/amd64` workaround needed

---

## Version Compatibility Matrix

| Component A | Compatible With | Notes |
|-------------|-----------------|-------|
| `otel/opentelemetry-collector-contrib:0.151.0` | OTel SDK `1.61.0` OTLP | SDK 1.61.0 OTLP output is compatible with Collector 0.151.0 receiver; OTLP protocol is versioned separately and stable |
| `otel/opentelemetry-collector-contrib:0.151.0` | `grafana/tempo:2.10.5` (OTLP gRPC/HTTP) | Collector sends OTLP; Tempo receives OTLP — protocol compatibility guaranteed |
| `otel/opentelemetry-collector-contrib:0.151.0` | `grafana/mimir:3.0.6` (Prometheus remote_write) | `prometheusremotewrite` exporter sends PRW 1.0; Mimir `/api/v1/push` accepts PRW — stable format |
| `otel/opentelemetry-collector-contrib:0.151.0` | `grafana/loki:3.7.1` (Loki HTTP push) | `lokiexporter` sends to `/loki/api/v1/push` — compatible |
| `grafana/grafana:13.0.1` | `grafana/mimir:3.0.6` (Prometheus datasource at `:9009/prometheus`) | Grafana Prometheus datasource queries Mimir's `/prometheus` prefix — confirmed by official mimir docs |
| `grafana/grafana:13.0.1` | `grafana/tempo:2.10.5` (Tempo datasource at `:3200`) | Confirmed in official Tempo docker-compose example using Grafana 13.0.1 |
| `grafana/grafana:13.0.1` | `grafana/loki:3.7.1` (Loki datasource at `:3100`) | Standard pairing; same versions bundled in otel-lgtm 0.26.0 |
| `grafana/tempo:2.10.5` metrics_generator | `grafana/mimir:3.0.6` remote_write | Tempo sends Prometheus remote_write to Mimir `/api/v1/push` — confirmed in tempo.yaml example; `send_exemplars: true` works |
| `spring-boot:3.4.13` | `hibernate:6.6.39.Final` (via `spring-boot-starter-data-jpa`) | BOM-managed — Spring Boot tested this combo. No override needed |
| `spring-boot:3.4.13` | `postgresql:42.7.8` (BOM-managed) | Confirmed in Spring Boot 3.4.13 BOM (`Postgresql: 42.7.8`) |
| `spring-boot:3.4.13` | `HikariCP:5.1.0` (BOM-managed) | v1.0 already uses HikariCP from Phase 8; no conflict |
| `opentelemetry-sdk:1.61.0` | `ExemplarFilter.traceBased()` | `ExemplarFilter` interface introduced in SDK 1.56.0; present and stable in 1.61.0 — no new artifact needed |
| `opentelemetry-sdk:1.61.0` | `Sampler.parentBased()`, `Sampler.traceIdRatioBased()` | Both present in `opentelemetry-sdk-trace` since SDK 1.0; stable API |

---

## Confidence Assessment

| Recommendation | Confidence | Source |
|----------------|------------|--------|
| OTel Collector Contrib `0.151.0` is latest stable | HIGH | GitHub Releases API (`open-telemetry/opentelemetry-collector-releases`) — published 2026-04-29 |
| OTel Collector Contrib `0.151.0` on Docker Hub, multi-arch | HIGH | Docker Hub API query — `0.151.0-amd64` and `0.151.0-arm64` tags confirmed |
| `tailsamplingprocessor` is contrib-only (not in core) | HIGH | OTel Collector Contrib README + component registry — confirmed contrib |
| `lokiexporter` is contrib-only | HIGH | OTel Collector Contrib README |
| `grafana/tempo:2.10.5` is latest stable | HIGH | GitHub Releases API (`grafana/tempo`) — `v2.10.5` published 2026-04-23; Docker Hub tag confirmed |
| `grafana/mimir:3.0.6` is latest stable 3.x | HIGH | GitHub Releases API (`grafana/mimir`) — `mimir-3.0.6` published 2026-04-20; Docker Hub tag confirmed |
| Mimir 3.0 monolithic mode + filesystem ruler supported | HIGH | Official Mimir HTTP API docs confirm Ruler in monolithic mode; mimir.yaml pattern from official tutorials |
| Mimir accepts Prometheus remote_write at `/api/v1/push` | HIGH | Official Grafana Mimir HTTP API docs confirmed |
| Mimir has exemplar query API (`/prometheus/api/v1/query_exemplars`) | HIGH | Official Grafana Mimir HTTP API docs endpoint list |
| `grafana/loki:3.7.1` is latest stable | HIGH | GitHub Releases API (`grafana/loki`) — `v3.7.1` published 2026-03-27; Docker Hub tag confirmed |
| Loki recording rules stable in 3.7.x | HIGH | Context7 `/grafana/loki` — `record:` key in rule groups documented in official alert/recording rules docs |
| `grafana/grafana:13.0.1` is latest stable | HIGH | GitHub Releases API (`grafana/grafana`) — `v13.0.1` published 2026-04-17; Docker Hub tag confirmed |
| `exemplarTraceIdDestinations` in Grafana provisioning YAML | HIGH | Official grafana-datasources.yaml in grafana/docker-otel-lgtm main branch |
| `ExemplarFilter.traceBased()` in OTel SDK 1.56.0+ | HIGH | Context7 OTel Java apidiff docs for SDK 1.56.0 — `ExemplarFilter` interface introduced |
| `spring-boot-starter-data-jpa` Hibernate `6.6.39.Final` in SB 3.4.13 BOM | HIGH | Direct read of `spring-boot-dependencies/build.gradle@v3.4.13` via GitHub raw — `library("Hibernate", "6.6.39.Final")` |
| PostgreSQL JDBC `42.7.8` in SB 3.4.13 BOM | HIGH | Direct read of `spring-boot-dependencies/build.gradle@v3.4.13` — `library("Postgresql", "42.7.8")` |
| No new OTel artifacts needed (sampling, baggage, exemplars) | HIGH | All SDK classes traced to existing `opentelemetry-sdk` and `opentelemetry-api` artifacts already in BOM |
| `opentelemetry-sdk-extension-incubator` NOT needed | HIGH | Incubator README read directly from GitHub main — feature content is view file config and declarative config, not samplers or exemplars |

---

## Sources

- [OTel Collector Releases GitHub API](https://github.com/open-telemetry/opentelemetry-collector-releases/releases) — `v0.151.0` published 2026-04-29
- [OTel Collector Contrib Docker Hub](https://hub.docker.com/r/otel/opentelemetry-collector-contrib/tags) — `0.151.0` tag with arm64/amd64 confirmed
- [OTel Collector tail_sampling README](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md) — policy types: `status_code`, `latency`, `probabilistic`; `decision_wait`, `num_traces` config fields
- [OTel Collector prometheusremotewrite exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/exporter/prometheusremotewriteexporter/DESIGN.md) — PRW exporter design; exemplar handling
- [OTel Collector loki exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/exporter/lokiexporter/README.md) — endpoint config
- [Grafana Tempo releases GitHub API](https://github.com/grafana/tempo/releases) — `v2.10.5` published 2026-04-23
- [Grafana Tempo Docker Hub](https://hub.docker.com/r/grafana/tempo/tags) — `2.10.5` tag with arm64/amd64 confirmed
- [Grafana Tempo single-binary docker-compose example](https://github.com/grafana/tempo/blob/main/example/docker-compose/single-binary/) — `tempo.yaml`, `docker-compose.yaml`, `grafana-datasources.yaml` read directly
- [grafana/docker-otel-lgtm tempo-config.yaml](https://github.com/grafana/docker-otel-lgtm/blob/main/docker/tempo-config.yaml) — `metrics_generator.remote_write.send_exemplars: true` confirmed
- [grafana/docker-otel-lgtm loki-config.yaml](https://github.com/grafana/docker-otel-lgtm/blob/main/docker/loki-config.yaml) — filesystem storage + inmemory ring config confirmed
- [grafana/docker-otel-lgtm grafana-datasources.yaml](https://github.com/grafana/docker-otel-lgtm/blob/main/docker/grafana-datasources.yaml) — `exemplarTraceIdDestinations`, `tracesToLogsV2`, `derivedFields` config confirmed
- [Grafana Mimir releases GitHub API](https://github.com/grafana/mimir/releases) — `mimir-3.0.6` published 2026-04-20
- [Grafana Mimir Docker Hub](https://hub.docker.com/r/grafana/mimir/tags) — `3.0.6` tag confirmed
- [Grafana Mimir HTTP API docs](https://grafana.com/docs/mimir/latest/references/http-api/) — `POST /api/v1/push`, `POST /otlp/v1/metrics`, `GET /prometheus/api/v1/query_exemplars`, ruler endpoints all confirmed
- [Grafana Loki releases GitHub API](https://github.com/grafana/loki/releases) — `v3.7.1` published 2026-03-27
- [Grafana Loki Docker Hub](https://hub.docker.com/r/grafana/loki/tags) — `3.7.1` tag with arm64/amd64 confirmed
- [Grafana Loki recording rules docs](https://github.com/grafana/loki/blob/main/docs/sources/alert/_index.md) — `record:` key in rule groups; ruler config (local storage) confirmed
- [Grafana releases GitHub API](https://github.com/grafana/grafana/releases) — `v13.0.1` published 2026-04-17
- [Grafana Docker Hub](https://hub.docker.com/r/grafana/grafana/tags) — `13.0.1` tag confirmed
- [Context7 OTel Java — ExemplarFilter apidiff](https://github.com/open-telemetry/opentelemetry-java/blob/main/docs/apidiffs/1.56.0_vs_1.55.0/opentelemetry-sdk-metrics.txt) — `ExemplarFilter.traceBased()` introduced SDK 1.56.0; stable through 1.61.0
- [Context7 OTel Java — samplers](https://context7.com/open-telemetry/opentelemetry-java/llms.txt) — `Sampler.parentBased()`, `Sampler.traceIdRatioBased()` in `opentelemetry-sdk`
- [Context7 OTel Java — baggage](https://context7.com/open-telemetry/opentelemetry-java/llms.txt) — `Baggage.builder()` in `opentelemetry-api`
- [OTel SDK incubator README](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/incubator/README.md) — confirms incubator contains view file config / declarative config only; NOT needed for v2.0
- [opentelemetry-bom:1.61.0 on Maven Central](https://repo1.maven.org/maven2/io/opentelemetry/opentelemetry-bom/1.61.0/opentelemetry-bom-1.61.0.pom) — artifact list confirmed: `opentelemetry-sdk`, `opentelemetry-api`, `opentelemetry-exporter-otlp`, `opentelemetry-sdk-extension-autoconfigure` — no incubator
- [Spring Boot 3.4.13 BOM — build.gradle](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.4.13/spring-boot-project/spring-boot-dependencies/build.gradle) — `Hibernate: 6.6.39.Final`, `Postgresql: 42.7.8`, `HikariCP: 5.1.0` confirmed
- [Spring Data JPA docs](https://github.com/spring-projects/spring-data-jpa) — `@Repository`, `@Transactional`, `EntityManager` API patterns

---
*Stack research for: OSE OTel Demo v2.0 Production Shapes — additions to v1.0 locked stack*
*Researched: 2026-05-02*
