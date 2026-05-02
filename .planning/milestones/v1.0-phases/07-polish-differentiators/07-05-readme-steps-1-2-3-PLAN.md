---
phase: 07-polish-differentiators
plan: 05
type: execute
wave: 4
depends_on: [07-01, 07-02, 07-03, 07-04]
files_modified:
  - README.md
autonomous: true
requirements: [DOC-01, DOC-04]
risk: medium
tags: [readme, walkthrough, doc-01, doc-04, template]

must_haves:
  truths:
    - "README has Steps 1/2/3 sections written from scratch using the lean 5-section template"
    - "Each Step section contains exactly 5 subsections in this order: What you'll learn / Checkpoint / Run / What to look for / Why it matters"
    - "Step 2 What to look for embeds docs/screenshots/step-02-disconnected-traces.png"
    - "Step 3 What to look for embeds docs/screenshots/step-03-joined-trace.png next to step-02 (DOC-04 broken/fixed pair side-by-side)"
    - "Existing Prerequisites + Workshop checkpoints summary preserved verbatim where unchanged"
    - "Existing Steps 4/5/6 prose UNCHANGED in this plan (plan 07-06 rewrites them)"
  artifacts:
    - path: "README.md"
      provides: "Steps 1/2/3 in lean 5-section template + dashboard/load-script callouts"
      contains: "## Step 1"
  key_links:
    - from: "Step 2 What to look for"
      to: "docs/screenshots/step-02-disconnected-traces.png"
      via: "Markdown image embed"
      pattern: "step-02-disconnected-traces.png"
    - from: "Step 3 What to look for"
      to: "docs/screenshots/step-03-joined-trace.png"
      via: "Markdown image embed"
      pattern: "step-03-joined-trace.png"
    - from: "Step 1 Run section"
      to: "mise run load"
      via: "copy-pasteable command"
      pattern: "mise run load"
---

<objective>
Author the README's Steps 1/2/3 from scratch using the lean 5-section per-step template (D-08), embed the DOC-04 broken/fixed screenshot pair, and add dashboard/load-script callouts. Implements DOC-01 (Steps 1-3 slice) + DOC-04 (the load-bearing visual pair) per CONTEXT.md D-07 + D-08.

Purpose: Steps 1/2/3 currently appear in the README only as one-line bullets in the "Workshop checkpoints" summary (lines 70-72). This plan writes their full per-step bodies in the new lean template so all six steps share one voice. The DOC-04 broken/fixed visual pair is the single most important asset in the repo (CONTEXT.md `<specifics>`); this plan lands it side-by-side in Steps 2 + 3.

Output: `README.md` with new Steps 1/2/3 sections inserted between the existing "Workshop checkpoints" summary block and the existing "## Step 4: Metrics" section. Steps 4/5/6 prose UNTOUCHED in this plan (plan 07-06 rewrites them to fit the same template).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@README.md
@.planning/phases/01-baseline-scaffold/1-06-SUMMARY.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@.planning/phases/03-amqp-context-propagation/03-CONTEXT.md
@docs/screenshots/step-01-empty-tempo.png
@docs/screenshots/step-02-disconnected-traces.png
@docs/screenshots/step-03-joined-trace.png
@docs/screenshots/step-03-waterfall.png
@CLAUDE.md

<interfaces>
<!-- The lean 5-section template (D-08 verbatim, EXACT order): -->
1. **What you'll learn** — 1-2 sentences
2. **Checkpoint** — annotated git tag + `git checkout step-NN-*` one-liner + 1-line "what's new since step-(N−1)"
3. **Run** — copy-pasteable `mise run` / `curl` commands
4. **What to look for** — Grafana queries / Mimir series / Loki filters + the screenshot embed
5. **Why it matters** — 1 paragraph pedagogical close + cross-ref to Concepts & FAQ where applicable

<!-- DOC-04 broken/fixed pair side-by-side rendering: -->
GitHub-flavored markdown does NOT support multi-column natively. The CONTEXT.md D-07 directive
allows an HTML <table> OR aligned image rows. Both approaches render correctly on GitHub web,
local cat, and most markdown previewers. Pick HTML <table> for predictable side-by-side layout.

<!-- Cross-reference targets in Concepts & FAQ (preserved by plan 07-06): -->
- "Reading the code"  ->  Step 2 Why-it-matters references this
- "Why is OtelSdkConfiguration.java duplicated?"  ->  Step 2 Why-it-matters references
- "Why is the propagation pair shared?"           ->  Step 3 Why-it-matters references
- "What's NOT here yet"                           ->  Step 1 Why-it-matters references

<!-- Existing README structural anchors: -->
- L1-5: Title + intro paragraph (preserved)
- L7-66: Prerequisites + IDE + One-time setup + First run (preserved verbatim — DOC-02 covered in Phase 1; CONTEXT.md D-07 explicitly says "Phase 7 cross-references it, doesn't duplicate")
- L68-77: Workshop checkpoints summary block (preserved; the bullet for step-06 is the "Current." marker)
- L79+: ## Step 4 / ## Step 5 / ## Step 6 / Reading the code / Why-duplicated / Why-shared / What's NOT here yet (UNTOUCHED in this plan)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add Step 1 — Baseline & Scaffold</name>
  <read_first>
    - README.md (whole file — confirms current structure)
    - .planning/phases/01-baseline-scaffold/1-06-SUMMARY.md (Phase 1 facts to anchor *Why it matters*)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07/D-08 — template, dashboard + load callouts)
    - docs/screenshots/step-01-empty-tempo.png (the screenshot to embed)
  </read_first>
  <action>
    Insert a new section between the existing "Workshop checkpoints" block (ending around L77) and
    the existing "## Step 4: Metrics" section (starting around L79). This task lands ONLY Step 1.
    Subsequent tasks land Steps 2 and 3. Tasks 1-3 land on the file in order.

    Use the Edit tool with `old_string` set to the exact existing transition between the two blocks.
    Find the EXISTING line:
    ```
    This section establishes the convention; Phase 7 turns each bullet into a full walkthrough.
    ```
    Replace it with:
    ```
    This section establishes the convention; the per-step walkthroughs below follow a uniform 5-section template — *What you'll learn* / *Checkpoint* / *Run* / *What to look for* / *Why it matters* — so you can read the workshop top-to-bottom or skip into any step. The load-bearing standalone narrative sections ("Reading the code", "Why is OtelSdkConfiguration.java duplicated?", "Why is the propagation pair shared?", "What's NOT here yet") are preserved as a Concepts & FAQ appendix at the bottom; per-step *Why it matters* paragraphs cross-reference them where relevant.

    ## Step 1: Baseline & Scaffold

    ### What you'll learn

    What a working two-service Spring Boot + RabbitMQ application looks like with **zero OpenTelemetry libraries on the classpath**. The baseline that every later step instruments — neutralised foundation pitfalls (BOM ordering, ports, mise/IDE) so every subsequent OTel lesson is uncontaminated by tooling friction.

    ### Checkpoint

    `git checkout step-01-baseline` — first commit; nothing to compare against.

    ### Run

    ```sh
    mise run preflight   # Docker up, ports free, JDK 17, Maven 3.9 active
    mise run infra:up    # starts RabbitMQ + grafana/otel-lgtm
    mise run dev         # starts producer + consumer in parallel
    mise run demo:order  # POSTs a sample order; expect 202
    mise run load        # OPTIONAL — continuous load (~1 req/sec, 50/50 priorities)
    ```

    `mise run load` is the workshop's continuous-load script (Phase 7 / WORK-03). It launches two parallel `oha` invocations alternating `priority=express` and `priority=standard` at ~0.5 req/sec each (~1 req/sec total). Run it in a second terminal alongside `mise run dev` so live demos have flowing telemetry without hand-clicking. Ctrl-C terminates both child loaders cleanly.

    ### What to look for

    - **Producer console**: `Started ProducerApplication in <Ns>`, then `OrderCreated` accept lines on every `mise run demo:order`.
    - **Consumer console**: `OrderCreated received: orderId=<uuid>` per published order.
    - **Grafana** (`mise run ui:grafana`, login `admin/admin`): the pre-provisioned **OSE OTel Demo — Three Signals** dashboard appears in the dashboard list automatically — but **all panels are empty**. The dashboard's two-row layout (top = projector-friendly demo strip; bottom = collapsed deeper-dive) IS the workshop's pedagogical message: small demo, bigger production glimpse.
    - **`mvn dependency:tree -Dincludes=io.opentelemetry`**: zero matches. There are no OTel libraries on the classpath yet.
    - **Tempo trace search** (Grafana → Explore → Tempo): zero traces ever, no matter how many orders you POST.

    ![Phase 1 baseline — empty Tempo trace search](docs/screenshots/step-01-empty-tempo.png)

    ### Why it matters

    Every subsequent step adds **one** OTel surface to this baseline. The empty Tempo view IS the lesson — until Phase 2 wires `OpenTelemetrySdk.builder()`, the OTLP endpoint is open and the Grafana stack is running, but the apps emit nothing. This intentional *uninstrumented* shape lets each later step's diff read as a focused addition rather than a tangled refactor. The continuous-load script also sneaks in a tiny instrumentation lesson: while `mise run load` is running, `oha`'s TUI shows live RPS + p50/p95/p99 latency in the same terminal pumping load — a side-by-side "client view vs server view" preview of what `http.server.request.duration` will eventually show in Mimir from Phase 4 onwards. See the *What's NOT here yet* entry in the Concepts & FAQ appendix for the full list of deliberate Phase 1 omissions.

    ```
    (placeholder — Step 2 inserted by Task 2)
    ```
    ```
  </action>
  <verify>
    <automated>
      grep -q '## Step 1: Baseline & Scaffold' README.md \
      && grep -q '### What you' README.md \
      && grep -q '### Checkpoint' README.md \
      && grep -q '### Run' README.md \
      && grep -q '### What to look for' README.md \
      && grep -q '### Why it matters' README.md \
      && grep -q 'docs/screenshots/step-01-empty-tempo.png' README.md \
      && grep -q 'mise run load' README.md \
      && grep -q 'OSE OTel Demo' README.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README.md contains the literal heading `## Step 1: Baseline & Scaffold`
    - All five subsection headers present: `### What you'll learn`, `### Checkpoint`, `### Run`, `### What to look for`, `### Why it matters`
    - Markdown image embed for `docs/screenshots/step-01-empty-tempo.png`
    - Run section contains `mise run preflight`, `mise run infra:up`, `mise run dev`, `mise run demo:order`, `mise run load`
    - Why-it-matters cross-references the Concepts & FAQ "What's NOT here yet" appendix entry
    - Existing "## Step 4: Metrics" section UNCHANGED (later in file)
    - Existing Prerequisites section UNCHANGED
  </acceptance_criteria>
  <done>
    Step 1 lands in README using the 5-section template; dashboard + load-script callouts present;
    empty-Tempo screenshot embedded.
  </done>
</task>

<task type="auto">
  <name>Task 2: Add Step 2 — Manual SDK Bootstrap & First Traces (with DOC-04 broken-half embed)</name>
  <read_first>
    - README.md (Step 1 just landed)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (Phase 2 D-01, D-12, D-15, DOC-03, DOC-05, broken-propagation framing)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07 invariants — per-service duplication callout MUST cross-reference Concepts & FAQ)
    - docs/screenshots/step-02-disconnected-traces.png (DOC-04 broken half — embedded here AND referenced from Step 3 for side-by-side rendering)
  </read_first>
  <action>
    Replace the placeholder `(placeholder — Step 2 inserted by Task 2)` block from Task 1 with the
    Step 2 section. Use the Edit tool with `old_string` matching the placeholder block (including
    its surrounding code-fence backticks if added) and `new_string` set to the Step 2 prose below.

    ```markdown
    ## Step 2: Manual SDK Bootstrap & First Traces

    ### What you'll learn

    The smallest possible OpenTelemetry surface — `OpenTelemetrySdk.builder()` + `Resource` + `SdkTracerProvider` + `BatchSpanProcessor` + `OtlpGrpcSpanExporter` + explicit `Sampler.parentBased(Sampler.alwaysOn())` + graceful shutdown — wired manually in EACH service. Plus span-kind discipline: SERVER + INTERNAL + PRODUCER on the producer; CONSUMER + INTERNAL on the consumer. The broken-propagation state is INTENTIONAL — Phase 3 fixes it.

    ### Checkpoint

    `git checkout step-02-traces` — adds `OpenTelemetrySdk` per-service + the inline span call sites. The producer trace and consumer trace appear separately in Tempo; they are not yet connected (that lands in Step 3).

    ### Run

    ```sh
    git checkout step-02-traces
    mise run infra:up
    mise run dev
    mise run demo:order
    # then open Grafana -> Tempo Explore
    ```

    ### What to look for

    - **Two distinct traces in Tempo** for one logical `POST /orders`: one with `service.name=order-producer`, one with `service.name=order-consumer`. They share NOTHING — no traceId, no parent/child link.
    - **Producer trace structure**: SERVER span (`POST /orders` with HTTP semconv attributes `http.request.method`, `url.path`, `http.response.status_code`) wrapping an INTERNAL span (business logic).
    - **Consumer trace structure**: CONSUMER span wrapping an INTERNAL span.
    - **Service identity** — never `unknown_service:java`; both services emit correct `service.name` / `service.namespace` / `service.instance.id` / `deployment.environment.name` resource attributes (Phase 2 TRACE-02 + D-05).
    - **Graceful shutdown**: press Ctrl-C on either app; the **last** batch of spans still appears in Tempo afterwards (`@Bean(destroyMethod = "close")` cascade flushes pending batches).

    <table>
      <tr>
        <th align="center">Step 2 — broken (TWO disconnected traces)</th>
        <th align="center">Step 3 — fixed (ONE joined trace)</th>
      </tr>
      <tr>
        <td><img src="docs/screenshots/step-02-disconnected-traces.png" alt="Step 2 — TWO disconnected traces in Tempo for one POST"></td>
        <td><img src="docs/screenshots/step-03-joined-trace.png" alt="Step 3 — ONE joined trace in Tempo for one POST"></td>
      </tr>
    </table>

    Read the broken/fixed pair side-by-side. The same `POST /orders` call: two traces in Step 2, one trace in Step 3. The single-line propagation pair Phase 3 introduces is what closes the gap.

    ### Why it matters

    The "brokenness" of unpropagated traces IS the phase deliverable. Every distributed-tracing implementation faces this exact moment — the SDK is wired, traces are flowing, services are correctly identified, but trace IDs are NOT yet shared across the messaging boundary. Reading two `OtelSdkConfiguration.java` files (one per service) and seeing the broken state in Tempo BEFORE seeing the fix anchors the propagation lesson Phase 3 lands. The per-service duplication of `OtelSdkConfiguration.java` is also intentional — see *Why is OtelSdkConfiguration.java duplicated?* in the Concepts & FAQ appendix for the rationale (so you don't "fix" the duplication by extracting a shared library and lose half the lesson). For the manual-SDK textbook tour, see *Reading the code* in the same appendix.

    ```
    (placeholder — Step 3 inserted by Task 3)
    ```
    ```
  </action>
  <verify>
    <automated>
      grep -q '## Step 2: Manual SDK Bootstrap' README.md \
      && grep -q 'docs/screenshots/step-02-disconnected-traces.png' README.md \
      && grep -q 'docs/screenshots/step-03-joined-trace.png' README.md \
      && grep -q '<table>' README.md \
      && grep -q 'Why is OtelSdkConfiguration.java duplicated' README.md \
      && grep -q 'Reading the code' README.md \
      && (grep -c '### What you' README.md) | { read -r n; [[ $n -ge 2 ]]; } \
      && (grep -c '### Checkpoint' README.md) | { read -r n; [[ $n -ge 2 ]]; } \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README contains `## Step 2: Manual SDK Bootstrap & First Traces`
    - All 5 subsection headers present in Step 2
    - HTML `<table>` element rendering step-02 + step-03 PNGs side-by-side (DOC-04 broken/fixed pair)
    - Both PNG paths referenced in the table (`step-02-disconnected-traces.png` and `step-03-joined-trace.png`)
    - Why-it-matters cross-references "Why is OtelSdkConfiguration.java duplicated?" AND "Reading the code"
    - Existing "## Step 4: Metrics" section UNCHANGED
  </acceptance_criteria>
  <done>
    Step 2 lands; DOC-04 broken/fixed pair embedded side-by-side via HTML table; cross-refs to
    Concepts & FAQ appendix entries present.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add Step 3 — AMQP Context Propagation (THE headline lesson)</name>
  <read_first>
    - README.md (Steps 1 + 2 just landed)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (PROP-01..PROP-04 + APP-04 + TRACE-09 — headline lesson framing)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07 invariants — propagation-pair-shared callout cross-references Concepts & FAQ)
    - docs/screenshots/step-03-joined-trace.png + (optional) docs/screenshots/step-03-waterfall.png
  </read_first>
  <action>
    Replace the placeholder `(placeholder — Step 3 inserted by Task 3)` block from Task 2 with the
    Step 3 section.

    ```markdown
    ## Step 3: AMQP Context Propagation — THE Headline Lesson

    ### What you'll learn

    The single-line addition that joins producer and consumer into ONE distributed trace — `propagator.inject(Context.current(), props, SETTER)` on the producer side and `propagator.extract(Context.current(), props, GETTER)` plus `.setParent(extracted)` on the consumer side. Plus the `recordException` + `setStatus(ERROR)` pattern on the deterministic 10th-order failure path.

    ### Checkpoint

    `git checkout step-03-context-propagation` — adds the `TracingMessagePostProcessor` (producer-side inject) + `TracingMessageListenerAdvice` (consumer-side extract) pair from the shared `otel-bootstrap` Maven module, deletes Phase 2's inline producer/consumer span bodies, and wires the deterministic-failure error path. The single-line `.setParent(extracted)` on the consumer span makes `consumer.parentSpanId == producer.spanId`.

    ### Run

    ```sh
    git checkout step-03-context-propagation
    mise run infra:up
    mise run dev
    mise run demo:order              # one normal order
    for i in $(seq 1 10); do mise run demo:order; done   # trigger the 10th-order failure
    ```

    ### What to look for

    - **One trace** in Tempo for one `POST /orders`: producer + consumer spans share `traceId`; the consumer span's `parentSpanId` equals the producer span's `spanId`. (Re-read Step 2's broken/fixed pair above — same call, two-traces-vs-one.)
    - **`traceparent` header** in the message: open RabbitMQ Management UI (`mise run ui:rabbitmq`, `guest/guest`), inspect any `orders.created` message, and see a readable `traceparent: 00-<32-hex>-<16-hex>-01` value. **Never** `[B@...` or hex-blob byte-array signatures (PITFALLS.md #2 — String, not `byte[]`).
    - **Error-status propagation across the AMQP hop**: every 10th order triggers `ProcessingFailedException` in the consumer; Tempo renders that trace as `Error` status with the exception event attached to the consumer span (`recordException` + `setStatus(ERROR)`).
    - **Symmetry of one inject method matched by one extract method**: open `otel-bootstrap/src/main/java/com/example/otel/amqp/` and read `TracingMessagePostProcessor` next to `TracingMessageListenerAdvice`. The structural symmetry IS the lesson.

    ```
    (Optional: a waterfall capture of the joined trace lives at docs/screenshots/step-03-waterfall.png if the screenshot pipeline produced it.)
    ```

    ### Why it matters

    Phase 3 is the workshop's headline lesson. The broken-then-fixed delta from Step 2 is the artifact's most powerful pedagogical moment — `git diff step-02-traces..step-03-context-propagation` shows a small, readable changeset focused on the propagation pair, and Tempo renders the consequence visually. The propagation pair lives in a shared module on purpose — exactly the OPPOSITE design choice from the per-service-duplicated SDK bootstrap. See *Why is the propagation pair shared?* in the Concepts & FAQ appendix for the contrast (per-service code is duplicated so attendees read it twice; cross-service code is shared so attendees read ONE inject method matched by ONE extract method, and the symmetry IS the lesson). Phase 3 also corrects an OTel messaging-semconv divergence from Phase 2 — the producer's `messaging.destination.name` attribute is now the **exchange** (`orders`), not the queue (`orders.created`). The error-status propagation across the AMQP hop is itself a teaching moment: the consumer span carries `Status.ERROR` with the recordException event because the extracted-and-attached parent context lets `Span.current()` return the right span at the listener body's catch block.

    ```
  </action>
  <verify>
    <automated>
      grep -q '## Step 3: AMQP Context Propagation' README.md \
      && grep -q 'Why is the propagation pair shared' README.md \
      && grep -q 'parentSpanId' README.md \
      && grep -q 'recordException' README.md \
      && grep -q 'traceparent' README.md \
      && (grep -c '## Step ' README.md) | { read -r n; [[ $n -ge 6 ]]; } \
      && grep -q '## Step 4: Metrics' README.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README contains `## Step 3: AMQP Context Propagation`
    - All 5 subsection headers present in Step 3
    - References `parentSpanId`, `recordException`, `traceparent` (Phase 3 invariants)
    - Why-it-matters cross-references "Why is the propagation pair shared?"
    - README now contains exactly 6 `## Step ` headings (1, 2, 3, 4, 5, 6)
    - Existing "## Step 4: Metrics" section UNCHANGED in this plan
    - Existing Concepts & FAQ section markers ("Reading the code" / "Why is OtelSdkConfiguration.java duplicated?" / "Why is the propagation pair shared?" / "What's NOT here yet") still present (will be reorganized into a "## Concepts & FAQ" header by plan 07-06)
  </acceptance_criteria>
  <done>
    Steps 1, 2, 3 land in the README using the 5-section template. DOC-04 broken/fixed pair is in
    Step 2's What-to-look-for. Cross-refs to Concepts & FAQ entries present in all three Steps'
    Why-it-matters paragraphs.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| README.md edits | No untrusted content — all text is authored by the planner from CONTEXT.md verbatim |
| Embedded image references | Local `docs/screenshots/*.png` paths only; no remote URL hotlinks |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-05-01 | Information Disclosure | README image embeds | accept | Local relative paths only (`docs/screenshots/*.png`); no external URLs that could exfiltrate viewer IPs |
| T-07-05-02 | Tampering | HTML `<table>` block in markdown | mitigate | GitHub-flavored markdown sanitizes HTML by allowlist; `<table>`, `<tr>`, `<th>`, `<td>`, `<img>` are all on the allowlist; no `<script>` or `<iframe>` |
| T-07-05-03 | Spoofing | Cross-references to Concepts & FAQ entries | mitigate | Plan 07-06 keeps the same anchor text exactly; this plan's grep gate verifies the appendix entries still exist before final phase exit |
</threat_model>

<verification>
- `## Step 1`, `## Step 2`, `## Step 3` sections present in README.
- Each Step contains exactly 5 subsection headers in the prescribed order.
- DOC-04 broken/fixed pair embedded side-by-side via HTML `<table>` in Step 2.
- Cross-references to Concepts & FAQ entries present in Why-it-matters paragraphs.
- Existing Steps 4/5/6 prose UNCHANGED (plan 07-06 rewrites them).
</verification>

<success_criteria>
- DOC-01 partial (Steps 1-3 of 6) — lean 5-section template applied.
- DOC-04 broken/fixed pair visible side-by-side in Step 2.
- Existing Prerequisites + Workshop checkpoints summary preserved.
- Steps 4/5/6 prose untouched (next plan rewrites them).
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-05-SUMMARY.md` recording:
- Final byte count of README.md before vs after this plan
- Whether the optional `step-03-waterfall.png` was referenced (yes/no — based on whether plan 07-04 produced it)
- Any deviations from D-07 invariants (none expected — all carryforward facts are in the prose)
</output>
