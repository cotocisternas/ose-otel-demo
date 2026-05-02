# Phase 4: Metrics - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning (no `/gsd-research-phase` needed — SUMMARY.md flags Phase 4 as paste-able from STACK.md)

<domain>
## Phase Boundary

Phase 4 adds the **second** OTel signal — metrics — to both services by extending each service's existing `OtelSdkConfiguration.java` with a `SdkMeterProvider` (10-second `PeriodicMetricReader` + `OtlpGrpcMetricExporter` to `:4317`) and wiring the three required instrument shapes so a `POST /orders` produces metric data in Mimir alongside the traces from Phases 2/3:

- **`orders.created`** — `LongCounter` (METRIC-02), producer-side, called from `OrderService.place(...)` after the publish returns
- **`http.server.request.duration`** — `DoubleHistogram` in **seconds** (METRIC-03), producer-side, recorded from inside the existing `HttpServerSpanFilter`
- **`orders.queue.depth.estimate`** — `ObservableGauge` (METRIC-04), consumer-side, fed by a `ThreadLocalRandom` synthetic value

The pedagogical goal is identical in shape to Phase 2's traces lesson: attendees read the SDK lines that built the meter pipeline (per-service-duplicated, like the tracer pipeline) and the lines that called `.add(1, attrs)` / `.record(seconds, attrs)` / `.buildWithCallback(...)` — and they see the result in Mimir within 15 seconds of issuing a request. METRIC-01..04 are paste-able from STACK.md; `/gsd-research-phase` is **not** needed.

**In scope (Phase 4 delivers):**
- Refactor `OtelSdkConfiguration.java` in **both** services so the existing `openTelemetry()` @Bean delegates to private helper methods (`buildTracerProvider(Resource)` extracted from current inline code; `buildMeterProvider(Resource)` added). The @Bean orchestrates: build Resource, call helpers, register on `OpenTelemetrySdk.builder()`. Phase 5's `buildLoggerProvider(Resource)` will land as a sibling helper.
- Both services register `SdkMeterProvider` with `PeriodicMetricReader` set to a **10-second interval** (METRIC-01 — overrides OTel's 60-second default) plus `OtlpGrpcMetricExporter` targeting the same OTLP endpoint via `System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")` with the `http://localhost:4317` fallback (Phase 2 D-12 carryforward).
- `OpenTelemetrySdk.builder().setMeterProvider(...)` line added; `@Bean(destroyMethod = "close")` already cascades shutdown to the meter provider via `OpenTelemetrySdk.close()` (Phase 2 D-15 — no new lifecycle work).
- Producer-service: a `Meter` @Bean (scope `com.example.producer`) parallel to the existing `Tracer` @Bean.
- Producer-service: `OrderService.place(...)` increments `LongCounter orders.created` after `publisher.publish(...)` returns, **inside the existing INTERNAL span body**, with one business attribute: `order.priority` read from `payload.get("priority")` with fallback `"standard"`. The `recordException` catch block (Phase 2 D-03) wraps the publish only — counter does not fire on the failure path. mise/README demo curl is updated to send both `{"priority":"express"}` and `{"priority":"standard"}` payloads so attendees see two series in Mimir.
- Producer-service: `HttpServerSpanFilter` is **extended** (not replaced, not paralleled by a second filter) — adds a `DoubleHistogram http.server.request.duration` field and records `Duration.between(start,end).toNanos() / 1e9` (seconds, semconv-aligned) in the existing `finally` block, with attributes `http.request.method` + `http.response.status_code`. The filter's name and class JavaDoc are updated to reflect "SERVER span + HTTP duration histogram" responsibilities; D-06 `/actuator/*` exclusion behavior is preserved.
- Consumer-service: a `Meter` @Bean (scope `com.example.consumer`) parallel to the existing `Tracer` @Bean.
- Consumer-service: ObservableGauge `orders.queue.depth.estimate` registered at startup. Callback returns `ThreadLocalRandom.current().nextInt(0, 50)`. Hosting class is at planner's discretion (small new `@Component` such as `QueueDepthGauge`, OR inline registration inside `OtelSdkConfiguration` via a `@PostConstruct` that registers the callback on the Meter bean). Phase 4 plan must pick one.
- README delta: a brief Phase-4-specific section walking the meter pipeline + the three instrument shapes (Counter / Histogram / ObservableGauge), keyed to the annotated tag `step-04-metrics`. The full step-by-step README walkthrough body (DOC-01) lands in Phase 7.
- Annotated git tag `step-04-metrics` on `main` (WORK-01) — same user-approved gate convention as Phases 2 and 3.

**Out of scope (deferred to later phases):**
- Logs signal + MDC `trace_id`/`span_id` correlation — Phase 5
- Testcontainers integration tests asserting metric exports — Phase 6 (would use `InMemoryMetricReader`)
- Pre-built Grafana dashboard panels for the three instruments + screenshots — Phase 7 (DOC-04 / WORK-02)
- Additional consumer-side business metrics (e.g., `orders.processed.total` / `orders.failed.total` counters mirroring producer's `orders.created`) — explicitly NOT in METRIC-01..04; if useful for the workshop, a Phase 7 polish opportunity
- Custom OTel `View` / `Aggregation` configuration on the meter provider (e.g., explicit histogram buckets) — defaults are sufficient for the workshop; tuning is a real-world concern outside the SDK lesson
- Any consumer-side HTTP histogram (consumer's only HTTP surface is `/actuator/health`, excluded by Phase 2 D-07 — so it has no SERVER spans and no histogram either)
- Replacing `BatchSpanProcessor` defaults (Phase 2 D-12 carryforward)

</domain>

<decisions>
## Implementation Decisions

### Meter pipeline wiring (both services)

- **D-01:** **Refactor existing `openTelemetry()` @Bean to delegate to private helper methods.** Extract Phase 2's inline tracer-provider construction into `private SdkTracerProvider buildTracerProvider(Resource resource)` and add a sibling `private SdkMeterProvider buildMeterProvider(Resource resource)`. The `@Bean openTelemetry()` body becomes a small orchestrator: build `Resource`, call both helpers, register on `OpenTelemetrySdk.builder().setTracerProvider(...).setMeterProvider(...).setPropagators(...).build()`. Resource is passed explicitly so the dependency is visible at the call site. Each helper carries its own comment-dense block. Phase 5's `private SdkLoggerProvider buildLoggerProvider(Resource)` lands as another sibling helper. **Pedagogical justification:** the diff for Phase 4 reads as "we added a sibling pipeline next to the trace pipeline" rather than "we shoved more stuff inside one method"; by Phase 5 the @Bean stays under ~30 lines with three readable helpers underneath instead of a 90+ line monolith.

- **D-02:** **Per-service duplication preserved (Phase 2 D-01 carryforward).** Both `producer-service/.../OtelSdkConfiguration.java` and `consumer-service/.../OtelSdkConfiguration.java` get the same refactor + `SdkMeterProvider` addition with the **same two changes** as today: `service.name` ("order-producer" / "order-consumer") and Meter scope name ("com.example.producer" / "com.example.consumer"). No extraction to a shared `otel-bootstrap` autoconfiguration — workshop attendees read the meter pipeline twice, same as the tracer pipeline. DOC-05's "Why duplicated?" callout already covers this; no README delta needed for the duplication rationale.

- **D-03:** **`PeriodicMetricReader` interval = 10 seconds (METRIC-01).** Overrides OTel's 60-second default via `PeriodicMetricReader.builder(metricExporter).setInterval(Duration.ofSeconds(10)).build()`. The 10-second value is locked by REQUIREMENTS METRIC-01 and is necessary so attendees see fresh metrics within ~15 seconds of issuing a `POST /orders` (ROADMAP SC #1) — a 60-second wait would break the workshop's tight feedback loop. Inline comment in the helper must explicitly document the override + the workshop-vs-production tradeoff (parallel to Phase 2 D-13's sampler comment).

- **D-04:** **OTLP endpoint pattern reused unchanged (Phase 2 D-12 carryforward).** `OtlpGrpcMetricExporter.builder().setEndpoint(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT") with fallback "http://localhost:4317")` — same `Optional.ofNullable(...).orElse(...)` shape and the same `DEFAULT_OTLP_ENDPOINT` constant already declared in Phase 2's file. **No new env var, no autoconfigure dependency.**

- **D-05:** **Resource is built once and shared.** Resource construction stays in the @Bean orchestrator (already there) and is passed into both `buildTracerProvider(resource)` and `buildMeterProvider(resource)`. Both providers carry the **identical** `service.name` / `service.namespace` / `service.instance.id` / `deployment.environment.name` attributes — so traces and metrics in Tempo/Mimir share the exact same resource identity (necessary for cross-signal correlation in Grafana).

- **D-06:** **`Meter` @Bean parallel to existing `Tracer` @Bean.** Each service declares `@Bean Meter meter(OpenTelemetry o) { return o.getMeter("com.example.producer"); }` (or `.consumer`) as a sibling to the existing Tracer bean — same scope name, same injection pattern. Constructor-injected into the call sites that need it.

- **D-07:** **`SdkMeterProvider` lifecycle is bound to the existing `OpenTelemetrySdk.close()` cascade (Phase 2 D-15 carryforward).** No new `@Bean(destroyMethod=...)` needed — the `OpenTelemetry` bean's `destroyMethod="close"` already cascades to `SdkMeterProvider.shutdown().join(10s)`, which forces a final flush of the metric export queue. **Verify** at smoke-test that Ctrl-C produces a final metric scrape (parallel to Phase 2 SC #2's "last batch flushed" verification).

### Counter: `orders.created` (METRIC-02, producer-side)

- **D-08:** **Call-site is `OrderService.place(...)` after `publisher.publish(orderId, payload)` returns, inside the Phase 2 INTERNAL span body.** Increment lives between the `publisher.publish(...)` line and the `return orderId` statement — same `try { ... } catch (RuntimeException e) { recordException; setStatus(ERROR); throw } finally { span.end() }` block. **Successful order = both business logic and AMQP send completed without throwing.** The `recordException` catch (Phase 2 D-03) does NOT increment the counter — failures are visible via the trace's error status, not as a metric (METRIC-02 is `orders.created`, not `orders.attempted`). Pedagogical parallel: trace + metric emitted from adjacent lines, attendees read both signals being produced in one spot.

- **D-09:** **Business attribute: `order.priority` read from `payload.get("priority")` with fallback `"standard"`.** Code shape:
  ```java
  String priority = String.valueOf(
      Optional.ofNullable(payload.get("priority")).orElse("standard"));
  ordersCreated.add(1, Attributes.of(AttributeKey.stringKey("order.priority"), priority));
  ```
  No new domain field, no classifier helper, no payload schema change beyond what callers already provide. Attribute key uses `AttributeKey.stringKey("order.priority")` (string literal — `order.priority` is **not** in the OTel semconv catalog as a stable attribute, so no semconv constant is appropriate; this contrasts with the histogram's `http.request.method` which IS semconv).

- **D-10:** **Demo payloads must exercise both attribute values to teach the cardinality-awareness lesson.** Phase 4 plan updates the existing `mise run demo:order` task (and the README curl snippets) to send TWO sample payloads — `{"priority":"express"}` and `{"priority":"standard"}` (or omit the field to exercise the fallback). Without two values, Mimir shows ONE series and the "attributes give you breakdowns" lesson is invisible.

- **D-11:** **Counter scope: producer-only.** Consumer-service does NOT host an `orders.processed` / `orders.failed` mirror counter in v1 — METRIC-01..04 are exhausted by Counter (producer) + Histogram (producer) + ObservableGauge (consumer). Adding a second consumer counter would be scope creep; deferred to Phase 7 if attendee feedback flags it.

### Histogram: `http.server.request.duration` (METRIC-03, producer-side)

- **D-12:** **Histogram is recorded from inside the existing `HttpServerSpanFilter`, NOT a new dedicated filter.** Phase 2's `HttpServerSpanFilter` already has `try { chain.doFilter(...) } finally { span.end() }` bracketing the request — adding `histogram.record(seconds, attrs)` inside the same `finally` reuses that timing surface. No new `OncePerRequestFilter`, no new filter chain ordering concern, no second `shouldNotFilter()` to keep aligned with the SERVER span's `/actuator/*` exclusion. **The filter's responsibility expands from "SERVER span" to "SERVER span + HTTP duration histogram"** — JavaDoc on the class is updated to reflect this; the change is documented inline. Producer-only (consumer's only HTTP surface is `/actuator/health` which D-07 already excluded — there's no consumer-side HTTP filter and no consumer histogram).

- **D-13:** **Unit is seconds (semconv-aligned).** OTel HTTP semconv 1.40.0 specifies `http.server.request.duration` in **seconds**, not milliseconds. Recording shape:
  ```java
  long startNanos = System.nanoTime();
  try {
      chain.doFilter(request, response);
  } finally {
      double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
      requestDuration.record(seconds, attrs);
      span.end();  // existing Phase 2 line
  }
  ```
  Inline comment must explicitly call out the seconds-not-millis trap — it's a textbook OTel pitfall and worth one comment line.

- **D-14:** **Histogram attributes follow HTTP semconv:** `http.request.method` (e.g., `"POST"`) and `http.response.status_code` (e.g., `200`) — both already available inside the filter (request method from `HttpServletRequest`, status code from `HttpServletResponse.getStatus()` AFTER `chain.doFilter` returns). Use the stable `io.opentelemetry.semconv.HttpAttributes` constants (NOT string literals — D-04 carryforward). `url.path` is already on the SERVER span but is NOT recorded on the histogram (high-cardinality path values would explode metric series).

- **D-15:** **Bucket configuration: SDK defaults.** No custom `View` / `ExplicitBucketHistogramAggregation` on the `SdkMeterProvider`. The default explicit-bucket aggregation (the OTel-spec'd default buckets in seconds for HTTP histograms) produces sensible workshop values. Bucket tuning is a real-world concern outside the SDK lesson — comment explicitly says so, parallel to Phase 2 D-12's "BatchSpanProcessor defaults".

### Observable gauge: `orders.queue.depth.estimate` (METRIC-04, consumer-side)

- **D-16:** **Gauge is registered consumer-side.** Semantically a "queue depth" measurement belongs to the consumer that drains the queue, not the producer that publishes to an exchange. This also gives the consumer's `SdkMeterProvider` a real business-level instrument to emit (the consumer has no Counter and no Histogram) — symmetric pedagogical surface across the two services.

- **D-17:** **Callback returns a synthetic `ThreadLocalRandom.current().nextInt(0, 50)` value.** REQUIREMENTS METRIC-04 says "synthetic queue-depth value" — so this is NOT a real measurement. The pedagogical point of `ObservableGauge` is the **callback-on-collection-interval** mechanic, not the value semantics. `ThreadLocalRandom` is thread-safe (callback fires on the meter reader thread), introduces no shared state, no race conditions, and no coupling to Phase 3's `AtomicInteger` (APP-04). Inline comment must call out: (a) the callback is invoked once per `PeriodicMetricReader` interval (10s per D-03), (b) the callback should be cheap and side-effect-free, (c) "real" queue depth would require RabbitMQ Management API polling — out of scope.

- **D-18:** **Hosting structure is at planner's discretion** between two acceptable shapes:
  - **(a) Inline registration** in `OtelSdkConfiguration.java` via a `@PostConstruct` method that calls `meter.gaugeBuilder("orders.queue.depth.estimate").ofLongs().buildWithCallback(measurement -> measurement.record(ThreadLocalRandom.current().nextInt(0, 50)))`. Single file, single touch point, mirrors the Phase 5 `OpenTelemetryAppender.install(...)` `@PostConstruct` pattern foreshadowed in Phase 2 D-15 commentary.
  - **(b) New `@Component QueueDepthGauge`** that constructor-injects `Meter` and registers the callback in its constructor or `@PostConstruct`. One small new file; cleaner separation between SDK config and instrument registration.
  Plan must pick one; neither violates Phase 2's "no helper at instrumentation call sites" rule (this is registration boilerplate, not a span/counter call site).

- **D-19:** **Instrument flavor is `ofLongs()`, not the default double.** The synthetic value is `int`, the gauge name carries `.estimate` (whole-number connotation), and integer queue depths are the conventional shape for messaging gauges — `LongCounterCallback` / `ObservableLongMeasurement` keeps the type honest.

### Documentation & comment density (carryforward)

- **D-20:** **DOC-03 comment density bar applies to Phase 4's additions.** Phase 2 set the bar at ≥40 comment lines per `OtelSdkConfiguration.java` (verified via `mise verify:bom` grep gate or equivalent). Phase 4's added meter pipeline lines (helper method body + Meter @Bean + interval-override comment + endpoint env-var comment) must keep that bar — running grep over each refactored file should still return ≥40 (and likely will reach 60+ given the meter helper is its own commented block). Plan must include a verification step.

- **D-21:** **README delta is small and Phase-4-specific.** Add a "Step 4: Metrics" section keyed to tag `step-04-metrics` that:
  - Names the three instrument shapes (Counter / Histogram / ObservableGauge) and points readers at the lines in `OtelSdkConfiguration.java` where the meter pipeline is built.
  - Calls out the seconds-not-millis trap (D-13) and the bounded-cardinality lesson (D-10).
  - Shows the Mimir query for `orders_created_total{order_priority="express"}` (and the OTel-to-Prometheus dot-to-underscore name mapping).
  Full README walkthrough body (DOC-01) and Grafana screenshots (DOC-04) land in Phase 7.

- **D-22:** **Annotated git tag `step-04-metrics` is the exit gate (WORK-01 carryforward).** Same human-checkpoint pattern as Phases 1/2/3: source merged + all 4 ROADMAP success criteria verified live, then user gate approves the orchestrator-applied tag. STATE.md / ROADMAP.md `[x]` flips land atomically with the tag-apply commit.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap

- `.planning/REQUIREMENTS.md` — METRIC-01..04 (locked: 10s reader interval, three instrument names, semconv-aligned histogram unit, `order.priority` business attribute) + WORK-01 (annotated tag exit gate)
- `.planning/ROADMAP.md` — Phase 4 details, all 4 success criteria, "Plans: TBD" (this discussion seeds plan creation)
- `.planning/PROJECT.md` — overall constraint set (Spring Boot 3.4.13, Java 17, no Java agent, no Micrometer bridge, no autoconfigure starter, single OTLP endpoint, otel-lgtm backend)
- `.planning/SUMMARY.md` — research-flag status: Phase 4 is paste-able from STACK.md; **no `/gsd-research-phase` needed**

### Carryforward decisions from prior phases

- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` — Phase 2 decisions D-01 (per-service duplication / "boilerplate IS the lesson"), D-12 (no-autoconfigure / `System.getenv` with fallback), D-13 (sampler choice + comment style), D-15 (`@Bean(destroyMethod="close")` lifecycle cascade), D-16 (propagators wired but unexercised in Phase 2)
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/PHASE-SUMMARY.md` — what Phase 2 actually shipped (the SDK shape, the inline span template, comment density baseline)
- `.planning/phases/03-amqp-context-propagation/03-CONTEXT.md` — Phase 3 D-03 catch-shape (already wraps publish in `OrderService.place`; Counter call-site coexists with this), Phase 3 deletion of inline PRODUCER/CONSUMER spans (Phase 4 must not reintroduce them via metrics-related refactors)

### Files Phase 4 modifies (read first to plan diffs)

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — refactor target for D-01 helper extraction; add `buildMeterProvider`, Meter @Bean
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — same refactor in mirror form (per D-02)
- `producer-service/src/main/java/com/example/producer/api/HttpServerSpanFilter.java` — extend for Histogram (D-12..D-15)
- `producer-service/src/main/java/com/example/producer/domain/OrderService.java` — add Counter call-site (D-08..D-09)
- `consumer-service/src/main/java/com/example/consumer/...` — new `QueueDepthGauge` @Component **OR** inline `@PostConstruct` in `OtelSdkConfiguration` (D-18 — planner picks one)
- `mise.toml` — update `demo:order` task(s) to send two `priority` values (D-10)
- `README.md` — add "Step 4: Metrics" section (D-21)
- `producer-service/pom.xml` and `consumer-service/pom.xml` — verify `opentelemetry-exporter-otlp` already present from Phase 2 (single artifact covers traces + metrics + logs); no new BOM-managed deps expected. Plan must `mvn dependency:tree` and confirm.

### OTel API surface (paste-able from STACK.md per SUMMARY.md)

- OTel SDK 1.61.0 BOM-managed: `io.opentelemetry:opentelemetry-sdk-metrics`, `io.opentelemetry.exporter:opentelemetry-exporter-otlp` (single artifact for all three signals — already on classpath from Phase 2)
- semconv 1.40.0 stable: `io.opentelemetry.semconv.HttpAttributes` (HTTP_REQUEST_METHOD, HTTP_RESPONSE_STATUS_CODE) — locked attribute keys for the histogram (D-14)
- semconv 1.40.0-incubating: not required for Phase 4 (no messaging.* attributes added by metrics layer; Phase 2/3 already cover the messaging semconv on PRODUCER/CONSUMER spans)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`HttpServerSpanFilter` (producer-service)** — Phase 2's SERVER-span filter wraps every non-/actuator request in `try/finally`. Phase 4 extends it (D-12) by adding a `DoubleHistogram` field and one `record(...)` line in the existing finally. No new filter, no chain reordering. The `shouldNotFilter("/actuator/*")` exclusion (D-06) already guarantees the histogram won't pollute metrics with health-check noise.
- **`OrderService.place(Map<String,Object>)` (producer-service)** — Phase 2's INTERNAL span body has the `try { ... publish ... } catch (RuntimeException) { recordException; throw } finally { span.end() }` shape. Phase 4 adds the `LongCounter.add(1, attrs)` line between `publisher.publish` and `return orderId` (D-08). The catch is unchanged — failures are NOT counted in `orders.created` (D-08 rationale).
- **`OtelSdkConfiguration.openTelemetry()` @Bean (both services)** — the existing Resource construction + propagators + `OpenTelemetrySdk.builder()` orchestration becomes the orchestrator after the D-01 refactor. `Resource resource = ...` stays in the @Bean body and is passed to both helpers.
- **`Tracer` @Bean pattern** — `@Bean Tracer tracer(OpenTelemetry o) { return o.getTracer("com.example.<service>"); }` is mirrored verbatim for the new `Meter` @Bean (D-06).
- **`DEFAULT_OTLP_ENDPOINT` constant** — Phase 2's `private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317"` is reused unchanged for the `OtlpGrpcMetricExporter`.
- **`opentelemetry-exporter-otlp` artifact** — already on classpath from Phase 2; the same artifact provides `OtlpGrpcMetricExporter` + `OtlpGrpcLogRecordExporter`. Phase 4 should add **zero** new BOM-managed dependencies (verify via `mvn dependency:tree`).

### Established Patterns

- **Per-service duplication, never extracted** — `OtelSdkConfiguration.java` lives twice with two-line diffs (Phase 2 D-01 / DOC-05). Phase 4's refactor + meter additions duplicate symmetrically.
- **No autoconfigure, no magic** — Phase 2 D-12: `System.getenv` with `Optional.ofNullable(...).orElse(...)` fallback; the same shape is reused for the metric exporter endpoint (D-04).
- **Phase 2 inline-span template** stays untouched — Phase 4 only ADDS a counter line inside the existing `OrderService.place` span body (D-08); does NOT introduce any new span helper or modify the catch shape.
- **`@Bean(destroyMethod="close")` cascade** — Phase 2 D-15: closing `OpenTelemetrySdk` shuts down all child providers. Adding `SdkMeterProvider` requires no new lifecycle bean (D-07).
- **Heavy comment density** — Phase 2 DOC-03: ≥40 comment lines per `OtelSdkConfiguration.java`. Phase 4's helper extraction + meter additions naturally land more comments; the bar holds (D-20).
- **Annotated tag at exit** — Phase 1/2/3 pattern: source ships, all SC verified live, then human-gate tag-apply with atomic STATE/ROADMAP flip (D-22 / WORK-01).

### Integration Points

- **`OpenTelemetrySdk.builder().setMeterProvider(...)`** — single new builder line in the @Bean orchestrator after D-01's helper refactor.
- **`Meter` @Bean → `OrderService` constructor** — adds one constructor parameter to `OrderService` (currently takes `OrderPublisher` + `Tracer`); same DI pattern as the Tracer.
- **`Meter` @Bean → `HttpServerSpanFilter` constructor** — adds one parameter (currently takes `Tracer`); the filter's `@Bean` factory in `OtelSdkConfiguration` adds a `Meter` argument.
- **`Meter` @Bean → consumer-side gauge host** — either inline `@PostConstruct` in `OtelSdkConfiguration` or a new `@Component QueueDepthGauge` (D-18).
- **mise tasks (`demo:order`)** — needs an updated/extra invocation that sends `{"priority":"express"}` so Mimir series breakdown is teachable (D-10).
- **README** — small Phase-4 section (D-21); full walkthrough is Phase 7.

</code_context>

<specifics>
## Specific Ideas

- The `+1` git diff between `step-03-context-propagation` and `step-04-metrics` should read as "we added a sibling pipeline" — D-01 helper-extraction is chosen partly because the refactor itself is a teaching artifact (compare the @Bean before vs after and you see the SDK building three signals symmetrically by Phase 5).
- Demo curl payloads must include both `{"priority":"express"}` and `{"priority":"standard"}` — the cardinality-awareness lesson is invisible without two series in Mimir (D-10).
- The "seconds, not milliseconds" trap (D-13) gets one explicit comment line in `HttpServerSpanFilter` — it's the most common mistake when porting custom histograms to OTel HTTP semconv.

</specifics>

<deferred>
## Deferred Ideas

- **Consumer-side `orders.processed.total` / `orders.failed.total` counters** — would mirror producer's `orders.created` and give the consumer's MeterProvider a Counter alongside its Gauge. Out of v1 scope (METRIC-01..04 are exhausted by current decisions); revisit in Phase 7 if attendee feedback flags asymmetry.
- **Custom histogram bucket tuning via `View` / `ExplicitBucketHistogramAggregation`** — real-world concern, not a workshop SDK lesson; defaults are sufficient. Could become a Phase 7 polish callout ("when you're ready to tune for production…").
- **Real RabbitMQ queue depth via Management API** — METRIC-04 is "synthetic" by spec; a real implementation would poll `:15672/api/queues/...` and is a non-trivial integration. Out of scope; potential Phase 7 polish topic.
- **Exemplars (linking metric data points to traces)** — OTel SDK 1.61.0 supports exemplars on histograms, which would make Mimir's "view trace from histogram bucket" interactive. Pedagogically powerful but adds a second config concern; deferred to a hypothetical v2.
- **Producer-side `messages.published.total` counter** — would parallel consumer's METRIC-04 conceptually but isn't in METRIC-01..04. Same rationale as the consumer counter deferral above.

</deferred>

---

*Phase: 4-metrics*
*Context gathered: 2026-05-01*
