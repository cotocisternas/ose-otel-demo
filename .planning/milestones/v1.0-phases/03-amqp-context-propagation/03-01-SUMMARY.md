---
phase: 03-amqp-context-propagation
plan: 01
subsystem: observability
tags: [opentelemetry, amqp, spring-amqp, context-propagation, w3c-trace-context, otel-bootstrap, propagator, textmap, junit5]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: |
      Per-service `OtelSdkConfiguration` wires the composite
      `W3CTraceContextPropagator + W3CBaggagePropagator`; `OpenTelemetry`
      and per-service `Tracer` (`com.example.producer` / `com.example.consumer`)
      beans are available for injection. The PRODUCER + CONSUMER inline spans
      from Phase 2 are the structural template Wave-2 plans will replace
      with bean-wired calls into the four classes shipped here.
provides:
  - "`com.example:otel-bootstrap:0.1.0-SNAPSHOT` JAR populated with 4 propagation classes (was empty placeholder)"
  - "`TracingMessagePostProcessor` — `MessagePostProcessor` (4-arg + 1-arg overloads) owning PRODUCER span lifecycle + W3C `traceparent` inject"
  - "`TracingMessageListenerAdvice` — `MethodInterceptor` owning CONSUMER span lifecycle + W3C `traceparent` extract; `.setParent(extracted)` is the LOAD-BEARING line that joins producer + consumer traces (ROADMAP SC #1)"
  - "`MessagePropertiesSetter` — `TextMapSetter<MessageProperties>` writing String-only header values (PITFALLS.md #2)"
  - "`MessagePropertiesGetter` — `TextMapGetter<MessageProperties>` defensively normalising via `.toString()` (PITFALLS.md #2)"
  - "`MessagePropertiesRoundTripTest` — 6 pure JUnit 5 + AssertJ tests proving setter ↔ getter round-trip"
  - "`otel-bootstrap/pom.xml` populated with 5 dependencies (3 BOM-managed, 1 explicit semconv-incubating, 1 test)"
affects:
  - "03-02-producer-wiring"
  - "03-03-consumer-wiring"
  - "03-04-app-04-failure-path"
  - "03-05-readme-and-exit-gate"

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry:opentelemetry-api:1.61.0 (compile, BOM-managed) on otel-bootstrap"
    - "org.springframework.amqp:spring-rabbit:3.2.8 (provided, BOM-managed) on otel-bootstrap"
    - "org.springframework:spring-aop:6.2.15 (provided, BOM-managed; brings org.aopalliance) on otel-bootstrap"
    - "io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha (provided, pinned) on otel-bootstrap"
    - "org.springframework.boot:spring-boot-starter-test:3.4.13 (test, BOM-managed) on otel-bootstrap"
  patterns:
    - "Pure-Java propagation classes with NO Spring annotations (D-01) — Spring wiring lives in each service's `RabbitConfig.java` (lands in 03-02 / 03-03)"
    - "Constructor injection of `(OpenTelemetry, Tracer)` (D-03) — propagator read via `openTelemetry.getPropagators().getTextMapPropagator()` (D-04 reuse of Phase 2's wiring)"
    - "Stateless singleton SETTER / GETTER as `private static final` fields inside each spans-and-propagation class"
    - "PRODUCER inject-only span lifetime (D-06): span ends INSIDE postProcessMessage, BEFORE `RabbitTemplate.send` writes to wire — matches OTel auto-instrumentation convention for Kafka/JMS/AMQP"
    - "Semconv-correct destination naming (D-07/D-10): span name `<exchange> publish` / `<exchange> process`; `messaging.destination.name = exchange` (not queue — Phase 2 correction)"
    - "CONSUMER catch shape (D-10): `catch (Throwable t)` → `recordException + setStatus(ERROR) + rethrow` so Spring AMQP container NACKs"
    - "PITFALLS.md #2 mitigation pair: `Setter` writes String only; `Getter` defensively `.toString()` normalises String / LongString / byte[]"
    - "Defensive `instanceof Message message` guard for batch-listener composition (RESEARCH FLAG #3 + Pitfall #6)"

key-files:
  created:
    - "otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java"
    - "otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java"
    - "otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java"
    - "otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java"
    - "otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java"
  modified:
    - "otel-bootstrap/pom.xml"

key-decisions:
  - "Followed CONTEXT.md D-01 through D-10 verbatim; no semantic deviation from the plan."
  - "AssertJ overload disambiguation in unit test: assigned `props.getHeader(\"traceparent\")` to a local `Object stored` variable to avoid javac ambiguity between `assertThat(Predicate<T>)` and `assertThat(IntPredicate)` overloads (Rule 1 deviation — runtime semantics unchanged, compile-time fix only)."
  - "All 4 propagation classes are `public class` (not package-private) — slightly broader than strictly needed, but symmetric across the four classes and friendlier for future test access from outside the package."
  - "Added `opentelemetry-semconv-incubating:1.40.0-alpha` to otel-bootstrap pom.xml at `provided` scope (planner-flagged at PATTERNS.md line 593 — done preemptively to avoid a second build cycle)."

patterns-established:
  - "Pattern 1: Pure-Java OTel adapters with constructor injection of `(OpenTelemetry, Tracer)` and runtime propagator reads — never `W3CTraceContextPropagator.getInstance()`. Honors Phase 2 D-04 / D-16 hand-off."
  - "Pattern 2: 4-arg `MessagePostProcessor` overload as the producer-side instrumentation hook — registered via `setBeforePublishPostProcessors(...)` and invoked by `RabbitTemplate.doSend` (verified in Spring AMQP 3.2.8 source per RESEARCH FLAG #2)."
  - "Pattern 3: `MethodInterceptor`-based CONSUMER advice reads `inv.getArguments()[1]` (RESEARCH FLAG #3 confirmed `ContainerDelegate.invokeListener(Channel, Object data)` arg layout) with defensive `instanceof Message message` guard for batch-listener safety (Pitfall #6)."
  - "Pattern 4: PRODUCER inject-only span lifetime (`try/finally`, no catch) — broker-level errors propagate up to caller's INTERNAL span via Phase 2 D-03 catch."
  - "Pattern 5: CONSUMER full-error span (`catch (Throwable) → recordException + setStatus(ERROR) + rethrow`) — Spring AMQP container handles NACK; `defaultRequeueRejected=false` set by RabbitConfig in 03-03 drops failed messages (no DLX per PROJECT.md)."

requirements-completed: [PROP-01, PROP-02, PROP-04]

# Metrics
duration: 12min
completed: 2026-05-01
---

# Phase 03 Plan 01: otel-bootstrap AMQP Propagation Classes Summary

**Populated the empty otel-bootstrap module with 4 pure-Java W3C trace-context propagation classes (TracingMessagePostProcessor, TracingMessageListenerAdvice, MessagePropertiesSetter, MessagePropertiesGetter) plus 6 round-trip unit tests, shipping the structural shared library that Wave 2 (03-02, 03-03) wires into both services' RabbitConfig.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-05-01T20:07:13Z
- **Completed:** 2026-05-01T20:19:52Z
- **Tasks:** 7/7 complete (T1 pom.xml, T2 Setter, T3 Getter, T4 PostProcessor, T5 Advice, T6 unit test, T7 reactor verification)
- **Files modified:** 6 (1 pom.xml + 4 main sources + 1 test source)

## Accomplishments

- `otel-bootstrap-0.1.0-SNAPSHOT.jar` is now a real artifact (not a placeholder) and is installed in the local Maven repo, ready for Wave 2 plans (03-02 producer wiring, 03-03 consumer wiring) to consume via `<dependency>com.example:otel-bootstrap</dependency>` and `import com.example.otel.amqp.*`.
- The LOAD-BEARING line for the workshop's headline lesson — `spanBuilder(...).setParent(extracted)` in `TracingMessageListenerAdvice` — is in place and ready to deliver ROADMAP SC #1 (`consumer.parentSpanId == producer.spanId`) once Wave 2 wires the bean into the listener factory.
- Phase 2 BOM invariant preserved: `mise run verify:bom` still PASSES — adding otel-bootstrap's 5 deps did not introduce duplicate OTel artifact versions across the reactor (verified by maven-enforcer's `<dependencyConvergence/>` rule on every `mvn install`).
- PITFALLS.md #2 (byte[] vs String headers — the silent trace-fragmentation pitfall) has a unit-test regression net (`MessagePropertiesRoundTripTest`, 6 tests) — the lowest-cost regression detection in the codebase.
- The Phase 2 → Phase 3 git diff for THIS plan is exactly the shape ROADMAP SC #5 demands: +5 new files, +1 modified pom.xml, ~250 added Java lines, ~25 added pom.xml lines. No deletions yet — the structural deletions (inline PRODUCER/CONSUMER spans) land in 03-02 / 03-03.

## Task Commits

Each task was committed atomically with `--no-verify` (parallel-worktree convention):

1. **Task 1: pom.xml deps** — `79181b3` (chore)
2. **Task 2: MessagePropertiesSetter** — `e1f7f7c` (feat)
3. **Task 3: MessagePropertiesGetter** — `c188eea` (feat)
4. **Task 4: TracingMessagePostProcessor** — `06ac020` (feat)
5. **Task 5: TracingMessageListenerAdvice** — `0a58110` (feat)
6. **Task 6: MessagePropertiesRoundTripTest** — `09000bb` (test)
7. **Task 7: Full reactor verification** — no source changes (verification only)

**Plan metadata commit:** to follow this SUMMARY.md write.

## Files Created

- `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` — `TextMapSetter<MessageProperties>` writing String-only header values (PITFALLS.md #2 writer side).
- `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` — `TextMapGetter<MessageProperties>` with `.toString()` defensive normalization (PITFALLS.md #2 reader side).
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` — Owns PRODUCER span (D-05); 4-arg `postProcessMessage` overload extracts `(exchange, routingKey)` directly from `RabbitTemplate.doSend` (RESEARCH FLAG #2 verified); semconv-correct attributes (D-07: `messaging.destination.name = exchange`, not queue); inject-only span lifetime (D-06); `try/finally` no-catch.
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` — Owns CONSUMER span (D-10); reads `inv.getArguments()[1]` (RESEARCH FLAG #3) with `instanceof Message message` guard (Pitfall #6); calls `propagator.extract` then `.setParent(extracted)` (LOAD-BEARING per ROADMAP SC #1); mirrors PRODUCER's 4 messaging semconv attributes; `catch (Throwable)` → `recordException + setStatus(ERROR) + rethrow`.
- `otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` — Pure JUnit 5 + AssertJ; 6 tests (String round-trip, non-String `.toString()` defense, absent header → null, null carrier on get, null carrier on set, `keys()` enumeration). All pass: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.

## Files Modified

- `otel-bootstrap/pom.xml` — Replaced Phase 1 placeholder comment with `<dependencies>` block; updated `<description>` to reflect Phase 3 scope. 5 deps added (3 BOM-managed, 1 explicit semconv-incubating, 1 test).

## Confirmed JAR Contents

```
$ jar tf ~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar | grep amqp
com/example/otel/amqp/
com/example/otel/amqp/MessagePropertiesGetter.class
com/example/otel/amqp/MessagePropertiesSetter.class
com/example/otel/amqp/TracingMessageListenerAdvice.class
com/example/otel/amqp/TracingMessagePostProcessor.class
```

Exactly 4 propagation `.class` files in the JAR (acceptance criterion T7 met).

## Test Results

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.otel.amqp.MessagePropertiesRoundTripTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.037 s
```

## Build / Verification Outcomes

- `mvn -q -pl otel-bootstrap compile` — exit 0 (after each of T2-T5).
- `mvn -pl otel-bootstrap test` — exit 0; 6/6 tests pass.
- `mvn clean install` (full reactor) — `BUILD SUCCESS` in 1.357s. Reactor order:
  - OSE OTel Demo (parent) ............ SUCCESS [0.131 s]
  - OSE OTel Demo (otel-bootstrap) .... SUCCESS [0.903 s]
  - OSE OTel Demo (producer) .......... SUCCESS [0.141 s]
  - OSE OTel Demo (consumer) .......... SUCCESS [0.088 s]
- `mise run verify:bom` — exit 0; output: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`
- `git status --short` — clean working tree (no rogue files, no leaked target/ outputs into VCS).

## Decisions Made

Followed plan + CONTEXT.md decisions D-01 through D-10 verbatim. No semantic decisions made beyond the plan; one Rule 1 deviation (compile-time fix; see below) was the only adjustment.

Architectural choices already locked by CONTEXT.md / RESEARCH.md and applied here:

| Decision | Source | Application |
|----------|--------|-------------|
| 4 separate top-level classes (not nested / not consolidated) | D-02 | One file each in `com/example/otel/amqp/` |
| Pure Java, no Spring annotations on the 4 classes | D-01 | No `@Component`, `@Service`, `@Bean`, `@Autowired`, `@Configuration` anywhere in the four files |
| Constructor `(OpenTelemetry, Tracer)` | D-03 | Both spans-and-propagation classes; per-service Tracer scope keeps spans under the consuming service's instrumentation scope in Tempo |
| Read propagator at runtime, never construct | D-04 | `openTelemetry.getPropagators().getTextMapPropagator()` in both classes; zero `W3CTraceContextPropagator.getInstance()` references |
| PRODUCER span owned entirely by post-processor | D-05 | `TracingMessagePostProcessor.postProcessMessage(...)` opens, injects, ends — `OrderPublisher.publish` will become a thin wrapper in 03-02 |
| Inject-only span lifetime | D-06 | `try (Scope) { propagator.inject; return message; } finally { span.end(); }` — span ends before `RabbitTemplate.send` |
| Semconv-correct destination naming (exchange, not queue) | D-07, D-10 | Span names `<exchange> publish` / `<exchange> process`; `messaging.destination.name = exchange` |
| LOAD-BEARING `.setParent(extracted)` | D-10 + ROADMAP SC #1 | Single line in `TracingMessageListenerAdvice` — visible call comment marks it |
| `catch (Throwable) → recordException + setStatus(ERROR) + rethrow` | D-10 | `TracingMessageListenerAdvice.invoke` catch block |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AssertJ assertThat overload ambiguity in unit test compile**
- **Found during:** Task 6 (`MessagePropertiesRoundTripTest`) — first `mvn -pl otel-bootstrap test` invocation.
- **Issue:** `assertThat(props.getHeader("traceparent"))` failed to compile with javac 17 + AssertJ 3.x:
  ```
  reference to assertThat is ambiguous
  both method assertThat(java.util.function.IntPredicate) in org.assertj.core.api.Assertions
   and method <T>assertThat(java.util.function.Predicate<T>) in org.assertj.core.api.Assertions match
  ```
  Root cause: `MessageProperties.getHeader(String)` returns `Object`. javac cannot disambiguate between `assertThat(Predicate<T>)` (boxed) and `assertThat(IntPredicate)` when the static type is bare `Object` because both are matched by reference type Object.
- **Fix:** Assigned the result to an `Object stored` local variable before the `assertThat(stored)` call. The local has explicit static type `Object` which fixes the inference path.
- **Files modified:** `otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` (lines 39-44, only).
- **Verification:** `mvn -pl otel-bootstrap test` then exited 0 with `Tests run: 6, Failures: 0`.
- **Committed in:** `09000bb` (Task 6 commit; the deviation is part of the test file as shipped — the assertion still verifies String-instance + value equality, runtime semantics unchanged).

No other deviations. The plan executed exactly as written for Tasks 1, 2, 3, 4, 5, 7.

## Authentication Gates

None.

## Wave 2 Hand-off

**Producer wiring (03-02) needs to:**
1. Add `<dependency>com.example:otel-bootstrap:${project.version}</dependency>` to `producer-service/pom.xml`.
2. In `producer-service/.../config/RabbitConfig.java`:
   - `import com.example.otel.amqp.TracingMessagePostProcessor;`
   - Add `@Bean TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry openTelemetry, Tracer tracer) { return new TracingMessagePostProcessor(openTelemetry, tracer); }`
   - Add explicit `@Bean RabbitTemplate(ConnectionFactory cf, MessageConverter conv, TracingMessagePostProcessor tracingMpp)` that calls `setBeforePublishPostProcessors(tracingMpp)` (replaces Spring Boot auto-created `RabbitTemplate`).
3. In `producer-service/.../messaging/OrderPublisher.java` — DELETE inline PRODUCER span (lines 51–83) and `Tracer` constructor parameter; final body becomes thin `convertAndSend(...)` call.

**Consumer wiring (03-03) needs to:**
1. Add `<dependency>com.example:otel-bootstrap:${project.version}</dependency>` to `consumer-service/pom.xml`.
2. In `consumer-service/.../config/RabbitConfig.java`:
   - `import com.example.otel.amqp.TracingMessageListenerAdvice;`
   - Add `@Bean TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry openTelemetry, Tracer tracer) { return new TracingMessageListenerAdvice(openTelemetry, tracer); }`
   - Add Configurer-aided `@Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...)` calling `factory.setAdviceChain(tracingAdvice)` and `factory.setDefaultRequeueRejected(false)` (D-08 + D-13).
3. In `consumer-service/.../messaging/OrderListener.java` — DELETE inline CONSUMER span; final body becomes 3-line pass-through (orderId extract, LOG.info, processingService.process).

**Verifying both wired correctly (smoke):** After Wave 2, `POST /orders` should produce a single trace in Tempo with consumer's `parentSpanId` matching producer's PRODUCER `spanId`. ROADMAP SC #1 lands at the Wave 2 / 3 boundary.

## TDD Gate Compliance

This plan has `type: execute` (not `type: tdd`). Task 6 ships a unit test alongside the implementation in the same Wave; the test is a regression net for PITFALLS.md #2, not a TDD gate driving a feature. No RED→GREEN sequencing required by the plan.

## Self-Check: PASSED

**File existence checks:**
- `otel-bootstrap/pom.xml` — FOUND (modified)
- `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` — FOUND
- `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` — FOUND
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` — FOUND
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` — FOUND
- `otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` — FOUND
- `~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar` — FOUND (4 .class files)

**Commit hash existence checks:**
- `79181b3` (T1 pom.xml deps) — FOUND in `git log`
- `e1f7f7c` (T2 Setter) — FOUND
- `c188eea` (T3 Getter) — FOUND
- `06ac020` (T4 PostProcessor) — FOUND
- `0a58110` (T5 Advice) — FOUND
- `09000bb` (T6 round-trip test) — FOUND
