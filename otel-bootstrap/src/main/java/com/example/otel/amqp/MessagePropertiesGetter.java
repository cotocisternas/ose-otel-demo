package com.example.otel.amqp;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.amqp.core.MessageProperties;

/**
 * {@link TextMapGetter} that reads W3C trace-context header values from a
 * Spring AMQP {@link MessageProperties} carrier, defensively normalizing
 * any non-String storage (AMQP {@code LongString}, raw {@code byte[]})
 * back to a {@link String} via {@link Object#toString()}.
 *
 * <p>This is the symmetric counterpart of {@link MessagePropertiesSetter}
 * and the second half of CRITICAL pitfall #2's mitigation
 * ({@code .planning/research/PITFALLS.md}). Even though our own producer
 * always writes Strings, AMQP brokers sometimes deliver header values
 * wrapped in {@code LongStringHelper.ByteArrayLongString} (which extends
 * {@code AbstractMap.SimpleEntry} and whose {@code toString()} returns the
 * UTF-8 decoded form). The {@code .toString()} call is idempotent for a
 * real {@code String}, well-defined for {@code LongString}, and degrades
 * gracefully (UTF-8 decode) for unexpected {@code byte[]} arrivals.
 *
 * <p>Without this normalization, an {@code instanceof String} check on the
 * header value would fail for {@code LongString} arrivals → the W3C
 * extract returns {@code Context.root()} → the consumer span starts a NEW
 * root trace, recreating the Phase 2 broken state.
 *
 * <p>No Spring annotations on this class (per CONTEXT.md D-01 — the
 * propagation classes are pure Java; Spring wiring lives in each service's
 * {@code RabbitConfig.java}).
 */
public class MessagePropertiesGetter implements TextMapGetter<MessageProperties> {

    @Override
    public Iterable<String> keys(MessageProperties carrier) {
        // MessageProperties.getHeaders() returns a non-null Map<String, Object>
        // (HashMap-backed) — verified against Spring AMQP v3.2.8 source.
        return carrier.getHeaders().keySet();
    }

    @Override
    public String get(MessageProperties carrier, String key) {
        // OTel TextMapGetter spec: carrier MAY be null. Defensive guard.
        if (carrier == null) {
            return null;
        }
        Object raw = carrier.getHeader(key);
        // Defensive .toString() normalises String / LongString / byte[]
        // arrivals (PITFALLS.md #2). Returns null if the header is absent.
        return raw == null ? null : raw.toString();
    }
}
