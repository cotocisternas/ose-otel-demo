---
slug: ld2-load-sh-burst-mode
type: quick
created: 2026-05-02
---

# Add burst mode to scripts/load.sh

## Problem

After ld1 the script ran a steady 200 rps but couldn't produce visible queue
growth on the dashboard — consumer kept up trivially. To demonstrate
backpressure during the workshop, the operator was layering stress oha
calls by hand (a 60s @ 800 rps spike against priority=standard was the
manual path that worked). Bake that pattern into the script.

## Design

Add a third **burst** stream that fires periodically on top of the steady
streams. Off by default; enabled by setting `BURST_RPS>0`.

New env knobs:

| Var                 | Default     | Purpose                                          |
|---------------------|-------------|--------------------------------------------------|
| `BURST_RPS`         | `0` (off)   | Rate during each burst                           |
| `BURST_DURATION`    | `60s`       | How long each burst lasts (oha `-z` value)       |
| `BURST_INTERVAL`    | `300`       | Idle time between bursts (sleep value)           |
| `BURST_CONNECTIONS` | `50`        | oha `-c` during the burst                        |
| `BURST_PRIORITY`    | `standard`  | Which priority the burst hits (express/standard) |

Implementation:
- Burst loop is a backgrounded subshell: `sleep $INTERVAL → finite oha → repeat`.
- Cleanup pkills the loop's children (in-flight oha) before killing the loop
  so an in-flight burst doesn't outlive the script.
- Burst priority defaults to `standard` so the dashboard's per-priority series
  shows one line spike while the other holds steady — clearer teaching shape
  than two synchronised spikes.

## Verification

- `bash -n` — syntax check
- Live run: `DURATION=35s QUERY_PER_SECOND=50 N_CONNECTIONS=5 BURST_RPS=600
  BURST_DURATION=8s BURST_INTERVAL=10s BURST_CONNECTIONS=40` against the
  running stack
- Confirm via Grafana:
  - Steady rates report at the configured value
  - Burst windows produce a clear rate spike on the configured priority
  - Queue depth responds (or doesn't, depending on consumer headroom)
- Confirm no zombies after script exit
