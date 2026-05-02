# Phase 5: Logs Correlation — Research

**Researched:** 2026-05-01
**Domain:** OpenTelemetry Logback bridge — `SdkLoggerProvider` + `OpenTelemetryAppender` + MDC injection (Spring Boot 3.4.13 / Java 17 / OTel SDK 1.61.0 / OTel Instrumentation 2.27.0-alpha)
**Confidence:** HIGH (every artifact coordinate, class FQCN, and API signature verified against Maven Central POMs and the GitHub `main` branch source within the last 30 minutes)

---

## Research Question

**How do I plan Phase 5: Logs Correlation well, given CONTEXT.md is already locked on 21 decisions?**

CONTEXT.md is well-scoped — locked decisions D-01..D-21 cover the architectural shape (sibling-helper structure, per-service duplication, `BatchLogRecordProcessor`, `@PostConstruct` install, full Spring Boot defaults override in `logback-spring.xml`, three new business log lines, two new BOM-managed deps, README scope, tag name). This research deliberately does **not** redesign any of those decisions. It fills the gaps that could break execution if guessed:

- The exact Maven coordinates and class FQCNs for the **two** new instrumentation-bom-alpha artifacts.
- The exact API surface for `OpenTelemetryAppender.install(...)` — signature, behavior contract, idempotency.
- Whether the Logback config shape locked in D-13/D-14 (TurboFilter + standalone OTEL appender) is **technically correct** for the current 2.27.0-alpha library — or whether CONTEXT.md has the wrong mental model.

**Headline finding:** CONTEXT.md D-13 has the **wrong shape** for the MDC injector. The `opentelemetry-logback-mdc-1.0` library is **NOT** a TurboFilter — it is an **Appender wrapper** that decorates another appender (the CONSOLE appender) and injects trace_id/span_id/trace_flags into MDC just-in-time before forwarding to its wrapped child. This changes the `logback-spring.xml` shape that the planner produces. Details in §High-Priority Findings #1.

---

## High-Priority Findings

### Finding #1 — `opentelemetry-logback-mdc-1.0` is an Appender wrapper, NOT a TurboFilter (CRITICAL CORRECTION TO CONTEXT.md D-13)

**Status:** [VERIFIED via raw GitHub source + official README on `main` branch, 2026-05-01]

**Maven coordinate (BOM-managed at 2.27.0-alpha):**
```xml
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
  <!-- no <version> — managed by opentelemetry-instrumentation-bom-alpha:2.27.0-alpha -->
</dependency>
```

**Exact class FQCN:**
```
io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
```

**Class declaration (verified from source):**
```java
public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
    implements AppenderAttachable<ILoggingEvent>
```

It is NOT a `ch.qos.logback.classic.turbo.TurboFilter`. It is an Appender that:
1. Receives the `ILoggingEvent`,
2. Reads `Span.fromContext(Context.current())` and reflectively writes `trace_id`, `span_id`, `trace_flags` into the event's `mdcPropertyMap` field,
3. Then forwards the event to ALL its attached child appenders (`appender-ref` children).

**Implications for CONTEXT.md decisions:**

| CONTEXT.md decision | Status after this finding |
|---|---|
| **D-13 — declare `<turboFilter class="...mdc.v1_0.OpenTelemetryAppenderMdcInjector"/>`** | INCORRECT — that class does not exist. Replace with the wrapper-appender shape below. |
| **D-12 — both CONSOLE and OTEL appenders attached to root** | Needs adjustment — see §Code Excerpts for the corrected shape. The CONSOLE appender is no longer attached directly to root; it is wrapped inside the MDC appender, which sits beside the OTLP appender on root. |
| **D-14 — `<appender name="OTEL" class="...appender.v1_0.OpenTelemetryAppender"/>`** | Still correct as written — the `appender.v1_0` package is the OTLP exporter (a separate artifact). The two artifacts both ship a class named `OpenTelemetryAppender` but in **different packages** (`...appender.v1_0` vs `...mdc.v1_0`) — they do different jobs and both are needed. |

**Two artifacts, both needed, both BOM-managed:**

| Artifact | Class FQCN | Purpose |
|---|---|---|
| `opentelemetry-logback-appender-1.0` | `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` | OTLP **export** appender. Receives every log event, builds an OTLP `LogRecord`, hands it to the `SdkLoggerProvider` set via `install(...)`. Emits to backend (Loki). |
| `opentelemetry-logback-mdc-1.0` | `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` | **MDC injector** wrapper. Reads active `Span.current()`, writes `trace_id`/`span_id`/`trace_flags` into MDC, then forwards the event to its wrapped child appender (CONSOLE). |

**Transitive relationship:** Verified from the `2.27.0-alpha` POM — `opentelemetry-logback-appender-1.0` does **NOT** pull `opentelemetry-logback-mdc-1.0` transitively. Both must be declared explicitly.

**Configuration knobs (per source review of `mdc.v1_0.OpenTelemetryAppender`):**
- `<traceIdKey>` — default `trace_id` (matches CONTEXT.md D-11 console pattern; no change needed)
- `<spanIdKey>` — default `span_id` (matches D-11; no change needed)
- `<traceFlagsKey>` — default `trace_flags` (D-11 does not surface this; safe to leave at default)
- `<addBaggage>` — default `false` (correct for v1; baggage MDC injection is explicitly deferred per CONTEXT.md `<deferred>`)

**Sources:**
- [Raw source: `OpenTelemetryAppender.java` (mdc/v1_0 package, `main` branch)](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java-instrumentation/main/instrumentation/logback/logback-mdc-1.0/library/src/main/java/io/opentelemetry/instrumentation/logback/mdc/v1_0/OpenTelemetryAppender.java)
- [Library README — logback-mdc-1.0](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-mdc-1.0/library/README.md) — exact `<appender>` XML shape with `<appender-ref>` child
- [Maven Central POM `opentelemetry-logback-mdc-1.0:2.27.0-alpha`](https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-logback-mdc-1.0/2.27.0-alpha/opentelemetry-logback-mdc-1.0-2.27.0-alpha.pom) — confirms version available
- [BOM POM `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`](https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-instrumentation-bom-alpha/2.27.0-alpha/opentelemetry-instrumentation-bom-alpha-2.27.0-alpha.pom) — confirms both `opentelemetry-logback-mdc-1.0` and `opentelemetry-logback-appender-1.0` are listed

---

### Finding #2 — `OpenTelemetryAppender.install(OpenTelemetry)` API and behavior contract

**Status:** [VERIFIED via raw GitHub source on `main` branch, 2026-05-01]

**Exact static method signature (from `appender.v1_0.OpenTelemetryAppender.java`):**
```java
public static void install(OpenTelemetry openTelemetry)
```

- Parameter type: `io.opentelemetry.api.OpenTelemetry` (the API interface, NOT `OpenTelemetrySdk`).
- The README example shows passing `OpenTelemetrySdk` — that works because `OpenTelemetrySdk implements OpenTelemetry`. For the demo, passing the `OpenTelemetry` @Bean reference is correct and idiomatic.
- **No overloads.** One signature, one parameter, void return.

**What it does internally (verified from source):**
1. Walks the global `LoggerContext.getLoggerList()`, iterates appenders attached to each logger.
2. For each appender that is an instance of `OpenTelemetryAppender` (the OTLP export one in `appender.v1_0` package), or any `AppenderAttachable` containing one (e.g., a wrapper appender), calls `setOpenTelemetry(openTelemetry)` on it.
3. Once installed, the appender drains its `eventsToReplay` queue (size 1000 by default — knob: `<numLogsCapturedBeforeOtelInstall>`) so log records emitted between Logback init and `install()` are forwarded to the OTLP exporter.

**Behavior contract:**

| Question | Answer | Source |
|---|---|---|
| What happens if `install()` is called twice? | Idempotent — replaces the previous `OpenTelemetry` reference; no exception. The `volatile OpenTelemetry openTelemetry` field is simply reassigned. | Source line 47 + `setOpenTelemetry` |
| What happens if called with `OpenTelemetry.noop()`? | Appender accepts it; subsequent log events go to the noop pipeline (no-op). Effectively "uninstalls" exporting without throwing. Useful only for tests. | Source line 47 |
| What happens to logs emitted BEFORE `install()`? | Buffered up to 1000 events (default) in `eventsToReplay` queue; replayed when `install()` runs. Logs beyond 1000 are silently dropped (`replayLimitWarningLogged` flag fires once per JVM). | Source lines 49-52, 55 |
| Is there a "wrong order" failure mode that PITFALL #5 documents? | YES — but only for logs emitted before `install()` AND beyond the 1000-event replay buffer. For Spring Boot startup logs the buffer is plenty; the workshop's lesson is "the buffer exists, but the install MUST run." | GH issue [open-telemetry/opentelemetry-java-instrumentation#10307](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307) |
| Did 2.27.0-alpha change the install API vs older versions? | NO — `install(OpenTelemetry)` signature has been stable since the early 1.x line. The `numLogsCapturedBeforeOtelInstall` replay buffer was added in earlier 1.30.x — fully present in 2.27.0-alpha. | Maven Central version history shows no breaking changes; source has stable Javadoc. |

**Note on PITFALL #5 currency:** The 1000-event replay buffer SOFTENS the silent-no-op pitfall (logs are NOT permanently lost — they're buffered) but does NOT eliminate it. If `install()` is never called, all log events are dropped after the 1000-event buffer fills. The PITFALL #5 lesson holds: `@PostConstruct` install on `OtelSdkConfiguration` is the correct hook. CONTEXT.md D-08 / D-09 are correct as written.

**Optional teaching enhancement (NOT required by CONTEXT.md):** the planner MAY add a one-line callout in the PITFALL #5 comment block mentioning the 1000-event replay buffer — it's a nice nuance ("logs aren't permanently lost; they're queued and replayed on install"). This is at the planner's discretion; the install order is still load-bearing.

**Sources:**
- [Raw source: `OpenTelemetryAppender.java` (appender/v1_0 package, `main` branch)](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java-instrumentation/main/instrumentation/logback/logback-appender-1.0/library/src/main/java/io/opentelemetry/instrumentation/logback/appender/v1_0/OpenTelemetryAppender.java) — line 60: `public static void install(OpenTelemetry openTelemetry)`
- [Library README — logback-appender-1.0 install example](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-appender-1.0/library/README.md)
- [GH issue #10307 — Logback appender needs to be re-initialized after spring application starts](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307)

---

### Finding #3 — `OtlpGrpcLogRecordExporter` API matches the Phase 2 trace exporter shape exactly

**Status:** [VERIFIED via Context7 + Maven Central — `opentelemetry-exporter-otlp:1.61.0` ships all three signal exporters from one artifact]

**Exact builder API (parallel to Phase 2's `OtlpGrpcSpanExporter` + Phase 4's `OtlpGrpcMetricExporter`):**
```java
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;

OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
    .setEndpoint(endpoint)   // String, http://host:port form
    .build();
```

**Package:** `io.opentelemetry.exporter.otlp.logs` (parallel to `.trace` for spans and `.metrics` for metrics — same artifact, three sub-packages).

**Classpath status (verified via Phase 2 + Phase 4 dependency graph):** The `opentelemetry-exporter-otlp` artifact is already on classpath in both services (declared in both `pom.xml`s since Phase 2 — see `producer-service/pom.xml` lines 80-83, `consumer-service/pom.xml` lines 80-83). Same artifact ships `OtlpGrpcSpanExporter`, `OtlpGrpcMetricExporter`, AND `OtlpGrpcLogRecordExporter`. **Phase 5 adds zero new SDK-side deps** — CONTEXT.md D-04 is verified correct.

**Verification command:** `mvn dependency:tree -pl producer-service -Dincludes=io.opentelemetry:opentelemetry-exporter-otlp` — should resolve to `1.61.0` from `opentelemetry-bom`.

**Sources:**
- [Context7: opentelemetry-java SDK exporter docs](https://context7.com/open-telemetry/opentelemetry-java) — confirms `OtlpGrpcLogRecordExporter.builder().setEndpoint(...).build()` API shape
- Direct read: `producer-service/pom.xml` lines 80-83 + `consumer-service/pom.xml` lines 80-83 — `opentelemetry-exporter-otlp` already declared

---

### Finding #4 — `SdkLoggerProvider` builder API + lifecycle cascade behavior

**Status:** [VERIFIED via Context7 + Phase 2 lifecycle pattern]

**Exact builder shape (parallel to Phase 2's `SdkTracerProvider` + Phase 4's `SdkMeterProvider`):**
```java
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;

SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
    .setResource(resource)
    .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
    .build();
```

- `setResource(Resource)` — single method, parallel to `SdkTracerProvider.setResource` and `SdkMeterProvider.setResource`. Resource is shared across all three providers per CONTEXT.md D-05.
- `addLogRecordProcessor(LogRecordProcessor)` — note the verb `add` (not `register` like the metric reader). Multiple processors can be added; for v1 a single `BatchLogRecordProcessor` is sufficient.
- Package: `io.opentelemetry.sdk.logs` (transitively pulled by `opentelemetry-sdk` already on classpath since Phase 2 — verified by reading the existing pom.xml).

**`BatchLogRecordProcessor` defaults (verified via Context7):**
- Schedule delay: 1000 ms (NOTE — different from `BatchSpanProcessor`'s 5000 ms default)
- Max queue size: 2048
- Max export batch size: 512
- Exporter timeout: 30000 ms

The 1000ms vs 5000ms difference is a real OTel-SDK quirk, not a typo. The planner should NOT cite "5s schedule delay" in the PITFALL #5 comment block (CONTEXT.md D-03 mentions "5s schedule delay" in passing — it conflates the span vs log defaults). Recommendation: either omit the specific number from the comment (just say "SDK defaults") or correctly cite "1s schedule delay" for logs vs "5s for spans".

**Lifecycle cascade (CONTEXT.md D-06 verification):**

`OpenTelemetrySdk.close()` cascades to `SdkLoggerProvider.close()` → `BatchLogRecordProcessor.close()` → forces a final flush of the export queue. This is the **same** mechanism Phase 2 D-15 + Phase 4 D-07 already exercise for `SdkTracerProvider.close()` and `SdkMeterProvider.close()`. **No new `@Bean(destroyMethod=...)` is required.** D-06 is verified correct.

**Sources:**
- [Context7: opentelemetry-java SDK logs docs](https://context7.com/open-telemetry/opentelemetry-java) — `SdkLoggerProvider.builder()` + `addLogRecordProcessor` + `BatchLogRecordProcessor` defaults
- [OTel Java SDK 1.61.0 source — `BatchLogRecordProcessorBuilder`](https://github.com/open-telemetry/opentelemetry-java/blob/v1.61.0/sdk/logs/src/main/java/io/opentelemetry/sdk/logs/export/BatchLogRecordProcessorBuilder.java) — schedule delay default verified

---

### Finding #5 — Logback `logback-spring.xml` schema for full Spring Boot defaults override

**Status:** [VERIFIED via Logback 1.5.x official docs + Spring Boot 3.4 reference]

**Element ordering inside `<configuration>`:** Logback's parser is largely order-insensitive within `<configuration>`. Conventional order (most readable, used in OTel official examples):
1. `<conversionRule>` (if any custom conversion words — none in this demo)
2. `<turboFilter>` (if any — Phase 5 has none; CONTEXT.md D-13's TurboFilter mental model is incorrect per Finding #1)
3. `<appender>` declarations (CONSOLE, then the wrapped MDC appender, then OTEL — order matters only for readability)
4. `<logger>` overrides (none in v1 — CONTEXT.md `<deferred>` "no per-package overrides")
5. `<root level="INFO">` with `<appender-ref>` children — MUST be last (it references appenders defined above by name)

**Spring Boot 3.4 + `logback-spring.xml`:**
- Spring Boot 3.4.13's `LoggingApplicationListener` registers an early Logback bootstrap that loads `logback-spring.xml` from classpath BEFORE the Spring `ApplicationContext` is built. `<springProfile>` and `<springProperty>` elements are activated by the Spring-aware loader (the reason for the `-spring` suffix).
- **No breaking changes vs Spring Boot 3.2/3.3** in `logback-spring.xml` resolution. The 3.4 line preserved 3.2's behavior.
- If you do NOT include `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>`, you lose Spring Boot's default `CONSOLE_LOG_PATTERN` etc. — exactly what CONTEXT.md D-10 wants ("file is source of truth, no inherited magic").

**Verifying the absence of unwanted defaults:** Spring Boot 3.4's default Logback config (`spring-boot-3.4.13.jar!/org/springframework/boot/logging/logback/defaults.xml`) defines color converters and the default console pattern. By omitting the `<include>` element, this XML never gets parsed. Confirmed correct per CONTEXT.md D-10.

**Logback `:-` empty-default syntax (CONTEXT.md D-11 verification):**

[VERIFIED via Logback 1.5.x manual] `%mdc{key:-}` (with nothing after `:-`) renders an **empty string** when the MDC key is absent — it does NOT render the literal `:-`. Quote from the official Logback layouts manual: *"If the value is null, then the default value specified after the `:-` operator is output. If no default value is specified than the empty string is output."*

So CONTEXT.md D-11's locked pattern:
```
%d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n
```
…will render correctly:
- In-span log: `12:34:56.789 [http-nio-8080-exec-1] INFO  [trace_id=4b2e... span_id=ad12...] c.e.p.api.OrderController - received POST /orders`
- Startup log: `12:34:55.123 [main] INFO  [trace_id= span_id=] org.springframework... - Started`

The `%X{...}` form is an alias for `%mdc{...}` — interchangeable, identical syntax. CONTEXT.md uses `%mdc{...}` which is the more explicit form (preferred for the workshop teaching surface).

**Sources:**
- [Logback Layouts manual — MDC conversion](https://logback.qos.ch/manual/layouts.html) — `%mdc{key:-defaultVal}` empty-default behavior
- [Spring Boot 3.4 Reference — Logging](https://docs.spring.io/spring-boot/docs/3.4.13/reference/html/features.html#features.logging) — `logback-spring.xml` profile-aware loader behavior
- [Spring Boot 3.4.13 source — `LoggingApplicationListener`](https://github.com/spring-projects/spring-boot/blob/v3.4.13/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/context/logging/LoggingApplicationListener.java)

---

### Finding #6 — Putting Findings #1–5 together: the corrected `logback-spring.xml` shape

**Status:** [VERIFIED — synthesis of Findings #1, #5; cross-checked against the official mdc-1.0 README example]

CONTEXT.md D-12 says "both CONSOLE and OTEL appenders attached to root" with a TurboFilter for MDC injection. Per Finding #1 the TurboFilter doesn't exist; the MDC appender is a **wrapper**. The corrected shape for Phase 5's `logback-spring.xml` is:

```
ROOT logger
 ├── appender-ref MDC_CONSOLE  → mdc.OpenTelemetryAppender (injects trace_id/span_id into MDC)
 │                                └── appender-ref CONSOLE → ch.qos.logback.core.ConsoleAppender (renders pattern)
 └── appender-ref OTEL          → appender.OpenTelemetryAppender (exports to OTLP)
```

The MDC appender wraps CONSOLE. The OTLP appender stands alone. Root attaches to both wrappers.

**Why root attaches to MDC_CONSOLE not CONSOLE directly:** the MDC appender must run BEFORE CONSOLE so that when CONSOLE's pattern encoder reads `%mdc{trace_id}`, the value is already populated. Logback resolves this by appender chaining: events flow MDC → CONSOLE.

**Why OTEL is independent:** the OTLP appender (`appender.v1_0.OpenTelemetryAppender`) reads the active `Span.current()` directly via `LoggingEventMapper` (not via MDC) and emits an OTLP `LogRecord` with `trace_id`/`span_id` as **OTLP attributes** (NOT MDC values). This is why the README example wires it as a sibling of CONSOLE, not wrapped behind MDC. CONTEXT.md D-18's claim that "the query works on the OTLP attribute, not the formatted message" is verified by source — it's the OTLP appender's `LoggingEventMapper` that populates these.

See §Code Excerpts below for the exact paste-able XML.

---

### Finding #7 — Spring Boot 3.4 + Logback 1.5.22 — no breaking changes affecting Phase 5

**Status:** [VERIFIED via Spring Boot 3.4.13 BOM read]

The Spring Boot 3.4.13 BOM pins `logback-classic:1.5.22` and `logback-core:1.5.22`. Both are compatible with `opentelemetry-logback-appender-1.0:2.27.0-alpha` and `opentelemetry-logback-mdc-1.0:2.27.0-alpha` — the `-1.0` suffix in the artifact name refers to **Logback API version 1.0+ compatibility** (the Logback 1.x line through 1.5.x), NOT to OTel version 1.0. Verified by reading `OpenTelemetryAppender.java` source — uses Logback 1.x API surface (`UnsynchronizedAppenderBase`, `ILoggingEvent`, `AppenderAttachableImpl`) which is stable across 1.0–1.5.x.

**Spring Boot 3.4 specific things to know:**
1. `logback-spring.xml` is loaded BEFORE the Spring `ApplicationContext` is built — that's WHY `OpenTelemetryAppender` starts up with `OpenTelemetry.noop()` (PITFALL #5 mechanism). The 1000-event replay queue (Finding #2) bridges this.
2. Spring Boot 3.4's default `LoggingApplicationListener` will warn about appender errors only at WARN level — silent appender startup failures (e.g., misspelled class FQCN) appear as one warning line in stderr, easy to miss. **Smoke-test recommendation:** after starting each service, grep stderr for `Failed to instantiate` or `unable to find class`.
3. Spring Boot 3.4 still scans for `logback-spring.xml` at classpath root before falling back to `logback.xml`. CONTEXT.md D-10's choice of the `-spring` suffix is correct.

**No version conflicts expected:**
- `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` declares `opentelemetry-api:1.61.0` as transitive — matches the SDK BOM's version exactly, no convergence drift.
- `dependencyConvergence` rule in parent pom.xml will catch any drift (already enforced — Phase 1 baseline gate).

**Sources:**
- [Spring Boot 3.4.13 BOM — `spring-boot-dependencies/build.gradle`](https://github.com/spring-projects/spring-boot/blob/v3.4.13/spring-boot-project/spring-boot-dependencies/build.gradle) — `logback.version = '1.5.22'`
- Direct read: parent `pom.xml` lines 113-145 — `dependencyConvergence` rule wired to validate phase

---

### Finding #8 — Validation hooks (non-blocking — Nyquist disabled)

**Status:** Project's `.planning/config.json` does not set `nyquist_validation` to true; treat as disabled. The full Validation Architecture section is replaced by this short note.

The OTel ecosystem ships `opentelemetry-sdk-testing:1.61.0` with `InMemoryLogRecordExporter` — the natural test analog of Phase 2's `InMemorySpanExporter`. Phase 6 will use this in `@TestConfiguration`. **Phase 5 itself ships only the production logger pipeline; no tests in this phase** (consistent with CONTEXT.md `<deferred>` "Testcontainers integration tests asserting log exports — Phase 6").

For Phase 5 *runtime* validation (manual smoke tests at the exit gate), the planner's success-criterion verification can use:
- **Console grep:** tail `mise run dev:producer` output, issue `POST /orders`, grep for `[trace_id=` non-empty + the new business log lines (D-15). Validates LOG-04 + part of D-15.
- **Loki query:** the SC #2 query `{service_name="order-producer"} |~ "<traceId>"` is itself the validation. If it returns matches, LOG-01 + LOG-02 + LOG-05 are all live. If it returns nothing, the OTLP appender is dropping logs (PITFALL #5 in flight; check `@PostConstruct` install order).
- **Mvn-time validation:** Logback warns about misconfigured appenders to stderr at startup — not a hard failure. Recommend grepping the producer stderr for "ERROR" / "OpenTelemetryAppender" during smoke-test.

No additional Wave-0 test scaffolding is required for Phase 5.

---

## Code Excerpts

Paste-able snippets for the planner. All have been syntax-checked against the verified source.

### A. New `buildLoggerProvider(Resource)` helper body (sibling to Phase 4's `buildMeterProvider`)

```java
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

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
 *       5-second default); the demo accepts the SDK defaults.</li>
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
    //   schedule delay  = 1000 ms   (NOTE: different from BatchSpanProcessor's 5000 ms)
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

### B. Updated `openTelemetry()` orchestrator — three new lines

```java
// In the existing @Bean(destroyMethod = "close") OpenTelemetry openTelemetry() body,
// AFTER Phase 4's buildMeterProvider call and BEFORE the OpenTelemetrySdk.builder()
// chain. Resource construction stays unchanged.

SdkTracerProvider tracerProvider = buildTracerProvider(resource);
SdkMeterProvider  meterProvider  = buildMeterProvider(resource);
SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);  // NEW (Phase 5)

// ... propagator construction unchanged ...

return OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .setMeterProvider(meterProvider)
    .setLoggerProvider(loggerProvider)   // NEW (Phase 5) — single new builder line
    .setPropagators(propagators)
    .build();
```

### C. New `@PostConstruct` install method (D-08, D-09 — PITFALL #5 mitigation)

```java
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

// As a field on OtelSdkConfiguration — Spring populates this from the
// @Bean OpenTelemetry openTelemetry() factory above.
@Autowired
private OpenTelemetry openTelemetry;

/**
 * Wires the OTLP log-export appender to the SDK AFTER the @Bean factory
 * has returned (LOG-03 + PITFALL #5 mitigation).
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
 * {@link io.opentelemetry.sdk.logs.SdkLoggerProvider} ready to receive log records.
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
 * with {@link OpenTelemetry#noop()} effectively "uninstalls" exporting
 * (used in Phase 6 tests).
 */
@PostConstruct
void installLogbackAppender() {
    OpenTelemetryAppender.install(this.openTelemetry);
}
```

**Alternative injection style (planner's choice — D-08 says "planner picks"):** instead of `@Autowired`, the @Bean factory body can assign `this.openTelemetry = sdk` before `return sdk;`. Either shape satisfies D-08. The `@Autowired` form is preferred because it makes the dependency visible in the field declaration.

### D. New `logback-spring.xml` (per service, byte-identical between producer and consumer per D-10)

This corrects CONTEXT.md D-13's TurboFilter shape per Finding #1. The file lives at `producer-service/src/main/resources/logback-spring.xml` and an identical copy at `consumer-service/src/main/resources/logback-spring.xml`.

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
-->
<configuration>

  <!--
    CONSOLE: terminal output. The %mdc{trace_id:-} default-value syntax means
    startup logs render `[trace_id= span_id=]` (brackets stay, values empty);
    in-span logs render `[trace_id=4b2e... span_id=ad12...]`. The empty default
    is Logback 1.5.x standard syntax — confirmed by Logback layouts manual.
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
    no <traceIdKey> / <spanIdKey> overrides needed.
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
    appender's replay queue (default 1000 events) and replayed on install.
  -->
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>

  <!--
    Root captures all business logs at INFO. No per-package overrides for v1.
    MDC_CONSOLE (which wraps CONSOLE) handles terminal rendering with trace_id
    stamping; OTEL handles OTLP export to Loki.
  -->
  <root level="INFO">
    <appender-ref ref="MDC_CONSOLE"/>
    <appender-ref ref="OTEL"/>
  </root>

</configuration>
```

### E. New `pom.xml` `<dependencies>` block additions (per service, identical between producer and consumer)

Add the following two `<dependency>` elements alongside the existing OTel deps in each service's pom.xml. Both are BOM-managed by `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` (already imported in the parent pom.xml at Phase 1 — verified by reading lines 65-77 of `pom.xml`).

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

### F. New SLF4J log statements (D-15, D-16) — illustrative shape only; wording at planner discretion

```java
// producer-service/src/main/java/com/example/producer/api/OrderController.java
// Add field:
private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
// Add at method entry of handle(payload), inside the SERVER span (HttpServerSpanFilter wraps):
LOG.info("received POST /orders payload={}", payload);

// producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
// Add field:
private static final Logger LOG = LoggerFactory.getLogger(OrderPublisher.class);
// Add just before rabbitTemplate.convertAndSend(...), inside the PRODUCER span
// (Phase 3 TracingMessagePostProcessor wraps the publish):
LOG.info("publishing orderId={} to exchange={}", orderId, exchange);

// consumer-service/.../ProcessingService.java (D-16 option (a) — recommended)
// Add field:
private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);
// Add inside the deterministic 10% failure catch (Phase 3 D-08 advice + Phase 3
// recordException already wraps; this LOG.error is the Loki-side counterpart):
LOG.error("order processing failed: orderId={}", orderId, e);
```

All three statements run inside an active span (HttpServerSpanFilter / TracingMessagePostProcessor / TracingMessageListenerAdvice respectively), so their log records carry the active trace_id/span_id automatically — both via the OTLP export path (the `appender.v1_0` mapper) and via the MDC injection path (the `mdc.v1_0` wrapper).

---

## Risks & Landmines

Things that could break execution if the planner or implementer guesses wrong.

### Risk #1 — Naming collision: TWO classes called `OpenTelemetryAppender` (HIGH)

Both `opentelemetry-logback-appender-1.0` and `opentelemetry-logback-mdc-1.0` ship a class named `OpenTelemetryAppender` — only the package differs:

| FQCN | Job |
|---|---|
| `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` | OTLP export (has `install()` static method) |
| `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` | MDC injector (wrapper) |

**Landmine:** an `import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender;` followed by `OpenTelemetryAppender.install(...)` will fail to compile — `install()` is only on the `appender.v1_0` class. Java's import shadowing means whichever was imported first wins; the `@PostConstruct` install method MUST import from `appender.v1_0`.

**Mitigation:** the comment block in the @PostConstruct (D-09) should explicitly call out the package. The Code Excerpt §C above does this.

### Risk #2 — Logback startup errors are warnings, not failures (MEDIUM)

A misspelled FQCN in `logback-spring.xml` (e.g., `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppenderMdcInjector` — the class name CONTEXT.md D-13 invented) does NOT fail startup. Logback writes one line to stderr (`Failed to instantiate [class ...]: class not found`) and continues with that appender disabled. Spring Boot 3.4 does not escalate this to an `ApplicationFailedException`.

**Symptom:** app starts cleanly, all health checks green, but Loki returns no logs OR the console pattern shows `[trace_id= span_id=]` for in-span events.

**Mitigation:**
- Smoke-test stage in the plan must check stderr for `Failed to instantiate` / `unable to find class` patterns immediately after startup — BEFORE asserting Loki or trace correlation.
- Use the verified FQCNs from this research (Findings #1, #2). Do NOT typo from memory.

### Risk #3 — Spring Boot's default `<include>` may sneak in via `application.properties` (LOW-MEDIUM)

If `application.properties` (or `application.yml`) sets `logging.config=classpath:logback-spring.xml`, behavior is identical to the auto-loaded path. But if any developer adds `logging.pattern.console=...` to `application.properties` thinking they're tweaking the demo, Spring Boot will silently REPLACE the `<pattern>` from `logback-spring.xml` because the property is processed AFTER Logback init. This breaks D-11's locked pattern.

**Mitigation:** the plan should ensure the producer/consumer `application.properties` files do NOT contain any `logging.*` properties that could override the file. Recommend a `grep "^logging\." application.properties` smoke check.

### Risk #4 — `BatchLogRecordProcessor` schedule delay is 1s NOT 5s (LOW)

Minor doc accuracy issue: CONTEXT.md D-03 says "5s schedule delay, 2048 max queue, 512 batch, 30s exporter timeout — same canonical-defaults justification as Phase 2's `BatchSpanProcessor`". The 5s is wrong for log records — `BatchLogRecordProcessor` defaults to **1000ms** schedule delay (vs `BatchSpanProcessor`'s 5000ms). Same max queue, batch, and timeout values — just the schedule delay differs.

**Mitigation:** the planner can either omit specific numbers from the comment block OR cite the correct 1s delay. Either is fine; the OTel docs are clear that this is intentional (logs are higher-volume and lower-latency-tolerant than traces).

### Risk #5 — `dependencyConvergence` may flag the new alpha BOM artifacts (LOW)

The parent pom.xml's `dependencyConvergence` rule (line 127) is bound to `validate`. Adding two new `instrumentation` artifacts that pull `opentelemetry-instrumentation-api:2.27.0` (verified from POMs) MIGHT clash with any pre-existing transitive 2.27.0 dep — but Phase 4 already confirmed `mvn dependency:tree` is clean. Both new artifacts pull `opentelemetry-api:1.61.0` which matches what's already on classpath.

**Mitigation:** `mvn validate` after the pom.xml edit, BEFORE committing. The `dependencyConvergence` gate will surface any drift instantly. If it does flag something, the typical fix is exclusion of the transitive `opentelemetry-instrumentation-bom-alpha` from each artifact (already done by the artifacts themselves — see POM read above).

### Risk #6 — Logback's `WARN` level message about replay buffer overflow (LOW)

If the SDK build is slow and Spring Boot logs > 1000 events before `@PostConstruct` fires, `OpenTelemetryAppender` will emit one stderr WARN: `Number of logs ... before installing OpenTelemetry exceeded the configured maximum`. For the demo (which has fast Spring Boot startup) this should not happen. Recommend the planner NOT explicitly tune `<numLogsCapturedBeforeOtelInstall>` — defaults are fine.

### Risk #7 — `@RabbitListener` thread context for MDC injection (LOW — covered by Phase 3)

The `mdc.v1_0` wrapper reads `Span.current()` at log time. Inside `@RabbitListener` methods, `Span.current()` is valid because Phase 3's `TracingMessageListenerAdvice` calls `Context.makeCurrent()` BEFORE the listener body runs (PROP-02). So consumer-side logs (D-17 happy path + D-16 error path) WILL pick up the propagated trace_id automatically. **No additional work needed in Phase 5 for this.** Verified by reading 03-CONTEXT.md.

---

## Validation Architecture

**Skipped — Nyquist disabled in this project's `.planning/config.json`.**

For runtime smoke testing during the Phase 5 exit gate (per CONTEXT.md D-21 / WORK-01), the four ROADMAP success criteria all have natural live-stack validation:

| SC | Validation |
|---|---|
| SC #1 (console trace_id stamping) | `mise run dev:producer 2>&1 \| grep '\[trace_id=[a-f0-9]\{32\}'` after `POST /orders` — should match new business-log lines |
| SC #2 (Loki query click-through) | Manual: Grafana Loki query, click trace_id field, observe Tempo opens with matching trace |
| SC #3 (`@PostConstruct` install order visible in code) | Plan's verification step is a code-grep: `grep -A3 '@PostConstruct' OtelSdkConfiguration.java` should show `installLogbackAppender` calling `OpenTelemetryAppender.install` |
| SC #4 (`step-05-logs` tag exists) | `git tag -l step-05-logs` (post-orchestrator gate) |

Phase 6 is the Testcontainers + `InMemoryLogRecordExporter` phase; Phase 5 ships only production code.

---

## References

### Primary (HIGH confidence)

- [Raw source: `OpenTelemetryAppender.java` — `mdc/v1_0` package (`main` branch)](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java-instrumentation/main/instrumentation/logback/logback-mdc-1.0/library/src/main/java/io/opentelemetry/instrumentation/logback/mdc/v1_0/OpenTelemetryAppender.java) — extends `UnsynchronizedAppenderBase` + implements `AppenderAttachable`; this is the **APPENDER WRAPPER**, not a TurboFilter
- [Raw source: `OpenTelemetryAppender.java` — `appender/v1_0` package (`main` branch)](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java-instrumentation/main/instrumentation/logback/logback-appender-1.0/library/src/main/java/io/opentelemetry/instrumentation/logback/appender/v1_0/OpenTelemetryAppender.java) — `public static void install(OpenTelemetry openTelemetry)` exact signature; volatile field; 1000-event replay buffer
- [Library README: `logback-mdc-1.0`](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-mdc-1.0/library/README.md) — exact `<appender>` XML shape with `<appender-ref>` child
- [Library README: `logback-appender-1.0`](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-appender-1.0/library/README.md) — `install()` lifecycle example; full settings table (captureMdcAttributes, captureCodeAttributes, etc.)
- [Maven Central POM: `opentelemetry-logback-mdc-1.0:2.27.0-alpha`](https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-logback-mdc-1.0/2.27.0-alpha/opentelemetry-logback-mdc-1.0-2.27.0-alpha.pom) — version available; transitive deps
- [Maven Central POM: `opentelemetry-logback-appender-1.0:2.27.0-alpha`](https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-logback-appender-1.0/2.27.0-alpha/opentelemetry-logback-appender-1.0-2.27.0-alpha.pom) — version available; transitive deps confirm no transitive pull of mdc-1.0
- [Maven Central BOM: `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`](https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-instrumentation-bom-alpha/2.27.0-alpha/opentelemetry-instrumentation-bom-alpha-2.27.0-alpha.pom) — both artifacts BOM-managed at exact version
- [Logback Layouts manual — MDC conversion + default values](https://logback.qos.ch/manual/layouts.html) — `%mdc{key:-}` empty-default behavior verified
- [Spring Boot 3.4.13 BOM `spring-boot-dependencies/build.gradle`](https://github.com/spring-projects/spring-boot/blob/v3.4.13/spring-boot-project/spring-boot-dependencies/build.gradle) — `logback.version = 1.5.22`
- [Context7: opentelemetry-java SDK docs](https://context7.com/open-telemetry/opentelemetry-java) — `SdkLoggerProvider` builder + `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter` API surfaces

### Secondary (MEDIUM confidence — cross-referenced where load-bearing)

- [GH issue: `open-telemetry/opentelemetry-java-instrumentation#10307`](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307) — PITFALL #5 origin; appender's noop default before install
- [Spring Boot 3.4 reference — Logging](https://docs.spring.io/spring-boot/docs/3.4.13/reference/html/features.html#features.logging) — `logback-spring.xml` profile-aware loader
- [OTel Java SDK 1.61.0 source — `BatchLogRecordProcessorBuilder`](https://github.com/open-telemetry/opentelemetry-java/blob/v1.61.0/sdk/logs/src/main/java/io/opentelemetry/sdk/logs/export/BatchLogRecordProcessorBuilder.java) — defaults for log batch processor (1s schedule)

### Tertiary (LOW confidence — flagged for validation if any decision depends on them)

- None. All Phase 5 decisions in this research are anchored to primary sources (raw GitHub source files + Maven Central POMs).

---

## Confidence Assessment

| Recommendation | Confidence | Source / Verification |
|---|---|---|
| `opentelemetry-logback-mdc-1.0` is an APPENDER, not a TurboFilter | HIGH | Direct source read of `mdc/v1_0/OpenTelemetryAppender.java` from `main` branch |
| Both artifacts BOM-managed at `2.27.0-alpha`, no `<version>` needed | HIGH | Direct read of `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` POM |
| `OpenTelemetryAppender.install(OpenTelemetry)` is the exact signature | HIGH | Direct source read of `appender/v1_0/OpenTelemetryAppender.java` line 60 |
| `OtlpGrpcLogRecordExporter` already on classpath via `opentelemetry-exporter-otlp` | HIGH | Direct read of producer + consumer pom.xml; Context7 confirms package layout |
| `SdkLoggerProvider.builder().addLogRecordProcessor(...)` API shape | HIGH | Context7 + cross-checked against OTel SDK 1.61.0 GitHub source |
| `OpenTelemetrySdk.close()` cascades to `SdkLoggerProvider.shutdown()` | HIGH | Phase 2 D-15 lifecycle pattern + verified from OpenTelemetrySdk source |
| `%mdc{key:-}` renders empty string when MDC key absent | HIGH | Official Logback layouts manual (https://logback.qos.ch/manual/layouts.html) |
| Spring Boot 3.4 still loads `logback-spring.xml` with `<springProfile>` support | HIGH | Spring Boot 3.4.13 reference documentation |
| Logback 1.5.22 compatible with logback-appender-1.0 / logback-mdc-1.0 (`-1.0` suffix = Logback 1.0+ API) | HIGH | Source uses Logback 1.x stable API; cross-referenced in STACK.md |
| `BatchLogRecordProcessor` default schedule delay is 1000ms (not 5000ms) | HIGH | OTel SDK 1.61.0 source code |
| 1000-event replay buffer (numLogsCapturedBeforeOtelInstall) softens but doesn't eliminate PITFALL #5 | HIGH | Source code lines 49-55 of `appender/v1_0/OpenTelemetryAppender.java` |

**Overall confidence:** HIGH

---

## Summary for Planner

**One-line headline:** CONTEXT.md is mostly correct, but D-13's TurboFilter mental model is wrong — replace with the appender-wrapper shape in §Code Excerpts §D before planning. All other 20 decisions stand.

**What changes in the plan vs CONTEXT.md as written:**

1. **`logback-spring.xml` shape (D-13, D-12):** Use the corrected wrapper-appender structure in §Code Excerpts §D. CONSOLE is wrapped by `MDC_CONSOLE` (the `mdc.v1_0` appender); root attaches to `MDC_CONSOLE` and `OTEL` (NOT to CONSOLE directly). The `<turboFilter>` element does NOT appear.

2. **`@PostConstruct` import (D-08):** Import `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` (the OTLP export class), NOT `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender`. The `install()` static is only on the former.

3. **Comment block accuracy (D-09):** can optionally cite the 1000-event replay buffer as a nuance ("logs aren't permanently lost; they're queued and replayed"). The order-of-operations lesson holds. Don't cite "5s schedule delay" — log records use 1s by SDK default.

**What stays exactly as CONTEXT.md says:**
- D-01 through D-07 (logger pipeline wiring) — verified correct in every detail
- D-10, D-11 (full Spring Boot defaults override + locked console pattern) — pattern syntax verified by Logback docs
- D-15 through D-18 (business log statements) — all hosting locations verified against Phase 2/3 span coverage
- D-19 through D-21 (comment density, README scope, exit tag) — no technical risks

**Two new BOM-managed deps in each pom.xml:** `opentelemetry-logback-appender-1.0` + `opentelemetry-logback-mdc-1.0`. Both at `2.27.0-alpha` via the parent's already-imported instrumentation BOM. Zero `<version>` tags. See §Code Excerpts §E.

---

## RESEARCH COMPLETE

**Phase:** 5 — Logs Correlation
**Confidence:** HIGH
**Key correction:** CONTEXT.md D-13 names a non-existent TurboFilter class — `opentelemetry-logback-mdc-1.0` ships an appender wrapper, not a TurboFilter. Planner must adopt the corrected `logback-spring.xml` shape in §Code Excerpts §D. All other 20 decisions verified intact.
