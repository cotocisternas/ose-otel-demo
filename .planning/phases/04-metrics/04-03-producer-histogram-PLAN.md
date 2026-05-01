---
id: 04-03-producer-histogram
phase: 04-metrics
plan: 03
type: execute
wave: 2
depends_on: [04-01-meter-pipeline-refactor]
requirements: [METRIC-03]
requirements_addressed: [METRIC-03]
files_modified:
  - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
autonomous: true
objective: "EXTEND the existing Phase 2 HttpServerSpanFilter (do NOT replace, do NOT add a second filter, do NOT touch shouldNotFilter) with a DoubleHistogram named `http.server.request.duration` (METRIC-03). Constructor-inject Meter (sibling to existing Tracer), build the histogram ONCE as a final field with unit `\"s\"` (semconv 1.40.0 — seconds, NOT milliseconds; D-13 is the textbook trap). Capture `long startNanos = System.nanoTime()` BEFORE the SERVER span is built and record `(System.nanoTime() - startNanos) / 1_000_000_000.0` seconds in the existing finally block, BEFORE `span.end()`. Histogram attributes use semconv constants `HttpAttributes.HTTP_REQUEST_METHOD` + `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` (D-14 — NOT string literals, NOT url.path). SDK-default explicit-bucket aggregation (D-15 — no custom View)."
must_haves:
  truths:
    - "HttpServerSpanFilter constructor takes (Tracer, Meter) — Meter is the new second parameter (D-12)"
    - "HttpServerSpanFilter has `private final DoubleHistogram requestDuration` field built ONCE in the constructor via `meter.histogramBuilder(\"http.server.request.duration\")` (D-12 — instruments built once)"
    - "Histogram unit is `\"s\"` (seconds, semconv 1.40.0 / D-13) — NOT `\"ms\"`. Inline comment must explicitly call out the seconds-not-millis trap"
    - "Histogram description set on the builder (workshop-readable)"
    - "Timing capture: `long startNanos = System.nanoTime();` BEFORE the `tracer.spanBuilder(...)` call so the histogram measures TOTAL request time including span start/end overhead (matches what real users would see)"
    - "Histogram record line: `requestDuration.record(seconds, Attributes.of(...))` lives in the existing `finally` block, BEFORE `span.end()` — so the record happens whether or not the chain throws (D-12: same timing surface as the SERVER span)"
    - "Seconds conversion: `(System.nanoTime() - startNanos) / 1_000_000_000.0` (D-13 — explicit double division, NOT `/ 1000.0` for millis-to-seconds, NOT `* 1e-9` style)"
    - "Histogram attributes use SEMCONV CONSTANTS (D-14): `HttpAttributes.HTTP_REQUEST_METHOD` and `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` — `io.opentelemetry.semconv.HttpAttributes` already imported at HEAD"
    - "`url.path` is NOT included in the histogram attributes (D-14 — high cardinality on path values would explode the metric series count); url.path stays on the SERVER span only"
    - "NO custom View / ExplicitBucketHistogramAggregation on the SdkMeterProvider (D-15) — SDK defaults; comment in the histogramBuilder block names the rationale"
    - "Existing Phase 2 SERVER span shape preserved: spanBuilder + setSpanKind(SERVER) + 6 attributes + startSpan + try(Scope) + chain.doFilter + setAttribute(HTTP_RESPONSE_STATUS_CODE) + catch (recordException + setStatus(ERROR) + throw) + finally span.end (D-12 — extension, not replacement)"
    - "Existing Phase 2 `shouldNotFilter` /actuator/* exclusion is UNCHANGED (D-12 — single exclusion predicate already keeps health-check noise out of both the SERVER span AND the histogram, no second predicate needed)"
    - "Class JavaDoc updated: existing 'Wraps every non-/actuator HTTP request in a SERVER span (TRACE-05)' becomes 'SERVER span + HTTP duration histogram (TRACE-05 + METRIC-03)' per D-12"
    - "New imports: io.opentelemetry.api.common.Attributes, io.opentelemetry.api.metrics.DoubleHistogram, io.opentelemetry.api.metrics.Meter (HttpAttributes already imported at HEAD)"
    - "mvn -pl producer-service compile exits 0 — together with Plan 04-01's HttpServerSpanFilter @Bean factory update, this plan unblocks producer-service compile"
  artifacts:
    - path: "producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java"
      provides: "HttpServerSpanFilter extended with a DoubleHistogram `http.server.request.duration` (seconds; HTTP_REQUEST_METHOD + HTTP_RESPONSE_STATUS_CODE attrs); records every non-/actuator request from inside the existing finally block, BEFORE span.end (METRIC-03 / D-12 / D-13 / D-14 / D-15)"
      contains: "requestDuration.record(seconds, Attributes.of"
  key_links:
    - from: "HttpServerSpanFilter constructor"
      to: "Meter @Bean (created in Plan 04-01, scope com.example.producer)"
      via: "Spring constructor injection via the @Bean HttpServerSpanFilter httpServerSpanFilter(Tracer, Meter) factory updated in Plan 04-01"
      pattern: "HttpServerSpanFilter\\(Tracer tracer, Meter meter\\)"
    - from: "requestDuration.record(seconds, attrs)"
      to: "Mimir / Prometheus exporter (via SdkMeterProvider → PeriodicMetricReader 10s → OTLP gRPC :4317 → otel-lgtm)"
      via: "OTel-to-Prometheus name mangling + unit suffix: 'http.server.request.duration' histogram with unit 's' -> 'http_server_request_duration_seconds' bucket time series"
      pattern: "requestDuration\\.record"
    - from: "(System.nanoTime() - startNanos) / 1_000_000_000.0"
      to: "Histogram value in seconds (D-13 — semconv-aligned unit)"
      via: "nanoseconds-to-seconds conversion via explicit double division — NOT /1000.0 (millis), NOT *1e-9 stylistic alternatives"
      pattern: "/ 1_000_000_000\\.0"
---

<objective>
Extend the producer service's existing Phase 2 `HttpServerSpanFilter` with a `DoubleHistogram` named `http.server.request.duration` (METRIC-03). The filter already brackets every non-`/actuator/*` request in `try { chain.doFilter } finally { span.end() }` — Phase 4 reuses that timing surface, adding a `histogram.record(seconds, attrs)` line in the existing finally block, BEFORE `span.end()`.

**This is an EXTENSION, not a replacement** (D-12). No new filter. No second `OncePerRequestFilter`. No second `shouldNotFilter`. The filter's responsibility expands from "SERVER span" to "SERVER span + HTTP duration histogram" — both signals share the same exclusion predicate (`/actuator/*`) and the same timing surface.

**Unit is seconds (D-13).** OTel HTTP semconv 1.40.0 specifies `http.server.request.duration` in seconds, not milliseconds. The seconds-not-millis trap is the textbook OTel pitfall — workshop attendees who later port custom histograms to OTel HTTP semconv routinely get this wrong. The inline comment explicitly calls it out.

**Attributes are semconv constants (D-14):** `HttpAttributes.HTTP_REQUEST_METHOD` and `HttpAttributes.HTTP_RESPONSE_STATUS_CODE`. NOT string literals. `url.path` is intentionally excluded — high-cardinality path values would explode the metric series count; the path stays on the SERVER span only.

**No custom buckets (D-15).** The OTel SDK's default explicit-bucket aggregation for `http.server.request.duration` (seconds) produces sensible workshop values. Bucket tuning is a real-world concern outside the SDK lesson — the inline comment says so.

Purpose: METRIC-03 satisfied. Plan 04-05 verifies live (~30s of demo traffic produces `http_server_request_duration_seconds` with `count`, `sum`, and bucket histograms in Mimir — ROADMAP SC #2).

Output: 1 modified file (`producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`).
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
@.planning/phases/04-metrics/04-01-meter-pipeline-refactor-PLAN.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@CLAUDE.md
@producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java

<interfaces>
<!-- Key types this plan uses. All BOM-managed via opentelemetry-bom:1.61.0 -->
<!-- and io.opentelemetry.semconv:1.40.0 (already on classpath since Phase 2). -->

From io.opentelemetry.api.metrics:
```java
public interface Meter {
    DoubleHistogramBuilder histogramBuilder(String name);
}
public interface DoubleHistogramBuilder {
    DoubleHistogramBuilder setDescription(String description);
    DoubleHistogramBuilder setUnit(String unit);   // "s" for seconds (semconv 1.40.0)
    DoubleHistogram build();
}
public interface DoubleHistogram {
    void record(double value, Attributes attributes);
    void record(double value);   // unused
}
```

From io.opentelemetry.semconv (ALREADY imported at HEAD — line 10 of HttpServerSpanFilter.java):
```java
public final class HttpAttributes {
    public static final AttributeKey<String> HTTP_REQUEST_METHOD;       // String value: "GET", "POST", ...
    public static final AttributeKey<Long>   HTTP_RESPONSE_STATUS_CODE; // Long value: 200, 404, ...
    public static final AttributeKey<String> HTTP_ROUTE;
    // (used by Phase 2 SERVER span at line 102 of current file)
}
```

From io.opentelemetry.api.common (NEW import):
```java
public interface Attributes {
    static <T1, T2> Attributes of(AttributeKey<T1> k1, T1 v1, AttributeKey<T2> k2, T2 v2);
    // 2-key form is what we use; the 1-key form is for Plan 04-02
}
```

HttpServerSpanFilter current shape (HEAD step-03-context-propagation — see read_first):
```java
public class HttpServerSpanFilter extends OncePerRequestFilter {
    private final Tracer tracer;
    public HttpServerSpanFilter(Tracer tracer) { this.tracer = tracer; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) ... {
        String method = request.getMethod();
        String path = request.getRequestURI();
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
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus());
        } catch (RuntimeException | ServletException | IOException e) {
            span.recordException(e); span.setStatus(StatusCode.ERROR); throw e;
        } finally {
            span.end();    // <-- histogram.record line slots BEFORE this
        }
    }
}
```
</interfaces>
</context>

<tasks>

<task id="04-03-T1" type="auto">
  <name>Task 1: Extend HttpServerSpanFilter — constructor-inject Meter, build DoubleHistogram once, capture startNanos before span build, record seconds in existing finally block</name>
  <files>producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java (current state at HEAD — Phase 2's SERVER-span filter; the modification target)
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 238-329 — exact target shape including the field/constructor extension at lines 256-272 and the timing-capture + record-in-finally pattern at lines 285-321)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-12 extend-don't-replace, D-13 seconds-not-millis with the conversion expression, D-14 semconv constants for attributes, D-15 SDK-default buckets)
    - .planning/phases/04-metrics/04-01-meter-pipeline-refactor-PLAN.md (Plan 04-01 produces the Meter @Bean AND updates the HttpServerSpanFilter @Bean factory to take Meter — but the HttpServerSpanFilter CONSTRUCTOR itself needs THIS plan to match (Tracer, Meter) before producer-service can compile)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (D-06 producer-only HttpServerSpanFilter, D-07 /actuator exclusion stays — single predicate covers both span and histogram)
  </read_first>
  <action>
    Modify `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` IN PLACE. This is purely additive at the source level (one new field, one new constructor parameter, one timing-capture line, one record line in the existing finally) — no Phase 2 lines are deleted, no behavior changes for the SERVER span pipeline, the catch shape and `shouldNotFilter` are unchanged.

    **Edit 1 — Imports.** ADD these imports below the existing OTel imports (preserve alphabetical-ish grouping). The file already imports `io.opentelemetry.semconv.HttpAttributes`, `io.opentelemetry.semconv.ServerAttributes`, `io.opentelemetry.semconv.UrlAttributes` at HEAD — those stay.

    ```java
    import io.opentelemetry.api.common.Attributes;
    import io.opentelemetry.api.metrics.DoubleHistogram;
    import io.opentelemetry.api.metrics.Meter;
    ```

    Do NOT remove any existing import.

    **Edit 2 — Update class JavaDoc** (D-12). Phase 2's class JavaDoc opens with "Wraps every non-/actuator HTTP request in a SERVER span with HTTP semantic-convention attributes (TRACE-05)." Phase 4 expands the responsibility to two signals (SERVER span + HTTP duration histogram). REPLACE the opening paragraph:

    ```java
    /**
     * Wraps every non-/actuator HTTP request in a SERVER span (TRACE-05) AND
     * records its duration into the {@code http.server.request.duration}
     * {@link DoubleHistogram} (METRIC-03 / Phase 4 / D-12).
     *
     * <p>The Phase 2 filter wrapped the chain in a {@code SERVER} span; Phase 4
     * EXTENDS this same filter (D-12 — no second {@code OncePerRequestFilter},
     * no chain reordering, no second {@link #shouldNotFilter}) so a single
     * exclusion predicate covers BOTH signals: {@code /actuator/*} stays out
     * of the trace AND out of the histogram.
     *
     * <p>The histogram's unit is {@code "s"} (seconds — semconv 1.40.0; D-13).
     * Recording milliseconds here would still produce values in Mimir, but
     * they would be 1000x off when correlated with other OTel-instrumented
     * apps that follow the spec. The seconds-not-millis trap is one of the
     * most common OTel-porting mistakes.
     *
     * <p>Histogram attributes follow HTTP semconv (D-14):
     * {@link HttpAttributes#HTTP_REQUEST_METHOD} (e.g. {@code "POST"}) and
     * {@link HttpAttributes#HTTP_RESPONSE_STATUS_CODE} (e.g. {@code 202}).
     * {@code url.path} is intentionally excluded — high-cardinality path
     * values would blow up the Mimir series count. The path stays on the
     * SERVER span only.
     *
     * <p>Bucket configuration is the SDK default (D-15) — no custom
     * {@link io.opentelemetry.sdk.metrics.View} or
     * {@code ExplicitBucketHistogramAggregation} on the meter provider.
     * Bucket tuning is a real-world concern outside the SDK lesson.
     *
     * <p>The remaining Phase 2 paragraphs (Why a Filter / Why exclude
     * /actuator / Why producer-only) still apply unchanged — preserved below.
     */
    ```

    Then PRESERVE the existing Phase 2 paragraphs ("Why a Filter and not a HandlerInterceptor?", "Why exclude /actuator/?", "Why producer-only?") immediately after — those rationales are unchanged by Phase 4.

    **Edit 3 — Field & constructor.** Replace the existing field/constructor block:

    ```java
    // Before (current HEAD):
    public class HttpServerSpanFilter extends OncePerRequestFilter {

        private final Tracer tracer;

        public HttpServerSpanFilter(Tracer tracer) {
            this.tracer = tracer;
        }
    ```

    With (Meter param appended; DoubleHistogram built once as a final field — D-12):

    ```java
    public class HttpServerSpanFilter extends OncePerRequestFilter {

        private final Tracer tracer;
        private final DoubleHistogram requestDuration;

        public HttpServerSpanFilter(Tracer tracer, Meter meter) {
            this.tracer = tracer;
            // Histogram built ONCE here and reused on every request. The OTel
            // SDK's instrument-resolution machinery is keyed on instrument
            // identity; rebuilding per-request would defeat caching AND is
            // structurally wrong per the OTel API contract.
            //
            // Name + unit are LOCKED by OTel HTTP semconv 1.40.0:
            //   https://opentelemetry.io/docs/specs/semconv/http/http-metrics/
            // Unit is "s" (seconds), NOT "ms". Recording milliseconds here
            // would still produce values in Mimir but they would be 1000x off
            // when correlated with other OTel-instrumented apps. The seconds-
            // not-millis trap (D-13) is one of the most common OTel-porting
            // mistakes — the spec is explicit and Mimir's
            // http_server_request_duration_seconds default dashboards assume
            // seconds.
            this.requestDuration = meter.histogramBuilder("http.server.request.duration")
                .setUnit("s")
                .setDescription("Duration of HTTP server requests, semconv 1.40.0")
                .build();
        }
    ```

    **Edit 4 — `shouldNotFilter` UNCHANGED.** D-12: a single `/actuator/*` exclusion predicate keeps health-check noise out of BOTH the SERVER span AND the histogram. The existing method (current file lines 64-67) stays exactly as-is:

    ```java
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }
    ```

    **Edit 5 — Extend `doFilterInternal` to capture timing + record in finally.** Modify the method body. The full new body:

    ```java
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // ---- Phase 4 D-13: capture timing BEFORE span build ----
        //
        // System.nanoTime() before the SDK's spanBuilder(...) call so the
        // histogram measures the TOTAL request time as a real user would
        // experience it — including the cost of starting the span and
        // ending it. If we captured AFTER startSpan, the histogram would
        // systematically under-report by the few hundred nanoseconds the
        // span builder takes (negligible for HTTP, but stylistically
        // wrong).
        //
        // Why nanoTime, not currentTimeMillis? nanoTime is monotonic
        // (immune to NTP wall-clock corrections) and has nanosecond
        // resolution — both required for sub-millisecond HTTP timing.
        long startNanos = System.nanoTime();

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
            // ---- Phase 4 D-13 / D-14: HTTP duration histogram (METRIC-03) ----
            //
            // Convert nanos -> seconds via explicit double division. The
            // semconv-aligned unit is SECONDS (D-13). Common pitfall: writing
            // (System.nanoTime() - startNanos) / 1_000_000 produces millis;
            // (System.nanoTime() - startNanos) / 1_000_000_000.0 produces
            // seconds (note the .0 — integer division of nanos by 1e9 would
            // truncate sub-second requests to 0).
            double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

            // Histogram attributes follow HTTP semconv (D-14): method +
            // status code. We do NOT record url.path here — high-cardinality
            // paths (e.g. /orders/{uuid}) would explode Mimir's series count.
            // The path remains on the SERVER span (set above), where one
            // span per request is the natural cardinality unit. Histograms
            // need bounded label sets.
            //
            // The histogram fires whether the chain threw or returned cleanly:
            // (a) success path: status_code is set above (line right after
            //     chain.doFilter returns), histogram records final status.
            // (b) failure path: status_code may not have been set on the
            //     span — but response.getStatus() still returns whatever
            //     the servlet container has decided (typically 500 by
            //     default for unhandled exceptions). The histogram captures
            //     this value too, giving Mimir a count of error responses
            //     by status code.
            requestDuration.record(seconds, Attributes.of(
                HttpAttributes.HTTP_REQUEST_METHOD, method,
                HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus()));

            span.end();
        }
    }
    ```

    **Constraint preservation checklist (verify after editing):**
    - The Phase 2 `shouldNotFilter` method is byte-for-byte unchanged (D-12).
    - The Phase 2 SERVER span's 6 starting attributes are unchanged (HTTP_REQUEST_METHOD / URL_PATH / URL_SCHEME / SERVER_ADDRESS / SERVER_PORT / HTTP_ROUTE).
    - The Phase 2 catch block content is byte-for-byte identical (recordException / setStatus / throw).
    - `extends OncePerRequestFilter` unchanged — NO second filter class introduced.
    - The Histogram is built ONCE in the constructor (final field) — no `meter.histogramBuilder` call inside `doFilterInternal`.
    - The histogram record line is INSIDE the `finally` block AND BEFORE `span.end()`.
    - `startNanos = System.nanoTime()` is captured BEFORE the `tracer.spanBuilder(...)` call (so the histogram includes span-build overhead).
    - Seconds conversion uses `/ 1_000_000_000.0` (D-13 — explicit double).
    - Histogram attributes use `HttpAttributes.HTTP_REQUEST_METHOD` and `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` constants (D-14) — NOT string literals; `url.path` is NOT recorded on the histogram (D-14).
    - `String method = request.getMethod();` and `String path = request.getRequestURI();` are still extracted in the same place (their values flow into BOTH the SERVER span and the histogram attrs).
    - No custom View / ExplicitBucketHistogramAggregation introduced anywhere in the file (D-15).
  </action>
  <acceptance_criteria>
    - File exists: `test -f producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - New imports present: `for i in 'import io.opentelemetry.api.common.Attributes;' 'import io.opentelemetry.api.metrics.DoubleHistogram;' 'import io.opentelemetry.api.metrics.Meter;'; do grep -qF "$i" producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java || exit 1; done`
    - Existing OTel imports preserved (regression — Phase 2 baseline): `for i in 'import io.opentelemetry.api.trace.Span;' 'import io.opentelemetry.api.trace.SpanKind;' 'import io.opentelemetry.api.trace.StatusCode;' 'import io.opentelemetry.api.trace.Tracer;' 'import io.opentelemetry.context.Scope;' 'import io.opentelemetry.semconv.HttpAttributes;' 'import io.opentelemetry.semconv.ServerAttributes;' 'import io.opentelemetry.semconv.UrlAttributes;'; do grep -qF "$i" producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java || exit 1; done`
    - Constructor takes (Tracer, Meter) (D-12): `grep -qE 'public HttpServerSpanFilter\(Tracer tracer, Meter meter\)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - DoubleHistogram field declared as final: `grep -qE 'private final DoubleHistogram requestDuration' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Histogram name = "http.server.request.duration" (METRIC-03): `grep -qF 'meter.histogramBuilder("http.server.request.duration")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Histogram unit = "s" (D-13 seconds): `grep -qF '.setUnit("s")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Histogram description set: `grep -qF '.setDescription(' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - startNanos captured BEFORE spanBuilder call (D-13 line-order): `awk '/long startNanos = System.nanoTime\(\);/{seen=NR} /tracer\.spanBuilder/{if (!seen) exit 1; exit 0}' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Seconds conversion uses `/ 1_000_000_000.0` (D-13 — NOT `/1_000_000` for millis, NOT `* 1e-9`): `grep -qF '/ 1_000_000_000.0' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Seconds conversion does NOT use millisecond divisor (regression check for D-13 trap): `! grep -qE '\(System\.nanoTime\(\) - startNanos\) / 1_000_000\b' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Histogram record line uses semconv constants (D-14): `grep -qF 'requestDuration.record(seconds, Attributes.of(' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'HttpAttributes.HTTP_REQUEST_METHOD' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'HttpAttributes.HTTP_RESPONSE_STATUS_CODE' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Histogram does NOT include url.path / URL_PATH (D-14 — high-cardinality exclusion): `awk '/requestDuration\.record\(seconds, Attributes\.of\(/,/\)\);/' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java | grep -qE 'URL_PATH|url\.path' && exit 1; exit 0`
    - Histogram record happens INSIDE the finally block AND BEFORE span.end (line-order check): `awk '/^\s*\} finally \{/{infinally=1} /requestDuration\.record\(/{if (!infinally) exit 1; seen_record=NR} /span\.end\(\);/{if (!seen_record) exit 1; exit 0}' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - shouldNotFilter unchanged (D-12): `grep -qE 'protected boolean shouldNotFilter\(HttpServletRequest request\)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'return request.getRequestURI().startsWith("/actuator/")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Single shouldNotFilter (no second predicate added): `grep -c 'shouldNotFilter' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java | awk '{ if ($1 < 1 || $1 > 3) exit 1 }'` (1 method definition + 0-2 references in JavaDoc)
    - Phase 2 SERVER span attributes preserved: `for c in 'HttpAttributes.HTTP_REQUEST_METHOD, method' 'UrlAttributes.URL_PATH, path' 'UrlAttributes.URL_SCHEME' 'ServerAttributes.SERVER_ADDRESS' 'ServerAttributes.SERVER_PORT' 'HttpAttributes.HTTP_ROUTE'; do grep -qF "$c" producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java || exit 1; done`
    - Phase 2 catch shape unchanged: `grep -qF 'catch (RuntimeException | ServletException | IOException e)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'span.recordException(e)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'span.setStatus(StatusCode.ERROR)' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'throw e;' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Histogram is built ONCE (constructor, final field), NOT per-request: `grep -c 'meter.histogramBuilder' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` returns exactly 1
    - JavaDoc updated to mention METRIC-03 + seconds + semconv (D-12): `grep -qF 'METRIC-03' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qE 'semconv 1\.40\.0' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qE 'seconds.*not millis|seconds-not-millis|"s".*NOT' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - Class still extends OncePerRequestFilter: `grep -qF 'extends OncePerRequestFilter' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
    - producer-service compiles — together with Plan 04-01's @Bean factory update + Plan 04-02's OrderService update, this plan should let producer-service compile cleanly: `mvn -pl producer-service compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]' || mvn -pl producer-service compile 2>&1 | tail -10 | grep -qE 'BUILD SUCCESS'`
  </acceptance_criteria>
  <verify>
    <automated>grep -qF 'private final DoubleHistogram requestDuration' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'meter.histogramBuilder("http.server.request.duration")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF '.setUnit("s")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF '/ 1_000_000_000.0' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'requestDuration.record(seconds, Attributes.of(' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'HttpAttributes.HTTP_REQUEST_METHOD' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'HttpAttributes.HTTP_RESPONSE_STATUS_CODE' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && grep -qF 'return request.getRequestURI().startsWith("/actuator/")' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java && [ "$(grep -c 'meter.histogramBuilder' producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java)" -eq 1 ] && mvn -pl producer-service compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]'</automated>
  </verify>
  <done>HttpServerSpanFilter.java extended: constructor takes (Tracer, Meter); DoubleHistogram requestDuration built ONCE in constructor with name "http.server.request.duration", unit "s" (seconds), description set; startNanos captured BEFORE spanBuilder call; record(seconds, attrs) line lives in the existing finally block, BEFORE span.end; attributes use HttpAttributes.HTTP_REQUEST_METHOD + HTTP_RESPONSE_STATUS_CODE semconv constants; url.path NOT recorded on histogram; shouldNotFilter unchanged; Phase 2 SERVER span shape and catch shape unchanged; class JavaDoc updated for METRIC-03 + seconds-not-millis trap callout. Producer-service compiles cleanly.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 04-03 — producer Histogram)

| Boundary | Description |
|----------|-------------|
| HTTP client → POST /orders → HttpServerSpanFilter → chain | Existing Phase 2 boundary; the filter sees `request.getMethod()`, `request.getRequestURI()`, `response.getStatus()` — all servlet-framework-controlled (not user-controlled string content). |
| HttpServerSpanFilter → SdkMeterProvider → OTLP gRPC :4317 | Already crossed by Plan 04-01's metric pipeline. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-03-01 | Denial of Service (cardinality explosion) | An attacker could send requests with crafted HTTP methods (e.g., `request.getMethod()` returns the raw method, including unusual values like `PROPFIND`, `PURGE`, custom methods) blowing up the `http_server_request_duration_seconds` series count by `http.request.method` label | mitigate (bounded by spec) | The HTTP_REQUEST_METHOD semconv attribute IS bounded by definition (HTTP RFCs enumerate the methods); even non-standard methods are a small finite set. Status codes are 3-digit integers — also bounded. The combinatorial space is method × status_code (~10 × ~50 = ~500) per service, well within Mimir's per-series budget. The deliberate exclusion of `url.path` (D-14) is the load-bearing mitigation. Severity: low. |
| T-04-03-02 | Tampering | A future PR could regress the seconds-not-millis property by changing `/ 1_000_000_000.0` to `/ 1_000_000` | mitigate | The acceptance criteria above explicitly assert the millisecond divisor regex is absent. The class-level JavaDoc documents the trap. PR review on this file's diff would catch the change. Severity: medium (workshop integrity), low (security). |
| T-04-03-03 | Information Disclosure | The histogram exports request timing — an attacker doing timing analysis on, e.g., login endpoints, could infer behavior | accept | The producer service has no auth surface; only `POST /orders` is non-actuator; timing of order placement reveals no secrets. Workshop scope. Severity: low. |
| T-04-03-04 | Denial of Service | The histogram callback runs on the request thread; a misimplementation that allocated unbounded objects would slow request latency | mitigate | `Attributes.of(...)` with two pre-known keys is allocation-cheap (semconv constants are static); `System.nanoTime()` is allocation-free. The implementation is hot-path-safe. Severity: low. |

**Phase scope:** Extension of an existing filter; no new threat surface.
</threat_model>

<verification>
- HttpServerSpanFilter.java: constructor takes (Tracer, Meter); DoubleHistogram requestDuration built ONCE with name="http.server.request.duration", unit="s", description set.
- startNanos = System.nanoTime() captured BEFORE the tracer.spanBuilder call.
- requestDuration.record(seconds, Attributes.of(HTTP_REQUEST_METHOD, method, HTTP_RESPONSE_STATUS_CODE, status)) in the existing finally block, BEFORE span.end().
- Seconds conversion uses `/ 1_000_000_000.0` (D-13). Regression check: NOT `/1_000_000`.
- Attributes use semconv constants (D-14); url.path NOT included.
- shouldNotFilter `/actuator/*` exclusion unchanged (D-12 — single predicate covers both signals).
- Phase 2 SERVER span shape preserved (6 starting attrs, status_code on success, recordException + setStatus(ERROR) in catch, span.end in finally).
- Class JavaDoc updated to reflect SERVER span + HTTP duration histogram (TRACE-05 + METRIC-03), with seconds-not-millis trap explicitly called out.
- producer-service mvn compile exits 0 (this plan + Plan 04-01's @Bean factory update + Plan 04-02's OrderService update collectively unblock producer compile).
</verification>

<success_criteria>
- METRIC-03 (DoubleHistogram `http.server.request.duration` in seconds with HTTP_REQUEST_METHOD + HTTP_RESPONSE_STATUS_CODE attrs) satisfied at the source level. Live verification (~30s of demo traffic produces http_server_request_duration_seconds with count/sum/buckets in Mimir) is Plan 04-05's gate.
- D-12 (extend, don't replace), D-13 (seconds), D-14 (semconv constants), D-15 (SDK-default buckets) honored.
- Phase 2 D-01 (pure-inline span template), D-06 (producer-only filter), D-07 (/actuator exclusion preserved) intact.
</success_criteria>

<output>
After completion, create `.planning/phases/04-metrics/04-03-SUMMARY.md` documenting:
- Final shape of HttpServerSpanFilter.java doFilterInternal — paste the timing-capture line + the finally block showing seconds conversion + record line + span.end ordering.
- Final shape of the constructor — paste the (Tracer, Meter) signature + the meter.histogramBuilder block.
- Confirmation that shouldNotFilter is unchanged.
- mvn -pl producer-service compile output (last 3 lines — should be BUILD SUCCESS now that Plan 04-01's HttpServerSpanFilter @Bean factory + this plan's matching constructor are aligned).
- Files modified: 1 (HttpServerSpanFilter.java); 0 new files; 0 new pom dependencies.
</output>
</content>
</invoke>