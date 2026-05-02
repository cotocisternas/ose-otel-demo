---
phase: 04-metrics
verified: 2026-05-01T23:30:00Z
status: human_needed
score: 4/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Start the full stack (mise run infra:up && mise run dev) and verify all 4 ROADMAP success criteria simultaneously green in Grafana/Mimir"
    expected: |
      SC1: Run `mise run demo:order`, wait 12s, query Mimir `orders_created_total` — expect two series with labels order_priority="express" and order_priority="standard", service_name="order-producer"
      SC2: Run ~30s of traffic, query `http_server_request_duration_seconds_count`, `_sum`, `_bucket` — expect non-zero values; _bucket carries http_request_method + http_response_status_code labels; _sum values in 0.05–0.5 range (seconds, NOT 50-500 millis)
      SC3: Query `orders_queue_depth_estimate` twice 12s apart — expect timestamp delta 8–15s (proves 10s PeriodicMetricReader interval override, not 60s default); values in [0, 50); service_name="order-consumer"
    why_human: "ROADMAP success criteria 1-3 require a running live stack with infrastructure containers. The 04-05 plan executor documented T3 live verification as PENDING because the stack was not running at execution time. The git tag step-04-metrics exists but was applied without confirming the live stack criteria. SC4 (tag exists) is verified programmatically."
---

# Phase 4: Metrics Verification Report

**Phase Goal:** Add all three OTel metric instrument shapes — Counter, Histogram, ObservableGauge — to both services using the OTel Java SDK directly. A SdkMeterProvider sibling pipeline is wired at startup (10s PeriodicMetricReader interval). Three named instruments emit to Mimir via OTLP: orders.created (LongCounter, producer), http.server.request.duration (DoubleHistogram, seconds, producer), orders.queue.depth.estimate (ObservableLongGauge, consumer).
**Verified:** 2026-05-01T23:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SdkMeterProvider pipeline wired in both services with 10s PeriodicMetricReader + OtlpGrpcMetricExporter (METRIC-01) | VERIFIED | Both OtelSdkConfiguration.java files have `buildMeterProvider(Resource)` helper, `Duration.ofSeconds(10)`, `OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build()`, `.setMeterProvider(meterProvider)` in the SDK builder. Comment density: producer 231, consumer 221 (target >= 40). |
| 2 | orders.created LongCounter fires after successful publish, inside INTERNAL span, with order.priority attribute (METRIC-02) | VERIFIED | `OrderService.java` has `ordersCreated.add(1, ...)` at line 122, after `publisher.publish` at line 91, before `return orderId` at line 125 — all inside `try (Scope scope = span.makeCurrent())`. Counter built once in constructor. Catch block unchanged. |
| 3 | http.server.request.duration DoubleHistogram records in finally block before span.end, unit "s", semconv constants (METRIC-03) | VERIFIED | `HttpServerSpanFilter.java`: `requestDuration.record(seconds, ...)` at line 212 in finally block, `span.end()` at line 216. `startNanos = System.nanoTime()` at line 147 before `tracer.spanBuilder` at line 157. Unit `"s"`, conversion `/ 1_000_000_000.0`, attributes use `HttpAttributes.HTTP_REQUEST_METHOD` + `HttpAttributes.HTTP_RESPONSE_STATUS_CODE`. url.path not in histogram attributes. |
| 4 | orders.queue.depth.estimate ObservableLongGauge with ThreadLocalRandom synthetic callback, @PreDestroy cleanup (METRIC-04) | VERIFIED | `QueueDepthGauge.java` exists at `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`. Gauge name correct. `.ofLongs()` present. `buildWithCallback(measurement -> measurement.record(ThreadLocalRandom.current().nextInt(0, 50)))`. `@PreDestroy gauge.close()` present. D-18b pattern: OtelSdkConfiguration.java NOT modified. |
| 5 | All 4 ROADMAP success criteria simultaneously green at live stack + annotated tag step-04-metrics created (WORK-01 portion) | UNCERTAIN | SC4 (git tag) VERIFIED: `git cat-file -t step-04-metrics` = `tag` (annotated, not lightweight). SC1–SC3 require live stack; 04-05-SUMMARY documents T3 as PENDING because stack was not running at execution time. Tag was applied without live confirmation. Needs human verification. |

**Score:** 4/5 truths verified (SC1–SC3 require human)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|---------|--------|---------|
| `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | Refactored with buildTracerProvider + buildMeterProvider helpers, Meter @Bean | VERIFIED | All key patterns confirmed: `private SdkMeterProvider buildMeterProvider(Resource resource)`, `Duration.ofSeconds(10)`, `.setMeterProvider(meterProvider)`, `getMeter("com.example.producer")`, `HttpServerSpanFilter httpServerSpanFilter(Tracer tracer, Meter meter)`, single DEFAULT_OTLP_ENDPOINT, destroyMethod="close" |
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | Mirror refactor with Meter @Bean, no HttpServerSpanFilter | VERIFIED | Mirror confirmed: `buildMeterProvider(Resource resource)`, `getMeter("com.example.consumer")`, no HttpServerSpanFilter factory, "Why no HttpServerSpanFilter here?" JavaDoc preserved |
| `producer-service/src/main/java/com/example/producer/domain/OrderService.java` | LongCounter orders.created with order.priority attribute | VERIFIED | `ordersCreated.add(1, Attributes.of(AttributeKey.stringKey("order.priority"), priority))` present; `Optional.ofNullable(payload.get("priority")).orElse("standard")` pattern; counter built once in constructor |
| `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` | DoubleHistogram http.server.request.duration, seconds, semconv attrs | VERIFIED | Constructor `(Tracer tracer, Meter meter)`, `requestDuration.record(seconds, Attributes.of(HttpAttributes.HTTP_REQUEST_METHOD, ..., HttpAttributes.HTTP_RESPONSE_STATUS_CODE, ...))` in finally before `span.end()`, unit `"s"`, conversion `/ 1_000_000_000.0` |
| `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` | New @Component with ObservableLongGauge, ofLongs(), @PreDestroy | VERIFIED | File exists. Package `com.example.consumer.observability`. `@Component`. Constructor `(Meter meter)`. Gauge `orders.queue.depth.estimate`. `.ofLongs().buildWithCallback(...)`. `ThreadLocalRandom.current().nextInt(0, 50)`. `@PreDestroy gauge.close()`. |
| `mise.toml` | demo:order sends two priority payloads (express + standard) | VERIFIED | Two curl lines with `"priority":"express"` and `"priority":"standard"`. `set -e` discipline. `PRODUCER_PORT` preserved. TOML-valid structure. |
| `README.md` | Step 4 Metrics section, Current marker moved, What's NOT here yet updated | VERIFIED | `## Step 4: Metrics` at line 79. `**Current.**` on step-04-metrics. "No log correlation (Phase 5)" in What's NOT here yet. Section order: Workshop checkpoints < Step 4: Metrics < Reading the code < Why duplicated < Why shared < What's NOT here yet. |
| `refs/tags/step-04-metrics` | Annotated git tag (not lightweight) | VERIFIED | `git cat-file -t step-04-metrics` = `tag`. Tag message references SdkMeterProvider, three instrument shapes, METRIC-01..04, and claims all 4 success criteria green. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `openTelemetry()` @Bean orchestrator | `buildTracerProvider(resource)` + `buildMeterProvider(resource)` | Direct method calls on the Resource built in the @Bean body | VERIFIED | Both `buildTracerProvider(resource)` and `buildMeterProvider(resource)` called in both service files |
| `buildMeterProvider` | OtlpGrpcMetricExporter -> otel-lgtm :4317 | `OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build()` with env-var fallback | VERIFIED | Present in both services; reuses `DEFAULT_OTLP_ENDPOINT` constant and `System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")` pattern |
| `PeriodicMetricReader` | 10-second interval override | `.setInterval(Duration.ofSeconds(10))` | VERIFIED | Present in both services (count=2 per file, once in code once in comment) |
| `Meter @Bean` | `OpenTelemetry.getMeter(scope)` | `openTelemetry.getMeter("com.example.producer/consumer")` | VERIFIED | Producer scope `"com.example.producer"`, consumer scope `"com.example.consumer"` |
| `OrderService constructor` | Meter @Bean | Spring constructor injection | VERIFIED | `OrderService(OrderPublisher publisher, Tracer tracer, Meter meter)` |
| `ordersCreated.add(1, attrs)` | SdkMeterProvider via PeriodicMetricReader | OTel SDK instrument pipeline | VERIFIED | Counter built once in constructor; `add()` called inside try(Scope) block after publish, before return |
| `HttpServerSpanFilter constructor` | Meter @Bean | Spring constructor injection via `httpServerSpanFilter(Tracer tracer, Meter meter)` factory | VERIFIED | Factory in producer OtelSdkConfiguration.java passes meter; constructor accepts `(Tracer tracer, Meter meter)` |
| `requestDuration.record(seconds, attrs)` | SdkMeterProvider | In finally block before `span.end()` | VERIFIED | Line 212 (record) before line 216 (span.end) in finally block |
| `QueueDepthGauge constructor` | Meter @Bean | Spring constructor injection | VERIFIED | `@Component` auto-scanned; single `Meter` parameter; Meter @Bean exists in consumer OtelSdkConfiguration |
| `buildWithCallback(measurement -> ...)` | PeriodicMetricReader 10s interval | SDK invokes callback on collection cycle | VERIFIED at source level; UNCERTAIN at runtime | Callback registered correctly in source; live proof requires running stack (SC3) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `OrderService.java` | `ordersCreated` (LongCounter) | Built in constructor, incremented on success path after `publisher.publish(...)` | Yes — real business event triggers increment | FLOWING |
| `HttpServerSpanFilter.java` | `requestDuration` (DoubleHistogram) | `System.nanoTime()` delta in nanoseconds converted to seconds | Yes — real request timing | FLOWING |
| `QueueDepthGauge.java` | `gauge` (ObservableLongGauge) | `ThreadLocalRandom.current().nextInt(0, 50)` — synthetic by design (METRIC-04 spec) | Synthetic — intentional per requirements | FLOWING (synthetic) |

### Behavioral Spot-Checks

Live behavioral checks require a running stack and were not performed by the plan executor (T3 in 04-05-SUMMARY was PENDING due to stack not running). Deferred to human verification below.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| orders_created_total in Mimir within 15s | `mise run demo:order && sleep 12 && curl Mimir query` | Not run — stack not running at execution | SKIP (human needed) |
| http_server_request_duration_seconds with count/sum/bucket | Traffic loop + Mimir query | Not run | SKIP (human needed) |
| orders_queue_depth_estimate fresh every 10s | Two Mimir queries 12s apart | Not run | SKIP (human needed) |
| Both services compile cleanly | `mvn -pl producer-service,consumer-service compile` | SUMMARY 04-03 confirms BUILD SUCCESS for producer after Plan 04-03 landed. Consumer compile confirmed in 04-01-SUMMARY and 04-04-SUMMARY. | PASS (from SUMMARY evidence) |
| mise verify:bom green | `mise run verify:bom` | Confirmed in 04-01-SUMMARY: "Phase 2 baseline confirmed: one version per OpenTelemetry artifact" | PASS (from SUMMARY evidence) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| METRIC-01 | 04-01 | Each service registers SdkMeterProvider with PeriodicMetricReader set to 10-second interval + OtlpGrpcMetricExporter to :4317 | SATISFIED at source; runtime proof needs human | Both OtelSdkConfiguration.java files verified to have buildMeterProvider with 10s interval and OTLP exporter. Live flow to :4317 requires running stack. |
| METRIC-02 | 04-02 | LongCounter `orders.created` with business-attribute tags (order.priority) | SATISFIED at source | OrderService.java verified: counter built once, incremented on success path, order.priority attribute with fallback |
| METRIC-03 | 04-03 | DoubleHistogram `http.server.request.duration` (seconds, semconv-aligned) with http.request.method and http.response.status_code | SATISFIED at source | HttpServerSpanFilter.java verified: unit "s", seconds conversion, semconv constants, in finally before span.end |
| METRIC-04 | 04-04 | ObservableGauge `orders.queue.depth.estimate` synthetic value per collection cycle | SATISFIED at source | QueueDepthGauge.java verified: ofLongs(), buildWithCallback, ThreadLocalRandom, @PreDestroy |

Note: REQUIREMENTS.md traceability still shows METRIC-01..04 as `[ ]` (Pending) — the REQUIREMENTS.md `[x]` flip was a planned step in Plan 04-05 T4 (the human-verify + tag task). This is consistent with T4 being a blocking human checkpoint that has not yet been confirmed.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None detected | — | All instruments are built once in constructors (not per-request), no stub return values, no placeholder implementations, no hardcoded empty collections. The gauge's synthetic value is intentional per METRIC-04 spec and documented in JavaDoc. | — | — |

### Human Verification Required

#### 1. Live Stack ROADMAP Success Criteria (SC1–SC3)

**Test:** Start the full workshop stack and verify all four Phase 4 success criteria are simultaneously green:

```sh
# Start infrastructure and services
mise run infra:up
mise run dev    # parallel producer + consumer

# Confirm both healthy
curl -s http://localhost:8080/actuator/health | grep '"status":"UP"'
curl -s http://localhost:8081/actuator/health | grep '"status":"UP"'

# SC1: orders_created_total with both priority labels within 15s
mise run demo:order
sleep 12
# In Grafana -> Explore -> Mimir/Prometheus: query orders_created_total
# Expect: two series with order_priority="express" and order_priority="standard"
# and service_name="order-producer"

# SC2: HTTP histogram with count/sum/buckets after ~30s traffic
for i in $(seq 1 20); do
  curl -s -o /dev/null -X POST http://localhost:8080/orders \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"SKU-$i\",\"quantity\":1,\"priority\":\"express\"}"
  sleep 1
done
sleep 12
# Query: http_server_request_duration_seconds_count (expect non-zero)
# Query: http_server_request_duration_seconds_sum (expect 0.05-0.5 per request, NOT 50-500)
# Query: http_server_request_duration_seconds_bucket (expect http_request_method + http_response_status_code labels)

# SC3: Gauge fresh every 10s
# Query orders_queue_depth_estimate. Wait 12s. Re-query.
# Expect timestamp delta 8-15s; values in [0, 50); service_name="order-consumer"

# SC4: Clean tree + BOM
mise run verify:bom && test -z "$(git status --porcelain)" && echo "SC4 GREEN"
```

**Expected:** All four criteria green. SC2 is the key regression guard for the seconds-not-millis issue — if `_sum` values are in the 50–500 range (millis), D-13 was violated. If gauge delta is ~60s instead of ~10s, the PeriodicMetricReader interval was not applied.

**Why human:** ROADMAP success criteria 1–3 require a running live stack with Docker infrastructure (otel-lgtm, RabbitMQ) and active Spring Boot services. The plan executor's T3 task confirmed the stack was not running at execution time (connection refused to :8080). The annotated tag `step-04-metrics` was created without live confirmation of these criteria — the tag message claims they are green, but this was not verified at execution time.

### Gaps Summary

No code-level gaps identified. All four metric instruments are substantively implemented and correctly wired in the source code:

- METRIC-01: Both `OtelSdkConfiguration.java` files have the complete `buildMeterProvider` helper with 10-second `PeriodicMetricReader`, `OtlpGrpcMetricExporter`, and `.setMeterProvider(meterProvider)` wired into the SDK builder.
- METRIC-02: `OrderService.java` has the `LongCounter` built once in the constructor and incremented correctly on the success path with the `order.priority` attribute.
- METRIC-03: `HttpServerSpanFilter.java` has the `DoubleHistogram` built once in the constructor with unit `"s"`, correct seconds conversion, semconv attribute constants, and recorded in the finally block before `span.end()`.
- METRIC-04: `QueueDepthGauge.java` is a complete `@Component` with `ObservableLongGauge` using `.ofLongs()`, `buildWithCallback`, `ThreadLocalRandom` synthetic value, and `@PreDestroy` cleanup.

The only unresolved item is runtime confirmation that the three named instruments actually reach Mimir — which requires a running stack. The source-level implementation is complete and correct. The git tag `step-04-metrics` exists as an annotated tag and the plan executor stated the intent to verify the live criteria, but T3 (live verification) was deferred due to the stack not being running.

---

_Verified: 2026-05-01T23:30:00Z_
_Verifier: Claude (gsd-verifier)_
