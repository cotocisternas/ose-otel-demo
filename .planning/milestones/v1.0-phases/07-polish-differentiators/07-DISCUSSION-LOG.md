# Phase 7: Polish & Differentiators - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `07-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 7-polish-differentiators
**Areas discussed:** Dashboard packaging, Load script (WORK-03), Screenshots strategy (DOC-04), README walkthrough scope (DOC-01), Phase 7 exit tag

---

## Initial gray-area selection

**Question:** Which Phase 7 implementation areas do you want to discuss?

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard packaging | Auto-provisioned via volume mount vs one-click import; panel layout tradeoffs | ✓ |
| Load script (WORK-03) | Pure bash+curl vs oha/hey via mise; cadence + termination + priority mix | ✓ |
| Screenshots strategy (DOC-04) | Storage path; capture method (hand vs scripted); scope (minimum vs per-step) | ✓ |
| README walkthrough scope (DOC-01) | Minimal fill of Steps 1/2/3 vs full refactor across all six | ✓ |

**User's choice:** All four areas selected.
**Notes:** User wanted full discussion across all four deliverables — no deferred areas at this gate.

---

## Dashboard packaging

### Sub-decision 1 — Provisioning mechanism

**Question:** How should the Grafana dashboard be packaged?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-provision via volume mount | Add `./grafana/dashboards:<otel-lgtm-path>:ro` to the lgtm service in docker-compose.yml; zero clicks for the attendee | ✓ |
| Ship JSON for one-click import | Commit `dashboards/ose-otel-demo.json` only; README adds an import-from-Grafana-UI step | |
| Both — auto-provision + JSON committed at known path | Auto-provision is the default; the JSON also lives at a known committed path for SaaS portability | |

**User's choice:** Auto-provision via volume mount.
**Notes:** Zero-click attendee experience preferred over one-click-import simplicity. README still teaches the provisioning mechanism in 2-3 lines.

### Sub-decision 2 — Panel layout

**Question:** What panels should the dashboard contain?

| Option | Description | Selected |
|--------|-------------|----------|
| Single-row demo dashboard | Four panels in one row: Tempo trace search → service graph → Mimir RED metrics → Loki log panel filtered by trace_id (FEATURES.md DF-1 default) | |
| Multi-row 'three signals' | Three rows, one per signal (traces / metrics / logs); production-style depth | |
| Single-row + collapsed deeper-dive second row | Top row = demo strip from option A; second row (collapsed by default) = per-priority breakdown, error-status traces, raw logs (recommended-friendly) | ✓ |

**User's choice:** Single-row + collapsed deeper-dive second row.
**Notes:** Best of both worlds: top row carries the live workshop demo; collapsed second row supports post-workshop poking around without crowding the demo screen.

---

## Load script (WORK-03)

### Sub-decision 1 — Tool + cadence

**Question:** How should `scripts/load.sh` work?

| Option | Description | Selected |
|--------|-------------|----------|
| Pure bash + curl, fixed-cadence trickle | Vanilla while-loop, ~1 req/sec, alternates priorities, runs until Ctrl-C, zero deps | |
| Pure bash + curl, configurable | Same as A + RPS / DURATION env-var support | |
| oha (or hey) via mise + curl fallback | mise pins oha; load.sh wraps oha invocation; live RPS + p50/p95 TUI doubles as a tiny instrumentation lesson | ✓ |

**User's choice:** oha (or hey) via mise + curl fallback.
**Notes:** oha's TUI showing live RPS + p50/p95 is itself a teaching moment — partial mirror of `http.server.request.duration` in Mimir.

### Sub-decision 2 — Priority mix mechanism

**Question:** How should the load script handle the express/standard priority mix?

| Option | Description | Selected |
|--------|-------------|----------|
| Two parallel oha invocations | Two backgrounded `oha` processes, one per priority, ~0.5 req/sec each, SIGINT/SIGTERM trap kills both on Ctrl-C | ✓ |
| Single oha, fixed payload (priority=standard) | Drop alternation in load.sh; demo:order remains the alternator for hand-driven demos | |
| Pre-generated payload file with both priorities, oha --body-file | Stream from line-delimited JSON; one process, mixed priorities — depends on oha version supporting the flag | |

**User's choice:** Two parallel oha invocations.
**Notes:** Trickier shell scripting (background processes + trap handling) but populates the dashboard's per-priority deeper-dive panel cleanly.

---

## Screenshots strategy (DOC-04)

### Sub-decision 1 — Capture method

**Question:** How are the broken-vs-fixed screenshots captured and stored?

| Option | Description | Selected |
|--------|-------------|----------|
| Hand-captured one-shot, committed to docs/screenshots/ | Author runs the workshop, takes PNGs manually, commits them (FEATURES.md DF-3 default) | |
| Scripted capture via Chrome MCP / Playwright | A script drives Grafana headlessly across all tags, captures deterministically, commits PNGs | ✓ |
| Hand-captured + a docs/SCREENSHOT-RECIPE.md | Same as A + a markdown recipe documenting how to reproduce each shot | |

**User's choice:** Scripted capture via Chrome MCP / Playwright.
**Notes:** User valued reproducibility over toolchain-simplicity. Trades zero-deps for a Playwright/Node toolchain dep — flagged as a real cost in CONTEXT.md.

### Sub-decision 2 — Scope + toolchain

**Question:** What's the screenshot scope and toolchain?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimum DOC-04 set, Playwright via mise | 3 captures (broken/fixed pair + waterfall); Playwright (npm) via mise-pinned Node | |
| Per-step set, Playwright via mise | DOC-04 + one screenshot per step (FEATURES.md DF-3); 6–8 PNGs total | ✓ |
| Minimum DOC-04 set, Chrome MCP (claude-in-chrome) | Same minimum as A but capture via the Chrome MCP server; no Playwright/Node toolchain dep | |

**User's choice:** Per-step set, Playwright via mise.
**Notes:** Materially richer README; ~3x the capture-script complexity (per-step Grafana navigation differs). Phase 7 will likely have screenshot tooling as its own plan within the phase.

---

## README walkthrough scope (DOC-01)

### Sub-decision 1 — Rewrite scope

**Question:** How should the README walkthrough be structured for DOC-01?

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal — fill Steps 1/2/3 to match existing 4/5/6 prose | Smallest diff; preserves existing 4/5/6 bodies untouched | |
| Refactor — establish a tighter per-step template, rewrite all six | Cleanest final artifact; reads like a textbook; preserves every load-bearing fact from existing 4/5/6 | ✓ |
| Hybrid — fill 1/2/3 in existing voice + add a uniform 'TL;DR strip' to all six | Smaller diff than full refactor; per-step scannability without rewriting working text | |

**User's choice:** Refactor — establish a tighter per-step template, rewrite all six.
**Notes:** Highest-risk option (could lose pedagogically-load-bearing detail). CONTEXT.md D-07 enumerates the explicit invariants the planner MUST preserve when rewriting Steps 4/5/6.

### Sub-decision 2 — Per-step template structure

**Question:** What sections must every per-step block in the refactored README contain?

| Option | Description | Selected |
|--------|-------------|----------|
| Lean 5-section template | What you'll learn / Checkpoint / Run / What to look for / Why it matters | |
| 7-section template (lean + 'Code touched' + 'Common questions') | Lean 5 + per-step file list + per-step FAQ entries | |
| Lean 5-section template + standalone Concepts appendix | Each step uses the lean 5; existing standalone narrative sections preserved as a Concepts & FAQ appendix | ✓ |

**User's choice:** Lean 5-section template + standalone Concepts appendix.
**Notes:** Best of both — per-step blocks stay scannable; deep-dive prose stays preserved without duplication. The README has TWO reading modes: walkthrough vs concepts-first.

---

## Phase 7 exit tag

**Question:** Does Phase 7 get its own annotated git tag at completion?

| Option | Description | Selected |
|--------|-------------|----------|
| Tag step-07-polish | Parallel naming with step-01..step-06; preserves convention | |
| Tag v1.0 | Semantic-version tag signaling 'workshop is delivery-ready' | |
| No tag — main HEAD is the polish state | ROADMAP.md says 'plus the polish state' (state, not tag); WORK-01 mandates only step-01..step-06 | ✓ |
| Both — step-07-polish AND v1.0 | Apply both tags at the same commit | |

**User's choice:** No tag — main HEAD is the polish state.
**Notes:** Steps 1–6 each correspond to a discrete OTel SDK lesson; Phase 7 is artifact polish, not a new lesson — adding step-07-polish would imply a SDK-shaped checkpoint that doesn't exist. v1.0 can be applied later via `/gsd-complete-milestone` if needed.

---

## Claude's Discretion

Areas explicitly deferred to research/planner per CONTEXT.md `<decisions>` "Claude's Discretion" section:

- Exact in-container path for otel-lgtm dashboard provisioning (D-01)
- `oha` mise plugin availability check / `hey` fallback decision (D-03)
- Exact dashboard panel queries — JSON authoring against running demo (D-02)
- Playwright auth + timing strategy for capture-screenshots (D-05)
- Whether to capture `step-03-waterfall.png` if Playwright timing proves brittle (D-06)
- README appendix cross-reference link style (D-08)
- README quick-start placement above Prerequisites or merged with it (D-07)
- Manual-only `mise run docs:screenshots` vs CI-checkable (D-05) — recommendation: manual

## Deferred Ideas

Captured in CONTEXT.md `<deferred>` section:

- GitHub Actions / CI YAML for `mise run test` — v2 if workshop becomes a maintained shared artifact
- `docs/FACILITATOR.md` (FAC-01) — v2 ask, depends on first-cohort feedback
- Sampling-variant / baggage / DLX-retry checkpoints (SAMP-01 / PROP-V2-01 / FAIL-01) — REQUIREMENTS.md v2
- Per-attendee load-script tweakability (RPS / DURATION env vars) — v1.x ask
- CI-based screenshot-regression check — v2 ask
- `step-03-waterfall.png` and `step-01-empty-tempo.png` — planner-discretion drops if Playwright timing brittle
- Vendor-specific exporter swap demo — out-of-scope for v1
- Pyroscope / fourth-signal lesson — out-of-scope per PROJECT.md
- Custom histogram bucket boundaries / Views API tutorial — out-of-scope for v1
- Phase 7 v1.0 tag (separate from phase exit gate) — possible at `/gsd-complete-milestone`
