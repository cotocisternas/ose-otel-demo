#!/usr/bin/env bash
# scripts/load.sh — continuous load against producer-service for live demos.
# WORK-03 / Phase 7 D-04: two parallel oha invocations alternating priorities.
#
# Defaults: QUERY_PER_SECOND=100 per priority, N_CONNECTIONS=10 per priority
# => ~200 rps total, split 50/50 across priority=express and priority=standard.
# Both per-priority series populate the dashboard's "Orders Created by Priority" panel.
#
# Override via env: TARGET, DURATION, QUERY_PER_SECOND, N_CONNECTIONS.
# Validated 2026-05-02 against running infra: rate(orders_created_total[1m])
# reports ~100/s per priority at the defaults; consumer keeps up (queue ≤ 1).
#
# Ctrl-C kills both child oha processes via the cleanup trap.
# Run alongside `mise run dev` (NOT `mise run demo:order` — that is one-shot).
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
QUERY_PER_SECOND="${QUERY_PER_SECOND:-100}"
N_CONNECTIONS="${N_CONNECTIONS:-10}"

cleanup() {
  # SIGINT/SIGTERM/EXIT — kill all children if alive, then wait so we don't leave zombies.
  [[ -n "${PID_EXPRESS:-}" ]] && kill "$PID_EXPRESS" 2>/dev/null || true
  [[ -n "${PID_STANDARD:-}" ]] && kill "$PID_STANDARD" 2>/dev/null || true
  [[ -n "${PID_HEARTBEAT:-}" ]] && kill "$PID_HEARTBEAT" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup SIGINT SIGTERM EXIT

echo "WORK-03: continuous load against ${TARGET}"
echo "Two parallel oha invocations:"
echo "  priority=express  @ ${QUERY_PER_SECOND} rps (c=${N_CONNECTIONS})"
echo "  priority=standard @ ${QUERY_PER_SECOND} rps (c=${N_CONNECTIONS})"
echo "Duration: ${DURATION} (Ctrl-C to stop early)"
echo

oha -z "${DURATION}" \
    -q "${QUERY_PER_SECOND}" \
    -c "${N_CONNECTIONS}" \
    -m POST \
    -T application/json \
    -d '{"sku":"WIDGET-EXPRESS","quantity":3,"priority":"express"}' \
    --no-tui \
    "${TARGET}" >/dev/null 2>&1 &
PID_EXPRESS=$!

oha -z "${DURATION}" \
    -q "${QUERY_PER_SECOND}" \
    -c "${N_CONNECTIONS}" \
    -m POST \
    -T application/json \
    -d '{"sku":"WIDGET-STANDARD","quantity":1,"priority":"standard"}' \
    --no-tui \
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
