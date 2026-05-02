# Phase 6: Verification Tests - Context

**Gathered:** 2026-05-02
**Status:** Ready for research → planning (`/gsd-research-phase 6` resolves the open `@ServiceConnection`-vs-manual-property research flag, then `/gsd-plan-phase 6`)

<domain>
## Phase Boundary

Phase 6 caps the workshop with a CI-grade proof of the full instrumentation chain that Phases 2–5 wired by hand. It adds a **new top-level `integration-tests` Maven module** containing a single cross-service `@SpringBootTest`-style integration test (`OrderFlowIT.java`) that:
1. Starts a Testcontainers `RabbitMQContainer` on a random port (TEST-01).
2. Programmatically launches **two** Spring contexts in one JVM via `SpringApplicationBuilder` — one for `ProducerApplication`, one for `ConsumerApplication` — both pointing at the same testcontainer broker. This preserves the two-service mental model the rest of the workshop teaches; it does NOT fuse them into a single context.
3. Imports a single shared `TestOtelConfiguration` into both contexts that swaps the production OTLP gRPC SDK for an `OpenTelemetrySdk` built with `InMemorySpanExporter` + `SimpleSpanProcessor` + `InMemoryLogRecordExporter` + `SimpleLogRecordProcessor` + `InMemoryMetricReader` (no `PeriodicMetricReader` wrapper — manual `collectAllMetrics()` for determinism). Both contexts share **one** `OpenTelemetry` instance so a single in-memory exporter sees spans/logs/metrics from both services.
4. Exercises the chain via `TestRestTemplate.postForEntity("/orders", ...)` against the producer's random port (`server.port=0`).
5. Asserts **all three signals** across **four `@Test` methods**:
   - **Trace test (TEST-03/04/05):** producer + consumer spans share `traceId`; consumer's `parentSpanId == producer.spanId`; SpanKind covers `SERVER` + `INTERNAL` + `PRODUCER` + `CONSUMER` + `INTERNAL`; messaging semconv attributes (`messaging.system=rabbitmq`, `messaging.destination.name`, `messaging.operation=publish/process`) present where expected.
   - **Log test (LOG-04 carryforward):** producer-side `OrderController` LOG.info + `OrderPublisher` LOG.info land in `InMemoryLogRecordExporter` with matching `trace_id` from the active spans (proves Phase 5's `OpenTelemetryAppender.install(...)` wiring still works through the test SDK).
   - **Metric test (METRIC-02 + METRIC-03 carryforward):** after manual `reader.collectAllMetrics()`, `orders.created` counter has count==1 with `order.priority` attribute; `http.server.request.duration` histogram has count==1 with `http.request.method=POST` + `http.response.status_code=202`.
   - **APP-04 failure-path test (TRACE-09 + Phase 5 D-16 carryforward):** POSTs ten orders sequentially; the 10th order's CONSUMER span has `Status.ERROR` + a recorded `ProcessingFailedException` event; `InMemoryLogRecordExporter` contains a `LOG.error` record whose `trace_id` matches that span's trace.
6. Exits non-zero on any assertion failure — `mise run test` (already wired to `mvn -T 1C verify` in Phase 1) inherits Failsafe automatically because tests are named `*IT.java` (TEST-06).

The pedagogical close: a workshop attendee runs `mise run test` on a fresh clone with the host docker-compose RabbitMQ stopped, sees test logs print a non-default random RabbitMQ port, all four tests pass, and tags the artifact `step-06-tests` — proving every preceding phase's instrumentation is regression-protected.

**In scope (Phase 6 delivers):**
- New `integration-tests` Maven module at `integration-tests/` (sibling of `producer-service`, `consumer-service`, `otel-bootstrap`). Parent `pom.xml` adds `<module>integration-tests</module>`.
- `integration-tests/pom.xml` — packaging `jar`; depends on `producer-service` + `consumer-service` (their unrepackaged classes jars via Spring Boot's `<classifier>` mechanism — see Plans for the exact `spring-boot-maven-plugin` knob), `otel-bootstrap` (transitively), `org.testcontainers:rabbitmq` + `org.testcontainers:junit-jupiter`, `io.opentelemetry:opentelemetry-sdk-testing` (BOM-managed by `opentelemetry-bom:1.61.0`), `org.springframework.boot:spring-boot-starter-test`, `org.awaitility:awaitility`. Configures `maven-failsafe-plugin` (auto-bound by Spring Boot parent — verify) so `*IT.java` runs in `integration-test` phase.
- Producer + consumer service POMs gain a `<classifier>` configuration on `spring-boot-maven-plugin` so each service publishes BOTH the executable fat jar AND the unrepackaged classes jar. The `integration-tests` module depends on the unrepackaged classes jar (without this, Spring Boot's repackaging shadows the `ProducerApplication.class` entry point and `SpringApplicationBuilder(ProducerApplication.class)` fails at test-compile/runtime). Exact classifier value (e.g., `<classifier>exec</classifier>` for the fat jar leaving the plain jar as the default artifact) deferred to research/planner per Spring Boot 3.4.13 docs.
- `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` — `@TestConfiguration` class with:
  - `@Bean InMemorySpanExporter inMemorySpanExporter()`
  - `@Bean InMemoryLogRecordExporter inMemoryLogRecordExporter()`
  - `@Bean InMemoryMetricReader inMemoryMetricReader()`
  - `@Bean OpenTelemetry openTelemetry(...)` — replaces production's `OpenTelemetry` bean by name (relies on `spring.main.allow-bean-definition-overriding=true` set via the test class's `@SpringBootTest(properties=...)`). Builds `OpenTelemetrySdk.builder()` from scratch with `Resource` carrying `service.name=order-producer-OR-order-consumer` (planner picks; either one resource shape used by both contexts so spans+logs+metrics share identity, OR two resources distinguished only by `service.name` and the test config picks at bean-creation time based on environment property — see Decisions).
  - `SdkTracerProvider` with `SimpleSpanProcessor.create(InMemorySpanExporter)`.
  - `SdkLoggerProvider` with `SimpleLogRecordProcessor.create(InMemoryLogRecordExporter)`.
  - `SdkMeterProvider` with `InMemoryMetricReader` registered directly (NO `PeriodicMetricReader` wrapper).
  - Calls `OpenTelemetryAppender.install(openTelemetry)` inside the `@Bean openTelemetry()` factory body BEFORE `return sdk;` — mirrors the Phase 5 fix in commit `f5c331a` exactly. (PITFALL #5 / bean-cycle mitigation carryforward.)
  - `@Bean Tracer tracer(OpenTelemetry openTelemetry)` (parallel to production); `@Bean Meter meter(OpenTelemetry openTelemetry)` (parallel to production); NO `Logger` @Bean (Phase 5 D-07 carryforward — application code logs via SLF4J).
  - `Sampler.alwaysOn()` (test-context sampler; the production `parentBased(alwaysOn())` choice doesn't matter in tests because every span is captured anyway).
  - Heavy comment block documenting why this file exists + every divergence from the production `OtelSdkConfiguration` (the SDK swap IS the lesson surface).
- `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` — JUnit 5 test class:
  - `@Testcontainers` JUnit 5 extension on the class.
  - `@Container static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.3-management-alpine")` (alpine variant to match Testcontainers' default; matches workshop infra `rabbitmq:4.3-management` major.minor).
  - `@BeforeAll` static method that:
    1. `rabbit.start()` if not already started by `@Testcontainers` (defensive).
    2. `System.setProperty("spring.rabbitmq.host", rabbit.getHost())` + `("spring.rabbitmq.port", String.valueOf(rabbit.getAmqpPort()))` + `("spring.rabbitmq.username", rabbit.getAdminUsername())` + `("spring.rabbitmq.password", rabbit.getAdminPassword())`.
    3. `producerCtx = new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class).properties("server.port=0", "spring.main.allow-bean-definition-overriding=true").run()`.
    4. `consumerCtx = new SpringApplicationBuilder(ConsumerApplication.class, TestOtelConfiguration.class).properties("server.port=0", "spring.main.allow-bean-definition-overriding=true").run()`.
    5. Resolve `producerPort = producerCtx.getEnvironment().getProperty("local.server.port", Integer.class)`.
    6. `restTemplate = new TestRestTemplate()` and `restUrl = "http://localhost:" + producerPort + "/orders"`.
  - `@AfterAll` static method shuts down both contexts (`producerCtx.close(); consumerCtx.close();`); `@Container` handles RabbitMQContainer.stop().
  - `@BeforeEach` resets both in-memory exporters AND the metric reader (`spanExporter.reset(); logExporter.reset(); /* metric reader reset is implicit via collectAllMetrics() returning all-time-since-start; planner picks reset strategy */`).
  - Four `@Test` methods (see scope above).
  - Uses `Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> spanExporter.getFinishedSpanItems().size() >= EXPECTED_COUNT)` then `tracerProvider.forceFlush().join(10, SECONDS)` before each set of assertions.
- `mise.toml` — NO changes expected. The existing `[tasks.test] run = "mvn -T 1C verify"` already invokes Failsafe via Spring Boot parent's plugin management. Plan must verify this empirically (it's possible Failsafe needs explicit activation if the parent doesn't bind it).
- README delta: a Phase-6-specific section keyed to tag `step-06-tests` walking the `integration-tests` module + the `RabbitMQContainer` random-port property + the four `@Test` methods + the `mise run test` exit-non-zero-on-failure check. Full README walkthrough body (DOC-01) lands in Phase 7.
- Annotated git tag `step-06-tests` on `main` (WORK-01) — same human-checkpoint convention as Phases 1/2/3/4/5.

**Out of scope (deferred to Phase 7 or v2):**
- Per-service `@SpringBootTest` smoke tests in `producer-service/src/test/` or `consumer-service/src/test/`. The cross-service IT is sufficient per ROADMAP SC #3's singular phrasing.
- GitHub Actions / CI workflow YAML. The TEST-06 contract is "`mise run test` exits non-zero on failure" — sufficient for any CI runner with Docker + mise. CI YAML belongs in Phase 7 polish if at all.
- `integration-tests` module testing logs/metrics on signals OTHER than the ones already locked by Phases 4/5 success criteria (e.g., gauge-only assertions, counter cardinality stress tests).
- Performance benchmarks, load tests, or JMH harnesses (PROJECT.md out-of-scope list).
- Replacing the `MessagePropertiesRoundTripTest` in `otel-bootstrap` — that test stays as the Phase 3 PITFALLS #2 regression net; it is unit-scope and lives correctly where it is.
- Native `@ServiceConnection` usage (it's available in Spring Boot 3.4 and works for typed `RabbitMQContainer`, but the two-context flow we chose can't use it because `@ServiceConnection` is bound to a `@SpringBootTest` context and we manage both contexts manually). Research/planner may revisit if the manual approach proves brittle.
- Test-only Logback config (`logback-test.xml`). Production `logback-spring.xml` (Phase 5) is loaded by both contexts and its `OpenTelemetryAppender` is rewired by `TestOtelConfiguration`'s `OpenTelemetryAppender.install(...)` call. If Logback console noise during tests becomes a problem, planner may add a test-scoped Logback config — but it's not load-bearing.
- Tests for Phase 5's TurboFilter MDC injection (`%mdc{trace_id}` resolving in console pattern) — that's a console-rendering concern visible at runtime; not asserted via in-memory exporters.
- `InMemoryMetricReader.collectAllMetrics()` reset semantics deep-dive — planner picks the reset strategy. For v1 the reader returns latest cumulative state; tests rely on `@BeforeEach` recreating the contexts OR resetting the reader via `OpenTelemetrySdk` rebuild between tests. Acceptable for the four tests we ship; not worth a research flag.

</domain>

<decisions>
## Implementation Decisions

### Test module structure

- **D-01:** **New top-level `integration-tests` Maven module — single home for the cross-service IT.** Parent `pom.xml` adds `<module>integration-tests</module>` next to existing `otel-bootstrap`, `producer-service`, `consumer-service`. The integration-tests module is the unambiguous home for any test that needs both services running. Matches ARCHITECTURE.md's "OPTIONAL cross-service e2e module" sketch — the optionality is closed in v1.
- **D-02:** **Only the cross-service IT lives in this module — no per-service smoke tests.** ROADMAP SC #3 phrasing is singular ("the cross-service integration test"). Per-service tests would duplicate visual Grafana-based SC #1/#2/#4 from Phases 2/3/4/5; pedagogical value is low. The four `@Test` methods inside `OrderFlowIT.java` (traces, logs, metrics, APP-04 failure) cover the full chain.
- **D-03:** **Failsafe + `*IT.java` naming convention.** Test class is `OrderFlowIT.java`. `maven-failsafe-plugin` runs it in Maven's `integration-test` phase (vs Surefire/`*Test.java` in `test`). Spring Boot parent typically auto-binds Failsafe; planner verifies this and adds the explicit binding if missing. `mvn verify` (already wired as `mise run test`) runs both phases — no `mise.toml` change needed. Leaves the existing `MessagePropertiesRoundTripTest.java` in `otel-bootstrap` untouched (it's a `*Test.java` Surefire unit test — correct for that scope).
- **D-04:** **`integration-tests` depends on producer + consumer **classes jars**, not fat jars.** Producer + consumer service POMs gain a `<classifier>` configuration on `spring-boot-maven-plugin` so the **executable** repackaged jar gets the classifier (e.g., `exec`) and the **plain** classes jar remains the default artifact. The `integration-tests` POM declares `<dependency>...producer-service</dependency>` (no classifier) which now resolves to the unrepackaged classes jar. Without this, Spring Boot 3.4.x's repackaging replaces the default artifact and `SpringApplicationBuilder(ProducerApplication.class)` fails to find the application class on the test classpath. **Exact classifier name + plugin config deferred to research/planner per Spring Boot 3.4.13 docs** (the canonical pattern is `<classifier>exec</classifier>` on the repackage execution, but Spring Boot has tweaked this knob across 3.x — confirm against 3.4.13 reference).

### `@TestConfiguration` shape

- **D-05:** **Parallel `TestOtelConfiguration` class — full SDK build.** Mirrors production `OtelSdkConfiguration` line-by-line so attendees can diff the two side-by-side ("same SDK shape, just swapped exporter+processor"). NOT a `@Bean @Primary` partial override (would require Phase 2 production refactor — violates locked structure). NOT JUnit5 `OpenTelemetryExtension` (bypasses Spring DI — defeats the point of the test exercising the real Spring context).
- **D-06:** **`@Import(TestOtelConfiguration.class)` + `spring.main.allow-bean-definition-overriding=true`.** Test class declares `@SpringBootTest(...)`-style properties via `SpringApplicationBuilder.properties(...)` to allow `TestOtelConfiguration`'s `@Bean OpenTelemetry` to override the production bean by name. NOT `@Profile`-based (would force Phase 2 retrofit on existing `OtelSdkConfiguration` files — violates Phase 2 lock). NOT a nested static class (less greppable; harder to reference from README).
- **D-07:** **Single shared `TestOtelConfiguration` instance — both contexts import the same class.** ONE `OpenTelemetry` instance used by both producer and consumer Spring contexts. Pros: the `InMemorySpanExporter` sees ALL spans across both services in ONE queue — `exporter.getFinishedSpanItems()` returns everything; cross-service assertions become straightforward (filter by `SpanKind` or `service.name` resource attribute). The "duplication IS the lesson" rule (Phase 2 D-01) is a **production-code** rule; test infrastructure is exempt. **Resource attribute strategy:** the shared SDK uses ONE `Resource` for the SDK itself (e.g., `service.name=integration-test`), and per-span `service.name` distinction relies on the fact that the same Tracer is shared but spans carry their own per-service context. **Open detail — planner resolves:** whether to use ONE resource (and assert spans by SpanKind/messaging-attributes, not service.name) OR build TWO resources at TestOtelConfiguration construction time and pick at runtime via a context-scoped property. Recommendation: ONE resource, assert by SpanKind — simpler, matches the in-memory test fixture purpose.
- **D-08:** **`TestOtelConfiguration` exposes `Tracer` + `Meter` + `InMemorySpanExporter` + `InMemoryLogRecordExporter` + `InMemoryMetricReader` as `@Bean`s.** Production-parallel beans (`Tracer`, `Meter`) so existing constructor-injected services (Phase 2 `OrderService`, Phase 3 `TracingMessagePostProcessor`, Phase 4 `OrderService`/`HttpServerSpanFilter`/`QueueDepthGauge`) keep wiring correctly. NO `Logger` @Bean (Phase 5 D-07 carryforward). The three in-memory exporter/reader beans are constructor-injected into the test class for assertions.
- **D-09:** **`OpenTelemetryAppender.install(openTelemetry)` runs INSIDE the `@Bean openTelemetry()` factory body** — BEFORE `return sdk;`, mirroring the Phase 5 fix in commit `f5c331a` exactly. NOT `@PostConstruct` (Phase 5's bean-cycle blocker recurrence — STATE.md line 107). The TestOtelConfiguration's appender install is the **same line of code** as production — the bug-then-fix lesson from Phase 5 is preserved across the test pipeline.

### Cross-service flow mechanism

- **D-10:** **Two `SpringApplicationBuilder` contexts in `@BeforeAll`.** `producerCtx = new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class).properties(...).run()` then same for `ConsumerApplication`. NOT a single `@SpringBootTest(classes={ProducerApplication.class, ConsumerApplication.class})` (would fuse both component-scans into ONE Spring context — blurs the two-service mental model the workshop teaches). NOT consumer-as-classes-only (skips real `@RabbitListener` startup — defeats Phase 3's listener-advice teaching). The two-context approach preserves "two services, one JVM" — the AMQP boundary is REAL.
- **D-11:** **`@Container static RabbitMQContainer` + `System.setProperty("spring.rabbitmq.*", ...)` in `@BeforeAll` BEFORE either context starts.** `@Container` (with class-level `@Testcontainers`) manages container start/stop. `System.setProperty` is read by Spring Boot's `Environment` during context refresh, so both contexts pick up the testcontainer's random AMQP port automatically. NOT `@DynamicPropertySource` (tied to `@SpringBootTest` lifecycle; doesn't auto-apply to manually-managed contexts). NOT `SpringApplicationBuilder.properties(Map.of(...))` for connection (works but pollutes per-context properties — `System.setProperty` is idiomatic for "test-environment-wide" flags). **Property leak note:** `System.setProperty` survives across test methods/classes in the same JVM. Test class's `@AfterAll` clears the four properties (`System.clearProperty(...)`) defensively. Planner adds this. (TEST-01 SC #2 — random port visible in test logs — works because Testcontainers' ryuk + container.start() banner prints the random port.)
- **D-12:** **`TestRestTemplate` against producer's random port (`server.port=0`).** Producer context started with `server.port=0`; actual port resolved via `producerCtx.getEnvironment().getProperty("local.server.port", Integer.class)`. `new TestRestTemplate().postForEntity(restUrl, orderRequest, Void.class)`. Real HTTP exercises the SERVER span (Phase 2 `HttpServerSpanFilter`) — required for SpanKind assertions per SC #3. NOT direct `OrderService.place(...)` (skips SERVER span, breaks SpanKind coverage). NOT `WebTestClient` (webflux dep we don't need; producer is Spring MVC).
- **D-13:** **Awaitility polling on `InMemorySpanExporter.getFinishedSpanItems().size()` + `forceFlush` before assertions.** After POST returns 202, test calls `Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> spanExporter.getFinishedSpanItems().size() >= EXPECTED_SPAN_COUNT)`. EXPECTED_SPAN_COUNT per test method (planner sets — likely 5 for happy path: SERVER + INTERNAL_producer + PRODUCER + CONSUMER + INTERNAL_consumer). After Awaitility resolves: `((SdkTracerProvider) openTelemetry.getSdkTracerProvider()).forceFlush().join(10, TimeUnit.SECONDS)` (belt-and-braces — `SimpleSpanProcessor` is synchronous so this is mostly a safety net). NO `Thread.sleep(...)` (PITFALLS #11 anti-pattern). NO test-only `@RabbitListener` with `CountDownLatch` (would compete with the production listener for messages). Add `org.awaitility:awaitility` (BOM-managed by Spring Boot 3.4.13) as a test-scope dep on `integration-tests/pom.xml`.

### Test signal coverage (full triple-signal — D-14..D-18)

- **D-14:** **Four `@Test` methods, one per signal + one for the failure path.** Single `OrderFlowIT.java` class. Methods:
  1. `happyPathProducesSingleTrace_traceAssertions()` — POSTs ONE order; awaits 5 spans; asserts shared `traceId` across all 5 spans, `consumerSpan.parentSpanId == producerSpan.spanId`, SpanKind set covers `{SERVER, INTERNAL, PRODUCER, CONSUMER, INTERNAL}`, messaging semconv attrs present (`messaging.system=rabbitmq`, `messaging.destination.name=<exchange>`, `messaging.operation=publish` on PRODUCER, `messaging.operation=process` on CONSUMER). (TEST-03 + TEST-04 + TEST-05.)
  2. `happyPathStampsLogsWithTraceId_logAssertions()` — POSTs ONE order; awaits spans + log records via `Awaitility.await().until(() -> logExporter.getFinishedLogRecordItems().size() >= 2)`; asserts `OrderController.LOG.info` + `OrderPublisher.LOG.info` records have `getSpanContext().getTraceId()` matching the producer trace. (LOG-04 carryforward — proves the appender install in `TestOtelConfiguration` works.)
  3. `successfulOrderRecordsCounterAndHistogram_metricAssertions()` — POSTs ONE order; awaits trace; calls `inMemoryMetricReader.collectAllMetrics()`; asserts a `MetricData` for `orders.created` with one cumulative point with value==1 and the `order.priority` attribute, AND a `MetricData` for `http.server.request.duration` with one histogram point carrying `http.request.method=POST` + `http.response.status_code=202`. (METRIC-02 + METRIC-03 carryforward.)
  4. `tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions()` — POSTs 10 orders sequentially (each 202); awaits enough spans for 10 traces; asserts the 10th order's CONSUMER span has `Status.ERROR` + an exception event whose name starts with `exception` and whose attribute `exception.type` ends in `ProcessingFailedException`; asserts `logExporter` contains a `LogRecordData` with severity `ERROR` and `getSpanContext().getTraceId()` matching that trace. (Phase 3 APP-04 + TRACE-09 + Phase 5 D-16 carryforward.)
- **D-15:** **`@BeforeEach` resets in-memory exporters; metric reader handled per-test.** `spanExporter.reset()` and `logExporter.reset()` in `@BeforeEach` (both expose a `reset()` method per `opentelemetry-sdk-testing` API). For `InMemoryMetricReader`: cumulative state survives across tests; the metric test method asserts `count >= 1` rather than exact equality, OR planner rebuilds the SDK between metric-related tests. Recommendation: filter `MetricData` by attribute (`order.priority` value unique per test) and assert presence of the test-specific point — robust against cumulative state.
- **D-16:** **`InMemoryMetricReader` registered DIRECTLY (no `PeriodicMetricReader` wrapper).** Production uses `PeriodicMetricReader.builder(...).setInterval(Duration.ofSeconds(10))` (METRIC-01); tests register `InMemoryMetricReader` directly so `collectAllMetrics()` is synchronous. Test diverges from production's metric pipeline shape — documented in `TestOtelConfiguration`'s comment block as "production wraps the exporter in PeriodicMetricReader for periodic export; tests register InMemoryMetricReader directly because it implements MetricReader and the test calls `collectAllMetrics()` synchronously for determinism" (parallel to the SimpleSpanProcessor-not-Batch comment).
- **D-17:** **Triple-signal correlation in the failure-path test is the workshop's strongest assertion.** Test 4 (`tenthOrderProducesErrorSpan...`) asserts that ONE trace_id stamps both the recordException event on the CONSUMER span AND the LOG.error record. This is the ONE place in the workshop where Phase 3's recordException + Phase 5's LOG.error + Phase 4's metric (counter does NOT increment on failure per Phase 4 D-08) all converge on a single observable behavior. Documented in the test's JavaDoc.
- **D-18:** **Test SDK uses `Sampler.alwaysOn()`.** Production uses `Sampler.parentBased(Sampler.alwaysOn())` (TRACE-03). In tests with `SimpleSpanProcessor` and a single test trace, `alwaysOn()` is sufficient and faster (skips parent-context check). The sampler choice is documented in `TestOtelConfiguration`'s comment block.

### Carryforward + tag (D-19..D-21)

- **D-19:** **DOC-03 comment density bar applies to `TestOtelConfiguration.java`.** Phase 2 set ≥40 comment lines per `OtelSdkConfiguration.java`; Phase 4/5 pushed it to ~80+. `TestOtelConfiguration.java` will naturally exceed 40 lines because it documents:
  - Why this file exists (test-only SDK swap).
  - Every divergence from production (`SimpleSpanProcessor` vs Batch, `InMemorySpanExporter` vs OTLP, `InMemoryMetricReader` direct vs `PeriodicMetricReader`, `Sampler.alwaysOn()` vs parentBased).
  - The `OpenTelemetryAppender.install(...)` ordering carryforward (Phase 5 PITFALL #5 + bean-cycle).
  - Why the file is shared between two Spring contexts (single in-memory exporter sees both).
  Plan must include a verification step (`grep` count or similar).
- **D-20:** **README delta is small and Phase-6-specific.** Add a "Step 6: Verification Tests" section keyed to tag `step-06-tests` that:
  - Names the new `integration-tests` module + the four `@Test` methods.
  - Calls out the `RabbitMQContainer` random-port property attendees can see in test logs (TEST-01 SC #2).
  - Calls out the SimpleSpanProcessor + `InMemorySpanExporter` swap as the test-determinism lesson.
  - Names the triple-signal CI guarantee (traces + logs + metrics + failure path).
  - Brief callout on the `<classifier>` mechanism for cross-module classes-jar dependency.
  Full README walkthrough body (DOC-01) and dashboard/load script (DOC-04 / WORK-02 / WORK-03) land in Phase 7.
- **D-21:** **Annotated git tag `step-06-tests` is the exit gate (WORK-01 carryforward).** Same human-checkpoint pattern as Phases 1/2/3/4/5: source merged + all 5 ROADMAP success criteria verified by running `mise run test` (with host RabbitMQ stopped) at HEAD, then user gate approves the orchestrator-applied tag. STATE.md / ROADMAP.md `[x]` flips land atomically with the tag-apply commit.

### Post-research clarification (added 2026-05-02 after `/gsd-research-phase 6`)

- **D-07.1:** **`TestOtelHolder` static-singleton pattern resolves D-07 across two Spring contexts.** RESEARCH §3.3 / OPEN-QUESTION-1 surfaced that `@Import(TestOtelConfiguration.class)` into two `SpringApplicationBuilder` contexts yields TWO `OpenTelemetry`/`InMemorySpanExporter` bean instances (Spring DI scopes beans per-context) — D-07's "ONE shared exporter sees both services" cannot hold by `@Import` alone. **Resolution:** add `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` with `static volatile` fields for `OpenTelemetrySdk`, `InMemorySpanExporter`, `InMemoryLogRecordExporter`, `InMemoryMetricReader`. A `synchronized static get()` method lazy-initializes the SDK on first call and returns the same instance to both contexts. `TestOtelConfiguration`'s `@Bean OpenTelemetry openTelemetry()` returns `TestOtelHolder.get()`; the three exporter `@Bean`s return `TestOtelHolder.SPANS / LOGS / METRICS` (calling `get()` first to ensure init). `OpenTelemetryAppender.install(SDK)` runs inside `TestOtelHolder.get()` (preserves D-09 ordering — install AFTER SDK build, BEFORE first @Bean returns). Test class reads from `TestOtelHolder` static fields directly (no need to choose between producerCtx vs consumerCtx beans). **Why this path:** preserves D-07 literally ("ONE OpenTelemetry instance across both contexts"); preserves D-10 literally (two SpringApplicationBuilder contexts); preserves D-09 install-ordering exactly; pedagogically clean — TestOtelHolder.java is a small obvious shared-resource class that mirrors well-known JUnit-with-shared-resource patterns. **Why not the alternatives:** "Two exporters, merge in test" violates D-07 ("ONE … sees ALL spans") even though it also works; "pre-build SDK in @BeforeAll" is functionally identical to TestOtelHolder but scatters wiring across the test class instead of centralizing in a tiny purpose-built helper. **Files affected:** new `TestOtelHolder.java` (≤60 lines including JavaDoc), modified `TestOtelConfiguration.java` (`@Bean` bodies now thin facades over `TestOtelHolder`), `OrderFlowIT.java` reads from `TestOtelHolder.SPANS / LOGS / METRICS` instead of `producerCtx.getBean(...)`.

### Claude's Discretion

- **Exact `<classifier>` value** on `spring-boot-maven-plugin` for the executable repackage (D-04). Spring Boot 3.4.13 reference is the source of truth — research/planner confirms whether `<classifier>exec</classifier>` on `<execution><goals><goal>repackage</goal></goals></execution>` is the current canonical syntax, or whether `<attach>true</attach>` + `<classifier>` is needed.
- **Awaitility version pin.** Spring Boot 3.4.13 BOM manages a version (`org.awaitility:awaitility:4.2.x`); planner uses BOM-managed unless a feature requires bumping.
- **Test class package** (`com.example.e2e` vs `com.example.integration` vs `com.example.tests`). Planner picks; `e2e` matches ARCHITECTURE.md's earlier sketch.
- **`@BeforeEach` exact reset semantics** for `InMemoryMetricReader` (D-15). Planner picks: filter-by-attribute OR per-test SDK rebuild OR rely on `>=` count assertions.
- **Exact assertion library** for span/metric structure inspection. AssertJ + opentelemetry-sdk-testing's `SpanDataAssert` / `MetricAssert` (if present in 1.61.0) are the natural choices; planner picks.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap

- `.planning/REQUIREMENTS.md` — TEST-01 through TEST-06 (locked: `@ServiceConnection` random-port behavior — note this CONTEXT D-11 chose manual `System.setProperty` over `@ServiceConnection` because we manage two contexts; researcher must validate the random-port still surfaces in test logs to honor TEST-01 SC #2), `InMemorySpanExporter` + `SimpleSpanProcessor` not Batch, cross-service traceId/parentSpanId/SpanKind/messaging-semconv assertions, `mise run test` exits non-zero on failure. WORK-01 (annotated tag `step-06-tests` exit gate).
- `.planning/ROADMAP.md` §"Phase 6: Verification Tests" — all 5 success criteria; "Plans: TBD" (this CONTEXT.md seeds plan creation); research flag still open (`@ServiceConnection` + `RabbitMQContainer` actual-container validation on Spring Boot 3.4.13).
- `.planning/PROJECT.md` — overall constraint set (Spring Boot 3.4.13, Java 17, no Java agent, no Micrometer bridge, no autoconfigure starter, single OTLP endpoint, otel-lgtm backend, `org.testcontainers:rabbitmq` for the RabbitMQContainer).
- `.planning/STATE.md` line 106 — flags the open Phase 6 research item: "Validate `@ServiceConnection` + `RabbitMQContainer` actually uses the test container (not the host RabbitMQ) on Spring Boot 3.4.13." This CONTEXT moved off `@ServiceConnection` for unrelated reasons (two-context flow), but the research flag is still relevant for the random-port log-printing assertion (TEST-01 SC #2).
- `.planning/STATE.md` line 107 — Phase 5 bean-cycle history. **Critical for Phase 6:** the Phase 5 fix moved `OpenTelemetryAppender.install(...)` from `@PostConstruct` into the `@Bean openTelemetry()` factory body (commit `f5c331a`). `TestOtelConfiguration` MUST replicate this exact pattern (D-09) or the log-stamping test will silent-no-op the same way Phase 5's smoke test did.

### Research artifacts

- `.planning/research/SUMMARY.md` §"Phase 6: Verification Tests" — locks `@ServiceConnection` + typed `RabbitMQContainer` + `@TestConfiguration` swap to `InMemorySpanExporter` + `SimpleSpanProcessor` (NOT Batch); `OpenTelemetryExtension` from `opentelemetry-sdk-testing` is mentioned as optional (this CONTEXT does NOT use it — see D-05 rationale).
- `.planning/research/STACK.md` — `org.testcontainers:rabbitmq` + `:junit-jupiter` modules, `spring-boot-testcontainers` artifact, `opentelemetry-sdk-testing` (BOM-managed by 1.61.0).
- `.planning/research/PITFALLS.md` §Pitfall #4 — `BatchSpanProcessor`/`BatchLogRecordProcessor` lose telemetry on JVM shutdown — CRITICAL for tests; Phase 6 mitigates via `SimpleSpanProcessor` + `SimpleLogRecordProcessor` + `forceFlush().join(10s)` (D-13).
- `.planning/research/PITFALLS.md` §Pitfall #5 — `OpenTelemetryAppender` initialised before SDK is ready → silent log drop. Mitigation = `install(openTelemetry)` AFTER SDK is built. `TestOtelConfiguration` D-09 mirrors the Phase 5 fix (in-factory-body call before `return sdk;`).
- `.planning/research/PITFALLS.md` §Pitfall #11 — `BatchSpanProcessor` with `SimpleSpanProcessor` confusion in tests. Phase 6 doctrine: tests use Simple, never Batch. `Thread.sleep` is the listed warning sign — D-13 forbids it.
- `.planning/research/PITFALLS.md` §Pitfall #13 — `@ServiceConnection` requires typed `RabbitMQContainer` + the `org.testcontainers:rabbitmq` module + `spring-boot-testcontainers` artifact; `GenericContainer` does NOT bind. Phase 6 uses typed `RabbitMQContainer` (D-11) even though we're not using `@ServiceConnection` itself — keeps the option open if planner reverses D-10.
- `.planning/research/ARCHITECTURE.md` §"Recommended Project Structure" — names the optional `integration-tests/` module sketched at the parent level. Phase 6 D-01 makes that module non-optional.

### Carryforward decisions from prior phases

- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` — Phase 2 D-01 (per-service duplication of `OtelSdkConfiguration` is a **production** rule; test infrastructure is exempt — D-07 carryforward), D-12 (`System.getenv` + `Optional.ofNullable(...).orElse(...)` for OTLP endpoint — irrelevant in tests because `TestOtelConfiguration` doesn't use OTLP), D-15 (`@Bean(destroyMethod="close")` lifecycle cascade — `TestOtelConfiguration`'s `OpenTelemetry` @Bean inherits this; AfterAll context.close() triggers SDK shutdown).
- `.planning/phases/03-amqp-context-propagation/03-CONTEXT.md` — TracingMessagePostProcessor + TracingMessageListenerAdvice classes (the propagation pair Phase 6's trace test asserts works); APP-04 (deterministic 10th-order failure — Phase 6 D-14 test 4 verifies); TRACE-09 (recordException + setStatus(ERROR) — Phase 6 D-17 asserts).
- `.planning/phases/04-metrics/04-CONTEXT.md` — METRIC-02 `orders.created` counter shape (asserted by D-14 test 3); METRIC-03 `http.server.request.duration` histogram shape (asserted by D-14 test 3); METRIC-01 PeriodicMetricReader 10s interval (Phase 6 D-16 explicitly diverges in tests — synchronous `InMemoryMetricReader` instead); Phase 4 D-08 (counter does NOT increment on failure — D-17 honors this in test 4 by NOT asserting counter on the failed path).
- `.planning/phases/05-logs-correlation/05-CONTEXT.md` — Phase 5 D-08/D-09 (`OpenTelemetryAppender.install(...)` PITFALL #5 + bean-cycle blocker resolution in commit `f5c331a` — Phase 6 D-09 replicates EXACTLY); Phase 5 D-15/D-16 (the producer + consumer log statements Phase 6 D-14 test 2 asserts on); Phase 5 D-07 (no `Logger` @Bean — Phase 6 D-08 honors).
- **`f5c331a`** — Phase 5 fix commit moving `OpenTelemetryAppender.install(...)` into the `@Bean` factory body. Read this commit before writing `TestOtelConfiguration` to mirror the exact ordering.

### Files Phase 6 modifies / creates (read first to plan diffs)

- `pom.xml` (parent) — add `<module>integration-tests</module>` to the `<modules>` list.
- `producer-service/pom.xml` — add `<classifier>` configuration to `spring-boot-maven-plugin` (D-04). Exact knob deferred to research/planner.
- `consumer-service/pom.xml` — same `<classifier>` configuration as producer.
- `integration-tests/pom.xml` (NEW) — packaging `jar`; deps on producer-service + consumer-service classes jars + Testcontainers + `opentelemetry-sdk-testing` + `spring-boot-starter-test` + Awaitility; `maven-failsafe-plugin` configured (or inherited).
- `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` (NEW) — D-05..D-09; ≥40 comment lines per D-19.
- `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` (NEW) — D-10..D-14; four `@Test` methods.
- `integration-tests/src/test/resources/` — possibly a test-only `logback-test.xml` if console noise is an issue (NOT load-bearing; planner picks).
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — **read only** (Phase 6 does not modify; `TestOtelConfiguration` is a parallel, not a replacement).
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — **read only** (same).
- `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` — **read only** (test 2 asserts the existing LOG.info from Phase 5 D-15 fires with trace_id stamping).
- `consumer-service/src/main/java/com/example/consumer/processing/ProcessingService.java` (or wherever Phase 5 D-16 LOG.error landed — verify) — **read only**.
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` — **read only** (test 1 asserts the inject works); `TracingMessageListenerAdvice.java` — **read only** (test 1 asserts the extract works).
- `mise.toml` — verify `mvn -T 1C verify` triggers Failsafe via Spring Boot parent; planner adds explicit Failsafe binding to `integration-tests/pom.xml` if needed.
- `README.md` — add "Step 6: Verification Tests" section (D-20).

### OTel API surface (paste-able from STACK.md / SDK 1.61.0)

- `io.opentelemetry:opentelemetry-sdk-testing` (BOM-managed by `opentelemetry-bom:1.61.0`) — `InMemorySpanExporter`, `InMemoryLogRecordExporter`, `InMemoryMetricReader`, `OpenTelemetryExtension` (NOT used per D-05).
- `io.opentelemetry.sdk.trace.export.SimpleSpanProcessor` — production-side via existing `opentelemetry-sdk` dep; no new artifact.
- `io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor` — same.
- semconv 1.40.0 (already on classpath) — `MessagingIncubatingAttributes.MESSAGING_SYSTEM`, `MESSAGING_DESTINATION_NAME`, `MESSAGING_OPERATION_TYPE`; `HttpAttributes.HTTP_REQUEST_METHOD`, `HTTP_RESPONSE_STATUS_CODE` — used in assertions.

### Test infrastructure

- `org.testcontainers:rabbitmq` (BOM-managed by Spring Boot 3.4.13) — typed `RabbitMQContainer`.
- `org.testcontainers:junit-jupiter` — `@Testcontainers` + `@Container` JUnit 5 extension.
- `org.springframework.boot:spring-boot-testcontainers` — declared on `integration-tests/pom.xml` even though we use manual property injection (D-11) rather than `@ServiceConnection`. Keeps the option open if planner reverses D-10.
- `org.awaitility:awaitility` (BOM-managed by Spring Boot 3.4.13) — D-13 polling.
- `org.springframework.boot:spring-boot-starter-test` — JUnit 5 + AssertJ + Mockito + TestRestTemplate.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`producer-service/src/main/java/com/example/producer/ProducerApplication.java`** — `@SpringBootApplication` entry point; reachable via `SpringApplicationBuilder(ProducerApplication.class)` once the producer-service classes jar is on the integration-tests classpath (D-04 classifier mechanism).
- **`consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java`** — same; reachable for the second context.
- **`producer-service/.../config/OtelSdkConfiguration.java`** — production SDK builder. `TestOtelConfiguration` mirrors its 4-step shape (Resource → tracer pipeline → meter pipeline → logger pipeline → SDK build) with in-memory exporters substituted (D-05).
- **`consumer-service/.../config/OtelSdkConfiguration.java`** — mirror; both production files exist with the exact line that must be replicated for the install ordering (Phase 5 D-09 / commit `f5c331a`).
- **`otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java`** — existing Surefire unit test (Phase 3 PITFALLS #2 regression net). UNCHANGED by Phase 6; the `*Test.java` Surefire convention here coexists with `*IT.java` Failsafe in `integration-tests/`.
- **`mise.toml [tasks.test]`** — already wired to `mvn -T 1C verify`; Phase 6 tests run without any `mise.toml` change as long as Failsafe is bound (D-03).
- **Phase 1 `docker-compose.yml` `rabbitmq:4.3-management`** — production broker on the host. The Testcontainers `RabbitMQContainer` defaults to `rabbitmq:4.3-management-alpine` (close cousin of the production image). Tests pass when host docker-compose RabbitMQ is stopped (TEST-01 SC #1).

### Established Patterns

- **Per-service production duplication, never extracted** — Phase 2 D-01 / DOC-05. **Phase 6 declines to apply this rule to test infrastructure** (D-07 carries one shared `TestOtelConfiguration` because the in-memory exporter must see both contexts). Documented in `TestOtelConfiguration`'s comment block to prevent confusion.
- **`@Bean(destroyMethod="close")` cascade** — Phase 2 D-15. `TestOtelConfiguration`'s `@Bean OpenTelemetry` inherits the `destroyMethod="close"` shape; `@AfterAll producerCtx.close(); consumerCtx.close()` triggers the cascade through both contexts.
- **`OpenTelemetryAppender.install(...)` runs INSIDE `@Bean openTelemetry()` factory body** — Phase 5 fix `f5c331a`. Phase 6 D-09 replicates exactly.
- **Heavy comment density bar (≥40 lines per OtelSdkConfiguration-shaped file)** — Phase 2 DOC-03; Phase 4/5 push to ~80+. Phase 6 D-19 carries the bar to `TestOtelConfiguration.java`.
- **Annotated tag at exit + atomic STATE/ROADMAP flip** — Phase 1/2/3/4/5 pattern (WORK-01 carryforward). Phase 6 D-21 honors.
- **SLF4J in business code** — Phase 1 / Phase 5 D-07 / D-15. Test does NOT add new business log lines; D-14 test 2 asserts on the existing Phase 5 LOG.info statements.

### Integration Points

- **`SpringApplicationBuilder` constructor** — accepts `Class<?>...` of `@Configuration` classes. Both test contexts pass `(ApplicationClass.class, TestOtelConfiguration.class)` so the test config bean overrides land alongside production component-scan.
- **`spring.main.allow-bean-definition-overriding=true`** — Spring Boot 2.1+ default-OFF; required for `TestOtelConfiguration`'s `@Bean OpenTelemetry` to override the production-scanned bean. Set per-context via `SpringApplicationBuilder.properties(...)`.
- **`server.port=0`** — random port; resolved post-startup via `Environment.getProperty("local.server.port", Integer.class)`. Standard Spring Boot test pattern.
- **`@Container` (with class-level `@Testcontainers`)** — JUnit 5 Testcontainers extension manages container lifecycle. Container starts BEFORE `@BeforeAll`; stops AFTER `@AfterAll`.
- **`Awaitility.await().atMost(Duration).until(BooleanSupplier)`** — JUnit-friendly polling helper. Default polling interval ~100ms; sufficient for `SimpleSpanProcessor`'s synchronous-but-multi-context path.
- **`InMemorySpanExporter.create()` / `.reset()` / `.getFinishedSpanItems()`** — opentelemetry-sdk-testing's canonical API.
- **`TestRestTemplate.postForEntity(url, body, Class<T>)`** — auto-configures with `HttpClientErrorException` non-throwing semantics; suitable for asserting status codes.

</code_context>

<specifics>
## Specific Ideas

- The integration-tests module is the workshop's first artifact that breaks the per-service duplication rule (in `TestOtelConfiguration`). The README delta (D-20) should explicitly call this out: production code duplicates so attendees read the SDK setup twice; test code shares because the test fixture's purpose (single in-memory exporter sees the whole chain) requires it. The contrast IS pedagogically interesting.
- The `<classifier>` mechanism (D-04) is the only Maven trickery in the phase — and it's a pattern attendees may need in their own multi-module Spring Boot codebases. Brief README mention is worth ~2 lines.
- The `OpenTelemetryAppender.install(...)` call in `TestOtelConfiguration` (D-09) mirrors the Phase 5 fix line-for-line. Comment block should reference commit `f5c331a` so attendees can `git show f5c331a` to see why this ordering matters.
- The four-test-method structure (D-14) reads like a checklist of the workshop's deliverables — traces, logs, metrics, error-path. README walkthrough can mirror this structure: "Phase 2 wired traces; test 1 asserts traces. Phase 3 wired propagation; test 1 also asserts propagation. Phase 4 wired metrics; test 3 asserts. Phase 5 wired logs; test 2 asserts. Phase 3 APP-04 wired errors; test 4 asserts."
- The triple-signal correlation in test 4 (D-17) is the workshop's strongest single statement of "all three signals work together." It's the Phase 6 highlight equivalent of Phase 3's "ONE trace spans both services" moment.
- The `RabbitMQContainer.getAmqpPort()` random port is visible in Testcontainers' default startup banner (`com.github.dockerjava` log line). Test logs printed by `mvn verify` show this without any extra logging — TEST-01 SC #2 satisfied for free.

</specifics>

<deferred>
## Deferred Ideas

- **GitHub Actions / CI workflow YAML** — TEST-06's "exits non-zero on failure" is a runner-agnostic contract. CI YAML belongs in Phase 7 polish if at all; not in scope for v1.
- **Per-service `@SpringBootTest` smoke tests** — Phase 6 D-02 declined; could be a v2 addition if cohort feedback requests per-service test isolation as a teaching surface.
- **`@ServiceConnection` variant** — Phase 6 D-10 picked two-context flow which precludes `@ServiceConnection`. A simpler "one fused context" variant could be a Phase 7 polish callout ("here's how the simpler test would look — and here's why we picked the harder shape").
- **Test-only Logback config (`logback-test.xml`)** — only added if console noise during tests becomes a problem; not load-bearing for v1.
- **Performance benchmarks / JMH harnesses** — PROJECT.md out-of-scope; remains so.
- **Native-image compatibility test** — out of scope per PROJECT.md.
- **Cardinality-stress tests for metrics** — `order.priority` cardinality is fixed (2 values per Phase 4 D-10); no need to assert cardinality bounds in v1.
- **Baggage propagation tests** — Phase 2 D-16 wired the baggage propagator but no phase exercises it. A baggage-focused phase + test would be a v2 candidate.
- **Span-sampling tests** — Phase 2 chose `parentBased(alwaysOn())`; alternate samplers are deferred per `SAMP-01` v2 entry in REQUIREMENTS.md.
- **Tests for Phase 5's TurboFilter MDC injection** (`%mdc{trace_id}` resolving in console pattern) — that's a console-rendering concern; not asserted in-memory. Could be a unit test on Logback's PatternLayout if attendees ask, but not load-bearing.
- **`integration-tests` module's own dashboard / load script** — Phase 7's WORK-02 + WORK-03 cover production demo-time tooling.

</deferred>

---

*Phase: 6-verification-tests*
*Context gathered: 2026-05-02*
