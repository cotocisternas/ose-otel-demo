---
phase: 15-outbound-http-client-spans
plan: "02"
subsystem: api
tags: [spring-restclient, otel-http, tracing-interceptor, http-client, notification-stub]

# Dependency graph
requires:
  - phase: 15-outbound-http-client-spans/15-01
    provides: TracingClientHttpRequestInterceptor + HttpHeadersSetter in otel-bootstrap/http/
provides:
  - HttpClientConfig: @Configuration with TracingClientHttpRequestInterceptor @Bean + RestClient.Builder @Bean (F6-1 mitigation)
  - NotificationStubController: POST /notifications stub logging traceparent header (D-H5)
  - OrderService updated with RestClient.Builder constructor injection + fire-and-forget outbound call (D-H1/D-H2/D-H3)
  - app.notification-url externalized in application.yaml with default http://localhost:8080/notifications (D-H7)
affects:
  - 15-03 (integration tests for CLIENT span — OrderFlowIT span count must be >= 7)
  - 15-04 (README + verify task)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Configuration parallel to RabbitConfig for HTTP client wiring (TracingClientHttpRequestInterceptor @Bean + RestClient.Builder @Bean)"
    - "RestClient.Builder singleton @Bean supersedes Spring Boot ConditionalOnMissingBean PROTOTYPE builder"
    - "Fire-and-forget outbound HTTP call with catch swallowing notification failures (D-H2)"
    - "@Value-injected URL with fixed loopback default (D-H7)"

key-files:
  created:
    - producer-service/src/main/java/com/example/producer/config/HttpClientConfig.java
    - producer-service/src/main/java/com/example/producer/api/NotificationStubController.java
  modified:
    - producer-service/src/main/java/com/example/producer/domain/OrderService.java
    - producer-service/src/main/resources/application.yaml

key-decisions:
  - "HttpClientConfig defines singleton RestClient.Builder @Bean, not PROTOTYPE — sufficient for one OrderService injection point (D-H8)"
  - "Fire-and-forget try/catch swallows notification failure with WARN log (D-H2) — order already AMQP-published before HTTP call"
  - "OTel interceptor registered last via .requestInterceptor() (F6-3) — F6-1 comment documents RestClient.create() prohibition"

patterns-established:
  - "HTTP client config mirrors AMQP config: tracing component @Bean + transport @Bean pattern"
  - "RestClient.Builder injected in constructor, built once (D-H3) — never per-request"

requirements-completed:
  - HCLI-03
  - HCLI-04

# Metrics
duration: 2min
completed: 2026-05-04
---

# Phase 15 Plan 02: Outbound HTTP Client Wiring Summary

**HttpClientConfig wires TracingClientHttpRequestInterceptor into a RestClient.Builder @Bean; OrderService injects it and fires a POST /notifications call after publisher.publish(); NotificationStubController logs the traceparent header for propagation proof**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-04T12:20:18Z
- **Completed:** 2026-05-04T12:22:27Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Created HttpClientConfig.java as exact structural parallel to RabbitConfig — two @Bean factory methods: TracingClientHttpRequestInterceptor and RestClient.Builder with interceptor registered last (F6-3)
- Created NotificationStubController.java — POST /notifications logs traceparent header at INFO (D-H5), required=false guard prevents 400 on misconfigured interceptor
- Extended OrderService constructor with RestClient.Builder + @Value-injected notifyUrl; builds restClient once (D-H3, F6-1 prevention)
- Inserted fire-and-forget HTTP call after publisher.publish() and before ordersCreated.add() (D-H1 ordering), wrapped in try/catch swallowing failures (D-H2)
- Added app.notification-url: http://localhost:8080/notifications to application.yaml

## Task Commits

Each task was committed atomically:

1. **Task 1: Create HttpClientConfig and NotificationStubController** - `2d09139` (feat)
2. **Task 2: Edit OrderService and application.yaml** - `1747da9` (feat)

## Files Created/Modified
- `producer-service/src/main/java/com/example/producer/config/HttpClientConfig.java` - @Configuration creating TracingClientHttpRequestInterceptor @Bean + RestClient.Builder @Bean with interceptor
- `producer-service/src/main/java/com/example/producer/api/NotificationStubController.java` - POST /notifications stub with traceparent header logging
- `producer-service/src/main/java/com/example/producer/domain/OrderService.java` - Added RestClient.Builder constructor injection, fire-and-forget HTTP call, SLF4J logger
- `producer-service/src/main/resources/application.yaml` - Added app.notification-url property

## Decisions Made
- HttpClientConfig defines a singleton RestClient.Builder @Bean (not PROTOTYPE), which is documented in Javadoc as sufficient for one OrderService injection at startup. Spring Boot's auto-configured PROTOTYPE builder backs off via @ConditionalOnMissingBean.
- Fire-and-forget catch block logs at WARN and swallows the exception — notification failure is observable in Tempo (CLIENT span status=ERROR) but does not fail the order or the 202 response.
- Comment in OrderService constructor explicitly calls out F6-1 prohibition (`RestClient.create(url)`); this comment line is the only occurrence of `RestClient.create` in the file — no actual prohibited call exists.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
- Initial `mvn -pl producer-service compile` failed because `otel-bootstrap` (where TracingClientHttpRequestInterceptor lives from Plan 01) had not been compiled. Used `mvn -pl producer-service -am compile` to build with module dependencies first. This is standard Maven multi-module behavior, not a defect.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness
- All four producer-service file changes from the plan are committed and compile cleanly
- Plan 03 (integration test update — OrderFlowIT span count >= 7) can proceed immediately
- Plan 04 (README + verify:http-client-spans mise task) can proceed in parallel with Plan 03

## Self-Check: PASSED

- HttpClientConfig.java: FOUND
- NotificationStubController.java: FOUND
- 15-02-SUMMARY.md: FOUND
- Task 1 commit 2d09139: FOUND
- Task 2 commit 1747da9: FOUND
- mvn -pl producer-service -am compile: Exit 0

---
*Phase: 15-outbound-http-client-spans*
*Completed: 2026-05-04*
