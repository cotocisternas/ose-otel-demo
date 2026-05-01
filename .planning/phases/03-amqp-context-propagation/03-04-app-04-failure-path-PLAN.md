---
id: 03-04-app-04-failure-path
phase: 03-amqp-context-propagation
plan: 04
type: execute
wave: 3
depends_on: [03-03-consumer-wiring]
requirements: [APP-04, TRACE-09]
files_modified:
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
autonomous: true
objective: "Wire APP-04's deterministic 10%-failure path: CREATE consumer-service/.../domain/ProcessingFailedException.java extending RuntimeException (per D-12); MODIFY consumer-service/.../domain/ProcessingService.java to ADD an AtomicInteger counter field and INSERT a counter.incrementAndGet() + if (n % 10 == 0) throw new ProcessingFailedException(...) inside the existing D-03 try-block (per D-11). The Phase 2 D-03 catch shape (already in place) and the consumer-wiring advice (plan 03-03) handle TRACE-09's recordException + setStatus(ERROR) on BOTH the INTERNAL and CONSUMER spans automatically — no restructuring of the catch block required."
must_haves:
  truths:
    - "ProcessingFailedException is a NEW public class extending java.lang.RuntimeException in package com.example.consumer.domain — single-arg constructor (String message); no cause chain; no serialVersionUID per D-12"
    - "ProcessingService gains a NEW instance field: private final AtomicInteger counter = new AtomicInteger(); — Spring @Service singleton scope ensures the counter persists across messages within one JVM run (D-11)"
    - "ProcessingService.process(...) inserts the throw site INSIDE the existing D-03 try-block: int n = counter.incrementAndGet(); if (n % 10 == 0) throw new ProcessingFailedException(...) — VERBATIM message wording 'Deterministic failure on order #N (every 10th order)' per APP-04 (D-11)"
    - "The Phase 2 D-03 catch block (recordException + setStatus(StatusCode.ERROR) + throw e) is PRESERVED unchanged — ProcessingFailedException extends RuntimeException so it's caught here automatically (D-11)"
    - "ProcessingService.process(...) keeps its existing @Service annotation, span lifecycle (D-01 inline template), Tracer constructor injection, and the placeholder comment is REPLACED by the throw site"
    - "After the change, consumer-service compiles (mvn -pl consumer-service compile exits 0) and the contextLoads test passes (mvn -pl consumer-service test exits 0)"
    - "ProcessingFailedException's fully-qualified class name (com.example.consumer.domain.ProcessingFailedException) will surface as the exception.type attribute on the recordException span event in Tempo — visible per ROADMAP SC #3"
    - "End-to-end runtime behavior at the 10th-order failure: ProcessingService.process throws PFE → INTERNAL span gets recordException + ERROR status → rethrown → propagates up through thin OrderListener.onOrder (no catch, plan 03-03) → caught by TracingMessageListenerAdvice (plan 03-01) → CONSUMER span gets recordException + ERROR status → rethrown → Spring AMQP container NACKs → defaultRequeueRejected=false (plan 03-03) → broker drops"
    - "BOTH INTERNAL and CONSUMER spans show ERROR status in Tempo for the 10th order; both carry the exception event with exception.type = ProcessingFailedException (TRACE-09 via the D-03 + advice catch shapes)"
  artifacts:
    - path: "consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java"
      provides: "Custom domain exception (extends RuntimeException) thrown by ProcessingService on the deterministic 10%-failure path; FQCN surfaces as exception.type in Tempo for pedagogical clarity (D-12)"
      contains: "extends RuntimeException"
    - path: "consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
      provides: "Adds AtomicInteger counter + throw site inside existing D-03 try-block; preserves Phase 2 INTERNAL-span shape and D-03 catch unchanged (D-11)"
      contains: "ProcessingFailedException"
  key_links:
    - from: "ProcessingService.process throw site"
      to: "Phase 2 D-03 catch (already in place)"
      via: "ProcessingFailedException extends RuntimeException → caught by catch (RuntimeException e) → recordException + setStatus(ERROR) on INTERNAL span (TRACE-09 satisfied for the INTERNAL span)"
      pattern: "catch \\(RuntimeException"
    - from: "Re-thrown ProcessingFailedException after INTERNAL span records"
      to: "TracingMessageListenerAdvice catch (Throwable) (plan 03-01)"
      via: "Propagates up through thin OrderListener.onOrder (plan 03-03 — no catch in onOrder) → caught by advice's catch (Throwable t) → recordException + setStatus(ERROR) on CONSUMER span (TRACE-09 satisfied for the CONSUMER span)"
      pattern: "catch \\(Throwable"
    - from: "Re-thrown ProcessingFailedException after advice records"
      to: "Spring AMQP container NACK + drop"
      via: "Container catches → defaultRequeueRejected=false (plan 03-03 D-13) → message NACK without requeue → no DLX → broker drops"
      pattern: "setDefaultRequeueRejected"
---

<objective>
Wire APP-04's deterministic 10%-failure path that gives the workshop its FIRST opportunity to demonstrate TRACE-09 (`recordException` + `setStatus(StatusCode.ERROR)`). Phase 3's pedagogical pairing — the FIRST time attendees see business logic failing should ALSO be the FIRST time they see the OTel error-span pattern — is enabled by this plan.

Two source artifacts:

1. **NEW** `consumer-service/.../domain/ProcessingFailedException.java` — a custom domain exception (extends `RuntimeException`) per D-12. The class name surfaces as `exception.type` in Tempo's recordException span event; the class name itself is documentation.

2. **MODIFY** `consumer-service/.../domain/ProcessingService.java` — add an `AtomicInteger counter` instance field and INSERT a `counter.incrementAndGet()` + `if (n % 10 == 0) throw new ProcessingFailedException(...)` site INSIDE the existing D-03 try-block per D-11. The Phase 2 D-03 catch shape (recordException + setStatus(ERROR) + throw e) is ALREADY in place — Phase 3 only provides the throw site.

The key insight from CONTEXT.md and the Phase 2 hand-off is that NO restructuring of the catch block is required. Phase 2 deliberately built the catch shape forward-compatible with this exact use case (D-03 forward-compat). Phase 3's edit to `ProcessingService.process(...)` is purely additive (~5 added lines + 1 new import + 1 new field).

The end-to-end runtime behavior is wired by the combination of plans 03-01 (the advice's catch shape), 03-03 (the listener factory's `defaultRequeueRejected=false` + thin `OrderListener.onOrder` that lets the exception propagate), and this plan (the throw site itself). When all three land, the 10th order produces:
- INTERNAL span (`ProcessingService.process`) → ERROR status + exception event
- CONSUMER span (`orders process`) → ERROR status + exception event (recorded by the advice)
- Spring AMQP container NACKs the message → broker drops (no DLX)

Both ERROR statuses propagate to Tempo's trace status display per ROADMAP SC #3. Runtime verification is deferred to plan 03-05's human-verify gate.

Purpose: APP-04 (deterministic 10%-failure trigger), TRACE-09 (recordException + setStatus(ERROR) — already wired in Phase 2's catch shape; this plan supplies the throw site that exercises it).

Output: 1 new file (~20 lines including JavaDoc), 1 modified file (~5 added lines + 1 added import + 1 added field). Net: +25 lines.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/03-amqp-context-propagation/03-CONTEXT.md
@.planning/phases/03-amqp-context-propagation/03-RESEARCH.md
@.planning/phases/03-amqp-context-propagation/03-PATTERNS.md
@.planning/phases/03-amqp-context-propagation/03-01-otel-bootstrap-amqp-classes-PLAN.md
@.planning/phases/03-amqp-context-propagation/03-03-consumer-wiring-PLAN.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-consumer-instrumentation-PLAN.md
@CLAUDE.md
@consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java

<interfaces>
<!-- The consumer-side domain contracts the executor needs. -->

Existing consumer-service/.../domain/ProcessingService.java (Phase 2 — 54 lines):
```java
@Service
public class ProcessingService {
    private final Tracer tracer;

    public ProcessingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void process(Map<String, Object> order) {
        Span span = tracer.spanBuilder("ProcessingService.process")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Phase 1: simulated domain work, in-memory only.
            // Phase 3 wires up the deterministic 10% failure path (APP-04).   <-- replaced in T2
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

REQUIREMENTS.md APP-04 verbatim wording (drives the throw message in T2):
> "The consumer's business logic fails deterministically on every 10th order so the workshop demonstrates the recordException + setStatus(ERROR) pattern"

CONTEXT.md D-11 verbatim message wording:
> `"Deterministic failure on order #" + n + " (every 10th order)"`

CONTEXT.md D-12 specs for ProcessingFailedException:
- Package: `com.example.consumer.domain`
- Extends: `java.lang.RuntimeException`
- Constructor: takes `String message` only (no cause chain)
- Visibility: `public` (Claude's discretion noted "package-private OR public — Claude's discretion"; recommend `public` for cleanliness)
- No `serialVersionUID` (modern Java practice for exceptions that don't cross JVM boundaries)
</interfaces>
</context>

<tasks>

<task id="03-04-T1" type="auto">
  <name>Task 1: CREATE ProcessingFailedException.java in com.example.consumer.domain (extends RuntimeException; D-12)</name>
  <files>consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java</files>
  <read_first>
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 130-132 — D-12 verbatim spec: "Custom ProcessingFailedException extends RuntimeException in com.example.consumer.domain (new file). Constructor takes String message. Pedagogical value: the class name appears as exception.type in Tempo's span detail")
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (line 47 — D-12 research-confirmed: "straightforward; class name surfaces as exception.type attribute on the recordException event in Tempo")
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 200-232 — ProcessingFailedException pattern: 5-item transformation notes; concrete excerpt)
    - .planning/REQUIREMENTS.md (lines 23 — APP-04 verbatim: "fails deterministically on every 10th order")
  </read_first>
  <action>
    Create the new file `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` with the EXACT content below. This is a trivial domain-exception class — no Spring annotations, no cause chain, no serialVersionUID.

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

    Verify with `mvn -pl consumer-service compile`. Expect exit 0.

    File should be ~25 lines including JavaDoc; the class body is 5 lines.
  </action>
  <acceptance_criteria>
    - File exists: `test -f consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` exits 0
    - Package declared correctly: `head -1 consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java | grep -q 'package com.example.consumer.domain;'` exits 0
    - Extends RuntimeException: `grep -q 'extends RuntimeException' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` exits 0
    - Class is public: `grep -q 'public class ProcessingFailedException' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` exits 0
    - Single-arg constructor (String message): `grep -q 'public ProcessingFailedException(String message)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` exits 0
    - Calls super(message): `grep -q 'super(message)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` exits 0
    - NO serialVersionUID: `grep -c 'serialVersionUID' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` returns 0
    - NO cause-chain constructor: `grep -c 'Throwable cause' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` returns 0
    - NO Spring annotations: `grep -cE '@(Component|Service|Configuration|Bean|Autowired)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` returns 0
    - JavaDoc references APP-04, D-12, TRACE-09: `grep -cE 'APP-04|D-12|TRACE-09' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` returns >= 3
    - Compiles: `mvn -q -pl consumer-service compile` exits 0
    - File line count between 15 and 35: `LINES=$(wc -l < consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java); python3 -c "l=$LINES; assert 15<=l<=35, l; print(f'OK: {l} lines')"` exits 0
  </acceptance_criteria>
  <verify>
    <automated>test -f consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java &amp;&amp; grep -q 'public class ProcessingFailedException extends RuntimeException' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java &amp;&amp; grep -q 'public ProcessingFailedException(String message)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java &amp;&amp; grep -q 'super(message)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java &amp;&amp; mvn -q -pl consumer-service compile</automated>
  </verify>
  <done>ProcessingFailedException.java exists at the canonical path; public class extending RuntimeException; single-arg (String message) constructor calling super(message); no serialVersionUID, no cause chain, no Spring annotations; JavaDoc references APP-04 + D-12 + TRACE-09; consumer compiles cleanly.</done>
</task>

<task id="03-04-T2" type="auto">
  <name>Task 2: MODIFY ProcessingService.java — add AtomicInteger counter + throw site inside existing D-03 try-block (D-11; APP-04)</name>
  <files>consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java (current state — 54 lines; the existing D-03 try-block is at lines 40-52; the placeholder body is at lines 41-42)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 119-128 — D-11 verbatim throw site code; AtomicInteger counter as instance field; verbatim message wording)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 743-790 — exact final-state code for ProcessingService; lines 833-835 — Existing Code Confirmation: D-03 catch is at lines 43-49; placeholder body at lines 41-42)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 461-516 — ProcessingService transformation: 6-item transformation notes including the exact 5-line throw-site replacement)
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java (just created in T1 — same package, no import needed)
  </read_first>
  <action>
    Open `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` (currently 54 lines).

    REPLACE THE ENTIRE FILE with the EXACT content below. The change is purely additive — preserving the existing @Service annotation, Tracer field, constructor, span lifecycle, and D-03 catch shape; adding an AtomicInteger field, an import, and a throw site inside the try-block.

    ```java
    package com.example.consumer.domain;

    import java.util.Map;
    import java.util.concurrent.atomic.AtomicInteger;

    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Scope;

    import org.springframework.stereotype.Service;

    /**
     * Domain layer — simulates downstream order-processing work.
     *
     * <p>Phase 2 wraps {@link #process(Map)} in an INTERNAL span (TRACE-06)
     * named "ProcessingService.process" using the D-01 pure-inline template,
     * with a forward-compatible D-03 catch that records exceptions on the
     * span as ERROR.
     *
     * <p>Phase 3 added the deterministic 10%-failure path (APP-04 + TRACE-09):
     * an in-memory {@link AtomicInteger} counter increments on every call;
     * on every 10th call, the method throws a custom {@link ProcessingFailedException}.
     * The Phase 2 D-03 catch reacts to it ({@code recordException} +
     * {@code setStatus(StatusCode.ERROR)} + rethrow) — NO restructuring of
     * the catch block was required (D-11).
     *
     * <p>The rethrown exception propagates up through {@code OrderListener.onOrder}
     * (Phase 3 plan 03-03 made it a thin pass-through with no catch) → caught
     * by {@code TracingMessageListenerAdvice} (Phase 3 plan 03-01) which
     * records it on the CONSUMER span and rethrows → Spring AMQP container
     * NACKs → with {@code defaultRequeueRejected=false} (Phase 3 plan 03-03),
     * the broker drops the message (no DLX per PROJECT.md). Both INTERNAL
     * and CONSUMER spans show ERROR status in Tempo for the 10th order
     * (ROADMAP SC #3).
     */
    @Service
    public class ProcessingService {
        private final Tracer tracer;

        // Phase 3: deterministic 10%-failure trigger (APP-04 + D-11). Spring
        // @Service is singleton scope by default — the counter persists across
        // messages within one JVM run; resets per `mise run dev` start (fine
        // for fresh demo sessions).
        private final AtomicInteger counter = new AtomicInteger();

        public ProcessingService(Tracer tracer) {
            this.tracer = tracer;
        }

        public void process(Map<String, Object> order) {
            // ---- D-01 inline span template (INTERNAL) ----
            Span span = tracer.spanBuilder("ProcessingService.process")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                // Phase 3: deterministic 10%-failure trigger (APP-04 + D-11).
                // Verbatim message wording from APP-04: "every 10th order".
                int n = counter.incrementAndGet();
                if (n % 10 == 0) {
                    throw new ProcessingFailedException(
                        "Deterministic failure on order #" + n + " (every 10th order)");
                }
                // Successful processing path — Phase 1 placeholder retained
                // (simulated domain work, in-memory).
            } catch (RuntimeException e) {
                // D-03 catch shape from Phase 2 — preserved unchanged.
                // ProcessingFailedException extends RuntimeException, so it
                // is caught here (TRACE-09). The advice's catch (Throwable)
                // also records this on the CONSUMER span when the rethrow
                // bubbles up.
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end();
            }
        }
    }
    ```

    Verify with `mvn -pl consumer-service compile`. Expect exit 0.

    Verify the throw site is INSIDE the try-block (not before/after):
    ```
    awk '/try \(Scope scope = span.makeCurrent\(\)\)/{t=NR} /throw new ProcessingFailedException/{th=NR} /} catch \(RuntimeException/{c=NR} END{exit (t<th && th<c)?0:1}' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
    ```

    Expected order: `try {` line < `throw` line < `} catch` line. The throw must land BEFORE the catch.

    Verify the counter is an instance field (not static, not local):
    ```
    grep -B1 'private final AtomicInteger counter' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
    ```
    Expected: appears at the class-body level, alongside `private final Tracer tracer` — not inside a method.

    Verify no import is needed for ProcessingFailedException (same package):
    ```
    grep -c 'import com.example.consumer.domain.ProcessingFailedException' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
    ```
    Expected: 0 (same package — no import line required).
  </action>
  <acceptance_criteria>
    - File compiles: `mvn -q -pl consumer-service compile` exits 0
    - AtomicInteger import added: `grep -q 'import java.util.concurrent.atomic.AtomicInteger;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - AtomicInteger counter instance field present: `grep -q 'private final AtomicInteger counter = new AtomicInteger();' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - counter.incrementAndGet() called inside method: `grep -q 'int n = counter.incrementAndGet()' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Modulus check on 10: `grep -q 'if (n % 10 == 0)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Throw new ProcessingFailedException: `grep -q 'throw new ProcessingFailedException(' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Verbatim message wording ("Deterministic failure on order # ... (every 10th order)"): `grep -q 'Deterministic failure on order #' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q '(every 10th order)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Throw site is INSIDE the try-block (before the catch): `awk '/try \(Scope/{t=NR} /throw new ProcessingFailedException/{th=NR} /} catch \(RuntimeException/{c=NR} END{exit (t<th \&\& th<c)?0:1}' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - D-03 catch block PRESERVED unchanged: `grep -q 'span.recordException(e);' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'span.setStatus(StatusCode.ERROR);' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q '} catch (RuntimeException e)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - @Service annotation preserved: `grep -q '@Service' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Tracer field + constructor preserved: `grep -q 'private final Tracer tracer' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'public ProcessingService(Tracer tracer)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Span lifecycle preserved (D-01 template): `grep -q 'spanBuilder("ProcessingService.process")' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'setSpanKind(SpanKind.INTERNAL)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'span.end();' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - NO import for ProcessingFailedException (same package): `grep -c 'import com.example.consumer.domain.ProcessingFailedException' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
    - JavaDoc references APP-04, D-11, TRACE-09: `grep -cE 'APP-04|D-11|TRACE-09' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns >= 3
    - File line count is between 60 and 100 (was 54): `LINES=$(wc -l < consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java); python3 -c "l=$LINES; assert 60<=l<=100, l; print(f'OK: {l} lines')"` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl consumer-service compile &amp;&amp; grep -q 'private final AtomicInteger counter = new AtomicInteger()' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'int n = counter.incrementAndGet()' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'if (n % 10 == 0)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'throw new ProcessingFailedException(' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q '(every 10th order)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; awk '/try \(Scope/{t=NR} /throw new ProcessingFailedException/{th=NR} /} catch \(RuntimeException/{c=NR} END{exit (t<th \&\& th<c)?0:1}' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java</automated>
  </verify>
  <done>ProcessingService.java has a NEW AtomicInteger counter instance field; the throw site (counter.incrementAndGet + n%10==0 + throw new ProcessingFailedException with verbatim APP-04 wording) is INSIDE the existing D-03 try-block; D-03 catch + @Service + Tracer + span lifecycle all preserved unchanged; consumer compiles.</done>
</task>

<task id="03-04-T3" type="auto">
  <name>Task 3: Verify consumer-service builds + context-loads test passes; mise verify:bom invariant preserved</name>
  <files>(none — verification only)</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java (just created in T1)
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java (just modified in T2)
    - .planning/phases/03-amqp-context-propagation/03-03-SUMMARY.md (if exists — Wave 2 hand-off summary; consumer already has tracing wired)
  </read_first>
  <action>
    Run a clean build on the consumer-service module + its upstream (otel-bootstrap) and confirm:
    1. The new ProcessingFailedException class is on the consumer's classpath.
    2. ProcessingService.process compiles with the new throw site.
    3. The context-loads test (`ConsumerApplicationTests.contextLoads()`) passes — proves Spring DI still resolves `ProcessingService` (no new DI requirement: AtomicInteger is `new`'d directly in the field initializer, not injected).

    Commands to run, in order:

    ```sh
    # 1. Clean compile + test the consumer module
    mvn -pl otel-bootstrap,consumer-service -am clean test

    # 2. Verify ProcessingFailedException compiled into the JAR
    mvn -pl consumer-service -am package -DskipTests
    test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar
    jar tf consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar 2>/dev/null | grep -F 'BOOT-INF/classes/com/example/consumer/domain/ProcessingFailedException.class' || jar tf consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar 2>/dev/null | grep -F 'com/example/consumer/domain/ProcessingFailedException.class'
    # Expect at least one match

    # 3. Mise verify:bom (Phase 2 invariant)
    mise run verify:bom

    # 4. Confirm only the 2 expected files changed in this plan
    git status --porcelain consumer-service/src/main/java/com/example/consumer/domain/
    # Expect output:
    #  M consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
    # ?? consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java
    ```

    Note that NO live runtime test against RabbitMQ is in scope for this plan — that's the human-verify gate in plan 03-05. Here we just confirm the source-level wiring compiles and the Spring context bootstraps without errors.

    The contextLoads test exercise:
    - Spring builds the application context.
    - `ProcessingService` bean is created (single-arg constructor with `Tracer` already provided by `OtelSdkConfiguration`).
    - The new `AtomicInteger counter` field is initialized to `0` (default for `new AtomicInteger()`).
    - The `OrderListener` bean is created (single-arg constructor with `ProcessingService`).
    - All Phase 3 beans from plan 03-03 (TracingMessageListenerAdvice + rabbitListenerContainerFactory) are present.

    No live broker is needed for the test to pass — Spring AMQP lazily connects.

    If the test fails with:
    - "ConflictingBeanDefinitionException for ProcessingService" → unlikely (we didn't change `@Service` or constructor); investigate any duplicate bean defs.
    - "ProcessingFailedException class not found" → T1 file missing or in wrong package; verify package declaration.
    - "Spring property resolution error" → unrelated to Phase 3; check baseline.
  </action>
  <acceptance_criteria>
    - Clean compile passes: `mvn -q -pl otel-bootstrap,consumer-service -am clean compile` exits 0
    - Consumer test passes: `mvn -q -pl consumer-service test` exits 0
    - Consumer JAR built: `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` exits 0
    - ProcessingFailedException.class is in the consumer's classpath: `jar tf consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar 2>/dev/null | grep -q 'ProcessingFailedException.class'` exits 0
    - ProcessingService.class is also present (regression check): `jar tf consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar 2>/dev/null | grep -q 'ProcessingService.class'` exits 0
    - mise verify:bom passes: `mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'` exits 0
    - Only 2 expected files in the consumer/domain/ tree are dirty: `git status --porcelain consumer-service/src/main/java/com/example/consumer/domain/ | wc -l` returns 2
    - Specifically: ProcessingService.java is modified, ProcessingFailedException.java is new: `git status --porcelain consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | grep -qE '^ ?M'` exits 0; `git status --porcelain consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java | grep -qE '^\?\?'` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl otel-bootstrap,consumer-service -am clean test &amp;&amp; mvn -q -pl consumer-service -am package -DskipTests &amp;&amp; jar tf consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar 2>/dev/null | grep -q 'ProcessingFailedException.class' &amp;&amp; mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'</automated>
  </verify>
  <done>Consumer module clean-compile + test + package all pass; ProcessingFailedException.class is in the produced JAR; the contextLoads test confirms Spring DI resolves ProcessingService with its new field; mise verify:bom invariant preserved; only 2 expected files dirty in git status.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 03-04 — APP-04 failure path)

| Boundary | Description |
|----------|-------------|
| Listener thread → broker (NACK loop) | `defaultRequeueRejected=false` (set in plan 03-03) prevents infinite NACK-requeue loops on failed messages |
| ProcessingFailedException → Tempo span event | Exception message ("Deterministic failure on order #N (every 10th order)") + stack trace surface as span event attributes |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-3-04-01 | DoS | Failed-message storm: an infinite NACK-requeue loop pinning CPU and spamming Tempo | mitigate | Plan 03-03's `setDefaultRequeueRejected(false)` (D-13) ensures NACKs drop the message instead of requeue. With no DLX per PROJECT.md, failed messages just disappear — clean. |
| T-3-04-02 | Information Disclosure | Stack trace in `recordException(e)` could leak internal class names / line numbers | accept | Demo only; orderId in the message is non-PII (synthetic UUID); the bounded message text contains no sensitive data ("Deterministic failure on order #N (every 10th order)" — N is a count, not user input). Production hardening: scrub or sanitize span attributes per ASVS V7. Documented in JavaDoc on ProcessingFailedException. |
| T-3-04-03 | Tampering | A future change increases the failure modulus (e.g., 1 in 100) and the workshop demo wait time becomes too long for a live demo | accept | Workshop scope: 1 in 10 is a workshop-tuned demo cadence (POST 10 orders → 1 fails). If a future workshop wants a different rate, change the modulus literal — but `mise run demo:order` would need to send N orders to trigger it. Out of scope for v1. |
| T-3-04-04 | Repudiation | Exception is silently swallowed at one of the catch layers | mitigate | Both Phase 2's D-03 catch (INTERNAL span) and Phase 3's advice catch (CONSUMER span) record + ERROR + rethrow. Spring AMQP container then handles the NACK + drop. Fail-closed by default; verifiable in Tempo (both spans show ERROR status for the 10th order). |

**No CRITICAL/HIGH security blockers.**
</threat_model>

<verification>
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` exists, is `public class extends RuntimeException`, has single-arg `(String message)` constructor calling `super(message)`, no serialVersionUID.
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` has new `private final AtomicInteger counter = new AtomicInteger();` field; the throw site (`int n = counter.incrementAndGet(); if (n % 10 == 0) throw new ProcessingFailedException(...)`) is INSIDE the existing D-03 try-block; the verbatim APP-04 message wording is present.
- The Phase 2 D-03 catch (recordException + setStatus(ERROR) + throw e) is PRESERVED unchanged — `ProcessingFailedException extends RuntimeException` so the existing catch handles it without restructuring.
- @Service + Tracer field + Tracer constructor + span lifecycle (D-01 template) all preserved.
- `mvn -pl otel-bootstrap,consumer-service -am clean test` exits 0.
- The consumer-service JAR contains `ProcessingFailedException.class` and the modified `ProcessingService.class`.
- `mise run verify:bom` exits 0.
- File modification list (git status) shows exactly: 1 NEW (ProcessingFailedException.java) + 1 MODIFIED (ProcessingService.java).
</verification>

<success_criteria>
- APP-04 (deterministic 10%-failure trigger): the consumer's business logic now fails on every 10th order via the AtomicInteger + modulus + throw pattern. Counter persists across messages within one JVM run (Spring @Service singleton).
- TRACE-09 (recordException + setStatus(ERROR)): wired automatically via Phase 2's existing D-03 catch on the INTERNAL span (no restructuring required) AND via Phase 3 plan 03-01's advice catch on the CONSUMER span. BOTH spans show ERROR status + exception event in Tempo for the 10th order.
- D-11 honored: throw site lives INSIDE the existing D-03 try-block; uses AtomicInteger; verbatim APP-04 message wording.
- D-12 honored: ProcessingFailedException is a public class in com.example.consumer.domain extending RuntimeException with single-arg constructor; no cause chain, no serialVersionUID.
- ROADMAP SC #3 will be runtime-observable after plan 03-05 (the human-verify gate): "Workshop attendee triggers the deterministic 10th order and sees Tempo render the trace as Error status with the exception event attached to the consumer span."
- The combined Phase 3 hand-off chain is now complete: producer wired (03-02) + consumer wired (03-03) + APP-04 throw site present (this plan); plan 03-05 finalizes with README delta + tag.
- Phase 2 functionality intact: consumer module compiles, contextLoads test passes, BOM invariant holds.
</success_criteria>

<output>
After completion, create `.planning/phases/03-amqp-context-propagation/03-04-SUMMARY.md` documenting:
- The 2 modified/created files (paste their final paths)
- The new ProcessingFailedException class (paste the full class body)
- The diff snippet for ProcessingService showing only the 3 added regions: import + counter field + throw site (paste a unified-diff-style fragment)
- The line count delta on ProcessingService.java (was 54 → now ~80; +26 lines including expanded JavaDoc)
- mvn test confirmation: paste the surefire summary line for ConsumerApplicationTests
- mise verify:bom result
- Wave 3 hand-off: APP-04 + TRACE-09 source delta complete; producer + consumer + failure path all wired; plan 03-05 (the LAST plan in Phase 3) lands the README delta + the user-approved annotated git tag
- A note that runtime verification (10th-order ERROR trace + exception event in Tempo) requires the live broker + sequential POSTs, deferred to plan 03-05's human-verify gate per Phase 2 precedent
</output>
