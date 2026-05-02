---
plan: 03-05-readme-and-exit-gate
phase: 03-amqp-context-propagation
status: complete
completed: 2026-05-01
duration: ~25 min (T1 + T2 + human checkpoint + Criterion-3 fix + retest + T3 tag)
---

# Plan 03-05 — README + step-03 exit gate

## Outcome

PHASE 3 SHIPPED. Annotated tag `step-03-context-propagation` lands at commit
`ae757e8` with all five Phase 3 success criteria simultaneously green.

## Commits (in order)

| SHA | Subject |
|-----|---------|
| `3c92dd1` | docs(03-05): add PROP-04 callout, mark step-03 as Current |
| `fe59eb6` | docs(03-05): capture T2 live-smoke evidence for human checkpoint |
| `50492cb` | chore: pin node lts via mise (out-of-scope mise.toml drift; user approved) |
| `ae757e8` | fix(03-05): unwrap ListenerExecutionFailedException so CONSUMER span shows ProcessingFailedException |
| `step-03-context-propagation` (tag) | annotated tag at `ae757e8` |

## README.md final structure (after T1)

`# OSE OTel Demo`
- `## Prerequisites`
  - `### Required tools`
  - `### Required free ports`
  - `### IDE setup (one-time, IntelliJ IDEA)`
- `## One-time setup`
- `## First run`
- `## Workshop checkpoints`
- `## Reading the code`
- `## Why is OtelSdkConfiguration.java duplicated?`
- `## Why is the propagation pair shared?` ← **NEW (PROP-04)**
- `## What's NOT here yet`

The PROP-04 section is placed immediately after Phase 2's DOC-05 callout and
reads as a parallel-symmetric pair: per-service code is duplicated; cross-service
code is shared; the symmetry IS the lesson. The Workshop checkpoints list now
marks `step-03-context-propagation` as **Current** (was `step-02-traces`); the
obsolete "No `traceparent` header injection on AMQP (Phase 3)" forward-pointer
bullet is removed from "What's NOT here yet".

## Phase 3 success criteria — final results

### Criterion 1 — ONE joined trace — PASS

- POST `/orders` payload `{"sku":"WIDGET-1","quantity":3}` → HTTP 202
- producer traceID = consumer traceID = `6ed7a18261e08d2baa9e259ec7b5535`
- consumer.parentSpanId = producer.spanId = `83797e1edaa85180`

```
[order-producer] SERVER    POST /orders                  span=60e718cefe40b18f
[order-producer] INTERNAL  OrderService.place            span=dd33669e7da008bd parent=60e718cefe40b18f
[order-producer] PRODUCER  orders publish                span=83797e1edaa85180 parent=dd33669e7da008bd
[order-consumer] CONSUMER  orders process                span=42be5781c2bc739f parent=83797e1edaa85180
[order-consumer] INTERNAL  ProcessingService.process     span=7f2e1bcd09171ace parent=42be5781c2bc739f
```

### Criterion 2 — Readable traceparent header — PASS

RabbitMQ Mgmt API peek (`POST /api/queues/%2F/orders.created/get`) on a queued
message returns:
```
"traceparent": "00-f02a31dea4b74cca2e9e5f66044ec954-4e116c10eb49708a-03"
```
String value, no `[B@…` signature; matches W3C regex
`^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$`. The `03` trace-flags byte
indicates W3C Trace Context Level 2 (sampled + random). PITFALLS.md #2 honored.

### Criterion 3 — 10th-order ERROR trace — PASS (after fix)

Initial T2 result was PARTIAL: the CONSUMER span showed
`exception.type=org.springframework.amqp.rabbit.support.ListenerExecutionFailedException`
(Spring's wrapper) instead of `ProcessingFailedException`. User approved
Option B (4-line cause-unwrap fix in `TracingMessageListenerAdvice.invoke`'s
`catch (Throwable t)`). After the fix:

- error trace ID: `537dc7e71b7447118a7ea3060ee86ab`
- CONSUMER span (`orders process`): `STATUS_CODE_ERROR`
- CONSUMER span exception event:
  - `exception.type = com.example.consumer.domain.ProcessingFailedException`
  - `exception.message = Deterministic failure on order #10 (every 10th order)`
- INTERNAL span (`ProcessingService.process`): `STATUS_CODE_ERROR` with the
  same exception event (Phase 2's D-03 catch).

The wrapper is still rethrown so Spring AMQP's listener container sees the
expected exception type and applies the NACK + drop path (D-13's
`defaultRequeueRejected=false`).

### Criterion 4 — Structural symmetry — PASS

Both `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` use
`openTelemetry.getPropagators().getTextMapPropagator()` (D-04 single source of
truth); one calls `.inject(...)`, one calls `.extract(...)`; one sets
`SpanKind.PRODUCER`, one sets `SpanKind.CONSUMER`; both reference the same 4
messaging semconv attributes (system, destination.name, operation.type,
rabbitmq.destination.routing_key). README PROP-04 callout present and links
all four classes.

### Criterion 5 — Diff size — PASS

```
$ git diff --shortstat step-02-traces..step-03-context-propagation
 36 files changed, 8325 insertions(+), 163 deletions(-)
```

Source-only (excluding `.planning/` documentation):
```
$ git diff --shortstat step-02-traces..step-03-context-propagation -- ':!.planning/'
 16 files changed, 828 insertions(+), 138 deletions(-)
```

New source files (6 — within the plan's predicted [4,8] range):
- `consumer-service/.../domain/ProcessingFailedException.java`
- `otel-bootstrap/.../amqp/MessagePropertiesGetter.java`
- `otel-bootstrap/.../amqp/MessagePropertiesSetter.java`
- `otel-bootstrap/.../amqp/TracingMessageListenerAdvice.java`
- `otel-bootstrap/.../amqp/TracingMessagePostProcessor.java`
- `otel-bootstrap/.../amqp/MessagePropertiesRoundTripTest.java`

The deletion-is-the-diff property holds: producer's `OrderPublisher.java`
shrank from 83 → 21 lines (-62/+0); consumer's `OrderListener.java` shrank
from 81 → 54 lines (-27/+0). Both lost their inline span body and their
`Tracer` constructor parameter — the visible payoff of moving propagation
into the shared module.

### Criterion 6 — Clean working tree — PASS

`git status --porcelain` returns empty at the moment of tagging.
Pre-existing `mise.toml` `+ node = "lts"` drift was committed first as
`50492cb chore: pin node lts via mise` (user-approved scope decision).

## Tag verification

```
$ git for-each-ref --format='%(objecttype) %(objectname:short) %(refname)' refs/tags/step-03-context-propagation
tag e9fa63f refs/tags/step-03-context-propagation
```

`objecttype = tag` (annotated, not `commit` which would mean lightweight). Tag
message references "Phase 3", "ONE distributed trace",
"consumer.parentSpanId == producer.spanId", and the 5 success criteria.

## Reproducibility self-test (SC #5)

```
$ git -C /tmp clone --branch step-03-context-propagation --depth 1 file://$(pwd) verify-step-03
$ cd /tmp/verify-step-03 && mise trust && mise run verify:bom && mvn -pl otel-bootstrap test
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Push reminder

The annotated tag is **local-only**. To publish:
```sh
git push origin main
git push origin step-03-context-propagation
```
Same git-safety convention as Phase 1's `step-01-baseline` and Phase 2's
`step-02-traces` — the user decides when to push.

## Deviations

1. **Cause-unwrap fix to advice** (commit `ae757e8`). Plan 03-05 T3 step 5
   sub-bullet 3 implied the planner's expectation that the CONSUMER span
   would surface PFE directly, but the as-implemented code in plan 03-01
   recorded the Spring wrapper. The 4-line fix is contained inside the
   advice's existing `catch (Throwable t)` block; rethrow path unchanged.
   Documented in the unwrap commit message.
2. **Out-of-scope mise.toml commit** (commit `50492cb`). The `node = "lts"`
   line was in the working tree before Phase 3 started. User approved
   committing it as `chore: pin node lts via mise` rather than discarding,
   for future node-based tooling (gsd-sdk runs on node).
3. **T2 method substitutions** documented in the executor's
   `03-05-T2-evidence.md`: Tempo `:3200` accessed via `docker exec` (not
   port-forwarded to host); apps spawned via direct `mvn spring-boot:run`
   (the `nohup mise run dev:*` pair raced on the `depends=["infra:up"]`
   docker-compose lock).

## Files modified by this plan

- `README.md` (T1: PROP-04 callout + Current marker move + obsolete bullet removal)
- `mise.toml` (chore commit; pre-existing node pin)
- `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` (cause-unwrap fix)
- `.planning/phases/03-amqp-context-propagation/03-05-T2-evidence.md` (new — T2 evidence file)
- 1 git ref: `refs/tags/step-03-context-propagation`
