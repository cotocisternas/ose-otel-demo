package com.example.otel.http;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.http.HttpHeaders;

/**
 * {@link TextMapSetter} that writes W3C trace-context header values into a
 * Spring HTTP {@link HttpHeaders} carrier.
 *
 * <p>This is the HTTP counterpart of {@code MessagePropertiesSetter} in the
 * {@code com.example.otel.amqp} package — structurally identical, with the
 * carrier type substituted from {@code MessageProperties} to {@code HttpHeaders}.
 * {@link TracingClientHttpRequestInterceptor} holds a singleton instance of this
 * class and passes it to the composite propagator's {@code inject()} call.
 *
 * <p><strong>Why {@link HttpHeaders#set} and not {@link HttpHeaders#add}.</strong>
 * {@code HttpHeaders.add(key, value)} appends a new header value; if a
 * {@code traceparent} header is already present (e.g., set by an earlier
 * interceptor), the result would be two {@code traceparent} values. The W3C
 * Trace Context spec requires exactly one {@code traceparent} header per request.
 * {@code HttpHeaders.set(key, value)} overwrites any existing value for the key,
 * guaranteeing a single clean header per propagation call.
 *
 * <p><strong>Null-carrier guard.</strong> The {@link TextMapSetter} contract
 * allows the carrier to be {@code null}. The guard prevents an NPE from
 * propagating into the OTel SDK propagator call.
 *
 * <p>No Spring annotations on this class — it is a pure Java adapter. Spring
 * wiring lives in each service's {@code HttpClientConfig.java}.
 */
public class HttpHeadersSetter implements TextMapSetter<HttpHeaders> {

    @Override
    public void set(HttpHeaders carrier, String key, String value) {
        // OTel TextMapSetter spec: carrier MAY be null. Defensive guard
        // — without this, propagator.inject(...) leaks an NPE from inside
        // the SDK call.
        if (carrier == null) {
            return;
        }
        // HttpHeaders.set(String, String) overwrites any existing value for the key.
        // W3CTraceContextPropagator always passes String values through set().
        carrier.set(key, value);
    }
}
