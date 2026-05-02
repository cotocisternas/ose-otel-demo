# Phase 5: Logs Correlation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 5-logs-correlation
**Areas discussed:** MDC injection mechanism, Logback file structure, Console pattern shape, Producer demo log strategy

---

## MDC injection mechanism

### Q1: Which MDC injection approach fits the workshop's teaching style best?

| Option | Description | Selected |
|--------|-------------|----------|
| Separate official artifact | Pull `io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0` from instrumentation-bom-alpha (already imported by appender). One `<turboFilter>` line in logback-spring.xml. Pedagogical: 'use OTel project's official tools'. | ✓ |
| Custom TurboFilter (15-line class) | Hand-roll a `LogbackOtelMdcTurboFilter` reading `Span.current()` and `MDC.put(...)`. Most code, most 'show how it works' value. Adds a third reading file per service at step-05. | |
| Appender capture only | Skip MDC injection. Use `<captureMdcAttributes>` on the appender. Console pattern's `%X{trace_id}` stays empty — insufficient for SC #1 unless dropped. | |

**User's choice:** Separate official artifact (Recommended)
**Notes:** Researcher will confirm exact 2.27.0-alpha coordinate + class FQCN of the MDC TurboFilter (this was already a STATE.md research flag). Adds ONE pom dep + ONE `<turboFilter>` line per service. Aligns with the workshop tone: same instrumentation-bom-alpha already imported for the appender.

### Q2: When no span is active (startup, background threads), what should the console line look like?

| Option | Description | Selected |
|--------|-------------|----------|
| Hide trace fields when absent | Use `%mdc{trace_id:-}` default-value syntax. Startup logs read clean; in-span logs have populated brackets. Cleanest visual. | ✓ |
| Always show empty markers | Pattern always emits `trace_id= span_id=` even when MDC empty. Visually consistent every line. Makes "before-install no-trace" lesson visible. | |
| Always show with placeholder | Use `%mdc{trace_id:-N/A}` so absent renders `trace_id=N/A`. Strong learning cue. Slightly noisy. | |

**User's choice:** Hide trace fields when absent (Recommended)
**Notes:** Combined with the bracketed-prefix pattern (chosen later in Area 3), startup lines will render `[trace_id= span_id=]` — brackets stay, values empty. Brackets disappearing entirely would require `%replace` conditional layout — not worth the complexity.

---

## Logback file structure

### Q1: Where should logback-spring.xml and the MDC dependency live?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-service duplication | Each service gets its own complete `logback-spring.xml`. MDC + appender deps in EACH service's pom. Mirrors DOC-05 + Phase 4 D-02. Workshop reads appender setup TWICE. | ✓ |
| Shared base in otel-bootstrap + includes | otel-bootstrap carries `otel-logback-base.xml`. Each service includes via `<include resource="...">`. MDC + appender deps in otel-bootstrap. Less duplication; breaks duplication-as-pedagogy theme. | |

**User's choice:** Per-service duplication (Recommended)
**Notes:** The "boilerplate IS the lesson" theme from DOC-05 (per-service OtelSdkConfiguration) extends to the logback config. Two files to maintain (~40 lines each), but they're byte-identical between services.

### Q2: How should logback-spring.xml relate to Spring Boot's default console appender?

| Option | Description | Selected |
|--------|-------------|----------|
| Full override | Define our OWN CONSOLE appender + OTEL appender + MDC filter + root from scratch. Spring Boot's default pattern is REPLACED. ~40 lines per file. | ✓ |
| Inherit Spring Boot defaults | `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` + console-appender include. ADD only OTEL + MDC filter + override pattern via property. ~15 lines per file but adds inherited magic. | |

**User's choice:** Full override (Recommended)
**Notes:** The file is source of truth. No inherited Spring Boot magic. Consistent with Phase 2 D-12 "no autoconfigure" theme. Pedagogically transparent — attendees read 40 explicit lines and know exactly what the appender pipeline looks like.

---

## Console pattern shape

### Q1: Which console pattern shape fits the live workshop demo best?

| Option | Description | Selected |
|--------|-------------|----------|
| Bracketed prefix | Trace fields appear in brackets BEFORE the message. Easy to scan during live demo. | ✓ |
| Trailing trace fields | Trace fields appear AFTER the message at end of line. Message stays at stable left position. Trace_id far right, harder to scan. | |
| Compact bracketed | Short labels: `[tid:abc... sid:def...]`. Saves horizontal space; slightly cryptic. | |

**User's choice:** Bracketed prefix (Recommended)
**Notes:** Final pattern: `%d{HH:mm:ss.SSS} [%thread] %-5level [trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}] %logger{36} - %msg%n`. The `[%thread]` element is a teaching moment — async hand-off across the AMQP boundary becomes visible in the console.

### Q2: What timestamp format for the console line?

| Option | Description | Selected |
|--------|-------------|----------|
| Time-only HH:mm:ss.SSS | `12:34:56.789`. Short, fits projector, matches Spring Boot default. | ✓ |
| Full date+time yyyy-MM-dd HH:mm:ss.SSS | `2026-05-01 12:34:56.789`. Adds 11 chars per line. Production-realistic. | |
| ISO-8601 yyyy-MM-dd'T'HH:mm:ss.SSS | `2026-05-01T12:34:56.789`. Standard for log aggregation, but workshop console is human-readable. | |

**User's choice:** Time-only HH:mm:ss.SSS (Recommended)
**Notes:** Workshop runs in one calendar day, date is implicit. Most OTel tutorials use this shape.

---

## Producer demo log strategy

### Q1: How many producer log statements should Phase 5 add, and where?

| Option | Description | Selected |
|--------|-------------|----------|
| Two logs at different layers | ONE in OrderController.handle (SERVER span entry) + ONE in OrderPublisher.publish (PRODUCER span). Same trace_id stamps TWO lines at DIFFERENT layers. Combined with consumer's existing log = 3 lines per order. | ✓ |
| One log at the controller | ONE in OrderController.handle only. Smallest delta. Single-line correlation; weaker demo. | |
| Three logs across the chain | Logs at controller, OrderService, OrderPublisher. Strongest demo but feels like over-instrumentation. | |

**User's choice:** Two logs at different layers (Recommended)
**Notes:** Pedagogical surface: attendees read three lines of code (one per file) to understand "just call SLF4J anywhere inside a span". Both producer logs use SLF4J's static `LOG` field pattern (same as existing OrderListener). Wording at planner's discretion — Loki query is trace_id-driven, not phrase-driven.

### Q2: Should Phase 5 also add an ERROR log on the consumer's deterministic failure path (APP-04)?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, add LOG.error | Add `LOG.error(...)` on Phase 3's APP-04 failure path. Triple-signal correlation: log → trace → exception event. | ✓ |
| No, happy-path only | Skip the error log. Existing TRACE-09 recordException already gives the error story in Tempo. Smaller blast radius. | |
| Add LOG.warn on success paths instead | Production-realistic warn logs (e.g., on missing payload.priority). Loses the strong error-correlation demo. | |

**User's choice:** Yes, add LOG.error (Recommended)
**Notes:** Strongest workshop moment — attendees query Loki for ERRORs, click trace_id, land on the error trace in Tempo with the recordException event already attached on the CONSUMER span (Phase 3 TRACE-09). All three signals correlate on one orderId. Planner picks hosting location: ProcessingService (closer to failure source) or listener advice's catch (framework-level).

---

## Claude's Discretion

User did not explicitly use "you decide" language at any decision point. The following implementation details were left to the planner per the user's choices:

- **D-08 injection shape** — whether `OpenTelemetry` is held via field set during the @Bean factory body or via `@Autowired` for the `@PostConstruct` install method. Planner picks.
- **D-15 log line wording** — exact text of the controller and publisher log statements. SC #2 query is trace_id-driven, so wording is flexible.
- **D-16 error log hosting location** — `ProcessingService.process(...)` (recommended in CONTEXT.md) or the listener advice's catch in `otel-bootstrap`. Planner picks.

## Deferred Ideas

Items that came up implicitly during the discussion but belong outside Phase 5:

- Custom OTel `Logger` API direct usage as a `Logger` @Bean parallel to Tracer/Meter — workshop uses SLF4J only.
- Async Logback appender wrapping OTEL appender — production concern, not a workshop SDK lesson.
- Structured logging (Logstash JSON encoder for stdout) — loses live-demo readability.
- Baggage keys in MDC (`%mdc{baggage.userId}`) — Phase 2 D-16 wired baggage propagator but no current phase exercises it.
- Per-package logger overrides (e.g., DEBUG on `com.example`) — root INFO is sufficient for v1.
- InMemoryLogRecordExporter assertions in Testcontainers tests — Phase 6's job.
- Pre-built Grafana data-link configuration screenshots — Phase 7 (DOC-04).
- Conditional `%replace` layout to make brackets fully disappear when no trace is active — workshop doesn't need this complexity.
