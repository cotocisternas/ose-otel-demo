---
phase: 03-amqp-context-propagation
status: SHIPPED
shipped: 2026-05-01
tag: step-03-context-propagation
plans_completed: 5
requirements_satisfied: [PROP-01, PROP-02, PROP-03, PROP-04, APP-04, TRACE-09, WORK-01 (3/6)]
---

# Phase 3 — AMQP Context Propagation — PHASE SUMMARY

## Single gate sentence (verified at runtime)

POST `/orders` produces ONE distributed trace; `consumer.parentSpanId == producer.spanId`;
the 10th order produces an ERROR trace with `exception.type=com.example.consumer.domain.ProcessingFailedException`
on the CONSUMER span.

## What shipped

| Wave | Plan | What landed |
|------|------|-------------|
| 1 | 03-01 otel-bootstrap-amqp-classes | 4 propagation classes + 1 round-trip unit test in shared `otel-bootstrap` module; 3 new BOM-managed deps in module pom |
| 2 | 03-02 producer-wiring | `otel-bootstrap` dep + `TracingMessagePostProcessor` `@Bean` + explicit `RabbitTemplate` `@Bean` (registers post-processor via `setBeforePublishPostProcessors`); `OrderPublisher` thinned to 21 lines (Phase 2 inline span deleted, `Tracer` field removed) |
| 2 | 03-03 consumer-wiring | `otel-bootstrap` dep + `TracingMessageListenerAdvice` `@Bean` + Configurer-aided `SimpleRabbitListenerContainerFactory` `@Bean` with `setAdviceChain(...)` + `setDefaultRequeueRejected(false)` (D-13 APP-04 safety); `OrderListener` thinned to 54 lines |
| 3 | 03-04 app-04-failure-path | `ProcessingFailedException` (custom RuntimeException; D-12) + `AtomicInteger`-driven 10% failure modulus inside `ProcessingService.process` D-03 try-block — first error-span demo for the workshop |
| 4 | 03-05 readme-and-exit-gate | README PROP-04 callout (parallel to DOC-05); 4-line cause-unwrap fix in advice (PFE on CONSUMER span); annotated tag `step-03-context-propagation` |

## Total artifacts

- **6 new source files** (4 propagation classes + 1 unit test + 1 ProcessingFailedException)
- **9 modified source files** (producer-service: pom, RabbitConfig, OrderPublisher; consumer-service: pom, RabbitConfig, OrderListener, ProcessingService; otel-bootstrap: pom; README.md)
- **1 modified config** (mise.toml — node pin, out-of-scope chore)
- **1 git ref** (`refs/tags/step-03-context-propagation`)
- **20 commits** on main (feat/refactor/chore/docs/merge/fix)

Source-only diff vs Phase 2: `16 files / 828 insertions / 138 deletions`.

## Key design choices honored

- **Shared module for propagation pair (PROP-04).** The `TracingMessagePostProcessor` + `TracingMessageListenerAdvice` pair lives in `otel-bootstrap` so attendees read ONE inject method matched by ONE extract method. The README callout makes the asymmetry explicit: per-service code (SDK setup) is duplicated; cross-service code (messaging boundary) is shared.
- **Per-service Tracer scope (D-03).** Each service injects its own `Tracer` (`com.example.producer` / `com.example.consumer`); spans created from inside `otel-bootstrap` still appear under each service's instrumentation scope in Tempo.
- **Inject-only span lifetime on producer side (D-09).** The post-processor opens, injects, and immediately ends the PRODUCER span — matches OTel Kafka/JMS convention; no header-injection-after-span-end risk.
- **Exchange-based destination naming (D-07).** Phase 3 corrects Phase 2's queue-as-destination divergence: `messaging.destination.name=orders` (the exchange), not `orders.created` (the queue). Visible in Tempo across the `step-02-traces` → `step-03-context-propagation` tag delta.
- **Pedagogical pairing of APP-04 + TRACE-09.** The first time attendees see business logic failing IS the first time they see the OTel error-span pattern (`recordException` + `setStatus(StatusCode.ERROR)`).

## Surprises / lessons learned

1. **Spring AMQP wraps user exceptions before the advice catch fires.** `MessagingMessageListenerAdapter.invokeListenerMethod` wraps any user-thrown exception in `ListenerExecutionFailedException` BEFORE `MethodInterceptor.invoke`'s `catch (Throwable)` runs. Without unwrap, the CONSUMER span surfaced the wrapper FQCN instead of the business exception. The 4-line `getCause()` unwrap (Plan 03-05 fix) resolves it. Worth a Decision in PROJECT.md and a footnote in the Phase 5 logging plan (the wrapper's stack trace is what attendees would otherwise see in their MDC-correlated logs).

2. **`mvn -pl <svc> spring-boot:run` does NOT rebuild dependent modules.** When iterating on `otel-bootstrap` while consumer-service is running, you must `mvn install -pl otel-bootstrap` (or use `-am` reactor flag) before restarting the service — otherwise Spring Boot picks up the stale snapshot from `~/.m2/repository`. This bit us once during the Criterion 3 retest. Worth calling out in the Phase 6 testing-strategy plan (Testcontainers integration tests should always go through a full `mvn verify` to avoid this trap).

3. **`mise run dev:producer` + `mise run dev:consumer` race on the `depends=["infra:up"]` clause** when launched via `nohup &`. The second loser sees a "Container already in use" docker conflict. Workshop README Step "First run" uses `mise run dev` (parallel-orchestrated) which serializes the dependency, so attendees won't hit this — but anyone scripting against the demo should know.

4. **Tempo TraceQL query format change.** `?tags=status%3Derror` returns empty in this Tempo build; `?q={status=error}` (TraceQL) is the correct query syntax. Documented in the T2 evidence file for Phase 6 reuse.

5. **W3C Trace Context Level 2 trace-flags `03`.** The OTel Java SDK `1.61.0` writes the `random` bit (bit 1) in addition to the `sampled` bit (bit 0), producing trace-flags byte `03` rather than the `01` shown in older docs. Workshop attendees will see `…-03` in the RabbitMQ Mgmt UI.

## Phase readiness signals (forward-looking)

### Phase 4 (metrics)

- `SdkMeterProvider` can be added to each `OtelSdkConfiguration` independently of the propagation pair.
- Metrics will inherit the propagated trace context as exemplars (the `Span.current()` is in scope inside both the producer publish path and the consumer listener body — the advice opens its scope BEFORE `inv.proceed()`).
- No structural changes needed in `otel-bootstrap` for Phase 4.

### Phase 5 (logs / MDC)

- `LOG.info(...)` inside `OrderListener.onOrder` runs INSIDE the CONSUMER span scope (D-09 verified at runtime — RESEARCH FLAG #1 confirms Spring AMQP does NOT switch threads between advice and listener body).
- Phase 5's MDC injector (`opentelemetry-logback-mdc-1.0` or `<captureMdcAttributes>`) will pick up `trace_id` / `span_id` from `Span.current()` automatically — no further code change needed in `OrderListener`.

### Phase 6 (tests)

- Testcontainers `@SpringBootTest` can swap `InMemorySpanExporter` for `OtlpGrpcSpanExporter` (or assert in-memory) and verify:
  - `traceId(producer) == traceId(consumer)`
  - `consumer.parentSpanId == producer.spanId`
  - both spans carry `SpanKind.PRODUCER` / `SpanKind.CONSUMER` and the 4 messaging semconv attributes
  - `defaultRequeueRejected=false` drops poison messages (the 10th-order PFE)
  - the cause-unwrap surfaces PFE on the CONSUMER span (regression net for the Plan 03-05 fix)
- The propagation pair IS the test surface; Phase 6 only adds the test code.

### Phase 7 (polish / DOC-04)

- `step-02-traces` ↔ `step-03-context-propagation` is the workshop's most powerful pedagogical delta. DOC-04 in Phase 7 will pair Tempo screenshots from the two tags side-by-side: two disconnected traces (Phase 2) vs ONE joined trace (Phase 3). Both tags are immutable, so the screenshots will reproduce indefinitely.

## What's NOT in Phase 3 scope (out-of-scope confirmed)

- No `SdkMeterProvider` or metrics emission (Phase 4)
- No log signal / MDC bridge (Phase 5)
- No integration tests against the propagation pair (Phase 6)
- No DLX / dead-letter routing for poison messages (per PROJECT.md)
- No Phase 7 README rewrite (Phase 3 only adds the PROP-04 section)
