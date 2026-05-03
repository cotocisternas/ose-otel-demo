package com.example.producer.domain;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.springframework.stereotype.Service;

import com.example.producer.messaging.OrderPublisher;

/**
 * Domain layer — orchestrates the place-an-order flow.
 *
 * Phase 2 wraps {@link #place(Map)} in an INTERNAL span (TRACE-06) named
 * "OrderService.place" using the D-01 pure-inline template — boilerplate
 * is the lesson here; do NOT extract this into a helper.
 *
 * <p><b>Phase 4 adds the {@code orders.created} {@link LongCounter}
 * (METRIC-02).</b> The counter increments ONCE per successful order,
 * AFTER {@code publisher.publish(...)} returns and BEFORE
 * {@code return orderId} — inside the existing INTERNAL span scope so
 * the trace and the metric are emitted from adjacent SDK calls. The
 * {@code catch (RuntimeException)} block does NOT fire the counter:
 * METRIC-02 is {@code orders.created}, not {@code orders.attempted}.
 * Failure is visible via the trace's ERROR status, not as a metric.
 *
 * <p>The counter carries one business attribute, {@code order.priority},
 * read from the request payload with {@code "standard"} as the fallback
 * (D-09). {@code order.priority} is NOT in the OTel semconv catalog — so
 * we use a string-literal {@link AttributeKey#stringKey(String)}; this
 * contrasts with {@code HttpServerSpanFilter}'s histogram which uses
 * {@code HttpAttributes.HTTP_REQUEST_METHOD} (semconv-stable). The
 * Prometheus exporter mangles dots to underscores and appends
 * {@code _total} for monotonic counters: this surfaces in Mimir as
 * {@code orders_created_total{order_priority="express"}}.
 *
 * <p><b>Phase 11 adds the WIDGET-SLOW SKU branch (D-T5/D-T6).</b> If the
 * request's sku is "WIDGET-SLOW", {@link #place(Map)} {@link Thread#sleep}s
 * 1500ms inside the INTERNAL span scope. The resulting trace duration > 1s
 * triggers the Collector's tail_sampling keep-slow latency policy (TSAMP-01).
 * No new architecture — mirrors the WIDGET-EXPRESS / WIDGET-STANDARD /
 * WIDGET-IDEMPOTENT / WIDGET-BURST SKU family pattern. Driven by
 * scripts/load.sh's SLOW_RPS stream (D-T7); workshop-safe range 0–5 (above 5,
 * raise oha -c proportionally to avoid Tomcat thread starvation — Tomcat's
 * default server.tomcat.threads.max=200 caps concurrent slow requests).
 */
@Service
public class OrderService {
    private final OrderPublisher publisher;
    private final Tracer tracer;
    private final LongCounter ordersCreated;

    public OrderService(OrderPublisher publisher, Tracer tracer, Meter meter) {
        this.publisher = publisher;
        this.tracer = tracer;
        // Counter "orders.created" — METRIC-02 locked. Built once here and
        // reused across every place(...) call. The OTel SDK's instrument
        // resolution machinery is keyed on instrument identity; building
        // per-request would defeat caching AND is structurally wrong per
        // the OTel API contract (the same instrument name + scope must
        // resolve to the same handle).
        //
        // The OTel-to-Prometheus exporter (in otel-lgtm's collector) maps
        // dot-namespaced names to underscore-namespaced ones and appends
        // "_total" for monotonic counters — so this surfaces in Mimir as
        // `orders_created_total`. Plan 04-05's README delta names this
        // mapping explicitly so attendees aren't surprised by the rename.
        this.ordersCreated = meter.counterBuilder("orders.created")
            .setDescription("Successful POST /orders -> publish completions")
            .setUnit("1")
            .build();
    }

    public String place(Map<String, Object> payload) {
        // ---- D-01 inline span template (INTERNAL) ----
        //
        // Pure inline. No helper. No AOP. The full try/Scope/try/catch/finally
        // idiom is REPEATED at every span site (5 sites in Phase 2 across
        // both services) so workshop attendees read the SDK calls in business
        // code and understand exactly which lines do what.
        //
        // INTERNAL span name follows the D-04 convention: ClassName.method.
        // No semconv attributes mandated for INTERNAL spans (D-04) — those
        // belong on SERVER / CLIENT / PRODUCER / CONSUMER kinds where the
        // OTel spec defines stable attribute keys.
        Span span = tracer.spanBuilder("OrderService.place")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            String orderId = UUID.randomUUID().toString();

            // ---- Phase 11 D-T5/D-T6: WIDGET-SLOW SKU branch (TSAMP-01 latency policy) ----
            //
            // If the request's sku is "WIDGET-SLOW", sleep 1500ms inside this
            // INTERNAL span scope. The 1500ms span duration > the Collector's
            // tail_sampling latency.threshold_ms=1000, so the keep-slow
            // sub-policy fires reliably (50% buffer above threshold). Tail-
            // sampling's latency policy uses MAX-span duration of the
            // assembled trace — the producer-side sleep alone is sufficient;
            // the consumer's CONSUMER+INTERNAL spans stay fast.
            //
            // Mirrors the WIDGET-EXPRESS / WIDGET-STANDARD / WIDGET-IDEMPOTENT /
            // WIDGET-BURST family pattern (zero new architecture). Driven from
            // scripts/load.sh's SLOW_RPS stream (D-T7) at ~2 rps default.
            //
            // See README §11 (Phase 11) for the workshop walkthrough; the
            // F2-3 head-vs-tail double-filter callout at the end of §11
            // explains why the SDK sampler stays parentBased(alwaysOn()) here.
            if ("WIDGET-SLOW".equals(payload.get("sku"))) {
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("WIDGET-SLOW sleep interrupted", ie);
                }
            }

            publisher.publish(orderId, payload);

            // ---- Phase 4 D-08 / D-09: orders.created Counter (METRIC-02) ----
            //
            // Fires AFTER publisher.publish returns successfully. Inside the
            // INTERNAL span scope so the trace and the metric increment are
            // emitted from adjacent SDK calls — workshop attendees read both
            // signals being produced in one spot.
            //
            // The catch block below does NOT fire the counter: METRIC-02 is
            // `orders.created`, not `orders.attempted`. Failures are visible
            // via the trace's ERROR status (recordException + setStatus(ERROR)
            // in the catch), not as a metric increment. If a future workshop
            // wants an `orders.attempted` counter, that's a parallel addition
            // in the catch — outside Phase 4's scope.
            //
            // `order.priority` is NOT in the OTel semconv catalog (semconv 1.40.0
            // covers HTTP, RPC, messaging, database — but not order-management
            // business attributes). We use a string-literal AttributeKey here.
            // Contrast with the histogram in HttpServerSpanFilter that uses
            // HttpAttributes.HTTP_REQUEST_METHOD — semconv constants where they
            // exist, literals for app-specific business dimensions.
            //
            // The Optional.ofNullable + String.valueOf idiom handles three cases:
            //   - null (priority key missing in payload): orElse("standard")
            //   - String (e.g. "express" / "standard"): String.valueOf is a no-op
            //   - any other JSON type (Number, Boolean): String.valueOf coerces
            // Workshop demo:order task always sends one of "express" / "standard"
            // / omitted, so the third branch is theoretical (D-10).
            String priority = String.valueOf(
                Optional.ofNullable(payload.get("priority")).orElse("standard"));
            ordersCreated.add(1, Attributes.of(
                AttributeKey.stringKey("order.priority"), priority));

            return orderId;
        } catch (RuntimeException e) {
            // D-03 catch — UNCHANGED from Phase 2 / Phase 3. Counter does NOT
            // fire on this path (D-08 rationale above). The recordException +
            // setStatus(ERROR) pattern records the failure ON THE TRACE.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
