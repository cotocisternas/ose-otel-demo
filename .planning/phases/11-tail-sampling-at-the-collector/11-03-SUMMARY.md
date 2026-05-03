---
phase: 11-tail-sampling-at-the-collector
plan: "03"
subsystem: producer-service, load-tooling
tags: [tail-sampling, widget-slow, load-generator, latency-policy]
dependency_graph:
  requires: [11-01]
  provides: [TSAMP-01, TSAMP-03]
  affects: [producer-service/OrderService.java, scripts/load.sh]
tech_stack:
  added: []
  patterns: [Thread.sleep-inside-INTERNAL-span-scope, oha-steady-stream-shape]
key_files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/domain/OrderService.java
    - scripts/load.sh
decisions:
  - "WIDGET-SLOW branch inserted between UUID generation and publisher.publish so trace MAX-span duration includes the 1500ms sleep"
  - "InterruptedException wrapped as RuntimeException so existing D-03 catch records it as span ERROR"
  - "SLOW oha stream uses -c 1 (not -c 10) because slow requests block Tomcat thread for ~1.5s"
  - "Workshop-safe SLOW_RPS upper bound documented in both source files (triple-redundant per WARNING-3 fix)"
metrics:
  duration: "~3m 17s"
  completed_date: "2026-05-03"
  tasks_completed: 2
  files_modified: 2
---

# Phase 11 Plan 03: WIDGET-SLOW SKU Branch + SLOW_RPS Load Stream Summary

WIDGET-SLOW SKU branch added to `OrderService.place()` (Thread.sleep 1500ms inside INTERNAL span scope) and SLOW_RPS fourth oha stream added to `scripts/load.sh`, making the Collector's tail_sampling latency policy (threshold_ms: 1000) reliably triggerable under default workshop load.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add WIDGET-SLOW SKU branch + class-JavaDoc to OrderService.place() | cd8753f | producer-service/.../OrderService.java |
| 2 | Add SLOW_RPS fourth oha stream + cleanup + heartbeat to scripts/load.sh | 4b650db | scripts/load.sh |

## What Was Built

**Task 1 — OrderService.java:**
- `if ("WIDGET-SLOW".equals(payload.get("sku")))` branch inserted between `UUID.randomUUID()` and `publisher.publish()` — correct position ensures publish's PRODUCER span is parented under the slow INTERNAL span AND trace MAX-span duration includes the sleep
- `Thread.sleep(1500L)` with `InterruptedException` handling: interrupt flag restored via `Thread.currentThread().interrupt()`, rethrown as `RuntimeException` so existing D-03 catch records it as span ERROR
- Class JavaDoc phase-attribution paragraph added (TSAMP-01 traceability, workshop-safe SLOW_RPS 0–5 caveat naming Tomcat thread starvation as the failure mode)

**Task 2 — scripts/load.sh:**
- `SLOW_RPS="${SLOW_RPS:-2}"` declared after `IDEMPOTENT_RPS` with workshop-safe caveat comment block
- SLOW oha stream block (`-c 1`, fixed payload `sku=WIDGET-SLOW`) inserted between `PID_STANDARD` and the idempotency stream header
- `"${PID_SLOW:-}"` added to cleanup trap for-loop
- Slow stream banner block (with disabled-fallback when `SLOW_RPS=0`)
- Heartbeat printf extended: `slow=%d` format + `"${PID_SLOW:-0}"` arg

## Deviations from Plan

None — plan executed exactly as written.

## Verification

- `mvn -B -pl producer-service -am compile -q` exits 0
- All 13 Task 1 acceptance criteria grep checks passed
- `bash -n scripts/load.sh` exits 0
- All 16 Task 2 acceptance criteria checks passed (including insertion order awk check)
- Plan automated verify commands both exit 0 with `task1 ok` / `task2 ok`

## Known Stubs

None. Both files are fully wired: WIDGET-SLOW branch fires on real requests; SLOW_RPS stream sends real traffic to the endpoint.

## Threat Flags

No new network endpoints or auth paths introduced. The WIDGET-SLOW SKU branch is loopback-only workshop tooling per threat register T-11-03-02 (accepted). Tomcat thread exhaustion mitigation (T-11-03-01) documented in both source files per WARNING-3 triple-redundancy requirement.

## Self-Check: PASSED

- `producer-service/src/main/java/com/example/producer/domain/OrderService.java` — modified, exists
- `scripts/load.sh` — modified, exists
- Commit cd8753f — `git log --oneline` confirms feat(11-03) OrderService commit
- Commit 4b650db — `git log --oneline` confirms feat(11-03) load.sh commit
