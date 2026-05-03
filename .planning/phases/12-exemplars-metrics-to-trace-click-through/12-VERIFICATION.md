---
phase: 12-exemplars-metrics-to-trace-click-through
verified: 2026-05-03T23:00:00Z
status: human_needed
score: 4/4 roadmap success criteria verified (2 WARNING items from code review require human decision)
overrides_applied: 0
human_verification:
  - test: "Confirm span.end()-before-scope.close() ordering is acceptable for Phase 12"
    expected: "Developer decides whether to accept the OTel spec violation or fix it before tagging step-12-exemplars"
    why_human: "CR-01 from code review: span.end() fires at line 217 before scope.close() at line 219 in HttpServerSpanFilter. The OTel spec requires scope.close() before span.end(). The plan itself prescribed this wrong order (its acceptance criteria said 'scope.close() appears AFTER span.end()'). The exemplar pipeline goal IS functionally achieved — EXMP-04 was human-verified working — but the ordering is a latent concurrency defect on shared Tomcat threads. Fix: swap lines 217 and 219 so scope.close() precedes span.end()."
  - test: "Confirm verify:exemplars GNU date syntax is acceptable for the workshop audience"
    expected: "Developer decides whether to fix date -d '10 minutes ago' to a cross-platform form, or accept Linux-only behavior"
    why_human: "CR-02 from code review: mise.toml line 575 uses GNU date -d which is Linux-only. On macOS BSD date, this produces garbage timestamps and causes verify:exemplars to always return 'no exemplars' even when the pipeline is active. The workshop runs on Linux in CI but CLAUDE.md lists macOS as a target platform (mise.toml already uses lsof as a macOS fallback in the preflight task). Cross-platform fix: replace with 'python3 -c ...' or Mimir's native 'start=now-10m&end=now' relative syntax."
---

# Phase 12: Exemplars — Metrics-to-Trace Click-Through Verification Report

**Phase Goal:** Three-layer exemplar plumbing (SDK ExemplarFilter, Mimir storage, Grafana dashboard) enabling one-click navigation from histogram metric data points to originating traces in Tempo.
**Verified:** 2026-05-03T23:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

All four Roadmap Success Criteria are verified in the codebase. Two WARNING items from the code review require developer decision before the `step-12-exemplars` tag is applied.

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | Both `OtelSdkConfiguration.buildMeterProvider()` calls include `.setExemplarFilter(ExemplarFilter.traceBased())` — exactly one line per service | VERIFIED | producer line 497, consumer line 505; import `io.opentelemetry.sdk.metrics.ExemplarFilter` at producer line 25, consumer line 27; placed correctly between `.setResource(resource)` and `.registerMetricReader(metricReader)` |
| SC2 | `mimir-config.yaml` has `limits.max_global_exemplars_per_user: 100000`; `grafana/datasources.yaml` has `exemplarTraceIdDestinations` (pre-wired Phase 10); `otelcol-config.yaml` has WHY comment about unconditional PRW exemplar forwarding with no `send_exemplars` key | VERIFIED | mimir-config.yaml lines 71-76 confirmed; grafana/datasources.yaml lines 53-56 confirmed; otelcol-config.yaml lines 297-298 confirmed; no `send_exemplars` key present |
| SC3 | After generating load, attendee sees exemplar dots on `http.server.request.duration` histogram panel and clicking one opens the originating trace in Tempo | VERIFIED (human-approved) | Dashboard panel id=16 exists with `"exemplar": true` on all three targets (p50/p95/p99); `exemplarLabel: trace_id`; EXMP-04 human-verify checkpoint approved in 12-04-SUMMARY.md: "exemplar dot click-through to Tempo confirmed" |
| SC4 | Exemplar labels contain only `trace_id` and `span_id` — no business attributes | VERIFIED | `ExemplarFilter.traceBased()` attaches only the active `SpanContext` (trace_id + span_id); README §12 cardinality safety section documents "~60 bytes per exemplar"; no business attributes are injectable via `ExemplarFilter.traceBased()` by OTel API design |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | ExemplarFilter.traceBased() in buildMeterProvider() | VERIFIED | Import at line 25; call at line 497 in correct builder position |
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | ExemplarFilter.traceBased() in buildMeterProvider() | VERIFIED | Import at line 27; call at line 505 in correct builder position |
| `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` | Manual scope management with scope.close() after record() | VERIFIED (with WARNING — see CR-01 below) | Manual scope at line 170; record() at line 213-215; span.end() at line 217; scope.close() at line 219. Exemplar correctness achieved. OTel spec ordering violated (scope.close() must precede span.end()). |
| `infra/observability/mimir-config.yaml` | `limits.max_global_exemplars_per_user: 100000` | VERIFIED | Lines 71-76; top-level `limits:` block; inline WHY comment present |
| `infra/observability/otelcol-config.yaml` | WHY comment on unconditional PRW exemplar forwarding; no `send_exemplars` key | VERIFIED | Lines 297-298 contain comment; grep confirms zero `send_exemplars` occurrences |
| `grafana/dashboards/ose-otel-demo.json` | Exemplars (Phase 12) open row (id=15) and histogram panel (id=16) with exemplar:true | VERIFIED | Panel id=15: `"Exemplars (Phase 12)"`, `"collapsed": false`; Panel id=16: 3 targets all with `"exemplar": true`, `"exemplarLabel": "trace_id"` |
| `mise.toml` | `[tasks."verify:exemplars"]` querying Mimir exemplar API | VERIFIED (with WARNING — see CR-02 below) | Task exists at line 568; queries `query_exemplars` endpoint; jq assertion on `trace_id`; exits 1 on failure. GNU `date -d` on line 575 breaks on macOS. |
| `README.md` | §12 section with all seven D-E8 subsections | VERIFIED | `grep -c 'Step 12: Exemplars'` → 1 (line 632); all seven subsections confirmed: What you'll learn, Checkpoint, Run, What to look for, Why it matters, cardinality safety, screenshot placeholder |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HttpServerSpanFilter.doFilterInternal()` | `SdkMeterProvider` (via requestDuration histogram) | `Span.current()` valid at `record()` time — scope not closed before record | VERIFIED | `record()` at line 213, `span.end()` at line 217, `scope.close()` at line 219. Active span IS present at record() time. |
| `OtelSdkConfiguration.buildMeterProvider()` | `SdkMeterProvider` | `.setExemplarFilter(ExemplarFilter.traceBased())` | VERIFIED | Both services: call between `.setResource()` and `.registerMetricReader()` |
| `mimir-config.yaml limits.max_global_exemplars_per_user` | Mimir ingester | Mimir startup config parse — positive value enables exemplar storage | VERIFIED | `max_global_exemplars_per_user: 100000` confirmed; verified working end-to-end (verify:exemplars GREEN per 12-04-SUMMARY) |
| `ose-otel-demo.json panel id=16` | Mimir prometheus datasource | `"exemplar": true` flag on each query target | VERIFIED | All three targets (refId A/B/C) have `"exemplar": true` |
| `mise run verify:exemplars` | Mimir `/prometheus/api/v1/query_exemplars` | curl + jq assertion on `.data[0].exemplars[0].labels.trace_id` | VERIFIED (with WARNING) | Task wired correctly; GNU date -d syntax breaks on macOS |
| `README.md §12` | `infra/observability/mimir-config.yaml limits.max_global_exemplars_per_user` | Annotated config excerpt explaining real Mimir enablement gate | VERIFIED | `grep -c 'max_global_exemplars_per_user' README.md` → 3 |
| `README.md §12` | `grafana/datasources.yaml exemplarTraceIdDestinations` | Three-layer narrative references pre-wired datasource | VERIFIED | `grep -c 'exemplarTraceIdDestinations' README.md` → 3 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ose-otel-demo.json panel id=16` | `http_server_request_duration_seconds_bucket` exemplar data | Mimir `/prometheus/api/v1/query_exemplars` via Grafana Prometheus datasource | Yes — verified GREEN by `mise run verify:exemplars` execution (12-04-SUMMARY documents GREEN confirmation) | FLOWING |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| EXMP-01 | 12-01-PLAN.md | `ExemplarFilter.traceBased()` in both `buildMeterProvider()` methods | SATISFIED | Producer line 497; consumer line 505; imports confirmed |
| EXMP-02 | 12-02-PLAN.md | Mimir `limits.max_global_exemplars_per_user: 100000` | SATISFIED | mimir-config.yaml line 72 |
| EXMP-03 | 12-03-PLAN.md | `grafana/datasources.yaml` includes `exemplarTraceIdDestinations` (pre-wired Phase 10); dashboard panel with `exemplar: true` | SATISFIED | datasources.yaml line 53 (pre-wired); dashboard panel id=16 with exemplar:true on all targets |
| EXMP-04 | 12-04-PLAN.md | Attendee clicks histogram exemplar dot and lands on trace in Tempo | SATISFIED (human-approved) | Human-verify checkpoint in 12-04-SUMMARY: "EXMP-04 APPROVED. User confirmed..." |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `producer-service/.../HttpServerSpanFilter.java` | 217, 219 | `span.end()` fires before `scope.close()` — OTel spec violation | WARNING (code review CR-01) | Leaves dead span context on Tomcat thread; potential incorrect parent-span linkage on high-concurrency paths. Exemplar pipeline goal works in practice but the ordering violates the OTel specification. Fix: swap lines 217 and 219 so `scope.close()` precedes `span.end()`. |
| `mise.toml` | 575 | `date -u -d '10 minutes ago'` — GNU coreutils only; fails silently on macOS BSD `date` | WARNING (code review CR-02) | `verify:exemplars` returns false-negative "no exemplars" on macOS even when pipeline is active. Fix: use `python3 -c "import datetime; print(...)"` or Mimir's relative time syntax `start=now-10m&end=now`. |

### Human Verification Required

#### 1. CR-01: Decide on span.end() / scope.close() ordering in HttpServerSpanFilter

**Test:** Read `HttpServerSpanFilter.java` lines 217-219. Currently: `span.end()` at line 217, `scope.close()` at line 219.

**Expected:** Developer decides: (a) fix it — swap the two lines so `scope.close()` precedes `span.end()`, matching the OTel specification and Phase 16's stated F7-4 pitfall mitigation ("all `span.makeCurrent()` calls in try-with-resources"); or (b) accept it — the exemplar goal works because `record()` fires at line 213 before either `span.end()` or `scope.close()`, so the exemplar IS attached correctly. The concurrency hazard is theoretical for a workshop with a single Tomcat thread per test.

**Why human:** The PLAN itself specified this wrong ordering in its acceptance criteria ("scope.close() appears AFTER span.end()"), so the implementation is correct per its plan. A mechanical fix requires a judgment call about whether to change what the plan prescribed. Note: Phase 16 F7-4 says "all span.makeCurrent() calls in try-with-resources" — this conflicts with Phase 12's manual scope pattern. A decision here affects future phase guidance.

#### 2. CR-02: Decide on verify:exemplars cross-platform date syntax

**Test:** Run `mise run verify:exemplars` on a macOS machine. Expected: the GNU `date -d '10 minutes ago'` at line 575 produces a garbage `START` value on macOS BSD `date`, causing Mimir to return empty data and the task to print the false-negative "no exemplars" error.

**Expected:** Developer decides: (a) fix it — replace line 575 with a cross-platform alternative (`python3 -c "import datetime; print((datetime.datetime.utcnow() - datetime.timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%SZ'))"` or simply change the curl call to use `start=now-10m&end=now` as Mimir supports relative time natively); or (b) accept it — the workshop is Linux-only for now.

**Why human:** Whether macOS support is required depends on the workshop audience. CLAUDE.md has no explicit Linux-only constraint and `mise.toml` has macOS-aware workarounds (the `lsof` fallback in `preflight`), suggesting macOS is a target platform.

---

### Gaps Summary

No BLOCKER gaps. All four Roadmap Success Criteria are verified in the codebase. The phase goal — three-layer exemplar plumbing with one-click metric-to-trace navigation — is achieved and human-verified working.

Two WARNING items from code review (CR-01: OTel spec ordering violation, CR-02: GNU-only date syntax) require human decision before applying the `step-12-exemplars` git tag. Neither blocks the observable goal but both affect code correctness and workshop portability respectively.

---

_Verified: 2026-05-03T23:00:00Z_
_Verifier: Claude (gsd-verifier)_
