# Phase 15: Outbound HTTP-Client Spans - Pattern Map

**Mapped:** 2026-05-04
**Files analyzed:** 8 (6 new, 2 edited)
**Analogs found:** 8 / 8

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `otel-bootstrap/src/main/java/com/example/otel/http/TracingClientHttpRequestInterceptor.java` | middleware (OTel interceptor) | request-response | `otel-bootstrap/.../amqp/TracingMessagePostProcessor.java` | exact (same span lifecycle template, same field shape, same propagator call) |
| `otel-bootstrap/src/main/java/com/example/otel/http/HttpHeadersSetter.java` | utility (TextMapSetter) | request-response | `otel-bootstrap/.../amqp/MessagePropertiesSetter.java` | exact (same interface, same null-guard, same single-method body) |
| `producer-service/src/main/java/com/example/producer/config/HttpClientConfig.java` | config (@Configuration) | request-response | `producer-service/.../config/RabbitConfig.java` | exact (same @Bean factory shape: tracing component bean + transport bean with interceptor registered) |
| `producer-service/src/main/java/com/example/producer/api/NotificationStubController.java` | controller (REST stub) | request-response | `producer-service/.../api/OrderController.java` | role-match (same @RestController + Logger + ResponseEntity pattern; no service layer needed in stub) |
| `producer-service/src/main/java/com/example/producer/domain/OrderService.java` | service (domain, EDITED) | request-response + event-driven | self (existing file with additions) | self-edit (add RestClient.Builder constructor param + fire-and-forget call after publisher.publish) |
| `producer-service/src/main/resources/application.yaml` | config (YAML, EDITED) | — | self (existing file with additive property) | self-edit (add `app.notification-url` following existing Valkey property pattern) |
| `mise.toml` | config (task runner, EDITED) | — | `[tasks."verify:jpa-spans"]` block in `mise.toml` | exact (same Tempo /api/search + jq + retry loop shape) |
| `README.md` | docs (EDITED) | — | Phase 14 README §14 section | role-match (additive §15 section, ~100-150 lines, follows prior phase precedent) |

---

## Pattern Assignments

### `otel-bootstrap/.../http/TracingClientHttpRequestInterceptor.java` (middleware, request-response)

**Analog:** `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java`

**Imports pattern** (lines 1-17):
```java
package com.example.otel.amqp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
// ... semconv imports (messaging-specific; HTTP interceptor uses HttpAttributes, ServerAttributes, UrlAttributes, ServiceIncubatingAttributes instead)
```

For `TracingClientHttpRequestInterceptor`, replace AMQP imports with:
```java
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpHeaders;
```

**Constructor + static SETTER field pattern** (analog lines 68-79):
```java
public class TracingMessagePostProcessor implements MessagePostProcessor {

    // Stateless / thread-safe; one instance per JVM is sufficient.
    private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TracingMessagePostProcessor(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }
```

For `TracingClientHttpRequestInterceptor`, the constructor adds a third parameter (`peerServiceName`), and the static SETTER is `HttpHeadersSetter`. Pattern:
```java
public class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final HttpHeadersSetter SETTER = new HttpHeadersSetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final String peerServiceName;   // D-H10: constructor parameter

    public TracingClientHttpRequestInterceptor(OpenTelemetry openTelemetry,
                                               Tracer tracer,
                                               String peerServiceName) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
        this.peerServiceName = peerServiceName;
    }
```

**Core span lifecycle pattern** (analog lines 87-113 — `postProcessMessage` 4-arg):
```java
Span span = tracer.spanBuilder(exchange + " publish")
    .setSpanKind(SpanKind.PRODUCER)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, ...)
    // ... more attributes
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
    propagator.inject(Context.current(), props, SETTER);
    return message;
} finally {
    span.end();
}
```

For `TracingClientHttpRequestInterceptor`, the core pattern extends this with:
- `SpanKind.CLIENT` instead of `SpanKind.PRODUCER`
- HTTP semconv attributes on `spanBuilder` call (method, server.address, server.port, url.full, service.peer.name)
- `try (Scope) { inject → execute → setAttribute(HTTP_RESPONSE_STATUS_CODE) }` plus explicit `catch (IOException)` with `recordException + setStatus(ERROR)` (unlike AMQP which uses try/finally only — HTTP has a real IOException path)
- Span name: `request.getMethod().name() + " " + request.getURI().getPath()` (e.g., `"POST /notifications"`)

**Error handling extension** (no analog in AMQP — use `TracingMessageListenerAdvice` lines 128-140 as error path template):
```java
// From TracingMessageListenerAdvice.java lines 128-140:
try (Scope scope = span.makeCurrent()) {
    return inv.proceed();
} catch (Throwable t) {
    span.recordException(recorded);
    span.setStatus(StatusCode.ERROR);
    throw t;
} finally {
    span.end();
}
```

Adapt for `IOException` (the `ClientHttpRequestInterceptor` contract):
```java
try (Scope scope = span.makeCurrent()) {
    openTelemetry.getPropagators().getTextMapPropagator()
        .inject(Context.current(), request.getHeaders(), SETTER);
    ClientHttpResponse response = execution.execute(request, body);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        (long) response.getStatusCode().value());
    if (response.getStatusCode().isError()) {
        span.setStatus(StatusCode.ERROR);
    }
    return response;
} catch (IOException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    span.end();
}
```

**Semconv attribute constants to use** (verified from RESEARCH.md against semconv 1.40.0 jars):
- `HttpAttributes.HTTP_REQUEST_METHOD` (AttributeKey<String>)
- `ServerAttributes.SERVER_ADDRESS` (AttributeKey<String>)
- `ServerAttributes.SERVER_PORT` (AttributeKey<Long>) — cast `request.getURI().getPort()` to `long`
- `UrlAttributes.URL_FULL` (AttributeKey<String>)
- `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` (AttributeKey<Long>) — use `response.getStatusCode().value()` cast to `long`
- `ServiceIncubatingAttributes.SERVICE_PEER_NAME` (AttributeKey<String>) — from incubating jar; do NOT use string literal; do NOT use deprecated `PeerIncubatingAttributes.PEER_SERVICE`

---

### `otel-bootstrap/.../http/HttpHeadersSetter.java` (utility, TextMapSetter)

**Analog:** `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java`

**Full file pattern** (analog lines 1-45 — copy exactly, substituting carrier type):
```java
// Analog (MessagePropertiesSetter.java lines 1-45):
package com.example.otel.amqp;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.amqp.core.MessageProperties;

public class MessagePropertiesSetter implements TextMapSetter<MessageProperties> {

    @Override
    public void set(MessageProperties carrier, String key, String value) {
        // OTel TextMapSetter spec: carrier MAY be null. Defensive guard
        if (carrier == null) {
            return;
        }
        carrier.setHeader(key, value);  // <-- AMQP write
    }
}
```

For `HttpHeadersSetter`, substitute:
- Package: `com.example.otel.http`
- Interface: `TextMapSetter<HttpHeaders>`
- Import: `org.springframework.http.HttpHeaders`
- Write call: `carrier.set(key, value)` (`HttpHeaders.set(String, String)` overwrites existing value — verified from spring-web-6.2.15.jar)
- Same null-guard, same Javadoc structure explaining the OTel spec requirement

---

### `producer-service/.../config/HttpClientConfig.java` (config @Configuration, request-response)

**Analog:** `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java`

**@Configuration + @Bean factory pattern** (analog lines 17-59):
```java
// RabbitConfig.java lines 17-59 (the tracing-component @Bean block):
@Configuration
public class RabbitConfig {

    @Bean
    TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry openTelemetry,
                                                             Tracer tracer) {
        return new TracingMessagePostProcessor(openTelemetry, tracer);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter,
                                   TracingMessagePostProcessor tracingMpp) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setBeforePublishPostProcessors(tracingMpp);
        return template;
    }
}
```

For `HttpClientConfig`, the parallel shape:
```java
@Configuration
public class HttpClientConfig {

    private static final String PEER_SERVICE_NAME = "notification-service";  // D-H9

    // Tracing component bean — same shape as tracingMessagePostProcessor @Bean in RabbitConfig.
    // Constructor receives OpenTelemetry + Tracer from OtelSdkConfiguration (same pattern).
    @Bean
    TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor(
            OpenTelemetry openTelemetry, Tracer tracer) {
        return new TracingClientHttpRequestInterceptor(openTelemetry, tracer, PEER_SERVICE_NAME);
    }

    // Transport bean — supersedes Spring Boot's @ConditionalOnMissingBean RestClient.Builder.
    // Registers the OTel interceptor LAST (F6-3). Single interceptor in this demo.
    @Bean
    RestClient.Builder restClientBuilder(TracingClientHttpRequestInterceptor tracingInterceptor) {
        return RestClient.builder()
            .requestInterceptor(tracingInterceptor);
    }
}
```

**Imports for HttpClientConfig** (derived from RabbitConfig lines 1-16, substituting Spring HTTP classes):
```java
import com.example.otel.http.TracingClientHttpRequestInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
```

---

### `producer-service/.../api/NotificationStubController.java` (controller, request-response)

**Analog:** `producer-service/src/main/java/com/example/producer/api/OrderController.java`

**Imports + class pattern** (analog lines 1-15):
```java
// OrderController.java lines 1-15:
package com.example.producer.api;

import com.example.producer.cache.IdempotencyService;
import com.example.producer.domain.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
```

For `NotificationStubController`, keep the Logger + ResponseEntity pattern, remove @RequestMapping on class, use `@PostMapping("/notifications")`:
```java
package com.example.producer.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class NotificationStubController {
    private static final Logger log = LoggerFactory.getLogger(NotificationStubController.class);

    @PostMapping("/notifications")
    public ResponseEntity<Void> notify(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        log.info("Notification received for order {}. traceparent header: {}",
            body.get("orderId"), traceparent);
        return ResponseEntity.ok().build();
    }
}
```

Note the `@RequestHeader(required = false)` pattern — also present in `OrderController` lines 26-27 (`@RequestHeader(value = "X-Idempotency-Key", required = false)`). This is the established project convention for optional headers.

---

### `producer-service/.../domain/OrderService.java` (service domain, EDITED)

**Self-edit with analog:** `OrderService.java` itself (existing file) + fire-and-forget try/catch pattern from `HttpServerSpanFilter.java` error handling.

**Constructor extension pattern** — add `RestClient.Builder` + `@Value`-injected `notifyUrl`. Mirror the existing constructor (lines 63-82):
```java
// EXISTING constructor (OrderService.java lines 63-82):
public OrderService(OrderPublisher publisher, Tracer tracer, Meter meter) {
    this.publisher = publisher;
    this.tracer = tracer;
    this.ordersCreated = meter.counterBuilder("orders.created")
        // ...
        .build();
}
```

After edit, add `RestClient.Builder restClientBuilder` and `@Value("${app.notification-url}") String notifyUrl`:
```java
// New fields:
private final RestClient restClient;
private final String notifyUrl;

// Constructor extension — new params come after existing params (publisher, tracer, meter):
public OrderService(OrderPublisher publisher, Tracer tracer, Meter meter,
                    RestClient.Builder restClientBuilder,
                    @Value("${app.notification-url}") String notifyUrl) {
    this.publisher = publisher;
    this.tracer = tracer;
    this.ordersCreated = meter.counterBuilder("orders.created")...build();
    this.restClient = restClientBuilder.build();  // D-H3: build once in constructor
    this.notifyUrl = notifyUrl;
}
```

**Fire-and-forget call insertion point** — after line 128 (`publisher.publish(orderId, payload);`), before line 157 (`ordersCreated.add(...)`):
```java
// D-H1: AFTER publisher.publish, BEFORE ordersCreated.add
// D-H2: fire-and-forget — catch swallows notification failures
// D-H3: inline in place() — no separate NotificationClient class
try {
    restClient.post()
        .uri(notifyUrl)
        .body(Map.of("orderId", orderId))
        .retrieve()
        .toBodilessEntity();
} catch (Exception e) {
    log.warn("Notification call failed for order {}; continuing (fire-and-forget): {}",
        orderId, e.getMessage());
}
```

**Logger field** — follow existing convention in `OrderController.java` line 15:
```java
// OrderController.java line 15 pattern:
private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
// OrderService does NOT have a logger yet; add one using same SLF4J convention
private static final Logger log = LoggerFactory.getLogger(OrderService.class);
```

---

### `producer-service/src/main/resources/application.yaml` (config YAML, EDITED)

**Self-edit analog** — follow existing property patterns in `application.yaml` (current lines 1-15):
```yaml
# EXISTING pattern (lines 10-14):
valkey:
  host: ${VALKEY_HOST:localhost}
  port: ${VALKEY_PORT:6379}
  idempotency-ttl-seconds: 3600
```

Add `app.notification-url` following the same top-level namespace pattern (no env-var interpolation needed for the default — it's a fixed in-process URL for the workshop):
```yaml
app:
  notification-url: http://localhost:8080/notifications
```

---

### `mise.toml` — `[tasks."verify:http-client-spans"]` (config task, EDITED)

**Analog:** `[tasks."verify:jpa-spans"]` block in `mise.toml` (lines 576-618)

**Full task structure to copy** (lines 576-618):
```toml
# Phase 14 — verify:jpa-spans (DBSP-05)
[tasks."verify:jpa-spans"]
description = "Phase 14 invariant: db.query.text attribute present in Tempo spans..."
run = """
set -e
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

echo "verify:jpa-spans: querying Tempo..."
for i in $(seq 1 $ATTEMPTS); do
  RESULT=$(curl -fsS 'http://localhost:3200/api/search?tags=service.name%3Dorder-consumer%20db.system.name%3Dpostgresql&limit=5' 2>&1) || {
    LAST_ERR="curl :3200 failed: $RESULT"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — Tempo not ready..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:jpa-spans timed out..."
    exit 1
  }
  if printf '%s' "$RESULT" | jq -e '.traces | length > 0' >/dev/null 2>&1; then break; fi
  LAST_ERR="no traces yet"
  [ "$i" -lt "$ATTEMPTS" ] && { echo "  attempt $i/$ATTEMPTS — $LAST_ERR; retrying..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:jpa-spans — $LAST_ERR..."
  exit 1
done

echo "verify:jpa-spans: GREEN..."
"""
```

For `verify:http-client-spans`, use the same retry loop with:
- `description`: Phase 15 invariant — `http.request.method` and `service.peer.name=notification-service` attributes present in Tempo CLIENT spans for `order-producer`
- Tempo query: search for `service.name=order-producer` + `span.kind=client` (or tag `http.request.method=POST`)
- Same `ATTEMPTS=6`, `SLEEP_SECS=5` defaults
- Same `jq -e '.traces | length > 0'` assertion
- GREEN message naming the expected CLIENT span name `"POST /notifications"` and `service.peer.name=notification-service`

---

## Shared Patterns

### D-01 Inline Span Template (apply to TracingClientHttpRequestInterceptor)

**Source:** `producer-service/.../config/HttpServerSpanFilter.java` lines 128-220 (the most complete example with try/Scope/try/catch/finally)

The canonical D-01 span template used across all span sites in the project:
```java
// From HttpServerSpanFilter.java lines 157-220:
Span span = tracer.spanBuilder(method + " " + path)
    .setSpanKind(SpanKind.SERVER)
    .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
    // ... more attributes
    .startSpan();
Scope scope = span.makeCurrent();
try {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus());
} catch (RuntimeException | ServletException | IOException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    scope.close();
    span.end();
}
```

Note: `TracingClientHttpRequestInterceptor` uses `try (Scope scope = span.makeCurrent())` (try-with-resources form from `TracingMessagePostProcessor` and `TracingMessageListenerAdvice`) rather than manual `scope.close()`. Both are correct; the try-with-resources form is used in `otel-bootstrap` classes.

### Propagator Reuse (apply to TracingClientHttpRequestInterceptor)

**Source:** `TracingMessagePostProcessor.java` lines 107-109
```java
try (Scope scope = span.makeCurrent()) {
    TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
    propagator.inject(Context.current(), props, SETTER);
```

The interceptor uses the same `openTelemetry.getPropagators().getTextMapPropagator()` call — never constructs a new `W3CTraceContextPropagator.getInstance()` directly (D-04).

### Constructor Injection Pattern (apply to all new classes)

**Source:** `OtelSdkConfiguration.java` lines 56-59, 619-621 (the `@Bean` factory methods)
```java
// OtelSdkConfiguration.java lines 56-59:
@Bean
TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry openTelemetry,
                                                         Tracer tracer) {
    return new TracingMessagePostProcessor(openTelemetry, tracer);
}

// OtelSdkConfiguration.java lines 619-621:
@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");
}
```

All classes that need `Tracer` or `OpenTelemetry` receive them as constructor parameters. No field injection (`@Autowired`). The `@Bean` factory in `HttpClientConfig` follows the same explicit constructor pattern.

### SLF4J Logger Pattern (apply to NotificationStubController, OrderService edit)

**Source:** `OrderController.java` line 15
```java
private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
```

The project uses `LoggerFactory.getLogger(SomeClass.class)`. `NotificationStubController` uses `log` (lowercase) consistent with the Spring convention — either `LOG` or `log` is acceptable; match the file context.

### @RequestHeader optional pattern (apply to NotificationStubController)

**Source:** `OrderController.java` lines 26-27
```java
@RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader
```

The stub uses the same `required = false` annotation to prevent 400 errors when the header is absent during development.

---

## No Analog Found

No files in this phase lack a close codebase analog. All 8 files have strong matches.

| File | Role | Data Flow | Match Notes |
|------|------|-----------|-------------|
| All 8 phase files | various | request-response | All have exact or role-match analogs in the existing codebase |

---

## Critical Implementation Notes for Planner

These are concrete pitfall-prevention items extracted from RESEARCH.md that must appear as explicit action steps in the plan:

1. **F6-1 (RestClient injection):** `OrderService` MUST inject `RestClient.Builder` as a constructor parameter and call `this.restClient = restClientBuilder.build()` in the constructor. Never use `RestClient.create(url)`.

2. **F6-2 (span before inject):** In `TracingClientHttpRequestInterceptor.intercept()`, the strict ordering is: `spanBuilder(...).startSpan()` → `span.makeCurrent()` → `propagator.inject(Context.current(), ...)` → `execution.execute(...)`. Injection MUST happen inside the active Scope.

3. **F6-3 (interceptor last):** `RestClient.builder().requestInterceptor(tracingInterceptor)` is the only interceptor in this demo. Comment must document "register OTel interceptor LAST" for future maintainers.

4. **F6-4 (status on exception path):** The `catch (IOException e)` block in the interceptor MUST call `span.recordException(e)` and `span.setStatus(StatusCode.ERROR, e.getMessage())`. The response status code may not be available on this path — that is acceptable; `status=ERROR` is the key signal.

5. **SERVICE_PEER_NAME constant:** Use `ServiceIncubatingAttributes.SERVICE_PEER_NAME` (from `opentelemetry-semconv-incubating:1.40.0-alpha` JAR, already on classpath). Never use string literal `"service.peer.name"` or deprecated `PeerIncubatingAttributes.PEER_SERVICE`.

6. **Integration test span count:** `OrderFlowIT.happyPathProducesSingleTrace_traceAssertions()` currently awaits `>= 5` spans. Phase 15 adds 1 CLIENT span (TracingClientHttpRequestInterceptor) + 1 SERVER span (HttpServerSpanFilter wrapping POST /notifications) = 2 additional spans. The await threshold must be raised to `>= 7`.

7. **Integration test notification URL:** The default `http://localhost:8080/notifications` will NOT work in integration tests where the producer's port is random (`server.port=0`). The plan must address how to configure `app.notification-url` dynamically in the IT context (e.g., via `SpringApplicationBuilder.properties(...)` after the producer context starts and its actual port is known).

---

## Metadata

**Analog search scope:** `otel-bootstrap/src/main/java/`, `producer-service/src/main/java/`, `integration-tests/src/test/java/`, `mise.toml`
**Files scanned:** 13 source files + mise.toml
**Pattern extraction date:** 2026-05-04
