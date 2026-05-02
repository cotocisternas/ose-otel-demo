# Phase 4: Metrics — Pattern Map

**Mapped:** 2026-05-01
**Files analyzed:** 7 (5 modify + 1 conditionally-create + 1 docs/build-config)
**Analogs found:** 7 / 7 (every Phase 4 file has a strong in-repo analog — most are the file itself at HEAD `step-03-context-propagation`)

---

## Orientation for the Planner

**The pedagogical north star (D-01 + D-02):** the diff between tag `step-03-context-propagation` and tag `step-04-metrics` should read as **"we added a sibling pipeline next to the trace pipeline"** — NOT as "we shoved more stuff inside one method." Phase 4 plans should produce that diff shape.

The richest analog set lives in the **Phase 4 target files themselves** at HEAD. Phase 2 baked in patterns the Phase 4 helper extraction must *preserve*:

1. `private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";` (constant, kept)
2. `Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse(DEFAULT_OTLP_ENDPOINT)` (env-var pattern, reused for `OtlpGrpcMetricExporter`)
3. `Resource.getDefault().merge(Resource.create(Attributes.builder()...))` (Resource construction, stays in @Bean orchestrator and is now passed to BOTH helpers per D-05)
4. `@Bean(destroyMethod = "close")` on `openTelemetry()` (lifecycle cascade, D-07 — no new lifecycle bean)
5. `@Bean Tracer tracer(OpenTelemetry o) { return o.getTracer("com.example.<svc>"); }` (the parallel `Meter` @Bean in D-06 mirrors this verbatim)
6. The 5-section comment-block style (`----- Resource -----`, `----- OTLP gRPC exporter -----`, etc.) — Phase 4's `buildMeterProvider` helper must follow the same banner-comment style for D-20 comment-density compliance.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | Spring config (SDK bootstrap) | startup-wiring | itself at HEAD (`step-03-context-propagation`) | **self-extension** (refactor target) |
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | Spring config (SDK bootstrap) | startup-wiring | itself at HEAD; mirror of producer's | **self-extension** (refactor target, mirror) |
| `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` | Spring filter (instrumentation surface) | request-response | itself at HEAD (existing `try { chain.doFilter } finally { span.end() }`) | **self-extension** (Histogram record line slots into existing finally) |
| `producer-service/src/main/java/com/example/producer/domain/OrderService.java` | Domain service (call-site instrumentation) | request-response → fan-out publish | itself at HEAD (existing INTERNAL span body, Phase 3 catch shape unchanged) | **self-extension** (Counter `.add(1, attrs)` slots between `publisher.publish` and `return`) |
| `consumer-service/.../observability/QueueDepthGauge.java` *(D-18b option)* | Spring `@Component` (instrument registration) | callback-on-collection-interval | `consumer-service/.../config/OtelSdkConfiguration.java` `Tracer` @Bean wiring (constructor-style DI) | role-match (no existing observability `@Component` analog) |
| `mise.toml` | Build/task config | n/a (declarative tasks) | `[tasks."demo:order"]` task block at HEAD (lines 118-120) | **self-extension** (add second invocation OR loop two payloads) |
| `README.md` | Workshop docs | n/a (markdown) | "Workshop checkpoints" + "Reading the code" sections at HEAD (lines 68-92) | role-match (existing prose voice, link conventions, DOC-05 callout style) |

**Match-quality legend:** *self-extension* = the analog **is the target file** at HEAD; the planner's job is to extend it without disturbing existing pedagogy. *role-match* = no exact analog; closest in-repo file with the same role/wiring shape.

---

## Pattern Assignments

### `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (Spring config, startup-wiring)

**Analog:** the file itself at HEAD `step-03-context-propagation`. The D-01 refactor extracts the existing inline tracer pipeline into `private SdkTracerProvider buildTracerProvider(Resource)` and adds a sibling `private SdkMeterProvider buildMeterProvider(Resource)`. **Every line in the existing @Bean body must end up in either the @Bean orchestrator OR one of the helpers — no Phase 2 lines get deleted by the refactor.**

**Imports pattern to extend** (lines 1-24 — Phase 4 ADDS imports for: `io.opentelemetry.api.metrics.Meter`, `io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter`, `io.opentelemetry.sdk.metrics.SdkMeterProvider`, `io.opentelemetry.sdk.metrics.export.PeriodicMetricReader`, `java.time.Duration`):

```java
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
```

**Reusable constant — DO NOT redeclare** (lines 65, in current file):

```java
private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";
```

This same constant is used by both `OtlpGrpcSpanExporter` (existing) and the new `OtlpGrpcMetricExporter` (Phase 4) per D-04. **Do not introduce `DEFAULT_OTLP_METRIC_ENDPOINT` or any second constant — endpoint is shared.**

**Env-var read pattern to mirror** (lines 113-115, in current `openTelemetry()` body — this exact 2-line idiom is reused inside `buildMeterProvider`):

```java
String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
    .orElse(DEFAULT_OTLP_ENDPOINT);
```

**Resource construction — moves to @Bean orchestrator, passed to helpers** (lines 95-101 today, stays here per D-05):

```java
Resource resource = Resource.getDefault().merge(
    Resource.create(Attributes.builder()
        .put(ServiceAttributes.SERVICE_NAME, "order-producer")
        .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
        .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
        .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop")
        .build()));
```

**@Bean orchestrator shape after D-01 refactor** (this is the *target* shape — the @Bean body shrinks to ~15 lines and reads as a 3-step recipe):

```java
@Bean(destroyMethod = "close")
OpenTelemetry openTelemetry() {
    Resource resource = Resource.getDefault().merge(
        Resource.create(Attributes.builder()
            .put(ServiceAttributes.SERVICE_NAME, "order-producer")
            .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
            .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
            .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop")
            .build()));

    SdkTracerProvider tracerProvider = buildTracerProvider(resource);
    SdkMeterProvider  meterProvider  = buildMeterProvider(resource);

    ContextPropagators propagators = ContextPropagators.create(
        TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()));

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)         // <-- single new builder line (D-01)
        .setPropagators(propagators)
        .build();
}
```

**`buildTracerProvider(Resource)` — the lift-and-shift target** (the body of this helper is verbatim the current lines 102-158: OTLP exporter → BatchSpanProcessor → Sampler → SdkTracerProvider.builder, with all comments preserved). The signature and shape:

```java
/** Phase 2's tracer pipeline, extracted unchanged in Phase 4 (D-01). */
private SdkTracerProvider buildTracerProvider(Resource resource) {
    // [verbatim lines 103-158 of the current file move here, comments and all]
    String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
        .orElse(DEFAULT_OTLP_ENDPOINT);
    SpanExporter spanExporter = OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build();
    BatchSpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter).build();
    Sampler sampler = Sampler.parentBased(Sampler.alwaysOn());
    return SdkTracerProvider.builder()
        .setResource(resource)
        .setSampler(sampler)
        .addSpanProcessor(spanProcessor)
        .build();
}
```

**`buildMeterProvider(Resource)` — the new sibling helper** (the meter pipeline; this is what Phase 4 adds — comment-density bar of Phase 2 carries forward per D-20). Shape derived from the trace pipeline as a "compare side-by-side" reading:

```java
/**
 * Meter pipeline added in Phase 4 — sibling to {@link #buildTracerProvider}.
 *
 * Three new SDK touch points (read top-to-bottom):
 *   1. OtlpGrpcMetricExporter.builder().setEndpoint(...) — same artifact
 *      and same env-var fallback as the trace exporter (D-04).
 *   2. PeriodicMetricReader.builder(exporter).setInterval(Duration.ofSeconds(10)).build()
 *      — overrides OTel's 60-second default to keep the workshop's
 *      ~15-second feedback loop tight (METRIC-01 / D-03). Production
 *      apps would typically leave this at 60s.
 *   3. SdkMeterProvider.builder().setResource(resource).registerMetricReader(reader).build()
 *      — same Resource as the tracer pipeline (D-05) so metrics and
 *      traces share identity in Mimir/Tempo for cross-signal correlation.
 *
 * Default View / Aggregation: SDK defaults — no custom histogram bucket
 * tuning here (D-15). Bucket configuration is a real-world concern
 * outside the SDK lesson.
 */
private SdkMeterProvider buildMeterProvider(Resource resource) {
    String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
        .orElse(DEFAULT_OTLP_ENDPOINT);
    OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
        .setEndpoint(endpoint)
        .build();
    PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
        .setInterval(Duration.ofSeconds(10))   // METRIC-01 / D-03 — workshop value, not production
        .build();
    return SdkMeterProvider.builder()
        .setResource(resource)
        .registerMetricReader(metricReader)
        .build();
}
```

**`Meter` @Bean — mirror of existing `Tracer` @Bean** (lines 193-196 of current file are the analog; the new bean sits as the line-for-line sibling):

```java
// Existing Tracer @Bean (analog to copy):
@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");
}

// New Meter @Bean (D-06) — sibling shape, scope name parallel:
@Bean
Meter meter(OpenTelemetry openTelemetry) {
    return openTelemetry.getMeter("com.example.producer");
}
```

**`HttpServerSpanFilter` factory — gains a Meter parameter** (lines 210-213 current file):

```java
// Current:
@Bean
HttpServerSpanFilter httpServerSpanFilter(Tracer tracer) {
    return new HttpServerSpanFilter(tracer);
}

// Phase 4 (D-12 — filter constructor takes Tracer + Meter):
@Bean
HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter) {
    return new HttpServerSpanFilter(tracer, meter);
}
```

**Lifecycle pattern — DO NOT touch** (D-07 carryforward): the `@Bean(destroyMethod = "close")` on `openTelemetry()` already cascades shutdown to `SdkMeterProvider.shutdown().join(10s)`. Adding `SdkMeterProvider` requires **zero** new lifecycle annotations.

---

### `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` (Spring config, startup-wiring)

**Analog:** the file itself at HEAD; the D-02 mirror of the producer's refactor. Two-line diff against producer's Phase 4 result preserved per Phase 2 D-01:

| Line | Producer | Consumer |
|---|---|---|
| Resource builder | `.put(ServiceAttributes.SERVICE_NAME, "order-producer")` | `.put(ServiceAttributes.SERVICE_NAME, "order-consumer")` |
| `Tracer` scope | `openTelemetry.getTracer("com.example.producer")` | `openTelemetry.getTracer("com.example.consumer")` |
| `Meter` scope (NEW per D-06) | `openTelemetry.getMeter("com.example.producer")` | `openTelemetry.getMeter("com.example.consumer")` |
| `HttpServerSpanFilter` @Bean | present (lines 210-213) | **absent** — D-07 carryforward |

**The mirror constraint** (Phase 2 DOC-05 / 02-CONTEXT D-01): Phase 4 must NOT introduce ANY producer/consumer asymmetry beyond the three lines above + the consumer-only D-18 gauge registration. **No shared `otel-bootstrap` extraction.** The "Why duplicated?" callout in `OtelSdkConfiguration.java`'s class JavaDoc (lines 33-39 producer / 33-39 consumer) and in README.md (lines 90-92) already anticipate Phase 4 — no JavaDoc updates needed unless the planner wants to add a "Phase 4 added the meter pipeline" note (optional).

**The consumer file's *additional* JavaDoc paragraph** (lines 55-60 today, "Why no HttpServerSpanFilter here?") is preserved unchanged — the bean reason is unaffected by Phase 4.

---

### `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` (Spring filter, request-response)

**Analog:** the file itself at HEAD. The D-12 extension adds a `DoubleHistogram` field + one `record(...)` line in the existing `finally` block. **No new filter, no second `OncePerRequestFilter`, no chain ordering concern.**

**Field & constructor — current shape** (lines 47-53):

```java
public class HttpServerSpanFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public HttpServerSpanFilter(Tracer tracer) {
        this.tracer = tracer;
    }
```

**Field & constructor — Phase 4 extension shape** (D-12 — Meter is constructor-injected, histogram built once and reused):

```java
public class HttpServerSpanFilter extends OncePerRequestFilter {

    private final Tracer tracer;
    private final DoubleHistogram requestDuration;

    public HttpServerSpanFilter(Tracer tracer, Meter meter) {
        this.tracer = tracer;
        // Histogram name + unit are locked by OTel HTTP semconv 1.40.0:
        // https://opentelemetry.io/docs/specs/semconv/http/http-metrics/
        // Unit "s" (seconds), NOT "ms" (D-13) — common porting mistake.
        this.requestDuration = meter.histogramBuilder("http.server.request.duration")
            .setUnit("s")
            .setDescription("Duration of HTTP server requests, semconv 1.40.0")
            .build();
    }
```

**`shouldNotFilter` — DO NOT touch** (lines 64-67 — the `/actuator/*` exclusion automatically keeps health-check noise out of the histogram, no second predicate needed):

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");
}
```

**Existing `try { chain.doFilter } catch { recordException } finally { span.end() }` — extension target** (lines 91-122 today). The Histogram record line slots into the finally **before** `span.end()`, with timing captured *outside* the span builder so the histogram measures total request time including span start/end overhead:

```java
// Phase 4 D-13 shape — start clock BEFORE span build, record seconds in finally:
long startNanos = System.nanoTime();
String method = request.getMethod();
String path   = request.getRequestURI();

Span span = tracer.spanBuilder(method + " " + path)
    .setSpanKind(SpanKind.SERVER)
    .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
    .setAttribute(UrlAttributes.URL_PATH, path)
    .setAttribute(UrlAttributes.URL_SCHEME, request.getScheme())
    .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getServerName())
    .setAttribute(ServerAttributes.SERVER_PORT, (long) request.getServerPort())
    .setAttribute(HttpAttributes.HTTP_ROUTE, path)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        (long) response.getStatus());
} catch (RuntimeException | ServletException | IOException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    // Phase 4 D-13: convert nanos to seconds (semconv-aligned unit).
    // Common pitfall: recording milliseconds here breaks the stable
    // semconv contract — Mimir would still graph values but they'd
    // be 1000x off when correlated with other OTel-instrumented apps.
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    requestDuration.record(seconds, Attributes.of(
        HttpAttributes.HTTP_REQUEST_METHOD, method,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus()));
    span.end();
}
```

**Attribute key constraint (D-14):** use `io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD` and `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` — both already imported at line 10 (`import io.opentelemetry.semconv.HttpAttributes;`). **Do NOT add `url.path` / `URL_PATH` to the histogram** — high-cardinality path values would explode the metric series count. The path stays on the SERVER span only.

**Imports pattern — Phase 4 ADDS** (existing line 10 `import io.opentelemetry.semconv.HttpAttributes;` is already present; new imports are `io.opentelemetry.api.common.Attributes`, `io.opentelemetry.api.metrics.DoubleHistogram`, `io.opentelemetry.api.metrics.Meter`):

```java
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
```

**Class JavaDoc update** (lines 21-46 today say "Wraps every non-/actuator HTTP request in a SERVER span with HTTP semantic-convention attributes (TRACE-05)"). Phase 4 expands this to "SERVER span + HTTP duration histogram (TRACE-05 + METRIC-03)" per D-12.

---

### `producer-service/src/main/java/com/example/producer/domain/OrderService.java` (domain service, request-response → publish)

**Analog:** the file itself at HEAD (Phase 2 INTERNAL span body + Phase 3 left this file's catch shape unchanged). The D-08 extension adds **one** `LongCounter.add(1, attrs)` line between `publisher.publish(...)` and `return orderId;`. **Critical:** the Counter call sits inside the existing `try { ... }` block but BEFORE the `return`, so it ONLY fires on success — the `catch (RuntimeException)` handler stays exactly as-is and does NOT fire the counter (D-08 rationale: METRIC-02 is `orders.created`, not `orders.attempted`).

**Field & constructor — current shape** (lines 25-31):

```java
public class OrderService {
    private final OrderPublisher publisher;
    private final Tracer tracer;

    public OrderService(OrderPublisher publisher, Tracer tracer) {
        this.publisher = publisher;
        this.tracer = tracer;
    }
```

**Field & constructor — Phase 4 extension shape** (D-08 — Meter is constructor-injected; counter built once and reused):

```java
public class OrderService {
    private final OrderPublisher publisher;
    private final Tracer tracer;
    private final LongCounter ordersCreated;

    public OrderService(OrderPublisher publisher, Tracer tracer, Meter meter) {
        this.publisher = publisher;
        this.tracer = tracer;
        // Counter name "orders.created" — METRIC-02 locked. The OTel
        // Prometheus exporter mangles dots to underscores and appends
        // "_total", so this surfaces in Mimir as `orders_created_total`.
        // README's Step 4 callout (D-21) names this mapping for attendees.
        this.ordersCreated = meter.counterBuilder("orders.created")
            .setDescription("Successful POST /orders → publish completions")
            .setUnit("1")
            .build();
    }
```

**Counter call-site — D-08 / D-09 verbatim shape** (lives between current line 50 `publisher.publish(orderId, payload);` and current line 51 `return orderId;` — INSIDE the `try (Scope scope = span.makeCurrent())` block per D-08):

```java
try (Scope scope = span.makeCurrent()) {
    String orderId = UUID.randomUUID().toString();
    publisher.publish(orderId, payload);
    // ---- Phase 4 D-08 / D-09: counter fires AFTER publish returns ----
    //
    // Inside the existing INTERNAL span scope so the trace and the metric
    // increment are emitted from adjacent SDK calls. Failure path (catch
    // below) does NOT fire — METRIC-02 is `orders.created`, not
    // `orders.attempted`. Failures are visible via the trace's ERROR
    // status, not as a metric.
    //
    // `order.priority` is NOT in the OTel semconv catalog as a stable key
    // — string literal is correct here (contrast with the histogram's
    // HttpAttributes.HTTP_REQUEST_METHOD which IS semconv-stable).
    String priority = String.valueOf(
        Optional.ofNullable(payload.get("priority")).orElse("standard"));
    ordersCreated.add(1, Attributes.of(
        AttributeKey.stringKey("order.priority"), priority));
    return orderId;
} catch (RuntimeException e) {
    // D-03 catch — UNCHANGED. Counter does NOT fire on this path.
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();
}
```

**Imports pattern — Phase 4 ADDS** (`java.util.Optional`, `io.opentelemetry.api.common.AttributeKey`, `io.opentelemetry.api.common.Attributes`, `io.opentelemetry.api.metrics.LongCounter`, `io.opentelemetry.api.metrics.Meter`):

```java
import java.util.Optional;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
```

**JavaDoc update** (lines 16-22 today end with "do NOT extract this into a helper"). Add a Phase 4 paragraph documenting the Counter call-site (parallel to how OrderPublisher and ProcessingService got their Phase 3 JavaDoc updates — see `OrderPublisher.java` lines 22-30 for the prose voice).

---

### `consumer-service/.../observability/QueueDepthGauge.java` *(D-18b option — planner picks D-18a vs D-18b)* (Spring `@Component`, callback-on-collection-interval)

**Two acceptable shapes per D-18 — planner picks one and writes the plan to that shape.**

#### Option (a) — Inline `@PostConstruct` in `OtelSdkConfiguration.java`

**Analog:** the existing `Tracer` / `Meter` @Bean wiring in `consumer-service/.../config/OtelSdkConfiguration.java` (lines 199-202). The `@PostConstruct` lives as a method on the `@Configuration` class itself, alongside the @Bean factories. Closest analog for the *style* is Phase 3's `RabbitConfig.rabbitListenerContainerFactory(...)` (lines 83-97 of `consumer-service/.../config/RabbitConfig.java`) — both are "register one extra side-effect at startup, in the @Configuration class."

```java
// Add as a sibling method to the existing @Bean Tracer / @Bean Meter on the
// @Configuration class. Inject the Meter bean as a method parameter is NOT
// possible for @PostConstruct — instead, store Meter as a field via setter
// or move the registration into a dedicated lifecycle bean. Cleanest shape:
// constructor-take Meter via a small helper method called from a @Bean factory.
//
// (If this option proves clunky, switch to D-18b.)
```

**Caveat:** `@PostConstruct` cannot have parameters in Spring, so the @Configuration class would need a `Meter` field set by a setter or via `@Autowired` — slightly clunkier than option (b). Both shapes are acceptable per D-18; planner judgment.

#### Option (b) — New `@Component QueueDepthGauge` class

**Analog:** `consumer-service/.../config/OtelSdkConfiguration.java` lines 199-202 for the `Meter` bean + the existing `@Component` style of `consumer-service/.../messaging/OrderListener.java` lines 39-46 (constructor injection of a single dependency, no other state):

```java
// OrderListener (analog for @Component constructor-injection style):
@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;

    public OrderListener(ProcessingService processingService) {
        this.processingService = processingService;
    }
    // ...
}
```

**`QueueDepthGauge.java` — D-18b shape** (new file at `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`):

```java
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
 * <p><b>Why a separate @Component (D-18b)?</b> The gauge registration is
 * one-shot side-effect boilerplate, not part of the SDK pipeline build.
 * Hosting it here keeps {@code OtelSdkConfiguration.java} symmetrical
 * with the producer's file (no consumer-only @PostConstruct that
 * doesn't exist on the producer side).
 *
 * <p><b>Why ofLongs() (D-19)?</b> The synthetic value is an int and
 * the gauge name carries {@code .estimate} (whole-number connotation);
 * integer queue depths are the conventional shape for messaging gauges.
 *
 * <p><b>Why ThreadLocalRandom (D-17)?</b> The callback fires on the
 * {@link io.opentelemetry.sdk.metrics.export.PeriodicMetricReader}
 * worker thread once per 10-second interval (D-03). ThreadLocalRandom
 * is thread-safe, has no shared state, and creates no coupling to
 * Phase 3's APP-04 AtomicInteger. Real queue-depth would require
 * polling the RabbitMQ Management API at :15672/api/queues/... —
 * out of scope for the SDK lesson (METRIC-04 is "synthetic").
 *
 * <p><b>Callback discipline.</b> The callback should be cheap and
 * side-effect-free. The PeriodicMetricReader invokes it on every
 * collection cycle whether or not anyone is querying the value.
 */
@Component
public class QueueDepthGauge {

    private final ObservableLongGauge gauge;

    public QueueDepthGauge(Meter meter) {
        this.gauge = meter.gaugeBuilder("orders.queue.depth.estimate")
            .setDescription("Synthetic queue-depth estimate (workshop demo, not a real measurement)")
            .setUnit("{messages}")
            .ofLongs()                           // D-19
            .buildWithCallback(measurement ->
                measurement.record(ThreadLocalRandom.current().nextInt(0, 50)));
    }

    /**
     * Closes the gauge so the SDK stops invoking the callback at shutdown.
     * Strictly speaking the SdkMeterProvider's destroyMethod="close" cascade
     * (Phase 2 D-15 / Phase 4 D-07) handles this transitively, but explicit
     * close gives a single, readable cleanup point.
     */
    @PreDestroy
    public void close() {
        gauge.close();
    }
}
```

**Recommendation surfaced to the planner:** D-18b (the new `@Component`) is the cleaner shape because (i) the producer's `OtelSdkConfiguration.java` has no equivalent registration, so adding a @PostConstruct only on the consumer side breaks the mirror property the duplication-pedagogy depends on; (ii) the @PostConstruct option requires a Meter field on the @Configuration class set via setter/@Autowired (no parameter injection on @PostConstruct), which is less idiomatic than constructor-injection in a @Component. **The planner is free to pick D-18a** if they prefer single-file simplicity — both satisfy the spec.

---

### `mise.toml` (build/task config, declarative tasks)

**Analog:** the existing `[tasks."demo:order"]` block at lines 118-120:

```toml
[tasks."demo:order"]
description = "POST a sample order to the producer; expect 202"
run = "curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders -H 'Content-Type: application/json' -d '{\"sku\":\"WIDGET-1\",\"quantity\":3}' && echo"
```

**Phase 4 D-10 shape** (must exercise BOTH `priority` values so Mimir shows two series — single-payload demo would make the cardinality lesson invisible):

```toml
[tasks."demo:order"]
description = "POST a sample order to the producer; expect 202. Phase 4: alternate priorities to teach attribute breakdowns."
run = """
set -e
curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders \\
  -H 'Content-Type: application/json' \\
  -d '{"sku":"WIDGET-1","quantity":3,"priority":"express"}' && echo
curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders \\
  -H 'Content-Type: application/json' \\
  -d '{"sku":"WIDGET-2","quantity":1,"priority":"standard"}' && echo
"""
```

**Style match (existing tasks):**
- `[tasks."verify:bom"]` (lines 122-165) is the in-repo analog for **multi-line `run = """ ... """` blocks** (`verify:bom` runs a multi-line bash script). The `demo:order` extension follows the same `set -e` discipline and triple-quoted string shape.
- The `description` field is a one-liner suffixed with the Phase rationale where relevant (see `verify:bom`'s description that names "Phase 2 invariant" for the same kind of context-tagging).

**The third payload option** (CONTEXT D-09 mentions "or omit the field to exercise the fallback"): if the planner prefers a 3-curl variant to also exercise the `"standard"` fallback path explicitly, that's also valid:

```toml
# alternative — three payloads, third one omits priority entirely:
curl ... -d '{"sku":"WIDGET-3","quantity":2}' && echo   # exercises the .orElse("standard") fallback
```

---

### `README.md` (workshop docs)

**Analog:** existing "Workshop checkpoints" section (lines 68-77) + "Reading the code" section (lines 79-88) + "Why is OtelSdkConfiguration.java duplicated?" callout (lines 90-92) + "Why is the propagation pair shared?" callout (lines 94-106).

**D-21 shape — small Phase-4-specific section keyed to tag `step-04-metrics`.** The full DOC-01 walkthrough body lands in Phase 7; this is just the checkpoint pointer + the three pedagogy hooks (D-13 seconds-not-millis trap, D-10 cardinality lesson, OTel-to-Prometheus name mapping).

**Where to add:** between current line 77 (end of "Workshop checkpoints") and line 79 ("Reading the code"). Phase 4 inserts a "Step 4: Metrics" subsection — this is the same pattern Phase 3 used to populate "Why is the propagation pair shared?" (lines 94-106).

**Voice match — copy the Phase 3 callout structure** (lines 94-106 — short prose paragraph + bulleted list of source pointers + a tail paragraph naming pedagogical hook). Suggested skeleton:

```markdown
## Step 4: Metrics

`step-04-metrics` adds the **second** OTel signal — metrics — to both services. The two
`OtelSdkConfiguration.java` files now build a `SdkMeterProvider` next to the
`SdkTracerProvider` (D-01 helper-extraction makes this readable as
"sibling pipelines"); the producer adds a `Counter` and a `Histogram`
to its existing instrumentation surfaces, and the consumer adds an
`ObservableGauge`.

The three instrument shapes — one Counter, one Histogram, one ObservableGauge —
cover the OTel SDK's three primary metric kinds:

- **`orders.created`** (`LongCounter`, producer-side) —
  [`OrderService.place(...)`](./producer-service/src/main/java/com/example/producer/domain/OrderService.java)
  increments after the publish returns. Carries `order.priority` from the request payload.
- **`http.server.request.duration`** (`DoubleHistogram`, producer-side) —
  [`HttpServerSpanFilter`](./producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java)
  records seconds (NOT milliseconds — semconv 1.40.0 specifies the unit, and porting
  histograms across services is where the unit mismatch most often bites).
- **`orders.queue.depth.estimate`** (`ObservableGauge`, consumer-side) —
  callback fires on every 10-second collection interval and returns a synthetic
  `ThreadLocalRandom` value. The lesson is the callback mechanism, not the value.

`mise run demo:order` now sends two payloads — `priority=express` and `priority=standard`
— so Mimir shows two series for `orders.created`. Try the Mimir query
`orders_created_total{order_priority="express"}` (note: the OTel Prometheus exporter
mangles dots to underscores and appends `_total` for monotonic counters).
```

**Existing patterns to NOT break:**
- The "Reading the code" section (line 84) currently links to both `OtelSdkConfiguration.java` files — Phase 4 plan should NOT delete or move that link list; the new "Step 4: Metrics" section sits *before* it.
- The "What's NOT here yet" section (lines 108-115) still says "No metrics or log correlation (Phase 4 / Phase 5)" — Phase 4 ships removes the "metrics" half. The planner should either delete that bullet entirely (since logs is now its own remaining bullet) or update it to "No log correlation (Phase 5)".

---

## Shared Patterns (cross-cutting — applied across multiple Phase 4 files)

### 1. Env-var-with-fallback for OTLP endpoint (D-04)

**Source:** `producer-service/.../OtelSdkConfiguration.java` lines 113-115 (already in repo since Phase 2).

```java
String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
    .orElse(DEFAULT_OTLP_ENDPOINT);
```

**Apply to:** `buildMeterProvider(Resource)` in BOTH `OtelSdkConfiguration.java` files. **Do NOT** introduce any new env var (e.g., `OTEL_EXPORTER_OTLP_METRIC_ENDPOINT`) — the metric exporter shares the same endpoint and the same constant. This preserves the "no autoconfigure, no magic" Phase 2 D-12 contract.

### 2. SDK Builder banner-comment style (D-20 / DOC-03 carryforward)

**Source:** `producer-service/.../OtelSdkConfiguration.java` lines 85-103 (the `// ----- Resource: identifies this service in Tempo, Mimir, Loki -----` banner-block style).

**Apply to:** `buildMeterProvider(Resource)` helper body. Each builder step (OTLP exporter, PeriodicMetricReader, SdkMeterProvider) gets its own `// ----- Banner -----` block + multi-line explanation of the workshop-vs-production tradeoff. Comment-density grep gate (≥40 lines per `OtelSdkConfiguration.java`) MUST stay green after Phase 4 — verify with the same grep used in Phase 2.

### 3. Constructor-injection for instruments (D-06 / D-08 / D-12)

**Source:** existing `Tracer` constructor injection in `OrderService` (line 28-31), `HttpServerSpanFilter` (line 51-53), `ProcessingService` (line 48-50).

**Apply to:** Phase 4's `Meter`-injecting classes (`OrderService`, `HttpServerSpanFilter`, optionally `QueueDepthGauge`). **Build instruments once** (Counter, Histogram, ObservableGauge handle) **as final fields**; reuse them across requests. Do NOT call `meter.counterBuilder(...)` per request — the OTel SDK's instrument-resolution machinery is keyed on instrument identity, and rebuilding per-request would (a) defeat caching and (b) is just structurally wrong per OTel's API contract.

### 4. semconv constants over string literals (D-14)

**Source:** existing usage in `HttpServerSpanFilter.java` lines 97-102 + `OtelSdkConfiguration.java` lines 97-100.

```java
.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus())
```

**Apply to:** Phase 4's Histogram `record(...)` Attributes builder. **Do NOT** use string literals for any HTTP attribute — `HttpAttributes` is already imported.

**Inverse pattern (also D-09 / D-14):** for `order.priority` use a string-literal `AttributeKey.stringKey("order.priority")` because it is **not** in the semconv catalog. The plan must state this contrast explicitly so attendees see why the two cases differ.

### 5. Per-service mirror diff (D-02 / Phase 2 D-01 carryforward)

**Source:** `producer-service/.../OtelSdkConfiguration.java` vs `consumer-service/.../OtelSdkConfiguration.java` at HEAD — three-line diff (`SERVICE_NAME` + `getTracer(scope)` + producer-only `httpServerSpanFilter` @Bean).

**Apply to:** every Phase 4 modification to `OtelSdkConfiguration.java`. After Phase 4 the diff between the two files grows by exactly:
- One line for the new `Meter` scope name (`com.example.producer` vs `com.example.consumer`).
- The producer-only `httpServerSpanFilter` @Bean factory gains a `Meter` parameter (no consumer counterpart — D-07).
- Optionally the consumer-only `@PostConstruct` for the gauge (D-18a) — but D-18b avoids this asymmetry by hosting the gauge in a separate `@Component`.

The planner's plan should include a verification step that diffs the two `OtelSdkConfiguration.java` files post-Phase-4 and confirms no unexpected divergence.

### 6. `@Bean(destroyMethod = "close")` lifecycle cascade (D-07 / Phase 2 D-15 carryforward)

**Source:** `OtelSdkConfiguration.openTelemetry()` line 83 today: `@Bean(destroyMethod = "close")`.

**Apply to:** **nothing new.** Phase 4 adds zero `@Bean(destroyMethod=...)` annotations. The existing `OpenTelemetry.close()` cascade already handles `SdkMeterProvider.shutdown().join(10s)` because `OpenTelemetrySdk.close()` walks all registered providers. This is a deliberate non-pattern: the planner should explicitly confirm that the smoke-test verifies "Ctrl-C produces a final metric scrape" (parallel to Phase 2's "last batch flushed" check) — but the verification is at runtime, not in source.

### 7. Annotated git tag at exit (WORK-01 / D-22)

**Source:** Phase 1 / 2 / 3 plan-NN-tag-and-exit-gate plans (most recent: Phase 3's tag-apply atomic commit pattern documented in STATE.md "Recent decisions" lines 83-86).

**Apply to:** Phase 4's final plan. The same shape:
1. Source merged + all 4 ROADMAP success criteria verified live by user gate.
2. Orchestrator applies annotated tag `step-04-metrics` on `main`.
3. STATE.md / ROADMAP.md / REQUIREMENTS.md `[x]` flips land **atomically** with the tag-apply commit (do NOT pre-flip).

---

## No Analog Found

| File | Reason | Planner guidance |
|---|---|---|
| *(none — every Phase 4 file has at least a role-match analog in-repo)* | | |

The closest thing to "no analog" is the optional `QueueDepthGauge.java` — there's no existing observability `@Component` in `consumer-service`, so its layout follows the `OrderListener.java` `@Component` pattern (constructor-inject one dependency, no other state) rather than an exact analog. This is captured in the Pattern Assignment for D-18b above.

---

## Metadata

**Analog search scope:**
- `producer-service/src/main/java/com/example/producer/**`
- `consumer-service/src/main/java/com/example/consumer/**`
- `mise.toml` (root)
- `README.md` (root)
- `producer-service/pom.xml`, `consumer-service/pom.xml` (BOM-managed dependency confirmation only — no source pattern extraction)
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` (carryforward decision references)
- `.planning/phases/03-amqp-context-propagation/03-CONTEXT.md` (carryforward decision references)

**Files scanned:** 13 source/config files + 3 planning docs.

**Files NOT modified by Phase 4 (confirmed against CONTEXT.md `<canonical_refs>`):**
- `producer-service/pom.xml` — `opentelemetry-exporter-otlp` already on classpath (Phase 2). Single artifact ships span + metric + log exporters. **Plan must `mvn dependency:tree` to confirm zero new BOM-managed deps.**
- `consumer-service/pom.xml` — same as producer.
- `otel-bootstrap/**` — Phase 3 shared module is unchanged in Phase 4.
- `producer-service/.../api/OrderController.java` — POST /orders entry point unchanged.
- `consumer-service/.../messaging/OrderListener.java`, `consumer-service/.../domain/ProcessingService.java` — Phase 3 final shapes unchanged in Phase 4.
- `producer-service/.../messaging/OrderPublisher.java` — Phase 3 final shape unchanged in Phase 4.
- `consumer-service/.../config/RabbitConfig.java` — Phase 3 final shape unchanged in Phase 4.

**Pattern extraction date:** 2026-05-01.

**Phase:** 4-metrics.
