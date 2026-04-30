# Pitfalls Research

**Domain:** Manual OpenTelemetry SDK instrumentation in Spring Boot 3.4.13 + Spring AMQP (RabbitMQ) + Testcontainers — workshop/teaching project
**Researched:** 2026-04-29
**Confidence:** HIGH (verified against official OTel docs, opentelemetry-java-instrumentation issues/discussions, Spring AMQP reference, and Spring Boot reference docs)

> Each pitfall is tagged with severity:
> - **CRITICAL** — silently breaks the headline pedagogical lesson (e.g., breaks the trace, drops telemetry, makes the demo lie about how OTel works)
> - **HIGH** — visibly broken behaviour or learning anti-pattern that an attendee would carry into production work
> - **MEDIUM** — friction, confusion, or poor practice that doesn't break the demo but degrades the workshop experience

The workshop step labels reference the staged checkpoints listed in `PROJECT.md`:
`step-01-baseline`, `step-02-traces`, `step-03-context-propagation`, `step-04-metrics`, `step-05-logs`, `step-06-tests`. A `step-00-bootstrap` is implied for the initial Maven/mise/docker-compose scaffold.

---

## Critical Pitfalls

### Pitfall 1: AMQP context propagation silently broken because Spring AMQP does NOT propagate W3C trace context by default

**Severity:** CRITICAL — this is the single headline lesson of the workshop. If it's wrong, the demo lies.

**What goes wrong:**
The producer's `RabbitTemplate.convertAndSend(...)` and the consumer's `@RabbitListener` execute in different JVMs (and different threads inside a single JVM). Unlike Spring MVC and JDBC, the OpenTelemetry **Java agent does not auto-instrument Spring AMQP**, and the Spring AMQP library itself does not inject `traceparent` / `tracestate` headers into the AMQP `BasicProperties`. With manual SDK instrumentation, if the developer does not explicitly inject context on publish and extract on consume, the consumer span has no parent and shows up as a separate trace.

**Why it happens:**
- Misconception: "The SDK propagates context automatically" (it does, but only across `Context` boundaries within the same thread, not across the network).
- Misconception: "Spring AMQP handles tracing now" (Spring AMQP defers this to Micrometer-tracing-bridge-otel when configured, which is explicitly out of scope per `PROJECT.md`).
- Developer adds spans on the producer and consumer sides, sees both spans appear in Tempo, but doesn't notice the trace IDs are different because Grafana groups them by service.

**How to avoid:**
- Define a `TextMapSetter<MessageProperties>` that calls `messageProperties.setHeader(key, value)` (string keys, **string** values — see Pitfall 2).
- On publish, call `propagator.inject(Context.current(), messageProperties, setter)` inside a `MessagePostProcessor` registered with the `RabbitTemplate`, or wrap the `convertAndSend` call manually.
- Define a `TextMapGetter<MessageProperties>` whose `get(carrier, key)` reads the header and returns its `toString()`.
- In the `@RabbitListener` method (or via a `@Header` parameter / `MessagePostProcessor` on the listener container), call `propagator.extract(Context.current(), messageProperties, getter)`, then `try (Scope s = extractedContext.makeCurrent()) { ... }` before starting the consumer span.
- The consumer span MUST have `SpanKind.CONSUMER`, the producer span MUST have `SpanKind.PRODUCER` (semconv requirement, also helps Tempo render the messaging flow correctly).
- Add the W3C TraceContext + Baggage propagators explicitly: `OpenTelemetrySdk.builder().setPropagators(ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))`.

**Warning signs:**
- In Grafana Tempo, the producer and consumer spans show up under different `Trace ID` values.
- The consumer span has no parent / is a root span when it should be a child of the publish span.
- The `traceparent` header is missing from the AMQP message when inspected in RabbitMQ's management UI (`http://localhost:15672` → Queues → Get message).
- Workshop assertion: `traceId(producerSpan) == traceId(consumerSpan)` fails in the integration test.

**Phase to address:** `step-03-context-propagation` (this IS the lesson). Verify with both visual inspection in Grafana AND a Testcontainers assertion in `step-06-tests`.

---

### Pitfall 2: AMQP header values written as `byte[]` instead of `String`, breaking the W3C `traceparent` round-trip

**Severity:** CRITICAL

**What goes wrong:**
The developer's `TextMapSetter` writes the value as `value.getBytes(StandardCharsets.UTF_8)` (a `byte[]`) into `MessageProperties.setHeader`. On the wire, this becomes a RabbitMQ `LongString`. On the receive side, Spring AMQP's `DefaultMessagePropertiesConverter` converts short `LongString` (≤1024 bytes) values to `String` via `toString()` — but only when the body is short. If the developer attempted `byte[]` to be "safe", the consumer-side header arrives as `com.rabbitmq.client.impl.LongStringHelper.ByteArrayLongString` whose `toString()` works but whose `equals(...)` and `instanceof String` checks do not, and the developer's `TextMapGetter` returns `null` for the `traceparent` key. Result: silent fallback to a new root span.

**Why it happens:**
- AMQP supports rich header types (long, byte[], List, Map, LongString); developers default to `byte[]` thinking "binary-safe".
- The `MessageProperties.getHeaders()` map is `Map<String, Object>`, so the compiler doesn't catch type mismatches.
- Round-trip behaviour differs based on header length (`DefaultMessagePropertiesConverter` has a `longStringLimit` of 1024 bytes by default — `traceparent` is 55 bytes so it round-trips, but a long `tracestate` could trip the limit).

**How to avoid:**
- Setter writes `String` values: `(carrier, key, value) -> carrier.setHeader(key, value)` where `value` is already `String` from the propagator.
- Getter normalizes: `String raw = carrier.getHeaders().get(key) == null ? null : carrier.getHeaders().get(key).toString()` (handles `String`, `LongString`, and any other AMQP header type defensively).
- `keys(carrier)` returns `carrier.getHeaders().keySet()` — straightforward.
- Add a unit test for the setter + getter round-trip independently of RabbitMQ (no need for a container).

**Warning signs:**
- In RabbitMQ management UI, the `traceparent` header value is displayed as `[B@...` (the `byte[].toString()` signature) or as a hex blob instead of `00-<traceId>-<spanId>-01`.
- Consumer-side debug log shows extracted context equals `Context.root()` — i.e., extraction silently failed.
- Integration test passes locally but fails in CI when message size or character set differs.

**Phase to address:** `step-03-context-propagation`. Include a deliberate "wrong" sub-step (or commented-out wrong code) showing `byte[]` failing, then show the fix — this is high-value teaching material.

---

### Pitfall 3: `service.name` not set → all telemetry shows up as `unknown_service:java`

**Severity:** CRITICAL — makes Grafana unusable as a teaching tool

**What goes wrong:**
The developer builds an `OpenTelemetrySdk` without supplying a `Resource`, or supplies `Resource.getDefault()` only. The OTel SDK falls back to the default `service.name` value of `unknown_service:java`. In Grafana Tempo and Mimir, both the producer and consumer services appear under the same name; the service map is meaningless; metrics are merged.

**Why it happens:**
- The SDK doesn't fail or warn when `service.name` is missing.
- Developers learn from snippets that omit Resource for brevity.
- `Resource.getDefault()` *looks* like it should be enough.

**How to avoid:**
- Each service builds: `Resource.getDefault().merge(Resource.create(Attributes.builder().put(ServiceAttributes.SERVICE_NAME, "order-producer").put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo").put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString()).put(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop").build()))` and passes it to `SdkTracerProvider.builder().setResource(...)`, `SdkMeterProvider.builder().setResource(...)`, `SdkLoggerProvider.builder().setResource(...)`.
- Use the `io.opentelemetry.semconv:opentelemetry-semconv` artifact for the attribute key constants (avoids string literal typos).
- Alternatively, set `OTEL_SERVICE_NAME=order-producer` as an environment variable — but the workshop should teach the programmatic path because that's what attendees will need to write in their own services.

**Warning signs:**
- Grafana Tempo "Search" → Service dropdown shows only `unknown_service:java`.
- Mimir metrics like `http_server_request_duration_seconds_count` have no `service_name` label or have `unknown_service:java`.
- Attendees ask "why are both services showing up under the same name?".

**Phase to address:** `step-02-traces` (the FIRST step that registers the SDK must set the resource correctly — getting this wrong taints every subsequent step).

---

### Pitfall 4: BatchSpanProcessor / BatchLogRecordProcessor lose telemetry on JVM shutdown

**Severity:** CRITICAL for tests; HIGH for the demo (manual `Ctrl+C` may flush, but `mvn test` does not)

**What goes wrong:**
By default `BatchSpanProcessor` schedules an export every 5 seconds and on a queue-full event, with a `MaxQueueSize` of 2048 and an `ExporterTimeout` of 30 seconds. If the JVM exits (test ends, `mise run dev` is interrupted, container stops) before the next scheduled flush, queued spans/log records are dropped. In a Testcontainers test that publishes a message and immediately asserts, the `traceId` may have made it to the in-memory exporter but the OTLP exporter to `otel-lgtm` will silently drop pending data when the JUnit JVM exits.

**Why it happens:**
- Default `BatchSpanProcessor` config is tuned for long-running services, not short-lived tests or demos.
- `OpenTelemetrySdk` implements `AutoCloseable` but Spring Boot does not register it as a bean by default when built manually — the developer must wire shutdown explicitly.
- Symptom is intermittent — long-running curl tests usually flush in time, fast Testcontainers tests don't.

**How to avoid:**
- Register a JVM shutdown hook OR (preferred for Spring) register the `OpenTelemetrySdk` as a `@Bean` with `destroyMethod = "close"`, which calls `shutdown()` on tracer/meter/logger providers — these block until the in-flight batch is flushed (with a timeout).
- For tests, call `openTelemetry.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS)` (and equivalent for meter/logger providers) before assertions, OR use a `SimpleSpanProcessor` in tests (synchronous, exports per span — never batches).
- If using `InMemorySpanExporter` in tests, prefer `SimpleSpanProcessor` for determinism — the Batch processor's timing makes assertions flaky.

**Warning signs:**
- Grafana shows ~80–90% of traces, never 100%.
- Test passes locally on a slow run but fails in CI.
- Last few seconds of a `dev` session are missing in Tempo.
- Log lines printed at app shutdown never appear in Loki.

**Phase to address:** `step-02-traces` (introduce shutdown hook/bean), reinforced in `step-06-tests` (use `SimpleSpanProcessor` + `forceFlush` in test config).

---

### Pitfall 5: `OpenTelemetryAppender` initialised before the SDK is ready → trace IDs missing from logs / logs not exported

**Severity:** CRITICAL for the logs lesson

**What goes wrong:**
Logback initializes very early in Spring Boot startup — before the `@Configuration` class that builds the `OpenTelemetrySdk` runs. If `logback-spring.xml` references `<appender class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">` but `OpenTelemetryAppender.install(openTelemetry)` is never called (or is called too late), the appender holds a no-op `OpenTelemetry` reference. Result: log records are silently not exported, and `trace_id` / `span_id` fields in the log are blank or absent. Logs from BEFORE the SDK is registered are permanently lost.

**Why it happens:**
- `OpenTelemetryAppender` defaults to `OpenTelemetry.noop()` until `install(...)` is called explicitly.
- Spring Boot starts logging immediately; the SDK bean is built later in the lifecycle.
- This is a documented quirk: see [open-telemetry/opentelemetry-java-instrumentation#10307](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307).

**How to avoid:**
- After building the `OpenTelemetrySdk` bean, call `OpenTelemetryAppender.install(openTelemetry)` in a `@PostConstruct` method or via an `InitializingBean`. The `OpenTelemetry` instance must already be fully constructed (tracer + logger provider + exporter all wired).
- Accept that startup-phase logs (before the SDK is registered) won't have trace IDs and won't be OTLP-exported. This is a teaching moment, not a flaw.
- Use the `opentelemetry-logback-mdc-1.0` appender (or a custom MDC injector) to populate `trace_id`, `span_id`, `trace_flags` keys in the MDC, then reference them in the layout pattern: `%X{trace_id}` etc. This is independent of the OTLP appender — it's about console formatting.
- In `logback-spring.xml`, wire the `OpenTelemetryAppender` so it forwards to `CONSOLE` AND exports via OTLP. Make sure `<root>` has `<appender-ref>` to it.

**Warning signs:**
- `trace_id=` is empty in console output even when a trace is active (verify by adding `LOG.info("...")` inside an HTTP handler).
- Loki shows fewer log lines than `mise run dev` printed to stdout.
- Logs from `main` thread / startup never appear in Loki (expected — but make sure attendees know).

**Phase to address:** `step-05-logs`. Test: assert that a log line emitted from inside an HTTP handler shows up in Loki with the same `trace_id` as the active span.

---

### Pitfall 6: Sampler defaults to `AlwaysOn` in workshop, hiding the production reality of `ParentBased(TraceIdRatioBased)`

**Severity:** HIGH (learning anti-pattern — attendees take "AlwaysOn" home and apply it to production at scale)

**What goes wrong:**
The default `Sampler` in the OTel SDK is `ParentBased(AlwaysOn)`, which captures every trace. This is fine for the demo, but if the workshop never mentions sampling, attendees will deploy services that emit 100% of traces in production. At any meaningful scale this overwhelms the backend, costs money, and gets sampling enabled by an SRE without the dev understanding why. They also won't understand the consistency property of `TraceIdRatioBased` (same decision across all services in a trace because it hashes the traceId).

**Why it happens:**
- Workshop demos almost always use `AlwaysOn` for visibility.
- `ParentBased(TraceIdRatioBased)` is more nuanced — it requires explaining why parent-based matters.
- It's tempting to skip this as "not the headline lesson".

**How to avoid:**
- In `step-02-traces`, configure `AlwaysOn` explicitly (don't rely on defaults — it teaches that sampling IS configurable).
- Add a brief callout or optional step: "to switch to production-style 10% sampling, replace with `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))`".
- Mention the `OTEL_TRACES_SAMPLER=parentbased_traceidratio` + `OTEL_TRACES_SAMPLER_ARG=0.1` env-var path as the production knob.
- Briefly explain why ParentBased matters: if the producer samples a trace OUT, the consumer must sample it OUT too (otherwise you get half-traces). The propagated `traceparent` carries a sampling flag in the last byte; ParentBased respects it.

**Warning signs:**
- README never mentions sampling.
- Attendees ask "is OTel really going to send every span to Tempo in production?".

**Phase to address:** `step-02-traces` — set sampler explicitly with a comment about production tradeoffs. Optional callout in README.

---

### Pitfall 7: `@RabbitListener` runs on a different thread → context lost unless extracted from message headers

**Severity:** HIGH — closely related to Pitfall 1 but distinct in mechanism

**What goes wrong:**
`SimpleMessageListenerContainer` (the default backing `@RabbitListener`) uses a `SimpleAsyncTaskExecutor` by default, which spawns a new thread per delivery. The `Context.current()` on that listener thread is `Context.root()` — there is no parent context from the publishing thread, even within the same JVM. Developers familiar with Spring MVC's request-scoped beans assume context "just works" and try to read `Span.current()` inside the listener, getting `Span.getInvalid()`.

**Why it happens:**
- Default Spring MVC instrumentation feels magical; developers assume `@RabbitListener` is similar.
- The listener container thread pool is invisible — there's no `@Async` annotation tipping them off.
- The fact that `SimpleAsyncTaskExecutor` creates a NEW thread per message (not pooled) means there's not even a chance of accidental ThreadLocal leakage from a prior listener invocation.

**How to avoid:**
- Always extract the context from the message in the listener — don't rely on `Context.current()` having anything useful.
- Pattern: `Context extracted = propagator.extract(Context.root(), messageProperties, getter); try (Scope s = extracted.makeCurrent()) { Span span = tracer.spanBuilder("process order").setSpanKind(SpanKind.CONSUMER).startSpan(); try (Scope s2 = span.makeCurrent()) { ... } finally { span.end(); } }`.
- Note that even `Context.root()` is the right starting point on the listener thread — it has no parent.
- Document that this also fixes the related issue where exceptions thrown from `@RabbitListener` lose trace context before reaching `ConditionalRejectingErrorHandler`. ([spring-amqp#1306](https://github.com/spring-projects/spring-amqp/issues/1306))

**Warning signs:**
- Inside a listener, `Span.current().getSpanContext().isValid()` returns false.
- Listener-side spans have no parent even though the producer side does and propagation appears correct.

**Phase to address:** `step-03-context-propagation` — the listener-side extraction is the second half of the propagation lesson, equally important as the producer-side injection.

---

### Pitfall 8: OTLP exporter port confusion — gRPC (4317) vs HTTP/protobuf (4318) → connection refused / 404

**Severity:** HIGH — first-touch failure that blocks attendees before they see any telemetry

**What goes wrong:**
`grafana/otel-lgtm` exposes BOTH OTLP gRPC on 4317 and OTLP HTTP on 4318. The `io.opentelemetry:opentelemetry-exporter-otlp` artifact provides both `OtlpGrpcSpanExporter` (default sender: okhttp, fine for gRPC over HTTP/2) and `OtlpHttpSpanExporter`. Mismatching exporter to port produces:
- gRPC exporter → port 4318: gRPC handshake fails, may show as "connection reset" or `UNAVAILABLE` after a long timeout.
- HTTP exporter → port 4317: 404 or "stream closed" because the gRPC server doesn't speak HTTP/1.1.
- Endpoint URL with wrong scheme: `http://localhost:4317` for gRPC silently works (gRPC ignores the scheme), but `http://localhost:4318/v1/traces` for HTTP MUST include the path.
- Per `PROJECT.md`, the workshop pins **gRPC** ("OTLP gRPC exporters for traces, metrics, and logs").

**Why it happens:**
- Two protocols, two ports, similar artifact names.
- `OtlpGrpcSpanExporter` and `OtlpHttpSpanExporter` look interchangeable in code.
- Documentation often mixes the two without highlighting the difference.

**How to avoid:**
- Use `OtlpGrpcSpanExporter.builder().setEndpoint("http://localhost:4317").build()` (no path; the `http://` is fine for plaintext gRPC).
- Pin the OTLP sender explicitly. Default sender is `opentelemetry-exporter-sender-okhttp`; for pure JDK 17 deployments without okhttp/Kotlin transitive deps, swap to `opentelemetry-exporter-sender-jdk` (HTTP) or `opentelemetry-exporter-sender-grpc-managed-channel` + `grpc-netty-shaded` (gRPC).
- Centralize the endpoint as `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` env var; let the SDK resolve it.
- In the demo `docker-compose.yml`, expose ONLY the two ports the apps need (4317 for OTLP, 3000 for Grafana UI). Hide 4318 if unused to remove ambiguity.

**Warning signs:**
- Apps log `Failed to export spans. The request could not be executed. Full error message: ...` repeatedly.
- Grafana Tempo "Search" returns no data.
- `nc -z localhost 4317` succeeds but exports still fail (= protocol mismatch, not network).

**Phase to address:** `step-02-traces` — first checkpoint that exports anything must verify the path end-to-end (publish → wait → query Tempo via API in a smoke test).

---

### Pitfall 9: OTel BOM not used → transitive version drift → `NoSuchMethodError` / `LinkageError` at runtime

**Severity:** HIGH

**What goes wrong:**
The OTel ecosystem ships ~30 artifacts (api, sdk, sdk-trace, sdk-metrics, sdk-logs, exporter-otlp, exporter-sender-okhttp, instrumentation-logback-appender-1.0, instrumentation-logback-mdc-1.0, semconv, etc.). Spring Boot 3.4.13 transitively pulls in `micrometer-tracing-bridge-otel` which itself depends on a specific OTel version. If the developer pins each artifact to its current version manually, Maven's nearest-wins rule produces a Frankenstein classpath. Symptoms: `NoSuchMethodError` for SDK methods that the API thought existed, or `IncompatibleClassChangeError`.

A documented case: Spring Boot 3.3.5 pinned `opentelemetry-bom 1.37.0` while `micrometer-tracing 1.3.5` needed `1.38.0` — workaround is to override the BOM version. ([spring-boot#43200](https://github.com/spring-projects/spring-boot/issues/43200))

**Why it happens:**
- BOMs are imported in the wrong order in Maven (matters for Maven, not Gradle).
- Developers add OTel dependencies one at a time without pinning a coherent set.
- Spring Boot's own BOM may pin an older OTel; the OTel BOM should override it.

**How to avoid:**
- In `pom.xml`'s `<dependencyManagement>`, import `io.opentelemetry:opentelemetry-bom` **before** `spring-boot-dependencies` (Maven order matters).
- Also import `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha` for the appender artifacts (`-alpha` suffix because they're under Instrumentation BOM, not the core OTel BOM).
- Declare individual dependencies WITHOUT `<version>` — the BOM provides them.
- Run `mvn dependency:tree -Dincludes=io.opentelemetry` before each step's commit; verify all versions match.
- Add `maven-enforcer-plugin` with `dependencyConvergence` rule to catch conflicts at build time.

**Warning signs:**
- `mvn dependency:tree` shows two different versions of `opentelemetry-api`.
- Runtime: `java.lang.NoSuchMethodError: 'io.opentelemetry.api.common.AttributeKey ...'`.
- Build succeeds but tests fail with class-not-found errors only on certain JVMs.

**Phase to address:** `step-00-bootstrap` (Maven scaffold). The first commit must have the BOM imports correct — every subsequent step assumes them.

---

## Moderate Pitfalls

### Pitfall 10: `CompletableFuture` / async controllers lose context — even if the workshop doesn't use async, users will

**Severity:** MEDIUM (out of scope for the demo, but high-value mention)

**What goes wrong:**
If the demo or attendees later add a `@GetMapping` returning `CompletableFuture<...>`, OR if listener business logic does `someExecutor.submit(...)`, the OTel `Context` does not propagate across the executor boundary by default — it's stored in a `ThreadLocal`. The downstream span has no parent.

**Why it happens:** `CompletableFuture` defaults to `ForkJoinPool.commonPool()`, where ThreadLocals don't transfer.

**How to avoid:**
- Wrap executors with `Context.taskWrapping(executor)` (returns an executor that captures and restores the current Context per task).
- For one-off tasks: capture context with `Context current = Context.current();` then in the task body `try (Scope s = current.makeCurrent()) { ... }`.

**Warning signs:** Async-handler spans have no parent.

**Phase to address:** Don't add async to the demo (PROJECT.md: order processing is "simulated in memory" and synchronous). Mention this as a callout in `step-03-context-propagation` README so attendees know to look it up when they add async at home.

---

### Pitfall 11: `BatchSpanProcessor` with `SimpleSpanProcessor` confusion in tests

**Severity:** MEDIUM

**What goes wrong:**
Workshop tests use the same SDK config as production (`BatchSpanProcessor` + OTLP exporter). Assertions race against the 5-second batch timer. Tests are flaky.

**Why it happens:** Copy-pasting production config into tests.

**How to avoid:** In `@TestConfiguration`, override the SDK to use `SimpleSpanProcessor` + `InMemorySpanExporter`. Provide a `forceFlush()` helper for tests that need both.

**Warning signs:** Test passes 9/10 times; CI is flaky; `Thread.sleep(6000)` appears in tests.

**Phase to address:** `step-06-tests`.

---

### Pitfall 12: Spring AMQP `Jackson2JsonMessageConverter` quirks when wrapping with custom `MessagePostProcessor`

**Severity:** MEDIUM

**What goes wrong:**
When using `Jackson2JsonMessageConverter`, the converter sets `content_type` to `application/json` and may add `__TypeId__` headers. If the developer implements tracing as a wrapper around `convertAndSend` that builds its OWN `Message` with `MessageProperties`, they accidentally bypass the converter and send a raw byte body. Symptom: consumer-side `Jackson2JsonMessageConverter.fromMessage(...)` fails with `MessageConversionException: Could not resolve message converter`. Note: `Jackson2JsonMessageConverter` is **deprecated as of Spring AMQP 4.0** in favor of `JacksonJsonMessageConverter` (Jackson 3) — Spring Boot 3.4.x still ships the Jackson 2 version, so this is the relevant one for the demo.

**Why it happens:**
- Mental model conflates "tracing wraps the publish" with "tracing replaces the publish".
- The cleanest tracing hook is a `MessagePostProcessor` (operates on an already-converted `Message`), not a `ChannelAwareMessageListener`-style wrap.

**How to avoid:**
- Inject context via a `MessagePostProcessor` registered on `RabbitTemplate.setBeforePublishPostProcessors(...)` — converter has already run, message bytes and `content_type` are correct, you just add headers.
- On the listener side, accept the deserialized POJO via `@RabbitListener` method parameter (let the converter run); access `MessageProperties` via `@Headers Map<String, Object> headers` parameter or the `Message` parameter alongside the payload.

**Warning signs:**
- Consumer fails to deserialize (`MessageConversionException`).
- Consumer receives `byte[]` instead of `OrderCreated`.
- `__TypeId__` header missing from messages.

**Phase to address:** `step-03-context-propagation`. Ensure the chosen tracing hook is `MessagePostProcessor`, not a converter replacement.

---

### Pitfall 13: Testcontainers `@ServiceConnection` requires correct module + Spring Boot version coupling

**Severity:** MEDIUM

**What goes wrong:**
`@ServiceConnection` on `RabbitMQContainer` is supported since Spring Boot 3.1 — Spring Boot 3.4.x supports it. But it requires:
- `org.testcontainers:rabbitmq` Testcontainers module (NOT just the core `testcontainers` artifact).
- `org.springframework.boot:spring-boot-testcontainers` artifact.
- `RabbitMQContainer` (typed) — `GenericContainer<>("rabbitmq:...")` will NOT work with `@ServiceConnection`.

If any of these are missing, the test silently falls back to the application's `application.properties` connection settings (= localhost:5672 = host RabbitMQ from `docker-compose`), and the test container is started but unused. Tests pass against the wrong Rabbit.

**Why it happens:**
- `@ServiceConnection` matches by container type at runtime — there's no compile-time check.
- Workshop infra has BOTH a host-port Rabbit (for `mise run dev`) and a Testcontainers Rabbit (for `mvn test`); they can collide.

**How to avoid:**
- Use `org.testcontainers:rabbitmq` and `RabbitMQContainer`.
- In tests, override `spring.rabbitmq.host` via `@ServiceConnection` ONLY — do not also `@DynamicPropertySource` it (would race).
- Verify in test logs that the test connects to a non-default port (`localhost:<random>` not `localhost:5672`).
- Optional: use the `@TestConfiguration` + `@Import` pattern to make the wiring explicit and visible to the workshop attendee (pedagogically clearer than the magic of `@ServiceConnection`).

**Warning signs:**
- Test fails when host RabbitMQ is down even though it's "supposed to use Testcontainers".
- Test logs show `localhost:5672` instead of `localhost:<random-port>`.
- Two queues named identically appear in dev RabbitMQ after running tests (= test polluted the dev broker).

**Phase to address:** `step-06-tests`.

---

### Pitfall 14: `otel-lgtm` port collisions on developer laptops

**Severity:** MEDIUM (first-touch friction)

**What goes wrong:**
`grafana/otel-lgtm` exposes 3000 (Grafana), 4317 (OTLP gRPC), 4318 (OTLP HTTP), 9090 (Prometheus), 3100 (Loki), 3200 (Tempo). On a typical engineer laptop:
- Port 3000 collides with: any local React/Next.js dev server, Grafana installs, every "Hello World" tutorial.
- Port 9090 collides with: existing Prometheus installs.
- Ports 3100 / 3200 / 4317 / 4318 rarely collide but are not zero-risk.

Mapping the host port differently (`-p 3001:3000`) is broken: the Grafana UI inside the container has `root_url` configured for port 3000, and reverse-proxying paths break. ([docker-otel-lgtm#461](https://github.com/grafana/docker-otel-lgtm/issues/461))

**Why it happens:** Default ports were chosen for the official Grafana product; otel-lgtm inherits them.

**How to avoid:**
- Workshop README should call out: "if port 3000 is in use, identify and stop the conflicting process before starting; remapping the port does not fully work".
- `docker-compose.yml` should expose ports under explicit names with comments.
- Pre-flight `mise` task: `mise run preflight` that checks `ss -tln` / `lsof -i:3000` etc., warns the attendee.

**Warning signs:**
- `docker compose up` fails: "bind: address already in use".
- Grafana loads but datasources show "Bad Gateway".
- OTLP exports get `connection refused`.

**Phase to address:** `step-00-bootstrap` (docker-compose). README "Prerequisites" section.

---

### Pitfall 15: mise + IntelliJ — JDK not auto-detected → IDE compiles with wrong Java version

**Severity:** MEDIUM (every new attendee hits this once)

**What goes wrong:**
`mise.toml` declares Corretto 17. Shell-side `mise run dev` works. IntelliJ opens the project, doesn't see `mise` (which lives in the user's shell PATH activated by `mise activate`), falls back to the system JDK (often Java 21 or 11). Compilation succeeds because Java 17 source is forward-compatible, but tests use APIs that don't exist in the IDE's JDK or class files target the wrong version.

JetBrains IDEs DO auto-read `.tool-versions` (asdf-compatible) for SDK detection. mise can produce a `.tool-versions` file alongside `mise.toml`, OR the JetBrains "Mise" plugin (Marketplace) can read `mise.toml` directly.

**Why it happens:**
- `mise activate` is per-shell; IDE doesn't run shell init.
- IntelliJ's PATH can be configured to inherit from login shell, but it's off by default on macOS.

**How to avoid:**
- Workshop README provides: "Install the JetBrains Mise plugin OR run `mise generate tool-versions > .tool-versions` to create an asdf-compatible file the IDE can read".
- Optionally commit `.tool-versions` alongside `mise.toml` (mise reads either; IDE prefers `.tool-versions`).
- `.idea/.gitignore` should NOT ignore `.idea/misc.xml` if the project commits a JDK pointer; alternatively keep `.idea/` out of git entirely (preferred for portable workshop repos).
- Document fallback: "Project Structure → Project SDK → Add SDK → JDK Home: `~/.local/share/mise/installs/java/corretto-17.0.x`".

**Warning signs:**
- `mvn -version` (in IDE terminal) shows Java 21 even though `mise current` shows 17.
- IntelliJ red-underlines `java.lang.foreign.MemorySegment` (or any 21-specific API) but `mise run test` passes.

**Phase to address:** `step-00-bootstrap`. README "Prerequisites" section.

---

### Pitfall 16: GraalVM native-image traps in OTel SDK code (out of scope but the demo should not paint itself into a corner)

**Severity:** MEDIUM (constraint, not active pitfall)

**What goes wrong:**
The demo is JVM-only per `PROJECT.md`. But idioms that work fine on the JVM and break under `native-image` (reflection on private fields, `Class.forName` of dynamically-named classes, classpath-scanning for `META-INF/services`) leak into demo code by accident. A reader who later wants to native-compile their adaptation hits walls.

**How to avoid:**
- Don't use `Class.forName(...)` to dynamically load OTel components — use the explicit builder API.
- Don't write reflection-based span attribute extractors in the demo.
- The OTel SDK itself has good native-image support; the workshop's manual SDK code should be written with explicit constructors / builders.

**Warning signs:**
- Reflective code in the demo's tracing utilities.
- `META-INF/services/...` files for SPI lookup that won't survive AOT.

**Phase to address:** Code review at every step. Not a step in itself.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Use `AlwaysOnSampler` and never mention production sampling | Workshop is simpler | Attendees take this home and emit 100% of traces in prod | Acceptable in the demo body; **never** without a README callout |
| Use `BatchSpanProcessor` defaults in tests | "It's like prod" | Flaky tests, `Thread.sleep` infections | Never — tests should use `SimpleSpanProcessor` |
| Hardcode OTLP endpoint as `http://localhost:4317` | Runs on the laptop | Doesn't transfer to environments where the endpoint differs | Acceptable for the workshop; but document `OTEL_EXPORTER_OTLP_ENDPOINT` override |
| Skip `service.namespace` and `service.instance.id`, only set `service.name` | One less line | At even slight scale, can't distinguish replicas | Only acceptable if the workshop has exactly two services and one instance each |
| `Resource.getDefault()` only (no merge) | Concise | All telemetry tagged `unknown_service:java`, demo unusable | **Never** — always set service name |
| Skip the `opentelemetry-bom` import, pin each artifact manually | "I know what I'm pulling in" | Version drift on every Spring Boot upgrade | **Never** for OTel — always use the BOM |
| Use `GenericContainer` for RabbitMQ in tests instead of `RabbitMQContainer` | "It's just a container" | `@ServiceConnection` won't work; bespoke port wiring needed | **Never** — typed containers exist for a reason |
| Inline the `TextMapSetter`/`Getter` lambdas | Less code | Can't unit-test the propagation step in isolation | Acceptable for terse examples; for the workshop, extract to named classes for clarity |
| Skip `MessageConverter` configuration, send raw `byte[]` bodies | Less moving parts | Doesn't reflect real Spring AMQP usage; converter pitfalls invisible | **Never** for a workshop — JSON converter is the realistic case |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Spring AMQP → RabbitMQ | Setting `traceparent` header as `byte[]`, gets returned as `LongString` on consumer | Set as `String`; in getter, normalize via `.toString()` |
| OTLP gRPC exporter → otel-lgtm | Pointing at port 4318 (HTTP path) | Use port 4317 with `OtlpGrpcSpanExporter`; or use `OtlpHttpSpanExporter` with port 4318 + `/v1/traces` path |
| OpenTelemetryAppender → Logback | Configuring in `logback.xml` and expecting it to install itself | Call `OpenTelemetryAppender.install(openTelemetry)` in `@PostConstruct` after SDK is built |
| Testcontainers RabbitMQContainer → Spring Boot | Using `GenericContainer` + manual `@DynamicPropertySource` | `RabbitMQContainer` + `@ServiceConnection` |
| `@RabbitListener` → tracer | Reading `Span.current()` inside the listener method | Extract context from `MessageProperties` first, `makeCurrent()`, then start span |
| RabbitTemplate publish hook → propagator | Subclassing `RabbitTemplate` or replacing `MessageConverter` | Register a `MessagePostProcessor` via `setBeforePublishPostProcessors(...)` |
| OTel BOM → Spring Boot BOM | Importing `spring-boot-dependencies` first, then OTel BOM | Import OTel BOM **first** in Maven (Gradle order doesn't matter) |
| mise → IntelliJ | Relying on `mise activate` to be picked up by the IDE | Commit `.tool-versions` alongside `mise.toml`, OR install JetBrains Mise plugin |
| docker-compose ports → host | Remapping otel-lgtm's port 3000 to 3001 to avoid collision | Remapping doesn't fully work; identify and free port 3000 before `compose up` |
| InMemorySpanExporter assertions → `BatchSpanProcessor` | Asserting before flush completes | Use `SimpleSpanProcessor` in tests, OR call `forceFlush().join(...)` before assertions |

---

## Performance Traps

(Demo is single-laptop; production-scale traps are noted for completeness only.)

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| `AlwaysOn` sampler in production | Backend overwhelmed, costs spike | `Sampler.parentBased(Sampler.traceIdRatioBased(0.05))` for 5% baseline | At ~100 RPS sustained |
| `SimpleSpanProcessor` in production | Synchronous export blocks request threads | `BatchSpanProcessor` only in production | At any sustained throughput |
| Span attributes with high cardinality (e.g., user IDs as attribute values) | Tempo index explodes; metrics derived from spans cardinality-blow | Use baggage for tracing-scoped values; keep attribute values bounded | At ~10k unique values per attribute |
| `Resource.create()` re-evaluated per span | CPU overhead on hot path | Build the `Resource` once at startup, share | At any meaningful throughput (this is a code smell, not a real trap if SDK is wired properly) |
| BatchSpanProcessor `MaxQueueSize` of 2048 too small for burst traffic | OTel-internal metric `otel.batch_span_processor.dropped_spans` ticks up | Increase `MaxQueueSize` or `MaxExportBatchSize` (defaults 2048 / 512) | Burst traffic > 400 spans/sec |

---

## Security Mistakes

(Demo is intentionally insecure per `PROJECT.md`: no auth on the HTTP API. These are listed for completeness — the workshop should briefly mention them so attendees know what's missing.)

| Mistake | Risk | Prevention |
|---------|------|------------|
| Logging full request body via OTel attributes | PII leak into Tempo / Loki | Sanitize / hash attribute values; use `SpanProcessor` that strips fields |
| Including secrets in baggage | Baggage propagates to ALL downstream services across W3C TraceContext-aware boundaries | Never put secrets in baggage; treat baggage as broadcast |
| OTLP endpoint over plain HTTP/gRPC in production | Telemetry data on the wire unencrypted; could leak business data | Use TLS in production; demo's HTTP-only is fine for localhost |
| Demo's RabbitMQ has default `guest:guest` credentials | If exposed beyond localhost, trivially accessible | Workshop's docker-compose binds Rabbit to `127.0.0.1` only; explicit in README |

---

## UX Pitfalls (Workshop UX)

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Each `step-XX` branch requires a fresh `mvn package` (no cached artifacts) | Long wait between checkpoints | Each step's README pre-warns about build time; consider `mvn -pl ... -am` partial builds |
| `git checkout step-04-metrics` doesn't restart the running app | Attendee sees stale code in browser, thinks step is broken | README explicit: every step starts with "stop the running app, checkout, restart" |
| Grafana datasources not pre-wired in `otel-lgtm` | Attendee opens Grafana, sees empty dashboard, thinks it's broken | `otel-lgtm` provisions Tempo/Mimir/Loki datasources by default — verify and document |
| Curl examples return JSON without trace IDs | Attendee can't easily correlate request → trace | Add `-v` flag examples; show `traceparent` header in response if echoed |
| Workshop assumes attendees have Docker running | First-time attendees on Linux without Docker fail at `step-00-bootstrap` | Prerequisites section lists Docker, mise, plus expected versions |
| README has 8 steps, attendee skips step-3 because "it's just propagation" | They miss the headline lesson, demo "doesn't work" for them | Each step's README starts with "you must complete the previous step" + a self-check |

---

## "Looks Done But Isn't" Checklist

- [ ] **Trace propagation across AMQP:** verify `traceparent` header is present in RabbitMQ Management UI (`http://localhost:15672` → Queues → Get Message)
- [ ] **Trace propagation across AMQP:** verify producer span and consumer span share a `traceId` in Tempo
- [ ] **Trace propagation across AMQP:** verify consumer span has `parentSpanId` pointing at the producer span
- [ ] **Trace propagation across AMQP:** verify producer has `SpanKind.PRODUCER`, consumer has `SpanKind.CONSUMER`
- [ ] **Service identity:** Grafana service dropdown lists `order-producer` and `order-consumer` (NOT `unknown_service:java`)
- [ ] **Logs export:** Loki returns log lines with `trace_id` matching the trace, queried via `{service_name="order-producer"} |~ "<traceId>"`
- [ ] **Metrics export:** Mimir has `http_server_request_duration_seconds_count` with non-empty `service_name` label
- [ ] **Shutdown flush:** `Ctrl+C` on the producer mid-request still produces a complete trace in Tempo (not 80% of one)
- [ ] **Shutdown flush:** `mvn test` produces traces in `InMemorySpanExporter` (test config uses `SimpleSpanProcessor`)
- [ ] **Sampler:** `Sampler` is set explicitly (not relying on default), and the README mentions production sampling
- [ ] **Resource attributes:** `service.name`, `service.namespace`, `service.instance.id`, `deployment.environment.name` all set
- [ ] **OTel BOM:** `mvn dependency:tree -Dincludes=io.opentelemetry` shows ONE version for every artifact
- [ ] **OTLP port:** `OtlpGrpcSpanExporter` points at `:4317`, NOT `:4318`
- [ ] **Logback appender:** `OpenTelemetryAppender.install(openTelemetry)` is called explicitly after the SDK is built
- [ ] **Logback MDC:** Console output shows `trace_id=<32 hex chars>` for log lines emitted inside an active span
- [ ] **Propagators:** `W3CTraceContextPropagator` AND `W3CBaggagePropagator` registered (composite)
- [ ] **Header type:** Setter writes `String` to `MessageProperties.setHeader`; getter normalizes via `.toString()`
- [ ] **Listener thread:** `@RabbitListener` extracts context from message headers, doesn't rely on `Context.current()`
- [ ] **Testcontainers:** `RabbitMQContainer` (typed) + `@ServiceConnection`; tests use a non-default random port
- [ ] **Workshop UX:** `git checkout step-XX-NAME` works for every staged step; README per-step explains what changed
- [ ] **Pre-flight:** `mise run preflight` checks Docker, port 3000/4317, mise tools, Java 17 active
- [ ] **mise + IDE:** `.tool-versions` (or JetBrains Mise plugin) ensures IntelliJ uses Corretto 17

---

## Recovery Strategies

When a pitfall occurs despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| AMQP propagation broken (Pitfall 1) | LOW | Check `RabbitTemplate` has the post-processor registered; check `MessageProperties.getHeaders()` actually contains `traceparent` after publish; fix setter/getter |
| Header byte[] vs String (Pitfall 2) | LOW | Change setter to write `String`; change getter to `.toString()` whatever it gets |
| `unknown_service:java` (Pitfall 3) | LOW | Add `Resource.create(...)` with `service.name`; redeploy |
| Telemetry dropped on shutdown (Pitfall 4) | LOW | Register `OpenTelemetrySdk` as bean with `destroyMethod="close"`; or add JVM shutdown hook |
| Logback appender not installed (Pitfall 5) | LOW | Add `@PostConstruct` calling `OpenTelemetryAppender.install(...)` |
| BOM version conflict (Pitfall 9) | MEDIUM | `mvn dependency:tree`; reorder BOMs; pin OTel BOM first |
| `@ServiceConnection` not picked up (Pitfall 13) | MEDIUM | Verify `RabbitMQContainer` (typed); verify Testcontainers `rabbitmq` module on classpath; verify `spring-boot-testcontainers` |
| Port 3000 collision (Pitfall 14) | LOW | `lsof -i:3000` → kill conflicting process; or change Grafana port (with caveats) |
| mise + IDE confusion (Pitfall 15) | LOW | Commit `.tool-versions`; install JetBrains Mise plugin; or manually point IDE at `~/.local/share/mise/installs/java/corretto-17.0.x` |
| AMQP message not deserialized (Pitfall 12) | LOW | Use `MessagePostProcessor` instead of replacing `MessageConverter`; verify `content_type` header is `application/json` |

---

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Severity | Prevention Phase | Verification |
|---------|----------|------------------|--------------|
| 1. AMQP propagation broken | CRITICAL | `step-03-context-propagation` | Tempo: producer + consumer share `traceId` AND consumer has `parentSpanId` |
| 2. byte[] vs String headers | CRITICAL | `step-03-context-propagation` | Unit test of setter/getter round-trip; Rabbit Management UI shows readable `traceparent` |
| 3. `unknown_service:java` | CRITICAL | `step-02-traces` | Tempo Service dropdown shows real service names |
| 4. Telemetry lost on shutdown | CRITICAL | `step-02-traces` (shutdown hook) + `step-06-tests` (SimpleSpanProcessor) | Stop producer mid-flight → trace fully present in Tempo |
| 5. OpenTelemetryAppender not installed | CRITICAL | `step-05-logs` | Loki query returns log line with same `trace_id` as the active span |
| 6. Sampler defaults | HIGH | `step-02-traces` (callout) | Sampler set explicitly; README mentions production tradeoff |
| 7. `@RabbitListener` thread context | HIGH | `step-03-context-propagation` | Consumer span has parent set to producer span (= context was extracted, not inherited) |
| 8. OTLP port confusion | HIGH | `step-02-traces` (first export) | App logs no export errors; Tempo Search returns recent traces |
| 9. OTel BOM transitive drift | HIGH | `step-00-bootstrap` | `mvn dependency:tree` shows one version per artifact; `maven-enforcer-plugin` rule |
| 10. CompletableFuture context loss | MEDIUM | Callout in `step-03-context-propagation` README | Not in demo; documented as gotcha |
| 11. SimpleSpanProcessor in tests | MEDIUM | `step-06-tests` | Tests don't use `Thread.sleep` to wait for batches |
| 12. Jackson MessageConverter quirks | MEDIUM | `step-03-context-propagation` | Use `MessagePostProcessor`; consumer deserializes `OrderCreated` POJO successfully |
| 13. `@ServiceConnection` wiring | MEDIUM | `step-06-tests` | Test logs show random port; test passes when host Rabbit is down |
| 14. otel-lgtm port collisions | MEDIUM | `step-00-bootstrap` (preflight + README) | `mise run preflight` passes |
| 15. mise + IntelliJ JDK | MEDIUM | `step-00-bootstrap` | README walkthrough; `.tool-versions` committed |
| 16. native-image traps | MEDIUM | Code review at every step | No reflection / `Class.forName` in demo code |

---

## Sources

### Authoritative / Official

- [OpenTelemetry Java SDK — Manage Telemetry with SDK](https://opentelemetry.io/docs/languages/java/sdk/) — span/log processor configuration, BatchSpanProcessor defaults
- [OpenTelemetry — Configure the SDK (Java)](https://opentelemetry.io/docs/languages/java/configuration/) — Resource, sampler, exporter configuration
- [OpenTelemetry — General SDK Configuration](https://opentelemetry.io/docs/languages/sdk-configuration/general/) — `OTEL_SERVICE_NAME` semantics
- [OpenTelemetry — OTLP Exporter Configuration](https://opentelemetry.io/docs/languages/sdk-configuration/otlp-exporter/) — gRPC vs HTTP, ports 4317/4318
- [OpenTelemetry — OTLP Specification 1.10.0](https://opentelemetry.io/docs/specs/otlp/) — protocol details
- [OpenTelemetry — Resource semantic conventions](https://opentelemetry.io/docs/specs/semconv/resource/) — `service.name`, `service.namespace`, `service.instance.id`
- [OpenTelemetry — Semantic conventions for RabbitMQ](https://opentelemetry.io/docs/specs/semconv/messaging/rabbitmq/) — `messaging.system`, span kind
- [OpenTelemetry — Context propagation](https://opentelemetry.io/docs/concepts/context-propagation/) — TextMap propagator model
- [OpenTelemetry — Propagators API spec](https://opentelemetry.io/docs/specs/otel/context/api-propagators/) — composite propagator, defaults
- [Spring AMQP — Threading and Asynchronous Consumers](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/threading.html) — listener container thread model
- [Spring AMQP — Message Converters](https://docs.spring.io/spring-amqp/reference/amqp/message-converters.html) — Jackson2JsonMessageConverter, content_type
- [Spring AMQP — `MessageProperties` API (4.0.3)](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/core/MessageProperties.html) — header types
- [Spring Boot — Testcontainers reference](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — `@ServiceConnection`
- [Spring Blog — Improved Testcontainers Support in Spring Boot 3.1](https://spring.io/blog/2023/06/23/improved-testcontainers-support-in-spring-boot-3-1/)
- [grafana/docker-otel-lgtm README](https://github.com/grafana/docker-otel-lgtm/blob/main/README.md) — port mappings, defaults
- [Grafana docs — Docker OpenTelemetry LGTM](https://grafana.com/docs/opentelemetry/docker-lgtm/)
- [mise — IDE Integration](https://mise.jdx.dev/ide-integration.html)
- [JetBrains — IntelliJ IDEA SDKs documentation](https://www.jetbrains.com/help/idea/sdk.html) — `.tool-versions` auto-detection

### Issues / Discussions (high-confidence pitfalls confirmed by maintainers and users)

- [opentelemetry-java-instrumentation#10307 — Logback appender needs to be re-initialized after spring application starts](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307)
- [opentelemetry-java-instrumentation#8942 — slf4j (logback) logs not getting forwarded to open-telemetry-collector by OpenTelemetryAppender](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8942)
- [opentelemetry-java-instrumentation#10806 — MDC Instrumentation for logback not injecting trace/span ids](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10806)
- [opentelemetry-java-instrumentation#2822 — Auto-Instrumentation across Message Queue (RabbitMQ): traceId lost in subsequent spans](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2822)
- [opentelemetry-java-instrumentation#3358 — Context propagation lost over CompletableFuture/Promise.async](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3358)
- [opentelemetry-java-instrumentation#7657 — Trace context propagation to CompletableFuture and @Async methods](https://github.com/open-telemetry/opentelemetry-java-instrumentation/discussions/7657)
- [opentelemetry-java#5905 — Maven POM for opentelemetry-exporter-sender-okhttp doesn't retain transitive dependency version overrides from BOMs](https://github.com/open-telemetry/opentelemetry-java/issues/5905)
- [opentelemetry-java#1566 — Leverage BatchSpanProcessor's span dropping functionality](https://github.com/open-telemetry/opentelemetry-java/issues/1566)
- [spring-amqp#2560 — Context being lost between @RabbitListener and Webclient](https://github.com/spring-projects/spring-amqp/issues/2560)
- [spring-amqp#1306 — Trace information is missing when Exception is thrown from RabbitListener methods](https://github.com/spring-projects/spring-amqp/issues/1306)
- [spring-amqp#1731 — Message header value has type ByteArrayLongString](https://github.com/spring-projects/spring-amqp/issues/1731)
- [spring-boot#43200 — Spring Boot 3.3.x dependencies do not converge for Micrometer Tracing and OpenTelemetry](https://github.com/spring-projects/spring-boot/issues/43200)
- [docker-otel-lgtm#461 — Change Grafana UI Port 3000?](https://github.com/grafana/docker-otel-lgtm/issues/461)

### Practitioner write-ups (lower confidence; cross-referenced where claims are load-bearing)

- [Spring Boot Logging with OpenTelemetry: Injecting Trace IDs Using Logback (Medium / Arkadii Osheev)](https://medium.com/@arkadii.osheev.official/spring-boot-logging-with-opentelemetry-injecting-trace-ids-using-logback-ae333016b3b6)
- [Distributed Tracing for RabbitMQ with OpenTelemetry (Medium / Aspecto)](https://medium.com/@team_Aspecto/distributed-tracing-for-rabbitmq-with-opentelemetry-1ebe7457b4c1)
- [OpenTelemetry: Utilizing Context Information Passed To Remote RabbitMQ Services (Craftsman Nadeem)](https://reachmnadeem.wordpress.com/2021/03/02/opentelemetry-utilizing-context-information-passed-to-remote-rabbmitmq-services/)
- [Uptrace — OpenTelemetry Sampling [Java]](https://uptrace.dev/get/opentelemetry-java/sampling)
- [Uptrace — OpenTelemetry Logback logging [Java]](https://uptrace.dev/guides/opentelemetry-logback)
- [Uptrace — OpenTelemetry Trace Context Propagation [Java]](https://uptrace.dev/get/opentelemetry-java/propagation)
- [SoftwareMill — Propagating OpenTelemetry context with Virtual Threads](https://softwaremill.com/propagating-opentelemetry-context-when-using-virtual-threads-and-structured-concurrency/)
- [Last9 — Implement Distributed Tracing with Spring Boot 3](https://last9.io/blog/distributed-tracing-with-spring-boot/)

---
*Pitfalls research for: Manual OpenTelemetry SDK + Spring Boot 3.4.13 + Spring AMQP + Testcontainers workshop*
*Researched: 2026-04-29*
