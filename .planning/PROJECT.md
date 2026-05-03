# OSE OTel Demo

## What This Is

A workshop-grade demo project that teaches engineers how to instrument a Spring Boot 3.4.13 / Java 17 application using the OpenTelemetry Java SDK directly (manual instrumentation, not the auto-instrumentation Java agent). The demo covers two of the most common service-to-service shapes — synchronous HTTP APIs and asynchronous RabbitMQ producer/consumer flows — and emits all three OTel signals (traces, metrics, logs) to Grafana's `otel-lgtm` all-in-one backend. It's built to be cloned and walked through commit-by-commit by an internal engineering team during a hands-on workshop.

## Core Value

A workshop attendee can clone the repo, run `docker compose up` + `mise run dev`, hit `POST /orders`, and see a single distributed trace flow from the HTTP handler through the RabbitMQ publish, into the consumer's processing logic, with correlated metrics and logs — and understand exactly which lines of SDK code made each piece work.

## Current Milestone: v2.0 Production Shapes

**Goal:** Evolve the v1.0 manual-SDK workshop into a production-shaped reference — decompose the all-in-one observability stack into a real Collector pipeline, teach in-SDK sampling + baggage and Collector-side tail sampling, expand instrumentation to JDBC/JPA + outbound HTTP, enrich the AMQP topology with fanout/topic/DLX variants, and wire cross-signal correlation (exemplars + log-based metrics) so attendees leave with patterns they can apply at work.

**Target features:**

*Operational chapters (production stack)*
- Decompose `otel-lgtm` into separate OTel Collector + Tempo + Mimir + Loki + Grafana containers
- Tail sampling at the Collector (keep-on-error, latency-based, probabilistic fallback)
- Exemplars wiring: Prometheus histogram exemplars click-through into Tempo traces
- Log-based metrics: Loki LogQL recording rules + log-derived metrics in Mimir

*Instrumentation lessons (SDK teaching surface)*
- JDBC/JPA database spans (manual `SPAN_KIND_CLIENT` beyond v1.0 Phase-8 db-cache, with `db.*` semconv attributes)
- Outbound HTTP-client spans (RestClient/WebClient → external HTTP hop, peer.service propagation)
- Sampling + baggage (parent-based head sampling, ratio sampling, baggage propagation across services)

*Transport/pattern shapes*
- AMQP fanout/topic/DLX variants (richer topologies on top of v1.0's `TracingMessagePostProcessor`/`Advice` scaffolding)

**Deferred to v2.x backlog:**
- gRPC service-to-service (substantial surface; candidate to anchor its own milestone)
- `@Scheduled` / `@Async` span lifecycle (orthogonal to production-shapes theme)

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ Spring Boot 3.4.13 producer service exposing `POST /orders` that publishes an `OrderCreated` message to RabbitMQ via Spring AMQP — v1.0
- ✓ Spring Boot 3.4.13 consumer service that processes `OrderCreated` messages with `@RabbitListener` and simulates downstream domain work — v1.0
- ✓ Manual OpenTelemetry SDK setup (no Java agent) — `OpenTelemetrySdk` configured at startup with OTLP gRPC exporters for traces, metrics, and logs — v1.0
- ✓ Trace instrumentation: explicit `Tracer` usage on HTTP entry points, around publish/receive, and inside business logic — v1.0
- ✓ W3C trace context propagation across the AMQP boundary — `TracingMessagePostProcessor` injects context into message headers on publish; `TracingMessageListenerAdvice` extracts on consume — producer and consumer spans share one trace — v1.0
- ✓ Metric instrumentation: Counter (`orders.created`), Histogram (HTTP server-request duration in seconds), ObservableGauge (`orders.queue.depth.estimate`) — v1.0
- ✓ Log instrumentation: OTel `OpenTelemetryAppender` bridge exports application logs as OTLP; MDC injector stamps every log with the active `trace_id`/`span_id` — v1.0
- ✓ Single-container Grafana `otel-lgtm` (`grafana/otel-lgtm:0.26.0`) as the observability backend, with Tempo, Mimir, and Loki datasources pre-wired — v1.0
- ✓ `docker-compose.yml` orchestrating only infrastructure (RabbitMQ + `otel-lgtm` + Valkey + Postgres + exporters); apps run on the host via `mise run` — v1.0
- ✓ `mise.toml` pinning Amazon Corretto 17.0.13.11.1 and Maven 3.9.11, plus the workshop task graph (`dev`, `test`, `infra:up`, `infra:down`, `verify:bom`, etc.) — v1.0
- ✓ Testcontainers integration tests with `RabbitMQContainer` + `InMemorySpanExporter` that exercise the producer-to-consumer path and assert exported spans — v1.0
- ✓ Staged git checkpoints — annotated tags on `main` (`step-01-baseline` through `step-06-tests` + bonus `step-08-db-cache`); D-09 honors no `step-07-*` tag — v1.0
- ✓ Step-by-step `README.md` mapping each tag to a specific OTel concept, with paired screenshots (broken/fixed DOC-04 anchor) and copy-pasteable curl + mise commands — v1.0

### Active

<!-- Current scope. Building toward these. -->

(v2.0 Production Shapes — 8 target features under definition. See `REQUIREMENTS.md` once generated for the REQ-ID-mapped list and `ROADMAP.md` for phase-level traceability. Phase numbering continues from Phase 10.)

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- OpenTelemetry Java Agent (`-javaagent:opentelemetry-javaagent.jar`) — the entire point of the workshop is to teach the *manual SDK*; auto-instrumentation hides the mechanics we want to expose
- Spring Boot OTel starter / Micrometer-tracing-bridge-otel — same reason; we want explicit `Tracer`/`Meter`/`Logger` calls, not framework-mediated ones
- Kubernetes manifests, Helm charts, Tilt/Skaffold — workshop runs on a laptop with `docker compose` + `mise`, not a cluster
- Production-grade RabbitMQ topology — no topic exchanges, no DLX, no RPC pattern, no fanout/work-queue variants; one direct exchange + one queue keeps the trace propagation lesson the headline
- Multi-AMQP-pattern coverage (fanout, topic, RPC) — adds surface area without adding instrumentation lessons; deferred to a v2 if requested
- Lower-level RabbitMQ Java client (`com.rabbitmq:amqp-client`) examples — Spring AMQP is the idiomatic Spring path attendees will recognize
- GraalVM native-image build — adds JVM/build complexity orthogonal to OTel
- Cloud observability backends (Honeycomb, Datadog, New Relic, Lightstep, AWS X-Ray, GCP Trace) — `otel-lgtm` is the demo backend; pointing at a SaaS is a one-line OTLP endpoint change attendees can do themselves
- A web UI / frontend — the demo is server-side; HTTP traffic is generated via curl or the workshop's load script
- Authentication / authorization on the HTTP API — irrelevant to the instrumentation lesson and would distract from it
- Database persistence (JPA, R2DBC, JDBC) — the order/consumer logic is simulated in memory; adding a DB is a separable instrumentation lesson, not this one
- Distributed deployment across multiple hosts — both services run on the workshop attendee's laptop
- Performance benchmarking / load testing harnesses — out of scope for the teaching artifact (could be a follow-up)

## Context

**Domain:** OpenTelemetry instrumentation in the JVM ecosystem. The OTel Java SDK and the Java agent are two distinct ways to instrument an app, and they look very different in code. Spring Boot 3.x ships with a Micrometer-based observability story by default, but this demo deliberately steps around that to expose the raw SDK so attendees understand what's underneath the abstractions.

**Audience:** Internal engineering team. Background: comfortable with Spring Boot and RabbitMQ; varying levels of OTel familiarity. Workshop is hands-on — attendees will clone, run, and read code.

**Why this demo exists:** Most OTel learning resources jump straight to auto-instrumentation, which gives a great out-of-the-box experience but leaves engineers fuzzy on what the SDK actually does. When something needs custom instrumentation (a domain-specific span, a business metric, propagating context across a non-HTTP boundary like RabbitMQ), engineers don't know which knobs to turn. This demo closes that gap by making attendees write the SDK calls themselves.

**Pedagogical hook — the AMQP propagation gap:** OTel's auto-instrumentation does *not* cover Spring AMQP out of the box (unlike Spring MVC and JDBC). This makes RabbitMQ the perfect teaching example: even teams that adopt the Java agent still have to handle this manually, so the demo's lesson transfers directly to production work.

**Tooling philosophy:** mise (asdf-compatible) handles Java + Maven versions for reproducibility. Attendees with mise installed get the right toolchain automatically. Apps run on the host (not in containers) so attendees can attach a debugger and walk through SDK calls.

## Constraints

- **Tech stack — Spring Boot version**: 3.4.13 — pinned by user request; aligns with Java 17 baseline and the recent (2025+) Spring Boot 3.x line
- **Tech stack — Java version**: 17 — pinned by user request; the LTS most teams target
- **Tech stack — JDK distribution**: Amazon Corretto — chosen as the workshop default
- **Tech stack — Build tool**: Maven — chosen for ubiquity in enterprise Spring Boot codebases
- **Tech stack — AMQP API**: Spring AMQP (`spring-boot-starter-amqp`, `RabbitTemplate`, `@RabbitListener`) — idiomatic Spring path; lower-level `com.rabbitmq:amqp-client` excluded
- **Tech stack — OTel API**: OpenTelemetry Java SDK directly (manual instrumentation only); no Java agent, no Micrometer bridge
- **Tech stack — Observability backend**: ~~Grafana `otel-lgtm` single-container distribution~~ → **decomposed into five separate pinned containers in Phase 10** (otel-collector v0.151.0, tempo v2.10.5, mimir v3.0.6, loki v3.7.1, grafana v13.0.1) wired via docker-compose service-name DNS. The OTLP receiver still terminates at `localhost:4317` (STACK-03 invariant — SDK code unchanged across the migration)
- **Tech stack — Test framework**: JUnit 5 + Testcontainers (RabbitMQ module); integration tests assert exported telemetry
- **Tooling**: `mise` for JDK + Maven version management; `mise.toml` is the source of truth
- **Packaging**: `docker-compose` for infrastructure (RabbitMQ + Postgres + Valkey + 5 observability services post-Phase 10) only; apps run on host
- **Audience constraint**: Workshop attendees with laptop-class hardware; no cloud dependencies; everything runs offline
- **Output medium**: Public-readable git repository with a step-by-step README and one branch (or tag) per workshop checkpoint

## Key Decisions

<!-- Decisions that constrain future work. Add throughout project lifecycle. -->

| Decision                                                                                     | Rationale                                                                                                                                         | Outcome                                                                                                                                                                                                                |
|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Use OpenTelemetry SDK manually, not the Java agent                                           | Workshop's pedagogical goal is teaching the SDK explicitly; auto-instrumentation hides the mechanics                                              | ✓ Validated in Phase 2 (manual `OpenTelemetrySdk.builder()` shipped per-service)                                                                                                                                       |
| Emit all three OTel signals (traces + metrics + logs)                                        | Workshop attendees need to see how each signal is configured/exported through the same SDK                                                        | ◆ In progress — traces (Phase 2) + metrics (Phase 4) shipped; logs pending (Phase 5)                                                                                                                                   |
| Use Grafana `otel-lgtm` single-container backend (v1.0 only)                                 | One container instead of five separate services (collector, Tempo, Mimir, Loki, Grafana); minimized ops noise during v1.0 single-signal lessons   | ✓ Validated in Phase 2 + Phase 4 (v1.0); ⊘ **superseded in Phase 10** — production-shapes milestone requires per-backend config visibility (tail-sampling, exemplars, Loki rules) impossible with the all-in-one image |
| Decompose lgtm into five pinned containers (Phase 10)                                        | Production observability is never a single container; tail-sampling/exemplars/recording-rules require per-backend config files attendees can read | ✓ Validated in Phase 10 (PREREQ-01 closed, STACK-01..05 satisfied; SDK code unchanged)                                                                                                                                 |
| Wrap TSAMP-01 three policies in a `composite` envelope (Phase 11)                            | Demonstrates per-branch rate-limits in addition to OR-semantics; enables the `probabilistic-fallback` story without the flat-list alternative that collapses sub-policy attribution | ✓ Validated in Phase 11 (TSAMP-01..03 closed; `recordpolicy` alpha gate wires sub-policy names to Tempo span attributes; `verify:tail-sampling` Tier 2 asserts gate is wired at runtime)                               |
| Minimal RabbitMQ topology (one direct exchange + one queue)                                  | Trace propagation lesson is the headline; richer topologies add surface area without adding instructional value                                   | — Pending                                                                                                                                                                                                              |
| Spring AMQP (`RabbitTemplate` / `@RabbitListener`) over the lower-level RabbitMQ Java client | Spring AMQP is what attendees recognize; manual span wrapping + header injection on top of it is the realistic teaching scenario                  | — Pending                                                                                                                                                                                                              |
| Order processing as the toy domain                                                           | Familiar shape (POST /orders → publish → consumer); makes business-logic spans concrete without distracting                                       | — Pending                                                                                                                                                                                                              |
| Staged git checkpoints (one branch/tag per workshop step)                                    | Workshop format demands "git checkout to follow along"; checkpoints keep each lesson focused                                                      | ◆ In progress — `step-01-baseline`, `step-02-traces`, `step-03-context-propagation`, `step-04-metrics` applied (4 of 6)                                                                                                |
| Testcontainers integration tests rather than mocks                                           | Demonstrates testing instrumented code against real infrastructure — itself a workshop lesson                                                     | — Pending                                                                                                                                                                                                              |
| `docker-compose` for infra, app runs on host via mise                                        | Lets attendees attach a debugger and step through SDK calls; containers for everything would block IDE workflows                                  | — Pending                                                                                                                                                                                                              |
| mise (not asdf or SDKMAN) for JDK/Maven version pinning                                      | mise was the user's chosen tooling; supports Corretto 17 and Maven natively                                                                       | — Pending                                                                                                                                                                                                              |
| Amazon Corretto 17 over Temurin/Liberica/GraalVM                                             | Chosen by user; well-supported, stable, vendor-backed                                                                                             | — Pending                                                                                                                                                                                                              |
| Maven over Gradle                                                                            | Chosen by user; ubiquity in enterprise Spring Boot codebases                                                                                      | — Pending                                                                                                                                                                                                              |

## Evolution

This document evolves at phase transitions and milestone boundaries.

*Last updated: 2026-05-03 — Phase 18 complete (automated-screenshot-generation-playwright). All v2.0 milestone phases executed. PREREQ-02 PNG capture delivered via Playwright script.*

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-03 — Phase 18 complete; PREREQ-02 delivered (step-04-metrics.png via Playwright); all docs/screenshots/ teaching PNGs now automated*
