package com.example.e2e;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only OpenTelemetry SDK swap (Phase 6 / D-05..D-09 + D-07.1).
 *
 * <p>This {@code @TestConfiguration} is {@code @Import}ed by both Spring
 * contexts started in {@link OrderFlowIT}'s {@code @BeforeAll}. Each
 * {@code @Bean} method is a THIN FACADE over {@link TestOtelHolder} so that
 * BOTH contexts share the SAME {@link OpenTelemetry} instance and the SAME
 * three in-memory sinks — D-07's "ONE shared exporter sees both services"
 * intent (RESEARCH OPEN-QUESTION-1 resolved by D-07.1).
 *
 * <p><b>Read this file side-by-side with</b> {@code producer-service/.../OtelSdkConfiguration.java}.
 * The {@code @Bean} signatures match production line-for-line; only the bodies
 * differ (production builds the SDK; tests delegate to {@link TestOtelHolder}).
 *
 * <p><b>What changes vs production</b> (same shape as the divergence list in
 * {@link TestOtelHolder} — paraphrased to keep this file free of any token
 * that could be mistaken for an SDK-construction reference; the actual
 * substitutions live one file over):
 * <ul>
 *   <li>OTLP gRPC span exporter is replaced by an in-memory span exporter
 *       (deterministic capture).</li>
 *   <li>The batch-style span processor is replaced by a synchronous one
 *       (PITFALLS #4 / #11 — batch loses telemetry on shutdown).</li>
 *   <li>OTLP gRPC log-record exporter is replaced by an in-memory log
 *       exporter; batch-style log-record processor becomes synchronous.</li>
 *   <li>OTLP gRPC metric exporter is replaced by an in-memory metric reader
 *       registered DIRECTLY (no periodic-metric-reader wrapper, D-16).</li>
 *   <li>{@code parentBased(alwaysOn())} sampler is replaced by plain
 *       {@code alwaysOn()} (D-18 — every span captured anyway).</li>
 *   <li>{@code service.name} swaps from {@code "order-producer"} /
 *       {@code "order-consumer"} to {@code "integration-test"} (D-07
 *       single-resource shape).</li>
 *   <li>One shared file across both Spring contexts instead of the
 *       per-service duplication production uses (test infra exempt from
 *       Phase 2 D-01 / DOC-05).</li>
 * </ul>
 *
 * <p><b>Why one shared file (not duplicated like production)?</b> Phase 2
 * D-01 / DOC-05 says SDK bootstrap is duplicated per-service for pedagogical
 * clarity. That rule applies to PRODUCTION code. Test infrastructure is
 * exempt (D-07): one InMemorySpanExporter must see ALL spans across BOTH
 * Spring contexts, so cross-service assertions in {@link OrderFlowIT} filter
 * by SpanKind / messaging.system rather than by service.name.
 *
 * <p><b>Why install(openTelemetry) is NOT here:</b> the install ordering
 * (Phase 5 commit f5c331a / PITFALL #5) lives inside {@link TestOtelHolder#get()},
 * not in this file. Calling {@code install()} from a {@code @PostConstruct}
 * on this {@code @Configuration} class would create a Spring self-cycle —
 * exactly what Phase 5 commit f5c331a fixed. Honor the holder.
 *
 * <p><b>Why no {@code @Bean Logger} method?</b> Phase 5 D-07 carryforward —
 * application code uses SLF4J via {@code LoggerFactory.getLogger(...)}; the
 * {@code OpenTelemetryAppender} declared in {@code logback-spring.xml} and
 * installed by {@link TestOtelHolder#get()} bridges Logback events to the
 * SDK's {@code SdkLoggerProvider}. Direct OTel Logger API usage is intended
 * for log bridges, not application code.
 *
 * <p><b>Override-by-name:</b> the @Bean methods named {@code openTelemetry},
 * {@code tracer}, {@code meter} match the production bean names exactly
 * (Cross-File Invariant #1 from PATTERNS.md §3). Combined with
 * {@code spring.main.allow-bean-definition-overriding=true} on each
 * {@code SpringApplicationBuilder} (set in {@link OrderFlowIT}), Spring
 * replaces the production scanned beans with these test ones in BOTH
 * contexts.
 */
@TestConfiguration
public class TestOtelConfiguration {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        TestOtelHolder.get();   // ensure SDK + sinks are initialized
        return TestOtelHolder.SPANS;
    }

    @Bean
    InMemoryLogRecordExporter inMemoryLogRecordExporter() {
        TestOtelHolder.get();
        return TestOtelHolder.LOGS;
    }

    @Bean
    InMemoryMetricReader inMemoryMetricReader() {
        TestOtelHolder.get();
        return TestOtelHolder.METRICS;
    }

    /**
     * Replaces the production {@code OpenTelemetry} bean by name (D-06).
     * {@code destroyMethod="close"} cascades to
     * {@code SdkTracerProvider.shutdown()} / {@code SdkMeterProvider.shutdown()}
     * / {@code SdkLoggerProvider.shutdown()} when the Spring context closes
     * (Phase 2 D-15 carryforward).
     */
    @Bean(destroyMethod = "close")
    OpenTelemetry openTelemetry() {
        return TestOtelHolder.get();
    }

    /**
     * Test-side {@link Tracer} bean. Production sets the instrumentation
     * scope to {@code "com.example.producer"} / {@code "com.example.consumer"};
     * tests use {@code "com.example.integration-test"}. SimpleSpanProcessor
     * captures all spans regardless of scope, so the scope rename is
     * cosmetic — but documenting it surfaces the divergence to attendees
     * reading the diff.
     */
    @Bean
    Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.example.integration-test");
    }

    /**
     * Test-side {@link Meter} bean. Same scope override as {@code tracer()}.
     */
    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.example.integration-test");
    }
}
