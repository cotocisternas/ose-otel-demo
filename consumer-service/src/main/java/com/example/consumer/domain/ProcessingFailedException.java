package com.example.consumer.domain;

/**
 * Thrown by {@link ProcessingService#process(java.util.Map)} on the
 * deterministic 10%-failure path (APP-04 — every 10th order fails).
 *
 * <p><strong>Pedagogical value (CONTEXT.md D-12).</strong> The
 * fully-qualified class name {@code com.example.consumer.domain.ProcessingFailedException}
 * surfaces as the {@code exception.type} attribute on the
 * {@code recordException} span event in Tempo (TRACE-09); the class
 * name itself is documentation. Workshop attendees opening the
 * 10th-order trace see the FQCN at the top of the exception event panel.
 *
 * <p>Extends {@link RuntimeException} (unchecked) so the listener thread
 * can rethrow it without a {@code throws} declaration on
 * {@code @RabbitListener public void onOrder(Map)}. Caught by Phase 2's
 * D-03 catch on the INTERNAL span (records + ERROR + rethrow); then
 * caught by Phase 3's {@code TracingMessageListenerAdvice} catch on the
 * CONSUMER span (records + ERROR + rethrow); then caught by Spring AMQP's
 * listener container — combined with {@code defaultRequeueRejected=false}
 * (D-13), the broker drops the message (no DLX per PROJECT.md).
 *
 * <p>No cause chain (single-arg constructor) — this is a deterministic
 * synthetic failure, not a wrap-and-rethrow.
 */
public class ProcessingFailedException extends RuntimeException {
    public ProcessingFailedException(String message) {
        super(message);
    }
}
