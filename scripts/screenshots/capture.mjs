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
import { existsSync } from 'node:fs';
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

  // Plan 07-04 / Wave 2: when invoked once per tag from the mise driver, restrict the
  // CAPTURES iteration to entries matching the current checkout's tag. Without the
  // filter, every per-tag invocation would attempt all seven captures against the
  // wrong checkout and write the same files seven times.
  const TAG_FILTER = process.env.CAPTURE_TAG_FILTER;
  const filtered = TAG_FILTER ? CAPTURES.filter(c => c.tag === TAG_FILTER) : CAPTURES;
  if (TAG_FILTER) {
    console.log(`CAPTURE_TAG_FILTER=${TAG_FILTER} — running ${filtered.length} of ${CAPTURES.length} captures`);
  }

  // Prefer system chromium when present (Arch, Fedora, Ubuntu+google-chrome).
  // Fall back to Playwright's bundled chromium when no system binary is found
  // (e.g., CI containers). PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH lets the operator
  // override the auto-detection. Rule-3 deviation (07-04): the original
  // `playwright install --with-deps chromium` call fails on Arch Linux because
  // Playwright's install-deps only knows apt-get/dnf/yum, not pacman.
  const executablePath =
    process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ||
    (existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' :
     existsSync('/usr/bin/chromium-browser') ? '/usr/bin/chromium-browser' :
     existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' :
     undefined);
  const browser = await chromium.launch({ headless: true, executablePath });
  try {
    const storage = await loginAndStoreState(browser);
    for (const c of filtered) {
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
