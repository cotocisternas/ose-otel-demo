# Phase 3: AMQP Context Propagation - Context

**Gathered:** 2026-05-01
**Status:** Ready for research-phase, then planning

<domain>
## Phase Boundary

Phase 3 installs the producer-side `TracingMessagePostProcessor` (inject) + consumer-side `TracingMessageListenerAdvice` (extract) pair from the shared `otel-bootstrap` module, retires the inline PRODUCER span from `OrderPublisher.publish(...)` and the inline CONSUMER span from `OrderListener.onOrder(...)` (Phase 2 D-09 hand-off), and wires APP-04's deterministic 10% failure path so TRACE-09's `recordException` + `setStatus(StatusCode.ERROR)` shape produces the workshop's first error span — all so one `POST /orders` produces ONE distributed trace whose consumer-side spans share the producer's `traceId` and whose CONSUMER span's `parentSpanId` equals the producer's PRODUCER `spanId`. THIS IS THE WORKSHOP'S HEADLINE LESSON — the broken-then-fixed delta from Phase 2 is the single most powerful pedagogical moment in the artifact.

**In scope (Phase 3 delivers):**
- `otel-bootstrap` module populated with FOUR classes in `com.example.otel.amqp`: `TracingMessagePostProcessor`, `TracingMessageListenerAdvice`, `MessagePropertiesSetter` (`TextMapSetter<MessageProperties>`), `MessagePropertiesGetter` (`TextMapGetter<MessageProperties>`)
- Producer wiring: producer-service's `RabbitConfig` adds `@Bean TracingMessagePostProcessor` + an explicit `@Bean RabbitTemplate` that calls `.setBeforePublishPostProcessors(tracingMpp)` (replaces Spring Boot's auto-created RabbitTemplate)
- Consumer wiring: consumer-service's `RabbitConfig` adds `@Bean TracingMessageListenerAdvice` + an explicit `@Bean SimpleRabbitListenerContainerFactory` (Configurer-aided) with `.setAdviceChain(advice)` and `.setDefaultRequeueRejected(false)`
- DELETE Phase 2's inline PRODUCER span code from `OrderPublisher.publish(...)` (lines 51–83); the method becomes a thin `convertAndSend(...)` call; `Tracer` constructor parameter removed
- DELETE Phase 2's inline CONSUMER span code from `OrderListener.onOrder(...)`; the method becomes a 3-line pass-through (orderId extract → `LOG.info` → `processingService.process(...)`); `Tracer` constructor parameter removed
- APP-04 failure path: `ProcessingService.process(...)` adds an `AtomicInteger counter` field; `incrementAndGet()` per call; on `count % 10 == 0` throws a new `ProcessingFailedException extends RuntimeException` (in `com.example.consumer.domain`). Phase 2's D-03 catch shape (already in place) handles `recordException` + `setStatus(ERROR)` on the INTERNAL span; the advice's catch handles the same on the CONSUMER span.
- Both PRODUCER and CONSUMER spans use OTel messaging semconv-correct destination naming: span name `<exchange> publish` / `<exchange> process`, `messaging.destination.name = exchange`, `messaging.rabbitmq.destination.routing_key = routingKey`. **Visible delta from Phase 2** (Phase 2 used QUEUE name as destination — Phase 3 corrects this).
- README delta: adds the per-step "Step 3" section and a PROP-04 callout contrasting the **shared** propagation pair with the **per-service-duplicated** SDK bootstrap (DOC-05 already callouts the duplication side; Phase 3 callouts the shared side).
- Annotated git tag `step-03-context-propagation` on `main` (WORK-01 — same user-approved gate convention as Phase 2's `step-02-traces`).

**Out of scope (deferred to later phases):**
- Metrics signal — Phase 4 (`SdkMeterProvider` + 3 instrument shapes)
- Logs signal + MDC trace_id/span_id correlation — Phase 5 (`SdkLoggerProvider` + `OpenTelemetryAppender`)
- Testcontainers integration tests asserting the parent/child span relationship — Phase 6 (`InMemorySpanExporter` + `SimpleSpanProcessor`)
- Pre-built Grafana dashboard, scripts/load.sh, README walkthrough body, broken-vs-fixed Grafana screenshots (DOC-04) — Phase 7
- Custom span events / `app.failure.reason` attribute on the failure path — recordException is sufficient per TRACE-09; richer semantics are a Phase 7 polish opportunity
- DLX / dead-letter routing for failed messages — explicitly out of scope per PROJECT.md
- Producer-side failure path (APP-04 is consumer-side only per REQUIREMENTS.md)

</domain>

<decisions>
## Implementation Decisions

### Bootstrap module shape

- **D-01:** **`otel-bootstrap` classes are pure Java** with NO Spring annotations on the propagation classes themselves. Each service's `RabbitConfig.java` declares `@Bean` methods that `new` them up:
  ```java
  @Bean
  TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry o, Tracer tracer) {
      return new TracingMessagePostProcessor(o, tracer);
  }
  ```
  Matches Phase 2 D-12's no-autoconfigure / no-magic ethos. Preserves the deliberate PROP-04 asymmetry: SDK bootstrap = duplicated FILES (read twice), propagation pair = ONE class file (read once) + two visible `@Bean` wirings (read explicitly per service).

- **D-02:** **Four separate top-level classes** in `otel-bootstrap/src/main/java/com/example/otel/amqp/`:
  - `TracingMessagePostProcessor.java` — implements `org.springframework.amqp.core.MessagePostProcessor`; PRODUCER span + inject
  - `TracingMessageListenerAdvice.java` — implements `org.aopalliance.intercept.MethodInterceptor`; CONSUMER span + extract
  - `MessagePropertiesSetter.java` — implements `TextMapSetter<MessageProperties>`; writes header values as **String** (PITFALLS.md #2)
  - `MessagePropertiesGetter.java` — implements `TextMapGetter<MessageProperties>`; defensively normalizes via `.toString()` for any AMQP `LongString` / `byte[]` arrival
  Each class has a name attendees can refer to, its own JavaDoc explaining its specific role and the pitfall it neutralises, and its own unit-testable surface (the setter↔getter round-trip test mentioned in PITFALLS.md #2). Reinforces D-01 (Phase 2) "boilerplate IS the lesson" applied to the propagation layer.

- **D-03:** **Constructor signature** for `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` takes BOTH `OpenTelemetry openTelemetry` (for `getPropagators().getTextMapPropagator()`) AND a per-service `Tracer tracer` (for `spanBuilder(...)`). The per-service Tracer carries the scope identity already established in Phase 2 (`com.example.producer` / `com.example.consumer`), so PRODUCER spans created from inside `otel-bootstrap` still appear under each service's instrumentation scope in Tempo — no new `com.example.otel.amqp` scope is introduced. Diff is structural (where code lives), not semantic (what attributes appear on spans).

- **D-04:** **`otel-bootstrap` reuses Phase 2's already-wired propagators** via `openTelemetry.getPropagators().getTextMapPropagator()` — does NOT construct a new `W3CTraceContextPropagator.getInstance()`. Honors Phase 2 D-16 hand-off; ensures any future propagator change (baggage, custom) flows through one configuration point.

### Producer-side: `TracingMessagePostProcessor`

- **D-05:** **Post-processor OWNS the entire PRODUCER span lifecycle** (Phase 2 D-09 hand-off honored). Phase 2's inline PRODUCER span code in `OrderPublisher.publish(...)` (lines 51–83 of `producer-service/.../messaging/OrderPublisher.java`) is **DELETED** as part of Phase 3's plan. `OrderPublisher.publish` becomes a thin call:
  ```java
  public void publish(String orderId, Map<String,Object> payload) {
      Map<String,Object> message = new HashMap<>(payload);
      message.put("orderId", orderId);
      rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
  }
  ```
  `Tracer` constructor parameter is **removed** (no longer used). Smallest possible step-02→step-03 git diff per ROADMAP SC #5.

- **D-06:** **PRODUCER span lifetime is inject-only** — span tightly wraps the `propagator.inject(Context.current(), props, SETTER)` call inside the post-processor's `try/finally`. Span ends when post-processor returns, BEFORE `RabbitTemplate.send(...)` actually talks to the broker. Matches OTel auto-instrumentation convention for Kafka/JMS/AMQP (their PRODUCER spans don't wrap the wire-send either). Broker-level send errors (`AmqpException`, etc.) propagate up the call stack and are caught by `OrderService.place(...)`'s INTERNAL span via Phase 2's D-03 catch — error visibility is preserved at the right layer.

- **D-07:** **PRODUCER span uses semconv-correct destination naming** (changed from Phase 2):
  - span name: `"<exchange> publish"` → `"orders publish"` (Phase 2 was `"orders.created publish"` — used QUEUE)
  - `messaging.destination.name`: exchange (`"orders"`) — Phase 2 used queue (`"orders.created"`)
  - `messaging.rabbitmq.destination.routing_key`: routing key (`"order.created"`)
  - `messaging.system`: `"rabbitmq"`
  - `messaging.operation.type`: `SEND`
  This corrects an OTel semconv divergence in Phase 2's inline span. Visible across the step-02→step-03 diff (span name change in Tempo); the README PROP-04 callout can highlight this as "Phase 3 also corrects the destination name to match OTel messaging semconv."

### Consumer-side: `TracingMessageListenerAdvice` + listener factory

- **D-08:** **`SimpleRabbitListenerContainerFactory` is a Configurer-aided `@Bean`** in consumer's `RabbitConfig.java`:
  ```java
  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
          ConnectionFactory connectionFactory,
          SimpleRabbitListenerContainerFactoryConfigurer configurer,
          TracingMessageListenerAdvice tracingAdvice) {
      var factory = new SimpleRabbitListenerContainerFactory();
      configurer.configure(factory, connectionFactory);   // Spring Boot defaults + spring.rabbitmq.listener.simple.* properties
      factory.setAdviceChain(tracingAdvice);              // Phase 3's tracing
      factory.setDefaultRequeueRejected(false);           // APP-04 safety
      return factory;
  }
  ```
  Spring Boot's `RabbitAutoConfiguration` backs off when a user-defined `SimpleRabbitListenerContainerFactory` bean is present. The Configurer-aided shape is future-proof: any later `spring.rabbitmq.listener.simple.concurrency=N` etc. in `application.yml` will still apply because the Configurer ran first.

- **D-09:** **`OrderListener.onOrder(...)` is simplified to a thin pass-through** (Phase 2 D-09 hand-off honored — the consumer-side counterpart to D-05). DELETE the inline CONSUMER span / try / catch / finally body. DELETE the `Tracer` field and constructor parameter. Final body:
  ```java
  @RabbitListener(queues = RabbitConfig.QUEUE)
  public void onOrder(Map<String,Object> message) {
      Object orderId = message.get("orderId");
      LOG.info("OrderCreated received: orderId={}", orderId);
      processingService.process(message);
  }
  ```
  `LOG.info(...)` runs **inside** the CONSUMER span's `Scope` (because the advice's `makeCurrent()` is active when `inv.proceed()` calls `onOrder`), so Phase 5's MDC trace_id/span_id injection will pick this log line up automatically.

- **D-10:** **`TracingMessageListenerAdvice`'s CONSUMER span shape mirrors the producer-side** (symmetry IS the lesson per ROADMAP SC #4):
  - span name: `"<exchange> process"` → `"orders process"`
  - `.setParent(extracted)` — joins the producer's trace via the `traceparent` header
  - `setSpanKind(SpanKind.CONSUMER)`
  - same four `messaging.*` attributes as producer (system, destination.name = exchange, operation.type = `PROCESS`, rabbitmq.destination.routing_key)
  - `catch (Throwable t)` records exception + sets ERROR status + rethrows so Spring AMQP container NACKs and (per D-11 below) drops

### APP-04 / TRACE-09 failure path

- **D-11:** **Trigger is in-memory `AtomicInteger` counter on `ProcessingService`** (Spring singleton):
  ```java
  private final AtomicInteger counter = new AtomicInteger();
  // inside process(...):
  int n = counter.incrementAndGet();
  if (n % 10 == 0) {
      throw new ProcessingFailedException(
          "Deterministic failure on order #" + n + " (every 10th order)");
  }
  ```
  Matches APP-04's verbatim wording ("every 10th order"). Counter resets per JVM start — fine for fresh demo sessions; each `mise run dev` is a clean run. The failure throw site sits INSIDE Phase 2's existing D-03 catch shape, so `span.recordException(e)` + `span.setStatus(StatusCode.ERROR)` + `throw e` already wrap it without restructuring.

- **D-12:** **Custom `ProcessingFailedException extends RuntimeException`** in `com.example.consumer.domain` (new file). Constructor takes `String message`. Pedagogical value: the class name appears as `exception.type` in Tempo's span detail (`com.example.consumer.domain.ProcessingFailedException`) — the name itself is documentation. Clean separation of concerns: domain layer throws domain exception; the listener container's NACK/requeue decision is configured separately on the factory (D-08).

- **D-13:** **`SimpleRabbitListenerContainerFactory.setDefaultRequeueRejected(false)`** drops failed messages instead of requeuing them. Without this, Spring AMQP's default behavior would NACK-requeue the failed message infinitely, creating a CPU hog and Tempo error-span spam. With it, the demo flow is: order 10 fails → CONSUMER span + INTERNAL span both ERROR + exception event in Tempo → message dropped → orders 11–19 succeed → order 20 fails. Clean. (No DLX per PROJECT.md; failed messages just go to /dev/null.)

### Claude's Discretion

These are NOT user-locked — the planner can pick reasonable defaults during planning:

- **`TracingMessagePostProcessor` catch shape:** `try/finally` is sufficient; W3C `propagator.inject(...)` is essentially infallible (it just calls `Setter.set(carrier, key, value)` which writes to a HashMap-backed `MessageProperties`). No defensive `catch (Throwable t)` needed unless the Setter throws (which it won't for the chosen `String` value type). Keep the post-processor body tight.
- **Defensive `arg-extraction` in `TracingMessageListenerAdvice`:** the `MethodInterceptor.invoke(MethodInvocation inv)` reads `inv.getArguments()[?]` to find the `Message`. Spring AMQP's listener invocation passes the Message in `MessageListener.onMessage(Message)` (arg[0]) for AUTO-ack listeners. Research-flag confirms the exact index — implement with a typed scan over `inv.getArguments()` if defensiveness is preferred (find the first `Message`-typed arg).
- **Span attribute extras** beyond the four-attribute messaging semconv core (e.g., `messaging.message.id`, `messaging.message.body.size`, `messaging.consumer.group.name = consumerTag`): out of scope unless they add pedagogical value. Stick to the core four for symmetry with producer.
- **`addEvent` calls** to mark the deterministic failure with `app.failure.reason = "deterministic-10-percent"` or similar: `recordException` is sufficient per TRACE-09; richer semantics are a Phase 7 polish opportunity if user requests.
- **`LOG.error` on the failure path** in `ProcessingService` or `OrderListener`: optional. If added, Phase 5's MDC injector will correlate the log line to the trace; useful demo material but not required.
- **`otel-bootstrap` pom.xml dependency scope:** `provided` for `spring-amqp` and `spring-aop` / `aopalliance` (the consuming services bring them via `spring-boot-starter-amqp`); `compile` for `opentelemetry-api`.
- **Phase 3 README delta scope and exact wording for the PROP-04 callout** (per ROADMAP SC #4 "README explicitly contrasts this with the per-service-duplicated SDK bootstrap"): match Phase 1 / 2 README delta size; one paragraph noting the deliberate asymmetry, one paragraph noting the destination-name semconv correction, one block listing the four new files.
- **Wave / plan structure**: planner's call. Likely 5 plans:
  - Wave 1 (parallelizable): `03-01-otel-bootstrap-amqp-classes` (the four propagation classes + their unit test for setter↔getter round-trip)
  - Wave 2 (blocked on Wave 1; parallelizable across services): `03-02-producer-wiring` (delete inline PRODUCER span; add `@Bean` propagation post-processor + explicit `@Bean RabbitTemplate`); `03-03-consumer-wiring` (delete inline CONSUMER span; add `@Bean` advice + Configurer-aided `@Bean` factory)
  - Wave 3 (blocked on Wave 2; consumer-only): `03-04-app-04-failure-path` (add `ProcessingFailedException` + counter + throw site in `ProcessingService.process`)
  - Wave 4 (blocked on Waves 1+2+3): `03-05-readme-and-exit-gate` (Step 3 README section + PROP-04 callout + annotated tag `step-03-context-propagation` at user-approved gate)
- **Test smoke gate**: defer all to Phase 6 per Phase 2 precedent. Optional `mise run verify:propagation` task could query Tempo for a single trace with the right span topology, but skip unless user requests.

### Research Flags (resolve in `/gsd-research-phase` before planning)

These are open questions about the Spring AMQP API surface that need confirmation against Spring AMQP 3.2.8 before the planner commits to method signatures:

- **RESEARCH FLAG #1** (carried from `SUMMARY.md` "Phase 3"): Verify `MethodInterceptor` advice on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` composes correctly with Spring AMQP 3.2.8's `@RabbitListener` invocation — specifically that the advice runs with `Message` available in `MethodInvocation.getArguments()` and that `inv.proceed()` triggers the actual `MessageListener.onMessage(...)` call. Confirm no thread-context loss between advice and `@RabbitListener` body execution (PITFALLS.md #7).
- **RESEARCH FLAG #2** (new from D-07): Confirm Spring AMQP 3.2.8 supports the `MessagePostProcessor.postProcessMessage(Message message, Correlation correlation, String exchange, String routingKey)` 4-arg overload — and that `RabbitTemplate.processBeforePublishMessageProcessors(...)` invokes the 4-arg overload when registered via `setBeforePublishPostProcessors(...)`. Source-confirm in `org.springframework.amqp:spring-rabbit:3.2.8`. If the 4-arg overload is NOT invoked, fall back to the 1-arg overload + a different way to derive the destination (e.g., reading from a header the producer set, or using a `ProducerCallback` instead of `MessagePostProcessor`).
- **RESEARCH FLAG #3** (new from D-10): Confirm the `MethodInvocation.getArguments()` index that contains the `Message` parameter when wrapping a `@RabbitListener` listener via `SimpleRabbitListenerContainerFactory.setAdviceChain(...)`. ARCHITECTURE.md Pattern 2 example uses index `[1]` with comment "(channel, message)" — but that may be for `ChannelAwareMessageListener`, while AUTO-ack `@RabbitListener` likely uses `MessageListener.onMessage(Message)` with index `[0]`. Source-confirm or use a typed scan.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents (researcher, planner) MUST read these before acting.**

### Project-level (read first)
- `.planning/PROJECT.md` — pinned tech stack, out-of-scope items (no Java agent, no Micrometer bridge, no DLX, no shared SDK library), Key Decisions table
- `.planning/REQUIREMENTS.md` §PROP-01..PROP-04 + APP-04 + TRACE-09 — Phase 3's user-testable acceptance criteria
- `.planning/ROADMAP.md` §"Phase 3: AMQP Context Propagation — THE HEADLINE LESSON" — goal, dependencies, 5 success criteria, notes
- `.planning/STATE.md` — current phase status, accumulated decisions, blockers/concerns (Phase 3 research flag listed)

### Prior phase hand-off (load-bearing)
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` — **D-09 hand-off** mandating deletion of inline PRODUCER + CONSUMER spans; **D-16 hand-off** mandating reuse of already-wired propagators; **D-03** catch shape forward-compat that Phase 3 fills with its throw site
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-VERIFICATION.md` — Phase 2 SHIPPED 2026-05-01 baseline against which Phase 3's diff is measured
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-producer-instrumentation-PLAN.md` — exact Phase 2 PRODUCER-span shape that Phase 3 replaces
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-consumer-instrumentation-PLAN.md` — exact Phase 2 CONSUMER-span shape that Phase 3 replaces

### Research synthesis (read for rationale and pitfall avoidance)
- `.planning/research/SUMMARY.md` §"Phase 3: AMQP Context Propagation (`step-03-context-propagation`) — THE HEADLINE LESSON" + §"Critical Pitfalls" — research-distilled approach; Phase 3 research flag #1
- `.planning/research/PITFALLS.md` Pitfall #1 (AMQP propagation broken — THE pitfall this phase exists to defeat), Pitfall #2 (byte[] vs String headers — drives D-02's `MessagePropertiesSetter` design), Pitfall #7 (`@RabbitListener` thread context loss — drives the advice-based extraction over inline-in-listener), Pitfall #12 (Jackson converter quirks — drives the `MessagePostProcessor`-via-setBeforePublishPostProcessors hook over a `MessageConverter` replacement)
- `.planning/research/ARCHITECTURE.md` Pattern 2 (AMQP propagation via TextMap*) — code shape reference for `TracingMessagePostProcessor` + `TracingMessageListenerAdvice`
- `.planning/research/STACK.md` — exact versions: OpenTelemetry SDK 1.61.0 (`opentelemetry-bom`), Spring AMQP 3.2.8 (`spring-boot-dependencies` 3.4.13 BOM-managed), `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` (stable) + `io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha` (messaging.* attribute keys live here)
- `.planning/research/FEATURES.md` TS-8 (PRODUCER span + inject), TS-9 (CONSUMER span + extract), TS-21 (broken-then-fixed pedagogy), AF-15 (anti-feature: shared SDK library — forbidden; the propagation pair being shared is NOT the same thing because it's about the messaging boundary, not the SDK setup)

### External standards (read when implementing)
- W3C Trace Context spec — `traceparent` header format (`00-<32-hex-traceid>-<16-hex-spanid>-<flag>`), `tracestate` header format
- OpenTelemetry Java SDK 1.61.0 docs — `ContextPropagators`, `TextMapPropagator.inject/extract`, `TextMapSetter<C>`, `TextMapGetter<C>`, `Span.recordException(Throwable)`, `Span.setStatus(StatusCode.ERROR)`
- OpenTelemetry Messaging Semantic Conventions (RabbitMQ profile) — `messaging.system`, `messaging.destination.name` (= exchange for RabbitMQ producer), `messaging.operation.type` (`SEND` / `PROCESS` / `RECEIVE`), `messaging.rabbitmq.destination.routing_key`. The 1.40.0-alpha incubating jar is where these constants live.
- Spring AMQP 3.2.x reference docs — `MessagePostProcessor`, `RabbitTemplate.setBeforePublishPostProcessors(...)`, `SimpleRabbitListenerContainerFactory.setAdviceChain(...)`, `setDefaultRequeueRejected(boolean)`, `SimpleRabbitListenerContainerFactoryConfigurer.configure(factory, connectionFactory)`, `RabbitListenerContainerFactory` API
- Spring AOP / aopalliance — `org.aopalliance.intercept.MethodInterceptor`, `MethodInvocation.proceed()`, `MethodInvocation.getArguments()`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (built in Phase 1 / Phase 2)
- **`producer-service/.../config/OtelSdkConfiguration.java`** — already wires composite `W3CTraceContextPropagator + W3CBaggagePropagator` via `setPropagators(...)`. Phase 3's post-processor reads this via `openTelemetry.getPropagators().getTextMapPropagator()`. NO change to this file in Phase 3.
- **`producer-service/.../messaging/OrderPublisher.java`** — Phase 3 **DELETES** lines 51–83 (the entire inline PRODUCER span/try/catch/finally body) and **REMOVES** the `Tracer` constructor parameter. Final shape: 3-line `publish(...)` body that just builds the message and calls `convertAndSend(...)`.
- **`producer-service/.../config/RabbitConfig.java`** — Phase 3 **ADDS**: `@Bean TracingMessagePostProcessor` (depends on `OpenTelemetry`, `Tracer`); explicit `@Bean RabbitTemplate(ConnectionFactory, MessageConverter, TracingMessagePostProcessor)` that calls `setBeforePublishPostProcessors(mpp)`. Replaces Spring Boot's auto-created RabbitTemplate. Existing `EXCHANGE/QUEUE/ROUTING_KEY` constants and `DirectExchange/Queue/Binding/MessageConverter` beans stay unchanged.
- **`producer-service/.../domain/OrderService.java`** — NO change. The INTERNAL span's D-03 catch shape stays exactly as Phase 2 left it; broker-level send errors propagate up to it from the now-thin `OrderPublisher.publish(...)`.
- **`consumer-service/.../config/OtelSdkConfiguration.java`** — same as producer-side; propagators already wired; Phase 3 unused beyond the `getPropagators().getTextMapPropagator()` read inside the advice.
- **`consumer-service/.../messaging/OrderListener.java`** — Phase 3 **DELETES** the inline CONSUMER span/try/catch/finally body and **REMOVES** the `Tracer` constructor parameter. Final body is 3 lines: `orderId` extract, `LOG.info`, `processingService.process(message)`. The `@RabbitListener(queues = RabbitConfig.QUEUE)` annotation stays.
- **`consumer-service/.../config/RabbitConfig.java`** — Phase 3 **ADDS**: `@Bean TracingMessageListenerAdvice` (depends on `OpenTelemetry`, `Tracer`); `@Bean SimpleRabbitListenerContainerFactory` (Configurer-aided shape per D-08). Existing `Queue` + `MessageConverter` beans stay unchanged.
- **`consumer-service/.../domain/ProcessingService.java`** — Phase 3 **ADDS** an `AtomicInteger counter` field (instance field; Spring singleton); inside the existing D-03 try-block (which currently has the empty placeholder), adds `incrementAndGet()` + `if (count % 10 == 0) throw new ProcessingFailedException(...)`. The D-03 catch already does the right thing — no restructuring of the catch needed.
- **`consumer-service/.../domain/ProcessingFailedException.java`** — **NEW FILE.** Package-private (or public — Claude's discretion); extends `RuntimeException`; constructor takes `String message`.
- **`otel-bootstrap/src/main/java/com/example/otel/`** — Phase 3 **POPULATES** the empty placeholder. Adds 4 new files in `com/example/otel/amqp/`: `TracingMessagePostProcessor.java`, `TracingMessageListenerAdvice.java`, `MessagePropertiesSetter.java`, `MessagePropertiesGetter.java`. The existing `package-info.java` placeholder gets updated (or left as-is if its forward-looking comment is now accurate).
- **`otel-bootstrap/pom.xml`** — Phase 3 **ADDS** dependencies: `io.opentelemetry:opentelemetry-api` (compile), `org.springframework.amqp:spring-rabbit` (provided), `org.springframework:spring-aop` (provided) for `MethodInterceptor` from `aopalliance`. The Spring Boot BOM (already imported via parent) manages versions.

### Established Patterns (from Phase 2 — preserved by Phase 3)
- **D-01 pure-inline span template** (Phase 2): preserved for the new spans inside `otel-bootstrap` classes — the `try (Scope s = span.makeCurrent()) { ... } finally { span.end(); }` pattern reads identically inside `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` to how it reads in Phase 2's now-deleted inline spans. The lesson moves from inline-in-business-code (Phase 2) to inline-in-shared-library (Phase 3); the SHAPE of the code is the same.
- **D-04 span naming convention** (Phase 2): "`<destination> <operation>`" for messaging. Phase 3 corrects the destination interpretation: was QUEUE in Phase 2, becomes EXCHANGE in Phase 3 (semconv-correct).
- **D-12 no-autoconfigure ethos** (Phase 2): preserved — no `opentelemetry-sdk-extension-autoconfigure`, no `@AutoConfiguration`, no `META-INF/spring/.../AutoConfiguration.imports`. All wiring is explicit `@Bean` methods.
- **D-13 Resource attributes** (Phase 2): unchanged. Phase 3 doesn't touch `OtelSdkConfiguration`.
- **D-15 `@Bean(destroyMethod = "close")`** (Phase 2): unchanged. Phase 3's new spans flush via the same shutdown cascade.
- **D-16 propagators wired** (Phase 2): reused via `openTelemetry.getPropagators().getTextMapPropagator()`. Phase 3 does NOT construct new propagators.
- **POM BOM order** (Phase 1): unchanged. `otel-bootstrap`'s new dependencies are BOM-managed (no `<version>` tags).
- **`mise.toml`**: no new tasks needed unless we add a `verify:propagation` smoke (Claude's discretion).

### Integration Points (where Phase 3 plugs into existing code)
- **`otel-bootstrap` becomes a real Maven dependency** of producer-service and consumer-service — both service `pom.xml` files add `<dependency><groupId>com.example</groupId><artifactId>otel-bootstrap</artifactId><version>${project.version}</version></dependency>`. Currently (post-Phase 2) the dependency line may already exist as a placeholder per Phase 1 scaffolding — confirm and reuse if present.
- **Producer's `RabbitTemplate` becomes user-defined** — Spring Boot's `RabbitAutoConfiguration` backs off. Producer's existing wiring (which uses the auto-created RabbitTemplate via `OrderPublisher`'s constructor injection) keeps working because Spring picks the user-defined bean automatically.
- **Consumer's `SimpleRabbitListenerContainerFactory` becomes user-defined** — same backoff; existing `@RabbitListener(queues = RabbitConfig.QUEUE)` annotations on `OrderListener.onOrder` automatically use the user-defined factory (Spring AMQP picks it by name `rabbitListenerContainerFactory`, which is the bean method name).
- **`ProcessingService.process(...)`** gets a counter field + throw site INSIDE the existing D-03 try-block. The catch + recordException + setStatus(ERROR) shape is already in place; Phase 3 just provides the throw.
- **`OrderListener.onOrder`'s exception flow** in Phase 3: `processingService.process(...)` throws `ProcessingFailedException` → propagates up through onOrder (no catch) → caught by `TracingMessageListenerAdvice.invoke`'s `catch (Throwable t)` → recorded on CONSUMER span + setStatus(ERROR) + rethrow → caught by Spring AMQP container → message NACK'd → defaultRequeueRejected=false → message dropped. **Both** INTERNAL and CONSUMER spans show ERROR status in Tempo.

</code_context>

<specifics>
## Specific Ideas

- The **`step-02-traces` → `step-03-context-propagation` git diff is LOAD-BEARING** per ROADMAP SC #5 ("git diff step-02-traces..step-03-context-propagation shows a small, readable changeset focused on the propagation pair — the broken-vs-fixed delta is reviewable in one diff"). Every decision above is optimized for that diff being **small and reviewable in one viewing**. Concretely, the diff should show:
  - **+** 4 new classes in `otel-bootstrap/.../amqp/`
  - **+** 1 new class in `consumer-service/.../domain/` (`ProcessingFailedException.java`)
  - **+** ~6 lines in producer's `RabbitConfig` (one `@Bean TracingMessagePostProcessor` + redefined `@Bean RabbitTemplate`)
  - **+** ~6 lines in consumer's `RabbitConfig` (one `@Bean TracingMessageListenerAdvice` + Configurer-aided `@Bean SimpleRabbitListenerContainerFactory`)
  - **+** ~5 lines in `ProcessingService.process(...)` (counter field + throw site)
  - **−** ~33 lines from `OrderPublisher.publish(...)` (inline PRODUCER span body)
  - **−** ~27 lines from `OrderListener.onOrder(...)` (inline CONSUMER span body)
  - **+** README delta (Step 3 section + PROP-04 callout)
  - **+** `pom.xml` deps for `otel-bootstrap` module (4 lines)
  - Net: ~50 new lines + ~60 deleted lines + 5 new files + README delta. The deletion-is-the-diff property makes this **reviewable in one viewing**.

- The **PROP-04 README callout** is the load-bearing pedagogical artifact for the asymmetry. Phase 2's README has a DOC-05 callout explaining "why is `OtelSdkConfiguration.java` duplicated?" Phase 3's README needs a parallel "why is the propagation pair shared?" callout. The two callouts together teach the deliberate boundary: **per-service code = duplicated (read twice); cross-service code = shared (read once, applied symmetrically)**.

- The **Phase 3 destination-name correction** (queue → exchange) is a visible behavioral delta in Tempo across the step-02/step-03 tags. This can be presented in the README as "Phase 3 also corrects the Phase 2 destination-name choice to match OTel messaging semconv (`messaging.destination.name = exchange` for AMQP producers)." A natural side-quest of the headline propagation lesson; not the headline itself.

- The **APP-04 + TRACE-09 pairing** (deterministic 10% failure → recordException + setStatus(ERROR)) is intentionally landed in Phase 3 (per ROADMAP) because the FIRST time attendees see business logic failing should ALSO be the FIRST time they see the OTel error-span pattern — the pairing teaches both concepts together rather than splitting them across phases. The error-status PROPAGATION across the AMQP hop (consumer-side error visible in the same trace as the producer-side success) is itself a teaching moment per ROADMAP SC #3.

- The **CONSUMER span's `setParent(extracted)`** is the SINGLE LINE that joins the producer's trace and the consumer's trace into ONE distributed trace. ROADMAP SC #1 ("consumer span's `parentSpanId` matching the producer span's `spanId`") depends entirely on this line. README PROP-04 callout should highlight it as the most important line in `TracingMessageListenerAdvice`.

</specifics>

<deferred>
## Deferred Ideas

### Phase 4 hand-off
- Adding `SdkMeterProvider` to each service's `OtelSdkConfiguration` — same env-var pattern as Phase 2 (D-12). The metric `messaging.client.consumed.messages` (or similar) could later wrap the CONSUMER span side; that's a Phase 4 decision.

### Phase 5 hand-off
- The `LOG.info("OrderCreated received: orderId={}", orderId)` line in the new thin `OrderListener.onOrder` runs INSIDE the CONSUMER span's `Scope` (the advice's `makeCurrent()` is active). Phase 5's MDC injector will pick this up automatically. NO change to `OrderListener` required in Phase 5 for this log line to gain `trace_id`/`span_id` correlation.

### Phase 6 hand-off
- Phase 6's Testcontainers-backed `@SpringBootTest` will assert Phase 3's outcome: `traceId(producer) == traceId(consumer)`, `consumer.parentSpanId == producer.spanId`, `SpanKind.PRODUCER`/`SpanKind.CONSUMER` correct, semconv `messaging.*` attributes present. Phase 3 ships **no test code itself** (Phase 2 precedent); the `InMemorySpanExporter` + `SimpleSpanProcessor` + `@TestConfiguration` swap arrives in Phase 6.
- The `MessagePropertiesSetter` ↔ `MessagePropertiesGetter` round-trip unit test mentioned in PITFALLS.md #2 could ship in Phase 3 (no Testcontainers needed — pure unit test of the byte[] vs String pitfall) OR be deferred to Phase 6. Claude's discretion.

### Phase 7 hand-off
- DOC-04 (broken-vs-fixed Grafana screenshots side-by-side) needs Phase 3's ONE-trace state captured at the `step-03-context-propagation` tag. Workshop facilitator should grab the screenshot from Tempo at this tag.
- The PROP-04 callout authored in Phase 3's README delta gets revisited in Phase 7's full README walkthrough; word the callout in Phase 3 such that it can be either kept verbatim or expanded by Phase 7 without restructuring.

### Roadmap backlog (out of phase scope)
- Span events + custom `app.failure.reason` attribute on the failure path — recordException is sufficient for Phase 3; richer semantics could be a Phase 7 polish item if the user requests.
- `LOG.error(...)` on the failure path — Phase 5's logs lesson can preview this if the user wants the failure visible in Loki without waiting for the trace correlation.
- A dedicated `verify:propagation` smoke task — defer to Phase 6's full Testcontainers test per Phase 2 precedent.
- Dead-letter exchange / retry instrumentation for the failed messages — explicitly deferred to v2 (FAIL-01 in REQUIREMENTS.md §v2 Requirements).
- Producer-side failure path (e.g., RabbitMQ broker-down scenario) — not in APP-04 scope; could be a Phase 7 polish item to demonstrate OrderService.place's INTERNAL span catching `AmqpException` from the broker.

</deferred>

---

*Phase: 3-AMQP Context Propagation*
*Context gathered: 2026-05-01*
