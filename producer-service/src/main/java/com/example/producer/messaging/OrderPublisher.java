package com.example.producer.messaging;

import java.util.HashMap;
import java.util.Map;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.producer.config.RabbitConfig;

/**
 * Publishes OrderCreated to the orders direct exchange via Spring AMQP.
 *
 * Phase 2 wraps {@link #publish(String, Map)} in a PRODUCER span (TRACE-07)
 * named "orders.created publish" with the four messaging semconv attributes
 * (D-11). Phase 3 will REPLACE this inline span with the
 * TracingMessagePostProcessor from otel-bootstrap, which takes over both
 * the inject AND the PRODUCER span lifecycle — see CONTEXT.md D-09.
 */
@Component
public class OrderPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Tracer tracer;

    public OrderPublisher(RabbitTemplate rabbitTemplate, Tracer tracer) {
        this.rabbitTemplate = rabbitTemplate;
        this.tracer = tracer;
    }

    public void publish(String orderId, Map<String, Object> payload) {
        // ---- D-01 inline span template (PRODUCER) ----
        //
        // Span name = "<destination> <operation>" per OTel messaging semconv:
        // "orders.created publish" — the QUEUE name (D-04 + D-11 note: span-name
        // uses queue; routing-key is a separate attribute below).
        //
        // semconv 1.40.0 renamed messaging.operation → messaging.operation.type;
        // values: send|receive|process|create. We use MESSAGING_OPERATION_TYPE
        // (the current constant) and the SEND value enum. The deprecated
        // MESSAGING_OPERATION key + the literal "publish" value would
        // technically still work but flag deprecation warnings.
        Span span = tracer.spanBuilder("orders.created publish")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                RabbitConfig.QUEUE)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                RabbitConfig.ROUTING_KEY)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<String, Object> message = new HashMap<>(payload);
            message.put("orderId", orderId);
            // APP-02: publish via direct exchange + routing key.
            //
            // Phase 2 does NOT inject the traceparent header here — that's
            // Phase 3's headline lesson (PROP-01), which adds the
            // TracingMessagePostProcessor that runs ON RabbitTemplate's
            // setBeforePublishPostProcessors hook to inject W3C trace
            // context into MessageProperties.
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        } catch (RuntimeException e) {
            // D-03: catch block present in Phase 2 even though no fail path yet.
            // Spring's RabbitTemplate throws AmqpException (a RuntimeException)
            // on broker connectivity issues — the recordException pattern
            // captures those for Tempo's error-status display.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
