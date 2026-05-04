---
phase: 16-head-sampling-w3c-baggage
plan: "01"
subsystem: sdk-sampling
tags: [otel, sampling, head-sampling, traceIdRatioBased, mise-tasks]
dependency_graph:
  requires: []
  provides:
    - Sampler.traceIdRatioBased(0.5) in both OtelSdkConfiguration.buildTracerProvider()
    - verify:head-sampling mise task (Tempo TraceQL retry loop)
  affects:
    - producer-service OtelSdkConfiguration (sampling behavior changed)
    - consumer-service OtelSdkConfiguration (sampling behavior changed)
tech_stack:
  added: []
  patterns:
    - parentBased(traceIdRatioBased) sampler pattern for consistent cross-service sampling decisions
key_files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
    - mise.toml
decisions:
  - Programmatic sampler swap only (no OTEL_TRACES_SAMPLER env var per HSAMP-01/F7-1)
  - DOC-05 duplication: both files get identical comment + sampler change; consumer SERVICE_NAME and tracer scope unchanged
  - verify:head-sampling asserts at least 1 trace (existence gate); ~50% ratio is a probabilistic property verified manually via 100-request README demo
metrics:
  duration: "3min"
  completed: "2026-05-04"
  tasks: 2
  files: 3
---

# Phase 16 Plan 01: Head Sampling Sampler Swap Summary

Swapped `Sampler.alwaysOn()` to `Sampler.parentBased(Sampler.traceIdRatioBased(0.5))` in both services' `OtelSdkConfiguration.buildTracerProvider()` and added the `verify:head-sampling` mise task with a Tempo TraceQL retry loop.

## What Was Built

### Task 1: Sampler swap in both OtelSdkConfiguration files

Both `OtelSdkConfiguration.java` files (producer and consumer) had their `buildTracerProvider()` sampler changed from:

```java
Sampler sampler = Sampler.parentBased(Sampler.alwaysOn());
```

to:

```java
Sampler sampler = Sampler.parentBased(Sampler.traceIdRatioBased(0.5));
```

The forward-looking "For production, swap to..." comment block was replaced with the Phase 16 pedagogical comment explaining:
- Deterministic hash-based sampling (same trace ID always makes the same decision)
- Why `parentBased` is required (without it, each service re-rolls per-span, producing fragmented traces)
- The ~50% root span sampling behavior

The DOC-05 duplication rule was honored: both files received identical changes; the consumer's `SERVICE_NAME = "order-consumer"` and tracer scope `"com.example.consumer"` were not modified.

**Commit:** `c4eb12f`

### Task 2: verify:head-sampling mise task

Added `[tasks."verify:head-sampling"]` to `mise.toml` immediately after the existing `verify:http-client-spans` task. The task:

- Queries Tempo `:3200` via TraceQL `{resource.service.name="order-producer"}` for any trace from the producer service
- Uses a 6-attempt x 5s retry loop (30s tolerance) for Tempo cold start
- Asserts at least 1 trace exists (proves the SDK is exporting sampled traces, not zero)
- Documents the manual ~50% ratio verification procedure (100 requests → ~50 traces expected)

The task does NOT set `OTEL_TRACES_SAMPLER` env var — the change is programmatic only, as required by HSAMP-01 and the F7-1 pitfall note.

**Commit:** `a7f9bd7`

## Verification Results

1. `grep -c "traceIdRatioBased(0.5)" producer-service/.../OtelSdkConfiguration.java` → 3 (comment + code)
2. `grep -c "traceIdRatioBased(0.5)" consumer-service/.../OtelSdkConfiguration.java` → 3 (comment + code)
3. `grep -rn "Sampler.alwaysOn()" producer-service/src/main consumer-service/src/main` → exit code 1 (zero matches — PASS)
4. `mvn -q clean test-compile -pl producer-service,consumer-service -am` → exit code 0 (PASS)
5. `grep -c "verify:head-sampling" mise.toml` → 5 (task header + description + echo lines)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. The sampler change is fully wired; `verify:head-sampling` targets live Tempo infrastructure (requires `mise run infra:up` and traffic generation as documented in the task's prerequisite comment).

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes introduced. The sampler change operates entirely within the SDK's in-process pipeline before OTLP export.

## Self-Check: PASSED

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — FOUND (modified)
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — FOUND (modified)
- `mise.toml` — FOUND (modified, verify:head-sampling task appended)
- Commit `c4eb12f` (Task 1 sampler swap) — FOUND
- Commit `a7f9bd7` (Task 2 mise task) — FOUND
