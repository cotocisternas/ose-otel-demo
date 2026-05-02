# Phase 2 Pattern Map

**Audience:** gsd-planner (reads this AFTER 02-RESEARCH.md, BEFORE authoring tasks)
**Mapped:** 2026-05-01
**New files:** 3 (2 SDK configs + 1 HTTP filter) | **Modified files:** 7 (5 instrumented classes + 2 service POMs)

## Files to Create

### `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
- **Role:** SDK config (`@Configuration` + `@Bean` factory) — duplicated by design (TRACE-01 / DOC-05)
- **Closest analog:** `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` (lines 12-37) — same package, same class shape (`@Configuration` exposing multiple `@Bean`s wired by constructor injection elsewhere)
- **Pattern to follow** (RabbitConfig.java:12-37):
  ```java
  @Configuration
  public class RabbitConfig {
      public static final String EXCHANGE = "orders";

      @Bean
      DirectExchange ordersExchange() { return new DirectExchange(EXCHANGE); }

      @Bean
      MessageConverter jsonMessageConverter() { return new Jackson2JsonMessageConverter(); }
  }
  ```
- **What's different in the new file:**
  - Beans returned: `OpenTelemetry` (with `destroyMethod="close"`) and `Tracer`
  - Heavy DOC-03 comment block at top (sampler tradeoff, env-var contract, per-service-duplication rationale, semconv-incubating note)
  - Inline `private static String envOrDefault(String, String)` helper for D-12 manual env-var read
- **Where to place it:** `com.example.producer.config.OtelSdkConfiguration` (alongside `RabbitConfig`)
- **Bean wiring:** see RESEARCH §A1, §A3, §A4, §A5, §A6, §A9 for verified API; see "Tracer Bean Wiring" section below for instrumentation-scope name

### `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
- **Role:** SDK config — DELIBERATELY DUPLICATED from producer (DOC-05)
- **Closest analog:** `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` (lines 9-18); also the about-to-be-written producer `OtelSdkConfiguration.java`
- **Pattern to follow:** identical structural copy of producer's `OtelSdkConfiguration`, with only TWO differences:
  ```java
  // line ~N: service.name
  .put(ServiceAttributes.SERVICE_NAME, "order-consumer")  // producer: "order-producer"
  // line ~M: tracer scope
  return openTelemetry.getTracer("com.example.consumer");  // producer: "com.example.consumer" → "com.example.producer"
  ```
- **What's different in the new file vs the producer config:** ONLY the two service-identity strings above. Resource attrs, sampler, exporter, propagators, BSP — all character-for-character identical. The DOC-05 callout depends on this.
- **Where to place it:** `com.example.consumer.config.OtelSdkConfiguration`

### `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
- **Role:** HTTP SERVER-span filter (D-05, D-06, D-07) — **producer ONLY**
- **Closest analog:** NONE in the existing codebase (no filter exists yet). Closest STRUCTURAL analog is `OrderListener.java` lines 12-28 — a Spring-managed `@Component`-style class with constructor-injected dependency that wraps a callback (here, the filter chain instead of a `@RabbitListener` body).
- **Pattern to follow** (OrderListener.java:12-28 — for the constructor + dependency-injected `Tracer` shape):
  ```java
  @Component
  public class OrderListener {
      private final ProcessingService processingService;

      public OrderListener(ProcessingService processingService) {
          this.processingService = processingService;
      }

      @RabbitListener(queues = RabbitConfig.QUEUE)
      public void onOrder(Map<String, Object> message) {
          // ...wrapped body...
      }
  }
  ```
- **What's different in the new file:**
  - Extends `org.springframework.web.filter.OncePerRequestFilter` (NOT `@Component` — registered by `@Bean` in `OtelSdkConfiguration` per RESEARCH §A8)
  - Overrides `doFilterInternal(req, res, chain)` AND `shouldNotFilter(req)` (RESEARCH §A8 — `shouldNotFilter` is the canonical exclusion hook for `/actuator/`, NOT `FilterRegistrationBean.setUrlPatterns`)
  - Body wraps `chain.doFilter(req, res)` in the D-01 inline span (try/Scope/try/catch/finally)
  - HTTP semconv attrs set BEFORE `chain.doFilter` (method/path/scheme/server.*) and AFTER (status_code/route) — see RESEARCH §B for the exact 7-attribute set Claude's-Discretion locked
  - Wire-up bean lives in `OtelSdkConfiguration` per RESEARCH §A8 (`@Bean HttpServerSpanFilter httpServerSpanFilter(Tracer tracer)`)
- **Where to place it:** `com.example.producer.config.HttpServerSpanFilter` (same package as `OtelSdkConfiguration`)

## Files to Modify

### `producer-service/src/main/java/com/example/producer/api/OrderController.java`
- **Current state** (lines 11-23):
  ```java
  public class OrderController {
      private final OrderService orderService;

      public OrderController(OrderService orderService) {
          this.orderService = orderService;
      }

      @PostMapping
      public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
          String orderId = orderService.place(payload);
          return ResponseEntity.accepted().body(Map.of("orderId", orderId));
      }
  }
  ```
- **Modification:** **NONE.** SERVER span lives in the filter (D-07). The controller is a thin pass-through with no INTERNAL span (CONTEXT.md line 178: "current controller is a thin pass-through to OrderService.place, so it may NOT need a Tracer"). DO NOT add a `Tracer` parameter to the controller.

### `producer-service/src/main/java/com/example/producer/domain/OrderService.java`
- **Current state** (lines 10-22):
  ```java
  public class OrderService {
      private final OrderPublisher publisher;

      public OrderService(OrderPublisher publisher) { this.publisher = publisher; }

      public String place(Map<String, Object> payload) {
          String orderId = UUID.randomUUID().toString();
          publisher.publish(orderId, payload);
          return orderId;
      }
  }
  ```
- **Modification:** Add `Tracer` constructor parameter; wrap `place(...)` body in INTERNAL span (`OrderService.place`). No semconv attrs mandated (D-04).
- **Pattern:** apply the D-01 template (see "Span-Wrapping Pattern" section below) around the entire body of `place(...)`. The `String orderId = ...` and `return orderId;` both live INSIDE the try block.

### `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`
- **Current state** (lines 11-24):
  ```java
  public class OrderPublisher {
      private final RabbitTemplate rabbitTemplate;

      public OrderPublisher(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }

      public void publish(String orderId, Map<String, Object> payload) {
          Map<String, Object> message = new HashMap<>(payload);
          message.put("orderId", orderId);
          rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
      }
  }
  ```
- **Modification:** Add `Tracer` constructor parameter; wrap entire `publish(...)` body in PRODUCER span (`orders.created publish` per D-04 — note span name uses queue name `orders.created`, while routing key `order.created` is set as an attribute per D-11).
- **Pattern:** D-01 inline template with `.setSpanKind(SpanKind.PRODUCER)` and 4 attrs (see Application Map row 3 below; constants per RESEARCH §B incubating table). All three lines (HashMap copy, put, convertAndSend) inside the try block.

### `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java`
- **Current state** (lines 13-28):
  ```java
  public class OrderListener {
      private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
      private final ProcessingService processingService;

      public OrderListener(ProcessingService processingService) { this.processingService = processingService; }

      @RabbitListener(queues = RabbitConfig.QUEUE)
      public void onOrder(Map<String, Object> message) {
          Object orderId = message.get("orderId");
          LOG.info("OrderCreated received: orderId={}", orderId);
          processingService.process(message);
      }
  }
  ```
- **Modification:** Add `Tracer` constructor parameter; wrap `onOrder(...)` body in CONSUMER span using **`Context.root()` parent** (D-10). Required multi-line teaching comment ABOVE the `spanBuilder` call previewing Phase 3's `propagator.extract(...)` shape (D-10 verbatim — load-bearing per `<specifics>`).
- **Pattern:** D-01 template with `.setParent(Context.root()).setSpanKind(SpanKind.CONSUMER)` and 3 messaging attrs (Application Map row 4). Existing `LOG.info(...)` and `processingService.process(...)` calls move INSIDE the try block.

### `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`
- **Current state** (lines 8-13):
  ```java
  public class ProcessingService {
      public void process(Map<String, Object> order) {
          // Phase 1: simulated domain work, in-memory only.
          // Phase 3 wires up the deterministic 10% failure path (APP-04).
      }
  }
  ```
- **Modification:** Add `Tracer` constructor parameter (introduces the field — currently no fields); wrap `process(...)` body in INTERNAL span (`ProcessingService.process`). Body remains the empty Phase 1 comment block, but the comment moves INSIDE the try block so the span has a body to wrap.

## POM Modifications (both `producer-service/pom.xml` and `consumer-service/pom.xml`)

### Current dependencies block (producer-service/pom.xml lines 20-44; consumer-service/pom.xml lines 20-48)

```xml
<!-- Spring Boot starters only. NO OpenTelemetry deps in Phase 1 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

Also note: the **Phase 1 invariant comment** on line 22-25 ("NO OpenTelemetry deps in Phase 1 — that is the Phase 1 invariant verified by Task 3 of this plan") MUST BE UPDATED — Phase 2 INVERTS this contract (RESEARCH §"Wave 0 Gaps" + RESEARCH §Open Q #3). Replace with: "Phase 2 onward: ONE version per OTel artifact, enforced by `dependencyConvergence` (parent pom.xml lines 113-145)."

### Additions for Phase 2 (5 new `<dependency>` entries, NO `<version>` tags — all BOM-managed)

```xml
<!-- OpenTelemetry SDK (BOM-managed: opentelemetry-bom:1.61.0 from parent pom.xml lines 57-63) -->
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-api</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-sdk</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Semconv stable (NOT BOM-managed — pin version directly per RESEARCH §B) -->
<dependency>
  <groupId>io.opentelemetry.semconv</groupId>
  <artifactId>opentelemetry-semconv</artifactId>
  <version>1.40.0</version>
</dependency>

<!-- Semconv incubating (RESEARCH FLAG #2 — REQUIRED for messaging + deployment attrs; NOT optional) -->
<dependency>
  <groupId>io.opentelemetry.semconv</groupId>
  <artifactId>opentelemetry-semconv-incubating</artifactId>
  <version>1.40.0-alpha</version>
</dependency>
```

**Forbidden additions (planner must NOT include):**
- `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure` — RESEARCH §A10 + D-12 explicitly forbid; `System.getenv` directly
- `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` — Phase 5 only
- Legacy `io.opentelemetry:opentelemetry-semconv` (the deprecated one shipped in the SDK BOM) — D-13 + RESEARCH §"Sources" forbid

## Span-Wrapping Pattern (D-01) Application Map

**5 inline span sites** (D-01 — pure inline, NO helper class, NO AOP):

| # | Call site | New file? | Span name | Span kind | Required attributes (semconv constants per RESEARCH §B) |
|---|---|---|---|---|---|
| 1 | `HttpServerSpanFilter.doFilterInternal` (producer) | NEW | `POST /orders` (D-04: METHOD ROUTE) | SERVER | `HttpAttributes.HTTP_REQUEST_METHOD`, `UrlAttributes.URL_PATH`, `UrlAttributes.URL_SCHEME`, `ServerAttributes.SERVER_ADDRESS`, `ServerAttributes.SERVER_PORT`, `HttpAttributes.HTTP_ROUTE` set pre-`chain.doFilter`; `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` set post |
| 2 | `OrderService.place` (producer) | MODIFIED | `OrderService.place` | INTERNAL | (none mandated per D-04) |
| 3 | `OrderPublisher.publish` (producer) | MODIFIED | `orders.created publish` (D-04 + D-11 note: span-name uses queue) | PRODUCER | `MessagingIncubatingAttributes.MESSAGING_SYSTEM` = `MessagingSystemIncubatingValues.RABBITMQ`; `MESSAGING_DESTINATION_NAME` = `"orders.created"`; `MESSAGING_OPERATION_TYPE` = `MessagingOperationTypeIncubatingValues.SEND`; `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY` = `"order.created"` (D-11) |
| 4 | `OrderListener.onOrder` (consumer) | MODIFIED | `orders.created process` (D-04) | CONSUMER | Same 3-attr base as #3 but with `MESSAGING_OPERATION_TYPE = PROCESS`; **`.setParent(Context.root())` mandatory per D-10**; **multi-line teaching comment mandatory** previewing Phase 3 `propagator.extract(...)` |
| 5 | `ProcessingService.process` (consumer) | MODIFIED | `ProcessingService.process` | INTERNAL | (none mandated per D-04) |

**RESEARCH FLAG #1 reminder (planner pasting from CONTEXT.md TRACE-07/08):** the literal key in the Resource/attribute call MUST be `MESSAGING_OPERATION_TYPE` (key `"messaging.operation.type"`) and the value MUST come from the enum (`SEND` / `PROCESS`), NOT the deprecated `MESSAGING_OPERATION` / literal `"publish"`. One-line teaching comment recommended (RESEARCH §Open Q #1):
```java
// semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create
```

### The pure-inline span template (D-01)

```java
Span span = tracer.spanBuilder("name")
    .setSpanKind(SpanKind.X)              // SERVER | INTERNAL | PRODUCER | CONSUMER
    .setAttribute(KEY_A, valueA)          // semconv constants per row above
    .setAttribute(KEY_B, valueB)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    // method body — every existing line moves inside this block
} catch (RuntimeException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;                               // D-03: catch present in Phase 2 even though no fail path exists yet
} finally {
    span.end();
}
```

CONSUMER variant (D-10 — only difference is `.setParent(...)` and the comment):
```java
// Phase 2: starting from Context.root() because no propagation yet —
// Phase 3 replaces this with:
//   Context extracted = propagator.extract(Context.root(), messageProperties, getter);
// The structural shape stays IDENTICAL.
Span span = tracer.spanBuilder("orders.created process")
    .setParent(Context.root())
    .setSpanKind(SpanKind.CONSUMER)
    // ... attrs ...
    .startSpan();
```

## Tracer Bean Wiring (D-02)

Lives inside each `OtelSdkConfiguration`. **Instrumentation scope = service package** (per CONTEXT.md D-02):

```java
// producer (com.example.producer.config.OtelSdkConfiguration)
@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");
}

// consumer (com.example.consumer.config.OtelSdkConfiguration)
@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.consumer");
}
```

The `OpenTelemetry` bean uses `destroyMethod = "close"` for graceful flush (RESEARCH §A7):
```java
@Bean(destroyMethod = "close")
OpenTelemetry openTelemetry() {
    // ...build SdkTracerProvider + propagators...
    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(propagators)
        .build();   // NOT buildAndRegisterGlobal() per RESEARCH §A1
}
```

## What Phase 2 DOES NOT Touch (verify before changing)

- **`otel-bootstrap/src/main/java/com/example/otel/package-info.java`** — Phase 1 placeholder; Phase 3 populates with `TracingMessagePostProcessor` + `TracingMessageListenerAdvice`. Phase 2 leaves untouched (CONTEXT.md `<code_context>` line 166).
- **`producer-service/src/main/resources/application.yaml`** + **`consumer-service/src/main/resources/application.yaml`** — `spring.application.name` already set; OTel env vars resolve via mise.toml. NO YAML changes (CONTEXT.md `<code_context>` line 183).
- **`mise.toml` env vars** — `OTEL_EXPORTER_OTLP_ENDPOINT` (line 22) + `OTEL_EXPORTER_OTLP_PROTOCOL` (line 23) already pre-wired; OtelSdkConfiguration READS these (CONTEXT.md `<code_context>` line 170).
- **`mise.toml` task `verify:bom`** (lines 121-132) — currently asserts ZERO OTel libs (Phase 1 gate). Per RESEARCH §"Wave 0 Gaps" + Open Q #3 the planner SHOULD invert it to assert "ONE version per OTel artifact" (Phase 2 invariant). Low severity / Claude's-Discretion-class task — flag for the planner to schedule.
- **`mise.toml` other tasks** — `dev`, `dev:producer`, `dev:consumer`, `preflight`, `infra:up`, `demo:order` reused as-is. Optional `verify:traces` task (Claude's discretion per CONTEXT.md decisions line 118).
- **Parent `pom.xml`** — BOM imports + maven-enforcer rules already correct (RESEARCH §B confirms BOM order is load-bearing AND verified). No parent-POM edits.
- **`ProducerApplication.java` / `ConsumerApplication.java`** — `@SpringBootApplication` main classes; Spring auto-discovers the new `@Configuration` and `@Bean Filter`. No edits.
- **Phase 1 success-criteria assertions in plans 1-01 through 1-06** — those are baseline-state assertions; Phase 2 changes the OTel libs constraint. Flagged in RESEARCH §"Wave 0 Gaps". Planner does NOT amend Phase 1 plan files.

## PATTERN MAPPING COMPLETE
