# Requirements: OSE OTel Demo — v2.0 Production Shapes

**Defined:** 2026-05-02
**Milestone:** v2.0 Production Shapes
**Continues from:** v1.0 Workshop (shipped 2026-05-02; archived to `milestones/v1.0-REQUIREMENTS.md`)
**Core Value (extended for v2.0):** A workshop attendee who already shipped v1.0's manual-SDK demo can run `docker compose up` against a decomposed Tempo/Mimir/Loki/Grafana stack, see Collector-side tail sampling shape what reaches Tempo, click a histogram exemplar to land on the originating trace, watch a JDBC/JPA span tree under a CONSUMER span, follow baggage from an HTTP header through AMQP into a consumer log, and understand exactly which lines of SDK and Collector config made each piece work.

---

## v2.0 Requirements

Each requirement is user-centric (workshop attendee can observe / verify it), atomic (one capability), and testable. REQ-IDs continue v1.0's `[CATEGORY]-[NUMBER]` scheme; new categories start at `-01`.

### Prerequisites carried from v1.0 (PREREQ)

> Small carryover items that must clear before v2.0 phases that touch the affected files. Sourced from STATE.md "Blockers/Concerns" + "Deferred Items" at v1.0 close.

- [x] **PREREQ-01**: Workshop attendee can start both `producer-service` and `consumer-service` without `BeanCurrentlyInCreationException` on the `otelSdkConfiguration` bean — the `@Autowired OpenTelemetry openTelemetry` field on the `@Configuration` class that produces the bean is removed; the assignment `this.openTelemetry = sdk` happens inside the `@Bean openTelemetry()` factory body just before `return sdk` (same shape v1.0's LOG-03 already established for the Logback appender install)
- [x] **PREREQ-02**: Workshop attendee can view `docs/screenshots/step-04-metrics.png` paired with Step 4 of the README (the deferred PNG from v1.0 Phase 7 polish)

### Decomposed Observability Stack (STACK)

> Replaces `grafana/otel-lgtm:0.26.0` with five separate containers — the foundation of v2.0; unblocks TSAMP / EXMP / LMET.

- [x] **STACK-01**: Workshop attendee runs `mise run infra:up` and sees five containers running side-by-side: `otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, `grafana/loki:3.7.1`, `grafana/grafana:13.0.1` — the `grafana/otel-lgtm` service is removed entirely from `docker-compose.yml`
- [x] **STACK-02**: All five container images are pinned to exact patch versions (no `:latest`, no `:0.x`-style floating tags); `mise run verify:images` (or equivalent) refuses to pass if any tag matches a floating pattern
- [x] **STACK-03**: Workshop attendee's `OtelSdkConfiguration` files require zero change for this migration — the Collector still listens on `:4317` (gRPC) and `:4318` (HTTP) and the `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` env var stays the same as v1.0
- [x] **STACK-04**: Workshop attendee can open Grafana at `:3000` and see the existing `ose-otel-demo` dashboard load every panel without any "Datasource not found" error — datasource UIDs in the new `grafana/datasources.yaml` provisioning match the UIDs the v1.0 dashboard JSON references (UIDs to be inspected from the running `grafana/otel-lgtm:0.26.0` container during planning research)
- [x] **STACK-05**: Mimir runs with `auth_enabled: false` in `mimir-config.yaml`; after `POST /orders` the workshop attendee sees zero `401` errors in Mimir container logs and metrics are visible in Grafana within ~10 seconds

### Tail Sampling at the Collector (TSAMP)

> Pure Collector configuration; no Java changes. Depends on STACK.

- [x] **TSAMP-01**: The Collector's traces pipeline includes a `tail_sampling` processor with three policies (in this order): `status_code ERROR` keep-100%, `latency` keep-100% above 1s, probabilistic `20%` fallback; `decision_wait: 10s` and `num_traces: 10000` for workshop responsiveness
- [x] **TSAMP-02**: The Collector config file contains an explicit comment block documenting the OR-semantics priority chain (`drop > inverted_not_sample > sample > inverted_sample`) — workshop attendees learn that policies are NOT first-match
- [x] **TSAMP-03**: Workshop attendee runs `scripts/load.sh` (or equivalent), opens Tempo, and observes two facts: (a) every error-status trace is preserved, (b) the volume of non-error traces drops to ~20% of pre-tail-sampling baseline — `step-XX-tail-sampling.png` README screenshot pairs ON-vs-OFF trace counts side-by-side

### Exemplars (EXMP)

> One SDK line per service + Collector + Grafana provisioning. Depends on STACK; pairs with v1.0's `http.server.request.duration` histogram.

- [x] **EXMP-01**: Both `producer-service` and `consumer-service` `OtelSdkConfiguration.buildMeterProvider()` calls add `.setExemplarFilter(ExemplarFilter.traceBased())` (the OTel-spec recommended default; one line per service)
- [x] **EXMP-02**: Mimir is configured with `limits.max_global_exemplars_per_user: 100000` to enable exemplar ingestion (the PRW exporter forwards exemplars unconditionally in collector-contrib v0.151.0 — no Collector config key needed)
- [x] **EXMP-03**: `grafana/datasources.yaml` includes `exemplarTraceIdDestinations` mapping a `trace_id` label to the Tempo datasource UID — the YAML key matches the Grafana 13.0.1 schema verified at planning time
- [x] **EXMP-04**: Workshop attendee opens the `http.server.request.duration` histogram panel in the `ose-otel-demo` dashboard, sees exemplar dots/diamonds rendered on the histogram, clicks one, and lands on the originating trace in Tempo (one click — no manual trace ID copy-paste)

### Log-Based Metrics (LMET)

> Pure Loki + Mimir YAML; no Java changes. Depends on STACK.

- [x] **LMET-01**: Loki runs with the `ruler` component enabled in `loki-config.yaml`, configured to remote-write recording rule outputs to Mimir on the same `auth_enabled: false` tenant as STACK-05
- [x] **LMET-02**: A recording rule lives at `infra/observability/loki-rules/order-errors.yaml` defining `log:order_errors:rate2m` as `sum by (service_name) (rate({service_name=~"order-.+"} |= "ERROR" [2m]))` — the metric name uses the `log:<thing>:<aggregation>` prefix convention to prevent collisions with SDK-emitted metric names
- [x] **LMET-03**: Workshop attendee opens a Grafana panel that plots the log-derived `log:order_errors:rate2m` series alongside the SDK-emitted `orders.created` counter; both share the same `service_name` axis so the workshop can demonstrate the equivalence (and divergence) of "metrics from app code" vs "metrics derived from logs"

### Database Spans — JDBC/JPA (DBSP)

> Extends v1.0 Phase-8 db-cache (which shipped a single `INSERT processed_orders` JdbcTemplate span) with full Spring Data JPA + `@Transactional` boundaries + complete `db.*` semconv. Consumer-service only.

- [x] **DBSP-01**: `consumer-service/pom.xml` adds `org.springframework.boot:spring-boot-starter-data-jpa` (BOM-managed by the existing Spring Boot 3.4.13 BOM — no version override); `org.postgresql:postgresql` is already present from v1.0 Phase 8
- [x] **DBSP-02**: A new `consumer-service/db/Order.java` (`@Entity`), `OrderJpaRepository extends JpaRepository<Order, Long>`, and `OrderJpaService` exist; the consumer's `@RabbitListener` order-processing path persists each order via `OrderJpaService.persist(...)`
- [x] **DBSP-03**: Each repository method invocation is wrapped in a manual `SpanKind.CLIENT` span carrying the full v2.0 `db.*` semconv attribute set: `db.system.name="postgresql"` (NOT the deprecated `db.system`), `db.namespace`, `db.operation.name`, `db.collection.name`, and `db.query.text` set via the `DbAttributes.DB_QUERY_TEXT` constant from semconv 1.40.0 (NEVER the deprecated string literal `"db.statement"`)
- [x] **DBSP-04**: A transaction-level INTERNAL span wraps the `@Transactional` boundary as the parent of the JPA repository spans; the deterministic 10% failure path triggers a rollback that surfaces as `status=ERROR` on that transaction span (span aspect ordered `@Order(HIGHEST_PRECEDENCE)` so it wraps — not is wrapped by — the transaction proxy)
- [x] **DBSP-05**: Workshop attendee can search Tempo for `db.query.text=*` (the new attribute name) and see SELECT/INSERT/UPDATE spans nested under the consumer's CONSUMER span as a waterfall; `step-XX-jpa.png` README screenshot pairs the v1.0 db-cache trace (one DB span) with the v2.0 trace (transaction parent + N JPA child spans)

### Outbound HTTP-Client Spans (HCLI)

> Completes v1.0's HTTP story (which shipped only the SERVER span). New shared module addition; pairs with BAG to demonstrate cross-service baggage.

- [ ] **HCLI-01**: `otel-bootstrap/http/TracingClientHttpRequestInterceptor.java` exports a `ClientHttpRequestInterceptor` that wraps each outbound HTTP call in a `SpanKind.CLIENT` span and injects `traceparent`/`tracestate` (and `baggage` when present) via a `TextMapSetter<HttpHeaders>` — symmetric to `otel-bootstrap/amqp/TracingMessagePostProcessor`
- [ ] **HCLI-02**: Each CLIENT span carries the full v2.0 HTTP-client semconv attribute set: `http.request.method`, `server.address`, `server.port`, `url.full`, `http.response.status_code`, plus `service.peer.name` (NOT the deprecated `peer.service` from older semconv) — `http.response.status_code` is captured on both success and exception paths
- [ ] **HCLI-03**: `producer-service/OrderService.place()` makes one outbound HTTP call to an in-process `NotificationStubController.notify()` endpoint via an injected `RestClient.Builder` bean (NEVER `RestClient.create(url)` — the OTel interceptor must be registered through the builder); the OTel interceptor is registered LAST in the interceptor chain so `traceparent` is the final header set
- [ ] **HCLI-04**: Workshop attendee can `POST /orders`, see in Tempo a CLIENT span as a child of producer's INTERNAL span, and confirm via the in-process target's request log that the `traceparent` header arrived at the receiving end

### Head Sampling (HSAMP)

> SDK-side companion to TSAMP; lives in `OtelSdkConfiguration`. Taught SEPARATELY from tail sampling to avoid double-filter confusion.

- [ ] **HSAMP-01**: Both `OtelSdkConfiguration.buildTracerProvider()` calls swap v1.0's `Sampler.parentBased(Sampler.alwaysOn())` for `Sampler.parentBased(Sampler.traceIdRatioBased(0.5))` — set programmatically in code (NOT via the `OTEL_TRACES_SAMPLER` env var; the workshop teaches the explicit-call shape)
- [ ] **HSAMP-02**: The README explains the head-vs-tail sampling contrast in a paired table: head sampling drops at the SDK before export (saves bandwidth, no view of full trace); tail sampling drops at the Collector after the trace is assembled (sees full trace before deciding) — the `step-XX-head-sampling.png` README screenshot pairs with `step-XX-tail-sampling.png` (TSAMP-03)
- [ ] **HSAMP-03**: Workshop attendee runs the load script for 100 requests and observes ~50 traces in Tempo (50% head-sampling ratio) — and can see in the console-emitted spans that the SDK is dropping spans before export, demonstrating that no Collector-side processor saw the dropped traces

### Baggage (BAG)

> W3C baggage propagation — head request → producer span → AMQP → consumer span. v1.0's propagator composition already includes `W3CBaggagePropagator`; v2.0 adds the SET/READ/STAMP layer on top.

- [ ] **BAG-01**: `OrderController.create()` reads an `X-Customer-Tier` HTTP header (default `"standard"` if absent) and sets it as the `order.customer-tier` baggage entry via `Baggage.builder().put(...).build().storeInContext(...)` for the duration of the request scope
- [ ] **BAG-02**: A new shared `SpanProcessor` in `otel-bootstrap` is registered on both `SdkTracerProvider`s; on span start it reads `Baggage.fromContext()` and stamps an explicit allowlist of keys (e.g., `customer-tier`) as span attributes named `baggage.<key>` — the allowlist prevents cardinality explosion from arbitrary baggage entries
- [ ] **BAG-03**: `TracingMessageListenerAdvice.invoke()` calls `extractedContext.makeCurrent()` for the ENTIRE listener body (not just for span parenting) — so `Baggage.current()` returns a populated `Baggage` instance throughout `OrderListener.onOrder()` and any code it calls
- [ ] **BAG-04**: Workshop attendee can run `curl -H "X-Customer-Tier: gold" -X POST http://localhost:8080/orders -d '{...}'` and observe in Tempo that BOTH the producer span and the consumer span (across the AMQP boundary) carry `baggage.customer-tier=gold` as a span attribute; one trace, two spans, both stamped

### AMQP Topic + DLX Variants (AMQP)

> Topic exchange + dead-letter exchange on top of v1.0's direct exchange. Span-link teaching deferred — fanout (V2-TS-8a) lives in v2.x backlog. Existing `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` are exchange-type-agnostic and require NO changes.

- [ ] **AMQP-01**: `consumer-service/config/RabbitTopologyConfig.java` declares: a topic exchange `orders.topic`, a queue `orders.standard.q` bound with the routing-key pattern `orders.*.standard`, and a DLX exchange `orders.dlx` with queue `orders.dlq` bound to receive dead-lettered messages from `orders.standard.q`
- [ ] **AMQP-02**: `producer-service`'s `OrderPublisher` publishes to the topic exchange with a routing key like `orders.us-east.standard`; the producer span carries `messaging.rabbitmq.destination.routing_key=orders.us-east.standard` as a span attribute (the routing key is templated from a small low-cardinality region set, NEVER user-controlled per-message values)
- [ ] **AMQP-03**: When the v1.0 deterministic 10% failure path triggers in the consumer, `setDefaultRequeueRejected(false)` routes the failed message to the DLX; a new `consumer-service/DlxRetryListener` `@RabbitListener` consumes from `orders.dlq`
- [ ] **AMQP-04**: `DlxRetryListener.onDlx()` STRIPS the `traceparent` and `tracestate` headers from the dead-lettered message BEFORE any retry-publish, then opens a new trace via `tracer.spanBuilder("dlx.retry").setNoParent().addLink(originalSpanContext).startSpan()` — workshop attendee sees in Tempo two SEPARATE traces connected by a span link (NEVER an infinite loop where the same trace gains spans on every retry)
- [ ] **AMQP-05**: An integration test at `integration-tests/.../DlxTopologyIT.java` proves three behaviors using `InMemorySpanExporter`: (a) a routing key `orders.us-east.standard` reaches `orders.standard.q`, (b) a triggered failure dead-letters the message to `orders.dlq`, (c) the DLX retry produces a NEW trace ID with a `Link` to the original failed trace

---

## Future Requirements

> Acknowledged scope that's deferred to v2.x or beyond. Not blocking v2.0 ship.

### Deferred from v2.0 user-scoping

- **AMQP fanout + span LINKS lesson** (was V2-TS-8a) — Fanout exchange with 2 consumers using `Span.addLink(producerContext)` instead of `setParent(extractedContext)`; the headline span-link pedagogical lesson. Deferred at v2.0 scoping; expected to anchor a future "messaging deepdive" milestone alongside Kafka.
- **gRPC service-to-service** — Adds a new service shape (Spring gRPC starter, ServerInterceptor / ClientInterceptor lessons, `rpc.*` semconv); substantial enough to anchor its own milestone.
- **`@Scheduled` / `@Async` span lifecycle** — Background-job tracing patterns: root spans without a parent, `Context.taskWrapping`, the workshop pattern for scheduled-job telemetry. Orthogonal to the production-shapes theme.

### Differentiators / polish (consider for late-v2.0 or v2.x)

- Side-by-side trace count ON-vs-OFF for tail sampling in the README (companion to TSAMP-03)
- Synthetic high-latency request scenario producing a P99 exemplar dot demo
- N+1 query waterfall demonstration via JPA — teaches the granularity lesson and how lazy loading explodes spans (companion to DBSP)
- `url.template` vs `url.full` cardinality demonstration for HTTP client spans (companion to HCLI)
- Baggage visible in `curl -v` outgoing `baggage:` header (cross-references HCLI + BAG)

### Other deferred items

- Kafka producer/consumer alongside AMQP (alternative messaging system)
- WebFlux / reactive HTTP path with Reactor Context propagation
- `OpenTelemetryAppender` for Log4j2 (v1.0 shipped only the Logback bridge)
- A second-cohort feedback round before v2.x scoping

---

## Out of Scope

> Hard exclusions for v2.0 — same boundaries as v1.0 unless explicitly noted, plus v2.0-specific anti-features.

### Carried forward from v1.0 (still excluded)

- OpenTelemetry Java Agent (`-javaagent:opentelemetry-javaagent.jar`) — the entire workshop teaches the manual SDK
- `opentelemetry-spring-boot-starter` / `micrometer-tracing-bridge-otel` — same reason
- Kubernetes manifests / Helm / Tilt / Skaffold — workshop runs on a laptop with `docker compose` + `mise`
- Lower-level `com.rabbitmq:amqp-client` direct usage — Spring AMQP remains the idiomatic path
- GraalVM native-image builds
- Cloud observability backends (Honeycomb, Datadog, New Relic, AWS X-Ray, GCP Trace) — endpoint change attendees can do themselves
- A frontend / web UI
- Authentication / authorization on the HTTP API
- Multi-host distributed deployment
- Performance benchmarking / load testing harnesses (the existing `scripts/load.sh` is for demo, not benchmarking)

### v2.0-specific exclusions

- **Grafana Alloy** — Collector-contrib is the canonical OTel teaching surface; Alloy is a Grafana-specific superset that adds non-OTel concepts orthogonal to the workshop's goal
- **Multi-Collector load-balancing** — single-Collector pipeline is the teaching shape; LB adds operational complexity without an instrumentation lesson
- **Prometheus scrape pull model** — the Collector's `prometheusremotewrite` push path is the production-shape we teach; pull model is documented in OTel docs and is a simple inversion for attendees who need it
- **AOP-based auto-DB-span injection** — DBSP requires manual span code so attendees see the SDK calls explicitly (same TRACE-01 / DOC-05 principle that drove per-service `OtelSdkConfiguration` duplication in v1.0)
- **`WebClient` reactive propagation** — Reactor Context vs OTel Context interaction is its own lesson worthy of a dedicated milestone; v2.0 sticks to `RestClient` (synchronous) for HCLI
- **Spring AMQP `ObservationRegistry` / Micrometer bridge** — would route AMQP spans through Micrometer, hiding the SDK calls v1.0 deliberately exposed
- **AMQP RPC pattern** — adds reply-queue + correlation-id machinery without adding instrumentation lessons; the topic + DLX shapes already cover the v2.0 propagation arc

---

## Traceability

> Filled by `gsd-roadmapper` after roadmap creation. Maps each REQ-ID to the phase that delivers it.

| REQ-ID | Category | Phase | Status |
|--------|----------|-------|--------|
| PREREQ-01 | Prerequisites | Phase 10 | Pending |
| PREREQ-02 | Prerequisites | Phase 10 | Pending |
| STACK-01 | Decomposed Stack | Phase 10 | Pending |
| STACK-02 | Decomposed Stack | Phase 10 | Pending |
| STACK-03 | Decomposed Stack | Phase 10 | Pending |
| STACK-04 | Decomposed Stack | Phase 10 | Pending |
| STACK-05 | Decomposed Stack | Phase 10 | Pending |
| TSAMP-01 | Tail Sampling | Phase 11 | Pending |
| TSAMP-02 | Tail Sampling | Phase 11 | Pending |
| TSAMP-03 | Tail Sampling | Phase 11 | Pending |
| EXMP-01 | Exemplars | Phase 12 | Pending |
| EXMP-02 | Exemplars | Phase 12 | Pending |
| EXMP-03 | Exemplars | Phase 12 | Pending |
| EXMP-04 | Exemplars | Phase 12 | Pending |
| LMET-01 | Log-Based Metrics | Phase 13 | Pending |
| LMET-02 | Log-Based Metrics | Phase 13 | Pending |
| LMET-03 | Log-Based Metrics | Phase 13 | Pending |
| DBSP-01 | Database Spans | Phase 14 | Complete |
| DBSP-02 | Database Spans | Phase 14 | Complete |
| DBSP-03 | Database Spans | Phase 14 | Complete |
| DBSP-04 | Database Spans | Phase 14 | Complete |
| DBSP-05 | Database Spans | Phase 14 | Complete |
| HCLI-01 | HTTP Client Spans | Phase 15 | Pending |
| HCLI-02 | HTTP Client Spans | Phase 15 | Pending |
| HCLI-03 | HTTP Client Spans | Phase 15 | Pending |
| HCLI-04 | HTTP Client Spans | Phase 15 | Pending |
| HSAMP-01 | Head Sampling | Phase 16 | Pending |
| HSAMP-02 | Head Sampling | Phase 16 | Pending |
| HSAMP-03 | Head Sampling | Phase 16 | Pending |
| BAG-01 | Baggage | Phase 16 | Pending |
| BAG-02 | Baggage | Phase 16 | Pending |
| BAG-03 | Baggage | Phase 16 | Pending |
| BAG-04 | Baggage | Phase 16 | Pending |
| AMQP-01 | AMQP Topic + DLX | Phase 17 | Pending |
| AMQP-02 | AMQP Topic + DLX | Phase 17 | Pending |
| AMQP-03 | AMQP Topic + DLX | Phase 17 | Pending |
| AMQP-04 | AMQP Topic + DLX | Phase 17 | Pending |
| AMQP-05 | AMQP Topic + DLX | Phase 17 | Pending |

---

*Last updated: 2026-05-02 — v2.0 Production Shapes roadmap revised to 8 phases (10–17); traceability table updated (35/35 REQ-IDs mapped to Phases 10–17).*
