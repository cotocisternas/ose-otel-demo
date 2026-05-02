---
id: 04-01-meter-pipeline-refactor
phase: 04-metrics
plan: 01
type: execute
wave: 1
depends_on: []
requirements: [METRIC-01]
requirements_addressed: [METRIC-01]
files_modified:
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
autonomous: true
objective: "Refactor BOTH OtelSdkConfiguration.java files (per-service-duplicated per D-02) so the existing openTelemetry() @Bean delegates to a private SdkTracerProvider buildTracerProvider(Resource) helper (extracted verbatim from Phase 2's inline tracer pipeline) AND a new sibling private SdkMeterProvider buildMeterProvider(Resource) helper that wires OtlpGrpcMetricExporter + PeriodicMetricReader.setInterval(Duration.ofSeconds(10)) (METRIC-01 / D-03). Add a Meter @Bean parallel to the existing Tracer @Bean (D-06). The refactor itself is a teaching artifact — the diff between step-03 and step-04 should read as 'we added a sibling pipeline next to the trace pipeline,' not 'we shoved more stuff inside one method.'"
must_haves:
  truths:
    - "openTelemetry() @Bean in BOTH services delegates to private SdkTracerProvider buildTracerProvider(Resource resource) AND private SdkMeterProvider buildMeterProvider(Resource resource) helpers (D-01)"
    - "Phase 2's tracer pipeline (OtlpGrpcSpanExporter + BatchSpanProcessor + Sampler.parentBased(alwaysOn) + SdkTracerProvider.builder) lives verbatim inside buildTracerProvider — no Phase 2 lines deleted, no behavior change"
    - "buildMeterProvider builds OtlpGrpcMetricExporter using the SAME endpoint pattern as the trace exporter — Optional.ofNullable(System.getenv(\"OTEL_EXPORTER_OTLP_ENDPOINT\")).orElse(DEFAULT_OTLP_ENDPOINT) — sharing the existing constant (D-04 / Phase 2 D-12 carryforward)"
    - "buildMeterProvider wraps the exporter in PeriodicMetricReader.builder(exporter).setInterval(Duration.ofSeconds(10)).build() — overrides OTel's 60-second default (METRIC-01 / D-03); inline comment cites the workshop-vs-production tradeoff"
    - "Resource is built once in the @Bean orchestrator and passed explicitly to BOTH helpers (D-05) — traces and metrics carry IDENTICAL service.name/namespace/instance.id/deployment.environment.name attributes for cross-signal correlation in Grafana"
    - "OpenTelemetrySdk.builder() adds .setMeterProvider(meterProvider) — single new builder line in the orchestrator (D-01)"
    - "@Bean Meter meter(OpenTelemetry o) { return o.getMeter(\"com.example.<service>\"); } sits as a sibling to the existing Tracer @Bean (D-06) — scope name 'com.example.producer' in producer file, 'com.example.consumer' in consumer file"
    - "@Bean(destroyMethod=\"close\") on openTelemetry() is unchanged — no new lifecycle annotation, the existing close() cascade handles SdkMeterProvider.shutdown().join(10s) (D-07 / Phase 2 D-15 carryforward)"
    - "Producer file's @Bean HttpServerSpanFilter factory gains a Meter parameter so 04-03 can constructor-inject it: HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter)"
    - "NO autoconfigure dependency added — no opentelemetry-sdk-extension-autoconfigure pulled in (Phase 2 D-12 carryforward); pom.xml NOT modified"
    - "Per-service mirror preserved (D-02): producer vs consumer differ ONLY in service.name string ('order-producer' vs 'order-consumer'), Meter scope name ('com.example.producer' vs 'com.example.consumer'), Tracer scope name (already different at HEAD), and the producer-only HttpServerSpanFilter @Bean — no other divergence"
    - "DOC-03 / D-20 comment density bar holds: each refactored OtelSdkConfiguration.java retains ≥40 comment lines (//-style or *-style); buildMeterProvider helper carries banner-comment style matching Phase 2's '----- Resource -----' / '----- OTLP gRPC exporter -----' blocks"
    - "mvn -pl producer-service,consumer-service compile exits 0 in both services after the refactor — pure refactor + additions, no behavioral regression"
    - "mise run verify:bom continues to exit 0 (Phase 2 invariant preserved — one version per OTel artifact across reactor)"
  artifacts:
    - path: "producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
      provides: "Refactored producer-side SDK bootstrap: openTelemetry() @Bean orchestrator + buildTracerProvider(Resource) helper (verbatim Phase 2 lift-and-shift) + buildMeterProvider(Resource) helper (new, wires OtlpGrpcMetricExporter + 10s PeriodicMetricReader + SdkMeterProvider) + Meter @Bean (scope 'com.example.producer') + HttpServerSpanFilter @Bean factory updated to take (Tracer, Meter)"
      contains: "private SdkMeterProvider buildMeterProvider(Resource"
    - path: "consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
      provides: "Mirrored consumer-side SDK bootstrap (per D-02): same refactor + Meter @Bean (scope 'com.example.consumer'); no HttpServerSpanFilter (D-07 carryforward)"
      contains: "private SdkMeterProvider buildMeterProvider(Resource"
  key_links:
    - from: "openTelemetry() @Bean orchestrator"
      to: "buildTracerProvider(resource) + buildMeterProvider(resource)"
      via: "Direct method calls on the Resource built in the @Bean body"
      pattern: "buildTracerProvider\\(resource\\)|buildMeterProvider\\(resource\\)"
    - from: "buildMeterProvider"
      to: "OtlpGrpcMetricExporter -> grafana/otel-lgtm :4317"
      via: "OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build() with endpoint = System.getenv with DEFAULT_OTLP_ENDPOINT fallback (D-04)"
      pattern: "OtlpGrpcMetricExporter\\.builder\\(\\)\\.setEndpoint"
    - from: "PeriodicMetricReader"
      to: "10-second collection interval (METRIC-01)"
      via: ".setInterval(Duration.ofSeconds(10)) override of the 60s default (D-03)"
      pattern: "Duration\\.ofSeconds\\(10\\)"
    - from: "Meter @Bean"
      to: "OpenTelemetry.getMeter(scope)"
      via: "openTelemetry.getMeter(\"com.example.<service>\") — sibling shape to existing Tracer @Bean (D-06)"
      pattern: "openTelemetry\\.getMeter\\("
---

<objective>
Refactor BOTH `OtelSdkConfiguration.java` files (producer + consumer) so Phase 4's meter pipeline lands as a **sibling** to Phase 2's tracer pipeline, not as additional inline code.

The existing `openTelemetry()` @Bean body becomes a small orchestrator that:
1. Builds the `Resource` (unchanged from Phase 2 — stays in the @Bean per D-05).
2. Calls `buildTracerProvider(resource)` (a new private helper containing Phase 2's tracer-pipeline code, lifted verbatim).
3. Calls `buildMeterProvider(resource)` (a new private helper — Phase 4's contribution).
4. Builds the propagators (unchanged).
5. Returns `OpenTelemetrySdk.builder().setTracerProvider(...).setMeterProvider(...).setPropagators(...).build()`.

A new `@Bean Meter meter(OpenTelemetry o)` sits as a sibling to the existing `@Bean Tracer tracer(OpenTelemetry o)` (D-06). The producer's `@Bean HttpServerSpanFilter` factory gains a `Meter` parameter so Plan 04-03 can constructor-inject the histogram.

Both services receive the SAME refactor (D-02). The producer/consumer diff grows by exactly one line for the Meter scope name (`com.example.producer` vs `com.example.consumer`); no other divergence.

Purpose: METRIC-01 (10-second `PeriodicMetricReader` interval), and the structural foundation for Plans 04-02 (Counter), 04-03 (Histogram), and 04-04 (ObservableGauge). The pedagogical north star (D-01): the diff between `step-03-context-propagation` and `step-04-metrics` should read as "we added a sibling pipeline."

Output: 2 modified files (producer + consumer `OtelSdkConfiguration.java`), zero new files, zero new pom dependencies.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/04-metrics/04-CONTEXT.md
@.planning/phases/04-metrics/04-PATTERNS.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/PHASE-SUMMARY.md
@CLAUDE.md
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
@consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java

<interfaces>
<!-- Key types Phase 4 will use. All BOM-managed via opentelemetry-bom:1.61.0; -->
<!-- the opentelemetry-sdk artifact already on classpath since Phase 2 transitively -->
<!-- pulls in sdk-trace + sdk-metrics + sdk-logs (verified via mvn dependency:tree). -->

From io.opentelemetry.sdk.metrics:
```java
public final class SdkMeterProvider implements MeterProvider, Closeable {
    public static SdkMeterProviderBuilder builder();
    @Override public CompletableResultCode shutdown();
    // shutdown() is invoked transitively by OpenTelemetrySdk.close()
}
public final class SdkMeterProviderBuilder {
    public SdkMeterProviderBuilder setResource(Resource resource);
    public SdkMeterProviderBuilder registerMetricReader(MetricReader reader);
    public SdkMeterProvider build();
}
```

From io.opentelemetry.sdk.metrics.export:
```java
public final class PeriodicMetricReader implements MetricReader {
    public static PeriodicMetricReaderBuilder builder(MetricExporter exporter);
}
public final class PeriodicMetricReaderBuilder {
    public PeriodicMetricReaderBuilder setInterval(Duration interval);
    public PeriodicMetricReader build();
}
```

From io.opentelemetry.exporter.otlp.metrics:
```java
public final class OtlpGrpcMetricExporter implements MetricExporter {
    public static OtlpGrpcMetricExporterBuilder builder();
}
public final class OtlpGrpcMetricExporterBuilder {
    public OtlpGrpcMetricExporterBuilder setEndpoint(String endpoint);
    public OtlpGrpcMetricExporter build();
}
```

From io.opentelemetry.api.metrics:
```java
public interface Meter {
    LongCounterBuilder counterBuilder(String name);     // used by Plan 04-02
    DoubleHistogramBuilder histogramBuilder(String name); // used by Plan 04-03
    LongGaugeBuilder gaugeBuilder(String name);          // used by Plan 04-04
    // Note: histogramBuilder returns a Double-valued histogram by default;
    // gaugeBuilder().ofLongs() switches to long-valued (D-19).
}
```

From io.opentelemetry.api (already imported at HEAD):
```java
public interface OpenTelemetry {
    Meter getMeter(String instrumentationScopeName);
    // Already-known: Tracer getTracer(String); ContextPropagators getPropagators();
}
```

From io.opentelemetry.sdk:
```java
public final class OpenTelemetrySdk implements OpenTelemetry, Closeable {
    public static OpenTelemetrySdkBuilder builder();
    @Override public void close();   // cascades to SdkTracerProvider.shutdown() AND SdkMeterProvider.shutdown()
}
public final class OpenTelemetrySdkBuilder {
    public OpenTelemetrySdkBuilder setTracerProvider(SdkTracerProvider provider);
    public OpenTelemetrySdkBuilder setMeterProvider(SdkMeterProvider provider);   // single new line in @Bean (D-01)
    public OpenTelemetrySdkBuilder setPropagators(ContextPropagators propagators);
    public OpenTelemetrySdk build();
}
```
</interfaces>
</context>

<tasks>

<task id="04-01-T1" type="auto">
  <name>Task 1: Refactor producer-service OtelSdkConfiguration.java — extract buildTracerProvider, add buildMeterProvider, add Meter @Bean, update HttpServerSpanFilter factory</name>
  <files>producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (current state at HEAD step-03-context-propagation — Phase 2's inline tracer pipeline lives in openTelemetry() body; this file is the refactor target)
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 42-218 — the exact target shape including the @Bean orchestrator skeleton at lines 102-126, the buildTracerProvider lift-and-shift at lines 131-145, the buildMeterProvider sibling at lines 148-182, and the Meter @Bean at lines 195-198)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-01 helper extraction, D-03 10-second interval, D-04 endpoint pattern, D-05 shared Resource, D-06 Meter @Bean, D-07 lifecycle, D-20 comment density)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (D-01 per-service duplication ethos, D-12 no-autoconfigure, D-15 destroyMethod cascade — these are the constraints the refactor must NOT violate)
  </read_first>
  <action>
    Modify `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` IN PLACE. This is a refactor + addition; **NO Phase 2 lines get deleted by the refactor** — every existing line either stays in the @Bean orchestrator body OR moves verbatim into `buildTracerProvider(Resource)`. Behavior is identical to Phase 2 for the trace pipeline; the Meter pipeline is purely additive.

    **Edit 1 — Imports.** ADD these imports below the existing OTel imports (preserve alphabetical-ish grouping the file uses):
    ```java
    import java.time.Duration;
    ```
    ```java
    import io.opentelemetry.api.metrics.Meter;
    import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
    import io.opentelemetry.sdk.metrics.SdkMeterProvider;
    import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
    ```
    Do NOT remove any existing import. Do NOT add `opentelemetry-sdk-extension-autoconfigure` (D-12 carryforward).

    **Edit 2 — Replace the body of `openTelemetry()` @Bean.** The existing body builds Resource → endpoint → SpanExporter → BatchSpanProcessor → Sampler → SdkTracerProvider → propagators → returns the SDK. Phase 4 keeps Resource and propagators in the orchestrator and moves the tracer pipeline lines into a helper. The new orchestrator body is:

    ```java
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
        // service.instance.id uses UUID.randomUUID() so each `mise run dev:producer`
        // appears as a distinct instance in Tempo. Correct for a workshop;
        // a real deployment would prefer a stable host/pod identifier.
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, "order-producer")
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
    ```

    **Edit 3 — Add `private SdkTracerProvider buildTracerProvider(Resource resource)` helper.** Place IMMEDIATELY AFTER the closing `}` of `openTelemetry()`. The body is verbatim Phase 2's inline tracer-pipeline code (current file lines 103-158 — endpoint env-var, OtlpGrpcSpanExporter, BatchSpanProcessor, Sampler, SdkTracerProvider). All Phase 2 inline comments move into the helper unchanged. Method shape:

    ```java
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
    ```

    **Edit 4 — Add `private SdkMeterProvider buildMeterProvider(Resource resource)` helper.** Place IMMEDIATELY AFTER `buildTracerProvider`. This is Phase 4's contribution. Method shape (banner-comment style matches the tracer helper, D-20 / DOC-03 carryforward):

    ```java
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
    ```

    **Edit 5 — Add `@Bean Meter meter(OpenTelemetry openTelemetry)` factory.** Place IMMEDIATELY AFTER the existing `@Bean Tracer tracer(...)` factory. Sibling shape, scope name parallel:

    ```java
    /**
     * Meter for instrumentation scope "com.example.producer" (D-06).
     *
     * Sibling to the {@link #tracer(OpenTelemetry)} @Bean above — same scope
     * name, same injection pattern. Workshop attendees inject this Meter into
     * OrderService (Plan 04-02 — orders.created LongCounter) and
     * HttpServerSpanFilter (Plan 04-03 — http.server.request.duration
     * DoubleHistogram) via constructor injection. Meter is thread-safe; one
     * bean is reused across all instrument call sites.
     *
     * <p>The scope name "com.example.producer" surfaces in Mimir as the
     * {@code otel_scope_name} label on every metric data point produced by
     * instruments built from this Meter — workshop attendees can filter
     * Mimir queries by it.
     */
    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.example.producer");
    }
    ```

    **Edit 6 — Update `@Bean HttpServerSpanFilter httpServerSpanFilter(...)` factory** to take a `Meter` parameter so Plan 04-03 can constructor-inject the histogram. Replace the existing factory:

    ```java
    // Before:
    @Bean
    HttpServerSpanFilter httpServerSpanFilter(Tracer tracer) {
        return new HttpServerSpanFilter(tracer);
    }

    // After (Plan 04-03 will then update the HttpServerSpanFilter constructor
    // itself; this plan only updates the factory wiring + the existing class
    // JavaDoc on the OtelSdkConfiguration @Bean below):
    @Bean
    HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter) {
        return new HttpServerSpanFilter(tracer, meter);
    }
    ```

    Note: This edit will cause `producer-service` to **NOT compile** until Plan 04-03 lands the matching `HttpServerSpanFilter(Tracer, Meter)` constructor. That is acceptable — Plan 04-03 is in Wave 2 and runs after this plan. The Wave-1 verification step below uses `mvn -pl consumer-service compile` (which has no HttpServerSpanFilter and IS expected to compile) plus a `mvn -pl producer-service test-compile -Dmaven.test.skip=true` that is allowed to fail with the specific HttpServerSpanFilter constructor-arity error and nothing else.

    **Edit 7 — Update class-level JavaDoc on `OtelSdkConfiguration`.** Phase 2's class JavaDoc (lines 26-54) mentions "Phase 4 + Phase 5 will reuse the same pattern when wiring the meter and logger providers." Phase 4 has now wired the meter provider. Update the line that says "Phase 4 + Phase 5 will reuse" to "Phase 5 will reuse" (the meter provider is now wired in this same file, no longer forward-looking). Also add ONE NEW JavaDoc paragraph under "Why no autoconfigure?" naming the D-01 helper-extraction:

    ```java
     * <p><b>Why three @Beans (openTelemetry / tracer / meter)?</b> The
     * {@link #openTelemetry()} @Bean is the orchestrator that builds the SDK
     * by delegating to {@link #buildTracerProvider(Resource)} (Phase 2's
     * trace pipeline) and {@link #buildMeterProvider(Resource)} (Phase 4's
     * metric pipeline) — see D-01 in 04-CONTEXT.md. The {@code Tracer} and
     * {@code Meter} @Beans are thin "instrumentation scope" handles that
     * call sites constructor-inject. Phase 5 will add a sibling
     * {@code buildLoggerProvider} helper and a {@code Logger} @Bean.
    ```

    **Constraint preservation checklist (verify after each edit):**
    - `@Bean(destroyMethod = "close")` is on `openTelemetry()` and unchanged (D-07).
    - `private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";` constant is unchanged (D-04 — single shared constant).
    - No new pom dependency added (D-12 — confirm by NOT touching producer-service/pom.xml).
    - Resource attributes unchanged (still 4: SERVICE_NAME / SERVICE_NAMESPACE / SERVICE_INSTANCE_ID / DEPLOYMENT_ENVIRONMENT_NAME).
    - Existing `Tracer` @Bean unchanged.
    - Sampler unchanged (still `Sampler.parentBased(Sampler.alwaysOn())`).
    - BatchSpanProcessor unchanged (still defaults).
    - W3CTraceContextPropagator + W3CBaggagePropagator composite unchanged.
  </action>
  <acceptance_criteria>
    - File exists: `test -f producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - New imports present: `grep -q 'import java.time.Duration;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.api.metrics.Meter;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.sdk.metrics.SdkMeterProvider;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - `buildTracerProvider(Resource)` helper exists: `grep -q 'private SdkTracerProvider buildTracerProvider(Resource' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - `buildMeterProvider(Resource)` helper exists: `grep -q 'private SdkMeterProvider buildMeterProvider(Resource' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - METRIC-01 / D-03 10-second interval present: `grep -q 'Duration.ofSeconds(10)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - PeriodicMetricReader builder used: `grep -q 'PeriodicMetricReader.builder(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - OtlpGrpcMetricExporter builder used: `grep -q 'OtlpGrpcMetricExporter.builder()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - .setMeterProvider added to OpenTelemetrySdk.builder: `grep -q '.setMeterProvider(meterProvider)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - Resource passed to BOTH helpers (D-05): `grep -q 'buildTracerProvider(resource)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'buildMeterProvider(resource)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - Single shared DEFAULT_OTLP_ENDPOINT constant — exactly one declaration: `grep -c 'private static final String DEFAULT_OTLP_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - Endpoint env-var pattern reused inside the meter helper (D-04): `grep -c 'OTEL_EXPORTER_OTLP_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns >= 2 (one read in each helper)
    - Meter @Bean exists with producer scope (D-06): `grep -q '@Bean' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'Meter meter(OpenTelemetry' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'openTelemetry.getMeter("com.example.producer")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - Existing Tracer @Bean unchanged: `grep -q 'openTelemetry.getTracer("com.example.producer")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - HttpServerSpanFilter @Bean factory updated to take Meter: `grep -q 'HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - Lifecycle annotation unchanged (D-07): `grep -c '@Bean(destroyMethod = "close")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - Sampler unchanged: `grep -q 'Sampler.parentBased(Sampler.alwaysOn())' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - W3C propagators preserved: `grep -q 'W3CTraceContextPropagator.getInstance()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'W3CBaggagePropagator.getInstance()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - Resource attributes preserved (D-05 — same identity in traces and metrics): `for c in 'SERVICE_NAME, "order-producer"' 'SERVICE_NAMESPACE, "ose-otel-demo"' SERVICE_INSTANCE_ID DEPLOYMENT_ENVIRONMENT_NAME; do grep -q "$c" producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java || exit 1; done`
    - DOC-03 / D-20 comment density bar: `grep -cE '^\s*//|^\s*\*' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns >= 40 (and likely >= 80 given the new buildMeterProvider banner block)
    - No autoconfigure dependency leaked into pom.xml: `! grep -q 'opentelemetry-sdk-extension-autoconfigure' producer-service/pom.xml`
    - No second endpoint constant introduced: `! grep -q 'DEFAULT_OTLP_METRIC_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
    - producer-service compiles AGAINST consumer-service alone (consumer-side has no HttpServerSpanFilter so it must still compile): `mvn -pl consumer-service -am compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]'`
  </acceptance_criteria>
  <verify>
    <automated>grep -q 'private SdkMeterProvider buildMeterProvider(Resource' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'private SdkTracerProvider buildTracerProvider(Resource' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'Duration.ofSeconds(10)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q '.setMeterProvider(meterProvider)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'openTelemetry.getMeter("com.example.producer")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && grep -q 'HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java && [ "$(grep -cE '^\s*//|^\s*\*' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java)" -ge 40 ] && ! grep -q 'opentelemetry-sdk-extension-autoconfigure' producer-service/pom.xml</automated>
  </verify>
  <done>Producer-service OtelSdkConfiguration.java refactored: openTelemetry() @Bean is a small orchestrator that builds Resource + propagators inline and delegates to buildTracerProvider(resource) + buildMeterProvider(resource). buildTracerProvider contains the verbatim Phase 2 tracer pipeline. buildMeterProvider wires OtlpGrpcMetricExporter + 10-second PeriodicMetricReader + SdkMeterProvider with the same Resource (D-05). Meter @Bean (scope com.example.producer) sits as sibling to Tracer @Bean. HttpServerSpanFilter @Bean factory updated to (Tracer, Meter) for Plan 04-03. Comment density >= 40. No new pom dependency.</done>
</task>

<task id="04-01-T2" type="auto">
  <name>Task 2: Mirror the refactor in consumer-service OtelSdkConfiguration.java (D-02 — same change minus HttpServerSpanFilter)</name>
  <files>consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (current state at HEAD — Phase 2's mirrored file; differs from producer's at HEAD by 5 things only: package, JavaDoc cross-reference, service.name='order-consumer', tracer scope='com.example.consumer', no HttpServerSpanFilter @Bean)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (post-Task-1 state — the producer-side target the consumer mirrors, minus HttpServerSpanFilter)
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 221-235 — the exact two-line diff table; Phase 4 grows the producer/consumer divergence by ONE line for the Meter scope, no other divergence)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-02 per-service mirror, D-07 no consumer-side filter — D-07 still holds because the consumer's only HTTP surface is /actuator/health, which D-06 in Phase 2 already excluded)
  </read_first>
  <action>
    Modify `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` IN PLACE to mirror Task 1's producer-side changes, minus the HttpServerSpanFilter wiring (D-07: consumer has no business HTTP surface; the per-service-duplication ethos applies to SDK BOOTSTRAP, not to instrumentation surfaces — see existing Phase 2 JavaDoc paragraph "Why no HttpServerSpanFilter here?" at consumer file lines 55-60, which stays unchanged).

    **Edits 1-4 are IDENTICAL to producer's Task 1 edits 1-4, with only ONE word substitution:**
    - In Edit 1 (imports): identical — no substitution.
    - In Edit 2 (orchestrator body): replace `"order-producer"` with `"order-consumer"` everywhere it appears (Resource builder).
    - In Edit 3 (buildTracerProvider): identical to producer's helper. Phase 2's consumer-side body is byte-for-byte identical to producer-side except for the absence of `HttpServerSpanFilter` (which lives in a separate file, not this @Bean factory).
    - In Edit 4 (buildMeterProvider): identical to producer's helper.

    **Edit 5 — Add `@Bean Meter meter(OpenTelemetry openTelemetry)` factory** as in producer-side, but with consumer scope name. Place IMMEDIATELY AFTER the existing `@Bean Tracer tracer(...)` factory:

    ```java
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
    ```

    **Edit 6 — DO NOT add a HttpServerSpanFilter factory.** D-07 from Phase 2 remains in force: consumer has no inbound HTTP business surface, only `/actuator/health`. The class JavaDoc paragraph "Why no HttpServerSpanFilter here?" (consumer file lines 55-60 today) is preserved unchanged.

    **Edit 7 — Update class-level JavaDoc.** Same change as producer's Edit 7: replace "Phase 4 + Phase 5 will reuse" with "Phase 5 will reuse" (Phase 4 has now wired the meter), AND add the same "Why three @Beans" paragraph (mention `QueueDepthGauge` from Plan 04-04 instead of `OrderService` / `HttpServerSpanFilter`):

    ```java
     * <p><b>Why three @Beans (openTelemetry / tracer / meter)?</b> The
     * {@link #openTelemetry()} @Bean is the orchestrator that builds the SDK
     * by delegating to {@link #buildTracerProvider(Resource)} (Phase 2's
     * trace pipeline) and {@link #buildMeterProvider(Resource)} (Phase 4's
     * metric pipeline) — see D-01 in 04-CONTEXT.md. The {@code Tracer} and
     * {@code Meter} @Beans are thin "instrumentation scope" handles that
     * call sites constructor-inject. Phase 5 will add a sibling
     * {@code buildLoggerProvider} helper and a {@code Logger} @Bean.
    ```

    **Mirror-symmetry self-check after editing.** After this edit completes, the diff between the two `OtelSdkConfiguration.java` files MUST consist of exactly:
    1. Package declaration (line 1: `com.example.producer.config` vs `com.example.consumer.config`).
    2. Class-level JavaDoc cross-references (the producer's JavaDoc says "consumer-service/.../config/OtelSdkConfiguration.java" and the consumer's says "producer-service/.../config/OtelSdkConfiguration.java").
    3. `Resource.create` line: `SERVICE_NAME, "order-producer"` vs `"order-consumer"`.
    4. `Tracer` scope: `getTracer("com.example.producer")` vs `("com.example.consumer")`.
    5. `Meter` scope (NEW per D-06): `getMeter("com.example.producer")` vs `("com.example.consumer")`.
    6. `HttpServerSpanFilter` @Bean — present in producer file ONLY (D-07).
    7. The consumer-only "Why no HttpServerSpanFilter here?" JavaDoc paragraph (D-07 carryforward).
    8. Comments referencing instrumentation call-sites that differ per service (e.g., producer mentions `OrderService` / `HttpServerSpanFilter`; consumer mentions `QueueDepthGauge`).

    No other divergence is expected.
  </action>
  <acceptance_criteria>
    - File exists: `test -f consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - Same import additions as producer: `grep -q 'import java.time.Duration;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.api.metrics.Meter;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.sdk.metrics.SdkMeterProvider;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - `buildTracerProvider(Resource)` helper exists: `grep -q 'private SdkTracerProvider buildTracerProvider(Resource' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - `buildMeterProvider(Resource)` helper exists: `grep -q 'private SdkMeterProvider buildMeterProvider(Resource' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - 10-second interval present: `grep -q 'Duration.ofSeconds(10)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - Endpoint env-var pattern reused (D-04): `grep -c 'OTEL_EXPORTER_OTLP_ENDPOINT' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns >= 2
    - Meter @Bean exists with consumer scope (D-06): `grep -q 'Meter meter(OpenTelemetry' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'openTelemetry.getMeter("com.example.consumer")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - Existing Tracer @Bean unchanged: `grep -q 'openTelemetry.getTracer("com.example.consumer")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - service.name preserved as "order-consumer": `grep -q 'SERVICE_NAME, "order-consumer"' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - .setMeterProvider added: `grep -q '.setMeterProvider(meterProvider)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - NO HttpServerSpanFilter @Bean factory in consumer (D-07): `! grep -q 'HttpServerSpanFilter httpServerSpanFilter' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - "Why no HttpServerSpanFilter here?" JavaDoc paragraph preserved (D-07 carryforward): `grep -q 'Why no HttpServerSpanFilter here?' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
    - Lifecycle annotation unchanged (D-07): `grep -c '@Bean(destroyMethod = "close")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - Single shared DEFAULT_OTLP_ENDPOINT constant: `grep -c 'private static final String DEFAULT_OTLP_ENDPOINT' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - DOC-03 / D-20 comment density bar: `grep -cE '^\s*//|^\s*\*' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns >= 40
    - consumer-service compiles cleanly (consumer has no HttpServerSpanFilter, so its compile is independent of Plan 04-03): `mvn -pl consumer-service compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]' || mvn -pl consumer-service compile 2>&1 | grep -qE 'BUILD SUCCESS'`
    - Phase 2 invariant preserved: `mise run verify:bom 2>&1 | tail -3 | grep -qE 'one version per OpenTelemetry artifact|Phase 2 baseline confirmed'`
    - Mirror-diff sanity check (the diff between the two files is small): `diff producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java | grep -cE '^[<>]' | awk '{ if ($1 > 80) exit 1 }'` (the line-level diff between the two files should remain small — Phase 4 grows it by ~3 lines, not by 80+)
  </acceptance_criteria>
  <verify>
    <automated>grep -q 'private SdkMeterProvider buildMeterProvider(Resource' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'Duration.ofSeconds(10)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && grep -q 'openTelemetry.getMeter("com.example.consumer")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && ! grep -q 'HttpServerSpanFilter httpServerSpanFilter' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java && [ "$(grep -cE '^\s*//|^\s*\*' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java)" -ge 40 ] && mvn -pl consumer-service compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]' && mise run verify:bom 2>&1 | tail -3 | grep -qE 'one version per OpenTelemetry artifact|Phase 2 baseline confirmed'</automated>
  </verify>
  <done>Consumer-service OtelSdkConfiguration.java mirrors the producer's Task-1 refactor: same imports, same orchestrator/buildTracerProvider/buildMeterProvider shape, same DEFAULT_OTLP_ENDPOINT constant, Meter @Bean with scope "com.example.consumer", NO HttpServerSpanFilter (D-07), "Why no HttpServerSpanFilter here?" JavaDoc preserved. consumer-service compiles cleanly. Comment density >= 40. mise verify:bom green. Mirror-diff between the two files remains small.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 04-01 — SDK refactor + meter pipeline wiring)

| Boundary | Description |
|----------|-------------|
| Service JVM → grafana/otel-lgtm OTLP gRPC :4317 | Already crossed in Phase 2 by the trace exporter; Phase 4 reuses the same endpoint and same env-var pattern (D-04). No new network egress. |
| Spring DI container → SDK @Beans | Same in-process DI as Phase 2; no new boundary. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-01-01 | Information Disclosure | OtlpGrpcMetricExporter could be misconfigured to ship metrics to a wrong endpoint (e.g., env var injection at runtime) | accept | Same env-var contract as Phase 2's span exporter; workshop runs on localhost only; no untrusted env source; mitigation already in place via `Optional.ofNullable(System.getenv).orElse(DEFAULT_OTLP_ENDPOINT)` (Phase 2 D-12 carryforward). Severity: low. |
| T-04-01-02 | Denial of Service | A misconfigured `setInterval(Duration.ofSeconds(0))` could spin the metric reader at the maximum rate the JVM can produce, flooding the OTLP exporter | mitigate | Interval is hardcoded to `Duration.ofSeconds(10)` in source — not env-driven, so a malicious env value cannot weaponize it. Code review on this PR is the gate. Severity: low. |
| T-04-01-03 | Tampering | A future PR could silently extract `OtelSdkConfiguration.java` into a shared `otel-bootstrap` autoconfiguration, defeating DOC-05 | mitigate | The Phase 2 D-01 / DOC-05 callout in README and the JavaDoc class header explicitly warn against this. Plan 04-01 preserves both; Plan 04-05's exit-gate verification confirms the per-service-duplication property holds. Severity: low. |
| T-04-01-04 | Repudiation | SdkMeterProvider.shutdown() not invoked at JVM exit drops the last batch of metrics silently | mitigate | The existing `@Bean(destroyMethod = "close")` on `openTelemetry()` cascades through `OpenTelemetrySdk.close()` to `SdkMeterProvider.shutdown().join(10s)` — verified by Plan 04-05's runtime smoke test (Ctrl-C produces a final metric scrape). Severity: low. |

**Phase scope:** Pure SDK refactor + meter pipeline addition. No new network surface, no new auth, no new secrets, no new user-controlled inputs.
</threat_model>

<verification>
- Producer file: openTelemetry() @Bean orchestrator + buildTracerProvider(Resource) helper + buildMeterProvider(Resource) helper + Meter @Bean (scope com.example.producer) + HttpServerSpanFilter factory taking (Tracer, Meter); class JavaDoc updated to reflect Phase 4 wiring; comment density ≥ 40.
- Consumer file: same refactor MINUS HttpServerSpanFilter (D-07); Meter @Bean with scope com.example.consumer; "Why no HttpServerSpanFilter here?" JavaDoc paragraph preserved; comment density ≥ 40.
- Both files: NEW imports added (java.time.Duration, Meter, OtlpGrpcMetricExporter, SdkMeterProvider, PeriodicMetricReader); single DEFAULT_OTLP_ENDPOINT constant per file; same Resource attributes; same lifecycle annotation; same propagators.
- consumer-service compiles cleanly. producer-service will compile after Plan 04-03 lands the matching HttpServerSpanFilter(Tracer, Meter) constructor.
- mise run verify:bom continues to exit 0 (Phase 2 invariant preserved — no new io.opentelemetry artifacts, exporter-otlp already on classpath).
- Mirror-symmetry property holds: the diff between producer and consumer OtelSdkConfiguration.java is still small (no unexpected divergence beyond the documented per-service identity differences).
</verification>

<success_criteria>
- METRIC-01 partially satisfied (the SDK-bootstrap half — actual 10-second metric flow at runtime is verified in Plan 04-05).
- D-01 (helper extraction), D-02 (mirror), D-03 (10s interval), D-04 (endpoint pattern), D-05 (shared Resource), D-06 (Meter @Bean), D-07 (lifecycle) honored at the source level.
- D-20 (comment density ≥ 40 per file) preserved.
- Phase 2 invariants intact: `@Bean(destroyMethod="close")`, no autoconfigure, single DEFAULT_OTLP_ENDPOINT constant, per-service mirror property.
- Plan 04-02, 04-03, 04-04 (Wave 2) can constructor-inject the Meter @Bean.
</success_criteria>

<output>
After completion, create `.planning/phases/04-metrics/04-01-SUMMARY.md` documenting:
- Final shape of producer's OtelSdkConfiguration.java: paste the @Bean orchestrator + the two helper signatures + the Meter @Bean.
- Final shape of consumer's OtelSdkConfiguration.java: same, minus the HttpServerSpanFilter factory.
- Comment-density grep result for both files.
- Mirror-diff line count between the two files.
- mise verify:bom output (last 3 lines).
- mvn -pl consumer-service compile output (last 3 lines).
- A note that producer-service compile is BLOCKED on Plan 04-03 landing HttpServerSpanFilter(Tracer, Meter) constructor — expected, by design.
- Files modified: 2 (both OtelSdkConfiguration.java files); 0 new files; 0 new pom dependencies.
</output>
</content>
</invoke>