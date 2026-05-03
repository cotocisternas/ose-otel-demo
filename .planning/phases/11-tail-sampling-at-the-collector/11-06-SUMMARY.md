---
phase: 11-tail-sampling-at-the-collector
plan: 06
subsystem: docs
tags: [readme, screenshot, dashboard, tail-sampling, histogram, grafana, otelcol]

# Dependency graph
requires:
  - phase: 11-tail-sampling-at-the-collector
    provides: "Plans 11-01..11-05: otelcol-config.yaml composite tail_sampling block, WIDGET-SLOW Java branch, SLOW_RPS load stream, verify:tail-sampling task, diagnostics dashboard row, OFF screenshot"
  - phase: 10-prerequisites-stack-decomposition
    provides: "Phase 10 decomposed stack + Step 10 README section as structural template"
provides:
  - "README.md ## Step 11 section (D-T11 + D-T12): five sub-sections, OR-semantics narrative, WARNING-2 four-tier priority chain in three artifacts, F2-3 double-filter-trap blockquote, paired OFF/ON screenshot table, mise run invocations"
  - "Histogram-suffix verification: live :8888/metrics confirms _milliseconds suffix present — Panel 4 PromQL correct as-written (Case 1, no dashboard edit)"
  - "PENDING: step-11-tail-sampling-ON.png operator capture (Task 3 checkpoint:human-action)"
affects:
  - Phase 16 README (must back-link to §11 F2-3 blockquote anchor per D-T12)
  - Workshop attendees following Step 11 walkthrough end-to-end

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Histogram-suffix live-verification pattern: curl :8888/metrics → grep → compare against dashboard PromQL; graceful SKIP when stack not live (WARNING-4 fix)"
    - "Three-artifact consistency pattern: YAML verbatim / README paraphrase / callout blockquote all reference the same four-tier reality; divergence is a documentation bug"
    - "F2-3 CRITICAL blockquote pattern at section end: Phase N head-vs-tail double-filter trap documented in Phase 11 §11 for Phase 16 back-link"

key-files:
  created:
    - docs/screenshots/step-11-tail-sampling-ON.png  # PENDING operator capture
  modified:
    - README.md
    - grafana/dashboards/ose-otel-demo.json  # NOT modified — suffix verification was Case 1 (PASS, no edit)

key-decisions:
  - "WARNING-2 fix: README §11 'What you'll learn' bullet acknowledges four-tier chain (drop > inverted_not_sample > sample > inverted_sample) and names deprecated inverted_* tiers — three artifacts now agree"
  - "WARNING-4 fix: histogram-suffix gate degrades gracefully when invoked outside operator-driven E2E flow — SKIP (exit 0) vs VERIFIED (exit 0), never false-FAIL"
  - "Case 1 (no dashboard correction): live :8888/metrics confirmed _milliseconds suffix IS present in otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket — Panel 4 PromQL correct as-written"
  - "Task 3 is checkpoint:human-action: operator must capture step-11-tail-sampling-ON.png from live Tempo Search (Service Name=order-producer, Last 5 min) after mise run load SLOW_RPS=2 has run for 5+ minutes"

patterns-established:
  - "Step-N README section shape: five sub-sections (What you'll learn / Checkpoint / Run / What to look for / Why it matters) + F2-N callout blockquote at end"
  - "Paired OFF/ON screenshot HTML <table> idiom for before/after trace-count delta demo (established in Phase 7, continued here)"

requirements-completed: [TSAMP-02, TSAMP-03]

# Metrics
duration: resuming prior session (tasks 1+2 done in prior session; task 3 pending operator)
completed: 2026-05-03
---

# Phase 11 Plan 06: README §11 + Histogram Verification Summary

**README Step 11 section added with OR-semantics four-tier priority chain (WARNING-2 fix), F2-3 double-filter callout, composite alpha-gate caveat, and paired screenshot table — histogram suffix verified Case 1 PASS (no dashboard edit)**

## Performance

- **Duration:** Resuming prior session — Tasks 1+2 committed; Task 3 pending operator
- **Started:** 2026-05-03 (prior session)
- **Completed:** 2026-05-03 (partial — Task 3 checkpoint:human-action not yet resolved)
- **Tasks:** 2 of 3 automated tasks complete; 1 checkpoint:human-action pending
- **Files modified:** 1 (README.md); grafana/dashboards/ose-otel-demo.json unchanged (Case 1)

## Accomplishments

- **Task 1 — Histogram-suffix verification (VERIFIED — Case 1):** Live curl of `:8888/metrics` confirmed `otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket` is present with the `_milliseconds` unit infix. Dashboard Panel 4 PromQL is correct as-written; no `sed` correction applied. The graceful-skip gate (WARNING-4 fix) also verified: when the stack is not running, the gate exits 0 with `SKIP:` log line instead of false-FAIL.
- **Task 2 — README §11 section:** Full five-sub-section Step 11 narrative (100+ lines) inserted between Step 10 and Concepts & FAQ. Includes: OR-semantics priority chain with four-tier reality explicitly named in two places (WARNING-2 fix), WIDGET-SLOW interaction lesson, composite envelope + `recordpolicy` alpha-gate caveat, paired OFF/ON screenshot HTML `<table>`, `mise run load SLOW_RPS=2` and `mise run verify:tail-sampling` invocations, F2-3 double-filter-trap CRITICAL blockquote with `OTEL_TRACES_SAMPLER=parentbased_always_on` escape hatch and Phase 16 forward-link.
- **Task 3 — ON screenshot (PENDING):** Checkpoint:human-action paused for operator to capture `docs/screenshots/step-11-tail-sampling-ON.png` from live Tempo Search post-Phase-11 with tail sampling active.

## Task Commits

Tasks were committed atomically (prior session):

1. **Task 1: Histogram-suffix verification micro-task** - `4ab84ef` (chore)
2. **Task 2: Add ## Step 11 section to README.md** - `c949225` (docs)
3. **Task 3: Operator captures ON screenshot** - PENDING checkpoint:human-action

**Plan metadata:** (docs commit below — this SUMMARY.md)

## Files Created/Modified

- `README.md` — Added 100+ line `## Step 11: Tail Sampling at the Collector — what survives the Tempo write path` section with all five sub-sections, OFF/ON paired screenshot `<table>`, F2-3 blockquote. No other content modified.
- `grafana/dashboards/ose-otel-demo.json` — NOT modified (histogram suffix Case 1: `_milliseconds` suffix present in live exposition; no correction needed).
- `docs/screenshots/step-11-tail-sampling-ON.png` — PENDING operator capture.

## Decisions Made

- **Case 1 confirmed:** Live `:8888/metrics` at otelcol-contrib v0.151.0 exposes `_milliseconds` suffix on the decision-timer histogram. Panel 4 PromQL in `ose-otel-demo.json` (plan 11-05) is correct. No sed correction applied. Dashboard JSON unchanged.
- **WARNING-2 fix applied:** The "What you'll learn" third bullet now names the full four-tier chain `drop > inverted_not_sample > sample > inverted_sample`, explicitly notes the `inverted_*` tiers are deprecated and unused in the workshop config, and the "What to look for" section adds a canonical-reference note contrasting the three artifacts (YAML verbatim / README paraphrase / F2-3 callout) that must agree. Three appearances of the four-tier chain in README.
- **WARNING-4 fix applied:** The Task 1 verify block front-loads a Docker ps + HTTP probe; if the stack is not live it logs `SKIP:` and exits 0, so CI lint passes don't see a false-FAIL.
- **`inverted_* tiers are deprecated` phrasing preserved exactly** for acceptance criteria grep gate.

## Deviations from Plan

None - plan executed exactly as written. WARNING-2 and WARNING-4 fixes were specified in the plan itself (not unplanned deviations).

## Histogram Suffix Verification Detail

**Outcome: Case 1 — PASS, no correction**

```
VERIFIED: histogram suffix verification PASS — _milliseconds suffix present in both
live :8888 exposition and dashboard PromQL. Case 1: no dashboard correction needed.
```

- `/tmp/phase11-metrics-check.txt` confirmed `otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket` lines in live exposition
- Dashboard Panel 4 PromQL uses `sampling_decision_timer_latency_milliseconds_bucket` — matches live exposition
- `sampling_late_span_age_bucket` (Panel 3, no `_seconds` infix) also verified present and correct
- JSON parses cleanly: `python3 -c "import json; json.load(open('grafana/dashboards/ose-otel-demo.json'))"` exits 0

## Known Stubs

- `docs/screenshots/step-11-tail-sampling-ON.png` — **Deferred by operator decision.** The README `<table>` references this file; until it is captured and committed, the ON cell of the paired-screenshot table shows a broken image. Follow-up: run `mise run load SLOW_RPS=2` for 5+ min, open Tempo Search (Service Name=order-producer, Last 5 min), capture screenshot, `git add docs/screenshots/step-11-tail-sampling-ON.png && git commit -m "docs(11): add tail-sampling ON screenshot"`.
- `docs/screenshots/step-11-tail-sampling-OFF.png` — **Also deferred.** Pre-tail-sampling baseline screenshot. To capture: `git checkout step-10-collector-decompose -- infra/observability/otelcol-config.yaml && docker compose restart otel-collector && mise run load && capture → git checkout HEAD -- infra/observability/otelcol-config.yaml`.

## Threat Flags

None. No new network endpoints, auth paths, or file access patterns introduced. Threats T-11-06-01 through T-11-06-05 from plan threat_model analyzed; all accepted or mitigated:
- T-11-06-02 (workshop-vs-production sizing confusion): mitigated by "Why it matters" paragraph naming the sizing tradeoffs explicitly.
- T-11-06-03 (F2-3 callout skipped): mitigated by `> **CRITICAL:**` blockquote at end of §11 + Phase 16 back-link contract.
- T-11-06-04 (typo'd histogram correction breaks Panel 4): Case 1 — no sed correction applied; not triggered.
- T-11-06-05 (skim-reader builds 3-tier mental model): mitigated by WARNING-2 fix — three artifacts now agree on four-tier reality.

## Issues Encountered

None during Tasks 1 and 2. Task 3 is an expected operator-gate checkpoint (checkpoint:human-action).

## Next Phase Readiness

- Tasks 1 and 2 complete; all automated work for plan 11-06 done.
- **Task 3 screenshot deferred by operator decision:** Both `step-11-tail-sampling-ON.png` and `step-11-tail-sampling-OFF.png` will be added in a follow-up commit. The README §11 paired-screenshot table will show broken images until then. This is accepted known debt.
- Phase 11 automated work is complete; proceeding to phase-level verification and completion.
- Phase 12 (exemplars + metrics trace click-through) can begin once Phase 11 ships.

## Self-Check

- [x] Task 1 commit `4ab84ef` exists: `git log --oneline | grep 4ab84ef` → present
- [x] Task 2 commit `c949225` exists: `git log --oneline | grep c949225` → present
- [x] README §11 verification: `bash -c '... && echo "task2 ok"'` → PASS
- [x] Histogram log: `grep -E "SKIP|VERIFIED" /tmp/11-06-task1.log` → VERIFIED
- [~] Task 3: screenshots deferred by operator — known debt, follow-up commit required

---
*Phase: 11-tail-sampling-at-the-collector*
*Completed: 2026-05-03 (partial — Task 3 pending)*
