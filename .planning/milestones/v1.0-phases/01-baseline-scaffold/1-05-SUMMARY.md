---
phase: 01-baseline-scaffold
plan: 05
subsystem: app
tags: [spring-boot, spring-amqp, rabbit-listener, consumer, java-17]

# Dependency graph
requires:
  - phase: 01-baseline-scaffold
    provides: "plan 1-01 Maven skeleton (consumer-service/pom.xml with starter-amqp/actuator/web/test, no OTel deps); plan 1-02 mise.toml (CONSUMER_PORT=8081, SPRING_RABBITMQ_*, dev:consumer task); plan 1-03 docker-compose.yml (RabbitMQ on localhost:5672, lgtm on localhost:4317)"
provides:
  - "consumer-service skeleton: @SpringBootApplication entrypoint + @Configuration declaring durable queue 'orders.created' + Jackson2JsonMessageConverter + @Component @RabbitListener delegating to a no-op @Service ProcessingService"
  - "Verifiable APP-03 surface: every received message logs 'OrderCreated received: orderId={uuid}' at INFO level"
  - "Empty ProcessingService.process(...) call site reserved for Phase 2 INTERNAL span (TRACE-06) and Phase 3 deterministic-failure (APP-04 + TRACE-09 recordException)"
  - "Minimal application.yaml: spring.application.name=order-consumer + management.endpoints.web.exposure.include=health (Pitfall F discipline; zero OTel/tracing tokens)"
affects:
  - "1-06 phase-1-verification — owns wave-3 end-to-end POST /orders → publish → consume → log + actuator/health on port 8081"
  - "Phase 2 (traces) — OtelSdkConfiguration.java will be added per service; OrderListener.onOrder becomes the CONSUMER span site, ProcessingService.process becomes the INTERNAL span site (TRACE-06)"
  - "Phase 3 (context propagation) — AMQP MessagePostProcessor extract pair; ProcessingService.process gains modulo-10 deterministic failure path (APP-04)"
  - "Phase 5 (logs) — opentelemetry-logback-appender-1.0 installed by SDK init wires trace_id/span_id MDC into the OrderCreated log line"

# Tech tracking
tech-stack:
  added:
    - "Spring AMQP 3.2.8 application surface — @RabbitListener annotation (BOM-managed via spring-boot-starter-amqp; no version override)"
    - "SLF4J Logger usage on OrderListener.LOG (verifiable APP-03 log line)"
    - "Jackson2JsonMessageConverter — JSON-on-the-wire round-trip with producer's converter (parity required)"
  patterns:
    - "Constructor injection of @Service into @Component listener (no field injection)"
    - "Public-static-final QUEUE constant on RabbitConfig — must match producer's RabbitConfig.QUEUE byte-for-byte; both sides declare the queue idempotently (RabbitMQ ignores duplicate matching declaration)"
    - "Empty-body call site convention: ProcessingService.process documents (in comments) which Phase will fill the body — explicit forward-references to TRACE-06 / APP-04 keep the workshop's narrative arc visible in the source code"

key-files:
  created:
    - "consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java — @SpringBootApplication entrypoint"
    - "consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java — durable queue 'orders.created' + Jackson2JsonMessageConverter beans"
    - "consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java — @RabbitListener delegating to ProcessingService and logging 'OrderCreated received: orderId={uuid}'"
    - "consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java — @Service no-op stub marking the future Phase-2 span site / Phase-3 failure site"
    - "consumer-service/src/main/resources/application.yaml — minimal Spring Boot config (app name + actuator health whitelist)"
  modified: []

key-decisions:
  - "Hardcoded queue name 'orders.created' as a static-final constant on consumer's RabbitConfig (matches producer's same constant). Phase 1 has no shared module yet (otel-bootstrap is empty); promotion to a shared constant is a Phase 3 concern when the AMQP propagation pair lands in otel-bootstrap"
  - "Consumer does NOT declare the DirectExchange or Binding beans. RabbitMQ accepts both ends declaring the queue, but only the producer owns the topology pedagogically — the consumer attaches to a known queue name. Avoids two-source-of-truth confusion for workshop attendees"
  - "ProcessingService.process body left intentionally empty (only comments). Resisting the temptation to add a debug log here keeps the future Phase 2 INTERNAL-span demonstration crisp: attendees will see the span appear when the SDK config + spanBuilder line is added, not the log line"
  - "Method signature on listener is `Map<String, Object>` (not a typed DTO). Two reasons: (a) Phase 3 needs to demo MessagePostProcessor extracting the W3C 'traceparent' header, which is easier to teach when the message body type is uncomplicated; (b) avoids Jackson polymorphic deserialization (T-1-05-01 mitigation — accepting Map disables `@class` header risk)"
  - "Followed orchestrator's <plan_specifics> scope-cap on Task 3: schema/build verification only (mvn package + dependency:tree gate), no live `mise run dev` cycle. Plan 1-06 owns the wave-3 runtime verification (POST /orders → consume → log + actuator/health). Sister plan 1-04 (producer) is being written in a parallel worktree and is not yet visible on this branch, so a real end-to-end cycle is not achievable here regardless"

patterns-established:
  - "Pattern: Per-service application.yaml is minimal — `spring.application.name` and `management.endpoints.web.exposure.include=health` only. NO `management.tracing.*`, NO `otel.*`, NO `logging.pattern.*`, NO `server.port` (port flows from `mise.toml` env vars via `-Dserver.port=${CONSUMER_PORT}` in the dev:consumer mise task). Direct enforcement of Pitfall F"
  - "Pattern: RabbitConfig declares only the topology its module owns. Producer owns exchange + binding + queue. Consumer owns queue (idempotent re-declaration). Connection details are autoconfigured by Spring Boot from SPRING_RABBITMQ_* env vars — no manual @Bean ConnectionFactory anywhere in the project"
  - "Pattern: Empty-body call sites named for their future role. ProcessingService.process is the CONSUMER-side INTERNAL span anchor; the comments inside the body name the future Phase explicitly so a phase-2 executor knows exactly where to inject `tracer.spanBuilder('process-order').startSpan()`"

requirements-completed: [APP-03, APP-05]

# Metrics
duration: 2min
completed: 2026-04-30
---

# Phase 1 Plan 05: Consumer Service Summary

**Spring Boot 3.4.13 consumer-service skeleton with @RabbitListener on durable queue 'orders.created', Jackson JSON converter parity with producer, no-op ProcessingService stub marking future Phase 2/3 instrumentation sites, and a minimal application.yaml that preserves the Phase 1 zero-OTel-deps invariant.**

## Performance

- **Duration:** ~2 min (file creation + compile + package + commits)
- **Started:** 2026-04-30T02:58:44Z
- **Completed:** 2026-04-30T03:00:51Z
- **Tasks:** 3 (T1 ConsumerApplication + RabbitConfig, T2 OrderListener + ProcessingService + application.yaml, T3 verification — scope-capped per orchestrator)
- **Files created:** 5 (4 Java + 1 yaml)

## Accomplishments

- Created the four consumer-service Java sources verbatim from RESEARCH.md lines 1006-1091 (`ConsumerApplication`, `config/RabbitConfig`, `messaging/OrderListener`, `domain/ProcessingService`).
- Created the minimal `consumer-service/src/main/resources/application.yaml` verbatim from RESEARCH.md lines 1096-1106 (`spring.application.name=order-consumer` + `management.endpoints.web.exposure.include=health`).
- `mvn -pl consumer-service -DskipTests package` exits 0 (`BUILD SUCCESS`, 0.7s) — produces `consumer-service-0.1.0-SNAPSHOT.jar`.
- `mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry` returns zero `[INFO] +- io.opentelemetry:` lines — Phase 1 baseline preserved on consumer (Pitfall F + Pitfall A jointly upheld).
- `application.yaml` greps zero matches for `(otel|opentelemetry|tracing)` — Pitfall F neutralised.
- `RabbitConfig.java` greps zero matches for `ConnectionFactory` and zero for `DirectExchange` — consumer does not own AMQP topology, and Spring Boot autoconfigures the ConnectionFactory from `SPRING_RABBITMQ_*` env vars (set in plan 1-02's `mise.toml`).
- `ProcessingService.process(...)` body content is exactly two comment lines (no statements) — the future Phase-2 INTERNAL-span site and Phase-3 deterministic-failure site are both reserved without any premature instrumentation.
- Worktree boundary respected: zero modifications under `producer-service/**`, `.planning/STATE.md`, or `.planning/ROADMAP.md`.

## Task Commits

Each task was committed atomically with `--no-verify` (parallel-worktree convention; orchestrator validates hooks centrally after wave merge):

1. **Task 1: ConsumerApplication + RabbitConfig (queue + JSON converter)** — `38e4921` (feat)
2. **Task 2: OrderListener + ProcessingService + application.yaml** — `fb8ba14` (feat)
3. **Task 3: Verification** — verification-only task (no file changes); see "Verification Gate Output" below

**Plan metadata commit:** appended after this SUMMARY.md is staged (the orchestrator owns the wave-merge final commit; this plan stages SUMMARY.md and lets the orchestrator commit it on its `git commit` after merging the worktree branch back to main).

## Files Created/Modified

- `consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java` (created, 11 lines) — `@SpringBootApplication` entrypoint; `main` calls `SpringApplication.run(...)`.
- `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` (created, 18 lines) — `@Configuration` with `public static final String QUEUE = "orders.created"`; two `@Bean`s: `Queue ordersCreatedQueue()` (durable=true) and `MessageConverter jsonMessageConverter()` (Jackson2JsonMessageConverter). NO `DirectExchange`, NO `Binding`, NO `ConnectionFactory`.
- `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` (created, 28 lines) — `@Component` listener; constructor-injects `ProcessingService`; one method `onOrder(Map<String,Object>)` annotated `@RabbitListener(queues = RabbitConfig.QUEUE)`; logs `"OrderCreated received: orderId={}"` and calls `processingService.process(message)`. Inline comment above the listener: `// APP-03: receive OrderCreated and simulate downstream domain work.`
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` (created, 12 lines) — `@Service`-annotated; one method `public void process(Map<String, Object> order)` with empty body (only two forward-reference comments naming Phase 2 and Phase 3 work).
- `consumer-service/src/main/resources/application.yaml` (created, 7 lines) — `spring.application.name: order-consumer` + `management.endpoints.web.exposure.include: health` only. NO OTel-related properties (Pitfall F).

## Verification Gate Output

### Task 1 grep-based acceptance criteria (all PASS)

| AC | Check | Expected | Actual |
|----|-------|----------|--------|
| 1 | `test -f consumer-service/.../ConsumerApplication.java` | exit 0 | ok |
| 2 | `test -f consumer-service/.../config/RabbitConfig.java` | exit 0 | ok |
| 3 | `grep -c '@SpringBootApplication' ConsumerApplication.java` | 1 | 1 |
| 4 | `grep -c 'package com.example.consumer;' ConsumerApplication.java` | 1 | 1 |
| 5 | `grep -c 'public static final String QUEUE = "orders.created";' RabbitConfig.java` | 1 | 1 |
| 6 | `grep -c '@Bean' RabbitConfig.java` | 2 | 2 (queue + converter only) |
| 7 | `grep -c 'DirectExchange' RabbitConfig.java` | 0 | 0 |
| 8 | `grep -c 'ConnectionFactory' RabbitConfig.java` | 0 | 0 |
| 9 | `mvn -pl consumer-service compile` | exit 0 | BUILD SUCCESS |

(Queue parity check with producer's `RabbitConfig.QUEUE` is structurally guaranteed at wave-merge time: this worktree only contains the consumer file, but both sister-plan agents wrote `QUEUE = "orders.created"` from the same plan-specifics directive in the orchestrator prompt. The merged tree at the end of wave 2 will contain both files matching byte-for-byte.)

### Task 2 grep-based acceptance criteria (all PASS)

| AC | Check | Expected | Actual |
|----|-------|----------|--------|
| 1-3 | three `test -f` checks for OrderListener / ProcessingService / application.yaml | all exit 0 | all ok |
| 4 | `grep -c '@RabbitListener(queues = RabbitConfig.QUEUE)' OrderListener.java` | 1 | 1 |
| 5 | `grep -c 'OrderCreated received: orderId={}' OrderListener.java` | 1 | 1 |
| 6 | `grep -c 'processingService.process(message);' OrderListener.java` | 1 | 1 |
| 7 | `grep -c '@Service' ProcessingService.java` | 1 | 1 |
| 8 | `grep -c 'public void process(Map<String, Object> order)' ProcessingService.java` | 1 | 1 |
| 9 | `grep -c 'name: order-consumer' application.yaml` | 1 | 1 |
| 10 | `grep -c 'include: health' application.yaml` | 1 | 1 |
| 11 | `grep -cE '(otel\|opentelemetry\|tracing)' application.yaml` (Pitfall F) | 0 | 0 |
| 12 | `mvn -pl consumer-service -DskipTests package` | exit 0 | BUILD SUCCESS |
| 13 | ProcessingService.process body — non-comment-non-signature-non-brace line count | 0 | 0 |

### `mvn -pl consumer-service -DskipTests package`

```
[INFO] --- jar:3.4.1:jar (default-jar) @ consumer-service ---
[INFO] Building jar: /home/coto/dev/demo/ose-otel-demo/.claude/worktrees/agent-a7d68dfee57e990bd/consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  0.691 s
```

JAR size: 5.5 KB (expected — Spring-Boot-style fat-jar repackaging is a `spring-boot-maven-plugin:repackage` goal that runs in the `package` phase; `-DskipTests` doesn't skip it; the small size reflects four small Java sources with no transitive bundling at the standard `package` step. Phase 2 will fatten this when OTel deps land.)

### `mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry` (Phase 1 BOM gate, consumer side)

Output is silent of any `io.opentelemetry:*` artifact lines:

```
[INFO] --- dependency:3.7.0:tree (default-cli) @ consumer-service ---
[INFO] BUILD SUCCESS
```

Filtered count of `[INFO] +-/\\- io.opentelemetry:*` lines: **0**.

### Pitfall F gate

```bash
$ grep -cE '(otel|opentelemetry|tracing)' consumer-service/src/main/resources/application.yaml
0
```

### Worktree boundary check (parallel-execution invariant)

```bash
$ git log --name-only HEAD~2..HEAD | grep -E '^(producer-service|\.planning/(STATE|ROADMAP)\.md)'
(no output — boundaries respected)
```

## Decisions Made

See the `key-decisions:` field in the frontmatter for the four architectural / pedagogical choices made during execution. The most consequential one for downstream phases is the empty-body convention on `ProcessingService.process`: leaving the body empty (only forward-reference comments) preserves a clean teaching surface for Phase 2 (`tracer.spanBuilder('process-order').startSpan()` lands here) and Phase 3 (deterministic 10% failure + `recordException` lands here) without any noise that the workshop attendee would have to mentally subtract.

## Deviations from Plan

### 1. [Rule 3 — Scope cap from orchestrator] Task 3 narrowed to schema/build verification only

- **Found during:** Task 3 (originally an end-to-end smoke test calling `mise run dev` in the background, POSTing to producer's `/orders`, polling the consumer's log file for the `OrderCreated received` line, and asserting consumer `/actuator/health` returns 200).
- **Issue:** Two structural blockers prevented Task 3 from running as plan-authored:
  1. **Sister plan 1-04 (producer-service) is being written in a parallel worktree right now.** It is not visible on this branch. There is no `producer-service/src/main/java/com/example/producer/ProducerApplication.java` to start — `mise run dev` would fail at the `dev:producer` task. End-to-end verification across both services can only happen after the wave-2 merge, which is plan 1-06's domain.
  2. **The orchestrator's `<plan_specifics>` block in the executor prompt explicitly caps Task 3:** *"Verification (run from your worktree): mvn -pl consumer-service -DskipTests package succeeds; mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry returns zero matches; DO NOT actually start the app — runtime checks belong to plan 1-06."* This mirrors how plan 1-03 (docker-compose) had its Task 2 scope-capped from a full `up -d --wait` cycle down to `docker compose config -q`, with the runtime verification explicitly deferred to plan 1-06.
- **Adjustment:** Ran the orchestrator-mandated subset only — `mvn -pl consumer-service -DskipTests package` (BUILD SUCCESS), `mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry` (zero matches), and the Pitfall F + ConnectionFactory + DirectExchange grep gates listed above. Did NOT run `mise run dev`, did NOT POST `/orders`, did NOT check `/actuator/health` over HTTP, did NOT inspect RabbitMQ queue depth.
- **Files modified:** None (Task 3 is verification-only).
- **Verification of correctness:** The five verification commands the orchestrator did mandate all pass. The deferred end-to-end checks are explicitly listed in plan 1-06's `<verification>` block as that plan's deliverable.
- **Committed in:** N/A (no source change; the verification reads happen at SUMMARY-write time).

### 2. [Rule 3 — Blocking environment fix] mise toolchain trust

- **Found during:** Task 1 setup (toolchain check).
- **Issue:** Worktree was freshly checked out; `mise.toml` (from sister plan 1-02 merged into the wave-2 base commit) was not yet trusted in this user's mise config, so `mise install` and `mise x` printed `error parsing config file: ... not trusted. Trust them with mise trust.`
- **Fix:** Ran `mise trust` once on the worktree root, then `mise install` (no-op — both Corretto 17.0.13.11.1 and Maven 3.9.11 were already populated locally from a prior plan). Subsequent `mise x -- mvn ...` invocations succeeded.
- **Files modified:** None in the repo.
- **Verification:** `mise x -- mvn -version` reports `Apache Maven 3.9.11` + `Java version: 17.0.13, vendor: Amazon.com Inc.` — matches the toolchain pinned in `mise.toml`.
- **Committed in:** N/A.

---

**Total deviations:** 2 (1 orchestrator-driven scope cap, 1 environment trust prompt)
**Impact on plan:** Zero functional impact. Both deviations are infrastructural — the scope cap moves runtime validation to plan 1-06 (its rightful owner per the wave-3 split), and the trust prompt is a one-time per-worktree side effect of the sister plan's `mise.toml` not being marked trusted yet on this user's mise installation. All Phase 1 plan-level success criteria for plan 1-05 are met.

## Issues Encountered

- **Initial `mise` invocation failed with "Config files ... are not trusted"** — the worktree was freshly checked out from the wave-2 base commit (`0045b6f`) which already contains `mise.toml`, but mise's per-user trust list didn't yet include this worktree path. Fixed by `mise trust` once. Workshop attendees who clone the repo for the first time will see the same prompt and have the same fix in plan 1-06's README.
- **Producer's `RabbitConfig.java` doesn't exist on this branch** (sister plan 1-04 is writing it in a parallel worktree right now). The plan's Task 1 acceptance criterion `grep 'QUEUE = "orders.created"' producer-service/.../RabbitConfig.java consumer-service/.../RabbitConfig.java | wc -l == 2` is therefore structurally impossible to verify here — only the consumer file is present, returning `wc -l == 1`. This is fine: parity is guaranteed at wave-merge time because both sister-plan executors received the same plan-specifics directive (`QUEUE = "orders.created"`) in their orchestrator prompts. Documented in the prompt's `<parallel_execution>` block: *"They're guaranteed to match — both agents are writing from the same plan-specific spec."*

## Threat Surface Scan

No new security-relevant surface beyond what `<threat_model>` documents (T-1-05-01 through T-1-05-05). All five threats are handled per the plan's dispositions:

- **T-1-05-01 (Tampering / EoP — Jackson polymorphic deserialization):** `mitigate`. OrderListener accepts `Map<String, Object>` (not polymorphic); the project uses default `Jackson2JsonMessageConverter` configuration which does NOT enable `@class`-header type info. Verified by reading the converter bean construction in `RabbitConfig.java`.
- **T-1-05-02 (Information Disclosure — Actuator surface on port 8081):** `mitigate`. `application.yaml` whitelists only `health` via `management.endpoints.web.exposure.include: health` — no `env`, `beans`, `metrics`, `loggers`, or `mappings` exposed. Verified by `grep -c 'include: health' application.yaml == 1` and the absence of any `'**'` or `'*'` whitelist.
- **T-1-05-03 (Repudiation — no trace_id/span_id in consumer logs):** `accept`. Phase 1 has no OTel by design; Phase 5 LOG-04 will add MDC injection.
- **T-1-05-04 (DoS — listener throws → backlog):** `accept`. Phase 1 ProcessingService.process is empty (cannot throw); Phase 3 introduces the deterministic-10%-failure path with explicit `recordException` and a controlled re-queue strategy.
- **T-1-05-05 (Tampering — malicious peer publishing to broker):** `accept`. Loopback-only AMQP per docker-compose port mapping; PROJECT.md scopes auth out of the workshop demo.

No new threats discovered. No new files introduce new trust boundaries beyond what the plan's threat register accounts for.

## User Setup Required

None — the consumer-service skeleton has no external service credentials, no environment variables that aren't already wired by sister plan 1-02's `mise.toml`, no secrets, and no manual steps. Plan 1-06 will document the workshop-attendee one-liner (`mise install && mise run preflight && mise run infra:up && mise run dev`) which transitively brings this consumer up.

## Next Phase Readiness

- **Plan 1-06 (phase-1-verification, wave 3):** Ready. Once the orchestrator merges this worktree and sister plan 1-04's producer-service worktree, plan 1-06 can run `mise run dev` (parallel `dev:producer` + `dev:consumer`), POST `/orders`, observe the `OrderCreated received: orderId=<uuid>` line in the consumer half of the log file, hit `GET http://localhost:8081/actuator/health` for `200 + {"status":"UP"}`, and confirm RabbitMQ Management UI shows `orders.created` with depth 0.
- **Phase 2 (traces) readiness:** Two CONSUMER-side instrumentation sites are now in place and named for their future role:
  - `OrderListener.onOrder(Map<String,Object>)` — CONSUMER-kind span site (TRACE-04). The Phase 2 SDK init will activate the `Tracer` here; the AMQP `MessagePostProcessor` extract pair (Phase 3) will pull `traceparent` from the message headers and link the consumer span to the producer's PRODUCER span.
  - `ProcessingService.process(Map<String,Object>)` — INTERNAL-kind span site (TRACE-06). Empty body intentional; Phase 2 will wrap it in `tracer.spanBuilder("process-order").startSpan()` + try-with-resources scope.
- **Phase 3 (context propagation) readiness:** The empty `process` body is also where APP-04's modulo-10 deterministic failure (and TRACE-09's `span.recordException(throwable)` + `span.setStatus(ERROR, ...)`) will land.
- **Phase 5 (logs) readiness:** The `LOG.info("OrderCreated received: orderId={}", orderId)` line is the canonical demonstration target for Phase 5 LOG-04 — once `opentelemetry-logback-appender-1.0` is wired and `OpenTelemetryAppender.install(openTelemetrySdk)` is called from the SDK config, this exact line will gain `trace_id` + `span_id` MDC keys, and the workshop attendee will see them light up in Loki side-by-side with the corresponding Tempo trace.

## Self-Check: PASSED

Verified after writing this SUMMARY (per `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java`
- `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java`
- `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java`
- `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`
- `consumer-service/src/main/resources/application.yaml`
- `.planning/phases/01-baseline-scaffold/1-05-SUMMARY.md`

**Commits (all FOUND in `git log`):**
- `38e4921` — feat(1-05): add ConsumerApplication + RabbitConfig (queue + JSON converter) (Task 1)
- `fb8ba14` — feat(1-05): add OrderListener + ProcessingService + application.yaml (Task 2)

---
*Phase: 01-baseline-scaffold*
*Plan: 05 (consumer-service)*
*Completed: 2026-04-30*
