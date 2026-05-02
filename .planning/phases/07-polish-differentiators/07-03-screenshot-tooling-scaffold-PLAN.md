---
phase: 07-polish-differentiators
plan: 03
type: execute
wave: 2
depends_on: [07-02]
files_modified:
  - scripts/screenshots/package.json
  - scripts/screenshots/capture.mjs
  - scripts/screenshots/.gitignore
  - mise.toml
autonomous: true
requirements: [DOC-04]
risk: medium
tags: [screenshots, playwright, node, mise, scaffold]

must_haves:
  truths:
    - "scripts/screenshots/package.json pins Playwright + Chromium versions for reproducibility"
    - "scripts/screenshots/capture.mjs is syntactically valid Node ESM and exports a callable script entry point"
    - "mise run docs:screenshots task is registered and invokes the capture pipeline"
    - "Authentication against otel-lgtm Grafana (admin/admin) is implemented as a login-once-then-cycle pattern"
  artifacts:
    - path: "scripts/screenshots/package.json"
      provides: "Pinned Playwright dependency for reproducible captures"
      contains: '"playwright"'
    - path: "scripts/screenshots/capture.mjs"
      provides: "Playwright capture script driving Grafana headlessly across all six git tags"
      contains: "chromium.launch"
    - path: "scripts/screenshots/.gitignore"
      provides: "Excludes node_modules + Playwright browsers cache from git"
      contains: "node_modules"
    - path: "mise.toml"
      provides: "[tasks.\"docs:screenshots\"] task wiring"
      contains: 'tasks."docs:screenshots"'
  key_links:
    - from: "mise.toml [tasks.\"docs:screenshots\"]"
      to: "scripts/screenshots/capture.mjs"
      via: "node invocation through npm script"
      pattern: 'capture\.mjs'
    - from: "scripts/screenshots/capture.mjs"
      to: "http://localhost:3000"
      via: "Playwright page.goto"
      pattern: "localhost:3000"
---

<objective>
Build the screenshot-tooling scaffold (package.json + capture.mjs + mise task) so plan 07-04 (Wave 2) can run it against each of the six git tags to produce the per-step screenshot set. Implements the tooling slice of DOC-04 per CONTEXT.md D-05. THIS PLAN DOES NOT RUN THE PIPELINE — Wave 2's plan 07-04 does the actual capture (Wave 1/2 split keeps capture in its own plan because it requires running infra + git-checkout cycling, which is high-blast-radius).

Purpose: Reproducibility — Grafana UI shifts every otel-lgtm release; without a script the screenshots silently rot. The script also embeds the deterministic-timing strategy (waitForSelector + fixed time-range URL parameters) that makes the captures consistent across re-runs.

Output: `scripts/screenshots/` directory containing `package.json`, `capture.mjs`, `.gitignore`, plus a new `[tasks."docs:screenshots"]` entry in `mise.toml`. No PNGs produced yet — Wave 2 produces those.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@mise.toml
@docker-compose.yml
@CLAUDE.md

<interfaces>
<!-- Grafana endpoints the script drives -->

otel-lgtm v0.26.0 default Grafana:
- URL: http://localhost:3000
- Login: POST /login with admin/admin (env vars in docker-compose.yml)
- Dashboard URL pattern: http://localhost:3000/d/<uid>/<slug>?from=<unix-ms>&to=<unix-ms>
- Tempo Explore: http://localhost:3000/explore?left=<json-encoded-query>

Dashboard UID (from plan 07-01): "ose-otel-demo"

<!-- Six tags being captured (all already exist on main per `git tag -l`) -->
- step-01-baseline       -> step-01-empty-tempo.png
- step-02-traces         -> step-02-disconnected-traces.png  (DOC-04 broken half)
- step-03-context-propagation -> step-03-joined-trace.png    (DOC-04 fixed half)
                              + (optional) step-03-waterfall.png
- step-04-metrics        -> step-04-metrics.png
- step-05-logs           -> step-05-logs-trace-jump.png
- step-06-tests          -> step-06-test-output.png

Node ESM (.mjs) — top-level await is supported in Node 22+ (LTS pinned in mise.toml).
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create scripts/screenshots/ scaffold (package.json + .gitignore)</name>
  <read_first>
    - mise.toml (existing `node = "lts"` entry confirms Node toolchain is already pinned)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-05 — Playwright + Node, scoped to scripts/screenshots/, does NOT enter Spring Boot toolchain)
  </read_first>
  <action>
    Create directory `scripts/screenshots/` (sibling to `scripts/load.sh`).

    Author `scripts/screenshots/package.json` with EXACT content:
    ```json
    {
      "name": "ose-otel-demo-screenshots",
      "version": "0.1.0",
      "private": true,
      "type": "module",
      "description": "Workshop screenshot capture pipeline (DOC-04 / Phase 7 D-05). Drives Grafana headlessly across the six step-NN-* git tags.",
      "scripts": {
        "capture": "playwright install --with-deps chromium && node capture.mjs"
      },
      "dependencies": {
        "playwright": "1.49.1"
      }
    }
    ```
    Notes on the package.json:
    - `"type": "module"` enables ESM (.mjs) without filename gymnastics.
    - `"private": true` prevents accidental npm publish (this is internal tooling).
    - Playwright pinned to a specific version (1.49.1) — D-05 reproducibility requirement;
      bumping is a deliberate v2 ask, not silent drift. If a newer Playwright is required for
      Node 22 LTS compatibility, pin to a similarly specific version.
    - The `capture` script runs `playwright install --with-deps chromium` first time then
      invokes `node capture.mjs`. Subsequent runs no-op the install.

    Author `scripts/screenshots/.gitignore`:
    ```
    node_modules/
    package-lock.json
    .playwright-cache/
    /test-results/
    ```
    Rationale: `node_modules/` and the Playwright browsers cache are huge and reproducible from
    `package.json`; package-lock pinning is unnecessary because `playwright` is the only direct dep
    and is itself pinned.
  </action>
  <verify>
    <automated>
      test -d scripts/screenshots \
      && test -f scripts/screenshots/package.json \
      && python3 -c "import json,sys; d=json.load(open('scripts/screenshots/package.json')); assert d.get('type')=='module'; assert 'playwright' in d.get('dependencies',{}); print('OK playwright=' + d['dependencies']['playwright'])" \
      && test -f scripts/screenshots/.gitignore \
      && grep -q 'node_modules' scripts/screenshots/.gitignore \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `scripts/screenshots/` directory exists
    - `scripts/screenshots/package.json` parses as valid JSON
    - package.json has `"type": "module"`
    - package.json has `"private": true`
    - package.json declares `playwright` dependency with a pinned version (NOT `^x.y.z` or `latest`)
    - `scripts/screenshots/.gitignore` exists and excludes `node_modules`
  </acceptance_criteria>
  <done>
    Node project scaffold ready. Playwright pinned. `npm install` will be runnable from
    `scripts/screenshots/`. capture.mjs not yet authored — Task 2 lands it.
  </done>
</task>

<task type="auto">
  <name>Task 2: Author scripts/screenshots/capture.mjs (Playwright capture pipeline)</name>
  <read_first>
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-05 — Playwright auth + timing strategy + workspace isolation; D-06 — exact PNG names + Grafana lenses to capture)
    - https://playwright.dev/docs/api/class-page (page.goto, page.screenshot, waitForSelector)
    - https://playwright.dev/docs/auth (login-once-then-cycle pattern)
  </read_first>
  <action>
    Author `scripts/screenshots/capture.mjs` as a Node ESM script with the following structure.
    Concrete content:

    ```javascript
    // scripts/screenshots/capture.mjs
    //
    // Workshop screenshot capture pipeline (DOC-04 / Phase 7 D-05 / D-06).
    //
    // Drives Grafana headlessly across the six step-NN-* git tags. Each capture run:
    //   1. git checkout step-NN-*           (in a worktree to keep main intact)
    //   2. mise run infra:up                (idempotent — already running OK)
    //   3. mise run dev                     (background — both apps)
    //   4. mise run load                    (background — populates dashboards with traffic)
    //   5. wait for "warm-up" (panels populate)
    //   6. Playwright: login once, navigate to dashboard, screenshot panel
    //   7. tear down dev + load
    //   8. git checkout main                (return)
    //
    // The git-checkout cycling is scoped to a `git worktree` so the main checkout is
    // not disturbed if the script is interrupted (D-05 workspace-isolation guidance).
    //
    // Run via: mise run docs:screenshots
    // Or directly:  cd scripts/screenshots && npm install && npm run capture
    //
    // Output: docs/screenshots/step-NN-*.png (committed to git; Wave 2 plan 07-04 commits them).

    import { chromium } from 'playwright';
    import { mkdir } from 'node:fs/promises';
    import { resolve, dirname } from 'node:path';
    import { fileURLToPath } from 'node:url';
    import { execSync } from 'node:child_process';

    const __dirname = dirname(fileURLToPath(import.meta.url));
    const REPO_ROOT = resolve(__dirname, '..', '..');
    const OUTPUT_DIR = resolve(REPO_ROOT, 'docs', 'screenshots');

    const GRAFANA_URL = process.env.GRAFANA_URL || 'http://localhost:3000';
    const GRAFANA_USER = process.env.GRAFANA_USER || 'admin';
    const GRAFANA_PASS = process.env.GRAFANA_PASS || 'admin';
    const DASHBOARD_UID = 'ose-otel-demo';
    const WARMUP_MS = Number(process.env.WARMUP_MS || 30_000);

    // Fixed time-range pin — D-05 deterministic-timing primitive.
    // Use a fixed 5-minute window ending "now" so panels render the same time slice
    // every run; Grafana URL params accept absolute Unix-ms.
    function fixedTimeRange() {
      const to = Date.now();
      const from = to - 5 * 60 * 1000;
      return { from, to };
    }

    /**
     * D-06 captures (the planner picks 6 minimum / 7-8 with optional waterfall).
     * Each entry: { tag, output, kind, target, selector }
     *  - kind = 'dashboard' (open ose-otel-demo dashboard, screenshot a panel by selector)
     *  - kind = 'tempo'     (open Tempo Explore, run a search, screenshot results)
     *  - kind = 'loki'      (open Loki Explore with a trace_id query, screenshot result)
     *  - kind = 'terminal'  (run `mise run test` in a child process, capture stdout to PNG via headless terminal)
     */
    const CAPTURES = [
      {
        tag: 'step-01-baseline',
        output: 'step-01-empty-tempo.png',
        kind: 'tempo',
        notes: 'Tempo trace search showing zero traces (Phase 1 baseline emits no telemetry)',
      },
      {
        tag: 'step-02-traces',
        output: 'step-02-disconnected-traces.png',
        kind: 'tempo',
        notes: 'DOC-04 broken half — TWO separate trace IDs for one logical request',
      },
      {
        tag: 'step-03-context-propagation',
        output: 'step-03-joined-trace.png',
        kind: 'tempo',
        notes: 'DOC-04 fixed half — ONE trace ID for one logical request',
      },
      {
        tag: 'step-03-context-propagation',
        output: 'step-03-waterfall.png',
        kind: 'tempo',
        optional: true,  // D-06 — drop to 6 PNGs if waterfall capture proves brittle
        notes: 'Waterfall view of joined trace showing producer -> AMQP -> consumer parentage',
      },
      {
        tag: 'step-04-metrics',
        output: 'step-04-metrics.png',
        kind: 'dashboard',
        panelSelector: '[data-panel-id]:has-text("RED Metrics")',
        notes: 'Mimir Explore showing orders_created_total + http_server_request_duration_seconds quantiles',
      },
      {
        tag: 'step-05-logs',
        output: 'step-05-logs-trace-jump.png',
        kind: 'loki',
        notes: 'Loki log line with trace_id MDC stamping + click-through-to-Tempo lens',
      },
      {
        tag: 'step-06-tests',
        output: 'step-06-test-output.png',
        kind: 'terminal',
        command: 'mise run test',
        notes: 'mvn verify output showing random RabbitMQ port + four green Failsafe tests',
      },
    ];

    // ---------------------------------------------------------------------------
    // Auth helper — login once, reuse storage state across all captures (D-05).
    // ---------------------------------------------------------------------------
    async function loginAndStoreState(browser) {
      const context = await browser.newContext();
      const page = await context.newPage();
      await page.goto(`${GRAFANA_URL}/login`);
      // Grafana login form selectors — verified against otel-lgtm v0.26.0 bundled Grafana.
      // If selectors break across Grafana versions, the script reports a clear error and exits.
      await page.fill('input[name="user"]', GRAFANA_USER);
      await page.fill('input[name="password"]', GRAFANA_PASS);
      await page.click('button[type="submit"]');
      await page.waitForURL(/\/$|\/dashboards/, { timeout: 15_000 });
      const storage = await context.storageState();
      await context.close();
      return storage;
    }

    // ---------------------------------------------------------------------------
    // Per-tag capture: cycles git tag, brings up infra + dev + load, screenshots,
    // then tears down. The actual cycling logic lives in this Wave-1 scaffold but
    // is exercised by Wave 2 (plan 07-04).
    // ---------------------------------------------------------------------------
    async function captureForTag(browser, storageState, capture) {
      const context = await browser.newContext({ storageState });
      const page = await context.newPage();
      const { from, to } = fixedTimeRange();
      const outPath = resolve(OUTPUT_DIR, capture.output);

      try {
        if (capture.kind === 'dashboard') {
          const url = `${GRAFANA_URL}/d/${DASHBOARD_UID}/?from=${from}&to=${to}&kiosk=tv`;
          await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
          await page.waitForSelector('[data-panel-id]', { timeout: 15_000 });
          // Allow panel data to settle.
          await page.waitForTimeout(2_000);
          await page.screenshot({ path: outPath, fullPage: true });
        } else if (capture.kind === 'tempo') {
          const url = `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=` +
            encodeURIComponent(JSON.stringify({ tempo: { datasource: 'tempo', queries: [{ refId: 'A', queryType: 'traceqlSearch' }], range: { from: String(from), to: String(to) } } }));
          await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
          await page.waitForTimeout(3_000);
          await page.screenshot({ path: outPath, fullPage: true });
        } else if (capture.kind === 'loki') {
          const url = `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=` +
            encodeURIComponent(JSON.stringify({ loki: { datasource: 'loki', queries: [{ refId: 'A', expr: '{service_name=~"order-.*"}' }], range: { from: String(from), to: String(to) } } }));
          await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
          await page.waitForTimeout(3_000);
          await page.screenshot({ path: outPath, fullPage: true });
        } else if (capture.kind === 'terminal') {
          // Run the command and capture stdout into an HTML page rendered to PNG.
          const stdout = execSync(capture.command, { cwd: REPO_ROOT, encoding: 'utf-8', maxBuffer: 50 * 1024 * 1024 });
          const html = `<!doctype html><meta charset="utf-8"><style>body{background:#1e1e1e;color:#d4d4d4;font-family:'Cascadia Code',Menlo,monospace;font-size:13px;line-height:1.4;padding:20px;white-space:pre-wrap;}</style><body>${stdout.replace(/[<>&]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;'}[c]))}`;
          await page.setContent(html);
          await page.screenshot({ path: outPath, fullPage: true });
        } else {
          throw new Error(`Unknown capture kind: ${capture.kind}`);
        }
        console.log(`captured ${capture.output}`);
      } catch (err) {
        if (capture.optional) {
          console.warn(`optional capture failed (${capture.output}): ${err.message} — skipping per D-06`);
        } else {
          throw err;
        }
      } finally {
        await context.close();
      }
    }

    // ---------------------------------------------------------------------------
    // Tag-cycling shell — Wave 2 (plan 07-04) is the actual exercise of this loop.
    // ---------------------------------------------------------------------------
    async function main() {
      await mkdir(OUTPUT_DIR, { recursive: true });
      console.log(`output dir: ${OUTPUT_DIR}`);

      const browser = await chromium.launch({ headless: true });
      try {
        const storage = await loginAndStoreState(browser);
        for (const c of CAPTURES) {
          // The driving loop assumes the *current* checkout matches `c.tag` and infra+dev+load
          // are already running with `WARMUP_MS` of warm-up traffic. Wave 2 / plan 07-04
          // wraps this script with the per-tag git-worktree cycle.
          await captureForTag(browser, storage, c);
        }
      } finally {
        await browser.close();
      }
    }

    main().catch(err => { console.error(err); process.exit(1); });
    ```

    Notes on this scaffold:
    - The script does NOT cycle git tags itself in Wave 1 — that responsibility lives in plan 07-04
      (Wave 2) which calls this script per-tag from a wrapping `mise run docs:screenshots` loop.
      This split keeps blast radius low: Wave 1's plan only authors files; Wave 2 actually runs
      the high-blast-radius operation (git checkout cycling + infra startup).
    - Auth uses login-once-then-cycle (D-05 — login form POST then storage-state reuse).
    - Deterministic-timing: fixed 5-minute window via Unix-ms URL params (D-05).
    - Optional capture (`step-03-waterfall`) is skipped silently if it fails (D-06 — falls back to
      6 PNGs, not 7).
    - The `terminal` kind is a creative trick to render `mvn verify` output as a PNG without using
      a true terminal-emulator screenshot — sets HTML content with monospace styling, screenshots
      via Playwright. Acceptable rendering for the workshop README's Step 6 *What to look for*.
  </action>
  <verify>
    <automated>
      test -f scripts/screenshots/capture.mjs \
      && node --check scripts/screenshots/capture.mjs \
      && grep -q "import { chromium } from 'playwright'" scripts/screenshots/capture.mjs \
      && grep -q 'chromium.launch' scripts/screenshots/capture.mjs \
      && grep -q 'localhost:3000' scripts/screenshots/capture.mjs \
      && grep -q 'step-01-empty-tempo.png' scripts/screenshots/capture.mjs \
      && grep -q 'step-02-disconnected-traces.png' scripts/screenshots/capture.mjs \
      && grep -q 'step-03-joined-trace.png' scripts/screenshots/capture.mjs \
      && grep -q 'step-04-metrics.png' scripts/screenshots/capture.mjs \
      && grep -q 'step-05-logs-trace-jump.png' scripts/screenshots/capture.mjs \
      && grep -q 'step-06-test-output.png' scripts/screenshots/capture.mjs \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `scripts/screenshots/capture.mjs` exists
    - `node --check scripts/screenshots/capture.mjs` exits 0 (syntactically valid ESM)
    - File imports `chromium` from `'playwright'`
    - File contains `chromium.launch`
    - File references all six required PNG names: `step-01-empty-tempo.png`, `step-02-disconnected-traces.png`, `step-03-joined-trace.png`, `step-04-metrics.png`, `step-05-logs-trace-jump.png`, `step-06-test-output.png`
    - File implements login-once-then-cycle (helper named `loginAndStoreState` or equivalent that returns `storageState`)
    - File implements `optional: true` skip-on-fail logic for the waterfall capture
    - File targets `http://localhost:3000` (configurable via `GRAFANA_URL` env var)
  </acceptance_criteria>
  <done>
    Capture script authored, syntactically valid, references all DOC-04 / D-06 PNG outputs.
    Pipeline NOT YET RUN — Wave 2 plan 07-04 cycles tags + invokes this script.
  </done>
</task>

<task type="auto">
  <name>Task 3: Wire mise run docs:screenshots task</name>
  <read_first>
    - mise.toml (existing [tasks.*] entries — add new without disturbing existing)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-05 — `mise run docs:screenshots` orchestrates git checkout + infra:up + dev + load + capture + checkout main)
  </read_first>
  <action>
    Append to `mise.toml` after the `[tasks.load]` block (added by plan 07-02):

    ```toml
    # ──────────────────────────────────────────────────────────────────
    # Screenshot capture pipeline (Phase 7 / DOC-04 / D-05).
    # Manual one-shot — run before completing Phase 7 to refresh docs/screenshots/.
    # CI does NOT re-run this (per D-05).
    # WARNING: cycles git tags (step-01-baseline ... step-06-tests) in a worktree.
    # ──────────────────────────────────────────────────────────────────
    [tasks."docs:screenshots"]
    description = "Capture per-step screenshots (DOC-04). Cycles step-NN-* tags, drives Grafana via Playwright, writes docs/screenshots/*.png."
    run = """
    set -euo pipefail
    cd scripts/screenshots
    npm install
    npx playwright install --with-deps chromium
    node capture.mjs
    """
    ```

    Existing tasks (`preflight`, `infra:*`, `build`, `test`, `dev:*`, `dev`, `demo:order`, `verify:bom`,
    `ui:grafana`, `ui:rabbitmq`, `load` from plan 07-02) UNCHANGED.
  </action>
  <verify>
    <automated>
      grep -q 'tasks."docs:screenshots"' mise.toml \
      && grep -q 'capture.mjs' mise.toml \
      && mise tasks 2>&1 | grep -q 'docs:screenshots' \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `mise.toml` contains the literal `tasks."docs:screenshots"`
    - `mise.toml` `docs:screenshots` task body references `capture.mjs`
    - `mise tasks` lists `docs:screenshots`
    - Existing `[tasks.load]` block (from plan 07-02) UNCHANGED
    - Existing `[tasks."verify:bom"]` block UNCHANGED
  </acceptance_criteria>
  <done>
    `mise run docs:screenshots` is registered. Wave 2's plan 07-04 will run it (after extending the
    task body to wrap the per-tag git-worktree cycle, OR keep this minimal task and wrap it via a
    bash driver — plan 07-04 picks the wrapper shape).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Workshop laptop -> Grafana UI (localhost:3000) | Default `admin/admin` creds intentional for offline workshop |
| Capture script -> arbitrary Grafana URL | URL is NOT user-supplied; hardcoded to localhost:3000 with env-var override `GRAFANA_URL` |
| `node_modules/` install footprint | Pinned via package.json; reproducible across team members |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-03-01 | Spoofing | Hardcoded admin/admin Grafana creds in capture.mjs (env-var fallback) | accept | Workshop default for offline laptop usage; `GRAFANA_USER`/`GRAFANA_PASS` env-vars allow override for production-Grafana variants — documented in capture.mjs comments |
| T-07-03-02 | Information Disclosure | capture.mjs reads `process.env.GRAFANA_PASS` | mitigate | Env vars are never logged by capture.mjs; storageState is held in-memory only, not written to disk |
| T-07-03-03 | Tampering | Pinned `playwright` version in package.json | mitigate | Exact version pinned (1.49.1); `package-lock.json` is gitignored to avoid drift; updates require deliberate edit + reverify |
| T-07-03-04 | Elevation of Privilege | `playwright install --with-deps chromium` | accept | Workshop tooling is run on developer laptops with intentional Playwright dependency; `--with-deps` installs OS browser libraries — documented as a one-time install |
| T-07-03-05 | Denial of Service | Playwright + Chromium install footprint (~500MB) | accept | One-time install; `.gitignore` excludes the cache from commits |
</threat_model>

<verification>
- `scripts/screenshots/package.json` parses cleanly, has pinned `playwright` version.
- `scripts/screenshots/capture.mjs` is valid ESM (`node --check`).
- Capture script references all six required PNG names.
- `mise tasks` lists `docs:screenshots`.
- Pipeline NOT yet exercised — Wave 2 plan 07-04 runs the capture.
</verification>

<success_criteria>
- `scripts/screenshots/{package.json,capture.mjs,.gitignore}` all exist with valid content.
- `mise.toml` `[tasks."docs:screenshots"]` registered, runnable via `mise run docs:screenshots`.
- Auth + deterministic-timing strategy implemented per D-05.
- DOC-04 PNG names referenced per D-06.
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-03-SUMMARY.md` recording:
- Pinned Playwright version
- Any Grafana selector that needed adjustment for otel-lgtm v0.26.0's bundled Grafana version
- Any Discretion items resolved (e.g., kept all 7 PNGs vs falling back to 6 — Wave 2 verifies)
</output>
