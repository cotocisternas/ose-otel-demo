---
phase: 07-polish-differentiators
plan: 05
subsystem: documentation
tags: [readme, walkthrough, doc-01, doc-04, lean-5-section-template]
requires: [07-04]
provides: [doc-01-partial-1-2-3, doc-04-broken-fixed-pair-embed]
affects: [README.md]
tech-stack:
  added: []
  patterns: [lean-5-section-per-step-template, html-table-side-by-side-screenshots]
key-files:
  created:
    - .planning/phases/07-polish-differentiators/07-05-SUMMARY.md
  modified:
    - README.md
decisions:
  - "Replaced plan's `login admin/admin` copy with anonymous-access language (Rule 2 deviation): docker-compose now sets GF_AUTH_ANONYMOUS_ENABLED=true / GF_AUTH_ANONYMOUS_ORG_ROLE=Admin / GF_AUTH_DISABLE_LOGIN_FORM=true so workshop attendees never see a password prompt — keeping the plan's literal text would have produced a contradiction with Phase 7 reality."
  - "Used HTML <table> for the DOC-04 broken/fixed PNG pair (D-07 invariant allows table OR aligned rows; <table> renders predictably across GitHub web, local cat, and most markdown previewers)."
  - "Embedded docs/screenshots/step-03-waterfall.png as an optional inline link in Step 3's What-to-look-for (file confirmed present in this worktree's docs/screenshots/ — 07-04 produced it)."
  - "Did NOT reference docs/screenshots/step-04-metrics.png — file absent (deferred per upstream signal). Step 4 prose is plan 07-06's responsibility; this plan does not touch it."
metrics:
  duration_min: 3
  completed_date: 2026-05-02
  tasks_completed: 3
  files_modified: 1
  files_created: 1
  readme_byte_count_before: 27028
  readme_byte_count_after: 37493
  readme_byte_delta: 10465
---

# Phase 7 Plan 5: README Steps 1/2/3 + Lean 5-Section Template + DOC-04 Broken/Fixed Pair Summary

Authored README Steps 1, 2, and 3 from scratch using the lean 5-section per-step template (D-08), embedding the DOC-04 broken/fixed screenshot pair side-by-side via HTML `<table>` in Step 2. Implements DOC-01 partial (Steps 1-3 of 6) and the DOC-04 visual centrepiece. Steps 4/5/6 prose untouched per plan scope.

## What Changed

**README.md** gained three new sections inserted between the existing "Workshop checkpoints" summary block and the existing "## Step 4: Metrics" heading:

1. **Step 1: Baseline & Scaffold** — empty-Tempo screenshot embed, mise run preflight/infra:up/dev/demo:order/load callouts, anonymous-Grafana access language, cross-reference to Concepts & FAQ "What's NOT here yet" appendix.
2. **Step 2: Manual SDK Bootstrap & First Traces** — DOC-04 broken/fixed PNG pair side-by-side via HTML `<table>`, span-kind discipline rundown, graceful-shutdown observability, cross-references to "Why is OtelSdkConfiguration.java duplicated?" + "Reading the code".
3. **Step 3: AMQP Context Propagation — THE Headline Lesson** — propagator inject/extract pair description, `traceparent` header invariant (String, not `byte[]`), `recordException` + `setStatus(ERROR)` on the 10th-order failure path, optional reference to `step-03-waterfall.png`, cross-reference to "Why is the propagation pair shared?".

The pre-existing transition sentence (`This section establishes the convention; Phase 7 turns each bullet into a full walkthrough.`) was rewritten to introduce the new template and its Concepts & FAQ appendix companion.

## Final README Structure

Six `## Step ` headings now present in order (verified by `grep -c '## Step ' README.md` = 6):

```
L79  ## Step 1: Baseline & Scaffold
L115 ## Step 2: Manual SDK Bootstrap & First Traces
L160 ## Step 3: AMQP Context Propagation — THE Headline Lesson
L194 ## Step 4: Metrics                              (untouched in this plan — plan 07-06 rewrites)
L242 ## Step 5: Logs Correlation                     (untouched in this plan — plan 07-06 rewrites)
L375 ## Step 6: Verification Tests                   (untouched in this plan — plan 07-06 rewrites)
```

Standalone narrative sections (Concepts & FAQ appendix candidates) preserved verbatim at the bottom of the file: `## Reading the code`, `## Why is OtelSdkConfiguration.java duplicated?`, `## Why is the propagation pair shared?`, `## What's NOT here yet`. Plan 07-06 will reorganise these under a single `## Concepts & FAQ` header.

## Plan Outputs (per plan's `<output>` block)

- **Final byte count of README.md before vs after**: before = 27,028 bytes; after = 37,493 bytes; delta = +10,465 bytes.
- **Whether the optional `step-03-waterfall.png` was referenced**: **YES** — referenced as an inline link in Step 3's *What to look for* section. The PNG is present in this worktree's `docs/screenshots/` (07-04 produced it).
- **Deviations from D-07 invariants**: see "Deviations from Plan" section below — one Rule 2 deviation (Grafana anonymous-access language) driven by upstream signals from 07-04. No D-07 invariants were skipped or weakened.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Critical correctness] Replaced plan's `login admin/admin` copy with anonymous-access language**

- **Found during:** Task 1
- **Issue:** The plan's literal Step 1 prose said "Grafana (`mise run ui:grafana`, login `admin/admin`)" — but the upstream signal from 07-04 (committed on this worktree's base) explicitly added `GF_AUTH_ANONYMOUS_ENABLED=true`, `GF_AUTH_ANONYMOUS_ORG_ROLE=Admin`, and `GF_AUTH_DISABLE_LOGIN_FORM=true` to `docker-compose.yml`. Workshop attendees no longer see a password prompt. Following the plan's literal text would have produced README copy that contradicts the actual workshop experience — a documentation correctness bug.
- **Fix:** Replaced the parenthetical with: *"`mise run ui:grafana` — opens `http://localhost:3000`, **no login required**; anonymous Admin access is enabled by docker-compose so workshop attendees never see a password prompt"*. The rest of the bullet (the dashboard's two-row layout pedagogy) is preserved verbatim.
- **Files modified:** README.md (Step 1, *What to look for*, Grafana bullet)
- **Commit:** 0a71fc2

This deviation is explicitly mandated by the plan-spawn prompt's `<upstream_signals_from_07-04>` block, which forbids "log in with admin/admin" or "skip the password change prompt" copy.

### Auth Gates

None. No interactive authentication was required during execution.

## Per-Task Commits

| Task | Description                                                          | Commit  | Files            |
|------|----------------------------------------------------------------------|---------|------------------|
| 1    | Add Step 1 — Baseline & Scaffold (with anon-Grafana copy fix)        | 0a71fc2 | README.md        |
| 2    | Add Step 2 — Manual SDK Bootstrap & First Traces (DOC-04 pair embed) | ff82df5 | README.md        |
| 3    | Add Step 3 — AMQP Context Propagation (headline lesson)              | 532de7f | README.md        |

## Verification Results

All plan-level success criteria satisfied:

- [x] `## Step 1`, `## Step 2`, `## Step 3` headings present in README.
- [x] Each Step contains exactly 5 subsection headers in prescribed order: *What you'll learn* / *Checkpoint* / *Run* / *What to look for* / *Why it matters*.
- [x] DOC-04 broken/fixed pair (`step-02-disconnected-traces.png` + `step-03-joined-trace.png`) embedded side-by-side via HTML `<table>` in Step 2's *What to look for*.
- [x] Cross-references to Concepts & FAQ entries present in every *Why it matters* paragraph: Step 1 → "What's NOT here yet"; Step 2 → "Why is OtelSdkConfiguration.java duplicated?" + "Reading the code"; Step 3 → "Why is the propagation pair shared?".
- [x] `mise run load` callout present in Step 1's *Run* section.
- [x] No `admin/admin` login copy or "skip the password change prompt" text anywhere in the README (verified by `grep -E 'login.*admin/admin|skip the password change'` returning zero matches).
- [x] No reference to non-existent `docs/screenshots/step-04-metrics.png` (verified by `grep 'step-04-metrics.png'` returning zero matches).
- [x] Existing Prerequisites + Workshop checkpoints summary preserved verbatim where unchanged (only the closing sentence of the Workshop checkpoints block was rewritten as planned).
- [x] Existing Steps 4/5/6 prose UNCHANGED — confirmed by `git diff 570de6db -- README.md | grep '^[-+]## Step [456]'` returning zero matches.
- [x] Concepts & FAQ section markers ("Reading the code" / "Why is OtelSdkConfiguration.java duplicated?" / "Why is the propagation pair shared?" / "What's NOT here yet") still present at end of README.

## Known Stubs

None. All embedded image paths resolve to files present in `docs/screenshots/`. All Concepts & FAQ cross-references point to anchors that exist verbatim in the README's tail.

## Threat Flags

None. The threat-model dispositions for `T-07-05-01` (information disclosure via image embeds — local relative paths only) and `T-07-05-02` (HTML `<table>` allowlist — only `<table>`/`<tr>`/`<th>`/`<td>`/`<img>` used, no `<script>` or `<iframe>`) and `T-07-05-03` (cross-reference anchor stability — appendix entries verified present) are all satisfied as planned.

## Self-Check: PASSED

- README.md exists and contains all six `## Step ` headings (1-6) — verified.
- Commits 0a71fc2, ff82df5, 532de7f all exist on `worktree-agent-a94ab8bb152525c41` — verified via `git log --oneline`.
- DOC-04 broken/fixed pair PNGs (`step-02-disconnected-traces.png` and `step-03-joined-trace.png`) referenced from README and present in `docs/screenshots/` — verified.
- Anti-pattern `admin/admin` login copy absent from README — verified.
- Non-existent `step-04-metrics.png` not referenced — verified.
- Steps 4/5/6 prose unchanged in this plan's diff — verified.
