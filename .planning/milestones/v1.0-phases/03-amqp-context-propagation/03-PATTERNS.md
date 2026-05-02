# Phase 3: AMQP Context Propagation - Pattern Map

**Mapped:** 2026-05-01
**Files analyzed:** 14 (5 NEW, 6 MODIFIED source, 3 pom.xml, 1 README, 1 git tag)
**Analogs found:** 13 / 14 (only the git tag has no source-file analog; convention inherited from Phase 2 `step-02-traces`)

## File Classification

| File | New/Modified | Role | Data Flow | Closest Analog | Match Quality |
|------|--------------|------|-----------|----------------|---------------|
| `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` | NEW | new-class | event-driven (PRODUCER inject side) | `producer-service/.../messaging/OrderPublisher.java` lines 39-84 (Phase 2 inline PRODUCER span) | exact (structural) |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` | NEW | new-class | event-driven (CONSUMER extract side) | `consumer-service/.../messaging/OrderListener.java` lines 46-80 (Phase 2 inline CONSUMER span) | exact (structural) |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` | NEW | new-class (utility) | transform (write to AMQP headers) | none in repo; pure 3-line `TextMapSetter<C>` impl | role-only |
| `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` | NEW | new-class (utility) | transform (read from AMQP headers) | none in repo; pure 5-line `TextMapGetter<C>` impl | role-only |
| `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` | NEW | new-class (domain exception) | none (data type) | none in repo; stock `RuntimeException` skeleton | role-only |
| `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` | MODIFIED | inline-edit (DELETE + slim) | request-response | self (Phase 2 baseline) — see "Transformation notes" | self-diff |
| `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` | MODIFIED | inline-edit (DELETE + slim) | event-driven | self (Phase 2 baseline) — see "Transformation notes" | self-diff |
| `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` | MODIFIED | bean-configuration | request-response | self (existing 4 `@Bean` methods) + RESEARCH.md §"Code Examples" lines 626-666 | exact (extension) |
| `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` | MODIFIED | bean-configuration | event-driven | self (existing 2 `@Bean` methods) + RESEARCH.md §"Code Examples" lines 692-739 | exact (extension) |
| `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` | MODIFIED | inline-edit (insert into D-03 try-block) | CRUD (in-memory counter) | self (existing D-01 + D-03 catch shape) | self-extension |
| `otel-bootstrap/pom.xml` | MODIFIED | pom-add-deps | n/a | `producer-service/pom.xml` + `consumer-service/pom.xml` (dependency block shape) | role-only (different scopes) |
| `producer-service/pom.xml` | MODIFIED | pom-add-deps | n/a | `producer-service/pom.xml` itself (existing `<dependency>` block shape) | self-extension |
| `consumer-service/pom.xml` | MODIFIED | pom-add-deps | n/a | `consumer-service/pom.xml` itself (existing `<dependency>` block shape) | self-extension |
| `README.md` | MODIFIED | documentation | n/a | `README.md` lines 90-92 ("Why is OtelSdkConfiguration.java duplicated?" callout) | exact (parallel structure) |
| `step-03-context-propagation` | NEW | git-tag | n/a | Phase 2's `step-02-traces` annotated tag (WORK-01 convention) | exact (convention) |

---

## Pattern Assignments

### `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` (new-class, event-driven)

**Analog:** `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` lines 39-84

**Why this analog:** Phase 2's inline PRODUCER span body is the structural template Phase 3 lifts into the shared module. Same `try (Scope s = span.makeCurrent()) { ... } finally { span.end(); }` skeleton; same four messaging semconv attributes; same `SpanKind.PRODUCER`; same builder chain. Phase 3 ADDS the `propagator.inject(...)` call (the only new behavior), CHANGES the destination from `RabbitConfig.QUEUE` to the `exchange` parameter (D-07 semconv correction), and DROPS the D-03 `catch (RuntimeException)` block (per Claude's-Discretion in CONTEXT.md — `propagator.inject(...)` over a String-valued setter is essentially infallible; broker errors propagate to the OrderService INTERNAL span).

**Concrete excerpt** (Phase 2 OrderPublisher.java lines 51-83 — the inline PRODUCER span body):
```java
Span span = tracer.spanBuilder("orders.created publish")
    .setSpanKind(SpanKind.PRODUCER)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
        MessagingSystemIncubatingValues.RABBITMQ)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
        RabbitConfig.QUEUE)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
        MessagingOperationTypeIncubatingValues.SEND)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
        RabbitConfig.ROUTING_KEY)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    Map<String, Object> message = new HashMap<>(payload);
    message.put("orderId", orderId);
    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
} catch (RuntimeException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();
}
```

**Transformation notes:**
1. **Move into 4-arg `MessagePostProcessor.postProcessMessage(Message, Correlation, String exchange, String routingKey)`** — RESEARCH §Pattern 1 confirms `RabbitTemplate.doSend()` invokes this overload (FLAG #2 resolved). The `exchange` and `routingKey` parameters arrive directly from the caller; no `RabbitConfig.*` constants are imported into otel-bootstrap.
2. **Span name:** `"orders.created publish"` → `exchange + " publish"` (D-07 semconv correction — Phase 3 uses exchange, Phase 2 used queue).
3. **`MESSAGING_DESTINATION_NAME`:** `RabbitConfig.QUEUE` → `exchange` parameter (D-07 semconv correction).
4. **`MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY`:** `RabbitConfig.ROUTING_KEY` → `routingKey` parameter (no semantic change; just lifted out of producer's static constants).
5. **Body:** the `convertAndSend(...)` call disappears (the post-processor runs INSIDE `RabbitTemplate.doSend()`); replaced with `propagator.inject(Context.current(), props, SETTER)` — the only NEW line of behavior. `MessageProperties props = message.getMessageProperties()` retrieved from the Message arg.
6. **`catch (RuntimeException) → recordException + setStatus(ERROR) + throw` block: REMOVED** per CONTEXT.md `### Claude's Discretion` and RESEARCH.md `### Claude's Discretion` (W3C inject + String setter is infallible; broker errors propagate up to `OrderService.place(...)`'s D-03 catch).
7. **Constructor:** `(OpenTelemetry openTelemetry, Tracer tracer)` per D-03. Stores both as final fields. Reads propagator via `openTelemetry.getPropagators().getTextMapPropagator()` per D-04.
8. **Implements both 4-arg AND 1-arg overloads.** RESEARCH.md §Pattern 1 shows the 1-arg overload as a defensive no-op `return message;` — `RabbitTemplate.doSend` invokes the 4-arg form for `setBeforePublishPostProcessors`, but `MessagePostProcessor`'s interface contract requires the 1-arg form.
9. **Static singleton SETTER field:** `private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter();` — stateless, thread-safe.

**Data flow:** Producer side. Receives `Message` post-conversion (after `Jackson2JsonMessageConverter` produced the body but BEFORE `RabbitTemplate` writes to AMQP wire). Writes `traceparent` + `tracestate` String values into `MessageProperties.headers`. Returns the same `Message` instance (mutated in place — Spring AMQP idiom). Span ends BEFORE wire-send (D-06).

---

### `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` (new-class, event-driven)

**Analog:** `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` lines 46-80

**Why this analog:** Phase 2's inline CONSUMER span body is the structural template. Same `try (Scope s = span.makeCurrent()) { ... } catch (RuntimeException e) { recordException + setStatus(ERROR) + throw } finally { end }` skeleton (the D-03 catch IS preserved here, unlike the producer side, because consumer-side errors are exactly what the advice exists to capture and surface on the CONSUMER span). Same `SpanKind.CONSUMER`. Same four messaging semconv attributes. Phase 3 REPLACES `.setParent(Context.root())` with `.setParent(extracted)` — the SINGLE LINE that joins the two traces (per CONTEXT.md `<specifics>`).

**Concrete excerpt** (Phase 2 OrderListener.java lines 55-79 — the inline CONSUMER span body):
```java
Span span = tracer.spanBuilder("orders.created process")
    .setParent(Context.root())               // <-- Phase 3 REPLACES with .setParent(extracted)
    .setSpanKind(SpanKind.CONSUMER)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
        MessagingSystemIncubatingValues.RABBITMQ)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
        RabbitConfig.QUEUE)
    .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
        MessagingOperationTypeIncubatingValues.PROCESS)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    Object orderId = message.get("orderId");
    LOG.info("OrderCreated received: orderId={}", orderId);
    processingService.process(message);
} catch (RuntimeException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();
}
```

**Transformation notes:**
1. **Class implements `org.aopalliance.intercept.MethodInterceptor`** — `Object invoke(MethodInvocation inv) throws Throwable`. Wired via `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` in consumer's RabbitConfig.
2. **Defensive Message extraction at top of `invoke(...)`:** RESEARCH.md FLAG #3 resolved — `inv.getArguments()[1]` is the `Message` (`[0]` is `Channel`). Use `instanceof Message message` pattern with early `return inv.proceed();` for the batch-listener edge case (Pitfall #6 in RESEARCH.md).
3. **Extract context BEFORE building span:** `MessageProperties props = message.getMessageProperties()` then `Context extracted = propagator.extract(Context.current(), props, GETTER)`. Note: `Context.current()` here is `Context.root()` because the listener runs on a fresh container thread (Pitfall #3 in RESEARCH.md, the very pitfall the advice exists to defeat).
4. **Span name:** `"orders.created process"` → `exchange + " process"` where `exchange = props.getReceivedExchange()` (D-07 semconv correction; Phase 2 used queue, Phase 3 uses exchange).
5. **`.setParent(Context.root())` → `.setParent(extracted)`** — THE LOAD-BEARING LINE per CONTEXT.md `<specifics>`. This is what makes `consumer.parentSpanId == producer.spanId` true (ROADMAP SC #1).
6. **`MESSAGING_DESTINATION_NAME`:** `RabbitConfig.QUEUE` → `exchange` (received from message properties).
7. **ADD `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY`** with `props.getReceivedRoutingKey()` — Phase 2's inline CONSUMER span omitted this; Phase 3 adds it for symmetry with PRODUCER side (D-10).
8. **`MESSAGING_OPERATION_TYPE`:** unchanged (PROCESS).
9. **Body inside try:** `return inv.proceed();` — synchronous; runs the entire `MessagingMessageListenerAdapter.onMessage(...)` → user `@RabbitListener` method body inside the CONSUMER span's scope (RESEARCH.md FLAG #1 resolved).
10. **Catch widened to `Throwable`** (not `RuntimeException` like Phase 2's inline span) — `MethodInterceptor.invoke` declares `throws Throwable`. RESEARCH.md §Pattern 2 example uses `catch (Throwable t)`.
11. **Constructor:** `(OpenTelemetry openTelemetry, Tracer tracer)` per D-03. Reads propagator via `openTelemetry.getPropagators().getTextMapPropagator()` per D-04.
12. **Static singleton GETTER field:** `private static final MessagePropertiesGetter GETTER = new MessagePropertiesGetter();` — stateless, thread-safe.

**Data flow:** Consumer side. Spring AMQP container thread receives a Message → ProxyFactory invokes advice chain → this advice's `invoke(...)` runs FIRST → `propagator.extract(...)` reads `traceparent` String from `MessageProperties.headers` → `extracted` Context becomes the parent of the CONSUMER span → `inv.proceed()` calls into `MessagingMessageListenerAdapter` → user's `@RabbitListener public void onOrder(Map)` body executes INSIDE the CONSUMER span's scope (so `LOG.info(...)` and `processingService.process(...)` see the right `Span.current()`).

---

### `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` (new-class, transform)

**Analog:** none in this codebase (no existing `TextMapSetter<C>` implementations). Stock OTel SDK contract.

**Why no analog:** Pure 3-line implementation of `io.opentelemetry.context.propagation.TextMapSetter<MessageProperties>`. The contract is fixed by the OTel SDK 1.61.0 `TextMapSetter` interface (`set(@Nullable C carrier, String key, String value)`).

**Concrete excerpt** (from RESEARCH.md §Pattern 3 — verified against `Spring AMQP v3.2.8 MessageProperties.java` headers map being `HashMap<String, Object>`):
```java
package com.example.otel.amqp;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.amqp.core.MessageProperties;

public class MessagePropertiesSetter implements TextMapSetter<MessageProperties> {
    @Override
    public void set(MessageProperties carrier, String key, String value) {
        if (carrier == null) return;                  // OTel TextMapSetter spec: carrier may be null
        carrier.setHeader(key, value);                // value is String — round-trips cleanly (PITFALLS.md #2)
    }
}
```

**Transformation notes:**
1. **Write `String`, NEVER `byte[]`** — load-bearing per PITFALLS.md #2. The byte[] regression is the #2 pitfall on the Phase 0 list; this class exists primarily to neutralize it. Even though `MessageProperties.setHeader(String, Object)` accepts an `Object`, the contract here is "always pass a String value" — the OTel `TextMapPropagator.inject()` calls `setter.set(carrier, key, value)` with String values, so this is automatic when the implementation matches the OTel contract.
2. **Defensive null guard on carrier** (`if (carrier == null) return;`) — required by the OTel `TextMapSetter` Javadoc which says the carrier may be `@Nullable`. Without the guard a NPE leaks from inside `propagator.inject(...)`.
3. **No Spring annotations** — pure-Java class per D-01. Constructed via `private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter();` inside `TracingMessagePostProcessor`. Stateless, thread-safe.
4. **JavaDoc** should call out PITFALLS.md #2 (the String-not-byte[] discipline) — the class's reason for existing.

**Data flow:** Producer side. Called by `W3CTraceContextPropagator.inject(...)` once per header (`traceparent`, `tracestate`, `baggage` if present). Writes to the `HashMap<String, Object>` backing `MessageProperties.headers`. The String values are then serialized by the AMQP client as AMQP `longstr` field types — round-trips correctly to the consumer.

---

### `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` (new-class, transform)

**Analog:** none in this codebase. Stock OTel SDK contract.

**Why no analog:** Pure implementation of `io.opentelemetry.context.propagation.TextMapGetter<MessageProperties>`. Two methods: `Iterable<String> keys(C carrier)` and `@Nullable String get(@Nullable C carrier, String key)`.

**Concrete excerpt** (from RESEARCH.md §Pattern 4):
```java
package com.example.otel.amqp;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.springframework.amqp.core.MessageProperties;

public class MessagePropertiesGetter implements TextMapGetter<MessageProperties> {
    @Override
    public Iterable<String> keys(MessageProperties carrier) {
        return carrier.getHeaders().keySet();         // headers map never null
    }

    @Nullable
    @Override
    public String get(@Nullable MessageProperties carrier, String key) {
        if (carrier == null) return null;
        Object raw = carrier.getHeader(key);
        return raw == null ? null : raw.toString();   // normalizes String / LongString / byte[]
    }
}
```

**Transformation notes:**
1. **Defensive `.toString()` normalization** — load-bearing per PITFALLS.md #2. Even if a misconfigured upstream wrote `byte[]` or AMQP delivers as `LongStringHelper.ByteArrayLongString` (subclass of `LongString` whose `toString()` decodes UTF-8), `.toString()` is idempotent for String, well-defined for LongString, and produces a UTF-8 decoded String for byte[]. Avoids the silent extraction-failure → fallback-to-root-span trap.
2. **Defensive null guards on both methods** — required by `@Nullable` annotations in the `TextMapGetter` Javadoc.
3. **`carrier.getHeaders().keySet()`** — Spring AMQP `MessageProperties.getHeaders()` returns a non-null `Map<String, Object>` per `MessageProperties.java` (verified in RESEARCH.md sources).
4. **No Spring annotations** — pure-Java class per D-01. Constructed via `private static final MessagePropertiesGetter GETTER = new MessagePropertiesGetter();` inside `TracingMessageListenerAdvice`. Stateless, thread-safe.
5. **JavaDoc** should call out the byte[]→String normalization (the symmetric counterpart to the Setter's PITFALLS.md #2 discipline).
6. **OPTIONAL setter↔getter round-trip unit test** (per RESEARCH.md `### Claude's Discretion`): a ~30-line JUnit 5 test in `otel-bootstrap/src/test/java/com/example/otel/amqp/` exercising `setter.set(props, "traceparent", "...")` then asserting `getter.get(props, "traceparent")` round-trips. Pure unit test (no Spring, no Testcontainers). Phase 6 alternative: defer to integration test. Planner picks.

**Data flow:** Consumer side. Called by `W3CTraceContextPropagator.extract(...)` to enumerate keys (find `traceparent`, `tracestate`) and read their values. Returns String form regardless of underlying header storage type. `extract` returns a `Context` whose span context is the producer's PRODUCER span — that becomes the parent of the CONSUMER span via `.setParent(extracted)`.

---

### `consumer-service/src/main/java/com/example/consumer/domain/ProcessingFailedException.java` (new-class, none)

**Analog:** none in this codebase (no existing custom exception classes — checked both `consumer-service/.../domain/` and `producer-service/.../domain/`). Stock Java `RuntimeException` skeleton.

**Why no analog:** Trivial domain-exception class. Spring/Java idiom; no existing pattern to copy.

**Concrete excerpt** (canonical Java RuntimeException-extension shape; matches D-12):
```java
package com.example.consumer.domain;

/**
 * Thrown by {@link ProcessingService#process(java.util.Map)} on the
 * deterministic 10%-failure path (APP-04). The fully qualified class name
 * surfaces as the {@code exception.type} attribute on the recordException
 * span event in Tempo (TRACE-09); the class name itself is documentation.
 */
public class ProcessingFailedException extends RuntimeException {
    public ProcessingFailedException(String message) {
        super(message);
    }
}
```

**Transformation notes:**
1. **Extends `RuntimeException`** per D-12 — must be unchecked so the listener thread can rethrow it without a `throws` declaration on `@RabbitListener public void onOrder(Map)`.
2. **Single-arg constructor `(String message)`** per D-12 — no cause chain needed (this is a deterministic synthetic failure, not a wrap-and-rethrow).
3. **Public visibility** — `OrderListener` doesn't catch it (passes through), but Spring AMQP's exception logging and the advice's `recordException(t)` call both need the class to be reachable. Public is simpler than package-private + cross-package shenanigans.
4. **No `serialVersionUID`** — modern Java practice for exceptions that don't cross JVM boundaries (this one is consumed by Spring AMQP within the same JVM and recorded as a span event).
5. **JavaDoc** notes: (a) the APP-04 / TRACE-09 link, (b) the `exception.type` attribute teaching point — the FQCN is the surface attendees see in Tempo.

**Data flow:** `ProcessingService.process(...)` throws on `count % 10 == 0` → caught by `ProcessingService`'s D-03 `catch (RuntimeException e)` → recordException on INTERNAL span + setStatus(ERROR) → rethrown → propagates through the thin `OrderListener.onOrder(...)` (no catch) → caught by `TracingMessageListenerAdvice.invoke`'s `catch (Throwable t)` → recordException on CONSUMER span + setStatus(ERROR) → rethrown → caught by Spring AMQP container → NACK + drop (D-13).

---

### `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` (inline-edit, request-response)

**Analog:** itself (Phase 2 baseline). The whole file is the analog and the edit target — Phase 3 is a deletion+slim diff.

**Why this analog:** Self-diff. The smallest-readable-changeset principle (CONTEXT.md `<specifics>`) demands the deletion be the dominant signal in the git diff.

**Concrete excerpt — current Phase 2 state (lines 30-85, the entire class body):**
```java
@Component
public class OrderPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Tracer tracer;

    public OrderPublisher(RabbitTemplate rabbitTemplate, Tracer tracer) {
        this.rabbitTemplate = rabbitTemplate;
        this.tracer = tracer;
    }

    public void publish(String orderId, Map<String, Object> payload) {
        Span span = tracer.spanBuilder("orders.created publish")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                RabbitConfig.QUEUE)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                RabbitConfig.ROUTING_KEY)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<String, Object> message = new HashMap<>(payload);
            message.put("orderId", orderId);
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**Transformation notes:**
1. **DELETE the `Tracer` field** (line 32) and the constructor's `Tracer tracer` parameter + assignment (lines 34-37). Constructor becomes `public OrderPublisher(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }`. Single-arg constructor is auto-wired by Spring without `@Autowired`.
2. **DELETE `publish(...)` body lines 40-83** (the inline PRODUCER span scaffolding — note: CONTEXT.md said lines 51-83, RESEARCH.md corrected to lines 39-84 covering the entire method body; the actual span/try/catch/finally block is lines 40-83 inside the method).
3. **REPLACE method body with thin 3-line shape** (D-05):
   ```java
   public void publish(String orderId, Map<String, Object> payload) {
       Map<String, Object> message = new HashMap<>(payload);
       message.put("orderId", orderId);
       rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
   }
   ```
4. **DELETE imports** (lines 6-13): `Span`, `SpanKind`, `StatusCode`, `Tracer`, `Scope`, `MessagingIncubatingAttributes`, `MessagingOperationTypeIncubatingValues`, `MessagingSystemIncubatingValues`. KEEP: `HashMap`, `Map`, `RabbitTemplate`, `Component`, `RabbitConfig`.
5. **UPDATE class-level JavaDoc** (lines 20-28) — Phase 2's JavaDoc says "Phase 2 wraps publish(...) in a PRODUCER span... Phase 3 will REPLACE this..." — Phase 3 should rewrite this to past-tense / current-state: "Publishes OrderCreated to the orders direct exchange. The PRODUCER span and traceparent injection are owned by `TracingMessagePostProcessor` registered on the `RabbitTemplate` bean (see `RabbitConfig.rabbitTemplate(...)`)."
6. **Net diff:** −33 lines (per CONTEXT.md `<specifics>` line 240) — the bulk of Phase 3's "deletion is the diff" property.

**Data flow:** Same as Phase 2 — receives `(orderId, payload)` from `OrderService.place(...)`, builds the message map, calls `convertAndSend(exchange, routingKey, message)`. The `RabbitTemplate` injected here is now the user-defined bean from `RabbitConfig`, which carries `setBeforePublishPostProcessors(tracingMpp)` — so `convertAndSend(...)` triggers the post-processor that creates the PRODUCER span + injects `traceparent`. The publish-call shape is identical; the instrumentation moved one layer down.

---

### `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` (inline-edit, event-driven)

**Analog:** itself (Phase 2 baseline). Self-diff matching the producer-side deletion.

**Why this analog:** Symmetric to `OrderPublisher.java`. Same deletion+slim shape.

**Concrete excerpt — current Phase 2 state (lines 33-81, entire class body):**
```java
@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;
    private final Tracer tracer;

    public OrderListener(ProcessingService processingService, Tracer tracer) {
        this.processingService = processingService;
        this.tracer = tracer;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrder(Map<String, Object> message) {
        Span span = tracer.spanBuilder("orders.created process")
            .setParent(Context.root())
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                RabbitConfig.QUEUE)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingOperationTypeIncubatingValues.PROCESS)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Object orderId = message.get("orderId");
            LOG.info("OrderCreated received: orderId={}", orderId);
            processingService.process(message);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**Transformation notes:**
1. **DELETE the `Tracer` field** (line 37) and the constructor's `Tracer tracer` parameter + assignment (lines 39-42). Constructor becomes `public OrderListener(ProcessingService processingService) { this.processingService = processingService; }`.
2. **DELETE `onOrder(...)` body lines 47-79** (the inline CONSUMER span/setParent(root)/4-attrs/try/catch/finally block).
3. **REPLACE method body with thin 3-line shape** (D-09):
   ```java
   @RabbitListener(queues = RabbitConfig.QUEUE)
   public void onOrder(Map<String, Object> message) {
       Object orderId = message.get("orderId");
       LOG.info("OrderCreated received: orderId={}", orderId);
       processingService.process(message);
   }
   ```
4. **KEEP `@RabbitListener(queues = RabbitConfig.QUEUE)`** annotation (line 45) — its handler is now wrapped by the user-defined `SimpleRabbitListenerContainerFactory` whose advice chain includes `TracingMessageListenerAdvice`.
5. **DELETE imports** (lines 5-13): `Span`, `SpanKind`, `StatusCode`, `Tracer`, `Context`, `Scope`, `MessagingIncubatingAttributes`, `MessagingOperationTypeIncubatingValues`, `MessagingSystemIncubatingValues`. KEEP: `Map`, `Logger`, `LoggerFactory`, `RabbitListener`, `Component`, `RabbitConfig`, `ProcessingService`.
6. **UPDATE class-level JavaDoc** (lines 23-32) — Phase 2's JavaDoc says "Phase 2 wraps onOrder(...) in a CONSUMER span... Phase 3 replaces the Context.root() with propagator.extract(...)..." — Phase 3 should rewrite to current-state: "AMQP listener — processes OrderCreated messages. The CONSUMER span and `propagator.extract(...)` are owned by `TracingMessageListenerAdvice` registered on the `SimpleRabbitListenerContainerFactory` bean (see `RabbitConfig.rabbitListenerContainerFactory(...)`). The `LOG.info(...)` line below runs INSIDE the CONSUMER span's scope (the advice's `Scope.makeCurrent()` is active when this method body runs) — Phase 5's MDC injector will pick it up automatically."
7. **Net diff:** −27 lines (per CONTEXT.md `<specifics>` line 241).

**Data flow:** Same as Phase 2 — Spring AMQP container delivers `Map<String, Object>` (the JSON-deserialized payload) to `onOrder`. NEW: that delivery is now wrapped by `TracingMessageListenerAdvice` whose `inv.proceed()` is what actually invokes this method. Inside the method, `Span.current()` is the CONSUMER span (parented to producer's PRODUCER span via the extracted W3C context). `LOG.info(...)` and `processingService.process(...)` both run inside that scope.

---

### `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` (bean-configuration, request-response)

**Analog (in-file):** existing `@Bean` methods in this file (lines 18-36) — `DirectExchange`, `Queue`, `Binding`, `MessageConverter`. **Analog (cross-reference):** RESEARCH.md §"Code Examples" lines 626-666 (the worked-out shape).

**Why this analog:** The new `@Bean` methods extend the same `RabbitConfig` class — same package, same `@Configuration` ceremony, same field/constant style. The RESEARCH.md example is the verified shape (Spring Boot 3.4.13 backoff + Spring AMQP 3.2.8 4-arg overload semantics).

**Concrete excerpt — existing `@Bean` shape in this file (lines 12-37):**
```java
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "orders";
    public static final String QUEUE = "orders.created";
    public static final String ROUTING_KEY = "order.created";

    @Bean
    DirectExchange ordersExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue ordersCreatedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding ordersBinding(Queue q, DirectExchange ex) {
        return BindingBuilder.bind(q).to(ex).with(ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

**Transformation notes:**
1. **KEEP existing 4 beans unchanged** — `EXCHANGE`/`QUEUE`/`ROUTING_KEY` constants, `ordersExchange()`, `ordersCreatedQueue()`, `ordersBinding(Queue, DirectExchange)`, `jsonMessageConverter()`. They are the analog AND they continue to ship as-is.
2. **ADD imports** at top of file: `com.example.otel.amqp.TracingMessagePostProcessor`, `io.opentelemetry.api.OpenTelemetry`, `io.opentelemetry.api.trace.Tracer`, `org.springframework.amqp.rabbit.connection.ConnectionFactory`, `org.springframework.amqp.rabbit.core.RabbitTemplate`.
3. **ADD `@Bean TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry, Tracer)`** — pure constructor wrapper per D-01. The same shape as the existing 4 beans (one builder/constructor call inside). Per CONTEXT.md `<specifics>`: ~6 added lines including JavaDoc.
4. **ADD `@Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter messageConverter, TracingMessagePostProcessor tracingMpp)`** — explicit override of Spring Boot's auto-created RabbitTemplate. Body:
   ```java
   RabbitTemplate template = new RabbitTemplate(cf);
   template.setMessageConverter(messageConverter);
   template.setBeforePublishPostProcessors(tracingMpp);
   return template;
   ```
   Spring Boot's `RabbitAutoConfiguration` backs off via `@ConditionalOnMissingBean(RabbitOperations.class)` — `RabbitTemplate implements RabbitOperations` (verified in RESEARCH.md §"RabbitAutoConfiguration Backoff").
5. **JavaDoc on the explicit `RabbitTemplate` bean** should explain (a) why we override Spring Boot (the post-processor hook) AND (b) the conversion-then-postprocess ordering (PITFALLS.md #12 — converter runs first, then post-processor adds headers).
6. **No `@Bean(name=...)` overrides** — bean method name = bean name; default Spring conventions handle injection.

**Data flow:** Spring Boot's `RabbitAutoConfiguration` no longer creates a `RabbitTemplate` (backed off by `RabbitOperations` conditional). Producer's `OrderPublisher` constructor-injects this `RabbitTemplate` bean (matched by type — only one in the context). When `OrderPublisher.publish` calls `convertAndSend(...)`, `RabbitTemplate.doSend(...)` runs → `Jackson2JsonMessageConverter` produces the byte body → `processBeforePublishMessageProcessors(...)` invokes `TracingMessagePostProcessor.postProcessMessage(message, correlation, exchange, routingKey)` → PRODUCER span created, traceparent injected as String header → message written to wire.

---

### `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` (bean-configuration, event-driven)

**Analog (in-file):** existing `@Bean` methods in this file (lines 13-17) — `Queue`, `MessageConverter`. **Analog (cross-reference):** RESEARCH.md §"Code Examples" lines 692-739 (the worked-out shape).

**Why this analog:** Same shape as producer's RabbitConfig but smaller — only `Queue` + `MessageConverter` exist before Phase 3. The same `@Configuration`/`@Bean` ceremony extends consistently.

**Concrete excerpt — existing `@Bean` shape in this file (lines 9-18):**
```java
@Configuration
public class RabbitConfig {
    public static final String QUEUE = "orders.created";

    @Bean Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }

    @Bean MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

**Transformation notes:**
1. **KEEP existing 2 beans unchanged** (`ordersCreatedQueue`, `jsonMessageConverter`) and the `QUEUE` constant.
2. **ADD imports:** `com.example.otel.amqp.TracingMessageListenerAdvice`, `io.opentelemetry.api.OpenTelemetry`, `io.opentelemetry.api.trace.Tracer`, `org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory`, `org.springframework.amqp.rabbit.connection.ConnectionFactory`, `org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer`.
3. **ADD `@Bean TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry, Tracer)`** — pure constructor wrapper per D-01.
4. **ADD `@Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory, SimpleRabbitListenerContainerFactoryConfigurer, TracingMessageListenerAdvice)`** — Configurer-aided shape per D-08. Body:
   ```java
   SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
   configurer.configure(factory, connectionFactory);     // Spring Boot defaults + spring.rabbitmq.listener.simple.* properties
   factory.setAdviceChain(tracingAdvice);                // Phase 3's tracing
   factory.setDefaultRequeueRejected(false);             // APP-04 safety: drop instead of requeue (D-13)
   return factory;
   ```
5. **CRITICAL: bean method name MUST be `rabbitListenerContainerFactory`** (Pitfall #7 in RESEARCH.md). Spring Boot's auto-config uses `@ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")` — exact name match. Lowercase `r`. The bean method name = bean name when no `@Bean(name=...)` override is given.
6. **CRITICAL: order of `configure(...)` vs `setAdviceChain(...)`** (Pitfall #5 in RESEARCH.md). `configure(...)` MUST come FIRST so any future `spring.rabbitmq.listener.simple.retry.enabled=true` doesn't overwrite the advice chain. JavaDoc the order.
7. **JavaDoc on the factory bean** should explain (a) Configurer-aided + override pattern, (b) bean-name discipline (Pitfall #7), (c) `defaultRequeueRejected=false` rationale (D-13: NACK without requeue → broker drops → no DLX per PROJECT.md), (d) `setAdviceChain` wiring → `inv.getArguments()[1]` is the Message (Pitfall #6 + RESEARCH.md FLAG #3).

**Data flow:** Spring Boot's `RabbitAnnotationDrivenConfiguration` no longer creates the listener factory (backed off by name match). When the AMQP broker delivers a message to `orders.created` queue, the user-defined factory's container thread receives it → ProxyFactory invokes the advice chain → `TracingMessageListenerAdvice.invoke(...)` extracts traceparent + creates CONSUMER span → `inv.proceed()` invokes `MessagingMessageListenerAdapter.onMessage(...)` → user's `OrderListener.onOrder(Map)` body runs inside the CONSUMER span's scope.

---

### `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` (inline-edit, CRUD)

**Analog:** itself (Phase 2 baseline). Phase 3 INSERTS into the existing D-01 try-block + uses the existing D-03 catch shape.

**Why this analog:** Phase 2 already shipped the D-01 inline-span template AND the D-03 catch block — explicitly forward-compatible for Phase 3's throw site. Phase 3's edit is purely additive inside lines 40-42 (the placeholder body).

**Concrete excerpt — current Phase 2 state (lines 24-54, the entire class body):**
```java
@Service
public class ProcessingService {
    private final Tracer tracer;

    public ProcessingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void process(Map<String, Object> order) {
        Span span = tracer.spanBuilder("ProcessingService.process")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Phase 1: simulated domain work, in-memory only.
            // Phase 3 wires up the deterministic 10% failure path (APP-04).
        } catch (RuntimeException e) {
            // D-03: catch block present in Phase 2 even though no fail path
            // exists yet (APP-04 lands in Phase 3 alongside TRACE-09's
            // recordException + setStatus(ERROR)).
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**Transformation notes:**
1. **ADD `import java.util.concurrent.atomic.AtomicInteger;`** at the top.
2. **ADD instance field `private final AtomicInteger counter = new AtomicInteger();`** below the `tracer` field (line ~26, between Tracer and constructor). Spring `@Service` produces singleton scope by default — counter persists across messages within one JVM run (D-11). NO Spring annotation on the field.
3. **REPLACE the placeholder comments at lines 41-42** (`// Phase 1: simulated domain work...` / `// Phase 3 wires up...`) with the throw site:
   ```java
   int n = counter.incrementAndGet();
   if (n % 10 == 0) {
       throw new ProcessingFailedException(
           "Deterministic failure on order #" + n + " (every 10th order)");
   }
   // Successful processing path — Phase 1 placeholder retained
   // (simulated domain work, in-memory).
   ```
   Verbatim message wording matches APP-04 in REQUIREMENTS.md.
4. **DO NOT touch the catch block (lines 43-49) or the finally (lines 50-52).** D-03's `catch (RuntimeException e) → recordException + setStatus(ERROR) + throw e` already does the right thing — `ProcessingFailedException extends RuntimeException`, so it's caught here, recorded on the INTERNAL span, and rethrown. The advice's catch then records the same exception on the CONSUMER span — both spans show ERROR status, both carry the exception event (matches ROADMAP SC #3).
5. **ADD import for the new exception:** `com.example.consumer.domain.ProcessingFailedException` is in the same package — NO import needed.
6. **Net diff:** ~5 added lines per CONTEXT.md `<specifics>` line 238. NO restructuring of the catch shape.

**Data flow:** `OrderListener.onOrder(...)` calls `processingService.process(message)` while inside the CONSUMER span scope. `ProcessingService.process(...)` opens its own INTERNAL span (parent = CONSUMER). `counter.incrementAndGet()` returns `n`. If `n % 10 == 0`, throws `ProcessingFailedException`. The throw is caught by the local D-03 catch → INTERNAL span gets recordException + ERROR + rethrow. Re-thrown back through `OrderListener.onOrder` (no catch) → re-caught by `TracingMessageListenerAdvice` → CONSUMER span gets recordException + ERROR + rethrow. Spring AMQP container catches → `defaultRequeueRejected=false` → NACK + drop.

---

### `otel-bootstrap/pom.xml` (pom-add-deps)

**Analog:** `producer-service/pom.xml` and `consumer-service/pom.xml` (existing `<dependencies>` block shape — but with DIFFERENT scopes for Phase 3's deps).

**Why this analog:** Same parent POM (`com.example:ose-otel-demo-parent:0.1.0-SNAPSHOT`), same BOM-managed convention (no `<version>` on BOM-managed deps), same indentation/comment style. The scope difference is the only divergence.

**Concrete excerpt — current state of `otel-bootstrap/pom.xml` (full file):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>ose-otel-demo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>otel-bootstrap</artifactId>
  <packaging>jar</packaging>

  <name>OSE OTel Demo (otel-bootstrap)</name>
  <description>Shared OTel helper module. Phase 1: empty placeholder. Phase 3 populates it with the AMQP propagation pair.</description>

  <!-- Phase 1: empty module. Phase 3 populates it with the AMQP propagation pair. -->
</project>
```

**Concrete excerpt — analog dependency block shape (from producer-service/pom.xml lines 56-68):**
```xml
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-api</artifactId>
</dependency>
```

**Transformation notes:**
1. **REPLACE the `<!-- Phase 1: empty module... -->` comment** with a `<dependencies>` block.
2. **ADD 3 dependencies** per RESEARCH.md §"New deps for `otel-bootstrap/pom.xml`":
   ```xml
   <dependencies>
     <!-- BOM-managed (parent pom.xml) — no <version>. Compile scope:
          users of otel-bootstrap need the OTel API on their compile classpath. -->
     <dependency>
       <groupId>io.opentelemetry</groupId>
       <artifactId>opentelemetry-api</artifactId>
     </dependency>

     <!-- BOM-managed (Spring Boot 3.4.13 BOM via parent). Provided scope:
          consuming services bring spring-rabbit transitively via
          spring-boot-starter-amqp; otel-bootstrap only references the API
          types at compile time. -->
     <dependency>
       <groupId>org.springframework.amqp</groupId>
       <artifactId>spring-rabbit</artifactId>
       <scope>provided</scope>
     </dependency>

     <!-- BOM-managed (Spring Boot 3.4.13 BOM via parent). Provided scope:
          spring-aop transitively brings org.aopalliance:aopalliance for
          MethodInterceptor; consuming services bring spring-aop transitively
          via spring-boot-starter-amqp → spring-tx → spring-aop. -->
     <dependency>
       <groupId>org.springframework</groupId>
       <artifactId>spring-aop</artifactId>
       <scope>provided</scope>
     </dependency>
   </dependencies>
   ```
3. **No `<version>` on any of the 3 deps** — all are BOM-managed (`opentelemetry-bom:1.61.0` for opentelemetry-api; `spring-boot-dependencies:3.4.13` for spring-rabbit and spring-aop). Convention matches Phase 1 / Phase 2 BOM order discipline.
4. **DO NOT add `opentelemetry-semconv-incubating`** here even though `TracingMessagePostProcessor` references `MessagingIncubatingAttributes` constants. Semconv constants are compile-time literals — they get inlined into the `.class` bytecode at compile, so `otel-bootstrap` only needs the artifact at compile time. Either add it as `<scope>provided</scope>` (parallel to spring-rabbit) OR rely on the consuming services to bring it. RESEARCH.md doesn't list it in the Phase 3 NEW deps table — implying constants-inlining is sufficient. **Planner decision point**: if `mvn package` on `otel-bootstrap` fails to compile due to missing semconv classes, add `<dependency><groupId>io.opentelemetry.semconv</groupId><artifactId>opentelemetry-semconv-incubating</artifactId><version>1.40.0-alpha</version><scope>provided</scope></dependency>`.
5. **UPDATE `<description>` and the inline `<!-- ... -->` comment** to reflect the populated module ("Shared OTel helper module — AMQP propagation pair (TracingMessagePostProcessor + TracingMessageListenerAdvice + MessagePropertiesSetter/Getter).").

**Data flow:** Build-time only. Maven resolves the 3 deps from the BOM-managed versions; the 4 new Java classes compile against them; the produced JAR (`otel-bootstrap-0.1.0-SNAPSHOT.jar`) ships with no embedded version metadata for those 3 deps (provided/compile scope semantics).

---

### `producer-service/pom.xml` (pom-add-deps)

**Analog:** itself (existing `<dependency>` block shape, lines 36-105).

**Why this analog:** Self-extension. The new dependency on `com.example:otel-bootstrap` follows the exact same `<dependency>` shape as the 8 existing ones.

**Concrete excerpt — analog dependency shape (lines 36-39, the simplest of the existing deps):**
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**Transformation notes:**
1. **ADD ONE dependency** to the existing `<dependencies>` block. Recommended location: top of the block (BEFORE the `spring-boot-starter-web` dep at line 36) so the intra-reactor dep is visually distinct from external deps.
2. **Coordinate:**
   ```xml
   <dependency>
     <groupId>com.example</groupId>
     <artifactId>otel-bootstrap</artifactId>
     <version>${project.version}</version>
   </dependency>
   ```
3. **`${project.version}`** resolves to `0.1.0-SNAPSHOT` via parent POM inheritance. Follows the multi-module reactor convention.
4. **No `<scope>`** — defaults to `compile`, which is correct (the propagation classes are referenced from `RabbitConfig.java` at compile time).
5. **DO NOT modify any existing dependency.**
6. **Net diff:** +5 lines (4 lines for the dependency + 1 blank line for visual separation).

**Data flow:** Build-time only. `mvn install` on `otel-bootstrap` (built first per reactor order) places the JAR in the local `~/.m2`; `mvn install` on `producer-service` resolves the dep. At runtime, the propagation classes are on the producer's classpath via the JAR.

---

### `consumer-service/pom.xml` (pom-add-deps)

**Analog:** itself (existing `<dependency>` block shape, lines 36-105) — symmetric to producer-service/pom.xml.

**Why this analog:** Identical edit to producer's pom.xml — same coordinate, same location convention.

**Concrete excerpt — same as producer-service/pom.xml.**

**Transformation notes:**
1. **IDENTICAL EDIT to producer-service/pom.xml** above — add the `com.example:otel-bootstrap:${project.version}` dep at the top of the `<dependencies>` block.
2. The two pom.xml edits intentionally mirror each other (consistent with DOC-05's per-service-duplication ethos for OTel deps).

**Data flow:** Same as producer's pom — build-time resolution; runtime classpath addition.

---

### `README.md` (documentation)

**Analog:** `README.md` lines 90-92 — the existing "Why is OtelSdkConfiguration.java duplicated?" callout (DOC-05 callout from Phase 2).

**Why this analog:** Phase 3's PROP-04 callout is the deliberate symmetric counterpart — DOC-05 explains why SDK config is duplicated; PROP-04 explains why the propagation pair is shared. The two callouts together teach the asymmetry boundary (per CONTEXT.md `<specifics>`: "per-service code = duplicated; cross-service code = shared"). They should sit side-by-side in the README at parallel structural positions.

**Concrete excerpt — existing DOC-05 callout (lines 90-92), the structural template for PROP-04:**
```markdown
## Why is OtelSdkConfiguration.java duplicated?

The two SDK config files are duplicated per service on purpose (DOC-05). Refactoring them into a shared `@AutoConfiguration` bean in the `otel-bootstrap` module would hide one of the two readings the workshop is built around — the whole point of Phase 2 is that an attendee reads `OpenTelemetrySdk.builder()`, `Resource.getDefault().merge(...)`, `BatchSpanProcessor.builder(...)`, `Sampler.parentBased(Sampler.alwaysOn())`, `OtlpGrpcSpanExporter.builder().setEndpoint(...)`, and `ContextPropagators.create(...)` _twice_, in two slightly different files, and develops a feel for which lines are workshop-pedagogy boilerplate and which lines are service-identity. The two files differ in only five small ways (package, JavaDoc cross-reference, the service.name string, the tracer scope name, plus the producer-only `HttpServerSpanFilter` bean) — the diff is small enough to read in one viewing. The propagation pair Phase 3 introduces, by contrast, IS shared in `otel-bootstrap` because the symmetry of one inject method matched by one extract method IS that lesson. Different design forces drive different choices; the workshop teaches both.
```

**Transformation notes:**
1. **ADD a "Step 3" subsection** under "Workshop checkpoints" (currently line 71-72: `step-03-context-propagation` — (Phase 3) THE headline lesson...). Replace the parenthetical placeholder with a small body — see ROADMAP SC #4 / RESEARCH.md `### Claude's Discretion` for scope.
2. **ADD a "Why is the propagation pair shared?" callout** — parallel to the existing "Why is OtelSdkConfiguration.java duplicated?" callout, placed AFTER it in the document (so the two callouts read as a pair). Body should:
   - Reference DOC-05 (the duplication rationale) as the foil.
   - State that `otel-bootstrap/.../amqp/` exports ONE class file (read once) for each side of the propagation: `TracingMessagePostProcessor` (inject) + `TracingMessageListenerAdvice` (extract).
   - State the symmetry: `inject(Context.current(), props, SETTER)` ↔ `extract(Context.current(), props, GETTER)` — same propagator, same Context API, same MessageProperties carrier — IS the lesson.
   - Note the per-service `@Bean` wiring is still explicit (no auto-config) — attendees see TWO `@Bean` declarations (one per service's RabbitConfig) but ONE class file.
   - Mention the CRITICAL line: `.setParent(extracted)` in `TracingMessageListenerAdvice` — the SINGLE LINE that joins the two traces.
   - Optionally note the destination-name correction (queue → exchange) as a side-quest semconv lesson.
3. **ADD a "What's NOT here yet" deletion** — line 99 says "No `traceparent` header injection on AMQP (Phase 3)". After Phase 3 ships, REMOVE this line OR reword to "Phase 3 added `traceparent` header injection — see the propagation-pair callout above."
4. **Length target:** ~25 lines for the new callout, matching the existing DOC-05 callout's size. Per RESEARCH.md `### Claude's Discretion`.
5. **DO NOT pre-write Phase 7's full walkthrough body** — keep this delta workshop-checkpoint-sized, not workshop-tutorial-sized. CONTEXT.md `<deferred>` confirms Phase 7 owns the walkthrough.

**Data flow:** Documentation only. The README delta lands as part of the same commit as the source edits, so `step-03-context-propagation` tag captures both code + docs in one immutable artifact.

---

### `step-03-context-propagation` (git-tag)

**Analog:** Phase 2's `step-02-traces` annotated git tag (WORK-01 convention).

**Why this analog:** Same gate convention. Phase 2 shipped 2026-05-01 with this tag; Phase 3 follows the same user-approved-then-tag flow.

**Concrete excerpt:** N/A (git tag is metadata, not source). Inherit the convention:
- Annotated tag (`git tag -a step-03-context-propagation -m "..."`), NOT lightweight.
- Tagged on `main` AFTER all Phase 3 commits land AND user explicitly approves the WORK-01 gate.
- Tag message: short workshop-checkpoint marker referencing the headline lesson (e.g., "Phase 3: AMQP context propagation — single distributed trace via TracingMessagePostProcessor (inject) + TracingMessageListenerAdvice (extract)").

**Transformation notes:**
1. **Gate is human-approved** per WORK-01 — planner schedules this as the LAST step of the LAST plan in the phase (likely `03-05-readme-and-exit-gate`).
2. **Verify with `git for-each-ref refs/tags/step-03-context-propagation`** — should report `tag` (annotated), not `commit` (lightweight). Per ROADMAP SC #5 verification.
3. **Verify diff size with `git diff --stat step-02-traces..step-03-context-propagation`** — should show ~50 added + ~60 deleted + 5 new files (CONTEXT.md `<specifics>` line 243). Reviewable in one viewing.

**Data flow:** N/A (git metadata).

---

## Shared Patterns

### Span lifecycle template (D-01 from Phase 2 — preserved across all new spans)
**Source:** `producer-service/.../messaging/OrderPublisher.java` lines 51-83 + `consumer-service/.../messaging/OrderListener.java` lines 55-79 + `consumer-service/.../domain/ProcessingService.java` lines 37-52
**Apply to:** `TracingMessagePostProcessor.postProcessMessage(...)` and `TracingMessageListenerAdvice.invoke(...)`
```java
Span span = tracer.spanBuilder(name)
    .setSpanKind(SpanKind.X)
    .setAttribute(/* messaging.* attrs */)
    .startSpan();
try (Scope scope = span.makeCurrent()) {
    // body
} catch (RuntimeException e) {              // (or Throwable on the advice)
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    span.end();
}
```
The shape is identical Phase 2 → Phase 3; the only structural delta is WHERE the code lives (inline-in-business-code → inline-in-shared-library).

### Per-service Tracer scope identity (D-03)
**Source:** `producer-service/.../config/OtelSdkConfiguration.java` lines 193-196 (`getTracer("com.example.producer")`); equivalent in consumer with `"com.example.consumer"`
**Apply to:** `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` constructors (both take `Tracer tracer` as the per-service Tracer; spans they create still appear under each service's instrumentation scope in Tempo)
```java
@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");   // or "com.example.consumer"
}
```
Because the Tracer is injected per service, spans created from inside `otel-bootstrap` code STILL appear under `com.example.producer` / `com.example.consumer` in Tempo — NOT under a new `com.example.otel.amqp` scope. This is the structural-not-semantic property that makes the shared module readable.

### Propagator wiring reuse (D-04 / D-16 from Phase 2)
**Source:** `producer-service/.../config/OtelSdkConfiguration.java` lines 172-180 (`ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))` then `.setPropagators(propagators)` on the SDK builder)
**Apply to:** Both `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` — they read the propagator via `openTelemetry.getPropagators().getTextMapPropagator()` rather than constructing a fresh `W3CTraceContextPropagator.getInstance()`.
```java
// inside the post-processor / advice
TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
propagator.inject(Context.current(), props, SETTER);  // producer side
Context extracted = propagator.extract(Context.current(), props, GETTER);  // consumer side
```
One configuration point: any future propagator addition (e.g., custom baggage, B3) flows transparently.

### Explicit `@Bean` wiring with no auto-config (D-12 from Phase 2)
**Source:** `producer-service/.../config/RabbitConfig.java` (4 explicit `@Bean` methods); `consumer-service/.../config/RabbitConfig.java` (2 explicit `@Bean` methods); both `OtelSdkConfiguration.java` files (3 explicit `@Bean` methods each)
**Apply to:** All Phase 3 wiring — `@Bean TracingMessagePostProcessor`, explicit `@Bean RabbitTemplate`, `@Bean TracingMessageListenerAdvice`, Configurer-aided `@Bean SimpleRabbitListenerContainerFactory`.
```java
@Bean
TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry o, Tracer tracer) {
    return new TracingMessagePostProcessor(o, tracer);
}
```
NO `@AutoConfiguration`, NO `META-INF/spring/.../AutoConfiguration.imports`, NO `@ConditionalOnX` annotations. The four propagation classes are pure Java with no Spring annotations on themselves (per D-01).

### String header values, never byte[] (PITFALLS.md #2)
**Source:** PITFALLS.md #2 + RESEARCH.md §Pitfall 2
**Apply to:** `MessagePropertiesSetter.set(...)` (writes String); `MessagePropertiesGetter.get(...)` (defensively normalizes via `.toString()`)
- Producer side: write String only (`carrier.setHeader(key, stringValue)`).
- Consumer side: `.toString()` any header value before returning (idempotent for String, well-defined for LongString, decodes for byte[]).
- Optional unit test: `setter.set(props, "k", "v"); assertEquals("v", getter.get(props, "k"));` — round-trip proof at the lowest level (RESEARCH.md `### Claude's Discretion`).

### POM BOM order (Phase 1 baseline)
**Source:** `producer-service/pom.xml` + `consumer-service/pom.xml` + parent `pom.xml`
**Apply to:** `otel-bootstrap/pom.xml` (new deps), `producer-service/pom.xml` (new dep on otel-bootstrap), `consumer-service/pom.xml` (new dep on otel-bootstrap)
- BOM-managed deps: NO `<version>` tag (`opentelemetry-bom:1.61.0` and `spring-boot-dependencies:3.4.13` manage versions).
- Non-BOM-managed deps: explicit `<version>` (e.g., the two semconv coordinates pin `1.40.0` / `1.40.0-alpha`).
- Intra-reactor deps: `${project.version}` for `com.example:otel-bootstrap`.
- Maven enforcer's `<dependencyConvergence/>` (parent pom.xml lines 113-145) catches version drift at validate phase.

---

## Cross-cutting truths

These invariants span multiple plans and must be respected throughout Phase 3:

1. **The propagation pair classes (`TracingMessagePostProcessor`, `TracingMessageListenerAdvice`) live in `otel-bootstrap` but their PER-SERVICE Tracer scope identity is preserved.** Spans they create still appear under `com.example.producer` / `com.example.consumer` (not `com.example.otel.amqp`) because the Tracer bean is injected per-service from each `OtelSdkConfiguration.@Bean Tracer tracer(...)` (D-03). This is the load-bearing distinction between WHERE code lives (shared module) and WHO claims the spans (per-service scope). Verifiable in Tempo: filter by service.name → all three span kinds (PRODUCER, CONSUMER, INTERNAL) appear under the same instrumentation scope.

2. **All header writes go through `MessagePropertiesSetter` writing String, never byte[]; all header reads go through `MessagePropertiesGetter` calling `.toString()`.** PITFALLS.md #2 is the highest-impact pitfall in this phase — silent byte[]-vs-String mismatch causes extract to return `Context.root()`, which silently fragments the trace AGAIN, recreating the Phase 2 broken state with no error message. Both classes' JavaDoc should warn the next maintainer; an optional unit test (Claude's Discretion) gives the round-trip an automated regression net.

3. **All Spring wiring is explicit `@Bean` — no auto-config, no `@AutoConfiguration`, no `META-INF/spring/.../AutoConfiguration.imports`** (D-12 from Phase 2, preserved). The propagation classes themselves carry NO Spring annotations; each service's `RabbitConfig.java` declares the `@Bean` methods that `new` them up. Every wiring decision in Phase 3 is auditable in two `RabbitConfig.java` files. This includes the BEAN NAME discipline: `rabbitListenerContainerFactory` (lowercase r, exact match per Pitfall #7) AND the order discipline: `configurer.configure(...)` BEFORE `setAdviceChain(...)` (Pitfall #5).

4. **The git diff `step-02-traces..step-03-context-propagation` is itself a deliverable.** Per ROADMAP SC #5 / CONTEXT.md `<specifics>`: ~50 added + ~60 deleted + 5 new files + README delta. Two-thirds of the value is in the DELETIONS (`OrderPublisher.publish` body and `OrderListener.onOrder` body). Every plan's actions should preserve this property — no opportunistic refactoring, no scope creep, no "while we're in here" edits to `OtelSdkConfiguration.java` (D-04 explicitly forbids touching it). Plans should size each commit to keep the cumulative diff reviewable in one viewing.

5. **The semconv 1.40.0 destination-name correction is a side-quest, not the headline.** Phase 2 used QUEUE for `messaging.destination.name`; Phase 3 corrects to EXCHANGE per OTel messaging semconv RabbitMQ profile (D-07). This is visible in Tempo across the step-02→step-03 tags as a behavioral delta. README PROP-04 callout MAY mention it as a teaching aside, but the headline remains: `consumer.parentSpanId == producer.spanId` via the propagation pair. Don't let the destination-name change distract from the trace-joining lesson.

---

## Metadata

**Analog search scope:**
- `/home/coto/dev/demo/ose-otel-demo/producer-service/src/main/java/` (full)
- `/home/coto/dev/demo/ose-otel-demo/consumer-service/src/main/java/` (full)
- `/home/coto/dev/demo/ose-otel-demo/otel-bootstrap/src/main/java/` (placeholder only — `package-info.java`)
- `/home/coto/dev/demo/ose-otel-demo/{producer-service,consumer-service,otel-bootstrap}/pom.xml`
- `/home/coto/dev/demo/ose-otel-demo/README.md`
- `/home/coto/dev/demo/ose-otel-demo/.planning/phases/03-amqp-context-propagation/{03-CONTEXT.md, 03-RESEARCH.md}`

**Files scanned:** 11 source files + 4 pom.xml + 1 README.md + 2 phase docs = 18 files.

**Pattern extraction date:** 2026-05-01

**Confidence:** HIGH. Phase 2 ships the analog code intact; Phase 3 transformations are mechanical (lift inline → shared, swap Context.root → extracted, swap queue → exchange, add throw site, add 4 deps, add 2 callouts). RESEARCH.md resolved all three Spring AMQP API-surface flags against the v3.2.8 source.
