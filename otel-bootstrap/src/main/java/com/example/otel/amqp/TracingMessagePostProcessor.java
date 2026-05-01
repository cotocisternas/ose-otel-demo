package com.example.otel.amqp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
import org.springframework.amqp.core.Correlation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

/**
 * Producer-side AMQP context propagation: opens a {@link SpanKind#PRODUCER}
 * span, injects W3C trace context headers ({@code traceparent},
 * {@code tracestate}) into {@link MessageProperties} via a
 * {@link TextMapPropagator}, and ends the span — all <em>before</em>
 * {@link org.springframework.amqp.rabbit.core.RabbitTemplate} writes the
 * message to the AMQP wire.
 *
 * <p><strong>Span ownership (CONTEXT.md D-05).</strong> This class OWNS
 * the entire PRODUCER span. Phase 2's inline PRODUCER span in
 * {@code OrderPublisher.publish(...)} is deleted as part of Phase 3 —
 * {@code OrderPublisher.publish(...)} becomes a thin
 * {@code rabbitTemplate.convertAndSend(...)} call.
 *
 * <p><strong>Inject-only span lifetime (CONTEXT.md D-06).</strong> The
 * span tightly wraps the {@link TextMapPropagator#inject} call inside a
 * {@code try / finally}. The span ends BEFORE
 * {@code RabbitTemplate.send(...)} talks to the broker. This matches OTel
 * auto-instrumentation convention for Kafka / JMS / AMQP — broker-level
 * send errors propagate up the call stack and are caught by
 * {@code OrderService.place(...)}'s INTERNAL span via Phase 2's D-03 catch.
 *
 * <p><strong>Semconv-correct destination naming (CONTEXT.md D-07 +
 * RESEARCH FLAG #2).</strong> Span name is
 * {@code "<exchange> publish"} (e.g., {@code "orders publish"}); the
 * {@code messaging.destination.name} attribute is the EXCHANGE — not the
 * queue (Phase 2 used queue, Phase 3 corrects to exchange per OTel
 * messaging semconv RabbitMQ profile). The 4-arg
 * {@link #postProcessMessage(Message, Correlation, String, String)}
 * overload — added in Spring AMQP 2.3.4 and invoked by
 * {@code RabbitTemplate.doSend(...)} when registered via
 * {@code setBeforePublishPostProcessors(...)} — provides the exchange and
 * routing key directly; no plumbing through a separate channel.
 *
 * <p><strong>Per-service Tracer scope (CONTEXT.md D-03).</strong> The
 * {@link Tracer} is injected per service ({@code com.example.producer}),
 * so spans created here still appear under the producer's instrumentation
 * scope in Tempo — NOT under a new {@code com.example.otel.amqp} scope.
 *
 * <p><strong>Propagator reuse (CONTEXT.md D-04).</strong> The propagator
 * is read from {@code openTelemetry.getPropagators().getTextMapPropagator()}
 * — Phase 2 already wired the composite
 * {@code W3CTraceContextPropagator + W3CBaggagePropagator}. This class
 * does NOT construct a fresh {@code W3CTraceContextPropagator.getInstance()}.
 *
 * <p><strong>String header values (PITFALLS.md #2).</strong> The
 * {@link MessagePropertiesSetter} singleton field writes header values as
 * {@link String} — never {@code byte[]} — so the consumer-side getter can
 * round-trip them cleanly.
 */
public class TracingMessagePostProcessor implements MessagePostProcessor {

    // Stateless / thread-safe; one instance per JVM is sufficient.
    private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TracingMessagePostProcessor(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }

    /**
     * 4-arg overload — added in Spring AMQP 2.3.4. Invoked by
     * {@code RabbitTemplate.doSend(...)} for processors registered via
     * {@code setBeforePublishPostProcessors(...)} (verified against
     * Spring AMQP v3.2.8 source, RESEARCH FLAG #2).
     */
    @Override
    public Message postProcessMessage(Message message, Correlation correlation,
                                      String exchange, String routingKey) {
        MessageProperties props = message.getMessageProperties();
        Span span = tracer.spanBuilder(exchange + " publish")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                exchange)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                routingKey)
            .startSpan();
        // try / finally only — no catch. propagator.inject(...) over a
        // String-valued setter is essentially infallible (the setter just
        // calls HashMap.put). Broker-level send errors happen LATER in
        // RabbitTemplate.send and are caught by the INTERNAL span in
        // OrderService.place(...) (Phase 2 D-03 catch).
        try (Scope scope = span.makeCurrent()) {
            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            propagator.inject(Context.current(), props, SETTER);
            return message;
        } finally {
            span.end();
        }
    }

    /**
     * 1-arg overload — defensive default. {@code RabbitTemplate.doSend(...)}
     * always invokes the 4-arg overload above for
     * {@code beforePublishPostProcessors}; this method exists only to
     * satisfy the {@link MessagePostProcessor} interface contract. If it
     * IS reached, fall through with no instrumentation — the destination
     * identity is unknown at this layer, so we cannot correctly name the
     * span or set the destination attribute.
     */
    @Override
    public Message postProcessMessage(Message message) {
        return message;
    }
}
