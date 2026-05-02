---
quick_id: 260502-8gk
description: Add new random features to have more points to monitor, use valkey for cache and postgresql for database
gathered: 2026-05-02
status: Ready for planning
---

# Quick Task 260502-8gk: Add Valkey + PostgreSQL + new monitorable features — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Task Boundary

Extend the OSE OTel Workshop demo (currently shipped at v1.0 / Phase 7 polish) with two new infrastructure components — Valkey (Redis-compatible cache) and PostgreSQL — and new feature surface in the existing producer/consumer Spring Boot services that exercises both. Each new client must be **manually instrumented with the OpenTelemetry Java SDK** (no auto-instrumentation, no `opentelemetry-spring-boot-starter`, no Java agent — per `PROJECT.md` constraints).

The work becomes a new workshop chapter — **Phase 8: DB & Cache Manual Instrumentation** — tagged as `step-08-db-cache` so attendees can `git checkout` into the lesson the same way they time-travel between Phases 1–6 today.

Out of scope:
- Adding Phase 8 to ROADMAP.md as a planned phase (this is being shipped as a quick task — ROADMAP stays at 7/7)
- Bumping milestone to v1.1 (stays v1.0; future cleanup task can promote if desired)
- Touching the existing AMQP propagation code (Phase 3's headline lesson must remain pristine)
- Auto-instrumentation libraries (`opentelemetry-jdbc`, `opentelemetry-jedis`, `opentelemetry-spring-boot-starter`) — defeats the workshop's manual-SDK premise

</domain>

<decisions>
## Implementation Decisions

### Workshop checkpoint strategy
- **LOCKED:** Tag as `step-08-db-cache` — becomes a new workshop chapter. Attendees can `git checkout step-08-db-cache` to enter the lesson. Parallels the existing `step-01-baseline` … `step-06-tests` contract.
- README walkthrough must add "Step 8: Database & Cache" section with the same 5-section template used for Steps 4/5/6 (Concept → Code → Verify → Pitfalls → Exercise).
- The "polish state" (current `main` HEAD) is preserved unchanged; `step-08-db-cache` is tagged on the new commit at the end of this work.

### Where data lives
- **LOCKED:** Producer = Valkey only. Consumer = PostgreSQL only.
- Producer flow: `POST /orders` → check Valkey for `order_id` (idempotency / dedup) → if not seen, mark in Valkey + publish to RabbitMQ → return 202.
- Consumer flow: receive AMQP message → INSERT into `processed_orders` table in Postgres → log + emit metrics.
- Trace topology: HTTP span → Valkey `SETNX` span → AMQP publish span → AMQP consume span → JDBC `INSERT` span (one continuous trace via existing W3C propagation from Phase 3).

### Spring abstraction depth
- **LOCKED:** JdbcTemplate (NOT Spring Data JPA) + Jedis (NOT Lettuce / NOT RedisTemplate).
- Rationale: maximum manual-SDK surface. Each `jdbcTemplate.update(...)` and each `jedis.setnx(...)` becomes a hand-wired `tracer.spanBuilder()` call where attendees can see exactly which OTel SDK lines produce which span. Spring Data JPA / RedisTemplate would hide this behind proxies and contradict the workshop's manual-SDK premise.
- Connection pooling: HikariCP (Spring Boot default for `spring-boot-starter-jdbc`) — pool gauges become an `ObservableGauge` exercise.

### Claude's Discretion
- **Feature selection** ("random features"): user said "you decide — pick the most workshop-pedagogical option". Bias toward features that add **distinct OTel telemetry shapes** rather than narrative depth. Working menu:
  - Producer: idempotency check (Valkey `SETNX` span + cache hit/miss counter), order_id seen-before metric, optional rate-limiter (Valkey `INCR` + TTL).
  - Consumer: `INSERT INTO processed_orders` (JDBC INSERT span), HikariCP active-connections `ObservableGauge`, slow-query histogram (record JDBC call duration as a `db.client.operation.duration` histogram).
- **Scope ceiling:** producer = 1 Valkey use case (idempotency); consumer = 1 Postgres table (`processed_orders`). No additional features beyond what produces the canonical instrumentation patterns. Resist adding "search by id" endpoints, admin tables, etc.
- **Testcontainers integration:** the existing `integration-tests/` module proves the AMQP chain end-to-end. Extend it to also assert the JDBC INSERT span and Valkey span are present in the same trace as the HTTP/AMQP spans. Use the Postgres + Valkey Testcontainers modules (latest BOM-managed versions).
- **Schema management:** Flyway is overkill for a workshop. Use a single `schema.sql` on the consumer classpath that Spring Boot auto-runs (`spring.sql.init.mode=always`). Workshop attendees see the table definition in one file.
- **Docker Compose images:** `valkey/valkey:8-alpine` (latest 8.x stable as of 2026), `postgres:17-alpine` (current LTS). Both pin to specific tags for workshop reproducibility (matching the existing `rabbitmq:4.3-management` / `grafana/otel-lgtm:0.26.0` discipline).
- **OTel semantic conventions:** use `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` constants (`DbAttributes.DB_SYSTEM_NAME`, `DbAttributes.DB_OPERATION_NAME`, `DbAttributes.DB_QUERY_TEXT`). For Redis: `db.system.name = "redis"` (Valkey speaks the Redis protocol — semconv has no separate "valkey" value as of 1.40.0).
- **Jedis OTel pattern:** wrap the `Jedis` instance in a manually-instrumented decorator (`InstrumentedJedis`) that opens a span around each command — workshop attendees write the decorator themselves so they see the semconv attribute mapping line by line.

</decisions>

<specifics>
## Specific Ideas

- **Producer idempotency feature:** `POST /orders` with `Idempotency-Key` header (or use `order_id` from body). On request: `SETNX idempotency:<key> "1" EX 3600`. If `0` (key existed), return 409 Conflict with the prior trace_id stamped on the value. If `1` (new), mark seen and proceed. The cache-hit-vs-miss flow gives attendees a binary metric to chart.
- **Consumer persistence feature:** `processed_orders` table (`order_id PRIMARY KEY`, `processed_at TIMESTAMPTZ`, `consumer_trace_id`, `payload JSONB`). On AMQP receipt: `INSERT ... ON CONFLICT (order_id) DO NOTHING` so the consumer is idempotent on its end too.
- **HikariCP gauge exercise:** register an `ObservableGauge` reading `HikariDataSource.getHikariPoolMXBean().getActiveConnections()` — teaches the async-callback flavor of OTel metrics, contrasting with the synchronous `Counter` and `Histogram` already covered in Phase 4.
- **Span attributes for Jedis decorator:** `db.system.name`, `db.operation.name` (e.g., `SETNX`), `network.peer.address`, `network.peer.port`, `server.address`. Attendees see how to extract these from a `JedisPool` `Connection`.
- **JDBC instrumentation pattern:** wrap `JdbcTemplate.update(...)` calls in a `withSpan(operation, sql, lambda)` helper that opens a `CLIENT`-kind span and stamps `db.system.name = "postgresql"`, `db.operation.name`, `db.collection.name = "processed_orders"`, `db.query.text` (with parameter scrubbing).
- **Workshop README structure:** new `Step 8 — Database & Cache` section, same 5-section template as Steps 4/5/6 (Concept / Code / Verify / Pitfalls / Exercise). Concepts & FAQ appendix gets two new entries: "Why no Spring Data JPA?" and "Why is Valkey treated as Redis in semconv?".

</specifics>

<canonical_refs>
## Canonical References

- `PROJECT.md` — bans Java agent, Micrometer bridge, and `opentelemetry-spring-boot-starter`. New work must honor these.
- `CLAUDE.md` (root) — Tech stack version pins (Spring Boot 3.4.13, Java 17 Corretto, OTel SDK 1.61.0, semconv 1.40.0).
- `.planning/ROADMAP.md` — Phases 1–7 ship contract; D-09 ("no `step-07-*` tag") confirms tag discipline. New `step-08-db-cache` tag is consistent with that discipline (it tags the new chapter, not the polish state).
- `docs/README.md` (workshop walkthrough) — Step 4/5/6 5-section template is the canonical structure for the new Step 8 section.
- OTel semconv 1.40.0 — `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` for stable database attribute keys.
- Phase 3 propagation code (producer `RabbitTemplate` MessagePostProcessor + consumer `@RabbitListener` extractor) — must NOT be modified; the new spans hang off the existing trace context.

</canonical_refs>
