-- Phase 8: processed_orders table — auto-run by spring.sql.init.mode=always on startup.
--
-- IF NOT EXISTS makes this idempotent across restarts (safe with spring.sql.init.mode=always).
-- The consumer uses INSERT ... ON CONFLICT DO NOTHING for idempotency on the DB side.
--
-- consumer_trace_id: the W3C trace_id from Span.current() at INSERT time — lets workshop
-- attendees look up the trace in Tempo directly from a database row.
-- payload JSONB: the full order message stored as JSON for workshop inspection.
CREATE TABLE IF NOT EXISTS processed_orders (
    order_id          VARCHAR(255) PRIMARY KEY,
    processed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    consumer_trace_id VARCHAR(64)  NOT NULL,
    payload           JSONB        NOT NULL
);
