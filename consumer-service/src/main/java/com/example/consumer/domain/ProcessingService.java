package com.example.consumer.domain;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.springframework.stereotype.Service;

/**
 * Domain layer — simulates downstream order-processing work.
 *
 * <p>Phase 2 wraps {@link #process(Map)} in an INTERNAL span (TRACE-06)
 * named "ProcessingService.process" using the D-01 pure-inline template,
 * with a forward-compatible D-03 catch that records exceptions on the
 * span as ERROR.
 *
 * <p>Phase 3 added the deterministic 10%-failure path (APP-04 + TRACE-09):
 * an in-memory {@link AtomicInteger} counter increments on every call;
 * on every 10th call, the method throws a custom {@link ProcessingFailedException}.
 * The Phase 2 D-03 catch reacts to it ({@code recordException} +
 * {@code setStatus(StatusCode.ERROR)} + rethrow) — NO restructuring of
 * the catch block was required (D-11).
 *
 * <p>The rethrown exception propagates up through {@code OrderListener.onOrder}
 * (Phase 3 plan 03-03 made it a thin pass-through with no catch) → caught
 * by {@code TracingMessageListenerAdvice} (Phase 3 plan 03-01) which
 * records it on the CONSUMER span and rethrows → Spring AMQP container
 * NACKs → with {@code defaultRequeueRejected=false} (Phase 3 plan 03-03),
 * the broker drops the message (no DLX per PROJECT.md). Both INTERNAL
 * and CONSUMER spans show ERROR status in Tempo for the 10th order
 * (ROADMAP SC #3).
 */
@Service
public class ProcessingService {
    private final Tracer tracer;

    // Phase 3: deterministic 10%-failure trigger (APP-04 + D-11). Spring
    // @Service is singleton scope by default — the counter persists across
    // messages within one JVM run; resets per `mise run dev` start (fine
    // for fresh demo sessions).
    private final AtomicInteger counter = new AtomicInteger();

    public ProcessingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void process(Map<String, Object> order) {
        // ---- D-01 inline span template (INTERNAL) ----
        Span span = tracer.spanBuilder("ProcessingService.process")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Phase 3: deterministic 10%-failure trigger (APP-04 + D-11).
            // Verbatim message wording from APP-04: "every 10th order".
            int n = counter.incrementAndGet();
            if (n % 10 == 0) {
                throw new ProcessingFailedException(
                    "Deterministic failure on order #" + n + " (every 10th order)");
            }
            // Successful processing path — Phase 1 placeholder retained
            // (simulated domain work, in-memory).
        } catch (RuntimeException e) {
            // D-03 catch shape from Phase 2 — preserved unchanged.
            // ProcessingFailedException extends RuntimeException, so it
            // is caught here (TRACE-09). The advice's catch (Throwable)
            // also records this on the CONSUMER span when the rethrow
            // bubbles up.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
