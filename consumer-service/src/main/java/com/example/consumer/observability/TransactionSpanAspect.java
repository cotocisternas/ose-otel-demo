package com.example.consumer.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that wraps {@link com.example.consumer.db.OrderJpaService#persist}
 * in an {@link SpanKind#INTERNAL} span — the transaction-level parent span
 * (Phase 14 DBSP-04).
 *
 * <p><b>Why {@code @Order(Ordered.HIGHEST_PRECEDENCE)} (F5-3)?</b>
 * Spring's {@code @Transactional} proxy has default AOP order {@code LOWEST_PRECEDENCE}
 * ({@code Integer.MAX_VALUE}). A custom {@code @Aspect} with
 * {@code @Order(Ordered.HIGHEST_PRECEDENCE)} = {@code Integer.MIN_VALUE} is applied
 * FIRST in the proxy chain — meaning this aspect wraps OUTERMOST. This guarantees:
 * <ol>
 *   <li>The transaction span covers the FULL transaction lifecycle including commit/rollback.</li>
 *   <li>When the deterministic 10% failure path throws a {@code ProcessingFailedException},
 *       the catch block here fires AFTER the transaction rolls back — so
 *       {@code span.setStatus(StatusCode.ERROR)} is set on the outermost span, and
 *       attendees see the INTERNAL span as ERROR in Tempo.</li>
 * </ol>
 * Without {@code @Order(HIGHEST_PRECEDENCE)}, the span ends before commit/rollback and
 * rollbacks show {@code status=OK} (the most common gotcha when instrumenting @Transactional).
 *
 * <p><b>Why target the concrete service method with {@code execution()}, not {@code bean()}?</b>
 * {@link com.example.consumer.db.OrderJpaService} is a concrete Spring {@code @Service}
 * bean — {@code execution()} is reliable on concrete classes (unlike on JDK proxy interfaces).
 * Using the concrete class FQCN makes the pointcut explicit and prevents accidental
 * interception of other services.
 *
 * <p><b>Span name:</b> {@code "OrderJpaService.persist"} — matches the method being wrapped.
 * Attendees see this span as the INTERNAL parent of the two CLIENT spans in Tempo.
 *
 * <p><b>Trace waterfall in Tempo (new order):</b>
 * <pre>
 * CONSUMER span (TracingMessageListenerAdvice)
 *   └── INTERNAL ProcessingService.process (existing)
 *         └── INTERNAL OrderJpaService.persist  ← this aspect
 *               ├── CLIENT OrderJpaRepository.findByOrderId  ← TracingRepositoryAspect
 *               └── CLIENT OrderJpaRepository.save           ← TracingRepositoryAspect
 * </pre>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // Integer.MIN_VALUE — wraps OUTSIDE @Transactional proxy (F5-3)
public class TransactionSpanAspect {

    private final Tracer tracer;

    public TransactionSpanAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Wrap the {@code persist()} method of {@link com.example.consumer.db.OrderJpaService}
     * in an INTERNAL span that captures the full @Transactional boundary.
     *
     * <p>Uses {@code execution()} on the concrete service class — reliable here because
     * {@code OrderJpaService} is a concrete Spring bean, not a JDK proxy interface.
     */
    @Around("execution(* com.example.consumer.db.OrderJpaService.persist(..))")
    public Object traceTransactionBoundary(ProceedingJoinPoint pjp) throws Throwable {
        Span span = tracer.spanBuilder("OrderJpaService.persist")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return pjp.proceed();
        } catch (Throwable t) {
            // Rollback — record the exception and set ERROR status so the
            // transaction boundary is visible as an error span in Tempo (DBSP-04).
            span.recordException(t);
            span.setStatus(StatusCode.ERROR);
            throw t;
        } finally {
            span.end();
        }
    }
}
