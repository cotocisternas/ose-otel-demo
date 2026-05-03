# Phase 11: Tail Sampling at the Collector - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a `tail_sampling` processor to the standalone Collector's traces pipeline so the *Collector* — not the SDK — decides which complete traces reach Tempo. Three policies (`status_code ERROR` keep-100%, `latency >1s` keep-100%, `probabilistic 20%` fallback), wrapped in a `composite` envelope with per-branch rate-limits (`errors=unlimited`, `slow=20/s`, `prob=40/s`), placed BEFORE the `batch` processor in the canonical OTel order. `decision_wait: 10s`, `num_traces: 10000`. The SDK stays at `Sampler.parentBased(Sampler.alwaysOn())` — no head sampling here (F2-3 mitigation).

A workshop attendee runs `mise run load SLOW_RPS=2`, opens Tempo, sees the trace count drop to ~30% of the pre-tail-sampling baseline, observes that EVERY error-status trace AND every latency>1s trace is preserved, and clicks through a new collapsed Grafana dashboard row to see the `tail_sampling` processor's own diagnostic counters live.

Phase boundaries (locked by REQUIREMENTS.md TSAMP-01..03 + this discussion):
- Pure Collector configuration is the headline lesson, but to make the latency policy actually fire, the producer service gets a small WIDGET-SLOW SKU branch (`Thread.sleep(1500)` inside the INTERNAL span) and `scripts/load.sh` gets a fourth `SLOW_RPS` stream — accepted minor scope expansion (~5 LOC + JavaDoc; mirrors the existing WIDGET-* SKU family pattern).
- ON/OFF demonstration via git-tag time-travel screenshot pair (`step-11-tail-sampling-OFF.png` captured manually NOW on `main` HEAD pre-Phase-11; `step-11-tail-sampling-ON.png` captured manually post-Phase-11) — mirrors Phase 10 D-13 / Phase 7 D-04 manual-one-shot precedent.
- New collapsed `Tail Sampling diagnostics` row added to `grafana/dashboards/ose-otel-demo.json` (Phase 10 D-01 deliberately preserved this file; Phase 11 explicitly amends it).
- New `mise run verify:tail-sampling` task asserts the processor is registered AND emitting decisions for all three policy names — fails fast on policy-name typos / config drift.
- Phase 16's mandatory F2-3 head-vs-tail double-filter callout is forward-flagged as an inline blockquote at the end of README §11.

Out of scope for this phase (carried forward from REQUIREMENTS.md and ROADMAP.md):
- Head sampling (Phase 16) — SDK sampler stays `parentBased(alwaysOn())`; F2-3 callout reminds attendees not to enable both simultaneously without the documented escape hatch.
- Multi-collector load-balancing — workshop is single-Collector by REQUIREMENTS.md "v2.0-specific exclusions".
- Exemplars send_exemplars wiring — Phase 12 (datasource surface for exemplarTraceIdDestinations was pre-wired in Phase 10 D-02; Phase 12 only flips on `send_exemplars` + `ExemplarFilter.traceBased()`).
- Loki recording rules — Phase 13 (ruler block was pre-enabled in Phase 10 D-07).
- New integration test for tail sampling — Collector-side processor; not unit-testable from the Java side. Phase-11 verification rests on the live `verify:tail-sampling` curl gate + the dashboard row + the README screenshot pair.

</domain>

<decisions>
## Implementation Decisions

### Tail-sampling pipeline + policy structure

- **D-T1:** **Pipeline placement = `[memory_limiter, tail_sampling, batch]`** — canonical OTel order (matches research/ARCHITECTURE.md line 185 verbatim). Tail-sampling buffers full traces internally regardless of placement, but emitting BEFORE batch means the batch processor only ever sees KEPT traces (~30% of incoming volume). The misleading forecast comment in `infra/observability/otelcol-config.yaml:180` (`# Phase 11 inserts tail_sampling between batch and the exporter`) is corrected as part of this phase.

- **D-T2:** **Composite policy structure** — TSAMP-01's three policies are nested under a `composite` envelope rather than written as a flat top-level list. This adds a per-branch rate-limit lesson on top of the OR-semantics lesson; production-realistic. Composite still enforces OR-semantics across sub-policies (any sub-policy votes 'sample' → composite votes 'sample'), but each sub-policy hits its own rate-limit BEFORE casting its vote.

- **D-T3:** **Composite per-branch rate-limits = `errors=unlimited`, `slow=20/s`, `prob=40/s`** — production logic ("ALWAYS keep errors") + tight caps on the fallback policies so bursts visibly clamp. At load.sh defaults (~200 rps steady, ~10% errors → ~20 errors/s, ~2 rps slow), errors stay uncapped, slow runs well under its 20/s cap, and probabilistic ~40/s ≈ 20% of the ~180 rps non-error volume — matches TSAMP-03's "approximately 20%" target. Burst (~150 rps additional) makes the prob cap visibly clamp.

- **D-T4:** **TSAMP-02 OR-semantics narrative placement** — Big comment block above the composite block + per-branch sub-comments per sub-policy. Top block explains the four-tier priority chain (`drop > inverted_not_sample > sample > inverted_sample`) AND the "composite envelope: per-branch rate-limit applies BEFORE the sample/not_sample vote" addendum. Per-branch sub-comments document the WHY of each rate cap. ~25-line comment block total. Matches Phase 10 D-04 teaching-grade YAML style.

- **D-T15:** **Policy names = `keep-errors`, `keep-slow`, `probabilistic-fallback`** — self-documenting verb-style names. They appear as the `policy=` label in `otelcol_processor_tail_sampling_count_traces_sampled` series, so they're the dashboard-row legend AND the verify:tail-sampling gate's assertion targets. README §11 walkthrough quotes them verbatim.

### Latency-policy realism (synthetic slow-trace generation)

- **D-T5:** **WIDGET-SLOW SKU branch in producer** — adds a tiny branch in `OrderService.place()`: if `sku == "WIDGET-SLOW"`, `Thread.sleep(1500)` inside the INTERNAL span. Mirrors the existing WIDGET-EXPRESS / WIDGET-STANDARD / WIDGET-IDEMPOTENT / WIDGET-BURST family pattern (zero new architecture). This bends the "pure Collector phase" framing but the pedagogical payoff (attendees see all three policies fire) is high. ~5 lines of Java + JavaDoc.

- **D-T6:** **Sleep location + duration: producer INTERNAL span, 1500ms** — span duration > 1.5s, latency policy fires reliably above the 1000ms threshold (50% buffer). Tail-sampling's `latency` policy uses MAX-span duration of the assembled trace, so producer-side sleep alone is sufficient; consumer span stays fast (~10ms). Smallest code change.

- **D-T7:** **load.sh adds `SLOW_RPS` env var (default 2, set to 0 to disable)** — fourth steady oha stream verbatim mirroring the IDEMPOTENT_RPS pattern (the v1.0 quick-task 260502-ld2 idempotency-stream block is the structural template). Heartbeat output adds a 'slow' line. ~2 rps means ≈20 slow traces in a 10s decision window — enough to be visible, not enough to dominate.

- **D-T8:** **WIDGET-SLOW shares the existing `AtomicInteger counter`** — slow traces hit the Phase 3 deterministic 10%-failure path on roughly every 10th call. Those traces match BOTH `status_code=ERROR` AND `latency>1s` — a perfect demonstration of OR-semantics (a trace can match multiple branches; gets sampled once). Zero changes to ProcessingService. The composite rate-cap math: at SLOW_RPS=2, ~0.2/s become slow+error, well under both the unlimited errors cap and the 20/s slow cap.

### ON/OFF demo mechanism

- **D-T9:** **Git-tag time-travel screenshot pair** — OFF screenshot captured manually NOW on `main` HEAD pre-Phase-11 and committed as `docs/screenshots/step-11-tail-sampling-OFF.png`. ON screenshot captured manually post-Phase-11 as `docs/screenshots/step-11-tail-sampling-ON.png`. Mirrors Phase 10 D-13 (PREREQ-02 manual one-shot) verbatim. Zero runtime toggle code in the YAML — the YAML they read in the repo is the truth, no commented-out OFF blocks.

- **D-T10:** **Screenshot subject = Tempo Search "Last 5 min" trace count, identical query** — `Service Name = order-producer`, same time range, captured twice. The OFF panel shows the unsampled count (e.g., ~1500 traces in 5min); the ON panel shows the sampled count (e.g., ~600 traces — 100% errors + 100% slow + ~20% rest). README caption highlights the numeric ratio.

- **D-T11:** **README §11 narrative depth = Phase-10-equivalent** — full step-11 walkthrough mirroring step-10's structure, ~100-150 lines: (1) what the YAML adds; (2) annotated YAML excerpt showing the composite + rate-limits + comment block; (3) ON/OFF screenshot pair via HTML `<table>` (Phase 7 D-04 paired-screenshot precedent); (4) the OR-semantics priority chain explained; (5) the WIDGET-SLOW interaction (why we added the sleep); (6) F2-3 head-vs-tail callout (D-T12); (7) `mise run load SLOW_RPS=2` invocation + `mise run verify:tail-sampling` invocation. Matches v1.0 README density.

- **D-T12:** **F2-3 head-vs-tail callout = inline blockquote at the end of README §11** — `> **CRITICAL: Double-filter trap.**` block mirrors the ROADMAP.md Phase-16 callout verbatim: (a) Phase 16 will introduce SDK-side head sampling at 50%; (b) running both simultaneously → effective rate ~10%; (c) options for the attendee (`OTEL_TRACES_SAMPLER=parentbased_always_on` to disable head sampling during tail demos, OR explicitly accept the ~10% rate). When Phase 16 lands, its README §16 callout back-links to Phase 11's blockquote.

### Self-observability of tail_sampling

- **D-T13:** **New collapsed "Tail Sampling diagnostics" row added to `grafana/dashboards/ose-otel-demo.json`** — 4 panels: (1) `rate(otelcol_processor_tail_sampling_count_traces_sampled[1m])` by `policy` label — shows which policy is keeping how many traces; (2) `rate(otelcol_processor_tail_sampling_count_spans_dropped[1m])` — drop counter; (3) `otelcol_processor_tail_sampling_late_span_total` — catches F2-2 (late spans evaluated against a cached decision); (4) `otelcol_processor_tail_sampling_decision_latency_bucket` histogram — catches when `decision_wait` is too short. Collapsed by default (matches Phase 7 dashboard's `Deeper-dive (post-workshop)` collapsed-row idiom). Phase 10 D-01 deliberately preserved ose-otel-demo.json — Phase 11 is the FIRST v2.0 phase to amend it; the row is a discrete additive block (no edits to existing panels, no UID changes).

- **D-T14:** **`mise run verify:tail-sampling` task** — curls `http://localhost:8888/metrics`, greps for `otelcol_processor_tail_sampling_count_traces_sampled` AND asserts that the `policy=` label values include all three configured policy names (`keep-errors`, `keep-slow`, `probabilistic-fallback`). Hard-fail if any policy is missing or the metric isn't present. Catches: typos in policy names, processor not loaded, configuration drift between the YAML and the dashboard row. Mirrors `verify:datasources` / `verify:images` naming (Phase 10 D-03/D-14 pattern). ~15 lines of bash.

- **D-T16:** **Belt + suspenders drift guard** — Beyond D-T14's verify gate, add a JSDoc-style top comment in the ose-otel-demo.json tail_sampling row noting "policy names are a contract with infra/observability/otelcol-config.yaml — see `mise run verify:tail-sampling`". The verify task's per-policy assertion is the load-bearing guard; the dashboard JSDoc is human-readable signaling.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact YAML key shape for the `composite` policy (pull from upstream OTel collector-contrib tail_sampling README; verified-via-Context7 reference in research/ARCHITECTURE.md line 946).
- Exact JavaDoc wording on the WIDGET-SLOW branch in `OrderService.place()` — should mention "Phase 11 D-T5" lineage and forward-link to README §11.
- `Thread.sleep(1500)` vs alternatives (e.g., `CompletableFuture.supplyAsync(...).get()` with delayed executor) — use the simplest mechanism; `Thread.sleep` is fine for a workshop demo (no thread-pool exhaustion concern at SLOW_RPS=2; if higher, planner picks another).
- Exact PromQL phrasing for the four diagnostic panels — planner picks defensible queries; legend should use `{{policy}}` template variable for the per-policy panel.
- Exact `verify:tail-sampling` bash regex — planner picks a defensible pattern (greps must match the `policy=` label format `otelcol_processor_tail_sampling_count_traces_sampled{policy="keep-errors",...}`).
- README §11 word-for-word phrasing for the OR-semantics narrative — paraphrase from research/PITFALLS.md F2-1 in the workshop's voice; don't quote verbatim.
- Whether the OFF screenshot needs a separate `mise run docs:screenshot:tail-sampling-off` task or stays manual — manual one-shot per D-T9 unless the planner finds an automatable shape (D-13's manual-one-shot precedent stands).
- Plan count + wave structure for Phase 11 — likely 3-4 plans (YAML edit, Java+load.sh edit, dashboard+verify task, README+screenshots) but planner decides based on dependency analysis.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service duplication of `OtelSdkConfiguration` — Phase 11 does NOT touch SDK), WORK-01 (annotated git tags on `main`)
- `.planning/REQUIREMENTS.md` § Tail Sampling at the Collector (TSAMP-01..03) — locked policies, decision_wait, num_traces, OR-semantics comment block requirement, ON/OFF screenshot requirement
- `.planning/REQUIREMENTS.md` § "v2.0-specific exclusions" — no Alloy, no multi-Collector LB, no Prometheus pull
- `.planning/ROADMAP.md` Phase 11 section — pedagogical headline, Success Criteria #1–3, pitfall mitigations (F2-1, F2-2, F2-3), git tag `step-11-tail-sampling`
- `.planning/STATE.md` — recent decisions including Phase 16/11 interaction (head sampling never simultaneous with tail sampling without F2-3 callout); Phase 10 completion record at commit 430231b (IN-06: Collector self-metrics scrape job in place — prerequisite for D-T13/D-T14)

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes operational arc (Phase 10 unblocks 11/12/13)
- `.planning/research/STACK.md` — collector-contrib v0.151.0 image rationale (`tail_sampling` is in contrib, not core); receivers/processors/exporters list
- `.planning/research/ARCHITECTURE.md` § "Feature 2: Tail Sampling at the Collector" (lines 270–293) — single-Collector topology rationale, OR-semantics description, "no SDK change required" invariant
- `.planning/research/ARCHITECTURE.md` line 185 — canonical pipeline order `[memory_limiter, resource, attributes, filter, tail_sampling, batch]` (D-T1 source)
- `.planning/research/ARCHITECTURE.md` lines 130–140 — example tail_sampling block (3 policies — D-T2 starting point, but composite envelope wraps them per D-T2)
- `.planning/research/PITFALLS.md` § F2-1 (OR semantics, NOT first-match) — D-T4 narrative source
- `.planning/research/PITFALLS.md` § F2-2 (`decision_wait`/`num_traces`/late-span behavior) — D-T13 dashboard-panel rationale; metric names `otelcol_processor_tail_sampling_late_span_total` and `..._dropped_traces_total`
- `.planning/research/PITFALLS.md` § F2-3 (head + tail double-filter trap) — D-T12 callout source (verbatim mirror)
- `.planning/research/FEATURES.md` § "V2-TS-2 Tail Sampling" + § "V2-DF-2 Tail Sampling differentiator" — original rationale for "side-by-side trace count visualization ON/OFF" → D-T9/D-T10
- `.planning/research/FEATURES.md` reference to [OTel Collector tail_sampling README](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md) — primary upstream doc for `composite` policy YAML key shape

### Phase 10 carryover (must read before touching otelcol-config.yaml or ose-otel-demo.json)

- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — D-04 (teaching-grade YAML style with WHY comments per block), D-13 (manual one-shot screenshot precedent), D-14 (verify:images mise task pattern), D-03 (verify:datasources mise task pattern), D-01 (ose-otel-demo.json deliberately preserved — Phase 11 EXPLICITLY amends it per D-T13)
- `.planning/phases/10-prerequisites-stack-decomposition/10-RESEARCH.md` — Collector decomposition research; collector-contrib component inventory
- `.planning/phases/10-prerequisites-stack-decomposition/10-VERIFICATION.md` — Phase 10's own verification artifacts; useful template for Phase 11's verification approach
- `.planning/phases/10-prerequisites-stack-decomposition/10-PATTERNS.md` — pattern map from Phase 10 (mise tasks, YAML comment density, screenshot capture)

### Files this phase EDITS (must read before planning)

- `infra/observability/otelcol-config.yaml` — current state of the Collector config; line 180 contains the misleading forecast comment that D-T1 corrects; lines 178–202 are the `pipelines:` block where `tail_sampling` is inserted between `memory_limiter` and `batch` in the `traces:` pipeline
- `producer-service/src/main/java/com/example/producer/domain/OrderService.java` — D-T5 adds the WIDGET-SLOW branch in `place()` method; existing JavaDoc convention requires phase-attribution comments (line 45 example: `orders_created_total{order_priority="express"}`)
- `scripts/load.sh` — D-T7 adds `SLOW_RPS` stream mirroring the IDEMPOTENT_RPS block (lines 73–74 declaration + lines 138–166 stream body; the curl-based idempotency stream is the structural template)
- `grafana/dashboards/ose-otel-demo.json` — D-T13 adds the collapsed "Tail Sampling diagnostics" row; this is the FIRST v2.0 phase to amend the file (Phase 10 D-01 deliberately preserved it)
- `mise.toml` — D-T14 adds the `[tasks."verify:tail-sampling"]` block; existing `verify:datasources` (Phase 10 D-03) and `verify:images` (Phase 10 D-14) blocks are the structural template
- `README.md` — D-T11 adds §11 (~100-150 lines); D-T9 adds the screenshot pair via HTML `<table>` per Phase 7 D-04 precedent; D-T12 adds the F2-3 blockquote at end of §11
- `docs/screenshots/step-11-tail-sampling-OFF.png` (NEW, captured BEFORE Phase 11 lands)
- `docs/screenshots/step-11-tail-sampling-ON.png` (NEW, captured AFTER Phase 11 lands)

### Phase 10 reference for v1.0 carryover infrastructure (must read before touching docker-compose.yml — though Phase 11 should NOT need to)

- `docker-compose.yml` — Phase 11 is config-only at the file level; the otel-collector service block is unchanged
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` — D-T8's "share the 10% counter" decision rests on this file's existing `AtomicInteger counter` (lines 55–59) + the `if (n % 10 == 0) throw new ProcessingFailedException(...)` branch (lines 73–79); read for context (no edits needed)

### Upstream documentation references (research must consult; bookmarked here so planner doesn't re-discover)

- [OpenTelemetry Collector tail_sampling processor README](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md) — `composite` policy YAML key shape (D-T2/D-T3 source), `status_code` / `latency` / `probabilistic` sub-policies, OR-semantics priority chain documentation
- [Context7 `/open-telemetry/opentelemetry-collector-contrib`](https://context7.com/open-telemetry/opentelemetry-collector-contrib) — verified `composite` + sub-policy + `max_total_spans_per_second` rate-limit YAML syntax (D-T3 verification)
- [Collector `tailsamplingprocessor` self-metrics list](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/internal/metadata/generated_telemetry.go) — full list of `otelcol_processor_tail_sampling_*` metric names for D-T13/D-T14 panel queries

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`infra/observability/otelcol-config.yaml` line 180 forecast comment** — the existing `# Phase 11 inserts tail_sampling between batch and the exporter` is wrong per D-T1 (canonical placement is BEFORE batch); fix as part of this phase. The rest of the file's `# WHY:` comment density is the Phase 11 template.
- **`mise.toml` `[tasks."verify:datasources"]` and `[tasks."verify:images"]` blocks** — structural template for D-T14's `[tasks."verify:tail-sampling"]`. Pattern: bash one-liner with curl + grep + assertion; non-zero exit on violation.
- **`scripts/load.sh` lines 73–74 (IDEMPOTENT_RPS env var) + lines 138–166 (idempotency stream body)** — verbatim structural template for D-T7's SLOW_RPS stream. Uses curl (not oha) for per-request payload variation; SLOW_RPS uses oha because the payload is fixed (`WIDGET-SLOW`). Heartbeat output (lines 191–201) needs a 'slow' line addition.
- **`producer-service/.../OrderService.java` `place()` method** — existing INTERNAL span scope is where D-T5's `Thread.sleep(1500)` lands. JavaDoc convention requires phase-attribution (existing example at line 45: `orders_created_total{order_priority="express"}`).
- **`grafana/dashboards/ose-otel-demo.json` row structure** — collapsed `Deeper-dive (post-workshop)` row from Phase 7 D-04 is the structural template for D-T13's "Tail Sampling diagnostics" row (`collapsed: true` + `gridPos.y` ordering).
- **README.md Step 10 section + step-10-collector-decompose tag callout** — structural template for D-T11's §11 (HTML `<table>` for paired screenshots from Phase 7 D-04; `mise run` command callouts; phase-tag callout pattern).
- **Phase 10 commit 430231b (IN-06)** — added `otelcol` self-scrape job to otelcol-config.yaml lines 79–91; this is the prerequisite that makes `otelcol_processor_tail_sampling_*` series appear in Mimir for D-T13/D-T14. Read the commit for the exact label conventions (`source: infra-exporter`).

### Established Patterns

- **TRACE-01 / DOC-05** — `OtelSdkConfiguration` is intentionally per-service duplicated AND Phase 11 does NOT touch the SDK at all (TSAMP requirement). The WIDGET-SLOW branch lives in domain code (OrderService.place), not in SDK config — the workshop's "manual SDK code" surface stays unchanged from v1.0 baseline.
- **WORK-01** — Workshop checkpoints are annotated tags on `main`, applied atomically with the phase-completion commit. Phase 11's tag is `step-11-tail-sampling`. Tag is NOT applied during phase execution; it lands with the orchestrator's atomic merge commit per Phase 2-06 / 6-06 / 7-07 / 10 precedent.
- **`mise run verify:*` family** — `verify:bom` (Phase 2 invariant), `verify:datasources` (Phase 10 D-03), `verify:images` (Phase 10 D-14), now `verify:tail-sampling` (D-T14). Pattern: bash one-liner with grep/curl + assertion; non-zero exit on violation; documented in README invocation list.
- **D-04 teaching-grade YAML** — every block in YAML configs gets a `# WHY:` one-line comment. Phase 11's composite block + each sub-policy block gets the same treatment per D-T4 (~25-line comment block above composite + per-branch sub-comments).
- **Manual one-shot screenshots (D-13 lineage)** — `docs/screenshots/step-NN-*.png` files are captured by the operator one-shot at the relevant git tag/HEAD, committed as binary blobs. No `mise run docs:screenshots` automation modification (D-13 documented why automation can't render older tags reliably). D-T9 inherits this — manual capture both OFF (now) and ON (post-Phase-11).
- **Paired screenshot HTML `<table>` in README** — Phase 7 D-04's broken/fixed pair (`step-02-disconnected.png` + `step-03-joined.png`) used `<table>` for side-by-side rendering in GitHub markdown. D-T11 follows the same shape for the OFF/ON pair.
- **F-pitfall-mitigation labeling** — research/PITFALLS.md uses `Fn-m:` IDs (e.g., F2-1, F2-3); README callouts and YAML comments reference these by ID for traceability. D-T12's blockquote cites F2-3 explicitly.

### Integration Points

- **`infra/observability/otelcol-config.yaml`** — single integration surface for the `tail_sampling` processor: add the processor block + insert it into the `traces:` pipeline's `processors:` list at position 2 (between `memory_limiter` and `batch`). Fix the line-180 forecast comment.
- **`producer-service/src/main/java/com/example/producer/domain/OrderService.java`** — minimal edit (~5 lines + JavaDoc): add WIDGET-SLOW SKU branch with `Thread.sleep(1500)` inside the existing INTERNAL span scope.
- **`scripts/load.sh`** — minimal edit (~25 lines): declare `SLOW_RPS` env var, add fourth oha stream block, extend cleanup trap PID list, extend heartbeat output.
- **`mise.toml`** — minimal edit (~15 lines): add `[tasks."verify:tail-sampling"]` block at the same position as `verify:datasources` / `verify:images`. The existing `[tasks.load]` block is unchanged (load.sh extension is back-compat).
- **`grafana/dashboards/ose-otel-demo.json`** — additive edit only (NEW collapsed row at the bottom; no edits to existing panels). Per D-T16, top of the new row's panel `description` field carries a JSDoc-style note about the policy-name contract with otelcol-config.yaml.
- **`README.md`** — additive edit (~100-150 new lines): new §11 section after the §10 section. New screenshot files in `docs/screenshots/`.
- **`docs/screenshots/step-11-tail-sampling-OFF.png`** + **`docs/screenshots/step-11-tail-sampling-ON.png`** — NEW files, manually captured one-shot per D-T9.
- **`OtelSdkConfiguration.java` (both services)** — UNTOUCHED. This phase's TSAMP-01 spec explicitly mandates SDK stays at `Sampler.parentBased(Sampler.alwaysOn())`.
- **`docker-compose.yml`** — UNTOUCHED. The otel-collector service block needs no changes; the bind-mounted `infra/observability/otelcol-config.yaml` change is picked up by `docker compose restart otel-collector`.

</code_context>

<specifics>
## Specific Ideas

- The user's pattern across Areas 1-2 was **"production-realistic over workshop-minimal"** — composite envelope with rate-limits (D-T2/D-T3) over flat list, real WIDGET-SLOW SKU branch (D-T5) over inert latency policy. Planner should default to the higher-fidelity teaching surface on any silent question, not the simpler shape.
- The user's pattern across Areas 3-4 was **"reuse existing v1.0/Phase-10 mechanics verbatim where possible"** — manual one-shot screenshots (D-T9 mirrors D-13), verify-task pattern (D-T14 mirrors D-03/D-14), README structural depth (D-T11 mirrors Phase 10 §10), HTML `<table>` paired screenshots (mirrors Phase 7 D-04). Planner should default to "what did Phase 10/Phase 7 do here?" on any silent question.
- The OR-semantics narrative is the LOAD-BEARING teaching artifact for this phase. Three places must agree verbatim or by direct quote: (a) the YAML comment block above `composite:` (D-T4), (b) README §11 sub-section "OR-semantics priority chain explained" (D-T11), (c) the F2-3 blockquote (D-T12). Planner should write the YAML block first (it's the authoritative source per D-04 "YAML is the textbook"), then quote it in the README.
- Policy names `keep-errors` / `keep-slow` / `probabilistic-fallback` (D-T15) are a stable contract referenced by THREE artifacts: the YAML, the dashboard JSON, and the verify:tail-sampling task. Renaming any one of them requires touching all three. The drift guard (D-T14 + D-T16) catches this in CI-style fast-fail; document the contract in the YAML comment block and the dashboard row JSDoc.
- The OFF screenshot (D-T9) MUST be captured BEFORE Phase 11's plans land — it's the "before" state, and once tail_sampling is in the YAML, you can't easily reconstruct the OFF state on `main`. Plan the OFF screenshot capture as either a Wave 0 task (capture on `main` HEAD before any plan) OR as a quick-task that lands separately on `main` before the Phase 11 PR opens. Planner should make this explicit.

</specifics>

<deferred>
## Deferred Ideas

- **Multi-collector tail sampling with loadbalancing exporter** — production-realistic but requires sticky routing (the `loadbalancing` exporter), which is a production concern. Anti-Pattern 1 in research/ARCHITECTURE.md explicitly excludes it from the workshop. Defer to a future "infra deepdive" milestone if ever revisited.
- **`drop`-decision policy for excluding /actuator/health traces** — research/PITFALLS.md F2-1 uses health-check exclusion as the canonical example of `drop`-policy use. The workshop's producer service uses Spring Actuator (Phase 1) so health-check traces ARE in the trace stream. Adding a `drop` policy demonstrates the four-tier priority chain with a real example, but it's a fourth policy beyond TSAMP-01's three. Defer to a future "tail sampling deepdive" lesson or to v2.x scope.
- **Synthetic high-latency request scenario producing P99 exemplar dot demo** — listed in REQUIREMENTS.md "Differentiators / polish" section. Phase 11's WIDGET-SLOW branch (D-T5) partially fulfills the synthetic-slow-request shape; future polish could pair with Phase 12 exemplar work to produce a P99 exemplar dot specifically.
- **Test coverage for tail_sampling rate-limit caps** — discussed but no integration test added (Collector-side processor is not unit-testable from the Java side). Defer to a future Collector-side IT (would require a Testcontainers Collector with custom config; significant complexity for a workshop). The verify:tail-sampling gate (D-T14) is the runtime substitute.
- **Automated screenshot capture for ON/OFF pair** — D-T9 keeps the manual one-shot pattern (D-13 lineage). A future quick-task could automate via Playwright (the existing scripts/screenshots/capture.mjs is the structural template) but Phase 7-07 documented why this is fragile across older tags. Revisit if the screenshot becomes stale.
- **Collector self-metrics dashboard panel for ALL processors (not just tail_sampling)** — D-T13 adds a row for tail_sampling specifically. A broader "Collector internals" row covering memory_limiter, batch, OTLP receiver/exporter metrics could be a future polish item but isn't load-bearing for Phase 11's lesson.
- **README anchor IDs for cross-linking from Phase 16** — D-T12 forward-flags the F2-3 callout. When Phase 16 lands, its README §16 callout needs a stable anchor to link back to. Planner can choose anchor naming convention (e.g., `#double-filter-trap`) at plan time; not a Phase 11 blocker but worth noting.
- **`mise run verify:dashboard-tail-sampling-row` task** — option B from D-T16's question. Belt+suspenders+belt would be a second verify task that loads ose-otel-demo.json and asserts the row's queries reference the three policy names. Phase 11 chose belt+suspenders (verify:tail-sampling + dashboard JSDoc) over belt+suspenders+belt; revisit if dashboard drift becomes a real problem.

### Reviewed Todos (not folded)

None — `cross_reference_todos` step did not surface matches for Phase 11 scope.

</deferred>

---

*Phase: 11-tail-sampling-at-the-collector*
*Context gathered: 2026-05-02*
