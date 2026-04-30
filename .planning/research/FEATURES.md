# Feature Research

**Domain:** OpenTelemetry manual-SDK instrumentation workshop (Spring Boot 3.4.13 + Java 17 + RabbitMQ via Spring AMQP, single-container Grafana `otel-lgtm` backend, mise + docker-compose tooling)
**Researched:** 2026-04-29
**Confidence:** HIGH (pedagogy + SDK shape verified against OpenTelemetry official docs and semantic-conventions; AMQP propagation verified against community manual-instrumentation references)

> "Features" here means **workshop steps and code-level demonstrations**, not application functionality. The artifact is a hands-on internal-team workshop demo. The yardstick is pedagogical: does this feature help an attendee understand, by reading the code, what the OTel Java SDK actually does and how to extend it to a propagation boundary the auto-instrumentation does not cover?

## Feature Landscape

### Table Stakes (Workshop Fails Without These)

The workshop's **core teaching goal** (per `PROJECT.md`): an attendee can clone the repo, run it, hit `POST /orders`, and see one connected trace through HTTP → publish → consume → process, with correlated metrics and logs, and **understand exactly which lines of SDK code made each piece work**. Anything below is non-negotiable for that goal.

| # | Feature | Why Expected (Pedagogical Role) | Complexity | Notes |
|---|---------|----------------------------------|------------|-------|
| TS-1 | **Manual `OpenTelemetrySdk.builder()` bootstrap with explicit `Resource`, `SdkTracerProvider`, `SdkMeterProvider`, `SdkLoggerProvider`, `ContextPropagators`** | This *is* the lesson — show that the SDK is constructed by code, not magic. Every attendee must see `service.name` set, providers wired, and the SDK registered globally. | M | One Spring `@Configuration` class per service producing the `OpenTelemetry` bean. Use `OpenTelemetrySdk.builder().setTracerProvider(...).setMeterProvider(...).setLoggerProvider(...).setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).buildAndRegisterGlobal()`. Single source of truth for both services (copy-paste, not a shared module — see anti-features). |
| TS-2 | **Resource attributes set explicitly** (`service.name`, `service.version`, `service.instance.id`, `deployment.environment.name`) | Without this, the two services are indistinguishable in Tempo/Loki/Mimir. Also teaches resource-vs-span attribute distinction. | S | `Resource.getDefault().toBuilder().put(ServiceAttributes.SERVICE_NAME, "orders-producer").put(...).build()`. Different `service.name` per service is the minimum; the others demonstrate the convention. |
| TS-3 | **OTLP gRPC exporters for traces, metrics, AND logs, all pointing at the single `otel-lgtm` endpoint (`localhost:4317`)** | Workshop promise is "all three signals." Showing one endpoint receives all three reinforces that OTLP is one protocol, not three. | S | `OtlpGrpcSpanExporter`, `OtlpGrpcMetricExporter`, `OtlpGrpcLogRecordExporter`. gRPC chosen over HTTP/protobuf because `otel-lgtm` exposes both and gRPC is the more common production default. Endpoint configurable via env var so attendees can later point at a SaaS. |
| TS-4 | **`BatchSpanProcessor` + `PeriodicMetricReader` + `BatchLogRecordProcessor` with sensible defaults** | Production-shaped exporters (not `SimpleSpanProcessor`) so attendees see what a realistic setup looks like. Tests use `Simple` + in-memory — that contrast is itself a lesson. | S | Defaults are fine: BatchSpanProcessor (5s schedule, 2048 queue), PeriodicMetricReader (60s — but workshop should override to 10–15s so metrics show up in Mimir within demo time), BatchLogRecordProcessor (1s schedule). |
| TS-5 | **Sampler explicitly configured to `Sampler.parentBased(Sampler.alwaysOn())`** | Default *is* `parentBased(alwaysOn)`, but workshop must show the line of code so attendees understand the sampling decision is *theirs*. Comment in code references `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` as the production knob to turn. | S | One line + a comment block. Skip head/tail-sampling theory; just plant the seed so attendees know where to look. |
| TS-6 | **Graceful SDK shutdown on application exit** (`OpenTelemetrySdk.shutdown()` / `close()` via Spring `DisposableBean` or `@PreDestroy`) | Without this, the last batch of spans/metrics/logs is silently dropped on Ctrl-C — attendees will demo, not see data, and blame the backend. Also teaches that batch processors hold buffered data. | S | Spring lifecycle wiring; can be a single `@Bean(destroyMethod = "close")` on the `OpenTelemetrySdk` bean. |
| TS-7 | **Manual `Tracer` instrumentation around `POST /orders` HTTP entry point** (Servlet filter or controller-level wrapper, **not** `@WithSpan` annotation) | The workshop deliberately avoids the agent and the Micrometer bridge — so the HTTP root span has to be created by code attendees can read. This anchors the trace tree. | M | Spring `OncePerRequestFilter` extracting `traceparent` from incoming HTTP headers (defensive — even though there's no upstream caller in the demo, the pattern is the lesson) and starting a `SERVER`-kind span with `http.*` semconv attributes. |
| TS-8 | **Manual `PRODUCER`-kind span around `RabbitTemplate.convertAndSend()` with `TextMapSetter` injecting context into AMQP headers** | The headline lesson. Spring AMQP is **not** auto-instrumented by the OTel agent (per `PROJECT.md` pedagogical hook). This is exactly the production scenario the workshop equips attendees for. | M | `MessagePostProcessor` lambda that takes the `Message`, calls `propagators.getTextMapPropagator().inject(Context.current(), message.getMessageProperties(), setter)` against `MessageProperties.setHeader(key, value)`. Span name `publish orders.exchange` per messaging semconv. |
| TS-9 | **Manual `CONSUMER`-kind span inside `@RabbitListener` with `TextMapGetter` extracting context from AMQP headers and `Context.makeCurrent()`** | The other half of TS-8. Without extraction, producer and consumer spans live in separate traces — visually breaking the lesson. | M | Wrap listener method body in `propagators.extract(Context.current(), message.getMessageProperties(), getter)`, then `tracer.spanBuilder("process orders.queue").setParent(extractedContext).setSpanKind(CONSUMER).startSpan()`. Span name `process orders.queue` per semconv. |
| TS-10 | **One nested domain span inside the consumer's processing logic** (e.g., `validateOrder`, `calculateShipping`) | Demonstrates the most common real-world need: child spans inside business logic. Without this attendees leave thinking spans only happen at I/O boundaries. | S | `tracer.spanBuilder("orders.validate").startSpan()` with try/finally end + scope. Shows `Context.current()` already carries the consumer span as parent — no manual parenting required. |
| TS-11 | **Messaging semantic-convention attributes on producer/consumer spans** (`messaging.system=rabbitmq`, `messaging.destination.name`, `messaging.operation.name`, `messaging.rabbitmq.destination.routing_key`, `messaging.message.body.size`) | Without semconv, Tempo's "Service Graph" and Mimir's RED metrics don't light up correctly. Also: semantic conventions are themselves a workshop lesson — "don't invent attribute names." | S | A small `MessagingAttributes` helper class is fine. Reference [messaging-spans semconv](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/). |
| TS-12 | **HTTP semantic-convention attributes on the entry-point span** (`http.request.method`, `url.path`, `http.response.status_code`, `server.address`, `server.port`) | Same reason as TS-11 — semconv is the contract. Tempo's service-graph operator depends on these. | S | Standard semconv keys. Don't go deeper than the stable set. |
| TS-13 | **Three concrete metric instruments with semconv-aligned names**: `Counter` (`orders.created`), `Histogram` (`http.server.request.duration` in seconds), `ObservableGauge` (e.g., `orders.queue.depth.estimate`) | Demonstrates the three instrument *shapes* attendees will encounter (sync monotonic, sync distribution, async). Without an async instrument the lesson feels incomplete. | M | `LongCounter`, `DoubleHistogram` (record in seconds, not millis, per semconv), `meter.gaugeBuilder("...").buildWithCallback(measurement -> measurement.record(currentDepth))`. Histogram boundaries: leave default — explanation overhead doesn't pay off in workshop time. |
| TS-14 | **OpenTelemetry Logback appender configured in `logback-spring.xml` with MDC trace_id/span_id pattern** | Logs without `trace_id`/`span_id` cannot be jumped-to-from-trace in Grafana. The whole "all three signals correlate" pitch falls flat. | S | Two-step setup: (1) `OpenTelemetryAppender` from `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` for OTLP log export, (2) `OpenTelemetryAppender` from `opentelemetry-logback-mdc-1.0` for MDC injection. logback-spring.xml pattern: `%d{HH:mm:ss.SSS} [trace_id=%X{trace_id} span_id=%X{span_id}] %-5level %logger{36} - %msg%n`. Programmatic install: `OpenTelemetryAppender.install(openTelemetry)` after SDK is built. |
| TS-15 | **Single-container `otel-lgtm` infrastructure via `docker-compose.yml`, exposing OTLP gRPC (4317), OTLP HTTP (4318), Grafana (3000)** | Teaching artifact must "just run." Five separate containers (collector, Tempo, Mimir, Loki, Grafana) is ops noise the workshop is explicitly avoiding. | S | `grafana/otel-lgtm` image. Datasources are pre-wired in the image — no provisioning needed. Workshop-time demo: open Grafana → Explore → Tempo → search by service. |
| TS-16 | **`docker-compose.yml` for RabbitMQ with management UI (port 15672) exposed** | Attendees will want to see the queue + the message + the AMQP headers (the propagation lesson is *visible* in the management UI when you click into a queued message). The UI is itself a teaching aid. | S | `rabbitmq:3.13-management-alpine` (or current LTS). Single direct exchange + single queue declared by Spring AMQP `Declarables` bean. |
| TS-17 | **`mise.toml` pinning Amazon Corretto 17 + Maven, with `dev`, `test`, `infra:up`, `infra:down` tasks** | "One command operation" promised by `PROJECT.md`. Attendees with mise installed get reproducible toolchain automatically. | S | Standard mise config. `mise run dev` likely starts both services in parallel (mise tasks support `depends`/parallel). |
| TS-18 | **Step-by-step `README.md` mapping each commit/branch to a specific OTel concept**, with copy-pasteable curl commands and Grafana navigation hints | The workshop without a README is a code-archaeology exercise, not a workshop. README is the lesson plan. | M | One section per checkpoint. Each section: (a) what you'll learn, (b) `git checkout step-NN`, (c) what changed, (d) commands to run, (e) what to look for in Grafana. Screenshots are differentiators (DF-3), not table-stakes. |
| TS-19 | **Staged git checkpoints (one branch OR tag per workshop step)** — minimum sequence: `step-00-baseline` → `step-01-sdk-bootstrap` → `step-02-http-traces` → `step-03-amqp-propagation` → `step-04-metrics` → `step-05-logs` → `step-06-tests` | Workshop format demands "git checkout to follow along." Without checkpoints the codebase is a frozen snapshot, not a progressive lesson. | M | **Tags over branches** for workshop steps (immutable, no accidental commits onto). Use `step-NN-slug` naming. CI must pass at every checkpoint — half-built checkpoints break trust. |
| TS-20 | **Testcontainers integration test exercising producer → consumer path with assertions on exported spans** | Without tests, the workshop teaches "instrument and hope." With tests (against an `InMemorySpanExporter` driven by the *same SDK builder logic* as production), it teaches "instrument and verify." | M | JUnit 5 + `@SpringBootTest` + Testcontainers RabbitMQ. Test-only `@Configuration` swaps OTLP exporter for `InMemorySpanExporter` + `SimpleSpanProcessor` (note: not `BatchSpanProcessor` — assertions need synchronous flush). Use `OpenTelemetryExtension` from `opentelemetry-sdk-testing` to simplify. Assert: producer span → consumer span → domain span share trace_id; semconv attributes present; expected count. |
| TS-21 | **A "broken first, then fixed" propagation demonstration** — the `step-02-http-traces` checkpoint shows traces working *within* a service but the producer and consumer spans living in **separate** traces, and `step-03-amqp-propagation` adds the inject/extract code that joins them | This is the most powerful pedagogical moment in the entire workshop. Showing the broken state before the fix burns the lesson into memory in a way that "here's the working version" cannot match. Aligns with the propagation gap explicitly called out in `PROJECT.md`. | S (delta is small; setup is just a missing inject/extract pair) | Important: `step-02` must still produce *valid* spans on both sides — only the propagation link is missing. Two trace IDs in Tempo, side-by-side, is the visual. README screenshot here is high-value. |

### Differentiators (Elevate Workshop From Good to Great)

These are not required for the lesson to land, but they materially improve attendee experience or increase the workshop's reusability/longevity.

| # | Feature | Value Proposition | Complexity | Notes |
|---|---------|-------------------|------------|-------|
| DF-1 | **Pre-built Grafana dashboard JSON** showing the demo's spans/metrics/logs, provisioned into otel-lgtm via volume mount | Saves 10+ minutes of "click here, then here, then this datasource" during the workshop. Lets the instructor focus on code, not Grafana navigation. Becomes the model dashboard attendees take back to their teams. | M | otel-lgtm supports dashboard provisioning via mounted JSON. Build the dashboard against a *running* demo, then export. Single-row layout: trace search → service-graph → RED metrics → log view filtered by trace_id. |
| DF-2 | **A small load-generator script** (`scripts/load.sh`, plain `curl`/`hey`/`oha` loop) that produces a steady trickle of `POST /orders` traffic | Live demos with single requests look quiet. A trickle of background load makes Grafana panels visibly *move* during the talk. Also lets the instructor demonstrate sampling effects (DF-7 below) with realistic volume. | S | One file, ~20 lines. `oha` (in mise) or vanilla `bash` + `curl`. Doesn't need to be a benchmarking harness (anti-feature) — just steady-state trickle. |
| DF-3 | **README screenshots from Grafana** at every step (trace waterfall, broken-vs-fixed propagation side-by-side, log lines with trace_id, metrics panel) | Attendees who skim the README before the workshop, or refer back later, get the visual outcome without running the demo. Materially improves discoverability if the repo is shared internally. | M | One screenshot per major checkpoint. Keep PNGs in `docs/screenshots/`. The "broken vs fixed" pair (TS-21) is the highest-impact image. |
| DF-4 | **Workshop facilitator notes** (`docs/FACILITATOR.md`) — talking points per step, common attendee questions, expected pitfalls, timing estimates | Lets a non-author run the workshop. Increases the artifact's lifespan beyond its creator. | M | One section per checkpoint. ~5–10 lines each. "If an attendee asks X, the answer is Y." |
| DF-5 | **Custom domain attributes on the order span** (`order.id`, `order.item_count`, `order.total_cents`) plus a custom event (`order.validation_failed`) | Demonstrates the second-most-common real-world need (after propagation): tagging spans with domain context. The event demonstrates `Span.addEvent(...)`, which is otherwise underused/unfamiliar. | S | Trivial code; high pedagogical payoff. Use *non-namespaced* attribute keys deliberately to teach the "your own attributes go under your own namespace" rule. |
| DF-6 | **Span exception recording** (`span.recordException(e); span.setStatus(StatusCode.ERROR)` in a catch block) | Errors-as-spans is fundamental and frequently misunderstood (vs. just logging). One try/catch in the consumer with a deterministic 10% failure rate makes the failure visible in Tempo. | S | Inject a `RuntimeException` for orders with `id % 10 == 0`. Visible immediately as red spans in Tempo. |
| DF-7 | **A second checkpoint variant showing `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))`** as a demonstrable change | Sampling theory is dry; seeing 90% of incoming requests *not* produce traces (while metrics still fully populate, because metrics are not sampled) is a concrete, memorable lesson. | S | Single line change + a re-run with the load generator. Pairs naturally with DF-2. Could be a tag like `step-04b-sampling`. |
| DF-8 | **Baggage propagation example** — set a `tenant.id` baggage entry in the producer, read it in the consumer | Baggage is the OTel feature most attendees don't know exists. A concrete example differentiates this workshop from the dozens of trace-only tutorials online. | S | `Baggage.current().toBuilder().put("tenant.id", "acme").build().makeCurrent()` in producer; `Baggage.fromContext(extractedContext).getEntryValue("tenant.id")` in consumer. Requires `W3CBaggagePropagator` added to the propagator composition (`TextMapPropagator.composite(...)`). |
| DF-9 | **A `make`-style task list (`mise tasks` output formatted in the README)** showing all available one-liners | Quality-of-life for attendees. "What can I run?" answered in ten seconds. | S | `mise tasks` already prints this; just include sample output in README. |
| DF-10 | **Comment density on SDK setup code** — every non-obvious line of the `OpenTelemetryConfig` class commented with *why*, not *what* | The code is the textbook. Comments are the annotations. This is what separates "demo code" from "workshop material." | S | Disciplined writing, not engineering. Reference URLs to OTel docs in comments. |
| DF-11 | **A consumer-side dead-letter / retry simulation that produces a span event chain** | Shows multi-event timeline within a single span — another common production pattern. Optional; only if time permits and the v2 RabbitMQ topology hasn't been excluded by the v1 scope. **Likely deferred** given `PROJECT.md` explicitly de-scopes DLX. | M | Listed for completeness but probably belongs in a v2. |

### Anti-Features (Deliberate Exclusions With Reasoning)

Each of these is something a well-meaning contributor might propose. Each must be rejected with a written reason so the rejection sticks across milestones.

| # | Anti-Feature | Why Tempting | Why It Dilutes the Lesson | Alternative |
|---|--------------|--------------|---------------------------|-------------|
| AF-1 | **OpenTelemetry Java Agent** (`-javaagent:opentelemetry-javaagent.jar`) | "It just works" — auto-instruments HTTP, JDBC, RabbitMQ adapters with zero code. | Hides the entire mechanism the workshop exists to expose. Attendees would walk away thinking OTel is a black-box flag, not an SDK. Also: the agent does **not** instrument Spring AMQP, so the headline lesson (manual AMQP propagation) gets lost in the agent narrative. | Manual SDK calls in code attendees can read and step through with a debugger. (Already a Key Decision in `PROJECT.md`.) |
| AF-2 | **Spring Boot OTel starter / `spring-boot-starter-opentelemetry` / Micrometer-tracing-bridge-otel** | Spring-native, idiomatic, "the right way for Spring Boot 3." | Wraps the SDK in a Spring abstraction layer. Attendees never see `OpenTelemetrySdk.builder()`, `Tracer`, `Meter`, or `Logger` directly. The lesson reduces to "configure properties." Also: the starter is Spring Boot 4-era; Spring Boot 3.4.13 + manual SDK is the *exact* gap most attendees' production codebases live in. | Direct SDK wiring in a `@Configuration` class. (Already a Key Decision in `PROJECT.md`.) |
| AF-3 | **Auto-instrumentation library JARs** (e.g., `opentelemetry-spring-boot-autoconfigure`, `opentelemetry-instrumentation-spring-webmvc`) | "Manual SDK *plus* a few helpful instrumentations" is a tempting middle ground. | The middle ground is the worst place to teach from. Attendees can't tell which spans came from their code vs. the library. The mental model becomes muddy. The MDC-injection appender is a deliberate, narrow exception (it's the only sane way to do MDC injection — see TS-14). | Pure manual instrumentation. The MDC appender is a one-line library use, not auto-instrumentation. |
| AF-4 | **Micrometer integration** (`MeterRegistry`, `@Timed`, `Counter.builder()`) | Spring Boot 3 ships with Micrometer enabled by default; "just bridge it to OTel." | The OTel `Meter` API and Micrometer `MeterRegistry` are different mental models. Showing both confuses attendees about which one the SDK is. Bridge-bashing is also production-relevant — but it's a follow-up workshop, not this one. | Disable Micrometer's OTel integration explicitly (or simply don't add the bridge dependency). Use `Meter` directly. Add a one-line README note acknowledging Micrometer exists. |
| AF-5 | **Richer RabbitMQ topology** (topic exchange, fanout, RPC, work queues, multiple consumers) | "More realistic." | Multiplies surface area without multiplying instrumentation lessons. The propagation pattern is identical for one queue or ten. Diluting attention onto routing keys, binding wildcards, and consumer groups robs time from SDK lessons. | One direct exchange, one queue, one consumer. (Already a Key Decision in `PROJECT.md`.) Workshop README can have a "Where this generalizes" paragraph. |
| AF-6 | **Multiple message patterns** (request-reply / RPC, fanout broadcast) | "Show how propagation works in different topologies." | Same root issue as AF-5. RPC over AMQP also requires bidirectional context propagation, which is interesting but is a *second* workshop. Adding it crowds out the headline lesson. | Defer to v2. README's "Further reading" can link to the OTel messaging spec for RPC pattern. |
| AF-7 | **Lower-level `com.rabbitmq:amqp-client` examples** | "Closer to the metal — see what Spring AMQP hides." | Attendees use Spring AMQP. The whole point is to show propagation **in the API they actually have**. Showing the lower-level client is a different lesson (one that's already well-covered by community examples). | Spring AMQP only. (Already a Key Decision in `PROJECT.md`.) |
| AF-8 | **GraalVM native-image build** | "Modern Spring Boot 3 deployment story." | Adds JVM/build complexity orthogonal to OTel. Native-image *also* has its own OTel quirks (reflection config, missing classes), which would derail the workshop into Graal debugging. | JVM only on Corretto 17. (Already a Key Decision in `PROJECT.md`.) |
| AF-9 | **Cloud observability backends** (Honeycomb, Datadog, Lightstep, AWS X-Ray, GCP Trace) as the demo target | "Show what production looks like." | Adds account setup, API keys, billing — exactly the friction the workshop is engineered to eliminate. Also: pointing OTel at a SaaS is genuinely a one-line endpoint change, so attendees can do it themselves later. | `otel-lgtm` only. README has a one-line "to point at vendor X, change `OTEL_EXPORTER_OTLP_ENDPOINT`" note. (Already a Key Decision in `PROJECT.md`.) |
| AF-10 | **A web UI / frontend** generating traces from browser to backend | "End-to-end distributed tracing!" | A frontend introduces a whole second instrumentation track (browser OTel SDK, CORS, `traceparent` over CORS). The workshop is server-side. | curl + load script (DF-2). (Already a Key Decision in `PROJECT.md`.) |
| AF-11 | **Authentication / authorization on `POST /orders`** | "Make it more realistic." | Spring Security is its own learning curve and produces noise spans (`SecurityFilterChain`) that crowd the trace view. Irrelevant to the instrumentation lesson. | None. Endpoint is open. (Already a Key Decision in `PROJECT.md`.) |
| AF-12 | **Database persistence** (JPA, R2DBC, JDBC, Postgres) | "Real apps have a DB; show JDBC instrumentation." | JDBC instrumentation is *its own* workshop — and importantly, it's one where the auto-instrumentation **does** work, so the lesson is different. Mixing it in here muddies the manual-vs-auto narrative. | In-memory simulation in the consumer (sleep + log). (Already a Key Decision in `PROJECT.md`.) |
| AF-13 | **Performance benchmarking / load testing harness** (JMH, Gatling, k6) | "Measure overhead of instrumentation." | Distinct skill (load tooling) and distinct lesson (overhead measurement). The workshop teaches *how* to instrument, not *how much* it costs. | DF-2 load script for visual liveliness only. JMH-style measurement is a follow-up. |
| AF-14 | **Distributed deployment** (multiple hosts, k8s manifests, Helm, Tilt, Skaffold) | "Distributed tracing needs a distributed system." | Two services on a laptop with one shared trace ID is *already* distributed for the trace's purposes — the trace doesn't know or care that both services are on `localhost`. K8s overhead would dwarf SDK lessons. | Both services on host via mise. (Already a Key Decision in `PROJECT.md`.) |
| AF-15 | **A shared library / module containing the OTel setup** | "DRY — both services configure OTel identically." | The whole pedagogical point is that **attendees can read the SDK setup**. Hiding it behind `import com.example.shared.OtelConfig` makes it invisible. Two near-identical files is the *correct* shape for a teaching artifact. | Copy-paste the `OpenTelemetryConfig` class into both services. Add a README note: "yes, this is duplicated on purpose." |
| AF-16 | **Custom histogram bucket boundaries / Views API tutorial** | "Show how to customize aggregations." | The Views API is powerful and complex. A 10-minute detour into it crowds out the propagation lesson. | Default histogram boundaries. Mention the Views API in README's "Further reading." |
| AF-17 | **Logback-only logging without the OTLP log appender** | "Just put `trace_id` in the pattern, ship logs to Loki via Promtail." | Splits the "all three signals via OTLP" story. The workshop's `otel-lgtm` setup happily receives OTLP logs; using Promtail introduces a second pipeline for no pedagogical gain. | OTLP log export via `OpenTelemetryAppender`. (TS-14.) |
| AF-18 | **`@WithSpan` annotation usage** (from `opentelemetry-instrumentation-annotations`) | "Cleaner code than try/finally with scopes." | `@WithSpan` is an auto-instrumentation feature that requires either the Java agent or AspectJ weaving. Workshop philosophy is explicit code, not magic annotations. Showing it would also send the message that try/finally is "the bad way" — when in fact it's the only way that works without the agent. | Explicit `tracer.spanBuilder(...).startSpan()` + try-with-resources `Scope` + `try/finally span.end()`. The verbosity *is* the lesson. |
| AF-19 | **`OpenTelemetryRule` / JUnit 4 patterns in tests** | Older blog posts use this. | OpenTelemetry Java has a JUnit 5 `OpenTelemetryExtension` that's the current recommendation. JUnit 4 is legacy. | JUnit 5 + `OpenTelemetryExtension` from `opentelemetry-sdk-testing`. (TS-20.) |
| AF-20 | **Pyroscope / continuous profiling integration** | otel-lgtm bundles Pyroscope; "free signal." | Profiling is a *fourth* signal with its own SDK and its own pedagogical surface. The workshop promises traces+metrics+logs (per `PROJECT.md` — the three OTel signals). Adding profiling crowds the agenda. | Mention in README "Further reading" — the otel-lgtm container exposes Pyroscope on port 4040 if attendees want to explore. |

## Feature Dependencies

```
TS-1 (SDK bootstrap)
 ├──requires──> TS-2 (Resource attributes)
 ├──enables───> TS-3 (OTLP exporters)
 │                └──requires──> TS-15 (otel-lgtm running)
 ├──enables───> TS-4 (Batch processors)
 ├──enables───> TS-5 (Sampler)
 └──requires──> TS-6 (Graceful shutdown)

TS-1 ──enables──> TS-7  (HTTP entry-point span)
TS-1 ──enables──> TS-8  (PRODUCER span + inject)
TS-1 ──enables──> TS-9  (CONSUMER span + extract)

TS-8 ──pairs-with──> TS-9   (publish/consume must round-trip)
TS-8 + TS-9 ──require──> TS-16 (RabbitMQ container)

TS-9 ──enables──> TS-10 (Domain span as child)
TS-7, TS-8, TS-9 ──require──> TS-11/TS-12 (Semconv attributes)

TS-1 ──enables──> TS-13 (Metric instruments)
TS-1 ──enables──> TS-14 (Logback appender + MDC)
                       └──requires──> TS-7/TS-8/TS-9 (active spans for MDC to inject)

TS-7..TS-14 ──enable──> TS-20 (Testcontainers test verifies the chain)
TS-7..TS-14 ──enable──> TS-21 (Broken-then-fixed demo)
                            └──pairs-with──> TS-19 (Git checkpoints make broken/fixed visible)

TS-17 (mise) ──enables──> TS-15, TS-16, TS-20 (one-command run/test)
TS-18 (README) ──enhances──> all checkpoints
TS-19 (Git checkpoints) ──structures──> TS-1 through TS-20

DF-1 (Dashboard)         ──enhances──> TS-15 (otel-lgtm visibility)
DF-2 (Load script)       ──enhances──> DF-1, DF-7
DF-3 (Screenshots)       ──enhances──> TS-18, TS-21
DF-4 (Facilitator notes) ──enhances──> TS-18
DF-5 (Domain attributes) ──enhances──> TS-7, TS-9, TS-10
DF-6 (Exception recording) ──enhances──> TS-10
DF-7 (Sampling variant)  ──requires──> DF-2 (visible volume)
DF-8 (Baggage)           ──enhances──> TS-8, TS-9 (extends the propagation lesson)
DF-10 (Comments)         ──enhances──> TS-1 (the most-read class)

AF-1 (Java agent)        ──conflicts-with──> TS-1 (defeats the lesson)
AF-2 (Spring OTel starter) ──conflicts-with──> TS-1
AF-4 (Micrometer bridge) ──conflicts-with──> TS-13
AF-15 (Shared OTel lib)  ──conflicts-with──> DF-10, TS-18 (hides what should be visible)
AF-18 (@WithSpan)        ──conflicts-with──> TS-7, TS-9, TS-10 (smuggles auto-instrumentation in)
```

### Dependency Notes

- **TS-14 (logs correlation) requires TS-7/TS-8/TS-9 (active spans):** The MDC appender injects `trace_id`/`span_id` from the *current* span. Without active spans, the MDC keys are empty and the lesson is silent — there is nothing to correlate. Span instrumentation must precede log correlation in the workshop sequence.
- **TS-21 (broken-first-then-fixed) requires TS-19 (git checkpoints):** The demonstration only works if attendees can `git checkout step-02` (broken) and `git checkout step-03` (fixed) and see the difference. Without checkpoints, the lesson degrades to "trust me, it would have been broken."
- **TS-20 (Testcontainers test) requires TS-1 to be testable in isolation:** The SDK bootstrap (TS-1) must accept an injectable `SpanExporter` (or the test must be able to swap providers via `@TestConfiguration`) so the in-memory exporter can substitute the OTLP one. Plan TS-1 with that injection point in mind from day one.
- **DF-7 (sampling variant) requires DF-2 (load script):** A 10% sampling rate is invisible with single hand-issued requests. You need volume to see the effect.
- **AF-15 (shared library) conflicts with TS-18 (README) and DF-10 (comments):** A shared library would hide the SDK setup from the README walk-through and make per-line comments meaningless ("see other repo"). Duplication is the correct teaching shape.
- **AF-1/AF-2 (agent / starter) conflict with TS-1:** They each define `OpenTelemetry` for you. Including them silently turns the manual workshop into an auto-instrumentation tour.

## MVP Definition

### Launch With (v1 — the workshop must ship with all of these)

The "minimum viable workshop" is the smallest set that lets the core promise — *clone, run, see one connected trace through HTTP → publish → consume → process, with correlated metrics and logs* — actually work. Per `PROJECT.md`, this is also the entire scope of v1.

- [ ] TS-1 SDK bootstrap — *the lesson*
- [ ] TS-2 Resource attributes — without these, services are anonymous
- [ ] TS-3 OTLP exporters (3 signals) — fulfills the "all three signals" promise
- [ ] TS-4 Batch processors — production-shaped
- [ ] TS-5 Sampler (parentBased + alwaysOn) — explicit, even if default
- [ ] TS-6 Graceful shutdown — without this, demos fail silently
- [ ] TS-7 HTTP entry-point span — anchors the trace
- [ ] TS-8 PRODUCER span + inject — half of the headline lesson
- [ ] TS-9 CONSUMER span + extract — other half of the headline lesson
- [ ] TS-10 Domain span — most-common real-world need
- [ ] TS-11 Messaging semconv — without these, Tempo Service Graph is broken
- [ ] TS-12 HTTP semconv — same reason
- [ ] TS-13 Three metric instruments — covers all three instrument shapes
- [ ] TS-14 Logback appender + MDC — fulfills the correlation promise
- [ ] TS-15 otel-lgtm container — fulfills the "single backend" promise
- [ ] TS-16 RabbitMQ container — required for the message path
- [ ] TS-17 mise.toml — fulfills "one command operation"
- [ ] TS-18 Step-by-step README — without this, the codebase is not a workshop
- [ ] TS-19 Git checkpoints — fulfills "git checkout to follow along"
- [ ] TS-20 Testcontainers integration test — fulfills "verify, not hope"
- [ ] TS-21 Broken-then-fixed propagation — the most powerful single moment

### Add After Validation (v1.x — ship if first-cohort feedback indicates)

Lightweight differentiators with high pedagogical payoff and low effort. Add only if the v1 workshop *runs cleanly* with attendees first.

- [ ] DF-1 Pre-built Grafana dashboard — trigger: instructor reports too much "click around in Grafana" time during workshop
- [ ] DF-2 Load generator script — trigger: live-demo panels look static
- [ ] DF-3 README screenshots — trigger: out-of-workshop readers ask "what does this look like?"
- [ ] DF-5 Custom domain attributes + event — trigger: attendees ask "how do I tag my own data?"
- [ ] DF-6 Span exception recording — trigger: attendees ask "how do I deal with errors?"
- [ ] DF-10 Heavy comments on `OpenTelemetryConfig` — should arguably be v1, but tighten in v1.x after instructor reads through with fresh eyes

### Future Consideration (v2+)

- [ ] DF-4 Facilitator notes — trigger: someone other than the author wants to run the workshop
- [ ] DF-7 Sampling variant checkpoint — trigger: attendees ask about production sampling
- [ ] DF-8 Baggage propagation — trigger: a follow-up workshop on cross-cutting attributes
- [ ] DF-11 Dead-letter / retry simulation — trigger: explicit v2 with richer AMQP topology (re-evaluate AF-5/AF-6 then)
- [ ] JDBC / database instrumentation lesson — separate workshop
- [ ] Multi-pattern AMQP (RPC, fanout, topic) — separate workshop
- [ ] Cloud-vendor exporter swap demo — separate cookbook entry, not a workshop step
- [ ] Pyroscope profiling integration — fourth-signal follow-up
- [ ] Performance benchmarking of instrumentation overhead — separate skill, separate workshop

## Feature Prioritization Matrix

| Feature | Pedagogical Value | Implementation Cost | Priority |
|---------|-------------------|---------------------|----------|
| TS-1 SDK bootstrap | HIGH | MEDIUM | P1 |
| TS-2 Resource attributes | HIGH | LOW | P1 |
| TS-3 OTLP exporters (3 signals) | HIGH | LOW | P1 |
| TS-4 Batch processors | MEDIUM | LOW | P1 |
| TS-5 Sampler (explicit) | MEDIUM | LOW | P1 |
| TS-6 Graceful shutdown | HIGH (silent failures kill demos) | LOW | P1 |
| TS-7 HTTP entry-point span | HIGH | MEDIUM | P1 |
| TS-8 PRODUCER + inject | HIGH (headline lesson) | MEDIUM | P1 |
| TS-9 CONSUMER + extract | HIGH (headline lesson) | MEDIUM | P1 |
| TS-10 Domain span | HIGH | LOW | P1 |
| TS-11 Messaging semconv | MEDIUM | LOW | P1 |
| TS-12 HTTP semconv | MEDIUM | LOW | P1 |
| TS-13 Three metric instruments | HIGH | MEDIUM | P1 |
| TS-14 Logback appender + MDC | HIGH (correlation promise) | LOW | P1 |
| TS-15 otel-lgtm container | HIGH | LOW | P1 |
| TS-16 RabbitMQ container | HIGH | LOW | P1 |
| TS-17 mise.toml | MEDIUM | LOW | P1 |
| TS-18 Step-by-step README | HIGH | MEDIUM | P1 |
| TS-19 Git checkpoints | HIGH | MEDIUM | P1 |
| TS-20 Testcontainers test | HIGH | MEDIUM | P1 |
| TS-21 Broken-then-fixed | HIGH (most memorable moment) | LOW | P1 |
| DF-1 Pre-built dashboard | MEDIUM | MEDIUM | P2 |
| DF-2 Load script | MEDIUM | LOW | P2 |
| DF-3 README screenshots | MEDIUM | MEDIUM | P2 |
| DF-4 Facilitator notes | LOW (in-house run) → MEDIUM (re-runs) | MEDIUM | P3 |
| DF-5 Custom domain attributes + event | MEDIUM | LOW | P2 |
| DF-6 Span exception recording | MEDIUM | LOW | P2 |
| DF-7 Sampling variant | LOW for v1 | LOW | P3 |
| DF-8 Baggage | LOW for v1 | LOW | P3 |
| DF-9 mise tasks in README | LOW | LOW | P3 |
| DF-10 Comment density | HIGH | LOW | P1 (treat as v1 quality bar, not differentiator) |
| DF-11 Dead-letter simulation | LOW for v1 | MEDIUM | P3 |

**Priority key:**
- **P1**: Must ship in v1. Workshop's core promise depends on it.
- **P2**: Add post-validation if first cohort exposes a gap.
- **P3**: Defer to a follow-up workshop or v2+.

**Note on DF-10:** Although categorized as a differentiator structurally, comment quality on the SDK setup class is the workshop's *primary teaching surface* (the code itself is the textbook). Treat it as a v1 quality bar.

## Comparable Workshops Analysis

These are the closest analogs — used to sanity-check feature scope, not to copy.

| Feature | Grafana opentelemetry-workshop | NovatecConsulting opentelemetry-training | OTel Java examples repo | Our Approach |
|---------|-------------------------------|------------------------------------------|--------------------------|---------------|
| Stack | Polyglot (Go/Java/Python/.NET) | Multi-language modules | Java only | Java 17 + Spring Boot 3.4.13 only — narrow and deep |
| Backend | Grafana stack | Multiple (collector + various) | None (examples only) | Single-container `otel-lgtm` |
| Manual SDK focus | Mixed (auto + manual) | Heavy focus on collector + concepts | Manual SDK examples (no narrative) | **Pure manual SDK with narrative** |
| Spring Boot integration | Limited | Limited | None | Spring Boot 3.4.13 first-class |
| RabbitMQ | Not covered | Not the primary focus | Not covered | **Headline integration** |
| Git checkpoints | Per-exercise structure | Per-lab structure | Single-snapshot | Tagged checkpoints per lesson |
| Broken-then-fixed | Not used | Not used | Not used | **Used for AMQP propagation gap** |
| Testcontainers verification | No | No | No | **First-class lesson** |
| Audience | Public/community | Public/community | Reference material | Internal engineering team (narrower, deeper) |

The differentiation thesis: **no existing workshop combines (manual SDK + Spring Boot + RabbitMQ propagation gap + verification tests + tagged-checkpoint pedagogy)**. That's the niche.

## Confidence Notes

- **HIGH** confidence on table-stakes selection: each TS-N feature is directly grounded in `PROJECT.md`'s Active Requirements and the OTel Java SDK's documented surface area (verified via official OTel docs, semantic-conventions specs, and SDK Javadoc).
- **HIGH** confidence on anti-feature exclusions: each AF-N is either directly listed in `PROJECT.md`'s Out of Scope, or follows from the workshop's stated pedagogical goal (e.g., AF-15 shared library, AF-18 `@WithSpan`).
- **MEDIUM** confidence on the exact ordering of git checkpoints (TS-19): the proposed sequence (`baseline → sdk-bootstrap → http-traces → amqp-propagation → metrics → logs → tests`) follows logical dependency order but could legitimately reorder metrics/logs depending on whether the instructor wants the "all three signals" reveal earlier. Final order should be validated during planning Phase 1 with a dry-run.
- **MEDIUM** confidence on TS-21 (broken-then-fixed) being placed at exactly `step-02 → step-03`. The lesson is correct; the exact step numbers are subject to checkpoint sequencing.
- **LOW** confidence in the necessity of DF-7 (sampling variant) for the workshop's core audience — added as differentiator but might be a distraction even at P3 if the audience is purely focused on the manual-SDK lesson.

## Sources

### Authoritative (HIGH confidence)
- [OpenTelemetry Java SDK — Manage Telemetry](https://opentelemetry.io/docs/languages/java/sdk/) — Builder pattern, BatchSpanProcessor, PeriodicMetricReader, BatchLogRecordProcessor recommendations
- [OpenTelemetry Java API — Record Telemetry](https://opentelemetry.io/docs/languages/java/api/) — Tracer, Meter, Logger, instrument types
- [Semantic conventions for messaging spans](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/) — span names, attributes, span kinds for producer/consumer
- [Semantic conventions for messaging metrics](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-metrics/) — metric naming
- [Messaging attribute registry](https://opentelemetry.io/docs/specs/semconv/registry/attributes/messaging/) — `messaging.system`, `messaging.destination.name`, `messaging.operation.name`
- [opentelemetry-logback-mdc-1.0 README](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-mdc-1.0/library/README.md) — MDC injection setup, key names, logback.xml pattern
- [Logger MDC instrumentation docs](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/logger-mdc-instrumentation.md) — `trace_id`, `span_id`, `trace_flags` MDC keys
- [opentelemetry-java InMemorySpanExporter source](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/exporter/InMemorySpanExporter.java) — testing pattern
- [opentelemetry-java OpenTelemetryExtension JUnit 5](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/testing/src/main/java/io/opentelemetry/sdk/testing/junit5/OpenTelemetryExtension.java) — JUnit 5 test pattern
- [grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) — image, ports, dashboard provisioning
- [Docker OpenTelemetry LGTM (Grafana docs)](https://grafana.com/docs/opentelemetry/docker-lgtm/) — endpoint and protocol defaults
- [W3CTraceContextPropagator Javadoc](https://javadoc.io/static/io.opentelemetry/opentelemetry-api/1.1.0/io/opentelemetry/api/trace/propagation/W3CTraceContextPropagator.html) — propagator API
- [MessagePostProcessor (Spring AMQP)](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/core/MessagePostProcessor.html) — header injection extension point

### Comparable workshops referenced (HIGH confidence — repos exist)
- [Grafana opentelemetry-workshop](https://github.com/grafana/opentelemetry-workshop)
- [Novatec Consulting opentelemetry-training](https://github.com/NovatecConsulting/opentelemetry-training)
- [Honeycomb opentelemetry-collector-workshop](https://github.com/honeycombio/opentelemetry-collector-workshop)
- [open-telemetry/opentelemetry-java-examples](https://github.com/open-telemetry/opentelemetry-java-examples)
- [Linux Foundation LFS148](https://training.linuxfoundation.org/training/getting-started-with-opentelemetry-lfs148/)
- [dasiths/OpenTelemetryDistributedTracingSample](https://github.com/dasiths/OpenTelemetryDistributedTracingSample) — RabbitMQ propagation example

### Tutorial / community references (MEDIUM confidence — verified against official docs)
- [Uptrace — Manual Instrumentation in Java](https://uptrace.dev/blog/opentelemetry-java-manual-instrumentation)
- [Uptrace — Trace Context Propagation](https://uptrace.dev/get/opentelemetry-java/propagation)
- [Uptrace — OpenTelemetry Sampling](https://uptrace.dev/get/opentelemetry-java/sampling)
- [Uptrace — Logback logging](https://uptrace.dev/guides/opentelemetry-logback)
- [OneUptime — Instrument RabbitMQ with OpenTelemetry](https://oneuptime.com/blog/post/2026-02-06-instrument-rabbitmq-opentelemetry-message-flow/view)
- [OneUptime — Test OTel Instrumentation with In-Memory Exporters](https://oneuptime.com/blog/post/2026-02-06-test-opentelemetry-instrumentation-in-memory-exporters/view)
- [Aspecto — Distributed Tracing for RabbitMQ](https://medium.com/@team_Aspecto/distributed-tracing-for-rabbitmq-with-opentelemetry-1ebe7457b4c1)
- [Eric Schabell — Hands-on OTel Manual Instrumentation](https://www.schabell.org/2024/09/hands-on-gide-to-opentelemetry-manual-instrumentation-for-developers.html)

---
*Feature research for: Manual-SDK OpenTelemetry workshop (Spring Boot + RabbitMQ + Grafana otel-lgtm)*
*Researched: 2026-04-29*
