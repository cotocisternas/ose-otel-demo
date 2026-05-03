# Phase 12: Exemplars: Metrics to Trace Click-Through - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 12-exemplars-metrics-to-trace-click-through
**Areas discussed:** Histogram scope fix (F3-1), Dashboard panel config, Verification & README depth, Consumer exemplar value

---

## Histogram scope fix (F3-1)

### Q1: How should the scope fix be framed in the workshop?

| Option | Description | Selected |
|--------|-------------|----------|
| Silent infrastructure fix (Recommended) | Restructure to manual scope management as part of Phase 12. No workshop narrative. ~4-line diff. | ✓ |
| Teaching moment (gotcha callout) | Same code fix + README "Gotcha" callout explaining why try-with-resources breaks exemplars. ~10 extra README lines. | |
| Before/after code diff in README | Show v1.0 (broken) vs v2.0 (fixed) side-by-side. Most teaching value but heaviest narrative weight for a 4-line diff. | |

**User's choice:** Silent infrastructure fix
**Notes:** None

### Q2: Should the scope fix also apply to the consumer service?

| Option | Description | Selected |
|--------|-------------|----------|
| Producer only (Recommended) | Only HttpServerSpanFilter.java records the histogram. Consumer has no HTTP filter. | ✓ |
| Check consumer too | Verify whether consumer has any metric recording outside span scope. | |

**User's choice:** Producer only
**Notes:** None

### Q3: Should the JavaDoc comment explain WHY?

| Option | Description | Selected |
|--------|-------------|----------|
| One-line WHY comment (Recommended) | Single comment: "close scope AFTER record() so ExemplarFilter.traceBased() sees active span". | ✓ |
| No comment | Code structure speaks for itself. | |

**User's choice:** One-line WHY comment
**Notes:** None

---

## Dashboard panel config

### Q1: Where should the new exemplar-bearing histogram panel live?

| Option | Description | Selected |
|--------|-------------|----------|
| New collapsed row (Recommended) | New "Exemplars (Phase 12)" row. Follows Phase 11 row pattern. Keeps top-level clean. | ✓ |
| Add to existing RED row | Add histogram panel alongside RED Metrics panel 3. More visible but heavier top row. | |
| Replace spanmetrics in RED panel | Swap spanmetrics with SDK histogram. Cleaner but loses Tempo-generated signal. | |

**User's choice:** New collapsed row
**Notes:** None

### Q2: What panels should the row contain?

| Option | Description | Selected |
|--------|-------------|----------|
| Single histogram + exemplars (Recommended) | One timeseries panel: p50/p95/p99 with exemplar query. Click dot → Tempo. | ✓ |
| Histogram + request rate by method | Two panels: histogram + rate by method. Richer but two panels. | |
| Histogram + heatmap variant | Two panels: timeseries + native histogram heatmap. Adds Mimir config complexity. | |

**User's choice:** Single histogram + exemplars
**Notes:** None

### Q3: Should the row be collapsed or open?

| Option | Description | Selected |
|--------|-------------|----------|
| Open by default (Recommended) | Exemplars are the headline artifact. Attendee sees dots immediately. | ✓ |
| Collapsed (consistent) | Matches Phase 7/11 collapsed-row convention. Keeps top-level clean. | |

**User's choice:** Open by default
**Notes:** None

---

## Verification & README depth

### Q1: Should Phase 12 have a mise run verify:exemplars task?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, lightweight (Recommended) | curl-based task querying Mimir /api/v1/query_exemplars. Catches filter/exporter/storage issues. | ✓ |
| No verify task, visual only | Dashboard IS the verification. Dots visible = success. | |
| Verify via integration test | OrderFlowIT.java assertion on InMemoryMetricReader. Catches SDK-side only. | |

**User's choice:** Yes, lightweight
**Notes:** None

### Q2: How deep should README §12 be?

| Option | Description | Selected |
|--------|-------------|----------|
| Focused walkthrough (~60-80 lines) | Three layers narrative. Lighter than Phase 11. | |
| Phase-11-equivalent (~100-150 lines) | Full depth: concept intro, three layers, code excerpts, cardinality, verify, screenshot. | ✓ |
| Minimal pointer (~30 lines) | Brief "what changed" + "how to verify". Trust code is self-explanatory. | |

**User's choice:** Phase-11-equivalent
**Notes:** None

### Q3: Screenshot approach for step-12-exemplars.png?

| Option | Description | Selected |
|--------|-------------|----------|
| Single screenshot (dots visible) | One PNG showing exemplar dots. Manual one-shot capture. Phase 18 replaces later. | |
| Annotated screenshot | Same + visual annotation (arrow). Harder to automate. | |
| Screenshot pair (no dots / dots) | Before/after pattern. Less dramatic than tail-sampling count difference. | |

**User's choice:** (Other) No screenshots until Phase 18 automates the full process
**Notes:** Deferred all screenshot captures to Phase 18 Playwright automation. README uses placeholders only.

### Q4: README placeholder style?

| Option | Description | Selected |
|--------|-------------|----------|
| Placeholder + caption (Recommended) | HTML img tag + alt text + one-line caption describing what the screenshot will show. | ✓ |
| Just the image tag | Bare placeholder. Phase 18 fills it in. | |

**User's choice:** Placeholder + caption
**Notes:** None

---

## Consumer exemplar value

### Q1: Consumer has no histogram metric. How to handle ExemplarFilter?

| Option | Description | Selected |
|--------|-------------|----------|
| Add to both, teach asymmetry (Recommended) | SC#1 compliance. README note: counter exemplars exist but are API-only, not chart dots. | ✓ |
| Add to both, no explanation | Both get filter. No asymmetry note. Simpler but attendees may wonder. | |
| Producer only, skip consumer | Avoids dead-code feel. Requires rewording SC#1. | |

**User's choice:** Add to both, teach the asymmetry
**Notes:** None

---

## Claude's Discretion

- Exact PromQL expressions for the histogram panel
- Exact verify:exemplars bash implementation
- Panel gridPos, ID numbering, JSON structure
- README §12 word-for-word phrasing
- Collector exporter key name verification (send_exemplars vs exemplars.send)
- ExemplarFilter import path verification against SDK 1.61.0

## Deferred Ideas

None — discussion stayed within phase scope.
