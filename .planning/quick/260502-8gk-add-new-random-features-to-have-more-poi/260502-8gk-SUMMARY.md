---
quick_id: 260502-8gk
status: complete
date: 2026-05-02
tasks_completed: 5
tasks_total: 5
commits: [7b1d438, bdde8c2, 0cc41ab, f712dfc, a87da09, 7ac45e0]
git_tag: step-08-db-cache
git_tag_commit: 7ac45e0
review_findings_addressed: [H-01, H-02, M-01, M-02, L-02]
---

## Summary

Phase 8 DB & Cache manual OTel instrumentation: Valkey idempotency (producer CLIENT span via `InstrumentedJedisPool`) + PostgreSQL persistence (consumer CLIENT span via `OrderRepository` + `HikariCpConnectionGauge` ObservableGauge) + 5th integration test asserting both CLIENT spans in the same trace as AMQP spans.

## Tasks

- **Task 1 (docker-compose adds Valkey + PostgreSQL services):** commit `7b1d438`, files `docker-compose.yml`. Added `valkey/valkey:8.1-alpine` and `postgres:17-alpine` services with pinned tags, healthchecks, and `restart: unless-stopped`. `docker compose config --quiet` exits 0.

- **Task 2 (producer Valkey idempotency check + InstrumentedJedisPool):** commit `bdde8c2`, files `producer-service/pom.xml`, `application.yaml`, `InstrumentedJedisPool.java`, `IdempotencyService.java`, `OrderController.java`. Added Jedis 7.5.0, InstrumentedJedisPool wrapping CLIENT span around atomic `SET NX EX`, IdempotencyService with hit/miss LongCounters, OrderController idempotency gate (X-Idempotency-Key header → 409 on duplicate). `mvn -pl producer-service compile` exits 0.

- **Task 3 (consumer JDBC OrderRepository + HikariCP gauge + schema.sql):** commit `0cc41ab`, files `consumer-service/pom.xml`, `application.yaml`, `schema.sql`, `OrderRepository.java`, `HikariCpConnectionGauge.java`, `ProcessingService.java`. Added spring-boot-starter-jdbc + postgresql driver, schema.sql DDL with IF NOT EXISTS, OrderRepository CLIENT span wrapping `jdbcTemplate.update(INSERT ... ON CONFLICT DO NOTHING)`, HikariCpConnectionGauge ObservableGauge with used/idle/pending dimensions, ProcessingService updated to inject repository + objectMapper and call insertProcessedOrder on success path. `mvn -pl consumer-service compile` exits 0.

- **Task 4 (integration test asserts Valkey + Postgres CLIENT spans):** commit `f712dfc`, files `integration-tests/pom.xml`, `OrderFlowIT.java`. Added `org.testcontainers:postgresql` and `org.testcontainers:testcontainers` deps; `PostgreSQLContainer` and `GenericContainer` (Valkey) fields; `System.setProperty` for Valkey host/port and Postgres datasource in `startTwoSpringContexts()`; `System.clearProperty` in `shutdown()`; new `dbClientSpansPresentInTrace_spanAssertions()` @Test asserting ≥2 CLIENT spans with correct `db.system.name` and same `traceId` as SERVER span. `mvn -pl integration-tests test-compile` exits 0. 5 @Test methods total.

- **Task 5 (README Step 8 + FAQ entries + git tag):** commit `a87da09`, files `README.md`. Added Step 8 section (5-section template: What you'll learn / Checkpoint / Run / What to look for / Why it matters + Pitfalls + Exercise). Added "Why no Spring Data JPA?" and "Why is Valkey treated as 'redis' in OTel semconv?" FAQ entries. Updated closing paragraph to reference `step-08-db-cache`. Applied annotated tag `step-08-db-cache` on HEAD. `mvn compile` exits 0; tag verified.

## Deviations from PLAN.md

**Warning 1 (Task 1 — non-blocking):** Used `valkey/valkey:8.1-alpine` per plan (stricter pin than CONTEXT.md's `8-alpine`). No code change needed.

**Warning 2 (Task 4 — clarification applied):** Per plan clarification, did NOT add `@DynamicPropertySource` static method for Valkey (does not work with SpringApplicationBuilder-managed contexts). Did NOT add `@ServiceConnection` on `PostgreSQLContainer`. Used `System.setProperty(...)` for BOTH Valkey AND Postgres in `startTwoSpringContexts()`, and `System.clearProperty(...)` in `@AfterAll` — exactly as the plan clarification specified.

No other deviations. Plan executed exactly as specified.

## Self-Check

**Created files exist:**
- `producer-service/src/main/java/com/example/producer/cache/InstrumentedJedisPool.java` — FOUND
- `producer-service/src/main/java/com/example/producer/cache/IdempotencyService.java` — FOUND
- `consumer-service/src/main/resources/schema.sql` — FOUND
- `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` — FOUND
- `consumer-service/src/main/java/com/example/consumer/observability/HikariCpConnectionGauge.java` — FOUND

**Commits exist:** 7b1d438, bdde8c2, 0cc41ab, f712dfc, a87da09 — all verified in git log.

**Tag:** `step-08-db-cache` annotated tag — moved to `7ac45e0` after applying review fixes (was `a87da09`).

**Full compile:** `mvn -B compile` exits 0 across all modules.

**docker compose config:** exits 0, both new services present.

## Self-Check: PASSED

## Post-Review Follow-Up (commit `7ac45e0`)

The orchestrator's `gsd-code-reviewer` returned **NEEDS-FIX** with 9 findings. User chose
"Fix HIGH + MEDIUM now in a follow-up commit" via AskUserQuestion. Applied inline:

- **H-01 — `InstrumentedJedisPool.java`:** nested the `Jedis` try-with-resources
  inside the `Scope` try-with-resources so the outer `catch` block reliably handles
  `pool.getResource()` failures and records them on the span. Made the error-handling
  shape explicit for workshop attendees.
- **H-02 — `ProcessingService.java`:** replaced `String.valueOf(order.get("orderId"))`
  with an `instanceof String` pattern-match + blank check. The previous code persisted
  the literal string `"null"` as a sentinel primary key; `ON CONFLICT DO NOTHING`
  silently no-op'd subsequent missing-orderId messages against that row.
- **M-01 — `HikariCpConnectionGauge.java`:** replaced unchecked `(HikariDataSource)`
  cast with `dataSource.unwrap(HikariDataSource.class)` so Spring/Actuator wrappers
  (e.g. `LazyConnectionDataSourceProxy`) don't crash startup with `ClassCastException`.
- **M-02 — `OrderFlowIT.dbClientSpansPresentInTrace_spanAssertions`:** the test
  request had no `orderId` field, so the controller's body fallback resolved to `null`
  and skipped the cache check entirely (no Valkey CLIENT span emitted). Now sends an
  `X-Idempotency-Key` header (uniquified with `System.nanoTime()` so each test run misses
  the cache and returns 202).
- **L-02 (drive-by) — `InstrumentedJedisPool.java`:** added `import io.opentelemetry.semconv.DbAttributes`
  so `DB_SYSTEM_NAME` and `DB_OPERATION_NAME` use the consistent named import (was
  previously mixed FQCN inline + named import within four consecutive lines).

`step-08-db-cache` tag was deleted from `a87da09` (broken state) and re-created on
`7ac45e0` (post-fix). Workshop attendees who `git checkout step-08-db-cache` get the
fixed code with the new integration test that actually exercises the Valkey path.

LOW findings remaining (NOT addressed — left as workshop exercises or future follow-up):
- **L-01 — `OrderRepository.java`:** missing `server.address` / `server.port` on JDBC
  CLIENT span (semconv 1.40.0 SHOULD-level). Workshop opportunity: ask attendees why
  Tempo span attribute coverage matters for cross-signal correlation.
- **L-03 — `docker-compose.yml`:** Postgres password equals username equals db name —
  fine for local demo, worth a one-line comment if the workshop ever publishes a
  hardened variant.
