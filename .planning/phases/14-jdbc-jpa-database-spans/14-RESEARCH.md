# Phase 14: JDBC/JPA Database Spans - Research

**Researched:** 2026-05-04
**Domain:** Spring Data JPA + Spring AOP + OTel semconv `db.*` attributes (consumer-service only)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-J1:** Replace Phase 8 raw-JDBC `OrderRepository.java` with JPA entirely. No coexistence. Pedagogical contrast via git tags.
- **D-J2:** JPA owns DDL. Delete `schema.sql`. Set `spring.jpa.hibernate.ddl-auto=update`. Remove `spring.sql.init.mode=always`.
- **D-J3:** `findById` + `save` = 2 child CLIENT spans. Check existence first (SELECT), persist only if new (INSERT).
- **D-J4:** Idempotent at the JPA layer: if `findById` returns the entity, skip `save()`.
- **D-J5:** `db.query.text` carries JPA method description, e.g. `"JpaRepository.findById(orderId)"` and `"JpaRepository.save(Order)"` — NOT generated SQL.
- **D-J6:** Span names are JPA method-based: `"OrderJpaRepository.findById"` and `"OrderJpaRepository.save"`.
- **D-J7:** New `orders` table with `@Table(name = "orders")`. Columns: `id` (Long, `@GeneratedValue`), `orderId` (String, `@Column(unique = true)`), `payload` (String), `processedAt` (Instant), `traceId` (String).
- **D-J8:** `traceId` column carries `Span.current().getSpanContext().getTraceId()` at persist time.

### Claude's Discretion

- Exact `@Around` pointcut expression for the repository tracing aspect
- Exact `@Order` value and mechanism for transaction-span aspect vs `@Transactional` proxy ordering
- Whether `db.namespace` maps to PostgreSQL database name or schema
- Exact `db.operation.name` values for findById/save
- `db.collection.name` value
- `HikariCpConnectionGauge` fate with JPA
- Transaction-span aspect implementation pattern (`@Aspect` class vs method-level wrapping)
- `application.yaml` Hibernate dialect, show-sql, format-sql settings
- Whether `@Column(columnDefinition = "jsonb")` or `@Convert` is used for the payload field
- README §14 exact wording, length, and structure
- `verify:jpa-spans` exact bash implementation

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DBSP-01 | Add `spring-boot-starter-data-jpa` to `consumer-service/pom.xml` (BOM-managed) | `spring-boot-starter-data-jpa` in Spring Boot 3.4.13 BOM; `spring-boot-starter-jdbc` becomes redundant (transitively pulled) |
| DBSP-02 | `Order.java` entity, `OrderJpaRepository`, `OrderJpaService` exist; `@RabbitListener` path calls `OrderJpaService.persist(...)` | Standard Spring Data JPA pattern; `ProcessingService.java` integration point at line ~96 (replace `repository.insertProcessedOrder(...)`) |
| DBSP-03 | Each repository method call wrapped in manual `SpanKind.CLIENT` span with full `db.*` semconv set | Semconv 1.40.0 `DbAttributes` constants; PITFALL F5-2 (use `DbAttributes.DB_QUERY_TEXT`, not string literal) |
| DBSP-04 | Transaction-level INTERNAL span wraps `@Transactional` boundary as parent; 10% failure rollback surfaces as `status=ERROR`; `@Order(HIGHEST_PRECEDENCE)` on aspect | `@Transactional` default order = `LOWEST_PRECEDENCE`; custom aspect `@Order(HIGHEST_PRECEDENCE)` = outer wrapper; PITFALL F5-3 |
| DBSP-05 | Tempo search for `db.query.text=*` returns consumer spans with SELECT/INSERT child waterfall; README screenshot placeholder | AOP aspect approach; verify:jpa-spans mise task follows verify:tail-sampling pattern |
</phase_requirements>

---

## Summary

Phase 14 replaces the Phase 8 raw-JDBC `OrderRepository` (single INSERT span using `JdbcTemplate`) with a full Spring Data JPA stack: an `Order` entity, `OrderJpaRepository` interface, `OrderJpaService`, and two AOP aspects that manually emit OTel spans without any auto-instrumentation. The teaching surface is the contrast between "JdbcTemplate: 1 span, SQL visible" (Phase 8, git tag `step-08-db-cache`) and "JPA: transaction parent + 2 child spans, SQL hidden by ORM abstraction" (Phase 14, git tag `step-14-jpa-spans`).

The critical technical decisions resolved by this research are: (1) the `@Around` pointcut expression to reliably intercept Spring Data JPA repository proxy methods, (2) the `@Order` mechanism ensuring the tracing aspect wraps outside the `@Transactional` proxy, (3) the exact semconv attribute values for `db.namespace`, `db.operation.name`, and `db.collection.name` for the JPA-over-PostgreSQL stack, and (4) the AOP architecture (two separate `@Aspect` classes — one for CLIENT repository spans, one for the INTERNAL transaction span).

The `HikariCpConnectionGauge` survives unchanged: Spring Data JPA still uses HikariCP as the connection pool under Spring Boot auto-config. The `spring-boot-starter-jdbc` dependency becomes transitively redundant when `spring-boot-starter-data-jpa` is added but is harmless to leave or remove.

**Primary recommendation:** Use `bean(*Repository) && execution(public * *(..))` as the repository aspect pointcut (reliable for Spring Data JPA proxy beans), implement the transaction-span aspect as a separate `@Aspect` class on `OrderJpaService.persist(...)` with `@Order(Ordered.HIGHEST_PRECEDENCE)`, and map `db.namespace` to the PostgreSQL database name (`orders`) extracted from the datasource URL.

---

## Project Constraints (from CLAUDE.md)

| Directive | Source | Impact on Phase 14 |
|-----------|--------|---------------------|
| Spring Boot 3.4.13 (pinned) | CLAUDE.md | `spring-boot-starter-data-jpa` version managed by BOM; no version override |
| Java 17 (pinned) | CLAUDE.md | No impact on JPA or AOP API selection |
| OpenTelemetry Java SDK 1.61.0 manual only — no Java agent | CLAUDE.md | All spans hand-coded via `tracer.spanBuilder()`; no Hibernate interceptors or auto-instrumentation |
| No `opentelemetry-spring-boot-starter` | CLAUDE.md | Spans must be created explicitly in code, not via Spring Boot OTel auto-config |
| No `micrometer-tracing-bridge-otel` | CLAUDE.md | No Micrometer abstraction layer; raw OTel API throughout |
| Spring AMQP (`spring-boot-starter-amqp`) | CLAUDE.md | Not affected by Phase 14 |
| Semconv 1.40.0 stable + 1.40.0-alpha incubating | CLAUDE.md | Use `DbAttributes.DB_QUERY_TEXT` (stable), `DbAttributes.DB_SYSTEM_NAME` (stable) — not the deprecated string literals |
| `mise` for JDK + Maven | CLAUDE.md | `verify:jpa-spans` task added to `mise.toml` |
| JUnit 5 + Testcontainers | CLAUDE.md | Integration test updates follow Phase 6 pattern |
| Per-service `OtelSdkConfiguration.java` duplication (TRACE-01 / DOC-05) | CLAUDE.md | Phase 14 does NOT touch `OtelSdkConfiguration` (CONTEXT.md explicit: no new providers needed) |

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| JPA entity persistence | Database / Storage | — | `Order` entity + `OrderJpaRepository` own the persistence boundary |
| Repository method tracing (CLIENT spans) | API / Backend | — | `TracingRepositoryAspect` wraps outbound calls to PostgreSQL via JPA proxy |
| Transaction boundary tracing (INTERNAL span) | API / Backend | — | `TransactionSpanAspect` wraps the `@Transactional` service-layer boundary |
| Connection pool metrics | API / Backend | — | `HikariCpConnectionGauge` persists unchanged; JPA still uses HikariCP |
| DDL schema management | Database / Storage | — | JPA/Hibernate `ddl-auto=update` replaces `schema.sql`; no docker-compose change |
| Rollback error surfacing | API / Backend | — | Transaction span aspect captures rollback as `status=ERROR` on the outermost span |

---

## Standard Stack

### Core (Phase 14 additions)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-data-jpa` | BOM-managed (Spring Boot 3.4.13) | Spring Data JPA repositories, Hibernate ORM, `JpaRepository` interface | BOM-managed; pulls Hibernate 6.x + Spring Data JPA 3.x + HikariCP 5.x |
| `org.springframework.boot:spring-boot-starter-aop` | BOM-managed (transitively from `starter-data-jpa`) | Enables Spring AOP proxy infrastructure; brings `org.aspectj:aspectjweaver` | Transitive dep of `starter-data-jpa`; provides `@Aspect`, `@Around`, `@Order` support |
| `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` | Already pinned in pom.xml | `DbAttributes.DB_QUERY_TEXT`, `DbAttributes.DB_SYSTEM_NAME`, `DbAttributes.DB_OPERATION_NAME`, `DbAttributes.DB_COLLECTION_NAME` | Already on classpath from Phase 8; semconv is stable at 1.40.0 |

### Already Present (no new dep needed)

| Library | Version | Notes |
|---------|---------|-------|
| `spring-boot-starter-jdbc` | BOM-managed | Becomes redundant when `starter-data-jpa` added (JPA transitively pulls it); can be removed |
| `org.postgresql:postgresql` | BOM-managed (~42.7.x) | Already present from Phase 8; HikariCP uses it |
| `io.opentelemetry:opentelemetry-api` | 1.61.0 | Tracer, Span, SpanKind, StatusCode, Scope |

**Dependency change summary:**
- **ADD:** `spring-boot-starter-data-jpa` (no version — BOM-managed)
- **REMOVE or keep:** `spring-boot-starter-jdbc` (redundant once JPA added; removing is cleaner but harmless to keep)
- **No version overrides:** BOM convergence rule continues to apply

**Version verification:** `spring-boot-starter-data-jpa` is managed by Spring Boot 3.4.13 BOM. [VERIFIED: CLAUDE.md `pom.xml` BOM section lists Spring Boot 3.4.13 as the root BOM].

---

## Architecture Patterns

### System Architecture Diagram

```
POST /orders
    │
    ▼
TracingMessageListenerAdvice (otel-bootstrap) — CONSUMER span
    │ extracts traceparent from AMQP headers
    ▼
OrderListener.onOrder()
    │ delegates to
    ▼
ProcessingService.process()  ← INTERNAL span (existing, unchanged)
    │ calls on success path
    ▼
OrderJpaService.persist()   ← NEW
    │
    ├─── TransactionSpanAspect (@Around, @Order(HIGHEST_PRECEDENCE))
    │       starts INTERNAL "OrderJpaService.persist" span BEFORE @Transactional proxy
    │
    ├─── @Transactional proxy (default order = LOWEST_PRECEDENCE)
    │       begins PostgreSQL transaction via HikariCP
    │
    ├─── OrderJpaRepository.findById()   ← TracingRepositoryAspect wraps
    │       CLIENT span "OrderJpaRepository.findById"
    │       db.system.name=postgresql, db.operation.name=SELECT
    │       db.namespace=orders, db.collection.name=orders
    │
    └─── [if not found] OrderJpaRepository.save()   ← TracingRepositoryAspect wraps
            CLIENT span "OrderJpaRepository.save"
            db.system.name=postgresql, db.operation.name=INSERT
            db.namespace=orders, db.collection.name=orders
            ▼
        Hibernate generates SQL → HikariCP → PostgreSQL :5432
        @Transactional commits (or rolls back on ProcessingFailedException)
        TransactionSpanAspect catches rollback → status=ERROR on INTERNAL span
```

**Trace waterfall in Tempo (new order):**
```
CONSUMER span (TracingMessageListenerAdvice)
  └── INTERNAL ProcessingService.process (existing)
        └── INTERNAL OrderJpaService.persist (NEW — TransactionSpanAspect)
              ├── CLIENT OrderJpaRepository.findById (NEW — TracingRepositoryAspect)
              └── CLIENT OrderJpaRepository.save (NEW — TracingRepositoryAspect)
```

**Trace waterfall in Tempo (duplicate order — idempotent skip):**
```
CONSUMER span
  └── INTERNAL ProcessingService.process
        └── INTERNAL OrderJpaService.persist
              └── CLIENT OrderJpaRepository.findById (SELECT only — entity found, save skipped)
```

### Recommended Project Structure

```
consumer-service/src/main/java/com/example/consumer/
├── db/
│   ├── Order.java                  # NEW: @Entity, @Table(name="orders")
│   ├── OrderJpaRepository.java     # NEW: JpaRepository<Order, Long>
│   ├── OrderJpaService.java        # NEW: @Service, @Transactional, persist()
│   └── OrderRepository.java        # DELETED
├── domain/
│   └── ProcessingService.java      # EDITED: call OrderJpaService.persist() instead of OrderRepository
├── observability/
│   ├── TracingRepositoryAspect.java  # NEW: @Aspect, @Around CLIENT spans for repository methods
│   ├── TransactionSpanAspect.java    # NEW: @Aspect, @Order(HIGHEST_PRECEDENCE), INTERNAL span for @Transactional
│   └── HikariCpConnectionGauge.java  # UNCHANGED
└── config/
    └── OtelSdkConfiguration.java   # UNCHANGED (no new providers)
```

### Pattern 1: Repository Tracing Aspect (CLIENT spans)

**What:** `@Aspect` class with `@Around` advice targeting `bean(*Repository) && execution(public * *(..))`. Wraps each repository method invocation in a `SpanKind.CLIENT` span with the `db.*` semconv attributes. Uses the established D-01 inline span template.

**When to use:** Any Spring Data JPA repository method call that crosses the process boundary to PostgreSQL.

**Pointcut reliability note:** `bean(*Repository)` targets the Spring-managed proxy bean instance by name pattern, which is more reliable than `execution()` alone for Spring Data JPA proxies. `execution(public * *(..))` narrows to public methods only, avoiding internal Spring lifecycle methods.

**Why `bean()` over `execution()` on the interface:** Spring Data JPA repositories are created as JDK dynamic proxies implementing the repository interface. The `execution()` designator against interface methods can fail intermittently due to proxy resolution timing in Spring's AOP infrastructure (GitHub spring-projects/spring-framework#24207, spring-projects/spring-framework#27761 — both closed "not planned"). `bean(*Repository)` is the documented reliable pattern for targeting named Spring beans.

```java
// Source: Spring AOP docs — bean() pointcut designator, cited from
// https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
// + PITFALL F5-1 and F5-2 from .planning/research/PITFALLS.md
// + existing D-01 inline span template from OrderRepository.java

@Aspect
@Component
public class TracingRepositoryAspect {

    private static final String DB_SYSTEM = DbAttributes.DbSystemNameValues.POSTGRESQL;
    private static final String DB_NAMESPACE = "orders";  // PostgreSQL database name (see db.namespace section)

    private final Tracer tracer;

    public TracingRepositoryAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("bean(*Repository) && execution(public * *(..))")
    public Object traceRepositoryMethod(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getDeclaringType().getSimpleName()
            + "." + pjp.getSignature().getName();
        String operationName = resolveOperationName(pjp.getSignature().getName());

        // D-J5: db.query.text = JPA method description (NOT generated SQL)
        String queryText = "JpaRepository." + pjp.getSignature().getName()
            + "(" + resolveArgs(pjp.getArgs()) + ")";

        Span span = tracer.spanBuilder(methodName)           // D-J6: span name = class.method
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, DB_SYSTEM)
            .setAttribute(DbAttributes.DB_NAMESPACE, DB_NAMESPACE)
            .setAttribute(DbAttributes.DB_OPERATION_NAME, operationName)
            .setAttribute(DbAttributes.DB_COLLECTION_NAME, "orders")    // D-J7: @Table(name="orders")
            .setAttribute(DbAttributes.DB_QUERY_TEXT, queryText)        // F5-2: constant, not string literal
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return pjp.proceed();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private String resolveOperationName(String methodName) {
        // D-J3: findById → SELECT, save → INSERT
        if (methodName.startsWith("find") || methodName.startsWith("get")) return "SELECT";
        if (methodName.startsWith("save") || methodName.startsWith("persist")) return "INSERT";
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) return "DELETE";
        return methodName.toUpperCase();  // fallback: use method name as-is
    }
}
```

### Pattern 2: Transaction-Span Aspect (INTERNAL span wrapping @Transactional)

**What:** Separate `@Aspect` class with `@Order(Ordered.HIGHEST_PRECEDENCE)` targeting `OrderJpaService.persist(...)`. Emits a `SpanKind.INTERNAL` span that wraps the entire `@Transactional` boundary. Catches exceptions from rollback and sets `status=ERROR`.

**Why separate class:** `@Transactional` default AOP order is `Ordered.LOWEST_PRECEDENCE` (confirmed: [Spring docs](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)). A custom `@Aspect` with `@Order(Ordered.HIGHEST_PRECEDENCE)` = value `Integer.MIN_VALUE` is applied FIRST in the advice chain — meaning it wraps OUTERMOST. Lower numeric order value = higher precedence = outer wrap in the proxy chain. This guarantees the transaction span covers commit/rollback latency (PITFALL F5-3).

**Why specific method pointcut, not broad:** The transaction span has business semantics (it is the unit of work). Targeting the specific service method `OrderJpaService.persist(...)` via `execution()` is reliable here because `OrderJpaService` is a concrete Spring `@Service` bean, not a proxy interface — `execution()` works reliably on concrete Spring beans.

```java
// Source: PITFALL F5-3 from .planning/research/PITFALLS.md
// + Spring docs @EnableTransactionManagement(order=...) confirming LOWEST_PRECEDENCE default

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // wraps OUTSIDE @Transactional proxy (F5-3 mitigation)
public class TransactionSpanAspect {

    private final Tracer tracer;

    public TransactionSpanAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    // Target the specific @Transactional service method — concrete class, reliable execution() matching
    @Around("execution(* com.example.consumer.db.OrderJpaService.persist(..))")
    public Object traceTransactionBoundary(ProceedingJoinPoint pjp) throws Throwable {
        Span span = tracer.spanBuilder("OrderJpaService.persist")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return pjp.proceed();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());  // rollback visible as ERROR in Tempo
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### Pattern 3: JPA Entity and Repository

```java
// Source: Spring Data JPA docs — @Entity, @Table, JpaRepository
// https://docs.spring.io/spring-data/jpa/reference/

@Entity
@Table(name = "orders")        // D-J7: new table, not "processed_orders"
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderId;    // business key (idempotency)

    @Column(columnDefinition = "TEXT")  // store JSON as TEXT (not jsonb — avoids @Convert complexity)
    private String payload;

    @Column(nullable = false)
    private Instant processedAt;

    @Column(length = 64)
    private String traceId;    // D-J8: W3C trace_id at persist time

    // JPA requires no-arg constructor
    protected Order() {}

    // factory constructor
    public Order(String orderId, String payload, Instant processedAt, String traceId) { ... }
    // getters
}
```

```java
// Spring Data JPA repository — no implementation needed
public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderId(String orderId);
}
```

```java
// Service: @Transactional wraps both the findById + optional save
@Service
public class OrderJpaService {

    private final OrderJpaRepository repository;

    public OrderJpaService(OrderJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void persist(String orderId, String payload, String traceId) {
        // D-J3 + D-J4: findById first (SELECT), save only if not found (INSERT)
        Optional<Order> existing = repository.findByOrderId(orderId);
        if (existing.isEmpty()) {
            repository.save(new Order(orderId, payload, Instant.now(), traceId));
        }
        // if present → idempotent skip (mirrors Phase 8's ON CONFLICT DO NOTHING)
    }
}
```

### Pattern 4: `application.yaml` JPA/Hibernate settings

```yaml
spring:
  application:
    name: order-consumer
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/orders}
    username: ${SPRING_DATASOURCE_USERNAME:orders}
    password: ${SPRING_DATASOURCE_PASSWORD:orders}
  # Phase 14: JPA config replaces spring.sql.init (deleted)
  jpa:
    hibernate:
      ddl-auto: update       # D-J2: JPA owns DDL — Hibernate creates/updates tables from entity annotations
    show-sql: false          # never true in production; attendees learn from spans, not SQL console
    properties:
      hibernate:
        format_sql: false    # irrelevant since show-sql=false; set explicitly to avoid ambiguity
        dialect: org.hibernate.dialect.PostgreSQLDialect   # explicit, no auto-detection surprises
  # REMOVED: spring.sql.init.mode=always (D-J2)
management:
  endpoints:
    web:
      exposure:
        include: health
```

**Why `ddl-auto: update` not `create-drop`:** `create-drop` destroys and recreates the schema on every restart, erasing workshop data between demo runs. `update` is the teaching-grade equivalent of `schema.sql IF NOT EXISTS`: it creates the table on first run and patches it on subsequent runs. For workshop hardware with no migration tool, `update` is the pragmatic choice.

**Why explicit dialect:** Without an explicit dialect, Hibernate auto-detects from the JDBC connection. The auto-detection path makes a database query on startup — a minor latency overhead that's invisible but unnecessary in a pinned-stack workshop.

**Why `show-sql: false`:** The workshop teaches OTel spans as the observability surface. Enabling SQL logging (which goes to Logback → OTLP Loki) would duplicate the `db.query.text` span attribute in logs, creating confusion about which surface is authoritative. Attendees learn from the JPA method description in `db.query.text`, not raw SQL.

### Anti-Patterns to Avoid

- **Wrapping at SQL execution level (not repository method level):** A `DataSource` proxy or Hibernate interceptor would emit one span per SQL statement — N+1 for any lazy-loaded association. The workshop entity has no associations, so this is safe for Phase 14, but the teaching point is "wrap at the repository method level" (PITFALL F5-1).
- **Using string literal `"db.statement"` instead of `DbAttributes.DB_QUERY_TEXT`:** The old attribute name. Tempo's index will be built under the wrong key; Tempo search for `db.query.text=*` returns nothing (PITFALL F5-2).
- **Placing the transaction span INSIDE the `@Transactional` proxy:** If the tracing aspect has a higher numeric order value than `@Transactional`'s `LOWEST_PRECEDENCE`, the span ends before commit/rollback. Rollbacks appear as `status=OK` (PITFALL F5-3).
- **Using `execution()` to intercept Spring Data JPA interface methods:** Intermittent failures reported in spring-projects/spring-framework#24207 and #27761. Use `bean(*Repository) && execution(public * *(..))` instead.
- **Putting the tracing aspect in `otel-bootstrap`:** TRACE-01 / DOC-05 principle: instrumentation visible per-service. The repository and transaction span aspects know the specific table name (`orders`), the service's domain semantics, and use the consumer's constructor-injected `Tracer`. They belong in `consumer-service/observability/`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JPA DDL management | Manual `schema.sql` CREATE statements | Hibernate `ddl-auto=update` with entity annotations | Entity IS the schema; manual SQL creates drift |
| Repository CRUD | Custom DAO + JDBC template | `JpaRepository<Order, Long>` | `findByOrderId`, `save`, `findById` are provided; no SQL needed |
| Connection pool | Custom `DataSource` bean | HikariCP auto-configured by Spring Boot | Custom `DataSource` bean disables `spring.sql.init.*` processing (Phase 8 research pitfall) |
| AOP proxy infrastructure | Manual `ProxyFactory` wrapping | `spring-boot-starter-aop` (transitive from `starter-data-jpa`) + `@Aspect` + `@EnableAspectJAutoProxy` (auto-configured) | Spring Boot's `AopAutoConfiguration` activates `@AspectJ` support automatically |

**Key insight:** `spring-boot-starter-data-jpa` transitively brings `spring-boot-starter-aop` which brings `aspectjweaver`. No explicit `aop` dependency declaration is needed. `@EnableAspectJAutoProxy` is activated automatically by Spring Boot's `AopAutoConfiguration`.

---

## Semconv Attribute Decisions (Claude's Discretion Resolved)

### `db.namespace` — maps to PostgreSQL database name

**Decision:** `db.namespace = "orders"` (the PostgreSQL database name from the datasource URL `jdbc:postgresql://localhost:5432/orders`).

**Reasoning:** Per semconv 1.40.0, `db.namespace` is "The name of the database, fully qualified within the server address and port." For PostgreSQL, the specification advises concatenating database name and schema using `|` as separator if both are available (e.g., `"orders|public"`). For this workshop, only the database name is relevant — the schema is `public` (PostgreSQL default) and is not a teaching point. Setting `db.namespace = "orders"` (database name only) is RECOMMENDED per the spec and avoids over-engineering. [CITED: https://opentelemetry.io/docs/specs/semconv/db/postgresql/]

**Implementation:** Extract from datasource URL at runtime, or hardcode `"orders"` as a constant in the aspect (valid for this single-database workshop). The aspect uses a constant `DB_NAMESPACE = "orders"` matching the database configured in `application.yaml`.

### `db.operation.name` — SQL verb based on JPA method name

**Decision:** Use conventional SQL verb mapping derived from the JPA method name prefix:
- `findByOrderId` / `findById` → `"SELECT"`
- `save` → `"INSERT"` (for new entities on first-time orders)

**Reasoning:** OTel semconv 1.40.0 for `db.operation.name` provides example values `SELECT`, `INSERT`, `EXECUTE` and says to capture "as provided by the application without case normalization." For JPA operations that map to clear SQL verbs, using the SQL verb is more searchable in Tempo than the JPA method name. This also aligns with Phase 8's convention (`"INSERT"` for the JDBC span). [CITED: https://opentelemetry.io/docs/specs/semconv/db/database-spans/]

**Edge case:** `save()` on an already-managed entity in Hibernate triggers `UPDATE`, not `INSERT`. For this workshop (idempotent path: `findByOrderId` first, then `save` only if not found), `save()` on a new `Order` instance always generates `INSERT`. The aspect uses `"INSERT"` for `save` — acceptable simplification for the workshop.

### `db.collection.name` — table name from `@Table(name="orders")`

**Decision:** `db.collection.name = "orders"` (the JPA `@Table(name = "orders")` value, per D-J7).

**Reasoning:** Semconv 1.40.0 `db.collection.name` is "The name of a collection (table, container) within the database." For a relational database this is the table name. [CITED: https://opentelemetry.io/docs/specs/semconv/db/database-spans/]

### `db.query.text` — JPA method description (D-J5, locked by user)

**Decision:** `"JpaRepository.findById(orderId)"` and `"JpaRepository.save(Order)"` — JPA method descriptions, not SQL.

**Reasoning:** User locked D-J5. The teaching point is honesty about the abstraction level — JPA hides the SQL; `db.query.text` documents what the developer called. This is also the safe approach per PITFALL F5-2 (never use string literal `"db.statement"`).

---

## Common Pitfalls

### Pitfall 1: Spring Data JPA proxy — `execution()` pointcut unreliable on interface methods (F5-1 variant)

**What goes wrong:** `@Around("execution(* com.example.consumer.db.OrderJpaRepository.*(..))")` works for `save()` (inherited from `SimpleJpaRepository`, a concrete class) but fails for `findByOrderId()` (a derived query method created as an interface method via proxy). Spring AOP GitHub #24207 documented this; issue closed "not planned."

**Why it happens:** Spring Data JPA creates repositories as JDK dynamic proxies. Some method interceptions on proxy-backed interfaces are inconsistent in Spring's AOP framework due to the proxy creation order.

**How to avoid:** Use `bean(*Repository) && execution(public * *(..))`. The `bean()` designator targets the named Spring bean instance (the proxy), not the interface type.

**Warning signs:** Aspect fires for `save()` but not for `findByOrderId()` in testing.

### Pitfall 2: Aspect wraps INSIDE @Transactional — rollbacks invisible (F5-3)

**What goes wrong:** If the transaction-span aspect has the default AOP order (`LOWEST_PRECEDENCE`), Spring applies it AFTER `@Transactional`. The span ends before the transaction commits or rolls back. The 10% failure path (`ProcessingFailedException`) triggers a rollback after the span has ended — the span shows `status=OK`.

**Why it happens:** `@Transactional`'s `TransactionInterceptor` has default order `Ordered.LOWEST_PRECEDENCE`. Two aspects at the same order are applied in an unspecified order. If the tracing aspect has no explicit order, it gets LOWEST_PRECEDENCE too.

**How to avoid:** `@Order(Ordered.HIGHEST_PRECEDENCE)` on `TransactionSpanAspect` sets its order to `Integer.MIN_VALUE`, which is lower numerically than `LOWEST_PRECEDENCE` (`Integer.MAX_VALUE`). In Spring AOP, lower numeric value = higher precedence = outer wrapping for `@Around` advice.

**Warning signs:** The 10th order (deterministic failure) shows `INTERNAL span status=OK` instead of `ERROR` in Tempo.

### Pitfall 3: `db.statement` string literal instead of `DbAttributes.DB_QUERY_TEXT` (F5-2)

**What goes wrong:** Searching Tempo with `db.query.text=*` returns no results. Span attributes panel shows `db.statement: ...` (old key name).

**Why it happens:** Pre-semconv-1.30 tutorials use `"db.statement"`. The current stable key is `db.query.text`.

**How to avoid:** Always use `DbAttributes.DB_QUERY_TEXT` constant. The Phase 8 `OrderRepository.java` already demonstrates the correct pattern.

**Warning signs:** `mvn compile` passes, but Tempo search for `db.query.text=*` returns zero spans.

### Pitfall 4: X-1 circular reference — verify before adding any new @Bean

**What goes wrong:** `OtelSdkConfiguration` had a circular reference cycle (X-1, fixed in Phase 10). Phase 14 adds two new `@Aspect` `@Component` beans to the consumer context. If either aspect constructor-injects `Tracer` AND `Tracer` is resolved through a chain that re-enters `OtelSdkConfiguration`, the cycle reappears.

**Why it happens:** Spring's circular reference detection applies to any bean that is part of a creation chain.

**How to avoid:** Both aspects receive `Tracer` via constructor injection. `Tracer` is a `@Bean` produced by `OtelSdkConfiguration.tracer(OpenTelemetry)`, which depends on `openTelemetry()`. As long as neither aspect is injected INTO `OtelSdkConfiguration`, there is no cycle. The Phase 10 fix (remove `@Autowired` field, assign inline) must be verified present before adding the new beans.

### Pitfall 5: `spring-boot-starter-jdbc` + `spring-boot-starter-data-jpa` redundancy

**What goes wrong:** Not a failure, but a teaching-surface confusion: `pom.xml` declares both starters. `starter-data-jpa` transitively pulls `starter-jdbc`. The dependency tree shows duplicates.

**How to avoid:** Remove `spring-boot-starter-jdbc` explicit dependency after adding `starter-data-jpa`. The Maven enforcer `dependencyConvergence` rule will surface any version divergence if the transitive version differs.

### Pitfall 6: `@EnableAspectJAutoProxy` not required — but classpath must have `aspectjweaver`

**What goes wrong:** `@Aspect` beans are declared but advice never fires. No error is thrown.

**Why it happens:** Spring Boot's `AopAutoConfiguration` activates `@AspectJ` support automatically IF `aspectjweaver` is on the classpath. `starter-data-jpa` → `starter-aop` → `aspectjweaver` provides this transitively. If for any reason the dependency chain is broken, AOP silently degrades.

**How to avoid:** After adding `starter-data-jpa`, run `mvn dependency:list | grep aspectjweaver` to confirm presence. Verify at runtime by placing a breakpoint or log in the aspect advice.

### Pitfall 7: X-4 — `TestOtelConfiguration` may need update

**What goes wrong:** `OrderFlowIT` uses `TestOtelConfiguration` which wires `Tracer` as a bean. The two new aspects (`TracingRepositoryAspect`, `TransactionSpanAspect`) inject `Tracer` via constructor. The test Spring context already provides a `Tracer` bean — no change to `TestOtelConfiguration` is needed. However, the existing `dbClientSpansPresentInTrace_spanAssertions` test asserts `db.collection.name = "processed_orders"` (Phase 8 table). This assertion must be updated to `db.collection.name = "orders"` (Phase 14 table).

**How to avoid:** Update `OrderFlowIT` test 5 to assert the new span structure: INTERNAL transaction span + 2 CLIENT child spans, new table name, JPA method descriptions in `db.query.text`.

---

## Code Examples

### ORDER entity (verified pattern)

```java
// Source: Spring Data JPA docs — entity and repository patterns
// https://docs.spring.io/spring-data/jpa/reference/ + CONTEXT.md D-J7 + D-J8

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false)
    private String orderId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;   // JSON stored as TEXT (simpler than JSONB + @Convert for workshop)

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;   // D-J8: Span.current().getSpanContext().getTraceId()

    protected Order() {}   // JPA no-arg constructor requirement

    public Order(String orderId, String payload, Instant processedAt, String traceId) {
        this.orderId = orderId;
        this.payload = payload;
        this.processedAt = processedAt;
        this.traceId = traceId;
    }
    // getters only — immutable after construction (per coding-style rules)
}
```

**Why `TEXT` not `JSONB` for payload:** The Phase 8 `schema.sql` used `JSONB`. With JPA/Hibernate, mapping a `JSONB` column requires either a custom `AttributeConverter` or `@Column(columnDefinition = "jsonb")` with a Hibernate dialect extension. For a workshop teaching OTel, not Hibernate type mapping, `TEXT` stores the same JSON content without any extra complexity. Workshop attendees can query the column with `::jsonb` casting in PostgreSQL ad-hoc queries.

### ProcessingService integration point

```java
// EDITED: replace repository.insertProcessedOrder() with jpaService.persist()
// Source: consumer-service/domain/ProcessingService.java line ~96

// BEFORE (Phase 8):
repository.insertProcessedOrder(orderId, traceId, payloadJson);

// AFTER (Phase 14):
jpaService.persist(orderId, payloadJson, traceId);
// Constructor injection: replace OrderRepository with OrderJpaService
// ProcessingService constructor signature changes accordingly
```

### OrderFlowIT test update (DBSP-05)

```java
// Updated assertion for the DB CLIENT span test — new table + new span count
// Source: integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java

// OLD (Phase 8): assert 2 CLIENT spans (Valkey + JDBC), db.collection.name="processed_orders"
// NEW (Phase 14): assert 3+ CLIENT spans (Valkey + JPA_findById + JPA_save), db.collection.name="orders"

// For a new order: >= 3 CLIENT spans
Awaitility.await()
    .atMost(Duration.ofSeconds(15))
    .until(() -> TestOtelHolder.SPANS.getFinishedSpanItems().stream()
        .filter(s -> s.getKind() == SpanKind.CLIENT)
        .count() >= 3);  // Valkey + findById CLIENT + save CLIENT

// Assert JPA CLIENT span attributes
SpanData jpaFindSpan = clientSpans.stream()
    .filter(s -> "OrderJpaRepository.findByOrderId".equals(s.getName()))
    .findFirst().orElseThrow(...);
assertThat(jpaFindSpan)
    .hasAttribute(equalTo(AttributeKey.stringKey("db.operation.name"), "SELECT"))
    .hasAttribute(equalTo(AttributeKey.stringKey("db.collection.name"), "orders"))
    .hasAttribute(equalTo(AttributeKey.stringKey("db.namespace"), "orders"));
```

### verify:jpa-spans mise task pattern

```toml
# Follows verify:tail-sampling and verify:exemplars patterns from mise.toml
[tasks."verify:jpa-spans"]
description = "Phase 14 invariant: db.query.text attribute present in Tempo spans for consumer-service JPA operations"
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5

echo "verify:jpa-spans: querying Tempo for db.query.text=* spans..."
for i in $(seq 1 $ATTEMPTS); do
  RESULT=$(curl -fsS 'http://localhost:3200/api/search?tags=db.query.text%3D*&limit=5&service=order-consumer' 2>&1) || {
    LAST_ERR="curl :3200 failed: $RESULT"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — Tempo not ready ($LAST_ERR); retrying..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:jpa-spans timed out — Tempo :3200 not reachable. Run: mise run infra:up"
    exit 1
  }
  if printf '%s' "$RESULT" | jq -e '.traces | length > 0' >/dev/null 2>&1; then break; fi
  LAST_ERR="no traces with db.query.text in Tempo yet"
  [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — $LAST_ERR; retrying..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:jpa-spans — $LAST_ERR after $((ATTEMPTS * SLEEP_SECS))s. Run: mise run load"
  exit 1
done

echo "verify:jpa-spans: GREEN — db.query.text spans present in Tempo for order-consumer."
"""
```

---

## Runtime State Inventory

> This is a replacement/migration phase: Phase 8's `processed_orders` table is being replaced by a JPA-managed `orders` table.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `processed_orders` table in PostgreSQL (`orders` database) — rows from Phase 8 JDBC inserts | No migration needed — Phase 14 creates a NEW `orders` table; `processed_orders` remains but is unused. Old table can be left or manually dropped. No data migration script needed. |
| Live service config | None — PostgreSQL datasource URL unchanged; same container, same database | No change |
| OS-registered state | None | None |
| Secrets/env vars | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` — same values | No change; values already configured in `application.yaml` |
| Build artifacts | `consumer-service/src/main/resources/schema.sql` — deleted in Phase 14 | Delete file; remove `spring.sql.init.mode=always` from `application.yaml` |

**Post-migration state:** After Phase 14, the PostgreSQL `orders` database will contain both `processed_orders` (Phase 8, unused) and `orders` (Phase 14, managed by Hibernate). No cleanup task is needed for workshop purposes, but a README note is appropriate: "The `processed_orders` table from Phase 8 is still in the database — Hibernate does not drop it since `ddl-auto=update` only creates or alters, never drops."

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | DBSP-01..04 | Checked by `docker-compose.yml` (Phase 8 infra) | Running in Docker compose | None — integration tests use `PostgreSQLContainer` |
| `spring-boot-starter-data-jpa` | DBSP-01 | Not yet in pom.xml — ADD in phase | BOM-managed by Spring Boot 3.4.13 | None |
| `aspectjweaver` | AOP aspect execution | Transitively pulled by `starter-data-jpa` → `starter-aop` | BOM-managed | None (required for `@Aspect` support) |
| Testcontainers `postgres` module | `OrderFlowIT` | Present in `integration-tests/pom.xml` (from Phase 8) | BOM-managed | None |

**Missing dependencies with no fallback:**
- `spring-boot-starter-data-jpa` must be added to `consumer-service/pom.xml` before any other Phase 14 code compiles.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.x (managed by Spring Boot 3.4.13 BOM) |
| Config file | `integration-tests/pom.xml` (failsafe plugin, explicit binding from Phase 6) |
| Quick run command | `mvn -pl consumer-service test` |
| Full suite command | `mvn -pl integration-tests verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DBSP-01 | `spring-boot-starter-data-jpa` in pom.xml | compile | `mvn -pl consumer-service compile` | N/A — compile check |
| DBSP-02 | `Order` entity, `OrderJpaRepository`, `OrderJpaService` exist; `@RabbitListener` path calls `persist()` | integration | `mvn -pl integration-tests verify` | ✅ `OrderFlowIT.java` (update test 5) |
| DBSP-03 | Each repository call emits CLIENT span with `db.*` attributes including `DbAttributes.DB_QUERY_TEXT` constant | integration | `mvn -pl integration-tests verify` | ✅ `OrderFlowIT.java` (update test 5) |
| DBSP-04 | Transaction INTERNAL span wraps repository CLIENT spans; 10th order rollback → `status=ERROR` on INTERNAL span | integration | `mvn -pl integration-tests verify` | ✅ `OrderFlowIT.java` (update test 4 + test 5) |
| DBSP-05 | Tempo returns `db.query.text=*` spans for consumer-service | smoke/live | `mise run verify:jpa-spans` | ❌ Wave 0 — add to `mise.toml` |

### Sampling Rate

- **Per task commit:** `mvn -pl consumer-service compile` (fast compile check)
- **Per wave merge:** `mvn -pl integration-tests verify`
- **Phase gate:** Full suite green + `mise run verify:jpa-spans` before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] Update `OrderFlowIT.java` test 5 to assert: 3 CLIENT spans (Valkey + findByOrderId + save), `db.collection.name="orders"`, `db.query.text` format matching JPA method descriptions, INTERNAL transaction span present as parent of CLIENT spans
- [ ] Update `OrderFlowIT.java` test 4 (failure path) to assert: INTERNAL `OrderJpaService.persist` span carries `status=ERROR` on the 10th order rollback
- [ ] Add `[tasks."verify:jpa-spans"]` block to `mise.toml`
- [ ] Confirm `bean(*Repository) && execution(public * *(..))` pointcut fires for both `findByOrderId()` and `save()` before merging (manual smoke test during development)

---

## `HikariCpConnectionGauge` Fate

**Decision: No change needed.**

`HikariCpConnectionGauge` injects `DataSource` (via `dataSource.unwrap(HikariDataSource.class)`) and `Meter`. Spring Boot auto-configures HikariCP as the connection pool for BOTH `spring-boot-starter-jdbc` AND `spring-boot-starter-data-jpa`. When JPA is the persistence layer, Spring Boot still wires a `HikariDataSource` as the primary `DataSource` bean — the gauge's `unwrap()` call continues to succeed.

[VERIFIED: `HikariCpConnectionGauge.java` source in codebase — uses `dataSource.unwrap(HikariDataSource.class)`, not type-cast. Spring Boot 3.4.13 docs confirm HikariCP remains the default connection pool for both JDBC and JPA starters.]

---

## Security Domain

This phase adds database spans and JPA entity persistence. No new authentication, session management, or HTTP endpoints are introduced.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Not applicable |
| V3 Session Management | No | Not applicable |
| V4 Access Control | No | Not applicable |
| V5 Input Validation | Yes (low risk) | `orderId` and `payload` are validated in `ProcessingService.process()` before reaching `OrderJpaService.persist()`; the not-blank check on `orderId` carries forward |
| V6 Cryptography | No | Not applicable |

**SQL injection:** Spring Data JPA uses parameterized queries (prepared statements) for all generated JPQL. `findByOrderId(String orderId)` uses a bind parameter — no string concatenation. `db.query.text` contains the JPA method description string, not raw SQL, so no injection surface via that attribute.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `JdbcTemplate` + `schema.sql` (Phase 8) | Spring Data JPA + `ddl-auto=update` | Phase 14 | Hibernate manages schema; `JpaRepository` provides CRUD without SQL |
| `"db.statement"` attribute key (pre-1.30 semconv) | `DbAttributes.DB_QUERY_TEXT` (stable, semconv 1.40.0) | semconv 1.30.0 | Old key is wrong; Tempo search for `db.query.text=*` requires the new key |
| Manual `DataSource` @Bean | Spring Boot auto-configured HikariCP DataSource | Phase 8 carryover | Manual bean disables `spring.sql.init.*`; auto-config is the correct path |
| `"db.system"` attribute key (incubating) | `DbAttributes.DB_SYSTEM_NAME` (stable, semconv 1.40.0) | Phase 8 already used the stable form | No change needed; Phase 8 already correct |

**Deprecated/outdated:**
- `spring.sql.init.mode=always` + `schema.sql`: replaced by JPA DDL management in Phase 14
- `OrderRepository.java` (Phase 8 JDBC): replaced entirely; preserved in git history at tag `step-08-db-cache`

---

## Open Questions

1. **`@Column(columnDefinition = "TEXT")` vs `@Column(columnDefinition = "jsonb")` for payload**
   - What we know: TEXT works. JSONB requires either `@Column(columnDefinition = "jsonb")` + custom Hibernate type or an `AttributeConverter<String, String>`.
   - What's unclear: Whether the workshop values seeing JSONB (enables `->` operators in ad-hoc queries) over simplicity.
   - Recommendation: Use `TEXT`. The CONTEXT.md is silent on this (Claude's Discretion). Workshop focus is OTel, not Hibernate type mapping.

2. **`@EnableAspectJAutoProxy` explicit annotation needed?**
   - What we know: `spring-boot-starter-data-jpa` → `spring-boot-starter-aop` → `AopAutoConfiguration` activates proxy support automatically.
   - What's unclear: Whether `consumer-service/ConsumerApplication.java` needs `@EnableAspectJAutoProxy` explicitly.
   - Recommendation: No explicit annotation needed. `AopAutoConfiguration` handles it. If aspects don't fire, the `@Aspect` annotation and `@Component` are sufficient with the auto-configuration.

3. **`OrderJpaRepository` — use `findByOrderId(String orderId)` or `findById(Long id)`?**
   - What we know: D-J3/D-J4 require checking existence by business key (`orderId`), not surrogate key (`id`). `JpaRepository<Order, Long>` provides `findById(Long id)` by default.
   - Recommendation: Declare `Optional<Order> findByOrderId(String orderId)` as a derived query method on `OrderJpaRepository`. This generates `SELECT ... WHERE order_id = ?` without custom JPQL.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@Order(Ordered.HIGHEST_PRECEDENCE)` on a custom `@Aspect` guarantees it wraps OUTSIDE `@Transactional` proxy in all Spring Boot 3.4.x configurations | Pattern 2 / DBSP-04 | If wrong: transaction span ends inside transaction boundary; rollbacks show `status=OK` — must add explicit `@EnableTransactionManagement(order=Ordered.LOWEST_PRECEDENCE)` to override |
| A2 | `bean(*Repository) && execution(public * *(..))` reliably intercepts both `findByOrderId()` and `save()` in Spring Boot 3.4.13 | Pattern 1 | If wrong: one of the two repository methods not intercepted; partial span coverage; PITFALL F5-1 variant — workaround: use explicit method calls in `OrderJpaService.persist()` with inline span template instead of AOP |
| A3 | `spring-boot-starter-data-jpa` does NOT introduce a new `DataSource`-creating `@Bean` that conflicts with Phase 8's HikariCP auto-config | HikariCpConnectionGauge Fate | If wrong: duplicate DataSource beans; `HikariCpConnectionGauge.unwrap()` may fail with `ClassCastException` — unlikely given Spring Boot 3.4.13 auto-config, but must verify at compile time |

**If A1 fails (aspect wraps inside @Transactional):** Use inline span template in `OrderJpaService.persist()` method body instead of `@Aspect`: start span BEFORE calling repository methods, end in `finally`. This is the simpler workshop approach that avoids the ordering complexity entirely.

**If A2 fails (intermittent proxy interception):** Use inline span template directly in `OrderJpaService.persist()` method body wrapping each repository call — same D-01 pattern used in Phase 8's `OrderRepository.java`. This is pedagogically equivalent and actually clearer (attendees see the span creation adjacent to the repository call).

---

## Sources

### Primary (HIGH confidence)

- [CODEBASE: `consumer-service/db/OrderRepository.java`] — Phase 8 D-01 CLIENT span template; `DbAttributes` import pattern; reusable as direct model
- [CODEBASE: `consumer-service/domain/ProcessingService.java`] — integration point; line ~96 `repository.insertProcessedOrder()` is the edit target
- [CODEBASE: `consumer-service/config/OtelSdkConfiguration.java`] — confirmed: no new providers needed; X-1 circular-ref fix already applied
- [CODEBASE: `consumer-service/pom.xml`] — current deps; `spring-boot-starter-jdbc` is explicit; `spring-boot-starter-data-jpa` absent
- [CODEBASE: `consumer-service/src/main/resources/application.yaml`] — current YAML; `spring.sql.init.mode=always` to be removed
- [CODEBASE: `.planning/research/PITFALLS.md` § F5] — F5-1 (repository-level, not SQL-level), F5-2 (`DbAttributes.DB_QUERY_TEXT`), F5-3 (`@Order(HIGHEST_PRECEDENCE)` outer wrapping)
- [CITED: https://opentelemetry.io/docs/specs/semconv/db/database-spans/] — stable `db.*` attribute definitions, requirement levels
- [CITED: https://opentelemetry.io/docs/specs/semconv/db/postgresql/] — PostgreSQL `db.namespace` = database name; example `EXECUTE`/`INSERT` operation values
- [CITED: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html] — `@Transactional` default order = `Ordered.LOWEST_PRECEDENCE`
- [CITED: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html] — `bean()` pointcut designator; JDK proxy limitations for `execution()` on interfaces

### Secondary (MEDIUM confidence)

- [https://github.com/spring-projects/spring-framework/issues/24207] — Spring AOP + Spring Data JPA proxy: `execution()` on interface methods unreliable; `save()` intercepted, `findAll()` not
- [https://github.com/spring-projects/spring-framework/issues/27761] — Spring AOP randomly fails when advising JpaRepository; closed "not planned" — confirms `bean()` approach preferred
- [Context7 `/spring-projects/spring-data-jpa` — transactions topic] — `@Transactional` facade pattern; service-level transaction management
- [Context7 `/open-telemetry/opentelemetry-java` — span management topic] — D-01 span lifecycle pattern (CLIENT span with try(Scope)/catch/finally)

### Tertiary (LOW confidence)

- [https://www.springboottutorial.com/spring-boot-and-aop-with-spring-boot-starter-aop] — `starter-data-jpa` transitively brings `starter-aop` + `aspectjweaver` [ASSUMED based on Spring Boot starter composition; verify with `mvn dependency:list | grep aspectjweaver` after adding `starter-data-jpa`]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring Boot 3.4.13 BOM-managed; existing codebase verified
- Architecture: HIGH — verified against existing code patterns; semconv from official docs
- Pitfalls: HIGH — F5-1/F5-2/F5-3 from project-specific PITFALLS.md (researched at project scope); AOP proxy issues from Spring Framework issue tracker
- `db.namespace` PostgreSQL mapping: HIGH — verified against official semconv 1.40.0 spec
- AOP `@Order` guarantee (A1/A2): MEDIUM — documented behavior; two known edge cases logged as assumptions

**Research date:** 2026-05-04
**Valid until:** 2026-08-04 (90 days — Spring Boot 3.4.13 is the final 3.4.x patch; semconv 1.40.0 is stable)
