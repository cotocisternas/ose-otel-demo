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

- [ ] **TRACE-01**: Each service contains its own `OtelSdkConfiguration.java` that builds `OpenTelemetrySdk` manually via `OpenTelemetrySdk.builder()` (no shared library, no autoconfigure starter, no Java agent) — the duplication is intentional so attendees read the SDK setup twice
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

Phase mapping is filled by the roadmap; this table will be updated when `gsd-roadmapper` runs.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | TBD | Pending |
| INFRA-02 | TBD | Pending |
| INFRA-03 | TBD | Pending |
| INFRA-04 | TBD | Pending |
| INFRA-05 | TBD | Pending |
| APP-01 | TBD | Pending |
| APP-02 | TBD | Pending |
| APP-03 | TBD | Pending |
| APP-04 | TBD | Pending |
| APP-05 | TBD | Pending |
| TRACE-01 | TBD | Pending |
| TRACE-02 | TBD | Pending |
| TRACE-03 | TBD | Pending |
| TRACE-04 | TBD | Pending |
| TRACE-05 | TBD | Pending |
| TRACE-06 | TBD | Pending |
| TRACE-07 | TBD | Pending |
| TRACE-08 | TBD | Pending |
| TRACE-09 | TBD | Pending |
| PROP-01 | TBD | Pending |
| PROP-02 | TBD | Pending |
| PROP-03 | TBD | Pending |
| PROP-04 | TBD | Pending |
| METRIC-01 | TBD | Pending |
| METRIC-02 | TBD | Pending |
| METRIC-03 | TBD | Pending |
| METRIC-04 | TBD | Pending |
| LOG-01 | TBD | Pending |
| LOG-02 | TBD | Pending |
| LOG-03 | TBD | Pending |
| LOG-04 | TBD | Pending |
| LOG-05 | TBD | Pending |
| TEST-01 | TBD | Pending |
| TEST-02 | TBD | Pending |
| TEST-03 | TBD | Pending |
| TEST-04 | TBD | Pending |
| TEST-05 | TBD | Pending |
| TEST-06 | TBD | Pending |
| DOC-01 | TBD | Pending |
| DOC-02 | TBD | Pending |
| DOC-03 | TBD | Pending |
| DOC-04 | TBD | Pending |
| DOC-05 | TBD | Pending |
| WORK-01 | TBD | Pending |
| WORK-02 | TBD | Pending |
| WORK-03 | TBD | Pending |

**Coverage:**

- v1 requirements: 45 total
- Mapped to phases: 0 (filled by roadmapper)
- Unmapped: 45 ⚠️ (will be 0 after `gsd-roadmapper`)

---
*Requirements defined: 2026-04-29*
*Last updated: 2026-04-29 after initial definition*
