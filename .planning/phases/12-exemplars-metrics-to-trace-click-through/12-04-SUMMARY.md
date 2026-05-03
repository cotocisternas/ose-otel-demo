---
phase: 12-exemplars-metrics-to-trace-click-through
plan: "04"
subsystem: docs, infra
tags: [exemplars, readme, mimir, grafana, tempo, verification, workshop]
dependency_graph:
  requires:
    - "12-01: ExemplarFilter.traceBased() on both SdkMeterProviders (EXMP-01)"
    - "12-02: Mimir exemplar storage config (EXMP-02)"
    - "12-03: Grafana histogram panel + verify:exemplars mise task (EXMP-03)"
  provides:
    - "README §12 workshop walkthrough with all seven D-E8 subsections (EXMP-04 documentation)"
    - "End-to-end exemplar pipeline verified GREEN via mise run verify:exemplars"
  affects:
    - "README.md §12 teaching section"
    - "All future phases that reference git diff step-11-tail-sampling..step-12-exemplars"
tech_stack:
  added: []
  patterns:
    - "Three-layer exemplar architecture explanation (SDK → Collector → Mimir → Grafana)"
    - "Annotated config excerpt pattern matching §11 style"
key_files:
  created:
    - .planning/phases/12-exemplars-metrics-to-trace-click-through/12-04-SUMMARY.md
  modified:
    - README.md
key_decisions:
  - "Services must be restarted after ExemplarFilter SDK changes — spring-boot:run loads classes at JVM startup from target/classes; hot-reload does not apply across session boundaries"
  - "Task 1 produced no file commit (smoke test verification only); README §12 committed as sole Task 2 artifact"
requirements-completed:
  - EXMP-04
metrics:
  duration: "5min"
  completed: "2026-05-03T20:34:16Z"
  tasks_completed: 3
  files_modified: 1
---

# Phase 12 Plan 04: README §12 and End-to-End Verification Summary

**README §12 (92 lines, seven D-E8 subsections) written and end-to-end exemplar pipeline verified GREEN — mise run verify:exemplars confirms trace_id-bearing exemplars in Mimir after Mimir restart and service restart with ExemplarFilter active**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-03T20:28:45Z
- **Completed:** 2026-05-03T20:34:16Z
- **Tasks:** 3 (1 smoke test, 1 README write, 1 human-verify checkpoint approved)
- **Files modified:** 1 (README.md)

## Accomplishments

- Restarted Mimir (picks up `limits.max_global_exemplars_per_user: 100000` config) and OTel Collector
- Identified and resolved root cause of initial verify:exemplars failure: services were running from the pre-Plan-01 JVM (started at session start before ExemplarFilter changes). Killed and restarted producer and consumer services to load the ExemplarFilter-enabled compiled classes
- Generated load and confirmed `mise run verify:exemplars` exits 0 with "GREEN — exemplars with trace_id are present in Mimir"
- Wrote README §12 ("Step 12: Exemplars — three lines that make histogram charts clickable") as 92-line insertion between §11 F2-3 blockquote and `## Concepts & FAQ`
- Section includes all seven D-E8 subsections: What you'll learn, Checkpoint, Run, What to look for (with three-layer annotated config excerpts), Why it matters, screenshot placeholder
- Consumer asymmetry teaching note (D-E11): counter exemplars in Mimir but not visible as chart dots
- Production consideration note for `/prometheus/api/v1/query_exemplars` access control (T-12-07 mitigation)

## Task Commits

1. **Task 1: Restart infra and run end-to-end smoke test** — no file commit (infrastructure verification only; services restarted in-process)
2. **Task 2: Write README §12 section** - `922ac85` (feat)
3. **Task 3: Human-verify checkpoint** — EXMP-04 approved: exemplar dot click-through to Tempo confirmed

**Plan metadata:** `0153d8f` (docs: complete plan — pre-checkpoint), final commit below (post-checkpoint)

## Files Created/Modified

- `README.md` — §12 inserted between §11 F2-3 blockquote (line 631) and `## Concepts & FAQ` (now line 724); 92 lines added

## Decisions Made

- Services (`spring-boot:run` via `mise run dev`) need to be explicitly restarted after SDK source changes — the JVM loads class files from `target/classes` at startup; `mvn compile` updates the files on disk but does not trigger a JVM restart. This is expected behavior; the plan's smoke test step implicitly depends on services being restarted after Plan 01's ExemplarFilter changes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Restarted Java services to load ExemplarFilter changes**
- **Found during:** Task 1 (end-to-end smoke test)
- **Issue:** `mise run verify:exemplars` returned `{"data":[]}` despite all config being correct. Root cause: the Java services were started at session start (before Plan 01's ExemplarFilter commits). The JVM had loaded the pre-ExemplarFilter compiled classes and did not hot-reload after `mvn compile`.
- **Fix:** Sent SIGTERM to both Java JVM processes (PIDs 1842719/1842738). Restarted producer (`mvn spring-boot:run`, port 8080) and consumer (`mvn spring-boot:run`, port 8081) with the updated `target/classes`. Waited for both `/actuator/health` endpoints to return `UP`. Generated load again — verify:exemplars turned GREEN.
- **Files modified:** None (infrastructure restart; no source change needed)
- **Verification:** `mise run verify:exemplars` exits 0 with "GREEN — exemplars with trace_id are present in Mimir"
- **Committed in:** N/A (no source files changed; deviation was procedural)

---

**Total deviations:** 1 auto-fixed (Rule 1 — services required restart after SDK changes from prior plan)
**Impact on plan:** Necessary for correctness; no scope creep. The plan's smoke test step assumed services would be restarted but did not explicitly state it.

## Issues Encountered

`mise run verify:exemplars` initially failed with `{"data":[]}`. Investigation sequence:
1. Checked all three diagnostic items from the verify task: ExemplarFilter present, scope fix applied, Mimir limits configured — all correct.
2. Confirmed histogram metric `http_server_request_duration_seconds_bucket` was present in Mimir (plain metric query returned data) — metrics were flowing.
3. Identified the running JVM processes were started at 14:06, before Plan 01's ExemplarFilter commits (made ~12 min before this plan started).
4. Restarted both services → verified:exemplars turned GREEN.

## Known Stubs

- `docs/screenshots/step-12-exemplars.png` — placeholder HTML `<img>` tag in README §12 references this file; actual screenshot to be captured by Phase 18 Playwright automation. This is intentional per D-E9/D-E10 and does not block Phase 12's teaching goals.

## Threat Flags

T-12-07 mitigated: README §12 "What to look for" section includes a `> Production consideration.` callout noting that `/prometheus/api/v1/query_exemplars` endpoint should be restricted to authorized internal users in production. This surfaces the ASVS L1 access control concern to workshop attendees.

## Human Verification (Task 3 — checkpoint:human-verify)

**EXMP-04 APPROVED.** User confirmed:
- Exemplar dots visible on the "HTTP Request Duration (with Exemplars)" histogram panel in the ose-otel-demo dashboard
- Clicking an exemplar dot navigates directly to the originating trace in Tempo
- No manual trace-ID copy-paste required — one-click metric-to-trace click-through works end-to-end

## Next Phase Readiness

- Phase 12 is complete. All four plans (01-04) have been committed and the human-verify checkpoint is approved.
- End-to-end exemplar pipeline is verified GREEN (automated + human).
- Git tag `step-12-exemplars` is ready to be applied per WORK-01 / D-21 (orchestrator-owned gate).
- Phase 13 (log-based-metrics-loki-recording-rules) can proceed — no blockers from Phase 12.

## Self-Check: PASSED

- FOUND: README.md (modified, §12 present)
- FOUND: .planning/phases/12-exemplars-metrics-to-trace-click-through/12-04-SUMMARY.md
- FOUND: commit 922ac85 (Task 2)
- `grep -c 'Step 12: Exemplars' README.md` → 1 (PASS)
- `grep -c 'verify:exemplars' README.md` → 4 (PASS, ≥1 required)
- `grep -c 'max_global_exemplars_per_user' README.md` → 3 (PASS, ≥1 required)
- `grep -c 'step-12-exemplars.png' README.md` → 1 (PASS)
- `grep -c 'exemplarTraceIdDestinations' README.md` → 3 (PASS, ≥1 required)
- `grep -c 'counter exemplars exist in Mimir' README.md` → 1 (PASS)
- `grep -c '## Concepts & FAQ' README.md` → 1 (PASS, not duplicated)
- §12 at line 632, Concepts & FAQ at line 724: ORDER CORRECT

---
*Phase: 12-exemplars-metrics-to-trace-click-through*
*Completed: 2026-05-03*
