---
phase: 03-amqp-context-propagation
plan: 04
subsystem: observability
tags: [opentelemetry, app-04, trace-09, record-exception, set-status-error, processing-failed-exception, atomic-counter, deterministic-failure, span-error-status]

# Dependency graph
requires:
  - phase: 03-amqp-context-propagation
    plan: 01
    provides: |
      `TracingMessageListenerAdvice` shipped in plan 03-01 owns the CONSUMER
      span and `catch (Throwable t) → recordException + setStatus(ERROR) +
      rethrow`. The rethrown `ProcessingFailedException` from THIS plan is
      caught by that advice catch and surfaces as ERROR on the CONSUMER span
      in Tempo (TRACE-09).
  - phase: 03-amqp-context-propagation
    plan: 03
    provides: |
      `consumer-service`'s `RabbitConfig.rabbitListenerContainerFactory(...)`
      sets `defaultRequeueRejected=false` (D-13) — when the
      `ProcessingFailedException` from THIS plan rethrows past the listener
      and the AMQP container NACKs, the broker drops the message instead of
      requeueing it (no NACK-storm). Plan 03-03 also thinned
      `OrderListener.onOrder` to a no-catch pass-through, so the exception
      propagates cleanly from `ProcessingService.process` up to the advice.
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: |
      `ProcessingService.process(...)`'s D-01 inline INTERNAL span and the
      forward-compatible D-03 `catch (RuntimeException e) → recordException
      + setStatus(ERROR) + rethrow` were ALREADY in place — Phase 3's edit
      here is purely additive (no catch-shape restructuring).
provides:
  - "APP-04: deterministic 10%-failure trigger — `ProcessingService.process(...)` throws `ProcessingFailedException` on every 10th call (counter % 10 == 0)"
  - "TRACE-09 on the INTERNAL span: Phase 2's existing D-03 catch wraps the throw site (recordException + setStatus(ERROR) + rethrow on `ProcessingService.process` span)"
  - "TRACE-09 on the CONSUMER span: the rethrown exception bubbles up through the thin `OrderListener.onOrder` (plan 03-03) into `TracingMessageListenerAdvice` (plan 03-01) — recordException + setStatus(ERROR) + rethrow on `<exchange> process` span"
  - "`com.example.consumer.domain.ProcessingFailedException` — public class extending `RuntimeException` with single-arg `(String)` constructor; FQCN surfaces as `exception.type` on the recordException span event in Tempo (D-12)"
  - "`AtomicInteger counter` instance field on the `@Service`-singleton `ProcessingService` — persists across messages within one JVM run; resets per `mise run dev` start"
  - "End-to-end fail-closed chain: throw → INTERNAL span ERROR → rethrow → advice catches → CONSUMER span ERROR → rethrow → AMQP container NACKs → broker drops (no DLX)"
affects:
  - "03-05-readme-and-exit-gate"

# Tech tracking
tech-stack:
  added:
    - "java.util.concurrent.atomic.AtomicInteger (JDK; new import on ProcessingService)"
  patterns:
    - "AtomicInteger-on-singleton counter pattern (D-11) — Spring `@Service` singleton scope provides cross-message persistence within a JVM run without static state or a second bean"
    - "Custom domain exception as documentation surface (D-12) — `ProcessingFailedException`'s FQCN appears as `exception.type` in Tempo's recordException span event panel; the class name itself is workshop documentation"
    - "Phase-2 forward-compatible catch shape pays off: D-03's `catch (RuntimeException e) → recordException + setStatus(ERROR) + rethrow` written in Phase 2 ahead of any throw site catches `ProcessingFailedException extends RuntimeException` automatically; ZERO restructuring required"
    - "Two-layer recordException coverage (D-11 + plan 03-01): INTERNAL span (D-03 catch) AND CONSUMER span (advice catch) both record + set ERROR — Tempo shows ERROR status on BOTH spans for the 10th order"
    - "Verbatim APP-04 message wording: `\"Deterministic failure on order #\" + n + \" (every 10th order)\"` — the exact text matches REQUIREMENTS.md APP-04"

key-files:
  created:
    - "consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java"
  modified:
    - "consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"

key-decisions:
  - "Followed the plan + CONTEXT.md decisions D-11 and D-12 verbatim. No semantic deviation."
  - "ProcessingFailedException declared `public` (over `package-private`) per plan recommendation — symmetric with the rest of the demo's domain classes and friendlier for any future cross-package test access."
  - "Used `Write` to replace the entire ProcessingService.java file (rather than multi-step Edit) because the change touches imports, class JavaDoc, a new field, and the try-block body — a near-total rewrite per the plan's verbatim file content block."
  - "T3 captured a side-discovery: the worktree's `mise.toml` was untrusted by mise (parallel-worktree convention); resolved via `mise trust` before re-running `mise run verify:bom`. Documented as a deviation (Rule 3 — blocking issue: tooling environment) for downstream waves."

patterns-established:
  - "Pattern 1: Throw site INSIDE the existing D-03 try-block — placement matters (must land before the catch). awk-based source-order check (`try` line < `throw` line < `} catch` line) verifies it structurally."
  - "Pattern 2: Same-package no-import simplicity — `ProcessingFailedException` lives in `com.example.consumer.domain` alongside `ProcessingService`, so no import statement is required (and verifying its absence catches accidental cross-package drift)."
  - "Pattern 3: Two-span ERROR propagation by structure, not by configuration — once Plan 03-01's advice catches `(Throwable)` and Plan 03-03 made `OrderListener.onOrder` no-catch, the SECOND ERROR status is ZERO additional code. This plan's throw site activates both."

requirements-completed: [APP-04, TRACE-09]

# Metrics
duration: 3min
completed: 2026-05-01
---

# Phase 03 Plan 04: APP-04 Failure Path Summary

**Wired APP-04's deterministic 10%-failure path: shipped a new `ProcessingFailedException` class (extends `RuntimeException`) and added an `AtomicInteger counter` + `n % 10 == 0` throw site INSIDE the Phase-2 D-03 try-block in `ProcessingService.process(...)`. The Phase 2 catch shape (recordException + setStatus(ERROR) + rethrow on the INTERNAL span) and Phase 3 plan 03-01's advice catch shape (recordException + setStatus(ERROR) + rethrow on the CONSUMER span) handle TRACE-09 on BOTH spans automatically — net delta is +25 added Java lines + 1 new 30-line file, no restructuring of any existing catch block.**

## Performance

- **Duration:** ~3 min (144s)
- **Started:** 2026-05-01T20:33:35Z
- **Completed:** 2026-05-01T20:35:59Z
- **Tasks:** 3/3 complete (T1 ProcessingFailedException, T2 ProcessingService throw site, T3 reactor verification)
- **Files modified:** 2 (1 new + 1 modified)

## Accomplishments

- `ProcessingFailedException` exists at the canonical path, extends `RuntimeException`, has a single-arg `(String message)` constructor calling `super(message)`. No serialVersionUID, no cause-chain constructor, no Spring annotations — exactly the D-12 spec.
- `ProcessingService.process(...)` has a NEW `private final AtomicInteger counter = new AtomicInteger();` instance field. Spring `@Service` singleton scope ensures the counter persists across messages within one JVM run; the workshop demo can reliably "POST 10 orders → see one fail" pattern.
- The throw site lives INSIDE the existing D-03 try-block (verified structurally via `awk` source-order check: `try (Scope ...)` line < `throw new ProcessingFailedException` line < `} catch (RuntimeException` line). Zero restructuring of the catch block was required.
- The verbatim APP-04 message text — `"Deterministic failure on order #" + n + " (every 10th order)"` — matches REQUIREMENTS.md APP-04 character-for-character.
- TRACE-09 is wired on TWO spans automatically (no extra code in this plan): INTERNAL via Phase 2's D-03 catch on `ProcessingService.process`, CONSUMER via plan 03-01's advice catch on `<exchange> process`. ROADMAP SC #3 ("the workshop demo shows ERROR status on the trace's consumer span with the exception event attached") is structurally satisfied.
- The end-to-end fail-closed chain is now complete: ProcessingFailedException thrown → INTERNAL span ERROR + recordException → rethrow → propagates through thin `OrderListener.onOrder` (plan 03-03 — no catch) → caught by `TracingMessageListenerAdvice` → CONSUMER span ERROR + recordException → rethrow → Spring AMQP container NACKs → `defaultRequeueRejected=false` (plan 03-03 D-13) → broker drops (no DLX per PROJECT.md). No NACK-storm risk (T-3-04-01 mitigated).
- Phase 2 BOM invariant preserved: `mise run verify:bom` exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.` Adding the AtomicInteger import (JDK class) introduced no version conflicts.
- Consumer JAR repackaged cleanly (11,009 bytes; up from 10,066 bytes after plan 03-03 — accounting for the new class + expanded ProcessingService bytecode + JavaDoc).

## Task Commits

Each task committed atomically with `--no-verify` (parallel-worktree convention):

1. **Task 1: ProcessingFailedException.java** — `c8b9cac` (feat)
2. **Task 2: ProcessingService.java throw site** — `44a141b` (feat)
3. **Task 3: Reactor verification** — no source changes (verification only)

**Plan metadata commit:** to follow this SUMMARY.md write.

## Files Created

- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` (30 lines):

```java
package com.example.consumer.domain;

/**
 * Thrown by {@link ProcessingService#process(java.util.Map)} on the
 * deterministic 10%-failure path (APP-04 — every 10th order fails).
 *
 * <p><strong>Pedagogical value (CONTEXT.md D-12).</strong> The
 * fully-qualified class name {@code com.example.consumer.domain.ProcessingFailedException}
 * surfaces as the {@code exception.type} attribute on the
 * {@code recordException} span event in Tempo (TRACE-09); the class
 * name itself is documentation. Workshop attendees opening the
 * 10th-order trace see the FQCN at the top of the exception event panel.
 *
 * <p>Extends {@link RuntimeException} (unchecked) so the listener thread
 * can rethrow it without a {@code throws} declaration on
 * {@code @RabbitListener public void onOrder(Map)}. Caught by Phase 2's
 * D-03 catch on the INTERNAL span (records + ERROR + rethrow); then
 * caught by Phase 3's {@code TracingMessageListenerAdvice} catch on the
 * CONSUMER span (records + ERROR + rethrow); then caught by Spring AMQP's
 * listener container — combined with {@code defaultRequeueRejected=false}
 * (D-13), the broker drops the message (no DLX per PROJECT.md).
 *
 * <p>No cause chain (single-arg constructor) — this is a deterministic
 * synthetic failure, not a wrap-and-rethrow.
 */
public class ProcessingFailedException extends RuntimeException {
    public ProcessingFailedException(String message) {
        super(message);
    }
}
```

## Files Modified

- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` — was 54 lines, now 80 lines (+26 lines including expanded class JavaDoc).

### Diff snippet (the 3 added regions)

```diff
@@ src/main/java/com/example/consumer/domain/ProcessingService.java @@
 package com.example.consumer.domain;

 import java.util.Map;
+import java.util.concurrent.atomic.AtomicInteger;

 import io.opentelemetry.api.trace.Span;
 import io.opentelemetry.api.trace.SpanKind;
@@ class JavaDoc expanded — Phase 3 paragraph added; remainder unchanged @@

 @Service
 public class ProcessingService {
     private final Tracer tracer;

+    // Phase 3: deterministic 10%-failure trigger (APP-04 + D-11). Spring
+    // @Service is singleton scope by default — the counter persists across
+    // messages within one JVM run; resets per `mise run dev` start (fine
+    // for fresh demo sessions).
+    private final AtomicInteger counter = new AtomicInteger();
+
     public ProcessingService(Tracer tracer) {
         this.tracer = tracer;
     }

     public void process(Map<String, Object> order) {
         // ---- D-01 inline span template (INTERNAL) ----
         Span span = tracer.spanBuilder("ProcessingService.process")
             .setSpanKind(SpanKind.INTERNAL)
             .startSpan();
         try (Scope scope = span.makeCurrent()) {
-            // Phase 1: simulated domain work, in-memory only.
-            // Phase 3 wires up the deterministic 10% failure path (APP-04).
+            // Phase 3: deterministic 10%-failure trigger (APP-04 + D-11).
+            // Verbatim message wording from APP-04: "every 10th order".
+            int n = counter.incrementAndGet();
+            if (n % 10 == 0) {
+                throw new ProcessingFailedException(
+                    "Deterministic failure on order #" + n + " (every 10th order)");
+            }
+            // Successful processing path — Phase 1 placeholder retained
+            // (simulated domain work, in-memory).
         } catch (RuntimeException e) {
             span.recordException(e);
             span.setStatus(StatusCode.ERROR);
             throw e;
         } finally {
             span.end();
         }
     }
 }
```

The D-03 catch block (recordException + setStatus(ERROR) + throw e) is preserved character-for-character; only the placeholder body was replaced with the throw site.

### Line count delta

| Metric | Before | After | Delta |
|--------|--------|-------|-------|
| Total file lines | 54 | 80 | +26 |
| Added regions | — | 3 (import + field + throw site) | — |

### Structural source-order check

```
$ awk '/try \(Scope/{t=NR} /throw new ProcessingFailedException/{th=NR} /} catch \(RuntimeException/{c=NR} END{print "try="t, "throw="th, "catch="c; exit (t<th && th<c)?0:1}' \
    consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
try=58 throw=64 catch=70
```

Throw site is correctly INSIDE the try-block (between `try (Scope ...)` line 58 and `} catch (RuntimeException` line 70).

## Confirmed JAR Contents

```
$ jar tf consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar | grep -E '(ProcessingFailedException|ProcessingService|OrderListener)'
com/example/consumer/domain/ProcessingFailedException.class
com/example/consumer/domain/ProcessingService.class
com/example/consumer/messaging/OrderListener.class
```

`ProcessingFailedException.class` and the recompiled `ProcessingService.class` are both present in the consumer's repackaged JAR (11,009 bytes).

## Test Results

```
[INFO] --- surefire:3.2.5:test (default-test) @ consumer-service ---
[INFO] No tests to run.
```

`consumer-service` has no test sources (Phase 1 scaffolding never produced `ConsumerApplicationTests` for the consumer; same situation Plan 03-03 observed). `mvn -pl consumer-service test` exits 0 — acceptance criterion satisfied. The plan-stated "contextLoads test passes" criterion is structurally inapplicable; runtime context-load verification is the explicit responsibility of plan 03-05's human-verify smoke gate.

The clean reactor build of `otel-bootstrap + consumer-service` exited SUCCESS:

```
[INFO] OSE OTel Demo (parent) ............................. SUCCESS [  0.109 s]
[INFO] OSE OTel Demo (otel-bootstrap) ..................... SUCCESS [  0.812 s]
[INFO] OSE OTel Demo (consumer) ........................... SUCCESS [  0.121 s]
[INFO] BUILD SUCCESS
```

— which proves: ProcessingFailedException + ProcessingService compile against the existing OTel API + JDK; the consumer's bean graph (already validated at compile-time by Spring's annotation processors via spring-boot-starter-test on the test classpath at otel-bootstrap test compile) takes the new field initializer (`new AtomicInteger()` is a literal, not a DI requirement); no new bean definitions or wiring needed.

## Build / Verification Outcomes

- `mvn -q -pl otel-bootstrap,consumer-service -am clean test` — exit 0; reactor SUCCESS (3/3 modules).
- `mvn -pl consumer-service -am package -DskipTests` — exit 0; `consumer-service-0.1.0-SNAPSHOT.jar` (11,009 bytes) repackaged successfully.
- `jar tf` confirms `ProcessingFailedException.class` and `ProcessingService.class` both in the JAR.
- `mise run verify:bom` — exit 0; output: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`
- `git status --porcelain` — clean working tree (both files committed; no untracked).
- `git diff --name-only base..HEAD -- consumer-service/` — exactly 2 files: `ProcessingFailedException.java` (new), `ProcessingService.java` (modified).

## Decisions Made

Followed the plan and CONTEXT.md decisions D-11 (AtomicInteger counter + throw site inside D-03 try-block + verbatim APP-04 message wording) and D-12 (custom ProcessingFailedException extending RuntimeException, single-arg constructor, no cause chain, no serialVersionUID) verbatim. No semantic decisions made beyond the plan.

| Decision | Source | Application |
|----------|--------|-------------|
| AtomicInteger as instance field on `@Service` singleton | D-11 | `private final AtomicInteger counter = new AtomicInteger();` |
| Throw site INSIDE existing D-03 try-block (no catch restructuring) | D-11 | `int n = counter.incrementAndGet(); if (n % 10 == 0) throw new ProcessingFailedException(...)` lands between the `try` and `catch` lines |
| Verbatim APP-04 message wording | REQUIREMENTS.md APP-04 | `"Deterministic failure on order #" + n + " (every 10th order)"` |
| Custom domain exception extends RuntimeException | D-12 | `public class ProcessingFailedException extends RuntimeException` |
| Single-arg `(String message)` constructor; no cause chain | D-12 | `public ProcessingFailedException(String message) { super(message); }` |
| No `serialVersionUID` (modern Java practice for non-serialized exceptions) | D-12 | Field absent |
| Class name is documentation — appears as `exception.type` in Tempo | D-12 + ROADMAP SC #3 | Public class name + JavaDoc explicitly references the pedagogical surface |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `mise run verify:bom` initially failed with untrusted-config error**

- **Found during:** Task 3 (verification step running `mise run verify:bom`).
- **Issue:** The worktree's `mise.toml` (a copy of the main repo's `mise.toml`, brought into the worktree by the parallel-execution scaffolding) was not on mise's trust list:
  ```
  mise ERROR error parsing config file: ~/.../worktrees/agent-a67bd168968236ce3/mise.toml
  mise ERROR Config files in ~/.../worktrees/agent-a67bd168968236ce3/mise.toml are not trusted.
  mise ERROR Trust them with `mise trust`.
  ```
- **Fix:** Ran `mise trust` from the worktree root (output: `mise trusted /home/coto/dev/demo/ose-otel-demo/.claude/worktrees/agent-a67bd168968236ce3`). Re-ran `mise run verify:bom` — exited 0 with the expected `Phase 2 baseline confirmed` message.
- **Files modified:** None (mise's trust list is per-user state, not in-repo).
- **Verification:** `mise run verify:bom` then exited 0; BOM invariant confirmed.
- **Why it's a deviation, not a plan flaw:** The plan reasonably assumed the standard verification environment. The parallel-worktree convention sometimes invalidates `mise trust` because the path differs from the main checkout. Future executor agents working in fresh worktrees should expect to `mise trust` before invoking any `mise run` task. This is a parallel-execution scaffolding concern, not a Phase 3 design issue.

No other deviations. Tasks T1, T2, and the substantive verification commands in T3 executed exactly as written.

## Authentication Gates

None.

## Wave 4 Hand-off

**Plan 03-05 (the LAST plan in Phase 3 — README delta + exit gate) is now unblocked.** All 4 source-level Phase 3 plans (03-01 propagation classes, 03-02 producer wiring, 03-03 consumer wiring, 03-04 APP-04 failure path) are complete; the runtime trace topology + error span behavior should now materialize end-to-end when the human-verify smoke gate runs.

**What plan 03-05 needs to verify at the smoke gate (against a real broker + Tempo):**

1. **ROADMAP SC #1**: A single `POST /orders` produces ONE distributed trace in Tempo with `consumer.parentSpanId == producer.spanId` (W3C `traceparent` extracted from the AMQP message header).
2. **ROADMAP SC #3**: After 10 sequential `POST /orders` calls, the 10th-order trace shows ERROR status on BOTH the CONSUMER span (`<exchange> process`) AND the INTERNAL span (`ProcessingService.process`); the recordException event panel shows `exception.type = com.example.consumer.domain.ProcessingFailedException` and `exception.message = "Deterministic failure on order #10 (every 10th order)"`.
3. **Auto-config backoff**: Spring Boot's `RabbitAnnotationDrivenConfiguration` should log "back off" for `rabbitListenerContainerFactory` (the user-defined bean from plan 03-03 wins via `@ConditionalOnMissingBean(name=...)` — Pitfall #7 honored).
4. **Failed-message drop**: After the 10th order, the message should NOT be requeued (RabbitMQ management UI on `:15672` should show 0 messages in `orders.created` queue post-NACK; `defaultRequeueRejected=false` from plan 03-03 D-13 + APP-04's PFE rethrow combine to drop).

**Plan 03-05 also needs to write README delta + the user-approved annotated git tag** (`step-03-context-propagation`).

## Phase 3 Hand-off Chain (post this plan)

| Wave | Plan | Status | What it delivers |
|------|------|--------|------------------|
| 1 | 03-01 otel-bootstrap classes | DONE | The 4 propagation classes + 6 round-trip tests |
| 2 | 03-02 producer wiring | DONE | Producer side: `TracingMessagePostProcessor` registered on `RabbitTemplate.setBeforePublishPostProcessors(...)`; thin `OrderPublisher.publish` |
| 2 | 03-03 consumer wiring | DONE | Consumer side: `TracingMessageListenerAdvice` registered on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)`; `defaultRequeueRejected=false`; thin `OrderListener.onOrder` |
| 3 | 03-04 APP-04 failure path | **THIS PLAN — DONE** | `ProcessingFailedException` + AtomicInteger + 10% throw site inside D-03 try-block |
| 4 | 03-05 README + tag | UNBLOCKED | Step 3 README delta + `step-03-context-propagation` annotated tag |

## TDD Gate Compliance

This plan has `type: execute` (not `type: tdd`). No RED→GREEN sequencing required. The plan's structural correctness is verified by:
- `mvn compile` exit codes (T1, T2, T3)
- 21 grep-based acceptance criteria on T2 file content (all PASSED)
- structural `awk` source-order check confirming throw site lives inside the try-block
- JAR contents check confirming the new class is on the consumer's classpath

Runtime correctness (the 10th-order ERROR trace materializing in Tempo) is the explicit responsibility of plan 03-05's human-verify smoke gate.

## Self-Check: PASSED

**File existence checks:**
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` — FOUND (30 lines, public class extends RuntimeException, single-arg ctor, JavaDoc cites APP-04 + D-12 + TRACE-09)
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` — FOUND (modified, 80 lines, AtomicInteger field + throw site inside D-03 try-block, JavaDoc cites APP-04 + D-11 + TRACE-09)
- `consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` — FOUND (11,009 bytes, contains both `ProcessingFailedException.class` and `ProcessingService.class`)

**Commit hash existence checks:**
- `c8b9cac` (T1: feat ProcessingFailedException) — FOUND in `git log`
- `44a141b` (T2: feat APP-04 throw site) — FOUND in `git log`
