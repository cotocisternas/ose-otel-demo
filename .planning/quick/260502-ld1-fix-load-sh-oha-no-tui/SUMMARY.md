---
slug: ld1-fix-load-sh-oha-no-tui
type: quick
status: complete
created: 2026-05-02
completed: 2026-05-02
---

# Summary — Fix scripts/load.sh oha TUI corruption

## What changed

`scripts/load.sh`:

- Added `--no-tui` to both `oha` invocations so two parallel children don't fight
  over the terminal alternate-screen buffer.
- Replaced invalid `-z 0` with a finite `-z 24h` (configurable via `DURATION`
  env var) — `-z 0` is rejected by oha and `-z` cannot be combined with `-n`.
- Redirected each oha child's output to `/dev/null`; final summary is suppressed
  in favour of a clean terminal during demos.
- Added a 30s bash-level heartbeat in a backgrounded subshell so the operator
  sees the script is alive while oha is silent under `--no-tui`. The heartbeat
  PID is tracked and torn down by the existing trap.
- `wait` now targets the two oha PIDs explicitly so the script's exit status
  reflects the workers, not the heartbeat loop.

## Why oha, not k6

Demo runs at 1 rps total — well below where k6's scenario tooling and
thresholds add value. oha is already the user's go-to ad-hoc load tool and
maps cleanly to the dashboard's per-priority series via two parallel processes.
k6 would force a JS scenario file plus a second tool to maintain. k6 is
installed (mise) and remains available for future stress-testing work, but for
this script it would be over-engineering.

## Verification

- `bash -n scripts/load.sh` — OK
- Live run with `DURATION=5s timeout 8 bash scripts/load.sh` against the
  running producer (which was simultaneously taking a 100c/500qps stress load
  in another terminal):
  - Clean stdout, no TUI corruption
  - Both children spawned, ran for 5s, exited 0
  - Wrapper exited 0
  - No zombies left behind
- Producer `/actuator/health` returned `{"status":"UP"}` after the run

## Files touched

- `scripts/load.sh` (+29 / -7)
