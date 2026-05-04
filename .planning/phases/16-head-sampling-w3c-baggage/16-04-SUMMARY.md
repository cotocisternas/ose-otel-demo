---
phase: 16-head-sampling-w3c-baggage
plan: "04"
subsystem: readme-docs
tags: [otel, readme, head-sampling, w3c-baggage, git-tag, HSAMP-02, BAG-04]
dependency_graph:
  requires:
    - 16-01 (sampler swap — traceIdRatioBased(0.5) ships in both services)
    - 16-02 (BaggageSpanAttributeProcessor created and registered)
    - 16-03 (OrderController baggage scope + TracingMessageListenerAdvice outer scope + verify:baggage)
  provides:
    - README §16 with sub-sections 16a (head sampling) and 16b (W3C baggage)
    - step-16-sampling-baggage annotated git tag on main
  affects:
    - README.md (§16 section appended after §15)
tech_stack:
  added: []
  patterns:
    - Step-NN README structure (What you'll learn / Run / What to look for / Checkpoint) — §16 follows §14/§15 pattern
key_files:
  created: []
  modified:
    - README.md
decisions:
  - §16 inserted immediately before "## Concepts & FAQ" — preserves existing README section order
  - §16a Warning callout appears FIRST in sub-section body (D-R1 honored)
  - Five-dimension head-vs-tail table covers Where/Sees/Bandwidth/Decides on/Trade-off (D-R3 honored)
  - D-B5 teaching moment in §16b: SERVER span no baggage.customer-tier — intentional, not a bug
  - step-16-sampling-baggage annotated git tag applied to README commit HEAD per WORK-01
  - Human-verify checkpoint APPROVED — all 5 verification steps passed (head-sampling GREEN, baggage GREEN, Tempo trace confirmed, ITs pass)
metrics:
  duration: "10min"
  completed: "2026-05-04"
  tasks: 3
  files: 1
---

# Phase 16 Plan 04: README §16 + Git Tag Summary

Added README §16 with sub-sections 16a (Head Sampling) and 16b (W3C Baggage), passed human-verify checkpoint (all 5 steps GREEN), and applied annotated git tag `step-16-sampling-baggage` per WORK-01.

## What Was Built

### Task 1: README §16 section with sub-sections 16a and 16b

Inserted `## Step 16: Head Sampling + W3C Baggage` into README.md immediately before the existing `## Concepts & FAQ` section (after §15 content, line 987).

**§16a — Head Sampling (lines 988-1065):**
- F2-3 double-filter-trap warning callout placed FIRST in the sub-section body (D-R1) — warns about 50% x 20% = ~10% effective trace rate when Phase 11 tail sampling is still active
- Five-dimension head-vs-tail comparison table covering: Where / Sees / Bandwidth / Decides on / Trade-off (D-R3)
- `traceIdRatioBased(0.5)` code snippet explaining why `parentBased` wrapper is load-bearing (without it, trace fragments appear)
- Run block: 100-request curl loop + `mise run verify:head-sampling`
- What to look for: Tempo search showing ~50 of 100 traces

**§16b — W3C Baggage (lines 1066-1155):**
- Three coordinated changes explained: BaggageSpanAttributeProcessor, OrderController baggage scope, TracingMessageListenerAdvice outer scope
- D-B5 teaching moment: SERVER span deliberately does NOT carry `baggage.customer-tier` — it was created before the controller set baggage
- Trace waterfall diagram showing baggage.customer-tier=gold on INTERNAL/PRODUCER/CLIENT/CONSUMER spans, absent on SERVER span
- Run block: gold-tier curl request + `mise run verify:baggage`
- Three-tier filter example: gold/silver/standard segmentation in Tempo

**§16 Checkpoint section:**
- `step-16-sampling-baggage` git tag reference (WORK-01)
- `git diff step-15-http-client-spans..step-16-sampling-baggage` file list

**Commit:** `cb4146d`

### Task 2: Human-Verify Checkpoint (APPROVED)

All 5 verification steps passed:
1. Stack started (both services running)
2. `verify:head-sampling` GREEN — head sampling producing traces
3. `verify:baggage` GREEN — `baggage.customer-tier=gold` reaching Tempo
4. Tempo trace confirmed: INTERNAL/PRODUCER/CONSUMER spans carry `baggage.customer-tier=gold`; SERVER span correctly absent (D-B5)
5. Integration tests: BUILD SUCCESS, all tests pass

### Task 3: Annotated git tag step-16-sampling-baggage

Applied annotated git tag `step-16-sampling-baggage` on commit `cb4146d` (HEAD) per WORK-01:

```
tag step-16-sampling-baggage
Workshop checkpoint: head sampling (50% traceIdRatioBased) + W3C baggage
(customer-tier flows from HTTP header through AMQP to both producer and consumer spans in Tempo)
```

Phase 16 source files were already committed incrementally across Plans 01-04 Task 1. The tag points to the README commit as the final Phase 16 artifact.

## Verification Results

1. `git tag -l "step-16-sampling-baggage"` returns `step-16-sampling-baggage` (PASS)
2. `git show step-16-sampling-baggage` shows the annotated tag message (PASS)
3. `grep -n "^## Step 16" README.md` returns line 988 (PASS)
4. `grep -n "^### Step 16a" README.md` returns line 994 (PASS)
5. `grep -n "^### Step 16b" README.md` returns line 1066 (PASS)
6. §15 at line 890, §16 at line 988 — correct ordering (PASS)
7. Human verified: `verify:head-sampling` GREEN (PASS)
8. Human verified: `verify:baggage` GREEN (PASS)
9. Human verified: `baggage.customer-tier=gold` on INTERNAL/PRODUCER/CONSUMER spans; absent on SERVER span (D-B5) (PASS)
10. Human verified: `mvn -pl integration-tests -am test -q` BUILD SUCCESS (PASS)

## Deviations from Plan

None — plan executed exactly as written.

Note: `grep -c "## Step 16" README.md` returns 3 (not 1 as stated in acceptance criteria) because `### Step 16a` and `### Step 16b` sub-headings also contain the string `## Step 16`. The top-level heading `## Step 16:` is present exactly once at line 988 — `grep -n "^## Step 16" README.md` returns 1. All content requirements are satisfied.

## Known Stubs

None. README §16 is fully written and verified against live stack.

## Threat Flags

None. README addition and git tag only — no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check: PASSED

- `README.md` — FOUND (modified, 171 lines inserted)
- `grep -n "^## Step 16" README.md` at line 988 — FOUND
- `grep -n "^### Step 16a" README.md` at line 994 — FOUND
- `grep -n "^### Step 16b" README.md` at line 1066 — FOUND
- `grep -c "Double-filter trap" README.md` returns 2 (PASS)
- `grep -c "BaggageSpanAttributeProcessor" README.md` returns 5 (PASS)
- `grep -c "D-B5" README.md` returns 2 (PASS)
- `grep -c "step-16-sampling-baggage" README.md` returns 3 (PASS)
- §15 (line 890) before §16 (line 988) — PASS
- Commit `cb4146d` (Task 1: README §16) — FOUND
- Tag `step-16-sampling-baggage` — FOUND
