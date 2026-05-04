---
phase: 15-outbound-http-client-spans
plan: "01"
subsystem: observability
tags: [opentelemetry, spring-web, ClientHttpRequestInterceptor, semconv, TextMapSetter, otel-bootstrap]

# Dependency graph
requires:
  - phase: 03-amqp-context-propagation
    provides: "MessagePropertiesSetter + TracingMessagePostProcessor structural templates mirrored exactly"
provides:
  - "otel-bootstrap/http/HttpHeadersSetter.java: TextMapSetter<HttpHeaders> adapter"
  - "otel-bootstrap/http/TracingClientHttpRequestInterceptor.java: ClientHttpRequestInterceptor with full CLIENT span lifecycle"
affects:
  - 15-outbound-http-client-spans (plans 02-04 use these classes)
  - producer-service (HttpClientConfig will wire these beans)

# Tech tracking
tech-stack:
  added:
    - "spring-web as provided dep to otel-bootstrap/pom.xml (HttpHeaders, ClientHttpRequestInterceptor)"
  patterns:
    - "HTTP CLIENT span interceptor mirrors AMQP TracingMessagePostProcessor: start span -> makeCurrent -> inject -> execute -> record status -> end in finally"
    - "F6-2: span started BEFORE inject — CLIENT spanId in Context.current() when traceparent written"
    - "ServiceIncubatingAttributes.SERVICE_PEER_NAME (not deprecated PeerIncubatingAttributes.PEER_SERVICE)"

key-files:
  created:
    - otel-bootstrap/src/main/java/com/example/otel/http/HttpHeadersSetter.java
    - otel-bootstrap/src/main/java/com/example/otel/http/TracingClientHttpRequestInterceptor.java
  modified:
    - otel-bootstrap/pom.xml

key-decisions:
  - "Added spring-web as provided dep to otel-bootstrap/pom.xml — spring-rabbit does not transitively bring spring-web; HttpHeaders requires it"
  - "peerServiceName is constructor-injected (D-H10) — interceptor stays reusable across any service"

patterns-established:
  - "Pattern: otel-bootstrap/http/ mirrors otel-bootstrap/amqp/ structure — one Setter + one interceptor/postprocessor per transport"
  - "Pattern: TextMapSetter null-guard on carrier before write"

requirements-completed:
  - HCLI-01
  - HCLI-02

# Metrics
duration: 2min
completed: 2026-05-04
---

# Phase 15 Plan 01: HTTP CLIENT Span Interceptor Classes (otel-bootstrap) Summary

**`TextMapSetter<HttpHeaders>` adapter and full `ClientHttpRequestInterceptor` with `SpanKind.CLIENT` lifecycle, F6-2/F6-3/F6-4 ordering guarantees, and complete semconv attribute set — structural mirror of the AMQP pair in `otel-bootstrap/amqp/`**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-04T13:09:34Z
- **Completed:** 2026-05-04T13:11:45Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Created `HttpHeadersSetter.java`: `TextMapSetter<HttpHeaders>` adapter with null-guard and `HttpHeaders.set()` (not `add()`) write — guarantees single `traceparent` header per W3C spec
- Created `TracingClientHttpRequestInterceptor.java`: `ClientHttpRequestInterceptor` with `SpanKind.CLIENT` span, full semconv attribute set (`http.request.method`, `server.address`, `server.port`, `url.full`, `http.response.status_code`, `service.peer.name`), F6-2 inject-after-makeCurrent ordering, F6-4 status code on both success and IOException paths
- Added `spring-web` as `provided` dep to `otel-bootstrap/pom.xml` (Rule 3 deviation — not transitively available via `spring-rabbit`)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create HttpHeadersSetter.java** - `9e028a5` (feat)
2. **Task 2: Create TracingClientHttpRequestInterceptor.java** - `d3e93e1` (feat)

## Files Created/Modified

- `otel-bootstrap/src/main/java/com/example/otel/http/HttpHeadersSetter.java` - `TextMapSetter<HttpHeaders>` with null-guard and overwrite-semantics write call
- `otel-bootstrap/src/main/java/com/example/otel/http/TracingClientHttpRequestInterceptor.java` - `ClientHttpRequestInterceptor` with full CLIENT span lifecycle (F6-2/F6-3/F6-4 compliant)
- `otel-bootstrap/pom.xml` - Added `spring-web` as `provided` dependency

## Decisions Made

- Added `spring-web` as `provided` scope to `otel-bootstrap/pom.xml`. The plan stated "no new dependencies" but `spring-rabbit` does not transitively bring `spring-web` (only `spring-context`, `spring-messaging`, `spring-tx`). Rule 3 auto-fix — blocking, required for compile.
- `peerServiceName` as constructor parameter (D-H10): interceptor is reusable across any service; the logical name is set per-service in wiring config (e.g., `HttpClientConfig`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added spring-web as provided dependency to otel-bootstrap**
- **Found during:** Task 1 (HttpHeadersSetter.java — HttpHeaders import)
- **Issue:** `otel-bootstrap/pom.xml` had no `spring-web` dependency. `spring-rabbit` (the existing provided dep) does not transitively bring `spring-web`. `HttpHeaders` and `ClientHttpRequestInterceptor` both live in `spring-web`. Without it, both new files would fail to compile.
- **Fix:** Added `<dependency><groupId>org.springframework</groupId><artifactId>spring-web</artifactId><scope>provided</scope></dependency>` to `otel-bootstrap/pom.xml`. Used `provided` scope consistent with all other Spring deps in that module.
- **Files modified:** `otel-bootstrap/pom.xml`
- **Verification:** `mvn -pl otel-bootstrap compile` exits 0
- **Committed in:** `9e028a5` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking dependency)
**Impact on plan:** Necessary for correctness — no scope creep. The plan's "no new dependencies" claim was based on the assumption that `spring-web` was already transitively available, which the actual dependency tree disproved.

## Issues Encountered

None beyond the Rule 3 deviation documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `otel-bootstrap/http/` package is complete and compiles cleanly
- Plan 15-02 can now create `HttpClientConfig.java` in `producer-service/config/` to wire `TracingClientHttpRequestInterceptor` as a Spring bean
- Plan 15-03 can edit `OrderService.java` to inject the `RestClient.Builder` and make the outbound notification call
- No blockers

---
*Phase: 15-outbound-http-client-spans*
*Completed: 2026-05-04*
