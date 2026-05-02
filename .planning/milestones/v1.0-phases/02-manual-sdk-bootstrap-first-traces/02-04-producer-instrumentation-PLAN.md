---
id: 02-04-producer-instrumentation
phase: 02-manual-sdk-bootstrap-first-traces
plan: 04
type: execute
wave: 3
depends_on: [02-02-producer-sdk-config]
requirements: [TRACE-06, TRACE-07]
files_modified:
  - producer-service/src/main/java/com/example/producer/domain/OrderService.java
  - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
autonomous: true
must_haves:
  truths:
    - "OrderService.place(...) is wrapped in an INTERNAL span named \"OrderService.place\" (TRACE-06) using the EXACT D-01 pure-inline template (8-12 lines: spanBuilder/setSpanKind/startSpan + try-with-Scope + try/catch/finally with recordException + setStatus(ERROR) + throw + span.end())"
    - "OrderPublisher.publish(...) is wrapped in a PRODUCER span named \"orders.created publish\" (D-04: span-name uses queue name; routing key set as ATTRIBUTE per D-11) using the EXACT D-01 pure-inline template"
    - "PRODUCER span carries 4 messaging semconv attributes: MESSAGING_SYSTEM=RABBITMQ, MESSAGING_DESTINATION_NAME=\"orders.created\" (queue name), MESSAGING_OPERATION_TYPE=SEND (using the new key+value per RESEARCH FLAG #1; NOT the deprecated MESSAGING_OPERATION/\"publish\"), MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY=\"order.created\" (D-11)"
    - "Both classes constructor-inject Tracer (D-02) — Spring's DI resolves the @Bean from OtelSdkConfiguration; no GlobalOpenTelemetry, no setter injection, no @Autowired field"
    - "OrderController is UNCHANGED (no Tracer parameter added) — controller is a thin pass-through to OrderService.place; PATTERNS.md line 91 explicitly says no INTERNAL span here"
    - "All catch blocks include the FULL D-03 forward-compatible shape: span.recordException(e); span.setStatus(StatusCode.ERROR); throw e; — even though Phase 2 has no failure path yet (APP-04 lands in Phase 3)"
    - "PRODUCER span includes a one-line teaching comment about the semconv 1.40.0 rename: \"// semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create\""
    - "mvn -pl producer-service compile exits 0; mvn -pl producer-service -DskipTests package exits 0"
    - "End-to-end: POST /orders produces a producer trace in Tempo with 3 nested spans — SERVER (POST /orders) → INTERNAL (OrderService.place) → PRODUCER (orders.created publish) — all sharing the same trace_id and traceId (parent-child chain)"
  artifacts:
    - path: "producer-service/src/main/java/com/example/producer/domain/OrderService.java"
      provides: "Tracer-injected OrderService with INTERNAL span around place(...) (TRACE-06)"
      contains: "tracer.spanBuilder(\"OrderService.place\")"
    - path: "producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java"
      provides: "Tracer-injected OrderPublisher with PRODUCER span around publish(...) + 4 messaging semconv attrs (TRACE-07 + D-11)"
      contains: "SpanKind.PRODUCER"
  key_links:
    - from: "producer-service/.../domain/OrderService.java"
      to: "OtelSdkConfiguration.@Bean Tracer"
      via: "constructor injection — Spring resolves the Tracer bean and passes it"
      pattern: "private final Tracer tracer"
    - from: "OrderService.place INTERNAL span"
      to: "OrderPublisher.publish PRODUCER span"
      via: "Span.makeCurrent() Scope keeps OrderService.place active while publisher.publish() runs; PRODUCER span builder picks up parent via Context.current()"
      pattern: "try (Scope scope = span.makeCurrent())"
    - from: "PRODUCER span MESSAGING_DESTINATION_NAME"
      to: "RabbitConfig.QUEUE constant"
      via: "Both reference \"orders.created\" — span attribute reads RabbitConfig.QUEUE for symmetry with the actual destination name"
      pattern: "RabbitConfig.QUEUE"
---

<objective>
Wrap producer-service's two business-logic touch points in their respective OTel spans using the D-01 pure-inline template (no helper, no AOP — boilerplate IS the lesson). `OrderService.place(...)` gets an INTERNAL span (TRACE-06); `OrderPublisher.publish(...)` gets a PRODUCER span (TRACE-07) with 4 messaging semantic-convention attributes (`messaging.system=rabbitmq`, `messaging.destination.name=orders.created`, `messaging.operation.type=send`, `messaging.rabbitmq.destination.routing_key=order.created`). Combined with the SERVER span from the filter (already in place via Plan 02-02), one `POST /orders` produces a 3-span producer trace: SERVER → INTERNAL → PRODUCER, all parent-chained via Span.makeCurrent() Scopes.

The SERVER span and the PRODUCER span both use semconv 1.40.0 constants — but PRODUCER span uses INCUBATING constants (MessagingIncubatingAttributes) because messaging conventions are still evolving. RESEARCH FLAG #1 documents the spec rename `messaging.operation` → `messaging.operation.type` (deprecated → current) and value rename `"publish"` → `"send"`; the plan uses the NEW key + value enum.

Per the per-service-duplication ethos, OrderController is NOT modified — it's a thin pass-through with no business logic to wrap (PATTERNS.md line 91 explicitly).

Purpose: TRACE-06 (INTERNAL spans around domain methods) + TRACE-07 (PRODUCER span with messaging semconv attrs) for the producer half. Output: 2 modified Java files + producer end-to-end produces a 3-span trace per POST /orders.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-02-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="02-04-T1" type="auto">
  <name>Task 1: Modify OrderService.java — add Tracer constructor param + wrap place(...) body in INTERNAL span (D-01 inline template)</name>
  <files>producer-service/src/main/java/com/example/producer/domain/OrderService.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 36-53 — D-01 pure-inline template; D-02 constructor-inject Tracer)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 54-58 — D-03 catch block included in Phase 2 even though no fail path; lines 56-61 D-04 INTERNAL span name convention "ClassName.method")
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 93-110 — file modification spec for OrderService: add Tracer param; wrap place(...) body in INTERNAL span; both lines String orderId = ... and return orderId; live INSIDE the try block)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 244-260 — verbatim D-01 template the executor pastes)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 230-235 — Application Map row 2: INTERNAL span name = "OrderService.place"; no semconv attrs mandated)
    - producer-service/src/main/java/com/example/producer/domain/OrderService.java (current Phase 1 state — 22 lines, just wraps publisher.publish)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (just written in Plan 02-02 — exposes @Bean Tracer for constructor injection)
  </read_first>
  <action>
    Replace the entire current `OrderService.java` content with the new version below. The body of `place(...)` is wrapped in an INTERNAL span using the D-01 pure-inline template — NOT a helper method, NOT a try-with-resources factory, NOT an AOP aspect. The boilerplate IS the lesson per CONTEXT.md `<specifics>` line 192.

    ```java
    package com.example.producer.domain;

    import java.util.Map;
    import java.util.UUID;

    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Scope;

    import org.springframework.stereotype.Service;

    import com.example.producer.messaging.OrderPublisher;

    /**
     * Domain layer — orchestrates the place-an-order flow.
     *
     * Phase 2 wraps {@link #place(Map)} in an INTERNAL span (TRACE-06) named
     * "OrderService.place" using the D-01 pure-inline template — boilerplate
     * is the lesson here; do NOT extract this into a helper.
     */
    @Service
    public class OrderService {
        private final OrderPublisher publisher;
        private final Tracer tracer;

        public OrderService(OrderPublisher publisher, Tracer tracer) {
            this.publisher = publisher;
            this.tracer = tracer;
        }

        public String place(Map<String, Object> payload) {
            // ---- D-01 inline span template (INTERNAL) ----
            //
            // Pure inline. No helper. No AOP. The full try/Scope/try/catch/finally
            // idiom is REPEATED at every span site (5 sites in Phase 2 across
            // both services) so workshop attendees read the SDK calls in business
            // code and understand exactly which lines do what.
            //
            // INTERNAL span name follows the D-04 convention: ClassName.method.
            // No semconv attributes mandated for INTERNAL spans (D-04) — those
            // belong on SERVER / CLIENT / PRODUCER / CONSUMER kinds where the
            // OTel spec defines stable attribute keys.
            Span span = tracer.spanBuilder("OrderService.place")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                String orderId = UUID.randomUUID().toString();
                publisher.publish(orderId, payload);
                return orderId;
            } catch (RuntimeException e) {
                // D-03: catch block present in Phase 2 even though no fail path
                // exists yet (APP-04's deterministic 10% failure lands in Phase 3).
                // Keeping the structural shape now means Phase 3 only adds the
                // failure path — no restructuring of these 8 lines.
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end();
            }
        }
    }
    ```

    Notes for the executor:
    - The `String orderId = ...`, `publisher.publish(...)`, AND `return orderId;` ALL live INSIDE the try block (per PATTERNS.md line 109). The return statement going inside the try is what threads the active Scope through the publisher.publish call (so the PRODUCER span in T2 picks up the right parent).
    - Constructor signature changes from `OrderService(OrderPublisher publisher)` to `OrderService(OrderPublisher publisher, Tracer tracer)` — Spring resolves both via constructor injection. NO `@Autowired` annotation needed (single-constructor classes get autowire-by-default in Spring 4.3+).
    - `Tracer` import is `io.opentelemetry.api.trace.Tracer`; `Span` is `io.opentelemetry.api.trace.Span`; `SpanKind` is `io.opentelemetry.api.trace.SpanKind`; `StatusCode` is `io.opentelemetry.api.trace.StatusCode`; `Scope` is `io.opentelemetry.context.Scope`.
    - 4-space Java indentation (match existing codebase).
    - Imports sorted: java.* first, io.opentelemetry.* second, org.springframework.* third, com.example.* last (matches Phase 1 style).
    - The catch block's parameter is `RuntimeException e` (NOT `Exception` — the publisher.publish signature throws no checked exceptions; Spring's RabbitTemplate wraps AMQP errors in unchecked AmqpException). Use `RuntimeException` for symmetry with the D-01 template at PATTERNS.md line 252.
  </action>
  <acceptance_criteria>
    - `test -f producer-service/src/main/java/com/example/producer/domain/OrderService.java` exits 0
    - Constructor injects both publisher and tracer: `grep -c 'public OrderService(OrderPublisher publisher, Tracer tracer)' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - Tracer is a private final field: `grep -c 'private final Tracer tracer;' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - INTERNAL span name and kind: `grep -c 'tracer.spanBuilder("OrderService.place")' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1; `grep -c 'setSpanKind(SpanKind.INTERNAL)' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - try-with-resources Scope present: `grep -c 'try (Scope scope = span.makeCurrent())' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - D-03 catch shape present (recordException + setStatus + throw + finally end): `grep -c 'span.recordException(e)' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1; `grep -c 'span.setStatus(StatusCode.ERROR)' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1; `grep -c 'throw e;' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1; `grep -c 'span.end()' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - All three original lines INSIDE the try block (UUID.randomUUID + publisher.publish + return orderId): `awk '/try \(Scope scope = span.makeCurrent\(\)\)/,/^ +} catch/' producer-service/src/main/java/com/example/producer/domain/OrderService.java | grep -cE 'UUID.randomUUID|publisher.publish|return orderId' | awk '{exit ($1==3)?0:1}'` exits 0
    - Catch parameter is RuntimeException: `grep -c 'catch (RuntimeException e)' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - Imports correct: `for c in 'io.opentelemetry.api.trace.Span' 'io.opentelemetry.api.trace.SpanKind' 'io.opentelemetry.api.trace.StatusCode' 'io.opentelemetry.api.trace.Tracer' 'io.opentelemetry.context.Scope'; do grep -q "import $c;" producer-service/src/main/java/com/example/producer/domain/OrderService.java || exit 1; done` exits 0
    - producer-service compiles: `mvn -pl producer-service -q compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl producer-service -q compile &amp;&amp; grep -q 'tracer.spanBuilder("OrderService.place")' producer-service/src/main/java/com/example/producer/domain/OrderService.java &amp;&amp; grep -q 'setSpanKind(SpanKind.INTERNAL)' producer-service/src/main/java/com/example/producer/domain/OrderService.java &amp;&amp; grep -q 'try (Scope scope = span.makeCurrent())' producer-service/src/main/java/com/example/producer/domain/OrderService.java &amp;&amp; grep -q 'span.recordException(e)' producer-service/src/main/java/com/example/producer/domain/OrderService.java</automated>
  </verify>
  <done>OrderService.java now constructor-injects Tracer alongside OrderPublisher; place(...) body wrapped in INTERNAL span "OrderService.place" using the EXACT D-01 inline template (8-12 lines); UUID.randomUUID + publisher.publish + return orderId all inside the try block; D-03 catch shape present (recordException + setStatus(ERROR) + throw + finally end); compiles green.</done>
</task>

<task id="02-04-T2" type="auto">
  <name>Task 2: Modify OrderPublisher.java — add Tracer constructor param + wrap publish(...) body in PRODUCER span with 4 messaging semconv attrs (D-11 + RESEARCH FLAG #1)</name>
  <files>producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 71-89 — D-08 PRODUCER span lives inline in OrderPublisher.publish; D-11 PRODUCER span sets 4 attrs incl. messaging.rabbitmq.destination.routing_key)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 207-215 — RESEARCH FLAG #1 — HIGH severity: messaging.operation is DEPRECATED in 1.40.0; use MESSAGING_OPERATION_TYPE constant + MessagingOperationTypeIncubatingValues.SEND value enum)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 156-167 — incubating constant catalog: MessagingIncubatingAttributes.MESSAGING_SYSTEM / MESSAGING_DESTINATION_NAME / MESSAGING_OPERATION_TYPE / MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY plus the value enums MessagingSystemIncubatingValues.RABBITMQ and MessagingOperationTypeIncubatingValues.SEND)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 111-127 — file modification spec; PRODUCER span name "orders.created publish" per D-04; routing_key set as ATTRIBUTE per D-11; HashMap copy + put + convertAndSend ALL inside try block)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 230-237 — Application Map row 3 with the exact 4-attribute set)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 237-241 — RESEARCH FLAG #1 reminder + the one-line teaching comment text the executor pastes verbatim)
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (current Phase 1 state — 24 lines; HashMap copy + put + convertAndSend)
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (provides EXCHANGE / QUEUE / ROUTING_KEY constants; QUEUE="orders.created" used as MESSAGING_DESTINATION_NAME, ROUTING_KEY="order.created" used as MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY)
  </read_first>
  <action>
    Replace the entire current `OrderPublisher.java` content with the new version below. The body of `publish(...)` is wrapped in a PRODUCER span using the D-01 pure-inline template, with 4 messaging semconv attributes set BEFORE the startSpan() call (D-11 + RESEARCH FLAG #1 — uses the new MESSAGING_OPERATION_TYPE constant, NOT the deprecated MESSAGING_OPERATION; uses the value enum MessagingOperationTypeIncubatingValues.SEND, NOT the literal "publish").

    The span name is `"orders.created publish"` per D-04 — span-name uses the QUEUE name (`orders.created`); the routing key (`order.created`) is set as a separate attribute per D-11.

    ```java
    package com.example.producer.messaging;

    import java.util.HashMap;
    import java.util.Map;

    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Scope;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;

    import org.springframework.amqp.rabbit.core.RabbitTemplate;
    import org.springframework.stereotype.Component;

    import com.example.producer.config.RabbitConfig;

    /**
     * Publishes OrderCreated to the orders direct exchange via Spring AMQP.
     *
     * Phase 2 wraps {@link #publish(String, Map)} in a PRODUCER span (TRACE-07)
     * named "orders.created publish" with the four messaging semconv attributes
     * (D-11). Phase 3 will REPLACE this inline span with the
     * TracingMessagePostProcessor from otel-bootstrap, which takes over both
     * the inject AND the PRODUCER span lifecycle — see CONTEXT.md D-09.
     */
    @Component
    public class OrderPublisher {
        private final RabbitTemplate rabbitTemplate;
        private final Tracer tracer;

        public OrderPublisher(RabbitTemplate rabbitTemplate, Tracer tracer) {
            this.rabbitTemplate = rabbitTemplate;
            this.tracer = tracer;
        }

        public void publish(String orderId, Map<String, Object> payload) {
            // ---- D-01 inline span template (PRODUCER) ----
            //
            // Span name = "<destination> <operation>" per OTel messaging semconv:
            // "orders.created publish" — the QUEUE name (D-04 + D-11 note: span-name
            // uses queue; routing-key is a separate attribute below).
            //
            // semconv 1.40.0 renamed messaging.operation → messaging.operation.type;
            // values: send|receive|process|create. We use MESSAGING_OPERATION_TYPE
            // (the current constant) and the SEND value enum. The deprecated
            // MESSAGING_OPERATION key + the literal "publish" value would
            // technically still work but flag deprecation warnings.
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
                // APP-02: publish via direct exchange + routing key.
                //
                // Phase 2 does NOT inject the traceparent header here — that's
                // Phase 3's headline lesson (PROP-01), which adds the
                // TracingMessagePostProcessor that runs ON RabbitTemplate's
                // setBeforePublishPostProcessors hook to inject W3C trace
                // context into MessageProperties.
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
            } catch (RuntimeException e) {
                // D-03: catch block present in Phase 2 even though no fail path yet.
                // Spring's RabbitTemplate throws AmqpException (a RuntimeException)
                // on broker connectivity issues — the recordException pattern
                // captures those for Tempo's error-status display.
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end();
            }
        }
    }
    ```

    Notes for the executor:
    - All three lines (`Map<String, Object> message = new HashMap<>(payload);`, `message.put(...)`, `rabbitTemplate.convertAndSend(...)`) live INSIDE the try block per PATTERNS.md line 127.
    - Constructor signature changes from `OrderPublisher(RabbitTemplate rabbitTemplate)` to `OrderPublisher(RabbitTemplate rabbitTemplate, Tracer tracer)` — single constructor → autowire by default.
    - The MessagingIncubatingAttributes inner enum classes are statically imported via `MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues` and `MessagingIncubatingAttributes.MessagingSystemIncubatingValues` — RESEARCH §B confirms these are nested classes inside MessagingIncubatingAttributes. (Some Maven artifact arrangements expose them as top-level; the safe import shape is the qualified one above. If `mvn compile` errors with "cannot find symbol MessagingSystemIncubatingValues", the executor can switch to top-level imports — the constant locations are guaranteed but the package nesting can vary across patch versions.)
    - The `// APP-02:` comment from Phase 1 is preserved (slightly expanded with the Phase-3-hint about the missing traceparent injection).
    - The new comment about RESEARCH FLAG #1 (the messaging.operation rename) is required — workshop attendees encountering deprecated semconv constants in their own codebases benefit from seeing the explicit migration note.
    - 4-space Java indentation; imports sorted: java.* / io.opentelemetry.* / org.springframework.* / com.example.* last.
    - Catch parameter is `RuntimeException` (matches D-01 template + RabbitTemplate's AmqpException is unchecked).
  </action>
  <acceptance_criteria>
    - `test -f producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - Constructor injects both rabbitTemplate and tracer: `grep -c 'public OrderPublisher(RabbitTemplate rabbitTemplate, Tracer tracer)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - Tracer is a private final field: `grep -c 'private final Tracer tracer;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - PRODUCER span name and kind: `grep -c 'tracer.spanBuilder("orders.created publish")' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1; `grep -c 'setSpanKind(SpanKind.PRODUCER)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - All four messaging semconv attrs set: `for k in MESSAGING_SYSTEM MESSAGING_DESTINATION_NAME MESSAGING_OPERATION_TYPE MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY; do grep -q "$k" producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java || exit 1; done` exits 0
    - Uses NEW key (NOT deprecated bare MESSAGING_OPERATION): `! grep -E 'MESSAGING_OPERATION[^_]' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0 (the only MESSAGING_OPERATION* references must be MESSAGING_OPERATION_TYPE)
    - Uses value enums (NOT string literals): `grep -c 'MessagingSystemIncubatingValues.RABBITMQ' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1; `grep -c 'MessagingOperationTypeIncubatingValues.SEND' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - Forbidden literal "publish" value NOT used: `! grep -E '"publish"' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0 (the string MUST come from the value enum, not a literal)
    - DESTINATION_NAME = RabbitConfig.QUEUE: `grep -B1 -A1 'MESSAGING_DESTINATION_NAME' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java | grep -c 'RabbitConfig.QUEUE'` returns 1
    - ROUTING_KEY = RabbitConfig.ROUTING_KEY: `grep -B1 -A1 'MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java | grep -c 'RabbitConfig.ROUTING_KEY'` returns 1
    - try-with-resources Scope present: `grep -c 'try (Scope scope = span.makeCurrent())' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - All three publish lines inside try (HashMap copy + put + convertAndSend): `awk '/try \(Scope scope = span.makeCurrent\(\)\)/,/^ +} catch/' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java | grep -cE 'new HashMap|message.put|rabbitTemplate.convertAndSend' | awk '{exit ($1==3)?0:1}'` exits 0
    - D-03 catch shape: `grep -c 'span.recordException(e)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1; `grep -c 'span.setStatus(StatusCode.ERROR)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - Catch parameter is RuntimeException: `grep -c 'catch (RuntimeException e)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - RESEARCH FLAG #1 teaching comment present: `grep -c 'semconv 1.40.0 renamed messaging.operation' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - Phase 3 hand-off comment present (Phase 3 deletes this inline PRODUCER span per CONTEXT.md D-09): `grep -c 'Phase 3' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns >= 1
    - Imports correct: `for c in 'io.opentelemetry.api.trace.Span' 'io.opentelemetry.api.trace.SpanKind' 'io.opentelemetry.api.trace.StatusCode' 'io.opentelemetry.api.trace.Tracer' 'io.opentelemetry.context.Scope' 'io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes'; do grep -q "import $c" producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java || exit 1; done` exits 0
    - producer-service compiles: `mvn -pl producer-service -q compile` exits 0
    - producer-service packages cleanly: `mvn -pl producer-service -q -DskipTests package` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl producer-service -q -DskipTests package &amp;&amp; grep -q 'tracer.spanBuilder("orders.created publish")' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; grep -q 'setSpanKind(SpanKind.PRODUCER)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; grep -q 'MessagingOperationTypeIncubatingValues.SEND' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; grep -q 'MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; ! grep -E 'MESSAGING_OPERATION[^_]' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; ! grep -E '"publish"' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java</automated>
  </verify>
  <done>OrderPublisher.java now constructor-injects Tracer alongside RabbitTemplate; publish(...) body wrapped in PRODUCER span "orders.created publish" with 4 messaging semconv attrs (system=RABBITMQ value enum, destination_name=RabbitConfig.QUEUE, operation_type=SEND value enum per RESEARCH FLAG #1, rabbitmq_destination_routing_key=RabbitConfig.ROUTING_KEY); HashMap copy + put + convertAndSend all inside try; D-03 catch shape present; producer-service packages cleanly.</done>
</task>

<task id="02-04-T3" type="auto">
  <name>Task 3: End-to-end smoke — start producer + send POST /orders, verify 3-span producer trace (SERVER → INTERNAL → PRODUCER) appears in Tempo with correct messaging semconv attrs</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 184-194 — End-user-verifiable surface for Phase 2: producer trace = SERVER → INTERNAL → PRODUCER per success criterion #4)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (line 191 — workshop attendee experience: 5 spans per round-trip, 3 on producer side)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-02-SUMMARY.md (Plan 02-02's smoke test set the precedent for /tmp/producer-*.log naming and Tempo HTTP API queries)
    - mise.toml (lines 22, 97-100, 117-119 — env vars, dev:producer task, demo:order task)
  </read_first>
  <action>
    Verify the producer half of Phase 2 is end-to-end correct: one POST /orders produces a producer-side trace in Tempo containing 3 nested spans — SERVER (POST /orders) → INTERNAL (OrderService.place) → PRODUCER (orders.created publish) — all sharing one trace_id, with parent-child chain SERVER→INTERNAL→PRODUCER, and the PRODUCER span carrying the 4 messaging semconv attributes.

    Note: the consumer remains uninstrumented at this stage (Plan 02-05 wires its CONSUMER + INTERNAL spans). For this task, only the producer trace is asserted. The consumer side will continue to log "OrderCreated received: orderId=..." (Phase 1 contract preserved) but produces no spans yet.

    **Step 1 — Ensure infra is up:**
    `mise run infra:up` (idempotent).

    **Step 2 — Start producer in background:**
    ```
    nohup mise run dev:producer > /tmp/producer-02-04.log 2>&1 &
    PID=$!
    for i in $(seq 1 45); do
      if grep -q "Started ProducerApplication" /tmp/producer-02-04.log; then break; fi
      sleep 2
    done
    grep -q "Started ProducerApplication" /tmp/producer-02-04.log || { tail -50 /tmp/producer-02-04.log; kill $PID 2>/dev/null; exit 1; }
    # Verify no startup exceptions
    if grep -E 'NoSuchMethodError|NoClassDefFoundError|IllegalArgumentException.*opentelemetry|unknown_service:java' /tmp/producer-02-04.log; then
      kill $PID 2>/dev/null; exit 1
    fi
    ```

    **Step 3 — POST /orders and capture orderId:**
    ```
    curl -s -o /tmp/order-resp.json -w "%{http_code}\n" -X POST http://localhost:8080/orders \
      -H 'Content-Type: application/json' \
      -d '{"sku":"WIDGET-1","quantity":3}'
    # Expect: 202
    ORDER_ID=$(python3 -c "import json; print(json.load(open('/tmp/order-resp.json'))['orderId'])")
    test -n "$ORDER_ID"
    ```

    **Step 4 — Wait for BSP flush + query Tempo:**
    ```
    sleep 8  # 5s BSP schedule + margin

    # Tempo Search v2 API. lgtm exposes Tempo's /api/search on :3200 (host port).
    # Query for service.name=order-producer; expect at least 1 trace.
    SEARCH=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&limit=10" 2>/dev/null)
    if [ -z "$SEARCH" ] || [ "$SEARCH" = '{"traces":null}' ] || [ "$SEARCH" = '{"traces":[]}' ]; then
      # Fallback to Grafana datasource proxy
      SEARCH=$(curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/search?tags=service.name%3Dorder-producer&limit=10")
    fi
    TRACE_ID=$(printf '%s' "$SEARCH" | python3 -c "import json,sys; d=json.load(sys.stdin); ts=d.get('traces',[]); assert ts, 'no traces'; print(ts[0]['traceID'])")
    test -n "$TRACE_ID"
    ```

    **Step 5 — Fetch the full trace and assert 3 spans of correct kinds:**
    ```
    TRACE=$(curl -s "http://localhost:3200/api/traces/$TRACE_ID" 2>/dev/null \
      || curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/traces/$TRACE_ID")

    # The Tempo trace JSON has shape: { batches: [ { resource: {...}, scopeSpans: [ { spans: [ ... ] } ] } ] }
    # Count distinct spans and their kinds. Span kind in OTLP JSON is encoded as a number:
    #   SPAN_KIND_INTERNAL=1, SPAN_KIND_SERVER=2, SPAN_KIND_CLIENT=3, SPAN_KIND_PRODUCER=4, SPAN_KIND_CONSUMER=5
    printf '%s' "$TRACE" | python3 - <<'PY'
    import json, sys, collections
    trace = json.load(sys.stdin)
    spans = []
    for batch in trace.get("batches", []):
        for ss in batch.get("scopeSpans", []):
            spans.extend(ss.get("spans", []))
    assert len(spans) >= 3, f"Expected >= 3 spans, got {len(spans)}: {[s['name'] for s in spans]}"
    by_kind = collections.Counter(s.get("kind", 0) for s in spans)
    # SPAN_KIND_SERVER=2, SPAN_KIND_INTERNAL=1, SPAN_KIND_PRODUCER=4
    assert by_kind.get(2, 0) >= 1, f"No SERVER span: {by_kind}"
    assert by_kind.get(1, 0) >= 1, f"No INTERNAL span: {by_kind}"
    assert by_kind.get(4, 0) >= 1, f"No PRODUCER span: {by_kind}"
    # Span name assertions
    names = {s["name"] for s in spans}
    assert "POST /orders" in names, f"missing 'POST /orders' SERVER span: {names}"
    assert "OrderService.place" in names, f"missing 'OrderService.place' INTERNAL span: {names}"
    assert "orders.created publish" in names, f"missing 'orders.created publish' PRODUCER span: {names}"
    # Parent-child chain check: every span (except root) has a parentSpanId set to another span's spanId
    by_id = {s["spanId"]: s for s in spans}
    for s in spans:
        psid = s.get("parentSpanId", "")
        if psid:
            assert psid in by_id, f"span '{s['name']}' has parentSpanId {psid} not in trace"
    print(f"OK: {len(spans)} spans with kinds {dict(by_kind)} and names {names}")
    PY
    ```

    **Step 6 — Assert PRODUCER span carries the 4 messaging semconv attrs:**
    Same Tempo trace; iterate the PRODUCER span's attributes and check each key.
    ```
    printf '%s' "$TRACE" | python3 - <<'PY'
    import json, sys
    trace = json.load(sys.stdin)
    spans = [s for b in trace.get("batches",[]) for ss in b.get("scopeSpans",[]) for s in ss.get("spans",[])]
    producer = next((s for s in spans if s.get("kind") == 4), None)
    assert producer, "no PRODUCER span"
    attrs = {a["key"]: a["value"] for a in producer.get("attributes",[])}
    # The OTel JSON attribute value is a typed object: {"stringValue":"..."} or {"intValue":...}
    def sv(k): v = attrs[k]; return v.get("stringValue") if isinstance(v, dict) else v
    assert sv("messaging.system") == "rabbitmq", f"messaging.system={sv('messaging.system')}"
    assert sv("messaging.destination.name") == "orders.created", f"messaging.destination.name={sv('messaging.destination.name')}"
    assert sv("messaging.operation.type") == "send", f"messaging.operation.type={sv('messaging.operation.type')}"
    assert sv("messaging.rabbitmq.destination.routing_key") == "order.created", f"messaging.rabbitmq.destination.routing_key={sv('messaging.rabbitmq.destination.routing_key')}"
    # Forbidden: deprecated key MESSAGING_OPERATION (bare) should NOT be set
    assert "messaging.operation" not in attrs, "deprecated key 'messaging.operation' should not be set; use 'messaging.operation.type'"
    print("OK: 4 messaging semconv attributes correct, deprecated key absent")
    PY
    ```

    **Step 7 — Cleanup:**
    ```
    kill $PID 2>/dev/null
    for i in $(seq 1 12); do kill -0 $PID 2>/dev/null || break; sleep 1; done
    ! kill -0 $PID 2>/dev/null
    ```

    Failure modes:
    - Trace has fewer than 3 spans → check parent-child chain (Spring's @Service / @Component might be re-creating instances; or makeCurrent() Scope was wrongly closed before publisher.publish ran — i.e. `return orderId;` was placed AFTER the try block). Re-read T1's note about the return statement going INSIDE the try.
    - PRODUCER span missing the routing_key attr → MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY constant typo or wrong import path; re-check RESEARCH §B.
    - "messaging.operation" key set instead of "messaging.operation.type" → executor pasted from CONTEXT.md TRACE-07 verbatim (which uses the deprecated name). Re-read T2 + RESEARCH FLAG #1.
  </action>
  <acceptance_criteria>
    - Background `mise run dev:producer` reaches `Started ProducerApplication` within 90 seconds
    - No OTel-related startup exceptions
    - POST /orders returns 202 with valid orderId in body
    - Tempo Search returns at least 1 trace with service.name=order-producer
    - The first trace contains >= 3 spans
    - Span kinds present: SERVER (kind=2), INTERNAL (kind=1), PRODUCER (kind=4)
    - Span names present (exact strings): "POST /orders", "OrderService.place", "orders.created publish"
    - Parent-child chain valid: every non-root span's parentSpanId resolves to another span in the same trace
    - PRODUCER span attributes (4 of them, exact keys + values):
      - messaging.system = "rabbitmq"
      - messaging.destination.name = "orders.created"
      - messaging.operation.type = "send"
      - messaging.rabbitmq.destination.routing_key = "order.created"
    - Deprecated key ABSENT: "messaging.operation" (bare) is NOT a key on the PRODUCER span
    - Producer process shuts down cleanly within 12s of SIGTERM
    - No leftover spring-boot:run process
  </acceptance_criteria>
  <verify>
    <automated>nohup mise run dev:producer &gt; /tmp/producer-02-04.log 2&gt;&amp;1 &amp; PID=$!; for i in $(seq 1 45); do grep -q "Started ProducerApplication" /tmp/producer-02-04.log &amp;&amp; break; sleep 2; done; grep -q "Started ProducerApplication" /tmp/producer-02-04.log &amp;&amp; test "$(curl -s -o /tmp/order-resp.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}')" = "202" &amp;&amp; sleep 8 &amp;&amp; SEARCH=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&amp;limit=10" 2&gt;/dev/null) &amp;&amp; TID=$(printf '%s' "$SEARCH" | python3 -c "import json,sys; print(json.load(sys.stdin)['traces'][0]['traceID'])") &amp;&amp; TRACE=$(curl -s "http://localhost:3200/api/traces/$TID") &amp;&amp; printf '%s' "$TRACE" | python3 -c "import json,sys; t=json.load(sys.stdin); spans=[s for b in t['batches'] for ss in b['scopeSpans'] for s in ss['spans']]; assert len(spans)&gt;=3; kinds=set(s.get('kind',0) for s in spans); assert {1,2,4}.issubset(kinds), kinds"; CODE=$?; kill $PID 2&gt;/dev/null; for i in $(seq 1 12); do kill -0 $PID 2&gt;/dev/null || break; sleep 1; done; exit $CODE</automated>
  </verify>
  <done>One POST /orders produces a producer-side trace in Tempo with 3 spans of kinds SERVER + INTERNAL + PRODUCER; span names match exactly ("POST /orders", "OrderService.place", "orders.created publish"); parent-child chain is valid; PRODUCER span carries the 4 messaging semconv attrs (system=rabbitmq, destination.name=orders.created, operation.type=send, rabbitmq.destination.routing_key=order.created) AND the deprecated "messaging.operation" key is absent.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 02-04 — producer-service business-code instrumentation)

| Boundary | Description |
|----------|-------------|
| OrderController → OrderService.place (existing) | In-process call; INTERNAL span wraps the work |
| OrderService → OrderPublisher.publish (existing) | In-process call; PRODUCER span wraps the convertAndSend |
| OrderPublisher → RabbitMQ broker (existing from Phase 1) | AMQP publish on the orders direct exchange |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-04-01 | Information Disclosure | Span attributes leaking sensitive payload data — INTERNAL span on OrderService.place(payload) could capture `payload.sku` / `payload.quantity` if attribute calls are added later | accept | Phase 2 INTERNAL span has NO setAttribute calls (D-04 — no semconv mandated); the workshop currently captures span name only. Future phases adding business attributes (e.g., order.priority for METRIC-02 in Phase 4) will be evaluated for PII exposure individually. (Phase-level T2 mitigated.) |
| T-2-04-02 | Information Disclosure | PRODUCER span attributes capture queue + routing-key names — these are workshop-local strings ("orders.created" / "order.created") with no PII | accept | Workshop scope; queue names ARE the messaging topology and are correctly observable in Tempo per OTel semconv |
| T-2-04-03 | Tampering | Catch clause re-throws RuntimeException after recordException — preserves Spring's exception flow and Tomcat's status-code mapping | mitigate | D-03 explicitly requires `throw e;` after recordException + setStatus(ERROR); acceptance criteria assert the throw line is present |
| T-2-04-04 | DoS | New spans add per-request overhead (~tens of microseconds for span creation + attribute setting) | accept | Workshop-volume traffic; BSP queue depth (2048) provides backpressure if Tempo backs up |
| T-2-04-05 | Information Disclosure | Stack trace from recordException(e) is included in span events; could leak internal class names | accept | Workshop scope; the stack trace IS the teaching moment for Phase 3's APP-04 + TRACE-09. No PII or credentials in stack traces. |
| T-2-04-06 | Repudiation | Without the inline catch, a swallowed exception in a future refactor could lose the failed-publish observation | mitigate | D-03 catch shape MUST be present (asserted by T1 + T2 acceptance criteria); refactor reviewers see the explicit shape and won't accidentally drop it |

**Plan scope:** Producer-service business-code instrumentation. No new network surface (the AMQP publish from Phase 1 is unchanged in shape — only spans are added around it). No new env vars, no new credentials, no new endpoints. Out of scope: span sampling tuning (D-14 fixed at parentBased(alwaysOn) for the workshop), span attribute redaction (Phase 2 captures no PII).
</threat_model>

<verification>
- `mvn -pl producer-service compile` exits 0
- `mvn -pl producer-service -DskipTests package` exits 0
- Background `mise run dev:producer` reaches `Started ProducerApplication` within 90s with no exceptions
- POST /orders returns 202; trace appears in Tempo within ~8s with service.name=order-producer
- Trace contains exactly 3 spans of kinds SERVER + INTERNAL + PRODUCER, named "POST /orders" + "OrderService.place" + "orders.created publish"
- Parent-child chain is valid (every non-root span's parentSpanId references another span's spanId in the same trace)
- PRODUCER span carries the 4 messaging semconv attrs with correct values; deprecated `messaging.operation` key absent
- `mise run verify:bom` still exits 0 (Plan 02-01 invariant preserved)
</verification>

<success_criteria>
- TRACE-06 satisfied (producer half): INTERNAL span wraps OrderService.place(...), named "OrderService.place" with SpanKind.INTERNAL.
- TRACE-07 satisfied: PRODUCER span wraps OrderPublisher.publish(...), named "orders.created publish" with SpanKind.PRODUCER and 4 messaging semantic-convention attributes (system=rabbitmq, destination.name=orders.created, operation.type=send, rabbitmq.destination.routing_key=order.created).
- D-01 honored: every span uses the EXACT pure-inline template (no helper, no AOP); 5 sites in Phase 2 share the same idiom.
- D-02 honored: Tracer is constructor-injected in OrderService and OrderPublisher; no GlobalOpenTelemetry, no field injection.
- D-03 honored: catch shape present in both new spans (`recordException + setStatus(ERROR) + throw + finally end`).
- D-04 honored: PRODUCER span name uses the QUEUE name ("orders.created"); routing key set as a separate attribute.
- D-11 honored: 4th attribute (messaging.rabbitmq.destination.routing_key) added to teach AMQP-specific semconv beyond the generic three.
- RESEARCH FLAG #1 addressed: uses MESSAGING_OPERATION_TYPE constant + MessagingOperationTypeIncubatingValues.SEND value enum, NOT the deprecated MESSAGING_OPERATION + literal "publish"; teaching comment present.
- One POST /orders produces a producer trace in Tempo with the correct 3-span shape.
- Plan 02-05 (consumer-side instrumentation, in parallel Wave 3) is unblocked and can independently land its CONSUMER + INTERNAL spans.
</success_criteria>

<output>
After completion, create `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-SUMMARY.md` documenting:
- File tree of producer-service/src/main/java (5 Java files; OrderService + OrderPublisher modified, others unchanged)
- Confirmed `mvn -pl producer-service -DskipTests package` BUILD SUCCESS line
- Confirmed Tempo trace fetch result: paste the python3 OK output ("OK: 3 spans with kinds {1: 1, 2: 1, 4: 1} and names ...")
- Confirmed messaging semconv attrs: paste the python3 OK output ("OK: 4 messaging semconv attributes correct, deprecated key absent")
- Confirmed parent-child chain: SERVER→INTERNAL→PRODUCER (paste the trace structure summary)
- Files modified: 2 (OrderService.java + OrderPublisher.java); OrderController unchanged per PATTERNS.md line 91
- Hand-off for Plan 02-05: producer-side Phase 2 instrumentation complete; consumer side still needs CONSUMER + INTERNAL spans before the Phase 2 success criteria are met end-to-end (TWO disconnected traces appearing in Tempo)
- Hand-off for Plan 02-06: when Plan 02-05 lands, the README "Why duplicated?" callout (DOC-05) is the next step + the `step-02-traces` annotated tag
- Hand-off for Phase 3: this Plan's PRODUCER span will be REPLACED by TracingMessagePostProcessor in Phase 3; the inline span code in OrderPublisher.java will be deleted at that point (CONTEXT.md D-09 deferred-ideas note)
</output>
