---
phase: 06-verification-tests
plan: 03
type: execute
wave: 3
depends_on:
  - 06-02
files_modified:
  - integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
autonomous: true
requirements:
  - TEST-02
tags:
  - opentelemetry
  - sdk-testing
  - in-memory-exporter
  - test-singleton
  - phase-6
must_haves:
  truths:
    - id: HOLDER-FILE-EXISTS
      description: "TestOtelHolder.java exists at the canonical e2e package path"
      verify: "test -f integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: PACKAGE-DECL
      description: "Package declaration is com.example.e2e"
      verify: "grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: FINAL-CLASS
      description: "Class is final with private constructor (cannot be subclassed or instantiated)"
      verify: "grep -q 'final class TestOtelHolder' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'private TestOtelHolder' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: VOLATILE-FIELDS
      description: "All four shared fields are static volatile (visible across threads after lazy init)"
      verify: "test $(grep -cE 'static\\s+volatile\\s+(OpenTelemetrySdk|InMemorySpanExporter|InMemoryLogRecordExporter|InMemoryMetricReader)\\s+(SDK|SPANS|LOGS|METRICS)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java) -ge 4"
    - id: SYNCHRONIZED-GET
      description: "static synchronized OpenTelemetrySdk get() method (lazy init guard)"
      verify: "grep -qE 'static\\s+synchronized\\s+OpenTelemetrySdk\\s+get\\s*\\(' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: SIMPLE-SPAN-PROCESSOR
      description: "Uses SimpleSpanProcessor (NOT BatchSpanProcessor) — PITFALLS #4/#11 / D-13"
      verify: "grep -q 'SimpleSpanProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'BatchSpanProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: SIMPLE-LOG-PROCESSOR
      description: "Uses SimpleLogRecordProcessor (NOT BatchLogRecordProcessor) — PITFALLS #4/#11"
      verify: "grep -q 'SimpleLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'BatchLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: NO-PERIODIC-METRIC-READER
      description: "Uses InMemoryMetricReader registered DIRECTLY (NOT wrapped in PeriodicMetricReader) — D-16"
      verify: "grep -q 'registerMetricReader(METRICS)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: ALWAYS-ON-SAMPLER
      description: "Uses Sampler.alwaysOn() (NOT parentBased) — D-18"
      verify: "grep -q 'Sampler.alwaysOn()' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'parentBased' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: APPENDER-INSTALL-AFTER-BUILD
      description: "OpenTelemetryAppender.install(sdk) called AFTER OpenTelemetrySdk.builder().build() and BEFORE SDK = sdk publication (Phase 5 commit f5c331a invariant / D-09)"
      verify: "awk '/OpenTelemetrySdk sdk = OpenTelemetrySdk.builder/{seen_build=NR} /OpenTelemetryAppender.install/{seen_install=NR} /SDK = sdk/{seen_publish=NR} END{exit !(seen_build < seen_install && seen_install < seen_publish)}' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: APPENDER-FQCN
      description: "Imports OpenTelemetryAppender from instrumentation.logback.appender.v1_0 (the install()-bearing class — Risk #1 carryforward)"
      verify: "grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: NO-MDC-APPENDER-IMPORT
      description: "Does NOT import the mdc.v1_0 OpenTelemetryAppender (wrong class — has no install method)"
      verify: "! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: SERVICE-NAME-INTEGRATION-TEST
      description: "Resource service.name='integration-test' (D-07 single-resource shape)"
      verify: "grep -F '\"integration-test\"' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: PROPAGATORS-W3C
      description: "Sets W3C TraceContext + Baggage propagators (production parity for AMQP propagation under test)"
      verify: "grep -q 'W3CTraceContextPropagator' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'W3CBaggagePropagator' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
    - id: COMPILES-CLEAN
      description: "integration-tests compiles cleanly with TestOtelHolder added"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile"
    - id: EARLY-RETURN-IDEMPOTENT
      description: "get() returns SDK directly if already initialized (idempotent re-entry)"
      verify: "grep -q 'if (SDK != null) return SDK' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java"
  artifacts:
    - path: integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
      provides: "Process-wide static singleton with lazy-initialized OpenTelemetrySdk + 3 InMemory* exporters/reader; OpenTelemetryAppender.install() ordering preserved per Phase 5 commit f5c331a"
      contains: "final class TestOtelHolder"
      contains: "static synchronized OpenTelemetrySdk get()"
      contains: "OpenTelemetryAppender.install(sdk)"
  key_links:
    - from: integration-tests/.../TestOtelHolder.java SDK field
      to: Plan 06-04's TestOtelConfiguration @Bean facades (return TestOtelHolder.SPANS/LOGS/METRICS/get())
      via: "Static field access from @Bean methods"
      pattern: "TestOtelHolder.SPANS|TestOtelHolder.LOGS|TestOtelHolder.METRICS|TestOtelHolder.get()"
    - from: integration-tests/.../TestOtelHolder.java OpenTelemetryAppender.install(sdk) call
      to: producer-service/.../OtelSdkConfiguration.java commit f5c331a (PITFALL #5 mitigation)
      via: "Phase 5 install-ordering invariant replicated in test fixture"
      pattern: "OpenTelemetryAppender.install"
    - from: integration-tests/.../TestOtelHolder.java SimpleSpanProcessor + InMemorySpanExporter
      to: PITFALLS.md #4 + #11 (Batch loses telemetry on shutdown; Thread.sleep is anti-pattern)
      via: "Synchronous test export pipeline"
      pattern: "SimpleSpanProcessor.create|SimpleLogRecordProcessor.create"
---

<objective>
Create `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` — the test-only static-singleton holder for the SHARED `OpenTelemetrySdk` + three `InMemory*` exporters/reader. This class resolves CONTEXT D-07.1: two `SpringApplicationBuilder` contexts that `@Import` the same `@TestConfiguration` would each get their own bean instances; the holder forces ONE shared SDK across both contexts so a single `InMemorySpanExporter` sees ALL spans across producer + consumer.

Purpose: Implement the "ONE OpenTelemetry instance, ONE in-memory queue captures spans from both services" intent literally (D-07 / D-07.1). Lazy-initialize the SDK on first `get()`; subsequent calls return the same instance. Critically, `OpenTelemetryAppender.install(sdk)` runs INSIDE `get()` AFTER the SDK is built and BEFORE `SDK = sdk` publishes the reference — exact replication of Phase 5 commit `f5c331a` install-ordering invariant (PITFALL #5 / D-09).

Output: ONE new file `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` (~80 lines including JavaDoc and section comments). Compiles cleanly via `mvn -pl integration-tests test-compile`.

Why this is wave 3: depends on 06-02 (the integration-tests POM with sdk-testing on classpath); independent from 06-04 in source (different file) but conceptually paired — TestOtelConfiguration's @Bean methods delegate to this holder. We serialize 06-03 → 06-04 (rather than parallel) because both touch the same Maven module and the executor's iterative compile loop is simpler with a stable target.

Why this is one focused plan: a single ~80-line file with one purpose (lazy-init holder). No splitting opportunities.
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
@.planning/phases/06-verification-tests/06-02-SUMMARY.md
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
@integration-tests/pom.xml

<interfaces>
<!-- API surface used by TestOtelHolder. All BOM-managed by opentelemetry-bom:1.61.0 + opentelemetry-instrumentation-bom-alpha:2.27.0-alpha. -->

From `io.opentelemetry.sdk.testing.exporter`:
```java
public final class InMemorySpanExporter implements SpanExporter {
    public static InMemorySpanExporter create();
    public List<SpanData> getFinishedSpanItems();
    public void reset();
}
public final class InMemoryLogRecordExporter implements LogRecordExporter {
    public static InMemoryLogRecordExporter create();
    public List<LogRecordData> getFinishedLogRecordItems();
    public void reset();
}
public final class InMemoryMetricReader implements MetricReader {
    public static InMemoryMetricReader create();  // cumulative temporality default
    public Collection<MetricData> collectAllMetrics();  // drains accumulator (RESEARCH §2.3)
    // NOTE: NO reset() method exists on InMemoryMetricReader (RESEARCH §2.3 / PATTERNS Cross-File Invariant #10)
}
```

From `io.opentelemetry.sdk.trace.export`:
```java
public final class SimpleSpanProcessor implements SpanProcessor {
    public static SpanProcessor create(SpanExporter exporter);  // synchronous; PITFALLS #4/#11
}
```

From `io.opentelemetry.sdk.logs.export`:
```java
public final class SimpleLogRecordProcessor implements LogRecordProcessor {
    public static LogRecordProcessor create(LogRecordExporter exporter);
}
```

From `io.opentelemetry.sdk.trace.samplers`:
```java
public final class Sampler {
    public static Sampler alwaysOn();  // D-18: every span captured (no parent-context check needed in tests)
}
```

From `io.opentelemetry.sdk.OpenTelemetrySdk`:
```java
public static OpenTelemetrySdkBuilder builder();
public OpenTelemetrySdkBuilder setTracerProvider(SdkTracerProvider tracerProvider);
public OpenTelemetrySdkBuilder setMeterProvider(SdkMeterProvider meterProvider);
public OpenTelemetrySdkBuilder setLoggerProvider(SdkLoggerProvider loggerProvider);
public OpenTelemetrySdkBuilder setPropagators(ContextPropagators propagators);
public OpenTelemetrySdk build();
```

From `io.opentelemetry.sdk.trace.SdkTracerProvider`:
```java
public static SdkTracerProviderBuilder builder();
public SdkTracerProviderBuilder setResource(Resource resource);
public SdkTracerProviderBuilder setSampler(Sampler sampler);
public SdkTracerProviderBuilder addSpanProcessor(SpanProcessor processor);  // verb: add (NOT register)
```

From `io.opentelemetry.sdk.metrics.SdkMeterProvider`:
```java
public static SdkMeterProviderBuilder builder();
public SdkMeterProviderBuilder setResource(Resource resource);
public SdkMeterProviderBuilder registerMetricReader(MetricReader reader);  // verb: register (the meter outlier)
```

From `io.opentelemetry.sdk.logs.SdkLoggerProvider`:
```java
public static SdkLoggerProviderBuilder builder();
public SdkLoggerProviderBuilder setResource(Resource resource);
public SdkLoggerProviderBuilder addLogRecordProcessor(LogRecordProcessor processor);
```

From `io.opentelemetry.context.propagation.ContextPropagators` + `TextMapPropagator`:
```java
public static ContextPropagators create(TextMapPropagator propagator);
public static TextMapPropagator composite(TextMapPropagator... propagators);
```

W3C propagators (production parity):
```java
io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance();
io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator.getInstance();
```

semconv constants (already on classpath via Phase 2 deps — semconv 1.40.0 stable + 1.40.0-alpha incubating):
```java
io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;        // AttributeKey<String>
io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAMESPACE;
io.opentelemetry.semconv.ServiceAttributes.SERVICE_INSTANCE_ID;
io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME;
```

CRITICAL FQCN (Risk #1 carryforward from Phase 5):
- `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` ← HAS install(); IMPORT THIS
- `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` ← MDC wrapper, no install(); DO NOT IMPORT

Production analog (producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java):
- Line 246 (approximate): `OpenTelemetryAppender.install(sdk);` is called BEFORE `return sdk;` inside the `@Bean openTelemetry()` factory body. This is the Phase 5 fix in commit f5c331a. TestOtelHolder.get() replicates this ordering exactly: install AFTER builder().build(), BEFORE the static SDK field is assigned.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Create TestOtelHolder.java with lazy-initialized SDK + InMemory exporters/reader + Phase 5 appender install ordering</name>
  <files>integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java</files>
  <read_first>
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 2 — paste-ready 80-line skeleton lines 101-203)
    - .planning/phases/06-verification-tests/06-RESEARCH.md (§3.3 OPEN-QUESTION-1 RESOLVED block lines 762-774; §2.3 InMemoryMetricReader has no reset() — collectAllMetrics() is the drain primitive; §2.4 sdk-testing AssertJ helpers; §2.6 Simple processors are synchronous)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-07 single shared SDK; D-07.1 holder pattern; D-09 install-ordering; D-16 InMemoryMetricReader registered directly; D-18 Sampler.alwaysOn(); D-19 ≥40 comment lines)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (read the full file — pay attention to the @Bean openTelemetry() factory body and the line where OpenTelemetryAppender.install(sdk) lands AFTER OpenTelemetrySdk.builder().build() and BEFORE return sdk; — that's the line we replicate)
    - integration-tests/pom.xml (verify opentelemetry-sdk-testing is on classpath)
  </read_first>
  <behavior>
    - First call to `TestOtelHolder.get()` lazy-initializes SPANS, LOGS, METRICS, builds the OpenTelemetrySdk, calls OpenTelemetryAppender.install(sdk), and assigns SDK = sdk. Returns the SDK.
    - Subsequent calls to get() return the SAME SDK instance immediately (idempotent — no rebuild).
    - Concurrent calls during initialization are serialized via the synchronized method (only one initialization happens).
    - After get() has been called once, the four static fields (SDK, SPANS, LOGS, METRICS) are non-null and reference the same instances forever (process lifetime).
    - Compile contract: the file compiles under `mvn -pl integration-tests -am test-compile` with no errors.
  </behavior>
  <action>
Use the Write tool to create `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`. Paste the following content VERBATIM (synthesized from PATTERNS §File 2 + RESEARCH §3.3 OPEN-QUESTION-1 resolution + CONTEXT D-07.1, with full JavaDoc and section banners for D-19 comment density):

```java
package com.example.e2e;

import java.util.UUID;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
// CRITICAL (Risk #1 / Phase 5 carryforward): two classes share the name
// OpenTelemetryAppender — import the appender.v1_0 one (HAS install()), NOT
// the mdc.v1_0 one (MDC wrapper, no install()).
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
 * launched by {@link OrderFlowIT}. Resolves CONTEXT.md D-07.1 + RESEARCH
 * OPEN-QUESTION-1.
 *
 * <p><b>Why a static holder, not @Bean?</b> Each {@code SpringApplicationBuilder}
 * context, even when it @Imports the same {@link TestOtelConfiguration}, yields
 * a SEPARATE bean instance (Spring DI scopes beans per-context). D-07 requires
 * ONE {@code OpenTelemetry} so a single in-memory queue captures spans from
 * both services. The holder is the smallest pedagogically-clean way to force
 * that — TestOtelConfiguration's @Bean methods become thin facades that
 * delegate here.
 *
 * <p><b>Lifecycle:</b> lazy init on first {@link #get()}; subsequent calls
 * return the same instances. {@link OpenTelemetryAppender#install(io.opentelemetry.api.OpenTelemetry)}
 * runs INSIDE {@link #get()} AFTER {@link OpenTelemetrySdk#builder()
 * OpenTelemetrySdk.builder()}.build() returns and BEFORE the static {@link #SDK}
 * reference is published — EXACT replication of Phase 5 commit {@code f5c331a}
 * install-ordering invariant (PITFALL #5 / D-09).
 *
 * <p><b>Divergences from production OtelSdkConfiguration:</b>
 * <ul>
 *   <li>NOT a {@code @Configuration} — bare {@code final class} with
 *       package-private static fields.</li>
 *   <li>{@link SimpleSpanProcessor} + {@link InMemorySpanExporter} (synchronous,
 *       deterministic) — production uses {@code BatchSpanProcessor} + OTLP
 *       (PITFALLS #4 / #11 — Batch loses telemetry on shutdown).</li>
 *   <li>{@link SimpleLogRecordProcessor} + {@link InMemoryLogRecordExporter}
 *       (synchronous) — production uses {@code BatchLogRecordProcessor} + OTLP.</li>
 *   <li>{@link InMemoryMetricReader} registered DIRECTLY — production wraps
 *       {@code OtlpGrpcMetricExporter} in {@code PeriodicMetricReader} with a
 *       10-second interval (D-16).</li>
 *   <li>{@link Sampler#alwaysOn()} — production uses
 *       {@code Sampler.parentBased(alwaysOn())} (D-18 — parent-based check is
 *       wasted work in tests since every span is captured anyway).</li>
 *   <li>{@code service.name="integration-test"} (single resource shape per
 *       D-07) — production uses {@code "order-producer"} / {@code "order-consumer"}
 *       per-service.</li>
 *   <li>Single shared instance across both contexts — production duplicates
 *       per service (Phase 2 D-01 / DOC-05; test infra exempt per D-07).</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> {@link #get()} is {@code synchronized}; subsequent
 * reads of the static fields are safe because the fields are {@code volatile}
 * (write-publish via {@code synchronized} block, reads via {@code volatile}
 * happens-before).
 *
 * <p><b>NOT replicated from production:</b> {@code @PreDestroy
 * uninstallLogbackAppender()}. {@link OrderFlowIT}'s {@code @AfterAll} closes
 * both Spring contexts; the {@code destroyMethod="close"} cascade on the
 * {@link OpenTelemetry} {@code @Bean} (defined in {@link TestOtelConfiguration})
 * shuts down the SDK. The appender is left bound to the closed SDK at JVM exit
 * — harmless because the test JVM exits immediately after.
 */
final class TestOtelHolder {

    /** The single {@link OpenTelemetrySdk} shared across both Spring contexts. */
    static volatile OpenTelemetrySdk SDK;

    /** In-memory span sink — {@link OrderFlowIT} reads via {@link InMemorySpanExporter#getFinishedSpanItems()}. */
    static volatile InMemorySpanExporter SPANS;

    /** In-memory log-record sink — {@link OrderFlowIT} reads via {@link InMemoryLogRecordExporter#getFinishedLogRecordItems()}. */
    static volatile InMemoryLogRecordExporter LOGS;

    /**
     * In-memory metric sink — synchronous; {@link OrderFlowIT} drains via
     * {@link InMemoryMetricReader#collectAllMetrics()}. NOTE:
     * {@link InMemoryMetricReader} has NO {@code reset()} method (verified
     * against SDK 1.61.0 source — RESEARCH §2.3); {@code collectAllMetrics()}
     * IS the drain primitive.
     */
    static volatile InMemoryMetricReader METRICS;

    private TestOtelHolder() {
        // utility class — not instantiable
    }

    /**
     * Returns the lazy-initialized {@link OpenTelemetrySdk}. First call builds
     * the SDK + 3 InMemory sinks + installs the Logback appender; subsequent
     * calls return the cached instance.
     *
     * @return the singleton SDK; never null after first call
     */
    static synchronized OpenTelemetrySdk get() {
        if (SDK != null) return SDK;

        // ----- In-memory sinks (sdk-testing artifact) -----
        SPANS = InMemorySpanExporter.create();
        LOGS = InMemoryLogRecordExporter.create();
        METRICS = InMemoryMetricReader.create();

        // ----- Resource: ONE shape used by both contexts (D-07) -----
        // service.name="integration-test" is sufficient because cross-service
        // assertions filter by SpanKind / messaging attributes, not by
        // service.name. UUID per JVM run keeps service.instance.id unique
        // across re-runs (production parity).
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, "integration-test")
                .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
                .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "test")
                .build()));

        // ----- Trace pipeline: SimpleSpanProcessor + InMemorySpanExporter -----
        // SimpleSpanProcessor is synchronous: every span.end() exports
        // immediately. PITFALLS #4 + #11 — Batch processor would race with
        // assertions and require Thread.sleep (anti-pattern, D-13).
        // Sampler.alwaysOn() (D-18): no parent-context check; every span
        // captured. parentBased() in production is correct because some spans
        // arrive with sampled=false from upstream — irrelevant in tests.
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.alwaysOn())
            .addSpanProcessor(SimpleSpanProcessor.create(SPANS))
            .build();

        // ----- Log pipeline: SimpleLogRecordProcessor + InMemoryLogRecordExporter -----
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(LOGS))
            .build();

        // ----- Metric pipeline: InMemoryMetricReader registered DIRECTLY (D-16) -----
        // Production wraps OtlpGrpcMetricExporter in
        // PeriodicMetricReader.builder(...).setInterval(Duration.ofSeconds(10)).
        // Tests skip the periodic wrapper because InMemoryMetricReader IS a
        // MetricReader and the test calls collectAllMetrics() synchronously
        // from each test method.
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(METRICS)
            .build();

        // ----- Propagators: identical to production (W3C TraceContext + Baggage) -----
        // The AMQP propagation pair (otel-bootstrap's TracingMessagePostProcessor +
        // TracingMessageListenerAdvice) reads the global propagator via
        // OpenTelemetry.getPropagators(). Test must register the SAME
        // propagators or the cross-service trace assertions in OrderFlowIT
        // (TEST-03 / TEST-04) silently fail.
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

        // ----- LOG-03 / PITFALL #5: install Logback appender BEFORE publishing SDK -----
        //
        // EXACT replication of Phase 5 commit f5c331a's install ordering:
        //   1. Build the SDK (above).
        //   2. Call OpenTelemetryAppender.install(sdk) so any Logback
        //      OpenTelemetryAppender instance in the LoggerContext (declared in
        //      producer/consumer logback-spring.xml) swaps its noop default
        //      OpenTelemetry reference for the real SDK; replay buffer flushes.
        //   3. Publish SDK reference (SDK = sdk below) — at this point both
        //      Spring contexts can call get() and observe the fully-wired SDK.
        //
        // Calling install() AFTER SDK = sdk would still work for THIS class
        // (no concurrent reader yet), but the order matches production for
        // pedagogical clarity. The synchronized method ensures atomic init.
        OpenTelemetryAppender.install(sdk);

        SDK = sdk;
        return SDK;
    }
}
```

DO NOT:
- Make the class `public` — it is package-private (final class, no `public`); only TestOtelConfiguration and OrderFlowIT in the same package access it.
- Import `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` (wrong class — has no install() method).
- Use `BatchSpanProcessor` or `BatchLogRecordProcessor` anywhere (PITFALLS #4 / #11 / D-13).
- Wrap METRICS in a `PeriodicMetricReader` (D-16 — register directly).
- Use `Sampler.parentBased(...)` (D-18 — production divergence is deliberate).
- Add a `@PreDestroy` or any uninstall hook (test JVM exits immediately after; harmless).
- Call `OpenTelemetryAppender.install(sdk)` AFTER `SDK = sdk;` (would deviate from Phase 5 commit f5c331a ordering — acceptance criterion `APPENDER-INSTALL-AFTER-BUILD` will fail).
- Add a `static OpenTelemetry GLOBAL` field or `GlobalOpenTelemetry.set(...)` call (production never sets the global — tests must follow).
- Use `cat << EOF` heredoc — use the Write tool with the verbatim block above.

After writing the file, run:
```bash
cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile
```

Expected: exit 0, with `[INFO] Compiled 1 source file` (or similar) in output.
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && test -f integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'final class TestOtelHolder' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'static synchronized OpenTelemetrySdk get' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'OpenTelemetryAppender.install(sdk)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'SimpleSpanProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'BatchSpanProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'SimpleLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'BatchLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'Sampler.alwaysOn()' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'parentBased' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && ! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -F '"integration-test"' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && grep -q 'W3CTraceContextPropagator' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && awk '/OpenTelemetrySdk sdk = OpenTelemetrySdk.builder/{seen_build=NR} /OpenTelemetryAppender.install/{seen_install=NR} /SDK = sdk/{seen_publish=NR} END{exit !(seen_build && seen_install && seen_publish && seen_build < seen_install && seen_install < seen_publish)}' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java && mvn -B -pl integration-tests -am test-compile</automated>
  </verify>
  <acceptance_criteria>
    - File exists at the canonical path
    - Package declaration: `grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - Class is `final class TestOtelHolder` (package-private, not public): `grep -qE '^final class TestOtelHolder' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - Private constructor: `grep -q 'private TestOtelHolder()' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - All four static volatile fields declared: `test $(grep -cE 'static\s+volatile\s+(OpenTelemetrySdk|InMemorySpanExporter|InMemoryLogRecordExporter|InMemoryMetricReader)\s+(SDK|SPANS|LOGS|METRICS)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java) -ge 4`
    - Synchronized lazy init guard: `grep -qE 'static\s+synchronized\s+OpenTelemetrySdk\s+get\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - Idempotent re-entry: `grep -q 'if (SDK != null) return SDK' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - SimpleSpanProcessor used; BatchSpanProcessor absent: `grep -q 'SimpleSpanProcessor' && ! grep -q 'BatchSpanProcessor'`
    - SimpleLogRecordProcessor used; BatchLogRecordProcessor absent
    - PeriodicMetricReader absent (D-16): `! grep -q 'PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - InMemoryMetricReader registered directly: `grep -q 'registerMetricReader(METRICS)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - Sampler.alwaysOn() used; parentBased absent: `grep -q 'Sampler.alwaysOn()' && ! grep -q 'parentBased'`
    - Correct OpenTelemetryAppender FQCN imported: `grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - Wrong FQCN NOT imported: `! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - Install ordering invariant (Phase 5 f5c331a): `awk '/OpenTelemetrySdk sdk = OpenTelemetrySdk.builder/{seen_build=NR} /OpenTelemetryAppender.install/{seen_install=NR} /SDK = sdk/{seen_publish=NR} END{exit !(seen_build < seen_install && seen_install < seen_publish)}'` exits 0
    - Resource service.name="integration-test": `grep -F '"integration-test"' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
    - W3C propagators wired: `grep -q 'W3CTraceContextPropagator' && grep -q 'W3CBaggagePropagator'`
    - `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile` exits 0
    - Comment density: `grep -E '^\s*(//|\*|/\*\*|/\*)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java | wc -l` is `>= 40` (D-19 bar)
  </acceptance_criteria>
  <done>
TestOtelHolder.java exists, compiles, and meets all 17 acceptance criteria. Phase 5 commit f5c331a install-ordering invariant replicated. PITFALLS #4/#11 mitigated (Simple processors, no Batch, no Thread.sleep, no PeriodicMetricReader). Ready for Plan 06-04 to delegate to it from @Bean facades.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test JVM → static singleton state | TestOtelHolder fields persist for the JVM lifetime; bounded by `mvn verify` invocation |
| OpenTelemetryAppender.install() → Logback LoggerContext | install() walks the global LoggerContext and rewires every OpenTelemetryAppender in producer/consumer logback-spring.xml; this is the same trust boundary as production |
| Test SDK → in-memory exporters | Spans/logs/metrics flow synchronously; no network egress (no OTLP exporter on test classpath) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-03-01 | Tampering | Wrong OpenTelemetryAppender FQCN imported (mdc.v1_0 vs appender.v1_0) | mitigate | Acceptance criterion `APPENDER-FQCN` greps for the correct import; `NO-MDC-APPENDER-IMPORT` greps that the wrong import is absent. Risk #1 carryforward from Phase 5 — the wrong class compiles but has no install() method, which would cause a method-not-found resolution failure caught by `mvn test-compile`. |
| T-06-03-02 | Tampering | Install ordering reversed (SDK = sdk before install) | mitigate | Acceptance criterion `APPENDER-INSTALL-AFTER-BUILD` is an awk script that asserts the line order: `OpenTelemetrySdk.builder().build()` < `OpenTelemetryAppender.install()` < `SDK = sdk`. Reversing this would compile but silently break log capture in OrderFlowIT test 2 (Phase 5 PITFALL #5 / commit f5c331a). |
| T-06-03-03 | Spoofing | Test SDK accidentally builds OTLP exporter and ships data to a real backend | mitigate | Plan 06-02's POM omits opentelemetry-exporter-otlp; Plan 06-03's class uses InMemory* exporters only. No OTLP API on classpath. Acceptance criterion `NO-PERIODIC-METRIC-READER` further verifies the periodic export wrapper is absent. |
| T-06-03-04 | Information Disclosure | UUID-based service.instance.id leaks JVM identity | accept | service.instance.id is a per-JVM-run random UUID; production parity. Test JVM has no PII. The UUID is observable only via the in-memory exporters which never leave the test JVM. |
| T-06-03-05 | Denial of Service | Synchronized get() serializes initialization across threads | accept | Initialization happens once in `@BeforeAll`; subsequent reads of `SDK`/`SPANS`/`LOGS`/`METRICS` are unsynchronized volatile reads (no contention). Even pathological concurrent get() calls during init are bounded by SDK construction time (~10ms). |
| T-06-03-06 | Repudiation | Test telemetry is not persisted across JVM restarts | accept | InMemory* exporters live only in the test JVM heap; they vanish on JVM exit. This is by design — test results are observed via JUnit assertions, not by inspecting persistent stores. |
| T-06-03-07 | Elevation of Privilege | static volatile field publication race | mitigate | Java Memory Model guarantees: a synchronized method's exit happens-before any volatile read of fields written inside the method. `SDK = sdk` inside the synchronized block + `volatile` field declaration means the four fields are safely published to all subsequent readers without further synchronization. |
| T-06-03-08 | Tampering | BOM-managed sdk-testing version drift bumps Simple-processor API | mitigate | opentelemetry-bom:1.61.0 is pinned in parent pom.xml. SimpleSpanProcessor.create() / SimpleLogRecordProcessor.create() / InMemoryMetricReader.create() / Sampler.alwaysOn() are stable since SDK 1.0 — no API drift risk in 1.61.x. |
</threat_model>

<verification>
- `test -f integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'final class TestOtelHolder' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'static synchronized OpenTelemetrySdk get' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'OpenTelemetryAppender.install(sdk)' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'SimpleSpanProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `! grep -q 'BatchSpanProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'SimpleLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `! grep -q 'BatchLogRecordProcessor' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `! grep -q 'PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'Sampler.alwaysOn()' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `! grep -q 'parentBased' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- `! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0' integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`
- Install-ordering awk gate (build → install → publish) passes
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile` exits 0
- Comment line count `>= 40`
</verification>

<success_criteria>
1. `TestOtelHolder.java` exists at `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` with package `com.example.e2e`.
2. Class is `final` with private constructor; four `static volatile` fields (SDK, SPANS, LOGS, METRICS); one `static synchronized OpenTelemetrySdk get()` method.
3. Lazy init builds SDK with: SimpleSpanProcessor + InMemorySpanExporter; SimpleLogRecordProcessor + InMemoryLogRecordExporter; InMemoryMetricReader registered directly (no PeriodicMetricReader); Sampler.alwaysOn(); W3C TraceContext + Baggage propagators; Resource carrying service.name="integration-test".
4. `OpenTelemetryAppender.install(sdk)` called AFTER `OpenTelemetrySdk.builder().build()` AND BEFORE `SDK = sdk` — Phase 5 commit f5c331a invariant replicated.
5. No `BatchSpanProcessor` / `BatchLogRecordProcessor` / `PeriodicMetricReader` / `parentBased` / `Thread.sleep` anywhere in the file.
6. Correct OpenTelemetryAppender FQCN imported (`appender.v1_0`); the `mdc.v1_0` sibling NOT imported (Risk #1).
7. `mvn -pl integration-tests -am test-compile` exits 0.
8. Comment density `>= 40` lines (D-19 bar).
</success_criteria>

<output>
After completion, create `.planning/phases/06-verification-tests/06-03-SUMMARY.md` with:
- Files created (1: TestOtelHolder.java) and line count
- Full file content as a code block
- `mvn -pl integration-tests -am test-compile` exit code (0) and trailing output
- Install-ordering verification: line numbers of `OpenTelemetrySdk sdk = OpenTelemetrySdk.builder` / `OpenTelemetryAppender.install(sdk)` / `SDK = sdk` (must be strictly ascending)
- Forward-link: Plan 06-04 will create `TestOtelConfiguration.java` whose @Bean methods delegate to `TestOtelHolder.get()` and the three static fields; Plan 06-05's `OrderFlowIT.java` reads `TestOtelHolder.SPANS / .LOGS / .METRICS` directly (NOT via `producerCtx.getBean(...)`)
</output>
