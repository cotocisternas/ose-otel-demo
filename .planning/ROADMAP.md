# Roadmap: OSE OTel Demo

## Milestones

- ✅ **v1.0 Workshop** — Phases 1–7 (shipped 2026-05-02; tag `v1.0`) — see `milestones/v1.0-ROADMAP.md` for the full archived roadmap
- 🚧 **v2.0 Production Shapes** — Phases 10–17 (in progress; started 2026-05-02)

## Phases

<details>
<summary>✅ v1.0 Workshop (Phases 1–7) — SHIPPED 2026-05-02</summary>

- [x] Phase 1: Baseline & Scaffold (6/6 plans) — completed 2026-04-29 — tag `step-01-baseline`
- [x] Phase 2: Manual SDK Bootstrap & First Traces (6/6 plans) — completed 2026-05-01 — tag `step-02-traces`
- [x] Phase 3: AMQP Context Propagation (5/5 plans) — completed 2026-05-01 — tag `step-03-context-propagation`
- [x] Phase 4: Metrics (5/5 plans) — completed 2026-05-01 — tag `step-04-metrics`
- [x] Phase 5: Logs Correlation (6/6 plans) — completed 2026-05-01 — tag `step-05-logs`
- [x] Phase 6: Verification Tests (6/6 plans) — completed 2026-05-02 — tag `step-06-tests`
- [x] Phase 7: Polish & Differentiators (7/7 plans) — completed 2026-05-02 — *no tag (D-09)*

Plus the post-Phase-7 quick-task workshop polish on `main`:
- `step-08-db-cache` — Phase 8 Valkey + PostgreSQL manual instrumentation
- Workshop NOC dashboard, load-script burst/idempotency streams, scrape-interval / rate-window tuning, slow-loading-dashboard fixes

</details>

### 🚧 v2.0 Production Shapes (Phases 10–17)

- [x] **Phase 10: Prerequisites & Stack Decomposition** - Fix the OtelSdkConfiguration circular-ref carryover, replace the all-in-one otel-lgtm container with five separate production-shaped services, and restore all existing dashboards (completed 2026-05-03)
- [x] **Phase 11: Tail Sampling at the Collector** - Configure the standalone Collector's tail_sampling processor to demonstrate intelligent, trace-complete sampling decisions impossible at the SDK level (completed 2026-05-03)
- [x] **Phase 12: Exemplars: Metrics to Trace Click-Through** - Wire ExemplarFilter on the SDK, send_exemplars on the Collector, and exemplarTraceIdDestinations on Grafana so one histogram click lands on the originating trace (completed 2026-05-03)
- [x] **Phase 13: Log-Based Metrics (Loki Recording Rules)** - Enable the Loki ruler and define a recording rule that derives an error-rate metric from log patterns, then visualize it alongside the SDK-emitted counter (completed 2026-05-04)
- [ ] **Phase 14: JDBC/JPA Database Spans** - Extend the consumer service with full Spring Data JPA instrumentation — transaction-parent span wrapping JPA repository child spans — using the complete stable db.* semconv attribute set
- [ ] **Phase 15: Outbound HTTP-Client Spans** - Add TracingClientHttpRequestInterceptor to otel-bootstrap and wire it into the producer's RestClient so every outbound HTTP hop produces a CLIENT span with traceparent injected
- [ ] **Phase 16: Head Sampling + W3C Baggage** - Swap both services to a parentBased(traceIdRatioBased) sampler (sub-lesson 16a) and wire BaggageSpanAttributeProcessor so X-Customer-Tier flows from HTTP header through AMQP into both producer and consumer spans (sub-lesson 16b)
- [ ] **Phase 17: AMQP Topic + DLX Variants** - Extend the RabbitMQ topology with a topic exchange and DLX dead-letter queue, prove the existing propagation pair is exchange-type-agnostic, and demonstrate span links for DLX retry traces

## Phase Details

### Phase 10: Prerequisites & Stack Decomposition

**Pedagogical headline:** Attendees learn that a production observability stack is five separate services with explicit pipelines, and that replacing the all-in-one container changes zero Java lines.

**Goal**: The workshop stack is reset to a production-shaped baseline — five pinned containers replace `grafana/otel-lgtm`, the runtime circular-reference bug is fixed, and all existing dashboards and signals work identically to v1.0.

**Depends on**: Nothing (first v2.0 phase; v1.0 shipped)

**Requirements**: PREREQ-01, PREREQ-02, STACK-01, STACK-02, STACK-03, STACK-04, STACK-05

**Success Criteria** (what must be TRUE):
  1. Both `producer-service` and `consumer-service` start without any `BeanCurrentlyInCreationException`; the `@Autowired` field on `OtelSdkConfiguration` is gone and the circular-reference cycle is eliminated
  2. `mise run infra:up` starts exactly five new containers (`otel-collector`, `tempo`, `mimir`, `loki`, `grafana`) with no `grafana/otel-lgtm` service present; all five image tags are exact patch versions
  3. A `POST /orders` request produces a trace in Tempo, a metric count in Mimir, and a log line in Loki — all three signals reach their backends without any change to `OTEL_EXPORTER_OTLP_ENDPOINT` or `OtelSdkConfiguration`
  4. The `ose-otel-demo` Grafana dashboard loads at `:3000` with all panels rendering — no "Datasource not found" error on any panel; the deferred `step-04-metrics.png` screenshot (PREREQ-02) is captured and added to `docs/screenshots/`
  5. Mimir container logs show zero `401` errors after a `POST /orders`; metrics appear in Grafana within ~10 seconds

**Pitfall mitigations**: X-1 (circular-reference fix is the first task in this phase — must land before any other change to `OtelSdkConfiguration`), F1-1 (datasource UIDs from `otel-lgtm` container inspected and reused verbatim in `grafana/datasources.yaml`), F1-3 (`mimir-config.yaml` sets `auth_enabled: false`), X-3 (all five image tags pinned to exact patch versions before phase exits; `mise run verify:images` gate enforced)

**Git tag**: `step-10-collector-decompose`

**Plans**: 5 plans across 4 waves
- [x] 10-01-PLAN.md — PREREQ-01: diagnose + fix BeanCurrentlyInCreationException cycle in both OtelSdkConfiguration.java files (D-12 inline-assign)
- [x] 10-02-PLAN.md — Author 5 backend YAML configs under infra/observability/ (otelcol, tempo, mimir, loki, .gitkeep) — verbatim live-verified shapes, multitenancy_enabled, otlp_http
- [x] 10-03-PLAN.md — Author grafana/datasources.yaml (verbatim lgtm UIDs prometheus/tempo/loki + D-02 cross-signal datalinks); update grafana/dashboards/dashboards.yaml options.path (Pitfall 8)
- [x] 10-04-PLAN.md — Rewrite docker-compose.yml (drop lgtm, add 5 obs services, 5 named volumes, healthchecks, depends_on); delete grafana/prometheus.yaml; extend mise.toml (preflight + verify:datasources + verify:images)
- [x] 10-05-PLAN.md — End-to-end smoke (5 SCs); human-verify dashboard + capture step-04-metrics.png (PREREQ-02 / D-13); README Step 10

---

### Phase 11: Tail Sampling at the Collector

**Pedagogical headline:** Attendees learn that the Collector can make sampling decisions that no SDK-side sampler can — decisions based on the complete assembled trace including error status and total latency.

**Goal**: The Collector's traces pipeline is extended with a three-policy `tail_sampling` processor; attendees observe trace-volume reduction and understand the OR-semantics priority chain governing how policies interact.

**Depends on**: Phase 10

**Requirements**: TSAMP-01, TSAMP-02, TSAMP-03

**Success Criteria** (what must be TRUE):
  1. The Collector config activates `tail_sampling` with three policies in order — `status_code ERROR` keep-100%, `latency` keep-100% above 1s, probabilistic 20% fallback — with `decision_wait: 10s` and `num_traces: 10000` for workshop responsiveness
  2. The Collector config file contains an explicit comment block documenting the OR-semantics priority chain (`drop > inverted_not_sample > sample > inverted_sample`) so attendees understand why all policies evaluate rather than stopping at the first match
  3. After running `scripts/load.sh`, the attendee observes in Tempo that every error-status trace is preserved and non-error trace volume drops to approximately 20% of the pre-tail-sampling baseline; the README §11 contains the OFF/ON paired `<table>` placeholder referencing `step-11-tail-sampling-OFF.png` / `step-11-tail-sampling-ON.png` (both PNGs generated by Phase 18 Playwright script)

**Pitfall mitigations**: F2-1 (OR semantics documented in Collector config comments — not first-match), F2-2 (`decision_wait: 10s` rather than the default 30s for demo responsiveness), F2-3 (SDK remains at `Sampler.parentBased(alwaysOn())` in this phase — head sampling is NOT activated here; README explicitly notes that Phase 16 head sampling must not be combined with this tail sampling without acknowledging the head%×tail% double-filter rate)

**Git tag**: `step-11-tail-sampling`

**Plans**: 6 plans across 4 waves
- [x] 11-01-PLAN.md — Wave 0: capture step-11-tail-sampling-OFF.png on main HEAD pre-Phase-11 (D-T9 manual one-shot; sequencing constraint per <specifics> bullet 4)
- [x] 11-02-PLAN.md — Wave 1: insert tail_sampling composite block in otelcol-config.yaml (D-T1/D-T2/D-T3/D-T4 + TSAMP-01/02 + line-180 fix); add --feature-gates=processor.tailsamplingprocessor.recordpolicy to docker-compose.yml otel-collector command (Route A)
- [x] 11-03-PLAN.md — Wave 1: WIDGET-SLOW SKU branch in OrderService.place() (D-T5/D-T6); SLOW_RPS=2 fourth oha stream in scripts/load.sh (D-T7) — parallel with 11-02 (zero file overlap)
- [x] 11-04-PLAN.md — Wave 2: mise verify:tail-sampling two-tier task (D-T14 Route A) — depends on 11-02 + 11-03
- [x] 11-05-PLAN.md — Wave 2: collapsed Tail Sampling diagnostics row in ose-otel-demo.json (D-T13/D-T16) — depends on 11-02; parallel with 11-04
- [x] 11-06-PLAN.md — Wave 3: README §11 Phase-10-equivalent narrative (D-T11) + F2-3 double-filter callout (D-T12) + histogram-suffix verification micro-task (RESEARCH §3.2.1); screenshot captures (D-T9/D-T10) deferred to Phase 18 Playwright script

---

### Phase 12: Exemplars: Metrics to Trace Click-Through

**Pedagogical headline:** Attendees learn the three-layer exemplar plumbing — one SDK line, one Collector line, one Grafana datasource key — that turns a histogram chart into a clickable gateway to the originating trace.

**Goal**: Both services gain `ExemplarFilter.traceBased()` on their MeterProvider, the Collector forwards exemplars to Mimir, and Grafana is provisioned with `exemplarTraceIdDestinations` so a single click on a histogram exemplar dot lands on the trace in Tempo.

**Depends on**: Phase 10

**Requirements**: EXMP-01, EXMP-02, EXMP-03, EXMP-04

**Success Criteria** (what must be TRUE):
  1. Both `OtelSdkConfiguration.buildMeterProvider()` calls include `.setExemplarFilter(ExemplarFilter.traceBased())` — exactly one line per service
  2. `infra/observability/mimir-config.yaml` adds `limits.max_global_exemplars_per_user: 100000` (the real enablement gate — Mimir's default is 0, which silently discards all exemplars); Grafana `datasources.yaml` already includes `exemplarTraceIdDestinations` pre-wired in Phase 10 D-02 — no edits needed; otelcol-config.yaml adds a WHY comment documenting that PRW v1 exemplar forwarding is unconditional (no `send_exemplars` config key exists in v0.151.0)
  3. After generating load, the attendee opens the `http.server.request.duration` histogram panel in the `ose-otel-demo` dashboard, sees exemplar dots or diamonds rendered, clicks one, and lands on the originating trace in Tempo — one click, no manual trace-ID copy-paste required
  4. Exemplar labels on emitted data contain only `trace_id` and `span_id` — no business attributes are attached that could cause cardinality explosion in Mimir

**Pitfall mitigations**: X-1 (circular-reference fix already applied in Phase 10 — verify before touching `OtelSdkConfiguration`), F3-1 (histogram observation occurs inside an active span scope in `HttpServerSpanFilter` so `ExemplarFilter.traceBased()` can attach a live `trace_id`), F3-2 (exemplar labels restricted to `trace_id`/`span_id` only), F3-3 (`exemplarTraceIdDestinations.datasourceUid` matches the actual Tempo datasource UID set in Phase 10's provisioning)

**Git tag**: `step-12-exemplars`

**Plans**: 4 plans across 2 waves
- [x] 12-01-PLAN.md — Wave 1: ExemplarFilter.traceBased() in both OtelSdkConfiguration.buildMeterProvider() + HttpServerSpanFilter manual scope fix (D-E1/EXMP-01)
- [x] 12-02-PLAN.md — Wave 1: mimir-config.yaml limits.max_global_exemplars_per_user: 100000 + otelcol-config.yaml WHY comment (EXMP-02) — parallel with 12-01
- [x] 12-03-PLAN.md — Wave 1: Exemplars open row in ose-otel-demo.json (D-E4/D-E5/D-E6) + verify:exemplars task in mise.toml (D-E7/EXMP-03) — parallel with 12-01 and 12-02
- [x] 12-04-PLAN.md — Wave 2: README §12 Phase-11-equivalent narrative (D-E8) + end-to-end human-verify checkpoint (EXMP-04)

---

### Phase 13: Log-Based Metrics (Loki Recording Rules)

**Pedagogical headline:** Attendees learn that an operations team can derive structured metrics from application logs without touching a single line of Java — and see where that power ends compared to SDK-emitted metrics.

**Goal**: The Loki ruler is enabled, a recording rule derives an error-rate metric from log patterns and remote-writes it to Mimir, and a Grafana panel visualizes the log-derived metric alongside the SDK-emitted counter on a shared axis.

**Depends on**: Phase 10

**Requirements**: LMET-01, LMET-02, LMET-03

**Success Criteria** (what must be TRUE):
  1. `loki-config.yaml` includes the `ruler` component enabled and configured to remote-write recording rule outputs to Mimir using the same `auth_enabled: false` tenant as Phase 10's Mimir setup
  2. A recording rule file at `infra/observability/loki-rules/order-errors.yaml` defines `log:order_errors:rate2m` using the LogQL expression `sum by (service_name) (rate({service_name=~"order-.+"} |= "ERROR" [2m]))` — the `log:` prefix prevents collision with SDK metric names
  3. A Grafana panel plots the log-derived `log:order_errors:rate2m` series alongside the SDK-emitted `orders.created` counter; both share the `service_name` axis so the workshop demonstration can contrast "metrics from app code" versus "metrics derived from logs"

**Pitfall mitigations**: F4-1 (`log:<metric>:<aggregation>` naming prefix prevents collision with SDK-emitted metric names in Mimir), F4-2 (LogQL range window set to `[2m]` — 2x the 1-minute evaluation interval — to avoid zero-valued metrics on sparse log streams), F4-3 (volume mount path in `docker-compose.yml` matches `ruler_storage.local.directory` in `loki-config.yaml`), F4-4 (rule groups only by `service_name` and `level` — never by `order_id`, `span_id`, or other high-cardinality labels)

**Git tag**: `step-13-log-based-metrics`

**Plans**: 2 plans across 2 waves
- [x] 13-01-PLAN.md — Recording rule file (fake/ subdirectory), dashboard panel (Log-Based Metrics row), verify:log-metrics mise task
- [x] 13-02-PLAN.md — README Step 13 walkthrough + end-to-end human-verify checkpoint

---

### Phase 14: JDBC/JPA Database Spans

**Pedagogical headline:** Attendees write manual `SpanKind.CLIENT` spans around JPA repository calls using the current stable `db.*` semconv constants, and see a transaction-level parent span expose rollback behavior as a `status=ERROR` span in Tempo.

**Goal**: The consumer service gains Spring Data JPA, an entity, and a service layer; every repository method invocation is wrapped in a `CLIENT` span with the full stable `db.*` semconv attribute set; a transaction-level `INTERNAL` span wraps the `@Transactional` boundary and surfaces rollbacks as errors.

**Depends on**: Phase 10

**Requirements**: DBSP-01, DBSP-02, DBSP-03, DBSP-04, DBSP-05

**Success Criteria** (what must be TRUE):
  1. `consumer-service/pom.xml` adds `spring-boot-starter-data-jpa` (BOM-managed; no version override); an `Order` entity, `OrderJpaRepository`, and `OrderJpaService` exist; the `@RabbitListener` order-processing path persists each order via `OrderJpaService.persist(...)`
  2. A Tempo trace search for `db.query.text=*` returns consumer-service spans; each DB span carries `db.system.name="postgresql"`, `db.namespace`, `db.operation.name`, `db.collection.name`, and `db.query.text` using `DbAttributes.DB_QUERY_TEXT` — never the deprecated string literal `"db.statement"`
  3. The transaction-level `INTERNAL` span is the parent of all JPA repository child spans; when the deterministic 10% failure path triggers a rollback, that transaction span carries `status=ERROR` in Tempo; the `@Order(HIGHEST_PRECEDENCE)` annotation ensures the span aspect wraps — not is wrapped by — the transaction proxy
  4. The README §14 contains a `step-14-jpa.png` placeholder pairing the v1.0 single-db-span trace (from `step-08-db-cache`) with the v2.0 transaction-parent plus N JPA child span waterfall; the PNG itself is generated by Phase 18 Playwright script

**Pitfall mitigations**: X-1 (circular-reference fix already applied in Phase 10; verify before adding any new `@Bean` to Spring context), X-4 (`TestOtelConfiguration` updated to match any `OtelSdkConfiguration` changes in this phase), F5-1 (span wraps at repository method level — not SQL execution level — to prevent N+1 span explosion), F5-2 (`DbAttributes.DB_QUERY_TEXT` constant used exclusively — no string literal `"db.statement"` anywhere in the codebase), F5-3 (span is placed outer to the `@Transactional` boundary so rollbacks surface as errors on the outermost span)

**Git tag**: `step-14-jpa-spans`

**Plans**: TBD

---

### Phase 15: Outbound HTTP-Client Spans

**Pedagogical headline:** Attendees complete the HTTP instrumentation story started in v1.0 by writing the CLIENT-side counterpart to the existing SERVER span — using the same intercept-and-inject pattern from the AMQP module, now applied to outbound HTTP via RestClient.

**Goal**: `otel-bootstrap` gains a `TracingClientHttpRequestInterceptor`; the producer's `OrderService` makes an outbound HTTP call through an injected `RestClient.Builder` bean; a `CLIENT` span appears in Tempo as a child of the producer's `INTERNAL` span with `traceparent` confirmed in the receiving endpoint's request log.

**Depends on**: Phase 10

**Requirements**: HCLI-01, HCLI-02, HCLI-03, HCLI-04

**Success Criteria** (what must be TRUE):
  1. `otel-bootstrap/http/TracingClientHttpRequestInterceptor.java` exports a `ClientHttpRequestInterceptor` that wraps each outbound call in a `SpanKind.CLIENT` span and injects `traceparent`, `tracestate`, and `baggage` (when present) via a `TextMapSetter<HttpHeaders>` — symmetric in structure to `TracingMessagePostProcessor`
  2. Each CLIENT span carries `http.request.method`, `server.address`, `server.port`, `url.full`, `http.response.status_code`, and `service.peer.name` — the stable v2.0 HTTP semconv constants; `http.response.status_code` is captured on both success and exception paths
  3. A `POST /orders` request shows in Tempo a `CLIENT` span as a child of the producer's `INTERNAL` span; the in-process `NotificationStubController.notify()` request log confirms the `traceparent` header arrived at the receiving end
  4. The interceptor is registered LAST in the `RestClient.Builder` interceptor chain; the `RestClient` is always obtained from an injected `RestClient.Builder` bean — `RestClient.create(url)` is never used in the producer service

**Pitfall mitigations**: F6-1 (`RestClient.Builder` injected as a Spring bean — `RestClient.create(url)` bypasses the interceptor and is explicitly prohibited in the phase plan), F6-2 (span started and made current before the outbound HTTP call executes — not after), F6-3 (OTel interceptor registered last in the interceptor chain so `traceparent` is the final header written), F6-4 (`http.response.status_code` captured in both the happy path and in the exception handler to avoid missing status data on errors)

**Git tag**: `step-15-http-client-spans`

**Plans**: TBD

---

### Phase 16: Head Sampling + W3C Baggage

**Pedagogical headline:** Sub-lesson 16a — attendees swap the SDK sampler to `parentBased(traceIdRatioBased(0.5))` and observe that the SDK discards traces before they reach the Collector; sub-lesson 16b — attendees wire `BaggageSpanAttributeProcessor` so the `X-Customer-Tier` header set at the HTTP entry point stamps both producer and consumer spans across the AMQP boundary.

**Goal**: Both services activate ratio-based head sampling (50%) via an explicit code change in `OtelSdkConfiguration`; `BaggageSpanAttributeProcessor` is added to `otel-bootstrap` and registered on both `SdkTracerProvider`s; an `X-Customer-Tier` HTTP header propagates as `baggage.customer-tier` on both the producer and consumer spans in Tempo.

**Depends on**: Phase 10, Phase 11 (head sampling taught explicitly AFTER tail sampling — see pitfall F2-3)

**Requirements**: HSAMP-01, HSAMP-02, HSAMP-03, BAG-01, BAG-02, BAG-03, BAG-04

**Success Criteria** (what must be TRUE):
  1. After running the load script for 100 requests, approximately 50 traces appear in Tempo (50% head-sampling ratio); console-emitted span output confirms that the SDK discards spans before export — no Collector processor ever received the dropped traces
  2. The README contains a paired table contrasting head vs tail sampling: head sampling decides at the SDK before export (saves bandwidth, cannot see the full trace); tail sampling decides at the Collector after the trace is assembled (can see full trace, but all spans were exported first); `step-16-head-sampling.png` pairs with `step-11-tail-sampling-ON.png` from Phase 11; both PNGs generated by Phase 18 Playwright script
  3. A `curl -H "X-Customer-Tier: gold" -X POST http://localhost:8080/orders` results in Tempo showing `baggage.customer-tier=gold` as a span attribute on BOTH the producer span and the consumer span — one trace, two spans, both stamped — proving baggage survived the AMQP boundary

> **CRITICAL: Double-filter trap (F2-3).** Running Phase 11's tail sampling (20% probabilistic fallback) simultaneously with Phase 16's head sampling (50% ratio) produces approximately 10% effective trace rate (50% × 20%). The Phase 16 README sub-lesson 16a MUST contain a dedicated callout explaining this trap. Workshop attendees who have Phase 11 active while activating Phase 16 MUST be instructed to either (a) set `OTEL_TRACES_SAMPLER=parentbased_always_on` to disable head sampling during tail-sampling demos, or (b) explicitly accept the ~10% effective rate.

**Pitfall mitigations**: X-1 (circular-reference fix already applied in Phase 10 — verify before modifying `OtelSdkConfiguration` in both services), X-4 (`TestOtelConfiguration` updated for new `BaggageSpanAttributeProcessor` registration and sampler change), F2-3 (head sampling taught in Phase 16 with mandatory double-filter-trap documentation; tail sampling was taught in Phase 11; the two are never activated simultaneously in the demo without the README callout), F7-1 (sampler swap done via explicit code change in `OtelSdkConfiguration` — NOT via `OTEL_TRACES_SAMPLER` env var; the workshop teaches the explicit-call shape), F7-2 (`extractedContext.makeCurrent()` wraps the ENTIRE listener body in `OrderListener` so `Baggage.current()` is populated throughout the consumer processing path), F7-3 (explicit allowlist of baggage keys stamped as attributes — never iterate all entries on `Baggage.fromContext()` as that produces unbounded cardinality), F7-4 (all `span.makeCurrent()` calls in try-with-resources)

**Git tag**: `step-16-sampling-baggage`

**Plans**: TBD

---

### Phase 17: AMQP Topic + DLX Variants

**Pedagogical headline:** Attendees discover that the existing `TracingMessagePostProcessor`/`TracingMessageListenerAdvice` pair propagates trace context unchanged across topic exchanges and DLX dead-lettering — and that span links (not parent-child) are the correct model when a failed message re-enters as a new logical operation after a DLX retry.

**Goal**: The RabbitMQ topology gains a topic exchange and DLX dead-letter queue; the producer publishes with a routing key; failed messages are dead-lettered and retried by a new `DlxRetryListener`; the retry opens a new trace linked to the failed original; an integration test proves all three behaviors.

**Depends on**: Phase 10 (decomposed stack for stable integration testing), Phase 16 (full instrumentation in place — sampling, baggage, JPA spans all visible in Tempo while diagnosing DLX retry behavior)

**Requirements**: AMQP-01, AMQP-02, AMQP-03, AMQP-04, AMQP-05

**Success Criteria** (what must be TRUE):
  1. `consumer-service/config/RabbitTopologyConfig.java` declares topic exchange `orders.topic`, queue `orders.standard.q` bound with routing-key pattern `orders.*.standard`, DLX exchange `orders.dlx`, and queue `orders.dlq` bound to receive dead-lettered messages from `orders.standard.q`
  2. A `POST /orders` request routes through the topic exchange with routing key `orders.us-east.standard`; the producer span in Tempo carries `messaging.rabbitmq.destination.routing_key=orders.us-east.standard`; the routing key is drawn from a small low-cardinality region set — never a per-message user-controlled value
  3. When the deterministic 10% failure path fires, the failed message appears in `orders.dlq`; `DlxRetryListener.onDlx()` strips `traceparent`/`tracestate` headers before any retry-publish, opens a new root trace via `tracer.spanBuilder("dlx.retry").setNoParent().addLink(originalSpanContext).startSpan()`, and the attendee sees in Tempo two SEPARATE traces connected by a span link — not a single trace growing on every retry
  4. `DlxTopologyIT.java` passes with three assertions green: (a) routing key `orders.us-east.standard` reaches `orders.standard.q`, (b) a triggered failure dead-letters the message to `orders.dlq`, (c) the DLX retry span carries a `Link` to the original failed trace and has a distinct `traceId` — proving no trace loop exists

**Pitfall mitigations**: F8-2 (`traceparent`/`tracestate` headers stripped from dead-lettered message BEFORE any retry-publish; `.setNoParent()` + `addLink()` used for DLX retry — this prevents the infinite trace loop where the same trace accumulates spans on every retry cycle; this is the highest-consequence pitfall in this phase), F8-3 (routing key stored as span attribute uses the low-cardinality key PATTERN, not a per-message resolved value — prevents cardinality explosion in Tempo)

**Git tag**: `step-17-amqp-topology`

**Plans**: TBD

### Phase 18: Automated Screenshot Generation (Playwright)

**Pedagogical headline:** All `docs/screenshots/` teaching artifacts are regenerated by a single Playwright script — no operator needs to know which Grafana panel to navigate to or which time-range to select.

**Goal**: A `scripts/capture-screenshots.mjs` Playwright script drives headless Chromium through every Grafana view referenced in the README and saves each PNG to `docs/screenshots/` at the canonical path. A `mise run screenshots` task orchestrates infra health-checks, load warm-up, the Playwright run, and a post-run diff that flags any PNG that failed to update. Phases 11, 14, and 16 each have at least one screenshot that was captured manually during their respective waves; Phase 18 replaces those manual captures with a reproducible automated run that any workshop maintainer can re-execute after a stack upgrade.

**Depends on**: Phase 17 (all teaching content in place so screenshots reflect the final state)

**Requirements**: SCAP-01, SCAP-02, SCAP-03

**Success Criteria** (what must be TRUE):
  1. `mise run screenshots` exits 0, producing or updating all PNG files listed in `docs/screenshots/` — at minimum: `step-11-tail-sampling-OFF.png`, `step-11-tail-sampling-ON.png`, `step-14-jpa.png`, `step-16-head-sampling.png`; existing screenshots (`step-01-*` through `step-10-*`) are not clobbered unless `FORCE=1` is set
  2. The tail-sampling OFF/ON pair is captured by the script itself — it temporarily disables the `tail_sampling` processor block in `otelcol-config.yaml` (sed comment-out), restarts the collector, waits for warm-up load, captures the OFF frame, then restores the config, restarts again, waits, and captures the ON frame; both PNGs show the Tempo Search panel with Service Name = order-producer and Last 5 min time range
  3. Each screenshot Playwright step includes an explicit `waitForLoadState('networkidle')` + a data-presence assertion (e.g., visible trace-count row or histogram bar) before `page.screenshot()` — no blank or spinner frames committed

**Pitfall mitigations**: P18-1 (Playwright Chromium launched with `--no-sandbox` for CI compatibility; `headless: true` required — never `headless: false` in the script), P18-2 (collector restart uses `docker compose restart otel-collector` + a `curl` health-check poll loop with 30s timeout before proceeding — avoids capturing the 10s `decision_wait` warm-up window), P18-3 (otelcol-config.yaml is restored unconditionally in a `finally` block — even if the OFF-frame capture throws — so the dev stack is never left with tail sampling disabled after an interrupted run)

**Git tag**: `step-18-screenshots`

**Plans**: 3 plans across 2 waves
- [x] 18-01-PLAN.md — Wave 1: Write scripts/capture-screenshots.mjs (unified Playwright capture script) + scripts/package.json (Playwright 1.59.1); all logic in script per D-S1/D-S2/D-S3/D-S4 (SCAP-01/02/03)
- [x] 18-02-PLAN.md — Wave 1: Delete scripts/screenshots/; update mise.toml (delete docs:screenshots, add screenshots one-liner per D-S5); update README.md (step-04 TODO → image embed, remove PREREQ-02 pending note per D-I3) — parallel with 18-01
- [x] 18-03-PLAN.md — Wave 2: Install Playwright + run mise run screenshots (v2.0 captures); human-verify PNG visual quality; commit + apply step-18-screenshots tag (WORK-01)

---

## Progress

| Phase | Milestone | Plans Complete | Status | Completed | Tag |
|-------|-----------|----------------|--------|-----------|-----|
| 1. Baseline & Scaffold | v1.0 | 6/6 | Shipped | 2026-04-29 | step-01-baseline |
| 2. Manual SDK Bootstrap & First Traces | v1.0 | 6/6 | Shipped | 2026-05-01 | step-02-traces |
| 3. AMQP Context Propagation | v1.0 | 5/5 | Shipped | 2026-05-01 | step-03-context-propagation |
| 4. Metrics | v1.0 | 5/5 | Shipped | 2026-05-01 | step-04-metrics |
| 5. Logs Correlation | v1.0 | 6/6 | Shipped | 2026-05-01 | step-05-logs |
| 6. Verification Tests | v1.0 | 6/6 | Shipped | 2026-05-02 | step-06-tests |
| 7. Polish & Differentiators | v1.0 | 7/7 | Shipped | 2026-05-02 | *(no tag — D-09)* |
| 10. Prerequisites & Stack Decomposition | v2.0 | 0/5 | Not started | - | step-10-collector-decompose |
| 11. Tail Sampling at the Collector | v2.0 | 0/? | Not started | - | step-11-tail-sampling |
| 12. Exemplars: Metrics to Trace Click-Through | v2.0 | 0/? | Not started | - | step-12-exemplars |
| 13. Log-Based Metrics (Loki Recording Rules) | v2.0 | 0/? | Not started | - | step-13-log-based-metrics |
| 14. JDBC/JPA Database Spans | v2.0 | 0/? | Not started | - | step-14-jpa-spans |
| 15. Outbound HTTP-Client Spans | v2.0 | 0/? | Not started | - | step-15-http-client-spans |
| 16. Head Sampling + W3C Baggage | v2.0 | 0/? | Not started | - | step-16-sampling-baggage |
| 17. AMQP Topic + DLX Variants | v2.0 | 0/? | Not started | - | step-17-amqp-topology |
| 18. Automated Screenshot Generation (Playwright) | v2.0 | 3/3 | Shipped | 2026-05-03 | step-18-screenshots |

## Backlog

*(v2.x deferred items documented in REQUIREMENTS.md § Future Requirements)*
