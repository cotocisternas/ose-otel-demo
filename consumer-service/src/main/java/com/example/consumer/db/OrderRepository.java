package com.example.consumer.db;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.ServerAttributes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed persistence for processed orders (Phase 8 DB-CACHE-03).
 *
 * <p>Each {@link #insertProcessedOrder} call wraps the {@link JdbcTemplate#update}
 * in a manually-built {@link SpanKind#CLIENT} span using the D-01 inline template.
 * Workshop attendees read every SDK call — there is no proxy, no AOP, no framework
 * magic between the business code and the OTel span lifecycle.
 *
 * <p><b>Why {@code JdbcTemplate} not Spring Data JPA (CONTEXT.md D-03)?</b>
 * JPA hides the SQL behind a proxy that auto-instruments via AOP. {@code JdbcTemplate}
 * calls {@code jdbcTemplate.update(sql, ...)} directly — every attendee can
 * see exactly which line of code crosses the DB boundary and which lines
 * surround it with a CLIENT span. The workshop's manual-SDK premise requires
 * visibility, not convenience.
 *
 * <p><b>Semconv attributes (RESEARCH §6):</b>
 * <ul>
 *   <li>{@code db.system.name = "postgresql"} — stable semconv from
 *       {@link DbAttributes.DbSystemNameValues#POSTGRESQL}</li>
 *   <li>{@code db.operation.name = "INSERT"} — the SQL verb</li>
 *   <li>{@code db.collection.name = "processed_orders"} — the table name</li>
 *   <li>{@code db.query.text} — the parameterized SQL template (safe because
 *       no user data is inlined; {@code ?} placeholders prevent injection)</li>
 * </ul>
 *
 * <p><b>ON CONFLICT DO NOTHING (CONTEXT.md § specifics):</b> makes the consumer
 * idempotent at the DB layer. If the producer's Valkey idempotency check lets a
 * duplicate through (e.g., TTL expired), the INSERT silently no-ops instead of
 * throwing a unique-constraint exception.
 */
@Repository
public class OrderRepository {

    private static final String TABLE = "processed_orders";

    private static final String INSERT_SQL =
        "INSERT INTO processed_orders (order_id, processed_at, consumer_trace_id, payload)"
        + " VALUES (?, NOW(), ?, ?::jsonb)"
        + " ON CONFLICT (order_id) DO NOTHING";

    private final JdbcTemplate jdbc;
    private final Tracer tracer;

    public OrderRepository(JdbcTemplate jdbc, Tracer tracer) {
        this.jdbc   = jdbc;
        this.tracer = tracer;
    }

    /**
     * Persist one processed order. Idempotent via ON CONFLICT DO NOTHING.
     *
     * @param orderId       the order identifier (PRIMARY KEY)
     * @param traceId       the W3C trace_id at call time — from {@code Span.current().getSpanContext().getTraceId()}
     * @param payloadJson   the full order payload serialized as JSON
     */
    public void insertProcessedOrder(String orderId, String traceId, String payloadJson) {
        // ---- D-01 inline span template (CLIENT) ----
        //
        // SpanKind.CLIENT because this call crosses a process boundary into the
        // PostgreSQL server. Same shape as InstrumentedJedisPool's SET span —
        // attendees see the pattern twice (Redis CLIENT + JDBC CLIENT) and
        // understand the CLIENT kind signals "outbound call to a remote system."
        //
        // db.system.name = "postgresql" is STABLE in semconv 1.40.0
        // (DbAttributes.DbSystemNameValues.POSTGRESQL) — contrast with the
        // Valkey span's db.system.name = "redis" which uses the INCUBATING
        // constant (DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS).
        // The workshop deliberately uses BOTH to teach the stable/incubating split.
        Span span = tracer.spanBuilder("INSERT " + TABLE)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, DbAttributes.DbSystemNameValues.POSTGRESQL)
            .setAttribute(DbAttributes.DB_OPERATION_NAME, "INSERT")
            .setAttribute(DbAttributes.DB_COLLECTION_NAME, TABLE)
            .setAttribute(DbAttributes.DB_QUERY_TEXT, INSERT_SQL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // The jdbcTemplate.update() call is the SINGLE line that crosses the
            // process boundary. Everything else in this method is OTel SDK boilerplate
            // — workshop attendees count the boilerplate lines vs the business line.
            jdbc.update(INSERT_SQL, orderId, traceId, payloadJson);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
