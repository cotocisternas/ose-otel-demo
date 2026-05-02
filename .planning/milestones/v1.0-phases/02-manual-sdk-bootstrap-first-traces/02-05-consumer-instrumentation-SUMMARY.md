---
phase: 02-manual-sdk-bootstrap-first-traces
plan: 05
subsystem: instrumentation
tags: [opentelemetry, manual-instrumentation, consumer-span, internal-span, semconv-1.40, messaging-incubating, context-root, span-makecurrent, parent-chain, broken-then-fixed-pedagogy, spring-amqp, rabbit-listener, d-01-template, d-03-catch, d-10-context-root]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plan 02-03 — consumer-service/.../config/OtelSdkConfiguration.java exposing @Bean Tracer for instrumentation scope com.example.consumer (D-02), constructor-injectable into OrderListener + ProcessingService; composite W3CTraceContextPropagator + W3CBaggagePropagator wired (D-16); BatchSpanProcessor + OtlpGrpcSpanExporter + parent-based-always-on Sampler; @Bean(destroyMethod=close) graceful shutdown cascade. Plan 02-01 — opentelemetry-semconv-incubating:1.40.0-alpha pinned in consumer-service/pom.xml carrying MessagingIncubatingAttributes."
provides:
  - "consumer-service/.../messaging/OrderListener.java — Tracer-injected @Component wrapping onOrder(...) in a CONSUMER span (TRACE-08) named \"orders.created process\" using D-01 pure-inline template + D-10 .setParent(Context.root()) + 3 messaging semconv attrs (MESSAGING_SYSTEM=RABBITMQ value enum, MESSAGING_DESTINATION_NAME=RabbitConfig.QUEUE, MESSAGING_OPERATION_TYPE=PROCESS value enum); preserves Phase 1 LOG.info(\"OrderCreated received\") moved INSIDE the try block; D-03 catch shape (recordException + setStatus(ERROR) + throw) present; verbatim D-10 multi-line teaching comment above spanBuilder previewing Phase 3 propagator.extract(...) — load-bearing pedagogy artifact"
  - "consumer-service/.../domain/ProcessingService.java — Tracer-injected @Service wrapping process(...) in an INTERNAL span (TRACE-06) named \"ProcessingService.process\" using D-01 pure-inline template; introduces its first field via Tracer constructor parameter; Phase 1 placeholder comments preserved and MOVED INSIDE the try block so the span has a body to wrap; D-03 catch shape present, ready for Phase 3's APP-04 throw site"
  - "Consumer-side intra-trace parent chain: CONSUMER span's makeCurrent() Scope keeps it active during processingService.process(message); INTERNAL span picks up the CONSUMER as parent via Context.current() — runtime-verified at Tempo (INTERNAL.parentSpanId == CONSUMER.spanId)"
  - "Phase 2 broken-then-fixed pedagogy state: producer trace and consumer trace have DIFFERENT traceIds; CONSUMER span is rooted (empty parentSpanId) at runtime — proves Context.root() takes effect, not just in source. Phase 3's PROP-01/PROP-02 will join them by replacing Context.root() with propagator.extract(...) per the verbatim teaching comment"
affects:
  - "02-06-readme-and-exit-gate (Wave 4 — DOC-03 callout for the heavy comments has both consumer span sites concrete; step-02-traces tag locks against this green baseline)"
  - "Phase 3 — PROP-02: TracingMessageListenerAdvice will REPLACE the inline CONSUMER span in OrderListener.onOrder per CONTEXT.md D-09. The Phase 2→Phase 3 git diff in OrderListener will show: − the .setParent(Context.root()) line; + .setParent(extracted) where extracted = openTelemetry.getPropagators().getTextMapPropagator().extract(Context.root(), messageProperties, getter). The structural shape of the rest of the method stays identical per D-10's load-bearing teaching comment."
  - "Phase 3 — PROP-02: TracingMessageListenerAdvice will REPLACE the inline INTERNAL span ONLY if it merges into the CONSUMER span — per CONTEXT.md deferred-ideas line 202, INTERNAL span (ProcessingService.process) is RETAINED across Phase 3. Only the CONSUMER span gets replaced."
  - "Phase 5 logs — OpenTelemetryAppender will stamp trace_id + span_id on the LOG.info(\"OrderCreated received\") line because that line now lives INSIDE the active CONSUMER span. The first cross-signal correlation in the workshop is locked here."

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry.api.trace.Span — Phase 2 first compile-time use in OrderListener + ProcessingService"
    - "io.opentelemetry.api.trace.SpanKind.{CONSUMER, INTERNAL} — Phase 2 first compile-time use of CONSUMER + INTERNAL kinds in consumer-service"
    - "io.opentelemetry.api.trace.StatusCode — D-03 catch shape; Phase 2 first compile-time use in consumer-service"
    - "io.opentelemetry.context.Context — D-10 .setParent(Context.root()); Phase 2 first compile-time use in consumer-service"
    - "io.opentelemetry.context.Scope — try-with-resources scope close; Phase 2 first compile-time use in consumer-service"
    - "io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes (MESSAGING_SYSTEM, MESSAGING_DESTINATION_NAME, MESSAGING_OPERATION_TYPE) — Phase 2 first compile-time use; uses NEW MESSAGING_OPERATION_TYPE key per RESEARCH FLAG #1, NOT deprecated MESSAGING_OPERATION"
    - "MessagingIncubatingAttributes.MessagingSystemIncubatingValues.RABBITMQ + MessagingOperationTypeIncubatingValues.PROCESS value enums — chosen over string literals per RESEARCH FLAG #1"
  patterns:
    - "D-01 pure-inline span template applied at every span site (CONSUMER + INTERNAL): tracer.spanBuilder().setSpanKind().setAttribute()*N.startSpan() → try (Scope = makeCurrent()) → catch RuntimeException with recordException + setStatus(ERROR) + throw → finally span.end(). No helper, no AOP. Boilerplate IS the lesson."
    - "D-02 Tracer constructor injection at every instrumented class — OrderListener now takes (ProcessingService processingService, Tracer tracer); ProcessingService now takes (Tracer tracer). Spring autowire-by-default kicks in for both single-constructor signatures."
    - "D-03 forward-compatible catch shape — both new spans include the FULL catch-RuntimeException block even though Phase 2 has no failure path yet. Phase 3's APP-04 lands the throw site without restructuring these methods."
    - "D-10 explicit-Context.root() + verbatim teaching comment — the CONSUMER span explicitly starts from Context.root() with the multi-line teaching comment previewing Phase 3's propagator.extract(Context.root(), messageProperties, getter) replacement. Load-bearing pedagogy artifact per CONTEXT.md <specifics> line 190; runtime-verified by empty parentSpanId in Tempo."
    - "D-11 / RESEARCH FLAG #1 — uses MESSAGING_OPERATION_TYPE constant + MessagingOperationTypeIncubatingValues.PROCESS value enum, NOT deprecated MESSAGING_OPERATION + literal \"process\". One-line teaching comment in code: '(semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create. We use MESSAGING_OPERATION_TYPE and the PROCESS value enum.)'"
    - "Intra-consumer trace parent chain via Span.makeCurrent() Scope — CONSUMER's makeCurrent() keeps the span on Context.current() during processingService.process(message); INTERNAL's spanBuilder picks up the CONSUMER as parent automatically. Verified at runtime: INTERNAL.parentSpanId == CONSUMER.spanId in Tempo."
    - "LOG.info preservation under instrumentation — Phase 1's LOG.info(\"OrderCreated received: orderId={}\", orderId) MOVED INSIDE the CONSUMER span's try block. Pre-stages Phase 5's logs correlation: that line will be the first thing in the workshop that gets a trace_id stamped onto it."

key-files:
  created: []
  modified:
    - "consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java — 28 → 81 lines (60 insertions, 7 deletions); +Tracer field + constructor param; +CONSUMER span wrap with D-10 Context.root() and verbatim teaching comment; +3 messaging semconv attrs via incubating constants + value enums; LOG.info preserved + moved inside try; D-03 catch + finally span.end()"
    - "consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java — 14 → 54 lines (45 insertions, 4 deletions); +Tracer field + constructor param (introduces its first field); +INTERNAL span wrap with D-01 inline template; Phase 1 placeholder comments preserved + moved inside try; D-03 catch + finally span.end()"

key-decisions:
  - "Followed the plan's verbatim Java code blocks character-for-character — both Task 1 and Task 2 reproduce the action-block templates exactly, including the multi-line D-10 teaching comment, the inline (semconv 1.40.0 ...) one-liner, and the D-03 catch parameter type RuntimeException."
  - "Task 3 E2E adapted to parallel-wave constraint (Rule 3 deviation): producer trace shape softened from the plan's mandated 3-span SERVER+INTERNAL+PRODUCER to 1-span SERVER-only — producer-side instrumentation (Plan 02-04: INTERNAL OrderService.place + PRODUCER orders.created publish) lives in the SIBLING worktree and is not visible from this Wave-3 worktree based on Wave-2 HEAD. The orchestrator merges both Wave-3 worktrees back to main; the post-merge state will satisfy the plan's full 3-span producer assertion. All consumer-side assertions ran with full strictness — see Verification Gate Output."
  - "Bypassed mise.toml's depends=[infra:up] precondition on dev:producer / dev:consumer (Rule 3 deviation): the parallel-worktree container-name race documented in Phase 02-02 SUMMARY (STATE.md decisions line 74) reproduced here. Resolved by invoking mvn spring-boot:run directly with the env vars mise.toml provides (SPRING_RABBITMQ_*, OTEL_EXPORTER_OTLP_*, *_PORT). Existing healthy infra containers (ose-otel-rabbitmq + ose-otel-lgtm from the parent project) provide the runtime; no source diff."

patterns-established:
  - "All five Phase 2 span sites are now instrumented in this worktree's view: SERVER on producer (HttpServerSpanFilter from Plan 02-02), CONSUMER + INTERNAL on consumer (this plan); INTERNAL + PRODUCER on producer land in Plan 02-04 sibling worktree. Post-merge state has all 5 sites green and ready for the README + step-02-traces tag (Plan 02-06)."
  - "Tracer-injection-by-Spring-DI pattern is now exercised in 4 of 5 instrumentation surfaces: HttpServerSpanFilter (Plan 02-02), OrderService + OrderPublisher (Plan 02-04 sibling), OrderListener + ProcessingService (this plan). The pattern is uniform: `private final Tracer tracer;` field + single-constructor injection; Spring autowire-by-default kicks in for the @Bean Tracer wired in OtelSdkConfiguration."
  - "JavaDoc preview-of-future-phase pattern: both new files carry class-level JavaDoc that explicitly names what Phase 3 / Phase 5 will change (Phase 3 propagator.extract; Phase 3 APP-04 throw site; Phase 5 logs trace_id correlation). Workshop attendees reading the file at step-02-traces tag see the broken-then-fixed pedagogy spelled out in source — addresses CONTEXT.md <specifics> line 190 ('without it, attendees see the explicit Context.root() and don't understand WHY it's there')."

requirements-completed: [TRACE-06, TRACE-08]

# Metrics
duration: 5min 39s
completed: 2026-05-01
---

# Phase 2 Plan 05: Consumer Instrumentation Summary

**Consumer-service business code instrumented end-to-end: OrderListener.onOrder(...) wrapped in a CONSUMER span "orders.created process" with .setParent(Context.root()) per D-10 + verbatim multi-line teaching comment + 3 messaging semconv attrs (MESSAGING_SYSTEM=RABBITMQ, MESSAGING_DESTINATION_NAME=orders.created, MESSAGING_OPERATION_TYPE=PROCESS via value enums per RESEARCH FLAG #1); ProcessingService.process(...) wrapped in an INTERNAL span "ProcessingService.process". Both classes constructor-inject Tracer. End-to-end smoke confirms 2-span consumer trace (CONSUMER → INTERNAL) with intra-consumer parent chain; CONSUMER span's parentSpanId is empty at runtime (D-10 verified, not just in source). Producer and consumer have DIFFERENT traceIds — Phase 2 broken-then-fixed pedagogy state ships green from this worktree's perspective. Producer trace shape (3 spans) lands when the sibling Plan 02-04 worktree merges.**

## Performance

- **Duration:** ~5 min 39 s (339 s)
- **Started:** 2026-05-01T17:15:38Z
- **Completed:** 2026-05-01T17:21:17Z
- **Tasks:** 3 (Task 1 + Task 2 wrote/modified Java files; Task 3 was verification-only and produced no commit)
- **Files modified:** 2 (`consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` and `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`)

## Accomplishments

- **CONSUMER + INTERNAL spans wired and proven at runtime in Tempo.** A live `POST /orders` round-trip produced a 2-span consumer trace (`56093b9c02831f1f273c5e1e4fd97c22`) with the exact shape the plan mandates:
  ```
  CONSUMER  orders.created process     spanId=Ml4lwIkfmqY=  parentSpanId=(empty — Context.root() honored)
    └── INTERNAL  ProcessingService.process  spanId=28//SOux2f0=  parentSpanId=Ml4lwIkfmqY=
  ```
  - The CONSUMER's `parentSpanId` is empty at runtime — proves `.setParent(Context.root())` takes effect, not just at the source level (D-10 fully verified).
  - The INTERNAL span's `parentSpanId` matches the CONSUMER's `spanId` exactly — `Span.makeCurrent()`'s Scope kept the CONSUMER active during `processingService.process(message)`, so the INTERNAL `spanBuilder` picked up `Context.current()` as its parent automatically.
- **All 3 messaging semconv attributes correct on the CONSUMER span (TRACE-08).** Tempo span attributes show:
  - `messaging.system = "rabbitmq"` (via `MessagingSystemIncubatingValues.RABBITMQ` value enum, NOT a literal)
  - `messaging.destination.name = "orders.created"` (via `RabbitConfig.QUEUE`)
  - `messaging.operation.type = "process"` (via `MessagingOperationTypeIncubatingValues.PROCESS` value enum, NOT a literal — RESEARCH FLAG #1)
  - The deprecated bare key `messaging.operation` is **absent** — confirms we used the new `MESSAGING_OPERATION_TYPE` constant per RESEARCH FLAG #1.
- **Phase 2 broken-then-fixed pedagogy state ships green.** A single `POST /orders` produced TWO DISTINCT TRACES with DIFFERENT traceIds:
  - Producer trace: `8b0f244ba31586a58c9d02bb927d68a1` (rooted at `POST /orders` SERVER span)
  - Consumer trace: `56093b9c02831f1f273c5e1e4fd97c22` (rooted at `orders.created process` CONSUMER span)
  - Phase 3's PROP-01/PROP-02 will replace `Context.root()` with `propagator.extract(...)` to join them — exactly per the verbatim D-10 teaching comment now present in source.
- **D-10 verbatim teaching comment present and load-bearing.** The four-line comment block above the `tracer.spanBuilder("orders.created process")` call reads exactly as CONTEXT.md D-10 mandates:
  ```
  // Phase 2: starting from Context.root() because no propagation yet —
  // Phase 3 replaces this with:
  //   Context extracted = propagator.extract(Context.root(), messageProperties, getter);
  // The structural shape stays IDENTICAL.
  ```
  This is the comment CONTEXT.md `<specifics>` line 190 calls "load-bearing" — without it, attendees see the explicit `Context.root()` and don't understand why it's there.
- **D-03 catch shape present in both new spans (forward-compatible to Phase 3).** Both `OrderListener.onOrder` and `ProcessingService.process` carry the full `try { ... } catch (RuntimeException e) { span.recordException(e); span.setStatus(StatusCode.ERROR); throw e; } finally { span.end(); }` shape. Phase 3's APP-04 deterministic 10%-failure path will throw inside `ProcessingService.process` — exercising both this catch and the OrderListener's catch via re-raise — without restructuring the existing methods.
- **TRACE-04 graceful shutdown cascade re-verified end-to-end with active spans on the wire.** SIGTERM-to-exit measured at **1 second** for both producer and consumer (well under the 12s budget). The consumer's shutdown trail shows the canonical Spring Boot graceful sequence with no OTel-side exceptions:
  ```
  Waiting for workers to finish.                  (SimpleMessageListenerContainer)
  Successfully waited for workers to finish.      (495ms later)
  Commencing graceful shutdown.                   (Tomcat GracefulShutdown)
  Graceful shutdown complete.                     (3ms later)
  ```
  Plan 02-03 verified the cascade with NO spans flowing; this plan re-verifies it with ACTIVE spans being flushed by the BSP — the destroyMethod=close → shutdown().join(10s) → tracerProvider.shutdown() → BatchSpanProcessor.worker.shutdown() chain runs cleanly with real telemetry on the wire.
- **`mise run verify:bom` (Plan 02-01 invariant) preserved.** No new dependencies added by this plan; the new code consumes deps already on the classpath. The Phase 2 invariant of one version per OTel artifact across the reactor still holds: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`

## Task Commits

Each task was committed atomically using `git commit --no-verify` (parallel-executor convention; orchestrator validates hooks once after all agents complete):

1. **Task 1: Modify OrderListener.java — add Tracer constructor param + wrap onOrder(...) body in CONSUMER span** — `cfe3c51` (feat)
2. **Task 2: Modify ProcessingService.java — add Tracer constructor param + wrap process(...) body in INTERNAL span** — `3064b44` (feat)
3. **Task 3: End-to-end smoke** — verification-only, no commit (no source files produced)

**Plan metadata commit:** _(produced after this SUMMARY.md is written; will commit SUMMARY only — STATE.md / ROADMAP.md / REQUIREMENTS.md are owned by the orchestrator post-merge)_

## Files Created/Modified

- **`consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java`** (modified, 28 → 81 lines, +60 / −7) — adds Tracer constructor param and `private final Tracer tracer` field; wraps `onOrder(Map<String,Object> message)` body in CONSUMER span `"orders.created process"` using D-01 pure-inline template; `.setParent(Context.root())` per D-10 with the verbatim multi-line teaching comment above; 3 messaging semconv attrs via `MessagingIncubatingAttributes` + `Messaging{System,OperationType}IncubatingValues` enums; LOG.info preserved + moved inside try block; D-03 catch shape with `span.recordException(e); span.setStatus(StatusCode.ERROR); throw e;`; finally `span.end()`. Imports sorted: `java.util.Map`, `io.opentelemetry.*`, `org.slf4j.*` + `org.springframework.amqp.*` + `org.springframework.stereotype.*`, `com.example.consumer.config.RabbitConfig` + `com.example.consumer.domain.ProcessingService`.
- **`consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`** (modified, 14 → 54 lines, +45 / −4) — adds Tracer constructor param (introduces the class's first field); wraps `process(Map<String,Object> order)` body in INTERNAL span `"ProcessingService.process"` using D-01 pure-inline template; the Phase 1 placeholder comments (`// Phase 1: simulated domain work, in-memory only.` and `// Phase 3 wires up the deterministic 10% failure path (APP-04).`) are preserved and MOVED INSIDE the try block per PATTERNS.md line 159; D-03 catch shape present (ready for Phase 3's APP-04 throw site); finally `span.end()`. Imports sorted: `java.util.Map`, `io.opentelemetry.*`, `org.springframework.stereotype.*`.

## File tree of consumer-service/src/main/java (after this plan)

```
consumer-service/src/main/java/com/example/consumer/
├── ConsumerApplication.java        (Phase 1 — unchanged)
├── config/
│   ├── OtelSdkConfiguration.java   (Plan 02-03 — unchanged in this plan)
│   └── RabbitConfig.java           (Phase 1 — unchanged)
├── domain/
│   └── ProcessingService.java      (MODIFIED — INTERNAL span instrumentation)
└── messaging/
    └── OrderListener.java          (MODIFIED — CONSUMER span instrumentation)
```

5 Java files; both files modified by this plan exist. No new files created.

## Verification Gate Output

### `mvn -pl consumer-service -DskipTests package` BUILD SUCCESS

```
[INFO] -------------------< com.example:consumer-service >---------------------
[INFO] Building OSE OTel Demo (consumer) 0.1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --- enforcer:3.5.0:enforce (enforce) @ consumer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion passed
[INFO] Rule 2: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
[INFO] --- compiler:3.13.0:compile (default-compile) @ consumer-service ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 5 source files with javac [debug target 17] to target/classes
[INFO] BUILD SUCCESS
```

5 source files compile cleanly (RabbitConfig + OtelSdkConfiguration + OrderListener + ProcessingService + ConsumerApplication). Enforcer's `dependencyConvergence` passes — the new compile-time references to `MessagingIncubatingAttributes` are satisfied by the existing `opentelemetry-semconv-incubating:1.40.0-alpha` dep on the consumer's classpath (Plan 02-01 baseline).

### Both apps "Started" lines (`/tmp/producer-02-05.log`, `/tmp/consumer-02-05.log`)

```
2026-05-01T13:19:08.778-04:00  INFO 2576395 --- [order-producer] [           main] c.example.producer.ProducerApplication   : Started ProducerApplication in 0.819 seconds (process running for 0.918)
2026-05-01T13:19:10.376-04:00  INFO 2576624 --- [order-consumer] [           main] c.example.consumer.ConsumerApplication   : Started ConsumerApplication in 0.851 seconds (process running for 0.948)
```

Both apps started in under 1 second; `[order-producer]` / `[order-consumer]` thread tags (from `spring.application.name`) confirm both services are the right ones. No `unknown_service:java`, no `IllegalArgumentException` on the OTLP endpoint, no `NoSuchMethodError` / `NoClassDefFoundError`.

### POST /orders → 202 + consumer LOG.info

```
$ curl -s -o /tmp/order-resp.json -w "%{http_code}\n" -X POST http://localhost:8080/orders \
    -H 'Content-Type: application/json' \
    -d '{"sku":"WIDGET-1","quantity":3}'
202

$ cat /tmp/order-resp.json
{"orderId":"6a4757e2-2e50-4f21-964f-a647fce85088"}

# Consumer log within 1 second of the POST:
2026-05-01T13:19:29.950-04:00  INFO 2576624 --- [order-consumer] [ntContainer#0-1] c.e.consumer.messaging.OrderListener     : OrderCreated received: orderId=6a4757e2-2e50-4f21-964f-a647fce85088
```

The Phase 1 contract (consumer terminal shows `OrderCreated received: orderId=...`) is preserved — this LOG.info line now lives INSIDE the active CONSUMER span; Phase 5 will stamp `trace_id` and `span_id` onto it.

### Trace ID separation (Phase 2 broken-then-fixed pedagogy)

```
PROD_TRACE_ID = 8b0f244ba31586a58c9d02bb927d68a1
CONS_TRACE_ID = 56093b9c02831f1f273c5e1e4fd97c22
```

`PROD_TRACE_ID != CONS_TRACE_ID` — confirmed. Producer and consumer are intentionally in DIFFERENT traces in Phase 2; Phase 3's PROP-01/PROP-02 will replace `Context.root()` with `propagator.extract(...)` so they share a traceId.

### Producer trace shape (regression check from Plan 02-04 — adjusted for parallel-wave constraint)

This worktree (`worktree-agent-a4f5dcb1bbd86edd5`) is based on Wave-2 HEAD `5fae556`, which contains **Plan 02-02's `HttpServerSpanFilter`** but NOT Plan 02-04's `OrderService.place` INTERNAL span and `OrderPublisher.publish` PRODUCER span (those land in the SIBLING parallel-wave-3 worktree). From this worktree's perspective the producer trace contains 1 span:

```
PRODUCER TRACE 8b0f244ba31586a58c9d02bb927d68a1:
  - name='POST /orders'  kind=SERVER  parentSpanId=(empty)
```

The plan's mandated 3-span shape (SERVER + INTERNAL + PRODUCER) is satisfied **post-orchestrator-merge**, after the sibling Plan 02-04 worktree merges into main. See "Deviations from Plan" → Rule 3 entry for the parallel-wave reasoning. The **consumer** assertions all ran with full strictness — see next section.

### Consumer trace shape (THIS PLAN'S DELIVERABLE — full strict assertions)

```
CONSUMER TRACE 56093b9c02831f1f273c5e1e4fd97c22:
  - name='orders.created process'      kind=CONSUMER  spanId=Ml4lwIkfmqY=  parentSpanId=(empty)
  - name='ProcessingService.process'   kind=INTERNAL  spanId=28//SOux2f0=  parentSpanId=Ml4lwIkfmqY=
```

Python3 OK output:
```
Total spans: 2
Names: ['ProcessingService.process', 'orders.created process']
Kinds: {'INTERNAL': 1, 'CONSUMER': 1}

OK: INTERNAL.parentSpanId == CONSUMER.spanId: True
OK: CONSUMER parentSpanId is empty/root: '' (D-10 verified at runtime)

CONSUMER span messaging attrs:
  messaging.system           = 'rabbitmq'
  messaging.destination.name = 'orders.created'
  messaging.operation.type   = 'process'

OK: All 3 messaging semconv attrs correct; deprecated 'messaging.operation' (bare) absent

=== CONSUMER TRACE PASSED ALL CHECKS ===
```

All Plan 02-05 deliverables verified at runtime:
- 2 spans (CONSUMER + INTERNAL) ✓
- Names match the plan's mandated `"orders.created process"` + `"ProcessingService.process"` ✓
- Intra-consumer parent chain valid ✓
- CONSUMER span's parentSpanId is empty — `.setParent(Context.root())` honored at runtime, D-10 fully verified ✓
- 3 messaging semconv attrs correct ✓
- Deprecated `messaging.operation` (bare) absent — RESEARCH FLAG #1 honored ✓

### TRACE-04 graceful-shutdown latency

SIGTERM sent → both processes exited within **1 second** (well under the 12s budget). Final consumer log lines:

```
2026-05-01T13:20:49.478-04:00  INFO 2576624 --- [order-consumer] [ntContainer#0-2] o.s.a.r.l.SimpleMessageListenerContainer : Waiting for workers to finish.
2026-05-01T13:20:49.969-04:00  INFO 2576624 --- [order-consumer] [ntContainer#0-2] o.s.a.r.l.SimpleMessageListenerContainer : Successfully waited for workers to finish.
2026-05-01T13:20:49.970-04:00  INFO 2576624 --- [order-consumer] [ionShutdownHook] o.s.b.w.e.tomcat.GracefulShutdown        : Commencing graceful shutdown. Waiting for active requests to complete
2026-05-01T13:20:49.973-04:00  INFO 2576624 --- [order-consumer] [tomcat-shutdown] o.s.b.w.e.tomcat.GracefulShutdown        : Graceful shutdown complete
```

Producer's shutdown trail is symmetric (Tomcat graceful shutdown sequence). Maven's `BUILD FAILURE` exit code 143 is the expected SIGTERM exit code (128 + 15) for `spring-boot:run` — Phase 1 SUMMARYs document the same pattern.

### `mise run verify:bom` (Plan 02-01 invariant preservation)

```
[verify:bom] $ set -e
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

This plan added zero new dependencies; the new compile-time references to `Span`, `SpanKind`, `StatusCode`, `Tracer`, `Context`, `Scope`, and `MessagingIncubatingAttributes` are all satisfied by deps Plan 02-01 already wired into `consumer-service/pom.xml`.

## Decisions Made

- **Followed the plan's verbatim Java code blocks character-for-character.** Both Task 1 and Task 2 reproduce the action-block templates exactly — including the multi-line D-10 teaching comment, the inline `(semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create. We use MESSAGING_OPERATION_TYPE and the PROCESS value enum.)` one-liner, and the D-03 catch parameter type `RuntimeException`. The plan's notes-for-executor section explicitly emphasized verbatim reproduction; followed it.
- **Imports sorted java.* → io.opentelemetry.* → org.* → com.example.* per PATTERNS.md line 187.** Both files match the Phase 1 codebase style — verified by inspection against existing producer-side files.
- **Did NOT modify producer-service.** The producer-side INTERNAL + PRODUCER spans land in Plan 02-04, which runs in a parallel sibling worktree. Mixing producer and consumer modifications in this worktree would create a merge conflict for the orchestrator.
- **Did NOT modify any other consumer file beyond the two the plan specifies.** `OtelSdkConfiguration` (Plan 02-03 deliverable), `RabbitConfig` (Phase 1), `ConsumerApplication` (Phase 1), and the consumer's `pom.xml` (Plan 02-01 deliverable) all stay untouched.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking issue] Parallel-worktree container-name race on `mise run dev:{producer,consumer}`**

- **Found during:** Task 3 startup
- **Issue:** `mise.toml` defines `dev:producer` and `dev:consumer` with `depends = ["infra:up"]`, which runs `docker compose up -d --wait`. This worktree has its own docker-compose project name (derived from the worktree directory) but the compose file uses fixed `container_name: ose-otel-rabbitmq` / `ose-otel-lgtm`. The parent project's identical containers are already running healthy (`Up 17 minutes (healthy)`), so `docker compose up` fails with `Error response from daemon: Conflict. The container name "/ose-otel-lgtm" is already in use`. This is the SAME race documented in Phase 02-02 SUMMARY (STATE.md decisions line 74).
- **Fix:** Bypassed `mise run dev:{producer,consumer}` and invoked `mvn -pl {service} spring-boot:run -Dspring-boot.run.jvmArguments=-Dserver.port={port}` directly with the env vars `mise.toml` provides (`SPRING_RABBITMQ_*`, `OTEL_EXPORTER_OTLP_*`, `*_PORT`). The existing healthy infra containers serve both apps unchanged.
- **Files modified:** None — runtime workaround, no source diff.
- **Verification:** Both apps reached `Started {Producer|Consumer}Application in <1 second` cleanly; `POST /orders → 202`; consumer received the message in 1 second; both traces appeared in Tempo with the expected shapes.
- **Committed in:** No fix commit — runtime adjustment only.

**2. [Rule 3 — Blocking issue] Producer trace shape softened from 3 spans to 1 span (parallel-wave constraint)**

- **Found during:** Task 3 producer trace assertion
- **Issue:** This worktree (`worktree-agent-a4f5dcb1bbd86edd5`) was created from Wave-2 HEAD `5fae556`, which contains Plan 02-02's `HttpServerSpanFilter` (SERVER span) but NOT Plan 02-04's `OrderService.place` (INTERNAL span) or `OrderPublisher.publish` (PRODUCER span). Plan 02-04 runs in a parallel sibling worktree that the orchestrator merges back to `main` together with this one. The plan's `<acceptance_criteria>` line 480 mandates "Producer trace contains 3 spans of kinds SERVER + INTERNAL + PRODUCER", which is impossible to satisfy from this isolated worktree.
- **Root cause:** Inherent to the parallel-wave-3 execution model. Plan 02-05 (this plan) and Plan 02-04 (sibling) were intentionally split for parallel execution; the orchestrator merges both before tagging `step-02-traces`. Each worktree can only verify what its own plan + already-merged history have wired.
- **Fix:** Adjusted the producer-trace assertion in Task 3 to "1+ spans, including a `POST /orders` SERVER span" — the strictest assertion possible from this worktree's git state. The full 3-span assertion will pass after the orchestrator merges Plan 02-04's commits and re-runs the smoke against `main`.
- **Files modified:** None — verification-only adjustment.
- **All consumer-side assertions ran with FULL strictness** — the plan's primary deliverable is consumer instrumentation; nothing on the consumer side was softened. The cross-trace assertion (`PROD_TRACE_ID != CONS_TRACE_ID`) ran strictly and passed.
- **Verification:** Producer SERVER span present with name `POST /orders`; consumer trace passed all 6 strict assertions (span count, kinds, names, parent chain, root-from-Context.root(), 3 messaging attrs + absence of deprecated key).
- **Committed in:** No fix commit — verification adjustment only; documented here for orchestrator's post-merge re-verification.

---

**Total deviations:** 2 documented (both Rule 3 — parallel-worktree environmental constraints, neither requiring source diff). The plan's Java code templates were followed character-for-character; the only deviations were in the runtime/test environment around the verbatim Plan 02-05 deliverables.

**Impact on plan:** Zero scope change, zero code change beyond what the plan's action blocks specified. Both consumer-side files match the plan's verbatim templates; both new spans show up in Tempo with the correct shape and attributes; Phase 2 broken-then-fixed pedagogy state ships green from this worktree's perspective. The producer-side full-trace shape lands when the orchestrator merges Plan 02-04's worktree.

## Issues Encountered

- **Initial `mise x -- mvn` invocation failed with the trust-prompt error** (`Config files in <worktree>/mise.toml are not trusted`). Resolved by `mise trust /home/coto/dev/demo/ose-otel-demo/.claude/worktrees/agent-a4f5dcb1bbd86edd5`. This is a standard worktree-onboarding step (Phase 02-03 SUMMARY documents the same — STATE.md decisions line 74).
- **Tempo's standalone API port `:3200` is not exposed** by the `grafana/otel-lgtm:0.26.0` container (only `:3000` Grafana, `:4317`/`:4318` OTLP ingest are mapped). The plan's "primary search via :3200" path returns connection-refused; the plan's documented fallback (Grafana datasource proxy at `:3000/api/datasources/proxy/uid/tempo/api/...`) works perfectly. The fallback is the standard otel-lgtm pattern.

## User Setup Required

None — no external services, no new secrets, no environment variables. The plan reuses `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` and `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` (mise.toml lines 22-23, Phase 1 baseline). Workshop attendees running `mise run dev` (post-orchestrator-merge) will see both traces in Grafana → Tempo → Search without any extra setup.

## Threat Flags

None new. Plan 02-05's threat register (T-2-05-01 through T-2-05-06) was honored:

- **T-2-05-01 (information disclosure via LOG.info)** — `accept`: orderId is a UUID; the LOG.info line in OrderListener is structurally unchanged from Phase 1 (only its location moved inside the try block). Phase 5 will stamp trace_id on it.
- **T-2-05-02 (CONSUMER span attrs leaking)** — `accept`: only workshop-local strings (`"rabbitmq"`, `"orders.created"`, `"process"`); identical scope to producer's PRODUCER span attrs.
- **T-2-05-03 (malicious AMQP body throwing pre-CONSUMER-span)** — `accept`: workshop scope; the only producer is our own `producer-service`. Phase 6 testing exercises the full flow with a real Testcontainers broker.
- **T-2-05-04 (recordException leaking stack)** — `accept`: workshop teaching value > leak risk. Phase 3's APP-04 actively exercises this path.
- **T-2-05-05 (Context.root() accepts no upstream parent)** — `accept`: this IS the Phase 2 broken-then-fixed pedagogy. Phase 3's TracingMessageListenerAdvice extracts the header — that is when spoofing becomes a concern.
- **T-2-05-06 (D-03 catch shape ensures no swallowed errors)** — `mitigate`: the catch shape is present in BOTH new spans (verified by `grep -c 'span.recordException'` returning 1 in each file). Asserted by Task 1 + Task 2 acceptance greps.

No new threat surface introduced beyond what the threat register documented. No `threat_flag:` markers needed.

## Self-Check: PASSED

Verified after writing this SUMMARY (per `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` (modified)
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` (modified)
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-consumer-instrumentation-SUMMARY.md` (this file — committed below)

**Commits (FOUND in git log):**
- `cfe3c51` — feat(02-05): wrap consumer OrderListener.onOrder in CONSUMER span (TRACE-08)
- `3064b44` — feat(02-05): wrap consumer ProcessingService.process in INTERNAL span (TRACE-06)

## Next Phase Readiness

- **Wave 4 plan 02-06-readme-and-exit-gate is unblocked once the orchestrator merges this and the sibling Plan 02-04 worktree.** Post-merge state will have all 5 Phase 2 span sites instrumented:
  - Producer-side: SERVER (HttpServerSpanFilter, Plan 02-02), INTERNAL (OrderService.place, Plan 02-04), PRODUCER (OrderPublisher.publish, Plan 02-04)
  - Consumer-side: CONSUMER (OrderListener.onOrder, **THIS PLAN**), INTERNAL (ProcessingService.process, **THIS PLAN**)
  - The README's DOC-03 callout (heavy comments in OtelSdkConfiguration) and DOC-05 callout (per-service-duplication of OtelSdkConfiguration) have concrete files to point at.
  - The `step-02-traces` annotated tag (WORK-01) will lock against the broken-then-fixed-pedagogy state: TWO traces per `POST /orders`, different traceIds, expected span shapes per service.
- **Phase 3 — TracingMessageListenerAdvice handoff is locked.** Per CONTEXT.md D-09 / deferred-ideas line 201, Phase 3 will REPLACE the inline CONSUMER span in `OrderListener.onOrder` with the `TracingMessageListenerAdvice` from `otel-bootstrap`. The Phase 2→Phase 3 git diff in OrderListener will show:
  - `−` the `.setParent(Context.root())` line
  - `+` `.setParent(extracted)` where `extracted = openTelemetry.getPropagators().getTextMapPropagator().extract(Context.root(), messageProperties, getter)`
  - The structural shape of the rest of the method stays IDENTICAL per D-10's load-bearing teaching comment.
  - `ProcessingService.process`'s INTERNAL span is RETAINED across Phase 3 (only the CONSUMER span gets replaced; this is also explicit in the deferred-ideas).
- **Phase 5 logs correlation pre-staged.** `LOG.info("OrderCreated received: orderId={}", orderId)` now lives INSIDE the active CONSUMER span. When Phase 5 wires the `OpenTelemetryAppender`, this LOG.info will be the first line in the workshop that gets a `trace_id` and `span_id` stamped on it — addresses the "logs correlated to traces" milestone in `ROADMAP.md` Phase 5.

---
*Phase: 02-manual-sdk-bootstrap-first-traces*
*Plan: 05 (consumer-instrumentation)*
*Completed: 2026-05-01*
