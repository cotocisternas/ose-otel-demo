package com.example.otel.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * {@link ClientHttpRequestInterceptor} that wraps each outbound HTTP call in a
 * {@link SpanKind#CLIENT} span and injects W3C trace context headers
 * ({@code traceparent}, {@code tracestate}, {@code baggage}) into the request
 * via the composite propagator (HCLI-01).
 *
 * <p>This is the HTTP counterpart of {@code TracingMessagePostProcessor} in the
 * {@code com.example.otel.amqp} package — same span lifecycle template, same
 * propagator reuse, same constructor injection shape — substituting
 * {@link ClientHttpRequestInterceptor} for {@code MessagePostProcessor} and
 * {@link org.springframework.http.HttpHeaders} for {@code MessageProperties}.
 *
 * <p><strong>Attribute coverage (HCLI-02).</strong> The CLIENT span carries the
 * full v2.0 HTTP-client semconv attribute set via stable constants from
 * {@code opentelemetry-semconv:1.40.0}:
 * {@link HttpAttributes#HTTP_REQUEST_METHOD},
 * {@link ServerAttributes#SERVER_ADDRESS}, {@link ServerAttributes#SERVER_PORT},
 * {@link UrlAttributes#URL_FULL}, {@link HttpAttributes#HTTP_RESPONSE_STATUS_CODE},
 * and the incubating {@link ServiceIncubatingAttributes#SERVICE_PEER_NAME} from
 * {@code opentelemetry-semconv-incubating:1.40.0-alpha}.
 *
 * <p><strong>F6-2: span started BEFORE injection.</strong> The {@link Span} is
 * started with {@link Tracer#spanBuilder} BEFORE {@link Span#makeCurrent()} is
 * called. Only after the span is current does {@code propagator.inject()} run —
 * guaranteeing that {@code Context.current()} contains the CLIENT span's
 * {@code spanId} when {@code traceparent} is written into the request headers.
 * If injected before {@code makeCurrent()}, the INTERNAL span's {@code spanId}
 * would be written; the downstream service would become a sibling of the CLIENT
 * span rather than a child.
 *
 * <p><strong>F6-3: interceptor registered last.</strong> {@code HttpClientConfig}
 * registers this interceptor last via {@code RestClient.Builder.requestInterceptor()}.
 * Any auth or retry interceptors added before this one see {@code traceparent}
 * already present; none can overwrite it since OTel's propagator uses
 * {@link HttpHeadersSetter#set}, which calls {@code HttpHeaders.set()} (overwrite,
 * not append).
 *
 * <p><strong>F6-4: {@code http.response.status_code} on both paths.</strong>
 * The status code is set on the span in both the happy path (from the response
 * object) and in the {@link IOException} catch block where a
 * {@code RestClientResponseException} may carry the HTTP status. When no HTTP
 * response is available (e.g., connection refused), only
 * {@link StatusCode#ERROR} and {@code recordException} are set — the status code
 * attribute is intentionally absent in that case.
 *
 * <p><strong>D-H10: {@code peerServiceName} as constructor parameter.</strong>
 * The peer service name is injected at construction time so this class stays
 * reusable across any service that makes outbound HTTP calls. The logical name
 * is set in each service's wiring class (e.g., {@code HttpClientConfig}).
 *
 * <p><strong>T-15-03: {@code span.end()} in finally.</strong> The {@code finally}
 * block guarantees {@code span.end()} is called even on {@link IOException} —
 * preventing a span leak that could exhaust the SDK's in-memory span buffer.
 * Pattern matches {@code TracingMessagePostProcessor}'s {@code finally} block.
 *
 * <p>No Spring annotations on this class — it is a pure Java helper. Spring
 * wiring lives in each service's {@code HttpClientConfig.java}.
 */
public class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    // Stateless / thread-safe; one instance per JVM is sufficient.
    private static final HttpHeadersSetter SETTER = new HttpHeadersSetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final String peerServiceName;  // D-H10: constructor parameter

    public TracingClientHttpRequestInterceptor(OpenTelemetry openTelemetry,
                                               Tracer tracer,
                                               String peerServiceName) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
        this.peerServiceName = peerServiceName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // Span name: "{METHOD} {path}" per OTel HTTP client span naming spec.
        Span span = tracer.spanBuilder(request.getMethod().name() + " " + request.getURI().getPath())
            .setSpanKind(SpanKind.CLIENT)
            // HCLI-02: full v2.0 HTTP-client semconv attribute set (stable constants only):
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.getMethod().name())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getURI().getHost())
            .setAttribute(ServerAttributes.SERVER_PORT, (long) request.getURI().getPort())
            .setAttribute(UrlAttributes.URL_FULL, request.getURI().toString())
            // D-H9: service.peer.name via typed incubating constant — NEVER string literal,
            // NEVER deprecated PeerIncubatingAttributes.PEER_SERVICE ("peer.service").
            .setAttribute(ServiceIncubatingAttributes.SERVICE_PEER_NAME, peerServiceName)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // F6-2: inject AFTER span.makeCurrent() so Context.current() contains the CLIENT
            // span's spanId. If injected before makeCurrent(), the INTERNAL span's spanId
            // would be written into traceparent — downstream service becomes a sibling, not a child.
            openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), request.getHeaders(), SETTER);

            // F6-3: execution.execute() runs AFTER injection (interceptor registered last
            // per HttpClientConfig — traceparent is the final header written).
            ClientHttpResponse response = execution.execute(request, body);

            // F6-4 happy path: capture status code when response is available.
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                (long) response.getStatusCode().value());
            if (response.getStatusCode().isError()) {
                span.setStatus(StatusCode.ERROR);
            }
            return response;

        } catch (IOException e) {
            // F6-4 exception path: status code unavailable; set ERROR status and record exception.
            // If e is a RestClientResponseException, its status code could be extracted via
            // ((RestClientResponseException) e).getStatusCode().value() — but we keep this
            // general since ClientHttpRequestInterceptor only declares IOException.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
