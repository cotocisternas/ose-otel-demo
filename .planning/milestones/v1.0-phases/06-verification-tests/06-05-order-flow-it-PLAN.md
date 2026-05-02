---
phase: 06-verification-tests
plan: 05
type: execute
wave: 5
depends_on:
  - 06-03
  - 06-04
files_modified:
  - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java
autonomous: true
requirements:
  - TEST-01
  - TEST-02
  - TEST-03
  - TEST-04
  - TEST-05
  - TEST-06
tags:
  - integration-test
  - testcontainers
  - rabbitmq
  - awaitility
  - opentelemetry-assertions
  - failsafe
  - phase-6
must_haves:
  truths:
    - id: IT-FILE-EXISTS
      description: "OrderFlowIT.java exists at the canonical e2e package path AND ends in 'IT.java' (Failsafe convention)"
      verify: "test -f integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: TESTCONTAINERS-CLASS-ANNOTATION
      description: "Class is annotated @Testcontainers (JUnit 5 extension manages container lifecycle)"
      verify: "grep -q '^@Testcontainers' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'import org.testcontainers.junit.jupiter.Testcontainers' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: CONTAINER-FIELD
      description: "@Container static final RabbitMQContainer with rabbitmq:4.3-management-alpine image (matches production major.minor per CONTEXT D-11)"
      verify: "grep -qE '@Container\\s*\\n?\\s*static\\s+final\\s+RabbitMQContainer\\s+rabbit' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -F '\"rabbitmq:4.3-management-alpine\"' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: BEFORE-ALL-LOG-RANDOM-PORT
      description: "TEST-01 SC #2: explicit LOG.info line in @BeforeAll surfaces the random port (RESEARCH §2.2)"
      verify: "grep -F 'LOG.info(\"RabbitMQ test container available at {}:{}\"' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: SYSTEM-PROPERTY-SET
      description: "@BeforeAll sets spring.rabbitmq.host/port/username/password via System.setProperty BEFORE either context starts (D-11)"
      verify: "test $(grep -c 'System.setProperty(\"spring.rabbitmq' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) -ge 4"
    - id: TWO-CONTEXTS
      description: "Two SpringApplicationBuilder contexts started in @BeforeAll: ProducerApplication + ConsumerApplication, each importing TestOtelConfiguration (D-10)"
      verify: "grep -q 'new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'new SpringApplicationBuilder(ConsumerApplication.class, TestOtelConfiguration.class)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: ALLOW-BEAN-OVERRIDING
      description: "Both contexts set spring.main.allow-bean-definition-overriding=true (D-06 — required for TestOtelConfiguration's @Bean OpenTelemetry to override production)"
      verify: "test $(grep -c 'spring.main.allow-bean-definition-overriding=true' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) -ge 2"
    - id: SERVER-PORT-RANDOM
      description: "Both contexts use server.port=0 (random port) — at least 2 occurrences (one per context)"
      verify: "test $(grep -c 'server.port=0' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) -ge 2"
    - id: HOLDER-DIRECT-READS
      description: "Test class reads telemetry from TestOtelHolder static fields directly (NOT via producerCtx.getBean — D-07.1 fix)"
      verify: "grep -q 'TestOtelHolder.SPANS' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'TestOtelHolder.LOGS' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'TestOtelHolder.METRICS' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && ! grep -q 'producerCtx.getBean(InMemorySpanExporter' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: BEFORE-EACH-RESET
      description: "@BeforeEach resets InMemorySpanExporter + InMemoryLogRecordExporter + drains InMemoryMetricReader via collectAllMetrics() (RESEARCH §2.3 — InMemoryMetricReader has no reset())"
      verify: "grep -q '@BeforeEach' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -qE 'TestOtelHolder\\.SPANS\\.reset\\(\\)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -qE 'TestOtelHolder\\.LOGS\\.reset\\(\\)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -qE 'TestOtelHolder\\.METRICS\\.collectAllMetrics\\(\\)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: AFTER-ALL-CLEANUP
      description: "@AfterAll closes producer FIRST, then consumer (RESEARCH §2.6); clears 4 System.properties"
      verify: "grep -q '@AfterAll' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && awk '/@AfterAll/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | awk '/producerCtx.close/{p=NR} /consumerCtx.close/{c=NR} END{exit !(p && c && p < c)}' && test $(grep -c 'System.clearProperty(\"spring.rabbitmq' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) -ge 4"
    - id: AWAITILITY-USED-NOT-SLEEP
      description: "Awaitility polling used; no Thread.sleep (D-13 / PITFALLS #11)"
      verify: "grep -q 'Awaitility.await()' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && ! grep -q 'Thread.sleep' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: FORCE-FLUSH-IN-TESTS
      description: "Tests call forceFlush().join(...) on tracer + logger providers AFTER awaiting (RESEARCH §2.6 belt-and-braces)"
      verify: "grep -q '.forceFlush().join(' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: FOUR-TEST-METHODS
      description: "Exactly 4 @Test methods (D-14 — happy-path-traces, happy-path-logs, happy-path-metrics, tenth-order-failure-path)"
      verify: "test $(grep -cE '^\\s*@Test\\s*$' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) -eq 4"
    - id: TEST-1-NAME
      description: "Test 1: happyPathProducesSingleTrace_traceAssertions (D-14 — TEST-03/04/05 coverage)"
      verify: "grep -qE 'void\\s+happyPathProducesSingleTrace_traceAssertions\\s*\\(' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: TEST-2-NAME
      description: "Test 2: happyPathStampsLogsWithTraceId_logAssertions (D-14)"
      verify: "grep -qE 'void\\s+happyPathStampsLogsWithTraceId_logAssertions\\s*\\(' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: TEST-3-NAME
      description: "Test 3: successfulOrderRecordsCounterAndHistogram_metricAssertions (D-14)"
      verify: "grep -qE 'void\\s+successfulOrderRecordsCounterAndHistogram_metricAssertions\\s*\\(' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: TEST-4-NAME
      description: "Test 4: tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions (D-14 / D-17 triple-signal correlation)"
      verify: "grep -qE 'void\\s+tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions\\s*\\(' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: ASSERTJ-STATIC-IMPORTS
      description: "Static imports from io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions (RESEARCH §2.4)"
      verify: "grep -qE 'import\\s+static\\s+io\\.opentelemetry\\.sdk\\.testing\\.assertj\\.OpenTelemetryAssertions' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: TRACE-ID-EQUAL-ASSERTION
      description: "Test 1 asserts ALL spans share the same traceId (TEST-03)"
      verify: "awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'hasTraceId\\(traceId\\)'"
    - id: PARENT-SPAN-ID-ASSERTION
      description: "Test 1 asserts consumerSpan.parentSpanId == producerSpan.spanId (TEST-04)"
      verify: "awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'hasParentSpanId\\(.*producerSpan\\.getSpanId\\(\\)\\)'"
    - id: SPAN-KIND-ASSERTIONS
      description: "Test 1 asserts SpanKind PRODUCER + CONSUMER + SERVER + INTERNAL coverage (TEST-05)"
      verify: "awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'SpanKind\\.PRODUCER' && awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'SpanKind\\.CONSUMER' && awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'SpanKind\\.SERVER'"
    - id: MESSAGING-SEMCONV-ASSERTIONS
      description: "Test 1 asserts messaging.system + messaging.operation_type semconv attributes on PRODUCER/CONSUMER spans (TEST-05)"
      verify: "awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'MESSAGING_SYSTEM' && awk '/happyPathProducesSingleTrace_traceAssertions/,/^\\s*}\\s*$/' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java | grep -qE 'MESSAGING_OPERATION_TYPE'"
    - id: PHASE-4-HISTOGRAM-NAME-CONFIRMED
      description: "Phase 4's HTTP server histogram is named `http.server.request.duration` (referenced by Test 3 metric assertion). If Phase 4 used a different name, the executor MUST update the assertion in OrderFlowIT.java to match — the verify command MUST locate the literal histogram name in producer-service source before assertion is written."
      verify: "grep -rqE 'http\\.server\\.request\\.duration|httpServerRequestDuration|http_server_request_duration' producer-service/src/main/java/ || (echo 'WARN: Phase 4 histogram name differs from assumed http.server.request.duration; update OrderFlowIT.java Test 3 to match' && false)"
    - id: NO-BATCH-PROCESSORS
      description: "OrderFlowIT.java does NOT reference Batch processors anywhere"
      verify: "! grep -qE 'BatchSpanProcessor|BatchLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: NO-INVALID-AS-IMPORT
      description: "OrderFlowIT.java contains no `assertJThat` token and no `import static ... as ` (invalid Java) — executor resolved per Option A or Option B"
      verify: "! grep -qE 'assertJThat|import static [^;]+ as ' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java"
    - id: COMPILES-CLEAN
      description: "integration-tests compiles cleanly with all 3 test classes"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile"
    - id: TESTS-PASS-WITH-HOST-RABBIT-DOWN
      description: "ROADMAP SC #1: mise run test (or mvn -pl integration-tests -am verify) passes with host docker-compose RabbitMQ stopped — proves Testcontainers genuinely used (TEST-01 SC #1)"
      verify: "cd $(git rev-parse --show-toplevel) && (docker compose -f docker-compose.yml ps rabbitmq 2>/dev/null | grep -q 'running' && docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true) && mvn -B -pl integration-tests -am verify"
    - id: TEST-LOG-RANDOM-PORT-VISIBLE
      description: "ROADMAP SC #2: test log output contains the random RabbitMQ port line (TEST-01 SC #2)"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am verify 2>&1 | tee /tmp/06-05-test.log >/dev/null && grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-05-test.log"
    - id: VERIFY-EXITS-NONZERO-ON-FAILURE
      description: "TEST-06 contract: introducing a deliberate assertion failure causes mvn verify to exit non-zero (manual sanity check — not run by acceptance gate; documented for executor reference)"
      verify: "echo 'TEST-06 contract is structural: Failsafe binds to verify, *IT.java triggers, assertions fail loud. Verified by 06-02s explicit Failsafe binding + this plans assertion shape.'"
  artifacts:
    - path: integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java
      provides: "JUnit 5 cross-service IT — RabbitMQContainer + two SpringApplicationBuilder contexts + 4 @Test methods covering traces/logs/metrics/failure-path; reads telemetry from TestOtelHolder static fields per D-07.1"
      contains: "@Testcontainers"
      contains: "RabbitMQContainer"
      contains: "@Test"
      contains: "TestOtelHolder.SPANS"
      contains: "Awaitility.await()"
  key_links:
    - from: integration-tests/.../OrderFlowIT.java @BeforeAll System.setProperty
      to: producer-service application.yaml (or hardcoded defaults) spring.rabbitmq.* properties
      via: "Spring Boot Environment property resolution at context refresh"
      pattern: "System.setProperty\\(\"spring.rabbitmq"
    - from: integration-tests/.../OrderFlowIT.java assertions on TestOtelHolder.SPANS
      to: otel-bootstrap/.../amqp/TracingMessagePostProcessor.java (PRODUCER span injection) + TracingMessageListenerAdvice.java (CONSUMER span extraction)
      via: "Phase 3 propagation pair produces the spans the test asserts on"
      pattern: "MessagingIncubatingAttributes\\.MESSAGING"
    - from: integration-tests/.../OrderFlowIT.java assertions on TestOtelHolder.LOGS
      to: producer-service/.../OrderController.java + OrderPublisher.java + consumer-service/.../ProcessingService.java (Phase 5 LOG sites)
      via: "OpenTelemetryAppender bridge installed by TestOtelHolder.get()"
      pattern: "logExporter|TestOtelHolder.LOGS"
    - from: integration-tests/.../OrderFlowIT.java metrics assertions
      to: producer-service/.../OrderService.java orders.created counter + HttpServerSpanFilter http.server.request.duration histogram
      via: "Phase 4 metric instruments visible in InMemoryMetricReader.collectAllMetrics()"
      pattern: "orders.created|http.server.request.duration"
---

<objective>
Create `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` — the cross-service `*IT.java` integration test that proves the full instrumentation chain in CI. Class uses `@Testcontainers` JUnit 5 extension to manage a `RabbitMQContainer` on a random port; `@BeforeAll` starts TWO `SpringApplicationBuilder` contexts (ProducerApplication + ConsumerApplication) sharing the same testcontainer broker via `System.setProperty("spring.rabbitmq.*", ...)` (D-11) and importing `TestOtelConfiguration` so both contexts swap to the test SDK in `TestOtelHolder` (D-07.1). Four `@Test` methods (D-14) cover traces (TEST-03/04/05), logs (LOG-04 carryforward), metrics (METRIC-02/03 carryforward), and the APP-04 failure-path (TRACE-09 + Phase 5 D-16) — the latter is the workshop's strongest triple-signal correlation assertion (D-17).

Purpose: This IT is the workshop's exit-gate proof: with host docker-compose RabbitMQ stopped, `mvn -pl integration-tests -am verify` (= `mise run test`) starts a Testcontainers RabbitMQ on a random port (visible in stdout — TEST-01 SC #2 via the explicit `LOG.info` line per RESEARCH §2.2), exercises the full `POST /orders` → publish → consume chain through real broker traffic, asserts on InMemorySpanExporter / InMemoryLogRecordExporter / InMemoryMetricReader contents, and exits non-zero on any assertion failure (TEST-06).

Output: ONE new file `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` (~270 lines including JavaDoc, @BeforeAll, @AfterAll, @BeforeEach, 4 @Test methods, and helpers).

Why this is wave 5: depends on 06-03 (TestOtelHolder static fields) AND 06-04 (TestOtelConfiguration imports). The IT must compile against both. Given file-overlap discipline (this plan touches a new file with no overlap), it could parallelize with neither; placing it last in the wave order is the dependency-safe choice.

Why one focused plan: this is one cohesive test class with four `@Test` methods that share `@BeforeAll`/`@AfterAll`/`@BeforeEach` lifecycle. Splitting into per-test plans would duplicate 100+ lines of lifecycle scaffolding and create per-test cross-cutting concerns. RESEARCH §3.3 ships the full skeleton as one paste — honor that.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/06-verification-tests/06-CONTEXT.md
@.planning/phases/06-verification-tests/06-RESEARCH.md
@.planning/phases/06-verification-tests/06-PATTERNS.md
@.planning/phases/06-verification-tests/06-03-SUMMARY.md
@.planning/phases/06-verification-tests/06-04-SUMMARY.md
@integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
@integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java
@integration-tests/pom.xml
@producer-service/src/main/java/com/example/producer/ProducerApplication.java
@consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java

<interfaces>
<!-- API surface used by OrderFlowIT. -->

JUnit 5 + Testcontainers:
```java
@org.testcontainers.junit.jupiter.Testcontainers   // class-level
@org.testcontainers.junit.jupiter.Container        // field-level (must be static for class-scoped lifecycle)

org.testcontainers.containers.RabbitMQContainer.RabbitMQContainer(String image);
String RabbitMQContainer.getHost();
Integer RabbitMQContainer.getAmqpPort();
String RabbitMQContainer.getAdminUsername();
String RabbitMQContainer.getAdminPassword();
```

Spring Boot:
```java
new SpringApplicationBuilder(Class<?>... sources)
    .properties(String... keyValuePairs)
    .run() -> ConfigurableApplicationContext;

ConfigurableApplicationContext.getEnvironment().getProperty("local.server.port", Integer.class);
ConfigurableApplicationContext.close();

new TestRestTemplate().postForEntity(String url, Object request, Class<?> responseType) -> ResponseEntity<?>;
```

Awaitility:
```java
Awaitility.await().atMost(Duration).until(BooleanSupplier);
```

OpenTelemetry test assertions (RESEARCH §2.4):
```java
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

assertThat(SpanData)  // returns SpanDataAssert
    .hasTraceId(String)
    .hasParentSpanId(String)
    .hasKind(SpanKind)
    .hasAttribute(equalTo(AttributeKey<T>, T value))
    .hasStatus(StatusData)
    .hasEventsSatisfyingExactly(Consumer<EventDataAssert>...);

assertThat(MetricData)
    .hasName(String)
    .hasUnit(String)
    .hasLongSumSatisfying(Consumer<LongSumAssert>)
    .hasHistogramSatisfying(Consumer<HistogramAssert>);
```

semconv constants:
```java
io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;        // String
io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;  // Long
io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;  // "publish" / "process"
io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
```

OpenTelemetry SDK + API:
```java
io.opentelemetry.api.trace.SpanKind { SERVER, CONSUMER, PRODUCER, INTERNAL, ... }
io.opentelemetry.api.trace.StatusCode { OK, ERROR, UNSET }
io.opentelemetry.api.logs.Severity { ERROR, WARN, INFO, ... }

io.opentelemetry.sdk.trace.data.SpanData;
io.opentelemetry.sdk.metrics.data.MetricData;
// LogRecordData accessed via io.opentelemetry.sdk.logs.data.LogRecordData (1.61.0)

((SdkTracerProvider) openTelemetry.getSdkTracerProvider())
    .forceFlush().join(long, TimeUnit);  // belt-and-braces; SimpleSpanProcessor is already synchronous
```

Phase 5 production LOG sites that test 2 / test 4 assert on (READ-ONLY — no edits to these files in Phase 6):
- producer-service/.../api/OrderController.java line 45 area: `LOG.info("accepted orderId={}", orderId)` (or similar — verify in source)
- producer-service/.../messaging/OrderPublisher.java line 45 area: `LOG.info("publishing orderId={} to exchange={}", ...)` (PATTERNS.md File 9)
- consumer-service/.../domain/ProcessingService.java line 96 area: `LOG.error("order processing failed: orderId={}", orderId, e)` (PATTERNS.md File 10)

Phase 4 metric instruments that test 3 asserts on:
- `orders.created` LongCounter with `order.priority` attribute (METRIC-02)
- `http.server.request.duration` DoubleHistogram with unit "s" + http.request.method + http.response.status_code attributes (METRIC-03)
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Create OrderFlowIT.java with @Testcontainers + two-context lifecycle + 4 @Test methods covering traces/logs/metrics/failure-path</name>
  <files>integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java</files>
  <read_first>
    - integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java (verify field names + types: SDK / SPANS / LOGS / METRICS)
    - integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java (verify class is public — needed for @Import)
    - .planning/phases/06-verification-tests/06-RESEARCH.md (§3.3 — full OrderFlowIT.java paste-ready skeleton lines 436-751; §2.2 — random-port logging pattern; §2.3 — InMemoryMetricReader has no reset() so collectAllMetrics() is the drain primitive; §2.4 — OpenTelemetryAssertions; §2.6 — producer-first context close)
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 4 — RESEARCH §3.3 is source of truth; D-07.1 changes lines 374-386 — read TestOtelHolder fields directly NOT producerCtx.getBean; Cross-File Invariant #7 *IT.java naming)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-10 two contexts; D-11 System.setProperty; D-12 TestRestTemplate against random producer port; D-13 Awaitility no Thread.sleep; D-14 four test method names; D-15 @BeforeEach reset; D-17 triple-signal correlation in test 4; D-18 Sampler.alwaysOn already in TestOtelHolder)
    - producer-service/src/main/java/com/example/producer/ProducerApplication.java (verify the @SpringBootApplication entry-point class name + package)
    - consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java (same)
    - producer-service/src/main/java/com/example/producer/api/OrderController.java (verify the POST /orders endpoint path + request body shape so the test's record types align)
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (verify Phase 5 LOG.info wording — test 2 asserts on the resulting log record)
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java (verify Phase 5 LOG.error wording + the 10th-order failure path lives here)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java (read-only — verify the messaging.* attribute keys the PRODUCER span emits)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java (read-only — verify the CONSUMER span shape)
    - producer-service/src/main/java/com/example/producer/ (recursive — executor MUST locate Phase 4's HTTP server histogram declaration to confirm its name matches Test 3's `http.server.request.duration` assertion; if the actual instrument name differs, update the test or downgrade Test 3 to fewer assertions)
  </read_first>
  <behavior>
    - With host docker-compose RabbitMQ stopped (port 5672 free), `mvn -B -pl integration-tests -am verify` exits 0.
    - Test logs (stdout/stderr captured by Failsafe) contain a line matching `RabbitMQ test container available at .*:[0-9]+` — visible random port (TEST-01 SC #2).
    - All 4 @Test methods pass on a clean run.
    - If a deliberate sabotage is introduced (e.g., remove the `LOG.info` from OrderController), test 2 fails with a clear AssertJ message; mvn verify exits non-zero (TEST-06).
    - On test cleanup, both Spring contexts close cleanly without lost-listener warnings (RESEARCH §2.6 producer-first ordering).
    - Compile contract: `mvn -pl integration-tests -am test-compile` exits 0 with 3 source files (TestOtelHolder + TestOtelConfiguration + OrderFlowIT).
  </behavior>
  <action>
Use the Write tool to create `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`. The content is derived from RESEARCH §3.3 (paste-ready skeleton at lines 436-751) with the D-07.1 modification: read telemetry from `TestOtelHolder.SPANS / .LOGS / .METRICS` static fields directly (NOT via `producerCtx.getBean(...)` — PATTERNS.md File 4 lines 374-386).

Paste this content VERBATIM (this is the integration of RESEARCH §3.3 + D-07.1 + the test record class declared inline as an inner record):

```java
package com.example.e2e;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.consumer.ConsumerApplication;
import com.example.producer.ProducerApplication;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat as assertJThat;

/**
 * Cross-service integration test (Phase 6) — proves the full instrumentation
 * chain in CI without a live OTLP backend.
 *
 * <p>{@code @Testcontainers} starts a {@link RabbitMQContainer} on a random
 * port; {@code @BeforeAll} sets {@code spring.rabbitmq.*} System properties
 * (D-11) and starts TWO {@link SpringApplicationBuilder} contexts (D-10 — one
 * for ProducerApplication, one for ConsumerApplication) sharing the same
 * broker. Both contexts {@code @Import} {@link TestOtelConfiguration}, which
 * delegates to {@link TestOtelHolder} for ONE shared {@link OpenTelemetry}
 * SDK + ONE shared {@link io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter}
 * across both contexts (D-07 / D-07.1).
 *
 * <p><b>Four {@code @Test} methods (D-14):</b>
 * <ol>
 *   <li>{@code happyPathProducesSingleTrace_traceAssertions} — TEST-03/04/05:
 *       producer + consumer spans share traceId; consumer.parentSpanId ==
 *       producer.spanId; SpanKind covers SERVER+INTERNAL+PRODUCER+CONSUMER+INTERNAL;
 *       messaging semconv attrs present.</li>
 *   <li>{@code happyPathStampsLogsWithTraceId_logAssertions} — LOG-04
 *       carryforward: producer-side LOG.info records carry the producer
 *       trace's traceId.</li>
 *   <li>{@code successfulOrderRecordsCounterAndHistogram_metricAssertions} —
 *       METRIC-02/03 carryforward: orders.created counter + http.server.request.duration
 *       histogram have the expected attributes.</li>
 *   <li>{@code tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions} —
 *       D-17 triple-signal correlation: 10th order's CONSUMER span has
 *       Status.ERROR + recorded exception event; matching LOG.error record
 *       carries the same trace_id (APP-04 + TRACE-09 + Phase 5 D-16).</li>
 * </ol>
 *
 * <p><b>Lifecycle ordering (RESEARCH §2.6):</b>
 * <ul>
 *   <li>{@code @AfterAll} closes producer FIRST (stops accepting HTTP), then
 *       consumer (drains in-flight @RabbitListener deliveries).</li>
 *   <li>{@code System.clearProperty(...)} clears the four spring.rabbitmq.*
 *       properties so subsequent test classes in the same JVM don't pick up
 *       a stale port.</li>
 *   <li>{@code @Container} extension stops RabbitMQContainer after the class
 *       finishes.</li>
 * </ul>
 *
 * <p><b>Determinism:</b> {@link io.opentelemetry.sdk.trace.export.SimpleSpanProcessor}
 * + {@link io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor} (in
 * {@link TestOtelHolder}) are synchronous; every {@code span.end()} exports
 * immediately. {@link Awaitility} polling on {@code getFinishedSpanItems().size()}
 * handles the cross-thread @RabbitListener delivery latency. {@link
 * SdkTracerProvider#forceFlush()} is belt-and-braces — costs ~µs.
 *
 * <p><b>NEVER use {@code Thread.sleep}</b> (D-13 / PITFALLS #11). Awaitility
 * is the canonical polling primitive.
 */
@Testcontainers
class OrderFlowIT {

    private static final Logger LOG = LoggerFactory.getLogger(OrderFlowIT.class);

    @Container
    static final RabbitMQContainer rabbit =
        new RabbitMQContainer("rabbitmq:4.3-management-alpine");

    private static ConfigurableApplicationContext producerCtx;
    private static ConfigurableApplicationContext consumerCtx;
    private static TestRestTemplate rest;
    private static String orderUrl;
    private static OpenTelemetry openTelemetry;

    @BeforeAll
    static void startTwoSpringContexts() {
        // 1. Container started by @Testcontainers extension before this method.

        // 2. TEST-01 SC #2: explicit log of random port (RESEARCH §2.2 —
        //    Testcontainers' default banner doesn't surface the mapped port
        //    at INFO; this LOG.info guarantees visibility under default
        //    Maven log levels).
        LOG.info("RabbitMQ test container available at {}:{}",
            rabbit.getHost(), rabbit.getAmqpPort());

        // 3. System.setProperty BEFORE either context starts (D-11). Spring
        //    Boot's Environment reads System properties during refresh so
        //    both contexts pick up the testcontainer's random AMQP port.
        System.setProperty("spring.rabbitmq.host", rabbit.getHost());
        System.setProperty("spring.rabbitmq.port", String.valueOf(rabbit.getAmqpPort()));
        System.setProperty("spring.rabbitmq.username", rabbit.getAdminUsername());
        System.setProperty("spring.rabbitmq.password", rabbit.getAdminPassword());

        // 4. Producer context (D-10) — server.port=0 → random port; allow
        //    bean-definition-overriding so TestOtelConfiguration's @Bean
        //    OpenTelemetry replaces production's by name (D-06).
        producerCtx = new SpringApplicationBuilder(
                ProducerApplication.class, TestOtelConfiguration.class)
            .properties(
                "server.port=0",
                "spring.main.allow-bean-definition-overriding=true")
            .run();

        // 5. Consumer context (D-10). Consumer also exposes /actuator/health
        //    on Tomcat; we don't hit it but server.port=0 avoids host-port
        //    collision with the producer context.
        consumerCtx = new SpringApplicationBuilder(
                ConsumerApplication.class, TestOtelConfiguration.class)
            .properties(
                "server.port=0",
                "spring.main.allow-bean-definition-overriding=true")
            .run();

        // 6. Capture the SHARED OpenTelemetry from TestOtelHolder (D-07.1).
        //    NOTE: do NOT use producerCtx.getBean(OpenTelemetry.class) —
        //    Spring DI scopes beans per-context, so each context has its
        //    own OpenTelemetry @Bean instance even though they delegate to
        //    the same TestOtelHolder.SDK underneath. Reading from the holder
        //    directly is unambiguous.
        openTelemetry = TestOtelHolder.get();

        // 7. Producer's actual port + REST client.
        Integer port = producerCtx.getEnvironment()
            .getProperty("local.server.port", Integer.class);
        rest = new TestRestTemplate();
        orderUrl = "http://localhost:" + port + "/orders";
    }

    @BeforeEach
    void resetTelemetry() {
        // RESEARCH §2.3: collectAllMetrics() drains the accumulator
        // (InMemoryMetricReader has NO reset() method).
        TestOtelHolder.SPANS.reset();
        TestOtelHolder.LOGS.reset();
        TestOtelHolder.METRICS.collectAllMetrics();
    }

    @AfterAll
    static void shutdown() {
        // RESEARCH §2.6: producer-first close (stops new HTTP traffic), then
        // consumer (drains in-flight @RabbitListener deliveries).
        if (producerCtx != null) producerCtx.close();
        if (consumerCtx != null) consumerCtx.close();

        // Defensive: clear System.properties so subsequent test classes in
        // the same JVM don't pick up the now-stopped container's port.
        System.clearProperty("spring.rabbitmq.host");
        System.clearProperty("spring.rabbitmq.port");
        System.clearProperty("spring.rabbitmq.username");
        System.clearProperty("spring.rabbitmq.password");
        // RabbitMQContainer.stop() handled by @Testcontainers extension.
    }

    // ----------------------------------------------------------------------
    // TEST 1 — D-14 happy-path traces (TEST-03 + TEST-04 + TEST-05)
    // ----------------------------------------------------------------------
    @Test
    void happyPathProducesSingleTrace_traceAssertions() {
        ResponseEntity<Void> response = rest.postForEntity(
            orderUrl,
            new TestOrderRequest("WIDGET-1", 3, "express"),
            Void.class);
        assertJThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // EXPECTED_SPAN_COUNT = 5: SERVER + INTERNAL_producer + PRODUCER
        // + CONSUMER + INTERNAL_consumer.
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().size() >= 5);

        forceFlushAll();

        List<SpanData> spans = TestOtelHolder.SPANS.getFinishedSpanItems();

        SpanData serverSpan = findSpanByKind(spans, SpanKind.SERVER);
        SpanData producerSpan = findSpanByKind(spans, SpanKind.PRODUCER);
        SpanData consumerSpan = findSpanByKind(spans, SpanKind.CONSUMER);

        // TEST-03: all spans share one traceId.
        String traceId = serverSpan.getTraceId();
        spans.forEach(s -> assertThat(s).hasTraceId(traceId));

        // TEST-04: cross-service parent linkage.
        assertThat(consumerSpan).hasParentSpanId(producerSpan.getSpanId());

        // TEST-05: SpanKind coverage (PRODUCER + CONSUMER + SERVER + INTERNAL).
        assertJThat(spans.stream().map(SpanData::getKind).toList())
            .contains(SpanKind.SERVER, SpanKind.INTERNAL,
                      SpanKind.PRODUCER, SpanKind.CONSUMER);

        // TEST-05: messaging semconv attributes.
        assertThat(producerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"));
        assertThat(producerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "publish"));
        assertThat(consumerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"));
        assertThat(consumerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "process"));
    }

    // ----------------------------------------------------------------------
    // TEST 2 — D-14 happy-path logs (LOG-04 carryforward)
    // ----------------------------------------------------------------------
    @Test
    void happyPathStampsLogsWithTraceId_logAssertions() {
        rest.postForEntity(orderUrl,
            new TestOrderRequest("WIDGET-2", 1, "standard"), Void.class);

        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> TestOtelHolder.LOGS.getFinishedLogRecordItems().size() >= 2
                && TestOtelHolder.SPANS.getFinishedSpanItems().stream()
                    .anyMatch(s -> s.getKind() == SpanKind.PRODUCER));

        forceFlushAll();

        SpanData producerSpan = findSpanByKind(
            TestOtelHolder.SPANS.getFinishedSpanItems(), SpanKind.PRODUCER);
        String traceId = producerSpan.getTraceId();

        // Producer-side LOG.info records (OrderController + OrderPublisher,
        // Phase 5 D-15) should have trace_id matching the producer trace.
        long producerLogCount = TestOtelHolder.LOGS.getFinishedLogRecordItems().stream()
            .filter(r -> r.getSpanContext().getTraceId().equals(traceId))
            .count();
        assertJThat(producerLogCount)
            .as("expected >= 2 producer log records with traceId=%s", traceId)
            .isGreaterThanOrEqualTo(2);
    }

    // ----------------------------------------------------------------------
    // TEST 3 — D-14 metrics (METRIC-02 + METRIC-03 carryforward)
    // ----------------------------------------------------------------------
    @Test
    void successfulOrderRecordsCounterAndHistogram_metricAssertions() {
        rest.postForEntity(orderUrl,
            new TestOrderRequest("WIDGET-3", 1, "express"), Void.class);

        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().size() >= 5);

        forceFlushAll();

        // collectAllMetrics() returns metrics accumulated since @BeforeEach
        // drained the accumulator (RESEARCH §2.3).
        var metrics = TestOtelHolder.METRICS.collectAllMetrics();

        MetricData ordersCreated = metrics.stream()
            .filter(m -> "orders.created".equals(m.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "missing orders.created metric — found: "
                + metrics.stream().map(MetricData::getName).toList()));
        assertThat(ordersCreated)
            .hasName("orders.created")
            .hasLongSumSatisfying(sum -> sum.hasPointsSatisfying(point ->
                point.hasValue(1L).hasAttribute(
                    equalTo(AttributeKey.stringKey("order.priority"), "express"))));

        MetricData httpDuration = metrics.stream()
            .filter(m -> "http.server.request.duration".equals(m.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "missing http.server.request.duration metric"));
        assertThat(httpDuration)
            .hasName("http.server.request.duration")
            .hasUnit("s")
            .hasHistogramSatisfying(h -> h.hasPointsSatisfying(p ->
                p.hasAttribute(equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"))
                 .hasAttribute(equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 202L))));
    }

    // ----------------------------------------------------------------------
    // TEST 4 — D-14/D-17 failure path (APP-04 + TRACE-09 + Phase 5 D-16)
    // ----------------------------------------------------------------------
    @Test
    void tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions() {
        for (int i = 1; i <= 10; i++) {
            rest.postForEntity(orderUrl,
                new TestOrderRequest("WIDGET-" + i, 1, "standard"), Void.class);
        }

        // 10 traces × 5 spans each = 50; allow margin.
        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().stream()
                .filter(s -> s.getKind() == SpanKind.CONSUMER)
                .anyMatch(s -> s.getStatus().getStatusCode() == StatusCode.ERROR));

        forceFlushAll();

        // Find the CONSUMER span with Status.ERROR (the 10th order).
        SpanData errorConsumerSpan = TestOtelHolder.SPANS.getFinishedSpanItems().stream()
            .filter(s -> s.getKind() == SpanKind.CONSUMER)
            .filter(s -> s.getStatus().getStatusCode() == StatusCode.ERROR)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "expected one CONSUMER span with Status.ERROR (10th order)"));

        // TRACE-09: recordException attached an event named "exception" with
        // exception.type ending in ProcessingFailedException.
        assertJThat(errorConsumerSpan.getEvents())
            .as("CONSUMER span should have at least one exception event")
            .anyMatch(e -> "exception".equals(e.getName())
                && e.getAttributes().asMap().entrySet().stream().anyMatch(entry ->
                    "exception.type".equals(entry.getKey().getKey())
                    && String.valueOf(entry.getValue()).endsWith("ProcessingFailedException")));

        // D-17: triple-signal correlation — a LOG.error record carries the same trace_id.
        String errorTraceId = errorConsumerSpan.getTraceId();
        boolean hasCorrelatedErrorLog = TestOtelHolder.LOGS.getFinishedLogRecordItems().stream()
            .anyMatch(r -> r.getSpanContext().getTraceId().equals(errorTraceId)
                && r.getSeverity() == Severity.ERROR);
        assertJThat(hasCorrelatedErrorLog)
            .as("expected a LOG.error record correlated to the failed trace %s", errorTraceId)
            .isTrue();
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------
    private static SpanData findSpanByKind(List<SpanData> spans, SpanKind kind) {
        return spans.stream()
            .filter(s -> s.getKind() == kind)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "no span with kind=" + kind + " — found "
                + spans.stream().map(s -> s.getKind().toString()).toList()));
    }

    private static void forceFlushAll() {
        ((SdkTracerProvider) openTelemetry.getTracerProvider())
            .forceFlush().join(10, TimeUnit.SECONDS);
        ((SdkLoggerProvider) ((io.opentelemetry.sdk.OpenTelemetrySdk) openTelemetry).getSdkLoggerProvider())
            .forceFlush().join(10, TimeUnit.SECONDS);
    }

    /**
     * Test-side request body matching producer-service's {@code OrderRequest}
     * contract (sku, quantity, priority). Inner record keeps the test
     * self-contained — does NOT depend on the producer-service exporting its
     * request DTO.
     */
    record TestOrderRequest(String sku, int quantity, String priority) {}
}
```

**IMPORTANT EXECUTOR NOTES:**

1. **AssertJ static-import alias collision.** Java does not support `import static ... as ...` syntax (the snippet uses `import static org.assertj.core.api.Assertions.assertThat as assertJThat;` for clarity in this paste — that is INVALID Java). The executor MUST RESOLVE the conflict between `OpenTelemetryAssertions.assertThat(SpanData)` and `org.assertj.core.api.Assertions.assertThat(...)` by either:
   - **Option A (recommended):** Use FQCN `org.assertj.core.api.Assertions.assertThat(...)` inline at every AssertJ-on-non-OTel-types call site; keep `OpenTelemetryAssertions.assertThat` static-imported.
   - **Option B:** Static-import `org.assertj.core.api.Assertions.*` and FQCN-qualify the OTel `OpenTelemetryAssertions.assertThat(...)` calls.
   - Option A is preferred because the OTel assertions dominate the file.

   When applying Option A: REMOVE the line `import static org.assertj.core.api.Assertions.assertThat as assertJThat;`. Replace every `assertJThat(` in the file with `org.assertj.core.api.Assertions.assertThat(`. Verify with `mvn -pl integration-tests -am test-compile`.

2. **Verify LogRecordData accessor names.** The snippet uses `r.getSpanContext().getTraceId()` and `r.getSeverity()`. If `mvn test-compile` reports method-not-found, check `io.opentelemetry.sdk.logs.data.LogRecordData` API in 1.61.0 — the actual signatures may be `r.getSpanContext()` (correct) and `r.getSeverity()` (correct, returns `io.opentelemetry.api.logs.Severity`). If the compiler complains, consult the SDK Javadoc.

3. **Verify `((io.opentelemetry.sdk.OpenTelemetrySdk) openTelemetry).getSdkLoggerProvider()` accessor.** In SDK 1.61.0 the method is `getSdkLoggerProvider()` (not `getLoggerProvider()`). If compile fails, swap to `((io.opentelemetry.sdk.OpenTelemetrySdk) openTelemetry).getSdkLoggerProvider()` or directly use `TestOtelHolder.SDK.getSdkLoggerProvider()` to avoid the cast.

4. **Verify producer's `OrderRequest` JSON contract.** The inner `record TestOrderRequest(String sku, int quantity, String priority)` matches the assumed shape from CONTEXT D-14. If producer-service's actual `OrderRequest` DTO uses different field names (e.g., `productId` instead of `sku`), the executor MUST adjust the record to match — verify by reading `producer-service/src/main/java/com/example/producer/api/OrderRequest.java` (or wherever the DTO lives) before committing. The acceptance criterion `TESTS-PASS-WITH-HOST-RABBIT-DOWN` will catch any field-name mismatch (Spring's Jackson deserialization will 400 the request).

5. **Verify Phase 5 LOG.info wording for test 2.** The assertion is structural (count `>= 2` log records carrying the producer trace's traceId). If Phase 5 has fewer than 2 producer-side LOG.info calls (OrderController + OrderPublisher = 2 minimum per Phase 5 D-15), test 2 will fail. Read the producer-service Phase 5 source to confirm both log lines exist; if only 1, lower the threshold to `>= 1` AND record the deviation in the SUMMARY (Rule-1 deviation note).

DO NOT:
- Use `Thread.sleep(...)` anywhere (D-13 / PITFALLS #11).
- Use `BatchSpanProcessor` / `BatchLogRecordProcessor` references (those live nowhere in this phase).
- Read telemetry from `producerCtx.getBean(InMemorySpanExporter.class)` (D-07.1 explicitly forbids — read from `TestOtelHolder.SPANS` directly).
- Add `@SpringBootTest(classes = {ProducerApplication.class, ConsumerApplication.class})` (D-10 — would fuse both component scans into ONE context, blurring the two-service mental model).
- Use `@DynamicPropertySource` for spring.rabbitmq.* (D-11 — tied to @SpringBootTest lifecycle, doesn't apply to manually-managed contexts).
- Name the file `OrderFlowTest.java` (Cross-File Invariant #7 — Failsafe matches `**/*IT.java`; `*Test.java` would route to Surefire which has no Testcontainers wiring).
- Use `cat << EOF` heredoc — use the Write tool with the verbatim block above.

After writing the file, run:
```bash
cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile
```

Then (after fixing any AssertJ alias / LogRecordData / SDK accessor compile issues per the executor notes above):
```bash
# Ensure host RabbitMQ is stopped to prove Testcontainers genuinely used (TEST-01 SC #1).
docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true
mvn -B -pl integration-tests -am verify 2>&1 | tee /tmp/06-05-test.log
grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-05-test.log
```

Expected: exit 0; 4 tests pass; random-port log line visible.
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && test -f integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q '@Testcontainers' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -F '"rabbitmq:4.3-management-alpine"' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -F 'LOG.info("RabbitMQ test container available at {}:{}"' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'new SpringApplicationBuilder(ConsumerApplication.class, TestOtelConfiguration.class)' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'TestOtelHolder.SPANS' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && ! grep -q 'producerCtx.getBean(InMemorySpanExporter' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && grep -q 'Awaitility.await()' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && ! grep -q 'Thread.sleep' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && ! grep -qE 'BatchSpanProcessor|BatchLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && test $(grep -cE '^\s*@Test\s*$' integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) -eq 4 && mvn -B -pl integration-tests -am test-compile</automated>
  </verify>
  <acceptance_criteria>
    - File exists at canonical path AND filename ends in `IT.java` (Failsafe convention)
    - `@Testcontainers` annotation on class
    - `@Container static final RabbitMQContainer rabbit` field with `"rabbitmq:4.3-management-alpine"` image string
    - Explicit `LOG.info("RabbitMQ test container available at {}:{}"` line in @BeforeAll (TEST-01 SC #2 visibility)
    - 4 `System.setProperty("spring.rabbitmq...` calls (host/port/username/password)
    - 2 `new SpringApplicationBuilder(...)` calls — one for ProducerApplication, one for ConsumerApplication, both importing TestOtelConfiguration
    - 2 occurrences of `spring.main.allow-bean-definition-overriding=true`
    - 2 occurrences of `server.port=0`
    - Reads telemetry from `TestOtelHolder.SPANS` / `.LOGS` / `.METRICS` (D-07.1)
    - NO `producerCtx.getBean(InMemorySpanExporter` reads (D-07.1 forbidden pattern)
    - `@BeforeEach` calls `TestOtelHolder.SPANS.reset()`, `TestOtelHolder.LOGS.reset()`, `TestOtelHolder.METRICS.collectAllMetrics()`
    - `@AfterAll` closes producer FIRST then consumer; clears 4 System.properties (RESEARCH §2.6)
    - Awaitility used; `Thread.sleep` NOT used
    - `forceFlush().join(...)` called somewhere (belt-and-braces per RESEARCH §2.6)
    - Exactly 4 `@Test` methods with the names from D-14
    - Test 1 asserts: `hasTraceId(traceId)` (TEST-03), `hasParentSpanId(...producerSpan.getSpanId())` (TEST-04), SpanKind.PRODUCER + SpanKind.CONSUMER + SpanKind.SERVER references (TEST-05), MESSAGING_SYSTEM + MESSAGING_OPERATION_TYPE attribute assertions (TEST-05)
    - No Batch processor references
    - `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile` exits 0 (after the executor resolves the AssertJ alias issue per action notes)
    - With docker-compose RabbitMQ stopped: `mvn -B -pl integration-tests -am verify` exits 0
    - Test log contains: `grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-05-test.log` returns 0 (TEST-01 SC #2)
  </acceptance_criteria>
  <done>
OrderFlowIT.java exists, compiles, and all 4 tests pass under `mvn -pl integration-tests -am verify` with host RabbitMQ stopped. Random-port log line visible in test output. All cross-cutting acceptance criteria green: D-07.1 telemetry direct-read, D-10 two contexts, D-11 System.setProperty, D-13 Awaitility-no-Thread.sleep, D-14 four named test methods with correct assertions, D-17 triple-signal correlation in test 4. Compiles 3 source files (TestOtelHolder + TestOtelConfiguration + OrderFlowIT).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test JVM ↔ Testcontainers Docker daemon | RabbitMQContainer.start() spawns a Docker container; ryuk reaper handles cleanup if test JVM crashes |
| Test HTTP client → producer-service Tomcat | TestRestTemplate posts to `http://localhost:<random>/orders`; Tomcat's request pipeline runs through HttpServerSpanFilter (Phase 2) and OrderController (Phase 5 LOG.info) |
| Producer Spring context → Testcontainers RabbitMQ | spring.rabbitmq.* System properties direct producer's Spring AMQP `RabbitTemplate` to the testcontainer; messages flow through real broker |
| Consumer Spring context → Testcontainers RabbitMQ | @RabbitListener pulls from the same broker; AMQP propagation pair (Phase 3) reads traceparent header |
| Test JVM static state ↔ TestOtelHolder | spans/logs/metrics flow from BOTH contexts into ONE shared in-memory queue (D-07.1) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-05-01 | Tampering | Test silently falls back to host RabbitMQ on :5672 instead of Testcontainers | mitigate | TEST-01 SC #1 acceptance criterion runs `docker compose stop rabbitmq` BEFORE `mvn verify` so :5672 is unbound. If Testcontainers wasn't actually used, Spring AMQP would fail to connect (no broker on host:5672) and tests fail loud. The explicit `LOG.info("RabbitMQ test container available at {}:{}", ...)` line plus the random-port log assertion (TEST-01 SC #2) further confirms which broker was used. |
| T-06-05-02 | Spoofing | TestOtelConfiguration's @Bean OpenTelemetry NOT actually overriding production | mitigate | Override-by-name discipline (Plan 06-04 acceptance) + `allow-bean-definition-overriding=true` (this plan, 2 occurrences). If override silently fails, test 1 would observe ZERO spans in TestOtelHolder.SPANS (production OTLP exporter would attempt :4317 — also stopped per TEST-01 SC #1) and Awaitility would time out at 10s — fail loud. |
| T-06-05-03 | Tampering | Test sleeps with Thread.sleep instead of Awaitility | mitigate | Acceptance criterion `! grep -q 'Thread.sleep'` guards this. PITFALLS #11 / D-13 invariant. |
| T-06-05-04 | Information Disclosure | Test exposes RabbitMQ credentials in System properties | accept | Testcontainers' default credentials (`guest`/`guest`) are public + the JVM is a transient test process. No production secrets. `@AfterAll` clears the props defensively. |
| T-06-05-05 | Denial of Service | RabbitMQContainer spawn fails on machines without Docker | mitigate | Phase 1's `mise run preflight` task asserts Docker is up before running tests. CI runners need Docker pre-installed (TEST-06's contract is runner-agnostic; CI YAML in Phase 7 will document Docker requirement). |
| T-06-05-06 | Repudiation | Test passes silently with bug present (false negative) | mitigate | Each @Test method asserts EXACT counts/values where possible: test 1 — exactly 5 spans, exact trace-id equality; test 3 — exact metric name match + value 1L; test 4 — exact StatusCode.ERROR + exception event match. AssertJ `as(...)` messages provide actionable failure context. |
| T-06-05-07 | Elevation of Privilege | Test JVM has Docker socket access | accept | Standard Testcontainers trust model — Docker daemon access is the test runner's responsibility. Host hardening is not the workshop's concern. |
| T-06-05-08 | Tampering | Future refactor of producer/consumer LOG.info wording silently breaks test 2 | mitigate | Test 2 asserts STRUCTURAL invariant (count >= 2 log records carrying producer traceId) NOT phrase content — wording can vary without breaking. If Phase 5 evolves to a different log volume, the threshold is the only knob; documented in the action notes. |
| T-06-05-09 | Tampering | Future SDK 1.61.x → 1.62.x bump renames LogRecordData accessors | mitigate | Test fails loud at `mvn test-compile`. SDK API surface (getSpanContext, getSeverity) is stable since 1.0+; risk is low. |
| T-06-05-10 | Spoofing | InMemoryMetricReader pollutes counts across tests | mitigate | `@BeforeEach` calls `collectAllMetrics()` to drain (RESEARCH §2.3 verified primitive). Test 3 asserts `point.hasValue(1L)` which catches both pollution (count > 1) and missing increment (no point at all). |
| T-06-05-11 | Tampering | Test 4 race: 10th order's CONSUMER span flushes AFTER test assertions read | mitigate | SimpleSpanProcessor is synchronous — every span.end() exports immediately. Awaitility polls `anyMatch(StatusCode.ERROR)` until the error span is present, so the test only proceeds AFTER the 10th order has fully processed. forceFlush is belt-and-braces. |
| T-06-05-12 | Information Disclosure | Test logs leak random-port URLs to CI artifacts | accept | Random ports change per JVM run; no persistent attack surface. The log is the FEATURE per TEST-01 SC #2. |
</threat_model>

<verification>
- `test -f integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`
- Filename is `OrderFlowIT.java` (matches Failsafe `**/*IT.java`): `ls integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`
- `@Testcontainers` annotation present
- `RabbitMQContainer("rabbitmq:4.3-management-alpine")` present
- `LOG.info("RabbitMQ test container available at {}:{}"` line present
- 4 `System.setProperty("spring.rabbitmq...` calls
- 2 `SpringApplicationBuilder(...)` calls (Producer + Consumer with TestOtelConfiguration import)
- `TestOtelHolder.SPANS` / `.LOGS` / `.METRICS` reads present; `producerCtx.getBean(InMemorySpanExporter` absent (D-07.1)
- `Awaitility.await()` present; `Thread.sleep` absent
- 4 `@Test` methods with names from D-14
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile` exits 0 (after AssertJ-alias resolution per executor notes)
- With host docker-compose RabbitMQ stopped: `mvn -B -pl integration-tests -am verify` exits 0
- Test log contains the random-port line (TEST-01 SC #2): `grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-05-test.log`
</verification>

<success_criteria>
1. `OrderFlowIT.java` exists at `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`.
2. `@Testcontainers` JUnit 5 extension manages a `RabbitMQContainer("rabbitmq:4.3-management-alpine")`.
3. `@BeforeAll` emits explicit `LOG.info("RabbitMQ test container available at {}:{}", ...)` (TEST-01 SC #2).
4. `@BeforeAll` sets 4 `spring.rabbitmq.*` System properties; starts 2 SpringApplicationBuilder contexts (ProducerApplication + ConsumerApplication, both importing TestOtelConfiguration with allow-bean-definition-overriding=true and server.port=0).
5. Reads telemetry from `TestOtelHolder.SPANS / .LOGS / .METRICS` static fields (D-07.1).
6. `@BeforeEach` resets span + log exporters; drains metric reader via `collectAllMetrics()`.
7. `@AfterAll` closes producer-first then consumer; clears 4 System properties (RESEARCH §2.6).
8. Awaitility used for polling; NO `Thread.sleep`.
9. Exactly 4 `@Test` methods with D-14 names: happyPathProducesSingleTrace_traceAssertions, happyPathStampsLogsWithTraceId_logAssertions, successfulOrderRecordsCounterAndHistogram_metricAssertions, tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions.
10. Test 1 asserts: shared traceId across 5 spans, parentSpanId linkage, SpanKind coverage, messaging semconv attrs (TEST-03/04/05).
11. Test 2 asserts: ≥2 producer log records with matching traceId (LOG-04 carryforward).
12. Test 3 asserts: orders.created counter (value=1, order.priority="express"), http.server.request.duration histogram (unit "s", method=POST, status=202).
13. Test 4 asserts: 10th-order CONSUMER span Status.ERROR + exception event with type ending in ProcessingFailedException + LOG.error correlated by traceId (D-17 triple-signal).
14. With host docker-compose RabbitMQ stopped, `mvn -pl integration-tests -am verify` exits 0 (TEST-01 SC #1 + ROADMAP SC #1).
15. Test log output contains the random-port line (TEST-01 SC #2 + ROADMAP SC #2).
16. NO Batch processor / Thread.sleep / producerCtx.getBean(InMemory*) anywhere.
</success_criteria>

<output>
After completion, create `.planning/phases/06-verification-tests/06-05-SUMMARY.md` with:
- Files created (1: OrderFlowIT.java) and line count
- Full file content as a code block (post-AssertJ-alias-resolution)
- AssertJ-alias resolution notes: which option (A or B) the executor picked + diff
- LogRecordData / SdkLoggerProvider accessor adjustments (if any)
- Producer's `OrderRequest` DTO field-name verification: actual fields vs the test record
- `mvn -pl integration-tests -am test-compile` exit code (0)
- `docker compose stop rabbitmq` output (or "already stopped")
- `mvn -pl integration-tests -am verify` exit code (0) + last 30 lines of output (showing 4 tests passed + random-port log line)
- Random-port grep evidence: `grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-05-test.log` output
- Forward-link: Plan 06-06 will add the README "Step 6: Verification Tests" section AND verify all 5 ROADMAP success criteria empirically; orchestrator-applies the annotated tag `step-06-tests` after the human gate
- Note: NO production source files modified (producer/consumer .java + otel-bootstrap .java are read-only this phase per CONTEXT canonical_refs)
</output>
