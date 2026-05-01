# Phase 2 Research — Manual SDK Bootstrap & First Traces

**Researched:** 2026-05-01
**Domain:** OpenTelemetry Java SDK 1.61.0 + semconv-java 1.40.0 + Spring Boot 3.4.13
**Confidence:** HIGH (every API call below verified against tagged source on GitHub)

## Executive Summary

Every API the 16 locked decisions assume is present and behaves as CONTEXT.md describes — with **three drift items** the planner must address: (1) `MessagingAttributes` does not exist as a stable class; messaging keys live in `io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes`. (2) `DeploymentAttributes` does not exist; the constant is `DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME`. (3) The bare key `messaging.operation` (used verbatim in TRACE-07/08) is **deprecated** in semconv 1.40.0 in favor of `messaging.operation.type`; values renamed (`publish` → `send`).

**The biggest planner-side gotcha:** Phase 2 needs a SECOND semconv coordinate. STACK.md only lists `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` (stable). Phase 2 also requires `io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha` — without it the build fails at compile time on the PRODUCER/CONSUMER spans and the Resource builder.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TRACE-01 | Per-service `OtelSdkConfiguration.java` via `OpenTelemetrySdk.builder()` | API verified §A1 |
| TRACE-02 | Resource with service.name/namespace/instance.id/deployment.environment.name | Stable ServiceAttributes (§B); **drift:** deployment in `DeploymentIncubatingAttributes` |
| TRACE-03 | `SdkTracerProvider` + `BatchSpanProcessor` + `OtlpGrpcSpanExporter` → `:4317` + `Sampler.parentBased(alwaysOn())` | §A3, §A4, §A5, §A6 |
| TRACE-04 | `@Bean(destroyMethod = "close")` for graceful shutdown flush | §A7 — `close()` calls `shutdown().join(10s)` cascading to all providers |
| TRACE-05 | SERVER span on `POST /orders` with HTTP semconv attrs | Stable HttpAttributes/UrlAttributes/ServerAttributes (§B) |
| TRACE-06 | INTERNAL span around domain methods | `tracer.spanBuilder(...).setSpanKind(SpanKind.INTERNAL)` (§A1) |
| TRACE-07 | PRODUCER span with messaging.system/destination.name/operation | **Drift:** use `MESSAGING_OPERATION_TYPE` + `SEND` value (§B); see Open Q #1 |
| TRACE-08 | CONSUMER span with messaging.operation=process | Same drift as TRACE-07; use `MessagingOperationTypeIncubatingValues.PROCESS` |
| DOC-03 | Heavy comments in `OtelSdkConfiguration.java` | Pedagogical, no API risk |
| DOC-05 | README callout on per-service duplication | Pedagogical, no API risk |
| WORK-01 | Annotated git tag `step-02-traces` | Tooling-only |

## API Surface Verification

All sources from `open-telemetry/opentelemetry-java` @ tag `v1.61.0` unless noted.

### A1–A2. SDK builder + Tracer bean (D-01, D-02)

Use `OpenTelemetrySdk.builder().setTracerProvider(...).setPropagators(...).build()` — **NOT** `buildAndRegisterGlobal()`. Demo injects `OpenTelemetry`/`Tracer` as Spring beans (D-02), so `GlobalOpenTelemetry` is never read; global registration would complicate Phase 6 test isolation. `OpenTelemetry.getTracer(String)` is thread-safe; safe as singleton bean. **No `@Lazy` needed** — Spring resolves `OpenTelemetry` first; `Tracer` depends on it, not vice versa. **No drift.**

### A3. `SdkTracerProvider.builder()` — Resource setting + merge order

```java
Resource resource = Resource.getDefault()
    .merge(Resource.create(Attributes.builder()
        .put(ServiceAttributes.SERVICE_NAME, "order-producer")
        .put(ServiceAttributes.SERVICE_NAMESPACE, "ose-otel-demo")
        .put(ServiceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
        .put(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, "workshop")
        .build()));
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .setResource(resource)
    .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
    .build();
```

`sdk/common/.../resources/Resource.java`: `Resource.DEFAULT = MANDATORY.merge(TELEMETRY_SDK)` where `MANDATORY = create(Attributes.of(SERVICE_NAME, "unknown_service:java"))`. The `merge(other)` method puts `other` last → custom `service.name` overrides the default. **PITFALLS.md #3 confirmed mitigated.**

### A4. `BatchSpanProcessor.builder()` is current; defaults match D-15

Use `BatchSpanProcessor.builder(spanExporter).build()` (NOT `.create()`). Defaults from `BatchSpanProcessorBuilder.java` v1.61.0: `5000ms` schedule / `2048` max queue / `512` max batch / `30000ms` exporter timeout. **D-15's "default tuning (5s/2048/512)" is exactly right** — do not call `.setScheduleDelay/.setMaxQueueSize/.setMaxExportBatchSize`.

### A5. `OtlpGrpcSpanExporter.setEndpoint(...)` — endpoint string format

```java
OtlpGrpcSpanExporter.builder()
    .setEndpoint("http://localhost:4317")  // MUST start with http:// or https://
    .build();
```

`exporters/otlp/all/.../OtlpGrpcSpanExporterBuilder.java` — Javadoc on `setEndpoint(String)` explicitly states *"endpoint must start with either http:// or https://"*. **Bare `localhost:4317` throws at build time.** Package: `io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter`. **PITFALLS.md #8 mitigated** — and `mise.toml` line 22 already sets `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` correctly.

### A6. `Sampler.parentBased(...)` — single-arg form correct

`sdk/trace/.../samplers/Sampler.java`: `static Sampler alwaysOn()` and `static Sampler parentBased(Sampler root)` both exist as single-arg. The 5-arg `parentBasedBuilder(...)` form exists for advanced cases; **D-14's `Sampler.parentBased(Sampler.alwaysOn())` is correct.** Teaching comment may also reference `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` — both compile.

### A7. `@Bean(destroyMethod = "close")` — graceful shutdown flush (load-bearing for D-15 + PITFALLS.md #4)

`sdk/all/.../OpenTelemetrySdk.java` v1.61.0:
```java
@Override public void close() { shutdown().join(10, TimeUnit.SECONDS); }

public CompletableResultCode shutdown() {
    if (!isShutdown.compareAndSet(false, true)) { ... }
    List<CompletableResultCode> results = new ArrayList<>();
    results.add(tracerProvider.unobfuscate().shutdown());   // tracer cascades to BatchSpanProcessor flush
    results.add(meterProvider.unobfuscate().shutdown());    // no-op in Phase 2
    results.add(loggerProvider.unobfuscate().shutdown());   // no-op in Phase 2
    return CompletableResultCode.ofAll(results);
}
```
`BatchSpanProcessor.shutdown()` delegates to `worker.shutdown()` which forces a final batch flush. **PITFALLS.md #4 fully addressed by `@Bean(destroyMethod="close")` alone** — no explicit `BatchSpanProcessor` shutdown timeout config needed. The 30s exporter timeout sits well inside `close()`'s 10s join window.

### A8. `OncePerRequestFilter` registered as `@Bean` + path exclusion (D-05, D-06)

```java
@Bean
HttpServerSpanFilter httpServerSpanFilter(Tracer tracer) {
    return new HttpServerSpanFilter(tracer);
}
// Inside HttpServerSpanFilter extends OncePerRequestFilter:
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");
}
```

Spring Framework `org.springframework.web.filter.OncePerRequestFilter` exposes `shouldNotFilter(HttpServletRequest)` as the canonical exclusion hook. Spring Boot 3.4 auto-discovers `Filter` beans and wraps them in a default `FilterRegistrationBean(/*)` — no explicit wrapper needed. **Drift / surprise:** D-06 says "filter excludes paths starting with `/actuator/`" — the cleanest implementation is `shouldNotFilter()`, **NOT** `FilterRegistrationBean.setUrlPatterns(...)` which has known issues per [spring-boot#38331](https://github.com/spring-projects/spring-boot/issues/38331). No `@Order` needed (no competing filters).

### A9. Composite propagator construction (D-16)

```java
ContextPropagators propagators = ContextPropagators.create(
    TextMapPropagator.composite(
        W3CTraceContextPropagator.getInstance(),
        W3CBaggagePropagator.getInstance()));
```

`context/.../propagation/TextMapPropagator.java`: `static TextMapPropagator composite(TextMapPropagator... propagators)`. `ContextPropagators.java`: `static ContextPropagators create(TextMapPropagator)`. Both `W3CTraceContextPropagator.getInstance()` (in `io.opentelemetry.api.trace.propagation`) and `W3CBaggagePropagator.getInstance()` (in `io.opentelemetry.api.baggage.propagation`) verified as `public static`. **D-16 is exactly correct.**

### A10. Manual env-var read pattern (D-12)

```java
String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
    .orElse("http://localhost:4317");
```

Pure JDK; no `opentelemetry-sdk-extension-autoconfigure` in Phase 2 POMs. **The planner must REMOVE that artifact from any pasted POM snippet** — STACK.md lists it as a runtime dep, but D-12 explicitly forbids it.

## Semconv 1.40.0 Constant Catalogue

**Two coordinates required** (incubating uses `-alpha` version qualifier):
```xml
<dependency>
  <groupId>io.opentelemetry.semconv</groupId>
  <artifactId>opentelemetry-semconv</artifactId>
  <version>1.40.0</version>
</dependency>
<dependency>
  <groupId>io.opentelemetry.semconv</groupId>
  <artifactId>opentelemetry-semconv-incubating</artifactId>
  <version>1.40.0-alpha</version>
</dependency>
```

| Attribute key | Class | Static field | Import path |
|---|---|---|---|
| `service.name` | `ServiceAttributes` | `SERVICE_NAME` | `io.opentelemetry.semconv.ServiceAttributes` |
| `service.namespace` | `ServiceAttributes` | `SERVICE_NAMESPACE` | `io.opentelemetry.semconv.ServiceAttributes` |
| `service.instance.id` | `ServiceAttributes` | `SERVICE_INSTANCE_ID` | `io.opentelemetry.semconv.ServiceAttributes` |
| `deployment.environment.name` | `DeploymentIncubatingAttributes` | `DEPLOYMENT_ENVIRONMENT_NAME` | `io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes` |
| `http.request.method` | `HttpAttributes` | `HTTP_REQUEST_METHOD` | `io.opentelemetry.semconv.HttpAttributes` |
| `http.response.status_code` | `HttpAttributes` | `HTTP_RESPONSE_STATUS_CODE` | `io.opentelemetry.semconv.HttpAttributes` |
| `http.route` | `HttpAttributes` | `HTTP_ROUTE` | `io.opentelemetry.semconv.HttpAttributes` |
| `url.path` | `UrlAttributes` | `URL_PATH` | `io.opentelemetry.semconv.UrlAttributes` |
| `url.scheme` | `UrlAttributes` | `URL_SCHEME` | `io.opentelemetry.semconv.UrlAttributes` |
| `server.address` | `ServerAttributes` | `SERVER_ADDRESS` | `io.opentelemetry.semconv.ServerAttributes` |
| `server.port` | `ServerAttributes` | `SERVER_PORT` | `io.opentelemetry.semconv.ServerAttributes` |
| `messaging.system` | `MessagingIncubatingAttributes` | `MESSAGING_SYSTEM` | `io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes` |
| `messaging.destination.name` | `MessagingIncubatingAttributes` | `MESSAGING_DESTINATION_NAME` | (same package) |
| `messaging.operation.type` | `MessagingIncubatingAttributes` | `MESSAGING_OPERATION_TYPE` | (same package) |
| `messaging.rabbitmq.destination.routing_key` | `MessagingIncubatingAttributes` | `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY` | (same package) |

**Enum value classes** (use these instead of literal `"rabbitmq"` / `"send"` / `"process"`):

| Value | Class.field |
|---|---|
| `"rabbitmq"` | `MessagingIncubatingAttributes.MessagingSystemIncubatingValues.RABBITMQ` |
| `"send"` (PRODUCER) | `MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND` |
| `"process"` (CONSUMER) | `MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.PROCESS` |

## Pitfall Mitigation Map

| # | What | Phase 2 mitigation | Verified by |
|---|---|---|---|
| #3 | `unknown_service:java` default | `Resource.getDefault().merge(custom)` with `ServiceAttributes.SERVICE_NAME` | §A3 — `MANDATORY.merge(other)` puts `other` last → custom wins |
| #4 | Telemetry lost on shutdown | `@Bean(destroyMethod="close")` on `OpenTelemetrySdk` | §A7 — `close()` → `shutdown().join(10s)` → `BatchSpanProcessor` flush |
| #6 | Sampler default anti-pattern | Explicit `Sampler.parentBased(Sampler.alwaysOn())` + multi-paragraph teaching comment showing `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` for prod | §A6 — both forms compile |
| #7 | Listener-thread context loss | CONSUMER span starts from `Context.root()` with comment previewing Phase 3 `propagator.extract(...)` | API: `tracer.spanBuilder(...).setParent(Context.root()).setSpanKind(SpanKind.CONSUMER)` is valid 1.61.0 |
| #8 | OTLP gRPC vs HTTP port confusion | Endpoint `"http://localhost:4317"` with explicit scheme | §A5 — `setEndpoint` Javadoc requires `http://` prefix |

## Validation Architecture

CONTEXT.md (line 119) explicitly says **Phase 2 ships ZERO test code** — all tests deferred to Phase 6. Validation is manual + maven-gate only.

### End-user-verifiable (workshop attendee gestures)

| Behavior | How attendee verifies |
|---|---|
| Two distinct `service.name`s | Grafana → Tempo → Search → Service dropdown shows `order-producer` AND `order-consumer` (NEVER `unknown_service:java`) |
| Two disconnected traces per `POST /orders` | `mise run demo:order` then Tempo → TWO traces with different `traceId`s, one per service |
| Span kinds correct | Producer: SERVER → INTERNAL → PRODUCER. Consumer: CONSUMER → INTERNAL |
| HTTP semconv attributes | Producer SERVER span shows `http.request.method=POST`, `url.path=/orders`, `http.response.status_code=202`, `http.route=/orders` |
| AMQP semconv attributes | Producer PRODUCER span shows `messaging.system=rabbitmq`, `messaging.destination.name=orders`, `messaging.operation.type=send`, `messaging.rabbitmq.destination.routing_key=order.created` |
| `/actuator/health` excluded | Hit health repeatedly; Tempo Search returns NO traces for `/actuator/*` |
| Graceful-shutdown flush | `POST /orders` then immediately `Ctrl-C` producer; just-emitted trace still appears in Tempo |
| README DOC-05 callout | One-paragraph "duplication is on purpose" rationale present |

### CI-verifiable (no test code, just maven gates)

- `mvn validate` — enforcer fires `dependencyConvergence` per parent pom.xml
- `mvn dependency:tree -Dincludes=io.opentelemetry` — shows `opentelemetry-api`, `-sdk`, `-exporter-otlp`, `-semconv`, `-semconv-incubating` (and **only one version per artifact**)
- `mvn -T 1C clean install` — exits 0 in both producer and consumer modules

### Wave 0 Gaps

`mise.toml` task `verify:bom` (lines 121-132) asserts ZERO OTel libs — correct for Phase 1, FALSE in Phase 2. Recommend inverting: assert ONE version per OTel artifact (the Phase 2 invariant; matches PITFALLS.md #9 prevention). Task name keeps its meaning, adds value forever.

## Open Questions

**RESEARCH FLAG #1 — `messaging.operation` key drift (HIGH severity):**

CONTEXT.md TRACE-07 / TRACE-08 / D-11 reference `messaging.operation=publish` and `messaging.operation=process` as literal attribute keys. Per semconv-java 1.40.0:
- `MESSAGING_OPERATION` (key = `"messaging.operation"`) is `@Deprecated` ("Replaced by `messaging.operation.type`")
- `MESSAGING_OPERATION_TYPE` (key = `"messaging.operation.type"`) is the current form
- Value `"publish"` was renamed to `"send"` in spec; `MessagingOperationTypeIncubatingValues.SEND = "send"` and `.PROCESS = "process"`

**Proposed resolution:** Planner uses `MESSAGING_OPERATION_TYPE` and the value enum (`SEND` / `PROCESS`). TRACE-07's intent ("PRODUCER span carries the standard messaging-operation attribute") is preserved; only the spelling changes. Recommend a one-line teaching comment: `// semconv 1.40.0 renamed messaging.operation → messaging.operation.type; values: send|receive|process|create`. **Does NOT require revisiting CONTEXT.md.**

**RESEARCH FLAG #2 — `opentelemetry-semconv-incubating:1.40.0-alpha` is a NEW dependency (MEDIUM severity):**

STACK.md lists incubating semconv as "Optional". Phase 2 makes it MANDATORY because `MessagingIncubatingAttributes` and `DeploymentIncubatingAttributes` are required for D-11 / D-13 / TRACE-07 / TRACE-08. The maven-enforcer `dependencyConvergence` rule should pass — incubating ships from the same release line as stable. Document in `OtelSdkConfiguration` comment: `// Incubating semconv carries the -alpha suffix because messaging conventions are still evolving; no shipping demo can use stable-only constants for AMQP yet`.

**RESEARCH FLAG #3 — `mise run verify:bom` task semantics flip (LOW severity):**

`mise.toml` lines 121-132 define `verify:bom` as "assert ZERO OTel libs" — Phase 1 success gate, Phase 2 failure gate. Planner should invert to "ONE version per OTel artifact". Low severity — tooling, not code; the maven-enforcer rule is the load-bearing check.

## Sources

### Primary (HIGH confidence — verified against tagged source)

- `open-telemetry/opentelemetry-java` @ tag `v1.61.0`:
  - `sdk/all/.../OpenTelemetrySdk.java` — `close()`, `shutdown()` cascade
  - `sdk/trace/.../export/BatchSpanProcessor.java` + `BatchSpanProcessorBuilder.java` — `builder()`, default tunings
  - `sdk/trace/.../samplers/Sampler.java` — `parentBased`, `alwaysOn`
  - `sdk/common/.../resources/Resource.java` — `getDefault()`, `MANDATORY`, `merge()`
  - `exporters/otlp/all/.../OtlpGrpcSpanExporterBuilder.java` — `setEndpoint(String)` Javadoc
  - `context/.../propagation/{TextMapPropagator,ContextPropagators}.java`
  - `api/all/.../{trace/propagation/W3CTraceContextPropagator,baggage/propagation/W3CBaggagePropagator}.java`
  - `api/all/.../trace/{SpanKind,StatusCode}.java`
- `open-telemetry/semantic-conventions-java` @ tag `v1.40.0`:
  - `semconv/.../{ServiceAttributes,HttpAttributes,UrlAttributes,ServerAttributes}.java` — stable
  - `semconv-incubating/.../incubating/DeploymentIncubatingAttributes.java` — DEPLOYMENT_ENVIRONMENT_NAME current; DEPLOYMENT_ENVIRONMENT deprecated
  - `semconv-incubating/.../incubating/MessagingIncubatingAttributes.java` — MESSAGING_SYSTEM, MESSAGING_DESTINATION_NAME, MESSAGING_OPERATION_TYPE current, MESSAGING_OPERATION deprecated, MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY, MessagingSystemIncubatingValues.RABBITMQ, MessagingOperationTypeIncubatingValues.SEND/PROCESS

### Secondary (MEDIUM confidence)

- Spring Framework `OncePerRequestFilter` Javadoc — `shouldNotFilter(HttpServletRequest)` hook
- Spring Boot 3.4 reference — `@Bean Filter` auto-discovery via default `FilterRegistrationBean(/*)`
- [spring-boot#38331](https://github.com/spring-projects/spring-boot/issues/38331) — `setUrlPatterns` quirks
- [Baeldung: Excluding URLs for a Filter in Spring](https://www.baeldung.com/spring-exclude-filter)

### Tertiary (Context7 — used for cross-check)

- `/open-telemetry/opentelemetry-java` Context7 snippets — manual SDK builder examples; both `build()` and `buildAndRegisterGlobal()` shown as supported

## RESEARCH COMPLETE
