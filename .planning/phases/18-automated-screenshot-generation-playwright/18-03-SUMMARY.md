---
phase: 18-automated-screenshot-generation-playwright
plan: "03"
subsystem: docs
tags:
  - playwright
  - screenshots
  - grafana
  - tempo
  - exemplars
  - git-tag

requires:
  - phase: 18-automated-screenshot-generation-playwright/18-01
    provides: scripts/capture-screenshots.mjs (unified Playwright capture script)
  - phase: 18-automated-screenshot-generation-playwright/18-02
    provides: mise.toml [tasks.screenshots] one-liner; README.md step-04 image embed

provides:
  - docs/screenshots/step-04-metrics.png (RED Metrics panel capture, closes PREREQ-02)
  - docs/screenshots/step-11-tail-sampling-OFF.png (Tempo trace search, tail sampling disabled)
  - docs/screenshots/step-11-tail-sampling-ON.png (Tempo trace search, tail sampling enabled)
  - docs/screenshots/step-12-exemplars.png (HTTP Request Duration histogram with exemplar dots)
  - Annotated git tag step-18-screenshots on main (WORK-01)
  - .gitignore entries for node_modules/ and package-lock.json

affects:
  - Workshop attendees (final visual teaching artifacts shipped)
  - ROADMAP Phase 18 status (can be flipped to Shipped)

tech-stack:
  added: []
  patterns:
    - "Grafana 13 viewPanel mode drops data-panel-id attribute; use bare 'canvas' selector"
    - "Tempo 2.10.x nativeSearch queryType in Grafana Explore; traceqlSearch returns 400"
    - "Grafana 13 Explore renders trace lists as ARIA virtual grid (role=gridcell), not HTML table"
    - "Grafana Explore requires explicit Run query click; queries do not auto-execute on URL load"

key-files:
  created:
    - docs/screenshots/step-04-metrics.png
    - docs/screenshots/step-11-tail-sampling-OFF.png
    - docs/screenshots/step-11-tail-sampling-ON.png
    - docs/screenshots/step-12-exemplars.png
  modified:
    - scripts/capture-screenshots.mjs
    - .gitignore

key-decisions:
  - "Grafana 13 viewPanel mode selector: bare 'canvas' instead of '[data-panel-id=N] canvas' (Rule 1 bug fix)"
  - "Tempo queryType nativeSearch over traceqlSearch: Tempo 2.10.x returns 400 on traceqlSearch in Grafana Explore"
  - "ARIA grid selector [role=gridcell] over table tbody tr: Grafana 13 renders trace results as virtual grid"
  - "Time range widened from 5 to 15 minutes: covers OTLP batch flush latency in workshop-grade infra"
  - "Explicit Run query click in tempo captures: Grafana Explore does not auto-execute on page load"

patterns-established:
  - "Grafana 13 Explore automation: always click Run query button after page load before waiting for results"
  - "ARIA role-based selectors for Grafana virtual lists: [role=gridcell] for trace table data"

requirements-completed:
  - SCAP-01
  - SCAP-02
  - SCAP-03

duration: 6min
completed: "2026-05-03"
---

# Phase 18 Plan 03: Execute Screenshot Capture and Apply Tag Summary

**4 v2.0 Playwright screenshots captured (RED Metrics, tail-sampling OFF/ON pair, exemplars histogram), human-verified, and tagged as step-18-screenshots on main**

## Performance

- **Duration:** ~6 min (including infra startup, 30s warm-up, tail-sampling toggle cycle, human verification)
- **Started:** 2026-05-03T23:10:00Z
- **Completed:** 2026-05-03T23:21:00Z
- **Tasks:** 3 (1 auto + 1 human-verify checkpoint + 1 auto)
- **Files modified:** 6

## Accomplishments

- All 4 v2.0 PNGs produced by `mise run screenshots` exit code 0 (SCAP-01)
- Tail-sampling OFF/ON pair shows trace table with visible rows; config restored cleanly (SCAP-02)
- All 4 PNGs passed human visual review: no blank frames, no spinners, correct panel content (SCAP-03)
- Annotated tag `step-18-screenshots` applied on main HEAD per WORK-01
- Fixed 5 Rule 1 bugs in capture script for Grafana 13 / Tempo 2.10.x compatibility
- Added `node_modules/` and `package-lock.json` to .gitignore (Rule 2)

## Task Commits

Each task was committed atomically:

1. **Task 1: Install Playwright and run mise run screenshots** - `fdb6fd5` (feat)
2. **Task 2: Human visual verification** - checkpoint (human-verify, approved)
3. **Task 3: Commit .gitignore + apply step-18-screenshots tag** - `ee7bc3c` (chore)

## Files Created/Modified

- `docs/screenshots/step-04-metrics.png` - RED Metrics panel (orders rate, p50/p95/p99 duration, queue depth) - closes PREREQ-02
- `docs/screenshots/step-11-tail-sampling-OFF.png` - Tempo Search with all traces visible (tail sampling disabled)
- `docs/screenshots/step-11-tail-sampling-ON.png` - Tempo Search with ~20% non-error traces (tail sampling enabled)
- `docs/screenshots/step-12-exemplars.png` - HTTP Request Duration histogram with exemplar dots visible
- `scripts/capture-screenshots.mjs` - 5 Rule 1 bug fixes for Grafana 13 / Tempo 2.10.x
- `.gitignore` - Added node_modules/ and package-lock.json entries

## Decisions Made

- **Grafana 13 viewPanel selector fix:** `[data-panel-id="N"] canvas` does not exist in viewPanel kiosk mode; switched to bare `canvas` selector. Verified via Playwright DOM inspection.
- **Tempo queryType fix:** `traceqlSearch` returns HTTP 400 on Tempo 2.10.5 via Grafana Explore. `nativeSearch` renders correctly with Search tab UI. The dashboard panels use `traceql` queryType directly, but Explore requires `nativeSearch` for the Search tab experience.
- **ARIA grid selector:** Grafana 13 renders Tempo trace results as a virtual ARIA grid (`role="row"` / `role="gridcell"`) not HTML `<table>` elements. Changed selector from `table tbody tr` to `[role="gridcell"]`.
- **Explicit Run query click:** Grafana 13 Explore does not auto-execute queries on URL load. Added `page.getByRole('button', { name: 'Run query' }).click()` before waiting for results.
- **Time range 5min to 15min:** OTLP batch exporters flush on intervals; 5-minute window missed traces that were still in the batch pipeline. 15 minutes provides reliable coverage.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed viewPanel canvas selector for Grafana 13**
- **Found during:** Task 1 (running mise run screenshots)
- **Issue:** `[data-panel-id="3"] canvas` selector timeout — Grafana 13's viewPanel mode does not render `data-panel-id` attribute
- **Fix:** Changed waitSelector to bare `canvas` for all dashboard captures
- **Files modified:** scripts/capture-screenshots.mjs
- **Verification:** Panel 3 (RED Metrics) and Panel 16 (Exemplars) both capture successfully
- **Committed in:** fdb6fd5

**2. [Rule 1 - Bug] Fixed Tempo queryType from traceqlSearch to nativeSearch**
- **Found during:** Task 1 (running mise run screenshots)
- **Issue:** `traceqlSearch` queryType returns HTTP 400 Bad Request from Tempo 2.10.5 via Grafana Explore
- **Fix:** Changed buildTempoUrl to use `nativeSearch` queryType with `serviceName` parameter
- **Files modified:** scripts/capture-screenshots.mjs
- **Verification:** Tempo Search tab renders trace table correctly with nativeSearch
- **Committed in:** fdb6fd5

**3. [Rule 1 - Bug] Fixed Tempo table selector from HTML to ARIA grid**
- **Found during:** Task 1 (running mise run screenshots)
- **Issue:** `table tbody tr` selector never matches — Grafana 13 renders trace results as virtual ARIA grid, not HTML table
- **Fix:** Changed waitSelector to `[role="gridcell"]`
- **Files modified:** scripts/capture-screenshots.mjs
- **Verification:** 18 gridcell elements found in DOM; selector resolves within 8s
- **Committed in:** fdb6fd5

**4. [Rule 1 - Bug] Added Run query button click for Grafana Explore**
- **Found during:** Task 1 (running mise run screenshots)
- **Issue:** Grafana 13 Explore does not auto-execute queries loaded via URL parameters; trace table empty after page load
- **Fix:** Added `page.getByRole('button', { name: 'Run query' }).click()` in the tempo capture handler
- **Files modified:** scripts/capture-screenshots.mjs
- **Verification:** After clicking Run query, trace rows populate within 8 seconds
- **Committed in:** fdb6fd5

**5. [Rule 1 - Bug] Extended time range from 5 to 15 minutes**
- **Found during:** Task 1 (running mise run screenshots)
- **Issue:** 5-minute window missed recently-exported traces due to OTLP batch flush latency
- **Fix:** Changed fixedTimeRange from 5*60*1000 to 15*60*1000
- **Files modified:** scripts/capture-screenshots.mjs
- **Verification:** Tempo search returns traces with the wider window
- **Committed in:** fdb6fd5

**6. [Rule 2 - Missing Critical] Added node_modules and package-lock.json to .gitignore**
- **Found during:** Task 3 (post-capture cleanup)
- **Issue:** `scripts/node_modules/` and `scripts/package-lock.json` are runtime artifacts that should not be tracked
- **Fix:** Added entries to root .gitignore
- **Files modified:** .gitignore
- **Verification:** git status no longer shows node_modules or package-lock as untracked
- **Committed in:** ee7bc3c

---

**Total deviations:** 6 auto-fixed (5 Rule 1 bugs, 1 Rule 2 missing critical)
**Impact on plan:** All fixes necessary for the capture script to work with Grafana 13 / Tempo 2.10.x. No scope creep — all fixes target the capture script's runtime behavior.

## Issues Encountered

None beyond the deviation fixes above. Once the 5 Rule 1 bugs were resolved, `mise run screenshots` executed cleanly with exit code 0 on the first full run.

## User Setup Required

None - no external service configuration required. Workshop attendees run `cd scripts && npm install && cd .. && mise run screenshots`.

## Next Phase Readiness

- Phase 18 is complete: all 3 plans executed, all artifacts shipped
- ROADMAP Phase 18 status can be flipped to Shipped
- All v2.0 teaching PNGs are committed and tagged
- Workshop repo is ready for delivery with `step-18-screenshots` as the final checkpoint tag

## Self-Check: PASSED

- docs/screenshots/step-04-metrics.png: EXISTS (71975 bytes)
- docs/screenshots/step-11-tail-sampling-OFF.png: EXISTS (102171 bytes)
- docs/screenshots/step-11-tail-sampling-ON.png: EXISTS (101853 bytes)
- docs/screenshots/step-12-exemplars.png: EXISTS (64668 bytes)
- Commit fdb6fd5: EXISTS (feat(18-03): install Playwright and capture all v2.0 screenshots)
- Commit ee7bc3c: EXISTS (chore(18-03): add node_modules and package-lock.json to .gitignore)
- Tag step-18-screenshots: EXISTS (annotated, on main)
- scripts/screenshots/: REMOVED (correct)

---
*Phase: 18-automated-screenshot-generation-playwright*
*Completed: 2026-05-03*
