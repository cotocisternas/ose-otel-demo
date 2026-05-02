# Phase 2: Manual SDK Bootstrap & First Traces - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 2-Manual SDK Bootstrap & First Traces
**Areas discussed:** Span-wrapping idiom, SERVER-span hook point, PRODUCER/CONSUMER span home, SDK env-var resolution

---

## Span-wrapping idiom

### Q1: How should each span be wrapped in business code?

| Option | Description | Selected |
|--------|-------------|----------|
| Pure inline | `tracer.spanBuilder().setSpanKind().startSpan()` + `try (Scope)` + `try/catch/recordException/setStatus(ERROR)/rethrow` + `finally span.end()` at every call site. Maximum SDK surface visible. | ✓ |
| Tiny helper in otel-bootstrap | Extract a `Spans.run(tracer, name, kind, supplier)` helper in otel-bootstrap. Each call site is one line. Less repetition; helper itself is teaching material. | |
| Spring AOP @Around aspect | Annotate methods with `@Traced`; an `@Aspect` handles span lifecycle. Zero call-site boilerplate. Defeats DOC-03 'code is the textbook' goal. | |

**User's choice:** Pure inline (Recommended)
**Notes:** Workshop pedagogy explicit — boilerplate IS the lesson. Phases 3-5 will follow this same pattern; no later helper extraction allowed.

### Q2: How does each instrumented class get its `Tracer`?

| Option | Description | Selected |
|--------|-------------|----------|
| Constructor-inject Tracer | `OtelSdkConfiguration` exposes `@Bean Tracer tracer(OpenTelemetry o) { return o.getTracer("com.example.producer"); }`. Each instrumented class takes Tracer as a constructor param. Idiomatic Spring DI. | ✓ |
| Inject OpenTelemetry, call getTracer per use | Inject `OpenTelemetry`; each call writes `openTelemetry.getTracer("name").spanBuilder(...)`. More honest about the SDK shape. One more line per call site. | |
| Static GlobalOpenTelemetry.getTracer(...) | Static `private static final Tracer TRACER = GlobalOpenTelemetry.getTracer(...)`. No DI on call site. Hides the wiring; opposes Spring idioms. | |

**User's choice:** Constructor-inject Tracer (Recommended)
**Notes:** One Tracer per service. Instrumentation scope = service package (`com.example.producer` / `com.example.consumer`).

### Q3: Should Phase 2 spans already include the try/catch with recordException + setStatus(ERROR)?

| Option | Description | Selected |
|--------|-------------|----------|
| Include catch now (forward-compatible) | Phase 2 spans use the full try/catch/finally shape. Phase 3 ONLY adds the propagation pair plus APP-04 failure path; doesn't restructure existing methods. Keeps step-02→step-03 diff focused. | ✓ |
| Omit catch in Phase 2; add in Phase 3 with TRACE-09 | Phase 2 spans only have try/finally. Phase 3 adds catch + recordException + setStatus alongside APP-04 — catch arrives in the same commit as the first failure that exercises it. Stronger pairing; larger step-02→step-03 diff. | |

**User's choice:** Include catch now (forward-compatible) (Recommended)
**Notes:** Phase 3 SC #5 mandates the step-02→step-03 git diff be readable in one viewing — keeping the diff propagation-focused matters more than catch/failure pedagogical pairing.

### Q4: What naming convention do span names follow?

| Option | Description | Selected |
|--------|-------------|----------|
| Semconv where applicable, ClassName.method for INTERNAL | HTTP: `POST /orders`. AMQP: `orders.created publish` / `orders.created process`. INTERNAL: `OrderService.place`, `ProcessingService.process`. Aligns with Tempo's HTTP/messaging UI lenses. | ✓ |
| ClassName.method everywhere | All five spans named after their Java location. Uniform; misses semconv naming for HTTP and messaging. | |
| Verb-noun semantic everywhere | `accept order`, `place order`, `publish order`, etc. Domain-readable; loses code-locality. | |

**User's choice:** Semconv where applicable, ClassName.method for INTERNAL (Recommended)

---

## SERVER-span hook point

### Q1: Where should the SERVER span on POST /orders be created?

| Option | Description | Selected |
|--------|-------------|----------|
| OncePerRequestFilter @Bean | Servlet filter. Wraps every HTTP request. status_code naturally available on the way out. Generic transplantable pattern. | ✓ |
| HandlerInterceptor via WebMvcConfigurer | Spring MVC HandlerInterceptor with preHandle/afterCompletion. More 'Spring-y'; same generality. | |
| Inline in OrderController.create(...) | Span at the top of the controller body. Narrow scope; status_code is awkward. | |

**User's choice:** OncePerRequestFilter @Bean (Recommended)

### Q2: Should the SERVER-span filter trace /actuator/health requests?

| Option | Description | Selected |
|--------|-------------|----------|
| Exclude /actuator/* | Filter early-returns for actuator paths. Reason: docker-compose healthchecks would flood Tempo. Document as production-realistic tradeoff. | ✓ |
| Trace everything | All HTTP gets a SERVER span. Simplest filter; floods Tempo with health-check noise. | |
| Trace everything but mark actuator low-priority via attribute | All requests get a span; actuator gets `otel.demo.health=true`. Floods Tempo's index. | |

**User's choice:** Exclude /actuator/* (Recommended)

### Q3: Does the SERVER-span filter exist in BOTH services or producer-only?

| Option | Description | Selected |
|--------|-------------|----------|
| Producer-only | Only producer has business HTTP. Consumer's only HTTP is /actuator/health (excluded anyway). Per-service-duplication ethos applies to BOOTSTRAP, not instrumentation surfaces. | ✓ |
| Both services (symmetric for teaching) | Add same filter to consumer too (effectively no-op since actuator is excluded). Shows attendees the wiring works wherever there's an HTTP server. Dead code in consumer. | |

**User's choice:** Producer-only (Recommended)

### Q4: Continue or move on?

**User's choice:** Next area

---

## PRODUCER/CONSUMER span home

### Q1: Where do the PRODUCER and CONSUMER spans live in Phase 2?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline in publisher/listener; otel-bootstrap stays empty | PRODUCER span inline in OrderPublisher.publish; CONSUMER inline in OrderListener.onOrder. otel-bootstrap stays Phase 1 placeholder. Phase 3 ADDS new bootstrap classes and REMOVES inline spans. Diff: + 2 classes, + wiring, − inline spans. | ✓ |
| Move to otel-bootstrap as no-inject shells now | Phase 2 already creates TracingMessagePostProcessor / TracingMessageListenerAdvice in otel-bootstrap (with PRODUCER/CONSUMER spans but no inject/extract). Phase 3 fills bodies. Diff: ~3-5 line edit in two existing classes. | |
| Inline in publisher/listener; ADD propagation pair in Phase 3 (no removal) | Same as (a) for Phase 2. Phase 3 adds inject-only / extract-only classes alongside (not replacing) inline spans. Slight redundancy. | |

**User's choice:** Inline in publisher/listener; otel-bootstrap stays empty (Recommended)
**Notes:** Phase 3 SC #5 (small readable git diff) drives this. Phase 3 plan must explicitly delete Phase 2's inline spans when installing the bootstrap pair — captured as a deferred Phase 3 hand-off note.

### Q2: On the consumer side in Phase 2, how should the CONSUMER span set its parent context?

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit Context.root() with comment | `tracer.spanBuilder("orders.created process").setParent(Context.root()).setSpanKind(CONSUMER).startSpan()` + multi-line comment previewing Phase 3's propagator.extract idiom. | ✓ |
| Implicit (no setParent call) | Just `tracer.spanBuilder(...).startSpan()` — implicit Context.current() = root() on the listener thread. Less code; loses the teaching opportunity. | |

**User's choice:** Explicit Context.root() with comment (Recommended)
**Notes:** Makes the 'broken' state INTENTIONAL not accidental. Comment explicitly previews Phase 3's `propagator.extract(Context.root(), messageProperties, getter)` shape so attendees see WHERE the parent gap is.

### Q3: Should the PRODUCER span set messaging.rabbitmq.destination.routing_key?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, set it now | Add `.setAttribute("messaging.rabbitmq.destination.routing_key", RabbitConfig.ROUTING_KEY)`. One extra line; teaches AMQP-specific semconv. | ✓ |
| Just the TRACE-07 trio (system, destination.name, operation) | Stick to exactly what TRACE-07 mandates. Routing key omitted as a 'nice-to-have'. | |

**User's choice:** Yes, set it now (Recommended)

---

## SDK env-var resolution

### Q1: How does OtelSdkConfiguration resolve the OTLP endpoint?

| Option | Description | Selected |
|--------|-------------|----------|
| Manual System.getenv with documented defaults | `Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse("http://localhost:4317")`. NO sdk-extension-autoconfigure dependency. Pure pedagogy: env-var contract visible in code. | ✓ |
| AutoConfiguredOpenTelemetrySdk (sdk-extension-autoconfigure) | `AutoConfiguredOpenTelemetrySdk.builder().setResultAsGlobal(false).build().getOpenTelemetrySdk()`. Auto-reads OTEL_* env vars. Less code; production-realistic; hides what's read from where. | |
| Hardcode http://localhost:4317 | `.setEndpoint("http://localhost:4317")` — no env var read. Simplest; loses 'production knob' lesson. | |

**User's choice:** Manual System.getenv with documented defaults (Recommended)
**Notes:** Preview also implicitly locked the resource attribute values: service.namespace="ose-otel-demo", deployment.environment.name="workshop", service.instance.id=UUID.randomUUID(); plus BatchSpanProcessor with default tuning, explicit Sampler.parentBased(alwaysOn()) with multi-paragraph teaching comment, and composite W3CTraceContext + W3CBaggage propagators registered now even though Phase 3 will be the first to exercise them.

### Q2: Continue or wrap up?

**User's choice:** Wrap up — ready for context

---

## Final wrap-up

### Q: Any gray areas remain unclear, or ready for me to write CONTEXT.md?

**User's choice:** I'm ready for context

## Claude's Discretion

Items where the user delegated to Claude (captured in CONTEXT.md `<decisions>` → "Claude's Discretion" subsection):

- Exact set of HTTP semconv attributes on the SERVER span (TRACE-05 trio + url.scheme + server.address + server.port + http.route)
- Filter `@Order` (default Spring Boot ordering — no competing filters)
- `OtelSdkConfiguration` package path (`com.example.{producer|consumer}.config` alongside RabbitConfig)
- Service-name source (hardcode strings rather than `@Value("${spring.application.name}")` — duplication is the point)
- Whether to read `OTEL_EXPORTER_OTLP_PROTOCOL` (no — OtlpGrpcSpanExporter is explicit; document the intentional redundancy)
- Sampler teaching comment depth (multi-paragraph, 4-8 lines)
- README DOC-03 / DOC-05 wording (brief but explicit)
- Whether to add a `mise run verify:traces` task (planner can decide; or leave to Phase 6)
- Phase 2 ships ZERO test code (all tests deferred to Phase 6 per the roadmap)

## Deferred Ideas

Captured in CONTEXT.md `<deferred>` section. Highlights:

- **Phase 3 hand-off (load-bearing):** Phase 3 plan MUST explicitly delete Phase 2's inline PRODUCER/CONSUMER spans when installing the TracingMessagePostProcessor / TracingMessageListenerAdvice — otherwise the trace gets DOUBLE spans.
- **Phase 4/5 hand-off:** SdkMeterProvider / SdkLoggerProvider follow the same env-var-and-shutdown pattern.
- **Phase 7 hand-off:** DOC-04 (broken-vs-fixed Tempo screenshots) needs the Phase 2 two-disconnected-traces state captured at the `step-02-traces` tag before Phase 3 fixes it.
- No backlog-worthy items emerged; discussion stayed within Phase 2 scope.
