package com.example.consumer.config;

import java.util.Optional;
import java.util.UUID;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
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
 * with an explicit fallback. Phase 4 + Phase 5 will reuse the same pattern
 * when wiring the meter and logger providers.
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
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(sampler)
            .addSpanProcessor(spanProcessor)
            .build();

        // ----- Propagators: composite W3C trace-context + W3C baggage -----
        //
        // Phase 2 wires both propagators but does NOT exercise them — the
        // producer and consumer are intentionally in DIFFERENT traces here
        // (broken-then-fixed pedagogy). Phase 3 reuses what's already wired
        // by calling openTelemetry.getPropagators().getTextMapPropagator() to
        // build the TracingMessageListenerAdvice extract method.
        //
        // W3CTraceContextPropagator carries the `traceparent` + `tracestate`
        // headers (the W3C Trace Context spec); W3CBaggagePropagator carries
        // the `baggage` header (W3C Baggage spec). Together they cover the
        // OTel-recommended baseline.
        ContextPropagators propagators = ContextPropagators.create(
            TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()));

        // ----- The SDK itself -----
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(propagators)
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
}
