# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-29)

**Core value:** A workshop attendee can clone the repo, run `docker compose up` + `mise run dev`, hit `POST /orders`, and see a single distributed trace flow from the HTTP handler through the RabbitMQ publish, into the consumer's processing logic, with correlated metrics and logs — and understand exactly which lines of SDK code made each piece work.
**Current focus:** Phase 1 SHIPPED 2026-04-29 (tag `step-01-baseline`); Phase 2 — Manual SDK Bootstrap & First Traces is next

## Current Position

Phase: 1 of 7 (Baseline & Scaffold) — **SHIPPED**
Plan: 6 of 6 complete
Status: Phase 1 shipped. Annotated tag `step-01-baseline` on commit `6aa3a92`. Local only — push pending user decision (`git push origin step-01-baseline`).
Last activity: 2026-04-29 — Tag applied + reproducibility self-test passed (clone → mise install → verify:bom → mvn install all green from /tmp/verify-baseline). Phase 2 (Manual SDK Bootstrap & First Traces) is unblocked.

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: —
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| — | — | — | — |

**Recent Trend:**
- Last 5 plans: —
- Trend: — (no data yet)

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Initialization: SDK bootstrap (`OtelSdkConfiguration`) is **inlined per-service**, not extracted to a shared library — duplication is the lesson (TRACE-01, DOC-05).
- Initialization: AMQP propagation pair (`TracingMessagePostProcessor` + `TracingMessageListenerAdvice`) lives in a **shared** `otel-bootstrap` Maven module — symmetry of one inject method matched by one extract method IS the lesson (PROP-04).
- Initialization: Maven multi-module build with parent + 3 children (`otel-bootstrap`, `producer-service`, `consumer-service`).
- Initialization: Workshop checkpoints are **annotated git tags on `main`** (immutable, frozen after first delivery), not long-lived branches (WORK-01).
- Initialization: Phase 7 (Polish & Differentiators) is locked into v1 (not deferred post-cohort) per user choice — dashboard, load script, screenshots, full README walkthrough.

### Pending Todos

None yet.

### Blockers/Concerns

- **Phase 1 research flag**: RESOLVED 2026-04-29 — RESEARCH.md (commit `d3bbf32`) confirms OTel BOM precedes Spring Boot BOM; mise pin is `corretto-17.0.13.11.1` (exact patch, not floating).
- **Phase 3 research flag**: Resolve listener-side extraction mechanism (`MethodInterceptor` advice on `SimpleRabbitListenerContainerFactory` vs inline extract in `@RabbitListener`) — needs `/gsd-research-phase` before planning.
- **Phase 5 research flag**: Confirm Maven coordinate for MDC injector (`opentelemetry-logback-mdc-1.0` artifact vs `<captureMdcAttributes>` on appender).
- **Phase 6 research flag**: Validate `@ServiceConnection` + `RabbitMQContainer` on Spring Boot 3.4.13.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-04-29 (Phase 1 shipped — all 3 waves merged, runtime verified, tag applied)
Stopped at: Phase 1 complete. Tag local-only (push deferred per GSD safety protocol). Next session begins Phase 2 — Manual SDK Bootstrap & First Traces (depends on Phase 1; the broken-then-fixed delta with Phase 3 is load-bearing).
Resume file: None
