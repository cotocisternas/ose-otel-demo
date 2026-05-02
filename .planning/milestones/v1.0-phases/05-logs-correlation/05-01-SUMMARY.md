---
phase: 05-logs-correlation
plan: 01
subsystem: infra
tags:
  - maven
  - bom
  - opentelemetry
  - logback
  - dependencies

# Dependency graph
requires:
  - phase: 01-baseline-scaffold
    provides: parent pom.xml import of opentelemetry-instrumentation-bom-alpha:2.27.0-alpha (forward-compat declaration)
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: per-service OTel SDK runtime block (insertion anchor between SDK runtime and semconv blocks)
provides:
  - opentelemetry-logback-appender-1.0 on classpath in producer-service and consumer-service (BOM-managed at 2.27.0-alpha)
  - opentelemetry-logback-mdc-1.0 on classpath in producer-service and consumer-service (BOM-managed at 2.27.0-alpha)
  - First per-service pulls from opentelemetry-instrumentation-bom-alpha (precedent for future instrumentation artifacts)
affects:
  - 05-02 (producer SDK + logback wiring — imports OpenTelemetryAppender.install)
  - 05-03 (consumer SDK + logback wiring — same import surface)
  - 05-04 (logback-spring.xml wiring — references the MDC appender FQCN)

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.27.0-alpha (BOM-managed)"
    - "io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.27.0-alpha (BOM-managed)"
  patterns:
    - "BOM-managed instrumentation artifacts (no <version> tag in dependency declaration)"
    - "Byte-identical per-service dependency block (D-02 mirror property)"

key-files:
  created: []
  modified:
    - producer-service/pom.xml
    - consumer-service/pom.xml

key-decisions:
  - "Both Logback bridge artifacts declared per-service (NOT in parent <dependencyManagement>) to preserve the per-service-duplication teaching pattern (D-02 / DOC-05)"
  - "Inserted between OTel SDK runtime block and semconv block so file reads top-to-bottom: starters → OTel SDK → OTel logback bridges (NEW) → semconv → starter-test"
  - "No <version> tags on either dependency — BOM resolution via opentelemetry-instrumentation-bom-alpha:2.27.0-alpha (already imported in parent pom.xml lines 65-77)"
  - "No <exclusions> needed — clean BOM resolution; both artifacts share opentelemetry-instrumentation-api:2.27.0 + opentelemetry-api:1.61.0 with the existing SDK BOM pin"

patterns-established:
  - "Per-service BOM-managed instrumentation artifact pulls — opens the door for future Phase 6+ instrumentation artifacts to follow the same pattern"
  - "Byte-identical Phase 5 block in both service POMs — diff between producer and consumer Phase 5 sections is empty"

requirements-completed:
  - LOG-02

# Metrics
duration: 3min
completed: 2026-05-02
---

# Phase 05 Plan 01: POM Dependencies Summary

**Two BOM-managed Logback bridge artifacts (`opentelemetry-logback-appender-1.0` + `opentelemetry-logback-mdc-1.0` at 2.27.0-alpha) added to producer-service and consumer-service, byte-identical mirror, mvn validate clean.**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-05-02T01:48:36Z
- **Completed:** 2026-05-02T01:51:17Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- `opentelemetry-logback-appender-1.0` (the OTLP log-record export appender, FQCN `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`) on producer + consumer classpath at version 2.27.0-alpha.
- `opentelemetry-logback-mdc-1.0` (the MDC injector wrapper appender, FQCN `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender`) on producer + consumer classpath at version 2.27.0-alpha.
- Both pulled via the parent's `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` import — first per-service artifacts to use that BOM (Phase 1 declared it forward-compat for this exact moment).
- `mvn -B validate` from repo root passes — `dependencyConvergence` rule clean for all four reactor modules.
- The Phase 5 dependency block in both POMs is **byte-identical** (`diff` produces empty output).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Phase 5 logback bridge dependencies to producer-service/pom.xml** — `3b175f3` (feat)
2. **Task 2: Add the byte-identical block to consumer-service/pom.xml + run mvn validate** — `fe766dd` (feat)

## Files Created/Modified

- `producer-service/pom.xml` — Phase 5 OTel Logback bridges block inserted at lines 85-118 (between the OTel SDK runtime block ending at line 83 and the semconv block starting at line 120). 35 insertions.
- `consumer-service/pom.xml` — Byte-identical Phase 5 OTel Logback bridges block inserted at lines 85-118 (same line range as producer). 35 insertions.

## Verification Results

### `grep` checks

- `grep -q 'opentelemetry-logback-appender-1.0' producer-service/pom.xml` → exit 0
- `grep -q 'opentelemetry-logback-mdc-1.0' producer-service/pom.xml` → exit 0
- `grep -q 'opentelemetry-logback-appender-1.0' consumer-service/pom.xml` → exit 0
- `grep -q 'opentelemetry-logback-mdc-1.0' consumer-service/pom.xml` → exit 0

### No `<version>` tags inside the new dependency blocks

Robust check (scoped to the actual `<dependency>` element, NOT the surrounding comment):

```bash
awk '/<artifactId>opentelemetry-logback-appender-1\.0<\/artifactId>/{found=1} found && /<\/dependency>/{exit 0} found && /<version>/{exit 1}' producer-service/pom.xml   # exit 0
awk '/<artifactId>opentelemetry-logback-mdc-1\.0<\/artifactId>/{found=1} found && /<\/dependency>/{exit 0} found && /<version>/{exit 1}' producer-service/pom.xml         # exit 0
awk '/<artifactId>opentelemetry-logback-appender-1\.0<\/artifactId>/{found=1} found && /<\/dependency>/{exit 0} found && /<version>/{exit 1}' consumer-service/pom.xml   # exit 0
awk '/<artifactId>opentelemetry-logback-mdc-1\.0<\/artifactId>/{found=1} found && /<\/dependency>/{exit 0} found && /<version>/{exit 1}' consumer-service/pom.xml         # exit 0
```

### `mvn dependency:tree` resolution (BOM-management proof)

```
producer-service:
  \- io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:jar:2.27.0-alpha:compile
  \- io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:jar:2.27.0-alpha:compile

consumer-service:
  \- io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:jar:2.27.0-alpha:compile
  \- io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:jar:2.27.0-alpha:compile
```

Both artifacts resolve to **2.27.0-alpha** in both services — confirms BOM-managed resolution from the parent's `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` import.

### `mvn validate` exit code

```
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed   (otel-bootstrap)
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed   (producer-service)
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed   (consumer-service)
[INFO] BUILD SUCCESS
validate exit=0
```

### Byte-identical mirror (D-02)

```bash
diff <(awk '/Phase 5: OTel Logback bridges/,/<\/dependency>$/' producer-service/pom.xml) \
     <(awk '/Phase 5: OTel Logback bridges/,/<\/dependency>$/' consumer-service/pom.xml)
# → exit 0, empty output
```

### `<exclusions>` and `<scope>` tags

Confirmed: no `<exclusions>` tags, no `<scope>` tags, no `<version>` tags added. Both dependencies are clean BOM-resolved declarations matching the existing OTel SDK runtime block style at lines 72-83.

## Decisions Made

- **No deviations.** Plan executed exactly as written. The two-step ordering (producer first, then consumer + `mvn validate`) was followed; the byte-identical mirror property (D-02) is preserved.
- The plan's acceptance-criteria `awk` snippets had a known false-positive: they match the substring `<version>` inside the explanatory comment (line containing "No `<version>` tags — both BOM-managed"). The acceptance check was satisfied via a more robust `awk` rooted on the `<artifactId>` line of each dependency, which scopes the search to the actual XML element rather than the surrounding comment text. The substantive constraint (no `<version>` tag on either of the two new `<dependency>` elements) is met. This is documented but not classified as a deviation — the plan goal was met, only the verification helper had a regex narrowness issue.

## Deviations from Plan

None — plan executed exactly as written. Both files modified at the planned insertion point with the byte-identical Phase 5 block. No `<version>` / `<scope>` / `<exclusions>` tags added. `mvn validate` clean.

## Issues Encountered

None.

## Threat Surface Scan

No new attack surface. Only Maven dependency declarations modified — same publisher (OpenTelemetry Java Instrumentation project) as the existing `opentelemetry-exporter-otlp`, BOM-pinned version (`2.27.0-alpha`), no floating tags, `dependencyConvergence` rule passes.

Threat register dispositions from PLAN frontmatter:
- T-05-01-01 (Tampering — Maven resolution): **mitigated** — BOM-pinned via parent pom.xml line 74; `mvn validate` runs `dependencyConvergence` (passed).
- T-05-01-02, T-05-01-03, T-05-01-04: accepted as planned; nothing new introduced beyond BOM-managed dep declarations.

## Known Stubs

None. The two new artifacts are infra-level dependency declarations; they expose classes that Plans 05-02, 05-03, and 05-04 will import. There are no stubs, placeholders, hardcoded empty values, or "TODO/FIXME" markers in the changes.

## User Setup Required

None — no external service configuration required for this plan. Plan 05-02 onward will reference the classes shipped by these artifacts.

## Next Phase Readiness

- Plan 05-02 can now `import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` in producer-service and call `OpenTelemetryAppender.install(openTelemetrySdk)` from `OtelSdkConfiguration`'s `@PostConstruct`.
- Plan 05-03 can do the same in consumer-service.
- Plan 05-04 can reference `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` as the MDC wrapper appender class in `logback-spring.xml`.
- No blockers. `dependencyConvergence` clean, no version drift, no exclusions needed.

## Self-Check

**Files claimed modified:**
- `producer-service/pom.xml` — verified present, contains both new dependency entries.
- `consumer-service/pom.xml` — verified present, contains both new dependency entries.

**Commits claimed:**
- `3b175f3` — verified via `git log` (Task 1, producer).
- `fe766dd` — verified via `git log` (Task 2, consumer).

## Self-Check: PASSED

---
*Phase: 05-logs-correlation*
*Completed: 2026-05-02*
