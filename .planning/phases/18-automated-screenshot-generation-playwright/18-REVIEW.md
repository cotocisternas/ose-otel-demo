---
phase: 18-automated-screenshot-generation-playwright
reviewed: 2026-05-03T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - scripts/capture-screenshots.mjs
  - scripts/package.json
  - mise.toml
  - README.md
  - .gitignore
findings:
  critical: 3
  warning: 6
  info: 1
  total: 10
status: issues_found
---

# Phase 18: Code Review Report

**Reviewed:** 2026-05-03
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

The primary artifact is `scripts/capture-screenshots.mjs`, a 521-line Playwright ESM
script for headless Grafana screenshot capture. The other four files (`package.json`,
`mise.toml`, `README.md`, `.gitignore`) contained no defects. The review focused on
process cleanup on exit, file restore safety, child process lifecycle, and `execSync`
injection risks as requested.

Three blockers were found, all in `capture-screenshots.mjs`:

1. `captureTagCycle` leaks Spring Boot and load-generator child processes when
   `captureView` throws, because teardown is not in a `finally` block.
2. `disableTailSampling` never verifies that its two regex substitutions actually
   matched; a silent no-op produces a screenshot labelled "tail_sampling OFF" that is
   actually taken with tail_sampling ON.
3. After a `SIGKILL` (or any crash that bypasses `process.on('exit')`), a subsequent
   re-run unconditionally overwrites the `.bak` file with the already-modified config,
   permanently destroying the only known-good copy of `otelcol-config.yaml`.

---

## Critical Issues

### CR-01: `captureTagCycle` — `devProc` / `tagLoadProc` not killed in `finally` block

**File:** `scripts/capture-screenshots.mjs:430-441`

**Issue:** The Spring Boot dev process (`devProc`, line 412) and the tag-specific load
generator (`tagLoadProc`, line 422) are killed in an inline teardown block at lines
433-436. That teardown is NOT inside a `finally`. If `captureView` (line 430) throws
for any reason, execution jumps directly to the `finally` at line 440, which only
removes the git worktree. Both child processes are left alive, holding ports 8080 and
8081. The next call to `captureTagCycle` (for the next tag) then fails with "port
already in use" rather than a meaningful error.

The `SIGINT`/`SIGTERM` handlers registered in `main()` (lines 457-458) only call
`stopLoad()`, which kills the global `loadProc`; they have no reference to the
worktree `devProc` or `tagLoadProc`.

**Fix:**

```js
async function captureTagCycle(browser, capture) {
  // ...
  let devProc = null;
  let tagLoadProc = null;
  try {
    execSync(`git worktree add "${WORKTREE_DIR}" "${capture.tag}"`, { cwd: REPO_ROOT, stdio: 'inherit' });
    execSync('mvn -T 1C -DskipTests -q clean install', { cwd: WORKTREE_DIR, stdio: 'inherit' });

    devProc = spawn('mise', ['run', 'dev'], { cwd: WORKTREE_DIR, stdio: 'ignore', detached: false });
    await sleep(25_000);

    if (capture.tag !== 'step-01-baseline') {
      tagLoadProc = spawn(resolve(REPO_ROOT, 'scripts', 'load.sh'), [], {
        cwd: REPO_ROOT, stdio: 'ignore', detached: false,
      });
      await sleep(WARMUP_MS);
    }

    return await captureView(browser, { ...capture, headCapture: true });

  } finally {
    // Always kill child processes, then remove worktree.
    if (tagLoadProc) try { tagLoadProc.kill('SIGTERM'); } catch { /* ignore */ }
    if (devProc)     try { devProc.kill('SIGTERM'); } catch { /* ignore */ }
    try { execSync(`pkill -f "${MVN_RUN_PATTERN}"`, { stdio: 'ignore' }); } catch { /* none running */ }
    await sleep(2_000);
    try { execSync(`git worktree remove --force "${WORKTREE_DIR}"`, { cwd: REPO_ROOT, stdio: 'ignore' }); } catch { /* ignore */ }
  }
}
```

---

### CR-02: `disableTailSampling` — silent no-op if regex does not match

**File:** `scripts/capture-screenshots.mjs:213-229`

**Issue:** Both `yaml.replace()` calls at lines 216-222 and 224-226 return the
original string unchanged when the regex does not match — which happens if:

- The `tail_sampling:` block was already commented out (e.g., from a previous run
  that left the config in the disabled state).
- The `transform/copy_recordpolicy:` key was renamed or the block ordering changed.
- The processors list already lacks `tail_sampling`.

In all three cases `writeFileSync` at line 228 writes the unchanged content back to
disk, the collector is restarted (line 268), and `waitForCollectorHealthy` reports
healthy — all without any indication that tail_sampling is still active. The
subsequent OFF-frame screenshot is silently wrong.

**Fix:** Verify at minimum that the processors-line substitution occurred (the
processors line is more reliably matchable than the multi-line block):

```js
function disableTailSampling(configPath) {
  let yaml = readFileSync(configPath, 'utf-8');

  yaml = yaml.replace(
    /^(  tail_sampling:[\s\S]*?)^(  transform\/copy_recordpolicy:)/m,
    (match, block, next) => {
      const commented = block.split('\n').map(l => l ? '# ' + l : l).join('\n');
      return commented + next;
    }
  );

  const PROC_BEFORE = 'processors: [memory_limiter, tail_sampling, transform/copy_recordpolicy, batch]';
  const PROC_AFTER  = 'processors: [memory_limiter, transform/copy_recordpolicy, batch]';
  if (!yaml.includes(PROC_BEFORE)) {
    throw new Error(
      'disableTailSampling: processors line not found in expected form. ' +
      'Config may already be modified or otelcol-config.yaml has drifted. ' +
      'Restore manually: git checkout -- infra/observability/otelcol-config.yaml'
    );
  }
  yaml = yaml.replace(PROC_BEFORE, PROC_AFTER);

  writeFileSync(configPath, yaml, 'utf-8');
}
```

---

### CR-03: `withTailSamplingDisabled` — stale `.bak` overwrite permanently destroys known-good config

**File:** `scripts/capture-screenshots.mjs:263-264`

**Issue:** Line 264 unconditionally overwrites `OTELCOL_CONFIG_BAK` with whatever is
currently in `OTELCOL_CONFIG`, with no check that a stale `.bak` already exists or
that `OTELCOL_CONFIG` is currently in its clean/original state.

When the Node process is terminated with `SIGKILL` (or crashes via a signal that
bypasses `process.on('exit')`), the config file is left modified on disk and
`OTELCOL_CONFIG_BAK` holds the original. On the next run, line 264 copies the
already-modified config over the original backup. Both the live config and the backup
now contain the disabled-tail-sampling state. The `restoreConfigSync` call in
`finally` then "restores" the modified version onto itself — permanently. The only
recovery path is `git checkout -- infra/observability/otelcol-config.yaml`.

**Fix:** Guard against overwriting a valid backup by checking whether the stale
`.bak` exists before proceeding:

```js
async function withTailSamplingDisabled(fn) {
  if (existsSync(OTELCOL_CONFIG_BAK)) {
    throw new Error(
      `Stale backup found: ${OTELCOL_CONFIG_BAK}\n` +
      'A previous run may have left otelcol-config.yaml in a modified state.\n' +
      'Verify the config is correct, then delete the .bak file and re-run.\n' +
      'To restore from git: git checkout -- infra/observability/otelcol-config.yaml'
    );
  }
  copyFileSync(OTELCOL_CONFIG, OTELCOL_CONFIG_BAK);
  // ... rest unchanged
}
```

---

## Warnings

### WR-01: `FORCE=0` enables force mode (Boolean string parsing)

**File:** `scripts/capture-screenshots.mjs:33`

**Issue:** `Boolean(process.env.FORCE)` evaluates to `true` for ANY non-empty string,
including `'0'`, `'false'`, and `'no'`. A developer who runs `FORCE=0 mise run
screenshots` expecting to use the default (no-force) mode actually enables force
re-capture of all files.

**Fix:** Use an explicit string comparison:

```js
const FORCE = process.env.FORCE === '1' || process.env.FORCE === 'true';
```

---

### WR-02: `WARMUP_MS` env accepts invalid values silently

**File:** `scripts/capture-screenshots.mjs:32`

**Issue:** `Number(process.env.WARMUP_MS || 30_000)` — when `WARMUP_MS` is set to an
empty string, `Number('')` evaluates to `0`, producing a zero-millisecond warmup
with no warning. When set to a non-numeric string (e.g., `WARMUP_MS=abc`),
`Number('abc')` is `NaN`; `setTimeout(resolve, NaN)` in Node.js fires immediately
(treated as 0). Both cases bypass the intended warmup silently, producing screenshots
before data has propagated.

**Fix:**

```js
const _warmupRaw = process.env.WARMUP_MS;
const WARMUP_MS = _warmupRaw !== undefined
  ? (() => {
      const v = Number(_warmupRaw);
      if (!Number.isFinite(v) || v < 0) throw new Error(`Invalid WARMUP_MS: "${_warmupRaw}"`);
      return v;
    })()
  : 30_000;
```

---

### WR-03: Global `loadProc` sends load to worktree app during tag-cycling captures

**File:** `scripts/capture-screenshots.mjs:461-463, 507-510`

**Issue:** `startLoad()` is called once in `main()` at line 461 and sends continuous
traffic to `http://localhost:8080/orders`. During `captureTagCycle`, the HEAD-branch
Spring Boot apps are NOT running; the worktree apps start on the same ports (8080,
8081 from `mise.toml` `PRODUCER_PORT`/`CONSUMER_PORT`). The global `loadProc` then
sends its warm-up traffic to the WORKTREE app, generating telemetry from the wrong
tag into the shared Grafana backend. This can contaminate Tempo/Mimir/Loki with
spans from the wrong step tag, producing false positives in tag-specific screenshots
(e.g., the step-01-baseline Tempo screenshot showing traces from a later tag).

**Fix:** Stop the global load before entering tag-cycling captures and restart it
afterward, or pass `stopLoad()`/`startLoad()` into `captureTagCycle`:

```js
// In captureTagCycle, before spawn(mise run dev):
stopLoad();
// ... capture ...
// After worktree teardown in finally:
startLoad();
```

---

### WR-04: `withTailSamplingDisabled` — collector left in inconsistent state when `waitForCollectorHealthy` throws

**File:** `scripts/capture-screenshots.mjs:268-281`

**Issue:** Lines 279-281 (the second `restartCollector` + `waitForCollectorHealthy` +
`sleep` that warm up the collector for the ON-frame capture) run only on the normal
(no-exception) code path after the `finally` block. If `waitForCollectorHealthy`
at line 269 throws (30-second timeout), `restoreConfigSync()` correctly runs in
`finally` — the config file on disk is restored — but the running collector is never
restarted after the restore. The next entry in `CAPTURES` is the ON-frame capture;
it runs against a collector whose loaded config still has `tail_sampling` disabled
(the last restart, at line 268, was with the disabled config). The ON-frame
screenshot is captured with tail sampling effectively OFF.

**Fix:** Move the ON-frame restart into a second `try/finally` that runs only when
the OFF-frame succeeded, or wrap lines 279-281 in a `try` and document that ON-frame
is invalid if this block throws:

```js
async function withTailSamplingDisabled(fn) {
  // ...backup + try/finally as before...
  // After finally:
  try {
    console.log('tail_sampling: restoring config, restarting for ON-frame warm-up...');
    restartCollector();
    await waitForCollectorHealthy();
    await sleep(WARMUP_MS);
  } catch (err) {
    console.error('ERROR: collector restart for ON-frame warm-up failed; ON-frame screenshot will be invalid.');
    console.error(err.message);
    throw err;  // propagate so main() registers the ON-frame as failed, not captured
  }
}
```

---

### WR-05: `ON-frame` capture has an undocumented, unguarded ordering dependency

**File:** `scripts/capture-screenshots.mjs:495-499`

**Issue:** The capture entry with `tailSamplingToggle === 'ON'` (line 131) assumes
that the `withTailSamplingDisabled` function has already run for the preceding OFF
entry, which performed the collector restart and 30-second warmup. This is a silent
ordering contract enforced only by array position. If the CAPTURES array is
reordered, if the ON entry is moved without its OFF counterpart, or if a future
developer adds a standalone ON entry, the capture runs without the required warmup
and against an indeterminate collector state.

The comment at line 496 ("warm-up already done by withTailSamplingDisabled above")
documents intent but provides no runtime guarantee.

**Fix:** Add an explicit guard so that an ON-frame entry without a preceding OFF-frame
execution fails fast with a clear error rather than silently producing a bad
screenshot:

```js
let tailSamplingRestored = false;  // set to true in withTailSamplingDisabled after ON-warmup

// In the ON-frame branch:
} else if (capture.tailSamplingToggle === 'ON') {
  if (!tailSamplingRestored) {
    throw new Error(
      'ON-frame capture reached without a preceding OFF-frame execution. ' +
      'Ensure the OFF-frame entry precedes the ON-frame entry in CAPTURES.'
    );
  }
  const r = await captureView(browser, capture);
  results.push(r);
}
```

---

### WR-06: `pkill -f` pattern kills any matching process system-wide, not just script children

**File:** `scripts/capture-screenshots.mjs:436`

**Issue:** `pkill -f "spring-boot:run -D"` sends `SIGTERM` to every process on the
system whose full command line contains that string, regardless of whether it is a
child of this script. On a developer machine running multiple Maven projects
simultaneously, this kills unrelated Spring Boot processes.

The comment at line 402 acknowledges this is a deliberate scoping decision ("Rule-3
deviation from v1.0") but does not document the cross-process risk.

**Fix:** Scope the kill to descendants of the current process using the PID of the
`mise run dev` subprocess, or track the maven PID explicitly:

```js
// After devProc teardown in finally:
try {
  // Kill only descendants of devProc's process group
  execSync(`pkill -P ${devProc.pid} -f "${MVN_RUN_PATTERN}"`, { stdio: 'ignore' });
} catch { /* none running */ }
```

Alternatively, document the intentional behavior with an explicit comment warning
operators not to run concurrent Spring Boot projects during screenshot capture.

---

## Info

### IN-01: `package-lock.json` is gitignored for a reproducibility-sensitive tool

**File:** `.gitignore:31`, `scripts/package.json`

**Issue:** `package-lock.json` is listed in `.gitignore`. While `playwright` is pinned
to exact version `1.59.1` (no `^` or `~`), omitting the lockfile means `npm install`
in a fresh clone resolves Playwright's own transitive dependencies against the npm
registry at install time. Playwright's transitive graph is small and stable, but the
absence of a lockfile reduces reproducibility across workshop cohorts — consistent
with the project's stated `verify:images` philosophy of exact version pins.

**Fix:** Either commit `scripts/package-lock.json` (remove from `.gitignore`), or
document in `scripts/package.json` that the tool is tested against Node.js LTS +
`playwright@1.59.1` specifically and that transitive resolution variance is accepted.

---

_Reviewed: 2026-05-03_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
