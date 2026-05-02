---
phase: 05-logs-correlation
plan: 02
type: execute
wave: 2
depends_on:
  - 05-01
files_modified:
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - producer-service/src/main/resources/logback-spring.xml
autonomous: true
requirements:
  - LOG-01
  - LOG-02
  - LOG-03
  - LOG-04
tags:
  - opentelemetry
  - logback
  - sdk
  - producer
  - postconstruct
must_haves:
  truths:
    - id: LOG-01
      description: "Producer registers SdkLoggerProvider with BatchLogRecordProcessor + OtlpGrpcLogRecordExporter to :4317"
      verify: "grep -q 'private SdkLoggerProvider buildLoggerProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'BatchLogRecordProcessor' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'OtlpGrpcLogRecordExporter' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: LOG-01-wired
      description: "Orchestrator chains .setLoggerProvider() onto OpenTelemetrySdk.builder()"
      verify: "grep -q '\\.setLoggerProvider(loggerProvider)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: LOG-03
      description: "@PostConstruct method calls OpenTelemetryAppender.install(this.openTelemetry) AFTER the @Bean returns"
      verify: "grep -q '@PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'OpenTelemetryAppender.install(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: D-08-fqcn
      description: "Import is from appender.v1_0 (the OTLP class with install()), NOT mdc.v1_0 (Risk #1)"
      verify: "grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: D-07-javadoc
      description: "Class JavaDoc updated — no Logger @Bean per D-07 (replaces 'will add a Logger @Bean' promise)"
      verify: "grep -q 'no Logger @Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java || grep -q 'No Logger @Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: LOG-02
      description: "logback-spring.xml exists with BOTH the appender.v1_0 OTEL appender AND the mdc.v1_0 MDC_CONSOLE wrapper"
      verify: "test -f producer-service/src/main/resources/logback-spring.xml && grep -q 'class=\"io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender\"' producer-service/src/main/resources/logback-spring.xml && grep -q 'class=\"io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender\"' producer-service/src/main/resources/logback-spring.xml"
    - id: D-13-correction
      description: "logback-spring.xml does NOT contain a <turboFilter> element (Risk #2 / RESEARCH §1 correction applied)"
      verify: "! grep -q '<turboFilter' producer-service/src/main/resources/logback-spring.xml"
    - id: D-12-root-attaches-mdc
      description: "Root logger attaches to MDC_CONSOLE and OTEL — NOT to bare CONSOLE (per RESEARCH §6 corrected wiring)"
      verify: "awk '/<root /,/<\\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref=\"MDC_CONSOLE\"' && awk '/<root /,/<\\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref=\"OTEL\"' && ! awk '/<root /,/<\\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref=\"CONSOLE\"'"
    - id: LOG-04
      description: "Console pattern stamps trace_id and span_id from MDC with empty-default syntax"
      verify: "grep -F '[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]' producer-service/src/main/resources/logback-spring.xml"
    - id: D-10-no-spring-include
      description: "No <include resource=\"org/springframework/boot/logging/logback/defaults.xml\"/> (full override per D-10)"
      verify: "! grep -q 'spring/boot/logging/logback/defaults.xml' producer-service/src/main/resources/logback-spring.xml"
    - id: D-19-comment-density
      description: "Comment density on OtelSdkConfiguration.java exceeds 80 lines (Phase 5 additions push past Phase 4's ≥40 bar)"
      verify: "test $(grep -E '^\\s*(//|\\*|/\\*\\*|/\\*)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | grep -v '^\\s*\\*/' | wc -l) -ge 80"
    - id: build-clean
      description: "Producer compiles cleanly with the SDK + logback changes"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile"
    - id: D-09-pitfall-comment-markers
      description: "@PostConstruct PITFALL #5 comment block contains all five load-bearing markers (D-09: Logback init order, noop default, install() swap, GH #10307 link, lifecycle ordering — comment block is load-bearing per CONTEXT.md)"
      verify: "grep -q '10307' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q -i 'order-of-operations\\|order of operations' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q -i 'noop' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q -i 'replay\\|silent' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q -i 'lifecycle' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: smoke-no-error
      description: "Spring Boot starts without fatal logback errors (no 'Failed to instantiate' / 'unable to find class' for OpenTelemetryAppender)"
      verify: "echo 'manual smoke at exit gate — Plan 05-06 verifies; this is documented for executor reference'"
    - id: D-01-sibling-helper
      description: "buildLoggerProvider(Resource) lands as third sibling helper next to buildTracerProvider and buildMeterProvider (D-01 — Phase 4 D-01 carryforward; explicitly foreshadowed in existing JavaDoc)"
      verify: "grep -qE 'private\\s+SdkLoggerProvider\\s+buildLoggerProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -qE 'private\\s+SdkTracerProvider\\s+buildTracerProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -qE 'private\\s+SdkMeterProvider\\s+buildMeterProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: D-03-batch-defaults
      description: "BatchLogRecordProcessor used (not SimpleLogRecordProcessor) — D-03 mandates SDK-default batching for production-realistic export semantics"
      verify: "grep -q 'BatchLogRecordProcessor' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && ! grep -q 'SimpleLogRecordProcessor' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: D-04-otlp-endpoint-pattern
      description: "OTLP endpoint reuses System.getenv('OTEL_EXPORTER_OTLP_ENDPOINT') with DEFAULT_OTLP_ENDPOINT fallback (D-04 — Phase 2 D-12 / Phase 4 D-04 carryforward)"
      verify: "grep -q 'OtlpGrpcLogRecordExporter' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'OTEL_EXPORTER_OTLP_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'DEFAULT_OTLP_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: D-05-resource-shared
      description: "Resource is built once in @Bean orchestrator and passed into buildTracerProvider, buildMeterProvider, AND buildLoggerProvider — all three providers carry identical service.name etc. (D-05 — Phase 4 D-05 carryforward)"
      verify: "grep -q 'buildLoggerProvider(resource)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'buildTracerProvider(resource)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'buildMeterProvider(resource)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - id: D-06-lifecycle-cascade
      description: "No new @Bean(destroyMethod=...) for SdkLoggerProvider — lifecycle cascades from OpenTelemetrySdk.close() (D-06 — Phase 2 D-15 / Phase 4 D-07 carryforward; the existing OpenTelemetry @Bean's destroyMethod='close' already cascades to logger provider shutdown)"
      verify: "test $(grep -cE 'destroyMethod' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java) -le 1"
    - id: D-11-console-pattern
      description: "Console pattern is the locked D-11 string with bracketed [trace_id=... span_id=...] empty-default :- syntax — visually self-distinguishes traced from untraced lines"
      verify: "grep -F '%d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n' producer-service/src/main/resources/logback-spring.xml"
    - id: D-14-explicit-otel-appender-class
      description: "logback-spring.xml declares OTEL appender with explicit appender.v1_0.OpenTelemetryAppender FQCN — no <captureMdcAttributes>, no autoconfiguration (D-14)"
      verify: "grep -q 'class=\"io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender\"' producer-service/src/main/resources/logback-spring.xml && ! grep -q 'captureMdcAttributes' producer-service/src/main/resources/logback-spring.xml"
  artifacts:
    - path: producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
      provides: "buildLoggerProvider(Resource) helper + setLoggerProvider chain + @PostConstruct installLogbackAppender + JavaDoc fix for D-07"
      contains: "private SdkLoggerProvider buildLoggerProvider"
      contains: "@PostConstruct"
      contains: "OpenTelemetryAppender.install"
    - path: producer-service/src/main/resources/logback-spring.xml
      provides: "Logback config with CONSOLE + MDC_CONSOLE wrapper + OTEL appender, root attaches to MDC_CONSOLE and OTEL"
      contains: "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"
      contains: "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"
  key_links:
    - from: producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
      to: producer-service/src/main/resources/logback-spring.xml
      via: "@PostConstruct calls install() on the OTEL appender declared in the XML"
      pattern: "OpenTelemetryAppender.install"
    - from: producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
      to: SdkLoggerProvider via OpenTelemetrySdk.builder().setLoggerProvider chain
      via: "@Bean orchestrator builds and chains the logger provider"
      pattern: ".setLoggerProvider(loggerProvider)"
---

<objective>
Extend `producer-service`'s `OtelSdkConfiguration.java` with the third sibling helper `private SdkLoggerProvider buildLoggerProvider(Resource resource)` (parallel to the existing `buildTracerProvider` and `buildMeterProvider`), wire it via `.setLoggerProvider(...)` in the `@Bean` orchestrator, add the `@PostConstruct installLogbackAppender()` method that calls `OpenTelemetryAppender.install(this.openTelemetry)` (PITFALL #5 mitigation per LOG-03 + D-08 + D-09), update the class-level JavaDoc to remove the reversed "Logger @Bean" promise (D-07 / PATTERNS §S-4), AND create a NEW `producer-service/src/main/resources/logback-spring.xml` with the corrected wrapper-appender shape from RESEARCH §Code Excerpts §D (CORRECTING CONTEXT.md D-13's TurboFilter mental model per RESEARCH Finding #1).

Purpose: Wire the producer's logger pipeline (LOG-01), declare the OpenTelemetry Logback appender + MDC injector wrapper (LOG-02), and ensure the appender is installed AFTER the SDK bean is built (LOG-03 — neutralises PITFALL #5). The console pattern (D-11) stamps `[trace_id=... span_id=...]` for terminal-side correlation (LOG-04 producer-side).

Output: One Java file modified (~80 lines added), one new XML file created (~50 lines). Producer-service compiles cleanly. Smoke verification of the @PostConstruct lifecycle is deferred to Plan 05-06.

Why these two files in ONE plan: The `@PostConstruct install()` method's behavior depends on the OTEL appender being declared in `logback-spring.xml` (otherwise `install()` walks the LoggerContext and finds nothing to install onto). Tight coupling — the two changes only make sense together, and Plan 05-04's producer business logs depend on both being live.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/05-logs-correlation/05-CONTEXT.md
@.planning/phases/05-logs-correlation/05-RESEARCH.md
@.planning/phases/05-logs-correlation/05-PATTERNS.md
@.planning/phases/05-logs-correlation/05-01-SUMMARY.md
@.planning/phases/04-metrics/04-CONTEXT.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
@producer-service/pom.xml

<interfaces>
<!-- Source of truth for new APIs imported into OtelSdkConfiguration.java -->
<!-- All extracted from 05-RESEARCH.md Findings #1-4 + Code Excerpts §A-C -->

From `io.opentelemetry.exporter.otlp.logs` (already on classpath via opentelemetry-exporter-otlp from Phase 2):
```java
public class OtlpGrpcLogRecordExporter {
    public static OtlpGrpcLogRecordExporterBuilder builder();
}
public class OtlpGrpcLogRecordExporterBuilder {
    public OtlpGrpcLogRecordExporterBuilder setEndpoint(String endpoint);  // e.g., "http://localhost:4317"
    public OtlpGrpcLogRecordExporter build();
}
```

From `io.opentelemetry.sdk.logs.export` (transitively via opentelemetry-sdk):
```java
public class BatchLogRecordProcessor implements LogRecordProcessor {
    public static BatchLogRecordProcessorBuilder builder(LogRecordExporter exporter);
}
public interface LogRecordExporter { /* OtlpGrpcLogRecordExporter implements this */ }
```

From `io.opentelemetry.sdk.logs`:
```java
public class SdkLoggerProvider {
    public static SdkLoggerProviderBuilder builder();
}
public class SdkLoggerProviderBuilder {
    public SdkLoggerProviderBuilder setResource(Resource resource);
    public SdkLoggerProviderBuilder addLogRecordProcessor(LogRecordProcessor processor);  // verb is "add", not "register"
    public SdkLoggerProvider build();
}
```

From `io.opentelemetry.sdk.OpenTelemetrySdk` (extends OpenTelemetrySdkBuilder):
```java
public OpenTelemetrySdkBuilder setLoggerProvider(SdkLoggerProvider loggerProvider);  // chains; new in Phase 5
```

From `io.opentelemetry.instrumentation.logback.appender.v1_0` (NEW from Plan 05-01 dep):
```java
public class OpenTelemetryAppender {
    public static void install(OpenTelemetry openTelemetry);  // The CRITICAL static method
}
```
**WARNING (Risk #1):** Two classes named `OpenTelemetryAppender` exist:
- `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` ← HAS install()
- `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` ← MDC wrapper, no install()
Import the FIRST one in the @PostConstruct.

From `jakarta.annotation`:
```java
public @interface PostConstruct { }  // Spring 6.x / Spring Boot 3.x uses jakarta.annotation, NOT javax.annotation
```

From `org.springframework.beans.factory.annotation`:
```java
public @interface Autowired { }  // Field injection for @PostConstruct's openTelemetry handle
```

<!-- Existing patterns to mirror (analog: buildMeterProvider) -->
buildMeterProvider helper (producer/OtelSdkConfiguration.java lines 250-293) is the byte-for-byte sibling; new buildLoggerProvider mirrors its 3-section banner shape (Exporter / Processor / Provider).

DEFAULT_OTLP_ENDPOINT constant (line 79): "http://localhost:4317" — reuse for the log exporter.

Existing orchestrator (lines 97-160): adds two lines (the SdkLoggerProvider local + the .setLoggerProvider() chain).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Extend producer's OtelSdkConfiguration.java with buildLoggerProvider helper, orchestrator wiring, @PostConstruct install method, and JavaDoc fix for D-07</name>
  <files>producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (full file — lines 1-349; pay attention to: imports lines 1-29, class JavaDoc lines 31-68, openTelemetry() @Bean lines 97-160, buildMeterProvider analog lines 227-293, Tracer/Meter @Beans lines 295-327)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §A — verbatim buildLoggerProvider body; §B — orchestrator diff; §C — @PostConstruct install method)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§A buildLoggerProvider analog; §B orchestrator update; §C @PostConstruct shape; §S-2 endpoint pattern; §S-4 comment density; §S-5 section banners)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-01 sibling-helper; D-04 endpoint reuse; D-05 Resource sharing; D-06 lifecycle cascade; D-07 NO Logger @Bean; D-08 install location; D-09 PITFALL #5 comment block)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-18b — why Phase 4 deliberately put @PostConstruct in QueueDepthGauge; Phase 5 reverses this for the appender install since both services need it symmetrically)
  </read_first>
  <action>
Apply the following edits to `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`. Each edit cites the line numbers BEFORE editing (in the current file).

**EDIT 1 — Update imports (currently lines 1-29).** Add the following imports preserving alphabetical order within the io.opentelemetry.* and jakarta.* groupings. The current import order (lines 7-29) has: `io.opentelemetry.api.*`, `io.opentelemetry.context.propagation.*`, `io.opentelemetry.exporter.otlp.metrics.*`, `io.opentelemetry.exporter.otlp.trace.*`, `io.opentelemetry.sdk.*`, `io.opentelemetry.semconv.*`, blank line, `org.springframework.context.*`. Insert:

- Add after line 16 (the `io.opentelemetry.exporter.otlp.trace.*` import — keeping `.metrics.* / .trace.* / .logs.*` is order-preserving but the package convention from PATTERNS §A says `.logs` is sibling to `.metrics` and `.trace`; alphabetically `.logs` < `.metrics` < `.trace`, so insert BEFORE the metrics import to keep alpha order):
  ```java
  import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
  ```
  Resulting order in that group: `.logs` (new), `.metrics` (existing), `.trace` (existing).

- Add the `instrumentation.logback.appender.v1_0` import. Group immediately AFTER all `io.opentelemetry.exporter.*` imports and BEFORE `io.opentelemetry.sdk.*`. With a leading section comment to call out Risk #1 inline:
  ```java
  // CRITICAL (RESEARCH Risk #1): two classes share the name OpenTelemetryAppender —
  // import the appender.v1_0 one (has install()), NOT the mdc.v1_0 one (wrapper).
  import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
  ```

- Add SDK logs imports after the existing `io.opentelemetry.sdk.metrics.*` lines 18-19 (sorted: `.logs` < `.metrics`, so they go BEFORE metrics — but to preserve readability of the existing block, insert AFTER `io.opentelemetry.sdk.metrics.export.PeriodicMetricReader` (line 19) and BEFORE `io.opentelemetry.sdk.resources.Resource` (line 20)):
  ```java
  import io.opentelemetry.sdk.logs.SdkLoggerProvider;
  import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
  import io.opentelemetry.sdk.logs.export.LogRecordExporter;
  ```

- Add `jakarta.annotation.PostConstruct` import. Place in a new import group BETWEEN the `io.opentelemetry.*` block (ends at current line 26) and the `org.springframework.*` block (currently starting at line 28), with a blank line separator:
  ```java
  import jakarta.annotation.PostConstruct;
  ```

- Add `org.springframework.beans.factory.annotation.Autowired` import. Place inside the existing `org.springframework.*` group, alphabetically before `org.springframework.context.annotation.Bean`:
  ```java
  import org.springframework.beans.factory.annotation.Autowired;
  ```

Final new total imports added: 6 (one with a 2-line comment).

**EDIT 2 — Update class-level JavaDoc (currently lines 31-68) to fix D-07 (no Logger @Bean) and reflect Phase 5 landing the helper.**

Replace lines 50-51 (currently `Phase 5 will reuse the same pattern when wiring the logger provider.`) with:
```
 * The same env-var-with-fallback pattern is used by Phase 5's
 * {@link #buildLoggerProvider(Resource)} for the log exporter.
```

Replace lines 60-67 (the entire `<p><b>Why three @Beans (openTelemetry / tracer / meter)?</b>` paragraph) with:
```
 * <p><b>Why three @Beans (openTelemetry / tracer / meter)?</b> The
 * {@link #openTelemetry()} @Bean is the orchestrator that builds the SDK
 * by delegating to {@link #buildTracerProvider(Resource)} (Phase 2's
 * trace pipeline), {@link #buildMeterProvider(Resource)} (Phase 4's
 * metric pipeline), AND {@link #buildLoggerProvider(Resource)} (Phase 5's
 * log pipeline) — see D-01 in 04-CONTEXT.md and 05-CONTEXT.md. The
 * {@code Tracer} and {@code Meter} @Beans are thin "instrumentation
 * scope" handles that call sites constructor-inject.
 *
 * <p><b>No Logger @Bean (D-07).</b> Unlike traces and metrics, the
 * workshop deliberately does NOT expose an OTel SDK {@code Logger} @Bean.
 * Application code uses SLF4J via {@code LoggerFactory.getLogger(...)};
 * the {@link OpenTelemetryAppender} configured in
 * {@code logback-spring.xml} bridges Logback events to the SDK's
 * {@link SdkLoggerProvider}. This is the OTel-recommended pattern for
 * application code — direct OTel Logger API usage is intended for log
 * bridges (like the appender itself), not for application-side logging.
```

**EDIT 3 — Update the orchestrator @Bean openTelemetry() body (currently lines 97-160).**

Inside the orchestrator, locate the section banner comment at line 124 (`// ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) -----`) and the local-variable block at lines 133-134.

Replace line 124 with:
```java
        // ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) + logs (Phase 5 / D-01) -----
```

Replace lines 133-134 with three lines (whitespace-aligned for readability — match Phase 4's column-alignment shape):
```java
        SdkTracerProvider tracerProvider = buildTracerProvider(resource);
        SdkMeterProvider  meterProvider  = buildMeterProvider(resource);
        SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);
```

Replace the comment block at lines 148-154 (the `// ----- The SDK itself -----` block plus the `.setMeterProvider(meterProvider) is THE single new builder line that Phase 4 contributes` paragraph) with:
```java
        // ----- The SDK itself -----
        //
        // .setLoggerProvider(loggerProvider) is THE single new builder line that
        // Phase 5 contributes to the orchestrator (D-01). The destroyMethod="close"
        // on this @Bean cascades through OpenTelemetrySdk.close() to ALL THREE
        // SdkTracerProvider.shutdown() AND SdkMeterProvider.shutdown() AND
        // SdkLoggerProvider.shutdown() — no new lifecycle annotation needed for
        // the logger pipeline (D-06 / Phase 4 D-07 / Phase 2 D-15 carryforward).
```

In the `return OpenTelemetrySdk.builder()` chain (currently lines 155-159), add `.setLoggerProvider(loggerProvider)` between `.setMeterProvider(meterProvider)` and `.setPropagators(propagators)`:
```java
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(propagators)
            .build();
```

**EDIT 4 — Add the @Autowired field for the OpenTelemetry handle.**

Insert immediately AFTER the `@Configuration / public class OtelSdkConfiguration {` opening brace (line 70) and BEFORE the `private static final String DEFAULT_OTLP_ENDPOINT` line 79. Place a single-line blank above and below:

```java

    /**
     * SDK handle injected by Spring after the {@link #openTelemetry()} @Bean
     * factory returns. Used by {@link #installLogbackAppender()} to wire the
     * {@link OpenTelemetryAppender} (LOG-03 / D-08).
     *
     * <p>The {@code @Autowired} on a field shape (rather than assigning
     * {@code this.openTelemetry = sdk} inside the @Bean factory body) is the
     * RESEARCH §C recommendation — makes the dependency visible in the field
     * declaration and reads naturally to a workshop attendee.
     */
    @Autowired
    private OpenTelemetry openTelemetry;

```

**EDIT 5 — Add the @PostConstruct installLogbackAppender() method.**

Insert immediately AFTER the existing `openTelemetry()` @Bean factory close brace (currently line 160) and BEFORE the buildTracerProvider JavaDoc (currently line 162). Paste this verbatim from RESEARCH §Code Excerpts §C, with one minor edit (the JavaDoc text below is the verbatim block):

```java

    /**
     * Wires the OTLP log-export appender to the SDK AFTER the @Bean factory
     * has returned (LOG-03 + PITFALL #5 mitigation / D-08 + D-09).
     *
     * <p><b>The order-of-operations problem:</b> Logback initializes BEFORE the
     * Spring ApplicationContext is built. Spring Boot's LoggingApplicationListener
     * loads logback-spring.xml from classpath at startup, which constructs an
     * {@link OpenTelemetryAppender} instance with its OpenTelemetry reference
     * defaulting to {@link io.opentelemetry.api.OpenTelemetry#noop()}. Every log
     * event emitted between Logback init and {@code install()} is buffered in
     * the appender's replay queue (default 1000 events — knob:
     * {@code <numLogsCapturedBeforeOtelInstall>} in logback-spring.xml).
     *
     * <p><b>Why this method runs AFTER the @Bean factory:</b> Spring's lifecycle
     * guarantees @PostConstruct runs after all @Bean factory methods return AND
     * after dependency injection completes. By the time {@code installLogbackAppender()}
     * fires, {@code this.openTelemetry} is the fully-built SDK with its
     * {@link SdkLoggerProvider} ready to receive log records.
     *
     * <p><b>What install() does:</b> Walks the global {@link ch.qos.logback.classic.LoggerContext},
     * finds every {@link OpenTelemetryAppender} (including ones nested inside
     * wrapper appenders like the MDC injector), and swaps the noop OpenTelemetry
     * reference for the real SDK. The replay queue is then drained — logs from
     * BEFORE this method ran are forwarded to the OTLP exporter, retroactively
     * stamped with attributes from the (now valid) OpenTelemetry instance.
     *
     * <p><b>Documented quirk:</b> This is the entire reason PITFALL #5 exists.
     * See <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307">
     * open-telemetry/opentelemetry-java-instrumentation#10307</a>. The replay
     * buffer (added in earlier 1.x) softens the loss but does NOT eliminate it:
     * if install() is never called, logs beyond the 1000-event buffer are
     * permanently dropped.
     *
     * <p><b>Idempotency:</b> install() is safe to call multiple times — the
     * appender's volatile OpenTelemetry field is simply reassigned. Calling it
     * with {@link io.opentelemetry.api.OpenTelemetry#noop()} effectively
     * "uninstalls" exporting (used in Phase 6 tests).
     *
     * <p><b>FQCN landmine (RESEARCH Risk #1):</b> the import at the top of this
     * file is {@code io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender}
     * — the OTLP-export class that has the static {@code install()} method.
     * The sibling artifact {@code opentelemetry-logback-mdc-1.0} ships a class
     * with the SAME name in {@code .mdc.v1_0} package — that one does NOT have
     * {@code install()} and is NOT what we want here.
     */
    @PostConstruct
    void installLogbackAppender() {
        OpenTelemetryAppender.install(this.openTelemetry);
    }
```

**EDIT 6 — Add the buildLoggerProvider(Resource) helper.**

Insert immediately AFTER the existing `buildMeterProvider` close brace (currently line 293) and BEFORE the `Tracer` @Bean JavaDoc (currently line 295). Paste this verbatim from RESEARCH §Code Excerpts §A:

```java

    /**
     * Logger pipeline added in Phase 5 — sibling to {@link #buildTracerProvider(Resource)}
     * (Phase 2) and {@link #buildMeterProvider(Resource)} (Phase 4).
     *
     * <p>Three SDK touch points (read top-to-bottom):
     * <ol>
     *   <li>{@link OtlpGrpcLogRecordExporter} — same artifact and same env-var
     *       fallback as the trace + metric exporters (D-04). The
     *       opentelemetry-exporter-otlp jar already on the classpath since Phase 2
     *       ships span + metric + log exporters in three sub-packages of one
     *       artifact; no new pom dependency on the SDK side.</li>
     *   <li>{@link BatchLogRecordProcessor} with {@code .builder(logExporter).build()}
     *       — production-shape batching pipeline (LOG-01 / D-03). Default schedule
     *       delay for log records is 1 second (faster than BatchSpanProcessor's
     *       5-second default — RESEARCH Finding #4); the demo accepts the SDK
     *       defaults.</li>
     *   <li>{@link SdkLoggerProvider} with {@code .setResource(resource)} — same
     *       Resource as the tracer + meter pipelines (D-05) so logs in Loki, traces
     *       in Tempo, and metrics in Mimir share an identical service identity for
     *       cross-signal correlation.</li>
     * </ol>
     *
     * <p><b>No application code calls the OTel Logger API directly.</b> Application
     * code uses SLF4J's LoggerFactory; the OpenTelemetryAppender (configured in
     * logback-spring.xml) bridges Logback events to the SDK's LogRecordProcessor
     * pipeline. This is the OTel-recommended pattern for application code (D-07).
     */
    private SdkLoggerProvider buildLoggerProvider(Resource resource) {
        // ----- OTLP gRPC log-record exporter: ships log records to grafana/otel-lgtm :4317 -----
        //
        // Reuses the SAME endpoint pattern as the span + metric exporters — System.getenv
        // with the DEFAULT_OTLP_ENDPOINT fallback (D-04 / Phase 4 D-04 / Phase 2 D-12
        // carryforward). Single artifact (opentelemetry-exporter-otlp) ships
        // OtlpGrpcSpanExporter, OtlpGrpcMetricExporter, AND OtlpGrpcLogRecordExporter
        // — three sub-packages, one jar. Verify with
        // `mvn dependency:tree -Dincludes=io.opentelemetry:opentelemetry-exporter-otlp`.
        String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
            .orElse(DEFAULT_OTLP_ENDPOINT);
        LogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(endpoint)
            .build();

        // ----- BatchLogRecordProcessor: production-shape batching pipeline (LOG-01 / D-03) -----
        //
        // .builder(logExporter).build() picks up the canonical defaults:
        //   schedule delay  = 1000 ms   (NOTE: different from BatchSpanProcessor's 5000 ms
        //                                — RESEARCH Finding #4. Logs are higher-volume and
        //                                lower-latency-tolerant than traces.)
        //   max queue size  = 2048
        //   max export batch = 512
        //   exporter timeout = 30000 ms
        // We deliberately use defaults — they're production-grade. Phase 6 will
        // swap to SimpleLogRecordProcessor in @TestConfiguration so test
        // assertions are deterministic.
        BatchLogRecordProcessor logProcessor = BatchLogRecordProcessor.builder(logExporter).build();

        // ----- LoggerProvider: assembles resource + processor -----
        return SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(logProcessor)
            .build();
    }
```

DO NOT:
- Use `import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` (the WRONG class — has no install() method per Risk #1).
- Add a `Logger` @Bean (D-07 explicitly forbids this — would require adding `import io.opentelemetry.api.logs.Logger` and an `openTelemetry.getLogsBridge().get(...)` call; both forbidden).
- Use `javax.annotation.PostConstruct` (Spring 6.x / Spring Boot 3.x switched to `jakarta.annotation`).
- Place the @PostConstruct method INSIDE the openTelemetry() factory body (must be a separate method on the class — Spring's lifecycle calls @PostConstruct after factory returns).
- Use `BatchLogRecordProcessor.create(exporter)` — the API is `.builder(exporter).build()` (Finding #4 verified).
- Use the verb `registerLogRecordProcessor` (METHOD DOES NOT EXIST — verb is `addLogRecordProcessor`, parallel to `addSpanProcessor` on tracer; meter provider's `registerMetricReader` is the OUTLIER on this naming pattern).
- Add a `<destroyMethod>` to `@PostConstruct` (Spring's @PreDestroy is the close mirror; D-06 says NO new destroyMethod is needed since OpenTelemetrySdk.close() cascades).
- Touch the existing `httpServerSpanFilter` @Bean (lines 345-348) — out of scope for Phase 5.
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'private SdkLoggerProvider buildLoggerProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'BatchLogRecordProcessor' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'OtlpGrpcLogRecordExporter' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q '\.setLoggerProvider(loggerProvider)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q '@PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'OpenTelemetryAppender.install(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && ! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'No Logger @Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && mvn -B -pl producer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q 'private SdkLoggerProvider buildLoggerProvider(Resource resource)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `grep -q 'BatchLogRecordProcessor' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `grep -q 'OtlpGrpcLogRecordExporter' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `grep -q '\.setLoggerProvider(loggerProvider)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `grep -q '@PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `grep -q 'OpenTelemetryAppender.install(this.openTelemetry)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
    - `! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (mdc class NOT imported in the .java — it lives ONLY in the .xml)
    - `grep -q 'import jakarta.annotation.PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (proves jakarta-not-javax)
    - `grep -q 'No Logger @Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-07 JavaDoc fix landed)
    - `! grep -q 'will add a sibling.*Logger @Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (the old "will add Logger @Bean" promise is REMOVED — D-07 reversal)
    - `grep -E '@PostConstruct\s*$' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` matches at least once (annotation on its own line)
    - `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile` exits 0
    - Comment line count: `grep -E '^\s*(//|\*|/\*\*|/\*)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | wc -l` >= 80 (D-19 density bar: Phase 5 additions push past Phase 2's ≥40 baseline)
    - PITFALL #5 marker — GH issue link: `grep -q '10307' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-09 / load-bearing comment — opentelemetry-java-instrumentation#10307)
    - PITFALL #5 marker — order-of-operations narrative: `grep -q -i 'order-of-operations\|order of operations' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-09 / Logback-init-before-Spring-context callout)
    - PITFALL #5 marker — noop default callout: `grep -q -i 'noop' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-09 / OpenTelemetry.noop() default before install())
    - PITFALL #5 marker — replay/silent buffer callout: `grep -q -i 'replay\|silent' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-09 / 1000-event replay queue + silent-no-op trap)
    - PITFALL #5 marker — lifecycle ordering callout: `grep -q -i 'lifecycle' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-09 / @PostConstruct lifecycle ordering — runs after @Bean factory + dependency injection)
  </acceptance_criteria>
  <done>
producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java has: (1) the new buildLoggerProvider helper with verbatim RESEARCH §A body, (2) the orchestrator's two new lines (local + chained setLoggerProvider call), (3) the @Autowired OpenTelemetry field + @PostConstruct installLogbackAppender method with full PITFALL #5 comment block, (4) JavaDoc fixed to reflect D-07 (no Logger @Bean), (5) imports updated with the appender.v1_0 OpenTelemetryAppender + jakarta.annotation.PostConstruct + Autowired + 4 new SDK imports. `mvn -pl producer-service -am compile` exits 0.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Create producer-service/src/main/resources/logback-spring.xml with the corrected wrapper-appender shape</name>
  <files>producer-service/src/main/resources/logback-spring.xml</files>
  <read_first>
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §D — verbatim XML block; §Finding #1 — TurboFilter correction; §Finding #6 — root attaches to MDC_CONSOLE not CONSOLE; §Risk #2 — silent FQCN typos are warnings not errors; §Risk #3 — application.properties logging.* overrides)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-10 — full Spring Boot defaults override; D-11 — locked console pattern; D-12 — both CONSOLE and OTEL on root [CORRECTED: root attaches to MDC_CONSOLE wrapper, not CONSOLE directly]; D-13 — MDC injector [CORRECTED to wrapper appender]; D-14 — OTEL appender FQCN)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§G — no analog, use RESEARCH §D verbatim; §S-3 byte-identical to consumer's logback-spring.xml — Plan 05-03 mirrors this file)
    - producer-service/src/main/resources/application.yaml (verify NO logging.* properties exist that could override the pattern per RESEARCH Risk #3)
  </read_first>
  <action>
Create the new file `producer-service/src/main/resources/logback-spring.xml` with the EXACT content below. This is the corrected wrapper-appender shape from RESEARCH §Code Excerpts §D — supersedes CONTEXT.md D-13's TurboFilter mental model per RESEARCH Finding #1.

Use the Write tool. The file MUST be byte-for-byte the content below (Plan 05-03 will create the IDENTICAL file in consumer-service per D-10):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Phase 5: Logs Correlation — full Spring Boot defaults override (D-10).

  No <include resource="org/springframework/boot/logging/logback/defaults.xml"/>:
  this file is the source of truth for log formatting. Pedagogical justification —
  no inherited magic; every appender visible in this file.

  Two appenders + one MDC-wrapper appender + root attaches to both:

    ROOT (INFO)
     ├── MDC_CONSOLE (mdc.v1_0.OpenTelemetryAppender — injects trace_id/span_id into MDC,
     │                 then forwards to its child)
     │    └── CONSOLE (ConsoleAppender — renders the bracketed pattern with %mdc{trace_id:-})
     └── OTEL (appender.v1_0.OpenTelemetryAppender — emits OTLP log records to :4317;
               trace_id/span_id come from Span.current() directly, NOT from MDC)

  The MDC injector is an APPENDER WRAPPER, NOT a TurboFilter — verified against
  opentelemetry-logback-mdc-1.0:2.27.0-alpha source (see 05-RESEARCH.md Finding #1).
  CONTEXT.md D-13 originally described a TurboFilter; that mental model is wrong
  (RESEARCH §1) — corrected here.

  CRITICAL: TWO classes named OpenTelemetryAppender ship with Phase 5 deps —
  one in .appender.v1_0 (OTLP export) and one in .mdc.v1_0 (MDC wrapper). Both
  are referenced below by FQCN to avoid Java import ambiguity (RESEARCH Risk #1).
-->
<configuration>

  <!--
    CONSOLE: terminal output. The %mdc{trace_id:-} default-value syntax means
    startup logs render `[trace_id= span_id=]` (brackets stay, values empty);
    in-span logs render `[trace_id=4b2e... span_id=ad12...]`. The empty default
    is Logback 1.5.x standard syntax — confirmed by Logback layouts manual
    (RESEARCH Finding #5).

    Pattern locked by D-11:
      %d{HH:mm:ss.SSS} — time-only (workshop runs in one calendar day)
      [%thread]        — load-bearing teaching element: producer logs run on
                         [http-nio-8080-exec-N], consumer on
                         [...RabbitListenerEndpointContainer#0-1]; thread names
                         make the async hand-off across the AMQP boundary visible
      %-5level         — left-justified 5-char level (INFO / ERROR / WARN )
      [trace_id=... span_id=...] — bracketed prefix, empty when no active span
      %logger{36}      — abbreviated FQCN to 36 chars
      - %msg%n         — message and newline
  -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!--
    MDC_CONSOLE: opentelemetry-logback-mdc-1.0's wrapper appender. Reads
    Span.current() and writes trace_id/span_id/trace_flags into the event's
    MDC just-in-time, then forwards the event to its <appender-ref> child.

    Default MDC keys (trace_id / span_id / trace_flags) match D-11's pattern;
    no <traceIdKey> / <spanIdKey> overrides needed. Workshop attendees can
    inspect this class with `javap -classpath ... io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender`
    after `mise install`.
  -->
  <appender name="MDC_CONSOLE" class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender">
    <appender-ref ref="CONSOLE"/>
  </appender>

  <!--
    OTEL: opentelemetry-logback-appender-1.0's OTLP export appender. Reads
    Span.current() directly via LoggingEventMapper and emits an OTLP LogRecord
    with trace_id/span_id as record-level attributes (NOT MDC values) — that's
    why the Loki query in SC #2 (D-18) works against the OTLP attribute, not
    the formatted message text.

    OpenTelemetryAppender.install(openTelemetry) is called from a @PostConstruct
    method on OtelSdkConfiguration AFTER the SDK bean is built (D-08, D-09 —
    PITFALL #5 mitigation). Logs emitted before install() are buffered in this
    appender's replay queue (default 1000 events) and replayed on install
    (RESEARCH Finding #2).

    No <captureMdcAttributes> here — D-14 + RESEARCH Finding #1: the MDC values
    are populated by MDC_CONSOLE (the mdc.v1_0 wrapper) for terminal rendering,
    while this OTLP appender reads trace context directly from Span.current().
    Two independent paths — neither needs the other's data.
  -->
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>

  <!--
    Root captures all business logs at INFO. No per-package overrides for v1
    (CONTEXT.md <deferred> — could be a Phase 7 polish).

    Root attaches to MDC_CONSOLE (which wraps CONSOLE) for terminal rendering,
    AND to OTEL for OTLP export. NOT to CONSOLE directly — RESEARCH Finding #6:
    if root attached directly to CONSOLE, the MDC injector would never run on
    those events and `%mdc{trace_id}` would resolve to empty for in-span logs.
  -->
  <root level="INFO">
    <appender-ref ref="MDC_CONSOLE"/>
    <appender-ref ref="OTEL"/>
  </root>

</configuration>
```

After writing the file, verify producer compiles AND that any existing `application.yaml` does not redefine the console pattern (RESEARCH Risk #3). If `application.yaml` contains a `logging.pattern.console` key, the executor must REMOVE that key (Spring Boot processes it after Logback init and would silently override D-11's pattern). Currently neither service's application.yaml is expected to have any `logging.*` overrides — verify with grep.

DO NOT:
- Add a `<turboFilter>` element (Risk #2: would silently fail since the FQCN CONTEXT.md D-13 invented does not exist; CORRECTED per RESEARCH §1).
- Use bare `OpenTelemetryAppender` class names — always FQCN (`io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` and `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender`).
- Attach root directly to CONSOLE (must attach to MDC_CONSOLE per RESEARCH Finding #6).
- Include `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` (D-10: full override).
- Add per-package `<logger>` overrides (CONTEXT.md `<deferred>`).
- Add `<captureMdcAttributes>` to the OTEL appender (D-14: not needed for v1; the MDC wrapper handles MDC for console-side, OTEL handles trace context directly).
- Use `logback.xml` filename (must be `logback-spring.xml` for the `-spring` profile-aware loader — D-10).
- Tune `<numLogsCapturedBeforeOtelInstall>` (RESEARCH Risk #6: 1000 default is fine for fast Spring Boot startup).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && test -f producer-service/src/main/resources/logback-spring.xml && grep -q 'class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"' producer-service/src/main/resources/logback-spring.xml && grep -q 'class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"' producer-service/src/main/resources/logback-spring.xml && ! grep -q '<turboFilter' producer-service/src/main/resources/logback-spring.xml && ! grep -q 'spring/boot/logging/logback/defaults.xml' producer-service/src/main/resources/logback-spring.xml && grep -F '[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]' producer-service/src/main/resources/logback-spring.xml && awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="MDC_CONSOLE"' && awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="OTEL"' && (! awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="CONSOLE"') && (! grep -E '^logging\.|^\s+logging\.|^logging:' producer-service/src/main/resources/application.yaml 2>/dev/null) && mvn -B -pl producer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - File exists: `test -f producer-service/src/main/resources/logback-spring.xml`
    - Contains the OTEL appender with FQCN: `grep -q 'class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"' producer-service/src/main/resources/logback-spring.xml`
    - Contains the MDC wrapper appender with FQCN: `grep -q 'class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"' producer-service/src/main/resources/logback-spring.xml`
    - Does NOT contain TurboFilter: `! grep -q '<turboFilter' producer-service/src/main/resources/logback-spring.xml` (D-13 correction applied)
    - Does NOT contain Spring Boot logback defaults include: `! grep -q 'spring/boot/logging/logback/defaults.xml' producer-service/src/main/resources/logback-spring.xml` (D-10 full override)
    - Console pattern matches D-11 literally: `grep -F '[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]' producer-service/src/main/resources/logback-spring.xml` returns 0
    - Root attaches to MDC_CONSOLE: `awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="MDC_CONSOLE"'`
    - Root attaches to OTEL: `awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="OTEL"'`
    - Root does NOT attach directly to CONSOLE: `! awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="CONSOLE"'` (Finding #6 wiring)
    - MDC wrapper has appender-ref to CONSOLE: `awk '/MDC_CONSOLE/,/<\/appender>/' producer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref="CONSOLE"'`
    - application.yaml has no `logging.*` overrides (Risk #3): `! grep -E '^logging\.|^\s+logging\.|^logging:' producer-service/src/main/resources/application.yaml` (or file does not exist — both acceptable)
    - Producer compiles cleanly: `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile` exits 0
    - File line count is between 50 and 100 (heavy comment density per DOC-03 / D-19 carryforward; the verbatim block above is ~80 lines with comments)
  </acceptance_criteria>
  <done>
producer-service/src/main/resources/logback-spring.xml exists with the corrected wrapper-appender shape: CONSOLE (raw ConsoleAppender), MDC_CONSOLE (mdc.v1_0 wrapper of CONSOLE), OTEL (appender.v1_0 standalone). Root attaches to MDC_CONSOLE and OTEL. No <turboFilter>. No Spring Boot defaults include. Console pattern matches D-11. Producer compiles cleanly.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| application code → Logback | Application threads emit log events. Logback dispatches to appenders. The OTEL appender reads `Span.current()` and emits OTLP records — span context comes from in-process state set by Phase 2/3 instrumentation. |
| Logback → OTLP gRPC :4317 | Log records leave the JVM as OTLP-format bytes targeting `localhost:4317`. The endpoint is workshop-local; production deployments would target a remote collector. |
| Spring lifecycle → @PostConstruct | Spring invokes `installLogbackAppender()` after `@Bean OpenTelemetry` returns. The injection contract (Spring → Java method invocation) is internal trust. |

## STRIDE Threat Register (ASVS L1, security_enforcement: enabled, block-on: high)

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-02-01 | Information Disclosure | logback-spring.xml console pattern | mitigate | The pattern (`%msg`) renders application-supplied log strings. Application code (Plan 05-04, 05-05) must NOT log secrets, full request bodies, or PII. The `payload` parameter to `OrderController.create(...)` is currently a `Map<String, Object>` of order fields; for a workshop demo with synthetic data this is acceptable. Plan 05-04 task 1 will document the constraint and Plan 05-04's threat model will gate this. |
| T-05-02-02 | Information Disclosure | OTLP gRPC :4317 export | accept | Workshop endpoint is `localhost:4317` (otel-lgtm in docker-compose, loopback only). Production deployments would replace this — flagged in `OtelSdkConfiguration.java`'s existing code comments (lines 71-74 of buildTracerProvider for the trace exporter, paralleled here for logs). No new attack surface in v1. |
| T-05-02-03 | Tampering | logback-spring.xml class FQCNs | mitigate | RESEARCH Risk #2: Logback silently disables appenders with bad FQCNs. Mitigation: acceptance criteria use `grep` against the EXACT verified FQCN strings (not paraphrased). Smoke check in Plan 05-06 will grep stderr for "Failed to instantiate" / "unable to find class". |
| T-05-02-04 | Denial of Service | OTLP appender backpressure | accept | `BatchLogRecordProcessor` has SDK-default queue (2048 events) and timeout (30s). Under sustained log volume exceeding export rate, log records are dropped at the queue boundary (counter incremented). For a workshop volume (~2 orders/min) this is far below saturation. Production tuning is out of scope. |
| T-05-02-05 | Elevation of Privilege | @PostConstruct execution | accept | The @PostConstruct method runs in the Spring lifecycle as the same user as the application JVM. It calls only `OpenTelemetryAppender.install(this.openTelemetry)` (no I/O, no reflection of attacker-controlled data). Spring's lifecycle ordering is well-defined and tested. |
| T-05-02-06 | Spoofing | Logback's OpenTelemetry.noop() default | mitigate | RESEARCH Finding #2: between Logback init and `install()`, the OTLP appender is bound to `OpenTelemetry.noop()`. Logs in this window are buffered (1000-event replay queue) and replayed on install. PITFALL #5 — handled by D-08's @PostConstruct timing. |
| T-05-02-07 | Repudiation | log record trace_id binding | mitigate | The OTEL appender emits OTLP `LogRecord` with `trace_id`/`span_id` as record-level attributes (read directly from `Span.current()` — NOT from MDC, per RESEARCH Finding #6). This guarantees the trace_id stamped in Loki matches what Tempo records — no possibility of MDC-mutation-by-attacker since trace context comes from the Phase 3 propagator, not application-mutable MDC. |
</threat_model>

<verification>
- `grep -q 'private SdkLoggerProvider buildLoggerProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
- `grep -q 'BatchLogRecordProcessor' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
- `grep -q 'OtlpGrpcLogRecordExporter' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
- `grep -q '\.setLoggerProvider(loggerProvider)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
- `grep -q '@PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
- `grep -q 'OpenTelemetryAppender.install(this.openTelemetry)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0
- `grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (Risk #1: correct package imported)
- `! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (Risk #1: WRONG package NOT imported)
- `grep -q 'No Logger @Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (D-07 JavaDoc landed)
- File exists: `test -f producer-service/src/main/resources/logback-spring.xml`
- XML has both FQCNs: `grep -q 'class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"' producer-service/src/main/resources/logback-spring.xml` AND `grep -q 'class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"' producer-service/src/main/resources/logback-spring.xml`
- XML has NO TurboFilter: `! grep -q '<turboFilter' producer-service/src/main/resources/logback-spring.xml` (D-13 correction)
- XML root attaches to MDC_CONSOLE and OTEL (not CONSOLE): `awk '/<root /,/<\/root>/' producer-service/src/main/resources/logback-spring.xml` shows MDC_CONSOLE + OTEL refs only
- Comment density: `grep -E '^\s*(//|\*|/\*\*|/\*)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | wc -l` >= 80
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile` exits 0
</verification>

<success_criteria>
1. `OtelSdkConfiguration.java` has the new `buildLoggerProvider(Resource)` helper with verbatim RESEARCH §A body (3 SDK touch points: exporter, batch processor, provider).
2. The `@Bean openTelemetry()` orchestrator chains `.setLoggerProvider(loggerProvider)` between `.setMeterProvider(...)` and `.setPropagators(...)`.
3. The new `@PostConstruct installLogbackAppender()` method calls `OpenTelemetryAppender.install(this.openTelemetry)` with the FQCN from `appender.v1_0` package (NOT `mdc.v1_0`).
4. The `@Autowired private OpenTelemetry openTelemetry` field is declared (RESEARCH §C recommended shape).
5. Class JavaDoc reflects D-07 — explicit "No Logger @Bean" callout replacing the old "will add Logger @Bean" promise.
6. The new `logback-spring.xml` matches RESEARCH §D verbatim: CONSOLE + MDC_CONSOLE wrapping CONSOLE + OTEL standalone, root attaches to MDC_CONSOLE and OTEL (NOT CONSOLE directly).
7. NO `<turboFilter>` in the XML (D-13 correction).
8. NO Spring Boot logback-defaults include in the XML (D-10 full override).
9. Console pattern matches D-11 byte-for-byte: `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]`.
10. Producer compiles: `mvn -pl producer-service -am compile` exits 0.
11. Comment density on `OtelSdkConfiguration.java` ≥ 80 lines (D-19 bar).
</success_criteria>

<output>
After completion, create `.planning/phases/05-logs-correlation/05-02-SUMMARY.md` with:
- Files modified (`OtelSdkConfiguration.java` line range, `logback-spring.xml` newly created)
- Verbatim diffs of the 6 EDITs to the .java file
- The .xml file's full content as a code-block
- `mvn -pl producer-service -am compile` exit code (0)
- Comment-line count of `OtelSdkConfiguration.java` (must be ≥ 80)
- Forward-link: Plan 05-03 mirrors this plan to consumer-service; Plan 05-04 adds the producer business log lines that this pipeline now exports
- The two CRITICAL FQCN imports verified (the appender.v1_0 in .java; both .appender.v1_0 and .mdc.v1_0 in .xml)
- Note that smoke testing the @PostConstruct lifecycle (start the app, POST /orders, observe Loki receives logs) is deferred to Plan 05-06's exit gate
</output>
