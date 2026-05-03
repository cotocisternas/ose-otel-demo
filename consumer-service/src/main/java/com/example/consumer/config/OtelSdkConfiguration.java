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
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
// Risk #1: TWO classes named OpenTelemetryAppender exist in the
// instrumentation-bom-alpha — the appender.v1_0 one (OTLP export, has the
// install() static method) and the mdc.v1_0 one (MDC injector wrapper,
// declared only in logback-spring.xml). Import the appender.v1_0 class here.
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
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
 * artefact would read OTEL_* env vars magically and build the SDK behind
 * the scenes. We deliberately do NOT pull it in (see consumer-service/pom.xml).
 * The env-var contract is visible in code below — System.getenv()
 * with an explicit fallback. The same env-var-with-fallback pattern is used by Phase 5's
 * {@link #buildLoggerProvider(Resource)} for the log exporter.
 *
 * <p><b>Why semconv-incubating?</b> The messaging conventions
 * (MessagingIncubatingAttributes — used by Plan 02-05 for the CONSUMER span)
 * and the deployment.environment.name attribute used below
 * (DeploymentIncubatingAttributes) live in the -incubating cord because
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
 * trace pipeline), {@link #buildMeterProvider(Resource)} (Phase 4's
 * metric pipeline), AND {@link #buildLoggerProvider(Resource)} (Phase 5's
 * log pipeline) — see D-01 in 04-CONTEXT.md and 05-CONTEXT.md. The
 * {@code Tracer} and {@code Meter} @Beans are thin "instrumentation
 * scope" handles that call sites constructor-inject.
 *
 * <p><b>No Logger @Bean (D-07).</b> Unlike traces and metrics, the
 * workshop deliberately does NOT expose an OTel SDK {@code Logger} @Bean.
 * Application code uses SLF4J via {@code LoggerFactory.getLogger(...)};
 * the {@link OpenTelemetryAppender} configured in
 * {@code logback-spring.xml} bridges Logback events to the SDK's
 * {@link SdkLoggerProvider}. This is the OTel-recommended pattern for
 * application code — direct OTel Logger API usage is intended for log
 * bridges (like the appender itself), not for application-side logging.
 */
@Configuration
public class OtelSdkConfiguration {

    /**
     * Default OTLP endpoint when OTEL_EXPORTER_OTLP_ENDPOINT is unset.
     *
     * Workshop default points at grafana/otel-lgtm exposed by docker-compose.
     * The endpoint MUST start with http:// or https:// — bare localhost:4317
     * throws at builder time (verified in OtlpGrpcSpanExporterBuilder Javadoc).
     */
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";

    /**
     * Self-reference to the SDK this @Configuration produces — assigned inline
     * inside {@link #openTelemetry()} BEFORE {@code return sdk}. PREREQ-01 / D-12
     * (mirror of the LOG-03 inline-assign pattern at line ~255 for
     * {@code OpenTelemetryAppender.install(sdk)}).
     *
     * <p><b>NOT @Autowired.</b> Spring would otherwise build a self-cycle —
     * the @Configuration would request the bean it itself produces. Sibling
     * @Beans on this class ({@link #tracer(OpenTelemetry)}, {@link #meter(OpenTelemetry)})
     * reach the SDK via factory parameter injection (resolved by Spring's bean graph),
     * not via this field. This field is held only for future @PreDestroy / phase-internal
     * use; it is NOT consumed elsewhere on this class today.
     */
    private OpenTelemetry openTelemetry;

    /**
     * The fully-built OpenTelemetry SDK as a Spring bean.
     *
     * <p><b>Shutdown is delegated to the {@link CloseableOpenTelemetrySdk} bean below
     * (WR-01 fix).</b> The shutdown sequence — install(noop) BEFORE sdk.close() — must
     * happen in a single call stack to guarantee that no log event racing the shutdown
     * hits a closed exporter. Spring's previous shape — `destroyMethod="close"` on
     * this bean plus a `@PreDestroy` on the @Configuration — could not provide that
     * guarantee because Spring destroys beans in reverse-creation order and the
     * @Configuration bean (created first) is destroyed AFTER its @Bean factory
     * outputs (created later) — install(noop) would have run AFTER sdk.close(),
     * defeating the protection. The holder bean below depends on this bean (so it is
     * created later) and therefore destroyed first; its close() runs install(noop)
     * then sdk.close() in one call stack.
     *
     * <p>{@code destroyMethod = ""} disables Spring's normal `close()` autodetection
     * on this bean — the holder owns the lifecycle. {@link OpenTelemetrySdk#close()}
     * is idempotent so a double-close from misconfiguration would be safe, but we
     * disable autodetection for clarity.
     *
     * <p>{@link CloseableOpenTelemetrySdk#close()} cascades through {@link OpenTelemetrySdk#close()}
     * to {@link SdkTracerProvider#shutdown()}, {@link SdkMeterProvider#shutdown()}, AND
     * {@link SdkLoggerProvider#shutdown()} — each calls shutdown() on its respective
     * Batch{Span,Metric,LogRecord}Processor (the metric pipeline's
     * PeriodicMetricReader has its own shutdown path), which forces a final flush of
     * any items still in flight in each pipeline. Without this binding, the last 5
     * seconds of spans (BatchSpanProcessor's 5s default) AND the last 1 second of log
     * records (BatchLogRecordProcessor's 1s default — RESEARCH Finding #4) AND the
     * last collection cycle of metrics (PeriodicMetricReader's 10s interval,
     * METRIC-01) would be silently dropped on graceful shutdown — a textbook OTel
     * pitfall, multiplied by three signals.
     *
     * Note: we call .build(), NOT .buildAndRegisterGlobal(). The demo
     * injects OpenTelemetry / Tracer as Spring beans (see the @Bean
     * Tracer below); GlobalOpenTelemetry is never read. Skipping global
     * registration also keeps Phase 6 test isolation simple.
     */
    @Bean(destroyMethod = "")
    OpenTelemetry openTelemetry() {
        // ----- Resource: identifies this service in Tempo, Mimir, Loki -----
        //
        // Built ONCE in the orchestrator and passed to ALL THREE pipeline helpers
        // (D-05) so traces, metrics, AND logs share an identical Resource —
        // service.name, service.namespace, service.instance.id, and
        // deployment.environment.name are byte-for-byte the same in Tempo,
        // Mimir, AND Loki. That shared identity is what makes cross-signal
        // correlation work in Grafana (click a metric sample, jump to the
        // matching trace by service.name + instance.id; click a log line,
        // jump to the same trace from Loki).
        //
        // Resource.getDefault() carries the OTel-defined defaults including
        // SERVICE_NAME=unknown_service:java. We .merge() our overrides on
        // top — merge(other) puts `other` last, so OUR service.name wins.
        // This neutralizes the textbook "unknown_service:java" pitfall.
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

        // ----- Sibling pipelines: traces (Phase 2) + metrics (Phase 4 / D-01) + logs (Phase 5 / D-01) -----
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
        SdkLoggerProvider loggerProvider = buildLoggerProvider(resource);

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
        // .setLoggerProvider(loggerProvider) is THE single new builder line that
        // Phase 5 contributes to the orchestrator (D-01). The destroyMethod="close"
        // on this @Bean cascades through OpenTelemetrySdk.close() to ALL THREE
        // SdkTracerProvider.shutdown() AND SdkMeterProvider.shutdown() AND
        // SdkLoggerProvider.shutdown() — no new lifecycle annotation needed for
        // the logger pipeline (D-06 / Phase 4 D-07 / Phase 2 D-15 carryforward).
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(propagators)
            .build();

        // ----- PREREQ-01 / D-12: capture the SDK reference into the @Configuration's
        // own non-@Autowired instance field BEFORE install() / return.
        //
        // Mirrors the LOG-03 inline-assign shape immediately below (the appender install
        // is itself the v1.0 precedent for "do it inline in the @Bean factory rather
        // than via @PostConstruct or @Autowired-on-this-class to avoid a Spring
        // self-cycle"). Sibling @Bean factories on this class
        // ({@code tracer(OpenTelemetry)}, {@code meter(OpenTelemetry)})
        // take their OpenTelemetry via parameter injection — Spring resolves them
        // through the bean graph. This field exists for any future this-class-internal
        // use that doesn't go through Spring.
        this.openTelemetry = sdk;

        // ----- LOG-03 / PITFALL #5: install the OpenTelemetryAppender HERE -----
        //
        // The order-of-operations problem: Logback initializes BEFORE the Spring
        // ApplicationContext is built. Spring Boot's LoggingApplicationListener
        // loads logback-spring.xml from classpath at startup, which constructs an
        // OpenTelemetryAppender instance with its OpenTelemetry reference
        // defaulting to OpenTelemetry.noop(). Every log event emitted between
        // Logback init and install() is buffered in the appender's replay queue
        // (default 1000 events — knob: <numLogsCapturedBeforeOtelInstall> in
        // logback-spring.xml).
        //
        // We call install(sdk) HERE — inside the @Bean factory, just before
        // returning — rather than from a @PostConstruct method on this same
        // @Configuration class. The @PostConstruct shape would create a Spring
        // self-cycle: the @Configuration bean would need to autowire the
        // OpenTelemetry it itself produces. Calling install(sdk) right where the
        // SDK is constructed sidesteps the cycle entirely AND tightens the
        // window in which logs can land in the noop replay queue.
        //
        // What install() does: walks the global ch.qos.logback.classic.LoggerContext,
        // finds every OpenTelemetryAppender (including ones nested inside
        // wrapper appenders like the MDC injector), and swaps the noop
        // OpenTelemetry reference for the real SDK. The replay queue is then
        // drained — logs from BEFORE this line are forwarded to the OTLP
        // exporter, retroactively stamped with attributes from the (now valid)
        // OpenTelemetry instance.
        //
        // Documented quirk: this is the entire reason PITFALL #5 exists. See
        // open-telemetry/opentelemetry-java-instrumentation#10307. The replay
        // buffer softens the loss but does NOT eliminate it: if install() is
        // never called, logs beyond the 1000-event buffer are permanently
        // dropped.
        //
        // Idempotency / GLOBAL STATE (workshop-level warning): install() walks
        // the global Logback LoggerContext and reassigns the appender's
        // `volatile OpenTelemetry` field. Calling install() multiple times is
        // safe — the field is simply reassigned — and calling it with
        // OpenTelemetry.noop() effectively "uninstalls" exporting (used in the
        // shutdown @PreDestroy hook below, and in Phase 6 tests).
        //
        // The flip side of "idempotent" is "double-install clobbers": if any
        // other code in the SAME JVM (Phase 6 Testcontainers tests, a future
        // dev-loop reload that rebuilds the SDK without restarting the JVM,
        // a shared test harness) builds a second OpenTelemetrySdk and calls
        // install() on it, the appender's reference jumps to the new SDK and
        // any spans/logs still in flight on the OLD SDK become orphans that
        // never flush. This is the textbook "double-install" pitfall on the
        // static install pattern. Phase 6 mitigates by calling install(noop)
        // between test methods; production code with a single SDK lifetime is
        // unaffected.
        //
        // FQCN landmine (RESEARCH Risk #1): the import at the top of this file
        // is io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
        // — the OTLP-export class that has the static install() method. The
        // sibling artifact opentelemetry-logback-mdc-1.0 ships a class with the
        // SAME name in .mdc.v1_0 package — that one does NOT have install() and
        // is NOT what we want here.
        OpenTelemetryAppender.install(sdk);

        return sdk;
    }

    /**
     * Lifecycle holder that owns the SDK shutdown sequence (WR-01 fix — D-08
     * carryforward — bookend to the inline install() on line 283).
     *
     * <p>Spring destroys beans in reverse-creation order. This bean takes the
     * {@link OpenTelemetry} bean as a constructor parameter, so it is created
     * AFTER the SDK and therefore destroyed BEFORE it. Its {@code close()} runs
     * {@code OpenTelemetryAppender.install(OpenTelemetry.noop())} FIRST, then
     * delegates to {@code sdk.close()} — both calls in a single, ordered call stack.
     *
     * <p>The previous shape — {@code @PreDestroy} on the @Configuration class
     * plus {@code destroyMethod="close"} on the SDK bean — looked correct but
     * inverted the guarantee: the @Configuration bean is created FIRST (Spring
     * instantiates the @Configuration bean before invoking its @Bean factory
     * methods), so it is destroyed LAST. install(noop) would have run AFTER
     * sdk.close(), leaving any log event emitted between those two steps to
     * hit a closed exporter. WR-01 documented and fixes this.
     */
    @Bean(destroyMethod = "close")
    CloseableOpenTelemetrySdk openTelemetryShutdownGuard(OpenTelemetry openTelemetry) {
        return new CloseableOpenTelemetrySdk((OpenTelemetrySdk) openTelemetry);
    }

    /**
     * AutoCloseable wrapper around {@link OpenTelemetrySdk} whose {@link #close()}
     * runs {@code OpenTelemetryAppender.install(OpenTelemetry.noop())} BEFORE
     * delegating to {@code sdk.close()} — guaranteeing the install(noop) precedes
     * the SDK shutdown in a single call stack (WR-01).
     *
     * <p>Why a wrapper rather than a {@code @PreDestroy} on this @Configuration:
     * Spring destroys beans in reverse-creation order. The @Configuration bean is
     * created first (instantiated before its @Bean factories run) and therefore
     * destroyed last — a {@code @PreDestroy} method on it would run AFTER the SDK
     * bean's own destroy method, defeating the protective swap. The wrapper, taking
     * the SDK as a constructor parameter, is created AFTER and destroyed BEFORE,
     * which is the ordering we need.
     */
    static final class CloseableOpenTelemetrySdk implements AutoCloseable {

        private final OpenTelemetrySdk sdk;

        CloseableOpenTelemetrySdk(OpenTelemetrySdk sdk) {
            this.sdk = sdk;
        }

        @Override
        public void close() {
            // Step 1: swap the appender's reference for noop BEFORE the SDK closes.
            // Any log event emitted between this call and sdk.close() (AMQP
            // listeners, thread pools, other @PreDestroy hooks) is safely discarded
            // by the noop SDK rather than hitting a closed exporter.
            OpenTelemetryAppender.install(OpenTelemetry.noop());
            // Step 2: shut the SDK down (forces final flush of spans, metrics, logs).
            sdk.close();
        }
    }

    /**
     * Builds Phase 2's tracer pipeline. Extracted in Phase 4 (D-01) so the
     * sibling {@link #buildMeterProvider(Resource)} reads as a parallel block.
     * Body is byte-for-byte identical to Phase 2's inline code — no behaviour
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
        // The same two-line read-env-with-fallback shape is duplicated in
        // buildMeterProvider and buildLoggerProvider — intentional
        // parallel-pipeline readability (the three helpers are byte-symmetric
        // by design; refactoring this into a private otlpEndpoint() helper
        // would lose the side-by-side teaching surface, DOC-05).
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
        // We deliberately use defaults — they're production-grade, and they
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
        // which keeps the parent-respecting behaviour for distributed traces
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
     *   <li>{@link OtlpGrpcMetricExporter} — same artefact and same env-var
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
        // opentelemetry-exporter-otlp artefact (already pulled in by Phase 2)
        // ships OtlpGrpcSpanExporter, OtlpGrpcMetricExporter, and
        // OtlpGrpcLogRecordExporter from one jar — single artefact for all three
        // signals. Verify with `mvn dependency:tree -Dincludes=io.opentelemetry`.
        // (Duplicated for parallel-pipeline readability — see the matching
        // comment in buildTracerProvider; the three helpers are byte-symmetric
        // on purpose, DOC-05.)
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
        // are spec by OTel and produce sensible workshop values. Tuning buckets
        // for production is a real-world concern outside the SDK lesson.
        return SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(metricReader)
            .build();
    }

    /**
     * Logger pipeline added in Phase 5 — sibling to {@link #buildTracerProvider(Resource)}
     * (Phase 2) and {@link #buildMeterProvider(Resource)} (Phase 4).
     *
     * <p>Three SDK touch points (read top-to-bottom):
     * <ol>
     *   <li>{@link OtlpGrpcLogRecordExporter} — same artifact and same env-var
     *       fallback as the trace + metric exporters (D-04). The
     *       opentelemetry-exporter-otlp jar already on the classpath since Phase 2
     *       ships span + metric + log exporters in three sub-packages of one
     *       artifact; no new pom dependency on the SDK side.</li>
     *   <li>{@link BatchLogRecordProcessor} with {@code .builder(logExporter).build()}
     *       — production-shape batching pipeline (LOG-01 / D-03). Default schedule
     *       delay for log records is 1 second (faster than BatchSpanProcessor's
     *       5-second default); the demo accepts the SDK defaults.</li>
     *   <li>{@link SdkLoggerProvider} with {@code .setResource(resource)} — same
     *       Resource as the tracer + meter pipelines (D-05) so logs in Loki, traces
     *       in Tempo, and metrics in Mimir share an identical service identity for
     *       cross-signal correlation.</li>
     * </ol>
     *
     * <p><b>No application code calls the OTel Logger API directly.</b> Application
     * code uses SLF4J's LoggerFactory; the OpenTelemetryAppender (configured in
     * logback-spring.xml) bridges Logback events to the SDK's LogRecordProcessor
     * pipeline. This is the OTel-recommended pattern for application code (D-07).
     */
    private SdkLoggerProvider buildLoggerProvider(Resource resource) {
        // ----- OTLP gRPC log-record exporter: ships log records to grafana/otel-lgtm :4317 -----
        //
        // Reuses the SAME endpoint pattern as the span + metric exporters — System.getenv
        // with the DEFAULT_OTLP_ENDPOINT fallback (D-04 / Phase 4 D-04 / Phase 2 D-12
        // carryforward). Single artifact (opentelemetry-exporter-otlp) ships
        // OtlpGrpcSpanExporter, OtlpGrpcMetricExporter, AND OtlpGrpcLogRecordExporter
        // — three sub-packages, one jar. Verify with
        // `mvn dependency:tree -Dincludes=io.opentelemetry:opentelemetry-exporter-otlp`.
        // (Duplicated for parallel-pipeline readability — see the matching
        // comment in buildTracerProvider; the three helpers are byte-symmetric
        // on purpose, DOC-05.)
        String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
            .orElse(DEFAULT_OTLP_ENDPOINT);
        LogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(endpoint)
            .build();

        // ----- BatchLogRecordProcessor: production-shape batching pipeline (LOG-01 / D-03) -----
        //
        // .builder(logExporter).build() picks up the canonical defaults:
        //   schedule delay  = 1000 ms   (NOTE: different from BatchSpanProcessor's 5000 ms)
        //   max queue size  = 2048
        //   max export batch = 512
        //   exporter timeout = 30000 ms
        // We deliberately use defaults — they're production-grade. Phase 6 will
        // swap to SimpleLogRecordProcessor in @TestConfiguration so test
        // assertions are deterministic.
        BatchLogRecordProcessor logProcessor = BatchLogRecordProcessor.builder(logExporter).build();

        // ----- LoggerProvider: assembles resource + processor -----
        return SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(logProcessor)
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
