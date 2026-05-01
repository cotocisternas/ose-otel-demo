package com.example.consumer.observability;

import java.util.concurrent.ThreadLocalRandom;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Registers the {@code orders.queue.depth.estimate} ObservableGauge
 * (METRIC-04) at consumer startup.
 *
 * <p><b>Why a separate {@link Component} (D-18b)?</b> The gauge registration
 * is one-shot side-effect boilerplate, not part of the SDK pipeline build.
 * Hosting it here keeps {@code OtelSdkConfiguration.java} symmetrical with
 * the producer's file (D-02 mirror property) — no consumer-only
 * {@code @PostConstruct} that doesn't exist on the producer side. The
 * planner choice between D-18a (inline {@code @PostConstruct} in
 * OtelSdkConfiguration) and D-18b (this class) is documented in
 * 04-CONTEXT.md and 04-PATTERNS.md; the recommendation favors this shape
 * because it preserves the duplication-pedagogy of Phase 2 D-01 / DOC-05.
 *
 * <p><b>Why {@code ofLongs()} (D-19)?</b> The synthetic value is an
 * {@code int} and the gauge name carries {@code .estimate} (whole-number
 * connotation); integer queue depths are the conventional shape for
 * messaging gauges. The default-flavor {@code DoubleGaugeBuilder} would
 * silently coerce {@code int -> double} without communicating intent.
 * {@link ObservableLongGauge} + {@code ObservableLongMeasurement.record(long)}
 * keeps the type honest.
 *
 * <p><b>Why {@link ThreadLocalRandom} (D-17)?</b> The callback fires on
 * the {@code PeriodicMetricReader} worker thread once per 10-second
 * interval (Plan 04-01 D-03). {@code ThreadLocalRandom} is documented
 * thread-safe, has no shared state, and creates no coupling to Phase 3's
 * {@code AtomicInteger} on {@code ProcessingService} (APP-04's
 * deterministic-failure counter). A real queue-depth measurement would
 * require polling the RabbitMQ Management API at
 * {@code http://localhost:15672/api/queues/%2F/orders.created} and
 * parsing JSON — non-trivial integration that distracts from the SDK
 * lesson. METRIC-04 explicitly spec's "synthetic queue-depth value" so
 * attendees see all three instrument shapes (Counter / Histogram /
 * ObservableGauge) without infrastructure-glue overhead.
 *
 * <p><b>Callback discipline.</b> The callback should be cheap and
 * side-effect-free. The SDK invokes it on every collection cycle whether
 * or not anyone is querying the value (push model — push to OTLP every
 * 10s). Long-running work in the callback would block the meter reader
 * thread and delay metric exports for ALL instruments registered on this
 * {@code SdkMeterProvider}, not just this gauge.
 *
 * <p><b>Why is this consumer-side, not producer-side (D-16)?</b>
 * Semantically, "queue depth" is what the consumer sees draining; the
 * producer publishes to an exchange, not directly to a queue. This also
 * gives the consumer's {@code SdkMeterProvider} a real business-level
 * instrument to emit (it has no Counter and no Histogram from Phase 4),
 * giving symmetric pedagogical surface across producer (Counter +
 * Histogram) and consumer (ObservableGauge).
 */
@Component
public class QueueDepthGauge {

    private final ObservableLongGauge gauge;

    public QueueDepthGauge(Meter meter) {
        // Build + register the gauge in the constructor. The gauge handle
        // returned by buildWithCallback is what we hold onto so we can
        // close() it explicitly in @PreDestroy below — closing the handle
        // is what unregisters the callback from the SdkMeterProvider.
        //
        // Name "orders.queue.depth.estimate" — METRIC-04 locked. The
        // OTel-to-Prometheus exporter mangles dots to underscores; this
        // surfaces in Mimir as `orders_queue_depth_estimate`.
        //
        // Unit "{messages}" follows OTel's curly-brace convention for
        // dimensionless or non-SI units. Some backends prefer "1" or
        // empty; "{messages}" is the most readable for a workshop.
        //
        // .ofLongs() — see D-19 in the class JavaDoc above.
        //
        // The lambda body is the WHOLE callback. ThreadLocalRandom.current()
        // is allocation-free (TLS-cached); nextInt(0, 50) returns int in
        // [0, 50). measurement.record(long) accepts the int via widening.
        this.gauge = meter.gaugeBuilder("orders.queue.depth.estimate")
            .setDescription("Synthetic queue-depth estimate (workshop demo, not a real measurement)")
            .setUnit("{messages}")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(ThreadLocalRandom.current().nextInt(0, 50)));
    }

    /**
     * Closes the gauge so the SDK stops invoking the callback at shutdown.
     *
     * <p>The {@code SdkMeterProvider}'s {@code destroyMethod="close"}
     * cascade (Phase 2 D-15 / Plan 04-01 D-07) handles this transitively
     * — when Spring closes the {@code OpenTelemetry} bean, it cascades to
     * {@code SdkMeterProvider.shutdown()}, which closes all registered
     * instruments including this one. So strictly speaking this method
     * is defensive: closing the gauge handle here gives us a single,
     * readable cleanup point and prevents stale callback invocations
     * during the brief window between Spring tearing down beans (which
     * happens in DI-graph order) and the SDK actually shutting down.
     */
    @PreDestroy
    public void close() {
        gauge.close();
    }
}
