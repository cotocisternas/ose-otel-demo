# Phase 13: Log-Based Metrics (Loki Recording Rules) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 13-log-based-metrics-loki-recording-rules
**Areas discussed:** Panel design & placement, Verification approach, README §13 narrative

---

## Panel Design & Placement

### Visualization approach

| Option | Description | Selected |
|--------|-------------|----------|
| Single panel, both as rates | One timeseries panel: `rate(orders_created_total[2m])` alongside `log:order_errors:rate2m`. Both events/sec, same Y-axis. Gap = success rate. | ✓ |
| Two panels side-by-side | Left: SDK rate. Right: log-derived rate. Comparison by juxtaposition. | |
| Single panel, dual Y-axes | One panel with two Y-axes: rate on left, counter total on right. Risks confusing mismatched scales. | |

**User's choice:** Single panel, both as rates
**Notes:** Cleanest visual comparison — same unit, same axis, direct overlay. The "gap between the lines is your success rate" is the headline teaching point.

### Row placement

| Option | Description | Selected |
|--------|-------------|----------|
| New open row after Exemplars | Headline teaching artifact — attendees see it immediately. Follows Phase 12 D-E4 precedent. | ✓ |
| New collapsed row at bottom | Compact by default; expand when reaching Step 13. Matches Phase 11 diagnostic-row treatment. | |
| Add to existing Exemplars row | Extend Phase 12 row. Keeps row count down but mixes teaching concepts. | |

**User's choice:** New open row after Exemplars
**Notes:** Open by default because this is the headline teaching artifact, not a deep-dive diagnostic.

### Panel description content

| Option | Description | Selected |
|--------|-------------|----------|
| Teaching callout | Description includes pedagogical framing: "gap = success rate", SDK vs log-derived context. | ✓ |
| Technical only | Just metric names, PromQL, data sources. Teaching stays in README. | |

**User's choice:** Teaching callout
**Notes:** Matches Phase 11 D-T16 belt+suspenders spirit — dashboard is self-documenting.

---

## Verification Approach

### Verification scope

| Option | Description | Selected |
|--------|-------------|----------|
| Two-tier: Loki rules + Mimir query | Tier 1: Loki /api/v1/rules (rule loaded). Tier 2: Mimir /api/v1/query (metric has data). Catches all failure modes. | ✓ |
| Mimir query only | Single check: metric exists in Mimir. Simpler but less diagnostic. | |
| Loki rules API only | Single check: rule loaded in Loki. Doesn't confirm remote-write works. | |

**User's choice:** Two-tier
**Notes:** Mirrors verify:tail-sampling's two-tier pattern.

### Task name

| Option | Description | Selected |
|--------|-------------|----------|
| verify:log-metrics | Aligns with teaching concept. Consistent with verify:tail-sampling, verify:exemplars. | ✓ |
| verify:recording-rules | Describes the Loki mechanism. More specific. | |

**User's choice:** verify:log-metrics
**Notes:** Concept-aligned naming consistent with the verify:* family.

---

## README §13 Narrative

### Depth

| Option | Description | Selected |
|--------|-------------|----------|
| Same depth, ~100-150 lines | Matches Phase 11/12 precedent. Conceptual depth despite small code change. | ✓ |
| Lighter, ~60-80 lines | Proportional to actual change. Recipe-style. | |
| Heavier, ~150-200 lines | Expanded conceptual section with detailed comparison table. | |

**User's choice:** Same depth, ~100-150 lines
**Notes:** Teaching depth proportional to conceptual complexity, not line count.

### Narrative hook

| Option | Description | Selected |
|--------|-------------|----------|
| Lead with zero-code angle | "Step 13 is the only step with no Java changes." Walk through recording rules, pipeline, panel, comparison. | ✓ |
| Lead with comparison angle | "Two ways to get the same metric." Side-by-side code comparison. | |
| Lead with ops-team persona | "Imagine you're on the ops team..." Persona-driven narrative. | |

**User's choice:** Lead with zero-code angle
**Notes:** Frames log-derived metrics as an ops capability — the core teaching point.

### Comparison table

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, short table (3-4 rows) | Dimensions: Latency, Accuracy, Code change required, Cardinality control. ~15 lines. | ✓ |
| No table, prose only | Woven into narrative. Less scannable. | |
| Yes, detailed table (6-8 rows) | Additional dimensions. More reference-material density. | |

**User's choice:** Yes, short table
**Notes:** Quick reference capturing the pedagogical payoff.

### Screenshots

| Option | Description | Selected |
|--------|-------------|----------|
| Defer to Phase 18 | Placeholder image reference. Phase 18 Playwright captures it. Matches D-E9 precedent. | ✓ |
| Manual one-shot now | Capture manually during this phase. Matches Phase 11 D-T9 precedent. | |

**User's choice:** Defer to Phase 18
**Notes:** Consistent with Phase 12 D-E9 precedent for post-Phase-11 phases.

---

## Claude's Discretion

- Exact LogQL expression syntax verification against Loki 3.7.1
- Loki ruler YAML file structure (groups/rules/record/expr)
- Exact PromQL expressions for dashboard panel
- Panel gridPos, ID numbering, JSON structure
- verify:log-metrics bash implementation details
- README §13 word-for-word phrasing
- Recording rule labels block necessity
- Rule group name
- Color assignments for panel series

## Deferred Ideas

None — discussion stayed within phase scope.
