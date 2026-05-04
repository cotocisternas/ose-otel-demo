---
phase: 16-head-sampling-w3c-baggage
plan: "02"
subsystem: sdk-baggage
tags: [otel, baggage, span-processor, w3c-baggage, BAG-02]
dependency_graph:
  requires:
    - 16-01 (sampler swap — buildTracerProvider() block must exist before this plan modifies it)
  provides:
    - BaggageSpanAttributeProcessor.java in otel-bootstrap/context/ package
    - baggageProcessor registered FIRST in both OtelSdkConfiguration.buildTracerProvider()
  affects:
    - producer-service OtelSdkConfiguration (baggageProcessor added before BatchSpanProcessor)
    - consumer-service OtelSdkConfiguration (baggageProcessor added before BatchSpanProcessor)
    - otel-bootstrap/pom.xml (opentelemetry-sdk provided scope added)
tech_stack:
  added:
    - opentelemetry-sdk provided scope in otel-bootstrap/pom.xml (enables SpanProcessor/ReadWriteSpan/ReadableSpan/CompletableResultCode compilation)
  patterns:
    - SpanProcessor allowlist pattern — stamp only named baggage keys as span attributes (F7-3 cardinality mitigation)
    - Processor ordering — baggageProcessor BEFORE BatchSpanProcessor so baggage is stamped before enqueueing (D-S1)
key_files:
  created:
    - otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java
  modified:
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
    - otel-bootstrap/pom.xml
decisions:
  - BaggageSpanAttributeProcessor reads Baggage.fromContext(parentContext) not Baggage.current() — spec-correct; parentContext is the context that parented this span
  - Set.copyOf(allowedKeys) — immutable allowlist; no runtime cardinality expansion (T-16-05)
  - opentelemetry-sdk added as provided scope in otel-bootstrap — pattern matches spring-rabbit/spring-aop/spring-web (consuming services supply the artifact at runtime)
  - DOC-05 honored — both OtelSdkConfiguration files receive identical changes; consumer SERVICE_NAME and tracer scope unchanged
metrics:
  duration: "2min"
  completed: "2026-05-04"
  tasks: 2
  files: 4
---

# Phase 16 Plan 02: BaggageSpanAttributeProcessor Summary

BaggageSpanAttributeProcessor created in otel-bootstrap/context/ and registered as the first SpanProcessor in both services' SdkTracerProvider builders, stamping `baggage.customer-tier` on every span start before BatchSpanProcessor enqueues.

## What Was Built

### Task 1: BaggageSpanAttributeProcessor in otel-bootstrap/context/

Created `otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java` implementing `SpanProcessor`:

- `onStart(Context parentContext, ReadWriteSpan span)` reads `Baggage.fromContext(parentContext)` and stamps `baggage.<key>` for each key in the allowlist
- Constructor takes `Set<String> allowedKeys` and defensively copies via `Set.copyOf()` (immutable — T-16-05)
- `isStartRequired()` returns `true`; `isEndRequired()` returns `false` (onEnd is no-op)
- `shutdown()` and `forceFlush()` return `CompletableResultCode.ofSuccess()` (stateless)
- No `@Component` — registered programmatically in `buildTracerProvider()`
- No `asMap()` iteration — only the allowlist keys are stamped (F7-3 cardinality mitigation)

**Commit:** `cb15e89`

### Task 2: Register BaggageSpanAttributeProcessor in both OtelSdkConfiguration files + pom fix

Updated both `OtelSdkConfiguration.buildTracerProvider()` methods to create and register `BaggageSpanAttributeProcessor` as the FIRST span processor:

```java
// ----- BaggageSpanAttributeProcessor -----
BaggageSpanAttributeProcessor baggageProcessor =
    new BaggageSpanAttributeProcessor(Set.of("customer-tier"));

return SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(sampler)
    .addSpanProcessor(baggageProcessor)   // FIRST — stamps baggage before batching
    .addSpanProcessor(spanProcessor)       // BatchSpanProcessor second
    .build();
```

Added imports `java.util.Set` and `com.example.otel.context.BaggageSpanAttributeProcessor` to both files.

Also added `opentelemetry-sdk` as `provided` scope dependency in `otel-bootstrap/pom.xml` (Rule 3 deviation — see below) to enable compilation of `SpanProcessor`, `ReadWriteSpan`, `ReadableSpan`, `CompletableResultCode` types.

**Commit:** `10aafdc`

## Verification Results

1. `test -f otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java` — PASS
2. `grep -rn "BaggageSpanAttributeProcessor" producer-service/src/main/java consumer-service/src/main/java` — 8 hits across both OtelSdkConfiguration files (import + comment + instantiation + constructor call in each file)
3. Order verified: `addSpanProcessor(baggageProcessor)` appears at line 436 (producer) and 444 (consumer), each BEFORE `addSpanProcessor(spanProcessor)` on the next line
4. `mvn -q clean compile -pl otel-bootstrap,producer-service,consumer-service -am` exits 0 — PASS

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added opentelemetry-sdk provided scope to otel-bootstrap/pom.xml**

- **Found during:** Task 2 — `mvn compile` failed with `package io.opentelemetry.sdk.common does not exist`
- **Issue:** `BaggageSpanAttributeProcessor` uses `SpanProcessor`, `ReadWriteSpan`, `ReadableSpan`, and `CompletableResultCode` from `opentelemetry-sdk`. The otel-bootstrap module only had `opentelemetry-api` — the SDK dependency was missing.
- **Fix:** Added `opentelemetry-sdk` with `<scope>provided</scope>` to otel-bootstrap/pom.xml, following the same provided-scope pattern as `spring-rabbit`, `spring-aop`, and `spring-web` (consuming services supply the artifact at runtime). BOM-managed — no explicit version needed.
- **Files modified:** `otel-bootstrap/pom.xml`
- **Commit:** `10aafdc` (bundled with Task 2 commit)

## Known Stubs

None. `BaggageSpanAttributeProcessor` is fully wired into both SdkTracerProvider builders. Plan 16-03 will wire the baggage injection on the producer side (HTTP header → baggage) and extraction on the consumer side so the `baggage.customer-tier` attribute actually has a value at runtime.

## Threat Flags

None. No new network endpoints, auth paths, or schema changes. The processor operates in-process within the SDK span pipeline; the only trust boundary is `parentContext → span attributes`, which is mitigated by the `Set.of("customer-tier")` allowlist (T-16-04 disposition: mitigate).

## Self-Check: PASSED

- `otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java` — FOUND (created)
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — FOUND (modified)
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — FOUND (modified)
- `otel-bootstrap/pom.xml` — FOUND (modified)
- Commit `cb15e89` (Task 1: BaggageSpanAttributeProcessor) — FOUND
- Commit `10aafdc` (Task 2: register in both OtelSdkConfiguration + pom fix) — FOUND
