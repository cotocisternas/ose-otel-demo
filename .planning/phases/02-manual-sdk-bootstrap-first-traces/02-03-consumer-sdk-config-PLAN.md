---
id: 02-03-consumer-sdk-config
phase: 02-manual-sdk-bootstrap-first-traces
plan: 03
type: execute
wave: 2
depends_on: [02-01-pom-dependencies]
requirements: [TRACE-01, TRACE-02, TRACE-03, TRACE-04, DOC-03]
files_modified:
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
autonomous: true
must_haves:
  truths:
    - "consumer-service/.../config/OtelSdkConfiguration.java exists and is STRUCTURALLY IDENTICAL to producer-service/.../config/OtelSdkConfiguration.java with EXACTLY TWO differences: package declaration / imports use `com.example.consumer`; service.name=\"order-consumer\"; tracer scope=\"com.example.consumer\""
    - "Resource is built via Resource.getDefault().merge(...) with the four required attributes from semconv 1.40.0 constants: SERVICE_NAME=order-consumer, SERVICE_NAMESPACE=ose-otel-demo, SERVICE_INSTANCE_ID=UUID.randomUUID().toString(), DEPLOYMENT_ENVIRONMENT_NAME=workshop (D-13)"
    - "Sampler.parentBased(Sampler.alwaysOn()) explicit + multi-paragraph teaching comment (D-14) — IDENTICAL text to producer's"
    - "BatchSpanProcessor with default tuning (5s/2048/512); destroyMethod=\"close\" cascades for graceful shutdown flush (D-15, TRACE-04)"
    - "OtlpGrpcSpanExporter endpoint reads OTEL_EXPORTER_OTLP_ENDPOINT with Optional.ofNullable(...).orElse(\"http://localhost:4317\") fallback (D-12)"
    - "Composite W3CTraceContextPropagator + W3CBaggagePropagator registered now (D-16) — Phase 3 reuses these for the consumer-side TracingMessageListenerAdvice extract"
    - "Tracer @Bean uses scope name \"com.example.consumer\" (NOT producer)"
    - "NO HttpServerSpanFilter @Bean factory (D-07): consumer's only HTTP surface is /actuator/health, which would be excluded anyway. The filter exists in producer-service ONLY."
    - "OtelSdkConfiguration.java is heavily commented per DOC-03 — same comment density as producer (>= 40 comment lines); the per-service-duplication callout in the JavaDoc references producer-service explicitly"
    - "mvn -pl consumer-service compile exits 0"
    - "Consumer app starts cleanly via mise run dev:consumer — no exceptions, no unknown_service:java"
  artifacts:
    - path: "consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java"
      provides: "Manual OpenTelemetrySdk wiring (TRACE-01..04) + Tracer bean for instrumentation scope com.example.consumer + DOC-03 textbook comments. NO HttpServerSpanFilter (per D-07)."
      contains: "@Bean(destroyMethod = \"close\")"
  key_links:
    - from: "consumer-service/.../config/OtelSdkConfiguration.java @Bean openTelemetry()"
      to: "OTEL_EXPORTER_OTLP_ENDPOINT env var (mise.toml line 22)"
      via: "System.getenv() + Optional.orElse(\"http://localhost:4317\") fed to OtlpGrpcSpanExporter.builder().setEndpoint(...)"
      pattern: "OTEL_EXPORTER_OTLP_ENDPOINT"
    - from: "consumer's @Bean Tracer scope"
      to: "OrderListener / ProcessingService"
      via: "constructor injection in Plan 02-05"
      pattern: "getTracer(\"com.example.consumer\")"
    - from: "OtelSdkConfiguration.@Bean(destroyMethod=\"close\")"
      to: "BatchSpanProcessor flush on shutdown"
      via: "OpenTelemetrySdk.close() → shutdown().join(10s) → tracerProvider.shutdown() → BSP.worker.shutdown() (RESEARCH §A7)"
      pattern: "destroyMethod = \"close\""
---

<objective>
Wire the consumer-service's manual OpenTelemetry SDK in `OtelSdkConfiguration.java`. This file is **deliberately structurally identical** to producer-service's OtelSdkConfiguration.java (Plan 02-02 / DOC-05 / TRACE-01) with EXACTLY two differences: the service-identity strings (`"order-producer"` → `"order-consumer"`, `"com.example.producer"` → `"com.example.consumer"`) and the package declaration / Spring imports. This identical-looking-twice file IS the lesson — refactoring it into a shared bean would hide one of the two readings the workshop is built around.

The consumer does NOT register `HttpServerSpanFilter` (D-07): consumer-service's only HTTP surface is `/actuator/health`, which the filter would exclude anyway, and the per-service-duplication ethos applies to the SDK BOOTSTRAP, not to instrumentation surfaces — those exist where the surface exists.

Purpose: TRACE-01 (per-service SDK), TRACE-02 (Resource with semconv constants), TRACE-03 (BSP + OtlpGrpcSpanExporter + parentBased(alwaysOn) sampler), TRACE-04 (graceful shutdown via destroyMethod="close"), DOC-03 (heavy textbook comments — second reading of the SDK setup the workshop attendee gets).
Output: 1 new Java file; consumer app starts cleanly with the SDK wired (CONSUMER + INTERNAL spans land in Plan 02-05).
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
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-01-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-05-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="02-03-T1" type="auto">
  <name>Task 1: Write consumer-service/.../config/OtelSdkConfiguration.java — IDENTICAL to producer's with two service-identity strings changed</name>
  <files>consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 30-72 — §A1, A2, A3 verified API surface; §A4 BSP defaults; §A5 endpoint Javadoc; §A6 sampler; §A7 close cascade)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 107-126 — §A9 composite propagators; §A10 manual env-var read pattern)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 128-167 — §B Semconv 1.40.0 constant catalogue: stable + incubating import paths)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 88-105 — D-12, D-13, D-14, D-15, D-16 — same locked decisions as producer)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (line 68 — D-07: consumer does NOT register HttpServerSpanFilter)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 32-43 — Consumer file role: STRUCTURAL COPY of producer with the two specific differences listed verbatim at lines 38-41)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (just written in Plan 02-02 — this is the reference file; Plan 02-03 mirrors it)
    - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java (Phase 1 — confirms package is com.example.consumer.config; @Bean style)
    - consumer-service/pom.xml (just modified in 02-01 — confirms IDENTICAL OTel deps to producer)
  </read_first>
  <action>
    Create EXACTLY ONE new file: `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`. This file is the consumer's mirror of producer-service's OtelSdkConfiguration.java (which was just written in Plan 02-02 / Task 02-02-T1).

    **The file MUST be character-for-character identical to producer's OtelSdkConfiguration.java with these EXACT changes** (and ONLY these changes):

    1. **Package declaration:**
       - Producer: `package com.example.producer.config;`
       - Consumer: `package com.example.consumer.config;`

    2. **Service name (line where SERVICE_NAME is set):**
       - Producer: `.put(ServiceAttributes.SERVICE_NAME, "order-producer")`
       - Consumer: `.put(ServiceAttributes.SERVICE_NAME, "order-consumer")`

    3. **Tracer scope (line where getTracer is called):**
       - Producer: `return openTelemetry.getTracer("com.example.producer");`
       - Consumer: `return openTelemetry.getTracer("com.example.consumer");`

    4. **JavaDoc on the class** — adjust the per-service-duplication callout to point AT producer (consumer is the second-read of the duplication; the JavaDoc names the OTHER copy):
       - Producer says: "The IDENTICAL file lives in consumer-service/.../config/OtelSdkConfiguration.java with two changes only..."
       - Consumer says: "The IDENTICAL file lives in producer-service/.../config/OtelSdkConfiguration.java with two changes only..."

    5. **DELETE the @Bean HttpServerSpanFilter factory method entirely** (D-07: consumer-service does not register the filter — its only HTTP surface is /actuator/health, which would be excluded anyway). Also DELETE the `import com.example.consumer.config.HttpServerSpanFilter;` if any auto-import inserted it (there isn't one to import — that class exists in producer-service only).

    Use the EXACT structure below. All comments are mandatory; do not abbreviate, summarize, or reformat. Note: the comments are essentially identical to producer's — the per-service-duplication is the lesson, so even the comment text is duplicated. The ONLY comment difference is the JavaDoc per-service-duplication callout naming producer-service instead of consumer-service.

    ```java
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
    ```

    Notes for the executor:
    - 4-space Java indentation matching the producer file and `consumer/.../config/RabbitConfig.java`.
    - `@Bean` methods are package-private (no `public` modifier).
    - Imports MUST match producer's exactly (sorted: java.* / io.opentelemetry.* / org.springframework.*) — diff-readability between the two files is part of the lesson.
    - Do NOT add a `@Bean HttpServerSpanFilter` — that bean does NOT exist in com.example.consumer.config (the class lives in com.example.producer.config and is producer-only per D-07).
    - Do NOT change ANYTHING else relative to producer's file. The diff between the two files (`diff producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`) should show ONLY:
      - Package line
      - Doc string (per-service-duplication callout names producer instead of consumer; comment for tracer @Bean references OrderListener/ProcessingService instead of OrderService/OrderPublisher; comments mention dev:consumer / Plan 02-05 instead of dev:producer / Plan 02-04)
      - "order-consumer" instead of "order-producer"
      - "com.example.consumer" instead of "com.example.producer"
      - The "Why no HttpServerSpanFilter here?" JavaDoc paragraph (PRESENT in consumer, ABSENT in producer)
      - The @Bean HttpServerSpanFilter factory method (ABSENT in consumer, PRESENT in producer)

    Verify the diff after writing — if any other line differs, normalize before declaring done.
  </action>
  <acceptance_criteria>
    - `test -f consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` exits 0
    - `grep -c '^package com.example.consumer.config;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '@Configuration' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '@Bean(destroyMethod = "close")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'OpenTelemetrySdk.builder()' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '\.buildAndRegisterGlobal()' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 0
    - Service identity is consumer (NOT producer): `grep -c '"order-consumer"' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - `! grep -F '"order-producer"' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` exits 0 (the producer string MUST NOT appear)
    - Tracer scope is consumer: `grep -c 'getTracer("com.example.consumer")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - `! grep -F 'getTracer("com.example.producer")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` exits 0
    - Resource attrs all four present: `for c in SERVICE_NAME SERVICE_NAMESPACE SERVICE_INSTANCE_ID DEPLOYMENT_ENVIRONMENT_NAME; do grep -q "$c" consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java || exit 1; done` exits 0
    - Sampler present: `grep -c 'Sampler.parentBased(Sampler.alwaysOn())' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - BSP present: `grep -c 'BatchSpanProcessor.builder(' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - OTLP exporter present: `grep -c 'OtlpGrpcSpanExporter.builder()' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - Env var read: `grep -c 'OTEL_EXPORTER_OTLP_ENDPOINT' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - Default endpoint: `grep -c 'http://localhost:4317' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - Composite propagators: `grep -c 'W3CTraceContextPropagator.getInstance()' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1; `grep -c 'W3CBaggagePropagator.getInstance()' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - NO HttpServerSpanFilter @Bean factory (D-07): `! grep -E 'HttpServerSpanFilter' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` exits 0
    - Forbidden imports ABSENT: `! grep -E 'opentelemetry-sdk-extension-autoconfigure|AutoConfiguredOpenTelemetrySdk|GlobalOpenTelemetry' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` exits 0
    - Stable + incubating semconv import paths correct: `grep -c 'import io.opentelemetry.semconv.ServiceAttributes;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1; `grep -c 'import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes;' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - DOC-03 comment density: `grep -cE '^\s*(//|\*|/\*\*|\*/)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns >= 40
    - Per-service-duplication callout names producer: `grep -c 'producer-service/.../config/OtelSdkConfiguration.java' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - "Why no HttpServerSpanFilter here?" paragraph present: `grep -c 'no HttpServerSpanFilter here' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` returns 1
    - consumer-service compiles: `mvn -pl consumer-service -q compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl consumer-service -q compile &amp;&amp; grep -q '@Bean(destroyMethod = "close")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java &amp;&amp; grep -q '"order-consumer"' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java &amp;&amp; grep -q 'getTracer("com.example.consumer")' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java &amp;&amp; ! grep -F '"order-producer"' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java &amp;&amp; ! grep -E 'HttpServerSpanFilter' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java</automated>
  </verify>
  <done>OtelSdkConfiguration.java exists in com.example.consumer.config; structurally identical to producer's with EXACTLY the four documented differences (package, JavaDoc per-service callout naming producer, service.name=order-consumer, tracer scope com.example.consumer); NO HttpServerSpanFilter @Bean factory present (D-07); >= 40 comment lines; consumer-service compiles green.</done>
</task>

<task id="02-03-T2" type="auto">
  <name>Task 2: Smoke-test the consumer SDK boots cleanly (start consumer, no exceptions, no unknown_service:java)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 184-194 — End-user-verifiable surface; in Phase 2 the consumer's CONSUMER span lands in Plan 02-05, but this task verifies the SDK boots cleanly first)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 161-165 — code_context: consumer-service runs on host JVM via mise run dev:consumer; pre-wired env vars from mise.toml)
    - mise.toml (lines 102-105 — dev:consumer task; line 22 OTEL_EXPORTER_OTLP_ENDPOINT)
    - .planning/phases/01-baseline-scaffold/1-05-SUMMARY.md (Phase 1 consumer smoke-test pattern)
  </read_first>
  <action>
    Bring infra up, start the consumer in the background, verify it starts cleanly with the SDK wired (no exceptions, no NoSuchMethodError, no unknown_service:java in output), confirm `/actuator/health` works on port 8081, and that the consumer process shuts down cleanly within 12s of SIGTERM (TRACE-04 graceful flush). Cleanup at the end.

    Note: in Phase 2 Plan 02-03, the consumer has NO span sites yet (those land in Plan 02-05's CONSUMER + INTERNAL spans). This task therefore does NOT assert any traces appear in Tempo for `service.name=order-consumer` — only that the SDK boots and the bean wiring resolves without errors. (Plan 02-05's smoke test will assert the trace visibility.)

    **Step 1 — Ensure infra is up:**
    `mise run infra:up` (idempotent).

    **Step 2 — Start consumer in background:**
    ```
    nohup mise run dev:consumer > /tmp/consumer-02-03.log 2>&1 &
    PID=$!
    for i in $(seq 1 45); do
      if grep -q "Started ConsumerApplication" /tmp/consumer-02-03.log; then break; fi
      sleep 2
    done
    grep -q "Started ConsumerApplication" /tmp/consumer-02-03.log || { tail -50 /tmp/consumer-02-03.log; kill $PID 2>/dev/null; exit 1; }
    ```

    **Step 3 — Verify NO startup exceptions related to OTel/SDK:**
    ```
    if grep -E 'NoSuchMethodError|NoClassDefFoundError|IllegalArgumentException.*opentelemetry|unknown_service:java' /tmp/consumer-02-03.log; then
      kill $PID 2>/dev/null
      exit 1
    fi
    ```

    Specifically the strings `unknown_service:java` and `IllegalArgumentException.*endpoint must start with` MUST NOT appear (proves Resource override and OTLP endpoint config both work).

    **Step 4 — Verify /actuator/health on port 8081:**
    ```
    test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/actuator/health)" = "200"
    curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'
    ```

    **Step 5 — Optional: verify the SDK didn't error-log on its background activity:**
    ```
    # The OTLP exporter's BatchSpanProcessor logs on connection issues only when there's actually something to export.
    # In Phase 2 Plan 02-03 there are no spans yet — but we still don't want stray ERROR-level lines about OTel.
    ! grep -E '^[0-9-]+T[0-9:.+ ]+ +ERROR.*opentelemetry' /tmp/consumer-02-03.log
    ```

    **Step 6 — Cleanup:**
    ```
    kill $PID 2>/dev/null
    for i in $(seq 1 12); do kill -0 $PID 2>/dev/null || break; sleep 1; done
    ! kill -0 $PID 2>/dev/null  # Confirm graceful exit within 12s
    ```

    Failure modes for the executor:
    - "Started ConsumerApplication" never appears → check /tmp/consumer-02-03.log; common cause is port collision on 8081 or RabbitMQ not reachable.
    - `IllegalArgumentException: endpoint must start with http://` → DEFAULT_OTLP_ENDPOINT typo in T1, or `mise env` does not include OTEL_EXPORTER_OTLP_ENDPOINT.
    - `service.name=unknown_service:java` somewhere → Resource.getDefault().merge args swapped; re-read RESEARCH §A3.
  </action>
  <acceptance_criteria>
    - Background `mise run dev:consumer` reaches `Started ConsumerApplication` within 90 seconds (extracted from /tmp/consumer-02-03.log)
    - No OTel-related startup exceptions: `! grep -E 'NoSuchMethodError|NoClassDefFoundError|IllegalArgumentException.*opentelemetry|unknown_service:java' /tmp/consumer-02-03.log` exits 0
    - GET /actuator/health on port 8081 returns 200: `test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/actuator/health)" = "200"`
    - `curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'` exits 0
    - No ERROR-level OTel log lines: `! grep -E '^[0-9-]+T[0-9:.+ ]+ +ERROR.*opentelemetry' /tmp/consumer-02-03.log` exits 0
    - Consumer process shuts down cleanly within 12s of SIGTERM (TRACE-04 graceful flush): after `kill $PID`, `for i in $(seq 1 12); do kill -0 $PID 2>/dev/null || break; sleep 1; done; ! kill -0 $PID 2>/dev/null` exits 0
    - No leftover spring-boot:run process: `! pgrep -f spring-boot:run` exits 0
  </acceptance_criteria>
  <verify>
    <automated>nohup mise run dev:consumer &gt; /tmp/consumer-02-03.log 2&gt;&amp;1 &amp; PID=$!; for i in $(seq 1 45); do grep -q "Started ConsumerApplication" /tmp/consumer-02-03.log &amp;&amp; break; sleep 2; done; grep -q "Started ConsumerApplication" /tmp/consumer-02-03.log &amp;&amp; ! grep -E 'NoSuchMethodError|NoClassDefFoundError|unknown_service:java' /tmp/consumer-02-03.log &amp;&amp; test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/actuator/health)" = "200"; CODE=$?; kill $PID 2&gt;/dev/null; for i in $(seq 1 12); do kill -0 $PID 2&gt;/dev/null || break; sleep 1; done; exit $CODE</automated>
  </verify>
  <done>Consumer-service starts cleanly with the manual SDK on the classpath; no NoSuchMethodError / NoClassDefFoundError / unknown_service:java; /actuator/health on 8081 returns 200; no ERROR-level OTel log lines; process shuts down cleanly within 12s of SIGTERM (TRACE-04 graceful flush via destroyMethod="close").</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 02-03 — consumer-service SDK config)

| Boundary | Description |
|----------|-------------|
| Consumer JVM → OTLP gRPC endpoint (NEW in this plan) | Spans flow over a gRPC channel to OTEL_EXPORTER_OTLP_ENDPOINT (workshop default: http://localhost:4317) |
| mise.toml [env] → consumer JVM | OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_EXPORTER_OTLP_PROTOCOL, SPRING_RABBITMQ_*, CONSUMER_PORT enter via process env |
| RabbitMQ broker → consumer (existing from Phase 1) | AMQP messages from `orders.created` queue; no inbound HTTP business surface |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-03-01 | Information Disclosure | OTEL_EXPORTER_OTLP_ENDPOINT env var accepts any URL — same risk as producer (Phase-level T1) | mitigate | Same control as producer: DEFAULT_OTLP_ENDPOINT hardcoded to http://localhost:4317; explicit fallback prevents accidental redirect; README will document override semantics |
| T-2-03-02 | Information Disclosure | Span attributes leaking sensitive payload data | accept | Phase 2 plan 02-03 only wires the SDK; the actual CONSUMER + INTERNAL spans (Plan 02-05) capture only span name + messaging semconv attrs (system / destination / operation_type / routing_key) — NOT the message body or `orderId`. (Phase-level T2 mitigated.) |
| T-2-03-03 | Information Disclosure | UUID.randomUUID() for service.instance.id | accept | Same as producer T-2-02-06 — UUID v4 is fine for an instance identifier |
| T-2-03-04 | DoS | OTLP exporter blocking on a slow/unreachable backend during shutdown could delay Spring's destroy lifecycle | mitigate | Same control as producer (T-2-02-07): close().shutdown().join(10s) bound — verified by T2's 12s graceful-shutdown gate |
| T-2-03-05 | Repudiation | Without HttpServerSpanFilter, /actuator/health requests on port 8081 produce no spans — an attacker probing for fingerprint info leaves no Tempo trace | accept | D-07 explicit decision; the actuator allow-list (Phase 1) restricts to /health only; workshop scope. Phase-level T3 verified across both services. |
| T-2-03-06 | Spoofing | Consumer spans starting from Context.root() (Plan 02-05) without propagation accept any "implicit parent" the broker provides | accept | This IS the Phase 2 broken-then-fixed pedagogy; Phase 3 fixes it via the propagation pair. Consumer spans live in their own trace ID until then; nothing to spoof |

**Plan scope:** Consumer-service SDK bootstrap. Network surface introduced: outbound gRPC to localhost:4317. No new inbound surface (existing AMQP consumer + /actuator/health unchanged in shape). Out of scope: TLS to OTLP backend (loopback), authentication on OTLP endpoint (lgtm doesn't require any), span attribute redaction (Phase 2 spans capture no PII).
</threat_model>

<verification>
- `mvn -pl consumer-service compile` exits 0 after T1 lands
- Background `mise run dev:consumer` reaches `Started ConsumerApplication` within 90s with no OTel-related exceptions
- /actuator/health on port 8081 returns 200 + status UP
- No ERROR-level OTel log lines during the smoke test
- Process shuts down cleanly within 12s of SIGTERM (TRACE-04 graceful flush)
- `mise run verify:bom` still exits 0 (Plan 02-01's invariant preserved)
- diff between producer and consumer OtelSdkConfiguration.java contains ONLY the documented differences (package, JavaDoc per-service-duplication callout, service.name string, tracer scope string, "Why no HttpServerSpanFilter" paragraph, missing @Bean HttpServerSpanFilter factory)
</verification>

<success_criteria>
- TRACE-01 (per-service SDK config) satisfied for consumer.
- TRACE-02 satisfied: Resource carries service.name=order-consumer + namespace + instance.id + deployment.environment.name.
- TRACE-03 satisfied: SdkTracerProvider with BatchSpanProcessor + OtlpGrpcSpanExporter targeting the env-var endpoint with localhost:4317 fallback; Sampler.parentBased(Sampler.alwaysOn()) explicit + multi-paragraph teaching comment.
- TRACE-04 satisfied: @Bean(destroyMethod="close") confirmed by clean shutdown within 12s of SIGTERM.
- DOC-03 satisfied (consumer half): heavy textbook comments in consumer's OtelSdkConfiguration.java.
- D-07 honored: NO HttpServerSpanFilter in consumer; the JavaDoc paragraph "Why no HttpServerSpanFilter here?" makes the decision explicit.
- D-12 enforced: no opentelemetry-sdk-extension-autoconfigure or AutoConfiguredOpenTelemetrySdk usage.
- D-16 enforced: composite W3C trace-context + W3C baggage propagators registered for Phase 3 reuse.
- Per-service-duplication ethos (DOC-05) is now visible in the codebase: two structurally-identical files exist, with the JavaDoc cross-reference making the duplication explicit. Plan 02-06 will write the README callout that names the duplication intentional.
</success_criteria>

<output>
After completion, create `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-03-SUMMARY.md` documenting:
- File tree of consumer-service/src/main/java/com/example/consumer/config/ (RabbitConfig.java + OtelSdkConfiguration.java = 2 files)
- Confirmed `mvn -pl consumer-service compile` BUILD SUCCESS line
- Confirmed startup line (`Started ConsumerApplication in X seconds`) — paste from /tmp/consumer-02-03.log
- Confirmed /actuator/health on 8081 returns 200 + status UP
- Confirmed graceful shutdown latency (paste seconds-to-exit measurement)
- Comment-line count from `grep -cE '^\s*(//|\*|/\*\*|\*/)' OtelSdkConfiguration.java` (should be >= 40)
- Diff vs producer's OtelSdkConfiguration.java (paste output of `diff producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`) — should show ONLY the 5 documented differences (package, JavaDoc callout naming producer, service.name string, tracer scope, "Why no HttpServerSpanFilter" paragraph + missing @Bean factory)
- Files created: 1 (OtelSdkConfiguration.java)
- Hand-off for Plan 02-05: Tracer bean is now constructor-injectable; OrderListener + ProcessingService can pull it from Spring's DI container in Plan 02-05.
</output>
