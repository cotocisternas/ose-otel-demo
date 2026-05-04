---
phase: 14-jdbc-jpa-database-spans
plan: "04"
subsystem: database
tags: [mise, verification, readme, workshop-docs, tempo, opentelemetry]

# Dependency graph
requires:
  - phase: 14-01
    provides: spring-boot-starter-data-jpa in pom.xml; JPA application.yaml config
  - phase: 14-02
    provides: Order entity, OrderJpaRepository, OrderJpaService, ProcessingService wired
  - phase: 14-03
    provides: TracingRepositoryAspect + TransactionSpanAspect AOP instrumentation
provides:
  - mise.toml verify:jpa-spans task (Tempo search for db.query.text=* on order-consumer)
  - README.md Step 14 section with Phase 8 contrast narrative and screenshot placeholder
affects:
  - orchestrator (tag application: step-14-jpa-spans)
  - Phase 18 (Playwright screenshot capture for step-14-jpa.png)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "verify:jpa-spans follows verify:log-metrics retry-loop pattern: 6 attempts, 5s sleep, curl Tempo /api/search with tags= and q= params"
    - "README screenshot comparison table: HTML <table> with <th>Phase 8</th> vs <th>Phase 14</th> and <img> in <td> cells"

key-files:
  created: []
  modified:
    - mise.toml
    - README.md

key-decisions:
  - "verify:jpa-spans searches for db.query.text=* (not db.statement=*) — validates F5-2 mitigation end-to-end at the Tempo query layer"
  - "README §14 inserted before Concepts & FAQ section — maintains step-by-step reading order (Steps 1-14 then conceptual deep-dives)"

patterns-established:
  - "Tempo tag search for span attribute presence: tags=<attr>%3D%2A&q=%7Bservice.name%3D%22<svc>%22%7D"

requirements-completed:
  - DBSP-05

# Metrics
duration: 3min
completed: "2026-05-04"
---

# Phase 14 Plan 04: Verification Task and README §14 Section

**verify:jpa-spans Tempo search task added to mise.toml; README §14 documents Phase 8 contrast (1 JDBC span vs 3 JPA spans), screenshot comparison table, and full span waterfall structure**

## Performance

- **Duration:** ~3min
- **Started:** 2026-05-04T08:50:00Z
- **Completed:** 2026-05-04T08:53:00Z
- **Tasks:** 2 (1 auto + 1 human-verify checkpoint)
- **Files modified:** 2

## Accomplishments

- Added `verify:jpa-spans` task to `mise.toml`: queries Tempo `/api/search` for `db.query.text=*` spans on `order-consumer` service with 6-attempt, 5-second retry loop. Diagnostic output on failure includes aspect-loading grep, attribute-key verification, and service restart commands.
- Added README §14 "Step 14: JPA Database Spans" section with Phase 8 contrast narrative: explains the jump from 1 JDBC span to 3 JPA spans (INTERNAL transaction parent + CLIENT findByOrderId + CLIENT save). Includes `@Order(HIGHEST_PRECEDENCE)` teaching callout, Hibernate DDL contrast with Phase 8's `schema.sql`, expected span waterfall diagram, `traceId` bridge column query, and `db.query.text` vs `db.statement` attribute name verification.
- Screenshot comparison table with `step-08-db-cache.png` (Phase 8) vs `step-14-jpa.png` (Phase 14) placeholder.
- Human-verify checkpoint passed: `mise run verify:jpa-spans` exits GREEN; Tempo shows transaction parent + SELECT/INSERT waterfall; 10% failure path surfaces as `status=ERROR` on INTERNAL span; all 5 integration tests pass.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add verify:jpa-spans task to mise.toml; add README §14 section** - `08df7aa` (feat)
2. **Task 2: Human-verify checkpoint** - approved (no commit; verification-only gate)

**Plan metadata:** (final commit below)

## Files Created/Modified

- `mise.toml` - MODIFIED: Added `[tasks."verify:jpa-spans"]` block with Tempo search for `db.query.text=*` spans (6-attempt retry, 5s interval)
- `README.md` - MODIFIED: Added "## Step 14: JPA Database Spans" section (~75 lines) with Phase 8 contrast, screenshot comparison table, span waterfall diagram, run commands

## Decisions Made

- **verify:jpa-spans search key**: Uses `db.query.text=*` (URL-encoded as `db.query.text%3D%2A`) rather than `db.statement=*`. This validates the F5-2 mitigation end-to-end: if someone accidentally used the deprecated `"db.statement"` string literal instead of `DbAttributes.DB_QUERY_TEXT` typed constant, the verify task would catch it by returning zero results.
- **README §14 placement**: Inserted immediately before "## Concepts & FAQ" to maintain the step-by-step reading order (Steps 1 through 14) followed by conceptual deep-dives. The "Why no Spring Data JPA?" FAQ subsection (which was added in a prior phase to explain Phase 8's choice of `JdbcTemplate`) remains in the Concepts section as historical context for the v1 decision that Phase 14 supersedes.

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None. The `step-14-jpa.png` screenshot file does not yet exist on disk, but this is by design: Phase 18's Playwright automation generates it. The README `<img>` tag is a placeholder per the established pattern (same as `step-08-db-cache.png`, `step-11-tail-sampling-off.png`, etc.).

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. The `verify:jpa-spans` task performs a read-only `curl` query to `localhost:3200` (Tempo) -- same trust boundary as all existing `verify:*` tasks.

- T-14-04-01: curl output shown on terminal (accepted -- local workshop stack only)
- T-14-04-02: Tempo search is read-only; no authentication in workshop stack (accepted)

## Next Phase Readiness

Phase 14 is complete. All 5 DBSP requirements satisfied:
- DBSP-01: starter-data-jpa in pom.xml, application.yaml JPA config, Phase 8 files deleted
- DBSP-02: Order entity, OrderJpaRepository, OrderJpaService created; ProcessingService wired
- DBSP-03: TracingRepositoryAspect wraps JPA repo calls in CLIENT spans with db.* semconv
- DBSP-04: TransactionSpanAspect wraps @Transactional boundary; rollback = status=ERROR
- DBSP-05: verify:jpa-spans GREEN; README §14 with screenshot placeholder

The git tag `step-14-jpa-spans` is ready for application by the orchestrator per WORK-01 / D-21.

---
*Phase: 14-jdbc-jpa-database-spans*
*Completed: 2026-05-04*

## Self-Check: PASSED

| Item | Status |
|------|--------|
| 14-04-SUMMARY.md at .planning/phases/14-jdbc-jpa-database-spans/ | FOUND |
| Task 1 commit 08df7aa | FOUND |
| verify:jpa-spans count in mise.toml (>=2) | CONFIRMED (6) |
| Step 14: JPA Database Spans heading in README | CONFIRMED (1) |
| step-14-jpa.png placeholder in README | CONFIRMED (1) |
| Human-verify checkpoint approved | PASSED |
