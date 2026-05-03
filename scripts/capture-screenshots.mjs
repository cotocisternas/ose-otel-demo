// scripts/capture-screenshots.mjs
//
// Unified workshop screenshot capture pipeline (Phase 18 / SCAP-01/02/03).
// Replaces scripts/screenshots/capture.mjs + the mise.toml docs:screenshots bash driver.
//
// Run via: mise run screenshots   (or: cd scripts && node capture-screenshots.mjs)
//
// Prerequisites: mise run infra:up && mise run dev must be running before executing.
// The script validates Grafana reachability at startup and emits a clear error if not.
//
// On interrupted run (Ctrl-C): otelcol-config.yaml is restored synchronously via
// process.on('exit') handler. The operator may need to run
// `docker compose restart otel-collector` manually after an interrupted tail-sampling toggle.

import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { existsSync, copyFileSync, readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync, spawn } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');   // script at scripts/, not scripts/screenshots/
const OUTPUT_DIR = resolve(REPO_ROOT, 'docs', 'screenshots');

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const GRAFANA_URL = process.env.GRAFANA_URL || 'http://localhost:3000';
const DASHBOARD_UID = 'ose-otel-demo';
const WARMUP_MS = Number(process.env.WARMUP_MS || 30_000);
const FORCE = Boolean(process.env.FORCE);

const OTELCOL_CONFIG = resolve(REPO_ROOT, 'infra/observability/otelcol-config.yaml');
const OTELCOL_CONFIG_BAK = OTELCOL_CONFIG + '.bak';

// Fixed time-range pin — D-T3 deterministic-timing primitive.
// Use a fixed 5-minute window ending "now" so panels render the same time slice
// every run; Grafana URL params accept absolute Unix-ms.
// 15-minute window ensures data is visible even if OTLP batch flushing adds latency.
function fixedTimeRange() {
  const to = Date.now();
  const from = to - 15 * 60 * 1000;
  return { from, to };
}

// ---------------------------------------------------------------------------
// CAPTURES array — all screenshot entries (D-I1 / D-I2)
//
// v1.0 tag-cycling entries (headCapture absent):
//   - require a git worktree + per-tag Spring Boot apps
//   - tag = the annotated git tag to check out for this capture
//
// v2.0 current-HEAD entries (headCapture: true):
//   - run against the live HEAD stack already running
//   - no tag checkout needed
// ---------------------------------------------------------------------------
const CAPTURES = [
  // ---- v1.0 tag-cycling captures ----
  {
    tag: 'step-01-baseline',
    output: 'step-01-empty-tempo.png',
    kind: 'tempo',
    waitSelector: '[data-testid="Explore"]',
    waitTimeout: 15_000,
    notes: 'Phase 1 baseline — zero traces; data-testid selector confirms page loaded even with empty results',
  },
  {
    tag: 'step-02-traces',
    output: 'step-02-disconnected-traces.png',
    kind: 'tempo',
    waitSelector: 'table tbody tr',
    waitTimeout: 15_000,
    notes: 'DOC-04 broken half — TWO separate trace IDs for one logical request',
  },
  {
    tag: 'step-03-context-propagation',
    output: 'step-03-joined-trace.png',
    kind: 'tempo',
    waitSelector: 'table tbody tr',
    waitTimeout: 15_000,
    notes: 'DOC-04 fixed half — ONE trace ID for one logical request',
  },
  {
    tag: 'step-03-context-propagation',
    output: 'step-03-waterfall.png',
    kind: 'tempo',
    optional: true,
    waitSelector: 'table tbody tr',
    waitTimeout: 15_000,
    notes: 'Waterfall view of joined trace',
  },
  {
    tag: 'step-05-logs',
    output: 'step-05-logs-trace-jump.png',
    kind: 'loki',
    waitSelector: 'table tbody tr',
    waitTimeout: 15_000,
    notes: 'Loki log line with trace_id click-through to Tempo',
  },
  {
    tag: 'step-06-tests',
    output: 'step-06-test-output.png',
    kind: 'terminal',
    command: 'mvn -B -pl integration-tests -am verify',
    waitSelector: null,
    waitTimeout: 0,
    notes: 'mvn verify output showing four green Failsafe tests',
  },

  // ---- v2.0 current-HEAD captures ----
  {
    headCapture: true,
    output: 'step-04-metrics.png',
    kind: 'dashboard',
    panelId: 3,
    waitSelector: 'canvas',
    waitTimeout: 20_000,
    notes: 'RED Metrics panel — closes PREREQ-02',
  },
  {
    headCapture: true,
    output: 'step-11-tail-sampling-OFF.png',
    kind: 'tempo',
    tailSamplingToggle: 'OFF',
    waitSelector: '[role="gridcell"]',
    waitTimeout: 25_000,
    serviceName: 'order-producer',
    notes: 'tail-sampling disabled — ALL traces visible',
  },
  {
    headCapture: true,
    output: 'step-11-tail-sampling-ON.png',
    kind: 'tempo',
    tailSamplingToggle: 'ON',
    waitSelector: '[role="gridcell"]',
    waitTimeout: 25_000,
    serviceName: 'order-producer',
    notes: 'tail-sampling enabled — ~20% non-error traces visible',
  },
  {
    headCapture: true,
    output: 'step-12-exemplars.png',
    kind: 'dashboard',
    panelId: 16,
    waitSelector: 'canvas',
    waitTimeout: 25_000,
    notes: 'HTTP Request Duration histogram with exemplar dots — requires load running >30s',
  },
];

// ---------------------------------------------------------------------------
// Grafana startup validation
// ---------------------------------------------------------------------------

async function assertGrafanaReachable() {
  try {
    const resp = await fetch(`${GRAFANA_URL}/api/health`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  } catch (err) {
    console.error(`ERROR: Cannot reach Grafana at ${GRAFANA_URL}/api/health`);
    console.error('Run: mise run infra:up && mise run dev');
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// load.sh lifecycle (D-W2)
// ---------------------------------------------------------------------------

let loadProc = null;

function startLoad() {
  loadProc = spawn(resolve(REPO_ROOT, 'scripts', 'load.sh'), [], {
    cwd: REPO_ROOT,
    env: { ...process.env, SLOW_RPS: '2' },  // Phase 11 D-T7: maintain slow stream
    stdio: 'ignore',
    detached: false,
  });
  console.log('load.sh started (SLOW_RPS=2)');
}

function stopLoad() {
  if (loadProc && !loadProc.killed) {
    try { loadProc.kill('SIGTERM'); } catch { /* ignore */ }
  }
}

// ---------------------------------------------------------------------------
// Tail-sampling config restore (synchronous, safe from process.on('exit'))
// P18-3: .bak restore in unconditional finally block + process.on('exit') safety net
// ---------------------------------------------------------------------------

function restoreConfigSync() {
  try {
    if (existsSync(OTELCOL_CONFIG_BAK)) {
      copyFileSync(OTELCOL_CONFIG_BAK, OTELCOL_CONFIG);
      try { unlinkSync(OTELCOL_CONFIG_BAK); } catch { /* ignore */ }
    }
  } catch (err) {
    process.stderr.write(`ERROR: failed to restore otelcol-config.yaml: ${err.message}\n`);
    process.stderr.write('Manual fix: cp infra/observability/otelcol-config.yaml.bak infra/observability/otelcol-config.yaml\n');
  }
}
// Register on process.exit so SIGINT handler's process.exit(1) still restores file.
process.on('exit', restoreConfigSync);

// ---------------------------------------------------------------------------
// disableTailSampling function (D-T1 verified sed pattern)
// ---------------------------------------------------------------------------

function disableTailSampling(configPath) {
  let yaml = readFileSync(configPath, 'utf-8');
  // 1. Comment out tail_sampling block from "  tail_sampling:" up to "  transform/copy_recordpolicy:"
  yaml = yaml.replace(
    /^(  tail_sampling:[\s\S]*?)^(  transform\/copy_recordpolicy:)/m,
    (match, block, next) => {
      const commented = block.split('\n').map(l => l ? '# ' + l : l).join('\n');
      return commented + next;
    }
  );
  // 2. Remove tail_sampling from the traces pipeline processors list
  yaml = yaml.replace(
    /processors: \[memory_limiter, tail_sampling, transform\/copy_recordpolicy, batch\]/,
    'processors: [memory_limiter, transform/copy_recordpolicy, batch]'
  );
  writeFileSync(configPath, yaml, 'utf-8');
}

// ---------------------------------------------------------------------------
// Collector restart and health-check
// ---------------------------------------------------------------------------

function restartCollector() {
  execSync('docker compose restart otel-collector', {
    cwd: REPO_ROOT,
    stdio: 'inherit',
  });
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitForCollectorHealthy(timeoutMs = 30_000) {
  console.log('Polling collector health at localhost:13133/...');
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const resp = await fetch('http://localhost:13133/');
      if (resp.ok) { console.log('Collector healthy.'); return; }
    } catch { /* not ready yet */ }
    await sleep(1_000);
  }
  throw new Error('otel-collector health check timed out after 30s');
}

// ---------------------------------------------------------------------------
// withTailSamplingDisabled wrapper (P18-3 unconditional finally)
// ---------------------------------------------------------------------------

async function withTailSamplingDisabled(fn) {
  copyFileSync(OTELCOL_CONFIG, OTELCOL_CONFIG_BAK);
  console.log('tail_sampling: disabling for OFF-frame capture...');
  try {
    disableTailSampling(OTELCOL_CONFIG);
    restartCollector();
    await waitForCollectorHealthy();
    await sleep(WARMUP_MS);  // D-T2: 30s warm-up after health-check
    await fn();
  } finally {
    // P18-3: synchronous restore in finally — always executes even on throw.
    // process.on('exit') is the second safety net for SIGINT path.
    restoreConfigSync();
  }
  // Normal exit only: restore config is done; now restart for ON-frame warm-up.
  console.log('tail_sampling: restoring config, restarting for ON-frame warm-up...');
  restartCollector();
  await waitForCollectorHealthy();
  await sleep(WARMUP_MS);  // D-T2: 30s warm-up before ON-frame capture
}

// ---------------------------------------------------------------------------
// URL builder functions
// ---------------------------------------------------------------------------

// Dashboard panel URL (kiosk=tv suppresses nav bar — avoids networkidle timeout pitfall)
function buildDashboardUrl(from, to, panelId) {
  const base = `${GRAFANA_URL}/d/${DASHBOARD_UID}/?from=${from}&to=${to}&kiosk=tv`;
  return panelId ? `${base}&viewPanel=${panelId}` : base;
}

// Tempo nativeSearch Explore URL (traceqlSearch/traceql both 400 on Tempo 2.10.x in Explore;
// nativeSearch renders a virtual-list grid with role="gridcell" cells, not <table> elements)
function buildTempoUrl(from, to, serviceName) {
  const query = serviceName
    ? { refId: 'A', queryType: 'nativeSearch', serviceName, limit: 20 }
    : { refId: 'A', queryType: 'nativeSearch', limit: 20 };
  const panes = { tempo: { datasource: 'tempo', queries: [query], range: { from: String(from), to: String(to) } } };
  return `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}`;
}

// Loki Explore URL
function buildLokiUrl(from, to) {
  const panes = { loki: { datasource: 'loki', queries: [{ refId: 'A', expr: '{service_name=~"order-.*"}' }], range: { from: String(from), to: String(to) } } };
  return `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}`;
}

// ---------------------------------------------------------------------------
// captureView function (D-W1 waitForSelector per capture kind)
// ---------------------------------------------------------------------------

async function captureView(browser, capture) {
  const outPath = resolve(OUTPUT_DIR, capture.output);

  // D-I1 skip-by-default: existing PNGs are not overwritten unless FORCE=1
  if (!FORCE && existsSync(outPath)) {
    return { output: capture.output, status: 'skipped', optional: capture.optional ?? false };
  }

  const context = await browser.newContext();
  const page = await context.newPage();
  const { from, to } = fixedTimeRange();

  try {
    if (capture.kind === 'dashboard') {
      const url = buildDashboardUrl(from, to, capture.panelId);
      await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
      await page.waitForSelector(capture.waitSelector, { state: 'visible', timeout: capture.waitTimeout ?? 15_000 });
      await page.waitForTimeout(500);
      await page.screenshot({ path: outPath, fullPage: false });

    } else if (capture.kind === 'tempo') {
      const url = buildTempoUrl(from, to, capture.serviceName);
      await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
      // Grafana Explore requires an explicit "Run query" click before data loads
      const runBtn = page.getByRole('button', { name: 'Run query' });
      if (await runBtn.count() > 0) await runBtn.click();
      await page.waitForSelector(capture.waitSelector, { state: 'visible', timeout: capture.waitTimeout ?? 15_000 });
      await page.waitForTimeout(500);
      await page.screenshot({ path: outPath, fullPage: true });

    } else if (capture.kind === 'loki') {
      const url = buildLokiUrl(from, to);
      await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
      await page.waitForSelector(capture.waitSelector, { state: 'visible', timeout: capture.waitTimeout ?? 15_000 });
      await page.waitForTimeout(500);
      await page.screenshot({ path: outPath, fullPage: true });

    } else if (capture.kind === 'terminal') {
      // Copy verbatim from v1.0 analog (no waitSelector needed — setContent is synchronous)
      const stdout = execSync(capture.command, { cwd: REPO_ROOT, encoding: 'utf-8', maxBuffer: 50 * 1024 * 1024 });
      const html = `<!doctype html><meta charset="utf-8"><style>body{background:#1e1e1e;color:#d4d4d4;font-family:'Cascadia Code',Menlo,monospace;font-size:13px;line-height:1.4;padding:20px;white-space:pre-wrap;}</style><body>${stdout.replace(/[<>&]/g, c => ({'<': '&lt;', '>': '&gt;', '&': '&amp;'}[c]))}`;
      await page.setContent(html);
      await page.screenshot({ path: outPath, fullPage: true });

    } else {
      throw new Error(`Unknown capture kind: ${capture.kind}`);
    }

    console.log(`captured: ${capture.output}`);
    return { output: capture.output, status: 'captured', optional: capture.optional ?? false };

  } catch (err) {
    console.warn(`FAILED: ${capture.output}: ${err.message}`);
    return { output: capture.output, status: 'failed', optional: capture.optional ?? false };
  } finally {
    await context.close();
  }
}

// ---------------------------------------------------------------------------
// printSummary function (D-W3)
// ---------------------------------------------------------------------------

function printSummary(results) {
  console.log('\n=== Screenshot Summary ===');
  console.log('filename'.padEnd(45) + ' | status');
  console.log('-'.repeat(55));
  for (const r of results) {
    console.log(r.output.padEnd(45) + ' | ' + r.status);
  }
  const failed = results.filter(r => r.status === 'failed' && !r.optional);
  if (failed.length > 0) {
    console.error(`\nFAILED: ${failed.length} required capture(s) failed`);
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// captureTagCycle: v1.0 tag-cycling captures (internalizes the bash driver loop)
// ---------------------------------------------------------------------------

async function captureTagCycle(browser, capture) {
  const outPath = resolve(OUTPUT_DIR, capture.output);
  if (!FORCE && existsSync(outPath)) {
    return { output: capture.output, status: 'skipped', optional: capture.optional ?? false };
  }

  const WORKTREE_DIR = resolve(REPO_ROOT, '.screenshots-worktree');
  const MVN_RUN_PATTERN = 'spring-boot:run -D';  // scoped kill pattern (Rule-3 deviation from v1.0)

  // Clean stale worktree
  try { execSync(`git worktree remove --force "${WORKTREE_DIR}"`, { cwd: REPO_ROOT, stdio: 'ignore' }); } catch { /* not present */ }

  try {
    execSync(`git worktree add "${WORKTREE_DIR}" "${capture.tag}"`, { cwd: REPO_ROOT, stdio: 'inherit' });
    execSync('mvn -T 1C -DskipTests -q clean install', { cwd: WORKTREE_DIR, stdio: 'inherit' });

    // Start dev from worktree
    const devProc = spawn('mise', ['run', 'dev'], {
      cwd: WORKTREE_DIR,
      stdio: 'ignore',
      detached: false,
    });
    await sleep(25_000);  // Spring Boot startup

    // Start load for all tags except step-01-baseline (no apps accept requests at baseline)
    let tagLoadProc = null;
    if (capture.tag !== 'step-01-baseline') {
      tagLoadProc = spawn(resolve(REPO_ROOT, 'scripts', 'load.sh'), [], {
        cwd: REPO_ROOT,
        stdio: 'ignore',
        detached: false,
      });
      await sleep(WARMUP_MS);
    }

    const result = await captureView(browser, { ...capture, headCapture: true });

    // Tear down
    if (tagLoadProc) try { tagLoadProc.kill('SIGTERM'); } catch { /* ignore */ }
    try { devProc.kill('SIGTERM'); } catch { /* ignore */ }
    // Kill any lingering maven spring-boot:run processes
    try { execSync(`pkill -f "${MVN_RUN_PATTERN}"`, { stdio: 'ignore' }); } catch { /* none running */ }
    await sleep(2_000);

    return result;
  } finally {
    try { execSync(`git worktree remove --force "${WORKTREE_DIR}"`, { cwd: REPO_ROOT, stdio: 'ignore' }); } catch { /* ignore */ }
  }
}

// ---------------------------------------------------------------------------
// main() function
// ---------------------------------------------------------------------------

async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true });
  console.log(`output dir: ${OUTPUT_DIR}`);

  // Validate Grafana is reachable before spending time on captures
  await assertGrafanaReachable();

  // Process signal handlers for load.sh cleanup + config restore
  process.on('SIGINT', () => { stopLoad(); process.exit(1); });
  process.on('SIGTERM', () => { stopLoad(); process.exit(1); });

  // Start load.sh background child (D-W2) — warm-up happens before each capture group
  startLoad();
  console.log(`Waiting ${WARMUP_MS}ms for warm-up...`);
  await sleep(WARMUP_MS);

  // Chromium detection (copy verbatim from v1.0 + P18-1 --no-sandbox)
  const executablePath =
    process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ||
    (existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' :
     existsSync('/usr/bin/chromium-browser') ? '/usr/bin/chromium-browser' :
     existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' :
     undefined);

  const browser = await chromium.launch({
    headless: true,
    executablePath,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],  // P18-1: CI compatibility
  });

  const results = [];

  try {
    for (const capture of CAPTURES) {
      if (capture.headCapture) {
        // v2.0 current-HEAD captures

        if (capture.tailSamplingToggle === 'OFF') {
          // The OFF-frame: disable tail_sampling, restart, warm-up, capture
          await withTailSamplingDisabled(async () => {
            const r = await captureView(browser, capture);
            results.push(r);
          });
          // After withTailSamplingDisabled returns: config restored + ON-frame warm-up done.
          // The ON-frame entry immediately follows in CAPTURES; its captureView call runs next.

        } else if (capture.tailSamplingToggle === 'ON') {
          // The ON-frame: warm-up already done by withTailSamplingDisabled above.
          // Config is restored + collector is healthy. Just capture.
          const r = await captureView(browser, capture);
          results.push(r);

        } else {
          // Regular head capture (no toggle)
          const r = await captureView(browser, capture);
          results.push(r);
        }

      } else {
        // v1.0 tag-cycling capture
        const r = await captureTagCycle(browser, capture);
        results.push(r);
      }
    }
  } finally {
    await browser.close();
    stopLoad();
  }

  printSummary(results);
}

main().catch(err => { console.error(err); process.exit(1); });
