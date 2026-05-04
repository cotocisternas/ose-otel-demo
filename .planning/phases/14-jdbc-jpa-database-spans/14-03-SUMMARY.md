---
phase: 14-jdbc-jpa-database-spans
plan: "03"
subsystem: database
tags: [spring-aop, aspectj, jpa, opentelemetry, tracing, consumer-service, integration-tests]

# Dependency graph
requires:
  - phase: 14-02
    provides: OrderJpaService.persist() and OrderJpaRepository.findByOrderId/save as AOP pointcut targets
provides:
  - consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java
  - consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java
  - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java (updated test 4 + test 5)
affects:
  - 14-04 (compile gate verifies clean build from all three waves together)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "bean(*Repository) && execution(public * *(..)) AOP pointcut for Spring Data JPA proxy interception"
    - "resolveRepositoryInterfaceName(): walk target.getClass().getInterfaces() to find user-defined *Repository (skips org.springframework.data.* framework interfaces)"
    - "@Order(Ordered.HIGHEST_PRECEDENCE) wraps OUTSIDE @Transactional proxy for correct rollback span status"
    - "Integration test explicit spring.jpa.hibernate.ddl-auto=update on consumer SpringApplicationBuilder"

key-files:
  created:
    - consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java
    - consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java
  modified:
    - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java

key-decisions:
  - "resolveRepositoryInterfaceName() walks proxy interfaces to find user-defined *Repository instead of using pjp.getSignature().getDeclaringType() — saves() declaring type is CrudRepository, not OrderJpaRepository"
  - "Explicit spring.jpa.hibernate.ddl-auto=update on consumer SpringApplicationBuilder — both producer and consumer contexts pick up JPA auto-configuration from shared classpath; only consumer context needs DDL management"
  - "TransactionSpanAspect catches Throwable (not Exception) to capture rollback exceptions; catch(Exception) would miss unchecked errors that @Transactional reacts to"

patterns-established:
  - "AOP-based OTel instrumentation: add spans without modifying business logic (TracingRepositoryAspect wraps JPA repo; TransactionSpanAspect wraps @Transactional boundary)"
  - "bean(*Repository) pointcut + resolveRepositoryInterfaceName pattern for Spring Data JPA AOP instrumentation"

requirements-completed:
  - DBSP-03
  - DBSP-04

# Metrics
duration: 16min
completed: "2026-05-04"
---

# Phase 14 Plan 03: Add OTel Tracing Aspects for JPA Repository and Transaction

**TracingRepositoryAspect wraps JPA repository calls in CLIENT spans (db.* semconv); TransactionSpanAspect wraps @Transactional boundary in INTERNAL span at @Order(HIGHEST_PRECEDENCE); integration test 5 asserts 3+ CLIENT spans; all 5 tests pass**

## Performance

- **Duration:** ~16min
- **Started:** 2026-05-04T07:06:54Z
- **Completed:** 2026-05-04T07:23:xx Z
- **Tasks:** 2
- **Files modified:** 3 (2 created, 1 edited)

## Accomplishments

- Created `TracingRepositoryAspect.java`: `bean(*Repository) && execution(public * *(..))` pointcut wraps every public JPA repository method in a `SpanKind.CLIENT` span with full `db.*` semconv (DBSP-03). Span names: `OrderJpaRepository.findByOrderId` (SELECT) + `OrderJpaRepository.save` (INSERT). Uses `DbAttributes.DB_QUERY_TEXT` typed constant — zero `"db.statement"` string literals (F5-2).
- Created `TransactionSpanAspect.java`: `@Order(Ordered.HIGHEST_PRECEDENCE)` wraps `OrderJpaService.persist()` in an `SpanKind.INTERNAL` span that covers the full `@Transactional` boundary. `catch(Throwable)` sets `StatusCode.ERROR` so rollbacks surface as error spans in Tempo (DBSP-04).
- Updated `OrderFlowIT` test 5: Awaitility threshold from `>= 2` to `>= 3` CLIENT spans; replaced JDBC span assertion (`db.collection.name=processed_orders`) with JPA span assertions (`OrderJpaRepository.findByOrderId`, `OrderJpaRepository.save`, `OrderJpaService.persist` INTERNAL); asserts `findByOrderId` span's `parentSpanId` equals the transaction INTERNAL span's `spanId`.
- Updated `OrderFlowIT` test 4: added conditional DBSP-04 assertion for `INTERNAL OrderJpaService.persist` span with `STATUS=ERROR` when the rollback path fires.
- All 5 integration tests pass (`mvn -pl integration-tests -am verify`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Create TracingRepositoryAspect and TransactionSpanAspect** - `520f5a2` (feat)
2. **Task 2: Update OrderFlowIT test 5 and test 4; run integration test suite** - `d804d71` (feat)

**Plan metadata:** (final commit below)

## Files Created/Modified

- `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java` - CREATED: CLIENT spans for JPA repository methods with full db.* semconv
- `consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java` - CREATED: INTERNAL span wrapping @Transactional boundary at HIGHEST_PRECEDENCE
- `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` - MODIFIED: test 5 JPA span assertions + test 4 INTERNAL ERROR span assertion + consumer context ddl-auto fix

## Decisions Made

- **resolveRepositoryInterfaceName() pattern**: Spring Data JPA's `save()` is declared in `CrudRepository`, not `OrderJpaRepository`. Using `pjp.getSignature().getDeclaringType().getSimpleName()` produces `CrudRepository.save` instead of `OrderJpaRepository.save`. The fix: walk `target.getClass().getInterfaces()` and return the first interface whose simple name ends in "Repository" and whose FQCN does NOT start with `org.springframework.data`. This reliably returns `OrderJpaRepository` for all methods on the bean.
- **spring.jpa.hibernate.ddl-auto=update explicit in test**: The integration-tests classpath contains both `consumer-service` and `producer-service` resources. Both Spring contexts (producer and consumer) pick up JPA auto-configuration when `spring.datasource.*` System properties are set. The producer context runs first; it connects to PostgreSQL with default `ddl-auto=none` (non-embedded DB). The consumer context runs second; its `application.yaml` has `ddl-auto=update`, but classpath ordering determines which `application.yaml` each context reads. Setting `spring.jpa.hibernate.ddl-auto=update` explicitly in the `SpringApplicationBuilder.properties()` call for the consumer context guarantees DDL runs regardless of classpath ordering.
- **catch(Throwable) in TransactionSpanAspect**: Consistent with `TracingMessageListenerAdvice` pattern. `@Transactional` rollback is triggered by `RuntimeException` and `Error` (unless `@Transactional(rollbackFor=Exception.class)`). `catch(Throwable)` is the correct shape to capture all rollback-triggering exceptions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] TracingRepositoryAspect span name used CrudRepository.save instead of OrderJpaRepository.save**
- **Found during:** Task 2 (integration test failure)
- **Issue:** `pjp.getSignature().getDeclaringType().getSimpleName()` returns the interface where the method is declared. `save()` is declared in `CrudRepository`, not `OrderJpaRepository`. Test 5 asserted `"OrderJpaRepository.save"` but the span was named `"CrudRepository.save"`.
- **Fix:** Added `resolveRepositoryInterfaceName(Object target)` helper that walks `target.getClass().getInterfaces()`, finds the first interface ending in "Repository" with a non-`org.springframework.data.*` FQCN, and returns its simple name.
- **Files modified:** `TracingRepositoryAspect.java`
- **Commit:** `d804d71`

**2. [Rule 1 - Bug] hasStatus(StatusCode.ERROR) type mismatch in test 4**
- **Found during:** Task 2 (compilation error)
- **Issue:** The plan snippet used `assertThat(span).hasStatus(StatusCode.ERROR)`, but `SpanDataAssert.hasStatus()` expects `StatusData`, not `StatusCode`.
- **Fix:** Changed to `org.assertj.core.api.Assertions.assertThat(errorTxnSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR)` — consistent with how test 4's Awaitility condition already checks status.
- **Files modified:** `OrderFlowIT.java`
- **Commit:** `d804d71`

**3. [Rule 3 - Blocking] orders table not created in test PostgreSQL container**
- **Found during:** Task 2 (integration test timeout — `ConditionTimeoutException` on `count() >= 3`)
- **Issue:** Hibernate's `ddl-auto=update` from `consumer-service/src/main/resources/application.yaml` was not running in the test environment. Both Spring contexts (producer and consumer) pick up JPA auto-configuration from the shared test classpath. The producer context initializes JPA first with default `ddl-auto=none` (non-embedded database default). The consumer context's own `application.yaml` may not win the classpath-ordering race. Result: `ERROR: relation "orders" does not exist` on every JPA SELECT, caught and swallowed by `ProcessingService.catch`, so no JPA CLIENT spans were emitted.
- **Fix:** Added `"spring.jpa.hibernate.ddl-auto=update"` to the consumer `SpringApplicationBuilder.properties()` call, ensuring DDL runs in the consumer context regardless of classpath `application.yaml` ordering.
- **Files modified:** `OrderFlowIT.java`
- **Commit:** `d804d71`

## Known Stubs

None. The AOP aspects are fully wired: `TracingRepositoryAspect` intercepts actual `OrderJpaRepository` calls, `TransactionSpanAspect` intercepts actual `OrderJpaService.persist()` calls. Integration tests verify the span waterfall end-to-end against a live PostgreSQL Testcontainer.

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced. The AOP aspects intercept in-process Java calls only. Threat model reviewed:

- T-14-03-01: `db.query.text` values are JPA method descriptions (`"JpaRepository.findByOrderId(String)"`) — no user data or credentials in span attributes (accepted).
- T-14-03-02: `db.namespace`/`db.collection.name` are static constants (`"orders"`) — no sensitive info (accepted).
- T-14-03-03: `bean(*Repository)` pointcut targets only `OrderJpaRepository` in this codebase (accepted).
- T-14-03-04: `@Order(HIGHEST_PRECEDENCE)` is a correctness concern, not a security boundary (accepted).

---
*Phase: 14-jdbc-jpa-database-spans*
*Completed: 2026-05-04*

## Self-Check: PASSED

| Item | Status |
|------|--------|
| TracingRepositoryAspect.java at consumer-service/.../observability/ | FOUND |
| TransactionSpanAspect.java at consumer-service/.../observability/ | FOUND |
| 14-03-SUMMARY.md at .planning/phases/14-jdbc-jpa-database-spans/ | FOUND |
| Task 1 commit 520f5a2 | FOUND |
| Task 2 commit d804d71 | FOUND |
| bean(*Repository) && execution(public * *(..)) pointcut present | CONFIRMED (grep count >= 1) |
| DB_QUERY_TEXT typed constant used (no "db.statement") | CONFIRMED (0 occurrences of "db.statement") |
| @Order(Ordered.HIGHEST_PRECEDENCE) present | CONFIRMED (grep count >= 1) |
| All 5 integration tests pass (mvn -pl integration-tests -am verify) | PASSED (BUILD SUCCESS) |
