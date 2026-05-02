#!/usr/bin/env bash
# scripts/load.sh — continuous load against producer-service for live demos.
# WORK-03 / Phase 7 D-04 + 260502-ld2 burst-mode follow-up.
#
# Steady streams (always on): two parallel oha invocations, one per priority.
#   QUERY_PER_SECOND per priority, N_CONNECTIONS per priority.
#   These streams send NO X-Idempotency-Key header — the controller's
#   idempotency gate is bypassed and every request flows straight to AMQP.
#   This keeps the steady demo throughput consistent with pre-Phase-8 behavior.
#
# Idempotency stream (always on, post-Phase-8): a third low-rate stream sends
# requests carrying a fresh X-Idempotency-Key per request, generated from
# /proc/sys/kernel/random/uuid. This drives the producer's
# InstrumentedJedisPool.setIfAbsent path so:
#   - SET CLIENT spans flow continuously into Tempo (Infra dashboard panel
#     "Recent SET spans"); and
#   - idempotency_cache_miss_total increments per request (every key is unique
#     in the TTL window so every request is a "miss"/NEW); and
#   - rate(redis_commands_processed_total[5m]) becomes non-zero.
# Rate is intentionally a few %% of the steady streams (default 5 rps) so it
# doesn't materially shift queue/AMQP/Postgres dynamics that the rest of the
# demo teaches.
#
# Burst stream (set BURST_RPS>0 to enable): a third oha is fired every
# BURST_INTERVAL seconds for BURST_DURATION, hitting BURST_PRIORITY at
# BURST_RPS rps with BURST_CONNECTIONS connections. This layers on top of
# the steady streams and produces the canonical "ramp-up / plateau / drain"
# shape on the dashboard — perfect for teaching queue backpressure and
# consumer throughput limits.
#
# Override via env:
#   TARGET, DURATION, QUERY_PER_SECOND, N_CONNECTIONS,
#   IDEMPOTENT_RPS (0=off; default 5),
#   BURST_RPS (0=off), BURST_DURATION, BURST_INTERVAL, BURST_CONNECTIONS,
#   BURST_PRIORITY (default: standard)
#
# Defaults: ~200 rps total (100 per priority) + 5 rps idempotent + no burst.
#
# Validated 2026-05-02 against running infra:
#   - Steady at defaults: rate(orders_created_total[1m]) ≈ 100/s per priority,
#     queue depth ≤ 1.
#   - 60s burst at 800 rps on top of steady: queue depth peaked at ≥14k as
#     reported by the RabbitMQ infra exporter (true peak between scrapes is
#     higher; the per-object endpoint scrapes on a coarse cadence), drained
#     back to ≤1 within ~60s.
#
# Ctrl-C kills all child processes via the cleanup trap.
# Run alongside `mise run dev` (NOT `mise run demo:order` — that is one-shot).
#
# IMPLEMENTATION NOTES (260502-ld1, 260502-ld2):
#   - `--no-tui` REQUIRED on every oha invocation: two TUIs in the same TTY
#     corrupt each other (terminal alternate-screen buffer collision).
#   - `-z 24h` for steady streams stands in for "run forever". oha rejects
#     `-z 0`. The burst oha uses a finite `-z $BURST_DURATION` which exits
#     on its own; the burst loop sleeps $BURST_INTERVAL between bursts.
#   - The burst loop is a background subshell. On Ctrl-C the script's
#     foreground process group receives SIGINT and any in-flight burst oha
#     dies with it. On programmatic SIGTERM, cleanup kills the loop and
#     pkills its direct children to catch any in-flight oha.
#   - The idempotency stream uses curl, not oha: oha sends a single fixed
#     header value per process (no per-request templating), so a fresh UUID
#     per request requires shelling out per request. /proc/sys/kernel/random/uuid
#     is the kernel-provided fast UUID source (no fork to uuidgen) — Linux only,
#     which matches the workshop's docker-compose Linux runtime.

set -euo pipefail

TARGET="${TARGET:-http://localhost:8080/orders}"
DURATION="${DURATION:-24h}"
QUERY_PER_SECOND="${QUERY_PER_SECOND:-100}"
N_CONNECTIONS="${N_CONNECTIONS:-10}"

# Idempotency stream — set IDEMPOTENT_RPS=0 to disable.
IDEMPOTENT_RPS="${IDEMPOTENT_RPS:-5}"

# Burst stream — disabled by default.
BURST_RPS="${BURST_RPS:-150}"
BURST_DURATION="${BURST_DURATION:-60s}"
BURST_INTERVAL="${BURST_INTERVAL:-300}"
BURST_CONNECTIONS="${BURST_CONNECTIONS:-50}"
BURST_PRIORITY="${BURST_PRIORITY:-standard}"

cleanup() {
  # SIGINT/SIGTERM/EXIT — kill all known children, then catch any in-flight
  # burst oha that the burst loop spawned. Order matters: pkill the loop's
  # children before killing the loop itself, so they're still findable.
  [[ -n "${PID_BURST_LOOP:-}" ]] && pkill -P "$PID_BURST_LOOP" 2>/dev/null || true
  [[ -n "${PID_IDEMPOTENT:-}" ]] && pkill -P "$PID_IDEMPOTENT" 2>/dev/null || true
  for pid in "${PID_EXPRESS:-}" "${PID_STANDARD:-}" "${PID_IDEMPOTENT:-}" "${PID_BURST_LOOP:-}" "${PID_HEARTBEAT:-}"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup SIGINT SIGTERM EXIT

echo "WORK-03: continuous load against ${TARGET}"
echo "Steady streams:"
echo "  priority=express  @ ${QUERY_PER_SECOND} rps (c=${N_CONNECTIONS})"
echo "  priority=standard @ ${QUERY_PER_SECOND} rps (c=${N_CONNECTIONS})"
if [[ "$IDEMPOTENT_RPS" -gt 0 ]]; then
  echo "Idempotency stream:"
  echo "  X-Idempotency-Key per request @ ${IDEMPOTENT_RPS} rps (priority=standard)"
else
  echo "Idempotency stream: disabled (set IDEMPOTENT_RPS>0 to enable)"
fi
if [[ "$BURST_RPS" -gt 0 ]]; then
  echo "Burst stream:"
  echo "  priority=${BURST_PRIORITY} @ ${BURST_RPS} rps (c=${BURST_CONNECTIONS})"
  echo "  duration ${BURST_DURATION}, idle ${BURST_INTERVAL} between bursts"
else
  echo "Burst: disabled (set BURST_RPS>0 to enable)"
fi
echo "Total duration: ${DURATION} (Ctrl-C to stop early)"
echo

# --- Steady streams ----------------------------------------------------------

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

# --- Idempotency stream -----------------------------------------------------
#
# Fresh UUID per request → every request is a cache miss (NEW) within the
# Phase 8 default TTL of 1h, so every request also publishes to AMQP and
# downstream Postgres — same flow shape as the steady streams, just with the
# extra Valkey SET CLIENT span on the producer side.
#
# Rate is governed by `sleep $sleep_interval` between requests. Computed once
# from IDEMPOTENT_RPS to avoid bc/awk in the hot path. Not exact (kernel sleep
# resolution + curl fork latency add jitter) but accurate to ~10%% which is
# fine for "keep the SET-spans panel populated".

if [[ "$IDEMPOTENT_RPS" -gt 0 ]]; then
  (
    sleep_interval=$(awk "BEGIN {printf \"%.3f\", 1.0 / ${IDEMPOTENT_RPS}}")
    idempotent_payload='{"sku":"WIDGET-IDEMPOTENT","quantity":1,"priority":"standard"}'
    while :; do
      key=$(< /proc/sys/kernel/random/uuid)
      curl -sS -o /dev/null \
        -X POST \
        -H "Content-Type: application/json" \
        -H "X-Idempotency-Key: ${key}" \
        -d "${idempotent_payload}" \
        "${TARGET}" || true
      sleep "${sleep_interval}"
    done
  ) &
  PID_IDEMPOTENT=$!
fi

# --- Burst loop (optional) ---------------------------------------------------

if [[ "$BURST_RPS" -gt 0 ]]; then
  (
    burst_payload="{\"sku\":\"WIDGET-BURST\",\"quantity\":1,\"priority\":\"${BURST_PRIORITY}\"}"
    while :; do
      sleep "$BURST_INTERVAL"
      printf '[burst] start — %s rps for %s on priority=%s\n' \
        "$BURST_RPS" "$BURST_DURATION" "$BURST_PRIORITY"
      oha -z "${BURST_DURATION}" \
          -q "${BURST_RPS}" \
          -c "${BURST_CONNECTIONS}" \
          -m POST \
          -T application/json \
          -d "${burst_payload}" \
          --no-tui \
          "${TARGET}" >/dev/null 2>&1 || true
      printf '[burst] end\n'
    done
  ) &
  PID_BURST_LOOP=$!
fi

# --- Heartbeat ---------------------------------------------------------------

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
