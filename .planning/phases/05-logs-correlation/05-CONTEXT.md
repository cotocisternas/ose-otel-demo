# Phase 5: Logs Correlation - Context

**Gathered:** 2026-05-01
**Status:** Ready for research → planning (`/gsd-research-phase 5` confirms `opentelemetry-logback-mdc-1.0` coordinate, then `/gsd-plan-phase 5`)

<domain>
## Phase Boundary

Phase 5 adds the **third** OTel signal — logs — to both services by extending each service's existing `OtelSdkConfiguration.java` with a sibling `private SdkLoggerProvider buildLoggerProvider(Resource resource)` helper (D-01 carryforward — explicitly foreshadowed in the existing JavaDoc), wiring `OpenTelemetrySdk.builder().setLoggerProvider(...)` in the orchestrator @Bean, creating a per-service `logback-spring.xml` that defines a complete CONSOLE + OTEL appender pair plus an MDC TurboFilter, and calling `OpenTelemetryAppender.install(openTelemetry)` in a `@PostConstruct` method on `OtelSdkConfiguration` AFTER the SDK bean is built (LOG-03 + PITFALL #5 mitigation). The console pattern is stamped with `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]` so terminal output is correlatable, and three new business-logic log statements (two producer, one consumer-error) give the workshop a stable Loki-query target.

The pedagogical goal closes the three-signals loop: a workshop attendee tails the host console while running `POST /orders`, sees every business-logic log line stamped with `trace_id`/`span_id`; runs the Loki query `{service_name="order-producer"} |~ "<traceId>"` in Grafana, gets matching log lines, clicks the `trace_id` field on a line, and Grafana opens the matching trace in Tempo's Explore tab.

**In scope (Phase 5 delivers):**
- Refactor `OtelSdkConfiguration.java` in **both** services so the existing `openTelemetry()` @Bean adds a third sibling helper `buildLoggerProvider(Resource)` next to `buildTracerProvider` (Phase 2) and `buildMeterProvider` (Phase 4). The orchestrator chains `.setLoggerProvider(...)` onto the `OpenTelemetrySdk.builder()` call. Resource is built once and shared across all three pipelines (Phase 4 D-05 carryforward).
- Both services register `SdkLoggerProvider` with `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter` to `:4317` (LOG-01). Endpoint reuses the `System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")` + `DEFAULT_OTLP_ENDPOINT` fallback pattern (Phase 2 D-12 / Phase 4 D-04 carryforward). The `opentelemetry-exporter-otlp` artifact already on classpath since Phase 2 ships `OtlpGrpcLogRecordExporter`; **zero** new BOM-managed deps from the SDK side.
- New `@PostConstruct` method on each service's `OtelSdkConfiguration` calling `OpenTelemetryAppender.install(this.openTelemetry)` AFTER the @Bean has returned. Inline comment block explains PITFALL #5 in detail — the silent-no-op behavior is the lesson, not a bug to hide. Method name encoded in code, not magic.
- New `src/main/resources/logback-spring.xml` per service. Byte-identical config (no service-name-driven differences in the file). Defines its OWN `<appender name="CONSOLE" class="ConsoleAppender">` with the bracketed-prefix pattern (full Spring Boot defaults override). Defines `<appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">`. Declares `<turboFilter>` from `opentelemetry-logback-mdc-1.0` so `trace_id` / `span_id` / `trace_flags` are injected into MDC on every log event. Root logger at INFO level with both appenders attached.
- Two new BOM-managed deps in each service's `pom.xml`: `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` (the appender) + `io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0` (the MDC TurboFilter). Both pulled from `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` already imported in parent `pom.xml` at Phase 1.
- Producer-side log statements: ONE in `OrderController.handle(payload)` (entry point, fires inside SERVER span — first log line in every trace, easy demo target) + ONE in `OrderPublisher.publish(orderId, payload)` (just before `RabbitTemplate.convertAndSend`, fires inside PRODUCER span). Both use SLF4J's `org.slf4j.LoggerFactory` (already on classpath via Spring Boot transitively) — no annotation-driven logging, no `@Slf4j`. Exact wording is at planner's discretion (Loki query in SC #2 matches by trace_id regex, not by phrase).
- Consumer-side error log: ONE `LOG.error("...", e)` on the deterministic 10% failure path (Phase 3 APP-04). Lands inside `ProcessingService.process(...)` or in the listener advice's catch block — planner picks. The existing `OrderListener.onOrder` happy-path log (`OrderCreated received: orderId={}`) is **untouched** — it already fires inside the CONSUMER span (Phase 3 advice wraps the listener body), so it gets the propagated trace_id automatically once the appender is installed.
- Console pattern locked: `%d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n`. The `:-` default-value syntax means startup logs render `[trace_id= span_id=]` (brackets stay, values empty) — visually self-distinguishes traced from untraced lines.
- README delta: a Phase-5-specific section keyed to tag `step-05-logs` walking the logger pipeline + the appender install + the Loki click-through-to-Tempo flow. Full README walkthrough body (DOC-01) lands in Phase 7.
- Annotated git tag `step-05-logs` on `main` (WORK-01) — same human-checkpoint convention as Phases 1/2/3/4.

**Out of scope (deferred to later phases):**
- Testcontainers integration tests asserting log exports — Phase 6 (would use `InMemoryLogRecordExporter`)
- Pre-built Grafana dashboard panels for log search + Loki/Tempo data-link configuration screenshots — Phase 7 (DOC-04 / WORK-02)
- Full README walkthrough body — Phase 7 owns DOC-01
- Custom OTel `Logger` API direct usage (the SDK-level handle parallel to `Tracer` / `Meter`) — workshop pattern is SLF4J/Logback through the appender; direct Logger API is out of scope for v1
- Async Logback appender wrapping the OTEL appender — production concern, not a workshop SDK lesson
- Structured logging (Logstash JSON encoder for stdout) — workshop console is human-readable; deferred for production-realism polish
- Log severity-based sampling on the OTel side — out of scope; defaults are fine
- Baggage keys (`%mdc{baggage.*}`) injection alongside trace_id/span_id — Phase 2 D-16 wired baggage propagator but no current phase exercises it; deferred to a hypothetical baggage-focused phase
- Per-package logger overrides (e.g., DEBUG on `com.example`) — not needed for v1; planner keeps root at INFO

</domain>

<decisions>
## Implementation Decisions

### Logger pipeline wiring (both services)

- **D-01:** **Sibling-helper structure preserved (Phase 4 D-01 carryforward; explicitly foreshadowed in current JavaDoc).** Phase 5 adds a third `private SdkLoggerProvider buildLoggerProvider(Resource resource)` method to each service's `OtelSdkConfiguration`, parallel to the existing `buildTracerProvider` / `buildMeterProvider` helpers. The `@Bean openTelemetry()` orchestrator gains one new line — `SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);` — and chains `.setLoggerProvider(loggerProvider)` onto the existing `OpenTelemetrySdk.builder()`. The diff between `step-04-metrics` and `step-05-logs` reads as "we added a third sibling pipeline next to traces and metrics" — the @Bean stays a 4-step recipe (Resource → tracer → meter → logger → SDK).

- **D-02:** **Per-service duplication preserved (Phase 2 D-01 / Phase 4 D-02 carryforward).** Both `producer-service/.../OtelSdkConfiguration.java` and `consumer-service/.../OtelSdkConfiguration.java` get the same `buildLoggerProvider` addition. No extraction to a shared `otel-bootstrap` autoconfiguration. DOC-05's "Why duplicated?" callout already covers the rationale; no new README delta needed for this point.

- **D-03:** **`BatchLogRecordProcessor` defaults (LOG-01).** REQUIREMENTS LOG-01 mandates `BatchLogRecordProcessor` (not Simple). Use `BatchLogRecordProcessor.builder(logRecordExporter).build()` with SDK defaults (5s schedule delay, 2048 max queue, 512 batch, 30s exporter timeout) — same canonical-defaults justification as Phase 2's `BatchSpanProcessor`. Phase 6 will swap to `SimpleLogRecordProcessor` in `@TestConfiguration` for deterministic test assertions.

- **D-04:** **OTLP endpoint pattern reused unchanged (Phase 2 D-12 / Phase 4 D-04 carryforward).** `OtlpGrpcLogRecordExporter.builder().setEndpoint(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") with fallback DEFAULT_OTLP_ENDPOINT)` — same `Optional.ofNullable(...).orElse(...)` shape. No new env var, no autoconfigure dependency. The `opentelemetry-exporter-otlp` artifact already on classpath from Phase 2 ships `OtlpGrpcLogRecordExporter` — single artifact, all three signals (verify with `mvn dependency:tree -Dincludes=io.opentelemetry`).

- **D-05:** **Resource sharing preserved (Phase 4 D-05 carryforward).** Resource construction stays in the @Bean orchestrator and is passed into `buildTracerProvider(resource)`, `buildMeterProvider(resource)`, AND `buildLoggerProvider(resource)`. All three providers carry **identical** `service.name` / `service.namespace` / `service.instance.id` / `deployment.environment.name` attributes — necessary for cross-signal correlation in Grafana (a log_event in Loki, a metric_data_point in Mimir, and a span in Tempo all share one resource identity).

- **D-06:** **`SdkLoggerProvider` lifecycle bound to `OpenTelemetrySdk.close()` cascade (Phase 2 D-15 / Phase 4 D-07 carryforward).** No new `@Bean(destroyMethod=...)` needed — the `OpenTelemetry` bean's `destroyMethod="close"` already cascades to `SdkLoggerProvider.shutdown().join(10s)`, which forces a final flush of the log export queue. Verify at smoke-test that Ctrl-C produces a final log scrape (parallel to Phase 2 SC #2's "last batch flushed" verification).

- **D-07:** **No `Logger` @Bean.** Unlike `Tracer` (Phase 2) and `Meter` (Phase 4), the workshop does NOT expose an OTel SDK `Logger` @Bean. Business code uses **SLF4J** through Logback's appender pipeline — `private static final Logger LOG = LoggerFactory.getLogger(X.class)`. The OTel `Logger` API exists but is intended for log bridges (like the Logback appender itself), not application code. Direct OTel Logger API usage is out of scope for v1. Inline comment in `OtelSdkConfiguration` must explicitly call out: "Note no `Logger` @Bean — application code logs via SLF4J; the OpenTelemetryAppender bridges those events to OTLP. This is the OTel-recommended pattern for application code."

### Appender install (PITFALL #5 mitigation)

- **D-08:** **`@PostConstruct` install location: on `OtelSdkConfiguration` itself (LOG-03).** Each service's `OtelSdkConfiguration` adds:
  ```java
  @PostConstruct
  void installLogbackAppender() {
      OpenTelemetryAppender.install(this.openTelemetry);
  }
  ```
  Holds the `OpenTelemetry` instance via field (set during the @Bean factory body) OR injects it via `@Autowired OpenTelemetry openTelemetry`. Planner picks the injection shape. The method MUST run AFTER the @Bean returns (Spring's @PostConstruct lifecycle guarantees this). Foreshadowed in Phase 4 D-18's commentary on the gauge `@PostConstruct` pattern.

- **D-09:** **Comment block on the `@PostConstruct` method is load-bearing — PITFALL #5 lives here.** Inline comment must explicitly document:
  - Logback initializes BEFORE the SDK is built (Spring Boot starts logging immediately).
  - The `OpenTelemetryAppender` instance is constructed by Logback at startup with `OpenTelemetry.noop()` as default.
  - `install(openTelemetry)` swaps the noop reference for the real SDK — log records emitted AFTER install land at the OTLP endpoint; log records emitted BEFORE are silently dropped from OTLP (still appear on console).
  - This is a documented OTel quirk (link to GH issue #10307 in the comment).
  - The `@PostConstruct` runs after the @Bean returns, guaranteeing order-of-operations.
  Comment density bar applies (≥40 comment lines per `OtelSdkConfiguration.java` from Phase 2 DOC-03 — Phase 5's additions naturally push past it).

### Logback configuration (per-service)

- **D-10:** **Per-service `logback-spring.xml`, full Spring Boot defaults override.** Each service gets `src/main/resources/logback-spring.xml` (the `-spring` suffix activates Spring Boot's profile-aware loader). Files are byte-identical between services — no service-name-driven differences in the logback file itself (service.name comes from the OTel Resource, not from Logback). NO `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` — the file defines its own CONSOLE appender from scratch. Pedagogical justification: file is source of truth, no inherited magic; consistent with Phase 2 D-12 "no autoconfigure" theme.

- **D-11:** **Console pattern locked.** Final pattern string:
  ```
  %d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n
  ```
  - `%d{HH:mm:ss.SSS}` — time-only (workshop runs in one calendar day; matches Spring Boot default).
  - `[%thread]` — load-bearing teaching element. Producer logs run on `[http-nio-8080-exec-N]`; consumer logs run on `[org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1]` (or similar). Thread names make the async hand-off across the AMQP boundary visible.
  - `%-5level` — left-justified 5-char level (`INFO ` / `ERROR` / `WARN `).
  - `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]` — bracketed prefix, `:-` default-value syntax for empty-when-absent rendering. Combined with hide-when-absent, startup logs render `[trace_id= span_id=]` (brackets stay, values empty); in-span logs render `[trace_id=4b2e... span_id=ad12...]`. Visually self-distinguishes traced from untraced lines.
  - `%logger{36}` — abbreviated FQCN to 36 chars (`com.example.producer.api.OrderController` → `c.e.p.api.OrderController`).
  - `- %msg%n` — message and newline.

- **D-12:** **Both CONSOLE and OTEL appenders attached to root.** `<root level="INFO">` has two `<appender-ref>` entries: `CONSOLE` (terminal output via the pattern in D-11) and `OTEL` (OTLP export). Different responsibilities — console for live demo readability, OTEL for backend correlation. Root level INFO captures all business logs without per-package overrides.

- **D-13:** **MDC injection via `opentelemetry-logback-mdc-1.0` TurboFilter.** Logback config declares:
  ```xml
  <turboFilter class="io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppenderMdcInjector"/>
  ```
  (Exact class FQCN to be confirmed by `/gsd-research-phase 5` — STATE.md flagged this for research.) The TurboFilter runs on every log event before pattern layout, reads `Span.current()` from the active OTel `Context`, and pushes `trace_id`, `span_id`, `trace_flags` into the SLF4J MDC. After it runs, `%mdc{trace_id}` in the pattern resolves correctly. The filter is configured ONCE per logback-spring.xml (it covers all appenders; not appender-specific).

- **D-14:** **Appender wiring: explicit OpenTelemetryAppender class.** Logback config declares:
  ```xml
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>
  ```
  No `<captureMdcAttributes>` config (D-13's TurboFilter handles MDC injection BEFORE the appender reads the event; the appender's own MDC-capture flag is unused for v1). The appender's body is empty — all behavior comes from Logback delegating events to `OpenTelemetryAppender.append(...)`, which forwards to the `OpenTelemetry` instance set by `install(...)` (D-08).

### Producer demo log statements

- **D-15:** **Two producer business-logic log lines (NEW).** Adds:
  - `OrderController.handle(payload)`: `LOG.info(...)` near method entry, inside the SERVER span (HttpServerSpanFilter wraps every non-/actuator request — Phase 2). Wording at planner's discretion; suggested shape: `LOG.info("received POST /orders payload={}", payload)`.
  - `OrderPublisher.publish(orderId, payload)`: `LOG.info(...)` just before the `RabbitTemplate.convertAndSend(...)` call, inside the PRODUCER span (Phase 3 TracingMessagePostProcessor wraps the publish). Wording at planner's discretion; suggested shape: `LOG.info("publishing orderId={} to exchange={}", orderId, exchange)`.

  Both use SLF4J: `private static final Logger LOG = LoggerFactory.getLogger(<Class>.class)`. No `@Slf4j` annotation (consistent with the existing OrderListener pattern).

- **D-16:** **Consumer error log on APP-04 failure path (NEW).** Adds `LOG.error("order processing failed: orderId={}", orderId, e)` (or similar) on the deterministic 10% failure path. Two acceptable hosting locations — planner picks one:
  - **(a) Inside `ProcessingService.process(...)`** — log before re-throwing (or in a catch-and-rethrow). Closest to the failure source. Fires inside the INTERNAL span (still within Phase 3's catch shape). Trace_id stamping is automatic via the active CONSUMER → INTERNAL span chain.
  - **(b) Inside the listener advice's catch in `otel-bootstrap`** — generic catch-all logging in the propagation layer. Reuses the advice's existing exception-handling code path. But it puts the log in shared infrastructure rather than business code — pedagogically less direct.

  Recommendation: option (a) for clarity (the log is at the failure source, not the framework). The error log's trace_id matches the producer's trace_id (Phase 3 propagation makes the failure trace cross-service), so the Loki query `{service_name="order-consumer"} |~ "ERROR"` on the failed order returns log lines whose trace_id resolves to a Tempo trace with the recordException event already attached on the CONSUMER span (Phase 3 TRACE-09).

- **D-17:** **Existing consumer happy-path log (`OrderListener.onOrder`) is UNTOUCHED.** Phase 1 shipped `LOG.info("OrderCreated received: orderId={}", orderId)` and Phase 3's listener advice wraps the listener body with the CONSUMER span. The log is automatically stamped with the propagated trace_id once the appender is installed (D-08). No code change, no wording change.

- **D-18:** **Loki query in README is trace_id-driven, not phrase-driven (LOG-05).** SC #2 query: `{service_name="order-producer"} |~ "<traceId>"`. The `|~` regex match against `<traceId>` matches against ANY field containing the trace_id — primarily the `trace_id` log-record attribute that the OpenTelemetryAppender automatically populates from `Span.current()`. The console-pattern's `[trace_id=...]` text is incidental; the query works on the OTLP attribute, not the formatted message. This means log-line wording can vary across services without breaking the query.

### Documentation, tag, comment density (carryforward)

- **D-19:** **DOC-03 comment density bar applies to Phase 5's additions.** Phase 2 set the bar at ≥40 comment lines per `OtelSdkConfiguration.java`; Phase 4 naturally pushed past it with the meter helper. Phase 5's additions (logger helper body + `@PostConstruct` install method + PITFALL #5 comment block + the no-`Logger`-@Bean explanation) push the bar to ~80+ per service file. Plan must include a verification step.

- **D-20:** **README delta is small and Phase-5-specific.** Add a "Step 5: Logs Correlation" section keyed to tag `step-05-logs` that:
  - Names the third pipeline (logger), the appender, and the MDC TurboFilter.
  - Calls out PITFALL #5 (the silent-no-op trap) — the `@PostConstruct` install order is the lesson.
  - Shows the Loki query for happy-path traces and the error-trace correlation.
  - Notes the three-signals close: log_event in Loki → click trace_id → Tempo trace with recordException → metric_data_point in Mimir for the same priority/method.

  Full README walkthrough body (DOC-01) and Grafana data-link configuration screenshots (DOC-04) land in Phase 7.

- **D-21:** **Annotated git tag `step-05-logs` is the exit gate (WORK-01 carryforward).** Same human-checkpoint pattern as Phases 1/2/3/4: source merged + all 4 ROADMAP success criteria verified live, then user gate approves the orchestrator-applied tag. STATE.md / ROADMAP.md `[x]` flips land atomically with the tag-apply commit.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap

- `.planning/REQUIREMENTS.md` — LOG-01..05 (locked: BatchLogRecordProcessor, OpenTelemetryAppender + MDC injector, @PostConstruct install, console pattern with %X{trace_id} %X{span_id}, Loki click-through to Tempo) + WORK-01 (annotated tag exit gate)
- `.planning/ROADMAP.md` — Phase 5 details, all 4 success criteria, "Plans: TBD" (this CONTEXT.md seeds plan creation)
- `.planning/PROJECT.md` — overall constraint set (Spring Boot 3.4.13, Java 17, no Java agent, no Micrometer bridge, no autoconfigure starter, single OTLP endpoint, otel-lgtm backend)
- `.planning/STATE.md` — flags Phase 5 research item: "Confirm Maven coordinate for MDC injector (`opentelemetry-logback-mdc-1.0` artifact vs `<captureMdcAttributes>` on appender)" — this CONTEXT.md picks the artifact path; researcher confirms the exact 2.27.0-alpha coordinate + class FQCN.

### Research artifacts (paste-able from STACK.md)

- `.planning/research/SUMMARY.md` §"Phase 5: Logs Correlation" — `SdkLoggerProvider` + `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter` + `opentelemetry-logback-appender-1.0` (instrumentation-bom-alpha)
- `.planning/research/STACK.md` — `opentelemetry-logback-appender-1.0` is the ONLY artifact pulled from instrumentation-bom-alpha (Phase 5 adds `opentelemetry-logback-mdc-1.0` from the same BOM); both are BOM-managed under `2.27.0-alpha`
- `.planning/research/PITFALLS.md` §Pitfall #5 — CRITICAL for the logs lesson: `OpenTelemetryAppender` initialized before SDK is ready → silent log drop. Mitigation = `OpenTelemetryAppender.install(openTelemetry)` in `@PostConstruct` after the SDK bean is built. PITFALL #5 informs the comment block in D-09. Cites GH issue [open-telemetry/opentelemetry-java-instrumentation#10307](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307).

### Carryforward decisions from prior phases

- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` — Phase 2 D-01 (per-service duplication / "boilerplate IS the lesson"), D-12 (no-autoconfigure / `System.getenv` with fallback), D-15 (`@Bean(destroyMethod="close")` lifecycle cascade), D-16 (propagators wired but unexercised — baggage MDC injection deferred from Phase 5)
- `.planning/phases/04-metrics/04-CONTEXT.md` — Phase 4 D-01 (sibling-helper structure — Phase 5's `buildLoggerProvider(Resource)` lands as third helper next to traces and metrics), D-02 (per-service duplication), D-04 (OTLP endpoint pattern), D-05 (Resource shared across helpers), D-07 (lifecycle cascade — no new destroyMethod), D-18 (the `@PostConstruct` pattern for the gauge — explicitly foreshadows Phase 5's appender install)
- `.planning/phases/03-amqp-context-propagation/03-CONTEXT.md` — Phase 3 D-08 (TracingMessageListenerAdvice catch shape — error log in D-16 lives downstream of this catch chain on the consumer), Phase 3 APP-04 + TRACE-09 (deterministic 10% failure with recordException — Phase 5's D-16 LOG.error correlates to this error span)
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` D-11 + Phase 1 — `OrderListener.onOrder` already has `LOG.info("OrderCreated received: orderId={}")` (consumer-side happy path); Phase 5 D-17 confirms this stays untouched and gets trace_id stamping automatically once the appender is installed

### Files Phase 5 modifies (read first to plan diffs)

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — refactor target for D-01 helper extension; add `buildLoggerProvider`, add `@PostConstruct installLogbackAppender()`, add no-Logger-@Bean comment (D-07). Existing JavaDoc at lines 50-67 already foreshadows this exact change.
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — same refactor in mirror form (per D-02)
- `producer-service/src/main/java/com/example/producer/api/OrderController.java` — add `private static final Logger LOG = ...` + `LOG.info(...)` near method entry (D-15)
- `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` — add `private static final Logger LOG = ...` + `LOG.info(...)` just before `RabbitTemplate.convertAndSend(...)` (D-15)
- `consumer-service/src/main/java/com/example/consumer/processing/ProcessingService.java` (or equivalent) — add `LOG.error("order processing failed: orderId={}", orderId, e)` on APP-04 failure path (D-16). Planner picks exact location.
- `producer-service/src/main/resources/logback-spring.xml` — NEW file with full override of Spring Boot defaults (D-10..D-14)
- `consumer-service/src/main/resources/logback-spring.xml` — NEW file (byte-identical to producer's)
- `producer-service/pom.xml` — add `opentelemetry-logback-appender-1.0` + `opentelemetry-logback-mdc-1.0` (BOM-managed, no `<version>`)
- `consumer-service/pom.xml` — same two deps
- `README.md` — add "Step 5: Logs Correlation" section (D-20)
- `mise.toml` — no changes expected; demo curl payloads from Phase 4 still produce traces, metrics, AND now logs from one HTTP call

### OTel API surface (paste-able from STACK.md per SUMMARY.md, with research-flagged exception)

- OTel SDK 1.61.0 BOM-managed: `io.opentelemetry:opentelemetry-sdk-logs` (transitively pulled by `opentelemetry-sdk`; no new pom dep), `io.opentelemetry.exporter:opentelemetry-exporter-otlp` (single artifact for all three signals — already on classpath from Phase 2)
- OTel Instrumentation 2.27.0-alpha BOM-managed: `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` (the OpenTelemetryAppender class), `io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0` (the MDC TurboFilter — coordinate confirmed by `/gsd-research-phase 5`)
- semconv 1.40.0 stable: no new attribute keys needed by Phase 5 — log records inherit `service.name` etc. from the SDK Resource (D-05)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`OtelSdkConfiguration.openTelemetry()` @Bean (both services)** — current orchestrator at lines 97-160 of producer's `OtelSdkConfiguration.java`. Builds Resource → tracer → meter → SDK builder. Phase 5 adds one new local: `SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);` and one chained builder call: `.setLoggerProvider(loggerProvider)`. The existing JavaDoc (lines 50-67) already names "Phase 5 will reuse the same pattern when wiring the logger provider", "Phase 5 will land buildLoggerProvider(resource) the same way", "Phase 5 will add a sibling buildLoggerProvider helper".
- **`DEFAULT_OTLP_ENDPOINT` constant** — `private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317"` is reused unchanged for `OtlpGrpcLogRecordExporter`.
- **`opentelemetry-exporter-otlp` artifact** — already on classpath from Phase 2; ships `OtlpGrpcLogRecordExporter` alongside the existing span and metric exporters. Phase 5 adds **zero** new SDK-side deps; only adds the two instrumentation-bom-alpha appender artifacts.
- **`OrderListener.onOrder` log** — `consumer-service/.../messaging/OrderListener.java:51` already has `LOG.info("OrderCreated received: orderId={}", orderId)` (Phase 1). Wraps inside CONSUMER span via Phase 3 advice. Untouched by Phase 5; gets trace_id stamping automatically once the appender is installed.
- **Phase 3 `recordException` on CONSUMER span (TRACE-09)** — APP-04's deterministic failure already records exception events on the trace. Phase 5 D-16 adds the LOG.error counterpart in Loki — same trace_id stamps both signals. Triple-signal correlation is achieved without modifying any Phase 3 code.
- **Phase 4's `@PostConstruct` precedent (Plan 04-04 / D-18)** — the consumer-side gauge registration uses `@PostConstruct` in `OtelSdkConfiguration` to attach the callback after the Meter bean is built. Phase 5's appender install reuses this exact lifecycle hook for the same reason (the SDK must exist first). The two `@PostConstruct` methods will sit in adjacent positions in the file.

### Established Patterns

- **Per-service duplication, never extracted** — `OtelSdkConfiguration.java` lives twice with two-line diffs (Phase 2 D-01 / DOC-05). Phase 5's logger helper additions duplicate symmetrically; logback-spring.xml is byte-identical between services.
- **No autoconfigure, no magic** — Phase 2 D-12: `System.getenv` with `Optional.ofNullable(...).orElse(...)` fallback; same shape for the log exporter (D-04).
- **`@Bean(destroyMethod="close")` cascade** — Phase 2 D-15: closing `OpenTelemetrySdk` shuts down all child providers. Adding `SdkLoggerProvider` requires no new lifecycle bean (D-06).
- **Heavy comment density** — Phase 2 DOC-03 + Phase 4 D-20: ≥40 comment lines per `OtelSdkConfiguration.java`. Phase 5's helper + @PostConstruct + PITFALL #5 block naturally exceed this; bar holds.
- **Annotated tag at exit** — Phase 1/2/3/4 pattern: source ships, all SC verified live, then human-gate tag-apply with atomic STATE/ROADMAP flip (D-21 / WORK-01).
- **SLF4J in business code** — Phase 1 set the precedent in `OrderListener` (`org.slf4j.LoggerFactory.getLogger(...)`); Phase 5's new logs (D-15, D-16) reuse the same import + field declaration shape.

### Integration Points

- **`OpenTelemetrySdk.builder().setLoggerProvider(...)`** — single new builder line in the @Bean orchestrator after the existing setMeterProvider call.
- **`@PostConstruct installLogbackAppender()`** — new method on `OtelSdkConfiguration`. Spring's lifecycle guarantees it runs after the @Bean factory body returns; the `OpenTelemetry` instance is fully built at that point. Holds the bean via field set in `openTelemetry()` body, OR via @Autowired.
- **Logback's TurboFilter pipeline** — runs on every log event before pattern layout. The MDC injector populates trace_id/span_id BEFORE the encoder reads `%mdc{trace_id}`. Order is automatic; no Logback-config-level ordering concerns.
- **`OrderController` / `OrderPublisher` constructors** — D-15's log statements use static `LOG` fields, so no constructor signature changes.
- **`ProcessingService` (or listener advice)** — D-16's error log lands in one of these. Planner picks; no constructor signature changes either way (Logger is static).
- **README** — small Phase-5 section (D-20); full walkthrough is Phase 7.

</code_context>

<specifics>
## Specific Ideas

- The `+1` git diff between `step-04-metrics` and `step-05-logs` should read as "we added a third sibling pipeline + a logback config + an install hook + 3 log lines" — D-01's helper-extension is chosen partly so the structural symmetry across all three signals is the artifact's takeaway shape.
- The `@PostConstruct` install method is the closest thing this workshop has to a "gotcha lesson" — its existence and ordering is the WHOLE Phase 5 SDK-vs-Logback story compressed into 5-6 lines. The PITFALL #5 comment block is mandatory.
- Console pattern's `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]` shape is workshop-projector-friendly — left-aligned brackets are visually predictable; the `%logger{36}` abbreviation keeps the line under ~120 chars on a typical projector.
- Triple-signal correlation on the failure path is the Phase 5 highlight: `LOG.error` (Loki) → trace_id click → trace in Tempo → recordException event on the CONSUMER span (Phase 3 TRACE-09) → metric_data_point in Mimir for the same orderId's order.priority. Workshop should walk through this in order.
- The `:-` empty-default in `%mdc{trace_id:-}` is itself a subtle teaching moment — Logback's syntax for "default value when MDC key absent". Worth ONE comment line in `logback-spring.xml`.

</specifics>

<deferred>
## Deferred Ideas

- **Custom OTel `Logger` API direct usage** — workshop pattern is SLF4J via the appender; a `Logger` @Bean parallel to Tracer/Meter is out of scope for v1. Could be a Phase 7 polish callout if attendees ask "how do I emit a log record without going through SLF4J?"
- **Async Logback appender wrapping OTEL appender** — production concern (off-thread log shipping); not a workshop SDK lesson. Defer to a hypothetical production-realism phase.
- **Structured logging (Logstash JSON encoder for stdout)** — production-realistic but loses live-demo readability on a workshop projector. Could be a Phase 7 polish or a v2 alt-pattern.
- **Baggage keys in MDC (`%mdc{baggage.userId}` etc.)** — Phase 2 D-16 wired the W3C baggage propagator but no current phase exercises it. A baggage-focused phase could surface baggage in logs as a teaching extension. Currently no phase scoped for this.
- **Log severity-based sampling on the OTel side** — out of scope; defaults are fine for the workshop volume.
- **Per-package logger overrides (DEBUG on com.example)** — not needed for v1; root INFO captures all business logs cleanly. Could become a Phase 7 polish if attendees want noisier output during the walkthrough.
- **InMemoryLogRecordExporter assertions in Testcontainers tests** — Phase 6's job; Phase 5 ships only the production logger pipeline.
- **Pre-built Grafana data-link configuration screenshots + Loki/Tempo correlation panels** — Phase 7 (DOC-04 / WORK-02).

</deferred>

---

*Phase: 5-logs-correlation*
*Context gathered: 2026-05-01*
