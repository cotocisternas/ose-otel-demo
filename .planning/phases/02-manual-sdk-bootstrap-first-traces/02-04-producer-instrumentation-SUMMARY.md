---
phase: 02-manual-sdk-bootstrap-first-traces
plan: 04
subsystem: observability
tags: [opentelemetry, manual-instrumentation, span-wrapping, internal-span, producer-span, semconv, semconv-incubating, messaging-rabbitmq, d-01-inline-template, d-02-constructor-injection, d-03-catch-shape, d-04-span-naming, d-11-routing-key-attr, research-flag-1, spring-boot-3.4, java-17]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plan 02-02: producer @Bean Tracer (instrumentation scope com.example.producer) wired in OtelSdkConfiguration; @Bean OpenTelemetry with destroyMethod=close for graceful BSP flush; HttpServerSpanFilter creates SERVER span for every non-/actuator request — establishes the parent context that OrderService.place inherits via Span.makeCurrent()."
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plan 02-01: producer-service/pom.xml carries opentelemetry-api/sdk/exporter-otlp (BOM-managed via opentelemetry-bom:1.61.0) plus opentelemetry-semconv:1.40.0 (stable) and opentelemetry-semconv-incubating:1.40.0-alpha (required for MessagingIncubatingAttributes — RESEARCH FLAG #2)."
  - phase: 01-baseline-scaffold
    provides: "producer-service/.../api/OrderController.java (POST /orders pass-through), .../domain/OrderService.java (UUID + publish), .../messaging/OrderPublisher.java (rabbitTemplate.convertAndSend), .../config/RabbitConfig.java (EXCHANGE/QUEUE/ROUTING_KEY constants used as semconv attribute values for symmetry with the actual destination)."

provides:
  - "Tracer-injected OrderService with INTERNAL span around place(...) using the EXACT D-01 pure-inline template (TRACE-06): tracer.spanBuilder(\"OrderService.place\").setSpanKind(SpanKind.INTERNAL).startSpan() / try (Scope = makeCurrent()) / UUID + publish + return all inside try / catch (RuntimeException) recordException + setStatus(ERROR) + throw / finally span.end(). Constructor signature changed: OrderService(OrderPublisher, Tracer)."
  - "Tracer-injected OrderPublisher with PRODUCER span around publish(...) using the EXACT D-01 pure-inline template (TRACE-07 + D-11): tracer.spanBuilder(\"orders.created publish\").setSpanKind(SpanKind.PRODUCER).setAttribute(MESSAGING_SYSTEM, RABBITMQ).setAttribute(MESSAGING_DESTINATION_NAME, RabbitConfig.QUEUE).setAttribute(MESSAGING_OPERATION_TYPE, SEND).setAttribute(MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, RabbitConfig.ROUTING_KEY).startSpan() — uses NEW semconv 1.40.0 keys + value enums (NOT deprecated MESSAGING_OPERATION/literal \"publish\" per RESEARCH FLAG #1). Constructor signature changed: OrderPublisher(RabbitTemplate, Tracer)."
  - "End-to-end producer trace contract: one POST /orders → 3 nested spans in Tempo (SERVER → INTERNAL → PRODUCER), all sharing one trace_id, parent-child chain valid via Span.makeCurrent() Scopes."
  - "Phase-3-hint comment in OrderPublisher.publish: \"Phase 2 does NOT inject the traceparent header here — that's Phase 3's headline lesson (PROP-01).\" Sets workshop expectations for the diff Phase 3 SC #5 mandates."
  - "Teaching comment in OrderPublisher about the semconv 1.40.0 messaging.operation → messaging.operation.type rename (RESEARCH FLAG #1) — workshop attendees encountering deprecated semconv constants in their own codebases benefit from seeing the explicit migration note."

affects:
  - "02-05-consumer-instrumentation (parallel sibling, Wave 3 — independent — modifies consumer-service classes only; this plan establishes the producer half of the 5-span Phase 2 round-trip but the two waves do not share files or run-time state)"
  - "02-06-readme-and-exit-gate (Wave 4 — asserts producer-side end-to-end shape SERVER → INTERNAL → PRODUCER; this SUMMARY confirms the contract and provides the Tempo trace fixture to reference in the README walkthrough)"
  - "Phase 3 (W3C context propagation across AMQP) — the inline PRODUCER span here will be REPLACED by TracingMessagePostProcessor (CONTEXT.md D-09); Phase 3's first task explicitly DELETES OrderPublisher's inline PRODUCER span to avoid double-spans. The INTERNAL span on OrderService.place stays unchanged through Phase 3 onward."
  - "Phase 4 (metrics) — INTERNAL span on OrderService.place gives Phase 4 a deterministic place to record METRIC-02 order.placed counter (single context, post-publish, inside the active span)"
  - "Phase 5 (logs correlation) — INTERNAL span keeps trace_id/span_id in MDC for any future log emissions inside OrderService.place"

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry.api.trace.Span — the span handle obtained from spanBuilder(...).startSpan(); makeCurrent() returns a Scope that activates the span on the current thread for nested SDK calls"
    - "io.opentelemetry.api.trace.SpanKind — INTERNAL (OrderService.place) and PRODUCER (OrderPublisher.publish); semconv-driven kinds the OTel spec defines for messaging vs. in-process work"
    - "io.opentelemetry.api.trace.StatusCode — StatusCode.ERROR set on span when catch block fires; preserves D-03 forward-compat shape even though Phase 2 has no failure path"
    - "io.opentelemetry.context.Scope — try-with-resources type returned by Span.makeCurrent(); auto-closed on try block exit, restoring the previous Context"
    - "io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes — MESSAGING_SYSTEM, MESSAGING_DESTINATION_NAME, MESSAGING_OPERATION_TYPE (NEW), MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY"
    - "io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues — RABBITMQ value enum (replaces the literal \"rabbitmq\")"
    - "io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues — SEND value enum for PRODUCER kind (replaces deprecated literal \"publish\" per RESEARCH FLAG #1)"
  patterns:
    - "D-01 pure-inline span template — applied inside a method body; structurally identical to the SERVER span site established in Plan 02-02. Two more applications follow (CONSUMER + INTERNAL) in Plan 02-05's consumer-side instrumentation. The 8-12 line idiom is REPEATED at every span site (no helper class, no AOP) so workshop attendees see the SDK calls in business code."
    - "D-02 constructor injection of Tracer — single-constructor classes get autowire-by-default in Spring 4.3+; no @Autowired needed. Tracer @Bean from OtelSdkConfiguration resolves transparently."
    - "D-03 forward-compatible catch shape — span.recordException(e) + span.setStatus(StatusCode.ERROR) + throw e + finally span.end(). Captures Phase 3+ failure paths (APP-04 deterministic 10% failure) without restructuring."
    - "D-04 span naming convention — INTERNAL spans use ClassName.method (\"OrderService.place\"); PRODUCER spans use \"<destination> <operation>\" with the QUEUE name (\"orders.created publish\"); the routing_key value (\"order.created\") goes on a separate attribute per D-11 (it is NOT part of the span name)."
    - "D-11 RabbitMQ-specific semconv attribute — MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY in addition to the generic 3 (system, destination, operation). Teaches AMQP-specific semconv beyond the messaging baseline."
    - "Active-Scope context threading — return statement INSIDE the try block keeps the makeCurrent() Scope active during publisher.publish(); the PRODUCER spanBuilder picks up the parent via Context.current() automatically. Misplacing the return AFTER the try block breaks the parent-child chain."

key-files:
  created: []
  modified:
    - "producer-service/src/main/java/com/example/producer/domain/OrderService.java (was 22 lines / Phase 1 baseline → 64 lines / +49 +0 / Tracer field + INTERNAL span wrapping place(...) using D-01 template)"
    - "producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (was 24 lines / Phase 1 baseline → 85 lines / +70 -9 / Tracer field + PRODUCER span wrapping publish(...) with 4 messaging semconv attrs using D-01 template)"

key-decisions:
  - "Used NEW semconv 1.40.0 key MESSAGING_OPERATION_TYPE + value enum MessagingOperationTypeIncubatingValues.SEND (NOT the deprecated MESSAGING_OPERATION + literal \"publish\") per RESEARCH FLAG #1. Verified at runtime: Tempo trace shows attribute key 'messaging.operation.type'=send and the bare 'messaging.operation' key is absent."
  - "Constructor parameter order: OrderService(OrderPublisher, Tracer) and OrderPublisher(RabbitTemplate, Tracer) — pre-existing dependency first, new Tracer last. Spring's auto-wiring is parameter-order-agnostic, but workshop diff readability favors appending the new dependency."
  - "OrderController is UNCHANGED (no Tracer parameter) — controller is a thin pass-through with no business logic to wrap; PATTERNS.md line 91 explicitly excludes a Tracer here, and the SERVER span lives in HttpServerSpanFilter (D-07). Verified: 0 lines changed in OrderController.java."
  - "Catch parameter is RuntimeException (not Exception) — both Spring's RabbitTemplate (AmqpException is unchecked) and OrderService.place's signature throw no checked exceptions. Matches the D-01 template at PATTERNS.md line 252."
  - "Used the qualified import shape for value-enum classes (\"import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues\") — verified the jar layout (MessagingIncubatingAttributes\\$MessagingOperationTypeIncubatingValues.class as a nested class file). Both qualified and the planning-time fallback \"top-level\" import shapes would compile, but the qualified form is robust to package-nesting changes across patch versions."
  - "Required RESEARCH FLAG #1 teaching comment is verbatim per PATTERNS.md line 240: \"semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create\" — workshop attendees see the migration rationale at the call site."

patterns-established:
  - "Span-wrapping symmetry across producer + consumer: the D-01 inline template established at the SERVER span (Plan 02-02 / HttpServerSpanFilter) is now applied at the INTERNAL + PRODUCER sites (this plan); Plan 02-05 will apply it at CONSUMER + INTERNAL sites. All 5 sites share one 8-12 line idiom — boilerplate IS the lesson."
  - "Active-Scope threading via return-inside-try — the canonical \"how to keep the active Span available for nested SDK calls\" pattern. The lesson generalises: any framework that bridges threads (Reactor, virtual threads, async servlets) needs Span.makeCurrent() Scope to span the work; the inline try-with-resources idiom is the simplest demonstration."
  - "Stable-vs-incubating semconv coordinate split applied at attribute call sites: MessagingIncubatingAttributes.* for messaging keys + value enums (still evolving in spec); HTTP/URL/Server attributes from the stable io.opentelemetry.semconv.* package were used in Plan 02-02. Workshop attendees see both sides of the split in two adjacent files."

requirements-completed: [TRACE-06, TRACE-07]

# Metrics
duration: 8min
completed: 2026-05-01
---

# Phase 2 Plan 04: Producer Instrumentation Summary

**Wraps OrderService.place(payload) in an INTERNAL span (TRACE-06) and OrderPublisher.publish(orderId, payload) in a PRODUCER span (TRACE-07 + D-11) using the EXACT D-01 pure-inline template — Tracer constructor-injected (D-02), full try/Scope/try/catch/finally shape with recordException + setStatus(ERROR) (D-03), 4 messaging semconv attrs from MessagingIncubatingAttributes including the new MESSAGING_OPERATION_TYPE=SEND (NOT the deprecated MESSAGING_OPERATION/"publish" per RESEARCH FLAG #1). Verified end-to-end: one POST /orders → 3-span producer trace in Tempo (SERVER → INTERNAL → PRODUCER), parent-child chain valid, all 4 messaging attributes correct.**

## Performance

- **Duration:** ~8 min (469 s)
- **Started:** 2026-05-01T17:14:56Z
- **Completed:** 2026-05-01T17:22:45Z
- **Tasks:** 3 (T1 modify OrderService.java; T2 modify OrderPublisher.java; T3 end-to-end smoke verification)
- **Files modified:** 2 (OrderService.java + OrderPublisher.java)
- **Files created:** 0 (T3 is verification-only — no source diff)

## Accomplishments

- **OrderService.place(...) now wraps the body in an INTERNAL span (TRACE-06).** Constructor injects Tracer alongside OrderPublisher; D-01 inline template names the span "OrderService.place" with `SpanKind.INTERNAL`; the body's three lines (`UUID.randomUUID()` + `publisher.publish(...)` + `return orderId`) all live INSIDE the try block so the active Scope threads through to the PRODUCER span in OrderPublisher (the parent-child chain depends on this). D-03 catch shape present (`recordException + setStatus(ERROR) + throw + finally end`) even though Phase 2 has no failure path — keeps Phase 3's diff focused on the propagation pair, not on restructuring spans.
- **OrderPublisher.publish(...) now wraps the body in a PRODUCER span with 4 messaging semconv attrs (TRACE-07 + D-11).** Constructor injects Tracer alongside RabbitTemplate; D-01 inline template names the span "orders.created publish" (D-04: span name uses the QUEUE name; the ROUTING_KEY goes on a separate attribute per D-11) with `SpanKind.PRODUCER`. The 4 attrs come from `MessagingIncubatingAttributes`: `MESSAGING_SYSTEM = MessagingSystemIncubatingValues.RABBITMQ`, `MESSAGING_DESTINATION_NAME = RabbitConfig.QUEUE` ("orders.created"), `MESSAGING_OPERATION_TYPE = MessagingOperationTypeIncubatingValues.SEND` (NEW key + value enum per RESEARCH FLAG #1, NOT the deprecated MESSAGING_OPERATION + literal "publish"), `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY = RabbitConfig.ROUTING_KEY` ("order.created"). All three publish-method body lines (HashMap copy + put + convertAndSend) live INSIDE the try block.
- **OrderController is UNCHANGED.** The controller is a thin pass-through to OrderService.place; PATTERNS.md line 91 explicitly excludes a Tracer parameter here, and the SERVER span lives in HttpServerSpanFilter (D-07). `git status` confirms 0 lines modified in OrderController.java.
- **producer-service compiles + packages cleanly.** `mvn -pl producer-service clean compile` BUILD SUCCESS (7 source files, no warnings); `mvn -pl producer-service -DskipTests package` BUILD SUCCESS produces `producer-service-0.1.0-SNAPSHOT.jar`. The qualified import shape for the value-enum nested classes (`import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues`) compiles cleanly — verified the jar layout (`MessagingIncubatingAttributes$MessagingOperationTypeIncubatingValues.class` as a nested class file), confirming nested-class import works.
- **End-to-end producer trace correct: 3 spans, parent-child chain SERVER → INTERNAL → PRODUCER.** Started producer JVM on port 8082 (port 8080 was held by a parallel sibling worktree's producer running pre-Plan-02-04 code — see Deviations); POST /orders returned 202 with orderId=`e457e90e-6763-45d7-87f5-f46fa8747769`; Tempo Search via Grafana datasource proxy returned trace `25eb831a77261da7c8e2832a71b2fee5` with 3 spans, kinds {SERVER:1, INTERNAL:1, PRODUCER:1}, names {"POST /orders", "OrderService.place", "orders.created publish"}, parent-child chain valid (every non-root span's parentSpanId resolves to another span in the trace), shape exactly: `POST /orders` (SERVER) → `OrderService.place` (INTERNAL) → `orders.created publish` (PRODUCER).
- **PRODUCER span carries the 4 messaging semconv attrs with correct values; deprecated key absent.** Tempo trace fetch confirms the PRODUCER span's attribute keys are exactly `{messaging.system, messaging.destination.name, messaging.operation.type, messaging.rabbitmq.destination.routing_key}` with values `{rabbitmq, orders.created, send, order.created}` — and the bare deprecated key `messaging.operation` is NOT set (RESEARCH FLAG #1 mitigated). Workshop attendees who inspect the trace in Grafana → Tempo → Span attributes will see the modern semconv shape, not deprecated relics.
- **Producer graceful shutdown latency = 1s.** SIGTERM to the producer JVM completed within 1 second (well inside the 12s acceptance window and the 10s `OpenTelemetrySdk.close()` join window from Plan 02-02's TRACE-04 mitigation). No leftover processes, port 8082 released cleanly.
- **Plan 02-01 invariant preserved.** `mise run verify:bom` exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`

## Task Commits

Each task committed atomically using `git commit --no-verify` (worktree mode; orchestrator validates hooks once after all parallel agents complete):

1. **Task 1: Modify OrderService.java — INTERNAL span around place(...) using D-01 inline template** — `de5dfbc` (feat)
2. **Task 2: Modify OrderPublisher.java — PRODUCER span around publish(...) with 4 messaging semconv attrs** — `2f906c1` (feat)
3. **Task 3: End-to-end smoke (verification only — no source changes)** — no commit (T3 produces no source diff per `<files>(none — verification only)</files>`)

**Plan metadata commit:** _(produced after this SUMMARY.md is written; commits SUMMARY only — STATE.md and ROADMAP.md owned by the orchestrator per the worktree contract)_

## Files Modified

- **`producer-service/src/main/java/com/example/producer/domain/OrderService.java`** (modified — was 22 lines / Phase 1 → 64 lines / +49 / commit `de5dfbc`) — Added `Tracer` constructor parameter (single-constructor autowire) and a `private final Tracer tracer` field. Replaced the 4-line method body with the D-01 inline INTERNAL span: `Span span = tracer.spanBuilder("OrderService.place").setSpanKind(SpanKind.INTERNAL).startSpan();` + `try (Scope scope = span.makeCurrent()) { ... return orderId; } catch (RuntimeException e) { recordException + setStatus(ERROR) + throw } finally { span.end(); }`. Imports use the explicit OTel API package paths (`io.opentelemetry.api.trace.{Span,SpanKind,StatusCode,Tracer}` + `io.opentelemetry.context.Scope`) per the existing codebase's import style. Class-level Javadoc mentions TRACE-06 and the D-01 template name so workshop attendees can cross-reference the design decision.
- **`producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`** (modified — was 24 lines / Phase 1 → 85 lines / +70 -9 / commit `2f906c1`) — Added `Tracer` constructor parameter and a `private final Tracer tracer` field. Replaced the 3-line method body with the D-01 inline PRODUCER span carrying 4 attributes set BEFORE `startSpan()`: `MessagingIncubatingAttributes.MESSAGING_SYSTEM = RABBITMQ`, `MESSAGING_DESTINATION_NAME = RabbitConfig.QUEUE`, `MESSAGING_OPERATION_TYPE = SEND`, `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY = RabbitConfig.ROUTING_KEY`. The `// APP-02:` comment from Phase 1 is preserved (slightly expanded with the Phase-3-hint about the missing traceparent injection). Required RESEARCH FLAG #1 teaching comment present verbatim. Class-level Javadoc references TRACE-07 + D-11 + the Phase-3 deletion hand-off (CONTEXT.md D-09).

## File Tree (producer-service Java sources after Plan 02-04)

```
producer-service/src/main/java/com/example/producer/
├── api/
│   └── OrderController.java                           (Phase 1 — unchanged this plan)
├── config/
│   ├── HttpServerSpanFilter.java                      (Plan 02-02 — unchanged this plan)
│   ├── OtelSdkConfiguration.java                      (Plan 02-02 — unchanged this plan)
│   └── RabbitConfig.java                              (Phase 1 — unchanged; QUEUE/ROUTING_KEY constants reused as PRODUCER-span attribute values)
├── domain/
│   └── OrderService.java                              (MODIFIED in 02-04 — INTERNAL span)
├── messaging/
│   └── OrderPublisher.java                            (MODIFIED in 02-04 — PRODUCER span + 4 messaging attrs)
└── ProducerApplication.java                           (Phase 1 — unchanged; @SpringBootApplication entrypoint)
```

(7 Java files. 2 modified this plan; 5 unchanged. The unchanged set is intentional — DOC-05 + D-07 + PATTERNS.md line 91.)

## Verification Gate Output

### `mvn -pl producer-service clean compile` — BUILD SUCCESS

```
[INFO] --- enforcer:3.5.0:enforce (enforce) @ producer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion passed
[INFO] Rule 2: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
[INFO]
[INFO] --- compiler:3.13.0:compile (default-compile) @ producer-service ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 7 source files with javac [debug target 17] to target/classes
[INFO] BUILD SUCCESS
```

### `mvn -pl producer-service -DskipTests package` — BUILD SUCCESS

```
[INFO] --- compiler:3.13.0:compile (default-compile) @ producer-service ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 7 source files with javac [debug target 17] to target/classes
[INFO] --- jar:3.4.1:jar (default-jar) @ producer-service ---
[INFO] Building jar: .../producer-service/target/producer-service-0.1.0-SNAPSHOT.jar
[INFO] BUILD SUCCESS
```

### Smoke-test boot

```
=== Started ProducerApplication line ===
2026-05-01T13:21:08.766-04:00 INFO 2580028 --- [order-producer] [main]
  c.example.producer.ProducerApplication : Started ProducerApplication in 0.806 seconds
  (process running for 0.91)

=== POST /orders ===
HTTP_CODE=202
BODY: {"orderId":"e457e90e-6763-45d7-87f5-f46fa8747769"}
ORDER_ID=e457e90e-6763-45d7-87f5-f46fa8747769
```

### Tempo trace structure (Step 5 — 3-span SERVER→INTERNAL→PRODUCER chain)

Used the Grafana datasource proxy fallback because port `:3200` is not exposed externally by `grafana/otel-lgtm:0.26.0` (only `:3000`, `:4317`, `:4318`); same approach Plan 02-02 documented.

```
$ curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/traces/25eb831a77261da7c8e2832a71b2fee5" | python3 ...

span count: 3
span kinds: {'SPAN_KIND_PRODUCER': 1, 'SPAN_KIND_INTERNAL': 1, 'SPAN_KIND_SERVER': 1}
normalized kinds: {4: 1, 1: 1, 2: 1}
names: {'orders.created publish', 'POST /orders', 'OrderService.place'}

--- parent-child chain ---
ROOT: kind=2 name='POST /orders' spanId=lqAa5RAXNxk=
|- kind=2 name='POST /orders' spanId=lqAa5RAXNxk= parentId=
  |- kind=1 name='OrderService.place' spanId=dV3rsHISS8U= parentId=lqAa5RAXNxk=
    |- kind=4 name='orders.created publish' spanId=DMgmZ0UNCNM= parentId=dV3rsHISS8U=

OK: 3 spans with kinds {4: 1, 1: 1, 2: 1} and names {'orders.created publish', 'POST /orders', 'OrderService.place'}
```

(Three spans, one root, fully nested. The chain `SERVER (POST /orders) → INTERNAL (OrderService.place) → PRODUCER (orders.created publish)` is exactly what TRACE-06 + TRACE-07 mandate. `parentSpanId` resolution succeeded for every non-root span.)

### Tempo PRODUCER-span attribute audit (Step 6 — 4 messaging semconv attrs + deprecated key absence)

```
$ python3 ... < /tmp/trace.json

PRODUCER span: name=orders.created publish spanId=DMgmZ0UNCNM=
attrs keys: ['messaging.system', 'messaging.operation.type',
             'messaging.rabbitmq.destination.routing_key', 'messaging.destination.name']
raw attrs: {'messaging.system': {'stringValue': 'rabbitmq'},
            'messaging.operation.type': {'stringValue': 'send'},
            'messaging.rabbitmq.destination.routing_key': {'stringValue': 'order.created'},
            'messaging.destination.name': {'stringValue': 'orders.created'}}

--- Required attributes ---
  OK: messaging.system = 'rabbitmq' (expected 'rabbitmq')
  OK: messaging.destination.name = 'orders.created' (expected 'orders.created')
  OK: messaging.operation.type = 'send' (expected 'send')
  OK: messaging.rabbitmq.destination.routing_key = 'order.created' (expected 'order.created')

--- Forbidden deprecated key ---
  OK: 'messaging.operation' (bare) absent — only operation.type used

ALL OK: 4 messaging semconv attributes correct, deprecated key absent
```

(All 4 expected keys present with the exact spec-correct values; the deprecated bare `messaging.operation` key is absent — RESEARCH FLAG #1 mitigated at runtime, not just at compile time.)

### Graceful shutdown

```
Killing PID=2580028
Shutdown elapsed: 1s
OK: shut down cleanly within 1s

=== Listener on 8082? ===
(8082 free again)
```

(1s shutdown latency, well inside the 12s acceptance window and the 10s `OpenTelemetrySdk.close()` join window. Plan 02-02's TRACE-04 mitigation continues to work as built.)

### `mise run verify:bom` — Plan 02-01 invariant preserved

```
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

## Decisions Made

- **Used the NEW semconv 1.40.0 keys + value enums (RESEARCH FLAG #1).** `MESSAGING_OPERATION_TYPE = MessagingOperationTypeIncubatingValues.SEND` instead of the deprecated `MESSAGING_OPERATION = "publish"`. Verified at runtime: Tempo trace shows `messaging.operation.type=send` and the bare `messaging.operation` is absent. The required teaching comment (`// semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create`) is present verbatim per PATTERNS.md line 240.
- **Constructor parameter order: pre-existing dependency first, Tracer last.** `OrderService(OrderPublisher, Tracer)` and `OrderPublisher(RabbitTemplate, Tracer)` — Spring's auto-wiring is parameter-order-agnostic, but appending the new dependency makes the Phase 1 → Phase 2 git diff cleanly readable in `git log -p`.
- **OrderController unchanged — no Tracer parameter.** PATTERNS.md line 91 explicitly excludes the controller (it's a thin pass-through with no business logic to wrap; the SERVER span already covers it via HttpServerSpanFilter, D-07). 0 lines changed in OrderController.java.
- **Catch parameter is `RuntimeException` (not `Exception`).** Spring's RabbitTemplate throws `AmqpException` (a `RuntimeException` subclass) on broker connectivity issues; OrderService.place's signature throws no checked exceptions. Matches the D-01 template at PATTERNS.md line 252.
- **Used the qualified import shape for the value-enum nested classes** (`import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues`). Verified the jar layout (`MessagingIncubatingAttributes$MessagingOperationTypeIncubatingValues.class` as a nested class file) — qualified import is robust to package-nesting changes across patch versions; the planning-time fallback (top-level imports) was not needed.
- **Required teaching comments included verbatim:** RESEARCH FLAG #1 migration note (PATTERNS.md line 240), D-01 inline-template "boilerplate is the lesson" rationale, D-03 forward-compat catch shape note, D-04 span-name-uses-queue note, D-11 routing-key-as-separate-attribute note, Phase 3 hand-off note (CONTEXT.md D-09 — this PRODUCER span will be deleted when TracingMessagePostProcessor lands).
- **Used port 8082 for smoke-test producer JVM (deviation Rule 3 workaround).** Port 8080 was held by a parallel sibling worktree's producer running pre-Plan-02-04 code; that JVM could not validate my new INTERNAL/PRODUCER spans. Started a fresh producer on 8082 backed by my worktree's compiled classes (verified: classpath path includes `agent-a2d92b2f70bc196f4`). The plan's smoke flow (POST → 202 → orderId → Tempo search → trace fetch) ran identically; only the URL host port changed from `:8080` to `:8082`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used port 8082 instead of 8080 for the smoke-test producer because port 8080 was held by a parallel sibling worktree's producer running pre-Plan-02-04 code.**

- **Found during:** Task 3, Step 2 (background producer launch).
- **Issue:** When my `mise x -- mvn -pl producer-service spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080"` launcher exited 144 (SIGPIPE — Claude's nohup wrapper terminated), I checked port 8080 and found a producer JVM already listening, but its working directory and classpath traced back to the sibling worktree `agent-a4f5dcb1bbd86edd5` (running plan 02-05 — consumer-side instrumentation; the sibling started a producer to e2e-validate its own consumer changes). That JVM was running OLD code: `grep tracer.spanBuilder("OrderService.place")` against the sibling's `OrderService.java` returned 0 — pre-Plan-02-04 baseline. I could not use it to verify my new INTERNAL/PRODUCER spans.
- **Root cause:** The same multi-worktree race documented in Plan 02-02's deviation (`docker-compose` and host ports are shared across parallel worktrees on the same machine; both Wave 3 plans 02-04 and 02-05 want to e2e-test producer behavior).
- **Fix:** Changed the smoke-test target port from 8080 to 8082 (which was free; verified with `ss -tln`). Kept everything else identical — same `mise x -- mvn` invocation pattern, same redirection through `setsid` + `disown` for full detachment, same `setid` shell ensures clean exit-on-bash-wrapper-death decoupling. The producer started cleanly on 8082 in 0.806s, smoke flow ran end-to-end (POST /orders → 202 → orderId → Tempo search via Grafana proxy → trace fetch → 3-span shape + 4-attr audit all OK), graceful shutdown in 1s.
- **Files modified:** None (pure runtime workaround for the smoke test; no source diff).
- **Verification:** Tempo trace `25eb831a77261da7c8e2832a71b2fee5` confirms my INTERNAL + PRODUCER spans landed (sibling's pre-Plan-02-04 producer would have produced only the SERVER span); `rootServiceName=order-producer` shared between mine and sibling's traces but my newer trace has 3 spans vs. sibling's 1.
- **Committed in:** N/A — runtime workaround only, no code change.
- **Out-of-scope deferral:** Multi-worktree port-sharing race is a parallel-execution orchestration concern, not an instrumentation concern. The orchestrator already mitigates the docker-compose container-name race (per Plan 02-02 deviation). A similar mitigation for host-ports — e.g., per-worktree port offsets, or running the producer on a Unix socket — is a candidate orchestrator improvement; not adding to deferred-items.md unless requested.

---

**Total deviations:** 1 (Rule 3 — Blocking, runtime-workaround only, no code change).

**Impact on plan:** No scope change, no architectural change, no new dependencies, no source-code modification. The plan executed exactly as written; only the smoke-test producer's port number was changed to work around a parallel-worktree port-sharing race. All 9 plan-level success criteria are green: TRACE-06 satisfied (INTERNAL span around OrderService.place), TRACE-07 satisfied (PRODUCER span with 4 attrs around OrderPublisher.publish), D-01..D-04 + D-11 honored, RESEARCH FLAG #1 addressed (NEW key + value enum), one POST → 3-span Tempo trace verified, plan 02-05 unblocked.

## Issues Encountered

- **Background launcher (`nohup mise x -- mvn ... &`) exited code 144 (SIGPIPE) before the JVM ever wrote to its log.** Claude's `nohup ... &` wrapper terminated after the file-descriptor pipe ahead of `mise x` was closed. The first attempt produced a zero-byte log and a sibling worktree's producer occupied port 8080 — see Deviations. Resolved by switching to a fully-detached `setsid bash -c '... < /dev/null > /dev/null 2>&1' &; disown` invocation that decouples the JVM lifecycle from the launcher shell — this gave a healthy producer that wrote to `/tmp/producer-02-04.log` and started in 0.806s.
- **Port `:3200` (Tempo's HTTP API) is NOT exposed externally** by `grafana/otel-lgtm:0.26.0` — only `:3000` (Grafana), `:4317` (OTLP gRPC), and `:4318` (OTLP HTTP) are bound. Plan's Step 4/5 anticipated this and provided the Grafana datasource proxy fallback (`http://localhost:3000/api/datasources/proxy/uid/tempo/api/...` with `admin:admin`). Used that fallback successfully — same approach Plan 02-02 documented.
- **Tempo's JSON span-kind encoding is a STRING in the proxy response** (`"SPAN_KIND_PRODUCER"`), not the OTLP-standard integer (4). Plan's Step 5 python3 verification snippet was robust enough — added a `normalize()` helper to map the string form back to the integer constants the snippet was checking. The acceptance-criteria assertions all passed against normalized integers.

## User Setup Required

None — no external services, no secrets, no environment variables. The two modified Java sources compile and run via the existing `mvn` + `mise` toolchain. Workshop attendees running this checkpoint will see:
- `mise run dev` brings infra up + starts both services
- `mise run demo:order` (or `curl -X POST :8080/orders -d '{}'`) returns 202 with an `orderId`
- Grafana → Tempo → Search with `service.name=order-producer` shows a 3-span trace per POST: `POST /orders` (SERVER) → `OrderService.place` (INTERNAL) → `orders.created publish` (PRODUCER)
- Span attribute panel on the PRODUCER span shows the 4 messaging semconv attrs

The two modified Java files are the readable deliverable; nothing to install.

## Threat Flags

None new. Plan 02-04's threat register (T-2-04-01 through T-2-04-06 in PLAN.md `<threat_model>`) covered the producer-service business-code instrumentation surface:

- **T-2-04-01 (Information Disclosure / span-attr PII):** Mitigated as designed — INTERNAL span on `OrderService.place(payload)` has NO `setAttribute` calls (D-04 — no semconv mandated for INTERNAL kind); the workshop captures span name only. Future phases adding business attributes (e.g., `order.priority` for METRIC-02 in Phase 4) will be evaluated for PII exposure individually. Verified in Tempo: INTERNAL span has 0 attribute keys.
- **T-2-04-02 (Information Disclosure / queue+routing-key names):** Accepted — these are workshop-local strings (`"orders.created"` / `"order.created"`) with no PII; the queue topology IS the messaging surface and is correctly observable in Tempo per OTel semconv.
- **T-2-04-03 / T-2-04-06 (Tampering / Repudiation — exception flow):** Mitigated — D-03 catch shape is present in both new spans (`recordException + setStatus(ERROR) + throw e`). Acceptance criteria asserted the `throw e;` line is present in both files; verified `grep -c 'throw e;' OrderService.java` returns 1, same for OrderPublisher.java. Refactor reviewers see the explicit shape and won't accidentally drop it.
- **T-2-04-04 (DoS / per-request span overhead):** Accepted — workshop-volume traffic; BSP queue depth (2048) provides backpressure if Tempo backs up. Smoke test confirmed POST /orders → 202 with no observable latency regression vs. Phase 1 baseline.
- **T-2-04-05 (Information Disclosure / stack trace in span events):** Accepted — workshop scope; the stack trace IS the teaching moment for Phase 3's APP-04 + TRACE-09 (deterministic 10% failure path). No PII or credentials in stack traces.

No new threat flags. This plan adds NO new network surface (the AMQP publish from Phase 1 is unchanged in shape — only spans are added around it), NO new env vars, NO new credentials, NO new endpoints.

## Self-Check: PASSED

Verified after writing this SUMMARY (per `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `producer-service/src/main/java/com/example/producer/domain/OrderService.java`
- `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-producer-instrumentation-SUMMARY.md` (this file)

**Commits (all FOUND in git log):**
- `de5dfbc` — `feat(02-04): wrap OrderService.place in INTERNAL span (TRACE-06)`
- `2f906c1` — `feat(02-04): wrap OrderPublisher.publish in PRODUCER span (TRACE-07 + D-11)`

## Next Phase Readiness

- **Plan 02-05 (consumer-instrumentation, Wave 3 parallel sibling) is independent.** This plan and 02-05 modify disjoint file sets (producer-service vs. consumer-service); both can land concurrently. When 02-05 ships, one POST /orders will produce two disconnected traces in Tempo — producer-side 3-span chain (this plan) plus consumer-side 2-span chain (CONSUMER + INTERNAL via 02-05) — the broken-then-fixed pedagogical state Phase 3 will resolve via W3C context propagation.
- **Plan 02-06 (Wave 4 — README + step-02-traces tag) is unblocked once 02-05 lands.** This SUMMARY's Tempo trace fixture (`25eb831a77261da7c8e2832a71b2fee5`) provides the producer-side reference for the README walkthrough (DOC-05 callout). The consumer-side reference will come from 02-05's SUMMARY.
- **Phase 3 hand-off (load-bearing per CONTEXT.md D-09):** Phase 3's first task MUST DELETE the inline PRODUCER span from `OrderPublisher.publish(...)` when installing `TracingMessagePostProcessor`, otherwise Phase 3 produces double PRODUCER spans (one inline + one from the post-processor). The Phase-3-hint comment block in OrderPublisher.publish (lines about "Phase 2 does NOT inject the traceparent header here — that's Phase 3's headline lesson (PROP-01)") makes this explicit. The INTERNAL span on OrderService.place stays unchanged through Phase 3 onward.
- **Phase 4 (metrics) inheritance.** The INTERNAL span on `OrderService.place` gives Phase 4 a deterministic place to record METRIC-02 (`order.placed` counter): inside the active span, post-publish, before `return orderId`. The Tracer bean and `Span.current()` API will be the same surface; only the meter API joins.
- **Phase 5 (logs correlation) inheritance.** Logback MDC will carry `trace_id`/`span_id` while the INTERNAL span is active; any future `LOG.info(...)` calls inside `OrderService.place` will correlate automatically once the OpenTelemetryAppender lands in Phase 5's OtelSdkConfiguration extension.
- **Manual-SDK pedagogy preserved.** Workshop attendees can `git checkout step-02-traces` (when Plan 02-06 tags it) and read the 5 spans across producer + consumer top-to-bottom in their IDE. Each span site uses the EXACT D-01 inline template — the only variation is `.setSpanKind()` and the attribute set. The 8-12 line idiom is the workshop's textbook.

---
*Phase: 02-manual-sdk-bootstrap-first-traces*
*Plan: 04 (producer-instrumentation)*
*Completed: 2026-05-01*
