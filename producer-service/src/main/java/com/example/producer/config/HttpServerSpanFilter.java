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
