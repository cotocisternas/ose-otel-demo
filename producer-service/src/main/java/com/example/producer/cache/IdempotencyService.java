package com.example.producer.cache;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Producer-side idempotency check using Valkey (Phase 8 DB-CACHE-02).
 *
 * <p>On every {@code POST /orders}, the controller calls {@link #checkAndMark(String)}
 * with the request's {@code Idempotency-Key} header value (or a client-supplied
 * {@code orderId} from the body). The method delegates to {@link InstrumentedJedisPool}
 * which opens a CLIENT span and executes an atomic Valkey SET NX EX command.
 *
 * <p><b>Two metric instruments (Phase 8 METRIC-08-01):</b>
 * <ul>
 *   <li>{@code idempotency.cache.hit} — increments when the key already existed
 *       (duplicate request — idempotency guard fired)</li>
 *   <li>{@code idempotency.cache.miss} — increments when the key was newly set
 *       (fresh request — let it through)</li>
 * </ul>
 *
 * These two counters contrast with Phase 4's {@code orders.created} counter:
 * that one fires after the AMQP publish; these two fire BEFORE the order is
 * placed — at the HTTP entry gate.
 *
 * <p><b>Result type:</b> the {@link Result} enum lets the controller make the
 * HTTP response decision (202 vs 409) without coupling to boolean return-value
 * conventions.
 */
@Service
public class IdempotencyService {

    /** Result of an idempotency check. */
    public enum Result {
        /** Key was newly set — proceed with the order. */
        NEW,
        /** Key already existed — duplicate request; return 409. */
        SEEN
    }

    private final InstrumentedJedisPool pool;
    private final long ttlSeconds;
    private final LongCounter cacheHit;
    private final LongCounter cacheMiss;

    public IdempotencyService(
            Tracer tracer,
            Meter meter,
            @Value("${valkey.host}") String valkeyHost,
            @Value("${valkey.port}") int valkeyPort,
            @Value("${valkey.idempotency-ttl-seconds:3600}") long ttlSeconds) {
        this.pool       = new InstrumentedJedisPool(tracer, valkeyHost, valkeyPort);
        this.ttlSeconds = ttlSeconds;

        // idempotency.cache.hit — fires when duplicate key detected (SEEN path).
        // Surfaces in Mimir as idempotency_cache_hit_total.
        this.cacheHit = meter.counterBuilder("idempotency.cache.hit")
            .setDescription("Duplicate POST /orders requests blocked by idempotency check")
            .setUnit("1")
            .build();

        // idempotency.cache.miss — fires when new key set (NEW path).
        // Surfaces in Mimir as idempotency_cache_miss_total.
        this.cacheMiss = meter.counterBuilder("idempotency.cache.miss")
            .setDescription("New (non-duplicate) POST /orders requests passing idempotency check")
            .setUnit("1")
            .build();
    }

    /**
     * Check whether {@code idempotencyKey} has been seen recently.
     *
     * <p>Stores the key under {@code "idempotency:<key>"} in Valkey with the
     * configured TTL. The first call for a given key within the TTL window
     * returns {@link Result#NEW}; subsequent calls within the same window
     * return {@link Result#SEEN}.
     *
     * @param idempotencyKey unique identifier from the caller (header or body field)
     */
    public Result checkAndMark(String idempotencyKey) {
        String valkeyKey = "idempotency:" + idempotencyKey;
        boolean isNew = pool.setIfAbsent(valkeyKey, "1", ttlSeconds);
        if (isNew) {
            cacheMiss.add(1, Attributes.of(AttributeKey.stringKey("result"), "new"));
            return Result.NEW;
        } else {
            cacheHit.add(1, Attributes.of(AttributeKey.stringKey("result"), "seen"));
            return Result.SEEN;
        }
    }

    @PreDestroy
    public void close() {
        pool.close();
    }
}
