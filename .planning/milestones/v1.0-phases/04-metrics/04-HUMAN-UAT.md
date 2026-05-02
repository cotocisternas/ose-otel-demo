---
status: partial
phase: 04-metrics
source: [04-VERIFICATION.md]
started: 2026-05-01T22:48:00.000Z
updated: 2026-05-01T22:48:00.000Z
---

## Current Test

[awaiting human testing — start `mise run infra:up && mise run dev` then run the queries below]

## Tests

### 1. orders_created_total visible in Mimir within 15s with two order_priority labels (SC1)
expected: `mise run demo:order` → wait 12s → `orders_created_total` in Mimir shows two series with `order_priority="express"` and `order_priority="standard"` labels, both carrying `service_name="order-producer"`
result: [pending]

### 2. http_server_request_duration_seconds populated with count+sum+bucket in seconds (SC2)
expected: ~30s of traffic → `http_server_request_duration_seconds_count/_sum/_bucket` present; `_sum` per-request ~0.05–0.5 (seconds, NOT 50–500 which would indicate millis regression); `_bucket` carries `http_request_method` + `http_response_status_code` labels
result: [pending]

### 3. orders_queue_depth_estimate refreshes every ~10s (SC3 — proves PeriodicMetricReader override)
expected: two consecutive Mimir queries 12s apart show timestamp delta 8–15s; values in [0, 50); `service_name="order-consumer"`
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
