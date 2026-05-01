---
phase: 04-metrics
reviewed: 2026-05-01T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java
  - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - producer-service/src/main/java/com/example/producer/domain/OrderService.java
  - mise.toml
  - README.md
findings:
  critical: 0
  warning: 6
  info: 1
  total: 7
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-05-01
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

All seven correctness constraints from the phase brief are satisfied: histogram unit is `"s"` (not `"ms"`), `requestDuration.record()` fires in the `finally` block before `span.end()`, the `LongCounter` is built once in the constructor, no `opentelemetry-sdk-extension-autoconfigure` dependency was added, `@Bean(destroyMethod="close")` is unchanged on the `openTelemetry()` bean, `ObservableLongGauge` uses `.ofLongs()`, and the producer/consumer `OtelSdkConfiguration.java` files maintain their mirror-with-small-diff property. No security vulnerabilities or data-loss risks were found.

All six findings below are documentation defects introduced during Phase 4 that are specific hazards in a workshop codebase — attendees read the comments and JavaDoc as authoritative teaching text, so inaccuracies have a higher impact here than in a production codebase.

## Warnings

### WR-01: Stale Tracer @Bean JavaDoc Lists OrderPublisher as an Injection Target

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:300`
**Issue:** The `tracer()` @Bean JavaDoc says "Workshop attendees inject this Tracer into OrderService, OrderPublisher, and HttpServerSpanFilter via constructor injection". Since Phase 3, `OrderPublisher` no longer injects `Tracer` — the `PRODUCER` span moved to `TracingMessagePostProcessor` in `otel-bootstrap`. `OrderPublisher`'s constructor currently takes only `RabbitTemplate`. Workshop attendees following this comment will look for a Tracer parameter in `OrderPublisher` and find none, breaking their mental model.
**Fix:** Remove "OrderPublisher" from the list. Updated text:
```java
/**
 * Tracer for instrumentation scope "com.example.producer".
 *
 * The scope name typically matches the package or library being
 * instrumented. Workshop attendees inject this Tracer into
 * OrderService and HttpServerSpanFilter via constructor injection (D-02).
 * Tracer is thread-safe; one bean is reused across all callers.
 */
```

---

### WR-02: Stale Future-Tense Comment in httpServerSpanFilter @Bean

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:341-343`
**Issue:** The `httpServerSpanFilter()` @Bean JavaDoc contains: "The Meter parameter is wired here so Plan 04-03 can constructor-inject the http.server.request.duration DoubleHistogram. The HttpServerSpanFilter constructor will be updated in Plan 04-03 to accept (Tracer, Meter)." This is planning-phase language. Plan 04-03 is already implemented — the constructor already accepts `(Tracer, Meter)`. Attendees reading "will be updated" will be confused about whether this work was done.
**Fix:** Replace with descriptive text about the current (implemented) state:
```java
/**
 * Wraps every NON-/actuator HTTP request in a SERVER span (TRACE-05)
 * and records {@code http.server.request.duration} (METRIC-03 / Phase 4).
 *
 * Spring Boot 3.4 auto-discovers @Bean Filter instances and wraps them
 * in a default FilterRegistrationBean(/*) — no explicit URL-pattern
 * config needed here. The /actuator/* exclusion lives in the filter
 * itself via shouldNotFilter() (D-06).
 *
 * Producer-only (D-07): consumer-service's only HTTP surface is
 * /actuator/health, which would be excluded anyway, so the consumer
 * does not register this filter.
 */
```

---

### WR-03: "Two Changes Only" Comment Undercounts After Phase 4 Adds Meter @Bean

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:39-42`
**Also:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:39-42`
**Issue:** Both files' class-level JavaDoc says the two files differ in "two changes only: the service.name string and the tracer scope name." Phase 4 added a `meter()` @Bean to both files, and the Meter's instrumentation scope string also differs (`"com.example.producer"` vs `"com.example.consumer"`). The comment now undercounts: there are three differing values (service.name, tracer scope name, meter scope name). Since this is workshop textbook material, attendees explicitly counting differences will find three, not two.
**Fix:** Update both files:
```java
 * consumer-service/.../config/OtelSdkConfiguration.java with three changes
 * only: the service.name string ("order-producer" -> "order-consumer"),
 * the tracer scope name ("com.example.producer" -> "com.example.consumer"),
 * and the meter scope name (same value pattern as the tracer scope).
```

---

### WR-04: README Intro Claims main Branch Is at step-01-baseline

**File:** `README.md:5`
**Issue:** The introduction states: "The current `main` branch as of `step-01-baseline` shows the **uninstrumented baseline** — both Spring Boot apps run end-to-end with `POST /orders` flowing through RabbitMQ, but with **zero OpenTelemetry libraries on the classpath**." After Phase 4, `main` is at `step-04-metrics` (the workshop checkpoints section on line 73 correctly marks it "Current."). A new workshop attendee reads the intro first, gets the wrong picture of what's on `main`, then finds `OtelSdkConfiguration.java` and is confused.
**Fix:**
```markdown
The workshop progresses through six annotated git tags: `step-01-baseline` → `step-02-traces` → `step-03-context-propagation` → `step-04-metrics` → `step-05-logs` → `step-06-tests`. You can `git checkout` any tag to time-travel through the workshop. The current `main` branch is at **`step-04-metrics`** — both services instrument traces, context propagation, and metrics. See the [Workshop checkpoints](#workshop-checkpoints) section below for the current state of each tag.
```

---

### WR-05: README "Five Small Ways" Count Is Off by One After Phase 4

**File:** `README.md:140`
**Issue:** The "Why is OtelSdkConfiguration.java duplicated?" section says the two config files "differ in only five small ways (package, JavaDoc cross-reference, the service.name string, the tracer scope name, plus the producer-only HttpServerSpanFilter bean)." Phase 4 added a `meter()` @Bean to both files. The meter scope name also differs, making it six differences. "Five" is now factually wrong in the workshop's explanatory prose, which is supposed to tell attendees exactly what to look for when comparing the two files side by side.
**Fix:**
```markdown
The two files differ in only six small ways (package, JavaDoc cross-reference, the service.name string, the tracer scope name, the meter scope name — both scope strings use the same value pattern — plus the producer-only `HttpServerSpanFilter` bean) ...
```

---

### WR-06: README "First Run" Section Says demo:order Posts "a sample order" (Singular)

**File:** `README.md:61`
**Issue:** The "First run" code block comments `mise run demo:order # POSTs a sample order; expect 202`. After the Phase 4 update, `demo:order` sends two `curl` requests (WIDGET-1 with `priority=express` and WIDGET-2 with `priority=standard`) and produces two `202` responses. The inline comment is wrong about the count. Attendees following the "First run" walkthrough will see two lines of output and wonder if something went wrong.
**Fix:**
```sh
mise run demo:order # POSTs two sample orders (express + standard); expect 202 each
```

---

## Info

### IN-01: Histogram Error-Path Status Code Comment Overstates Reliability

**File:** `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java:207`
**Issue:** The comment in the `finally` block explains the failure path: "(b) failure path: status_code may not have been set on the span — but response.getStatus() still returns whatever the servlet container has decided (typically 500 by default for unhandled exceptions)." This is accurate for the common case where Spring MVC's `DispatcherServlet` writes a 500 before propagating the exception back to the filter, but it is not always true. If an `IOException` or `ServletException` propagates out of the `FilterChain` without reaching the `DispatcherServlet`'s exception-mapping logic (e.g., a downstream filter throws before dispatch), `response.getStatus()` returns the servlet-spec default of 200 (or 0 in some Tomcat versions), not 500. The histogram would then record `http.response.status_code=200` for a request that actually failed. This is a known limitation of filter-based HTTP metrics and is acceptable for a workshop demo, but the comment should not present the 500 outcome as reliable.

This does not affect the trace path (the catch block correctly calls `span.setStatus(StatusCode.ERROR)`) or the success path.
**Fix:** Soften the comment:
```java
// (b) failure path: status_code may not have been set on the span —
//     response.getStatus() returns whatever status the servlet container
//     has committed by this point. Spring MVC's exception handling
//     typically writes 500 before the exception propagates back to this
//     filter, but this is not guaranteed for all failure shapes (e.g. a
//     downstream filter throwing before DispatcherServlet handles it).
//     In practice, workshop-scale traffic only exercises the Spring MVC
//     path, so the histogram will show the correct 5xx status.
```

---

_Reviewed: 2026-05-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
