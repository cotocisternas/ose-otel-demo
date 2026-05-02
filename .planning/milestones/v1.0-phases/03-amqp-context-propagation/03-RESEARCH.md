# Phase 3: AMQP Context Propagation — Research

**Researched:** 2026-05-01
**Domain:** W3C trace-context propagation across the Spring AMQP boundary using OpenTelemetry Java SDK 1.61.0; producer-side `MessagePostProcessor` injection and consumer-side `MethodInterceptor` advice extraction
**Confidence:** HIGH

## Summary

This research resolves all three open Spring AMQP 3.2.8 API-surface flags from `03-CONTEXT.md`, confirms the OTel SDK 1.61.0 propagator/setter/getter contract, and verifies the semconv 1.40.0-alpha incubating constants used for the messaging attributes. **Every locked decision in CONTEXT.md (D-01 through D-13) is compatible with what Spring AMQP 3.2.8 source actually does** — no `## ⚠ DECISION CONFLICT` block is needed.

Three findings that the planner needs as load-bearing inputs:

1. **`MethodInvocation.getArguments()[1]` IS the `Message` parameter** (FLAG #3). The advice chain wraps `ContainerDelegate.invokeListener(Channel channel, Object data)` — so arg `[0]` is the `Channel` and arg `[1]` is the data, which is the `Message` for non-batch listeners. This MATCHES `ARCHITECTURE.md` Pattern 2's example. The `(channel, message)` comment is correct.
2. **`RabbitTemplate.doSend(...)` invokes the 4-arg `MessagePostProcessor.postProcessMessage(Message, Correlation, String exchange, String routingKey)` overload** (FLAG #2). The exchange/routingKey parameters reach the processor unchanged from `convertAndSend(exchange, routingKey, message)` (after null-safe defaulting). No fallback path needed.
3. **The advice chain runs synchronously, on the same thread, around the user's `@RabbitListener` method body** (FLAG #1). Proxy wraps `ContainerDelegate.invokeListener(Channel, Object)` → `actualInvokeListener(Channel, Object)` → `doInvokeListener(ChannelAwareMessageListener, Channel, Object)` → `MessagingMessageListenerAdapter.onMessage(Message, Channel)` → `invokeHandlerAndProcessResult(...)` → user method. All in the same call stack; no Reactor/Mono/async dispatch. `Scope.makeCurrent()` opened by the advice IS visible to the user method's body and to the `LOG.info(...)` line — Phase 5's MDC injector will find a non-default `trace_id`/`span_id` for that log line.

**Primary recommendation:** Plan exactly the shape CONTEXT.md describes. Use `inv.getArguments()[1]` with a defensive `instanceof Message` guard (cheap, future-proof against batch-listener composition). Use the 4-arg `postProcessMessage` overload signature. Wire `factory.setAdviceChain(tracingAdvice)` AFTER `configurer.configure(factory, connectionFactory)` — Spring Boot's default `spring.rabbitmq.listener.simple.retry.enabled=false` means Configurer never touches `adviceChain` itself, so user override is conflict-free.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **PROP-01** | `TracingMessagePostProcessor` registered on `RabbitTemplate.setBeforePublishPostProcessors(...)` injects W3C `traceparent`/`tracestate` headers into `MessageProperties` via `TextMapSetter<MessageProperties>` writing **String** values | Spring AMQP 3.2.8 source confirms `RabbitTemplate.doSend()` invokes `processor.postProcessMessage(messageToUse, correlationData, exch, rKey)` — the 4-arg overload. `MessageProperties.setHeader(String, Object)` stores into a `HashMap<String, Object>`; passing a `String` value preserves the round-trip (PITFALLS.md #2). `W3CTraceContextPropagator.inject` writes a `String` value via `setter.set(carrier, key, value)` — never `byte[]`. |
| **PROP-02** | `TracingMessageListenerAdvice` registered on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` extracts W3C context from `MessageProperties` via `TextMapGetter<MessageProperties>` and `Context.makeCurrent()` so the listener body executes inside the extracted span context | FLAG #1 + FLAG #3 resolved: the advice's `MethodInterceptor.invoke(MethodInvocation)` runs synchronously around `ContainerDelegate.invokeListener(Channel, Object)`. `getArguments()[1]` IS the `Message`. `Scope.makeCurrent()` is visible to the user `onOrder(...)` body because there is NO thread-switch between the advice's `proceed()` and `MessagingMessageListenerAdapter.onMessage(Message, Channel)`. |
| **PROP-03** | After step-03 the workshop attendee can `POST /orders` and see in Tempo a single trace whose `traceId` spans both services, with the consumer span's `parentSpanId` equal to the producer span's `spanId` | The OTel SDK 1.61.0 `Span.spanBuilder(...).setParent(extracted)` call uses the extracted W3C parent context. The producer's `propagator.inject(Context.current(), props, SETTER)` runs inside the PRODUCER span's `Scope` — so the spanId in the injected `traceparent` IS the PRODUCER span's spanId. The consumer's `propagator.extract(Context.current(), props, GETTER)` reads exactly that header. Verified by tracing the inject/extract flow against W3C spec format `00-<32-hex-traceid>-<16-hex-spanid>-<flag>`. |
| **PROP-04** | The shared `otel-bootstrap` module exports the propagation pair; README contrasts the **shared** propagation pair with the **per-service-duplicated** SDK bootstrap | This is a structural / pedagogical requirement — research confirms the four-class layout (D-02) compiles cleanly with `otel-bootstrap` declaring `provided` dependencies on `org.springframework.amqp:spring-rabbit` and `aopalliance` (transitive via `spring-aop`). |
| **APP-04** | Consumer's business logic fails deterministically on every 10th order so the workshop demonstrates the `recordException` + `setStatus(ERROR)` pattern | `AtomicInteger.incrementAndGet()` + `if (n % 10 == 0) throw new ProcessingFailedException(...)` — a Spring `@Service` is a singleton, so the counter persists across messages within one JVM run. The throw site lands inside Phase 2's existing D-03 catch shape, which already records `recordException` + `setStatus(ERROR)` on the INTERNAL span. The advice's catch records the same on the CONSUMER span. |
| **TRACE-09** | The deterministic 10% failure path calls `span.recordException(e)` and `span.setStatus(StatusCode.ERROR)` so Tempo shows the trace as `Error` status with the exception event attached | `Span.recordException(Throwable)` adds an `exception` span event with `exception.type`, `exception.message`, `exception.stacktrace` attributes. `Span.setStatus(StatusCode.ERROR)` sets the span's status code to ERROR. Tempo renders any span with status ERROR as red and any trace containing such a span as `Error`. The exception event is visible in the span detail panel. |

## User Constraints (from CONTEXT.md)

### Locked Decisions

The following 13 decisions from `03-CONTEXT.md` are user-locked. Research has confirmed each is compatible with Spring AMQP 3.2.8 + OTel SDK 1.61.0:

- **D-01:** Pure-Java propagation classes; per-service `@Bean` wiring with explicit `new` calls. **Research-confirmed compatible.**
- **D-02:** Four separate top-level classes in `com.example.otel.amqp` (`TracingMessagePostProcessor`, `TracingMessageListenerAdvice`, `MessagePropertiesSetter`, `MessagePropertiesGetter`). **Research-confirmed compatible.**
- **D-03:** Constructor signature for both top-level classes takes `(OpenTelemetry openTelemetry, Tracer tracer)`; `Tracer` is the per-service Tracer Phase 2 already exposes via `@Bean Tracer tracer(OpenTelemetry o) { return o.getTracer("com.example.producer"|"com.example.consumer"); }`. **Research-confirmed:** `Tracer.spanBuilder()` returns spans tagged with the per-service instrumentation scope, so spans created in `otel-bootstrap` code still appear under `com.example.producer` / `com.example.consumer` in Tempo. No new `com.example.otel.amqp` scope appears. ✓
- **D-04:** `otel-bootstrap` reads the propagator via `openTelemetry.getPropagators().getTextMapPropagator()`. **Research-confirmed:** Phase 2's producer + consumer `OtelSdkConfiguration.java` both call `.setPropagators(ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))` — the propagator composite is wired and reachable. Verified by direct code read of `producer-service/.../config/OtelSdkConfiguration.java` lines 173-180. ✓
- **D-05:** Post-processor OWNS the entire PRODUCER span lifecycle; `OrderPublisher.publish(...)` becomes a thin `convertAndSend(...)` call; `Tracer` constructor parameter removed. **Research-confirmed:** the 4-arg `postProcessMessage` overload gives the post-processor access to `exchange` + `routingKey` for the span attributes — no need to plumb them through a separate path. ✓
- **D-06:** PRODUCER span is inject-only; lifecycle wraps the `propagator.inject(...)` call inside a `try/finally`, ends BEFORE `RabbitTemplate.send(...)` writes to the wire. **Research-confirmed:** matches OTel auto-instrumentation convention for messaging. The post-processor returns the processed `Message` to `doSend(...)`, which then calls `.basicPublish(...)` on the AMQP channel — by that point the PRODUCER span has already ended. Broker-level errors propagate up to `OrderService.place(...)`'s INTERNAL span via Phase 2's D-03 catch.
- **D-07:** PRODUCER span uses semconv-correct destination naming — span name `"<exchange> publish"` → `"orders publish"` (Phase 2 was `"orders.created publish"`); `messaging.destination.name` = exchange (`"orders"`) (Phase 2 was queue); `messaging.rabbitmq.destination.routing_key` = routing key (`"order.created"`); `messaging.system` = `"rabbitmq"`; `messaging.operation.type` = `SEND`. **Research-confirmed:** OTel messaging semconv (RabbitMQ profile) explicitly says producer-side `messaging.destination.name` should be the exchange. The 4-arg overload provides both `exchange` and `routingKey` to the processor.
- **D-08:** `SimpleRabbitListenerContainerFactory` is a Configurer-aided `@Bean`; `configurer.configure(factory, connectionFactory)` runs first, then user calls `setAdviceChain(advice)` and `setDefaultRequeueRejected(false)`. **Research-confirmed:** Spring Boot's `RabbitAnnotationDrivenConfiguration.simpleRabbitListenerContainerFactory(...)` uses `@ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")` — the auto-created factory backs off when the user defines a bean with that name. The bean method name `rabbitListenerContainerFactory` matches the convention. The Configurer (`SimpleRabbitListenerContainerFactoryConfigurer`) IS exposed as a bean (named `simpleRabbitListenerContainerFactoryConfigurer`) and is injectable. ✓ See "Configurer Order Semantics" below for the override sequence.
- **D-09:** `OrderListener.onOrder(...)` simplified to a thin pass-through (3 lines: extract orderId, LOG.info, processingService.process); `Tracer` constructor parameter removed. **Research-confirmed:** the advice's `Scope.makeCurrent()` is active during the user method body, so `LOG.info(...)` runs INSIDE the CONSUMER span's scope — Phase 5's MDC injector reads `Span.current()` at log emission time and finds the right span. ✓
- **D-10:** `TracingMessageListenerAdvice`'s CONSUMER span shape mirrors the producer-side: span name `"<exchange> process"` → `"orders process"`; `.setParent(extracted)`; `setSpanKind(SpanKind.CONSUMER)`; same four `messaging.*` attributes (system, destination.name = exchange, operation.type = `PROCESS`, rabbitmq.destination.routing_key); `catch (Throwable t)` → `recordException` + `setStatus(ERROR)` + rethrow. **Research-confirmed:** OTel SDK 1.61.0 `Span.spanBuilder(name).setParent(Context).setSpanKind(...)` API supports this shape. The `MessageProperties.getReceivedExchange()` and `getReceivedRoutingKey()` return the inbound message's exchange + routing key — both populated for messages routed via direct exchange.
- **D-11:** APP-04 trigger is in-memory `AtomicInteger` counter on `ProcessingService` (Spring singleton); `incrementAndGet()` per call; on `count % 10 == 0` throws `ProcessingFailedException`. **Research-confirmed:** Spring `@Service` produces singleton scope by default. Counter resets per JVM start.
- **D-12:** Custom `ProcessingFailedException extends RuntimeException` in `com.example.consumer.domain`. **Research-confirmed:** straightforward; class name surfaces as `exception.type` attribute on the recordException event in Tempo.
- **D-13:** `SimpleRabbitListenerContainerFactory.setDefaultRequeueRejected(false)` drops failed messages instead of requeuing. **Research-confirmed:** `BaseRabbitListenerContainerFactory.setDefaultRequeueRejected(Boolean)` is a single-set field. With `false`, Spring AMQP NACKs without requeue. With NO Dead-Letter Exchange (DLX) configured on the queue (the demo's `orders.created` queue is declared in `consumer-service/RabbitConfig.java` as `new Queue(QUEUE, true)` — durable but no `x-dead-letter-exchange` argument), the broker silently drops the message. **NOT routed to /dev/null in some pathological sense — it's just discarded.** This is the intended Phase 3 behavior.

### Claude's Discretion

These items in CONTEXT.md `<decisions> ### Claude's Discretion` are NOT user-locked — research-informed recommendations:

- **`TracingMessagePostProcessor` catch shape:** RESEARCH RECOMMENDS `try/finally` only (no `catch`). The W3C `propagator.inject(...)` call is essentially infallible on the chosen `String` value type — `Setter.set(MessageProperties, String, String)` calls `MessageProperties.setHeader(String, Object)` which calls `HashMap.put`. No throwing path.
- **Defensive arg-extraction in `TracingMessageListenerAdvice`:** RESEARCH RECOMMENDS index `[1]` with an `instanceof Message` guard — `Object data = inv.getArguments()[1]; if (!(data instanceof Message message)) return inv.proceed();`. Rationale below in "Pitfall: Batch Listener Composition".
- **Span attribute extras:** RESEARCH RECOMMENDS sticking to the four-attribute messaging semconv core for symmetry with producer.
- **`addEvent` calls / custom `app.failure.reason`:** RESEARCH RECOMMENDS skipping; `recordException` is sufficient per TRACE-09.
- **`LOG.error` on the failure path:** RESEARCH RECOMMENDS skipping in Phase 3 (Phase 5 logs lesson can preview if user requests).
- **`otel-bootstrap` pom.xml dependency scope:** RESEARCH RECOMMENDS `compile` for `opentelemetry-api`; `provided` for `org.springframework.amqp:spring-rabbit` (consuming services bring it via `spring-boot-starter-amqp`); `provided` for `org.springframework:spring-aop` and the implicit `aopalliance` (transitively pulled by `spring-aop`). See Stack table below.
- **Phase 3 README delta scope:** RESEARCH RECOMMENDS one paragraph noting the deliberate asymmetry, one paragraph noting the destination-name semconv correction, one block listing the four new files. Keep size comparable to Phase 2's "Why duplicated?" callout (~25 lines).
- **Wave / plan structure:** CONTEXT.md proposed 5 plans across 4 waves; research has no reason to deviate.
- **Setter↔Getter unit test (PITFALLS.md #2):** RESEARCH RECOMMENDS landing this in Phase 3 as a Claude's-discretion deliverable. It's a pure unit test (no Spring, no Testcontainers, no broker) that exercises `MessagePropertiesSetter.set(props, "traceparent", "...")` followed by `MessagePropertiesGetter.get(props, "traceparent")` and asserts string round-trip. Cost: ~30 lines. Value: catches the byte[] regression at the lowest possible level, and previews Phase 6's testing surface. **OPTIONAL.**

### Deferred Ideas (OUT OF SCOPE)

- **Phase 4 (Metrics):** `SdkMeterProvider` + 3 instruments — not in Phase 3 scope.
- **Phase 5 (Logs):** `OpenTelemetryAppender` + MDC `trace_id`/`span_id` — not in Phase 3 scope.
- **Phase 6 (Tests):** Testcontainers + `InMemorySpanExporter` cross-service assertions — not in Phase 3 scope.
- **Phase 7 (Polish):** Pre-built dashboard, scripts/load.sh, README walkthrough body, broken-vs-fixed Grafana screenshots.
- **DLX / dead-letter routing:** Excluded by `PROJECT.md` § Out of Scope.
- **Producer-side failure path:** Not in APP-04 (consumer-side only per REQUIREMENTS.md).
- **Span events / `app.failure.reason` attribute:** Phase 7 polish opportunity if user requests.
- **Custom listener-thread error handler:** Spring AMQP's default behavior (NACK + drop with `defaultRequeueRejected=false`) is sufficient.

## Project Constraints (from CLAUDE.md)

`./CLAUDE.md` (project-instructions copy of `PROJECT.md`) lists the pinned tech stack — every constraint relevant to Phase 3:

- **Spring Boot 3.4.13** — pinned. RabbitTemplate auto-create + listener factory auto-create both back off correctly when user beans are present (verified against the v3.4.13 source).
- **Spring AMQP 3.2.8** — pinned (BOM-managed). The 4-arg `postProcessMessage` overload exists since 2.3.4; the `setAdviceChain` field is a single-set property; both verified against v3.2.8 source.
- **OTel Java SDK 1.61.0** — pinned (BOM-managed). `TextMapSetter`, `TextMapGetter`, `W3CTraceContextPropagator`, `Span.recordException`, `Span.setStatus(StatusCode.ERROR)` all verified against v1.61.0 source.
- **Manual SDK only** — no Java agent, no Micrometer bridge, no Spring Boot OTel starter. Phase 3 honors this: the 4 new classes are pure-Java (no annotations); each service's `RabbitConfig.java` declares explicit `@Bean` methods.
- **No DLX** — explicitly excluded. `setDefaultRequeueRejected(false)` drops failed messages.
- **GSD enforcement gate** — present in CLAUDE.md. Phase 3 work flows through `/gsd-execute-phase`, which is the correct entry point post-research.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| W3C trace context injection on AMQP publish | Shared library (`otel-bootstrap`) | Service config (per-service `@Bean` wiring) | The lesson is that ONE class file handles both sides; library hosts the code, services wire it explicitly so attendees see two `@Bean` declarations |
| W3C trace context extraction on AMQP consume | Shared library (`otel-bootstrap`) | Service config (per-service `@Bean` wiring) | Symmetric with inject; same library, same explicit wiring |
| PRODUCER span lifecycle | Shared library (post-processor owns it) | — | D-05: post-processor OWNS the entire PRODUCER span (Phase 2's inline span deleted) |
| CONSUMER span lifecycle | Shared library (advice owns it) | — | D-09: advice OWNS the entire CONSUMER span (Phase 2's inline span deleted) |
| Listener container factory configuration | Service config (consumer-service `RabbitConfig`) | Spring Boot Configurer (defaults + properties) | D-08: Configurer-aided shape; user adds `setAdviceChain` + `setDefaultRequeueRejected` AFTER `configure(...)` |
| Producer's `RabbitTemplate` | Service config (producer-service `RabbitConfig`) | Spring Boot auto-config (backs off) | D-05: explicit `@Bean RabbitTemplate(...)` calls `setBeforePublishPostProcessors(mpp)` |
| Deterministic 10% failure trigger | Domain layer (consumer's `ProcessingService`) | — | D-11/D-12: throw site lives inside Phase 2's existing INTERNAL-span try block |
| Failed-message disposition | Listener container (consumer's `RabbitConfig`) | — | D-13: `defaultRequeueRejected=false` drops on NACK; no DLX per PROJECT.md |
| `traceparent` header storage on the wire | RabbitMQ broker (AMQP message properties) | — | Standard AMQP `BasicProperties.headers` map; values written as String, observable in RabbitMQ Management UI |

## Standard Stack

### Core (already on classpath, unchanged in Phase 3)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OpenTelemetry API | 1.61.0 (BOM) | `Tracer`, `Span`, `Context`, `TextMapPropagator`, `TextMapSetter`, `TextMapGetter` | The propagation primitives Phase 3 calls |
| OpenTelemetry SDK | 1.61.0 (BOM) | `OpenTelemetrySdk`, `SdkTracerProvider`, propagator composition | Already wired by Phase 2's `OtelSdkConfiguration` |
| OpenTelemetry semconv (incubating) | 1.40.0-alpha (pinned) | `MessagingIncubatingAttributes` constants + value enums | Already on classpath in both services |
| Spring AMQP `spring-rabbit` | 3.2.8 (Spring Boot 3.4.13 BOM) | `RabbitTemplate`, `MessagePostProcessor`, `SimpleRabbitListenerContainerFactory`, `MessageProperties` | The Spring AMQP API surface Phase 3 hooks |
| Spring AOP `spring-aop` | 6.2.x (Spring Boot 3.4.13 BOM) | `org.aopalliance.intercept.MethodInterceptor`, `MethodInvocation`, Spring's `ProxyFactory` | Required for the listener advice chain (transitive via `spring-boot-starter-amqp`) |

### New deps for `otel-bootstrap/pom.xml` (Phase 3)

| Library | Coordinate | Scope | Purpose | Notes |
|---------|------------|-------|---------|-------|
| OpenTelemetry API | `io.opentelemetry:opentelemetry-api` | `compile` | Used in source code (not transitive to consumers) | BOM-managed (parent POM) — no `<version>` |
| Spring AMQP API | `org.springframework.amqp:spring-rabbit` | `provided` | `MessagePostProcessor`, `MessageProperties`, `Message`, etc. | BOM-managed — consumers bring it via `spring-boot-starter-amqp` |
| Spring AOP | `org.springframework:spring-aop` | `provided` | Transitively brings `org.aopalliance:aopalliance` for `MethodInterceptor` | BOM-managed — consumers bring it via `spring-boot-starter-amqp` (which depends on `spring-tx` which depends on `spring-aop`) |

**Both `producer-service/pom.xml` and `consumer-service/pom.xml`** must add a `<dependency>` block on `com.example:otel-bootstrap:${project.version}` (`compile` scope). [VERIFIED: both POMs currently have NO dependency on `otel-bootstrap` — the placeholder module from Phase 1 is unreferenced. Phase 3 must add this dependency to both.]

### Version verification

| Artifact | Verified | Source |
|----------|----------|--------|
| `org.springframework.amqp:spring-rabbit:3.2.8` | [VERIFIED: Maven Central] | `central.sonatype.com/artifact/org.springframework.amqp/spring-rabbit/3.2.8` confirmed published; tag `v3.2.8` exists on `spring-projects/spring-amqp` GitHub |
| `io.opentelemetry:opentelemetry-api:1.61.0` | [VERIFIED: Phase 2 verified, in service POMs] | Already resolves cleanly per Phase 2 `mvn dependency:tree` |
| `io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha` | [VERIFIED: Maven Central + already in Phase 2 service POMs] | confirmed via `central.sonatype.com/artifact/io.opentelemetry.semconv/opentelemetry-semconv-incubating/1.40.0-alpha` |

## Architecture Patterns

### System Architecture Diagram

```
                  HTTP POST /orders                                       
                          │                                                
        ┌─────────────────▼──────────────────┐                            
        │ producer-service                   │                            
        │  ┌──────────────────────────────┐  │                            
        │  │ HttpServerSpanFilter [SERVER]│  │                            
        │  │  └─ OrderService.place       │  │                            
        │  │     [INTERNAL]               │  │                            
        │  │      └─ OrderPublisher       │  │                            
        │  │         .publish(...) — thin │  │   <-- Phase 3 deletes inline span here
        │  │           │                  │  │                            
        │  │           ▼                  │  │                            
        │  │  RabbitTemplate.convertAndSend                                
        │  │           │                  │  │                            
        │  │           ▼ (doSend invokes  │  │                            
        │  │             beforePublishPPs)│  │                            
        │  │  TracingMessagePostProcessor │  │   <-- Phase 3 NEW (otel-bootstrap)
        │  │   span [PRODUCER] +          │  │                            
        │  │   propagator.inject(props,   │  │                            
        │  │     SETTER) writes           │  │                            
        │  │     traceparent=String       │  │                            
        │  │           │                  │  │                            
        │  │           ▼                  │  │                            
        │  │  AMQP wire → broker          │  │                            
        │  └──────────────────────────────┘  │                            
        └────────────────────────────────────┘                            
                          │                                                
                  (orders exchange)                                        
                          │                                                
                  (order.created routing key)                              
                          │                                                
                  (orders.created queue)                                   
                          │                                                
        ┌─────────────────▼──────────────────┐                            
        │ consumer-service                   │                            
        │  ┌──────────────────────────────┐  │                            
        │  │ SimpleRabbitListenerContainer│  │                            
        │  │  consumer thread receives msg│  │                            
        │  │           │                  │  │                            
        │  │           ▼ (proxy invokes)  │  │                            
        │  │  ContainerDelegate           │  │                            
        │  │   .invokeListener(channel,   │  │                            
        │  │                  message)    │  │                            
        │  │           │                  │  │                            
        │  │           ▼ (advice wraps)   │  │                            
        │  │  TracingMessageListenerAdvice│  │   <-- Phase 3 NEW (otel-bootstrap)
        │  │   propagator.extract(props,  │  │                            
        │  │     GETTER) → extracted ctx  │  │                            
        │  │   span [CONSUMER]            │  │                            
        │  │   .setParent(extracted)      │  │                            
        │  │           │                  │  │                            
        │  │           ▼ (inv.proceed)    │  │                            
        │  │  MessagingMessageListener-   │  │                            
        │  │   Adapter.onMessage(msg, ch) │  │                            
        │  │   → invokeHandler(...)       │  │                            
        │  │           │                  │  │                            
        │  │           ▼                  │  │                            
        │  │  OrderListener.onOrder(map)  │  │   <-- Phase 3 deletes inline span here
        │  │   - LOG.info (in CONSUMER    │  │                            
        │  │     span scope - Phase 5     │  │                            
        │  │     reads trace_id here)     │  │                            
        │  │   - processingService.process│  │                            
        │  │           │                  │  │                            
        │  │           ▼                  │  │                            
        │  │  ProcessingService.process   │  │                            
        │  │   [INTERNAL]                 │  │                            
        │  │   - counter.incrementAndGet  │  │   <-- Phase 3 NEW
        │  │   - if n%10==0:              │  │                            
        │  │       throw                  │  │                            
        │  │       ProcessingFailedExc    │  │   <-- Phase 3 NEW
        │  │           │                  │  │                            
        │  │           ▼ (exception)      │  │                            
        │  │  Phase 2 D-03 catch on       │  │                            
        │  │   INTERNAL span:             │  │                            
        │  │   recordException + ERROR    │  │                            
        │  │           │                  │  │                            
        │  │           ▼ (rethrow)        │  │                            
        │  │  OrderListener.onOrder       │  │                            
        │  │           │                  │  │                            
        │  │           ▼ (rethrow)        │  │                            
        │  │  TracingMessageListenerAdvice│  │                            
        │  │   catch (Throwable):         │  │                            
        │  │   recordException + ERROR    │  │                            
        │  │   on CONSUMER span; rethrow  │  │                            
        │  │           │                  │  │                            
        │  │           ▼ (rethrow)        │  │                            
        │  │  Spring AMQP container       │  │                            
        │  │   NACKs message              │  │                            
        │  │   defaultRequeueRejected=    │  │                            
        │  │     false → DROP             │  │                            
        │  └──────────────────────────────┘  │                            
        └────────────────────────────────────┘                            
                                                                           
        OTLP/gRPC :4317 → grafana/otel-lgtm                               
        Tempo: ONE trace, traceId(producer)==traceId(consumer)            
        consumer.parentSpanId == producer.spanId                          
        Failed (10th) order: trace status = ERROR                         
```

### Recommended Project Structure (Phase 3 deltas)

```
otel-bootstrap/
├── pom.xml                                # ADD: 3 deps (otel-api compile, spring-rabbit + spring-aop provided)
└── src/main/java/com/example/otel/
    ├── package-info.java                  # KEEP existing or update comment
    └── amqp/                              # NEW package
        ├── TracingMessagePostProcessor.java   # NEW — implements MessagePostProcessor
        ├── TracingMessageListenerAdvice.java  # NEW — implements MethodInterceptor
        ├── MessagePropertiesSetter.java       # NEW — TextMapSetter<MessageProperties>
        └── MessagePropertiesGetter.java       # NEW — TextMapGetter<MessageProperties>

producer-service/
├── pom.xml                                # ADD: <dependency>com.example:otel-bootstrap</dependency>
└── src/main/java/com/example/producer/
    ├── config/RabbitConfig.java           # MODIFY: + @Bean TracingMessagePostProcessor + explicit @Bean RabbitTemplate
    └── messaging/OrderPublisher.java      # MODIFY: DELETE inline PRODUCER span body; remove Tracer ctor param

consumer-service/
├── pom.xml                                # ADD: <dependency>com.example:otel-bootstrap</dependency>
└── src/main/java/com/example/consumer/
    ├── config/RabbitConfig.java           # MODIFY: + @Bean TracingMessageListenerAdvice + Configurer-aided @Bean SimpleRabbitListenerContainerFactory
    ├── messaging/OrderListener.java       # MODIFY: DELETE inline CONSUMER span body; remove Tracer ctor param
    ├── domain/ProcessingService.java      # MODIFY: + AtomicInteger counter + throw site (inside Phase 2 try block)
    └── domain/ProcessingFailedException.java  # NEW — extends RuntimeException
```

### Pattern 1: 4-arg `MessagePostProcessor` for inject + PRODUCER span

**What:** Implement the `MessagePostProcessor` 4-arg overload (added in Spring AMQP 2.3.4) so the post-processor receives `exchange` and `routingKey` directly — no need to plumb destination identity through a separate channel.
**When to use:** Always for AMQP context propagation in Spring AMQP ≥ 2.3.4. The 1-arg overload is the abstract entry point but the 4-arg overload is what `RabbitTemplate.doSend(...)` actually invokes.
**Example:**
```java
// Source: Spring AMQP v3.2.8 MessagePostProcessor.java + RabbitTemplate.doSend
// otel-bootstrap/.../amqp/TracingMessagePostProcessor.java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.springframework.amqp.core.Correlation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

public class TracingMessagePostProcessor implements MessagePostProcessor {
    private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TracingMessagePostProcessor(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }

    /**
     * 4-arg overload (added in Spring AMQP 2.3.4) — invoked by
     * RabbitTemplate.doSend() with the user-supplied exchange + routingKey.
     * The 1-arg overload below delegates to this; in practice Spring AMQP
     * always calls the 4-arg form for beforePublishPostProcessors.
     */
    @Override
    public Message postProcessMessage(Message message, Correlation correlation,
                                      String exchange, String routingKey) {
        MessageProperties props = message.getMessageProperties();
        Span span = tracer.spanBuilder(exchange + " publish")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingIncubatingAttributes.MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, exchange)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                routingKey)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            propagator.inject(Context.current(), props, SETTER);
            return message;
        } finally {
            span.end();
        }
    }

    @Override
    public Message postProcessMessage(Message message) {
        // Defensive — should never be reached because the 4-arg overload is the
        // one beforePublishPostProcessors invokes. If it IS reached, fall back
        // to reading destination from the message properties (post-publish path
        // sets those on inbound conversion; for outbound they may be empty).
        return message;
    }
}
```

### Pattern 2: `MethodInterceptor` advice for extract + CONSUMER span (with `instanceof Message` guard)

**What:** Implement `MethodInterceptor` so that `MethodInvocation.getArguments()[1]` is the `Message`. Defensive `instanceof` guard handles batch-listener composition (`List<Message>` instead of `Message`).
**When to use:** Always for Spring AMQP listener-side context extraction. Wrapping at the container delegate level (instead of inline in each `@RabbitListener` method) keeps the lesson concentrated in one class.
**Example:**
```java
// Source: Spring AMQP v3.2.8 AbstractMessageListenerContainer.invokeListener +
//         ContainerDelegate.invokeListener(Channel channel, Object data)
// otel-bootstrap/.../amqp/TracingMessageListenerAdvice.java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

public class TracingMessageListenerAdvice implements MethodInterceptor {
    private static final MessagePropertiesGetter GETTER = new MessagePropertiesGetter();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TracingMessageListenerAdvice(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
    }

    @Override
    public Object invoke(MethodInvocation inv) throws Throwable {
        // ContainerDelegate.invokeListener(Channel channel, Object data)
        // — args[0] is the Channel, args[1] is the data (Message for non-batch).
        Object data = inv.getArguments()[1];
        if (!(data instanceof Message message)) {
            // Batch listener (List<Message>) or unexpected shape — skip tracing,
            // proceed without wrapping. This phase doesn't teach batch listeners.
            return inv.proceed();
        }
        MessageProperties props = message.getMessageProperties();
        TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
        Context extracted = propagator.extract(Context.current(), props, GETTER);

        String exchange = props.getReceivedExchange();  // "orders"
        String routingKey = props.getReceivedRoutingKey();  // "order.created"

        Span span = tracer.spanBuilder(exchange + " process")
            .setParent(extracted)
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                MessagingIncubatingAttributes.MessagingSystemIncubatingValues.RABBITMQ)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, exchange)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.PROCESS)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                routingKey)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return inv.proceed();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR);
            throw t;
        } finally {
            span.end();
        }
    }
}
```

### Pattern 3: Setter — write `String`, never `byte[]` (PITFALLS.md #2)

```java
// Source: PITFALLS.md #2; OTel TextMapSetter contract (1.61.0)
// otel-bootstrap/.../amqp/MessagePropertiesSetter.java
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.amqp.core.MessageProperties;

public class MessagePropertiesSetter implements TextMapSetter<MessageProperties> {
    @Override
    public void set(MessageProperties carrier, String key, String value) {
        if (carrier == null) return;  // OTel TextMapSetter spec: carrier may be null
        carrier.setHeader(key, value);  // value is String — round-trips cleanly
    }
}
```

### Pattern 4: Getter — defensive `.toString()` normalization (PITFALLS.md #2)

```java
// Source: PITFALLS.md #2; OTel TextMapGetter contract (1.61.0); Spring AMQP
// MessageProperties.getHeaders() returns non-null Map<String, Object>
// otel-bootstrap/.../amqp/MessagePropertiesGetter.java
import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.springframework.amqp.core.MessageProperties;

public class MessagePropertiesGetter implements TextMapGetter<MessageProperties> {
    @Override
    public Iterable<String> keys(MessageProperties carrier) {
        return carrier.getHeaders().keySet();  // Headers map is never null
    }

    @Nullable
    @Override
    public String get(@Nullable MessageProperties carrier, String key) {
        if (carrier == null) return null;
        Object raw = carrier.getHeader(key);
        return raw == null ? null : raw.toString();  // normalizes String / LongString / byte[]
    }
}
```

### Anti-Patterns to Avoid

- **Subclassing `RabbitTemplate` to inject context:** Bypasses `MessageConverter` (PITFALLS.md #12). Use `setBeforePublishPostProcessors(...)` so the converter runs first.
- **Reading context inside the `@RabbitListener` method body:** `Context.current()` is `Context.root()` on the listener thread (PITFALLS.md #7). The advice approach extracts BEFORE the user method runs.
- **Writing `byte[]` to `MessageProperties.setHeader`:** Returns as `LongStringHelper.ByteArrayLongString` on the consumer; getter returns wrong type → silent extraction failure → fallback to root span (PITFALLS.md #2).
- **Calling `setAdviceChain(advice)` BEFORE `configurer.configure(factory, connectionFactory)`:** Configurer doesn't directly set `adviceChain` UNLESS `spring.rabbitmq.listener.simple.retry.enabled=true` (default `false`). Even so, Spring Boot's retry advice would overwrite the user's tracing advice if order is reversed — call `configure(...)` first, then `setAdviceChain(tracingAdvice)`.
- **Naming the listener factory bean anything OTHER than `rabbitListenerContainerFactory`:** Spring's `@RabbitListener` resolves by that exact name (the convention `RabbitListenerAnnotationBeanPostProcessor` looks for). The bean method name `rabbitListenerContainerFactory` IS the bean name when no `@Bean(name=...)` override is given.

## Configurer Order Semantics (D-08)

Investigation of `org.springframework.boot.autoconfigure.amqp.AbstractRabbitListenerContainerFactoryConfigurer.configure(factory, connectionFactory, config)` in Spring Boot 3.4.13 reveals the following property setters are called by the Configurer (in `configure(...)` body order):

1. `factory.setConnectionFactory(connectionFactory)`
2. `factory.setMessageConverter(this.messageConverter)` (if set)
3. `factory.setAutoStartup(configuration.isAutoStartup())`
4. `factory.setAcknowledgeMode(configuration.getAcknowledgeMode())` (if set)
5. `factory.setPrefetchCount(configuration.getPrefetch())` (if set)
6. **`factory.setDefaultRequeueRejected(configuration.getDefaultRequeueRejected())`** (if `spring.rabbitmq.listener.simple.default-requeue-rejected` is set in properties; default unset)
7. `factory.setIdleEventInterval(configuration.getIdleEventInterval().toMillis())` (if set)
8. `factory.setMissingQueuesFatal(configuration.isMissingQueuesFatal())`
9. `factory.setDeBatchingEnabled(configuration.isDeBatchingEnabled())`
10. `factory.setForceStop(configuration.isForceStop())`
11. `factory.setTaskExecutor(this.taskExecutor)` (if set)
12. `factory.setObservationEnabled(configuration.isObservationEnabled())`
13. **`factory.setAdviceChain(builder.build())`** (only if `spring.rabbitmq.listener.simple.retry.enabled=true`; default `false`, verified against `RabbitProperties.ListenerRetry`/`Retry` parent class)

`SimpleRabbitListenerContainerFactoryConfigurer.configure(...)` then chains additional simple-listener properties (`concurrency`, `maxConcurrency`, `batchSize`, `consumerBatchEnabled`).

**Implications for D-08:**
- User's call `factory.setAdviceChain(tracingAdvice)` AFTER `configure(...)` overwrites both: (a) the `null` adviceChain (default case), (b) Spring Boot's retry advice IF a future user enabled `spring.rabbitmq.listener.simple.retry.enabled=true`. Phase 3's demo doesn't enable retry; safe.
- User's call `factory.setDefaultRequeueRejected(false)` AFTER `configure(...)` overwrites the default (which is `null` — Spring AMQP's own default of `true` then applies). Setting `false` flips Spring AMQP's behavior. Order: Configurer-then-user is correct. ✓
- Both setters are single-set field assignments — defensive `Arrays.copyOf` for `adviceChain`, direct field for `defaultRequeueRejected`. Verified in `BaseRabbitListenerContainerFactory.java`.

[VERIFIED: github.com/spring-projects/spring-boot v3.4.13 tag — AbstractRabbitListenerContainerFactoryConfigurer.java + SimpleRabbitListenerContainerFactoryConfigurer.java]
[VERIFIED: github.com/spring-projects/spring-amqp v3.2.8 tag — BaseRabbitListenerContainerFactory.java]

## RabbitAutoConfiguration Backoff (D-05, D-08)

Spring Boot 3.4.13 `RabbitAutoConfiguration` and `RabbitAnnotationDrivenConfiguration` use the following conditions:

- **`@Bean RabbitTemplate rabbitTemplate(...)`:** annotated with `@ConditionalOnSingleCandidate(ConnectionFactory.class)` + `@ConditionalOnMissingBean(RabbitOperations.class)`. **`RabbitTemplate` implements `RabbitOperations`** — defining a user `@Bean RabbitTemplate(...)` causes the auto-created template to back off. ✓
- **`@Bean(name = "rabbitListenerContainerFactory") simpleRabbitListenerContainerFactory(...)`:** annotated with `@ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")` + `@ConditionalOnProperty(prefix = "spring.rabbitmq.listener", name = "type", havingValue = "simple", matchIfMissing = true)`. Defining a user `@Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...)` (matching by bean name) causes the auto-created factory to back off. **Bean method name = bean name** in Spring (no `@Bean(name=...)` needed). ✓
- **`SimpleRabbitListenerContainerFactoryConfigurer` is exposed as a bean** named `simpleRabbitListenerContainerFactoryConfigurer`. Two variants based on `@ConditionalOnThreading` (PLATFORM vs VIRTUAL); user code injects via type alone. ✓

[VERIFIED: github.com/spring-projects/spring-boot v3.4.13 tag — RabbitAutoConfiguration.java + RabbitAnnotationDrivenConfiguration.java]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| W3C `traceparent` header parsing/formatting | Manual `String.format` of `00-{traceId}-{spanId}-{flags}` | `OpenTelemetry.getPropagators().getTextMapPropagator()` (already wired) → `.inject(...)` / `.extract(...)` | Composite-propagator handles `traceparent` AND `tracestate`; future baggage propagator additions flow through one configuration point (D-04) |
| AMQP listener thread context extraction | Inline `propagator.extract(...)` at the top of every `@RabbitListener` method | One `MethodInterceptor` advice on the listener container factory | (a) doesn't compose across multiple listener methods; (b) the extracted context is not the parent of the framework-level work (deserialization, error-handling) — the advice catches all of it; (c) ARCHITECTURE.md Anti-Pattern 3 |
| AMQP message header injection | Subclassing `RabbitTemplate` and overriding `convertAndSend` | `setBeforePublishPostProcessors(MessagePostProcessor)` registered on `RabbitTemplate` | Hook runs AFTER `MessageConverter` produced the byte body — your post-processor adds headers without bypassing the converter (PITFALLS.md #12) |
| Listener factory configuration | `new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(...); factory.setMessageConverter(...); ...` (12+ setters) | Inject `SimpleRabbitListenerContainerFactoryConfigurer` and call `configurer.configure(factory, connectionFactory)` | Spring Boot's Configurer applies `spring.rabbitmq.listener.simple.*` properties + sane defaults; user only overrides the 1-2 settings that matter (advice chain + requeue) |
| Deterministic counter | `synchronized` block around an `int` field | `java.util.concurrent.atomic.AtomicInteger` | One `incrementAndGet()` is atomic + lock-free |

**Key insight:** Every primitive Phase 3 needs (W3C propagator, message post-processor hook, listener advice chain, listener factory configurer) is a documented extension point in the OTel SDK or Spring AMQP. Hand-rolling any of these would re-implement library code badly.

## Common Pitfalls

### Pitfall 1: AMQP context propagation silently broken (CRITICAL — the headline lesson) [from PITFALLS.md #1]

**What goes wrong:** Spring AMQP does NOT propagate W3C trace context by default. Without an explicit inject/extract pair, producer and consumer spans appear in separate traces.
**Why it happens:** OTel Java agent does NOT auto-instrument Spring AMQP; manual SDK + Spring AMQP have no built-in messaging propagation.
**How to avoid:** Phase 3 IS the avoidance. `TracingMessagePostProcessor` injects; `TracingMessageListenerAdvice` extracts.
**Warning signs:**
- In Tempo, producer and consumer spans show different `traceId` values (PHASE 2 STATE, fixed by Phase 3).
- Consumer span has empty `parentSpanId`.
- RabbitMQ Mgmt UI shows AMQP message without `traceparent` header.

### Pitfall 2: `byte[]` vs `String` AMQP header values (CRITICAL) [from PITFALLS.md #2]

**What goes wrong:** Setter writes `value.getBytes(UTF_8)` → consumer-side `getHeader()` returns `LongStringHelper.ByteArrayLongString` → getter's `instanceof String` check fails → returns null → extract returns `Context.root()` → silent fallback to a new root span.
**Why it happens:** AMQP supports rich header types; "binary-safe" instinct.
**How to avoid:** Setter writes `String` directly; getter `.toString()` normalizes everything (idempotent for actual `String` values; fixes `LongString` / `byte[]` arrivals).
**Warning signs:**
- RabbitMQ Mgmt UI shows `traceparent` value as `[B@...` or hex blob.
- Consumer-side debug log shows extracted context equals `Context.root()`.

### Pitfall 3: `@RabbitListener` thread context loss [from PITFALLS.md #7]

**What goes wrong:** `SimpleMessageListenerContainer` uses `SimpleAsyncTaskExecutor` (default) which spawns a NEW thread per delivery. `Context.current()` on the listener thread is `Context.root()` — no upstream parent context.
**Why it happens:** The container thread pool is invisible; developers expect Spring-MVC-like context magic.
**How to avoid:** The advice extracts from `MessageProperties` (the message header is the only context carrier across thread boundaries) BEFORE the listener body runs. `Scope.makeCurrent()` makes the extracted context the current context for the duration of `inv.proceed()` — verified by FLAG #1 (synchronous, same-thread).
**Warning signs:** Inside listener, `Span.current().getSpanContext().isValid()` returns false WITHOUT the advice in place.

### Pitfall 4: Jackson converter quirks if tracing replaces the converter [from PITFALLS.md #12]

**What goes wrong:** Subclassing `RabbitTemplate` to inject context bypasses `Jackson2JsonMessageConverter`; `content_type` and `__TypeId__` headers go missing; consumer's deserialization fails.
**Why it happens:** Mental model conflates "wrap the publish" with "replace the publish".
**How to avoid:** Use `MessagePostProcessor` registered via `setBeforePublishPostProcessors(...)`. The hook runs AFTER the converter has already produced the message body — the post-processor only adds headers.
**Warning signs:** Consumer raises `MessageConversionException`.

### Pitfall 5: Calling `.setAdviceChain(...)` BEFORE `configurer.configure(...)` (NEW)

**What goes wrong:** Configurer's `configure(...)` may set its own `adviceChain` (when retry is enabled), overwriting the user's tracing advice.
**Why it happens:** Order-sensitive. Spring Boot's `setAdviceChain(...)` is conditional on `spring.rabbitmq.listener.simple.retry.enabled=true`; default is `false`, so day-1 demos don't trigger this — but enabling retry later silently breaks tracing.
**How to avoid:** Always: `configurer.configure(factory, connectionFactory)` FIRST, then `factory.setAdviceChain(tracingAdvice)`. CONTEXT.md D-08 shape is correct.
**Warning signs:** Future workshop attendee adds `spring.rabbitmq.listener.simple.retry.enabled=true` and propagation breaks silently.

### Pitfall 6: Batch listener composition breaks `getArguments()[1]` typed cast (NEW)

**What goes wrong:** If a future change adds `factory.setBatchListener(true)` or `factory.setConsumerBatchEnabled(true)`, the listener receives `List<Message>` instead of `Message`. Naive `(Message) inv.getArguments()[1]` `ClassCastException`s.
**Why it happens:** Spring AMQP 3.x added batch-listener composition; `ContainerDelegate.invokeListener(Channel, Object data)` accepts both single `Message` and `List<Message>`.
**How to avoid:** Defensive `if (!(data instanceof Message message)) return inv.proceed();` — skip tracing for batch messages (out of scope for this demo) but don't crash.
**Warning signs:** `ClassCastException` in advice when batching is enabled.

### Pitfall 7: `@Bean(name=...)` conflict for the listener factory (NEW)

**What goes wrong:** User defines `@Bean SimpleRabbitListenerContainerFactory tracingListenerContainerFactory(...)` — Spring Boot's auto-config does NOT back off (it checks for the EXACT name `rabbitListenerContainerFactory`). Two listener factories exist; `@RabbitListener` resolves by the conventional name and ignores the user's.
**Why it happens:** Bean-name matching is exact in `@ConditionalOnMissingBean(name = "...")`.
**How to avoid:** Name the bean method `rabbitListenerContainerFactory` (lowercase r). The bean method name = bean name.
**Warning signs:** Tracing advice never runs; CONSUMER spans never appear; `mvn dependency:tree` doesn't help; the only signal is debugging the actual listener container's factory reference.

### Pitfall 8: `defaultRequeueRejected=false` with no DLX silently drops failed messages (intentional, but warn the attendee)

**What goes wrong:** Failed messages disappear with no audit trail.
**Why it happens:** No DLX configured; `false` means NACK without requeue → broker drops.
**How to avoid:** This IS the intended Phase 3 behavior per D-13 and PROJECT.md. The teaching moment: production code adds a DLX. The README PROP-04 callout should mention this asymmetry.
**Warning signs:** RabbitMQ Mgmt UI shows ack-without-requeue counts climbing on `orders.created` queue.

## Runtime State Inventory

> Phase 3 is NOT a rename/refactor phase. It's an additive feature phase that DELETES Phase 2's inline span code and INSERTS the propagation pair. No string-rename runtime state to inventory.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — verified by inspection. RabbitMQ has no persistent state Phase 3 changes (the queue + exchange names are unchanged). | None |
| Live service config | None — verified by inspection. No changes to docker-compose, no changes to `mise.toml`. | None |
| OS-registered state | None — verified by inspection. No OS-level changes. | None |
| Secrets/env vars | None — verified by inspection. No new env vars; existing OTLP endpoint unchanged. | None |
| Build artifacts | `otel-bootstrap-0.1.0-SNAPSHOT.jar` currently exists in `target/` from a Phase 1 placeholder build. After Phase 3, the JAR will contain the four new classes — Maven's `clean install` regenerates correctly. | None — `mvn clean install` is sufficient |

## Verification Mechanisms (Manual, Phase 3)

> `nyquist_validation` is `false` in `.planning/config.json`, so a programmatic Validation Architecture section is NOT required. Phase 6 owns automated test code per Phase 2 precedent. Phase 3's verifications are manual — Tempo UI, RabbitMQ Mgmt UI, code review, git diff. Below maps each ROADMAP success criterion to its verification mechanism.

| SC # | Criterion | Verification Mechanism | Tool / Where |
|------|-----------|------------------------|--------------|
| 1 | One-trace topology in Tempo: `POST /orders` → ONE trace; `traceId(producer)==traceId(consumer)`; `consumer.parentSpanId==producer.spanId` | (a) `mise run dev` + `mise run demo:order`; (b) Tempo Search by service.name=`order-producer`; (c) note traceID; (d) Tempo Search by service.name=`order-consumer`; (e) confirm matching traceID; (f) open trace detail panel; (g) confirm CONSUMER span's `parentSpanId` == PRODUCER span's `spanId` | Grafana Tempo UI (`http://localhost:3000`, admin/admin) |
| 2 | RabbitMQ Mgmt UI shows readable `traceparent` header on a peeked message — String not byte[] | (a) Stop consumer (so message stays in queue); (b) `mise run demo:order`; (c) RabbitMQ Mgmt UI (`http://localhost:15672`, guest/guest) → Queues → click `orders.created` → "Get messages" with Reject requeue=true, Messages=1; (d) inspect Properties / Headers; (e) confirm `traceparent` value matches `00-<32-hex>-<16-hex>-01` (NOT `[B@...`, NOT a hex blob). PITFALLS.md #2 round-trip avoidance proof. | RabbitMQ Management UI |
| 3 | 10th-order failure produces ERROR trace in Tempo with exception event | (a) Run `mise run demo:order` 10 times in a row (or use a one-liner: `for i in $(seq 1 10); do mise run demo:order; sleep 0.5; done`); (b) Tempo Search by status=`error`; (c) confirm at least 1 trace; (d) drill into the trace; (e) confirm CONSUMER span's `Status Code = ERROR`; (f) confirm `Events` panel contains an `exception` event with `exception.type=com.example.consumer.domain.ProcessingFailedException`, `exception.message="Deterministic failure on order #10 (every 10th order)"`, `exception.stacktrace=...`; (g) confirm INTERNAL span (`ProcessingService.process`) ALSO has Status Code=ERROR (Phase 2 D-03 catch shape recorded the same exception). | Grafana Tempo UI |
| 4 | Structural symmetry of the propagation pair (one inject method matched by one extract method) | Code review: open `otel-bootstrap/.../amqp/TracingMessagePostProcessor.java` AND `TracingMessageListenerAdvice.java` side-by-side. Confirm: (a) both use `openTelemetry.getPropagators().getTextMapPropagator()` (one source of truth, D-04); (b) one calls `.inject(...)`, one calls `.extract(...)`; (c) both create a span (PRODUCER vs CONSUMER) with the SAME 4 messaging semconv attributes; (d) both use the matching pair `MessagePropertiesSetter` ↔ `MessagePropertiesGetter`. The README PROP-04 callout must explicitly contrast this against the per-service-duplicated `OtelSdkConfiguration.java`. | IDE side-by-side; README inspection |
| 5 | Annotated git tag `step-03-context-propagation` exists; `step-02-traces..step-03-context-propagation` diff is small and reviewable | (a) `git for-each-ref refs/tags/step-03-context-propagation` returns `tag` (annotated, NOT `commit`); (b) `git ls-tree step-03-context-propagation` shows the four new files in `otel-bootstrap`; (c) `git diff --stat step-02-traces..step-03-context-propagation` shows ~50 new + ~60 deleted lines + 5 new files (CONTEXT.md `<specifics>` line 240); (d) human reads the full diff in one viewing without losing context. | git CLI; PR-style review of the diff |

**Manual smoke (optional, Claude's discretion):** A `mise run verify:propagation` task could query Tempo for a single trace with the right span topology — out of scope for Phase 3 per Phase 2 precedent (defer test code to Phase 6). Document this in CONTEXT.md `<deferred>` only.

## Code Examples (verified patterns)

### `producer-service/.../config/RabbitConfig.java` — Phase 3 changes

```java
// Source: Spring AMQP v3.2.8 RabbitTemplate.setBeforePublishPostProcessors;
//         Spring Boot v3.4.13 RabbitAutoConfiguration backoff via
//         @ConditionalOnMissingBean(RabbitOperations.class)
package com.example.producer.config;

import com.example.otel.amqp.TracingMessagePostProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "orders";
    public static final String QUEUE = "orders.created";
    public static final String ROUTING_KEY = "order.created";

    @Bean
    DirectExchange ordersExchange() { return new DirectExchange(EXCHANGE); }

    @Bean
    Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }

    @Bean
    Binding ordersBinding(Queue q, DirectExchange ex) {
        return BindingBuilder.bind(q).to(ex).with(ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---- Phase 3 NEW ----
    @Bean
    TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry openTelemetry,
                                                             Tracer tracer) {
        return new TracingMessagePostProcessor(openTelemetry, tracer);
    }

    /**
     * Explicit RabbitTemplate bean overriding Spring Boot's auto-created one
     * (RabbitAutoConfiguration's @ConditionalOnMissingBean(RabbitOperations.class)
     * backs off because RabbitTemplate implements RabbitOperations).
     *
     * The post-processor runs on RabbitTemplate.doSend() AFTER the
     * MessageConverter produced the message body — adding the W3C
     * traceparent header without disturbing content_type or __TypeId__.
     */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory cf,
                                   MessageConverter messageConverter,
                                   TracingMessagePostProcessor tracingMpp) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(messageConverter);
        template.setBeforePublishPostProcessors(tracingMpp);
        return template;
    }
}
```

### `consumer-service/.../config/RabbitConfig.java` — Phase 3 changes

```java
// Source: Spring Boot v3.4.13 SimpleRabbitListenerContainerFactoryConfigurer;
//         RabbitAnnotationDrivenConfiguration's
//         @ConditionalOnMissingBean(name="rabbitListenerContainerFactory")
package com.example.consumer.config;

import com.example.otel.amqp.TracingMessageListenerAdvice;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String QUEUE = "orders.created";

    @Bean
    Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---- Phase 3 NEW ----
    @Bean
    TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry openTelemetry,
                                                                Tracer tracer) {
        return new TracingMessageListenerAdvice(openTelemetry, tracer);
    }

    /**
     * Configurer-aided listener factory bean. Spring Boot's
     * RabbitAnnotationDrivenConfiguration's auto-created factory backs off
     * because we declare a bean named "rabbitListenerContainerFactory"
     * (the bean method name = bean name; matches @ConditionalOnMissingBean(name=...)).
     *
     * Order matters: configurer.configure(...) FIRST so any
     * spring.rabbitmq.listener.simple.* properties (concurrency, prefetch, etc.)
     * apply, then user setters override (advice chain + requeue).
     *
     * setAdviceChain wraps the listener via Spring AOP ProxyFactory around
     * ContainerDelegate.invokeListener(Channel, Object) — the advice's
     * MethodInvocation.getArguments()[1] is the Message (verified against
     * Spring AMQP 3.2.8 source).
     *
     * setDefaultRequeueRejected(false): on listener exception, NACK without
     * requeue. With no DLX configured, the broker drops the message —
     * intentional per CONTEXT.md D-13 (PROJECT.md excludes DLX).
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            TracingMessageListenerAdvice tracingAdvice) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(tracingAdvice);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
```

### `consumer-service/.../domain/ProcessingService.java` — Phase 3 changes (additive only)

```java
// Source: CONTEXT.md D-11/D-12; APP-04 wording from REQUIREMENTS.md;
//         Phase 2 D-03 catch shape preserved
package com.example.consumer.domain;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Service;

@Service
public class ProcessingService {
    private final Tracer tracer;
    private final AtomicInteger counter = new AtomicInteger();   // Phase 3 NEW

    public ProcessingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void process(Map<String, Object> order) {
        Span span = tracer.spanBuilder("ProcessingService.process")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Phase 3 NEW: deterministic 10% failure (APP-04 + TRACE-09).
            int n = counter.incrementAndGet();
            if (n % 10 == 0) {
                throw new ProcessingFailedException(
                    "Deterministic failure on order #" + n + " (every 10th order)");
            }
            // Successful processing path — Phase 1 placeholder retained
            // (simulated downstream domain work, in-memory).
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

## Existing Code Confirmation (line ranges + bean shapes)

**`producer-service/.../messaging/OrderPublisher.java`** [VERIFIED: file is 85 lines]
- Inline PRODUCER span body Phase 3 deletes: lines 39-84 (the entire `publish(...)` method body INCLUDING the span creation, `try/catch/finally`, attribute assignments, and the comment block).
- Method signature line: 39.
- CONTEXT.md said "lines 51-83" — that range covered just the span body excluding signature/braces; the file is shorter than CONTEXT.md estimated. The DELETION TARGET is the entire method body except `convertAndSend(...)`. Final post-Phase-3 method (per D-05):
```java
public void publish(String orderId, Map<String, Object> payload) {
    Map<String, Object> message = new HashMap<>(payload);
    message.put("orderId", orderId);
    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
}
```
- Constructor: change from `(RabbitTemplate, Tracer)` to `(RabbitTemplate)`. Tracer field deleted.
- Imports to delete: all `io.opentelemetry.*` imports + `MessagingIncubatingAttributes` + value enums.

**`consumer-service/.../messaging/OrderListener.java`** [VERIFIED: file is 81 lines]
- Inline CONSUMER span body Phase 3 deletes: lines 47-79 (Phase 2's spanBuilder/setParent/setSpanKind/4 attrs/try/catch/finally, including the verbatim D-10 teaching comment).
- Method signature line: 46.
- Final post-Phase-3 method (per D-09):
```java
@RabbitListener(queues = RabbitConfig.QUEUE)
public void onOrder(Map<String, Object> message) {
    Object orderId = message.get("orderId");
    LOG.info("OrderCreated received: orderId={}", orderId);
    processingService.process(message);
}
```
- Constructor: change from `(ProcessingService, Tracer)` to `(ProcessingService)`. Tracer field deleted.
- Imports to delete: all `io.opentelemetry.*` imports + `MessagingIncubatingAttributes` + value enums.
- `LOG`, `LoggerFactory`, `RabbitListener`, `RabbitConfig`, `ProcessingService`, `Map` imports retained.

**`producer-service/.../config/RabbitConfig.java`** [VERIFIED: 38 lines, 4 @Bean methods]
- Constants `EXCHANGE = "orders"`, `QUEUE = "orders.created"`, `ROUTING_KEY = "order.created"` are present and `public static final` — usable from `OrderPublisher` AND from new `@Bean RabbitTemplate(...)` Phase 3 will add.
- Existing beans preserved: `ordersExchange()`, `ordersCreatedQueue()`, `ordersBinding(Queue, DirectExchange)`, `jsonMessageConverter()`.

**`consumer-service/.../config/RabbitConfig.java`** [VERIFIED: 18 lines, 2 @Bean methods]
- Constant `QUEUE = "orders.created"` present.
- Existing beans preserved: `ordersCreatedQueue()`, `jsonMessageConverter()`.

**`consumer-service/.../domain/ProcessingService.java`** [VERIFIED: 54 lines]
- Phase 2 D-03 catch shape present (lines 43-49: catch RuntimeException → recordException + setStatus(ERROR) + throw).
- Empty try-block placeholder lines 41-42 — the throw site Phase 3 inserts goes BEFORE these comment lines (or replaces them).

**`otel-bootstrap/pom.xml`** [VERIFIED: 22 lines, ZERO dependencies]
- Module is empty placeholder. Phase 3 adds the 3 deps documented in Stack table.
- `package-info.java` exists (627 bytes) — content can stay or get updated; not load-bearing.

**`producer-service/pom.xml` and `consumer-service/pom.xml`** [VERIFIED: 117 lines each]
- Currently NO dependency on `otel-bootstrap`. Phase 3 must add `<dependency><groupId>com.example</groupId><artifactId>otel-bootstrap</artifactId><version>${project.version}</version></dependency>` to BOTH.
- `${project.version}` resolves to `0.1.0-SNAPSHOT` via parent POM inheritance.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `messaging.operation = "publish"` (deprecated) | `messaging.operation.type = SEND` (incubating value enum) | OTel semconv 1.40.0 (Feb 2026) | Phase 2 already uses the new key; Phase 3 inherits |
| `MESSAGING_DESTINATION_NAME = queue name` (semconv anti-pattern for producer) | `MESSAGING_DESTINATION_NAME = exchange name` (semconv-correct for AMQP producer) | OTel messaging semconv RabbitMQ profile | Phase 3 corrects this from Phase 2; visible in Tempo across step-02/step-03 tags |
| `MessagePostProcessor.postProcessMessage(Message)` only | Plus `(Message, Correlation, String exchange, String routingKey)` | Spring AMQP 2.3.4 | Phase 3 uses the 4-arg overload — destination identity flows in directly |

**Deprecated/outdated:**
- `io.opentelemetry:opentelemetry-semconv` (legacy SDK-BOM coordinate) — Phase 1 deliberately uses `io.opentelemetry.semconv:opentelemetry-semconv` instead.
- `MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.PUBLISH` — present in 1.40.0-alpha but marked deprecated; use `SEND` instead. (`PUBLISH` was renamed to `SEND` in spec evolution.)

## Assumptions Log

> All claims tagged `[ASSUMED]` need user confirmation before locking. If empty: every claim was verified or cited.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | "Tag `step-02-traces` deletion target line range in `OrderPublisher.publish` is lines 39-84 (entire method body)" | Existing Code Confirmation | Low — lines confirmed by direct file read; CONTEXT.md said 51-83 which was a span-body subset. Planner can verify with `git blame` if precision matters. |
| A2 | "`spring.rabbitmq.listener.simple.retry.enabled` defaults to `false`" | Pitfall 5; Configurer Order Semantics | Low — `RabbitProperties.ListenerRetry extends Retry`; the parent `Retry` class wasn't fully read but Java field defaults to `false` for a `boolean` field, and `Retry.enabled` does not appear to be re-initialized in any Spring Boot 3.4.x change |

(All other claims were verified against Spring AMQP v3.2.8 / Spring Boot v3.4.13 / OTel SDK v1.61.0 source code on GitHub, or against the existing repo files via direct read.)

## Open Questions

> No open questions blocking planning. All three CONTEXT.md research flags are CLOSED.

(For documentation: the `Retry.enabled` default could be definitively confirmed by reading `RabbitProperties.Retry` in spring-boot v3.4.13. The risk is low because (a) Phase 3's demo doesn't enable retry, (b) even if the default were `true` the user override after `configure(...)` correctly takes precedence.)

## Environment Availability

> Skipped — Phase 3 has no NEW external dependencies. All required runtime tools (Java 17 / Maven 3.9 / Docker / RabbitMQ container / grafana-otel-lgtm container) were validated in Phase 1's environment audit and remain unchanged. Phase 2 verified all are functional (status: SHIPPED 2026-05-01).

## Security Domain

> `security_enforcement: true` and `security_asvs_level: 1` in `.planning/config.json` — section required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Demo has no AuthN on HTTP API by design (`PROJECT.md` § Out of Scope) |
| V3 Session Management | no | Stateless; no sessions |
| V4 Access Control | no | No authorization model in workshop demo |
| V5 Input Validation | partial | The advice's `data instanceof Message` guard is defensive validation at a trust boundary (the AMQP message carrier crossing the JVM/JVM hop) |
| V6 Cryptography | no | OTLP gRPC over plain HTTP is intentional (`localhost`-only); no secrets in spans |
| V7 Error Handling & Logging | yes | `recordException(Throwable)` includes the stack trace as a span event attribute. PII in stack traces would propagate to Tempo. The throw site message contains no sensitive data ("Deterministic failure on order #N (every 10th order)") |
| V14 Configuration | yes | Header injection MUST write `String` not `byte[]` (PITFALLS.md #2); `defaultRequeueRejected=false` MUST be paired with the team's awareness that messages get dropped |

### Known Threat Patterns for `Spring AMQP + OTel manual-SDK`

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Header spoofing — a malicious AMQP producer sets `traceparent` to a chosen `traceId`/`spanId` to inject itself into a victim's trace | Spoofing | Workshop scope: only our own producer publishes. Production: validate `traceparent` format strictness via `W3CTraceContextPropagator`'s built-in validation (already enforced by the SDK — invalid traceparent values are silently dropped, not propagated) |
| Tampering on the wire — broker MITM modifies `traceparent` header | Tampering | Workshop scope: localhost-only AMQP. Production: TLS-AMQP + broker auth; out of scope per `PROJECT.md` |
| Information disclosure via span attributes — `messaging.destination.name` etc. expose topology | Information Disclosure | Workshop-local strings (`orders` exchange, `order.created` routing key); no PII; same posture as Phase 2 (TC-2-04-02) |
| Information disclosure via `recordException` stack trace — exception traces leak internal class names | Information Disclosure | Demo-only; `ProcessingFailedException`'s message is bounded text. Production: scrub or sanitize attributes per V7 |
| Denial of service — failed-message storm if `defaultRequeueRejected=true` AND a poison message persists | DoS | D-13 sets `false` to break the loop. The 10th-order failure path is deterministic, but each failed message is dropped; the next message succeeds |
| Repudiation — exception is silently swallowed | Repudiation | The advice's `catch (Throwable t) { recordException + setStatus(ERROR); throw t; }` rethrows after recording — fail-closed by default. Phase 2's D-03 shape on the INTERNAL span is identical |
| Spoofing via baggage — propagating malicious baggage values | Spoofing | `W3CBaggagePropagator` is wired but Phase 3 doesn't read baggage. Production: validate baggage attribute names against an allow-list before reading |

**No CRITICAL/HIGH security blockers identified for Phase 3.** All threats are workshop-bounded; production hardening notes are documented for attendees.

## Sources

### Primary (HIGH confidence)
- [Spring AMQP v3.2.8 `RabbitTemplate.java`](https://raw.githubusercontent.com/spring-projects/spring-amqp/v3.2.8/spring-rabbit/src/main/java/org/springframework/amqp/rabbit/core/RabbitTemplate.java) — `doSend()` invokes 4-arg `postProcessMessage`; FLAG #2 resolved
- [Spring AMQP v3.2.8 `MessagePostProcessor.java`](https://raw.githubusercontent.com/spring-projects/spring-amqp/v3.2.8/spring-amqp/src/main/java/org/springframework/amqp/core/MessagePostProcessor.java) — 3 method signatures; the 4-arg overload's default delegates to 2-arg, which delegates to 1-arg
- [Spring AMQP v3.2.8 `AbstractMessageListenerContainer.java`](https://raw.githubusercontent.com/spring-projects/spring-amqp/v3.2.8/spring-rabbit/src/main/java/org/springframework/amqp/rabbit/listener/AbstractMessageListenerContainer.java) — `ContainerDelegate.invokeListener(Channel, Object)` proxy chain; FLAG #1 + FLAG #3 resolved
- [Spring AMQP v3.2.8 `MessagingMessageListenerAdapter.java`](https://raw.githubusercontent.com/spring-projects/spring-amqp/v3.2.8/spring-rabbit/src/main/java/org/springframework/amqp/rabbit/listener/adapter/MessagingMessageListenerAdapter.java) — `onMessage(Message, Channel)` dispatches to user method synchronously
- [Spring AMQP v3.2.8 `BaseRabbitListenerContainerFactory.java`](https://raw.githubusercontent.com/spring-projects/spring-amqp/v3.2.8/spring-rabbit/src/main/java/org/springframework/amqp/rabbit/config/BaseRabbitListenerContainerFactory.java) — `setAdviceChain` and `setDefaultRequeueRejected` are single-set field assignments
- [Spring AMQP v3.2.8 `MessageProperties.java`](https://raw.githubusercontent.com/spring-projects/spring-amqp/v3.2.8/spring-amqp/src/main/java/org/springframework/amqp/core/MessageProperties.java) — headers map is `HashMap<String, Object>`, never null, case-sensitive
- [Spring Boot v3.4.13 `RabbitAutoConfiguration.java`](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.4.13/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/amqp/RabbitAutoConfiguration.java) — RabbitTemplate `@ConditionalOnMissingBean(RabbitOperations.class)`
- [Spring Boot v3.4.13 `RabbitAnnotationDrivenConfiguration.java`](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.4.13/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/amqp/RabbitAnnotationDrivenConfiguration.java) — listener factory `@ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")`
- [Spring Boot v3.4.13 `AbstractRabbitListenerContainerFactoryConfigurer.java`](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.4.13/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/amqp/AbstractRabbitListenerContainerFactoryConfigurer.java) — `configure(...)` property setter list; `setAdviceChain` only when `retry.enabled=true`
- [Spring Boot v3.4.13 `RabbitProperties.java`](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.4.13/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/amqp/RabbitProperties.java) — `ListenerRetry extends Retry`; default `enabled=false`
- [OpenTelemetry Java v1.61.0 `W3CTraceContextPropagator.java`](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java/v1.61.0/api/all/src/main/java/io/opentelemetry/api/trace/propagation/W3CTraceContextPropagator.java) — `traceparent` format `00-<32hex>-<16hex>-<2hex>`, written as `String` via setter
- [OpenTelemetry Java v1.61.0 `TextMapSetter.java`](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java/v1.61.0/context/src/main/java/io/opentelemetry/context/propagation/TextMapSetter.java) — single `set(@Nullable C, String, String)` method
- [OpenTelemetry Java v1.61.0 `TextMapGetter.java`](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java/v1.61.0/context/src/main/java/io/opentelemetry/context/propagation/TextMapGetter.java) — `keys(C)`, `@Nullable get(@Nullable C, String)`, default `getAll(...)`
- [OpenTelemetry semantic-conventions-java v1.40.0 `MessagingIncubatingAttributes.java`](https://raw.githubusercontent.com/open-telemetry/semantic-conventions-java/v1.40.0/semconv-incubating/src/main/java/io/opentelemetry/semconv/incubating/MessagingIncubatingAttributes.java) — `MESSAGING_SYSTEM`, `MESSAGING_DESTINATION_NAME`, `MESSAGING_OPERATION_TYPE`, `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY` keys + `MessagingSystemIncubatingValues.RABBITMQ`, `MessagingOperationTypeIncubatingValues.{SEND, PROCESS, RECEIVE, CREATE, ...}`
- Direct repo file reads (HIGH confidence): `/home/coto/dev/demo/ose-otel-demo/{producer-service,consumer-service}/src/main/java/com/example/{producer,consumer}/**.java`, `pom.xml`, `mise.toml`, all 03-CONTEXT.md / Phase 2 PLAN files

### Secondary (MEDIUM confidence)
- Maven Central confirmation of `org.springframework.amqp:spring-rabbit:3.2.8` (publish date "6 months ago" per central.sonatype.com)
- Maven Central confirmation of `io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha`
- `.planning/research/PITFALLS.md` (Phase 0 research synthesis) — pitfalls #1, #2, #7, #12 cross-referenced

### Tertiary (none — no LOW-confidence claims load-bearing for Phase 3 planning)

## Metadata

**Confidence breakdown:**
- FLAG #1 (advice composition): HIGH — direct read of v3.2.8 source confirms synchronous, same-thread proxy chain
- FLAG #2 (4-arg overload): HIGH — direct read of `RabbitTemplate.doSend(...)` confirms 4-arg invocation
- FLAG #3 (arg index): HIGH — direct read of `ContainerDelegate.invokeListener(Channel, Object)` confirms `[1]` is the data
- Standard stack: HIGH — versions Phase 2 verified, no new deps versioned
- Architecture patterns: HIGH — 4 code examples grounded in cited source
- Pitfalls: HIGH — 8 pitfalls each grounded in `PITFALLS.md` or new source-confirmed gotchas
- Existing code line ranges: MEDIUM — file reads confirm overall structure; planner should re-confirm exact line ranges with `wc -l` at execution time

**Research date:** 2026-05-01
**Valid until:** 2026-06-01 (30 days — Spring AMQP 3.2 line is in maintenance; OTel SDK 1.61.0 may be superseded by 1.62.0+ within window but BOM pinning prevents drift)
