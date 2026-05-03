# Phase 11: Tail Sampling at the Collector - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 11-tail-sampling-at-the-collector
**Areas discussed:** Pipeline placement of tail_sampling, Latency-policy realism, ON/OFF demo mechanism, Self-observability of tail_sampling

---

## Pipeline placement of tail_sampling

### Q1: Where should the tail_sampling processor sit in the traces pipeline?

| Option | Description | Selected |
|--------|-------------|----------|
| Before batch | `[memory_limiter, tail_sampling, batch]` — canonical OTel order; only KEPT traces flow into batch processor; matches research/ARCHITECTURE.md line 185 verbatim; fixes the misleading forecast comment in otelcol-config.yaml line 180 | ✓ |
| After batch | `[memory_limiter, batch, tail_sampling]` — what the existing file comment forecasts; functionally still works (tail_sampling buffers internally) but means batch runs on 100% of spans even though ~80% will be dropped; no upstream OTel example uses this order | |
| You decide | Defer to planner (recommended fallback to option 1 since the canonical answer is unambiguous) | |

**User's choice:** Before batch (Recommended)
**Notes:** Locks D-T1 and triggers fix of misleading forecast comment in otelcol-config.yaml line 180.

### Q2: Flat list vs composite policy structure?

| Option | Description | Selected |
|--------|-------------|----------|
| Flat list | Three top-level policies; matches TSAMP-01 verbatim; matches research/PITFALLS.md F2-1 example; simplest YAML | |
| Composite with sub-policy rate-limits | `composite` envelope wraps three sub-policies, each with `max_total_spans_per_second` cap; production-realistic; adds rate-limit lesson on top of OR-semantics | ✓ |
| You decide | Defer to planner | |

**User's choice:** Composite with sub-policy rate-limits
**Notes:** Elevates the lesson from "three independent policies, OR-semantics" to "three policies with per-branch rate caps under a composite envelope." Establishes user pattern: "production-realistic over workshop-minimal."

### Q3: Composite per-branch rate-limit values?

| Option | Description | Selected |
|--------|-------------|----------|
| errors=100/s, slow=50/s, prob=50/s | Errors largest cap; slow only fires on artificially-slow traces; prob caps fallback at ~25%; total ~200/s tracks load.sh steady throughput | |
| errors=unlimited, slow=20/s, prob=40/s | Errors uncapped (production logic 'always keep errors'); slow + prob each capped tightly so bursts visibly clamp; lower total ceiling demonstrates the rate-limit lesson harder | ✓ |
| You decide | Defer to planner | |
| Reconsider — go back to flat | Revert D-T2 if composite YAML complexity makes the OR-semantics lesson too hard to read | |

**User's choice:** errors=unlimited, slow=20/s, prob=40/s
**Notes:** Reinforces "errors are load-bearing observability target" framing. Locks D-T3.

### Q4: Where should TSAMP-02's mandatory comment block live?

| Option | Description | Selected |
|--------|-------------|----------|
| Big comment block above the composite, sub-comments per branch | Top block: four-tier priority chain + composite envelope addendum; per-branch sub-comments per sub-policy; ~25 lines; matches Phase 10 D-04 teaching-grade YAML style | ✓ |
| Single comment block above tail_sampling, no per-branch annotation | Satisfies TSAMP-02 literally; lighter (~12 lines); attendees apply priority chain to each sub-policy themselves | |
| Comments + a README §11 priority-chain table | Per-branch annotation + mirror in README so attendees can read it without opening YAML | |

**User's choice:** Big comment block above the composite, sub-comments per branch (Recommended)
**Notes:** Locks D-T4. The YAML is the textbook (D-04 lineage); README §11 paraphrases.

---

## Latency-policy realism

### Q1: How should the workshop make the latency policy actually fire?

| Option | Description | Selected |
|--------|-------------|----------|
| WIDGET-SLOW SKU branch in producer | Add `Thread.sleep(1500)` if `sku == "WIDGET-SLOW"`; new oha stream in load.sh; mirrors WIDGET-* SKU family pattern; ~5 lines of Java + JavaDoc | ✓ |
| ?slow=true query param + Thread.sleep | Producer reads query param; load.sh adds curl-based stream; less consistent with payload-based WIDGET-* pattern | |
| Keep latency policy inert (educational only) | Most honest about what the YAML key MEANS; zero Java changes; deferred idea for future synthetic-slow lesson | |
| Set threshold_ms artificially low (e.g., 100ms) | Drop threshold so normal traces fire policy; cheating semantically (latency policies are for outliers, not normal traffic) | |

**User's choice:** WIDGET-SLOW SKU branch in producer (Recommended)
**Notes:** Bends the "pure Collector phase" framing slightly but pedagogical payoff (all three policies fire) is high. Locks D-T5.

### Q2: Sleep location and duration?

| Option | Description | Selected |
|--------|-------------|----------|
| Inside producer's INTERNAL span, sleep 1500ms | Span duration > 1.5s, latency policy fires reliably above 1000ms threshold (50% buffer); smallest code change | ✓ |
| Inside consumer's processing service | Producer returns 202 fast; consumer span is the slow one; more realistic 'slow downstream' scenario; needs SKU wired through AMQP payload | |
| Sleep in BOTH services | Demonstrates latency policy uses MAX-span duration; more contrived; more code | |
| You decide | Defer to planner | |

**User's choice:** Inside producer's INTERNAL span, sleep 1500ms (Recommended)
**Notes:** Locks D-T6.

### Q3: How should load.sh expose the slow stream?

| Option | Description | Selected |
|--------|-------------|----------|
| New SLOW_RPS env var, default 2 rps, fourth steady oha stream | Mirrors IDEMPOTENT_RPS pattern verbatim; can be disabled by setting SLOW_RPS=0; heartbeat output adds 'slow' line | ✓ |
| Folded into burst stream (BURST_PRIORITY=slow + WIDGET-SLOW payload) | Reuse burst loop's plumbing via new BURST_SKU env var; less code but ties slow-trace generation to burst cadence (300s idle by default — too sparse) | |
| One-shot mise task: `mise run demo:slow-burst` | Don't touch load.sh; separate mise task one-shot fires N slow requests; means README has TWO commands to demo tail sampling | |

**User's choice:** New SLOW_RPS env var, default 2 rps, fourth steady oha stream (Recommended)
**Notes:** Locks D-T7. Pattern symmetry with existing streams = cheap maintenance.

### Q4: Should WIDGET-SLOW also trigger the deterministic 10%-failure path?

| Option | Description | Selected |
|--------|-------------|----------|
| Leave existing 10% counter unchanged | Slow traces share AtomicInteger counter; ~10% of slow traces hit BOTH `status_code=ERROR` AND `latency>1s` — perfect OR-semantics demonstration; zero new code | ✓ |
| Make WIDGET-SLOW skip the 10% failure | Guard in ProcessingService: SKU == WIDGET-SLOW increments counter but never throws; cleaner separation; more code | |
| Make WIDGET-SLOW deterministically error-free | Separate counter, no failure path; most explicit but most code | |

**User's choice:** Leave existing 10% counter unchanged (Recommended)
**Notes:** Locks D-T8. Slow+error overlap is exactly the OR-semantics demonstration the workshop wants.

---

## ON/OFF demo mechanism

### Q1: Which ON/OFF demo mechanism?

| Option | Description | Selected |
|--------|-------------|----------|
| Git-tag time-travel: capture OFF at step-10, ON at step-11 | OFF screenshot captured manually NOW pre-Phase-11; ON captured manually post-Phase-11; mirrors Phase 10 D-13 / Phase 7 D-04 manual-one-shot precedent; zero runtime toggle code | ✓ |
| Commented YAML toggle: attendees uncomment + restart | `# UNCOMMENT TO DISABLE TAIL SAMPLING:` block in otelcol-config.yaml; attendees toggle, restart Collector; adds confusion (ON state in repo, OFF state commented) | |
| docker-compose override file | Two compose configs: default = ON; override mounts `otelcol-config-no-tail.yaml`; most production-realistic but heavy | |
| Two side-by-side YAML config files | Reference-only OFF YAML + screenshot pair; hybrid: time-travel screenshots + frozen reference YAML for code-diff teaching | |

**User's choice:** Git-tag time-travel: capture OFF at step-10, ON at step-11 (Recommended)
**Notes:** Locks D-T9. The YAML in the repo is the truth, no commented-out OFF blocks.

### Q2: What should the screenshot SHOW?

| Option | Description | Selected |
|--------|-------------|----------|
| Tempo Search: 'Last 5 min' trace count, identical query | `Service Name = order-producer`, same time range; OFF ~1500 traces; ON ~600 traces; concrete numbers, easy to visually verify ratio | ✓ |
| Grafana panel: `rate(tempo_distributor_spans_received_total[1m])` | Time-series panel showing rate of accepted spans; harder to read at a glance | |
| Both — Tempo Search count + Grafana rate panel | Two-panel screenshot; most informative but more capture work | |
| Side-by-side trace search counts via dashboard variable | New Grafana panel with two Tempo queries rendering ON/OFF ratio; requires modifying ose-otel-demo.json (which Phase 10 D-01 deliberately preserved) | |

**User's choice:** Tempo Search: 'Last 5 min' trace count, identical query (Recommended)
**Notes:** Locks D-T10. README caption highlights numeric ratio.

### Q3: README §11 narrative depth?

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-10-equivalent depth | 7 sub-sections, ~100-150 lines: YAML adds, annotated YAML excerpt, screenshot pair, OR-semantics explained, WIDGET-SLOW interaction, F2-3 callout, load command; matches v1.0 README density | ✓ |
| Lighter — prose + screenshot pair only | Short (~40 lines); YAML's own comment-block does heavy teaching; README is the index | |
| Hybrid: README has screenshot + TL;DR table; full YAML in config | ~60 lines; single source of truth for each piece of content | |

**User's choice:** Phase-10-equivalent depth (Recommended)
**Notes:** Locks D-T11. Composite + rate-limits + OR-semantics need room to breathe.

### Q4: Where does the F2-3 head-vs-tail callout live?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline blockquote at the end of §11 | Mirrors ROADMAP.md Phase-16 callout verbatim; Phase 16 README will back-link to it | ✓ |
| Brief inline mention + dedicated 'Sampling Layer Interactions' subsection | Dedicated subsection separates lesson from trap; more structure | |
| YAML comment block + brief README mention pointing to YAML | Heavy callout in otelcol-config.yaml; README has one-liner; catches attendees who jump to file | |

**User's choice:** Inline blockquote at the end of §11 (Recommended)
**Notes:** Locks D-T12.

---

## Self-observability of tail_sampling

### Q1: Add Grafana panels for tail_sampling diagnostic metrics?

| Option | Description | Selected |
|--------|-------------|----------|
| Add a 'Tail Sampling diagnostics' row to ose-otel-demo dashboard | New collapsed row with 4 panels: traces_sampled by policy, spans_dropped, late_span_total (F2-2), decision_latency histogram; collapsed by default | ✓ |
| Just the README-curl pattern, no dashboard panels | README §11 includes 2-3 `curl localhost:8888/metrics | grep tail_sampling` one-liners; F2-2 mitigation lives in YAML comment + README curl | |
| Both — dashboard row AND README curl one-liners | Dashboard row for visual learners, curl one-liners for command-line learners; more teaching surface, more work | |

**User's choice:** Add a 'Tail Sampling diagnostics' row to ose-otel-demo dashboard (Recommended)
**Notes:** Locks D-T13. First v2.0 phase to amend ose-otel-demo.json (Phase 10 D-01 deliberately preserved it).

### Q2: Add `mise run verify:tail-sampling` task?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — curl Collector :8888 and assert tail_sampling processor present + emitting decisions | Asserts metric exists AND `policy=` label values match all three configured names; catches typos, missing processor, drift | ✓ |
| Yes — lighter: just assert the processor is present, no per-policy check | Simpler grep; doesn't catch policy-name typos | |
| No — dashboard row + README screenshot pair are enough proof | Dashboard panels themselves are the verification surface; if they show zero data, attendees know something's wrong | |
| You decide | Defer to planner; precedent suggests Phase 11 should add one for consistency | |

**User's choice:** Yes — curl Collector :8888 and assert tail_sampling processor present + emitting decisions (Recommended)
**Notes:** Locks D-T14. Mirrors verify:datasources / verify:images naming.

### Q3: Policy NAMES in the YAML?

| Option | Description | Selected |
|--------|-------------|----------|
| keep-errors, keep-slow, probabilistic-fallback | Self-documenting verb-style names; matches OTel docs example style; stable contract for verify gate; README walkthrough quotes them verbatim | ✓ |
| errors, slow, probabilistic | Shorter; more terse YAML; slightly less self-documenting | |
| policy-1-status-error, policy-2-latency, policy-3-probabilistic | Numbered + descriptive; verbose; OR-semantics lesson explains ordering doesn't change OUTCOME, so numbering may mislead | |

**User's choice:** keep-errors, keep-slow, probabilistic-fallback (Recommended)
**Notes:** Locks D-T15. They're a stable contract referenced by YAML + dashboard JSON + verify task.

### Q4: Drift guard between YAML policy names and dashboard panel queries?

| Option | Description | Selected |
|--------|-------------|----------|
| Wire verify:tail-sampling to assert all three policy names + JSDoc-style top comment in dashboard JSON | Already-decided D-T14 task does the runtime assertion; dashboard JSDoc note reinforces the contract; belt + suspenders | ✓ |
| Add a separate verify:dashboard-tail-sampling-row task | Loads ose-otel-demo.json and asserts row queries reference the three policy names; catches drift in EITHER direction; heavier | |
| Just verify:tail-sampling — dashboard drift is acceptable | Don't over-engineer; dashboard going blank becomes obvious | |

**User's choice:** Wire verify:tail-sampling to assert all three policy names (Recommended)
**Notes:** Locks D-T16.

---

## Claude's Discretion

The user explicitly deferred the following to the planner/researcher (no specific user pick):
- Exact YAML key shape for the `composite` policy (pull from upstream OTel collector-contrib tail_sampling README)
- Exact JavaDoc wording on the WIDGET-SLOW branch in `OrderService.place()`
- `Thread.sleep(1500)` vs alternatives (`CompletableFuture.supplyAsync` with delayed executor) — simplest mechanism preferred
- Exact PromQL phrasing for the four diagnostic panels
- Exact `verify:tail-sampling` bash regex
- README §11 word-for-word phrasing for the OR-semantics narrative (paraphrase from research/PITFALLS.md F2-1 in the workshop's voice)
- Whether the OFF screenshot needs an automated `mise run` task or stays manual (manual one-shot per D-T9 / D-13 precedent unless planner finds an automatable shape)
- Plan count + wave structure for Phase 11 (likely 3-4 plans; planner decides via dependency analysis)

## Deferred Ideas

- Multi-collector tail sampling with loadbalancing exporter — research/ARCHITECTURE.md Anti-Pattern 1 explicitly excludes
- `drop`-decision policy for excluding `/actuator/health` traces — fourth policy beyond TSAMP-01's three; defer to v2.x or future tail-sampling deepdive
- Synthetic high-latency request scenario producing P99 exemplar dot demo — partially fulfilled by D-T5; future polish pairs with Phase 12
- Test coverage for tail_sampling rate-limit caps — Collector-side processor not unit-testable from Java; verify:tail-sampling gate is the runtime substitute
- Automated screenshot capture for ON/OFF pair via Playwright — Phase 7-07 documented why fragile across older tags
- Collector self-metrics dashboard panel for ALL processors (not just tail_sampling) — broader "Collector internals" row; future polish, not Phase 11 load-bearing
- README anchor IDs for cross-linking from Phase 16 — Phase 11 forward-flags via D-T12 blockquote; planner picks anchor naming at plan time
- `mise run verify:dashboard-tail-sampling-row` task — option B from D-T16 question; revisit if dashboard drift becomes a real problem
