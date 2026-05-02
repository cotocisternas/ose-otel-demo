# Milestones

## v1.0 — Workshop (Shipped: 2026-05-02)

**Delivered:** A workshop-grade Spring Boot 3.4.13 / Java 17 demo that teaches engineers to instrument an HTTP → AMQP → consumer flow using the OpenTelemetry Java SDK directly, emitting all three signals (traces, metrics, logs) to Grafana otel-lgtm, with annotated git tags `step-01-baseline` through `step-06-tests` for time-travel through the workshop.

**Scope:** 7 phases · 41 plans · 61 tasks · 277 commits · ~3,800 LOC Java · 2026-04-29 → 2026-05-02

### Key Deliverables (per phase)

| # | Phase | Tag | Headline |
|---|---|---|---|
| 1 | Baseline & Scaffold | `step-01-baseline` | Working two-service Spring Boot + RabbitMQ app on host JVM with **zero** OTel libraries; foundation pitfalls neutralised (BOM ordering, ports, mise/IDE) |
| 2 | Manual SDK Bootstrap & First Traces | `step-02-traces` | `OpenTelemetrySdk` wired per-service; traces emitted; producer + consumer in **separate** trace IDs (intentional pre-Phase-3 broken state) |
| 3 | AMQP Context Propagation | `step-03-context-propagation` | **Headline lesson** — `TracingMessagePostProcessor` (inject) + `TracingMessageListenerAdvice` (extract) join the two services into one distributed trace; deterministic 10% failure path added |
| 4 | Metrics | `step-04-metrics` | `SdkMeterProvider` as a sibling pipeline next to the tracer; `orders.created` Counter, HTTP server-duration Histogram (seconds, not millis), `orders.queue.depth.estimate` ObservableGauge → Mimir on 10s interval |
| 5 | Logs Correlation | `step-05-logs` | `SdkLoggerProvider` + Logback `OpenTelemetryAppender` + MDC injector; logs in Loki carry `trace_id`/`span_id` structured metadata; click-through datalink to Tempo |
| 6 | Verification Tests | `step-06-tests` | Cross-service Testcontainers `RabbitMQContainer` + `InMemorySpanExporter` + `SimpleSpanProcessor` deterministically prove the three-signal chain in CI |
| 7 | Polish & Differentiators | *no tag (D-09)* | Pre-built Grafana dashboard (`ose-otel-demo` UID), `scripts/load.sh` workshop load generator, 6 step-paired screenshots, full README walkthrough |

### Workshop Polish & Quick Tasks (post-Phase-7, on `main` past `step-06-tests`)

- `step-08-db-cache` — Phase 8 quick task added Valkey + PostgreSQL with manual `SPAN_KIND_CLIENT` instrumentation (`InstrumentedJedisPool.SET`, `INSERT processed_orders`), HikariCP `db.client.connection.count` ObservableGauge, fifth integration test
- `scripts/load.sh` evolved through three quick tasks: `--no-tui` + finite `-z` (260502-ld1), burst-mode (260502-ld2), idempotency-key stream
- Grafana NOC view audited end-to-end: aggregate-across-services framing, RED matrix scoped to entry-point spans, traceid textbox no-op until populated, default time-range tightened to `now-15m`, auto-refresh `30s`
- Prometheus `global.scrape_interval` tightened to `10s` for workshop-demo resolution; rate windows widened to `[5m]` for scraped exporter metrics

### Key Decisions Carried Forward

- **D-01** Sibling-pipeline structure (tracer → meter → logger build helpers in `OtelSdkConfiguration`) — every diff against the prior tag reads as "we added a sibling pipeline"
- **D-09** No `step-07-*` tag — `main` HEAD past `step-06-tests` IS the polish state
- **DOC-04** Broken/fixed pedagogy anchor: `step-02-disconnected-traces.png` paired side-by-side with `step-03-joined-trace.png`
- **PROP-04** AMQP propagation pair lives in shared `otel-bootstrap` Maven module — symmetry of one inject method matched by one extract method IS the lesson (opposite of the SDK-bootstrap per-service duplication)
- **TRACE-01 / DOC-05** SDK bootstrap (`OtelSdkConfiguration`) is **inlined per-service**, not shared — duplication is the lesson
- **WORK-01** Workshop checkpoints are immutable annotated git tags on `main`, not long-lived branches

### Known Deferred Items at Close (6)

Acknowledged at milestone close on 2026-05-02; carried forward as workshop-polish backlog (see `STATE.md` § Deferred Items):

- 2 quick-task directories with non-standard status records (260502-8gk shipped under tag `step-08-db-cache`; 260502-j00 shipped as the Phase 9 infra exporters in `docker-compose.yml`)
- Phase 02 + Phase 04 `*-HUMAN-UAT.md` and `*-VERIFICATION.md` items marked `human_needed` — manual telemetry inspection completed by operator outside the GSD audit loop
- Phase 02 verification tests 4 & 5 are superseded-by-Phase-3 (broken-state proofs no longer hold at HEAD; observable only at tag `step-02-traces`)
- One Phase 7 screenshot (`docs/screenshots/step-04-metrics.png`) deferred per operator approval — DOC-04 anchor pair (step-02 + step-03) shipped

### Stats

- **Files:** ~110 source files (Java: producer-service, consumer-service, otel-bootstrap, integration-tests; configs: `pom.xml` × 4, `mise.toml`, `docker-compose.yml`, `application.yaml` × 2, `logback-spring.xml` × 2; observability: `grafana/dashboards/*.json` × 3, `grafana/prometheus.yaml`, `grafana/dashboards.yaml`)
- **Tags:** 7 workshop checkpoints (step-01 through step-06 + step-08-db-cache); D-09 honored (no step-07-*)
- **Screenshots:** 6 / 7 PNGs (step-04-metrics.png deferred per operator approval)
- **Workshop-load surface:** `scripts/load.sh` with three streams (steady idempotent, burst, idempotency-key) all controllable via env vars

---
