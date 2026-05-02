# OSE OTel Demo

A workshop-grade demo that teaches engineers how to instrument a Spring Boot 3.4.13 / Java 17 application using the OpenTelemetry Java SDK directly — manual instrumentation, no `-javaagent` and no Micrometer bridge. The demo covers two of the most common service-to-service shapes (synchronous HTTP and asynchronous RabbitMQ producer/consumer) and emits all three OpenTelemetry signals — traces, metrics, and logs — to Grafana's `otel-lgtm` all-in-one backend.

The workshop progresses through six annotated git tags: `step-01-baseline` → `step-02-traces` → `step-03-context-propagation` → `step-04-metrics` → `step-05-logs` → `step-06-tests`. You can `git checkout` any tag to time-travel through the workshop. The current `main` branch as of `step-01-baseline` shows the **uninstrumented baseline** — both Spring Boot apps run end-to-end with `POST /orders` flowing through RabbitMQ, but with **zero OpenTelemetry libraries on the classpath**. Phase 2 onward adds the SDK.

## Prerequisites

You will run two Spring Boot apps on your laptop's JVM and two infrastructure containers (RabbitMQ + grafana/otel-lgtm) via Docker. Before starting, verify your environment with `mise run preflight`.

### Required tools

| Tool                       | Version      | Install                                                  |
|----------------------------|--------------|----------------------------------------------------------|
| mise                       | `≥ 2025.1.0` | `curl https://mise.run \| sh`                            |
| Docker Engine + Compose v2 | `≥ 24.0`     | https://docs.docker.com/engine/install/                  |
| Git                        | `≥ 2.30`     | `brew install git` / `apt install git` / `pacman -S git` |

mise will install the right JDK and Maven for you on first `mise install`:

| Auto-installed via mise   | Version                  |
|---------------------------|--------------------------|
| Amazon Corretto JDK       | `corretto-17.0.13.11.1`  |
| Apache Maven              | `3.9.11`                 |

### Required free ports

| Port  | Service                | Why                                          |
|-------|------------------------|----------------------------------------------|
| 3000  | Grafana UI             | Common collision (React/Next.js dev servers) |
| 4317  | OTLP gRPC ingest       | Used from Phase 2 onwards                    |
| 4318  | OTLP HTTP ingest       | Reserved for HTTP-fallback variant           |
| 5672  | RabbitMQ AMQP          | Standard AMQP port                           |
| 15672 | RabbitMQ Management UI | Standard management port                     |
| 8080  | producer-service HTTP  | Spring Boot default                          |
| 8081  | consumer-service HTTP  | `/actuator/health` only                      |

If a port is in use, `mise run preflight` will tell you which one and suggest `lsof -i:<port>` to identify the conflicting process.

### IDE setup

If you use **IntelliJ IDEA**: install the [Mise plugin](https://plugins.jetbrains.com/plugin/24009-mise) OR ensure IntelliJ's "Project SDK" points at the mise-installed Corretto JDK (run `mise where java` to print the absolute path). The committed `.tool-versions` file enables IntelliJ's built-in auto-detection as a fallback.

If you use **VS Code**: install the [Mise extension](https://marketplace.visualstudio.com/items?itemName=hverlin.mise-vscode).

### One-time setup

```sh
git clone https://github.com/cotocisternas/ose-otel-demo
cd ose-otel-demo
mise install        # installs the Corretto JDK + Maven versions pinned in mise.toml
mise run preflight  # validates everything before you start
```

### First run

```sh
mise run infra:up   # starts RabbitMQ + grafana/otel-lgtm
mise run dev        # starts producer + consumer in parallel
# in a second terminal:
mise run demo:order # POSTs a sample order; expect 202
```

You should see the consumer log a line like: `OrderCreated received: orderId=<uuid>`.

In Phase 1 there is **no telemetry** — the OTLP endpoint is open and the Grafana stack is running, but the apps emit nothing. This is intentional: Phase 2 introduces the OpenTelemetry SDK and traces start flowing. To verify the baseline: `mise run verify:bom` should report zero OpenTelemetry libraries on the classpath.

## Workshop checkpoints

- `step-01-baseline` — Working two-service Spring Boot + RabbitMQ app on host JVM with ZERO telemetry.
- `step-02-traces` — Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces (intentional setup for the Phase 3 propagation lesson).
- `step-03-context-propagation` — THE headline lesson: AMQP context propagation joins the two traces; `consumer.parentSpanId == producer.spanId` after this checkpoint.
- `step-04-metrics` — `SdkMeterProvider` lands as a sibling pipeline next to the tracer pipeline; `orders.created` (Counter), `http.server.request.duration` (Histogram, seconds), `orders.queue.depth.estimate` (ObservableGauge) flow to Mimir on a 10-second interval.
- `step-05-logs` — Logs correlation + Loki-to-Tempo click-through. **Current.**
- `step-06-tests` — (Phase 6) Testcontainers verification.

This section establishes the convention; Phase 7 turns each bullet into a full walkthrough.

## Step 4: Metrics

`step-04-metrics` adds the **second** OTel signal — metrics — to both services. The two
`OtelSdkConfiguration.java` files now build a `SdkMeterProvider` next to the
`SdkTracerProvider` (D-01 in `04-CONTEXT.md` extracted Phase 2's tracer pipeline
into `buildTracerProvider(Resource)` and added a sibling `buildMeterProvider(Resource)`,
so the diff against `step-03-context-propagation` reads as "we added a sibling
pipeline next to the trace pipeline"). The producer adds a Counter and a Histogram
to its existing instrumentation surfaces; the consumer adds an ObservableGauge.

The three instrument shapes — one Counter, one Histogram, one ObservableGauge —
cover the OTel SDK's three primary metric kinds:

- **`orders.created`** (`LongCounter`, producer-side) — fires after each successful
  `POST /orders` from inside [`OrderService.place(...)`](./producer-service/src/main/java/com/example/producer/domain/OrderService.java)
  with the business attribute `order.priority` from the request payload (fallback `"standard"`).
  The counter does NOT fire on the failure path — failures are visible via the trace's
  ERROR status, not as a metric. `order.priority` is a string-literal `AttributeKey`
  because it is NOT in the OTel semconv catalog (contrast with the histogram's
  `HttpAttributes.HTTP_REQUEST_METHOD` which IS semconv).
- **`http.server.request.duration`** (`DoubleHistogram`, producer-side) — recorded from
  inside the existing [`HttpServerSpanFilter`](./producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java)
  finally block, BEFORE `span.end()`. **Unit is seconds (`"s"`), not milliseconds.**
  The seconds-not-millis trap (D-13) is the textbook OTel-porting mistake — semconv 1.40.0
  specifies seconds, and Mimir's default `http_server_request_duration_seconds` dashboards
  assume seconds. Attributes follow HTTP semconv: `http.request.method` and
  `http.response.status_code` only — `url.path` is intentionally excluded because
  high-cardinality path values would explode the metric series count.
- **`orders.queue.depth.estimate`** (`ObservableGauge`, consumer-side) — registered by
  [`QueueDepthGauge`](./consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java).
  Callback fires on every 10-second collection interval (METRIC-01 — overrides OTel's
  60-second default `PeriodicMetricReader`) and returns a synthetic
  `ThreadLocalRandom.current().nextInt(0, 50)` value. The lesson is the
  callback-on-interval mechanism, not the value semantics; a real implementation would
  poll the RabbitMQ Management API (out of scope for this workshop).

`mise run demo:order` now sends two payloads — `priority=express` and `priority=standard`
— so Mimir shows two series for `orders.created`. Try the Mimir query
`orders_created_total{order_priority="express"}` to see one of them. **Note the name
mangling:** the OTel-to-Prometheus exporter (running inside `otel-lgtm`'s collector)
converts dots to underscores and appends `_total` for monotonic counters, so the
OTel-side `orders.created` surfaces in Mimir as `orders_created_total` and
`http.server.request.duration` (unit `s`) surfaces as `http_server_request_duration_seconds`.
The same Resource attributes from Phase 2 (`service.name`, `service.namespace`,
`service.instance.id`, `deployment.environment.name`) appear on every metric data
point (D-05 — built once and shared between traces and metrics for cross-signal
correlation in Grafana).

## Step 5: Logs Correlation

`step-05-logs` adds the **third** OTel signal — logs — to both services, closing
the three-signals loop. The two `OtelSdkConfiguration.java` files now build a
`SdkLoggerProvider` next to the existing `SdkTracerProvider` and `SdkMeterProvider`
(D-01 in `05-CONTEXT.md` lands a third sibling helper `buildLoggerProvider(Resource)`,
parallel to the Phase 2 and Phase 4 helpers, so the diff against `step-04-metrics`
reads as "we added a sibling pipeline next to the trace and metric pipelines"). A
new `logback-spring.xml` per service declares the `OpenTelemetryAppender` for OTLP
export plus the MDC injector wrapper appender that stamps `trace_id`/`span_id`
into the console pattern.

The three Phase 5 SDK + Logback touch points cover the two ways trace context
flows into a log line:

- **`SdkLoggerProvider` + `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter`** —
  the third pipeline added to
  [`OtelSdkConfiguration.java`](./producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java)
  (and its consumer mirror at
  [`consumer-service/.../OtelSdkConfiguration.java`](./consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java)).
  Same OTLP endpoint as traces and metrics (`:4317`, `OTEL_EXPORTER_OTLP_ENDPOINT`
  env var with fallback — D-04 carryforward); same shared `Resource` for cross-signal
  correlation (D-05 — same `service.name` / `service.namespace` / `service.instance.id`
  attributes appear on every log record, every span, every metric data point).
  The `opentelemetry-exporter-otlp` artifact already on classpath since Phase 2
  ships log + metric + span exporters from one jar — Phase 5 adds **zero** new
  SDK-side dependencies.
- **`OpenTelemetryAppender` (OTLP export) + the MDC injector wrapper** — declared
  in [`producer-service/src/main/resources/logback-spring.xml`](./producer-service/src/main/resources/logback-spring.xml)
  (and its byte-identical consumer mirror at
  [`consumer-service/src/main/resources/logback-spring.xml`](./consumer-service/src/main/resources/logback-spring.xml)).
  Two artifacts pulled from the `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`
  BOM that Phase 1 declared forward-compat for this exact moment:
  `opentelemetry-logback-appender-1.0` (the OTLP export appender) and
  `opentelemetry-logback-mdc-1.0` (the MDC injector). **Heads-up — both ship a
  class named `OpenTelemetryAppender` in different packages**: the
  `appender.v1_0.OpenTelemetryAppender` is the OTLP exporter (has the `install()`
  static); the `mdc.v1_0.OpenTelemetryAppender` is an appender WRAPPER that reads
  `Span.current()` and stamps `trace_id`/`span_id` into MDC before forwarding to
  its child appender. The MDC injector is wrapped around `CONSOLE` so the
  bracketed pattern `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]`
  resolves correctly for in-span events.
- **Inline `OpenTelemetryAppender.install(sdk)` in the `@Bean` factory** —
  the load-bearing PITFALL #5 mitigation (LOG-03 / D-08 / D-09). **The
  order-of-operations problem:** Logback initializes BEFORE the Spring
  `ApplicationContext` is built, so the `OpenTelemetryAppender` constructed
  at startup defaults to `OpenTelemetry.noop()`. We call `install(sdk)`
  inside the `@Bean` factory itself, immediately after
  `OpenTelemetrySdk.builder()...build()` returns and before `return sdk` —
  this avoids the Spring self-cycle that a `@PostConstruct` shape would
  create (the `@Configuration` bean would have to autowire the
  `OpenTelemetry` it itself produces) AND tightens the window in which logs
  can land in the noop replay queue. `install()` walks the global
  `LoggerContext`, finds every OTEL appender (including ones nested inside
  wrapper appenders like the MDC injector), and swaps the noop reference
  for the real SDK; the replay queue (1000-event default) is then drained.
  Nothing is lost in normal Spring Boot startup, but if `install()` is
  never called, log records beyond the buffer are silently dropped. **The
  install-inline-in-the-`@Bean`-factory shape IS the lesson** — the
  silent-no-op trap PLUS the Spring self-cycle that `@PostConstruct` would
  create are textbook OTel logback gotchas (see
  [opentelemetry-java-instrumentation#10307](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307)).

`mise run demo:order` now produces a console line per service with `trace_id` /
`span_id` stamped in brackets — pre-`POST` startup logs render
`[trace_id= span_id=]` (empty defaults via Logback's `%mdc{key:-}` syntax),
in-span logs render `[trace_id=4b2e... span_id=ad12...]`. The same trace_id
flows to Loki via the OTLP appender. In Grafana → Explore → Loki, run:

```
{service_name="order-producer"} |~ "<traceId>"
```

(replace `<traceId>` with the 32-hex value you copied from the console). Click
the `trace_id` field on a returned log line and Grafana opens the matching trace
in Tempo's Explore tab. **The triple-signal correlation highlight** lands on the
deterministic 10th order (Phase 3 APP-04): the consumer's `LOG.error` in
[`ProcessingService`](./consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java)
fires alongside the existing `span.recordException(e)` — the Loki query
`{service_name="order-consumer"} | severity_text="ERROR"` returns the failure log; click
its trace_id and Tempo shows the trace whose CONSUMER span carries the
recordException event AND a metric data point in Mimir for the same priority/method.
All three signals share the trace_id; the resource attributes (D-05) make the
identity match across Loki / Tempo / Mimir.

> **Why `severity_text="ERROR"` instead of `|= "ERROR"`?** The OTLP
> `OpenTelemetryAppender` ships the formatted message **without** a level
> prefix — the Logback level lands on the OTLP record as the `severity_text`
> field, which Loki's OTLP receiver indexes as a detected field / label. A
> substring filter against the message body (`|= "ERROR"`) returns zero
> results for `LOG.error("order processing failed: orderId={}", orderId, e)`
> because the formatted body is just `order processing failed: orderId=<uuid>`.
> Filter on the OTLP severity field — that's the OTel-idiomatic shape and
> what Loki's OTLP receiver was built around.

## Reading the code

The two `OtelSdkConfiguration.java` files are the workshop's textbook for the manual SDK setup. Open them in your IDE and read top-to-bottom — every `@Bean` carries an inline comment explaining what each builder call does and why (DOC-03):

- [`producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`](./producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java)
- [`consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`](./consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java)

The producer adds one extra file — [`HttpServerSpanFilter.java`](./producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java) — that wraps every non-`/actuator/*` HTTP request in a `SERVER` span. The consumer has no inbound HTTP business surface so it does not register the filter.

The five business-code span sites (one `SERVER`, two `INTERNAL`, one `PRODUCER`, one `CONSUMER`) all use the same pure-inline `try`/`Scope`/`try`/`catch`/`finally` template — no helper, no AOP. The boilerplate IS the lesson.

## Why is OtelSdkConfiguration.java duplicated?

The two SDK config files are duplicated per service on purpose (DOC-05). Refactoring them into a shared `@AutoConfiguration` bean in the `otel-bootstrap` module would hide one of the two readings the workshop is built around — the whole point of Phase 2 is that an attendee reads `OpenTelemetrySdk.builder()`, `Resource.getDefault().merge(...)`, `BatchSpanProcessor.builder(...)`, `Sampler.parentBased(Sampler.alwaysOn())`, `OtlpGrpcSpanExporter.builder().setEndpoint(...)`, and `ContextPropagators.create(...)` _twice_, in two slightly different files, and develops a feel for which lines are workshop-pedagogy boilerplate and which lines are service-identity. The two files differ in only five small ways (package, JavaDoc cross-reference, the service.name string, the tracer scope name, plus the producer-only `HttpServerSpanFilter` bean) — the diff is small enough to read in one viewing. The propagation pair Phase 3 introduces, by contrast, IS shared in `otel-bootstrap` because the symmetry of one inject method matched by one extract method IS that lesson. Different design forces drive different choices; the workshop teaches both.

## Why is the propagation pair shared?

The propagation pair lives in `otel-bootstrap/src/main/java/com/example/otel/amqp/` (PROP-04) and is shared across both services on purpose — the deliberate counterpart of Phase 2's per-service-duplicated `OtelSdkConfiguration.java`. Read these two callouts as a pair: per-service code (the SDK setup) is duplicated so attendees read it twice; cross-service code (the messaging boundary) is shared so attendees read ONE inject method matched by ONE extract method, and the symmetry IS the lesson.

The shared module exports four classes:

- [`TracingMessagePostProcessor.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java) — `MessagePostProcessor` that opens a `PRODUCER` span, calls `propagator.inject(Context.current(), props, SETTER)`, and ends. Wired on the producer's `RabbitTemplate` via `setBeforePublishPostProcessors(...)` in `RabbitConfig.rabbitTemplate(...)`.
- [`TracingMessageListenerAdvice.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java) — `MethodInterceptor` that calls `propagator.extract(Context.current(), props, GETTER)` and opens a `CONSUMER` span with `.setParent(extracted)` — the SINGLE LINE that makes `consumer.parentSpanId == producer.spanId`. Wired on the consumer's listener container factory via `setAdviceChain(...)` in `RabbitConfig.rabbitListenerContainerFactory(...)`.
- [`MessagePropertiesSetter.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java) and [`MessagePropertiesGetter.java`](./otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java) — the `TextMapSetter` / `TextMapGetter` pair that writes header values as `String` (never `byte[]`) and defensively `.toString()`s on read. PITFALLS.md #2 in two files.

The four classes carry zero Spring annotations; each service's `RabbitConfig.java` declares the explicit `@Bean` wiring. The Tracer is injected per service (`com.example.producer` / `com.example.consumer`), so spans created from inside `otel-bootstrap` still appear under each service's instrumentation scope in Tempo — the structural-not-semantic property that makes the shared module readable.

Phase 3 also corrects an OTel messaging semconv divergence from Phase 2: the producer's `messaging.destination.name` attribute is now the **exchange** (`orders`), not the queue (`orders.created`). This is visible in Tempo across the `step-02-traces` → `step-03-context-propagation` tags.

## What's NOT here yet

The following are deliberate Phase 1 omissions — the repo isn't incomplete, it's **uninstrumented on purpose** so each later phase has something concrete to add:

- No `OtelSdkConfiguration.java` (Phase 2)
- No integration tests (Phase 6)
- No pre-built Grafana dashboard or load script (Phase 7)
