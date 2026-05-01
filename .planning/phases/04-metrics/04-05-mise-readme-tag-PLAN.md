---
id: 04-05-mise-readme-tag
phase: 04-metrics
plan: 05
type: execute
wave: 3
depends_on: [04-01-meter-pipeline-refactor, 04-02-producer-counter, 04-03-producer-histogram, 04-04-consumer-gauge]
requirements: [METRIC-01, METRIC-02, METRIC-03, METRIC-04]
requirements_addressed: [METRIC-01, METRIC-02, METRIC-03, METRIC-04]
files_modified:
  - mise.toml
  - README.md
autonomous: false
objective: "Land the Phase 4 workshop-attendee surfaces and the exit gate. (1) Update `mise.toml`'s `demo:order` task to send TWO sample payloads with `priority=express` and `priority=standard` (D-10 — without two values the order.priority cardinality lesson is invisible in Mimir). (2) Add a small `## Step 4: Metrics` section to README.md keyed to tag `step-04-metrics` (D-21 — names the three instrument shapes, calls out the seconds-not-millis trap from D-13, names the bounded-cardinality lesson from D-10, shows the OTel→Prometheus dot-to-underscore mapping). Update the README's `## Workshop checkpoints` Current marker from `step-03-context-propagation` to `step-04-metrics`, and update the `## What's NOT here yet` section to remove the metrics bullet. (3) Verify all 4 ROADMAP success criteria simultaneously green at the live stack (orders_created_total visible within 15s with order_priority labels, http_server_request_duration_seconds populated with count+sum+buckets after ~30s, orders_queue_depth_estimate sample every 10s, clean working tree). (4) Human-verify gate (workshop attendee experience), then create the annotated git tag `step-04-metrics` (WORK-01 / D-22 — same convention as Phases 1/2/3). STATE/ROADMAP/REQUIREMENTS `[x]` flips land atomically with the tag-apply commit; tag is local-only until user pushes."
must_haves:
  truths:
    - "mise.toml `demo:order` task sends TWO POST /orders requests: one with `{\"priority\":\"express\"}` and one with `{\"priority\":\"standard\"}` (D-10 — without two values the cardinality-awareness lesson is invisible in Mimir)"
    - "mise.toml `demo:order` task uses the existing multi-line `run = \"\"\" ... \"\"\"` style with `set -e` (matches the in-repo `verify:bom` analog at lines 122-165)"
    - "mise.toml `demo:order` description updated to name the Phase 4 priority-alternation purpose"
    - "README.md gains a `## Step 4: Metrics` section between `## Workshop checkpoints` and `## Reading the code` (matches Phase 3's placement of the `## Why is the propagation pair shared?` callout)"
    - "Step 4 README section names all three instrument shapes (Counter / Histogram / ObservableGauge) and links to the modified files (OrderService, HttpServerSpanFilter, QueueDepthGauge)"
    - "Step 4 README section explicitly calls out the seconds-not-millis trap (D-13) and the bounded-cardinality lesson (D-10)"
    - "Step 4 README section shows the OTel→Prometheus name mapping: `orders.created` → `orders_created_total`; `http.server.request.duration` (unit s) → `http_server_request_duration_seconds`; `orders.queue.depth.estimate` → `orders_queue_depth_estimate` — explicit dot-to-underscore + `_total` suffix mention"
    - "README.md `## Workshop checkpoints` section: `step-04-metrics` description matches the Phase 4 reality (mentions the meter pipeline + the three instrument shapes); the `**Current.**` marker MOVES from `step-03-context-propagation` to `step-04-metrics`"
    - "README.md `## What's NOT here yet` section: the existing `No metrics or log correlation (Phase 4 / Phase 5)` bullet is UPDATED to `No log correlation (Phase 5)` — Phase 4 ships the metrics half"
    - "All 4 Phase 4 ROADMAP success criteria simultaneously green at the moment of tagging: orders_created_total increments within 15s with order_priority='express' label visible (SC #1), http_server_request_duration_seconds populated with count+sum+bucket histograms after ~30s (SC #2), orders_queue_depth_estimate produces a fresh sample every 10s (SC #3 — proves PeriodicMetricReader interval override per METRIC-01), clean working tree (precondition for tagging)"
    - "Phase 2 invariant preserved: `mise run verify:bom` exits 0 (no new io.opentelemetry artifacts; opentelemetry-exporter-otlp + opentelemetry-sdk-metrics already on classpath)"
    - "Annotated git tag `step-04-metrics` exists on the working branch; created with `git tag -a` (NOT lightweight); points at a commit where ALL 4 success criteria are simultaneously true; tag message references the meter pipeline + the three instrument shapes + METRIC-01..04"
    - "Repository working tree is clean at the moment the tag is applied"
    - "git checkout step-04-metrics reproduces the green Phase-4 state in a temp clone"
  artifacts:
    - path: "mise.toml"
      provides: "demo:order task updated to alternate priority=express and priority=standard payloads (D-10) so workshop attendees see TWO orders_created_total series in Mimir"
      contains: "priority"
    - path: "README.md"
      provides: "Phase 4 documentation delta: ## Step 4: Metrics section keyed to tag step-04-metrics; **Current.** marker moved; metrics removed from What's NOT here yet"
      contains: "Step 4: Metrics"
    - path: "(git ref) refs/tags/step-04-metrics"
      provides: "Immutable annotated workshop checkpoint marking Phase 4 exit; FOURTH of the six WORK-01 tags"
      contains: "(annotated tag message — references SdkMeterProvider + Counter/Histogram/ObservableGauge + METRIC-01..04)"
  key_links:
    - from: "mise demo:order"
      to: "Two priority values in Mimir (orders_created_total{order_priority=...})"
      via: "Two curl invocations: priority=express + priority=standard (D-10)"
      pattern: "priority"
    - from: "README ## Step 4: Metrics"
      to: "OrderService.java + HttpServerSpanFilter.java + QueueDepthGauge.java"
      via: "Markdown links to the three Phase 4 modification targets"
      pattern: "OrderService\\.java|HttpServerSpanFilter\\.java|QueueDepthGauge\\.java"
    - from: "git tag step-04-metrics"
      to: "Working metrics state (orders_created_total / http_server_request_duration_seconds / orders_queue_depth_estimate visible in Mimir)"
      via: "Commit pointed at by the tag reproduces the Phase 4 success-criteria-green state"
      pattern: "step-04-metrics"
---

<objective>
Land the Phase 4 workshop-attendee surfaces and the exit gate.

**Three deliverables in this plan:**
1. **`mise.toml` update (D-10):** the `demo:order` task currently POSTs a single `{"sku":"WIDGET-1","quantity":3}` payload. Phase 4 needs TWO payloads (priority=express and priority=standard) so workshop attendees see TWO time series in Mimir for `orders_created_total{order_priority=...}` — without the two values the cardinality-awareness lesson is invisible.

2. **README.md delta (D-21):** add a small `## Step 4: Metrics` section keyed to tag `step-04-metrics` that:
   - Names the three instrument shapes (Counter / Histogram / ObservableGauge) and points readers at the three Phase 4 source files.
   - Calls out the seconds-not-millis trap (D-13) — the textbook OTel pitfall.
   - Names the bounded-cardinality lesson (D-10) and the OTel→Prometheus name-mangling rules (dots → underscores; `_total` suffix on monotonic counters).
   - Update `## Workshop checkpoints`: move the `**Current.**` marker from `step-03-context-propagation` to `step-04-metrics`; expand the `step-04-metrics` description to match Phase 4 reality.
   - Update `## What's NOT here yet`: change `No metrics or log correlation (Phase 4 / Phase 5)` to `No log correlation (Phase 5)`.
   - The full step-by-step README walkthrough body (DOC-01) and Grafana screenshots (DOC-04) land in Phase 7 — Phase 4's delta is intentionally small.

3. **Phase 4 exit gate (WORK-01 / D-22):**
   - Verify all 4 Phase 4 ROADMAP success criteria simultaneously green at the live stack.
   - Human-verify the workshop-attendee experience.
   - Create the annotated git tag `step-04-metrics` (same convention as Phases 1/2/3).
   - Tag is local-only after creation; user pushes when ready.

This is the FOURTH of the six WORK-01 tags. STATE.md / ROADMAP.md / REQUIREMENTS.md `[x]` flips land atomically with the tag-apply commit (do NOT pre-flip).

Purpose: METRIC-01..04 verified live; Phase 4 SHIPPED.

Output: 2 modified files (mise.toml, README.md) + 1 git ref (refs/tags/step-04-metrics).
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
@.planning/phases/04-metrics/04-CONTEXT.md
@.planning/phases/04-metrics/04-PATTERNS.md
@.planning/phases/04-metrics/04-01-meter-pipeline-refactor-PLAN.md
@.planning/phases/04-metrics/04-02-producer-counter-PLAN.md
@.planning/phases/04-metrics/04-03-producer-histogram-PLAN.md
@.planning/phases/04-metrics/04-04-consumer-gauge-PLAN.md
@.planning/phases/03-amqp-context-propagation/03-05-readme-and-exit-gate-PLAN.md
@CLAUDE.md
@README.md
@mise.toml
</context>

<tasks>

<task id="04-05-T1" type="auto">
  <name>Task 1: Update mise.toml `demo:order` task to send two priority payloads (D-10)</name>
  <files>mise.toml</files>
  <read_first>
    - mise.toml (current state — `[tasks."demo:order"]` block at lines 118-120; `[tasks."verify:bom"]` block at lines 122-165 is the in-repo analog for the multi-line `run = """ ... """` style)
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 529-565 — the D-10 target shape including the multi-line run block, set -e discipline, and the third-payload-fallback alternative)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-10 two-payload requirement; D-09 priority attribute key shape)
  </read_first>
  <action>
    Modify `mise.toml` IN PLACE. The current `[tasks."demo:order"]` block (lines 118-120 today) is:

    ```toml
    [tasks."demo:order"]
    description = "POST a sample order to the producer; expect 202"
    run = "curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders -H 'Content-Type: application/json' -d '{\"sku\":\"WIDGET-1\",\"quantity\":3}' && echo"
    ```

    Replace this block with the multi-line shape that sends BOTH priority values (D-10). The new block uses the same `set -e` + triple-quoted-string style as `[tasks."verify:bom"]` already in the file:

    ```toml
    [tasks."demo:order"]
    description = "POST sample orders to the producer; expect 202. Phase 4: alternates priority=express and priority=standard so Mimir shows two orders_created_total series."
    run = """
    set -e
    curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders \\
      -H 'Content-Type: application/json' \\
      -d '{"sku":"WIDGET-1","quantity":3,"priority":"express"}' && echo
    curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders \\
      -H 'Content-Type: application/json' \\
      -d '{"sku":"WIDGET-2","quantity":1,"priority":"standard"}' && echo
    """
    ```

    Leave ALL other tasks in `mise.toml` unchanged. Specifically: do NOT touch `[tasks.preflight]`, `[tasks."infra:up"]`/`infra:down`/`infra:reset`/`infra:logs`, `[tasks.build]`, `[tasks.test]`, `[tasks."dev:producer"]`/`dev:consumer`/`dev`, `[tasks."verify:bom"]`, `[tasks."ui:grafana"]`/`ui:rabbitmq`. The `[env]` block (`OTEL_EXPORTER_OTLP_ENDPOINT`, etc.) and the `[tools]` block (Java + Maven pins) also stay untouched.

    After editing, verify the new task runs cleanly (this requires the producer to be running — defer the runtime test to T3 below, just sanity-check the parsing here).

    **Constraint preservation checklist:**
    - File still parses as valid TOML — no broken multi-line blocks, no unescaped quotes inside the JSON payloads.
    - The two payloads use DIFFERENT priority values (express vs standard) — D-10's "two series" property requires distinct values.
    - The `set -e` discipline matches the `verify:bom` analog (so a failed first curl aborts the second).
    - The `&& echo` at end of each curl line preserves the human-readable terminal output of Phase 1's task.
  </action>
  <acceptance_criteria>
    - File still exists: `test -f mise.toml`
    - mise.toml parses as valid TOML (use python's tomllib or `mise tasks list` as a parse-check): `python3 -c "import tomllib; tomllib.loads(open('mise.toml').read())" 2>&1 | grep -qE '^$' || python3 -c "import tomllib; tomllib.load(open('mise.toml','rb'))"`
    - The demo:order task block exists: `grep -qE '^\[tasks\."demo:order"\]$' mise.toml`
    - demo:order description mentions Phase 4 / priority: `awk '/^\[tasks\."demo:order"\]$/,/^\[/' mise.toml | grep -qE 'description.*[Pp]riority|description.*Phase 4'`
    - demo:order sends BOTH priority values (D-10): `awk '/^\[tasks\."demo:order"\]$/,/^\[tasks\./' mise.toml | grep -qF '"priority":"express"' && awk '/^\[tasks\."demo:order"\]$/,/^\[tasks\./' mise.toml | grep -qF '"priority":"standard"'`
    - At least 2 occurrences of `"priority"` inside the demo:order block (sanity for two-payload property): `awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -c '"priority"' | awk '{ if ($1 < 2) exit 1 }'`
    - At least 2 curl invocations inside demo:order: `awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -c 'curl -sf -X POST' | awk '{ if ($1 < 2) exit 1 }'`
    - set -e discipline preserved: `awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -qE '^set -e'`
    - PRODUCER_PORT env var still used (preserves Phase 1 indirection): `awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -qF 'PRODUCER_PORT'`
    - Other tasks unchanged (regression — none of these tasks edited): `for t in 'preflight' 'infra:up' 'infra:down' 'verify:bom' 'dev:producer' 'dev:consumer'; do grep -qE "^\[tasks\.\"?${t}\"?\]" mise.toml || grep -qE "^\[tasks\.${t}\]" mise.toml || exit 1; done`
    - [env] block preserved: `grep -qE 'OTEL_EXPORTER_OTLP_ENDPOINT.*=.*"http://localhost:4317"' mise.toml && grep -qE 'OTEL_EXPORTER_OTLP_PROTOCOL.*=.*"grpc"' mise.toml`
    - [tools] block preserved: `grep -qE 'corretto-17\.0\.13\.11\.1' mise.toml && grep -qE 'maven.*=.*"3\.9\.11"' mise.toml`
  </acceptance_criteria>
  <verify>
    <automated>python3 -c "import tomllib; tomllib.load(open('mise.toml','rb'))" && grep -qE '^\[tasks\."demo:order"\]$' mise.toml && awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -qF '"priority":"express"' && awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -qF '"priority":"standard"' && awk '/^\[tasks\."demo:order"\]$/{p=1} p && /^\[tasks\.[^d]/{p=0} p' mise.toml | grep -qE '^set -e'</automated>
  </verify>
  <done>mise.toml `demo:order` task sends two POST /orders payloads — one with priority=express, one with priority=standard (D-10). Multi-line `run = """ ... """` block uses `set -e` discipline matching the verify:bom analog. PRODUCER_PORT indirection preserved. No other tasks touched. File parses as valid TOML.</done>
</task>

<task id="04-05-T2" type="auto">
  <name>Task 2: Update README.md — add `## Step 4: Metrics` section, move **Current.** marker, update What's NOT here yet (D-21)</name>
  <files>README.md</files>
  <read_first>
    - README.md (current state — Workshop checkpoints at lines 68-77; Reading the code at lines 79-88; Why is OtelSdkConfiguration.java duplicated at lines 90-92; Why is the propagation pair shared at lines 94-106; What's NOT here yet at lines 108-115)
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 568-611 — the D-21 target shape including suggested skeleton at lines 580-606 and the placement note "between Workshop checkpoints and Reading the code")
    - .planning/phases/04-metrics/04-CONTEXT.md (D-10 cardinality lesson, D-13 seconds-not-millis trap, D-21 README delta scope)
    - .planning/phases/03-amqp-context-propagation/03-05-readme-and-exit-gate-PLAN.md (Plan 03-05 — exact analog Edit-1/Edit-2/Edit-3 pattern; Phase 4 mirrors this structure with three precise edits)
  </read_first>
  <action>
    Modify `README.md` IN PLACE with three precise edits. Do NOT regenerate the entire file — Phase 1+2+3 README content is correct as written; Phase 4 only adds one new section, updates one bullet (Workshop checkpoints), and updates one bullet (What's NOT here yet).

    **Edit 1 — Update `## Workshop checkpoints` bullet list** (around lines 70-75 of the current README).

    Old (find this exact line):
    ```
    - `step-03-context-propagation` — THE headline lesson: AMQP context propagation joins the two traces; `consumer.parentSpanId == producer.spanId` after this checkpoint. **Current.**
    ```
    New (replace with — drop the **Current.** marker):
    ```
    - `step-03-context-propagation` — THE headline lesson: AMQP context propagation joins the two traces; `consumer.parentSpanId == producer.spanId` after this checkpoint.
    ```

    Old (find this exact line):
    ```
    - `step-04-metrics` — (Phase 4) `SdkMeterProvider` + Counter/Histogram/ObservableGauge.
    ```
    New (replace with — drop the "(Phase 4)" forward-pointer prefix; expand the description to cite the meter pipeline + the three instrument shapes; add the **Current.** marker):
    ```
    - `step-04-metrics` — `SdkMeterProvider` lands as a sibling pipeline next to the tracer pipeline; `orders.created` (Counter), `http.server.request.duration` (Histogram, seconds), `orders.queue.depth.estimate` (ObservableGauge) flow to Mimir on a 10-second interval. **Current.**
    ```

    Leave the other 4 bullets (step-01-baseline, step-02-traces, step-05-logs, step-06-tests) unchanged.

    **Edit 2 — Insert a new `## Step 4: Metrics` section AFTER `## Workshop checkpoints` and BEFORE `## Reading the code`.** This is the D-21 deliverable — the small Phase-4-specific callout. Use the EXACT markdown below verbatim:

    ```markdown
    ## Step 4: Metrics

    `step-04-metrics` adds the **second** OTel signal — metrics — to both services. The two
    `OtelSdkConfiguration.java` files now build a `SdkMeterProvider` next to the
    `SdkTracerProvider` (D-01 in `04-CONTEXT.md` extracted Phase 2's tracer pipeline
    into `buildTracerProvider(Resource)` and added a sibling `buildMeterProvider(Resource)`,
    so the diff against `step-03-context-propagation` reads as "we added a sibling
    pipeline next to the trace pipeline"). The producer adds a Counter and a Histogram
    to its existing instrumentation surfaces; the consumer adds an ObservableGauge.

    The three instrument shapes — one Counter, one Histogram, one ObservableGauge —
    cover the OTel SDK's three primary metric kinds:

    - **`orders.created`** (`LongCounter`, producer-side) — fires after each successful
      `POST /orders` from inside [`OrderService.place(...)`](./producer-service/src/main/java/com/example/producer/domain/OrderService.java)
      with the business attribute `order.priority` from the request payload (fallback `"standard"`).
      The counter does NOT fire on the failure path — failures are visible via the trace's
      ERROR status, not as a metric. `order.priority` is a string-literal `AttributeKey`
      because it is NOT in the OTel semconv catalog (contrast with the histogram's
      `HttpAttributes.HTTP_REQUEST_METHOD` which IS semconv).
    - **`http.server.request.duration`** (`DoubleHistogram`, producer-side) — recorded from
      inside the existing [`HttpServerSpanFilter`](./producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java)
      finally block, BEFORE `span.end()`. **Unit is seconds (`"s"`), not milliseconds.**
      The seconds-not-millis trap (D-13) is the textbook OTel-porting mistake — semconv 1.40.0
      specifies seconds, and Mimir's default `http_server_request_duration_seconds` dashboards
      assume seconds. Attributes follow HTTP semconv: `http.request.method` and
      `http.response.status_code` only — `url.path` is intentionally excluded because
      high-cardinality path values would explode the metric series count.
    - **`orders.queue.depth.estimate`** (`ObservableGauge`, consumer-side) — registered by
      [`QueueDepthGauge`](./consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java).
      Callback fires on every 10-second collection interval (METRIC-01 — overrides OTel's
      60-second default `PeriodicMetricReader`) and returns a synthetic
      `ThreadLocalRandom.current().nextInt(0, 50)` value. The lesson is the
      callback-on-interval mechanism, not the value semantics; a real implementation would
      poll the RabbitMQ Management API (out of scope for this workshop).

    `mise run demo:order` now sends two payloads — `priority=express` and `priority=standard`
    — so Mimir shows two series for `orders.created`. Try the Mimir query
    `orders_created_total{order_priority="express"}` to see one of them. **Note the name
    mangling:** the OTel-to-Prometheus exporter (running inside `otel-lgtm`'s collector)
    converts dots to underscores and appends `_total` for monotonic counters, so the
    OTel-side `orders.created` surfaces in Mimir as `orders_created_total` and
    `http.server.request.duration` (unit `s`) surfaces as `http_server_request_duration_seconds`.
    The same Resource attributes from Phase 2 (`service.name`, `service.namespace`,
    `service.instance.id`, `deployment.environment.name`) appear on every metric data
    point (D-05 — built once and shared between traces and metrics for cross-signal
    correlation in Grafana).
    ```

    **Edit 3 — Update `## What's NOT here yet`** (around lines 110-114 of current README).

    Old (find this exact line):
    ```
    - No metrics or log correlation (Phase 4 / Phase 5)
    ```
    New (replace with — Phase 4 just delivered the metrics half; logs is the remaining gap):
    ```
    - No log correlation (Phase 5)
    ```

    Leave all other "What's NOT here yet" bullets unchanged. The other bullets cover Phase 1 baseline omissions still relevant after Phase 4 — specifically: `No OtelSdkConfiguration.java (Phase 2)` (this bullet is now historically misleading since Phases 2/3/4 all delivered SDK code, but it's a Phase 1 self-reference that the README's intro paragraph contextualizes; do NOT edit it; Phase 7's DOC-01 is the full README rewrite that will harmonize all of this).

    Do NOT change anything else in the README. Specifically: leave the H1 / intro paragraph / Prerequisites table / IDE setup / one-time setup / first run / Reading the code / Why is OtelSdkConfiguration.java duplicated? / Why is the propagation pair shared? sections untouched. Phase 4's delta is intentionally small (D-21).

    Verify the edits:
    - The README still has its existing sections in the correct relative order: Prerequisites → Workshop checkpoints → Step 4: Metrics (NEW) → Reading the code → Why is OtelSdkConfiguration.java duplicated? → Why is the propagation pair shared? → What's NOT here yet.
    - The **Current.** marker has moved from `step-03-context-propagation` to `step-04-metrics`.
    - The Phase 4 forward-pointer "(Phase 4)" prefix is gone from the step-04-metrics bullet description.
    - The metrics bullet in "What's NOT here yet" is updated to mention only Phase 5 logs.
    - File parses as valid markdown — no broken code fences (count of triple-backtick fences is even).
  </action>
  <acceptance_criteria>
    - `test -f README.md` exits 0
    - Step 4 section title present: `grep -c '^## Step 4: Metrics$' README.md` returns 1
    - Step 4 section names all three instrument shapes: `for s in 'LongCounter' 'DoubleHistogram' 'ObservableGauge'; do grep -q "$s" README.md || exit 1; done`
    - Step 4 section names the three instrument names: `for n in 'orders\.created' 'http\.server\.request\.duration' 'orders\.queue\.depth\.estimate'; do grep -qE "$n" README.md || exit 1; done`
    - Step 4 section calls out seconds-not-millis trap (D-13): `grep -qE 'seconds-not-millis|seconds.*not milliseconds|Unit is seconds' README.md`
    - Step 4 section names the bounded-cardinality lesson (D-10 / D-14): `grep -qE 'cardinality|series count' README.md`
    - Step 4 section shows OTel→Prometheus name mapping: `grep -qE 'orders_created_total' README.md && grep -qE 'http_server_request_duration_seconds' README.md && grep -qE 'dots to underscores|name mangling' README.md`
    - Step 4 section links to all three Phase 4 source files: `for f in 'OrderService\.java' 'HttpServerSpanFilter\.java' 'QueueDepthGauge\.java'; do grep -qE "$f" README.md || exit 1; done`
    - Step 4 section mentions the 10-second PeriodicMetricReader interval (METRIC-01): `grep -qE '10-second|10 second' README.md && grep -qE 'PeriodicMetricReader' README.md`
    - **Current.** marker moved (D-22): `grep 'step-03-context-propagation' README.md | grep -c '\*\*Current\.\*\*'` returns 0; `grep 'step-04-metrics' README.md | grep -c '\*\*Current\.\*\*'` returns 1
    - Workshop checkpoint description for step-04-metrics expanded (no more "(Phase 4)" forward-pointer): `! grep -qE '\(Phase 4\) `SdkMeterProvider`' README.md && grep -qE 'step-04-metrics.*SdkMeterProvider' README.md`
    - "What's NOT here yet" updated — metrics removed, logs remains: `! grep -qE 'No metrics or log correlation \(Phase 4 / Phase 5\)' README.md && grep -qE 'No log correlation \(Phase 5\)' README.md`
    - Section order preserved (Workshop checkpoints < Step 4: Metrics < Reading the code < Why is OtelSdkConfiguration.java duplicated < Why is the propagation pair shared < What's NOT here yet): `awk '/^## Workshop checkpoints/{w=NR} /^## Step 4: Metrics/{s=NR} /^## Reading the code/{r=NR} /^## Why is OtelSdkConfiguration\.java duplicated/{d=NR} /^## Why is the propagation pair shared/{ps=NR} /^## What.s NOT here yet/{n=NR} END{exit (w<s && s<r && r<d && d<ps && ps<n)?0:1}' README.md`
    - Existing Phase 1+2+3 sections still present (regression check): `for s in 'Prerequisites' 'Required tools' 'Required free ports' 'IDE setup' 'One-time setup' 'First run' 'Workshop checkpoints' 'Reading the code' 'OtelSdkConfiguration.java duplicated' 'propagation pair shared' "What.s NOT here yet"; do grep -q "$s" README.md || exit 1; done`
    - All 6 step-tag names still listed in Workshop checkpoints (regression): `for t in step-01-baseline step-02-traces step-03-context-propagation step-04-metrics step-05-logs step-06-tests; do grep -q "$t" README.md || exit 1; done`
    - File is well-formed markdown — code fences balanced: `awk '/^```/{c++} END{exit (c%2==0)?0:1}' README.md`
  </acceptance_criteria>
  <verify>
    <automated>grep -q '^## Step 4: Metrics$' README.md && grep 'step-04-metrics' README.md | grep -q '\*\*Current\.\*\*' && ! grep 'step-03-context-propagation' README.md | grep -q '\*\*Current\.\*\*' && grep -qE 'orders_created_total' README.md && grep -qE 'http_server_request_duration_seconds' README.md && grep -qE '10-second|10 second' README.md && grep -qE 'No log correlation \(Phase 5\)' README.md && ! grep -qE 'No metrics or log correlation' README.md && awk '/^```/{c++} END{exit (c%2==0)?0:1}' README.md</automated>
  </verify>
  <done>README.md gains the D-21 `## Step 4: Metrics` section in the right order (after Workshop checkpoints, before Reading the code) — names all three instrument shapes, links to the three Phase 4 source files, calls out the seconds-not-millis trap (D-13) and the bounded-cardinality lesson (D-10 / D-14), shows the OTel→Prometheus name mapping. The **Current.** marker moves from step-03 to step-04. The "What's NOT here yet" metrics+logs combined bullet is split — metrics removed, logs remains. Existing Phase 1+2+3 sections unchanged.</done>
</task>

<task id="04-05-T3" type="auto">
  <name>Task 3: Verify all 4 Phase 4 success criteria simultaneously green at the live stack (gates the tag in T4)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/ROADMAP.md (lines 120-124 — Phase 4 success criteria — all 4 must be simultaneously green for the tag to be applied)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-22 exit-gate convention; D-03 10-second PeriodicMetricReader)
    - mise.toml (verify:bom, dev:producer, dev:consumer, demo:order, infra:up, infra:down — task names referenced below)
    - All Phase 4 SUMMARYs (04-01 through 04-04 from prior plans)
    - README.md (just modified in T2 — Step 4: Metrics section + step-04-metrics marked Current)
    - Tag refs/tags/step-03-context-propagation (the diff baseline)
  </read_first>
  <action>
    Run all four Phase 4 success criteria back-to-back on a clean working tree. T4 (the tag) may ONLY run if EVERY criterion is green AND `git status --porcelain` is empty.

    **Setup:** Bring infrastructure up and start both services in the background.
    ```sh
    mise run infra:up
    nohup mise run dev:producer > /tmp/producer-04-05.log 2>&1 &
    PID_P=$!
    nohup mise run dev:consumer > /tmp/consumer-04-05.log 2>&1 &
    PID_C=$!
    for i in $(seq 1 60); do
      grep -q "Started ProducerApplication" /tmp/producer-04-05.log 2>/dev/null && \
      grep -q "Started ConsumerApplication" /tmp/consumer-04-05.log 2>/dev/null && break
      sleep 2
    done
    test "$(curl -s http://localhost:8080/actuator/health | python3 -c 'import json,sys; print(json.load(sys.stdin)[\"status\"])')" = "UP"
    test "$(curl -s http://localhost:8081/actuator/health | python3 -c 'import json,sys; print(json.load(sys.stdin)[\"status\"])')" = "UP"
    ```

    **Criterion 1 — orders_created_total increments within 15s with order_priority="express" label visible in Mimir:**

    Issue the updated mise demo:order task (which sends BOTH priorities), wait up to 15s for the PeriodicMetricReader 10s interval, and query Mimir for `orders_created_total`.
    ```sh
    mise run demo:order   # sends priority=express AND priority=standard
    # Wait up to 15s for the 10-second PeriodicMetricReader to flush a scrape
    for i in $(seq 1 8); do
      RESP=$(curl -s "http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=orders_created_total")
      if echo "$RESP" | grep -q 'order_priority'; then
        echo "$RESP" | python3 -c "
    import json, sys
    r = json.load(sys.stdin)
    res = r['data']['result']
    assert res, 'orders_created_total not present in Mimir yet'
    priorities = sorted({m['metric'].get('order_priority', '') for m in res})
    print(f'OK: orders_created_total series with order_priority labels: {priorities}')
    assert 'express' in priorities, f'express label missing; got {priorities}'
    assert 'standard' in priorities, f'standard label missing; got {priorities}'
    services = sorted({m['metric'].get('service_name', '') for m in res})
    assert 'order-producer' in services, f'service_name=order-producer missing; got {services}'
    "
        break
      fi
      sleep 2
    done
    ```
    PASS criterion: `orders_created_total` series visible in Mimir within ~15s of POST; labels include both `order_priority="express"` and `order_priority="standard"`; `service_name="order-producer"` label is present and non-empty.

    Note: the Mimir HTTP API path on otel-lgtm's bundled Grafana proxy is `http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=...`. The exact datasource UID may be `prometheus` or `mimir-prometheus` depending on otel-lgtm's bundled config — if the first proxy URL returns 404, list datasources via `curl -s -u admin:admin http://localhost:3000/api/datasources | python3 -m json.tool` and use the actual UID.

    **Criterion 2 — http_server_request_duration_seconds populated with count, sum, and bucket histograms after ~30s of traffic:**

    Generate ~30s of traffic with a small loop, then query Mimir for the bucket histogram.
    ```sh
    for i in $(seq 1 20); do
      curl -s -o /dev/null -X POST http://localhost:8080/orders \
        -H 'Content-Type: application/json' \
        -d "{\"sku\":\"SKU-$i\",\"quantity\":1,\"priority\":\"express\"}"
      sleep 1
    done
    sleep 12   # one more PeriodicMetricReader interval
    # Query the histogram's _count and _sum series
    COUNT_RESP=$(curl -s "http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=http_server_request_duration_seconds_count")
    SUM_RESP=$(curl -s "http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=http_server_request_duration_seconds_sum")
    BUCKET_RESP=$(curl -s "http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=http_server_request_duration_seconds_bucket")
    python3 -c "
    import json
    c = json.loads('''$COUNT_RESP''')
    s = json.loads('''$SUM_RESP''')
    b = json.loads('''$BUCKET_RESP''')
    assert c['data']['result'], 'http_server_request_duration_seconds_count missing'
    assert s['data']['result'], 'http_server_request_duration_seconds_sum missing'
    assert b['data']['result'], 'http_server_request_duration_seconds_bucket missing'
    # Validate the bucket series carries http_request_method and http_response_status_code labels (D-14)
    bucket_labels = b['data']['result'][0]['metric']
    assert 'http_request_method' in bucket_labels, f'http_request_method label missing; got {bucket_labels.keys()}'
    assert 'http_response_status_code' in bucket_labels, f'http_response_status_code label missing; got {bucket_labels.keys()}'
    print(f'OK: histogram has count + sum + bucket; labels include http_request_method={bucket_labels[\"http_request_method\"]} status={bucket_labels[\"http_response_status_code\"]}')
    "
    ```
    PASS criterion: all three series (`_count`, `_sum`, `_bucket`) present; `_bucket` carries `http_request_method` AND `http_response_status_code` labels (proves D-14 semconv constants reached the wire); proves the histogram is in seconds (the unit suffix `_seconds` on the metric name comes from the OTel exporter using the SDK's unit `"s"` per D-13).

    **Criterion 3 — orders_queue_depth_estimate produces a fresh sample every 10 seconds (proves PeriodicMetricReader interval override per METRIC-01):**

    Sample the gauge twice with a 12-second gap and confirm the timestamps differ by ~10s and the values are within range [0, 50).
    ```sh
    R1=$(curl -s "http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=orders_queue_depth_estimate")
    sleep 12
    R2=$(curl -s "http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query?query=orders_queue_depth_estimate")
    python3 -c "
    import json
    r1 = json.loads('''$R1''')
    r2 = json.loads('''$R2''')
    assert r1['data']['result'], 'orders_queue_depth_estimate not present'
    assert r2['data']['result'], 'orders_queue_depth_estimate not present after 12s'
    t1 = float(r1['data']['result'][0]['value'][0])
    t2 = float(r2['data']['result'][0]['value'][0])
    v1 = float(r1['data']['result'][0]['value'][1])
    v2 = float(r2['data']['result'][0]['value'][1])
    delta = t2 - t1
    print(f'OK: gauge samples at t1={t1} t2={t2} delta={delta:.1f}s; values v1={v1} v2={v2}')
    # PeriodicMetricReader at 10s; allow 8-15s tolerance for scrape timing jitter
    assert 8 <= delta <= 15, f'sample delta {delta:.1f}s outside expected 8-15s window (proves D-03 10s interval, not 60s default)'
    assert 0 <= v1 < 50 and 0 <= v2 < 50, f'values out of [0,50) range: {v1} {v2}'
    # Verify service_name=order-consumer label
    sn1 = r1['data']['result'][0]['metric'].get('service_name', '')
    assert sn1 == 'order-consumer', f'service_name expected order-consumer; got {sn1}'
    print(f'OK: orders_queue_depth_estimate fresh every ~10s; service_name=order-consumer; values in [0, 50)')
    "
    ```
    PASS criterion: gauge sample timestamps differ by 8-15 seconds (proves the 10s PeriodicMetricReader interval — METRIC-01 / D-03 — overriding OTel's 60s default); values are integers in [0, 50); service_name label is `order-consumer`.

    **Criterion 4 — Phase 2 invariant + clean working tree (preconditions for tagging):**
    ```sh
    mise run verify:bom 2>&1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'
    test -z "$(git status --porcelain)" || { git status --porcelain; echo "ABORT: uncommitted changes — commit them before running T4"; exit 1; }
    ```

    **Final cleanup:** Stop both apps cleanly so T4 inherits a clean process state.
    ```sh
    kill $PID_P $PID_C 2>/dev/null
    for i in $(seq 1 12); do
      kill -0 $PID_P 2>/dev/null && continue
      kill -0 $PID_C 2>/dev/null && continue
      break
    done
    ! pgrep -f spring-boot:run
    ```

    Failure modes — if ANY criterion fails, T4 (the tag) MUST NOT run. Document which criterion failed in the SUMMARY and STOP. Common failures:
    - Criterion 1 fails (orders_created_total missing): Plan 04-02's counter line not reached — check OrderService.place is invoked AND the counter is built in the constructor (Meter @Bean from Plan 04-01 must be visible to Spring DI). If the Counter exists but no labels: check D-09 attribute key shape; if labels are 'standard' only: Plan 04-05's mise demo:order task didn't update (verify T1).
    - Criterion 2 fails (histogram missing or wrong labels): Plan 04-03's record line in the finally not reached, or the histogram unit is wrong (millis would still produce a series but with the wrong _seconds suffix), or D-14 attribute keys are string literals not semconv constants.
    - Criterion 3 fails (gauge missing): Plan 04-04's QueueDepthGauge @Component not picked up by Spring scan, OR PeriodicMetricReader interval not 10s (delta would be 60s+ if Plan 04-01 didn't override the default).
    - Criterion 4 fails (verify:bom): unexpected new io.opentelemetry artifact pulled in (shouldn't happen — Phase 4 adds zero deps).
    - Clean tree fails: uncommitted changes; commit them with a chore message and re-run T3.
  </action>
  <acceptance_criteria>
    - Criterion 1 PASS: orders_created_total series in Mimir within ~15s of POST; both order_priority='express' AND order_priority='standard' labels present; service_name='order-producer' present
    - Criterion 2 PASS: http_server_request_duration_seconds_count, _sum, AND _bucket all populated; bucket carries http_request_method AND http_response_status_code labels; series name has _seconds suffix (proves D-13 unit "s" reached the wire)
    - Criterion 3 PASS: two consecutive orders_queue_depth_estimate samples taken 12s apart show timestamp delta in [8, 15] seconds (proves the 10s PeriodicMetricReader override); values are integers in [0, 50); service_name='order-consumer'
    - Criterion 4 (Phase 2 invariant) PASS: `mise run verify:bom` exits 0
    - Criterion 4 (clean tree) PASS: `test -z "$(git status --porcelain)"` exits 0
    - All background processes cleaned up: `! pgrep -f spring-boot:run` exits 0
    - producer trace from Phase 2/3 still works (no regression): a POST /orders still produces a SERVER+INTERNAL+PRODUCER span trace + a CONSUMER+INTERNAL trace joined via traceparent (smoke check via Tempo /api/search)
  </acceptance_criteria>
  <verify>
    <automated>mise run verify:bom 2>&1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact' && test -z "$(git status --porcelain)" && ! pgrep -f spring-boot:run && grep -q '^## Step 4: Metrics$' README.md</automated>
  </verify>
  <done>All 4 Phase 4 success criteria verified simultaneously green at the live stack: orders_created_total in Mimir within 15s with both order_priority labels (SC #1); http_server_request_duration_seconds populated with count + sum + bucket carrying HTTP method + status code labels (SC #2); orders_queue_depth_estimate fresh every ~10s with values in [0, 50) (SC #3); annotated tag pending T4. Phase 2 verify:bom invariant green. Working tree clean. All background processes cleaned up. T4 may proceed.</done>
</task>

<task id="04-05-T4" type="checkpoint:human-verify" gate="blocking">
  <name>Task 4: Human-verify Phase 4 metrics state + create annotated git tag step-04-metrics</name>
  <what-built>
    - Wave 1 (Plan 04-01): Both `OtelSdkConfiguration.java` files refactored — `openTelemetry()` @Bean orchestrator delegates to `buildTracerProvider(Resource)` (verbatim Phase 2 lift-and-shift) and a new sibling `buildMeterProvider(Resource)` that wires `OtlpGrpcMetricExporter` + `PeriodicMetricReader.setInterval(Duration.ofSeconds(10))` + `SdkMeterProvider`. New `Meter` @Bean in each service (scope `com.example.producer` / `com.example.consumer`). Producer's `HttpServerSpanFilter` @Bean factory updated to take `(Tracer, Meter)`. Per-service mirror property preserved (D-02). No new pom dependencies (opentelemetry-exporter-otlp + opentelemetry-sdk-metrics already on classpath since Phase 2).
    - Wave 2 (Plan 04-02): `OrderService` constructor takes `(OrderPublisher, Tracer, Meter)`; `LongCounter ordersCreated` built once in constructor with name `orders.created`, unit `"1"`; counter increments INSIDE the existing INTERNAL span body, AFTER `publisher.publish(...)` returns and BEFORE `return orderId`, with the business attribute `order.priority` from the request payload (fallback `"standard"`); the catch block is unchanged — counter does NOT fire on the failure path (METRIC-02 / D-08 / D-09).
    - Wave 2 (Plan 04-03): `HttpServerSpanFilter` constructor takes `(Tracer, Meter)`; `DoubleHistogram requestDuration` built once in constructor with name `http.server.request.duration`, unit `"s"` (seconds — D-13 semconv-aligned); `startNanos = System.nanoTime()` captured BEFORE the spanBuilder call; record line in the existing finally block, BEFORE `span.end()`, using `(System.nanoTime() - startNanos) / 1_000_000_000.0` (seconds) and semconv constants `HttpAttributes.HTTP_REQUEST_METHOD` + `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` for attributes (METRIC-03 / D-12 / D-13 / D-14). `shouldNotFilter("/actuator/*")` unchanged — single predicate covers both span and histogram. SDK-default buckets (D-15).
    - Wave 2 (Plan 04-04): NEW @Component `QueueDepthGauge` at `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` — constructor-injects Meter, builds `ObservableLongGauge` named `orders.queue.depth.estimate` with `.ofLongs()` (D-19) and `buildWithCallback(measurement -> measurement.record(ThreadLocalRandom.current().nextInt(0, 50)))` (D-17 — synthetic value, METRIC-04). @PreDestroy closes the gauge defensively. D-18b adopted (separate @Component, NOT inline @PostConstruct in OtelSdkConfiguration) — preserves the producer/consumer mirror property (D-02).
    - Wave 3 (this plan): `mise.toml`'s `demo:order` task sends two payloads (`priority=express` + `priority=standard`) per D-10. README.md gains a `## Step 4: Metrics` section keyed to tag `step-04-metrics` (D-21) — names the three instrument shapes, links to the three Phase 4 source files, calls out the seconds-not-millis trap (D-13) and the bounded-cardinality lesson (D-10 / D-14), shows the OTel→Prometheus name mapping. The **Current.** marker moves from `step-03-context-propagation` to `step-04-metrics`. The "What's NOT here yet" combined metrics+logs bullet is split — metrics removed, logs remains. T3 already verified all 4 ROADMAP success criteria simultaneously green.
    - Pending in this task: human verification of the workshop-attendee experience (Mimir Explore shows orders_created_total with two order_priority labels; http_server_request_duration_seconds histogram with count+sum+buckets after ~30s; orders_queue_depth_estimate ticks every 10s; README reads cleanly for the Step 4 delta) plus creation of the annotated git tag `step-04-metrics`. This is the FOURTH of the six WORK-01 tags.
  </what-built>
  <how-to-verify>
    Spend ~10-15 minutes confirming the workshop-attendee experience is clean and the three instrument shapes are visible in Mimir before the tag becomes immutable.

    **Step 1 — Open the README in a markdown viewer** (GitHub preview, IntelliJ, VS Code preview, or `glow README.md`). Confirm:
    1. The new `## Step 4: Metrics` section is present in the right place (after `## Workshop checkpoints`, before `## Reading the code`).
    2. The Step 4 section names all three instrument shapes (Counter / Histogram / ObservableGauge), links to all three Phase 4 source files (`OrderService.java`, `HttpServerSpanFilter.java`, `QueueDepthGauge.java`), and explicitly calls out:
       - The seconds-not-millis trap (D-13) — on the histogram bullet.
       - The bounded-cardinality lesson (D-10 / D-14) — `url.path` excluded from the histogram, `order.priority` is the only Counter attribute.
       - The OTel-to-Prometheus name mangling (dots → underscores; `_total` for monotonic counters).
       - The 10-second `PeriodicMetricReader` interval (METRIC-01 — overrides OTel's 60s default).
    3. The `## Workshop checkpoints` list shows `step-04-metrics` as **Current** (not `step-03-context-propagation`).
    4. The `## What's NOT here yet` section says "No log correlation (Phase 5)" — the metrics half has been delivered.
    5. No broken markdown — code fences balanced, all the file links resolve when clicked from the markdown viewer.

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

    **Step 3 — Issue the updated demo:order task and open Grafana → Explore → datasource Mimir/Prometheus** (`http://localhost:3000` → admin/admin → Explore → select the Prometheus/Mimir datasource):
    ```sh
    mise run demo:order   # sends BOTH priority=express AND priority=standard payloads (D-10)
    sleep 12              # one PeriodicMetricReader 10s interval + buffer
    ```
    1. Run the Mimir query: `orders_created_total`. You should see TWO time series, distinguished by `order_priority="express"` and `order_priority="standard"` labels. Both should also carry `service_name="order-producer"`. **This is the cardinality-awareness lesson made visible** — without sending two priority values, Mimir would only show one series and the lesson would be invisible (D-10). ROADMAP SC #1 satisfied.
    2. Run the Mimir query: `http_server_request_duration_seconds_count`. Should show a non-zero count for `http_request_method="POST"` and `http_response_status_code="202"`. Try `http_server_request_duration_seconds_sum` — non-zero seconds value. Try `histogram_quantile(0.95, sum(rate(http_server_request_duration_seconds_bucket[1m])) by (le))` — should give a real p95 latency value. ROADMAP SC #2 satisfied.

    **Step 4 — Confirm the histogram unit is seconds, not milliseconds (D-13):**
    Look at the `_sum` value above. For an HTTP POST that returns 202 in ~50-200ms, the `_sum` per-request value should be around `0.05 - 0.5` (seconds). If you see values in the `50 - 500` range, the unit is millis (regression). If you see values in the `50000 - 500000` range, the unit is microseconds (also regression). The Mimir query inspector will also display the metric name with the `_seconds` suffix — confirm.

    **Step 5 — Generate ~30s of traffic and confirm bucket histograms populate:**
    ```sh
    for i in $(seq 1 25); do
      curl -s -o /dev/null -X POST http://localhost:8080/orders \
        -H 'Content-Type: application/json' \
        -d "{\"sku\":\"SKU-$i\",\"quantity\":1,\"priority\":\"express\"}"
      sleep 1
    done
    sleep 12
    ```
    1. Run the Mimir query: `http_server_request_duration_seconds_bucket{le="+Inf"}`. Should show a count matching the total POSTs.
    2. Run a query inspecting different bucket boundaries: `http_server_request_duration_seconds_bucket{le="0.1"}`, `http_server_request_duration_seconds_bucket{le="0.5"}`. The counts should differ — early buckets contain only the fastest requests; the `+Inf` bucket contains ALL requests.
    3. ROADMAP SC #2 satisfied: histogram is properly populated with count + sum + bucket histograms, in seconds.

    **Step 6 — Confirm the gauge ticks every ~10 seconds (METRIC-01 / D-03):**
    1. Run the Mimir query: `orders_queue_depth_estimate`. Should return a single integer value in [0, 50) with `service_name="order-consumer"`.
    2. Wait 12 seconds. Re-run the query. The value should change (random ThreadLocalRandom each callback) AND the timestamp should be ~10 seconds later than the previous one. **This is the load-bearing proof of the PeriodicMetricReader interval override** — if the interval were the OTel 60s default, you would see the SAME value for ~60 seconds at a stretch.
    3. ROADMAP SC #3 satisfied: gauge produces a fresh sample every 10s.

    **Step 7 — Cross-signal correlation in Grafana (bonus / pedagogical confirmation):**
    Click on a metric data point in Mimir's Explore panel; Grafana should let you "View Trace" by `service_name` + `service_instance_id` (the Resource attributes from D-05 — built once and shared between traces and metrics). If the correlation links light up, the shared-Resource design is working as intended. (This is not a hard ROADMAP criterion but it's the workshop's pedagogical payoff.)

    **Step 8 — Read the three Phase 4 source files side-by-side:**
    Open `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` and `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` side-by-side. Confirm:
    1. Both files have the orchestrator + buildTracerProvider + buildMeterProvider helper triple (D-01).
    2. The diff between the two files is small — service.name, Tracer scope, Meter scope, and the producer-only HttpServerSpanFilter @Bean. No other divergence (D-02 / DOC-05 mirror property preserved).
    3. The `buildMeterProvider` helper has a banner-comment style matching `buildTracerProvider` (D-20 comment density).
    4. `OpenTelemetrySdk.builder()` has both `.setTracerProvider(...)` and `.setMeterProvider(...)` lines.

    Then open `producer-service/.../OrderService.java` and confirm the counter increment lives BETWEEN `publisher.publish(...)` and `return orderId` — adjacent to the existing INTERNAL span body. Then `producer-service/.../HttpServerSpanFilter.java` and confirm `startNanos = System.nanoTime()` is captured BEFORE the span is built and `requestDuration.record(seconds, attrs)` lives in the existing finally before `span.end()`. Then `consumer-service/.../observability/QueueDepthGauge.java` and confirm the @Component constructor-injects Meter and registers the callback in one place.

    **Step 9 — Approve or describe issues.**

    If approved, proceed to Step 10 (the tag). If issues exist, list them with file references; do NOT tag.

    **Step 10 — Create the annotated git tag (executor performs after approval):**

    First, ensure git working tree is still clean (T3 confirmed this; double-check):
    ```sh
    git status --porcelain
    ```
    Expected: empty output. If anything is staged or untracked beyond .gitignored items, commit them with a chore message:
    ```sh
    git add <files>
    git commit -m "chore(04): phase-4 wrap-up"
    ```

    Then create the annotated tag (`-a` is mandatory — lightweight tags are not sufficient for WORK-01):
    ```sh
    git tag -a step-04-metrics -m "Workshop checkpoint: Phase 4 — Metrics. SdkMeterProvider added to both services as a sibling pipeline next to the SdkTracerProvider; three OTel instrument shapes flow to Mimir.

    Phase 4 extends each service's OtelSdkConfiguration.java by extracting Phase 2's inline tracer pipeline into private SdkTracerProvider buildTracerProvider(Resource) (verbatim lift-and-shift) and adding a sibling private SdkMeterProvider buildMeterProvider(Resource) that wires OtlpGrpcMetricExporter + PeriodicMetricReader.setInterval(Duration.ofSeconds(10)) (METRIC-01 — overrides OTel's 60s default). The opentelemetry-exporter-otlp artifact already on the classpath since Phase 2 ships span + metric + log exporters from a single jar — Phase 4 adds zero new pom dependencies. Resource is built once in the @Bean orchestrator and passed to both helpers, so traces and metrics share identical service.name / service.instance.id for cross-signal correlation in Grafana.

    Three instrument shapes (METRIC-02..04):
      - orders.created (LongCounter, producer-side) — fires after each successful publish, INSIDE the existing INTERNAL span scope, with order.priority business attribute from the request payload (fallback 'standard'). Counter does NOT fire on the failure path (counter is orders.created, not orders.attempted; failure is visible via the trace's ERROR status).
      - http.server.request.duration (DoubleHistogram, producer-side) — recorded from inside the existing HttpServerSpanFilter finally block, in SECONDS (semconv 1.40.0; the seconds-not-millis trap is one of the most common OTel-porting mistakes). Attributes follow HTTP semconv: HTTP_REQUEST_METHOD + HTTP_RESPONSE_STATUS_CODE only — url.path intentionally excluded to keep cardinality bounded. SDK-default explicit bucket aggregation.
      - orders.queue.depth.estimate (ObservableLongGauge, consumer-side) — registered in a small @Component QueueDepthGauge that constructor-injects Meter; callback returns ThreadLocalRandom.current().nextInt(0, 50) on every 10s collection interval. Synthetic by spec (METRIC-04); a real implementation would poll the RabbitMQ Management API.

    All four Phase 4 success criteria simultaneously green at this commit:
      1. orders_created_total visible in Mimir within 15s of POST /orders with both order_priority='express' and order_priority='standard' labels (mise demo:order alternates the two values per D-10).
      2. http_server_request_duration_seconds populated with count + sum + bucket histograms after ~30s of traffic; bucket carries http_request_method + http_response_status_code labels.
      3. orders_queue_depth_estimate produces a fresh sample every 10s (proves PeriodicMetricReader interval override per METRIC-01 — without this, the gauge would only refresh every 60s).
      4. step-04-metrics annotated tag exists on main; reproduces the green-Phase-4 state in a temp clone."
    ```

    Verify the tag:
    ```sh
    git tag --list step-04-metrics
    git for-each-ref --format='%(objecttype) %(refname)' refs/tags/step-04-metrics
    git show step-04-metrics | head -25
    ```

    The first command outputs `step-04-metrics`. The second outputs `tag refs/tags/step-04-metrics` (NOT `commit` — proves annotated, not lightweight). The third shows the full tag message including the 4 success criteria.

    DO NOT push the tag automatically. The user (`coto@petabyte.cl`) decides when to push. Mention in the SUMMARY that `git push origin step-04-metrics` is the follow-up the user runs when ready (or `git push --tags`). Tag is local-only until explicit push — same git-safety convention as Phase 1, 2, and 3.

    **Step 11 — Reproducibility self-test:**
    From a temp clone:
    ```sh
    git -C /tmp clone --branch step-04-metrics --depth 1 file://$(pwd) verify-step-04 2>/dev/null
    cd /tmp/verify-step-04
    mise install
    mise run verify:bom    # MUST exit 0
    mvn -pl producer-service,consumer-service -am compile    # MUST exit 0
    cd -
    rm -rf /tmp/verify-step-04
    ```

    `git checkout step-04-metrics` (or temp clone) reproduces the green-Phase-4 state.

    **Step 12 — Quantify the diff size:**
    ```sh
    git diff --shortstat step-03-context-propagation..step-04-metrics
    git diff --name-status step-03-context-propagation..step-04-metrics
    ```
    Expected: ~6-8 files changed (2 OtelSdkConfiguration.java + OrderService.java + HttpServerSpanFilter.java + QueueDepthGauge.java [new] + mise.toml + README.md = 6 modified, 1 new). Insertions roughly balanced; the comment-density bar (D-20) means added line counts are heavier than minimum-viable.
  </how-to-verify>
  <resume-signal>Type "approved" to proceed with creating the annotated tag, "not yet — issues:" with a list of issues to fix, or "skip-tag" to defer tagging until later (which leaves Phase 4 incomplete — WORK-01 unsatisfied for the fourth checkpoint).</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 04-05 — README + tag + verification gate)

| Boundary | Description |
|----------|-------------|
| Local repo → remote (e.g., GitHub) | Tag pushed via `git push origin step-04-metrics` — workshop artifact will be public/internally-published |
| Workshop attendee reading README → external links | The new Step 4 section adds intra-repo links to .java files (no new external URLs) |
| HTTP smoke from T3 → producer service `:8080` | Already present from Phase 1; T3's smoke uses `mise demo:order` which runs locally |
| Mimir HTTP query → otel-lgtm `:3000` | Already present from Phase 2's Tempo queries; T3 reuses the same Grafana proxy with the prometheus datasource UID |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-05-01 | Tampering | Lightweight tag substituted for annotated tag, allowing silent re-pointing | mitigate | T4's verification step asserts via `git for-each-ref` that the tag's objecttype is `tag`, not `commit` — same convention as Phase 1's step-01-baseline / Phase 2's step-02-traces / Phase 3's step-03-context-propagation. |
| T-04-05-02 | Information Disclosure | README.md leaks workshop infrastructure details | accept | README is intended public/internal-team-shared; no secrets; no internal hosts; only documented workshop defaults (admin/admin Grafana, guest/guest RabbitMQ — both Phase 1 baseline). Phase 4 adds Mimir query examples — the queries are intrinsic to the workshop pedagogy, not a leak. |
| T-04-05-03 | Tampering | A future PR force-pushes step-04-metrics to a different commit | mitigate | Tags are immutable under normal git workflow; force-push would be needed to overwrite. Convention documented in PROJECT.md. The reproducibility self-test (Step 11) is the regression net. |
| T-04-05-04 | Repudiation | Tag created without a meaningful message; cannot trace what state it represents | mitigate | T4 requires the annotated message to mention "Phase 4", "Metrics", "SdkMeterProvider", "three instrument shapes", and the 4 success criteria — content auditable. |
| T-04-05-05 | Denial of Service (workshop-only) | The README's Mimir query examples include `histogram_quantile(0.95, sum(rate(...)) by (le))` — a malformed copy-paste could OOM Grafana on a tiny laptop | accept | Workshop scope; otel-lgtm running on a developer laptop has bounded memory and the queries are bounded by `[1m]` rate windows; Grafana's query timeout would kill a runaway query. The README's queries are battle-tested against a real laptop-class otel-lgtm during T4's manual verification. |
| T-04-05-06 | Information Disclosure (cardinality consequence) | The Phase 4 `order.priority` attribute is user-controlled — a malicious workshop attendee could spam unique values to blow up Mimir's series count, OOMing the otel-lgtm container | mitigate (pedagogy) | Workshop demo runs on the attendee's own laptop; the only attacker is the attendee themselves, and the README's Step 4 callout (D-10) explicitly names the cardinality-awareness lesson so attendees know the tradeoff. Production mitigation (a downstream allowlist that normalizes unknown priority values to "other") is mentioned in the README delta. Severity: medium for production mitigation guidance; low for workshop runtime risk. |

**Phase scope:** Documentation delta + tag + runtime verification. No new runtime threat surface introduced beyond what Plans 04-01..04 created.
</threat_model>

<verification>
- mise.toml `demo:order` task sends two priority payloads (express + standard).
- README.md gains the `## Step 4: Metrics` section in correct order; **Current.** marker on step-04-metrics; "What's NOT here yet" updated.
- All 4 Phase 4 ROADMAP success criteria simultaneously green at the live stack (T3).
- After T4 approval: annotated tag step-04-metrics exists; tag-message contains "Phase 4", "Metrics", "SdkMeterProvider", three instrument shapes, the 4 success criteria.
- `git for-each-ref` confirms annotated, not lightweight.
- `git checkout step-04-metrics` reproduces the green-Phase-4 state in a temp clone (mise verify:bom + mvn compile both green).
- Phase 2 invariant preserved: `mise run verify:bom` exits 0 throughout.
- Diff size: ~6-8 files changed (2 OtelSdkConfiguration.java + OrderService + HttpServerSpanFilter + QueueDepthGauge [new] + mise.toml + README.md).
</verification>

<success_criteria>
- METRIC-01..04 all verified live (orders_created_total + http_server_request_duration_seconds + orders_queue_depth_estimate visible in Mimir within the SC time windows).
- D-10 (two demo payloads), D-21 (small README delta), D-22 (annotated tag exit gate) honored.
- WORK-01 (Phase 4 portion) satisfied: annotated git tag step-04-metrics exists on the working branch; the 6-tag convention (step-01 through step-06) is one tag closer to complete (4/6 tags now applied: step-01-baseline, step-02-traces, step-03-context-propagation, step-04-metrics).
- All 4 ROADMAP Phase 4 success criteria simultaneously green at the moment of tagging.
- The tag is local-only after T4 (user pushes when ready) — respects git-safety protocol.
- Phase 4 is now SHIPPED. The repo carries the meter pipeline + three instrument shapes; Phase 5 (logs) inherits all the Phase 4 wiring (it adds buildLoggerProvider as a third sibling helper to buildTracerProvider and buildMeterProvider).
</success_criteria>

<output>
After completion, create `.planning/phases/04-metrics/04-05-SUMMARY.md` documenting:
- Final shape of mise.toml `demo:order` block (paste).
- README.md final structure (paste H1 + section titles list — should show Workshop checkpoints → Step 4: Metrics → Reading the code → Why is OtelSdkConfiguration.java duplicated → Why is the propagation pair shared → What's NOT here yet).
- Confirmed Phase 4 success-criteria results from T3 (paste the green outputs of each criterion — the Mimir query JSON for orders_created_total, the histogram count/sum/bucket query results, the gauge sample timestamps + values + delta, the diff size).
- Confirmed annotated tag exists: paste `git for-each-ref --format='%(objecttype) %(objectname:short) %(refname)' refs/tags/step-04-metrics`.
- Confirmed tag message: paste `git show step-04-metrics | head -30`.
- A note that the user should run `git push origin step-04-metrics` when ready to publish (NOT done by this plan — same git-safety convention as Phases 1/2/3).
- Files modified: 2 (mise.toml + README.md) + 1 git ref (refs/tags/step-04-metrics).
- Confirmed reproducibility self-test (paste the temp-clone verify:bom + mvn compile output).
- The exact git diff stat: paste `git diff --shortstat step-03-context-propagation..step-04-metrics` and `git diff --name-status step-03-context-propagation..step-04-metrics`.

Then create `.planning/phases/04-metrics/PHASE-SUMMARY.md` rolling up all five plan summaries — phase exit summary documenting:
- Total artifacts created across the phase: 1 new file (QueueDepthGauge.java) + 5 modified files (2 OtelSdkConfiguration.java + OrderService.java + HttpServerSpanFilter.java + mise.toml + README.md) + 1 git ref. Zero new pom dependencies.
- The single gate sentence: "POST /orders produces orders_created_total + http_server_request_duration_seconds histogram in Mimir within ~30s; orders_queue_depth_estimate ticks every 10s; ALL THREE OTel instrument shapes (Counter / Histogram / ObservableGauge) flow through the same OTLP endpoint as Phase 2/3 traces" — confirmed.
- Tag created: step-04-metrics.
- Lessons learned: any deviations from RESEARCH.md (Phase 4 had no RESEARCH.md — paste-able from STACK.md), surprising failure modes, anything to feed back to retrospective.
- Phase 5 readiness signal: `buildLoggerProvider(Resource)` lands as a third sibling helper to `buildTracerProvider(Resource)` and `buildMeterProvider(Resource)` — the @Bean orchestrator stays a 3-step recipe. The Logback appender install pattern (LOG-03) lives outside this plan; the SDK pipeline triple is what Phase 5 plugs into.
- Phase 6 readiness signal: Testcontainers @SpringBootTest can swap OtlpGrpcMetricExporter for InMemoryMetricReader and assert the three instrument shapes exist + the right attribute keys are present + counter increments by 1 per publish.
</output>
</content>
</invoke>