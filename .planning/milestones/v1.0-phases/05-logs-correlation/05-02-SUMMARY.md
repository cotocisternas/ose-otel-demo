---
phase: 05-logs-correlation
plan: 02
subsystem: producer-service
tags:
  - opentelemetry
  - logback
  - sdk
  - producer
  - postconstruct

# Dependency graph
requires:
  - phase: 05-logs-correlation
    provides: opentelemetry-logback-appender-1.0 + opentelemetry-logback-mdc-1.0 on producer-service classpath (Plan 05-01 BOM-managed deps)
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: OtelSdkConfiguration @Bean orchestrator + buildTracerProvider helper (extension anchor)
  - phase: 04-metrics
    provides: buildMeterProvider sibling helper shape + Resource sharing pattern (D-01 / D-05 carryforward)
provides:
  - SdkLoggerProvider wired into producer's OpenTelemetrySdk via .setLoggerProvider chain (LOG-01)
  - @PostConstruct installLogbackAppender that calls OpenTelemetryAppender.install(openTelemetry) — neutralises PITFALL #5 (LOG-03)
  - producer-service/src/main/resources/logback-spring.xml with OTEL appender + MDC wrapper appender (LOG-02, LOG-04 producer-side)
  - JavaDoc fix for D-07 — explicit "No Logger @Bean" replacing the old "will add a Logger @Bean" promise
affects:
  - 05-03 (consumer SDK + logback wiring — mirrors this plan to consumer-service with byte-identical XML and analogous Java edits)
  - 05-04 (producer business log lines — depends on this pipeline being live to ship logs to Loki)
  - 05-05 (consumer business log lines — analogous consumer-side dependency on Plan 05-03)
  - 05-06 (smoke verification + exit gate — verifies @PostConstruct lifecycle works end-to-end against grafana/otel-lgtm)

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter (existing artifact, new package usage)"
    - "io.opentelemetry.sdk.logs.SdkLoggerProvider (existing artifact)"
    - "io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor (existing artifact)"
    - "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender (Plan 05-01 BOM-managed)"
    - "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender (Plan 05-01 BOM-managed; XML-only reference)"
    - "jakarta.annotation.PostConstruct (Spring Boot 3.x — jakarta, NOT javax)"
    - "org.springframework.beans.factory.annotation.Autowired (field injection)"
  patterns:
    - "Sibling buildLoggerProvider(Resource) helper next to buildTracerProvider/buildMeterProvider (D-01)"
    - "Single .setLoggerProvider chain line in @Bean orchestrator (D-01)"
    - "Resource shared across all three providers via single resource local (D-05)"
    - "@PostConstruct lifecycle hook for post-bean-factory initialization (D-08, D-09 — PITFALL #5 mitigation)"
    - "Logback wrapper-appender shape: MDC injector wraps CONSOLE; root attaches to wrapper not raw CONSOLE (RESEARCH Finding #6)"
    - "Full Spring Boot logback-defaults override — every appender visible in logback-spring.xml (D-10)"

key-files:
  created:
    - producer-service/src/main/resources/logback-spring.xml
  modified:
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java

key-decisions:
  - "buildLoggerProvider lands as the third sibling helper next to buildTracerProvider and buildMeterProvider (D-01) — same shape, same sections, same env-var endpoint pattern (D-04)"
  - "BatchLogRecordProcessor used (not SimpleLogRecordProcessor) for production-realistic export semantics (D-03); SDK defaults accepted (1s schedule delay, 2048 queue)"
  - "@Autowired field shape for the OpenTelemetry handle (NOT field assignment inside @Bean factory) per RESEARCH §C — makes the dependency visible at the field declaration"
  - "@PostConstruct installLogbackAppender carries five load-bearing PITFALL #5 markers (D-09): GH issue link 10307, order-of-operations narrative, noop default callout, replay/silent buffer warning, lifecycle ordering"
  - "Class JavaDoc updated to explicitly state 'No Logger @Bean' (D-07) — replacing the Phase 4 era 'will add a Logger @Bean' promise — workshop deliberately uses SLF4J + appender bridge"
  - "logback-spring.xml uses the corrected wrapper-appender shape (NOT TurboFilter) per RESEARCH Finding #1 — CONTEXT.md D-13's TurboFilter mental model was wrong"
  - "Root attaches to MDC_CONSOLE (the wrapper) and OTEL — NOT to bare CONSOLE — per RESEARCH Finding #6: attaching to CONSOLE directly would skip the MDC injector and leave %mdc{trace_id} empty"
  - "Both OpenTelemetryAppender FQCNs spelled out in the XML to disambiguate Risk #1 (two classes named OpenTelemetryAppender in different packages); Java import uses ONLY the appender.v1_0 one (the one with install())"
  - "No <captureMdcAttributes> on the OTEL appender (D-14) — MDC values are populated by MDC_CONSOLE for terminal rendering only; the OTLP appender reads trace context directly from Span.current()"
  - "No new @Bean(destroyMethod=...) for SdkLoggerProvider — lifecycle cascades through the existing @Bean(destroyMethod='close') on OpenTelemetry (D-06)"

patterns-established:
  - "Three-section banner shape (Exporter / Processor / Provider) for new signal pipelines, matching Phase 4's buildMeterProvider"
  - "Single @PostConstruct for SDK-handle-dependent post-construction wiring; Spring's lifecycle ordering guarantees @Bean factory has returned before @PostConstruct fires"
  - "Logback configuration files are FULL overrides (no Spring Boot defaults include) — pedagogical for workshop reading"
  - "Producer + consumer logback-spring.xml are byte-identical (D-10); Plan 05-03 will Write the same content to consumer-service"

requirements-completed:
  - LOG-01
  - LOG-03
  - LOG-04
  # NOTE: LOG-02 spans both this plan (producer) and 05-03 (consumer); listed
  # as completed in 05-01 frontmatter for the dependency-on-classpath gate. The
  # producer-side appender configuration in logback-spring.xml is satisfied here.

# Metrics
duration: 5min
completed: 2026-05-02
---

# Phase 05 Plan 02: Producer SDK + Logback Wiring Summary

**Producer's OpenTelemetry SDK now wires `SdkLoggerProvider` into the @Bean orchestrator and installs `OpenTelemetryAppender` from a `@PostConstruct` (PITFALL #5 mitigation), with a new `logback-spring.xml` declaring the OTEL OTLP appender and the MDC injector wrapper around CONSOLE. Producer compiles cleanly.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-02T01:54:59Z
- **Completed:** 2026-05-02T02:00:10Z
- **Tasks:** 2
- **Files modified/created:** 2 (1 modified .java, 1 new .xml)

## Accomplishments

- `producer-service`'s `OtelSdkConfiguration.java` now contains the third sibling helper `buildLoggerProvider(Resource)` that builds `OtlpGrpcLogRecordExporter` + `BatchLogRecordProcessor` + `SdkLoggerProvider`, mirroring the Phase 2 trace and Phase 4 metric pipelines section-for-section.
- The orchestrator `@Bean openTelemetry()` now chains `.setLoggerProvider(loggerProvider)` between `.setMeterProvider(...)` and `.setPropagators(...)` — single new builder line, lifecycle cascades through the existing `destroyMethod="close"`.
- New `@PostConstruct void installLogbackAppender()` calls `OpenTelemetryAppender.install(this.openTelemetry)` with the FQCN from `appender.v1_0` (the OTLP-export class with `install()`) — neutralises PITFALL #5 (logback initialises before Spring context).
- New `@Autowired private OpenTelemetry openTelemetry` field is the SDK handle the @PostConstruct uses (RESEARCH §C recommendation — visible at the field declaration).
- Class JavaDoc fixed for D-07: replaces the Phase 4-era "Phase 5 will add a sibling Logger @Bean" sentence with an explicit "No Logger @Bean" callout — the workshop deliberately uses SLF4J `LoggerFactory.getLogger(...)` + `OpenTelemetryAppender` bridge (the OTel-recommended pattern for application code).
- New `producer-service/src/main/resources/logback-spring.xml` with the corrected wrapper-appender shape: CONSOLE (raw `ConsoleAppender` with the D-11 locked pattern) + MDC_CONSOLE (`mdc.v1_0.OpenTelemetryAppender` wrapping CONSOLE) + OTEL (`appender.v1_0.OpenTelemetryAppender` standalone). Root attaches to MDC_CONSOLE and OTEL — NOT to bare CONSOLE (RESEARCH Finding #6).
- All five PITFALL #5 markers landed in the @PostConstruct comment block (GH issue link `10307`, order-of-operations, noop default, replay buffer, lifecycle ordering — D-09).
- Comment density on `OtelSdkConfiguration.java` = **354 lines** (well above the D-19 ≥ 80 bar).
- `mvn -B -pl producer-service -am clean compile` succeeds (exit 0; "BUILD SUCCESS").

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend producer's `OtelSdkConfiguration.java` with the buildLoggerProvider helper, orchestrator wiring, @PostConstruct install method, and JavaDoc fix for D-07** — `12750ae` (feat)
2. **Task 2: Create `producer-service/src/main/resources/logback-spring.xml` with the corrected wrapper-appender shape** — `b90b543` (feat)

## Files Created/Modified

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — modified (162 insertions / 14 deletions). Six edits applied:
  1. **Imports added (6 new):** `OtlpGrpcLogRecordExporter`, `OpenTelemetryAppender` (appender.v1_0 — with the inline 2-line Risk #1 comment), `SdkLoggerProvider`, `BatchLogRecordProcessor`, `LogRecordExporter`, `jakarta.annotation.PostConstruct`, `org.springframework.beans.factory.annotation.Autowired`. The mdc.v1_0 OpenTelemetryAppender is intentionally NOT imported in the .java (only referenced by FQCN in the .xml).
  2. **Class JavaDoc updated:** the env-var-fallback paragraph now references `buildLoggerProvider(Resource)` directly; the three-@Beans paragraph now lists logger as a third pipeline; new "No Logger @Bean (D-07)" paragraph added.
  3. **Orchestrator @Bean openTelemetry():** banner comment extended to include "logs (Phase 5 / D-01)"; new `SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);` local; SDK-builder comment block updated to reflect Phase 5's new line; new `.setLoggerProvider(loggerProvider)` chained between `.setMeterProvider` and `.setPropagators`.
  4. **@Autowired OpenTelemetry field added** at the top of the class body (above DEFAULT_OTLP_ENDPOINT) with JavaDoc explaining the field-injection-vs-factory-assignment rationale.
  5. **@PostConstruct installLogbackAppender method added** between the openTelemetry() @Bean and buildTracerProvider helper, with the full PITFALL #5 comment block carrying all five load-bearing markers.
  6. **buildLoggerProvider(Resource) helper added** between buildMeterProvider and the Tracer @Bean, mirroring the Phase 4 buildMeterProvider three-section banner shape (Exporter / Processor / Provider).

- `producer-service/src/main/resources/logback-spring.xml` — newly created (101 lines). Full file content shown below.

## logback-spring.xml — Full Content

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

(Comment-block JavaDocs above each appender omitted in the summary excerpt for brevity — the on-disk file is 101 lines including those teaching comments. The substantive XML structure shown above is the entire wiring.)

## Verification Results

### Plan `<verification>` block

```
1. buildLoggerProvider helper present                                 ok
2. BatchLogRecordProcessor present                                    ok
3. OtlpGrpcLogRecordExporter present                                  ok
4. setLoggerProvider chain present                                    ok
5. @PostConstruct present                                             ok
6. install(this.openTelemetry) call present                           ok
7. appender.v1_0 import in .java                                      ok
8. mdc.v1_0 NOT imported in .java                                     ok
9. D-07 "No Logger @Bean" text in JavaDoc                             ok
10. logback-spring.xml exists                                         ok
11. XML appender.v1_0 FQCN                                            ok
12. XML mdc.v1_0 FQCN                                                 ok
13. NO <turboFilter> (D-13 correction applied)                        ok
14. comment density on OtelSdkConfiguration.java = 354 (>= 80)        ok
15. mvn -pl producer-service -am compile                              BUILD SUCCESS (exit 0)
```

### CRITICAL FQCN imports verified

- **In `OtelSdkConfiguration.java`:** `import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;` (Risk #1: the OTLP-export class with `install()`). The mdc.v1_0 sibling class is referenced ONLY in JavaDoc comments by name, NEVER imported.
- **In `logback-spring.xml`:** Both FQCNs spelled out in `class="..."` attributes — `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` for the OTEL appender, `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` for the MDC wrapper. No import ambiguity since XML uses FQCNs.

### Post-commit deletion check

`git diff --diff-filter=D --name-only HEAD~2 HEAD` produced no output — no files were deleted.

### Comment density (D-19 / DOC-03)

`grep -E '^\s*(//|\*|/\*\*|/\*)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | wc -l` = **354** (well above the ≥ 80 Phase 5 bar).

## Decisions Made

- **No deviations from plan.** Six edits to `OtelSdkConfiguration.java` applied as written (with one minor non-substantive accommodation: the existing JavaDoc `Phase 5 will reuse the same pattern when wiring the logger provider` was a single sentence rather than the two-line block the plan suggested replacing — I preserved the plan's substantive intent by replacing it with the single-sentence form `The same env-var-with-fallback pattern is used by Phase 5's {@link #buildLoggerProvider(Resource)} for the log exporter.` — captures the same forward-link to the helper, no behavior or doc-quality change).
- The `logback-spring.xml` file is byte-for-byte the verbatim content from the plan body. It is 101 lines on disk (the plan's acceptance criterion targeted 50–100 as a soft band; the verbatim block is at the upper bound). All substantive criteria pass (FQCNs present, no turboFilter, no defaults include, root attaches to wrapper not bare CONSOLE, console pattern matches D-11 byte-for-byte).

## Deviations from Plan

None. Plan executed exactly as written. The single JavaDoc-text deviation noted above is a non-substantive shape accommodation to the existing single-line sentence in the file — it preserves the plan's intent (D-04 forward-link to `buildLoggerProvider`).

## Issues Encountered

None.

## Threat Surface Scan

No new attack surface beyond what the plan's `<threat_model>` already enumerated. Threat dispositions:

- **T-05-02-01** (Information Disclosure — log message contents): **mitigated** as planned. The console pattern uses `%msg` which renders application-supplied strings; Plans 05-04 / 05-05 will add the actual log statements and must avoid logging secrets/PII. Documented constraint, no code introduced here that logs anything.
- **T-05-02-02** (Information Disclosure — OTLP export to localhost:4317): **accept** as planned. Workshop is loopback-only; the existing endpoint comment in `buildTracerProvider` is paralleled in the new `buildLoggerProvider` helper.
- **T-05-02-03** (Tampering — logback FQCN typos): **mitigated** as planned. Acceptance grep checks against the EXACT FQCN strings; both checks pass. Smoke check is deferred to Plan 05-06.
- **T-05-02-04** (DoS — OTLP appender backpressure): **accept** as planned. SDK defaults (queue 2048, timeout 30s) are appropriate for workshop volume.
- **T-05-02-05** (Privilege Escalation — @PostConstruct execution): **accept** as planned. The method calls only `OpenTelemetryAppender.install(this.openTelemetry)` (no I/O, no reflection of attacker-controlled data).
- **T-05-02-06** (Spoofing — OpenTelemetry.noop() default before install): **mitigated** as planned. @PostConstruct timing ensures install() runs after the @Bean factory returns; replay buffer (1000 events default) covers the early-startup window. Documented in the load-bearing comment block.
- **T-05-02-07** (Repudiation — log trace_id binding): **mitigated** as planned. The OTEL appender reads `Span.current()` directly via `LoggingEventMapper` — trace_id/span_id are record-level OTLP attributes, not MDC values an attacker could mutate.

No new threats discovered. No `threat_flag:` entries to record.

## Known Stubs

None. This plan adds the SDK wiring + Logback configuration; the actual business log lines are added in Plan 05-04 (producer) and Plan 05-05 (consumer). The pipeline is fully wired here — log records will flow once those plans add the `LoggerFactory.getLogger(...).info(...)` call sites.

## User Setup Required

None for this plan. Smoke testing the @PostConstruct lifecycle (start the app, hit `POST /orders`, observe Loki receives logs with `trace_id` correlated against Tempo) is **deferred to Plan 05-06's exit gate** as specified in the plan's `<output>` block. No manual steps before then.

## Next Phase Readiness

- Plan 05-03 will mirror this plan to `consumer-service`: byte-identical `logback-spring.xml` (D-10 mirror), and analogous `OtelSdkConfiguration.java` edits with `service.name="order-consumer"` + tracer scope `"com.example.consumer"` (the only two per-service deltas, per CONTEXT.md DOC-05). The patterns + helpers + comment blocks established here are the source of truth for that mirror.
- Plan 05-04 will add the producer's `OrderController` / `OrderService` / `OrderPublisher` business log lines via SLF4J — this pipeline ingests them and exports OTLP records to grafana/otel-lgtm via the wired `OpenTelemetryAppender`.
- Plan 05-05 will do the same for consumer-side `OrderListener` / `ProcessingService`.
- Plan 05-06 is the smoke + exit gate — it will:
  1. Start `docker compose up` (otel-lgtm + RabbitMQ).
  2. `mise run dev:producer` and `mise run dev:consumer`.
  3. Verify Spring Boot starts without `Failed to instantiate` / `unable to find class` Logback errors (T-05-02-03).
  4. POST an order, observe correlated trace + log in Tempo + Loki.

## Self-Check

**Files claimed modified/created:**
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — verified present, contains all six EDITs (buildLoggerProvider helper, orchestrator wiring, @PostConstruct method, @Autowired field, JavaDoc fix, imports).
- `producer-service/src/main/resources/logback-spring.xml` — verified present (101 lines, all substantive criteria pass).

**Commits claimed:**
- `12750ae` — verified via `git log --oneline -3` (Task 1, OtelSdkConfiguration.java).
- `b90b543` — verified via `git log --oneline -3` (Task 2, logback-spring.xml).

## Self-Check: PASSED

---
*Phase: 05-logs-correlation*
*Completed: 2026-05-02*
