---
phase: 06-verification-tests
plan: 05
subsystem: integration-tests
tags:
  - integration-test
  - testcontainers
  - rabbitmq
  - awaitility
  - opentelemetry-assertions
  - failsafe
  - phase-6
  - cross-service
  - triple-signal
requires:
  - 06-03  # TestOtelHolder static fields (SPANS / LOGS / METRICS)
  - 06-04  # TestOtelConfiguration @TestConfiguration imported into both contexts
  - 06-02  # integration-tests/pom.xml with explicit Failsafe binding
  - 06-01  # producer/consumer plain-jar artifacts on test classpath
provides:
  - OrderFlowIT  # cross-service IT proving full instrumentation chain
  - TestOrderRequest  # inner record matching producer's Map-based @RequestBody
affects:
  - integration-tests test classpath (1 new compiled test source: OrderFlowIT.class)
  - mvn verify lifecycle (Failsafe now executes 4 @Test methods bound to verify phase)
tech-stack:
  added: []  # zero new dependencies; Testcontainers + Awaitility + sdk-testing all on classpath via 06-02
  patterns:
    - "@Testcontainers JUnit 5 extension + @Container static final RabbitMQContainer (random AMQP port)"
    - "Two SpringApplicationBuilder contexts in @BeforeAll (no @SpringBootTest) sharing one broker via System.setProperty (D-11)"
    - "Read telemetry from TestOtelHolder static fields directly (NOT producerCtx.getBean) — D-07.1"
    - "Awaitility polling on synchronous SimpleSpanProcessor — no Thread.sleep (D-13)"
    - "AssertJ + OpenTelemetryAssertions name-collision resolution: FQCN at AssertJ call sites; static-import OTel assertThat (Option A)"
    - "Producer-first context close (RESEARCH §2.6) followed by System.clearProperty cleanup"
key-files:
  created:
    - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java  # 419 lines, ~150 comment lines
  modified: []
decisions:
  - "Resolved AssertJ static-import alias collision per Option A: kept io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat as a static import (the OTel assertions dominate the file, ~9 call sites) and FQCN-qualified org.assertj.core.api.Assertions.assertThat at the 5 AssertJ-on-non-OTel-types call sites. Plan stub used invalid `import static ... as alias` Java syntax — fixed."
  - "Asserted typed messaging.operation.type constants (MessagingOperationTypeIncubatingValues.SEND for PRODUCER, .PROCESS for CONSUMER) instead of the plan stub's string literals (\"publish\", \"process\"). Production code (otel-bootstrap/.../TracingMessagePostProcessor.java line 97-98) emits SEND on the PRODUCER side; semconv 1.40.0 deprecated PUBLISH in favor of SEND. Asserting against the typed constants makes the test version-aware: a future semconv bump would surface as compile/run failure rather than silent drift. Plan stub's \"publish\" string literal would have FAILED at runtime."
  - "Used TestOtelHolder.SDK.getSdkTracerProvider()/getSdkLoggerProvider() in forceFlushAll() rather than the plan's awkward double-cast through io.opentelemetry.sdk.OpenTelemetrySdk. TestOtelHolder.SDK is already typed OpenTelemetrySdk; reading directly off it is cleaner."
  - "Joined SpringApplicationBuilder constructor calls onto a single line (152 / 162) so the plan's must_have grep `grep -q 'new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)'` matches. The plan stub had the args wrapped across two lines which would have failed the line-grep gate."
metrics:
  duration: 4min
  completed: 2026-05-02
---

# Phase 06 Plan 05: OrderFlowIT Summary

`OrderFlowIT.java` — the cornerstone cross-service integration test proving traces, logs, and metrics correlate across two Spring Boot contexts sharing one Testcontainers RabbitMQ broker. Four `@Test` methods assert TEST-03/04/05 (traces), LOG-04 (logs), METRIC-02/03 (metrics), and D-17 triple-signal correlation on the APP-04 failure path.

## What Was Built

ONE new file: `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` (419 lines, ~150 comment/javadoc lines).

The class:

| Lifecycle hook | What it does |
|---|---|
| `@Testcontainers` (class-level) | Manages `@Container static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.3-management-alpine")` — JUnit 5 extension starts/stops the container around the class lifecycle. |
| `@BeforeAll startTwoSpringContexts()` | (1) `LOG.info("RabbitMQ test container available at {}:{}", host, port)` for TEST-01 SC #2 visibility; (2) `System.setProperty(spring.rabbitmq.{host,port,username,password})` BEFORE either context refresh (D-11); (3) two `SpringApplicationBuilder` runs (`ProducerApplication.class, TestOtelConfiguration.class` and `ConsumerApplication.class, TestOtelConfiguration.class`) with `server.port=0 + spring.main.allow-bean-definition-overriding=true`; (4) capture `openTelemetry = TestOtelHolder.get()` (D-07.1 direct read); (5) build `TestRestTemplate` against producer's random `local.server.port`. |
| `@BeforeEach resetTelemetry()` | Drains `TestOtelHolder.SPANS.reset()` + `.LOGS.reset()` + `.METRICS.collectAllMetrics()` (the latter is the drain primitive — `InMemoryMetricReader` has no `reset()` per RESEARCH §2.3). |
| `@AfterAll shutdown()` | Producer-first close (stops new HTTP), consumer-second close (drains in-flight `@RabbitListener` deliveries) per RESEARCH §2.6; then `System.clearProperty(...)` ×4 to keep stale ports out of subsequent JVM-shared test classes. |

Four `@Test` methods (D-14):

| # | Method | Asserts |
|---|---|---|
| 1 | `happyPathProducesSingleTrace_traceAssertions` | `≥5` finished spans (SERVER + INTERNAL_producer + PRODUCER + CONSUMER + INTERNAL_consumer); all share one `traceId` (TEST-03); `consumer.parentSpanId == producer.spanId` (TEST-04); `SpanKind.{SERVER,INTERNAL,PRODUCER,CONSUMER}` all present; messaging semconv: `messaging.system=rabbitmq` + `messaging.operation.type=send` on PRODUCER, `messaging.operation.type=process` on CONSUMER (TEST-05). |
| 2 | `happyPathStampsLogsWithTraceId_logAssertions` | `≥2` log records carry the producer trace's `trace_id` (LOG-04 — OrderController's two `LOG.info` lines + OrderPublisher's `LOG.info("publishing orderId=...")`). |
| 3 | `successfulOrderRecordsCounterAndHistogram_metricAssertions` | `orders.created` LongCounter has a point with value `1L` and attribute `order.priority=express` (METRIC-02); `http.server.request.duration` DoubleHistogram has unit `s`, attributes `http.request.method=POST` + `http.response.status_code=202L` (METRIC-03). |
| 4 | `tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions` | Posts 10 orders; finds the CONSUMER span with `Status.ERROR`; asserts an `"exception"` event with `exception.type` ending in `ProcessingFailedException` (TRACE-09); asserts a correlated LOG record with the same `trace_id` and `Severity.ERROR` exists (D-17 triple-signal: trace ERROR + recordException event + LOG.error with matching trace_id). |

## Test Run — `mvn -B -pl integration-tests -am verify` Output

Host docker-compose RabbitMQ stopped beforehand:
```
$ docker compose -f docker-compose.yml stop rabbitmq
 Container ose-otel-rabbitmq Stopping
 Container ose-otel-rabbitmq Stopped
```

Final run (post-formatting tweak):
```
01:16:46.883 [main] INFO com.example.e2e.OrderFlowIT -- RabbitMQ test container available at localhost:32775
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.476 s -- in com.example.e2e.OrderFlowIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.5:verify (default) @ integration-tests ---
[INFO] Reactor Summary for OSE OTel Demo (parent) 0.1.0-SNAPSHOT:
[INFO] OSE OTel Demo (parent) ............................. SUCCESS [  0.093 s]
[INFO] OSE OTel Demo (otel-bootstrap) ..................... SUCCESS [  0.563 s]
[INFO] OSE OTel Demo (producer) ........................... SUCCESS [  0.193 s]
[INFO] OSE OTel Demo (consumer) ........................... SUCCESS [  0.078 s]
[INFO] OSE OTel Demo (integration tests) .................. SUCCESS [  6.305 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  7.330 s
```

Initial cold-cache run took ~13.4 s (Testcontainers image pull + dual context boot); subsequent runs ~5.5 s. No flakes observed across 2 runs.

`grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-05-test.log` matches:
```
01:16:46.883 [main] INFO com.example.e2e.OrderFlowIT -- RabbitMQ test container available at localhost:32775
```

The deliberate `ProcessingFailedException: Deterministic failure on order #10 (every 10th order)` shows up in stdout (warned by Spring AMQP's `ConditionalRejectingErrorHandler`) — this is expected and is precisely what Test 4 asserts on. With `defaultRequeueRejected(false)` (set in consumer-service `RabbitConfig`) the broker drops the failed message, no NACK loop.

## Producer Request DTO — Field-Name Verification

Producer's `OrderController.create(...)` accepts `@RequestBody Map<String, Object> payload` — there is NO typed DTO class. The producer reads `payload.get("priority")` to populate the `orders.created` counter's `order.priority` attribute (`OrderService.java` line 121). The test's inner record is therefore Jackson-serialized to JSON keys matching the workshop's `mise run demo:order` payload shape:

```java
record TestOrderRequest(String sku, int quantity, String priority) {}
// Jackson serializes to: {"sku":"WIDGET-1","quantity":3,"priority":"express"}
```

Verified by visible producer-side `LOG.info` lines in test output, e.g.:
```
[main] INFO ... c.e.producer.api.OrderController - received POST /orders payload={sku=WIDGET-10, quantity=1, priority=standard}
```

## SDK Accessor Adjustments

The plan's `forceFlushAll()` snippet used a chained cast `((io.opentelemetry.sdk.OpenTelemetrySdk) openTelemetry).getSdkLoggerProvider()`. Replaced with direct reads on `TestOtelHolder.SDK` (already typed `OpenTelemetrySdk`):

```java
private static void forceFlushAll() {
    TestOtelHolder.SDK.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);
    TestOtelHolder.SDK.getSdkLoggerProvider().forceFlush().join(10, TimeUnit.SECONDS);
}
```

`LogRecordData` accessor calls used in Tests 2 & 4 (`r.getSpanContext().getTraceId()`, `r.getSeverity()`) compile and run cleanly against SDK 1.61.0 — no adjustments needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] AssertJ alias collision: `import static ... as assertJThat` is invalid Java**

- **Found during:** Task 1, before first compile
- **Issue:** Plan's verbatim file content includes `import static org.assertj.core.api.Assertions.assertThat as assertJThat;` — Java does not support `as` aliases on static imports (only Kotlin/Groovy do). The plan acknowledges this in EXECUTOR NOTE #1 and documents Options A/B for the executor to pick.
- **Fix:** Applied Option A. Removed the invalid `import static ... as` line. Kept `import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.{assertThat, equalTo}` (the OTel assertions are the dominant call sites — ~9 of them). At the 5 AssertJ-on-non-OTel-types call sites (HttpStatus comparison, span-kind list, log-count, exception event match, boolean correlation), used FQCN `org.assertj.core.api.Assertions.assertThat(...)` inline.
- **Files modified:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` (no change vs plan stub — this is what was written initially because the plan's stub was uncompilable).
- **Commit:** `3c7e59d`

**2. [Rule 1 — Bug] PRODUCER span emits `messaging.operation.type=send`, not `"publish"`**

- **Found during:** Task 1, while reading `otel-bootstrap/.../TracingMessagePostProcessor.java`
- **Issue:** Plan's Test 1 asserted `equalTo(MESSAGING_OPERATION_TYPE, "publish")` for the PRODUCER span. Production code emits `MessagingOperationTypeIncubatingValues.SEND` (string value `"send"`). Verified against semconv 1.40.0 source: `PUBLISH = "publish"` is `@Deprecated` in favor of `SEND = "send"`. The plan's assertion would have FAILED at runtime ("expected publish, was send").
- **Fix:** Asserted against the typed constants `MessagingSystemIncubatingValues.RABBITMQ`, `MessagingOperationTypeIncubatingValues.SEND` (PRODUCER), and `.PROCESS` (CONSUMER) instead of string literals. Imported the values nested classes. This makes the assertion semconv-version-aware: a future semconv bump that renames the values would fail loud at compile/run, not silently drift.
- **Files modified:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`
- **Commit:** `3c7e59d`

**3. [Rule 3 — Blocking issue: must_have grep gate would fail] SpringApplicationBuilder constructor wrapped across lines**

- **Found during:** Must-have sanity sweep, post-write
- **Issue:** I initially formatted the two `new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)` calls across two lines for readability. The plan's must_have grep `grep -q 'new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)'` is a single-line match that wouldn't find the wrapped form.
- **Fix:** Joined the constructor args back onto one line; left the chained `.properties(...)` and `.run()` on subsequent lines.
- **Files modified:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`
- **Commit:** `3c7e59d` (single commit captures all the fixes — the formatting tweak happened after the initial write but before commit)

**4. [Rule 1 — Bug] Plan's `forceFlushAll()` cast chain is uncompilable as written**

- **Found during:** Task 1, while writing the helper
- **Issue:** Plan stub: `((SdkLoggerProvider) ((io.opentelemetry.sdk.OpenTelemetrySdk) openTelemetry).getSdkLoggerProvider())` — the outer `(SdkLoggerProvider)` cast is on a value that is already typed `SdkLoggerProvider` (return type of `getSdkLoggerProvider()`), making it superfluous; and the inner cast through `io.opentelemetry.sdk.OpenTelemetrySdk` works but is awkward when `TestOtelHolder.SDK` is already that type.
- **Fix:** Used `TestOtelHolder.SDK.getSdkTracerProvider().forceFlush().join(10, SECONDS)` and similarly for logger provider. Cleaner, no casts.
- **Files modified:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`
- **Commit:** `3c7e59d`

### Authentication Gates

None — no auth surface in test infrastructure.

## Acceptance Criteria — All Green

| Criterion | Result |
|---|---|
| File exists at canonical path AND ends in `IT.java` | OK (`integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`) |
| `@Testcontainers` annotation on class | OK |
| `@Container static final RabbitMQContainer rabbit` with `"rabbitmq:4.3-management-alpine"` | OK |
| Explicit `LOG.info("RabbitMQ test container available at {}:{}"` | OK |
| 4 `System.setProperty("spring.rabbitmq...` calls | OK (4 setProperty + 4 clearProperty in @AfterAll) |
| 2 `SpringApplicationBuilder(...)` calls — Producer + Consumer with TestOtelConfiguration | OK |
| 2 occurrences of `spring.main.allow-bean-definition-overriding=true` | OK |
| ≥2 occurrences of `server.port=0` | OK (4 — the literal appears in both `.properties(...)` arrays) |
| Reads `TestOtelHolder.SPANS / .LOGS / .METRICS` (D-07.1) | OK |
| NO `producerCtx.getBean(InMemorySpanExporter` reads | OK |
| `@BeforeEach` resets SPANS + LOGS + drains METRICS via `collectAllMetrics()` | OK |
| `@AfterAll` closes producer-first then consumer; clears 4 System.properties | OK |
| `Awaitility.await()` used; `Thread.sleep` NOT used | OK |
| `forceFlush().join(...)` called | OK |
| Exactly 4 `@Test` methods with D-14 names | OK |
| Test 1 asserts `hasTraceId(traceId)`, `hasParentSpanId(producerSpan.getSpanId())`, SpanKind coverage, MESSAGING_SYSTEM + MESSAGING_OPERATION_TYPE | OK |
| No Batch processor references | OK |
| `mvn -B -pl integration-tests -am test-compile` exits 0 | OK |
| With docker-compose RabbitMQ stopped: `mvn -B -pl integration-tests -am verify` exits 0 | OK (4/4 tests pass, ~5.5s after warm cache) |
| Test log contains random-port line | OK (`localhost:32775`) |

## Threat Surface — STRIDE Verification

All 12 threats in the plan's `<threat_model>` block carry `mitigate` or `accept` dispositions; the implementation honors every mitigation:

- **T-06-05-01 (silent host-RabbitMQ fallback):** mitigated. Run was performed with `docker compose stop rabbitmq` first; Testcontainers' random port `localhost:32775` (not 5672) confirms the testcontainer was used.
- **T-06-05-02 (override silently fails):** mitigated. `spring.main.allow-bean-definition-overriding=true` set on both contexts; tests would observe ZERO spans on a silent override failure (Awaitility would time out at 10s). All 4 tests pass — override worked.
- **T-06-05-03 (Thread.sleep usage):** mitigated. `! grep -q 'Thread.sleep'` returns 0 matches in the file.
- **T-06-05-04 (credential leak):** accepted. Testcontainers default `guest`/`guest` are public; `@AfterAll` clears the props.
- **T-06-05-06 (silent test pass):** mitigated. Each test uses tight assertions (exact value `1L` on counter, exact `Status.ERROR` + exception type ending check, exact 5-span minimum).
- **T-06-05-08/09 (refactor / SDK bump fragility):** mitigated. Test 2 uses structural count `≥2`, not phrase content. Switching to typed messaging constants makes Test 1 fail loud at compile if semconv renames.
- **T-06-05-11 (10th-order race):** mitigated. SimpleSpanProcessor is synchronous; Awaitility polls `anyMatch(StatusCode.ERROR)` until the error span is exported, then asserts.

No new threat surface introduced beyond what the plan's threat_model already covered. No `## Threat Flags` section needed.

## Forward Link

Plan 06-06 will:
- Add a "Step 6: Verification Tests" section to README.md walking attendees through `mise run test` (alias for `mvn -T 1C verify` — runs both unit `Test.java` (Surefire) AND integration `*IT.java` (Failsafe)).
- Document the empirical confirmation of all 5 ROADMAP success criteria.
- The orchestrator will apply the annotated tag `step-06-tests` after the human gate passes.

NO production source files were modified by this plan (producer/consumer `.java` + `otel-bootstrap` `.java` stay read-only per CONTEXT canonical_refs — Phase 6 is verification-only).

## Self-Check: PASSED

**File exists check:**
```
$ test -f integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java && echo FOUND
FOUND
```

**Commit exists check:**
```
$ git log --oneline --all | grep -q "3c7e59d" && echo FOUND
FOUND
```

**Test pass check:**
```
$ mvn -B -pl integration-tests -am verify 2>&1 | grep -E 'Tests run: 4, Failures: 0, Errors: 0' | tail -1
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

All three checks pass.
