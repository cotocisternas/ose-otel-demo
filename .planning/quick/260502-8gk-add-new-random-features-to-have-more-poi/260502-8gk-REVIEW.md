---
phase: 260502-8gk
reviewed: 2026-05-02T00:00:00Z
depth: quick
files_reviewed: 15
files_reviewed_list:
  - producer-service/src/main/java/com/example/producer/cache/InstrumentedJedisPool.java
  - producer-service/src/main/java/com/example/producer/cache/IdempotencyService.java
  - producer-service/src/main/java/com/example/producer/api/OrderController.java
  - producer-service/src/main/resources/application.yaml
  - producer-service/pom.xml
  - consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java
  - consumer-service/src/main/java/com/example/consumer/observability/HikariCpConnectionGauge.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
  - consumer-service/src/main/resources/application.yaml
  - consumer-service/src/main/resources/schema.sql
  - consumer-service/pom.xml
  - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java
  - integration-tests/pom.xml
  - docker-compose.yml
  - README.md
findings:
  critical: 2
  warning: 4
  info: 3
  total: 9
status: issues_found
---

# Phase 260502-8gk: Code Review Report

**Reviewed:** 2026-05-02
**Depth:** quick (standard applied where ambiguity required file reads)
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Phase 8 adds Valkey-backed idempotency to the producer and PostgreSQL persistence to the consumer, both
manually instrumented with CLIENT-kind OTel spans. The structural work is solid: span lifecycle (start
→ makeCurrent → execute → catch/recordException → finally end) is correct in both new files, semconv
attribute choices are intentional and documented, and the integration test wires three new containers
correctly.

Two correctness bugs surface under scrutiny: a try-with-resources ordering issue in
`InstrumentedJedisPool` silently swallows connection-acquisition exceptions from the span error path,
and `ProcessingService` passes `String.valueOf(null)` (`"null"`) as the `orderId` primary key to
PostgreSQL when the order map contains no `orderId` field, which will produce misleading rows instead
of a visible failure. Four warnings cover test isolation brittleness, an unchecked hard cast in the
gauge, missing `network.peer.*` attributes on the JDBC span, and the `mode=always` schema-init default
that can corrupt a shared dev database.

---

## HIGH Findings

### H-01: Scope and Jedis leased from pool in same try-with-resources — exception in getResource() skips span.recordException

**File:** `producer-service/src/main/java/com/example/producer/cache/InstrumentedJedisPool.java:90`

**Issue:** Connection acquisition failure is silently unrecorded on the span

**Detail:** Line 90 is:
```java
try (Scope scope = span.makeCurrent(); Jedis jedis = pool.getResource()) {
```
Java's try-with-resources closes resources in reverse declaration order and rolls back earlier ones
on construction failure, but the `catch (Exception e)` block at line 96 only executes when an
exception is thrown **from the body** of the try block. If `pool.getResource()` throws (e.g., pool
exhausted, Valkey unreachable at connection time), the exception propagates **past** the catch block
directly into the `finally` — `span.recordException(e)` and `span.setStatus(ERROR)` are **never
called**, and the span ends with status UNSET. The `RuntimeException` wrapping at line 99 also never
fires, so the raw Jedis exception propagates upward unwrapped, potentially exposing Jedis internals
(e.g., `JedisConnectionException`) to callers instead of the documented contract exception.

For a workshop demo this means attendees who deliberately kill Valkey will see spans with UNSET status
and no exception event — exactly the wrong teaching outcome for error handling.

**Suggested fix:** Separate resource acquisition from the OTel scope so that ANY failure is caught:
```java
public boolean setIfAbsent(String key, String value, long ttlSeconds) {
    Span span = tracer.spanBuilder("SET")
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(DbIncubatingAttributes.DB_SYSTEM_NAME,
            DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS)
        .setAttribute(io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME, "SET")
        .setAttribute(ServerAttributes.SERVER_ADDRESS, host)
        .setAttribute(ServerAttributes.SERVER_PORT, (long) port)
        .startSpan();
    try (Scope scope = span.makeCurrent()) {
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.set(key, value, SetParams.setParams().nx().ex(ttlSeconds));
            return "OK".equals(result);
        }
    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        throw new RuntimeException("Valkey SET failed for key=" + key, e);
    } finally {
        span.end();
    }
}
```
This also teaches the correct nesting: the OTel scope wraps everything, the resource handle is
inner.

---

### H-02: String.valueOf(null) produces the literal string "null" as orderId primary key

**File:** `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java:86`

**Issue:** Missing orderId in the order map silently inserts a row with `order_id = "null"` into PostgreSQL

**Detail:** Line 86 is:
```java
String orderId = String.valueOf(order.get("orderId"));
```
`String.valueOf(null)` (when the key is absent or mapped to `null`) returns the four-character string
`"null"`, not a null reference and not an exception. That string is then passed to
`repository.insertProcessedOrder("null", traceId, payloadJson)`. The `ON CONFLICT DO NOTHING` clause
means the first such message inserts a sentinel row `order_id = "null"`, and every subsequent message
with a missing orderId silently no-ops against that row. The consumer emits no error, the AMQP message
is ACK'd, and the order is lost.

This matters even in the demo because the 10%-failure path throws before reaching line 86, but any
message missing `orderId` from a consumer triggered outside the demo script (e.g., manual RabbitMQ
management UI publish) would silently accumulate under the `"null"` row.

The same variable is also re-declared at line 121 (`Object orderId`) — if a reviewer reads quickly
they may not notice the two different types of the same name in the same method.

**Suggested fix:**
```java
Object rawOrderId = order.get("orderId");
if (rawOrderId == null) {
    LOG.warn("order map missing orderId field — skipping DB persist; payload={}", order);
    return; // or throw, depending on desired semantics
}
String orderId = rawOrderId.toString();
```

---

## MEDIUM Findings

### M-01: HikariDataSource hard cast will crash at startup if a proxy DataSource wraps Hikari

**File:** `consumer-service/src/main/java/com/example/consumer/observability/HikariCpConnectionGauge.java:45`

**Issue:** Unchecked `(HikariDataSource)` cast throws `ClassCastException` if Spring wraps the DataSource

**Detail:**
```java
HikariDataSource hikariDs = (HikariDataSource) dataSource;
```
Spring Boot's `spring.sql.init.mode=always` path wraps the Hikari `DataSource` in a
`HikariDataSourceWrapper` (or, in some Actuator/LazyInitialization configurations, a
`LazyConnectionDataSourceProxy` or `DelegatingDataSource`). The hard cast will throw
`ClassCastException` at application startup — the bean construction fails, and since it is a
`@Component`, the entire application context fails to start. The null guard on `getHikariPoolMXBean()`
at line 59 never gets a chance to run.

**Suggested fix:** Unwrap via `DataSourceUnwrapper` from `spring-boot-starter-jdbc`, or use
`instanceof` with a graceful fallback:
```java
HikariDataSource hikariDs;
if (dataSource instanceof HikariDataSource hds) {
    hikariDs = hds;
} else {
    // Attempt unwrap (works through LazyConnectionDataSourceProxy etc.)
    hikariDs = dataSource.unwrap(HikariDataSource.class);
}
```
Or, more defensively, catch the failure and log a warning so the gauge simply does not register rather
than aborting startup.

---

### M-02: Integration test — AtomicInteger counter and Valkey key state leak across test methods

**File:** `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java:203`

**Issue:** `@BeforeEach` resets OTel telemetry but not the ProcessingService counter or Valkey keys — test ordering can break `tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions`

**Detail:** The `@BeforeEach resetTelemetry()` at line 203–208 resets spans/logs/metrics but does
not reset the `AtomicInteger counter` in `ProcessingService` (singleton bean) or flush Valkey keys.
JUnit 5 does not guarantee method execution order for `@Test` methods in the absence of
`@TestMethodOrder`. If `happyPathProducesSingleTrace_traceAssertions` runs after
`tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions`, the counter is already at 10+ and
the "10th order" failure will fire on a different request number in that second test — or conversely,
the failure test needs exactly 10 requests to trigger an ERROR but the counter may already be at 5
from prior tests, causing the error to fire on request 5 of 10 and the assertion on CONSUMER ERROR
to pass spuriously, or on request 15 and the test will time out waiting.

Additionally, `dbClientSpansPresentInTrace_spanAssertions` sends `"WIDGET-DB-1"` as `sku`, not as
`X-Idempotency-Key` header — the controller falls back to `payload.get("orderId")` for the
idempotency check. The `TestOrderRequest` record serializes to `{"sku":"WIDGET-DB-1","quantity":1,"priority":"standard"}`.
Since there is no `orderId` field, `idempotencyKey` is `null` and the check is skipped entirely —
so the test comment "202 Accepted = new order (idempotency cache miss)" is misleading; the Valkey
CLIENT span will NOT appear in this test run because `checkAndMark()` is never called.
This means the test assertion at line 427 (`count() >= 2` CLIENT spans) will fail — there will be
only 1 CLIENT span (the JDBC INSERT).

**Suggested fix:**
- Send an `X-Idempotency-Key` header in `dbClientSpansPresentInTrace_spanAssertions` using
  `TestRestTemplate` with `HttpEntity` and `HttpHeaders`.
- Add `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` and assign `@Order` values, or reset
  the counter via reflection / a dedicated test-only reset endpoint.

---

### M-03: spring.sql.init.mode=always runs schema.sql on every startup against a shared dev database

**File:** `consumer-service/src/main/resources/application.yaml:12`

**Issue:** `mode: always` is correct but undocumented risk — re-running against a populated database

**Detail:** `CREATE TABLE IF NOT EXISTS` in `schema.sql` makes the DDL idempotent, so re-runs do
not corrupt existing data. However, `spring.sql.init.mode=always` also processes `data.sql` if that
file exists. More importantly, workshop attendees who add `data.sql` as part of a follow-on exercise
(a common step) will see that file re-applied every restart, causing duplicate inserts. The
`application.yaml` comment explains the `mode=always` requirement but does not warn about `data.sql`
re-application risk.

For a workshop demo targeting `docker compose up` + `mise run dev` workflows this is a latent trap.

**Suggested fix:** Add a comment explicitly warning that `data.sql` (if ever added) will re-run on
every startup, and recommend scoping `spring.sql.init.mode` to a Spring profile so production-like
configs can override it:
```yaml
spring:
  sql:
    init:
      # WARNING: if data.sql is ever added, it will re-apply on every startup.
      # Use spring.profiles to restrict this to dev/test environments.
      mode: always
```

---

## LOW Findings

### L-01: OrderRepository does not set server.address/server.port on the JDBC CLIENT span

**File:** `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java:82-88`

**Issue:** OTel DB semconv requires `server.address` + `server.port` on CLIENT spans; JDBC span omits them

**Detail:** The OTel database client semconv (1.40.0 §Database Client Calls) specifies that
`server.address` and `server.port` SHOULD be set on all database CLIENT spans. `InstrumentedJedisPool`
sets them correctly (lines 87-88). `OrderRepository` does not set them, creating an inconsistency
attendees will notice when comparing the two CLIENT spans in Tempo. The Javadoc comment at line 28-38
lists four attributes but omits `server.address`/`server.port`.

The JDBC URL is available via `JdbcTemplate → DataSource → (HikariDataSource).getJdbcUrl()` parsing,
but that is complex. A simpler workshop approach is to inject the `spring.datasource.url` value via
`@Value` and parse host/port at construction time.

**Suggested fix (minimal):** Inject the URL and set the attributes:
```java
@Repository
public class OrderRepository {
    // ...
    private final String dbHost;
    private final long   dbPort;

    public OrderRepository(JdbcTemplate jdbc, Tracer tracer,
            @Value("${spring.datasource.url}") String jdbcUrl) {
        this.jdbc   = jdbc;
        this.tracer = tracer;
        // jdbc:postgresql://host:port/db
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        this.dbHost = uri.getHost();
        this.dbPort = uri.getPort() == -1 ? 5432L : uri.getPort();
    }
    // then in spanBuilder: .setAttribute(ServerAttributes.SERVER_ADDRESS, dbHost)
    //                      .setAttribute(ServerAttributes.SERVER_PORT, dbPort)
```

---

### L-02: InstrumentedJedisPool uses DB_SYSTEM_NAME from incubating namespace but DB_OPERATION_NAME from stable namespace — mixed import is visually confusing for workshop

**File:** `producer-service/src/main/java/com/example/producer/cache/InstrumentedJedisPool.java:84-86`

**Issue:** Two different attribute namespaces used in four consecutive lines without a clear visual boundary

**Detail:** Lines 84-86:
```java
.setAttribute(DbIncubatingAttributes.DB_SYSTEM_NAME,          // incubating namespace
    DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS)
.setAttribute(io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME, "SET")  // stable namespace (FQCN)
```
`DB_OPERATION_NAME` is pulled via FQCN (`io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME`)
rather than a top-level import, while `DB_SYSTEM_NAME` uses the imported incubating class. The
comment above explains the split but the code itself presents as inconsistent: one attribute from a
named import, the next from a FQCN inline reference. For a workshop codebase that is explicitly a
reading text, this creates unnecessary visual noise.

Incubating `DbIncubatingAttributes` also exposes `DB_OPERATION_NAME`, so the split is not even
driven by availability — it just happened to be wired via the stable artifact here.

**Suggested fix:** Add a top-level import for `io.opentelemetry.semconv.DbAttributes` and use it
consistently:
```java
import io.opentelemetry.semconv.DbAttributes;
// ...
.setAttribute(DbIncubatingAttributes.DB_SYSTEM_NAME, DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS)
.setAttribute(DbAttributes.DB_OPERATION_NAME, "SET")
.setAttribute(ServerAttributes.SERVER_ADDRESS, host)
.setAttribute(ServerAttributes.SERVER_PORT, (long) port)
```

---

### L-03: docker-compose.yml postgres credentials are hardcoded defaults ("orders"/"orders") without a comment discouraging reuse in non-workshop environments

**File:** `docker-compose.yml:44-45`

**Issue:** Weak/shared password in a public repo with no disclaimer

**Detail:** `POSTGRES_PASSWORD: orders` is the same as the username and database name — trivially
guessable. For a workshop-only local demo this is explicitly acceptable, but the repo is described
as "public-readable". No comment in the compose file warns that these are workshop-only values. If
an attendee copies the compose file to a staging environment the credentials ship as-is.

The `application.yaml` correctly gates the password behind `${SPRING_DATASOURCE_PASSWORD:orders}`
(the env-var fallback is the workshop default), which is the right pattern. The compose file could
carry a matching note.

**Suggested fix:** Add an inline comment:
```yaml
environment:
  POSTGRES_DB: orders
  POSTGRES_USER: orders
  POSTGRES_PASSWORD: orders  # workshop-only default — override POSTGRES_PASSWORD for any non-local env
```

---

## Summary

| Severity | Count | Items |
|----------|-------|-------|
| HIGH     | 2     | H-01 (Jedis exception swallowed by span), H-02 (String.valueOf(null) → silent "null" PK) |
| MEDIUM   | 3     | M-01 (HikariDataSource hard cast), M-02 (test isolation + Valkey CLIENT span missing from TEST-08-01), M-03 (mode=always data.sql risk) |
| LOW      | 3     | L-01 (JDBC span missing server.address/port), L-02 (mixed import style), L-03 (postgres password comment) |

**Overall verdict: NEEDS-FIX**

H-02 and M-02 are the most urgent: H-02 is a correctness bug that causes silent data loss under
normal workshop traffic (any message without an explicit `orderId` field), and M-02 means the new
`dbClientSpansPresentInTrace_spanAssertions` test will fail in CI because the Valkey CLIENT span
is never emitted when the test request body lacks an `orderId` / `X-Idempotency-Key`. H-01 degrades
error observability in a way that directly undermines the workshop's teaching objective for CLIENT
spans. M-01 is a startup crasher in environments where Spring wraps the DataSource. None of these
require architectural rework — all four have targeted line-level fixes.

---

_Reviewed: 2026-05-02_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: quick/standard_
