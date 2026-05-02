---
phase: 03-amqp-context-propagation
plan: 03
subsystem: observability
tags: [opentelemetry, amqp, spring-amqp, context-propagation, w3c-trace-context, otel-bootstrap, consumer-wiring, rabbit-listener-factory, advice-chain]

# Dependency graph
requires:
  - phase: 03-amqp-context-propagation
    plan: 01
    provides: |
      `com.example:otel-bootstrap` JAR with `TracingMessageListenerAdvice`
      class — the `(OpenTelemetry, Tracer)` constructor wrapper consumed
      here as a `@Bean`. Plan 03-01 also shipped the
      `MessagePropertiesGetter` used internally by the advice for the
      W3C `traceparent` extract.
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: |
      Consumer-service's `OtelSdkConfiguration` already exposes
      `OpenTelemetry` (with W3C propagators wired per Phase 2 D-16) and a
      per-service `Tracer` named `com.example.consumer` — both injected
      by-type into the new `tracingMessageListenerAdvice` bean.
provides:
  - "Consumer side of W3C AMQP context propagation: `TracingMessageListenerAdvice` is now registered on every `@RabbitListener` invocation via `SimpleRabbitListenerContainerFactory.setAdviceChain(...)`"
  - "`@Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...)` (exact bean name) — Spring Boot's `RabbitAnnotationDrivenConfiguration` backs off cleanly via `@ConditionalOnMissingBean(name=\"rabbitListenerContainerFactory\")`"
  - "`setDefaultRequeueRejected(false)` on the listener factory — APP-04's deterministic 10% failure path (plan 03-04) won't trigger a NACK-requeue storm"
  - "`OrderListener.onOrder` reduced to a 3-line thin pass-through (Tracer parameter + field + 9 OTel imports + inline span body all DELETED per D-09)"
  - "`com.example:otel-bootstrap:0.1.0-SNAPSHOT` is now a runtime dependency of `consumer-service` (mirror-symmetric to plan 03-02's edit on `producer-service`)"
affects:
  - "03-04-app-04-failure-path"
  - "03-05-readme-and-exit-gate"

# Tech tracking
tech-stack:
  added:
    - "com.example:otel-bootstrap:0.1.0-SNAPSHOT (compile, intra-reactor) on consumer-service"
  patterns:
    - "Configurer-aided `@Bean SimpleRabbitListenerContainerFactory` with strict ordering: `configurer.configure(factory, connectionFactory)` → `setAdviceChain(tracingAdvice)` → `setDefaultRequeueRejected(false)` (D-08 + Pitfall #5)"
    - "Bean-name-as-API-contract — method name `rabbitListenerContainerFactory` (lowercase r) is the load-bearing string Spring Boot's `@ConditionalOnMissingBean(name=...)` matches against (Pitfall #7)"
    - "Thin pass-through listener body (D-09) — span lifecycle owned by AOP advice, listener body holds only domain logic. Mirrors plan 03-02's thin `OrderPublisher.publish(...)` shape (CONTEXT.md symmetry)"
    - "Per-service Tracer scope preserved (D-03) — the CONSUMER span appears under `com.example.consumer` instrumentation scope in Tempo even though the span is now built inside `com.example.otel.amqp` code"

key-files:
  created: []
  modified:
    - "consumer-service/pom.xml"
    - "consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java"
    - "consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java"

key-decisions:
  - "Followed the plan + CONTEXT.md decisions D-08, D-09, D-13 verbatim. No semantic deviation."
  - "RabbitConfig's @Bean methods left at package-private visibility (default), matching the existing 2 beans Phase 2 shipped — symmetric and minimum-surface-area."
  - "Used `Write` to replace RabbitConfig.java and OrderListener.java entirely (rather than multi-step Edit) because the per-task transformations were a near-total rewrite per the plan's verbatim file content blocks."

patterns-established:
  - "Pattern 1: User-defined `@Bean SimpleRabbitListenerContainerFactory` named EXACTLY `rabbitListenerContainerFactory` overrides Spring Boot's auto-configured factory via name-match on `@ConditionalOnMissingBean(name=...)`. The bean method name IS the bean name IS the override key."
  - "Pattern 2: Configurer-aided factory builders apply Boot defaults FIRST, then user overrides — order matters when the user override touches a property the Configurer ALSO touches under certain conditions (e.g., `spring.rabbitmq.listener.simple.retry.enabled=true` would touch adviceChain)."
  - "Pattern 3: Listener body D-09 thin shape: extract id from message → log → delegate. Span/error visibility lives in the wrapping advice; the listener body only owns domain logic."

requirements-completed: [PROP-02, PROP-03, PROP-04]

# Metrics
duration: 4min
completed: 2026-05-01
---

# Phase 03 Plan 03: Consumer Wiring Summary

**Wired the consumer-service side of W3C AMQP context propagation: registered `TracingMessageListenerAdvice` on a Configurer-aided `@Bean SimpleRabbitListenerContainerFactory` named exactly `rabbitListenerContainerFactory`, deleted Phase 2's inline CONSUMER span body from `OrderListener.onOrder` (along with the `Tracer` constructor parameter and 9 OTel imports), and added the `com.example:otel-bootstrap` dependency to consumer-service's pom — completing Wave 2's reader-side half so that once plan 03-02 is also merged, `consumer.parentSpanId == producer.spanId` will materialize at runtime per ROADMAP SC #1.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-05-01T20:24:14Z
- **Completed:** 2026-05-01T20:28:33Z
- **Tasks:** 4/4 complete (T1 pom dep, T2 RabbitConfig, T3 OrderListener, T4 verification)
- **Files modified:** 3 (1 pom.xml + 2 java sources)

## Accomplishments

- The `consumer-service` module now consumes `com.example:otel-bootstrap:0.1.0-SNAPSHOT` (verified via `mvn dependency:tree -Dincludes=com.example:otel-bootstrap` returning exactly ONE line).
- `consumer-service/.../config/RabbitConfig.java` now declares 4 `@Bean` methods (was 2): the existing `Queue ordersCreatedQueue` + `MessageConverter jsonMessageConverter` are preserved, plus the new `TracingMessageListenerAdvice tracingMessageListenerAdvice(...)` and `SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...)`.
- The user-defined factory bean has the EXACT method name `rabbitListenerContainerFactory` (lowercase r) that Spring Boot's `RabbitAnnotationDrivenConfiguration` matches against via `@ConditionalOnMissingBean(name="rabbitListenerContainerFactory")` — the auto-config backs off cleanly. Pitfall #7 honored.
- The factory builder order is the strict triple: `configurer.configure(factory, connectionFactory)` FIRST, `factory.setAdviceChain(tracingAdvice)` SECOND, `factory.setDefaultRequeueRejected(false)` THIRD. Pitfall #5 honored.
- `consumer-service/.../messaging/OrderListener.java` is now a 54-line file (down from 81): the `onOrder(Map)` body is 3 lines (extract `orderId`, `LOG.info`, `processingService.process(message)`); the `Tracer` constructor parameter + field + 9 OTel imports + inline span scaffolding are all deleted; `@RabbitListener(queues = RabbitConfig.QUEUE)` and `@Component` annotations preserved.
- The CONSUMER span lifecycle is now owned entirely by `TracingMessageListenerAdvice` (otel-bootstrap, plan 03-01) — Phase 5's MDC injector will continue to pick up `trace_id`/`span_id` for the `LOG.info` line because the advice's `Scope.makeCurrent()` is active on the same thread when the listener body runs (RESEARCH FLAG #1 verified).
- APP-04's 10% deterministic failure path (plan 03-04) now has a safe landing pad: `setDefaultRequeueRejected(false)` ensures failed messages drop instead of NACK-requeueing infinitely (D-13).
- Phase 2 BOM invariant preserved: `mise run verify:bom` exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.` Adding the intra-reactor otel-bootstrap dep introduced no version conflicts.

## Task Commits

Each task committed atomically with `--no-verify` (parallel-worktree convention):

1. **Task 1: pom.xml otel-bootstrap dep** — `063aa9d` (chore)
2. **Task 2: RabbitConfig 4 @Beans** — `9b0048b` (feat)
3. **Task 3: OrderListener thin pass-through** — `547c211` (refactor)
4. **Task 4: Reactor verification** — no source changes (verification only)

**Plan metadata commit:** to follow this SUMMARY.md write.

## Files Modified

- `consumer-service/pom.xml` — Added `<dependency>com.example:otel-bootstrap:${project.version}</dependency>` at top of `<dependencies>` block (BEFORE `spring-boot-starter-amqp`); 132 lines total (was 117); 8 existing deps preserved unchanged; default compile scope.
- `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` — Was 18 lines, now 98 lines. Added 6 imports (otel-bootstrap class, OpenTelemetry, Tracer, SimpleRabbitListenerContainerFactory, ConnectionFactory, SimpleRabbitListenerContainerFactoryConfigurer). Added 2 `@Bean` methods with rich JavaDoc referencing D-08 / D-13 / Pitfall #5 / Pitfall #7. The QUEUE constant + 2 existing beans preserved verbatim.
- `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` — Was 81 lines, now 54 lines. Deleted the inline CONSUMER span body (lines 46–79 in the original), the `Tracer` constructor param + field, and 9 OTel imports (Span, SpanKind, StatusCode, Tracer, Context, Scope, MessagingIncubatingAttributes + 2 nested enum imports). Replaced with class-level JavaDoc explaining the Phase 2→3 transition (D-09 + RESEARCH FLAG #1).

## Dependency Tree Confirmation

```
$ mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap
[INFO] --- dependency:3.7.0:tree (default-cli) @ consumer-service ---
[INFO] com.example:consumer-service:jar:0.1.0-SNAPSHOT
[INFO] \- com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT:compile
```

Exactly one line — otel-bootstrap is reached via the direct dependency, not transitively. Maven-enforcer's `dependencyConvergence` rule passes.

## RabbitConfig @Bean Inventory

| Bean | Method name | Type | Source |
|------|-------------|------|--------|
| `ordersCreatedQueue` | `ordersCreatedQueue` | `Queue` | Phase 2 (preserved) |
| `jsonMessageConverter` | `jsonMessageConverter` | `MessageConverter` | Phase 2 (preserved) |
| `tracingMessageListenerAdvice` | `tracingMessageListenerAdvice` | `TracingMessageListenerAdvice` | **Phase 3 NEW** |
| `rabbitListenerContainerFactory` | `rabbitListenerContainerFactory` | `SimpleRabbitListenerContainerFactory` | **Phase 3 NEW** (load-bearing exact name) |

`grep -c '^    @Bean$' RabbitConfig.java` = `4`.

## Order Verification (D-08 + Pitfall #5)

In `rabbitListenerContainerFactory(...)` source order:

1. `configurer.configure(factory, connectionFactory);` — Spring Boot defaults + `spring.rabbitmq.listener.simple.*` properties applied first.
2. `factory.setAdviceChain(tracingAdvice);` — tracing advice registered, overwriting whatever Configurer set on `adviceChain` (no-op in default config; load-bearing if anyone enables `spring.rabbitmq.listener.simple.retry.enabled=true`).
3. `factory.setDefaultRequeueRejected(false);` — APP-04 safety; failed messages drop instead of requeue.

Verified with awk source-order checks; both `c<a` and `a<r` pass.

## Build / Verification Outcomes

- `mvn -q -pl otel-bootstrap,consumer-service -am clean compile` — exit 0.
- `mvn -pl consumer-service test` — exit 0; `[INFO] No tests to run.` (Phase 1's scaffolding never produced `ConsumerApplicationTests` for the consumer; the only real verification of bean wiring is the human-verify smoke gate in plan 03-05). Build success demonstrates the application class + bean graph compiles.
- `mvn -q -pl consumer-service -am package -DskipTests` — exit 0; `consumer-service-0.1.0-SNAPSHOT.jar` (10,066 bytes) repackaged.
- `mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap` — single line: `\- com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT:compile`.
- `mise run verify:bom` — exit 0; output: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`
- `git status --porcelain consumer-service/` — empty (all 3 modified files committed; clean tree).
- `git diff --name-only base..HEAD -- consumer-service/` — exactly the 3 expected files (pom.xml, RabbitConfig.java, OrderListener.java).

## Decisions Made

Followed the plan and CONTEXT.md decisions D-08, D-09, D-13 verbatim. No semantic decisions made beyond the plan.

Architectural choices already locked (recap):

| Decision | Source | Application |
|----------|--------|-------------|
| Configurer-aided `@Bean SimpleRabbitListenerContainerFactory` (vs. raw constructor or `@AutoConfiguration` override) | D-08 | `rabbitListenerContainerFactory(ConnectionFactory, SimpleRabbitListenerContainerFactoryConfigurer, TracingMessageListenerAdvice)` |
| Bean method name MUST be `rabbitListenerContainerFactory` exactly | Pitfall #7 | Method name matches Spring Boot's `@ConditionalOnMissingBean(name=...)` string |
| Configure → setAdviceChain → setDefaultRequeueRejected order | Pitfall #5 + D-08 | Three sequential statements with line-number-asserted ordering |
| `setDefaultRequeueRejected(false)` | D-13 | APP-04 failures drop, no NACK-requeue storm; no DLX per PROJECT.md |
| `OrderListener` becomes thin pass-through; Tracer + OTel imports + inline span deleted | D-09 | 81 → 54 lines; `onOrder(Map)` body is 3 lines |
| `@RabbitListener(queues = RabbitConfig.QUEUE)` annotation preserved | D-09 | Spring AMQP picks the user-defined factory automatically by bean name |

## Deviations from Plan

None. The plan executed exactly as written. The two grep-only acceptance criteria that returned non-zero counts (`@(AutoConfiguration|ConditionalOn)` matched 1 in RabbitConfig.java; `spanBuilder|setSpanKind|...|makeCurrent|...` matched 1 in OrderListener.java) both matched against JavaDoc text — `RabbitConfig` JavaDoc references Spring Boot's own `@ConditionalOnMissingBean(name="rabbitListenerContainerFactory")` annotation (explaining the backoff mechanism), and `OrderListener` JavaDoc references the advice's `Scope.makeCurrent()` (explaining why MDC will still see the right trace_id). Neither match is in code; the criteria's intent ("we don't apply these annotations / inline spans ourselves") is met. Documenting here for transparency, not as a deviation.

## Authentication Gates

None.

## Auto-config Quirks Observed

None observed. `mvn package` repackaged cleanly without any "RabbitListenerContainerFactory backed off" log line in the build output (Spring Boot Maven plugin's repackage step doesn't bootstrap the application context, so it wouldn't surface that log anyway). True context-load verification is the human-verify smoke gate in plan 03-05; if at runtime the auto-config does NOT back off (i.e., the bean name was somehow wrong), `@RabbitListener` would resolve the auto-bean and the tracing advice would silently never fire — which is exactly the failure mode Pitfall #7 warns about. The exact-name check at acceptance time is the structural defense.

## Wave 2 Hand-off

**Producer side (plan 03-02):** Independent of this plan; both Wave 2 plans run in parallel. Once both merge, the runtime trace becomes ONE distributed trace (ROADMAP SC #1).

**Wave 3 (plan 03-04 — APP-04 failure path):** Now unblocked. The deterministic 10% throw site in `ProcessingService.process(...)` lands inside the existing D-03 try-block; the thrown `ProcessingFailedException` propagates up through the now-thin `OrderListener.onOrder` (no catch) → caught by `TracingMessageListenerAdvice.invoke`'s `catch (Throwable t)` → `recordException + setStatus(ERROR) + rethrow` on the CONSUMER span → Spring AMQP container NACKs → `defaultRequeueRejected=false` (set HERE in T2) drops the message. End-to-end safety net is in place.

**Wave 4 (plan 03-05 — README delta + exit gate):** Now unblocked once both 03-02 and 03-04 land. The human-verify smoke gate at 03-05 is where:
- Spring context-load is empirically verified against a real bean graph.
- A POST /orders is observed in Tempo as ONE trace with `consumer.parentSpanId == producer.spanId` (ROADMAP SC #1 materializes).
- Whether Spring Boot logs an "auto-config backed off" message can be observed.
- The `step-03-context-propagation` annotated tag lands at the user-approved gate.

## Phase 3 Hand-off Chain

| Wave | Plan | Status (post this plan) | What it delivers |
|------|------|-------------------------|------------------|
| 1 | 03-01 otel-bootstrap classes | DONE | The 4 propagation classes including `TracingMessageListenerAdvice` consumed here |
| 2 | 03-02 producer wiring | INDEPENDENT (parallel) | Producer side: registers `TracingMessagePostProcessor` on `RabbitTemplate.setBeforePublishPostProcessors(...)` |
| 2 | 03-03 consumer wiring | **THIS PLAN — DONE** | Consumer side: registers `TracingMessageListenerAdvice` on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` |
| 3 | 03-04 APP-04 failure path | UNBLOCKED | Consumer-side `ProcessingFailedException` + 10% throw site |
| 4 | 03-05 README + tag | UNBLOCKED (waits on 03-02 + 03-04) | Step 3 README + `step-03-context-propagation` annotated tag |

## TDD Gate Compliance

This plan has `type: execute` (not `type: tdd`). No RED→GREEN sequencing required. The plan's structural correctness is verified by `mvn compile` + `mvn package` + `dependency:tree` + grep-based acceptance gates; runtime correctness is the explicit responsibility of plan 03-05's human-verify smoke gate.

## Self-Check: PASSED

**File existence checks:**
- `consumer-service/pom.xml` — FOUND (modified, 132 lines)
- `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` — FOUND (modified, 98 lines, 4 @Bean methods)
- `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` — FOUND (modified, 54 lines, 0 OTel imports)
- `consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` — FOUND (10,066 bytes, repackaged after T4)

**Commit hash existence checks:**
- `063aa9d` (T1 pom.xml dep) — FOUND in `git log`
- `9b0048b` (T2 RabbitConfig wiring) — FOUND
- `547c211` (T3 OrderListener thin pass-through) — FOUND
