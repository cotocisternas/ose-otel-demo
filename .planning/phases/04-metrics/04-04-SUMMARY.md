---
phase: 04-metrics
plan: "04"
subsystem: consumer-metrics
tags: [metrics, otel-sdk, observable-gauge, consumer, synthetic, queue-depth]
dependency_graph:
  requires: [04-01-meter-pipeline-refactor]
  provides: [consumer-gauge-METRIC-04]
  affects: [04-05-live-verification]
tech_stack:
  added: [ObservableLongGauge, ThreadLocalRandom]
  patterns: [separate-component-D18b, constructor-injection, observable-instrument, pre-destroy-cleanup]
key_files:
  created:
    - consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java
  modified: []
decisions:
  - "D-18b adopted over D-18a: gauge hosted in a dedicated @Component (not @PostConstruct inline in OtelSdkConfiguration), preserving the producer/consumer OtelSdkConfiguration mirror property (D-02)"
  - "D-19 ofLongs() flavor: integer queue-depth convention + honest type for int synthetic value"
  - "D-17 ThreadLocalRandom: thread-safe synthetic callback, no coupling to APP-04 AtomicInteger"
  - "D-16 consumer-side placement: queue depth semantically belongs to the consumer drain view"
metrics:
  duration: 2min
  completed_date: "2026-05-01"
  tasks: 1
  files_modified: 0
  files_created: 1
---

# Phase 4 Plan 04: Consumer Gauge (QueueDepthGauge) Summary

New `@Component` class `QueueDepthGauge` in `consumer-service` registers the `orders.queue.depth.estimate` `ObservableLongGauge` (METRIC-04) via constructor-injected `Meter` bean, using a `ThreadLocalRandom.current().nextInt(0, 50)` synthetic callback and `@PreDestroy` cleanup — D-18b separate-component pattern preserving the producer/consumer `OtelSdkConfiguration` mirror.

## What Was Built

### QueueDepthGauge.java (new file)

**Full file content:**

```java
package com.example.consumer.observability;

import java.util.concurrent.ThreadLocalRandom;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Registers the {@code orders.queue.depth.estimate} ObservableGauge
 * (METRIC-04) at consumer startup.
 * ... (full JavaDoc with D-16, D-17, D-18b, D-19, METRIC-04, synthetic-value caveat)
 */
@Component
public class QueueDepthGauge {

    private final ObservableLongGauge gauge;

    public QueueDepthGauge(Meter meter) {
        this.gauge = meter.gaugeBuilder("orders.queue.depth.estimate")
            .setDescription("Synthetic queue-depth estimate (workshop demo, not a real measurement)")
            .setUnit("{messages}")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(ThreadLocalRandom.current().nextInt(0, 50)));
    }

    @PreDestroy
    public void close() {
        gauge.close();
    }
}
```

Key properties:
- Package: `com.example.consumer.observability`
- Stereotype: `@Component` (picked up by `@SpringBootApplication` component scan rooted at `com.example.consumer`)
- Constructor: single `Meter` parameter — Spring constructor injection
- Gauge name: `orders.queue.depth.estimate` (METRIC-04 locked)
- Instrument flavor: `.ofLongs()` (D-19 — integer queue-depth convention)
- Callback: `measurement.record(ThreadLocalRandom.current().nextInt(0, 50))` (D-17 — synthetic, thread-safe)
- Cleanup: `@PreDestroy gauge.close()` — defensive close, readable shutdown point

### D-18a vs D-18b Choice

The planner documented two hosting options:
- **D-18a:** Inline `@PostConstruct` in `OtelSdkConfiguration.java` — rejected because it would introduce a consumer-only `@PostConstruct` absent from the producer's file, breaking the Phase 2 duplication-pedagogy mirror (D-02).
- **D-18b (adopted):** Separate `@Component` class — keeps `OtelSdkConfiguration.java` byte-for-byte symmetric (the 72-line diff confirmed in Plan 04-01 SUMMARY is unchanged). The gauge registration is one-shot side-effect boilerplate, more idiomatically isolated in a focused `@Component` anyway.

## Verification Results

### Acceptance criteria (all passed)

All 14 acceptance criteria from the plan verified:

```
file exists at correct path:           PASS
package com.example.consumer.observability: PASS
all 5 imports present:                 PASS
@Component annotation:                 PASS
public class QueueDepthGauge {:        PASS
private final ObservableLongGauge gauge: PASS
QueueDepthGauge(Meter meter):          PASS
gaugeBuilder("orders.queue.depth.estimate"): PASS
setDescription + setUnit("{messages}"): PASS
.ofLongs():                            PASS
.buildWithCallback(:                   PASS
ThreadLocalRandom.current().nextInt(0, 50): PASS
@PreDestroy + gauge.close():           PASS
single gaugeBuilder call (count=1):    PASS
JavaDoc references D-16/D-17/D-18b/D-19: PASS
METRIC-04 + synthetic caveat:          PASS
OtelSdkConfiguration.java UNMODIFIED:  PASS
```

### consumer-service compile

```
mvn -pl consumer-service compile
[INFO] BUILD SUCCESS
[INFO] Total time:  0.620 s
```

### OtelSdkConfiguration.java unchanged (D-18b preservation)

```
git diff --quiet HEAD -- consumer-service/.../config/OtelSdkConfiguration.java
EXIT 0 (no changes)
```

The D-02 producer/consumer OtelSdkConfiguration mirror property is preserved exactly as it stood after Plan 04-01.

## Deviations from Plan

None — plan executed exactly as written. Single new file created per spec; no modifications to any existing file.

## Files Modified

| File | Change |
|------|--------|
| `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` | NEW — 110 lines: @Component, ObservableLongGauge registration, @PreDestroy |

**New files:** 1
**Modified files:** 0
**New pom dependencies:** 0

## Known Stubs

None. The gauge value is explicitly documented as synthetic (METRIC-04 spec'd), not a placeholder — the synthetic nature IS the intended behavior for the workshop. The class JavaDoc and `setDescription(...)` text both name the synthetic-value caveat explicitly.

## Threat Flags

None. Single new `@Component` file — no new network surface, no new auth paths, no new file access patterns. The gauge callback is CPU-only (ThreadLocalRandom). T-04-04-02 (callback DoS mitigation) is implemented: the callback body is allocation-free and sub-microsecond.

## Self-Check: PASSED

File exists at correct path:
```
FOUND: consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java
```

Commit exists:
```
FOUND: c8f68ef feat(04-04): add QueueDepthGauge @Component with orders.queue.depth.estimate ObservableLongGauge
```
