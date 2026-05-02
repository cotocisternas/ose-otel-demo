---
phase: 07-polish-differentiators
plan: 07
type: execute
wave: 6
depends_on: [07-01, 07-02, 07-03, 07-04, 07-05, 07-06]
files_modified:
  - .planning/STATE.md
  - .planning/ROADMAP.md
  - .planning/REQUIREMENTS.md
autonomous: false
requirements: [WORK-02, WORK-03, DOC-01, DOC-04]
risk: medium
tags: [exit-gate, state-flip, no-tag, d-09]

must_haves:
  truths:
    - "All 4 ROADMAP Phase 7 success criteria verified end-to-end (live infra + visual + content checks)"
    - "STATE.md reflects Phase 7 complete; current-position points past Phase 7"
    - "ROADMAP.md Phase 7 row marked Shipped (no tag — per D-09)"
    - "REQUIREMENTS.md WORK-02 / WORK-03 / DOC-01 / DOC-04 all marked Complete"
    - "NO step-07-* git tag is applied (D-09 — explicit)"
    - "Atomic commit lands STATE/ROADMAP/REQUIREMENTS together with the polish-merge state"
  artifacts:
    - path: ".planning/STATE.md"
      provides: "Phase 7 completion marker"
      contains: "Phase 7"
    - path: ".planning/ROADMAP.md"
      provides: "Phase 7 row flipped to Shipped"
      contains: "Shipped"
    - path: ".planning/REQUIREMENTS.md"
      provides: "Four Phase-7 requirements marked complete"
      contains: "[x] **WORK-02**"
  key_links:
    - from: ".planning/STATE.md"
      to: "Phase 7 Plans complete"
      via: "Progress table row"
      pattern: "7\\..*Polish.*Shipped"
---

<objective>
Validate all 4 ROADMAP Phase 7 success criteria end-to-end against the live workshop, then atomically flip STATE.md / ROADMAP.md / REQUIREMENTS.md to mark Phase 7 complete in a single commit. Per CONTEXT.md D-09: NO Phase 7 git tag is applied — main HEAD past `step-06-tests` IS the polish state.

Purpose: Phase exit gates have followed the same pattern across Phases 2-6 (smoke verify all SCs simultaneously green, atomic status-flip commit, then orchestrator applies the annotated tag). Phase 7 explicitly DEPARTS from that pattern at the tag step (D-09) — this plan is the same atomic-flip commit MINUS the tag-apply step. The README's final paragraph (landed by plan 07-06) carries the prescribed close: "Workshop is at main HEAD past step-06-tests; ..."

Output: STATE.md / ROADMAP.md / REQUIREMENTS.md flipped; SUMMARY.md recording all 4 success criteria results; one atomic commit. No git tag.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@README.md
@docker-compose.yml
@mise.toml
@scripts/load.sh
@scripts/screenshots/capture.mjs
@grafana/dashboards/dashboards.yaml
@grafana/dashboards/ose-otel-demo.json
@CLAUDE.md
</context>

<tasks>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 1: Smoke-verify all 4 ROADMAP Phase 7 success criteria simultaneously green</name>
  <what-built>
    Phase 7 source-complete: dashboard auto-provisioned (07-01), load script wired (07-02),
    screenshot tooling + 6-8 PNGs (07-03/07-04), full README rewrite to 5-section template + Concepts
    & FAQ appendix + D-09 final paragraph (07-05/07-06).
  </what-built>
  <how-to-verify>
    Run all four SC checks in sequence at the live stack. Stop and investigate any failure before
    approving.

    **SC #1 (WORK-02): pre-provisioned dashboard at known path with live three-signal data**
    1. `mise run infra:down` (clean slate)
    2. `mise run infra:up` (wait ~30s for healthcheck green)
    3. `mise run dev` (in second terminal — wait for both apps "Started" log lines)
    4. `mise run load` (in third terminal — let it run for 60s)
    5. Open Grafana: `mise run ui:grafana` → log in `admin/admin`
    6. Navigate to Dashboards. Confirm `OSE OTel Demo — Three Signals` is auto-listed (no manual import).
    7. Open dashboard. Confirm:
       - Top row's 4 panels populated (Tempo trace search shows traces; service graph shows nodes; RED metrics shows non-zero rate; Loki shows log lines).
       - Second row collapsed; expanding it shows per-priority breakdown + error span count + raw logs.

    **SC #2 (WORK-03): scripts/load.sh produces continuously-flowing telemetry**
    With load.sh still running from SC #1:
    1. Tempo Explore: confirm new traces appearing every ~1-2s.
    2. Mimir Explore: `rate(orders_created_total[1m])` shows non-zero rate.
    3. Loki Explore: `{service_name=~"order-.*"}` shows new log lines per second.
    4. Press Ctrl-C in the load.sh terminal. Run `pgrep -af 'oha |hey '` — should be empty (clean trap).

    **SC #3 (DOC-04): README readable start-to-finish; broken/fixed pair side-by-side**
    1. Open `README.md` in a markdown previewer (GitHub web, or `glow README.md`, or VS Code preview).
    2. Read top-to-bottom WITHOUT running any code:
       - Prerequisites + Workshop checkpoints summary read smoothly.
       - All 6 Steps read smoothly in the same voice (5-section template applied uniformly).
       - Step 2 + Step 3 share the broken/fixed PNG pair side-by-side — visually compelling.
    3. Confirm the markdown previewer renders the HTML `<table>` block correctly (PNGs sit side-by-side).

    **SC #4 (DOC-01): every step has paired README block with copy-pasteable curl/mise commands**
    1. For each tag step-01-* through step-06-*, confirm the README's matching `## Step N` section contains a fenced-code block in `### Run` with at least one `mise run <task>` or `curl ...` command.
    2. Pick step-04-metrics: `git checkout step-04-metrics`; follow the README Step 4 *Run* block; confirm reproducibility (orders flow, dashboard panels populate).
    3. Return to main: `git checkout main`.

    Record each SC's status (✅ green / ❌ failed + remediation needed) in this checkpoint's resume signal.
  </how-to-verify>
  <resume-signal>Type "approved" if SC#1, SC#2, SC#3, SC#4 all green. Or describe which failed and remediation taken.</resume-signal>
</task>

<task type="auto">
  <name>Task 2: Update STATE.md — Phase 7 complete</name>
  <read_first>
    - .planning/STATE.md (current state, last_activity, progress numbers)
    - .planning/ROADMAP.md (phase 7 row format)
  </read_first>
  <action>
    Edit `.planning/STATE.md`:

    Step 1 — Update YAML frontmatter:
    - `stopped_at: "Phase 7 source-complete"` → `stopped_at: "Phase 7 shipped (no tag — D-09)"`
    - `last_updated: "<old>"` → today's ISO datetime
    - `last_activity: "<old>"` → `2026-05-02 -- Phase 07 polish-and-differentiators source-complete (4/4 SC green); D-09 honored: NO step-07-* tag applied`
    - `progress.completed_phases: 6` → `7`
    - `progress.total_plans: 34` → `41` (or whatever the new total is — recompute as `34 + 7` for the seven Phase 7 plans)
    - `progress.completed_plans: 35` → `42` (or current+7)
    - `progress.percent: 100` (already 100 — leave as-is OR adjust to whatever STATE.md schema expects for v1.0 milestone-completion percent)

    Step 2 — Update body sections:
    - "Current Position" Phase: change to `Phase: 07 — SHIPPED (no tag — D-09); milestone v1.0 complete (subject to /gsd-complete-milestone)`
    - "Current Position" Plan: change to `Plan: 7 of 7`
    - "Current Position" Status: `Phase 07 source-complete; all 4 ROADMAP success criteria verified green at live stack; D-09 honored: NO step-07-* tag applied`
    - "Current Position" Last activity: same as `last_activity` from frontmatter

    Step 3 — Append to "Accumulated Context > Decisions" list:
    ```
    - [Phase 07-07]: All 4 ROADMAP Phase 7 SC verified simultaneously green at live stack: SC#1 dashboard auto-provisions and renders all 3 signals; SC#2 scripts/load.sh sustains ~1 req/sec with clean Ctrl-C trap; SC#3 README readable end-to-end with DOC-04 broken/fixed pair side-by-side via HTML <table>; SC#4 every step has paired README block with copy-pasteable mise/curl commands.
    - [Phase 07-07]: D-09 honored — NO step-07-* git tag applied. README final paragraph closes with: "Workshop is at main HEAD past step-06-tests; ..." per D-09 verbatim. STATE/ROADMAP/REQUIREMENTS flipped atomically with the polish-merge commit (Phase 2-06 / 6-06 precedent minus tag-apply step).
    ```

    Do NOT modify "Performance Metrics", "Recent Trend", or per-plan duration tables — those
    auto-update via the executor; if they need updates the orchestrator handles them.
  </action>
  <verify>
    <automated>
      grep -q 'Phase 7' .planning/STATE.md \
      && grep -q 'D-09' .planning/STATE.md \
      && grep -q 'NO step-07' .planning/STATE.md \
      && grep -qE 'completed_phases: *7' .planning/STATE.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - STATE.md frontmatter `completed_phases: 7`
    - STATE.md body contains "Phase 7" with "SHIPPED" or "shipped" status
    - STATE.md decisions list includes the D-09 callout (NO step-07-* tag applied)
    - last_activity / last_updated reflect today's date
  </acceptance_criteria>
  <done>
    STATE.md reflects Phase 7 ship state without an associated tag.
  </done>
</task>

<task type="auto">
  <name>Task 3: Update ROADMAP.md — Phase 7 row flipped to Shipped (no tag)</name>
  <read_first>
    - .planning/ROADMAP.md (current Phase 7 row, Progress table format)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-09 — no tag)
  </read_first>
  <action>
    Step 1 — Update the Phases summary list at the top of ROADMAP.md:
    Change the existing line:
    ```
    - [ ] **Phase 7: Polish & Differentiators** — Pre-built dashboard, load script, screenshots, full README walkthrough
    ```
    to:
    ```
    - [x] **Phase 7: Polish & Differentiators** *(shipped 2026-05-02; no tag per D-09 — main HEAD past step-06-tests IS the polish state)* — Pre-built dashboard, load script, screenshots, full README walkthrough
    ```

    Step 2 — Update the Phase 7 details section. Per the existing template structure, find the
    `### Phase 7: Polish & Differentiators` block. The "Plans" sub-block currently mirrors Phase 6's
    plans (a copy-paste from Phase 6 in the existing ROADMAP). Replace it with the actual Phase 7
    plan list:

    ```markdown
    **Plans** (7 plans, 4 waves):
    - **Wave 1** *(parallelizable, no dependencies)*
      - [x] `07-01-grafana-dashboard-provisioning` — WORK-02 — `grafana/dashboards/{ose-otel-demo.json,dashboards.yaml}` + docker-compose volume mount; two-row layout per D-02
      - [x] `07-02-load-script` — WORK-03 — `scripts/load.sh` (two parallel oha invocations + SIGINT/SIGTERM trap, D-04) + `mise run load` task wiring + oha/hey pinned in mise.toml
      - [x] `07-03-screenshot-tooling-scaffold` — DOC-04 — `scripts/screenshots/{package.json,capture.mjs,.gitignore}` + `mise run docs:screenshots` task scaffold; Playwright pinned for reproducibility
    - **Wave 2** *(blocked on 07-01/02/03)*
      - [x] `07-04-screenshot-capture` — DOC-04 — Tag-cycling driver via git worktree; produces 6-8 PNGs in docs/screenshots/ committed to git
    - **Wave 3** *(blocked on 07-04)*
      - [x] `07-05-readme-steps-1-2-3` — DOC-01, DOC-04 — Steps 1/2/3 written from scratch in lean 5-section template; DOC-04 broken/fixed PNG pair side-by-side via HTML <table> in Step 2
      - [x] `07-06-readme-steps-4-5-6-and-appendix` — DOC-01 — Steps 4/5/6 rewritten to fit template; four standalone narrative sections consolidated as ## Concepts & FAQ appendix; D-09 final paragraph appended
    - **Wave 4** *(blocked on 07-05/06; contains human checkpoint)*
      - [x] `07-07-exit-gate` — WORK-02, WORK-03, DOC-01, DOC-04 — Smoke-verify all 4 ROADMAP SCs simultaneously green; STATE/ROADMAP/REQUIREMENTS atomic flip; D-09 honored (NO git tag)

    **Cross-cutting constraints** *(must_haves shared across plans)*:
    - D-01 / D-02 (auto-provisioned two-row dashboard with all 3 signals — WORK-02 SC #1)
    - D-04 (two parallel oha invocations + trap — WORK-03 SC #1)
    - D-06 (DOC-04 broken/fixed pair — Step 2 + Step 3 README embed; per-step PNGs for Steps 1/4/5/6)
    - D-07 (D-07 invariants list — every Phase 4/5/6 pedagogical fact preserved verbatim or as Concepts & FAQ cross-reference)
    - D-08 (lean 5-section template applied uniformly across all 6 Steps)
    - D-09 (NO step-07-* git tag — main HEAD past step-06-tests IS the polish state)
    ```

    Step 3 — Update the Progress table:
    ```
    | 7. Polish & Differentiators | 7/7 | Shipped (no tag — D-09) | 2026-05-02 |
    ```

    Step 4 — Confirm Research Flags section is unchanged for Phase 7 (it was already listed under
    "Phases that can plan directly" — leave as-is).
  </action>
  <verify>
    <automated>
      grep -q '\[x\] \*\*Phase 7: Polish' .planning/ROADMAP.md \
      && grep -q '07-07-exit-gate' .planning/ROADMAP.md \
      && grep -qE 'D-09|no tag' .planning/ROADMAP.md \
      && grep -q '7\. Polish.*Shipped' .planning/ROADMAP.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - ROADMAP.md Phases summary list: Phase 7 row marked `[x]` with `(shipped 2026-05-02; no tag per D-09 ...)`
    - ROADMAP.md Phase 7 detail section's Plans block lists all 7 Phase 7 plans (07-01..07-07) marked `[x]`
    - ROADMAP.md Progress table Phase 7 row: `Shipped (no tag — D-09)`, completion date 2026-05-02
    - "Cross-cutting constraints" block referencing D-01..D-09
  </acceptance_criteria>
  <done>
    ROADMAP.md reflects Phase 7 shipped, plan structure correctly recorded, no tag invariant
    documented.
  </done>
</task>

<task type="auto">
  <name>Task 4: Update REQUIREMENTS.md — mark WORK-02, WORK-03, DOC-01, DOC-04 Complete</name>
  <read_first>
    - .planning/REQUIREMENTS.md (current Phase 7 requirement entries — all currently `[ ]` Pending)
  </read_first>
  <action>
    Edit `.planning/REQUIREMENTS.md`:

    Step 1 — Flip the four Phase 7 requirement bullets from `[ ]` to `[x]`:
    - `- [ ] **WORK-02**: ...` → `- [x] **WORK-02**: ...` (rest unchanged)
    - `- [ ] **WORK-03**: ...` → `- [x] **WORK-03**: ...`
    - `- [ ] **DOC-01**: ...` → `- [x] **DOC-01**: ...`
    - `- [ ] **DOC-04**: ...` → `- [x] **DOC-04**: ...`

    Step 2 — Flip the corresponding rows in the Traceability table at the bottom:
    - `| WORK-02 | Phase 7 | Pending |` → `| WORK-02 | Phase 7 | Complete |`
    - `| WORK-03 | Phase 7 | Pending |` → `| WORK-03 | Phase 7 | Complete |`
    - `| DOC-01 | Phase 7 | Pending |` → `| DOC-01 | Phase 7 | Complete |`
    - `| DOC-04 | Phase 7 | Pending |` → `| DOC-04 | Phase 7 | Complete |`

    Step 3 — Update "Last updated" footer:
    `*Last updated: 2026-04-29 by gsd-roadmapper — traceability filled, judgment calls documented*`
    →
    `*Last updated: 2026-05-02 — Phase 7 shipped (no tag per D-09); WORK-02 + WORK-03 + DOC-01 + DOC-04 complete*`

    No other rows change.
  </action>
  <verify>
    <automated>
      grep -q '\[x\] \*\*WORK-02\*\*' .planning/REQUIREMENTS.md \
      && grep -q '\[x\] \*\*WORK-03\*\*' .planning/REQUIREMENTS.md \
      && grep -q '\[x\] \*\*DOC-01\*\*' .planning/REQUIREMENTS.md \
      && grep -q '\[x\] \*\*DOC-04\*\*' .planning/REQUIREMENTS.md \
      && grep -q '| WORK-02 | Phase 7 | Complete |' .planning/REQUIREMENTS.md \
      && grep -q '| WORK-03 | Phase 7 | Complete |' .planning/REQUIREMENTS.md \
      && grep -q '| DOC-01 | Phase 7 | Complete |' .planning/REQUIREMENTS.md \
      && grep -q '| DOC-04 | Phase 7 | Complete |' .planning/REQUIREMENTS.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - All four Phase 7 requirements `[x]` in their checkbox bullets
    - All four Phase 7 rows `Complete` in the Traceability table
    - Footer reflects Phase 7 ship + D-09 callout
  </acceptance_criteria>
  <done>
    All four Phase 7 requirements marked complete; REQUIREMENTS.md atomic with STATE+ROADMAP flip.
  </done>
</task>

<task type="auto">
  <name>Task 5: Atomic commit + final invariant check (NO TAG per D-09)</name>
  <read_first>
    - .planning/STATE.md (post Task 2)
    - .planning/ROADMAP.md (post Task 3)
    - .planning/REQUIREMENTS.md (post Task 4)
    - README.md (already shipped by 07-05/06)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-09 — explicit "no tag")
  </read_first>
  <action>
    Step 1 — Verify clean working tree before commit (only the planning artifacts should be staged):
    ```bash
    git status
    ```
    Expected modified files (no untracked surprises):
    - `.planning/STATE.md`
    - `.planning/ROADMAP.md`
    - `.planning/REQUIREMENTS.md`
    - `.planning/phases/07-polish-differentiators/07-07-SUMMARY.md` (after this task creates it)

    All other Phase 7 artifacts (README.md, docker-compose.yml, mise.toml, scripts/*, grafana/*,
    docs/screenshots/*) should ALREADY be committed by their respective plans.

    Step 2 — Final invariant check before committing:
    ```bash
    # CRITICAL: D-09 — NO step-07-* tag must exist.
    if git tag -l 'step-07-*' | grep -q 'step-07'; then
      echo "ERROR: D-09 violation — step-07-* tag found. Phase 7 must not have a tag."
      git tag -l 'step-07-*'
      exit 1
    fi
    echo "D-09 confirmed: no step-07-* tag exists."

    # README D-09 final paragraph present.
    grep -q 'Workshop is at main HEAD past' README.md \
      && grep -q 'step-06-tests' README.md \
      && grep -q 'git checkout step-NN' README.md \
      || { echo "ERROR: README D-09 final paragraph missing"; exit 1; }
    echo "README D-09 final paragraph confirmed."

    # All 6 step PNGs present.
    for png in step-01-empty-tempo step-02-disconnected-traces step-03-joined-trace \
               step-04-metrics step-05-logs-trace-jump step-06-test-output; do
      test -f "docs/screenshots/${png}.png" || { echo "ERROR: missing PNG ${png}"; exit 1; }
    done
    echo "All 6 required PNGs present."

    # Dashboard auto-provision artifacts.
    test -f grafana/dashboards/ose-otel-demo.json
    test -f grafana/dashboards/dashboards.yaml
    grep -q './grafana/dashboards:' docker-compose.yml
    echo "Dashboard provisioning confirmed."

    # Load script.
    test -x scripts/load.sh
    grep -q '\[tasks.load\]' mise.toml
    echo "Load script confirmed."
    ```

    Step 3 — Commit the atomic flip:
    ```bash
    git add .planning/STATE.md .planning/ROADMAP.md .planning/REQUIREMENTS.md \
            .planning/phases/07-polish-differentiators/07-07-SUMMARY.md

    git status   # confirm only those 4 files are staged

    git commit -m "$(cat <<'EOF'
    docs(07): mark Phase 7 polish-and-differentiators shipped (no tag — D-09)

    All 4 ROADMAP success criteria verified green at the live stack:
    - WORK-02: pre-provisioned dashboard auto-loads, all 3 signals visible
    - WORK-03: scripts/load.sh sustains ~1 req/sec with clean Ctrl-C trap
    - DOC-04: README readable end-to-end; broken/fixed PNG pair side-by-side
    - DOC-01: every step has paired README block with copy-pasteable commands

    D-09 honored: NO step-07-* git tag applied. Main HEAD past step-06-tests
    IS the polish state. Atomic flip of STATE/ROADMAP/REQUIREMENTS lands
    here without a tag-apply step (Phase 2-06 / 6-06 precedent minus tag).
    EOF
    )"
    ```

    Step 4 — CRITICAL: do NOT run `git tag` for any step-07-* name. Phase 7 explicitly does not
    receive an annotated tag. If the user asks "should I tag this?", the answer is "no — D-09".

    Step 5 — Run a post-commit invariant check:
    ```bash
    git log -1 --format='%h %s'
    git tag -l 'step-07-*'   # MUST be empty
    git status               # MUST be clean
    ```
  </action>
  <verify>
    <automated>
      # Final post-commit check — D-09 invariant
      ! git tag -l 'step-07-*' | grep -q 'step-07' \
      && [ -z "$(git status --porcelain | grep -v '^??' | grep -vE '07-07-SUMMARY|07-07-PLAN')" ] \
      && git log -1 --format='%s' | grep -q 'Phase 7' \
      || (echo "VERIFY FAILED — D-09 tag invariant or commit shape" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - Atomic commit lands STATE.md + ROADMAP.md + REQUIREMENTS.md + 07-07-SUMMARY.md
    - Commit message references "Phase 7", "no tag", and the four SC names
    - `git tag -l 'step-07-*'` returns empty (D-09 invariant)
    - `git status` is clean after commit (no leftover untracked planning artifacts)
    - All 6 required PNGs in docs/screenshots/ (re-checked)
    - Dashboard JSON + provisioning manifest + docker-compose mount present (re-checked)
    - Load script + mise task present (re-checked)
    - README D-09 final paragraph present (re-checked)
  </acceptance_criteria>
  <done>
    Phase 7 shipped. Milestone v1.0 source-complete. No `step-07-*` tag exists. Workshop
    artifact is delivery-ready at main HEAD past step-06-tests.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Planning artifacts -> git commit | Atomic commit with explicit file list (no `git add -A`) |
| D-09 tag invariant | Verified pre-commit AND post-commit; no tag-apply path in this plan |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-07-01 | Tampering | Accidental `git tag step-07-*` application | mitigate | Task 5 invariant check confirms zero step-07-* tags pre-commit AND post-commit; commit message explicitly references "no tag — D-09" |
| T-07-07-02 | Spoofing | Atomic flip-commit message accuracy | mitigate | Heredoc commit message is verbatim; references the four ROADMAP SCs by name + D-09 |
| T-07-07-03 | Information Disclosure | STATE.md / SUMMARY.md content | accept | All content is project-internal planning artifacts; no secrets, no untrusted input |
| T-07-07-04 | Denial of Service | Pre-commit verification suite | accept | Lightweight grep gates; sub-second runtime |
</threat_model>

<verification>
- All 4 ROADMAP Phase 7 success criteria verified green at live stack (Task 1 human-verify checkpoint).
- STATE / ROADMAP / REQUIREMENTS atomic flip committed.
- NO step-07-* git tag (D-09).
- README D-09 final paragraph present.
- 6 required PNGs in docs/screenshots/.
- Dashboard auto-provision artifacts present.
- Load script + mise task present.
</verification>

<success_criteria>
- ROADMAP Phase 7 SC #1 (WORK-02): pre-provisioned dashboard with 3 signals — verified.
- ROADMAP Phase 7 SC #2 (WORK-03): continuous-load produces flowing telemetry — verified.
- ROADMAP Phase 7 SC #3 (DOC-04): broken/fixed pair side-by-side in README — verified.
- ROADMAP Phase 7 SC #4 (DOC-01): every step has paired README block — verified.
- D-09 honored: no step-07-* git tag; README closes per D-09 verbatim.
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-07-SUMMARY.md` recording:
- Per-SC verification results (timestamps, observed metrics, screenshots if useful)
- Confirmation that no step-07-* tag was applied
- Total Phase 7 wall-clock duration (sum across plans 07-01 .. 07-07)
- Any deferred items surfaced during the verification (e.g., "first cohort feedback may revisit X")
- Pointer to /gsd-complete-milestone for v1.0 milestone-completion artifacts (if the user wants a milestone-level v1.0 tag, that lives in /gsd-complete-milestone, separate from phase exit gates per D-09)
</output>
