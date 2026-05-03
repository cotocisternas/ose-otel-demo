---
slug: tail-sampling-recordpolicy
status: resolved
trigger: |
  Phase 11 verify:tail-sampling fails Tier 2 — Tempo finds 0 of 15722 traces with the
  tailsampling.composite_policy attribute despite the recordpolicy alpha feature gate
  being passed to otel-collector-contrib v0.151.0 in docker-compose.yml. Concurrently
  the tail_sampling buffer is pinned at num_traces=10000 (Grafana Panel 5) under
  workshop load, which may be starving the composite policy of decisions and hiding
  the recordpolicy issue. Goal: prove or rule out (a) gate not accepted by v0.151.0,
  (b) gate accepted but no spans flow through composite sub-policy decisions because
  buffer overflows, (c) gate works but Tempo write/search drops the attribute.
  Tier 1 self-metrics PASS — count_traces_sampled_total{policy="composite-policy"}
  is emitted at :8888.
created: 2026-05-03
updated: 2026-05-03
---

# Debug Session: tail-sampling-recordpolicy

## Symptoms

- **Expected behavior**: Tempo's TraceQL search `{ .tailsampling.composite_policy != "" }`
  (or equivalent) should return matching traces — every span retained by the
  tail_sampling processor's composite policy should carry the
  `tailsampling.composite_policy` resource/span attribute when the
  `processor.tailsamplingprocessor.recordpolicy` alpha feature gate is enabled in
  otel-collector-contrib.
- **Actual behavior**: Tempo search returns 0 of 15722 sampled traces with the
  attribute. The traces are flowing into Tempo (15722 is the post-sampling count),
  but the policy-name attribute is absent.
- **Error messages**: None at the collector or Tempo level — silent attribute drop.
- **Timeline / reproduction**:
  - Image: `otel/opentelemetry-collector-contrib:0.151.0`
  - Flag passed in `docker-compose.yml`: `--feature-gates=processor.tailsamplingprocessor.recordpolicy`
  - Tier 1 (self-metrics) PASSES: `count_traces_sampled_total{policy="composite-policy"}`
    is emitted on `:8888` — confirms the composite policy IS firing decisions.
  - Tier 2 (TraceQL attribute presence in Tempo) FAILS: 0 / 15722 hits.
  - Concurrent confound: Grafana Panel 5 (traces in memory sanity gauge) shows the
    tail_sampling buffer pinned at `num_traces=10000` under workshop load — the
    buffer may be saturating and ejecting traces before the composite sub-policy
    decision is recorded onto the trace.

## Hypotheses to investigate

- **H-A**: The `processor.tailsamplingprocessor.recordpolicy` feature gate is not
  accepted (or not yet present) in collector-contrib v0.151.0 — the flag is silently
  ignored. **Test**: query `/debug/featuregates` on the collector or grep the
  `--feature-gates` startup log for "registered"/"unknown" wording; cross-check
  v0.151.0 release notes for the exact gate ID.
- **H-B**: The gate IS accepted but the composite sub-policy decision never makes
  it onto the span attributes because the upstream buffer (`num_traces=10000`) is
  full — traces are evicted/decided-by-default before the composite branch is
  evaluated, so `recordpolicy` has nothing to write. **Test**: raise `num_traces`
  10–50× temporarily and re-run Tier 2; or query the collector self-telemetry for
  `_dropped_traces` / buffer eviction counters.
- **H-C**: The gate works AND the composite policy writes the attribute on the
  collector side, but Tempo's write path or search index drops/normalizes the
  attribute. **Test**: enable the `debug` exporter (or `file` exporter) on the
  collector's tail-sampled pipeline and inspect raw OTLP output; compare to what
  TraceQL returns.
- **H-D** (emerged during investigation): The gate works, the composite policy
  records the attribute, AND it survives the Tempo write path — but it is stamped
  onto the **InstrumentationScope** (`scopeSpans[].scope.attributes`), not onto the
  span or resource. Tempo 2.10's search APIs (legacy `tags=` and TraceQL
  `span.*`/`resource.*`/`.*`) do not search instrumentation-scope attributes, so
  the attribute is invisible to verify:tail-sampling Tier 2 even though it is
  present on disk.

## Current Focus

```yaml
hypothesis: |
  H-D confirmed. The recordpolicy gate writes tailsampling.composite_policy onto
  the InstrumentationScope of each kept ScopeSpans batch (not onto the span
  itself, not onto the resource). Tempo 2.10.5 stores it under scope=instrumentation
  but its search path has no scope-attribute filter, so Tier 2 always returns 0.
test: |
  1. Probe collector featuregate listing (rule out H-A)
  2. Send 30 error traces directly via OTLP HTTP, wait decision_wait, fetch one
     full trace from Tempo, inspect attribute placement
  3. Cross-reference upstream source (processor.go + util.go + composite.go) for
     the writer that places the attribute
expecting: |
  - featuregate listing contains processor.tailsamplingprocessor.recordpolicy → H-A out
  - sampled trace's ScopeSpans.scope.attributes carries tailsampling.* keys →
    H-C confirmed in the most specific form (H-D)
  - upstream source uses ss.Scope().Attributes().PutStr(...) → architectural
    decision, not a deployment bug
next_action: |
  H-D confirmed. Three viable fix routes:
  (1) Move the attribute onto spans via OTel Collector transform processor in the
      same pipeline AFTER tail_sampling (copy scope attribute onto spans).
  (2) Switch verify:tail-sampling Tier 2 from /api/search?tags= to /api/v2/search/tags?scope=instrumentation
      and assert the values via /api/v2/search/tag/{name}/values.
  (3) Combination — both, so the dashboard's TraceQL filter and the verify task
      both work without the workshop attendee needing scope=instrumentation knowledge.
reasoning_checkpoint: ""
tdd_checkpoint: ""
```

## Relevant artifacts

- `docker-compose.yml` — collector image pin and feature-gate flag
- `infra/observability/otelcol-config.yaml` — tail_sampling processor + traces pipeline
- `mise.toml` — verify:tail-sampling Tier 2 query
- `.planning/phases/11-tail-sampling-at-the-collector/11-RESEARCH.md` §2.3, §Code Examples 1
- `.planning/phases/11-tail-sampling-at-the-collector/11-04-PLAN.md` Task 1 — verify:tail-sampling block
- `.planning/phases/11-tail-sampling-at-the-collector/11-VERIFICATION.md` — Tier 1 / Tier 2 gate definitions
- Upstream source (collector-contrib v0.151.0):
  - `processor/tailsamplingprocessor/processor.go:596,815,854` (recordPolicy callsites)
  - `processor/tailsamplingprocessor/internal/sampling/util.go:99` (`SetAttrOnScopeSpans`)
  - `processor/tailsamplingprocessor/internal/sampling/composite.go:122` (composite sub-policy attr)

## Evidence

- timestamp: 2026-05-03T17:13Z
  source: docker run --rm otel/opentelemetry-collector-contrib:0.151.0 featuregate
  finding: |
    `processor.tailsamplingprocessor.recordpolicy` is registered in v0.151.0,
    Alpha, default false. Description matches the workshop's intent verbatim:
    "When enabled, attaches the name of the policy (and if applicable, composite
    policy) responsible for sampling a trace in the 'tailsampling.policy',
    'tailsampling.composite_policy', and `tailsampling.cached_decision` attributes."
    → **H-A FALSIFIED**: gate ID is correct in v0.151.0.

- timestamp: 2026-05-03T17:13Z
  source: docker inspect ose-otel-collector --format '{{json .Args}}'
  finding: |
    Container Args = `["--config=/etc/otelcol-contrib/config.yaml",
    "--feature-gates=processor.tailsamplingprocessor.recordpolicy"]`. Collector
    started without errors and reached "Everything is ready. Begin running and
    processing data." If the gate ID were unknown, collector would crash at
    startup. → confirms gate is **applied** at runtime.

- timestamp: 2026-05-03T17:14Z
  source: 30 OTLP-HTTP error spans (status=ERROR) → http://localhost:4318/v1/traces, 15s wait
  finding: |
    Tier 1 metric:
      otelcol_processor_tail_sampling_count_traces_sampled_total{...,
        policy="composite-policy", sampled="true", decision="sampled"} = 30
    All 30 error traces took the keep-errors composite sub-policy path.
    → composite policy IS evaluating sub-policies and recording metrics.

- timestamp: 2026-05-03T17:14Z
  source: curl http://localhost:3200/api/search?tags=tailsampling.composite_policy
  finding: |
    `{"traces":[],"metrics":{"inspectedTraces":39128,...}}` — 0 hits, ~39k inspected.
    Same 0 result for the **correctly-shaped** logfmt queries:
      tags=tailsampling.policy=composite-policy → 0
      tags=tailsampling.composite_policy=keep-errors → 0
    Same 0 result for TraceQL `{ span.tailsampling.composite_policy = "keep-errors" }`
    AND `{ resource.tailsampling.composite_policy = "keep-errors" }`
    AND untyped `{ .tailsampling.composite_policy = "keep-errors" }`.
    Tempo 2.10 rejects `scope.tailsampling...` with HTTP 400.

- timestamp: 2026-05-03T17:14Z
  source: curl http://localhost:3200/api/traces/{traceID} (ground-truth fetch)
  finding: |
    Sampled trace's structure:
      batches[0].resource.attributes = [{service.name: "debug-tail-sampling"}]
      batches[0].scopeSpans[0].scope.attributes = [
        {tailsampling.composite_policy: "keep-errors"},
        {tailsampling.policy: "composite-policy"}
      ]
      batches[0].scopeSpans[0].spans[0].attributes = [{debug.iter: 16}]   ← span attrs UNCHANGED
      batches[0].scopeSpans[0].spans[0].status.code = STATUS_CODE_ERROR
    The recordpolicy attributes ARE present on the trace — but on the
    InstrumentationScope, NOT on the spans nor on the Resource.

- timestamp: 2026-05-03T17:14Z
  source: curl http://localhost:3200/api/v2/search/tags?scope=instrumentation
  finding: |
    `{"scopes":[{"name":"instrumentation","tags":["tailsampling.composite_policy",
    "tailsampling.policy"]}],...}` — Tempo enumerates the keys under
    scope=instrumentation, but the `/api/search?tags=` and TraceQL search APIs
    only filter span+resource scopes. Confirms attribute is ingested + indexed
    but **unreachable** from the verify task's query path.

- timestamp: 2026-05-03T17:14Z
  source: |
    Upstream source v0.151.0:
    - processor/tailsamplingprocessor/processor.go:596,815,854
    - processor/tailsamplingprocessor/internal/sampling/util.go:99
    - processor/tailsamplingprocessor/internal/sampling/composite.go:122
  finding: |
    Both writers use `sampling.SetAttrOnScopeSpans(...)` which calls
    `ss.Scope().Attributes().PutStr(attrName, attrKey)` — attribute is placed on
    the InstrumentationScope explicitly. This is upstream architectural choice
    (not a defect): keeps per-span overhead at zero. The README claim that the
    attribute is "on the span" is misleading — the OTel proto wire shape places
    the attribute one level above (on the scope) where every span in that scope
    inherits it semantically but it is NOT a span-level attribute by the proto.
    → **H-D confirmed**: gate works as designed; verify:tail-sampling's query
    shape is incompatible with where the attribute actually lands.

## Eliminated hypotheses

- **H-A** — gate-ID/version mismatch.
  - Falsified by featuregate listing (gate exists, alpha, in v0.151.0).
  - Falsified by collector clean startup with `--feature-gates=processor.tailsamplingprocessor.recordpolicy`
    (unknown gates would crash startup).

- **H-B** — buffer-overflow eviction starves the composite policy.
  - Made irrelevant by direct test: with only 30 traces in flight (well under the
    10000 cap), composite still records ZERO span/resource attributes — meaning
    even the *unloaded* baseline produces the same Tempo Tier 2 failure. Buffer
    saturation is a real concern for Phase 11's workshop-load story but is
    **orthogonal** to the Tier 2 / recordpolicy defect. (Buffer saturation under
    load is its own follow-up, tracked by Panel 5 in Plan 11-05.)

- **H-C** (loose form) — Tempo write path drops the attribute.
  - Falsified: Tempo enumerates the keys under `scope=instrumentation`. Write
    path preserves them faithfully. Refined to **H-D**.

## Root Cause (CONFIRMED)

The `processor.tailsamplingprocessor.recordpolicy` alpha gate in collector-contrib
v0.151.0 stamps `tailsampling.policy` and `tailsampling.composite_policy` onto the
**InstrumentationScope** (via `ss.Scope().Attributes().PutStr(...)` in
`internal/sampling/util.go:99`), NOT onto individual spans or the resource. Tempo
2.10.5 ingests and indexes these correctly under `scope=instrumentation` but its
search APIs (`/api/search?tags=...`, TraceQL with `span.*` / `resource.*` / `.*`)
do not filter on instrumentation-scope attributes. Phase 11 design assumed
"span attributes" per the upstream README's loose phrasing; verify:tail-sampling
Tier 2 + the dashboard caption + the README §11 narrative all encode that
incorrect assumption. Tier 2 will return 0 hits regardless of buffer saturation,
load, or sub-policy fan-out, because the attribute simply does not live where
those queries look.

## Specialist hint

specialist_hint: general (OTel Collector + Tempo specifics; no language-specific
expertise needed — fix lives in YAML config + a `mise.toml` query rewrite).

## Resolution

```yaml
root_cause: |
  collector-contrib v0.151.0's `processor.tailsamplingprocessor.recordpolicy`
  alpha gate stamps `tailsampling.policy` and `tailsampling.composite_policy`
  onto the InstrumentationScope (via `ss.Scope().Attributes().PutStr(...)` in
  `processor/tailsamplingprocessor/internal/sampling/util.go:99` and
  `composite.go:122`), NOT onto individual spans or the resource. Tempo 2.10.5
  ingests scope attributes under `scope=instrumentation` but its `/api/search?tags=`
  and TraceQL `span.*`/`resource.*`/`.*` filters only see span+resource scopes,
  so the workshop's verify:tail-sampling Tier 2 query returned 0 hits even
  though the gate was working as designed. A secondary defect: the original
  Tier 2 query used `tags=tailsampling.composite_policy` (bare key, no value),
  which Tempo's logfmt parser silently treats as key=key, value="" — also
  always returning 0.

fix: |
  Route 1 (Phase 11 narrative-preserving):
    - Added a `transform/copy_recordpolicy` processor to
      `infra/observability/otelcol-config.yaml` that uses OTTL `set(attributes
      ["tailsampling.composite_policy"], instrumentation_scope.attributes
      ["tailsampling.composite_policy"])` (same for `tailsampling.policy`),
      guarded by `where ... != nil`, with `error_mode: ignore` for safety.
    - Wired it into `service.pipelines.traces.processors` between
      `tail_sampling` and `batch`. After the transform runs, the attribute
      lives on BOTH the InstrumentationScope (as the gate originally placed
      it) AND on every kept span — so Tempo's span-scope search APIs find it.
    - Switched `mise.toml` `verify:tail-sampling` Tier 2 from a single bare-key
      `/api/search?tags=tailsampling.composite_policy` query to per-sub-policy
      `tags=tailsampling.composite_policy=<name>&limit=1` queries, asserting
      each of the three sub-policy values yields at least one Tempo hit
      (literal "traceID" substring presence).

verification: |
  - mise run verify:tail-sampling now exits 0 with:
      "verify:tail-sampling tier-1: composite-policy registered and emitting (self-metrics)."
      "verify:tail-sampling tier-2: all three sub-policy values returned non-empty Tempo /api/search hits."
      "verify:tail-sampling: GREEN — composite + sub-policy contracts intact."
  - Direct Tempo /api/traces/{id} fetch on a kept trace shows
    `spans[].attributes` contains both `tailsampling.composite_policy` and
    `tailsampling.policy`, alongside the original
    `scopeSpans[].scope.attributes` placement (both paths preserved).
  - Tier 1 metric still reports
    `count_traces_sampled_total{policy="composite-policy",sampled="true"} = N`
    (61 sampled / 164 not_sampled out of 225 synthetic traces — confirming the
    composite policy is unchanged).
  - Collector startup logs are clean (no parse errors on the new transform/
    copy_recordpolicy processor; "Everything is ready. Begin running and
    processing data." appears as before).

files_changed:
  - infra/observability/otelcol-config.yaml
      "+ transform/copy_recordpolicy processor (OTTL span context, copies the
       two scope attributes onto each span, guarded with where ... != nil)"
      "+ service.pipelines.traces.processors gains transform/copy_recordpolicy
       between tail_sampling and batch"
  - mise.toml
      "verify:tail-sampling Tier 2 rewritten: per-sub-policy =value queries
       with literal traceID-substring assertion; expanded error message lists
       four likely-cause diagnostics in priority order."
  - .planning/debug/resolved/tail-sampling-recordpolicy.md
      "session record (this file) — moved from .planning/debug/ on resolution."

files_unchanged_by_design:
  - docker-compose.yml          # Route 1 keeps the recordpolicy gate as is
  - grafana/dashboards/ose-otel-demo.json   # dashboard's TraceQL filter now works without edits
  - README.md                    # §11 narrative ("attribute appears on each kept span") becomes literally true
  - .planning/phases/11-tail-sampling-at-the-collector/*  # historical records (PLAN/SUMMARY/RESEARCH/ROADMAP)
```

## Deferred follow-ups

- **Panel 5 buffer-saturation observation (Plan 11-05 / F2-2 canary).** Under
  workshop load (~200 rps steady), Grafana Panel 5 reports the
  `otelcol_processor_tail_sampling_sampling_traces_on_memory` gauge pinned at
  `num_traces=10000`. This was a concurrent confound during the investigation
  and was eliminated as a cause of THIS defect (the recordpolicy mismatch
  reproduces with only 30 traces in flight, far below the cap). It remains a
  separate Phase 11 quality concern — if buffer saturation eventually evicts
  in-flight traces before `decision_wait` expires, the workshop's "all errors
  are kept" promise can quietly violate. Track separately; not addressed by
  this fix.

- **Upstream issue / PR** (optional). The collector-contrib README under
  "Tracking sampling policy" describes the recordpolicy gate as stamping the
  attributes "on each sampled span" — which is misleading when read against
  the actual implementation that places them on the InstrumentationScope.
  A short README clarification (or, alternatively, moving the attribute to
  the span level inside `SetAttrOnScopeSpans`) would let downstream users
  skip the workaround we just landed. Out of scope for this workshop fix.
