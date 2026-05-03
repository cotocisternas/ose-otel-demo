# Phase 18: Automated Screenshot Generation (Playwright) - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all manual `docs/screenshots/` captures with a single self-contained Playwright script (`scripts/capture-screenshots.mjs`) and a thin `mise run screenshots` task. The script handles both v1.0 tag-cycling captures (step-01 through step-06) and v2.0 current-HEAD captures (step-04-metrics, step-11 OFF/ON pair, step-12 exemplars, step-14 JPA, step-16 head-sampling) — any workshop maintainer can regenerate every teaching PNG after a stack upgrade by running one command.

Phase boundaries (locked by ROADMAP.md SC#1–3 + this discussion):
- Single `scripts/capture-screenshots.mjs` replaces `scripts/screenshots/capture.mjs` + its `package.json` + the `mise.toml` `docs:screenshots` bash driver
- The old `scripts/screenshots/` directory is deleted entirely
- All CAPTURES entries for screenshots that have README placeholders TODAY are included; future phases (13, 15, 17) add their own entries when they define README image references
- Existing v1.0 PNGs are skipped by default (file-exists check); `FORCE=1` re-captures everything
- The tail-sampling OFF/ON pair uses backup-restore on `otelcol-config.yaml` with unconditional restore in a `finally` block (P18-3)
- Per-capture data-presence selectors with 15s timeout replace the v1.0 fixed-delay approach
- Script manages load.sh lifecycle (start → warm-up → capture → kill) — no external load assumption
- `step-04-metrics.png` captured at current HEAD (closes PREREQ-02)

Out of scope for this phase:
- Phases 13, 15, 17 screenshot entries — added by each phase when it defines README image references
- Head-sampling ON/OFF toggle (Phase 16 is a separate toggle from tail-sampling; its screenshot is a single "~50% trace count" capture, not a paired OFF/ON)
- Video recording or animated GIF capture
- CI/CD integration for screenshot freshness checks

</domain>

<decisions>
## Implementation Decisions

### Script architecture

- **D-S1:** **Replace entirely** — delete `scripts/screenshots/capture.mjs`, `scripts/screenshots/package.json`, and the `scripts/screenshots/` directory. The new `scripts/capture-screenshots.mjs` subsumes all v1.0 AND v2.0 captures in a single CAPTURES array. One script, one source of truth.

- **D-S2:** **JavaScript (.mjs)** — no TypeScript, no build step, no `tsx` dependency. The capture script is a dev tool, not a teaching artifact. The roadmap's `.ts` naming is overridden by this decision.

- **D-S3:** **Location: `scripts/capture-screenshots.mjs`** — top-level in `scripts/`, not in a subdirectory. Playwright dependency lives in a `scripts/package.json` (or the script's own adjacent `package.json`).

- **D-S4:** **All logic in the script** — git worktree create/checkout/teardown for v1.0 tag captures AND current-HEAD captures for v2.0 are all self-contained in the script. `mise run screenshots` is a thin one-liner (`node scripts/capture-screenshots.mjs`). No bash/JS split.

- **D-S5:** **`mise run screenshots` replaces `mise run docs:screenshots`** — the old task is deleted along with the bash driver. New task name is shorter and sits alongside `mise run load`, `mise run dev`, etc.

### ON/OFF toggle mechanism

- **D-T1:** **Backup-restore** — `cp otelcol-config.yaml otelcol-config.yaml.bak` → `sed` to comment out `tail_sampling` from the `processors:` definition block AND the `pipelines.traces.processors:` list → `docker compose restart otel-collector` → health-check poll on `:13133/` → 30s warm-up → capture OFF frame → `cp .bak` back → restart → health-check → 30s warm-up → capture ON frame. P18-3 compliance: the `.bak` restore happens unconditionally in a `finally` block — even if the OFF-frame capture throws, the dev stack is never left with tail sampling disabled.

- **D-T2:** **30s warm-up** after each Collector restart — 10s covers `decision_wait` expiry on the first batch, plus 20s buffer for enough traces to show a visible volume difference in Tempo. Matches P18-2 (30s timeout prescribed by roadmap). The warm-up is after the health-check passes (Collector responding on `:13133/`), not after `docker compose restart` returns.

- **D-T3:** **Time-range isolation for OFF baseline** — don't flush Tempo. Use Grafana's `Last 5 min` time-range in the capture URL so the visible window shows only traces ingested during the 30s warm-up period. Wait 30s after removing tail sampling so all visible traces were ingested without the processor. Non-destructive, matches the README's `Last 5 min` framing from Phase 11 D-T10.

### Screenshot inventory

- **D-I1:** **All captures in one array, skip-by-default** — the CAPTURES array includes ALL screenshots (v1.0 tag-cycling + v2.0 current-HEAD). Existing PNGs on disk are skipped by default (file-exists check before capture). `FORCE=1` env var re-captures everything. One authoritative inventory means one run can regenerate the full set after a stack upgrade.

- **D-I2:** **Add entries as phases land** — Phase 18 ships with entries only for screenshots that have README placeholders or references TODAY: `step-01` through `step-06` (v1.0), `step-04-metrics` (PREREQ-02), `step-11-tail-sampling-OFF/ON`, `step-12-exemplars`. Entries for `step-14-jpa` and `step-16-head-sampling` are added when Phases 14 and 16 land and define their README image references. No speculative entries.

- **D-I3:** **step-04-metrics.png at current HEAD** — captured without tag checkout. The README already documents that "the dashboard JSON itself is unchanged since v1.0 Phase 7, so the migration is invisible." Running against the decomposed stack at HEAD produces the correct panel view. Closes PREREQ-02.

### Warm-up & assertions

- **D-W1:** **Per-capture CSS selector + timeout** — each CAPTURES entry defines a `waitSelector` (CSS selector for a visible data element) and a `waitTimeout` (default 15s). Capture flow: `waitForLoadState('networkidle')` → `waitForSelector(waitSelector, { timeout: waitTimeout })` → 500ms settle delay → `page.screenshot()`. Different views get different selectors (Tempo search table row, histogram SVG path, exemplar annotation marker). Capture fails hard if the selector doesn't appear within the timeout.

- **D-W2:** **Script-managed load lifecycle** — the script starts `scripts/load.sh` as a background child process, waits `WARMUP_MS` (default 30s, env-configurable), then begins captures. On script exit, the load process is killed. Self-contained — no assumption about external state. Matches the v1.0 `capture.mjs` `WARMUP_MS` pattern.

- **D-W3:** **Summary table to stdout** — after all captures complete, print a table: `filename | status (captured / skipped / failed)`. If any required (non-optional) captures failed, exit non-zero. Simple, CI-friendly. Matches the roadmap SC's "flags any PNG that failed to update" requirement.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on codebase patterns and Grafana DOM inspection:
- Exact CSS selectors for each capture's `waitSelector` — researcher inspects Grafana 13.0.1's DOM structure at runtime or via docs to find stable selectors (prefer `data-testid` or `aria-*` attributes over class names).
- Exact `sed` regex for commenting out the tail_sampling processor block and pipeline entry — researcher inspects the current `otelcol-config.yaml` structure and picks a defensible pattern.
- Playwright version to pin in the new `package.json` — use latest stable at plan time (v1.0 used 1.49.1).
- `WARMUP_MS` default value tuning — 30s is the starting point; researcher can adjust based on observed panel render times during planning.
- Whether v1.0 tag-cycling captures need the full worktree + `mise run dev` + `mise run load` cycle per tag (v1.0 pattern) or can share a single running stack — researcher decides based on which captures actually need tag-specific code running.
- Exact Grafana URL parameter construction for each view (Tempo Explore, Loki Explore, dashboard panel) — follow v1.0 `capture.mjs` patterns and extend for v2.0 views.
- `scripts/package.json` scope — whether Playwright dep lives in a new `scripts/package.json` or the existing `scripts/screenshots/package.json` is moved. The directory is deleted, so a new file is needed regardless.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including WORK-01 (annotated git tags on `main`), TRACE-01/DOC-05
- `.planning/REQUIREMENTS.md` § PREREQ-02 (step-04-metrics.png deferred capture) — closed by this phase
- `.planning/ROADMAP.md` Phase 18 section — SC#1–3, pitfall mitigations P18-1/P18-2/P18-3, git tag `step-18-screenshots`
- `.planning/STATE.md` — Phase 12 completion record; roadmap evolution noting Phase 18 addition

### Prior phase context (screenshots deferred TO this phase)

- `.planning/phases/11-tail-sampling-at-the-collector/11-CONTEXT.md` — D-T9 (manual one-shot screenshots deferred to Phase 18), D-T10 (screenshot subject = Tempo Search "Last 5 min" trace count)
- `.planning/phases/12-exemplars-metrics-to-trace-click-through/12-CONTEXT.md` — D-E9 (no manual screenshots, deferred to Phase 18), D-E10 (placeholder + caption style in README)
- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — D-13 (PREREQ-02 manual one-shot precedent — Phase 18 replaces this)

### Files this phase REPLACES (must read before planning)

- `scripts/screenshots/capture.mjs` — v1.0 Playwright capture script; structural predecessor, CAPTURES array format and `captureForTag()` function shape are the template for the new script
- `scripts/screenshots/package.json` — v1.0 Playwright dep (1.49.1); new package.json replaces this
- `mise.toml` `[tasks."docs:screenshots"]` block (lines 163-259) — v1.0 bash tag-cycling driver; replaced by thin `[tasks.screenshots]` one-liner

### Files this phase EDITS

- `mise.toml` — delete `docs:screenshots` task, add `screenshots` task
- `README.md` — update step-04 TODO placeholder to render the actual PNG (PREREQ-02 closure); verify all screenshot references match the CAPTURES array filenames

### Files this phase CREATES

- `scripts/capture-screenshots.mjs` — new unified Playwright capture script
- `scripts/package.json` — Playwright dependency for the new script
- `docs/screenshots/step-04-metrics.png` — PREREQ-02 closure
- `docs/screenshots/step-11-tail-sampling-OFF.png` — tail-sampling OFF frame
- `docs/screenshots/step-11-tail-sampling-ON.png` — tail-sampling ON frame
- `docs/screenshots/step-12-exemplars.png` — exemplar dots on histogram panel

### Files referenced by the script at runtime (must understand for selector/URL design)

- `infra/observability/otelcol-config.yaml` — tail_sampling processor block (D-T1 sed target)
- `grafana/dashboards/ose-otel-demo.json` — panel IDs and row structure for dashboard captures
- `grafana/datasources.yaml` — datasource UIDs for Explore URL construction
- `scripts/load.sh` — load generator managed by the capture script (D-W2)
- `docker-compose.yml` — `otel-collector` service name for `docker compose restart`

### Upstream documentation references

- [Playwright API — page.screenshot()](https://playwright.dev/docs/api/class-page#page-screenshot) — capture options, fullPage, clip
- [Playwright API — page.waitForSelector()](https://playwright.dev/docs/api/class-page#page-wait-for-selector) — selector timeout and state options
- [Grafana Explore URL parameters](https://grafana.com/docs/grafana/latest/explore/) — panes JSON schema for constructing Tempo/Loki Explore URLs

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`scripts/screenshots/capture.mjs` lines 58-104 (CAPTURES array)** — the v1.0 capture definition format (`{ tag, output, kind, notes, selector }`) is the structural template for the v2.0 array. Extend with `waitSelector`, `waitTimeout`, `headCapture: true` (for current-HEAD v2.0 captures vs tag-cycling v1.0 captures).
- **`scripts/screenshots/capture.mjs` lines 116-161 (`captureForTag`)** — the per-capture Playwright function handles dashboard, tempo, loki, and terminal capture kinds. V2.0 extends with exemplar and histogram-specific kinds (or parameterizes existing kinds with selectors).
- **`scripts/screenshots/capture.mjs` lines 180-191 (Chromium detection)** — system Chromium fallback logic for Arch Linux / CI. Reuse verbatim in the new script (P18-1 `--no-sandbox` + `headless: true`).
- **`mise.toml` `docs:screenshots` task (lines 163-259)** — the git worktree lifecycle (create → checkout → dev → load → warmup → capture → teardown) is the template for the in-script worktree management. Key pattern: `WORKTREE_DIR`, `CAPTURE_TAG_FILTER`, per-tag loop with cleanup trap.
- **`scripts/load.sh`** — the load generator script that D-W2 spawns as a child process. Supports `SLOW_RPS`, `BURST_RPS`, and `IDEMPOTENT_RPS` env vars for different traffic shapes.

### Established Patterns

- **`mise run verify:*` family** — `verify:bom`, `verify:datasources`, `verify:images`, `verify:tail-sampling`, `verify:exemplars`. The new `screenshots` task follows the same naming convention but is a capture task, not a verification task. It lives alongside the `verify:*` tasks in `mise.toml`.
- **WORK-01** — Workshop checkpoints are annotated tags on `main`. The v1.0 CAPTURES array references these tags for tag-cycling. Phase 18's tag is `step-18-screenshots`.
- **Phase 10 D-13 / Phase 11 D-T9 / Phase 12 D-E9** — the "defer screenshots to Phase 18" pattern established across three phases. Phase 18 is the catch-all that produces every deferred PNG.
- **Grafana anonymous access** — `GF_AUTH_ANONYMOUS_ENABLED=true` + `GF_AUTH_DISABLE_LOGIN_FORM=true` in `docker-compose.yml` means no auth needed for Playwright navigation. The v1.0 script already leverages this (no login flow).
- **Fixed time-range pinning** — v1.0's `fixedTimeRange()` function uses `Date.now()` - 5 min as the Grafana URL time range. Reuse for deterministic captures.

### Integration Points

- **`scripts/capture-screenshots.mjs`** — the primary deliverable. Imports `playwright`, manages git worktrees, starts/stops load.sh, navigates Grafana views, captures PNGs.
- **`scripts/package.json`** — new file with Playwright dependency.
- **`mise.toml`** — delete `[tasks."docs:screenshots"]` block, add `[tasks.screenshots]` one-liner.
- **`infra/observability/otelcol-config.yaml`** — read/modified at runtime by the script for the tail-sampling OFF/ON toggle (backup-restore in finally block).
- **`docs/screenshots/*.png`** — output directory; new PNGs committed alongside the script.
- **`README.md`** — step-04 TODO placeholder replaced with actual image embed (PREREQ-02 closure).
- **`docker-compose.yml`** — `docker compose restart otel-collector` called by the script for tail-sampling toggle; health-check on `:13133/`.

</code_context>

<specifics>
## Specific Ideas

- The user's pattern across all four areas was **"self-contained, single-run, no external assumptions"** — the script manages its own load lifecycle (D-W2), handles its own git worktrees (D-S4), and guards against interrupted runs (P18-3 finally block). Planner should default to "the script does everything" on any silent question.
- The user consistently chose **the v1.0 structural template as the starting point** — CAPTURES array format, Chromium detection, `fixedTimeRange()`, `captureForTag()` function shape. The new script is an evolution, not a rewrite from scratch. Planner should preserve working patterns and extend them.
- The user chose **skip-by-default semantics** (D-I1) over always-overwrite, which means the script needs a file-exists check before each capture. This is important for CI: a fresh checkout (no PNGs) captures everything; a workspace with existing PNGs only fills gaps.
- The **step-04-metrics.png at current HEAD** (D-I3) decision means this capture does NOT need tag-cycling — it runs against the live decomposed stack. Planner should group it with the v2.0 current-HEAD captures, not with the v1.0 tag-cycling batch.
- The user values **per-capture selectors** (D-W1) over generic delays. This means the CAPTURES array needs to be enriched with `waitSelector` fields that are Grafana-version-specific. Researcher should inspect the live Grafana 13.0.1 DOM to find stable selectors before planning.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

### Reviewed Todos (not folded)

None — `cross_reference_todos` step did not surface matches for Phase 18 scope.

</deferred>

---

*Phase: 18-automated-screenshot-generation-playwright*
*Context gathered: 2026-05-03*
