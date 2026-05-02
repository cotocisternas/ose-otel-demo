# Phase 5: Logs Correlation - Pattern Map

**Mapped:** 2026-05-01
**Files analyzed:** 10 (8 modified, 2 new)
**Analogs found:** 8 / 10 (logback-spring.xml is genuinely net-new, no codebase analog)
**Note for planner:** RESEARCH §Code Excerpts §D supersedes CONTEXT.md D-13's TurboFilter. The MDC injector is an **appender wrapper**, not a TurboFilter. All references below to the new logback-spring.xml shape assume the corrected wrapper-appender structure.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `producer-service/.../config/OtelSdkConfiguration.java` (mod) | config (SDK bootstrap) | startup-wiring | self — existing `buildTracerProvider` + `buildMeterProvider` siblings | exact |
| `consumer-service/.../config/OtelSdkConfiguration.java` (mod) | config (SDK bootstrap) | startup-wiring | self — existing `buildTracerProvider` + `buildMeterProvider` siblings | exact |
| `producer-service/.../api/OrderController.java` (mod — add LOG.info) | controller | request-response | `consumer-service/.../messaging/OrderListener.java` (existing `LOG.info` call site) | role-different / pattern-exact |
| `producer-service/.../messaging/OrderPublisher.java` (mod — add LOG.info) | messaging (producer) | event-publish | `consumer-service/.../messaging/OrderListener.java` (existing `LOG.info`) | exact (same package shape, both pre-AMQP-call sites) |
| `consumer-service/.../domain/ProcessingService.java` (mod — add LOG.error) | service (domain) | event-handler-internal | NONE — Phase 5 introduces the FIRST `LOG.error` in the codebase | establishes new pattern |
| `producer-service/src/main/resources/logback-spring.xml` (NEW) | config (logging) | log-event-pipeline | NONE — no prior logback config in repo (Spring Boot defaults today) | net-new (use RESEARCH §D verbatim) |
| `consumer-service/src/main/resources/logback-spring.xml` (NEW) | config (logging) | log-event-pipeline | producer-service's new file (byte-identical copy per D-10) | exact (mirror) |
| `producer-service/pom.xml` (mod — 2 new deps) | build config | dependency-mgmt | existing `opentelemetry-api` / `opentelemetry-sdk` / `opentelemetry-exporter-otlp` blocks (lines 72-83) | exact (BOM-managed, no `<version>`) |
| `consumer-service/pom.xml` (mod — same 2 deps) | build config | dependency-mgmt | producer-service/pom.xml | exact (mirror) |
| `README.md` (mod — Step 5 section) | docs | n/a | existing "## Step 4: Metrics" section (lines 79-125) | exact |

---

## Pattern Assignments

### A. `OtelSdkConfiguration.java` — `buildLoggerProvider(Resource)` helper

**Analog:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (and the consumer mirror). The existing `buildTracerProvider(Resource)` (lines 168-225) and `buildMeterProvider(Resource)` (lines 250-293) are the EXACT siblings the new helper must mirror. Phase 5's helper lands as the third sibling.

**Existing JavaDoc foreshadows Phase 5 explicitly** (producer file lines 50-51, 130-131, 66-67):

```
* Phase 5 will reuse the same pattern when wiring the logger provider.
...
* Phase 5 will land buildLoggerProvider(resource) the same way, and the @Bean stays a 3-step recipe.
...
* Phase 5 will add a sibling buildLoggerProvider helper and a Logger @Bean.
```

NOTE: D-07 reverses the "and a Logger @Bean" promise — Phase 5 deliberately does NOT expose a `Logger` @Bean (SLF4J handles application-side logging via the appender). The planner must update the JavaDoc text.

**Existing `buildMeterProvider(Resource)` body — closest sibling** (producer lines 227-293):

```java
/**
 * Meter pipeline added in Phase 4 — sibling to {@link #buildTracerProvider(Resource)}.
 *
 * <p>Three new SDK touch points (read top-to-bottom):
 * <ol>
 *   <li>{@link OtlpGrpcMetricExporter} — same artifact and same env-var
 *       fallback as the trace exporter (D-04). The opentelemetry-exporter-otlp
 *       jar already on the classpath from Phase 2 ships span + metric + log
 *       exporters; no new pom dependency is needed.</li>
 *   ...
 * </ol>
 *
 * <p><b>Default View / Aggregation: SDK defaults.</b> No custom histogram
 * bucket tuning here (D-15). ...
 */
private SdkMeterProvider buildMeterProvider(Resource resource) {
    // ----- OTLP gRPC metric exporter: ships metrics to grafana/otel-lgtm :4317 -----
    //
    // Reuses the SAME endpoint pattern as the span exporter (D-04 / Phase 2 D-12
    // carryforward) — System.getenv with the DEFAULT_OTLP_ENDPOINT fallback. ...
    String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
        .orElse(DEFAULT_OTLP_ENDPOINT);
    OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
        .setEndpoint(endpoint)
        .build();

    // ----- PeriodicMetricReader: 10s collection interval (METRIC-01 / D-03) -----
    PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
        .setInterval(Duration.ofSeconds(10))
        .build();

    // ----- MeterProvider: assembles resource + reader -----
    return SdkMeterProvider.builder()
        .setResource(resource)
        .registerMetricReader(metricReader)
        .build();
}
```

**Shape to copy for `buildLoggerProvider`:**
- 4-tier section banner comments: `// ----- <name>: <one-line description> -----`
- Endpoint resolution **identical**: `Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse(DEFAULT_OTLP_ENDPOINT)` — three-line block (matches lines 180-181, 260-261)
- Exporter built via `.builder().setEndpoint(endpoint).build()` chain — terse single statement
- Processor built via `.builder(exporter).build()` — single line for the batch processor (per D-03 the planner accepts SDK defaults; do NOT cite "5s schedule delay" — RESEARCH Risk #4 corrects this to 1s)
- Provider build via `.builder().setResource(resource).<add-processor>(processor).build()` — terminal `return`
- Verb is `addLogRecordProcessor` (NOT `registerMetricReader`, NOT `addSpanProcessor`) — RESEARCH Finding #4 confirms

**JavaDoc shape to copy** (above the `buildMeterProvider` declaration, lines 227-249):
- Opening line names the phase + sibling: `"Logger pipeline added in Phase 5 — sibling to {@link #buildTracerProvider(Resource)} (Phase 2) and {@link #buildMeterProvider(Resource)} (Phase 4)."`
- `<ol>` listing the three SDK touch points with `{@link}` references
- `<p><b>...</b>` follow-up paragraph for the no-`Logger`-@Bean teaching point (D-07)

**Imports to add** (paste-able, paralleling existing exporter/sdk imports at lines 15-22):
```java
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
```

(The exporter package is `.logs` — sibling to existing `.metrics` import line 15 and `.trace` import line 16.)

---

### B. `OtelSdkConfiguration.java` — `@Bean openTelemetry()` orchestrator update

**Analog:** the existing orchestrator body (producer lines 97-160, consumer lines 104-167). The planner adds exactly **two lines**.

**Current orchestrator end** (producer lines 124-160):

```java
        // ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) -----
        SdkTracerProvider tracerProvider = buildTracerProvider(resource);
        SdkMeterProvider  meterProvider  = buildMeterProvider(resource);

        // ----- Propagators: composite W3C trace-context + W3C baggage -----
        ContextPropagators propagators = ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));

        // ----- The SDK itself -----
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(propagators)
            .build();
```

**Phase 5 diff (two lines):**

```java
        // ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) + logs (Phase 5 / D-01) -----
        SdkTracerProvider tracerProvider = buildTracerProvider(resource);
        SdkMeterProvider  meterProvider  = buildMeterProvider(resource);
        SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);   // NEW

        // ... propagators unchanged ...

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)   // NEW
            .setPropagators(propagators)
            .build();
```

**Shape preserved:** column-aligned local-variable block (note the existing producer lines 133-134 use whitespace-aligned `tracerProvider`/`meterProvider`); `setLoggerProvider(loggerProvider)` lands between `setMeterProvider` and `setPropagators` to read top-to-bottom as Resource → tracer → meter → logger → propagators → build.

**Comment update:** the section banner `// ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) -----` should gain `+ logs (Phase 5 / D-01)`. The `// ----- The SDK itself -----` block's commentary that says ".setMeterProvider(meterProvider) is THE single new builder line that Phase 4 contributes" should be updated or extended to mention the Phase 5 addition.

---

### C. `@PostConstruct installLogbackAppender()` — analog from Phase 4 + new field

**Analog:** Phase 4's `@PostConstruct` precedent lives in `consumer-service/.../observability/QueueDepthGauge.java` — but in a CONSTRUCTOR, not `@PostConstruct`. The closest `@PostConstruct` precedent is the JavaDoc commentary in `QueueDepthGauge` describing the D-18a/D-18b decision (lines 16-23):

```java
/**
 * <p><b>Why a separate {@link Component} (D-18b)?</b> The gauge registration
 * is one-shot side-effect boilerplate, not part of the SDK pipeline build.
 * Hosting it here keeps {@code OtelSdkConfiguration.java} symmetrical with
 * the producer's file (D-02 mirror property) — no consumer-only
 * {@code @PostConstruct} that doesn't exist on the producer side. ...
 */
```

**Pedagogical note for planner:** Phase 4 deliberately AVOIDED `@PostConstruct` in `OtelSdkConfiguration` (chose the D-18b separate-Component path) to preserve byte-identical structural parity between producer and consumer config files. **Phase 5 reverses this:** the appender install is INHERENTLY symmetric (both services must call it after their SDK builds), so `@PostConstruct` lands in `OtelSdkConfiguration` itself, identically in both files. This is consistent with D-08 and explicitly NOT a violation of D-02 (the new method is byte-identical between services).

**`@PreDestroy` shape from QueueDepthGauge** (lines 106-109) — closest mechanical analog for an annotated lifecycle method:

```java
/**
 * Closes the gauge so the SDK stops invoking the callback at shutdown.
 *
 * <p>The {@code SdkMeterProvider}'s {@code destroyMethod="close"}
 * cascade ... handles this transitively ...
 */
@PreDestroy
public void close() {
    gauge.close();
}
```

**Shape to copy for `@PostConstruct installLogbackAppender()`:**
- `jakarta.annotation.PostConstruct` import (parallel to QueueDepthGauge's `jakarta.annotation.PreDestroy` line 8)
- Heavy JavaDoc explaining ordering / cascade / pitfall (the QueueDepthGauge JavaDoc explains the cascade mechanism at lines 93-105 — Phase 5's PITFALL #5 comment block is the analogous "explain the lifecycle gotcha" prose, but ~3-5x longer per D-09 / RESEARCH §Code Excerpts §C)
- Method body is **one line**: `OpenTelemetryAppender.install(this.openTelemetry);`
- Method visibility: package-private (default) like the existing `@Bean` methods at lines 97, 168, 250 of OtelSdkConfiguration

**Field shape — `@Autowired OpenTelemetry`:**

There is NO existing `@Autowired` field on `OtelSdkConfiguration` today; the existing `Tracer` and `Meter` @Beans (producer lines 304-307, 324-327) take `OpenTelemetry` as a method parameter:

```java
@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");
}
```

**Two acceptable shapes for D-08** (planner picks one):
1. **Field-with-`@Autowired`** (RESEARCH §C, recommended by research):
   ```java
   @Autowired
   private OpenTelemetry openTelemetry;
   ```
2. **Field-set-in-factory-body**: assign `this.openTelemetry = sdk;` before `return sdk;` inside the `@Bean openTelemetry()` body. Lower coupling; field is `private` non-`@Autowired`.

The research recommends (1) because the dependency is visible in the field declaration. Either satisfies D-08.

**Imports to add for the install method:**
```java
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;  // only if shape #1
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
```

**CRITICAL:** the import is `appender.v1_0.OpenTelemetryAppender` — NOT `mdc.v1_0.OpenTelemetryAppender`. RESEARCH Risk #1 documents the naming collision: BOTH artifacts ship a class named `OpenTelemetryAppender`; only the `appender.v1_0` class has the `install()` static. The comment block in the `@PostConstruct` JavaDoc must call out the package explicitly (RESEARCH §C does this).

---

### D. SLF4J Logger field + `LOG.info(...)` (D-15) — analog from `OrderListener`

**Analog:** `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` lines 41 and 51. Phase 1 set this precedent; Phase 5 D-15/D-16 reuse the exact same field + import shape across THREE new locations.

**Existing field declaration + import** (OrderListener lines 5-6, 41):

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
...
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
```

**Existing call site** (OrderListener line 51, inside `onOrder(Map<String, Object> message)`):

```java
LOG.info("OrderCreated received: orderId={}", orderId);
```

**Shape to copy verbatim across THREE new locations:**

1. **`producer-service/.../api/OrderController.java`** — D-15 part 1
   - Add field: `private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);`
   - Add at method entry of `create(@RequestBody Map<String, Object> payload)` (line 19), inside the SERVER span (HttpServerSpanFilter wraps every non-/actuator request — Phase 2):
     ```java
     LOG.info("received POST /orders payload={}", payload);
     ```
   - **Note:** the existing method is `create`, not `handle` (CONTEXT.md §canonical_refs uses the name "handle"; the actual method name is `create`). Planner should use `create`.
   - **NOTE:** OrderController has no JavaDoc today; planner can keep it minimal but the new logger field is a structural addition that doesn't require ceremony.

2. **`producer-service/.../messaging/OrderPublisher.java`** — D-15 part 2
   - Add field: `private static final Logger LOG = LoggerFactory.getLogger(OrderPublisher.class);`
   - Add JUST BEFORE `rabbitTemplate.convertAndSend(...)` at line 42, inside the PRODUCER span (Phase 3 TracingMessagePostProcessor wraps the publish):
     ```java
     LOG.info("publishing orderId={} to exchange={}", orderId, RabbitConfig.EXCHANGE);
     ```

3. **Imports for both files:**
   ```java
   import org.slf4j.Logger;
   import org.slf4j.LoggerFactory;
   ```

**Shape conventions preserved (all three from OrderListener):**
- `private static final Logger LOG` (NOT `log` lowercase; OrderListener uses uppercase)
- `LoggerFactory.getLogger(<ThisClass>.class)` (class literal, not string)
- SLF4J `{}` placeholders, NEVER string concatenation (consistent with OrderListener line 51)
- No `@Slf4j` annotation (D-15 confirms; Phase 1 set the no-Lombok precedent)
- Field declaration is the FIRST member of the class, before other final fields (matches OrderListener line 41 — appears before `processingService` field at line 42)

---

### E. `LOG.error("...", e)` (D-16) — NEW pattern (no analog in codebase)

**Analog search result:** `grep -r "LOG.error\|log.error\|logger.error" producer-service consumer-service otel-bootstrap` → **zero matches**. Phase 5 D-16 introduces the FIRST `LOG.error` call in the codebase.

**Established pattern fields:**
- `LOG` field shape (uppercase static final, SLF4J `org.slf4j.Logger`) — identical to D-15 / OrderListener
- Call shape: `LOG.error("message: key={}", value, exception)` — SLF4J's two-arg-plus-throwable signature (last argument is treated as a throwable, NOT a placeholder substitution)

**Recommended host file:** `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` (D-16 option (a) — recommended).

**Existing catch block** (ProcessingService lines 67-75):

```java
} catch (RuntimeException e) {
    // D-03 catch shape from Phase 2 — preserved unchanged.
    // ProcessingFailedException extends RuntimeException, so it
    // is caught here (TRACE-09). The advice's catch (Throwable)
    // also records this on the CONSUMER span when the rethrow
    // bubbles up.
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
}
```

**Phase 5 addition — single `LOG.error` line just BEFORE the existing `span.recordException(e)`:**

```java
} catch (RuntimeException e) {
    // D-03 catch shape from Phase 2 — preserved unchanged.
    // Phase 5 D-16 adds the Loki-side counterpart to span.recordException —
    // both signals carry the same trace_id/span_id, so the Loki query for
    // ERROR lines on this trace_id resolves to a Tempo trace whose CONSUMER
    // span carries the matching exception event (RESEARCH Finding #7 confirms
    // Span.current() is valid inside the listener body).
    Object orderId = order.get("orderId");
    LOG.error("order processing failed: orderId={}", orderId, e);
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
}
```

**Field declaration** (add near top of class, alongside the existing `tracer` and `counter` fields at ProcessingService lines 40-46):
```java
private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);
```

**Note on the `orderId` access:** `ProcessingService.process(Map<String, Object> order)` (line 52) receives the message map; `order.get("orderId")` retrieves the field. The planner should add this local before the LOG.error call (one line) — no constructor or signature changes needed.

**Imports to add:**
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

**Pedagogical "this is the first error log" callout:** the comment block above the `LOG.error` line should explicitly note the triple-signal-on-failure pattern — this is the workshop highlight per CONTEXT.md `<specifics>` ("Triple-signal correlation on the failure path is the Phase 5 highlight").

---

### F. `pom.xml` `<dependency>` block — analog from existing OTel deps

**Analog:** `producer-service/pom.xml` lines 80-83 — the existing `opentelemetry-exporter-otlp` block (BOM-managed, no `<version>` tag):

```xml
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

**Comment context** above the OTel deps block (producer pom lines 64-71):

```xml
<!--
  OpenTelemetry SDK runtime — versions managed by opentelemetry-bom:1.61.0
  in parent pom.xml. Three artifacts cover the API surface, the SDK
  implementation, and the OTLP gRPC exporter (the single artifact that
  ships exporters for traces, metrics, and logs — Phase 2 only uses the
  trace exporter; Phase 4 + Phase 5 reuse the same artifact for the
  meter and logger pipelines).
-->
```

**No existing `instrumentation-bom-alpha` artifact in the per-service pom.xml today** — both new Phase 5 deps are the FIRST instrumentation-bom-alpha pulls into per-service pom files. The instrumentation BOM itself is imported in **parent pom.xml** (referenced at producer pom line 41, consumer mirror), but no per-service artifact has been pulled yet (Phase 1-4 used only `opentelemetry-bom`). Phase 5 establishes this precedent.

**Shape to copy verbatim** (per RESEARCH §Code Excerpts §E):

```xml
<!--
  Phase 5: OTel Logback bridges (BOM-managed by
  opentelemetry-instrumentation-bom-alpha:2.27.0-alpha imported in parent pom.xml).
  These are the ONLY two artifacts pulled from the instrumentation BOM in v1.

  Both sit in the same `instrumentation.logback` namespace but in DIFFERENT packages
  and serve DIFFERENT jobs — both are required, neither pulls the other transitively
  (verified against 2.27.0-alpha POM):
    - opentelemetry-logback-appender-1.0 → OTLP EXPORT appender (sends log records
      to the SDK's SdkLoggerProvider). FQCN: io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
    - opentelemetry-logback-mdc-1.0      → MDC INJECTOR wrapper appender (reads
      Span.current() and stamps trace_id/span_id into MDC for the console pattern).
      FQCN: io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
-->
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-logback-appender-1.0</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
</dependency>
```

**Where to land in producer-service/pom.xml:** after the existing `opentelemetry-exporter-otlp` block (line 83) and BEFORE the semconv block (line 93). This keeps "OTel SDK runtime" → "OTel logback bridges" → "semconv" reading top-to-bottom.

**Shape conventions preserved:**
- `<groupId>` line break before `<artifactId>` (matches lines 73-74, 77-78, 81-82)
- NO `<version>` tag (matches lines 72-83 — BOM-managed)
- NO `<scope>` tag (matches all OTel deps; these are runtime + compile)
- Block comment ABOVE the deps explains "why" (matches lines 64-71 / 85-91 / 99-109)
- Both files identical (D-02 / DOC-05 mirror property)

**`<dependencyConvergence>` validation:** RESEARCH Risk #5 calls out that the parent pom's `dependencyConvergence` rule on line 127 is bound to `validate`. The two new artifacts pull `opentelemetry-instrumentation-api:2.27.0` transitively. The plan should include `mvn validate` as a smoke gate before commit.

---

### G. `logback-spring.xml` — NEW file (no codebase analog)

**Analog search:** `find /home/coto/dev/demo/ose-otel-demo -name "logback*.xml" -o -name "log4j*.xml"` → **zero results**. Neither service has any logback-spring.xml today; both rely on Spring Boot's default Logback config provided by `org.springframework.boot:spring-boot:logging.LoggingApplicationListener`.

**Established conventions to honor:**
- Files live in `src/main/resources/` (existing structure has only `application.yaml` here today)
- Both services get byte-identical files (D-10 / D-02 / DOC-05 — same per-service-duplication ethos)
- Heavy comment density (DOC-03 carryforward — every appender block carries a `<!-- -->` block explaining the why)

**No existing analog — use RESEARCH §Code Excerpts §D verbatim.** The corrected wrapper-appender shape (NOT the TurboFilter shape from CONTEXT.md D-13) is the source of truth. Re-paraphrased here for the planner's convenience:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  <appender name="MDC_CONSOLE" class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender">
    <appender-ref ref="CONSOLE"/>
  </appender>
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>
  <root level="INFO">
    <appender-ref ref="MDC_CONSOLE"/>
    <appender-ref ref="OTEL"/>
  </root>
</configuration>
```

**Critical errors to avoid (per RESEARCH Findings #1, #6 + Risk #2):**
- **DO NOT** use `<turboFilter>` — that class does not exist in the artifact.
- **DO NOT** attach CONSOLE directly to root — root attaches to MDC_CONSOLE (which wraps CONSOLE). Otherwise `%mdc{trace_id}` resolves to empty for in-span events.
- **DO NOT** include `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` (D-10 — full override per "no inherited magic").

---

### H. README "Step 5" section — analog from existing "Step 4: Metrics"

**Analog:** `README.md` lines 79-125 (the existing "## Step 4: Metrics" section). Phase 5's "Step 5: Logs Correlation" section mirrors this exact shape.

**Existing structure to copy** (Step 4 anatomy):

| Section element | Line | Phase 5 mirror |
|-----------------|------|----------------|
| `## Step 4: Metrics` H2 heading | 79 | `## Step 5: Logs Correlation` |
| Opening paragraph naming the tag (`step-04-metrics`) + the diff vs prior tag + the structural change | 81-87 | name `step-05-logs`, name the third pipeline (logger), reference D-01 sibling-helper structure carryforward, link to prior tag `step-04-metrics` |
| "The three instrument shapes ... cover the OTel SDK's three primary metric kinds" framing sentence | 89-90 | "The third OTel signal — logs — closes the three-signals loop" framing |
| Bulleted list with bold instrument name + path link + explanation | 92-113 | Bulleted list of: (a) the logger pipeline + appender install, (b) the MDC injector wrapper, (c) the three new business log lines |
| Closing paragraph with concrete demo command + Mimir/Loki query + observation about resource attributes | 115-125 | Closing paragraph with `mise run demo:order` + the Loki query `{service_name="order-producer"} \|~ "<traceId>"` + observation about Loki→Tempo click-through (D-20) + PITFALL #5 callout |

**Shape conventions preserved:**
- Markdown link style: `[\`OrderController\`](./producer-service/src/main/java/com/example/producer/api/OrderController.java)` — backticks inside link text, repo-relative paths starting with `./`
- Code-fence triple-backtick blocks for queries (no language tag in existing Step 4; can add `logql` for the Loki query as a polish)
- Bold inline emphasis: `**unit is seconds (`"s"`), not milliseconds.**` — bold + inline-code combo for "trap" callouts (parallel for PITFALL #5)
- Reference links to decision IDs: `(D-05 — built once and shared between traces and metrics ...)` parenthetical at end of relevant claims
- Length: ~45 lines (Step 4 is lines 79-125 = 47 lines)

**Where to land:** AFTER the existing Step 4 block (line 125) and BEFORE the "## Reading the code" section (line 127). The "## What's NOT here yet" block at line 156 should be updated — `- No log correlation (Phase 5)` (line 161) gets removed since Phase 5 ships it.

**Workshop checkpoints table at lines 70-75:** existing line 74 already foreshadows: `- step-05-logs — (Phase 5) Logs correlation + Loki-to-Tempo click-through.` The planner can update this line to remove the parenthetical "(Phase 5)" once Phase 5 ships, mirroring how Phase 4 turned line 73 into the **Current.** marker.

---

## Shared Patterns

### S-1: SLF4J Logger field declaration (applies to D-15 × 2 files + D-16 × 1 file)

**Source:** `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` lines 5-6, 41.

**Apply to:** `OrderController.java` (producer), `OrderPublisher.java` (producer), `ProcessingService.java` (consumer).

```java
// Imports — add to all three files:
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Field — first private member of the class:
private static final Logger LOG = LoggerFactory.getLogger(<ThisClass>.class);
```

**Shape conventions:** uppercase `LOG`, `private static final`, `<Class>.class` literal, no `@Slf4j` annotation, field declared first before other instance fields.

---

### S-2: `Optional.ofNullable(System.getenv(...)).orElse(DEFAULT_OTLP_ENDPOINT)` endpoint pattern (applies to `buildLoggerProvider`)

**Source:** `producer-service/.../OtelSdkConfiguration.java` lines 180-181 (used in `buildTracerProvider`) and lines 260-261 (used in `buildMeterProvider`).

```java
String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
    .orElse(DEFAULT_OTLP_ENDPOINT);
```

**Apply to:** the new `buildLoggerProvider(Resource)` body — first three lines after the section banner comment. Same env var, same fallback constant (already declared at line 79).

---

### S-3: Per-service-duplicated symmetric files (applies to all Phase 5 file pairs)

**Source:** `OtelSdkConfiguration.java` (D-02, DOC-05) — the two files differ only in `service.name`, tracer scope name, and the producer-only `HttpServerSpanFilter` bean. README "Why is OtelSdkConfiguration.java duplicated?" (lines 138-140) makes this explicit.

**Apply to:**
- The new `buildLoggerProvider` helper — byte-identical between producer and consumer files (logger pipeline has NO service-name-driven differences; service.name comes from the shared Resource, D-05).
- The new `@PostConstruct installLogbackAppender()` method — byte-identical between files.
- The new `logback-spring.xml` — byte-identical between services (D-10 explicit).
- The two new pom.xml `<dependency>` blocks — byte-identical between services (D-02).

**Note:** D-15's NEW log lines are NOT duplicated — they live in different classes per service (OrderController is producer-only, OrderListener is consumer-only). D-16's LOG.error is consumer-only. The duplication ethos applies to SDK BOOTSTRAP and to LOGBACK CONFIG, not to per-service business code (consistent with consumer's existing Phase 2 callout at lines 60-65 "the per-service-duplication ethos applies to the SDK BOOTSTRAP; it does NOT apply to instrumentation surfaces").

---

### S-4: Heavy inline comment density (DOC-03, applies to OtelSdkConfiguration additions)

**Source:** the entire `OtelSdkConfiguration.java` is the textbook for this — ~245 lines of code carry ~150 lines of comments (≥40-comment-line bar set in Phase 2 DOC-03; Phase 4 pushed past it). The `buildMeterProvider` method (lines 227-293 = 67 lines) has ~25 comment lines including JavaDoc.

**Apply to:**
- The new `buildLoggerProvider` body must carry section-banner comments (`// ----- name -----`) for each of the three SDK touch points (exporter / processor / provider) — parallel to the existing meter helper.
- The new `@PostConstruct installLogbackAppender()` JavaDoc must explain PITFALL #5 in detail (D-09 — load-bearing). RESEARCH §C provides the full comment block; the planner should paste it verbatim.
- The class-level JavaDoc at lines 31-68 (producer) / 31-75 (consumer) must be UPDATED:
  - Line 50-51 / 51-52: change "Phase 5 will reuse the same pattern when wiring the logger provider" past tense → present (it now does).
  - Line 60-67 / 67-74: change "Phase 5 will add a sibling buildLoggerProvider helper and a Logger @Bean" — D-07 reverses the Logger-@Bean promise; the JavaDoc must reflect that "no Logger @Bean by design".

---

### S-5: `// -----` section-banner comments inside SDK helpers

**Source:** `buildMeterProvider` body uses three section banners (producer lines 251, 266, 283):

```java
// ----- OTLP gRPC metric exporter: ships metrics to grafana/otel-lgtm :4317 -----
// ----- PeriodicMetricReader: 10s collection interval (METRIC-01 / D-03) -----
// ----- MeterProvider: assembles resource + reader -----
```

**Apply to:** the new `buildLoggerProvider` — exactly three banners:
```java
// ----- OTLP gRPC log-record exporter: ships log records to grafana/otel-lgtm :4317 -----
// ----- BatchLogRecordProcessor: production-shape batching pipeline (LOG-01 / D-03) -----
// ----- LoggerProvider: assembles resource + processor -----
```

**Banner shape:** five hyphens, label, colon, one-line description, five hyphens, ALL on one comment line, blank comment line below before the multi-line explanation (matches lines 169-180, 251-260, 266-272 of the meter helper).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `producer-service/src/main/resources/logback-spring.xml` (NEW) | logging config | log-event-pipeline | No prior Logback config in the repo. Use RESEARCH §Code Excerpts §D verbatim (the corrected wrapper-appender shape). |
| `consumer-service/src/main/resources/logback-spring.xml` (NEW) | logging config | log-event-pipeline | Same — but byte-identical mirror of the producer's new file (D-10). |
| `LOG.error("...", e)` call shape (D-16) | error-logging idiom | n/a | First `LOG.error` in the codebase. Phase 5 establishes this idiom; subsequent phases inherit. The shape is just SLF4J's standard `LOG.error(String message, Object... args, Throwable t)` signature — the throwable is the LAST argument and is treated specially by SLF4J (NOT bound to a `{}` placeholder). |

---

## Field-Level Cross-References

For the planner's quick lookup — file:line markers used above:

| Symbol | File | Lines |
|--------|------|-------|
| `OtelSdkConfiguration.openTelemetry()` @Bean (producer) | `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | 97-160 |
| `OtelSdkConfiguration.buildTracerProvider` (producer) | same | 168-225 |
| `OtelSdkConfiguration.buildMeterProvider` (producer) | same | 250-293 |
| `OtelSdkConfiguration.tracer / meter` @Beans (producer) | same | 304-307, 324-327 |
| `OtelSdkConfiguration.httpServerSpanFilter` @Bean (producer-only) | same | 345-348 |
| `OtelSdkConfiguration.openTelemetry()` @Bean (consumer) | `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | 104-167 |
| `OtelSdkConfiguration.buildTracerProvider` (consumer) | same | 175-232 |
| `OtelSdkConfiguration.buildMeterProvider` (consumer) | same | 257-300 |
| `OtelSdkConfiguration.tracer / meter` @Beans (consumer) | same | 310-313, 329-332 |
| `DEFAULT_OTLP_ENDPOINT` constant (both) | both files | 79 / 86 |
| `OrderListener.LOG` field + `LOG.info` call | `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` | 41 / 51 |
| `ProcessingService` catch block (D-16 host candidate (a)) | `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` | 67-75 |
| `OrderController.create` (D-15 host #1; method name is `create`, not `handle`) | `producer-service/src/main/java/com/example/producer/api/OrderController.java` | 18-23 |
| `OrderPublisher.publish` (D-15 host #2) | `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` | 39-43 |
| `QueueDepthGauge.@PreDestroy close()` (lifecycle-method shape analog) | `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` | 106-109 |
| `producer-service/pom.xml` OTel deps (BOM-managed analog) | `producer-service/pom.xml` | 72-83 |
| `producer-service/pom.xml` semconv deps (insertion point upper boundary) | `producer-service/pom.xml` | 85-114 |
| `README.md` "Step 4: Metrics" section (analog) | `README.md` | 79-125 |
| `README.md` workshop checkpoints table | `README.md` | 70-75 |
| `README.md` "What's NOT here yet" block (line 161 needs deletion) | `README.md` | 156-163 |

---

## Metadata

**Analog search scope:** `producer-service/`, `consumer-service/`, `otel-bootstrap/`, repo root (for `logback*.xml`, `log4j*.xml`, README precedents).
**Files scanned:** 14 (10 Java sources read in full; 1 pom.xml; 1 README; 2 directory listings to confirm absence of logback config + ProcessingService location).
**Pattern extraction date:** 2026-05-01.
