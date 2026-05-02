---
phase: 07-polish-differentiators
plan: 04
type: execute
wave: 3
depends_on: [07-01, 07-02, 07-03]
files_modified:
  - docs/screenshots/step-01-empty-tempo.png
  - docs/screenshots/step-02-disconnected-traces.png
  - docs/screenshots/step-03-joined-trace.png
  - docs/screenshots/step-03-waterfall.png
  - docs/screenshots/step-04-metrics.png
  - docs/screenshots/step-05-logs-trace-jump.png
  - docs/screenshots/step-06-test-output.png
  - mise.toml
autonomous: false
requirements: [DOC-04]
risk: high
tags: [screenshots, capture, git-worktree, dangerous]

must_haves:
  truths:
    - "docs/screenshots/ contains at least 6 PNG files (7-8 if optional waterfall capture succeeded)"
    - "step-02-disconnected-traces.png shows TWO separate trace IDs for one logical request (DOC-04 broken half)"
    - "step-03-joined-trace.png shows ONE trace ID for one logical request (DOC-04 fixed half)"
    - "Main checkout returned to HEAD past step-06-tests after capture cycle (no left-over git worktree state)"
    - "PNGs are committed to git with lossless dimensions (>=800px wide, readable text)"
  artifacts:
    - path: "docs/screenshots/step-01-empty-tempo.png"
      provides: "Phase 1 baseline — empty Tempo (D-06 capture #1)"
    - path: "docs/screenshots/step-02-disconnected-traces.png"
      provides: "DOC-04 broken half (D-06 capture #2)"
    - path: "docs/screenshots/step-03-joined-trace.png"
      provides: "DOC-04 fixed half (D-06 capture #3)"
    - path: "docs/screenshots/step-04-metrics.png"
      provides: "Phase 4 RED metrics (D-06 capture #5)"
    - path: "docs/screenshots/step-05-logs-trace-jump.png"
      provides: "Phase 5 Loki-to-Tempo click-through (D-06 capture #6)"
    - path: "docs/screenshots/step-06-test-output.png"
      provides: "Phase 6 mvn verify with random RabbitMQ port (D-06 capture #7)"
  key_links:
    - from: "scripts/screenshots/capture.mjs"
      to: "docs/screenshots/*.png"
      via: "Playwright page.screenshot writes"
      pattern: "page.screenshot"
    - from: "Per-tag git checkout in worktree"
      to: "infra:up + dev + load + capture"
      via: "Wrapper bash driver (Task 1)"
      pattern: "git worktree"
---

<objective>
Run the screenshot pipeline scaffolded by plan 07-03 against each of the six git tags to produce the 6-8 PNGs that anchor DOC-04 and the README rewrite. Implements DOC-04 per CONTEXT.md D-05 + D-06.

Purpose: Workshop attendees see the broken-vs-fixed propagation delta visually side-by-side in the README without running the steps. The DOC-04 anchor pair (step-02 disconnected vs step-03 joined) is the single most important visual asset in the repo per CONTEXT.md `<specifics>`.

Output: 6-8 PNGs committed to `docs/screenshots/`. Plan is `autonomous: false` because the capture cycle is high-blast-radius (cycles git tags via worktree, brings up infra, starts apps, runs load, captures, tears down — each step has failure modes humans need to observe).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@scripts/screenshots/capture.mjs
@mise.toml
@docker-compose.yml
@.planning/phases/07-polish-differentiators/07-01-SUMMARY.md
@.planning/phases/07-polish-differentiators/07-02-SUMMARY.md
@.planning/phases/07-polish-differentiators/07-03-SUMMARY.md

<interfaces>
<!-- All six tags exist on main per `git tag -l` (verified at planning time): -->
- step-01-baseline
- step-02-traces
- step-03-context-propagation
- step-04-metrics
- step-05-logs
- step-06-tests

<!-- Capture script entry point (from plan 07-03): -->
node scripts/screenshots/capture.mjs

<!-- Dashboard UID (from plan 07-01): -->
ose-otel-demo

<!-- Required env vars (defaults in capture.mjs): -->
GRAFANA_URL  default http://localhost:3000
GRAFANA_USER default admin
GRAFANA_PASS default admin
WARMUP_MS    default 30000
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Author tag-cycling driver — extend mise run docs:screenshots</name>
  <read_first>
    - mise.toml (existing [tasks."docs:screenshots"] from plan 07-03)
    - scripts/screenshots/capture.mjs (existing capture loop, expects current checkout to match each capture's `tag` field)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-05 — workspace isolation via git worktree)
  </read_first>
  <action>
    Replace the body of `[tasks."docs:screenshots"]` in `mise.toml` (the minimal version from plan
    07-03) with a driver that cycles tags via `git worktree`. Exact replacement:

    ```toml
    [tasks."docs:screenshots"]
    description = "Capture per-step screenshots (DOC-04). Cycles step-NN-* tags via git worktree, drives Grafana via Playwright, writes docs/screenshots/*.png."
    run = """
    set -euo pipefail

    REPO_ROOT="$PWD"
    WORKTREE_DIR="$REPO_ROOT/.screenshots-worktree"
    OUTPUT_DIR="$REPO_ROOT/docs/screenshots"
    mkdir -p "$OUTPUT_DIR"

    cleanup() {
      # Kill any background dev/load processes and tear down worktree.
      pkill -f 'spring-boot:run' 2>/dev/null || true
      pkill -f 'oha ' 2>/dev/null || true
      pkill -f 'hey ' 2>/dev/null || true
      if git worktree list 2>/dev/null | grep -q "$WORKTREE_DIR"; then
        git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || true
      fi
    }
    trap cleanup EXIT

    # Install Playwright deps once at the canonical location (main checkout).
    cd "$REPO_ROOT/scripts/screenshots"
    npm install
    npx playwright install --with-deps chromium
    cd "$REPO_ROOT"

    # Bring up infra once — the same lgtm/rabbitmq containers serve every tag.
    mise run infra:up

    TAGS=(step-01-baseline step-02-traces step-03-context-propagation step-04-metrics step-05-logs step-06-tests)

    for tag in "${TAGS[@]}"; do
      echo "=== capturing for $tag ==="
      # Fresh worktree for the tag (idempotent — clean any stale one).
      [ -d "$WORKTREE_DIR" ] && git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || true
      git worktree add "$WORKTREE_DIR" "$tag"

      # Build + start dev from the worktree (host JVM via mise).
      pushd "$WORKTREE_DIR" > /dev/null

      # Phase 1 baseline (step-01-baseline) is a working Spring Boot app with zero OTel libs —
      # the build SHOULD succeed at every tag. A failure here is a real problem; surface it.
      if ! mvn -T 1C -DskipTests -q clean install; then
        echo "build failed for $tag — aborting capture (this should not happen for any step-NN-* tag)"
        exit 1
      fi

      # Start dev in background; let Spring Boot processes warm up.
      mise run dev > "$REPO_ROOT/.screenshots-worktree-dev.log" 2>&1 &
      DEV_PID=$!
      sleep 25  # Spring Boot startup (~15-20s realistic)

      # Start load in background to populate panels (skip for step-01-baseline — no apps will accept).
      if [ "$tag" != "step-01-baseline" ]; then
        "$REPO_ROOT/scripts/load.sh" > "$REPO_ROOT/.screenshots-worktree-load.log" 2>&1 &
        LOAD_PID=$!
        sleep 30  # WARMUP_MS — let panels populate
      fi

      popd > /dev/null

      # Run capture for this tag's outputs.
      # capture.mjs runs all CAPTURES; we filter via env to capture only this tag's entries.
      CAPTURE_TAG_FILTER="$tag" node "$REPO_ROOT/scripts/screenshots/capture.mjs" || echo "capture for $tag had failures — see logs"

      # Tear down dev + load before moving to next tag.
      [ -n "${LOAD_PID:-}" ] && kill "$LOAD_PID" 2>/dev/null || true
      kill "$DEV_PID" 2>/dev/null || true
      pkill -f 'spring-boot:run' 2>/dev/null || true
      sleep 5

      git worktree remove --force "$WORKTREE_DIR" 2>/dev/null || true
    done

    echo "=== screenshots complete ==="
    ls -la "$OUTPUT_DIR"
    """
    ```

    AND update `scripts/screenshots/capture.mjs` to honor a `CAPTURE_TAG_FILTER` env var that
    restricts the for-loop to only the entries whose `tag` equals the filter. Add this filter near
    the top of `main()`:
    ```javascript
    const TAG_FILTER = process.env.CAPTURE_TAG_FILTER;
    const filtered = TAG_FILTER ? CAPTURES.filter(c => c.tag === TAG_FILTER) : CAPTURES;
    // ... then iterate `filtered` instead of `CAPTURES`
    ```

    Notes:
    - The driver uses `git worktree add` to keep the main checkout intact (D-05 workspace isolation).
    - Infra is brought up ONCE at the start; the same lgtm/rabbitmq serve all six tag runs.
    - For `step-01-baseline` the build SUCCEEDS (Phase 1 ships a working two-service Spring Boot app
      with zero OTel libs) but the apps emit zero telemetry — Tempo simply remains empty. That IS the
      lesson; we capture the empty-Tempo screenshot per D-06 #1. A real build failure at any tag is
      surfaced via `exit 1` (no silent-mask fallback).
    - Each tag's CAPTURES are filtered via `CAPTURE_TAG_FILTER` so capture.mjs is invoked once per tag.
    - `cleanup()` trap kills bg processes + removes worktree on any exit (clean or error).
  </action>
  <verify>
    <automated>
      grep -q 'git worktree' mise.toml \
      && grep -q 'CAPTURE_TAG_FILTER' mise.toml \
      && grep -q 'CAPTURE_TAG_FILTER' scripts/screenshots/capture.mjs \
      && grep -q 'TAGS=(step-01-baseline' mise.toml \
      && bash -n scripts/load.sh \
      && node --check scripts/screenshots/capture.mjs \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `mise.toml` `[tasks."docs:screenshots"]` body contains `git worktree`
    - mise.toml task body iterates a TAGS array including all six step-* tags
    - mise.toml task body sets `CAPTURE_TAG_FILTER` per iteration
    - `scripts/screenshots/capture.mjs` reads `process.env.CAPTURE_TAG_FILTER` and filters CAPTURES
    - `node --check scripts/screenshots/capture.mjs` exits 0
    - `cleanup()` trap exists in the mise.toml task body
  </acceptance_criteria>
  <done>
    Tag-cycling driver authored. capture.mjs filter installed. Pipeline can be run end-to-end via
    `mise run docs:screenshots`.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 2: Run the capture pipeline (high-blast-radius operation)</name>
  <what-built>
    Tag-cycling driver in mise.toml + capture.mjs filter. PNGs not yet produced.
  </what-built>
  <how-to-verify>
    Pre-flight (do these BEFORE approving):
    1. Ensure main checkout is clean: `git status` shows no uncommitted changes (or stash them).
    2. Ensure no critical work-in-progress is in the worktree dir: `ls .screenshots-worktree 2>/dev/null` should be empty/missing.
    3. Stop any running infra + apps: `mise run infra:down` and `pkill -f spring-boot:run`.
    4. Confirm at least 5GB free disk (Playwright Chromium + Maven build cache for six tags).

    Run the pipeline:
    ```bash
    mise run docs:screenshots 2>&1 | tee /tmp/docs-screenshots.log
    ```
    This will take ~15-30 minutes:
    - Playwright Chromium install (~3 min first time, cached after)
    - Six iterations of: worktree add + mvn build + dev startup + load + capture + teardown (~2-4 min each)

    During the run, watch for:
    - "build failed for $tag — aborting capture" — should NOT occur for any step-NN-* tag. Phase 1 baseline
      builds cleanly without OTel libs (it's a working Spring Boot app); any real failure aborts the pipeline
      so it can be investigated rather than silently masked.
    - "capture for <tag> had failures — see logs" — investigate per-tag failure; optional waterfall capture is allowed to fail per D-06.

    After the run completes:
    1. `ls docs/screenshots/` should show 6-7 PNGs.
    2. Open each PNG in an image viewer; confirm:
       - `step-01-empty-tempo.png`: Tempo trace search shows 0 results.
       - `step-02-disconnected-traces.png`: TWO separate trace rows for one logical POST.
       - `step-03-joined-trace.png`: ONE trace row spanning producer + consumer.
       - `step-03-waterfall.png` (if present): waterfall view of the joined trace.
       - `step-04-metrics.png`: Mimir/Prometheus showing orders_created_total and HTTP histogram.
       - `step-05-logs-trace-jump.png`: Loki log line with trace_id field visible.
       - `step-06-test-output.png`: terminal-styled output showing four green tests + random RabbitMQ port.
    3. `git status` from the main checkout should show only NEW files in `docs/screenshots/` (no other side effects).
    4. Verify no leftover worktree: `git worktree list` should NOT show `.screenshots-worktree`.

    If any non-optional capture failed: re-run only that tag manually — `git worktree add .screenshots-worktree <tag>` then build + dev + load + `CAPTURE_TAG_FILTER=<tag> node scripts/screenshots/capture.mjs`.

    Stage and commit the PNGs:
    ```bash
    git add docs/screenshots/*.png
    git status  # confirm only PNGs staged
    ```
  </how-to-verify>
  <resume-signal>Type "approved" if all 6 (or 7-8) PNGs are present, visually correct, and main checkout is clean. Or describe issues (which capture failed, any worktree leftover state).</resume-signal>
</task>

<task type="auto">
  <name>Task 3: Commit PNGs to git</name>
  <read_first>
    - docs/screenshots/ (just-produced PNGs)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-06 — PNGs are git-tracked, embedded in README per-step)
  </read_first>
  <action>
    Step 1 — Verify PNGs are reasonably sized (PNG should be > 50KB and < 5MB each — sanity check
    that Playwright captured something real, not a blank page):
    ```bash
    for f in docs/screenshots/*.png; do
      size=$(stat -c%s "$f")
      if [ "$size" -lt 50000 ]; then
        echo "WARN: $f is suspiciously small ($size bytes) — re-capture?"
      elif [ "$size" -gt 5000000 ]; then
        echo "WARN: $f is suspiciously large ($size bytes) — fullPage capture maybe too long"
      else
        echo "OK: $f ($size bytes)"
      fi
    done
    ```

    Step 2 — Stage + commit the PNGs (do NOT commit `.screenshots-worktree*.log` files — they are
    transient and the cleanup trap removes them, but verify with `git status`).
    ```bash
    git add docs/screenshots/
    git status  # verify ONLY docs/screenshots/*.png are staged + .gitignore entry if needed
    git commit -m "docs(07-04): capture per-step screenshots (DOC-04 / D-06)"
    ```

    Step 3 — Add `.screenshots-worktree*` to repo root `.gitignore` if not already present:
    ```bash
    if ! grep -q '.screenshots-worktree' .gitignore 2>/dev/null; then
      echo '' >> .gitignore
      echo '# Phase 7 / DOC-04 — screenshot capture worktree + transient logs' >> .gitignore
      echo '.screenshots-worktree/' >> .gitignore
      echo '.screenshots-worktree-*.log' >> .gitignore
    fi
    ```
    Then commit:
    ```bash
    git add .gitignore
    git diff --cached  # review
    git commit -m "chore(07-04): gitignore screenshot capture worktree"
    ```
    (Only if the .gitignore changed.)
  </action>
  <verify>
    <automated>
      ls docs/screenshots/step-01-empty-tempo.png \
      && ls docs/screenshots/step-02-disconnected-traces.png \
      && ls docs/screenshots/step-03-joined-trace.png \
      && ls docs/screenshots/step-04-metrics.png \
      && ls docs/screenshots/step-05-logs-trace-jump.png \
      && ls docs/screenshots/step-06-test-output.png \
      && git ls-files docs/screenshots/ | grep -c '\.png$' | { read -r n; [[ $n -ge 6 ]]; } \
      || (echo "VERIFY FAILED: required PNGs missing or not committed" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - At least 6 PNGs exist in `docs/screenshots/` (7-8 if optional waterfall succeeded):
      * step-01-empty-tempo.png
      * step-02-disconnected-traces.png
      * step-03-joined-trace.png
      * step-04-metrics.png
      * step-05-logs-trace-jump.png
      * step-06-test-output.png
      * (optional) step-03-waterfall.png
    - Each PNG > 50KB and < 5MB (sanity bounds)
    - All required PNGs tracked in git (`git ls-files docs/screenshots/`)
    - `.gitignore` excludes `.screenshots-worktree*` if those paths exist
    - `git worktree list` does NOT show `.screenshots-worktree`
  </acceptance_criteria>
  <done>
    PNGs committed and ready for README embedding (plan 07-05 / 07-06). Main checkout is at HEAD past
    step-06-tests (where it was at plan start) with screenshot commits added.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Capture pipeline -> git worktree (state isolation) | `git worktree` keeps main checkout intact during tag cycling |
| Background dev/load processes -> trap cleanup | `trap cleanup EXIT` kills children on any exit path |
| Disk usage | Maven cache + Playwright Chromium + 6 worktrees ~= 5GB |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-04-01 | Tampering | Tag-cycling git operations | mitigate | Worktree-based isolation (`git worktree add/remove`); main checkout never `git checkout`-ed; cleanup trap removes worktree on any exit |
| T-07-04-02 | Denial of Service | Background `mvn` + `dev` processes leaking across iterations | mitigate | `pkill -f 'spring-boot:run'` + `pkill -f 'oha '` in cleanup; per-iteration `kill $DEV_PID $LOAD_PID` |
| T-07-04-03 | Information Disclosure | `.screenshots-worktree-*.log` may contain Spring Boot startup logs (no secrets — env vars are demo defaults) | accept | Logs are transient; `.gitignore` excludes them; cleanup removes worktree |
| T-07-04-04 | Spoofing | PNG content is auto-captured from running Grafana | mitigate | Human-verify checkpoint (Task 2) requires inspecting each PNG; suspicious-size warnings flag empty/blank captures |
| T-07-04-05 | Tampering | Captured PNG of `mvn verify` output (terminal kind) | accept | Output is captured from real `mise run test` execution; not user-supplied; if test fails the build exits non-zero and the PNG is not produced |
</threat_model>

<verification>
- 6+ PNGs committed under `docs/screenshots/`.
- DOC-04 anchor pair (step-02-disconnected + step-03-joined) visually shows the broken/fixed delta.
- Main checkout returned to pre-run state (no leftover worktree, no checkout drift).
- DOC-04 success criterion satisfied at the artifact level (PNGs exist; embedding in README lands in plans 07-05/06).
</verification>

<success_criteria>
- All 6 required PNGs in `docs/screenshots/`, committed to git.
- DOC-04 broken/fixed pair (step-02 + step-03) visually shows the propagation delta.
- Main checkout intact after run; no orphan processes; worktree cleaned up.
- Human-verify checkpoint approved.
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-04-SUMMARY.md` recording:
- Number of PNGs produced (6, 7, or 8)
- Whether optional captures (step-03-waterfall) succeeded or were skipped
- Total wall-clock time for the capture pipeline
- Any per-tag failures + manual remediation taken
- File sizes of each PNG (sanity check)
</output>
