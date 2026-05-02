---
phase: 05-logs-correlation
plan: 05
subsystem: observability
tags: [slf4j, logback, logging, consumer, error-handling, otel, log-correlation, app-04]

# Dependency graph
requires:
  - phase: 05-logs-correlation
    provides: "consumer SDK loggerProvider + OpenTelemetryAppender install + logback-spring.xml with MDC injector + logback-appender + logback-mdc dependencies (Wave 2 — plan 05-03)"
  - phase: 03-amqp-context-propagation
    provides: "ProcessingService.process catch block with span.recordException(e) + span.setStatus(StatusCode.ERROR) + throw e (TRACE-09); CONSUMER span wraps the listener body so Span.current() inside ProcessingService is valid; ProcessingFailedException is caught by the RuntimeException catch (D-11)"
  - phase: 02-trace-baseline
    provides: "D-01 inline span template + D-03 catch shape (recordException + setStatus + rethrow) on the INTERNAL span"
  - phase: 01-skeleton
    provides: "ProcessingService class structure + AtomicInteger counter + Map<String, Object> message contract carrying orderId"

provides:
  - "First LOG.error call in the codebase — establishes the SLF4J error-logging idiom with throwable-as-last-arg"
  - "Triple-signal-on-failure correlation primitive: LOG.error (Loki) + span.recordException (Tempo) + ERROR status on INTERNAL+CONSUMER spans, all stamped with the same trace_id/span_id from the active INTERNAL span"
  - "Loki failure-path query target for plan 05-06's workshop README walkthrough: `{service_name=\"order-consumer\"} |~ \"ERROR\"` resolves the deterministic 10th-order failure"
  - "Static SLF4J Logger field + slf4j imports in ProcessingService.java — confirms the no-Lombok / uppercase-LOG / class-literal pattern (PATTERNS §S-1) carries to consumer domain layer"

affects:
  - phase 05 plan 06 (README Step 5 walkthrough — failure-path triple-signal correlation is the Phase 5 highlight per CONTEXT.md `<specifics>`; README walks attendees through `mise run demo:order` × 10 → 10th fails → Loki ERROR query → click trace_id → Tempo trace with recordException event)
  - any future phase introducing additional LOG.error sites (precedent: throwable-as-last-arg, uppercase static final LOG, no @Slf4j)

# Tech tracking
tech-stack:
  added: []  # No new artifacts — slf4j-api was already on the classpath via Spring Boot starter (used by Phase 1's OrderListener.LOG.info)
  patterns:
    - "SLF4J error idiom: LOG.error(\"message: key={}\", value, exception) with throwable as the LAST positional argument (NOT bound to a {} placeholder; Logback renders the stack trace automatically)"
    - "Triple-signal-on-failure correlation: pair every span.recordException(e) with a corresponding LOG.error(\"...\", e) inside the same span context"
    - "LOG.error precedes span.recordException(e) in catch blocks — top-to-bottom narrative is log → record → rethrow"

key-files:
  created: []
  modified:
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java

key-decisions:
  - "D-16 host choice option (a): LOG.error lives in ProcessingService.process catch block (failure source), NOT in the listener advice's catch (option b — shared infrastructure)."
  - "LOG.error positioned BEFORE span.recordException(e) in the catch block: workshop attendees read top-to-bottom as log → record → rethrow; defense in depth if recordException ever throws."
  - "Throwable `e` is the LAST positional argument in LOG.error — SLF4J's special-case throwable handling renders the full stack trace via Logback. Binding `e` to a {} placeholder would call e.toString() and lose the stack trace."
  - "OrderListener.onOrder happy-path LOG.info('OrderCreated received: orderId={}') untouched (D-17) — once Wave 2's OpenTelemetryAppender.install completes, it gets trace_id stamping automatically with no code change."

patterns-established:
  - "PATTERNS §E (LOG.error idiom): first LOG.error site in the codebase. Two-arg-plus-throwable signature, throwable-as-last-arg, paired with span.recordException for triple-signal-on-failure correlation."
  - "Catch-block ordering convention: when adding a LOG.error to an existing recordException catch, place LOG.error BEFORE recordException."

requirements-completed: [LOG-04]

# Metrics
duration: 1m 35s
completed: 2026-05-01
---

# Phase 5 Plan 05: Consumer Error Log Summary

**First LOG.error in the codebase — single SLF4J error call inside ProcessingService's APP-04 catch block, paired with the existing span.recordException for triple-signal-on-failure correlation (Loki ERROR log ↔ Tempo recordException event ↔ ERROR span status).**

## Performance

- **Duration:** 1m 35s
- **Started:** 2026-05-02T02:05:52Z
- **Completed:** 2026-05-02T02:07:27Z
- **Tasks:** 1 / 1
- **Files modified:** 1

## Accomplishments

- Established the FIRST `LOG.error` pattern in the codebase (PATTERNS §E — no analog existed; Phase 5 establishes the idiom).
- Wired the triple-signal-on-failure correlation primitive on the CONSUMER side: a single `LOG.error("order processing failed: orderId={}", orderId, e)` call inside the existing Phase 3 catch block, pairing the Loki log signal with the existing `span.recordException(e)` Tempo signal — both stamped with the same trace_id/span_id from the active INTERNAL span (which is wrapped by Phase 3's CONSUMER span, joining the producer's trace via W3C propagation).
- Preserved Phase 3's catch contract byte-for-byte: `span.recordException(e)`, `span.setStatus(StatusCode.ERROR)`, and `throw e;` are unchanged. Phase 5 ADDED lines, did not REWRITE the existing logic.
- Left the producer-service untouched (that is plan 05-04's scope, executing in parallel in Wave 3) and left OrderListener.onOrder's happy-path LOG.info untouched (D-17 — gets trace_id stamping automatically once Wave 2's OpenTelemetryAppender install runs).

## Task Commits

1. **Task 1: Add SLF4J Logger field + LOG.error inside the APP-04 catch block of ProcessingService** — `162543e` (feat)

## Exact Diff (catch-block edit)

```diff
@@ ProcessingService.java @@
 import io.opentelemetry.context.Scope;

+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
 import org.springframework.stereotype.Service;

 @Service
 public class ProcessingService {
+    private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);
+
     private final Tracer tracer;

@@ catch block @@
         } catch (RuntimeException e) {
-            // D-03 catch shape from Phase 2 — preserved unchanged.
+            // D-03 catch shape from Phase 2 — preserved unchanged below.
             // ProcessingFailedException extends RuntimeException, so it
             // is caught here (TRACE-09). The advice's catch (Throwable)
             // also records this on the CONSUMER span when the rethrow
             // bubbles up.
+            //
+            // Phase 5 D-16: LOG.error is the Loki-side counterpart to
+            // span.recordException — both signals carry the same trace_id
+            // and span_id (Span.current() is valid here per RESEARCH
+            // Finding #7; the active span is this method's INTERNAL span,
+            // wrapped by Phase 3's CONSUMER span). The Loki query
+            // {service_name="order-consumer"} |~ "<traceId>" returns this
+            // log line, and clicking the trace_id field opens the trace
+            // in Tempo with the recordException event already attached on
+            // the CONSUMER span. This is the FIRST LOG.error in the
+            // codebase — Phase 5 establishes the triple-signal-on-failure
+            // idiom (Loki ERROR log + Tempo recordException event + ERROR
+            // status on both INTERNAL and CONSUMER spans).
+            //
+            // SLF4J's throwable-as-last-arg idiom: the trailing `e` is
+            // treated as the exception (its stack trace is rendered by
+            // Logback automatically) — NOT bound to a {} placeholder.
+            // The single {} placeholder matches `orderId`.
+            Object orderId = order.get("orderId");
+            LOG.error("order processing failed: orderId={}", orderId, e);
             span.recordException(e);
             span.setStatus(StatusCode.ERROR);
             throw e;
         } finally {
```

## Phase 3 Catch Contract Preservation

The 3 Phase 3 lines are preserved byte-for-byte (verified by grep):

| Line                                | Status     |
| ----------------------------------- | ---------- |
| `span.recordException(e);`          | ✓ unchanged |
| `span.setStatus(StatusCode.ERROR);` | ✓ unchanged |
| `throw e;`                          | ✓ unchanged |

## Verification

| Check                                                                                          | Result |
| ---------------------------------------------------------------------------------------------- | ------ |
| `grep 'import org.slf4j.Logger;'`                                                              | OK     |
| `grep 'import org.slf4j.LoggerFactory;'`                                                       | OK     |
| `grep 'private static final Logger LOG = LoggerFactory.getLogger(ProcessingService.class);'`   | OK     |
| `grep 'LOG.error('`                                                                            | OK     |
| LOG.error inside `catch (RuntimeException e)` block (awk range scan)                           | OK     |
| LOG.error appears BEFORE `span.recordException(e)` in catch block                              | OK     |
| `grep -E 'LOG.error\(.*[, ]e\)'` (throwable as trailing positional arg)                        | OK     |
| `grep 'span.recordException(e)'` (Phase 3 preserved)                                           | OK     |
| `grep 'span.setStatus(StatusCode.ERROR)'` (Phase 3 preserved)                                  | OK     |
| `grep 'throw e;'` (Phase 3 preserved)                                                          | OK     |
| `! grep '@Slf4j'` (no Lombok)                                                                  | OK     |
| `grep -c 'LOG\.\(info\|warn\|debug\|trace\)'` equals 0 (only LOG.error in this file)           | OK     |
| `grep 'OrderCreated received: orderId={}'` in OrderListener.java (D-17 untouched)              | OK     |
| `mvn -B -pl consumer-service -am compile`                                                      | exit 0 (BUILD SUCCESS, 0.685s reactor time) |

All 8 frontmatter `must_haves.truths` (`LOG-04-consumer-error`, `D-16-position`, `slf4j-imports-consumer`, `error-throwable-arg`, `no-lombok-consumer`, `phase-3-catch-preserved`, `build-clean-consumer`, `D-17-onorder-untouched`) confirmed passing.

## Files Created/Modified

- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` — Added 2 SLF4J imports, 1 static Logger field, 1 LOG.error call inside the APP-04 catch block (positioned before span.recordException), and a ~14-line comment block explaining the Phase 5 D-16 addition + the SLF4J throwable-as-last-arg idiom. 25 insertions, 1 deletion.

## Decisions Made

- **D-16 host = option (a) — ProcessingService.** The plan committed to option (a) (failure source) over option (b) (listener advice's shared infrastructure catch); option (a) is closer to failure semantics and pedagogically more direct. No deviation from the plan; this is the recorded decision.
- **LOG.error position = BEFORE span.recordException.** The plan made the recommendation; executor honored it. Rationale: top-to-bottom narrative `log → record → rethrow`, defense in depth if recordException ever throws, and consistency with PATTERNS §E.
- **`Object orderId` (no cast).** The map is `Map<String, Object>` and the `{}` placeholder calls `toString()` which is correct for any Object including `null`. Avoided unnecessary cast — keeps the call site small.

## Deviations from Plan

None — plan executed exactly as written. The single Task 1 was applied as three contiguous edits (imports → field → catch block), all verifications passed on first compile, and no auto-fixes (Rule 1/2/3) were necessary.

**Total deviations:** 0
**Impact on plan:** Zero — straight-through execution.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required for this plan. The triple-signal-on-failure correlation will become observable end-to-end once plan 05-06's smoke test runs (`mise run demo:order` × 10 against a running stack), which is plan 05-06's scope.

## Pedagogical Note for Plan 05-06 README Task

The failure-path triple-signal correlation is the Phase 5 highlight (CONTEXT.md `<specifics>` — "Triple-signal correlation on the failure path is the Phase 5 highlight"). Plan 05-06's README Step 5 section should walk a workshop attendee through the following sequence:

1. Run `mise run demo:order` 10 times (the deterministic counter triggers `ProcessingFailedException` on the 10th call, per APP-04 / D-11).
2. Observe the CONSUMER service's stderr — a single `LOG.error("order processing failed: orderId=<UUID>")` line appears with the orderId of the failing message and the `ProcessingFailedException` stack trace (rendered by Logback's default throwable converter via the SLF4J trailing-arg idiom).
3. In Grafana → Loki, run `{service_name="order-consumer"} |~ "ERROR"` (or `|~ "<orderId>"`) and find the LOG.error line. Notice the `trace_id` and `span_id` MDC fields stamped by Wave 2's `OpenTelemetryAppender` install.
4. Click the `trace_id` field's "view trace" action — Grafana's Loki→Tempo derived field (D-20) opens the trace in Tempo with both INTERNAL and CONSUMER spans showing ERROR status, the CONSUMER span carrying the `recordException` event, and the producer's HTTP span as the trace root (Phase 3 propagation joined them).
5. Show that the same orderId, same trace_id, same span_id appear in the LOG.error line and the recordException event — that's the triple-signal correlation made visible.

## Forward-Link

- **Plan 05-06** adds the README Step 5 section, applies the `step-05-logs` git tag, and runs the end-to-end smoke test that exercises this LOG.error site.
- **Wave 3 sibling: plan 05-04** adds two `LOG.info` happy-path call sites on the producer side (`OrderController.create` + `OrderPublisher.publish`) — out of scope here. Together with this plan's `LOG.error`, the three new business-log call sites complete D-15/D-16's call-site additions.

## Next Phase Readiness

- **Phase 5 highlight wired:** LOG.error + recordException pairing is in place; Wave 4 plan 05-06 can proceed to README + smoke test.
- **No blockers.** Consumer service compiles cleanly; the listener's happy-path log + this catch-block error log together cover the consumer-side logging surface.
- **No concerns.** The patch is minimal (25 insertions, 1 deletion in a single file), the 3 Phase 3 contract lines are preserved byte-for-byte, and no producer-side files were touched (respecting plan 05-04's parallel-wave scope).

## Self-Check: PASSED

- File `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exists and contains the LOG.error call (verified via Read tool above).
- Commit `162543e` exists in git log (verified via `git rev-parse --short HEAD` after commit).

---
*Phase: 05-logs-correlation*
*Plan: 05*
*Completed: 2026-05-01*
