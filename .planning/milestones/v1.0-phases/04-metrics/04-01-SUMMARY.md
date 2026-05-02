---
phase: 04-metrics
plan: "01"
subsystem: sdk-bootstrap
tags: [metrics, otel-sdk, refactor, sdk-configuration, producer, consumer]
dependency_graph:
  requires: []
  provides: [meter-pipeline, meter-bean-producer, meter-bean-consumer]
  affects: [04-02, 04-03, 04-04]
tech_stack:
  added: [OtlpGrpcMetricExporter, PeriodicMetricReader, SdkMeterProvider]
  patterns: [helper-extraction, sibling-pipeline, constructor-injection, env-var-fallback]
key_files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
decisions:
  - "Refactored openTelemetry() @Bean into orchestrator + two helper methods (buildTracerProvider + buildMeterProvider); tracer pipeline lifted verbatim (no behavior change)"
  - "10-second PeriodicMetricReader interval chosen for workshop tight-feedback-loop; production default is 60s"
  - "Single DEFAULT_OTLP_ENDPOINT constant shared by both span exporter and metric exporter (D-04)"
  - "HttpServerSpanFilter factory in producer updated to (Tracer, Meter) for Plan 04-03 constructor injection"
metrics:
  duration: 5min
  completed_date: "2026-05-01"
  tasks: 2
  files_modified: 2
  files_created: 0
---

# Phase 4 Plan 01: Meter Pipeline Refactor Summary

Refactored both `OtelSdkConfiguration.java` files to add the SDK metrics pipeline as a sibling helper next to the trace pipeline — 10-second `PeriodicMetricReader` interval, `OtlpGrpcMetricExporter` reusing the same `DEFAULT_OTLP_ENDPOINT` constant and env-var fallback, `SdkMeterProvider` with the same `Resource`, and a new `Meter` `@Bean` per service.

## What Was Built

### Producer-service OtelSdkConfiguration.java

**@Bean orchestrator shape (post-refactor):**

```java
@Bean(destroyMethod = "close")
OpenTelemetry openTelemetry() {
    // Resource built once, passed to BOTH helpers (D-05)
    Resource resource = Resource.getDefault().merge(
        Resource.create(Attributes.builder()
            .put(ServiceAttributes.SERVICE_NAME, "order-producer")
            .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
            .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
            .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop")
            .build()));

    SdkTracerProvider tracerProvider = buildTracerProvider(resource);
    SdkMeterProvider  meterProvider  = buildMeterProvider(resource);

    ContextPropagators propagators = ContextPropagators.create(
        TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()));

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)   // single new line (D-01)
        .setPropagators(propagators)
        .build();
}
```

**Helper signatures:**

```java
private SdkTracerProvider buildTracerProvider(Resource resource) { /* Phase 2 verbatim */ }
private SdkMeterProvider  buildMeterProvider(Resource resource)  { /* Phase 4 addition */ }
```

**Meter @Bean (D-06):**

```java
@Bean
Meter meter(OpenTelemetry openTelemetry) {
    return openTelemetry.getMeter("com.example.producer");
}
```

**HttpServerSpanFilter factory (updated for Plan 04-03):**

```java
@Bean
HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter) {
    return new HttpServerSpanFilter(tracer, meter);
}
```

### Consumer-service OtelSdkConfiguration.java

Same refactor as producer, minus the `HttpServerSpanFilter` factory (D-07 carryforward). Meter `@Bean` scope is `"com.example.consumer"`.

```java
@Bean
Meter meter(OpenTelemetry openTelemetry) {
    return openTelemetry.getMeter("com.example.consumer");
}
```

The "Why no HttpServerSpanFilter here?" class JavaDoc paragraph is preserved unchanged.

## Verification Results

### Comment density grep (D-20 / DOC-03)

```
producer OtelSdkConfiguration.java: 231 comment lines (>= 40 target)
consumer OtelSdkConfiguration.java: 221 comment lines (>= 40 target)
```

### Mirror diff line count

```
diff producer .../OtelSdkConfiguration.java consumer .../OtelSdkConfiguration.java | grep -cE '^[<>]'
72 lines
```

Divergences are exactly as specified: package, service.name string, tracer/meter scope names, pom.xml cross-references, plan references in JavaDoc, Meter `@Bean` JavaDoc mentioning different call-sites, and the producer-only `HttpServerSpanFilter` factory.

### consumer-service compile

```
mvn -pl consumer-service compile -q
EXIT 0 (BUILD SUCCESS)
```

### producer-service compile status

Producer-service does NOT compile cleanly because `HttpServerSpanFilter(Tracer, Meter)` constructor does not yet exist — the factory now passes `meter` but the filter class still has a single-argument `HttpServerSpanFilter(Tracer)` constructor. This is **expected by design** — Plan 04-03 (Wave 2) will update `HttpServerSpanFilter` to accept `(Tracer, Meter)`. The consumer-service compile (which has no `HttpServerSpanFilter`) verifies the meter pipeline code is syntactically and import-wise correct.

### mise verify:bom

```
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
EXIT 0
```

No new `io.opentelemetry` artifacts added — `opentelemetry-exporter-otlp` already on classpath from Phase 2 ships span + metric + log exporters in one jar.

## Deviations from Plan

None — plan executed exactly as written. Both edits were straightforward lifts of the plan's verbatim code snippets. The consumer compile verified cleanly; the producer compile failure is explicitly documented in the plan as the expected state until Plan 04-03.

## Files Modified

| File | Change |
|------|--------|
| `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | +159/-24 lines: extract buildTracerProvider, add buildMeterProvider, add Meter @Bean, update HttpServerSpanFilter factory, update JavaDoc |
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | +152/-22 lines: mirror refactor minus HttpServerSpanFilter, Meter @Bean scope "com.example.consumer" |

**New files:** 0
**New pom dependencies:** 0

## Known Stubs

None. Both files are pure SDK bootstrap configuration with no data flow to UI rendering and no hardcoded placeholder values.

## Threat Flags

None. The new `OtlpGrpcMetricExporter.setEndpoint(...)` calls in `buildMeterProvider` use the same `DEFAULT_OTLP_ENDPOINT` constant and `System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")` pattern as the existing span exporter — no new network surface introduced beyond what Phase 2 already established. This is covered by T-04-01-01 in the plan's threat model (accepted, low severity, localhost-only workshop).

## Self-Check: PASSED
