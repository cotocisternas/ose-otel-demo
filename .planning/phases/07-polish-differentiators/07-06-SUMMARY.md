---
phase: 07-polish-differentiators
plan: 06
subsystem: docs
tags: [readme, walkthrough, doc-01, refactor, appendix, concepts-faq]
requires:
  - 07-04-screenshot-set
  - 07-05-readme-steps-1-2-3
provides:
  - README Steps 4/5/6 in lean 5-section template
  - README ## Concepts & FAQ appendix consolidating four narrative sections
  - README D-09 closing paragraph (no Phase 7 git tag)
affects:
  - README.md
tech-stack:
  added: []
  patterns:
    - Lean 5-section per-step template (D-08) applied uniformly across all six Steps
    - Concepts & FAQ appendix as second reading mode for narrative deep-dives
    - HTML TODO comment as placeholder for deferred screenshot (step-04-metrics.png)
key-files:
  created: []
  modified:
    - README.md
decisions:
  - Step 4 docs/screenshots/step-04-metrics.png embed replaced with HTML TODO comment per upstream signal from 07-04 (PNG capture deferred); dashboard URL used as visual stand-in in the prose
  - Concepts & FAQ appendix anchor link spelled `#concepts--faq` (GitHub auto-slug for "## Concepts & FAQ")
  - Body content of all four pre-existing narrative sections preserved BYTE-IDENTICAL except "What's NOT here yet" which was refreshed to v1-ship state per CONTEXT.md `<deferred>`
  - D-09 final paragraph appended verbatim with `---` horizontal-rule separator; NO Phase 7 git tag mentioned
metrics:
  duration_min: 5
  completed_date: 2026-05-02
  readme_lines: 393
  readme_bytes: 39080
---

# Phase 07 Plan 06: README Steps 4/5/6 + Concepts & FAQ Appendix Summary

Rewrote README Steps 4/5/6 to the lean 5-section template (D-08), consolidated four standalone narrative sections at the bottom of the README into a single `## Concepts & FAQ` appendix with `### `-level subsections, refreshed the obsolete "What's NOT here yet" bullets to a v2-deferred-ideas list, and appended the D-09 closing paragraph (no Phase 7 git tag, `git checkout step-NN-*` revisit pointer). All D-07 CRITICAL pedagogical invariants preserved verbatim.

## Outcomes

- Steps 4/5/6 now share the same 5-section structure as Steps 1/2/3 (rewritten in 07-05). Across all six steps the README has exactly 6 of each subsection header (`### What you'll learn`, `### Checkpoint`, `### Run`, `### What to look for`, `### Why it matters`).
- Phase 4 pedagogical invariants preserved in the rewritten Step 4: `PeriodicMetricReader` 10-second interval (METRIC-01), seconds-not-millis histogram unit, `order.priority` non-semconv business attribute, OTel→Prometheus name mangling (`orders.created` → `orders_created_total`; `http.server.request.duration` → `http_server_request_duration_seconds`), shared `Resource` attributes (D-05).
- Phase 5 pedagogical invariants preserved in the rewritten Step 5: PITFALL #5 / commit `f5c331a` `OpenTelemetryAppender.install(sdk)` inline-in-the-`@Bean`-factory ordering fix; `appender.v1_0` vs `mdc.v1_0` package collision callout; `severity_text="ERROR"` Loki-OTLP-receiver-idiomatic query callout. **The "Production-readiness callout: do not log untrusted payload fields" subsection is preserved BYTE-IDENTICAL** — `OrderController.create(...)` and `ProcessingService.process(...)` log-site references, CRLF log-injection threat description, T-05-04-01 threat-model cross-reference. Threat T-05-04-01 was unchanged in scope; the security-relevant prose was carried verbatim from the Phase 5 SUMMARY.
- Phase 6 pedagogical invariants preserved in the rewritten Step 6: `<classifier>exec</classifier>` Maven trickery callout (D-04 dual-jar publish), `TestOtelHolder` static-singleton (D-07.1 @TestConfiguration vs @Bean bootstrap-ordering resolution), `SimpleSpanProcessor` + `InMemorySpanExporter` test-determinism lesson (NO `Thread.sleep`, Awaitility for async settling), two `SpringApplicationBuilder` contexts in one JVM rationale, commit `f5c331a` cross-reference (test-side replication of Phase 5 ordering fix), random RabbitMQ port visibility (TEST-01 SC #2), the four `@Test` methods (traces / logs / metrics / failure-path triple-signal correlation).
- Concepts & FAQ appendix consolidates four standalone narrative sections: *Reading the code*, *Why is OtelSdkConfiguration.java duplicated?*, *Why is the propagation pair shared?*, *What's NOT here yet*. Sections demoted from `## ` to `### `; GitHub auto-generated anchors still resolve. Each per-step *Why it matters* paragraph cross-references the relevant appendix entry via `[Concepts & FAQ](#concepts--faq)`.
- "What's NOT here yet" refreshed: removed the obsolete `No pre-built Grafana dashboard or load script (Phase 7)` bullet (Phase 7 has now landed those). Replaced with a v2-deferred-ideas list (SAMP-01, PROP-V2-01, FAIL-01, FAC-01, CI YAML, Pyroscope, vendor-specific exporter swap demo) sourced from CONTEXT.md `<deferred>` and REQUIREMENTS.md v2 entries.
- D-09 final paragraph appended verbatim: *"Workshop is at main HEAD past `step-06-tests`; dashboard, load script, and full walkthrough are here. To revisit any step, `git checkout step-NN-*`."* Preceded by an `---` horizontal-rule separator. **No Phase 7 git tag** — `step-NN-*` checkpoints alone, per D-09.

## Grep Audit (Task 5) — All Green

All D-07 CRITICAL invariant tokens grep-verified in the final README.md:

| Phase | Token | Status |
|---|---|---|
| 4 | `PeriodicMetricReader` | OK |
| 4 | `10-second` | OK |
| 4 | `seconds-not-millis` | OK |
| 4 | `order.priority` | OK |
| 4 | `name mangling` | OK |
| 4 | `orders_created_total` | OK |
| 4 | `http_server_request_duration_seconds` | OK |
| 5 | `f5c331a` | OK |
| 5 | `install(sdk)` | OK |
| 5 | `PITFALL #5` | OK |
| 5 | `appender.v1_0` | OK |
| 5 | `mdc.v1_0` | OK |
| 5 | `do not log untrusted` | OK |
| 5 | `Production-readiness callout` | OK |
| 5 | `severity_text` | OK |
| 6 | `classifier` | OK |
| 6 | `TestOtelHolder` | OK |
| 6 | `SimpleSpanProcessor` | OK |
| 6 | `SpringApplicationBuilder` | OK |
| 6 | `random.* port` | OK |
| Cross-cutting | `duplicat` | OK |
| Cross-cutting | `shared` | OK |
| Cross-cutting | `Concepts & FAQ` | OK |
| Structural | `^## Step ` count == 6 | OK |
| Structural | `^### What you` count == 6 | OK |
| Structural | `^### Checkpoint` count == 6 | OK |
| Structural | `^### Run` count == 6 | OK |
| Structural | `^### What to look for` count == 6 | OK |
| Structural | `^### Why it matters` count == 6 | OK |
| DOC-04 | `step-02-disconnected-traces.png` embed | OK |
| DOC-04 | `step-03-joined-trace.png` embed | OK |
| DOC-04 | `<table>` (side-by-side render) | OK |
| DOC-04 | `step-01-empty-tempo.png` embed | OK |
| DOC-04 | `step-04-metrics.png` reference | OK (in TODO HTML comment — see Deviations) |
| DOC-04 | `step-05-logs-trace-jump.png` embed | OK |
| DOC-04 | `step-06-test-output.png` embed | OK |
| D-09 | `Workshop is at main HEAD past` | OK |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Step 4 `step-04-metrics.png` embed replaced with TODO HTML comment**

- **Found during:** Task 1 startup (file existence check)
- **Issue:** The plan's Task 1 `<action>` block specifies a markdown image embed `![Step 4 — Mimir RED metrics](docs/screenshots/step-04-metrics.png)`, but `docs/screenshots/step-04-metrics.png` **does not exist on disk** — Phase 7 wave-4 (07-04) deferred its capture per the upstream signal in this executor's prompt: *"step-04-metrics.png ✗ DEFERRED — DO NOT reference. Step 4's *What to look for* MUST use Grafana dashboard URL or text description. If you wish to embed a placeholder, use a TODO comment instead."*
- **Fix:** Wrote the embed as an HTML comment (invisible in the rendered README, satisfies the literal grep gate that checks for the path string, and leaves a clear `TODO(DOC-04 v1.x)` marker for the future plan that lands the PNG). Inline prose now points readers at the auto-provisioned dashboard URL `http://localhost:3000/d/ose-otel-demo` as the visual stand-in.
- **Files modified:** `README.md` (Step 4 *What to look for*)
- **Commit:** `745f1a2`
- **Why this is correct:** the upstream-signal block in the executor prompt is dispositive over the plan's literal verbatim block — the plan was authored before 07-04's screenshot deferral. Both the upstream signal and CLAUDE.md's "fail-fast on missing dependencies" guidance point to the same fix. The TODO comment shape is explicitly endorsed by the upstream signal.

### Auth Gates

None.

### Deferred Issues

None — no fix-attempt-cap reached.

## Threat Flags

No new security-relevant surface introduced. The Phase 5 *Production-readiness callout* subsection (security-relevant per D-07; threat T-05-04-01) was preserved BYTE-IDENTICAL during the Step 5 rewrite — both the `OrderController.create(...)` and `ProcessingService.process(...)` log-site references and the CRLF-log-injection threat description carry forward verbatim. No threat-model edits required.

## Commit History

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Step 4 Metrics in 5-section template | `745f1a2` |
| 2 | Step 5 Logs Correlation in 5-section template | `a4b560b` |
| 3 | Step 6 Verification Tests in 5-section template | `5e598a5` |
| 4 | Concepts & FAQ appendix + D-09 close | `ce984be` |
| 5 | Whole-document grep audit (verification-only, no diff) | n/a |

## Self-Check: PASSED

Verified after writing this SUMMARY:
- README.md contains `## Concepts & FAQ` heading: FOUND
- README.md contains `Workshop is at main HEAD past` (D-09 close): FOUND
- README.md contains exactly 6 `## Step ` headings: FOUND (count == 6)
- All four task commits present in `git log`:
  - `745f1a2` (Task 1): FOUND
  - `a4b560b` (Task 2): FOUND
  - `5e598a5` (Task 3): FOUND
  - `ce984be` (Task 4): FOUND
- Task 5 produced no diff (verification-only); audit results recorded above.
- Per parallel-executor instructions: `.planning/STATE.md`, `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md` NOT modified.
