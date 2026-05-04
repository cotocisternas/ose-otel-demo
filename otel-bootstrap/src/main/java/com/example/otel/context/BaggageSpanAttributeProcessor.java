package com.example.otel.context;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Set;

/**
 * SpanProcessor that stamps allowlisted baggage keys as {@code baggage.<key>}
 * span attributes on every span start (Phase 16 / BAG-02).
 *
 * <p>Only keys in {@link #allowedKeys} are stamped. The allowlist prevents
 * cardinality explosion if arbitrary baggage entries are added (F7-3).
 *
 * <p>Reads baggage from {@code parentContext} (the context BEFORE the new span
 * is started), NOT from {@code Baggage.current()} (which would read the SAME
 * context — same result for in-thread baggage, but parentContext is more
 * correct per OTel spec: the processor receives the context that parented
 * this span).
 *
 * <p>Added FIRST in {@code OtelSdkConfiguration#buildTracerProvider(Resource)}
 * so baggage is stamped before {@code BatchSpanProcessor} enqueues the span.
 */
public class BaggageSpanAttributeProcessor implements SpanProcessor {

    private final Set<String> allowedKeys;

    public BaggageSpanAttributeProcessor(Set<String> allowedKeys) {
        this.allowedKeys = Set.copyOf(allowedKeys);
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        Baggage baggage = Baggage.fromContext(parentContext);
        for (String key : allowedKeys) {
            String value = baggage.getEntryValue(key);
            if (value != null) {
                span.setAttribute("baggage." + key, value);
            }
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // No-op — stamping happens at start only
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }
}
