# Phase 16: Head Sampling + W3C Baggage - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Two SDK sub-lessons taught together under one git tag (`step-16-sampling-baggage`):

**Sub-lesson 16a — Head Sampling:** Both services swap `Sampler.parentBased(Sampler.alwaysOn())` for `Sampler.parentBased(Sampler.traceIdRatioBased(0.5))` inside `OtelSdkConfiguration.buildTracerProvider()`. The change is programmatic (NOT via `OTEL_TRACES_SAMPLER` env var — F7-1). Workshop attendees observe that the SDK discards traces before export — no Collector-side processor ever sees the dropped traces. The README contrasts head sampling (SDK-side, pre-export) with tail sampling (Collector-side, post-assembly) via a paired comparison table.

**Sub-lesson 16b — W3C Baggage:** `OrderController.create()` reads an `X-Customer-Tier` HTTP header (default `"standard"`), sets it as the `customer-tier` baggage entry, and scopes the baggage `Context` around `orderService.place()`. A new shared `BaggageSpanAttributeProcessor` in `otel-bootstrap/context/` stamps allowlisted baggage keys as `baggage.<key>` span attributes on every span start. `TracingMessageListenerAdvice` is restructured so `extractedContext.makeCurrent()` wraps the entire listener body (BAG-03), making baggage available on the consumer side. The attendee sees `baggage.customer-tier=gold` on both producer-side and consumer-side spans in Tempo.

This is an **OtelSdkConfiguration + otel-bootstrap + producer-service controller phase** with a minor otel-bootstrap/amqp edit. No infrastructure changes.

Phase boundaries:
- **Edits** both `OtelSdkConfiguration.java` files (sampler swap + processor registration)
- **Edits** `otel-bootstrap/amqp/TracingMessageListenerAdvice.java` (extracted context scoping)
- **Edits** `producer-service/api/OrderController.java` (X-Customer-Tier header + baggage setup)
- **Edits** `scripts/load.sh` (X-Customer-Tier header rotation)
- **Creates** `otel-bootstrap/context/BaggageSpanAttributeProcessor.java`
- **Does not touch** `docker-compose.yml` or `infra/observability/*` (no infrastructure changes)
- **Does not touch** `consumer-service/config/OtelSdkConfiguration.java` beyond sampler + processor (no new providers or exporters)
- **Does not touch** `TracingMessagePostProcessor` or `TracingClientHttpRequestInterceptor` (baggage propagation is automatic via existing `W3CBaggagePropagator` in the composite propagator chain)

Out of scope for this phase:
- AMQP topology variants (Phase 17)
- Additional baggage keys beyond `customer-tier`
- Baggage-based tail sampling policies at the Collector

</domain>

<decisions>
## Implementation Decisions

### Baggage lifecycle scoping

- **D-B1:** **Controller-scoped baggage.** `OrderController.create()` reads `X-Customer-Tier` via `@RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard")` and wraps `orderService.place(payload)` in a `try(Scope)` with the baggage `Context`. Attendees see the full Baggage API surface — `Baggage.builder().put().build().storeInContext()` + `makeCurrent()` — in one method. Same "boilerplate is the lesson" ethos as Phase 2.

- **D-B2:** **Baggage key = `customer-tier`.** Span attribute = `baggage.customer-tier`. The `order.` prefix in BAG-01 was contextual description, not the literal key. Matches BAG-04 exactly.

- **D-B3:** **Scope wraps only `orderService.place()`.** The `try(Scope)` does NOT wrap the idempotency gate or logging. Baggage is active exactly where it matters: the AMQP publish (`TracingMessagePostProcessor` injects `baggage:` header) and the HTTP notification call (`TracingClientHttpRequestInterceptor` injects `baggage:` header). Both injections happen automatically via `W3CBaggagePropagator` in the existing composite propagator chain.

- **D-B5:** **SERVER span intentionally missing baggage attribute — teaching moment.** The `HttpServerSpanFilter` creates the SERVER span BEFORE the controller sets baggage. So the SERVER span does NOT carry `baggage.customer-tier`. The INTERNAL, PRODUCER, CLIENT, and CONSUMER spans (all created after `makeCurrent()`) DO carry it. The README calls this out: "Notice the SERVER span doesn't carry the attribute — it was created before you set baggage."

- **D-B6:** **`defaultValue = "standard"` on the annotation.** `@RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard") String customerTier` — Spring handles the default; `customerTier` is always non-null.

- **D-B7:** **Load script rotates through gold/silver/standard.** `scripts/load.sh` randomly picks from `[gold, silver, standard]` per request. Attendees see all three tiers in Tempo and can filter by `baggage.customer-tier`. Demonstrates the cardinality-control lesson (only 3 values match the allowlist).

### SpanProcessor wiring pattern

- **D-S1:** **Inline creation inside `buildTracerProvider()`.** `BaggageSpanAttributeProcessor` is created next to the existing `BatchSpanProcessor`, added FIRST (attribute-stamping before batching). Two `.addSpanProcessor()` calls on the builder. Self-contained method — DOC-05 per-service duplication stays intentional. The `Set.of("customer-tier")` allowlist is passed at construction time.

- **D-S2:** **Split into 4 plans.** Plan 16-01 (head sampling: sampler swap + verify:head-sampling + README §16a with F2-3 callout). Plan 16-02 (BaggageSpanAttributeProcessor: new class in otel-bootstrap + registration in both buildTracerProvider()). Plan 16-03 (baggage end-to-end: OrderController header + TracingMessageListenerAdvice BAG-03 + verify:baggage + README §16b). Plan 16-04 (tag + final verification). Each plan produces a verifiable state.

- **D-S3:** **New `context/` package in `otel-bootstrap`.** `com.example.otel.context.BaggageSpanAttributeProcessor` — cross-cutting concern separate from `amqp/` and `http/`. Taxonomy: `amqp/` for AMQP tracing, `http/` for HTTP tracing, `context/` for context propagation.

### Listener advice restructuring

- **D-L1:** **Outer `extracted.makeCurrent()` + inner `span.makeCurrent()`.** Two nested try-with-resources in `TracingMessageListenerAdvice.invoke()`. The outer scope makes the extracted context (including baggage) current for the entire listener body. The inner scope makes the CONSUMER span current. Minimal structural diff from current code — one new `try` wrapper around the existing logic.

- **D-L2:** **Just describe the change in the commit message.** No backwards-compatibility note — the change is self-evidently safe (empty baggage → `extracted.makeCurrent()` is a no-op for baggage).

- **D-L3:** **Keep `.setParent(extracted)`.** Even though it's now redundant with `extracted.makeCurrent()`, the line is marked LOAD-BEARING (ROADMAP SC #1) and is a teaching artifact. Attendees comparing v1.0 and v2.0 code see the same line in both versions. Explicit > implicit for the workshop.

### README sub-lesson narrative

- **D-R1:** **F2-3 warning box BEFORE the code change.** Prominent callout at the top of §16a, before the sampler swap code. Attendees read the double-filter-trap before activating head sampling. Explains the math: 50% head × 20% tail = 10% effective rate.

- **D-R2:** **Two numbered sub-sections.** `### Step 16a: Head Sampling` and `### Step 16b: W3C Baggage` as sub-sections under `## Step 16: Head Sampling + W3C Baggage`. Each has its own code walkthrough, verification command (`verify:head-sampling` / `verify:baggage`), and key takeaway.

- **D-R3:** **Side-by-side markdown table for head-vs-tail contrast.** Five dimensions: Where, Sees, Bandwidth, Decides on, Trade-off. Compact and scannable — the pedagogical centerpiece of 16a.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- `BaggageSpanAttributeProcessor` implementation details (reads `Baggage.fromContext(parentContext)` in `onStart`, iterates allowlist, stamps `baggage.<key>` attributes via `span.setAttribute()`)
- Exact sampler swap diff (one-line change per service: `Sampler.alwaysOn()` → `Sampler.traceIdRatioBased(0.5)` inside the `parentBased()` wrapper)
- `TestOtelConfiguration` updates for the new sampler and processor registration (X-4 mitigation)
- `verify:head-sampling` and `verify:baggage` mise task implementations (follow `verify:http-client-spans` pattern)
- Integration test assertions for baggage propagation (InMemorySpanExporter pattern — assert `baggage.customer-tier` attribute on both PRODUCER-side and CONSUMER-side spans)
- Screenshot deferral to Phase 18 Playwright pipeline (follows D-E9 precedent)
- OtelSdkConfiguration comment updates for the sampler swap (replace the "For production, swap to..." forward-looking comment)
- README §16 exact wording, length, and structure (follow Phase 14/15 precedent ~100-150 lines per sub-section)
- Whether `orderId` variable needs to be declared before the try(Scope) block for scoping reasons (code-level detail)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service SDK duplication), PROP-04 (shared `otel-bootstrap` for cross-cutting propagation), WORK-01 (annotated git tags on `main`)
- `.planning/REQUIREMENTS.md` § Head Sampling (HSAMP-01..03) + § Baggage (BAG-01..04) — locked requirements for sampler swap, BaggageSpanAttributeProcessor, listener advice context fix, and Tempo visibility
- `.planning/ROADMAP.md` Phase 16 section — pedagogical headline, Success Criteria #1–3, pitfall mitigations (X-1, X-4, F2-3, F7-1, F7-2, F7-3, F7-4), git tag `step-16-sampling-baggage`
- `.planning/STATE.md` — Phase 10–15 completion records, F2-3 double-filter-trap blocker documentation

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes operational arc
- `.planning/research/ARCHITECTURE.md` — system architecture, service boundaries, otel-bootstrap module structure
- `.planning/research/PITFALLS.md` § F2 (F2-3 double-filter trap: head + tail sampling compound effect) + § F7 (F7-1..F7-4: head sampling and baggage pitfalls)

### Prior phase context (MUST read — patterns to mirror)

- `.planning/phases/15-outbound-http-client-spans/15-CONTEXT.md` — D-H8 (HttpClientConfig parallel to RabbitConfig), D-H10 (constructor-injected config in otel-bootstrap), D-H2 (fire-and-forget pattern)
- `.planning/phases/14-jdbc-jpa-database-spans/14-CONTEXT.md` — D-J1 (replace not coexist), D-01 inline span template pattern
- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — PREREQ-01 circular-ref fix (X-1 already resolved), D-01 (datasource UID preservation)

### Files this phase EDITS or CREATES

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — **EDITED** (sampler swap in `buildTracerProvider()`, `BaggageSpanAttributeProcessor` inline creation + registration)
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — **EDITED** (same sampler swap + processor registration)
- `otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java` — **NEW** SpanProcessor that stamps allowlisted baggage keys as `baggage.<key>` span attributes
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` — **EDITED** (outer `extracted.makeCurrent()` wrapping the listener body for BAG-03)
- `producer-service/src/main/java/com/example/producer/api/OrderController.java` — **EDITED** (`X-Customer-Tier` header + baggage context setup around `orderService.place()`)
- `scripts/load.sh` — **EDITED** (X-Customer-Tier header rotation through gold/silver/standard)
- `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` — **EDITED** (sampler + processor changes for test configuration)
- `mise.toml` — additive `[tasks."verify:head-sampling"]` and `[tasks."verify:baggage"]` blocks
- `README.md` — additive §16 section with sub-sections 16a and 16b

### Files this phase does NOT edit

- `docker-compose.yml` — no infrastructure changes
- `infra/observability/*` — no observability config changes (head sampling is SDK-side, not Collector-side)
- `otel-bootstrap/amqp/TracingMessagePostProcessor.java` — baggage injection into AMQP headers is automatic via `W3CBaggagePropagator`
- `otel-bootstrap/http/TracingClientHttpRequestInterceptor.java` — baggage injection into HTTP headers is automatic via `W3CBaggagePropagator`
- `grafana/dashboards/ose-otel-demo.json` — no new dashboard panel

### Upstream documentation references (research must consult)

- [OTel Java SDK Sampler API](https://opentelemetry.io/docs/languages/java/instrumentation/#sampling) — `Sampler.parentBased()`, `Sampler.traceIdRatioBased()`, sampling decision lifecycle
- [OTel Java SDK SpanProcessor API](https://opentelemetry.io/docs/languages/java/instrumentation/#span-processor) — `onStart(Context, ReadWriteSpan)` lifecycle, multiple processor registration
- [W3C Baggage specification](https://www.w3.org/TR/baggage/) — header format, key/value semantics
- [OTel Java Baggage API](https://opentelemetry.io/docs/languages/java/instrumentation/#baggage) — `Baggage.builder().put().build().storeInContext()`, `Baggage.fromContext()`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`producer-service/config/OtelSdkConfiguration.java` lines 402-420** — THE sampler definition block. Currently `Sampler.parentBased(Sampler.alwaysOn())` with a forward-looking comment that literally says "For production, swap to `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))`". The diff is one line per service.
- **`otel-bootstrap/amqp/TracingMessageListenerAdvice.java` lines 108-128** — THE context extraction and span creation block. The extracted context at line 109 already contains baggage (via `W3CBaggagePropagator` in the composite propagator). BAG-03 adds `extracted.makeCurrent()` wrapping the span creation + `inv.proceed()`.
- **`producer-service/api/OrderController.java` line 29** — Already reads `X-Idempotency-Key` via `@RequestHeader`. Adding `X-Customer-Tier` follows the same pattern. The method signature grows by one parameter.
- **`otel-bootstrap/amqp/TracingMessagePostProcessor.java`** — Calls `openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), ...)` which automatically includes `W3CBaggagePropagator`. When baggage is in `Context.current()`, the `baggage:` header is injected into AMQP message headers automatically. NO changes needed.
- **`otel-bootstrap/http/TracingClientHttpRequestInterceptor.java`** — Same propagator injection pattern. Baggage header is injected into outbound HTTP headers automatically. NO changes needed.

### Established Patterns

- **DOC-05 per-service duplication** — Both `OtelSdkConfiguration` files change identically (modulo service.name and scope name). The sampler swap and processor registration are duplicated by design.
- **D-01 inline span template** — `spanBuilder().setSpanKind().setAttribute(...)startSpan()` → `try(Scope) { work } catch { recordException, setStatus(ERROR) } finally { span.end() }`. The listener advice restructuring (D-L1) adds an outer `extracted.makeCurrent()` try-with-resources wrapping this established pattern.
- **Constructor-injected config in otel-bootstrap** — Every class that takes configuration receives it via constructor parameters (D-H10 from Phase 15). `BaggageSpanAttributeProcessor` takes `Set<String> allowedKeys` in its constructor.
- **W3C propagator composition already wired** — Both `OtelSdkConfiguration` files compose `W3CTraceContextPropagator.getInstance()` + `W3CBaggagePropagator.getInstance()` (lines 208-211 producer, 216-219 consumer). The propagation layer is ready; Phase 16 adds the application-layer SET/READ/STAMP on top.

### Integration Points

- **`OrderController.create()` method signature** — Grows from 2 parameters to 3 (adds `@RequestHeader X-Customer-Tier`). The `try(Scope)` block wraps the `orderService.place(payload)` call.
- **`buildTracerProvider()` in both `OtelSdkConfiguration` files** — Two changes: (1) sampler line swap, (2) new `BaggageSpanAttributeProcessor` inline creation + `.addSpanProcessor()` call before the existing `BatchSpanProcessor`.
- **`TracingMessageListenerAdvice.invoke()`** — Structural change: wrap existing `Span span = ... try(Scope) { inv.proceed() }` logic inside a new outer `try (Scope ctxScope = extracted.makeCurrent()) { ... }`.
- **`scripts/load.sh`** — curl command gains `-H "X-Customer-Tier: $TIER"` with random rotation through gold/silver/standard.

</code_context>

<specifics>
## Specific Ideas

- The user chose **controller-scoped baggage** (D-B1) — attendees see the entire Baggage API surface in `OrderController.create()`, right at the HTTP entry point. This is consistent with the "boilerplate is the lesson" ethos: the baggage setup code is visible where the request enters, not hidden in a filter.
- The user chose **SERVER span missing baggage as a teaching moment** (D-B5) — the trace waterfall visually demonstrates that baggage has a lifecycle. The INTERNAL/PRODUCER/CLIENT/CONSUMER spans all carry `baggage.customer-tier`, but the SERVER span does not. The README calls this out explicitly.
- The user chose **outer extracted + inner span nesting** (D-L1) — the minimal restructuring of `TracingMessageListenerAdvice` adds one try-with-resources wrapper without changing the inner span creation logic. The LOAD-BEARING `.setParent(extracted)` line is preserved for teaching continuity (D-L3).
- The user chose **4-plan split** (D-S2) matching the sub-lesson structure — each plan maps to a verifiable intermediate state. Plan 16-01 (head sampling) and Plan 16-03 (baggage end-to-end) are the pedagogical load-bearing plans.
- The user chose **F2-3 warning BEFORE the code** (D-R1) — attendees read the double-filter-trap before activating head sampling, preventing confusion about unexpected trace counts.
- The user chose **3-value rotation** in load.sh (D-B7) — gold/silver/standard per request demonstrates the cardinality-control lesson and the allowlist filtering in `BaggageSpanAttributeProcessor`.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 16-head-sampling-w3c-baggage*
*Context gathered: 2026-05-04*
