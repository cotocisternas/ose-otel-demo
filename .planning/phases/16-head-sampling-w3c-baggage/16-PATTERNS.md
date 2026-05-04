# Phase 16: Head Sampling + W3C Baggage - Pattern Map

**Mapped:** 2026-05-04
**Files analyzed:** 9
**Analogs found:** 9 / 9

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | config | request-response | itself (prior state) | exact — one-line sampler swap + addSpanProcessor call |
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | config | request-response | producer `OtelSdkConfiguration.java` | exact — DOC-05 duplication |
| `otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java` | utility | request-response | `otel-bootstrap/amqp/TracingMessageListenerAdvice.java` (SpanProcessor interface shape); full implementation supplied in RESEARCH.md Pattern 2 | role-match (new OTel SDK component) |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` | middleware | event-driven | itself (prior state) | exact — structural wrap of existing try-with-resources |
| `producer-service/src/main/java/com/example/producer/api/OrderController.java` | controller | request-response | itself (prior state) | exact — adds @RequestHeader param + baggage try-with-resources |
| `scripts/load.sh` | utility | batch | itself lines 185-201 (idempotency stream) | exact — same curl-loop pattern |
| `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` | config | request-response | itself (prior state) | exact — addSpanProcessor insertion point |
| `mise.toml` | config | batch | itself lines 622-663 (`verify:http-client-spans`) | exact — same retry loop + TraceQL query pattern |
| `README.md` | documentation | — | itself (Phase 14/15 sections) | exact — additive §16 section |

---

## Pattern Assignments

### `producer-service/.../config/OtelSdkConfiguration.java` (config, request-response)

**Analog:** itself — `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`

**Imports to add** (no new imports; `Set` may already be present via existing usages — add if missing):
```java
import java.util.Set;
import com.example.otel.context.BaggageSpanAttributeProcessor;
```

**Sampler swap** (line 420 — one-line change):
```java
// BEFORE:
Sampler sampler = Sampler.parentBased(Sampler.alwaysOn());

// AFTER (HSAMP-01 — Phase 16 swaps to 50% ratio sampling):
Sampler sampler = Sampler.parentBased(Sampler.traceIdRatioBased(0.5));
```

**Comment block to update** (lines 402–419) — replace the forward-looking "For production, swap to..." comment with:
```java
// ----- Sampler: parent-based, 50% ratio sampling (Phase 16 / HSAMP-01) -----
//
// Phase 16 swapped this to 50% ratio sampling. See README §16a for the
// head-vs-tail sampling contrast.
//
// Sampler.parentBased(Sampler.traceIdRatioBased(0.5)) means:
//   - If there is a parent SpanContext (from upstream propagation),
//     respect its sampling decision via the traceparent flag byte.
//   - If there is NO parent (root span), sample ~50% based on a hash
//     of the trace ID (deterministic: same trace ID always produces
//     the same sampling decision — all spans within a trace are
//     sampled or dropped together).
//
// Why parentBased matters: without it, traceIdRatioBased(0.5) applied
// directly would re-roll per span — producing trace fragments where the
// consumer span is sampled but the producer parent is not.
// parentBased ensures one decision per trace, made at the root.
```

**BaggageSpanAttributeProcessor registration** (inside `buildTracerProvider()`, D-S1 — add BEFORE the existing `BatchSpanProcessor` line at the end of the method):
```java
// ----- BaggageSpanAttributeProcessor: stamp allowlisted baggage keys as span attributes -----
//
// Added FIRST so baggage is stamped before BatchSpanProcessor enqueues the span.
// Set.of("customer-tier") is the allowlist — F7-3 mitigation against cardinality explosion.
BaggageSpanAttributeProcessor baggageProcessor =
    new BaggageSpanAttributeProcessor(Set.of("customer-tier"));

// ----- TracerProvider: assembles resource + sampler + processor -----
return SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(sampler)
    .addSpanProcessor(baggageProcessor)   // FIRST — stamps baggage before batching
    .addSpanProcessor(spanProcessor)       // BatchSpanProcessor second
    .build();
```

**Current `buildTracerProvider()` return block** (lines 422–427 — shows what the end of the method looks like before the edit):
```java
return SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(sampler)
    .addSpanProcessor(spanProcessor)
    .build();
```

---

### `consumer-service/.../config/OtelSdkConfiguration.java` (config, request-response)

**Analog:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`

Identical changes to the producer-service file above (DOC-05 per-service duplication). The consumer version's sampler is at line 428 and the `buildTracerProvider()` return block is at lines 431–435.

**Key differences from producer** (do NOT change these):
- `SERVICE_NAME` stays `"order-consumer"`
- Tracer scope name stays `"com.example.consumer"`

**Import to add:**
```java
import java.util.Set;
import com.example.otel.context.BaggageSpanAttributeProcessor;
```

---

### `otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java` (utility, request-response)

**Analog:** New class — no prior analog in `otel-bootstrap/context/` (package does not yet exist). The SpanProcessor interface shape is observable in RESEARCH.md Pattern 2.

**Package declaration and directory:**
```
otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java
```

**Full class** (from RESEARCH.md Pattern 2 — complete implementation):
```java
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

**Constructor-injection pattern** (mirrors D-H10 from Phase 15 — all otel-bootstrap classes take configuration via constructor):
```java
// Pattern: constructor receives Set<String> allowedKeys at build time
// Caller passes: new BaggageSpanAttributeProcessor(Set.of("customer-tier"))
// Set.copyOf() defends against mutation of the caller's set.
public BaggageSpanAttributeProcessor(Set<String> allowedKeys) {
    this.allowedKeys = Set.copyOf(allowedKeys);
}
```

---

### `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` (middleware, event-driven)

**Analog:** itself — `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java`

**Current `invoke()` body** (lines 96–141 — read before editing):
```java
@Override
public Object invoke(MethodInvocation inv) throws Throwable {
    Object data = inv.getArguments()[1];
    if (!(data instanceof Message message)) {
        return inv.proceed();
    }
    MessageProperties props = message.getMessageProperties();
    TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
    Context extracted = propagator.extract(Context.current(), props, GETTER);

    String exchange = props.getReceivedExchange();
    String routingKey = props.getReceivedRoutingKey();

    Span span = tracer.spanBuilder(exchange + " process")
        .setParent(extracted)                          // <-- LOAD-BEARING (ROADMAP SC #1)
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

**After (BAG-03 / D-L1 — add outer `extracted.makeCurrent()` wrapper):**

The change is: wrap everything after the `extracted = propagator.extract(...)` line inside a new `try (Scope ctxScope = extracted.makeCurrent())`. No other changes. The `setParent(extracted)` line is preserved verbatim (D-L3).

```java
Context extracted = propagator.extract(Context.current(), props, GETTER);
String exchange = props.getReceivedExchange();
String routingKey = props.getReceivedRoutingKey();

// BAG-03 / D-L1: outer scope makes extracted context (including baggage) current
// for the ENTIRE listener body, not just for span parenting.
// F7-2 mitigation: Baggage.current() inside OrderListener.onOrder() returns
// the propagated baggage only after this makeCurrent() call.
try (Scope ctxScope = extracted.makeCurrent()) {
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

**No new imports required** — `Scope` is already imported at line 9.

---

### `producer-service/src/main/java/com/example/producer/api/OrderController.java` (controller, request-response)

**Analog:** itself — `producer-service/src/main/java/com/example/producer/api/OrderController.java`

**Imports to add:**
```java
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
```

**Existing @RequestHeader pattern** (line 27 — shows the pattern to copy for X-Customer-Tier):
```java
@RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader
```

**Current method signature** (lines 25–27):
```java
public ResponseEntity<Map<String, String>> create(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader) {
```

**After (D-B1, D-B6 — add X-Customer-Tier parameter with default):**
```java
public ResponseEntity<Map<String, String>> create(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
        @RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard")
            String customerTier) {
```

**Current method body** (lines 28–54 — shows where the baggage scope is inserted):
```java
LOG.info("received POST /orders payload={}", payload);

// Phase 8: idempotency gate ...
String idempotencyKey = idempotencyKeyHeader != null ...;
if (idempotencyKey != null) { ... }

String orderId = orderService.place(payload);
LOG.info("accepted orderId={}", orderId);
return ResponseEntity.accepted().body(Map.of("orderId", orderId));
```

**After (D-B1, D-B3 — baggage scope wraps only `orderService.place()`):**
```java
LOG.info("received POST /orders payload={}", payload);

// Phase 8: idempotency gate (unchanged — D-B3: scope does NOT wrap this)
String idempotencyKey = idempotencyKeyHeader != null
    ? idempotencyKeyHeader
    : (payload.get("orderId") instanceof String s ? s : null);

if (idempotencyKey != null) {
    IdempotencyService.Result result = idempotencyService.checkAndMark(idempotencyKey);
    if (result == IdempotencyService.Result.SEEN) {
        LOG.info("idempotency duplicate detected: key={}", idempotencyKey);
        return ResponseEntity.status(409)
            .body(Map.of("status", "duplicate", "idempotencyKey", idempotencyKey));
    }
}

// BAG-01 / D-B1: set customer-tier baggage for the AMQP publish scope.
// Baggage is active only while orderService.place() executes — AMQP injection
// (TracingMessagePostProcessor) and HTTP injection (TracingClientHttpRequestInterceptor)
// both run inside this scope and pick up the baggage: header automatically via
// W3CBaggagePropagator. try-with-resources is mandatory (F7-4: leaked Scope causes
// context pollution on the next request on this thread).
Baggage baggage = Baggage.builder().put("customer-tier", customerTier).build();
String orderId;
try (Scope baggageScope = baggage.makeCurrent()) {
    orderId = orderService.place(payload);
}
// orderId declared before try block — required for it to be in scope here (Pitfall 6)
LOG.info("accepted orderId={}", orderId);
return ResponseEntity.accepted().body(Map.of("orderId", orderId));
```

---

### `scripts/load.sh` (utility, batch)

**Analog:** itself — `scripts/load.sh` lines 185–201 (idempotency stream with curl loop)

**Existing curl stream pattern** (lines 185–201 — copy this structure):
```bash
if [[ "$IDEMPOTENT_RPS" -gt 0 ]]; then
  (
    sleep_interval=$(awk "BEGIN {printf \"%.3f\", 1.0 / ${IDEMPOTENT_RPS}}")
    idempotent_payload='{"sku":"WIDGET-IDEMPOTENT","quantity":1,"priority":"standard"}'
    while :; do
      key=$(< /proc/sys/kernel/random/uuid)
      curl -sS -o /dev/null \
        -X POST \
        -H "Content-Type: application/json" \
        -H "X-Idempotency-Key: ${key}" \
        -d "${idempotent_payload}" \
        "${TARGET}" || true
      sleep "${sleep_interval}"
    done
  ) &
  PID_IDEMPOTENT=$!
fi
```

**New baggage stream** (D-B7 — add after the idempotency stream block):
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

---

### `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` (config, request-response)

**Analog:** itself — `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java`

**Current `SdkTracerProvider` builder** (lines 143–147):
```java
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.alwaysOn())
    .addSpanProcessor(SimpleSpanProcessor.create(SPANS))
    .build();
```

**After (X-4 mitigation — add BaggageSpanAttributeProcessor BEFORE SimpleSpanProcessor):**
```java
// X-4 mitigation: register BaggageSpanAttributeProcessor so integration test
// assertions on baggage.customer-tier attribute work.
// Sampler stays alwaysOn() per D-18 — test determinism overrides the Phase 16
// production sampler swap (traceIdRatioBased). Every span is captured in tests.
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.alwaysOn())
    .addSpanProcessor(new BaggageSpanAttributeProcessor(Set.of("customer-tier")))  // NEW
    .addSpanProcessor(SimpleSpanProcessor.create(SPANS))
    .build();
```

**Import to add:**
```java
import java.util.Set;
import com.example.otel.context.BaggageSpanAttributeProcessor;
```

---

### `mise.toml` (config, batch)

**Analog:** itself — `mise.toml` lines 622–663 (`verify:http-client-spans` task)

**Existing retry loop + TraceQL query pattern** (lines 636–657):
```toml
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

echo "verify:http-client-spans: querying Tempo for ..."
for i in $(seq 1 $ATTEMPTS); do
  RESULT=$(curl -sS --data-urlencode 'q={...TraceQL...}' 'http://localhost:3200/api/search?limit=5' 2>&1) || {
    LAST_ERR="curl :3200 failed: $RESULT"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — Tempo not ready ($LAST_ERR); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:http-client-spans timed out ..."
    exit 1
  }
  if printf '%s' "$RESULT" | jq -e '.traces | length > 0' >/dev/null 2>&1; then break; fi
  LAST_ERR="no ... in Tempo yet"
  [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — $LAST_ERR; retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:http-client-spans — $LAST_ERR after $((ATTEMPTS * SLEEP_SECS))s."
  exit 1
done
echo "verify:http-client-spans: GREEN — ..."
"""
```

**New `verify:head-sampling` task** (HSAMP-03 — copy pattern, change TraceQL):
```toml
[tasks."verify:head-sampling"]
description = "Phase 16 invariant: ~50% of traces reach Tempo under head sampling (traceIdRatioBased(0.5))"
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

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

**New `verify:baggage` task** (BAG-04 — copy pattern, change TraceQL to span attribute query):
```toml
[tasks."verify:baggage"]
description = "Phase 16 invariant: baggage.customer-tier attribute present on both producer and consumer spans in Tempo"
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

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

---

### `README.md` (documentation)

**Analog:** README.md Phase 14/15 sections (~100–150 lines per sub-section per D-R2).

**Structure to follow:** Two sub-sections `### Step 16a: Head Sampling` and `### Step 16b: W3C Baggage` under `## Step 16: Head Sampling + W3C Baggage`.

16a must contain:
- F2-3 warning callout box FIRST (D-R1)
- Sampler swap code diff
- Five-dimension head-vs-tail comparison table (D-R3): Where / Sees / Bandwidth / Decides on / Trade-off
- `verify:head-sampling` invocation example

16b must contain:
- `BaggageSpanAttributeProcessor` walkthrough
- `OrderController.create()` baggage setup code
- `TracingMessageListenerAdvice` outer scope change
- D-B5 teaching moment callout (SERVER span missing `baggage.customer-tier`)
- `verify:baggage` invocation example with `curl -H "X-Customer-Tier: gold"`

---

## Shared Patterns

### try-with-resources for every Scope
**Source:** `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` lines 128–140
**Apply to:** `OrderController.java` (new `baggage.makeCurrent()`), `TracingMessageListenerAdvice.java` (new `extracted.makeCurrent()`)
```java
try (Scope scope = something.makeCurrent()) {
    // work
} catch (Throwable t) {
    // record if span
    throw t;
} finally {
    // end span if applicable
}
```
Every `makeCurrent()` call MUST use try-with-resources (F7-4 — Scope leak causes context pollution across requests).

### `addSpanProcessor()` ordering: custom processor BEFORE BatchSpanProcessor
**Source:** RESEARCH.md Pattern 3 (D-S1)
**Apply to:** both `OtelSdkConfiguration.buildTracerProvider()` and `TestOtelHolder.get()` `SdkTracerProvider` builder
```java
SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(sampler)
    .addSpanProcessor(baggageProcessor)   // FIRST
    .addSpanProcessor(spanProcessor)       // BatchSpanProcessor / SimpleSpanProcessor second
    .build();
```

### Constructor-injected configuration for otel-bootstrap classes
**Source:** `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` lines 91–94
**Apply to:** `BaggageSpanAttributeProcessor` constructor
```java
// Pattern: constructor receives immutable configuration at build time
public TracingMessageListenerAdvice(OpenTelemetry openTelemetry, Tracer tracer) {
    this.openTelemetry = openTelemetry;
    this.tracer = tracer;
}
// BaggageSpanAttributeProcessor mirrors this:
public BaggageSpanAttributeProcessor(Set<String> allowedKeys) {
    this.allowedKeys = Set.copyOf(allowedKeys);
}
```

### Tempo retry loop for mise verify tasks
**Source:** `mise.toml` lines 622–663 (`verify:http-client-spans`)
**Apply to:** `verify:head-sampling` and `verify:baggage` tasks
```bash
ATTEMPTS=6; SLEEP_SECS=5
curl --data-urlencode 'q={TraceQL}' http://localhost:3200/api/search?limit=5
jq -e '.traces | length > 0'
```

---

## No Analog Found

All 9 files have analogs. `BaggageSpanAttributeProcessor` is new but its complete implementation is supplied verbatim in RESEARCH.md Pattern 2 — the planner can copy it directly rather than deriving from an analog.

---

## Critical Pitfalls (planner must address in plan actions)

| Pitfall | ID | Where to mitigate |
|---------|----|--------------------|
| F2-3 double-filter trap (50% × 20% = 10%) | D-R1 | README §16a warning callout BEFORE code |
| X-4 TestOtelConfiguration divergence | RESEARCH Pitfall 2 | Plan 16-03: update `TestOtelHolder.get()` |
| F7-2 baggage missing in consumer without BAG-03 | RESEARCH Pitfall 3 | Plan 16-03: `TracingMessageListenerAdvice` outer scope |
| F7-4 Scope leak without try-with-resources | RESEARCH Pitfall 4 | `OrderController.create()` baggage scope |
| `orderId` Java scoping | RESEARCH Pitfall 6 | Declare `String orderId;` BEFORE try block |
| D-B5 SERVER span missing `baggage.customer-tier` | intentional teaching moment | README §16b callout |

---

## Metadata

**Analog search scope:** `producer-service/`, `consumer-service/`, `otel-bootstrap/`, `integration-tests/`, `scripts/`, `mise.toml`
**Files scanned:** 9 source files read directly
**Pattern extraction date:** 2026-05-04
