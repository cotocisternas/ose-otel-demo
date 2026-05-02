---
phase: 05-logs-correlation
plan: 05
type: execute
wave: 3
depends_on:
  - 05-03
files_modified:
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
autonomous: true
requirements:
  - LOG-04
tags:
  - slf4j
  - logging
  - consumer
  - error-handling
  - app-04
must_haves:
  truths:
    - id: LOG-04-consumer-error
      description: "ProcessingService has a private static final Logger LOG field and a LOG.error call on the APP-04 deterministic-failure path"
      verify: "grep -q 'private static final Logger LOG' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'LOG.error(' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
    - id: D-16-position
      description: "LOG.error appears INSIDE the catch block AFTER counter increment and BEFORE span.recordException (per PATTERNS.md §E)"
      verify: "awk '/} catch \\(RuntimeException e\\) {/,/throw e;/' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | awk '/LOG.error\\(/{found=1; next} found && /span.recordException/{print \"OK\"; exit 0} END{exit !found}'"
    - id: slf4j-imports-consumer
      description: "ProcessingService imports org.slf4j.Logger and org.slf4j.LoggerFactory"
      verify: "grep -q 'import org.slf4j.Logger;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'import org.slf4j.LoggerFactory;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
    - id: error-throwable-arg
      description: "LOG.error passes the exception as the LAST argument (SLF4J's throwable-as-last-arg idiom)"
      verify: "grep -E 'LOG.error\\(.*[, ]e\\)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
    - id: no-lombok-consumer
      description: "No @Slf4j annotation"
      verify: "! grep -q '@Slf4j' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
    - id: phase-3-catch-preserved
      description: "The Phase 3 catch block's recordException + setStatus + throw e shape is preserved"
      verify: "grep -q 'span.recordException(e)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'span.setStatus(StatusCode.ERROR)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'throw e;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
    - id: build-clean-consumer
      description: "Consumer compiles cleanly"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl consumer-service -am compile"
    - id: D-17-onorder-untouched
      description: "OrderListener.onOrder happy-path log is UNTOUCHED — Phase 1's LOG.info('OrderCreated received: orderId={}') stays as-is and gets trace_id stamping automatically once the appender is installed (D-17)"
      verify: "grep -F 'OrderCreated received: orderId={}' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java"
  artifacts:
    - path: consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
      provides: "Static SLF4J Logger field + LOG.error call inside the APP-04 catch block, paired with the existing span.recordException for triple-signal correlation on failure"
      contains: "private static final Logger LOG"
      contains: "LOG.error("
  key_links:
    - from: consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
      to: SLF4J → Logback → MDC_CONSOLE wrapper → CONSOLE / OTEL appender (declared in logback-spring.xml from Plan 05-03)
      via: "LOG.error call dispatches through Logback's appender chain inside the CONSUMER span context (Phase 3 advice)"
      pattern: "LOG.error\\("
    - from: consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
      to: Phase 3 TRACE-09 recordException event on the CONSUMER span
      via: "Both signals fire on the same orderId in the same trace — Loki ERROR query → Tempo trace with recordException event"
      pattern: "span.recordException(e)"
---

<objective>
Add a single SLF4J `LOG.error("...", e)` call to `consumer-service/.../ProcessingService.java` inside the existing Phase 3 `catch (RuntimeException e)` block (lines 67-75 of the current file). This establishes the FIRST `LOG.error` pattern in the codebase (PATTERNS §E — no analog exists; Phase 5 establishes the idiom). Pairs with the existing `span.recordException(e)` for triple-signal correlation on failure: log_event in Loki + span.recordException in Tempo + the ERROR-status of both INTERNAL and CONSUMER spans (Phase 3 TRACE-09).

Purpose: Phase 5 D-16 — give the workshop a stable Loki query for the failure-path lesson. Workshop attendees can run `{service_name="order-consumer"} |~ "ERROR"` (or filter by trace_id), see the LOG.error with the orderId, click trace_id, and land in Tempo's view of the trace whose CONSUMER span carries the recordException event. This IS the Phase 5 highlight (CONTEXT.md `<specifics>` — "Triple-signal correlation on the failure path is the Phase 5 highlight").

Output: 1 file modified — 2 imports, 1 Logger field, 1 LOG.error call inside the catch block. Consumer compiles cleanly.

D-16 host choice: option (a) per CONTEXT.md recommendation — log AT the failure source (`ProcessingService.process` catch block), NOT in the listener advice's catch (option b). Option (a) is closer to the failure semantics; option (b) would put the log in shared infrastructure code which is pedagogically less direct.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/05-logs-correlation/05-CONTEXT.md
@.planning/phases/05-logs-correlation/05-RESEARCH.md
@.planning/phases/05-logs-correlation/05-PATTERNS.md
@.planning/phases/05-logs-correlation/05-03-SUMMARY.md
@.planning/phases/03-amqp-context-propagation/03-CONTEXT.md
@consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
@consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java

<interfaces>
<!-- Existing analog: OrderListener uses LOG.info (Phase 1). PATTERNS §E says LOG.error is NEW — first in codebase. -->
<!-- Existing Phase 3 catch block — must NOT modify the recordException / setStatus / throw e shape. -->

ProcessingService.java current state (lines 1-80):
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

/* class JavaDoc lines 14-37 — leave untouched */

@Service
public class ProcessingService {
    private final Tracer tracer;
    private final AtomicInteger counter = new AtomicInteger();

    public ProcessingService(Tracer tracer) { this.tracer = tracer; }

    public void process(Map<String, Object> order) {
        Span span = tracer.spanBuilder("ProcessingService.process")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            int n = counter.incrementAndGet();
            if (n % 10 == 0) {
                throw new ProcessingFailedException(
                    "Deterministic failure on order #" + n + " (every 10th order)");
            }
            // Successful processing path — Phase 1 placeholder retained
        } catch (RuntimeException e) {
            // D-03 catch shape from Phase 2 — preserved unchanged.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

The `process` method takes `Map<String, Object> order` — to extract orderId for the log, use `order.get("orderId")`. The orderId field is set on the message map by `OrderPublisher.publish` (line 41: `message.put("orderId", orderId)`).

The active span at LOG.error time is the INTERNAL span (the one this method opened). The CONSUMER span (from Phase 3 advice) wraps this whole method, so the trace_id stamped in the log record will be the same as the producer's trace_id (proven by Phase 3's joined trace).

OrderListener pattern (PATTERNS §S-1):
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
```

SLF4J error idiom: `LOG.error("message: key={}", value, exception)` — the THROWABLE is the LAST argument and is treated specially by SLF4J (NOT bound to a `{}` placeholder). The `{}` placeholders match all args EXCEPT the trailing throwable.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Add SLF4J Logger field + LOG.error inside the APP-04 catch block of ProcessingService</name>
  <files>consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java (full file, current state — pay special attention to the catch block at lines 67-75, the `order` parameter on line 52, and the existing imports at lines 1-12)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (the SLF4J pattern source — lines 5-6 imports, line 41 field declaration)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-16 — recommended host is ProcessingService option (a))
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§E — LOG.error pattern; §S-1 — SLF4J Logger field shape; the §E action snippet shows the exact catch-block edit)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §F — illustrative shape; §Risk #7 — Span.current() valid inside the listener body, so trace_id stamping is automatic)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (Phase 3 D-08 — the catch shape that recordException + setStatus(ERROR) + throw was already locked; Phase 5 ADDS the LOG.error counterpart, must NOT change the existing 3 lines)
  </read_first>
  <action>
Edit `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`. Three changes:

**EDIT 1 — Add SLF4J imports.** The current file has 7 imports (lines 3-12) split into three groups by blank lines (java.* / io.opentelemetry.* / org.springframework.*). Add `org.slf4j.Logger` + `org.slf4j.LoggerFactory` in their own group between `io.opentelemetry.*` and `org.springframework.*`:

```java
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
```

**EDIT 2 — Add Logger field as the FIRST instance member of the class.** The current class body (line 39 onwards) declares `tracer` (line 40) and `counter` (line 46). Add the Logger field BEFORE `tracer` so the LOG field is first:

```java
@Service
public class ProcessingService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);
    private final Tracer tracer;
```

**EDIT 3 — Add LOG.error inside the existing catch block.** The current catch block (lines 67-75):

```java
        } catch (RuntimeException e) {
            // D-03 catch shape from Phase 2 — preserved unchanged.
            // ProcessingFailedException extends RuntimeException, so it
            // is caught here (TRACE-09). The advice's catch (Throwable)
            // also records this on the CONSUMER span when the rethrow
            // bubbles up.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        }
```

Replace it with:

```java
        } catch (RuntimeException e) {
            // D-03 catch shape from Phase 2 — preserved unchanged below.
            // ProcessingFailedException extends RuntimeException, so it
            // is caught here (TRACE-09). The advice's catch (Throwable)
            // also records this on the CONSUMER span when the rethrow
            // bubbles up.
            //
            // Phase 5 D-16: LOG.error is the Loki-side counterpart to
            // span.recordException — both signals carry the same trace_id
            // and span_id (Span.current() is valid here per RESEARCH
            // Finding #7; the active span is this method's INTERNAL span,
            // wrapped by Phase 3's CONSUMER span). The Loki query
            // {service_name="order-consumer"} |~ "<traceId>" returns this
            // log line, and clicking the trace_id field opens the trace
            // in Tempo with the recordException event already attached on
            // the CONSUMER span.
            //
            // SLF4J's throwable-as-last-arg idiom: the trailing `e` is
            // treated as the exception (not bound to a {} placeholder).
            // The single {} placeholder matches `orderId`.
            Object orderId = order.get("orderId");
            LOG.error("order processing failed: orderId={}", orderId, e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        }
```

The new code:
1. Adds a comment block explaining the Phase 5 D-16 addition (~12 lines of comments — preserves the comment-density bar inherited from Phase 2 DOC-03)
2. Extracts `orderId` from the `order` parameter via `order.get("orderId")` (returns Object since the map is `Map<String, Object>`)
3. Calls `LOG.error("order processing failed: orderId={}", orderId, e)` — SLF4J's two-arg-plus-throwable idiom; the `e` is the LAST argument and is treated as the exception
4. The existing 3 Phase 3 lines (`span.recordException(e)`, `span.setStatus(StatusCode.ERROR)`, `throw e;`) are preserved BYTE-FOR-BYTE — Phase 5 ADDS new lines, does NOT modify Phase 3 lines

**Position rationale (WHY before recordException):** the LOG.error fires while the active span is still INTERNAL (Phase 2's span on this method). After `recordException(e)` runs, the span is still active until `span.end()` in the finally block — so technically either ordering would stamp the same trace_id/span_id. The "before" ordering is preferred because:
- Workshop attendees reading the catch block top-to-bottom see "log the error" → "record on span" → "rethrow" — natural narrative flow
- If `recordException` ever throws (it shouldn't, but defense in depth), the LOG.error has already fired
- The PATTERNS §E recommendation places it BEFORE recordException

DO NOT:
- Modify the existing 3 Phase 3 lines (`span.recordException(e)`, `span.setStatus(StatusCode.ERROR)`, `throw e;`)
- Use `@Slf4j` Lombok annotation
- Use lowercase `log` field name
- Bind the exception `e` to a `{}` placeholder (must be the LAST positional argument; SLF4J handles the throwable specially — `LOG.error("msg {}", e)` would render the exception's `toString()` into the placeholder rather than attaching the stack trace)
- Place the LOG.error in the listener advice (D-16 option (b)) — D-16 explicitly recommends option (a)
- Place the LOG.error inside the try block (must be in catch — only fires on the failure path)
- Add a `LOG.info` for the happy path (D-15/D-17 already cover happy-path logging via OrderController + OrderListener; one error log on the failure path is sufficient)
- Refactor the existing `try (Scope scope = span.makeCurrent())` block — out of scope; keep the 7-line shape
- Change the method signature
- Cast `orderId` (Object) to a more specific type — the `{}` placeholder calls `toString()` which is fine for any Object including null
- Truncate the existing comment about "D-03 catch shape from Phase 2" — extend the comment block, don't shorten it
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'import org.slf4j.Logger;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'import org.slf4j.LoggerFactory;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'LOG.error(' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && awk '/} catch \(RuntimeException e\) {/,/^        }$/' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | grep -q 'LOG.error(' && awk '/} catch \(RuntimeException e\) {/,/^        }$/' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | awk '/LOG.error\(/{found=1; next} found && /span.recordException/{print \"OK\"; exit 0} END{exit !found}' && grep -E 'LOG.error\(.*[, ]e\)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'span.recordException(e)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'span.setStatus(StatusCode.ERROR)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && grep -q 'throw e;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && ! grep -q '@Slf4j' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java && mvn -B -pl consumer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q 'import org.slf4j.Logger;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
    - `grep -q 'import org.slf4j.LoggerFactory;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
    - `grep -q 'private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
    - `grep -q 'LOG.error(' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
    - LOG.error is INSIDE the catch block: `awk '/} catch \(RuntimeException e\) {/,/^        }$/' file | grep -q 'LOG.error('` (catch-block content matches LOG.error)
    - LOG.error appears BEFORE span.recordException: awk script that matches LOG.error first then span.recordException succeeds
    - LOG.error has the throwable as the trailing argument: `grep -E 'LOG.error\(.*[, ]e\)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0 (must end with `, e)` or ` e)`)
    - Phase 3 lines preserved (3 grep checks):
      - `grep -q 'span.recordException(e)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
      - `grep -q 'span.setStatus(StatusCode.ERROR)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
      - `grep -q 'throw e;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
    - LOG.error is the only NEW LOG call (no LOG.info added — D-17 keeps OrderListener happy-path log untouched, no new info log here):
      - `grep -c 'LOG\.\(info\|warn\|debug\|trace\)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` equals 0 (no info/warn/debug/trace in this file)
    - NO `@Slf4j` annotation: `! grep -q '@Slf4j' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`
    - Consumer compiles: `cd $(git rev-parse --show-toplevel) && mvn -B -pl consumer-service -am compile` exits 0
  </acceptance_criteria>
  <done>
ProcessingService.java has Logger field and LOG.error call inside the catch block, positioned before span.recordException(e). The existing Phase 3 lines are preserved byte-for-byte. The throwable `e` is the trailing argument (SLF4J idiom). Consumer compiles cleanly. The triple-signal-on-failure pattern is established.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| AMQP message → consumer application code | Message body contains `orderId` (UUID set by producer) and any other fields the producer added. Untrusted data — Spring AMQP deserializes via Jackson to `Map<String, Object>`. |
| application LOG.error call → Logback | The exception object's stack trace is rendered by Logback's pattern (no special encoder for stack traces — Logback handles this automatically when the throwable is the last argument). |

## STRIDE Threat Register (ASVS L1, security_enforcement: enabled, block-on: high)

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-05-01 | Information Disclosure | LOG.error logs orderId only | accept | `orderId` is a UUID generated by `OrderService.place(...)` — not PII. The exception's stack trace (rendered by Logback when the throwable is the trailing argument) shows `ProcessingFailedException` and the workshop's stack frames — no secrets or external file paths beyond standard Java/Spring frames. |
| T-05-05-02 | Information Disclosure | Stack trace in OTLP export | accept | The exception stack trace flows to Loki via the OTEL appender. For workshop synthetic data this is fine. Production teams might apply stack-trace shortening (Logback's `%shortenedThrowable{...}`) — out of scope for v1. |
| T-05-05-03 | Tampering | Log injection via orderId | accept | The `order.get("orderId")` value comes from the AMQP message map. If an attacker could inject newlines into orderId, a Loki log line could be split. For the workshop's synthetic data flow (producer generates orderId via UUID), this is bounded. Production: validate orderId format before logging. |
| T-05-05-04 | Repudiation | LOG.error trace_id correlation | mitigate | LOG.error fires inside the INTERNAL span (which is wrapped by Phase 3's CONSUMER span). `Span.current()` is valid (RESEARCH Finding #7). The OTEL appender stamps trace_id/span_id from the active span, which matches what the producer's trace shows in Tempo (Phase 3 propagation joins them). Verified by Plan 05-06 smoke test on the deterministic 10th order. |
| T-05-05-05 | Spoofing | Wrong span context at log time | accept | The Phase 3 listener advice calls `Context.makeCurrent()` BEFORE the listener body runs (PROP-02). By the time `ProcessingService.process(...)` enters its catch block, the chain is: outer CONSUMER span → outer listener Scope → INTERNAL span → INTERNAL Scope → catch. `Span.current()` returns the INTERNAL span; trace_id matches the trace from the producer. Verified by Phase 3's success criteria + RESEARCH Finding #7. |
| T-05-05-06 | Denial of Service | Stack trace allocation cost on every 10th order | accept | The deterministic-failure path fires once per 10 orders (controlled by `AtomicInteger counter`). Stack-trace allocation is O(stack-depth) which is bounded for this app (~30 frames). For workshop volume (1-2 orders/min) this is negligible. |
</threat_model>

<verification>
- `grep -q 'import org.slf4j.Logger' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
- `grep -q 'import org.slf4j.LoggerFactory' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
- `grep -q 'private static final Logger LOG' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
- `grep -q 'LOG.error(' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 0
- LOG.error is INSIDE the catch (RuntimeException e) block (awk verifies)
- LOG.error appears BEFORE span.recordException(e) (awk verifies)
- LOG.error has `e` as the trailing positional argument (regex matches `, e)` or ` e)` at end of call)
- Phase 3 catch lines (recordException, setStatus, throw e) all preserved
- NO @Slf4j, no LOG.info/warn/debug/trace added (only LOG.error)
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl consumer-service -am compile` exits 0
</verification>

<success_criteria>
1. ProcessingService.java has SLF4J imports, Logger field, and LOG.error call inside the existing catch block.
2. LOG.error is positioned BEFORE span.recordException(e) (top-to-bottom narrative: log → record → rethrow).
3. The throwable `e` is the LAST positional argument (SLF4J throwable-as-last-arg idiom).
4. Phase 3's catch block lines (span.recordException, span.setStatus, throw e) are preserved byte-for-byte.
5. NO Lombok @Slf4j annotation.
6. orderId is logged but no PII / secrets are logged.
7. Consumer compiles: `mvn -pl consumer-service -am compile` exits 0.
8. The triple-signal correlation pattern is established: LOG.error (Loki) + span.recordException (Tempo) + same trace_id/span_id on both, on the same orderId.
</success_criteria>

<output>
After completion, create `.planning/phases/05-logs-correlation/05-05-SUMMARY.md` with:
- Files modified (1 file)
- Exact diff of the catch-block edit
- Confirmation that the 3 Phase 3 catch lines are preserved (recordException, setStatus, throw e)
- `mvn -pl consumer-service -am compile` exit code (0)
- Pedagogical note for Plan 05-06 README task: the failure-path triple-signal correlation is the Phase 5 highlight (CONTEXT.md `<specifics>`); README should walk an attendee through `mise run demo:order` 10 times → see the 10th fail → run Loki query → click trace_id → land on Tempo trace with recordException event
- Forward-link: Plan 05-06 README adds the Step 5 section + smoke-tests the entire pipeline + applies the exit tag step-05-logs
</output>
