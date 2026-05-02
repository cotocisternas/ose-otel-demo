# Project Research Summary

**Project:** OSE OTel Demo
**Domain:** OpenTelemetry manual-SDK instrumentation workshop (Spring Boot 3.4.13 + Java 17 + Spring AMQP, Grafana otel-lgtm backend)
**Researched:** 2026-04-29
**Confidence:** HIGH

## Executive Summary

This is a **workshop-grade teaching artifact**, not a product — the success metric is "an attendee can `git checkout` each step and read the SDK code that makes a single distributed trace flow from `POST /orders` through a Spring AMQP publish, into a `@RabbitListener` consumer, with correlated metrics and logs." The four research dimensions converged independently on the same conclusion: the workshop's center of gravity is the **W3C trace-context propagation across the AMQP boundary** — a real-world gap that even the OpenTelemetry Java agent does not auto-instrument for Spring AMQP, making this exercise directly transferable to the audience's production work.

The recommended approach is a **three-module Maven build** (`otel-bootstrap` library + `producer-service` + `consumer-service`) pinned to a coherent triple via BOMs (Spring Boot 3.4.13 + OpenTelemetry SDK 1.61.0 + Instrumentation 2.27.0-alpha). Apps run on the host via `mise` (Corretto 17 + Maven 3.9) so attendees can attach a debugger and step through SDK calls; only RabbitMQ 4.3 and `grafana/otel-lgtm:0.26.0` run in `docker compose`. The headline pedagogical mechanism is **staged git tags** (`step-01-baseline` → `step-02-traces` → `step-03-context-propagation` → `step-04-metrics` → `step-05-logs` → `step-06-tests`) including a deliberate "broken-then-fixed" demonstration at the step-02 / step-03 boundary where producer and consumer spans live in *separate* traces until the inject/extract pair joins them.

The largest risks are **silent failure modes** that would make the workshop "lie" about how OTel works: AMQP propagation breaking because of `byte[]` vs `String` header type mismatch, `service.name` defaulting to `unknown_service:java` and merging both services in Grafana, `BatchSpanProcessor` dropping the last batch on JVM shutdown, and the `OpenTelemetryAppender` initialising before the SDK is registered (silently dropping logs). All four are addressable with one-line fixes that the workshop should *teach* rather than hide. Secondary risks are tooling friction (port 3000 collisions on developer laptops, IntelliJ not detecting `mise`-managed JDKs) addressable with a pre-flight task and a committed `.tool-versions` file.

## Key Findings

### Recommended Stack

Pin **three coherent BOMs** and let them manage every transitive version: Spring Boot 3.4.13 (the user-pinned final 3.4.x patch), OpenTelemetry SDK 1.61.0, and OpenTelemetry Instrumentation 2.27.0-alpha (the alpha BOM is required because the Logback appender only ships there). Run apps on the host via `mise` to preserve debugger workflows; run only infrastructure in `docker compose`. Use the **new** `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` coordinate (the legacy `io.opentelemetry:opentelemetry-semconv` is deprecated). Test with JUnit 5, Testcontainers RabbitMQ module, and `opentelemetry-sdk-testing`'s `InMemorySpanExporter` driven by a `SimpleSpanProcessor` (never `Batch` in tests).

**Core technologies:**

- **Spring Boot 3.4.13** + **Java 17 (Amazon Corretto)** + **Maven 3.9** — user-pinned; aligns with audience's production stack
- **OpenTelemetry Java SDK 1.61.0** (`opentelemetry-bom`) — manual SDK only; **no** Java agent, **no** Micrometer bridge, **no** Spring Boot OTel starter (all explicitly out of scope)
- **OpenTelemetry Instrumentation 2.27.0-alpha** (`opentelemetry-instrumentation-bom-alpha`) — used **only** for the Logback appender; everything else is hand-written
- **Spring AMQP 3.2.8** (managed by Spring Boot BOM) + **RabbitMQ 4.3-management** — the propagation-gap teaching surface
- **Grafana otel-lgtm 0.26.0** (single container) — embedded Collector + Tempo + Mimir + Loki + Grafana on one OTLP gRPC endpoint (`:4317`)
- **mise** (Corretto 17 + Maven 3.9) for host JDK pinning; **docker compose** for infra only
- **JUnit 5 + Testcontainers `rabbitmq` 1.20.x + `@ServiceConnection`** for integration tests with real broker

See `STACK.md` for full BOM imports, `pom.xml` snippets, and `mise.toml` / `docker-compose.yml` shapes.

### Expected Features

The "features" here are **workshop steps and code-level demonstrations**, not application functionality. The yardstick is pedagogical: does this feature help an attendee understand the OTel SDK by reading code?

**Must have (table stakes — workshop fails without these):**

- Manual `OpenTelemetrySdk.builder()` bootstrap with explicit `Resource`, `SdkTracerProvider`, `SdkMeterProvider`, `SdkLoggerProvider`, and W3C propagators (TS-1)
- Resource attributes: `service.name`, `service.version`, `service.instance.id`, `deployment.environment.name` (TS-2)
- OTLP gRPC exporters for **all three** signals to a single `:4317` endpoint (TS-3)
- Production-shaped processors: `BatchSpanProcessor` + `PeriodicMetricReader` + `BatchLogRecordProcessor` (TS-4)
- Explicit `Sampler.parentBased(Sampler.alwaysOn())` with a comment about production tradeoffs (TS-5)
- Graceful SDK shutdown via `@Bean(destroyMethod = "close")` (TS-6) — without this, demos lose the last batch
- Manual SERVER span on `POST /orders` (TS-7), PRODUCER span around publish with `TextMapSetter` injecting context into `MessageProperties` (TS-8), CONSUMER span inside `@RabbitListener` with `TextMapGetter` extracting context (TS-9), nested INTERNAL span in business logic (TS-10)
- Messaging + HTTP semantic-convention attributes (TS-11, TS-12)
- Three metric instrument shapes: `Counter`, `Histogram`, `ObservableGauge` (TS-13)
- `OpenTelemetryAppender` for OTLP log export + MDC `trace_id`/`span_id` injection (TS-14)
- `docker-compose.yml` with `otel-lgtm` + RabbitMQ-management (TS-15, TS-16); `mise.toml` with `dev`/`test`/`infra:up`/`infra:down` tasks (TS-17)
- Step-by-step `README.md` (TS-18) keyed to staged git tags `step-01` through `step-06` (TS-19)
- Testcontainers integration test asserting parent/child span relationship via `InMemorySpanExporter` (TS-20)
- **Broken-then-fixed propagation demonstration**: step-02 produces *valid spans on both sides but in separate traces*; step-03 adds the inject/extract pair joining them (TS-21) — the workshop's most powerful teaching moment

**Should have (differentiators, add post-validation):**

- Pre-built Grafana dashboard JSON (DF-1) — saves "click around in Grafana" time
- Load generator script (`scripts/load.sh`) for steady traffic during live demos (DF-2)
- README screenshots from Grafana (DF-3) — especially the broken-vs-fixed pair
- Custom domain attributes + `Span.addEvent(...)` example (DF-5)
- `span.recordException(e); span.setStatus(ERROR)` pattern with deterministic 10% failure (DF-6)
- Heavy `OpenTelemetryConfig.java` comments — the code is the textbook (DF-10) — *should be a v1 quality bar even though structurally a "differentiator"*

**Defer (v2+):**

- Facilitator notes (DF-4) — only when someone other than the author runs it
- Sampling-variant checkpoint (DF-7), baggage propagation (DF-8) — separate follow-up workshop
- Dead-letter / retry simulation (DF-11) — would require re-evaluating the "minimal AMQP topology" decision

**Anti-features (deliberately excluded — see `FEATURES.md` AF-1 through AF-20):** OTel Java Agent, Spring Boot OTel starter, Micrometer-tracing bridge, `@WithSpan` annotations, shared library hiding the SDK setup, richer AMQP topologies (fanout/topic/RPC), GraalVM native-image, cloud observability backends, web UI, Spring Security, JPA/database, JMH benchmarks, Pyroscope.

### Architecture Approach

A **three-module Maven build** (`otel-bootstrap` shared library + `producer-service` + `consumer-service`), parent POM importing the OTel + Spring Boot + Testcontainers BOMs. Apps run on the host JVM (Corretto 17 via `mise`) so attendees can attach a debugger; only RabbitMQ and `grafana/otel-lgtm` run in `docker compose`. Trace context crosses the AMQP boundary via a `TracingMessagePostProcessor` (producer-side, registered on `RabbitTemplate.setBeforePublishPostProcessors(...)`) and a `TracingMessageListenerAdvice` (consumer-side, registered as `MethodInterceptor` on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)`). All telemetry exits via OTLP/gRPC `:4317` to the single-container LGTM stack. Workshop checkpoints are **annotated git tags on `main`** — immutable, no merge conflicts, frozen after first delivery.

> **Open architecture question:** ARCHITECTURE.md treats `otel-bootstrap` as a shared `@AutoConfiguration` library, while FEATURES.md AF-15 explicitly argues *against* a shared library — "duplicate the SDK setup so attendees can read it in both services." The synthesis recommendation: **keep the shared library** for the AMQP `TracingMessagePostProcessor` / `TracingMessageListenerAdvice` pair (they are symmetric — the lesson is that one piece of code handles both sides) but **inline the SDK bootstrap (`OtelSdkConfiguration`) in each service** so attendees see it in both places. The README must call this out: "this duplication is on purpose."

**Major components:**

1. **`otel-bootstrap` library** — symmetric AMQP propagation pair (`TracingMessagePostProcessor`, `TracingMessageListenerAdvice`, `MessagePropertiesSetter`, `MessagePropertiesGetter`)
2. **`producer-service`** — Spring Boot app: `@RestController` for `POST /orders`, `OrderService`, `OrderPublisher` using `RabbitTemplate` with the post-processor; per-service `OtelSdkConfiguration`
3. **`consumer-service`** — Spring Boot app: `@RabbitListener` consuming `orders.created`, `ProcessingService` simulating downstream work; same SDK wiring
4. **Infrastructure (docker-compose)** — `rabbitmq:4.3-management` (AMQP `:5672`, UI `:15672`) + `grafana/otel-lgtm:0.26.0` (Grafana `:3000`, OTLP gRPC `:4317`, OTLP HTTP `:4318`)
5. **Workshop checkpoints** — annotated git tags `step-01-baseline` through `step-06-tests` on `main`

### Critical Pitfalls

The five pitfalls below would each silently make the workshop "lie" about how OTel works. Every roadmap phase must explicitly verify the corresponding fix.

1. **AMQP propagation silently broken** (CRITICAL — the headline lesson) — Spring AMQP does **not** propagate W3C trace context by default; the OTel Java agent does **not** auto-instrument Spring AMQP. *Avoid by:* registering `TracingMessagePostProcessor` on `RabbitTemplate.setBeforePublishPostProcessors(...)` and `TracingMessageListenerAdvice` as advice on `SimpleRabbitListenerContainerFactory`; verify visually in Tempo AND assert `traceId(producer) == traceId(consumer)` in a Testcontainers test.
2. **`byte[]` vs `String` AMQP header values** (CRITICAL) — Setting `traceparent` as `byte[]` causes consumer-side `getHeader()` to return `LongStringHelper.ByteArrayLongString`, corrupting extraction. *Avoid by:* setter writes `String`; getter normalizes via `.toString()` defensively. Round-trip unit test the setter/getter pair without a container. Workshop bonus: deliberately show the wrong code first.
3. **`service.name` defaults to `unknown_service:java`** (CRITICAL — makes Grafana unusable) — *Avoid by:* every service builds `Resource.getDefault().merge(Resource.create(...))` with `service.name`, `service.namespace`, `service.instance.id`, `deployment.environment.name` using the new `io.opentelemetry.semconv` constants. Address in `step-02-traces` (the FIRST step that registers the SDK).
4. **`BatchSpanProcessor` drops telemetry on JVM shutdown** (CRITICAL for tests, HIGH for demos) — *Avoid by:* register `OpenTelemetrySdk` as `@Bean(destroyMethod = "close")`; in tests use `SimpleSpanProcessor` + `forceFlush().join(...)` before assertions, never `BatchSpanProcessor` in tests.
5. **`OpenTelemetryAppender` initialised before SDK is ready** (CRITICAL for the logs lesson) — Logback initialises early in Spring Boot startup; the appender holds `OpenTelemetry.noop()` until `install(...)` is called. *Avoid by:* call `OpenTelemetryAppender.install(openTelemetry)` in `@PostConstruct` *after* the SDK bean is fully built.

Additional HIGH-severity items in `PITFALLS.md`: sampler defaults teaching anti-pattern (#6), `@RabbitListener` thread context loss (#7), OTLP port 4317-vs-4318 confusion (#8), and OTel BOM transitive version drift causing `NoSuchMethodError` (#9).

## Implications for Roadmap

The four research dimensions independently converged on the **same six-step workshop progression**. Treat that consensus as locked in. The only soft tension is whether the initial Maven/mise/docker-compose scaffolding is its own phase (`step-00-bootstrap`) or merged into `step-01-baseline`. Given `granularity:coarse` and audience-facing tag naming, the recommendation is to **merge bootstrap into step-01-baseline as the first phase, but explicitly call out the BOM-ordering pitfall (#9) and port-collision pitfall (#14) as gates inside that phase**.

### Phase 1: Baseline & Scaffold (`step-01-baseline`)

**Rationale:** Establishes the working app the attendee will instrument. Telemetry is *intentionally* absent. Catches all foundation-layer pitfalls (BOM ordering, port collisions, mise-IDE coupling) before any OTel surface is exposed.
**Delivers:** Parent POM with three modules + BOM imports (OTel BOM **before** Spring Boot BOM in Maven order); `producer-service` + `consumer-service` skeletons; `docker-compose.yml` (RabbitMQ 4.3-management + grafana/otel-lgtm:0.26.0 pinned); `mise.toml` (Corretto 17 + Maven 3.9 + tasks `dev`, `test`, `infra:up`, `infra:down`, `preflight`, `demo:order`); committed `.tool-versions`; README skeleton + Prerequisites section.
**Addresses (FEATURES.md):** TS-15, TS-16, TS-17, TS-18 skeleton, TS-19 first tag.
**Avoids (PITFALLS.md):** #9 BOM drift, #14 port 3000 collisions, #15 mise+IntelliJ JDK detection.
**Done when:** `mise run dev` + `mise run demo:order` works end-to-end with no telemetry; `mvn dependency:tree -Dincludes=io.opentelemetry` shows ONE version per artifact; tag `step-01-baseline` annotated.

### Phase 2: Manual SDK Bootstrap & First Traces (`step-02-traces`)

**Rationale:** Smallest possible OTel surface — the SDK is wired and emitting traces, but **producer and consumer traces are intentionally separate** because no AMQP propagation exists yet. Sets up the "broken-then-fixed" payoff in Phase 3 (TS-21). Reversing this order destroys the workshop's most powerful pedagogical mechanism.
**Delivers:** `OtelSdkConfiguration` per service, building `OpenTelemetrySdk` with explicit `Resource`, `SdkTracerProvider`, `BatchSpanProcessor`, `OtlpGrpcSpanExporter` to `:4317`, explicit `Sampler.parentBased(alwaysOn())`, `W3CTraceContextPropagator` + `W3CBaggagePropagator` composite, `@Bean(destroyMethod = "close")`. Manual SERVER span on `POST /orders` with HTTP semconv; INTERNAL spans in `OrderService.place(...)` and `ProcessingService.process(...)`.
**Uses (STACK.md):** `opentelemetry-api`, `-sdk`, `-exporter-otlp`, `-sdk-extension-autoconfigure`; `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0`.
**Implements (ARCHITECTURE.md):** Pattern 1 (SDK bootstrap), Pattern 3 (tag immutability).
**Avoids (PITFALLS.md):** #3 `unknown_service:java`, #4 telemetry lost on shutdown, #6 sampler defaults, #8 OTLP port confusion.
**Done when:** Tempo shows TWO separate traces (producer + consumer) for one `POST /orders` — both with correct service names, both flushed completely on `Ctrl-C`. **The visible "brokenness" is the lesson; do not fix it in this phase.**

### Phase 3: AMQP Context Propagation (`step-03-context-propagation`) — THE HEADLINE LESSON

**Rationale:** This is the single most important phase. The workshop exists to teach this. The "broken-then-fixed" delta from Phase 2 is the most powerful pedagogical moment in the entire artifact (TS-21).
**Delivers:** `TracingMessagePostProcessor` (producer-side, registered via `RabbitTemplate.setBeforePublishPostProcessors(...)`) creating PRODUCER span and injecting `traceparent`/`tracestate` into `MessageProperties` headers via `TextMapSetter<MessageProperties>`; `TracingMessageListenerAdvice` (consumer-side, `MethodInterceptor` on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)`) extracting context, calling `Context.makeCurrent()`, creating CONSUMER span with extracted parent. Messaging semconv attributes both sides. README "broken vs fixed" comparison with Grafana screenshots side-by-side.
**Uses (STACK.md):** `opentelemetry-api` (`TextMapSetter`/`TextMapGetter`, `ContextPropagators`); `spring-rabbit` (`MessagePostProcessor`).
**Implements (ARCHITECTURE.md):** Pattern 2 (AMQP propagation via TextMap*).
**Avoids (PITFALLS.md):** #1 AMQP propagation broken (THE pitfall this phase exists to defeat), #2 byte[] vs String headers, #7 listener thread context loss, #12 Jackson converter quirks.
**Done when:** Tempo shows ONE trace with 5 spans across both services sharing the same `traceId`; consumer span has `parentSpanId` pointing at producer span; `SpanKind.PRODUCER` / `SpanKind.CONSUMER` correct; RabbitMQ Management UI shows readable `traceparent` header (NOT `[B@...`).

### Phase 4: Metrics (`step-04-metrics`)

**Rationale:** SDK already wired in Phase 2 — just adds a `Meter` and three instrument shapes. Delivered after propagation so the trace-correlation lesson lands first.
**Delivers:** `SdkMeterProvider` with `PeriodicMetricReader` (interval **overridden to 10s** for workshop responsiveness, not the 60s default) + `OtlpGrpcMetricExporter`. Three instrument shapes: `LongCounter` (`orders.created`), `DoubleHistogram` (`http.server.request.duration` in **seconds**, semconv-aligned), `ObservableGauge` (`orders.queue.depth.estimate`).
**Uses (STACK.md):** `opentelemetry-sdk` (`SdkMeterProvider`, `PeriodicMetricReader`), `opentelemetry-exporter-otlp` (`OtlpGrpcMetricExporter`).
**Done when:** Mimir has `http_server_request_duration_seconds_count` with non-empty `service_name` label; counter increments visible within 15s of a `demo:order`.

### Phase 5: Logs Correlation (`step-05-logs`)

**Rationale:** Last signal; closes the loop showing all three sources correlate in Grafana. Depends on active spans existing (Phases 2/3) — the appender injects `trace_id`/`span_id` from `Span.current()`.
**Delivers:** `SdkLoggerProvider` with `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter`. `logback-spring.xml` per service with `OpenTelemetryAppender` from `opentelemetry-logback-appender-1.0` + MDC `trace_id`/`span_id` injection. `OpenTelemetryAppender.install(openTelemetry)` called explicitly in `@PostConstruct` after SDK bean is built. Console pattern includes `trace_id=%X{trace_id} span_id=%X{span_id}`.
**Uses (STACK.md):** `opentelemetry-logback-appender-1.0` (the only artifact pulled from the alpha BOM).
**Avoids (PITFALLS.md):** #5 OpenTelemetryAppender not installed.
**Done when:** Loki query `{service_name="order-producer"} |~ "<traceId>"` returns log lines; clicking a log line in Grafana opens the matching trace.

### Phase 6: Verification Tests (`step-06-tests`)

**Rationale:** Caps the workshop with "now you can prove your instrumentation works in CI." Depends on Phases 1–5 because the test asserts the full chain.
**Delivers:** Per-service `@SpringBootTest` + Testcontainers `RabbitMQContainer` + `@ServiceConnection`. `@TestConfiguration` swapping OTLP exporter for `InMemorySpanExporter` + `SimpleSpanProcessor` (NOT `BatchSpanProcessor`); `OpenTelemetryExtension` from `opentelemetry-sdk-testing`. Optional `integration-tests` module for cross-service e2e. Assertions: spans share `traceId`; consumer's `parentSpanId == producer.spanId`; `SpanKind` correct; messaging semconv present.
**Uses (STACK.md):** `org.testcontainers:rabbitmq` + `:junit-jupiter`, `spring-boot-testcontainers`, `opentelemetry-sdk-testing`.
**Avoids (PITFALLS.md):** #4 Batch processor in tests, #11 Batch/Simple flakiness, #13 `@ServiceConnection` requires typed `RabbitMQContainer`.
**Done when:** `mise run test` passes; test logs show a non-default random RabbitMQ port; tests pass even when host RabbitMQ in docker-compose is stopped.

### Phase 7 (Optional): Polish & Differentiators

**Rationale:** Add **only** after first-cohort feedback validates v1.
**Delivers (pick from FEATURES.md based on cohort feedback):** DF-1 dashboard JSON; DF-2 load generator; DF-3 README screenshots (especially broken-vs-fixed); DF-5 custom domain attributes + `Span.addEvent`; DF-6 span exception recording; DF-10 heavy comments on `OtelSdkConfiguration`.

### Phase Ordering Rationale

- **Step-01 first** because BOM ordering and port collisions are foundation pitfalls that taint every later step.
- **Step-02 (intentionally without propagation) before step-03** because the broken state is the setup for the "aha moment" (TS-21). Reversing this order destroys the workshop's most powerful teaching mechanism.
- **Step-03 immediately after step-02** because the broken-then-fixed delta is small and the lesson is volatile in attendee memory.
- **Step-04 (metrics) before step-05 (logs)** because logs correlation depends on active spans, and metrics demonstrate the SDK already wired in step-02 supports more than just traces.
- **Step-06 (tests) last** because the integration test asserts the full chain.
- **Polish phase last and conditional** — the workshop must run cleanly with attendees first.

### Research Flags

Phases likely needing deeper research during planning (`/gsd-research-phase`):

- **Phase 1:** Verify exact ordering of BOM imports (OTel **before** Spring Boot in Maven `<dependencyManagement>`); verify `mise` plugin ID for Corretto 17 (could be `corretto-17` or a precise patch).
- **Phase 3:** Verify `MethodInterceptor` advice on `SimpleRabbitListenerContainerFactory` composes correctly with Spring AMQP 3.2.8's listener lifecycle, vs. inline extraction inside the listener method body. Resolve the architecture-vs-features tension on this hook before implementation.
- **Phase 5:** Confirm exact Maven coordinate / version for the MDC injector (`opentelemetry-logback-mdc-1.0` separate artifact vs. `<captureMdcAttributes>` on the appender itself).
- **Phase 6:** Validate `@ServiceConnection` + `RabbitMQContainer` actually uses the test container (not the host RabbitMQ) on Spring Boot 3.4.13.

Phases with standard patterns (skip research-phase, just plan):

- **Phase 2:** Manual `OpenTelemetrySdk.builder()` is paste-able from STACK.md and ARCHITECTURE.md Pattern 1.
- **Phase 4:** Three instrument shapes are textbook OTel; semconv naming is fixed.
- **Phase 7:** All differentiators are small, independent, and well-described in FEATURES.md.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions cross-checked against Maven Central, GitHub Releases, official Spring/Grafana sources within 30 days. MEDIUM only on exact `mise` Corretto patch ID and RabbitMQ tag stability across patch updates. |
| Features | HIGH | Each TS-N grounded in `PROJECT.md` Active Requirements + OTel SDK documented surface. Anti-features either explicitly out-of-scope or follow from pedagogical goal. MEDIUM on exact ordering of git checkpoints (independently validated by all four dimensions). |
| Architecture | HIGH (MEDIUM on workshop sequencing) | Stack and propagation patterns verified against OTel/Spring docs and lgtm container repo. Workshop-step sequencing is opinionated pedagogical ordering — see open question on shared `OtelSdkConfiguration` vs duplicated. |
| Pitfalls | HIGH | Verified against OTel docs, opentelemetry-java-instrumentation issues (#10307, #2822, #1731, #3358), spring-amqp issues (#1306, #2560), spring-boot #43200, docker-otel-lgtm #461. |

**Overall confidence:** HIGH

### Gaps to Address

1. **Shared library boundary for SDK bootstrap (architecture vs feature tension):** ARCHITECTURE.md proposes shared `otel-bootstrap` library auto-configuring `OpenTelemetrySdk`; FEATURES.md AF-15 argues *against* a shared library. **Resolution recommendation:** keep the shared library for the AMQP propagation pair (the symmetry IS the lesson) but **inline `OtelSdkConfiguration` in each service**. README must explicitly call out the deliberate duplication. **Decide before Phase 2 begins.**
2. **`step-00-bootstrap` vs merged-into-`step-01-baseline`:** PITFALLS.md treats bootstrap as load-bearing; ARCHITECTURE.md / FEATURES.md merge them. **Resolution:** merge into `step-01-baseline` but enumerate bootstrap-layer pitfalls (#9, #14, #15) as gates inside that phase's "done when" criteria.
3. **Listener-side context extraction mechanism:** (a) `MethodInterceptor` advice chain on `SimpleRabbitListenerContainerFactory` (ARCHITECTURE.md) vs (b) inline extract at the top of each `@RabbitListener` method (FEATURES.md TS-9). **Resolution:** flag for `/gsd-research-phase` during Phase 3 planning.
4. **`grafana/otel-lgtm` image tag:** STACK.md `0.26.0` (latest April 2026); ARCHITECTURE.md example `0.11.4`. **Resolution:** pin `0.26.0`, freeze in `docker-compose.yml`.
5. **RabbitMQ image version:** STACK.md `4.3-management` (4.x current); ARCHITECTURE.md `3.13-management-alpine`. **Resolution:** pin `rabbitmq:4.3-management`. Verify Testcontainers `RabbitMQContainer.withImageName(...)` override in tests.
6. **`PeriodicMetricReader` interval:** Default 60s is too slow for workshop responsiveness. **Resolution:** override to 10s in `OtelSdkConfiguration`; document the override and the 60s production default in a code comment.

## Sources

### Primary (HIGH confidence)

- `STACK.md` — BOM coordinates and rationale; Spring blog, Maven Central, Context7 `/open-telemetry/opentelemetry-java`, GitHub Releases APIs.
- `FEATURES.md` — TS/DF/AF inventory; OTel official docs (sdk, api, semconv messaging spans + metrics), opentelemetry-java-instrumentation Logback appender README, Spring AMQP `MessagePostProcessor` API.
- `ARCHITECTURE.md` — three-module Maven structure, code samples for SDK bootstrap and AMQP propagation pair; opentracing-contrib/java-spring-rabbitmq prior art.
- `PITFALLS.md` — 16 pitfalls with severity, prevention, recovery; specific GitHub issue references (otel-java-instrumentation #10307/#8942/#2822/#3358; spring-amqp #1306/#1731/#2560; spring-boot #43200; docker-otel-lgtm #461).

### Secondary (MEDIUM confidence)

- Practitioner write-ups: Uptrace OpenTelemetry Java guides (sampling, propagation, logback), Aspecto distributed tracing for RabbitMQ, Eric Schabell hands-on guide, OneUptime instrumentation posts.
- Comparable workshop repos (sanity check): Grafana opentelemetry-workshop, NovatecConsulting opentelemetry-training, Honeycomb collector-workshop, opentelemetry-java-examples, Linux Foundation LFS148.

### Tertiary (LOW confidence — flagged for validation during planning)

- Exact `mise` plugin patch ID for Corretto 17 — verify with `mise ls-remote java | grep corretto-17` at Phase 1 implementation time.
- RabbitMQ `4.3-management` tag stability across patch updates — pin to `4.3.0-management` for absolute reproducibility if drift observed.

---
*Research completed: 2026-04-29*
*Ready for roadmap: yes*
