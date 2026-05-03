# Phase 11: Tail Sampling at the Collector — Research

**Researched:** 2026-05-02
**Domain:** OpenTelemetry Collector contrib v0.151.0 `tailsamplingprocessor` configuration; OTel Java SDK `Thread.sleep` inside an INTERNAL span scope; Grafana dashboard PromQL against Collector self-metrics on `:8888/metrics`.
**Confidence:** HIGH on every YAML key, every metric series name, and every label set (verified against the v0.151.0 git tag — see Confidence Assessment table). MEDIUM on the unit-suffix exposition (curly-bracket `{traces}` annotation handling — fix landed in OTel-Go after a known regression, see §3.2 below; reconfirm at first live `curl :8888/metrics` once Phase 11 lands).

> **HEADLINE FINDING — TWO LOAD-BEARING CORRECTIONS NEEDED VS CONTEXT.md.** Researcher does NOT re-decide the locked decisions, but flags two upstream-fact mismatches that the planner MUST resolve with the operator:
> 1. **D-T13 panels 2/3/4 cite metric series that do not exist by those names.** The actual v0.151.0 series are `..._count_spans_sampled` (gated by an alpha feature gate; not enabled by default), `..._sampling_late_span_age` (histogram in seconds, not a `_total` counter), and `..._sampling_decision_timer_latency_bucket` (note the `sampling_` prefix). See §3.1.
> 2. **D-T15 + D-T13 + D-T14 assume the `policy=` label on `count_traces_sampled` will carry `keep-errors` / `keep-slow` / `probabilistic-fallback`.** With the D-T2 composite envelope, the `policy=` label carries the SINGLE outer composite policy name (one series, not three) — sub-policy attribution requires the alpha `processor.tailsamplingprocessor.recordpolicy` feature gate which surfaces sub-policy names as SPAN ATTRIBUTES (`tailsampling.composite_policy`), NOT as metric labels. See §2.3.
>
> Both corrections preserve every locked CONTEXT.md decision; they only force the planner to make ONE structural choice with the operator: **(A)** keep composite as locked and rewrite the dashboard panel + verify task to match composite's single-series reality, OR **(B)** flatten the three policies to top-level (giving up the per-branch rate-limit lesson) so the per-policy series the dashboard wants actually exist. See §2.3 for both routes fully spec'd.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Tail-sampling pipeline + policy structure**

- **D-T1:** Pipeline placement = `[memory_limiter, tail_sampling, batch]` — canonical OTel order (matches research/ARCHITECTURE.md line 185 verbatim). Tail-sampling buffers full traces internally regardless of placement, but emitting BEFORE batch means the batch processor only ever sees KEPT traces (~30% of incoming volume). The misleading forecast comment in `infra/observability/otelcol-config.yaml:180` (`# Phase 11 inserts tail_sampling between batch and the exporter`) is corrected as part of this phase.
- **D-T2:** Composite policy structure — TSAMP-01's three policies are nested under a `composite` envelope rather than written as a flat top-level list. Adds a per-branch rate-limit lesson on top of the OR-semantics lesson; production-realistic. Composite still enforces OR-semantics across sub-policies, but each sub-policy hits its own rate-limit BEFORE casting its vote.
- **D-T3:** Composite per-branch rate-limits = `errors=unlimited`, `slow=20/s`, `prob=40/s` — production logic ("ALWAYS keep errors") + tight caps on the fallback policies so bursts visibly clamp.
- **D-T4:** TSAMP-02 OR-semantics narrative placement — Big comment block above the composite block + per-branch sub-comments per sub-policy. Top block explains the four-tier priority chain (`drop > inverted_not_sample > sample > inverted_sample`) AND the "composite envelope: per-branch rate-limit applies BEFORE the sample/not_sample vote" addendum. ~25-line comment block total. Matches Phase 10 D-04 teaching-grade YAML style.
- **D-T15:** Policy names = `keep-errors`, `keep-slow`, `probabilistic-fallback` — self-documenting verb-style names.

**Latency-policy realism (synthetic slow-trace generation)**

- **D-T5:** WIDGET-SLOW SKU branch in producer — adds a tiny branch in `OrderService.place()`: if `sku == "WIDGET-SLOW"`, `Thread.sleep(1500)` inside the INTERNAL span. ~5 lines of Java + JavaDoc.
- **D-T6:** Sleep location + duration: producer INTERNAL span, 1500ms — span duration > 1.5s, latency policy fires reliably above the 1000ms threshold (50% buffer). Tail-sampling's `latency` policy uses MAX-span duration of the assembled trace, so producer-side sleep alone is sufficient; consumer span stays fast (~10ms). Smallest code change.
- **D-T7:** load.sh adds `SLOW_RPS` env var (default 2, set to 0 to disable) — fourth steady oha stream verbatim mirroring the IDEMPOTENT_RPS pattern. Heartbeat output adds a 'slow' line. ~2 rps means ≈20 slow traces in a 10s decision window.
- **D-T8:** WIDGET-SLOW shares the existing `AtomicInteger counter` — slow traces hit the Phase 3 deterministic 10%-failure path on roughly every 10th call. Those traces match BOTH `status_code=ERROR` AND `latency>1s` — a perfect demonstration of OR-semantics (a trace can match multiple branches; gets sampled once). Zero changes to ProcessingService.

**ON/OFF demo mechanism**

- **D-T9:** Git-tag time-travel screenshot pair — OFF screenshot captured manually NOW on `main` HEAD pre-Phase-11 (`docs/screenshots/step-11-tail-sampling-OFF.png`); ON screenshot captured manually post-Phase-11 (`docs/screenshots/step-11-tail-sampling-ON.png`). Mirrors Phase 10 D-13 verbatim.
- **D-T10:** Screenshot subject = Tempo Search "Last 5 min" trace count, identical query — `Service Name = order-producer`, same time range, captured twice.
- **D-T11:** README §11 narrative depth = Phase-10-equivalent (~100-150 lines).
- **D-T12:** F2-3 head-vs-tail callout = inline blockquote at the end of README §11.

**Self-observability of tail_sampling**

- **D-T13:** New collapsed "Tail Sampling diagnostics" row added to `grafana/dashboards/ose-otel-demo.json` — 4 panels (per-policy sampled, drop counter, late-span, decision latency histogram). Collapsed by default. Phase 11 is the FIRST v2.0 phase to amend ose-otel-demo.json; the row is a discrete additive block (no edits to existing panels, no UID changes). **Researcher flag — see §3.1: panels 2/3/4 cite metric names that do not exist.**
- **D-T14:** `mise run verify:tail-sampling` task — curls `http://localhost:8888/metrics`, greps for `otelcol_processor_tail_sampling_count_traces_sampled` AND asserts that the `policy=` label values include all three configured policy names. Mirrors `verify:datasources` / `verify:images` naming. ~15 lines of bash. **Researcher flag — see §2.3: with composite envelope as locked (D-T2), the `policy=` label carries the OUTER composite name only (one series), not three.**
- **D-T16:** Belt + suspenders drift guard — JSDoc-style top comment in the ose-otel-demo.json tail_sampling row noting "policy names are a contract with infra/observability/otelcol-config.yaml — see `mise run verify:tail-sampling`".

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact YAML key shape for the `composite` policy → §2.1, §2.2 (verbatim from v0.151.0 README + composite_helper.go).
- Exact JavaDoc wording on the WIDGET-SLOW branch → §4.2 (template provided).
- `Thread.sleep(1500)` vs alternatives → §4.1 (Thread.sleep is correct; no SDK-side timing pitfall).
- Exact PromQL phrasing for the four diagnostic panels → §3.1, §3.3 (verbatim queries provided).
- Exact `verify:tail-sampling` bash regex → §3.4 (verbatim bash provided, both Route A and Route B variants).
- README §11 word-for-word phrasing → out of researcher scope; PITFALLS.md F2-1 wording paraphrased per D-T11.
- Whether the OFF screenshot needs a separate mise task → manual one-shot per D-T9 (no automation).
- Plan count + wave structure → out of researcher scope (planner decides).

### Deferred Ideas (OUT OF SCOPE)

- Multi-collector tail sampling with loadbalancing exporter (production-realistic but requires sticky routing).
- `drop`-decision policy for excluding /actuator/health traces (deferred to a future "tail sampling deepdive" lesson).
- Synthetic high-latency request scenario producing P99 exemplar dot demo (paired with Phase 12 exemplar work).
- Test coverage for tail_sampling rate-limit caps (Collector-side processor not unit-testable from Java side).
- Automated screenshot capture for ON/OFF pair (D-T9 keeps manual one-shot per D-13 lineage).
- Collector self-metrics dashboard panel for ALL processors.
- README anchor IDs for cross-linking from Phase 16 (planner can choose at plan time).
- `mise run verify:dashboard-tail-sampling-row` task (option B from D-T16's question).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description (from REQUIREMENTS.md) | Research Support |
|----|------------------------------------|------------------|
| TSAMP-01 | The Collector's traces pipeline includes a `tail_sampling` processor with three policies (in this order): `status_code ERROR` keep-100%, `latency` keep-100% above 1s, probabilistic `20%` fallback; `decision_wait: 10s` and `num_traces: 10000`. | §2.1 (verbatim composite YAML from v0.151.0 README), §2.5 (decision_wait + num_traces sized correctly for ~200 rps workshop load — no hidden caps trigger drops). |
| TSAMP-02 | The Collector config file contains an explicit comment block documenting the OR-semantics priority chain (`drop > inverted_not_sample > sample > inverted_sample`). | §2.4 (verbatim quote from v0.151.0 README "Policy Decision Flow" section + the explicit "inside `composite`/`and` the inverted decisions collapse to plain Sampled/NotSampled" caveat the README spells out). |
| TSAMP-03 | Workshop attendee runs `scripts/load.sh`, observes that every error-status trace is preserved AND non-error trace volume drops to ~20% of the pre-tail-sampling baseline; `step-XX-tail-sampling.png` README screenshot pairs ON-vs-OFF trace counts. | §2.5 (load math — at default 200 rps + 10% errors, errors stay uncapped at ~20 traces/s, slow stays under 20/s cap, prob fires at ~20% of remainder), §5.1 (ON/OFF screenshot mechanism — manual one-shot per D-T9). |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

The repo's `CLAUDE.md` documents Phase 11 stack constraints already locked in CONTEXT.md (collector-contrib v0.151.0, single-Collector, no Alloy, manual SDK only). The following directives apply to ALL planning for this phase:

- **GSD Workflow Enforcement** — direct repo edits outside a GSD command are prohibited. Phase 11 plans land via `/gsd-execute-phase`.
- **Stack pins** — `collector-contrib:0.151.0` is non-negotiable; do NOT recommend an upgrade even if v0.152+ ships before plan execution. Spring Boot 3.4.13, Java 17, Maven 3.9.x are also locked.
- **TRACE-01 / DOC-05** — `OtelSdkConfiguration` is intentionally per-service duplicated. The WIDGET-SLOW branch lives in `OrderService.place()` (domain layer); it does NOT touch `OtelSdkConfiguration`.
- **TSAMP-01 SDK invariant** — SDK sampler stays `Sampler.parentBased(Sampler.alwaysOn())`. Head sampling lands in Phase 16.
- **No Alloy / no `loadbalancing` exporter / no Micrometer bridge** — single-Collector, manual OTel SDK only.

## Summary

Phase 11 is a **single-file Collector configuration change** plus a **5-line Java domain edit** plus a **load-script extension** plus a **dashboard row + verify task + README section + screenshot pair**. The locked decisions in 11-CONTEXT.md are correct on every architectural axis — placement (D-T1), policy choice (TSAMP-01), policy names (D-T15), sleep mechanism (D-T5/D-T6), screenshot mechanism (D-T9), README depth (D-T11), and the F2-3 callout (D-T12).

The research uncovered TWO load-bearing upstream-fact mismatches with CONTEXT.md that the planner MUST surface to the operator before writing plans — both are in the self-observability surface (D-T13 dashboard row + D-T14 verify task), and BOTH stem from the composite envelope (D-T2) collapsing per-sub-policy metric attribution into a single outer-composite series:

1. **Three of the four panels in D-T13 cite metric names that don't exist.** v0.151.0's actual canonical metric names are documented at §3.1 with verbatim PromQL replacements that preserve the panel intent.
2. **The `verify:tail-sampling` (D-T14) per-policy assertion will fail** because the composite envelope produces ONE `policy=composite-policy-1` series, not three `policy=keep-errors|keep-slow|probabilistic-fallback` series. Two routes to fix: (A) keep composite + rewrite verify task to assert the outer-composite name + the alpha `recordpolicy` feature gate so sub-policy names land on spans (verifiable via Tempo, not metrics); (B) flatten policies to top-level (gives up rate-limit lesson but preserves three-series dashboard). See §2.3.

Everything else verifies clean. The composite YAML keys (`composite_sub_policy`, `policy_order`, `rate_allocation`, `max_total_spans_per_second`, `percent`, `policy`) are correct as cited in v0.151.0 README. The OR-semantics priority chain text is verbatim quotable from the README. `decision_wait: 10s` + `num_traces: 10000` is well-sized for workshop load. `Thread.sleep(1500)` inside `try (Scope scope = span.makeCurrent())` correctly stretches the span end-time — `span.end()` runs in the `finally` block AFTER sleep AFTER the publish, and uses wall-clock time so the 1.5s extends the span (no SDK-side gotcha).

**Primary recommendation:** Planner should bring the operator BOTH §3.1 panel rewrites AND §2.3 Route A/B before Wave 0 to lock the dashboard + verify-task contract; everything else is mechanically described below.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tail-sampling decision (which traces reach Tempo) | **Collector** (`tail_sampling` processor) | — | TSAMP-01 + ARCHITECTURE.md Feature 2: SDK stays `alwaysOn`; the *Collector* sees the assembled trace and decides. Hard invariant — F2-3 (head + tail double-filter trap) ensures SDK sampler is NOT touched here. |
| Synthetic slow-trace generation | **Producer service** (Java domain layer, `OrderService.place()`) | — | D-T5: latency policy needs traces > 1s to fire reliably; producer's INTERNAL span is the cleanest place to inject the sleep. Adding it at the Collector via OTTL would defeat "this is a Java app emitting real load" lesson. |
| Slow-traffic load generation | **Workshop tooling** (`scripts/load.sh`) | — | D-T7: a fourth oha stream is the simplest mechanism; mirrors IDEMPOTENT_RPS pattern. |
| Per-policy decision visibility | **Collector self-metrics** scraped by Mimir → Grafana panel | **Tempo span attributes** (sub-policy attribution, alpha gate `recordpolicy`) | D-T13 + Phase 10 IN-06 (otelcol self-scrape job already in place). Sub-policy attribution requires either flat policies (route B) or a span-attribute fallback via the `recordpolicy` feature gate (route A). See §2.3. |
| Drift guard (policy name contract) | **`mise run verify:tail-sampling`** (curl + grep + assertion) | Dashboard JSDoc comment | D-T14 + D-T16. Mirrors verify:datasources / verify:images pattern. |
| ON/OFF demonstration | **Manual screenshot capture** + README HTML `<table>` | — | D-T9 / D-T10 / D-T11. Mirrors Phase 10 D-13 + Phase 7 D-04 paired-screenshot precedent. No runtime toggle in YAML. |

## Standard Stack

This phase adds NO new library or container. Every artifact is already in scope:

| Component | Pinned Version | Source | Purpose in Phase 11 |
|-----------|---------------|--------|---------------------|
| `otel/opentelemetry-collector-contrib` | `0.151.0` | docker-compose.yml:33 (Phase 10) | Hosts the `tail_sampling` processor (contrib-only component) |
| OpenTelemetry Java SDK API | `1.61.0` (BOM-managed) | producer-service pom (Phase 2) | `Thread.sleep(1500)` inside `tracer.spanBuilder(...).startSpan()` scope — no new API |
| Spring Boot | `3.4.13` | parent pom (Phase 1) | `OrderService.place()` is `@Service`-annotated; no new framework surface |
| `oha` | `1.14.0` | mise.toml:16 | Fourth load stream uses oha (fixed payload) per D-T7 |
| Grafana | `13.0.1` | docker-compose.yml (Phase 10) | New collapsed dashboard row uses existing Prometheus datasource |

**No installation step, no `mvn install`, no `docker compose pull` required.** All artifacts are already pulled and pinned by Phase 10.

## 1 — Pipeline Placement Verification (D-T1)

The locked decision puts `tail_sampling` BEFORE `batch`: `[memory_limiter, tail_sampling, batch]`. This is upstream-canonical.

**Source:** v0.151.0 README, "Tail Sampling Processor" intro paragraph and the canonical `composite-policy-1` example shown under `processors.tail_sampling.policies`. The README does not prescribe pipeline order beyond "This processor must be placed in pipelines after any processors that rely on context, e.g. `k8sattributes`. It reassembles spans into new batches, causing them to lose their original context." `[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md]`

**Why BEFORE batch is correct (D-T1 rationale verification):** The README explicitly notes tail_sampling "reassembles spans into new batches" — running `batch` AFTER `tail_sampling` lets `batch` see only the kept traces, which is the whole efficiency point. Running `batch` BEFORE tail_sampling would have the batch processor compose batches that are immediately torn apart by tail_sampling's per-trace buffering. ARCHITECTURE.md line 185 also documents this order verbatim.

**Required correction to existing file:**

`infra/observability/otelcol-config.yaml:180` currently says:
```yaml
    # WHY: traces — apps emit OTLP → memory_limiter → batch → Tempo via OTLP HTTP.
    # Phase 11 inserts tail_sampling between batch and the exporter.
```

This is wrong per D-T1. The corrected wording (write this verbatim into the YAML when Phase 11 lands):
```yaml
    # WHY: traces — apps emit OTLP → memory_limiter → tail_sampling → batch → Tempo
    # via OTLP HTTP. tail_sampling is placed BEFORE batch (canonical OTel order:
    # see https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md
    # — "This processor … reassembles spans into new batches, causing them to lose
    # their original context"). Running batch AFTER tail_sampling means the batch
    # processor only ever sees KEPT traces (~30% of incoming volume at workshop
    # load), so batches are smaller and arrive at Tempo more efficiently.
```

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md]`

## 2 — Composite Policy YAML (D-T2 / D-T3 / D-T4)

### 2.1 Verbatim composite YAML keys (HIGH confidence)

The exact key names confirmed against the v0.151.0 README example block AND the `composite_helper.go` source:

| YAML Key | Type | Required? | Notes |
|----------|------|-----------|-------|
| `composite` | object | yes | Outer wrapper (sibling to `name:` and `type: composite`) |
| `composite.max_total_spans_per_second` | int (spans/s) | yes | **Note unit: SPANS per second, not traces** — see §2.5 for the load-math impact |
| `composite.policy_order` | array of strings | yes | Order in which sub-policies are evaluated; matches sub-policy `name:` values verbatim |
| `composite.composite_sub_policy` | array of objects | yes | The sub-policy definitions; each carries `name:`, `type:`, and the type-specific config block (`status_code:`, `latency:`, `probabilistic:`) |
| `composite.rate_allocation` | array of `{policy: <name>, percent: <0-100>}` | optional | Allocates a percentage of `max_total_spans_per_second` to each named sub-policy; sub-policies omitted from `rate_allocation` get the default share (`max_total_spans_per_second / N` per `composite_helper.go:getRateAllocationMap`) |
| `composite.rate_allocation[].policy` | string | yes (within rate_allocation entry) | Must match a sub-policy `name:` |
| `composite.rate_allocation[].percent` | int (0-100) | yes (within rate_allocation entry) | Percentage of `max_total_spans_per_second` |

**Sub-policy YAML keys (each in the `composite_sub_policy` array):**

| Sub-policy type | YAML Key | Field |
|----------------|----------|-------|
| `status_code` | `status_code: {status_codes: [ERROR]}` | List; valid values are `OK`, `ERROR`, `UNSET` |
| `latency` | `latency: {threshold_ms: 1000}` | int milliseconds. Optional `upper_threshold_ms:` for ranged matches; omit for "anything above threshold" |
| `probabilistic` | `probabilistic: {sampling_percentage: 20}` | int (0-100). Optional `hash_salt:` for deterministic salting (not needed here) |

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md (lines containing "composite-policy-1" example) AND blob/v0.151.0/processor/tailsamplingprocessor/composite_helper.go (lines 14-49: NewComposite + getRateAllocationMap signatures)]`

### 2.2 Verbatim YAML block ready for paste into otelcol-config.yaml

> **Planner — paste this verbatim** into `infra/observability/otelcol-config.yaml` under the `processors:` block (between `memory_limiter:` and `batch:` per D-T1). Comment density matches Phase 10 D-04 teaching-grade style. ~25-line comment block above composite per D-T4.

```yaml
  # ──────────────────────────────────────────────────────────────────
  # tail_sampling — Phase 11 / TSAMP-01..03 (composite envelope, D-T2)
  # ──────────────────────────────────────────────────────────────────
  #
  # WHY this processor exists: the SDK exports 100% of spans at
  # parentBased(alwaysOn()) (TSAMP invariant — head sampling lives in
  # Phase 16, never here). The Collector buffers each trace for
  # decision_wait, then DECIDES which complete traces reach Tempo based
  # on the assembled trace's full content (status_code, total latency,
  # probabilistic roll). This is the headline differentiator from head
  # sampling: the decision uses information that does not exist at span
  # creation time.
  #
  # WHY composite envelope (D-T2): wraps the three TSAMP-01 policies in
  # a single outer block that adds two production-realistic teaching
  # surfaces on top of the OR-semantics lesson:
  #   1. Per-branch RATE-LIMITS — each sub-policy has its own
  #      max-spans-per-second cap; bursts visibly clamp.
  #   2. policy_order — sub-policies are evaluated in the listed order;
  #      composite returns SAMPLED on the FIRST sub-policy that votes
  #      sample within its rate cap (so a trace that is BOTH error AND
  #      slow is sampled exactly ONCE — matched by keep-errors first,
  #      keep-slow never gets a vote — see D-T8).
  #
  # WHY the four-tier OR-semantics priority chain (TSAMP-02, F2-1):
  # the upstream tail_sampling processor evaluates ALL top-level
  # policies and applies a priority decision logic — NOT first-match.
  # Verbatim from the v0.151.0 README "Policy Decision Flow" section:
  #
  #   "When there's a 'drop' decision, the trace is not sampled;
  #    When there's an 'inverted not sample' decision, the trace is
  #      not sampled (deprecated);
  #    When there's a 'sample' decision, the trace is sampled;
  #    When there's an 'inverted sample' decision and no 'not sample'
  #      decisions, the trace is sampled (deprecated);
  #    In all other cases, the trace is NOT sampled."
  #
  # NOTE the composite-specific addendum from the same README section:
  # "if the policy is within an `and` or `composite` policy, the
  # resulting decision will be either sampled or not sampled" — i.e.,
  # inverted decisions collapse inside composite/and. With our config,
  # the priority chain reduces to "ANY top-level sample wins" because
  # we use no `drop` policies (drop-policy for /actuator/health is
  # deferred per CONTEXT.md).
  #
  # decision_wait + num_traces sizing (F2-2 mitigation): at workshop
  # load (~200 rps steady, ~10s window = ~2000 trace-fragments in flight),
  # 10000 buffer slots give 5x headroom. decision_wait=10s vs default
  # 30s is the workshop-responsiveness tradeoff (attendees see traces
  # within ~10s of POSTing).
  tail_sampling:
    decision_wait: 10s
    num_traces: 10000
    expected_new_traces_per_sec: 50
    policies:
      - name: composite-policy
        type: composite
        composite:
          # WHY 1000 spans/s ceiling: at workshop load (~200 rps × ~10
          # spans per trace = ~2000 spans/s if everything were sampled),
          # 1000 is half — enough to clamp visibly under burst, generous
          # enough that steady load never hits the global ceiling.
          # NOTE the unit is SPANS per second, NOT traces per second.
          max_total_spans_per_second: 1000
          # WHY this order: composite evaluates sub-policies in
          # policy_order and STOPS at the first vote-to-sample (within
          # rate cap). Errors are the highest-priority signal — they
          # must always survive. Slow second — never drop a slow trace
          # under workshop conditions. Probabilistic last — the
          # "representative sample of normal traffic" backstop.
          policy_order: [keep-errors, keep-slow, probabilistic-fallback]
          composite_sub_policy:
            # WHY keep-errors: production rule #1 — never drop a trace
            # that contains an error span. v1.0's TRACE-09 sets
            # span.setStatus(StatusCode.ERROR) on the deterministic
            # 10% failure path (every 10th order at consumer side).
            - name: keep-errors
              type: status_code
              status_code:
                status_codes: [ERROR]
            # WHY keep-slow: production rule #2 — never drop a trace
            # whose total duration exceeds the SLO threshold. Phase 11
            # adds a WIDGET-SLOW SKU branch in OrderService.place()
            # that Thread.sleep(1500)s inside the producer INTERNAL
            # span — the trace's MAX-span duration always > 1s.
            - name: keep-slow
              type: latency
              latency:
                threshold_ms: 1000
            # WHY probabilistic-fallback: 20% of remaining traces
            # (matches TSAMP-03 "approximately 20%" target). Tempo
            # never goes empty during workshop demos.
            - name: probabilistic-fallback
              type: probabilistic
              probabilistic:
                sampling_percentage: 20
          # WHY rate_allocation: each sub-policy's vote is GATED by
          # its own spans-per-second budget BEFORE composite returns.
          # If a sub-policy's budget is exhausted in the current 1s
          # window, composite treats its vote as not-sample and
          # continues to the next sub-policy in policy_order.
          #   - keep-errors: 100% of max_total_spans_per_second = 1000
          #     spans/s. At ~10 spans per error trace, that's ~100
          #     error traces/s — well above the workshop's actual ~20
          #     errors/s. EFFECTIVELY UNLIMITED for workshop load.
          #   - keep-slow: 20 spans/s = ~2 traces/s at ~10 spans each
          #     (matches SLOW_RPS=2 default). Slow traces beyond the
          #     cap fall through to probabilistic.
          #   - probabilistic-fallback: 40 spans/s = ~4 traces/s. ~20%
          #     of the remaining ~180 rps non-error non-slow load.
          rate_allocation:
            - policy: keep-errors
              percent: 100
            - policy: keep-slow
              percent: 2
            - policy: probabilistic-fallback
              percent: 4
```

> **Planner cross-check:** the `percent` field is a percentage of `max_total_spans_per_second` (1000), so `percent: 2` = 20 spans/s, `percent: 4` = 40 spans/s, `percent: 100` = unlimited (= the global cap). This matches D-T3's intent verbatim. If the operator wants to express the budgets as raw spans/s rather than percentages, ALL three values would have to add up to ≤100% of the global cap — composite does NOT pro-rate; it enforces each individually.

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/internal/sampling/composite.go (lines 96-128: Evaluate function — sub-policies iterated in order; first sample-vote within rate cap returns Sampled)]`

### 2.3 ⚠ Composite policy interaction with the `policy=` metric label — CRITICAL FINDING

**The CONTEXT.md plan assumes** that `count_traces_sampled{policy="keep-errors"}`, `{policy="keep-slow"}`, and `{policy="probabilistic-fallback"}` will all appear as separate Prometheus series, and that the `mise run verify:tail-sampling` task (D-T14) can grep for all three policy names.

**Upstream reality (v0.151.0):** When sub-policies are nested inside a `composite` envelope, the `policy=` attribute on `count_traces_sampled` carries the **outer composite policy's name** (e.g., `policy="composite-policy"`), NOT the sub-policy names. Composite is registered as a single top-level policy in the processor's `tsp.policies` slice.

**Source — `processor.go` v0.151.0:**

```go
// loadSamplingPolicies (lines 257-290): one *policy entry per top-level cfg
// policies := make([]*policy, 0, cLen)
// for _, cfg := range cfgs {
//     p := &policy{
//         name: cfg.Name,
//         attribute: metric.WithAttributes(attribute.String("policy", uniquePolicyName)),
//     }
// }

// recordPerPolicyEvaluationMetrics (lines ~510): iterates top-level policies only
// for i, p := range tsp.policies {
//     for decision, stats := range metrics.tracesSampledByPolicyDecision[i] {
//         tsp.telemetry.ProcessorTailSamplingCountTracesSampled.Add(
//             tsp.ctx, int64(stats.tracesSampled),
//             p.attribute,                  // ← outer composite name
//             decisionToAttributes[decision])
//     }
// }
```

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/processor.go (recordPerPolicyEvaluationMetrics function)]`

**Sub-policy attribution exists but as a SPAN ATTRIBUTE, not a metric label.** v0.151.0 ships an alpha feature gate `processor.tailsamplingprocessor.recordpolicy` (added in v0.120.0; still alpha at v0.151.0 per `metadata.yaml`). When enabled, sampled spans carry:
- `tailsampling.policy` — top-level policy name (always present unless decision came from cache)
- `tailsampling.composite_policy` — sub-policy name within composite (when composite policy used)
- `tailsampling.cached_decision` — bool (when decision cache used)

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md (section "Tracking sampling policy") AND blob/v0.151.0/processor/tailsamplingprocessor/metadata.yaml (feature_gates: "processor.tailsamplingprocessor.recordpolicy")]`

**Implication:** D-T13 panel 1 (`rate(otelcol_processor_tail_sampling_count_traces_sampled[1m])` by `policy` label) will produce ONE series labeled `policy="composite-policy"` — not three series. D-T14's verify task assertion ("the `policy=` label values include all three configured policy names") will FAIL out of the box.

#### Two routes to resolve (planner must surface to operator at Wave-0 plan-check):

**Route A — Keep composite (preserves D-T2 + D-T3 verbatim), accept the metric-label collapse, add the `recordpolicy` feature gate.**
- Composite YAML (§2.2) lands as-is.
- Add to docker-compose.yml `otel-collector` service `command:` array: `"--feature-gates=processor.tailsamplingprocessor.recordpolicy"` (alpha gate; opts in to span-attribute attribution).
- D-T13 panel 1 shows ONE series (`policy="composite-policy"`) — caption explains "composite collapses sub-policy attribution at the metric layer; see Tempo `tailsampling.composite_policy` span attribute for per-sub-policy view".
- D-T14 verify task asserts the OUTER composite policy name exists as a `policy=` label, AND additionally curls Tempo to verify a kept span carries the `tailsampling.composite_policy` attribute with one of the three expected values (heavier task — Tempo-side assertion).
- Pro: keeps the per-branch rate-limit lesson (D-T2/D-T3 intent preserved).
- Con: more complex verify task; alpha feature gate adds a fragility surface (gate could change name or default in v0.152+).

**Route B — Flatten policies to top-level (gives up rate-limit lesson, recovers three-series dashboard).**
- Replace the composite YAML with a flat top-level list (canonical CONTEXT.md "alternative considered"):
  ```yaml
  tail_sampling:
    decision_wait: 10s
    num_traces: 10000
    policies:
      - name: keep-errors
        type: status_code
        status_code: {status_codes: [ERROR]}
      - name: keep-slow
        type: latency
        latency: {threshold_ms: 1000}
      - name: probabilistic-fallback
        type: probabilistic
        probabilistic: {sampling_percentage: 20}
  ```
- D-T13 panel 1 produces THREE series naturally (`policy="keep-errors"`, `policy="keep-slow"`, `policy="probabilistic-fallback"`).
- D-T14 verify task asserts all three names verbatim (matches the CONTEXT.md intent).
- Pro: simpler verify task; no alpha feature gate needed; matches the CONTEXT.md dashboard plan as written.
- Con: gives up the per-branch rate-limit lesson (D-T2/D-T3 production-realism rationale). The "what happens when traffic bursts beyond the slow cap" demo moment disappears.

**Researcher recommendation (NOT a re-decision — for operator to confirm):** Route A — preserves the locked D-T2/D-T3 rate-limit lesson. The dashboard caption + verify-task complexity is a one-time author cost; the production-realism payoff is permanent. Phase 11's whole framing in CONTEXT.md is "production-realistic over workshop-minimal" (per `<specifics>` first bullet).

> **DOWNSTREAM TASK:** plan-checker should verify the operator confirmed Route A vs Route B before any plan touches `infra/observability/otelcol-config.yaml`. The choice cascades into 4 artifacts (otelcol-config.yaml, ose-otel-demo.json, mise.toml verify task, README §11). The discuss-phase note in 11-DISCUSSION-LOG.md should be updated.

### 2.4 OR-semantics priority chain — verbatim quote for D-T4 comment block

The TSAMP-02 spec requires the YAML to contain "an explicit comment block documenting the OR-semantics priority chain (`drop > inverted_not_sample > sample > inverted_sample`) so attendees understand why all policies evaluate rather than stopping at the first match."

**Verbatim quote from v0.151.0 README "Policy Decision Flow" section** (already embedded in §2.2's YAML comment block):

> Each policy will result in a decision, and the processor will evaluate them to make a final decision:
> - When there's a "drop" decision, the trace is not sampled;
> - When there's an "inverted not sample" decision, the trace is not sampled; ***Deprecated***
> - When there's a "sample" decision, the trace is sampled;
> - When there's a "inverted sample" decision and no "not sample" decisions, the trace is sampled; ***Deprecated***
> - In all other cases, the trace is NOT sampled

> An "inverted" decision is the one made based on the "invert_match" attribute, such as the one from the string, numeric or boolean tag policy. There is an exception to this if the policy is within an `and` or `composite` policy, the resulting decision will be either sampled or not sampled. The "inverted" decisions have been deprecated, please make use of either
> - the `drop` policy to explicitly not sample select traces, or
> - the `not` policy to sample based on the opposite of the sampling decision of a policy

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md (section "Policy Decision Flow")]`

**Note for D-T4 narrative — the "inverted decisions are deprecated" caveat:** The four-tier chain in TSAMP-02's wording (`drop > inverted_not_sample > sample > inverted_sample`) reflects what the chain LOOKED LIKE before the `v0.126.0` `disableinvertdecisions` feature gate landed. Inverted decisions still exist by default at v0.151.0 (so the four-tier description is accurate), but they're flagged as deprecated. The YAML comment block should reflect the deprecation status (per the v0.151.0 README); the README §11 narrative can either match verbatim or paraphrase. F2-1 in PITFALLS.md uses the same four-tier wording — Phase 11 narratives align across all three artifacts (YAML comment, README §11, F2-3 callout) per CONTEXT.md `<specifics>` bullet 3.

### 2.5 `decision_wait: 10s` vs `num_traces: 10000` interaction at workshop load — no hidden caps

**Concern from objective:** "at `load.sh` defaults (~200 rps steady), 10s of buffering = ~2000 trace-fragments in flight, well under 10000. Verify there are no hidden caps (max_traces_per_second per sub-policy in composite? memory-pressure eviction?) that would cause unexpected drops at this scale."

**Verification — composite_helper.go + composite.go + processor.go review at v0.151.0:**

1. **No hidden trace cap inside composite.** The only cap is `max_total_spans_per_second` (note: SPANS, not traces), and the per-sub-policy `MaxSpansPerSecond` (also SPANS). At ~200 rps × ~10 spans/trace = ~2000 spans/s incoming; with `max_total_spans_per_second: 1000`, composite caps the GLOBAL sampled span rate at 1000 (which is intentional — visible burst clamping per D-T3).
2. **Memory eviction by `num_traces`.** From the README "Dropped Traces" section: "A circular buffer is used to ensure the number of traces in-memory doesn't exceed `num_traces`. When a new trace arrives, the oldest trace is removed. This can cause a trace to be dropped before it's sampled." At 200 rps × 10s window = ~2000 in-flight traces vs `num_traces: 10000` → 5× headroom. Safe.
3. **`memory_limiter` upstream of `tail_sampling`.** Existing config in `infra/observability/otelcol-config.yaml` has `memory_limiter` first with `limit_percentage: 75`, `spike_limit_percentage: 15`. If tail_sampling buffers cause Collector heap to exceed 75%, `memory_limiter` returns `ErrTooManyMessages` to the receiver and upstream backs off. At workshop scale this is unlikely (10000 traces × ~10 spans × ~1KB/span ≈ 100MB; well within Collector's default 512MB heap).
4. **`expected_new_traces_per_sec: 50`** is an allocation hint for internal data structure sizing; setting it at 50 (matches TSAMP-01 spec / ARCHITECTURE.md line 136) is fine — it under-estimates the actual ~200 rps but the data structures grow dynamically anyway.

**Burst scenario.** `scripts/load.sh` BURST_RPS=150 default + steady 200 rps + future SLOW_RPS=2 → ~352 rps peak × 10s = ~3520 in-flight traces vs 10000 cap → still 2.8× headroom. No drops expected from `num_traces` even under burst.

**Conclusion:** No hidden caps. The 10s/10000 sizing is correct for workshop load. F2-2 mitigation is sound. `[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md (Monitoring and Tuning section, "Dropped Traces" subsection) AND blob/v0.151.0/processor/tailsamplingprocessor/processor.go]`

### 2.6 Late-span behavior — F2-2 panel-3 metric semantics

**Concern from objective:** "when a trace arrives with spans after `decision_wait` has elapsed and the decision is cached, what happens to those late spans? Are they stamped with the cached decision, dropped silently, counted in `late_span_total`?"

**Upstream answer — three scenarios (verbatim from v0.151.0 README "Late-Arriving Spans" section):**

> A span's arrival is considered "late" if it arrives after its trace's sampling decision is made. Late spans can cause different sampling decisions for different parts of the trace.
>
> There are two scenarios for late arriving spans:
> - **Scenario 1:** While the sampling decision of the trace remains in the circular buffer of `num_traces` length, the late spans inherit that decision. That means late spans do not influence the trace's sampling decision.
> - **Scenario 2:** (Default, no decision cache configured) After the sampling decision is removed from the buffer, it's as if this component has never seen the trace before: The late spans are buffered for `decision_wait` seconds and then a new sampling decision is made.
> - **Scenario 3:** (Decision cache is configured) When a "keep" decision is made on a trace, the trace ID is cached. The component will remember which trace IDs it sampled even after it releases the span data from memory. Unless it has been evicted from the cache after some time, it will remember the same "keep trace" decision.
>
> Occurrences of Scenario 1 where late spans are not sampled can be tracked with the below histogram metric.
> ```
> otelcol_processor_tail_sampling_sampling_late_span_age
> ```

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md (section "Late-Arriving Spans")]`

**For Phase 11's workshop config (no `decision_cache` configured = Scenario 2):** late spans that arrive after the buffer eviction get a fresh `decision_wait` window and a fresh policy evaluation. This is the "trace splits into two trace fragments" scenario the F2-2 panel surfaces. The metric to plot is **`otelcol_processor_tail_sampling_sampling_late_span_age_bucket`** (histogram in seconds; track p50/p99/+Inf to see lateness distribution). NOT `otelcol_processor_tail_sampling_late_span_total` as named in CONTEXT.md D-T13 — that name does not exist in v0.151.0. See §3.1 panel 3 for the corrected query.

**Caption candidate for the dashboard panel:** "Late spans (arriving after the trace's `decision_wait` window). Workshop config has no decision_cache → late spans trigger Scenario 2 (re-evaluation) per the v0.151.0 README. p99 close to `decision_wait` (10s) means traces are routinely splitting; consider increasing `decision_wait` or enabling `decision_cache.sampled_cache_size`."

## 3 — Self-observability surface (D-T13 / D-T14)

### 3.1 ⚠ Metric series names — DEFINITIVE LIST FROM v0.151.0 generated_telemetry.go

Researcher pulled the CANONICAL list of metric series this processor emits (the file is generated by `mdatagen` from `metadata.yaml`; both files cross-checked at the v0.151.0 tag).

**Every metric series the `tail_sampling` processor exposes on `:8888/metrics` (v0.151.0):**

| Metric name (verbatim from generated_telemetry.go) | Type | Unit | Attributes (Prometheus labels) | Notes |
|---------------------------------------------------|------|------|-------------------------------|-------|
| `otelcol_processor_tail_sampling_count_spans_sampled` | Sum (Int, monotonic) | `{spans}` | `policy`, `sampled`, `decision` | **Gated by alpha feature gate `processor.tailsamplingprocessor.metricstatcountspanssampled` — disabled by default. Will NOT appear unless the gate is enabled.** |
| `otelcol_processor_tail_sampling_count_traces_sampled` | Sum (Int, monotonic) | `{traces}` | `policy`, `sampled`, `decision` | **THE one D-T13 panel 1 + D-T14 verify task target.** Always emitted at telemetry level `basic` and above. |
| `otelcol_processor_tail_sampling_early_releases_from_cache_decision` | Sum (Int, monotonic) | `{spans}` | `sampled` | Only relevant when `decision_cache` is configured (we don't); will be 0. |
| `otelcol_processor_tail_sampling_global_count_traces_sampled` | Sum (Int, monotonic) | `{traces}` | `sampled`, `decision` | "Global" = aggregated across all top-level policies; one increment per trace (vs per-policy). |
| `otelcol_processor_tail_sampling_new_trace_id_received` | Sum (Int, monotonic) | `{traces}` | (none) | Counts arrival of new trace IDs to the processor. |
| `otelcol_processor_tail_sampling_sampling_decision_timer_latency` | Histogram (Int) | `ms` | (none) | Histogram of decision-loop wall-clock latency. Buckets: `[1, 2, 5, 10, 25, 50, 75, 100, 150, 200, 300, 400, 500, 750, 1000, 2000, 3000, 4000, 5000, 10000, 20000, 30000, 50000]` ms. |
| `otelcol_processor_tail_sampling_sampling_late_span_age` | Histogram (Int) | `s` | (none) | **THE metric for D-T13 panel 3.** Note unit is SECONDS. |
| `otelcol_processor_tail_sampling_sampling_policy_evaluation_error` | Sum (Int, monotonic) | `{errors}` | (none) | Catches per-policy panic / error during evaluation. |
| `otelcol_processor_tail_sampling_sampling_policy_execution_count` | Sum (Int, monotonic) | `{executions}` | `policy` | Per-policy execution count. |
| `otelcol_processor_tail_sampling_sampling_policy_execution_time_sum` | Sum (Int, monotonic) | `µs` | `policy` | Per-policy cumulative execution time (microseconds). |
| `otelcol_processor_tail_sampling_sampling_trace_dropped_too_early` | Sum (Int, monotonic) | `{traces}` | (none) | Increments when a trace is evicted from `num_traces` buffer BEFORE its `decision_wait` elapses. F2-2's "buffer too small" canary. |
| `otelcol_processor_tail_sampling_sampling_trace_removal_age` | Histogram (Int) | `s` | (none) | Time from arrival to removal — useful for tuning `decision_wait`. |
| `otelcol_processor_tail_sampling_sampling_traces_on_memory` | Gauge (Int) | `{traces}` | (none) | In-memory active trace count. Useful sanity gauge. |
| `otelcol_processor_tail_sampling_traces_dropped_too_large` | Sum (Int, monotonic) | `{traces}` | (none) | Only fires when `maximum_trace_size_bytes` is configured (we don't); will be 0. |

`[VERIFIED: github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/internal/metadata/generated_telemetry.go (lines 62-150) AND blob/v0.151.0/processor/tailsamplingprocessor/documentation.md AND blob/v0.151.0/processor/tailsamplingprocessor/metadata.yaml]`

**CONTEXT.md D-T13 panel-by-panel mapping table — actual vs assumed:**

| D-T13 Panel | CONTEXT.md cited name | Exists at v0.151.0? | Correct name + PromQL |
|-------------|----------------------|---------------------|------------------------|
| 1. Per-policy `traces_sampled` | `otelcol_processor_tail_sampling_count_traces_sampled` (with `policy=` label) | ✅ EXISTS — but composite collapses (§2.3) | See §3.3 panel 1 query |
| 2. Drop counter | `otelcol_processor_tail_sampling_count_spans_dropped` | ❌ DOES NOT EXIST | Closest semantic match: `count_traces_sampled{decision="not_sampled"}` (per-policy "vote-to-drop" rate). See §3.3 panel 2 query. The `count_spans_sampled` series Could fit but is gated by the `metricstatcountspanssampled` alpha feature gate. |
| 3. Late-span `_total` | `otelcol_processor_tail_sampling_late_span_total` | ❌ DOES NOT EXIST | Correct name: `otelcol_processor_tail_sampling_sampling_late_span_age` (note `sampling_` prefix; histogram in SECONDS, not a `_total` counter). See §3.3 panel 3 query. |
| 4. Decision-latency histogram | `otelcol_processor_tail_sampling_decision_latency_bucket` | ❌ DOES NOT EXIST by that name | Correct name: `otelcol_processor_tail_sampling_sampling_decision_timer_latency_bucket` (note `sampling_` prefix and `_timer_` infix). See §3.3 panel 4 query. |

### 3.2 Prometheus exposition format — `_total` suffix + `{traces}`-unit handling

**The collector self-telemetry block in `infra/observability/otelcol-config.yaml`** (lines 167-176) configures a Prometheus pull reader on `0.0.0.0:8888` at `level: detailed`. The OTel-Go SDK's Prometheus exporter (vendored as `go.opentelemetry.io/otel v1.43.0` per `processor/tailsamplingprocessor/go.mod`) applies the canonical OpenMetrics-style transformations:

1. **`_total` suffix added to all monotonic Sum metrics** (Prometheus convention). So `otelcol_processor_tail_sampling_count_traces_sampled` (Sum, monotonic) is exposed at `:8888/metrics` as **`otelcol_processor_tail_sampling_count_traces_sampled_total`**. `[VERIFIED: opentelemetry.io/docs/collector/internal-telemetry/]`
2. **Histogram suffixes:** `_bucket`, `_sum`, `_count`. The `sampling_decision_timer_latency` histogram appears as `*_milliseconds_bucket` (with `_milliseconds` unit-suffix; unit `ms` → `_milliseconds`). The `sampling_late_span_age` histogram (unit `s`) appears as `*_seconds_bucket`. **OR — depending on the unit-suffix bug fix status — they may appear without the seconds/milliseconds suffix.** See §3.2.1.
3. **Curly-bracket annotation units (`{traces}`, `{spans}`, `{errors}`, `{executions}`) are STRIPPED** per OpenMetrics convention — they're metadata, not real units. This is the OTel-Go fix landed in `exporters/prometheus/v0.59.1` (PR #7044, merged 2025-07-21); collector v0.151.0 ships OTel-Go v1.43.0 which is later than the fix. So `count_traces_sampled` appears as `count_traces_sampled_total` (NOT `count_traces_sampled__traces__total`). `[VERIFIED: github.com/open-telemetry/opentelemetry-go/pull/7044 (merged in milestone "Experimental Metrics v0.59.1")]`
4. **Attributes → Prometheus labels** verbatim. So `policy="composite-policy"`, `sampled="true"`, `decision="sampled"` become `{policy="composite-policy",sampled="true",decision="sampled"}`. Plus the standard collector resource labels: `service_instance_id`, `service_name="otelcol-contrib"`, `service_version`.

#### 3.2.1 Confidence note on unit-suffix exposition

**MEDIUM confidence:** The OTel-Go v1.43.0 vendored into collector contrib v0.151.0 is later than the fix-merge milestone (`exporters/prometheus/v0.59.1`, July 2025), and the `_total` suffix on monotonic sums is well-documented as default-on. However, the EXACT form of histogram unit suffixes (`_milliseconds_bucket` vs `_bucket`) was not 100% verifiable from documentation alone — the OTel-Go Prometheus exporter has shipped multiple toggles (`without_units`, `without_type_suffix`) and the default behavior moved at least once in 2025.

**Verification step — DO THIS at first live boot of Phase 11 plans:**
```bash
mise run infra:up
mise run dev   # in another terminal
mise run load  # in another terminal
sleep 30
curl -s http://localhost:8888/metrics | grep -E '^otelcol_processor_tail_sampling_' | sort -u
```

The output above is the source of truth. If `late_span_age_bucket` appears WITHOUT the `_seconds` infix, the dashboard PromQL in §3.3 should drop the `_seconds` infix; symmetric correction for the `_milliseconds` infix on `sampling_decision_timer_latency`. The §3.3 queries below assume the suffixes are PRESENT (canonical OpenMetrics behavior). If they are absent, drop them — function and panel intent are unchanged.

### 3.3 Verbatim PromQL for the four D-T13 panels

> **Planner — paste these verbatim** into `grafana/dashboards/ose-otel-demo.json`. Each panel is a discrete object inside a new collapsed `row` panel; structural template = the existing `Deeper-dive (post-workshop)` row at line 220 (`collapsed: true`, `gridPos.y: 21` to land below current row at y=10 + h=9). Datasource UID = `prometheus` per Phase 10 D-01 contract. Legend uses `{{policy}}` template variable per CONTEXT.md `<discretion>`.

#### Panel 1 — Per-policy sampling decisions

**Title:** `Sampling decisions by policy` (or, under Route A: `Sampling decisions (composite envelope — see Tempo for sub-policy)`)

**PromQL (Route A — composite envelope locked):**
```promql
sum by (policy, decision) (
  rate(otelcol_processor_tail_sampling_count_traces_sampled_total[1m])
)
```
**Legend:** `{{policy}} / {{decision}}`

**Expected series at workshop load:**
- `composite-policy / sampled` (the headline series — kept traces)
- `composite-policy / not_sampled` (drop-vote outcomes from the composite as a whole)

**PromQL (Route B — flat policies):**
```promql
sum by (policy) (
  rate(otelcol_processor_tail_sampling_count_traces_sampled_total{decision="sampled"}[1m])
)
```
**Legend:** `{{policy}}`

**Expected series at workshop load (Route B):**
- `keep-errors` — ~20/s under default load
- `keep-slow` — ~2/s under SLOW_RPS=2
- `probabilistic-fallback` — ~36/s (~20% of remaining ~180 rps non-error non-slow traffic)

#### Panel 2 — Per-policy "vote to drop" rate (replaces the broken D-T13 panel 2)

**Title:** `Per-policy not-sample votes`

**PromQL (Route A or B; both work because `decision="not_sampled"` is per-policy):**
```promql
sum by (policy, decision) (
  rate(otelcol_processor_tail_sampling_count_traces_sampled_total{decision=~"not_sampled|dropped"}[1m])
)
```
**Legend:** `{{policy}} / {{decision}}`

**Caption:** "Each policy votes sample/not_sampled/dropped per trace; this panel surfaces the not-sampled and dropped votes. Read alongside Panel 1 to see, e.g., probabilistic-fallback's drop-vote rate roughly 4x its sample-vote rate (20% sampling → 80% not-sampled). The CONTEXT.md spec called for `count_spans_dropped` which doesn't exist at v0.151.0; the per-policy `not_sampled` decision count is the canonical replacement (see processor README ‘Sampling Policy Decision Frequency')."

#### Panel 3 — Late-arriving spans (F2-2 surface)

**Title:** `Late-arriving spans (Scenario 1 buffer hits)`

**PromQL — counter of late-span events (sum increments per late span):**
```promql
rate(otelcol_processor_tail_sampling_sampling_late_span_age_count[5m])
```
**Legend:** `late spans/s`

**Companion PromQL — p99 lateness:**
```promql
histogram_quantile(
  0.99,
  sum by (le) (rate(otelcol_processor_tail_sampling_sampling_late_span_age_bucket[5m]))
)
```
**Legend:** `p99 lateness (seconds)`

**Caption:** "Late span = a span arriving AFTER its trace's sampling decision was made (Scenario 1 of the v0.151.0 README's three late-span scenarios). Low and stable = `decision_wait: 10s` is right-sized. Climbing = traces have spans straggling beyond the window; consider raising `decision_wait` or enabling `decision_cache.sampled_cache_size`. Note the metric is a histogram in SECONDS — the `_count` series above gives event rate; the `_bucket` series gives the lateness distribution. CONTEXT.md cited `late_span_total` which doesn't exist; this is the v0.151.0 canonical surface."

#### Panel 4 — Decision-loop latency (F2-2 surface)

**Title:** `Sampling decision-loop latency (p50/p95/p99)`

**PromQL — three quantiles overlaid:**
```promql
histogram_quantile(
  0.50,
  sum by (le) (rate(otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket[5m]))
)
histogram_quantile(
  0.95,
  sum by (le) (rate(otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket[5m]))
)
histogram_quantile(
  0.99,
  sum by (le) (rate(otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket[5m]))
)
```
**Legends:** `p50 (ms)`, `p95 (ms)`, `p99 (ms)`

**Caption:** "Wall-clock time the Collector spends running the per-trace decision loop for one batch. README quote: ‘A latency exceeding 1 second can delay sampling decisions beyond `decision_wait`, increasing the chance of traces being dropped before sampling.' Workshop p99 should stay well under 10ms. CONTEXT.md cited `decision_latency` which doesn't exist by that name; the canonical metric has `sampling_` prefix and `_timer_` infix. Bucket boundaries [1, 2, 5, 10, 25, 50, 75, 100, 150, 200, 300, 400, 500, 750, 1000, 2000, 3000, 4000, 5000, 10000, 20000, 30000, 50000] ms — the `_milliseconds` unit suffix may or may not be present per §3.2.1; verify at first live boot."

#### Bonus panel (recommended — D-T16 surface for "is the dashboard contract still alive?")

**Title:** `Traces in memory (sanity gauge)`

**PromQL:**
```promql
otelcol_processor_tail_sampling_sampling_traces_on_memory
```

**Caption:** "Active traces buffered for evaluation. Should oscillate around `decision_wait × incoming_rps` ≈ 2000 at workshop default load. Approaching `num_traces: 10000` means the buffer is filling up and `sampling_trace_dropped_too_early` will start ticking — F2-2 canary."

### 3.4 `mise run verify:tail-sampling` — verbatim bash

The structural template is `verify:datasources` (mise.toml lines 319-389) and `verify:images` (lines 394-450). Mirror: bash one-liner with curl + grep + assertion; non-zero exit on violation.

**Route A version (composite envelope; verifies single composite policy + all three sub-policy names appear at least once on a Tempo span via the `recordpolicy` feature gate):**

```toml
# ──────────────────────────────────────────────────────────────────
# Phase 11 — verify:tail-sampling (D-T14)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:tail-sampling"]
description = "Phase 11 invariant: tail_sampling processor is loaded AND emitting decisions for the composite envelope (Route A). Sub-policy names verified via Tempo span attribute (alpha recordpolicy gate)."
run = """
set -e

# WHY: catches typos in policy names, processor not loaded, configuration
# drift between otelcol-config.yaml and the ose-otel-demo.json dashboard
# row. Mirrors verify:datasources (Phase 10 D-03) and verify:images (D-14)
# pattern: bash one-liner with grep + assertion; non-zero exit on violation.
#
# Two-tier check:
#   1. Self-metrics (:8888/metrics) — assert composite-policy is registered
#      and emitting non-zero count_traces_sampled. This proves the processor
#      LOADED and is making decisions.
#   2. Tempo (:3200/api/search) — assert at least one recently-sampled
#      trace carries a tailsampling.composite_policy attribute with one of
#      the three configured sub-policy names (proves the recordpolicy
#      feature gate is wired AND the YAML names match what's running).
#
# Retry loop (6×5s = 30s tolerance) — Collector cold-start + decision_wait
# buffering means a freshly-started stack needs ~15s before the first
# composite-policy series appears. Mirrors verify:datasources retry shape.

EXPECTED_OUTER='composite-policy'
EXPECTED_SUBS='keep-errors keep-slow probabilistic-fallback'
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

# --- Tier 1: self-metrics ---
for i in $(seq 1 $ATTEMPTS); do
  ACTUAL=$(curl -fsS http://localhost:8888/metrics 2>&1) || {
    LAST_ERR="curl :8888 failed: $ACTUAL"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "verify:tail-sampling tier-1 attempt $i/$ATTEMPTS — Collector self-metrics not ready ($LAST_ERR); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:tail-sampling tier-1 timed out after $((ATTEMPTS * SLEEP_SECS))s — Collector :8888 not reachable."
    echo "Last error: $LAST_ERR"
    echo "Run: mise run infra:up"
    exit 1
  }

  # Match a non-zero count_traces_sampled series with policy=composite-policy.
  # The _total suffix is added by the OTel-Go Prometheus exporter (§3.2 of
  # 11-RESEARCH.md). The match is intentionally loose on label ordering since
  # the exporter does not guarantee a stable label order.
  if printf '%s\n' "$ACTUAL" | grep -E "^otelcol_processor_tail_sampling_count_traces_sampled_total\\{[^}]*policy=\\"${EXPECTED_OUTER}\\"" >/dev/null 2>&1; then
    break
  fi

  LAST_ERR="metric otelcol_processor_tail_sampling_count_traces_sampled_total not yet emitted with policy=\\"${EXPECTED_OUTER}\\""
  [ "$i" -lt "$ATTEMPTS" ] && { echo "verify:tail-sampling tier-1 attempt $i/$ATTEMPTS — $LAST_ERR; retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:tail-sampling tier-1 — $LAST_ERR after $((ATTEMPTS * SLEEP_SECS))s."
  echo "Diagnostics:"
  printf '%s\n' "$ACTUAL" | grep -E '^otelcol_processor_tail_sampling_' | head -20
  echo
  echo "If composite-policy renamed in otelcol-config.yaml, update EXPECTED_OUTER above."
  exit 1
done

echo "verify:tail-sampling tier-1: composite-policy registered and emitting (self-metrics)."

# --- Tier 2: Tempo span attribute (recordpolicy feature gate) ---
# Search Tempo for any span carrying the tailsampling.composite_policy
# attribute set to one of the three expected sub-policy names. The
# recordpolicy feature gate must be enabled on the otel-collector container
# (--feature-gates=processor.tailsamplingprocessor.recordpolicy in compose).
TEMPO_SEARCH='http://localhost:3200/api/search?tags=tailsampling.composite_policy'
TEMPO_OUT=$(curl -fsS "$TEMPO_SEARCH" 2>&1) || {
  echo "ERROR: verify:tail-sampling tier-2 — Tempo /api/search failed: $TEMPO_OUT"
  exit 1
}

MISSING=""
for sub in $EXPECTED_SUBS; do
  if ! printf '%s' "$TEMPO_OUT" | grep -F "$sub" >/dev/null 2>&1; then
    MISSING="$MISSING $sub"
  fi
done

if [ -n "$MISSING" ]; then
  echo "ERROR: verify:tail-sampling tier-2 — sub-policy names missing from Tempo span attributes:$MISSING"
  echo "Either the recordpolicy feature gate isn't enabled on the otel-collector"
  echo "container, OR the policy names in otelcol-config.yaml drifted from this task."
  echo "Tempo response (first 500 chars):"
  printf '%s' "$TEMPO_OUT" | head -c 500
  exit 1
fi

echo "verify:tail-sampling tier-2: all three sub-policy names present on Tempo spans."
echo "verify:tail-sampling: GREEN — composite + sub-policy contracts intact."
"""
```

**Route B version (flat policies; simpler — single tier, asserts all three names as `policy=` labels):**

```toml
[tasks."verify:tail-sampling"]
description = "Phase 11 invariant: tail_sampling processor is loaded AND emitting decisions for all three policy names (Route B — flat policies)."
run = """
set -e

EXPECTED='keep-errors
keep-slow
probabilistic-fallback'
ATTEMPTS=6
SLEEP_SECS=5

for i in $(seq 1 $ATTEMPTS); do
  ACTUAL=$(curl -fsS http://localhost:8888/metrics 2>&1) || {
    [ "$i" -lt "$ATTEMPTS" ] && { sleep $SLEEP_SECS; continue; }
    echo "ERROR: Collector :8888 not reachable after $((ATTEMPTS * SLEEP_SECS))s"; exit 1
  }

  ACTUAL_POLICIES=$(printf '%s\n' "$ACTUAL" \\
    | grep -oE '^otelcol_processor_tail_sampling_count_traces_sampled_total\\{[^}]*policy="[^"]+"' \\
    | grep -oE 'policy="[^"]+"' \\
    | sed 's/policy="\\([^"]*\\)"/\\1/' \\
    | sort -u)

  if diff <(printf '%s\n' "$EXPECTED") <(printf '%s\n' "$ACTUAL_POLICIES") >/dev/null 2>&1; then
    echo "verify:tail-sampling: GREEN — all three policies emitting (keep-errors, keep-slow, probabilistic-fallback)."
    exit 0
  fi

  [ "$i" -lt "$ATTEMPTS" ] && { sleep $SLEEP_SECS; continue; }
  echo "ERROR: policy contract drift after $((ATTEMPTS * SLEEP_SECS))s."
  echo "Expected:"; printf '  %s\n' "$EXPECTED"
  echo "Actual:";   printf '  %s\n' "$ACTUAL_POLICIES"
  exit 1
done
"""
```

**Both versions verified:**
- `:8888/metrics` IS the right endpoint (per `infra/observability/otelcol-config.yaml` lines 167-176 self-telemetry config; per docker-compose.yml port mapping `"8888:8888"`; per Phase 10 IN-06 commit 430131b adding the otelcol scrape job). `[VERIFIED: docker-compose.yml otel-collector service block AND infra/observability/otelcol-config.yaml lines 167-176]`
- The `_total` suffix is correct per §3.2.
- `bc/awk`-free for hot-path simplicity per existing `verify:datasources` style.

> **Planner — pick Route A or Route B once the operator resolves §2.3.** Both are mechanically described; the choice is purely a function of whether composite envelope is preserved.

## 4 — Producer service edit (D-T5 / D-T6)

### 4.1 `Thread.sleep(1500)` inside an OTel SDK INTERNAL span scope — verified, no SDK gotcha

**Verification per OTel Java SDK docs + opentelemetry-java repository conventions:**

The canonical pattern from upstream examples is exactly what D-T5/D-T6 prescribe:

```java
Span span = tracer.spanBuilder("...").startSpan();
try (Scope scope = span.makeCurrent()) {
    Thread.sleep(1500);
    // ... do other work ...
} finally {
    span.end();
}
```

**Confirmed semantics:**

1. **Span end-time = wall-clock at `span.end()`.** The OTel Java SDK records `startEpochNanos` at `startSpan()` and `endEpochNanos` at `span.end()` using `Clock.systemUTC()` (or whatever clock the `SdkTracerProvider` is built with — default is system clock). `Thread.sleep` BLOCKS the calling thread for the requested duration; when it returns, `span.end()` is called and reads `Clock.now()`, which has advanced by ~1500ms. The span's reported duration in Tempo is ~1500ms + the surrounding work (~10-50ms). `[CITED: opentelemetry.io/docs/languages/java/api/]`
2. **`try-with-resources` Scope close happens AFTER `Thread.sleep` returns AND AFTER `span.end()`.** The order is: span starts → scope opens → Thread.sleep blocks → wakes → publish runs → counter increments → return → finally block: `span.end()` → try-with-resources end: `scope.close()`. The scope close restores the prior context — does NOT affect the span's end-time (the span was already ended in finally).
3. **Thread context is preserved across `Thread.sleep`.** The Scope is a `ThreadLocal` indirection; `Thread.sleep` doesn't switch threads or clear ThreadLocals. So `Span.current()` inside the scope is still the WIDGET-SLOW span both before and after the sleep. Any nested span created (e.g., the publish's PRODUCER span via the `TracingMessagePostProcessor`) correctly parents to the WIDGET-SLOW INTERNAL span.
4. **No interruption / virtual-thread concerns** — Spring Boot 3.4 on Java 17 uses platform threads by default for the Tomcat request thread; virtual threads are opt-in via `spring.threads.virtual.enabled=true` (we don't set this). At workshop SLOW_RPS=2 (~1 in-flight slow request at any time), no thread-pool exhaustion concern.
5. **InterruptedException** must be either re-thrown or restored on the interrupt flag. The cleanest pattern is to wrap the sleep in a try/catch and rethrow as `RuntimeException` so the existing `catch (RuntimeException)` Phase 2 D-03 catch records it as a span error.

**No timing pitfall.** `[VERIFIED: pattern matches opentelemetry-java/discussions/3858 + verified by docs.opentelemetry.io api guide; cross-checked against existing OrderService.place() Phase 4 metric-recording-inside-scope pattern]`

### 4.2 Verbatim Java edit for `OrderService.place()`

> **Planner — paste this verbatim** as a new branch inside the existing `try (Scope scope = ...)` block of `OrderService.place()`. The branch sits BEFORE the existing `publisher.publish(orderId, payload)` call so that the publish (PRODUCER span) is parented under the slow INTERNAL span, AND so that the publish itself is delayed (the trace's MAX-span duration includes the sleep, satisfying the `latency.threshold_ms: 1000` policy).

**Patch site:** `producer-service/src/main/java/com/example/producer/domain/OrderService.java`, inside `place(...)`, immediately after line 90 (`String orderId = UUID.randomUUID().toString();`) and BEFORE line 91 (`publisher.publish(orderId, payload);`).

**Verbatim insertion (~14 lines including JavaDoc-style comment):**

```java
            // ---- Phase 11 D-T5/D-T6: WIDGET-SLOW SKU branch (TSAMP-01 latency policy) ----
            //
            // If the request's sku is "WIDGET-SLOW", sleep 1500ms inside this
            // INTERNAL span scope. The 1500ms span duration > the Collector's
            // tail_sampling latency.threshold_ms=1000, so the keep-slow
            // sub-policy fires reliably (50% buffer above threshold). Tail-
            // sampling's latency policy uses MAX-span duration of the
            // assembled trace — the producer-side sleep alone is sufficient;
            // the consumer's CONSUMER+INTERNAL spans stay fast.
            //
            // Mirrors the WIDGET-EXPRESS / WIDGET-STANDARD / WIDGET-IDEMPOTENT /
            // WIDGET-BURST family pattern (zero new architecture). Driven from
            // scripts/load.sh's SLOW_RPS stream (D-T7) at ~2 rps default.
            //
            // See README §11 (Phase 11) for the workshop walkthrough; the
            // F2-3 head-vs-tail double-filter callout at the end of §11
            // explains why the SDK sampler stays parentBased(alwaysOn()) here.
            if ("WIDGET-SLOW".equals(payload.get("sku"))) {
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("WIDGET-SLOW sleep interrupted", ie);
                }
            }
```

**Why the rethrow-as-RuntimeException pattern:** the existing Phase 2 D-03 `catch (RuntimeException)` block records the exception on the span and rethrows. Wrapping `InterruptedException` and rethrowing as `RuntimeException` keeps the WIDGET-SLOW branch ERROR-status-correct without touching the catch block (D-03 catch unchanged — same as Phase 3 APP-04 reused it).

**Why `equals` not `==`:** `payload.get("sku")` returns an `Object` (boxed `String`); `equals` is the only correct comparison.

**JavaDoc class-comment delta:** add to the class JavaDoc above `OrderService` (around line 47):

```java
 * <p><b>Phase 11 adds the WIDGET-SLOW SKU branch (D-T5/D-T6).</b> If the
 * request's sku is "WIDGET-SLOW", {@link #place(Map)} {@link Thread#sleep}s
 * 1500ms inside the INTERNAL span scope. The resulting trace duration > 1s
 * triggers the Collector's tail_sampling keep-slow latency policy (TSAMP-01).
 * No new architecture — mirrors the WIDGET-EXPRESS / WIDGET-STANDARD /
 * WIDGET-IDEMPOTENT / WIDGET-BURST SKU family pattern. Driven by
 * scripts/load.sh's SLOW_RPS stream (D-T7).
```

## 5 — Workshop tooling edits

### 5.1 `scripts/load.sh` SLOW_RPS stream (D-T7)

Structural template = the existing IDEMPOTENT_RPS block (load.sh lines 73-74 declaration + lines 138-166 stream body). Per CONTEXT.md `<code_context>` "SLOW_RPS uses oha because the payload is fixed (`WIDGET-SLOW`)" — so the template is actually closer to the steady streams (lines 118-136) than the idempotency stream.

**Verbatim ~25-line additions to `scripts/load.sh`:**

```bash
# (Add to env-var block around line 75, after IDEMPOTENT_RPS line:)
# Slow stream — set SLOW_RPS=0 to disable. Default 2 rps drives the
# Collector tail_sampling latency policy (Phase 11 D-T7). At ~2 rps,
# ~20 slow traces fall inside the 10s decision_wait window — visible
# in dashboards without dominating the steady streams.
SLOW_RPS="${SLOW_RPS:-2}"

# (Add to the cleanup trap PID list around line 89:)
#   add PID_SLOW alongside PID_IDEMPOTENT/PID_BURST_LOOP/PID_HEARTBEAT

# (Add to the heartbeat banner block around line 105:)
if [[ "$SLOW_RPS" -gt 0 ]]; then
  echo "Slow stream:"
  echo "  priority=standard sku=WIDGET-SLOW @ ${SLOW_RPS} rps (Phase 11 D-T7)"
else
  echo "Slow stream: disabled (set SLOW_RPS>0 to enable)"
fi

# (Add a fourth oha stream block AFTER the steady streams, BEFORE the
#  idempotency stream — order: steady, slow, idempotent, burst — around
#  line 137, between the PID_STANDARD line and the idempotency block:)

# --- Slow stream (Phase 11 D-T7) ----------------------------------------
#
# Drives the Collector tail_sampling latency.threshold_ms=1000 policy
# by sending requests with sku=WIDGET-SLOW. OrderService.place() sleeps
# 1500ms inside its INTERNAL span scope when it sees this sku — total
# trace duration ~1.5s reliably trips the keep-slow sub-policy.
#
# Single oha invocation with -q $SLOW_RPS, fixed payload — same shape
# as WIDGET-EXPRESS and WIDGET-STANDARD streams. No per-request
# templating needed (sku is constant).
if [[ "$SLOW_RPS" -gt 0 ]]; then
  oha -z "${DURATION}" \\
      -q "${SLOW_RPS}" \\
      -c 1 \\
      -m POST \\
      -T application/json \\
      -d '{"sku":"WIDGET-SLOW","quantity":1,"priority":"standard"}' \\
      --no-tui \\
      "${TARGET}" >/dev/null 2>&1 &
  PID_SLOW=$!
fi

# (Update heartbeat printf around line 197 to include slow PID:)
printf '[load] alive — elapsed=%ds (express=%d, standard=%d, slow=%d)\\n' \\
  "$elapsed" "$PID_EXPRESS" "$PID_STANDARD" "${PID_SLOW:-0}"
```

**Connections (`-c 1`):** because slow requests sit on the Tomcat thread for ~1.5s each, even at SLOW_RPS=2 you only need ~3 concurrent connections at peak. `-c 1` keeps the connection-pool footprint minimal; if SLOW_RPS is raised much above 5, bump `-c` proportionally. (The express/standard streams use `-c 10` at 100 rps — same ratio of ~0.1 connections per rps.)

### 5.2 `mise.toml` — add the `verify:tail-sampling` task block

Insert AFTER the existing `[tasks."verify:images"]` block (mise.toml line 450). Body = §3.4 Route A or Route B verbatim, gated on the operator's §2.3 decision.

**Phase 11 also adds NO new env vars to `mise.toml`** — `SLOW_RPS` is a load.sh-local env var, not a mise tool var.

## 6 — Dashboard row (D-T13 / D-T16)

**Structural template:** the existing `Deeper-dive (post-workshop)` collapsed row in `grafana/dashboards/ose-otel-demo.json` (line 220-373). That row is `collapsed: true`, lives at `gridPos.y: 10`, contains 3 inline panels with `gridPos.h: 9`.

**Phase 11's new row should be appended** at `gridPos.y: 21` (below the existing row at y=10 + h=1 row-header + h=9 panel = y=20; +1 buffer = y=21). Same `collapsed: true` shape. 4 panels (or 5 with the bonus from §3.3) with `gridPos.h: 9`, distributed across `gridPos.w: 6` (4 panels in a 24-wide row) or `gridPos.w: 8` (3 panels + 1 wider one).

**JSON skeleton for the new row** (planner fills in panel bodies from §3.3 PromQL; this is the structural shell ONLY):

```json
{
  "collapsed": true,
  "gridPos": { "h": 1, "w": 24, "x": 0, "y": 21 },
  "id": 9,
  "title": "Tail Sampling diagnostics (Phase 11)",
  "type": "row",
  "panels": [
    /* Panel 1 — see §3.3, gridPos: { "h": 9, "w": 12, "x": 0, "y": 22 } */
    /* Panel 2 — see §3.3, gridPos: { "h": 9, "w": 12, "x": 12, "y": 22 } */
    /* Panel 3 — see §3.3, gridPos: { "h": 9, "w": 8,  "x": 0, "y": 31 } */
    /* Panel 4 — see §3.3, gridPos: { "h": 9, "w": 8,  "x": 8, "y": 31 } */
    /* Panel 5 (bonus) — see §3.3, gridPos: { "h": 9, "w": 8, "x": 16, "y": 31 } */
  ],
  "description": "POLICY-NAMES CONTRACT (Phase 11 D-T16): the policy= labels referenced in this row's queries are a contract with infra/observability/otelcol-config.yaml's tail_sampling.policies block. Renaming any policy in the YAML requires updating these queries. Verified at runtime by `mise run verify:tail-sampling` (D-T14)."
}
```

**Panel `id:` values** (`9`, `10`, `11`, `12`, `13`) — verify these are not collisions with existing panel IDs. Existing IDs in the dashboard are `1, 2, 3, 4, 5, 6, 7, 8` (panels 1-4 in top row, row-header id=5, panels 6-8 in collapsed row). New row header id=9, panels 10-13 (or 10-14).

**No edits to existing panels per CONTEXT.md `<integration_points>`** — additive only.

## 7 — Screenshot mechanism (D-T9 / D-T10)

**Mechanism:** manual one-shot per D-T9, mirrors Phase 10 D-13 verbatim. NO modification to `scripts/screenshots/capture.mjs` and NO new mise task. Tag-time-travel is not used; the OFF screenshot is taken on `main` HEAD pre-Phase-11 (BEFORE any Phase 11 plan lands), and the ON screenshot is taken on `main` HEAD post-Phase-11 (AFTER all Phase 11 plans land).

**Both screenshots from the SAME Tempo Search query** (D-T10 invariant):
- URL: `http://localhost:3000/explore` → Tempo datasource → Search tab → `Service Name = order-producer`, time range `Last 5 min`
- Capture: full Tempo Search results panel (the trace count is what matters; ~1500 vs ~600 is the visible delta)
- Output: `docs/screenshots/step-11-tail-sampling-OFF.png` and `docs/screenshots/step-11-tail-sampling-ON.png`

**Sequencing constraint (per `<specifics>` bullet 4):** the OFF screenshot MUST be captured BEFORE Phase 11's first plan lands (it's a "before" state of `main`; once tail_sampling is in the YAML, the OFF state is unreachable on `main`). Planner should make this explicit — either as a Wave 0 task ("OFF screenshot capture; commits manually before Wave 1 begins") or as a quick-task that lands separately on `main` first.

**README HTML `<table>` template** (mirrors Phase 7 D-04 / `step-02-disconnected.png` + `step-03-joined.png` paired-screenshot pattern at `README.md` Phase 2 section):

```markdown
<table>
<tr>
<td><b>Pre-Phase-11 (OFF) — all traces reach Tempo</b></td>
<td><b>Post-Phase-11 (ON) — composite policy in effect</b></td>
</tr>
<tr>
<td><img src="docs/screenshots/step-11-tail-sampling-OFF.png" alt="Tempo Search showing N traces in last 5 min, no tail sampling"></td>
<td><img src="docs/screenshots/step-11-tail-sampling-ON.png"  alt="Tempo Search showing M traces in last 5 min — every error trace, every slow trace, ~20% of the rest"></td>
</tr>
</table>
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Selecting which traces reach Tempo | Custom `processor` Go code or post-export filter | `tail_sampling` processor (already in collector-contrib v0.151.0) | Decision logic, OR-semantics priority chain, decision-cache, late-span handling, and rate-allocation are non-trivial; upstream's evaluator + circular buffer is battle-tested. |
| Per-policy rate-limit | Custom `rate_limit` middleware in Java | `composite.rate_allocation` + `max_total_spans_per_second` | Already in `composite_helper.go`; the per-second window-reset logic is in `composite.go` (lines 91-128) and handles span-count math correctly. |
| Synthetic latency in a Java handler | A custom `Sampler` that delays | `Thread.sleep(1500)` inside the existing INTERNAL span scope | The cleanest, simplest mechanism. CONTEXT.md `<discretion>` already considered `CompletableFuture.supplyAsync(...).get()` with delayed executor and rejected it for SLOW_RPS=2. |
| Sub-policy attribution at the metric layer | Custom processor wrapper that re-emits per-sub-policy series | Either flat policies (Route B) OR the upstream `recordpolicy` feature gate + Tempo span attribute (Route A) | Hand-rolling re-emission would duplicate the entire decision evaluator. Upstream gates this behind a feature flag for a reason — sub-policy metric attribution interacts with rate-allocation accounting. |
| `:8888/metrics` exposition | Custom Prometheus endpoint in the collector | The collector's built-in `service.telemetry.metrics` block (already wired in Phase 10 IN-06) | Already wired; the Phase 10 `prometheus` receiver + `prometheusremotewrite/mimir` exporter chain feeds these into Mimir for Grafana queries. |

**Key insight:** Phase 11 is almost entirely a "consume upstream as-is" phase. The ONE place where hand-rolling tempts is the dashboard PromQL (when the canonical metric name doesn't match the panel's intuitive name); §3.3 gives verbatim PromQL that uses upstream metric names verbatim — no transformation, no recording rule, no custom emission.

## Common Pitfalls

### Pitfall 1: Composite envelope collapses per-sub-policy metric attribution
**What goes wrong:** Dashboard panel (D-T13 panel 1) and verify task (D-T14) assume per-sub-policy `policy=` label values; composite produces a single outer-policy series.
**Why it happens:** Composite is a single top-level policy in `tsp.policies`; `recordPerPolicyEvaluationMetrics` iterates the top-level slice only.
**How to avoid:** §2.3 Route A (composite + alpha `recordpolicy` feature gate + span-attribute attribution) or Route B (flat top-level policies).
**Warning signs:** `verify:tail-sampling` fails with "expected: keep-errors\nkeep-slow\nprobabilistic-fallback / actual: composite-policy"; dashboard panel 1 shows ONE colored line instead of three.

### Pitfall 2: `_total` suffix mismatch in PromQL
**What goes wrong:** PromQL written against `count_traces_sampled` (without `_total`) returns no data; data is at `count_traces_sampled_total`.
**Why it happens:** Prometheus exposition adds `_total` to monotonic Sum metrics by default; OTel-Go honors this convention.
**How to avoid:** Always use `_total` suffix in PromQL targeting otelcol self-metrics. §3.3 queries do this verbatim.
**Warning signs:** "No data" in dashboard panels; verify task succeeds (its grep also has `_total`).

### Pitfall 3: `Thread.sleep` `InterruptedException` swallowed
**What goes wrong:** Naïve `try { Thread.sleep(1500); } catch (InterruptedException) {}` swallows the interrupt; downstream code can't tell the thread was interrupted.
**Why it happens:** `InterruptedException` is checked; the obvious catch is empty.
**How to avoid:** Either restore the interrupt flag (`Thread.currentThread().interrupt()`) AND/OR rethrow as `RuntimeException`. §4.2 verbatim Java does both.
**Warning signs:** WIDGET-SLOW requests that are mid-sleep when the JVM is stopped via Ctrl-C don't terminate cleanly; jstack shows threads still in sleep.

### Pitfall 4: Composite `max_total_spans_per_second` vs `rate_allocation.percent` confusion
**What goes wrong:** Operator sets `rate_allocation: [{percent: 20}, {percent: 40}]` thinking it means raw spans-per-second; actually it means a percentage of `max_total_spans_per_second`.
**Why it happens:** The unit is implicit in the field name; `rate_allocation` does not carry an explicit unit in YAML.
**How to avoid:** Use the §2.2 verbatim YAML which is annotated. Cross-check that `Σ percent ≤ 100` (composite enforces each sub-policy's individual cap; over-allocation just means a sub-policy's cap is higher than the global, which is fine since the global cap also clamps).
**Warning signs:** No traces reach Tempo at workshop scale (over-restrictive caps); composite always returns NotSampled.

### Pitfall 5: Workshop attendee enables Phase 16 head sampling on the same stack as Phase 11
**What goes wrong:** Effective sampling rate becomes head% × tail% = 50% × 20% = 10% — Tempo looks empty.
**Why it happens:** F2-3 (PITFALLS.md). Both samplers are independently configured; no system-level guard.
**How to avoid:** D-T12 callout in README §11. Re-callout in Phase 16 README §16 with back-link.
**Warning signs:** "Why is Tempo empty after I enable Phase 16?" — answer is in F2-3.

### Pitfall 6: `policy_order` mismatch with `composite_sub_policy.name` values
**What goes wrong:** `policy_order: [keep-error, keep-slow, probabilistic-fallback]` (note typo `keep-error` vs sub-policy name `keep-errors`) — composite silently drops the un-listed sub-policy from evaluation.
**Why it happens:** The two arrays are correlated by string matching; YAML doesn't enforce the relationship.
**How to avoid:** Lint at plan-checker time — verify every `policy_order[i]` matches a `composite_sub_policy[j].name`. The §2.2 verbatim YAML is correct.
**Warning signs:** `keep-errors` (or whichever was typo'd) gets the `default share` (= max_total_spans_per_second / N) instead of the intended allocation.

## Code Examples

### Example 1 — Verifying which sub-policy fired on a kept trace via Tempo (Route A)

After Phase 11 lands and the `recordpolicy` feature gate is enabled:

```bash
# Find a recently-sampled trace and inspect its sub-policy attribution
curl -s 'http://localhost:3200/api/search?tags=tailsampling.policy=composite-policy&limit=5' | jq '.traces[].traceID'
# Pick one, fetch the trace
curl -s 'http://localhost:3200/api/traces/<traceID>' | jq '..|.attributes? | select(.) | .[] | select(.key | startswith("tailsampling"))'
# Expected output:
# {"key":"tailsampling.policy","value":{"stringValue":"composite-policy"}}
# {"key":"tailsampling.composite_policy","value":{"stringValue":"keep-errors"}}  # or keep-slow / probabilistic-fallback
```

### Example 2 — `processor.tailsamplingprocessor.recordpolicy` feature gate enablement (Route A only)

In `docker-compose.yml` `otel-collector` service block, change `command:`:

```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.151.0
    container_name: ose-otel-collector
    command:
      - "--config=/etc/otelcol-contrib/config.yaml"
      - "--feature-gates=processor.tailsamplingprocessor.recordpolicy"
```

(The existing single-element `command: ["--config=..."]` becomes a two-element array.)

### Example 3 — Sanity-check the `:8888/metrics` exposition shape

After `mise run infra:up` + `mise run dev` + `mise run load` + 30s wait:

```bash
curl -s http://localhost:8888/metrics | grep -E '^otelcol_processor_tail_sampling_' | sort -u
```

Expected output (Route A — composite locked):
```
otelcol_processor_tail_sampling_count_traces_sampled_total{decision="not_sampled",policy="composite-policy",sampled="false",service_instance_id="...",service_name="otelcol-contrib",service_version="0.151.0"} 1234
otelcol_processor_tail_sampling_count_traces_sampled_total{decision="sampled",policy="composite-policy",sampled="true",...} 567
otelcol_processor_tail_sampling_global_count_traces_sampled_total{decision="not_sampled",sampled="false",...} 1234
otelcol_processor_tail_sampling_global_count_traces_sampled_total{decision="sampled",sampled="true",...} 567
otelcol_processor_tail_sampling_new_trace_id_received_total{...} 1801
otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket{...} ...
otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_count{...} ...
otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_sum{...} ...
otelcol_processor_tail_sampling_sampling_late_span_age_seconds_bucket{...} ...
otelcol_processor_tail_sampling_sampling_late_span_age_seconds_count{...} ...
otelcol_processor_tail_sampling_sampling_late_span_age_seconds_sum{...} ...
otelcol_processor_tail_sampling_sampling_policy_evaluation_error_total{...} 0
otelcol_processor_tail_sampling_sampling_policy_execution_count_total{policy="composite-policy",...} 1801
otelcol_processor_tail_sampling_sampling_policy_execution_time_sum{policy="composite-policy",...} 12345
otelcol_processor_tail_sampling_sampling_trace_dropped_too_early_total{...} 0
otelcol_processor_tail_sampling_sampling_trace_removal_age_seconds_bucket{...} ...
otelcol_processor_tail_sampling_sampling_traces_on_memory{...} 1850
otelcol_processor_tail_sampling_traces_dropped_too_large_total{...} 0
```

The `_seconds`/`_milliseconds` infix on histogram buckets is what §3.2.1 flags as MEDIUM confidence. If those infixes are absent, drop them from the §3.3 PromQL.

## State of the Art

| Old Approach | Current Approach (v0.151.0) | When Changed | Impact |
|--------------|----------------------------|--------------|--------|
| Inverted decisions (`InvertedSample`, `InvertedNotSample`) | Deprecated; replaced by `drop` and `not` policies | v0.126.0 (`disableinvertdecisions` feature gate) | TSAMP-02 four-tier priority chain wording is still ACCURATE (deprecation ≠ removal); `drop` policy syntax is the going-forward way to express "always drop X" |
| `tailsamplingprocessor.metricstatcountspanssampled` always emitting `count_spans_sampled` | Behind alpha feature gate; off by default | v0.95.0 | D-T13 panel 2's "drop counter" can't use `count_spans_dropped` (doesn't exist) and probably can't use `count_spans_sampled` (gated); canonical replacement is `count_traces_sampled{decision="not_sampled"}` |
| Per-policy metric attribution always emits | When inside composite/and, attribution collapses to outer name | (always, by design) | §2.3 — drives Route A vs Route B |
| `tailsampling.composite_policy` span attribute always emitted | Behind alpha feature gate `recordpolicy`; off by default | v0.120.0 | Required for Route A's verify task tier 2 |

**Deprecated/outdated:**
- `InvertedSample`/`InvertedNotSample` decision types (use `drop`/`not` instead).
- The `count_spans_dropped` metric name **never existed** at any version; CONTEXT.md D-T13 panel 2 is a fabrication. `count_spans_sampled{decision="not_sampled"}` (gated) or `count_traces_sampled{decision="not_sampled"}` are the v0.151.0-canonical replacements.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The OTel-Go Prometheus exporter shipped in collector-contrib v0.151.0 strips curly-bracket annotation units (`{traces}`, `{spans}`) from metric names | §3.2 | If wrong, metric names exposed at `:8888/metrics` would be `count_traces_sampled__traces__total` (double-underscore around mangled unit). PromQL in §3.3 + verify-task grep in §3.4 would all fail. **Verification step:** `curl :8888/metrics | grep tail_sampling` at first live boot — see §3.2.1. |
| A2 | The `_milliseconds_bucket` / `_seconds_bucket` unit-suffix infixes ARE present on histogram exposition (default OTel-Go behavior) | §3.2.1, §3.3 panel 3/4 | If absent, drop infix from PromQL. Function unchanged. |
| A3 | Adding `--feature-gates=processor.tailsamplingprocessor.recordpolicy` to the otel-collector container `command:` array does NOT break any other Phase 10 invariant | §2.3 Route A, Example 2 | Alpha feature gate could in principle interact with another component's behavior; reviewed metadata.yaml for v0.151.0 and the gate is scoped to tailsamplingprocessor only. **Verification step:** smoke-test the Phase 10 success criteria after adding the gate. |
| A4 | At workshop load (~200 rps + SLOW_RPS=2 + BURST_RPS=150 peak), `num_traces: 10000` is sufficient | §2.5 | If wrong, `sampling_trace_dropped_too_early_total` will tick up. Bonus panel 5 (§3.3) catches this. |
| A5 | Operator will choose Route A (composite envelope preserved) over Route B (flat policies) | §2.3 | If operator chooses Route B, every artifact in Phase 11's plans (otelcol-config.yaml, ose-otel-demo.json, mise.toml, README §11) needs to use the Route B variants. Both routes are spec'd verbatim. |

## Open Questions

1. **Operator confirmation: Route A vs Route B (§2.3).**
   - What we know: Composite envelope is locked (D-T2). The metric-attribution collapse is a hard upstream property of v0.151.0.
   - What's unclear: Does the operator prefer to preserve the rate-limit lesson (Route A, more complex verify task) or simplify the dashboard contract (Route B, gives up rate-limit lesson)?
   - Recommendation: Surface to operator at Wave 0 plan-check; default to Route A per CONTEXT.md `<specifics>` bullet 1 ("production-realistic over workshop-minimal").

2. **Should the OFF-screenshot capture be a Wave-0 task or a separate quick-task?**
   - What we know: D-T9 is manual one-shot; OFF must be captured BEFORE any Phase 11 plan lands.
   - What's unclear: CONTEXT.md `<specifics>` bullet 4 says "make this explicit"; planner decides which mechanism.
   - Recommendation: Quick-task that lands on `main` BEFORE the Phase 11 PR opens — clean separation; Phase 11 itself never spans an "OFF state" boundary.

3. **`_milliseconds_bucket` vs `_bucket` exposition shape (§3.2.1).**
   - What we know: OTel-Go SDK has shipped multiple toggles; PR #7044 fixed curly-bracket stripping but the seconds/milliseconds infix behavior was not explicitly verified for v0.151.0.
   - What's unclear: Whether `service.telemetry.metrics.readers[0].pull.exporter.prometheus` accepts `without_units: true` toggle in the configured shape.
   - Recommendation: Verify at first live boot per §3.2.1 procedure. Plans should NOT take this as a blocker — both shapes can be fixed in a 1-line PromQL edit.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `otel/opentelemetry-collector-contrib:0.151.0` Docker image | tail_sampling processor | ✓ (Phase 10) | 0.151.0 | — |
| `:8888/metrics` host port | verify:tail-sampling task + dashboard panels | ✓ (Phase 10 D-10) | — | — |
| Phase 10 IN-06 otelcol scrape job (`source: infra-exporter`) | feeds `otelcol_processor_tail_sampling_*` series into Mimir | ✓ (commit 430161e) | — | — |
| `oha 1.14.0` | `scripts/load.sh` SLOW_RPS stream | ✓ (mise.toml:16) | 1.14.0 | — |
| `Tempo :3200/api/search` | verify:tail-sampling tier-2 (Route A only) | ✓ (Phase 10 D-10) | 2.10.5 | — |
| `Grafana :3000` (Prometheus datasource UID `prometheus`) | new dashboard row queries | ✓ (Phase 10 D-01) | 13.0.1 | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None.

**Skip condition:** does not apply — Phase 11 has external dependencies (the Collector image + the existing self-metric scrape job + Tempo + Grafana); all are present per Phase 10's completion record.

## Security Domain

> Phase 11 is config-only on the Collector side + 5 LoC of Java (Thread.sleep) + bash extension to load.sh + dashboard JSON + verify task + README. No new external surface, no new auth, no PII handling, no cryptography.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | (Phase 11 introduces no new auth surface; reuses existing Mimir `auth_enabled: false` workshop tenant per Phase 10 STACK-05) |
| V3 Session Management | no | (no session state added) |
| V4 Access Control | no | (no new access control needed) |
| V5 Input Validation | yes | `OrderService.place()` reads `payload.get("sku")` to switch on `WIDGET-SLOW`; this is workshop input from `scripts/load.sh` over loopback. Validation happens at the Spring `@RequestBody` deserialization (already in place from Phase 1). The new `equals` check is null-safe (`"WIDGET-SLOW".equals(...)`). |
| V6 Cryptography | no | (no new crypto surface) |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Synthetic-load DoS via `WIDGET-SLOW` payloads tying up Tomcat threads | Denial of Service | Workshop runs on a single laptop; Tomcat default thread pool (200 max) handles SLOW_RPS=2 (≤3 in-flight at any moment) trivially. No external network exposure (`localhost:8080` only). Document in README §11 that raising SLOW_RPS above ~50 with default `-c` settings could exhaust the Tomcat thread pool — workshop attendee guidance. |
| `Thread.sleep` swallowing `InterruptedException` blocks JVM shutdown | Resource exhaustion / shutdown delay | §4.2 verbatim Java restores the interrupt flag AND rethrows as RuntimeException — JVM shutdown signals propagate cleanly. |

**No CRITICAL or HIGH security issues introduced by Phase 11.** All locked decisions are config-tier or workshop-domain; no PII, no auth surface, no externally-reachable endpoint changes.

## Sources

### Primary (HIGH confidence — verified at v0.151.0 git tag)

- [opentelemetry-collector-contrib v0.151.0 — tailsamplingprocessor README](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/README.md) — composite YAML keys, OR-semantics priority chain text, Sampling Strategies, Late-Arriving Spans scenarios, Monitoring and Tuning section, Tracking sampling policy section
- [opentelemetry-collector-contrib v0.151.0 — generated_telemetry.go](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/internal/metadata/generated_telemetry.go) — definitive list of every metric name + type + unit
- [opentelemetry-collector-contrib v0.151.0 — metadata.yaml](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/metadata.yaml) — feature_gates, metric attributes (policy/sampled/decision)
- [opentelemetry-collector-contrib v0.151.0 — documentation.md](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/documentation.md) — auto-generated metric attribute table
- [opentelemetry-collector-contrib v0.151.0 — composite.go](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/internal/sampling/composite.go) — composite Evaluate logic (first-match within rate cap)
- [opentelemetry-collector-contrib v0.151.0 — composite_helper.go](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/composite_helper.go) — getRateAllocationMap (default share = max/N)
- [opentelemetry-collector-contrib v0.151.0 — processor.go](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/processor.go) — recordPerPolicyEvaluationMetrics (top-level policies only)
- [opentelemetry-collector-contrib v0.151.0 — testdata/tail_sampling_config.yaml](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.151.0/processor/tailsamplingprocessor/testdata/tail_sampling_config.yaml) — canonical composite example
- [OTel Collector — Internal telemetry docs](https://opentelemetry.io/docs/collector/internal-telemetry/) — `:8888/metrics` endpoint, `_total` suffix convention
- [OpenTelemetry Java API docs](https://opentelemetry.io/docs/languages/java/api/) — Span lifecycle, makeCurrent + Scope semantics

### Secondary (MEDIUM confidence — cross-checked with primary)

- [opentelemetry-go PR #7044 — Prometheus exporter unit-suffix fix](https://github.com/open-telemetry/opentelemetry-go/pull/7044) — confirms curly-bracket stripping fix landed in `exporters/prometheus/v0.59.1` (July 2025); collector v0.151.0 ships OTel-Go v1.43.0 (later than fix)
- [Context7 `/open-telemetry/opentelemetry-collector-contrib`](https://context7.com/open-telemetry/opentelemetry-collector-contrib) — verified composite YAML key shapes match WebFetch results
- [Grafana Tempo `:3200/api/search`](https://grafana.com/docs/tempo/latest/api_docs/#search) — used by verify:tail-sampling tier-2 query

### Tertiary (LOW confidence — not load-bearing)

- [GitHub issue open-telemetry/opentelemetry-collector-contrib#27567](https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/27567) — community discussion of `count_traces_sampled` label semantics (cross-references composite collapse — community confirms, not authoritative)
- [Grafana Alloy tail_sampling docs](https://grafana.com/docs/alloy/latest/reference/components/otelcol/otelcol.processor.tail_sampling/) — alternative phrasing of YAML keys (Alloy is out of scope per v2.0 exclusions but their docs corroborate field names)

## Confidence Assessment

| Area | Confidence | Reason |
|------|-----------|--------|
| Composite YAML key names (`composite_sub_policy`, `policy_order`, `rate_allocation`, `max_total_spans_per_second`, `percent`) | HIGH | Verified against three independent sources at v0.151.0: README, testdata YAML, composite_helper.go source |
| `decision_wait` / `num_traces` / `expected_new_traces_per_sec` defaults and behavior | HIGH | README + processor.go source |
| `tail_sampling` placement BEFORE `batch` (D-T1) | HIGH | README intro paragraph + ARCHITECTURE.md line 185 + Phase 11 D-T1 lock |
| Metric series names — every `otelcol_processor_tail_sampling_*` | HIGH | Direct read of generated_telemetry.go + metadata.yaml at v0.151.0 |
| `_total` suffix added by Prometheus exposition | HIGH | Documented in OTel Collector internal-telemetry docs |
| `{traces}`/`{spans}` curly-bracket unit stripping | MEDIUM | OTel-Go v1.43.0 includes the fix per PR #7044 milestone, but full release-note traceability incomplete; verify at first live boot per §3.2.1 |
| `_milliseconds`/`_seconds` infix on histogram exposition | MEDIUM | OTel-Go default behavior; multiple toggles exist; verify at first live boot |
| Composite metric-attribution collapse to single outer policy | HIGH | processor.go recordPerPolicyEvaluationMetrics direct read; cross-checked against community issue #27567 |
| `recordpolicy` feature gate behavior + span attributes | HIGH | metadata.yaml + README "Tracking sampling policy" section |
| Composite first-match-within-rate-cap semantics | HIGH | composite.go Evaluate function direct read |
| `Thread.sleep` inside Scope correctly stretches span end-time | HIGH | OTel Java API docs + canonical pattern from opentelemetry-java/discussions/3858 |
| OR-semantics priority chain wording (TSAMP-02) | HIGH | Verbatim quote from v0.151.0 README "Policy Decision Flow" |
| `:8888/metrics` is the correct endpoint | HIGH | infra/observability/otelcol-config.yaml lines 167-176 + docker-compose.yml port mapping `8888:8888` + Phase 10 IN-06 |
| Late-span behavior (Scenario 1/2/3 + which metric surfaces it) | HIGH | README "Late-Arriving Spans" section verbatim quote |
| `num_traces: 10000` sufficient at workshop load | HIGH | Math (200 rps × 10s = 2000 traces vs 10000 cap = 5× headroom); BURST math gives 2.8× headroom |
| Sub-policy attribution requires alpha feature gate (Route A) | HIGH | metadata.yaml feature_gates + README documents alpha status |
| Route A vs Route B is the only way to resolve §2.3 | HIGH | Composite collapse is a hard upstream property; only YAML-level changes (composite vs flat) or feature-gate enablement can affect attribution |

**Confidence breakdown:**
- Standard stack: HIGH — all artifacts already pulled/pinned by Phase 10
- Architecture (composite YAML, pipeline placement, metric series): HIGH — direct source verification at v0.151.0 tag
- Self-metrics exposition (`_total`, unit suffixes): MEDIUM — verify at first live boot
- Pitfalls (composite collapse, metric name fabrications in CONTEXT.md): HIGH — directly observed in upstream source

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (collector-contrib release cadence is monthly; v0.152+ may move feature gates from alpha; re-verify if Phase 11 stretches past June)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Phase 10 already pinned every container; this phase adds no new dependency.
- Architecture: HIGH for everything verified at v0.151.0 tag; MEDIUM for `:8888/metrics` exposition formatting (suffix semantics).
- Pitfalls: HIGH — researcher independently surfaced TWO load-bearing CONTEXT.md mismatches (D-T13 metric names; D-T14 + composite policy-label collapse) by reading upstream source.

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (30-day window for v0.151.0 stability — collector contrib releases monthly)

---

## RESEARCH COMPLETE

Phase 11 is mechanically simple — one Collector YAML block, 5 LoC of Java, one load-script extension, one dashboard row, one verify task, one README section, one screenshot pair. Every locked CONTEXT.md decision is honored verbatim. The research surfaced TWO load-bearing upstream-fact mismatches that the planner MUST escalate to the operator at Wave 0 plan-check before any plan touches the otelcol-config.yaml or ose-otel-demo.json: (1) three of the four metric series cited in D-T13 do not exist by those names at v0.151.0 (verbatim corrected PromQL provided in §3.3); (2) the composite envelope (D-T2) collapses per-sub-policy metric attribution into a single outer-composite series, breaking the D-T14 verify task as written. The researcher provides BOTH Route A (composite preserved + alpha `recordpolicy` feature gate + Tempo span-attribute attribution) and Route B (flatten policies, give up rate-limit lesson) fully spec'd with verbatim YAML, PromQL, and bash; recommendation is Route A per the CONTEXT.md "production-realistic over workshop-minimal" guidance. Composite YAML keys, OR-semantics priority chain wording, decision_wait/num_traces sizing, late-span behavior, Thread.sleep-inside-Scope semantics, and the `:8888/metrics` endpoint contract all verify clean against v0.151.0 source. Planner can lift §2.2 (composite YAML), §3.3 (panel PromQL), §3.4 (verify task bash), §4.2 (Java edit), §5.1 (load.sh delta), §6 (dashboard row skeleton), §7 (screenshot README pattern) verbatim into PLAN.md tasks once §2.3 is resolved with the operator.
