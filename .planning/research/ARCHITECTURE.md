# Architecture Research

**Domain:** Workshop-grade Spring Boot 3.4.13 + Java 17 demo with manual OpenTelemetry SDK instrumentation across an HTTP → AMQP → consumer flow
**Researched:** 2026-04-29
**Confidence:** HIGH (stack and propagation patterns verified against OTel/Spring docs and lgtm container repo); MEDIUM on the workshop-step sequencing (opinionated pedagogical ordering)

## Standard Architecture

### System Overview

```
                                    ┌─────────────────────┐
              curl / httpie  ─────► │   producer-service  │
              POST /orders          │  (Spring Boot,      │
                                    │   host JVM via mise)│
                                    │                     │
                                    │  ┌───────────────┐  │
                                    │  │ OrdersCtrl    │  │ ← server span (HTTP)
                                    │  ├───────────────┤  │
                                    │  │ OrderService  │  │ ← internal span (business)
                                    │  ├───────────────┤  │
                                    │  │ RabbitTemplate│  │ ← producer span (AMQP)
                                    │  │  + Tracing-   │  │   injects W3C traceparent
                                    │  │   MessagePost-│  │   into message headers
                                    │  │   Processor   │  │
                                    │  └───────┬───────┘  │
                                    └──────────┼──────────┘
                                               │ OTLP/gRPC :4317
                                               │ (traces, metrics, logs)
                                               │
                                               ▼
                                    ┌─────────────────────┐
                                    │  grafana/otel-lgtm  │
                                    │  (single container) │
                                    │                     │
                                    │  OTel Collector ─►  │
                                    │   ├─ Tempo  :3200   │
                                    │   ├─ Mimir  :9090   │
                                    │   └─ Loki   :3100   │
                                    │           ▲         │
                                    │  Grafana :3000 ─────┤ ← workshop attendee browser
                                    └─────────────────────┘
                                               ▲
                                               │ OTLP/gRPC :4317
                                               │
                                    ┌──────────┼──────────┐
                                    │          │          │
                                    │  consumer-service   │
                                    │  (Spring Boot,      │
                                    │   host JVM via mise)│
                                    │                     │
                                    │  ┌───────────────┐  │
                                    │  │@RabbitListener│  │ ← consumer span (AMQP)
                                    │  │ + Tracing-    │  │   extracts traceparent
                                    │  │   MessageList-│  │   from message headers,
                                    │  │   enerAdvice  │  │   continues parent trace
                                    │  ├───────────────┤  │
                                    │  │ProcessingSvc  │  │ ← internal span (business)
                                    │  └───────────────┘  │
                                    └─────────────────────┘
                                               ▲
                                               │ AMQP :5672
                                               │
                                    ┌──────────┼──────────┐
                                    │   RabbitMQ          │
                                    │   (docker-compose)  │
                                    │   :5672  AMQP       │
                                    │   :15672 Management │
                                    │                     │
                                    │   exchange: orders  │
                                    │   queue:    orders.created │
                                    │   routing:  order.created  │
                                    └─────────────────────┘
```

Three boxes, three responsibilities:
1. **Apps on host** (producer + consumer) — one JVM each, started via `mise run dev:producer` / `dev:consumer`. No containerization for apps so attendees can attach a debugger.
2. **Infrastructure in docker-compose** — RabbitMQ and `grafana/otel-lgtm`. Both apps connect to localhost (RabbitMQ 5672, OTLP 4317).
3. **Shared OTel bootstrap** — extracted to a `otel-bootstrap` library module so the same SDK wiring is used by both services without copy-paste drift.

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `producer-service` | HTTP entry point; publishes `OrderCreated` to RabbitMQ | Spring Boot 3.4.13 app, `@RestController`, `RabbitTemplate` |
| `consumer-service` | Consumes `OrderCreated`; simulates downstream domain work | Spring Boot 3.4.13 app, `@RabbitListener`, in-memory processing |
| `otel-bootstrap` (shared lib) | Programmatically constructs `OpenTelemetrySdk` with OTLP/gRPC exporters for all three signals; registers as Spring bean | `@AutoConfiguration` class + builder methods returning `Tracer`, `Meter`, `OpenTelemetry` beans |
| `TracingMessagePostProcessor` (in shared lib) | Producer-side: injects W3C `traceparent`/`tracestate` into AMQP message headers via `TextMapSetter<MessageProperties>` | Implements `org.springframework.amqp.core.MessagePostProcessor` |
| `TracingMessageListenerAdvice` (in shared lib) | Consumer-side: extracts W3C context from headers via `TextMapGetter<MessageProperties>`, makes context current, creates CONSUMER span | Implements `org.aopalliance.intercept.MethodInterceptor`; registered on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` |
| `RabbitMQ` (docker-compose) | Single direct exchange `orders` + queue `orders.created` bound on routing key `order.created` | `rabbitmq:3-management-alpine` |
| `grafana/otel-lgtm` (docker-compose) | Embedded OTel Collector + Tempo + Mimir + Loki + Grafana, one container | `grafana/otel-lgtm:latest` |

## Recommended Project Structure

### Top-level layout

```
ose-otel-demo/
├── pom.xml                              # parent / aggregator POM (packaging=pom)
├── mise.toml                            # JDK + Maven pinning + tasks
├── docker-compose.yml                   # RabbitMQ + grafana/otel-lgtm
├── README.md                            # workshop walkthrough, step-by-step
├── docs/
│   ├── screenshots/                     # Grafana panels per step
│   └── grafana-dashboard.json           # optional shipped dashboard
│
├── otel-bootstrap/                      # shared library module
│   ├── pom.xml
│   └── src/main/java/com/example/otel/
│       ├── OtelSdkConfiguration.java    # @AutoConfiguration: builds OpenTelemetrySdk
│       ├── OtelProperties.java          # @ConfigurationProperties("otel")
│       ├── amqp/
│       │   ├── TracingMessagePostProcessor.java  # producer-side inject
│       │   ├── TracingMessageListenerAdvice.java # consumer-side extract
│       │   ├── MessagePropertiesSetter.java      # TextMapSetter<MessageProperties>
│       │   └── MessagePropertiesGetter.java      # TextMapGetter<MessageProperties>
│       └── METRIC_NAMES.java            # constants for metric names
│
├── producer-service/                    # Spring Boot app #1
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/producer/
│       │   ├── ProducerApplication.java
│       │   ├── api/OrdersController.java
│       │   ├── domain/OrderService.java
│       │   ├── messaging/OrderPublisher.java
│       │   └── config/RabbitConfig.java
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── logback-spring.xml       # OpenTelemetryAppender wired here
│       └── test/java/com/example/producer/
│           └── ProducerIntegrationTest.java   # @SpringBootTest + Testcontainers
│
├── consumer-service/                    # Spring Boot app #2
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/consumer/
│       │   ├── ConsumerApplication.java
│       │   ├── messaging/OrderListener.java
│       │   ├── domain/ProcessingService.java
│       │   └── config/RabbitConfig.java       # registers TracingMessageListenerAdvice
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── logback-spring.xml
│       └── test/java/com/example/consumer/
│           └── ConsumerIntegrationTest.java
│
└── integration-tests/                   # OPTIONAL cross-service e2e module
    ├── pom.xml                          # depends on both services + Testcontainers
    └── src/test/java/com/example/e2e/
        └── OrderFlowE2ETest.java        # spins up RabbitMQ, runs both apps, asserts trace
```

### Structure Rationale

- **Multi-module Maven (parent + 3 children) over two top-level projects:** chosen because (a) the workshop must demonstrate trace context crossing the AMQP boundary, and that lesson lives in the *shared* `TracingMessagePostProcessor`/`TracingMessageListenerAdvice` pair — duplicating them in two flat repos would force attendees to read the same code twice and obscure that they're symmetric. (b) A single `mvn -T 1C verify` builds everything; one `mise run test` runs all tests. (c) The parent POM holds `<dependencyManagement>` with the OTel BOM (`io.opentelemetry:opentelemetry-bom:1.61.0`) and Spring Boot BOM imports, so child modules declare dependencies without versions — preventing version drift between the two services. (d) The Spring Boot Maven plugin is configured at the parent level but only `<executions>` are inherited per module that needs it.
- **`otel-bootstrap` as a library, not a Spring Boot app:** packaging is `jar` (not Spring Boot fat jar) and it has zero `main()`. It auto-configures via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listing `OtelSdkConfiguration`. This keeps producer and consumer free of OTel boilerplate — they just `implementation otel-bootstrap` and the `Tracer`, `Meter`, and `OpenTelemetry` beans appear.
- **`producer-service` and `consumer-service` as siblings, not nested:** equal billing in the README; attendees can `cd producer-service && mvn spring-boot:run` if they want to focus on one side.
- **Tests inside each service module + optional `integration-tests` module:** unit/component tests live with their service; cross-service e2e (the "I posted an order, did the consumer span show up in the same trace?" assertion) lives in a dedicated module so its dependency on Testcontainers + both services doesn't leak into the runtime classpath of either app.
- **`docs/screenshots/` at top level, not per-step:** a single staged repo on `main` only ever shows the latest state; per-step screenshots in commit-specific folders create merge friction across checkpoints. Cross-link from `README.md` by step name.
- **Workshop checkpoints as **tags**, not long-lived branches:** tags are immutable, branches drift. `step-01-baseline` through `step-06-tests` are tags pointing at `main`'s history; attendees `git checkout step-03-context-propagation` to time-travel.

### Parent POM essentials (snippet)

```xml
<project>
  <groupId>com.example</groupId>
  <artifactId>ose-otel-demo-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
    <!-- <module>integration-tests</module> -->
  </modules>

  <properties>
    <java.version>17</java.version>
    <spring-boot.version>3.4.13</spring-boot.version>
    <opentelemetry.version>1.61.0</opentelemetry.version>
    <opentelemetry-instrumentation.version>2.21.0</opentelemetry-instrumentation.version>
    <testcontainers.version>1.21.5</testcontainers.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
      <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-bom</artifactId>
        <version>${opentelemetry.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
      <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-instrumentation-bom</artifactId>
        <version>${opentelemetry-instrumentation.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

## Architectural Patterns

### Pattern 1: Shared OTel Bootstrap as Spring Auto-Configuration

**What:** Put the `OpenTelemetrySdk` builder in a library module, expose it via `@AutoConfiguration` so each service gets `Tracer`, `Meter`, and `OpenTelemetry` beans by adding the dependency.
**When to use:** Anytime you have ≥2 JVM services that should emit telemetry the same way.
**Trade-offs:**
- Pro: single source of truth for resource attributes (`service.name`, `service.version`), exporter endpoint, sampling, propagator config.
- Pro: workshop attendees see the SDK code in *one* place, not two.
- Con: a library doing auto-config is a tiny abstraction layer; attendees who skim past it might miss what it does. Mitigate with a callout in the README pointing at `OtelSdkConfiguration.java`.

**Example:**
```java
// otel-bootstrap/.../OtelSdkConfiguration.java
@AutoConfiguration
@EnableConfigurationProperties(OtelProperties.class)
public class OtelSdkConfiguration {

    @Bean(destroyMethod = "close")
    public OpenTelemetry openTelemetry(OtelProperties props) {
        Resource resource = Resource.getDefault().merge(
            Resource.builder()
                .put(SERVICE_NAME, props.serviceName())
                .put(SERVICE_VERSION, props.serviceVersion())
                .build());

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint(props.endpoint())
                    .build()).build())
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(PeriodicMetricReader.builder(
                OtlpGrpcMetricExporter.builder()
                    .setEndpoint(props.endpoint())
                    .build()).build())
            .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(
                OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(props.endpoint())
                    .build()).build())
            .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();

        // Wire Logback appender to this SDK
        OpenTelemetryAppender.install(sdk);
        return sdk;
    }

    @Bean
    public Tracer tracer(OpenTelemetry otel, OtelProperties props) {
        return otel.getTracer(props.serviceName(), props.serviceVersion());
    }

    @Bean
    public Meter meter(OpenTelemetry otel, OtelProperties props) {
        return otel.getMeter(props.serviceName());
    }
}
```

### Pattern 2: AMQP Trace Context Propagation via TextMap*

**What:** Inject W3C `traceparent` into `MessageProperties.headers` on publish, extract on consume. Keep producer-side concern in a `MessagePostProcessor`, consumer-side in a `MethodInterceptor` registered as listener container advice.
**When to use:** Always for Spring AMQP — OTel auto-instrumentation does **not** cover Spring AMQP (this is the workshop's pedagogical hook).
**Trade-offs:**
- Pro: standard W3C headers — interoperates with any other OTLP-compatible service downstream.
- Pro: header keys are exactly `traceparent`/`tracestate` (lowercase, hyphenated) — same as HTTP, so attendees recognize them.
- Con: the listener-advice approach (vs. extracting inside the `@RabbitListener` method body) requires understanding Spring AOP ordering. It's worth the cost: attendees see one piece of code that handles every listener method, not propagation boilerplate sprayed across handlers.

**Example — producer side:**
```java
// otel-bootstrap/.../amqp/TracingMessagePostProcessor.java
public class TracingMessagePostProcessor implements MessagePostProcessor {
    private static final TextMapSetter<MessageProperties> SETTER =
        (carrier, key, value) -> carrier.setHeader(key, value);

    private final OpenTelemetry otel;
    private final Tracer tracer;

    public Message postProcessMessage(Message message) {
        MessageProperties props = message.getMessageProperties();
        Span span = tracer.spanBuilder(props.getReceivedExchange() + " publish")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(MESSAGING_DESTINATION_NAME,
                props.getReceivedExchange() + ":" + props.getReceivedRoutingKey())
            .setAttribute(MESSAGING_OPERATION_NAME, "send")
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            otel.getPropagators().getTextMapPropagator()
                .inject(Context.current(), props, SETTER);
            return message;
        } finally {
            span.end();
        }
    }
}
```

**Example — consumer side:**
```java
// otel-bootstrap/.../amqp/TracingMessageListenerAdvice.java
public class TracingMessageListenerAdvice implements MethodInterceptor {
    private static final TextMapGetter<MessageProperties> GETTER = new TextMapGetter<>() {
        public Iterable<String> keys(MessageProperties carrier) {
            return carrier.getHeaders().keySet();
        }
        public String get(MessageProperties carrier, String key) {
            Object v = carrier == null ? null : carrier.getHeader(key);
            return v == null ? null : v.toString();
        }
    };

    public Object invoke(MethodInvocation inv) throws Throwable {
        Message message = (Message) inv.getArguments()[1];  // (channel, message)
        MessageProperties props = message.getMessageProperties();
        Context extracted = otel.getPropagators().getTextMapPropagator()
            .extract(Context.current(), props, GETTER);

        Span span = tracer.spanBuilder(props.getReceivedExchange() + " process")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(extracted)
            .setAttribute(MESSAGING_SYSTEM, "rabbitmq")
            .setAttribute(MESSAGING_DESTINATION_NAME,
                props.getReceivedExchange() + ":" + props.getReceivedRoutingKey()
                    + ":" + props.getConsumerQueue())
            .setAttribute(MESSAGING_OPERATION_NAME, "process")
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {
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

Wired in `RabbitConfig` of the consumer:
```java
@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory cf, TracingMessageListenerAdvice tracingAdvice) {
    SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
    f.setConnectionFactory(cf);
    f.setAdviceChain(tracingAdvice);
    return f;
}
```

### Pattern 3: Staged Git Checkpoints as Tags, Not Branches

**What:** One annotated tag per workshop step. `main` carries the full final state; tags are time-travel anchors.
**When to use:** Workshop format where attendees `git checkout` to follow along.
**Trade-offs:**
- Pro: linear, immutable, no merge conflicts when iterating on later content.
- Pro: `git log --oneline` shows the workshop arc.
- Con: rewriting history (rebasing earlier tags) is destructive — once a workshop has been delivered with these tags, treat them as frozen.

Suggested commit/tag arc — **each is dependent on its predecessor**:

| Tag | Adds | Why this order |
|-----|------|----------------|
| `step-01-baseline` | producer + consumer Spring Boot apps, RabbitMQ wired, end-to-end POST → publish → consume works **with no telemetry** | Establishes the working app the attendee will instrument; nothing OTel yet |
| `step-02-traces` | `otel-bootstrap` module, `OpenTelemetrySdk` with **traces only**, manual `@WithSpan`-style calls in `OrdersController` and `ProcessingService` | Smallest possible OTel surface; attendee sees a span in Tempo without context propagation yet (producer trace + consumer trace are *separate*) |
| `step-03-context-propagation` | `TracingMessagePostProcessor` + `TracingMessageListenerAdvice` | The "aha" moment: same operation, now one connected trace. Headlines the workshop. |
| `step-04-metrics` | `SdkMeterProvider`, counters (`orders.processed`), histogram (HTTP latency, message duration) | Builds on the SDK already wired; just adds a `Meter` |
| `step-05-logs` | `SdkLoggerProvider`, `logback-spring.xml` `OpenTelemetryAppender`, MDC-style trace correlation | Last signal; closes the loop showing all three sources correlate in Grafana |
| `step-06-tests` | Testcontainers-based integration test asserting parent/child span relationship using `InMemorySpanExporter` | Caps the workshop with "now you can prove your instrumentation works in CI" |

## Data Flow

### Request Flow (the trace's life)

```
attendee runs:  curl -X POST localhost:8080/orders -d '{"id":"42"}'
                          │
                          ▼
   ┌────────────────────────────────────────────────────┐
   │ producer-service                                    │
   │                                                     │
   │  OrdersController.create()                          │
   │    └─ tracer.spanBuilder("POST /orders")            │
   │       .setSpanKind(SERVER) .startSpan()             │  span A (SERVER)
   │       └─ orderService.place(order)                  │
   │            └─ tracer.spanBuilder("place order")     │  span B (INTERNAL, child of A)
   │               .startSpan()                          │
   │               └─ orderPublisher.publish(order)      │
   │                    └─ rabbitTemplate.convertAndSend │
   │                         + TracingMessagePostProcessor
   │                         creates span C (PRODUCER, child of B)
   │                         injects traceparent into MessageProperties
   └────────────────────────────────────────────────────┘
                          │
                          │  AMQP (5672) — message + traceparent header
                          ▼
   ┌────────────────────────────────────────────────────┐
   │ RabbitMQ                                            │
   │   exchange "orders" → queue "orders.created"        │
   └────────────────────────────────────────────────────┘
                          │
                          ▼
   ┌────────────────────────────────────────────────────┐
   │ consumer-service                                    │
   │                                                     │
   │  TracingMessageListenerAdvice.invoke()              │
   │    └─ extract(traceparent) → parent context         │
   │       tracer.spanBuilder("orders process")          │  span D (CONSUMER, child of C)
   │       .setParent(extracted) .startSpan()            │
   │       └─ OrderListener.onOrder()                    │
   │            └─ processingService.process(order)      │
   │                 └─ tracer.spanBuilder("process")    │  span E (INTERNAL, child of D)
   │                                                     │
   │  All five spans share the same trace_id.            │
   └────────────────────────────────────────────────────┘
                          │
                          ▼  OTLP/gRPC :4317
   ┌────────────────────────────────────────────────────┐
   │ grafana/otel-lgtm: Tempo stores trace, Grafana      │
   │ "Explore" panel shows the full waterfall.           │
   └────────────────────────────────────────────────────┘
```

### Trace Context Injection / Extraction Points

| Boundary | Direction | Carrier | Setter/Getter | Class |
|----------|-----------|---------|---------------|-------|
| HTTP inbound at producer | extract | `HttpServletRequest` headers | `TextMapGetter<HttpServletRequest>` | A `Filter` registered as `FilterRegistrationBean` (or skip — manual `tracer.spanBuilder` in controller is enough for a workshop) |
| AMQP outbound at producer | inject | `MessageProperties` headers | `TextMapSetter<MessageProperties>` | `TracingMessagePostProcessor` |
| AMQP inbound at consumer | extract | `MessageProperties` headers | `TextMapGetter<MessageProperties>` | `TracingMessageListenerAdvice` |
| Logging | inject | `MDC` (auto via `OpenTelemetryAppender`) | n/a | `OpenTelemetryAppender` reads `Span.current()` |

### State Management

This demo has effectively no application state — orders are not persisted. The interesting "state" is OTel SDK state:

```
ApplicationContext startup
        │
        ▼
OtelSdkConfiguration creates OpenTelemetrySdk
        │  registers as global (GlobalOpenTelemetry.set)
        ▼
Tracer / Meter / OpenTelemetry beans available
        │
        ▼
TracingMessagePostProcessor (singleton bean)
TracingMessageListenerAdvice (singleton bean)
        │
        ▼
ApplicationContext shutdown
        │  destroyMethod = "close" → flushes BatchSpanProcessor,
        │  PeriodicMetricReader, BatchLogRecordProcessor
        ▼
SDK disposed cleanly
```

### Key Data Flows

1. **HTTP → AMQP → Consumer trace flow:** described above; produces 5 spans in one trace, demonstrates W3C propagation across non-HTTP boundary.
2. **Metrics flow:** apps export every 60s (default `PeriodicMetricReader` interval — override to 10s for workshop responsiveness) → OTel Collector in lgtm → Mimir → Grafana metric panels.
3. **Logs flow:** Logback `OpenTelemetryAppender` enriches each log event with active `trace_id`/`span_id` → OTLP/gRPC → OTel Collector → Loki → Grafana Loki panel; clicking a log line in Grafana jumps to the trace via the `trace_id` linker.

## docker-compose.yml shape

```yaml
services:
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: ose-rabbitmq
    ports:
      - "5672:5672"      # AMQP
      - "15672:15672"    # Management UI (guest/guest)
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  otel-lgtm:
    image: grafana/otel-lgtm:0.11.4   # pin a release; do not use :latest in workshop repo
    container_name: ose-otel-lgtm
    ports:
      - "3000:3000"      # Grafana UI (admin/admin)
      - "4317:4317"      # OTLP gRPC ingest
      - "4318:4318"      # OTLP HTTP ingest (not used by this demo, exposed for completeness)
      - "9090:9090"      # Prometheus / Mimir (read-only debugging)
      - "3100:3100"      # Loki HTTP API (read-only debugging)
      - "3200:3200"      # Tempo HTTP API (read-only debugging)
    volumes:
      - lgtm-data:/data  # persist between restarts
    restart: unless-stopped

volumes:
  lgtm-data:
```

Notes:
- No app services in compose. Apps run on host so attendees can debug the SDK code.
- `otel-lgtm` ships its own embedded Collector; no separate `otel-collector` service needed.
- Pin a specific tag (`0.11.4` is the verified contemporaneous release; latest as of research was `v0.26.0` — pick the one closest to workshop date and **freeze it in `docker-compose.yml`** so attendees joining later get identical UI screenshots).
- `lgtm-data` volume keeps Grafana dashboards persistent across `infra:down`/`infra:up` cycles during workshop iteration.

## mise.toml shape

```toml
[tools]
java = "corretto-17"        # Amazon Corretto 17, set via mise plugin
maven = "3.9.9"

[env]
OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"
OTEL_EXPORTER_OTLP_PROTOCOL = "grpc"
SPRING_RABBITMQ_HOST = "localhost"
SPRING_RABBITMQ_PORT = "5672"

# Infrastructure tasks ------------------------------------------------

[tasks."infra:up"]
description = "Start RabbitMQ + grafana/otel-lgtm in docker-compose"
run = "docker compose up -d"

[tasks."infra:down"]
description = "Stop infrastructure containers"
run = "docker compose down"

[tasks."infra:logs"]
description = "Tail infra logs"
run = "docker compose logs -f"

# Build / test --------------------------------------------------------

[tasks.build]
description = "Build all Maven modules"
run = "mvn -T 1C -DskipTests clean install"

[tasks.test]
description = "Run all tests across modules (unit + integration)"
depends = ["infra:up"]   # Testcontainers picks up Docker; lgtm not needed for InMemorySpanExporter tests
run = "mvn -T 1C verify"

# Run apps ------------------------------------------------------------

[tasks."dev:producer"]
description = "Run producer-service on the host JVM"
depends = ["infra:up"]
run = "mvn -pl producer-service spring-boot:run"

[tasks."dev:consumer"]
description = "Run consumer-service on the host JVM"
depends = ["infra:up"]
run = "mvn -pl consumer-service spring-boot:run"

[tasks.dev]
description = "Convenience: start producer + consumer in parallel (foreground)"
depends = ["infra:up"]
run = """
mvn -pl producer-service spring-boot:run &
PRODUCER_PID=$!
mvn -pl consumer-service spring-boot:run &
CONSUMER_PID=$!
trap "kill $PRODUCER_PID $CONSUMER_PID" EXIT
wait
"""

# Workshop helpers ----------------------------------------------------

[tasks."demo:order"]
description = "Send a sample POST /orders to the producer"
run = "curl -sf -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{\"id\":\"42\",\"sku\":\"WIDGET-1\",\"quantity\":3}'"

[tasks."ui:grafana"]
description = "Open Grafana in the default browser"
run = "xdg-open http://localhost:3000 || open http://localhost:3000"
```

## Workshop step sequence (build order)

The roadmap should treat each tag as a phase boundary. Hard dependencies between steps:

```
step-01-baseline ─────► step-02-traces ─────► step-03-context-propagation
       │                       │                         │
       │                       └─ depends on             └─ depends on
       │                          OtelSdkConfiguration      Tracer + Meter? no,
       │                                                    just on traces being wired
       │                                                    AND on AMQP working
       │
       ▼
step-04-metrics ─────► step-05-logs ─────► step-06-tests
       │                    │                    │
       depends on          depends on            depends on everything
       SDK existing        SDK existing,         (asserts the trace from step-03,
       (added in step-2)   Logback config        the metrics from step-4,
                                                  the logs from step-5)
```

| Step | Files added/changed | "Done when" criterion |
|------|---------------------|------------------------|
| 01-baseline | `pom.xml` (parent + 3 modules), `producer-service/**`, `consumer-service/**`, `docker-compose.yml`, `mise.toml`, `README.md` (skeleton) | `mise run dev` + `mise run demo:order` produces a log line in consumer; no telemetry exists |
| 02-traces | `otel-bootstrap/**` (just `OtelSdkConfiguration` for traces), explicit `tracer.spanBuilder` calls in `OrdersController` and `ProcessingService` | Grafana Tempo shows two **separate** traces — one for the HTTP call, one for the consume — proving propagation is missing |
| 03-context-propagation | `TracingMessagePostProcessor`, `TracingMessageListenerAdvice`, `MessagePropertiesSetter`, `MessagePropertiesGetter`, `RabbitConfig` updates | Grafana Tempo shows **one** trace with 5 spans across both services |
| 04-metrics | `Meter` injection, counters in publisher and listener, histogram around `place order` | Grafana metrics panel shows `orders_processed_total` increasing |
| 05-logs | `SdkLoggerProvider` already in bootstrap (or added here), `logback-spring.xml` with `OpenTelemetryAppender`, MDC trace_id pattern | Grafana Loki panel shows logs; clicking a log line opens the matching trace |
| 06-tests | `producer-service/src/test/...`, `consumer-service/src/test/...`, optional `integration-tests/` module | `mise run test` passes, including a span-relationship assertion using `InMemorySpanExporter` |

## Scaling Considerations

This is a workshop demo, not a production system. Scale concerns are pedagogical:

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1 attendee laptop | Default config — apps + infra fit comfortably in 4GB RAM. Set `BatchSpanProcessor` max queue 2048, schedule delay 5s for fast feedback. |
| 10–20 attendees in a room | No change — each runs their own copy locally. |
| 50+ remote workshop with shared backend | Don't share `otel-lgtm`; it's ephemeral. Each attendee runs their own. |

### Scaling Priorities

1. **First bottleneck (workshop UX):** default `PeriodicMetricReader` interval (60s) is too slow to feel responsive. Override to 10s in `OtelProperties`.
2. **Second bottleneck (workshop UX):** `BatchSpanProcessor` default schedule delay (5s) is fine; do **not** switch to `SimpleSpanProcessor` — that hides the realistic batching behavior attendees should see in production.

## Anti-Patterns

### Anti-Pattern 1: Two flat top-level Spring Boot projects with no shared module

**What people do:** `producer/` and `consumer/` as siblings, each with its own copy of `OtelSdkConfiguration`, `TracingMessagePostProcessor`, etc.
**Why it's wrong:** copy-paste drift; the workshop's headline lesson is that *the same propagator code* runs on both sides — duplicating it obscures that.
**Do this instead:** parent POM + `otel-bootstrap` library module + two service modules.

### Anti-Pattern 2: Putting OTel SDK setup inside `application.yml` via Spring Boot starter

**What people do:** add `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` and call it a day.
**Why it's wrong:** the workshop's whole point is teaching the manual SDK; the starter does it for you and hides the very lines we want to teach. Explicit constraint in `PROJECT.md` excludes this.
**Do this instead:** programmatic `OpenTelemetrySdk.builder()` in `OtelSdkConfiguration.java`.

### Anti-Pattern 3: Extracting trace context inside the `@RabbitListener` method body

**What people do:** sprinkle `propagator.extract(...)` at the top of every `@RabbitListener` method.
**Why it's wrong:** doesn't compose (every listener method needs the boilerplate); the extracted context is not the parent of the *listener method's framework-level work* (deserialization, error handling), only of code after the extract call.
**Do this instead:** register a `MethodInterceptor` as advice on the `SimpleRabbitListenerContainerFactory`. One class handles every listener.

### Anti-Pattern 4: Long-lived branches per workshop step

**What people do:** `git checkout step-03-context-propagation` against a branch that was created weeks ago and has diverged.
**Why it's wrong:** branches drift, tags don't; if you have to fix a typo in step-01, every later branch needs a rebase.
**Do this instead:** annotated tags on `main`, frozen after first delivery. Fix typos in a `step-XX-fix-*` follow-up tag if needed.

### Anti-Pattern 5: Running apps in docker-compose alongside infra

**What people do:** `producer:` and `consumer:` services in `docker-compose.yml`.
**Why it's wrong:** attendees can't attach an IDE debugger to step through SDK code; rebuild loop is slower.
**Do this instead:** infra in compose, apps on host via `mise run dev:*`.

### Anti-Pattern 6: Shipping a full Grafana dashboard JSON in the repo

**What people do:** export a finely-tuned dashboard, commit `dashboard.json`, mount it in the lgtm container.
**Why it's wrong:** lgtm's bundled "OpenTelemetry APM" dashboard already shows traces/metrics/logs out of the box; a custom dashboard is workshop polish that takes time to maintain through SDK version changes. Recommendation: **rely on lgtm defaults** for the workshop, ship a dashboard JSON only if attendees explicitly request it post-workshop.
**Do this instead:** README screenshots of Grafana's "Explore" view (Tempo / Loki / Mimir tabs). Add `docs/grafana-dashboard.json` only if a v2 demands it.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| RabbitMQ | Spring AMQP `RabbitTemplate` (publish), `@RabbitListener` (consume); single direct exchange `orders` + queue `orders.created` | Workshop ergonomics: `rabbitmq:3.13-management-alpine` so attendees can poke the management UI at :15672. Healthcheck via `rabbitmq-diagnostics ping`. |
| `grafana/otel-lgtm` | OTLP/gRPC :4317 (one endpoint, all three signals) | Pin the image tag in `docker-compose.yml`. Don't use `:latest` — Grafana panels move and screenshots in the README rot. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `producer-service` ↔ `consumer-service` | AMQP via RabbitMQ — no direct call | The boundary the workshop is teaching: trace context must cross via message headers |
| `otel-bootstrap` ← `producer-service` | Compile-time Maven dependency; runtime `@AutoConfiguration` | Library, not Spring Boot app; packaging `jar`, no `main()` |
| `otel-bootstrap` ← `consumer-service` | Same | Symmetric — the lesson |
| `producer-service` → `grafana/otel-lgtm` | OTLP/gRPC :4317 | Plain text, no auth (localhost only) |
| `consumer-service` → `grafana/otel-lgtm` | OTLP/gRPC :4317 | Same |
| Each app → RabbitMQ | AMQP :5672, `guest`/`guest` default creds | Workshop only — never in production |

## Sources

- [OpenTelemetry Java SDK manual setup](https://opentelemetry.io/docs/languages/java/sdk/) — `OpenTelemetrySdk.builder()`, `SdkTracerProvider`, `SdkMeterProvider`, `SdkLoggerProvider`, OTLP gRPC exporters
- [OpenTelemetry Java context propagation](https://uptrace.dev/get/opentelemetry-java/propagation) — `TextMapSetter`/`TextMapGetter`, W3C trace context patterns for non-HTTP carriers
- [OpenTelemetry semantic conventions for messaging spans](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/) — `messaging.system`, `messaging.destination.name`, `messaging.operation.name`
- [OpenTelemetry semantic conventions for RabbitMQ](https://opentelemetry.io/docs/specs/semconv/messaging/rabbitmq/) — destination format `{exchange}:{routing key}` (producer), `{exchange}:{routing key}:{queue}` (consumer)
- [grafana/docker-otel-lgtm GitHub](https://github.com/grafana/docker-otel-lgtm) — port mappings, volume layout, latest version (v0.26.0 as of April 2026)
- [Grafana docker-otel-lgtm blog announcement](https://grafana.com/blog/an-opentelemetry-backend-in-a-docker-image-introducing-grafana-otel-lgtm/) — single-container LGTM stack design
- [Spring AMQP reference docs](https://docs.spring.io/spring-amqp/docs/3.0.0/reference/html/) — `MessagePostProcessor`, advice chain on `SimpleRabbitListenerContainerFactory`
- [opentelemetry-java-instrumentation Logback appender README](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-appender-1.0/library/README.md) — `OpenTelemetryAppender` config in `logback-spring.xml`
- [Spring Boot multi-module guide](https://spring.io/guides/gs/multi-module/) — parent POM packaging, module declaration
- [Baeldung: Multi-module project with Spring Boot](https://www.baeldung.com/spring-boot-multiple-modules) — BOM imports vs. parent inheritance
- [opentracing-contrib/java-spring-rabbitmq](https://github.com/opentracing-contrib/java-spring-rabbitmq) — prior art for the `MessagePostProcessor` + listener-advice pattern (OpenTracing predecessor; same shape applies to OTel)
- [Aspecto: Distributed Tracing for RabbitMQ with OpenTelemetry](https://medium.com/@team_Aspecto/distributed-tracing-for-rabbitmq-with-opentelemetry-1ebe7457b4c1) — confirms manual instrumentation is needed for Spring AMQP (auto-instrumentation gap)
- [mise TOML tasks reference](https://mise.jdx.dev/tasks/toml-tasks.html) — `[tasks.X]` syntax, `depends`, `run`, `description`
- [Testcontainers RabbitMQ Module](https://java.testcontainers.org/modules/rabbitmq/) — `RabbitMQContainer`, Spring Boot `@ServiceConnection`

---
*Architecture research for: Spring Boot 3.4.13 + manual OpenTelemetry SDK demo with RabbitMQ propagation*
*Researched: 2026-04-29*
