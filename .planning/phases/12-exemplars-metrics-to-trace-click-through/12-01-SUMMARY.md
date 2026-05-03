---
phase: 12-exemplars-metrics-to-trace-click-through
plan: "01"
subsystem: infra
tags: [opentelemetry, exemplars, histogram, sdk, java, spring-boot]

requires:
  - phase: 04-metrics
    provides: SdkMeterProvider + HttpServerSpanFilter histogram instrumentation
  - phase: 10-prerequisites-stack-decomposition
    provides: decomposed observability stack ready for exemplar click-through

provides:
  - ExemplarFilter.traceBased() active on SdkMeterProvider in both services (EXMP-01)
  - Scope closed after requestDuration.record() in HttpServerSpanFilter (D-E1 / F3-1 mitigation)

affects:
  - 12-exemplars-metrics-to-trace-click-through
  - any future phase touching SdkMeterProvider or HttpServerSpanFilter

tech-stack:
  added: []
  patterns:
    - "ExemplarFilter.traceBased() inserted between .setResource() and .registerMetricReader() in buildMeterProvider()"
    - "Manual Scope management (Scope scope = ...; try { ... } finally { record(); span.end(); scope.close(); }) so ExemplarFilter sees active span at record() time"

key-files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
    - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java

key-decisions:
  - "ExemplarFilter.traceBased() is the correct disposition — it attaches trace_id + span_id to histogram data points only when a sampled span is active; NEVER attaches business attributes (T-12-01 accepted)"
  - "scope.close() must come AFTER span.end() AND AFTER record() in finally block — any other ordering means ExemplarFilter.traceBased() sees a closed/invalid span context at record() time (D-E1)"

patterns-established:
  - "Phase N — REQ-ID: reason inline comment convention applied to setExemplarFilter() line"
  - "Manual scope management pattern for histograms that need exemplar attachment"

requirements-completed:
  - EXMP-01

duration: 1min
completed: "2026-05-03"
---

# Phase 12 Plan 01: Exemplar SDK Layer Summary

**ExemplarFilter.traceBased() activated on both SdkMeterProvider builders and HttpServerSpanFilter scope restructured to manual try-finally so ExemplarFilter sees a valid active span at histogram record() time**

## Performance

- **Duration:** 1 min
- **Started:** 2026-05-03T20:18:05Z
- **Completed:** 2026-05-03T20:19:13Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Added `import io.opentelemetry.sdk.metrics.ExemplarFilter;` and `.setExemplarFilter(ExemplarFilter.traceBased())` to both producer-service and consumer-service `OtelSdkConfiguration.buildMeterProvider()` methods (EXMP-01 / D-E11)
- Restructured `HttpServerSpanFilter.doFilterInternal()` from `try (Scope scope = span.makeCurrent())` (try-with-resources) to manual scope management with `scope.close()` as the last statement in the finally block, after `requestDuration.record()` and `span.end()` (D-E1 / F3-1 mitigation)
- Both services compile cleanly with `mvn -B -pl producer-service,consumer-service compile -q`

## Task Commits

Each task was committed atomically:

1. **Task 1: Add ExemplarFilter to both OtelSdkConfiguration.java files** - `5dbad35` (feat)
2. **Task 2: Restructure HttpServerSpanFilter to manual scope management (D-E1)** - `355b278` (feat)

**Plan metadata:** _(docs commit — to follow)_

## Files Created/Modified

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` - Added `ExemplarFilter` import + `.setExemplarFilter(ExemplarFilter.traceBased())` in `buildMeterProvider()`
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` - Same changes mirrored (TRACE-01/DOC-05 mirror pattern)
- `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` - Replaced try-with-resources scope with manual scope + `scope.close()` as last finally statement

## Decisions Made

- `ExemplarFilter.traceBased()` placed between `.setResource(resource)` and `.registerMetricReader(metricReader)` in the builder chain — this is the correct and only valid insertion point; inserting after `registerMetricReader` would also compile but is conventionally wrong (filter configures sampling behavior, reader configures export)
- `scope.close()` placed as the absolute last line in the finally block — the order `record() → span.end() → scope.close()` is load-bearing; reversing any pair breaks ExemplarFilter's view of the active span context

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required. Infrastructure changes are SDK-internal only.

## Known Stubs

None — no placeholder values or wired-but-empty paths introduced.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced. T-12-01 and T-12-02 accepted per plan threat model (ExemplarFilter only attaches OTel-internal correlation IDs, not business attributes).

## Next Phase Readiness

- SDK exemplar layer (Layer 1 of 3) is now active in both services
- Plan 12-02 can proceed: Prometheus scrape config + exemplar-enabling Prometheus flags (Layer 2)
- Plan 12-03 can proceed: Grafana panel + Tempo datasource link (Layer 3, click-through UI)
- No blockers

## Self-Check: PASSED

- FOUND: producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
- FOUND: consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
- FOUND: producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
- FOUND: .planning/phases/12-exemplars-metrics-to-trace-click-through/12-01-SUMMARY.md
- FOUND: commit 5dbad35
- FOUND: commit 355b278

---
*Phase: 12-exemplars-metrics-to-trace-click-through*
*Completed: 2026-05-03*
