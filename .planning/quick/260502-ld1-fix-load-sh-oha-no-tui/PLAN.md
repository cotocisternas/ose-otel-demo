---
slug: ld1-fix-load-sh-oha-no-tui
type: quick
created: 2026-05-02
---

# Fix scripts/load.sh — oha TUI breaks parallel invocation

## Problem

`scripts/load.sh` runs two `oha` processes in parallel (one per priority) without
`--no-tui`. oha defaults to a 16fps TUI rendered via the terminal's alternate
screen buffer. Two TUIs writing to the same TTY corrupt each other and the user
sees a broken/empty display.

Additionally `-z 0` is not a valid duration argument for oha — `-z` requires a
real duration like `5s`, `3m`, `24h`. The script as written likely runs zero
requests (or is rejected outright by recent oha versions).

User confirmed `oha -z 3600s -c 100 -q 500 ...` works fine for a one-off stress
test, so the binary itself is healthy — only `load.sh` is broken.

## Decision: oha vs k6

Keep oha. Reasoning:

- Demo workload is 0.5 rps per priority — well below the threshold where k6's
  scenario/threshold tooling pays for itself.
- k6 would require a JS scenario file plus committing to a second tool.
- The current shape (two parallel processes, one per priority) maps cleanly to
  the dashboard's "Orders Created by Priority" panel; switching to k6 scenarios
  doesn't simplify that.
- oha is already what the user reaches for ad-hoc.

## Fix

1. Add `--no-tui` to both oha invocations.
2. Replace `-z 0` with a finite-but-long duration (`-z 24h`) so the script runs
   effectively forever until Ctrl-C; the existing trap still cleans up children.
3. Add a bash-level heartbeat line every ~30s so the operator sees the script
   is alive (otherwise `--no-tui` is silent until completion).
4. Document the change inline.

## Verification

- `bash -n scripts/load.sh` — syntax OK
- `shellcheck scripts/load.sh` if available — no new findings
- Run the script for ~10s against the running producer, confirm:
  - No TUI corruption
  - Heartbeat visible
  - Orders reach RabbitMQ (check management UI count delta or logs)
- Ctrl-C cleanly stops both children
