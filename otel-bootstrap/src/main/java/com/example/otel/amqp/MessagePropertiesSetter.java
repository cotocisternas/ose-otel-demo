package com.example.otel.amqp;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.amqp.core.MessageProperties;

/**
 * {@link TextMapSetter} that writes W3C trace-context header values into a
 * Spring AMQP {@link MessageProperties} carrier as <strong>String</strong>
 * values — never {@code byte[]} or any binary form.
 *
 * <p>This discipline neutralises CRITICAL pitfall #2 from
 * {@code .planning/research/PITFALLS.md}: if header values are written as
 * {@code byte[]}, the consumer-side {@code MessageProperties.getHeader(key)}
 * returns a {@code LongStringHelper.ByteArrayLongString} whose value cannot
 * be matched by an {@code instanceof String} check — the W3C extract
 * silently returns {@code Context.root()}, and the consumer span
 * starts a NEW root trace instead of joining the producer's trace.
 *
 * <p>The OpenTelemetry {@code W3CTraceContextPropagator} always passes
 * {@code String} values through {@link #set}, so this implementation just
 * forwards them to {@link MessageProperties#setHeader(String, Object)}
 * (the headers map is a {@code HashMap<String, Object>}); the AMQP wire
 * encoding turns the String into an AMQP {@code longstr} field that the
 * consumer's {@link MessagePropertiesGetter} can decode back to a String.
 *
 * <p>No Spring annotations on this class (per CONTEXT.md D-01 — the
 * propagation classes are pure Java; Spring wiring lives in each service's
 * {@code RabbitConfig.java}).
 */
public class MessagePropertiesSetter implements TextMapSetter<MessageProperties> {

    @Override
    public void set(MessageProperties carrier, String key, String value) {
        // OTel TextMapSetter spec: carrier MAY be null. Defensive guard
        // — without this, propagator.inject(...) leaks an NPE from inside
        // the SDK call.
        if (carrier == null) {
            return;
        }
        // value is always String here (W3CTraceContextPropagator only
        // passes String values through set). Round-trips cleanly to the
        // consumer (PITFALLS.md #2).
        carrier.setHeader(key, value);
    }
}
