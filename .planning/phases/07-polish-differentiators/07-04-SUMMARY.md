---
phase: 07-polish-differentiators
plan: 04
subsystem: docs/screenshots
tags: [screenshots, capture, git-worktree, playwright, doc-04]
status: paused-at-checkpoint
requires:
  - 07-01 (dashboard + provisioning) — for step-04 dashboard panel screenshot
  - 07-02 (load script) — for traffic to populate panels
  - 07-03 (capture.mjs scaffold + npm package + minimal mise task) — for the capture script being driven
provides:
  - tag-cycling driver in mise.toml `[tasks."docs:screenshots"]`
  - CAPTURE_TAG_FILTER env-var support in capture.mjs (per-tag invocation)
  - (pending Task 2 + Task 3) docs/screenshots/*.png — DOC-04 anchor pair + per-step PNGs
affects:
  - mise.toml
  - scripts/screenshots/capture.mjs
tech-stack:
  added: []
  patterns:
    - per-tag git-worktree isolation (D-05 — main checkout untouched during capture cycle)
    - CAPTURE_TAG_FILTER env filter (one capture.mjs invocation per tag)
    - cleanup-trap on bg processes (T-07-04-02 mitigation)
key-files:
  created: []
  modified:
    - mise.toml
    - scripts/screenshots/capture.mjs
decisions:
  - Tag-cycling lives in mise driver (bash) so the cleanup trap can pkill bg JVMs reliably; capture.mjs stays a single-iteration Playwright runner per tag
  - CAPTURE_TAG_FILTER chosen over per-tag config files — keeps capture.mjs's CAPTURES array as the single source of truth for what gets captured
metrics:
  completed: 2026-05-02
  duration: paused-at-task-2-checkpoint
  tasks-complete: 1
  tasks-total: 3
---

# Phase 7 Plan 04: Screenshot Capture Pipeline Summary

Tag-cycling driver wired; high-blast-radius capture run paused at human-verify checkpoint pending operator approval (Task 2 is `gate="blocking"`).

## What Was Built (Task 1)

### Tag-cycling driver (`mise.toml [tasks."docs:screenshots"]`)
- Replaces the minimal Playwright-only task from plan 07-03 with a full driver that:
  - Creates `.screenshots-worktree/` via `git worktree add` per iteration (D-05 workspace isolation; T-07-04-01 mitigation)
  - Brings up infra ONCE before the loop (same lgtm/rabbitmq containers serve every tag)
  - For each of the six `step-NN-*` tags: builds Maven, starts `mise run dev` in background, sleeps 25s for Spring Boot startup, starts `scripts/load.sh` in background (skipped for `step-01-baseline`), sleeps 30s for warmup, invokes `capture.mjs` with `CAPTURE_TAG_FILTER=$tag`, then tears down dev + load + worktree
  - `cleanup()` trap on EXIT kills `spring-boot:run`, `oha`, `hey` bg processes and removes the worktree (T-07-04-02 mitigation)
  - Build failures at any step abort the pipeline with `exit 1` (no silent-mask)

### CAPTURE_TAG_FILTER support (`scripts/screenshots/capture.mjs`)
- `main()` reads `process.env.CAPTURE_TAG_FILTER` and filters `CAPTURES` to entries whose `tag` matches before running the loop
- Without the filter, every per-tag invocation would attempt all seven captures against the wrong checkout
- Logs the filter setting + filtered count for observability

## Status

**Task 1: COMPLETE** (commit `ddd372e`)
- Acceptance criteria: all 7 verify clauses pass (`git worktree`, `CAPTURE_TAG_FILTER` in both files, `TAGS=(step-01-baseline...)` array, `cleanup()` trap, `bash -n scripts/load.sh`, `node --check scripts/screenshots/capture.mjs`)

**Task 2: PAUSED — checkpoint:human-verify (gate=blocking)**
- Pre-flight requirements: clean main checkout, no `.screenshots-worktree` leftover, infra down, ≥5GB free disk
- Pipeline command: `mise run docs:screenshots 2>&1 | tee /tmp/docs-screenshots.log`
- Expected wall-clock: 15–30 minutes (Playwright Chromium install ~3 min first time, six tag iterations ~2–4 min each)
- Manual verification of each PNG required (DOC-04 anchor pair = step-02 disconnected vs step-03 joined is the pedagogically load-bearing visual)

**Task 3: PENDING** — commit PNGs once Task 2 produces them; add `.screenshots-worktree*` to `.gitignore`.

## Why This Plan Is Non-Autonomous

The capture pipeline is a high-blast-radius operation:
- Cycles git tags via worktree (T-07-04-01: tampering risk if a build interrupts mid-checkout)
- Brings up infra + dev (Spring Boot 3.4.13 reactor, two services) per tag — six full build+startup cycles
- Runs `scripts/load.sh` against `localhost:8080` — could collide with operator's own running services
- Captures PNGs whose semantic correctness only a human can verify (Tempo's "no traces" empty state vs an actual capture failure look identical to a script; only an inspecting human can confirm step-02 truly shows two disconnected trace IDs and step-03 truly shows one joined trace)

Per `D-05` and the plan's `<task type="checkpoint:human-verify" gate="blocking">` directive, the run is operator-driven; the human-verify checkpoint forces visual inspection of each PNG before commit (T-07-04-04 mitigation).

## Files Modified

| File | Change |
|------|--------|
| `mise.toml` | `[tasks."docs:screenshots"]` body replaced with tag-cycling driver (~70 lines) |
| `scripts/screenshots/capture.mjs` | `main()` reads `CAPTURE_TAG_FILTER`, filters `CAPTURES` (5 lines added) |

## Commits

| Task | Hash | Message |
|------|------|---------|
| 1 | `ddd372e` | `feat(07-04): tag-cycling driver for docs:screenshots` |
| 2 | (pending) | (operator runs `mise run docs:screenshots`; PNGs committed in Task 3) |
| 3 | (pending) | (`docs(07-04): capture per-step screenshots (DOC-04 / D-06)` + optional `chore(07-04): gitignore screenshot capture worktree`) |

## Deviations from Plan

None — Task 1 executed exactly as the plan's `<action>` block specified.

## Self-Check: PARTIAL — paused at blocking checkpoint

- [x] Task 1 acceptance criteria all pass (verified via `<verify>` block)
- [x] Task 1 committed (`ddd372e` — present in `git log`)
- [ ] Task 2: requires operator-driven capture run (blocking checkpoint by plan design)
- [ ] Task 3: requires Task 2 outputs

This SUMMARY will be amended once Task 2 + Task 3 complete and the final commit is made by the resume agent.
