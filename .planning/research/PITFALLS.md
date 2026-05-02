# Pitfalls Research — v2.0 Production Shapes

**Domain:** Adding production-grade observability pipeline + expanded SDK instrumentation to Spring Boot 3.4.13 + manual OTel SDK 1.61.0 + Spring AMQP workshop app
**Researched:** 2026-05-02
**Extends:** `.planning/milestones/v1.0-research/PITFALLS.md` (inherited v1.0 pitfalls apply in full; this file adds v2.0-specific ones)
**Confidence:** HIGH where verified against official docs / issue tracker; MEDIUM where community-sourced only

> Severity tags:
> - **HIGH** — milestone-blocking or demo-silently-lying; must be addressed before the phase ships
> - **MEDIUM** — polish-affecting or learning-anti-pattern; must be addressed before workshop delivery
> - **LOW** — nice-to-fix; won't block the phase but will surface as a friction point for some attendees

---

## Cross-Cutting Pitfalls (apply to all 8 features)

### X-1: Carryover circular-reference cycle in `OtelSdkConfiguration`

**Severity:** HIGH — every v2.0 feature that touches `OtelSdkConfiguration` re-hits this at runtime

**What goes wrong:**
Plans 05-02 and 05-03 introduced `@Autowired OpenTelemetry openTelemetry` as a field on the same `@Configuration` class that produces the `openTelemetry` `@Bean`. Spring 2.6+ disallows circular references by default; the JVM crashes at startup with `BeanCurrentlyInCreationException`. `mvn compile` is silent — the cycle is runtime-only.

**Warning signs:**
- Application starts, emits a Spring `BeanCurrentlyInCreationException` for `otelSdkConfiguration`, then exits immediately.
- The cycle is invisible in the IDE (no red underlines) and invisible in `mvn compile`.
- Any v2.0 plan that adds a second injected dependency to `OtelSdkConfiguration` amplifies the cycle (two broken beans instead of one).

**Prevention:**
Assign `this.openTelemetry = sdk` inside the `@Bean openTelemetry()` factory body immediately after the `OpenTelemetrySdk.builder()...build()` call. Drop the `@Autowired` field. The current code (post-Phase-05-06) already does this for the `OpenTelemetryAppender.install(sdk)` call — apply the same pattern to any reference needed in sibling `@Bean` methods. The fix is a one-liner: remove the field, call the method using the local variable.

**Which v2.0 feature must address it:** Whichever phase first modifies `OtelSdkConfiguration` (likely the JDBC/JPA spans phase or the sampling + baggage phase). Fix must be applied before any new code touches this class.

---

### X-2: Mid-milestone SDK minor-version bump (1.61.x → 1.62.x) during the development window

**Severity:** MEDIUM — 1.60.0 had a breaking glob-pattern case-sensitivity change; 1.61.0 changed `EnvironmentGetter`/`EnvironmentSetter` key normalization

**What goes wrong:**
OTel Java SDK ships a minor release approximately every 4–6 weeks. SDK 1.61.0 was released 2026-04-10; 1.62.0 will likely land during v2.0 development. Historical pattern: each minor version deprecates or removes incubating items. SDK 1.60.0 removed `otel.experimental.metrics.cardinality.limit` and deprecated `ExtendedAttributes`. SDK 1.61.0 changed environment variable key normalization in `EnvironmentGetter`/`EnvironmentSetter` — which affects any code that reads `OTEL_*` env-vars through the autoconfigure API.

**Warning signs:**
- Dependabot / Renovate opens a PR bumping `opentelemetry-bom` to 1.62.0; tests pass locally but `mvn verify` on the bumped BOM fails with `NoSuchMethodError` on an incubating type.
- A workshop attendee clones the repo six weeks after v2.0 ships, runs `mvn dependency:tree`, and sees a newer BOM than what the instructions mention.

**Prevention:**
Pin all OTel coordinates to `1.61.0` (core BOM) and `2.27.0-alpha` (instrumentation BOM) in the root `pom.xml` BOM import section. Do NOT use `LATEST` or range expressions. Add a `maven-enforcer-plugin` `dependencyConvergence` rule so any transitive drift surfaces as a build error, not a runtime surprise. When deliberately upgrading, do it as a dedicated commit so the diff is obvious.

---

### X-3: Floating container image tags for new Tempo / Mimir / Loki / Collector images

**Severity:** HIGH — workshop reproducibility is a first-class requirement; floating tags break it

**What goes wrong:**
The v1.0 `docker-compose.yml` correctly pins `grafana/otel-lgtm:0.26.0`, `rabbitmq:4.3-management-alpine`, etc. v2.0 decomposes `otel-lgtm` into five new services. Teams commonly start with `:latest` while iterating, intending to pin later — and then ship without pinning. The Grafana Tempo image, for example, releases about twice a month. An attendee who clones the repo three weeks after v2.0 ships may pull `grafana/tempo:main` (a nightly) with a different metrics_generator config schema.

**Warning signs:**
- `docker-compose.yml` contains `image: grafana/tempo:latest` or no version tag.
- `docker compose pull` shows a different digest on the CI machine than on the developer's laptop.

**Prevention:**
Pin every new image before the phase exits. Recommended pins (as of research date 2026-05-02):
- `grafana/tempo:2.10.x` (latest 2.10.x stable)
- `grafana/mimir:2.14.x` (latest 2.14.x stable)
- `grafana/loki:3.7.x` (latest 3.7.x stable)
- `otel/opentelemetry-collector-contrib:0.125.x` (latest stable contrib)
- `grafana/grafana:11.x.x` (latest 11.x LTS)

Use exact patch versions, not minor-only tags like `:2.10`.

---

### X-4: Test isolation: `InMemorySpanExporter` tests break when `OtelSdkConfiguration` changes

**Severity:** MEDIUM — v1.0 Phase-6 tests use a `TestOtelConfiguration` that wires `SimpleSpanProcessor` + `InMemorySpanExporter`; any change to `OtelSdkConfiguration` that adds a new `@Bean` or changes constructor signatures requires a matching update to `TestOtelConfiguration`

**What goes wrong:**
Phase-6 `TestOtelConfiguration` is a parallel copy of `OtelSdkConfiguration` with in-memory exporters. It is not derived from the production class. When v2.0 adds a `JdbcTracer` bean, a `BaggageManager` bean, or modifies the constructor signature of `Tracer`/`Meter`, the test configuration silently diverges — the test still passes but is no longer testing the same wiring as production.

**Warning signs:**
- A new `@Bean` added to production `OtelSdkConfiguration` exists in production tests (which fail) but passes in `integration-tests` module because `TestOtelConfiguration` does not include it.
- Integration test `OrderFlowIT` passes green but the span it validates doesn't contain the new attribute added by the v2.0 instrumentation (because `TestOtelConfiguration` doesn't wire the new component).

**Prevention:**
For each v2.0 phase that modifies `OtelSdkConfiguration`, the phase plan must include a step to update `TestOtelConfiguration` in parallel. A reliable check: `mvn -pl integration-tests test` must still produce green after every phase.

---

## Feature-Specific Pitfalls

---

## F1 — Decompose otel-lgtm into Separate Collector + Tempo + Mimir + Loki + Grafana

### F1-1: Grafana dashboard datasource UIDs — `ose-otel-demo` dashboard breaks if UIDs change

**Severity:** HIGH — the v1.0 `ose-otel-demo` dashboard JSON hardcodes the datasource UIDs from `otel-lgtm`'s built-in provisioning

**What goes wrong:**
`grafana/otel-lgtm` provisions its internal datasources with specific UIDs (`grafanacloud-tempo`, `grafanacloud-prometheus`, `grafanacloud-loki` or equivalent). The v1.0 `grafana/dashboards/ose-otel-demo.json` references these UIDs in every panel query (the `datasource: { uid: "..." }` field). When you decompose into separate containers and provision new datasources with new UIDs (e.g., `tempo`, `mimir`, `loki`), every panel in the existing dashboard returns "Datasource not found" or silently shows no data.

**Warning signs:**
- Grafana loads `ose-otel-demo` dashboard but all panels show "Datasource not found" or render empty.
- Grafana provisioning logs show: `Datasource not found: <old-uid>`.
- The datalink from Loki logs → Tempo traces no longer resolves.

**Prevention:**
Before decomposing, inspect the actual UIDs inside `otel-lgtm:0.26.0`. Run:
```
docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml
```
Note the `uid:` fields for each datasource. When you provision the new separate containers, reuse the SAME UIDs in the new `grafana/provisioning/datasources/*.yaml` files, OR do a search-and-replace in `ose-otel-demo.json` to use the new UIDs. The former (reuse same UIDs) is strongly preferred because it avoids editing the dashboard JSON and keeps the git diff minimal.

**Concrete action:** The decomposition phase plan must include a step that explicitly maps old UID → new UID and either reuses old UIDs in the new provisioning YAML or patches all occurrences in the dashboard JSON. Not doing this is the single most common "decompose and spend 2 hours debugging blank dashboards" mistake.

---

### F1-2: Tempo `metrics_generator` WAL path not mounted → RED metrics silently absent

**Severity:** HIGH for the exemplars feature (F3); MEDIUM for general service-graph metrics

**What goes wrong:**
Tempo's `metrics_generator` (which generates service graphs and span-rate RED metrics from traces) requires a write-ahead log (WAL) directory to persist in-progress state across restarts. In Docker Compose, if the WAL path (typically `/var/tempo/wal`) is not in a named volume or bind mount, every Tempo container restart loses the in-progress metric windows and the service graph appears empty for 1–5 minutes until enough traces arrive to re-prime it.

Additionally, `metrics_generator.remote_write` must point at the Mimir (or Prometheus) endpoint in the same compose network using the Docker service name, e.g., `http://mimir:9009/api/v1/push`. Using `http://localhost:...` from within the Tempo container resolves to the container's own loopback — connection refused.

**Warning signs:**
- Grafana "Service Graph" panel in the Tempo datasource shows no edges after 5+ minutes of traffic.
- Tempo logs: `metrics_generator: failed to remote write` or `connection refused`.
- `http_server_request_duration_seconds` generated by Tempo's metrics_generator never appears in Mimir.

**Prevention:**
- Mount the WAL: `volumes: [tempo-wal:/var/tempo/wal]` in the Tempo compose service.
- Use Docker service names for intra-compose URLs: `remote_write: url: http://mimir:9009/api/v1/push`.
- Mimir in single-binary mode (default for workshop) must have `--auth.multitenancy-enabled=false` OR the remote_write call must include `X-Scope-OrgID: anonymous` header; missing this header returns 401 and the metric is silently dropped.

---

### F1-3: Mimir single-binary missing `--auth.multitenancy-enabled=false` → all remote writes rejected

**Severity:** HIGH — metrics from both the OTel SDK (via Collector) and from Tempo's `metrics_generator` go to Mimir; if auth is on and no tenant ID is sent, all metrics are silently dropped

**What goes wrong:**
Grafana Mimir in single-binary mode (as used in the workshop) defaults to multi-tenancy enabled. Every Prometheus `remote_write` and OTLP ingest request MUST include an `X-Scope-OrgID` header. The OTel Collector's `prometheusremotewrite` exporter and `otlphttp` exporter do not add this header by default. Result: every metric write returns HTTP 401 and Mimir receives nothing, with no error surfaced in the SDK (the Collector logs the failure, but the SDK is unaware).

**Warning signs:**
- Mimir logs: `no org id: no X-Scope-OrgID header found` or `Unauthorized`.
- Grafana shows empty Mimir datasource despite traffic hitting the Collector.
- OTel Collector logs: `Exporting failed. Will retry the request after interval` repeatedly.

**Prevention:**
Either:
- Start Mimir with `--auth.multitenancy-enabled=false` (recommended for workshop — one less config dimension).
- Or: add `headers: {X-Scope-OrgID: anonymous}` to every remote_write and OTLP ingest path in the Collector config and in Tempo's `remote_write` config.

Chose option A (disable multitenancy) for the workshop — simpler and removes a source of attendee confusion.

---

### F1-4: OTel Collector config `otlp_http` vs `otlphttp` exporter naming — schema changed in Collector 0.150+

**Severity:** MEDIUM — will cause "unknown component" startup error in the Collector if the old name is copied from otel-lgtm's bundled config

**What goes wrong:**
In `grafana/otel-lgtm:0.24.0+`, Grafana updated the bundled Collector config to rename the OTLP HTTP exporter from `otlphttp` to `otlp/http` (or use explicit `otlphttp` depending on version). Community examples and tutorials still use the old name. If you copy a config snippet from an older blog post, the Collector fails with `unknown type: otlp_http` at startup.

**Warning signs:**
- OTel Collector container exits immediately with `unknown component: otlphttp` or `unknown component: otlp_http`.
- `docker compose logs collector` shows the parse error.

**Prevention:**
When writing the new Collector config, use the current stable component names from the `otel/opentelemetry-collector-contrib:0.125.x` image's own documentation. For traces: `exporters: otlp/tempo:` with `endpoint: tempo:4317`. For metrics: `exporters: prometheusremotewrite:` or `otlphttp/mimir:`. Run `docker run --rm otel/opentelemetry-collector-contrib:0.125.x components` to print all valid component names before writing the config.

---

### F1-5: Port collision between decomposed containers and developer laptop services

**Severity:** MEDIUM — same class as v1.0 Pitfall 14, but scope is larger; five new containers add five new potential conflicts

**What goes wrong:**
Decomposing otel-lgtm adds ~6 new ports to `docker-compose.yml`:
- Grafana: 3000 (same as before)
- Tempo: 3200 (HTTP), 4317 (OTLP gRPC — CONFLICTS with apps already sending to this port if both the old lgtm and new Tempo are up)
- Mimir: 9009 (HTTP)
- Loki: 3100 (HTTP)
- Collector: 4317 gRPC, 4318 HTTP

The critical conflict: if `lgtm` container is still in `docker-compose.yml` (even commented out) and the new `collector` service also exposes port 4317, `docker compose up` fails with "address already in use".

**Warning signs:**
- `docker compose up` fails on the collector or tempo service: `Bind: address already in use`.
- Apps start sending to port 4317 successfully but receive a rejection from a stale lgtm process.

**Prevention:**
Remove the `lgtm` service from `docker-compose.yml` entirely (not just commented out) before adding the decomposed services. Do this in a single atomic commit to prevent half-states. Add a `mise run preflight` check that verifies ports 3000, 3200, 4317, 4318, 9009, 3100 are free.

---

## F2 — Tail Sampling at the Collector

### F2-1: Tail sampling evaluates ALL policies, not first-match — "keep-errors first" ordering does NOT short-circuit

**Severity:** HIGH — widely misunderstood; teams write policies in "first-match wins" mental model but the processor uses a priority-based decision logic

**What goes wrong:**
The OpenTelemetry Collector's `tailsamplingprocessor` evaluates all policies and then applies a **decision priority hierarchy**: `drop` beats everything; `inverted_not_sample` beats everything else; `sample` beats `inverted_sample`; unmatched traces are dropped. This means:
1. A "keep all errors" `status_code: ERROR` policy does NOT prevent a "probabilistic 10%" policy from also running and potentially marking the same trace as `not_sample`.
2. If the `probabilistic` policy runs AFTER the error policy and both evaluate, the probabilistic "not_sample" decision does NOT override the error policy's "sample" decision — because `sample` beats `not_sample` in the priority chain. This is actually correct behavior.
3. But the inverse is the trap: if you want "drop noisy health-check traces ALWAYS, then sample the rest at 10%", you must use the `drop` decision (via `and` policy combining `http.url matches /health` with `inverted` wrap), not rely on the probabilistic policy running "first". A `not_sample` from probabilistic is overridden by ANY `sample` from a subsequent policy.

**Warning signs:**
- Health-check traces still appear in Tempo despite a policy that says "if URL contains /actuator/health, drop".
- "I thought the first matching policy would stop the others from running" — attendees expecting short-circuit behavior are surprised.

**Prevention:**
Use `drop` policies (available since collector-contrib 0.91.0 via the `and` + `inverted` combo, or directly with the `drop` decision type in newer versions) for explicit exclusions. For the workshop's "keep errors, keep slow, sample rest" goal:
```yaml
policies:
  - name: keep-errors
    type: status_code
    status_code: {status_codes: [ERROR]}
  - name: keep-slow
    type: latency
    latency: {threshold_ms: 1000}
  - name: probabilistic-10pct
    type: probabilistic
    probabilistic: {sampling_percentage: 10}
```
In this config, a trace that hits an error is sampled; a trace that hits the probabilistic policy is also sampled IF it passes the 10% roll. These are additive (OR semantics). This is the INTENDED behavior — document it explicitly.

---

### F2-2: `decision_wait` timeout with `decision_wait_after_root_received = 0s` drops late-arriving spans

**Severity:** MEDIUM — spans that arrive at the Collector after the 30-second window are evaluated on the already-made decision; if the trace was kept but late spans are not stored, Tempo shows incomplete traces

**What goes wrong:**
`decision_wait` defaults to 30 seconds. The Collector evaluates a trace for sampling once all spans within `decision_wait` have arrived. Any span that arrives AFTER the decision window is processed immediately with the cached decision. If `num_traces` (the circular buffer) is too small, the oldest trace (possibly mid-evaluation) is evicted — the spans are dropped, not forwarded.

For a workshop with 20 attendees simultaneously generating load, a `num_traces` of 50000 (default) is probably sufficient. But if someone runs `scripts/load.sh --burst` AND tail sampling simultaneously, the buffer can saturate.

**Warning signs:**
- Tempo shows "incomplete traces" — a parent span with no child spans, or vice versa.
- Collector metrics: `otelcol_processor_tail_sampling_late_span_total` ticking up.
- Collector metrics: `otelcol_processor_tail_sampling_dropped_traces_total` ticking up.

**Prevention:**
For the workshop, set `decision_wait: 10s` (shorter than the default 30s — orders complete in <500ms; 10s is generous). Set `num_traces: 10000` (right-sized for laptop-scale load). Expose `otelcol_processor_tail_sampling_*` metrics in the README as "the metrics you check when sampling looks wrong".

---

### F2-3: Head sampling + tail sampling double-filtering: effective rate = `head% × tail%`

**Severity:** HIGH — workshop invariant is "you see EVERY order trace"; if both samplers are active, you see far fewer

**What goes wrong:**
The SDK's `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` at the SDK level drops 90% of traces BEFORE they reach the Collector. The Collector's tail sampling processor then sees only the 10% that passed SDK-side. Tail sampling's probabilistic 10% policy then drops another 90% of those. Effective rate: 1%. The workshop produces 100 POSTs, you see ~1 trace in Tempo. Attendees ask "why aren't my traces showing up?"

The v1.0 `OtelSdkConfiguration` uses `Sampler.parentBased(Sampler.alwaysOn())` — safe default. The danger is that a v2.0 sampling lesson introduces `traceIdRatioBased` at the SDK level at the same time as the Collector tail sampling is introduced, without explaining the interaction.

**Warning signs:**
- After configuring both SDK ratio sampling AND Collector tail sampling, Tempo shows far fewer traces than the number of POST /orders requests sent.
- `scripts/load.sh` sends 60 requests, Tempo shows 2-5 traces.

**Prevention:**
Teach one layer at a time. For the tail sampling lesson:
- Keep the SDK sampler at `Sampler.parentBased(Sampler.alwaysOn())` — ALL traces reach the Collector.
- Configure tail sampling only at the Collector.
The SDK ratio sampling lesson (feature F7) should come BEFORE or AFTER the tail sampling lesson, never simultaneously, and must explicitly mention: "if you activate SDK ratio sampling AND tail sampling, the effective rate is the product of both."

---

## F3 — Exemplars: Prometheus → Tempo Click-Through

### F3-1: OTel Java SDK exemplars are `AlwaysOff` by default in manual instrumentation context — opt-in required

**Severity:** HIGH — the exemplar feature produces no visible output if not enabled; teams waste time looking for exemplars in Prometheus and find nothing

**What goes wrong:**
The OTel Java SDK's `ExemplarFilter` defaults to `ExemplarFilter.traceBased()` when a sampler is active (it records an exemplar for every span that is sampled). However, exemplars are only attached to histogram measurements when the histogram is built via an instrument that explicitly supports them, AND when the active span context at measurement time is valid. In the manual SDK setup (no autoconfigure), if `SdkMeterProvider.setExemplarFilter(ExemplarFilter.alwaysOff())` is never called, the SDK uses trace-based exemplars by default — but this only works if the `Span.current()` context is valid at the point the histogram is recorded.

The trap specific to this workshop: `http.server.request.duration` histogram is recorded inside `HttpServerSpanFilter` (a `HandlerInterceptor`). If that interceptor does not have an active span when it records the histogram observation (e.g., it runs AFTER the span is ended in a different filter), the exemplar context is `SpanContext.invalid()` and no exemplar is attached.

**Warning signs:**
- Prometheus scrape of the app's metrics endpoint shows histograms with no `{trace_id=...}` exemplar annotations.
- In Grafana, clicking a histogram data point does NOT offer a "Jump to trace" link.
- The Prometheus scrape is in Prometheus text format (not OpenMetrics format) — Prometheus drops exemplars that are not in OpenMetrics format.

**Prevention:**
Three steps must all be true simultaneously:
1. The OTel SDK `SdkMeterProvider` must have `setExemplarFilter(ExemplarFilter.traceBased())` (or `alwaysOn()`) set explicitly — the default is already `traceBased()` but making it explicit is pedagogically correct.
2. The histogram observation MUST happen while a valid span is active (`Span.current().getSpanContext().isValid()` must return true). In `HttpServerSpanFilter`, ensure the histogram record call happens BEFORE the span is ended.
3. The Collector must export metrics in **OpenMetrics format** to Prometheus/Mimir — the `prometheusremotewrite` exporter does this automatically; the plain `prometheus` exporter does not attach exemplars in scraped output unless `send_exemplars: true` is set.

---

### F3-2: Exemplar metadata size limit — Prometheus drops exemplars >128 bytes total

**Severity:** MEDIUM — easy to hit if you add multiple span attributes as exemplar labels

**What goes wrong:**
The OpenMetrics specification caps exemplar label set size at 128 bytes (total: key names + values + formatting). Prometheus enforces this: `exemplar label combined exceeds 128 bytes`. The OTel SDK attaches `trace_id` (32 hex chars) and `span_id` (16 hex chars) as the exemplar — those two fields sum to ~60 bytes including the label names. Safe. But if code adds additional span attributes (e.g., `order.id`, `customer.name`) to the exemplar, the total can exceed 128 bytes and Prometheus silently discards the entire exemplar.

**Warning signs:**
- Prometheus logs: `err="exemplar label combined exceeds 128 bytes"`.
- Grafana shows histogram data but no "Jump to trace" link.
- Prometheus has `prometheus_tsdb_exemplar_last_attach_error_total` counter incrementing.

**Prevention:**
Keep exemplar labels to `trace_id` + `span_id` only (the OTel SDK default). Do not add business attributes to exemplars. This is a teaching opportunity: explain that exemplars carry JUST enough context to find the trace, not business data (that goes in span attributes).

---

### F3-3: Grafana exemplar datalink uses `${__value.raw}` but the Tempo UID must match the new provisioned UID

**Severity:** HIGH — inherits from F1-1; the exemplar datalink configuration in Grafana (`exemplarTraceIdDestinations`) references a datasource UID

**What goes wrong:**
When the Prometheus/Mimir datasource in Grafana is provisioned, the `exemplarTraceIdDestinations` field in the datasource YAML tells Grafana: "when this metric has an exemplar with a `traceID` label, open it in datasource with UID X using the exemplar value as the trace ID." If UID X is wrong (e.g., still points to the old `otel-lgtm` bundled Tempo UID), clicking an exemplar opens an error page or opens the wrong datasource.

**Warning signs:**
- Histogram panel shows exemplar dots (Grafana renders them as diamond-shaped markers on the timeseries).
- Clicking an exemplar shows "Datasource not found" or lands on a blank Tempo search with no trace ID populated.

**Prevention:**
In the Prometheus/Mimir datasource YAML provisioning:
```yaml
jsonData:
  exemplarTraceIdDestinations:
    - name: traceID
      datasourceUid: <EXACT UID of your Tempo datasource>
      urlDisplayLabel: Open in Tempo
```
Verify the UID by checking the Tempo datasource YAML. The `${__value.raw}` template variable is NOT used here — Grafana injects the exemplar trace ID automatically when `datasourceUid` matches a Tempo datasource. The `${__value.raw}` variable is used in manual datalink panel overrides, not in datasource-level exemplar routing.

---

## F4 — Loki Recording Rules and Log-Derived Metrics

### F4-1: Loki recording rule metric names collide with OTel SDK-emitted metric names in Mimir

**Severity:** HIGH — you end up with two time series named identically, sourced from different systems; Grafana queries return ambiguous or merged results

**What goes wrong:**
The OTel SDK emits `orders.created` (a Counter) and `http.server.request.duration` (a Histogram) to Mimir via the Collector. If a Loki recording rule is defined with `metric_name: orders_created` or `metric_name: http_server_request_duration_seconds`, the resulting metric in Mimir has the same name but different labels (the recording rule lacks `service.name`, `otel.scope.name`, etc.). Grafana PromQL queries that aggregate across both series return incorrect counts.

**Warning signs:**
- A PromQL query like `sum(rate(orders_created_total[5m]))` returns double the expected value.
- Mimir shows two series with the same metric name but different label cardinalities.
- Grafana panel shows a sawtooth / doubled spike that doesn't match actual order volume.

**Prevention:**
Follow the Grafana-recommended recording rule naming convention: `log:<metric>:<aggregation>`. For example:
- `log:order_errors:rate1m` (NOT `orders_errors_total` which mirrors the SDK metric name).
- `log:http_5xx_count:sum` (NOT `http_server_request_duration_seconds` which mirrors the OTel semconv name).

Document this naming convention in the README's "Log-based metrics" section so attendees do not inadvertently collide with their own SDK metrics.

---

### F4-2: Loki `rate()` window vs ruler evaluation interval — selecting a window shorter than `2 × interval` produces zero-valued metrics

**Severity:** MEDIUM — produces metrics that look correct (no errors) but always return 0

**What goes wrong:**
Loki recording rules run at the `evaluation_interval` (default: 1m). A LogQL `rate()` function requires a range selector (e.g., `rate({service_name="order-producer"}[1m])`). If the range window (`[1m]`) is equal to the evaluation interval (`1m`), there is a statistical aliasing problem: the ruler evaluates at exactly T=1m, T=2m, etc., but the `rate()` window `[1m]` from T=1m looks at [T-60s, T]. If no logs landed in that exact window (because they landed at T-61s), the rate is 0. Using a window of `[2m]` or `[5m]` provides stable coverage.

**Warning signs:**
- Loki recording rule metric is always 0 even when the app is generating logs.
- Metric occasionally shows spikes of the correct value, then drops to 0 for several intervals.
- Querying the same LogQL directly in Grafana Explore returns data, but the recording rule metric doesn't.

**Prevention:**
Set the LogQL range window to at least `2 × evaluation_interval`. For an eval interval of 1m, use `[2m]`. Document: "recording rule range windows should be 2× the evaluation interval to avoid aliasing."

---

### F4-3: Loki ruler requires explicit `ruler_storage` configuration and does NOT auto-load rules from a volume mount

**Severity:** MEDIUM — teams mount a `rules/` directory into the Loki container and wonder why rules are not evaluated

**What goes wrong:**
Unlike Prometheus (which watches its `--rule-files` glob and hot-reloads on `SIGHUP`), Loki's ruler by default uses object store–backed rule storage (S3/GCS/local filesystem via `ruler_storage`). When running in Docker Compose with a `local` filesystem backend, rules are stored in the path defined by `ruler_storage.local.directory`. If that path is different from the bind-mount where you put your YAML rule files, Loki ignores them.

Additionally, Loki does NOT hot-reload ruler config without a `SIGHUP` or container restart. Changes to rule files in the local directory are not picked up automatically.

**Warning signs:**
- Rules YAML is mounted at `/etc/loki/rules/` but `ruler_storage.local.directory` is set to `/loki/rules/` — rules never evaluate.
- Ruler API endpoint (`GET /loki/api/v1/rules`) returns empty even though rule files are present on disk.

**Prevention:**
Align the volume mount path with `ruler_storage.local.directory` in `loki-config.yaml`. Explicitly set:
```yaml
ruler_storage:
  backend: local
  local:
    directory: /etc/loki/rules
```
And mount: `./grafana/loki/rules:/etc/loki/rules:ro`. After changing rules, trigger a reload via `docker compose kill -s HUP loki`.

---

### F4-4: High-cardinality label from log attributes used in recording rules — Mimir cardinality bomb

**Severity:** HIGH — cardinality issues are silent (Mimir accepts the writes) until the series limit is hit, at which point new series are silently dropped

**What goes wrong:**
Loki can parse structured log fields (e.g., `orderId`, `customerId`, `spanId`) as labels in LogQL queries. If a recording rule groups by `orderId` or `spanId`, each unique value creates a new Mimir time series. A workshop with 1000 orders generates 1000 series for a single metric — benign at workshop scale but a canonical production anti-pattern that attendees should NOT take home.

**Warning signs:**
- A recording rule uses `by (order_id)` or `by (span_id)` in LogQL.
- Mimir metric `cortex_ingester_active_series_gauge` grows without bound during a load test.
- Mimir logs: `err=rpc error: code = ResourceExhausted desc = per-user series limit`.

**Prevention:**
Recording rules should ONLY group by low-cardinality labels: `service_name`, `level`, `http.method`, `http.status_code`. Never group by trace IDs, span IDs, request IDs, or user IDs. Teach this as an explicit anti-pattern in the README.

---

## F5 — JDBC/JPA Database Spans

### F5-1: Hibernate N+1 queries → N+1 spans → Tempo cardinality explosion

**Severity:** HIGH — at workshop load levels, one page load triggers 50–100 database spans; Tempo index grows unboundedly; attendees see a trace with 50 `SELECT` child spans and think "OTel is broken"

**What goes wrong:**
Manual JDBC/JPA span instrumentation that wraps every `EntityManager.find()` or JDBC `statement.execute()` naively creates one span per SQL call. Hibernate's lazy loading produces N+1 `SELECT` statements per parent entity: fetch `ProcessedOrder` list → Hibernate fires `SELECT * FROM processed_orders` (1 query) + N `SELECT * FROM order_items WHERE order_id=?` (N queries). Each has its own span. The resulting trace in Tempo has 1+N child spans, which looks alarming.

**Warning signs:**
- A single `POST /orders` → consumer processing produces a trace with dozens of `db.query.text` spans.
- Tempo "Trace Detail" view shows >20 spans for what should be a simple insert.
- Grafana "Orders.created" counter doesn't match the number of DB INSERT spans (expected 1:1 ratio).

**Prevention:**
The demo already uses HikariCP and Spring Data JPA. Use `@EntityGraph` or `JOIN FETCH` to avoid N+1 before adding span instrumentation. Apply instrumentation at the repository method level (span wraps the entire repository call), NOT at the JDBC connection level (which wraps each SQL statement). The span description should be `"save ProcessedOrder"`, not `"execute SELECT..."`. This is a teaching point: instrument at the right granularity.

---

### F5-2: `db.statement` is the old name; the stable semconv attribute is `db.query.text` — using the wrong constant produces stale attribute names

**Severity:** MEDIUM — the v1.0 codebase uses `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` which includes `DbAttributes.DB_QUERY_TEXT` (stable); using string literal `"db.statement"` from memory or older tutorials is the trap

**What goes wrong:**
Pre-semconv-1.30 tutorials and many Stack Overflow answers use `"db.statement"` as the attribute key. The current stable semconv (1.40.0) defines `DbAttributes.DB_QUERY_TEXT` (attribute name: `db.query.text`). Using the old string literal means the span attribute appears under the wrong key in Tempo's span attribute search — attendees can't find it with `db.query.text=...` search.

The `OTEL_SEMCONV_STABILITY_OPT_IN=database` env-var controls which schema auto-instrumentation emits; manual instrumentation must use the correct constant directly.

**Warning signs:**
- Tempo span detail shows `db.statement: SELECT ...` instead of `db.query.text: SELECT ...`.
- Searching Tempo with `db.query.text=*` returns no results despite DB spans being present.

**Prevention:**
Use `DbAttributes.DB_QUERY_TEXT` from `io.opentelemetry.semconv.DbAttributes` (stable, semconv 1.40.0). Also use `DbSystemValues.POSTGRESQL` from `io.opentelemetry.semconv.DbSystemValues`. The v1.0 `CLAUDE.md` explicitly calls out the semconv coordinate — apply the same discipline for `db.*` attributes.

---

### F5-3: `@Transactional` + manual span aspect ordering — span must WRAP the transaction, not be wrapped by it

**Severity:** HIGH — if the span ends INSIDE the transaction, it does not capture the transaction commit/rollback in its duration; if the transaction rolls back AFTER the span ends, the span's status is "OK" but data was not persisted

**What goes wrong:**
Spring AOP applies advice in an ordered chain. If the manual span `@Around` advice has lower order priority than `@Transactional`'s proxy advice, execution is:
```
enter transaction → enter span → business logic → end span → commit/rollback transaction
```
The span shows "OK" even when the transaction rolls back. The teaching point — "the span should cover the full unit of work including commit latency" — is violated.

**Warning signs:**
- A transaction that rolls back (due to `ProcessingFailedException`) produces a span with `status=OK`.
- The span's duration does not include the commit time visible in HikariCP connection wait metrics.
- `@Order(Ordered.LOWEST_PRECEDENCE)` on the span aspect places it as the innermost, not outermost.

**Prevention:**
Apply `@Order(Ordered.HIGHEST_PRECEDENCE)` to the custom span aspect, ensuring it runs OUTER to `@Transactional`. Alternatively, since the v1.0 workshop uses explicit `tracer.spanBuilder()` calls in the method body (not aspects), wrap the transaction boundary by placing the span start BEFORE `@Transactional` entry. The simplest workshop pattern: place the span in the `OrderListener.onOrder()` method body (before the `@Transactional` `ProcessingService.process()` call) rather than inside `ProcessingService` itself.

---

### F5-4: HikariCP connection acquisition time not captured — the "wait for connection" latency is invisible

**Severity:** MEDIUM — in a workshop, connection pool exhaustion (easy to trigger under load) produces a `SQLTimeoutException` with no trace evidence of WHERE the time was spent

**What goes wrong:**
Manual JDBC span instrumentation typically starts the span AFTER `dataSource.getConnection()` returns. If the HikariCP pool is exhausted (all connections in use), `getConnection()` blocks for up to `connectionTimeout` (default: 30s). This blocking wait is not captured by the span, so Tempo shows a slow DB span but the span's start time is after the wait — the wait is invisible.

The v1.0 `OtelSdkConfiguration` already has a `HikariCpConnectionGauge` for connection pool depth, which surfaces saturation. But a metric showing "pool is saturated" without a trace event showing "this specific request waited 29s for a connection" is incomplete.

**Warning signs:**
- Under load, `POST /orders` returns 500 (HikariCP timeout) but the trace shows no DB spans (the span never started because `getConnection()` threw).
- Load test latency histogram shows a bimodal distribution (fast p50, very slow p99) that is not explained by any span.

**Prevention:**
For the workshop, add a note in the README: "HikariCP connection wait time is not captured in DB spans in this demo — the `db.client.connection.count` gauge shows saturation; in production, use a DataSource proxy (e.g., `p6spy` or Spring Boot Actuator's JDBC instrumentation) to capture wait time." This is a teaching callout, not a feature to implement in the demo.

---

## F6 — Outbound HTTP-Client Spans (RestClient)

### F6-1: `RestClient` built with `new RestClient.create()` bypasses `RestClient.Builder` injection — no interceptor, no span

**Severity:** HIGH — context is not propagated; downstream service receives no `traceparent` header; outbound span is never created

**What goes wrong:**
Spring Boot auto-configures the `RestClient.Builder` bean with a `ClientHttpRequestInterceptor` chain that includes any interceptors registered by the developer. If the developer creates a `RestClient` directly (via `RestClient.create()` or `RestClient.create("http://...")`) instead of using the auto-configured `RestClient.Builder`, the custom interceptors (including any OTel propagation interceptor) are not present.

**Warning signs:**
- The outbound HTTP call succeeds but the downstream service shows no parent span / independent trace.
- Tempo shows the producer span with no outbound child span of kind `CLIENT`.
- The downstream service's access log shows requests WITHOUT a `traceparent` header.

**Prevention:**
Always inject `RestClient.Builder` (the Spring-managed bean) and call `.build()` on it:
```java
@Bean
RestClient restClient(RestClient.Builder builder) {
    return builder.baseUrl(externalServiceUrl).build();
}
```
Do NOT use `RestClient.create(...)` directly. In the workshop, this is a teaching point: "always inject the builder, never build from scratch." Add a `// WRONG: RestClient.create(url)` comment showing the anti-pattern before the correct code.

---

### F6-2: Outbound span must be created BEFORE the interceptor injects the `traceparent` header — span context must be active when injection runs

**Severity:** HIGH — if the span is created AFTER injection, the outgoing `traceparent` carries the PARENT's trace ID, not the new child span's span ID

**What goes wrong:**
Correct pattern:
```
1. Start CLIENT span (new SpanContext with new spanId)
2. Make span current (Scope)
3. Inject context (propagator writes NEW spanId into traceparent header)
4. Execute HTTP request
5. End span
```

Incorrect pattern (common mistake):
```
1. Inject context (propagator writes PARENT spanId into traceparent header)
2. Start CLIENT span
3. Execute HTTP request
4. End span
```

In the incorrect pattern, the downstream service receives a `traceparent` pointing to the PARENT span's spanId as the parent, not the new CLIENT span. Tempo shows the downstream service as a child of the upstream service's root span, not as a child of the HTTP CLIENT span. The CLIENT span appears as a sibling of the downstream work, not its parent.

**Warning signs:**
- In Tempo trace detail, the outbound service appears as a SIBLING of the HTTP client span, not a child.
- The downstream service's span has a `parentSpanId` that points to the upstream service root span, not to the HTTP CLIENT span created for the outbound call.

**Prevention:**
Pattern: start span first, make current, THEN inject. If using a `ClientHttpRequestInterceptor`, the interceptor runs INSIDE the request execution — start the span BEFORE calling `restClient.get().retrieve()`, wrap the entire call in `try (Scope s = span.makeCurrent())`, then inside the interceptor use `Context.current()` (which now contains the new span).

---

### F6-3: `ClientHttpRequestInterceptor` order matters — OTel propagation interceptor must run LAST so `traceparent` is the final header set

**Severity:** MEDIUM — if another interceptor modifies headers AFTER the OTel interceptor, it may overwrite `traceparent` with a blank value or a wrong value

**What goes wrong:**
`RestClient.Builder.requestInterceptors()` is an ordered list. An authorization interceptor that runs AFTER the OTel propagation interceptor might call `headers.remove("traceparent")` (as part of a security sanitization step) or might re-build the entire headers object, discarding OTel's injection.

**Warning signs:**
- OTel interceptor is registered; spans are created; but downstream service sees no `traceparent` header.
- Enabling gRPC debug logging on the client shows `traceparent` absent from the outgoing request.

**Prevention:**
Register the OTel propagation interceptor LAST in the `RequestInterceptors` list. In the workshop, there is likely only one interceptor (the OTel one), so this pitfall is latent rather than immediate. Mention it in the README as a production gotcha: "if you have an auth interceptor, ensure the OTel interceptor is registered after it."

---

### F6-4: Missing `http.response.status_code` on exception path — exception thrown from interceptor skips status attribute

**Severity:** MEDIUM — span shows `status=ERROR` but no HTTP status code attribute, making it hard to distinguish 4xx from 5xx in Tempo attribute search

**What goes wrong:**
When `restClient.get().retrieve().body(...)` throws `HttpClientErrorException` (4xx) or `HttpServerErrorException` (5xx), the exception propagates before the interceptor's "after" code runs. A naive interceptor that only sets `http.response.status_code` in the success path leaves the attribute missing on error paths.

**Warning signs:**
- Tempo span has `status=ERROR` but no `http.response.status_code` or `http.status_code` attribute.
- Searching Tempo for `http.response.status_code=500` returns fewer spans than expected.

**Prevention:**
In the interceptor's `ClientHttpResponse response = execution.execute(request, body)` call, wrap in try-catch. In the catch block for `HttpStatusCodeException`:
```java
span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, e.getStatusCode().value());
span.setStatus(StatusCode.ERROR, e.getMessage());
span.recordException(e);
```
Then always call `span.end()` in a finally block.

---

## F7 — Sampling and Baggage

### F7-1: `OTEL_TRACES_SAMPLER=traceidratio` silently breaks the "see EVERY trace" workshop invariant if set via env var

**Severity:** HIGH — workshop invariant is 100% trace visibility; if an attendee (or mise task) sets this env var, all later workshop steps appear "broken"

**What goes wrong:**
The `OtelSdkConfiguration` sets `Sampler.parentBased(Sampler.alwaysOn())` programmatically. However, if `OTEL_TRACES_SAMPLER=parentbased_traceidratio` is exported in the shell (or added to `mise.toml` as part of the sampling lesson), it overrides the programmatic sampler IF the `opentelemetry-sdk-extension-autoconfigure` artifact is on the classpath. The demo deliberately does NOT include autoconfigure (CLAUDE.md constraint: "no autoconfigure dependency") so this should be safe — but the README must explicitly warn: "do NOT export `OTEL_TRACES_SAMPLER` unless you understand the interaction with the programmatic sampler."

**Warning signs:**
- After the sampling + baggage lesson, previously-working steps (Phase 2, 3, 5) appear "broken" with far fewer traces.
- Tempo shows traces arriving at a 10% rate even though the SDK config still says `alwaysOn`.
- `mise run dev:producer` env var output shows `OTEL_TRACES_SAMPLER=parentbased_traceidratio`.

**Prevention:**
The sampling lesson should demonstrate ratio sampling by CHANGING the `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` line in `OtelSdkConfiguration` directly — NOT by env var injection. This way the lesson is self-contained in code, and reverting is a one-line code change rather than a shell environment hunt. Annotate the change with `// WORKSHOP: change back to alwaysOn() after this lesson`.

---

### F7-2: `TracingMessagePostProcessor` (v1.0) propagates `traceparent` but NOT baggage — attendees discover this gap mid-lesson

**Severity:** MEDIUM — the v1.0 propagation pair uses `W3CTraceContextPropagator` via the composite propagator; `W3CBaggagePropagator` is also registered in the composite propagator; the AMQP inject/extract pair should therefore propagate baggage automatically — but only if the Baggage is attached to the Context, not just stored locally

**What goes wrong:**
`W3CBaggagePropagator` writes baggage as the `baggage` header. In v1.0's `TracingMessagePostProcessor.postProcessMessage()`, the code calls `propagator.inject(Context.current(), messageProperties, setter)`. If `Context.current()` carries a Baggage entry (set via `Baggage.builder().put("customerId", ...).build().storeInContext(Context.current()).makeCurrent()`), the baggage IS injected as the `baggage` AMQP header. The consumer's `TracingMessageListenerAdvice` calls `propagator.extract(Context.root(), messageProperties, getter)` and returns the extracted context — which includes both the trace context AND the baggage.

The gap: `Baggage.current()` on the consumer thread only works if the extracted context (including baggage) was made current. If the advice stops at `setParent(extractedContext)` on the span builder but does NOT call `extractedContext.makeCurrent()` for the duration of the consumer business logic, `Baggage.current()` returns empty and attendees think "baggage doesn't work across AMQP."

**Warning signs:**
- `Baggage.current().getEntryValue("customerId")` returns `null` inside `ProcessingService.process()`.
- Span attribute manually extracted via `Baggage.current()` is empty even though the header is present in the message.

**Prevention:**
In `TracingMessageListenerAdvice`, the extracted context must be made current for the ENTIRE listener execution, not just used to parent the consumer span:
```java
Context extracted = propagator.extract(Context.root(), messageProperties, getter);
try (Scope s = extracted.makeCurrent()) {  // <-- makes baggage accessible via Baggage.current()
    Span span = tracer.spanBuilder("consume order").setSpanKind(CONSUMER)
        .setParent(Context.current())  // picks up baggage-bearing context
        .startSpan();
    try (Scope s2 = span.makeCurrent()) {
        // Baggage.current() is now valid here
    } finally { span.end(); }
}
```

---

### F7-3: Baggage as span attribute — cardinality bomb if promoted automatically

**Severity:** MEDIUM — workshop may demonstrate `Baggage.current().asMap().forEach((key, entry) -> span.setAttribute(key, entry.getValue()))` as a "how to read baggage" example; if this pattern is used with user-controlled baggage keys, it becomes a Tempo index cardinality problem

**What goes wrong:**
Promoting ALL baggage entries as span attributes is a convenient "log everything" shortcut. In the workshop, baggage keys are fixed (`customerId`, `region`). In production, if attendees copy this pattern and the upstream service puts user-supplied data in baggage (e.g., a session token, a correlation ID), the number of unique attribute values explodes and Tempo's index grows unboundedly.

**Warning signs:**
- Span has many attributes with keys starting with `baggage.*` or matching baggage names.
- Tempo index grows proportionally to unique user IDs.

**Prevention:**
Demonstrate baggage reading with ONLY named keys: `Baggage.current().getEntryValue("customerId")`. Do NOT iterate all baggage entries into span attributes. README callout: "always read specific baggage keys; never promote all baggage to span attributes in production."

---

### F7-4: `Context.makeCurrent()` try-with-resources forgetting `.close()` — silent context leak

**Severity:** HIGH for correctness; MEDIUM for the workshop (laptop JVM; no long-term context leak consequences)

**What goes wrong:**
`Context.makeCurrent()` returns a `Scope` that implements `AutoCloseable`. If the `Scope` is not closed (i.e., `try-with-resources` is not used OR the close is in a code path that is not reached due to an exception), the `Context` ThreadLocal is not restored. Subsequent spans on the same thread inherit the leaked context (wrong parent, wrong trace ID). In server-side apps, this means every request processed by that thread after the leak has an incorrect trace.

**Warning signs:**
- A span appears as a child of a long-finished span (incorrect parent).
- Trace in Tempo shows a span with `parentSpanId` pointing to a span that already ended seconds ago.
- `Span.current()` inside an HTTP handler returns a span started in a different request (visible via `spanId` mismatch in logs).

**Prevention:**
Always use try-with-resources for `Scope`:
```java
try (Scope s = span.makeCurrent()) {
    // business logic
} // Scope.close() restores the prior context even on exception
```
Never use:
```java
Scope s = span.makeCurrent();
// forgot s.close()
```
Code review checklist: every `span.makeCurrent()` call must have a corresponding `close()` in a `finally` block OR must use try-with-resources. This is a non-negotiable pattern in the workshop README.

---

## F8 — AMQP Fanout / Topic / DLX Variants

### F8-1: Fanout span model — consumers should be LINKED to the producer span, not CHILDREN; one message = N consumer traces

**Severity:** MEDIUM — the v1.0 demo correctly models direct-exchange (one producer → one consumer → parent/child relationship is acceptable); fanout changes the semantics and produces visually confusing traces if parent/child is used

**What goes wrong:**
With a fanout exchange, one producer publishes one message → N queues → N consumers. Each consumer starts its own trace. If each consumer uses `setParent(extractedContext)` (the v1.0 pattern), Tempo shows N consumer spans all as children of the single producer span. This looks correct for N=2 in the workshop, but it violates OTel messaging semconv which says: "spans can only have a single parent; for fan-out, use span links instead of parent-child relationships."

Using `setParent()` for fanout makes the producer span appear to have N consumer children — which is fine visually but architecturally wrong: the producer did not "call" each consumer. The producer published a message; each consumer independently consumed it.

**Warning signs:**
- The trace in Tempo shows the producer span with multiple children all having `span.kind=CONSUMER`.
- The teaching explanation is "each consumer is a child of the producer" — which is wrong per semconv.

**Prevention:**
For fanout consumers, use span links:
```java
Span consumerSpan = tracer.spanBuilder("consume order.fanout")
    .setSpanKind(SpanKind.CONSUMER)
    .addLink(extractedContext.get(SpanContextKey.KEY))  // link to producer span
    // do NOT call .setParent(extractedContext)
    .startSpan();
```
Tempo renders links as separate traces with a "linked from" indicator. This matches the OTel spec and teaches the correct production pattern. The tradeoff: linked traces are harder to navigate in Tempo than parent/child chains — mention this UX tradeoff in the README.

---

### F8-2: DLX republish with original `traceparent` header creates an infinite trace if the DLX consumer re-publishes to the original exchange

**Severity:** HIGH — produces an infinite trace loop in Tempo; storage and index grow without bound; only observable by watching Tempo disk usage or span count climbing

**What goes wrong:**
When a message is dead-lettered (rejected without requeue), RabbitMQ copies the message headers (including `traceparent`) to the dead-letter queue. If the DLX consumer (a retry or notification handler) republishes the message to the original exchange WITHOUT resetting the trace context, the new publish carries the original `traceparent` header. The consumer on the original exchange extracts this context and creates a new span as a child of the original producer span. If this cycle repeats (DLX → republish → DLX → republish), every cycle adds spans to the same original trace. Eventually Tempo's span-per-trace limit is hit or the trace grows impractically large.

**Warning signs:**
- A "failed" order trace in Tempo grows by 2 spans every few seconds after the failure.
- Tempo trace detail shows consumer spans with incrementing `retry_count` attributes but all under the same trace ID.
- `otelcol_exporter_sent_spans_total` climbs continuously even when no new user requests are being made.

**Prevention:**
On DLX republish, reset the trace context. Pattern:
```java
// When republishing a dead-lettered message to the original exchange:
// 1. Remove the traceparent and tracestate headers from message properties
messageProperties.getHeaders().remove("traceparent");
messageProperties.getHeaders().remove("tracestate");
// 2. Start a NEW root span for the retry attempt
Span retrySpan = tracer.spanBuilder("retry order.processing")
    .setSpanKind(SpanKind.PRODUCER)
    .setNoParent()  // explicitly start a new trace
    .startSpan();
// 3. Add a link to the original trace for correlation
.addLink(originalSpanContext)  // link to the failed trace
// 4. Inject the NEW context into the republished message
```
This creates a new trace for the retry attempt, linked to the original failed trace — fully navigable in Tempo without loops.

---

### F8-3: Topic exchange routing key as span attribute — high cardinality if routing keys are user-controlled

**Severity:** MEDIUM — follows the same pattern as F7-3 (baggage-as-attribute)

**What goes wrong:**
OTel messaging semconv defines `messaging.destination.routing_key` (or equivalent) as a span attribute. For a topic exchange, routing keys like `orders.created.us-east-1.premium` are fine — low cardinality, bounded set. But if routing keys include order IDs, user IDs, or session tokens (e.g., `orders.created.<orderId>`), each unique value creates a new Tempo span attribute index entry. At workshop scale (1000 orders), this is 1000 unique `messaging.destination.routing_key` values.

**Warning signs:**
- Routing key attribute includes a UUID or numeric ID.
- `scripts/load.sh` output creates 100 spans all with unique routing keys.
- Tempo attribute search on `messaging.destination.routing_key` returns more values than expected.

**Prevention:**
Normalize routing keys before using as span attributes. Capture the routing KEY PATTERN (e.g., `orders.created.#`) rather than the specific key with a resolved value. Or: record only the exchange name and operation type as span attributes; omit the specific routing key. Add a README note: "topic routing keys must be bounded-cardinality patterns, not per-message values."

---

## Integration Gotchas (v2.0-specific)

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| otel-lgtm decompose → Grafana | New datasource UIDs break existing dashboard panels | Reuse old UIDs from otel-lgtm in new provisioning YAML |
| Tempo metrics_generator → Mimir | `remote_write` points to `localhost` from inside Tempo container | Use Docker service name: `http://mimir:9009/api/v1/push` |
| Mimir single-binary → OTel Collector | Mimir rejects all writes with 401 (multitenancy on by default) | Start with `--auth.multitenancy-enabled=false` |
| Tail sampling + head sampling | Double filtering produces `head% × tail%` effective rate | Keep SDK sampler at `alwaysOn` for the tail sampling lesson |
| Exemplar in histogram → Grafana | `ExemplarFilter` default may not fire if no active span at measurement time | Ensure histogram observation occurs inside an active span scope |
| Loki recording rules → Mimir | Metric names clash with OTel SDK metric names | Prefix recording rule metrics with `log:` |
| DLX republish → trace loop | Original `traceparent` preserved → same trace grows forever | Remove `traceparent`/`tracestate` headers before republishing; use `setNoParent()` |
| RestClient | `RestClient.create(url)` bypasses builder interceptors | Always inject `RestClient.Builder` bean |
| JDBC span | Wrapping at SQL-statement level produces N+1 spans for Hibernate lazy loading | Wrap at repository method level, not SQL execution level |
| `@Transactional` + span aspect | Span ends before commit — wrong status on rollback | `@Order(HIGHEST_PRECEDENCE)` on span aspect so it wraps the transaction proxy |
| Baggage extraction in AMQP listener | `Baggage.current()` empty in consumer business logic | Call `extractedContext.makeCurrent()` for the entire listener scope, not just the span builder |
| Fanout consumers | `setParent(extractedContext)` makes all consumers children of producer span | Use span links for fan-out; reserve parent-child for direct/work-queue |
| `Context.makeCurrent()` | `Scope` not closed → ThreadLocal context leak | ALWAYS use try-with-resources; enforce via code review checklist |

---

## Phase-Specific Warning Summary

| v2.0 Feature / Phase | Likely Pitfall (ID) | Severity | Mitigation |
|----------------------|---------------------|----------|------------|
| F1: Decompose otel-lgtm | F1-1 (UID breakage), F1-5 (port collision) | HIGH | Reuse UIDs; remove `lgtm` service atomically |
| F1: Decompose otel-lgtm | F1-2 (Tempo WAL), F1-3 (Mimir auth) | HIGH | Named volume for WAL; `--auth.multitenancy-enabled=false` |
| F1: Decompose otel-lgtm | F1-4 (Collector naming) | MEDIUM | Verify component names via `otelcol components` |
| F2: Tail sampling | F2-1 (policy evaluation model) | HIGH | Document priority chain; use explicit `drop` for exclusions |
| F2: Tail sampling | F2-3 (head+tail double filter) | HIGH | Keep SDK at `alwaysOn` during tail sampling lesson |
| F2: Tail sampling | F2-2 (decision_wait / buffer) | MEDIUM | Set `decision_wait: 10s`, `num_traces: 10000` |
| F3: Exemplars | F3-1 (exemplar filter), F3-3 (UID/datalink) | HIGH | Explicit `ExemplarFilter`; verify datasource UID in provisioning |
| F3: Exemplars | F3-2 (128-byte limit) | MEDIUM | Only include `trace_id` + `span_id` in exemplar labels |
| F4: Loki recording rules | F4-1 (metric name collision) | HIGH | Prefix with `log:` naming convention |
| F4: Loki recording rules | F4-4 (cardinality bomb) | HIGH | Only group by low-cardinality labels |
| F4: Loki recording rules | F4-2 (rate window aliasing), F4-3 (ruler storage path) | MEDIUM | Window = 2× interval; align volume mount and `ruler_storage.local.directory` |
| F5: JDBC/JPA spans | F5-1 (N+1), F5-3 (tx/span ordering) | HIGH | Wrap at repository level; `@Order(HIGHEST_PRECEDENCE)` on span aspect |
| F5: JDBC/JPA spans | F5-2 (db.statement old name) | MEDIUM | Use `DbAttributes.DB_QUERY_TEXT` constant |
| F5: JDBC/JPA spans | F5-4 (HikariCP wait invisible) | MEDIUM | Document as a known teaching gap in README |
| F6: HTTP-client spans | F6-1 (create vs builder), F6-2 (injection order) | HIGH | Inject `RestClient.Builder`; start span then inject headers |
| F6: HTTP-client spans | F6-3 (interceptor order), F6-4 (error status code) | MEDIUM | OTel interceptor last; set `http.response.status_code` in catch block |
| F7: Sampling + baggage | F7-1 (env var override), F7-4 (scope leak) | HIGH | Demo ratio sampling in code, not env var; enforce try-with-resources |
| F7: Sampling + baggage | F7-2 (baggage extraction scope), F7-3 (cardinality) | MEDIUM | `extractedContext.makeCurrent()` for entire listener; read specific keys only |
| F8: AMQP fanout/topic/DLX | F8-2 (DLX trace loop) | HIGH | Remove `traceparent`/`tracestate` before republish; use `setNoParent()` + link |
| F8: AMQP fanout/topic/DLX | F8-1 (links vs children), F8-3 (routing key cardinality) | MEDIUM | Span links for fanout; normalize routing keys before use as attributes |
| All phases | X-1 (circular ref), X-2 (SDK version), X-3 (image tags), X-4 (test isolation) | HIGH/MEDIUM | Fix circular ref in OtelSdkConfiguration before any new edits; pin BOMs and images |

---

## Sources

### Official Documentation

- [OTel tail sampling processor README — policy evaluation model, `decision_wait` defaults, `num_traces` buffer](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md)
- [OTel messaging semantic conventions — span links for fanout, producer/consumer span kinds](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/)
- [OTel database semantic conventions — `db.query.text` stable name, `OTEL_SEMCONV_STABILITY_OPT_IN` opt-in](https://opentelemetry.io/docs/specs/semconv/db/database-spans/)
- [OTel sampling concepts — head vs tail, double-filtering interaction](https://opentelemetry.io/docs/concepts/sampling/)
- [OTel Java SDK `SdkMeterProvider.setExemplarFilter` API](https://opentelemetry.io/docs/languages/java/sdk/)
- [OTel Java SDK CHANGELOG — breaking changes in 1.60.0 and 1.61.0](https://raw.githubusercontent.com/open-telemetry/opentelemetry-java/main/CHANGELOG.md)
- [Grafana Tempo Docker Compose local example](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/deploy/locally/docker-compose/)
- [Grafana Tempo metrics_generator community issue — remote_write to Prometheus](https://community.grafana.com/t/tempo-metrics-generator-not-writing-to-prometheus/109895)
- [Grafana Mimir — configure OTel Collector](https://grafana.com/docs/mimir/latest/configure/configure-otel-collector/)
- [Grafana Mimir authentication and authorization — `X-Scope-OrgID`](https://grafana.com/docs/mimir/latest/manage/secure/authentication-and-authorization/)
- [Grafana Loki recording rules — ruler storage, evaluation interval](https://grafana.com/docs/loki/latest/operations/recording-rules/)
- [Grafana Loki alerting rules configuration](https://grafana.com/docs/loki/latest/alert/)
- [Grafana Tempo datasource provisioning — `exemplarTraceIdDestinations`](https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/provision/)
- [Prometheus exemplar storage — size limits (128 bytes)](https://prometheus.io/docs/prometheus/latest/feature_flags/#exemplars-storage)
- [OTel issue: tail-based sampling first-match mechanism discussion](https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/36795)
- [OTel issue: Reactive Spring-Boot context not propagating correctly](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4982)

### Community and Practitioner Sources

- [Control Theory — Tail-Based Sampling with OTel Collector (explains policy evaluation model clearly)](https://www.controltheory.com/resources/tail-sampling-with-the-otel-collector/)
- [Loki high-cardinality label issue (GitHub #91)](https://github.com/grafana/loki/issues/91)
- [Spring Framework issue: `@Bean` method in `@Configuration` `@PostConstruct` → circular reference](https://github.com/spring-projects/spring-framework/issues/27876)
- [Spring AMQP issue #1731: message header `ByteArrayLongString` type](https://github.com/spring-projects/spring-amqp/issues/1731)
- [Grafana docker-otel-lgtm issue #149: Tempo/Prometheus/Loki datasource linking](https://github.com/grafana/docker-otel-lgtm/issues/149)
- [OTel Java instrumentation discussion #10689: RestClient instrumentation in Spring Web](https://github.com/open-telemetry/opentelemetry-java-instrumentation/discussions/10689)

---
*Pitfalls research for: v2.0 Production Shapes milestone — adding production pipeline + expanded SDK instrumentation to Spring Boot 3.4.13 + manual OTel SDK 1.61.0*
*Researched: 2026-05-02*
*Extends: .planning/milestones/v1.0-research/PITFALLS.md*
