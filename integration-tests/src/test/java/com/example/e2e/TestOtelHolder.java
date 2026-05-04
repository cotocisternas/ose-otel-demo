package com.example.e2e;

import java.util.Set;
import java.util.UUID;

import com.example.otel.context.BaggageSpanAttributeProcessor;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
// CRITICAL (Risk #1 / Phase 5 carryforward): two classes share the name
// OpenTelemetryAppender — import the appender.v1_0 one (HAS install()), NOT
// the mdc.v1_0 one (MDC wrapper, no install()).
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes;

/**
 * Process-wide singleton that holds ONE {@link OpenTelemetrySdk} + three
 * in-memory sinks shared across BOTH producer and consumer Spring contexts
 * launched by {@link OrderFlowIT}. Resolves CONTEXT.md D-07.1 + RESEARCH
 * OPEN-QUESTION-1.
 *
 * <p><b>Why a static holder, not @Bean?</b> Each {@code SpringApplicationBuilder}
 * context, even when it @Imports the same {@link TestOtelConfiguration}, yields
 * a SEPARATE bean instance (Spring DI scopes beans per-context). D-07 requires
 * ONE {@code OpenTelemetry} so a single in-memory queue captures spans from
 * both services. The holder is the smallest pedagogically-clean way to force
 * that — TestOtelConfiguration's @Bean methods become thin facades that
 * delegate here.
 *
 * <p><b>Lifecycle:</b> lazy init on first {@link #get()}; subsequent calls
 * return the same instances. {@link OpenTelemetryAppender#install(io.opentelemetry.api.OpenTelemetry)}
 * runs INSIDE {@link #get()} AFTER {@link OpenTelemetrySdk#builder()
 * OpenTelemetrySdk.builder()}.build() returns and BEFORE the static {@link #SDK}
 * reference is published — EXACT replication of Phase 5 commit {@code f5c331a}
 * install-ordering invariant (PITFALL #5 / D-09).
 *
 * <p><b>Divergences from production OtelSdkConfiguration:</b>
 * <ul>
 *   <li>NOT a {@code @Configuration} — bare {@code final class} with
 *       package-private static fields.</li>
 *   <li>{@link SimpleSpanProcessor} + {@link InMemorySpanExporter} (synchronous,
 *       deterministic) — production uses the batch-style span processor + OTLP
 *       (PITFALLS #4 / #11 — the batch processor loses telemetry on shutdown).</li>
 *   <li>{@link SimpleLogRecordProcessor} + {@link InMemoryLogRecordExporter}
 *       (synchronous) — production uses the batch-style log-record processor + OTLP.</li>
 *   <li>{@link InMemoryMetricReader} registered DIRECTLY — production wraps
 *       {@code OtlpGrpcMetricExporter} in a periodic metric reader with a
 *       10-second interval (D-16).</li>
 *   <li>{@link Sampler#alwaysOn()} — production uses a parent-based sampler
 *       wrapping {@code alwaysOn()} (D-18 — that parent check is wasted work
 *       in tests since every span is captured anyway).</li>
 *   <li>{@code service.name="integration-test"} (single resource shape per
 *       D-07) — production uses {@code "order-producer"} / {@code "order-consumer"}
 *       per-service.</li>
 *   <li>Single shared instance across both contexts — production duplicates
 *       per service (Phase 2 D-01 / DOC-05; test infra exempt per D-07).</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> {@link #get()} is {@code synchronized}; subsequent
 * reads of the static fields are safe because the fields are {@code volatile}
 * (write-publish via {@code synchronized} block, reads via {@code volatile}
 * happens-before).
 *
 * <p><b>NOT replicated from production:</b> {@code @PreDestroy
 * uninstallLogbackAppender()}. {@link OrderFlowIT}'s {@code @AfterAll} closes
 * both Spring contexts; the {@code destroyMethod="close"} cascade on the
 * {@link io.opentelemetry.api.OpenTelemetry} {@code @Bean} (defined in {@link TestOtelConfiguration})
 * shuts down the SDK. The appender is left bound to the closed SDK at JVM exit
 * — harmless because the test JVM exits immediately after.
 */
final class TestOtelHolder {

    /** The single {@link OpenTelemetrySdk} shared across both Spring contexts. */
    static volatile OpenTelemetrySdk SDK;

    /** In-memory span sink — {@link OrderFlowIT} reads via {@link InMemorySpanExporter#getFinishedSpanItems()}. */
    static volatile InMemorySpanExporter SPANS;

    /** In-memory log-record sink — {@link OrderFlowIT} reads via {@link InMemoryLogRecordExporter#getFinishedLogRecordItems()}. */
    static volatile InMemoryLogRecordExporter LOGS;

    /**
     * In-memory metric sink — synchronous; {@link OrderFlowIT} drains via
     * {@link InMemoryMetricReader#collectAllMetrics()}. NOTE:
     * {@link InMemoryMetricReader} has NO {@code reset()} method (verified
     * against SDK 1.61.0 source — RESEARCH §2.3); {@code collectAllMetrics()}
     * IS the drain primitive.
     */
    static volatile InMemoryMetricReader METRICS;

    private TestOtelHolder() {
        // utility class — not instantiable
    }

    /**
     * Returns the lazy-initialized {@link OpenTelemetrySdk}. First call builds
     * the SDK + 3 InMemory sinks + installs the Logback appender; subsequent
     * calls return the cached instance.
     *
     * @return the singleton SDK; never null after first call
     */
    static synchronized OpenTelemetrySdk get() {
        if (SDK != null) return SDK;

        // ----- In-memory sinks (sdk-testing artifact) -----
        SPANS = InMemorySpanExporter.create();
        LOGS = InMemoryLogRecordExporter.create();
        METRICS = InMemoryMetricReader.create();

        // ----- Resource: ONE shape used by both contexts (D-07) -----
        // service.name="integration-test" is sufficient because cross-service
        // assertions filter by SpanKind / messaging attributes, not by
        // service.name. UUID per JVM run keeps service.instance.id unique
        // across re-runs (production parity).
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, "integration-test")
                .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
                .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "test")
                .build()));

        // ----- Trace pipeline: SimpleSpanProcessor + InMemorySpanExporter -----
        // SimpleSpanProcessor is synchronous: every span.end() exports
        // immediately. PITFALLS #4 + #11 — the batch-style processor would
        // race with assertions and require Thread.sleep (anti-pattern, D-13).
        // Sampler.alwaysOn() (D-18): no parent-context check; every span
        // captured. The parent-based wrapper used in production is correct
        // because some spans arrive with sampled=false from upstream —
        // irrelevant in tests.
        // X-4 mitigation: register BaggageSpanAttributeProcessor so integration test
        // assertions on baggage.customer-tier attribute work.
        // D-18: test sampler stays alwaysOn() — test determinism overrides the Phase 16
        // production sampler swap (traceIdRatioBased). Every span is captured in tests.
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.alwaysOn())
            .addSpanProcessor(new BaggageSpanAttributeProcessor(Set.of("customer-tier")))  // X-4 fix
            .addSpanProcessor(SimpleSpanProcessor.create(SPANS))
            .build();

        // ----- Log pipeline: SimpleLogRecordProcessor + InMemoryLogRecordExporter -----
        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(LOGS))
            .build();

        // ----- Metric pipeline: InMemoryMetricReader registered DIRECTLY (D-16) -----
        // Production wraps OtlpGrpcMetricExporter in a periodic-metric-reader
        // builder with setInterval(Duration.ofSeconds(10)). Tests skip the
        // periodic wrapper because InMemoryMetricReader IS a MetricReader and
        // the test calls collectAllMetrics() synchronously from each test
        // method.
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(METRICS)
            .build();

        // ----- Propagators: identical to production (W3C TraceContext + Baggage) -----
        // The AMQP propagation pair (otel-bootstrap's TracingMessagePostProcessor +
        // TracingMessageListenerAdvice) reads the global propagator via
        // OpenTelemetry.getPropagators(). Test must register the SAME
        // propagators or the cross-service trace assertions in OrderFlowIT
        // (TEST-03 / TEST-04) silently fail.
        ContextPropagators propagators = ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));

        // ----- The SDK -----
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(propagators)
            .build();

        // ----- LOG-03 / PITFALL #5: install Logback appender BEFORE publishing SDK -----
        //
        // EXACT replication of Phase 5 commit f5c331a's install ordering:
        //   1. Build the SDK (above).
        //   2. Call OpenTelemetryAppender.install(sdk) so any Logback
        //      OpenTelemetryAppender instance in the LoggerContext (declared in
        //      producer/consumer logback-spring.xml) swaps its noop default
        //      OpenTelemetry reference for the real SDK; replay buffer flushes.
        //   3. Publish SDK reference (SDK = sdk below) — at this point both
        //      Spring contexts can call get() and observe the fully-wired SDK.
        //
        // Calling install() AFTER SDK = sdk would still work for THIS class
        // (no concurrent reader yet), but the order matches production for
        // pedagogical clarity. The synchronized method ensures atomic init.
        OpenTelemetryAppender.install(sdk);

        SDK = sdk;
        return SDK;
    }
}
