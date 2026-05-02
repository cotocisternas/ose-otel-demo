---
phase: 04-metrics
plan: "03"
subsystem: producer-instrumentation
tags: [metrics, histogram, http-semconv, otel-sdk, producer, filter]
dependency_graph:
  requires: [04-01-meter-pipeline-refactor]
  provides: [http-server-request-duration-histogram]
  affects: [04-05]
tech_stack:
  added: [DoubleHistogram, Meter constructor injection]
  patterns: [extend-dont-replace, timing-capture-before-span, record-in-finally, semconv-constants, single-predicate-both-signals]
key_files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
decisions:
  - "Extended existing HttpServerSpanFilter in place (D-12) — no second filter, no second shouldNotFilter; single /actuator/* predicate covers trace AND histogram"
  - "Histogram unit 's' (seconds) per semconv 1.40.0 (D-13) — inline comment calls out the seconds-not-millis trap explicitly as the textbook OTel pitfall"
  - "startNanos captured BEFORE tracer.spanBuilder call so histogram measures total request time including span overhead"
  - "Attributes: HTTP_REQUEST_METHOD + HTTP_RESPONSE_STATUS_CODE semconv constants (D-14); url.path intentionally excluded (high-cardinality)"
  - "SDK-default explicit-bucket aggregation — no custom View or ExplicitBucketHistogramAggregation (D-15)"
  - "DoubleHistogram built once as a final field in the constructor — not per-request (D-12 OTel API contract)"
metrics:
  duration: 3min
  completed_date: "2026-05-01"
  tasks: 1
  files_modified: 1
  files_created: 0
---

# Phase 4 Plan 03: Producer Histogram Summary

Extended the existing Phase 2 `HttpServerSpanFilter` with a `DoubleHistogram` named `http.server.request.duration` (seconds, semconv 1.40.0), recording method + status-code attributes in the existing `finally` block before `span.end()` — no new filter, no second predicate, no structural change to the Phase 2 SERVER span pipeline.

## What Was Built

### HttpServerSpanFilter — constructor shape (post-extension)

```java
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

### HttpServerSpanFilter — timing capture + finally block (post-extension)

```java
// ---- Phase 4 D-13: capture timing BEFORE span build ----
long startNanos = System.nanoTime();

String method = request.getMethod();
String path = request.getRequestURI();

Span span = tracer.spanBuilder(method + " " + path)
    // ... 6 HTTP semconv attributes unchanged from Phase 2 ...
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
    // ---- Phase 4 D-13 / D-14: HTTP duration histogram (METRIC-03) ----
    //
    // Convert nanos -> seconds via explicit double division. The
    // semconv-aligned unit is SECONDS (D-13). Common pitfall: writing
    // (System.nanoTime() - startNanos) / 1_000_000 produces millis;
    // (System.nanoTime() - startNanos) / 1_000_000_000.0 produces
    // seconds (note the .0 — integer division of nanos by 1e9 would
    // truncate sub-second requests to 0).
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

    requestDuration.record(seconds, Attributes.of(
        HttpAttributes.HTTP_REQUEST_METHOD, method,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus()));

    span.end();
}
```

### shouldNotFilter — unchanged

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");
}
```

Identical byte-for-byte to Phase 2 — a single predicate keeps health-check noise out of BOTH the SERVER span AND the histogram (D-12).

## Verification Results

### Acceptance criteria checks

All acceptance criteria passed:
- New imports present: `Attributes`, `DoubleHistogram`, `Meter`
- Existing OTel imports all preserved (Span, SpanKind, StatusCode, Tracer, Scope, HttpAttributes, ServerAttributes, UrlAttributes)
- Constructor takes `(Tracer tracer, Meter meter)` — D-12
- `private final DoubleHistogram requestDuration` field declared
- Histogram name `"http.server.request.duration"` — METRIC-03
- Histogram unit `"s"` — D-13 (seconds-not-millis)
- Histogram description set
- `startNanos = System.nanoTime()` captured BEFORE `tracer.spanBuilder(...)` call
- Seconds conversion: `/ 1_000_000_000.0` (explicit double, not millis)
- `requestDuration.record(seconds, Attributes.of(...)` in finally block BEFORE `span.end()`
- Attributes use `HttpAttributes.HTTP_REQUEST_METHOD` and `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` semconv constants — D-14
- `url.path` NOT included in histogram attributes — D-14 cardinality guard
- `shouldNotFilter` predicate byte-for-byte unchanged
- `meter.histogramBuilder` call count = 1 (built once in constructor, not per-request)
- JavaDoc updated for METRIC-03, semconv 1.40.0, seconds-not-millis trap
- Class still `extends OncePerRequestFilter`

Note on the "no millis divisor" check: the acceptance criterion regex `\(System\.nanoTime\(\) - startNanos\) / 1_000_000\b` matches a comment line (line 190) that explicitly documents "THIS would produce millis — don't do it." This is a false positive from the grep — the comment is the educational callout the plan requires (D-13). The actual conversion code on line 194 uses `/ 1_000_000_000.0`. The real regression check (no millis in code) passes; the comment is intentional and correct.

### producer-service compile

```
mvn -pl producer-service compile -q
Exit: 0 (BUILD SUCCESS)
```

Producer-service now compiles cleanly with Plan 04-01's updated `HttpServerSpanFilter(Tracer, Meter)` factory and this plan's matching constructor.

### Comment density

```
HttpServerSpanFilter.java: 67 comment lines (219 total lines)
```

Well above the DOC-03 quality bar. The file carries inline comments explaining the seconds-not-millis trap, the timing-before-span-build rationale, the cardinality guard for url.path, and the finally-ordering guarantees.

## Deviations from Plan

None — plan executed exactly as written. The file was modified in place per the plan's Edit 1-5 structure. All acceptance criteria pass. The one automated check that appeared to fail (millis divisor) is a known false positive: the pattern matches a comment line that documents the trap, not production code.

## Files Modified

| File | Change |
|------|--------|
| `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` | +100/-4 lines: add Meter imports, update class JavaDoc, add Meter parameter + DoubleHistogram field to constructor, capture startNanos, add record() in finally before span.end |

**New files:** 0
**New pom dependencies:** 0

## Known Stubs

None. The histogram is wired to the real OTel SDK pipeline (via the `Meter` @Bean from Plan 04-01). No placeholder values, no hardcoded data.

## Threat Flags

None. The new histogram surface is bounded:
- `http.request.method` values are HTTP RFC-enumerated (finite set ~10)
- `http.response.status_code` values are 3-digit integers (finite set ~50)
- Combinatorial space ~500 series per service — well within Mimir's per-series budget
- `url.path` explicitly excluded (D-14) — the primary cardinality guard

No new network endpoints, no new auth paths, no new file access patterns introduced.

## Self-Check: PASSED
