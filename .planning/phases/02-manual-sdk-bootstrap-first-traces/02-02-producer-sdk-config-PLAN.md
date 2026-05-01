---
id: 02-02-producer-sdk-config
phase: 02-manual-sdk-bootstrap-first-traces
plan: 02
type: execute
wave: 2
depends_on: [02-01-pom-dependencies]
requirements: [TRACE-01, TRACE-02, TRACE-03, TRACE-04, TRACE-05, DOC-03]
files_modified:
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
autonomous: true
must_haves:
  truths:
    - "producer-service/.../config/OtelSdkConfiguration.java exists with @Configuration, exposes @Bean(destroyMethod=\"close\") OpenTelemetry openTelemetry() and @Bean Tracer tracer(OpenTelemetry) — the SDK is constructor-injectable everywhere (D-02)"
    - "Resource is built via Resource.getDefault().merge(...) with all four required attributes from semconv 1.40.0 constants: ServiceAttributes.SERVICE_NAME=order-producer, SERVICE_NAMESPACE=ose-otel-demo, SERVICE_INSTANCE_ID=UUID.randomUUID().toString(), DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME=workshop (D-13)"
    - "Sampler is explicit: Sampler.parentBased(Sampler.alwaysOn()) — never default; multi-paragraph teaching comment present (D-14)"
    - "BatchSpanProcessor with default tuning (5s/2048/512) — production shape, not SimpleSpanProcessor (D-15); destroyMethod=close cascades to it for graceful shutdown flush"
    - "OtlpGrpcSpanExporter endpoint reads OTEL_EXPORTER_OTLP_ENDPOINT env var with Optional.ofNullable(...).orElse(\"http://localhost:4317\") fallback (D-12); endpoint string starts with http:// (RESEARCH §A5)"
    - "Composite W3CTraceContextPropagator + W3CBaggagePropagator registered now (D-16) — Phase 3 reuses these without re-wiring"
    - "Tracer @Bean uses scope name \"com.example.producer\" via openTelemetry.getTracer(\"com.example.producer\") (D-02)"
    - "HttpServerSpanFilter (D-05) extends OncePerRequestFilter, overrides shouldNotFilter() to short-circuit /actuator/* paths (D-06), and wraps every NON-actuator request in a SERVER span using the D-01 inline template; @Bean wire-up lives in OtelSdkConfiguration"
    - "SERVER span sets HTTP semconv attributes: HTTP_REQUEST_METHOD + URL_PATH + URL_SCHEME + SERVER_ADDRESS + SERVER_PORT + HTTP_ROUTE pre-chain.doFilter; HTTP_RESPONSE_STATUS_CODE post-chain.doFilter (TRACE-05 + Claude's-discretion 7-attr set)"
    - "OtelSdkConfiguration.java is heavily commented per DOC-03 — sampler tradeoff (4-8 lines), env-var contract, per-service-duplication rationale, semconv-incubating-mandatory note, BatchSpanProcessor production-shape note, propagators-wired-but-not-yet-exercised note. Total comment lines >= 40 (the comments ARE the deliverable)"
    - "mvn -pl producer-service compile exits 0 — file compiles against the deps from Plan 02-01"
    - "Producer app starts cleanly (mise run dev:producer) — no exception in startup logs, OpenTelemetry beans resolve, no unknown_service:java in any output"
  artifacts:
    - path: "producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java"
      provides: "Manual OpenTelemetrySdk wiring (TRACE-01..04) + Tracer bean for instrumentation scope com.example.producer + HttpServerSpanFilter @Bean factory + DOC-03 textbook comments"
      contains: "@Bean(destroyMethod = \"close\")"
    - path: "producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java"
      provides: "OncePerRequestFilter wrapping every non-/actuator request in a SERVER span with HTTP semconv attrs (TRACE-05); D-05 + D-06 + D-07"
      contains: "extends OncePerRequestFilter"
  key_links:
    - from: "producer-service/.../config/OtelSdkConfiguration.java @Bean openTelemetry()"
      to: "OTEL_EXPORTER_OTLP_ENDPOINT env var (mise.toml line 22)"
      via: "System.getenv() + Optional.orElse(\"http://localhost:4317\") fed to OtlpGrpcSpanExporter.builder().setEndpoint(...)"
      pattern: "OTEL_EXPORTER_OTLP_ENDPOINT"
    - from: "producer-service/.../config/HttpServerSpanFilter.java"
      to: "Spring MVC filter chain"
      via: "@Bean HttpServerSpanFilter in OtelSdkConfiguration; Spring Boot auto-discovers Filter beans (RESEARCH §A8)"
      pattern: "extends OncePerRequestFilter"
    - from: "OtelSdkConfiguration.@Bean(destroyMethod=\"close\")"
      to: "BatchSpanProcessor flush on shutdown"
      via: "OpenTelemetrySdk.close() → shutdown().join(10s) → tracerProvider.shutdown() → BSP.worker.shutdown() (RESEARCH §A7)"
      pattern: "destroyMethod = \"close\""
---

<objective>
Wire the producer-service's manual OpenTelemetry SDK in `OtelSdkConfiguration.java` (TRACE-01..04) and add `HttpServerSpanFilter.java` (TRACE-05 + D-05/D-06/D-07). Together these two files are the producer's complete Phase 2 SDK surface: the SDK config exposes `OpenTelemetry` (with graceful-shutdown flush), `Tracer` (scope `com.example.producer`), and `HttpServerSpanFilter` as Spring beans; the filter wraps every non-`/actuator/*` request in a SERVER span. Heavy DOC-03 comments throughout — the code IS the workshop's textbook for the SDK setup.

Per the per-service-duplication ethos (DOC-05), this file is structurally identical to consumer-service's `OtelSdkConfiguration.java` (Plan 02-03) except for two service-identity strings (`order-producer`, `com.example.producer`) and the addition of `HttpServerSpanFilter` which is producer-only (D-07: consumer's only HTTP surface is `/actuator/health`, excluded anyway).

Purpose: TRACE-01 (per-service SDK), TRACE-02 (Resource with semconv constants), TRACE-03 (BSP + OtlpGrpcSpanExporter + parentBased(alwaysOn) sampler), TRACE-04 (graceful shutdown via destroyMethod="close"), TRACE-05 (SERVER span with HTTP semconv attrs), DOC-03 (heavy textbook comments). Output: 2 new Java files + producer app starts cleanly with the SDK wired (no spans yet from business code — Plan 02-04 adds those).
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
@.planning/phases/01-baseline-scaffold/1-04-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="02-02-T1" type="auto">
  <name>Task 1: Write producer-service/.../config/OtelSdkConfiguration.java with heavy DOC-03 commentary</name>
  <files>producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 30-72 — §A1, A2, A3 verified API surface: builder().setTracerProvider().setPropagators().build(); Resource.getDefault().merge(...); SdkTracerProvider.builder().setResource().setSampler().addSpanProcessor().build())
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 56-89 — §A4 BSP defaults match D-15; §A5 endpoint MUST start with http://; §A6 parentBased single-arg form correct; §A7 close()→shutdown().join(10s) cascade for graceful flush)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 107-126 — §A9 composite propagators construction; §A10 manual env-var read pattern with orElse default)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 128-167 — §B Semconv 1.40.0 constant catalogue: ServiceAttributes (stable), DeploymentIncubatingAttributes (incubating coord) — full import paths)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 88-105 — D-12 manual env-var read; D-13 Resource attrs locked; D-14 explicit sampler with multi-paragraph teaching comment; D-15 BSP defaults; D-16 composite propagators)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 107-118 — Claude's-discretion: package path com.example.producer.config; service-name hardcoded; OTEL_EXPORTER_OTLP_PROTOCOL informational; sampler comment depth multi-paragraph 4-8 lines)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 9-30 — file role + RabbitConfig analog at lines 12-37 of producer-service/.../config/RabbitConfig.java for the @Configuration shape)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 274-302 — Tracer Bean Wiring section with verbatim @Bean Tracer code)
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (Phase 1 file — confirms package `com.example.producer.config`; `@Configuration` + `@Bean` style for analog reference)
    - producer-service/pom.xml (just modified in 02-01 — confirms which io.opentelemetry* artifacts are on the classpath)
    - mise.toml (lines 22-23 — OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OTLP_PROTOCOL pre-wired)
  </read_first>
  <action>
    Create EXACTLY ONE new file: `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`. The file is intentionally LARGE (>120 lines) because the comments ARE the deliverable per DOC-03 — workshop attendees read this file line-by-line in their IDE.

    Use the EXACT structure below. All comments are mandatory; do not abbreviate, summarize, or reformat. The semconv constant imports must be the exact paths verified in RESEARCH §B (stable from `io.opentelemetry.semconv.*`; incubating from `io.opentelemetry.semconv.incubating.*`).

    ```java
    package com.example.producer.config;

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
     * Manual OpenTelemetry SDK bootstrap for producer-service (TRACE-01..04 + DOC-03).
     *
     * THIS FILE IS THE WORKSHOP'S TEXTBOOK FOR THE SDK SETUP.
     * Every @Bean below carries an inline comment explaining what each builder
     * call does and why. Read it top-to-bottom in your IDE.
     *
     * <p><b>Why duplicated per service?</b> The IDENTICAL file lives in
     * consumer-service/.../config/OtelSdkConfiguration.java with two changes
     * only: the service.name string ("order-producer" → "order-consumer") and
     * the tracer scope name ("com.example.producer" → "com.example.consumer").
     * Refactoring this into an @AutoConfiguration bean in otel-bootstrap
     * would hide one of the two readings the workshop is built around. See
     * README "Why is OtelSdkConfiguration.java duplicated?" (DOC-05).
     *
     * <p><b>Why no autoconfigure?</b> The opentelemetry-sdk-extension-autoconfigure
     * artifact would read OTEL_* env vars magically and build the SDK behind
     * the scenes. We deliberately do NOT pull it in (see producer-service/pom.xml).
     * The env-var contract is visible in code below — System.getenv()
     * with an explicit fallback. Phase 4 + Phase 5 will reuse the same pattern
     * when wiring the meter and logger providers.
     *
     * <p><b>Why semconv-incubating?</b> The messaging conventions
     * (MessagingIncubatingAttributes — used by Plan 02-04 for the PRODUCER span)
     * and the deployment.environment.name attribute used below
     * (DeploymentIncubatingAttributes) live in the -incubating coord because
     * they are still evolving in the OTel spec. The stable semconv 1.40.0 jar
     * alone is NOT enough; both jars are pinned in producer-service/pom.xml.
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
         * (Ctrl-C on `mise run dev:producer`), Spring calls openTelemetry.close(),
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
            // build the TracingMessagePostProcessor inject method.
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
         * Tracer for instrumentation scope "com.example.producer".
         *
         * The scope name typically matches the package or library being
         * instrumented. Workshop attendees inject this Tracer into
         * OrderService, OrderPublisher, and HttpServerSpanFilter via
         * constructor injection (D-02). Tracer is thread-safe; one bean
         * is reused across all callers.
         */
        @Bean
        Tracer tracer(OpenTelemetry openTelemetry) {
            return openTelemetry.getTracer("com.example.producer");
        }

        /**
         * Wraps every NON-/actuator HTTP request in a SERVER span (TRACE-05).
         *
         * Spring Boot 3.4 auto-discovers @Bean Filter instances and wraps them
         * in a default FilterRegistrationBean(/*) — no explicit URL-pattern
         * config needed here. The /actuator/* exclusion lives in the filter
         * itself via shouldNotFilter() (D-06).
         *
         * Producer-only (D-07): consumer-service's only HTTP surface is
         * /actuator/health, which would be excluded anyway, so the consumer
         * does not register this filter.
         */
        @Bean
        HttpServerSpanFilter httpServerSpanFilter(Tracer tracer) {
            return new HttpServerSpanFilter(tracer);
        }
    }
    ```

    Notes for the executor:
    - Use 4-space Java indentation matching `RabbitConfig.java` style.
    - All `@Bean` methods are package-private (no `public` modifier) — matches `RabbitConfig.java` convention.
    - Imports are sorted: java.* first, then io.opentelemetry.*, then org.springframework.* (as in the template above).
    - Do NOT add `static` to `DEFAULT_OTLP_ENDPOINT` modifier order (final static is wrong; static final is correct — the template has it right).
    - Do NOT add `@Lazy` to any bean. Spring resolves OpenTelemetry first, then Tracer (which depends on it), then HttpServerSpanFilter (which depends on Tracer) — DAG is acyclic.
    - The file MUST compile without HttpServerSpanFilter.java existing yet (Java will accept the forward reference once T2 lands; until then `mvn compile` will fail with "cannot find symbol HttpServerSpanFilter" — this is expected; T2 closes the gap).
  </action>
  <acceptance_criteria>
    - `test -f producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` exits 0
    - `grep -c '^package com.example.producer.config;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '@Configuration' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '@Bean(destroyMethod = "close")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'OpenTelemetrySdk.builder()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '\.buildAndRegisterGlobal()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 0 (must use .build() per RESEARCH §A1)
    - `grep -c 'ServiceAttributes.SERVICE_NAME' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '"order-producer"' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1 (hardcoded service name per Claude's-discretion)
    - `grep -c '"ose-otel-demo"' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c '"workshop"' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'UUID.randomUUID()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'Resource.getDefault().merge(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'Sampler.parentBased(Sampler.alwaysOn())' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'BatchSpanProcessor.builder(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'OtlpGrpcSpanExporter.builder()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'OTEL_EXPORTER_OTLP_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'http://localhost:4317' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1 (default fallback in DEFAULT_OTLP_ENDPOINT)
    - `grep -c 'W3CTraceContextPropagator.getInstance()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'W3CBaggagePropagator.getInstance()' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - `grep -c 'TextMapPropagator.composite(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - Tracer scope is com.example.producer: `grep -c 'getTracer("com.example.producer")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - HttpServerSpanFilter @Bean factory present: `grep -c 'HttpServerSpanFilter httpServerSpanFilter(' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - Forbidden imports ABSENT: `! grep -E 'opentelemetry-sdk-extension-autoconfigure|AutoConfiguredOpenTelemetrySdk|GlobalOpenTelemetry' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` exits 0
    - Stable semconv import path correct: `grep -c 'import io.opentelemetry.semconv.ServiceAttributes;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - Incubating semconv import path correct: `grep -c 'import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes;' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns 1
    - DOC-03 comment density: ignoring blank lines, count single-line and JavaDoc comment lines — `grep -cE '^\s*(//|\*|/\*\*|\*/)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` returns >= 40
    - At least one comment block has a "Sampler" multi-paragraph teaching note: `grep -B1 -A12 'parent-based, always-on root' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | grep -c 'traceIdRatioBased(0.1)'` returns >= 1 (the comment must mention the 10% sampling alternative per D-14)
    - At least one comment explains "destroyMethod=close" graceful shutdown: `grep -B1 -A6 'destroyMethod' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | grep -c 'graceful'` returns >= 1
    - File compiles in isolation requires HttpServerSpanFilter to also be present (T2 below); deferring full compile gate to T2's acceptance.
  </acceptance_criteria>
  <verify>
    <automated>grep -q '@Bean(destroyMethod = "close")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java &amp;&amp; grep -q 'Sampler.parentBased(Sampler.alwaysOn())' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java &amp;&amp; grep -q 'getTracer("com.example.producer")' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java &amp;&amp; grep -q 'OTEL_EXPORTER_OTLP_ENDPOINT' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java &amp;&amp; grep -q 'DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java &amp;&amp; ! grep -E 'AutoConfiguredOpenTelemetrySdk|GlobalOpenTelemetry|buildAndRegisterGlobal' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java</automated>
  </verify>
  <done>OtelSdkConfiguration.java exists in com.example.producer.config; carries @Bean(destroyMethod="close") OpenTelemetry openTelemetry() with the exact 6-builder-call chain (Resource.getDefault().merge / OtlpGrpcSpanExporter / BatchSpanProcessor.builder / Sampler.parentBased(alwaysOn) / SdkTracerProvider.builder / ContextPropagators.create); plus @Bean Tracer (scope com.example.producer) and @Bean HttpServerSpanFilter factory; service-name "order-producer" hardcoded; OTEL_EXPORTER_OTLP_ENDPOINT env-var read with localhost:4317 fallback; semconv constants imported from the correct stable + incubating coords; >= 40 comment lines (DOC-03 textbook density). Compiles only AFTER T2 closes the HttpServerSpanFilter forward reference.</done>
</task>

<task id="02-02-T2" type="auto">
  <name>Task 2: Write producer-service/.../config/HttpServerSpanFilter.java with D-01 inline span template + HTTP semconv attrs</name>
  <files>producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 91-105 — §A8 OncePerRequestFilter + shouldNotFilter() canonical exclusion hook; FilterRegistrationBean.setUrlPatterns has known issues per spring-boot#38331)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 142-160 — §B HTTP semconv constants table: HttpAttributes / UrlAttributes / ServerAttributes — full import paths and exact constant names)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 60-69 — D-05 SERVER span via OncePerRequestFilter; D-06 /actuator/* exclusion with code comment about docker-compose healthcheck noise; D-07 producer-only)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (line 111 — Claude's-discretion: 7-attr HTTP semconv set including the 3 mandated by TRACE-05 + 4 additions; stop short of body sizes / user-agent)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 36-50 — D-01 pure inline span template — the EXACT 8-line idiom; D-03 catch block included even in Phase 2 for forward-compatibility)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 45-71 — file role + analog: OrderListener constructor-injection shape; NEW file extends OncePerRequestFilter NOT @Component)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 230-272 — Span-Wrapping Pattern Application Map row 1 (HttpServerSpanFilter): exact span name "POST /orders" / kind SERVER / 7-attr set; verbatim D-01 template at lines 244-272)
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (Phase 1 — for the constructor-injection style this filter mirrors)
  </read_first>
  <action>
    Create EXACTLY ONE new file: `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`. The class extends Spring's `OncePerRequestFilter`, exposes a constructor for `Tracer` injection, and overrides `doFilterInternal` (wraps the chain in a SERVER span) plus `shouldNotFilter` (excludes /actuator/* paths). The body of `doFilterInternal` uses the EXACT D-01 inline template — no helper, no extraction, full try/Scope/try/catch/finally idiom.

    Use this EXACT structure:

    ```java
    package com.example.producer.config;

    import java.io.IOException;

    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Scope;
    import io.opentelemetry.semconv.HttpAttributes;
    import io.opentelemetry.semconv.ServerAttributes;
    import io.opentelemetry.semconv.UrlAttributes;

    import jakarta.servlet.FilterChain;
    import jakarta.servlet.ServletException;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;

    import org.springframework.web.filter.OncePerRequestFilter;

    /**
     * Wraps every non-/actuator HTTP request in a SERVER span with HTTP
     * semantic-convention attributes (TRACE-05).
     *
     * <p><b>Why a Filter and not a HandlerInterceptor?</b> A Filter wraps the
     * Servlet chain symmetrically — anything Spring MVC throws still flows
     * through the finally block here, ensuring span.end() always fires.
     * HandlerInterceptor.afterCompletion runs only after the dispatch returns
     * cleanly. The Filter form also makes http.response.status_code naturally
     * available after chain.doFilter(), since the response object has been
     * fully written by then.
     *
     * <p><b>Why exclude /actuator/?</b> docker-compose healthchecks hit
     * /actuator/health every few seconds. Without the exclusion, Tempo would
     * be flooded with health-check spans that drown out the order-flow
     * traces the workshop is teaching. This is a tiny taste of the
     * production tradeoff between sampling and filtering — a pre-sampling
     * filter for known-noisy paths is one of the simplest controls available.
     *
     * <p><b>Why producer-only?</b> consumer-service's only HTTP surface is
     * /actuator/health, which would be excluded anyway, so the consumer
     * does not register this filter (D-07). The per-service-duplication
     * ethos applies to the SDK BOOTSTRAP (OtelSdkConfiguration); it does
     * NOT apply to instrumentation surfaces — those exist where the
     * surface exists.
     */
    public class HttpServerSpanFilter extends OncePerRequestFilter {

        private final Tracer tracer;

        public HttpServerSpanFilter(Tracer tracer) {
            this.tracer = tracer;
        }

        /**
         * Skip /actuator/* paths so health-check noise stays out of Tempo.
         *
         * shouldNotFilter() is the canonical OncePerRequestFilter exclusion
         * hook. Using FilterRegistrationBean.setUrlPatterns(...) instead is
         * known to misbehave on Spring Boot 3.4 (spring-boot#38331) — that
         * approach silently includes paths matching the prefix when
         * multiple filter beans exist.
         */
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return request.getRequestURI().startsWith("/actuator/");
        }

        /**
         * Wrap the filter chain in a SERVER span using the D-01 inline template.
         *
         * Span name follows the OTel HTTP semconv recommendation:
         * "{METHOD} {ROUTE}" (we use the URI path here as the route — Spring's
         * actual @RequestMapping route would be ideal but isn't available
         * pre-dispatch from a generic Filter).
         */
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {

            String method = request.getMethod();
            String path = request.getRequestURI();

            // ---- D-01 inline span template ----
            //
            // Pure inline. No helper, no AOP. The boilerplate IS the lesson.
            // Every span in Phase 2 (5 of them across producer + consumer)
            // follows this exact 8-12 line idiom.
            Span span = tracer.spanBuilder(method + " " + path)
                .setSpanKind(SpanKind.SERVER)
                // 6 HTTP semconv attributes set BEFORE the chain runs
                // (status_code is set AFTER, when the response is filled in).
                // Using io.opentelemetry.semconv constants because string
                // literals would defeat the teaching point about the spec.
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(UrlAttributes.URL_PATH, path)
                .setAttribute(UrlAttributes.URL_SCHEME, request.getScheme())
                .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getServerName())
                .setAttribute(ServerAttributes.SERVER_PORT, (long) request.getServerPort())
                .setAttribute(HttpAttributes.HTTP_ROUTE, path)
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                chain.doFilter(request, response);
                // Set the response status AFTER the chain runs — by now the
                // controller (or an exception handler) has populated it.
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                    (long) response.getStatus());
            } catch (RuntimeException | ServletException | IOException e) {
                // D-03: catch block present even in Phase 2 (no fail path yet).
                // Phase 3's APP-04 deterministic 10% failure exercises this
                // handler from the consumer side; keeping the structural shape
                // identical now means Phase 3 only adds the failure path,
                // not the catch wiring.
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end();
            }
        }
    }
    ```

    Notes for the executor:
    - Imports: `jakarta.servlet.*` (NOT `javax.servlet.*` — Spring Boot 3.x uses Jakarta).
    - Constants used: `HttpAttributes.HTTP_REQUEST_METHOD`, `HttpAttributes.HTTP_RESPONSE_STATUS_CODE`, `HttpAttributes.HTTP_ROUTE`, `UrlAttributes.URL_PATH`, `UrlAttributes.URL_SCHEME`, `ServerAttributes.SERVER_ADDRESS`, `ServerAttributes.SERVER_PORT` — all from the STABLE `io.opentelemetry.semconv.*` package (no -incubating needed for HTTP).
    - The catch clause's union type `RuntimeException | ServletException | IOException` covers the three throwables `chain.doFilter` can produce; we re-throw to preserve Spring's exception handling.
    - `(long)` casts on SERVER_PORT and HTTP_RESPONSE_STATUS_CODE are required because the semconv attribute keys are typed `AttributeKey<Long>`, but `getServerPort()` and `getStatus()` return `int`.
    - 4-space Java indentation matching the rest of the codebase.
    - No `@Component` annotation — the bean is registered by `OtelSdkConfiguration`'s `@Bean HttpServerSpanFilter` factory (T1).

    After this file is written, the producer-service module compiles cleanly (the forward reference from T1 is now closed).
  </action>
  <acceptance_criteria>
    - `test -f producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` exits 0
    - `grep -c '^package com.example.producer.config;' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - `grep -c 'extends OncePerRequestFilter' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - NOT @Component-annotated (registered by OtelSdkConfiguration @Bean factory): `! grep -E '^@Component' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` exits 0
    - Constructor for Tracer injection present: `grep -c 'public HttpServerSpanFilter(Tracer tracer)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - shouldNotFilter override present: `grep -c '@Override' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 2 (shouldNotFilter + doFilterInternal)
    - /actuator/ exclusion present in shouldNotFilter: `grep -c 'startsWith("/actuator/")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - D-01 inline template present: `grep -c '\.setSpanKind(SpanKind.SERVER)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - All 7 HTTP semconv attrs set: `for k in HTTP_REQUEST_METHOD URL_PATH URL_SCHEME SERVER_ADDRESS SERVER_PORT HTTP_ROUTE HTTP_RESPONSE_STATUS_CODE; do grep -q "${k}" producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java || exit 1; done` exits 0
    - try-with-resources Scope present: `grep -c 'try (Scope scope = span.makeCurrent())' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - Catch block records exception (D-03): `grep -c 'span.recordException(e)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - Catch block sets ERROR status (D-03): `grep -c 'span.setStatus(StatusCode.ERROR)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - finally span.end() present: `grep -c 'span.end()' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - Jakarta servlet (Spring Boot 3.x): `grep -c 'jakarta.servlet' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns >= 4 (FilterChain, ServletException, HttpServletRequest, HttpServletResponse)
    - NO javax.servlet imports: `! grep -E 'javax.servlet' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` exits 0
    - Stable semconv import paths (NOT incubating — HTTP semconv is stable in 1.40.0): `grep -c 'import io.opentelemetry.semconv.HttpAttributes;' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1; `grep -c 'import io.opentelemetry.semconv.UrlAttributes;' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1; `grep -c 'import io.opentelemetry.semconv.ServerAttributes;' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns 1
    - producer-service compiles after BOTH T1 + T2 land: `mvn -pl producer-service -q compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl producer-service -q compile &amp;&amp; grep -q 'extends OncePerRequestFilter' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java &amp;&amp; grep -q 'startsWith("/actuator/")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java &amp;&amp; grep -q 'try (Scope scope = span.makeCurrent())' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java &amp;&amp; grep -q 'span.recordException(e)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java &amp;&amp; ! grep -E 'javax.servlet' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java</automated>
  </verify>
  <done>HttpServerSpanFilter.java exists in com.example.producer.config; extends OncePerRequestFilter (NOT @Component); constructor-injects Tracer; overrides shouldNotFilter (returns true for paths starting "/actuator/") and doFilterInternal (wraps chain.doFilter in the D-01 inline template with 7 HTTP semconv attrs and full try/Scope/try/catch/finally idiom); jakarta.servlet imports (not javax); producer-service compiles green via `mvn -pl producer-service compile`.</done>
</task>

<task id="02-02-T3" type="auto">
  <name>Task 3: Smoke-test the SDK boots (start producer, verify no exceptions, no unknown_service:java, /actuator/* excluded from spans)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 184-194 — End-user-verifiable surface: Tempo two-distinct-traces NOT YET reachable; this task only verifies the SDK boots and exposes correct service.name once spans are produced. Plans 02-04 + 02-05 add the actual span sites.)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (line 161 — code_context: producer-service runs on host JVM via mise run dev:producer; pre-wired env vars from mise.toml line 22)
    - mise.toml (lines 97-100 — dev:producer task; line 22 OTEL_EXPORTER_OTLP_ENDPOINT)
    - .planning/phases/01-baseline-scaffold/1-04-SUMMARY.md (Phase 1 producer smoke-test pattern — same nohup/grep idiom)
  </read_first>
  <action>
    Bring infra up, start the producer in the background, verify it starts cleanly with the SDK wired (no exceptions, no startup errors), confirm one HEALTHY request to /actuator/health is NOT instrumented (D-06 exclusion), and that one POST /orders (which has no business-code spans yet — those land in Plan 02-04, but the SERVER span filter covers the request) produces a TRACE in Tempo with the correct service.name="order-producer". Cleanup at the end.

    **Step 1 — Ensure infra is up:**
    `mise run infra:up` (idempotent).

    **Step 2 — Start producer in background:**
    Launch `mise run dev:producer` in the background. Capture PID. Wait for `Started ProducerApplication` (poll up to 90 seconds — first cold start with the new SDK on the classpath may be slightly slower than Phase 1).
    ```
    nohup mise run dev:producer > /tmp/producer-02-02.log 2>&1 &
    PID=$!
    for i in $(seq 1 45); do
      if grep -q "Started ProducerApplication" /tmp/producer-02-02.log; then break; fi
      sleep 2
    done
    grep -q "Started ProducerApplication" /tmp/producer-02-02.log || { tail -50 /tmp/producer-02-02.log; kill $PID 2>/dev/null; exit 1; }
    ```

    **Step 3 — Verify NO startup exceptions related to OTel/SDK:**
    The startup log MUST NOT contain `java.lang.NoSuchMethodError`, `java.lang.NoClassDefFoundError`, `java.lang.IllegalArgumentException` related to `io.opentelemetry`, or any FATAL/ERROR line referencing `opentelemetry`. The `unknown_service:java` literal MUST NOT appear (RESEARCH §A3 — confirms PITFALLS.md #3 mitigated).
    ```
    if grep -E 'NoSuchMethodError|NoClassDefFoundError|IllegalArgumentException.*opentelemetry|unknown_service:java' /tmp/producer-02-02.log; then
      kill $PID 2>/dev/null
      exit 1
    fi
    ```

    **Step 4 — Send one /actuator/health and one POST /orders to generate a SERVER span (only the POST should produce one):**
    ```
    curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health
    # Expect: 200, but NO span (excluded by D-06)
    curl -s -o /tmp/order-resp.json -w "%{http_code}\n" -X POST http://localhost:8080/orders \
      -H 'Content-Type: application/json' \
      -d '{"sku":"WIDGET-1","quantity":3}'
    # Expect: 202, AND ONE SERVER span emitted to Tempo within ~5s (BSP schedule delay)
    ```

    Both responses must succeed (200 and 202 respectively). The POST response body should still be `{"orderId":"<uuid>"}` (Plan 02-04 hasn't touched the controller yet, so the Phase 1 contract is preserved).

    **Step 5 — Wait for BSP flush (5s default) and query Tempo for the producer's traces:**

    Tempo is exposed by grafana/otel-lgtm at `http://localhost:3000`. The Tempo HTTP API is also exposed (lgtm runs the Collector + Tempo combo); the simplest endpoint is the Search API at `:3000/api/datasources/proxy/uid/tempo/api/search?tags=service.name%3Dorder-producer` but the path can vary by lgtm version. Use the more universal `:3200/api/search` if Tempo's HTTP port is bound (lgtm's Tempo runs on host port `:3200` by default for the lgtm distribution — the README's grafana/docker-otel-lgtm doc confirms this).

    Wait then query:
    ```
    sleep 7  # BSP schedule delay = 5s; allow margin
    # Try Tempo HTTP API: lgtm exposes Tempo's /api/search on :3200
    SEARCH_RESULT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=10")
    echo "$SEARCH_RESULT" | python3 -c "import json,sys; d=json.load(sys.stdin); traces=d.get('traces',[]); assert len(traces) >= 1, 'no traces for service.name=order-producer'; print(f'Found {len(traces)} traces')" || {
      # Fallback: hit Tempo through Grafana datasource proxy (admin/admin in workshop default)
      curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/search?tags=service.name%3Dorder-producer&limit=10" | python3 -c "import json,sys; d=json.load(sys.stdin); traces=d.get('traces',[]); assert len(traces) >= 1, 'no traces for service.name=order-producer (via grafana proxy either)'; print(f'Found {len(traces)} traces')"
    }
    ```

    EITHER endpoint succeeding is sufficient. The key assertion: at least one trace exists with `service.name=order-producer`. The actual span body (POST /orders) is verified by inspecting one trace; not strictly required at this stage (Plan 02-06 does the workshop-attendee Tempo walk).

    **Step 6 — Verify /actuator/health did NOT produce a trace:**
    Sanity check the D-06 exclusion: query Tempo for any trace whose root span name contains "actuator". The exclusion means even though the request happened, no SERVER span was created by our filter. (Spring Boot does NOT auto-instrument MVC, and our filter is the only span source on the producer in Phase 2 so far.)
    ```
    ACTUATOR_TRACES=$(curl -s "http://localhost:3200/api/search?q=%7Bname%3D~%22.*actuator.*%22%7D&limit=10" 2>/dev/null || echo '{"traces":[]}')
    echo "$ACTUATOR_TRACES" | python3 -c "import json,sys; d=json.load(sys.stdin); traces=d.get('traces',[]); assert len(traces) == 0, f'Expected 0 actuator traces, got {len(traces)} — D-06 exclusion broken'"
    ```

    **Step 7 — Cleanup:**
    `kill $PID 2>/dev/null`. Confirm process exits within 12s (graceful shutdown via destroyMethod="close" — TRACE-04 + RESEARCH §A7's 10s join). Optionally leave infra up for the next plan.

    Failure modes for the executor:
    - "Started ProducerApplication" never appears → check /tmp/producer-02-02.log; common cause is Tomcat port collision (preflight should have caught this; rerun preflight).
    - `IllegalArgumentException: endpoint must start with http://` → DEFAULT_OTLP_ENDPOINT typo in T1, or env var is set to a value missing the http:// prefix (mise.toml line 22 sets it correctly; check `mise env`).
    - `service.name=unknown_service:java` somewhere in the trace → Resource.getDefault().merge(...) called with arguments swapped (the merge order matters); re-read RESEARCH §A3.
    - Tempo search returns zero traces but everything else is green → BSP hasn't flushed yet; bump the sleep to 12s. If still zero, check that the OTLP exporter isn't logging connection refused (lgtm container down).
  </action>
  <acceptance_criteria>
    - Background `mise run dev:producer` reaches `Started ProducerApplication` within 90 seconds (extracted from /tmp/producer-02-02.log)
    - No OTel-related startup exceptions: `! grep -E 'NoSuchMethodError|NoClassDefFoundError|IllegalArgumentException.*opentelemetry|unknown_service:java' /tmp/producer-02-02.log` exits 0
    - GET /actuator/health returns 200: `test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health)" = "200"`
    - POST /orders returns 202: `test "$(curl -s -o /tmp/order-resp.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}')" = "202"`
    - Producer trace is visible in Tempo within 12s with service.name=order-producer (try Tempo direct then Grafana proxy fallback): the python3 trace-count assertion in Step 5 exits 0
    - /actuator/health was NOT instrumented (D-06 verified): the actuator-traces python3 assertion in Step 6 exits 0
    - Producer process shuts down cleanly within 12s of SIGTERM (TRACE-04 graceful flush): after `kill $PID`, `for i in $(seq 1 12); do kill -0 $PID 2>/dev/null || break; sleep 1; done; ! kill -0 $PID 2>/dev/null` exits 0
    - No leftover spring-boot:run process: `! pgrep -f spring-boot:run` exits 0
  </acceptance_criteria>
  <verify>
    <automated>nohup mise run dev:producer &gt; /tmp/producer-02-02.log 2&gt;&amp;1 &amp; PID=$!; for i in $(seq 1 45); do grep -q "Started ProducerApplication" /tmp/producer-02-02.log &amp;&amp; break; sleep 2; done; grep -q "Started ProducerApplication" /tmp/producer-02-02.log &amp;&amp; ! grep -E 'NoSuchMethodError|NoClassDefFoundError|unknown_service:java' /tmp/producer-02-02.log &amp;&amp; test "$(curl -s -o /tmp/order-resp.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}')" = "202"; CODE=$?; kill $PID 2&gt;/dev/null; for i in $(seq 1 12); do kill -0 $PID 2&gt;/dev/null || break; sleep 1; done; exit $CODE</automated>
  </verify>
  <done>Producer-service starts cleanly with the manual SDK on the classpath; no NoSuchMethodError / NoClassDefFoundError / unknown_service:java; POST /orders returns 202 and produces a SERVER span visible in Tempo with service.name=order-producer within 12s; GET /actuator/health works (200) but produces NO span (D-06 exclusion verified); process shuts down cleanly within 12s of SIGTERM (TRACE-04 graceful flush via destroyMethod="close").</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 02-02 — producer-service SDK config)

| Boundary | Description |
|----------|-------------|
| External HTTP client → producer (existing from Phase 1) | Untrusted JSON crosses POST /orders; SERVER span filter sees every non-/actuator request |
| Producer JVM → OTLP gRPC endpoint (NEW in this plan) | Spans flow over a gRPC channel to OTEL_EXPORTER_OTLP_ENDPOINT (workshop default: http://localhost:4317) |
| mise.toml [env] → producer JVM | OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_EXPORTER_OTLP_PROTOCOL, SPRING_RABBITMQ_*, PRODUCER_PORT enter via process env |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-02-01 | Information Disclosure | OTEL_EXPORTER_OTLP_ENDPOINT env var accepts any URL — a misconfigured workshop attendee could ship spans to a public collector | mitigate | DEFAULT_OTLP_ENDPOINT hardcoded to http://localhost:4317 in OtelSdkConfiguration; fallback prevents accidental "spans go nowhere" but ALSO prevents accidental "spans go elsewhere" by default. Workshop README will document that overriding the env var ships spans to whatever the override points at — that's the attendee's deliberate choice. (Phase-level threat T1.) |
| T-2-02-02 | Information Disclosure | Span attributes leaking sensitive payload data | accept | Phase 2 SERVER span sets ONLY method/path/scheme/server.address/server.port/route/status — no body, no headers, no user-agent, no query string. The payload Map contents (which a future caller might fill with PII) are NOT captured. (Phase-level threat T2 mitigated.) |
| T-2-02-03 | Information Disclosure / DoS | HttpServerSpanFilter sees every NON-actuator request including paths Spring MVC has no handler for (404s) | accept | The filter wraps the chain, so 404s also produce SERVER spans with status_code=404 — desirable observability. Workshop volume is laptop-class; no DoS surface. |
| T-2-02-04 | Information Disclosure | /actuator/* paths bypassing the SERVER span produce no telemetry; an attacker probing /actuator/health for fingerprint info leaves no trace in Tempo | accept | The exclusion is a pedagogical tradeoff (kill the docker-compose health-check noise). Spring Boot Actuator's whitelist (`management.endpoints.web.exposure.include=health` in application.yaml from Phase 1) already restricts what /actuator/* exposes. (Phase-level threat T3 verified.) |
| T-2-02-05 | Tampering | RuntimeException thrown by the chain may leak through finally without being recorded | mitigate | Catch clause uses union type `RuntimeException \| ServletException \| IOException` covering all three throwables doFilter can produce; each is recordException()'d and span.setStatus(ERROR) is called before re-throw |
| T-2-02-06 | Tampering | UUID.randomUUID() for service.instance.id uses java.util.UUID's SecureRandom under the hood — sufficient for distinguishing JVM instances | accept | UUID v4 with SecureRandom seed is fine for an instance ID; no security boundary depends on its unpredictability |
| T-2-02-07 | DoS | OTLP exporter blocking on a slow/unreachable backend during shutdown could delay Spring's destroy lifecycle | mitigate | OpenTelemetrySdk.close() calls shutdown().join(10s) per RESEARCH §A7 — bounded wait, can't hang Spring forever; BSP exporter timeout (30s default) sits inside that window |

**Plan scope:** Producer-service SDK bootstrap + HTTP filter. Network surface introduced: outbound gRPC to localhost:4317. No new inbound surface (the existing /orders + /actuator/health from Phase 1 is unchanged in shape — only spans are added around it). Out of scope: TLS to the OTLP backend (workshop runs on loopback), authentication on the OTLP endpoint (lgtm doesn't require any), span attribute redaction (Phase 2 spans capture no PII).
</threat_model>

<verification>
- `mvn -pl producer-service compile` exits 0 after both T1 + T2 land
- Background `mise run dev:producer` reaches `Started ProducerApplication` within 90s with no OTel-related exceptions
- POST /orders returns 202; SERVER span with service.name=order-producer visible in Tempo within 12s of the POST
- GET /actuator/health returns 200 but produces NO span (D-06 exclusion)
- Process shuts down cleanly within 12s of SIGTERM (TRACE-04: destroyMethod=close → shutdown().join(10s) → BSP flush)
- `mise run verify:bom` still exits 0 (Plan 02-01's invariant preserved across the new code)
</verification>

<success_criteria>
- TRACE-01 (per-service SDK config) satisfied for producer.
- TRACE-02 satisfied: Resource carries service.name=order-producer + namespace + instance.id + deployment.environment.name via the correct semconv 1.40.0 constants (stable + incubating).
- TRACE-03 satisfied: SdkTracerProvider with BatchSpanProcessor + OtlpGrpcSpanExporter targeting the env-var endpoint with localhost:4317 fallback; Sampler.parentBased(Sampler.alwaysOn()) explicit + multi-paragraph teaching comment.
- TRACE-04 satisfied: @Bean(destroyMethod="close") confirmed by clean shutdown within 12s of SIGTERM.
- TRACE-05 satisfied: HttpServerSpanFilter creates SERVER span on POST /orders with all 7 HTTP semconv attrs (method/path/scheme/server.address/server.port/route + status_code post-chain).
- DOC-03 satisfied (producer half): OtelSdkConfiguration.java has >= 40 comment lines covering sampler tradeoff (D-14, multi-paragraph), env-var contract, per-service-duplication rationale, semconv-incubating-mandatory note, BSP production-shape rationale, propagators-wired-but-not-yet-exercised note.
- D-12 enforced: no opentelemetry-sdk-extension-autoconfigure dependency or AutoConfiguredOpenTelemetrySdk usage.
- D-16 enforced: composite W3C trace-context + W3C baggage propagators registered NOW for Phase 3 reuse.
- Phase 2 invariant preserved: `mise run verify:bom` still exits 0.
</success_criteria>

<output>
After completion, create `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-02-SUMMARY.md` documenting:
- File tree of producer-service/src/main/java/com/example/producer/config/ (RabbitConfig.java + OtelSdkConfiguration.java + HttpServerSpanFilter.java = 3 files)
- Confirmed `mvn -pl producer-service compile` BUILD SUCCESS line
- Confirmed startup line (`Started ProducerApplication in X seconds`) — paste from /tmp/producer-02-02.log
- Confirmed Tempo trace count for service.name=order-producer (paste python3 output: `Found N traces`)
- Confirmed actuator traces == 0 (D-06 verified)
- Confirmed graceful shutdown latency (paste the seconds-to-exit measurement)
- Comment-line count from `grep -cE '^\s*(//|\*|/\*\*|\*/)' OtelSdkConfiguration.java` (should be >= 40)
- Files created: 2 (OtelSdkConfiguration.java + HttpServerSpanFilter.java)
- Hand-off for Plan 02-04: Tracer bean is now constructor-injectable; OrderService + OrderPublisher can pull it from Spring's DI container. SERVER span is in place; INTERNAL + PRODUCER spans land in 02-04.
</output>
