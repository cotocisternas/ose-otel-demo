---
phase: 14-jdbc-jpa-database-spans
reviewed: 2026-05-04T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - consumer-service/pom.xml
  - consumer-service/src/main/java/com/example/consumer/db/Order.java
  - consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java
  - consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
  - consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java
  - consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java
  - consumer-service/src/main/resources/application.yaml
  - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java
  - mise.toml
  - README.md
findings:
  critical: 1
  warning: 4
  info: 4
  total: 9
status: issues_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-05-04
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 14 adds Spring Data JPA entity persistence and two AOP aspects that emit OTel spans across the JPA boundary without touching existing business logic. The structural approach is sound: the `@Order(HIGHEST_PRECEDENCE)` trick for wrapping outside `@Transactional`, the `bean(*Repository)` pointcut for intercepting JDK proxy beans, and the D-01 inline span template are all applied correctly. The main critical finding is a semantic mismatch between the hardcoded `DB_NAMESPACE = "orders"` constant and the actual database name used by the Testcontainers PostgreSQL container in integration tests, which causes the test to assert and validate an incorrect attribute value. Four warnings cover `TracingRepositoryAspect`'s narrower `catch (Exception)` vs the broader `catch (Throwable)` in `TransactionSpanAspect`, the incorrect `db.operation.name = "INSERT"` for JPA's upsert `save()` behaviour, a misleading test comment about rollback semantics, and the test-4 span count comment becoming stale in Phase 14.

---

## Critical Issues

### CR-01: `DB_NAMESPACE` hardcoded to `"orders"` is incorrect against the test container database

**File:** `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java:47`

**Issue:** `DB_NAMESPACE = "orders"` is a hardcoded constant that is set on every CLIENT span as `db.namespace`. In production the datasource URL is `jdbc:postgresql://localhost:5432/orders`, so the database name matches. However in the integration test suite, `PostgreSQLContainer` is constructed without `.withDatabaseName(...)`, so it defaults to the database named `"test"` (Testcontainers default). The integration test at `OrderFlowIT.java:508` asserts `db.namespace = "orders"` against the span attribute — this assertion passes because the value is baked into the aspect at startup, **not** derived from the live connection. The result: the test validates an incorrect attribute value. Attendees tracing the code believe `db.namespace` reflects the actual database name, but in the test environment it is always `"orders"` regardless of which database Hibernate actually connected to. This is a correctness defect in the test setup and teaches a false invariant.

**Fix:** Either (a) align the test container to use the same database name as the production config:

```java
// OrderFlowIT.java — change line 127
static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("orders");
```

Or (b) inject the database name dynamically into the aspect via the datasource URL at startup rather than hardcoding it, which makes the attribute accurate in all environments. Option (a) is the minimal correct fix for a workshop demo and keeps the pedagogical value intact.

---

## Warnings

### WR-01: `TracingRepositoryAspect` catches `Exception`, not `Throwable` — inconsistent with `TransactionSpanAspect` and silently drops `Error` cases

**File:** `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java:88`

**Issue:** The `catch` block in `traceRepositoryMethod` catches `Exception`, but `ProceedingJoinPoint.proceed()` is declared `throws Throwable`. If the repository method throws a non-`Exception` `Throwable` (e.g., `OutOfMemoryError`, `StackOverflowError`, or any `Error` from Hibernate's internal machinery), the error bypasses the `catch` block entirely. The CLIENT span will end via `finally` with no `recordException` call and no `StatusCode.ERROR` — it silently reports `OK` for a catastrophic failure. `TransactionSpanAspect.traceTransactionBoundary` at line 79 correctly uses `catch (Throwable t)`. The two aspects are inconsistent.

**Fix:**
```java
// TracingRepositoryAspect.java line 88 — change Exception to Throwable
} catch (Throwable e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
}
```

---

### WR-02: `resolveOperationName` maps `save()` to `"INSERT"` but JPA `save()` is an upsert (`INSERT` or `UPDATE`)

**File:** `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java:133`

**Issue:** `JpaRepository.save(entity)` in Spring Data JPA performs an `INSERT` when the entity has no ID, and an `UPDATE` (via `EntityManager.merge()`) when the entity already has an ID. The aspect maps any method starting with `"save"` unconditionally to `"INSERT"`. In the demo's runtime path `save()` is always called on new entities after the idempotency check, so Hibernate generates `INSERT` in practice. However the mapping is semantically wrong for the general JPA case and teaches attendees an incorrect relationship between the OTel semconv `db.operation.name` value and the SQL verb Hibernate actually executes. If an entity is ever updated (e.g., someone passes an entity with an existing ID, or Hibernate decides to MERGE), the span will say `INSERT` while the SQL is `UPDATE`. OTel semconv 1.40.0 expects `db.operation.name` to reflect the actual database operation.

**Fix:** Add a guard that infers from entity state rather than method name, or add a code comment explicitly documenting the limitation and the runtime invariant that prevents the wrong path:

```java
// TracingRepositoryAspect.java — resolveOperationName
// For workshop demo: save() is ONLY called after findByOrderId() returns empty (OrderJpaService.persist),
// so Hibernate always generates INSERT. Mark explicitly to prevent silent drift:
if (methodName.startsWith("save") || methodName.startsWith("persist")) return "INSERT"; // precondition: new entity only
```

If the demo is ever extended to support entity updates, this must be revisited.

---

### WR-03: Test comment for `DBSP-04` asserts rollback scenario that never occurs on the 10% failure path

**File:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java:416-435`

**Issue:** The comment at line 416 states: "TransactionSpanAspect emits an INTERNAL span for `OrderJpaService.persist` with `status=ERROR` when the 10% failure path causes a rollback." This is factually incorrect. `ProcessingFailedException` is thrown at `ProcessingService.java:77` (the `n % 10 == 0` check), which runs **before** the `jpaService.persist()` call at line 96. `TransactionSpanAspect.traceTransactionBoundary` wraps `OrderJpaService.persist` — it is **never entered** for the 10th order. No transaction is started, no rollback occurs, and the transaction INTERNAL span is simply absent. The test correctly guards against this with `orElse(null)` and the null check at line 430, but the comment states the opposite of what happens. Workshop attendees reading the comment alongside Tempo traces will be confused when the `OrderJpaService.persist` INTERNAL span is absent on the 10th order.

**Fix:** Replace the comment at line 416 with an accurate description:

```java
// DBSP-04: On the 10% failure path, ProcessingFailedException is thrown BEFORE
// jpaService.persist() is called (ProcessingService.java line 76-79). Therefore
// TransactionSpanAspect never runs for the 10th order — no transaction is started
// and no transaction INTERNAL span exists. The null-safe orElse(null) below handles
// this expected absence. The CONSUMER and INTERNAL ProcessingService spans carry ERROR status.
```

---

### WR-04: Stale span count comment in `tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions`

**File:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java:381`

**Issue:** The comment reads `"// 10 traces × 5 spans each = 50; allow margin."` Phase 14 adds three spans per successful order (INTERNAL `OrderJpaService.persist` + 2 CLIENT JPA spans), raising the per-order count to 8 for non-failing orders. Nine successful orders × 8 spans + 1 failing order × 5 spans = 77 spans total — not 50. The `Awaitility` condition at line 384 correctly awaits an ERROR CONSUMER span rather than a fixed count, so the test is functionally correct, but the stale arithmetic misleads workshop attendees who read comments to understand what to expect.

**Fix:**
```java
// 10 orders: 9 succeed (8 spans each = INTERNAL txn + 2 JPA CLIENT + CONSUMER + INTERNAL + SERVER + PRODUCER)
// + 1 fails (5 spans each = CONSUMER + INTERNAL + SERVER + PRODUCER + no JPA spans since throw precedes persist)
// Total: ~77 spans. Awaitility correctly waits for the ERROR CONSUMER span rather than a fixed count.
```

---

## Info

### IN-01: `postgres:17-alpine` test container image uses a floating major.minor tag — not pinned to a patch version

**File:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java:127`

**Issue:** `new PostgreSQLContainer<>("postgres:17-alpine")` uses a floating tag. The project's `verify:images` invariant (enforced in `mise.toml`) requires all Docker images to be pinned to exact patch versions for workshop reproducibility. The test container image is not checked by `verify:images` (which only scans `docker-compose.yml`), but the same reproducibility rationale applies — a cohort running tests six months later may pull a different `postgres:17-alpine` patch, potentially breaking Hibernate DDL compatibility.

**Fix:** Pin to an exact tag:
```java
static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:17.5-alpine");
```

---

### IN-02: `happyPathProducesSingleTrace_traceAssertions` comment `EXPECTED_SPAN_COUNT = 5` is stale for Phase 14

**File:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java:253-257`

**Issue:** The comment `"// EXPECTED_SPAN_COUNT = 5: SERVER + INTERNAL_producer + PRODUCER + CONSUMER + INTERNAL_consumer"` reflects the Phase 6 span structure. Phase 14 adds three more spans for successful orders: `INTERNAL OrderJpaService.persist`, `CLIENT OrderJpaRepository.findByOrderId`, and `CLIENT OrderJpaRepository.save`. The `Awaitility` condition uses `>= 5` (a lower bound), so it remains functionally correct, but attendees reading the comment as a complete description of the trace will miss three spans.

**Fix:** Update the comment:
```java
// Phase 14 span count for a successful order: 8
// SERVER + INTERNAL_producer + PRODUCER + CONSUMER + INTERNAL_consumer
// + INTERNAL OrderJpaService.persist + CLIENT findByOrderId + CLIENT save
// Awaiting >= 5 is a lower bound; all 8 arrive before Awaitility times out.
Awaitility.await()
    .atMost(Duration.ofSeconds(10))
    .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().size() >= 5);
```

---

### IN-03: `successfulOrderRecordsCounterAndHistogram_metricAssertions` also uses stale `>= 5` span count comment

**File:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java:338`

**Issue:** Same as IN-02 — the `Awaitility` condition at line 338 also uses `size() >= 5` with no updated comment. Same cause, same fix pattern as IN-02.

---

### IN-04: `resolveRepositoryInterfaceName` undocumented assumption about Spring AOP proxy layering

**File:** `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java:112-124`

**Issue:** The method uses `pjp.getTarget().getClass().getInterfaces()` to find the user-defined repository interface name. This works because Spring AOP, when intercepting a `bean(*Repository)` bean, wraps the JDK proxy (which does implement `OrderJpaRepository`) with its own proxy — so `getTarget()` returns the JDK proxy instance, and `getClass().getInterfaces()` includes `OrderJpaRepository`. If Spring ever changes this proxy layering behaviour, or if the aspect is applied in a context where `getTarget()` returns the concrete `SimpleJpaRepository` directly, the fallback fires and span names become `"SimpleJpaRepository.findByOrderId"` instead of `"OrderJpaRepository.findByOrderId"`. The current integration test at `OrderFlowIT.java:497` would catch this regression. The assumption is not documented in the method's JavaDoc.

**Fix:** Add a comment documenting the proxy layering assumption:
```java
// pjp.getTarget() returns the JDK dynamic proxy P that implements OrderJpaRepository
// (Spring AOP wraps P with an outer proxy; getTarget() peels back to P, not to
// SimpleJpaRepository which is P's internal delegate). P.getClass().getInterfaces()
// therefore includes the user-defined repository interface. If this fallback is ever
// reached, the integration test OrderFlowIT#dbClientSpansPresentInTrace_spanAssertions
// will fail with span name "SimpleJpaRepository.findByOrderId".
private static String resolveRepositoryInterfaceName(Object target) {
```

---

_Reviewed: 2026-05-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
