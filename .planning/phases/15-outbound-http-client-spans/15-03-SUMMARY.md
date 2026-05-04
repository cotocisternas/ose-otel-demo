---
phase: 15-outbound-http-client-spans
plan: "03"
subsystem: observability
tags: [opentelemetry, integration-tests, client-span, semconv, mise, SpanKind.CLIENT]

# Dependency graph
requires:
  - phase: 15-outbound-http-client-spans/15-01
    provides: TracingClientHttpRequestInterceptor in otel-bootstrap/http/
  - phase: 15-outbound-http-client-spans/15-02
    provides: HttpClientConfig + OrderService outbound HTTP call wiring
provides:
  - "OrderFlowIT updated: >= 7 span threshold, SpanKind.CLIENT coverage, new CLIENT span test"
  - "verify:http-client-spans mise task: queries Tempo for CLIENT spans from order-producer"
affects:
  - 15-04 (README references verify:http-client-spans as the live-stack verification command)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Integration test CLIENT span assertion: Awaitility.await on SpanKind.CLIENT + name.startsWith('POST /')"
    - "ServiceIncubatingAttributes.SERVICE_PEER_NAME typed constant in IT assertion (never string literal)"
    - "verify:http-client-spans mirrors verify:jpa-spans pattern exactly (ATTEMPTS=6, SLEEP_SECS=5, span.kind=client Tempo tag)"
    - "Span filter by kind + name prefix to distinguish HTTP CLIENT from DB CLIENT spans in shared InMemorySpanExporter"

key-files:
  created: []
  modified:
    - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java
    - mise.toml

key-decisions:
  - "Notification URL not overridden in IT: fire-and-forget (D-H2) absorbs connection failure; CLIENT span always exported via finally block in TracingClientHttpRequestInterceptor.intercept()"
  - "HTTP CLIENT span distinguished from DB CLIENT spans by name.startsWith('POST /') filter, not by attribute lookup — simpler and deterministic"
  - "Span count threshold raised to >= 7 for both happyPath and metricAssertions tests — consistent threshold across tests"

patterns-established:
  - "Pattern: CLIENT span test uses Awaitility.await on anyMatch(kind==CLIENT && name.startsWith('POST ')) then filters the full span list"

requirements-completed:
  - HCLI-03
  - HCLI-04

# Metrics
duration: 1min
completed: 2026-05-04
---

# Phase 15 Plan 03: Integration Tests + verify:http-client-spans Summary

**OrderFlowIT updated with >= 7 span count, SpanKind.CLIENT coverage assertion, and new test asserting SERVICE_PEER_NAME + parent span linkage; verify:http-client-spans mise task queries Tempo for CLIENT spans from order-producer**

## Performance

- **Duration:** 1 min
- **Started:** 2026-05-04T12:25:37Z
- **Completed:** 2026-05-04T12:27:26Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Updated `happyPathProducesSingleTrace_traceAssertions()`: Awaitility await threshold raised from `>= 5` to `>= 7` spans (Phase 15 adds CLIENT span from `TracingClientHttpRequestInterceptor` + SERVER span from `HttpServerSpanFilter` wrapping `POST /notifications`)
- Updated `successfulOrderRecordsCounterAndHistogram_metricAssertions()`: same `>= 7` threshold for consistency
- Updated SpanKind coverage assertion in `happyPathProducesSingleTrace` to include `SpanKind.CLIENT`
- Added import `io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes`
- Added new test `httpClientSpanPresentInTrace_clientSpanAssertions()`:
  - Awaits a CLIENT span with name `startsWith("POST ")`
  - Asserts `HTTP_REQUEST_METHOD=POST` and `SERVICE_PEER_NAME=notification-service` via typed constants
  - Asserts HTTP CLIENT span is a child of `OrderService.place` INTERNAL span (HCLI-03)
  - Asserts HTTP CLIENT span shares the same traceId as the SERVER span (HCLI-04)
- Added `[tasks."verify:http-client-spans"]` to `mise.toml`:
  - Mirrors `verify:jpa-spans` retry loop exactly (ATTEMPTS=6, SLEEP_SECS=5)
  - Queries `http://localhost:3200/api/search?tags=service.name%3Dorder-producer%20span.kind%3Dclient&limit=5`
  - GREEN message names `"POST /notifications"` span and `service.peer.name=notification-service` attribute
  - Diagnostic step 2 checks for `RestClient.create` absence (F6-1 mitigation)

## Task Commits

Each task was committed atomically:

1. **Task 1: Update OrderFlowIT.java** - `fb5d3a3` (test)
2. **Task 2: Add verify:http-client-spans to mise.toml** - `281d5e7` (feat)

## Files Created/Modified

- `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` - Span count threshold `>= 7`, SpanKind.CLIENT in coverage, new CLIENT span test method, ServiceIncubatingAttributes import
- `mise.toml` - New `[tasks."verify:http-client-spans"]` block after `verify:jpa-spans`

## Decisions Made

- **Notification URL not overridden in IT context**: The plan confirms the `app.notification-url` default (`http://localhost:8080/notifications`) targets port 8080 which may not be listening when `server.port=0` is used. The fire-and-forget catch in `OrderService.place()` (D-H2) absorbs the `IOException`. `TracingClientHttpRequestInterceptor.intercept()` always calls `span.end()` in its `finally` block, guaranteeing the CLIENT span is exported to `InMemorySpanExporter` regardless of HTTP outcome. No `System.setProperty` override needed.
- **HTTP CLIENT span distinguished from DB CLIENT spans by name prefix**: `name.startsWith("POST /")` is a simpler filter than attribute comparison and directly expresses what attendees see in Tempo (span name `"POST /notifications"`).
- **Consistent `>= 7` threshold across both affected tests**: Keeps test expectations in sync; both tests emit the same Phase 15 spans.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all tests passed on first run.

## User Setup Required

None.

## Next Phase Readiness

- `OrderFlowIT` is fully updated for Phase 15 spans; all integration tests pass
- `mise.toml` has `verify:http-client-spans` for live-stack verification
- Plan 15-04 (README + step-15 git tag) can proceed immediately

---
*Phase: 15-outbound-http-client-spans*
*Completed: 2026-05-04*

## Self-Check: PASSED

- `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` — FOUND
- `mise.toml` — FOUND (verify:http-client-spans task present, 6 occurrences)
- Commit `fb5d3a3` (Task 1) — FOUND
- Commit `281d5e7` (Task 2) — FOUND
- `mvn -pl integration-tests -am test` — EXIT 0 (all tests GREEN)
- `grep -c ">= 7" OrderFlowIT.java` — 2 (>= 1 required)
- `grep -c "httpClientSpanPresentInTrace" OrderFlowIT.java` — 1 (required)
- `grep -c "verify:http-client-spans" mise.toml` — 6 (>= 2 required)
- `grep -c "RestClient.create" mise.toml` — 2 (>= 1 required)
