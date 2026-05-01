---
id: 02-06-readme-and-exit-gate
phase: 02-manual-sdk-bootstrap-first-traces
plan: 06
type: execute
wave: 4
depends_on: [02-01-pom-dependencies, 02-02-producer-sdk-config, 02-03-consumer-sdk-config, 02-04-producer-instrumentation, 02-05-consumer-instrumentation]
requirements: [DOC-03, DOC-05, WORK-01]
files_modified:
  - README.md
autonomous: false
must_haves:
  truths:
    - "README.md gains a `## Why is OtelSdkConfiguration.java duplicated?` section (DOC-05) — single paragraph that explicitly names the per-service duplication as INTENTIONAL with the rationale 'so attendees read the SDK setup twice', explicitly warns readers NOT to refactor it into a shared @AutoConfiguration bean, and links to the file paths in both services"
    - "README.md gains a brief `## Reading the code` section (DOC-03 callout) pointing attendees at OtelSdkConfiguration.java in BOTH services as the workshop's textbook for the SDK setup, with a reminder that every @Bean carries an inline comment"
    - "README.md `## Workshop checkpoints` section is updated: `step-02-traces` is the **Current** marker (was step-01-baseline); step-01-baseline gets back-marked as historical"
    - "All 6 Phase 2 success criteria from ROADMAP.md are simultaneously green: TWO traces / Ctrl-C flushes last batch / heavily-commented OtelSdkConfiguration in both services / SERVER+INTERNAL on producer + CONSUMER+INTERNAL on consumer / README per-service-duplication callout / annotated tag step-02-traces exists"
    - "Annotated git tag `step-02-traces` exists on the working branch (typically main); points at a commit where ALL six success criteria are simultaneously true"
    - "Repository is committed with a clean working tree at the moment the tag is applied; the tag is annotated (-a flag), NOT lightweight"
    - "git checkout step-02-traces reproduces the green Phase-2 state (criterion #6)"
  artifacts:
    - path: "README.md"
      provides: "Phase 2 documentation deltas: DOC-05 per-service-duplication callout (single paragraph) + DOC-03 reading-the-code pointer + Workshop checkpoints update marking step-02-traces as Current"
      contains: "Why is OtelSdkConfiguration.java duplicated"
    - path: "(git ref) refs/tags/step-02-traces"
      provides: "Immutable annotated workshop checkpoint marking Phase 2 exit; second of the six WORK-01 tags"
      contains: "(annotated tag message)"
  key_links:
    - from: "README.md '## Why is OtelSdkConfiguration.java duplicated?'"
      to: "producer-service/.../config/OtelSdkConfiguration.java + consumer-service/.../config/OtelSdkConfiguration.java"
      via: "Documented file paths and the rationale that keeps readers from refactoring"
      pattern: "duplicated"
    - from: "git tag step-02-traces"
      to: "Working two-trace state (producer + consumer in DIFFERENT traces in Tempo per POST /orders)"
      via: "Commit pointed at by the tag reproduces the Phase 2 success-criteria-green state"
      pattern: "step-02-traces"
---

<objective>
Land the Phase 2 documentation gate (DOC-03 reading-the-code callout + DOC-05 per-service-duplication paragraph in README.md) and the Phase 2 exit gate (WORK-01 — annotated git tag `step-02-traces` on the working branch). The tag is created ONLY after verifying ALL SIX Phase 2 success criteria from ROADMAP.md are simultaneously green: TWO distinct traces in Tempo per POST /orders / graceful shutdown flushes the last span batch / OtelSdkConfiguration heavily commented in BOTH services / producer trace = SERVER + INTERNAL + PRODUCER and consumer trace = CONSUMER + INTERNAL / README explains the per-service duplication is intentional / `git checkout step-02-traces` reproduces the state.

The tag is the workshop attendee's "broken state" checkpoint — Phase 3's `step-03-context-propagation` produces the dramatic delta when the two disconnected traces become one joined trace. The screenshot capture for DOC-04 (deferred to Phase 7) will pair this state side-by-side with Phase 3's fixed state.

Purpose: DOC-03 (README pointer to the heavily-commented OtelSdkConfiguration files; the heavy comments themselves were delivered by Plans 02-02 + 02-03), DOC-05 (per-service-duplication callout in README — keeps readers from "fixing" the duplication), WORK-01 (annotated tag `step-02-traces`). Output: 1 modified README.md + 1 git ref + a clean commit history committed.
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
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-01-SUMMARY.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-02-SUMMARY.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-03-SUMMARY.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-SUMMARY.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-06-readme-and-exit-gate-PLAN.md
@.planning/phases/01-baseline-scaffold/1-06-SUMMARY.md
@README.md
@CLAUDE.md
</context>

<tasks>

<task id="02-06-T1" type="auto">
  <name>Task 1: Update README.md — add DOC-05 per-service-duplication callout + DOC-03 reading-the-code pointer + back-mark step-01-baseline / mark step-02-traces as Current</name>
  <files>README.md</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 116-117 — Claude's discretion: README DOC-05 wording — "single paragraph: OtelSdkConfiguration is duplicated per service on purpose — refactoring it into a shared @AutoConfiguration bean would hide one of the two readings the workshop is built around")
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 16-29 — In scope: DOC-03 (heavily commented `OtelSdkConfiguration`) + DOC-05 (per-service-duplication callout) — both READMEs deliverables for Phase 2)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (line 192 — workshop attendees attendee at step-02-traces sees "TWO traces in Tempo for one POST"; README's checkpoint list must mark step-02-traces as Current)
    - README.md (current state — Phase 1 README written by Plan 1-06; has the `## Workshop checkpoints` section with step-01-baseline marked **Current** at line 70)
    - .planning/REQUIREMENTS.md (DOC-03 lines 73, DOC-05 lines 75 — exact text of the requirement)
    - .planning/phases/01-baseline-scaffold/1-06-readme-and-exit-gate-PLAN.md (the prior README plan — Plan 1-06's task 1 wrote the workshop-checkpoints structure that Phase 2 updates in place)
  </read_first>
  <action>
    Modify the existing `README.md` in three precise edits — do NOT regenerate the entire file. The Phase 1 README content is correct as written; Phase 2 only adds two new sections and updates one bullet.

    **Edit 1 — Update the `## Workshop checkpoints` bullet list** (around lines 68-77 of the current README). Change ONLY two lines:

    Old (find this exact line):
    ```
    - `step-01-baseline` — Working two-service Spring Boot + RabbitMQ app on host JVM with ZERO telemetry. **Current.**
    ```
    New (replace with):
    ```
    - `step-01-baseline` — Working two-service Spring Boot + RabbitMQ app on host JVM with ZERO telemetry.
    ```
    (drop the **Current.** marker)

    Old (find this exact line):
    ```
    - `step-02-traces` — (Phase 2) Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces.
    ```
    New (replace with):
    ```
    - `step-02-traces` — Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces (intentional setup for the Phase 3 propagation lesson). **Current.**
    ```
    (drop the "(Phase 2)" prefix that was a forward-pointer in Phase 1; add the **Current.** marker; expand the description to mention the broken-then-fixed pedagogy explicitly so the attendee at this checkpoint understands the intentionality)

    Leave the other 4 bullets (step-03 through step-06) unchanged — they remain forward-pointers.

    **Edit 2 — Insert a new `## Reading the code` section AFTER the `## Workshop checkpoints` section and BEFORE the `## What's NOT here yet` section** (the latter currently lives around lines 79-87). The new section is brief — one short paragraph that satisfies DOC-03's "the code IS the workshop's textbook" intent at the README level (the heavy comments in the code itself were delivered by Plans 02-02 + 02-03).

    Insert this new section verbatim:
    ```markdown
    ## Reading the code

    The two `OtelSdkConfiguration.java` files are the workshop's textbook for the manual SDK setup. Open them in your IDE and read top-to-bottom — every `@Bean` carries an inline comment explaining what each builder call does and why (DOC-03):

    - [`producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`](./producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java)
    - [`consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`](./consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java)

    The producer adds one extra file — [`HttpServerSpanFilter.java`](./producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java) — that wraps every non-`/actuator/*` HTTP request in a `SERVER` span. The consumer has no inbound HTTP business surface so it does not register the filter.

    The five business-code span sites (one `SERVER`, two `INTERNAL`, one `PRODUCER`, one `CONSUMER`) all use the same pure-inline `try`/`Scope`/`try`/`catch`/`finally` template — no helper, no AOP. The boilerplate IS the lesson.
    ```

    **Edit 3 — Insert a new `## Why is OtelSdkConfiguration.java duplicated?` section AFTER the `## Reading the code` section and BEFORE the `## What's NOT here yet` section.** This is the DOC-05 deliverable — a single paragraph that explicitly names the duplication intentional and warns against refactoring.

    Insert this new section verbatim:
    ```markdown
    ## Why is OtelSdkConfiguration.java duplicated?

    The two SDK config files are duplicated per service on purpose (DOC-05). Refactoring them into a shared `@AutoConfiguration` bean in the `otel-bootstrap` module would hide one of the two readings the workshop is built around — the whole point of Phase 2 is that an attendee reads `OpenTelemetrySdk.builder()`, `Resource.getDefault().merge(...)`, `BatchSpanProcessor.builder(...)`, `Sampler.parentBased(Sampler.alwaysOn())`, `OtlpGrpcSpanExporter.builder().setEndpoint(...)`, and `ContextPropagators.create(...)` _twice_, in two slightly different files, and develops a feel for which lines are workshop-pedagogy boilerplate and which lines are service-identity. The two files differ in only five small ways (package, JavaDoc cross-reference, the service.name string, the tracer scope name, plus the producer-only `HttpServerSpanFilter` bean) — the diff is small enough to read in one viewing. The propagation pair Phase 3 introduces, by contrast, IS shared in `otel-bootstrap` because the symmetry of one inject method matched by one extract method IS that lesson. Different design forces drive different choices; the workshop teaches both.
    ```

    Verify the edits:
    - The README still has its existing sections (Prerequisites / Workshop checkpoints / What's NOT here yet) in the correct relative order
    - Two NEW sections appear between "Workshop checkpoints" and "What's NOT here yet": "Reading the code" + "Why is OtelSdkConfiguration.java duplicated?"
    - The "Current." marker has moved from `step-01-baseline` to `step-02-traces`
    - File still parses as valid markdown (no broken fences, no orphan `</details>`, etc.)

    Do NOT change anything else in the README. Specifically: leave the H1 / intro paragraph / Prerequisites table / IDE setup / one-time setup / first run / "What's NOT here yet" bullet list (all 5 items) untouched. Phase 2 doesn't update those — Phase 7's DOC-01 is the full README rewrite.
  </action>
  <acceptance_criteria>
    - `test -f README.md` exits 0
    - DOC-05 section title present: `grep -c '^## Why is OtelSdkConfiguration.java duplicated?$' README.md` returns 1
    - DOC-05 paragraph contains the key keywords: `grep -c 'duplicated per service on purpose' README.md` returns 1; `grep -c '@AutoConfiguration' README.md` returns 1; `grep -c 'hide one of the two readings' README.md` returns 1
    - DOC-03 section title present: `grep -c '^## Reading the code$' README.md` returns 1
    - DOC-03 section links to BOTH OtelSdkConfiguration.java files: `grep -c 'producer/config/OtelSdkConfiguration.java' README.md` returns >= 1; `grep -c 'consumer/config/OtelSdkConfiguration.java' README.md` returns >= 1
    - DOC-03 section also references HttpServerSpanFilter: `grep -c 'HttpServerSpanFilter' README.md` returns >= 1
    - "**Current.**" marker has moved from step-01-baseline to step-02-traces: `grep 'step-01-baseline.*ZERO telemetry' README.md | grep -c '\*\*Current\.\*\*'` returns 0; `grep 'step-02-traces' README.md | grep -c '\*\*Current\.\*\*'` returns 1
    - Workshop checkpoint description for step-02-traces explicitly mentions intentionality: `grep -c 'intentional setup' README.md` returns 1
    - Sections appear in correct order — Prerequisites < Workshop checkpoints < Reading the code < Why is OtelSdkConfiguration.java duplicated < What's NOT here yet: `awk '/^## Prerequisites/{p=NR} /^## Workshop checkpoints/{w=NR} /^## Reading the code/{r=NR} /^## Why is OtelSdkConfiguration\.java duplicated/{d=NR} /^## What.s NOT here yet/{n=NR} END{exit (p<w && w<r && r<d && d<n)?0:1}' README.md` exits 0
    - Existing Phase-1 sections still present (regression check): `for s in 'Prerequisites' 'Required tools' 'Required free ports' 'IDE setup' 'One-time setup' 'First run' 'Workshop checkpoints'; do grep -q "$s" README.md || exit 1; done` exits 0
    - All 6 step-tag names still listed in Workshop checkpoints (regression): `for t in step-01-baseline step-02-traces step-03-context-propagation step-04-metrics step-05-logs step-06-tests; do grep -q "$t" README.md || exit 1; done` exits 0
    - File is well-formed markdown — no orphan code-fence: `awk '/^```/{c++} END{exit (c%2==0)?0:1}' README.md` exits 0
  </acceptance_criteria>
  <verify>
    <automated>grep -q '^## Why is OtelSdkConfiguration.java duplicated?$' README.md &amp;&amp; grep -q '^## Reading the code$' README.md &amp;&amp; grep -q 'duplicated per service on purpose' README.md &amp;&amp; grep -q 'producer/config/OtelSdkConfiguration.java' README.md &amp;&amp; grep -q 'consumer/config/OtelSdkConfiguration.java' README.md &amp;&amp; grep 'step-02-traces' README.md | grep -q '\*\*Current\.\*\*' &amp;&amp; awk '/^```/{c++} END{exit (c%2==0)?0:1}' README.md</automated>
  </verify>
  <done>README.md gains the two new sections in the right order (Reading the code + Why is OtelSdkConfiguration.java duplicated?) between Workshop checkpoints and What's NOT here yet; the **Current.** marker moves from step-01-baseline to step-02-traces with an updated description; existing Phase-1 sections (Prerequisites/IDE setup/One-time setup/First run/etc.) are unchanged; all 6 step-tag names still listed.</done>
</task>

<task id="02-06-T2" type="auto">
  <name>Task 2: Verify all 6 Phase 2 success criteria simultaneously green (gates the tag in T3)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/ROADMAP.md (lines 60-66 — Phase 2 success criteria — all 6 must be simultaneously green for the tag to be applied)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 184-204 — End-user-verifiable + CI-verifiable gates)
    - mise.toml (verify:bom, dev:producer, dev:consumer, demo:order, infra:up tasks)
    - All Phase 2 SUMMARYs (02-01 through 02-05) from prior plans
    - README.md (just modified in T1 — DOC-05 + DOC-03 callouts and Current marker on step-02-traces)
  </read_first>
  <action>
    Run all six Phase 2 success criteria back-to-back on a clean working tree. T3 (the tag) may ONLY run if EVERY criterion is green AND `git status --porcelain` is empty.

    **Criterion 1 — TWO distinct traces in Tempo per POST /orders, both with correct service.name labels (NEVER unknown_service:java):**

    This is the criterion Plan 02-05 T3 already exercises. Re-run the smoke test here for end-to-end confirmation against the FULL Phase 2 codebase:
    ```
    mise run infra:up
    nohup mise run dev:producer > /tmp/producer-02-06.log 2>&1 &
    PID_P=$!
    nohup mise run dev:consumer > /tmp/consumer-02-06.log 2>&1 &
    PID_C=$!
    for i in $(seq 1 60); do
      grep -q "Started ProducerApplication" /tmp/producer-02-06.log && grep -q "Started ConsumerApplication" /tmp/consumer-02-06.log && break
      sleep 2
    done
    test "$(curl -s -o /tmp/o.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}')" = "202"
    sleep 8
    PT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=5" | python3 -c "import json,sys; ts=json.load(sys.stdin)['traces']; print(ts[0]['traceID']) if ts else exit(1)")
    CT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-consumer&limit=5" | python3 -c "import json,sys; ts=json.load(sys.stdin)['traces']; print(ts[0]['traceID']) if ts else exit(1)")
    test -n "$PT" && test -n "$CT" && test "$PT" != "$CT"
    UNK=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dunknown_service%3Ajava&limit=5" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('traces',[])))")
    test "$UNK" = "0"
    ```

    **Criterion 2 — Ctrl-C flushes the last batch (graceful shutdown via @Bean(destroyMethod="close")):**

    Verify by sending another POST and immediately sending SIGTERM to the producer; the producer trace from the JUST-emitted POST must still appear in Tempo within ~12 seconds (BSP flush + close().join(10s)).
    ```
    BEFORE_PT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=50" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('traces',[])))")
    test "$(curl -s -o /tmp/o2.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-2","quantity":1}')" = "202"
    kill $PID_P
    for i in $(seq 1 12); do kill -0 $PID_P 2>/dev/null || break; sleep 1; done
    ! kill -0 $PID_P 2>/dev/null
    sleep 3
    AFTER_PT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=50" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('traces',[])))")
    python3 -c "import sys; b=int('$BEFORE_PT'); a=int('$AFTER_PT'); assert a > b, f'last batch not flushed: before={b} after={a}'; print(f'OK: traces grew {b}->{a} after Ctrl-C')"
    ```

    **Criterion 3 — Both OtelSdkConfiguration.java files exist with heavy commenting:**
    ```
    test -f producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
    test -f consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
    P_COMMENTS=$(grep -cE '^\s*(//|\*|/\*\*|\*/)' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java)
    C_COMMENTS=$(grep -cE '^\s*(//|\*|/\*\*|\*/)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java)
    python3 -c "p=int('$P_COMMENTS'); c=int('$C_COMMENTS'); assert p>=40 and c>=40, f'comment density too low: producer={p} consumer={c}'; print(f'OK: comments producer={p} consumer={c}')"
    grep -B1 -A12 'parent-based, always-on root' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | grep -q 'traceIdRatioBased(0.1)'
    grep -B1 -A12 'parent-based, always-on root' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java | grep -q 'traceIdRatioBased(0.1)'
    ```

    **Criterion 4 — Producer trace = SERVER+INTERNAL+PRODUCER; consumer trace = CONSUMER+INTERNAL:**

    Restart the producer (we killed it in Criterion 2) and re-run a POST to capture a clean trace pair.
    ```
    nohup mise run dev:producer > /tmp/producer-02-06b.log 2>&1 &
    PID_P=$!
    for i in $(seq 1 45); do grep -q "Started ProducerApplication" /tmp/producer-02-06b.log && break; sleep 2; done
    test "$(curl -s -o /tmp/o3.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-3","quantity":2}')" = "202"
    sleep 8
    NEW_PT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=5" | python3 -c "import json,sys; print(json.load(sys.stdin)['traces'][0]['traceID'])")
    NEW_CT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-consumer&limit=5" | python3 -c "import json,sys; print(json.load(sys.stdin)['traces'][0]['traceID'])")
    test "$NEW_PT" != "$NEW_CT"
    PT_TR=$(curl -s "http://localhost:3200/api/traces/$NEW_PT")
    printf '%s' "$PT_TR" | python3 -c "import json,sys,collections; t=json.load(sys.stdin); spans=[s for b in t.get('batches',[]) for ss in b.get('scopeSpans',[]) for s in ss.get('spans',[])]; k=collections.Counter(s.get('kind',0) for s in spans); assert k.get(2,0)>=1 and k.get(1,0)>=1 and k.get(4,0)>=1, f'producer kinds {k}'; print(f'OK producer: kinds {dict(k)}')"
    CT_TR=$(curl -s "http://localhost:3200/api/traces/$NEW_CT")
    printf '%s' "$CT_TR" | python3 -c "import json,sys,collections; t=json.load(sys.stdin); spans=[s for b in t.get('batches',[]) for ss in b.get('scopeSpans',[]) for s in ss.get('spans',[])]; k=collections.Counter(s.get('kind',0) for s in spans); assert k.get(5,0)>=1 and k.get(1,0)>=1, f'consumer kinds {k}'; print(f'OK consumer: kinds {dict(k)}')"
    printf '%s' "$CT_TR" | python3 -c "import json,sys; t=json.load(sys.stdin); spans=[s for b in t.get('batches',[]) for ss in b.get('scopeSpans',[]) for s in ss.get('spans',[])]; cs=next(s for s in spans if s.get('kind')==5); psid=cs.get('parentSpanId',''); assert psid in ('','0000000000000000'), f'CONSUMER parentSpanId {psid!r} — Context.root() should produce empty'; print('OK: CONSUMER parentSpanId empty (Context.root() honored)')"
    ```

    **Criterion 5 — README has the per-service-duplication callout (DOC-05):**
    ```
    grep -q '^## Why is OtelSdkConfiguration.java duplicated?$' README.md
    grep -q 'duplicated per service on purpose' README.md
    grep -q 'hide one of the two readings' README.md
    ```

    **Criterion 6 — pre-tag clean tree:**
    ```
    test -z "$(git status --porcelain)" || { git status --porcelain; echo "ABORT: uncommitted changes — commit them before running T3"; exit 1; }
    ```

    **Final cleanup:** Stop both apps cleanly so T3 inherits a clean process state.
    ```
    kill $PID_P $PID_C 2>/dev/null
    for i in $(seq 1 12); do { kill -0 $PID_P 2>/dev/null || kill -0 $PID_C 2>/dev/null; } || break; sleep 1; done
    ! pgrep -f spring-boot:run
    ```

    Failure mode: if ANY criterion fails, T3 (the tag) MUST NOT run. Document which criterion failed in the SUMMARY and STOP — fixing it is a Phase 2 follow-up task, not a planner task. Common failures:
    - Criterion 1 fails on `unknown_service:java` count → Resource.getDefault().merge args wrong; rerun Plan 02-02 / 02-03 fix.
    - Criterion 2 fails on "last batch not flushed" → @Bean is missing destroyMethod="close"; rerun Plan 02-02 / 02-03 fix.
    - Criterion 4 fails on producer or consumer trace shape → instrumentation regression; rerun Plan 02-04 / 02-05 fix.
    - Criterion 6 fails → uncommitted changes from a partial earlier task; commit them with a chore message and re-run T2.
  </action>
  <acceptance_criteria>
    - Criterion 1 PASS: producer + consumer traces visible in Tempo per POST /orders with DIFFERENT traceIds and correct service.name labels; zero unknown_service:java traces
    - Criterion 2 PASS: SIGTERM on producer flushes the last batch (trace count for service.name=order-producer increases AFTER kill compared to BEFORE the final POST)
    - Criterion 3 PASS: both OtelSdkConfiguration.java files >= 40 comment lines; sampler teaching comment includes the traceIdRatioBased(0.1) reference (multi-paragraph per D-14)
    - Criterion 4 PASS: producer trace contains kinds {1,2,4} = INTERNAL+SERVER+PRODUCER; consumer trace contains kinds {1,5} = INTERNAL+CONSUMER; consumer's CONSUMER span has empty parentSpanId
    - Criterion 5 PASS: README has the DOC-05 section title and the key paragraph keywords (`duplicated per service on purpose` + `hide one of the two readings`)
    - Criterion 6 PASS: `test -z "$(git status --porcelain)"` exits 0
    - Phase 2 invariant preserved: `mise run verify:bom` still exits 0
    - All background processes cleaned up: `! pgrep -f spring-boot:run` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mise run verify:bom 2>&amp;1 | tail -1 | grep -q 'Phase 2 baseline confirmed' &amp;&amp; grep -q '^## Why is OtelSdkConfiguration.java duplicated?$' README.md &amp;&amp; test -z "$(git status --porcelain)" &amp;&amp; ! pgrep -f spring-boot:run</automated>
  </verify>
  <done>All six Phase 2 success criteria are simultaneously green: TWO distinct traces (different traceIds) per POST with correct service.name labels and zero unknown_service:java; SIGTERM flushes the last batch; both OtelSdkConfiguration files have >= 40 comment lines including the multi-paragraph sampler tradeoff; producer trace kinds {SERVER, INTERNAL, PRODUCER}, consumer trace kinds {CONSUMER, INTERNAL} with consumer's CONSUMER root having empty parentSpanId; README has the DOC-05 callout; git working tree clean. T3 may proceed with the tag.</done>
</task>

<task id="02-06-T3" type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Human-verify Phase 2 broken-trace baseline + create annotated git tag step-02-traces</name>
  <what-built>
    - Wave 1: POM dependencies — both producer-service/pom.xml and consumer-service/pom.xml carry the IDENTICAL 5-dep OTel block (api / sdk / exporter-otlp BOM-managed; semconv 1.40.0 + semconv-incubating 1.40.0-alpha pinned); mise.toml `verify:bom` task inverted to assert one-version-per-OTel-artifact.
    - Wave 2: Per-service OtelSdkConfiguration.java written in BOTH services — manual `OpenTelemetrySdk.builder()` with Resource (semconv constants, no `unknown_service:java`), `SdkTracerProvider` + `BatchSpanProcessor` + `OtlpGrpcSpanExporter` (env-var endpoint with localhost:4317 fallback, no autoconfigure), `Sampler.parentBased(Sampler.alwaysOn())` with multi-paragraph teaching comment, `@Bean(destroyMethod="close")` for graceful shutdown, composite W3CTraceContext + W3CBaggage propagators registered for Phase 3 reuse, and `@Bean Tracer` per service. Producer ALSO carries `HttpServerSpanFilter` (D-07 producer-only) wrapping every non-`/actuator/*` request in a SERVER span with 7 HTTP semconv attrs.
    - Wave 3: Business-code instrumentation — producer's `OrderService.place(...)` (INTERNAL span) and `OrderPublisher.publish(...)` (PRODUCER span with 4 messaging semconv attrs using the new MESSAGING_OPERATION_TYPE constant + SEND value enum per RESEARCH FLAG #1); consumer's `OrderListener.onOrder(...)` (CONSUMER span starting from `Context.root()` per D-10 with the verbatim multi-line teaching comment previewing Phase 3's `propagator.extract(...)` line) and `ProcessingService.process(...)` (INTERNAL span). Five span sites total, all using the EXACT D-01 pure-inline template.
    - Wave 4 (this plan): README.md gains `## Reading the code` (DOC-03) and `## Why is OtelSdkConfiguration.java duplicated?` (DOC-05) sections; `## Workshop checkpoints` updated so step-02-traces is **Current**. T2 already verified all 6 Phase 2 success criteria simultaneously green and the working tree is clean.
    - Pending in this task: human verification of the workshop-facing surfaces (Tempo Explore shows the TWO disconnected traces; RabbitMQ Mgmt UI shows the published message DOES NOT carry a `traceparent` header — proving Phase 2's missing-propagation state is real; README reads cleanly for the DOC-03/DOC-05 deltas) plus creation of the annotated git tag `step-02-traces`. This is the SECOND of the six WORK-01 tags; the convention established at `step-01-baseline` propagates here.
  </what-built>
  <how-to-verify>
    Spend ~10 minutes confirming the workshop attendee experience is clean before the tag becomes immutable. The state at this tag is **deliberately broken** (TWO traces instead of one) — that's the Phase 2 deliverable. Phase 3 fixes it.

    **Step 1 — Open the README in a markdown viewer** (GitHub preview, IntelliJ markdown panel, VS Code preview, or `glow README.md`). Confirm:
    1. The new `## Reading the code` section is present, lists both `OtelSdkConfiguration.java` paths as clickable links, and mentions `HttpServerSpanFilter`.
    2. The new `## Why is OtelSdkConfiguration.java duplicated?` section is present and reads as a single paragraph that names the duplication intentional and warns against `@AutoConfiguration` extraction.
    3. The `## Workshop checkpoints` list shows `step-02-traces` as **Current** (not `step-01-baseline`).
    4. No broken markdown — code fences balanced, links resolve when clicked from the markdown viewer.

    **Step 2 — Sanity-check the running stack:**
    - `mise run infra:up` — both containers should be healthy.
    - Run `mise run dev` in one terminal (parallel producer + consumer).
    - In another terminal, `mise run demo:order`. Expect 202 returned and the consumer-side terminal logs `OrderCreated received: orderId=<uuid>`.

    **Step 3 — Open Grafana → Tempo Explore (`http://localhost:3000` → admin/admin → Explore → Tempo datasource):**
    1. In the Search tab, set "Service Name" to `order-producer`. Click Run query. Confirm at least ONE trace appears with the structure: a SERVER root span "POST /orders" → INTERNAL "OrderService.place" → PRODUCER "orders.created publish".
    2. Click the trace and inspect the spans. Confirm:
       - The PRODUCER span has the 4 messaging semconv attributes: `messaging.system=rabbitmq`, `messaging.destination.name=orders.created`, `messaging.operation.type=send`, `messaging.rabbitmq.destination.routing_key=order.created`. NOT the deprecated `messaging.operation` (bare).
       - The SERVER span has `http.request.method=POST`, `url.path=/orders`, `http.response.status_code=202`, `http.route=/orders` (and the additional url.scheme / server.address / server.port).
    3. Go back to Search. Set "Service Name" to `order-consumer`. Click Run query. Confirm a SEPARATE trace appears with structure: CONSUMER root span "orders.created process" → INTERNAL "ProcessingService.process".
    4. Note the trace IDs of the two traces you opened. **They MUST be different.** This is the Phase 2 broken-then-fixed-pedagogy state — the producer's trace ID and the consumer's trace ID are different because there is no propagation across the AMQP boundary yet. Phase 3 fixes this; the dramatic delta is Phase 3's headline lesson.

    **Step 4 — Open RabbitMQ Management UI (`http://localhost:15672` → guest/guest):**
    1. Go to Queues → click `orders.created`.
    2. Use "Get messages" with `Ack mode: Reject requeue true` and `Messages: 1` (this peeks at a queued message without consuming it).
    3. Inspect the displayed message properties. Confirm:
       - The message has the JSON payload (`sku`, `quantity`, `orderId` keys).
       - The message properties / headers section does NOT contain a `traceparent` header. (If you see one, propagation is accidentally working — Phase 2's broken state is broken in the wrong direction.)
    4. This confirms PROP-01 is correctly NOT yet implemented — Phase 3 will inject the `traceparent` header here.

    **Step 5 — Verify graceful shutdown manually (criterion #2 — TRACE-04):**
    - In the `mise run dev` terminal, send Ctrl-C ONCE. Both apps should print their shutdown banners and exit within ~10 seconds.
    - Open Tempo again. Confirm the trace from the LAST `POST /orders` you sent before Ctrl-C is visible (BSP flushed on close — the destroyMethod="close" cascade worked).

    **Step 6 — Approve or describe issues.**

    If approved, proceed to Step 7 (the tag). If issues exist, list them with file references; do NOT tag.

    **Step 7 — Create the annotated git tag (executor performs after approval):**

    First, ensure git working tree is still clean (T2 confirmed this; double-check):
    ```
    git status --porcelain
    ```
    Expected: empty output. If anything is staged or untracked beyond .gitignored items, commit them first with a message like:
    ```
    git add <files>
    git commit -m "chore(02): phase-2 wrap-up"
    ```

    Then create the annotated tag (the `-a` flag is mandatory — lightweight tags are not sufficient for WORK-01 per the convention established at `step-01-baseline`):
    ```
    git tag -a step-02-traces -m "Workshop checkpoint: Phase 2 — manual SDK bootstrap; producer + consumer in DIFFERENT traces.

    Both services run the OpenTelemetry Java SDK 1.61.0 with manual @Bean wiring (no autoconfigure, no agent, no Micrometer bridge). Each POST /orders produces TWO distinct traces in Tempo: a producer trace (SERVER + INTERNAL + PRODUCER spans with HTTP + messaging semconv attributes) and a consumer trace (CONSUMER + INTERNAL spans). The two traces have DIFFERENT traceIds because no W3C context propagation crosses the AMQP boundary yet — that is the Phase 3 headline lesson. The OtelSdkConfiguration.java file is intentionally duplicated per service (DOC-05).

    All six Phase 2 success criteria simultaneously green at this commit:
      1. TWO distinct traces visible in Tempo per POST /orders with correct service.name labels (never unknown_service:java)
      2. Ctrl-C flushes the last span batch via @Bean(destroyMethod=\"close\")
      3. Both OtelSdkConfiguration.java files heavily commented (DOC-03)
      4. Producer trace = SERVER+INTERNAL+PRODUCER; consumer trace = CONSUMER+INTERNAL
      5. README explicitly states the per-service duplication is intentional (DOC-05)
      6. This tag reproduces the state on git checkout"
    ```

    Verify the tag:
    ```
    git tag --list step-02-traces
    git for-each-ref --format='%(objecttype) %(refname)' refs/tags/step-02-traces
    git show step-02-traces | head -20
    ```

    The first command outputs `step-02-traces`. The second outputs `tag refs/tags/step-02-traces` (NOT `commit` — proves annotated, not lightweight). The third shows the full tag message.

    DO NOT push the tag automatically. The user (`coto@petabyte.cl`) decides when to push. Mention in the SUMMARY that `git push origin step-02-traces` is the follow-up the user runs when ready (or `git push --tags` to push all annotated tags). The tag is local-only until the user explicitly pushes — this respects the GSD git-safety protocol.

    **Step 8 — Reproducibility self-test (criterion #6):**
    From a temp clone (less invasive than checking out in the working tree):
    ```
    git -C /tmp clone --branch step-02-traces --depth 1 file://$(pwd) verify-step-02 2>/dev/null
    cd /tmp/verify-step-02
    mise install
    mise run verify:bom  # MUST exit 0 with "Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules."
    cd -
    rm -rf /tmp/verify-step-02
    ```

    Either path proves criterion #6 (`git checkout step-02-traces` reproduces the green-Phase-2 state).
  </how-to-verify>
  <resume-signal>Type "approved" to proceed with creating the annotated tag, "not yet — issues:" with a list of issues to fix, or "skip-tag" to defer tagging until later (which leaves Phase 2 incomplete — WORK-01 unsatisfied for the second checkpoint).</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 02-06 — README + tag)

| Boundary | Description |
|----------|-------------|
| Local repo → remote (e.g., GitHub) | Tag pushed via `git push origin step-02-traces` — workshop artifact will be public/internally-published |
| Workshop attendee reading README → external links | Existing Phase 1 external links unchanged; the new sections add only intra-repo links to .java files (no new external URLs) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-06-01 | Tampering | Lightweight tag substituted for annotated tag, allowing silent re-pointing | mitigate | The tag-creation command uses `git tag -a` (annotated); T3's verification step asserts via `git for-each-ref` that the tag's objecttype is `tag`, not `commit` |
| T-2-06-02 | Information Disclosure | README.md leaking internal infrastructure details | accept | README is intended public/internal-team-shared; no secrets, no internal hosts, no real credentials beyond documented workshop defaults (guest/guest, admin/admin) — Phase 1 baseline preserved |
| T-2-06-03 | Tampering | A future phase tag overwrites step-02-traces (force-push) | mitigate | Tags are immutable under normal git workflow; `git push --force-with-lease origin :step-02-traces` would be needed to remove. Convention is documented in PROJECT.md (Key Decisions row "Staged git checkpoints"). Out-of-band protection (e.g., GitHub branch/tag protection rules) is the user's call. |
| T-2-06-04 | Repudiation | Tag created without a meaningful message; cannot trace what state it represents | mitigate | T3 acceptance criteria require the annotated message to mention "Phase 2", "DIFFERENT traceIds", "step-02-traces" — content auditable |
| T-2-06-05 | Information Disclosure | The DOC-05 section reveals architectural intent (per-service duplication) — could "tip off" a malicious code reviewer | accept | Workshop scope; the entire repo is teaching material — there is no architectural secret to protect |

**Phase scope:** Workshop README delta + git tag. No runtime threat surface introduced. The phase-level threats T1 (env-var endpoint), T2 (span PII), T3 (actuator filter) are all evaluated and mitigated in Plans 02-02 / 02-03 / 02-04 / 02-05; Plan 02-06 introduces no new threat. Out of scope: signed tags (`git tag -s`) — same call as Phase 1, the workshop is not currently signing artifacts.
</threat_model>

<verification>
- README.md has the two new sections (`## Reading the code` + `## Why is OtelSdkConfiguration.java duplicated?`) in the correct relative order; the **Current.** marker is on `step-02-traces`; existing Phase 1 sections are unchanged.
- All 6 Phase 2 success criteria from ROADMAP.md verified simultaneously green in T2.
- After T3 approval: `git tag --list step-02-traces` outputs `step-02-traces`.
- `git for-each-ref --format='%(objecttype)' refs/tags/step-02-traces` outputs `tag` (annotated, not lightweight).
- `git show step-02-traces` displays the tag message containing "Phase 2", "DIFFERENT traceIds", and the six success-criteria lines.
- `git checkout step-02-traces` (in a clean temp clone) reproduces the green-Phase-2 state — `mise run verify:bom` exits 0 there.
- Phase 2 invariant preserved: `mise run verify:bom` continues to exit 0.
</verification>

<success_criteria>
- DOC-03 satisfied (README half — heavy comments themselves landed in Plans 02-02 + 02-03): README's `## Reading the code` section explicitly points workshop attendees at both `OtelSdkConfiguration.java` files and `HttpServerSpanFilter.java` as the workshop's textbook for the SDK setup.
- DOC-05 satisfied: README has a single-paragraph `## Why is OtelSdkConfiguration.java duplicated?` section explicitly naming the per-service duplication as intentional and warning against refactoring into a shared `@AutoConfiguration` bean.
- WORK-01 (Phase 2 portion) satisfied: annotated git tag `step-02-traces` exists on the working branch; the tagging convention from Phase 1 propagates to Phase 2.
- All 6 Phase 2 success criteria from ROADMAP.md §"Phase 2" verified simultaneously green at the moment of tagging.
- The tag is local-only after T3 (user pushes when ready) — respects git-safety protocol.
- Phase 2 is now SHIPPED and the repo is the broken-trace baseline that Phase 3 will fix in `step-03-context-propagation` to produce the workshop's most powerful pedagogical moment.
</success_criteria>

<output>
After completion, create `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-06-SUMMARY.md` documenting:
- README.md final structure (paste H1 + section titles list)
- Confirmed Phase 2 success-criteria results from T2 (paste the green outputs of each criterion)
- Confirmed annotated tag exists: paste `git for-each-ref --format='%(objecttype) %(objectname:short) %(refname)' refs/tags/step-02-traces`
- Confirmed tag message: paste `git show step-02-traces | head -20`
- A note that the user should run `git push origin step-02-traces` when ready to publish (NOT done by this plan — same git-safety convention as Phase 1's step-01-baseline)
- Files modified: 1 (README.md) + 1 git ref (refs/tags/step-02-traces)
- Confirmed reproducibility self-test (paste the temp-clone verify:bom output)

Then create `.planning/phases/02-manual-sdk-bootstrap-first-traces/PHASE-SUMMARY.md` rolling up all six plan summaries — phase exit summary documenting:
- Total artifacts created across the phase: 5 new Java files (2 OtelSdkConfiguration + 1 HttpServerSpanFilter, plus modifications to OrderService/OrderPublisher/OrderListener/ProcessingService) + POM changes + mise.toml task rewrite + README delta + 1 git ref
- The single gate sentence: "POST /orders produces TWO distinct traces in Tempo with different traceIds — broken-then-fixed-pedagogy state confirmed" — confirmed
- Tag created: step-02-traces
- Lessons learned: any deviations from RESEARCH.md (e.g., MessagingIncubatingAttributes inner-class import path quirks if encountered), surprising failure modes
- Phase 3 readiness signal: composite W3C propagators are wired in both OtelSdkConfiguration files (Phase 3 calls `openTelemetry.getPropagators().getTextMapPropagator()` to use them); inline PRODUCER + CONSUMER spans are in place AND will be REPLACED by `otel-bootstrap`'s `TracingMessagePostProcessor` + `TracingMessageListenerAdvice` in Phase 3 — the Phase 2→Phase 3 git diff Phase 3's SC #5 mandates be readable in one viewing is now well-defined (per CONTEXT.md D-09)
- Phase 3 hand-off note: APP-04 (deterministic 10% failure) and TRACE-09 (recordException + setStatus(ERROR) wired to actual failures) land alongside the propagation pair; the D-03 catch shape already in place across all 5 Phase 2 spans means Phase 3 only needs to add the THROW site, not restructure existing methods
</output>
