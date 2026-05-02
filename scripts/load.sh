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
