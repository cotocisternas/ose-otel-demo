---
phase: 13-log-based-metrics-loki-recording-rules
plan: "02"
subsystem: documentation
tags: [readme, log-based-metrics, loki, recording-rules, walkthrough]
dependency_graph:
  requires: [phase-13-plan-01-loki-recording-rule, phase-13-plan-01-dashboard-panel]
  provides: [LMET-01-readme-walkthrough, LMET-02-readme-annotation, LMET-03-readme-comparison]
  affects: [README.md]
tech_stack:
  added: []
  patterns:
    - Zero-code narrative hook for ops-team-owned metrics lesson
    - SDK vs log-derived comparison table (4-row reference)
    - Phase 18 screenshot placeholder pattern (HTML img tag)
key_files:
  created: []
  modified:
    - README.md
decisions:
  - "Step 13 leads with zero-code angle per D-L7: 'only step with no Java changes'"
  - "Comparison table uses 4 rows (Latency, Accuracy, Code change, Cardinality control) per D-L8"
  - "Screenshot placeholder uses Phase 12 HTML img pattern with Phase 18 comment per D-L9"
  - "Section placed between Step 12 screenshot placeholder and Concepts & FAQ heading (line 716)"
metrics:
  duration: "2 minutes"
  completed_date: "2026-05-04"
  tasks_completed: 1
  files_changed: 1
---

# Phase 13 Plan 02: README Step 13 Walkthrough Section Summary

**One-liner:** README Step 13 section delivering zero-code narrative hook, annotated recording rule YAML, SDK vs log-derived comparison table, and Phase 18 screenshot placeholder (99 lines, within 100-150 target).

## What Was Built

### Task 1: README Step 13 section (COMPLETE)

**File: `README.md`** — Inserted 99-line Step 13 section before `## Concepts & FAQ` at line 716.

The section follows the Step 12 structural template exactly:

**Subsections delivered:**
1. **What you'll learn** — Five bullet points covering: zero-code angle (D-L7 hook), Loki recording rule pipeline, log: naming prefix (F4-1), [2m] window aliasing mitigation (F4-2), fake/ tenant directory (F4-3)
2. **Checkpoint** — Git tag `step-13-log-based-metrics` context; `git diff step-12-exemplars..step-13-log-based-metrics` callout
3. **Run** — Full command sequence: preflight, infra:up, docker compose restart loki, dev, load, sleep 90, verify:log-metrics, ui:grafana
4. **What to look for** — Three verification points: rules API curl, Mimir query curl, dashboard panel description; annotated YAML excerpt; comparison table
5. **Why it matters** — Recording rules as ops-team/dev-team boundary; log: prefix convention rationale; fake/ directory gotcha explanation

**Comparison table (D-L8):** 4-row table covering Latency, Accuracy, Code change, and Cardinality control dimensions comparing SDK Metric vs Log-Derived.

**Annotated YAML excerpt:** Full `groups:`/`rules:` block with inline comments explaining interval and LogQL expression.

**Screenshot placeholder (D-L9):** `<img src="docs/screenshots/step-13-log-based-metrics.png" ... width="900" />` with Phase 18 comment — matches Phase 12's HTML img pattern exactly.

### Task 2: End-to-end pipeline verification (PENDING — checkpoint:human-verify)

Human verification of the complete Phase 13 pipeline against a live stack. Not yet executed — awaiting checkpoint.

## Deviations from Plan

None — Task 1 executed exactly as specified. Section content matches the plan's `<action>` block verbatim.

## Known Stubs

None. The README section:
- References real files created in Plan 01 (`infra/observability/loki-rules/fake/order-errors.yaml`)
- References real mise tasks created in Plan 01 (`verify:log-metrics`)
- References real dashboard panels created in Plan 01 (Panel id=18 "Log-Based Error Rate vs SDK Counter")
- Screenshot placeholder is intentional (Phase 18 Playwright will capture it per D-L9/D-E9 precedent)

## Threat Surface Scan

No new security-relevant surfaces. README is public-facing workshop content containing only localhost URLs and copy-paste commands. Threats T-13-05 and T-13-06 are within the `accept` disposition per the plan's threat model.

## Self-Check: PASSED

- `README.md`: FOUND (modified)
- `## Step 13: Log-Based Metrics` heading: FOUND at line 716
- Step 13 before `## Concepts & FAQ` (line 815): CONFIRMED
- `log:order_errors:rate2m` occurrences: 6 (>= 3, PASS)
- `step-13-log-based-metrics.png` placeholder: FOUND
- `mise run verify:log-metrics` in fenced bash block: FOUND
- `docker compose restart loki` in Run block: FOUND
- `record: log:order_errors:rate2m` YAML excerpt: FOUND
- `only step with no Java changes` D-L7 hook: FOUND
- Subsections (What you'll learn / Checkpoint / Run / What to look for / Why it matters): ALL FOUND
- SDK Metric vs Log-Derived comparison table: FOUND
- Commit 8bdba43: FOUND
