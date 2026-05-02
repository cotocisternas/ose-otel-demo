# Phase 6: Verification Tests — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 13 (8 new/modified + 5 read-only invariants)
**Analogs found:** 5 strong / 8 actionable; 3 NEW (no analog — paste from RESEARCH §3)

This file maps each Phase 6 file-to-create/modify to the closest existing analog
in the repo, gives a paste-ready code excerpt, and flags every divergence the
planner must preserve. Where there is NO in-repo analog, the planner pastes from
RESEARCH §3 verbatim.

## §1 Summary Table

| # | File (target) | Role | Data flow | Closest analog | Match | Confidence |
|---|---------------|------|-----------|----------------|-------|-----|
| 1 | `integration-tests/pom.xml` | Maven module pom | build-tooling | `otel-bootstrap/pom.xml` (shape only — NO Failsafe binding exists) | weak | MED → use RESEARCH §3.4 verbatim |
| 2 | `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` | test-only static-singleton holder | shared-state | **NO ANALOG** | none | MED → RESEARCH §3.3 sketch + paste below |
| 3 | `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` | `@TestConfiguration` SDK swap | DI / startup-wiring | `producer-service/.../OtelSdkConfiguration.java` (production SDK shape) | exact-shape | HIGH |
| 4 | `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` | JUnit 5 `@Testcontainers` cross-service IT | e2e-test / request-response + AMQP | `otel-bootstrap/.../MessagePropertiesRoundTripTest.java` (JUnit 5 shape ONLY — NOT a SpringBootTest) | partial-role | MED → RESEARCH §3.3 is source of truth |
| 5 | `pom.xml` (parent, modify) | reactor aggregator | build-tooling | itself — existing `<modules>` list | exact | HIGH |
| 6 | `producer-service/pom.xml` (modify) | service pom — add `<classifier>` execution | build-tooling | **no existing classifier config in repo** | weak | HIGH (RESEARCH §3.1 verified) |
| 7 | `consumer-service/pom.xml` (modify) | service pom — same as #6 | build-tooling | identical to #6 | weak | HIGH |
| 8 | `README.md` (modify — add "Step 6") | doc | narrative | `README.md` "Step 5: Logs Correlation" section (lines 127–210) | exact-shape | HIGH |

**Read-only invariants (no edits in Phase 6 — but planner must verify they remain unchanged):**

| # | File | Why it matters for Phase 6 |
|---|------|---------------------------|
| 9 | `producer-service/.../messaging/OrderPublisher.java` line 45 | Test 2 asserts on `LOG.info("publishing orderId={} to exchange={}", ...)` |
| 10 | `consumer-service/.../domain/ProcessingService.java` line 96 | Test 4 asserts on `LOG.error("order processing failed: orderId={}", orderId, e)` |
| 11 | `consumer-service/.../messaging/OrderListener.java` line 51 | Test 2 may assert on consumer-side `LOG.info` (planner picks producer-only or both) |
| 12 | `otel-bootstrap/.../amqp/TracingMessagePostProcessor.java` | Test 1 asserts the inject path produced a PRODUCER span with messaging semconv |
| 13 | `otel-bootstrap/.../amqp/TracingMessageListenerAdvice.java` | Test 1 asserts the extract path produced a CONSUMER span whose `parentSpanId == producer.spanId` |

## §2 Per-File Pattern Entries

### File 1 — `integration-tests/pom.xml` (NEW)

**Role:** Maven module POM for an integration-test-only module under the existing
`ose-otel-demo-parent` aggregator.
**Data flow:** build-tooling. Declares deps on producer + consumer **plain
classes jars** (D-04), Testcontainers, OTel sdk-testing, Awaitility, and binds
**maven-failsafe-plugin** explicitly because `ose-otel-demo-parent` does NOT
inherit from `spring-boot-starter-parent` (RESEARCH §2.5).

**Closest analog:** `otel-bootstrap/pom.xml` — same parent, similar test-only
posture. **Critical gap:** `otel-bootstrap/pom.xml` has NO Failsafe binding (it
runs `*Test.java` via Surefire, which is auto-bound). The integration-tests POM
must add Failsafe explicitly.

**Analog excerpt** (`otel-bootstrap/pom.xml` lines 5–18 — parent + packaging shape):

```xml
<parent>
  <groupId>com.example</groupId>
  <artifactId>ose-otel-demo-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <relativePath>../pom.xml</relativePath>
</parent>

<artifactId>otel-bootstrap</artifactId>
<packaging>jar</packaging>
```

**Paste source:** RESEARCH §3.4 — the full POM block (lines 779–889 of
06-RESEARCH.md). Apply verbatim. Includes:
- Producer + consumer deps (no `<classifier>` → resolves to plain classes jar per D-04).
- `spring-boot-starter-test` (test scope).
- `spring-boot-testcontainers` (defensive carry per CONTEXT canonical_refs).
- `org.testcontainers:junit-jupiter` + `:rabbitmq` (test scope, BOM-managed).
- `io.opentelemetry:opentelemetry-sdk-testing` (test scope, BOM-managed by `opentelemetry-bom:1.61.0`).
- `org.awaitility:awaitility` (test scope, BOM-managed by Spring Boot 3.4.13).
- Explicit `maven-failsafe-plugin:3.5.5` with `<goals><goal>integration-test</goal><goal>verify</goal></goals>`.

**Divergence from analog:**
- Does NOT depend on `org.springframework.amqp:spring-rabbit` directly (transitive via consumer-service).
- Adds Failsafe binding (otel-bootstrap has none).
- Adds Testcontainers + sdk-testing + Awaitility (otel-bootstrap has none).

---

### File 2 — `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` (NEW per D-07.1)

**Role:** Test-only static-singleton holder for the SHARED `OpenTelemetrySdk`
and the three `InMemory*` exporters/reader. Resolves OPEN-QUESTION-1: two
SpringApplicationBuilder contexts that `@Import` the same `@TestConfiguration`
each get their OWN bean instances; the holder forces ONE shared SDK across both
contexts so `InMemorySpanExporter` sees ALL spans across producer + consumer
(D-07 literal).
**Data flow:** shared-state (process-wide static singleton, lazy-initialized,
synchronized).

**Closest analog:** **NONE in repo.** RESEARCH §3.3 sketches a 4-line stub but
the full skeleton (lazy init + install ordering + `@AfterAll` shutdown) does
not appear anywhere. This is genuinely net-new; planner stamps from the
paste-ready snippet below.

**Paste-ready snippet** (synthesized from D-07.1 + RESEARCH §3.3 OPEN-QUESTION-1
recommendation + Phase 5 install-ordering invariant):

```java
package com.example.e2e;

import java.util.UUID;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
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

/**
 * Process-wide singleton that holds ONE {@link OpenTelemetrySdk} + three
 * in-memory sinks shared across BOTH producer and consumer Spring contexts
 * launched by {@link OrderFlowIT}. Resolves CONTEXT.md D-07.1.
 *
 * <p>Why a static holder, not @Bean? Each {@code SpringApplicationBuilder}
 * context, even when it @Imports the same {@link TestOtelConfiguration},
 * yields a SEPARATE bean instance. {@code D-07} requires ONE
 * {@code OpenTelemetry} so a single in-memory queue captures spans from
 * both services. The holder is the smallest pedagogically-clean way to
 * force that.
 *
 * <p>Lifecycle: lazy init on first {@link #get()}; subsequent calls return
 * the same instances. {@link OpenTelemetryAppender#install(OpenTelemetry)}
 * runs INSIDE {@link #get()} BEFORE the SDK reference is published — exact
 * replication of Phase 5 commit {@code f5c331a} install ordering (D-09).
 */
final class TestOtelHolder {

    static volatile OpenTelemetrySdk SDK;
    static volatile InMemorySpanExporter SPANS;
    static volatile InMemoryLogRecordExporter LOGS;
    static volatile InMemoryMetricReader METRICS;

    private TestOtelHolder() {}

    static synchronized OpenTelemetrySdk get() {
        if (SDK != null) return SDK;

        SPANS = InMemorySpanExporter.create();
        LOGS = InMemoryLogRecordExporter.create();
        METRICS = InMemoryMetricReader.create();

        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, "integration-test")
                .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
                .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "test")
                .build()));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.alwaysOn())  // D-18
            .addSpanProcessor(SimpleSpanProcessor.create(SPANS))  // PITFALLS #4/#11
            .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(LOGS))
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(METRICS)  // D-16: no PeriodicMetricReader wrapper
            .build();

        ContextPropagators propagators = ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(propagators)
            .build();

        // PITFALL #5 / Phase 5 commit f5c331a — install BEFORE publishing SDK.
        OpenTelemetryAppender.install(sdk);

        SDK = sdk;
        return SDK;
    }
}
```

**Divergence from production `OtelSdkConfiguration`:**
- NOT a `@Configuration` — bare `final class` with package-private static fields.
- Single `service.name="integration-test"` Resource shape (D-07 single-resource recommendation).
- Lazy init via `synchronized static get()` (production uses Spring `@Bean` lifecycle).
- No `@PreDestroy` / appender uninstall hook — `OrderFlowIT.@AfterAll` calls `OpenTelemetryAppender.install(OpenTelemetry.noop())` if needed (planner adds defensively).

---

### File 3 — `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` (NEW)

**Role:** `@TestConfiguration` whose `@Bean`s are thin facades over
`TestOtelHolder` (per D-07.1). Each `@Bean` first calls `TestOtelHolder.get()`
to ensure the SDK is initialized, then returns the static field. Both
SpringApplicationBuilder contexts `@Import` this class.
**Data flow:** DI / startup-wiring. `spring.main.allow-bean-definition-overriding=true`
(D-06) lets the `@Bean OpenTelemetry` override the production-scanned bean by
name in BOTH contexts.

**Closest analog:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
(541 lines). The SHAPE is identical (Resource → tracer/meter/logger providers →
SDK build → appender install → @Bean Tracer + @Bean Meter). The bean SIGNATURES
must match by NAME so override-by-name works (D-06).

**Analog excerpt (production `OtelSdkConfiguration.openTelemetry()` lines 120–249, key invariants only):**

```java
@Bean(destroyMethod = "close")  // <-- MUST replicate (Phase 2 D-15 carryforward)
OpenTelemetry openTelemetry() {
    Resource resource = Resource.getDefault().merge(
        Resource.create(Attributes.builder()
            .put(ServiceAttributes.SERVICE_NAME, "order-producer")  // diverges in test → "integration-test"
            .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
            .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
            .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop")  // → "test"
            .build()));

    SdkTracerProvider tracerProvider = buildTracerProvider(resource);  // BatchSpanProcessor + OTLP
    SdkMeterProvider  meterProvider  = buildMeterProvider(resource);   // PeriodicMetricReader + OTLP
    SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);  // BatchLogRecordProcessor + OTLP

    ContextPropagators propagators = ContextPropagators.create(
        TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()));

    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .setLoggerProvider(loggerProvider)
        .setPropagators(propagators)
        .build();

    OpenTelemetryAppender.install(sdk);  // <-- PITFALL #5 / commit f5c331a — REPLICATE EXACTLY

    return sdk;
}

@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");  // diverges → "com.example.integration-test"
}

@Bean
Meter meter(OpenTelemetry openTelemetry) {
    return openTelemetry.getMeter("com.example.producer");
}
```

**Paste source:** RESEARCH §3.2 — full `TestOtelConfiguration` skeleton (lines
249–428 of 06-RESEARCH.md). Per D-07.1, the @Bean bodies are SIMPLER than
RESEARCH §3.2 shows: each bean delegates to `TestOtelHolder`. Use this body shape:

```java
@TestConfiguration
public class TestOtelConfiguration {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        TestOtelHolder.get();  // ensure init
        return TestOtelHolder.SPANS;
    }

    @Bean
    InMemoryLogRecordExporter inMemoryLogRecordExporter() {
        TestOtelHolder.get();
        return TestOtelHolder.LOGS;
    }

    @Bean
    InMemoryMetricReader inMemoryMetricReader() {
        TestOtelHolder.get();
        return TestOtelHolder.METRICS;
    }

    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        return TestOtelHolder.get();
    }

    @Bean
    Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.example.integration-test");
    }

    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.example.integration-test");
    }
}
```

**Divergences from production `OtelSdkConfiguration` (every one MUST be commented per D-19, ≥40 lines):**

| Production | Test |
|------------|------|
| `BatchSpanProcessor` + `OtlpGrpcSpanExporter` | `SimpleSpanProcessor` + `InMemorySpanExporter` (PITFALLS #4/#11) |
| `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter` | `SimpleLogRecordProcessor` + `InMemoryLogRecordExporter` |
| `PeriodicMetricReader.builder(...).setInterval(10s).build()` wrapping `OtlpGrpcMetricExporter` | `InMemoryMetricReader` registered DIRECTLY (D-16) |
| `Sampler.parentBased(Sampler.alwaysOn())` | `Sampler.alwaysOn()` (D-18) |
| `service.name="order-producer"` / `"order-consumer"` | `service.name="integration-test"` (D-07 single shape) |
| `deployment.environment.name="workshop"` | `"test"` |
| Per-service file (duplicated) | ONE file shared across both contexts (D-07 — test infra exempt from Phase 2 D-01) |
| Has `@PreDestroy uninstallLogbackAppender()` | NOT replicated; `@AfterAll` in OrderFlowIT may call `install(noop)` defensively |
| Tracer scope `"com.example.producer"` / `"com.example.consumer"` | `"com.example.integration-test"` |
| Inline `OpenTelemetryAppender.install(sdk)` BEFORE `return sdk` (line 246) | **EXACT same line, MUST replicate** — but inside `TestOtelHolder.get()` not the @Bean body, because the @Bean now delegates |

**Critical invariant:** the `@Bean` named `openTelemetry` must override production by name. Spring resolves overrides by bean NAME (default = method name). Production beans are also named `openTelemetry` (default from method `OpenTelemetry openTelemetry()`). Match this exactly. Same for `tracer` / `meter`.

---

### File 4 — `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` (NEW)

**Role:** JUnit 5 cross-service IT. `@Testcontainers` extension manages
`RabbitMQContainer`. `@BeforeAll` starts two `SpringApplicationBuilder`
contexts (D-10) sharing the broker via `System.setProperty` (D-11). Four
`@Test` methods cover trace / log / metric / failure-path (D-14).
**Data flow:** request-response (HTTP via `TestRestTemplate`) → AMQP → assertions
on `TestOtelHolder.SPANS / LOGS / METRICS`.

**Closest analog:** `otel-bootstrap/.../MessagePropertiesRoundTripTest.java` —
JUnit 5 shape ONLY (`@Test`, `@DisplayName`, AssertJ static imports). It is NOT
a SpringBootTest, NOT a `@Testcontainers` test, NOT a cross-service test. It
gives the planner the JUnit 5 import idiom and AssertJ static-import idiom
ONLY. **Source of truth for the rest is RESEARCH §3.3.**

**Analog excerpt** (`MessagePropertiesRoundTripTest.java` lines 1–50 — JUnit 5 idiom only):

```java
package com.example.otel.amqp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessagePropertiesRoundTripTest {
    @Test
    @DisplayName("setter writes a String value that the getter reads back identically (PITFALLS.md #2)")
    void roundTripStringHeader() {
        // ...assertion body...
    }
}
```

**Paste source:** RESEARCH §3.3 — full `OrderFlowIT.java` skeleton (lines
436–751 of 06-RESEARCH.md). With D-07.1 resolution applied, the `@BeforeAll`
no longer reads exporters from `producerCtx` beans — it reads from
`TestOtelHolder` static fields directly:

```java
// Before (RESEARCH §3.3 line 542–545 — pre-D-07.1):
spanExporter = producerCtx.getBean(InMemorySpanExporter.class);
logExporter = producerCtx.getBean(InMemoryLogRecordExporter.class);
metricReader = producerCtx.getBean(InMemoryMetricReader.class);
openTelemetry = producerCtx.getBean(OpenTelemetry.class);

// After (D-07.1):
spanExporter = TestOtelHolder.SPANS;
logExporter = TestOtelHolder.LOGS;
metricReader = TestOtelHolder.METRICS;
openTelemetry = TestOtelHolder.get();
```

**Critical invariants from RESEARCH (paste-ready in §3.3) — planner must preserve:**
- `@Container static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.3-management-alpine")`.
- `LOG.info("RabbitMQ test container available at {}:{}", rabbit.getHost(), rabbit.getAmqpPort())` defensive log line in `@BeforeAll` (RESEARCH §2.2 — TEST-01 SC #2).
- `System.setProperty("spring.rabbitmq.host"/"port"/"username"/"password", ...)` BEFORE either context starts (D-11).
- Two `SpringApplicationBuilder` contexts; each with `.properties("server.port=0", "spring.main.allow-bean-definition-overriding=true").run()`.
- `@AfterAll`: producer-first `close()`, then consumer (RESEARCH §2.6); then `System.clearProperty(...)` for the four rabbit props.
- `@BeforeEach`: `spanExporter.reset(); logExporter.reset(); metricReader.collectAllMetrics();` (RESEARCH §2.3 — `collectAllMetrics()` IS the metric-reader reset primitive; `InMemoryMetricReader` has NO `.reset()` method).
- Awaitility polling on `getFinishedSpanItems().size() >= EXPECTED_COUNT` then `forceFlush().join(10, TimeUnit.SECONDS)` on tracerProvider AND loggerProvider (D-13 + RESEARCH §2.6).
- AssertJ helpers via `import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.*` (RESEARCH §2.4).
- Four `@Test` methods named per D-14:
  1. `happyPathProducesSingleTrace_traceAssertions()`
  2. `happyPathStampsLogsWithTraceId_logAssertions()`
  3. `successfulOrderRecordsCounterAndHistogram_metricAssertions()`
  4. `tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions()`

**Divergences from analog (`MessagePropertiesRoundTripTest`):**
- This test uses `@Testcontainers`, two Spring contexts, real broker — analog uses none of that.
- This test class is `*IT.java` (Failsafe); analog is `*Test.java` (Surefire).
- Analog has NO `@BeforeAll` / `@AfterAll`; this test has heavy lifecycle logic.

---

### File 5 — `pom.xml` (parent, MODIFY)

**Role:** Reactor aggregator. Add `<module>integration-tests</module>` to the
existing `<modules>` list.
**Data flow:** build-tooling.

**Analog:** itself (lines 27–31). Existing block:

```xml
<modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
</modules>
```

**Target after edit:**

```xml
<modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
    <module>integration-tests</module>
</modules>
```

Append at line 30 (between `consumer-service` and the closing `</modules>` on line 31). No other edits to parent POM.

---

### File 6 — `producer-service/pom.xml` (MODIFY)

**Role:** Service POM. Add `<execution>` block on `spring-boot-maven-plugin`
with `<classifier>exec</classifier>` so the repackaged executable jar gets
the classifier suffix and the **plain classes jar remains the default
artifact** (D-04 / RESEARCH §2.1).
**Data flow:** build-tooling.

**Closest analog:** **NO existing classifier config** in producer-service or
consumer-service. The current plugin block (producer-service/pom.xml lines
160–166) is bare — version-from-parent only:

```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

The `<classifier>` knob is genuinely net-new for this repo. Spring Boot
3.4.13 reference (RESEARCH §2.1) confirms the canonical syntax: classifier
inside an `<execution><configuration>`, NOT at plugin level.

**Paste source:** RESEARCH §3.1 — apply verbatim (lines 209–233 of 06-RESEARCH.md):

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

**Verification command (RESEARCH §3.1):**

```bash
mvn -pl producer-service -am clean install
unzip -l ~/.m2/repository/com/example/producer-service/0.1.0-SNAPSHOT/producer-service-0.1.0-SNAPSHOT.jar \
  | grep -E "(ProducerApplication\.class|BOOT-INF)"
```

Expected: ONE line for `com/example/producer/ProducerApplication.class` at the top level (NOT under `BOOT-INF/classes/`). The `BOOT-INF/` path appears only in `producer-service-0.1.0-SNAPSHOT-exec.jar`.

**Side effect to watch:** the JIB plugin block (lines 169–186) is unchanged — it
builds an image from the project classes; it doesn't depend on the repackaged jar.

**Divergence from analog:** there IS no analog. The bare plugin block becomes
an `<executions>` block with one explicit execution.

---

### File 7 — `consumer-service/pom.xml` (MODIFY)

**Identical to File 6.** Same paste from RESEARCH §3.1, applied to
`consumer-service/pom.xml` lines 160–166. Per Phase 2 DOC-05 mirror discipline,
both service POMs carry the same block byte-for-byte.

---

### File 8 — `README.md` (MODIFY — add "Step 6: Verification Tests" section)

**Role:** Add a Phase-6-specific section keyed to tag `step-06-tests` (D-20).
**Data flow:** narrative documentation.

**Closest analog:** `README.md` "Step 5: Logs Correlation" section, lines
127–210 (90 lines). Same structural template:
- One-paragraph opening keyed to the tag name (`step-05-logs`).
- A bulleted list of 2–3 SDK / config touch points with backtick code references and full file links.
- An idiomatic command (e.g., `mise run demo:order`) showing what the attendee runs.
- A Grafana / Loki query block showing the assertion the attendee can verify.
- Closing paragraph that calls out the headline correlation insight.

**Analog excerpt (structural shape, NOT contents — README.md lines 127–137):**

```markdown
## Step 5: Logs Correlation

`step-05-logs` adds the **third** OTel signal — logs — to both services, closing
the three-signals loop. The two `OtelSdkConfiguration.java` files now build a
`SdkLoggerProvider` next to the existing `SdkTracerProvider` and `SdkMeterProvider`
(D-01 in `05-CONTEXT.md` lands a third sibling helper `buildLoggerProvider(Resource)`,
parallel to the Phase 2 and Phase 4 helpers, so the diff against `step-04-metrics`
reads as "we added a sibling pipeline next to the trace and metric pipelines"). A
new `logback-spring.xml` per service declares the `OpenTelemetryAppender` for OTLP
export plus the MDC injector wrapper appender that stamps `trace_id`/`span_id`
into the console pattern.
```

**Target structure for "Step 6":**
- Opening paragraph: `step-06-tests` adds a CI-grade proof of the three-signal chain via a new `integration-tests` Maven module + a single cross-service `OrderFlowIT.java`.
- Bullet 1: the `RabbitMQContainer` random-port property (TEST-01 SC #2, link to test class).
- Bullet 2: the `SimpleSpanProcessor` + `InMemorySpanExporter` swap as the test-determinism lesson (PITFALLS #4/#11).
- Bullet 3: the `<classifier>exec</classifier>` mechanism on `spring-boot-maven-plugin` (D-04) — ~2-line callout with link to producer-service POM.
- Bullet 4: the four `@Test` methods (traces / logs / metrics / failure path) named explicitly.
- Bullet 5: the production-vs-test SDK divergence as a deliberate pedagogical contrast (Phase 2 D-01 per-service duplication is a PRODUCTION rule; test infra shares one `TestOtelConfiguration` per D-07).
- Closing paragraph: `mise run test` exits non-zero on failure (TEST-06 / D-21 tag gate). One-line `mise run test` example.

**Divergence from analog:**
- Step 5 has heavy paragraph blocks; Step 6 leans on bullet-list structure (matches the four-test enumeration).
- Step 5 ends with a Loki query block; Step 6 ends with a `mise run test` invocation block.

---

## §3 Cross-File Invariants

The planner must NOT lose these between files. They tie the new artifacts to
the existing instrumentation chain:

1. **`@Bean` name override-by-name.** `TestOtelConfiguration`'s `@Bean OpenTelemetry openTelemetry()`, `@Bean Tracer tracer(OpenTelemetry)`, and `@Bean Meter meter(OpenTelemetry)` MUST match production bean names exactly (`openTelemetry`, `tracer`, `meter` — default = method name). Production names are set by `OtelSdkConfiguration.openTelemetry()`, `tracer(OpenTelemetry)`, `meter(OpenTelemetry)` (lines 121, 496, 516 producer; 130, 501, 520 consumer). Spring's `allow-bean-definition-overriding=true` is name-based; mismatched names → both beans coexist → undefined wiring.

2. **`OpenTelemetryAppender.install(sdk)` runs BEFORE the SDK reference is published.** Production: inside `@Bean openTelemetry()` body BEFORE `return sdk;` (producer line 246, consumer line 255). Test: inside `TestOtelHolder.get()` BEFORE `SDK = sdk; return SDK;`. NEVER from `@PostConstruct` — Phase 5 commit `f5c331a` proved the bean self-cycle (PITFALL #5). Both files reference this commit by SHA in their comment block.

3. **Resource attribute keys must match across SDKs for cross-signal correlation to work in assertions.** Even though test uses `service.name="integration-test"` (single resource per D-07) vs production's `"order-producer"` / `"order-consumer"` (per-service), the OTHER attribute keys (`SERVICE_NAMESPACE`, `SERVICE_INSTANCE_ID`, `DEPLOYMENT_ENVIRONMENT_NAME`) MUST come from the same imports — `io.opentelemetry.semconv.ServiceAttributes` + `io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes`. If the test imports a different package, span `getResource().getAttributes()` lookups in test 1 will return null.

4. **`destroyMethod="close"` on the `@Bean OpenTelemetry`.** Production has it (producer line 120, consumer line 129) — Phase 2 D-15 lifecycle cascade. Test mirror MUST have it so `producerCtx.close()` / `consumerCtx.close()` in `@AfterAll` cascades to `OpenTelemetrySdk.close()` → tracer/meter/logger provider shutdown. Without this, `forceFlush()` belt-and-braces calls in test methods are doing real work the cascade should have handled.

5. **Producer-first context close order.** RESEARCH §2.6: `producerCtx.close()` before `consumerCtx.close()` so HTTP traffic stops first, then in-flight `@RabbitListener` deliveries drain. Reversing this can lose the 10th-order CONSUMER span in test 4.

6. **The `<classifier>exec</classifier>` knob applies to BOTH service POMs.** Apply once and the build breaks asymmetrically — `integration-tests` resolves `producer-service`'s plain classes jar but `consumer-service`'s repackaged jar (or vice versa), and one of the two `SpringApplicationBuilder(*.class)` calls fails to find the application class on the test classpath.

7. **`*IT.java` naming convention.** Failsafe matches `**/*IT.java` (and `**/IT*.java`, `**/*ITCase.java`) by default. The class file MUST be named `OrderFlowIT.java` exactly. Naming it `OrderFlowTest.java` puts it under Surefire which runs in the `test` phase with no Testcontainers wiring; naming it `OrderFlowIntegrationTest.java` matches neither.

8. **`spring-boot-testcontainers` artifact stays on the deps list defensively** even though D-10 / D-11 use `System.setProperty` instead of `@ServiceConnection`. Per CONTEXT canonical_refs: keeps the option open if planner reverses D-10. Removing it for "minimalism" would block a future `@ServiceConnection` retrofit.

9. **Read-only files must NOT be modified.** Tests assert on the EXISTING Phase 5 LOG sites (`OrderPublisher.java:45`, `OrderListener.java:51`, `ProcessingService.java:96`) and the Phase 3 propagation pair (`TracingMessagePostProcessor.java`, `TracingMessageListenerAdvice.java`). If the planner mutates any of these to "make the test pass," the regression net is broken. Test 2 / test 4 must verify these files are unchanged in HEAD.

10. **`InMemoryMetricReader` has no `.reset()` method** (RESEARCH §2.3 verified vs. SDK 1.61.0 source). The reset primitive is `collectAllMetrics()` itself — call it once in `@BeforeEach` and discard the return; call it again in the metric `@Test` and assert. Calling a non-existent `.reset()` will fail to compile; calling it via reflection would silently no-op.

## PATTERN MAPPING COMPLETE
