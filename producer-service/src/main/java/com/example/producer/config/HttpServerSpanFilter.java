package com.example.producer.config;

import java.io.IOException;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
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
     * Wrap the filter chain in a SERVER span using the D-01 inline template
     * and record the HTTP duration histogram (METRIC-03).
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
}
