---
phase: 05-logs-correlation
plan: 03
type: execute
wave: 2
depends_on:
  - 05-01
files_modified:
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - consumer-service/src/main/resources/logback-spring.xml
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
  - consumer
  - postconstruct
  - mirror
must_haves:
  truths:
    - id: LOG-01-consumer
      description: "Consumer registers SdkLoggerProvider with BatchLogRecordProcessor + OtlpGrpcLogRecordExporter to :4317"
      verify: "grep -q 'private SdkLoggerProvider buildLoggerProvider' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'BatchLogRecordProcessor' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'OtlpGrpcLogRecordExporter' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
    - id: LOG-01-consumer-wired
      description: "Consumer orchestrator chains .setLoggerProvider() onto OpenTelemetrySdk.builder()"
      verify: "grep -q '\\.setLoggerProvider(loggerProvider)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
    - id: LOG-03-consumer
      description: "Consumer @PostConstruct method calls OpenTelemetryAppender.install(this.openTelemetry)"
      verify: "grep -q '@PostConstruct' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'OpenTelemetryAppender.install(' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
    - id: D-08-consumer-fqcn
      description: "Consumer imports appender.v1_0 (not mdc.v1_0) — Risk #1 mitigated"
      verify: "grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && ! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
    - id: D-07-consumer-javadoc
      description: "Consumer class JavaDoc updated for D-07 — no Logger @Bean promise"
      verify: "grep -q 'No Logger @Bean' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && ! grep -q 'will add a sibling.*Logger @Bean' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
    - id: LOG-02-consumer
      description: "Consumer logback-spring.xml has both appender.v1_0 OTEL and mdc.v1_0 MDC_CONSOLE"
      verify: "test -f consumer-service/src/main/resources/logback-spring.xml && grep -q 'class=\"io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender\"' consumer-service/src/main/resources/logback-spring.xml && grep -q 'class=\"io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender\"' consumer-service/src/main/resources/logback-spring.xml"
    - id: D-13-consumer-correction
      description: "Consumer logback-spring.xml does NOT contain <turboFilter> (RESEARCH §1 correction)"
      verify: "! grep -q '<turboFilter' consumer-service/src/main/resources/logback-spring.xml"
    - id: D-12-consumer-root
      description: "Consumer root attaches to MDC_CONSOLE and OTEL, not bare CONSOLE"
      verify: "awk '/<root /,/<\\/root>/' consumer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref=\"MDC_CONSOLE\"' && awk '/<root /,/<\\/root>/' consumer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref=\"OTEL\"' && ! awk '/<root /,/<\\/root>/' consumer-service/src/main/resources/logback-spring.xml | grep -q 'appender-ref ref=\"CONSOLE\"'"
    - id: LOG-04-consumer
      description: "Consumer console pattern stamps trace_id and span_id"
      verify: "grep -F '[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]' consumer-service/src/main/resources/logback-spring.xml"
    - id: D-10-no-spring-include-consumer
      description: "Consumer logback-spring.xml does NOT include Spring Boot defaults"
      verify: "! grep -q 'spring/boot/logging/logback/defaults.xml' consumer-service/src/main/resources/logback-spring.xml"
    - id: D-10-byte-identical-xml
      description: "logback-spring.xml is byte-identical between producer and consumer (D-10)"
      verify: "diff producer-service/src/main/resources/logback-spring.xml consumer-service/src/main/resources/logback-spring.xml"
    - id: build-clean-consumer
      description: "Consumer compiles cleanly"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl consumer-service -am compile"
    - id: D-09-pitfall-comment-markers-consumer
      description: "Consumer's @PostConstruct PITFALL #5 comment block mirrors producer's load-bearing markers (D-09: Logback init order, noop default, install() swap, GH #10307 link, lifecycle ordering — same load-bearing comment block as producer per Plan 05-02)"
      verify: "grep -q '10307' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q -i 'order-of-operations\\|order of operations' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q -i 'noop' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q -i 'replay\\|silent' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q -i 'lifecycle' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
    - id: D-19-comment-density-consumer
      description: "Comment density on consumer's OtelSdkConfiguration.java exceeds 80 lines"
      verify: "test $(grep -E '^\\s*(//|\\*|/\\*\\*|/\\*)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java | grep -v '^\\s*\\*/' | wc -l) -ge 80"
    - id: D-02-per-service-duplication
      description: "Consumer's OtelSdkConfiguration carries the same buildLoggerProvider helper as producer's, and consumer's logback-spring.xml is byte-identical to producer's — no extraction to a shared otel-bootstrap autoconfiguration (D-02 — Phase 2 D-01 / Phase 4 D-02 carryforward)"
      verify: "grep -qE 'private\\s+SdkLoggerProvider\\s+buildLoggerProvider' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -qE 'private\\s+SdkLoggerProvider\\s+buildLoggerProvider' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && diff -q consumer-service/src/main/resources/logback-spring.xml producer-service/src/main/resources/logback-spring.xml >/dev/null"
  artifacts:
    - path: consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
      provides: "Mirror of producer's Phase 5 additions: buildLoggerProvider helper + setLoggerProvider chain + @PostConstruct installLogbackAppender + JavaDoc fix for D-07. Differences from producer: tracer scope name (com.example.consumer), service.name (order-consumer), no httpServerSpanFilter @Bean, references to consumer-side classes in JavaDoc"
      contains: "private SdkLoggerProvider buildLoggerProvider"
      contains: "@PostConstruct"
      contains: "OpenTelemetryAppender.install"
    - path: consumer-service/src/main/resources/logback-spring.xml
      provides: "Byte-identical mirror of producer's logback-spring.xml (D-10)"
      contains: "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"
      contains: "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"
  key_links:
    - from: consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
      to: consumer-service/src/main/resources/logback-spring.xml
      via: "@PostConstruct install() walks LoggerContext, finds the OTEL appender declared in XML"
      pattern: "OpenTelemetryAppender.install"
---

<objective>
Mirror the producer-side Phase 5 additions in consumer-service. Add the third sibling helper `private SdkLoggerProvider buildLoggerProvider(Resource resource)` to consumer's `OtelSdkConfiguration.java`, wire `.setLoggerProvider(...)` in the orchestrator, add `@PostConstruct installLogbackAppender()` calling `OpenTelemetryAppender.install(this.openTelemetry)`, fix the class-level JavaDoc for D-07 (no Logger @Bean), AND create `consumer-service/src/main/resources/logback-spring.xml` BYTE-IDENTICAL to the producer's file (D-10 — no service-name-driven differences in the logback file).

Purpose: D-02 mandates per-service duplication. The consumer needs its own logger pipeline because the SDK is per-service (each service has its own `OpenTelemetrySdk` @Bean), and the @PostConstruct install() is per-LoggerContext (each JVM has its own). Byte-identical logback-spring.xml proves the per-service-duplication ethos for SDK BOOTSTRAP applies symmetrically (D-02 / DOC-05).

Output: Consumer's OtelSdkConfiguration.java mirrors producer's Phase 5 additions (with consumer-specific differences: scope name `com.example.consumer`, service.name `order-consumer`, JavaDoc references to consumer classes, NO httpServerSpanFilter section). The new `consumer-service/src/main/resources/logback-spring.xml` is byte-identical to `producer-service/src/main/resources/logback-spring.xml`. Consumer compiles cleanly.
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
@.planning/phases/05-logs-correlation/05-02-SUMMARY.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
@producer-service/src/main/resources/logback-spring.xml
@consumer-service/pom.xml

<interfaces>
<!-- Same OTel API surface as Plan 05-02 (the producer plan); see that plan's <interfaces> for full details. -->
<!-- Difference: consumer uses tracer/meter scope "com.example.consumer", service.name "order-consumer". -->

Existing consumer SDK config:
- consumer/OtelSdkConfiguration.java is the mirror of producer's, with these LOCKED differences (D-02 carryforward — DO NOT erase):
  - Class JavaDoc says "consumer-service" not "producer-service"
  - service.name is "order-consumer" (line 125 of consumer file)
  - tracer scope is "com.example.consumer" (line 312)
  - meter scope is "com.example.consumer" (line 331)
  - JavaDoc paragraph about no HttpServerSpanFilter (consumer lines 60-65) — consumer-only callout, has no producer counterpart
  - @Bean Tracer references "OrderListener and ProcessingService" (line 307) instead of producer's "OrderService, OrderPublisher, and HttpServerSpanFilter"
  - @Bean Meter references "QueueDepthGauge (Plan 04-04 — orders.queue.depth.estimate ObservableGauge)" (line 320) instead of producer's "OrderService (Plan 04-02 ...) and HttpServerSpanFilter (Plan 04-03 ...)"
  - NO httpServerSpanFilter @Bean (last 5 lines of producer's file at lines 345-348 do not appear in consumer)

Logback-spring.xml: producer and consumer files are byte-identical per D-10. Build the consumer file by copying producer's file directly (after Plan 05-02 lands).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Mirror Phase 5 changes into consumer's OtelSdkConfiguration.java</name>
  <files>consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (full file, 1-333; pay attention to: imports lines 1-29, class JavaDoc lines 31-75, openTelemetry() @Bean lines 104-167, buildMeterProvider analog lines 234-300, Tracer/Meter @Beans lines 302-332)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (post-Plan-05-02 state — the canonical reference for Phase 5 additions)
    - .planning/phases/05-logs-correlation/05-02-SUMMARY.md (Plan 05-02's diff for verbatim mirroring)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §A, §B, §C — same source-of-truth as Plan 05-02)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§A, §B, §C — analogs; §S-3 byte-identical mirror property; §S-4 comment density)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-02 — per-service duplication; consumer's mirror keeps consumer-specific JavaDoc references and the no-HttpServerSpanFilter callout intact)
  </read_first>
  <action>
Apply the SAME 6 EDITs as Plan 05-02 Task 1, but to `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`. The differences from producer are:

**EDIT 1 — Update imports (currently lines 1-29).** Identical to Plan 05-02 Task 1 EDIT 1 — same 6 imports added in the same positions:
- After line 16: `import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;`
- New section between exporter and sdk imports: the comment about Risk #1 + `import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;`
- Between `io.opentelemetry.sdk.metrics.export.PeriodicMetricReader` (line 19) and `io.opentelemetry.sdk.resources.Resource` (line 20):
  - `import io.opentelemetry.sdk.logs.SdkLoggerProvider;`
  - `import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;`
  - `import io.opentelemetry.sdk.logs.export.LogRecordExporter;`
- New import group between io.opentelemetry.* and org.springframework.*: `import jakarta.annotation.PostConstruct;`
- Inside org.springframework.* group, before `org.springframework.context.annotation.Bean`: `import org.springframework.beans.factory.annotation.Autowired;`

**EDIT 2 — Update consumer class-level JavaDoc (currently lines 31-75).** Same intent as Plan 05-02 EDIT 2 but PRESERVE consumer-specific text:

Replace lines 50-51 (`Phase 5 will reuse the same pattern when wiring the logger provider.`) with:
```
 * The same env-var-with-fallback pattern is used by Phase 5's
 * {@link #buildLoggerProvider(Resource)} for the log exporter.
```

Replace the existing `<p><b>Why three @Beans (openTelemetry / tracer / meter)?</b>` paragraph (currently lines 67-74) with:
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

PRESERVE consumer-specific paragraphs untouched: the `<p><b>Why duplicated per service?</b>`, `<p><b>Why no autoconfigure?</b>`, `<p><b>Why semconv-incubating?</b>`, AND `<p><b>Why no HttpServerSpanFilter here?</b>` paragraphs (lines 38-65). These are consumer-side teaching surface and remain unchanged. Only the "Why three @Beans" paragraph at the bottom of the JavaDoc (line 67-74) is replaced.

**EDIT 3 — Update orchestrator @Bean openTelemetry() body (currently lines 104-167).** Same as Plan 05-02 EDIT 3 with line numbers shifted +7 (consumer file is 7 lines longer than producer due to the no-HttpServerSpanFilter paragraph):

- Replace line 131 (`// ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) -----`) with `// ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) + logs (Phase 5 / D-01) -----`
- Replace lines 140-141 (the SdkTracerProvider + SdkMeterProvider declarations) with three lines:
  ```java
          SdkTracerProvider tracerProvider = buildTracerProvider(resource);
          SdkMeterProvider  meterProvider  = buildMeterProvider(resource);
          SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);
  ```
- Replace the "// ----- The SDK itself -----" comment block (currently lines 155-161) with the same updated block as Plan 05-02 EDIT 3:
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
- In the `return OpenTelemetrySdk.builder()` chain (lines 162-166), insert `.setLoggerProvider(loggerProvider)` between `.setMeterProvider(meterProvider)` and `.setPropagators(propagators)`:
  ```java
          return OpenTelemetrySdk.builder()
              .setTracerProvider(tracerProvider)
              .setMeterProvider(meterProvider)
              .setLoggerProvider(loggerProvider)
              .setPropagators(propagators)
              .build();
  ```

**EDIT 4 — Add the @Autowired field (between class-open-brace and DEFAULT_OTLP_ENDPOINT).** Currently the class opens at line 77 with `public class OtelSdkConfiguration {` and DEFAULT_OTLP_ENDPOINT lives at line 86. Insert the field block between them, identical to Plan 05-02 EDIT 4 (no consumer-specific text changes — the field is shared semantics):

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

**EDIT 5 — Add the @PostConstruct installLogbackAppender() method.** Insert immediately AFTER the openTelemetry() @Bean factory close brace (currently line 167) and BEFORE the buildTracerProvider JavaDoc. Paste the SAME verbatim method as Plan 05-02 EDIT 5 (the JavaDoc is identical between services — both refer to the same SDK behavior; no consumer-specific wording).

**EDIT 6 — Add the buildLoggerProvider(Resource) helper.** Insert immediately AFTER the existing buildMeterProvider close brace (currently line 300) and BEFORE the Tracer @Bean JavaDoc. Paste the SAME verbatim helper as Plan 05-02 EDIT 6 (identical between services — Resource is shared, scope-name does not appear in this helper).

**Final post-edit comparison:** After all 6 edits, run `diff producer-service/.../OtelSdkConfiguration.java consumer-service/.../OtelSdkConfiguration.java`. The diff should show ONLY:
- The package line (`com.example.producer.config` vs `com.example.consumer.config`)
- The `service.name` value (`order-producer` vs `order-consumer`)
- The Tracer scope name (`com.example.producer` vs `com.example.consumer`)
- The Meter scope name (`com.example.producer` vs `com.example.consumer`)
- The class-level JavaDoc text (which has consumer-specific `<p><b>Why no HttpServerSpanFilter?</b>` paragraph, and consumer/producer mirror text in other paragraphs)
- The Tracer / Meter @Bean JavaDoc text (consumer-specific call-site references)
- The producer-only `httpServerSpanFilter` @Bean (last 5 lines of producer file — absent in consumer per D-07 / D-02 + consumer's no-HttpServerSpanFilter callout)
- Trivial spelling differences in pre-existing comments (`artefact` vs `artifact`, `Javadoc` vs `Javadoc`, `behaviour` vs `behavior`, `cord` vs `coord`, `cite` vs `cited`, `neutralizes` vs `neutralises`) — these are pre-existing locale variants and MUST NOT be normalized in this plan (it's not in scope).

The Phase 5 additions (buildLoggerProvider, @PostConstruct, @Autowired field, JavaDoc fix in the "Why three @Beans" paragraph) MUST be byte-identical between the two files where the consumer-specific differences listed above don't apply.

DO NOT:
- Erase any consumer-specific paragraphs (the no-HttpServerSpanFilter callout, consumer-side service.name, consumer scope names, consumer-specific Tracer/Meter JavaDoc references).
- Normalize British/American spelling in pre-existing comments (out of scope; pre-existing baseline).
- Add an `httpServerSpanFilter` @Bean to consumer (D-07 / consumer file's existing JavaDoc explicitly explains why there isn't one).
- Reorder existing @Bean methods.
- Add `import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` (Risk #1).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'private SdkLoggerProvider buildLoggerProvider' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q '\.setLoggerProvider(loggerProvider)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q '@PostConstruct' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'OpenTelemetryAppender.install(this.openTelemetry)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && ! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'No Logger @Bean' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && ! grep -q 'will add a sibling.*Logger @Bean' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'Why no HttpServerSpanFilter here?' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && mvn -B -pl consumer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - All same checks as Plan 05-02 Task 1 acceptance, applied to consumer file:
      - `grep -q 'private SdkLoggerProvider buildLoggerProvider(Resource resource)' consumer-service/.../OtelSdkConfiguration.java` returns 0
      - `grep -q '\.setLoggerProvider(loggerProvider)' consumer-service/.../OtelSdkConfiguration.java` returns 0
      - `grep -q '@PostConstruct' consumer-service/.../OtelSdkConfiguration.java` returns 0
      - `grep -q 'OpenTelemetryAppender.install(this.openTelemetry)' consumer-service/.../OtelSdkConfiguration.java` returns 0
      - `grep -q 'import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender' consumer-service/.../OtelSdkConfiguration.java` returns 0
      - `! grep -q 'import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender' consumer-service/.../OtelSdkConfiguration.java`
      - `grep -q 'import jakarta.annotation.PostConstruct' consumer-service/.../OtelSdkConfiguration.java`
      - `grep -q 'No Logger @Bean' consumer-service/.../OtelSdkConfiguration.java` (D-07 fix landed)
      - `! grep -q 'will add a sibling.*Logger @Bean' consumer-service/.../OtelSdkConfiguration.java` (old promise removed)
    - Consumer-specific preservation:
      - `grep -q 'Why no HttpServerSpanFilter here?' consumer-service/.../OtelSdkConfiguration.java` (existing consumer-specific paragraph preserved)
      - `grep -q '"order-consumer"' consumer-service/.../OtelSdkConfiguration.java` (service.name unchanged)
      - `grep -q '"com.example.consumer"' consumer-service/.../OtelSdkConfiguration.java` (tracer + meter scope unchanged)
      - `! grep -q 'httpServerSpanFilter' consumer-service/.../OtelSdkConfiguration.java` (no @Bean added — consumer remains correct per D-07/D-02)
    - Build clean: `cd $(git rev-parse --show-toplevel) && mvn -B -pl consumer-service -am compile` exits 0
    - Comment density: `grep -E '^\s*(//|\*|/\*\*|/\*)' consumer-service/.../OtelSdkConfiguration.java | wc -l` >= 80 (D-19)
    - PITFALL #5 marker — GH issue link: `grep -q '10307' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 0 (D-09 / mirrors producer's load-bearing comment)
    - PITFALL #5 marker — order-of-operations narrative: `grep -q -i 'order-of-operations\|order of operations' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 0 (D-09)
    - PITFALL #5 marker — noop default callout: `grep -q -i 'noop' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 0 (D-09)
    - PITFALL #5 marker — replay/silent buffer callout: `grep -q -i 'replay\|silent' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 0 (D-09)
    - PITFALL #5 marker — lifecycle ordering callout: `grep -q -i 'lifecycle' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 0 (D-09)
    - PITFALL #5 mirror property — every marker present in producer's file is also present in consumer's file (catches silent drift between the two services' load-bearing comment blocks): `for marker in 10307 'order-of-operations\|order of operations' noop 'replay\|silent' lifecycle; do prod=$(grep -c -i -E "$marker" producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java); cons=$(grep -c -i -E "$marker" consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java); test "$prod" -gt 0 -a "$cons" -gt 0 || { echo "MISSING marker: $marker (producer=$prod consumer=$cons)"; exit 1; }; done`
  </acceptance_criteria>
  <done>
consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java has all 6 EDITs applied (mirror of producer's Plan 05-02 changes), with consumer-specific text preserved (service.name, scope names, no-HttpServerSpanFilter paragraph). mvn -pl consumer-service -am compile exits 0. Comment density >= 80.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Create consumer-service/src/main/resources/logback-spring.xml byte-identical to producer's</name>
  <files>consumer-service/src/main/resources/logback-spring.xml</files>
  <read_first>
    - producer-service/src/main/resources/logback-spring.xml (post-Plan-05-02 state — the source-of-truth file to mirror)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-10 — files are byte-identical between services; the logback file has no service-name-driven differences because service.name comes from the SDK Resource, not from Logback)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§S-3 — same per-service-duplication ethos applies to logback config; §G — no analog, mirror producer's file)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §D — verbatim block; §Risk #3 — verify consumer's application.yaml has no logging.* overrides)
    - consumer-service/src/main/resources/application.yaml (verify no logging.* properties exist)
  </read_first>
  <action>
Create `consumer-service/src/main/resources/logback-spring.xml` BYTE-IDENTICAL to `producer-service/src/main/resources/logback-spring.xml`.

The simplest correct implementation: use the Read tool to read producer's file, then use the Write tool to create consumer's file with the EXACT same content. The file MUST diff cleanly against producer's:

```bash
diff producer-service/src/main/resources/logback-spring.xml consumer-service/src/main/resources/logback-spring.xml
# Expected: NO output (files identical)
```

The expected file content is the same as Plan 05-02 Task 2 (RESEARCH §Code Excerpts §D verbatim). If you produce the file from memory rather than from producer's file, ensure:

- All FQCNs are the verified ones (Risk #1):
  - `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` (OTEL appender — has install())
  - `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` (MDC_CONSOLE wrapper)
- NO `<turboFilter>` element (D-13 corrected per RESEARCH §1)
- NO `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` (D-10 full override)
- Console pattern matches D-11 byte-for-byte
- Root attaches to MDC_CONSOLE and OTEL (NOT bare CONSOLE — Finding #6)
- Comment text mirrors producer's (the comments are not service-specific — they describe library behavior)

Verify the byte-identical property AFTER writing:

```bash
diff producer-service/src/main/resources/logback-spring.xml consumer-service/src/main/resources/logback-spring.xml
# Must output nothing (exit 0)
```

DO NOT:
- Make ANY content changes between producer's file and consumer's file. Service-name, scope-name, and class-name differences live in the .java code; the logback file is generic per D-10.
- Use a hard link or symlink (Maven would handle the build either way, but workshop attendees inspecting the source tree expect two separate files for the per-service-duplication lesson — D-02 / DOC-05 carryforward).
- Reference the consumer's classes anywhere in the file.
- Add a per-package `<logger>` for `com.example.consumer` (CONTEXT.md `<deferred>` — no per-package overrides for v1).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && test -f consumer-service/src/main/resources/logback-spring.xml && diff producer-service/src/main/resources/logback-spring.xml consumer-service/src/main/resources/logback-spring.xml && grep -q 'class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"' consumer-service/src/main/resources/logback-spring.xml && grep -q 'class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"' consumer-service/src/main/resources/logback-spring.xml && ! grep -q '<turboFilter' consumer-service/src/main/resources/logback-spring.xml && (! grep -E '^logging\.|^\s+logging\.|^logging:' consumer-service/src/main/resources/application.yaml 2>/dev/null) && mvn -B -pl consumer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - File exists: `test -f consumer-service/src/main/resources/logback-spring.xml`
    - Files diff cleanly: `diff producer-service/src/main/resources/logback-spring.xml consumer-service/src/main/resources/logback-spring.xml` exits 0 with no output (byte-identical per D-10)
    - Consumer XML has both FQCNs: `grep -q 'class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"' consumer-service/src/main/resources/logback-spring.xml` AND `grep -q 'class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"' consumer-service/src/main/resources/logback-spring.xml`
    - NO TurboFilter: `! grep -q '<turboFilter' consumer-service/src/main/resources/logback-spring.xml` (D-13 correction applied)
    - NO Spring Boot logback-defaults include: `! grep -q 'spring/boot/logging/logback/defaults.xml' consumer-service/src/main/resources/logback-spring.xml`
    - Console pattern matches D-11: `grep -F '[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]' consumer-service/src/main/resources/logback-spring.xml`
    - Root attaches to MDC_CONSOLE and OTEL (not bare CONSOLE): `awk '/<root /,/<\/root>/' consumer-service/src/main/resources/logback-spring.xml` matches MDC_CONSOLE + OTEL but not bare CONSOLE
    - Consumer's application.yaml has no `logging.*` overrides (Risk #3): `! grep -E '^logging\.|^\s+logging\.|^logging:' consumer-service/src/main/resources/application.yaml` (or file does not exist)
    - Consumer compiles: `cd $(git rev-parse --show-toplevel) && mvn -B -pl consumer-service -am compile` exits 0
  </acceptance_criteria>
  <done>
consumer-service/src/main/resources/logback-spring.xml exists, byte-identical to producer-service/src/main/resources/logback-spring.xml. Consumer compiles cleanly. D-10 mirror property satisfied.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| consumer application code → Logback | Consumer's @RabbitListener handlers emit log events. Logback dispatches to MDC_CONSOLE + OTEL appenders. Span context comes from Phase 3's TracingMessageListenerAdvice (extracted from AMQP headers). |
| Logback → OTLP gRPC :4317 | Same as producer plan. Workshop-local; production deploy needs replacement. |

## STRIDE Threat Register (ASVS L1, security_enforcement: enabled, block-on: high)

Phase 5's consumer plan inherits the same threat surface as Plan 05-02 (producer):

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-03-01 | Information Disclosure | logback-spring.xml console pattern | mitigate | Same as T-05-02-01 — consumer business code (Plan 05-05) must NOT log secrets. Consumer's existing OrderListener.LOG.info logs only `orderId` (a UUID — not sensitive). Plan 05-05 will add a LOG.error with `orderId` only — verified non-sensitive. |
| T-05-03-02 | Information Disclosure | OTLP gRPC :4317 export | accept | Same as T-05-02-02. |
| T-05-03-03 | Tampering | logback-spring.xml class FQCNs | mitigate | Same as T-05-02-03. Acceptance criteria use exact verified FQCN strings; smoke check in Plan 05-06 will detect failed-instantiate warnings. |
| T-05-03-04 | Denial of Service | OTLP appender backpressure | accept | Same as T-05-02-04. Consumer log volume is even lower than producer (one log/error per message vs producer's 2/order). |
| T-05-03-05 | Elevation of Privilege | @PostConstruct execution | accept | Same as T-05-02-05. |
| T-05-03-06 | Spoofing | Logback's OpenTelemetry.noop() default | mitigate | Same as T-05-02-06. The consumer-side install() runs in the consumer JVM's @PostConstruct cycle, independent of producer. |
| T-05-03-07 | Repudiation | log record trace_id binding | mitigate | Same as T-05-02-07. The consumer's appended trace_id matches what the listener-side advice extracted from AMQP headers — verified by the producer/consumer parentSpanId equality from Phase 3. |
| T-05-03-08 | Tampering | byte-identical file mirror property | mitigate | The `diff` check in Task 2 acceptance criteria locks in D-10 — any deviation between producer and consumer logback files is a defect (NOT an intentional difference). This catches accidental edits. |
</threat_model>

<verification>
Same checks as Plan 05-02 verification, applied to consumer files:
- consumer's OtelSdkConfiguration.java has the buildLoggerProvider helper, setLoggerProvider chain, @PostConstruct + install method, JavaDoc fix
- consumer's OtelSdkConfiguration.java imports `appender.v1_0.OpenTelemetryAppender` (NOT `mdc.v1_0`)
- consumer's logback-spring.xml exists with the corrected wrapper-appender shape
- consumer's logback-spring.xml is byte-identical to producer's (D-10)
- consumer-specific surfaces preserved: service.name "order-consumer", scope "com.example.consumer", no-HttpServerSpanFilter callout, no httpServerSpanFilter @Bean
- `mvn -pl consumer-service -am compile` exits 0
- comment density on consumer's OtelSdkConfiguration.java >= 80 lines

Cross-plan verification:
- `diff producer-service/.../logback-spring.xml consumer-service/.../logback-spring.xml` exits 0 with no output (D-10 byte-identical)
- Both Plan 05-02 and Plan 05-03 logback-spring.xml files are valid Logback config (no parse errors at startup — verified by mvn compile + Plan 05-06 smoke)
</verification>

<success_criteria>
1. consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java has the SAME 6 Phase 5 EDITs as producer's file (with consumer-specific text preserved).
2. consumer-service/src/main/resources/logback-spring.xml is byte-identical to producer's logback-spring.xml.
3. Consumer compiles cleanly: `mvn -pl consumer-service -am compile` exits 0.
4. Comment density on consumer's OtelSdkConfiguration.java ≥ 80 (D-19).
5. D-02 mirror property is satisfied: producer/consumer Phase 5 additions diff only on the canonical D-02 differences (service.name, scope name, JavaDoc references, no-HttpServerSpanFilter).
6. NO `<turboFilter>` in consumer's logback file (D-13 correction).
7. NO `<include>` of Spring Boot logback defaults (D-10 full override).
8. Consumer imports the appender.v1_0 OpenTelemetryAppender (NOT mdc.v1_0) — Risk #1 mitigated.
</success_criteria>

<output>
After completion, create `.planning/phases/05-logs-correlation/05-03-SUMMARY.md` with:
- Files modified (consumer's OtelSdkConfiguration.java; new logback-spring.xml)
- The line-count diff against producer's OtelSdkConfiguration.java (proves D-02 mirror)
- The byte-identical property of the two logback-spring.xml files (`diff` empty)
- `mvn -pl consumer-service -am compile` exit code (0)
- Comment-line count of consumer's OtelSdkConfiguration.java (≥ 80)
- Forward-link: Plan 05-04 adds producer business log lines; Plan 05-05 adds consumer error log line; Plan 05-06 smoke-tests the entire pipeline
</output>
