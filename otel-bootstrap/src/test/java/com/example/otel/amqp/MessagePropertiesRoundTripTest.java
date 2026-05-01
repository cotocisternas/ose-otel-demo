package com.example.otel.amqp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

/**
 * Pure unit test for {@link MessagePropertiesSetter} ↔
 * {@link MessagePropertiesGetter} round-trip.
 *
 * <p>This test is the lowest-level regression net for CRITICAL pitfall #2
 * ({@code .planning/research/PITFALLS.md}): if a future change has the
 * setter write {@code byte[]} (or has the getter assume the value is
 * always a {@link String}), this test fails — long before the broken
 * trace shows up in Tempo.
 *
 * <p>No Spring, no Testcontainers, no broker. {@link MessageProperties}
 * is a plain POJO backed by a {@code HashMap<String, Object>}; we
 * construct one directly and exercise the setter / getter contract.
 */
class MessagePropertiesRoundTripTest {

    private final MessagePropertiesSetter setter = new MessagePropertiesSetter();
    private final MessagePropertiesGetter getter = new MessagePropertiesGetter();

    @Test
    @DisplayName("setter writes a String value that the getter reads back identically (PITFALLS.md #2)")
    void roundTripStringHeader() {
        MessageProperties props = new MessageProperties();
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

        setter.set(props, "traceparent", traceparent);

        // The header MUST be stored as a String — the headers map is
        // HashMap<String, Object>; setHeader does no transformation.
        // Note: assign to Object local first to disambiguate AssertJ's
        // assertThat(Predicate) vs assertThat(IntPredicate) overloads
        // which both match a bare Object expression in some javac builds.
        Object stored = props.getHeader("traceparent");
        assertThat(stored)
            .isInstanceOf(String.class)
            .isEqualTo(traceparent);

        // Getter reads back identical value.
        assertThat(getter.get(props, "traceparent")).isEqualTo(traceparent);
    }

    @Test
    @DisplayName("getter normalises a non-String header value via .toString() (PITFALLS.md #2 — defensive)")
    void getterNormalisesNonStringHeader() {
        // Simulate an upstream that wrote a non-String value (e.g., a
        // misconfigured library that wrote byte[] or an AMQP LongString).
        // The getter should normalise via raw.toString() — not return null.
        MessageProperties props = new MessageProperties();
        props.setHeader("custom-non-string", 42);          // Integer; toString() → "42"

        assertThat(getter.get(props, "custom-non-string")).isEqualTo("42");
    }

    @Test
    @DisplayName("getter returns null for an absent header")
    void getterReturnsNullForAbsentHeader() {
        MessageProperties props = new MessageProperties();
        assertThat(getter.get(props, "absent")).isNull();
    }

    @Test
    @DisplayName("getter handles a null carrier defensively")
    void getterHandlesNullCarrier() {
        assertThat(getter.get(null, "traceparent")).isNull();
    }

    @Test
    @DisplayName("setter is a no-op on a null carrier (no NPE)")
    void setterHandlesNullCarrier() {
        // Should not throw; this is the OTel TextMapSetter spec contract.
        setter.set(null, "traceparent", "value");
    }

    @Test
    @DisplayName("getter.keys() exposes every header key (W3CTraceContextPropagator iterates these)")
    void keysExposesAllHeaders() {
        MessageProperties props = new MessageProperties();
        setter.set(props, "traceparent", "00-aaa-bbb-01");
        setter.set(props, "tracestate", "vendor=value");

        Iterable<String> keys = getter.keys(props);
        Iterator<String> iter = keys.iterator();
        assertThat(iter.hasNext()).isTrue();
        assertThat(keys).contains("traceparent", "tracestate");
    }
}
