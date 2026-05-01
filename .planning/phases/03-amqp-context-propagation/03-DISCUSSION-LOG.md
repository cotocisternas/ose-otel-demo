# Phase 3: AMQP Context Propagation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `03-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-01
**Phase:** 3-AMQP Context Propagation
**Areas discussed:** Bootstrap wiring shape, Producer span ownership, APP-04 failure design, Listener factory wiring
**Mode:** default (interactive, single-question turns; no `--text`/`--batch`/`--analyze`/`--auto` overlays)

---

## Bootstrap wiring shape

### Q1.1 — How should otel-bootstrap expose the propagation pair to producer-service and consumer-service?

| Option | Description | Selected |
|--------|-------------|----------|
| Plain classes + per-service @Bean | otel-bootstrap stays pure-library; each service's RabbitConfig declares @Bean methods. Most explicit; matches Phase 2's no-magic ethos (D-12); preserves PROP-04 asymmetry. | ✓ |
| @Component beans + @ComponentScan extension | Propagation classes annotated with @Component; each service adds @ComponentScan(basePackages = {"com.example.{producer\|consumer}", "com.example.otel"}) to ProducerApplication/ConsumerApplication. Less wiring per service but adds an annotation attendees have to notice. | |
| @AutoConfiguration in otel-bootstrap | otel-bootstrap ships an @AutoConfiguration class registered via META-INF/spring/.../AutoConfiguration.imports. Beans appear by virtue of the dependency on classpath. Same machinery the rejected opentelemetry-spring-boot-starter (AF-15) uses. | |

**User's choice:** Plain classes + per-service @Bean wiring (recommended option).
**Notes:** Preserves PROP-04 asymmetry — SDK bootstrap = duplicated FILES (read twice in Phase 2); propagation pair = ONE class file (read once) + two visible @Bean wirings (read explicitly per service). Aligns with Phase 2 D-12 no-autoconfigure ethos.

---

### Q1.2 — Should the TextMapSetter / TextMapGetter live as separate top-level classes in otel-bootstrap, or as private static fields inside TracingMessagePostProcessor / TracingMessageListenerAdvice?

| Option | Description | Selected |
|--------|-------------|----------|
| Separate classes | 4 files in otel-bootstrap/.../amqp/. Each setter/getter has a name attendees can refer to, JavaDoc explaining the byte[] vs String pitfall (PITFALLS #2), and a unit-testable surface. | ✓ |
| Inline static fields | 2 files instead of 4. Symmetry of inject/extract methods denser side-by-side. Less ceremony but also less attendee-facing surface for the byte[] vs String pitfall. | |

**User's choice:** Separate classes (recommended option).
**Notes:** Reinforces D-01 (Phase 2) "boilerplate IS the lesson" applied to the propagation layer. Each setter/getter file becomes a referenceable artifact in the workshop ("the MessagePropertiesSetter is where PITFALLS #2 lives").

---

### Q1.3 — How should TracingMessagePostProcessor get registered on RabbitTemplate?

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit RabbitTemplate @Bean | Producer's RabbitConfig defines @Bean RabbitTemplate(ConnectionFactory, MessageConverter, TracingMessagePostProcessor) calling .setBeforePublishPostProcessors(mpp). Replaces Spring Boot's auto-created RabbitTemplate. | ✓ |
| RabbitTemplateCustomizer bean | Producer's RabbitConfig only adds @Bean TracingMessagePostProcessor + @Bean RabbitTemplateCustomizer that mutates the auto-created RabbitTemplate. Less code but introduces RabbitTemplateCustomizer abstraction. | |

**User's choice:** Explicit RabbitTemplate @Bean (recommended option).
**Notes:** Wiring is right in front of attendees in one file. Spring Boot's RabbitAutoConfiguration backs off when a user-defined RabbitTemplate bean exists. Matches the "explicit code" ethos; no Spring-Boot-magic abstraction in between.

---

### Q1.4 — What instrumentation scope should the spans created inside TracingMessagePostProcessor / TracingMessageListenerAdvice land under?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-service Tracer (com.example.{producer\|consumer}) | Constructor takes both OpenTelemetry (for propagator) AND per-service Tracer (for spans). PRODUCER span on producer side carries instrumentation.scope.name=com.example.producer. Matches Phase 2's per-service scope identity. | ✓ |
| Library scope (com.example.otel.amqp) | Propagation classes take only OpenTelemetry; internally call openTelemetry.getTracer("com.example.otel.amqp"). Semantically more correct per OTel's instrumentation-library convention but introduces a NEW scope visible in Tempo. | |

**User's choice:** Per-service Tracer (recommended option).
**Notes:** Diff from Phase 2 to Phase 3 is structural (where code lives), not semantic (what attribute keys appear on spans). Attendees keep the same mental model from Phase 2's per-service scope.

---

## Producer span ownership

### Q2.1 — Where should the PRODUCER span's lifecycle live in Phase 3?

| Option | Description | Selected |
|--------|-------------|----------|
| Post-processor owns it (Phase 2 D-09 hand-off) | TracingMessagePostProcessor.postProcessMessage() opens the PRODUCER span, injects traceparent, ends the span. OrderPublisher.publish() becomes a thin convertAndSend(...) call. Phase 2's inline span (lines 51–83) DELETED. Smallest possible step-02→step-03 diff. | ✓ |
| OrderPublisher keeps span; post-processor only injects | OrderPublisher.publish() KEEPS the inline PRODUCER span code unchanged. TracingMessagePostProcessor only does propagator.inject() — no span lifecycle. PRODUCER span code stays visible at the call site, but the diff is larger and consumer-side symmetry breaks. | |

**User's choice:** Post-processor owns it (recommended option).
**Notes:** Honors Phase 2 D-09 hand-off. ROADMAP SC #5 "small, readable changeset" depends on this. The diff is "+1 new class with the span body, −1 inline span body, +1 RabbitTemplate @Bean wiring line."

---

### Q2.2 — What lifetime should the PRODUCER span cover — just inject, or also wrap the wire-send?

| Option | Description | Selected |
|--------|-------------|----------|
| Inject-only; ends before wire-send | Post-processor's try/finally tightly wraps propagator.inject(). Span ends when post-processor returns. RabbitTemplate.send() happens AFTER, span already closed. Matches OTel auto-instrumentation convention for Kafka/JMS/AMQP. Broker errors propagate to OrderService.place's INTERNAL span catch (D-03). | ✓ |
| Span wraps the wire-send via RabbitTemplate.invoke() | OrderPublisher uses RabbitTemplate.invoke(callback) where the callback opens the span, calls operations.convertAndSend, ends span. PRODUCER span captures broker-level errors. Larger implementation footprint; defeats the deletion-is-the-diff goal. | |

**User's choice:** Inject-only; ends before wire-send (recommended option).
**Notes:** Matches OTel's own auto-instrumentation convention for messaging. Broker errors aren't lost — they propagate up to OrderService.place's INTERNAL span catch (Phase 2 D-03), which records them at the right semantic layer.

---

### Q2.3 — How should the PRODUCER span name its destination?

| Option | Description | Selected |
|--------|-------------|----------|
| Use exchange (semconv-correct) | destination.name = exchange ("orders"); rabbitmq.destination.routing_key = "order.created"; span name = "orders publish". Spec-aligned with OTel messaging semconv. Visible diff vs Phase 2: span name changes from "orders.created publish" to "orders publish". | ✓ |
| Use routing key (Phase-2-continuity-ish) | destination.name = routing key ("order.created"); span name = "order.created publish". Closer to Phase 2's queue convention. NOT semconv-correct. | |
| Pluggable span-name function | Constructor accepts a BiFunction<String,String,String> mapping (exchange, routingKey) -> name. Each service's RabbitConfig provides its own naming. Production-grade flexibility; adds complexity workshop attendees don't need. | |

**User's choice:** Use exchange (semconv-correct, recommended option).
**Notes:** Phase 3 also corrects Phase 2's queue-as-destination choice. RESEARCH FLAG: confirm Spring AMQP 3.2.8 supports the postProcessMessage(Message, Correlation, exchange, routingKey) overload — needed because the otel-bootstrap class is generic (can't hardcode "orders"). The destination-name correction can be presented in the README as a side-quest of the headline propagation lesson.

---

## APP-04 failure design

### Q3.1 — What triggers the deterministic 10% failure in ProcessingService.process(...)?

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory counter modulus | AtomicInteger field on ProcessingService (Spring singleton); incrementAndGet() each call; throw when count % 10 == 0. Matches APP-04's verbatim "every 10th order". Predictable demo. Counter resets per JVM start. | ✓ |
| OrderId hash modulus | Math.abs(orderId.hashCode()) % 10 == 0. Stateless; deterministic per-orderId; survives JVM restart. But OrderId is random UUID — attendees can't predict which order will fail. | |
| OrderId numeric suffix pattern | Inspect orderId for numeric suffix; throw on "...0". Requires APP-01 to send numeric orderIds (currently UUID). Would force re-shaping the producer side. | |

**User's choice:** In-memory counter modulus (recommended option).
**Notes:** Matches APP-04's "every 10th order" wording exactly. Counter resets per JVM start — fine because each `mise run dev` is a fresh demo session. Predictable demo: orders 1..9 succeed, order 10 fails (visible error in Tempo), orders 11..19 succeed, order 20 fails.

---

### Q3.2 — What exception type should ProcessingService throw when the 10th-order trigger fires?

| Option | Description | Selected |
|--------|-------------|----------|
| Custom ProcessingFailedException | New package-private class in com.example.consumer.domain; extends RuntimeException; constructor takes message. Class name appears as exception.type in Tempo's span detail — class name is documentation. | ✓ |
| Generic RuntimeException | `throw new RuntimeException("...")`. No new class. Tempo shows exception.type = java.lang.RuntimeException — less specific. | |
| AmqpRejectAndDontRequeueException | Spring AMQP recognizes this and rejects-without-requeueing regardless of factory config. Couples domain layer to messaging infrastructure — pedagogically muddies the lesson. | |

**User's choice:** Custom ProcessingFailedException (recommended option).
**Notes:** Domain layer throws domain exception. Tempo span detail will show `exception.type = com.example.consumer.domain.ProcessingFailedException` — the fully-qualified class name is itself documentation. Clean separation of concerns: messaging-side NACK/requeue decision lives on the listener factory (D-13), not in the domain code.

---

### Q3.3 — What should happen to the failed AMQP message after the ProcessingFailedException is thrown?

| Option | Description | Selected |
|--------|-------------|----------|
| setDefaultRequeueRejected(false) on the listener factory | One line on the SimpleRabbitListenerContainerFactory bean. NACK'd failed messages dropped — no DLX (PROJECT.md), no infinite redelivery loop. | ✓ |
| Leave defaultRequeueRejected at default true (infinite requeue) | Don't override default. Spring AMQP requeues failed messages; consumer immediately re-processes, fails, requeues. Infinite loop. Pedagogically dangerous: log spam, CPU hog. | |
| AmqpRejectAndDontRequeueException (alternative to factory config) | Switch the exception type to AmqpRejectAndDontRequeueException (contradicts Q3.2's choice) OR wrap at the listener boundary. Adds a layer for no semantic gain over factory config. | |

**User's choice:** setDefaultRequeueRejected(false) on the listener factory (recommended option).
**Notes:** Demo flow: order 10 fails → CONSUMER span + INTERNAL span both ERROR + exception event in Tempo → Spring AMQP NACKs → defaultRequeueRejected=false → message dropped → orders 11–19 succeed → order 20 fails. Clean. No DLX needed (PROJECT.md says no DLX); failed messages just go to /dev/null.

---

## Listener factory wiring

### Q4.1 — How should the SimpleRabbitListenerContainerFactory bean be constructed in consumer's RabbitConfig?

| Option | Description | Selected |
|--------|-------------|----------|
| Configurer-aided @Bean | Take SimpleRabbitListenerContainerFactoryConfigurer as a @Bean parameter; configurer.configure(factory, connectionFactory) FIRST (honors Spring Boot defaults + spring.rabbitmq.listener.simple.* properties), THEN setAdviceChain + setDefaultRequeueRejected(false). Future-proof. | ✓ |
| Vanilla @Bean | Construct factory entirely by hand: setConnectionFactory + setMessageConverter + setAdviceChain + setDefaultRequeueRejected. Demo's application.yml has no listener properties so functionally identical, but bypasses Configurer abstraction. | |

**User's choice:** Configurer-aided @Bean (recommended option).
**Notes:** Spring Boot's idiomatic pattern. Future-proof: if anyone later adds spring.rabbitmq.listener.simple.concurrency=N to consumer's application.yml, it'll still apply because Configurer ran first. Two-line difference vs vanilla; one extra parameter on the @Bean method.

---

### Q4.2 — What does OrderListener.onOrder become after Phase 3 deletes the inline CONSUMER span (per Phase 2 D-09)?

| Option | Description | Selected |
|--------|-------------|----------|
| Thin pass-through; Tracer constructor param removed | Delete the entire span/try/catch/finally body. Delete Tracer field and constructor parameter. Body is 3 lines: orderId extract, LOG.info, processingService.process. Cleanest delta. | ✓ |
| Keep Tracer + a Span.current() reference for symmetry | Delete inline span lifecycle but keep Tracer field; use Span.current() inside onOrder to add a custom domain attribute or event. More code surface; teaches "read current span" pattern but adds a concept Phase 3 doesn't need. | |

**User's choice:** Thin pass-through; Tracer constructor param removed (recommended option).
**Notes:** OrderListener becomes a 3-line method. CONSUMER span lives in TracingMessageListenerAdvice (otel-bootstrap), which makes the extracted context current BEFORE this method runs. ProcessingService.process(...) starts its INTERNAL span as a child of the CONSUMER span automatically via Context.current(). The LOG.info(...) line stays — runs INSIDE the CONSUMER span's Scope, so Phase 5's MDC trace_id/span_id injector picks it up automatically.

---

### Q4.3 — What span shape should TracingMessageListenerAdvice produce on the consumer side? (Symmetric to producer-side decisions.)

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror producer: exchange-named, semconv attributes | Span name = "<exchange> process" ("orders process"). Attributes: messaging.system=rabbitmq, messaging.destination.name=exchange, messaging.operation.type=PROCESS, messaging.rabbitmq.destination.routing_key=routingKey. .setParent(extractedContext) joins producer's trace. | ✓ |
| Phase-2-continuity: queue-named, identical attributes to Phase 2 | Span name = "<queue> process" ("orders.created process"). Same destination.name = queue. Diff vs Phase 2 minimal but breaks producer/consumer symmetry on naming. | |

**User's choice:** Mirror producer: exchange-named, semconv attributes (recommended option).
**Notes:** Producer/consumer symmetry per ROADMAP SC #4 "symmetry of one inject method matched by one extract method IS the lesson". Both spans have parallel naming and attribute structure. .setParent(extracted) is THE single line that joins the traces — README PROP-04 callout should highlight it.

---

## Claude's Discretion

These items were noted but not pinned to a specific user choice — the planner has flexibility:

- **TracingMessagePostProcessor catch shape** — try/finally is sufficient (W3C inject is essentially infallible); no defensive `catch (Throwable t)` needed
- **Defensive arg-extraction in TracingMessageListenerAdvice** — research-flag confirms whether `inv.getArguments()[0]` or `[1]` holds the Message; could implement a typed scan if defensiveness is preferred
- **Span attribute extras** beyond the four-attribute messaging semconv core (e.g., messaging.message.id, messaging.message.body.size, messaging.consumer.group.name = consumerTag) — out of scope unless they add pedagogical value
- **`addEvent` calls** to mark the deterministic failure with `app.failure.reason = "deterministic-10-percent"` — recordException is sufficient per TRACE-09; richer semantics are a Phase 7 polish opportunity if user requests
- **`LOG.error` on the failure path** — optional; if added, Phase 5's MDC injector will correlate the log line to the trace
- **otel-bootstrap pom.xml dependency scope** — likely `provided` for spring-amqp + spring-aop / aopalliance; `compile` for opentelemetry-api
- **Phase 3 README delta scope and exact wording for the PROP-04 callout** — match Phase 1 / 2 README delta size; one paragraph noting the deliberate asymmetry, one paragraph noting the destination-name correction, one block listing the four new files
- **Wave / plan structure** — planner's call (likely 5 plans across 4 waves; outlined in CONTEXT.md "Claude's Discretion" section)
- **Test smoke gate** — defer to Phase 6 per Phase 2 precedent; optional `mise run verify:propagation` task could query Tempo's API for a single trace with the right span topology

## Deferred Ideas

Captured to CONTEXT.md `<deferred>` section. Summary:

- **Phase 4 hand-off:** SdkMeterProvider env-var pattern; messaging client metrics
- **Phase 5 hand-off:** The new thin OrderListener.onOrder LOG.info line will pick up MDC trace_id/span_id automatically — no Phase 5 change needed for this specific log
- **Phase 6 hand-off:** Testcontainers test asserts traceId/parentSpanId/SpanKind/semconv attrs. Phase 3 ships zero test code per Phase 2 precedent. Optional: setter↔getter round-trip unit test could ship in Phase 3 (no Testcontainers needed)
- **Phase 7 hand-off:** DOC-04 broken-vs-fixed Grafana screenshots need Phase 3's ONE-trace state captured at the `step-03-context-propagation` tag
- **Roadmap backlog:** custom span events, LOG.error on failure path, dedicated propagation smoke task, DLX (FAIL-01 in REQUIREMENTS.md §v2), producer-side broker-down failure path

## Research Flags (for `/gsd-research-phase`)

- **#1** (carried from SUMMARY.md): MethodInterceptor advice on SimpleRabbitListenerContainerFactory composes correctly with Spring AMQP 3.2.8's listener lifecycle; no thread-context loss between advice and @RabbitListener body
- **#2** (new from D-07): Spring AMQP 3.2.8 supports the `postProcessMessage(Message, Correlation, exchange, routingKey)` 4-arg overload AND RabbitTemplate.processBeforePublishMessageProcessors invokes it
- **#3** (new from D-10): MethodInvocation.getArguments() index for Message arg when wrapping a @RabbitListener (arg[0] vs arg[1] vs typed scan)
