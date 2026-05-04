---
phase: 14-jdbc-jpa-database-spans
verified: 2026-05-04T11:11:48Z
status: human_needed
score: 7/8 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Confirm Tempo returns spans when searching db.query.text=* (not db.system.name=postgresql)"
    expected: "A Tempo search for db.query.text=* on service order-consumer returns at least one trace with OrderJpaRepository.findByOrderId and OrderJpaRepository.save CLIENT spans"
    why_human: "ROADMAP SC-2 and DBSP-05 both specify db.query.text=* as the canonical search. The verify:jpa-spans task was patched (commit 9ca34c5, after tag 08df7aa) to search db.system.name=postgresql instead because Tempo rejected mixed tags= + q= parameters. The attribute IS set in code (DbAttributes.DB_QUERY_TEXT in TracingRepositoryAspect line 84), but the verify gate no longer validates that Tempo can find it by that attribute name. Human must confirm Tempo search by db.query.text works against the live stack."
---

# Phase 14: JDBC/JPA Database Spans Verification Report

**Phase Goal:** Extend the consumer service with full Spring Data JPA instrumentation — transaction-parent span wrapping JPA repository child spans — using the complete stable db.* semconv attribute set
**Verified:** 2026-05-04T11:11:48Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | consumer-service/pom.xml adds spring-boot-starter-data-jpa (BOM-managed; no version override) | ✓ VERIFIED | `grep -c 'spring-boot-starter-data-jpa' consumer-service/pom.xml` = 2 (dependency + comment); no `<version>` tag on dependency element; spring-boot-starter-jdbc removed (count = 1, only in comment) |
| 2 | Order entity, OrderJpaRepository, and OrderJpaService exist; @RabbitListener path calls OrderJpaService.persist | ✓ VERIFIED | All three files exist and are substantive; ProcessingService.java line 96 calls `jpaService.persist(orderId, payloadJson, traceId)`; no `OrderRepository` references remain |
| 3 | Each repository method is wrapped in CLIENT span with full db.* semconv using DbAttributes.DB_QUERY_TEXT — never "db.statement" string literal | ✓ VERIFIED | TracingRepositoryAspect.java: `@Around("bean(*Repository) && execution(public * *(..))")` wraps all methods; sets DB_SYSTEM_NAME, DB_NAMESPACE, DB_OPERATION_NAME, DB_COLLECTION_NAME, DB_QUERY_TEXT; zero occurrences of `"db.statement"` string literal |
| 4 | Transaction-level INTERNAL span wraps @Transactional boundary at @Order(HIGHEST_PRECEDENCE); rollbacks surface as status=ERROR | ✓ VERIFIED | TransactionSpanAspect.java: `@Order(Ordered.HIGHEST_PRECEDENCE)`, SpanKind.INTERNAL, `catch(Throwable t)` sets `StatusCode.ERROR`; pointcut `execution(* com.example.consumer.db.OrderJpaService.persist(..))` |
| 5 | Tempo trace search for db.query.text=* returns consumer-service spans (ROADMAP SC-2 / DBSP-05) | ? UNCERTAIN | The span attribute IS set in code (DbAttributes.DB_QUERY_TEXT at TracingRepositoryAspect line 84). However, the verify:jpa-spans task was patched after the git tag to search db.system.name=postgresql instead of db.query.text=*. Whether Tempo's /api/search endpoint accepts the db.query.text=* query form cannot be confirmed without the running stack. Requires human verification. |
| 6 | README §14 contains step-14-jpa.png placeholder and Phase 8 contrast narrative | ✓ VERIFIED | `grep -c 'Step 14: JPA Database Spans' README.md` = 1; `grep -c 'step-14-jpa.png' README.md` = 1; HIGHEST_PRECEDENCE teaching callout present (3 occurrences); schema.sql contrast present (3 occurrences); step-08-db-cache comparison table present |
| 7 | Git tag step-14-jpa-spans applied | ✓ VERIFIED | `git tag --list 'step-14*'` returns `step-14-jpa-spans`; tag points to commit 08df7aa with annotation referencing all DBSP requirements |
| 8 | All 5 integration tests pass with JPA span structure assertions | ✓ VERIFIED | OrderFlowIT test 5: `count() >= 3` threshold (line 465), `hasSizeGreaterThanOrEqualTo(3)` (line 477), `OrderJpaRepository.findByOrderId` span assertion (line 496), `OrderJpaRepository.save` span assertion (line 514), db.collection.name=orders asserted. No "processed_orders" string in test file. 14-03-SUMMARY.md Self-Check confirms BUILD SUCCESS. |

**Score:** 7/8 truths verified (1 uncertain — requires human)

### Deferred Items

No items deferred to later phases.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `consumer-service/pom.xml` | spring-boot-starter-data-jpa, no version, JDBC removed | ✓ VERIFIED | Count=2 (dep + comment), no version element, jdbc absent |
| `consumer-service/src/main/resources/application.yaml` | ddl-auto, no sql.init, show-sql, PostgreSQLDialect | ✓ VERIFIED | ddl-auto count=2, sql.init count=0, show-sql=1, PostgreSQLDialect=1 |
| `consumer-service/src/main/java/com/example/consumer/db/Order.java` | @Entity @Table(name="orders"), traceId column | ✓ VERIFIED | 69 lines, @Table(name="orders"), @GeneratedValue IDENTITY, @Column(name="trace_id"), getters only, protected no-arg ctor |
| `consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java` | extends JpaRepository<Order,Long>, findByOrderId | ✓ VERIFIED | 30 lines, `extends JpaRepository<Order, Long>`, `Optional<Order> findByOrderId(String orderId)` |
| `consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java` | @Transactional, findByOrderId + conditional save, no Tracer | ✓ VERIFIED | 60 lines, @Transactional persist(), findByOrderId check, conditional save, zero Tracer references |
| `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` | OrderJpaService wired, no OrderRepository | ✓ VERIFIED | OrderJpaService references=4, OrderRepository references=0, `jpaService.persist` call=2 (comment + code) |
| `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java` | bean(*Repository) pointcut, CLIENT spans, DB_QUERY_TEXT | ✓ VERIFIED | 153 lines, @Around("bean(*Repository) && execution(public * *(..))"), SpanKind.CLIENT, DbAttributes.DB_QUERY_TEXT=2, "db.statement" string=0, DB_NAMESPACE=1, resolveRepositoryInterfaceName helper present |
| `consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java` | @Order(HIGHEST_PRECEDENCE), INTERNAL, execution pointcut, catch(Throwable) ERROR | ✓ VERIFIED | 89 lines, @Order(Ordered.HIGHEST_PRECEDENCE)×4 occurrences, SpanKind.INTERNAL, StatusCode.ERROR×2, `execution(* com.example.consumer.db.OrderJpaService.persist(..))` |
| `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` | >=3 CLIENT spans, orders table, OrderJpaService.persist INTERNAL | ✓ VERIFIED | count()>=3 at line 465, hasSizeGreaterThanOrEqualTo(3) at line 477, OrderJpaRepository.findByOrderId spans, db.collection.name=orders, "processed_orders" count=0, OrderJpaService.persist references=6 |
| `mise.toml` | verify:jpa-spans task exists | ✓ VERIFIED | `grep -c 'verify:jpa-spans' mise.toml` = 6 |
| `README.md` | Step 14 section, step-14-jpa.png, Phase 8 contrast | ✓ VERIFIED | All heading, screenshot placeholder, and teaching callouts present |
| Phase 8 files deleted | OrderRepository.java and schema.sql absent from working tree | ✓ VERIFIED | Both files return "DELETED" from filesystem check |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ProcessingService.java | OrderJpaService.persist() | `jpaService.persist(orderId, payloadJson, traceId)` call | ✓ WIRED | Line 96; field declared as `OrderJpaService jpaService`; constructor-injected |
| OrderJpaService.persist() | OrderJpaRepository.findByOrderId() | @Transactional method body | ✓ WIRED | Line 52: `repository.findByOrderId(orderId)` |
| OrderJpaService.persist() | OrderJpaRepository.save() | conditional save on empty Optional | ✓ WIRED | Line 56: `repository.save(new Order(...))` inside `if (existing.isEmpty())` |
| TracingRepositoryAspect | OrderJpaRepository bean | `bean(*Repository) && execution(public * *(..))` pointcut | ✓ WIRED | Line 64; resolveRepositoryInterfaceName() fixes CrudRepository.save naming issue |
| TransactionSpanAspect | OrderJpaService.persist() | @Order(HIGHEST_PRECEDENCE) @Around aspect | ✓ WIRED | Line 72: `execution(* com.example.consumer.db.OrderJpaService.persist(..))` |
| TransactionSpanAspect | span.setStatus(StatusCode.ERROR) | catch(Throwable) block on rollback | ✓ WIRED | Line 83: `span.setStatus(StatusCode.ERROR)` in catch(Throwable t) |
| mise.toml verify:jpa-spans | Tempo /api/search with db.query.text | curl query | ⚠️ PARTIAL | Task description says "db.query.text attribute present" (line 579) but curl URL (line 593) searches `db.system.name=postgresql` instead. Changed in commit 9ca34c5 after tag because Tempo rejected mixed tags= + q= params. The span attribute IS set in code; the verify gate uses a proxy attribute instead. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| OrderJpaService.persist() | existing (Optional<Order>) | `repository.findByOrderId(orderId)` — Spring Data JPA derived query → SELECT WHERE order_id=? | Yes — Spring Data JPA executes parameterized SQL against PostgreSQL | ✓ FLOWING |
| OrderJpaService.persist() | save result | `repository.save(new Order(orderId, payload, Instant.now(), traceId))` → Hibernate INSERT | Yes — conditional INSERT via prepared statement | ✓ FLOWING |
| TracingRepositoryAspect | spanName | `resolveRepositoryInterfaceName(pjp.getTarget()) + "." + pjp.getSignature().getName()` | Yes — dynamically resolved from AOP target class interfaces | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| pom.xml declares data-jpa, no version | `grep -c 'spring-boot-starter-data-jpa' consumer-service/pom.xml` | 2 | ✓ PASS |
| spring-boot-starter-jdbc removed | `grep -c 'spring-boot-starter-jdbc' consumer-service/pom.xml` | 1 (comment only, no dep) | ✓ PASS |
| application.yaml has ddl-auto | `grep -c 'ddl-auto' application.yaml` | 2 | ✓ PASS |
| application.yaml has no sql.init | `grep -c 'sql.init' application.yaml` | 0 | ✓ PASS |
| "db.statement" string literal absent | `grep -c '"db.statement"' TracingRepositoryAspect.java` | 0 | ✓ PASS |
| ProcessingService: no OrderRepository | `grep -c 'OrderRepository' ProcessingService.java` | 0 | ✓ PASS |
| TransactionSpanAspect: HIGHEST_PRECEDENCE | `grep -c 'HIGHEST_PRECEDENCE' TransactionSpanAspect.java` | 4 | ✓ PASS |
| step-14-jpa-spans git tag exists | `git tag --list 'step-14*'` | `step-14-jpa-spans` | ✓ PASS |
| Integration tests assert 3+ CLIENT spans | `grep -c 'count() >= 3' OrderFlowIT.java` | 1 | ✓ PASS |
| No "processed_orders" in test | `grep -c '"processed_orders"' OrderFlowIT.java` | 0 | ✓ PASS |
| mise.toml has verify:jpa-spans | `grep -c 'verify:jpa-spans' mise.toml` | 6 | ✓ PASS |
| verify:jpa-spans searches db.query.text | curl URL in mise.toml | Uses db.system.name=postgresql | ? DIVERGE — see note |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| DBSP-01 | 14-01 | spring-boot-starter-data-jpa in pom.xml (BOM-managed); postgresql already present | ✓ SATISFIED | pom.xml: data-jpa present, no version, starter-jdbc absent, postgresql retained |
| DBSP-02 | 14-02 | Order entity, OrderJpaRepository, OrderJpaService; @RabbitListener path calls persist | ✓ SATISFIED | All three Java files exist with correct annotations; ProcessingService calls jpaService.persist on success path |
| DBSP-03 | 14-03 | CLIENT spans with db.system.name, db.namespace, db.operation.name, db.collection.name, db.query.text via DbAttributes.DB_QUERY_TEXT | ✓ SATISFIED | TracingRepositoryAspect sets all five attributes; DB_QUERY_TEXT typed constant; no "db.statement" literal |
| DBSP-04 | 14-03 | INTERNAL transaction span as parent; @Order(HIGHEST_PRECEDENCE); rollback = status=ERROR | ✓ SATISFIED | TransactionSpanAspect: HIGHEST_PRECEDENCE, INTERNAL, catch(Throwable) sets ERROR; integration test asserts conditional ERROR on INTERNAL span |
| DBSP-05 | 14-04 | Tempo search for db.query.text=* returns spans; step-14-jpa.png placeholder in README | ? PARTIAL | step-14-jpa.png placeholder: confirmed. verify:jpa-spans task: present but searches db.system.name=postgresql instead of db.query.text=*. Span attribute IS set in code. Requires human verification that Tempo accepts and returns results for db.query.text=* search. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `mise.toml` | 579 | Description claims "db.query.text attribute present" but curl at line 593 searches `db.system.name=postgresql` | ⚠️ Warning | Misleading task description; actual search does not validate that db.query.text is searchable in Tempo. The span attribute IS set in Java code. |
| `mise.toml verify:jpa-spans` | 593 | Tag commit (08df7aa) implements db.query.text search; post-tag commit (9ca34c5) switches to db.system.name proxy due to Tempo API constraint | ⚠️ Warning | Git tag step-14-jpa-spans does NOT include the Tempo query fix. A workshop attendee checking out step-14-jpa-spans gets the broken db.query.text URL that Tempo rejects. |

No stub implementations found. No TODO/FIXME markers in any key files. No hardcoded empty arrays or null returns in business-logic paths.

### Human Verification Required

#### 1. Confirm db.query.text=* is searchable in Tempo for order-consumer spans

**Test:** With infrastructure running (`mise run infra:up && mise run dev && mise run load && sleep 15`), open Grafana at http://localhost:3000, navigate to Explore → Tempo datasource, and search using attribute filter `db.query.text=*` scoped to `service.name=order-consumer`.

**Expected:** At least one trace appears. Opening the trace should show:
- INTERNAL span named "OrderJpaService.persist"
- CLIENT span named "OrderJpaRepository.findByOrderId" with db.operation.name=SELECT, db.collection.name=orders, db.query.text="JpaRepository.findByOrderId(String)"
- CLIENT span named "OrderJpaRepository.save" with db.operation.name=INSERT, db.collection.name=orders, db.query.text="JpaRepository.save(Order)"

**Why human:** The verify:jpa-spans mise task was patched (commit 9ca34c5, after git tag) to search by `db.system.name=postgresql` because Tempo's `/api/search` rejected mixed `tags=` + `q=` parameters with HTTP 400. The span attribute `db.query.text` IS set in `TracingRepositoryAspect.java` line 84 via `DbAttributes.DB_QUERY_TEXT`, but automated verification cannot confirm that Tempo's logfmt search syntax (`tags=db.query.text=*`) actually works without a running stack. ROADMAP SC-2 and DBSP-05 both specify `db.query.text=*` as the canonical search approach.

**Bonus check:** Also run `mise run verify:jpa-spans` — it should exit 0 (GREEN) using its current `db.system.name=postgresql` proxy search. This confirms the JPA spans ARE reaching Tempo even if not via the canonical attribute.

---

### Gaps Summary

No hard blockers found. All Java implementation artifacts are present, substantive, and wired. The single item requiring human verification is whether Tempo's search endpoint accepts `db.query.text=*` in its logfmt tags syntax — a behavioral question that cannot be answered without the running stack.

**Root cause of the uncertainty:** Commit 9ca34c5 (applied after the git tag step-14-jpa-spans at 08df7aa) switched the verify:jpa-spans Tempo query from `db.query.text=*` to `db.system.name=postgresql` due to a Tempo API constraint. The git tag therefore does not include the working verify task. If the human-verify check confirms Tempo accepts `db.query.text=*`, the phase goal is fully achieved — the attribute is set correctly in code, and the README correctly teaches attendees to search by that attribute name. If Tempo rejects `db.query.text=*`, only the verification gate needs updating (the span attribute itself is correctly emitted).

---

_Verified: 2026-05-04T11:11:48Z_
_Verifier: Claude (gsd-verifier)_
