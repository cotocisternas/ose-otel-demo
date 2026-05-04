# Phase 16: Head Sampling + W3C Baggage - Research

**Researched:** 2026-05-04
**Domain:** OTel Java SDK — Sampler API, SpanProcessor API, W3C Baggage API, TracerProvider wiring
**Confidence:** HIGH (all critical claims verified via Context7 or direct codebase inspection)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Baggage lifecycle scoping**
- D-B1: Controller-scoped baggage. `OrderController.create()` reads `X-Customer-Tier` via `@RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard")` and wraps `orderService.place(payload)` in a `try(Scope)` with the baggage `Context`.
- D-B2: Baggage key = `customer-tier`. Span attribute = `baggage.customer-tier`. The `order.` prefix in BAG-01 was contextual description, not the literal key.
- D-B3: Scope wraps only `orderService.place()`. The `try(Scope)` does NOT wrap the idempotency gate or logging.
- D-B5: SERVER span intentionally missing baggage attribute — teaching moment. The `HttpServerSpanFilter` creates the SERVER span BEFORE the controller sets baggage. README calls this out explicitly.
- D-B6: `defaultValue = "standard"` on the annotation. Spring handles the default; `customerTier` is always non-null.
- D-B7: Load script rotates through gold/silver/standard. `scripts/load.sh` randomly picks from `[gold, silver, standard]` per request.

**SpanProcessor wiring pattern**
- D-S1: Inline creation inside `buildTracerProvider()`. `BaggageSpanAttributeProcessor` is created next to the existing `BatchSpanProcessor`, added FIRST (attribute-stamping before batching). Two `.addSpanProcessor()` calls on the builder. `Set.of("customer-tier")` allowlist passed at construction time.
- D-S2: Split into 4 plans: Plan 16-01 (head sampling), Plan 16-02 (BaggageSpanAttributeProcessor), Plan 16-03 (baggage end-to-end), Plan 16-04 (tag + final verification).
- D-S3: New `context/` package in `otel-bootstrap`. `com.example.otel.context.BaggageSpanAttributeProcessor` — cross-cutting concern separate from `amqp/` and `http/`.

**Listener advice restructuring**
- D-L1: Outer `extracted.makeCurrent()` + inner `span.makeCurrent()`. Two nested try-with-resources in `TracingMessageListenerAdvice.invoke()`. The outer scope makes the extracted context (including baggage) current for the entire listener body. The inner scope makes the CONSUMER span current.
- D-L2: Just describe the change in the commit message. No backwards-compatibility note.
- D-L3: Keep `.setParent(extracted)`. Even though it's now redundant with `extracted.makeCurrent()`, the line is marked LOAD-BEARING (ROADMAP SC #1) and is a teaching artifact.

**README sub-lesson narrative**
- D-R1: F2-3 warning box BEFORE the code change. Prominent callout at the top of §16a, before the sampler swap code. Math: 50% head × 20% tail = 10% effective rate.
- D-R2: Two numbered sub-sections: `### Step 16a: Head Sampling` and `### Step 16b: W3C Baggage`.
- D-R3: Side-by-side markdown table for head-vs-tail contrast. Five dimensions: Where, Sees, Bandwidth, Decides on, Trade-off.

### Claude's Discretion

- `BaggageSpanAttributeProcessor` implementation details (reads `Baggage.fromContext(parentContext)` in `onStart`, iterates allowlist, stamps `baggage.<key>` attributes via `span.setAttribute()`)
- Exact sampler swap diff (one-line change per service: `Sampler.alwaysOn()` → `Sampler.traceIdRatioBased(0.5)` inside the `parentBased()` wrapper)
- `TestOtelConfiguration` updates for the new sampler and processor registration (X-4 mitigation)
- `verify:head-sampling` and `verify:baggage` mise task implementations (follow `verify:http-client-spans` pattern)
- Integration test assertions for baggage propagation
- Screenshot deferral to Phase 18 Playwright pipeline
- `OtelSdkConfiguration` comment updates for the sampler swap
- README §16 exact wording, length, and structure (follow Phase 14/15 precedent ~100-150 lines per sub-section)
- Whether `orderId` variable needs to be declared before the try(Scope) block

### Deferred Ideas (OUT OF SCOPE)

- AMQP topology variants (Phase 17)
- Additional baggage keys beyond `customer-tier`
- Baggage-based tail sampling policies at the Collector
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HSAMP-01 | Both `OtelSdkConfiguration.buildTracerProvider()` calls swap `Sampler.parentBased(Sampler.alwaysOn())` for `Sampler.parentBased(Sampler.traceIdRatioBased(0.5))` — set programmatically in code (NOT via env var) | OtelSdkConfiguration.java line 420 is the exact current state; one-line swap per service confirmed |
| HSAMP-02 | README explains head-vs-tail sampling contrast in a paired table: SDK-side pre-export vs Collector-side post-assembly | D-R3 locked; five-dimension table format specified |
| HSAMP-03 | Workshop attendee runs load script for 100 requests and observes ~50 traces in Tempo (50% head-sampling ratio) | `verify:head-sampling` mise task implements this assertion; `Sampler.traceIdRatioBased(0.5)` is the correct API call |
| BAG-01 | `OrderController.create()` reads `X-Customer-Tier` HTTP header (default `"standard"`) and sets it as `order.customer-tier` baggage entry | D-B2 clarifies key is `customer-tier` (no `order.` prefix); Spring `@RequestHeader` pattern established at line 27 of `OrderController.java` |
| BAG-02 | New shared `SpanProcessor` in `otel-bootstrap` registered on both `SdkTracerProvider`s; stamps allowlist of keys as `baggage.<key>` span attributes | `BaggageSpanAttributeProcessor` reads `Baggage.fromContext(parentContext)` in `onStart(Context, ReadWriteSpan)`; `context/` package is new |
| BAG-03 | `TracingMessageListenerAdvice.invoke()` calls `extractedContext.makeCurrent()` for the ENTIRE listener body | Current code (lines 128-141) only has `span.makeCurrent()` scope — outer `extracted.makeCurrent()` wrapper is the structural change |
| BAG-04 | Workshop attendee can run `curl -H "X-Customer-Tier: gold"` and see `baggage.customer-tier=gold` on BOTH producer and consumer spans in Tempo | Requires BAG-01 + BAG-02 + BAG-03 all active; `W3CBaggagePropagator` already in composite propagator chain (no new wiring needed) |
</phase_requirements>

---

## Summary

Phase 16 is a pure SDK instrumentation phase — no infrastructure changes whatsoever. It bundles two sub-lessons into a single git tag (`step-16-sampling-baggage`):

**Sub-lesson 16a (Head Sampling):** Both `OtelSdkConfiguration.buildTracerProvider()` methods swap `Sampler.alwaysOn()` for `Sampler.traceIdRatioBased(0.5)` inside the existing `Sampler.parentBased()` wrapper. This is a one-line change per service. The critical teaching surface is that the SDK drops traces *before* export — no Collector processor ever sees the dropped data. The README §16a pairs with §11 (tail sampling) via a five-dimension comparison table, and a mandatory F2-3 warning callout precedes the code change to prevent the "50% × 20% = 10%" double-filter confusion.

**Sub-lesson 16b (W3C Baggage):** Three coordinated changes introduce end-to-end baggage propagation: (1) `OrderController.create()` sets the `customer-tier` baggage entry from the `X-Customer-Tier` HTTP header; (2) a new shared `BaggageSpanAttributeProcessor` in `otel-bootstrap/context/` stamps allowlisted baggage keys as `baggage.<key>` span attributes on every span start; (3) `TracingMessageListenerAdvice.invoke()` gains an outer `extracted.makeCurrent()` scope so baggage is accessible throughout the consumer listener body. Because `W3CBaggagePropagator` is already wired in the composite propagator chain, the baggage `baggage:` header flows through AMQP automatically — no changes to `TracingMessagePostProcessor` or `TracingClientHttpRequestInterceptor` are needed.

**Primary recommendation:** Execute plans in the order D-S2: sampler swap first (verifiable in isolation via Tempo trace count), then `BaggageSpanAttributeProcessor` (verifiable via unit test), then baggage end-to-end wiring (verifiable via integration test + `verify:baggage` mise task), then git tag.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Head sampling (50% ratio) | SDK / Host JVM | — | `Sampler` decision happens inside `SdkTracerProvider` before any export; Collector never sees dropped spans |
| Baggage SET at HTTP entry | API layer (`OrderController`) | — | Baggage must be in `Context.current()` before AMQP inject runs; controller is the earliest correct point after the `X-Customer-Tier` header is available |
| Baggage propagation over AMQP | otel-bootstrap propagation layer | — | `W3CBaggagePropagator` already in the composite propagator; inject/extract pair handles this automatically |
| Baggage-to-span-attribute stamping | otel-bootstrap SpanProcessor | Both SDK TracerProviders | `BaggageSpanAttributeProcessor.onStart()` runs in both services via registration in `buildTracerProvider()` |
| Baggage available in consumer business logic | otel-bootstrap advice | — | `TracingMessageListenerAdvice` outer `extracted.makeCurrent()` scope — BAG-03 fix makes `Baggage.current()` non-empty inside `OrderListener.onOrder()` |

---

## Standard Stack

### Core (all already on classpath — no new pom.xml additions)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.opentelemetry:opentelemetry-api` | 1.61.0 (via BOM) | `Sampler`, `Baggage`, `Scope`, `Context` | Already on classpath; `Sampler` class accessed via `import io.opentelemetry.sdk.trace.samplers.Sampler` |
| `io.opentelemetry:opentelemetry-sdk` | 1.61.0 (via BOM) | `SdkTracerProvider`, `SdkTracerProviderBuilder`, `ReadWriteSpan`, `SpanProcessor` | Already on classpath; `SpanProcessor` interface accessed for `BaggageSpanAttributeProcessor` |
| `io.opentelemetry.api.baggage` | (part of opentelemetry-api) | `Baggage.builder()`, `Baggage.fromContext()`, `Baggage.current()` | Already on classpath; no new dependency |
| `io.opentelemetry.sdk.trace` | (part of opentelemetry-sdk) | `SpanProcessor`, `ReadWriteSpan` | `SpanProcessor` interface for `BaggageSpanAttributeProcessor` |

**No new Maven dependencies required for Phase 16.** All required classes are in the existing `opentelemetry-api` and `opentelemetry-sdk` artifacts already declared in the parent pom.xml BOM. [VERIFIED: codebase grep — `OtelSdkConfiguration.java` already imports `Sampler`; `BaggageSpanAttributeProcessor` only needs API and SDK classes already present]

**Version verification:** `opentelemetry-bom:1.61.0` confirmed in `pom.xml` (root). `Sampler.traceIdRatioBased(double)` is a stable API method since SDK 1.0. [VERIFIED: Context7 /open-telemetry/opentelemetry-java]

---

## Architecture Patterns

### System Architecture Diagram

```
HTTP POST /orders
  X-Customer-Tier: gold
        │
        ▼
HttpServerSpanFilter — starts SERVER span
  (BaggageSpanAttributeProcessor.onStart fires here too)
  (baggage NOT yet set → SERVER span gets NO baggage.customer-tier)
        │
        ▼
OrderController.create()
  1. @RequestHeader X-Customer-Tier = "gold"
  2. Baggage.builder().put("customer-tier","gold").build()
  3. baggageContext = baggage.storeInContext(Context.current())
  4. try (Scope s = baggageContext.makeCurrent()) {
        orderId = orderService.place(payload)
     }
        │ [baggage now in Context.current()]
        ▼
OrderService.place() — starts INTERNAL span
  BaggageSpanAttributeProcessor.onStart fires:
    reads Baggage.fromContext(parentContext) → "customer-tier"="gold"
    span.setAttribute("baggage.customer-tier", "gold")
        │
        ├── TracingMessagePostProcessor.inject()
        │     W3CBaggagePropagator writes baggage header automatically
        │     AMQP headers: traceparent + baggage: customer-tier=gold
        │     → PRODUCER span stamped with baggage.customer-tier=gold
        │
        └── TracingClientHttpRequestInterceptor.intercept()
              W3CBaggagePropagator injects baggage into HTTP headers
              → CLIENT span stamped with baggage.customer-tier=gold
                         │
                         ▼ AMQP :5672
                    RabbitMQ
                         │
                         ▼
TracingMessageListenerAdvice.invoke()
  Context extracted = propagator.extract(...)  ← contains baggage: customer-tier=gold
  try (Scope ctxScope = extracted.makeCurrent()) {   ← BAG-03 new outer scope
    Span span = spanBuilder.setParent(extracted)...startSpan()
    BaggageSpanAttributeProcessor.onStart fires:
      reads Baggage.fromContext(parentContext) → "customer-tier"="gold"
      span.setAttribute("baggage.customer-tier", "gold")
    try (Scope spanScope = span.makeCurrent()) {
      inv.proceed()    ← CONSUMER span stamped with baggage.customer-tier=gold
    }
  }
        │ OTLP/gRPC :4317
        ▼
otel-collector → Tempo → Grafana
  Search: baggage.customer-tier=gold
  Both producer INTERNAL/PRODUCER and consumer CONSUMER spans visible
```

### Recommended Project Structure (new file only)

```
otel-bootstrap/src/main/java/com/example/otel/
  amqp/               (unchanged)
  http/               (unchanged)
  context/            ← NEW package
    BaggageSpanAttributeProcessor.java  ← NEW class
```

### Pattern 1: Sampler Swap (HSAMP-01)

**What:** One-line change per service in `OtelSdkConfiguration.buildTracerProvider()`
**When to use:** Phase 16a only — swap for workshop; later phases may revert or keep as-is

**Producer service — before (line 420):**
```java
// Source: producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
Sampler sampler = Sampler.parentBased(Sampler.alwaysOn());
```

**After (same line):**
```java
// Source: OTel Java SDK Sampler API — Context7 /open-telemetry/opentelemetry-java
Sampler sampler = Sampler.parentBased(Sampler.traceIdRatioBased(0.5));
```

The comment block above the line (lines 402–419) must also be updated: the forward-looking "For production, swap to..." comment becomes "Phase 16 swapped this to 50% ratio sampling. See README §16a for the head-vs-tail sampling contrast." The comment about why `parentBased` matters stays verbatim — it is pedagogically load-bearing.

**Same change in consumer service** at `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` line 428 (identical pattern, confirmed via codebase inspection). [VERIFIED: codebase read]

### Pattern 2: BaggageSpanAttributeProcessor (BAG-02)

**What:** New `SpanProcessor` implementation that stamps allowlisted baggage keys as span attributes on `onStart`.
**When to use:** Registered in both `buildTracerProvider()` methods before `BatchSpanProcessor`.

```java
// Source: otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java
// Pattern verified against: Context7 /websites/opentelemetry_io "Custom SpanProcessor Implementation"

package com.example.otel.context;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Set;

/**
 * SpanProcessor that stamps allowlisted baggage keys as {@code baggage.<key>}
 * span attributes on every span start (Phase 16 / BAG-02).
 *
 * <p>Only keys in {@link #allowedKeys} are stamped. The allowlist prevents
 * cardinality explosion if arbitrary baggage entries are added (F7-3).
 *
 * <p>Reads baggage from {@code parentContext} (the context BEFORE the new span
 * is started), NOT from {@code Baggage.current()} (which would read the SAME
 * context — same result for in-thread baggage, but parentContext is more
 * correct per OTel spec: the processor receives the context that parented
 * this span).
 *
 * <p>Added FIRST in {@link OtelSdkConfiguration#buildTracerProvider(Resource)}
 * so baggage is stamped before {@link BatchSpanProcessor} enqueues the span.
 */
public class BaggageSpanAttributeProcessor implements SpanProcessor {

    private final Set<String> allowedKeys;

    public BaggageSpanAttributeProcessor(Set<String> allowedKeys) {
        this.allowedKeys = Set.copyOf(allowedKeys);
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        Baggage baggage = Baggage.fromContext(parentContext);
        for (String key : allowedKeys) {
            String value = baggage.getEntryValue(key);
            if (value != null) {
                span.setAttribute("baggage." + key, value);
            }
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // No-op — stamping happens at start only
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }
}
```

### Pattern 3: Registration in buildTracerProvider() (D-S1)

**What:** Add `BaggageSpanAttributeProcessor` as FIRST processor before `BatchSpanProcessor`.
**Both services:** Identical change per DOC-05.

```java
// In buildTracerProvider() — after sampler swap, before .build()
// Source: OtelSdkConfiguration.java modification pattern (D-S1)
BaggageSpanAttributeProcessor baggageProcessor =
    new BaggageSpanAttributeProcessor(Set.of("customer-tier"));

return SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(sampler)
    .addSpanProcessor(baggageProcessor)   // FIRST — stamps before batching
    .addSpanProcessor(spanProcessor)       // BatchSpanProcessor second
    .build();
```

**Import:** `import com.example.otel.context.BaggageSpanAttributeProcessor;` in both `OtelSdkConfiguration` files. `import java.util.Set;` already present or needed.

### Pattern 4: OrderController baggage setup (BAG-01, D-B1)

**What:** Add `X-Customer-Tier` header parameter + baggage scoping around `orderService.place()`.

Current signature (line 25-27 of `OrderController.java`):
```java
public ResponseEntity<Map<String, String>> create(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader)
```

After:
```java
// Source: OTel Java Baggage API — Context7 /websites/opentelemetry_io "Manipulate and read Baggage"
// Pattern: Baggage.builder().put().build().makeCurrent() + try-with-resources (F7-4)
public ResponseEntity<Map<String, String>> create(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
        @RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard")
            String customerTier) {

    // ... idempotency gate unchanged ...

    // BAG-01: set customer-tier baggage for the AMQP publish scope.
    // Baggage is active only while orderService.place() executes — AMQP
    // injection (TracingMessagePostProcessor) and HTTP injection
    // (TracingClientHttpRequestInterceptor) both run inside this scope.
    Baggage baggage = Baggage.builder().put("customer-tier", customerTier).build();
    String orderId;
    try (Scope baggageScope = baggage.makeCurrent()) {
        orderId = orderService.place(payload);
    }
    LOG.info("accepted orderId={}", orderId);
    return ResponseEntity.accepted().body(Map.of("orderId", orderId));
}
```

**Import:** `import io.opentelemetry.api.baggage.Baggage;` and `import io.opentelemetry.context.Scope;` — both are in `opentelemetry-api` already on the classpath.

**Scoping note for `orderId`:** The variable must be declared before the `try` block (as shown) to remain in scope for `LOG.info` and `return` after the block closes. This is a standard Java scoping requirement. [VERIFIED: Java language spec; no OTel-specific concern]

**D-B5 teaching moment:** The SERVER span created by `HttpServerSpanFilter` starts BEFORE `OrderController.create()` executes, before baggage is set. `BaggageSpanAttributeProcessor.onStart()` fires for the SERVER span with empty baggage → no `baggage.customer-tier` attribute. The README must call this out: "Notice the SERVER span doesn't carry the attribute — it was created before you set baggage."

### Pattern 5: TracingMessageListenerAdvice restructuring (BAG-03, D-L1)

**Current code (lines 109-141 of TracingMessageListenerAdvice.java):**
```java
Context extracted = propagator.extract(Context.current(), props, GETTER);
// ... exchange + routingKey read ...
Span span = tracer.spanBuilder(exchange + " process")
    .setParent(extracted)   // LOAD-BEARING — D-L3
    ...
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    return inv.proceed();
} catch (Throwable t) {
    ...
} finally {
    span.end();
}
```

**After (D-L1 — outer extracted scope wraps everything):**
```java
// Source: TracingMessageListenerAdvice.java restructuring (BAG-03 / D-L1)
// F7-2 mitigation: extracted.makeCurrent() makes Baggage.current() non-empty
// throughout the consumer body, not just for span parenting.
Context extracted = propagator.extract(Context.current(), props, GETTER);
String exchange = props.getReceivedExchange();
String routingKey = props.getReceivedRoutingKey();

try (Scope ctxScope = extracted.makeCurrent()) {  // NEW outer scope — makes baggage current
    Span span = tracer.spanBuilder(exchange + " process")
        .setParent(extracted)                          // <-- LOAD-BEARING (ROADMAP SC #1) — D-L3
        .setSpanKind(SpanKind.CONSUMER)
        .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
            MessagingSystemIncubatingValues.RABBITMQ)
        .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
            exchange)
        .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
            MessagingOperationTypeIncubatingValues.PROCESS)
        .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
            routingKey)
        .startSpan();
    try (Scope scope = span.makeCurrent()) {
        return inv.proceed();
    } catch (Throwable t) {
        Throwable recorded =
            (t instanceof ListenerExecutionFailedException && t.getCause() != null)
                ? t.getCause()
                : t;
        span.recordException(recorded);
        span.setStatus(StatusCode.ERROR);
        throw t;
    } finally {
        span.end();
    }
}
```

**Why `setParent(extracted)` stays (D-L3):** After `extracted.makeCurrent()`, `Context.current()` IS `extracted`. So `.setParent(extracted)` is technically redundant — the builder would pick up `Context.current()` as the parent anyway. But the line is a load-bearing teaching artifact: Phase 2/3 commit history shows this explicit `.setParent(extractedContext)` as the "how does the consumer span link to the producer trace?" explanation. D-L3 preserves it unchanged to maintain teaching continuity for attendees reading git blame. [VERIFIED: codebase read; CONTEXT.md D-L3]

### Pattern 6: load.sh X-Customer-Tier rotation (D-B7)

**What:** Add tier variable rotation to the existing curl-based idempotency stream pattern.

The new baggage stream is a fourth stream alongside the existing express, standard, and slow streams. The load.sh already uses `oha` for steady streams but the baggage lesson calls for explicit `curl` calls showing the header — following the idempotency stream pattern (lines 185-201 of `scripts/load.sh`):

```bash
# Phase 16 — baggage stream: rotates X-Customer-Tier through gold/silver/standard
# at BAGGAGE_RPS per request. Demonstrates cardinality-control lesson (3 values,
# not per-user values) and verifies AMQP baggage propagation end-to-end.
BAGGAGE_RPS="${BAGGAGE_RPS:-3}"
if [[ "$BAGGAGE_RPS" -gt 0 ]]; then
  (
    sleep_interval=$(awk "BEGIN {printf \"%.3f\", 1.0 / ${BAGGAGE_RPS}}")
    tiers=(gold silver standard)
    i=0
    while :; do
      tier="${tiers[$((i % 3))]}"
      i=$((i + 1))
      curl -sS -o /dev/null \
        -X POST \
        -H "Content-Type: application/json" \
        -H "X-Customer-Tier: ${tier}" \
        -d '{"sku":"WIDGET-BAGGAGE","quantity":1,"priority":"standard"}' \
        "${TARGET}" || true
      sleep "${sleep_interval}"
    done
  ) &
  PID_BAGGAGE=$!
fi
```

### Pattern 7: verify:head-sampling mise task

**Pattern:** Follow `verify:http-client-spans` (lines 621-663 of `mise.toml`) — Tempo TraceQL query + retry loop.

```toml
# Phase 16 — verify:head-sampling (HSAMP-03)
[tasks."verify:head-sampling"]
description = "Phase 16 invariant: ~50% of traces reach Tempo under head sampling (traceIdRatioBased(0.5))"
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

# Prerequisites: mise run infra:up, mise run dev, then send ~100 requests:
#   for i in $(seq 1 100); do curl -sS -o /dev/null -X POST -H 'Content-Type: application/json' \
#     -d '{"sku":"WIDGET-1","quantity":1}' http://localhost:8080/orders; done
# Then wait ~15s for the Collector decision_wait window, then run this task.
# 
# We assert at least 1 trace exists (proves sampling is working, not zero).
# The ~50% ratio is a probabilistic property verified by the README demo
# (100 requests → ~50 traces), not by this automated gate.

echo "verify:head-sampling: querying Tempo for any trace from order-producer (proves SDK is exporting sampled traces)..."
for i in $(seq 1 $ATTEMPTS); do
  RESULT=$(curl -sS --data-urlencode 'q={resource.service.name="order-producer"}' 'http://localhost:3200/api/search?limit=5' 2>&1) || {
    LAST_ERR="curl :3200 failed: $RESULT"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — Tempo not ready ($LAST_ERR); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:head-sampling timed out — Tempo not reachable."
    exit 1
  }
  if printf '%s' "$RESULT" | jq -e '.traces | length > 0' >/dev/null 2>&1; then break; fi
  LAST_ERR="no traces from order-producer in Tempo yet"
  [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — $LAST_ERR; retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:head-sampling — $LAST_ERR after $((ATTEMPTS * SLEEP_SECS))s."
  exit 1
done

echo "verify:head-sampling: GREEN — sampled traces from order-producer are reaching Tempo."
echo "  Manual verification: send 100 requests, confirm ~50 traces in Tempo (50% head sampling)."
echo "  Console-emitted spans will show the SDK discarding ~50% before OTLP export."
"""
```

### Pattern 8: verify:baggage mise task

```toml
# Phase 16 — verify:baggage (BAG-04)
[tasks."verify:baggage"]
description = "Phase 16 invariant: baggage.customer-tier attribute present on both producer and consumer spans in Tempo"
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

# Prerequisites: mise run infra:up, mise run dev, then:
#   curl -X POST -H "Content-Type: application/json" -H "X-Customer-Tier: gold" \
#     -d '{"sku":"WIDGET-1","quantity":1}' http://localhost:8080/orders
# Wait ~15s for Collector decision_wait, then run this task.

echo "verify:baggage: querying Tempo for spans with baggage.customer-tier=gold..."
for i in $(seq 1 $ATTEMPTS); do
  RESULT=$(curl -sS --data-urlencode 'q={span.baggage.customer-tier="gold"}' 'http://localhost:3200/api/search?limit=5' 2>&1) || {
    LAST_ERR="curl :3200 failed: $RESULT"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — Tempo not ready ($LAST_ERR); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:baggage timed out — Tempo not reachable."
    exit 1
  }
  if printf '%s' "$RESULT" | jq -e '.traces | length > 0' >/dev/null 2>&1; then break; fi
  LAST_ERR="no spans with baggage.customer-tier=gold in Tempo yet"
  [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — $LAST_ERR; retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:baggage — $LAST_ERR after $((ATTEMPTS * SLEEP_SECS))s."
  echo "Diagnose:"
  echo "  1. Did you include -H 'X-Customer-Tier: gold' in the curl request?"
  echo "  2. Is BaggageSpanAttributeProcessor registered in buildTracerProvider()?"
  echo "  3. Did TracingMessageListenerAdvice.invoke() get the outer extracted.makeCurrent()?"
  echo "Tempo API response: $RESULT"
  exit 1
done

echo "verify:baggage: GREEN — baggage.customer-tier=gold spans present in Tempo."
echo "  Open the trace in Tempo and confirm BOTH the PRODUCER-side span AND the CONSUMER span carry baggage.customer-tier=gold."
echo "  Confirm the SERVER span does NOT carry baggage.customer-tier (D-B5 teaching moment)."
"""
```

### Anti-Patterns to Avoid

- **Setting baggage via `Baggage.current().toBuilder()` (wrong for controller):** The correct idiom is `Baggage.builder().put(...)` to build from scratch, then `makeCurrent()`. Using `Baggage.current().toBuilder()` would inherit any stale baggage already in the thread context, which is harmless here but obscures the teaching surface. Use `Baggage.builder()` explicitly.

- **Reading `Baggage.current()` in `BaggageSpanAttributeProcessor.onStart()`:** The processor receives `parentContext` as a parameter. Use `Baggage.fromContext(parentContext)` — not `Baggage.current()`. `Baggage.current()` reads from the ThreadLocal; `Baggage.fromContext(parentContext)` reads from the context that will parent the new span. These are the same for synchronous in-thread flows, but using `parentContext` is spec-correct and pedagogically precise.

- **Calling `makeCurrent()` without try-with-resources (F7-4):** Every `baggage.makeCurrent()`, `span.makeCurrent()`, and `extracted.makeCurrent()` call MUST use `try (Scope s = ...)` to guarantee `Scope.close()` even on exception. A leaked Scope causes context pollution on the next request on that thread.

- **Registering `BaggageSpanAttributeProcessor` AFTER `BatchSpanProcessor`:** The processor must be FIRST in the chain. `BatchSpanProcessor` enqueues the span immediately; attribute stamping must complete before the span enters the batch queue. If added second, `onStart` fires at the right time regardless (both fire on `span.start()`), but the ordering convention makes the intent explicit and mirrors the Phase 12 `ExemplarFilter` insertion point precedent (between `.setResource()` and `registerMetricReader()`).

- **Iterating ALL baggage entries:** Do NOT call `Baggage.fromContext(parentContext).asMap().forEach(...)` to promote all entries as span attributes. The allowlist (`Set.of("customer-tier")`) is the F7-3 mitigation. In production, arbitrary baggage from upstream services can contain high-cardinality values. Only explicitly allowlisted keys get stamped.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Baggage propagation over AMQP | Custom header injection/extraction for baggage | `W3CBaggagePropagator.getInstance()` already in composite propagator | `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` already call `otel.getPropagators().getTextMapPropagator().inject/extract()`. The composite propagator already includes `W3CBaggagePropagator`. No changes needed to either class for propagation. |
| Baggage propagation over HTTP | Custom header injection for baggage | Same `W3CBaggagePropagator` via `TracingClientHttpRequestInterceptor` | Same composite propagator handles both AMQP and HTTP. No changes to `TracingClientHttpRequestInterceptor`. |
| Ratio-based sampling with parent respect | Custom sampler | `Sampler.parentBased(Sampler.traceIdRatioBased(0.5))` | `traceIdRatioBased` uses a hash of the trace ID to make a deterministic, consistent decision; `parentBased` wrapper respects the parent's sampling flag for non-root spans. No custom logic needed. |
| SpanProcessor lifecycle management | Custom flush/shutdown logic | `CompletableResultCode.ofSuccess()` no-op returns | `BaggageSpanAttributeProcessor` has no internal state or async work; `forceFlush()` and `shutdown()` are no-ops. |
| Default baggage value | Null check + default in controller body | `@RequestHeader(defaultValue = "standard")` | Spring MVC handles absent header → `customerTier` is always non-null. No `if (customerTier == null)` check needed. |

---

## Common Pitfalls

### Pitfall 1: F2-3 Double-Filter Trap (CRITICAL — README-blocking)

**What goes wrong:** Phase 11's tail sampling processor (20% probabilistic fallback) is still active in `infra/observability/otelcol-config.yaml`. When Phase 16 activates 50% head sampling at the SDK, the effective trace rate drops to 50% × 20% = 10%. An attendee sending 100 requests sees ~10 traces in Tempo and thinks "sampling is broken."

**Why it happens:** Tail sampling operates on spans that REACH the Collector. Head sampling drops spans BEFORE they reach the Collector. The two stages are multiplicative.

**How to avoid:** D-R1 — the README §16a MUST contain a dedicated callout box BEFORE the code change:

> **Warning — Double-filter trap:** Phase 11 tail sampling (20% probabilistic fallback at the Collector) is still active. If you activate Phase 16 head sampling (50% at the SDK) simultaneously, the effective trace rate is **50% × 20% = 10%**. Send 100 requests, expect ~10 traces — not ~50. To see the pure head-sampling effect, temporarily disable Phase 11's `probabilistic-fallback` policy while running this lesson.

**Warning signs:** `scripts/load.sh` sends 100 requests, Tempo shows ~10 traces.

### Pitfall 2: X-4 — TestOtelConfiguration Divergence

**What goes wrong:** `TestOtelConfiguration` / `TestOtelHolder` do not register `BaggageSpanAttributeProcessor`. Integration tests in `OrderFlowIT` that assert `baggage.customer-tier` attribute on PRODUCER and CONSUMER spans will FAIL because the test SDK does not stamp those attributes.

**Why it happens:** `TestOtelHolder.get()` (lines 143-146 of `TestOtelHolder.java`) builds `SdkTracerProvider` with only `SimpleSpanProcessor`. No `BaggageSpanAttributeProcessor` is registered.

**How to avoid:** Plan 16-03 must include updating `TestOtelHolder.get()` to add `BaggageSpanAttributeProcessor` with the same `Set.of("customer-tier")` allowlist. The `SdkTracerProvider.builder()` call becomes:
```java
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.alwaysOn())  // D-18 — unchanged
    .addSpanProcessor(new BaggageSpanAttributeProcessor(Set.of("customer-tier")))  // NEW
    .addSpanProcessor(SimpleSpanProcessor.create(SPANS))
    .build();
```
Note: `TestOtelHolder` uses `Sampler.alwaysOn()` (not `traceIdRatioBased`) because test determinism requires all spans captured (D-18). The sampler swap is NOT mirrored in test configuration.

**Warning signs:** `mvn -pl integration-tests test` passes but `OrderFlowIT` assertions for `baggage.customer-tier` attribute return null.

### Pitfall 3: F7-2 — Baggage Missing in Consumer Without BAG-03 Fix

**What goes wrong:** `Baggage.current()` returns empty inside `OrderListener.onOrder()` even though the baggage header arrived in the AMQP message. `baggage.customer-tier` attribute is null on the CONSUMER span.

**Why it happens:** The current `TracingMessageListenerAdvice.invoke()` (lines 128-141) creates a `try(Scope scope = span.makeCurrent())` for the inner listener body. But baggage from the extracted context is only accessible if `extracted.makeCurrent()` is called — not just `span.makeCurrent()`. The span starts with the correct parent but the extracted context's baggage is not pushed to the ThreadLocal.

**How to avoid:** BAG-03 fix — outer `extracted.makeCurrent()` scope wrapping the entire `invoke()` body (Pattern 5 above). The `BaggageSpanAttributeProcessor.onStart()` fires with `parentContext = extracted` which contains baggage regardless of the `makeCurrent()` state, but the `BaggageSpanAttributeProcessor` reads from `parentContext` not `Baggage.current()`. This means BAG-02 (span attribute stamping) works even WITHOUT the BAG-03 fix for the span itself — but `Baggage.current()` in consumer business logic (`OrderListener.onOrder()`) requires BAG-03. Both must be implemented.

**Warning signs:** Tempo shows `baggage.customer-tier=gold` on the CONSUMER span (from `BaggageSpanAttributeProcessor`) but `Baggage.current().getEntryValue("customer-tier")` inside `OrderListener.onOrder()` returns null.

### Pitfall 4: F7-4 — Scope Leak in Controller (try-with-resources missing)

**What goes wrong:** If `baggage.makeCurrent()` is called without `try-with-resources`, the ThreadLocal context is never restored. Every subsequent request on that thread inherits the stale baggage context.

**Why it happens:** `Scope` implements `AutoCloseable`. If `close()` is not called (via try-with-resources or explicit `finally`), the ThreadLocal stack is never popped.

**How to avoid:** Always use `try (Scope baggageScope = baggage.makeCurrent()) { ... }` in `OrderController.create()`. The code example in Pattern 4 above uses this correctly. Code review checklist item: every `makeCurrent()` call has a `try-with-resources` wrapper.

**Warning signs:** Second request on the same thread shows `baggage.customer-tier=gold` even when no `X-Customer-Tier` header was sent.

### Pitfall 5: F7-1 — Sampler Override via OTEL_TRACES_SAMPLER env var

**What goes wrong:** If `OTEL_TRACES_SAMPLER=parentbased_traceidratio` is exported in the shell or `mise.toml`, AND `opentelemetry-sdk-extension-autoconfigure` is on the classpath, the env var overrides the programmatic sampler.

**Why it doesn't apply here:** The demo deliberately does NOT include the autoconfigure artifact (CLAUDE.md constraint). Setting `OTEL_TRACES_SAMPLER` has no effect because the SDK is built manually without `AutoConfiguredOpenTelemetrySdk`. But the README must explicitly warn attendees not to set this env var to avoid confusion.

**How to avoid:** HSAMP-01 explicitly says the swap is programmatic (NOT via env var). README §16a callout: "Do NOT set `OTEL_TRACES_SAMPLER` — this demo uses manual SDK construction without autoconfigure; env var overrides are silently ignored (no autoconfigure on classpath)."

### Pitfall 6: `orderId` scope issue in OrderController

**What goes wrong:** If `orderId = orderService.place(payload)` is inside the `try(Scope)` block and `LOG.info("accepted orderId={}", orderId)` is outside, a compile error occurs because `orderId` is not in scope after the `try` block closes.

**How to avoid:** Declare `String orderId;` BEFORE the `try` block (Pattern 4 above). This is a standard Java scoping pattern, noted as a "Claude's Discretion" item in CONTEXT.md.

---

## Code Examples

### BaggageSpanAttributeProcessor.onStart() — reading baggage from parentContext

```java
// Source: OTel Java SDK SpanProcessor interface — Context7 /websites/opentelemetry_io
// and Baggage.fromContext() — Context7 /open-telemetry/opentelemetry-java
@Override
public void onStart(Context parentContext, ReadWriteSpan span) {
    Baggage baggage = Baggage.fromContext(parentContext);
    for (String key : allowedKeys) {
        String value = baggage.getEntryValue(key);
        if (value != null) {
            span.setAttribute("baggage." + key, value);
        }
    }
}
```

**Why `fromContext(parentContext)` not `Baggage.current()`:** At the time `onStart` fires, the new span has just been started but is not yet current. `parentContext` is the context that will parent this span — it contains the baggage set upstream. Using `Baggage.fromContext(parentContext)` is spec-correct; `Baggage.current()` would read the same value for synchronous in-thread flows but is less precise semantically. [CITED: opentelemetry.io/docs/languages/java/api — "Access current baggage with `Baggage.current()` or from a Context: `Baggage.fromContext(current())`"]

### OrderController baggage setup — Baggage.builder() pattern

```java
// Source: OTel Java Baggage API — Context7 /websites/opentelemetry_io
// "Manipulate and read Baggage in Java"
Baggage baggage = Baggage.builder().put("customer-tier", customerTier).build();
String orderId;
try (Scope baggageScope = baggage.makeCurrent()) {
    orderId = orderService.place(payload);
}
```

**Not using `Baggage.current().toBuilder()`:** Starting from `Baggage.builder()` (empty) is cleaner for this use case — the controller is the origin of baggage, not accumulating entries on top of existing baggage. [CITED: opentelemetry.io/docs/languages/java/api — "Baggage.empty().toBuilder().put('shopId', 'abc123').build()"]

### TracingMessageListenerAdvice restructuring — nested try-with-resources

```java
// Source: F7-2 mitigation pattern — PITFALLS.md §F7-2
// and Context7 /open-telemetry/opentelemetry-java Baggage cross-cutting concern
try (Scope ctxScope = extracted.makeCurrent()) {   // outer: makes baggage current
    Span span = tracer.spanBuilder(...)
        .setParent(extracted)   // LOAD-BEARING — preserved for teaching continuity
        .startSpan();
    try (Scope scope = span.makeCurrent()) {         // inner: makes CONSUMER span current
        return inv.proceed();
    } finally {
        span.end();
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Baggage.storeInContext(context)` (stateful) | `Baggage.builder().put().build().makeCurrent()` (immutable) | SDK 1.x (stable API) | Baggage is immutable; each `.put()` returns a new builder; `.makeCurrent()` scopes to try-with-resources |
| `Sampler.alwaysOn()` (100% sampling) | `Sampler.parentBased(Sampler.traceIdRatioBased(ratio))` (ratio) | Phase 16 introduction | SDK discards ~50% of root spans before OTLP export |
| Manual `Baggage.current()` reading in consumer | `BaggageSpanAttributeProcessor.onStart()` auto-stamping | Phase 16 introduction | Attendees see zero boilerplate in `OrderListener` — baggage attribute appears automatically via `SpanProcessor` |

**Note on `storeInContext()`:** The OTel Java API offers both `Baggage.makeCurrent()` (which stores baggage in the current context ThreadLocal via a Scope) and `baggage.storeInContext(existingContext)` (which returns a new Context with baggage embedded, without making it current). CONTEXT.md D-B1 specifies `makeCurrent()` — the simpler and more common idiom for controller-scoped baggage. [CITED: opentelemetry.io/docs/languages/java/api — "Calling Baggage.makeCurrent() sets Baggage.current() to the baggage until the scope is closed"]

---

## Environment Availability

Step 2.6: SKIPPED — Phase 16 is a pure SDK code change with no external dependencies. All tools required for development are confirmed available: Java 17 (Corretto 17.0.13), Maven 3.9.11, Docker 29.4.1. No new container images, CLI tools, or external services are needed. [VERIFIED: `java -version`, `mvn -version`, `docker --version`]

---

## Runtime State Inventory

Step 2.5: NOT APPLICABLE — Phase 16 is a feature addition (new processor, new API calls, sampler swap), not a rename/refactor/migration. No stored data, live service config, OS-registered state, secrets, or build artifacts embed names that will change. No data migration required.

---

## Security Domain

`security_enforcement` is enabled (absent from config → treated as enabled per `.planning/config.json` inspection). ASVS Level 1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Phase 16 adds no auth surface |
| V3 Session Management | no | No session state changes |
| V4 Access Control | no | No authorization changes |
| V5 Input Validation | yes | `X-Customer-Tier` header value propagated into baggage |
| V6 Cryptography | no | No cryptographic operations |

### V5 Input Validation — X-Customer-Tier Header

**Risk:** The `customerTier` string from `X-Customer-Tier` is set as a baggage entry (`Baggage.builder().put("customer-tier", customerTier)`) and ultimately stamped as a span attribute (`span.setAttribute("baggage.customer-tier", value)`). If a user supplies a malicious value:
- It appears in Tempo span attributes (observability backend, not user-facing)
- It propagates across AMQP and HTTP headers as the W3C `baggage` header value
- It is NOT stored in a database or returned to the user

**Threat assessment — LOW for this workshop demo:**
- OWASP context: baggage values in W3C baggage headers are bounded to 8192 bytes total. The SDK's `Baggage.builder().put()` accepts arbitrary strings; no SDK-level validation occurs.
- For the workshop, the allowlist in `BaggageSpanAttributeProcessor` (`Set.of("customer-tier")`) prevents arbitrary user-supplied keys from becoming span attributes (F7-3 mitigation). Only the `customer-tier` KEY is allowlisted.
- The VALUE for `customer-tier` is not validated — a user can send `X-Customer-Tier: <script>alert(1)</script>`. This appears in Tempo as a span attribute. Tempo renders attribute values as plain text, not HTML.
- ASVS 5.1.1 (generic input validation): No SQL injection, XSS, or path traversal risk from span attribute values stored in Tempo.

**Mitigation for workshop:** The load.sh D-B7 rotation (gold/silver/standard) demonstrates the INTENDED use of a bounded cardinality set. README §16b should mention: "In production, validate or normalize baggage values at the HTTP ingress (e.g., whitelist `[gold, silver, standard]`) before setting them in baggage to prevent arbitrary user data from flowing into your observability backend."

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Arbitrary string in baggage value | Information Disclosure (Tempo stores unvalidated data) | Allowlist in `BaggageSpanAttributeProcessor` limits which KEYS are stamped; VALUE validation is production concern, documented in README |
| Baggage header injection (SSRF via X-Customer-Tier) | Not applicable | Baggage values are strings stored in observability backend; not used for routing or HTTP calls |
| Context leak via Scope not closed | Tampering (incorrect trace parentage) | try-with-resources enforced in all new code (F7-4 mitigation) |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Baggage.fromContext(parentContext)` is the correct API to read baggage in `SpanProcessor.onStart()` | Code Examples | If wrong, might need `Baggage.current()` instead — functionally equivalent for synchronous flows but different semantics |

**All other claims verified:** Sampler API verified via Context7; SpanProcessor API verified via Context7; OrderController header pattern verified by codebase inspection; TracingMessageListenerAdvice restructuring verified by codebase inspection; `W3CBaggagePropagator` already wired verified by `TestOtelHolder.java` and `OtelSdkConfiguration.java` inspection.

---

## Open Questions

1. **`verify:head-sampling` — probabilistic assertion vs trace presence check**
   - What we know: `Sampler.traceIdRatioBased(0.5)` makes decisions based on trace ID hash — over 100 requests the expected value is ~50 traces, but variance is real
   - What's unclear: Whether to assert exact count (~50) or just presence (>0 traces). Asserting exact count in a scripted task risks flakiness.
   - Recommendation: Assert presence (at least one trace exists, proving the SDK is sampling and exporting). Document the "send 100 requests, observe ~50 in Tempo" as a manual verification step in the README, not an automated gate. This follows the pattern of `verify:http-client-spans` which asserts presence, not count.

2. **TestOtelHolder sampler for baggage test assertions**
   - What we know: `TestOtelHolder` uses `Sampler.alwaysOn()` (D-18). Phase 16 swaps production to `traceIdRatioBased(0.5)`.
   - What's unclear: Should integration tests assert the baggage attribute on spans under a specific ratio? No — tests use `alwaysOn()` so all spans are captured and assertions are deterministic. The D-18 note in `TestOtelHolder.java` must be updated to explain: "Test sampler stays `alwaysOn()` (not `traceIdRatioBased`) — D-18 test determinism overrides the Phase 16 production sampler swap."
   - Recommendation: Keep `Sampler.alwaysOn()` in test configuration. Add `BaggageSpanAttributeProcessor` to the test `SdkTracerProvider` builder only. Update D-18 comment to acknowledge Phase 16.

---

## Sources

### Primary (HIGH confidence)

- Context7 `/open-telemetry/opentelemetry-java` — `Sampler.traceIdRatioBased()`, `Sampler.parentBased()`, `SdkTracerProvider.builder().addSpanProcessor()`, `Baggage.builder().put().build().makeCurrent()`, `Baggage.fromContext()` API verified
- Context7 `/websites/opentelemetry_io` — `SpanProcessor` interface (`onStart(Context, ReadWriteSpan)`, `isStartRequired()`, `shutdown()`, `forceFlush()`), `Baggage` creation and scoping patterns
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` (lines 402-427) — current sampler definition and `buildTracerProvider()` structure verified by direct read
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` (lines 410-435) — confirms identical sampler pattern as producer
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` (lines 96-142) — current structure of `invoke()` method; BAG-03 change scope identified
- `producer-service/src/main/java/com/example/producer/api/OrderController.java` — current signature and X-Idempotency-Key pattern (D-B6 model)
- `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` — `W3CBaggagePropagator` already wired in test SDK confirmed (line 174-175); X-4 gap identified (no `BaggageSpanAttributeProcessor` in test `SdkTracerProvider`)
- `.planning/research/PITFALLS.md` §F7 — F7-1, F7-2, F7-3, F7-4 pitfall descriptions; §F2-3 double-filter trap
- `.planning/phases/16-head-sampling-w3c-baggage/16-CONTEXT.md` — all locked decisions D-B1..D-B7, D-S1..D-S3, D-L1..D-L3, D-R1..D-R3

### Secondary (MEDIUM confidence)

- `.planning/research/ARCHITECTURE.md` §Feature 7 — confirmed `W3CBaggagePropagator` is already in the composite propagator; `BaggageSpanAttributeProcessor` package placement in `otel-bootstrap` (D-S3 rationale)
- `scripts/load.sh` — confirmed pattern for curl-based stream with per-request variation (idempotency stream, lines 185-201); D-B7 baggage stream follows same structure
- `mise.toml` `verify:http-client-spans` task (lines 621-663) — confirmed retry loop + TraceQL query pattern for `verify:head-sampling` and `verify:baggage`

### Tertiary (LOW confidence — none)

All claims are HIGH or MEDIUM. The only ASSUMED claim (A1) concerns API semantics documented by Context7.

---

## Metadata

**Confidence breakdown:**
- Standard stack / required APIs: HIGH — verified via Context7 and codebase inspection; no new dependencies
- Architecture / component structure: HIGH — verified by codebase read and CONTEXT.md locked decisions
- Pitfalls: HIGH — sourced from `.planning/research/PITFALLS.md` which was verified against official OTel docs

**Research date:** 2026-05-04
**Valid until:** 2026-06-04 (OTel Java SDK 1.x API is stable; 30-day window appropriate)
