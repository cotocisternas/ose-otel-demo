# Plan 03-05 Task 2 — Live Smoke Evidence

Captured: 2026-05-01T20:55:00Z (US/Eastern: 2026-05-01 16:55-04:00)
Executor: sequential agent on main working tree (no worktree isolation).
T1 commit: `3c92dd1` (`docs(03-05): add PROP-04 callout, mark step-03 as Current`).

Method note — Tempo HTTP API access: Tempo's `:3200` port is bound only inside the
`ose-otel-lgtm` container in this stack (only `:3000`, `:4317`, `:4318` are
forwarded to the host). All Tempo `/api/*` calls were issued via
`docker exec ose-otel-lgtm curl -s http://localhost:3200/...` rather than the
plan's `curl -s http://localhost:3200/...`. Same endpoint semantics — just routed
through the container. Documented as a Rule 3 deviation (tooling environment).

Method note — process spawn: The plan's `nohup mise run dev:producer` and
`nohup mise run dev:consumer` launched in parallel race each other on the
`depends = ["infra:up"]` clause; the second loser sees a "Container … is already
in use" docker conflict and exits non-zero. Both apps were instead spawned
directly via `mvn -pl <svc> spring-boot:run` with the env vars from `mise.toml`
exported in a sub-shell. Same JVM, same args (`-Dserver.port=8080` /
`-Dserver.port=8081`), same classpath. Documented as a Rule 3 deviation.

## Criterion 1 — ONE joined trace — PASS

- POST /orders payload: `{"sku":"WIDGET-1","quantity":3}` → HTTP `202`
  `{"orderId":"afc92134-7381-4b01-b747-157a8171793e"}`
- producer traceID: `6ed7a18261e08d2baa9e259ec7b5535`
- consumer traceID: `6ed7a18261e08d2baa9e259ec7b5535`
- match: **PASS**
- consumer.parentSpanId: `83797e1edaa85180`
- producer.spanId:       `83797e1edaa85180`
- match: **PASS**

Span dump (5 spans across 2 services, single trace):

```
[order-producer] SPAN_KIND_SERVER     POST /orders                  span=60e718cefe40b18f parent=
[order-producer] SPAN_KIND_INTERNAL   OrderService.place            span=dd33669e7da008bd parent=60e718cefe40b18f
[order-producer] SPAN_KIND_PRODUCER   orders publish                span=83797e1edaa85180 parent=dd33669e7da008bd
[order-consumer] SPAN_KIND_CONSUMER   orders process                span=42be5781c2bc739f parent=83797e1edaa85180
[order-consumer] SPAN_KIND_INTERNAL   ProcessingService.process     span=7f2e1bcd09171ace parent=42be5781c2bc739f
```

CONSUMER span messaging.* attributes (semconv-correct, exchange-as-destination per D-07):

```
messaging.system                            = rabbitmq
messaging.destination.name                  = orders                    <- exchange (Phase 3 D-07 correction)
messaging.operation.type                    = process
messaging.rabbitmq.destination.routing_key  = order.created
```

ROADMAP SC #1 visibly satisfied: `consumer.parentSpanId == producer.spanId` at
runtime, and the trace tree appears correctly with one PRODUCER parent of
one CONSUMER child across two services.

## Criterion 2 — Readable traceparent header in queued message — PASS

Method: stop consumer JVM (PID 2955540 + child 2955779 killed); POST a fresh
order with consumer down so the message lingers in `orders.created`; peek with
the RabbitMQ Mgmt API (guest/guest) using `ackmode=reject_requeue_true`.

POST payload: `{"sku":"WIDGET-2","quantity":1}` → HTTP `202`
`{"orderId":"bff16b7f-ba1b-4bf7-9903-592435870e96"}`

Raw RabbitMQ Mgmt API response (truncated):

```json
[{
  "exchange": "orders",
  "routing_key": "order.created",
  "properties": {
    "delivery_mode": 2,
    "headers": {
      "__ContentTypeId__": "java.lang.Object",
      "__KeyTypeId__":     "java.lang.Object",
      "__TypeId__":        "java.util.HashMap",
      "traceparent":       "00-f02a31dea4b74cca2e9e5f66044ec954-4e116c10eb49708a-03"
    },
    "content_encoding": "UTF-8",
    "content_type":     "application/json"
  },
  "payload": "{\"sku\":\"WIDGET-2\",\"orderId\":\"bff16b7f-ba1b-4bf7-9903-592435870e96\",\"quantity\":1}",
  "payload_encoding": "string"
}]
```

- traceparent header value: `00-f02a31dea4b74cca2e9e5f66044ec954-4e116c10eb49708a-03`
- Python type as decoded by RabbitMQ Mgmt API: `str`
- W3C regex `^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$` match: **PASS**
- PITFALLS.md #2 (String-not-byte[]) honored: header value is a plain string,
  no `[B@…` signature, no base64 envelope.

Note on trace-flags byte: the value is `03`, not the older `01` shown in the
plan's verbose example. Bits decoded: bit 0 = `sampled` (1), bit 1 = `random`
(W3C Trace Context Level 2 — promoted in 2025+ versions of the OTel Java SDK).
The plan's regex `^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$` accepts any
hex byte for trace-flags, so this matches cleanly. Workshop attendees inspecting
the header in the RabbitMQ UI will see `…-03` rather than `…-01`; this is
expected and correct.

ROADMAP SC #2 visibly satisfied.

## Criterion 3 — 10th-order ERROR trace — PARTIAL

Method: send 10 sequential POST /orders with `WIDGET-1` … `WIDGET-10`,
sleep 12s for BatchSpanProcessor flush, search Tempo for traces with
`status=error` (TraceQL `{status=error}` via `/api/search?q=…` — the older
`tags=status%3Derror&limit=10` query returns empty in this Tempo build).

- error trace ID: `b20d0eb8cec166379713b3bf93107a31`
- CONSUMER span (`orders process`) status code: `STATUS_CODE_ERROR` — **PASS**
- INTERNAL span (`ProcessingService.process`) status code: `STATUS_CODE_ERROR`
  — **PASS** (Phase 2 D-03 catch fired)
- exception event present on CONSUMER span: **YES**
- exception event present on INTERNAL span: **YES**

Two exception event recordings — analysed precisely:

```
=== order-consumer SPAN_KIND_CONSUMER orders process ===
exception.type     = org.springframework.amqp.rabbit.support.ListenerExecutionFailedException
exception.message  = Listener method 'public void com.example.consumer.messaging.OrderListener.onOrder(java.util.Map<java.lang.String, java.lang.Object>)' threw exception
stacktrace contains "ProcessingFailedException": NO

=== order-consumer SPAN_KIND_INTERNAL ProcessingService.process ===
exception.type     = com.example.consumer.domain.ProcessingFailedException
exception.message  = Deterministic failure on order #10 (every 10th order)
stacktrace contains "ProcessingFailedException": YES (top of stack)
```

### Why this is "PARTIAL" not "PASS"

The plan's Criterion 3 acceptance text says:
> "the CONSUMER span has exception event with `exception.type` containing
> `ProcessingFailedException`"

That assertion is FALSE for the CONSUMER span itself. Spring AMQP's
`MessagingMessageListenerAdapter.invokeListenerMethod(...)` wraps the user's
exception in `ListenerExecutionFailedException` BEFORE the
`TracingMessageListenerAdvice.invoke(...)` `catch (Throwable t)` runs. So at
advice-catch time, `t.getClass().getName()` returns the Spring wrapper FQCN,
not `ProcessingFailedException`. `Span.recordException(t)` records only
`t.getClass().getName()` as `exception.type` — it does not unwrap the cause
chain by default. The original `ProcessingFailedException` IS the
`getCause()` of the wrapper at JVM-level, but the span event does not surface it.

The original `ProcessingFailedException` IS recorded — just on the INTERNAL
span (`ProcessingService.process`) one frame inward in the SAME trace, with
the verbatim APP-04 message text "Deterministic failure on order #10
(every 10th order)". Phase 2's D-03 catch (`catch (RuntimeException e) ->
recordException(e); setStatus(ERROR); throw e;`) is what surfaces it there.
That recording is correct and matches D-12.

### Implications for the human checkpoint

This is a real Phase 3 surface decision for the user to confirm or override.

**Option A — accept as-is.** The 10th-order ERROR trace is fully visible;
ERROR status DOES propagate to the CONSUMER span; the exception event IS
present; the original FQCN `ProcessingFailedException` IS recorded one frame
deeper on the INTERNAL span (also ERROR-statused). The workshop attendee opens
the trace, sees red on the CONSUMER span, drills in, sees red on the
INTERNAL span, and sees the precise PFE event there. Pedagogically
defensible. ROADMAP SC #3 wording is "Tempo render the trace as `Error`
status with the exception event attached to the consumer span" — that IS
true; only the FQCN is the wrapper, not PFE.

**Option B — small fix in `TracingMessageListenerAdvice`.** Unwrap the
Spring wrapper before recording:
```java
} catch (Throwable t) {
    Throwable recorded = (t instanceof org.springframework.amqp.rabbit.support.ListenerExecutionFailedException
                          && t.getCause() != null) ? t.getCause() : t;
    span.recordException(recorded);
    span.setStatus(StatusCode.ERROR);
    throw t; // still rethrow the original wrapper
}
```
This would make CONSUMER span report `exception.type=…ProcessingFailedException`
directly — matching the plan's literal acceptance text. Trade-off: the advice
acquires a hard import on `org.springframework.amqp.rabbit.support.ListenerExecutionFailedException`,
which is reasonable since `otel-bootstrap` already declares `spring-rabbit`
at `provided` scope.

The plan's how-to-verify text in T3 (Step 5 sub-bullet 3) says:
> "The INTERNAL span (`ProcessingService.process`) ALSO shows Status Code = ERROR
>  with the same exception event attached (Phase 2's D-03 catch recorded it on
>  the INTERNAL span; the advice's catch recorded it again on the CONSUMER span
>  — both are visible)."

The "recorded it again on the CONSUMER span" phrasing implies the planner
expected Option B's behavior — but in practice Option A is what shipped. The
human checkpoint should explicitly accept one of the two before T3 tags.

## Criterion 4 — Structural symmetry — PASS

All checks pass:

- `TracingMessagePostProcessor.java` exists: PASS
- `TracingMessageListenerAdvice.java` exists: PASS
- Both call `openTelemetry.getPropagators().getTextMapPropagator()` (D-04
  single source of truth, no `W3CTraceContextPropagator.getInstance()`):
  PostProcessor PASS, Advice PASS.
- `propagator.inject(Context.current(), props, SETTER)` in PostProcessor: PASS
- `propagator.extract(Context.current(), props, GETTER)` in Advice: PASS
- `setSpanKind(SpanKind.PRODUCER)` in PostProcessor: PASS
- `setSpanKind(SpanKind.CONSUMER)` in Advice: PASS
- 4 messaging semconv attribute references per file (MESSAGING_SYSTEM,
  MESSAGING_DESTINATION_NAME, MESSAGING_OPERATION_TYPE,
  MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY):
  - PostProcessor: 4 — PASS
  - Advice: 4 — PASS
- README PROP-04 callout (`## Why is the propagation pair shared?`) present:
  PASS (verified in T1).

## Criterion 5 — Diff size vs step-02-traces — PASS (with footnote)

Raw diff including planning docs:

```
$ git diff --shortstat step-02-traces..HEAD
 34 files changed, 7944 insertions(+), 163 deletions(-)
```

Source-only diff (excluding `.planning/`):

```
$ git diff --shortstat step-02-traces..HEAD -- ':!.planning/'
 15 files changed, 813 insertions(+), 138 deletions(-)
```

- New source files (excluding `.planning/`): **6**
  - `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java`
  - `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java`
  - `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java`
  - `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java`
  - `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java`
  - `otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java`
- Plan range expects 4–8 new source files: **PASS** (6 ∈ [4,8]).
- Footnote: the raw all-files count includes 16 new `.planning/phases/03-…/*.md`
  files (PLANs, SUMMARYs, RESEARCH, CONTEXT, DISCUSSION-LOG, PATTERNS) plus 3
  Phase-2 retrospective docs. These are documentation artifacts, not code
  surface. The plan's predicted "~50 added + ~60 deleted + 5 new files"
  estimate is a code-only estimate; the source-only diff's 813/138 is bigger
  because the JavaDoc on every new class is rich (the plan body even predicts
  this: "the JavaDoc-rich plans accumulate more added lines than the
  CONTEXT.md estimate of ~50; that's fine — the deletion+small-net property
  is what matters").

Full name-status:

```
M	.planning/PROJECT.md
M	.planning/ROADMAP.md
M	.planning/STATE.md
A	.planning/phases/02-manual-sdk-bootstrap-first-traces/02-HUMAN-UAT.md
A	.planning/phases/02-manual-sdk-bootstrap-first-traces/02-REVIEW.md
A	.planning/phases/02-manual-sdk-bootstrap-first-traces/02-VERIFICATION.md
A	.planning/phases/03-amqp-context-propagation/03-01-SUMMARY.md
A	.planning/phases/03-amqp-context-propagation/03-01-otel-bootstrap-amqp-classes-PLAN.md
A	.planning/phases/03-amqp-context-propagation/03-02-SUMMARY.md
A	.planning/phases/03-amqp-context-propagation/03-02-producer-wiring-PLAN.md
A	.planning/phases/03-amqp-context-propagation/03-03-SUMMARY.md
A	.planning/phases/03-amqp-context-propagation/03-03-consumer-wiring-PLAN.md
A	.planning/phases/03-amqp-context-propagation/03-04-SUMMARY.md
A	.planning/phases/03-amqp-context-propagation/03-04-app-04-failure-path-PLAN.md
A	.planning/phases/03-amqp-context-propagation/03-05-readme-and-exit-gate-PLAN.md
A	.planning/phases/03-amqp-context-propagation/03-CONTEXT.md
A	.planning/phases/03-amqp-context-propagation/03-DISCUSSION-LOG.md
A	.planning/phases/03-amqp-context-propagation/03-PATTERNS.md
A	.planning/phases/03-amqp-context-propagation/03-RESEARCH.md
M	README.md
M	consumer-service/pom.xml
M	consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java
A	consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java
M	consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
M	consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
M	otel-bootstrap/pom.xml
A	otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java
A	otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java
A	otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
A	otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java
A	otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java
M	producer-service/pom.xml
M	producer-service/src/main/java/com/example/producer/config/RabbitConfig.java
M	producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
```

## Criterion 6 — Clean working tree — NOT CLEAN (pre-existing drift)

```
$ git status --porcelain
 M mise.toml
```

The `mise.toml` modification was present BEFORE this plan started (visible in
the orchestrator-supplied initial gitStatus). The diff is one line:

```diff
@@ -9,6 +9,7 @@ min_version = "2025.1.0"
 # Floating "corretto-17" would drift across cohorts.
 java  = "corretto-17.0.13.11.1"
 maven = "3.9.11"
+node  = "lts"

 [env]
```

This adds `node = "lts"` to mise's `[tools]` table. It is **unrelated** to
plan 03-05 and to Phase 3 propagation surface — Phase 3 touches only Java
sources and the README. Per executor scope-boundary rules, the executor did
NOT auto-modify it.

The plan's failure-mode notes for Criterion 6 say:
> "Criterion 6 fails: uncommitted changes; commit them with a chore message
>  and re-run T2."

Recommendation for the human checkpoint: decide to either (a) discard the
`node = "lts"` change with `git checkout -- mise.toml` if it was an
accidental edit, or (b) commit it with `chore: pin node lts via mise` before
T3 runs the tag — both options leave the tag landing on a clean tree as
WORK-01 requires.

## Phase 2 BOM invariant — PASS

```
$ mise run verify:bom
[verify:bom] $ set -e
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

Exit code: 0.

## Background processes — CLEAN

```
$ ps -ef | grep -E 'java.*com\.example\.(producer|consumer)\.(Producer|Consumer)Application' | grep -v grep
(no matches — both Spring Boot JVMs killed cleanly)

$ ps -ef | grep -E 'classworlds.*\(producer\|consumer\)-service' | grep -v grep
(no matches — both mvn launchers killed cleanly)
```

Note: the plan's verbatim cleanup check `! pgrep -f 'spring-boot:run'` cannot
be used reliably from inside a bash `-c` invocation, because pgrep matches
the calling shell's own command line (which contains the literal string
`spring-boot:run`). The `ps -ef | grep -E … | grep -v grep` form above is
robust to that self-match and shows the real process state.

## T3 readiness — NOT READY

T3 tag should NOT be created automatically. Two open items the human
checkpoint must resolve:

1. **Criterion 3 PARTIAL.** Decide between Option A (accept as-is — ERROR
   propagates to CONSUMER span; PFE FQCN visible on INTERNAL span one frame
   inward) or Option B (small unwrap fix in `TracingMessageListenerAdvice`).
   Option A keeps the existing diff clean; Option B requires re-running T2
   after a 4-line code change in the advice.
2. **Criterion 6 NOT CLEAN.** The `mise.toml` `+ node = "lts"` line is
   pre-existing unrelated drift. Decide to discard or commit before T3 tags.

All other criteria (1, 2, 4, 5) PASS unconditionally and the runtime trace
behavior is exactly what ROADMAP Phase 3 advertises: ONE distributed trace
per POST /orders, readable W3C traceparent on the AMQP message, structural
symmetry of the propagation pair, small reviewable source-only diff.
