package com.example.producer.config;

import com.example.otel.http.TracingClientHttpRequestInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Producer-service HTTP client wiring — parallel to {@link RabbitConfig} for AMQP.
 *
 * <p><strong>D-H8:</strong> Creates {@link TracingClientHttpRequestInterceptor} as a {@code @Bean}
 * (passing {@code OpenTelemetry}, {@code Tracer}, and the static {@code peerServiceName}), then
 * produces a {@link RestClient.Builder} {@code @Bean} with the interceptor registered.
 * Attendees who understood AMQP wiring ({@link RabbitConfig}) recognize the same pattern for HTTP.
 *
 * <p><strong>F6-1 mitigation:</strong> Defining {@code @Bean RestClient.Builder} here supersedes
 * Spring Boot's {@code @ConditionalOnMissingBean} auto-configured builder. Any {@code OrderService}
 * that injects {@code RestClient.Builder} receives this builder — the OTel interceptor is
 * ALWAYS registered. {@code RestClient.create(url)} bypasses this entirely and is prohibited.
 *
 * <p><strong>F6-3:</strong> The OTel interceptor is registered LAST via
 * {@code .requestInterceptor(tracingInterceptor)}. If additional interceptors exist (e.g., auth),
 * add them BEFORE this call so {@code traceparent} is the final header written.
 */
@Configuration
public class HttpClientConfig {

    // D-H9: static descriptive peer service name — teaches the production pattern.
    // Even though the stub is in-process, the attribute demonstrates how production
    // systems name their remote dependencies for Tempo's service graph.
    private static final String PEER_SERVICE_NAME = "notification-service";

    /**
     * D-H8: Creates the interceptor as a {@code @Bean} so it is injectable and testable.
     * Constructor receives {@code OpenTelemetry} + {@code Tracer} from
     * {@code OtelSdkConfiguration} — same pattern as
     * {@code RabbitConfig.tracingMessagePostProcessor}.
     */
    @Bean
    TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(
            OpenTelemetry openTelemetry, Tracer tracer) {
        return new TracingClientHttpRequestInterceptor(openTelemetry, tracer, PEER_SERVICE_NAME);
    }

    /**
     * D-H8: Explicit {@link RestClient.Builder} {@code @Bean} overrides Spring Boot's
     * auto-configured {@code @ConditionalOnMissingBean} PROTOTYPE builder.
     * The explicit singleton builder is sufficient for this demo (one {@code OrderService}
     * injects once at startup — no per-request cloning needed).
     */
    @Bean
    RestClient.Builder restClientBuilder(
            TracingClientHttpRequestInterceptor tracingInterceptor) {
        return RestClient.builder()
            // F6-3: OTel interceptor registered LAST so traceparent is the final header set.
            // If additional interceptors exist (e.g., auth, correlation-id), add them BEFORE
            // this call.
            .requestInterceptor(tracingInterceptor);
    }
}
