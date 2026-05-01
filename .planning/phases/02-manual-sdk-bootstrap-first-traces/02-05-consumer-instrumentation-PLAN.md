---
id: 02-05-consumer-instrumentation
phase: 02-manual-sdk-bootstrap-first-traces
plan: 05
type: execute
wave: 3
depends_on: [02-03-consumer-sdk-config]
requirements: [TRACE-06, TRACE-08]
files_modified:
  - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
autonomous: true
must_haves:
  truths:
    - "OrderListener.onOrder(...) is wrapped in a CONSUMER span named \"orders.created process\" (TRACE-08) with `.setParent(Context.root())` (D-10) and 3 messaging semconv attrs (system=RABBITMQ, destination_name=\"orders.created\", operation_type=PROCESS)"
    - "The CONSUMER span includes the EXACT verbatim multi-line teaching comment from D-10 previewing Phase 3's propagator.extract(Context.root(), messageProperties, getter) line — this is a load-bearing pedagogy artifact, not a nicety"
    - "ProcessingService.process(...) is wrapped in an INTERNAL span named \"ProcessingService.process\" (TRACE-06 consumer half) using the EXACT D-01 pure-inline template — body is just the Phase-1 placeholder comment that moves INSIDE the try block"
    - "Both classes constructor-inject Tracer (D-02); existing dependencies preserved (OrderListener still depends on ProcessingService; ProcessingService introduces its first field via the Tracer parameter)"
    - "All catch blocks include the FULL D-03 forward-compatible shape: span.recordException(e); span.setStatus(StatusCode.ERROR); throw e; — even though Phase 2 has no failure path yet"
    - "CONSUMER span uses MESSAGING_OPERATION_TYPE constant + MessagingOperationTypeIncubatingValues.PROCESS value enum (NOT the deprecated MESSAGING_OPERATION/\"process\" literal per RESEARCH FLAG #1)"
    - "OrderListener preserves its Phase 1 LOG.info(...) line — the SLF4J call MOVES inside the try block (it's part of the listener body now wrapped in the CONSUMER span)"
    - "mvn -pl consumer-service compile exits 0; mvn -pl consumer-service -DskipTests package exits 0"
    - "End-to-end: with both producer + consumer running, POST /orders produces TWO DISTINCT traces in Tempo (one with service.name=order-producer 3 spans; one with service.name=order-consumer 2 spans CONSUMER → INTERNAL) — the broken-then-fixed-pedagogy state Phase 2 ships, NOT one joined trace (Phase 3 fixes that)"
  artifacts:
    - path: "consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java"
      provides: "Tracer-injected OrderListener with CONSUMER span around onOrder(...) starting from Context.root() per D-10 + 3 messaging semconv attrs (TRACE-08)"
      contains: "SpanKind.CONSUMER"
    - path: "consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
      provides: "Tracer-injected ProcessingService with INTERNAL span around process(...) — span body wraps the Phase-1 placeholder comment so the span has a body to wrap"
      contains: "tracer.spanBuilder(\"ProcessingService.process\")"
  key_links:
    - from: "consumer-service/.../messaging/OrderListener.java"
      to: "OtelSdkConfiguration.@Bean Tracer (consumer-side)"
      via: "constructor injection"
      pattern: "private final Tracer tracer"
    - from: "OrderListener.onOrder CONSUMER span"
      to: "ProcessingService.process INTERNAL span"
      via: "Span.makeCurrent() Scope keeps CONSUMER active while processingService.process(message) runs; INTERNAL span's spanBuilder picks up parent via Context.current()"
      pattern: "try (Scope scope = span.makeCurrent())"
    - from: "CONSUMER span .setParent(Context.root())"
      to: "Phase 3's propagator.extract(...) replacement"
      via: "Multi-line teaching comment above the spanBuilder call previewing the Phase 3 idiom"
      pattern: "Context.root()"
---

<objective>
Wrap consumer-service's two business-logic touch points in their respective OTel spans using the D-01 pure-inline template. `OrderListener.onOrder(...)` gets a CONSUMER span (TRACE-08) named `"orders.created process"` with 3 messaging semconv attributes (`system=rabbitmq`, `destination_name=orders.created`, `operation_type=process` via the value enum). `ProcessingService.process(...)` gets an INTERNAL span (TRACE-06 consumer half) named `"ProcessingService.process"`. Together they produce a 2-span consumer trace per inbound message: CONSUMER → INTERNAL.

The CONSUMER span is special: it explicitly starts from `Context.root()` (D-10) so the consumer trace is INTENTIONALLY disconnected from the producer trace in Phase 2. Above the `spanBuilder` call sits the EXACT multi-line teaching comment from CONTEXT.md D-10 that previews Phase 3's `propagator.extract(Context.root(), messageProperties, getter)` line. The comment is **load-bearing** per CONTEXT.md `<specifics>` line 190: without it, attendees see the explicit `Context.root()` and don't understand WHY it's there. The structural shape stays IDENTICAL between Phase 2 and Phase 3 — only the parent source changes.

Together with the producer's 3-span trace from Plan 02-04, one `POST /orders` round-trip produces TWO distinct Tempo traces in Phase 2 — that's the workshop's broken-then-fixed-pedagogy state. Phase 3's PROP-01/PROP-02 will join them.

Purpose: TRACE-06 consumer half + TRACE-08 (CONSUMER span with messaging semconv attrs). Output: 2 modified Java files + the consumer half of Phase 2's success criterion #4 (consumer trace = CONSUMER → INTERNAL) + criterion #1 (TWO distinct traces in Tempo per POST).
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
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-03-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="02-05-T1" type="auto">
  <name>Task 1: Modify OrderListener.java — add Tracer constructor param + wrap onOrder(...) body in CONSUMER span starting from Context.root() (D-10) with the load-bearing teaching comment + 3 messaging semconv attrs</name>
  <files>consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 76-86 — D-10 EXACT verbatim teaching comment + spanBuilder("orders.created process").setParent(Context.root()).setSpanKind(SpanKind.CONSUMER); load-bearing per <specifics> line 190)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 71-89 — D-08 CONSUMER span lives inline in OrderListener.onOrder; D-09 Phase 3 will REPLACE this with TracingMessageListenerAdvice)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 199-203 — Deferred: Phase 3 hand-off note that Phase 3 deletes this inline CONSUMER span when adding TracingMessageListenerAdvice)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 156-167 — incubating constants: MessagingIncubatingAttributes.MESSAGING_SYSTEM/MESSAGING_DESTINATION_NAME/MESSAGING_OPERATION_TYPE + MessagingOperationTypeIncubatingValues.PROCESS value enum)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 207-215 — RESEARCH FLAG #1: use MESSAGING_OPERATION_TYPE + PROCESS value enum, NOT deprecated MESSAGING_OPERATION + "process" literal)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 129-148 — file modification spec; LOG.info + processingService.process MOVE INSIDE the try block)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 230-235 + 261-272 — Application Map row 4 + CONSUMER variant of D-01 template with verbatim D-10 teaching comment text)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (current Phase 1 state — 28 lines; SLF4J already imported; @RabbitListener intact)
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (just written in Plan 02-03 — exposes @Bean Tracer)
    - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java (provides QUEUE constant for MESSAGING_DESTINATION_NAME)
  </read_first>
  <action>
    Replace the entire current `OrderListener.java` content with the new version below. The body of `onOrder(...)` is wrapped in a CONSUMER span using the D-01 pure-inline template, with 3 messaging semconv attrs and **the EXACT multi-line teaching comment from D-10 above the spanBuilder call**.

    The comment text is verbatim from CONTEXT.md D-10 lines 78-83 — do NOT rephrase, do NOT shorten, do NOT inline. It IS a Phase 2 deliverable per CONTEXT.md `<specifics>` line 190.

    The existing `LOG.info("OrderCreated received: orderId={}", orderId)` line MUST be preserved AND moved INSIDE the try block (per PATTERNS.md line 147). Keeping the log line is important for two reasons: (1) Phase 5 wires logs correlation and the LOG.info inside the active CONSUMER span will be the first thing that gets a trace_id stamped; (2) the workshop's verify step checks the consumer terminal for "OrderCreated received" — that contract must continue to hold.

    ```java
    package com.example.consumer.messaging;

    import java.util.Map;

    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Context;
    import io.opentelemetry.context.Scope;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;

    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.amqp.rabbit.annotation.RabbitListener;
    import org.springframework.stereotype.Component;

    import com.example.consumer.config.RabbitConfig;
    import com.example.consumer.domain.ProcessingService;

    /**
     * AMQP listener — processes OrderCreated messages from the orders.created queue.
     *
     * Phase 2 wraps {@link #onOrder(Map)} in a CONSUMER span (TRACE-08) named
     * "orders.created process" that explicitly starts from Context.root()
     * (D-10) — the consumer is INTENTIONALLY in a separate trace from the
     * producer in Phase 2 (broken-then-fixed pedagogy). Phase 3 replaces the
     * Context.root() with propagator.extract(...) to join the traces — see
     * the multi-line teaching comment above the spanBuilder call.
     */
    @Component
    public class OrderListener {
        private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
        private final ProcessingService processingService;
        private final Tracer tracer;

        public OrderListener(ProcessingService processingService, Tracer tracer) {
            this.processingService = processingService;
            this.tracer = tracer;
        }

        // APP-03: receive OrderCreated and simulate downstream domain work.
        @RabbitListener(queues = RabbitConfig.QUEUE)
        public void onOrder(Map<String, Object> message) {
            // Phase 2: starting from Context.root() because no propagation yet —
            // Phase 3 replaces this with:
            //   Context extracted = propagator.extract(Context.root(), messageProperties, getter);
            // The structural shape stays IDENTICAL.
            //
            // (semconv 1.40.0 renamed messaging.operation → messaging.operation.type;
            // values: send|receive|process|create. We use MESSAGING_OPERATION_TYPE
            // and the PROCESS value enum.)
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
    - The teaching comment above the `spanBuilder` call MUST contain the four lines from D-10 verbatim (the colon-suffixed `Phase 3 replaces this with:` line, the indented `Context extracted = propagator.extract(...)` line, the `The structural shape stays IDENTICAL.` line). The acceptance test greps for the exact substring `propagator.extract(Context.root(), messageProperties, getter)` — that's the verbatim Phase 3 idiom CONTEXT.md says to preview.
    - All three lines (`Object orderId = ...`, `LOG.info(...)`, `processingService.process(...)`) live INSIDE the try block.
    - `.setParent(Context.root())` MUST be called BEFORE `.setSpanKind(SpanKind.CONSUMER)` and `.startSpan()` — order matters in spanBuilder fluent chains.
    - `Context` import is `io.opentelemetry.context.Context` (NOT a Spring or javax class).
    - Constructor signature changes from `OrderListener(ProcessingService processingService)` to `OrderListener(ProcessingService processingService, Tracer tracer)`.
    - Imports sorted: java.* / io.opentelemetry.* / org.* / com.example.* (matches Phase 1 style).
    - Catch parameter is `RuntimeException` (Spring AMQP wraps all listener-thread errors in unchecked RuntimeException by default; the @RabbitListener handler signature can throw checked but Spring's RabbitListenerErrorHandler wraps them — keeping RuntimeException is symmetric with the producer-side spans).
  </action>
  <acceptance_criteria>
    - `test -f consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - Constructor injects both processingService and tracer: `grep -c 'public OrderListener(ProcessingService processingService, Tracer tracer)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - Tracer is a private final field: `grep -c 'private final Tracer tracer;' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - Existing LOG / SLF4J wiring preserved: `grep -c 'private static final Logger LOG' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1; `grep -c 'LOG.info("OrderCreated received: orderId={}", orderId)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - @RabbitListener intact: `grep -c '@RabbitListener(queues = RabbitConfig.QUEUE)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - CONSUMER span name and kind: `grep -c 'tracer.spanBuilder("orders.created process")' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1; `grep -c 'setSpanKind(SpanKind.CONSUMER)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - Context.root() parent set (D-10): `grep -c '.setParent(Context.root())' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - D-10 verbatim teaching comment present (load-bearing per CONTEXT.md <specifics>):
      - `grep -c 'Phase 2: starting from Context.root() because no propagation yet' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
      - `grep -c 'Phase 3 replaces this with:' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
      - `grep -c 'propagator.extract(Context.root(), messageProperties, getter)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
      - `grep -c 'The structural shape stays IDENTICAL' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - All three messaging semconv attrs set: `for k in MESSAGING_SYSTEM MESSAGING_DESTINATION_NAME MESSAGING_OPERATION_TYPE; do grep -q "$k" consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java || exit 1; done` exits 0
    - Uses NEW key (NOT deprecated bare MESSAGING_OPERATION): `! grep -E 'MESSAGING_OPERATION[^_]' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - Uses value enum (NOT string literal): `grep -c 'MessagingOperationTypeIncubatingValues.PROCESS' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1; `grep -c 'MessagingSystemIncubatingValues.RABBITMQ' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - Forbidden literal "process" value NOT used as a value (only acceptable matches are inside the span name "orders.created process" and the comment text): `grep -E '"process"' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java | grep -v '"orders.created process"' | wc -l | awk '{exit ($1==0)?0:1}'` exits 0
    - DESTINATION_NAME = RabbitConfig.QUEUE: `grep -B1 -A1 'MESSAGING_DESTINATION_NAME' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java | grep -c 'RabbitConfig.QUEUE'` returns 1
    - try-with-resources Scope present: `grep -c 'try (Scope scope = span.makeCurrent())' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - All three onOrder lines inside try (orderId get + LOG.info + processingService.process): `awk '/try \(Scope scope = span.makeCurrent\(\)\)/,/^ +} catch/' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java | grep -cE 'message.get\("orderId"\)|LOG.info|processingService.process' | awk '{exit ($1==3)?0:1}'` exits 0
    - D-03 catch shape: `grep -c 'span.recordException(e)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1; `grep -c 'span.setStatus(StatusCode.ERROR)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1; `grep -c 'throw e;' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1; `grep -c 'span.end()' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - Imports correct: `for c in 'io.opentelemetry.api.trace.Span' 'io.opentelemetry.api.trace.SpanKind' 'io.opentelemetry.api.trace.StatusCode' 'io.opentelemetry.api.trace.Tracer' 'io.opentelemetry.context.Context' 'io.opentelemetry.context.Scope' 'io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes'; do grep -q "import $c" consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java || exit 1; done` exits 0
    - consumer-service compiles: `mvn -pl consumer-service -q compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl consumer-service -q compile &amp;&amp; grep -q 'tracer.spanBuilder("orders.created process")' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q '.setParent(Context.root())' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'setSpanKind(SpanKind.CONSUMER)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'MessagingOperationTypeIncubatingValues.PROCESS' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'propagator.extract(Context.root(), messageProperties, getter)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'The structural shape stays IDENTICAL' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; ! grep -E 'MESSAGING_OPERATION[^_]' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java</automated>
  </verify>
  <done>OrderListener.java now constructor-injects Tracer alongside ProcessingService; onOrder(...) body wrapped in CONSUMER span "orders.created process" with .setParent(Context.root()) (D-10) and 3 messaging semconv attrs (system=RABBITMQ value enum, destination_name=RabbitConfig.QUEUE, operation_type=PROCESS value enum); the verbatim D-10 multi-line teaching comment is present above the spanBuilder; LOG.info preserved + moved inside try; D-03 catch shape present; consumer-service compiles green.</done>
</task>

<task id="02-05-T2" type="auto">
  <name>Task 2: Modify ProcessingService.java — add Tracer constructor param + wrap process(...) body in INTERNAL span (D-01 inline template)</name>
  <files>consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 36-58 — D-01 pure-inline template + D-03 catch shape + D-04 INTERNAL span name "ClassName.method")
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 149-159 — file modification spec for ProcessingService: introduces Tracer field for the first time; body remains the empty Phase-1 placeholder comment but moves INSIDE try)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 230-235 — Application Map row 5: INTERNAL span name = "ProcessingService.process"; no semconv attrs mandated)
    - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java (current Phase 1 state — 14 lines; method body is just two comment lines)
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (just written in Plan 02-03 — exposes @Bean Tracer)
  </read_first>
  <action>
    Replace the entire current `ProcessingService.java` content with the new version below. The body of `process(...)` is wrapped in an INTERNAL span using the D-01 pure-inline template — even though the body is currently empty (Phase 1 placeholder), the comment block MOVES INSIDE the try block so the span has a body to wrap.

    Phase 3 will populate the empty body with the deterministic 10%-failure path (APP-04) and the recordException-driven error span (TRACE-09). The catch block we set up here ALREADY has the D-03 shape — Phase 3 just provides the throw site.

    ```java
    package com.example.consumer.domain;

    import java.util.Map;

    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Scope;

    import org.springframework.stereotype.Service;

    /**
     * Domain layer — simulates downstream order-processing work.
     *
     * Phase 2 wraps {@link #process(Map)} in an INTERNAL span (TRACE-06)
     * named "ProcessingService.process" using the D-01 pure-inline template.
     * The body is currently empty (the Phase 1 placeholder comments) — Phase 3
     * adds the deterministic 10%-failure path (APP-04) and the
     * recordException-driven error span (TRACE-09). The D-03 catch shape is
     * ALREADY in place; Phase 3 only provides the throw site.
     */
    @Service
    public class ProcessingService {
        private final Tracer tracer;

        public ProcessingService(Tracer tracer) {
            this.tracer = tracer;
        }

        public void process(Map<String, Object> order) {
            // ---- D-01 inline span template (INTERNAL) ----
            //
            // Pure inline. No helper. The body is currently empty (Phase 1
            // placeholder); the placeholder comment block has moved INSIDE
            // the try so the span actually wraps something.
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

    Notes for the executor:
    - The Phase 1 comment lines (`// Phase 1: simulated domain work, in-memory only.` and `// Phase 3 wires up the deterministic 10% failure path (APP-04).`) are PRESERVED but MOVED INSIDE the try block (per PATTERNS.md line 159).
    - Constructor signature changes from no-arg implicit to explicit single-arg `ProcessingService(Tracer tracer)` — the class now has its first field. Spring's autowire-by-default kicks in for the single constructor.
    - 4-space Java indentation; imports sorted as in Phase 1 style.
    - Catch parameter is `RuntimeException` for symmetry with the rest of Phase 2's spans.
  </action>
  <acceptance_criteria>
    - `test -f consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - Constructor: `grep -c 'public ProcessingService(Tracer tracer)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - Tracer is a private final field: `grep -c 'private final Tracer tracer;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - INTERNAL span name and kind: `grep -c 'tracer.spanBuilder("ProcessingService.process")' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1; `grep -c 'setSpanKind(SpanKind.INTERNAL)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - try-with-resources Scope present: `grep -c 'try (Scope scope = span.makeCurrent())' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - Phase 1 placeholder comments MOVED INSIDE try (still present): `awk '/try \(Scope scope = span.makeCurrent\(\)\)/,/^ +} catch/' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | grep -c 'simulated domain work'` returns 1
    - D-03 catch shape: `grep -c 'span.recordException(e)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1; `grep -c 'span.setStatus(StatusCode.ERROR)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1; `grep -c 'throw e;' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1; `grep -c 'span.end()' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - Imports correct: `for c in 'io.opentelemetry.api.trace.Span' 'io.opentelemetry.api.trace.SpanKind' 'io.opentelemetry.api.trace.StatusCode' 'io.opentelemetry.api.trace.Tracer' 'io.opentelemetry.context.Scope'; do grep -q "import $c;" consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java || exit 1; done` exits 0
    - consumer-service compiles: `mvn -pl consumer-service -q compile` exits 0
    - consumer-service packages cleanly: `mvn -pl consumer-service -q -DskipTests package` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl consumer-service -q -DskipTests package &amp;&amp; grep -q 'tracer.spanBuilder("ProcessingService.process")' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'setSpanKind(SpanKind.INTERNAL)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; grep -q 'span.recordException(e)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java &amp;&amp; awk '/try \(Scope scope = span.makeCurrent\(\)\)/,/^ +} catch/' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | grep -q 'simulated domain work'</automated>
  </verify>
  <done>ProcessingService.java now constructor-injects Tracer (introduces its first field); process(...) body wrapped in INTERNAL span "ProcessingService.process"; the Phase 1 placeholder comments preserved and moved INSIDE the try block; D-03 catch shape present (ready for Phase 3's APP-04 to provide the throw site); consumer-service packages cleanly.</done>
</task>

<task id="02-05-T3" type="auto">
  <name>Task 3: End-to-end smoke — start BOTH apps, POST /orders, verify TWO DISTINCT TRACES (producer 3-span SERVER→INTERNAL→PRODUCER + consumer 2-span CONSUMER→INTERNAL) appear in Tempo with different traceIds</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 184-194 — End-user-verifiable: TWO disconnected traces per POST; producer SERVER→INTERNAL→PRODUCER; consumer CONSUMER→INTERNAL; criterion #1 from ROADMAP §"Phase 2 Success Criteria")
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 8-10 — Phase Boundary: "TWO disconnected traces in Tempo — one rooted at the producer's HTTP SERVER span, one rooted at the consumer's CONSUMER span. The disconnection is INTENTIONAL")
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-SUMMARY.md (Plan 02-04 ended with the producer trace verified; this task adds the consumer trace + the disconnection assertion)
    - mise.toml (lines 22, 97-112, 117-119 — env vars, dev:producer, dev:consumer, dev parallel, demo:order)
  </read_first>
  <action>
    Verify the Phase 2 broken-then-fixed-pedagogy state ships correctly: with BOTH producer and consumer running, one POST /orders produces TWO DISTINCT TRACES in Tempo — a producer trace (3 spans, service.name=order-producer) and a consumer trace (2 spans, service.name=order-consumer). The two traces have DIFFERENT traceIds (proving propagation is intentionally absent).

    **Step 1 — Ensure infra is up:**
    `mise run infra:up` (idempotent).

    **Step 2 — Start BOTH apps in parallel:**
    The simplest path is `mise run dev` (which spawns dev:producer + dev:consumer in parallel and Ctrl-C cleanly stops both — verified by Phase 1 SUMMARYs). For deterministic verification we'll run them as separate background processes so we can capture per-process logs and PIDs.
    ```
    nohup mise run dev:producer > /tmp/producer-02-05.log 2>&1 &
    PID_PROD=$!
    nohup mise run dev:consumer > /tmp/consumer-02-05.log 2>&1 &
    PID_CONS=$!
    for i in $(seq 1 60); do
      grep -q "Started ProducerApplication" /tmp/producer-02-05.log && grep -q "Started ConsumerApplication" /tmp/consumer-02-05.log && break
      sleep 2
    done
    grep -q "Started ProducerApplication" /tmp/producer-02-05.log || { tail -50 /tmp/producer-02-05.log; kill $PID_PROD $PID_CONS 2>/dev/null; exit 1; }
    grep -q "Started ConsumerApplication" /tmp/consumer-02-05.log || { tail -50 /tmp/consumer-02-05.log; kill $PID_PROD $PID_CONS 2>/dev/null; exit 1; }
    # Verify no startup exceptions in either
    if grep -E 'NoSuchMethodError|NoClassDefFoundError|IllegalArgumentException.*opentelemetry|unknown_service:java' /tmp/producer-02-05.log /tmp/consumer-02-05.log; then
      kill $PID_PROD $PID_CONS 2>/dev/null; exit 1
    fi
    ```

    **Step 3 — POST /orders to trigger the round-trip:**
    ```
    curl -s -o /tmp/order-resp.json -w "%{http_code}\n" -X POST http://localhost:8080/orders \
      -H 'Content-Type: application/json' \
      -d '{"sku":"WIDGET-1","quantity":3}'
    # Expect: 202
    ORDER_ID=$(python3 -c "import json; print(json.load(open('/tmp/order-resp.json'))['orderId'])")
    test -n "$ORDER_ID"

    # Wait for the consumer to receive (Phase 1 contract: LOG.info "OrderCreated received: orderId=...")
    for i in $(seq 1 15); do
      if grep -q "OrderCreated received: orderId=$ORDER_ID" /tmp/consumer-02-05.log; then break; fi
      sleep 1
    done
    grep -q "OrderCreated received: orderId=$ORDER_ID" /tmp/consumer-02-05.log
    ```

    **Step 4 — Wait for BSP flush + query Tempo for BOTH traces:**
    ```
    sleep 8  # 5s BSP schedule + margin (both apps flush independently)

    # Helper to query and parse one trace by service.name
    fetch_trace_for() {
      local svc="$1"
      local SEARCH=$(curl -s "http://localhost:3200/api/search?tags=service.name%3D${svc}&limit=10" 2>/dev/null)
      if [ -z "$SEARCH" ] || [ "$SEARCH" = '{"traces":null}' ] || [ "$SEARCH" = '{"traces":[]}' ]; then
        SEARCH=$(curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/search?tags=service.name%3D${svc}&limit=10")
      fi
      printf '%s' "$SEARCH" | python3 -c "import json,sys; d=json.load(sys.stdin); ts=d.get('traces',[]); assert ts, 'no traces for ${svc}'; print(ts[0]['traceID'])"
    }

    PROD_TRACE_ID=$(fetch_trace_for "order-producer")
    CONS_TRACE_ID=$(fetch_trace_for "order-consumer")
    test -n "$PROD_TRACE_ID"
    test -n "$CONS_TRACE_ID"

    # ============================================================
    # CORE PHASE 2 ASSERTION: traces are DISCONNECTED
    # The whole point of Phase 2 is that producer + consumer have DIFFERENT traceIds.
    # Phase 3 will make them equal; Phase 2's broken-then-fixed pedagogy depends on
    # them being DIFFERENT here.
    # ============================================================
    test "$PROD_TRACE_ID" != "$CONS_TRACE_ID" || { echo "FAIL: producer and consumer share traceId ${PROD_TRACE_ID} — Phase 2 should NOT yet have propagation working"; kill $PID_PROD $PID_CONS 2>/dev/null; exit 1; }
    ```

    **Step 5 — Fetch + assert producer trace shape (3 spans, kinds SERVER+INTERNAL+PRODUCER):**
    Same assertion as Plan 02-04 T3 — repeat here to confirm Plan 02-05's parallel changes did NOT regress the producer trace.
    ```
    PROD_TRACE=$(curl -s "http://localhost:3200/api/traces/$PROD_TRACE_ID" 2>/dev/null \
      || curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/traces/$PROD_TRACE_ID")
    printf '%s' "$PROD_TRACE" | python3 - <<PY
    import json, sys, collections
    t=json.load(sys.stdin)
    spans=[s for b in t.get("batches",[]) for ss in b.get("scopeSpans",[]) for s in ss.get("spans",[])]
    kinds=collections.Counter(s.get("kind",0) for s in spans)
    assert kinds.get(2,0)>=1 and kinds.get(1,0)>=1 and kinds.get(4,0)>=1, f"producer trace kinds {kinds}"
    names={s["name"] for s in spans}
    assert {"POST /orders","OrderService.place","orders.created publish"} <= names, f"producer trace names {names}"
    print(f"OK producer: {len(spans)} spans, kinds {dict(kinds)}, names {names}")
    PY
    ```

    **Step 6 — Fetch + assert consumer trace shape (2 spans, kinds CONSUMER+INTERNAL, plus 3 messaging attrs on the CONSUMER span):**
    ```
    CONS_TRACE=$(curl -s "http://localhost:3200/api/traces/$CONS_TRACE_ID" 2>/dev/null \
      || curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/traces/$CONS_TRACE_ID")
    printf '%s' "$CONS_TRACE" | python3 - <<PY
    import json, sys, collections
    t=json.load(sys.stdin)
    spans=[s for b in t.get("batches",[]) for ss in b.get("scopeSpans",[]) for s in ss.get("spans",[])]
    assert len(spans)>=2, f"consumer trace expected >=2 spans, got {len(spans)}: {[s['name'] for s in spans]}"
    kinds=collections.Counter(s.get("kind",0) for s in spans)
    # SPAN_KIND_CONSUMER=5, SPAN_KIND_INTERNAL=1
    assert kinds.get(5,0)>=1, f"no CONSUMER span: {kinds}"
    assert kinds.get(1,0)>=1, f"no INTERNAL span: {kinds}"
    names={s["name"] for s in spans}
    assert "orders.created process" in names, f"missing CONSUMER span name: {names}"
    assert "ProcessingService.process" in names, f"missing INTERNAL span name: {names}"
    # Parent-child: INTERNAL's parentSpanId should match CONSUMER's spanId
    consumer_span = next(s for s in spans if s.get("kind")==5)
    internal_span = next(s for s in spans if s.get("kind")==1)
    assert internal_span.get("parentSpanId","") == consumer_span.get("spanId",""), (
        f"INTERNAL parentSpanId {internal_span.get('parentSpanId')} != CONSUMER spanId {consumer_span.get('spanId')}"
    )
    # CONSUMER span attrs check
    attrs={a["key"]:a["value"] for a in consumer_span.get("attributes",[])}
    def sv(k): v=attrs[k]; return v.get("stringValue") if isinstance(v,dict) else v
    assert sv("messaging.system")=="rabbitmq", attrs
    assert sv("messaging.destination.name")=="orders.created", attrs
    assert sv("messaging.operation.type")=="process", attrs
    assert "messaging.operation" not in attrs, "deprecated key 'messaging.operation' should be absent"
    print(f"OK consumer: {len(spans)} spans, kinds {dict(kinds)}, names {names}")
    PY
    ```

    **Step 7 — Verify CONSUMER span has NO parent (root started from Context.root() per D-10):**
    ```
    printf '%s' "$CONS_TRACE" | python3 - <<PY
    import json, sys
    t=json.load(sys.stdin)
    spans=[s for b in t.get("batches",[]) for ss in b.get("scopeSpans",[]) for s in ss.get("spans",[])]
    consumer_span = next(s for s in spans if s.get("kind")==5)
    psid=consumer_span.get("parentSpanId","")
    # parentSpanId should be empty (or the OTel "00000..." zero-marker) — proves Context.root() took effect
    assert psid in ("", "0000000000000000"), f"CONSUMER span has parentSpanId {psid!r} — Context.root() should produce empty/zero parent"
    print(f"OK: CONSUMER span has empty parentSpanId (Context.root() honored, D-10 verified)")
    PY
    ```

    **Step 8 — Cleanup:**
    ```
    kill $PID_PROD $PID_CONS 2>/dev/null
    for i in $(seq 1 12); do { kill -0 $PID_PROD 2>/dev/null || kill -0 $PID_CONS 2>/dev/null; } || break; sleep 1; done
    ! kill -0 $PID_PROD 2>/dev/null
    ! kill -0 $PID_CONS 2>/dev/null
    ! pgrep -f spring-boot:run
    ```

    Failure modes:
    - Two traces share the SAME traceId → propagation accidentally working in Phase 2 (CONSUMER span isn't starting from Context.root() — re-check T1; or AMQP message is somehow carrying a traceparent header that the SDK is auto-extracting; baseline OTel does NOT auto-instrument @RabbitListener so this is unlikely but worth verifying).
    - Consumer trace has 0 or 1 span → either CONSUMER span never started (check Tracer bean wiring in Plan 02-03), or makeCurrent() Scope was wrongly closed before processingService.process ran (check that processingService.process is INSIDE the try block in T1).
    - CONSUMER span has a non-empty parentSpanId → `.setParent(Context.root())` was omitted from the spanBuilder chain (re-read T1).
    - "OrderCreated received" log never appears → AMQP plumbing broken (check RabbitMQ Mgmt UI for queue depth on `orders.created`).
  </action>
  <acceptance_criteria>
    - Both apps start cleanly within 60s; no OTel-related startup exceptions in either log
    - POST /orders returns 202; orderId captured
    - Consumer log contains "OrderCreated received: orderId=$ORDER_ID" within 15s of the POST
    - Tempo Search returns at least 1 trace for service.name=order-producer AND at least 1 trace for service.name=order-consumer
    - **PROD_TRACE_ID != CONS_TRACE_ID** — the two traces have DIFFERENT traceIds (Phase 2 broken-then-fixed-pedagogy state)
    - Producer trace contains 3 spans of kinds SERVER + INTERNAL + PRODUCER with names "POST /orders" + "OrderService.place" + "orders.created publish" (regression check from Plan 02-04)
    - Consumer trace contains 2 spans of kinds CONSUMER + INTERNAL with names "orders.created process" + "ProcessingService.process"
    - Consumer trace's INTERNAL span parentSpanId equals the CONSUMER span's spanId (intra-consumer parent chain valid)
    - Consumer trace's CONSUMER span parentSpanId is empty/zero (root started from Context.root() per D-10)
    - CONSUMER span carries 3 messaging semconv attrs with correct values: messaging.system=rabbitmq, messaging.destination.name=orders.created, messaging.operation.type=process; deprecated `messaging.operation` (bare) key absent
    - Both processes shut down cleanly within 12s of SIGTERM
    - No leftover spring-boot:run processes
  </acceptance_criteria>
  <verify>
    <automated>nohup mise run dev:producer &gt; /tmp/producer-02-05.log 2&gt;&amp;1 &amp; PID_P=$!; nohup mise run dev:consumer &gt; /tmp/consumer-02-05.log 2&gt;&amp;1 &amp; PID_C=$!; for i in $(seq 1 60); do grep -q "Started ProducerApplication" /tmp/producer-02-05.log &amp;&amp; grep -q "Started ConsumerApplication" /tmp/consumer-02-05.log &amp;&amp; break; sleep 2; done; grep -q "Started ProducerApplication" /tmp/producer-02-05.log &amp;&amp; grep -q "Started ConsumerApplication" /tmp/consumer-02-05.log &amp;&amp; ! grep -E 'NoSuchMethodError|unknown_service:java' /tmp/producer-02-05.log /tmp/consumer-02-05.log &amp;&amp; test "$(curl -s -o /tmp/order-resp.json -w '%{http_code}' -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}')" = "202" &amp;&amp; OID=$(python3 -c "import json; print(json.load(open('/tmp/order-resp.json'))['orderId'])") &amp;&amp; for i in $(seq 1 15); do grep -q "OrderCreated received: orderId=$OID" /tmp/consumer-02-05.log &amp;&amp; break; sleep 1; done &amp;&amp; grep -q "OrderCreated received: orderId=$OID" /tmp/consumer-02-05.log &amp;&amp; sleep 8 &amp;&amp; PT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-producer&amp;limit=5" | python3 -c "import json,sys; print(json.load(sys.stdin)['traces'][0]['traceID'])") &amp;&amp; CT=$(curl -s "http://localhost:3200/api/search?tags=service.name%3Dorder-consumer&amp;limit=5" | python3 -c "import json,sys; print(json.load(sys.stdin)['traces'][0]['traceID'])") &amp;&amp; test "$PT" != "$CT"; CODE=$?; kill $PID_P $PID_C 2&gt;/dev/null; for i in $(seq 1 12); do { kill -0 $PID_P 2&gt;/dev/null || kill -0 $PID_C 2&gt;/dev/null; } || break; sleep 1; done; exit $CODE</automated>
  </verify>
  <done>With both producer + consumer running, one POST /orders produces TWO DISTINCT TRACES in Tempo with DIFFERENT traceIds. Producer trace = 3 spans (SERVER POST /orders → INTERNAL OrderService.place → PRODUCER orders.created publish). Consumer trace = 2 spans (CONSUMER orders.created process → INTERNAL ProcessingService.process). Consumer's CONSUMER span has empty/zero parentSpanId (Context.root() honored per D-10). All 3 messaging semconv attrs present on CONSUMER span; deprecated key absent. Phase 2 success criteria #1, #4, partial #2 (graceful shutdown latency ok) met from the in-Tempo perspective; the README + tag for Plan 02-06 now have a green baseline to lock.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 02-05 — consumer-service business-code instrumentation)

| Boundary | Description |
|----------|-------------|
| RabbitMQ broker → OrderListener (existing) | AMQP message delivery from `orders.created` queue; the message payload is workshop-trusted (originates from our own producer in the same workshop network) |
| OrderListener → ProcessingService (existing) | In-process call; INTERNAL span wraps the work |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-05-01 | Information Disclosure | LOG.info("OrderCreated received: orderId={}", orderId) — orderId is a UUID; no PII concern, but the log line is now stamped with a CONSUMER span's trace_id (in Phase 5) | accept | UUID is opaque; future logging additions that include payload contents would need PII review. Phase 2 leaves the LOG.info exactly as Phase 1 wrote it. |
| T-2-05-02 | Information Disclosure | CONSUMER span attrs capture queue + operation_type — workshop-local strings with no PII | accept | Workshop scope; identical to producer's PRODUCER span attrs (T-2-04-02) |
| T-2-05-03 | Tampering | A malicious AMQP message body (untrusted producer) could throw on deserialization in the @RabbitListener method invocation BEFORE our CONSUMER span starts — span never recorded, error invisible | accept | Workshop scope: the only producer is our own producer-service in the same docker-compose network. Phase 6 testing will exercise the full flow with a real broker. Production deployments would add a Spring AMQP error handler before this entry point — out of scope for Phase 2. |
| T-2-05-04 | Information Disclosure | recordException(e) on a RuntimeException leaks stack trace into Tempo span events | accept | Workshop teaching value > leak risk. Phase 3's APP-04 actively exercises this path; Tempo's error display IS the lesson. |
| T-2-05-05 | Spoofing | Phase 2 CONSUMER span explicitly starts from Context.root() — accepts no upstream parent. A header-spoofed `traceparent` on the AMQP message would have no effect because we are NOT extracting | accept | This is the Phase 2 broken-then-fixed pedagogy. Phase 3's TracingMessageListenerAdvice extracts the header — that is when spoofing becomes a concern. (Phase-3-scope.) |
| T-2-05-06 | Repudiation | Without the inline catch, a swallowed exception in a future refactor could lose the failed-process observation | mitigate | D-03 catch shape MUST be present (asserted by T1 + T2); same control as T-2-04-06 |

**Plan scope:** Consumer-service business-code instrumentation. No new network surface. No new env vars, no new credentials, no new endpoints. Out of scope: header validation on inbound AMQP messages (Phase 3's PROP-02 introduces the extract path; sanitization is Phase 6's testing concern).
</threat_model>

<verification>
- `mvn -pl consumer-service compile` exits 0
- `mvn -pl consumer-service -DskipTests package` exits 0
- Both producer + consumer start cleanly within 60s with no OTel-related exceptions
- POST /orders returns 202; consumer LOG.info "OrderCreated received: orderId=..." appears within 15s
- Tempo shows TWO DISTINCT TRACES with DIFFERENT traceIds (broken-then-fixed-pedagogy state)
- Producer trace = 3 spans (SERVER + INTERNAL + PRODUCER) with the expected names + attrs (regression-checked from Plan 02-04)
- Consumer trace = 2 spans (CONSUMER + INTERNAL) with the expected names + 3 messaging semconv attrs on the CONSUMER span (system=rabbitmq, destination.name=orders.created, operation.type=process via value enum)
- Consumer's CONSUMER span has empty/zero parentSpanId (Context.root() honored per D-10)
- Both processes shut down cleanly within 12s of SIGTERM
- `mise run verify:bom` still exits 0 (Plan 02-01 invariant preserved)
</verification>

<success_criteria>
- TRACE-06 satisfied (consumer half): INTERNAL span wraps ProcessingService.process(...), named "ProcessingService.process" with SpanKind.INTERNAL.
- TRACE-08 satisfied: CONSUMER span wraps OrderListener.onOrder(...), named "orders.created process" with SpanKind.CONSUMER and 3 messaging semantic-convention attributes (system=rabbitmq, destination.name=orders.created, operation.type=process via value enum).
- D-01 honored on the consumer side: pure-inline templates at every span site.
- D-02 honored: Tracer constructor-injected in OrderListener and ProcessingService.
- D-03 honored: catch shape present in both new spans.
- D-10 honored: CONSUMER span explicitly `.setParent(Context.root())` AND the verbatim multi-line teaching comment is present above the spanBuilder call. The Tempo trace verifies the empty parentSpanId — proving Context.root() takes effect at runtime, not just in source.
- D-11 / RESEARCH FLAG #1 honored: uses MESSAGING_OPERATION_TYPE constant + MessagingOperationTypeIncubatingValues.PROCESS value enum, NOT the deprecated MESSAGING_OPERATION + literal "process".
- Phase 2 success criterion #1 met: TWO DISTINCT TRACES per POST /orders in Tempo with correct service.name labels and DIFFERENT traceIds.
- Phase 2 success criterion #4 met: producer trace = SERVER + INTERNAL + PRODUCER; consumer trace = CONSUMER + INTERNAL.
- All five Phase 2 span sites are now instrumented; Plan 02-06 can lock the README + tag step-02-traces against this green baseline.
</success_criteria>

<output>
After completion, create `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-SUMMARY.md` documenting:
- File tree of consumer-service/src/main/java (4 Java files; OrderListener + ProcessingService modified)
- Confirmed `mvn -pl consumer-service -DskipTests package` BUILD SUCCESS line
- Confirmed both apps start cleanly (paste both "Started" lines from the two log files)
- Confirmed POST /orders → 202 + consumer LOG.info "OrderCreated received: orderId=..."
- Confirmed PROD_TRACE_ID != CONS_TRACE_ID (paste both traceIds and confirm they differ)
- Confirmed producer trace shape (3 spans SERVER+INTERNAL+PRODUCER) — paste python3 OK output
- Confirmed consumer trace shape (2 spans CONSUMER+INTERNAL with intra-consumer parent chain) — paste python3 OK output
- Confirmed CONSUMER span has empty parentSpanId (Context.root() runtime-verified) — paste python3 OK output
- Confirmed CONSUMER span carries 3 correct messaging semconv attrs and deprecated key absent
- Confirmed graceful shutdown latency (paste seconds-to-exit for BOTH processes)
- Files modified: 2 (OrderListener.java + ProcessingService.java)
- Hand-off for Plan 02-06: ALL 5 Phase 2 span sites are now instrumented and verified green; ready for the README updates (DOC-03 callout for the heavy comments, DOC-05 callout for the per-service-duplication rationale) and the annotated tag `step-02-traces`.
- Hand-off for Phase 3: per CONTEXT.md D-09 (deferred-ideas), Phase 3 will REPLACE the inline CONSUMER span in OrderListener.onOrder with the TracingMessageListenerAdvice from otel-bootstrap. The Phase 2→Phase 3 git diff in OrderListener will show: − the .setParent(Context.root()) line; + .setParent(extracted) where extracted = openTelemetry.getPropagators().getTextMapPropagator().extract(Context.root(), messageProperties, getter). The structural shape of the rest of the method stays identical per D-10's load-bearing comment.
</output>
