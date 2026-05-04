---
phase: 14-jdbc-jpa-database-spans
plan: "01"
subsystem: database
tags: [spring-data-jpa, hibernate, postgresql, jpa, ddl-auto, consumer-service]

# Dependency graph
requires:
  - phase: 08-db-cache
    provides: Phase 8 JDBC foundation (starter-jdbc, schema.sql, OrderRepository.java) that this plan surgically replaces
provides:
  - consumer-service/pom.xml with spring-boot-starter-data-jpa (no version tag, BOM-managed)
  - consumer-service/src/main/resources/application.yaml with spring.jpa.hibernate.ddl-auto=update and PostgreSQLDialect
  - Deletion of Phase 8 artifacts (OrderRepository.java, schema.sql) from git index
affects:
  - 14-02 (JPA entity + repository depend on this pom.xml and application.yaml state)
  - 14-03 (ProcessingService wiring depends on 14-02 which depends on 14-01)
  - 14-04 (compile gate depends on this Wave 1 breakage being the expected error)

# Tech tracking
tech-stack:
  added:
    - spring-boot-starter-data-jpa (Hibernate 6.x + Spring Data JPA 3.x + HikariCP 5.x)
  patterns:
    - "Wave 1 intentional breakage: delete old artifacts before adding replacements (ordered plans)"
    - "BOM-managed JPA dependency: no version tag on spring-boot-starter-data-jpa"
    - "Hibernate DDL replaces spring.sql.init schema.sql: ddl-auto=update"

key-files:
  created: []
  modified:
    - consumer-service/pom.xml
    - consumer-service/src/main/resources/application.yaml
  deleted:
    - consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java
    - consumer-service/src/main/resources/schema.sql

key-decisions:
  - "Phase 8 starter-jdbc replaced by starter-data-jpa; postgresql driver retained as JPA uses the same JDBC driver"
  - "ddl-auto=update chosen over create-drop (workshop-local Postgres; data loss risk with create-drop per T-14-01-02)"
  - "Wave 1 compile breakage (ProcessingService references deleted OrderRepository) is EXPECTED and intentional — resolved by plan 14-02"

patterns-established:
  - "Phase 14 comment style: each JPA dependency block carries a pedagogical comment explaining Phase 8 contrast"

requirements-completed:
  - DBSP-01

# Metrics
duration: 2min
completed: "2026-05-04"
---

# Phase 14 Plan 01: Replace Phase 8 JDBC Foundation with Spring Data JPA

**spring-boot-starter-data-jpa replaces starter-jdbc in consumer-service pom.xml; Hibernate ddl-auto=update replaces schema.sql init; Phase 8 OrderRepository.java and schema.sql deleted from git index**

## Performance

- **Duration:** 2min
- **Started:** 2026-05-04T06:55:11Z
- **Completed:** 2026-05-04T06:57:xx Z
- **Tasks:** 2
- **Files modified:** 4 (2 edited, 2 deleted)

## Accomplishments

- Swapped `spring-boot-starter-jdbc` for `spring-boot-starter-data-jpa` in consumer-service/pom.xml with full pedagogical comment (Phase 8 contrast, BOM-managed, no version tag)
- Rewrote application.yaml: removed `spring.sql.init` block, added `spring.jpa.hibernate.ddl-auto=update`, `show-sql: false`, `dialect: PostgreSQLDialect`
- Deleted Phase 8 artifacts via `git rm` (OrderRepository.java, schema.sql) — tracked deletion in git index
- Compile check confirmed expected Wave 1 breakage: `cannot find symbol: class OrderRepository` in ProcessingService — JPA dependency resolved correctly, only missing class reference fails

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace starter-jdbc with starter-data-jpa in consumer-service/pom.xml** - `0182bbc` (chore)
2. **Task 2: Update application.yaml + delete Phase 8 files** - `bd41c99` (chore)

**Plan metadata:** (final commit below)

## Files Created/Modified

- `consumer-service/pom.xml` - Replaced `spring-boot-starter-jdbc` with `spring-boot-starter-data-jpa` (BOM-managed, pedagogical comment)
- `consumer-service/src/main/resources/application.yaml` - Replaced `spring.sql.init` block with `spring.jpa` config block (ddl-auto, dialect)
- `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` - DELETED (Phase 8; git rm)
- `consumer-service/src/main/resources/schema.sql` - DELETED (Phase 8; git rm)

## Decisions Made

- Kept `postgresql` driver dependency: spring-boot-starter-data-jpa brings Hibernate + JPA but the PostgreSQL JDBC driver is still required at runtime. The Phase 8 comment mentioning driver retention is preserved in the new JPA comment block.
- `ddl-auto=update` chosen per T-14-01-02 threat acceptance: workshop-local Postgres, `create-drop` would cause data loss on restart, `validate` would fail before JPA entities exist.
- Wave 1 intentional breakage: plan design is split across waves. Plan 14-01 deletes old JDBC layer; plan 14-02 adds JPA entity + repository. The gap causes a compile error on `ProcessingService` — expected and documented.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Comment text in application.yaml caused grep -c 'sql.init' to return 1 instead of 0**
- **Found during:** Task 2 verification
- **Issue:** The prescribed comment "Phase 14: JPA config replaces spring.sql.init (D-J2...)" contained the text "sql.init" which grep matched, failing the acceptance criterion
- **Fix:** Reworded comment to "schema DDL init" which preserves the pedagogical intent without matching the criterion's grep pattern
- **Files modified:** consumer-service/src/main/resources/application.yaml
- **Verification:** `grep -c 'sql.init' application.yaml` returns 0
- **Committed in:** bd41c99 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - comment text grep collision)
**Impact on plan:** Minor wording change to comment; semantic intent fully preserved. No scope creep.

## Issues Encountered

- Compile verification confirmed `cannot find symbol: class OrderRepository` in ProcessingService (expected Wave 1 breakage per plan). No JPA dependency resolution errors occurred — confirming starter-data-jpa was added correctly and is on the classpath.

## Known Stubs

None - this plan performs infrastructure surgery only (dependency swap + config update + file deletion). No business logic, no data wiring, no UI components.

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. Changes are:
- Build dependency substitution (compile-time only)
- YAML configuration (datasource credentials remain env-var sourced per T-14-01-01)
- File deletions from git index

No threat flags added beyond the pre-existing T-14-01-02 accept for `ddl-auto=update` (documented in plan threat model).

## Next Phase Readiness

- Plan 14-02 can now proceed: pom.xml has `spring-boot-starter-data-jpa`, application.yaml has JPA config, Phase 8 artifacts are deleted
- Plan 14-02 must: add the `Order` JPA entity, create a new `JpaOrderRepository` interface (extending `JpaRepository`), update `ProcessingService` to use the new repository — resolving the Wave 1 compile breakage
- No blockers for 14-02; the Wave 1 breakage is the designed handoff point

---
*Phase: 14-jdbc-jpa-database-spans*
*Completed: 2026-05-04*

## Self-Check: PASSED

| Item | Status |
|------|--------|
| SUMMARY.md exists at .planning/phases/14-jdbc-jpa-database-spans/14-01-SUMMARY.md | FOUND |
| Task 1 commit 0182bbc | FOUND |
| Task 2 commit bd41c99 | FOUND |
| pom.xml contains spring-boot-starter-data-jpa | FOUND |
| application.yaml contains ddl-auto | FOUND |
| OrderRepository.java absent from working tree | ABSENT (correct) |
| schema.sql absent from working tree | ABSENT (correct) |
