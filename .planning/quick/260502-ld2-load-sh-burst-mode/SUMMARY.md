---
slug: ld2-load-sh-burst-mode
type: quick
status: complete
created: 2026-05-02
completed: 2026-05-02
---

# Summary — load.sh burst mode

## What changed

`scripts/load.sh` gained an optional **burst stream** that layers on top of
the two steady streams. Off by default (`BURST_RPS=0`), so existing
behaviour is preserved.

New env knobs:

- `BURST_RPS` (default `0` = off)
- `BURST_DURATION` (default `60s`)
- `BURST_INTERVAL` (default `300`, idle seconds between bursts)
- `BURST_CONNECTIONS` (default `50`)
- `BURST_PRIORITY` (default `standard`)

Each burst is a finite `oha -z $BURST_DURATION` invocation. The burst loop
is a backgrounded subshell that sleeps, fires the oha, repeats, and is
torn down by the existing trap. Cleanup pkills the loop's children before
killing the loop so an in-flight burst can't outlive the script under
programmatic SIGTERM.

Header docstring updated; banner now distinguishes "Steady streams" from
"Burst stream" and prints all knob values.

## Verification

`bash -n` clean.

Live run alongside the operator's already-running `load.sh` (default 200
rps), to validate the burst path under realistic conditions:

    DURATION=35s QUERY_PER_SECOND=50 N_CONNECTIONS=5 \
    BURST_RPS=600 BURST_DURATION=8s BURST_INTERVAL=10s \
    BURST_CONNECTIONS=40 BURST_PRIORITY=standard \
    bash scripts/load.sh

Observed via `sum by (order_priority) (rate(orders_created_total[30s]))`:

| t       | express rps | standard rps |
|---------|-------------|--------------|
| -180s   | 100         | 100          |
| -120s   | 100         | 100          |
| -60s    | 150 (test steady on top) | 390 (1st burst) |
| -45s    | 150         | 450 (2nd burst peak) |
| -30s    | 108         | 217 (drain)  |
| now     | 100         | 100 (back to steady) |

`express` rose to 150 (50 from test + 100 from operator) — confirms test
steady stream fired. `standard` peaked at **450 rps** during the burst
windows (100 operator + 50 test steady + ~300 burst average over the rate
window) — confirms burst stream fired and was visible in Prometheus.

Queue depth stayed at ≤2: deliberate — test parameters were tuned to
exercise the burst PATH, not to overflow the consumer. Operator can scale
`BURST_RPS` up to any level; the earlier 800 rps × 60s manual run already
demonstrated 14k+ queue depth at the exporter scrape resolution.

`pgrep` after exit: no leftover oha or load.sh processes from the test.

## Cosmetic fix folded in

Initial test exposed a display quirk: when `BURST_INTERVAL` was passed
with a unit suffix (`10s`), the banner echoed `every 10ss` — the script
unconditionally appended `s`. Reworded to "duration X, idle Y between
bursts" so the value renders cleanly whether the user passes `300`, `10s`,
or `5m`.

## Files touched

- `scripts/load.sh` (+~70 lines: burst loop, env defaults, cleanup hook,
  banner, docstring)
- `.planning/STATE.md` (Quick Tasks Completed row added)
