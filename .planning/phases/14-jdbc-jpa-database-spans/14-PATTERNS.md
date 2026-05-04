# Phase 14: JDBC/JPA Database Spans - Pattern Map

**Mapped:** 2026-05-04
**Files analyzed:** 13 (9 new/edited Java + config, 2 deleted, 1 mise.toml task, 1 README section, 1 integration test update)
**Analogs found:** 11 / 11

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `consumer-service/src/main/java/com/example/consumer/db/Order.java` | model | CRUD | _(no JPA entity exists; use RESEARCH.md Pattern 3)_ | none |
| `consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java` | repository | CRUD | `consumer-service/.../db/OrderRepository.java` | role-match |
| `consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java` | service | CRUD | `consumer-service/.../domain/ProcessingService.java` | role-match |
| `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java` | middleware/interceptor | request-response | `consumer-service/.../db/OrderRepository.java` | exact (D-01 CLIENT span pattern) |
| `consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java` | middleware/interceptor | request-response | `otel-bootstrap/.../amqp/TracingMessageListenerAdvice.java` | role-match (INTERNAL span + catch) |
| `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` _(edit)_ | service | request-response | self (existing file) | exact |
| `consumer-service/pom.xml` _(edit)_ | config | — | self (existing pom.xml with Phase 8 JDBC block) | exact |
| `consumer-service/src/main/resources/application.yaml` _(edit)_ | config | — | self (existing application.yaml) | exact |
| `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` _(DELETE)_ | — | — | — | — |
| `consumer-service/src/main/resources/schema.sql` _(DELETE)_ | — | — | — | — |
| `mise.toml` _(additive)_ | config | — | `mise.toml` `verify:tail-sampling` / `verify:log-metrics` tasks | exact |
| `README.md` _(additive §14)_ | docs | — | README §11 / §12 / §13 sections | exact |
| `integration-tests/.../OrderFlowIT.java` _(edit test 5)_ | test | CRUD | self (existing test 5, lines 413-483) | exact |

---

## Pattern Assignments

### `consumer-service/src/main/java/com/example/consumer/db/Order.java` (model, CRUD)

**Analog:** No JPA entity exists in the codebase. Use RESEARCH.md Pattern 3 directly.

**Imports pattern** (copy from RESEARCH.md Pattern 3):
```java
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
```

**Core pattern** (RESEARCH.md Pattern 3, with CONTEXT.md D-J7 / D-J8):
- `@Entity` + `@Table(name = "orders")` — new table, NOT `processed_orders`
- Surrogate `Long id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Business key `orderId` with `@Column(name = "order_id", unique = true, nullable = false)`
- `payload` as `@Column(columnDefinition = "TEXT")` — TEXT not JSONB (no `AttributeConverter` complexity)
- `processedAt` as `Instant` with `@Column(name = "processed_at", nullable = false)`
- `traceId` as `String` with `@Column(name = "trace_id", length = 64)` — D-J8 bridge column
- `protected Order() {}` no-arg constructor required by JPA
- Public factory constructor takes all fields; getters only — immutable after construction (coding-style.md)

**No analog to copy — follow RESEARCH.md Pattern 3 verbatim.**

---

### `consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java` (repository, CRUD)

**Analog:** `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java`
_(The JDBC repository being replaced — provides the package convention and `@Repository` placement)_

**Imports pattern** (no parallel JPA interface exists; use Spring Data pattern):
```java
package com.example.consumer.db;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
```

**Core pattern** — interface only, no implementation:
```java
// Spring Data generates the implementation; no @Repository annotation needed
// (Spring Data JPA auto-detects interfaces extending JpaRepository)
public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    // Derived query method: generates SELECT ... WHERE order_id = ?
    // Used by OrderJpaService.persist() for the D-J3/D-J4 idempotency check
    Optional<Order> findByOrderId(String orderId);
}
```

**Package placement:** Same `com.example.consumer.db` package as the deleted `OrderRepository.java`.

---

### `consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java` (service, CRUD)

**Analog:** `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`
_(Same `@Service` singleton pattern, constructor injection of `Tracer`, Spring-managed lifecycle)_

**Imports pattern** (from `ProcessingService.java` lines 1-18, adapted):
```java
package com.example.consumer.db;

import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
```

**Core pattern** — `@Transactional` wraps both repository calls; `TransactionSpanAspect` wraps this method via AOP:
```java
@Service
public class OrderJpaService {

    private final OrderJpaRepository repository;

    // Constructor injection — same pattern as ProcessingService (no @Autowired field)
    public OrderJpaService(OrderJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void persist(String orderId, String payload, String traceId) {
        // D-J3 + D-J4: check existence first (SELECT span from TracingRepositoryAspect),
        // save only if not found (INSERT span from TracingRepositoryAspect)
        Optional<Order> existing = repository.findByOrderId(orderId);
        if (existing.isEmpty()) {
            repository.save(new Order(orderId, payload, Instant.now(), traceId));
        }
        // if present → idempotent skip; mirrors Phase 8's ON CONFLICT DO NOTHING
    }
}
```

**Note:** `OrderJpaService` does NOT inject `Tracer`. The `TransactionSpanAspect` creates the INTERNAL span around this method via AOP. `TracingRepositoryAspect` creates CLIENT spans around the repository calls. This keeps `OrderJpaService` clean — pure business logic.

---

### `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java` (middleware, request-response)

**Analog:** `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java`
_(The Phase 8 JDBC repository — provides the exact D-01 CLIENT span template to replicate in AOP form)_

**Imports pattern** (from `OrderRepository.java` lines 1-13, extended with AOP imports):
```java
package com.example.consumer.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.DbAttributes;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
```

**Core D-01 CLIENT span pattern** (from `OrderRepository.java` lines 82-100, wrapped in `@Around`):

The `OrderRepository.java` D-01 inline span template (lines 82–100) is the DIRECT source for the aspect body. The aspect wraps this template around `pjp.proceed()` instead of `jdbc.update()`:

```java
// From OrderRepository.java lines 82-100 — the D-01 template to replicate:
Span span = tracer.spanBuilder("INSERT " + TABLE)
    .setSpanKind(SpanKind.CLIENT)
    .setAttribute(DbAttributes.DB_SYSTEM_NAME, DbAttributes.DbSystemNameValues.POSTGRESQL)
    .setAttribute(DbAttributes.DB_OPERATION_NAME, "INSERT")
    .setAttribute(DbAttributes.DB_COLLECTION_NAME, TABLE)
    .setAttribute(DbAttributes.DB_QUERY_TEXT, INSERT_SQL)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    jdbc.update(INSERT_SQL, orderId, traceId, payloadJson);  // ← becomes pjp.proceed()
} catch (Exception e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    span.end();
}
```

**Phase 14 adaptation** — span name (D-J6) and `db.query.text` (D-J5) are JPA-method-based, not SQL-based:
- Span name: `"OrderJpaRepository." + pjp.getSignature().getName()` (e.g., `"OrderJpaRepository.findByOrderId"`)
- `db.query.text`: `"JpaRepository." + pjp.getSignature().getName() + "(" + resolveArgs(pjp.getArgs()) + ")"` (e.g., `"JpaRepository.findByOrderId(orderId)"`)
- `db.operation.name`: resolved from method prefix (`find*` → `"SELECT"`, `save` → `"INSERT"`)
- `db.collection.name`: `"orders"` (the `@Table(name="orders")` value per D-J7)
- `db.namespace`: `"orders"` (PostgreSQL database name from datasource URL per semconv 1.40.0)
- `db.system.name`: `DbAttributes.DbSystemNameValues.POSTGRESQL` (same constant as Phase 8)

**Pointcut** (RESEARCH.md Pattern 1 — use `bean()` not `execution()` on interface):
```java
@Around("bean(*Repository) && execution(public * *(..))")
public Object traceRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable { ... }
```

**Critical:** Always use `DbAttributes.DB_QUERY_TEXT` constant, never the string literal `"db.statement"` (F5-2 mitigation from `OrderRepository.java` line 87).

**Constructor injection pattern** (from `OrderRepository.java` line 57):
```java
// From OrderRepository.java lines 54-59:
private final Tracer tracer;

public TracingRepositoryAspect(Tracer tracer) {
    this.tracer = tracer;
}
```

---

### `consumer-service/src/main/java/com/example/consumer/observability/TransactionSpanAspect.java` (middleware, request-response)

**Analog:** `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java`
_(Provides the INTERNAL span lifecycle: spanBuilder + try(Scope) + catch(Throwable) + finally span.end(); the closest existing wrapping-interceptor pattern)_

**Imports pattern** (from `TracingMessageListenerAdvice.java` lines 1-14, adapted):
```java
package com.example.consumer.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
```

**INTERNAL span pattern** (adapted from `TracingMessageListenerAdvice.java` lines 116-141):

The `TracingMessageListenerAdvice` span lifecycle (lines 116–141) is the source for the transaction span body — replace `inv.proceed()` with `pjp.proceed()` and set `SpanKind.INTERNAL`:

```java
// From TracingMessageListenerAdvice.java lines 116-141 — adapted for INTERNAL span:
Span span = tracer.spanBuilder(exchange + " process")  // → "OrderJpaService.persist"
    .setSpanKind(SpanKind.CONSUMER)                    // → SpanKind.INTERNAL
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    return inv.proceed();                               // → pjp.proceed()
} catch (Throwable t) {                                // same catch(Throwable) shape
    span.recordException(t);
    span.setStatus(StatusCode.ERROR);                  // rollback visible as ERROR in Tempo
    throw t;
} finally {
    span.end();
}
```

**`@Order` annotation** (F5-3 mitigation — outer wrapping, not inner):
```java
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // Integer.MIN_VALUE = wraps OUTSIDE @Transactional proxy
public class TransactionSpanAspect { ... }
```

**Pointcut** (RESEARCH.md Pattern 2 — concrete class, `execution()` is reliable):
```java
@Around("execution(* com.example.consumer.db.OrderJpaService.persist(..))")
public Object traceTransactionBoundary(ProceedingJoinPoint pjp) throws Throwable { ... }
```

**Constructor injection** (same pattern as `TracingMessageListenerAdvice.java` lines 91-94):
```java
private final Tracer tracer;

public TransactionSpanAspect(Tracer tracer) {
    this.tracer = tracer;
}
```

---

### `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` (edit)

**Analog:** Self — existing file `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`

**Edit target** — line 96 (replace `OrderRepository` call with `OrderJpaService` call):

```java
// BEFORE (Phase 8, line 96):
repository.insertProcessedOrder(orderId, traceId, payloadJson);

// AFTER (Phase 14):
jpaService.persist(orderId, payloadJson, traceId);
// Note: arg order changes — ProcessingService passes (orderId, payloadJson, traceId)
// matching OrderJpaService.persist(String orderId, String payload, String traceId)
```

**Constructor change** (lines 61-65):
```java
// BEFORE:
public ProcessingService(Tracer tracer, OrderRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
// AFTER:
public ProcessingService(Tracer tracer, OrderJpaService jpaService, ObjectMapper objectMapper) {
    this.jpaService = jpaService;
```

**Field change:**
```java
// BEFORE (line 52):
private final OrderRepository repository;
// AFTER:
private final OrderJpaService jpaService;
```

**Import change:**
```java
// REMOVE:
import com.example.consumer.db.OrderRepository;
// ADD:
import com.example.consumer.db.OrderJpaService;
```

**Preserve unchanged:** The entire D-01 INTERNAL span template (lines 68-136), the 10% failure counter logic, and the outer catch/finally structure remain untouched.

---

### `consumer-service/pom.xml` (edit)

**Analog:** Self — existing file, specifically the Phase 8 JDBC block (lines 154-174)

**Pattern to follow** — Phase 8 JDBC comment style and placement:

```xml
<!-- From pom.xml lines 154-174 (Phase 8 block) — follow this comment style: -->
<!--
  Phase 14: Spring Data JPA — replaces spring-boot-starter-jdbc (which becomes
  transitively redundant; this dep is removed when JPA is added).
  spring-boot-starter-data-jpa is BOM-managed by Spring Boot 3.4.13; pulls
  Hibernate 6.x + Spring Data JPA 3.x + HikariCP 5.x + aspectjweaver
  (via spring-boot-starter-aop). No <version> tag needed.
  @EnableAspectJAutoProxy is activated automatically by AopAutoConfiguration.
-->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**Remove** (becomes redundant — `starter-data-jpa` transitively pulls it):
```xml
<!-- REMOVE — now transitively provided by spring-boot-starter-data-jpa -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

**Keep unchanged:** PostgreSQL driver dependency (lines 164-173), all OTel deps, all other Spring Boot starters.

---

### `consumer-service/src/main/resources/application.yaml` (edit)

**Analog:** Self — existing file (lines 1-17)

**Current state** (lines 1-17):
```yaml
spring:
  application:
    name: order-consumer
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/orders}
    username: ${SPRING_DATASOURCE_USERNAME:orders}
    password: ${SPRING_DATASOURCE_PASSWORD:orders}
  sql:
    init:
      mode: always   # ← REMOVE per D-J2
management:
  endpoints:
    web:
      exposure:
        include: health
```

**After edit** (remove `spring.sql.init` block, add `spring.jpa` block per RESEARCH.md Pattern 4):
```yaml
spring:
  application:
    name: order-consumer
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/orders}
    username: ${SPRING_DATASOURCE_USERNAME:orders}
    password: ${SPRING_DATASOURCE_PASSWORD:orders}
  # Phase 14: JPA config replaces spring.sql.init (D-J2: JPA owns DDL)
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        dialect: org.hibernate.dialect.PostgreSQLDialect
management:
  endpoints:
    web:
      exposure:
        include: health
```

**Key removals:** `spring.sql.init.mode: always` — D-J2 requires this to be deleted; JPA/Hibernate manages DDL via `ddl-auto: update`.

---

### `mise.toml` (additive `verify:jpa-spans` task)

**Analog:** `mise.toml` existing `[tasks."verify:tail-sampling"]` block (lines 353-461) and `[tasks."verify:log-metrics"]` block (lines 465-575)

**Pattern to copy** (from `verify:tail-sampling` lines 353-365, and `verify:log-metrics` lines 465-480):

Header/placement convention:
```toml
# Phase 14 — verify:jpa-spans (DBSP-05)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:jpa-spans"]
description = "Phase 14 invariant: db.query.text attribute present in Tempo spans for consumer-service JPA operations"
```

Retry loop pattern (from `verify:tail-sampling` lines 378-410):
```toml
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
# ... retry loop body (6×5s = 30s tolerance) ...
"""
```

Tier structure: single-tier (Tempo search for `db.query.text=*`), following the compact single-tier pattern from `verify:exemplars` (lines 465-498) rather than the two-tier pattern from `verify:tail-sampling`.

Tempo search URL pattern (from `verify:tail-sampling` lines 429-430):
```bash
curl -fsS 'http://localhost:3200/api/search?tags=db.query.text%3D*&limit=5&service=order-consumer'
```

Exit message convention (from `verify:tail-sampling` line 461):
```bash
echo "verify:jpa-spans: GREEN — db.query.text spans present in Tempo for order-consumer."
```

**Placement:** After `[tasks."verify:log-metrics"]` block, following the Phase N chronological ordering pattern.

---

### `README.md` (additive §14 section)

**Analog:** README §11 (lines 542-625), §12 (lines 624-715), §13 (lines 716+)

**Structure to copy** (from §11 pattern, lines 542-570):
```markdown
## Step 14: JPA Database Spans — transaction parent + SELECT/INSERT waterfall

### What you'll learn

- [3-4 bullet points]

### Checkpoint

Workshop is at `step-14-jpa-spans` — ...
`git diff step-13-log-metrics..step-14-jpa-spans` shows ...

### Run

```bash
mise run preflight
mise run infra:up
mise run dev
mise run load
mise run verify:jpa-spans
mise run ui:grafana
```

### What to look for

<table> ... screenshot pair ... </table>

[narrative with Phase 8 contrast callout]
```

**Screenshot placeholder** (from §11 `<table>` pattern at lines 578-587): Use a two-column table showing Phase 8 single span vs Phase 14 waterfall. Image path convention: `docs/screenshots/step-14-jpa.png`.

**Length target:** 100-150 lines, matching §11/§12/§13 precedent (~85-100 lines body).

**Phase 8 contrast narrative** (from CONTEXT.md `<specifics>` bullets): Must include:
- "Phase 8 named spans after SQL verbs (`INSERT processed_orders`); Phase 14 names spans after repository methods (`OrderJpaRepository.findByOrderId`) because JPA hides the SQL."
- "In Phase 8, a hand-written `schema.sql` created the table. In Phase 14, the entity annotations are the schema — Hibernate generates the DDL."
- "Compare tags: `git diff step-08-db-cache..step-14-jpa-spans`"

---

### `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` (edit test 5)

**Analog:** Self — existing file, test 5 `dbClientSpansPresentInTrace_spanAssertions()` (lines 413-483)

**Current test 5 pattern to update** (lines 413-483) — preserve structure, update assertions:

```java
// BEFORE (lines 432-436): waits for 2 CLIENT spans (Valkey + JDBC)
Awaitility.await()
    .atMost(Duration.ofSeconds(15))
    .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().stream()
        .filter(s -> s.getKind() == SpanKind.CLIENT)
        .count() >= 2);

// AFTER (Phase 14): waits for 3+ CLIENT spans (Valkey + JPA findByOrderId + JPA save)
Awaitility.await()
    .atMost(Duration.ofSeconds(15))
    .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().stream()
        .filter(s -> s.getKind() == SpanKind.CLIENT)
        .count() >= 3);
```

```java
// BEFORE (lines 462-476): asserts db.collection.name="processed_orders"
assertThat(jdbcSpan)
    .hasAttribute(equalTo(
        io.opentelemetry.api.common.AttributeKey.stringKey("db.operation.name"), "INSERT"))
    .hasAttribute(equalTo(
        io.opentelemetry.api.common.AttributeKey.stringKey("db.collection.name"), "processed_orders"));

// AFTER (Phase 14): assert INTERNAL transaction span present + JPA CLIENT spans with new table
// Transaction INTERNAL span (TransactionSpanAspect):
SpanData txnSpan = spans.stream()
    .filter(s -> s.getKind() == SpanKind.INTERNAL
        && "OrderJpaService.persist".equals(s.getName()))
    .findFirst()
    .orElseThrow(...);

// JPA findByOrderId CLIENT span:
SpanData jpaFindSpan = clientSpans.stream()
    .filter(s -> "OrderJpaRepository.findByOrderId".equals(s.getName()))
    .findFirst().orElseThrow(...);
assertThat(jpaFindSpan)
    .hasAttribute(equalTo(AttributeKey.stringKey("db.operation.name"), "SELECT"))
    .hasAttribute(equalTo(AttributeKey.stringKey("db.collection.name"), "orders"))   // D-J7
    .hasAttribute(equalTo(AttributeKey.stringKey("db.namespace"), "orders"));

// JPA save CLIENT span (new order path):
SpanData jpaSaveSpan = clientSpans.stream()
    .filter(s -> "OrderJpaRepository.save".equals(s.getName()))
    .findFirst().orElseThrow(...);
assertThat(jpaSaveSpan)
    .hasAttribute(equalTo(AttributeKey.stringKey("db.operation.name"), "INSERT"))
    .hasAttribute(equalTo(AttributeKey.stringKey("db.collection.name"), "orders"));
```

**Test 4 update** (`tenthOrderProducesErrorSpanAndErrorLog_failurePathAssertions`, lines 368-408):
Add assertion that the INTERNAL `OrderJpaService.persist` span from `TransactionSpanAspect` also carries `status=ERROR` on the 10th order rollback (DBSP-04).

**`AttributeKey` import pattern** (from existing test lines 17, 453-454): Use `io.opentelemetry.api.common.AttributeKey.stringKey(...)` inline or add a static import — match the existing test's style.

---

## Shared Patterns

### D-01 Inline Span Template (CLIENT)

**Source:** `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` lines 82-100
**Apply to:** `TracingRepositoryAspect.java` (wraps each `@Around` proceeding join point)

```java
// The exact D-01 template from OrderRepository.java lines 82-100:
Span span = tracer.spanBuilder("<span-name>")
    .setSpanKind(SpanKind.CLIENT)
    .setAttribute(DbAttributes.DB_SYSTEM_NAME, DbAttributes.DbSystemNameValues.POSTGRESQL)
    .setAttribute(DbAttributes.DB_OPERATION_NAME, "<SELECT|INSERT>")
    .setAttribute(DbAttributes.DB_COLLECTION_NAME, "<table-name>")
    .setAttribute(DbAttributes.DB_QUERY_TEXT, <queryText>)   // F5-2: typed constant, not string literal
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    <work>;                 // ← pjp.proceed() in aspect form
} catch (Exception e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    span.end();
}
```

### INTERNAL Span with Error Catch

**Source:** `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` lines 116-141
**Apply to:** `TransactionSpanAspect.java` (wraps `OrderJpaService.persist()` boundary)

```java
// From TracingMessageListenerAdvice.java lines 116-141 (adapted for INTERNAL):
Span span = tracer.spanBuilder("OrderJpaService.persist")
    .setSpanKind(SpanKind.INTERNAL)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    return pjp.proceed();
} catch (Throwable t) {
    span.recordException(t);
    span.setStatus(StatusCode.ERROR);
    throw t;
} finally {
    span.end();
}
```

### Constructor-Injected `Tracer`

**Source:** `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` lines 54-59
**Apply to:** `TracingRepositoryAspect.java`, `TransactionSpanAspect.java`

```java
// From OrderRepository.java lines 54-59:
private final Tracer tracer;

public OrderRepository(JdbcTemplate jdbc, Tracer tracer) {  // ← just Tracer for aspect
    this.tracer = tracer;
}
```

Never use `GlobalOpenTelemetry.getTracer()` — constructor injection is the project standard.

### DbAttributes Semconv Constants

**Source:** `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` lines 8, 84-87
**Apply to:** `TracingRepositoryAspect.java`

```java
// From OrderRepository.java — the exact imports and constant usage:
import io.opentelemetry.semconv.DbAttributes;

DbAttributes.DB_SYSTEM_NAME          → DbAttributes.DbSystemNameValues.POSTGRESQL
DbAttributes.DB_OPERATION_NAME       → "SELECT" or "INSERT" (string value)
DbAttributes.DB_COLLECTION_NAME      → "orders"  (D-J7 table name)
DbAttributes.DB_QUERY_TEXT           → "JpaRepository.findByOrderId(orderId)" (D-J5)
DbAttributes.DB_NAMESPACE            → "orders"  (PostgreSQL database name, semconv 1.40.0)
```

**Critical:** `DB_QUERY_TEXT` not `"db.statement"` — F5-2. Phase 8's `OrderRepository.java` line 87 already uses the correct constant; copy it exactly.

### `@Component` + Spring DI for Aspects

**Source:** `consumer-service/src/main/java/com/example/consumer/observability/HikariCpConnectionGauge.java` lines 39-44
**Apply to:** Both aspect classes (`TracingRepositoryAspect`, `TransactionSpanAspect`)

```java
// From HikariCpConnectionGauge.java lines 39-44 — @Component + constructor injection pattern:
@Component
public class HikariCpConnectionGauge {
    public HikariCpConnectionGauge(Meter meter, DataSource dataSource) { ... }
```

Both aspects must be `@Component` (so Spring discovers them as beans) AND `@Aspect` (so Spring AOP processes the advice annotations). `@EnableAspectJAutoProxy` is NOT needed — `AopAutoConfiguration` handles it when `aspectjweaver` is on the classpath (pulled transitively by `spring-boot-starter-data-jpa` → `spring-boot-starter-aop`).

### Mise Task Structure

**Source:** `mise.toml` `[tasks."verify:tail-sampling"]` lines 353-461 and `[tasks."verify:log-metrics"]` lines 465-575

```toml
# Pattern: 6-attempt retry loop, 5s sleep, clear ERROR message with diagnosis steps
[tasks."verify:<name>"]
description = "Phase N invariant: ..."
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""
for i in $(seq 1 $ATTEMPTS); do
  RESULT=$(curl -fsS '<url>' 2>&1) || { ... retry or exit ... }
  if <check passes>; then break; fi
  [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: ..."
  exit 1
done
echo "verify:<name>: GREEN — ..."
"""
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `consumer-service/src/main/java/com/example/consumer/db/Order.java` | model | CRUD | No JPA entity exists in the codebase. Use RESEARCH.md Pattern 3 verbatim. |
| `consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java` | repository | CRUD | No Spring Data JPA repository interface exists. Pattern provided inline above. |

---

## Pitfall Cross-Reference (for planner)

| Pitfall | Source | Mitigation in Pattern Assignment |
|---------|--------|----------------------------------|
| F5-1: Wrap at repository level, not SQL level | `.planning/research/PITFALLS.md` | `TracingRepositoryAspect` uses `bean(*Repository) && execution(public * *(..))` pointcut |
| F5-2: `DbAttributes.DB_QUERY_TEXT` not `"db.statement"` | `OrderRepository.java` line 87 | Shared pattern "DbAttributes Semconv Constants" section above |
| F5-3: Transaction span OUTSIDE `@Transactional` proxy | RESEARCH.md Pitfall 2 | `@Order(Ordered.HIGHEST_PRECEDENCE)` on `TransactionSpanAspect` |
| X-1: Circular reference — verify before adding new @Bean | 10-CONTEXT.md D-01 | Both aspects inject `Tracer` only; neither is injected INTO `OtelSdkConfiguration` |
| A2: `bean(*Repository)` may not intercept derived query methods | RESEARCH.md Assumption A2 | Fallback: inline D-01 span template directly in `OrderJpaService.persist()` wrapping each repository call |

---

## Metadata

**Analog search scope:** `consumer-service/src/main/java/`, `otel-bootstrap/src/main/java/`, `integration-tests/src/test/java/`, `mise.toml`, `README.md`
**Files scanned:** 11 source files + mise.toml + README.md
**Pattern extraction date:** 2026-05-04
