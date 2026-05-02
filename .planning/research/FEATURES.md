# Feature Landscape: v2.0 Production Shapes

**Domain:** OpenTelemetry manual-SDK instrumentation workshop — v2.0 extension adding production observability stack, advanced sampling, cross-signal correlation, richer instrumentation, and AMQP topology variants on top of the v1.0 Workshop baseline.
**Researched:** 2026-05-02
**Confidence:** HIGH (OTel specs, Grafana docs, Collector contrib README verified; MEDIUM on Loki recording-rules-to-Mimir wire path — requires runtime validation)

> "Features" here means **workshop steps and code-level demonstrations** that extend v1.0. Yardstick is pedagogical: does this feature close the gap between "workshop toy" and "production shape an engineer would encounter at work"? Anti-features document what v2.0 deliberately does NOT do, so the scope doesn't creep back.

---

## Feature Grouping

v2.0 features naturally split into three teaching categories that should inform phase ordering:

| Category | Features | Teaching Angle |
|----------|----------|----------------|
| **Operational** | Decomposed Collector, Tail Sampling, Exemplars, Log-based Metrics | "What the infra team owns" — Collector-level concerns invisible to app code |
| **Instrumentation** | JDBC/JPA spans, Outbound HTTP spans, Sampling + Baggage | "What the app team writes" — SDK-level code that runs in the JVM |
| **Transport/Pattern** | AMQP Fanout/Topic/DLX | "What changes when the topology gets real" — existing AMQP propagation code adapts to new exchange shapes |

---

## Feature 1: Decomposed OTel Collector Pipeline

**Category:** Operational — Foundational
**Complexity:** Medium — many moving parts, but each piece is configuration not code
**Dependency:** All other v2.0 features depend on this being in place first (tail sampling, exemplars, log-based metrics all require a standalone Collector).

### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| Replace `grafana/otel-lgtm` single container with separate OTel Collector + Tempo + Mimir + Loki + Grafana containers | The entire v2.0 premise is "production-shaped" — single-container is a shortcut that doesn't exist in any real deployment | Five services in `docker-compose.yml`; apps still run on host |
| OTLP receiver on Collector (gRPC `:4317`, HTTP `:4318`) | Applications send to the Collector, not directly to backends — this is the production pattern | Same endpoint address as v1.0's `otel-lgtm`; no app code change |
| `batch` processor in every pipeline | Reduces backend write load; every production Collector has this | Reasonable defaults: `timeout: 5s`, `send_batch_size: 1024` |
| Traces pipeline: `otlp` receiver → `batch` processor → `otlp` exporter → Tempo | Canonical trace-to-Tempo path; Tempo speaks native OTLP since v2.x | Tempo endpoint `http://tempo:4317`; no transform needed |
| Metrics pipeline: `otlp` receiver → `batch` processor → `prometheusremotewrite` exporter → Mimir | Canonical metrics-to-Mimir path; Mimir exposes `/api/v1/push` | `prometheusremotewrite` exporter endpoint `http://mimir:9009/api/v1/push` |
| Logs pipeline: `otlp` receiver → `batch` processor → `otlphttp` exporter → Loki native OTLP endpoint | Loki's native OTLP endpoint (`/otlp`) available since Loki 3.x; avoid the deprecated push API | Loki endpoint `http://loki:3100/otlp`; avoids needing Loki's Promtail pipeline |
| Grafana with Tempo, Mimir (Prometheus-compatible), Loki datasources provisioned via volume-mounted `datasources.yaml` | v1.0 got datasources for free from `otel-lgtm`; decomposed Grafana needs explicit provisioning | Standard Grafana provisioning pattern; `datasources.yaml` mounts to `/etc/grafana/provisioning/datasources/` |
| `memory_limiter` processor before `batch` in all pipelines | Prevents OOM on the Collector; required for any real deployment | 80% heap limit via `limit_percentage: 80` |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| `resource_detection` processor to add host/OS resource attributes | Teaches that Collector can enrich spans without touching app code — a key production pattern | Uses `system` and `env` detectors |
| Separate Collector config file (`otel-collector.yaml`) mounted into container via volume | Pattern attendees will see in every production setup; makes the config editable without rebuilding the image | Use `otel/opentelemetry-collector-contrib` image (not core — tail sampling is contrib-only) |
| `healthcheck` extension on Collector with `/` endpoint | Makes `docker compose` healthchecks reliable; demonstrates Collector's extension system | Port `13133` is the default |
| `servicegraph` connector (Collector contrib) generating service-graph metrics → Mimir | Teaches that Grafana's service map isn't magic — it derives from span `client`/`server` attribute pairs arriving at the Collector | Pairs with Grafana's `Tempo` datasource service-graph view |
| Comments in `otel-collector.yaml` explaining each pipeline stage | Code-as-textbook philosophy from v1.0 applied to Collector config | Workshop attendees read YAML like they read Java |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Grafana Alloy instead of OTel Collector | Alloy is Grafana-specific; OTel Collector is vendor-neutral. Workshop teaches the Collector because that's what attendees will encounter at any vendor. |
| Multiple Collector replicas with load-balancing exporter | Real production multi-collector pattern, but adds compose complexity without adding OTel-SDK lesson. Single Collector is sufficient for laptop demo. |
| Separate pipelines per service (one Collector instance per app) | Not how production works (Collectors are infra). |
| `jaeger` exporter for traces | Deprecated; Tempo speaks OTLP natively. |
| Prometheus exporter on Collector (scrape-pull model) | Remote write push is the modern path to Mimir. Scrape model would require an extra Prometheus instance. |

---

## Feature 2: Tail Sampling at the Collector

**Category:** Operational
**Complexity:** Medium — configuration-heavy; requires decomposed Collector (Feature 1) first
**Dependency:** Requires Feature 1 (standalone Collector with `tailsamplingprocessor` from Collector contrib).

### What Makes Tail Sampling Different From Head Sampling

Head sampling (in-SDK) decides at trace start, before any spans exist. Tail sampling (in Collector) buffers the entire trace, then decides based on what actually happened. Key implication: the Collector must see ALL spans for a trace on one node (single-instance setup satisfies this; multi-collector requires a load-balancing layer first, which is out of scope).

### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| `tailsamplingprocessor` in the traces pipeline (Collector contrib only — not in core) | Teaches the canonical Collector-side sampling pattern | Goes between `otlp` receiver and `batch` processor in traces pipeline |
| `status_code` policy: `ERROR` → always sample | "Keep every broken trace" is the #1 production requirement — errors are rare and all valuable | `status_codes: [ERROR]`; status is set on span (requires v1.0's `span.setStatus(StatusCode.ERROR)` to be present) |
| `latency` policy: sample traces where duration exceeds a threshold (e.g., 500 ms) | "Keep every slow trace" is the #2 production requirement | `threshold_ms: 500` |
| `probabilistic` policy: sample a flat percentage of all remaining traces | Fallback for traces that are neither errors nor slow; keeps a representative sample | `sampling_percentage: 10` |
| `and` policy combining the above three into a composite decision: ERROR OR slow OR (probabilistic fallback) | The real-world pattern: errors at 100%, slow traces at 100%, everything else at N% | Use `composite` policy type with `rate_allocation` to give the error and latency policies priority budget |
| `decision_wait: 30s` (or tuned-down `10s` for workshop demo speed) | Controls how long Collector buffers spans before deciding; shorter = faster feedback in demos | 30s default is production-appropriate; 10s is more demo-friendly at the cost of dropping late-arriving spans |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| Side-by-side visualization: run load generator (`scripts/load.sh`) with tail sampling ON vs. OFF, show trace counts in Grafana dropping | Makes the "90% drop" concrete and visible — converts theory into demo moment | 10% ratio + load.sh burst mode produces obvious visual |
| Comment in Collector YAML explaining `decision_wait` and memory tradeoffs | The most common mistake: setting `decision_wait` too low loses tail of slow traces; too high exhausts memory | Reference Collector contrib tailsamplingprocessor README |
| Explicit "what head sampling can't do" comment | Workshop's pedagogical contrast: SDK sampler drops by ratio before any spans are emitted; Collector sampler sees completed traces | Links to v1.0's `Sampler.parentBased(alwaysOn())` |
| Note that tail sampling breaks exemplar alignment (sampled traces are a subset; exemplar trace IDs may not be in Tempo) | Important production gotcha — exemplars depend on traces being present | When tail sampling is active at >10% drop rate, exemplar click-through may yield "trace not found" |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Multi-collector tail sampling with load-balancing exporter | Requires two Collector tiers; out of scope for single-laptop setup |
| `probabilistic_sampling` processor (the simpler, older processor) | `tailsamplingprocessor` with a `probabilistic` policy supersedes this; teach the current approach |
| Sampling percentage below 5% in the demo | Makes demo data too sparse; hard to demonstrate correlation lessons |

---

## Feature 3: Exemplars Wiring

**Category:** Operational (Collector-side config + SDK-side ExemplarFilter)
**Complexity:** Medium — requires decomposed Collector, requires an existing histogram metric (v1.0's `http.server.request.duration` is the natural source), and requires Grafana datasource configuration
**Dependency:** Requires Feature 1 (decomposed pipeline); `prometheusremotewrite` exporter must forward exemplars; the `http.server.request.duration` histogram from v1.0 Phase-4 is the natural exemplar source.

### What Exemplars Are

An exemplar is a concrete measurement sample attached to an aggregated metric bucket, carrying a `trace_id` label that links the aggregate (e.g., "99th percentile latency was 450 ms") to the specific trace that caused it (e.g., "here's the trace that was 450 ms"). In Grafana panels, exemplars render as small dots/diamonds alongside the metric chart; clicking one navigates directly to the trace in Tempo.

Wire format: when Prometheus scrapes with `Accept: application/openmetrics-text`, the response includes exemplar payloads inline in each histogram bucket line with labels and timestamp.

### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| `SdkMeterProviderBuilder.setExemplarFilter(ExemplarFilter.traceBasedExemplarFilter())` (or leave default, as `with_sampled_trace` is the SDK default) | Tells the SDK to attach the active trace context to metric measurements recorded inside a sampled span | Default is already `traceBasedExemplarFilter`; explicitly setting it is the teaching moment |
| `http.server.request.duration` histogram already records during an active SERVER span (v1.0 TS-13) | The histogram measurement must happen inside a span context for the SDK to attach a trace_id exemplar | v1.0's filter servlet already holds the span open during `histogram.record(duration)` |
| Exemplar forwarding through the OTLP pipeline: SDK → Collector OTLP receiver → `prometheusremotewrite` exporter with `send_exemplars: true` | Exemplars flow through OTLP natively; `prometheusremotewrite` exporter must be told to forward them | Add `send_exemplars: true` under `prometheusremotewrite` exporter config |
| Mimir configured with `--querier.exemplar-storage-enabled=true` (or Mimir's default exemplar retention) | Mimir must store exemplars to serve them back to Grafana | In Grafana Mimir, exemplar storage is enabled by default since Mimir 2.x |
| Grafana Prometheus/Mimir datasource configured with `exemplarTraceIdDestinations: [{name: traceID, datasourceUid: <tempo-uid>}]` | Tells Grafana: "when an exemplar carries a `traceID` label, clicking it should open Tempo with that ID" | Provisioned in `datasources.yaml`; `traceID` must match the label key the SDK emits |
| Demo: click an exemplar dot on the `http.server.request.duration` panel → Tempo trace opens | The "wow moment" — metrics and traces become navigable from each other without any query writing | Requires all of the above to align simultaneously |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| Workshop step that triggers high-latency requests (e.g., `scripts/load.sh --burst` or a deliberate `Thread.sleep` in the handler) to make exemplar dots appear at P99 bucket | Exemplars only appear on bucket boundaries that have active measurements; synthetic load produces visible dots | High-latency bucket exemplars are more dramatic than fast-path ones |
| Comment explaining why exemplars only appear on sampled traces (ExemplarFilter = `traceBasedExemplarFilter`) and what happens when tail sampling drops the trace | Directly addresses the tail-sampling + exemplar conflict from Feature 2 | Common production confusion |
| Screenshot: panel showing histogram with exemplar dot + Tempo trace opened from click | High visual impact for README/docs | Pairs with v1.0's DOC-04 screenshot pattern |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Exemplars on Counter metrics | OTel spec supports exemplars on counters but Grafana's exemplar UI only surfaces them prominently on histograms; confusing for a workshop |
| Custom exemplar labels beyond `trace_id` | The SDK automatically attaches `trace_id` from the active span context; manual exemplar attachment is the agent/auto-instrumentation path, contradicting workshop philosophy |
| Using Prometheus scrape endpoint instead of OTLP for metrics | Forces a Prometheus scrape target on the Spring Boot service; adds a separate metrics path; conflicts with the "one OTLP endpoint" story |

---

## Feature 4: Log-Based Metrics

**Category:** Operational — Collector/Loki-side (no app code changes)
**Complexity:** Medium-Low — pure configuration (LogQL recording rules YAML); no SDK or app code
**Dependency:** Requires Feature 1 (standalone Loki with ruler enabled); requires v1.0 log emission (SdkLoggerProvider + OpenTelemetryAppender) to have `service_name` labels in Loki.

### What Log-Based Metrics Are

Log-based metrics derive numeric time series from log data — without instrumenting the app code. Loki's ruler evaluates LogQL metric queries on a schedule and writes the results as Prometheus-compatible time series, either into Loki itself (for alerting) or into a remote-write target like Mimir. This fills the gap between "I should have added a counter" (app code change required) and "I can compute an error rate from existing error logs right now" (no app change).

This is explicitly different from emitting metrics from app code: log-based metrics are derived, potentially lossy (depend on log sampling), and have higher cardinality limits than SDK counters.

### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| Loki ruler enabled in the decomposed Loki container | Recording rules require Loki's ruler component, not just the query layer | Enable via `--ruler.enable-api=true` and ruler storage config in Loki's `loki.yaml` |
| At least one recording rule computing an error rate per service: `sum(rate({service_name="orders-producer"} \|= "ERROR" [5m])) by (service_name)` | Most fundamental log-derived metric; demonstrates the LogQL-as-metric-query pattern | Record as `log_errors_total:rate5m`; must match Loki's label names which use `service_name` (OTLP→Loki mapping) |
| Recording rules written to Mimir via ruler remote-write: `ruler.remote_write.enabled: true`, `url: http://mimir:9009/api/v1/push` | Makes log-derived metrics available in Grafana Mimir dashboards alongside SDK-emitted metrics | Mimir then serves them via its Prometheus-compatible query API |
| Grafana dashboard panel showing the log-derived error-rate metric alongside the SDK-emitted `orders.created` counter | Side-by-side comparison teaches "two sources of truth for the same fact" — SDK metric is reliable; log-derived metric is a backstop | Visual contrast is the lesson |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| Second recording rule computing request rate from access logs (if the HTTP filter logs each request) | Demonstrates that latency-from-logs is also possible, even without a histogram | `sum(rate({service_name="orders-producer"} \| logfmt \| duration > 500ms [5m]))` |
| Explicit workshop explanation: "log-based metrics vs. SDK metrics — when to use each" | This is the most common question; the answer is "SDK metrics are more accurate, log-based metrics are emergency backstops and migration tools" | README section or in-YAML comment |
| Alert rule alongside recording rule | Loki ruler supports both; showing them together teaches the ruler's dual role | Alert fires if error rate > 5% |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Replacing SDK metrics with log-based metrics | Log-based metrics are complement, not replacement; higher latency, dependent on log volume, affected by sampling |
| Loki rule files managed by Grafana UI instead of version-controlled YAML | Workshop must be reproducible from `git clone`; UI-created rules vanish on restart |
| Using Loki's own metric storage instead of remote-writing to Mimir | Keeps metrics siloed in Loki; the workshop's point is that Mimir is the unified metrics store |

---

## Feature 5: JDBC/JPA Database Spans

**Category:** Instrumentation — SDK Teaching Surface
**Complexity:** Medium — extends v1.0 Phase-8's `SPAN_KIND_CLIENT` pattern; adds transaction span wrapping, Spring Data JPA repository method spans, and richer semconv attributes
**Dependency:** Extends v1.0 Phase-8 (`step-08-db-cache`); requires existing `processed_orders` Postgres table + HikariCP pool. NOT a fresh start — attendees already saw basic `SPAN_KIND_CLIENT` with `INSERT processed_orders`. v2.0 goes deeper.

### Current Baseline (v1.0 Phase-8)

v1.0 Phase-8 shipped: manual `SPAN_KIND_CLIENT` span wrapping a `JdbcTemplate.update("INSERT processed_orders ...")` call with `db.system = "postgresql"` and `db.operation.name = "INSERT"`. That's the minimum. v2.0 extends to:

1. Transaction-level span as parent of N statement spans
2. Spring Data JPA repository method spans
3. Full `db.*` semconv attribute set
4. Connection pool `db.client.connection.*` metrics

### Stable db.* Semconv Attribute Set (OTel semconv ≥ 1.40.0)

Note: as of semconv 1.40.0, the attribute name is `db.system.name` (not `db.system`), though `db.system` still appears in older docs. Use the semconv 1.40.0 Java constants.

| Attribute | Requirement Level | Example |
|-----------|-------------------|---------|
| `db.system.name` | Required | `"postgresql"` |
| `db.namespace` | Conditionally required (if available) | `"orders"` (database name) |
| `db.operation.name` | Conditionally required (if single operation) | `"INSERT"`, `"SELECT"` |
| `db.collection.name` | Conditionally required (if single collection/table) | `"processed_orders"` |
| `db.query.text` | Recommended | `"INSERT INTO processed_orders (?) VALUES (?)"` (sanitized — replace literals with `?`) |
| `db.query.summary` | Recommended | `"INSERT processed_orders"` (low-cardinality, max 255 chars) |
| `server.address` | Recommended | `"localhost"` |
| `server.port` | Conditionally required (if non-default) | `5432` |

**Span name format:** `{db.query.summary}` preferred → `{db.operation.name} {db.collection.name}` → `{db.system.name}`

**Span kind:** `CLIENT` for all database calls.

### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| Full `db.*` attribute set on all database spans (table above) | v1.0 Phase-8 had minimal attributes; v2.0 demonstrates the full contract | Teaches the attribute hierarchy: required → conditionally required → recommended |
| Transaction-level parent span wrapping a logical unit of work (e.g., `orders.process-transaction`) | Teaches that transactions are instrumentation boundaries — the span tree shows a transaction as parent and each statement as a child | Use `Tracer.spanBuilder("orders.process-transaction").setSpanKind(SpanKind.INTERNAL)...` as parent; each `JdbcTemplate` or `EntityManager` operation is a child `CLIENT` span |
| Spring Data JPA repository method spans | v1.0 used `JdbcTemplate` directly; v2.0 shows that `@Repository` interface methods also need manual wrapping | `tracer.spanBuilder("OrderRepository.save").setSpanKind(SpanKind.CLIENT)...` — because Spring Data JPA auto-generated queries don't have accessible SQL text at the repo layer |
| `db.query.text` sanitized (replace literal values with `?`) | Teaches the data-safety requirement for query text in telemetry | "Never log PII in span attributes" — same principle as log redaction |
| `db.client.connection.count` ObservableGauge from HikariCP (already in v1.0 Phase-8) | Connection pool observability is a production concern; extends the existing gauge with additional pool attributes per new semconv | Use `db.client.connection.pool.name` attribute (new in semconv 1.40.0) |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| N+1 query detection demonstration: show that a loop calling `repository.findById()` produces N child spans inside one parent | Teaches that spans reveal hidden inefficiency invisible in logs or counters | Creates a memorable teaching moment: the trace waterfall looks "bad" in Tempo before optimization |
| `error.type` attribute on failed query spans | Teaches that DB errors need to be surfaced on the span, not just logged | `span.setAttribute(ErrorAttributes.ERROR_TYPE, e.getClass().getName())` + `span.recordException(e)` |
| Side-by-side comparison: v1.0 Phase-8 minimal span vs. v2.0 full semconv span in Tempo | Visual diff motivates why the attribute set matters | Use two workshop checkpoint tags |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| AOP/interceptor-based automatic JDBC span injection | Crosses into auto-instrumentation territory — defeats the workshop's manual-SDK premise |
| `db.query.parameter.<key>` attributes (opt-in, development status) | Development-status attributes change; workshop should stay on stable/conditionally-required set |
| Hibernate SQL interception via `StatementInspector` to capture all queries | Complex Spring/Hibernate internals; distracts from OTel lesson |
| Replacing the workshop's `JdbcTemplate`/`EntityManager` calls with a tracing proxy | Proxy-based approaches are the auto-instrumentation agent's job; manual = explicit wrapper calls |

---

## Feature 6: Outbound HTTP-Client Spans

**Category:** Instrumentation — SDK Teaching Surface
**Complexity:** Low-Medium — one `CLIENT`-kind span wrapper around a `RestClient` or `WebClient` call; straightforward semconv
**Dependency:** Requires v1.0 SDK bootstrap and an outbound HTTP call target. The demo can introduce a lightweight external call (e.g., calling a mock `/inventory` endpoint or a well-known public API via `RestClient`) to provide the instrumentation surface.

### What This Teaches

The v1.0 demo only had inbound HTTP (the `POST /orders` SERVER span). v2.0 adds an outbound call — when the producer or consumer calls another service. This is the other half of HTTP instrumentation that engineers need in production. Key differences from SERVER spans:

- Span kind is `CLIENT`, not `SERVER`
- Span name is `{method} {url.template}` (low-cardinality), not the full URL
- Context propagation direction is reversed: we inject into outgoing headers (same as AMQP PRODUCER, not extract)
- `service.peer.name` (stable semconv replacement for deprecated `peer.service`) labels the destination service for service maps

### Stable HTTP Client Span Attribute Set (OTel HTTP semconv, stable since 2023)

| Attribute | Requirement Level | Example |
|-----------|-------------------|---------|
| `http.request.method` | Required | `"GET"`, `"POST"` |
| `server.address` | Required | `"inventory-service"` |
| `server.port` | Required | `8080` |
| `url.full` | Required | `"http://inventory-service:8080/api/v1/stock?sku=ABC"` |
| `http.response.status_code` | Conditionally required (if response received) | `200`, `404`, `503` |
| `network.protocol.version` | Recommended | `"1.1"` |
| `error.type` | Required when request errors (no response or 5xx) | `"java.net.SocketTimeoutException"`, `"5xx"` |
| `service.peer.name` | Opt-in (use for service maps) | `"inventory-service"` |

**Span kind:** `CLIENT`
**Span name:** `{http.request.method} {url.template}` e.g., `"GET /api/v1/stock/{sku}"` — never include dynamic path segments in the name (cardinality explosion)

**Context propagation:** inject `traceparent` into outgoing request headers using `W3CTraceContextPropagator` — same pattern as v1.0 AMQP PRODUCER inject, just `HttpHeaders` as the setter target.

### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| `CLIENT`-kind span around an outbound `RestClient` call with the full stable attribute set above | Shows the outbound HTTP instrumentation pattern; completes the HTTP lesson started with the SERVER span in v1.0 | Spring Boot 3.2+ `RestClient` is the current idiomatic synchronous client |
| `traceparent` header injected into outgoing request via `TextMapPropagator.inject(...)` | Demonstrates propagation for outbound HTTP — the same mechanic as AMQP inject but over HTTP headers | Spring `RestClient`'s `.header()` or `ClientHttpRequestInterceptor` is the setter |
| `service.peer.name` attribute set to the logical name of the target service | Enables Grafana service map to show directed edge from `orders-producer` → `inventory-service` (or whatever mock target is used) | Use `PeerAttributes.SERVICE_PEER_NAME` constant from semconv 1.40.0; note `peer.service` is deprecated |
| Error status set on span when HTTP 5xx or connection failure occurs | Teaches the client-span error-handling rule: 4xx = error on CLIENT span (unlike SERVER spans); 5xx = always error | Contrast with SERVER span error semantics explicitly in README |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| Explicit contrast in README: "SERVER span extracts context from incoming headers; CLIENT span injects context into outgoing headers" | The symmetry of extract/inject is the most frequently confused OTel concept | Mirror the v1.0 AMQP broken-then-fixed pedagogy pattern |
| Show trace view in Tempo with HTTP CLIENT span as child of the SERVER span, connecting to a downstream "inventory-service" child trace | Full distributed trace spanning two HTTP services — the "production picture" attendees haven't seen until now | Inventory service can be a simple Spring Boot stub or WireMock |
| `url.template` as span name vs `url.full` — demo why using full URL as span name creates cardinality explosion in Mimir | This is one of the most common production mistakes; seeing the metric cardinality explode in Mimir is memorable | One bad example tag; then the correct template-based name |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| `WebClient` (reactive) instead of `RestClient` | `WebClient` propagation requires reactive context propagation (`Context.current()` in reactive Mono/Flux chains) — a separate complex lesson; `RestClient` propagation is identical to the AMQP PRODUCER pattern already taught |
| `RestTemplate` (deprecated Spring 5 API) | Do not introduce deprecated APIs in a new-feature workshop |
| Auto-wired HTTP client tracing via an `ExchangeFilterFunction` interceptor that's pre-built | Crosses into auto-instrumentation territory; the manual wrapper is the lesson |

---

## Feature 7: Sampling + Baggage

**Category:** Instrumentation — SDK Teaching Surface
**Complexity:** Low (individual concepts are small) — combined as one feature because head sampling and baggage both live in `OtelSdkConfiguration` and both are "first request decisions"
**Dependency:** No hard dependency on new features; extends v1.0 SDK bootstrap. Naturally pairs with Feature 2 (tail sampling) as the contrast between SDK-side and Collector-side decisions.

### 7a — Head Sampling

This extends the v1.0 comment hint (`Sampler.parentBased(alwaysOn())`) into an actual demonstration.

#### Three Sampler Shapes

| Sampler | When | Behavior |
|---------|------|----------|
| `Sampler.alwaysOn()` | Development, full-fidelity testing | Every trace exported |
| `Sampler.traceIdRatioBased(0.1)` | When used as root in ParentBased | 10% of new root traces sampled; deterministic by trace ID |
| `Sampler.parentBased(traceIdRatioBased(0.1))` | Production default | If parent is sampled → sample; if parent is not sampled → drop; if no parent → apply ratio. Guarantees consistent sampling decision across all services for one trace. |

**Key teaching point:** `parentBased` is almost always what you want in distributed systems. `traceIdRatioBased` alone applied at every service produces inconsistent decisions across service hops.

**Env-var driven:** `OTEL_TRACES_SAMPLER=parentbased_traceidratio`, `OTEL_TRACES_SAMPLER_ARG=0.1` — autoconfigure reads these; no SDK code change needed when using `AutoConfiguredOpenTelemetrySdk`. Workshop should demonstrate both the programmatic and env-var paths.

#### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| Workshop checkpoint showing 10% head sampling active with `scripts/load.sh` running | Visible demo: metrics still fully populate (counters are not sampled); trace count drops 90% in Tempo | Metric completeness vs trace sampling is the key contrast |
| Explicit code comment explaining `parentBased` correctness: "without ParentBased, service B might drop a span whose parent in service A was sampled" | The most important nuance; frequently misconfigured in production | Pairs with a one-sentence README callout |
| Contrast: "SDK sampler drops spans before they're emitted; Collector tail sampler drops after seeing the full trace" | The head-vs-tail teaching point; both features together give attendees the full picture | README comparison table |

### 7b — Baggage

Baggage is W3C-standard key-value metadata that propagates alongside trace context in the `baggage` header. Unlike span attributes (visible in Tempo), baggage is a runtime propagation mechanism — consumed by downstream services, not stored in the trace backend.

#### Table Stakes

| Requirement | Why | Notes |
|-------------|-----|-------|
| `W3CBaggagePropagator` added to the propagator composition: `TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())` | Baggage requires its own propagator; trace context propagator does not carry baggage | Both propagators needed; v1.0 only registered W3CTraceContextPropagator |
| Producer sets a baggage entry: `Baggage.current().toBuilder().put("tenant.id", tenantId).build().makeCurrent()` | Concrete use case: multi-tenant routing context; attendees recognize this immediately | Use `try (Scope s = baggage.makeCurrent()) {}` |
| Consumer reads baggage from extracted context: `Baggage.fromContext(extractedContext).getEntryValue("tenant.id")` | Shows the consumer side; completes the propagation loop | The extracted context already carries the baggage because it was injected by the AMQP producer (v1.0 `TracingMessagePostProcessor`) |
| One span attribute explicitly set FROM baggage: `span.setAttribute("tenant.id", baggageValue)` | Teaches the critical distinction: baggage does NOT automatically appear as span attributes; you must explicitly copy it | Most common misconception about baggage |

#### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| README section: "baggage is NOT traces — it doesn't appear in Tempo; it's a runtime channel" | Clears up the most frequent confusion | Side-by-side: span attributes visible in Tempo vs. baggage only visible after explicit `span.setAttribute()` |
| Demonstrate baggage surviving multiple hops: producer → consumer → outbound HTTP call | Shows W3C baggage propagating through the `baggage` HTTP header automatically | Pairs with Feature 6 (outbound HTTP) |
| Show `baggage` HTTP header value in browser/curl output: `baggage: tenant.id=acme` | Makes the wire format visible without a packet sniffer | `curl -v` output in README |

#### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Using baggage to pass PII (user names, emails) | Baggage propagates to ALL downstream services and is visible in HTTP headers; it is security-insensitive metadata only | Address explicitly in workshop security note |
| OpenTelemetry-specific baggage implementations (non-W3C) | W3C standard is vendor-neutral; custom implementations break interoperability |

---

## Feature 8: AMQP Fanout / Topic / DLX Variants

**Category:** Transport/Pattern — extends v1.0 AMQP lesson
**Complexity:** Medium — the `TracingMessagePostProcessor`/`TracingMessageListenerAdvice` scaffolding from v1.0 transfers directly; new complexity is span relationship semantics (links vs parent) and exchange-type routing
**Dependency:** Requires v1.0's AMQP propagation pair (`otel-bootstrap` module with inject/extract methods). NOT a rewrite — an extension.

### v1.0 Baseline Recap

v1.0: one direct exchange, one queue, one consumer. PRODUCER span → CONSUMER span via `traceparent` header injection. Single parent-child relationship. Simple.

### What v2.0 Adds

Three AMQP topology variants that each expose a different span relationship or routing concern:

#### 8a — Fanout Exchange (One Producer → N Consumers)

**The problem:** With a fanout exchange, one publish produces N messages in N queues consumed by N consumers. Each consumer starts a new CONSUMER span. These N spans cannot all have the same parent (a span has exactly one parent). The OTel messaging semconv answer is **span links**, not parent-child.

**Span relationship rule (from OTel messaging semconv):**
- PRODUCER span: `SpanKind.PRODUCER`, set `messagingOperation = "publish"`
- Each CONSUMER span: `SpanKind.CONSUMER`, set `messagingOperation = "process"`, **add a link to the PRODUCER span's context** (`span.addLink(producerContext.span().getSpanContext())`) instead of using the PRODUCER span as parent
- The CONSUMER spans are in separate traces (no shared `trace_id`) but are linked by span links visible in Tempo

**Teaching value:** Span links are the OTel feature most attendees have never used; fanout is the canonical reason to use them.

| Table-Stake | Why | Notes |
|-------------|-----|-------|
| Fanout exchange declared in Spring AMQP `Declarables` bean with 2 consumer queues | Demonstrates exchange type differences at the broker level | `FanoutExchange`, `Queue × 2`, `Binding × 2` |
| PRODUCER span on publish with `messaging.destination.name = fanout-exchange-name` | Same producer-side shape as v1.0 direct exchange | `messaging.operation.name = "publish"` |
| Each consumer extracts context and creates its own CONSUMER span **linked to** (not child of) the producer span | Teaches `Span.addLink(SpanContext)` — the only correct model for fan-out | `tracer.spanBuilder("process fanout.queue.*").addLink(producerContext.span().getSpanContext()).startSpan()` |
| Tempo visualization: two separate traces, each with a link back to the producer span | Visual proof that span links appear in Tempo as navigable connections | Screenshot candidate |

#### 8b — Topic Exchange (Routing Key as Attribute)

**The problem:** Topic exchanges route by pattern-matching routing keys (`orders.*.created`). The routing key is semantically meaningful — it should appear as a span attribute.

| Table-Stake | Why | Notes |
|-------------|-----|-------|
| `messaging.rabbitmq.destination.routing_key` attribute on the PRODUCER span | Routing key is a first-class concept in the messaging semconv | `MessagingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY` constant in semconv 1.40.0 |
| `messaging.destination.name` = `"{exchange}:{routing_key}"` on producer span (per semconv guidance when both are present) | Full semconv-specified span name shape for RabbitMQ | e.g., `"orders.topic:orders.us.created"` |
| Consumer spans carry the routing key they actually received (may differ from publish pattern) | Teaches that consumer and producer routing keys may differ (wildcard matching) | Read from message properties in the `TracingMessageListenerAdvice` pattern |

#### 8c — Dead Letter Exchange (DLX) / Retry Loop

**The problem:** When a message fails processing and is NACK'd, RabbitMQ routes it to a DLX. The message may be retried, possibly multiple times. The question: does a retry span parent to the original consumer span, start a completely new trace, or use a link?

**The OTel answer:** A retry is a new processing attempt. The re-published message carries the original `traceparent` header — the retry CONSUMER span becomes a sibling (parent = same original PRODUCER span), not a new root trace. If the retry architecture re-publishes as a new message without the original headers, the retry starts a new trace; a span link to the original trace captures the causal relationship.

| Table-Stake | Why | Notes |
|-------------|-----|-------|
| DLX-routed queue declared in Spring AMQP with `x-dead-letter-exchange` argument | Shows DLX configuration; provides the instrumentation surface | `new Queue("orders.dlx.queue", true, false, false, Map.of("x-dead-letter-exchange", "orders.dlx"))` |
| Consumer NACK handler that causes messages to route to DLX | Simulates the failure path that triggers DLX routing | Deterministic 10% NACK on `orderId % 10 == 0` (mirrors v1.0 error pattern) |
| DLX consumer span: extract `traceparent` from re-queued message headers; if headers present, continue same trace; if absent, start new trace + add link to original | Teaches the "was the original context preserved?" decision tree | Original `traceparent` IS preserved by RabbitMQ when routing to DLX via NACK; would be absent if a new message is published |
| `messaging.rabbitmq.message.delivery_tag` on CONSUMER spans | Delivery tag is the per-attempt unique ID; useful for correlating retries to specific broker deliveries | `MessagingAttributes.MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG` constant |

### Anti-Features (All AMQP Variants)

| Anti-Feature | Why Avoid |
|--------------|-----------|
| RPC over AMQP (request-reply pattern) | Bidirectional context propagation across AMQP reply queues is a distinct and complex lesson; out of scope per v2.0 |
| Work queues with competing consumers across multiple JVM processes | Multi-JVM setup exceeds laptop constraints |
| Custom message serialization / complex payloads | Orthogonal to the propagation lesson; use the same `OrderCreated` JSON from v1.0 |
| Automatic span injection via Spring AMQP's `ObservationRegistry` integration | Uses Micrometer Observation — directly conflicts with the workshop's anti-Micrometer stance |

---

## Feature Cross-Dependencies (v2.0 Explicit Graph)

```
Feature 1: Decomposed Collector
 ├──required-by──> Feature 2 (Tail Sampling — tailsamplingprocessor is Collector-only)
 ├──required-by──> Feature 3 (Exemplars — prometheusremotewrite exemplar forwarding)
 └──required-by──> Feature 4 (Log-based metrics — Loki ruler + remote-write to Mimir)

Feature 2: Tail Sampling
 └──conflicts-with──> Feature 3 (Exemplars) at high drop rates
    (tail sampling may drop traces whose trace_id is in an exemplar)
    Mitigation: use low drop rate (10%) in demo or note the conflict in README

Feature 3: Exemplars
 └──requires-precondition──> v1.0 Phase-4 `http.server.request.duration` histogram
    (histogram must record inside active span for exemplar SDK to attach trace_id)

Feature 5: JDBC/JPA Spans
 └──extends──> v1.0 Phase-8 db-cache (Postgres + HikariCP already in docker-compose; JdbcTemplate usage already shown)

Feature 6: Outbound HTTP Spans
 └──extends──> v1.0 Phase-2 HTTP SERVER span (completes the HTTP story)
 └──pairs-with──> Feature 7b Baggage (baggage survives to outbound HTTP hop)

Feature 7a: Head Sampling
 └──contrasts-with──> Feature 2 Tail Sampling (SDK-side vs. Collector-side decision)

Feature 7b: Baggage
 └──requires──> v1.0 AMQP propagation (baggage must flow through the same AMQP inject/extract path)
 └──pairs-with──> Feature 6 (baggage visible in outbound HTTP `baggage:` header)

Feature 8a: Fanout
 └──requires──> v1.0 otel-bootstrap TracingMessageListenerAdvice (extend, not replace)

Feature 8b: Topic
 └──requires──> v1.0 otel-bootstrap inject/extract (extend with routing key attribute)

Feature 8c: DLX
 └──requires──> Feature 8a or 8b (needs a multi-queue topology to demonstrate DLX routing)
 └──demonstrates──> Feature 2 (tail sampling with retry loops: multiple consumer spans for one producer)
```

### Mandatory Phase Ordering

Based on dependencies:

1. Feature 1 (Decomposed Collector) must be Phase 10 — everything else assumes a standalone Collector
2. Features 2, 3, 4 (Tail Sampling, Exemplars, Log-based Metrics) can follow in any order after Phase 10, but 2 before 3 is pedagogically cleaner (explain sampling first, then show the exemplar/sampling conflict)
3. Features 5, 6, 7 (JDBC, HTTP Client, Sampling+Baggage) are SDK-level; no hard ordering dependency between them; can interleave with operational features
4. Feature 8 (AMQP variants) logically follows after attendees understand span links (introduced in Feature 8a), which means placing it after at least Feature 2 so the concept of "trace relationships" has been expanded

---

## Table Stakes vs. Differentiators vs. Anti-Features Summary

### Table Stakes (Minimum for Each Feature to Be "Working")

| ID | Feature Area | Table-Stake Core |
|----|--------------|------------------|
| V2-TS-1 | Decomposed Collector | OTLP receiver → batch → traces:Tempo, metrics:Mimir, logs:Loki; all three pipelines operational |
| V2-TS-2 | Tail Sampling | status_code ERROR policy + latency policy + probabilistic fallback in composite; decision_wait configured |
| V2-TS-3 | Exemplars | `traceBasedExemplarFilter` on SDK; `send_exemplars: true` on prometheusremotewrite; Grafana datasource exemplarTraceIdDestinations; one click-through demo |
| V2-TS-4 | Log-based Metrics | Loki ruler enabled; one recording rule (error rate); remote-write to Mimir; Grafana panel |
| V2-TS-5 | JDBC/JPA Spans | Full `db.*` semconv attribute set; transaction parent span; sanitized `db.query.text` |
| V2-TS-6 | Outbound HTTP Spans | CLIENT span with full HTTP semconv; `traceparent` injected into outgoing headers; `service.peer.name` set |
| V2-TS-7a | Head Sampling | `parentBased(traceIdRatioBased(0.1))` demo with visible metric vs trace divergence |
| V2-TS-7b | Baggage | `W3CBaggagePropagator` added; producer sets; consumer reads; explicit copy to span attribute |
| V2-TS-8a | AMQP Fanout | Fanout exchange + 2 consumers + span links (not parent-child) on consumer spans |
| V2-TS-8b | AMQP Topic | Topic exchange + routing key attribute on producer span |
| V2-TS-8c | AMQP DLX | DLX routing + retry consumer span continuing same trace (if headers preserved) |

### Differentiators (Polish / Teaching Quality Uplift)

| ID | Feature Area | Differentiator |
|----|--------------|----------------|
| V2-DF-1 | Decomposed Collector | `resource_detection` processor; annotated `otel-collector.yaml` |
| V2-DF-2 | Tail Sampling | Side-by-side trace count visualization ON/OFF; tail-vs-head contrast in README |
| V2-DF-3 | Exemplars | High-latency scenario to produce P99 exemplar dots; screenshot |
| V2-DF-4 | Log-based Metrics | Second recording rule (request rate); alert rule alongside recording rule |
| V2-DF-5 | JDBC/JPA | N+1 query demonstration via trace waterfall |
| V2-DF-6 | Outbound HTTP | `url.template` vs `url.full` cardinality explosion demo |
| V2-DF-7 | Sampling+Baggage | Baggage surviving multi-hop (HTTP header visible in curl -v output) |
| V2-DF-8 | AMQP DLX | Tempo visualization showing retry span links |

### Anti-Features (Explicitly Out of Scope for v2.0)

| Anti-Feature | Category | Reason |
|--------------|----------|--------|
| Grafana Alloy instead of OTel Collector | Operational | Vendor-specific |
| Multi-collector tail sampling with load balancer | Operational | Exceeds laptop constraints |
| Prometheus scrape pull model for metrics | Operational | Contradicts OTLP-first story |
| Replacing SDK metrics with log-based metrics | Operational | Log metrics are complement only |
| AOP/interceptor automatic DB span injection | Instrumentation | Auto-instrumentation territory |
| `WebClient` reactive propagation | Instrumentation | Separate complex lesson |
| Baggage carrying PII | Instrumentation | Security anti-pattern; address explicitly |
| `@WithSpan` annotation for JDBC spans | Instrumentation | Still requires agent/AOP |
| Spring AMQP `ObservationRegistry` for AMQP spans | Transport | Uses Micrometer; contradicts anti-Micrometer stance |
| AMQP RPC pattern | Transport | Out of scope per PROJECT.md deferred list |
| gRPC service-to-service | Transport | Explicitly deferred to v2.x backlog per PROJECT.md |

---

## Comparable v2.0 Reference Implementations

| Reference | What It Demonstrates | Gap vs. This Workshop |
|-----------|---------------------|----------------------|
| [Grafana intro-to-mltp](https://github.com/grafana/intro-to-mltp) | Decomposed LGTM stack with separate Mimir/Loki/Tempo/Grafana containers + OTel Collector | Polyglot demo; no Spring Boot; no AMQP; no manual SDK teaching |
| [blueswen/spring-boot-observability](https://github.com/blueswen/spring-boot-observability) | Spring Boot + Tempo/Prometheus/Loki + exemplars | Uses auto-instrumentation (Java agent); no AMQP; no tail sampling |
| [open-telemetry/opentelemetry-java-examples](https://github.com/open-telemetry/opentelemetry-java-examples) | Manual SDK reference implementations | No Collector pipeline; no AMQP; no teaching narrative |
| [honeycombio/opentelemetry-collector-workshop](https://github.com/honeycombio/opentelemetry-collector-workshop) | Tail sampling patterns in the Collector | Non-Spring; targets Honeycomb backend; no exemplars/log-metrics |

**Differentiation thesis remains:** no existing reference combines (manual SDK + Spring Boot + AMQP span links + Collector tail sampling + exemplars + log-based metrics + baggage + decomposed LGTM) in a single workshop.

---

## Sources

### Authoritative (HIGH confidence)

- [OTel Collector tailsamplingprocessor README](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md) — policy types, decision_wait, composite policy, rate_allocation
- [OTel Sampling concepts](https://opentelemetry.io/docs/concepts/sampling/) — head vs tail, ParentBased limitations
- [OTel Baggage concepts](https://opentelemetry.io/docs/concepts/signals/baggage/) — use cases, explicit copy requirement
- [OTel messaging spans semconv](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/) — PRODUCER/CONSUMER span kinds, span links for fanout, "process" vs "receive" operation names
- [OTel RabbitMQ semconv](https://opentelemetry.io/docs/specs/semconv/messaging/rabbitmq/) — `messaging.rabbitmq.destination.routing_key`, `messaging.rabbitmq.message.delivery_tag`
- [OTel database spans semconv](https://opentelemetry.io/docs/specs/semconv/db/database-spans/) — `db.system.name`, `db.namespace`, `db.operation.name`, `db.collection.name`, `db.query.text`, connection pool attributes
- [OTel HTTP spans semconv](https://opentelemetry.io/docs/specs/semconv/http/http-spans/) — HTTP CLIENT required attributes, span name format, error handling
- [OTel peer attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/peer/) — `peer.service` deprecated, replaced by `service.peer.name`
- [Grafana Loki alerting/recording rules](https://grafana.com/docs/loki/latest/alert/) — ruler config, recording rule YAML, remote-write to Mimir
- [Grafana exemplars introduction](https://grafana.com/docs/grafana/latest/fundamentals/exemplars/) — exemplar concept, Tempo click-through
- [Grafana Mimir OTel Collector configuration](https://grafana.com/docs/mimir/latest/configure/configure-otel-collector/) — prometheusremotewrite exporter endpoint
- [OTel Collector Grafana setup](https://grafana.com/docs/opentelemetry/collector/opentelemetry-collector/) — canonical pipeline configuration

### Community (MEDIUM confidence — verified against specs)

- [Using Prometheus Exemplars to jump from metrics to traces in Grafana](https://vbehar.medium.com/using-prometheus-exemplars-to-jump-from-metrics-to-traces-in-grafana-249e721d4192) — exemplarTraceIdDestinations Grafana config, OpenMetrics wire format
- [Uptrace — OpenTelemetry Sampling Java](https://uptrace.dev/get/opentelemetry-java/sampling) — ParentBased + TraceIdRatioBased Java examples
- [Distributed Tracing for RabbitMQ with OpenTelemetry](https://medium.com/@team_Aspecto/distributed-tracing-for-rabbitmq-with-opentelemetry-1ebe7457b4c1) — AMQP context propagation, fanout span links
- [Grafana intro-to-mltp](https://github.com/grafana/intro-to-mltp) — decomposed LGTM stack reference
- [Grafana LGTM self-hosted deep-dive](https://blog.tarazevits.io/a-deep-dive-into-my-self-hosted-grafana-lgtm-stack/) — separate container compose patterns
- [blueswen/spring-boot-observability](https://github.com/blueswen/spring-boot-observability) — Spring Boot + exemplars reference (agent-based, but useful for exemplar wiring patterns)

---
*Feature research for: v2.0 Production Shapes milestone (extends v1.0 Workshop)*
*Researched: 2026-05-02*
