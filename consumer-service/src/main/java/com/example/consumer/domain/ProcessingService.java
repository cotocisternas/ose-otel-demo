package com.example.consumer.domain;

import java.util.Map;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.springframework.stereotype.Service;

/**
 * Domain layer — simulates downstream order-processing work.
 *
 * Phase 2 wraps {@link #process(Map)} in an INTERNAL span (TRACE-06)
 * named "ProcessingService.process" using the D-01 pure-inline template.
 * The body is currently empty (the Phase 1 placeholder comments) — Phase 3
 * adds the deterministic 10%-failure path (APP-04) and the
 * recordException-driven error span (TRACE-09). The D-03 catch shape is
 * ALREADY in place; Phase 3 only provides the throw site.
 */
@Service
public class ProcessingService {
    private final Tracer tracer;

    public ProcessingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void process(Map<String, Object> order) {
        // ---- D-01 inline span template (INTERNAL) ----
        //
        // Pure inline. No helper. The body is currently empty (Phase 1
        // placeholder); the placeholder comment block has moved INSIDE
        // the try so the span actually wraps something.
        Span span = tracer.spanBuilder("ProcessingService.process")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Phase 1: simulated domain work, in-memory only.
            // Phase 3 wires up the deterministic 10% failure path (APP-04).
        } catch (RuntimeException e) {
            // D-03: catch block present in Phase 2 even though no fail path
            // exists yet (APP-04 lands in Phase 3 alongside TRACE-09's
            // recordException + setStatus(ERROR)).
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
