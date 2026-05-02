---
phase: 07-polish-differentiators
plan: 02
type: execute
wave: 1
depends_on: []
files_modified:
  - scripts/load.sh
  - mise.toml
autonomous: true
requirements: [WORK-03]
risk: low
tags: [load, oha, mise, demo]

must_haves:
  truths:
    - "mise run load issues continuous POST /orders requests at ~1 req/sec total split 50/50 across express/standard priorities"
    - "Ctrl-C on mise run load kills both oha child processes cleanly (no orphan oha processes)"
    - "Both per-priority series populate the dashboard's deeper-dive `Orders Created by Priority` panel"
    - "scripts/load.sh is executable (chmod +x)"
  artifacts:
    - path: "scripts/load.sh"
      provides: "Two parallel oha invocations + SIGINT/SIGTERM trap (D-04)"
      contains: "oha"
      executable: true
    - path: "mise.toml"
      provides: "[tools] oha (or hey fallback) entry + [tasks.load] task definition"
      contains: "[tasks.load]"
  key_links:
    - from: "mise.toml [tasks.load]"
      to: "./scripts/load.sh"
      via: "run = ./scripts/load.sh"
      pattern: 'run = "./scripts/load.sh"'
    - from: "scripts/load.sh"
      to: "http://localhost:8080/orders"
      via: "oha HTTP POST"
      pattern: "localhost:.*8080.*orders"
---

<objective>
Add a continuous-load wrapper script that pumps a steady stream of `POST /orders` requests so live demos have continuously-flowing telemetry without hand-clicking. Implements WORK-03 per CONTEXT.md D-03 + D-04 + D-04.1.

Purpose: A workshop instructor running `mise run load` in one terminal alongside `mise run dev` sees Tempo, Mimir, and Loki populate live during a 60-minute demo with zero manual `curl`-clicking. The chosen tool (`oha`) ALSO surfaces a live-RPS + p50/p95 latency TUI in the same terminal — a side-by-side "client view vs server view" moment the README Step 1 *Why it matters* paragraph calls out (CONTEXT.md `<specifics>`).

Output: `scripts/load.sh` (executable), and `[tools]` + `[tasks.load]` entries appended to `mise.toml`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@mise.toml
@.planning/phases/01-baseline-scaffold/01-CONTEXT.md
@CLAUDE.md

<interfaces>
<!-- Phase 1 / mise run demo:order is the source of truth for the order JSON payload shape -->

Existing mise.toml `[tasks."demo:order"]` posts:
```json
{"sku":"WIDGET-1","quantity":3,"priority":"express"}
{"sku":"WIDGET-2","quantity":1,"priority":"standard"}
```

Producer endpoint (Phase 1 APP-01): POST http://localhost:8080/orders -> 202

oha CLI (https://github.com/hatoo/oha):
- `-z 0` infinite duration
- `-q <rps>` requests-per-second
- `-m POST` HTTP method
- `-d '<body>'` request body
- `-T application/json` Content-Type
- TUI shows live RPS + p50/p95/p99
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Resolve oha install path (mise plugin or fallback)</name>
  <read_first>
    - mise.toml (existing [tools] block, current pinned tools)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-03 — locked tool preference + fallback)
    - https://mise.jdx.dev/registry.html (mise plugin registry, as of 2026-05)
  </read_first>
  <action>
    Step 1 — Probe mise plugin availability for `oha`:
    ```bash
    mise registry 2>/dev/null | grep -iE '\boha\b' || echo "NOT FOUND"
    mise plugins ls-remote 2>/dev/null | grep -i oha || true
    ```
    If `oha` is in the registry or a community plugin exists, that is the preferred path.
    If `oha` is NOT in the registry, probe `hey`:
    ```bash
    mise registry 2>/dev/null | grep -iE '\bhey\b' || echo "NOT FOUND"
    ```

    Step 2 — Resolution outcomes (pick the first that applies):

    A) If `oha` plugin exists → append to `mise.toml` `[tools]` block:
       ```toml
       oha = "latest"
       ```
       (or pin to a specific version if registry returns one — pin if available for reproducibility,
       e.g. `oha = "1.4.6"`).

    B) If `oha` plugin missing but `hey` plugin exists → append:
       ```toml
       hey = "latest"
       ```
       and adjust `scripts/load.sh` (Task 2) to call `hey` with its CLI flags
       (`-n -1 -q <rps> -m POST -T application/json -d <body>` — `hey` does not have a `-z 0`
       infinite shape; use a very large `-n` such as `-n 1000000` per CONTEXT.md fallback note).

    C) If neither plugin exists → use `cargo install oha` via a mise task. Append to `mise.toml`:
       ```toml
       [tools]
       cargo:oha = "latest"
       ```
       (mise's `cargo:` backend installs Rust binaries; this is the documented vendor-bypass route.)
       If this also fails, document the failure in SUMMARY and add a Phase 7 deferred item:
       "manual install of oha required". Last-resort: write `scripts/load.sh` as a pure curl + sleep
       loop fallback (degrades the live-RPS TUI lesson but preserves WORK-03 contract).

    Record the chosen path (A/B/C/D) in SUMMARY.md.
  </action>
  <verify>
    <automated>
      grep -qE '^(oha|hey|cargo:oha) *=' mise.toml \
      || (echo "VERIFY FAILED: no load tool in mise.toml [tools]" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `mise.toml` `[tools]` block contains exactly one of: `oha = ...`, `hey = ...`, or `cargo:oha = ...`
    - Existing `[tools]` entries (`java`, `maven`, `node`) UNCHANGED
    - SUMMARY.md records which option (A/B/C/D) was chosen and why
    - If option D: a deferred-item note is added to the SUMMARY (manual install required)
  </acceptance_criteria>
  <done>
    Load tool pinned via mise (or fallback path documented). Task 2 references the chosen tool.
  </done>
</task>

<task type="auto">
  <name>Task 2: Author scripts/load.sh with two parallel oha invocations + trap</name>
  <read_first>
    - mise.toml (existing [tasks."demo:order"] for payload shape source-of-truth per D-04)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-04 verbatim — sketch of bash body, trap shape, payload schema parameterized only by priority)
  </read_first>
  <action>
    Create directory `scripts/` at repo root if missing.

    Author `scripts/load.sh` with the following EXACT structure (assuming Task 1 chose option A — `oha`).
    If Task 1 chose option B (`hey`), substitute `hey -n 1000000 ...` for the `oha -z 0 ...` lines.
    If Task 1 chose option C (`cargo:oha`), the binary name is still `oha`.

    ```bash
    #!/usr/bin/env bash
    # scripts/load.sh — continuous load against producer-service for live demos.
    # WORK-03 / Phase 7 D-04: two parallel oha invocations alternating priorities.
    #
    # ~0.5 req/sec per priority = ~1 req/sec total split 50/50.
    # Both per-priority series populate the dashboard's "Orders Created by Priority" panel.
    #
    # Ctrl-C kills both child oha processes via the cleanup trap.
    # Run alongside `mise run dev` (NOT `mise run demo:order` — that is one-shot).

    set -euo pipefail

    TARGET="${TARGET:-http://localhost:8080/orders}"

    cleanup() {
      # SIGINT/SIGTERM/EXIT — kill both children if alive, then wait so we don't leave zombies.
      [[ -n "${PID_EXPRESS:-}" ]] && kill "$PID_EXPRESS" 2>/dev/null || true
      [[ -n "${PID_STANDARD:-}" ]] && kill "$PID_STANDARD" 2>/dev/null || true
      wait 2>/dev/null || true
    }
    trap cleanup SIGINT SIGTERM EXIT

    echo "WORK-03: continuous load against ${TARGET}"
    echo "Two parallel oha invocations: priority=express @ 0.5 rps, priority=standard @ 0.5 rps"
    echo "Press Ctrl-C to stop both."
    echo

    oha -z 0 -q 0.5 \
        -m POST \
        -T application/json \
        -d '{"sku":"WIDGET-EXPRESS","quantity":3,"priority":"express"}' \
        "${TARGET}" &
    PID_EXPRESS=$!

    oha -z 0 -q 0.5 \
        -m POST \
        -T application/json \
        -d '{"sku":"WIDGET-STANDARD","quantity":1,"priority":"standard"}' \
        "${TARGET}" &
    PID_STANDARD=$!

    wait
    ```

    Then `chmod +x scripts/load.sh`.

    Notes on locked decisions (do NOT alter):
    - Two parallel invocations (NOT one) — D-04 (loses per-priority panel otherwise).
    - Hard-coded payload (NOT body-file cycling) — D-04 (fragile across oha versions).
    - No RPS/DURATION env-var arg parser — D-04 (v1 ships workshop default; per-attendee
      tweakability is a v1.x ask). The single `TARGET` env override is a minor convenience for
      pointing at a non-default producer port; CONTEXT.md does not forbid it.
    - JSON payload schema mirrors `mise run demo:order` shape (sku/quantity/priority); the only
      parameterized field is `priority` (D-04).
    - SIGINT/SIGTERM/EXIT trap with `kill` + `wait` (D-04 verbatim).
  </action>
  <verify>
    <automated>
      test -f scripts/load.sh \
      && [[ -x scripts/load.sh ]] \
      && bash -n scripts/load.sh \
      && grep -q '#!/usr/bin/env bash' scripts/load.sh \
      && grep -q 'set -euo pipefail' scripts/load.sh \
      && grep -q 'trap cleanup' scripts/load.sh \
      && grep -q 'priority":"express"' scripts/load.sh \
      && grep -q 'priority":"standard"' scripts/load.sh \
      && grep -q 'localhost:8080/orders' scripts/load.sh \
      && grep -cE '(oha|hey) ' scripts/load.sh | { read -r n; [[ $n -ge 2 ]]; } \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `scripts/load.sh` exists
    - File is executable: `[[ -x scripts/load.sh ]]`
    - `bash -n scripts/load.sh` (syntax check) exits 0
    - File contains shebang `#!/usr/bin/env bash`
    - File contains `set -euo pipefail`
    - File contains the literal `trap cleanup` and a `cleanup()` function definition
    - File contains the literal substring `priority":"express"` (express payload)
    - File contains the literal substring `priority":"standard"` (standard payload)
    - File targets `localhost:8080/orders` (matches producer port from mise.toml PRODUCER_PORT)
    - File contains AT LEAST TWO `oha ` (or `hey `) invocations (the two parallel children)
    - File contains backgrounding `&` after each load-tool invocation
    - File contains a final `wait`
  </acceptance_criteria>
  <done>
    Load script authored; backgrounds two parallel load-tool invocations alternating priorities;
    SIGINT/SIGTERM trap installed; payload mirrors `mise run demo:order` schema parameterized only
    by priority.
  </done>
</task>

<task type="auto">
  <name>Task 3: Wire mise run load task</name>
  <read_first>
    - mise.toml (existing [tasks.*] entries — extend, do not reorder)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-04.1 — `mise run load` task wraps scripts/load.sh)
  </read_first>
  <action>
    Append to `mise.toml` (after the existing `[tasks."demo:order"]` block, before `[tasks."verify:bom"]`):

    ```toml
    # ──────────────────────────────────────────────────────────────────
    # Continuous load (Phase 7 / WORK-03 / D-04.1)
    # ──────────────────────────────────────────────────────────────────
    [tasks.load]
    description = "Continuous POST /orders load (~1 req/sec, ~50/50 express/standard). Run alongside `mise run dev`. Ctrl-C stops both child loaders."
    run = "./scripts/load.sh"
    ```

    Do NOT add `depends = ["dev"]` — the user starts dev in one terminal then load in another;
    coupling them creates a circular-startup expectation. Do NOT reorder existing tasks.
    Existing `[tasks.preflight]`, `[tasks."infra:up"]`, `[tasks."dev:*"]`, `[tasks."demo:order"]`,
    `[tasks."verify:bom"]`, `[tasks."ui:grafana"]`, `[tasks."ui:rabbitmq"]` UNCHANGED.
  </action>
  <verify>
    <automated>
      grep -q '\[tasks.load\]' mise.toml \
      && grep -q 'run = "./scripts/load.sh"' mise.toml \
      && mise tasks 2>&1 | grep -qE '^load\b' \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `mise.toml` contains the literal substring `[tasks.load]`
    - `mise.toml` contains the literal substring `run = "./scripts/load.sh"`
    - `mise tasks` lists `load` as a registered task
    - Existing `[tasks.preflight]` block UNCHANGED
    - Existing `[tasks."demo:order"]` block UNCHANGED
    - No `depends = [...]` array on `[tasks.load]`
  </acceptance_criteria>
  <done>
    `mise run load` is wired and discoverable via `mise tasks`. Workshop instructor can now run
    `mise run dev` in one terminal and `mise run load` in another.
  </done>
</task>

<task type="auto">
  <name>Task 4: Smoke-verify end-to-end load flow</name>
  <read_first>
    - scripts/load.sh (just authored)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (WORK-03 success criterion)
  </read_first>
  <action>
    Step 1 — Ensure infra + apps are running:
    ```bash
    mise run infra:up
    # in another terminal:
    mise run dev
    ```
    If apps are already running, skip.

    Step 2 — Smoke-test the load script for ~30 seconds in foreground (we want to see `oha`'s TUI
    if it draws one; in a non-tty shell it will print summary lines instead):
    ```bash
    timeout 30 mise run load || true
    ```
    Expected: `oha` (or `hey`) starts; ~30 seconds of `POST /orders -> 202` traffic flows; `timeout`
    sends SIGTERM which the trap converts to a clean child-kill.

    Step 3 — Verify post-cleanup that no orphan `oha`/`hey` processes remain:
    ```bash
    pgrep -af 'oha |hey ' | grep -v grep || echo "no orphans (expected)"
    ```
    If the output is "no orphans", the trap worked.

    Step 4 — Confirm producer received traffic by tailing recent producer log lines or hitting
    `/actuator/metrics/http.server.request.duration` to see request count > some baseline (small):
    ```bash
    curl -sf http://localhost:8081/actuator/health 2>/dev/null | head -c 200
    ```
    (consumer health is a smoke check that infra is up; the actual proof of load flow lives in
    Wave 2's screenshot capture which will show populated dashboard panels)

    Record results in SUMMARY.md.
  </action>
  <verify>
    <automated>
      # Static-only re-verify; runtime verification was done above
      test -x scripts/load.sh \
      && grep -q '\[tasks.load\]' mise.toml \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - 30-second smoke run completes without error (timeout-driven SIGTERM is OK)
    - `pgrep -af 'oha |hey '` shows zero orphan processes after the smoke run terminates
    - SUMMARY.md records the smoke-run output (RPS observed, any oha TUI lines if captured)
  </acceptance_criteria>
  <done>
    Load script smoke-verified end-to-end: produces traffic, terminates cleanly via SIGTERM, no
    orphan child processes. WORK-03 success criterion `mise run load -> continuously-flowing
    telemetry` is satisfied at the source level (full live verification with all three signals
    populated lands in plan 07-04 screenshot capture, which uses this load script as a dependency).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Workshop laptop -> producer-service localhost:8080 | Internal-only; no remote ingress |
| `scripts/load.sh` -> oha child processes | Trusted authored content; payload hardcoded; no env-var-driven user input flows into the request body |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-02-01 | Tampering | scripts/load.sh future patch parameterizing payload from env vars | mitigate | Comments in scripts/load.sh state "do NOT add env-var-driven RPS/DURATION/payload args without re-validating shell-injection surface"; D-04 explicitly forbids env-var arg parsing in v1 |
| T-07-02-02 | Information Disclosure | Hardcoded JSON payload | accept | Payload contains synthetic SKU/quantity/priority — no PII, no secrets, no attacker-supplied content |
| T-07-02-03 | Denial of Service | ~1 req/sec rate against localhost producer | accept | Workshop laptop absorbs trivially; rate is intentionally low (one demo-friendly req/sec, not a benchmark) |
| T-07-02-04 | Elevation of Privilege | Script executes oha (or hey) binary | mitigate | Binary installed via mise — pinned and reproducible across team members; no `curl <url> | sh` install pattern |
</threat_model>

<verification>
- `scripts/load.sh` exists, is executable, parses cleanly with `bash -n`.
- `mise.toml` registers the `load` task; `mise tasks` lists it.
- 30-second smoke run produces traffic and terminates cleanly via SIGTERM trap.
- No orphan `oha`/`hey` processes after smoke run.
- WORK-03 success criterion verified end-to-end during plan 07-04 (screenshot capture uses this script).
</verification>

<success_criteria>
- `scripts/load.sh` exists, executable, two parallel oha invocations + trap (D-04 verbatim).
- `mise.toml` `[tools]` includes load tool (oha / hey / cargo:oha — Task 1 chosen).
- `mise.toml` `[tasks.load]` runs `./scripts/load.sh`.
- Smoke verification clean (Task 4).
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-02-SUMMARY.md` recording:
- Resolution path chosen for load tool (A/B/C/D from Task 1)
- Smoke-run output (RPS observed, trap behavior, any orphan-process state)
- Any deviations from D-04 verbatim (e.g., hey CLI flag substitution)
</output>
