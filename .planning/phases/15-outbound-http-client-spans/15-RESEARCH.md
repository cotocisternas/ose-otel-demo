# Phase 15: Outbound HTTP-Client Spans - Research

**Researched:** 2026-05-04
**Domain:** Spring RestClient + OTel SDK 1.61.0 HTTP CLIENT spans + semconv 1.40.0 HTTP/service attributes
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-H1:** HTTP call placement — AFTER `publisher.publish()`, BEFORE `ordersCreated.add()`.
- **D-H2:** Fire-and-forget — catch exception, log warning, continue to counter. Order is already AMQP-published.
- **D-H3:** Inline in `OrderService` — `RestClient.Builder` injected in constructor, built once (`this.restClient = restClientBuilder.build()`), call inline in `place()`. No separate `NotificationClient` class.
- **D-H4:** `POST /notifications` endpoint in `NotificationStubController`.
- **D-H5:** Stub logs `traceparent` header at INFO and returns `200 OK` with empty body. `@RequestHeader(value = "traceparent", required = false)`.
- **D-H6:** Request body is `Map.of("orderId", orderId)`.
- **D-H7:** URL via `app.notification-url` in `application.yaml`, default `http://localhost:8080/notifications`. `@Value("${app.notification-url}")` in `OrderService`.
- **D-H8:** New `HttpClientConfig` `@Configuration` class (parallel to `RabbitConfig`) — creates `TracingClientHttpRequestInterceptor` as `@Bean`, produces `RestClient.Builder` `@Bean` with interceptor registered.
- **D-H9:** `service.peer.name = "notification-service"` (static, descriptive).
- **D-H10:** `peerServiceName` as constructor parameter in `TracingClientHttpRequestInterceptor`.

### Claude's Discretion

- Exact semconv attribute constants for HTTP client spans (`HttpAttributes`, `ServerAttributes`, `UrlAttributes` from semconv 1.40.0)
- Whether `service.peer.name` has a stable constant or requires string literal
- `HttpHeadersSetter` implementation details
- Span naming convention for the CLIENT span
- `HttpServerSpanFilter` behavior on the stub's incoming request (automatic, no change needed)
- README §15 exact wording and structure
- Screenshot deferral to Phase 18 Playwright pipeline
- `verify:http-client-spans` bash implementation
- Integration test assertions for the CLIENT span
- Error handling details in the interceptor
- Whether `@Bean RestClient.Builder` replaces Spring Boot's auto-configured builder or customizes it via `RestClientCustomizer`

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HCLI-01 | `otel-bootstrap/http/TracingClientHttpRequestInterceptor.java` exports a `ClientHttpRequestInterceptor` wrapping each outbound call in `SpanKind.CLIENT` span with W3C header injection via `TextMapSetter<HttpHeaders>` | `ClientHttpRequestInterceptor` interface confirmed: single `intercept(HttpRequest, byte[], ClientHttpRequestExecution)` method. Pattern directly mirrors `TracingMessagePostProcessor`. `HttpHeaders.set(String, String)` is the `TextMapSetter` write method. |
| HCLI-02 | CLIENT span carries `http.request.method`, `server.address`, `server.port`, `url.full`, `http.response.status_code`, `service.peer.name` (NOT deprecated `peer.service`) — status code on both success and exception paths | All stable constants verified in semconv 1.40.0 jar. `service.peer.name` constant `ServiceIncubatingAttributes.SERVICE_PEER_NAME` confirmed in incubating 1.40.0-alpha jar. `peer.service` is the old name in `PeerIncubatingAttributes.PEER_SERVICE` — confirmed deprecated. |
| HCLI-03 | `OrderService.place()` calls outbound via injected `RestClient.Builder` bean (never `RestClient.create(url)`); OTel interceptor registered LAST in interceptor chain | `RestClient.Builder.requestInterceptor()` method confirmed. Spring Boot `RestClientAutoConfiguration` produces a `@ConditionalOnMissingBean` `@Scope(PROTOTYPE)` `RestClient.Builder` — defining our own `@Bean RestClient.Builder` in `HttpClientConfig` supersedes it cleanly. |
| HCLI-04 | `POST /orders` shows CLIENT span as child of producer INTERNAL span in Tempo; in-process `NotificationStubController` log confirms `traceparent` header arrived | `HttpServerSpanFilter` uses `shouldNotFilter()` to exclude only `/actuator/*` — `POST /notifications` is NOT excluded, so the stub's incoming request automatically gets a SERVER span wrapping the CLIENT span, enabling the teaching moment of CLIENT→SERVER within one JVM. |

</phase_requirements>

---

## Summary

Phase 15 completes the HTTP instrumentation arc started in v1.0 by adding the CLIENT-side counterpart to the existing `HttpServerSpanFilter` SERVER span. The implementation follows a single structural pattern that already exists in `otel-bootstrap/amqp/`: an interceptor that starts a CLIENT span, injects W3C context headers, executes the underlying call, records response attributes, and ends the span in a finally block.

The technical surface is narrow and well-bounded: two new classes in `otel-bootstrap/http/` (the interceptor + `TextMapSetter`), two new classes in `producer-service/` (a config bean + a stub controller), and edits to one domain class (`OrderService`) and one YAML file. No new Maven dependencies are required — `RestClient` ships in `spring-boot-starter-web` (Spring 6.1+, verified in Spring Boot 3.4.13), and all semconv constants exist in the already-pinned `opentelemetry-semconv:1.40.0` (stable) and `opentelemetry-semconv-incubating:1.40.0-alpha` JARs.

The key research findings: (1) `service.peer.name` has a typed constant `ServiceIncubatingAttributes.SERVICE_PEER_NAME` in the incubating semconv JAR — use it, do not use string literal; (2) the old deprecated attribute is `peer.service` in `PeerIncubatingAttributes.PEER_SERVICE` — never use it; (3) Spring Boot's auto-configured `RestClient.Builder` is `@ConditionalOnMissingBean` and `SCOPE_PROTOTYPE`, so defining `@Bean RestClient.Builder` in `HttpClientConfig` supersedes it; (4) the OTel spec names HTTP CLIENT spans `"METHOD /path"` or just `"METHOD"` — use `"POST /notifications"` for the workshop's single-target case; (5) the `ClientHttpResponse.getStatusCode()` returns `HttpStatusCode` — use `.value()` to get the int, and handle `getStatusCode()` throwing `IOException` in the exception path to satisfy F6-4.

**Primary recommendation:** Mirror `TracingMessagePostProcessor` exactly — same span lifecycle template, same propagator reuse, same constructor injection shape — substituting `ClientHttpRequestInterceptor` for `MessagePostProcessor` and `HttpHeaders` for `MessageProperties`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HTTP CLIENT span lifecycle (start/inject/execute/end) | `otel-bootstrap` (shared) | — | Exchange-agnostic propagation helper; reusable by both services. Mirrors AMQP `TracingMessagePostProcessor` placement (PROP-04). |
| `TextMapSetter<HttpHeaders>` | `otel-bootstrap` (shared) | — | Pure Java adapter; no Spring dependencies; parallel to `MessagePropertiesSetter`. |
| `RestClient.Builder` `@Bean` with interceptor registration | `producer-service/config` | — | Producer-service–specific wiring (which target, what peer name). Mirrors `RabbitConfig`. |
| Notification stub endpoint (`POST /notifications`) | `producer-service/api` | — | HTTP surface lives where the HTTP layer lives (same producer Tomcat). |
| `@Value`-injected `notifyUrl` and outbound call | `producer-service/domain` (OrderService) | — | Domain layer orchestrates the full `place()` flow. |
| `app.notification-url` property | `producer-service` application.yaml | — | Per-service configuration, consistent with RabbitMQ/Valkey connection strings. |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.opentelemetry.semconv:opentelemetry-semconv` | `1.40.0` | `HttpAttributes`, `ServerAttributes`, `UrlAttributes` stable constants | Already pinned in both producer-service and otel-bootstrap pom.xml. Verified in local Maven cache. [VERIFIED: local jar inspection] |
| `io.opentelemetry.semconv:opentelemetry-semconv-incubating` | `1.40.0-alpha` | `ServiceIncubatingAttributes.SERVICE_PEER_NAME` constant | Already pinned. `SERVICE_PEER_NAME = stringKey("service.peer.name")` confirmed by decompiling the JAR. [VERIFIED: javap on local jar] |
| `spring-boot-starter-web` (includes `spring-web`) | `6.2.15` (managed by Spring Boot 3.4.13 BOM) | `RestClient`, `RestClient.Builder`, `ClientHttpRequestInterceptor`, `HttpRequest`, `HttpHeaders`, `ClientHttpResponse` | Already on producer-service classpath. No new dependency needed. [VERIFIED: Spring 6.2.15 jar found in local Maven cache] |
| `io.opentelemetry:opentelemetry-api` | `1.61.0` (BOM-managed) | `Tracer`, `Span`, `SpanKind.CLIENT`, `Context`, `Scope`, `StatusCode` | Already on otel-bootstrap classpath. [VERIFIED: project pom.xml] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `io.opentelemetry:opentelemetry-sdk-testing` | `1.61.0` (BOM-managed) | `InMemorySpanExporter` for integration test assertion of CLIENT span presence and attributes | Integration test for HCLI-04 — already used in `OrderFlowIT.java`. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Explicit `@Bean RestClient.Builder` in `HttpClientConfig` | `RestClientCustomizer` bean | `RestClientCustomizer` is the Spring Boot idiomatic extension point that customizes the auto-configured builder without replacing it. The explicit `@Bean` approach was chosen (D-H8) for workshop transparency — attendees see the wiring the same way they see `RabbitConfig`. Both approaches work; the explicit `@Bean` supersedes the auto-configured one via `@ConditionalOnMissingBean`. |
| `ServiceIncubatingAttributes.SERVICE_PEER_NAME` constant | String literal `"service.peer.name"` | The typed constant is preferred by the project's semconv discipline (no string literals for attributes with constants). The constant is in the incubating JAR already on classpath. Use the constant. |
| Span name `"POST /notifications"` | `"POST notification-service"` or just `"POST"` | OTel HTTP client span naming guidance: `"{METHOD} {target}"` where target is the low-cardinality path. `"POST /notifications"` is specific, readable, and workshop-friendly. Matches what attendees will see in Tempo. |

**No new Maven dependencies required.** All needed classes are on the classpath already.

---

## Architecture Patterns

### System Architecture Diagram

```
POST /orders (curl)
    │
    ▼
HttpServerSpanFilter (SERVER span)
    └─ OrderController.create()
         └─ OrderService.place()  [INTERNAL span]
              ├─ OrderPublisher.publish()  [PRODUCER span — existing Phase 3]
              │   TracingMessagePostProcessor injects traceparent + baggage into AMQP headers
              │
              └─ restClient.post().uri(notifyUrl).body(Map.of("orderId",orderId))
                   │   .retrieve().toBodilessEntity()  [fire-and-forget, wrapped in try/catch]
                   │
                   TracingClientHttpRequestInterceptor.intercept()  ← NEW
                   │   1. spanBuilder("POST /notifications").setSpanKind(CLIENT).startSpan()
                   │   2. span.makeCurrent() → Scope
                   │   3. propagator.inject(Context.current(), request, SETTER)
                   │      → writes traceparent/tracestate/baggage into HttpHeaders
                   │   4. execution.execute(request, body)  → HTTP call to :8080/notifications
                   │   5. span.setAttribute(HTTP_RESPONSE_STATUS_CODE, 200)
                   │   6. span.end() in finally
                   │
                   ▼
              NotificationStubController.notify() [POST /notifications]
                   │   HttpServerSpanFilter wraps this too — SERVER span as child of CLIENT span
                   │   Logs: "traceparent header: 00-<traceId>-<spanId>-01"
                   └─  returns 200 OK

              ordersCreated.add(1, attributes)  ← counter increments after HTTP call
```

### Recommended Project Structure

```
otel-bootstrap/src/main/java/com/example/otel/
  amqp/
    TracingMessagePostProcessor.java    (existing)
    MessagePropertiesSetter.java        (existing)
    MessagePropertiesGetter.java        (existing)
    TracingMessageListenerAdvice.java   (existing)
  http/
    TracingClientHttpRequestInterceptor.java  (NEW — Phase 15)
    HttpHeadersSetter.java                    (NEW — Phase 15)
  package-info.java                     (existing)

producer-service/src/main/java/com/example/producer/
  api/
    OrderController.java               (existing)
    NotificationStubController.java    (NEW — Phase 15)
  config/
    OtelSdkConfiguration.java          (NO CHANGE — D-H8 constraint)
    RabbitConfig.java                  (existing)
    HttpClientConfig.java              (NEW — Phase 15)
    HttpServerSpanFilter.java          (existing, no change)
  domain/
    OrderService.java                  (EDITED — Phase 15)
  ...
```

### Pattern 1: CLIENT Span Template (mirrors D-01 inline pattern)

The interceptor follows the same try/Scope/try/catch/finally idiom used by every existing span site in the project. The critical ordering: span starts BEFORE injection, injection happens inside the active Scope, `execution.execute()` runs after injection.

```java
// Source: otel-bootstrap existing TracingMessagePostProcessor + F6-2 pitfall mitigation
// otel-bootstrap/src/main/java/com/example/otel/http/TracingClientHttpRequestInterceptor.java

public class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    // Stateless / thread-safe; one instance per JVM is sufficient.
    private static final HttpHeadersSetter SETTER = new HttpHeadersSetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final String peerServiceName;  // D-H10: constructor parameter

    public TracingClientHttpRequestInterceptor(OpenTelemetry openTelemetry,
                                               Tracer tracer,
                                               String peerServiceName) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
        this.peerServiceName = peerServiceName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // F6-2: span STARTS here — before injection — so the new spanId is in Context
        // when the propagator writes traceparent into headers.
        Span span = tracer.spanBuilder(request.getMethod().name() + " " + request.getURI().getPath())
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.getMethod().name())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getURI().getHost())
            .setAttribute(ServerAttributes.SERVER_PORT, (long) request.getURI().getPort())
            .setAttribute(UrlAttributes.URL_FULL, request.getURI().toString())
            .setAttribute(ServiceIncubatingAttributes.SERVICE_PEER_NAME, peerServiceName)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Inject traceparent/tracestate/baggage AFTER span is current (F6-2).
            openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), request.getHeaders(), SETTER);

            // F6-3: execution.execute() runs AFTER injection (interceptor is registered last).
            ClientHttpResponse response = execution.execute(request, body);

            // F6-4 happy path: record status code when response is available.
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                (long) response.getStatusCode().value());
            if (response.getStatusCode().isError()) {
                span.setStatus(StatusCode.ERROR);
            }
            return response;

        } catch (IOException e) {
            // F6-4 exception path: status code may not be available; record exception.
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### Pattern 2: TextMapSetter for HttpHeaders (mirrors MessagePropertiesSetter)

```java
// Source: otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java (template)
// otel-bootstrap/src/main/java/com/example/otel/http/HttpHeadersSetter.java

public class HttpHeadersSetter implements TextMapSetter<HttpHeaders> {

    @Override
    public void set(HttpHeaders carrier, String key, String value) {
        // OTel TextMapSetter spec: carrier MAY be null. Defensive guard.
        if (carrier == null) {
            return;
        }
        // HttpHeaders.set(String, String) overwrites any existing value for the key.
        // W3CTraceContextPropagator always passes String values through set().
        carrier.set(key, value);
    }
}
```

### Pattern 3: HttpClientConfig (mirrors RabbitConfig)

```java
// Source: producer-service/.../config/RabbitConfig.java (template)
// producer-service/src/main/java/com/example/producer/config/HttpClientConfig.java

@Configuration
public class HttpClientConfig {

    // D-H9: static descriptive peer service name — teaches the production pattern.
    private static final String PEER_SERVICE_NAME = "notification-service";

    // D-H8: Creates interceptor as @Bean so it is injectable and testable.
    // Constructor receives OpenTelemetry + Tracer from OtelSdkConfiguration (same
    // pattern as TracingMessagePostProcessor @Bean in RabbitConfig).
    @Bean
    TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(
            OpenTelemetry openTelemetry, Tracer tracer) {
        return new TracingClientHttpRequestInterceptor(openTelemetry, tracer, PEER_SERVICE_NAME);
    }

    // D-H8: Explicit RestClient.Builder @Bean overrides Spring Boot's auto-configured
    // @ConditionalOnMissingBean PROTOTYPE builder. F6-1 mitigation: any code that
    // injects RestClient.Builder gets this builder with the OTel interceptor pre-registered.
    //
    // F6-3: interceptor registered via .requestInterceptor() — single interceptor in
    // this demo, so "last" = "only". Comment explicitly notes the production rule.
    @Bean
    RestClient.Builder restClientBuilder(
            TracingClientHttpRequestInterceptor tracingInterceptor) {
        return RestClient.builder()
            // F6-3: OTel interceptor registered LAST so traceparent is the final header set.
            // If additional interceptors exist (e.g., auth), add them BEFORE this call.
            .requestInterceptor(tracingInterceptor);
    }
}
```

### Pattern 4: OrderService edit (D-H1, D-H2, D-H3, D-H7)

The outbound call lands AFTER `publisher.publish()` and BEFORE `ordersCreated.add()`, wrapped in its own try/catch for fire-and-forget.

```java
// EDITED section inside OrderService.place(), after publisher.publish(orderId, payload)

// ---- Phase 15 D-H1/D-H2/D-H3: outbound HTTP notification (fire-and-forget) ----
//
// Call AFTER AMQP publish (D-H1): trace waterfall shows familiar PRODUCER span first,
// then new CLIENT span. Wrapped in its own try/catch (D-H2): notification failure
// does NOT roll back the published AMQP message; the order is already queued.
// D-H3: inline in this method — "boilerplate is the lesson." Attendees read AMQP
// publish (TracingMessagePostProcessor) and HTTP call (interceptor) side by side.
try {
    restClient.post()
        .uri(notifyUrl)  // @Value("${app.notification-url}") injected in constructor
        .body(Map.of("orderId", orderId))
        .retrieve()
        .toBodilessEntity();
} catch (Exception e) {
    // F6-4 / D-H2: notification failure is observable in Tempo (CLIENT span status=ERROR
    // with http.response.status_code) but does NOT fail the order. Log at WARN so
    // attendees can find the failure in Loki without it contaminating the error rate.
    log.warn("Notification call failed for order {}; continuing (fire-and-forget): {}",
        orderId, e.getMessage());
}
```

### Pattern 5: NotificationStubController (D-H4, D-H5, D-H6)

```java
// producer-service/src/main/java/com/example/producer/api/NotificationStubController.java

@RestController
public class NotificationStubController {
    private static final Logger log = LoggerFactory.getLogger(NotificationStubController.class);

    // D-H4: POST /notifications — semantically correct (side-effect trigger), distinct from POST /orders.
    // D-H5: @RequestHeader required=false prevents 400 if propagation breaks during development.
    @PostMapping("/notifications")
    public ResponseEntity<Void> notify(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        // D-H5: Log the traceparent header so attendees can grep the producer log for proof.
        log.info("Notification received for order {}. traceparent header: {}",
            body.get("orderId"), traceparent);
        return ResponseEntity.ok().build();
    }
}
```

### Anti-Patterns to Avoid

- **`RestClient.create(url)` static factory (F6-1):** Creates a `RestClient` without any interceptors registered on the shared builder. The OTel interceptor is NEVER invoked. Symptom: CLIENT span never appears, downstream receives no `traceparent`. Always inject and build from the `RestClient.Builder` bean.
- **Inject before span (F6-2):** If `propagator.inject()` is called BEFORE `span.makeCurrent()`, the injected `traceparent` carries the PARENT span's `spanId`, not the CLIENT span's `spanId`. The downstream service becomes a sibling of the CLIENT span rather than a child. Always: start span → `makeCurrent` → inject → execute.
- **`http.response.status_code` missing on exception path (F6-4):** When `execution.execute()` throws an IOException, there is no `ClientHttpResponse` to read from. The catch block must still set `StatusCode.ERROR` and call `recordException(e)`. The status code may be absent from the span on this path — that is acceptable; the `status=ERROR` is the key signal. If the exception wraps an `HttpStatusCodeException` (RestClientResponseException subclass), the status code is available via `((HttpStatusCodeException) e).getStatusCode().value()`.
- **String literal for `service.peer.name` (semconv discipline):** Use `ServiceIncubatingAttributes.SERVICE_PEER_NAME` — the typed constant from the incubating semconv JAR already on the classpath.
- **`peer.service` (deprecated attribute):** The old `PeerIncubatingAttributes.PEER_SERVICE` constant maps to `"peer.service"` — confirmed in the jar. Do not use it. HCLI-02 explicitly forbids it.
- **Registering the interceptor NOT last (F6-3):** If a future interceptor modifies headers AFTER the OTel interceptor, `traceparent` may be overwritten or cleared. The code comment in `HttpClientConfig` documents the production rule.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| W3C header injection | Custom string-concat `"traceparent: 00-" + traceId + ...` | `openTelemetry.getPropagators().getTextMapPropagator().inject()` | The propagator handles trace flag bytes, sampling decisions, tracestate forwarding, and baggage header simultaneously. Manual assembly gets sampling flag wrong for non-sampled traces. |
| HTTP client factory | `RestTemplate` or raw `HttpURLConnection` | `RestClient.Builder` (Spring 6.1+, in `spring-boot-starter-web`) | `RestClient` is the modern synchronous client; `RestTemplate` is in maintenance mode. Both support `ClientHttpRequestInterceptor` — but `RestClient` is what the project's stack uses. |
| Peer service resolution | Dynamic lookup of downstream service's `service.name` | Static `peerServiceName = "notification-service"` | In this demo there is one target. Production pattern: static string per injected RestClient instance. |

**Key insight:** The `TextMapPropagator.inject()` call is the non-negotiable primitive — all propagation (traceparent, tracestate, baggage) flows through one call, using the composite propagator already wired in `OtelSdkConfiguration`.

---

## Common Pitfalls

### Pitfall 1: `RestClient.create(url)` bypasses all interceptors (F6-1)
**What goes wrong:** `RestClient.create("http://localhost:8080/notifications")` creates an instance without any of the interceptors registered on the Spring-managed `RestClient.Builder` bean. No span is created; no `traceparent` is injected. Tempo shows no CLIENT span. The stub log shows no `traceparent` header.
**Why it happens:** Static factory methods on `RestClient` bypass the builder's interceptor chain entirely.
**How to avoid:** Inject `RestClient.Builder restClientBuilder` as a constructor parameter in `OrderService`, call `this.restClient = restClientBuilder.build()` once. Never use `RestClient.create()`.
**Warning signs:** CLIENT span missing from Tempo trace; stub logs show `traceparent: null`.

### Pitfall 2: Span started AFTER injection (F6-2)
**What goes wrong:** If `propagator.inject()` is called before `span.makeCurrent()`, the active context at injection time is the INTERNAL span (the parent), not the new CLIENT span. The injected `traceparent` writes the INTERNAL span's `spanId` as the parent, not the CLIENT span's `spanId`. The downstream service becomes a child of the INTERNAL span, not the CLIENT span. The CLIENT span appears as a sibling.
**Why it happens:** Developers write "inject first so the request is ready before building the span."
**How to avoid:** Strict ordering: `spanBuilder(...).startSpan()` → `span.makeCurrent()` → `inject(Context.current(), ...)` → `execution.execute(...)` → `span.end()`.
**Warning signs:** In Tempo, the stub's SERVER span appears as a sibling of the CLIENT span, not a child. Stub `parentSpanId` points to the INTERNAL span's `spanId`.

### Pitfall 3: Missing `http.response.status_code` on exception path (F6-4)
**What goes wrong:** A naive interceptor sets `HTTP_RESPONSE_STATUS_CODE` only in the success branch. When the stub returns 5xx or the connection drops, the exception propagates out and the status code is never set. Tempo shows `status=ERROR` but no `http.response.status_code` attribute. Attendees cannot search Tempo for `http.response.status_code=503`.
**Why it happens:** Exception path exits before the attribute-setting line.
**How to avoid:** The catch block must call `span.setStatus(StatusCode.ERROR, ...)` and `span.recordException(e)`. If the exception is a `RestClientResponseException` (Spring's wrapper for HTTP error responses), the status code is available via `((RestClientResponseException) e).getStatusCode().value()` — set it explicitly.
**Warning signs:** `status=ERROR` on CLIENT span but no `http.response.status_code` attribute in Tempo span detail.

### Pitfall 4: Spring Boot `RestClientAutoConfiguration` conflict
**What goes wrong:** Spring Boot auto-configures a `RestClient.Builder` bean as `SCOPE_PROTOTYPE` and `@ConditionalOnMissingBean`. If `HttpClientConfig` defines `@Bean RestClient.Builder` as a singleton (the default `@Bean` scope), Spring Boot's auto-configuration backs off. However, there is a subtlety: the auto-configured builder is `PROTOTYPE` (each injection gets a fresh clone). The explicit `@Bean` in `HttpClientConfig` is singleton — every injection shares the same builder instance. For this demo (one `OrderService` injecting once), this is fine. Document it in the `HttpClientConfig` comment.
**Why it happens:** Misunderstanding of Spring Boot's prototype-scoped auto-configured beans.
**How to avoid:** The explicit `@Bean RestClient.Builder` in `HttpClientConfig` supersedes the auto-configured one. `spring.main.allow-bean-definition-overriding=true` is already set for integration tests. For the workshop, the explicit singleton builder works correctly.
**Warning signs:** `BeanDefinitionOverrideException` if `allow-bean-definition-overriding=false` and both definitions are present (rare, but check in ITs).

### Pitfall 5: `HttpServerSpanFilter` wraps `POST /notifications` — creates nested trace in Tempo
**What goes wrong:** This is NOT a pitfall — it is the teaching point. `HttpServerSpanFilter.shouldNotFilter()` excludes only `/actuator/*`. The stub's `POST /notifications` is a normal path that gets wrapped in a SERVER span by the filter. In Tempo, attendees see `CLIENT → SERVER` span nesting within the same JVM.
**Why it matters:** Some attendees may be confused seeing a SERVER span inside a producer-service CLIENT span. The README §15 must explicitly call out this in-JVM CLIENT→SERVER nesting as the proof that `traceparent` was correctly propagated.
**Warning signs:** None — expected behavior. But if the SERVER span is MISSING from the trace, it means `shouldNotFilter()` was accidentally changed to exclude `/notifications` or the stub path.

### Pitfall 6: Integration test span count — CLIENT span changes expected count
**What goes wrong:** `OrderFlowIT.happyPathProducesSingleTrace_traceAssertions()` currently expects `>= 5` spans: SERVER + INTERNAL_producer + PRODUCER + CONSUMER + INTERNAL_consumer. Phase 15 adds a CLIENT span to the producer side. The happy-path assertion must now await `>= 6` spans. Additionally, the in-process SERVER span for `POST /notifications` adds another, so `>= 7`.
**Why it happens:** Tests have hardcoded span-count waits.
**How to avoid:** The integration test update is a required task in the plan. The `Awaitility.await().until(() -> count >= N)` threshold must be raised. Alternatively, use `>= 6` and let the test be satisfied even if the in-process SERVER span from the stub arrives later.

---

## Code Examples

### Verified: `ClientHttpRequestInterceptor` interface contract

```java
// Source: [VERIFIED: javap on spring-web-6.2.15.jar]
public interface ClientHttpRequestInterceptor {
    ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                 ClientHttpRequestExecution execution) throws IOException;
}
```

### Verified: Stable semconv constants for HTTP CLIENT spans

```java
// Source: [VERIFIED: javap on opentelemetry-semconv-1.40.0.jar]
HttpAttributes.HTTP_REQUEST_METHOD   // AttributeKey<String>
HttpAttributes.HTTP_RESPONSE_STATUS_CODE   // AttributeKey<Long>
ServerAttributes.SERVER_ADDRESS      // AttributeKey<String>
ServerAttributes.SERVER_PORT         // AttributeKey<Long>
UrlAttributes.URL_FULL               // AttributeKey<String>
```

### Verified: `service.peer.name` constant in incubating semconv

```java
// Source: [VERIFIED: javap + source jar extraction on opentelemetry-semconv-incubating-1.40.0-alpha.jar]
// io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes
ServiceIncubatingAttributes.SERVICE_PEER_NAME
// = AttributeKey.stringKey("service.peer.name")

// DEPRECATED — do NOT use:
PeerIncubatingAttributes.PEER_SERVICE
// = AttributeKey.stringKey("peer.service")
```

### Verified: `RestClient.Builder.requestInterceptor()` API

```java
// Source: [VERIFIED: javap on spring-web-6.2.15.jar]
// org.springframework.web.client.RestClient.Builder
Builder requestInterceptor(ClientHttpRequestInterceptor interceptor);
Builder requestInterceptors(Consumer<List<ClientHttpRequestInterceptor>> interceptorsConsumer);
```

### Verified: `HttpHeaders.set()` for TextMapSetter implementation

```java
// Source: [VERIFIED: javap on spring-web-6.2.15.jar]
// org.springframework.http.HttpHeaders
void set(String headerName, String headerValue);
```

### Verified: `ClientHttpResponse.getStatusCode()` return type

```java
// Source: [VERIFIED: javap on spring-web-6.2.15.jar]
HttpStatusCode getStatusCode() throws IOException;
// Use: response.getStatusCode().value() → int
//      response.getStatusCode().isError() → boolean (4xx or 5xx)
```

### Verified: Spring Boot `RestClientAutoConfiguration` — `@ConditionalOnMissingBean`

```java
// Source: [VERIFIED: spring-boot-autoconfigure-3.4.13-sources.jar]
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnMissingBean
RestClient.Builder restClientBuilder(RestClientBuilderConfigurer restClientBuilderConfigurer) {
    return restClientBuilderConfigurer.configure(RestClient.builder());
}
// → Defining @Bean RestClient.Builder in HttpClientConfig supersedes this.
```

### Verified: OTel HTTP CLIENT span naming — spec guidance

```
// Source: [CITED: https://opentelemetry.io/docs/specs/semconv/http/http-spans/]
// HTTP CLIENT span name: "{METHOD} {target}" where target is a low-cardinality path.
// For this demo: "POST /notifications"
// Fallback (unknown method): "HTTP /path"
// Minimal (no target): "POST"
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `peer.service` attribute (`PeerIncubatingAttributes.PEER_SERVICE`) | `service.peer.name` (`ServiceIncubatingAttributes.SERVICE_PEER_NAME`) | semconv 1.40.0 (2026-02-19) | HCLI-02 explicitly requires the new name; old name would produce wrong attribute key `"peer.service"` in Tempo. |
| `RestTemplate` + `ClientHttpRequestInterceptor` | `RestClient.Builder` + `ClientHttpRequestInterceptor` | Spring 6.1 (Spring Boot 3.2+) | `RestClient` is the modern API; `RestTemplate` is in maintenance mode. Both support interceptors identically. |
| `WebClient` + `ExchangeFilterFunction` for reactive HTTP tracing | `RestClient` + `ClientHttpRequestInterceptor` for synchronous HTTP tracing | Spring 5.x → 6.x | `WebClient` requires Reactor/WebFlux dependency. Workshop explicitly excludes reactive propagation (REQUIREMENTS.md Out of Scope). |

**Deprecated/outdated:**
- `peer.service` string key / `PeerIncubatingAttributes.PEER_SERVICE`: superseded by `service.peer.name`. The old coordinate is in the semconv incubating JAR but marks the old string. HCLI-02 and CONTEXT.md both explicitly prohibit it.
- `RestTemplate.getInterceptors()` pattern: still works but `RestTemplate` is in maintenance mode since Spring 6. Use `RestClient.Builder.requestInterceptor()`.

---

## Assumptions Log

> All claims in this research were verified or cited. No unverified assumptions.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Span name format `"POST /notifications"` follows OTel HTTP client spec | Standard Stack / Code Examples | Low — spec guidance says `"{METHOD} {target}"` and the path qualifies as a low-cardinality target. If wrong, only the display name in Tempo differs; no functional impact. |
| A2 | Spring Boot `allow-bean-definition-overriding` is set in integration test context, preventing `BeanDefinitionOverrideException` when `HttpClientConfig` defines `RestClient.Builder` | Common Pitfalls | Low — `allow-bean-definition-overriding=true` is already set in `OrderFlowIT.startTwoSpringContexts()` for both producer and consumer contexts. The conflict scenario only arises if the property is absent. |

**If this table is effectively empty (all LOW risk):** All critical claims in this research were verified via jar inspection, source code reading, or official documentation.

---

## Open Questions

1. **Should the integration test update bump the span count to 6 or 7?**
   - What we know: Phase 15 adds 1 CLIENT span (producer → stub). The existing `HttpServerSpanFilter` automatically wraps the stub's `POST /notifications` with a SERVER span (not excluded by `/actuator/*` guard). So the in-process total adds 2 spans.
   - What's unclear: Does the `TestOtelConfiguration` in `integration-tests` capture the stub's SERVER span from `HttpServerSpanFilter`? If both contexts share the same `InMemorySpanExporter` (via `TestOtelHolder`), yes — both producer contexts export to the same exporter.
   - Recommendation: The plan should await `>= 7` spans for the happy path (SERVER + INTERNAL_producer + PRODUCER + CLIENT_http + SERVER_stub + CONSUMER + INTERNAL_consumer). The test should look for the CLIENT span by kind, not by total count.

2. **Does `app.notification-url` need to be configurable per-integration-test port?**
   - What we know: `OrderService` receives the URL via `@Value("${app.notification-url}")`. In integration tests, both producer and consumer contexts run on random ports. The notification URL `http://localhost:8080/notifications` will NOT work if the producer's test port is not 8080.
   - What's unclear: The default `http://localhost:8080/notifications` works for `mise run dev` (producer runs on 8080). But in IT, `server.port=0` → random port.
   - Recommendation: The plan must set `app.notification-url=http://localhost:${local.server.port}/notifications` in the producer's integration test context, OR read the port dynamically. The simplest approach: in `OrderFlowIT.startTwoSpringContexts()`, set `app.notification-url=http://localhost:0/notifications` initially and then override after the producer context starts with the actual port. Alternatively, the plan can use `@DynamicPropertySource` style by setting the property via `SpringApplicationBuilder.properties(...)` after capturing the port. This is a plan-level decision but the planner must address it.

---

## Environment Availability

> This phase is code-only changes within the existing project. No new external services are required.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Spring Web (`RestClient.Builder`) | HCLI-01, HCLI-03 | Yes | 6.2.15 (managed by Spring Boot 3.4.13 BOM) | — |
| `opentelemetry-semconv:1.40.0` | HCLI-02 | Yes | 1.40.0 (pinned directly) | — |
| `opentelemetry-semconv-incubating:1.40.0-alpha` | HCLI-02 (`service.peer.name`) | Yes | 1.40.0-alpha (pinned directly) | — |
| Tempo (for HCLI-04 human verification) | verify:http-client-spans task | Yes (Phase 10 infra) | 2.10.5 | — |
| RabbitMQ (for integration tests) | `OrderFlowIT` | Yes (Testcontainers) | rabbitmq:4.3-management-alpine | — |

---

## Validation Architecture

> `workflow.nyquist_validation` is explicitly `false` in `.planning/config.json`. Section omitted per config.

---

## Security Domain

> `security_enforcement` is `true` in `.planning/config.json`. ASVS Level 1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Notification stub is in-process, no auth required for workshop. |
| V3 Session Management | No | Stateless HTTP call. |
| V4 Access Control | No | Internal stub endpoint. CLAUDE.md explicitly excludes authentication/authorization from scope. |
| V5 Input Validation | Yes (LOW risk) | Stub receives `Map<String, Object>` body. The `orderId` is a UUID generated in-process — not user-controlled. No injection risk. |
| V6 Cryptography | No | No secrets; in-process HTTP call over loopback. |

### Known Threat Patterns for This Phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| `traceparent` header injection via malicious upstream | Tampering | Not applicable — in this demo the producer generates the `traceparent` itself. The stub's `@RequestHeader(required = false)` means a missing header is benign (logs null). |
| Unbounded `service.peer.name` cardinality in Tempo | Not STRIDE — observability concern | Static constant `"notification-service"` — not user-controlled. Bounded. |

**Security assessment:** This phase has minimal security exposure. The notification stub is an in-process endpoint with no auth requirement (explicitly excluded by CLAUDE.md). The HTTP call is loopback-only in the workshop context.

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: javap on `/home/coto/.m2/repository/io/opentelemetry/semconv/opentelemetry-semconv/1.40.0/opentelemetry-semconv-1.40.0.jar`] — `HttpAttributes`, `ServerAttributes`, `UrlAttributes` field names and types confirmed.
- [VERIFIED: javap on `/home/coto/.m2/repository/io/opentelemetry/semconv/opentelemetry-semconv-incubating/1.40.0-alpha/opentelemetry-semconv-incubating-1.40.0-alpha.jar`] — `ServiceIncubatingAttributes.SERVICE_PEER_NAME = stringKey("service.peer.name")` and `PeerIncubatingAttributes.PEER_SERVICE = stringKey("peer.service")` confirmed by source extraction.
- [VERIFIED: javap on `/home/coto/.m2/repository/org/springframework/spring-web/6.2.15/spring-web-6.2.15.jar`] — `ClientHttpRequestInterceptor` interface, `RestClient.Builder.requestInterceptor()`, `HttpHeaders.set(String, String)`, `ClientHttpResponse.getStatusCode()` API confirmed.
- [VERIFIED: source extraction from spring-boot-autoconfigure-3.4.13-sources.jar] — `RestClientAutoConfiguration` confirmed as `@ConditionalOnMissingBean` and `SCOPE_PROTOTYPE` for `RestClient.Builder`.
- [VERIFIED: codebase inspection] — `TracingMessagePostProcessor.java`, `MessagePropertiesSetter.java`, `RabbitConfig.java`, `HttpServerSpanFilter.java`, `OrderService.java`, `OrderFlowIT.java` — all read directly as structural templates.
- [CITED: https://opentelemetry.io/docs/specs/semconv/http/http-spans/] — HTTP CLIENT span naming: `"{METHOD} {target}"` format.
- [CITED: https://opentelemetry.io/docs/specs/semconv/general/attributes/] — `service.peer.name` attribute is Development stage, opt-in, identifies the remote service's logical name.
- [VERIFIED: `.planning/research/PITFALLS.md` §F6] — F6-1 through F6-4 pitfalls documented; research confirms all four apply to this phase.
- [VERIFIED: `.planning/phases/15-outbound-http-client-spans/15-CONTEXT.md`] — All decisions D-H1 through D-H10 locked.
- [VERIFIED: project pom.xml files] — No new Maven dependencies needed.

### Secondary (MEDIUM confidence)
- [CITED: `.planning/research/ARCHITECTURE.md` §Feature 6] — Code example sketch for `TracingClientHttpRequestInterceptor` (ARCHITECTURE.md was written during v2.0 research; verified against actual class signatures in this session).

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — all library versions verified in local Maven cache via `javap`; no new dependencies.
- Architecture: HIGH — structural templates exist in codebase; API contracts verified in Spring 6.2.15 jars.
- Semconv constants: HIGH — verified by decompiling the exact pinned JARs used by the project.
- Pitfalls: HIGH — F6-1 through F6-4 from PITFALLS.md verified against API signatures (e.g., `ClientHttpResponse.getStatusCode()` throws `IOException`, confirming the exception-path concern is real).
- Integration test impact: MEDIUM — open question on span count and notification URL in test context; planner must address.

**Research date:** 2026-05-04
**Valid until:** 2026-06-04 (semconv 1.40.0 and Spring Boot 3.4.13 are pinned; no changes expected within 30 days)
