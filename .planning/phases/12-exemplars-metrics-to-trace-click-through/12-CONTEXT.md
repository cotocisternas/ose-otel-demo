# Phase 12: Exemplars: Metrics to Trace Click-Through - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire the three-layer exemplar plumbing — SDK `ExemplarFilter.traceBased()`, Collector `send_exemplars: true`, Grafana `exemplarTraceIdDestinations` (already pre-wired in Phase 10 D-02) — so a single click on an exemplar dot in the `http.server.request.duration` histogram panel lands on the originating trace in Tempo.

The attendee generates load, opens the `ose-otel-demo` dashboard, sees exemplar dots on the new histogram panel, clicks one, and arrives at the full trace waterfall in Tempo — no manual trace-ID copy-paste required.

Phase boundaries (locked by REQUIREMENTS.md EXMP-01..04 + this discussion):
- SDK: one `.setExemplarFilter(ExemplarFilter.traceBased())` call per service in `buildMeterProvider()`
- Collector: one `send_exemplars: true` line on the `prometheusremotewrite/mimir` exporter
- Grafana datasource: already pre-wired (`exemplarTraceIdDestinations` in Phase 10 D-02) — no change needed
- Scope fix: `HttpServerSpanFilter.doFilterInternal()` restructured from try-with-resources to manual scope management so histogram records inside active span scope (F3-1 mitigation)
- Dashboard: new open row "Exemplars (Phase 12)" with single histogram panel showing `http.server.request.duration` percentiles + exemplar dots
- Verification: `mise run verify:exemplars` task (Mimir exemplar API query)
- README §12: Phase-11-equivalent depth (~100-150 lines), placeholder screenshot deferred to Phase 18 Playwright

Out of scope for this phase:
- Head sampling (Phase 16) — SDK stays at `Sampler.parentBased(Sampler.alwaysOn())`
- Native histograms / heatmap visualization — standard classic histograms only
- Consumer-side histogram metrics — consumer has counters only; ExemplarFilter still set for SDK correctness
- Manual screenshot capture — Phase 18 automates all screenshots

</domain>

<decisions>
## Implementation Decisions

### F3-1 histogram scope fix (exemplar attachment guarantee)

- **D-E1:** **Silent infrastructure fix** — restructure `HttpServerSpanFilter.doFilterInternal()` from try-with-resources (`try (Scope scope = span.makeCurrent()) { ... } finally { record(); }`) to manual scope management (`Scope scope = span.makeCurrent(); try { ... } finally { record(); span.end(); scope.close(); }`). This ensures `Span.current()` returns the live SERVER span when `requestDuration.record()` fires, so `ExemplarFilter.traceBased()` attaches `trace_id`/`span_id`. The fix is presented as silent infrastructure — no README narrative about it. The code just works correctly for exemplars.

- **D-E2:** **Producer only** — the consumer service has no `HttpServerSpanFilter` (it listens on RabbitMQ, not HTTP). No equivalent scope fix needed on the consumer side.

- **D-E3:** **One-line WHY comment** — add a single comment above `scope.close()` in the finally block: `// close scope AFTER record() so ExemplarFilter.traceBased() sees active span`. Matches Phase 10 D-04 one-line WHY density.

### Dashboard panel configuration

- **D-E4:** **New row "Exemplars (Phase 12)" — OPEN by default** — unlike Phase 11's tail-sampling diagnostics row (collapsed), this row is open because exemplars are the headline teaching artifact. Attendees need to see the dots immediately when they open the dashboard. Uses the same additive row pattern (discrete block, no edits to existing panels, no UID changes).

- **D-E5:** **Single histogram panel** — one timeseries panel showing `histogram_quantile(0.50/0.95/0.99, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))` with the `exemplar: true` query flag enabled. Attendee sees percentile lines + exemplar dots overlaid. Click dot → Tempo. Minimal, focused teaching surface.

- **D-E6:** **Row position** — placed AFTER the existing top-level panels (Recent Traces, Service Graph, RED Metrics, Logs) but BEFORE the collapsed "Deeper-dive" and "Tail Sampling diagnostics" rows. This gives it visual priority without displacing the NOC overview.

### Verification & README approach

- **D-E7:** **`mise run verify:exemplars` task** — lightweight curl-based task that queries Mimir's exemplar API endpoint (`/api/v1/query_exemplars?query=http_server_request_duration_seconds_bucket&start=...&end=...`) and asserts ≥1 exemplar with a `trace_id` label exists. Follows Phase 10/11 `verify:*` pattern (bash one-liner, non-zero on failure). Catches: filter not set, send_exemplars missing, Mimir not storing exemplars.

- **D-E8:** **README §12 is Phase-11-equivalent depth (~100-150 lines)** — full walkthrough: (1) what exemplars are (concept), (2) the three layers explained, (3) annotated config excerpts for SDK + Collector + datasource, (4) cardinality safety (SC#4: only trace_id/span_id), (5) `mise run verify:exemplars` invocation, (6) click-through UX description, (7) placeholder screenshot.

- **D-E9:** **No manual screenshots — deferred to Phase 18** — no `step-12-exemplars.png` file captured in this phase. README includes the image reference as a placeholder. Phase 18's Playwright script captures it from the live dashboard.

- **D-E10:** **Placeholder + caption in README** — HTML img tag with alt text + one-line caption: "Exemplar dots on http.server.request.duration — click any dot to land on the originating trace in Tempo." Matches Phase 11's placeholder style for its ON/OFF pair.

### Consumer exemplar handling

- **D-E11:** **Both services get ExemplarFilter, teach the asymmetry** — SC#1 literal compliance: both `OtelSdkConfiguration.buildMeterProvider()` calls add `.setExemplarFilter(ExemplarFilter.traceBased())`. README §12 includes a one-line note explaining: "The consumer's counter exemplars exist in Mimir but are only visible via the API, not as chart dots — histograms are the natural exemplar surface." Teaching point: "set the filter everywhere, visualize where it makes sense."

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact PromQL expression for the histogram panel (bucket selection, rate window, label matchers) — planner picks defensible queries matching the existing RED Metrics panel's conventions.
- Exact `verify:exemplars` bash implementation (API endpoint URL construction, time range, jq parsing) — planner picks a defensible pattern mirroring `verify:tail-sampling`.
- Panel `gridPos` coordinates, ID numbering, and JSON structure — follow `ose-otel-demo.json` conventions from existing rows.
- README §12 word-for-word phrasing — paraphrase from research/FEATURES.md and PITFALLS.md in the workshop's voice.
- Whether the `prometheusremotewrite/mimir` exporter key is `send_exemplars` or `exemplars.send` — researcher verifies against collector-contrib v0.151.0 docs.
- Exact import statement for `ExemplarFilter` in `OtelSdkConfiguration.java` (likely `io.opentelemetry.sdk.metrics.export.ExemplarFilter` or `io.opentelemetry.sdk.metrics.ExemplarFilter`) — researcher confirms against SDK 1.61.0 API.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service duplication of `OtelSdkConfiguration`)
- `.planning/REQUIREMENTS.md` § Exemplars (EXMP-01..04) — locked requirements for SDK filter, Collector config, Grafana wiring, and visual verification
- `.planning/ROADMAP.md` Phase 12 section — pedagogical headline, Success Criteria #1–4, pitfall mitigations (X-1, F3-1, F3-2, F3-3), git tag `step-12-exemplars`
- `.planning/STATE.md` — Phase 10/11 completion records; verify:* task pattern documentation

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes operational arc
- `.planning/research/ARCHITECTURE.md` — exemplar pipeline description (if present); datasource wiring rationale
- `.planning/research/PITFALLS.md` § F3 (F3-1 span-scope-at-record-time, F3-2 exemplar cardinality, F3-3 datasource UID matching) — concrete mitigation steps
- `.planning/research/FEATURES.md` § "V2-DF-2" or exemplar sections — original rationale for click-through feature

### Phase 10 carryover (must read before touching datasources.yaml or ose-otel-demo.json)

- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — D-02 (exemplarTraceIdDestinations pre-wired, Tempo UID = "tempo"), D-04 (teaching-grade YAML style), D-14 (verify:images mise task pattern), D-03 (verify:datasources task pattern)
- `.planning/phases/11-tail-sampling-at-the-collector/11-CONTEXT.md` — D-T13 (collapsed row additive pattern in ose-otel-demo.json), D-T14 (verify:tail-sampling task pattern), D-T16 (JSDoc-style policy-name contract comment)

### Files this phase EDITS (must read before planning)

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — `buildMeterProvider()` method (line 452–497): add `.setExemplarFilter(ExemplarFilter.traceBased())` to `SdkMeterProvider.builder()` chain
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — same shape, same edit in `buildMeterProvider()`
- `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` — `doFilterInternal()` method (line 128–218): restructure from try-with-resources to manual scope management per D-E1
- `infra/observability/otelcol-config.yaml` — `prometheusremotewrite/mimir` exporter block (line 292–298): add `send_exemplars: true`
- `grafana/dashboards/ose-otel-demo.json` — add new open "Exemplars (Phase 12)" row with single histogram panel (D-E4/D-E5/D-E6)
- `mise.toml` — add `[tasks."verify:exemplars"]` block (D-E7)
- `README.md` — add §12 section (~100-150 lines per D-E8)

### Files this phase does NOT edit (pre-wired in Phase 10)

- `grafana/datasources.yaml` — `exemplarTraceIdDestinations` already wired (lines 50-56); UID = "tempo" matches the decomposed-stack Tempo datasource. NO CHANGES NEEDED.
- `infra/observability/tempo-config.yaml` — `send_exemplars: true` already set (line 64) for Tempo metrics_generator path. NOT the same as the Collector's PRW exporter setting.

### Upstream documentation references (research must consult)

- [OTel Java SDK ExemplarFilter API](https://opentelemetry.io/docs/languages/java/sdk/) — `ExemplarFilter.traceBased()` behavior, which labels are attached
- [OTel Collector prometheusremotewrite exporter docs](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/prometheusremotewriteexporter) — `send_exemplars` key verification against v0.151.0
- [Grafana exemplar configuration](https://grafana.com/docs/grafana/latest/datasources/prometheus/configure-prometheus-data-source/#exemplars) — panel-level `exemplar: true` query flag schema

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`grafana/datasources.yaml` lines 50-56** — `exemplarTraceIdDestinations` already wired with `name: trace_id`, `datasourceUid: tempo`, `urlDisplayLabel: "Trace: ${__value.raw}"`. This is the Grafana side of the three-layer plumbing — fully complete, no edits needed.
- **`infra/observability/tempo-config.yaml` line 64** — `send_exemplars: true` on Tempo's metrics_generator. This is a DIFFERENT pipeline path (Tempo → Mimir for service graph metrics). The Collector's PRW exporter is the path that carries SDK histogram exemplars.
- **`mise.toml` `[tasks."verify:tail-sampling"]` block** — structural template for D-E7's `verify:exemplars`. Pattern: curl + grep/jq + assertion; non-zero exit on violation.
- **`grafana/dashboards/ose-otel-demo.json` Tail Sampling row (panels 9-14)** — structural template for D-E4's new exemplar row (JSON shape for collapsed/open rows, gridPos conventions, panel ID numbering sequence: next free ID after 14).
- **`producer-service/.../HttpServerSpanFilter.java` lines 128-218** — the integration surface for D-E1 (scope fix). Current try-with-resources pattern at line 170; histogram recording at line 212 in finally block.

### Established Patterns

- **TRACE-01 / DOC-05** — `OtelSdkConfiguration` is per-service duplicated. Both get ExemplarFilter independently (D-E11). No extraction to otel-bootstrap.
- **Phase 10 D-02 "pre-wire now, activate later"** — Phase 10 wired the datasource surface; Phase 12 activates the SDK + Collector. This pattern means Phase 12 does NOT touch `grafana/datasources.yaml`.
- **`mise run verify:*` family** — `verify:bom` (Phase 2), `verify:datasources` (Phase 10), `verify:images` (Phase 10), `verify:tail-sampling` (Phase 11), now `verify:exemplars` (D-E7). Consistent bash-one-liner pattern.
- **Phase 10 D-04 teaching-grade YAML** — every block gets a one-line WHY comment. The `send_exemplars: true` line in otelcol-config.yaml follows this style.
- **Manual one-shot screenshots deferred to Phase 18** — no PNG files captured in Phase 12 (D-E9). README placeholders only.

### Integration Points

- **`OtelSdkConfiguration.buildMeterProvider()` in BOTH services** — single line added to `SdkMeterProvider.builder()` chain (between `.setResource(resource)` and `.registerMetricReader(metricReader).build()`). Import for `ExemplarFilter` added to imports block.
- **`HttpServerSpanFilter.doFilterInternal()` in producer** — restructure affects lines 170-217: try-with-resources becomes manual scope + try-finally. No behavioral change except exemplar attachment now works.
- **`infra/observability/otelcol-config.yaml` line 292-298** — one `send_exemplars: true` line added under `prometheusremotewrite/mimir:` block, with a WHY comment.
- **`grafana/dashboards/ose-otel-demo.json`** — additive new row (D-E4). Next panel ID after 14 (from Phase 11). No edits to existing panels or UIDs.
- **`mise.toml`** — additive `[tasks."verify:exemplars"]` block near `verify:tail-sampling`.
- **`README.md`** — additive §12 section after §11.

</code_context>

<specifics>
## Specific Ideas

- The user's pattern is **"deferred screenshots to Phase 18 automation"** — no manual PNG captures in Phase 12. README uses placeholders. This differs from Phase 11's D-T9 (manual one-shot) and establishes a new precedent: going forward, phases that would have manual screenshots just place the reference and let Phase 18 fill them in.
- The user chose **Phase-11-equivalent README depth** despite Phase 12 being architecturally simpler (three lines vs Phase 11's composite policy structure). This signals that the "three layers" conceptual narrative is valuable even when the code change is small — the teaching surface is understanding the plumbing, not the line count.
- The user chose **open row (not collapsed)** — breaking from the Phase 7/11 "collapsed by default" precedent. This signals that exemplars are core to the Phase 12 teaching goal (SC#3's "one click" UX must be immediately visible) while tail-sampling diagnostics were supplementary deep-dive content.
- The user values **"set it everywhere, visualize where it makes sense"** as a teaching point for exemplars on the consumer (D-E11). The asymmetry between histogram exemplars (visible) and counter exemplars (API-only) is itself a lesson worth one sentence.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

### Reviewed Todos (not folded)

None — `cross_reference_todos` step did not surface matches for Phase 12 scope.

</deferred>

---

*Phase: 12-exemplars-metrics-to-trace-click-through*
*Context gathered: 2026-05-03*
