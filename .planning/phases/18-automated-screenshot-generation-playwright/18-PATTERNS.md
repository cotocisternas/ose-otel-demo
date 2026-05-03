# Phase 18: Automated Screenshot Generation (Playwright) - Pattern Map

**Mapped:** 2026-05-03
**Files analyzed:** 5 (2 creates, 2 modifies, 1 delete operation)
**Analogs found:** 5 / 5

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `scripts/capture-screenshots.mjs` | utility / automation script | event-driven (sequential stages: load → warm-up → capture → teardown) | `scripts/screenshots/capture.mjs` | exact |
| `scripts/package.json` | config | — | `scripts/screenshots/package.json` | exact |
| `mise.toml` (delete `docs:screenshots`, add `screenshots`) | config | — | `mise.toml` `[tasks.load]` + existing task block patterns | role-match |
| `README.md` (step-04 TODO → image embed) | docs | — | `README.md` lines 109, 149–150, 263, 340 (existing screenshot embeds) | role-match |
| `scripts/screenshots/` (deleted) | — | — | n/a — deletion only | n/a |

---

## Pattern Assignments

### `scripts/capture-screenshots.mjs` (utility, sequential automation)

**Primary analog:** `scripts/screenshots/capture.mjs` (207 lines — read in full)

The new script is a direct extension of this file. Every structural element below is extracted verbatim from the analog; line numbers are from `scripts/screenshots/capture.mjs`.

---

#### Imports pattern (lines 23–31)

```javascript
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');
```

**Extension for the new script:** Add `import { spawn } from 'node:child_process'` (alongside `execSync`), and `import { copyFileSync, readFileSync, writeFileSync } from 'node:fs'` for tail-sampling backup-restore. `REPO_ROOT` computation changes because the script moves up one level: `resolve(__dirname, '..')` (script is `scripts/capture-screenshots.mjs`, not `scripts/screenshots/capture.mjs`).

---

#### Constants pattern (lines 34–48)

```javascript
const GRAFANA_URL = process.env.GRAFANA_URL || 'http://localhost:3000';
const DASHBOARD_UID = 'ose-otel-demo';
const WARMUP_MS = Number(process.env.WARMUP_MS || 30_000);

function fixedTimeRange() {
  const to = Date.now();
  const from = to - 5 * 60 * 1000;
  return { from, to };
}
```

**Extension:** Add `const OUTPUT_DIR = resolve(REPO_ROOT, 'docs', 'screenshots')` (same as analog but path relative to new `REPO_ROOT`). Add `const FORCE = Boolean(process.env.FORCE)` for skip-by-default (D-I1). Add `const OTELCOL_CONFIG = resolve(REPO_ROOT, 'infra/observability/otelcol-config.yaml')` for D-T1.

---

#### CAPTURES array pattern (lines 58–104)

```javascript
const CAPTURES = [
  {
    tag: 'step-01-baseline',
    output: 'step-01-empty-tempo.png',
    kind: 'tempo',
    notes: 'Tempo trace search showing zero traces (Phase 1 baseline emits no telemetry)',
  },
  {
    tag: 'step-03-context-propagation',
    output: 'step-03-waterfall.png',
    kind: 'tempo',
    optional: true,  // D-06 — drop if waterfall capture proves brittle
    notes: 'Waterfall view of joined trace showing producer -> AMQP -> consumer parentage',
  },
  {
    tag: 'step-04-metrics',
    output: 'step-04-metrics.png',
    kind: 'dashboard',
    panelSelector: '[data-panel-id]:has-text("RED Metrics")',
    notes: 'Mimir Explore showing orders_created_total + http_server_request_duration_seconds quantiles',
  },
];
```

**Extension fields for v2.0 entries (all new fields are additive — existing v1.0 shape is preserved):**

```javascript
// v2.0 current-HEAD captures add these fields:
{
  headCapture: true,              // no tag checkout; run against live HEAD stack
  output: 'step-04-metrics.png',
  kind: 'dashboard',
  panelId: 3,                     // numeric id from ose-otel-demo.json (panel "RED Metrics")
  waitSelector: '[data-panel-id="3"] canvas',  // canvas = timeseries panel rendered
  waitTimeout: 15_000,
  optional: false,
  notes: '...',
}

// Tail-sampling pair entries:
{
  headCapture: true,
  output: 'step-11-tail-sampling-OFF.png',
  kind: 'tempo',
  waitSelector: 'table tbody tr',
  waitTimeout: 15_000,
  tailSamplingToggle: 'OFF',      // signals the toggle sequence in captureAll()
  optional: false,
  notes: '...',
}
```

---

#### `captureForTag` function — per-capture Playwright flow (lines 116–161)

```javascript
async function captureForTag(browser, capture) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const { from, to } = fixedTimeRange();
  const outPath = resolve(OUTPUT_DIR, capture.output);

  try {
    if (capture.kind === 'dashboard') {
      const url = `${GRAFANA_URL}/d/${DASHBOARD_UID}/?from=${from}&to=${to}&kiosk=tv`;
      await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
      await page.waitForSelector('[data-panel-id]', { timeout: 15_000 });
      await page.waitForTimeout(2_000);
      await page.screenshot({ path: outPath, fullPage: true });
    } else if (capture.kind === 'tempo') {
      const url = `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=` +
        encodeURIComponent(JSON.stringify({
          tempo: {
            datasource: 'tempo',
            queries: [{ refId: 'A', queryType: 'traceqlSearch' }],
            range: { from: String(from), to: String(to) }
          }
        }));
      await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
      await page.waitForTimeout(3_000);
      await page.screenshot({ path: outPath, fullPage: true });
    } else if (capture.kind === 'loki') {
      const url = `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=` +
        encodeURIComponent(JSON.stringify({
          loki: {
            datasource: 'loki',
            queries: [{ refId: 'A', expr: '{service_name=~"order-.*"}' }],
            range: { from: String(from), to: String(to) }
          }
        }));
      await page.goto(url, { waitUntil: 'networkidle', timeout: 30_000 });
      await page.waitForTimeout(3_000);
      await page.screenshot({ path: outPath, fullPage: true });
    } else if (capture.kind === 'terminal') {
      const stdout = execSync(capture.command, { cwd: REPO_ROOT, encoding: 'utf-8', maxBuffer: 50 * 1024 * 1024 });
      const html = `<!doctype html>...<body>${stdout.replace(/[<>&]/g, ...)}`;
      await page.setContent(html);
      await page.screenshot({ path: outPath, fullPage: true });
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
```

**Key replacements in the new function (D-W1):**
- Replace `await page.waitForTimeout(3_000)` with:
  ```javascript
  await page.waitForLoadState('networkidle', { timeout: 30_000 });
  await page.waitForSelector(capture.waitSelector, { state: 'visible', timeout: capture.waitTimeout ?? 15_000 });
  await page.waitForTimeout(500);  // settle delay
  ```
- Replace `panelSelector: '[data-panel-id]:has-text("RED Metrics")'` with `panelId: 3` and `waitSelector: '[data-panel-id="3"] canvas'`.
- Add skip-by-default at the top of the function:
  ```javascript
  if (!FORCE && existsSync(outPath)) {
    return { status: 'skipped' };
  }
  ```
- Return `{ status: 'captured' }` on success.

---

#### Chromium detection pattern (lines 186–192)

**Copy verbatim** with one addition (`--no-sandbox` args per P18-1):

```javascript
// From scripts/screenshots/capture.mjs lines 186-192
const executablePath =
  process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ||
  (existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' :
   existsSync('/usr/bin/chromium-browser') ? '/usr/bin/chromium-browser' :
   existsSync('/usr/bin/google-chrome') ? '/usr/bin/google-chrome' :
   undefined);

// v1.0 used: chromium.launch({ headless: true, executablePath });
// New script adds --no-sandbox for P18-1 (CI + Docker compatibility):
const browser = await chromium.launch({
  headless: true,
  executablePath,
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
});
```

---

#### `main()` function — tag-filter + orchestration (lines 166–205)

```javascript
async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true });
  console.log(`output dir: ${OUTPUT_DIR}`);

  const TAG_FILTER = process.env.CAPTURE_TAG_FILTER;
  const filtered = TAG_FILTER ? CAPTURES.filter(c => c.tag === TAG_FILTER) : CAPTURES;
  if (TAG_FILTER) {
    console.log(`CAPTURE_TAG_FILTER=${TAG_FILTER} — running ${filtered.length} of ${CAPTURES.length} captures`);
  }

  // ... chromium detection ...
  const browser = await chromium.launch({ headless: true, executablePath });
  try {
    for (const c of filtered) {
      await captureForTag(browser, c);
    }
  } finally {
    await browser.close();
  }
}

main().catch(err => { console.error(err); process.exit(1); });
```

**Extension for new script:** The `main()` function grows to:
1. Start `load.sh` background child (D-W2) before the capture loop
2. `await sleep(WARMUP_MS)` before v2.0 head captures
3. Route `headCapture: true` entries through a `captureHeadView()` path, tag-cycling entries through a `captureForTag()` path that manages git worktrees internally
4. Run tail-sampling OFF/ON pair via `withTailSamplingDisabled(async () => { ... })` wrapper for `tailSamplingToggle: 'OFF'` entries
5. Print summary table (D-W3) after the loop
6. Kill load process in `finally`

---

#### Error handling pattern (lines 152–160)

```javascript
// From captureForTag — optional-capture gate
} catch (err) {
  if (capture.optional) {
    console.warn(`optional capture failed (${capture.output}): ${err.message} — skipping per D-06`);
  } else {
    throw err;
  }
}
```

**Extension:** Return `{ status: 'failed', optional: capture.optional }` from catch block instead of rethrowing, to enable summary table (D-W3). Rethrow only if the summary table logic decides to exit non-zero after all captures complete.

---

### `scripts/package.json` (config)

**Primary analog:** `scripts/screenshots/package.json` (14 lines — read in full)

```json
{
  "name": "ose-otel-demo-screenshots",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "description": "Workshop screenshot capture pipeline (DOC-04 / Phase 7 D-05). Drives Grafana headlessly across the six step-NN-* git tags.",
  "scripts": {
    "capture": "node capture.mjs"
  },
  "dependencies": {
    "playwright": "1.49.1"
  }
}
```

**Changes for new file:**
- `name`: `"ose-otel-demo-capture"` (or omit — `private: true` makes it irrelevant)
- `scripts.capture`: `"node capture-screenshots.mjs"` (new filename)
- `playwright` version: `"1.59.1"` (bumped from 1.49.1 — current stable as of 2026-05-03)
- Keep `"type": "module"` and `"private": true` verbatim

---

### `mise.toml` — task changes (config)

**Primary analog for deletion target:** `[tasks."docs:screenshots"]` block, lines 163–261 of `mise.toml`

The entire 99-line bash block (git worktree cycle, PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install, per-tag loop) is **deleted**.

**Primary analog for new task shape:** `[tasks.load]` block (lines 153–156), which is a thin one-liner:

```toml
[tasks.load]
description = "Continuous POST /orders load (~1 req/sec, ~50/50 express/standard). Run alongside `mise run dev`. Ctrl-C stops both child loaders."
run = "./scripts/load.sh"
```

**New task to add (same minimal shape):**

```toml
[tasks.screenshots]
description = "Capture all workshop screenshots to docs/screenshots/. Set FORCE=1 to re-capture existing PNGs."
run = "node scripts/capture-screenshots.mjs"
```

The task's position in `mise.toml` should be in the same section as `load` and `demo:order` (workshop helpers block, after line 137). The `docs:screenshots` block at lines 163–261 is replaced by this 3-line block.

**Also verify:** `[tools]` block at line 7 already includes `node = "lts"` — the `screenshots` task inherits Node from mise without any change to `[tools]`.

---

### `README.md` — step-04 placeholder replacement (docs)

**Pattern analog:** Existing screenshot embeds at README.md lines 109, 149–150, 263, 340.

**Current step-04 state (README.md lines 222–224):**
```markdown
<!-- TODO(DOC-04 v1.x): docs/screenshots/step-04-metrics.png — Mimir RED metrics panel.
     so the screenshot embed can be re-introduced without re-touching the surrounding prose.
-->
```

**Pattern to copy from** (README.md line 109 — simplest existing embed):
```markdown
![Phase 1 baseline — empty Tempo trace search](docs/screenshots/step-01-empty-tempo.png)
```

**Pattern for step-04** (matches style of the two-column table at lines 149–150):
```markdown
![Step 4 — RED Metrics panel in Grafana (orders_created_total + http_server_request_duration)](docs/screenshots/step-04-metrics.png)
```

The TODO comment block (lines 222–224) is replaced by the `![alt](path)` embed. The surrounding prose ("Verification screenshot pending" at line 548) is also updated to remove the PREREQ-02 deferral note.

---

## Shared Patterns

### load.sh spawn lifecycle (D-W2)

**Source:** `scripts/screenshots/capture.mjs` lines 186–206 (analogous `execSync` pattern) + `scripts/load.sh` lines 66–100 (env var interface)

**Apply to:** `captureMain()` in `scripts/capture-screenshots.mjs`

The v1.0 bash driver spawned `scripts/load.sh` as a background process per tag (line 237 of `mise.toml` task). The new script spawns it once at startup using Node's `child_process.spawn`. Key env vars from `load.sh` that the new script passes:

```javascript
// scripts/load.sh supports: SLOW_RPS, BURST_RPS, IDEMPOTENT_RPS, TARGET, DURATION
// For tail-sampling lesson captures, SLOW_RPS=2 is essential (Phase 11 D-T7):
const loadProc = spawn(resolve(REPO_ROOT, 'scripts/load.sh'), [], {
  cwd: REPO_ROOT,
  env: { ...process.env, SLOW_RPS: '2' },  // ensures slow traces for tail-sampling lesson
  stdio: 'ignore',
  detached: false,
});
// Cleanup on all exit paths:
process.on('exit', () => { if (!loadProc.killed) loadProc.kill('SIGTERM'); });
process.on('SIGINT', () => { loadProc.kill('SIGTERM'); process.exit(1); });
process.on('SIGTERM', () => { loadProc.kill('SIGTERM'); process.exit(1); });
```

---

### git worktree lifecycle (v1.0 tag-cycling)

**Source:** `mise.toml` `[tasks."docs:screenshots"]` lines 163–261 (bash driver that the new script internalizes)

**Apply to:** `captureForTag()` in `scripts/capture-screenshots.mjs` for entries where `headCapture` is absent/false

Key pattern from the bash driver (lines 214–256):

```bash
# Bash driver pattern — internalized as JavaScript in the new script:
TAGS=(step-01-baseline step-02-traces step-03-context-propagation step-04-metrics step-05-logs step-06-tests)
for tag in "${TAGS[@]}"; do
  [ -d "$WORKTREE_DIR" ] && git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || true
  git worktree add "$WORKTREE_DIR" "$tag"
  pushd "$WORKTREE_DIR"
  mvn -T 1C -DskipTests -q clean install || exit 1
  mise run dev > "$REPO_ROOT/.screenshots-worktree-dev.log" 2>&1 &
  DEV_PID=$!
  sleep 25   # Spring Boot startup
  [ "$tag" != "step-01-baseline" ] && "$REPO_ROOT/scripts/load.sh" & LOAD_PID=$!
  sleep 30   # WARMUP_MS
  popd
  CAPTURE_TAG_FILTER="$tag" node "$REPO_ROOT/scripts/screenshots/capture.mjs"
  [ -n "${LOAD_PID:-}" ] && kill "$LOAD_PID" 2>/dev/null || true
  kill "$DEV_PID" 2>/dev/null || true
  git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || true
done
```

In JavaScript, `execSync` with `{ cwd, stdio: 'inherit' }` replaces the bash subshell pattern. The cleanup trap (`trap cleanup EXIT`) becomes `process.on('exit', cleanup)`.

---

### otelcol-config.yaml mutation (D-T1 sed pattern)

**Source:** `infra/observability/otelcol-config.yaml` lines 167 and 368 (verified structure)

**Apply to:** `disableTailSampling()` function in `scripts/capture-screenshots.mjs`

Verified targets:
- **Block start** (line 167): `  tail_sampling:` (2-space indent, starts the ~70-line processor definition block)
- **Block end marker** (line 268): `  transform/copy_recordpolicy:` (next 2-space-indented processor; use as stop marker for the regex)
- **Pipeline reference** (line 368): `      processors: [memory_limiter, tail_sampling, transform/copy_recordpolicy, batch]`

```javascript
// From RESEARCH.md Pattern 4 — verified against otelcol-config.yaml line 368
function disableTailSampling(configPath) {
  let yaml = readFileSync(configPath, 'utf-8');
  // 1. Comment out tail_sampling block (lines 167–267):
  yaml = yaml.replace(
    /^(  tail_sampling:[\s\S]*?)^(  transform\/copy_recordpolicy:)/m,
    (match, block, next) => {
      const commented = block.split('\n').map(l => l ? '# ' + l : l).join('\n');
      return commented + next;
    }
  );
  // 2. Remove tail_sampling from traces pipeline (line 368):
  yaml = yaml.replace(
    /processors: \[memory_limiter, tail_sampling, transform\/copy_recordpolicy, batch\]/,
    'processors: [memory_limiter, transform/copy_recordpolicy, batch]'
  );
  writeFileSync(configPath, yaml, 'utf-8');
}
```

---

### Grafana anonymous access (no auth pattern)

**Source:** `scripts/screenshots/capture.mjs` lines 110–115 (doc comment) + `docker-compose.yml` (env vars `GF_AUTH_ANONYMOUS_ENABLED=true`, `GF_AUTH_DISABLE_LOGIN_FORM=true`)

**Apply to:** All `page.goto()` calls in `scripts/capture-screenshots.mjs`

No login flow, no `page.fill('#user', ...)`, no session cookie management. Every `page.goto(grafanaUrl)` navigates directly to the target view. The new script inherits this behavior unchanged — no auth context changes needed.

---

### Dashboard URL construction (kiosk mode)

**Source:** `scripts/screenshots/capture.mjs` line 124

```javascript
// Copy verbatim for dashboard kind:
const url = `${GRAFANA_URL}/d/${DASHBOARD_UID}/?from=${from}&to=${to}&kiosk=tv`;
// For single-panel focus (v2.0 entries with panelId):
const url = `${GRAFANA_URL}/d/${DASHBOARD_UID}/?from=${from}&to=${to}&kiosk=tv&viewPanel=${capture.panelId}`;
```

`kiosk=tv` suppresses the Grafana nav bar and the refresh picker — eliminates the `networkidle` timeout pitfall (D-W1 / RESEARCH anti-patterns).

---

### Tempo Explore URL construction

**Source:** `scripts/screenshots/capture.mjs` lines 131–133

```javascript
// v1.0 base shape — copy and extend for v2.0 service-name filter:
const panesV1 = { tempo: { datasource: 'tempo', queries: [{ refId: 'A', queryType: 'traceqlSearch' }], range: { from: String(from), to: String(to) } } };

// v2.0 extension — add filters array for service name constraint (D-T3):
const panesV2 = {
  tempo: {
    datasource: 'tempo',
    queries: [{
      refId: 'A',
      queryType: 'traceqlSearch',
      filters: [{ id: 'service-name', tag: 'resource.service.name', operator: '=', value: ['order-producer'], type: 'tag' }],
    }],
    range: { from: String(from), to: String(to) },
  },
};
const url = `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}`;
```

Note: v1.0 used `{ tempo: { ... } }` as the key; this panes key name is arbitrary (it is the pane ID in Grafana's Explore split-view). Keep `tempo` as the key name for consistency with v1.0.

---

### Terminal capture (HTML render) pattern

**Source:** `scripts/screenshots/capture.mjs` lines 143–147

```javascript
// Copy verbatim for step-06-test-output.png (terminal kind):
const stdout = execSync(capture.command, { cwd: REPO_ROOT, encoding: 'utf-8', maxBuffer: 50 * 1024 * 1024 });
const html = `<!doctype html><meta charset="utf-8"><style>body{background:#1e1e1e;color:#d4d4d4;font-family:'Cascadia Code',Menlo,monospace;font-size:13px;line-height:1.4;padding:20px;white-space:pre-wrap;}</style><body>${stdout.replace(/[<>&]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;'}[c]))}`;
await page.setContent(html);
await page.screenshot({ path: outPath, fullPage: true });
// No waitSelector needed for terminal kind — page.setContent() is synchronous.
```

---

### Summary table (D-W3)

**No direct analog** in existing codebase. Pattern comes from RESEARCH.md Pattern section:

```javascript
// New in capture-screenshots.mjs — no existing analog:
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
```

---

### verify:* task name convention (mise.toml)

**Source:** `mise.toml` — `verify:bom`, `verify:datasources`, `verify:images`, `verify:tail-sampling`, `verify:exemplars` all follow `verb:noun` naming. The new `screenshots` task uses only the noun (no verb prefix) matching `load` and `dev` which are also noun-only. This matches D-S5's intent — `mise run screenshots` sits alongside `mise run load`, not alongside `mise run verify:*`.

---

## No Analog Found

| File/Function | Role | Data Flow | Reason |
|---|---|---|---|
| `withTailSamplingDisabled(fn)` wrapper | utility function | — | No existing pattern for OTel Collector config mutation + docker compose restart + health-check polling + async finally restore. RESEARCH.md Pattern 4 provides the full implementation template. |
| `printSummary(results)` | utility function | — | No existing summary/reporting pattern in the codebase. RESEARCH.md D-W3 provides the template. |
| `waitForCollectorHealthy(timeoutMs)` | utility function | — | No existing health-check polling pattern in the codebase. RESEARCH.md Pattern 4 provides the `fetch()` poll loop template. |

---

## Metadata

**Analog search scope:** `scripts/screenshots/`, `scripts/`, `mise.toml`, `README.md`
**Files read:** 6 (capture.mjs, package.json, mise.toml, load.sh, otelcol-config.yaml head, README.md head)
**Pattern extraction date:** 2026-05-03

**Panel IDs confirmed from `grafana/dashboards/ose-otel-demo.json`** (via RESEARCH.md — file read during research phase):
- `panelId: 3` = "RED Metrics" panel (timeseries, Mimir datasource) — used by `step-04-metrics.png`
- `panelId: 16` = "HTTP Request Duration (with Exemplars)" (histogram) — used by `step-12-exemplars.png`

**Datasource UIDs confirmed from `grafana/datasources.yaml`** (via RESEARCH.md):
- Tempo: `tempo`
- Prometheus/Mimir: `prometheus`
- Loki: `loki`

**OTel Collector health endpoint:** `:13133/` (from `docker-compose.yml` health_check + `verify:tail-sampling` task pattern in mise.toml lines 484–511)

**docker compose service name for restart:** `otel-collector` (service key in docker-compose.yml, not container_name `ose-otel-collector`)
