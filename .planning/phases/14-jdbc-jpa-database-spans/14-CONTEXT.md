# Phase 14: JDBC/JPA Database Spans - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend the consumer service with full Spring Data JPA instrumentation — replacing the Phase 8 raw-JDBC `OrderRepository` with a proper JPA entity, repository interface, and service layer. Each JPA repository method invocation is manually wrapped in a `SpanKind.CLIENT` span carrying the full stable `db.*` semconv attribute set. A transaction-level `INTERNAL` span wraps the `@Transactional` boundary as the parent, surfacing rollbacks as `status=ERROR` in Tempo.

This is a **consumer-service Java + config phase** — no infrastructure changes. The entire teaching surface is:
- New JPA entity (`Order.java`), repository interface (`OrderJpaRepository`), and service (`OrderJpaService`)
- A tracing aspect (`TracingRepositoryAspect`) that wraps repository calls in CLIENT spans
- A transaction-span aspect ordered `@Order(HIGHEST_PRECEDENCE)` so it wraps the `@Transactional` proxy
- `application.yaml` JPA/Hibernate config (`ddl-auto`, dialect, show-sql)
- README §14 walkthrough with paired screenshot placeholder (v1.0 single-span vs v2.0 waterfall)
- `mise run verify:jpa-spans` verification task

Phase boundaries:
- **Replaces** Phase 8's raw-JDBC `OrderRepository.java` and `schema.sql` — the v1.0 single-INSERT-span approach lives in git history at tag `step-08-db-cache`
- **Deletes** `schema.sql` — JPA/Hibernate owns DDL generation via entity annotations
- **Does not touch** existing OTel SDK wiring in `OtelSdkConfiguration.java` (no new providers, no new exporters)
- **Does not introduce** Spring Data JPA auto-instrumentation or Hibernate interceptors for SQL capture

Out of scope for this phase:
- Outbound HTTP client spans (Phase 15)
- Head sampling or baggage (Phase 16)
- AMQP topology variants (Phase 17)
- Additional entities or complex JPA relationships (one entity is sufficient for the teaching surface)

</domain>

<decisions>
## Implementation Decisions

### Phase 8 JDBC code disposition

- **D-J1:** **Replace with JPA, not coexist.** The existing raw-JDBC `OrderRepository.java` (JdbcTemplate + manual CLIENT span) is deleted. The JPA path becomes the ONLY persistence path. The v1.0 contrast lives in git history (`step-08-db-cache` tag) and in the README screenshot pair — not in parallel code paths. Teaching point: "compare tags `step-08-db-cache` (raw JDBC, 1 span) vs `step-14-jpa-spans` (JPA, transaction parent + N child spans)."

- **D-J2:** **JPA owns DDL — delete `schema.sql`.** Remove `schema.sql` and set `spring.jpa.hibernate.ddl-auto=update`. The JPA entity annotations ARE the schema definition. Remove `spring.sql.init.mode=always` from `application.yaml`. Teaching point: "JPA manages your schema from annotations."

### JPA operation depth

- **D-J3:** **`findById` + `save` = 2 child CLIENT spans.** Each order persist triggers two repository calls: (1) `findById(orderId)` to check existence (SELECT span), then (2) `save(entity)` only if not found (INSERT span). This produces a 2-child span waterfall under the transaction parent on new orders, and a 1-child waterfall on duplicates (SELECT only). Teaching: attendees see the N+1-like pattern and understand why F5-1 says "wrap at repository level, not SQL level."

- **D-J4:** **Idempotent at the JPA layer.** If `findById` returns the entity, skip the `save()` — order was already persisted (e.g., redelivery). Mirrors Phase 8's `ON CONFLICT DO NOTHING` semantic, but expressed as application logic instead of SQL. The transaction span still shows a clean waterfall even on duplicates (1 SELECT child span).

### db.query.text and span naming

- **D-J5:** **`db.query.text` carries JPA method description, not SQL.** Values like `"JpaRepository.findById(orderId)"` and `"JpaRepository.save(Order)"`. Honest about the JPA abstraction level — attendees see what they called, not what Hibernate generated. Teaching: "JPA hides the SQL; the span documents the operation you wrote, not the generated query."

- **D-J6:** **Span names are JPA method-based.** Span names like `"OrderJpaRepository.findById"` and `"OrderJpaRepository.save"`. Matches D-J5 — consistent abstraction level. Attendees see method names in Tempo and know exactly which line of code to read. Contrast with Phase 8's SQL-verb convention (`"INSERT processed_orders"`) reinforces the "two approaches, same semconv attributes" narrative.

### Entity table design

- **D-J7:** **New `orders` table with idiomatic JPA.** Fresh entity with `@Table(name = "orders")`. Does NOT map to the Phase 8 `processed_orders` table. Columns: `id` (Long, `@GeneratedValue`), `orderId` (String, `@Column(unique = true)`), `payload` (String/jsonb), `processedAt` (Instant), `traceId` (String). The surrogate `Long` key follows JPA conventions; `orderId` is the business key.

- **D-J8:** **Trace correlation column preserved.** The `traceId` column carries `Span.current().getSpanContext().getTraceId()` at persist time — same teaching artifact as Phase 8: "find any DB row's originating trace in Tempo by querying the traceId column." This column is the bridge between the relational world and the observability world.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact `@Around` pointcut expression for the repository tracing aspect (researcher verifies Spring AOP + Spring Data proxy interaction)
- Exact `@Order` value for the transaction-span aspect vs Spring's `@Transactional` proxy ordering (researcher verifies `HIGHEST_PRECEDENCE` achieves outer-wrapping)
- Whether `db.namespace` attribute maps to the PostgreSQL database name (`orders`) or schema (`public`) — researcher consults semconv 1.40.0 `db.namespace` definition
- Exact `db.operation.name` values for findById/save (likely `SELECT`/`INSERT` — researcher verifies semconv convention for ORM operations)
- `db.collection.name` value (likely `orders` — the JPA `@Table(name)` value)
- `HikariCpConnectionGauge` fate — if it depends on raw JDBC wiring, may need adjustment (planner assesses)
- Transaction-span aspect implementation pattern — `@Aspect` class vs method-level wrapping in `OrderJpaService`
- `application.yaml` Hibernate dialect, show-sql, format-sql settings
- Whether `@Column(columnDefinition = "jsonb")` or `@Convert` is used for the payload field
- README §14 exact wording, length, and structure (follow Phase 11-13 precedent ~100-150 lines)
- Dashboard panel for `step-14-jpa.png` content (researcher determines which Tempo view shows the waterfall best)
- `verify:jpa-spans` exact bash implementation (follows `verify:tail-sampling` pattern)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service duplication), WORK-01 (annotated git tags on `main`)
- `.planning/REQUIREMENTS.md` § JDBC/JPA Database Spans (DBSP-01..05) — locked requirements for JPA entity, repository spans, transaction span, and Tempo visibility
- `.planning/ROADMAP.md` Phase 14 section — pedagogical headline, Success Criteria #1–4, pitfall mitigations (X-1, X-4, F5-1, F5-2, F5-3), git tag `step-14-jpa-spans`
- `.planning/STATE.md` — Phase 10/11/12/13 completion records

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes operational arc
- `.planning/research/ARCHITECTURE.md` — system architecture, service boundaries
- `.planning/research/PITFALLS.md` § F5 (F5-1 repository-level not SQL-level, F5-2 DbAttributes constant not string literal, F5-3 span outer to @Transactional) — concrete mitigation steps

### Phase 10 carryover (MUST read — circular-ref fix)

- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — D-01 (datasource UID preservation), circular-ref fix verification (X-1 already resolved)

### Files this phase EDITS or CREATES

- `consumer-service/src/main/java/com/example/consumer/db/OrderRepository.java` — **DELETED** (replaced by JPA)
- `consumer-service/src/main/resources/schema.sql` — **DELETED** (JPA owns DDL)
- `consumer-service/src/main/java/com/example/consumer/db/Order.java` — **NEW** JPA entity
- `consumer-service/src/main/java/com/example/consumer/db/OrderJpaRepository.java` — **NEW** Spring Data JPA repository interface
- `consumer-service/src/main/java/com/example/consumer/db/OrderJpaService.java` — **NEW** service layer with `@Transactional`
- `consumer-service/src/main/java/com/example/consumer/observability/TracingRepositoryAspect.java` — **NEW** AOP aspect wrapping repository calls in CLIENT spans (or equivalent — planner decides exact shape per DBSP-03/04)
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` — **EDITED** to call `OrderJpaService.persist(...)` instead of `OrderRepository.insertProcessedOrder(...)`
- `consumer-service/pom.xml` — **EDITED** to add `spring-boot-starter-data-jpa` (DBSP-01)
- `consumer-service/src/main/resources/application.yaml` — **EDITED** to add JPA/Hibernate config, remove `spring.sql.init.mode`
- `mise.toml` — additive `[tasks."verify:jpa-spans"]` block
- `README.md` — additive §14 section

### Files this phase does NOT edit

- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — no new providers or exporters needed
- `docker-compose.yml` — PostgreSQL already in place from Phase 8
- `infra/observability/*` — no infrastructure changes
- `grafana/dashboards/ose-otel-demo.json` — no new dashboard panel (screenshot is from Tempo trace search, not a custom panel)

### Upstream documentation references (research must consult)

- [OTel Semconv 1.40.0 `db.*` attributes](https://opentelemetry.io/docs/specs/semconv/database/) — `db.system.name`, `db.namespace`, `db.operation.name`, `db.collection.name`, `db.query.text` stable attribute definitions
- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/) — `JpaRepository` method contracts, `@Transactional` behavior on repository methods
- [Spring AOP ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice-ordering.html) — `@Order(HIGHEST_PRECEDENCE)` for aspect-over-proxy wrapping

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`consumer-service/db/OrderRepository.java` (Phase 8)** — D-01 inline span template (CLIENT span with `try(Scope)/catch/finally`) pattern to replicate in the new tracing aspect. The `DbAttributes` import and attribute-setting pattern transfers directly.
- **`consumer-service/domain/ProcessingService.java`** — existing INTERNAL span with 10% failure path. The JPA service call replaces the current `repository.insertProcessedOrder(...)` line inside the success path.
- **`consumer-service/observability/HikariCpConnectionGauge.java`** — HikariCP metric gauge. Needs assessment: does it still work with JPA (JPA still uses HikariCP as the connection pool under Spring Boot auto-config, so likely no change needed).
- **`otel-bootstrap` module** — if the tracing aspect is general enough, it COULD live here for reuse. But TRACE-01/DOC-05 favors per-service visibility for the workshop. Planner decides placement.

### Established Patterns

- **D-01 inline span template** — `spanBuilder().setSpanKind().setAttribute(...)startSpan()` → `try(Scope) { work } catch { recordException, setStatus(ERROR) } finally { span.end() }`. Used consistently in `OrderRepository`, `ProcessingService`, `HttpServerSpanFilter`, `TracingMessagePostProcessor`, `TracingMessageListenerAdvice`.
- **CLIENT span for outbound calls** — Phase 8 `OrderRepository` (JDBC→PostgreSQL) and `InstrumentedJedisPool` (→Valkey) both use `SpanKind.CLIENT` for calls crossing a process boundary to a remote system.
- **Semconv attribute constants** — always via typed constants (`DbAttributes.DB_SYSTEM_NAME`, `DbAttributes.DB_QUERY_TEXT`), never string literals. F5-2 mitigation.
- **Single `Tracer` injected via constructor** — every class that creates spans receives `Tracer` as a constructor parameter (Spring DI); no static `GlobalOpenTelemetry.getTracer()` calls.

### Integration Points

- **`ProcessingService.process()`** — the JPA persist call replaces `repository.insertProcessedOrder(orderId, traceId, payloadJson)` on the success path (line ~96). The parent span (`ProcessingService.process` INTERNAL) remains; the transaction INTERNAL span + CLIENT child spans nest under it.
- **`consumer-service/pom.xml`** — adds `spring-boot-starter-data-jpa` alongside existing `spring-boot-starter-jdbc`. Note: starter-data-jpa transitively pulls starter-jdbc, so the explicit `spring-boot-starter-jdbc` dep becomes redundant and can be removed (planner verifies).
- **`application.yaml`** — adds `spring.jpa.*` properties. Existing `spring.datasource.*` properties stay (JPA uses the same datasource).
- **Integration tests in `integration-tests/`** — may need updates if they assert against `processed_orders` table or the old `OrderRepository` bean. Planner assesses.

</code_context>

<specifics>
## Specific Ideas

- The user chose **full replacement** over coexistence — clean codebase, no dead code, pedagogical contrast via git tags not parallel paths. This signals the workshop teaches ONE approach per checkpoint, with history as the comparison mechanism.
- The user chose **JPA method descriptions** for both span names and `db.query.text` — honest about the abstraction level. This is a deliberate contrast with Phase 8's SQL-verb naming (`INSERT processed_orders`). The README should call this out: "Phase 8 named spans after SQL verbs because JdbcTemplate executes raw SQL; Phase 14 names spans after repository methods because JPA hides the SQL."
- The user chose **idempotent findById + save** pattern — mirrors Phase 8's `ON CONFLICT DO NOTHING` but at the application layer. This means the trace waterfall varies by scenario: 2 child spans (new order) vs 1 child span (duplicate). The README screenshot should show the 2-span case (more instructive).
- The user chose **JPA-owned DDL** — this is a clean pedagogical break from Phase 8's `schema.sql` approach. The README should note: "In Phase 8, a hand-written schema.sql created the table. In Phase 14, the entity annotations are the schema — Hibernate generates the DDL."

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 14-jdbc-jpa-database-spans*
*Context gathered: 2026-05-04*
