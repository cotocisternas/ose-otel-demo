#!/usr/bin/env bash
# scripts/load.sh — continuous load against producer-service for live demos.
# WORK-03 / Phase 7 D-04: two parallel oha invocations alternating priorities.
#
# ~0.5 req/sec per priority = ~1 req/sec total split 50/50.
# Both per-priority series populate the dashboard's "Orders Created by Priority" panel.
#
# Ctrl-C kills both child oha processes via the cleanup trap.
# Run alongside `mise run dev` (NOT `mise run demo:order` — that is one-shot).
#
# NOTE: do NOT add env-var-driven RPS / DURATION / payload arg parsing without
# re-validating shell-injection surface. D-04 explicitly forbids env-var arg
# parsing in v1; per-attendee tweakability is a v1.x ask (T-07-02-01).
#
# IMPLEMENTATION NOTES (260502-ld1):
#   - `--no-tui` is REQUIRED. Without it, both oha children render their 16fps
#     TUI to the same terminal alternate-screen buffer and corrupt each other.
#   - `-z 24h` stands in for "run forever until Ctrl-C". oha rejects `-z 0`,
#     and `-z` cannot be combined with `-n` to mean unlimited. 24h is well
#     beyond any plausible demo session; the trap below tears children down.
#   - With --no-tui oha is silent until each run finishes, so the bash wrapper
#     prints a heartbeat every 30s to confirm the load is still flowing.

set -euo pipefail

TARGET="${TARGET:-http://localhost:8080/orders}"
DURATION="${DURATION:-24h}"

cleanup() {
  # SIGINT/SIGTERM/EXIT — kill all children if alive, then wait so we don't leave zombies.
  [[ -n "${PID_EXPRESS:-}" ]] && kill "$PID_EXPRESS" 2>/dev/null || true
  [[ -n "${PID_STANDARD:-}" ]] && kill "$PID_STANDARD" 2>/dev/null || true
  [[ -n "${PID_HEARTBEAT:-}" ]] && kill "$PID_HEARTBEAT" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup SIGINT SIGTERM EXIT

echo "WORK-03: continuous load against ${TARGET}"
echo "Two parallel oha invocations: priority=express @ 0.5 rps, priority=standard @ 0.5 rps"
echo "Duration: ${DURATION} (Ctrl-C to stop early)"
echo

oha -z "${DURATION}" -q 0.5 --no-tui \
    -m POST \
    -T application/json \
    -d '{"sku":"WIDGET-EXPRESS","quantity":3,"priority":"express"}' \
    "${TARGET}" >/dev/null 2>&1 &
PID_EXPRESS=$!

oha -z "${DURATION}" -q 0.5 --no-tui \
    -m POST \
    -T application/json \
    -d '{"sku":"WIDGET-STANDARD","quantity":1,"priority":"standard"}' \
    "${TARGET}" >/dev/null 2>&1 &
PID_STANDARD=$!

# Heartbeat: prove the script is alive while oha is silent under --no-tui.
(
  start=$(date +%s)
  while sleep 30; do
    elapsed=$(( $(date +%s) - start ))
    printf '[load] alive — elapsed=%ds (express=%d, standard=%d)\n' \
      "$elapsed" "$PID_EXPRESS" "$PID_STANDARD"
  done
) &
PID_HEARTBEAT=$!

wait "$PID_EXPRESS" "$PID_STANDARD"
