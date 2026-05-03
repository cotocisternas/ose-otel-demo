# Phase 18: Automated Screenshot Generation (Playwright) - Research

**Researched:** 2026-05-03
**Domain:** Playwright headless browser automation + Grafana Explore deep links + OTel Collector config mutation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-S1:** Replace entirely — delete `scripts/screenshots/capture.mjs`, `scripts/screenshots/package.json`, and the `scripts/screenshots/` directory. The new `scripts/capture-screenshots.mjs` subsumes all v1.0 AND v2.0 captures in a single CAPTURES array. One script, one source of truth.

**D-S2:** JavaScript (.mjs) — no TypeScript, no build step, no `tsx` dependency.

**D-S3:** Location: `scripts/capture-screenshots.mjs` — top-level in `scripts/`. Playwright dependency lives in `scripts/package.json`.

**D-S4:** All logic in the script — git worktree create/checkout/teardown for v1.0 tag captures AND current-HEAD captures for v2.0 are all self-contained in the script. `mise run screenshots` is a thin one-liner (`node scripts/capture-screenshots.mjs`).

**D-S5:** `mise run screenshots` replaces `mise run docs:screenshots` — old task deleted along with bash driver.

**D-T1:** Backup-restore — `cp otelcol-config.yaml otelcol-config.yaml.bak` → sed to comment out `tail_sampling` from the `processors:` definition block AND the `pipelines.traces.processors:` list → `docker compose restart otel-collector` → health-check poll on `:13133/` → 30s warm-up → capture OFF frame → `cp .bak` back → restart → health-check → 30s warm-up → capture ON frame. P18-3: `.bak` restore in unconditional `finally` block.

**D-T2:** 30s warm-up after each Collector restart — after the health-check passes, not after `docker compose restart` returns.

**D-T3:** Time-range isolation via `Last 5 min` in Grafana URL so the visible window shows only traces ingested during the 30s warm-up.

**D-I1:** All captures in one CAPTURES array; skip-by-default (file-exists check). `FORCE=1` re-captures everything.

**D-I2:** Phase 18 ships with entries only for screenshots that have README placeholders TODAY: step-01 through step-06 (v1.0), step-04-metrics (PREREQ-02), step-11-tail-sampling-OFF/ON, step-12-exemplars. Entries for step-14-jpa and step-16-head-sampling added when Phases 14 and 16 land.

**D-I3:** step-04-metrics.png at current HEAD — no tag checkout needed.

**D-W1:** Per-capture CSS selector + timeout — each CAPTURES entry defines `waitSelector` and `waitTimeout` (default 15s). Capture flow: `waitForLoadState('networkidle')` → `waitForSelector(waitSelector, { timeout: waitTimeout })` → 500ms settle delay → `page.screenshot()`.

**D-W2:** Script-managed load lifecycle — script starts `scripts/load.sh` as background child, waits `WARMUP_MS` (default 30s), begins captures, kills load on exit.

**D-W3:** Summary table to stdout — filename | status (captured/skipped/failed). Non-zero exit if any required capture failed.

### Claude's Discretion

- Exact CSS selectors for each capture's `waitSelector`
- Exact `sed` regex for commenting out the tail_sampling processor block and pipeline entry
- Playwright version to pin in `scripts/package.json`
- `WARMUP_MS` default value tuning
- Whether v1.0 tag-cycling captures need full per-tag dev stack or can share a single running stack
- Exact Grafana URL parameter construction for each view
- `scripts/package.json` scope

### Deferred Ideas (OUT OF SCOPE)

- Phases 13, 15, 17 screenshot entries — added by each phase
- Head-sampling ON/OFF toggle (Phase 16's screenshot is a single ~50% trace count, not a paired OFF/ON)
- Video recording or animated GIF capture
- CI/CD integration for screenshot freshness checks

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCAP-01 | `mise run screenshots` exits 0 producing/updating all PNGs; existing step-01 through step-10 not clobbered unless `FORCE=1` | File-exists check pattern + CAPTURES array + summary table |
| SCAP-02 | Tail-sampling OFF/ON pair captured by the script via sed comment-out, collector restart, health-check, warm-up, capture cycle | Exact sed regex verified; health endpoint `:13133/` confirmed; D-T1 backup-restore pattern |
| SCAP-03 | Each Playwright step includes explicit `waitForLoadState('networkidle')` + data-presence assertion before `page.screenshot()` | Playwright API verified; selector strategy per capture kind documented |

</phase_requirements>

---

## Summary

Phase 18 replaces the two-part v1.0 screenshot system (a bash driver in `mise.toml` plus `scripts/screenshots/capture.mjs`) with a single self-contained JavaScript ESM script at `scripts/capture-screenshots.mjs`. The script is the authoritative inventory of every screenshot in `docs/screenshots/`, handles its own git worktree lifecycle for v1.0 tag-cycling captures, manages the `scripts/load.sh` background process, and performs the tail-sampling OFF/ON toggle using a backup-restore pattern around `docker compose restart otel-collector`.

The new script is an evolution of the v1.0 `capture.mjs` — the CAPTURES array shape, Chromium detection block, `fixedTimeRange()` helper, and `captureForTag()` function are all carried forward with extensions. Key additions are: a `headCapture: true` flag distinguishing current-HEAD v2.0 captures from tag-cycling v1.0 captures; per-entry `waitSelector`/`waitTimeout` fields replacing the v1.0 fixed-delay approach; a skip-by-default file-exists check; a tail-sampling toggle sequence with unconditional `finally` restore; and a stdout summary table.

Research priorities that had open questions at context-gather time are now resolved: the exact sed pattern for commenting out the tail_sampling block in `otelcol-config.yaml` is verified against the actual file structure; stable Grafana DOM selectors are identified using Grafana's own data-testid strategy and the role/title approach where testids are absent; the Explore URL construction for Tempo traceqlSearch follows the v1.0 pattern confirmed to work against Grafana 13.0.1; and Playwright 1.59.1 is the current stable version.

**Primary recommendation:** Build the new script by direct extension of `scripts/screenshots/capture.mjs` — copy the skeleton, add the `headCapture` flag and `waitSelector` field, add the tail-sampling toggle sequence, and replace the per-capture fixed-delay with `waitForSelector`. Keep the existing `CAPTURES` v1.0 entries; extend with v2.0 entries.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Screenshot capture automation | Host (Node.js script) | — | The script runs on the developer's host, driving a headless Chromium via Playwright to navigate Grafana at `localhost:3000` |
| Grafana UI rendering | Browser / Container | — | Grafana at `localhost:3000` renders panels; Playwright captures what the browser sees |
| Load generation | Host (bash child process) | — | `scripts/load.sh` runs as a background child of the capture script, sending HTTP traffic to the host-resident producer service |
| OTel Collector config mutation | Host (script + docker CLI) | Container | The script reads/writes `infra/observability/otelcol-config.yaml` on the host, then calls `docker compose restart otel-collector` to reload the config |
| Collector health-check polling | Host (script + curl) | — | Script polls `http://localhost:13133/` using the Collector's `health_check` extension exposed on port 13133 |
| Git worktree management | Host (script + git CLI) | — | Script creates/removes worktrees for v1.0 tag-cycling captures |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| playwright | 1.59.1 [VERIFIED: npm registry] | Headless Chromium automation — navigate Grafana URLs, wait for DOM, take PNGs | Only Playwright choice for this script; v1.0 used 1.49.1; 1.59.1 is current stable at research date |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Node.js built-ins (fs, path, child_process) | bundled | File I/O, process spawning, shell execution | No external dep needed — ESM native APIs sufficient |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| playwright (chromium) | puppeteer | Playwright already used in v1.0 and is more actively maintained; puppeteer would be a breaking switch |
| system chromium detection | `npx playwright install chromium` | v1.0 R3 deviation showed `playwright install-deps` fails on Arch; system chromium fallback is the established pattern (P18-1) |

**Installation:**

```bash
# scripts/package.json
npm install playwright@1.59.1
# On machines without bundled chromium — Playwright's bundled fallback:
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install  # use system chromium (Arch Linux pattern)
# OR: npx playwright install chromium           # download bundled chromium (CI, macOS)
```

**Version verification:** Latest Playwright stable confirmed as 1.59.1 via `npm view playwright version` [VERIFIED: npm registry, 2026-05-03]. v1.0 used 1.49.1.

---

## Architecture Patterns

### System Architecture Diagram

```
scripts/capture-screenshots.mjs
        │
        ├── [startup] spawn scripts/load.sh ──────────────────► http://localhost:8080/orders
        │                                                              │
        │                                                              ▼
        │                                                   producer-service (host)
        │                                                              │ OTLP gRPC
        │                                                              ▼
        │                                                   otel-collector (container)
        │                                                              │ tail_sampling
        │                                                              ▼
        │                                                       Tempo / Mimir / Loki
        │                                                              │
        │                                                              ▼
        │                                              Grafana :3000 (container)
        │
        ├── [tail-sampling OFF/ON] ─────────────────────────────────────────────┐
        │    cp otelcol-config.yaml → .bak                                       │
        │    sed: comment out tail_sampling block + pipeline entry               │
        │    docker compose restart otel-collector                               │
        │    poll curl localhost:13133/ (30s timeout)                            │
        │    sleep WARMUP_MS=30000                                               │
        │    [capture OFF frame]                                                 │
        │    cp .bak → otelcol-config.yaml (finally)                            │
        │    docker compose restart otel-collector                               │
        │    poll curl localhost:13133/ (30s timeout)                            │
        │    sleep WARMUP_MS=30000                                               │
        │    [capture ON frame]                                                  │
        │                                                                        │
        ├── [v1.0 tag captures] ──────────────────────────────────────────────┐ │
        │    for tag in step-01..step-06:                                      │ │
        │      git worktree add .screenshots-worktree <tag>                   │ │
        │      mvn clean install -DskipTests (in worktree)                     │ │
        │      mise run dev (background, worktree)                             │ │
        │      sleep 25s (Spring Boot startup)                                 │ │
        │      scripts/load.sh (background, if tag != step-01)                │ │
        │      sleep WARMUP_MS                                                 │ │
        │      [Playwright capture]                                            │ │
        │      kill dev + load                                                 │ │
        │      git worktree remove                                             │ │
        │                                                                      │ │
        └── [v2.0 current-HEAD captures] (headCapture: true)                  │ │
             waitForLoadState('networkidle')                                   │ │
             waitForSelector(waitSelector, {timeout})   ◄──────────────────────┘ │
             page.screenshot({path})                    ◄────────────────────────┘
             → docs/screenshots/*.png
```

### Recommended Project Structure

```
scripts/
├── capture-screenshots.mjs    # new unified capture script (replaces scripts/screenshots/)
├── package.json               # Playwright dependency for the new script
└── load.sh                    # unchanged; spawned as background child by capture script

docs/
└── screenshots/
    ├── step-01-empty-tempo.png        (existing — v1.0 skip-by-default)
    ├── step-02-disconnected-traces.png (existing)
    ├── step-03-joined-trace.png        (existing)
    ├── step-03-waterfall.png           (existing)
    ├── step-04-metrics.png             (NEW — PREREQ-02 closure)
    ├── step-05-logs-trace-jump.png     (existing)
    ├── step-06-test-output.png         (existing)
    ├── step-11-tail-sampling-OFF.png   (NEW)
    ├── step-11-tail-sampling-ON.png    (NEW)
    └── step-12-exemplars.png           (NEW)

infra/observability/
└── otelcol-config.yaml        (read/written by script at runtime for tail-sampling toggle)
```

### Pattern 1: CAPTURES Array with headCapture Flag

The v1.0 CAPTURES array shape is extended with two new fields: `headCapture` (boolean, true = current-HEAD capture, false/absent = tag-cycling) and `waitSelector`/`waitTimeout` replacing the v1.0 fixed-delay approach.

```javascript
// Source: evolution of scripts/screenshots/capture.mjs CAPTURES array
const CAPTURES = [
  // v1.0 tag-cycling (headCapture absent or false)
  {
    tag: 'step-01-baseline',
    output: 'step-01-empty-tempo.png',
    kind: 'tempo',
    waitSelector: '[data-testid="Explore"]',    // Explore container present = page loaded
    waitTimeout: 15_000,
  },
  {
    tag: 'step-03-context-propagation',
    output: 'step-03-joined-trace.png',
    kind: 'tempo',
    waitSelector: 'table tbody tr',             // at least one trace result row
    waitTimeout: 15_000,
  },

  // v2.0 current-HEAD (headCapture: true — no tag checkout)
  {
    headCapture: true,
    output: 'step-04-metrics.png',
    kind: 'dashboard',
    panelId: 3,                                 // id=3 = "RED Metrics" panel
    waitSelector: '[data-panel-id="3"] canvas', // canvas rendered by Grafana timeseries
    waitTimeout: 15_000,
  },
  {
    headCapture: true,
    output: 'step-11-tail-sampling-OFF.png',
    kind: 'tempo',
    waitSelector: 'table tbody tr',             // trace results table has at least one row
    waitTimeout: 15_000,
    tailSamplingToggle: 'OFF',                  // signals the toggle sequence
  },
  {
    headCapture: true,
    output: 'step-11-tail-sampling-ON.png',
    kind: 'tempo',
    waitSelector: 'table tbody tr',
    waitTimeout: 15_000,
    tailSamplingToggle: 'ON',
  },
  {
    headCapture: true,
    output: 'step-12-exemplars.png',
    kind: 'dashboard',
    panelId: 16,                                // id=16 = "HTTP Request Duration (with Exemplars)"
    waitSelector: '[data-panel-id="16"] canvas',
    waitTimeout: 20_000,                        // exemplar dots may take longer to appear
  },
];
```

### Pattern 2: Chromium Detection (Reuse Verbatim)

```javascript
// Source: scripts/screenshots/capture.mjs lines 186-192
// P18-1 compliance: --no-sandbox via args; headless: true always
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
```

### Pattern 3: Capture Flow with waitForSelector

```javascript
// Source: Playwright docs + project pattern (D-W1)
async function captureView(page, capture) {
  const outPath = resolve(OUTPUT_DIR, capture.output);
  // Skip if already present (D-I1 skip-by-default)
  if (!process.env.FORCE && existsSync(outPath)) {
    return { status: 'skipped' };
  }
  await page.waitForLoadState('networkidle', { timeout: 30_000 });
  await page.waitForSelector(capture.waitSelector, {
    state: 'visible',
    timeout: capture.waitTimeout ?? 15_000,
  });
  await page.waitForTimeout(500);   // 500ms settle (D-W1)
  await page.screenshot({ path: outPath, fullPage: false });
  return { status: 'captured' };
}
```

### Pattern 4: Tail-Sampling Toggle (D-T1 / P18-3)

```javascript
// Source: D-T1 decision; sed pattern derived from otelcol-config.yaml inspection
const OTELCOL_CONFIG = resolve(REPO_ROOT, 'infra/observability/otelcol-config.yaml');
const OTELCOL_CONFIG_BAK = OTELCOL_CONFIG + '.bak';

async function withTailSamplingDisabled(fn) {
  // Backup
  copyFileSync(OTELCOL_CONFIG, OTELCOL_CONFIG_BAK);
  try {
    // Disable: comment out processor definition + pipeline reference (see sed patterns below)
    disableTailSampling(OTELCOL_CONFIG);
    await restartCollector();
    await waitForCollectorHealthy();
    await sleep(WARMUP_MS);
    await fn();
  } finally {
    // P18-3: unconditional restore
    copyFileSync(OTELCOL_CONFIG_BAK, OTELCOL_CONFIG);
    await restartCollector();
    await waitForCollectorHealthy();
  }
}

async function waitForCollectorHealthy(timeoutMs = 30_000) {
  // P18-2: poll :13133/ until {"status":"Server available"} or timeout
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const resp = await fetch('http://localhost:13133/');
      if (resp.ok) return;
    } catch { /* not ready yet */ }
    await sleep(1_000);
  }
  throw new Error('otel-collector health check timed out after 30s');
}
```

### Pattern 5: load.sh Lifecycle (D-W2)

```javascript
// Source: capture.mjs v1.0 pattern + load.sh env vars inspection
import { spawn } from 'node:child_process';

let loadProc = null;

async function startLoad() {
  loadProc = spawn(resolve(REPO_ROOT, 'scripts/load.sh'), [], {
    cwd: REPO_ROOT,
    env: { ...process.env, SLOW_RPS: '2' },  // maintain slow stream for tail-sampling lesson
    stdio: 'ignore',
    detached: false,
  });
}

function stopLoad() {
  if (loadProc && !loadProc.killed) {
    loadProc.kill('SIGTERM');
  }
}
// Register cleanup in process exit handlers
process.on('exit', stopLoad);
process.on('SIGINT', () => { stopLoad(); process.exit(1); });
process.on('SIGTERM', () => { stopLoad(); process.exit(1); });
```

### Anti-Patterns to Avoid

- **Fixed delays as sole wait strategy:** `await page.waitForTimeout(3000)` alone guarantees neither data presence nor absence of spinners. Replace with `waitForLoadState('networkidle')` + `waitForSelector()` per D-W1. The 500ms settle delay is in addition, not a replacement.
- **Hardcoded class name selectors:** Grafana CSS class names (e.g., `.css-1fpqoic`) change with every Grafana release. Use `[data-panel-id="N"]`, `table tbody tr`, `[data-testid="Explore"]`, or role/title selectors.
- **Omitting `--no-sandbox` on Linux:** Playwright Chromium requires `--no-sandbox` and `--disable-setuid-sandbox` args when running as non-root on Linux hosts (Docker, CI, Arch). Omitting causes Chromium to refuse to launch.
- **`finally` block with async operations that can throw:** The `finally` block that restores `otelcol-config.yaml` must not itself be allowed to throw an unhandled exception, or the original error is lost. Wrap the restore operations in their own try/catch and log restore failures separately.
- **`headless: false` in committed code:** P18-1 requires `headless: true` unconditionally. Never commit a script with `headless: false`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Browser automation / screenshot | Custom HTTP screenshot service | playwright (chromium) | Playwright handles cross-platform Chromium binary detection, async page lifecycle, and networkidle state tracking |
| Chromium binary management | Manual download/path logic | Playwright's bundled chromium OR system binary detection (v1.0 pattern) | Cross-platform path detection already works in capture.mjs; extend verbatim |
| Health-check polling | Exponential backoff library | Simple `while Date.now() < deadline` poll loop with 1s sleep | 30s timeout, 1s poll — trivial implementation; no dependency needed |
| Process management | PM2 or similar | Node.js `child_process.spawn` + SIGTERM cleanup | load.sh is a simple foreground process; spawn + kill is sufficient |

---

## Sed Patterns for Tail-Sampling Toggle (D-T1)

This section resolves the "Claude's Discretion" item for sed regex, verified against the actual `infra/observability/otelcol-config.yaml` file [VERIFIED: file read 2026-05-03].

### What must be commented out

Two locations in `otelcol-config.yaml` must be commented out to disable tail_sampling:

**Location 1: The `tail_sampling:` processor definition block (lines 167–234 in the current file)**

The block starts with exactly:
```yaml
  tail_sampling:
    decision_wait: 10s
```

**Location 2: The pipeline reference in `pipelines.traces.processors:`**

Current line:
```yaml
      processors: [memory_limiter, tail_sampling, transform/copy_recordpolicy, batch]
```

Must become:
```yaml
      processors: [memory_limiter, transform/copy_recordpolicy, batch]
```

### Sed strategy (JavaScript `fs.readFileSync` + replace)

Because the `tail_sampling:` block spans ~70 lines and sed multi-line matching is fragile across shells, the recommended approach is to read the YAML as a string in the script and apply two string replacements using regex:

```javascript
// Source: derived from otelcol-config.yaml structure inspection [VERIFIED: 2026-05-03]
import { readFileSync, writeFileSync } from 'node:fs';

function disableTailSampling(configPath) {
  let yaml = readFileSync(configPath, 'utf-8');

  // 1. Comment out the tail_sampling processor block in processors: section.
  // The block runs from "  tail_sampling:" through the end of the composite policy
  // definition. We use a marker: from "  tail_sampling:" up to the NEXT processor
  // that starts at the same 2-space indentation (transform/copy_recordpolicy:).
  // Approach: comment every line between "  tail_sampling:" and the next "  \w" line.
  yaml = yaml.replace(
    /^(  tail_sampling:[\s\S]*?)^(  transform\/copy_recordpolicy:)/m,
    (match, block, next) => {
      const commented = block.split('\n').map(l => l ? '# ' + l : l).join('\n');
      return commented + next;
    }
  );

  // 2. Remove tail_sampling from the traces pipeline processors list.
  yaml = yaml.replace(
    /processors: \[memory_limiter, tail_sampling, transform\/copy_recordpolicy, batch\]/,
    'processors: [memory_limiter, transform/copy_recordpolicy, batch]'
  );

  writeFileSync(configPath, yaml, 'utf-8');
}
```

**Restore is trivially `copyFileSync(OTELCOL_CONFIG_BAK, OTELCOL_CONFIG)` — no sed needed.**

### Why not sed CLI

Using `sed -i` with multi-line patterns is unreliable across GNU sed (Linux) and BSD sed (macOS). The project runs on Linux (`platform: linux` per env) so GNU sed would work, but the JavaScript string approach is self-contained in the script and matches the "all logic in the script" constraint (D-S4) better.

---

## Grafana URL Construction (Per Capture Kind)

Datasource UIDs verified from `grafana/datasources.yaml` [VERIFIED: file read 2026-05-03]:
- Tempo: `tempo`
- Prometheus/Mimir: `prometheus`
- Loki: `loki`

### Kind: `tempo` (Tempo Explore — traceqlSearch)

```javascript
// Source: extension of capture.mjs v1.0 URL pattern; Explore panes schema from Grafana docs
// Service name filter for v2.0 captures (order-producer):
function buildTempoSearchUrl({ from, to, serviceName }) {
  const panes = {
    A: {
      datasource: 'tempo',
      queries: [{
        refId: 'A',
        queryType: 'traceqlSearch',
        // traceqlSearch filters array for service name constraint:
        filters: serviceName ? [{
          id: 'service-name',
          tag: 'resource.service.name',
          operator: '=',
          value: [serviceName],
          type: 'tag',
        }] : [],
      }],
      range: { from: String(from), to: String(to) },
    },
  };
  return `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}`;
}
// For v1.0 captures (no service filter): { from, to } — matches capture.mjs v1.0 shape.
// For v2.0 OFF/ON captures: serviceName = 'order-producer' per success criteria SC#2.
```

**Note:** The v1.0 `capture.mjs` Tempo URL used `datasource: 'tempo'` (a string UID) which worked against Grafana 13.0.1 in production. This approach is confirmed to work [ASSUMED: v1.0 script ran against Grafana 13.0.1 via otel-lgtm 0.26.0 bundled Grafana].

### Kind: `dashboard` (Dashboard panel view)

```javascript
// Source: capture.mjs v1.0 dashboard URL pattern
// Panel clip: use ?viewPanel=<panelId> to focus a single panel.
function buildDashboardPanelUrl({ from, to, panelId }) {
  return `${GRAFANA_URL}/d/${DASHBOARD_UID}/?from=${from}&to=${to}&kiosk=tv&viewPanel=${panelId}`;
}
```

Panel IDs confirmed from `grafana/dashboards/ose-otel-demo.json` [VERIFIED: file read 2026-05-03]:
- id=3: "RED Metrics" (used for step-04-metrics.png — timeseries, Mimir datasource)
- id=16: "HTTP Request Duration (with Exemplars)" (used for step-12-exemplars.png — exemplar dots on histogram)

### Kind: `loki` (Loki Explore)

```javascript
// Source: capture.mjs v1.0 Loki URL pattern
function buildLokiUrl({ from, to }) {
  const panes = {
    A: {
      datasource: 'loki',
      queries: [{
        refId: 'A',
        queryType: 'range',
        expr: '{service_name=~"order-.*"}',
      }],
      range: { from: String(from), to: String(to) },
    },
  };
  return `${GRAFANA_URL}/explore?orgId=1&schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}`;
}
```

---

## DOM Selectors for waitSelector (Per Capture Kind)

Grafana 13.0.1 uses a mix of `data-testid` attributes (preferred, stable across releases) and structural HTML selectors. The v1.0 script used `[data-panel-id]` which is confirmed to exist in Grafana 13 (visible in the v1.0 capture flow). [ASSUMED] for some selectors below where the stack is not running at research time; the planning section on Wave 0 verification must confirm these before coding.

| Capture | Selector | Confidence | Rationale |
|---------|----------|------------|-----------|
| step-01-empty-tempo (no traces) | `[data-testid="Explore"]` | MEDIUM | Explore container present = page loaded; empty results have no table rows — this selector confirms page is ready even with zero results |
| step-02/03 (traces exist) | `table tbody tr` | MEDIUM | Tempo search results render as an HTML table; at least one row confirms data loaded; generic but present in Grafana 10–13 Explore trace tables |
| step-04-metrics (dashboard panel) | `[data-panel-id="3"] canvas` | HIGH | Grafana timeseries panel renders a `canvas` element; `data-panel-id` is a load-bearing attribute confirmed in capture.mjs v1.0 |
| step-05-logs (Loki) | `[data-testid="Explore"] [class*="logs-row"]` OR `table tbody tr` | LOW | Loki results in Explore render as log rows; class names are fragile; alternative: `[data-testid="Explore"] [aria-label*="Log"]`; recommend using structural `div[class*="log-row"]` as fallback |
| step-06-test-output (terminal capture) | n/a | n/a | Terminal capture uses `page.setContent(html)` — no Grafana DOM; no waitSelector needed |
| step-11-OFF/ON (Tempo search, Last 5 min) | `table tbody tr` | MEDIUM | Same as step-02/03; expects at least one trace row within 15s of warm-up completing |
| step-12-exemplars (histogram panel) | `[data-panel-id="16"] canvas` | HIGH | Same canvas selector pattern as panel id=3; exemplar dots are rendered inside the canvas element — the selector confirms panel rendered, not specifically that exemplar dots are visible |

**Fallback strategy for LOW-confidence selectors:** If `waitForSelector` fails on first try, the script should log the failure with context and let it propagate (capture fails, summary table marks it failed). The planner should include a Wave 0 task to verify all selectors against a live Grafana instance before committing the script.

---

## v1.0 Tag-Cycling: Full Stack Per Tag vs Shared Stack

**Decision (Claude's Discretion):** Each v1.0 tag capture needs its OWN running Spring Boot apps because the tags have different code states — `step-01-baseline` has zero OTel libs, `step-03-context-propagation` has context propagation, etc. The dashboard panels and Tempo data reflect what the apps at that tag produced. The same infra containers (RabbitMQ, OTel stack, Grafana) can serve all tags unchanged — only the apps need per-tag restarts.

This matches the v1.0 bash driver pattern exactly:
1. `docker compose up` once (infra stable across all tags)
2. Per tag: `git worktree add` → build → `mise run dev` (background) → 25s startup wait → `load.sh` → 30s warm-up → capture → kill apps → `git worktree remove`

The new script internalizes this loop (D-S4), but the pattern is unchanged from the bash driver.

---

## Common Pitfalls

### Pitfall P18-1: Chromium `--no-sandbox` omission
**What goes wrong:** Playwright Chromium refuses to launch on Linux hosts running as non-root, producing `Running as root without --no-sandbox is not supported` or similar error.
**Why it happens:** Chromium's sandbox requires kernel namespaces; many CI and container environments restrict them.
**How to avoid:** Always pass `args: ['--no-sandbox', '--disable-setuid-sandbox']` to `chromium.launch()`. The v1.0 script did NOT include these args because it ran as non-root on the developer's Arch machine where the system Chromium is already configured. The new script must add them for CI and Docker compatibility.
**Warning signs:** `Error: Failed to launch the browser process` on CI.

### Pitfall P18-2: Capturing during Collector decision_wait window
**What goes wrong:** Collector restarts and begins ingesting new spans, but `decision_wait: 10s` means no traces are released to Tempo for the first 10 seconds. If the warm-up is too short, the Tempo Search panel shows zero results.
**Why it happens:** tail_sampling buffers entire traces for `decision_wait` before evaluating policies. The buffer is empty immediately after restart.
**How to avoid:** Wait until `curl localhost:13133/` returns 200 (Collector accepting spans), THEN wait `WARMUP_MS` (default 30s = 10s decision_wait + 20s buffer for visible volume). Do NOT start the clock when `docker compose restart` returns — the Collector may still be initializing.
**Warning signs:** OFF-frame PNG shows zero trace rows; ON-frame PNG shows very few rows.

### Pitfall P18-3: otelcol-config.yaml left disabled after interrupted run
**What goes wrong:** The capture script is interrupted mid-toggle (Ctrl-C, crash), leaving tail_sampling commented out. The workshop stack now accepts and exports 100% of traces without the sampling lesson — attendees see incorrect behavior.
**Why it happens:** Any throw between disabling and restoring tail_sampling bypasses the restore logic if it's not in a `finally` block.
**How to avoid:** The restore `copyFileSync(BAK, CONFIG)` + `docker compose restart otel-collector` sequence must be in a `finally` block — one that executes on normal completion, thrown errors, and Node.js `process.exit`. Use `process.on('exit', ...)` for synchronous cleanup (the `finally` approach handles async throws; `exit` handler handles `process.exit(1)` calls).
**Warning signs:** Grafana shows 100% of traces in Tempo after the script exits.

### Pitfall: `networkidle` timeout on Grafana Explore
**What goes wrong:** `waitForLoadState('networkidle')` times out with 30s timeout because Grafana Explore's live-refresh makes periodic background XHR requests that prevent the network from going idle.
**Why it happens:** Grafana's auto-refresh (default 30s) and panel subscriptions keep background network activity alive.
**How to avoid:** Navigate to Grafana URLs with `&refresh=false` or disable refresh by appending `&kiosk=tv` (kiosk mode suppresses the refresh picker). Alternatively, use a `goto` timeout longer than the auto-refresh interval, or accept `domcontentloaded` + `waitForSelector` as the combined signal instead of relying on `networkidle` alone.
**Warning signs:** Intermittent timeout failures on dashboard kind captures.

### Pitfall: Panel canvas not rendered (dashboard blank)
**What goes wrong:** `waitForSelector('[data-panel-id="3"] canvas')` succeeds but the canvas is blank (spinner replaced by empty canvas before data arrives).
**Why it happens:** Grafana renders the canvas element before the Prometheus query returns data. `networkidle` may fire before the panel query completes.
**How to avoid:** Add the 500ms `waitForTimeout` settle after `waitForSelector`. For panels with known slow queries (exemplars), increase `waitTimeout` to 20s.

---

## Code Examples

### CAPTURES Array Shape (Complete v2.0 Entry)

```javascript
// Source: extension of scripts/screenshots/capture.mjs [VERIFIED: file read 2026-05-03]
{
  headCapture: true,           // true = current HEAD; absent/false = tag-cycling
  output: 'step-12-exemplars.png',
  kind: 'dashboard',
  panelId: 16,                 // id=16 in ose-otel-demo.json: "HTTP Request Duration (with Exemplars)"
  waitSelector: '[data-panel-id="16"] canvas',
  waitTimeout: 20_000,         // exemplar dots take longer to appear than simple timeseries
  optional: false,             // required capture — failure exits non-zero (D-W3)
  notes: 'Exemplar dots on http_server_request_duration histogram panel — requires load running for >30s',
},
```

### Summary Table to Stdout (D-W3)

```javascript
// Source: D-W3 decision
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

### Collector Restart via docker compose

```javascript
// Source: D-T1 / P18-2; docker-compose.yml container name: ose-otel-collector [VERIFIED: 2026-05-03]
import { execSync } from 'node:child_process';

function restartCollector() {
  execSync('docker compose restart otel-collector', {
    cwd: REPO_ROOT,
    stdio: 'inherit',
  });
}
// Note: docker-compose.yml service name is 'otel-collector' (not 'ose-otel-collector' which is container_name)
// docker compose restart uses SERVICE name, not container_name.
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| v1.0: bash driver (`docs:screenshots` task) + separate `capture.mjs` | Single `.mjs` script with all logic (D-S1/D-S4) | Phase 18 | One file to maintain; mise task is a one-liner |
| v1.0: fixed `waitForTimeout(3000)` delays | Per-entry `waitForSelector` + 500ms settle (D-W1) | Phase 18 | Faster captures, no blank frames |
| v1.0: no tail-sampling toggle | Backup-restore cycle with `finally` (D-T1/P18-3) | Phase 18 | Reproducible OFF/ON pair; stack never left broken |
| playwright 1.49.1 | playwright 1.59.1 [VERIFIED: npm] | Package bump | 10 minor versions of bug fixes and API stability |

**Deprecated/outdated:**
- `scripts/screenshots/` directory: deleted entirely in this phase (D-S1)
- `mise.toml` `[tasks."docs:screenshots"]` block: deleted and replaced with `[tasks.screenshots]` one-liner (D-S5)

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | v1.0 Tempo Explore URL (`datasource: 'tempo'` string in panes JSON) works against Grafana 13.0.1 standalone | Grafana URL Construction | Tempo URL produces 404 or empty results; planner must add Wave 0 validation step |
| A2 | `table tbody tr` selector is present in Grafana 13 Explore trace search results | DOM Selectors | waitSelector times out; planner must include a Wave 0 selector verification task |
| A3 | `step-01-baseline` tag capture (zero traces) correctly uses `[data-testid="Explore"]` as the wait signal | DOM Selectors | Selector not found in Grafana 13 Explore; fallback: `main[class*="explore"]` or just a long timeout with no selector |
| A4 | `?viewPanel=<panelId>` query parameter works in Grafana 13 to focus a single panel | Dashboard URL Construction | Dashboard renders full page instead of focused panel; screenshot captures wrong area |
| A5 | `docker compose restart otel-collector` uses the service name (not container name) | Collector Restart | Command fails with "no such service"; fix: use `docker compose restart` on the compose service key |
| A6 | v1.0 tag captures need per-tag Spring Boot apps (not just infra restart) | v1.0 Tag-Cycling | If tags share a running app, panel data may reflect wrong-tag telemetry |
| A7 | The `filters` array in the traceqlSearch query is the correct Grafana 13 Tempo URL schema for service name filtering | Grafana URL Construction | Service filter silently ignored; OFF/ON screenshots show all services, not just order-producer |

**Critical assumptions requiring Wave 0 verification:** A1, A2, A4 — all URL/selector related. The plan MUST include a Wave 0 task that runs the script in dry-run or interactive mode against a live stack to confirm these before the full capture run.

---

## Open Questions

1. **`viewPanel` URL parameter in Grafana 13**
   - What we know: Grafana supports `?viewPanel=N` to expand a single panel in recent versions
   - What's unclear: Whether this works in `kiosk=tv` mode simultaneously
   - Recommendation: Add a Wave 0 task that navigates to `http://localhost:3000/d/ose-otel-demo/?viewPanel=3&kiosk=tv` and confirms the RED Metrics panel is visible; if not, fall back to full-dashboard screenshot with panel-specific crop or `page.locator('[data-panel-id="3"]').screenshot()`

2. **Exemplar dots selector — confirming data presence vs panel render**
   - What we know: Panel id=16 renders a canvas element; `data-panel-id="16"` is the container
   - What's unclear: Whether the canvas appears before exemplar data is loaded (giving a false-positive on the waitSelector)
   - Recommendation: Use `waitSelector: '[data-panel-id="16"] canvas'` as the primary, plus a longer settle delay (1000ms instead of 500ms) for the exemplars panel specifically; note that the screenshot may capture the panel without exemplar dots if load hasn't run long enough

3. **`traceqlSearch` filters schema for Tempo in Grafana 13**
   - What we know: The Explore URL panes JSON accepts a `filters` array for traceqlSearch based on Grafana Tempo docs
   - What's unclear: Whether `type: 'tag'`, `tag: 'resource.service.name'` is the correct shape for Grafana 13.0.1's Tempo datasource plugin
   - Recommendation: Wave 0 task — open Grafana Explore, set service name filter to `order-producer` via UI, copy the resulting URL, extract the panes JSON, and confirm the filter shape; use that shape verbatim in the CAPTURES array

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js (lts) | scripts/capture-screenshots.mjs | ✓ | managed by mise | — |
| npm | scripts/package.json install | ✓ | bundled with Node lts | — |
| docker (compose) | `docker compose restart otel-collector` | ✓ (per preflight task) | Docker Compose v2 | — |
| system chromium | headless browser | ✓ (Arch Linux: `/usr/bin/chromium`) | detected at runtime | Playwright bundled chromium via `npx playwright install chromium` |
| git | worktree management for v1.0 tags | ✓ | git is a project prerequisite | — |
| curl | health-check polling (localhost:13133) | ✓ (Linux default) | — | Node.js `fetch()` (used instead — no curl dep in script) |
| infra containers running | all captures | must be started by user pre-run | N/A | script should emit `mise run infra:up` instruction if Grafana not reachable |
| apps running (v2.0 captures) | headCapture: true entries | must be started by user | N/A | script should check `localhost:8080/actuator/health` before v2.0 captures |

**Missing dependencies with no fallback:** None — all dependencies are present or have confirmed fallbacks.

**Implicit assumption:** The operator runs `mise run infra:up` and `mise run dev` before executing `mise run screenshots`. The script should validate Grafana reachability (`curl localhost:3000/api/health`) and emit a clear error if it cannot reach Grafana, rather than silently timing out in `page.goto()`.

---

## Security Domain

> `security_enforcement: true`, ASVS level 1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Grafana anonymous access enabled — no credentials in script |
| V3 Session Management | No | No session — each capture is a fresh browser context |
| V4 Access Control | No | Script is a local dev tool, not an API endpoint |
| V5 Input Validation | Partial | Environment variables (`WARMUP_MS`, `FORCE`, `GRAFANA_URL`) are strings — validate/sanitize before use in exec calls |
| V6 Cryptography | No | No cryptographic operations |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Shell injection via env vars passed to `execSync` | Tampering | Do not interpolate env vars directly into shell strings; pass as `env` object to `spawn`/`execSync` options; use `execFileSync` over `execSync` where possible |
| Backup file left on disk | Information Disclosure | `otelcol-config.yaml.bak` contains the full Collector config including any secrets; delete `.bak` in the `finally` block after restore |
| Script left with tail_sampling disabled | Tampering | P18-3 `finally` block + `process.on('exit')` handler; covered by design |

---

## Sources

### Primary (HIGH confidence)
- `scripts/screenshots/capture.mjs` — v1.0 Playwright capture script; CAPTURES array format, Chromium detection, fixedTimeRange, captureForTag structure [VERIFIED: file read 2026-05-03]
- `scripts/screenshots/package.json` — v1.0 Playwright dep 1.49.1 [VERIFIED: file read 2026-05-03]
- `infra/observability/otelcol-config.yaml` — tail_sampling processor block and pipeline entry verified for sed pattern design [VERIFIED: file read 2026-05-03]
- `grafana/datasources.yaml` — Tempo UID = `tempo`, Prometheus/Mimir UID = `prometheus`, Loki UID = `loki` [VERIFIED: file read 2026-05-03]
- `grafana/dashboards/ose-otel-demo.json` — Panel id=3 "RED Metrics", id=16 "HTTP Request Duration (with Exemplars)" [VERIFIED: file read 2026-05-03]
- `docker-compose.yml` — otel-collector service name = `otel-collector`, container_name = `ose-otel-collector`, health_check port = 13133 [VERIFIED: file read 2026-05-03]
- `scripts/load.sh` — env vars (SLOW_RPS, BURST_RPS, WARMUP_MS pattern), spawn behavior [VERIFIED: file read 2026-05-03]
- `mise.toml` — existing `docs:screenshots` task structure (lines 163-261); `screenshots` task will replace it [VERIFIED: file read 2026-05-03]
- npm registry — `npm view playwright version` → 1.59.1 [VERIFIED: 2026-05-03]
- Playwright docs (`/microsoft/playwright`) — `waitForLoadState('networkidle')`, `waitForSelector(selector, {state, timeout})`, `page.screenshot({path})`, `chromium.launch({headless, executablePath, args})` [CITED: Context7 /microsoft/playwright docs, 2026-05-03]

### Secondary (MEDIUM confidence)
- Grafana e2e-selectors — `data-testid="Explore"` confirmed as Explore container selector from `@grafana/e2e-selectors` package [CITED: https://raw.githubusercontent.com/grafana/grafana/main/packages/grafana-e2e-selectors/src/selectors/pages.ts]
- Grafana Explore URL schema — panes JSON with `datasource`, `queries`, `range` fields [CITED: Grafana Explore documentation + existing capture.mjs v1.0 production usage]
- Grafana Tempo traceqlSearch `filters` array — Service Name filter via `resource.service.name` tag [CITED: https://grafana.com/docs/grafana/latest/datasources/tempo/query-editor/traceql-search/]

### Tertiary (LOW confidence)
- `table tbody tr` as Tempo search results selector — inferred from Grafana UI structure, not directly verified against Grafana 13.0.1 DOM [ASSUMED]
- `?viewPanel=N` URL parameter for single-panel focus [ASSUMED — standard Grafana behavior, not verified against 13.0.1 with kiosk mode]

---

## Metadata

**Confidence breakdown:**
- Script architecture: HIGH — v1.0 code is readable and the extension points are clear
- Sed pattern / YAML mutation: HIGH — otelcol-config.yaml structure verified; string replace approach is safe
- Playwright API: HIGH — Context7 docs verified; same API shape as v1.0 usage
- DOM selectors: MEDIUM — `data-panel-id` and `data-testid="Explore"` are confirmed; `table tbody tr` is inferred
- Grafana URL construction: MEDIUM — v1.0 URL pattern worked against Grafana 13; traceqlSearch filters schema is LOW-verified

**Research date:** 2026-05-03
**Valid until:** 2026-06-03 (Playwright updates frequently; verify version before planning if > 2 weeks pass)
