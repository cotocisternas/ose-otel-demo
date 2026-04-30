---
phase: 01-baseline-scaffold
plan: 04
subsystem: producer
tags: [spring-boot, rest, rabbitmq, spring-amqp, actuator, baseline-no-otel]

# Dependency graph
requires:
  - phase: 01-baseline-scaffold
    provides: "Plan 1-01 producer-service/pom.xml — Spring Boot starters web/amqp/actuator/test (zero OTel deps)"
  - phase: 01-baseline-scaffold
    provides: "Plan 1-02 mise.toml — SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD env vars + PRODUCER_PORT=8080 + dev:producer task"
  - phase: 01-baseline-scaffold
    provides: "Plan 1-03 docker-compose.yml — RabbitMQ broker on localhost:5672 (Spring Boot autoconfigures ConnectionFactory from the env vars in mise.toml)"
provides:
  - "Producer service skeleton: 5 Java sources + 1 application.yaml under producer-service/src/main/, total 118 lines of Java + 8 lines of YAML"
  - "POST /orders → 202 Accepted with body {\"orderId\":\"<uuid>\"} (APP-01)"
  - "OrderPublisher.publish() — direct call to RabbitTemplate.convertAndSend(EXCHANGE='orders', ROUTING_KEY='order.created') (APP-02)"
  - "GET /actuator/health endpoint exposed (APP-05 producer half — runtime verified by plan 1-06)"
  - "AMQP topology beans: DirectExchange('orders'), durable Queue('orders.created'), Binding, Jackson2JsonMessageConverter — autowired into Spring AMQP's RabbitAutoConfiguration"
  - "OrderService.place() — the future Phase-2 INTERNAL span site (TRACE-06); kept deliberately three lines so the workshop attendee can see the exact instrumentation boundary"
affects:
  - "1-05 consumer-service (sister plan, parallel) — produces messages this plan binds to (consumer @RabbitListener subscribes to QUEUE='orders.created' bound by ROUTING_KEY='order.created' on EXCHANGE='orders'); both must agree on those three constants"
  - "1-06 phase-1-verification (wave 3) — owns the runtime smoke test of dev:producer + curl POST /orders + actuator/health + RabbitMQ Mgmt API publish_in counter (deferred from Task 3 of this plan per orchestrator scope cap)"
  - "Phase 2 (traces) — TRACE-06 wraps OrderService.place() in an INTERNAL span; TRACE-04 wraps OrderPublisher.publish() in a PRODUCER span and injects traceparent into AMQP headers via OtelMessagePostProcessor"
  - "Phase 5 (logs) — application.yaml will gain a logback-spring.xml-driven trace_id pattern; THIS plan deliberately does NOT add logging.pattern.* (Pitfall F)"

# Tech tracking
tech-stack:
  added:
    - "Spring AMQP RabbitTemplate / DirectExchange / Queue / Binding / Jackson2JsonMessageConverter — consumed via spring-boot-starter-amqp 3.4.13"
    - "Spring MVC @RestController / @RequestMapping / @PostMapping / @RequestBody / ResponseEntity — consumed via spring-boot-starter-web 3.4.13"
    - "Spring Boot Actuator /actuator/health — consumed via spring-boot-starter-actuator 3.4.13"
    - "java.util.UUID for orderId generation"
  patterns:
    - "Constructor injection only (no @Autowired field injection) — workshop-recommended pattern for testability"
    - "Symbolic constants for AMQP topology (EXCHANGE / QUEUE / ROUTING_KEY public static final) — Phase 2 OTel propagation tests will reference these by name, not by string literal"
    - "Spring Boot autoconfigured ConnectionFactory — NO manual @Bean ConnectionFactory; SPRING_RABBITMQ_* env vars (mise.toml) drive the autoconfiguration (RESEARCH lines 304-306 Don't-Hand-Roll)"
    - "Actuator exposure whitelist (include: health) — NOT include: '*' (security control: prevents /actuator/env, /actuator/beans, /actuator/configprops from leaking credentials and class structure; T-1-04-02 mitigation)"
    - "Minimal application.yaml — no OTel/tracing/logging-pattern keys (Pitfall F enforced)"

key-files:
  created:
    - "producer-service/src/main/java/com/example/producer/ProducerApplication.java (11 lines) — @SpringBootApplication entry point"
    - "producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (37 lines) — AMQP topology beans + symbolic constants"
    - "producer-service/src/main/java/com/example/producer/api/OrderController.java (24 lines) — POST /orders @RestController returning 202 + orderId"
    - "producer-service/src/main/java/com/example/producer/domain/OrderService.java (22 lines) — UUID + delegation to OrderPublisher"
    - "producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (24 lines) — RabbitTemplate.convertAndSend wrapper"
    - "producer-service/src/main/resources/application.yaml (8 lines) — spring.application.name + actuator health whitelist"
  modified: []

key-decisions:
  - "Followed RESEARCH.md lines 856-1000 verbatim — verified Java skeletons; no implementation alternatives chosen because the plan is highly prescriptive and the skeletons were already vetted in research"
  - "Did NOT declare a @Bean ConnectionFactory in RabbitConfig — Spring Boot's RabbitAutoConfiguration builds it from SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD env vars set in mise.toml. Adding a manual ConnectionFactory bean is one of the Don't-Hand-Roll items in RESEARCH.md"
  - "application.yaml omitted server.port — the dev:producer mise task passes -Dserver.port=${PRODUCER_PORT} via JVM args; declaring it in yaml would be redundant or conflicting"
  - "application.yaml omitted ALL OTel-related keys (otel.*, management.tracing.*, logging.pattern.*) — Pitfall F. Phase 2 wires the OTel SDK programmatically via OtelSdkConfiguration.java; this Phase 1 baseline must stay clean"
  - "Used actuator exposure whitelist `include: health` (NOT `include: '*'`) — security control enforced by the threat model T-1-04-02 (prevents /actuator/env, /actuator/beans leaking credentials/class structure)"
  - "Inline comments retained verbatim from RESEARCH skeleton — `// 202 Accepted: order accepted for async processing via AMQP.` in OrderController and `// APP-02: publish via direct exchange + routing key.` in OrderPublisher. Heavier JavaDoc comes in Phase 2 (DOC-03)"
  - "Task 3 runtime smoke test scope-capped to static verification only (mvn package + dependency:tree) per orchestrator's <plan_specifics> directive — full app startup + curl POST /orders + RabbitMQ Mgmt API check is plan 1-06's wave-3 territory. Mirrors plan 1-03's identical scope cap on `docker compose up -d --wait`"

patterns-established:
  - "Pattern: 4-package layout per service (root + api + domain + messaging + config) — workshop attendees see one responsibility per package; future plans (consumer side) follow the same layout"
  - "Pattern: Three-line domain method (UUID generate → publish → return orderId) at the future INTERNAL-span site — keeps the Phase 2 instrumentation visually obvious"
  - "Pattern: AMQP topology constants defined ONCE in RabbitConfig and referenced everywhere via RabbitConfig.EXCHANGE / RabbitConfig.ROUTING_KEY — no string-literal duplication in OrderPublisher or future tests"
  - "Pattern: Constructor injection (private final) for all Spring beans — no @Autowired, no setter injection"
  - "Pattern: Stage task-related files individually with `git add <path>` (not `git add .` or `git add -A`) — leaves producer-service/target/ untracked even though no .gitignore exists yet (which is plan 1-02's deferred scope)"

requirements-completed: [APP-01, APP-02, APP-05]

# Metrics
duration: ~2min
completed: 2026-04-30
---

# Phase 1 Plan 04: Producer Service Summary

**Spring Boot 3.4.13 producer service skeleton — 5 Java sources + 1 application.yaml — exposing `POST /orders` (202 + orderId UUID) that publishes an OrderCreated message via Spring AMQP `RabbitTemplate.convertAndSend(EXCHANGE='orders', ROUTING_KEY='order.created')`. Zero OpenTelemetry imports, zero OTel/tracing config in application.yaml — Phase 1 baseline preserved (`mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry` returns 0 lines).**

## Performance

- **Duration:** ~131 s (~2 min) — well under the planner's 1-hour budget
- **Started:** 2026-04-30T02:57:48Z
- **Completed:** 2026-04-30T02:59:59Z
- **Tasks:** 3 (Task 1 ProducerApplication + RabbitConfig; Task 2 OrderController + OrderService + OrderPublisher + application.yaml; Task 3 verification-only — runtime portion deferred to plan 1-06 per orchestrator scope cap)
- **Files created:** 6 (5 Java sources + 1 application.yaml)
- **Lines of code:** 118 Java (across 5 files) + 8 YAML

## Accomplishments

- **POST /orders contract delivered (APP-01):** `OrderController.create()` returns `ResponseEntity.accepted().body(Map.of("orderId", orderId))` — exactly 202 + `{"orderId":"<uuid>"}` per the requirement.
- **AMQP publish path delivered (APP-02):** `OrderPublisher.publish()` calls `rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message)`. The constants are public-static-final in RabbitConfig, ready for the consumer-side @RabbitListener (sister plan 1-05) to bind on the same names.
- **Actuator health endpoint wired (APP-05 producer half):** `management.endpoints.web.exposure.include=health` whitelist (NOT `*`); `spring-boot-starter-actuator` is on the classpath via plan 1-01's pom.xml. Runtime verification belongs to plan 1-06 (wave 3).
- **AMQP topology beans declared in RabbitConfig:**
  - `DirectExchange ordersExchange()` → `new DirectExchange("orders")`
  - `Queue ordersCreatedQueue()` → `new Queue("orders.created", true)` (durable=true)
  - `Binding ordersBinding(...)` → `BindingBuilder.bind(queue).to(exchange).with("order.created")`
  - `MessageConverter jsonMessageConverter()` → `new Jackson2JsonMessageConverter()`
- **NO manual @Bean ConnectionFactory** — Spring Boot autoconfigures it from `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` env vars set in `mise.toml`. Verified by `! grep -c 'ConnectionFactory' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` → 0 (RESEARCH lines 304-306 Don't-Hand-Roll).
- **Pitfall F neutralised:** application.yaml contains zero `otel`, `opentelemetry`, or `tracing` substrings. Verified by `! grep -E '(otel|opentelemetry|tracing)' producer-service/src/main/resources/application.yaml` → exit 0.
- **Phase 1 baseline gate (BOM-convergence):** `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry` → BUILD SUCCESS with 0 `io.opentelemetry:*` artifact lines on the classpath.
- **Maven build green:** `mvn -pl producer-service -DskipTests package` → BUILD SUCCESS in 0.7 s; jar produced at `producer-service/target/producer-service-0.1.0-SNAPSHOT.jar`.

## Task Commits

Each task was committed atomically with `--no-verify` (parallel-worktree contention avoidance, per orchestrator's `<parallel_execution>` block):

1. **Task 1: ProducerApplication + RabbitConfig** — `543e28f` (feat) — 2 files, 48 insertions.
2. **Task 2: OrderController + OrderService + OrderPublisher + application.yaml** — `bb47011` (feat) — 4 files, 79 insertions.
3. **Task 3: Verification-only** — no commit (no file changes; static verification ran successfully and the runtime smoke test was deferred to plan 1-06 per orchestrator scope cap).

**Plan metadata commit:** appended after this SUMMARY.md is staged (final commit at end of executor lifecycle).

## Files Created/Modified

| Path | Status | Lines | Purpose |
|------|--------|-------|---------|
| `producer-service/src/main/java/com/example/producer/ProducerApplication.java` | created | 11 | `@SpringBootApplication` entry point |
| `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` | created | 37 | AMQP topology beans + symbolic constants |
| `producer-service/src/main/java/com/example/producer/api/OrderController.java` | created | 24 | POST /orders → 202 + orderId (APP-01) |
| `producer-service/src/main/java/com/example/producer/domain/OrderService.java` | created | 22 | UUID + publish delegation; future Phase-2 INTERNAL span site |
| `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` | created | 24 | `rabbitTemplate.convertAndSend` wrapper (APP-02) |
| `producer-service/src/main/resources/application.yaml` | created | 8 | Minimal Spring Boot config (name + actuator whitelist) |

Total: 6 files, 126 lines.

## Verified Acceptance Criteria

### Task 1 (12 acceptance checks)

| # | Check | Result |
|---|-------|--------|
| 1 | `test -f producer-service/.../ProducerApplication.java` | exit 0 |
| 2 | `test -f producer-service/.../config/RabbitConfig.java` | exit 0 |
| 3 | `grep -c '@SpringBootApplication' ProducerApplication.java` | 1 |
| 4 | `grep -c 'package com.example.producer;' ProducerApplication.java` | 1 |
| 5 | `grep -c 'public static final String EXCHANGE = "orders";' RabbitConfig.java` | 1 |
| 6 | `grep -c 'public static final String QUEUE = "orders.created";' RabbitConfig.java` | 1 |
| 7 | `grep -c 'public static final String ROUTING_KEY = "order.created";' RabbitConfig.java` | 1 |
| 8 | `grep -c 'new DirectExchange' RabbitConfig.java` | 1 |
| 9 | `grep -c 'Jackson2JsonMessageConverter' RabbitConfig.java` | 2 (import + new — both legitimate; criterion is `>=1` essentially) |
| 10 | `grep -c '@Bean' RabbitConfig.java` | 4 (exchange, queue, binding, converter) |
| 11 | `! grep -c 'ConnectionFactory' RabbitConfig.java` exit 0 | exit 0 (zero manual CF beans) |
| 12 | `mvn -pl producer-service compile` | BUILD SUCCESS |

### Task 2 (15 acceptance checks)

| # | Check | Result |
|---|-------|--------|
| 1-4 | All 4 file existence tests | exit 0 |
| 5 | `grep -c '@RestController'` OrderController | 1 |
| 6 | `grep -c '@RequestMapping("/orders")'` OrderController | 1 |
| 7 | `grep -c '@PostMapping'` OrderController | 1 |
| 8 | `grep -c 'ResponseEntity.accepted()'` OrderController | 1 (202, NOT 200) |
| 9 | `grep -c 'UUID.randomUUID()'` OrderService | 1 |
| 10 | `grep -c 'rabbitTemplate.convertAndSend'` OrderPublisher | 1 |
| 11 | `grep -c 'RabbitConfig.EXCHANGE'` OrderPublisher | 1 |
| 12 | `grep -c 'RabbitConfig.ROUTING_KEY'` OrderPublisher | 1 |
| 13 | `grep -c 'name: order-producer'` application.yaml | 1 |
| 14 | `grep -c 'include: health'` application.yaml | 1 |
| 15 | `! grep -E '(otel|opentelemetry|tracing)' application.yaml` exit 0 | exit 0 (Pitfall F) |
| 16 | `! grep -E 'include:.*\*' application.yaml` exit 0 | exit 0 (no wildcard exposure) |
| 17 | `mvn -pl producer-service -DskipTests package` | BUILD SUCCESS (5 Java sources compiled, jar built) |

### Task 3 (static portion only — runtime portion deferred)

| # | Check | Result |
|---|-------|--------|
| 1 | `mvn -pl producer-service -DskipTests package` | BUILD SUCCESS in 0.7 s |
| 2 | `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry` | BUILD SUCCESS, 0 OTel artifact lines |
| 3 | OTel artifact line count via `grep -cE '^\[INFO\] [+\\][- ]+io\.opentelemetry:'` | 0 |

## Sample Build Tail (for traceability)

```
[INFO] --- compiler:3.13.0:compile (default-compile) @ producer-service ---
[INFO] Compiling 5 source files with javac [debug target 17] to target/classes
[INFO] --- jar:3.4.1:jar (default-jar) @ producer-service ---
[INFO] Building jar: producer-service/target/producer-service-0.1.0-SNAPSHOT.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  0.7 s
```

## Phase 1 Baseline Gate Result

```
$ mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry
[INFO] Scanning for projects...
[INFO] --------------------< com.example:producer-service >--------------------
[INFO] Building OSE OTel Demo (producer) 0.1.0-SNAPSHOT
[INFO] --- dependency:3.7.0:tree (default-cli) @ producer-service ---
[INFO] BUILD SUCCESS
[INFO] Total time:  0.4 s
```

Zero `io.opentelemetry:*` artifact lines under the BUILD SUCCESS — confirms the Phase 1 invariant (APP/INFRA-01) for the producer module.

## Decisions Made

Followed plan as specified — no implementation alternatives required. The plan is highly prescriptive (RESEARCH.md lines 856-1000 are verified Java skeletons), and all six files are a one-to-one match with the research output. The only material decision was Task 3's scope cap (runtime smoke test deferred to plan 1-06), which was directly mandated by the orchestrator's `<plan_specifics>` block — see "Deviations" below.

## Deviations from Plan

### Scope-Adjusted Verification (Task 3)

**1. [Rule 3 — Scope cap from orchestrator] Task 3 verification narrowed to static checks only**

- **Found during:** Task 3 (Smoke-test the running producer)
- **Issue:** The plan's Task 3 calls for a full `mise run dev:producer` background launch + `curl POST /orders` round-trip + `/actuator/health` GET + RabbitMQ Management API publish_in counter check. This requires (a) infra to be up via `mise run infra:up` (full image pulls if cold cache — plan 1-03 deferred its own end-to-end up cycle), (b) waiting for "Started ProducerApplication" log line (up to 60 s), and (c) Live HTTP + AMQP integration that is rightfully a wave-3 phase-level concern. The orchestrator's `<plan_specifics>` block in this executor's prompt explicitly instructs:
  > *"DO NOT actually start the app — that's a runtime check (plan 1-06 owns it). Compilation success is sufficient here."*
- **Adjustment:** Task 3 ran the static portions only — `mvn -pl producer-service -DskipTests package` (BUILD SUCCESS, 0.7 s) and `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry` (BUILD SUCCESS, 0 OTel artifact lines). The runtime smoke test (curl POST → 202, actuator/health → 200, RabbitMQ Mgmt API publish_in ≥ 1) is the explicit deliverable of plan 1-06 (wave-3 phase-1-verification owner).
- **Files modified:** None (Task 3 is verification-only by design — `<files>(none — verification only)</files>` per the plan).
- **Why this is safe:** APP-01/02/05 satisfaction is structurally guaranteed by the source code (verified by 27 grep-based acceptance checks across Tasks 1-2 + a green `mvn package`). Wave-3 plan 1-06 will do the live round-trip and is the right place to assert HTTP status codes and RabbitMQ Mgmt API counters. This mirrors plan 1-03's identical scope cap (`docker compose config -q` only; full `up -d --wait` deferred to 1-06).
- **Committed in:** No commit — Task 3 produces no file changes.

---

**Total deviations:** 1 scope adjustment (orchestrator-driven, not a quality issue).

**Impact on plan:** Zero — runtime verification is a phase-level concern that plan 1-06 owns. The static checks performed here are sufficient to commit the file-creating tasks with confidence; APP-01/02/05 satisfaction is structurally guaranteed by source-code grep + green `mvn package` and will be runtime-verified in wave 3.

## Issues Encountered

- **mise trust prompt on first invocation:** `mvn` failed initially with `Config files in <worktree>/mise.toml are not trusted`. Resolved with one-time `mise trust /home/coto/dev/demo/ose-otel-demo/.claude/worktrees/agent-a9bef573bdbadd019/mise.toml`. Not a code issue — worktree isolation artifact. Did not require any plan changes.
- **`producer-service/target/` left untracked:** No `.gitignore` exists at the repo root yet (plan 1-01 deferred it to plan 1-02, and plan 1-02 did not add one either). Per the executor's commit protocol ("stage task-related files individually"), I staged each task's files by exact path and left `target/` untracked. This matches the precedent in plans 1-01/1-02/1-03 (none of them committed build outputs). A `.gitignore` is out-of-scope for plan 1-04.

## Threat Model Mitigations Implemented

| Threat ID | Mitigation in this plan |
|-----------|-------------------------|
| T-1-04-02 (Information Disclosure — Actuator exposing sensitive endpoints) | `application.yaml` sets `management.endpoints.web.exposure.include=health` (whitelist, not `*`); only `/health` is reachable. Verified by `! grep -E 'include:.*\*' application.yaml` → exit 0 |
| T-1-04-04 (Tampering / EoP — Jackson polymorphic deserialization) | Plain `Map<String, Object>` payloads (no `@JsonTypeInfo`, no polymorphic types); `Jackson2JsonMessageConverter` default config does NOT enable `@class` headers. Verified by reading the bean declaration (`new Jackson2JsonMessageConverter()` — no custom configuration) |

T-1-04-01 (no input schema validation), T-1-04-03 (unauthenticated endpoint), T-1-04-05 (large request body), T-1-04-06 (stack-trace leakage), T-1-04-07 (env-var override of broker host) are all `accept` per the plan's threat register — workshop scope, no Internet exposure, no PII.

## User Setup Required

None — the producer module compiles and packages with the host JDK + Maven configured by `mise.toml`. The runtime smoke test (start app, POST /orders, verify 202) is the responsibility of plan 1-06 and the workshop attendee following the README walk-through.

## Next Phase Readiness

- **Within Phase 1, Wave 3:** Plan 1-06 owns the end-to-end runtime check — `mise run infra:up && mise run dev:producer && mise run demo:order` should yield 202 with an orderId UUID and the RabbitMQ Mgmt API should report `message_stats.publish_in >= 1` on the `orders` exchange.
- **Phase 2 readiness — Trace propagation:**
  - `OrderService.place()` is the future TRACE-06 INTERNAL span site (line 21 in OrderService.java).
  - `OrderPublisher.publish()` is the future TRACE-04 PRODUCER span site; the `rabbitTemplate.convertAndSend` call at line 22 of OrderPublisher.java is where Phase 2 will wrap a `MessagePostProcessor` that injects `traceparent` into AMQP message headers.
  - `RabbitConfig.EXCHANGE` and `RabbitConfig.ROUTING_KEY` constants are referenced symbolically from OrderPublisher — Phase 2's tests can assert on the same constants without string-literal duplication.
- **Phase 5 readiness — Logs:** application.yaml deliberately has no `logging.pattern.*` keys (Pitfall F). Phase 5 will add `logback-spring.xml` driving the trace_id pattern and the OTel Logback appender.

## Self-Check: PASSED

- `producer-service/src/main/java/com/example/producer/ProducerApplication.java`: FOUND.
- `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java`: FOUND.
- `producer-service/src/main/java/com/example/producer/api/OrderController.java`: FOUND.
- `producer-service/src/main/java/com/example/producer/domain/OrderService.java`: FOUND.
- `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`: FOUND.
- `producer-service/src/main/resources/application.yaml`: FOUND.
- Commit `543e28f` (feat(1-04): add ProducerApplication + RabbitConfig) in `git log`: FOUND.
- Commit `bb47011` (feat(1-04): add OrderController + OrderService + OrderPublisher + application.yaml) in `git log`: FOUND.
- All 27 grep-based acceptance checks across Tasks 1-2: PASS.
- `mvn -pl producer-service -DskipTests package`: BUILD SUCCESS.
- `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry`: BUILD SUCCESS, 0 OTel artifact lines.

---
*Phase: 01-baseline-scaffold*
*Plan: 04-producer-service*
*Completed: 2026-04-30*
