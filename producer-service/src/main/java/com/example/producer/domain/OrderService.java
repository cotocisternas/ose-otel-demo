package com.example.producer.domain;

import java.util.Map;
import java.util.UUID;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.springframework.stereotype.Service;

import com.example.producer.messaging.OrderPublisher;

/**
 * Domain layer — orchestrates the place-an-order flow.
 *
 * Phase 2 wraps {@link #place(Map)} in an INTERNAL span (TRACE-06) named
 * "OrderService.place" using the D-01 pure-inline template — boilerplate
 * is the lesson here; do NOT extract this into a helper.
 */
@Service
public class OrderService {
    private final OrderPublisher publisher;
    private final Tracer tracer;

    public OrderService(OrderPublisher publisher, Tracer tracer) {
        this.publisher = publisher;
        this.tracer = tracer;
    }

    public String place(Map<String, Object> payload) {
        // ---- D-01 inline span template (INTERNAL) ----
        //
        // Pure inline. No helper. No AOP. The full try/Scope/try/catch/finally
        // idiom is REPEATED at every span site (5 sites in Phase 2 across
        // both services) so workshop attendees read the SDK calls in business
        // code and understand exactly which lines do what.
        //
        // INTERNAL span name follows the D-04 convention: ClassName.method.
        // No semconv attributes mandated for INTERNAL spans (D-04) — those
        // belong on SERVER / CLIENT / PRODUCER / CONSUMER kinds where the
        // OTel spec defines stable attribute keys.
        Span span = tracer.spanBuilder("OrderService.place")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            String orderId = UUID.randomUUID().toString();
            publisher.publish(orderId, payload);
            return orderId;
        } catch (RuntimeException e) {
            // D-03: catch block present in Phase 2 even though no fail path
            // exists yet (APP-04's deterministic 10% failure lands in Phase 3).
            // Keeping the structural shape now means Phase 3 only adds the
            // failure path — no restructuring of these 8 lines.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
