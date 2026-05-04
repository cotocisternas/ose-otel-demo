---
phase: 16-head-sampling-w3c-baggage
plan: "03"
subsystem: sdk-baggage-wiring
tags: [otel, baggage, w3c-baggage, amqp, BAG-01, BAG-03, BAG-04]
dependency_graph:
  requires:
    - 16-01 (sampler swap — buildTracerProvider() block must exist)
    - 16-02 (BaggageSpanAttributeProcessor created and registered in both OtelSdkConfiguration files)
  provides:
    - X-Customer-Tier header parameter in OrderController.create() with defaultValue=standard
    - Baggage try-with-resources scope around orderService.place() (BAG-01)
    - Outer extracted.makeCurrent() scope in TracingMessageListenerAdvice.invoke() (BAG-03)
    - BaggageSpanAttributeProcessor in TestOtelHolder SdkTracerProvider (X-4 mitigation)
    - BAGGAGE_RPS stream in scripts/load.sh rotating gold/silver/standard tiers
    - verify:baggage mise task with Tempo TraceQL retry loop (BAG-04)
  affects:
    - producer-service OrderController (HTTP entry point baggage SET)
    - otel-bootstrap TracingMessageListenerAdvice (consumer context availability fix)
    - integration-tests TestOtelHolder (baggage assertion enablement)
    - scripts/load.sh (load generation for baggage demo)
    - mise.toml (operational verification task)
tech_stack:
  added: []
  patterns:
    - Baggage.builder().put() + try-with-resources Scope pattern for HTTP → AMQP baggage injection
    - Context.makeCurrent() outer scope wrapping span builder — makes baggage available to BaggageSpanAttributeProcessor
    - BaggageSpanAttributeProcessor registered before SimpleSpanProcessor in test SdkTracerProvider (X-4 mitigation)
key_files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/api/OrderController.java
    - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
    - integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
    - scripts/load.sh
    - mise.toml
decisions:
  - String orderId declared before try block (Pitfall 6 — Java scoping; orderId used after try-with-resources closes)
  - setParent(extracted) preserved verbatim inside outer ctxScope (D-L3 teaching artifact — load-bearing line)
  - Sampler.alwaysOn() kept in TestOtelHolder (D-18 test determinism; traceIdRatioBased not mirrored in tests)
  - BAGGAGE_RPS default=3 rps (low rate to avoid dominating steady streams; 3 requests/cycle covers all 3 tier values)
  - verify:baggage uses Tempo TraceQL {span.baggage.customer-tier="gold"} with 6-attempt x 5s retry loop
metrics:
  duration: "2min"
  completed: "2026-05-04"
  tasks: 2
  files: 5
---

# Phase 16 Plan 03: Baggage End-to-End Wiring Summary

Wired the complete W3C baggage propagation path: `OrderController` sets `customer-tier` baggage from the `X-Customer-Tier` HTTP header; `TracingMessageListenerAdvice` gains an outer `extracted.makeCurrent()` scope so the consumer body has baggage access; `TestOtelHolder` is updated so integration tests can assert `baggage.customer-tier` attributes; `load.sh` gets a `BAGGAGE_RPS` rotation stream; and `verify:baggage` is added to `mise.toml`.

## What Was Built

### Task 1: OrderController baggage scope + TracingMessageListenerAdvice outer scope

**Edit 1: OrderController.java**

Added two imports (`io.opentelemetry.api.baggage.Baggage`, `io.opentelemetry.context.Scope`) and extended the `create()` method signature with a third parameter:

```java
@RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard")
    String customerTier
```

Replaced the inline `String orderId = orderService.place(payload)` with the baggage try-with-resources pattern (BAG-01 / D-B1):

```java
Baggage baggage = Baggage.builder().put("customer-tier", customerTier).build();
String orderId;
try (Scope baggageScope = baggage.makeCurrent()) {
    orderId = orderService.place(payload);
}
```

`String orderId` is declared before the try block so it remains in scope for `LOG.info` and the `return` statement (Pitfall 6 — Java scoping).

The baggage scope activates before `orderService.place()` executes, which means both `TracingMessagePostProcessor` (AMQP inject) and `TracingClientHttpRequestInterceptor` (HTTP inject to notification service) run inside this scope and pick up the baggage automatically via W3CBaggagePropagator.

**Edit 2: TracingMessageListenerAdvice.java**

Wrapped the entire span-builder + `inv.proceed()` logic in an outer `try (Scope ctxScope = extracted.makeCurrent())` (BAG-03 / D-L1). The resulting nesting is:

```
try (Scope ctxScope = extracted.makeCurrent()) {   // outer: makes baggage accessible
    Span span = tracer.spanBuilder(...)
        .setParent(extracted)                        // LOAD-BEARING (D-L3) — preserved
        ...
        .startSpan();
    try (Scope scope = span.makeCurrent()) {         // inner: makes span accessible to MDC
        return inv.proceed();
    } catch ... { } finally { span.end(); }
}
```

Without the outer `ctxScope`, `Baggage.current()` inside the listener body (`OrderListener.onOrder()`) returns empty baggage — the W3C baggage header was extracted into `extracted` but never made current on the thread (F7-2 mitigation).

`Scope` was already imported — no new imports needed in this file.

`mvn -q clean compile -pl otel-bootstrap,producer-service -am` exits 0.

**Commit:** `46450a0`

### Task 2: TestOtelHolder BaggageSpanAttributeProcessor, load.sh BAGGAGE_RPS stream, verify:baggage

**Edit 1: TestOtelHolder.java (X-4 mitigation)**

Added imports `java.util.Set` and `com.example.otel.context.BaggageSpanAttributeProcessor`.

Inserted `BaggageSpanAttributeProcessor` as the FIRST span processor in the `SdkTracerProvider` builder chain, before `SimpleSpanProcessor`:

```java
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.alwaysOn())
    .addSpanProcessor(new BaggageSpanAttributeProcessor(Set.of("customer-tier")))  // X-4 fix
    .addSpanProcessor(SimpleSpanProcessor.create(SPANS))
    .build();
```

`Sampler.alwaysOn()` is deliberately preserved (D-18) — the production `traceIdRatioBased(0.5)` sampler is NOT mirrored in tests. Every span must be captured for deterministic IT assertions.

**Edit 2: scripts/load.sh**

Added `BAGGAGE_RPS` stream immediately after the `PID_IDEMPOTENT=$!` line (end of IDEMPOTENT_RPS block). The stream:

- Defaults to `BAGGAGE_RPS=3` (overridable via env)
- Rotates through `tiers=(gold silver standard)` per request using index modulus
- Sends `X-Customer-Tier: ${tier}` header on each POST to `/orders`
- Uses `awk` to compute `sleep_interval` from `BAGGAGE_RPS` (same pattern as idempotency stream)
- Background subshell assigns `PID_BAGGAGE=$!`

**Edit 3: mise.toml**

Appended `[tasks."verify:baggage"]` after the `verify:head-sampling` task. The task:

- Queries Tempo `:3200` via TraceQL `{span.baggage.customer-tier="gold"}` for any span with the gold tier attribute
- Uses a 6-attempt x 5s retry loop (30s tolerance for Tempo cold start)
- Asserts `jq -e '.traces | length > 0'` (at least one trace with the baggage attribute)
- On failure: emits 3 diagnose hints (X-Customer-Tier header, BaggageSpanAttributeProcessor, outer makeCurrent())
- On success: prints teaching note about PRODUCER + CONSUMER span carrying the attribute, and D-B5 moment (SERVER span does NOT carry it)

`mvn -pl integration-tests -am test -q --no-transfer-progress` exits 0 (all ITs pass).

**Commit:** `20d43e4`

## Verification Results

1. `grep -c "Baggage.builder().put" producer-service/.../OrderController.java` → 1 (PASS)
2. `grep -c "extracted.makeCurrent()" otel-bootstrap/.../TracingMessageListenerAdvice.java` → 1 (PASS)
3. `grep -c "BaggageSpanAttributeProcessor" integration-tests/.../TestOtelHolder.java` → 3 (import + comment + usage) (PASS)
4. `grep -c "BAGGAGE_RPS" scripts/load.sh` → 4 (declaration + guard + internal usage + PID) (PASS)
5. `grep -c "verify:baggage" mise.toml` → 5 (task header + description + echo lines) (PASS)
6. `mvn -pl integration-tests -am test -q` → exit 0, BUILD SUCCESS (PASS)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. The full baggage propagation path is wired:
- HTTP layer: `OrderController` reads `X-Customer-Tier` and sets it as baggage (BAG-01)
- AMQP inject: `TracingMessagePostProcessor` runs inside the baggage scope, so W3CBaggagePropagator serializes `baggage: customer-tier=gold` into AMQP message headers automatically
- AMQP extract: `TracingMessageListenerAdvice` extracts the W3C context (including baggage) via `propagator.extract()` and makes it current via the new outer scope (BAG-03)
- Span stamping: `BaggageSpanAttributeProcessor.onStart()` reads `Baggage.fromContext(parentContext)` and stamps `baggage.customer-tier` on the CONSUMER span (BAG-02, from Plan 16-02)
- Test assertions: `TestOtelHolder` has the processor registered so integration tests can assert `baggage.customer-tier` attribute on captured spans
- Operational verification: `verify:baggage` provides a runnable gate against live Tempo

## Threat Flags

None. All threat register items from the plan's STRIDE model are addressed:
- T-16-08 (Scope leak): mitigated by `try (Scope baggageScope = baggage.makeCurrent())` pattern
- T-16-10 (extracted.makeCurrent() without try-with-resources): mitigated by `try (Scope ctxScope = extracted.makeCurrent())` in TracingMessageListenerAdvice
- T-16-07 (information disclosure via Tempo): accepted per plan — value flows to observability only, BaggageSpanAttributeProcessor allowlist limits surface
- T-16-09 (arbitrary baggage values from external clients): accepted per plan — workshop demo, no auth layer

## Self-Check: PASSED

- `producer-service/src/main/java/com/example/producer/api/OrderController.java` — FOUND (modified)
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` — FOUND (modified)
- `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` — FOUND (modified)
- `scripts/load.sh` — FOUND (modified)
- `mise.toml` — FOUND (modified, verify:baggage task appended)
- Commit `46450a0` (Task 1: OrderController + TracingMessageListenerAdvice) — FOUND
- Commit `20d43e4` (Task 2: TestOtelHolder + load.sh + mise.toml) — FOUND
