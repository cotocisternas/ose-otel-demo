---
phase: 04-metrics
plan: "02"
subsystem: metrics
tags: [metrics, otel-sdk, counter, producer, order-service, long-counter]
dependency_graph:
  requires:
    - phase: 04-metrics/04-01
      provides: [meter-bean-producer]
  provides:
    - orders.created LongCounter increment on success path in OrderService.place
  affects: [04-03, 04-04, 04-05]
tech_stack:
  added: [LongCounter, Meter (injected), AttributeKey.stringKey]
  patterns: [instrument-built-once-in-constructor, success-path-only-counter, business-attribute-string-literal]
key_files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/domain/OrderService.java
key_decisions:
  - "Counter built ONCE as final field in constructor (D-08 instrument-identity contract) — not per-request"
  - "order.priority uses string-literal AttributeKey.stringKey (not semconv) — intentional contrast with histogram's HttpAttributes.HTTP_REQUEST_METHOD in Plan 04-03"
  - "Counter fires ONLY on success path (AFTER publisher.publish, BEFORE return orderId) — D-08 rationale: METRIC-02 is orders.created, not orders.attempted"
  - "Optional.ofNullable + String.valueOf idiom handles null/String/Number JSON types for priority attribute (D-09)"
patterns-established:
  - "Success-path-only counter: instrument increments inside try(Scope) block AFTER business logic completes, BEFORE return"
  - "Business attribute with standard fallback: String.valueOf(Optional.ofNullable(payload.get(key)).orElse(defaultValue))"
requirements-completed: [METRIC-02]
duration: 2min
completed: "2026-05-01"
---

# Phase 4 Plan 02: Producer Counter Summary

**LongCounter `orders.created` (METRIC-02) added to OrderService.place — increments once per successful order inside the existing INTERNAL span scope, with `order.priority` business attribute (fallback "standard"), surfacing in Mimir as `orders_created_total{order_priority="express"}`**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-01T22:29:50Z
- **Completed:** 2026-05-01T22:32:01Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Constructor-injected `Meter` as third parameter in `OrderService(OrderPublisher, Tracer, Meter)` — Spring auto-wires the `@Bean Meter meter(OpenTelemetry)` produced by Plan 04-01
- `LongCounter ordersCreated` built ONCE as a `private final` field in the constructor — instrument name `"orders.created"`, description set, unit `"1"` (semconv: dimensionless)
- Counter incremented AFTER `publisher.publish(orderId, payload)` returns and BEFORE `return orderId` — strictly inside the existing `try (Scope scope = span.makeCurrent())` block
- Phase 2 catch block (recordException + setStatus(ERROR) + throw) preserved byte-for-byte — counter does NOT fire on failure path
- Phase 4 JavaDoc paragraph added naming METRIC-02 and the `orders_created_total` Mimir name mapping

## Task Commits

Each task was committed atomically:

1. **Task 1: Add LongCounter orders.created to OrderService** - `8beaa59` (feat)

## Final Code Shapes

### Constructor (field + builder)

```java
public class OrderService {
    private final OrderPublisher publisher;
    private final Tracer tracer;
    private final LongCounter ordersCreated;

    public OrderService(OrderPublisher publisher, Tracer tracer, Meter meter) {
        this.publisher = publisher;
        this.tracer = tracer;
        this.ordersCreated = meter.counterBuilder("orders.created")
            .setDescription("Successful POST /orders -> publish completions")
            .setUnit("1")
            .build();
    }
```

### place(...) try(Scope) block (counter call-site)

```java
try (Scope scope = span.makeCurrent()) {
    String orderId = UUID.randomUUID().toString();
    publisher.publish(orderId, payload);

    String priority = String.valueOf(
        Optional.ofNullable(payload.get("priority")).orElse("standard"));
    ordersCreated.add(1, Attributes.of(
        AttributeKey.stringKey("order.priority"), priority));

    return orderId;
} catch (RuntimeException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();
}
```

### Catch block (UNCHANGED from Phase 2 / Phase 3)

```java
catch (RuntimeException e) {
    // D-03 catch — UNCHANGED from Phase 2 / Phase 3. Counter does NOT
    // fire on this path (D-08 rationale above). The recordException +
    // setStatus(ERROR) pattern records the failure ON THE TRACE.
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
}
```

## Files Created/Modified

| File | Change |
|------|--------|
| `producer-service/src/main/java/com/example/producer/domain/OrderService.java` | +78/-5 lines: add Meter param + LongCounter field + counter call-site + Phase 4 JavaDoc |

**New files:** 0
**New pom dependencies:** 0

## Compile Status

```
mvn -pl producer-service compile
FAILS with 1 error — HttpServerSpanFilter(Tracer, Meter) constructor does not yet exist.
```

This failure is in `OtelSdkConfiguration.java` (Plan 04-01 updated the factory call-site), NOT in `OrderService.java`. Plan 04-03 (Wave 2 sibling) will add the `(Tracer, Meter)` constructor to `HttpServerSpanFilter`.

`OrderService.java` itself has no compilation issues — the error is entirely in a different class.

## Decisions Made

- `order.priority` uses `AttributeKey.stringKey(...)` (string literal) — intentionally NOT semconv. The workshop teaching point is: use semconv constants where they exist (HTTP, messaging, etc.), use string literals for app-specific business dimensions. This contrasts with Plan 04-03's histogram which uses `HttpAttributes.HTTP_REQUEST_METHOD`.
- Counter fires strictly on the success path — the plan's rationale (METRIC-02 = `orders.created`, not `orders.attempted`) is preserved. Failure visibility is via the trace ERROR status, not via a metric.
- `Optional.ofNullable(payload.get("priority")).orElse("standard")` idiom chosen per D-09 — cleanly handles null, String, and non-String JSON types without instanceof branching.

## Deviations from Plan

None — plan executed exactly as written. All four edits (imports, JavaDoc, field+constructor, counter call-site) applied as specified. All acceptance criteria passed on first attempt.

## Issues Encountered

None.

## Known Stubs

None. The counter is fully wired: `ordersCreated.add(1, ...)` calls into the `SdkMeterProvider` built in Plan 04-01, which is registered with the `OpenTelemetrySdk` bean and exports via the `PeriodicMetricReader` → `OtlpGrpcMetricExporter` → otel-lgtm pipeline. No placeholder values, no hardcoded empty collections.

## Threat Flags

No new trust boundaries introduced. The `payload.get("priority")` user-controlled input flows only to an OTel attribute value (not to SQL/HTML/log/eval surfaces) — covered by T-04-02-01/02/03 in the plan's threat model. T-04-02-01 cardinality concern is mitigated by pedagogy (README delta in Plan 04-05).

## Next Phase Readiness

- METRIC-02 satisfied at source level — ready for Plan 04-05's live smoke test (orders_created_total visible in Mimir within 15s of POST /orders)
- Plan 04-03 (HttpServerSpanFilter histogram) is the sibling plan that unblocks the full producer-service compile
- Plan 04-04 (consumer ObservableGauge) is independent of this plan
- All three Wave-2 plans (04-02, 04-03, 04-04) must land before 04-05 smoke test can run

---
*Phase: 04-metrics*
*Completed: 2026-05-01*

## Self-Check: PASSED
