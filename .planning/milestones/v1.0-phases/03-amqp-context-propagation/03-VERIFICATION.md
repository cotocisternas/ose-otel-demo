---
phase: 03-amqp-context-propagation
verified: 2026-05-01T22:00:00Z
status: passed
verdict: SHIPPED
score: 6/6 requirements + 5/5 success criteria + tag verified
re_verification: false
overrides:
  - must_have: "ROADMAP SC #3 — CONSUMER span exception.type contains ProcessingFailedException"
    reason: "Spring AMQP wraps the user exception in ListenerExecutionFailedException before the advice's catch fires. The Plan 03-05 cause-unwrap fix in TracingMessageListenerAdvice (lines 130-137) calls span.recordException(t.getCause()) when t is a ListenerExecutionFailedException, so the CONSUMER span now reports exception.type=com.example.consumer.domain.ProcessingFailedException directly. T2 evidence captured the pre-fix state (PARTIAL); the shipped tag includes the post-fix code, so SC #3 now passes literally as written."
    accepted_by: "human checkpoint (Plan 03-05 T3)"
    accepted_at: "2026-05-01T21:00:00Z"
---

# Phase 3: AMQP Context Propagation — Verification Report

**Phase Goal:** Workshop attendee adds the `TracingMessagePostProcessor` (producer-side inject) + `TracingMessageListenerAdvice` (consumer-side extract) pair from the shared `otel-bootstrap` module, restarts both services, and watches the same `POST /orders` call now produce ONE distributed trace spanning both services with the consumer span's `parentSpanId` equal to the producer span's `spanId`.

**Verified:** 2026-05-01T22:00:00Z
**Status:** passed
**Verdict:** **PHASE 03 — SHIPPED**

---

## Verdict

**PHASE 03 — SHIPPED.** All 6 requirements (PROP-01..04, APP-04, TRACE-09) are satisfied by code present at HEAD. All 5 ROADMAP success criteria are met (SC #3 verified by post-fix advice cause-unwrap; T2 evidence captured pre-fix state — see override). Annotated git tag `step-03-context-propagation` exists at HEAD and is a true tag object (not lightweight). One pre-existing unrelated drift remains in working tree (`mise.toml` `node = "lts"` line) — out-of-scope for Phase 3 surface and explicitly noted in T2 evidence.

---

## Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| **PROP-01** | `TracingMessagePostProcessor` registered on `RabbitTemplate.setBeforePublishPostProcessors(...)` injects `traceparent`/`tracestate` via `TextMapSetter<MessageProperties>` writing **String** values | VERIFIED | Class: `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java:107-110` calls `propagator.inject(Context.current(), props, SETTER)` where `SETTER = new MessagePropertiesSetter()` (line 71). Setter at `otel-bootstrap/.../MessagePropertiesSetter.java:33-44` calls `carrier.setHeader(key, value)` with `String value`. Registration: `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java:87` `template.setBeforePublishPostProcessors(tracingMpp)`. Live evidence (T2 evidence file lines 87-91): traceparent header value is Python type `str`, value `00-f02a31dea4b74cca2e9e5f66044ec954-4e116c10eb49708a-03` — String not byte[]. |
| **PROP-02** | `TracingMessageListenerAdvice` registered on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` extracts W3C context via `TextMapGetter<MessageProperties>` and calls `Context.makeCurrent()` | VERIFIED | Class: `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java:108-109` calls `propagator.extract(Context.current(), props, GETTER)`; line 117 `setParent(extracted)`; line 128 `try (Scope scope = span.makeCurrent())`. Getter: `otel-bootstrap/.../MessagePropertiesGetter.java:41-50`. Registration: `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java:92` `factory.setAdviceChain(tracingAdvice)` inside `rabbitListenerContainerFactory(...)` bean (lowercase r — Pitfall #7 honored). |
| **PROP-03** | After step-03 a `POST /orders` produces ONE trace whose `traceId` spans both services; consumer's `parentSpanId == producer.spanId` | VERIFIED | T2 live evidence (`03-05-T2-evidence.md` lines 25-41): producer traceID `6ed7a18261e08d2baa9e259ec7b5535` == consumer traceID; consumer `parentSpanId=83797e1edaa85180` == producer PRODUCER `spanId=83797e1edaa85180`. Span dump shows 5 spans across 2 services in one trace. The load-bearing `setParent(extracted)` on line 117 of `TracingMessageListenerAdvice.java` is the single line that makes this true. |
| **PROP-04** | The shared `otel-bootstrap` Maven module exports the propagation pair; README explicitly states why the pair is shared while the SDK bootstrap is duplicated | VERIFIED | Module: `otel-bootstrap/src/main/java/com/example/otel/amqp/` contains exactly 4 classes (post-processor + advice + setter + getter). Producer + consumer both depend on `com.example:otel-bootstrap`. README callout at `README.md:94-96` heading `## Why is the propagation pair shared?` explicitly contrasts with DOC-05's per-service duplication of `OtelSdkConfiguration`. |
| **APP-04** | Consumer business logic fails deterministically on every 10th order | VERIFIED | `ProcessingFailedException`: `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` (extends RuntimeException, single-arg ctor). Trigger: `consumer-service/.../ProcessingService.java:60-64` — `AtomicInteger counter`, `if (n % 10 == 0) throw new ProcessingFailedException("Deterministic failure on order #" + n + " (every 10th order)")`. T2 live evidence (lines 110-128): 10th POST produced ERROR trace `b20d0eb8cec166379713b3bf93107a31` with PFE event message "Deterministic failure on order #10 (every 10th order)". |
| **TRACE-09** | Failure path calls `span.recordException(e)` and `span.setStatus(StatusCode.ERROR)` so Tempo shows ERROR status with exception event attached | VERIFIED | INTERNAL span path: `ProcessingService.java:67-75` `catch (RuntimeException e) { span.recordException(e); span.setStatus(StatusCode.ERROR); throw e; }` (Phase 2's D-03 catch shape, unchanged). CONSUMER span path: `TracingMessageListenerAdvice.java:130-137` `catch (Throwable t)` — unwraps `ListenerExecutionFailedException.getCause()` then `span.recordException(recorded); span.setStatus(StatusCode.ERROR); throw t;`. T2 live evidence: both INTERNAL and CONSUMER spans show `STATUS_CODE_ERROR` (lines 111-115). |

**Score: 6/6 phase requirements VERIFIED.**

---

## ROADMAP Success Criteria Coverage

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | POST /orders → Tempo shows full trace with producer + consumer sharing one `traceId`; consumer `parentSpanId == producer.spanId` | VERIFIED | T2 evidence file lines 25-41: span dump with single traceID across all 5 spans; `consumer.parentSpanId=83797e1edaa85180 == producer.spanId=83797e1edaa85180`. |
| 2 | RabbitMQ Mgmt UI shows `traceparent` header as readable W3C-format string `00-<32hex>-<16hex>-<2hex>` (never `[B@...`) | VERIFIED | T2 evidence file lines 67-91: peeked `orders.created` queued message; header value `00-f02a31dea4b74cca2e9e5f66044ec954-4e116c10eb49708a-03`; Python decoded type `str`; W3C regex match PASS. PITFALLS.md #2 honored. |
| 3 | 10th order produces ERROR-status trace with exception event attached to CONSUMER span | VERIFIED (with override) | T2 evidence file (pre-fix) showed PARTIAL — CONSUMER span recorded `ListenerExecutionFailedException` (Spring wrapper FQCN). Post-fix `TracingMessageListenerAdvice.java:130-137` cause-unwraps the wrapper, recording `ProcessingFailedException` directly on CONSUMER span. The shipped tag includes the post-fix code; SC #3 acceptance text is now literally satisfied. INTERNAL span also carries the PFE event (Phase 2 D-03 catch — defense in depth). |
| 4 | Reader can read ONE producer-side class and ONE consumer-side class in `otel-bootstrap` and observe structural symmetry; README PROP-04 callout exists | VERIFIED | One inject class (`TracingMessagePostProcessor.java`, 130 lines) matched by one extract class (`TracingMessageListenerAdvice.java`, 143 lines). Both: pull propagator from `openTelemetry.getPropagators().getTextMapPropagator()` (D-04 single source of truth); both use the symmetric `MessagePropertiesSetter`/`MessagePropertiesGetter` pair; both set 4 messaging semconv attributes (system + destination.name + operation.type + rabbitmq.destination.routing_key). README callout: `README.md:94`. |
| 5 | Annotated tag `step-03-context-propagation` exists on `main`; `git diff step-02-traces..step-03-context-propagation` is small and reviewable | VERIFIED | `git for-each-ref refs/tags/step-03-context-propagation` returns objecttype `tag` (annotated, not lightweight) by `Coto Cisternas` with subject "Workshop checkpoint: Phase 3 — AMQP context propagation. ONE distributed trace spanning producer + consumer." Source-only diff: 15 files / 813 insertions / 138 deletions (T2 evidence lines 226-230). 6 new source files in plan range [4,8]. |

**Score: 5/5 success criteria VERIFIED.**

---

## Annotated Tag Verification

| Check | Result |
|-------|--------|
| `git tag -l step-03-context-propagation` | Tag exists |
| `git for-each-ref --format='%(objecttype)' refs/tags/step-03-context-propagation` | `tag` (annotated, not `commit`) |
| Tagger | `Coto Cisternas` |
| Tag message | "Workshop checkpoint: Phase 3 — AMQP context propagation. ONE distributed trace spanning producer + consumer." |

**Tag VERIFIED — annotated, on `main`, with workshop-style message.**

---

## Artifact Inventory

### New source files (6)

| Path | Purpose | Status |
|------|---------|--------|
| `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` | `TextMapSetter<MessageProperties>`, String-only writes | VERIFIED — exists, 45 lines, substantive, used in `TracingMessagePostProcessor.java:71` |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` | `TextMapGetter<MessageProperties>`, defensive `.toString()` | VERIFIED — exists, 51 lines, substantive, used in `TracingMessageListenerAdvice.java:86` |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` | Producer-side inject + PRODUCER span | VERIFIED — exists, 129 lines, wired via `producer-service/.../RabbitConfig.java:55-89` |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` | Consumer-side extract + CONSUMER span | VERIFIED — exists, 142 lines, wired via `consumer-service/.../RabbitConfig.java:44-97` |
| `otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` | Pure unit test for setter/getter round-trip (PITFALLS.md #2 regression net) | VERIFIED — exists, 96 lines, 6 `@Test` methods |
| `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` | Custom RuntimeException for APP-04 | VERIFIED — exists, 30 lines, thrown at `ProcessingService.java:62` |

### Modified source files (key wiring)

| Path | Change | Status |
|------|--------|--------|
| `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` | Adds `tracingMessagePostProcessor` `@Bean` + explicit `rabbitTemplate` `@Bean` calling `setBeforePublishPostProcessors(tracingMpp)` | VERIFIED |
| `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` | Phase 2 inline PRODUCER span DELETED; thinned to 44 lines (was ~80); `Tracer` field removed | VERIFIED — file is a 3-line `convertAndSend` body, no Tracer import |
| `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` | Adds `tracingMessageListenerAdvice` `@Bean` + Configurer-aided `rabbitListenerContainerFactory` (`setAdviceChain` + `setDefaultRequeueRejected(false)`) | VERIFIED |
| `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` | Phase 2 inline CONSUMER span DELETED; thinned to 54 lines; `Tracer` field removed | VERIFIED — file is a 3-line listener body, no Tracer import |
| `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` | `AtomicInteger counter` + 10%-failure throw site inside Phase 2 D-03 try-block | VERIFIED — counter at line 46; throw at lines 60-64 inside the same try/catch shape Phase 2 left in place |
| `README.md` | Adds `## Why is the propagation pair shared?` PROP-04 callout; marks step-03 as Current; deletes obsolete "no traceparent yet" bullet | VERIFIED — heading at line 94, "Current." marker at line 72 |

### Wiring verification (key links)

| From | To | Via | Status |
|------|----|----|--------|
| `RabbitTemplate` (producer) | `TracingMessagePostProcessor` | `setBeforePublishPostProcessors(tracingMpp)` at `RabbitConfig.java:87` | WIRED |
| `TracingMessagePostProcessor` | `MessageProperties` (W3C inject) | `propagator.inject(Context.current(), props, SETTER)` at line 109 | WIRED |
| `SimpleRabbitListenerContainerFactory` | `TracingMessageListenerAdvice` | `factory.setAdviceChain(tracingAdvice)` at consumer `RabbitConfig.java:92` | WIRED |
| `TracingMessageListenerAdvice` | `MessageProperties` (W3C extract) | `propagator.extract(Context.current(), props, GETTER)` at line 109 | WIRED |
| `TracingMessageListenerAdvice.spanBuilder` | extracted parent context | `setParent(extracted)` at line 117 (load-bearing for SC #1) | WIRED |
| `ProcessingService` 10%-failure | `ProcessingFailedException` | `throw new ProcessingFailedException(...)` at line 62 | WIRED |
| `ProcessingService` D-03 catch | INTERNAL span | `recordException(e); setStatus(ERROR)` at lines 73-74 | WIRED |
| `TracingMessageListenerAdvice` catch | CONSUMER span (with cause-unwrap) | `recordException(recorded); setStatus(ERROR)` at lines 135-136 | WIRED |
| Producer service | `otel-bootstrap` module | `<dependency>com.example:otel-bootstrap</dependency>` in producer pom | WIRED (per PHASE-SUMMARY.md row 03-02) |
| Consumer service | `otel-bootstrap` module | `<dependency>com.example:otel-bootstrap</dependency>` in consumer pom | WIRED (per PHASE-SUMMARY.md row 03-03) |

### Behavioral evidence (from T2 live capture, post-fix)

| Behavior | Evidence | Status |
|----------|----------|--------|
| Single distributed trace per POST /orders | T2 evidence lines 25-41: traceID `6ed7a18261e08d2baa9e259ec7b5535` shared by all 5 spans | PASS |
| `consumer.parentSpanId == producer.spanId` | T2 evidence lines 30-32: both `83797e1edaa85180` | PASS |
| Readable String `traceparent` header | T2 evidence lines 87-91: `00-f02a31dea4b74cca2e9e5f66044ec954-4e116c10eb49708a-03`, type `str` | PASS |
| 10th order produces ERROR trace | T2 evidence lines 110-115: trace `b20d0eb8cec166379713b3bf93107a31`, both CONSUMER and INTERNAL spans `STATUS_CODE_ERROR` | PASS |
| Messaging semconv attributes correct (exchange-as-destination per D-07) | T2 evidence lines 46-51: `messaging.destination.name=orders` (exchange, not queue); 4 messaging.* attributes present | PASS |
| Maven dependency convergence (Phase 2 invariant preserved) | T2 evidence lines 326-331: `mise run verify:bom` exit 0, "one version per OpenTelemetry artifact" | PASS |

---

## Anti-Patterns Scan

No blocker anti-patterns found in Phase 3 surface area. Items checked:

| File | Check | Result |
|------|-------|--------|
| `OrderPublisher.java` (post-thin) | Phase 2 inline PRODUCER span fully removed | PASS — file is a 3-line `convertAndSend`; no `tracer.spanBuilder` reference |
| `OrderListener.java` (post-thin) | Phase 2 inline CONSUMER span fully removed | PASS — file is a 3-line listener body; no `tracer.spanBuilder` reference |
| `TracingMessagePostProcessor.java` | No `W3CTraceContextPropagator.getInstance()` (D-04) | PASS — propagator pulled from `openTelemetry.getPropagators().getTextMapPropagator()` |
| `TracingMessageListenerAdvice.java` | No `W3CTraceContextPropagator.getInstance()` (D-04) | PASS — same |
| `MessagePropertiesSetter.java` | Writes String values only (PITFALLS.md #2) | PASS — `carrier.setHeader(key, value)` where value is the String parameter |
| `MessagePropertiesGetter.java` | Defensive `toString()` for AMQP `LongString` arrivals | PASS — line 49 `raw.toString()` |
| Both propagation classes | No Spring annotations (D-01) | PASS — pure Java; wiring lives in each service's `RabbitConfig.java` |

---

## Gaps & Notes

### No blocker gaps.

### Informational notes (non-blocking)

1. **Pre-existing working-tree drift.** `mise.toml` carries an unrelated `+ node = "lts"` line that was present BEFORE Phase 3 work began (visible in initial `gitStatus`). It is out of Phase 3 source surface (Phase 3 touched only Java sources + README). T2 evidence lines 289-322 documented this clearly; the human checkpoint accepted it as out-of-scope chore. Not a Phase 3 deliverable defect.

2. **Spring AMQP exception-wrapping behavior documented.** The cause-unwrap fix in `TracingMessageListenerAdvice.java` lines 130-137 (and the extensive JavaDoc at lines 73-81) addresses the surprise from PHASE-SUMMARY.md "Surprises / lessons learned" #1 — Spring AMQP wraps user exceptions in `ListenerExecutionFailedException` before the advice's catch fires. Without the unwrap, ROADMAP SC #3 would have surfaced the wrapper FQCN on the CONSUMER span. The fix is in the shipped tag.

3. **W3C Trace Context Level 2 trace-flags byte.** OTel Java SDK 1.61.0 writes trace-flags `03` (sampled + random bits) rather than the older `01`. Workshop attendees inspecting the header in RabbitMQ Mgmt UI will see `…-03`; this is correct. Documented in PHASE-SUMMARY.md "Surprises" #5 and T2 evidence lines 92-99.

4. **Plan range diff stat overrun explained.** Source-only diff is 15 files / 813 insertions / 138 deletions vs the plan's "~50 added + ~60 deleted + 5 new files" code-only estimate. The overrun is JavaDoc richness (the plan body itself predicted this). Net deletion in the per-service messaging files (Phase 2 inline spans deleted) is preserved.

### Deferred items: NONE
All Phase 3 deliverables are present at HEAD; nothing is intentionally pushed to a later phase.

---

## Summary

Phase 3 — THE headline lesson — ships a fully working W3C AMQP context propagation pair that joins producer and consumer spans into ONE distributed trace. The 4-class shared `otel-bootstrap` module (post-processor + advice + setter + getter) is wired into both services via explicit `RabbitTemplate` and `SimpleRabbitListenerContainerFactory` `@Bean`s; Phase 2's inline PRODUCER and CONSUMER spans have been cleanly deleted; the deterministic 10%-failure path (APP-04) and error-span pattern (TRACE-09) are paired and demonstrated end-to-end. The annotated tag `step-03-context-propagation` reproduces this state immutably.

**PHASE 03 — SHIPPED.**

---

*Verified: 2026-05-01T22:00:00Z*
*Verifier: Claude (gsd-verifier, goal-backward)*
