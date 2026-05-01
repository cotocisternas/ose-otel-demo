# Phase 2: Manual SDK Bootstrap & First Traces - Context

**Gathered:** 2026-05-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 wires the smallest viable OpenTelemetry SDK in BOTH services so that one `POST /orders` produces TWO disconnected traces in Tempo — one rooted at the producer's HTTP SERVER span, one rooted at the consumer's CONSUMER span. The disconnection is INTENTIONAL: it sets up the broken-then-fixed pedagogical payoff that Phase 3 delivers by adding W3C context propagation across the AMQP boundary.

**In scope (Phase 2 delivers):**
- `OtelSdkConfiguration.java` in producer-service AND consumer-service (deliberately duplicated per TRACE-01 / DOC-05) — the file IS the workshop's textbook for the SDK setup
- Resource (service.name + namespace + instance.id + deployment.environment.name via semconv 1.40.0 constants)
- `SdkTracerProvider` + `BatchSpanProcessor` + `OtlpGrpcSpanExporter → :4317` + explicit `Sampler.parentBased(alwaysOn())` with multi-paragraph teaching comment
- `@Bean(destroyMethod = "close")` so Ctrl-C flushes the last batch
- Composite `W3CTraceContextPropagator` + `W3CBaggagePropagator` registered (Phase 3 will exercise them)
- `@Bean Tracer` exposed per service for constructor injection
- 5 spans per `POST /orders` round-trip: SERVER (`POST /orders`), INTERNAL (`OrderService.place`), PRODUCER (`orders.created publish`) on the producer side; CONSUMER (`orders.created process`), INTERNAL (`ProcessingService.process`) on the consumer side
- HTTP semconv attributes on the SERVER span (TRACE-05) + AMQP semconv attributes on PRODUCER and CONSUMER (TRACE-07/08)
- README updates: DOC-03 (heavily commented `OtelSdkConfiguration`) + DOC-05 (per-service-duplication callout)
- Annotated git tag `step-02-traces` on `main` (WORK-01)

**Out of scope (deferred to later phases):**
- W3C context propagation across the AMQP boundary (Phase 3 — THE headline lesson; the producer and consumer are SUPPOSED to be in separate traces in Phase 2)
- Populating `otel-bootstrap` with the propagation pair (Phase 3)
- `recordException` + `setStatus(ERROR)` triggered by APP-04's deterministic 10% failure (Phase 3 — the fail path doesn't exist yet)
- Metrics (Phase 4) and logs (Phase 5) signals — Phase 2's `OtelSdkConfiguration` ONLY wires `SdkTracerProvider`; meter/logger providers come later
- Tests (Phase 6 — `InMemorySpanExporter`-driven `@SpringBootTest` against a Testcontainers RabbitMQ)

</domain>

<decisions>
## Implementation Decisions

### Span-wrapping idiom

- **D-01:** Every span is wrapped using the **pure inline pattern** at every call site:
  ```java
  Span span = tracer.spanBuilder("name").setSpanKind(SpanKind.X).setAttribute(...).startSpan();
  try (Scope scope = span.makeCurrent()) {
      // body
  } catch (RuntimeException e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      throw e;
  } finally {
      span.end();
  }
  ```
  No helper class. No AOP aspect. Boilerplate is the lesson — the workshop teaches the SDK by making attendees read SDK calls in business code.

- **D-02:** **Constructor-inject `Tracer`** in every instrumented class (`OrderController`, `OrderService`, `OrderPublisher`, `OrderListener`, `ProcessingService`). `OtelSdkConfiguration` exposes one `@Bean Tracer tracer(OpenTelemetry o)` per service via `o.getTracer("com.example.producer")` / `o.getTracer("com.example.consumer")`. Instrumentation scope is the service package.

- **D-03:** **Include the catch block in Phase 2** (forward-compatible). Phase 2 spans use the full try/catch/finally shape with `span.recordException(e); span.setStatus(StatusCode.ERROR); throw e;`. Phase 3 ONLY adds the propagation pair (otel-bootstrap classes) plus APP-04's failure path; it does NOT restructure existing methods. Keeps the `step-02-traces..step-03-context-propagation` git diff small and focused on propagation (Phase 3 SC #5 is load-bearing).

- **D-04:** **Span naming convention** — semconv where applicable, ClassName.method for INTERNAL:
  - SERVER: `POST /orders` (semconv: METHOD ROUTE)
  - PRODUCER: `orders.created publish` (semconv: DESTINATION OPERATION; note `orders.created` is the QUEUE name; the destination is also the routing key here for the direct exchange)
  - CONSUMER: `orders.created process`
  - INTERNAL: `OrderService.place`, `ProcessingService.process` (no semconv mandate; dotted Java identifier is conventional)

### SERVER-span hook point

- **D-05:** SERVER span on `POST /orders` is created in an **`OncePerRequestFilter` registered as `@Bean`**, NOT inline in `OrderController` and NOT via `HandlerInterceptor`. The filter wraps every HTTP request; `http.response.status_code` is naturally available on the way out (after `chain.doFilter`).

- **D-06:** Filter **excludes paths starting with `/actuator/`** with an early-return + a code comment explaining why (docker-compose healthchecks would flood Tempo with noise). The exclusion is itself a teaching moment about the production tradeoff between sampling and filtering.

- **D-07:** SERVER-span filter exists in **producer-service ONLY**, not consumer-service. Consumer's only HTTP surface is `/actuator/health`, which is excluded anyway. Per-service-duplication ethos applies to the SDK BOOTSTRAP (`OtelSdkConfiguration`), not to instrumentation surfaces — they exist where the surface exists.

### PRODUCER/CONSUMER span home

- **D-08:** PRODUCER span lives **inline in `producer-service/.../messaging/OrderPublisher.publish(...)`**, wrapping the `rabbitTemplate.convertAndSend(...)` call. CONSUMER span lives **inline in `consumer-service/.../messaging/OrderListener.onOrder(...)`**, wrapping the body. `otel-bootstrap` stays as the empty Phase 1 placeholder until Phase 3.

- **D-09:** Phase 3 will **REPLACE** these inline spans with the `otel-bootstrap` propagation pair (`TracingMessagePostProcessor` + `TracingMessageListenerAdvice`). The Phase 2→Phase 3 git diff therefore shows: + 2 new classes in otel-bootstrap; + ~4 wiring lines per service (post-processor / advice registration); − inline span code in `OrderPublisher` / `OrderListener`. **This is the diff Phase 3 SC #5 mandates be readable in one viewing**, so Phase 3's plan must explicitly delete the Phase 2 inline span code as part of installing the propagation pair. → see Deferred Ideas: Phase 3 hand-off note.

- **D-10:** Consumer-side CONSUMER span **explicitly starts from `Context.root()`** with a multi-line code comment that previews the Phase 3 idiom:
  ```java
  // Phase 2: starting from Context.root() because no propagation yet —
  // Phase 3 replaces this with:
  //   Context extracted = propagator.extract(Context.root(), messageProperties, getter);
  // The structural shape stays IDENTICAL.
  Span span = tracer.spanBuilder("orders.created process")
      .setParent(Context.root())
      .setSpanKind(SpanKind.CONSUMER) ...
  ```
  Makes the "broken" Phase 2 state INTENTIONAL (not accidental), and previews the Phase 3 pattern. Addresses PITFALLS.md #7 (listener-thread context loss) preemptively.

- **D-11:** PRODUCER span sets `messaging.rabbitmq.destination.routing_key` (semconv) in addition to TRACE-07's mandated trio (`messaging.system=rabbitmq`, `messaging.destination.name`, `messaging.operation=publish`). One extra `.setAttribute()` line teaches AMQP-specific semconv beyond generic messaging.

### SDK env-var resolution

- **D-12:** OtelSdkConfiguration uses **manual `System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")` reads with `Optional.ofNullable(...).orElse("http://localhost:4317")` defaults**. NO `opentelemetry-sdk-extension-autoconfigure` dependency in Phase 2. Pure pedagogy: the env-var contract is visible IN code; nothing is magic. Phases 4-5 may revisit when wiring meter / logger providers; the pattern stays the same.

- **D-13:** **Resource attributes** (locked into the OtelSdkConfiguration preview):
  - `service.name`: hardcoded per service (`"order-producer"` / `"order-consumer"`)
  - `service.namespace`: `"ose-otel-demo"`
  - `service.instance.id`: `UUID.randomUUID().toString()` — regenerated per JVM start (correct for workshop where each `mise run dev` IS a new instance)
  - `deployment.environment.name`: `"workshop"`
  - All keys via `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` constants (`ServiceAttributes.*`, `DeploymentAttributes.*`) — the legacy `io.opentelemetry:opentelemetry-semconv` from the SDK BOM is deprecated and MUST NOT be used.

- **D-14:** **Sampler explicit:** `Sampler.parentBased(Sampler.alwaysOn())` with a multi-paragraph teaching comment about the production tradeoff (`Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` for 10% sampling; ParentBased respects the upstream sampling decision via the `traceparent` flag byte). Addresses PITFALLS.md #6.

- **D-15:** **BatchSpanProcessor with default tuning** (5s schedule, 2048 max queue, 512 max batch). Production-shape, NOT SimpleSpanProcessor (which is reserved for Phase 6 test config per Phase 6 SC #3). `@Bean(destroyMethod = "close")` ensures graceful shutdown flushes the last batch — addresses PITFALLS.md #4.

- **D-16:** **Composite propagators registered now**: `ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))`. Phase 2 doesn't EXERCISE them (no inject/extract on the AMQP boundary yet) but the SDK is ready — Phase 3 just calls `openTelemetry.getPropagators().getTextMapPropagator()` to use what's already wired.

### Claude's Discretion

These small decisions are not user-locked — the planner can pick reasonable defaults:

- **HTTP semconv attribute set on SERVER span:** TRACE-05's mandated three (`http.request.method`, `url.path`, `http.response.status_code`) PLUS `url.scheme`, `server.address`, `server.port`, `http.route`. All using `io.opentelemetry.semconv` constants (`HttpAttributes.*`, `UrlAttributes.*`, `ServerAttributes.*`). Stop short of body sizes / user-agent — those are recommended-not-required and would add noise without adding pedagogy.
- **Filter `@Order`:** default Spring Boot ordering. No competing filters in this demo (no Spring Security, no CORS).
- **`OtelSdkConfiguration` package path:** `com.example.{producer|consumer}.config` (alongside existing `RabbitConfig.java`).
- **Service-name source:** hardcode the strings (`"order-producer"`, `"order-consumer"`) directly in each `OtelSdkConfiguration` rather than reading `spring.application.name` via `@Value`. The duplication is the point; reading from config would split the visible service identity across two files per service.
- **`OTEL_EXPORTER_OTLP_PROTOCOL` env var:** mise.toml sets it to `"grpc"`, but Phase 2 uses `OtlpGrpcSpanExporter` explicitly so the var is informational. Don't read it; document the intentional redundancy in `OtelSdkConfiguration`'s comment (the env var is forward-looking for any team that switches to autoconfigure later).
- **Sampler teaching comment depth:** multi-paragraph (4-8 lines) explaining (a) what `parentBased(alwaysOn())` actually does, (b) what `parentBased(traceIdRatioBased(0.1))` would change, (c) why `parentBased` matters for distributed traces (consistent decision via `traceparent` flag). Aligns with DOC-03 ("the code IS the textbook").
- **README DOC-03 / DOC-05 wording:** brief but explicit. DOC-05 callout should be a single paragraph: "`OtelSdkConfiguration` is duplicated per service on purpose — refactoring it into a shared `@AutoConfiguration` bean would hide one of the two readings the workshop is built around."
- **mise.toml verification task:** consider adding a `mise run verify:traces` task that does `curl :3000/api/datasources` (Tempo health) + a tiny smoke `POST /orders` round-trip; can also be left to Phase 6's full Testcontainers run.
- **Phase 2 ships ZERO test code** — all tests deferred to Phase 6 per the roadmap. (The Phase 1 baseline already proves `mvn test` runs; we don't add per-phase test fixtures.)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level (read first)
- `.planning/PROJECT.md` — pinned tech stack, out-of-scope items, Key Decisions table
- `.planning/REQUIREMENTS.md` §TRACE-01 through TRACE-08 + DOC-03 + DOC-05 + WORK-01 — exact user-testable acceptance criteria for Phase 2
- `.planning/ROADMAP.md` §"Phase 2: Manual SDK Bootstrap & First Traces" — goal statement, dependencies, 6 success criteria, notes on what's deliberately deferred (TRACE-09 / APP-04 → Phase 3)
- `.planning/STATE.md` — current phase status, accumulated decisions

### Research synthesis (read for context)
- `.planning/research/SUMMARY.md` §"Phase 2" + §"Recommended Stack" + §"Critical Pitfalls" — research-distilled rationale for the SDK approach
- `.planning/research/STACK.md` — exact BOM coordinates and versions (`opentelemetry-bom:1.61.0`, `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`, `opentelemetry-semconv:1.40.0`); semconv coordinate guidance (use new `io.opentelemetry.semconv:*`, NOT legacy `io.opentelemetry:opentelemetry-semconv`)
- `.planning/research/PITFALLS.md` — pitfalls Phase 2 MUST address: #3 unknown_service:java, #4 telemetry-lost-on-shutdown, #6 sampler defaults, #7 listener-thread context loss (preempt for Phase 3), #8 OTLP gRPC vs HTTP port confusion. Pitfalls 1, 2, 5 belong to later phases.
- `.planning/research/ARCHITECTURE.md` Pattern 1 (SDK bootstrap shape) and Pattern 3 (tag immutability)
- `.planning/research/FEATURES.md` TS-1..TS-12 (table-stakes features Phase 2 implements), AF-15 (anti-feature: shared SDK library — explicitly forbidden)

### Phase 1 prior art (read for code shape)
- `.planning/phases/01-baseline-scaffold/1-RESEARCH.md` — BOM ordering rationale, mise/IDE pitfalls (already neutralised in Phase 1, but explains the constraints the OtelSdkConfiguration runs inside)
- `.planning/phases/01-baseline-scaffold/1-04-producer-service-PLAN.md` — current producer-service shape (OrderController, OrderService, OrderPublisher, RabbitConfig)
- `.planning/phases/01-baseline-scaffold/1-05-consumer-service-PLAN.md` — current consumer-service shape (OrderListener, ProcessingService, RabbitConfig)
- `.planning/phases/01-baseline-scaffold/1-01-SUMMARY.md` — actual Phase 1 BOM imports as built (parent pom.xml lines 50-92)

### External standards (read when implementing semconv / propagation)
- OpenTelemetry Java SDK 1.61.0 docs — `OpenTelemetrySdk.builder()`, `SdkTracerProvider`, `BatchSpanProcessor`, `OtlpGrpcSpanExporter`, `Sampler.parentBased`, `ContextPropagators`
- W3C Trace Context spec — `traceparent` header format (relevant for Phase 3, but propagators are wired in Phase 2)
- OTel Resource semconv — `service.*` and `deployment.*` attribute keys (in `io.opentelemetry.semconv:1.40.0`)
- OTel Messaging semconv (RabbitMQ profile) — `messaging.system`, `messaging.destination.name`, `messaging.operation`, `messaging.rabbitmq.destination.routing_key`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (already built in Phase 1)
- **`producer-service/.../api/OrderController.java`** — `POST /orders` handler returning 202; the SERVER span filter wraps this without modifying the controller
- **`producer-service/.../domain/OrderService.java`** — `place(payload)`; INTERNAL span goes here, wrapping the call to `publisher.publish(...)`
- **`producer-service/.../messaging/OrderPublisher.java`** — `publish(orderId, payload)` calling `rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message)`; PRODUCER span wraps this call
- **`consumer-service/.../messaging/OrderListener.java`** — `@RabbitListener(queues = QUEUE) onOrder(message)`; CONSUMER span wraps the body. Already imports SLF4J (good — Phase 5 logs correlation will reuse)
- **`consumer-service/.../domain/ProcessingService.java`** — `process(order)` (currently empty body, with comment "Phase 3 wires up the deterministic 10% failure path"); INTERNAL span wraps this
- **`producer-service/src/main/resources/application.yaml`** — `spring.application.name: order-producer` already set (matches the hardcoded `service.name` in OtelSdkConfiguration; redundant but harmless — both files name the service)
- **`consumer-service/src/main/resources/application.yaml`** — same shape, `spring.application.name: order-consumer`
- **`otel-bootstrap/.../package-info.java`** — empty placeholder with a comment that explicitly says "Phase 1 ships this module empty. Phase 3 populates it with the shared AMQP propagation pair." Phase 2 leaves this untouched.

### Established Patterns (from Phase 1, follow these)
- **POM BOM order is load-bearing** (`parent pom.xml` lines 50-92): OTel BOM first, OTel Instrumentation BOM second, Spring Boot BOM third. Maven enforcer's `dependencyConvergence` rule fires on `validate` — Phase 2's new dependencies must converge or the build fails. Verify with `mvn dependency:tree -Dincludes=io.opentelemetry` (Phase 1 baseline asserts this returns ZERO; Phase 2 baseline asserts every artifact has exactly ONE version).
- **mise.toml is the source of truth for env vars**: `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` and `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` are already pre-wired (lines 4-5 per Bash grep). The new OtelSdkConfiguration just READS them; no mise.toml change needed.
- **mise tasks** (`mise run dev`, `mise run dev:producer`, `mise run dev:consumer`, `mise run preflight`, `mise run infra:up`) are stable and reused as-is. No new tasks needed for Phase 2 unless we add a verification task (Claude's discretion).
- **Spring config classes live in `.../<service>/config/`** (currently `RabbitConfig.java`). Add `OtelSdkConfiguration.java` in the same package. Add `HttpServerSpanFilter.java` (or in-line as anonymous class in OtelSdkConfiguration — planner's call) in the producer-service config package only.
- **Module structure (parent + 3 children) is fixed** — no new modules in Phase 2. Spring Boot maven plugin already wired in each service's pom.xml.

### Integration Points (where Phase 2 plugs into existing code)
- `producer-service/pom.xml` — needs to ADD: `opentelemetry-api`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp`, `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0`. NO version tags (BOM-managed). NO `opentelemetry-sdk-extension-autoconfigure`. NO `opentelemetry-logback-appender-1.0` (Phase 5).
- `consumer-service/pom.xml` — same dependency additions as producer.
- `OrderController` — adds `Tracer` constructor parameter (no body change since SERVER span lives in the filter). Actually: only INTERNAL span if controller does any work; current controller is a thin pass-through to `OrderService.place`, so it may NOT need a Tracer. Planner verifies.
- `OrderService` — adds `Tracer` constructor parameter; `place(...)` body wrapped in INTERNAL span.
- `OrderPublisher` — adds `Tracer` constructor parameter; `publish(...)` body wrapped in PRODUCER span (with messaging semconv attrs + routing_key).
- `OrderListener` — adds `Tracer` constructor parameter; `onOrder(...)` body wrapped in CONSUMER span (`Context.root()` parent + messaging semconv attrs).
- `ProcessingService` — adds `Tracer` constructor parameter; `process(...)` body wrapped in INTERNAL span.
- `application.yaml` — no change needed; spring.application.name already set, env vars resolve via mise.

</code_context>

<specifics>
## Specific Ideas

- The CONSUMER-span code comment that previews Phase 3's `propagator.extract(Context.root(), messageProperties, getter)` line is load-bearing for the workshop — without it, attendees see the explicit `Context.root()` and don't understand WHY it's there. The comment is a Phase 2 deliverable, not an afterthought.
- The Phase 2 git tag `step-02-traces` is the workshop checkpoint where attendees first see "TWO traces in Tempo for one POST" — README must explicitly highlight this in DOC-04 (deferred to Phase 7) but the SDK config + spans must SHIP this exact behavior at this tag.
- The user explicitly preferred PURE-INLINE span wrapping over a helper class — this is a workshop-pedagogy preference; downstream phases must NOT introduce a `Spans.run(...)` helper later either. Phases 3-5 follow the same try/scope/try/catch/finally idiom.
- BatchSpanProcessor stays default-tuned even though we'll lose the last 5 seconds of telemetry on hard kills — `@Bean(destroyMethod = "close")` handles graceful shutdown, which is what the workshop is teaching. Phase 6 will swap to SimpleSpanProcessor in `@TestConfiguration`.

</specifics>

<deferred>
## Deferred Ideas

### Phase 3 hand-off (load-bearing)
- **Phase 3 MUST delete the inline PRODUCER span in `OrderPublisher.publish(...)` when installing the `TracingMessagePostProcessor`** — the post-processor takes over both the inject AND the PRODUCER span lifecycle. Same on the consumer side: `TracingMessageListenerAdvice` REPLACES the inline CONSUMER span in `OrderListener.onOrder(...)`. If the inline spans aren't removed, Phase 3 produces DOUBLE spans (one from the inline code, one from the bootstrap pair) and the trace is malformed. → Add this as an explicit pre-condition / first task in the Phase 3 plan.
- **Phase 3 keeps the INTERNAL spans** (`OrderService.place`, `ProcessingService.process`) and the SERVER span filter intact — those are NOT replaced.
- **Phase 3 reuses the propagators already wired** in Phase 2's `OpenTelemetrySdk.builder().setPropagators(...)` — calls `openTelemetry.getPropagators().getTextMapPropagator()` rather than constructing a new propagator.

### Phase 4 hand-off
- Adding `SdkMeterProvider` to `OtelSdkConfiguration` should follow the same env-var pattern (`OTEL_EXPORTER_OTLP_ENDPOINT` reused). The `@Bean(destroyMethod = "close")` keeps working — `OpenTelemetrySdk.close()` cascades to all providers.

### Phase 5 hand-off
- Adding `SdkLoggerProvider` + `OpenTelemetryAppender` follows the same pattern; the `@PostConstruct` install pattern (PITFALLS.md #5) is the new piece.

### Phase 7 hand-off
- DOC-04 (broken-vs-fixed Tempo screenshots) needs Phase 2's two-disconnected-traces state captured BEFORE Phase 3 fixes it. Workshop facilitator should grab the screenshot at the `step-02-traces` tag.

### Roadmap backlog (out of phase scope)
- None — discussion stayed within Phase 2 scope. The user explicitly redirected potential scope-adjacent items (test code, README walkthrough body, Grafana dashboard) to their respective phases (6, 7, 7).

</deferred>

---

*Phase: 2-Manual SDK Bootstrap & First Traces*
*Context gathered: 2026-05-01*
