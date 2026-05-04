# Phase 15: Outbound HTTP-Client Spans - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Complete the HTTP instrumentation story started in v1.0 — v1.0 shipped the SERVER span (`HttpServerSpanFilter`), Phase 15 adds the CLIENT-side counterpart. A new `TracingClientHttpRequestInterceptor` in `otel-bootstrap/http/` wraps each outbound HTTP call in a `SpanKind.CLIENT` span and injects `traceparent`/`tracestate`/`baggage` via a `TextMapSetter<HttpHeaders>` — symmetric to `TracingMessagePostProcessor` in `otel-bootstrap/amqp/`. The producer's `OrderService.place()` makes one outbound POST to an in-process `NotificationStubController` via an injected `RestClient.Builder` bean; attendees see a CLIENT span as a child of the INTERNAL span in Tempo with `traceparent` confirmed in the receiving endpoint's log.

This is a **producer-service Java + otel-bootstrap phase** with minor infrastructure touches (application.yaml). The entire teaching surface is:
- New `otel-bootstrap/http/TracingClientHttpRequestInterceptor.java` (the interceptor)
- New `otel-bootstrap/http/HttpHeadersSetter.java` (TextMapSetter for HttpHeaders)
- New `producer-service/api/NotificationStubController.java` (the target endpoint)
- New `producer-service/config/HttpClientConfig.java` (RestClient.Builder @Bean + interceptor registration)
- Edited `producer-service/domain/OrderService.java` (RestClient injection + outbound call)
- Edited `producer-service/src/main/resources/application.yaml` (notification URL config)
- README §15 walkthrough
- `mise run verify:http-client-spans` verification task

Phase boundaries:
- **Does not touch** `consumer-service` at all (consumer has no outbound HTTP calls)
- **Does not touch** `OtelSdkConfiguration.java` (no new providers or exporters)
- **Does not touch** `docker-compose.yml` or `infra/observability/*` (no infrastructure changes)
- **Does not touch** existing `HttpServerSpanFilter` (it automatically wraps the stub's incoming request)

Out of scope for this phase:
- Head sampling or baggage (Phase 16)
- AMQP topology variants (Phase 17)
- WebClient reactive propagation (explicitly excluded in REQUIREMENTS.md Out of Scope)
- Multiple outbound targets or dynamic peer service mapping

</domain>

<decisions>
## Implementation Decisions

### HTTP call timing in OrderService

- **D-H1:** **After AMQP publish, before counter.** The outbound HTTP call to `/notifications` lands AFTER `publisher.publish()` and BEFORE `ordersCreated.add()`. Trace waterfall reads: INTERNAL → PRODUCER (familiar from v1.0) → CLIENT (new). Attendees see the existing flow first, then the new HTTP hop. Narrative: "order is queued, now notify."

- **D-H2:** **Fire-and-forget.** If the notification HTTP call fails (connection error, 5xx), catch the exception, log a warning, and continue to the counter. The order is already published to AMQP — notification failure doesn't roll that back. The interceptor captures `http.response.status_code` and `status=ERROR` on the CLIENT span regardless. Attendees see both happy and unhappy HTTP paths cleanly in Tempo.

- **D-H3:** **Inline in OrderService.** `OrderService` receives `RestClient.Builder` in its constructor and builds `RestClient` once (`this.restClient = restClientBuilder.build()`). The outbound call is a direct `restClient.post().uri(notifyUrl).body(...).retrieve().toBodilessEntity()` call inside `place()`. No separate `NotificationClient` class — attendees read the HTTP call right next to the AMQP publish. Matches the workshop's "boilerplate is the lesson" ethos.

### NotificationStubController design

- **D-H4:** **POST /notifications endpoint.** Semantically correct (triggers a side effect). Path `/notifications` is distinct from the existing `POST /orders`. Attendees see two different POST endpoints in the same service — one inbound (orders), one outbound target (notifications).

- **D-H5:** **Log traceparent + return 200.** The stub logs the `traceparent` header value at INFO level and returns `200 OK` with empty body. Minimal implementation satisfying HCLI-04's proof of propagation. The `@RequestHeader(value = "traceparent", required = false)` annotation captures the header; `required = false` prevents 400 errors if propagation breaks during development. Attendees grep the producer log for `traceparent` to verify the header arrived.

- **D-H6:** **Request body is just orderId.** `OrderService.place()` sends `Map.of("orderId", orderId)` — the minimum that ties the notification to the order. Mirrors what the AMQP message carries as the routing identity.

- **D-H7:** **URL configurable via application.yaml.** Property `app.notification-url` defaults to `http://localhost:8080/notifications`. `OrderService` constructor receives the URL via `@Value("${app.notification-url}")`. Consistent with how other connection strings (RabbitMQ, Postgres) surface in `application.yaml`.

### RestClient @Bean wiring

- **D-H8:** **New HttpClientConfig class.** `producer-service/config/HttpClientConfig.java` — parallel to `RabbitConfig`. Creates `TracingClientHttpRequestInterceptor` as a `@Bean` (passing `OpenTelemetry`, `Tracer`, `peerServiceName`), then produces a `RestClient.Builder` `@Bean` with the interceptor registered. Attendees see the parallel: `RabbitConfig` wires AMQP tracing, `HttpClientConfig` wires HTTP tracing.

- **D-H9:** **service.peer.name = "notification-service".** Static, descriptive value. Teaches that CLIENT spans identify their remote dependency by name for Tempo's service graph. Even though the stub is in-process, the attribute demonstrates the production pattern of naming dependencies.

- **D-H10:** **Constructor parameter for peerServiceName.** `TracingClientHttpRequestInterceptor` takes `peerServiceName` as a constructor argument. The interceptor stays reusable in `otel-bootstrap`; the `@Bean` factory in `HttpClientConfig` configures the target-specific value. Mirrors how `TracingMessagePostProcessor` receives its per-service `Tracer`.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact semconv attribute constants for HTTP client spans (researcher verifies `HttpAttributes`, `ServerAttributes`, `UrlAttributes` against semconv 1.40.0 for client-side usage — HCLI-02 specifies the attribute names)
- Whether `service.peer.name` is a typed semconv constant or string literal (HCLI-02 says NOT the deprecated `peer.service` from older semconv — researcher checks if `service.peer.name` has a stable constant in semconv 1.40.0)
- `HttpHeadersSetter` implementation details (`TextMapSetter<HttpHeaders>` — trivially `headers.set(key, value)`)
- Span naming convention for the CLIENT span (e.g., `"POST /notifications"`, `"POST notification-service"` — researcher checks OTel HTTP client span naming guidance)
- `HttpServerSpanFilter` behavior on the stub's incoming request (automatic — existing filter wraps all non-actuator paths; no change needed; trace shows CLIENT → SERVER span link)
- README §15 exact wording, length, and structure (follow Phase 14 precedent ~100-150 lines)
- Screenshot deferral to Phase 18 Playwright pipeline (follows D-E9 precedent)
- `verify:http-client-spans` bash implementation (follows `verify:jpa-spans` pattern)
- Integration test assertions for the CLIENT span (InMemorySpanExporter pattern)
- Error handling details in the interceptor (try/catch/finally pattern mirrors `TracingMessagePostProcessor`)
- Whether the `@Bean RestClient.Builder` replaces Spring Boot's auto-configured builder or customizes it via `RestClientCustomizer` (explicit `@Bean` preferred for workshop transparency)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service SDK duplication), PROP-04 (shared `otel-bootstrap` for cross-cutting propagation), WORK-01 (annotated git tags on `main`)
- `.planning/REQUIREMENTS.md` § Outbound HTTP-Client Spans (HCLI-01..04) — locked requirements for interceptor, semconv attributes, RestClient wiring, and Tempo visibility
- `.planning/ROADMAP.md` Phase 15 section — pedagogical headline, Success Criteria #1–4, pitfall mitigations (F6-1 RestClient.Builder injection, F6-2 span-before-call, F6-3 interceptor-last, F6-4 status on exception path), git tag `step-15-http-client-spans`
- `.planning/STATE.md` — Phase 10/11/12/13/14 completion records

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes operational arc
- `.planning/research/ARCHITECTURE.md` — system architecture, service boundaries, otel-bootstrap module structure
- `.planning/research/PITFALLS.md` § F6 (F6-1..F6-4) — concrete HTTP client span mitigation steps

### Prior phase context (MUST read — patterns to mirror)

- `.planning/phases/14-jdbc-jpa-database-spans/14-CONTEXT.md` — D-J1 (replace not coexist), established patterns for CLIENT span wrapping
- `.planning/phases/10-prerequisites-stack-decomposition/10-CONTEXT.md` — D-01 (datasource UID preservation), circular-ref fix (X-1 resolved)

### Files this phase EDITS or CREATES

- `otel-bootstrap/src/main/java/com/example/otel/http/TracingClientHttpRequestInterceptor.java` — **NEW** ClientHttpRequestInterceptor with CLIENT span + context injection
- `otel-bootstrap/src/main/java/com/example/otel/http/HttpHeadersSetter.java` — **NEW** TextMapSetter<HttpHeaders>
- `producer-service/src/main/java/com/example/producer/api/NotificationStubController.java` — **NEW** POST /notifications stub endpoint
- `producer-service/src/main/java/com/example/producer/config/HttpClientConfig.java` — **NEW** @Configuration with interceptor + RestClient.Builder @Beans
- `producer-service/src/main/java/com/example/producer/domain/OrderService.java` — **EDITED** to add RestClient.Builder constructor param, build RestClient once, make outbound POST call after AMQP publish
- `producer-service/src/main/resources/application.yaml` — **EDITED** to add `app.notification-url` property
- `mise.toml` — additive `[tasks."verify:http-client-spans"]` block
- `README.md` — additive §15 section

### Files this phase does NOT edit

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — no new providers or exporters
- `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` — automatically wraps the stub's incoming request (no change needed)
- `consumer-service/` — no consumer changes in this phase
- `docker-compose.yml` — no infrastructure changes
- `infra/observability/*` — no observability config changes

### Upstream documentation references (research must consult)

- [OTel Semconv 1.40.0 HTTP client spans](https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-client) — `http.request.method`, `server.address`, `server.port`, `url.full`, `http.response.status_code`, span naming guidance
- [OTel Semconv 1.40.0 `service.peer.name`](https://opentelemetry.io/docs/specs/semconv/general/attributes/#service-peer) — attribute definition, deprecation of `peer.service`
- [Spring Framework RestClient.Builder](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient) — builder API, `requestInterceptor()` registration, `ClientHttpRequestInterceptor` contract

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`otel-bootstrap/amqp/TracingMessagePostProcessor.java`** — THE structural template for `TracingClientHttpRequestInterceptor`. Same shape: constructor receives `OpenTelemetry + Tracer`, opens a span with `spanBuilder().setSpanKind(CLIENT).setAttribute(...)`, injects context via `propagator.inject()`, ends span in `finally`. The HTTP interceptor adds semconv attributes from the request/response rather than from AMQP properties.
- **`otel-bootstrap/amqp/MessagePropertiesSetter.java`** — THE structural template for `HttpHeadersSetter`. Same shape: `TextMapSetter<T>` with a single `set(carrier, key, value)` method. For HTTP: `headers.set(key, value)`.
- **`producer-service/config/RabbitConfig.java`** — THE structural template for `HttpClientConfig`. Same shape: `@Configuration` class that creates the tracing component as a `@Bean`, then wires it into the transport mechanism (`RabbitTemplate` / `RestClient.Builder`).
- **`producer-service/config/HttpServerSpanFilter.java`** — reference for HTTP semconv attribute usage (`HttpAttributes`, `ServerAttributes`, `UrlAttributes` imports). The CLIENT span mirrors these attributes from the outbound request perspective.

### Established Patterns

- **D-01 inline span template** — `spanBuilder().setSpanKind().setAttribute(...).startSpan()` → `try(Scope) { work } catch { recordException, setStatus(ERROR) } finally { span.end() }`. The interceptor follows this exactly.
- **CLIENT span for outbound calls** — Phase 8 `OrderRepository` (JDBC→PostgreSQL), Phase 14 `TracingRepositoryAspect` (JPA→PostgreSQL), and `InstrumentedJedisPool` (→Valkey) all use `SpanKind.CLIENT`. The HTTP interceptor is the fourth CLIENT span type.
- **Semconv attribute constants** — always via typed constants from `io.opentelemetry.semconv`, never string literals. `service.peer.name` may need a string literal if not yet stabilized in semconv 1.40.0 (researcher checks).
- **Single `Tracer` injected via constructor** — every class that creates spans receives `Tracer` as a constructor parameter.
- **Propagator reuse** — `openTelemetry.getPropagators().getTextMapPropagator()` per Phase 3 D-04.

### Integration Points

- **`OrderService.place()` line ~128** — the outbound HTTP call inserts AFTER `publisher.publish(orderId, payload)` and BEFORE `ordersCreated.add(1, ...)`. Wrapped in its own try/catch for fire-and-forget semantics.
- **`producer-service/pom.xml`** — no new dependency needed (`spring-boot-starter-web` already includes `RestClient` since Spring Boot 3.2+)
- **`HttpServerSpanFilter`** — automatically creates a SERVER span for `POST /notifications` (it excludes only `/actuator/*`). This means the trace shows CLIENT → SERVER span nesting within the same JVM — powerful teaching moment.
- **Integration tests in `integration-tests/`** — may need a new test asserting the CLIENT span presence and semconv attributes via `InMemorySpanExporter`.

</code_context>

<specifics>
## Specific Ideas

- The user chose **after-publish placement** (D-H1) — the trace waterfall reads top-down as "familiar PRODUCER span first, then new CLIENT span." This signals the workshop narrative is additive: "you already know the AMQP flow; now watch the HTTP hop layer on top." The README should follow this ordering.
- The user chose **fire-and-forget** (D-H2) — this teaches a production reality: notification failure shouldn't block the primary flow. Attendees see that OTel traces capture the failure for observability without coupling service availability. The interceptor's error path (F6-4) is exercised naturally when the stub is intentionally stopped or returns an error.
- The user chose **inline in OrderService** (D-H3) over a separate NotificationClient — same "boilerplate is the lesson" ethos from Phase 2. Attendees read the HTTP call right next to the AMQP publish: two outbound patterns (AMQP + HTTP) side by side in one method.
- The user chose **POST /notifications** with just `orderId` body (D-H4/D-H6) — minimal, mirrors AMQP identity. The stub is intentionally trivial: log traceparent, return 200. The stub's only job is proving propagation works.
- The user chose **HttpClientConfig parallel to RabbitConfig** (D-H8) — the structural symmetry is deliberate. Attendees who understood AMQP wiring recognize the same pattern for HTTP. The README should call out this parallel explicitly.
- The user chose **constructor-injected peerServiceName** (D-H10) — the interceptor stays reusable in `otel-bootstrap` while the `@Bean` factory provides target-specific configuration. This pattern scales to multiple outbound targets (each getting its own interceptor instance).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 15-outbound-http-client-spans*
*Context gathered: 2026-05-04*
