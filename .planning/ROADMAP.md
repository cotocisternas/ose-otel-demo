# Roadmap: OSE OTel Demo

## Overview

This roadmap delivers a workshop-grade demo that teaches engineers how to instrument a Spring Boot 3.4.13 / Java 17 application using the OpenTelemetry Java SDK directly across an HTTP → AMQP → consumer flow. The journey moves from a working-but-uninstrumented baseline (Phase 1) to manual SDK bootstrap with traces in **separate** producer and consumer trace IDs (Phase 2 — intentionally broken), to the **headline lesson** of W3C context propagation across the AMQP boundary that joins them (Phase 3), then layers metrics (Phase 4) and logs correlation (Phase 5), proves the chain in CI with Testcontainers (Phase 6), and finally polishes for first-cohort delivery (Phase 7). Each phase is tagged on `main` (`step-01-baseline` through `step-06-tests` plus the polish state) so attendees can `git checkout` to time-travel through the workshop. The broken-then-fixed delta between Phase 2 and Phase 3 is **load-bearing** and must not be merged or reordered.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Baseline & Scaffold** *(shipped 2026-04-29; tag `step-01-baseline`)* — Working two-service Spring Boot + RabbitMQ app on host JVM with ZERO telemetry; foundation pitfalls neutralised (BOM ordering, ports, mise/IDE)
- [x] **Phase 2: Manual SDK Bootstrap & First Traces** *(shipped 2026-05-01; tag `step-02-traces`)* — `OpenTelemetrySdk` wired per-service, traces emitted, but producer and consumer in **separate** traces (intentional setup for Phase 3's "aha")
- [x] **Phase 3: AMQP Context Propagation** — THE headline lesson: `TextMapSetter`/`TextMapGetter` pair joins producer and consumer into ONE trace
- [ ] **Phase 4: Metrics** — `SdkMeterProvider` + Counter, Histogram, ObservableGauge instrument shapes flowing to Mimir
- [x] **Phase 5: Logs Correlation** — `OpenTelemetryAppender` + MDC trace_id/span_id; Loki-to-Tempo click-through working (completed 2026-05-02)
- [ ] **Phase 6: Verification Tests** — Testcontainers `RabbitMQContainer` + `InMemorySpanExporter` proves the full chain in CI
- [ ] **Phase 7: Polish & Differentiators** — Pre-built dashboard, load script, screenshots, full README walkthrough

## Phase Details

### Phase 1: Baseline & Scaffold
**Goal**: Workshop attendee can clone the repo and have a working two-service Spring Boot + RabbitMQ application running on the host JVM via `mise`, with infrastructure (RabbitMQ + `grafana/otel-lgtm`) in `docker compose`, and **zero OpenTelemetry libraries on the classpath**. This phase exists to neutralise foundation-layer pitfalls (BOM ordering, port collisions, mise/IDE coupling) before any OTel surface is exposed — so every subsequent OTel lesson is uncontaminated by tooling friction.
**Depends on**: Nothing (first phase)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, APP-01, APP-02, APP-03, APP-05, DOC-02, WORK-01
**Success Criteria** (what must be TRUE):
  1. Workshop attendee with `mise` installed runs `mise run preflight` and sees a green report (Docker up, ports 3000/4317/4318/5672/15672 free, Java 17 active, Maven 3.9 active).
  2. Workshop attendee runs `mise run infra:up` then `mise run dev` and sees both Spring Boot apps log a "Started" line; `mise run demo:order` (or curl `POST /orders`) returns 202 and the consumer logs an `OrderCreated` receipt.
  3. `mvn dependency:tree -Dincludes=io.opentelemetry` returns **zero** matches — no OTel libraries are pulled in yet — proving the baseline is truly uninstrumented.
  4. The annotated git tag `step-01-baseline` exists on `main` and points at this commit; checking it out reproduces the green-baseline state.
  5. README "Prerequisites" section exists and lists exact tool versions, ports, and the `mise run preflight` command — a first-time attendee can self-diagnose tooling issues before opening any code.
**Plans** (6 plans, 3 waves):
- **Wave 1** *(parallelizable, no dependencies)* — **✅ complete**
  - [x] `1-01-maven-skeleton` — INFRA-01 — parent POM + 3 child modules with BOM-import ordering
  - [x] `1-02-mise-toolchain` — INFRA-02, INFRA-03, INFRA-05 — `mise.toml` + `.tool-versions` + 14 named tasks (preflight, dev, infra:up/down, etc.)
  - [x] `1-03-docker-compose` — INFRA-04 — `docker-compose.yml` (rabbitmq:4.3-management + grafana/otel-lgtm:0.26.0) with healthchecks
- **Wave 2** *(blocked on Wave 1 completion)* — **✅ complete**
  - [x] `1-04-producer-service` — APP-01, APP-02, APP-05 — `OrderController` + `OrderPublisher` + `RabbitConfig` + Spring Boot app
  - [x] `1-05-consumer-service` — APP-03, APP-05 — `@RabbitListener` + `ProcessingService` + Spring Boot app
- **Wave 3** *(blocked on Waves 1+2 completion; contains human checkpoint)* — **✅ complete**
  - [x] `1-06-readme-and-exit-gate` — DOC-02 + WORK-01 — README Prerequisites + .gitignore + annotated tag `step-01-baseline` (criterion 4 reproducibility self-test verified via /tmp/verify-baseline clone)

**Cross-cutting constraints** *(must_haves shared across plans)*:
- `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matches (asserted by 1-01, 1-02, 1-04, 1-05, 1-06)
- OTel BOMs declared **before** Spring Boot BOM in parent `<dependencyManagement>` (1-01 + enforced by 1-02 `verify:bom`)
- `mise.toml` Java pin is exact patch `corretto-17.0.13.11.1` (not floating `corretto-17`)
- Apps run on host JVM via `mise`; only RabbitMQ + lgtm in `docker-compose`
**Notes**:
- WORK-01 (annotated git tags) lands here because the *first* tag is the exit gate; later phases each create their own tag at their exit, but the convention and tooling are established now.
- APP-04 (deterministic 10% failure) is **deferred to Phase 3** because the recordException pattern is part of the propagation/error-span lesson — see TRACE-09 mapping.
- DOC-02 belongs here (Prerequisites section is the gate that prevents pre-OTel friction); the rest of the README walkthrough (DOC-01) lands in Phase 7 once all six steps exist to walk through.

### Phase 2: Manual SDK Bootstrap & First Traces
**Goal**: Workshop attendee can read `OtelSdkConfiguration.java` line-by-line in **both** services (deliberately duplicated, not extracted to a shared library), see the smallest possible OpenTelemetry surface — `OpenTelemetrySdk.builder()` with `Resource`, `SdkTracerProvider`, `BatchSpanProcessor`, `OtlpGrpcSpanExporter`, explicit sampler, and graceful shutdown — and observe that issuing a `POST /orders` produces **two separate traces** in Tempo (one for the producer, one for the consumer). The visible "brokenness" of unpropagated traces IS the phase deliverable; it sets up the broken-then-fixed payoff in Phase 3.
**Depends on**: Phase 1
**Requirements**: TRACE-01, TRACE-02, TRACE-03, TRACE-04, TRACE-05, TRACE-06, TRACE-07, TRACE-08, DOC-03, DOC-05, WORK-01 (tag `step-02-traces`)
**Success Criteria** (what must be TRUE):
  1. Workshop attendee runs `POST /orders`, opens Grafana Tempo, and sees TWO distinct traces — both with correct `service.name` (`order-producer`, `order-consumer`, never `unknown_service:java`) and matching service-namespace/instance metadata.
  2. Workshop attendee presses Ctrl-C on either app and finds that the **last** batch of spans still appears in Tempo — proving graceful shutdown via `@Bean(destroyMethod = "close")` works (no telemetry lost).
  3. Workshop attendee opens `producer-service/.../OtelSdkConfiguration.java` and `consumer-service/.../OtelSdkConfiguration.java` and reads heavily-commented inline explanations of every SDK builder call — the code IS the workshop's textbook (DOC-03).
  4. Workshop attendee sees the producer trace contains a SERVER span (`POST /orders` with HTTP semconv attributes) wrapping an INTERNAL span (business logic), and the consumer trace contains a CONSUMER span wrapping an INTERNAL span — proving span-kind discipline before propagation is added.
  5. README explicitly states that the per-service duplication of `OtelSdkConfiguration` is **intentional** with a one-paragraph rationale (DOC-05) — no reader is tempted to "refactor" the duplication away.
  6. The annotated git tag `step-02-traces` exists on `main` and reproduces this exact two-traces state.
**Plans** (6 plans, 4 waves):
- **Wave 1** *(parallelizable, no dependencies)* — **✅ complete**
  - [x] `2-01-pom-dependencies` — TRACE-01 — Add 5 OTel deps to both service POMs (api/sdk/exporter-otlp BOM-managed; semconv 1.40.0 + semconv-incubating 1.40.0-alpha pinned) + invert `mise run verify:bom` to assert one-version-per-OTel-artifact
- **Wave 2** *(blocked on Wave 1 completion; parallelizable across services)* — **✅ complete**
  - [x] `2-02-producer-sdk-config` — TRACE-01..05, DOC-03 — Producer's `OtelSdkConfiguration.java` (heavily commented; ≥40 comment lines per DOC-03 grep gate) + `HttpServerSpanFilter.java` (D-05/D-06/D-07 producer-only, `OncePerRequestFilter.shouldNotFilter` for `/actuator/*`) + `Tracer @Bean`
  - [x] `2-03-consumer-sdk-config` — TRACE-01..04, DOC-03 — Consumer's `OtelSdkConfiguration.java` (mirror of producer's with documented diffs; NO HttpServerSpanFilter per D-07)
- **Wave 3** *(blocked on Wave 2 completion; parallelizable across services)* — **✅ complete**
  - [x] `2-04-producer-instrumentation` — TRACE-06, TRACE-07 — INTERNAL span on `OrderService.place` + PRODUCER span on `OrderPublisher.publish` (using `MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE` + `SEND` value per RESEARCH FLAG #1, NOT deprecated `MESSAGING_OPERATION="publish"`)
  - [x] `2-05-consumer-instrumentation` — TRACE-06, TRACE-08 — CONSUMER span on `OrderListener.onOrder` (with verbatim D-10 multi-line teaching comment + `.setParent(Context.root())`) + INTERNAL span on `ProcessingService.process`
- **Wave 4** *(blocked on Waves 1+2+3; contains human checkpoint)* — **source delta SHIPPED 2026-05-01; tag pending user gate**
  - [x] `2-06-readme-and-exit-gate` — DOC-03, DOC-05, WORK-01 — README "Reading the code" + "Why is OtelSdkConfiguration.java duplicated?" sections committed (`0f6c99e`); all 6 ROADMAP success criteria verified simultaneously green; annotated tag `step-02-traces` applied at `dac865f`

**Cross-cutting constraints** *(must_haves shared across plans)*:
- Maven dependency convergence enforced at `mvn validate` — every `io.opentelemetry*` artifact appears EXACTLY once across the reactor (asserted by 2-01, 2-02, 2-03)
- BOM ordering preserved from Phase 1: OTel BOM FIRST, OTel Instrumentation BOM SECOND (unused by Phase 2 but kept for Phase 5), Spring Boot BOM THIRD
- `OtelSdkConfiguration.java` is deliberately duplicated per service (D-01 / DOC-05) — refactoring it to a shared library is a hard FAIL (asserted by 2-02 + 2-03 + 2-06)
- Pure-inline span wrapping at every call site (D-01) — NO helper class, NO `Spans.run(...)`, NO AOP aspect, NO `@WithSpan` (asserted by 2-02 + 2-04 + 2-05)
- `@Bean(destroyMethod = "close")` on the `OpenTelemetrySdk` bean (D-15) — graceful shutdown flushes the final span batch via `OpenTelemetrySdk.close()` → `shutdown().join(10s)` cascade (asserted by 2-02 + 2-03 + 2-06 Criterion 2)
- `mise.toml` env vars (`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`, `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`) are READ by `System.getenv(...)` with `Optional.ofNullable(...).orElse(...)` defaults (D-12) — NO `opentelemetry-sdk-extension-autoconfigure` dependency
- semconv constants only — `io.opentelemetry.semconv:1.40.0` (stable) + `io.opentelemetry.semconv-incubating:1.40.0-alpha` (messaging.* + deployment.* live here per RESEARCH FLAG #2); legacy `io.opentelemetry:opentelemetry-semconv` from the SDK BOM is FORBIDDEN
**Notes**:
- TRACE-09 (`recordException` + `setStatus(ERROR)`) is **deferred to Phase 3** because the error-span lesson lands more naturally with APP-04's deterministic-10%-failure path, which itself sets up the consumer-side error-trace propagation story.
- A PRODUCER span (TRACE-07) is created in this phase but **without** header injection — propagation is intentionally absent so Phase 3's delta is visible.
- D-09 forward-compat: PRODUCER and CONSUMER inline spans in 2-04 / 2-05 are structured as clean rectangular blocks (no retry/error entanglement) so Phase 3 can cleanly DELETE them when installing the `otel-bootstrap` propagation pair. Phase 3's plan must explicitly delete these inline spans as part of installing `TracingMessagePostProcessor` + `TracingMessageListenerAdvice` — see CONTEXT.md `<deferred>` Phase 3 hand-off.

### Phase 3: AMQP Context Propagation — THE HEADLINE LESSON
**Goal**: Workshop attendee adds the `TracingMessagePostProcessor` (producer-side inject) + `TracingMessageListenerAdvice` (consumer-side extract) pair from the shared `otel-bootstrap` module, restarts both services, and watches the **same** `POST /orders` call now produce ONE distributed trace spanning both services with the consumer span's `parentSpanId` equal to the producer span's `spanId`. This is the workshop's most important phase — the broken-then-fixed delta from Phase 2 is the artifact's most powerful pedagogical moment.
**Depends on**: Phase 2 (requires the SDK and PRODUCER/CONSUMER spans from Phase 2; without that scaffolding, the inject/extract pair has nothing to inject into or extract from)
**Requirements**: PROP-01, PROP-02, PROP-03, PROP-04, APP-04, TRACE-09
**Success Criteria** (what must be TRUE):
  1. Workshop attendee issues `POST /orders` and opens any span in Tempo — the full trace shows producer + consumer spans sharing one `traceId`, with the consumer span's `parentSpanId` matching the producer span's `spanId` (visible in the span detail panel).
  2. Workshop attendee opens RabbitMQ Management UI (`:15672`), inspects an `orders.created` message, and sees a readable `traceparent` header value of the form `00-<32-hex-traceid>-<16-hex-spanid>-01` — **never** `[B@...` or a hex-blob byte-array signature (proves String-not-byte[] header type from PROP-01).
  3. Workshop attendee triggers the deterministic 10th order and sees Tempo render the trace as `Error` status with the exception event attached to the consumer span (proves APP-04 + TRACE-09 working together) — the error-status propagation across the AMQP hop is itself a teaching moment.
  4. Workshop attendee can read **one** producer-side class (`TracingMessagePostProcessor`) and **one** consumer-side class (`TracingMessageListenerAdvice`) in the shared `otel-bootstrap` Maven module and observe their structural symmetry — one inject method matched by one extract method IS the lesson; README explicitly contrasts this with the per-service-duplicated SDK bootstrap (PROP-04).
  5. The annotated git tag `step-03-context-propagation` exists on `main`; running `git diff step-02-traces..step-03-context-propagation` shows a small, readable changeset focused on the propagation pair (the broken-vs-fixed delta is reviewable in one diff).
**Plans** (5 plans, 4 waves):
- **Wave 1** *(parallelizable, no dependencies)*
  - [ ] `03-01-otel-bootstrap-amqp-classes` — PROP-01, PROP-02, PROP-04 — Populate otel-bootstrap with the 4 propagation classes (TracingMessagePostProcessor + TracingMessageListenerAdvice + MessagePropertiesSetter + MessagePropertiesGetter); add 3 BOM-managed deps (otel-api compile, spring-rabbit + spring-aop provided) + semconv-incubating + spring-boot-starter-test; add MessagePropertiesRoundTripTest unit test for PITFALLS.md #2 regression net
- **Wave 2** *(blocked on Wave 1; parallelizable across services)*
  - [ ] `03-02-producer-wiring` — PROP-01, PROP-04 — Add com.example:otel-bootstrap dep to producer-service/pom.xml; add @Bean TracingMessagePostProcessor + explicit @Bean RabbitTemplate that calls setBeforePublishPostProcessors(mpp) per D-05; DELETE Phase 2's inline PRODUCER span body from OrderPublisher.publish (lines 39-83) and remove Tracer ctor param
  - [ ] `03-03-consumer-wiring` — PROP-02, PROP-03, PROP-04 — Add com.example:otel-bootstrap dep to consumer-service/pom.xml; add @Bean TracingMessageListenerAdvice + Configurer-aided @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...) with setAdviceChain + setDefaultRequeueRejected(false) per D-08; DELETE Phase 2's inline CONSUMER span body from OrderListener.onOrder (lines 46-79) and remove Tracer ctor param per D-09
- **Wave 3** *(blocked on Wave 2 consumer plan; consumer-only)*
  - [ ] `03-04-app-04-failure-path` — APP-04, TRACE-09 — Create ProcessingFailedException (extends RuntimeException) per D-12; modify ProcessingService.process to add AtomicInteger counter + throw site (n%10==0) inside existing D-03 try-block per D-11. Phase 2 catch shape + Plan 03-03's advice catch automatically wire TRACE-09 on both INTERNAL and CONSUMER spans
- **Wave 4** *(blocked on Waves 1+2+3; contains human checkpoint)*
  - [ ] `03-05-readme-and-exit-gate` — PROP-03, PROP-04, APP-04, TRACE-09 — README delta: add "Why is the propagation pair shared?" PROP-04 callout (parallel to DOC-05); mark step-03-context-propagation as Current; remove obsolete "no traceparent yet" bullet. Human-verify gate confirms all 5 success criteria green at the live stack, then create annotated git tag step-03-context-propagation per WORK-01
**Notes**:
- This phase will need `/gsd-research-phase` before planning — the listener-side extraction mechanism (`MethodInterceptor` advice on `SimpleRabbitListenerContainerFactory` vs inline extract in each `@RabbitListener`) is flagged as an open research item in SUMMARY.md and must be resolved before implementation.
- APP-04 (deterministic 10th-order failure) lives here, not Phase 1 — the failure is wired in concert with TRACE-09's `recordException` so the *first* time attendees see business logic failing is also the first time they see the OTel error-span pattern.

### Phase 4: Metrics
**Goal**: Workshop attendee adds an `SdkMeterProvider` to each service's `OtelSdkConfiguration` (the SDK is already wired from Phase 2 — this phase just adds the meter pipeline + three instrument shapes) and watches `orders.created` (counter), `http.server.request.duration` (histogram), and `orders.queue.depth.estimate` (observable gauge) flow to Mimir on a 10-second interval — proving the second OTel signal arrives through the same OTLP endpoint as traces.
**Depends on**: Phase 2 (SDK exists), Phase 3 (so the metrics lesson is taught after the trace-correlation lesson lands; metrics depend on the SDK being wired but are presented after propagation for pedagogical sequencing)
**Requirements**: METRIC-01, METRIC-02, METRIC-03, METRIC-04
**Success Criteria** (what must be TRUE):
  1. Workshop attendee issues `POST /orders` and within 15 seconds sees `orders_created_total` increment in Grafana's Mimir-backed metric explorer with a non-empty `service_name="order-producer"` label and a business-attribute label like `order_priority`.
  2. Workshop attendee runs the demo for ~30 seconds and sees `http_server_request_duration_seconds` populated with `count`, `sum`, and bucket histograms — proving the histogram instrument shape and **seconds** unit (semconv-aligned).
  3. Workshop attendee sees the `orders_queue_depth_estimate` observable gauge produce a fresh sample every 10 seconds in Mimir (proving the `PeriodicMetricReader` interval was overridden from the 60-second default — see METRIC-01).
  4. The annotated git tag `step-04-metrics` exists on `main`.
**Plans** (5 plans, 3 waves):
- **Wave 1** *(parallelizable, no dependencies)*
  - [ ] `04-01-meter-pipeline-refactor` — METRIC-01 — Refactor BOTH OtelSdkConfiguration.java files: extract Phase 2's inline tracer pipeline into `private SdkTracerProvider buildTracerProvider(Resource)` (verbatim lift-and-shift) and add a sibling `private SdkMeterProvider buildMeterProvider(Resource)` that wires OtlpGrpcMetricExporter + PeriodicMetricReader.setInterval(Duration.ofSeconds(10)) + SdkMeterProvider; add Meter @Bean parallel to existing Tracer @Bean (D-06); update producer's HttpServerSpanFilter @Bean factory to take (Tracer, Meter); zero new pom deps (opentelemetry-exporter-otlp + sdk-metrics already on classpath since Phase 2)
- **Wave 2** *(blocked on Wave 1; parallelizable across the three instrument shapes)*
  - [ ] `04-02-producer-counter` — METRIC-02 — Extend OrderService: constructor-inject Meter; build LongCounter `orders.created` once as a final field; increment INSIDE the existing INTERNAL span body, AFTER `publisher.publish` returns and BEFORE `return orderId`, with `order.priority` business attribute from payload (fallback "standard", D-09); catch block UNCHANGED — counter does NOT fire on failure (D-08)
  - [ ] `04-03-producer-histogram` — METRIC-03 — EXTEND existing HttpServerSpanFilter (D-12 — no new filter, no chain reordering): constructor-inject Meter; build DoubleHistogram `http.server.request.duration` once with unit `"s"` (seconds — D-13 semconv-aligned); capture `startNanos = System.nanoTime()` BEFORE spanBuilder; record `(System.nanoTime() - startNanos) / 1_000_000_000.0` in existing finally block BEFORE span.end(); attributes use `HttpAttributes.HTTP_REQUEST_METHOD` + `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` semconv constants (D-14 — `url.path` excluded for cardinality); SDK-default buckets (D-15)
  - [ ] `04-04-consumer-gauge` — METRIC-04 — Create new @Component QueueDepthGauge at consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java (D-18b chosen over D-18a's inline @PostConstruct to preserve producer/consumer mirror property D-02); constructor-inject Meter; register ObservableLongGauge `orders.queue.depth.estimate` with `.ofLongs()` (D-19) and `buildWithCallback(measurement -> measurement.record(ThreadLocalRandom.current().nextInt(0, 50)))` (D-17 synthetic); @PreDestroy gauge.close() defensive cleanup
- **Wave 3** *(blocked on Waves 1+2; contains human checkpoint)*
  - [ ] `04-05-mise-readme-tag` — METRIC-01..04 — Update mise.toml `demo:order` task to send TWO priority payloads (express + standard, D-10 cardinality-awareness lesson); add `## Step 4: Metrics` README section keyed to tag step-04-metrics (D-21 — names three instrument shapes, seconds-not-millis trap callout, OTel→Prometheus name mapping); move **Current.** marker from step-03-context-propagation to step-04-metrics; update "What's NOT here yet" to remove metrics; verify all 4 ROADMAP success criteria simultaneously green at live stack; human-verify gate then create annotated tag step-04-metrics per WORK-01 / D-22

**Cross-cutting constraints** *(must_haves shared across plans)*:
- D-01 (refactor to helper methods — pedagogical north star: diff against step-03 reads as "we added a sibling pipeline")
- D-02 (per-service duplication preserved — Phase 2 D-01 / DOC-05 carryforward; producer/consumer OtelSdkConfiguration.java differs by ~3 lines only)
- D-04 (no autoconfigure dependency — Phase 2 D-12 carryforward; same env-var-with-fallback pattern as the trace exporter)
- D-05 (Resource built once in @Bean orchestrator and shared between tracer + meter pipelines for cross-signal correlation)
- D-07 (lifecycle: existing @Bean(destroyMethod="close") cascade handles SdkMeterProvider.shutdown() — no new lifecycle annotation)
- D-13 (seconds, not milliseconds — semconv 1.40.0 trap; explicit `/ 1_000_000_000.0`)
- D-14 (semconv constants for HTTP attrs; string literal AttributeKey for app-specific `order.priority`)
- D-20 (DOC-03 / comment-density bar ≥ 40 lines per OtelSdkConfiguration.java — Phase 4's helper additions naturally exceed this)
**Notes**:
- Standard OTel patterns; flagged in SUMMARY.md as **does not need** `/gsd-research-phase` — paste-able from STACK.md.
- Wave 2 (04-02 / 04-03 / 04-04) can run in parallel: each plan touches a different file (OrderService.java / HttpServerSpanFilter.java / QueueDepthGauge.java [new]) with zero file-overlap.
- D-18 hosting choice committed to D-18b (separate @Component) per PATTERNS.md recommendation — preserves producer/consumer OtelSdkConfiguration mirror.

### Phase 5: Logs Correlation
**Goal**: Workshop attendee adds the `SdkLoggerProvider` and `OpenTelemetryAppender` (with `install(openTelemetry)` invoked from `@PostConstruct` after the SDK bean is built — neutralising the silent-no-op pitfall) plus MDC `trace_id`/`span_id` injection, then runs a query in Grafana Loki, finds a log line, clicks the `trace_id` link, and lands directly on the matching trace in Tempo — closing the loop on all three signals.
**Depends on**: Phase 2 (SDK exists), Phase 3 (active spans must exist for `Span.current()` to give a non-default `trace_id`/`span_id` — without propagation the consumer-side log correlation would be misleadingly missing)
**Requirements**: LOG-01, LOG-02, LOG-03, LOG-04, LOG-05
**Success Criteria** (what must be TRUE):
  1. Workshop attendee tails the host console while running `POST /orders` and sees every business-logic log line stamped with `trace_id=<32 hex chars> span_id=<16 hex chars>` (proves LOG-04).
  2. Workshop attendee runs the Loki query `{service_name="order-producer"} |~ "<traceId>"` in Grafana, gets matching log lines, clicks the `trace_id` field on a line, and Grafana opens the matching trace in Tempo's Explore tab (proves LOG-05 end-to-end click-through).
  3. Workshop attendee can read `OtelSdkConfiguration.java` and see the `@PostConstruct`-annotated method calling `OpenTelemetryAppender.install(openTelemetry)` AFTER the SDK bean is fully built — the order of operations is explicit in the code, not magic (proves LOG-03 + addresses CRITICAL pitfall #5).
  4. The annotated git tag `step-05-logs` exists on `main`.
**Plans** (6 plans, 4 waves):
- **Wave 1** *(parallelizable, no dependencies)*
  - [ ] `05-01-pom-dependencies` — LOG-02 — Add `opentelemetry-logback-appender-1.0` + `opentelemetry-logback-mdc-1.0` to BOTH service POMs (BOM-managed by `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`, byte-identical mirror per D-02); `mvn validate` clean
- **Wave 2** *(blocked on Wave 1; parallelizable across services)*
  - [ ] `05-02-producer-sdk-and-logback` — LOG-01, LOG-02, LOG-03, LOG-04 — Producer's `OtelSdkConfiguration.java` extension (buildLoggerProvider helper + setLoggerProvider chain + @PostConstruct installLogbackAppender + JavaDoc fix for D-07) PLUS new `producer-service/src/main/resources/logback-spring.xml` with the corrected wrapper-appender shape per RESEARCH §1 (CONSOLE + MDC_CONSOLE wrapper + OTEL appender, NO TurboFilter)
  - [ ] `05-03-consumer-sdk-and-logback` — LOG-01, LOG-02, LOG-03, LOG-04 — Consumer mirror (same 6 EDITs to consumer's `OtelSdkConfiguration.java`, byte-identical `logback-spring.xml`)
- **Wave 3** *(blocked on Wave 2; parallelizable across services)*
  - [ ] `05-04-producer-business-logs` — LOG-04 — `OrderController.create(...)` LOG.info entry + `OrderPublisher.publish(...)` LOG.info pre-publish (D-15)
  - [ ] `05-05-consumer-error-log` — LOG-04 — `ProcessingService` LOG.error in catch block paired with existing span.recordException for triple-signal correlation on failure (D-16 option (a))
- **Wave 4** *(blocked on Waves 1+2+3; contains human checkpoint)*
  - [ ] `05-06-readme-and-tag` — LOG-05, WORK-01 — README "Step 5: Logs Correlation" section (D-20) + smoke verification of all 4 ROADMAP success criteria + human-verify gate; orchestrator applies annotated git tag `step-05-logs` post-gate (D-21)

**Cross-cutting constraints** *(must_haves shared across plans)*:
- D-01 (sibling-helper structure — diff against step-04 reads as "we added a third sibling pipeline"; carryforward of Phase 4 D-01)
- D-02 (per-service duplication preserved — Phase 2 D-01 / DOC-05 carryforward)
- D-04 (no autoconfigure dependency — Phase 2 D-12 / Phase 4 D-04 carryforward; same env-var-with-fallback pattern as the trace + metric exporters)
- D-05 (Resource built once and shared between tracer + meter + logger pipelines for cross-signal correlation)
- D-06 (lifecycle: existing @Bean(destroyMethod="close") cascade handles SdkLoggerProvider.shutdown() — no new lifecycle annotation)
- D-07 (no Logger @Bean — application code logs via SLF4J; the OpenTelemetryAppender bridges those events to OTLP)
- D-08 / D-09 (PITFALL #5 mitigation — `@PostConstruct` install with full inline comment block; load-bearing teaching surface)
- D-10 (logback-spring.xml is byte-identical between services; no Spring Boot defaults include)
- D-11 (locked console pattern: `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]`)
- D-13 (CORRECTED per RESEARCH §1: MDC injector is an APPENDER WRAPPER, NOT a TurboFilter — uses `mdc.v1_0.OpenTelemetryAppender` wrapping CONSOLE)
- D-19 (DOC-03 / comment-density bar ≥ 80 lines per OtelSdkConfiguration.java — Phase 5's helper + @PostConstruct + PITFALL #5 block naturally exceed this)

**Notes**:
- Phase 5 needed `/gsd-research-phase` before planning — research RESOLVED 2026-05-01: confirmed `opentelemetry-logback-mdc-1.0:2.27.0-alpha` Maven coordinate AND CORRECTED CONTEXT.md D-13 (the MDC injector is an appender wrapper, not a TurboFilter). All 21 D-decisions verified or corrected; all FQCNs verified against Maven Central POMs.
- Wave 2 (05-02 / 05-03) can run in parallel: each plan touches a different service's files with zero file-overlap; Wave 1 (pom.xml updates) MUST land first so the new artifacts are on classpath when Wave 2's Java code imports them.
- Wave 3 (05-04 / 05-05) can run in parallel: 05-04 touches producer-only files (OrderController, OrderPublisher); 05-05 touches consumer-only files (ProcessingService).
- Wave 4 (05-06) is sequential — README + smoke + tag application gate.

### Phase 6: Verification Tests
**Goal**: Workshop attendee adds Testcontainers-backed `@SpringBootTest`s using `RabbitMQContainer` + `@ServiceConnection` plus an `InMemorySpanExporter`-driven `@TestConfiguration` that proves the full instrumentation chain in CI: traceId shared across producer/consumer, parent/child relationship correct, span kinds correct, messaging semconv attributes present. Caps the workshop with "now you can prove your instrumentation works in CI without a live OTLP backend."
**Depends on**: Phase 1 (Maven scaffolding), Phase 2 (SDK config to swap), Phase 3 (the propagation pair the test asserts), Phase 4 (metrics module exists), Phase 5 (logs module exists) — i.e., **all preceding phases**
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, TEST-06
**Success Criteria** (what must be TRUE):
  1. Workshop attendee runs `mise run test` (with the host docker-compose RabbitMQ stopped) and **all tests still pass** — proving Testcontainers `RabbitMQContainer` + `@ServiceConnection` is genuinely used (not silently falling back to the host RabbitMQ on `:5672`).
  2. Workshop attendee inspects test logs and sees a non-default random RabbitMQ port (e.g., `localhost:54321` not `localhost:5672`) — visible proof of the random-port behaviour from TEST-01.
  3. The cross-service integration test asserts that producer and consumer spans share the same `traceId`, that consumer's `parentSpanId == producer.spanId`, and that both spans carry correct `SpanKind` (`PRODUCER` / `CONSUMER`) and messaging semconv attributes — and the test is deterministic (uses `SimpleSpanProcessor` + `forceFlush`, never `BatchSpanProcessor`).
  4. `mise run test` exits non-zero on any assertion failure — suitable for CI.
  5. The annotated git tag `step-06-tests` exists on `main`.
**Plans** (6 plans, 6 waves):
- **Wave 1** *(no dependencies)*
  - [ ] `06-01-parent-pom-and-classifier-config` — TEST-06 — Add `<module>integration-tests</module>` to parent reactor + `<classifier>exec</classifier>` repackage execution to producer/consumer service POMs (RESEARCH §3.1; D-04). Produces both plain classes jars (top-level ApplicationClass.class) and `-exec` fat jars per service.
- **Wave 2** *(blocked on Wave 1)*
  - [ ] `06-02-integration-tests-pom` — TEST-01, TEST-02, TEST-06 — Create `integration-tests/pom.xml` with 8 deps (producer/consumer plain jars + spring-boot-starter-test + spring-boot-testcontainers + testcontainers junit-jupiter + rabbitmq + opentelemetry-sdk-testing + awaitility) + EXPLICIT `maven-failsafe-plugin:3.5.5` binding (parent does NOT inherit from spring-boot-starter-parent — RESEARCH §2.5).
- **Wave 3** *(blocked on Wave 2)*
  - [ ] `06-03-test-otel-holder` — TEST-02 — Create `integration-tests/.../TestOtelHolder.java`: static-singleton with synchronized lazy-init (`OpenTelemetrySdk` + 3 InMemory* sinks); `OpenTelemetryAppender.install(sdk)` runs AFTER builder().build() and BEFORE SDK reference is published (Phase 5 commit f5c331a invariant — D-09). NO Batch processor; NO PeriodicMetricReader; `Sampler.alwaysOn()` (D-13/D-16/D-18).
- **Wave 4** *(blocked on Wave 3)*
  - [ ] `06-04-test-otel-configuration` — TEST-02 — Create `integration-tests/.../TestOtelConfiguration.java`: `@TestConfiguration` with 6 `@Bean` facades (`openTelemetry`/`tracer`/`meter` matching production names for override-by-name; 3 InMemory facades). Each delegates to `TestOtelHolder.get()` + static fields. NO `Logger` @Bean (Phase 5 D-07 carryforward); NO direct SDK construction; NO appender install (lives in TestOtelHolder per D-07.1).
- **Wave 5** *(blocked on Waves 3+4)*
  - [ ] `06-05-order-flow-it` — TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, TEST-06 — Create `integration-tests/.../OrderFlowIT.java`: `@Testcontainers` JUnit 5; `@Container static final RabbitMQContainer("rabbitmq:4.3-management-alpine")`; `@BeforeAll` emits explicit `LOG.info("RabbitMQ test container available at {}:{}")` (TEST-01 SC #2 — RESEARCH §2.2) + sets 4 `spring.rabbitmq.*` System properties + starts 2 `SpringApplicationBuilder` contexts (Producer + Consumer, both `@Import` TestOtelConfiguration). 4 `@Test` methods (D-14): traces, logs, metrics, failure-path triple-signal. Awaitility polling, NO Thread.sleep. Reads telemetry from `TestOtelHolder.SPANS / .LOGS / .METRICS` directly per D-07.1.
- **Wave 6** *(blocked on Wave 5; contains human checkpoint)*
  - [ ] `06-06-readme-and-tag` — TEST-06 + WORK-01 — Add README "## Step 6: Verification Tests" section (D-20) keyed to tag `step-06-tests` + smoke verification of all 5 ROADMAP success criteria + human-verify gate; orchestrator applies annotated git tag `step-06-tests` post-gate (D-21 — same pattern as Phases 2/3/4/5)
**Notes**:
- This phase will need `/gsd-research-phase` before planning — validate `@ServiceConnection` + `RabbitMQContainer` actually uses the test container (not the host RabbitMQ) on Spring Boot 3.4.13 per SUMMARY.md.

### Phase 7: Polish & Differentiators
**Goal**: Workshop attendee opens Grafana on a freshly-cloned laptop and sees a pre-built dashboard with all three signals on one panel; runs `scripts/load.sh` to generate continuously-flowing traffic for live demos; reads a step-by-step README that walks each annotated git tag (`step-01` through `step-06`) with copy-pasteable curl commands and screenshots that pair Phase 2's broken state with Phase 3's fixed state side-by-side. Finishes the workshop's polish layer so the artifact is delivery-ready.
**Depends on**: Phases 1-6 (every step must exist before the README can walk through them and screenshots can be captured; the dashboard JSON references metrics from Phase 4 and logs from Phase 5)
**Requirements**: WORK-02, WORK-03, DOC-04, DOC-01
**Success Criteria** (what must be TRUE):
  1. Workshop attendee runs `mise run infra:up`, opens Grafana, and sees the pre-provisioned (or one-click-importable) dashboard at a known path showing live traces, metrics, and logs for both services without any manual datasource configuration (WORK-02).
  2. Workshop attendee runs `scripts/load.sh` and observes a steady stream of `POST /orders` requests producing continuously-flowing telemetry — Tempo, Mimir, and Loki all show fresh data without manual curl-clicking (WORK-03).
  3. Workshop attendee reads `README.md` start-to-finish without running any code and understands the broken-vs-fixed propagation delta because Phase 2 ("step-02-traces") and Phase 3 ("step-03-context-propagation") screenshots are placed side-by-side with explanatory captions (DOC-04).
  4. Workshop attendee can `git checkout step-NN-NAME` for any step, follow the README section keyed to that tag with copy-pasteable curl commands, and reproduce the demonstrated state — every step has a paired README block (DOC-01).
**Plans** (6 plans, 6 waves):
- **Wave 1** *(no dependencies)*
  - [ ] `06-01-parent-pom-and-classifier-config` — TEST-06 — Add `<module>integration-tests</module>` to parent reactor + `<classifier>exec</classifier>` repackage execution to producer/consumer service POMs (RESEARCH §3.1; D-04). Produces both plain classes jars (top-level ApplicationClass.class) and `-exec` fat jars per service.
- **Wave 2** *(blocked on Wave 1)*
  - [ ] `06-02-integration-tests-pom` — TEST-01, TEST-02, TEST-06 — Create `integration-tests/pom.xml` with 8 deps (producer/consumer plain jars + spring-boot-starter-test + spring-boot-testcontainers + testcontainers junit-jupiter + rabbitmq + opentelemetry-sdk-testing + awaitility) + EXPLICIT `maven-failsafe-plugin:3.5.5` binding (parent does NOT inherit from spring-boot-starter-parent — RESEARCH §2.5).
- **Wave 3** *(blocked on Wave 2)*
  - [ ] `06-03-test-otel-holder` — TEST-02 — Create `integration-tests/.../TestOtelHolder.java`: static-singleton with synchronized lazy-init (`OpenTelemetrySdk` + 3 InMemory* sinks); `OpenTelemetryAppender.install(sdk)` runs AFTER builder().build() and BEFORE SDK reference is published (Phase 5 commit f5c331a invariant — D-09). NO Batch processor; NO PeriodicMetricReader; `Sampler.alwaysOn()` (D-13/D-16/D-18).
- **Wave 4** *(blocked on Wave 3)*
  - [ ] `06-04-test-otel-configuration` — TEST-02 — Create `integration-tests/.../TestOtelConfiguration.java`: `@TestConfiguration` with 6 `@Bean` facades (`openTelemetry`/`tracer`/`meter` matching production names for override-by-name; 3 InMemory facades). Each delegates to `TestOtelHolder.get()` + static fields. NO `Logger` @Bean (Phase 5 D-07 carryforward); NO direct SDK construction; NO appender install (lives in TestOtelHolder per D-07.1).
- **Wave 5** *(blocked on Waves 3+4)*
  - [ ] `06-05-order-flow-it` — TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, TEST-06 — Create `integration-tests/.../OrderFlowIT.java`: `@Testcontainers` JUnit 5; `@Container static final RabbitMQContainer("rabbitmq:4.3-management-alpine")`; `@BeforeAll` emits explicit `LOG.info("RabbitMQ test container available at {}:{}")` (TEST-01 SC #2 — RESEARCH §2.2) + sets 4 `spring.rabbitmq.*` System properties + starts 2 `SpringApplicationBuilder` contexts (Producer + Consumer, both `@Import` TestOtelConfiguration). 4 `@Test` methods (D-14): traces, logs, metrics, failure-path triple-signal. Awaitility polling, NO Thread.sleep. Reads telemetry from `TestOtelHolder.SPANS / .LOGS / .METRICS` directly per D-07.1.
- **Wave 6** *(blocked on Wave 5; contains human checkpoint)*
  - [ ] `06-06-readme-and-tag` — TEST-06 + WORK-01 — Add README "## Step 6: Verification Tests" section (D-20) keyed to tag `step-06-tests` + smoke verification of all 5 ROADMAP success criteria + human-verify gate; orchestrator applies annotated git tag `step-06-tests` post-gate (D-21 — same pattern as Phases 2/3/4/5)
**Notes**:
- All four requirements are differentiators flagged in SUMMARY.md as standard patterns — does not need `/gsd-research-phase`, can plan directly.
- Per user decision earlier in initialization, this phase is locked into v1 (rather than deferred post-cohort) so the artifact is delivery-ready on first ship.

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Baseline & Scaffold | 6/6 | Shipped (tag step-01-baseline) | 2026-04-29 |
| 2. Manual SDK Bootstrap & First Traces | 6/6 | Shipped | 2026-05-01 |
| 3. AMQP Context Propagation | 0/5 | In progress (planned) | - |
| 4. Metrics | 0/5 | In progress (planned) | - |
| 5. Logs Correlation | 6/6 | Complete   | 2026-05-02 |
| 6. Verification Tests | 0/6 | Planned | - |
| 7. Polish & Differentiators | 0/TBD | Not started | - |

## Research Flags

Per SUMMARY.md "Research Flags" section, the following phases need `/gsd-research-phase` before `/gsd-plan-phase`:

- **Phase 1**: Verify exact ordering of BOM imports (OTel **before** Spring Boot in Maven `<dependencyManagement>`); verify `mise` plugin ID for Corretto 17 (could be `corretto-17` or a precise patch).
- **Phase 3**: Verify `MethodInterceptor` advice on `SimpleRabbitListenerContainerFactory` composes correctly with Spring AMQP 3.2.8's listener lifecycle, vs. inline extraction inside the listener method body. Resolve before implementation.
- ~~**Phase 5**~~: RESOLVED 2026-05-01 — `opentelemetry-logback-mdc-1.0:2.27.0-alpha` is a separate BOM-managed artifact AND CORRECTED CONTEXT.md D-13: the MDC injector is an appender wrapper (extends `UnsynchronizedAppenderBase`), NOT a TurboFilter. See `.planning/phases/05-logs-correlation/05-RESEARCH.md` Finding #1.
- **Phase 6**: Validate `@ServiceConnection` + `RabbitMQContainer` actually uses the test container (not the host RabbitMQ) on Spring Boot 3.4.13.

Phases that can plan directly (no research-phase needed):

- **Phase 2**: Manual `OpenTelemetrySdk.builder()` is paste-able from STACK.md and ARCHITECTURE.md Pattern 1.
- **Phase 4**: Three instrument shapes are textbook OTel; semconv naming is fixed.
- **Phase 7**: All differentiators are small, independent, and well-described in FEATURES.md.

---

*Roadmap created: 2026-04-29 by gsd-roadmapper*
*Granularity: coarse (config.json) — 7 phases retained because they mirror immutable workshop git tags `step-01-` through `step-06-` plus the Phase 7 polish state explicitly chosen by the user; collapsing them would destroy load-bearing pedagogical sequencing.*
