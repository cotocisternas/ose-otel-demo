---
phase: 14-jdbc-jpa-database-spans
plan: "02"
subsystem: database
tags: [spring-data-jpa, jpa, entity, repository, transaction, consumer-service]

# Dependency graph
requires:
  - phase: 14-01
    provides: pom.xml with spring-boot-starter-data-jpa, application.yaml with JPA config, Phase 8 JDBC files deleted
provides:
  - consumer-service/src/main/java/com/example/consumer/db/Order.java
  - consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java
  - consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java (wired to JPA layer)
affects:
  - 14-03 (AOP aspects wrap OrderJpaService.persist and OrderJpaRepository methods)
  - 14-04 (compile gate verifies clean build from Wave 2 + Wave 3 together)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JPA entity: @Table(name='orders') with surrogate Long id + business key orderId (unique=true)"
    - "Spring Data derived query: findByOrderId generates SELECT...WHERE order_id=? with bind parameter (injection-safe)"
    - "@Transactional persist: findByOrderId check then conditional save (application-layer idempotency per D-J3/D-J4)"
    - "OTel-free service layer: OrderJpaService has no Tracer injection; AOP aspects in 14-03 wrap it externally"

key-files:
  created:
    - consumer-service/src/main/java/com/example/consumer/db/Order.java
    - consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java
    - consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java
  modified:
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java

key-decisions:
  - "OrderJpaService kept free of OTel SDK calls — AOP aspects in plan 14-03 wrap it; teaching point: instrumentation without modifying business logic"
  - "Argument order in jpaService.persist corrected from Phase 8: (orderId, payloadJson, traceId) matches OrderJpaService signature (orderId, payload, traceId)"
  - "traceId column carries W3C trace_id at persist time (D-J8): bridge between rows in PostgreSQL and traces in Tempo"

patterns-established:
  - "JPA entity with immutable post-construction: protected no-arg constructor for JPA; public constructor for app use; getters only"
  - "Idempotency at application layer: findByOrderId before save mirrors Phase 8 ON CONFLICT DO NOTHING in SQL"

requirements-completed:
  - DBSP-02

# Metrics
duration: 5min
completed: "2026-05-04"
---

# Phase 14 Plan 02: Create JPA Persistence Layer

**Spring Data JPA entity + repository + service wired into ProcessingService — Wave 1 compile breakage resolved; consumer-service compiles clean**

## Performance

- **Duration:** ~5min
- **Started:** 2026-05-04T06:58:00Z
- **Completed:** 2026-05-04T07:03:41Z
- **Tasks:** 2
- **Files modified:** 4 (3 created, 1 edited)

## Accomplishments

- Created `Order.java`: `@Entity @Table(name="orders")` with surrogate `id`, unique `orderId`, `payload` TEXT, `processedAt` Instant, `traceId` (D-J8 bridge column). Immutable after construction — only getters, JPA no-arg constructor is protected.
- Created `OrderJpaRepository.java`: extends `JpaRepository<Order, Long>` with `findByOrderId` derived query — Spring Data generates `SELECT...WHERE order_id=?` (bind parameter, injection-safe per T-14-02-01).
- Created `OrderJpaService.java`: `@Transactional persist()` calls `findByOrderId` first, then `save` only if absent (application-layer idempotency). No `Tracer` injection — AOP aspects in plan 14-03 wrap this method externally.
- Edited `ProcessingService.java`: replaced `OrderRepository` with `OrderJpaService` throughout (import, field, constructor, call site). Argument order corrected to `(orderId, payloadJson, traceId)` per OrderJpaService signature. LOG.warn messages updated from "processed_orders" to "orders".
- `mvn -pl consumer-service compile` exits 0: Wave 1 breakage introduced in plan 14-01 is resolved.

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Order entity, OrderJpaRepository, and OrderJpaService** - `781d97f` (feat)
2. **Task 2: Wire ProcessingService to OrderJpaService; compile check** - `98ff929` (feat)

**Plan metadata:** (final commit below)

## Files Created/Modified

- `consumer-service/src/main/java/com/example/consumer/db/Order.java` - CREATED: JPA entity mapping to `orders` table with traceId bridge column
- `consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java` - CREATED: JpaRepository interface with findByOrderId derived query
- `consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java` - CREATED: @Transactional persist() with idempotency check; no OTel SDK calls
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` - MODIFIED: wired to OrderJpaService; OrderRepository references removed

## Decisions Made

- `OrderJpaService` deliberately contains no `Tracer` injection — the Phase 14 teaching point is that AOP (in plan 14-03) can add spans without touching business logic. A student reading this class sees pure persistence code.
- Argument order for `jpaService.persist` is `(orderId, payloadJson, traceId)` — this is the correct order matching the `OrderJpaService` signature `(String orderId, String payload, String traceId)`. Phase 8 had the args in a different order (`insertProcessedOrder(orderId, traceId, payloadJson)`); this plan swaps `payload` and `traceId` to match the JPA service's cleaner parameter naming.
- `@Column(name = "trace_id", length = 64)` uses length 64 to accommodate W3C trace IDs (32 hex chars) with room for future format changes.

## Deviations from Plan

None — plan executed exactly as written. All 5 acceptance criteria for Task 1 pass (counts match or exceed expected values, with minor over-count explained by JavaDoc containing the same strings as code — semantically correct). Task 2 compile exits 0.

## Known Stubs

None. The JPA layer is fully wired: `ProcessingService` calls `OrderJpaService.persist()` on the success path, which calls `OrderJpaRepository` methods. The aspects in plan 14-03 will wrap these calls to emit spans.

## Threat Surface Scan

No new network endpoints or auth paths introduced. Changes are in-process Java classes only.

Threat mitigations confirmed per plan T-14-02-01 / T-14-02-02:
- `findByOrderId`: Spring Data derived query uses bind parameter (`WHERE order_id = ?`) — no string concatenation, SQL injection impossible
- `save(Order)`: Hibernate prepared statement INSERT — injection not possible through the save() path

T-14-02-03 and T-14-02-04 remain accepted per plan threat model (traceId is non-PII; orderId validation already present in ProcessingService unchanged from Phase 8).

## Next Phase Readiness

- Plan 14-03 can now proceed: `OrderJpaService.persist()` and `OrderJpaRepository.findByOrderId/save` exist as AOP pointcut targets
- Plan 14-03 must: add `TransactionSpanAspect` (wraps OrderJpaService.persist to emit INTERNAL span) and `TracingRepositoryAspect` (wraps JpaRepository methods to emit CLIENT spans)
- No blockers for 14-03; the Wave 2 compile success is the designed handoff

---
*Phase: 14-jdbc-jpa-database-spans*
*Completed: 2026-05-04*

## Self-Check: PASSED

| Item | Status |
|------|--------|
| SUMMARY.md at .planning/phases/14-jdbc-jpa-database-spans/14-02-SUMMARY.md | FOUND |
| Task 1 commit 781d97f | FOUND |
| Task 2 commit 98ff929 | FOUND |
| Order.java exists with @Table(name="orders") | FOUND |
| OrderJpaRepository.java extends JpaRepository<Order, Long> | FOUND |
| OrderJpaService.java has @Transactional persist() | FOUND |
| ProcessingService.java has zero OrderRepository references | CONFIRMED (grep -c outputs 0) |
| ProcessingService.java calls jpaService.persist | CONFIRMED |
| mvn -pl consumer-service compile exits 0 | PASSED |
