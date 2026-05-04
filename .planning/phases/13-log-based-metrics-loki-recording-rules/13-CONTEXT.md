# Phase 13: Log-Based Metrics (Loki Recording Rules) - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Enable the Loki ruler's first recording rule to derive an error-rate metric from application log patterns, remote-write it to Mimir, and visualize the log-derived metric alongside the SDK-emitted `orders.created` counter in a new Grafana dashboard panel — teaching the "metrics from app code vs metrics derived from logs" contrast.

This is a **pure infrastructure phase** — zero Java code changes. The entire teaching surface is one Loki recording rule YAML file, one Grafana dashboard panel, one `mise run verify:log-metrics` task, and a README section.

Phase boundaries (locked by REQUIREMENTS.md LMET-01..03 + this discussion):
- The Loki ruler is ALREADY pre-enabled in Phase 10 D-07 (`loki-config.yaml` ruler block, remote-write to Mimir, volume mount, `evaluation_interval: 1m`). **No `loki-config.yaml` change is required** — Phase 13 drops a rule file into the bind-mounted `infra/observability/loki-rules/` dir.
- One recording rule YAML file: `infra/observability/loki-rules/order-errors.yaml`
- One new open Grafana dashboard row "Log-Based Metrics (Phase 13)" with a single timeseries panel
- One `mise run verify:log-metrics` verification task (two-tier: Loki rules API + Mimir query)
- README §13 walkthrough (~100-150 lines)
- Placeholder screenshot deferred to Phase 18 Playwright pipeline

Out of scope for this phase:
- Java code changes of any kind — this is a pure Collector/backend/Grafana phase
- Additional recording rules beyond the single `log:order_errors:rate2m`
- Alerting rules (Loki ruler supports them, but no Alertmanager in the workshop stack)
- Head sampling or baggage (Phases 16)
- JDBC/JPA spans (Phase 14) or HTTP client spans (Phase 15)
- Manual screenshot capture — Phase 18 automates all screenshots (D-E9 precedent)

</domain>

<decisions>
## Implementation Decisions

### Dashboard panel design

- **D-L1:** **Single timeseries panel, both series as rates** — one panel overlaying `rate(orders_created_total[2m])` (SDK-emitted) alongside `log:order_errors:rate2m` (Loki-derived). Both are events/sec on the same Y-axis, same scale. Teaching point: "the gap between the SDK-emitted creation rate and the log-derived error rate is your success rate." This is the cleanest visual comparison — same unit, same axis, direct overlap. The `[2m]` window on the `rate()` wrapping the SDK counter matches the recording rule's `[2m]` window for fair comparison.

- **D-L2:** **New open row "Log-Based Metrics (Phase 13)"** — placed AFTER the Exemplars row (Phase 12) and BEFORE the collapsed diagnostic rows (Deeper-dive, Tail Sampling diagnostics). Open by default because this is the headline teaching artifact for Phase 13 — attendees need to see it immediately. Follows Phase 12 D-E4 precedent (open row for headline teaching artifacts, collapsed for diagnostics). Uses the same additive row pattern (discrete block, no edits to existing panels, no UID changes).

- **D-L3:** **Teaching callout in panel description** — panel `description` field includes: "The gap between the SDK-emitted creation rate and the log-derived error rate is your success rate. The SDK counter is the source of truth; the log-derived metric is an ops-team approximation that requires no code change." Matches Phase 11 D-T16 belt+suspenders spirit — the dashboard is self-documenting.

### Verification approach

- **D-L4:** **`mise run verify:log-metrics` — two-tier check** — Tier 1: curl Loki `/loki/api/v1/rules`, assert the rule group name and rule name (`log:order_errors:rate2m`) appear in the response. Tier 2: curl Mimir `/api/v1/query?query=log:order_errors:rate2m`, assert the metric has recent non-empty data. Catches: rule not loaded, YAML syntax error, remote-write broken, metric name typo, Mimir not receiving. Mirrors `verify:tail-sampling`'s two-tier pattern with retry logic.

- **D-L5:** **Task name: `verify:log-metrics`** — aligns with the teaching concept ("log-based metrics"), not the mechanism ("recording-rules"). Consistent with `verify:tail-sampling` (concept) and `verify:exemplars` (concept).

### README §13 narrative

- **D-L6:** **Same depth as Phases 11/12: ~100-150 lines** — the teaching surface is conceptual (how recording rules work, the Loki ruler → Mimir pipeline, the `log:` prefix convention, equivalence vs divergence) even though the actual change is one YAML file. Phase 12 chose this depth for exemplars despite being "three lines of SDK code" — same rationale applies.

- **D-L7:** **Lead with the zero-code angle** — open with: "Step 13 is the only step with no Java changes — everything happens in YAML." Then walk through: (1) what a recording rule is, (2) annotated rule YAML excerpt, (3) the Loki ruler → Mimir pipeline explained, (4) the Grafana panel showing both series, (5) the "gap is your success rate" callout, (6) short comparison table (SDK metrics vs log-derived metrics), (7) `mise run verify:log-metrics` invocation, (8) placeholder screenshot.

- **D-L8:** **Short "when to use which" comparison table** — 3-4 row markdown table: | Dimension | SDK Metric | Log-Derived | with rows for Latency, Accuracy, Code change required, Cardinality control. Quick reference capturing the pedagogical payoff. ~15 lines. Placed after the pipeline walkthrough, before the verify invocation.

- **D-L9:** **Placeholder screenshot deferred to Phase 18** — README includes `<img>` reference for `step-13-log-based-metrics.png` with alt text + one-line caption. Matches Phase 12 D-E9/D-E10 precedent. Phase 18's Playwright script captures it from the live dashboard.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact LogQL expression for the recording rule (the LMET-02 requirement specifies the expression, but researcher verifies syntax against Loki 3.7.1 ruler docs)
- Exact Loki ruler YAML structure (groups → name → rules → record/expr format) — researcher verifies against Loki 3.7.1 recording rule docs
- Exact PromQL expressions for the dashboard panel (SDK counter `rate()` wrapper, log-derived metric direct query, label matchers, legend format)
- Panel `gridPos` coordinates, ID numbering (next free after existing panels), and JSON structure — follow `ose-otel-demo.json` conventions
- `verify:log-metrics` exact bash implementation (retry logic, timeout, jq parsing) — planner picks a defensible pattern mirroring `verify:tail-sampling` / `verify:exemplars`
- README §13 word-for-word phrasing — paraphrase from research/FEATURES.md and PITFALLS.md in the workshop's voice
- Whether the recording rule needs a `labels:` block for additional labeling (likely not — `sum by (service_name)` already carries the grouping label through)
- Loki ruler rule group name (likely `order-error-rules` or similar descriptive name)
- Color assignment for the two series in the panel (planner picks distinguishable colors from Grafana's default palette)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service duplication irrelevant here — no Java changes), WORK-01 (annotated git tags on `main`)
- `.planning/REQUIREMENTS.md` § Log-Based Metrics (LMET-01..03) — locked requirements for ruler enablement, recording rule definition, and Grafana visualization
- `.planning/ROADMAP.md` Phase 13 section — pedagogical headline, Success Criteria #1–3, pitfall mitigations (F4-1, F4-2, F4-3, F4-4), git tag `step-13-log-based-metrics`
- `.planning/STATE.md` — Phase 10/11/12 completion records; "Phase 13 research flag: Loki ruler remote-write config YAML syntax for 3.7.1 may differ between ARCHITECTURE.md and PITFALLS.md accounts. Verify against live container during Phase 13 planning."

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes operational arc (Phase 10 unblocks 11/12/13)
- `.planning/research/ARCHITECTURE.md` — Loki recording rules architecture, ruler config rationale
- `.planning/research/PITFALLS.md` § F4 (F4-1 naming prefix, F4-2 rate-window aliasing, F4-3 volume mount path, F4-4 cardinality) — concrete mitigation steps
- `.planning/research/FEATURES.md` § "V2-DF-4" or log-based-metrics sections — original rationale for log-derived metrics feature

### Phase 10 carryover (MUST read — ruler config is Phase 10 D-07)

- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — D-07 (Loki ruler pre-enabled with empty rules dir, remote-write to Mimir, evaluation_interval: 1m, F4-3 volume mount path mitigation), D-04 (teaching-grade YAML style), D-01 (datasource UID preservation)
- `.planning/phases/12-exemplars-metrics-to-trace-click-through/12-CONTEXT.md` — D-E4 (open row for headline teaching artifacts), D-E9 (screenshot deferral to Phase 18), D-E7 (verify task pattern)
- `.planning/phases/11-tail-sampling-at-the-collector/11-CONTEXT.md` — D-T13 (collapsed row additive pattern in ose-otel-demo.json), D-T14 (two-tier verify task pattern)

### Files this phase EDITS (must read before planning)

- `infra/observability/loki-rules/order-errors.yaml` — NEW file (currently only `.gitkeep` exists). The recording rule YAML lands here.
- `grafana/dashboards/ose-otel-demo.json` — additive new row "Log-Based Metrics (Phase 13)" with single timeseries panel (D-L1/D-L2). Next panel ID after existing panels. No edits to existing panels or UIDs.
- `mise.toml` — additive `[tasks."verify:log-metrics"]` block (D-L4/D-L5)
- `README.md` — additive §13 section (~100-150 lines per D-L6/D-L7/D-L8)

### Files this phase does NOT edit (pre-wired in Phase 10)

- `infra/observability/loki-config.yaml` — ruler block already enabled (Phase 10 D-07). NO CHANGES NEEDED. The ruler scans `infra/observability/loki-rules/` on (re)start; SIGHUP triggers reload.
- `docker-compose.yml` — bind-mount `./infra/observability/loki-rules:/loki/rules:ro` already in place. NO CHANGES NEEDED.
- `grafana/datasources.yaml` — no changes (Phase 10 D-02 pre-wired cross-signal datalinks)

### Upstream documentation references (research must consult)

- [Grafana Loki Recording Rules](https://grafana.com/docs/loki/latest/alert/#recording-rules) — ruler YAML format, `groups:` → `rules:` → `record:` / `expr:` schema for Loki 3.7.1
- [Loki Ruler configuration](https://grafana.com/docs/loki/latest/configure/#ruler) — `remote_write` clients map syntax verification against 3.7.1
- [Loki HTTP API /api/v1/rules](https://grafana.com/docs/loki/latest/reference/loki-http-api/#list-rules) — response format for verify:log-metrics Tier 1

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`infra/observability/loki-config.yaml` ruler block (lines 51-72)** — fully pre-wired by Phase 10 D-07: `ruler.storage.type: local`, `ruler.storage.local.directory: /loki/rules`, `ruler.remote_write.clients.mimir.url: http://mimir:9009/api/v1/push`, `evaluation_interval: 1m`, `enable_api: true`. Phase 13 adds ONLY the rule file — no config changes.
- **`infra/observability/loki-rules/.gitkeep`** — placeholder in the bind-mounted dir. Phase 13 replaces the empty state with `order-errors.yaml` (`.gitkeep` can be deleted or kept alongside).
- **`mise.toml` `[tasks."verify:tail-sampling"]` and `[tasks."verify:exemplars"]` blocks** — structural templates for D-L4's `verify:log-metrics`. Pattern: curl + retry + jq assertion; two-tier check; non-zero exit on violation.
- **`grafana/dashboards/ose-otel-demo.json` Exemplars row (Phase 12)** — structural template for D-L2's new open row (JSON shape for open rows, gridPos conventions, panel ID numbering after the highest existing ID).

### Established Patterns

- **Phase 10 D-07 "pre-enable now, populate later"** — Phase 10 enabled the ruler + volume mount; Phase 13 drops the rule file. This pattern means Phase 13 does NOT touch `loki-config.yaml` or `docker-compose.yml`.
- **Phase 10 D-04 teaching-grade YAML** — every block gets a WHY comment. The recording rule YAML file follows this style.
- **`mise run verify:*` family** — `verify:bom` (Phase 2), `verify:datasources` (Phase 10), `verify:images` (Phase 10), `verify:tail-sampling` (Phase 11), `verify:exemplars` (Phase 12), now `verify:log-metrics` (D-L4/D-L5).
- **Open row for headline teaching artifacts** — Phase 12 D-E4 (exemplars row = open, tail-sampling diagnostics = collapsed). Phase 13's log-based metrics row is the headline teaching artifact → open by default (D-L2).
- **Screenshot deferral to Phase 18** — Phase 12 D-E9 established that phases after Phase 11 defer screenshots to the Playwright automation pipeline. Phase 13 follows this precedent (D-L9).

### Integration Points

- **`infra/observability/loki-rules/order-errors.yaml`** — NEW file. Loki ruler scans this directory on startup and via SIGHUP. The bind-mount is already in `docker-compose.yml`. Adding the file + restarting Loki is sufficient.
- **`grafana/dashboards/ose-otel-demo.json`** — additive new row (D-L2). Next panel IDs after existing panels. No edits to existing panels or UIDs.
- **`mise.toml`** — additive `[tasks."verify:log-metrics"]` block near `verify:exemplars`.
- **`README.md`** — additive §13 section after §12.
- **Mimir** — receives the recording rule output metric via Loki ruler's remote-write. No Mimir config change needed — `auth_enabled: false` (Phase 10 STACK-05) means the ruler's push is accepted without `X-Scope-OrgID`.

</code_context>

<specifics>
## Specific Ideas

- The user chose **"both as rates" overlay** (D-L1) over side-by-side or dual-Y-axes — the strongest visual comparison format. This signals that the teaching point is the RELATIONSHIP between the two lines (the gap = success rate), not just their individual shapes. Planner should ensure the `rate()` window on the SDK counter matches the recording rule's `[2m]` window for a fair visual comparison.
- The user chose **open row** (D-L2) following Phase 12's precedent — this phase's panel is a headline teaching artifact, not a diagnostic deep-dive. Dashboard real estate is being used to signal pedagogical importance.
- The user chose **same ~100-150 line depth** (D-L6) despite Phase 13 being the smallest code change in v2.0. This confirms the Phase 12 pattern: teaching depth is proportional to conceptual complexity, not to line count of the change.
- The user chose **zero-code angle as the narrative hook** (D-L7) — "Step 13 is the only step with no Java changes." This frames log-derived metrics as an ops capability, which is the core teaching point. The comparison table (D-L8) then provides the structured "when to use which" reference.
- The user's consistent pattern is **reusing Phase 10 pre-wired infrastructure verbatim** — ruler config, volume mount, Mimir remote-write URL are all Phase 10 D-07 artifacts. Phase 13 should explicitly note "no config changes needed" in the README to reinforce the D-07 pre-enablement lesson.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

### Reviewed Todos (not folded)

None — `cross_reference_todos` step did not surface matches for Phase 13 scope.

</deferred>

---

*Phase: 13-log-based-metrics-loki-recording-rules*
*Context gathered: 2026-05-03*
