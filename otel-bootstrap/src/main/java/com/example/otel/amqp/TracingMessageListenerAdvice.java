package com.example.otel.amqp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * Consumer-side AMQP context propagation: extracts W3C trace context from
 * incoming {@link MessageProperties}, opens a {@link SpanKind#CONSUMER}
 * span parented to the extracted producer context, and runs the
 * downstream {@code @RabbitListener}-annotated method body inside that
 * span's {@link Scope}.
 *
 * <p><strong>Wiring (CONTEXT.md D-08).</strong> Registered on the
 * consumer-service's {@code SimpleRabbitListenerContainerFactory} via
 * {@code setAdviceChain(this)}. The factory MUST be a Configurer-aided
 * user-defined bean named exactly {@code rabbitListenerContainerFactory}
 * (lowercase r) — see {@code RabbitConfig.rabbitListenerContainerFactory(...)}.
 *
 * <p><strong>The load-bearing line (ROADMAP SC #1).</strong>
 * {@code spanBuilder(...).setParent(extracted)} is the SINGLE LINE that
 * makes {@code consumer.parentSpanId == producer.spanId}. Without it,
 * even with header injection on the producer side, the consumer span
 * would still start a new root trace (Phase 2's broken state).
 *
 * <p><strong>MethodInvocation argument layout (RESEARCH FLAG #3).</strong>
 * The advice chain wraps {@code ContainerDelegate.invokeListener(Channel
 * channel, Object data)} — verified against Spring AMQP v3.2.8 source.
 * So {@code inv.getArguments()[0]} is the {@code Channel}, and
 * {@code inv.getArguments()[1]} is the payload. For non-batch listeners
 * (the only kind this workshop covers) the payload is a {@link Message};
 * for batch listeners (out of scope) it would be a {@code List<Message>}.
 * The defensive {@code instanceof Message} guard skips tracing for the
 * batch case (Pitfall #6) instead of throwing a {@code ClassCastException}.
 *
 * <p><strong>Synchronous, same-thread execution (RESEARCH FLAG #1).</strong>
 * Spring AMQP's listener container does NOT switch threads between this
 * advice and the user method body — verified against
 * {@code AbstractMessageListenerContainer.doInvokeListener(...)} →
 * {@code MessagingMessageListenerAdapter.onMessage(...)} call chain. The
 * {@link Scope#makeCurrent()} opened here IS visible to the user
 * {@code onOrder(...)} body — Phase 5's MDC injector will pick up the
 * {@code trace_id} / {@code span_id} from {@code Span.current()} when
 * the {@code LOG.info(...)} fires inside the listener.
 *
 * <p><strong>Per-service Tracer scope (CONTEXT.md D-03).</strong> The
 * {@link Tracer} is injected per service ({@code com.example.consumer}),
 * so the CONSUMER span appears under the consumer's instrumentation scope
 * in Tempo — NOT under {@code com.example.otel.amqp}.
 *
 * <p><strong>Catch shape (CONTEXT.md D-10).</strong>
 * {@code catch (Throwable t)} matches {@code MethodInterceptor.invoke}'s
 * {@code throws Throwable}; {@code recordException(t) + setStatus(ERROR)
 * + throw t} mirrors Phase 2's D-03 catch on the INTERNAL span. The
 * rethrow lets Spring AMQP's listener container handle the NACK; combined
 * with {@code defaultRequeueRejected(false)} on the factory (D-13), failed
 * messages are dropped (no DLX per PROJECT.md). The CONSUMER span carries
 * the exception event — workshop attendees see ERROR status + the
 * {@code exception.type} attribute in Tempo (TRACE-09 + ROADMAP SC #3).
 */
public class TracingMessageListenerAdvice implements MethodInterceptor {

    // Stateless / thread-safe; one instance per JVM is sufficient.
    private static final MessagePropertiesGetter GETTER = new MessagePropertiesGetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TracingMessageListenerAdvice(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }

    @Override
    public Object invoke(MethodInvocation inv) throws Throwable {
        // ContainerDelegate.invokeListener(Channel channel, Object data):
        // args[0] = Channel; args[1] = data (Message for non-batch listeners).
        Object data = inv.getArguments()[1];
        if (!(data instanceof Message message)) {
            // Batch listener (List<Message>) or unexpected shape — skip
            // tracing, proceed without wrapping. This phase doesn't teach
            // batch listeners (Pitfall #6).
            return inv.proceed();
        }
        MessageProperties props = message.getMessageProperties();
        TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
        Context extracted = propagator.extract(Context.current(), props, GETTER);

        // Inbound exchange + routing key are populated on consumed messages
        // (Spring AMQP MessageProperties.getReceivedExchange / getReceivedRoutingKey).
        String exchange = props.getReceivedExchange();
        String routingKey = props.getReceivedRoutingKey();

        Span span = tracer.spanBuilder(exchange + " process")
            .setParent(extracted)                          // <-- LOAD-BEARING (ROADMAP SC #1)
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                exchange)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingOperationTypeIncubatingValues.PROCESS)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                routingKey)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return inv.proceed();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR);
            throw t;
        } finally {
            span.end();
        }
    }
}
