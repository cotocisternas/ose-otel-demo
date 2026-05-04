# Phase 14: JDBC/JPA Database Spans - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-04
**Phase:** 14-jdbc-jpa-database-spans
**Areas discussed:** Phase 8 JDBC fate, JPA operation depth, db.query.text source, Entity table design

---

## Phase 8 JDBC Fate

### Q1: What happens to the existing Phase 8 raw-JDBC OrderRepository when JPA arrives?

| Option | Description | Selected |
|--------|-------------|----------|
| Replace with JPA (Recommended) | Delete the raw-JDBC OrderRepository. JPA path becomes the ONLY persistence path. v1.0 contrast lives in git history and README screenshot pair. | ✓ |
| Keep both side-by-side | Rename existing OrderRepository → OrderJdbcRepository. Both coexist. | |
| Comment out, don't delete | Comment the JDBC path with a NOTE pointing to step-08-db-cache tag. | |

**User's choice:** Replace with JPA
**Notes:** Clean replacement — v1.0 JDBC approach stays in git history at `step-08-db-cache`.

### Q2: What about schema.sql and spring.sql.init?

| Option | Description | Selected |
|--------|-------------|----------|
| JPA owns DDL (Recommended) | Delete schema.sql. Set spring.jpa.hibernate.ddl-auto=update. Entity annotations are the schema. | ✓ |
| Keep schema.sql, JPA validates | Keep schema.sql, set ddl-auto=validate. Two sources of truth. | |
| Keep schema.sql, JPA ignores | Keep schema.sql + spring.sql.init.mode=always. Set ddl-auto=none. | |

**User's choice:** JPA owns DDL
**Notes:** Schema.sql deleted, Hibernate generates table from entity annotations.

---

## JPA Operation Depth

### Q3: How many distinct JPA repository calls per order persist?

| Option | Description | Selected |
|--------|-------------|----------|
| findById + save (2 spans) | Check if order exists first (SELECT), then save (INSERT). 2 child spans. | ✓ |
| save only (1 span) | Just call repository.save(entity). 1 child span. | |
| findById + save + flush (3 spans) | Explicit find + save + flush. 3 child spans. Flush is artificial. | |

**User's choice:** findById + save (2 spans)
**Notes:** SELECT + INSERT pair shows realistic repository usage pattern.

### Q4: Semantic behavior when order already exists?

| Option | Description | Selected |
|--------|-------------|----------|
| Skip save (idempotent) | If findById returns the entity, skip save. Still produces SELECT span. | ✓ |
| Merge/update always | Always call save() regardless. INSERT on new, UPDATE on existing. | |
| You decide | Let planner pick. | |

**User's choice:** Skip save (idempotent)
**Notes:** Mirrors Phase 8's ON CONFLICT DO NOTHING at the JPA application layer.

---

## db.query.text Source

### Q5: What value should db.query.text carry on JPA repository spans?

| Option | Description | Selected |
|--------|-------------|----------|
| JPQL/method description (Recommended) | Descriptive string like "JpaRepository.save(Order)". Honest about abstraction. | ✓ |
| Hibernate-generated SQL | Intercept actual SQL via StatementInspector. Most accurate but verbose. | |
| Static template SQL | Hand-write approximate SQL. Recognizable but technically a lie. | |

**User's choice:** JPQL/method description
**Notes:** Honest about the JPA abstraction level — documents what the developer wrote, not what Hibernate generated.

### Q6: Span name convention?

| Option | Description | Selected |
|--------|-------------|----------|
| JPA method-based (Recommended) | "OrderJpaRepository.findById", "OrderJpaRepository.save". Matches db.query.text. | ✓ |
| SQL-verb-based | "SELECT orders", "INSERT orders". Matches Phase 8 convention. | |
| You decide | Let planner choose. | |

**User's choice:** JPA method-based
**Notes:** Consistent abstraction level — attendees see method names in Tempo and know which code to look at.

---

## Entity Table Design

### Q7: Should the JPA Order entity map to existing processed_orders or a new table?

| Option | Description | Selected |
|--------|-------------|----------|
| New 'orders' table (Recommended) | Fresh entity with @Table(name="orders"). Clean JPA design with surrogate Long key. | ✓ |
| Map to processed_orders | Entity maps to existing table. Non-idiomatic JPA (VARCHAR PK). | |
| You decide | Let planner pick. | |

**User's choice:** New 'orders' table
**Notes:** Idiomatic JPA with Long surrogate key and orderId as unique business key.

### Q8: What columns beyond id and orderId?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal + trace correlation (Recommended) | id, orderId(unique), payload(jsonb), processedAt(Instant), traceId(String). | ✓ |
| Business-rich entity | Add status, customerName, amount, region fields. May distract from OTel lesson. | |
| Absolute minimum | Just id + orderId + processedAt. Loses trace-correlation teaching point. | |

**User's choice:** Minimal + trace correlation
**Notes:** Preserves Phase 8's trace-correlation teaching artifact (traceId column) in JPA form.

---

## Claude's Discretion

- Exact AOP pointcut expression for repository tracing aspect
- `@Order` value for transaction-span aspect ordering
- `db.namespace` attribute mapping (PostgreSQL database name vs schema)
- `db.operation.name` exact values for findById/save
- HikariCpConnectionGauge compatibility assessment
- Transaction-span aspect implementation pattern
- Hibernate dialect/show-sql/format-sql settings
- JSONB column mapping strategy
- README §14 wording and structure
- verify:jpa-spans implementation

## Deferred Ideas

None — discussion stayed within phase scope.
