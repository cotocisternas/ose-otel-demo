---
id: 04-02-producer-counter
phase: 04-metrics
plan: 02
type: execute
wave: 2
depends_on: [04-01-meter-pipeline-refactor]
requirements: [METRIC-02]
requirements_addressed: [METRIC-02]
files_modified:
  - producer-service/src/main/java/com/example/producer/domain/OrderService.java
autonomous: true
objective: "Add the LongCounter `orders.created` (METRIC-02) to OrderService.place(...): constructor-inject Meter (sibling to existing Tracer), build the counter once as a final field, and increment it BETWEEN `publisher.publish(orderId, payload)` and `return orderId` — INSIDE the existing INTERNAL span body (D-08), with a single business attribute `order.priority` read from `payload.get(\"priority\")` with `\"standard\"` fallback (D-09). The catch block is UNCHANGED — failure does NOT increment the counter (METRIC-02 is `orders.created`, not `orders.attempted`)."
must_haves:
  truths:
    - "OrderService constructor takes (OrderPublisher, Tracer, Meter) — Meter is the new third parameter (D-08)"
    - "OrderService has a `private final LongCounter ordersCreated` field built ONCE in the constructor via `meter.counterBuilder(\"orders.created\")` (D-08 — instruments are built once, not per-request)"
    - "Counter description and unit set on the builder: `.setDescription(...)` (workshop-readable) and `.setUnit(\"1\")` (semconv: dimensionless)"
    - "Counter call site lives inside the existing `try (Scope scope = span.makeCurrent())` block, AFTER `publisher.publish(orderId, payload)` returns and BEFORE `return orderId` (D-08) — successful completion of business logic + AMQP send is what increments"
    - "Counter call site reads `payload.get(\"priority\")` with `\"standard\"` fallback: `String priority = String.valueOf(Optional.ofNullable(payload.get(\"priority\")).orElse(\"standard\"));` (D-09)"
    - "Counter increment uses string-literal AttributeKey (D-09 — `order.priority` is NOT in OTel semconv): `ordersCreated.add(1, Attributes.of(AttributeKey.stringKey(\"order.priority\"), priority));`"
    - "Phase 2's catch block (recordException + setStatus(ERROR) + throw) is UNCHANGED — counter does NOT fire on the failure path (D-08 rationale: METRIC-02 is `orders.created`, not `orders.attempted`)"
    - "Producer-only counter scope (D-11) — consumer-service hosts NO `orders.processed`/`orders.failed` mirror counter; METRIC-01..04 are exhausted by Counter (producer) + Histogram (producer) + ObservableGauge (consumer); a consumer-side mirror counter would be scope creep deferred to Phase 7 if attendee feedback flags it"
    - "Phase 2's INTERNAL span body shape preserved: spanBuilder + startSpan + try(Scope)/catch/finally span.end() — no helper, no AOP, pure inline (Phase 2 D-01 carryforward)"
    - "Class JavaDoc updated to add a Phase 4 paragraph documenting the Counter call-site (parallel to how OrderPublisher and ProcessingService got their Phase 3 JavaDoc updates) — names METRIC-02, the success-path-only semantic, the order.priority business attribute, and the Mimir name mangling (orders.created → orders_created_total)"
    - "Imports added: java.util.Optional, io.opentelemetry.api.common.AttributeKey, io.opentelemetry.api.common.Attributes, io.opentelemetry.api.metrics.LongCounter, io.opentelemetry.api.metrics.Meter"
    - "mvn -pl producer-service compile exits 0 (this plan plus Plan 04-03 unblocks producer-service compile after Plan 04-01's factory-arity change to HttpServerSpanFilter)"
  artifacts:
    - path: "producer-service/src/main/java/com/example/producer/domain/OrderService.java"
      provides: "OrderService.place(...) gains a LongCounter `orders.created` increment between publisher.publish and return orderId, inside the existing INTERNAL span body, with order.priority business attribute (METRIC-02 / D-08 / D-09)"
      contains: "ordersCreated.add(1, Attributes.of"
  key_links:
    - from: "OrderService constructor"
      to: "Meter @Bean (created in Plan 04-01, scope com.example.producer)"
      via: "Spring constructor injection"
      pattern: "OrderService\\([^)]*Meter meter"
    - from: "ordersCreated.add(1, attrs)"
      to: "Mimir / Prometheus exporter (via SdkMeterProvider → PeriodicMetricReader 10s → OTLP gRPC :4317 → otel-lgtm)"
      via: "OTel-to-Prometheus name mangling: 'orders.created' counter -> 'orders_created_total' time series with order_priority label"
      pattern: "ordersCreated\\.add"
    - from: "payload.get(\"priority\")"
      to: "AttributeKey.stringKey(\"order.priority\") on the Counter increment"
      via: "Optional.ofNullable(...).orElse(\"standard\") fallback (D-09)"
      pattern: "order\\.priority"
---

<objective>
Add a `LongCounter` named `orders.created` (METRIC-02) to the producer service's `OrderService.place(...)` business logic. The counter increments ONCE per successful order — strictly after `publisher.publish(orderId, payload)` returns and strictly before `return orderId`, INSIDE the existing INTERNAL span body that Phase 2 wrapped around the method.

The counter carries ONE business-level attribute: `order.priority`, read from the request payload's `"priority"` field with `"standard"` as the fallback when omitted (D-09). The attribute key is a string-literal `AttributeKey.stringKey("order.priority")` because `order.priority` is **not** in the OTel semconv catalog — by contrast, Plan 04-03's histogram uses semconv constants (`HttpAttributes.HTTP_REQUEST_METHOD`, etc.). The contrast between the two attribute-key choices is the workshop's lesson on "use semconv when it exists; literal attribute keys for app-specific business dimensions."

The Phase 2 catch block (recordException + setStatus(ERROR) + throw) is UNCHANGED. The counter does NOT increment on the failure path — METRIC-02 is `orders.created`, not `orders.attempted`. Failure is visible via the trace's ERROR status.

Pedagogical parallel: the Counter call site sits adjacent to the already-active INTERNAL span. Workshop attendees read both signals being produced from a single block of business code — `tracer.spanBuilder(...)` and `ordersCreated.add(1, attrs)` from a few lines apart in the same `try(Scope)` block.

Purpose: METRIC-02 satisfied. Plan 04-05 verifies live (issue POST /orders, see `orders_created_total{order_priority="express"}` increment in Mimir within 15 seconds — ROADMAP SC #1).

Output: 1 modified file (`producer-service/src/main/java/com/example/producer/domain/OrderService.java`).
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
@.planning/phases/03-amqp-context-propagation/03-CONTEXT.md
@CLAUDE.md
@producer-service/src/main/java/com/example/producer/domain/OrderService.java
@producer-service/src/main/java/com/example/producer/api/OrderController.java
@producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java

<interfaces>
<!-- Key types this plan uses. All BOM-managed via opentelemetry-bom:1.61.0. -->

From io.opentelemetry.api.metrics:
```java
public interface Meter {
    LongCounterBuilder counterBuilder(String name);
}
public interface LongCounterBuilder {
    LongCounterBuilder setDescription(String description);
    LongCounterBuilder setUnit(String unit);
    LongCounter build();
}
public interface LongCounter {
    void add(long value, Attributes attributes);
    void add(long value);   // unused — we always pass attrs
}
```

From io.opentelemetry.api.common:
```java
public final class AttributeKey<T> {
    public static AttributeKey<String> stringKey(String key);
    public static AttributeKey<Long> longKey(String key);
    // ...
}
public interface Attributes {
    static Attributes of(AttributeKey<T> k1, T v1);
    // overloads for 2/4/6/8/10 keys exist; we use the single-key form
}
```

From OrderController (existing — DO NOT modify):
```java
@PostMapping
public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
    String orderId = orderService.place(payload);
    return ResponseEntity.accepted().body(Map.of("orderId", orderId));
}
// payload comes from Spring's Jackson deserialization of the request body.
// payload.get("priority") returns Object — could be null (key missing), a String,
// or any JSON type. The String.valueOf(Optional.ofNullable(...).orElse("standard"))
// idiom in D-09 handles all three cleanly: null → "standard"; "express" (String) → "express";
// 42 (Number) → "42" (still a valid attribute value, unlikely in practice).
```

OrderService current shape (the file being modified — see read_first):
```java
public class OrderService {
    private final OrderPublisher publisher;
    private final Tracer tracer;

    public OrderService(OrderPublisher publisher, Tracer tracer) { ... }

    public String place(Map<String, Object> payload) {
        Span span = tracer.spanBuilder("OrderService.place")
            .setSpanKind(SpanKind.INTERNAL).startSpan();
        try (Scope scope = span.makeCurrent()) {
            String orderId = UUID.randomUUID().toString();
            publisher.publish(orderId, payload);
            return orderId;     // <-- counter increment slots here, BEFORE this return
        } catch (RuntimeException e) {
            span.recordException(e); span.setStatus(StatusCode.ERROR); throw e;
        } finally {
            span.end();
        }
    }
}
```
</interfaces>
</context>

<tasks>

<task id="04-02-T1" type="auto">
  <name>Task 1: Add LongCounter `orders.created` to OrderService — constructor-inject Meter, build counter as final field, increment between publisher.publish and return orderId</name>
  <files>producer-service/src/main/java/com/example/producer/domain/OrderService.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/domain/OrderService.java (current state at HEAD — Phase 2's INTERNAL span body shape; Phase 3 left this file's catch shape unchanged; this is the modification target)
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 336-417 — exact target shape including the field/constructor extension at lines 354-372 and the counter call-site at lines 376-404)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-08 call site between publish and return INSIDE the INTERNAL span body, D-09 order.priority attribute with "standard" fallback)
    - .planning/phases/04-metrics/04-01-meter-pipeline-refactor-PLAN.md (Plan 04-01 produces the Meter @Bean with scope com.example.producer that this plan injects)
    - producer-service/src/main/java/com/example/producer/api/OrderController.java (current state — confirms payload is Map<String, Object> from Spring's Jackson JSON binding; payload.get("priority") returns Object that can be null)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (D-01 pure-inline span template — Phase 4 must NOT introduce any helper or AOP; the Counter line slots BETWEEN existing lines, doesn't replace them)
  </read_first>
  <action>
    Modify `producer-service/src/main/java/com/example/producer/domain/OrderService.java` IN PLACE. This is purely additive — no Phase 2 lines are deleted, no behavior changes for the trace pipeline, the catch shape is unchanged.

    **Edit 1 — Imports.** ADD these imports below the existing imports (preserve the file's existing grouping pattern: java.* first, then io.opentelemetry.*, then org.springframework, then com.example):

    ```java
    import java.util.Optional;
    ```
    ```java
    import io.opentelemetry.api.common.AttributeKey;
    import io.opentelemetry.api.common.Attributes;
    import io.opentelemetry.api.metrics.LongCounter;
    import io.opentelemetry.api.metrics.Meter;
    ```

    Do NOT remove any existing import. Existing imports include: `java.util.Map`, `java.util.UUID`, `io.opentelemetry.api.trace.Span`, `io.opentelemetry.api.trace.SpanKind`, `io.opentelemetry.api.trace.StatusCode`, `io.opentelemetry.api.trace.Tracer`, `io.opentelemetry.context.Scope`, `org.springframework.stereotype.Service`, `com.example.producer.messaging.OrderPublisher`.

    **Edit 2 — Update class-level JavaDoc.** Phase 2's JavaDoc (lines 16-22) ends with "do NOT extract this into a helper." ADD a new Phase 4 paragraph after that sentence:

    ```java
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
     */
    ```

    **Edit 3 — Field & constructor extension.** Replace the existing field/constructor block:

    ```java
    // Before:
    public class OrderService {
        private final OrderPublisher publisher;
        private final Tracer tracer;

        public OrderService(OrderPublisher publisher, Tracer tracer) {
            this.publisher = publisher;
            this.tracer = tracer;
        }
    ```

    With (Meter param appended; LongCounter built ONCE as a final field — D-08 instruments-are-built-once-not-per-request):

    ```java
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
    ```

    **Edit 4 — Counter call site inside `place(Map)`.** The current `place(Map)` body has this shape (Phase 2 INTERNAL span; Phase 3 left it unchanged):

    ```java
    Span span = tracer.spanBuilder("OrderService.place")
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan();
    try (Scope scope = span.makeCurrent()) {
        String orderId = UUID.randomUUID().toString();
        publisher.publish(orderId, payload);
        return orderId;
    } catch (RuntimeException e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR);
        throw e;
    } finally {
        span.end();
    }
    ```

    INSERT the counter increment between `publisher.publish(orderId, payload);` and `return orderId;`. The new try-block body becomes:

    ```java
    try (Scope scope = span.makeCurrent()) {
        String orderId = UUID.randomUUID().toString();
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
    ```

    **Constraint preservation checklist (verify after editing):**
    - The Phase 2 INTERNAL span body shape is intact: `Span span = tracer.spanBuilder("OrderService.place").setSpanKind(SpanKind.INTERNAL).startSpan();` followed by `try (Scope scope = span.makeCurrent()) { ... } catch (RuntimeException e) { recordException; setStatus(ERROR); throw } finally { span.end() }` — no helper, no AOP (Phase 2 D-01).
    - The catch block content is byte-for-byte identical to Phase 2 / Phase 3 (D-08).
    - The counter increment is INSIDE the `try (Scope scope = span.makeCurrent())` block (D-08 — INSIDE the INTERNAL span scope).
    - The counter increment is AFTER `publisher.publish(...)` and BEFORE `return orderId` (D-08).
    - The Counter is built ONCE in the constructor (final field), not per-request.
    - Spring `@Service` annotation on the class is unchanged.
    - `String orderId = UUID.randomUUID().toString();` line is unchanged.
    - `publisher.publish(orderId, payload);` line is unchanged.
  </action>
  <acceptance_criteria>
    - File exists: `test -f producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - New imports present: `for i in 'import java.util.Optional;' 'import io.opentelemetry.api.common.AttributeKey;' 'import io.opentelemetry.api.common.Attributes;' 'import io.opentelemetry.api.metrics.LongCounter;' 'import io.opentelemetry.api.metrics.Meter;'; do grep -qF "$i" producer-service/src/main/java/com/example/producer/domain/OrderService.java || exit 1; done`
    - Existing imports preserved (regression check): `for i in 'import java.util.Map;' 'import java.util.UUID;' 'import io.opentelemetry.api.trace.Span;' 'import io.opentelemetry.api.trace.SpanKind;' 'import io.opentelemetry.api.trace.StatusCode;' 'import io.opentelemetry.api.trace.Tracer;' 'import io.opentelemetry.context.Scope;' 'import com.example.producer.messaging.OrderPublisher;'; do grep -qF "$i" producer-service/src/main/java/com/example/producer/domain/OrderService.java || exit 1; done`
    - Constructor takes Meter as third parameter (D-08): `grep -qE 'public OrderService\(OrderPublisher publisher, Tracer tracer, Meter meter\)' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - LongCounter field declared as final: `grep -qE 'private final LongCounter ordersCreated' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter built in constructor with name "orders.created" (METRIC-02): `grep -qF 'meter.counterBuilder("orders.created")' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter has setDescription and setUnit("1"): `grep -qF '.setDescription(' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF '.setUnit("1")' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter increment line exists with order.priority attribute (D-09): `grep -qF 'ordersCreated.add(1, Attributes.of(' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'AttributeKey.stringKey("order.priority")' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Priority fallback uses Optional.ofNullable + "standard" (D-09): `grep -qF 'Optional.ofNullable(payload.get("priority")).orElse("standard")' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter increment is INSIDE the try(Scope) block AND BEFORE the return statement (line-order check): `awk '/try \(Scope scope = span.makeCurrent\(\)\)/{intry=1} /ordersCreated\.add\(1/{seen_add=NR; if (!intry || seen_return) exit 1} /return orderId;/{if (!seen_add) exit 1; seen_return=1; intry=0} END {exit (seen_add && seen_return) ? 0 : 1}' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter increment is AFTER `publisher.publish` (line-order check): `awk '/publisher\.publish\(orderId, payload\);/{seen_pub=NR} /ordersCreated\.add\(1/{if (!seen_pub) exit 1; exit 0}' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Phase 2 INTERNAL span shape preserved: `grep -qF 'tracer.spanBuilder("OrderService.place")' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'setSpanKind(SpanKind.INTERNAL)' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'startSpan()' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Phase 2 catch shape unchanged (D-08 — counter does NOT fire on failure): `grep -qF 'catch (RuntimeException e)' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'span.recordException(e)' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'span.setStatus(StatusCode.ERROR)' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'throw e;' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'span.end();' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter call site does NOT appear in the catch block (regression — D-08): `awk '/catch \(RuntimeException e\)/{incatch=1} /\}/{if (incatch) incatch=0} /ordersCreated\.add/{if (incatch) exit 1} END {exit 0}' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Phase 4 JavaDoc paragraph added: `grep -qF 'METRIC-02' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'orders_created_total' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - @Service annotation preserved: `grep -qE '^@Service' producer-service/src/main/java/com/example/producer/domain/OrderService.java`
    - Counter is built ONCE (constructor, final field) — NOT per-request: `grep -c 'meter.counterBuilder' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns exactly 1
  </acceptance_criteria>
  <verify>
    <automated>grep -qF 'private final LongCounter ordersCreated' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'meter.counterBuilder("orders.created")' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'ordersCreated.add(1, Attributes.of(' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'AttributeKey.stringKey("order.priority")' producer-service/src/main/java/com/example/producer/domain/OrderService.java && grep -qF 'Optional.ofNullable(payload.get("priority")).orElse("standard")' producer-service/src/main/java/com/example/producer/domain/OrderService.java && [ "$(grep -c 'meter.counterBuilder' producer-service/src/main/java/com/example/producer/domain/OrderService.java)" -eq 1 ] && awk '/publisher\.publish\(orderId, payload\);/{seen_pub=NR} /ordersCreated\.add\(1/{if (!seen_pub) exit 1; exit 0}' producer-service/src/main/java/com/example/producer/domain/OrderService.java</automated>
  </verify>
  <done>OrderService.java extended: constructor takes (OrderPublisher, Tracer, Meter); LongCounter ordersCreated built once in constructor with name "orders.created", description, unit "1"; counter increments inside the existing INTERNAL span body BETWEEN publisher.publish and return orderId, with `order.priority` attribute from payload (fallback "standard"); catch block UNCHANGED — counter does NOT fire on failure; class JavaDoc updated with Phase 4 paragraph naming METRIC-02 and the orders_created_total Mimir mapping.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 04-02 — producer Counter)

| Boundary | Description |
|----------|-------------|
| HTTP client → POST /orders → OrderController → OrderService.place | Existing Phase 1 boundary; Phase 4 reads `payload.get("priority")` from this user-controlled input. |
| OrderService → SdkMeterProvider → OTLP gRPC :4317 | Already crossed by Plan 04-01's metric pipeline; counter increment flows through the same export queue. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-02-01 | Denial of Service (cardinality explosion) | The `order.priority` attribute is read from user-controlled `payload.get("priority")` — an attacker could spam `POST /orders` with a unique priority value per request, blowing up the Mimir time-series count for `orders_created_total{order_priority=...}` | mitigate (workshop scope) | Workshop runs on localhost with `mise demo:order` sending only `"express"` / `"standard"` / omitted (D-10). For production, a downstream value-allowlist (e.g., normalize anything outside `{express, standard}` to `"other"`) would be the standard mitigation. Plan 04-05's README delta names the cardinality-awareness lesson explicitly so attendees know this is a real-world concern, not a workshop bug. Severity: medium for production; low for the workshop's localhost demo. |
| T-04-02-02 | Information Disclosure | The Counter increment writes `order.priority` to Mimir — if this attribute could carry PII (e.g., a customer name), Mimir would persist it | accept | The `priority` field of the request payload is by domain-design a low-cardinality classifier (express/standard); never PII. The workshop's `demo:order` task hardcodes the values; no PII flows in. Severity: low. |
| T-04-02-03 | Tampering | An attacker calling POST /orders with a `priority` value that triggers a JSON-injection downstream | accept | The value is only used as an OTel attribute (a typed AttributeKey value), not as an untrusted string in any SQL/HTML/log/template/eval surface. The String.valueOf coercion handles non-string JSON types defensively. Severity: low. |
| T-04-02-04 | Repudiation | Counter increments lost during graceful shutdown if SdkMeterProvider doesn't flush | mitigate | Plan 04-01's `@Bean(destroyMethod = "close")` cascade flushes the metric export queue at JVM exit. Verified by Plan 04-05's runtime smoke test (Ctrl-C produces a final metric scrape). Severity: low. |

**Phase scope:** One additive line in business code + one constructor-injected dependency. The cardinality concern (T-04-02-01) is the only non-trivial item; mitigation is pedagogy (the README delta) since this is a workshop demo.
</threat_model>

<verification>
- OrderService.java: constructor takes (OrderPublisher, Tracer, Meter) and builds the LongCounter ordersCreated once with name="orders.created", description set, unit="1".
- Counter increment lives inside the try(Scope) block, AFTER publisher.publish and BEFORE return orderId — line-order verified.
- Counter increment uses AttributeKey.stringKey("order.priority") with value from `Optional.ofNullable(payload.get("priority")).orElse("standard")` (D-09).
- Phase 2 INTERNAL span body shape preserved (spanBuilder, setSpanKind(INTERNAL), startSpan, try/catch/finally, span.end). Phase 2 catch shape (recordException + setStatus(ERROR) + throw) unchanged — counter does NOT fire on failure (D-08).
- Class JavaDoc updated with Phase 4 paragraph naming METRIC-02 and the orders_created_total mapping.
- @Service annotation preserved.
- The Counter is built once (final field initialized in constructor) — `grep -c 'meter.counterBuilder' = 1`.
</verification>

<success_criteria>
- METRIC-02 (LongCounter `orders.created` with `order.priority` business attribute) satisfied at the source level. Live verification (orders_created_total visible in Mimir within 15s of POST /orders) is Plan 04-05's gate.
- D-08 (call site between publish and return, INSIDE INTERNAL span body, NOT in catch), D-09 (order.priority with "standard" fallback, string-literal AttributeKey), D-11 (producer-only counter — no consumer mirror) honored.
- Phase 2 D-01 (pure-inline span template) preserved — no helper, no AOP, no @WithSpan.
</success_criteria>

<output>
After completion, create `.planning/phases/04-metrics/04-02-SUMMARY.md` documenting:
- Final shape of OrderService.java place(...) body — paste the try(Scope) block showing publisher.publish → counter increment → return orderId.
- Final shape of OrderService constructor — paste the (OrderPublisher, Tracer, Meter) signature + the meter.counterBuilder block.
- Confirmation that the catch block is unchanged from Phase 2 / Phase 3 (paste the catch block).
- mvn -pl producer-service compile output — note that compile may still fail with the HttpServerSpanFilter constructor-arity error from Plan 04-01 if Plan 04-03 has not yet landed; that is expected.
- Files modified: 1 (OrderService.java); 0 new files; 0 new pom dependencies.
</output>
</content>
</invoke>