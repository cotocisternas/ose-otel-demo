# Project Research Summary

**Project:** OSE OTel Demo v2.0 Production Shapes
**Domain:** OpenTelemetry manual-SDK workshop — v2.0 milestone adding a decomposed observability stack, Collector-side tail sampling, cross-signal correlation, expanded SDK instrumentation, and richer AMQP topologies on top of the v1.0 Workshop baseline.
**Researched:** 2026-05-02
**Confidence:** HIGH (all stack versions cross-checked against Docker Hub, GitHub Releases, Maven Central, and official source configs on the research date)

---

## Executive Summary

v2.0 Production Shapes evolves the workshop from a "minimal working demo" into a "production-shaped reference." The central move is replacing the single `grafana/otel-lgtm:0.26.0` container with five separate containers (`otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, `grafana/loki:3.7.1`, `grafana/grafana:13.0.1`) — the topology every real deployment uses. Everything else in v2.0 builds on top of that decomposition: tail sampling requires a standalone Collector, exemplars require Mimir to be directly addressable, log-based metrics require the Loki ruler to remote-write to Mimir. On the Java side, no new OTel SDK dependencies are needed; `Sampler`, `Baggage`, `ExemplarFilter`, `TextMapSetter`, and all `db.*` + `http.*` semconv constants are already present in the existing BOM-managed artifacts.

The recommended phase sequence is a two-arc approach: an Operational Arc (Phases 10-13) that stabilizes the infra stack before touching application code, followed by an SDK/Instrumentation Arc (Phases 14-17) that layers in JDBC spans, HTTP-client spans, sampling+baggage, and AMQP topology variants. Each arc is self-contained: an attendee could stop after Phase 13 and have a fully functional decomposed observability stack; Phases 14-17 independently add SDK teaching surfaces. The critical ordering constraint is that Phase 10 (Decompose Collector) must complete before Phases 11-13 begin, because tail sampling, exemplars, and log-based metrics all assume a standalone Collector and separate backend containers.

The primary milestone risk is the Phase 10 decomposition step itself. It touches five new config files, eight new compose services, Grafana dashboard UID references, and the existing v1.0 dashboard JSON simultaneously. The "blank dashboard" failure mode (pitfall F1-1, Grafana UID mismatch) is the most common two-hour debugging session in any lgtm decomposition. The acceptance criteria for Phase 10 must explicitly require: (a) the existing `ose-otel-demo` dashboard loads without "Datasource not found" errors, (b) Mimir rejects no writes (multitenancy disabled), (c) port 4317 has exactly one listener. Secondary risks are the `OtelSdkConfiguration` circular-reference carryover from v1.0 (pitfall X-1, must be fixed before any phase that modifies that class) and the DLX traceparent loop (pitfall F8-2, must be addressed before Phase 17 ships).

---

## Key Findings

### Recommended Stack

**Infrastructure additions (replace `grafana/otel-lgtm:0.26.0`):**

| Image | Tag | Feature |
|-------|-----|---------|
| `otel/opentelemetry-collector-contrib` | `0.151.0` | All signals; tail sampling (contrib-only); loki exporter (contrib-only) |
| `grafana/tempo` | `2.10.5` | Trace storage; metrics_generator with `send_exemplars: true` |
| `grafana/mimir` | `3.0.6` | Metric storage; Ruler for recording rules; exemplar query API |
| `grafana/loki` | `3.7.1` | Log storage; Ruler for LogQL recording rules to Mimir remote write |
| `grafana/grafana` | `13.0.1` | Dashboards; `exemplarTraceIdDestinations`; `tracesToLogsV2` |

All five images ship native multi-arch manifests (amd64 + arm64); no `--platform` workaround needed on Apple Silicon. Every image must be pinned to an exact patch version — no floating tags.

**Java/Maven additions — two new dependencies only:**

| Artifact | BOM | Feature |
|----------|-----|---------|
| `spring-boot-starter-data-jpa` | Spring Boot 3.4.13 (Hibernate 6.6.39.Final, HikariCP 5.1.0) | Feature 5 — JDBC/JPA spans |
| `org.postgresql:postgresql` | Spring Boot 3.4.13 (42.7.8) | Feature 5 — only if not already present from Phase 8 |

No additional OTel SDK dependencies are required. All v2.0 SDK capabilities (`Sampler.parentBased()`, `Sampler.traceIdRatioBased()`, `Baggage`, `ExemplarFilter.traceBased()`, `TextMapSetter`, `SpanKind.CLIENT`) are already present in the existing `opentelemetry-sdk:1.61.0` and `opentelemetry-api:1.61.0` artifacts. The `opentelemetry-sdk-extension-incubator` is NOT needed and must NOT be added.

**What NOT to add:**
- `otel/opentelemetry-collector:0.151.0` (core image) — missing `tailsamplingprocessor` and `lokiexporter`; must use contrib
- `grafana/otel-lgtm:*` alongside the decomposed stack — port 4317 conflict and data duplication
- Any floating `:latest` tags — workshop reproducibility is a hard requirement
- `io.opentelemetry:opentelemetry-sdk-extension-incubator` — no v2.0 feature requires it
- `spring-boot-starter-webflux` for HTTP client spans — `RestClient` in existing `spring-boot-starter-web` is sufficient

See `STACK.md` for complete config file templates (otelcol-config.yaml, tempo.yaml, mimir.yaml, loki.yaml, grafana-datasources.yaml, docker-compose.yml).

### Expected Features

Features group into three teaching categories that should drive phase design:

**Operational (infra team owns — no app code changes):**

| ID | Feature | Table-Stake Core |
|----|---------|-----------------|
| V2-TS-1 | Decomposed Collector | OTLP receiver to batch to traces:Tempo, metrics:Mimir, logs:Loki; all three pipelines operational |
| V2-TS-2 | Tail Sampling | `status_code ERROR` + `latency 1s` + `probabilistic 20%` in Collector traces pipeline |
| V2-TS-3 | Exemplars | `ExemplarFilter.traceBased()` on SDK; `send_exemplars: true` on prometheusremotewrite; Grafana `exemplarTraceIdDestinations`; one click-through demo |
| V2-TS-4 | Log-based Metrics | Loki ruler enabled; one recording rule (error rate); remote-write to Mimir; Grafana panel |

**Instrumentation (app team writes — SDK code changes):**

| ID | Feature | Table-Stake Core |
|----|---------|-----------------|
| V2-TS-5 | JDBC/JPA Spans | Full `db.*` semconv (use `DbAttributes.DB_QUERY_TEXT`, NOT `"db.statement"`); transaction parent span; sanitized query text |
| V2-TS-6 | Outbound HTTP Spans | `CLIENT` span via `TracingClientHttpRequestInterceptor` in `otel-bootstrap`; full HTTP semconv; `traceparent` injected; `service.peer.name` set |
| V2-TS-7a | Head Sampling | `parentBased(traceIdRatioBased(0.5))` demo; visible metric vs trace count divergence |
| V2-TS-7b | Baggage | `W3CBaggagePropagator` in composite propagator (already wired in v1.0); producer sets; consumer reads; `BaggageSpanAttributeProcessor` in `otel-bootstrap` for auto-stamp |

**Transport/Pattern (AMQP topology — propagation scaffolding unchanged):**

| ID | Feature | Table-Stake Core |
|----|---------|-----------------|
| V2-TS-8a | AMQP Fanout | Fanout exchange + 2 consumers + span LINKS (not parent-child) on consumer spans |
| V2-TS-8b | AMQP Topic | Topic exchange + `messaging.rabbitmq.destination.routing_key` attribute on producer span |
| V2-TS-8c | AMQP DLX | DLX routing + retry span continuing same trace (if headers preserved); explicit `traceparent` removal before re-publish to avoid loop |

**Differentiators (polish / teaching quality — add after table stakes pass):**

- V2-DF-2: Side-by-side trace count ON vs OFF for tail sampling; head-vs-tail contrast table in README
- V2-DF-3: Synthetic high-latency scenario to produce P99 exemplar dots (visible as diamonds in Grafana)
- V2-DF-5: N+1 query demonstration via trace waterfall (teaches granularity lesson for JDBC instrumentation)
- V2-DF-6: `url.template` vs `url.full` cardinality explosion demo for HTTP client spans
- V2-DF-7: Baggage surviving HTTP hop visible in `curl -v` `baggage:` header

**Anti-features (explicitly out of scope for v2.0):**
Grafana Alloy, multi-collector load-balancing, Prometheus scrape pull model for metrics, AOP auto-DB-span injection, `WebClient` reactive propagation, Spring AMQP `ObservationRegistry` integration (Micrometer), AMQP RPC, gRPC.

### Architecture Approach

v2.0 follows a strict "extend, not rewrite" principle relative to v1.0. The module structure (`otel-bootstrap` + `producer-service` + `consumer-service`) is unchanged. `OtelSdkConfiguration` remains intentionally duplicated per-service (TRACE-01/DOC-05). The OTLP endpoint stays `http://localhost:4317` throughout — the decomposed Collector exposes the same port as the old `otel-lgtm` container, so no SDK config changes.

Two new shared components are added to `otel-bootstrap`:
1. `http/TracingClientHttpRequestInterceptor.java` — `ClientHttpRequestInterceptor` wrapping each outbound HTTP call in a `CLIENT` span and injecting `traceparent` (Feature 6)
2. `amqp/BaggageSpanAttributeProcessor.java` — `SpanProcessor` that reads baggage entries at span start and stamps them as span attributes (Feature 7)

Per-service additions are isolated to their owning service: `OrderJpaService`/`OrderJpaRepository`/`Order` entity live in `consumer-service` only; `RabbitTopologyConfig` for fanout/topic/DLX declarations lives in `consumer-service`. `OtelSdkConfiguration` in both services gets two one-line changes for v2.0: `.setExemplarFilter(ExemplarFilter.traceBased())` in `buildMeterProvider()`, and swapping `alwaysOn()` to `traceIdRatioBased(0.5)` in `buildTracerProvider()` for the sampling lesson.

Infra layout after v2.0:
```
infra/
  observability/
    otelcol-config.yaml
    tempo-config.yaml
    mimir-config.yaml
    loki-config.yaml
    loki-rules/
      order-errors.yaml
grafana/
  datasources.yaml        (NEW — was auto-provisioned by otel-lgtm)
  dashboards/
    dashboards.yaml
    ose-otel-demo.json    (patch datasource UIDs to match new provisioning)
```

**New `otel-bootstrap` package layout after v2.0:**
```
otel-bootstrap/src/main/java/com/example/otel/
  amqp/
    TracingMessagePostProcessor.java    (existing)
    TracingMessageListenerAdvice.java   (existing)
    MessagePropertiesSetter.java        (existing)
    MessagePropertiesGetter.java        (existing)
    BaggageSpanAttributeProcessor.java  (NEW — Feature 7)
  http/
    TracingClientHttpRequestInterceptor.java  (NEW — Feature 6)
```

### Critical Pitfalls

Top pitfalls that would silently break the workshop or teach wrong patterns:

1. **OtelSdkConfiguration circular reference (X-1, HIGH)** — `@Autowired OpenTelemetry openTelemetry` field on the same `@Configuration` class that produces the `openTelemetry` @Bean causes `BeanCurrentlyInCreationException` at startup; invisible to the compiler. Fix: assign `this.openTelemetry = sdk` inside the factory method body; drop the `@Autowired` field. Must fix before any v2.0 phase modifies this class.

2. **Grafana datasource UID mismatch after decomposition (F1-1, HIGH)** — `ose-otel-demo.json` hardcodes UIDs from `otel-lgtm`'s internal provisioning. When decomposed containers provision new datasources with different UIDs, all dashboard panels show "Datasource not found." Inspect actual UIDs inside the running container and reuse them in the new provisioning YAML.

3. **Mimir multi-tenancy on by default (F1-3, HIGH)** — All remote_write calls return 401 unless `auth_enabled: false` is set in `mimir.yaml`. Metrics are silently dropped; no error surfaces in the SDK.

4. **`db.statement` vs `db.query.text` semconv rename (F5-2, MEDIUM)** — The stable semconv 1.40.0 constant is `DbAttributes.DB_QUERY_TEXT`. Using string literal `"db.statement"` from memory or older tutorials produces wrong attribute names in Tempo.

5. **Tail sampling OR semantics, not first-match (F2-1, HIGH)** — All policies evaluate; the decision is priority-based (`drop` > `inverted_not_sample` > `sample` > `inverted_sample`). The three-policy workshop config (keep-error, keep-slow, probabilistic fallback) uses OR semantics intentionally. Document the priority chain explicitly in Collector config comments.

---

## Cross-Feature Dependencies

```
Feature 1: Decomposed Collector (Phase 10)
 ├──required-by──> Feature 2: Tail Sampling (tailsamplingprocessor is Collector-contrib-only)
 ├──required-by──> Feature 3: Exemplars (prometheusremotewrite to Mimir; Grafana datasource provisioning)
 └──required-by──> Feature 4: Log-based Metrics (Loki ruler + remote-write to Mimir)

Feature 2: Tail Sampling
 └──soft-conflict──> Feature 3: Exemplars (high drop rate = exemplar trace_ids may not be in Tempo)
    Mitigation: keep probabilistic fallback at >=20% in demo OR document the conflict explicitly

Feature 3: Exemplars
 └──requires-precondition──> v1.0 Phase-4 http.server.request.duration histogram
    (histogram must record INSIDE an active span scope for ExemplarFilter to attach trace_id)

Feature 5: JDBC/JPA Spans
 └──extends──> v1.0 Phase-8 db-cache (Postgres + HikariCP already in compose; JdbcTemplate pattern shown)

Feature 6: Outbound HTTP Spans
 └──extends──> v1.0 Phase-2 HTTP SERVER span (completes the HTTP story)
 └──pairs-with──> Feature 7b Baggage (baggage propagates through outbound HTTP baggage: header)

Feature 7a: Head Sampling
 └──contrasts-with──> Feature 2: Tail Sampling — teach separately to avoid double-filter confusion (F2-3)

Feature 7b: Baggage
 └──requires──> v1.0 AMQP propagation (baggage flows through existing inject/extract path)
 └──pairs-with──> Feature 6 (baggage visible in outbound HTTP header)

Feature 8a/8b/8c: AMQP Variants
 └──requires──> v1.0 otel-bootstrap TracingMessageListenerAdvice (extend, not replace)
 └──8c (DLX) requires──> 8a or 8b (multi-queue topology for DLX routing to be visible)
```

**Mandatory sequencing constraints:**
- Phase 10 must complete before Phases 11, 12, 13 begin (hard dependency)
- Feature 8c (DLX) after Feature 8a or 8b (pedagogical dependency)
- Feature 7 (Sampling + Baggage) should be taught BEFORE or AFTER Feature 2 (Tail Sampling), never simultaneously (F2-3)
- Fix X-1 (circular reference) before any phase that modifies `OtelSdkConfiguration`

---

## Implications for Roadmap

### Suggested Phase Structure

#### Operational Arc (Phases 10-13) — "What the infra team owns"

**Phase 10: Decompose otel-lgtm into Separate Collector + Backends**

**Rationale:** Foundation of the entire v2.0 milestone. Unblocks Phases 11-13. Pure infrastructure — no Java source files change. The pedagogical value is the Collector config itself: attendees read one YAML that shows all pipeline stages (memory_limiter > resource > attributes > filter > tail_sampling > batch).

**Delivers:** Five new docker-compose services (otel-collector, tempo, mimir, loki, grafana); `infra/observability/` directory with four config files; `grafana/datasources.yaml` explicit provisioning; updated `docker-compose.yml` with `lgtm:` service removed entirely.

**Addresses:** V2-TS-1

**Must address before shipping:**
- Inspect and reuse `otel-lgtm` datasource UIDs (F1-1) — acceptance criterion is: existing dashboard loads without errors
- Set `auth_enabled: false` in `mimir.yaml` (F1-3)
- Mount Tempo WAL as named volume (F1-2)
- Remove `lgtm:` service entirely before adding decomposed services — single atomic commit (F1-5)
- Verify Collector component names via `docker run --rm otel/opentelemetry-collector-contrib:0.151.0 components` (F1-4)

**Done when:** `docker compose up` succeeds; `POST /orders` produces a trace in Tempo, a metric in Mimir, and a log in Loki; existing `ose-otel-demo` dashboard loads all panels without "Datasource not found"; all five images pinned to exact patch versions.

---

**Phase 11: Tail Sampling at the Collector**

**Rationale:** Builds directly on Phase 10's Collector config. The `tail_sampling` processor is already scaffolded in the teaching pipeline from Phase 10; this phase makes it the lesson focus. No Java changes.

**Delivers:** `tail_sampling` processor active in the traces pipeline with three policies (keep-error, keep-slow-1s, probabilistic-20%); `decision_wait: 10s`; `num_traces: 10000`; load generator demo showing trace count drop.

**Addresses:** V2-TS-2

**Must address before shipping:**
- Document OR semantics (not first-match) of policy evaluation in Collector config comments (F2-1)
- Keep SDK at `Sampler.parentBased(Sampler.alwaysOn())` — do NOT activate ratio sampling in this phase (F2-3)
- Set `decision_wait: 10s` rather than the default 30s for demo responsiveness (F2-2)

---

**Phase 12: Exemplars Wiring**

**Rationale:** Mimir and Grafana are now addressable (Phase 10 done). The SDK change is one line in each `OtelSdkConfiguration`. Grafana datasource provisioning is already in place from Phase 10. The teaching payoff — clicking a metric chart to jump to the trace — is the most visually impressive moment in the operational arc.

**Delivers:** `ExemplarFilter.traceBased()` in both `OtelSdkConfiguration.buildMeterProvider()` calls; `send_exemplars: true` confirmed on the `prometheusremotewrite/mimir` exporter; Grafana `exemplarTraceIdDestinations` provisioned; click-through demo on `http.server.request.duration` histogram.

**Addresses:** V2-TS-3

**Must address before shipping:**
- Fix X-1 (circular reference) since this phase touches `OtelSdkConfiguration`
- Ensure histogram observation occurs INSIDE an active span scope in `HttpServerSpanFilter` (F3-1)
- Verify `exemplarTraceIdDestinations.datasourceUid` matches the actual Tempo UID (F3-3)
- Keep exemplar labels to `trace_id` + `span_id` only — no business attributes (F3-2)

---

**Phase 13: Log-Based Metrics (Loki Recording Rules)**

**Rationale:** Loki and Mimir are running (Phase 10 done). No Java or Collector changes needed — pure Loki ruler configuration and rule YAML. Good phase to end the operational arc because it reinforces "you can derive metrics from logs without touching app code."

**Delivers:** `ruler` section enabled in `loki-config.yaml`; `infra/observability/loki-rules/order-errors.yaml` with error rate recording rule remote-writing to Mimir; Grafana panel showing log-derived metric alongside SDK-emitted counter.

**Addresses:** V2-TS-4

**Must address before shipping:**
- Use `log:<metric>:<aggregation>` naming convention to avoid SDK metric name collision (F4-1)
- Set LogQL range window to `[2m]` (2x the 1m eval interval) to avoid zero-valued metrics (F4-2)
- Align volume mount path with `ruler_storage.local.directory` in `loki-config.yaml` (F4-3)
- Only group by `service_name` / `level` — never by `order_id`, `span_id`, or high-cardinality labels (F4-4)

---

#### SDK/Instrumentation Arc (Phases 14-17) — "What the app team writes"

Phases 14-17 are independent of each other and independent of whether Phases 10-13 are complete (they work equally well against `otel-lgtm` or the decomposed stack). However, they are logically easier to teach after the infra stack is stable.

**Phase 14: JDBC/JPA Database Spans**

**Rationale:** Extends v1.0 Phase-8's `SPAN_KIND_CLIENT` pattern with full semconv, transaction-level span wrapping, and Spring Data JPA repository spans. Consumer-service only. Deepens the database instrumentation lesson already started in Phase 8.

**Delivers:** `consumer-service/db/OrderJpaService.java` + `OrderJpaRepository.java` + `Order.java` entity; `spring-boot-starter-data-jpa` added to consumer pom; full `db.*` semconv attribute set on all DB spans; transaction parent span + JPA method child spans.

**Addresses:** V2-TS-5

**Must address before shipping:**
- Fix X-1 (circular reference) if not already fixed, since this phase adds a new @Bean to consumer-service's Spring context
- Use `DbAttributes.DB_QUERY_TEXT` (not `"db.statement"`) (F5-2)
- Wrap at repository method level, NOT SQL execution level (prevents N+1 span explosion) (F5-1)
- Place the span OUTER to the `@Transactional` boundary so rollbacks surface as errors on the span (F5-3)
- Update `TestOtelConfiguration` if `OtelSdkConfiguration` changes (X-4)

---

**Phase 15: Outbound HTTP-Client Spans**

**Rationale:** Completes the HTTP story started in v1.0 (SERVER span). The `TracingClientHttpRequestInterceptor` belongs in `otel-bootstrap` (same rationale as the AMQP propagation pair: symmetric, exchange-agnostic). `RestClient` is already available in `spring-boot-starter-web` — no new Maven dependency.

**Delivers:** `otel-bootstrap/http/TracingClientHttpRequestInterceptor.java`; `NotificationStubController.java` in producer-service as in-process mock target; outbound HTTP call from `OrderService.place()`; CLIENT span visible in Tempo as child of INTERNAL span.

**Addresses:** V2-TS-6

**Must address before shipping:**
- Always inject `RestClient.Builder` bean — never `RestClient.create(url)` (F6-1)
- Start span BEFORE the HTTP call and make it current BEFORE interceptor injection runs (F6-2)
- Register OTel interceptor LAST in interceptor chain (F6-3)
- Capture `http.response.status_code` in both success and exception paths (F6-4)

---

**Phase 16: Sampling + Baggage**

**Rationale:** SDK-level companion to Phase 11's Collector-side tail sampling. Head sampling and baggage are bundled because both live in `OtelSdkConfiguration` and both are "first request decisions." The `BaggageSpanAttributeProcessor` belongs in `otel-bootstrap`.

**Delivers:** `otel-bootstrap/amqp/BaggageSpanAttributeProcessor.java`; `OtelSdkConfiguration.buildTracerProvider()` updated in both services: `alwaysOn()` to `traceIdRatioBased(0.5)` + `BaggageSpanAttributeProcessor` registered; `OrderController.create()` sets `order.customer-tier` baggage; `OrderListener.onOrder()` reads baggage.

**Addresses:** V2-TS-7a, V2-TS-7b

**Must address before shipping:**
- Fix X-1 (circular reference) — this phase modifies `OtelSdkConfiguration` in both services
- Change sampler via direct code change, NOT via `OTEL_TRACES_SAMPLER` env var (F7-1)
- `extractedContext.makeCurrent()` must wrap the ENTIRE listener execution, not just the span builder (F7-2)
- Demonstrate specific baggage key reading; never iterate all baggage entries as span attributes (F7-3)
- ALL `span.makeCurrent()` calls in try-with-resources (F7-4)

---

**Phase 17: AMQP Fanout / Topic / DLX Variants**

**Rationale:** Last phase because it expands the RabbitMQ topology and requires verifying that existing trace propagation works across all exchange types. Placed last so the entire instrumented v2.0 stack is in place. The `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` require no changes — the lesson is that one propagation pair handles all exchange shapes.

**Delivers:** `consumer-service/config/RabbitTopologyConfig.java`; `AuditListener.java`; `producer-service` `OrderPublisher` methods for topic and fanout; DLX queue declarations; fanout consumers using span LINKS (not parent-child).

**Addresses:** V2-TS-8a, V2-TS-8b, V2-TS-8c

**Must address before shipping:**
- Fanout consumers: use `span.addLink(producerSpanContext)`, not `setParent(extractedContext)` (F8-1)
- DLX republish: remove `traceparent`/`tracestate` headers before re-publishing; use `.setNoParent()` + add link to original span (F8-2)
- Topic routing keys: use low-cardinality KEY PATTERN as span attribute, not per-message resolved value (F8-3)
- Verify DLX re-queued messages preserve original AMQP headers including `traceparent` (integration test required) (ARCHITECTURE.md note)

---

### Phase Ordering Rationale

- Phase 10 first — all three operational features (11, 12, 13) technically require it; it is also the most complex and highest-risk phase, so validating it early contains blast radius.
- Phase 11 before Phase 12: explain WHAT tail sampling drops before teaching WHY exemplars need a trace to click through to. Also avoids introducing the exemplar/sampling conflict before both concepts exist.
- Phase 13 (log-based metrics) is a pure configuration lesson requiring no Java changes — good "low code" phase to end the operational arc.
- Phases 14-17 ordered loosely: JDBC (14) builds directly on v1.0 Phase 8 patterns; HTTP client (15) completes the HTTP story; Sampling+Baggage (16) pairs thematically with Phase 11; AMQP variants (17) last to avoid distracting from propagation fundamentals earlier.
- Phases 14-17 can run in parallel in the planning/requirements phase since they have no dependencies on each other.

### Research Flags

**Phases likely needing `/gsd-research-phase` during planning:**
- **Phase 10:** Verify exact datasource UIDs inside `grafana/otel-lgtm:0.26.0` via live container inspection (cannot be determined from docs alone). Also verify Loki native OTLP endpoint path (`/otlp` vs `/otlp/v1/logs`) for the Collector `otlphttp/loki` exporter.
- **Phase 13:** Verify Loki ruler remote-write config syntax for Loki 3.7.1 (ARCHITECTURE.md and PITFALLS.md cite slightly different YAML shapes; needs live-container validation).

**Phases with standard patterns (skip research, just plan):**
- **Phase 11:** `tailsamplingprocessor` config is fully documented; STACK.md provides the exact YAML.
- **Phase 12:** One-line SDK change + Grafana provisioning key confirmed in official `grafana/docker-otel-lgtm` source.
- **Phase 14:** Full `db.*` semconv attribute set and span shape documented in FEATURES.md and ARCHITECTURE.md.
- **Phase 15:** `TracingClientHttpRequestInterceptor` mirrors the existing `TracingMessagePostProcessor`; ARCHITECTURE.md provides the complete implementation.
- **Phase 16:** `BaggageSpanAttributeProcessor` pattern documented in ARCHITECTURE.md; sampler swap is a one-liner.
- **Phase 17:** Span link semantics for fanout documented in OTel messaging semconv; ARCHITECTURE.md provides the full DLX flow with tracing.

---

## Watch Out For

The 10 highest-impact items distilled from PITFALLS.md — if any of these are forgotten, the milestone is in trouble:

| # | Item | Phase | Consequence if missed |
|---|------|-------|----------------------|
| 1 | **`OtelSdkConfiguration` circular reference (X-1)** | First phase touching that class | App crashes at startup with `BeanCurrentlyInCreationException`; invisible to compiler |
| 2 | **Grafana datasource UIDs from otel-lgtm must be preserved (F1-1)** | Phase 10 | All existing dashboard panels show "Datasource not found"; 2-hour debug session |
| 3 | **Mimir `auth_enabled: false` (F1-3)** | Phase 10 | All metric remote-writes return 401; metrics silently absent from Mimir |
| 4 | **Tail sampling OR semantics, not first-match (F2-1)** | Phase 11 | Workshop teaches wrong mental model; attendees go home with incorrect understanding |
| 5 | **Head + tail double-filter = head% x tail% effective rate (F2-3)** | Phases 11+16 | Demo shows near-zero traces; attendees think OTel is broken |
| 6 | **`db.query.text` not `db.statement` (F5-2)** | Phase 14 | Wrong attribute name; Tempo search `db.query.text=*` returns nothing |
| 7 | **DLX republish must strip `traceparent` (F8-2)** | Phase 17 | Infinite loop: same trace gains spans on every retry; Tempo storage grows unboundedly |
| 8 | **`extractedContext.makeCurrent()` wraps ENTIRE listener scope for baggage (F7-2)** | Phase 16 | `Baggage.current()` empty in consumer; attendees think baggage does not work |
| 9 | **Loki recording rule names must not collide with SDK metric names (F4-1)** | Phase 13 | Two Mimir series with same name; Grafana PromQL returns doubled values |
| 10 | **`RestClient.Builder` injection — never `RestClient.create()` (F6-1)** | Phase 15 | OTel interceptor silently absent; no CLIENT span; no `traceparent` in outgoing headers |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All 5 container images cross-checked against Docker Hub + GitHub Releases API on 2026-05-02. All Java BOM artifact versions verified from Spring Boot 3.4.13 `build.gradle` source. SDK class availability (ExemplarFilter, Sampler, Baggage) traced to existing BOM artifacts. |
| Features | HIGH | Table stakes derived from OTel specs (messaging semconv, HTTP semconv, DB semconv 1.40.0), Grafana docs, and Collector contrib README. MEDIUM on exact Loki ruler remote-write YAML syntax for 3.7.1 — needs live container validation. |
| Architecture | HIGH | Integration map grounded in actual v1.0 codebase structure (ARCHITECTURE.md anchors against existing class names). Config file templates derived from official sources (docker-otel-lgtm repo, Tempo single-binary example, Mimir HTTP API docs). MEDIUM on Loki ruler config (two slightly different YAML shapes cited in different research files). |
| Pitfalls | HIGH | All HIGH-severity pitfalls verified against official docs or OTel issue tracker. MEDIUM on DLX `traceparent` header preservation (needs integration test to verify definitively). |

**Overall confidence:** HIGH

### Gaps to Address

1. **Grafana datasource UIDs inside `otel-lgtm:0.26.0`:** Must be inspected via live container before Phase 10 requirements are written. Command: `docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml`. This is the single most important open question for Phase 10.

2. **Loki ruler remote-write YAML syntax for 3.7.1:** ARCHITECTURE.md and PITFALLS.md cite slightly different YAML shapes (`remote_write.enabled` + `clients:` map vs inline `url:` key). Verify against Loki 3.7.1 documentation during Phase 13 planning.

3. **Loki native OTLP endpoint path:** The Collector `otlphttp/loki` exporter endpoint should be `http://loki:3100/otlp` (native OTLP, Loki 3.x). Verify this path is enabled by default in `grafana/loki:3.7.1` without requiring explicit config key.

4. **DLX traceparent header preservation:** ARCHITECTURE.md notes "must be verified with an integration test" — RabbitMQ's behavior when dead-lettering is documented as headers preserved, but a passing integration test is the only reliable proof.

5. **`TestOtelConfiguration` update tracking (X-4):** Each phase that modifies `OtelSdkConfiguration` must include an explicit step to sync `TestOtelConfiguration`. This is a recurring process gap, not a one-time fix — the requirements for each relevant phase must include it.

---

## Sources

### Primary (HIGH confidence)

- `STACK.md` — container image tags + Maven artifact versions; all cross-checked against Docker Hub, GitHub Releases API, Maven Central, official Grafana/OTel docs on 2026-05-02
- `FEATURES.md` — table-stakes / differentiator inventory per feature; grounded in OTel messaging semconv, HTTP semconv, DB semconv, Grafana exemplar docs, Loki recording rules docs
- `ARCHITECTURE.md` — component integration map; config file templates derived from official sources (grafana/docker-otel-lgtm, Tempo single-binary example, Mimir HTTP API docs, OTel Java SDK docs)
- `PITFALLS.md` — severity-tagged pitfall inventory; HIGH-severity items verified against official docs, OTel issue tracker, and community-confirmed production patterns
- `.planning/milestones/v1.0-research/SUMMARY.md` — v1.0 architecture anchors and locked decisions that v2.0 extends
- [grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) — bundled config file shapes (otelcol-config.yaml, tempo-config.yaml, loki-config.yaml, grafana-datasources.yaml) read directly from main branch

### Secondary (MEDIUM confidence)

- [grafana/intro-to-mltp](https://github.com/grafana/intro-to-mltp) — decomposed LGTM stack compose reference
- [blueswen/spring-boot-observability](https://github.com/blueswen/spring-boot-observability) — Spring Boot exemplar wiring patterns (agent-based, but exemplar config is portable)
- [honeycombio/opentelemetry-collector-workshop](https://github.com/honeycombio/opentelemetry-collector-workshop) — tail sampling patterns

### Tertiary (LOW confidence — needs validation at implementation time)

- Loki 3.7.1 ruler remote-write YAML key names — verify against live container or current Loki changelog
- Exact Grafana datasource UIDs inside `grafana/otel-lgtm:0.26.0` — must inspect the container at Phase 10 implementation time

---
*Research completed: 2026-05-02*
*Ready for requirements definition: yes*
