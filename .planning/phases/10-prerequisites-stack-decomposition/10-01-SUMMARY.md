---
phase: 10-prerequisites-stack-decomposition
plan: 01
subsystem: infra
tags: [spring-boot, opentelemetry, circular-reference, bean-lifecycle, java]

# Dependency graph
requires:
  - phase: 05-logs-correlation
    provides: "LOG-03 inline-assign pattern in @Bean factory body (Phase 5-06 commit f5c331a)"
provides:
  - "PREREQ-01 closure: D-12 non-@Autowired instance field + this.openTelemetry=sdk inline-assign in both OtelSdkConfiguration.java files"
  - "10-01-CYCLE-DIAGNOSIS.md: pre-fix baseline with historical cycle path + post-fix smoke verification"
affects:
  - "10-02 through 10-05 (all Wave 1+ plans): both services boot cleanly, STACK-03 OTLP endpoint contract preserved"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "D-12: non-@Autowired instance field assignment inline in @Bean factory body — mirrors LOG-03 shape from Phase 5-06"

key-files:
  created:
    - ".planning/phases/10-prerequisites-stack-decomposition/10-01-CYCLE-DIAGNOSIS.md"
  modified:
    - "producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
    - "consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"

key-decisions:
  - "D-12 fix applied as forward-mitigation: cycle resolved in Phase 5-06 (commit f5c331a), D-12 adds defensive non-@Autowired field + inline-assign as PREREQ-01 closure pattern"
  - "TRACE-01/DOC-05 preserved: per-service file duplication maintained, no extraction to otel-bootstrap shared module"
  - "Producer field JavaDoc references httpServerSpanFilter; consumer omits it per Phase 4 D-07 per-service distinction"

patterns-established:
  - "D-12: inline-assign this.openTelemetry=sdk before OpenTelemetryAppender.install(sdk) and return sdk in @Bean factory body"

requirements-completed: [PREREQ-01, STACK-03]

# Metrics
duration: 20min
completed: 2026-05-02
---

# Phase 10 Plan 01: PREREQ-01 OtelSdkConfiguration Cycle Fix Summary

**D-12 non-@Autowired instance field + this.openTelemetry=sdk inline-assign applied to both OtelSdkConfiguration.java files; both services boot in <1s; STACK-03 OTLP endpoint contract preserved verbatim**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-05-02T20:33:00Z
- **Completed:** 2026-05-02T20:39:56Z
- **Tasks:** 3
- **Files modified:** 3 (2 Java + 1 diagnosis doc created)

## Accomplishments

- Captured PREREQ-01 pre-fix baseline: both services already boot cleanly at HEAD (cycle fixed in Phase 5-06 commit `f5c331a`); diagnosis doc records historical cycle path and confirms no @Autowired field present
- Applied D-12 minimal fix to producer and consumer OtelSdkConfiguration.java: `private OpenTelemetry openTelemetry` non-@Autowired field + `this.openTelemetry = sdk` inline-assign with PREREQ-01/D-12 comment block before `OpenTelemetryAppender.install(sdk)` in `@Bean openTelemetry()` factory body
- Smoke-tested both services post-fix: producer boots in 0.835s, consumer in 0.907s — no BeanCurrentlyInCreationException; PREREQ-01 closed

## Task Commits

Each task was committed atomically:

1. **Task 1: Capture pre-fix cycle stack trace and diagnose bean-name path** - `a6c6b6c` (docs)
2. **Task 2: Apply D-12 inline-assign fix to both OtelSdkConfiguration.java files** - `03bafa5` (feat)
3. **Task 3: Smoke test — both services boot to "Started" without cycle exception** - `3674b01` (docs)

## Files Created/Modified

- `.planning/phases/10-prerequisites-stack-decomposition/10-01-CYCLE-DIAGNOSIS.md` — Pre-fix cycle diagnosis (historical Phase 5-06 cycle path, HEAD state confirmation) + post-fix smoke result (Task 3 evidence)
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — D-12 non-@Autowired `private OpenTelemetry openTelemetry` field (line 98-112) + `this.openTelemetry = sdk` inline-assign with comment block (line 213-224 in post-fix file); field JavaDoc references httpServerSpanFilter
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — identical D-12 fix shape (TRACE-01/DOC-05); field JavaDoc omits httpServerSpanFilter reference per Phase 4 D-07

## Decisions Made

- **D-12 as forward-mitigation:** The PREREQ-01 issue was already resolved in Phase 5-06 (commit `f5c331a` removed the `@Autowired private OpenTelemetry openTelemetry` field that caused the self-cycle). At HEAD, both files had no @Autowired field and no cycle. The D-12 fix is applied as a PREREQ-01 closure pattern: adds the non-@Autowired field + inline-assign as a defensive guard and teaching surface even though the cycle is already gone. This is explicitly anticipated by the plan ("D-12 inline-assign is harmless additive change either way").

- **TRACE-01/DOC-05 preserved:** Identical edit shape applied independently to both files. No extraction to otel-bootstrap shared module (per-service duplication is the workshop's load-bearing teaching surface). Producer field JavaDoc references `httpServerSpanFilter(Tracer, Meter)` since that @Bean exists only in producer; consumer JavaDoc lists only `tracer(OpenTelemetry)` and `meter(OpenTelemetry)`.

- **STACK-03 invariant:** `OTEL_EXPORTER_OTLP_ENDPOINT` env-var-with-fallback shape and `http://localhost:4317` DEFAULT_OTLP_ENDPOINT constant untouched in both files (5 occurrences each for env-var, 2 each for the constant).

## Deviations from Plan

None — plan executed exactly as written. The "cycle doesn't reproduce at HEAD" outcome was explicitly anticipated by Task 1's action note ("If the producer or consumer somehow boots cleanly at HEAD, record THAT outcome and proceed to Task 2 — D-12 inline-assign is harmless additive change either way").

The first smoke test attempt (Task 3) hit port 8080 already in use from the Task 1 pre-check run — the earlier run's Java process was still alive. Killed the stale processes, retried, and both services started cleanly. This is a transient environment issue, not a code problem.

## Issues Encountered

- Port 8080/8081 held by Task 1 pre-check JVM processes during Task 3 smoke test. Resolved by killing the stale processes (`kill 599710 600876`) and retrying. Not a deviation — no plan change required.

## Known Stubs

None — this plan produces only a field declaration + inline-assign (no UI rendering, no data source wiring, no placeholder text).

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced. The `private OpenTelemetry openTelemetry` field is a non-injectable instance field on a Spring @Configuration class with no getter; it does not expand any trust boundary.

## Next Phase Readiness

- PREREQ-01 closed: both services boot cleanly to "Started" with the D-12 pattern in place
- STACK-03 preserved: `OTEL_EXPORTER_OTLP_ENDPOINT` env-var-with-fallback unchanged in both files
- Wave 0 gate satisfied: Wave 1 infrastructure decomposition plans (10-02 through 10-05) can proceed
- Downstream plans that touch OtelSdkConfiguration should be aware: the `private OpenTelemetry openTelemetry` non-@Autowired field now exists in both files (can be used for future @PreDestroy / phase-internal use without going through Spring's bean graph)

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| 10-01-CYCLE-DIAGNOSIS.md exists | FOUND |
| 10-01-SUMMARY.md exists | FOUND |
| producer OtelSdkConfiguration.java exists | FOUND |
| consumer OtelSdkConfiguration.java exists | FOUND |
| Commit a6c6b6c (Task 1) | FOUND |
| Commit 03bafa5 (Task 2) | FOUND |
| Commit 3674b01 (Task 3) | FOUND |
| Producer `this.openTelemetry = sdk` | FOUND |
| Consumer `this.openTelemetry = sdk` | FOUND |
| PREREQ-01 closure section in diagnosis doc | FOUND |

---
*Phase: 10-prerequisites-stack-decomposition*
*Completed: 2026-05-02*
