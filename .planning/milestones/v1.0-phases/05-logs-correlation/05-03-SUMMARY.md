---
phase: 05-logs-correlation
plan: 03
subsystem: consumer-service / OTel SDK + Logback bridge
tags:
  - opentelemetry
  - logback
  - sdk
  - consumer
  - postconstruct
  - mirror
dependency_graph:
  requires:
    - 05-01-pom-dependencies (instrumentation-bom-alpha + two logback bridge deps on consumer classpath)
    - 05-02-producer-sdk-and-logback (parallel wave-2 sibling — establishes producer's Phase 5 mirror; D-10 byte-identical
      diff between producer and consumer logback-spring.xml is validated by the orchestrator post-merge, since 05-02
      runs in a sibling worktree and is not visible at execution time)
  provides:
    - Consumer-side logger pipeline — SdkLoggerProvider with BatchLogRecordProcessor + OtlpGrpcLogRecordExporter
      registered on the OpenTelemetry SDK @Bean (LOG-01)
    - Consumer-side OpenTelemetryAppender.install() wired via @PostConstruct — mitigates PITFALL #5 / GH #10307
      (silent 1000-event replay buffer drained once the SDK is built; LOG-03 / D-08 / D-09)
    - Consumer-side logback-spring.xml — full Spring Boot defaults override; declares CONSOLE + MDC_CONSOLE wrapper
      + OTEL OTLP appender; root attaches MDC_CONSOLE + OTEL (LOG-02 / LOG-04 / D-10 / D-11 / D-12 / D-13-correction)
    - Foundation surface for Plan 05-05 (consumer error log line — LOG-05) to flow through the OTLP appender to Loki
      with the active trace_id/span_id stamped automatically
  affects:
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (modified — 6 EDITs)
    - consumer-service/src/main/resources/logback-spring.xml (created)
tech_stack:
  added: []
  patterns:
    - per-service-duplication of SDK bootstrap (D-02 carryforward — Phase 2 D-01 / Phase 4 D-02; Phase 5
      adds a third sibling helper to the producer/consumer twin file pair)
    - sibling-helper structure (buildTracerProvider / buildMeterProvider / buildLoggerProvider all read as
      parallel blocks inside the orchestrator @Bean)
    - shared Resource across all three providers (D-05 — single service.name + service.instance.id flowing to
      Tempo, Mimir, and Loki for cross-signal correlation)
    - lifecycle cascade via @Bean(destroyMethod="close") — no new lifecycle annotation needed for the logger
      pipeline; OpenTelemetrySdk.close() cascades to SdkLoggerProvider.shutdown() (D-06)
    - byte-identical logback-spring.xml between producer and consumer (D-10 — service.name is sourced from the
      SDK Resource, not from the Logback file; the per-service-duplication ethos applies symmetrically without
      any service-name-driven differences in this XML)
key_files:
  created:
    - consumer-service/src/main/resources/logback-spring.xml
  modified:
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
decisions:
  - Mirror producer's Phase 5 additions verbatim where service-agnostic; preserve consumer-specific surfaces
    (service.name "order-consumer", scope "com.example.consumer", no-HttpServerSpanFilter callout, no
    httpServerSpanFilter @Bean) per D-02 / D-07.
  - Use the @Autowired field shape (not factory-body assignment) for the SDK handle consumed by
    @PostConstruct — RESEARCH §C recommendation; makes the dependency visible in the field declaration.
  - Construct consumer's logback-spring.xml from RESEARCH §Code Excerpts §D verbatim (the same source that
    Plan 05-02 uses for producer's file) so both files are byte-identical post-merge without coordination
    between the sibling worktrees.
metrics:
  duration: ~2min
  completed_date: 2026-05-02
---

# Phase 5 Plan 03: Consumer SDK + Logback Mirror Summary

Mirrored Phase 5's SDK additions into consumer-service: added `buildLoggerProvider(Resource)` helper +
`@PostConstruct installLogbackAppender()` + `.setLoggerProvider(...)` chain in the orchestrator @Bean, fixed
the class JavaDoc for D-07 (no Logger @Bean), and created consumer's `logback-spring.xml` with the corrected
appender-wrapper shape (RESEARCH Finding #1 — mdc.v1_0 is an Appender wrapper, NOT a TurboFilter as
CONTEXT.md D-13 originally claimed). All consumer-specific surfaces preserved per D-02 / D-07.

## Files Modified

| File | Action | Purpose |
|------|--------|---------|
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | modified (6 EDITs) | Mirror of producer's Plan 05-02 additions — buildLoggerProvider helper, @PostConstruct install, @Autowired SDK field, `.setLoggerProvider` chain, JavaDoc D-07 fix, 6 new imports. Consumer-specific surfaces preserved. |
| `consumer-service/src/main/resources/logback-spring.xml` | created | Byte-identical mirror of producer's logback-spring.xml (D-10). Two appenders (CONSOLE + OTEL) plus the MDC_CONSOLE wrapper around CONSOLE; root attaches to MDC_CONSOLE + OTEL. No `<turboFilter>` (D-13 correction). No Spring Boot logback-defaults `<include>` (D-10 full override). |

## D-02 Mirror Property — Diff Lines vs Producer

**Note:** The byte-identical diff between producer and consumer `OtelSdkConfiguration.java` (canonical D-02
differences only) cannot be measured at execution time — Plan 05-02 runs in a sibling worktree and is not
visible here. Both files are constructed verbatim from the same source-of-truth (RESEARCH §Code Excerpts
§A / §B / §C and the producer file's pre-Plan-05-02 state), so the only diff post-merge will be:

| Diff bucket | Why |
|-------------|-----|
| `package com.example.consumer.config` vs `com.example.producer.config` | Per-service Java package |
| `service.name "order-consumer"` vs `"order-producer"` | D-02 service identity |
| Tracer scope `"com.example.consumer"` vs `"com.example.producer"` | D-02 instrumentation scope |
| Meter scope `"com.example.consumer"` vs `"com.example.producer"` | D-02 instrumentation scope |
| Class JavaDoc paragraph `<p><b>Why no HttpServerSpanFilter here?</b>` | Consumer-only callout (D-07) |
| Tracer @Bean JavaDoc references (`OrderListener and ProcessingService` vs `OrderService, OrderPublisher, and HttpServerSpanFilter`) | Consumer-side call sites |
| Meter @Bean JavaDoc references (`QueueDepthGauge (Plan 04-04 — orders.queue.depth.estimate ObservableGauge)` vs `OrderService (Plan 04-02 …) and HttpServerSpanFilter (Plan 04-03 …)`) | Consumer-side call sites |
| Producer-only `httpServerSpanFilter` @Bean (last 5 lines of producer file) | Absent in consumer (D-07 / D-02) |
| Pre-existing locale variants (`artefact`/`artifact`, `behaviour`/`behavior`, `cord`/`coord`, `cite`/`cited`, `neutralizes`/`neutralises`) | Pre-existing baseline; out of scope to normalise |

The Phase 5 additions themselves (buildLoggerProvider helper body, @PostConstruct install method, @Autowired
field block, "No Logger @Bean (D-07)" JavaDoc paragraph, 6 new imports) are byte-identical between the two
services — no service-specific text inside any of those additions.

**Consumer file metrics (post-edit):**

- Total lines: 473 (up from 333 — +140 lines for the 6 EDITs)
- Comment lines: 325 (D-19 requires ≥ 80 — well above the floor)

## D-10 Byte-Identical Logback Property

The byte-identical diff between producer's and consumer's `logback-spring.xml` will be validated post-merge
by the orchestrator. Both files are constructed verbatim from RESEARCH §Code Excerpts §D — the same
source-of-truth Plan 05-02 uses — so the diff should be empty.

**Consumer logback-spring.xml metrics:**

- 70 lines total (including the leading XML declaration and closing `</configuration>`)
- 3 `<appender>` declarations: CONSOLE, MDC_CONSOLE (wraps CONSOLE), OTEL
- 1 `<root level="INFO">` with two `<appender-ref>` children (MDC_CONSOLE, OTEL)
- 0 `<turboFilter>` elements (D-13 correction)
- 0 `<include>` elements (D-10 full Spring Boot defaults override)
- Pattern verbatim matches D-11: `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]` inside the bracketed
  encoder pattern

## Verification Results

| Gate | Command | Result |
|------|---------|--------|
| Build clean (consumer + transitive deps) | `mvn -B -pl consumer-service -am compile` | exit 0 — BUILD SUCCESS |
| `dependencyConvergence` enforcer rule | (part of compile above) | passed (no version drift from the new logback bridge artifacts) |
| LOG-01-consumer (logger provider helper exists) | grep `private SdkLoggerProvider buildLoggerProvider` | matched |
| LOG-01-consumer-wired (orchestrator chains setLoggerProvider) | grep `\.setLoggerProvider(loggerProvider)` | matched |
| LOG-03-consumer (@PostConstruct install) | grep `@PostConstruct` + `OpenTelemetryAppender.install(` | matched |
| D-08-consumer-fqcn (Risk #1 mitigated — appender.v1_0 import only) | grep `appender.v1_0.OpenTelemetryAppender` AND NOT `mdc.v1_0.OpenTelemetryAppender` in code | matched |
| D-07-consumer-javadoc ("No Logger @Bean" present, old promise removed) | grep `No Logger @Bean` AND NOT `will add a sibling.*Logger @Bean` | matched |
| LOG-02-consumer (logback has both FQCNs) | grep both `appender.v1_0.OpenTelemetryAppender` and `mdc.v1_0.OpenTelemetryAppender` in XML | matched |
| D-13-consumer-correction (no `<turboFilter>`) | NOT grep `<turboFilter` | matched |
| D-12-consumer-root (root → MDC_CONSOLE + OTEL, NOT bare CONSOLE) | awk root block | matched |
| LOG-04-consumer (D-11 console pattern) | grep `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]` | matched |
| D-10-no-spring-include-consumer | NOT grep `spring/boot/logging/logback/defaults.xml` | matched |
| D-19-comment-density-consumer (≥80) | comment-line count of consumer's OtelSdkConfiguration.java | 325 (≥ 80, passed) |
| D-09 PITFALL #5 markers (10307, order-of-operations, noop, replay/silent, lifecycle) | grep all five | all matched |
| Risk #3 — application.yaml has no `logging.*` overrides | NOT grep `^logging\.|^\s+logging\.|^logging:` | passed (no matches) |

**Cross-plan gates deferred to orchestrator post-merge:** the D-10 byte-identical `diff` between
producer's and consumer's `logback-spring.xml`, and the D-02 byte-identical mirror property of the Phase 5
additions inside the two `OtelSdkConfiguration.java` files, are validated post-merge because Plan 05-02 runs
in a sibling worktree (parallel wave 2). Both consumer-side files are constructed verbatim from RESEARCH's
§Code Excerpts (the same source-of-truth Plan 05-02 uses), so the post-merge diff should be empty / canonical.

## Deviations from Plan

None — plan executed exactly as written. No Rule 1/2/3 auto-fixes were necessary (no bugs surfaced; consumer
already had Plan 05-01's deps on classpath; no environmental blockers).

## Authentication Gates

None encountered — execution is purely source modification + Maven compile; no external service auth.

## Self-Check

**Created files exist:**

```
[FOUND] consumer-service/src/main/resources/logback-spring.xml
```

**Modified files exist (with new content):**

```
[FOUND] consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
        (now 473 lines, comment lines 325 — verified ≥ D-19 floor of 80)
```

**Commits exist on this worktree branch:**

```
[FOUND] 75f74cd feat(05-03): mirror Phase 5 SDK additions into consumer OtelSdkConfiguration
[FOUND] f8abe22 feat(05-03): add consumer logback-spring.xml with OTLP + MDC appender chain
```

## Forward Links

- **Plan 05-04 — producer business logs:** Adds `LOG.info` lines to `OrderController` and `OrderPublisher`
  (D-15) so producer-side logs flow through the OTEL appender to Loki with the SERVER and PRODUCER span
  trace_ids stamped.
- **Plan 05-05 — consumer error log:** Adds `LOG.error` line to `ProcessingService` (D-16) inside the
  Phase 3 deterministic-failure catch; will flow through THIS plan's logger pipeline + OpenTelemetryAppender
  with the CONSUMER span trace_id stamped (Phase 3's `TracingMessageListenerAdvice` calls
  `Context.makeCurrent()` before the listener body runs, so `Span.current()` is valid at log time —
  RESEARCH Risk #7 verified).
- **Plan 05-06 — README + tag:** Smoke-tests the entire pipeline end-to-end via the live Loki query
  `{service_name="order-consumer"} |~ "<traceId>"`; if it returns matches, this plan's @PostConstruct
  install fired correctly.

## Self-Check: PASSED

- All Task 1 acceptance criteria gates returned 0 / matched.
- All Task 2 acceptance criteria gates returned 0 / matched.
- `mvn -B -pl consumer-service -am compile` exits 0 (BUILD SUCCESS).
- Comment density on consumer's OtelSdkConfiguration.java: 325 (≥ 80, D-19 floor).
- Risk #1 mitigated: only `appender.v1_0.OpenTelemetryAppender` imported in code (forbidden
  `mdc.v1_0.OpenTelemetryAppender` import is absent — verified via inverse grep).
- Risk #3 verified: consumer's `application.yaml` contains no `logging.*` properties that could
  silently override the locked D-11 console pattern.
- Threat surface scan: nothing introduced beyond the threats already enumerated in `<threat_model>`
  (T-05-03-01 .. T-05-03-08 carryforward from Plan 05-02). No new endpoints, no new auth paths, no
  schema changes — purely SDK bootstrap + Logback config in a service that already exists.
