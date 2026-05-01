package com.example.consumer.messaging;

import java.util.Map;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.consumer.config.RabbitConfig;
import com.example.consumer.domain.ProcessingService;

/**
 * AMQP listener — processes OrderCreated messages from the orders.created queue.
 *
 * Phase 2 wraps {@link #onOrder(Map)} in a CONSUMER span (TRACE-08) named
 * "orders.created process" that explicitly starts from Context.root()
 * (D-10) — the consumer is INTENTIONALLY in a separate trace from the
 * producer in Phase 2 (broken-then-fixed pedagogy). Phase 3 replaces the
 * Context.root() with propagator.extract(...) to join the traces — see
 * the multi-line teaching comment above the spanBuilder call.
 */
@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;
    private final Tracer tracer;

    public OrderListener(ProcessingService processingService, Tracer tracer) {
        this.processingService = processingService;
        this.tracer = tracer;
    }

    // APP-03: receive OrderCreated and simulate downstream domain work.
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrder(Map<String, Object> message) {
        // Phase 2: starting from Context.root() because no propagation yet —
        // Phase 3 replaces this with:
        //   Context extracted = propagator.extract(Context.root(), messageProperties, getter);
        // The structural shape stays IDENTICAL.
        //
        // (semconv 1.40.0 renamed messaging.operation → messaging.operation.type;
        // values: send|receive|process|create. We use MESSAGING_OPERATION_TYPE
        // and the PROCESS value enum.)
        Span span = tracer.spanBuilder("orders.created process")
            .setParent(Context.root())
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                RabbitConfig.QUEUE)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingOperationTypeIncubatingValues.PROCESS)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Object orderId = message.get("orderId");
            LOG.info("OrderCreated received: orderId={}", orderId);
            processingService.process(message);
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
