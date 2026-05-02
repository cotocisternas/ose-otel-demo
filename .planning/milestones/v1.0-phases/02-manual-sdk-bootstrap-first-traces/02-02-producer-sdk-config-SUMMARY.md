---
phase: 02-manual-sdk-bootstrap-first-traces
plan: 02
subsystem: observability
tags: [opentelemetry, otel-sdk, manual-instrumentation, semconv, server-span, http-filter, batch-span-processor, otlp-grpc, w3c-trace-context, w3c-baggage, parent-based-sampler, graceful-shutdown, spring-boot-3.4, jakarta-servlet]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plan 02-01: producer-service/pom.xml carries the 5 OTel deps (api/sdk/exporter-otlp BOM-managed via opentelemetry-bom:1.61.0; opentelemetry-semconv:1.40.0 + opentelemetry-semconv-incubating:1.40.0-alpha pinned). Phase 2 invariant verified by mise verify:bom."
  - phase: 01-baseline-scaffold
    provides: "producer-service Spring Boot app with OrderController (POST /orders), OrderService, OrderPublisher, RabbitConfig; mise.toml with OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 and dev:producer task; docker-compose.yml exposing grafana/otel-lgtm :3000/:4317/:4318 and rabbitmq:4.3-management :5672/:15672."
provides:
  - "producer-service/.../config/OtelSdkConfiguration.java — manual OpenTelemetrySdk wiring (TRACE-01..04): Resource via Resource.getDefault().merge(...) with semconv 1.40.0 ServiceAttributes (stable) + DeploymentIncubatingAttributes (incubating); SdkTracerProvider with BatchSpanProcessor (default tuning 5s/2048/512) + OtlpGrpcSpanExporter reading OTEL_EXPORTER_OTLP_ENDPOINT with http://localhost:4317 fallback; Sampler.parentBased(Sampler.alwaysOn()) explicit; composite W3CTraceContextPropagator + W3CBaggagePropagator; @Bean(destroyMethod=\"close\") for graceful shutdown flush"
  - "producer-service/.../config/HttpServerSpanFilter.java — OncePerRequestFilter wrapping every non-/actuator HTTP request in a SERVER span (TRACE-05) using the D-01 inline template (try/Scope/catch (RuntimeException | ServletException | IOException)/finally) with 7 HTTP semconv attrs (HTTP_REQUEST_METHOD, URL_PATH, URL_SCHEME, SERVER_ADDRESS, SERVER_PORT, HTTP_ROUTE pre-chain; HTTP_RESPONSE_STATUS_CODE post-chain). shouldNotFilter() short-circuits paths starting /actuator/ (D-06 — kills docker-compose healthcheck noise)"
  - "Tracer bean (instrumentation scope com.example.producer) constructor-injectable into OrderService, OrderPublisher, and any future producer-side instrumented class"
  - "OpenTelemetry bean exposing the SDK's ContextPropagators — Phase 3 reuses these via openTelemetry.getPropagators().getTextMapPropagator() (no new propagator construction needed for AMQP context propagation)"
  - "Per-service-duplication ethos extended: this OtelSdkConfiguration is the FIRST half of the duplicated pair; consumer-service mirrors it in Plan 02-03 with two service-identity strings flipped (order-producer→order-consumer; com.example.producer→com.example.consumer)"
affects:
  - "02-03-consumer-sdk-config (parallel sibling — IDENTICAL OtelSdkConfiguration.java with two service-identity strings flipped per DOC-05; the file structure established here is the template)"
  - "02-04-producer-instrumentation (consumes Tracer bean for INTERNAL span around OrderService.place + PRODUCER span around OrderPublisher.publish — D-08; messaging semconv attrs from MessagingIncubatingAttributes already available via Plan 02-01's incubating dep)"
  - "02-05-consumer-instrumentation (consumes consumer-side Tracer for INTERNAL + CONSUMER spans; CONSUMER span starts from Context.root() per D-10)"
  - "02-06-readme-and-exit-gate (asserts SERVER span visible in Tempo for service.name=order-producer; verifies graceful shutdown flush; confirms /actuator/* exclusion)"
  - "Phase 3 (W3C context propagation across AMQP) — propagators wired here are reused by TracingMessagePostProcessor (inject) + TracingMessageListenerAdvice (extract); the inline PRODUCER span from Plan 02-04 is REPLACED in Phase 3 by the post-processor"
  - "Phase 4 (metrics) — OtelSdkConfiguration extended to add SdkMeterProvider; OTEL_EXPORTER_OTLP_ENDPOINT pattern reused; opentelemetry-exporter-otlp already on classpath"
  - "Phase 5 (logs correlation) — OtelSdkConfiguration extended to add SdkLoggerProvider + OpenTelemetryAppender install; same destroyMethod=close cascade keeps working (close() shuts down all three providers)"

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry.api.OpenTelemetry — central SDK accessor exposed as Spring bean"
    - "io.opentelemetry.sdk.OpenTelemetrySdk + .builder().setTracerProvider(...).setPropagators(...).build() — manual SDK wiring (NOT .buildAndRegisterGlobal())"
    - "io.opentelemetry.sdk.resources.Resource + Resource.getDefault().merge(...) — neutralises the unknown_service:java default by putting our service.name on top of the merge"
    - "io.opentelemetry.sdk.trace.SdkTracerProvider — assembles Resource + Sampler + processors"
    - "io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder(spanExporter).build() — production-shape BSP with default tuning (5s schedule / 2048 max queue / 512 max batch / 30s exporter timeout)"
    - "io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter.builder().setEndpoint(http://...).build() — gRPC OTLP exporter targeting grafana/otel-lgtm :4317"
    - "io.opentelemetry.sdk.trace.samplers.Sampler.parentBased(Sampler.alwaysOn()) — explicit sampler with multi-paragraph teaching comment about Sampler.parentBased(Sampler.traceIdRatioBased(0.1)) production tradeoff"
    - "io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator + io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator wired via TextMapPropagator.composite(...) → ContextPropagators.create(...)"
    - "io.opentelemetry.semconv.ServiceAttributes (stable) — SERVICE_NAME, SERVICE_NAMESPACE, SERVICE_INSTANCE_ID"
    - "io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes — DEPLOYMENT_ENVIRONMENT_NAME"
    - "io.opentelemetry.semconv.HttpAttributes (stable) — HTTP_REQUEST_METHOD, HTTP_RESPONSE_STATUS_CODE, HTTP_ROUTE"
    - "io.opentelemetry.semconv.UrlAttributes (stable) — URL_PATH, URL_SCHEME"
    - "io.opentelemetry.semconv.ServerAttributes (stable) — SERVER_ADDRESS, SERVER_PORT"
    - "org.springframework.web.filter.OncePerRequestFilter — SERVER span filter base class; shouldNotFilter() canonical exclusion hook (NOT FilterRegistrationBean.setUrlPatterns, which has known issues per spring-boot#38331)"
  patterns:
    - "@Bean(destroyMethod=\"close\") on OpenTelemetry — load-bearing for graceful shutdown flush; close() → shutdown().join(10s) cascades to BSP.worker.shutdown() forcing a final batch flush. Same pattern reused for SdkMeterProvider (Phase 4) and SdkLoggerProvider (Phase 5)"
    - "Manual env-var read (D-12): String endpoint = Optional.ofNullable(System.getenv(\"OTEL_EXPORTER_OTLP_ENDPOINT\")).orElse(\"http://localhost:4317\"). NO opentelemetry-sdk-extension-autoconfigure dependency — env-var contract visible in code"
    - "Resource.getDefault().merge(custom) — merge order matters; custom attrs put last so service.name overrides the unknown_service:java default. Pattern reused in consumer-service (Plan 02-03)"
    - "D-01 pure-inline span template — tracer.spanBuilder().setSpanKind().setAttribute()...startSpan() / try (Scope = makeCurrent()) / body / catch recordException + setStatus(ERROR) + throw / finally span.end(). Boilerplate IS the lesson — no helper class, no AOP. All 5 spans across producer + consumer follow this exact 8-12 line idiom"
    - "@Bean Filter auto-discovery (Spring Boot 3.4) — Spring wraps @Bean Filter instances in a default FilterRegistrationBean(/*); no explicit URL-pattern config in OtelSdkConfiguration. Path exclusion lives in the filter itself via shouldNotFilter(HttpServletRequest)"
    - "Heavy DOC-03 commentary in OtelSdkConfiguration.java (137 comment lines / 214 total) — sampler tradeoff (multi-paragraph), env-var contract, per-service-duplication rationale, semconv-incubating-mandatory note, BSP production-shape, propagators-wired-but-not-yet-exercised. The comments ARE the deliverable — workshop attendees read this file top-to-bottom in their IDE"

key-files:
  created:
    - "producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (214 lines, 137 comment lines)"
    - "producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java (123 lines)"
  modified: []

key-decisions:
  - "Used @Bean(destroyMethod=\"close\") on the OpenTelemetry bean (NOT explicit @PreDestroy) — Spring's lifecycle management auto-invokes close() on context shutdown; chosen because OpenTelemetrySdk.close() already cascades to all providers via shutdown().join(10s). Verified by clean shutdown in 1s during smoke test."
  - "Used .build() (not .buildAndRegisterGlobal()) on OpenTelemetrySdk — D-02 demands constructor injection of the OpenTelemetry/Tracer beans; GlobalOpenTelemetry is never read; skipping global registration also keeps Phase 6 test isolation simple."
  - "Used shouldNotFilter() (not FilterRegistrationBean.setUrlPatterns) for /actuator/* exclusion — RESEARCH §A8 confirmed setUrlPatterns has known issues on Spring Boot 3.4 (spring-boot#38331); shouldNotFilter is the canonical OncePerRequestFilter exclusion hook."
  - "Used 7 HTTP semconv attrs (TRACE-05's mandated 3 + 4 Claude's-discretion additions) — HTTP_REQUEST_METHOD + URL_PATH + URL_SCHEME + SERVER_ADDRESS + SERVER_PORT + HTTP_ROUTE pre-chain; HTTP_RESPONSE_STATUS_CODE post-chain. Stopped short of body sizes/user-agent (recommended-not-required would add noise without adding pedagogy)."
  - "Did NOT add @Order to HttpServerSpanFilter — no competing filters in this demo (no Spring Security, no CORS); default Spring Boot ordering is correct. Plan's Claude's-discretion line 113 confirmed."
  - "Did NOT read OTEL_EXPORTER_OTLP_PROTOCOL env var — D-12 says it's informational; we explicitly choose OtlpGrpcSpanExporter (gRPC) and document the redundancy in a code comment for any team that later switches to autoconfigure."

patterns-established:
  - "Per-service-duplication of OtelSdkConfiguration (DOC-05): the IDENTICAL file lives in consumer-service/.../config/OtelSdkConfiguration.java (Plan 02-03) with only two strings flipped (service.name and tracer scope). The file structure established here — 4 builder calls (Resource, OtlpGrpcSpanExporter, BSP, Sampler), composite propagators, @Bean(destroyMethod=close), @Bean Tracer, [@Bean HttpServerSpanFilter — producer-only] — is the template the consumer mirrors."
  - "Manual env-var read with Optional.orElse(default) — the env-var contract is visible in code below the @Bean openTelemetry() method. Phase 4 + Phase 5 reuse the same pattern when wiring meter and logger providers."
  - "D-01 inline span template applied at the SERVER-span site — 4 more applications follow in Plans 02-04 / 02-05 (INTERNAL × 2, PRODUCER, CONSUMER). The shape is identical at every site; the only variation is .setSpanKind() and the attr set."
  - "Stable-vs-incubating semconv coordinate split — io.opentelemetry.semconv.* for HTTP/URL/Server/Service constants (1.40.0); io.opentelemetry.semconv.incubating.* for Deployment + Messaging (1.40.0-alpha). Workshop attendees see the rationale spelled out in the OtelSdkConfiguration class-level Javadoc."

requirements-completed: [TRACE-01, TRACE-02, TRACE-03, TRACE-04, TRACE-05, DOC-03]

# Metrics
duration: 9min
completed: 2026-05-01
---

# Phase 2 Plan 02: Producer SDK Config Summary

**Manual OpenTelemetrySdk wiring for producer-service via OtelSdkConfiguration.java (heavy DOC-03 textbook comments, 137 comment lines / 214 total) + HttpServerSpanFilter.java that wraps every non-/actuator HTTP request in a SERVER span using the D-01 inline template with 7 HTTP semconv attributes — producer starts cleanly, POST /orders generates a trace visible in Tempo with service.name=order-producer, /actuator/health is excluded, and graceful shutdown flushes the BSP queue in 1 second.**

## Performance

- **Duration:** ~9 min (537 s)
- **Started:** 2026-05-01T16:54:23Z
- **Completed:** 2026-05-01T17:03:20Z
- **Tasks:** 3 (T1 OtelSdkConfiguration.java; T2 HttpServerSpanFilter.java; T3 smoke-test the SDK boots)
- **Files created:** 2 (`OtelSdkConfiguration.java` + `HttpServerSpanFilter.java`)
- **Files modified:** 0

## Accomplishments

- **Manual OpenTelemetrySdk wired in producer-service.** `OtelSdkConfiguration.java` exposes three beans: `OpenTelemetry` (with `destroyMethod="close"` for graceful flush), `Tracer` (instrumentation scope `com.example.producer`), and `HttpServerSpanFilter` (factory wired by `OtelSdkConfiguration`). The SDK builds via `OpenTelemetrySdk.builder().setTracerProvider(...).setPropagators(...).build()` — NOT `.buildAndRegisterGlobal()` (D-02 + RESEARCH §A1).
- **Resource correctly identifies the service.** `Resource.getDefault().merge(custom)` carries `ServiceAttributes.SERVICE_NAME=order-producer`, `SERVICE_NAMESPACE=ose-otel-demo`, `SERVICE_INSTANCE_ID=UUID.randomUUID()`, and `DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME=workshop`. The `unknown_service:java` default is overridden because `merge(other)` puts `other` last (RESEARCH §A3 — PITFALLS.md #3 mitigated).
- **Exporter pipeline production-shape.** `BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().setEndpoint(System.getenv).orElse(http://localhost:4317).build()).build()` — manual env-var read (D-12, no autoconfigure), endpoint string starts with `http://` (RESEARCH §A5 — PITFALLS.md #8 mitigated), default BSP tuning (5s/2048/512 — RESEARCH §A4).
- **Sampler explicit with teaching comment.** `Sampler.parentBased(Sampler.alwaysOn())` plus a 14-line teaching comment explaining (a) what `parentBased(alwaysOn())` does, (b) what `Sampler.parentBased(Sampler.traceIdRatioBased(0.1))` would change for production, (c) why `parentBased` matters for distributed traces — addresses PITFALLS.md #6.
- **Composite W3C propagators wired now (D-16).** `ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()))`. Phase 2 doesn't EXERCISE them — producer and consumer are deliberately in different traces — but the SDK is ready: Phase 3's `TracingMessagePostProcessor` reuses these via `openTelemetry.getPropagators().getTextMapPropagator()` rather than constructing new propagators.
- **HTTP SERVER-span filter (TRACE-05) live.** `HttpServerSpanFilter extends OncePerRequestFilter` — `shouldNotFilter()` short-circuits paths starting `/actuator/` (D-06); `doFilterInternal()` wraps `chain.doFilter(...)` in the D-01 inline template with all 7 HTTP semconv attrs (`HTTP_REQUEST_METHOD`, `URL_PATH`, `URL_SCHEME`, `SERVER_ADDRESS`, `SERVER_PORT`, `HTTP_ROUTE` pre-chain; `HTTP_RESPONSE_STATUS_CODE` post-chain). All semconv constants from the stable `io.opentelemetry.semconv.*` package — HTTP semconv is stable in 1.40.0, no `-incubating` needed for this filter.
- **DOC-03 textbook density achieved (>= 40 comment lines required, delivered 137).** Class-level Javadoc explains why duplicated per service, why no autoconfigure, why semconv-incubating; bean-level inline comments explain destroyMethod=close graceful shutdown cascade, Resource merge order, OTLP endpoint format requirement, BSP defaults rationale, sampler tradeoff (multi-paragraph), composite propagators forward-compat note. Workshop attendees read this file top-to-bottom in their IDE.
- **Producer starts in 0.913s, POST /orders → 202, GET /actuator/health → 200, NO `unknown_service:java` anywhere.** Smoke test (T3) confirmed: producer started cleanly, no OTel-related startup errors (no `NoSuchMethodError` / `NoClassDefFoundError` / `IllegalArgumentException.*opentelemetry` / `unknown_service:java`), 1 trace visible in Tempo with `rootServiceName=order-producer` / `rootTraceName=POST /orders`, 0 actuator traces (D-06 verified), graceful shutdown latency = 1s (well inside the 10s `shutdown().join()` window — TRACE-04 confirmed).
- **Phase 2 invariant preserved.** `mise run verify:bom` still exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`

## Task Commits

Each task was committed atomically using `git commit --no-verify` (worktree mode; orchestrator validates hooks once after all parallel agents complete):

1. **Task 1: Add producer OtelSdkConfiguration with DOC-03 textbook comments** — `4c758c2` (feat)
2. **Task 2: Add producer HttpServerSpanFilter with D-01 inline SERVER span** — `a057034` (feat)
3. **Task 3: Smoke-test the SDK boots (verification only — no source changes)** — no commit (T3 produces no source diff per the plan's `<files>(none — verification only)</files>`)

**Plan metadata commit:** _(produced after this SUMMARY.md is written; will commit SUMMARY only — STATE.md and ROADMAP.md are owned by the orchestrator per the worktree contract)_

## Files Created/Modified

- **`producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`** (created — 214 lines, 137 comment lines) — `@Configuration` exposing `@Bean(destroyMethod="close") OpenTelemetry openTelemetry()` (the full SDK wiring: Resource via `getDefault().merge`, BSP wrapping `OtlpGrpcSpanExporter`, `Sampler.parentBased(alwaysOn)`, composite W3C propagators), `@Bean Tracer tracer(OpenTelemetry)` (scope `com.example.producer`), and `@Bean HttpServerSpanFilter httpServerSpanFilter(Tracer)`. Heavy DOC-03 comments throughout — sampler multi-paragraph tradeoff, env-var contract, per-service-duplication rationale, semconv-incubating mandatory note, BSP production-shape rationale, propagators-wired-but-not-yet-exercised note. `DEFAULT_OTLP_ENDPOINT` constant defined as `"http://localhost:4317"` for the `Optional.orElse` fallback.
- **`producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`** (created — 123 lines) — `extends OncePerRequestFilter` (NOT `@Component` — registered by `OtelSdkConfiguration`'s `@Bean` factory). Constructor injects `Tracer` (final field). `shouldNotFilter(HttpServletRequest)` returns `true` for paths starting `/actuator/` (D-06). `doFilterInternal(req, res, chain)` wraps `chain.doFilter` in the D-01 inline template: `tracer.spanBuilder("METHOD path").setSpanKind(SpanKind.SERVER)` + 6 attrs pre-chain; `try (Scope = span.makeCurrent())` body; `span.setAttribute(HTTP_RESPONSE_STATUS_CODE, response.getStatus())` after the chain; `catch (RuntimeException | ServletException | IOException e)` records exception + sets ERROR status + rethrows (D-03); `finally span.end()`. Imports use `jakarta.servlet.*` (Spring Boot 3.x), NOT `javax.servlet.*`.

## File Tree (producer-service config package)

```
producer-service/src/main/java/com/example/producer/config/
├── HttpServerSpanFilter.java         (NEW in 02-02 — 123 lines)
├── OtelSdkConfiguration.java         (NEW in 02-02 — 214 lines, 137 comment lines)
└── RabbitConfig.java                 (Phase 1 — unchanged)
```

## Verification Gate Output

### `mvn -pl producer-service compile` — BUILD SUCCESS

```
[INFO] --- enforcer:3.5.0:enforce (enforce) @ producer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO]
[INFO] --- compiler:3.13.0:compile (default-compile) @ producer-service ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 7 source files with javac [debug target 17] to target/classes
[INFO] BUILD SUCCESS
```

(7 source files = the 5 Phase 1 files + 2 new Phase 2 files. The forward reference from `OtelSdkConfiguration`'s `@Bean HttpServerSpanFilter` factory closed by Task 2.)

### Smoke-test cycle (Task 3)

```
=== Started ProducerApplication line ===
2026-05-01T13:00:44.239-04:00 INFO 2546461 --- [order-producer] [main]
  c.example.producer.ProducerApplication : Started ProducerApplication in 0.913 seconds
  (process running for 1.016)

=== /actuator/health ===
GET /actuator/health: HTTP 200
{"status":"UP"}

=== POST /orders ===
POST /orders: HTTP 202
Response body: {"orderId":"9766c44a-f8c8-42f6-8339-283770e78a57"}
```

### Tempo trace count (via Grafana datasource proxy)

`localhost:3200` is NOT exposed externally by `grafana/otel-lgtm:0.26.0` (only `:3000`, `:4317`, `:4318` are bound). Used the Grafana datasource proxy fallback the plan anticipated (Step 5):

```
$ curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/search?tags=service.name%3Dorder-producer&limit=10" \
    | python3 -c "..."
Found 1 traces
  traceID=85419879abc4dd7a9270b0b376d4e4f5 root=order-producer/POST /orders duration=4ms
```

(`rootServiceName=order-producer` confirms TRACE-02 — Resource correctly carries `ServiceAttributes.SERVICE_NAME=order-producer`. `rootTraceName=POST /orders` confirms TRACE-05 — `HttpServerSpanFilter` named the span correctly per `method + " " + path` (D-04 spec for SERVER spans). `durationMs=4` confirms the SERVER span captures the full request lifecycle.)

### D-06 actuator exclusion verified

```
$ curl -s -u admin:admin "http://localhost:3000/api/datasources/proxy/uid/tempo/api/search?q=%7Bname%3D~%22.*actuator.*%22%7D&limit=10"
{"traces":[],"metrics":{"inspectedBytes":"136065","completedJobs":3,"totalJobs":3}}

PASS: 0 actuator traces (D-06 exclusion verified)
```

### TRACE-04 graceful shutdown verified

```
2026-05-01T13:02:45.337-04:00 INFO ... o.s.b.w.e.tomcat.GracefulShutdown :
  Commencing graceful shutdown. Waiting for active requests to complete
2026-05-01T13:02:45.339-04:00 INFO ... o.s.b.w.e.tomcat.GracefulShutdown :
  Graceful shutdown complete

PID exited at iteration 2 (1s)
Shutdown latency: 1s
```

(Well inside the 10s `shutdown().join(10s)` window from `OpenTelemetrySdk.close()` per RESEARCH §A7 — PITFALLS.md #4 mitigated.)

### `mise run verify:bom` — Plan 02-01 invariant preserved

```
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

### DOC-03 comment-line gate

```
$ grep -cE '^\s*(//|\*|/\*\*|\*/)' \
    producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
137
```

(Plan must_haves required `>= 40`; delivered 137. The comments ARE the deliverable per DOC-03 — workshop attendees read this file line-by-line in their IDE.)

## Decisions Made

- **Used `@Bean(destroyMethod="close")` on the OpenTelemetry bean** — chosen over an explicit `@PreDestroy` because Spring's lifecycle management auto-invokes `close()` on context shutdown, AND `OpenTelemetrySdk.close()` already cascades to all providers via `shutdown().join(10s)`. Verified by graceful shutdown latency = 1s during smoke test.
- **Used `.build()` (not `.buildAndRegisterGlobal()`)** on `OpenTelemetrySdk.builder()` — D-02 demands constructor injection of the `OpenTelemetry`/`Tracer` beans; `GlobalOpenTelemetry` is never read in this codebase. Skipping global registration also keeps Phase 6 test isolation simple (each test can construct its own SDK without collisions).
- **Used `shouldNotFilter()` (not `FilterRegistrationBean.setUrlPatterns`)** for `/actuator/*` exclusion — RESEARCH §A8 confirmed `setUrlPatterns` has known issues on Spring Boot 3.4 (`spring-boot#38331`); `shouldNotFilter` is the canonical `OncePerRequestFilter` exclusion hook. Class-level Javadoc spells out the rationale for workshop attendees.
- **Used 7 HTTP semconv attrs (TRACE-05's mandated 3 + 4 Claude's-discretion additions)** — `HTTP_REQUEST_METHOD` + `URL_PATH` + `URL_SCHEME` + `SERVER_ADDRESS` + `SERVER_PORT` + `HTTP_ROUTE` pre-chain; `HTTP_RESPONSE_STATUS_CODE` post-chain. Stopped short of body sizes / user-agent (recommended-not-required would add noise without adding pedagogy).
- **Did NOT add `@Order` to `HttpServerSpanFilter`** — no competing filters in this demo (no Spring Security, no CORS); default Spring Boot ordering is correct. Plan's Claude's-discretion line 113 confirmed.
- **Did NOT read `OTEL_EXPORTER_OTLP_PROTOCOL` env var** — D-12 says it's informational; we explicitly choose `OtlpGrpcSpanExporter` (gRPC) and document the redundancy in a code comment for any team that later switches to autoconfigure. mise.toml line 23 sets it but Phase 2's code does not depend on it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Bypassed `mise run dev:producer` task chain because `infra:up` failed on existing-container conflict during smoke-test (Task 3).**

- **Found during:** Task 3 (smoke-test the SDK boots).
- **Issue:** `mise run dev:producer` declares `depends = ["infra:up"]`, and `infra:up` runs `docker compose up -d --wait`. A parallel worktree agent (Wave 2 sister `02-03-consumer-sdk-config`) had brought the same containers up earlier, so my worktree's `docker compose up` got `Error response from daemon: Conflict. The container name "/ose-otel-lgtm" is already in use`. The mise task chain treats infra:up's exit code as fatal and never starts the producer.
- **Root cause:** Worktree-level `docker-compose.yml` uses fixed container names (`ose-otel-lgtm`, `ose-otel-rabbitmq`); parallel worktree execution with shared infra is a known race.
- **Fix:** Bypassed the mise task chain by invoking maven directly inside the mise environment: `nohup mise x -- mvn -pl producer-service spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080" > /tmp/producer-02-02.log 2>&1 &`. mise's `[env]` block (loaded automatically) provided `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`, `SPRING_RABBITMQ_HOST=localhost`, etc. The infra was confirmed healthy externally (`docker ps` showed both containers `(healthy)`; `nc -zv localhost 4317` succeeded; `nc -zv localhost 5672` succeeded).
- **Files modified:** None (this was a runtime-orchestration workaround for the smoke test; no source diff). Documented in this SUMMARY.
- **Mid-test infra reset:** During the first smoke attempt the infra containers were removed by something external (likely the parallel worktree's `infra:up` was retrying with `docker compose down` first, or mise wiped them). Both `localhost:5672` and `localhost:4317` started returning `Connection refused`, the first POST /orders returned 500 (`AmqpConnectException`), and `/actuator/health` returned 503 (`{"status":"DOWN"}`). I called `mise run infra:up` directly (which now succeeded because the containers were absent), waited for both to report `healthy`, then re-ran the smoke flow against the same already-running producer JVM. Second attempt: `/actuator/health` → 200 / `{"status":"UP"}`, `POST /orders` → 202 / `{"orderId":"9766c44a-f8c8-42f6-8339-283770e78a57"}`, Tempo search returned 1 trace.
- **Verification:** `Started ProducerApplication in 0.913 seconds`, `mvn -pl producer-service compile` BUILD SUCCESS, Tempo search returned 1 trace with `rootServiceName=order-producer`, graceful shutdown in 1s (well inside 10s join window).
- **Committed in:** N/A — runtime workaround only, no code change.
- **Out-of-scope deferral:** The fact that `mise run dev:producer` and `mise run infra:up` race against parallel worktrees is a multi-worktree orchestration concern, not an OtelSdkConfiguration concern. Out of scope per the deviation rules' SCOPE BOUNDARY clause; if it becomes a recurring blocker the orchestrator should consider per-worktree container name suffixes or a shared-infra mode. Logged as a candidate deferred item; not adding to deferred-items.md unless requested.

---

**Total deviations:** 1 (Rule 3 — Blocking, runtime-workaround only, no code change).
**Impact on plan:** No scope change, no architectural change, no new dependencies, no source-code modification. The plan executed exactly as written; only the runtime invocation pattern of `mvn spring-boot:run` was changed for one smoke-test attempt to work around a parallel-worktree container-name race. All 6 success criteria of the plan are green.

## Issues Encountered

- **Worktree mise.toml not trusted on first invocation.** First `mise x -- mvn` call failed with `Config files in ... are not trusted. Trust them with mise trust.` Resolved with `mise trust /home/coto/dev/demo/ose-otel-demo/.claude/worktrees/agent-ad7ed644642d6ba1f/mise.toml`. Single one-shot fix; no recurrence.
- **Tempo's HTTP API port `:3200` is NOT exposed externally** by `grafana/otel-lgtm:0.26.0` — only `:3000` (Grafana), `:4317` (OTLP gRPC), and `:4318` (OTLP HTTP) are bound to the host. The plan's Step 5 anticipated this and provided the Grafana datasource proxy fallback (`http://localhost:3000/api/datasources/proxy/uid/tempo/api/search...` with `admin/admin`); used that fallback successfully.
- **First smoke-test attempt failed** because the infra containers were torn down mid-test (likely by a parallel worktree's `mise run infra:up` retrying the conflict). The producer JVM was already running but RabbitMQ/OTLP-collector were unreachable for ~30s. Resolved by re-running `mise run infra:up` (fresh slate, no conflict because containers were truly absent), then re-issuing `/actuator/health` + `POST /orders` against the same producer JVM. Second attempt clean.

## User Setup Required

None — no external services, no secrets, no environment variables. The new Java sources compile and run via the existing `mvn` + `mise` toolchain. Workshop attendees running this checkpoint will see:
- `mise run dev:producer` brings infra up + starts the producer JVM
- `mise run demo:order` (or curl) returns 202 with an `orderId`
- Grafana → Tempo → Search with `service.name=order-producer` shows 1 trace per POST

The two new Java files are the readable deliverable; nothing to install.

## Threat Flags

None new. Plan 02-02's threat register (T-2-02-01 through T-2-02-07) covered HTTP filter surface (chain exception handling, status code attribution), env-var trust boundary (OTLP endpoint default + override semantics), and the destroyMethod=close DoS bound. All `mitigate` dispositions are honored:

- **T-2-02-01 (Information Disclosure / mis-configured OTLP endpoint):** `DEFAULT_OTLP_ENDPOINT` hardcoded to `"http://localhost:4317"` in `OtelSdkConfiguration` line 64 — fallback prevents accidental "spans go elsewhere" by default. Code comment on lines 56-62 documents the http:// prefix requirement.
- **T-2-02-05 (Tampering / RuntimeException leakage):** Catch block uses `catch (RuntimeException | ServletException | IOException e)` covering all three throwables `chain.doFilter` can produce; each is `recordException()`'d and `setStatus(ERROR)` is called before re-throw (`HttpServerSpanFilter.java` lines 109-118).
- **T-2-02-07 (DoS / OTLP-blocked shutdown):** `OpenTelemetrySdk.close()` calls `shutdown().join(10s)` per RESEARCH §A7 — bounded wait, can't hang Spring forever; the 30s exporter timeout (BSP default) sits inside that 10s window for the *graceful* path. Smoke test verified graceful shutdown latency = 1s.

`accept` dispositions (T-2-02-02 span attr leakage by future PII, T-2-02-03 404 spans, T-2-02-04 actuator-bypass observability gap, T-2-02-06 UUID v4 instance ID) remain unchanged — Phase 2 captures no body/header/query data, the UUID seed is SecureRandom (no security boundary depends on its unpredictability).

No new threat flags — Phase 2 introduces only outbound gRPC to `localhost:4317` (already in scope) and the SERVER-span filter (no new inbound surface, no PII captured).

## Self-Check: PASSED

Verified after writing this SUMMARY (see `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`
- `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java`
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-02-producer-sdk-config-SUMMARY.md`

**Commits (all FOUND in git log):**
- `4c758c2` — `feat(02-02): add producer OtelSdkConfiguration with DOC-03 textbook comments`
- `a057034` — `feat(02-02): add producer HttpServerSpanFilter with D-01 inline SERVER span`

## Next Phase Readiness

- **Plan 02-04 (producer-instrumentation, Wave 3) is unblocked.** `Tracer` bean is constructor-injectable; `OrderService` and `OrderPublisher` can pull it from Spring's DI container. The SERVER span is in place (filter runs on every non-actuator request); INTERNAL + PRODUCER spans land in 02-04 — both follow the D-01 inline template established here.
- **Plan 02-03 (consumer-sdk-config, parallel sibling, Wave 2) — independent.** This plan's deliverable (`OtelSdkConfiguration` template) is duplicated structurally in consumer-service per DOC-05; parallel execution is safe because the two plans modify disjoint file sets.
- **Phase 3 propagator wiring is forward-compatible.** `OpenTelemetrySdk` already has the composite `W3CTraceContextPropagator + W3CBaggagePropagator` registered; Phase 3's `TracingMessagePostProcessor` (in `otel-bootstrap`) will call `openTelemetry.getPropagators().getTextMapPropagator()` rather than constructing a new propagator.
- **Phase 4 (metrics) and Phase 5 (logs) extend `OtelSdkConfiguration` linearly.** Add `SdkMeterProvider` / `SdkLoggerProvider` to the same `@Bean(destroyMethod="close") openTelemetry()` builder chain; the `close()` cascade already handles all three signal providers.
- **Manual-SDK pedagogy preserved across the workshop.** Workshop attendees can `git checkout step-02-traces` (when Plan 02-06 tags it) and read `OtelSdkConfiguration.java` top-to-bottom in their IDE — every SDK call accompanied by a teaching comment, the env-var contract visible in code, no autoconfigure magic.

---
*Phase: 02-manual-sdk-bootstrap-first-traces*
*Plan: 02 (producer-sdk-config)*
*Completed: 2026-05-01*
