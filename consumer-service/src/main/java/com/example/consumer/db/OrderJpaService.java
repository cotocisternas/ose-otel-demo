package com.example.consumer.db;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

/**
 * Service layer for JPA-backed order persistence (Phase 14 DBSP-02).
 *
 * <p>This class is intentionally thin — pure business logic with no OTel SDK calls.
 * The {@link com.example.consumer.observability.TransactionSpanAspect} wraps
 * this method via AOP to emit the INTERNAL transaction span ({@code @Order(HIGHEST_PRECEDENCE)}
 * ensures it wraps OUTSIDE the {@code @Transactional} proxy — see RESEARCH.md Pitfall 2).
 * The {@link com.example.consumer.observability.TracingRepositoryAspect} wraps each
 * repository call to emit CLIENT spans.
 *
 * <p><b>Idempotency (D-J3 + D-J4):</b> {@code findByOrderId} first (SELECT — TracingRepositoryAspect
 * wraps it), then {@code save} only if the entity is absent (INSERT — TracingRepositoryAspect
 * wraps it). If the entity exists, the save is skipped — mirrors Phase 8's
 * {@code ON CONFLICT DO NOTHING} at the application layer instead of SQL.
 *
 * <p><b>Why no {@code Tracer} injection here?</b> The AOP aspects handle all spans.
 * Keeping this class free of OTel SDK calls is the Phase 14 teaching point:
 * "OTel instrumentation can be added without modifying existing business logic."
 *
 * <p><b>Trace waterfall for a new order:</b>
 * {@code INTERNAL OrderJpaService.persist (TransactionSpanAspect)}
 *   ├── {@code CLIENT OrderJpaRepository.findByOrderId (TracingRepositoryAspect)}
 *   └── {@code CLIENT OrderJpaRepository.save (TracingRepositoryAspect)}
 */
@Service
public class OrderJpaService {

    private final OrderJpaRepository repository;

    public OrderJpaService(OrderJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist an order idempotently.
     *
     * @param orderId  the business order identifier (unique business key)
     * @param payload  the full order payload as JSON (stored as TEXT)
     * @param traceId  the W3C trace_id from {@code Span.current().getSpanContext().getTraceId()}
     *                 at call time — D-J8 bridge column for Tempo cross-referencing
     */
    @Transactional
    public void persist(String orderId, String payload, String traceId) {
        // D-J3: check existence first (SELECT span from TracingRepositoryAspect)
        Optional<Order> existing = repository.findByOrderId(orderId);
        if (existing.isEmpty()) {
            // D-J4: save only if not found (INSERT span from TracingRepositoryAspect)
            // Mirrors Phase 8's ON CONFLICT DO NOTHING at the application layer.
            repository.save(new Order(orderId, payload, Instant.now(), traceId));
        }
        // If present → idempotent skip: no save, no error.
    }
}
