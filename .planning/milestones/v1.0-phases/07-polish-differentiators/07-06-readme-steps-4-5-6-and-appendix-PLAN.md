---
phase: 07-polish-differentiators
plan: 06
type: execute
wave: 5
depends_on: [07-04, 07-05]
files_modified:
  - README.md
autonomous: true
requirements: [DOC-01]
risk: high
tags: [readme, walkthrough, doc-01, refactor, appendix]

must_haves:
  truths:
    - "Steps 4/5/6 are rewritten to fit the lean 5-section template (What you'll learn / Checkpoint / Run / What to look for / Why it matters)"
    - "Every Phase 4/5/6 pedagogical invariant from CONTEXT.md D-07 is preserved verbatim or as a Concepts & FAQ cross-reference"
    - "The four standalone narrative sections become a single ## Concepts & FAQ appendix at the bottom"
    - "Concepts & FAQ retains all four sub-entries: Reading the code / Why is OtelSdkConfiguration.java duplicated? / Why is the propagation pair shared? / What's NOT here yet"
    - "README ends with the D-09 final paragraph: 'Workshop is at main HEAD past step-06-tests; ...'"
    - "DOC-04 step-04/05/06 PNG embeds present in each step's What to look for"
  artifacts:
    - path: "README.md"
      provides: "Steps 4/5/6 rewritten + Concepts & FAQ appendix + D-09 final paragraph"
      contains: "## Concepts & FAQ"
  key_links:
    - from: "Step 4 What to look for"
      to: "docs/screenshots/step-04-metrics.png"
      via: "Markdown image embed"
      pattern: "step-04-metrics.png"
    - from: "Step 5 What to look for"
      to: "docs/screenshots/step-05-logs-trace-jump.png"
      via: "Markdown image embed"
      pattern: "step-05-logs-trace-jump.png"
    - from: "Step 6 What to look for"
      to: "docs/screenshots/step-06-test-output.png"
      via: "Markdown image embed"
      pattern: "step-06-test-output.png"
    - from: "Each Step's Why it matters"
      to: "## Concepts & FAQ appendix"
      via: "markdown anchor link"
      pattern: "#concepts--faq"
---

<objective>
Rewrite the existing Steps 4/5/6 prose bodies to fit the lean 5-section template (D-08), and consolidate the four standalone narrative sections at the bottom of the README into a single `## Concepts & FAQ` appendix that the Step *Why it matters* paragraphs cross-reference. Implements DOC-01 (Steps 4-6 + appendix slice) per CONTEXT.md D-07 + D-08 + D-09.

Purpose: Steps 4/5/6 currently have rich author-voice prose bodies that don't share a structure. This plan refactors them into the same 5-section template Plan 07-05 applied to Steps 1/2/3, so the README reads like a textbook with one voice across all six steps. Every pedagogically load-bearing fact from D-07's CRITICAL invariants list is preserved — either inline in the rewritten Step body, or as a Concepts & FAQ cross-reference. The appendix consolidates four standalone narrative sections into one named block for easier navigation. The README's final paragraph closes per D-09.

Output: `README.md` with Steps 4/5/6 rewritten in-place (no new sections added), a new `## Concepts & FAQ` heading wrapping the four existing standalone narrative sections, and a D-09 closing paragraph appended after the appendix.

This plan is HIGH risk because it rewrites the most prose-heavy parts of the README and must NOT lose any of the D-07 invariants. The acceptance criteria are exhaustive grep checks against the existing prose anchors.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@README.md
@.planning/phases/04-metrics/04-CONTEXT.md
@.planning/phases/05-logs-correlation/05-CONTEXT.md
@.planning/phases/06-verification-tests/06-CONTEXT.md
@docs/screenshots/step-04-metrics.png
@docs/screenshots/step-05-logs-trace-jump.png
@docs/screenshots/step-06-test-output.png
@CLAUDE.md

<interfaces>
<!-- D-07 CRITICAL invariants (MUST be preserved verbatim or as cross-references): -->

Phase 4 (Step 4):
- 10-second `PeriodicMetricReader` interval (METRIC-01 — overrides 60s default)
- seconds-not-millis histogram unit (`"s"` not `"ms"`)
- `order.priority` non-semconv attribute key contrast (string-literal AttributeKey, not semconv)
- OTel→Prometheus name mangling (`orders.created` → `orders_created_total`; `http.server.request.duration` → `http_server_request_duration_seconds`)

Phase 5 (Step 5):
- PITFALL #5 / commit `f5c331a` `OpenTelemetryAppender.install(...)` ordering fix
- `appender.v1_0` vs `mdc.v1_0` package collision callout
- "Production-readiness callout: do not log untrusted payload fields" subsection — security-relevant, MUST stay verbatim

Phase 6 (Step 6):
- `<classifier>` Maven trickery callout
- Two `SpringApplicationBuilder` contexts in one JVM rationale
- `TestOtelHolder` static-singleton resolution of D-07.1 from 06-CONTEXT.md
- SimpleSpanProcessor-not-Batch test determinism lesson
- commit `f5c331a` cross-reference (test-side replication)

Across all steps:
- Per-service `OtelSdkConfiguration.java` duplication is intentional (DOC-05 / Phase 2 D-01) → callout in Concepts & FAQ
- The propagation pair in `otel-bootstrap` is shared on purpose (PROP-04) → callout in Concepts & FAQ

<!-- D-09 final paragraph (verbatim, EXACT wording per CONTEXT.md): -->
"Workshop is at main HEAD past step-06-tests; dashboard, load script, and full walkthrough are here. To revisit any step, `git checkout step-NN-*`."
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite Step 4 — Metrics in 5-section template</name>
  <read_first>
    - README.md (current Step 4 prose, L79-126; replace whole section)
    - .planning/phases/04-metrics/04-CONTEXT.md (METRIC-01..04 facts to preserve)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07 invariants for Step 4)
    - docs/screenshots/step-04-metrics.png
  </read_first>
  <action>
    Use the Edit tool. The `old_string` is the EXACT existing Step 4 section starting at the line
    `## Step 4: Metrics` and ending immediately before the line `## Step 5: Logs Correlation`. The
    `new_string` is the 5-section rewrite below. Read the existing section first to capture its
    exact bounds; then replace.

    Replacement Step 4 content:

    ```markdown
    ## Step 4: Metrics

    ### What you'll learn

    Three OTel instrument shapes — `LongCounter` (`orders.created`), `DoubleHistogram` (`http.server.request.duration`, **seconds**), and `ObservableLongGauge` (`orders.queue.depth.estimate`) — wired into both services as a sibling pipeline alongside the existing trace pipeline, with a 10-second `PeriodicMetricReader` interval that overrides OTel's 60-second default.

    ### Checkpoint

    `git checkout step-04-metrics` — adds `SdkMeterProvider` per service. The diff against `step-03-context-propagation` reads as "we added a sibling pipeline next to the trace pipeline" because Phase 4 D-01 extracted Phase 2's inline tracer pipeline into `private SdkTracerProvider buildTracerProvider(Resource)` and added a sibling `private SdkMeterProvider buildMeterProvider(Resource)`. Zero new dependencies (`opentelemetry-exporter-otlp` ships traces + metrics + logs from one jar; on classpath since Phase 2).

    ### Run

    ```sh
    git checkout step-04-metrics
    mise run infra:up
    mise run dev
    mise run demo:order        # alternates priority=express + priority=standard
    mise run load              # in another terminal — populates per-priority panels
    ```

    ### What to look for

    - **`orders_created_total`** in Mimir (Grafana → Explore → Prometheus): increments on every successful POST. Two series — `order_priority="express"` and `order_priority="standard"`. **Note the name mangling**: the OTel-to-Prometheus exporter (running inside `otel-lgtm`'s collector) converts dots to underscores and appends `_total` for monotonic counters, so OTel-side `orders.created` surfaces as Prometheus `orders_created_total`. The counter does NOT fire on the failure path (D-08) — failures are visible via the trace's ERROR status, not as a metric.
    - **`http_server_request_duration_seconds`** (Histogram, **seconds**): query `histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket[1m])))` for p95. **Unit is seconds (`"s"`), not milliseconds.** The seconds-not-millis trap (Phase 4 D-13) is the textbook OTel-porting mistake — semconv 1.40.0 specifies seconds, and Mimir's default `http_server_request_duration_seconds` dashboards assume seconds. Attributes follow HTTP semconv: `http.request.method` and `http.response.status_code` only — `url.path` is intentionally excluded for cardinality reasons.
    - **`orders_queue_depth_estimate`** (ObservableGauge, consumer-side): a synthetic value from `ThreadLocalRandom.current().nextInt(0, 50)` reported on every 10-second collection cycle. The `PeriodicMetricReader` interval is set to **10 seconds** (METRIC-01 — overrides OTel's 60-second default; this is the difference between "fresh metric every demo" and "wait a minute every demo").
    - **Attribute-key contrast** — `order.priority` is a string-literal `AttributeKey<String>` because it is NOT in the OTel semconv catalog (a *business* attribute), while `http.request.method` and `http.response.status_code` come from `HttpAttributes.HTTP_REQUEST_METHOD` and `HttpAttributes.HTTP_RESPONSE_STATUS_CODE` (semconv constants from `io.opentelemetry.semconv:1.40.0`).
    - **Same Resource attributes on every metric data point** — `service.name`, `service.namespace`, `service.instance.id`, `deployment.environment.name` (Phase 4 D-05 — Resource built once and shared between tracer + meter pipelines for cross-signal correlation in Grafana).

    ![Step 4 — Mimir RED metrics](docs/screenshots/step-04-metrics.png)

    ### Why it matters

    The three instrument shapes cover OTel's primary metric kinds. The textbook traps Phase 4 surfaces — seconds-not-millis, dots-to-underscores name mangling, the 60-second-vs-10-second reader interval, semconv-vs-business attribute keys — are the specific shapes that bite teams porting from custom metrics libraries. The "sibling pipeline" structure is a deliberate carryforward from Phase 2's helper extraction; the diff against the previous tag reads as a focused addition, not a tangled refactor. For why both services repeat the same `OtelSdkConfiguration.java` shape rather than sharing one — see *Why is OtelSdkConfiguration.java duplicated?* in the Concepts & FAQ appendix.
    ```

    Notes — do NOT alter on rewrite:
    - The METRIC-01..04 facts above are EXACT reproductions of D-07 invariants. Do not paraphrase.
    - The cross-reference to "Why is OtelSdkConfiguration.java duplicated?" anchors to the appendix entry preserved by Task 4.
  </action>
  <verify>
    <automated>
      grep -q '## Step 4: Metrics' README.md \
      && grep -q 'PeriodicMetricReader' README.md \
      && grep -q '10-second' README.md \
      && grep -q 'orders_created_total' README.md \
      && grep -q 'http_server_request_duration_seconds' README.md \
      && grep -q 'order.priority' README.md \
      && grep -q 'order_priority' README.md \
      && grep -q 'docs/screenshots/step-04-metrics.png' README.md \
      && grep -q 'seconds-not-millis' README.md \
      && grep -q 'name mangling' README.md \
      && grep -q '### What you' README.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README still contains `## Step 4: Metrics`
    - All 5 subsection headers present in Step 4
    - Step 4 contains literal strings: `PeriodicMetricReader`, `10-second`, `orders_created_total`, `http_server_request_duration_seconds`, `order.priority`, `order_priority`, `name mangling`, `seconds-not-millis`
    - Step 4 contains the screenshot embed `docs/screenshots/step-04-metrics.png`
    - Step 4 cross-references "Why is OtelSdkConfiguration.java duplicated?" in its Why-it-matters
    - Existing "## Step 5: Logs Correlation" section UNCHANGED in this task (Task 2 rewrites it)
  </acceptance_criteria>
  <done>
    Step 4 rewritten in 5-section template; all D-07 Step 4 invariants preserved; PNG embedded;
    appendix cross-ref present.
  </done>
</task>

<task type="auto">
  <name>Task 2: Rewrite Step 5 — Logs Correlation in 5-section template (preserve all D-07 invariants verbatim)</name>
  <read_first>
    - README.md (current Step 5 prose; replace whole section verbatim, including the load-bearing "Production-readiness callout" subsection)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-08, D-09, D-15, D-16 — PITFALL #5, appender package collision, untrusted-payload security callout)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07 Phase 5 invariants — security-relevant subsection MUST stay verbatim)
    - docs/screenshots/step-05-logs-trace-jump.png
  </read_first>
  <action>
    Replace the existing `## Step 5: Logs Correlation` section (current README L127-258) with the
    rewrite below. The "Production-readiness callout: do not log untrusted payload fields"
    subsection MUST be preserved verbatim — it is security-relevant and explicitly listed in the
    D-07 invariants.

    Replacement Step 5 content:

    ```markdown
    ## Step 5: Logs Correlation

    ### What you'll learn

    The third OTel signal — logs — wired alongside traces and metrics, plus MDC `trace_id`/`span_id` injection so terminal output is correlatable without leaving the workshop laptop. The load-bearing PITFALL #5 mitigation: `OpenTelemetryAppender.install(sdk)` runs INLINE in the `@Bean` factory body so Logback's pre-Spring initialization doesn't pin the appender to `OpenTelemetry.noop()`.

    ### Checkpoint

    `git checkout step-05-logs` — adds `SdkLoggerProvider` next to `SdkTracerProvider` and `SdkMeterProvider`, plus a per-service `logback-spring.xml` with the `OpenTelemetryAppender` (OTLP export) wrapped by the MDC injector wrapper appender. Commit `f5c331a` is the load-bearing fix moving `OpenTelemetryAppender.install(...)` into the `@Bean` factory body — `git show f5c331a` for the bug-fix narrative.

    ### Run

    ```sh
    git checkout step-05-logs
    mise run infra:up
    mise run dev
    mise run demo:order
    # then in Grafana → Explore → Loki:
    #   {service_name="order-producer"} |~ "<traceId>"
    ```

    ### What to look for

    - **Console output stamps `trace_id` / `span_id`**: every business-logic log line renders with `[trace_id=4b2e... span_id=ad12...]` in the bracketed pattern. Pre-`POST` startup logs render `[trace_id= span_id=]` (empty defaults via Logback's `%mdc{key:-}` syntax — that's the difference between "no active span" and "missing key").
    - **Loki log lines carry the same `trace_id`**: in Grafana → Explore → Loki, run `{service_name="order-producer"} |~ "<traceId>"` (replace `<traceId>` with the 32-hex value from console). Click the `trace_id` field on a returned log line; Grafana opens the matching trace in Tempo's Explore tab. **Click-through working IS LOG-05.**
    - **Triple-signal correlation on the failure path**: the deterministic 10th order fires `LOG.error` in the consumer's `ProcessingService` alongside `span.recordException(e)`. Loki query `{service_name="order-consumer"} | severity_text="ERROR"` returns the failure log; click its trace_id and Tempo shows the trace whose CONSUMER span carries the recordException event AND a metric data point in Mimir for the same priority/method. All three signals share the trace_id.
    - **`severity_text="ERROR"` (not `|= "ERROR"`)** — the OTLP `OpenTelemetryAppender` ships the formatted message **without** a level prefix; the Logback level lands on the OTLP record as the `severity_text` field which Loki's OTLP receiver indexes as a detected field. A substring filter against the message body returns zero results because the formatted body is just `order processing failed: orderId=<uuid>`.
    - **Two `OpenTelemetryAppender` classes in different packages** — `appender.v1_0.OpenTelemetryAppender` is the OTLP exporter (has the `install()` static); `mdc.v1_0.OpenTelemetryAppender` is an appender WRAPPER that reads `Span.current()` and stamps `trace_id`/`span_id` into MDC before forwarding. The MDC injector wraps `CONSOLE` so the bracketed pattern resolves correctly for in-span events.

    ![Step 5 — Loki log line with trace_id click-through to Tempo](docs/screenshots/step-05-logs-trace-jump.png)

    ### Why it matters

    The `OpenTelemetryAppender.install(sdk)` order-of-operations is the textbook OTel logback gotcha (see [opentelemetry-java-instrumentation#10307](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307)). Logback initializes BEFORE the Spring `ApplicationContext` is built, so the appender constructed at startup defaults to `OpenTelemetry.noop()`. Calling `install(sdk)` inside the `@Bean` factory body — immediately after `OpenTelemetrySdk.builder()...build()` returns and before `return sdk;` — avoids the Spring self-cycle a `@PostConstruct` shape would create AND tightens the window in which logs land in the noop replay queue. **The install-inline-in-the-`@Bean`-factory shape IS the lesson.** Commit `f5c331a` is the bug-fix narrative; reading the diff is itself part of the workshop. For the per-service-vs-shared design contrast (logs follow the same per-service-duplicated pattern as Phase 2's SDK setup), see *Why is OtelSdkConfiguration.java duplicated?* in the Concepts & FAQ appendix.

    ### Production-readiness callout: do not log untrusted payload fields

    The Phase 5 business log lines deliberately mirror what application code looks like **before** anyone has thought about log hygiene — the workshop runs on a developer laptop with synthetic data, and "what raw application logs look like" is part of the lesson. Two specific log sites would NOT be safe in production and should be tightened before this code is copied anywhere real:

    - **`OrderController.create(...)`** — `LOG.info("received POST /orders payload={}", payload)` writes the entire `Map<String, Object>` request body to logs and Loki. Any field an attendee POSTs (free-form text, accidental secrets, credit-card-shaped strings) lands in log storage. The `Map.toString()` formatter does no CRLF escaping either — a `{"note":"hi\r\n[INFO] forged"}` payload injects a forged log line into the file/console that downstream parsers may treat as real.
    - **`ProcessingService.process(...)`** — `LOG.error("order processing failed: orderId={}", orderId, e)` logs an attacker-controlled string from the message payload. The producer's `OrderPublisher` currently overwrites this field with a server-minted UUID, but that defense is one edit away — the consumer itself does not validate the `orderId` shape, so a CRLF in the field would inject a forged log line on the consumer side.

    **Production fixes (out of scope for the workshop demo, in scope for any real deployment of this code shape):** replace untrusted-payload logs with explicit allowlisted fields (e.g. log only `priority` from the controller body, or move the entry log into `OrderService.place(...)` after the orderId is generated and log only that orderId); validate the shape of fields read from messages at consumer ingress before they hit a log line; or drop the field entirely and rely on the `trace_id` stamped by the OpenTelemetryAppender as the correlation key (the workshop's success criterion is "correlate via `trace_id`", not "via `orderId`"). Tracked under threat-model row T-05-04-01 (producer payload disclosure) in `.planning/phases/05-logs-correlation/05-04-SUMMARY.md`; the consumer-side concern is the symmetric case on the failure path.
    ```

    Notes:
    - The "Production-readiness callout" subsection is preserved VERBATIM from the existing README
      (current L222-258). D-07 explicitly flags this subsection as security-relevant — DO NOT
      paraphrase, DO NOT shorten, DO NOT remove.
    - The `severity_text="ERROR"` callout is also preserved (current L212-220). It explains the
      OTLP appender's body shape vs Logback's level field.
    - PITFALL #5 / commit `f5c331a` references retained.
    - `appender.v1_0` vs `mdc.v1_0` package collision callout retained.
  </action>
  <verify>
    <automated>
      grep -q '## Step 5: Logs Correlation' README.md \
      && grep -q 'PeriodicMetricReader' README.md \
      && grep -q 'f5c331a' README.md \
      && grep -q 'PITFALL #5' README.md || grep -q 'install(sdk)' README.md \
      && grep -q 'appender.v1_0' README.md \
      && grep -q 'mdc.v1_0' README.md \
      && grep -q 'do not log untrusted' README.md \
      && grep -q 'Production-readiness callout' README.md \
      && grep -q 'severity_text' README.md \
      && grep -q 'docs/screenshots/step-05-logs-trace-jump.png' README.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README still contains `## Step 5: Logs Correlation`
    - All 5 subsection headers present in Step 5
    - Step 5 contains literal `f5c331a` (the load-bearing commit reference)
    - Step 5 contains literal `appender.v1_0` and `mdc.v1_0` (the package collision callout)
    - Step 5 contains literal `do not log untrusted` (security callout marker)
    - Step 5 contains literal `Production-readiness callout` heading
    - Step 5 contains literal `severity_text` (Loki query callout)
    - Step 5 contains the screenshot embed `docs/screenshots/step-05-logs-trace-jump.png`
    - Step 5 contains `OrderController.create` AND `ProcessingService.process` (the two specific log-site references)
    - Existing "## Step 6: Verification Tests" section UNCHANGED in this task
  </acceptance_criteria>
  <done>
    Step 5 rewritten; PITFALL #5 / f5c331a / appender package collision / untrusted-payload security
    callout all preserved verbatim; appendix cross-ref present.
  </done>
</task>

<task type="auto">
  <name>Task 3: Rewrite Step 6 — Verification Tests in 5-section template</name>
  <read_first>
    - README.md (current Step 6 prose; replace whole section)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-04 classifier, D-07.1 TestOtelHolder, D-09 f5c331a test-side, D-13 Awaitility, D-14 four @Test methods, D-17 triple-signal failure correlation)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07 Phase 6 invariants)
    - docs/screenshots/step-06-test-output.png
  </read_first>
  <action>
    Replace the existing `## Step 6: Verification Tests` section with the rewrite below.

    ```markdown
    ## Step 6: Verification Tests

    ### What you'll learn

    A CI-grade proof of the three-signal instrumentation chain — Testcontainers `RabbitMQContainer` + `InMemorySpanExporter` + `SimpleSpanProcessor` in a `@TestConfiguration` that asserts traceId shared, parentSpanId correct, span kinds correct, and messaging semconv attributes present. Caps the workshop with "now you can prove your instrumentation works in CI without a live OTLP backend."

    ### Checkpoint

    `git checkout step-06-tests` — adds a top-level `integration-tests` Maven module with a single cross-service `OrderFlowIT.java`, a `TestOtelHolder` static-singleton, a `TestOtelConfiguration` `@TestConfiguration`, and a `<classifier>exec</classifier>` repackage execution on producer/consumer service POMs so the test module can depend on the plain classes jars while production builds still produce runnable fat jars.

    ### Run

    ```sh
    git checkout step-06-tests
    docker compose stop rabbitmq    # IMPORTANT — prove Testcontainers is genuinely used
    mise run test                   # → mvn -T 1C verify; expect 4 green tests in Failsafe summary
    ```

    The test exits non-zero on any assertion failure — suitable for any CI runner with Docker available.

    ### What to look for

    - **Random RabbitMQ port in test logs**: a `@BeforeAll` line `RabbitMQ test container available at localhost:<random-port>` (e.g., `localhost:54321`) — NOT `localhost:5672`. With your host `docker compose` RabbitMQ stopped, the tests still pass — proof Testcontainers is genuinely used (TEST-01 SC #2).
    - **Four green `@Test` methods** in the Failsafe summary covering the workshop's four signal areas:
      1. **traces** — producer + consumer spans share `traceId`; consumer's `parentSpanId == producer.spanId`; SpanKind set covers SERVER + INTERNAL + PRODUCER + CONSUMER + INTERNAL; messaging semconv attributes (`messaging.system=rabbitmq`, `messaging.operation_type=publish/process`).
      2. **logs** — producer-side `LOG.info` records carry the producer trace's `trace_id` (proves Phase 5's `OpenTelemetryAppender.install(...)` wiring still works through the test SDK).
      3. **metrics** — `orders.created` counter increments to 1 with `order.priority="express"`; `http.server.request.duration` histogram records the POST with `http.request.method=POST` + `http.response.status_code=202`.
      4. **failure path** — the 10th order's CONSUMER span has `Status.ERROR` + a recorded exception event; a `LOG.error` record carries the same trace_id (triple-signal correlation — the workshop's strongest single statement of "all three signals work together").
    - **`SimpleSpanProcessor` + `InMemorySpanExporter` swap** — production wires `BatchSpanProcessor` + `OtlpGrpcSpanExporter`; tests use `TestOtelHolder` which builds the SDK with the synchronous `SimpleSpanProcessor` and the in-memory exporter from `opentelemetry-sdk-testing`. Every `span.end()` exports immediately — **NO Thread.sleep** in tests; Awaitility polling for async settling.
    - **`<classifier>exec</classifier>` Maven trickery** — producer/consumer POMs publish TWO artifacts: the plain classes jar (default, exposes `ProducerApplication.class` directly on classpath) and a separate `-exec` repackaged executable fat jar. The integration-tests module depends on the plain jars so `new SpringApplicationBuilder(ProducerApplication.class, ...)` works. See `producer-service/pom.xml` for the canonical Spring Boot 3.4.13 syntax.
    - **`TestOtelHolder` static-singleton** — the test-side replication of commit `f5c331a` ordering: `OpenTelemetryAppender.install(sdk)` runs AFTER `builder().build()` and BEFORE the SDK reference is published. The static-singleton resolves the @TestConfiguration vs @Bean bootstrap-ordering dance (06-CONTEXT.md D-07.1).
    - **Two `SpringApplicationBuilder` contexts in one JVM** — `OrderFlowIT.@BeforeAll` starts both `ProducerApplication` and `ConsumerApplication` as separate Spring contexts in the same JVM, each `@Import`ing `TestOtelConfiguration`. The shared `TestOtelHolder` lets BOTH contexts emit spans into one `InMemorySpanExporter` — the only way to assert cross-service `traceId`/`parentSpanId` relationships in-process.

    ![Step 6 — mvn verify with random RabbitMQ port + four green tests](docs/screenshots/step-06-test-output.png)

    ### Why it matters

    Production-vs-test SDK divergence is a deliberate pedagogical contrast. Phase 2's per-service duplication of `OtelSdkConfiguration.java` is a PRODUCTION rule — `TestOtelConfiguration` is a single `@TestConfiguration` shared by both Spring contexts because the in-memory exporter must see ALL spans across both services in one queue. The contrast itself is the lesson: duplicate when readers benefit from reading the same setup twice; share when the test fixture's purpose requires one shared instance. The triple-signal correlation `@Test` (failure path) is the workshop's strongest single statement that all three signals work together — one trace_id, one error, one log, one metric data point, one in-memory queue per signal sink, one assertion suite. For the broader per-service-vs-shared design pattern, see *Why is the propagation pair shared?* and *Why is OtelSdkConfiguration.java duplicated?* in the Concepts & FAQ appendix.
    ```
  </action>
  <verify>
    <automated>
      grep -q '## Step 6: Verification Tests' README.md \
      && grep -q 'random RabbitMQ port' README.md || grep -q 'localhost:54321' README.md \
      && grep -q 'classifier' README.md \
      && grep -q 'TestOtelHolder' README.md \
      && grep -q 'SimpleSpanProcessor' README.md \
      && grep -q 'SpringApplicationBuilder' README.md \
      && grep -q 'docs/screenshots/step-06-test-output.png' README.md \
      && grep -q 'four .* tests' README.md || grep -q 'four green' README.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README still contains `## Step 6: Verification Tests`
    - All 5 subsection headers present in Step 6
    - Step 6 contains literal `classifier` (Maven trickery callout)
    - Step 6 contains literal `TestOtelHolder` (static-singleton resolution)
    - Step 6 contains literal `SimpleSpanProcessor` (test-determinism lesson)
    - Step 6 contains literal `SpringApplicationBuilder` (two-contexts-one-JVM rationale)
    - Step 6 contains the screenshot embed `docs/screenshots/step-06-test-output.png`
    - Step 6 cross-references the Concepts & FAQ appendix entries
  </acceptance_criteria>
  <done>
    Step 6 rewritten; all D-07 Phase 6 invariants preserved; appendix cross-refs present.
  </done>
</task>

<task type="auto">
  <name>Task 4: Consolidate four standalone narrative sections into ## Concepts & FAQ appendix + add D-09 final paragraph</name>
  <read_first>
    - README.md (current standalone sections at the bottom: "## Reading the code", "## Why is OtelSdkConfiguration.java duplicated?", "## Why is the propagation pair shared?", "## What's NOT here yet")
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-08 — appendix structure; D-09 — final paragraph verbatim)
  </read_first>
  <action>
    Step 1 — Find the line `## Reading the code` (currently around L289). Insert ABOVE it:
    ```markdown
    ## Concepts & FAQ

    The following four sections collect the narrative deep-dives the per-step *Why it matters* paragraphs cross-reference. They preserve a second reading mode: skim the per-step walkthrough top-to-bottom, then dive into the conceptual narrative — or read this section first and use the per-step blocks as worked examples.
    ```

    Demote the four `## ` headings to `### ` so they render as subsections of `## Concepts & FAQ`.
    Use the Edit tool with `replace_all: true` for each rename (these heading strings appear only
    once each in the file, so a single replacement is sufficient — but verify before commit).

    - `## Reading the code` → `### Reading the code`
    - `## Why is OtelSdkConfiguration.java duplicated?` → `### Why is OtelSdkConfiguration.java duplicated?`
    - `## Why is the propagation pair shared?` → `### Why is the propagation pair shared?`
    - `## What's NOT here yet` → `### What's NOT here yet`

    The body content of each subsection MUST be preserved BYTE-IDENTICAL. Do not paraphrase, do not
    re-flow paragraphs, do not modernize the prose. The four narrative sections are
    pedagogically load-bearing and have been validated through prior phases.

    Step 2 — Add the D-09 closing paragraph as the last block in the file. Append after the last
    line of the existing "What's NOT here yet" section. Exact verbatim content (D-09 prescribes
    this wording):

    ```markdown

    ---

    Workshop is at main HEAD past `step-06-tests`; dashboard, load script, and full walkthrough are here. To revisit any step, `git checkout step-NN-*`.
    ```

    The horizontal rule (`---`) visually separates the closing paragraph from the appendix.

    Step 3 — Verify the existing "What's NOT here yet" content is updated to reflect Phase 7 ship
    state. Currently the section reads "No pre-built Grafana dashboard or load script (Phase 7)";
    Phase 7 has now landed those, so the bullet is obsolete. Replace the existing bullets with
    a v1.x / v2 outlook (preserving the "uninstrumented on purpose" framing prefix). Use Edit
    to find and replace the bullet list:

    Existing block (verbatim, current L319-323):
    ```
    The following are deliberate Phase 1 omissions — the repo isn't incomplete, it's **uninstrumented on purpose** so each later phase has something concrete to add:

    - No `OtelSdkConfiguration.java` (Phase 2)
    - No pre-built Grafana dashboard or load script (Phase 7)
    ```

    Replacement block:
    ```
    The workshop ships at main HEAD with all six steps' instrumentation, the auto-provisioned dashboard, the continuous-load script, and the per-step screenshot set. Deliberate v1 omissions (deferred to v2):

    - **Sampling-variant checkpoint** (`step-07-sampling-variant` / SAMP-01) — `TraceIdRatioBased` and `ParentBased` samplers side-by-side with environment-driven config.
    - **Baggage propagation checkpoint** (`step-08-baggage` / PROP-V2-01) — `W3CBaggagePropagator` carrying business attributes across the AMQP boundary. Phase 2 already wired the propagator; a v2 phase exercises it.
    - **DLX/retry checkpoint** (`step-09-dlx-retry` / FAIL-01) — dead-letter exchange and retry instrumentation with messaging-semconv `messaging.rabbitmq.destination_routing_key`.
    - **`docs/FACILITATOR.md`** (FAC-01) — timing notes, common questions, "if you see X, do Y" — only needed when someone other than the original author delivers the workshop.
    - **CI YAML** for `mise run test` on PRs — the test exits non-zero (TEST-06), sufficient for any CI runner; YAML belongs in v2 if the workshop becomes a maintained shared artifact across cohorts.
    - **Pyroscope / continuous profiling** — fourth-signal extension if a future cohort wants it.
    - **Vendor-specific exporter swap demo** (Honeycomb, Datadog, etc.) — one-line OTLP endpoint change attendees can do themselves.
    ```
    This refresh removes the obsolete Phase 7 bullet AND captures the v2 deferred-ideas list from
    REQUIREMENTS.md / CONTEXT.md `<deferred>`.
  </action>
  <verify>
    <automated>
      grep -q '^## Concepts & FAQ' README.md \
      && grep -q '^### Reading the code' README.md \
      && grep -q '^### Why is OtelSdkConfiguration.java duplicated?' README.md \
      && grep -q '^### Why is the propagation pair shared?' README.md \
      && grep -q "^### What's NOT here yet" README.md \
      && grep -q 'Workshop is at main HEAD past' README.md \
      && grep -q 'step-06-tests' README.md \
      && grep -q 'git checkout step-NN' README.md \
      && (grep -c '^## ' README.md) | { read -r n; [[ $n -ge 7 && $n -le 9 ]]; } \
      && ! grep -q '^## Reading the code$' README.md \
      && ! grep -q '^## Why is OtelSdkConfiguration.java duplicated?$' README.md \
      && ! grep -q '^## Why is the propagation pair shared?$' README.md \
      && ! grep -q "^## What's NOT here yet$" README.md \
      && ! grep -q 'No pre-built Grafana dashboard or load script' README.md \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - README contains `## Concepts & FAQ` heading
    - README contains all four `### `-level subsections: `### Reading the code`, `### Why is OtelSdkConfiguration.java duplicated?`, `### Why is the propagation pair shared?`, `### What's NOT here yet`
    - README does NOT contain the four standalone-section headings at `## ` level any more (they have been demoted)
    - README contains the D-09 final paragraph: `Workshop is at main HEAD past`, `step-06-tests`, `git checkout step-NN`
    - README contains a `---` horizontal rule before the final paragraph
    - The number of `## ` headings is between 7 and 9 (Prerequisites + Workshop checkpoints + 6 Steps + Concepts & FAQ = 9; or 8 if Workshop checkpoints is `### `; or 7 if Prerequisites is `### `)
    - Obsolete bullet `No pre-built Grafana dashboard or load script` is REMOVED from "What's NOT here yet"
    - Updated "What's NOT here yet" lists v2 deferred items per CONTEXT.md `<deferred>`
  </acceptance_criteria>
  <done>
    Concepts & FAQ appendix consolidated; four narrative sections preserved as `### ` subsections;
    D-09 final paragraph appended; "What's NOT here yet" refreshed to v1-ship state.
  </done>
</task>

<task type="auto">
  <name>Task 5: Whole-document invariant audit (D-07 grep gates)</name>
  <read_first>
    - README.md (whole file — Tasks 1-4 have completed)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-07 — full invariants list)
  </read_first>
  <action>
    Run a final whole-document grep audit to confirm EVERY D-07 invariant is preserved. This task
    is verification-only; it produces no diff. If any check fails, fix the affected Step section
    in-place and re-run.

    Invariant checklist (every grep MUST hit):

    Phase 4:
    - `grep -q 'PeriodicMetricReader' README.md`
    - `grep -q '10-second' README.md` (or `10 second` — interval call-out)
    - `grep -q 'seconds-not-millis' README.md`
    - `grep -q 'order.priority' README.md` (non-semconv business attribute)
    - `grep -q 'name mangling' README.md` (OTel→Prometheus)
    - `grep -q 'orders_created_total' README.md`
    - `grep -q 'http_server_request_duration_seconds' README.md`

    Phase 5:
    - `grep -q 'f5c331a' README.md`
    - `grep -q 'install(sdk)' README.md` (or `OpenTelemetryAppender.install` — PITFALL #5)
    - `grep -q 'appender.v1_0' README.md`
    - `grep -q 'mdc.v1_0' README.md`
    - `grep -q 'do not log untrusted' README.md` (security callout)
    - `grep -q 'Production-readiness callout' README.md`
    - `grep -q 'severity_text' README.md`

    Phase 6:
    - `grep -q 'classifier' README.md`
    - `grep -q 'TestOtelHolder' README.md`
    - `grep -q 'SimpleSpanProcessor' README.md`
    - `grep -q 'SpringApplicationBuilder' README.md`
    - `grep -q 'random.* port' README.md` (TEST-01 SC #2 — random RabbitMQ port visibility)

    Cross-cutting:
    - `grep -q 'duplicat' README.md` (per-service duplication callout)
    - `grep -q 'shared' README.md` (propagation pair shared callout)
    - `grep -q 'Concepts & FAQ' README.md` (appendix exists)

    Structural:
    - `grep -c '^## Step ' README.md` returns exactly `6`
    - `grep -c '^### What you' README.md` returns exactly `6`
    - `grep -c '^### Checkpoint' README.md` returns exactly `6`
    - `grep -c '^### Run' README.md` returns exactly `6`
    - `grep -c '^### What to look for' README.md` returns exactly `6`
    - `grep -c '^### Why it matters' README.md` returns exactly `6`

    DOC-04 broken/fixed pair:
    - `grep -q 'docs/screenshots/step-02-disconnected-traces.png' README.md`
    - `grep -q 'docs/screenshots/step-03-joined-trace.png' README.md`
    - `grep -q '<table>' README.md` (side-by-side rendering)

    Per-step screenshot embeds (DOC-04):
    - `grep -q 'docs/screenshots/step-01-empty-tempo.png' README.md`
    - `grep -q 'docs/screenshots/step-04-metrics.png' README.md`
    - `grep -q 'docs/screenshots/step-05-logs-trace-jump.png' README.md`
    - `grep -q 'docs/screenshots/step-06-test-output.png' README.md`

    D-09 final paragraph:
    - `grep -q 'Workshop is at main HEAD past' README.md`

    Author the failed-check fixes inline in this task. If any invariant is missing, edit the
    relevant Step section to include the missing literal token. Common fixes:
    - PITFALL #5 token missing → re-insert "PITFALL #5" or "install(sdk)" reference in Step 5
    - "shared" missing → ensure Step 3's Why-it-matters mentions "shared"

    Record audit results in SUMMARY.md.
  </action>
  <verify>
    <automated>
      # Phase 4
      grep -q 'PeriodicMetricReader' README.md \
      && grep -q 'seconds-not-millis' README.md \
      && grep -q 'order.priority' README.md \
      && grep -q 'name mangling' README.md \
      && grep -q 'orders_created_total' README.md \
      && grep -q 'http_server_request_duration_seconds' README.md \
      \
      # Phase 5
      && grep -q 'f5c331a' README.md \
      && grep -q 'appender.v1_0' README.md \
      && grep -q 'mdc.v1_0' README.md \
      && grep -q 'do not log untrusted' README.md \
      && grep -q 'Production-readiness callout' README.md \
      && grep -q 'severity_text' README.md \
      \
      # Phase 6
      && grep -q 'classifier' README.md \
      && grep -q 'TestOtelHolder' README.md \
      && grep -q 'SimpleSpanProcessor' README.md \
      && grep -q 'SpringApplicationBuilder' README.md \
      \
      # Structural — exactly 6 of each subsection header
      && [ "$(grep -c '^### What you' README.md)" -eq 6 ] \
      && [ "$(grep -c '^### Checkpoint' README.md)" -eq 6 ] \
      && [ "$(grep -c '^### Run' README.md)" -eq 6 ] \
      && [ "$(grep -c '^### What to look for' README.md)" -eq 6 ] \
      && [ "$(grep -c '^### Why it matters' README.md)" -eq 6 ] \
      && [ "$(grep -c '^## Step ' README.md)" -eq 6 ] \
      \
      # DOC-04 broken/fixed pair
      && grep -q 'docs/screenshots/step-02-disconnected-traces.png' README.md \
      && grep -q 'docs/screenshots/step-03-joined-trace.png' README.md \
      && grep -q '<table>' README.md \
      \
      # All step PNGs
      && grep -q 'docs/screenshots/step-01-empty-tempo.png' README.md \
      && grep -q 'docs/screenshots/step-04-metrics.png' README.md \
      && grep -q 'docs/screenshots/step-05-logs-trace-jump.png' README.md \
      && grep -q 'docs/screenshots/step-06-test-output.png' README.md \
      \
      # D-09 close
      && grep -q 'Workshop is at main HEAD past' README.md \
      \
      # Concepts & FAQ
      && grep -q '## Concepts & FAQ' README.md \
      || (echo "VERIFY FAILED — D-07 invariant audit caught a missing token" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - All Phase 4/5/6 D-07 invariants preserved (grep checks above all pass)
    - Exactly 6 of each subsection header (`### What you'll learn`, `### Checkpoint`, `### Run`, `### What to look for`, `### Why it matters`)
    - Exactly 6 `## Step ` headings
    - DOC-04 broken/fixed pair side-by-side via `<table>`
    - All 6 (or 7) per-step PNG embeds present
    - D-09 final paragraph present
    - `## Concepts & FAQ` appendix present
  </acceptance_criteria>
  <done>
    README.md is structurally and content-wise complete for Phase 7. All D-07 invariants verified.
    Plan 07-07 takes over for the exit gate (success-criteria validation + STATE/ROADMAP atomic flip).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| README.md edits | All content authored by planner from CONTEXT.md verbatim or prior-phase prose; no untrusted input |
| Embedded image references | Local relative paths only — `docs/screenshots/*.png` |
| HTML in markdown (`<table>`, `<img>`) | GitHub-flavored markdown sanitizes via allowlist |
| Untrusted-payload security callout | Preserved verbatim from existing prose; threat-model row T-05-04-01 referenced |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-06-01 | Information Disclosure | "Production-readiness callout" subsection (security-relevant) | mitigate | Preserved VERBATIM per D-07 invariants; grep gate confirms presence; threat-model cross-references existing T-05-04-01 in 05-04-SUMMARY.md |
| T-07-06-02 | Tampering | Demoting four standalone-section headings from `## ` to `### ` | mitigate | Anchor links to those sections (e.g., `#reading-the-code`) still resolve because GitHub auto-generates anchors from heading text regardless of level; grep gate verifies `### ` headings present |
| T-07-06-03 | Spoofing | "What's NOT here yet" rewrite removes obsolete Phase 7 bullet | accept | Replacement content captures REQUIREMENTS.md v2 deferred items + CONTEXT.md `<deferred>` ideas — no security-relevant content lost |
| T-07-06-04 | Tampering | Cross-references between Steps and Concepts & FAQ | mitigate | Grep gate in Task 5 verifies the appendix subsection names exist with their canonical text |
</threat_model>

<verification>
- All D-07 invariants preserved (Task 5 grep audit).
- Exactly 6 Step sections in 5-section template.
- Concepts & FAQ appendix consolidates four standalone narrative sections.
- D-09 final paragraph present.
- DOC-04 broken/fixed pair embedded via HTML table.
</verification>

<success_criteria>
- DOC-01 fully delivered (all 6 Steps in lean template).
- All D-07 CRITICAL invariants preserved (grep-verifiable).
- ROADMAP Phase 7 success criterion 3 (READ start-to-finish without running code) is structurally satisfied.
- ROADMAP Phase 7 success criterion 4 (every step has paired README block + copy-pasteable curl) satisfied.
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-06-SUMMARY.md` recording:
- Final byte count and line count of README.md
- Grep audit results (Task 5) — which invariants checked, all green
- Any deviations or fixes applied during the audit
- Any preservation tradeoffs (none expected — all D-07 invariants are in the rewrites verbatim)
</output>
