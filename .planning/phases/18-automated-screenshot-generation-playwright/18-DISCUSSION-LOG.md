# Phase 18: Automated Screenshot Generation (Playwright) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 18-automated-screenshot-generation-playwright
**Areas discussed:** Script architecture, ON/OFF toggle mechanism, Screenshot inventory, Warm-up & assertions

---

## Script architecture

### Q1: Replace existing capture.mjs or create new script alongside?

| Option | Description | Selected |
|--------|-------------|----------|
| Replace entirely | Delete scripts/screenshots/capture.mjs + package.json. New scripts/capture-screenshots.mjs subsumes all v1.0 AND v2.0 captures. One script, one CAPTURES array, one mise task. | ✓ |
| New script, keep old | Create scripts/capture-screenshots.ts for v2.0 captures only. Keep scripts/screenshots/capture.mjs for v1.0 tag-cycling captures. Two separate mise tasks. | |

**User's choice:** Replace entirely
**Notes:** None

### Q2: TypeScript or JavaScript?

| Option | Description | Selected |
|--------|-------------|----------|
| JavaScript (.mjs) | Keep plain JS like the v1.0 script. No build step, no tsx dependency. The capture script is a dev tool, not a teaching artifact. | ✓ |
| TypeScript (.ts with tsx) | Follow the roadmap's scripts/capture-screenshots.ts naming. Add tsx as a devDependency. | |

**User's choice:** JavaScript (.mjs)
**Notes:** Overrides roadmap's .ts naming

### Q3: Where should the new script live?

| Option | Description | Selected |
|--------|-------------|----------|
| scripts/capture-screenshots.mjs | Top-level in scripts/. Delete old scripts/screenshots/ directory. | ✓ |
| scripts/screenshots/capture-screenshots.mjs | Stay in the existing screenshots/ subdirectory. Reuse existing package.json. | |

**User's choice:** scripts/capture-screenshots.mjs
**Notes:** None

### Q4: Tag-cycling logic location?

| Option | Description | Selected |
|--------|-------------|----------|
| All logic in the script | Script handles git worktree create/checkout/teardown for v1.0 + current-HEAD for v2.0. mise run screenshots is a thin one-liner. | ✓ |
| Tag cycling in mise bash | Keep the mise task bash driver for worktree management. Script handles only Playwright capture per invocation. | |

**User's choice:** All logic in the script
**Notes:** None

---

## ON/OFF toggle mechanism

### Q1: How to toggle tail sampling OFF?

| Option | Description | Selected |
|--------|-------------|----------|
| Backup-restore | cp to .bak → sed comment-out → restart → capture OFF → restore .bak → restart → capture ON. Unconditional restore in finally. | ✓ |
| Alternate config file | Ship a second otelcol-config-no-tail-sampling.yaml. Script swaps in place. No sed. | |
| Pipeline-only toggle | Only remove 'tail_sampling' from pipelines list (leave processor definition). | |

**User's choice:** Backup-restore
**Notes:** P18-3 compliant (unconditional restore in finally block)

### Q2: How long to wait after ON restore before capturing?

| Option | Description | Selected |
|--------|-------------|----------|
| 30s warm-up | Wait 30s after health-check passes. 10s for decision_wait + 20s buffer. | ✓ |
| Health-check only | Proceed as soon as Collector responds on :13133/. | |
| You decide | Let planner/researcher pick based on throughput math. | |

**User's choice:** 30s warm-up
**Notes:** Matches P18-2

### Q3: Flush Tempo for clean OFF baseline?

| Option | Description | Selected |
|--------|-------------|----------|
| Time-range isolation | Don't flush. Use Grafana 'Last 5 min' time range. Wait 30s so visible window shows only unsampled traces. | ✓ |
| Flush + reload | docker compose restart tempo to clear in-flight traces. | |

**User's choice:** Time-range isolation
**Notes:** Non-destructive, matches README's 'Last 5 min' framing from Phase 11 D-T10

---

## Screenshot inventory

### Q1: Re-capture v1.0 screenshots or v2.0 only?

| Option | Description | Selected |
|--------|-------------|----------|
| All captures, guard with skip | CAPTURES includes ALL screenshots. Existing PNGs skipped by default. FORCE=1 re-captures everything. | ✓ |
| v2.0 only | New script only handles v2.0 captures (step-11+). v1.0 captures stay as manual PNGs. | |
| All captures, always overwrite | No skip logic. Every run re-captures everything. | |

**User's choice:** All captures, guard with skip
**Notes:** One authoritative inventory

### Q2: Include future Phase 13/15/17 entries now?

| Option | Description | Selected |
|--------|-------------|----------|
| Add entries as phases land | Ship with entries only for screenshots that have README placeholders TODAY. | ✓ |
| Placeholder entries now | Add speculative entries with TODO markers. | |

**User's choice:** Add entries as phases land
**Notes:** Avoids guessing view names

### Q3: step-04-metrics.png capture strategy?

| Option | Description | Selected |
|--------|-------------|----------|
| Current-HEAD capture | Capture from dashboard at main HEAD. No tag checkout. Closes PREREQ-02. | ✓ |
| Tag checkout capture | Checkout step-04-metrics tag in worktree. | |

**User's choice:** Current-HEAD capture
**Notes:** Dashboard JSON unchanged since v1.0; migration invisible

---

## Warm-up & assertions

### Q1: Data-presence assertion strategy?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-capture selector + timeout | Each entry defines waitSelector + waitTimeout (15s). networkidle → waitForSelector → 500ms settle → screenshot. | ✓ |
| Generic networkidle + fixed delay | waitForLoadState('networkidle') + waitForTimeout(3000). No per-capture config. | |
| Retry loop with screenshot diff | Compare consecutive frames for pixel stability. | |

**User's choice:** Per-capture selector + timeout
**Notes:** Fails hard if selector doesn't appear

### Q2: Load warm-up approach?

| Option | Description | Selected |
|--------|-------------|----------|
| Script-managed load lifecycle | Script starts load.sh as background child, waits WARMUP_MS, captures, kills load on exit. | ✓ |
| Assume load running externally | Operator must start load first. Script just checks infra + apps. | |
| Infra + app + load orchestration | Script does full lifecycle including infra health + dev app startup. | |

**User's choice:** Script-managed load lifecycle
**Notes:** Self-contained, matches v1.0 WARMUP_MS pattern

### Q3: Post-run diff report?

| Option | Description | Selected |
|--------|-------------|----------|
| Summary to stdout | Print table: filename / status (captured/skipped/failed). Exit non-zero on required failures. | ✓ |
| Git diff integration | Run git diff --stat docs/screenshots/ and print changed files. | |
| No report | Each capture logs as it goes. No summary. | |

**User's choice:** Summary to stdout
**Notes:** CI-friendly, matches roadmap SC

---

## Claude's Discretion

- Exact CSS selectors for each capture's `waitSelector` — researcher inspects Grafana 13.0.1 DOM
- Exact `sed` regex for commenting out tail_sampling processor block and pipeline entry
- Playwright version to pin in new package.json
- WARMUP_MS default value tuning
- Whether v1.0 tag-cycling captures need full worktree + dev + load cycle per tag
- Exact Grafana URL parameter construction for each view
- scripts/package.json scope and location

## Deferred Ideas

None — discussion stayed within phase scope.
