package com.example.producer.cache;

import java.io.Closeable;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * Thin manually-instrumented wrapper around {@link JedisPool} (Phase 8 DB-CACHE-01).
 *
 * <p>Every public method opens a {@link SpanKind#CLIENT} span, records the canonical
 * OTel database semconv attributes, executes the Jedis call, and ends the span.
 * This is the Phase 8 textbook for CLIENT-kind spans — workshop attendees read
 * the exact same try/Scope/try/catch/finally template from Phase 2, now with
 * {@code DB_SYSTEM_NAME}, {@code DB_OPERATION_NAME}, {@code SERVER_ADDRESS},
 * and {@code SERVER_PORT} instead of messaging attributes.
 *
 * <p><b>Why not {@code jedis.setnx()} (RESEARCH §7 pitfall #1)?</b>
 * {@code setnx} is legacy Jedis API — it requires a separate {@code expire()} call
 * which is NOT atomic. {@code jedis.set(key, value, SetParams.setParams().nx().ex(ttl))}
 * is a single atomic Redis SET command with NX (only-if-not-exists) and EX (TTL in
 * seconds) options — the correct idiom in Jedis 5.x and 7.x.
 *
 * <p><b>Why {@code db.system.name = "redis"} not "valkey" (RESEARCH §6)?</b>
 * OTel semconv 1.40.0 has no "valkey" value. Valkey speaks the Redis RESP protocol;
 * the correct value is {@code DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS}.
 * This uses the <em>incubating</em> artifact ({@code opentelemetry-semconv-incubating:1.40.0-alpha})
 * because {@code "redis"} is not yet promoted to the stable semconv; both service POMs
 * already carry this dependency from Phase 2.
 *
 * <p><b>Span name discipline (RESEARCH §7 pitfall #2):</b> span name is the Redis
 * command verb only ({@code "SET"}) — NOT the key. High-cardinality key strings in
 * span names bloat Tempo's trace index. The key goes into a span attribute if
 * needed — not the name.
 */
public class InstrumentedJedisPool implements Closeable {

    private final JedisPool pool;
    private final Tracer tracer;
    private final String host;
    private final int port;

    public InstrumentedJedisPool(Tracer tracer, String host, int port) {
        this.pool   = new JedisPool(host, port);
        this.tracer = tracer;
        this.host   = host;
        this.port   = port;
    }

    /**
     * Atomic SET key value NX EX ttlSeconds.
     *
     * <p>Returns {@code true} if the key was newly set (this is the FIRST time
     * this key has been seen within the TTL window — the "cache miss" / new-order
     * path). Returns {@code false} if the key already existed (duplicate / idempotency
     * hit).
     *
     * <p>The OTel {@code db.operation.name} is {@code "SET"} — the actual Redis
     * command. The NX+EX semantics are an option on the SET command, not a
     * separate command.
     */
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        // ---- D-01 inline span template (CLIENT) ----
        //
        // Same try/Scope/catch/finally shape as the Phase 2 INTERNAL spans and
        // the Phase 3 PRODUCER/CONSUMER spans. The SpanKind is CLIENT here because
        // this call crosses a process boundary into the Valkey server.
        //
        // Attributes follow OTel database semconv 1.40.0:
        //   db.system.name  = "redis"  (incubating — Valkey speaks Redis protocol)
        //   db.operation.name = "SET"  (the Redis command, stable semconv)
        //   server.address + server.port = Valkey host:port (stable semconv)
        Span span = tracer.spanBuilder("SET")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME,
                DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS)
            .setAttribute(DbAttributes.DB_OPERATION_NAME, "SET")
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

    @Override
    public void close() {
        pool.close();
    }
}
