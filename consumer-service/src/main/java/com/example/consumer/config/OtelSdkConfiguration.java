package com.example.consumer.config;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual OpenTelemetry SDK bootstrap for consumer-service (TRACE-01..04 + DOC-03).
 *
 * THIS FILE IS THE WORKSHOP'S TEXTBOOK FOR THE SDK SETUP.
 * Every @Bean below carries an inline comment explaining what each builder
 * call does and why. Read it top-to-bottom in your IDE.
 *
 * <p><b>Why duplicated per service?</b> The IDENTICAL file lives in
 * producer-service/.../config/OtelSdkConfiguration.java with two changes
 * only: the service.name string ("order-consumer" → "order-producer") and
 * the tracer scope name ("com.example.consumer" → "com.example.producer").
 * Refactoring this into an @AutoConfiguration bean in otel-bootstrap
 * would hide one of the two readings the workshop is built around. See
 * README "Why is OtelSdkConfiguration.java duplicated?" (DOC-05).
 *
 * <p><b>Why no autoconfigure?</b> The opentelemetry-sdk-extension-autoconfigure
 * artifact would read OTEL_* env vars magically and build the SDK behind
 * the scenes. We deliberately do NOT pull it in (see consumer-service/pom.xml).
 * The env-var contract is visible in code below — System.getenv()
 * with an explicit fallback. Phase 5 will reuse the same pattern
 * when wiring the logger provider.
 *
 * <p><b>Why semconv-incubating?</b> The messaging conventions
 * (MessagingIncubatingAttributes — used by Plan 02-05 for the CONSUMER span)
 * and the deployment.environment.name attribute used below
 * (DeploymentIncubatingAttributes) live in the -incubating coord because
 * they are still evolving in the OTel spec. The stable semconv 1.40.0 jar
 * alone is NOT enough; both jars are pinned in consumer-service/pom.xml.
 *
 * <p><b>Why no HttpServerSpanFilter here?</b> The consumer-service has
 * no inbound HTTP business surface — only /actuator/health, which the
 * producer's HttpServerSpanFilter would have excluded anyway (D-06).
 * The per-service-duplication ethos applies to the SDK BOOTSTRAP; it
 * does NOT apply to instrumentation surfaces, which exist where the
 * surface exists (D-07).
 *
 * <p><b>Why three @Beans (openTelemetry / tracer / meter)?</b> The
 * {@link #openTelemetry()} @Bean is the orchestrator that builds the SDK
 * by delegating to {@link #buildTracerProvider(Resource)} (Phase 2's
 * trace pipeline) and {@link #buildMeterProvider(Resource)} (Phase 4's
 * metric pipeline) — see D-01 in 04-CONTEXT.md. The {@code Tracer} and
 * {@code Meter} @Beans are thin "instrumentation scope" handles that
 * call sites constructor-inject. Phase 5 will add a sibling
 * {@code buildLoggerProvider} helper and a {@code Logger} @Bean.
 */
@Configuration
public class OtelSdkConfiguration {

    /**
     * Default OTLP endpoint when OTEL_EXPORTER_OTLP_ENDPOINT is unset.
     *
     * Workshop default points at grafana/otel-lgtm exposed by docker-compose.
     * The endpoint MUST start with http:// or https:// — bare localhost:4317
     * throws at builder time (verified in OtlpGrpcSpanExporterBuilder javadoc).
     */
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";

    /**
     * The fully-built OpenTelemetry SDK as a Spring bean.
     *
     * destroyMethod="close" is load-bearing: when Spring shuts the context down
     * (Ctrl-C on `mise run dev:consumer`), Spring calls openTelemetry.close(),
     * which calls shutdown().join(10s), which cascades to the
     * SdkTracerProvider, which cascades to BatchSpanProcessor.worker.shutdown(),
     * which forces a final flush of any spans still in the BSP queue. Without
     * this binding the last 5 seconds of telemetry are silently dropped on
     * graceful shutdown — a textbook OTel pitfall.
     *
     * Note: we call .build(), NOT .buildAndRegisterGlobal(). The demo
     * injects OpenTelemetry / Tracer as Spring beans (see the @Bean
     * Tracer below); GlobalOpenTelemetry is never read. Skipping global
     * registration also keeps Phase 6 test isolation simple.
     */
    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        // ----- Resource: identifies this service in Tempo, Mimir, Loki -----
        //
        // Built ONCE in the orchestrator and passed to BOTH pipeline helpers
        // (D-05) so traces and metrics share an identical Resource — service.name,
        // service.namespace, service.instance.id, and deployment.environment.name
        // are byte-for-byte the same in Tempo and Mimir. That shared identity is
        // what makes cross-signal correlation work in Grafana (click a metric
        // sample, jump to the matching trace by service.name + instance.id).
        //
        // Resource.getDefault() carries the OTel-defined defaults including
        // SERVICE_NAME=unknown_service:java. We .merge() our overrides on
        // top — merge(other) puts `other` last, so OUR service.name wins.
        // This neutralises the textbook "unknown_service:java" pitfall.
        //
        // service.instance.id uses UUID.randomUUID() so each `mise run dev:consumer`
        // appears as a distinct instance in Tempo. Correct for a workshop;
        // a real deployment would prefer a stable host/pod identifier.
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, "order-consumer")
                .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
                .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop")
                .build()));

        // ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) -----
        //
        // Phase 2 inlined the entire tracer pipeline inside this @Bean. Phase 4
        // extracts that into buildTracerProvider(resource) — no behavior change,
        // every Phase 2 line lives unchanged inside the helper — and adds a
        // sibling buildMeterProvider(resource) so the diff between step-03 and
        // step-04 reads as "we added a sibling pipeline next to the trace
        // pipeline." Phase 5 will land buildLoggerProvider(resource) the same
        // way, and the @Bean stays a 3-step recipe.
        SdkTracerProvider tracerProvider = buildTracerProvider(resource);
        SdkMeterProvider  meterProvider  = buildMeterProvider(resource);

        // ----- Propagators: composite W3C trace-context + W3C baggage -----
        //
        // Wired in Phase 2; exercised in Phase 3 (the propagation pair in
        // otel-bootstrap reads back via openTelemetry.getPropagators()).
        // Phase 4 does not touch the propagator wiring — metrics inherit the
        // active span's traceId/spanId via exemplars at record time, but
        // exemplar emission requires no extra propagator config.
        ContextPropagators propagators = ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));

        // ----- The SDK itself -----
        //
        // .setMeterProvider(meterProvider) is THE single new builder line that
        // Phase 4 contributes to the orchestrator (D-01). The destroyMethod="close"
        // on this @Bean cascades through OpenTelemetrySdk.close() to BOTH
        // SdkTracerProvider.shutdown() AND SdkMeterProvider.shutdown() — no new
        // lifecycle annotation needed for the meter pipeline (D-07).
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(propagators)
            .build();
    }

    /**
     * Builds Phase 2's tracer pipeline. Extracted in Phase 4 (D-01) so the
     * sibling {@link #buildMeterProvider(Resource)} reads as a parallel block.
     * Body is byte-for-byte identical to Phase 2's inline code — no behavior
     * change.
     */
    private SdkTracerProvider buildTracerProvider(Resource resource) {
        // ----- OTLP gRPC exporter: ships spans to grafana/otel-lgtm :4317 -----
        //
        // mise.toml pre-wires OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
        // and OTEL_EXPORTER_OTLP_PROTOCOL=grpc. We READ the endpoint env var
        // directly (D-12: no autoconfigure dependency, no magic). The
        // OTEL_EXPORTER_OTLP_PROTOCOL value is informational here — we
        // explicitly choose the gRPC exporter class below. The env var is
        // forward-looking for any team that later switches to autoconfigure.
        //
        // The endpoint Javadoc requires the http:// or https:// prefix.
        // Bare "localhost:4317" throws IllegalArgumentException at build().
        String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
            .orElse(DEFAULT_OTLP_ENDPOINT);
        SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .build();

        // ----- BatchSpanProcessor: production-shape batching pipeline -----
        //
        // .builder(spanExporter).build() picks up the canonical defaults:
        //   schedule delay  = 5000 ms
        //   max queue size  = 2048
        //   max export batch = 512
        //   exporter timeout = 30000 ms
        // We deliberately use defaults — they're production-grade and they
        // expose the right tradeoff (latency vs throughput). Phase 6 will
        // swap to SimpleSpanProcessor in @TestConfiguration so test
        // assertions are deterministic.
        BatchSpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter).build();

        // ----- Sampler: parent-based, always-on root -----
        //
        // Sampler.parentBased(Sampler.alwaysOn()) means:
        //   - If there is a parent SpanContext (from upstream propagation),
        //     respect its sampling decision via the traceparent flag byte.
        //   - If there is NO parent (root span), sample 100%.
        //
        // For production, swap to:
        //   Sampler.parentBased(Sampler.traceIdRatioBased(0.1))
        // which keeps the parent-respecting behavior for distributed traces
        // (so a sampled trace stays sampled across all hops via traceparent)
        // while sampling only 10% of brand-new root spans.
        //
        // Why parentBased matters: without it, if you set
        // traceIdRatioBased(0.1) directly as the root sampler, each service
        // would re-roll the dice on EVERY span — producing trace fragments
        // where the consumer span gets sampled but its producer parent does
        // not. parentBased ensures one decision per trace, made at the root.
        Sampler sampler = Sampler.parentBased(Sampler.alwaysOn());

        // ----- TracerProvider: assembles resource + sampler + processor -----
        return SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(sampler)
            .addSpanProcessor(spanProcessor)
            .build();
    }

    /**
     * Meter pipeline added in Phase 4 — sibling to {@link #buildTracerProvider(Resource)}.
     *
     * <p>Three new SDK touch points (read top-to-bottom):
     * <ol>
     *   <li>{@link OtlpGrpcMetricExporter} — same artifact and same env-var
     *       fallback as the trace exporter (D-04). The opentelemetry-exporter-otlp
     *       jar already on the classpath from Phase 2 ships span + metric + log
     *       exporters; no new pom dependency is needed.</li>
     *   <li>{@link PeriodicMetricReader} with {@code .setInterval(Duration.ofSeconds(10))}
     *       — overrides OTel's 60-second default to keep the workshop's ~15-second
     *       feedback loop tight (METRIC-01 / D-03). Production apps would typically
     *       leave this at the 60s default; 10s here is a workshop value.</li>
     *   <li>{@link SdkMeterProvider} with {@code .setResource(resource)} — same
     *       Resource as the tracer pipeline (D-05) so metrics and traces share
     *       identity in Mimir/Tempo for cross-signal correlation.</li>
     * </ol>
     *
     * <p><b>Default View / Aggregation: SDK defaults.</b> No custom histogram
     * bucket tuning here (D-15). The OTel-spec default explicit-bucket aggregation
     * for {@code http.server.request.duration} (seconds) produces sensible workshop
     * values. Bucket tuning is a real-world concern outside the SDK lesson.
     */
    private SdkMeterProvider buildMeterProvider(Resource resource) {
        // ----- OTLP gRPC metric exporter: ships metrics to grafana/otel-lgtm :4317 -----
        //
        // Reuses the SAME endpoint pattern as the span exporter (D-04 / Phase 2 D-12
        // carryforward) — System.getenv with the DEFAULT_OTLP_ENDPOINT fallback. No
        // new env var; metrics flow to the same OTLP endpoint as traces. The
        // opentelemetry-exporter-otlp artifact (already pulled in by Phase 2)
        // ships OtlpGrpcSpanExporter, OtlpGrpcMetricExporter, and
        // OtlpGrpcLogRecordExporter from one jar — single artifact for all three
        // signals. Verify with `mvn dependency:tree -Dincludes=io.opentelemetry`.
        String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
            .orElse(DEFAULT_OTLP_ENDPOINT);
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .build();

        // ----- PeriodicMetricReader: 10s collection interval (METRIC-01 / D-03) -----
        //
        // The default PeriodicMetricReader interval is 60 seconds. We override to
        // 10 seconds so workshop attendees see fresh metrics within ~15 seconds of
        // POSTing an order — the Phase 4 ROADMAP success criterion #1 explicitly
        // cites a 15-second window. A 60-second wait would break the workshop's
        // tight feedback loop.
        //
        // Production rule of thumb: keep this at 60s (the OTel default). Lower
        // values increase scrape volume on the metric backend (Mimir/Prometheus)
        // without proportional value — production dashboards rarely refresh faster
        // than 30s anyway. The 10s value here is a workshop-only choice (parallel
        // to the always-on sampler choice in buildTracerProvider).
        PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
            .setInterval(Duration.ofSeconds(10))
            .build();

        // ----- MeterProvider: assembles resource + reader -----
        //
        // No custom View / ExplicitBucketHistogramAggregation (D-15) — the SDK's
        // default bucket boundaries for http.server.request.duration (seconds)
        // are spec'd by OTel and produce sensible workshop values. Tuning buckets
        // for production is a real-world concern outside the SDK lesson.
        return SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(metricReader)
            .build();
    }

    /**
     * Tracer for instrumentation scope "com.example.consumer".
     *
     * The scope name typically matches the package or library being
     * instrumented. Workshop attendees inject this Tracer into
     * OrderListener and ProcessingService via constructor injection (D-02).
     * Tracer is thread-safe; one bean is reused across all callers.
     */
    @Bean
    Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.example.consumer");
    }

    /**
     * Meter for instrumentation scope "com.example.consumer" (D-06).
     *
     * Sibling to the {@link #tracer(OpenTelemetry)} @Bean above — same scope
     * name, same injection pattern. Workshop attendees inject this Meter into
     * QueueDepthGauge (Plan 04-04 — orders.queue.depth.estimate ObservableGauge)
     * via constructor injection. Meter is thread-safe; one bean is reused
     * across all instrument call sites.
     *
     * <p>The scope name "com.example.consumer" surfaces in Mimir as the
     * {@code otel_scope_name} label on every metric data point produced by
     * instruments built from this Meter — workshop attendees can filter
     * Mimir queries by it.
     */
    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.example.consumer");
    }
}
