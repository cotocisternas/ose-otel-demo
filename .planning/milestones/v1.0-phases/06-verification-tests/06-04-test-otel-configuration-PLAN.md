---
phase: 06-verification-tests
plan: 04
type: execute
wave: 4
depends_on:
  - 06-03
files_modified:
  - integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java
autonomous: true
requirements:
  - TEST-02
tags:
  - opentelemetry
  - sdk-testing
  - test-configuration
  - bean-override
  - phase-6
must_haves:
  truths:
    - id: CONFIG-FILE-EXISTS
      description: "TestOtelConfiguration.java exists at the canonical e2e package path"
      verify: "test -f integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: PACKAGE-DECL
      description: "Package declaration is com.example.e2e"
      verify: "grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: TEST-CONFIGURATION-ANNOTATION
      description: "Class is annotated @TestConfiguration (Spring Boot test stereotype)"
      verify: "grep -q '@TestConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'import org.springframework.boot.test.context.TestConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: PUBLIC-CLASS
      description: "Class is public (Spring needs to instantiate via reflection from another package's @SpringApplicationBuilder)"
      verify: "grep -qE '^public class TestOtelConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: BEAN-NAME-OPENTELEMETRY
      description: "@Bean named 'openTelemetry' (matches production bean name for override-by-name to work — D-06 / Cross-File Invariant #1)"
      verify: "grep -qE '@Bean\\b.*\\n?\\s*.*OpenTelemetry\\s+openTelemetry\\s*\\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java || awk '/@Bean/{found=NR} found && /OpenTelemetry openTelemetry\\s*\\(\\)/{exit 0} END{exit 1}' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: BEAN-DESTROY-METHOD-CLOSE
      description: "@Bean(destroyMethod = \"close\") on OpenTelemetry — preserves Phase 2 D-15 lifecycle cascade"
      verify: "grep -qE '@Bean\\s*\\(\\s*destroyMethod\\s*=\\s*\"close\"\\s*\\)' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: BEAN-NAME-TRACER
      description: "@Bean named 'tracer' (matches production)"
      verify: "grep -qE 'Tracer\\s+tracer\\s*\\(\\s*OpenTelemetry' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: BEAN-NAME-METER
      description: "@Bean named 'meter' (matches production)"
      verify: "grep -qE 'Meter\\s+meter\\s*\\(\\s*OpenTelemetry' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: NO-LOGGER-BEAN
      description: "NO Logger @Bean (Phase 5 D-07 carryforward — application logs via SLF4J)"
      verify: "! grep -qE '@Bean[^@]+(io\\.opentelemetry\\.api\\.logs\\.)?Logger\\s+logger' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: SPAN-EXPORTER-BEAN
      description: "@Bean InMemorySpanExporter delegates to TestOtelHolder.SPANS"
      verify: "grep -qE 'InMemorySpanExporter\\s+inMemorySpanExporter\\s*\\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.SPANS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: LOG-EXPORTER-BEAN
      description: "@Bean InMemoryLogRecordExporter delegates to TestOtelHolder.LOGS"
      verify: "grep -qE 'InMemoryLogRecordExporter\\s+inMemoryLogRecordExporter\\s*\\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.LOGS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: METRIC-READER-BEAN
      description: "@Bean InMemoryMetricReader delegates to TestOtelHolder.METRICS"
      verify: "grep -qE 'InMemoryMetricReader\\s+inMemoryMetricReader\\s*\\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.METRICS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: OPENTELEMETRY-BEAN-DELEGATES
      description: "@Bean OpenTelemetry openTelemetry() returns TestOtelHolder.get()"
      verify: "awk '/OpenTelemetry openTelemetry\\s*\\(/,/^\\s*}/' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java | grep -q 'TestOtelHolder.get()'"
    - id: GET-CALLS-BEFORE-FIELDS
      description: "Each InMemory* @Bean calls TestOtelHolder.get() before reading the static field (idempotent init guard)"
      verify: "test $(grep -c 'TestOtelHolder.get();' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java) -ge 3"
    - id: COMPILES-CLEAN
      description: "integration-tests compiles cleanly with TestOtelConfiguration added (alongside TestOtelHolder)"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile"
    - id: NO-BATCH-PROCESSORS
      description: "Does NOT instantiate any Batch processor or PeriodicMetricReader (D-13 / D-16 carryforward — TestOtelConfiguration is a thin facade and must not duplicate SDK construction)"
      verify: "! grep -qE 'BatchSpanProcessor|BatchLogRecordProcessor|PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: NO-OTEL-APPENDER-INSTALL
      description: "Does NOT call OpenTelemetryAppender.install(...) directly — that runs inside TestOtelHolder.get() (D-09 install-ordering invariant lives in the holder, not here)"
      verify: "! grep -q 'OpenTelemetryAppender.install' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java"
    - id: COMMENT-DENSITY
      description: "Comment density ≥40 lines (D-19 bar carries to TestOtelConfiguration.java)"
      verify: "test $(grep -E '^\\s*(//|\\*|/\\*\\*|/\\*)' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java | wc -l) -ge 40"
  artifacts:
    - path: integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java
      provides: "@TestConfiguration with 6 @Bean facades over TestOtelHolder; overrides production OpenTelemetry/Tracer/Meter beans by name"
      contains: "@TestConfiguration"
      contains: "TestOtelHolder.get()"
      contains: "TestOtelHolder.SPANS"
      contains: "TestOtelHolder.LOGS"
      contains: "TestOtelHolder.METRICS"
  key_links:
    - from: integration-tests/.../TestOtelConfiguration.java @Bean methods
      to: integration-tests/.../TestOtelHolder.java static fields
      via: "Static field access from @Bean factory bodies"
      pattern: "TestOtelHolder\\."
    - from: integration-tests/.../TestOtelConfiguration.java @Bean openTelemetry
      to: producer-service / consumer-service production @Bean openTelemetry (override-by-name)
      via: "Spring's spring.main.allow-bean-definition-overriding=true (D-06; set by 06-05's SpringApplicationBuilder properties)"
      pattern: "OpenTelemetry openTelemetry\\(\\)"
    - from: integration-tests/.../TestOtelConfiguration.java @Bean(destroyMethod=\"close\")
      to: OpenTelemetrySdk.close() lifecycle cascade
      via: "Spring context close → @Bean destroy method → SdkTracerProvider.shutdown() / SdkMeterProvider.shutdown() / SdkLoggerProvider.shutdown()"
      pattern: "destroyMethod"
---

<objective>
Create `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` — a `@TestConfiguration` whose `@Bean` methods are thin facades over `TestOtelHolder` (created in 06-03). Each bean returns the singleton `TestOtelHolder` field (lazy-initializing the SDK on first call). Both `SpringApplicationBuilder` contexts in `OrderFlowIT.@BeforeAll` (Plan 06-05) `@Import` this class so the production `OpenTelemetry` / `Tracer` / `Meter` beans are overridden by name (D-06).

Purpose: Replace the production OTLP-exporting `OpenTelemetry` bean with the test SDK from `TestOtelHolder` in BOTH Spring contexts simultaneously. The override-by-name mechanism requires `spring.main.allow-bean-definition-overriding=true` (set per-context by Plan 06-05) plus matching `@Bean` names (`openTelemetry`, `tracer`, `meter`) AND the same `destroyMethod="close"` lifecycle shape (Phase 2 D-15 cascade). Honors CONTEXT D-05 (parallel SDK shape), D-06 (allow-bean-overriding + name-match), D-07.1 (delegation to TestOtelHolder), D-08 (Tracer + Meter beans, NO Logger bean — Phase 5 D-07 carryforward), D-19 (comment density ≥40).

Output: ONE new file `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` (~75 lines). Compiles cleanly via `mvn -pl integration-tests test-compile`.

Why this is wave 4: depends on 06-03 (TestOtelHolder must exist for the @Bean delegations to compile). Could not be merged with 06-03 because each plan touches a separate file with distinct purposes (one is the SDK construction, the other is the Spring DI surface — splitting matches the testability of each independently).

Why this is one focused plan: a single ~75-line file with one purpose (Spring-DI facade). No splitting opportunities.
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
@integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
@consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java

<interfaces>
<!-- API surface used by TestOtelConfiguration. -->

From `org.springframework.boot.test.context.TestConfiguration`:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration
public @interface TestConfiguration { /* test-stereotype variant of @Configuration */ }
```

From `org.springframework.context.annotation.Bean`:
```java
@Target(ElementType.METHOD)
public @interface Bean {
    String[] name() default {};       // empty = uses method name as bean name
    String destroyMethod() default ""; // we use "close" — Phase 2 D-15 lifecycle cascade
}
```

From `io.opentelemetry.api.OpenTelemetry`:
```java
public interface OpenTelemetry {
    Tracer getTracer(String instrumentationScopeName);
    Meter getMeter(String instrumentationScopeName);   // via getMeterProvider() in newer APIs but getMeter shortcut exists
    // ContextPropagators getPropagators();
    // close() inherited via OpenTelemetrySdk extends Closeable — Phase 2 D-15 cascade target
}
```

Production bean names (verbatim) — TestOtelConfiguration MUST match these so override-by-name works:
- producer/consumer `OtelSdkConfiguration.openTelemetry()` — bean name = method name = `openTelemetry`
- producer/consumer `OtelSdkConfiguration.tracer(OpenTelemetry openTelemetry)` — bean name = `tracer`
- producer/consumer `OtelSdkConfiguration.meter(OpenTelemetry openTelemetry)` — bean name = `meter`

The 3 InMemory @Bean methods (`inMemorySpanExporter`, `inMemoryLogRecordExporter`, `inMemoryMetricReader`) have NO production analogs — they are net-new beans for OrderFlowIT to constructor-inject (or read directly via TestOtelHolder; D-07.1 chooses the latter).

TestOtelHolder fields (from Plan 06-03):
```java
final class TestOtelHolder {  // package-private
    static volatile OpenTelemetrySdk SDK;
    static volatile InMemorySpanExporter SPANS;
    static volatile InMemoryLogRecordExporter LOGS;
    static volatile InMemoryMetricReader METRICS;
    static synchronized OpenTelemetrySdk get();  // lazy init guard
}
```

Phase 5 D-07 carryforward (verified against producer/consumer OtelSdkConfiguration): NO `Logger` @Bean. Application code uses SLF4J's `LoggerFactory.getLogger(...)`; the `OpenTelemetryAppender` declared in `logback-spring.xml` and installed via `OpenTelemetryAppender.install(SDK)` (which TestOtelHolder.get() does) bridges Logback events to the SDK's SdkLoggerProvider. TestOtelConfiguration honors this — no `@Bean Logger` method.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Create TestOtelConfiguration.java with 6 @Bean facades over TestOtelHolder + heavy comment block documenting divergences from production</name>
  <files>integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java</files>
  <read_first>
    - integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java (the holder this configuration delegates to — verify field names + get() signature)
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 3 — D-07.1 simplified body shape; the side-by-side production-vs-test divergence table at lines 318-330; Cross-File Invariant #1 about @Bean name override-by-name)
    - .planning/phases/06-verification-tests/06-RESEARCH.md (§3.2 — the original (pre-D-07.1) skeleton lines 249-428; ignore the SDK-construction body but borrow the JavaDoc structure and comment shape)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-05 parallel SDK shape; D-06 override-by-name + allow-bean-definition-overriding; D-07.1 holder pattern; D-08 Tracer + Meter beans, NO Logger; D-19 comment density)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (read the full @Bean openTelemetry() body lines ~120-249, @Bean tracer / @Bean meter; verify @Bean(destroyMethod="close") shape; verify NO @Bean Logger method exists in production)
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (same — verify mirror shape)
  </read_first>
  <behavior>
    - When Spring loads `TestOtelConfiguration` (via `@SpringApplicationBuilder.run()` import), each @Bean factory call invokes `TestOtelHolder.get()` (idempotent — first invocation across the JVM lazy-inits; subsequent calls return cached SDK).
    - The `OpenTelemetry` @Bean returns the same `OpenTelemetrySdk` instance for every Spring context that imports this class — D-07 single-shared-SDK invariant.
    - When the Spring context closes (`producerCtx.close()` / `consumerCtx.close()` in `@AfterAll`), the `destroyMethod="close"` cascade calls `OpenTelemetrySdk.close()` which shuts down all three providers.
    - Override-by-name: in a context that ALSO has `OtelSdkConfiguration` (the production one, picked up by component scan from `@SpringBootApplication`), Spring's `allow-bean-definition-overriding=true` (set by Plan 06-05) lets `TestOtelConfiguration`'s `openTelemetry` bean replace the production one because the bean NAMES match exactly.
    - Compile contract: `mvn -pl integration-tests -am test-compile` exits 0; the file compiles alongside TestOtelHolder.
  </behavior>
  <action>
Use the Write tool to create `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`. Paste the following content VERBATIM (synthesized from PATTERNS §File 3 D-07.1 simplified body shape + RESEARCH §3.2 JavaDoc structure):

```java
package com.example.e2e;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only OpenTelemetry SDK swap (Phase 6 / D-05..D-09 + D-07.1).
 *
 * <p>This {@code @TestConfiguration} is {@code @Import}ed by both Spring
 * contexts started in {@link OrderFlowIT}'s {@code @BeforeAll}. Each
 * {@code @Bean} method is a THIN FACADE over {@link TestOtelHolder} so that
 * BOTH contexts share the SAME {@link OpenTelemetry} instance and the SAME
 * three in-memory sinks — D-07's "ONE shared exporter sees both services"
 * intent (RESEARCH OPEN-QUESTION-1 resolved by D-07.1).
 *
 * <p><b>Read this file side-by-side with</b> {@code producer-service/.../OtelSdkConfiguration.java}.
 * The {@code @Bean} signatures match production line-for-line; only the bodies
 * differ (production builds the SDK; tests delegate to {@link TestOtelHolder}).
 *
 * <p><b>What changes vs production:</b>
 * <pre>
 *   Production                        | Test (via TestOtelHolder)
 *   ----------------------------------|------------------------------------------
 *   OtlpGrpcSpanExporter              | InMemorySpanExporter           (deterministic)
 *   BatchSpanProcessor                | SimpleSpanProcessor            (synchronous, PITFALLS #4/#11)
 *   OtlpGrpcLogRecordExporter         | InMemoryLogRecordExporter      (in-memory)
 *   BatchLogRecordProcessor           | SimpleLogRecordProcessor       (synchronous)
 *   OtlpGrpcMetricExporter            | InMemoryMetricReader           (no exporter — reader IS the sink)
 *   PeriodicMetricReader (10s)        | (none — InMemoryMetricReader registered DIRECTLY, D-16)
 *   parentBased(alwaysOn())           | alwaysOn()                     (D-18)
 *   service.name="order-producer/-consumer" | "integration-test"      (D-07 single-resource shape)
 *   Per-service file (duplicated)     | ONE file shared (test infra exempt from D-01)
 * </pre>
 *
 * <p><b>Why one shared file (not duplicated like production)?</b> Phase 2
 * D-01 / DOC-05 says SDK bootstrap is duplicated per-service for pedagogical
 * clarity. That rule applies to PRODUCTION code. Test infrastructure is
 * exempt (D-07): one InMemorySpanExporter must see ALL spans across BOTH
 * Spring contexts, so cross-service assertions in {@link OrderFlowIT} filter
 * by SpanKind / messaging.system rather than by service.name.
 *
 * <p><b>Why install(openTelemetry) is NOT here:</b> the install ordering
 * (Phase 5 commit f5c331a / PITFALL #5) lives inside {@link TestOtelHolder#get()},
 * not in this file. Calling {@code install()} from a {@code @PostConstruct}
 * on this {@code @Configuration} class would create a Spring self-cycle —
 * exactly what Phase 5 commit f5c331a fixed. Honor the holder.
 *
 * <p><b>Why no {@code @Bean Logger} method?</b> Phase 5 D-07 carryforward —
 * application code uses SLF4J via {@code LoggerFactory.getLogger(...)}; the
 * {@code OpenTelemetryAppender} declared in {@code logback-spring.xml} and
 * installed by {@link TestOtelHolder#get()} bridges Logback events to the
 * SDK's {@code SdkLoggerProvider}. Direct OTel Logger API usage is intended
 * for log bridges, not application code.
 *
 * <p><b>Override-by-name:</b> the @Bean methods named {@code openTelemetry},
 * {@code tracer}, {@code meter} match the production bean names exactly
 * (Cross-File Invariant #1 from PATTERNS.md §3). Combined with
 * {@code spring.main.allow-bean-definition-overriding=true} on each
 * {@code SpringApplicationBuilder} (set in {@link OrderFlowIT}), Spring
 * replaces the production scanned beans with these test ones in BOTH
 * contexts.
 */
@TestConfiguration
public class TestOtelConfiguration {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        TestOtelHolder.get();   // ensure SDK + sinks are initialized
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

    /**
     * Replaces the production {@code OpenTelemetry} bean by name (D-06).
     * {@code destroyMethod="close"} cascades to
     * {@code SdkTracerProvider.shutdown()} / {@code SdkMeterProvider.shutdown()}
     * / {@code SdkLoggerProvider.shutdown()} when the Spring context closes
     * (Phase 2 D-15 carryforward).
     */
    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        return TestOtelHolder.get();
    }

    /**
     * Test-side {@link Tracer} bean. Production sets the instrumentation
     * scope to {@code "com.example.producer"} / {@code "com.example.consumer"};
     * tests use {@code "com.example.integration-test"}. SimpleSpanProcessor
     * captures all spans regardless of scope, so the scope rename is
     * cosmetic — but documenting it surfaces the divergence to attendees
     * reading the diff.
     */
    @Bean
    Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.example.integration-test");
    }

    /**
     * Test-side {@link Meter} bean. Same scope override as {@code tracer()}.
     */
    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.example.integration-test");
    }
}
```

DO NOT:
- Add a `@Bean Logger logger(...)` method (Phase 5 D-07 carryforward — application logs via SLF4J).
- Build any SDK construction in this file (`OpenTelemetrySdk.builder()`, `SdkTracerProvider.builder()`, `SimpleSpanProcessor`, etc. — all live in TestOtelHolder per D-07.1).
- Call `OpenTelemetryAppender.install(...)` here (lives in TestOtelHolder.get(); putting it here would defeat D-09 ordering and risk Spring self-cycle).
- Use `@Configuration` instead of `@TestConfiguration` (test-stereotype is the canonical Spring Boot variant for `@Import` use in tests; `@TestConfiguration` does NOT compete with component scan in production code paths).
- Rename any of the 6 @Bean methods (override-by-name requires byte-exact name match: `openTelemetry`, `tracer`, `meter`; the other 3 names are net-new for test injection).
- Remove `destroyMethod="close"` from the OpenTelemetry @Bean (Phase 2 D-15 lifecycle cascade — without it, OpenTelemetrySdk.close() is never called when the Spring context closes; harmless in test JVM but surfaces a real divergence from production parity).
- Make the class `final` (Spring needs to subclass for proxying — `@Configuration` proxies are CGLIB-generated subclasses).
- Make any @Bean method `private` or `static` (Spring won't instantiate them).
- Use `cat << EOF` heredoc — use the Write tool with the verbatim block above.

After writing the file, run:
```bash
cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile
```

Expected: exit 0; compiles 2 source files (TestOtelHolder + TestOtelConfiguration).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && test -f integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q '@TestConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -qE '^public class TestOtelConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -qE '@Bean\s*\(\s*destroyMethod\s*=\s*"close"\s*\)' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -qE 'OpenTelemetry\s+openTelemetry\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -qE 'Tracer\s+tracer\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -qE 'Meter\s+meter\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.SPANS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.LOGS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.METRICS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && grep -q 'TestOtelHolder.get()' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && ! grep -qE '@Bean[^@]+(io\.opentelemetry\.api\.logs\.)?Logger\s+logger' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && ! grep -qE 'BatchSpanProcessor|BatchLogRecordProcessor|PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && ! grep -q 'OpenTelemetryAppender.install' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java && test $(grep -c 'TestOtelHolder.get();' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java) -ge 3 && mvn -B -pl integration-tests -am test-compile</automated>
  </verify>
  <acceptance_criteria>
    - File exists at the canonical path
    - Package: `grep -q '^package com.example.e2e;' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
    - `@TestConfiguration` annotation + correct import
    - Class is `public class TestOtelConfiguration` (not final, not nested)
    - `@Bean(destroyMethod = "close")` on the OpenTelemetry method (with optional whitespace tolerance via the regex)
    - Three production-bean-name-matching methods: `OpenTelemetry openTelemetry()`, `Tracer tracer(OpenTelemetry)`, `Meter meter(OpenTelemetry)` (override-by-name invariant)
    - Three InMemory @Bean facade methods present and delegating: `inMemorySpanExporter()` returns `TestOtelHolder.SPANS`; `inMemoryLogRecordExporter()` returns `TestOtelHolder.LOGS`; `inMemoryMetricReader()` returns `TestOtelHolder.METRICS`
    - `TestOtelHolder.get()` called inside the OpenTelemetry @Bean body (delegation invariant)
    - Each InMemory @Bean calls `TestOtelHolder.get();` (with semicolon — discarded return) to ensure init: `test $(grep -c 'TestOtelHolder.get();' file) -ge 3`
    - NO `@Bean` returning `Logger` (Phase 5 D-07 carryforward): `! grep -qE '@Bean[^@]+(io\.opentelemetry\.api\.logs\.)?Logger\s+logger' file`
    - NO Batch processor / PeriodicMetricReader anywhere (D-13 / D-16 carryforward; this file should not duplicate SDK construction)
    - NO `OpenTelemetryAppender.install(...)` call (D-09 ordering invariant lives in TestOtelHolder, not here): `! grep -q 'OpenTelemetryAppender.install' file`
    - Compiles cleanly: `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile` exits 0
    - Comment density ≥ 40 lines (D-19): `test $(grep -E '^\s*(//|\*|/\*\*|/\*)' file | wc -l) -ge 40`
  </acceptance_criteria>
  <done>
TestOtelConfiguration.java exists, compiles, and meets all 17 acceptance criteria. The 6 @Bean methods are thin facades over TestOtelHolder. Override-by-name discipline preserved (matches production OpenTelemetry / tracer / meter names). NO Logger @Bean, NO Batch processors, NO direct SDK construction, NO appender install — all of those live in TestOtelHolder per D-07.1 / D-09. Plan 06-05 can now @Import this class into both SpringApplicationBuilder contexts.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Spring DI container → @Bean factory invocation | Spring calls each @Bean method during context refresh; return value becomes the singleton bean |
| TestOtelConfiguration → TestOtelHolder static field access | Cross-class read of package-private volatile static fields; serialized by TestOtelHolder.get() synchronization |
| @Bean overrides → production component-scanned beans | `allow-bean-definition-overriding=true` (set by Plan 06-05) permits replacement-by-name |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-04-01 | Spoofing | @Bean name mismatch silently doubles beans (test bean coexists with production bean instead of replacing it) | mitigate | Acceptance criteria grep-match the EXACT method names `openTelemetry`, `tracer`, `meter` (case-sensitive). PATTERNS.md Cross-File Invariant #1 explicitly calls out this risk. If the override silently fails, OrderFlowIT (Plan 06-05) test 1 will fail with assertion errors about wrong span counts (production OTLP exporter would attempt to ship spans to localhost:4317 which is unavailable in the test JVM with no docker-compose running, OR send them silently to a dropped network if the host stack happens to be running). |
| T-06-04-02 | Tampering | Method renamed during refactor breaks override | mitigate | Comment block in the JavaDoc explicitly documents the override-by-name invariant; PATTERNS.md Cross-File Invariant #1 cross-references. Any future refactor reads the JavaDoc before changing names. |
| T-06-04-03 | Information Disclosure | Test SDK reachable from production code via Spring DI | accept | TestOtelConfiguration is in `src/test/java` — never on the production classpath. The class is `public` only because Spring needs to instantiate via reflection; it cannot leak into a production runtime build. |
| T-06-04-04 | Tampering | destroyMethod="close" missing → SDK never shutdown on context close | mitigate | Acceptance criterion `BEAN-DESTROY-METHOD-CLOSE` verifies the `destroyMethod="close"` token. Without it, the OpenTelemetrySdk lives in the static TestOtelHolder field even after Spring contexts close (~harmless in test JVM since process exit cleans up, but breaks the "matches production lifecycle" parity discipline). |
| T-06-04-05 | Spoofing | Logger @Bean accidentally added (would deviate from Phase 5 D-07) | mitigate | Acceptance criterion `NO-LOGGER-BEAN` greps for the absence of `@Bean Logger logger`. Phase 5 D-07 explicitly forbids this; the production `OtelSdkConfiguration` has no Logger bean either. |
| T-06-04-06 | Elevation of Privilege | @TestConfiguration loaded into production-like context | accept | `@TestConfiguration` is opt-in via `@Import` — it does NOT compete with component scan. Production code paths (e.g., a misconfigured `mise run dev`) never load this class because its source location (`src/test/java`) is excluded from production jars. |
| T-06-04-07 | Repudiation | TestOtelHolder.get() never called → bean returns null field | mitigate | The @Bean methods explicitly call `TestOtelHolder.get();` BEFORE returning the static field. Acceptance criterion `GET-CALLS-BEFORE-FIELDS` verifies ≥3 calls (one per InMemory @Bean). If the call were absent, the first context refresh would NPE on the field read — caught at Spring startup. |
| T-06-04-08 | Tampering | Bean method declared `private` or `static` (Spring would silently ignore) | mitigate | Method visibility is package-private (default in Java). Standard Spring convention. Compile errors would surface if `private` were used because `@Bean` requires Spring AOP proxy access. |
</threat_model>

<verification>
- `test -f integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -q '@TestConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -qE '^public class TestOtelConfiguration' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -qE '@Bean\s*\(\s*destroyMethod\s*=\s*"close"\s*\)' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -qE 'OpenTelemetry\s+openTelemetry\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -qE 'Tracer\s+tracer\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -qE 'Meter\s+meter\s*\(' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -q 'TestOtelHolder.SPANS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -q 'TestOtelHolder.LOGS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -q 'TestOtelHolder.METRICS' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `grep -q 'TestOtelHolder.get()' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `! grep -qE '@Bean[^@]+(io\.opentelemetry\.api\.logs\.)?Logger\s+logger' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `! grep -qE 'BatchSpanProcessor|BatchLogRecordProcessor|PeriodicMetricReader' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `! grep -q 'OpenTelemetryAppender.install' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`
- `test $(grep -c 'TestOtelHolder.get();' integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java) -ge 3`
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am test-compile` exits 0
- Comment density `>= 40` lines
</verification>

<success_criteria>
1. `TestOtelConfiguration.java` exists at `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java`.
2. Annotated `@TestConfiguration`; class is `public class TestOtelConfiguration` (Spring requires public + non-final for proxying).
3. 6 @Bean methods: `inMemorySpanExporter` / `inMemoryLogRecordExporter` / `inMemoryMetricReader` (delegate to TestOtelHolder static fields after calling `get()`); `openTelemetry()` with `destroyMethod="close"` (returns `TestOtelHolder.get()`); `tracer(OpenTelemetry)` (scope `com.example.integration-test`); `meter(OpenTelemetry)` (same scope).
4. NO Logger @Bean (Phase 5 D-07 carryforward).
5. NO Batch processor / PeriodicMetricReader / direct SDK construction / OpenTelemetryAppender.install() in this file (those all live in TestOtelHolder per D-07.1 / D-09).
6. @Bean names exactly match production for override-by-name (Cross-File Invariant #1).
7. `mvn -pl integration-tests -am test-compile` exits 0.
8. Comment density `>= 40` lines (D-19).
</success_criteria>

<output>
After completion, create `.planning/phases/06-verification-tests/06-04-SUMMARY.md` with:
- Files created (1: TestOtelConfiguration.java) and line count
- Full file content as a code block
- `mvn -pl integration-tests -am test-compile` exit code (0) and trailing output (should report 2 source files compiled)
- Verification of override-by-name invariant: bean method signatures from production OtelSdkConfiguration.java side-by-side with TestOtelConfiguration's matching signatures (must be byte-identical method names)
- Forward-link: Plan 06-05 will create OrderFlowIT.java which @Imports this class via `new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)` and reads telemetry via `TestOtelHolder.SPANS / .LOGS / .METRICS` (NOT via Spring DI from this class)
</output>
