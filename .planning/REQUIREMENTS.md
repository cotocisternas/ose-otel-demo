# Requirements: OSE OTel Demo

**Defined:** 2026-04-29
**Core Value:** A workshop attendee can clone the repo, run `docker compose up` + `mise run dev`, hit `POST /orders`, and see a single distributed trace flow from the HTTP handler through the RabbitMQ publish, into the consumer's processing logic, with correlated metrics and logs — and understand exactly which lines of SDK code made each piece work.

## v1 Requirements

Requirements for initial release. Each maps to a roadmap phase. All requirements are user-centric and testable from the workshop attendee's perspective.

### Infrastructure (INFRA)

- [ ] **INFRA-01**: Workshop attendee can run `mvn -pl producer-service` from a parent POM that imports the OpenTelemetry BOM (`opentelemetry-bom:1.61.0`) **before** the Spring Boot BOM (`spring-boot-dependencies:3.4.13`) plus the OpenTelemetry Instrumentation alpha BOM (`opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`); `mvn dependency:tree` shows exactly one version per `io.opentelemetry` artifact
- [ ] **INFRA-02**: Workshop attendee with `mise` installed gets the right toolchain (Amazon Corretto 17 + Maven 3.9) automatically via a committed `mise.toml` and `.tool-versions`
- [ ] **INFRA-03**: Workshop attendee runs `mise run preflight` to verify Docker is up, ports 3000/4317/4318/5672/15672 are free, mise tools are installed, and Java 17 is active before starting any step
- [ ] **INFRA-04**: Workshop attendee runs `mise run infra:up` to start `rabbitmq:4.3-management` and `grafana/otel-lgtm:0.26.0` via `docker compose`; `mise run infra:down` tears them down without losing Grafana state across cycles
- [ ] **INFRA-05**: Workshop attendee runs `mise run dev` (parallel) or `mise run dev:producer` / `mise run dev:consumer` (per-service) to start each Spring Boot app on the host JVM with `OTEL_EXPORTER_OTLP_ENDPOINT` and `SPRING_RABBITMQ_*` environment variables pre-wired

### Application functionality (APP)

- [ ] **APP-01**: Workshop attendee can `POST /orders` to the producer service with a JSON payload and receive a 202 with the assigned order ID
- [ ] **APP-02**: The producer service publishes an `OrderCreated` message to a RabbitMQ direct exchange via Spring AMQP `RabbitTemplate.convertAndSend(...)` for every successful POST
- [ ] **APP-03**: The consumer service receives `OrderCreated` messages via a `@RabbitListener` and simulates downstream domain work (payment + inventory)
- [ ] **APP-04**: The consumer's business logic fails deterministically on every 10th order so the workshop demonstrates the `recordException` + `setStatus(ERROR)` pattern
- [ ] **APP-05**: Both services expose `/actuator/health` so docker-compose health checks pass

### Manual SDK bootstrap & traces (TRACE)

- [x] **TRACE-01**: Each service contains its own `OtelSdkConfiguration.java` that builds `OpenTelemetrySdk` manually via `OpenTelemetrySdk.builder()` (no shared library, no autoconfigure starter, no Java agent) — the duplication is intentional so attendees read the SDK setup twice
- [ ] **TRACE-02**: Each service's `Resource` is built with the new `io.opentelemetry.semconv:1.40.0` constants for `service.name`, `service.namespace`, `service.instance.id`, and `deployment.environment.name` — Tempo shows distinct service names (never `unknown_service:java`)
- [ ] **TRACE-03**: Each service registers a `SdkTracerProvider` with `BatchSpanProcessor` + `OtlpGrpcSpanExporter` targeting `:4317` and an explicit `Sampler.parentBased(Sampler.alwaysOn())` chosen with a code comment that explains the production-vs-workshop tradeoff
- [ ] **TRACE-04**: `OpenTelemetrySdk` is registered as `@Bean(destroyMethod = "close")` so a graceful shutdown (`Ctrl-C`) flushes the final batch of spans, metrics, and logs
- [ ] **TRACE-05**: A SERVER span wraps every `POST /orders` invocation with HTTP semantic-convention attributes (`http.request.method`, `url.path`, `http.response.status_code`)
- [ ] **TRACE-06**: An INTERNAL span wraps each domain method in both services (e.g., `OrderService.place(...)`, `ProcessingService.process(...)`) so attendees see nested business-logic spans
- [ ] **TRACE-07**: A PRODUCER span wraps the publish call with messaging semantic-convention attributes (`messaging.system=rabbitmq`, `messaging.destination.name`, `messaging.operation=publish`)
- [ ] **TRACE-08**: A CONSUMER span wraps the listener handler with messaging semantic-convention attributes (`messaging.operation=process`)
- [ ] **TRACE-09**: The deterministic 10% failure path calls `span.recordException(e)` and `span.setStatus(StatusCode.ERROR)` so Tempo shows the trace as `Error` status with the exception event attached

### AMQP context propagation (PROP) — the headline lesson

- [ ] **PROP-01**: A `TracingMessagePostProcessor` registered on `RabbitTemplate.setBeforePublishPostProcessors(...)` injects W3C `traceparent`/`tracestate` headers into `MessageProperties` via a `TextMapSetter<MessageProperties>` writing **String** values (never `byte[]`)
- [ ] **PROP-02**: A `TracingMessageListenerAdvice` registered on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` extracts the W3C context from `MessageProperties` via a `TextMapGetter<MessageProperties>` and calls `Context.makeCurrent()` so the listener executes inside the extracted span context
- [ ] **PROP-03**: After step-03 the workshop attendee can issue `POST /orders` and see in Tempo a single trace whose `traceId` spans both services, with the consumer span's `parentSpanId` equal to the producer span's `spanId`
- [ ] **PROP-04**: The shared `otel-bootstrap` Maven module exports the propagation pair (the symmetry of one inject method matched by one extract method IS the lesson); the README explicitly states why the propagation pair is shared while the SDK bootstrap is duplicated

### Metrics (METRIC)

- [ ] **METRIC-01**: Each service registers a `SdkMeterProvider` with `PeriodicMetricReader` set to a 10-second interval (overriding the 60-second default) plus `OtlpGrpcMetricExporter` to `:4317`
- [ ] **METRIC-02**: A `LongCounter` named `orders.created` increments on every successful order with business-attribute tags (e.g., `order.priority`)
- [ ] **METRIC-03**: A `DoubleHistogram` named `http.server.request.duration` (seconds, semconv-aligned) records every HTTP request with `http.request.method` and `http.response.status_code` attributes
- [ ] **METRIC-04**: An `ObservableGauge` named `orders.queue.depth.estimate` reports a synthetic queue-depth value on each collection cycle so attendees see all three instrument shapes

### Logs (LOG)

- [ ] **LOG-01**: Each service registers a `SdkLoggerProvider` with `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter` to `:4317`
- [ ] **LOG-02**: Each service's `logback-spring.xml` includes the `OpenTelemetryAppender` from `opentelemetry-logback-appender-1.0` plus an MDC injector that adds `trace_id` and `span_id` to every log record
- [ ] **LOG-03**: `OpenTelemetryAppender.install(openTelemetry)` is called from a `@PostConstruct` method on the SDK config class so the appender is wired AFTER the SDK bean is built
- [ ] **LOG-04**: The console log pattern in both services includes `trace_id=%X{trace_id} span_id=%X{span_id}` so terminal output is correlatable without leaving the workshop laptop
- [ ] **LOG-05**: A workshop attendee can run a Loki query `{service_name="order-producer"} |~ "<traceId>"` in Grafana and click a log line to jump directly to the matching trace in Tempo

### Tests (TEST)

- [ ] **TEST-01**: Each service's `@SpringBootTest` uses `@ServiceConnection` with Testcontainers `RabbitMQContainer` so tests provision a fresh RabbitMQ on a random port (NOT the host docker-compose RabbitMQ); test logs prove this by showing the random port
- [ ] **TEST-02**: A `@TestConfiguration` substitutes `InMemorySpanExporter` + `SimpleSpanProcessor` (NOT `BatchSpanProcessor`) for the OTLP exporter so test assertions are deterministic
- [ ] **TEST-03**: A cross-service integration test triggers the full `POST /orders` → publish → consume flow and asserts that producer and consumer spans share the same `traceId`
- [ ] **TEST-04**: The same test asserts the consumer span's `parentSpanId` equals the producer span's `spanId` (proving the inject/extract pair works)
- [ ] **TEST-05**: The same test asserts both spans carry correct `SpanKind` (`PRODUCER` / `CONSUMER`) and the messaging semantic-convention attributes
- [ ] **TEST-06**: `mise run test` runs all module tests and exits non-zero on any assertion failure (suitable for CI)

### Documentation (DOC)

- [ ] **DOC-01**: A `README.md` walks the workshop attendee through each step keyed to the matching annotated git tag (`step-01-baseline` through `step-06-tests`) with copy-pasteable curl commands
- [ ] **DOC-02**: README "Prerequisites" section lists ports, tools, and the `mise run preflight` task; first-time attendees can self-diagnose tooling issues before getting stuck
- [ ] **DOC-03**: `OtelSdkConfiguration.java` is heavily commented — every SDK builder call has an inline comment explaining what it does and why; the code IS the workshop's textbook
- [ ] **DOC-04**: README includes Grafana screenshots that pair `step-02-traces` (TWO disconnected traces) with `step-03-context-propagation` (ONE joined trace) so the broken-then-fixed delta is visible without running the steps
- [ ] **DOC-05**: README explicitly states that the per-service duplication of `OtelSdkConfiguration` is intentional and explains why (so readers don't "fix" it by extracting a shared library)

### Workshop artifacts (WORK)

- [ ] **WORK-01**: Annotated git tags `step-01-baseline`, `step-02-traces`, `step-03-context-propagation`, `step-04-metrics`, `step-05-logs`, `step-06-tests` exist on `main` and immutably mark each workshop checkpoint
- [ ] **WORK-02**: A pre-built Grafana dashboard JSON is committed at a known path and either auto-provisioned by the lgtm container or imported in one click from the README so attendees see all three signals on one panel
- [ ] **WORK-03**: A `scripts/load.sh` script issues a steady stream of `POST /orders` requests so live demos have continuously-flowing traces, metrics, and logs without manual clicking

## v2 Requirements

Deferred to a future workshop iteration. Tracked but not in current roadmap.

### Sampling & propagation deep-dives

- **SAMP-01**: A `step-07-sampling-variant` checkpoint demonstrating `TraceIdRatioBased` and `ParentBased` samplers side-by-side with environment-driven config
- **PROP-V2-01**: A `step-08-baggage` checkpoint showing `W3CBaggagePropagator` carrying business attributes across the AMQP boundary

### Failure-path richness

- **FAIL-01**: A `step-09-dlx-retry` checkpoint adding a dead-letter exchange and retry instrumentation so attendees see the messaging-semconv `messaging.rabbitmq.destination_routing_key` and retry counter patterns

### Facilitator support

- **FAC-01**: A `FACILITATOR.md` with timing notes, common questions, and "if you see X, do Y" troubleshooting — needed only when someone other than the original author delivers the workshop

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| OpenTelemetry Java Agent (`-javaagent:opentelemetry-javaagent.jar`) | The workshop's pedagogical goal is teaching the manual SDK; auto-instrumentation hides the mechanics we want to expose |
| Spring Boot OTel starter / `micrometer-tracing-bridge-otel` | Same reason; framework-mediated `Tracer`/`Meter`/`Logger` calls would obscure the lesson |
| `@WithSpan` annotations | Smuggle auto-instrumentation back in; defeat the manual-SDK pedagogy |
| Shared library that auto-configures `OpenTelemetrySdk` | AF-15 — duplicate SDK bootstrap per-service is the correct teaching shape; library would hide one of the two readings |
| Lower-level RabbitMQ Java client (`com.rabbitmq:amqp-client`) examples | Spring AMQP is the idiomatic Spring path attendees will recognize |
| Kubernetes manifests, Helm charts, Tilt/Skaffold | Workshop runs on a laptop with `docker compose` + `mise`, not a cluster |
| Production-grade RabbitMQ topology (DLX, topic exchange, RPC) | One direct exchange + one queue keeps the trace-propagation lesson the headline |
| Multiple AMQP patterns (fanout, topic, RPC, work-queue variants) | Adds surface area without adding instrumentation lessons; v2 candidate |
| GraalVM native-image build | Adds JVM/build complexity orthogonal to OTel |
| Cloud observability backends (Honeycomb, Datadog, New Relic, Lightstep, AWS X-Ray, GCP Trace) | `otel-lgtm` is the demo backend; pointing at a SaaS is a one-line OTLP endpoint change attendees can do themselves |
| Web UI / frontend | The demo is server-side; HTTP traffic is generated via curl or `scripts/load.sh` |
| Authentication / authorization on the HTTP API | Irrelevant to the instrumentation lesson and would distract from it |
| Database persistence (JPA, R2DBC, JDBC) | The order/consumer logic is simulated in memory; DB instrumentation is a separable workshop |
| Distributed deployment across multiple hosts | Both services run on the workshop attendee's laptop |
| Performance benchmarking / JMH harnesses | Out of scope for the teaching artifact |
| Pyroscope / continuous profiling | Out of scope for v1; could be a v2 if the workshop wants to extend into the fourth signal |

## Traceability

Every v1 requirement maps to exactly one phase. Updated by `gsd-roadmapper` 2026-04-29.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 1 | Pending |
| INFRA-02 | Phase 1 | Pending |
| INFRA-03 | Phase 1 | Pending |
| INFRA-04 | Phase 1 | Pending |
| INFRA-05 | Phase 1 | Pending |
| APP-01 | Phase 1 | Pending |
| APP-02 | Phase 1 | Pending |
| APP-03 | Phase 1 | Pending |
| APP-04 | Phase 3 | Pending |
| APP-05 | Phase 1 | Pending |
| TRACE-01 | Phase 2 | Complete |
| TRACE-02 | Phase 2 | Pending |
| TRACE-03 | Phase 2 | Pending |
| TRACE-04 | Phase 2 | Pending |
| TRACE-05 | Phase 2 | Pending |
| TRACE-06 | Phase 2 | Pending |
| TRACE-07 | Phase 2 | Pending |
| TRACE-08 | Phase 2 | Pending |
| TRACE-09 | Phase 3 | Pending |
| PROP-01 | Phase 3 | Pending |
| PROP-02 | Phase 3 | Pending |
| PROP-03 | Phase 3 | Pending |
| PROP-04 | Phase 3 | Pending |
| METRIC-01 | Phase 4 | Pending |
| METRIC-02 | Phase 4 | Pending |
| METRIC-03 | Phase 4 | Pending |
| METRIC-04 | Phase 4 | Pending |
| LOG-01 | Phase 5 | Pending |
| LOG-02 | Phase 5 | Pending |
| LOG-03 | Phase 5 | Pending |
| LOG-04 | Phase 5 | Pending |
| LOG-05 | Phase 5 | Pending |
| TEST-01 | Phase 6 | Pending |
| TEST-02 | Phase 6 | Pending |
| TEST-03 | Phase 6 | Pending |
| TEST-04 | Phase 6 | Pending |
| TEST-05 | Phase 6 | Pending |
| TEST-06 | Phase 6 | Pending |
| DOC-01 | Phase 7 | Pending |
| DOC-02 | Phase 1 | Pending |
| DOC-03 | Phase 2 | Pending |
| DOC-04 | Phase 7 | Pending |
| DOC-05 | Phase 2 | Pending |
| WORK-01 | Phase 1 | Pending |
| WORK-02 | Phase 7 | Pending |
| WORK-03 | Phase 7 | Pending |

**Coverage:**

- v1 requirements: 46 total (header in original definition said 45; recount across all 9 categories — INFRA 5 + APP 5 + TRACE 9 + PROP 4 + METRIC 4 + LOG 5 + TEST 6 + DOC 5 + WORK 3 = 46)
- Mapped to phases: 46 ✓
- Unmapped: 0 ✓
- Per-phase distribution: P1=11, P2=10, P3=6, P4=4, P5=5, P6=6, P7=4

### Judgment Calls (documented for traceability)

- **APP-04** (deterministic 10th-order failure): mapped to **Phase 3**, not Phase 1 or Phase 2. Rationale: APP-04 wires the *failure path* whose only purpose is to produce the error condition that TRACE-09 (`recordException` + `setStatus(ERROR)`) reacts to. Wiring APP-04 in Phase 1 would create a deterministic failure with no instrumentation around it (a defect, not a lesson); wiring it in Phase 2 would force TRACE-09 into Phase 2 and bloat the SDK-bootstrap lesson. Landing both in Phase 3 keeps the "first failure → first error span" pairing pedagogically tight.
- **TRACE-09** (`recordException` + `setStatus(ERROR)`): mapped to **Phase 3** alongside APP-04 (see above).
- **WORK-01** (annotated git tags `step-01` through `step-06`): mapped to **Phase 1** because it is one requirement covering six tags and the *first* tag is created at Phase 1's exit gate, establishing the convention. Each subsequent phase's success criteria explicitly require the matching tag to be created at that phase's exit (callout in each `### Phase N` section); we did not split WORK-01 across phases because that would inflate the requirement count artificially.
- **DOC-01** (full README walkthrough): mapped to **Phase 7** because the walkthrough requires *all* six step-tags to exist before it can be authored end-to-end with copy-pasteable curl commands. Per-phase README skeleton increments are absorbed into each phase's "tag this checkpoint" exit work but do not constitute the DOC-01 deliverable.
- **DOC-02** (Prerequisites section): mapped to **Phase 1** because it is the gate that prevents pre-OTel friction and must exist before any attendee runs `mise run preflight`.
- **DOC-03** (heavy comments on `OtelSdkConfiguration`): mapped to **Phase 2** because that is when `OtelSdkConfiguration.java` first exists; the heavy-commenting quality bar applies from the moment the file is written.
- **DOC-05** (deliberate-duplication callout): mapped to **Phase 2** because that is when the duplication first becomes visible in the codebase — the callout must land in the same phase to head off "fix the duplication" PRs from a reader.

---

*Requirements defined: 2026-04-29*
*Last updated: 2026-04-29 by gsd-roadmapper — traceability filled, judgment calls documented*
