# Phase 4: Metrics - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 4-metrics
**Areas discussed:** Counter call-site & business attribute, Histogram placement, Observable gauge source, Meter wiring shape

---

## Counter call-site (orders.created)

| Option | Description | Selected |
|--------|-------------|----------|
| OrderService.place() after publish | Increment after orderPublisher.publish(...) returns, inside the same INTERNAL span body (Phase 2 D-03 catch). Pedagogical parallel: trace + metric emitted from the same lines. 'Successful order' = business logic + AMQP send both completed without throwing. Matches the spot where the INTERNAL span calls .end() today. | ✓ |
| OrderController after place() returns | Increment after orderService.place(payload) returns, before the ResponseEntity.accepted() build. 'Successful order' = HTTP 202 about to be sent to caller. Couples the metric to the HTTP boundary; pulls Counter into the controller layer. | |
| OrderPublisher.publish() after convertAndSend | Increment inside OrderPublisher.publish(...) after rabbitTemplate.convertAndSend(...) returns. 'Successful order' = AMQP frame handed to the channel without throwing. Counter lives next to messaging concerns; reuses the file Phase 3 already touches for the publisher bean. | |

**User's choice:** OrderService.place() after publish (Recommended)
**Notes:** Aligns with Phase 2 D-03 catch shape; counter and INTERNAL span emit from adjacent lines so attendees read both signals being produced in one spot. The Phase 2 catch wraps publish — failures continue to set ERROR status on the INTERNAL span but do NOT increment the counter (`orders.created` is for successes, not attempts).

---

## Counter business attribute (order.priority source)

| Option | Description | Selected |
|--------|-------------|----------|
| payload.get("priority"), fallback "standard" | String value comes from the JSON payload; demo curl in mise tasks/README sends {"priority": "express"} and {"priority": "standard"} so attendees see two series in Mimir. Default to "standard" when key missing/null. One-line code addition; teaches the bounded-cardinality lesson by example. | ✓ |
| Derive from payload (e.g., total > 100 -> express) | Compute priority server-side from another field (introduces a 'total' or 'amount' field on the payload). More 'realistic' business logic, but adds domain that doesn't exist today and obscures the metric attribute lesson behind classifier code. | |
| Hard-code constant "standard" | Always tag with priority="standard". Cheapest to implement; teaches the Counter mechanics but produces only ONE series so the cardinality lesson is invisible. Workshop attendee sees the pipeline but not the value of attributes. | |

**User's choice:** payload.get("priority"), fallback "standard" (Recommended)
**Notes:** Demo curl/mise task must be updated to send both values so two series appear in Mimir; without that, the cardinality lesson is invisible. Attribute key is a string literal (`order.priority` is not in the OTel semconv catalog).

---

## Histogram placement (http.server.request.duration)

| Option | Description | Selected |
|--------|-------------|----------|
| Extend HttpServerSpanFilter | Add a DoubleHistogram field + one histogram.record(seconds, attrs) line in the existing finally block of HttpServerSpanFilter. Same start/end timing as the SERVER span. One file touched, zero new filters, attendees read 'span emission + histogram emission' as a single block. Filter docstring becomes 'SERVER span + HTTP duration histogram'. | ✓ |
| New dedicated HttpServerMetricsFilter | Create a second OncePerRequestFilter solely for the histogram. Cleaner separation of concerns (one filter = one signal), but two filters in the chain to coordinate, and Phase 2's 'one HTTP touch point' narrative becomes 'two HTTP touch points'. More files, more boilerplate, more bean wiring. | |
| Spring Boot's built-in HandlerInterceptor | Use a HandlerInterceptor (preHandle/afterCompletion) instead of a Filter. Different timing surface than the SERVER span (interceptor runs after Spring MVC dispatch, filter wraps it). Diverges from Phase 2's filter-based pattern; teaches a parallel mechanism that the workshop doesn't otherwise touch. | |

**User's choice:** Extend HttpServerSpanFilter (Recommended)
**Notes:** Filter responsibility expands from "SERVER span" to "SERVER span + HTTP duration histogram"; class JavaDoc updated. Reuses the existing `try/finally` timing surface — no new filter chain ordering concern. D-06 `/actuator/*` exclusion guarantees no health-check noise in the histogram.

---

## Observable gauge host & source (orders.queue.depth.estimate)

| Option | Description | Selected |
|--------|-------------|----------|
| Consumer + ThreadLocalRandom synthetic | Consumer-service registers the ObservableGauge; the callback returns ThreadLocalRandom.nextInt(0, 50) (or similar bounded synthetic). Semantically aligned (queue depth belongs to the consumer side), gives the consumer's SdkMeterProvider a real instrument to emit, satisfies REQUIREMENTS' 'synthetic' clause. Phase 4 touches a small consumer file (e.g., a new QueueDepthGauge @Component or inline in OtelSdkConfiguration). | ✓ |
| Producer + ThreadLocalRandom synthetic | Producer-service hosts all three instruments (Counter + Histogram + Gauge). Single-service read-through for the metrics lesson. Consumer's MeterProvider exists but emits no business metric (only any SDK self-metrics). Less semantically clean; gauge name 'queue.depth' on the publisher side reads oddly. | |
| Both services + ThreadLocalRandom on each | Each service registers its own gauge. Symmetry, but duplicates the teaching surface; attendees read essentially the same callback twice. Doubles the number of series in Mimir for one lesson. | |
| Consumer + bind to APP-04 AtomicInteger | Reuse Phase 3's processed-counter; gauge returns counter.get() % 10 or similar. Couples Phase 4 to Phase 3 internals; the value isn't really 'queue depth'; harder to read without flipping back to ProcessingService. | |

**User's choice:** Consumer + ThreadLocalRandom synthetic (Recommended)
**Notes:** Hosting structure (inline `@PostConstruct` in OtelSdkConfiguration vs new `@Component QueueDepthGauge`) left at planner's discretion (CONTEXT.md D-18) — both shapes are acceptable. Gauge uses `ofLongs()` (not the default double) to keep the integer queue-depth conventional shape.

---

## Meter wiring shape (SdkMeterProvider integration)

| Option | Description | Selected |
|--------|-------------|----------|
| Extract per-provider private helpers | Refactor existing tracer-provider construction into private SdkTracerProvider buildTracerProvider(Resource) and add private SdkMeterProvider buildMeterProvider(Resource). The @Bean openTelemetry() becomes an orchestrator: build resource, call helpers, register on OpenTelemetrySdk.builder(). Each helper is its own comment-dense block. Phase 5 lands cleanly as another helper (buildLoggerProvider). Resource is passed explicitly — dependency visible. Diff for Phase 4 reads as 'sibling pipeline added.' | ✓ |
| Inline inside the existing @Bean | Keep adding directly inside openTelemetry() body — Resource, then SpanExporter+BatchSpanProcessor+Sampler+SdkTracerProvider, then MetricExporter+PeriodicMetricReader+SdkMeterProvider, then propagators, then SDK builder. Mirrors current style. By Phase 5 the @Bean is ~90+ lines. One file, one method, but a long one. | |
| Separate @Bean for each provider | @Bean SdkTracerProvider, @Bean SdkMeterProvider; OpenTelemetry openTelemetry(SdkTracerProvider, SdkMeterProvider, ContextPropagators) consumes them. Most Spring-idiomatic, but introduces lifecycle subtleties (which bean's destroyMethod fires when relative to SdkTracerProvider.shutdown vs OpenTelemetrySdk.close)? Pedagogically off-message for the SDK lesson. | |

**User's choice:** Extract per-provider private helpers (Recommended)
**Notes:** The refactor diff is itself a teaching artifact — "we added a sibling pipeline" rather than "we shoved more stuff inside one method". By Phase 5 the @Bean stays under ~30 lines with three readable helpers (`buildTracerProvider`, `buildMeterProvider`, `buildLoggerProvider`). Resource is built once in the @Bean body and passed to all helpers — guarantees identical resource attributes across all three signals (necessary for cross-signal correlation in Grafana).

---

## Claude's Discretion

- **Hosting structure for the ObservableGauge** (D-18): inline `@PostConstruct` in `OtelSdkConfiguration.java` vs. a new small `@Component QueueDepthGauge` class. Both shapes are acceptable per the discussion; planner picks one based on file-count vs single-touch-point preference. Neither violates the Phase 2 "no helper at instrumentation call sites" rule (this is registration boilerplate, not a span/counter call site).
- **Exact `mise demo:order` task wording** for the two `priority` values (D-10): planner can choose two named tasks (`demo:order:express` + `demo:order:standard`), one parameterized task, or just two README-snippet curls. The decision is "two values must reach Mimir" — implementation shape is open.

## Deferred Ideas

- Consumer-side `orders.processed.total` / `orders.failed.total` counters mirroring producer's `orders.created` — would give consumer's MeterProvider a Counter alongside its Gauge; out of v1 scope (METRIC-01..04 exhausted by current decisions); revisit in Phase 7 if attendee feedback flags asymmetry.
- Custom histogram bucket tuning via `View` / `ExplicitBucketHistogramAggregation` — real-world concern, not a workshop SDK lesson; defaults sufficient. Potential Phase 7 polish callout.
- Real RabbitMQ queue depth via Management API (`:15672/api/queues/...`) — METRIC-04 is "synthetic" by spec; real impl is a non-trivial integration. Out of scope; potential Phase 7 polish topic.
- Exemplars (linking metric data points to traces) — OTel SDK 1.61.0 supports this on histograms; pedagogically powerful but adds a second config concern; deferred to v2.
- Producer-side `messages.published.total` counter — would parallel consumer's gauge conceptually but isn't in METRIC-01..04. Same rationale as consumer counter deferral.
