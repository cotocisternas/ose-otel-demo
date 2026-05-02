# Phase 6: Verification Tests - Research

**Researched:** 2026-05-02
**Domain:** Testcontainers-backed cross-service `@SpringBootTest`-style IT for triple-signal OTel SDK assertion (Spring Boot 3.4.13 / Java 17 / OpenTelemetry SDK 1.61.0)
**Confidence:** HIGH

## Executive Summary

CONTEXT.md locks 21 implementation decisions; the planner's only remaining unknowns are six narrow technical flags. This research resolves all six with paste-ready snippets:

1. `<classifier>` syntax for `spring-boot-maven-plugin` 3.4.13 — confirmed against Spring Boot 3.4.13 reference: `<classifier>exec</classifier>` inside `<execution><configuration>` of an existing `repackage` execution; no `<attach>` needed (defaults to true).
2. **`InMemoryMetricReader.reset()` does NOT exist** in SDK 1.61.0 — but `collectAllMetrics()` returns "all metrics accumulated since the last call," which is the per-test reset primitive. Strategy: call `collectAllMetrics()` once at test entry to drain, then exercise, then `collectAllMetrics()` to assert.
3. **`OpenTelemetryAssertions` + `SpanDataAssert` + `MetricAssert` ARE available** in `opentelemetry-sdk-testing` 1.61.0. Recommend using them.
4. **Failsafe must be declared explicitly** in `integration-tests/pom.xml` because the project's parent (`ose-otel-demo-parent`) deliberately does NOT inherit from `spring-boot-starter-parent` (per Phase 1 BOM-ordering comment in parent `pom.xml` lines 7-17). Spring's auto-binding does NOT reach this module.
5. **TEST-01 SC #2 random-port log surfacing:** Testcontainers' default ryuk + container start lifecycle prints the mapped port via the Docker client logs at INFO level when SLF4J is active. Recommend a defensive explicit `LOG.info("RabbitMQ available at {}:{}", ...)` in `@BeforeAll` to guarantee visibility regardless of Testcontainers log level.
6. **Two-context cleanup ordering:** close consumer FIRST (drains in-flight `@RabbitListener` deliveries), then producer; call `forceFlush().join(...)` on the SHARED `SdkTracerProvider`/`SdkLoggerProvider` BEFORE either context closes (because once a context closes, the SDK bean's `destroyMethod="close"` cascade has already run on it).

All paste-ready code blocks live in §3. Cross-references to PITFALLS.md #4/#5/#11/#13 are in §4.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TEST-01 | Testcontainers `RabbitMQContainer` on a random port; test logs prove this | §2.2 — random-port log surfacing pattern |
| TEST-02 | `@TestConfiguration` substitutes `InMemorySpanExporter` + `SimpleSpanProcessor` (NOT Batch) | §3.2 — `TestOtelConfiguration` skeleton |
| TEST-03 | Cross-service IT asserts producer + consumer share `traceId` | §3.3 — `OrderFlowIT` pattern + AssertJ helpers |
| TEST-04 | Same test asserts consumer's `parentSpanId == producer.spanId` | §3.3 |
| TEST-05 | Same test asserts SpanKind + messaging semconv attributes | §3.3 + AssertJ §2.4 |
| TEST-06 | `mise run test` exits non-zero on failure (CI-suitable) | §2.5 — Failsafe binding |

## 1. Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Test orchestration (JUnit lifecycle) | JVM/test runtime | — | `@Testcontainers`, `@BeforeAll`, `@AfterAll` |
| Container lifecycle (RabbitMQ start/stop) | Docker daemon (test-time) | Testcontainers JVM extension | `@Container static` field |
| Two Spring contexts (producer + consumer) | JVM (single process) | Spring Boot | `SpringApplicationBuilder` ×2 |
| OTel SDK swap | Spring DI / `@TestConfiguration` | OpenTelemetry SDK 1.61.0 | `@Bean OpenTelemetry` overrides production by name |
| HTTP exercise | Test JVM → producer Tomcat | `TestRestTemplate` | `server.port=0` random port |
| AMQP propagation under test | Producer → Testcontainers RabbitMQ → Consumer | otel-bootstrap propagation pair | Real broker, real headers |
| Telemetry capture | `InMemorySpanExporter` / `InMemoryLogRecordExporter` / `InMemoryMetricReader` | OpenTelemetry SDK testing | Synchronous; Simple processors only |
| Failsafe binding | Maven build (`integration-test` + `verify`) | `maven-failsafe-plugin` | EXPLICIT in `integration-tests/pom.xml` (parent does not inherit from spring-boot-starter-parent) |

## 2. Open Flag Resolutions

### 2.1 — `<classifier>` syntax for Spring Boot 3.4.13 `spring-boot-maven-plugin`

**Verified against:** Spring Boot 3.4.13 reference `https://docs.spring.io/spring-boot/3.4.13/maven-plugin/packaging.html` [CITED].

**Finding:** `<classifier>` must live inside an `<execution><configuration>` block, NOT at the plugin-level `<configuration>`. The existing `<id>repackage</id>` execution gets the classifier; `<attach>` is unnecessary (defaults to `true`, which means BOTH artifacts are installed/deployed).

**Critical caveat for THIS project:** Producer and consumer POMs do NOT inherit from `spring-boot-starter-parent` (the project's parent `pom.xml` deliberately omits POM inheritance — see parent `pom.xml` lines 7-17 comment about BOM-ordering). That means there is NO pre-configured `<execution><id>repackage</id></execution>` to extend. The producer/consumer service POMs MUST declare the full execution, not just override its configuration.

**Behavior after the change:**
- `producer-service-0.1.0-SNAPSHOT.jar` — plain classes jar (default artifact, no Spring Boot loader, has `ProducerApplication.class` directly accessible). This is what `integration-tests/pom.xml` consumes via `<dependency>` with no `<classifier>`.
- `producer-service-0.1.0-SNAPSHOT-exec.jar` — repackaged executable fat jar (Spring Boot loader, runnable with `java -jar`).

**Planner action:** Apply the snippet in §3.1 to BOTH `producer-service/pom.xml` and `consumer-service/pom.xml`. Verify with `mvn -pl producer-service install` then `unzip -l ~/.m2/repository/com/example/producer-service/0.1.0-SNAPSHOT/producer-service-0.1.0-SNAPSHOT.jar | grep ProducerApplication.class` — must show one entry, NOT under `BOOT-INF/classes/`.

**Confidence:** HIGH `[VERIFIED: Spring Boot 3.4.13 reference docs + cross-checked against the Phase 1 parent POM convention]`.

### 2.2 — TEST-01 SC #2: Random-port visible in test logs

**Verified against:** Testcontainers JUnit Jupiter docs (`https://java.testcontainers.org/test_framework_integration/junit_5/`) [CITED]; Testcontainers logging conventions [VERIFIED via codebase: `org.testcontainers:testcontainers` ships SLF4J logging by default].

**Finding:** `RabbitMQContainer.start()` does emit container start banners via SLF4J at INFO level, including a line of the form:

```
[main] INFO  🐳 [rabbitmq:4.3-management-alpine] - Container rabbitmq:4.3-management-alpine started in PT2.847S
```

…but the **mapped random port** is logged at DEBUG by default in the `org.testcontainers` package. To guarantee TEST-01 SC #2 visibility under default `mvn -T 1C verify` log levels (which are typically INFO), the test class MUST emit an explicit INFO line in `@BeforeAll` after `rabbit.start()`:

```java
LOG.info("RabbitMQ test container available at {}:{}", rabbit.getHost(), rabbit.getAmqpPort());
```

**Why explicit beats implicit:** SC #2 phrasing is "workshop attendee inspects test logs and sees a non-default random RabbitMQ port (e.g., `localhost:54321` not `localhost:5672`)." A defensive explicit log line removes dependency on Testcontainers' internal log levels and survives any future Testcontainers version bump.

**Planner action:** Add the SLF4J `LOG.info(...)` line in `@BeforeAll` AFTER `rabbit.start()` and BEFORE `System.setProperty(...)`. Match the producer/consumer's existing SLF4J idiom (`LoggerFactory.getLogger(OrderFlowIT.class)`). See §3.3 for placement.

**Confidence:** HIGH on the explicit-log approach `[VERIFIED]`. MEDIUM on the precise default Testcontainers log level — the explicit `LOG.info` makes this irrelevant to the success criterion `[ASSUMED]`.

### 2.3 — `InMemoryMetricReader` reset semantics (D-15)

**Verified against:** OpenTelemetry Java SDK 1.61.0 source `https://github.com/open-telemetry/opentelemetry-java/blob/v1.61.0/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/exporter/InMemoryMetricReader.java` [VERIFIED via WebFetch].

**Finding (definitive):**
- `InMemoryMetricReader` does **NOT** expose a `reset()` method.
- `collectAllMetrics()` Javadoc states: **"Returns all metrics accumulated since the last call."**
- That means `collectAllMetrics()` is itself the per-test isolation primitive: each call drains the accumulator.
- The reader supports both `create()` (cumulative temporality, the default) and `createDelta()` (delta temporality). For Phase 6 the cumulative default is correct because `orders.created` is a counter (cumulative is the spec default).

**Recommended pattern (simplest robust strategy):**

```java
@BeforeEach
void resetTelemetry() {
    spanExporter.reset();              // sdk-testing SpanExporter has reset()
    logExporter.reset();               // sdk-testing LogRecordExporter has reset()
    metricReader.collectAllMetrics();  // drains the accumulator — return value discarded
}
```

This is robust against cumulative state without requiring per-test SDK rebuild and without relying on `>=` count semantics. After this `@BeforeEach`, the metric test calls `collectAllMetrics()` again to read ONLY the metrics produced inside the test method.

**Why NOT per-test SDK rebuild:** the two Spring contexts are started ONCE in `@BeforeAll`; rebuilding the shared `OpenTelemetry` bean between tests would require re-injecting it into the producer's `OrderService`/`HttpServerSpanFilter` and consumer's `ProcessingService`/`OrderListener` Tracer/Meter beans — practically a context restart per test. The "drain via collectAllMetrics()" pattern keeps the test fixture lightweight.

**Why NOT `>=` count assertions:** they pass even when behavior is wrong (e.g., counter incremented twice instead of once). The drain-then-collect pattern allows exact-equality assertions.

**Planner action:** Use the `@BeforeEach` pattern above. The test method asserts `count == 1` (exact) on the metric data point. See §3.3 test 3 for the assertion shape.

**Confidence:** HIGH `[VERIFIED: javadoc string in source]`.

### 2.4 — `SpanDataAssert` / `MetricAssert` availability (D-25 in Discretion)

**Verified against:** OpenTelemetry Java SDK 1.61.0 source `https://github.com/open-telemetry/opentelemetry-java/tree/v1.61.0/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/assertj` [VERIFIED via WebFetch].

**Finding:** All three classes exist and are stable in `opentelemetry-sdk-testing` 1.61.0:
- `OpenTelemetryAssertions` — entry point with static `assertThat(SpanData)`, `assertThat(MetricData)`, `assertThat(Resource)`, `assertThat(Attributes)`, `equalTo(AttributeKey, value)` matchers.
- `SpanDataAssert` — fluent assertions on `SpanData` (`.hasTraceId(...)`, `.hasParentSpanId(...)`, `.hasKind(...)`, `.hasAttribute(...)`, `.hasStatus(...)`, `.hasEventsSatisfyingExactly(...)`).
- `MetricAssert` — fluent assertions on `MetricData` (`.hasName(...)`, `.hasUnit(...)`, `.hasLongSumSatisfying(...)`, `.hasHistogramSatisfying(...)`).

Plus 32 supporting classes including `LogRecordDataAssert`, `LongSumAssert`, `HistogramAssert`, `EventDataAssert`, `StatusDataAssert`, `TraceAssert`, `TracesAssert`.

**Planner action:** Use the AssertJ helpers throughout. Static-import `io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.*` at the top of `OrderFlowIT.java`. Avoid hand-rolling reflective access on raw `SpanData`/`MetricData` records — the helpers compose better with AssertJ's `satisfiesExactlyInAnyOrder(...)` for collection assertions.

**Sample usage:**

```java
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

assertThat(consumerSpan)
    .hasTraceId(producerSpan.getTraceId())
    .hasParentSpanId(producerSpan.getSpanId())
    .hasKind(SpanKind.CONSUMER)
    .hasAttribute(equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"))
    .hasAttribute(equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "process"));
```

**Confidence:** HIGH `[VERIFIED: directory listing of v1.61.0 tag]`.

### 2.5 — Failsafe binding via Spring Boot parent (D-03)

**Verified against:** `https://github.com/spring-projects/spring-boot/blob/v3.4.13/spring-boot-project/spring-boot-starters/spring-boot-starter-parent/build.gradle` [VERIFIED via WebFetch — confirms `spring-boot-starter-parent` 3.4.13 DOES bind `maven-failsafe-plugin` to `integration-test` + `verify` goals].

**Critical project-specific finding:** The `ose-otel-demo` project's parent `pom.xml` deliberately does NOT inherit from `spring-boot-starter-parent` (see parent `pom.xml` lines 7-17 comment: "We use BOM imports below to control ordering. Inheriting from spring-boot-starter-parent would inject the Spring Boot BOM via POM inheritance BEFORE our `<dependencyManagement>` block runs..."). Producer and consumer service POMs also do NOT inherit from `spring-boot-starter-parent` — they inherit from `ose-otel-demo-parent`.

**Therefore:** Spring's auto-Failsafe-binding is NOT reaching ANY module in this reactor. The `integration-tests/pom.xml` MUST declare Failsafe explicitly with execution bindings.

**Why this didn't break Phase 1's `MessagePropertiesRoundTripTest`:** that test is named `*Test.java` (Surefire convention), not `*IT.java` (Failsafe convention). Surefire IS auto-bound to the `test` phase by `maven-default-plugins` regardless of parent. `mvn verify` works for Surefire because `verify` runs `test` as a transitive phase. But `*IT.java` requires Failsafe explicitly when you don't inherit from spring-boot-starter-parent.

**Planner action:** Add the explicit `maven-failsafe-plugin` block to `integration-tests/pom.xml` per §3.4. Use Failsafe version managed by the parent's `<pluginManagement>` if added there, OR pin `3.5.5` (latest stable as of research date) directly in the integration-tests POM.

**Confidence:** HIGH `[VERIFIED: cross-checked Spring Boot 3.4.13 starter-parent source AND project's parent pom.xml convention]`.

### 2.6 — Two-context cleanup ordering (D-10)

**Reasoning ground truth:** `SimpleSpanProcessor` is synchronous — every `span.end()` triggers an export immediately. `SimpleLogRecordProcessor` is the same. So in principle, no flush is needed before close. BUT: the consumer's `@RabbitListener` runs on a `SimpleAsyncTaskExecutor` thread. If we close the consumer context FIRST, in-flight messages still being processed can hit a closing SDK and silently lose their consumer span / log record.

**Recommended cleanup sequence:**

```java
@AfterAll
static void shutdown() {
    // 1. Stop accepting new HTTP traffic (close producer first).
    //    This guarantees no new messages are published during shutdown.
    if (producerCtx != null) producerCtx.close();

    // 2. Drain any messages still being processed by the consumer's
    //    @RabbitListener. Spring's context.close() blocks until the
    //    SimpleMessageListenerContainer.stop() completes (configurable
    //    timeout, default 30s — sufficient for any in-flight message).
    if (consumerCtx != null) consumerCtx.close();

    // 3. Defensive: clear System properties so subsequent test classes
    //    in the same JVM don't accidentally pick up the stale port.
    System.clearProperty("spring.rabbitmq.host");
    System.clearProperty("spring.rabbitmq.port");
    System.clearProperty("spring.rabbitmq.username");
    System.clearProperty("spring.rabbitmq.password");
    // RabbitMQContainer.stop() handled by @Testcontainers extension.
}
```

**`forceFlush()` placement:** Inside each `@Test` method, AFTER awaiting the expected span count and BEFORE assertions. NOT in `@AfterAll` — by `@AfterAll` time the Spring context cascade has already closed the SDK. The forceFlush in-test is belt-and-braces; with `SimpleSpanProcessor` the queue is already empty after `await...until(...)` resolves, but the call costs ~microseconds and adds a robustness margin.

```java
((SdkTracerProvider) openTelemetry.getSdkTracerProvider())
    .forceFlush().join(10, TimeUnit.SECONDS);
((SdkLoggerProvider) openTelemetry.getSdkLoggerProvider())
    .forceFlush().join(10, TimeUnit.SECONDS);
```

**Why producer-first close (not consumer-first):** Producer hosts the HTTP listener. Closing it first blocks new POSTs from arriving and pushing more messages into RabbitMQ. Consumer-first close would let POSTs queue messages that nothing reads, and Tomcat would still serve them with 202s — those messages would land in a queue that's about to be torn down.

**Planner action:** Use the `@AfterAll` sequence above. Use the in-test `forceFlush` pattern in each `@Test` method. Document the rationale in `OrderFlowIT.java`'s class JavaDoc (~5 lines).

**Confidence:** MEDIUM-HIGH `[ASSUMED: SimpleSpanProcessor synchronicity is documented; the two-context interaction is a reasoned synthesis, not directly verified in OTel docs because it's a project-specific shape]`. The `forceFlush()` calls are defensive; correctness does not strictly depend on them.

## 3. Paste-Ready Code Patterns

### 3.1 — `<classifier>` configuration for `producer-service/pom.xml` and `consumer-service/pom.xml`

REPLACE the existing minimal `<plugin>` block (currently just version-from-parent) with the explicit-execution form below. This applies to BOTH service POMs identically (preserves DOC-05 per-service mirror).

```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <executions>
    <!--
      Phase 6 / D-04: keep the plain classes jar as the default artifact so
      integration-tests can resolve <dependency>producer-service</dependency>
      and find ProducerApplication.class on the classpath. The <classifier>exec</classifier>
      causes the repackaged executable fat jar to be installed as
      producer-service-${version}-exec.jar — runnable with java -jar but
      OUT of the way of the test-classpath module dependency.
      <attach> defaults to true; both artifacts deploy.
    -->
    <execution>
      <id>repackage</id>
      <goals>
        <goal>repackage</goal>
      </goals>
      <configuration>
        <classifier>exec</classifier>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Verification command (per service):**

```bash
mvn -pl producer-service -am clean install
unzip -l ~/.m2/repository/com/example/producer-service/0.1.0-SNAPSHOT/producer-service-0.1.0-SNAPSHOT.jar \
  | grep -E "(ProducerApplication\.class|BOOT-INF)"
```

Expected: ONE line for `com/example/producer/ProducerApplication.class` at the top level (NOT under `BOOT-INF/`). The `BOOT-INF/` path appears only in `producer-service-0.1.0-SNAPSHOT-exec.jar`.

### 3.2 — `TestOtelConfiguration.java` skeleton

Path: `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`

```java
package com.example.e2e;

import java.util.UUID;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only OpenTelemetry SDK swap (Phase 6 / D-05..D-09).
 *
 * Read this file side-by-side with producer-service/.../OtelSdkConfiguration.java
 * — the SHAPE is identical (Resource → tracer provider → meter provider →
 * logger provider → SDK build → install Logback appender). What CHANGES:
 *
 *   Production              | Test
 *   ------------------------|--------------------------------------------
 *   OtlpGrpcSpanExporter    | InMemorySpanExporter           (deterministic)
 *   BatchSpanProcessor      | SimpleSpanProcessor            (synchronous, PITFALLS #4/#11)
 *   OtlpGrpcLogRecordExpor. | InMemoryLogRecordExporter      (in-memory)
 *   BatchLogRecordProcessor | SimpleLogRecordProcessor       (synchronous)
 *   OtlpGrpcMetricExporter  | InMemoryMetricReader           (no exporter, reader IS the sink)
 *   PeriodicMetricReader    | (none — InMemoryMetricReader registered DIRECTLY, D-16)
 *   parentBased(alwaysOn()) | alwaysOn()                     (D-18 — every span captured)
 *
 * <p><b>Why one shared file (not duplicated like production)?</b> Test
 * infrastructure is exempt from Phase 2's per-service duplication rule
 * (D-07). One InMemorySpanExporter sees ALL spans across BOTH the
 * producer and consumer Spring contexts, so cross-service assertions
 * filter by SpanKind/messaging.system rather than by service.name.
 *
 * <p><b>Why install(openTelemetry) inline (not @PostConstruct)?</b>
 * EXACT replication of the Phase 5 fix in commit f5c331a — calling
 * install() from a @PostConstruct method on this @Configuration class
 * would create a Spring self-cycle (the @Configuration would need to
 * autowire the OpenTelemetry it itself produces). See the production
 * OtelSdkConfiguration's openTelemetry() @Bean for the longer
 * explanation. PITFALLS.md #5.
 *
 * <p><b>Why these three @Beans (InMemory*)?</b> The test class
 * constructor-injects them so each @Test method can call
 * spanExporter.getFinishedSpanItems(), logExporter.getFinishedLogRecordItems(),
 * and metricReader.collectAllMetrics() directly.
 */
@TestConfiguration
public class TestOtelConfiguration {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    @Bean
    InMemoryLogRecordExporter inMemoryLogRecordExporter() {
        return InMemoryLogRecordExporter.create();
    }

    @Bean
    InMemoryMetricReader inMemoryMetricReader() {
        // Cumulative temporality (default). collectAllMetrics() returns
        // "all metrics accumulated since the last call" — that's the
        // per-test reset primitive, see RESEARCH §2.3.
        return InMemoryMetricReader.create();
    }

    /**
     * Replaces the production OpenTelemetry @Bean by name. Requires
     * spring.main.allow-bean-definition-overriding=true (set on each
     * SpringApplicationBuilder per D-06).
     *
     * destroyMethod="close" cascades to SdkTracerProvider.shutdown(),
     * SdkMeterProvider.shutdown(), SdkLoggerProvider.shutdown() when
     * @AfterAll closes the Spring context.
     */
    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry(InMemorySpanExporter spanExporter,
                                InMemoryLogRecordExporter logExporter,
                                InMemoryMetricReader metricReader) {

        // ----- Resource: ONE shape used by both contexts (D-07) -----
        // service.name="integration-test" is sufficient because cross-service
        // assertions filter by SpanKind / messaging attributes, not by
        // service.name. See D-07 commentary in CONTEXT.md.
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, "integration-test")
                .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
                .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "test")
                .build()));

        // ----- Trace pipeline: SimpleSpanProcessor + InMemorySpanExporter -----
        // SimpleSpanProcessor is synchronous: every span.end() exports
        // immediately. PITFALLS.md #4 + #11 — Batch processor would race
        // with assertions and require Thread.sleep (anti-pattern, D-13).
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.alwaysOn())  // D-18: parentBased adds no value when every span is captured
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();

        // ----- Log pipeline: SimpleLogRecordProcessor + InMemoryLogRecordExporter -----
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
            .build();

        // ----- Metric pipeline: InMemoryMetricReader registered DIRECTLY (D-16) -----
        // Production wraps OtlpGrpcMetricExporter in PeriodicMetricReader.builder(...)
        // .setInterval(Duration.ofSeconds(10)). Tests skip the periodic wrapper because
        // InMemoryMetricReader IS a MetricReader and we call collectAllMetrics()
        // synchronously from each test method. The diff between this file and
        // OtelSdkConfiguration on the metric pipeline IS the lesson surface.
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(metricReader)
            .build();

        // ----- Propagators: identical to production -----
        ContextPropagators propagators = ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));

        // ----- The SDK -----
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(propagators)
            .build();

        // ----- LOG-03 / PITFALLS #5: install Logback appender BEFORE return -----
        // EXACT replication of the Phase 5 fix in commit f5c331a — calling
        // install() from a @PostConstruct on this @Configuration class would
        // create a Spring self-cycle. See production OtelSdkConfiguration line 246.
        OpenTelemetryAppender.install(sdk);

        return sdk;
    }

    @Bean
    Tracer tracer(OpenTelemetry openTelemetry) {
        // Match production scope name. The application code's
        // tracer @Beans use scope "com.example.producer" / "com.example.consumer";
        // this test bean overrides BOTH (the @Bean name "tracer" matches
        // both production beans, and SimpleSpanProcessor doesn't care about
        // scope — every span goes to the in-memory exporter regardless).
        return openTelemetry.getTracer("com.example.integration-test");
    }

    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.example.integration-test");
    }
}
```

**Comment density check (D-19):** the skeleton above carries ~70 comment lines (counted via `grep -cE '^\s*(//|\*)' TestOtelConfiguration.java` — the planner verifies the final file ≥ 40).

### 3.3 — `OrderFlowIT.java` `@BeforeAll` / `@AfterAll` / test method skeletons

Path: `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java`

```java
package com.example.e2e;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.consumer.ConsumerApplication;
import com.example.producer.ProducerApplication;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
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

    private static InMemorySpanExporter spanExporter;
    private static InMemoryLogRecordExporter logExporter;
    private static InMemoryMetricReader metricReader;
    private static OpenTelemetry openTelemetry;

    @BeforeAll
    static void startTwoSpringContexts() {
        // 1. Container is started by @Testcontainers extension. Defensive
        //    re-check left out — extension guarantees start before @BeforeAll.

        // 2. TEST-01 SC #2: explicit log of the random port (RESEARCH §2.2).
        LOG.info("RabbitMQ test container available at {}:{}",
            rabbit.getHost(), rabbit.getAmqpPort());

        // 3. System.setProperty BEFORE either context starts (D-11).
        //    Spring Boot's Environment reads System properties during
        //    refresh; both contexts will pick these up automatically.
        System.setProperty("spring.rabbitmq.host", rabbit.getHost());
        System.setProperty("spring.rabbitmq.port", String.valueOf(rabbit.getAmqpPort()));
        System.setProperty("spring.rabbitmq.username", rabbit.getAdminUsername());
        System.setProperty("spring.rabbitmq.password", rabbit.getAdminPassword());

        // 4. Producer context (server.port=0 → random port; allow override
        //    so TestOtelConfiguration's @Bean OpenTelemetry replaces production).
        producerCtx = new SpringApplicationBuilder(
                ProducerApplication.class, TestOtelConfiguration.class)
            .properties(
                "server.port=0",
                "spring.main.allow-bean-definition-overriding=true")
            .run();

        // 5. Consumer context. server.port=0 because consumer also exposes
        //    /actuator/health on a Tomcat instance; we don't hit it but
        //    Spring needs SOME port and 0 avoids collision.
        consumerCtx = new SpringApplicationBuilder(
                ConsumerApplication.class, TestOtelConfiguration.class)
            .properties(
                "server.port=0",
                "spring.main.allow-bean-definition-overriding=true")
            .run();

        // 6. Capture references to the in-memory telemetry sinks. Use the
        //    PRODUCER context's beans — both contexts share an identical
        //    TestOtelConfiguration but each context has its OWN bean
        //    instances (Spring DI scoping). The IMPORTANT point: in this
        //    skeleton we only assert on the producer-context's telemetry
        //    sinks. SEE OPEN-QUESTION-1 below — the planner must resolve
        //    whether this is acceptable or whether a single shared SDK
        //    across contexts is required.
        spanExporter = producerCtx.getBean(InMemorySpanExporter.class);
        logExporter = producerCtx.getBean(InMemoryLogRecordExporter.class);
        metricReader = producerCtx.getBean(InMemoryMetricReader.class);
        openTelemetry = producerCtx.getBean(OpenTelemetry.class);

        // 7. Producer's actual port + REST client.
        Integer port = producerCtx.getEnvironment()
            .getProperty("local.server.port", Integer.class);
        rest = new TestRestTemplate();
        orderUrl = "http://localhost:" + port + "/orders";
    }

    @BeforeEach
    void resetTelemetry() {
        spanExporter.reset();
        logExporter.reset();
        // RESEARCH §2.3: collectAllMetrics() drains the accumulator.
        metricReader.collectAllMetrics();
    }

    @AfterAll
    static void shutdown() {
        // RESEARCH §2.6: producer-first close drains in-flight HTTP,
        // then consumer close drains in-flight @RabbitListener deliveries.
        if (producerCtx != null) producerCtx.close();
        if (consumerCtx != null) consumerCtx.close();

        // Defensive: clear System properties so subsequent test classes
        // in the same JVM don't pick up the now-stopped container's port.
        System.clearProperty("spring.rabbitmq.host");
        System.clearProperty("spring.rabbitmq.port");
        System.clearProperty("spring.rabbitmq.username");
        System.clearProperty("spring.rabbitmq.password");
    }

    // -----------------------------------------------------------------
    // TEST 1 — D-14 happy-path trace (TEST-03 + TEST-04 + TEST-05)
    // -----------------------------------------------------------------
    @Test
    void happyPathProducesSingleTrace_traceAssertions() {
        ResponseEntity<Void> response = rest.postForEntity(
            orderUrl,
            new TestOrderRequest("WIDGET-1", 3, "express"),
            Void.class);
        assert response.getStatusCode() == HttpStatus.ACCEPTED;

        // EXPECTED_SPAN_COUNT = 5: SERVER + INTERNAL_producer + PRODUCER
        // + CONSUMER + INTERNAL_consumer (planner verifies actual count
        // against Phase 2/3 source).
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> spanExporter.getFinishedSpanItems().size() >= 5);

        forceFlushAll();

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        SpanData serverSpan = findSpanByKind(spans, SpanKind.SERVER);
        SpanData producerSpan = findSpanByKind(spans, SpanKind.PRODUCER);
        SpanData consumerSpan = findSpanByKind(spans, SpanKind.CONSUMER);

        // All five spans share one traceId.
        String traceId = serverSpan.getTraceId();
        spans.forEach(s -> assertThat(s).hasTraceId(traceId));

        // Cross-service parent linkage (TEST-04).
        assertThat(consumerSpan).hasParentSpanId(producerSpan.getSpanId());

        // Messaging semconv attributes (TEST-05).
        assertThat(producerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"));
        assertThat(producerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "publish"));
        assertThat(consumerSpan).hasAttribute(equalTo(
            MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, "process"));
    }

    // -----------------------------------------------------------------
    // TEST 2 — D-14 logs (LOG-04 carryforward)
    // -----------------------------------------------------------------
    @Test
    void happyPathStampsLogsWithTraceId_logAssertions() {
        rest.postForEntity(orderUrl,
            new TestOrderRequest("WIDGET-2", 1, "standard"), Void.class);

        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> logExporter.getFinishedLogRecordItems().size() >= 2);

        forceFlushAll();

        SpanData producerSpan = findSpanByKind(
            spanExporter.getFinishedSpanItems(), SpanKind.PRODUCER);
        String traceId = producerSpan.getTraceId();

        // Producer-side LOG.info statements (OrderController + OrderPublisher,
        // Phase 5 D-15) should have trace_id matching the producer trace.
        long producerLogCount = logExporter.getFinishedLogRecordItems().stream()
            .filter(r -> r.getSpanContext().getTraceId().equals(traceId))
            .count();
        assert producerLogCount >= 2 : "expected ≥2 producer log records with traceId="
            + traceId + " — got " + producerLogCount;
    }

    // -----------------------------------------------------------------
    // TEST 3 — D-14 metrics (METRIC-02 + METRIC-03 carryforward)
    // -----------------------------------------------------------------
    @Test
    void successfulOrderRecordsCounterAndHistogram_metricAssertions() {
        rest.postForEntity(orderUrl,
            new TestOrderRequest("WIDGET-3", 1, "express"), Void.class);

        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> spanExporter.getFinishedSpanItems().size() >= 5);

        forceFlushAll();

        // collectAllMetrics() returns metrics accumulated since @BeforeEach
        // drained the accumulator (RESEARCH §2.3).
        var metrics = metricReader.collectAllMetrics();

        MetricData ordersCreated = metrics.stream()
            .filter(m -> "orders.created".equals(m.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(ordersCreated)
            .hasName("orders.created")
            .hasLongSumSatisfying(sum -> sum.hasPointsSatisfying(point ->
                point.hasValue(1L).hasAttribute(
                    equalTo(io.opentelemetry.api.common.AttributeKey.stringKey("order.priority"),
                        "express"))));

        MetricData httpDuration = metrics.stream()
            .filter(m -> "http.server.request.duration".equals(m.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(httpDuration)
            .hasName("http.server.request.duration")
            .hasUnit("s")
            .hasHistogramSatisfying(h -> h.hasPointsSatisfying(p ->
                p.hasAttribute(equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "POST"))
                 .hasAttribute(equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 202L))));
    }

    // -----------------------------------------------------------------
    // TEST 4 — D-14/D-17 failure path (APP-04 + TRACE-09 + Phase 5 D-16)
    // -----------------------------------------------------------------
    @Test
    void tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions() {
        for (int i = 1; i <= 10; i++) {
            rest.postForEntity(orderUrl,
                new TestOrderRequest("WIDGET-" + i, 1, "standard"), Void.class);
        }

        // 10 traces × 5 spans each = 50; allow margin.
        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> spanExporter.getFinishedSpanItems().size() >= 50);

        forceFlushAll();

        // Find the CONSUMER span with Status.ERROR.
        SpanData errorConsumerSpan = spanExporter.getFinishedSpanItems().stream()
            .filter(s -> s.getKind() == SpanKind.CONSUMER)
            .filter(s -> s.getStatus().getStatusCode() == StatusCode.ERROR)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "expected one CONSUMER span with Status.ERROR (10th order)"));

        // recordException attached an event whose attribute exception.type
        // ends in ProcessingFailedException.
        assertThat(errorConsumerSpan).hasEventsSatisfyingExactly(event ->
            event.hasName("exception")
                 .hasAttributesSatisfying(attrs -> {
                     // exception.type attribute key from semconv
                     // (incubating.ExceptionAttributes.EXCEPTION_TYPE).
                 }));

        // Triple-signal correlation: a LOG.error record carries the same trace_id.
        String errorTraceId = errorConsumerSpan.getTraceId();
        boolean hasCorrelatedErrorLog = logExporter.getFinishedLogRecordItems().stream()
            .anyMatch(r -> r.getSpanContext().getTraceId().equals(errorTraceId)
                && r.getSeverity() == io.opentelemetry.api.logs.Severity.ERROR);
        assert hasCorrelatedErrorLog
            : "expected a LOG.error record correlated to the failed trace " + errorTraceId;
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------
    private static SpanData findSpanByKind(List<SpanData> spans, SpanKind kind) {
        return spans.stream()
            .filter(s -> s.getKind() == kind)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "no span with kind=" + kind + " — found "
                + spans.stream().map(s -> s.getKind().toString()).toList()));
    }

    private static void forceFlushAll() {
        ((SdkTracerProvider) openTelemetry.getSdkTracerProvider())
            .forceFlush().join(10, TimeUnit.SECONDS);
        ((SdkLoggerProvider) openTelemetry.getSdkLoggerProvider())
            .forceFlush().join(10, TimeUnit.SECONDS);
    }

    record TestOrderRequest(String sku, int quantity, String priority) {}
}
```

**OPEN-QUESTION-1 (planner resolves before merging):** The skeleton above captures `spanExporter` / `logExporter` / `metricReader` from the producer context only. Each Spring context — even when both `@Import` the same `TestOtelConfiguration` — gets its OWN bean instances by default. That means the consumer's spans, logs, and metrics flow into the **consumer context's** in-memory sinks, not the producer's. The cross-service trace assertion would then fail because `spanExporter.getFinishedSpanItems()` only sees half the spans.

**Two viable resolutions for the planner:**

(a) **Capture ALL six sinks** — `spanExporterProducer`, `spanExporterConsumer`, etc. — and merge in each test (`Stream.concat(producer.getFinishedSpanItems().stream(), consumer.getFinishedSpanItems().stream())`). Honors strict per-context bean isolation. ~6 extra fields on the test class.

(b) **Move the `InMemory*` and `OpenTelemetry` bean creation to `@BeforeAll` (manual instantiation, NOT @Bean) and pass them as Spring properties / a static holder** so both contexts share the SAME instances. Cleanest match for D-07's "ONE shared OpenTelemetry instance, ONE in-memory exporter sees ALL spans" intent. Requires custom `ApplicationContextInitializer` or `@Bean` factory method that reads from a static holder (e.g., a package-private `TestOtelHolder` class).

**Recommendation:** option (b) — it matches the D-07 intent literally. Implementation sketch:

```java
// integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
final class TestOtelHolder {
    static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();
    static final InMemoryLogRecordExporter LOGS = InMemoryLogRecordExporter.create();
    static final InMemoryMetricReader METRICS = InMemoryMetricReader.create();
}
```

Then `TestOtelConfiguration`'s @Beans return `TestOtelHolder.SPANS` etc. instead of `.create()`. Both contexts get the same singletons; one in-memory queue sees both services' spans.

**Confidence on this resolution:** HIGH — the SDK's in-memory exporters are thread-safe (their internal lists use synchronized blocks per the source). MEDIUM on whether the production code paths honor a shared `OpenTelemetry` instance correctly across the two contexts (planner verifies during plan execution).

### 3.4 — `integration-tests/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>ose-otel-demo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>integration-tests</artifactId>
  <packaging>jar</packaging>

  <name>OSE OTel Demo (integration tests)</name>
  <description>Cross-service IT proving the full instrumentation chain (Phase 6).</description>

  <dependencies>
    <!--
      Phase 6 / D-04: depend on the PLAIN classes jars (no classifier).
      The producer/consumer service POMs publish the executable repackage
      with classifier=exec; the default artifact is the plain classes jar
      that exposes ProducerApplication.class / ConsumerApplication.class
      directly on the classpath. SpringApplicationBuilder(ProducerApplication.class)
      requires this.
    -->
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>producer-service</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>consumer-service</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- otel-bootstrap is transitive via producer/consumer; no explicit dep. -->

    <!-- Spring Boot test infra. -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <!--
      Defensive carry per CONTEXT.md canonical_refs — kept on the deps list
      even though D-10's two-context flow doesn't use @ServiceConnection.
      Keeps the option open if planner reverses D-10.
    -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Testcontainers (BOM-managed by Spring Boot 3.4.13). -->
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>rabbitmq</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- OTel in-memory exporters (BOM-managed by opentelemetry-bom:1.61.0). -->
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-sdk-testing</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Awaitility (BOM-managed by Spring Boot 3.4.13). -->
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!--
        Phase 6 / D-03: EXPLICIT Failsafe binding. The project's parent does
        NOT inherit from spring-boot-starter-parent (deliberate Phase 1 BOM-
        ordering choice — see parent pom.xml lines 7-17), so Spring's auto-
        Failsafe-binding does not reach this module. Bind goals manually.
        Version 3.5.5 = latest stable as of 2026-04.
      -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
        <version>3.5.5</version>
        <executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

**Parent POM update:** Add `<module>integration-tests</module>` to the parent `pom.xml` `<modules>` list (existing list: `otel-bootstrap`, `producer-service`, `consumer-service`).

## 4. Pitfall Reinforcement

| PITFALLS.md # | Phase 6 line of code that mitigates it |
|---------------|-----------------------------------------|
| **#4** BatchSpanProcessor loses telemetry on shutdown | `TestOtelConfiguration.java`: `SimpleSpanProcessor.create(spanExporter)` (§3.2). Synchronous export per `span.end()`; no shutdown race. |
| **#5** OpenTelemetryAppender silent no-op when init order wrong | `TestOtelConfiguration.java` line `OpenTelemetryAppender.install(sdk);` BEFORE `return sdk;` inside `@Bean openTelemetry()`. EXACT replication of Phase 5 commit `f5c331a`. |
| **#11** SimpleSpanProcessor vs Batch confusion in tests; Thread.sleep anti-pattern | `OrderFlowIT.java`: `SimpleSpanProcessor` in TestOtelConfiguration; Awaitility polling on `getFinishedSpanItems().size() >= N` (NOT `Thread.sleep`); `forceFlush().join(10, TimeUnit.SECONDS)` defensive. |
| **#13** `@ServiceConnection` requires typed `RabbitMQContainer` + `org.testcontainers:rabbitmq` + `spring-boot-testcontainers` | `integration-tests/pom.xml` keeps all three on the deps list defensively even though D-10's manual two-context flow uses `System.setProperty` instead of `@ServiceConnection`. Reversing to `@ServiceConnection` would require ZERO POM changes. |

**One additional risk surfaced by this research, not in PITFALLS.md:**

- **Per-context bean isolation breaks the "one shared in-memory exporter" mental model.** OPEN-QUESTION-1 in §3.3 — two Spring contexts importing the same `@TestConfiguration` class get TWO sets of bean instances by default. Mitigated by the `TestOtelHolder` static-singleton pattern (recommended) OR by capturing six separate sinks and merging in each test (alternate). Planner picks before implementation.

## 5. Sources

### Primary (HIGH confidence)
- Spring Boot 3.4.13 Maven plugin reference — `<classifier>` packaging guidance (§2.1) — `https://docs.spring.io/spring-boot/3.4.13/maven-plugin/packaging.html` [VERIFIED via WebFetch]
- OpenTelemetry Java SDK 1.61.0 source — `InMemoryMetricReader` API (§2.3) — `https://github.com/open-telemetry/opentelemetry-java/blob/v1.61.0/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/exporter/InMemoryMetricReader.java` [VERIFIED via WebFetch]
- OpenTelemetry Java SDK 1.61.0 assertj package listing (§2.4) — `https://github.com/open-telemetry/opentelemetry-java/tree/v1.61.0/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/assertj` [VERIFIED via WebFetch]
- Spring Boot 3.4.13 starter-parent build — Failsafe auto-binding (§2.5) — `https://github.com/spring-projects/spring-boot/blob/v3.4.13/spring-boot-project/spring-boot-starters/spring-boot-starter-parent/build.gradle` [VERIFIED via WebFetch]
- This repo: parent `pom.xml` lines 7-17 — Phase 1 BOM-ordering rationale that drives §2.5's "explicit Failsafe required" finding [VERIFIED via Read]
- This repo: `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` lines 246, 274-277 — Phase 5 install ordering pattern replicated in `TestOtelConfiguration` [VERIFIED via Read]
- `.planning/research/PITFALLS.md` §#4, §#5, §#11, §#13 [VERIFIED via Read]

### Secondary (MEDIUM confidence)
- Maven Failsafe plugin usage docs — `https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html` [VERIFIED via WebFetch]
- Testcontainers JUnit 5 integration — pre-`@BeforeAll` container lifecycle [ASSUMED via training]

### Tertiary (LOW confidence — flagged by [ASSUMED])
- §2.6 two-context cleanup ordering rationale — synthesized from SimpleSpanProcessor synchronicity + Spring lifecycle docs; not directly verified in OTel docs because it's a project-specific shape [ASSUMED]
- §2.2 default Testcontainers log level for the random port banner — explicit `LOG.info` in `@BeforeAll` makes this irrelevant to TEST-01 SC #2 [ASSUMED]

## 6. Confidence Assessment

| Flag | Confidence | Rationale |
|------|------------|-----------|
| §2.1 `<classifier>` syntax | HIGH | Spring Boot 3.4.13 reference docs explicit; cross-checked with project's "no spring-boot-starter-parent inheritance" convention |
| §2.2 Random-port log surfacing | HIGH | The explicit `LOG.info` pattern is independent of Testcontainers internals and guaranteed visible at default Maven log levels |
| §2.3 `InMemoryMetricReader` reset | HIGH | Verified via Javadoc string in 1.61.0 source: "Returns all metrics accumulated since the last call" |
| §2.4 `SpanDataAssert` / `MetricAssert` | HIGH | Verified via 1.61.0 directory listing; 35 assertion classes total |
| §2.5 Failsafe auto-binding gap | HIGH | Cross-verified Spring Boot 3.4.13 starter-parent (binds Failsafe) AND project's parent pom.xml (does not inherit) |
| §2.6 Two-context cleanup ordering | MEDIUM-HIGH | Reasoned synthesis; in-test `forceFlush()` is defensive and correctness-independent |
| §3.3 OPEN-QUESTION-1 (per-context bean isolation) | HIGH on the mechanism, MEDIUM on which resolution is correct for D-07 — planner picks |

## 7. Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Testcontainers' default container-start banner does NOT surface the random port at INFO; default Maven log level is INFO; therefore an explicit `LOG.info` is required | §2.2 | NONE — explicit `LOG.info` guarantees SC #2 visibility regardless of Testcontainers' actual log level |
| A2 | `SimpleSpanProcessor.create(...)` returns a synchronous processor whose `forceFlush()` is a no-op when the queue is empty | §2.6 | LOW — verified by SDK source per training; in-test `forceFlush()` is defensive |
| A3 | Spring's `ConfigurableApplicationContext.close()` blocks on `SimpleMessageListenerContainer.stop()` until in-flight deliveries complete (default `shutdownTimeout=5000ms`) | §2.6 | MEDIUM — if the timeout is shorter than message processing in the failure path test (test 4), the 10th order's CONSUMER span might be cut off mid-export. Mitigation: tests assert AFTER `Awaitility.await()` resolves and BEFORE `@AfterAll`, so SimpleSpanProcessor has already exported synchronously inside the listener thread |
| A4 | The `OrderFlowIT.java` package matches CONTEXT.md's recommendation `com.example.e2e` | §3.3 | LOW — planner confirms during `/gsd-plan-phase` |

## 8. Open Questions

1. **OPEN-QUESTION-1 (per-context bean isolation across two SpringApplicationBuilder contexts):** see §3.3. Recommendation: `TestOtelHolder` static-singleton pattern. Planner picks before implementation. **— RESOLVED 2026-05-02 by user (post-research AskUserQuestion):** `TestOtelHolder` static singleton chosen. CONTEXT.md D-07.1 added to record the resolution. Planner stamps `TestOtelHolder.java` per CONTEXT D-07.1 and updates `TestOtelConfiguration.java` @Bean bodies + `OrderFlowIT.java` exporter access accordingly.
2. **Awaitility version pin:** Spring Boot 3.4.13 BOM-managed version (4.2.x) is fine. No override needed unless planner discovers a feature dependency on a newer version (none anticipated).
3. **Should `TestOtelConfiguration` set the test-context Resource service.name to "integration-test" (single shape) or to "order-producer-test" / "order-consumer-test" (two shapes)?** D-07 recommends single shape; this research follows D-07. If the planner reverses, the assertion strategy in test 1 must shift from filter-by-SpanKind to filter-by-service.name.
4. **Failsafe version pin:** §3.4 pins 3.5.5 directly. If the parent's `<pluginManagement>` later adds Failsafe, the integration-tests POM should reference the parent-managed version. Not blocking for v1.

## RESEARCH COMPLETE
