---
phase: 02-manual-sdk-bootstrap-first-traces
plan: 03
subsystem: instrumentation
tags: [opentelemetry, sdk, tracer-provider, otlp-grpc, batch-span-processor, parent-based-sampler, w3c-propagators, semconv-1.40, spring-boot-3.4.13, deployment-incubating, consumer-service]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plan 02-01 — five OTel deps (opentelemetry-api/sdk/exporter-otlp BOM-managed via opentelemetry-bom:1.61.0; opentelemetry-semconv 1.40.0 + opentelemetry-semconv-incubating 1.40.0-alpha pinned) on the consumer-service classpath; mise verify:bom enforcing one-version-per-OTel-artifact reactor invariant"
provides:
  - "consumer-service/.../config/OtelSdkConfiguration.java — manually-wired OpenTelemetrySdk Spring @Bean (TRACE-01..04): SdkTracerProvider with parent-based always-on Sampler (D-14), default-tuned BatchSpanProcessor (5s/2048/512 — D-15), OtlpGrpcSpanExporter reading OTEL_EXPORTER_OTLP_ENDPOINT env var with localhost:4317 fallback (D-12), composite W3CTraceContextPropagator + W3CBaggagePropagator (D-16), graceful shutdown via @Bean(destroyMethod=\"close\") (D-15 + TRACE-04)"
  - "Resource (D-13 + TRACE-02): Resource.getDefault().merge(...) with the four required attributes from semconv 1.40.0 constants — SERVICE_NAME=order-consumer, SERVICE_NAMESPACE=ose-otel-demo, SERVICE_INSTANCE_ID=UUID.randomUUID().toString(), DEPLOYMENT_ENVIRONMENT_NAME=workshop. Neutralises the unknown_service:java pitfall."
  - "@Bean Tracer for instrumentation scope com.example.consumer (D-02). Constructor-injectable into Plan 02-05's OrderListener + ProcessingService."
  - "Per-service-duplication ethos (DOC-05) made visible at the codebase level: producer-service AND consumer-service now carry structurally-identical OtelSdkConfiguration.java files differing only by the locked four points listed in must_haves. The JavaDoc cross-reference (consumer's ‟Why duplicated per service?” paragraph names producer-service explicitly) closes the loop."
  - "DOC-03 (consumer half): heavily-commented OtelSdkConfiguration.java — 131 comment lines (>= 40 mandated)."
affects:
  - "02-02-producer-sdk-config (PARALLEL Wave 2 — sibling worktree; planner-asserted structural mirror; orchestrator merges both)"
  - "02-04-producer-instrumentation (Wave 3 — uses producer's Tracer bean; no consumer change)"
  - "02-05-consumer-instrumentation (Wave 3 — constructor-injects consumer's Tracer bean for OrderListener.onOrder + ProcessingService.process spans)"
  - "02-06-readme-and-exit-gate (Wave 4 — DOC-05 callout depends on the visible duplication this plan creates; step-02-traces tag asserts the SDK boots in both services)"
  - "Phase 3 — TracingMessageListenerAdvice will reuse openTelemetry.getPropagators().getTextMapPropagator() (the W3C composite registered here) to extract upstream trace context from AMQP message headers"
  - "Phase 4 metrics — Future SdkMeterProvider follows the same env-var pattern; @Bean(destroyMethod=close) cascades to MeterProvider.shutdown() for free"
  - "Phase 5 logs — Future SdkLoggerProvider + OpenTelemetryAppender install pattern reuses the OpenTelemetry bean exposed here"

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry.api.OpenTelemetry / Tracer / Span / SpanKind / StatusCode (compile-time use limited to OpenTelemetry + Tracer in Plan 02-03; rest land in 02-05)"
    - "io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator (D-16)"
    - "io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator (D-16)"
    - "io.opentelemetry.context.propagation.{ContextPropagators, TextMapPropagator} (D-16 composite construction)"
    - "io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter (TRACE-03)"
    - "io.opentelemetry.sdk.OpenTelemetrySdk (TRACE-01 + TRACE-04 destroy hook)"
    - "io.opentelemetry.sdk.resources.Resource (TRACE-02)"
    - "io.opentelemetry.sdk.trace.{SdkTracerProvider, export.BatchSpanProcessor, export.SpanExporter, samplers.Sampler} (TRACE-03)"
    - "io.opentelemetry.semconv.ServiceAttributes (stable — TRACE-02)"
    - "io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes (incubating — TRACE-02)"
  patterns:
    - "Per-service-duplicated SDK bootstrap (TRACE-01 / DOC-05): identical @Configuration class in each service, differing by exactly four locked points (package, JavaDoc per-service-duplication callout, service.name string, tracer scope). Refactoring into a shared @AutoConfiguration bean would hide one of the two readings the workshop is built around."
    - "Pure manual env-var read (D-12): Optional.ofNullable(System.getenv(\"OTEL_EXPORTER_OTLP_ENDPOINT\")).orElse(DEFAULT_OTLP_ENDPOINT) — no opentelemetry-sdk-extension-autoconfigure, no AutoConfiguredOpenTelemetrySdk, no GlobalOpenTelemetry. The env-var contract is visible IN code; nothing is magic. Phase 4 + Phase 5 reuse this pattern for meter and logger providers."
    - "Graceful shutdown flush via @Bean(destroyMethod=\"close\") (TRACE-04 / D-15): close() → shutdown().join(10s) → tracerProvider.shutdown() → BatchSpanProcessor.worker.shutdown() forces a final flush. Without this, the last 5 seconds of telemetry are silently dropped on Ctrl-C — a textbook OTel pitfall."
    - "Sampler.parentBased(Sampler.alwaysOn()) explicit + multi-paragraph teaching comment (D-14): respects upstream sampling decision via traceparent flag for distributed traces; samples 100% of root spans. Production swap in comment: Sampler.parentBased(Sampler.traceIdRatioBased(0.1))."
    - "Composite W3C trace-context + W3C baggage propagators registered upfront (D-16): wired in Phase 2; exercised in Phase 3 via openTelemetry.getPropagators().getTextMapPropagator()."
    - "NO HttpServerSpanFilter in consumer (D-07): per-service-duplication ethos applies to SDK BOOTSTRAP, not instrumentation surfaces. Consumer's only HTTP surface is /actuator/health, which the producer's filter would have excluded anyway."

key-files:
  created:
    - "consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java — manual OpenTelemetrySdk wiring + Tracer @Bean for instrumentation scope com.example.consumer; 203 lines / 131 comment lines / 0 HttpServerSpanFilter"
  modified: []

key-decisions:
  - "Followed the plan's verbatim code template character-for-character (the action block explicitly states ‟Use the EXACT structure below. All comments are mandatory; do not abbreviate, summarize, or reformat.”). No structural deviations."
  - "Honored D-07 by ABSENCE: the JavaDoc carries an explicit ‟Why no HttpServerSpanFilter here?” paragraph but no @Bean factory — consumer-service has no HttpServerSpanFilter class, so no import to delete. The producer's HttpServerSpanFilter is producer-side only."
  - "Smoke test at consumer JVM PID 2543054 confirmed graceful shutdown in 1s (well under the 12s budget) — TRACE-04 destroyMethod=close cascade is end-to-end working, even though Plan 02-03 emits no spans yet (those land in Plan 02-05). The SimpleMessageListenerContainer + GracefulShutdown lines in /tmp/consumer-02-03.log show the destroy lifecycle running cleanly."

patterns-established:
  - "consumer-service/.../config/OtelSdkConfiguration.java now carries the Phase 2 SDK bootstrap pattern; future plans (Plan 02-05 instrumentation; Phase 4 meter; Phase 5 logger) extend this same @Configuration"
  - "JavaDoc cross-references between paired duplicated files: producer's ‟The IDENTICAL file lives in consumer-service/...” paragraph and consumer's ‟The IDENTICAL file lives in producer-service/...” paragraph make the duplication discoverable from either entry point"

requirements-completed: [TRACE-01, TRACE-02, TRACE-03, TRACE-04, DOC-03]

# Metrics
duration: 6min
completed: 2026-05-01
---

# Phase 2 Plan 03: Consumer SDK Config Summary

**Manual OpenTelemetry SDK wired in consumer-service via a structurally-identical mirror of producer's OtelSdkConfiguration.java — same SdkTracerProvider + BatchSpanProcessor + OtlpGrpcSpanExporter + parent-based-always-on Sampler + composite W3C propagators + graceful-shutdown @Bean(destroyMethod="close"), differing only by the four locked points (package, service.name=order-consumer, tracer scope=com.example.consumer, JavaDoc per-service-duplication callout naming producer); NO HttpServerSpanFilter (D-07) since consumer has no inbound HTTP business surface; consumer JVM boots cleanly with `Started ConsumerApplication in 1.099 seconds`, /actuator/health 200 UP on 8081, and exits in 1s on SIGTERM.**

## Performance

- **Duration:** ~6 min (339 s)
- **Started:** 2026-05-01T16:54:44Z
- **Completed:** 2026-05-01T17:00:23Z
- **Tasks:** 2 (Task 1 wrote the new file; Task 2 was verification-only and produced no commit)
- **Files modified:** 1 (`consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — newly created)

## Accomplishments

- **Consumer's manual OTel SDK wired and proven to boot cleanly.** `mvn -pl consumer-service clean compile` BUILD SUCCESS with `Compiling 5 source files with javac [debug target 17] to target/classes` (RabbitConfig + OrderListener + ProcessingService + ConsumerApplication + the new OtelSdkConfiguration). `mise run dev:consumer` reaches `Started ConsumerApplication in 1.099 seconds` with the `[order-consumer]` thread tag visible — neither `unknown_service:java` nor `IllegalArgumentException: endpoint must start with` appears in the log. Consumer's `/actuator/health` returns `{"status":"UP"}` on port 8081.
- **Per-service-duplication ethos visible at the codebase level (DOC-05).** The consumer's OtelSdkConfiguration.java is a structural mirror of producer's. The mandated four differences:
    1. **Package:** `com.example.consumer.config` (vs `com.example.producer.config`)
    2. **Service name:** `.put(ServiceAttributes.SERVICE_NAME, "order-consumer")` (vs `"order-producer"`)
    3. **Tracer scope:** `openTelemetry.getTracer("com.example.consumer")` (vs `"com.example.producer"`)
    4. **JavaDoc per-service-duplication callout:** points AT producer-service (the OTHER copy of the duplication)
    Plus the structural omission D-07 mandates: NO `@Bean HttpServerSpanFilter` factory, NO `import com.example.consumer.config.HttpServerSpanFilter;` (the class only exists in producer-service). The JavaDoc carries an explicit `Why no HttpServerSpanFilter here?` paragraph that names D-06's actuator-exclusion rationale and the per-service-duplication's surface-vs-bootstrap distinction.
- **TRACE-04 graceful shutdown flush proven end-to-end.** SIGTERM-to-exit measured at 1 second — well under the 12s `close().shutdown().join(10s)` budget. `/tmp/consumer-02-03.log`'s tail shows the canonical Spring Boot graceful-shutdown sequence: `Waiting for workers to finish.` → `Successfully waited for workers to finish.` → `Commencing graceful shutdown.` → `Graceful shutdown complete`. Even though Plan 02-03 emits no spans yet (those land in Plan 02-05), the destroy-lifecycle wiring works.
- **D-12 enforced — no autoconfigure.** No `import` of `AutoConfiguredOpenTelemetrySdk`, no `GlobalOpenTelemetry`, no `opentelemetry-sdk-extension-autoconfigure` runtime dependency. The endpoint env-var contract is visible in code: `Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse(DEFAULT_OTLP_ENDPOINT)`. (The transitive `opentelemetry-sdk-extension-autoconfigure-spi` artifact is harmless — it's the SPI interfaces, not the runtime.)
- **`mise run verify:bom` (Plan 02-01 invariant) preserved.** The new file consumes deps from the existing classpath with no version drift; the Phase 2 invariant of one version per OTel artifact across the reactor still holds: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`

## Task Commits

Each task was committed atomically using `git commit --no-verify` (parallel-executor convention; orchestrator validates hooks once after all agents complete):

1. **Task 1: Write consumer-service/.../config/OtelSdkConfiguration.java — IDENTICAL to producer's with two service-identity strings changed** — `49be1af` (feat)
2. **Task 2: Smoke-test the consumer SDK boots cleanly** — verification-only, no commit (no files produced)

**Plan metadata commit:** _(produced after this SUMMARY.md is written; will commit SUMMARY only — STATE.md / ROADMAP.md / REQUIREMENTS.md are owned by the orchestrator post-merge)_

## Files Created/Modified

- **`consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`** (created, 203 lines, 10 386 bytes, 131 comment lines) — manual OTel SDK config exposing two Spring beans: `OpenTelemetry openTelemetry()` (with `destroyMethod="close"` for TRACE-04 graceful flush) and `Tracer tracer(OpenTelemetry openTelemetry)` returning `openTelemetry.getTracer("com.example.consumer")`. Structurally identical to producer's OtelSdkConfiguration.java (which lands in the parallel Plan 02-02 worktree; orchestrator will merge both) with the four locked differences plus the structural absence of `HttpServerSpanFilter` (D-07).

## Producer-vs-Consumer Diff (synthesized)

> **Note on actual diff:** Plan 02-03 runs in a parallel worktree spawned in Wave 2 alongside Plan 02-02 (producer's OtelSdkConfiguration.java); both Wave-2 worktrees are based on `b759f96` (Wave 1 HEAD), so producer's file isn't visible from this worktree. The orchestrator merges both worktrees back into `main` after Wave 2 completes. The diff below is the **specified** diff — i.e., the differences the action block of Plan 02-03 mandated. The orchestrator's post-merge verifier should re-run `diff producer-service/.../OtelSdkConfiguration.java consumer-service/.../OtelSdkConfiguration.java` against the actual files and confirm the same set of differences.

The mandated differences (and ONLY these) between the two files:

```diff
- package com.example.producer.config;
+ package com.example.consumer.config;

  /**
   * Manual OpenTelemetry SDK bootstrap for [SERVICE]-service (TRACE-01..04 + DOC-03).
-  * ...consumer-service/.../config/OtelSdkConfiguration.java with two changes
-  * only: the service.name string ("order-producer" → "order-consumer") and
-  * the tracer scope name ("com.example.producer" → "com.example.consumer").
+  * ...producer-service/.../config/OtelSdkConfiguration.java with two changes
+  * only: the service.name string ("order-consumer" → "order-producer") and
+  * the tracer scope name ("com.example.consumer" → "com.example.producer").

+  * <p><b>Why no HttpServerSpanFilter here?</b> The consumer-service has
+  *  no inbound HTTP business surface — only /actuator/health, which the
+  *  producer's HttpServerSpanFilter would have excluded anyway (D-06).
+  *  The per-service-duplication ethos applies to the SDK BOOTSTRAP; it
+  *  does NOT apply to instrumentation surfaces, which exist where the
+  *  surface exists (D-07).
+  */

- .put(ServiceAttributes.SERVICE_NAME, "order-producer")
+ .put(ServiceAttributes.SERVICE_NAME, "order-consumer")

- return openTelemetry.getTracer("com.example.producer");
+ return openTelemetry.getTracer("com.example.consumer");

- @Bean
- HttpServerSpanFilter httpServerSpanFilter(Tracer tracer) {
-     return new HttpServerSpanFilter(tracer);
- }
+ (no equivalent — D-07: filter is producer-only)
```

The locked five differences in plan-language order: (1) package; (2) JavaDoc per-service-duplication callout target; (3) service.name string; (4) tracer scope; (5) "Why no HttpServerSpanFilter here?" paragraph (PRESENT in consumer, ABSENT in producer) AND the @Bean HttpServerSpanFilter factory method (ABSENT in consumer, PRESENT in producer).

## Verification Gate Output

### `mvn -pl consumer-service clean compile`

```
[INFO] --- enforcer:3.5.0:enforce (enforce) @ consumer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion passed
[INFO] Rule 2: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
...
[INFO] --- compiler:3.13.0:compile (default-compile) @ consumer-service ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 5 source files with javac [debug target 17] to target/classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  1.159 s
```

### `mise run dev:consumer` startup line (extracted from /tmp/consumer-02-03.log)

```
2026-05-01T12:58:48.454-04:00  INFO 2543054 --- [order-consumer] [           main] c.example.consumer.ConsumerApplication   : Started ConsumerApplication in 1.099 seconds (process running for 1.24)
```

The `[order-consumer]` thread-pool tag (from `spring.application.name`) confirms the consumer is the active service.

### `/actuator/health` on port 8081

```
$ curl -s -w '%{http_code}\n' http://localhost:8081/actuator/health
{"status":"UP"}200
```

### TRACE-04 graceful-shutdown latency

SIGTERM sent → JVM exit polled once per second → exit detected at **1 second** (well under the 12s budget). Final log lines:

```
2026-05-01T12:59:25.846-04:00  INFO ... o.s.a.r.l.SimpleMessageListenerContainer : Waiting for workers to finish.
2026-05-01T12:59:26.455-04:00  INFO ... o.s.a.r.l.SimpleMessageListenerContainer : Successfully waited for workers to finish.
2026-05-01T12:59:26.456-04:00  INFO ... o.s.b.w.e.tomcat.GracefulShutdown        : Commencing graceful shutdown. Waiting for active requests to complete
2026-05-01T12:59:26.457-04:00  INFO ... o.s.b.w.e.tomcat.GracefulShutdown        : Graceful shutdown complete
```

The `OpenTelemetrySdk.close()` cascade (close → shutdown → tracerProvider.shutdown → BatchSpanProcessor.worker.shutdown) sits inside the same Spring destroy-lifecycle that emits these lines; the absence of any OTel-side exception during shutdown confirms the cascade ran cleanly.

### Comment density (DOC-03)

```
$ grep -cE '^\s*(//|\*|/\*\*|\*/)' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
131
```

131 comment lines >> 40 mandated.

### `mise run verify:bom` (Plan 02-01 invariant preservation)

```
[verify:bom] $ set -e
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

### File tree of consumer-service config (after this plan)

```
consumer-service/src/main/java/com/example/consumer/config/
├── OtelSdkConfiguration.java   (10 386 bytes, NEW in 02-03)
└── RabbitConfig.java           (   629 bytes, Phase 1)
```

Two files; matches the producer-service config tree shape (RabbitConfig + OtelSdkConfiguration), but consumer-service intentionally lacks `HttpServerSpanFilter.java` (D-07).

## Decisions Made

- **Followed the plan's verbatim code template character-for-character.** The action block explicitly states "Use the EXACT structure below. All comments are mandatory; do not abbreviate, summarize, or reformat." — no structural reinterpretation.
- **Honored D-07 by absence.** Consumer's `OtelSdkConfiguration` carries the explicit "Why no HttpServerSpanFilter here?" JavaDoc paragraph the planner mandated, but no `@Bean HttpServerSpanFilter` factory. Workshop attendees reading the file see the decision (D-07) called out in JavaDoc, then look at the bean methods and find only `OpenTelemetry openTelemetry()` + `Tracer tracer(...)` — symmetric to producer minus the filter, exactly as the per-service-duplication ethos requires.
- **Did NOT touch producer-service.** Plan 02-02 is running in a parallel sibling worktree; the orchestrator merges both Wave-2 worktrees back into `main` after both complete. This worktree's responsibility is consumer-only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking grep acceptance criteria collide with the plan's verbatim code template]**

- **Found during:** Task 1 acceptance-criteria validation
- **Issue:** The acceptance criteria block of `<task id="02-03-T1">` includes literal greps that conflict with the verbatim code template the same task block mandates:
    - `grep -c '\.buildAndRegisterGlobal()' ... returns 0` — but the JavaDoc on the `openTelemetry()` bean carries the explicit teaching line `Note: we call .build(), NOT .buildAndRegisterGlobal()`, which the grep matches once.
    - `grep -c '"order-consumer"' ... returns 1` — but the JavaDoc per-service-duplication callout names both strings: `the service.name string ("order-consumer" → "order-producer")`. Counting the JavaDoc reference + the actual `.put(...)` call yields 2.
    - `! grep -F '"order-producer"' ... exits 0` — fails for the same reason: the JavaDoc carries `"order-producer"` as a literal pedagogical reference.
    - `! grep -F 'getTracer("com.example.producer")' ... exits 0` — passes (the JavaDoc says `("com.example.consumer" → "com.example.producer")` without the `getTracer(...)` call shape, so the `-F` literal grep doesn't match).
- **Root cause:** Per the plan, the JavaDoc text deliberately includes the producer-side identity strings as pedagogical references (the per-service-duplication callout names "the OTHER copy"). The grep-based acceptance criteria assume the identity strings live ONLY in code, which contradicts the verbatim template.
- **Resolution:** Followed the verbatim code template (the action block explicitly states "Use the EXACT structure below. All comments are mandatory; do not abbreviate, summarize, or reformat.") — the template is the source of truth. The semantic intent of each conflicting grep IS satisfied:
    - **No actual call to `.buildAndRegisterGlobal()`** — `grep -n 'buildAndRegisterGlobal' OtelSdkConfiguration.java` returns one line (line 85: a JavaDoc comment, not code).
    - **No actual code reference to `"order-producer"` or `getTracer("com.example.producer")`** — the only `order-producer` reference is line 35 (JavaDoc), and `getTracer(...)` is called once on line 201 with the consumer scope.
- **Files modified:** None — the verbatim template was kept; the plan's grep criteria are noted as inconsistent with the same plan's mandated code.
- **Verification:** Manual line-by-line inspection (above), `mvn -pl consumer-service clean compile` BUILD SUCCESS, `mise run dev:consumer` reaches `Started ConsumerApplication`. The intent of every grep criterion is satisfied semantically.
- **Committed in:** `49be1af` (Task 1 commit) — no fix commit because no fix was needed; the plan's code template was already correct.

---

**Total deviations:** 1 documented (Rule 3 inconsistency between the plan's grep acceptance criteria and the same plan's verbatim code template; resolved by following the explicit "Use the EXACT structure below" directive).
**Impact on plan:** Zero scope change, zero code change, zero new dependencies. The deliverable matches the plan's verbatim template exactly. The semantic intent of every grep criterion (no actual code call to producer-side identities, no actual code call to `buildAndRegisterGlobal`) is preserved; only the literal grep counts (which fold JavaDoc text in) deviate. Future plans should phrase such acceptance greps as "no actual code call to" rather than "literal string absent" to avoid the same trap.

## Issues Encountered

- **Initial mise toolchain trust prompt.** First `mise x -- mvn ...` invocation in this worktree failed with `ERROR error parsing config file: ... Config files in ... are not trusted.`. Resolved by `mise trust /home/coto/dev/demo/ose-otel-demo/.claude/worktrees/agent-acef50d2c37b698fc/mise.toml`. This is a standard worktree-onboarding step and not a plan defect.
- **Shell-self-match in `pgrep`.** Initial `pgrep -f spring-boot:run` matched the bash command-line that contained `spring-boot:run` as a literal string in the argv — false positive. Resolved by switching to `ps -ef | grep -E ... | grep -v grep | grep -v '/bash -c'` for the leftover-process check. No actual leftover processes existed.

## User Setup Required

None — no external services, no new secrets, no environment variables. The new SDK config reads `OTEL_EXPORTER_OTLP_ENDPOINT` from the environment but mise.toml line 22 already pre-wires this to `http://localhost:4317` (Phase 1 baseline). Workshop attendees running `mise run dev:consumer` get the right endpoint without any extra setup.

## Threat Flags

None new. Plan 02-03's threat register (T-2-03-01 through T-2-03-06) was honored:

- **T-2-03-01 (information disclosure via OTEL_EXPORTER_OTLP_ENDPOINT)** — `mitigate` disposition met: `DEFAULT_OTLP_ENDPOINT` is hardcoded to `http://localhost:4317`; the explicit fallback prevents accidental redirect. Same control as producer.
- **T-2-03-02 (span attributes leaking sensitive payload data)** — `accept` disposition: Plan 02-03 wires the SDK only; no spans emit yet. Plan 02-05 will emit only span name + messaging semconv attrs (system / destination / operation_type / routing_key), NOT message body or `orderId`.
- **T-2-03-03 (UUID v4 for service.instance.id)** — `accept`: same as producer T-2-02-06, fine for an instance identifier.
- **T-2-03-04 (DoS on shutdown via slow OTLP backend)** — `mitigate`: `close().shutdown().join(10s)` bound; **verified end-to-end at 1s** (well inside the 10s join window).
- **T-2-03-05 (no HttpServerSpanFilter on /actuator/health → no Tempo trace of probes)** — `accept`: D-07 explicit; actuator allow-list (Phase 1) restricts to `/health` only.
- **T-2-03-06 (CONSUMER spans starting from `Context.root()` accept "implicit parent" from broker)** — `accept`: this IS the Phase 2 broken-then-fixed pedagogy; lands in Plan 02-05.

No new threat surface introduced by this plan beyond what the threat register documented. No `threat_flag:` markers needed.

## Self-Check: PASSED

Verified after writing this SUMMARY (per `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-03-consumer-sdk-config-SUMMARY.md` (this file — committed below)

**Commits (FOUND in git log):**
- `49be1af` — feat(02-03): wire consumer manual OTel SDK in OtelSdkConfiguration.java

## Next Phase Readiness

- **Wave 3 plan 02-05-consumer-instrumentation is unblocked.** The `Tracer` bean for instrumentation scope `com.example.consumer` is now constructor-injectable from Spring's DI container; Plan 02-05's `OrderListener.onOrder(...)` and `ProcessingService.process(...)` can pull it directly. The CONSUMER + INTERNAL spans land there.
- **Phase 3 propagator extraction is unblocked at the SDK side.** The composite W3CTraceContextPropagator + W3CBaggagePropagator is wired via `OpenTelemetrySdk.builder().setPropagators(...)`. Phase 3's `TracingMessageListenerAdvice` will call `openTelemetry.getPropagators().getTextMapPropagator()` rather than constructing a new propagator — symmetry with the producer-side `TracingMessagePostProcessor` is preserved.
- **Phase 4 (metrics) and Phase 5 (logs) inherit a working `@Bean(destroyMethod="close")` cascade.** Adding `SdkMeterProvider` and `SdkLoggerProvider` in those phases does NOT need to revisit the shutdown semantics — `OpenTelemetrySdk.close()` already cascades to all three providers per RESEARCH §A7.
- **DOC-05 callout (Plan 02-06) is now drafting against a real codebase.** Two structurally-identical OtelSdkConfiguration.java files now exist; the README's "Why is OtelSdkConfiguration.java duplicated?" paragraph in Plan 02-06 has concrete files to point at.
- **Wave 2 sibling (Plan 02-02-producer-sdk-config) is in a parallel worktree and unobserved from here.** The orchestrator merges both Wave-2 worktrees back to `main` after both complete. The post-merge `step-02-traces` tag (Plan 02-06 / WORK-01) will assert both files are present and structurally aligned.

---
*Phase: 02-manual-sdk-bootstrap-first-traces*
*Plan: 03 (consumer-sdk-config)*
*Completed: 2026-05-01*
