---
id: 03-05-readme-and-exit-gate
phase: 03-amqp-context-propagation
plan: 05
type: execute
wave: 4
depends_on: [03-01-otel-bootstrap-amqp-classes, 03-02-producer-wiring, 03-03-consumer-wiring, 03-04-app-04-failure-path]
requirements: [PROP-03, PROP-04, APP-04, TRACE-09]
files_modified:
  - README.md
autonomous: false
objective: "Land the Phase 3 documentation delta (Step 3 section + the PROP-04 'Why is the propagation pair shared?' callout that contrasts with Phase 2's 'Why is OtelSdkConfiguration.java duplicated?' callout) and the Phase 3 exit gate (WORK-01 — annotated git tag step-03-context-propagation on main). The tag is created ONLY after a human-verified end-to-end smoke against the live stack confirms ALL FIVE Phase 3 success criteria from ROADMAP.md are simultaneously green: ONE joined trace per POST /orders / readable traceparent String header in RabbitMQ Mgmt UI / 10th-order ERROR trace with exception event / structural symmetry of the propagation pair / step-02→step-03 git diff is small and reviewable."
must_haves:
  truths:
    - "README.md gains a `## Why is the propagation pair shared?` section immediately AFTER the existing `## Why is OtelSdkConfiguration.java duplicated?` section — single paragraph parallel-symmetric to the DOC-05 callout, explicitly naming the otel-bootstrap propagation pair as INTENTIONALLY shared and noting the structural asymmetry: per-service code = duplicated; cross-service code = shared (PROP-04)"
    - "README.md `## Workshop checkpoints` section is updated: step-03-context-propagation is the **Current** marker (was step-02-traces); step-02-traces gets back-marked as historical (drop the **Current.** marker)"
    - "README.md `## What's NOT here yet` bullet 'No traceparent header injection on AMQP (Phase 3)' is REMOVED (the feature is now present)"
    - "All 5 Phase 3 success criteria from ROADMAP.md are simultaneously green at the moment of tagging: ONE trace per POST /orders with consumer.parentSpanId == producer.spanId (SC #1) / RabbitMQ Mgmt UI shows readable traceparent String header (SC #2) / 10th-order ERROR trace with exception event on consumer span (SC #3) / structural symmetry of TracingMessagePostProcessor + TracingMessageListenerAdvice in otel-bootstrap (SC #4) / git diff step-02-traces..step-03-context-propagation is small and reviewable (SC #5 — ~50 added + ~60 deleted + 5 new files)"
    - "Annotated git tag `step-03-context-propagation` exists on the working branch (typically main); points at a commit where ALL five Phase 3 success criteria are simultaneously true; tag is annotated (-a flag), NOT lightweight"
    - "Repository working tree is clean at the moment the tag is applied"
    - "git checkout step-03-context-propagation reproduces the green Phase-3 state (SC #5 verifiable in a temp clone)"
  artifacts:
    - path: "README.md"
      provides: "Phase 3 documentation delta: PROP-04 'Why is the propagation pair shared?' callout (parallel to DOC-05); workshop-checkpoints update marking step-03-context-propagation as Current; deletion of the 'No traceparent header injection' bullet from 'What's NOT here yet'"
      contains: "Why is the propagation pair shared"
    - path: "(git ref) refs/tags/step-03-context-propagation"
      provides: "Immutable annotated workshop checkpoint marking Phase 3 exit; THIRD of the six WORK-01 tags"
      contains: "(annotated tag message — references ONE joined trace + the headline lesson)"
  key_links:
    - from: "README.md '## Why is the propagation pair shared?'"
      to: "README.md '## Why is OtelSdkConfiguration.java duplicated?'"
      via: "Parallel-symmetric structure — together the two callouts teach 'per-service code = duplicated; cross-service code = shared' (PROP-04)"
      pattern: "propagation pair"
    - from: "git tag step-03-context-propagation"
      to: "Working ONE-trace state (producer + consumer in SAME trace in Tempo per POST /orders; consumer.parentSpanId == producer.spanId)"
      via: "Commit pointed at by the tag reproduces the Phase 3 success-criteria-green state"
      pattern: "step-03-context-propagation"
    - from: "git diff step-02-traces..step-03-context-propagation"
      to: "Small, readable changeset focused on the propagation pair (ROADMAP SC #5)"
      via: "~50 added + ~60 deleted + 5 new files; the deletion-is-the-diff property makes it reviewable in one viewing"
      pattern: "step-02-traces"
---

<objective>
Land the Phase 3 documentation gate (PROP-04 README callout in `README.md` — parallel-symmetric to Phase 2's DOC-05 callout) and the Phase 3 exit gate (WORK-01 — annotated git tag `step-03-context-propagation` on the working branch). The tag is created ONLY after a human-verified end-to-end smoke against the live stack confirms all five Phase 3 success criteria from ROADMAP.md are simultaneously green:
1. ONE distributed trace per POST /orders (consumer.parentSpanId == producer.spanId — visible in Tempo)
2. RabbitMQ Mgmt UI shows readable `traceparent` header value `00-<32-hex>-<16-hex>-01` — String, not byte[]/`[B@...`
3. 10th-order failure produces ERROR trace in Tempo with exception event on the consumer span
4. Structural symmetry of `TracingMessagePostProcessor` + `TracingMessageListenerAdvice` in `otel-bootstrap` (one inject method, one extract method)
5. `git diff step-02-traces..step-03-context-propagation` is small and reviewable (~50 added + ~60 deleted + 5 new files)

The tag is the workshop attendee's "fixed state" checkpoint — it pairs with `step-02-traces` to produce the workshop's most powerful pedagogical delta. Per ROADMAP, the broken-vs-fixed visual (Phase 7's DOC-04) will pair this state's Tempo screenshot side-by-side with `step-02-traces`'s two-trace state.

Purpose: PROP-04 (README half — the architectural-asymmetry teaching), PROP-03 (the WORK-01 tag at the runtime-verified joined-trace state), APP-04 + TRACE-09 (verified runtime — the 10th-order ERROR trace), and WORK-01 (third of the six annotated tags).

Output: 1 modified README.md + 1 git ref (refs/tags/step-03-context-propagation) + a clean commit history committed.
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
@.planning/phases/03-amqp-context-propagation/03-02-producer-wiring-PLAN.md
@.planning/phases/03-amqp-context-propagation/03-03-consumer-wiring-PLAN.md
@.planning/phases/03-amqp-context-propagation/03-04-app-04-failure-path-PLAN.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-06-readme-and-exit-gate-PLAN.md
@CLAUDE.md
@README.md
@mise.toml
</context>

<tasks>

<task id="03-05-T1" type="auto">
  <name>Task 1: Update README.md — add PROP-04 callout, mark step-03 as Current, remove the 'no traceparent yet' bullet</name>
  <files>README.md</files>
  <read_first>
    - README.md (current state — has Phase 1+2 sections; line 71 has step-02-traces as Current; lines 79-92 contain the Reading the code + Why is OtelSdkConfiguration.java duplicated? sections; line 99 has "No traceparent header injection on AMQP (Phase 3)" bullet)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (line 144 — Claude's Discretion: README delta scope = "one paragraph noting the deliberate asymmetry, one paragraph noting the destination-name semconv correction, one block listing the four new files")
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (line 60 — RESEARCH RECOMMENDS one paragraph + destination-name correction note + four-file list, ~25 lines comparable to Phase 2's DOC-05 callout)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 649-675 — README pattern: structural template is the existing DOC-05 callout at lines 90-92; placement = AFTER the DOC-05 section; 6-item transformation notes)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-06-readme-and-exit-gate-PLAN.md (Task 1 — exact analog Edit-1/Edit-2/Edit-3 pattern; Phase 3 mirrors this structure)
  </read_first>
  <action>
    Modify the existing `README.md` in three precise edits — do NOT regenerate the entire file. The Phase 1 + Phase 2 README content is correct as written; Phase 3 only adds one new section, updates one bullet, and removes one bullet.

    **Edit 1 — Update the `## Workshop checkpoints` bullet list** (around lines 70-75 of the current README). Change ONLY two lines:

    Old (find this exact line):
    ```
    - `step-02-traces` — Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces (intentional setup for the Phase 3 propagation lesson). **Current.**
    ```
    New (replace with):
    ```
    - `step-02-traces` — Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces (intentional setup for the Phase 3 propagation lesson).
    ```
    (drop the **Current.** marker)

    Old (find this exact line):
    ```
    - `step-03-context-propagation` — (Phase 3) THE headline lesson: AMQP context propagation joins the two traces.
    ```
    New (replace with):
    ```
    - `step-03-context-propagation` — THE headline lesson: AMQP context propagation joins the two traces; `consumer.parentSpanId == producer.spanId` after this checkpoint. **Current.**
    ```
    (drop the "(Phase 3)" prefix that was a forward-pointer; add the **Current.** marker; expand the description to cite the load-bearing assertion that defines the Phase 3 success state)

    Leave the other 4 bullets (step-01-baseline, step-04-metrics, step-05-logs, step-06-tests) unchanged.

    **Edit 2 — Insert a new `## Why is the propagation pair shared?` section AFTER the existing `## Why is OtelSdkConfiguration.java duplicated?` section and BEFORE the `## What's NOT here yet` section.** This is the PROP-04 deliverable — the parallel-symmetric callout to Phase 2's DOC-05 callout.

    Insert this new section verbatim:
    ```markdown
    ## Why is the propagation pair shared?

    The propagation pair lives in `otel-bootstrap/src/main/java/com/example/otel/amqp/` (PROP-04) and is shared across both services on purpose — the deliberate counterpart of Phase 2's per-service-duplicated `OtelSdkConfiguration.java`. Read these two callouts as a pair: per-service code (the SDK setup) is duplicated so attendees read it twice; cross-service code (the messaging boundary) is shared so attendees read ONE inject method matched by ONE extract method, and the symmetry IS the lesson.

    The shared module exports four classes:

    - [`TracingMessagePostProcessor.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java) — `MessagePostProcessor` that opens a `PRODUCER` span, calls `propagator.inject(Context.current(), props, SETTER)`, and ends. Wired on the producer's `RabbitTemplate` via `setBeforePublishPostProcessors(...)` in `RabbitConfig.rabbitTemplate(...)`.
    - [`TracingMessageListenerAdvice.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java) — `MethodInterceptor` that calls `propagator.extract(Context.current(), props, GETTER)` and opens a `CONSUMER` span with `.setParent(extracted)` — the SINGLE LINE that makes `consumer.parentSpanId == producer.spanId`. Wired on the consumer's listener container factory via `setAdviceChain(...)` in `RabbitConfig.rabbitListenerContainerFactory(...)`.
    - [`MessagePropertiesSetter.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java) and [`MessagePropertiesGetter.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java) — the `TextMapSetter` / `TextMapGetter` pair that writes header values as `String` (never `byte[]`) and defensively `.toString()`s on read. PITFALLS.md #2 in two files.

    The four classes carry zero Spring annotations; each service's `RabbitConfig.java` declares the explicit `@Bean` wiring. The Tracer is injected per service (`com.example.producer` / `com.example.consumer`), so spans created from inside `otel-bootstrap` still appear under each service's instrumentation scope in Tempo — the structural-not-semantic property that makes the shared module readable.

    Phase 3 also corrects an OTel messaging semconv divergence from Phase 2: the producer's `messaging.destination.name` attribute is now the **exchange** (`orders`), not the queue (`orders.created`). This is visible in Tempo across the `step-02-traces` → `step-03-context-propagation` tags.
    ```

    **Edit 3 — Remove the obsolete bullet from `## What's NOT here yet`.** The current bullet (around line 99) reads:
    ```
    - No `traceparent` header injection on AMQP (Phase 3)
    ```
    DELETE this single line. Phase 3 just delivered it. Leave the rest of the "What's NOT here yet" bullets unchanged (they still cover Phase 4 / Phase 5 / Phase 6 / Phase 7 deferrals).

    Verify the edits:
    - The README still has its existing sections (Prerequisites / Workshop checkpoints / Reading the code / Why is OtelSdkConfiguration.java duplicated? / What's NOT here yet) in the correct relative order.
    - One NEW section appears between "Why is OtelSdkConfiguration.java duplicated?" and "What's NOT here yet": "Why is the propagation pair shared?".
    - The "Current." marker has moved from `step-02-traces` to `step-03-context-propagation`.
    - The `traceparent`/Phase-3 forward-pointer bullet is gone from "What's NOT here yet".
    - File still parses as valid markdown (no broken fences, no orphan `</details>`, etc.).

    Do NOT change anything else in the README. Specifically: leave the H1 / intro paragraph / Prerequisites table / IDE setup / one-time setup / first run / "Reading the code" / "Why is OtelSdkConfiguration.java duplicated?" sections untouched. Phase 3 doesn't touch those — Phase 7's DOC-01 is the full README rewrite.
  </action>
  <acceptance_criteria>
    - `test -f README.md` exits 0
    - PROP-04 section title present: `grep -c '^## Why is the propagation pair shared?$' README.md` returns 1
    - PROP-04 paragraph contains key keywords: `grep -q 'shared across both services on purpose' README.md &amp;&amp; grep -q 'one inject method matched by one extract method' README.md &amp;&amp; grep -q 'symmetry IS the lesson' README.md` exits 0
    - PROP-04 section links to all four otel-bootstrap classes: `for f in TracingMessagePostProcessor TracingMessageListenerAdvice MessagePropertiesSetter MessagePropertiesGetter; do grep -q "otel/amqp/$f.java" README.md || exit 1; done` exits 0
    - PROP-04 section mentions the LOAD-BEARING `.setParent(extracted)` line: `grep -q 'setParent(extracted)' README.md` exits 0
    - PROP-04 section mentions the destination-name correction (D-07): `grep -q 'messaging.destination.name' README.md &amp;&amp; grep -q 'now the \*\*exchange\*\*' README.md` exits 0
    - "**Current.**" marker has moved from step-02-traces to step-03-context-propagation: `grep 'step-02-traces' README.md | grep -c '\*\*Current\.\*\*'` returns 0; `grep 'step-03-context-propagation' README.md | grep -c '\*\*Current\.\*\*'` returns 1
    - Workshop checkpoint description for step-03-context-propagation cites the load-bearing assertion: `grep -q 'consumer.parentSpanId == producer.spanId' README.md` exits 0
    - "No traceparent header injection" bullet REMOVED from What's NOT here yet: `grep -c 'No \`traceparent\` header injection' README.md` returns 0
    - Section order preserved: Prerequisites < Workshop checkpoints < Reading the code < Why is OtelSdkConfiguration.java duplicated < Why is the propagation pair shared < What's NOT here yet: `awk '/^## Prerequisites/{p=NR} /^## Workshop checkpoints/{w=NR} /^## Reading the code/{r=NR} /^## Why is OtelSdkConfiguration\.java duplicated/{d=NR} /^## Why is the propagation pair shared/{ps=NR} /^## What.s NOT here yet/{n=NR} END{exit (p<w \&\& w<r \&\& r<d \&\& d<ps \&\& ps<n)?0:1}' README.md` exits 0
    - Existing Phase-1/Phase-2 sections still present (regression check): `for s in 'Prerequisites' 'Required tools' 'Required free ports' 'IDE setup' 'One-time setup' 'First run' 'Workshop checkpoints' 'Reading the code' 'OtelSdkConfiguration.java duplicated' "What.s NOT here yet"; do grep -q "$s" README.md || exit 1; done` exits 0
    - All 6 step-tag names still listed in Workshop checkpoints (regression): `for t in step-01-baseline step-02-traces step-03-context-propagation step-04-metrics step-05-logs step-06-tests; do grep -q "$t" README.md || exit 1; done` exits 0
    - File is well-formed markdown — no orphan code-fence: `awk '/^```/{c++} END{exit (c%2==0)?0:1}' README.md` exits 0
  </acceptance_criteria>
  <verify>
    <automated>grep -q '^## Why is the propagation pair shared?$' README.md &amp;&amp; grep -q 'shared across both services on purpose' README.md &amp;&amp; grep -q 'symmetry IS the lesson' README.md &amp;&amp; grep 'step-03-context-propagation' README.md | grep -q '\*\*Current\.\*\*' &amp;&amp; ! grep -q 'No \`traceparent\` header injection' README.md &amp;&amp; awk '/^```/{c++} END{exit (c%2==0)?0:1}' README.md</automated>
  </verify>
  <done>README.md gains the PROP-04 "Why is the propagation pair shared?" section in the right order (after Why is OtelSdkConfiguration.java duplicated, before What's NOT here yet); the **Current.** marker moves from step-02-traces to step-03-context-propagation with an updated description citing consumer.parentSpanId == producer.spanId; the obsolete Phase 3 forward-pointer bullet is removed from What's NOT here yet; existing Phase-1+Phase-2 sections unchanged; all 6 step-tag names still listed.</done>
</task>

<task id="03-05-T2" type="auto">
  <name>Task 2: Verify all 5 Phase 3 success criteria simultaneously green (gates the tag in T3)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/ROADMAP.md (lines 96-101 — Phase 3 success criteria — all 5 must be simultaneously green for the tag to be applied)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 588-597 — Verification Mechanisms table mapping each SC to its verification mechanism)
    - mise.toml (verify:bom, dev:producer, dev:consumer, demo:order, infra:up, infra:down tasks)
    - All Phase 3 SUMMARYs (03-01 through 03-04) from prior plans
    - README.md (just modified in T1 — PROP-04 callout + step-03 marked Current)
    - Tag refs/tags/step-02-traces (the diff baseline for SC #5)
  </read_first>
  <action>
    Run all five Phase 3 success criteria back-to-back on a clean working tree. T3 (the tag) may ONLY run if EVERY criterion is green AND `git status --porcelain` is empty.

    **Setup:** Bring infrastructure up and start both services in the background.
    ```sh
    mise run infra:up
    nohup mise run dev:producer > /tmp/producer-03-05.log 2>&1 &
    PID_P=$!
    nohup mise run dev:consumer > /tmp/consumer-03-05.log 2>&1 &
    PID_C=$!
    for i in $(seq 1 60); do
      grep -q "Started ProducerApplication" /tmp/producer-03-05.log && grep -q "Started ConsumerApplication" /tmp/consumer-03-05.log && break
      sleep 2
    done
    ```

    **Criterion 1 — ONE distributed trace per POST /orders; consumer.parentSpanId == producer.spanId:**

    Issue ONE order and inspect Tempo for a single trace whose ID is shared by both services AND whose CONSUMER span has the producer's PRODUCER spanId as its parent.
    ```sh
    test "$(curl -s -o /tmp/o.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}')" = "202"
    sleep 8
    PT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=5" | python3 -c "import json,sys; ts=json.load(sys.stdin)['traces']; print(ts[0]['traceID']) if ts else exit(1)")
    CT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-consumer&limit=5" | python3 -c "import json,sys; ts=json.load(sys.stdin)['traces']; print(ts[0]['traceID']) if ts else exit(1)")
    test "$PT" = "$CT"
    # Inspect spans: find the PRODUCER span in producer service + the CONSUMER span; assert consumer.parentSpanId == producer.spanId.
    TR=$(curl -s "http://localhost:3200/api/traces/$PT")
    printf '%s' "$TR" | python3 -c "
    import json, sys
    t = json.load(sys.stdin)
    spans = [s for b in t.get('batches',[]) for ss in b.get('scopeSpans',[]) for s in ss.get('spans',[])]
    prod_span = next(s for s in spans if s.get('kind') == 4)   # PRODUCER
    cons_span = next(s for s in spans if s.get('kind') == 5)   # CONSUMER
    prod_sid = prod_span['spanId']
    cons_psid = cons_span.get('parentSpanId', '')
    assert prod_sid == cons_psid, f'consumer.parentSpanId={cons_psid!r} != producer.spanId={prod_sid!r}'
    print(f'OK: consumer.parentSpanId={cons_psid} == producer.spanId={prod_sid}')
    "
    ```
    PASS criterion: PT == CT (one traceId) AND consumer.parentSpanId == producer.spanId.

    **Criterion 2 — RabbitMQ Mgmt UI shows readable traceparent String header:**

    Stop the consumer so the next message stays in the queue, post an order, peek the message via the management API, and assert the `traceparent` header value matches the W3C format.
    ```sh
    kill $PID_C
    for i in $(seq 1 12); do kill -0 $PID_C 2>/dev/null || break; sleep 1; done
    test "$(curl -s -o /tmp/o2.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-2","quantity":1}')" = "202"
    sleep 4
    # Peek the message via RabbitMQ Management API (requires guest:guest)
    PEEK=$(curl -s -u guest:guest -X POST http://localhost:15672/api/queues/%2F/orders.created/get -H 'Content-Type: application/json' -d '{"count":1,"ackmode":"reject_requeue_true","encoding":"auto"}')
    TP=$(printf '%s' "$PEEK" | python3 -c "
    import json, sys
    msgs = json.load(sys.stdin)
    assert msgs, 'no message in queue'
    headers = msgs[0].get('properties', {}).get('headers', {})
    tp = headers.get('traceparent', '')
    assert tp, f'traceparent header missing; headers={headers}'
    import re
    assert re.match(r'^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$', tp), f'traceparent format wrong: {tp!r}'
    print(tp)
    ")
    echo "OK: traceparent = $TP (W3C format, String, not byte[])"
    ```
    PASS criterion: traceparent header matches `^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$`. The header value as returned by the RabbitMQ Mgmt API is a STRING — if it had been written as byte[]/`[B@...`, the API would return either an empty string, `[B@<hex>`, or the explicit base64-encoded form (depending on encoding setting), all of which fail the regex.

    Restart the consumer to drain the in-flight message and clear the queue:
    ```sh
    nohup mise run dev:consumer > /tmp/consumer-03-05b.log 2>&1 &
    PID_C=$!
    for i in $(seq 1 30); do grep -q "Started ConsumerApplication" /tmp/consumer-03-05b.log && break; sleep 2; done
    sleep 3
    ```

    **Criterion 3 — 10th-order failure produces ERROR trace with exception event on consumer span:**

    Send 10 orders sequentially, find the trace whose status is ERROR, drill in, and assert the CONSUMER span has an `exception` event with `exception.type=com.example.consumer.domain.ProcessingFailedException`.
    ```sh
    for i in $(seq 1 10); do
      curl -s -o /dev/null -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d "{\"sku\":\"WIDGET-$i\",\"quantity\":1}"
      sleep 0.4
    done
    sleep 8
    # Search Tempo for an error trace
    ERR=$(curl -s 'http://localhost:3200/api/search?tags=status%3Derror&limit=10' | python3 -c "import json,sys; ts=json.load(sys.stdin).get('traces',[]); print(ts[0]['traceID']) if ts else ''")
    test -n "$ERR"
    TR=$(curl -s "http://localhost:3200/api/traces/$ERR")
    printf '%s' "$TR" | python3 -c "
    import json, sys
    t = json.load(sys.stdin)
    spans = [s for b in t.get('batches',[]) for ss in b.get('scopeSpans',[]) for s in ss.get('spans',[])]
    cons = next((s for s in spans if s.get('kind') == 5), None)
    assert cons, 'no CONSUMER span'
    status = cons.get('status', {}).get('code', 0)
    # status code 2 = STATUS_CODE_ERROR
    assert status == 2, f'CONSUMER span status={status} (want 2 = ERROR)'
    events = cons.get('events', [])
    exc_event = next((e for e in events if e.get('name') == 'exception'), None)
    assert exc_event, f'no exception event on CONSUMER span; events={events}'
    attrs = {a['key']: a['value'].get('stringValue', '') for a in exc_event.get('attributes', [])}
    assert 'ProcessingFailedException' in attrs.get('exception.type', ''), f'exception.type={attrs.get(\"exception.type\")!r}'
    print(f'OK: CONSUMER span ERROR + exception event with exception.type={attrs[\"exception.type\"]}')
    "
    ```
    PASS criterion: at least one trace with status=error in Tempo; the CONSUMER span has exception event with `exception.type` containing `ProcessingFailedException`.

    **Criterion 4 — Structural symmetry of the propagation pair:**

    This is a code-review criterion (no live runtime check). Confirm the two classes have parallel structure:
    ```sh
    test -f otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java
    test -f otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
    # Both use openTelemetry.getPropagators().getTextMapPropagator() (single source of truth)
    grep -q 'openTelemetry.getPropagators().getTextMapPropagator()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java
    grep -q 'openTelemetry.getPropagators().getTextMapPropagator()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
    # One uses inject, one uses extract
    grep -q 'propagator.inject(Context.current(), props, SETTER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java
    grep -q 'propagator.extract(Context.current(), props, GETTER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
    # Both create a span with PRODUCER vs CONSUMER kind
    grep -q 'setSpanKind(SpanKind.PRODUCER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java
    grep -q 'setSpanKind(SpanKind.CONSUMER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
    # Both set the same 4 messaging semconv attributes
    for f in TracingMessagePostProcessor TracingMessageListenerAdvice; do
      C=$(grep -cE 'MESSAGING_SYSTEM|MESSAGING_DESTINATION_NAME|MESSAGING_OPERATION_TYPE|MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY' otel-bootstrap/src/main/java/com/example/otel/amqp/$f.java)
      python3 -c "c=$C; assert c>=4, f'{f} only has $C semconv attrs (want >= 4)'"
    done
    # README PROP-04 callout exists (verified by T1)
    grep -q '^## Why is the propagation pair shared?$' README.md
    ```
    PASS criterion: all greps exit 0.

    **Criterion 5 — git diff step-02-traces..step-03-context-propagation is small and reviewable (~50 added + ~60 deleted + 5 new files):**

    The tag doesn't exist YET (it's created in T3), so this criterion is verified PRE-tag using HEAD as the proxy:
    ```sh
    git fetch --tags 2>/dev/null
    git tag -l step-02-traces | grep -q '^step-02-traces$'   # baseline tag exists
    DIFFSTAT=$(git diff --shortstat step-02-traces..HEAD)
    echo "diff vs step-02-traces: $DIFFSTAT"
    # Expect roughly: ~5-10 files changed, ~80-130 insertions, ~50-80 deletions (the JavaDoc-rich plans accumulate more added lines than the CONTEXT.md estimate of ~50; that's fine — the deletion+small-net property is what matters)
    NEW_FILES=$(git diff --name-status step-02-traces..HEAD | grep -c '^A')
    python3 -c "n=$NEW_FILES; assert 4<=n<=8, f'new files: {n} (want 4-8)'; print(f'OK: {n} new files (the 4 propagation classes + ProcessingFailedException + maybe RoundTripTest)')"
    ```
    PASS criterion: at least 4 new files (the propagation pair + getter + setter), at most 8 (room for the optional unit test + ProcessingFailedException). Total diff size readable in one viewing.

    **Criterion 6 — clean working tree (precondition for tagging):**
    ```sh
    test -z "$(git status --porcelain)" || { git status --porcelain; echo "ABORT: uncommitted changes — commit them before running T3"; exit 1; }
    ```

    **Final cleanup:** Stop both apps cleanly so T3 inherits a clean process state.
    ```sh
    kill $PID_P $PID_C 2>/dev/null
    for i in $(seq 1 12); do { kill -0 $PID_P 2>/dev/null || kill -0 $PID_C 2>/dev/null; } || break; sleep 1; done
    ! pgrep -f spring-boot:run
    ```

    Failure modes — if ANY criterion fails, T3 (the tag) MUST NOT run. Document which criterion failed in the SUMMARY and STOP. Common failures:
    - Criterion 1 fails (PT != CT): the LOAD-BEARING `.setParent(extracted)` is missing or wrong. Inspect TracingMessageListenerAdvice.java.
    - Criterion 2 fails (traceparent missing or `[B@...`): producer's `setBeforePublishPostProcessors` not registered, OR setter writes byte[]. Inspect producer's RabbitConfig.java + MessagePropertiesSetter.java.
    - Criterion 3 fails (no error trace): APP-04 throw site missing, OR D-13's `setDefaultRequeueRejected(false)` missing → message infinitely requeues and the assertion times out. Inspect ProcessingService.java + consumer's RabbitConfig.java.
    - Criterion 4 fails: regression in Plan 03-01's classes; re-verify those files.
    - Criterion 5 fails (too many new files): scope creep; investigate which files crept in.
    - Criterion 6 fails: uncommitted changes; commit them with a chore message and re-run T2.
  </action>
  <acceptance_criteria>
    - Criterion 1 PASS: producer + consumer traces have IDENTICAL traceId; consumer's CONSUMER span parentSpanId equals producer's PRODUCER span spanId
    - Criterion 2 PASS: RabbitMQ Mgmt API returns the queued message with a `traceparent` header value matching `^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$` — String, NOT byte[]/`[B@...`
    - Criterion 3 PASS: at least one trace with status=error visible in Tempo within 8s of sending 10 orders; CONSUMER span on that trace has exception event with `exception.type=com.example.consumer.domain.ProcessingFailedException`
    - Criterion 4 PASS: both `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` use `openTelemetry.getPropagators().getTextMapPropagator()` (single source of truth, D-04); one calls `.inject(...)`, one calls `.extract(...)`; one sets PRODUCER kind, one sets CONSUMER kind; both have 4 messaging semconv attributes
    - Criterion 5 PASS: `git diff --name-status step-02-traces..HEAD` shows 4-8 new files; total diff small enough for one-viewing review
    - Criterion 6 PASS: `test -z "$(git status --porcelain)"` exits 0 (clean tree)
    - Phase 2 invariant preserved: `mise run verify:bom` exits 0
    - All background processes cleaned up: `! pgrep -f spring-boot:run` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact' &amp;&amp; grep -q '^## Why is the propagation pair shared?$' README.md &amp;&amp; test -z "$(git status --porcelain)" &amp;&amp; ! pgrep -f spring-boot:run</automated>
  </verify>
  <done>All five Phase 3 success criteria simultaneously green: ONE joined trace per POST /orders (consumer.parentSpanId == producer.spanId); RabbitMQ Mgmt UI shows readable traceparent String header in W3C format; 10th-order failure produces ERROR trace with exception event having exception.type=ProcessingFailedException on the CONSUMER span; structural symmetry of the propagation pair verified; git diff vs step-02-traces is small (4-8 new files); working tree clean. T3 may proceed with the tag.</done>
</task>

<task id="03-05-T3" type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Human-verify Phase 3 joined-trace state + create annotated git tag step-03-context-propagation</name>
  <what-built>
    - Wave 1 (Plan 03-01): otel-bootstrap module populated with 4 propagation classes + 1 unit test + 5 BOM-managed dependencies. The propagation pair (TracingMessagePostProcessor + TracingMessageListenerAdvice) compiles and ships in otel-bootstrap-0.1.0-SNAPSHOT.jar. The MessagePropertiesSetter ↔ MessagePropertiesGetter round-trip test passes (PITFALLS.md #2 regression net).
    - Wave 2 (Plan 03-02 producer-side): producer-service/pom.xml gains the otel-bootstrap dep; RabbitConfig adds @Bean TracingMessagePostProcessor + explicit @Bean RabbitTemplate that calls setBeforePublishPostProcessors(mpp); OrderPublisher.publish becomes a thin 3-line convertAndSend pass-through (Phase 2's inline PRODUCER span deleted, Tracer constructor param removed).
    - Wave 2 (Plan 03-03 consumer-side): consumer-service/pom.xml gains the otel-bootstrap dep; RabbitConfig adds @Bean TracingMessageListenerAdvice + Configurer-aided @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...) with setAdviceChain + setDefaultRequeueRejected(false); OrderListener.onOrder becomes a thin 3-line pass-through (Phase 2's inline CONSUMER span deleted, Tracer constructor param removed).
    - Wave 3 (Plan 03-04): ProcessingFailedException created in com.example.consumer.domain (extends RuntimeException; D-12); ProcessingService.process gains an AtomicInteger counter + a `if (n % 10 == 0) throw new ProcessingFailedException(...)` site INSIDE the existing D-03 try-block (D-11 + APP-04). The Phase 2 D-03 catch (recordException + setStatus(ERROR) + throw) records the exception on the INTERNAL span; the advice's catch (Throwable) records the same exception on the CONSUMER span. defaultRequeueRejected=false drops the failed message after NACK.
    - Wave 4 (this plan): README.md gains the PROP-04 "Why is the propagation pair shared?" callout (parallel to Phase 2's DOC-05); the **Current.** marker moves from step-02-traces to step-03-context-propagation; the obsolete "No traceparent header injection" bullet is removed from "What's NOT here yet". T2 already verified all 5 Phase 3 success criteria simultaneously green and the working tree is clean.
    - Pending in this task: human verification of the workshop-facing surfaces (Tempo Explore shows ONE distributed trace per POST /orders with consumer.parentSpanId == producer.spanId; RabbitMQ Mgmt UI shows the published message DOES carry a String-valued traceparent header in W3C format `00-<32hex>-<16hex>-01`; the 10th-order failure produces an ERROR trace with the exception event visible; README reads cleanly for the PROP-04 delta) plus creation of the annotated git tag `step-03-context-propagation`. This is the THIRD of the six WORK-01 tags.
  </what-built>
  <how-to-verify>
    Spend ~10-15 minutes confirming the workshop attendee experience is clean and the headline lesson is visible before the tag becomes immutable. The state at this tag is the workshop's most powerful pedagogical artifact — it pairs with `step-02-traces` to produce the broken-vs-fixed delta (DOC-04 in Phase 7).

    **Step 1 — Open the README in a markdown viewer** (GitHub preview, IntelliJ, VS Code preview, or `glow README.md`). Confirm:
    1. The new `## Why is the propagation pair shared?` section is present and reads as a parallel-symmetric callout to the existing `## Why is OtelSdkConfiguration.java duplicated?`. The two read as a pair: per-service code is duplicated; cross-service code is shared.
    2. The PROP-04 callout has clickable links to all four classes in `otel-bootstrap/src/main/java/com/example/otel/amqp/`.
    3. The PROP-04 callout cites the LOAD-BEARING line `.setParent(extracted)` and the destination-name correction (queue → exchange).
    4. The `## Workshop checkpoints` list shows `step-03-context-propagation` as **Current** (not `step-02-traces`).
    5. The "What's NOT here yet" section no longer mentions `traceparent` injection.
    6. No broken markdown — code fences balanced, links resolve when clicked from the markdown viewer.

    **Step 2 — Sanity-check the running stack:**
    ```sh
    mise run infra:up
    mise run dev    # parallel producer + consumer
    ```
    In another terminal, confirm both apps started cleanly:
    ```sh
    curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'
    curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'
    ```

    **Step 3 — POST one order, then open Grafana → Tempo Explore (`http://localhost:3000` → admin/admin → Explore → Tempo datasource):**
    ```sh
    mise run demo:order      # or curl POST /orders manually
    ```
    1. In the Tempo Search tab, set "Service Name" to `order-producer`. Click Run query. Note the most recent traceID.
    2. Click the trace. Confirm the trace contains spans from BOTH services in ONE timeline:
       - SERVER `POST /orders` (order-producer, kind=2)
       - INTERNAL `OrderService.place` (order-producer, kind=1)
       - PRODUCER `orders publish` (order-producer, kind=4) — span name uses EXCHANGE not queue (D-07 correction)
       - CONSUMER `orders process` (order-consumer, kind=5) — parented to the PRODUCER span
       - INTERNAL `ProcessingService.process` (order-consumer, kind=1)
    3. Click the CONSUMER span in the trace timeline. In the right-hand details panel, find the `parentSpanId` field. Confirm it EQUALS the spanId of the PRODUCER span. **This is the workshop's headline lesson made visible.** ROADMAP SC #1 satisfied.
    4. Confirm the CONSUMER span carries the 4 messaging semconv attributes: `messaging.system=rabbitmq`, `messaging.destination.name=orders` (NOT `orders.created` — Phase 3 corrected from queue to exchange per D-07), `messaging.operation.type=process`, `messaging.rabbitmq.destination_routing_key=order.created`.
    5. Repeat the search for `order-consumer` service name — confirm the consumer's view of the trace shows the SAME traceID (the trace is unified across both service-name views).

    **Step 4 — Open RabbitMQ Management UI (`http://localhost:15672` → guest/guest):**
    ```sh
    # First, stop the consumer so the next POST stays in the queue:
    pkill -f 'consumer-service.*spring-boot:run'
    sleep 4
    mise run demo:order
    sleep 2
    ```
    1. In the RabbitMQ Mgmt UI, go to Queues → click `orders.created` → "Get messages" with `Ack mode: Reject requeue true` and `Messages: 1`.
    2. Inspect the displayed message properties.
    3. Find the "Headers" section. Confirm it contains a `traceparent` header.
    4. The value MUST be a string of the form `00-<32-hex-chars>-<16-hex-chars>-01` (W3C Trace Context format). It MUST NOT be `[B@<hex>`, MUST NOT be a hex blob, MUST NOT be empty. PROP-01 + PITFALLS.md #2 satisfied. ROADMAP SC #2 satisfied.
    5. Restart the consumer (`mise run dev:consumer`) and let it drain the queue.

    **Step 5 — Trigger the 10th-order failure and observe the error trace in Tempo:**
    ```sh
    # Send 10 orders in sequence:
    for i in $(seq 1 10); do
      mise run demo:order
      sleep 0.5
    done
    sleep 8
    ```
    1. In Tempo Search, set "Status" filter to `error`. Click Run query.
    2. At least one trace should appear (the 10th order's trace).
    3. Click into the trace. Confirm:
       - The CONSUMER span (`orders process`) shows Status Code = `ERROR` (typically a red exclamation mark in Tempo's UI).
       - The CONSUMER span has an `events` panel showing an `exception` event with attributes: `exception.type=com.example.consumer.domain.ProcessingFailedException`, `exception.message="Deterministic failure on order #10 (every 10th order)"`, `exception.stacktrace=...`.
       - The INTERNAL span (`ProcessingService.process`) ALSO shows Status Code = ERROR with the same exception event attached (Phase 2's D-03 catch recorded it on the INTERNAL span; the advice's catch recorded it again on the CONSUMER span — both are visible).
    4. ROADMAP SC #3 satisfied: 10th-order failure produces ERROR trace with exception event.

    **Step 6 — Read the propagation pair source side-by-side (ROADMAP SC #4 — structural symmetry):**

    Open `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` and `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` in your IDE side-by-side. Confirm:
    1. Both classes use `openTelemetry.getPropagators().getTextMapPropagator()` (single source of truth, D-04).
    2. One calls `.inject(...)`, the other calls `.extract(...)`. The MessagePropertiesSetter ↔ MessagePropertiesGetter pair is the carrier of that asymmetry.
    3. Both create a span with the SAME 4 messaging semconv attributes (system, destination.name, operation.type, rabbitmq.destination.routing_key); only the SpanKind (PRODUCER vs CONSUMER) and the value of operation.type (SEND vs PROCESS) differ.
    4. The SHAPE of the two classes is symmetric by design — the lesson IS the symmetry. PROP-04 satisfied at the code level (and in the README callout from T1).

    **Step 7 — Approve or describe issues.**

    If approved, proceed to Step 8 (the tag). If issues exist, list them with file references; do NOT tag.

    **Step 8 — Create the annotated git tag (executor performs after approval):**

    First, ensure git working tree is still clean (T2 confirmed this; double-check):
    ```sh
    git status --porcelain
    ```
    Expected: empty output. If anything is staged or untracked beyond .gitignored items, commit them with a chore message:
    ```sh
    git add <files>
    git commit -m "chore(03): phase-3 wrap-up"
    ```

    Then create the annotated tag (`-a` is mandatory — lightweight tags are not sufficient for WORK-01):
    ```sh
    git tag -a step-03-context-propagation -m "Workshop checkpoint: Phase 3 — AMQP context propagation. ONE distributed trace spanning producer + consumer.

    The TracingMessagePostProcessor (otel-bootstrap, registered on RabbitTemplate.setBeforePublishPostProcessors) injects W3C traceparent + tracestate headers on every publish. The TracingMessageListenerAdvice (registered on SimpleRabbitListenerContainerFactory.setAdviceChain) extracts the same headers and calls .setParent(extracted) on the CONSUMER span — the SINGLE LINE that makes consumer.parentSpanId == producer.spanId. The propagation pair lives in com.example.otel.amqp (shared module; PROP-04). The deterministic 10%-failure path in ProcessingService throws ProcessingFailedException on every 10th order; the Phase 2 D-03 catch + the advice's catch both recordException + setStatus(ERROR), and defaultRequeueRejected=false drops the failed message. Phase 3 also corrects the OTel messaging-semconv destination name from queue (Phase 2) to exchange (Phase 3) per the RabbitMQ profile.

    All five Phase 3 success criteria simultaneously green at this commit:
      1. ONE distributed trace per POST /orders; consumer.parentSpanId == producer.spanId visible in Tempo
      2. RabbitMQ Mgmt UI shows readable traceparent String header in W3C format
      3. 10th-order failure produces ERROR trace with exception event on CONSUMER span (exception.type=com.example.consumer.domain.ProcessingFailedException)
      4. Structural symmetry of TracingMessagePostProcessor + TracingMessageListenerAdvice in otel-bootstrap (PROP-04)
      5. git diff step-02-traces..step-03-context-propagation is small and reviewable in one viewing"
    ```

    Verify the tag:
    ```sh
    git tag --list step-03-context-propagation
    git for-each-ref --format='%(objecttype) %(refname)' refs/tags/step-03-context-propagation
    git show step-03-context-propagation | head -25
    ```

    The first command outputs `step-03-context-propagation`. The second outputs `tag refs/tags/step-03-context-propagation` (NOT `commit` — proves annotated, not lightweight). The third shows the full tag message including the 5 success criteria.

    DO NOT push the tag automatically. The user (`coto@petabyte.cl`) decides when to push. Mention in the SUMMARY that `git push origin step-03-context-propagation` is the follow-up the user runs when ready (or `git push --tags`). Tag is local-only until explicit push — same git-safety convention as Phase 1 and Phase 2.

    **Step 9 — Reproducibility self-test (SC #5):**
    From a temp clone:
    ```sh
    git -C /tmp clone --branch step-03-context-propagation --depth 1 file://$(pwd) verify-step-03 2>/dev/null
    cd /tmp/verify-step-03
    mise install
    mise run verify:bom    # MUST exit 0
    mvn -pl otel-bootstrap test    # MUST exit 0 (round-trip unit test passes)
    cd -
    rm -rf /tmp/verify-step-03
    ```

    SC #5 portion: `git checkout step-03-context-propagation` (or temp clone) reproduces the green-Phase-3 state.

    **Step 10 — Quantify the diff size for SC #5:**
    ```sh
    git diff --shortstat step-02-traces..step-03-context-propagation
    git diff --name-status step-02-traces..step-03-context-propagation
    ```
    Expected `--shortstat`: ~5-10 files changed; insertions and deletions roughly balanced (the JavaDoc-rich plans produce more added lines than the CONTEXT.md `~50` estimate; that's fine — the deletion-is-the-diff property still holds qualitatively). At minimum: 4 new propagation classes + 1 new ProcessingFailedException + (optionally) 1 new round-trip test = 5-6 new files. Modified: producer-service/pom.xml + producer-service/.../RabbitConfig.java + producer-service/.../OrderPublisher.java + consumer-service/pom.xml + consumer-service/.../RabbitConfig.java + consumer-service/.../OrderListener.java + consumer-service/.../ProcessingService.java + otel-bootstrap/pom.xml + README.md = 9 modified files.
  </how-to-verify>
  <resume-signal>Type "approved" to proceed with creating the annotated tag, "not yet — issues:" with a list of issues to fix, or "skip-tag" to defer tagging until later (which leaves Phase 3 incomplete — WORK-01 unsatisfied for the third checkpoint).</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 03-05 — README + tag)

| Boundary | Description |
|----------|-------------|
| Local repo → remote (e.g., GitHub) | Tag pushed via `git push origin step-03-context-propagation` — workshop artifact will be public/internally-published |
| Workshop attendee reading README → external links | Existing Phase 1+2 external links unchanged; the new PROP-04 section adds intra-repo links to .java files (no new external URLs) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-3-05-01 | Tampering | Lightweight tag substituted for annotated tag, allowing silent re-pointing | mitigate | The tag-creation command uses `git tag -a` (annotated); T3's verification step asserts via `git for-each-ref` that the tag's objecttype is `tag`, not `commit`. Same convention as Phase 1's step-01-baseline and Phase 2's step-02-traces. |
| T-3-05-02 | Information Disclosure | README.md leaking internal infrastructure details | accept | README is intended public/internal-team-shared; no secrets; no internal hosts; only documented workshop defaults (guest/guest, admin/admin) — Phase 1+2 baseline preserved. |
| T-3-05-03 | Tampering | A future phase tag overwrites step-03-context-propagation (force-push) | mitigate | Tags are immutable under normal git workflow; force-push would be needed to overwrite. Convention documented in PROJECT.md (Key Decisions row "Staged git checkpoints"). |
| T-3-05-04 | Repudiation | Tag created without a meaningful message; cannot trace what state it represents | mitigate | T3 acceptance criteria require the annotated message to mention "Phase 3", "ONE distributed trace", "consumer.parentSpanId == producer.spanId", and the 5 success criteria — content auditable. |
| T-3-05-05 | Information Disclosure | The PROP-04 section references the LOAD-BEARING `.setParent(extracted)` line, exposing a "tip" about the propagation mechanism | accept | Workshop scope; the entire repo is teaching material — there is no architectural secret to protect. Calling out the load-bearing line IS the lesson. |

**Phase scope:** Workshop README delta + git tag. No runtime threat surface introduced.
</threat_model>

<verification>
- README.md has the new `## Why is the propagation pair shared?` section in the correct relative order; the **Current.** marker is on `step-03-context-propagation`; the obsolete "No traceparent header injection" bullet is removed; existing Phase 1+2 sections are unchanged.
- All 5 Phase 3 success criteria from ROADMAP.md verified simultaneously green in T2.
- After T3 approval: `git tag --list step-03-context-propagation` outputs `step-03-context-propagation`.
- `git for-each-ref --format='%(objecttype)' refs/tags/step-03-context-propagation` outputs `tag` (annotated, not lightweight).
- `git show step-03-context-propagation` displays the tag message containing "Phase 3", "ONE distributed trace", "consumer.parentSpanId == producer.spanId", and the 5 success-criteria lines.
- `git checkout step-03-context-propagation` (in a clean temp clone) reproduces the green-Phase-3 state — `mise run verify:bom` exits 0 there AND `mvn -pl otel-bootstrap test` exits 0 (round-trip unit test passes).
- Phase 1+2 invariants preserved: `mise run verify:bom` continues to exit 0.
- `git diff --shortstat step-02-traces..step-03-context-propagation` shows a small, readable changeset (5-8 new files; balanced add/delete profile).
</verification>

<success_criteria>
- PROP-04 satisfied (README half — the propagation classes themselves landed in plan 03-01): README has a single-paragraph `## Why is the propagation pair shared?` section explicitly contrasting the SHARED propagation pair with the per-service-duplicated SDK bootstrap (DOC-05). The two callouts together teach the asymmetry boundary: per-service code = duplicated; cross-service code = shared.
- PROP-03 verified at runtime: `consumer.parentSpanId == producer.spanId` confirmed live in Tempo at the moment of tagging (T3 Step 3).
- APP-04 + TRACE-09 verified at runtime: the 10th-order ERROR trace with exception event is observable in Tempo (T3 Step 5).
- WORK-01 (Phase 3 portion) satisfied: annotated git tag `step-03-context-propagation` exists on the working branch; the tagging convention from Phase 1+2 propagates.
- All 5 Phase 3 success criteria from ROADMAP.md §"Phase 3" verified simultaneously green at the moment of tagging.
- The tag is local-only after T3 (user pushes when ready) — respects git-safety protocol.
- Phase 3 is now SHIPPED. The repo is the joined-trace state that pairs with `step-02-traces` to produce the workshop's most powerful pedagogical delta. DOC-04 (Phase 7) will pair Tempo screenshots from these two tags.
</success_criteria>

<output>
After completion, create `.planning/phases/03-amqp-context-propagation/03-05-SUMMARY.md` documenting:
- README.md final structure (paste H1 + section titles list)
- Confirmed Phase 3 success-criteria results from T2 (paste the green outputs of each criterion — the Tempo trace IDs, the traceparent header value seen on the queued message, the exception.type observed on the error trace, the diff size)
- Confirmed annotated tag exists: paste `git for-each-ref --format='%(objecttype) %(objectname:short) %(refname)' refs/tags/step-03-context-propagation`
- Confirmed tag message: paste `git show step-03-context-propagation | head -25`
- A note that the user should run `git push origin step-03-context-propagation` when ready to publish (NOT done by this plan — same git-safety convention as Phase 1 + Phase 2)
- Files modified: 1 (README.md) + 1 git ref (refs/tags/step-03-context-propagation)
- Confirmed reproducibility self-test (paste the temp-clone verify:bom + mvn test output)
- The exact git diff stat: paste `git diff --shortstat step-02-traces..step-03-context-propagation` and `git diff --name-status step-02-traces..step-03-context-propagation`

Then create `.planning/phases/03-amqp-context-propagation/PHASE-SUMMARY.md` rolling up all five plan summaries — phase exit summary documenting:
- Total artifacts created across the phase: 4 new propagation classes (otel-bootstrap), 1 round-trip unit test, 1 new ProcessingFailedException, modified producer/consumer pom.xml + RabbitConfig + OrderPublisher / OrderListener + ProcessingService, README delta, 1 git ref
- The single gate sentence: "POST /orders produces ONE distributed trace; consumer.parentSpanId == producer.spanId; 10th order produces ERROR trace with exception event" — confirmed
- Tag created: step-03-context-propagation
- Lessons learned: any deviations from RESEARCH.md (e.g., did the semconv-incubating dep need to be added to otel-bootstrap or was constants-inlining sufficient?), surprising failure modes, anything to feed back to retrospective
- Phase 4 readiness signal: SdkMeterProvider can now be added to each OtelSdkConfiguration; metrics will inherit the propagated trace context (exemplars). No structural changes needed in the propagation pair for Phase 4.
- Phase 5 readiness signal: LOG.info inside OrderListener.onOrder runs INSIDE the CONSUMER span scope (D-09 + RESEARCH FLAG #1) — Phase 5's MDC injector will pick up trace_id/span_id automatically without any further code change in OrderListener.
- Phase 6 readiness signal: Testcontainers @SpringBootTest can swap InMemorySpanExporter for OtlpGrpcSpanExporter and assert: traceId(producer)==traceId(consumer); consumer.parentSpanId==producer.spanId; both spans carry SpanKind.PRODUCER/SpanKind.CONSUMER and the 4 messaging semconv attributes; defaultRequeueRejected=false drops poison messages. The propagation pair is the test surface; Phase 6 only adds the test code.
</output>
