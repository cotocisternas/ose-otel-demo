# Architecture: v2.0 Production Shapes Integration

**Domain:** OpenTelemetry workshop demo — v2.0 integration architecture for 8 new features
**Researched:** 2026-05-02
**Confidence:** HIGH (OTel Collector/tail-sampling docs verified via Context7; Loki/Tempo/Mimir via official Grafana docs; Java SDK exemplars and baggage verified via Context7 OTel Java)

---

## Anchor: v1.0 Architecture (Do Not Modify)

Before describing v2.0 additions, the immutable v1.0 anchors:

```
producer-service (host JVM)                 consumer-service (host JVM)
  OtelSdkConfiguration.java (DUPLICATED)       OtelSdkConfiguration.java (DUPLICATED)
    buildTracerProvider()                         buildTracerProvider()
    buildMeterProvider()                          buildMeterProvider()
    buildLoggerProvider()                         buildLoggerProvider()
  OrderController → OrderService                OrderListener → ProcessingService
  → OrderPublisher                              OrderRepository (JDBC, Phase 8)
  InstrumentedJedisPool (Phase 8)               HikariCpConnectionGauge (Phase 8)
         │ AMQP :5672                                     ▲
         └──────── RabbitMQ (docker-compose) ─────────────┘
         │ OTLP/gRPC :4317                                │ OTLP/gRPC :4317
         └──────── grafana/otel-lgtm:0.26.0 (single container) ─────────┘

otel-bootstrap/ (shared Maven module)
  amqp/TracingMessagePostProcessor.java    ← producer-side inject
  amqp/TracingMessageListenerAdvice.java   ← consumer-side extract
  amqp/MessagePropertiesSetter.java
  amqp/MessagePropertiesGetter.java
```

Key constraints that cascade into v2.0:
- `OtelSdkConfiguration` is intentionally duplicated per service (TRACE-01 / DOC-05 lesson). This constraint is preserved — v2.0 phases that touch SDK config must modify BOTH files.
- `otel-bootstrap` is shared only for AMQP propagation pair. This boundary must remain: shared = exchange-type-agnostic propagation helpers; per-service = anything that names a concrete exchange/queue or knows a specific semconv attribute set.
- OTLP endpoint remains `http://localhost:4317` throughout. The decomposed Collector exposes the same ports — SDK config in both service OtelSdkConfiguration files does not change.
- Phase numbering continues from Phase 10.

---

## v2.0 Feature Integration Map

### Feature 1: Decompose otel-lgtm into Separate Collector + Backends

**What changes:** Replace the single `grafana/otel-lgtm:0.26.0` container in `docker-compose.yml` with five containers: `otel-collector`, `tempo`, `mimir`, `loki`, `grafana`. This is a pure infrastructure change — no Java source files are modified.

**New components:**

| Component | Lives at | Purpose |
|-----------|----------|---------|
| `otel-collector` service | `docker-compose.yml` | Receives OTLP/gRPC :4317 + HTTP :4318; routes to backends |
| `infra/observability/otelcol-config.yaml` | `infra/observability/otelcol-config.yaml` | Teaching-grade Collector pipeline (one of each processor type) |
| `infra/observability/tempo-config.yaml` | `infra/observability/tempo-config.yaml` | Tempo single-node config with metrics-generator + exemplars |
| `infra/observability/mimir-config.yaml` | `infra/observability/mimir-config.yaml` | Mimir single-binary config |
| `infra/observability/loki-config.yaml` | `infra/observability/loki-config.yaml` | Loki config with ruler remote-write to Mimir |
| `grafana/datasources.yaml` | `grafana/datasources.yaml` | Provisioned datasources with cross-signal datalinks |
| `grafana/dashboards.yaml` | `grafana/dashboards.yaml` | Dashboard provisioning config (existing file, updated path) |

**Modified components:**

| Component | Change |
|-----------|--------|
| `docker-compose.yml` | Remove `lgtm:` service; add `otel-collector:`, `tempo:`, `mimir:`, `loki:`, `grafana:` services |
| `grafana/prometheus.yaml` | Move into `infra/observability/` (or repurpose as Mimir's prometheus.yaml override); the scrape configs become part of the Collector's Prometheus receiver or Mimir's scrape config |

**Infra directory layout:**

```
infra/
  observability/
    otelcol-config.yaml
    tempo-config.yaml
    mimir-config.yaml
    loki-config.yaml
grafana/
  dashboards/
    dashboards.yaml
    *.json
  datasources.yaml   ← NEW (was auto-provisioned by lgtm; now explicit)
```

**Teaching-grade `otelcol-config.yaml` — one processor of each canonical type:**

The key pedagogical point is that the Collector pipeline is visible in a single file with each stage named and commented. The pipeline structure for workshop use:

```yaml
# infra/observability/otelcol-config.yaml
# Canonical OTel Collector pipeline for the OSE OTel Demo (v2.0).
# One of each processor type so attendees can read a breadth-first
# survey of Collector primitives in one config file.

receivers:
  otlp:
    protocols:
      grpc:
        endpoint: "0.0.0.0:4317"   # SDK default endpoint — unchanged from v1.0
      http:
        endpoint: "0.0.0.0:4318"   # HTTP alternative (demo uses gRPC, but exposed)

processors:
  # 1. memory_limiter — MUST come first. Prevents OOM by back-pressuring receivers.
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128

  # 2. resource — Resource-level attribute enrichment.
  #    Adds deployment.environment if missing (attendees learn Resource vs Span attributes).
  resource:
    attributes:
      - key: deployment.environment
        value: "workshop"
        action: upsert

  # 3. attributes (span/log attribute transform) — Span/log-level attribute manipulation.
  #    Example: redact a sensitive header key present in incoming spans.
  attributes:
    actions:
      - key: http.request.header.authorization
        action: delete

  # 4. filter — Drop noisy spans (e.g., actuator health checks).
  #    attendees learn selective dropping at the Collector vs. at SDK sampler.
  filter:
    error_mode: ignore
    traces:
      span:
        - 'attributes["url.path"] == "/actuator/health"'

  # 5. tail_sampling — Keep-on-error first, then latency, then probabilistic fallback.
  #    Lives in the traces pipeline only. See Feature 2 for full config.
  tail_sampling:
    decision_wait: 10s
    num_traces: 10000
    expected_new_traces_per_sec: 50
    policies:
      - name: keep-on-error
        type: status_code
        status_code:
          status_codes: [ERROR]
      - name: keep-slow-traces
        type: latency
        latency:
          threshold_ms: 1000
      - name: probabilistic-fallback
        type: probabilistic
        probabilistic:
          sampling_percentage: 20

  # 6. batch — MUST come last in each pipeline (after samplers).
  #    Groups records for efficient export; reduces HTTP round trips.
  batch:
    send_batch_size: 512
    timeout: 5s

exporters:
  # Traces → Tempo (OTLP HTTP on internal port 4418 as in bundled otel-lgtm)
  otlphttp/tempo:
    endpoint: "http://tempo:4418"
    tls:
      insecure: true

  # Metrics → Mimir via Prometheus Remote Write
  prometheusremotewrite/mimir:
    endpoint: "http://mimir:9009/api/v1/push"
    tls:
      insecure: true

  # Logs → Loki (native OTLP endpoint)
  otlphttp/loki:
    endpoint: "http://loki:3100/otlp"
    tls:
      insecure: true

extensions:
  health_check:
    endpoint: "0.0.0.0:13133"

service:
  extensions: [health_check]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, resource, attributes, filter, tail_sampling, batch]
      exporters: [otlphttp/tempo]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [prometheusremotewrite/mimir]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [otlphttp/loki]
```

**Port contract — unchanged for SDK:**

| Port | Was (otel-lgtm) | Is (decomposed) | SDK impact |
|------|-----------------|-----------------|------------|
| :4317 (gRPC) | otel-lgtm → embedded Collector | otel-collector → Collector | None — OTLP_ENDPOINT unchanged |
| :4318 (HTTP) | otel-lgtm → embedded Collector | otel-collector → Collector | None |
| :3000 (Grafana) | otel-lgtm → Grafana | grafana → Grafana | None |
| :3200 (Tempo) | otel-lgtm internal | tempo:3200 host-exposed | None (apps don't speak Tempo directly) |
| :9009 (Mimir) | otel-lgtm as :9090 | mimir:9009 | None |
| :3100 (Loki) | otel-lgtm internal | loki:3100 | None |

**Grafana datasources provisioning (`grafana/datasources.yaml`):**

```yaml
apiVersion: 1
datasources:
  - name: Tempo
    type: tempo
    url: http://tempo:3200
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        filterByTraceID: true
        filterBySpanID: false
      tracesToMetrics:
        datasourceUid: mimir
        queries:
          - name: "Request rate"
            query: "rate(http_server_request_duration_seconds_count{service_name=\"$__tags.service.name\"}[5m])"
      serviceMap:
        datasourceUid: mimir
      nodeGraph:
        enabled: true
      lokiSearch:
        datasourceUid: loki

  - name: Loki
    uid: loki
    type: loki
    url: http://loki:3100
    jsonData:
      derivedFields:
        - name: trace_id
          matcherRegex: "trace_id=(\\w+)"
          url: "$${__value.raw}"
          datasourceUid: tempo

  - name: Mimir
    uid: mimir
    type: prometheus
    url: http://mimir:9009/prometheus
    jsonData:
      exemplarTraceIdDestinations:
        - name: trace_id
          datasourceUid: tempo
```

**Data flow diagram (decomposed):**

```
producer/consumer (host) ─── OTLP/gRPC :4317 ──► otel-collector
                                                       │
                              ┌────────────────────────┼────────────────────────┐
                              ▼ traces                  ▼ metrics                ▼ logs
                         Tempo :4418             Mimir :9009/api/v1/push    Loki :3100/otlp
                              │                         │                        │
                              └────────────────── Grafana :3000 ─────────────────┘
                                                 (datasources.yaml wires
                                                  cross-signal datalinks)
```

**Existing v1.0 infra exporters (RabbitMQ, Redis, Postgres exporters):** The Prometheus scrape job config currently mounted at `grafana/prometheus.yaml` targeted the bundled Prometheus inside `otel-lgtm`. In the decomposed stack, these scrape configs move to a separate `mimir/prometheus.yaml` (or equivalent Mimir `-config.file` override), or the exporters are wired as Collector Prometheus receiver jobs. The simplest path: add a `prometheus` receiver block in `otelcol-config.yaml` that scrapes the three exporters and forwards via the metrics pipeline to Mimir.

---

### Feature 2: Tail Sampling at the Collector

**What changes:** The `tail_sampling` processor shown above in Feature 1 is Feature 2's main deliverable. Tail sampling lives as a single processor stage within the Collector's traces pipeline — NOT a separate "sampling Collector." The single-Collector topology is correct for a workshop: adding a second Collector for sampling requires load-balancing the `loadbalancing` exporter, which is a production concern, not a teaching surface.

**Policy ordering rationale (keep-on-error first, latency second, probabilistic fallback):**

OTel tail sampling evaluates policies in order and samples a trace if ANY policy votes to keep it (`OR` semantics). The ordering matters because the decision is made once after `decision_wait` elapses:

1. `keep-on-error` (`status_code: [ERROR]`) — Never drop traces that contain an error span. This is the non-negotiable production rule. Workshop attendees see this as "error traces always survive regardless of volume."
2. `keep-slow-traces` (`latency: threshold_ms: 1000`) — Keep any trace whose total duration exceeds 1 second. Maps to SLO breach investigation. The 1s threshold is demo-appropriate; production teams tune per their p99 SLO.
3. `probabilistic-fallback` (`probabilistic: 20%`) — For traces that are neither errors nor slow, keep a 20% random sample. This is the "normal traffic slice" that prevents Tempo from being empty during workshop demos. For production, 1-5% is common.

**New components:**

| Component | Lives at | Change |
|-----------|----------|--------|
| `tail_sampling` processor block | `infra/observability/otelcol-config.yaml` | Added to traces pipeline (see Feature 1 config) |

**Modified components:** None beyond what Feature 1 establishes.

**No SDK change required.** The SDK's `BatchSpanProcessor` continues to export 100% of spans to the Collector. The Collector decides which complete traces to forward to Tempo. SDK-side `parentBased(alwaysOn())` sampler in `buildTracerProvider()` is left unchanged — the demo's teaching point is Collector-side tail sampling, not SDK-side head sampling.

**Important gotcha — `decision_wait` vs. workshop latency:** `decision_wait: 10s` means Tempo receives traces up to 10 seconds after the last span. For a workshop demo with immediate `POST /orders`, the attendee sees traces with a ~10s delay. Set this to `5s` if the workshop's Grafana refresh window feels too slow. Do not set below `5s` — some RabbitMQ consumer paths take 2-3 seconds in simulated failure mode.

---

### Feature 3: Exemplars — Metrics-to-Traces Click-Through

**What changes:**

Exemplars require two modifications:

1. **SDK side (minimal):** `SdkMeterProviderBuilder.setExemplarFilter(ExemplarFilter.traceBased())` in BOTH `OtelSdkConfiguration` files. The `traceBased()` filter stamps each histogram observation with the active span's `trace_id` and `span_id` when the measurement is recorded inside an active span scope. This is the *only* SDK change — no new instruments needed.

2. **Collector/backend side:** The `prometheusremotewrite/mimir` exporter must forward exemplars. The Prometheus remote write protocol carries exemplars natively. Mimir 2.x accepts and stores them. Grafana's Tempo datasource `exemplarTraceIdDestinations` config (in `datasources.yaml`, Feature 1) creates the click-through datalink.

**Demo exemplar source:** The `http.server.request.duration` histogram in `HttpServerSpanFilter.java` (producer-service) is the best exemplar surface — it is already recording inside the SERVER span scope (so `trace_id` is always available), and HTTP duration is what attendees already have open in Grafana dashboards. The `orders.queue.depth.estimate` ObservableGauge in consumer-service is NOT suitable — ObservableGauges are reported asynchronously via a callback and no span is active at callback time.

**Modified components:**

| Component | Change |
|-----------|--------|
| `producer-service/.../config/OtelSdkConfiguration.java` | `buildMeterProvider()`: add `.setExemplarFilter(ExemplarFilter.traceBased())` |
| `consumer-service/.../config/OtelSdkConfiguration.java` | Same change in its `buildMeterProvider()` |
| `infra/observability/otelcol-config.yaml` | Confirm `prometheusremotewrite/mimir` exporter does not strip exemplars (default: it preserves them) |
| `grafana/datasources.yaml` | Add `exemplarTraceIdDestinations` block to Mimir datasource (see Feature 1 config above) |

**New components:** None beyond Feature 1 infra.

**Data flow for exemplars:**

```
HttpServerSpanFilter.record(duration)
  [active SERVER span → trace_id + span_id in Context]
  ExemplarFilter.traceBased() stamps measurement
       │
       ▼ (PeriodicMetricReader every 10s)
OtlpGrpcMetricExporter → otel-collector → prometheusremotewrite/mimir
                                           (exemplars embedded in Prometheus RemoteWrite proto)
       │
       ▼
Mimir stores histogram + exemplar{trace_id="..."}
       │
       ▼
Grafana metric panel → user clicks histogram bar
       → datalink resolves trace_id → opens Tempo trace
```

**Build dependency:** Feature 3 requires Feature 1 (decomposed Collector + Grafana datasources provisioning). It does not require Feature 2 (tail sampling), but in practice exemplars should be tested AFTER tail sampling is wired — if tail sampling drops 80% of traces, exemplar click-throughs will find Tempo empty 80% of the time. For workshop demos, keep probabilistic fallback at ≥20%.

---

### Feature 4: Log-Based Metrics (Loki Recording Rules → Mimir)

**What changes:** Loki recording rules evaluate a LogQL metric query on a schedule and remote-write the result time series to Mimir. The attendee lesson is: "You can derive a metric from a log pattern even when the SDK never recorded that metric explicitly."

**Architecture decision — where do recording rules live?**

Recording rules are Loki-side, not Mimir-side. The workflow:
1. Loki's `ruler` component evaluates a LogQL expression periodically (e.g., every 1m).
2. The computed time series is pushed to Mimir via Prometheus remote write.
3. The result appears in Mimir under a `recording_rules` job label and is queryable from Grafana.

This is NOT the same as Mimir's own recording rules (which operate on PromQL over already-stored metrics). Loki recording rules operate over raw log streams.

**New components:**

| Component | Lives at | Purpose |
|-----------|----------|---------|
| `infra/observability/loki-rules/` directory | `infra/observability/loki-rules/` | Loki ruler rule files (YAML) |
| `infra/observability/loki-rules/order-errors.yaml` | same | Example recording rule: error-log rate → Mimir |

**Example Loki recording rule file (`order-errors.yaml`):**

```yaml
# infra/observability/loki-rules/order-errors.yaml
# Loki recording rules for the OSE OTel Demo (v2.0 Feature 4).
# These rules derive metrics FROM LOGS using LogQL — the attendee lesson is
# that structured log fields can be aggregated without SDK metric instrumentation.
#
# Result metric lands in Mimir under job="loki-recording-rules".
# Grafana dashboard can query it alongside SDK-emitted metrics.

groups:
  - name: order_processing
    interval: 1m
    rules:
      # processing.error.rate — rate of ERROR-level log lines in the
      # order-consumer service. Derived purely from log stream, NOT from a
      # Counter instrument in ProcessingService.java.
      - record: order_processing_errors_per_minute
        expr: |
          sum(
            rate(
              {service_name="order-consumer"} |= "ERROR" [5m]
            )
          ) by (service_name)

      # order_published_log_rate — cross-check against the SDK counter.
      # Attendees compare this log-derived rate vs. orders_created_total
      # from the SDK LongCounter. Discrepancy teaches "logs and metrics
      # can diverge due to buffering/sampling."
      - record: order_published_log_rate
        expr: |
          sum(
            rate(
              {service_name="order-producer"} |= "published order" [5m]
            )
          ) by (service_name)
```

**Modified components:**

| Component | Change |
|-----------|--------|
| `infra/observability/loki-config.yaml` | Enable `ruler` component, configure `ruler_storage.local.directory`, set `remote_write` to Mimir endpoint |
| `docker-compose.yml` | Mount `infra/observability/loki-rules/` into the loki container at the ruler rules path |

**Loki config ruler additions:**

```yaml
# Add to loki-config.yaml ruler section:
ruler:
  storage:
    type: local
    local:
      directory: /loki/rules
  rule_path: /loki/rules-temp
  alertmanager_url: http://localhost:9093
  remote_write:
    enabled: true
    clients:
      mimir:
        url: http://mimir:9009/api/v1/push
        tls_config:
          insecure_skip_verify: true
        write_relabel_configs:
          - action: replace
            target_label: job
            replacement: loki-recording-rules
```

**Data flow:**

```
Loki log streams (order-consumer, order-producer)
       │ (ruler evaluates LogQL every 1m)
       ▼
Loki ruler → remote_write → Mimir :9009/api/v1/push
                                   │
                                   ▼
                    Mimir stores metric series {job="loki-recording-rules"}
                                   │
                                   ▼
                    Grafana Mimir datasource → dashboards + alerts
```

**Build dependency:** Feature 4 requires Feature 1 (Loki + Mimir as separate containers with their own config files). Feature 4 does NOT require Features 2 or 3.

---

### Feature 5: JDBC/JPA Database Spans (Beyond Phase 8)

**Context:** Phase 8 (v1.0 bonus step) already ships manual `SPAN_KIND_CLIENT` instrumentation for:
- `InstrumentedJedisPool.setIfAbsent()` in producer-service (Valkey SET)
- `OrderRepository.insertProcessedOrder()` in consumer-service (PostgreSQL INSERT via JdbcTemplate)

v2.0 Feature 5 extends this with Spring Data JPA repository spans, adding a second ORM-layer instrumentation pattern. The pedagogical delta: Phase 8 showed raw JDBC (`JdbcTemplate.update`); Feature 5 shows JPA (`@Transactional` boundary + `JpaRepository.save()`), which is the more common enterprise pattern.

**Architecture decision — where does JPA span instrumentation live?**

Three options were considered:

- **Option A: Spring AOP `@Around` advice on `@Repository` beans** — Intercepts every JPA repository call via a pointcut. Adds spans around the transactional method. Complex Spring AOP ordering with `@Transactional` (transaction proxy must be outermost).
- **Option B: `EntityListeners` / Hibernate `Interceptor` callbacks** — Pre/post persist/update/delete events fire inside the Hibernate session. Very low level, misses the full JPA call including query planning.
- **Option C: Explicit `tracer.spanBuilder()` wrapping inside a `@Transactional` service method** — Same D-01 inline template already in Phase 8. No new infrastructure, directly visible to attendees.

**Recommendation: Option C** — consistent with the workshop's D-01 principle (inline, visible, no magic). JPA instrumentation lives in a new `OrderJpaService.java` in consumer-service that wraps `orderJpaRepository.save(entity)` in a CLIENT span. The `@Transactional` boundary and the CLIENT span are clearly layered: the outer span covers the full transaction; attendees can see that the DB CLIENT span is a child of the CONSUMER span, not the transaction boundary itself.

**No shared module for JPA spans.** The v1.0 decision PROP-04 kept AMQP propagation in `otel-bootstrap` because the same code runs symmetrically in both services. JPA instrumentation only exists in the consumer-service (where orders are persisted). Per TRACE-01 / DOC-05, per-service instrumentation stays per-service.

**New components:**

| Component | Lives at | Purpose |
|-----------|----------|---------|
| `consumer-service/.../domain/Order.java` | `consumer-service/src/main/java/com/example/consumer/domain/` | JPA `@Entity` for the order |
| `consumer-service/.../db/OrderJpaRepository.java` | `consumer-service/src/main/java/com/example/consumer/db/` | Spring Data JPA `JpaRepository<Order, String>` |
| `consumer-service/.../db/OrderJpaService.java` | `consumer-service/src/main/java/com/example/consumer/db/` | Wraps `repository.save()` in CLIENT span |

**Modified components:**

| Component | Change |
|-----------|--------|
| `consumer-service/pom.xml` | Add `spring-boot-starter-data-jpa` |
| `consumer-service/src/main/resources/application.yaml` | Add JPA datasource + Hibernate DDL-auto config |

**Span shape:**

```
CONSUMER span (TracingMessageListenerAdvice)
  └─ INTERNAL "ProcessingService.process"
       └─ CLIENT "SAVE orders" (SpanKind.CLIENT)
            db.system.name = "postgresql"
            db.operation.name = "INSERT"  (Hibernate-generated)
            db.collection.name = "orders"
```

**Teaching delta over Phase 8:** Phase 8 showed the raw JDBC template. Feature 5 shows that even when using Spring Data JPA (which hides the SQL), you still manually wrap the `repository.save()` boundary with a CLIENT span — because the OTel Java agent is not active.

---

### Feature 6: Outbound HTTP-Client Spans

**What changes:** The producer-service makes an outbound HTTP call to a downstream "notification service" (a mock/stub endpoint). The consumer-service alternatively could call a "fulfillment-service" stub. The purpose: demonstrate CLIENT-kind spans for outbound HTTP with `peer.service` propagation.

**Architecture decision — which service and which client?**

The producer-service is the natural owner: it already has an HTTP layer (`HttpServerSpanFilter` for inbound). Adding an outbound call from `OrderService.place()` to a fake notification endpoint keeps the full trace shape: `SERVER → INTERNAL → PRODUCER → ... → CLIENT(http)`. The consumer-service could also make an outbound call but would require adding a new HTTP client dependency where none currently exists.

**HTTP client choice — `RestClient` (Spring 6.1+) with `ClientHttpRequestInterceptor`:**

`RestClient` is the modern synchronous HTTP client in Spring Boot 3.2+. The interceptor pattern is exactly analogous to what `TracingMessagePostProcessor` does for AMQP: a `ClientHttpRequestInterceptor` wraps each request in a CLIENT span, injects W3C `traceparent` header via `TextMapSetter`, and calls `execution.execute()`.

`WebClient` (`ExchangeFilterFunction`) would require adding Reactor/WebFlux to the producer's classpath — an unnecessary dependency for a synchronous demo. RestClient + interceptor is the teaching-grade choice.

**Shared module decision:** The HTTP-client interceptor belongs in `otel-bootstrap`. Rationale: it is exchange-type-agnostic (could be used by producer or consumer), it is a symmetrical propagation helper (inject on outbound = symmetric to TracingMessagePostProcessor's inject on AMQP), and centralizing it avoids the D-01 per-service duplication trap for propagation code.

**New components:**

| Component | Lives at | Purpose |
|-----------|----------|---------|
| `otel-bootstrap/.../http/TracingClientHttpRequestInterceptor.java` | `otel-bootstrap/src/main/java/com/example/otel/http/` | `ClientHttpRequestInterceptor` that wraps each outbound HTTP call in a CLIENT span and injects W3C `traceparent` |
| Fake notification endpoint / mock server | `producer-service/src/main/java/com/example/producer/stub/NotificationStubController.java` | In-process stub that accepts POST and returns 200, so no external dependency is needed |

**Modified components:**

| Component | Change |
|-----------|--------|
| `producer-service/.../config/OtelSdkConfiguration.java` | Add `@Bean RestClient.Builder restClientBuilder(TracingClientHttpRequestInterceptor interceptor)` that registers the interceptor |
| `producer-service/.../domain/OrderService.java` | Inject `RestClient` and add outbound HTTP call after `publisher.publish()` — adds CLIENT(http) span as sibling to PRODUCER span |
| `otel-bootstrap/pom.xml` | No new dep — `spring-web` is already a transitive dep via `spring-boot-starter-amqp` → `spring-context` |
| `producer-service/pom.xml` | Confirm `spring-boot-starter-web` is present (it is, for OrderController) |

**Span shape:**

```
SERVER "POST /orders"
  └─ INTERNAL "OrderService.place"
       ├─ PRODUCER "orders publish"   (existing, AMQP)
       └─ CLIENT "POST /notifications" (new, HTTP outbound)
            http.request.method = "POST"
            server.address = "localhost"  (or stub hostname)
            server.port = 8080
            peer.service = "notification-stub"
            url.full = "http://localhost:8080/stub/notifications"
```

**`TracingClientHttpRequestInterceptor` pattern:**

```java
// otel-bootstrap/.../http/TracingClientHttpRequestInterceptor.java
public class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    private static final TextMapSetter<HttpRequest> SETTER =
        (carrier, key, value) -> carrier.getHeaders().set(key, value);

    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        Span span = tracer.spanBuilder(request.getMethod() + " " + request.getURI().getPath())
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.getMethod().name())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getURI().getHost())
            .setAttribute(ServerAttributes.SERVER_PORT, (long) request.getURI().getPort())
            .setAttribute(UrlAttributes.URL_FULL, request.getURI().toString())
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            otel.getPropagators().getTextMapPropagator()
                .inject(Context.current(), request, SETTER);
            ClientHttpResponse response = execution.execute(request, body);
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                (long) response.getStatusCode().value());
            if (response.getStatusCode().isError()) {
                span.setStatus(StatusCode.ERROR);
            }
            return response;
        } catch (IOException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**Build dependency:** Feature 6 can be built independently of Features 1-5. However, it benefits from Feature 3 (exemplars) being wired, because the outbound HTTP latency histogram will carry exemplars if `ExemplarFilter.traceBased()` is set.

---

### Feature 7: Sampling + Baggage

**What changes:** Two teaching sub-features bundled:

**7a. Head sampling (SDK-side):** Change the root sampler in `buildTracerProvider()` in both `OtelSdkConfiguration` files from `Sampler.parentBased(Sampler.alwaysOn())` to `Sampler.parentBased(Sampler.traceIdRatioBased(0.5))`. The change is intentionally small — one line in each service's config — so the diff reads cleanly. The pedagogical point: head sampling vs. tail sampling, and why `parentBased` is the correct wrapper for distributed scenarios (preserves the decision made at the root).

**7b. Baggage propagation:** Baggage is a W3C-standardized key-value sidecar that rides alongside `traceparent` in HTTP headers and AMQP message headers. The demo flow: the HTTP request sets a baggage entry (`order.customer-tier`), the `TracingMessagePostProcessor` propagates it (automatically — it calls `otel.getPropagators().getTextMapPropagator().inject()` which injects BOTH `traceparent` AND `baggage` header because the propagator is `W3CTraceContextPropagator` + `W3CBaggagePropagator` composite, already wired in v1.0 `OtelSdkConfiguration`), and the consumer reads it and stamps it as a span attribute.

**New components:**

| Component | Lives at | Purpose |
|-----------|----------|---------|
| `otel-bootstrap/.../amqp/BaggageSpanAttributeProcessor.java` | `otel-bootstrap/src/main/java/com/example/otel/amqp/` | OTel `SpanProcessor` that reads baggage entries at span start and copies them as span attributes. Registered in `buildTracerProvider()`. |

**Modified components:**

| Component | Change |
|-----------|--------|
| `producer-service/.../config/OtelSdkConfiguration.java` | `buildTracerProvider()`: swap `alwaysOn()` → `traceIdRatioBased(0.5)`. Add `BaggageSpanAttributeProcessor` to `SdkTracerProvider.builder()`. |
| `consumer-service/.../config/OtelSdkConfiguration.java` | Same two changes. |
| `producer-service/.../api/OrderController.java` | Set baggage entry `order.customer-tier` from request header or payload, make it current before calling `OrderService.place()`. |
| `consumer-service/.../messaging/OrderListener.java` | Read `Baggage.current().getEntryValue("order.customer-tier")` and log it (the `BaggageSpanAttributeProcessor` already stamps it on the span automatically). |

**Baggage flow:**

```
HTTP POST /orders header: baggage: order.customer-tier=premium

OrderController.create()
  Baggage.current().toBuilder()
    .put("order.customer-tier", tier)
    .build().storeInContext(Context.current()).makeCurrent()
             │ (Scope wraps OrderService.place())
             │
  TracingMessagePostProcessor.inject()
    otel.getPropagators().inject()  ← injects traceparent + baggage header
             │ AMQP message headers:
             │   traceparent: 00-<traceId>-<spanId>-01
             │   baggage: order.customer-tier=premium
             ▼
  TracingMessageListenerAdvice.extract()
    otel.getPropagators().extract()  ← restores Context including baggage
             │
  BaggageSpanAttributeProcessor.onStart()
    Baggage.current().forEach((k, v) -> span.setAttribute(k, v.getValue()))
    ← "order.customer-tier"="premium" appears as span attribute
```

**`BaggageSpanAttributeProcessor` lives in `otel-bootstrap`** because it is reusable across both services and is conceptually parallel to the AMQP propagation pair.

**Why `W3CBaggagePropagator` is already wired (no new SDK change needed for propagation):** v1.0 `OtelSdkConfiguration` already constructs:
```java
TextMapPropagator.composite(
    W3CTraceContextPropagator.getInstance(),
    W3CBaggagePropagator.getInstance())
```
The baggage propagator is already registered. The only missing pieces are: (a) setting the baggage at the HTTP layer (OrderController), (b) reading it in the consumer, and (c) the `BaggageSpanAttributeProcessor` to auto-stamp baggage as span attributes.

---

### Feature 8: AMQP Fanout/Topic/DLX Topology

**What changes:** The existing single direct exchange `orders` with queue `orders.created` is augmented (not replaced) with:

1. A **topic exchange** `orders.topic` with routing keys `order.created.#`, demonstrating wildcard routing.
2. A **fanout exchange** `orders.fan` with two queues: `orders.fulfillment` and `orders.audit` — demonstrating one message to multiple consumers.
3. A **DLX** (Dead Letter Exchange) bound to the existing `orders.created` queue, with a `orders.dlq` dead-letter queue — demonstrating message rejection/TTL expiry handling.

**Architecture decision — exchange-type-agnostic propagation:**

`TracingMessagePostProcessor` and `TracingMessageListenerAdvice` are completely exchange-type-agnostic. They operate on `MessageProperties` headers — the AMQP message payload. Exchange type (direct/topic/fanout) determines routing, not headers. Attendees see that the same two classes instrument all three exchange shapes without modification. This is the primary pedagogical point of Feature 8.

**Additional consumers needed:** Feature 8 requires a second consumer for fanout (`orders.audit` queue), because fanout only makes sense if multiple consumers actually exist. This means a new Spring `@RabbitListener` method in consumer-service. It does NOT mean a new service — both `orders.fulfillment` and `orders.audit` listeners can live in the same `consumer-service`.

**New components:**

| Component | Lives at | Purpose |
|-----------|----------|---------|
| `consumer-service/.../messaging/AuditListener.java` | `consumer-service/src/main/java/com/example/consumer/messaging/` | Second `@RabbitListener` on `orders.audit` queue (fanout consumer) |
| `consumer-service/.../config/RabbitTopologyConfig.java` | `consumer-service/src/main/java/com/example/consumer/config/` | Declares topic + fanout + DLX topology beans (`TopicExchange`, `FanoutExchange`, `Queue`, `Binding`, `DeadLetterExchange`) |

**Modified components:**

| Component | Change |
|-----------|--------|
| `producer-service/.../config/RabbitConfig.java` | Add `TopicExchange` and `FanoutExchange` beans + `RabbitTemplate` usage for topic routing key |
| `producer-service/.../messaging/OrderPublisher.java` | Add `publishToTopic()` and `publishToFanout()` methods alongside existing `publish()` to direct exchange |
| `consumer-service/.../config/RabbitConfig.java` | Register `TracingMessageListenerAdvice` on the container factory used by all three consumer queues (existing `setAdviceChain` call applies to all listeners on that factory) |
| `otel-bootstrap/.../amqp/TracingMessagePostProcessor.java` | Verify `receivedExchange` + `receivedRoutingKey` null-handling — fanout exchange sets routing key to `""`, DLX re-queues with `x-death` header. No semantic change needed but nullability guards should be confirmed. |

**DLX flow with tracing:**

```
Message nack'd / TTL expires on orders.created queue
  → RabbitMQ routes to DLX (e.g. orders.dlx exchange)
  → Delivered to orders.dlq queue
  → @RabbitListener on orders.dlq (new in Feature 8)
     TracingMessageListenerAdvice extracts traceparent from original message headers
     (x-death header added by RabbitMQ wraps the original headers — verify that
     MessageProperties.getHeaders() still returns original headers including traceparent)
```

**Critical verification needed:** When RabbitMQ dead-letters a message, it adds `x-death` array header but preserves all original message headers including `traceparent`. The `TracingMessageListenerAdvice` `MessagePropertiesGetter` reads from `MessageProperties.getHeaders()`, which includes the original headers. This should work without modification, but must be verified with an integration test.

**`TracingMessageListenerAdvice` and multiple queues:** The existing advice is registered on the `SimpleRabbitListenerContainerFactory` via `setAdviceChain`. Since all three consumer listeners (`orders.fulfillment`, `orders.audit`, `orders.dlq`) can be annotated with `@RabbitListener` using the same container factory, the single advice chain applies to all of them. No per-queue advice registration needed.

---

## Build Order (Phase Sequence)

The 8 features form a dependency graph. The recommended phase order:

```
Phase 10: Decompose otel-lgtm (Feature 1)
    │
    ├─► Phase 11: Tail Sampling (Feature 2)  [requires decomposed Collector]
    │       │
    │       └─► Phase 12: Exemplars (Feature 3) [requires Mimir + Grafana datasources]
    │               │
    │               └─► Phase 13: Log-Based Metrics (Feature 4) [requires Loki + Mimir]
    │
    └─► Phase 14: JDBC/JPA Spans (Feature 5) [independent of Collector; builds on Phase 8 pattern]
    │
    └─► Phase 15: Outbound HTTP Spans (Feature 6) [independent; adds to otel-bootstrap]
    │
    └─► Phase 16: Sampling + Baggage (Feature 7) [independent; modifies OtelSdkConfiguration]
    │
    └─► Phase 17: AMQP Fanout/Topic/DLX (Feature 8) [independent of all infra changes]
```

**Rationale for this ordering:**

- Phases 10-13 form the "Operational/Infrastructure arc." Decomposing the Collector first unlocks tail sampling (which requires a standalone Collector), which unlocks exemplar testing (you want sampled traces to exist in Tempo before testing click-through), which unlocks log-based metrics (Loki ruler needs a Mimir target to remote-write to).
- Phases 14-17 are the "SDK/Instrumentation arc." They are independent of each other and independent of Phases 10-13 (they still work against `otel-lgtm` or the decomposed stack). However, they logically build on the v1.0 SDK patterns and are easier to teach AFTER the infra stack is stabilized.
- Phase 16 (Sampling + Baggage) benefits from Feature 3 being in place (so attendees can see sampled vs. dropped traces in Grafana), but is not technically blocked on it.
- Phase 17 (AMQP topologies) is last because it expands the RabbitMQ topology, which requires verifying that the existing trace propagation still works across all exchange types. Placing it last means Phase 17's integration tests build on a fully-instrumented stack.

**Alternative ordering if infra work is too heavy for one phase:**

Phase 10 (Decompose Collector) can be split into 10a (decompose + basic pipeline) and 10b (add scrape targets + provisioned dashboards). Phase 11 (Tail Sampling) depends only on 10a.

---

## Shared vs. Per-Service Component Decisions

| Component | Where | Rationale |
|-----------|-------|-----------|
| `TracingMessagePostProcessor` | `otel-bootstrap` | Symmetric inject/extract pair (v1.0 decision PROP-04) |
| `TracingMessageListenerAdvice` | `otel-bootstrap` | Same |
| `TracingClientHttpRequestInterceptor` (NEW) | `otel-bootstrap` | Same rationale as AMQP pair: exchange-agnostic, reusable by both services |
| `BaggageSpanAttributeProcessor` (NEW) | `otel-bootstrap` | SpanProcessor reusable by both services; registered in `buildTracerProvider()` which is per-service but the processor class is shared |
| `OtelSdkConfiguration` | Per-service (intentional duplicate) | TRACE-01 / DOC-05 constraint — preserved |
| `OrderJpaService`, `OrderJpaRepository`, `Order` entity | `consumer-service` only | JPA instrumentation only exists where the DB surface exists |
| `HttpServerSpanFilter` | `producer-service` only | HTTP server surface only in producer |
| `InstrumentedJedisPool` | `producer-service` only | Valkey only in producer |
| `OrderRepository` (JDBC) | `consumer-service` only | PostgreSQL only in consumer |

**New `otel-bootstrap` package layout after v2.0:**

```
otel-bootstrap/src/main/java/com/example/otel/
  amqp/
    TracingMessagePostProcessor.java    (existing)
    TracingMessageListenerAdvice.java   (existing)
    MessagePropertiesSetter.java        (existing)
    MessagePropertiesGetter.java        (existing)
    BaggageSpanAttributeProcessor.java  (NEW — Feature 7)
  http/
    TracingClientHttpRequestInterceptor.java  (NEW — Feature 6)
  package-info.java                     (existing)
```

---

## System Overview Diagram (v2.0)

```
curl / httpie
POST /orders
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ producer-service (host JVM)                                  │
│                                                              │
│  HttpServerSpanFilter (SERVER span)                          │
│    └─ OrderController.create()                               │
│         └─ OrderService.place()      (INTERNAL span)         │
│              ├─ OrderPublisher        (PRODUCER span)        │
│              │   TracingMessagePostProcessor                  │
│              │   injects traceparent + baggage               │
│              └─ RestClient + TracingClientHttpRequestInterceptor │
│                  (CLIENT span, HTTP outbound — Feature 6)    │
│                                                              │
│  BaggageSpanAttributeProcessor stamps baggage on spans       │
│  ExemplarFilter.traceBased() stamps histogram measurements   │
└──────────────────────────┬──────────────────────────────────┘
                           │  AMQP :5672
                           ▼
                    ┌─────────────────────┐
                    │ RabbitMQ            │
                    │ direct: orders.created   (v1.0)     │
                    │ topic:  orders.topic     (Feature 8) │
                    │ fanout: orders.fan       (Feature 8) │
                    │ DLX:    orders.dlx → orders.dlq      │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │ consumer-service (host JVM)          │
                    │                                      │
                    │ TracingMessageListenerAdvice          │
                    │  extracts traceparent + baggage       │
                    │  (all exchange types, one class)      │
                    │                                       │
                    │  OrderListener (orders.fulfillment)   │
                    │   └─ ProcessingService.process()      │
                    │       ├─ InstrumentedJedisPool.SET    │
                    │       ├─ OrderRepository.insert()     │
                    │       └─ OrderJpaService.save() ── Feature 5 │
                    │                                       │
                    │  AuditListener (orders.audit)  ── Feature 8  │
                    │  DlqListener   (orders.dlq)    ── Feature 8  │
                    └───────────────────────────────────────┘
                               │ OTLP/gRPC :4317
                               ▼
              ┌────────────────────────────────────┐
              │ otel-collector (docker-compose)     │
              │                                    │
              │  Processors (teaching pipeline):    │
              │  memory_limiter                     │
              │  → resource (env attribute upsert)  │
              │  → attributes (header redact)       │
              │  → filter (drop /actuator/health)   │
              │  → tail_sampling (error/slow/20%)   │
              │  → batch                            │
              │                                    │
              │  Pipelines:                         │
              │  traces → Tempo :4418               │
              │  metrics → Mimir :9009/api/v1/push  │
              │  logs → Loki :3100/otlp             │
              └──────────────┬─────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼ :3200              ▼ :9009              ▼ :3100
     Tempo                 Mimir                 Loki
  (trace store)        (metric store)         (log store)
        │            Loki ruler remote-write ──►│
        │                   │                   │
        └───────────────────┼───────────────────┘
                            ▼ :3000
                         Grafana
                  (datasources.yaml: cross-signal
                   datalinks Tempo↔Loki↔Mimir,
                   exemplar click-through via trace_id)
```

---

## Data Flow Changes by Signal

### Traces (v2.0 delta over v1.0)

```
v1.0: producer/consumer → OTLP :4317 → otel-lgtm (embedded Collector) → Tempo

v2.0: producer/consumer → OTLP :4317 → otel-collector → tail_sampling → Tempo
                                                       (only sampled traces forwarded)
```

New spans added by v2.0:
- `CLIENT "POST /notifications"` in producer (Feature 6)
- `CLIENT "SAVE orders"` in consumer for JPA path (Feature 5)
- Multiple CONSUMER spans per publish in fanout scenario (Feature 8)

### Metrics (v2.0 delta)

```
v1.0: producer/consumer → OTLP :4317 → otel-lgtm (embedded Collector → Prometheus) → Mimir

v2.0: producer/consumer → OTLP :4317 → otel-collector → prometheusremotewrite → Mimir
      (exemplars now embedded in remote-write payloads — ExemplarFilter.traceBased())

      Loki ruler → remote_write → Mimir      (new: log-derived metrics, Feature 4)
```

### Logs (v2.0 delta)

```
v1.0: producer/consumer → OTLP :4317 → otel-lgtm (embedded Collector) → Loki

v2.0: producer/consumer → OTLP :4317 → otel-collector → Loki (native OTLP)
      (no behavioral change — structurally identical)
```

---

## Integration Points Summary

### New External Services (docker-compose)

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| `otel-collector` | `otel/opentelemetry-collector-contrib:0.128.0` (pin) | 4317, 4318 (external); 13133 (health) | Replace `lgtm`; contrib image needed for `tail_sampling`, `prometheusremotewrite`, `filter` processors |
| `tempo` | `grafana/tempo:2.10.0` (pin) | 3200 | Local backend; metrics-generator enabled |
| `mimir` | `grafana/mimir:2.16.0` (pin) | 9009 | Single-binary mode (`-target=all`) |
| `loki` | `grafana/loki:3.7.0` (pin) | 3100 | Ruler enabled with remote-write |
| `grafana` | `grafana/grafana:11.6.0` (pin) | 3000 | Datasources provisioned from `grafana/datasources.yaml` |

### Internal Module Boundaries (Maven)

| Module | New Dependencies |
|--------|-----------------|
| `otel-bootstrap` | No new Maven deps (uses only OTel API + Spring AMQP already present) |
| `producer-service` | No new deps (RestClient in `spring-boot-starter-web` already) |
| `consumer-service` | `spring-boot-starter-data-jpa` (Feature 5) |
| `integration-tests` | May need Testcontainers modules for Collector + Loki/Tempo/Mimir to assert tail-sampled trace delivery |

---

## Anti-Patterns to Avoid (v2.0 Specific)

### Anti-Pattern 1: Separate Sampling Collector for Tail Sampling

**What people do:** Add a second `otel-collector` behind a `loadbalancing` exporter to handle tail sampling separately.
**Why wrong:** Over-engineering for a workshop. The `tail_sampling` processor works in a single-Collector pipeline. A second Collector requires sticky routing (the `loadbalancing` exporter) because all spans for a given trace must arrive at the same Collector to make a complete decision. That's a production concern, not a teaching surface.
**Do this instead:** Single Collector, `tail_sampling` processor in the traces pipeline.

### Anti-Pattern 2: SDK-Side Exemplar Filter as alwaysOn()

**What people do:** `SdkMeterProviderBuilder.setExemplarFilter(ExemplarFilter.alwaysOn())`.
**Why wrong:** `alwaysOn()` records an exemplar for every measurement regardless of whether a span is active. For instruments called outside span context (e.g., scheduled metrics), this produces exemplars with empty trace_id that are useless. `traceBased()` only stamps when a span is active — producing meaningful exemplars.
**Do this instead:** `ExemplarFilter.traceBased()` (which is the OTel spec's RECOMMENDED default).

### Anti-Pattern 3: JPA Instrumentation via Spring AOP Proxy Advice

**What people do:** Write a `@Aspect @Around("@within(org.springframework.stereotype.Repository)")` to auto-wrap all repository calls.
**Why wrong:** The workshop's whole point is visible SDK calls. An aspect hides the span lifecycle behind framework magic — exactly what auto-instrumentation does. Attendees can't see which SDK call opens the span.
**Do this instead:** Explicit `tracer.spanBuilder()` wrapping inside the service method (Option C above). The boilerplate IS the lesson.

### Anti-Pattern 4: Using lgtm Container in the Decomposed Stack (Mixing)

**What people do:** Keep `grafana/otel-lgtm` in compose alongside separate Tempo/Mimir/Loki containers.
**Why wrong:** lgtm runs its own embedded Collector on :4317 and its own Prometheus on :9090. Running both creates port conflicts and confusing data duplication.
**Do this instead:** Full replacement. Remove `lgtm:` from docker-compose.yml entirely.

### Anti-Pattern 5: Floating Docker Image Tags in Workshop Repo

**What people do:** `image: grafana/tempo:latest`.
**Why wrong:** Image contents change without notice; workshop screenshots rot; attendees joining 6 months later see different UI.
**Do this instead:** Pin all images at the same version used during workshop development. Use a comment referencing the date and the release URL.

---

## Sources

- [OTel Collector tail_sampling processor README](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md) — policy types, `decision_wait`, `num_traces`, composite vs. sequential policy ordering
- [Context7 /open-telemetry/opentelemetry-collector-contrib tail sampling docs](https://context7.com/open-telemetry/opentelemetry-collector-contrib) — verified `status_code`, `latency`, `probabilistic` policy YAML syntax (HIGH confidence)
- [Context7 /open-telemetry/opentelemetry-java exemplar docs](https://context7.com/open-telemetry/opentelemetry-java) — `ExemplarFilter.traceBased()`, `SdkMeterProviderBuilder.setExemplarFilter()` API (HIGH confidence)
- [Context7 /open-telemetry/opentelemetry-java baggage docs](https://context7.com/open-telemetry/opentelemetry-java) — `Baggage.builder().put()`, `Baggage.current()`, `storeInContext()`, `W3CBaggagePropagator` (HIGH confidence)
- [Grafana docker-otel-lgtm bundled otelcol-config.yaml](https://raw.githubusercontent.com/grafana/docker-otel-lgtm/main/docker/otelcol-config.yaml) — confirmed port 4317/4318 receiver, batch processor, otlphttp exporters to Tempo/Mimir/Loki (HIGH confidence)
- [Grafana docker-otel-lgtm bundled tempo-config.yaml](https://raw.githubusercontent.com/grafana/docker-otel-lgtm/main/docker/tempo-config.yaml) — metrics-generator with exemplars enabled via `send_exemplars: true` on remote_write (HIGH confidence)
- [Grafana Mimir configure OTel Collector](https://grafana.com/docs/mimir/latest/configure/configure-otel-collector/) — `prometheusremotewrite` exporter endpoint format (MEDIUM-HIGH confidence)
- [Grafana Loki recording rules](https://grafana.com/docs/loki/latest/operations/recording-rules/) — ruler config, remote_write Prometheus format, rule YAML structure (MEDIUM confidence — file format confirmed, full ruler config verified via Loki repo)
- [OTel Java baggage API docs](https://opentelemetry.io/docs/languages/java/api/) — `Baggage.current().toBuilder().put()`, `storeInContext()`, `getEntryValue()` (HIGH confidence)
- [Spring AMQP DLX/fanout exchange documentation](https://docs.spring.io/spring-amqp/docs/) — `FanoutExchange`, `TopicExchange`, DLX binding configuration (HIGH confidence)
- AMQP DLX tracing: confirmed original message headers (including `traceparent`) are preserved when RabbitMQ dead-letters a message — the `x-death` array is added but original headers are not removed (MEDIUM confidence — needs integration test to verify)
