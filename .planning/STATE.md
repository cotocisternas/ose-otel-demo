---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Phase 7 plan 01 (grafana-dashboard-provisioning) SHIPPED — next plan 07-02 load-script
last_updated: "2026-05-02T08:50:37.908Z"
last_activity: 2026-05-02
progress:
  total_phases: 7
  completed_phases: 6
  total_plans: 41
  completed_plans: 41
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-29)

**Core value:** A workshop attendee can clone the repo, run `docker compose up` + `mise run dev`, hit `POST /orders`, and see a single distributed trace flow from the HTTP handler through the RabbitMQ publish, into the consumer's processing logic, with correlated metrics and logs — and understand exactly which lines of SDK code made each piece work.
**Current focus:** Phase 07 — polish-and-differentiators (Phase 06 shipped)

## Current Position

Phase: 07 (polish-differentiators) — EXECUTING
Plan: 7 of 7 (next: 07-07 exit-gate)
Status: Ready to execute
Last activity: 2026-05-02

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**

- Total plans completed (Phase 2): 6 source-complete (tag pending for plan 06)
- Average duration: ~7min
- Total execution time (Phase 2): ~41 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 02-manual-sdk-bootstrap-first-traces | 6 | ~41min | ~7min |
| 04 | 5 | - | - |

**Recent Trend:**

- Last 5 plans: 02-02 (9min) → 02-03 (6min) → 02-04 (8min) → 02-05 (5.5min) → 02-06 (8min)
- Trend: stable — all sub-10-minute plans

*Updated after each plan completion*

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 02 P01 (pom-dependencies) | 5min | 2 | 3 |
| Phase 02 P02 (producer-sdk-config) | 9min | 3 | 2 |
| Phase 02 P03 (consumer-sdk-config) | 6min | 2 | 1 |
| Phase 02 P04 (producer-instrumentation) | 8min | 3 | 2 |
| Phase 02 P05 (consumer-instrumentation) | 5.5min | 3 | 2 |
| Phase 02 P06 (readme-and-exit-gate) | 8min | 2 (T3 staged) | 1 |
| Phase 5 P6 | 30min | 2 tasks | 2 files |
| Phase 06 P01 | 3min | 2 tasks | 3 files |
| Phase 06 P02 | 2min | 1 tasks | 1 files |
| Phase 06 P04 | 5min | 1 tasks | 1 files |
| Phase 06 P05 | 4min | 1 tasks | 1 files |
| Phase 07 P01 (grafana-dashboard-provisioning) | ~16min* | 4 (2 commit-bearing) | 3 files (1 modified, 2 created) |

*Wall-clock spans 03:00–07:15 UTC because of human-verify checkpoint wait between Task 3 author and Task 4 live-verify; active work ~16min.

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Initialization: SDK bootstrap (`OtelSdkConfiguration`) is **inlined per-service**, not extracted to a shared library — duplication is the lesson (TRACE-01, DOC-05).
- Initialization: AMQP propagation pair (`TracingMessagePostProcessor` + `TracingMessageListenerAdvice`) lives in a **shared** `otel-bootstrap` Maven module — symmetry of one inject method matched by one extract method IS the lesson (PROP-04).
- Initialization: Maven multi-module build with parent + 3 children (`otel-bootstrap`, `producer-service`, `consumer-service`).
- Initialization: Workshop checkpoints are **annotated git tags on `main`** (immutable, frozen after first delivery), not long-lived branches (WORK-01).
- Initialization: Phase 7 (Polish & Differentiators) is locked into v1 (not deferred post-cohort) per user choice — dashboard, load script, screenshots, full README walkthrough.
- [Phase 02-01]: Inverted mise verify:bom IN PLACE — task name keeps meaning; Phase 1 zero-libs assertion → Phase 2 one-version-per-artifact assertion
- [Phase 02-01]: Removed -q from mvn dependency:tree in mise verify:bom (Rule 1 deviation): -q suppresses [INFO] logs that the dependency-plugin uses to emit the tree, causing the script to read empty output and trigger a false-alarm
- [Phase 02-02]: Producer worktree smoke test required runtime workaround for parallel-worktree container-name race (Rule 3 documented); no source diff. SDK-config code itself is unaffected.
- [Phase 02-03]: Consumer's JavaDoc deliberately includes producer-side identities as pedagogical references; collides with grep-only acceptance gates that fold JavaDoc into code matching. Followed the explicit "use the EXACT structure below" PLAN directive — semantic intent preserved on every must_have.
- [Phase 02-04]: Producer Wave-3 smoke test ran on port 8082 (Rule 3 — sibling worktree's pre-Plan-02-04 producer was holding 8080); runtime-only workaround. Source uses configured 8080.
- [Phase 02-05]: Consumer Wave-3 smoke bypassed `depends=[infra:up]` for the worktree container-name race; producer trace observed had 1 span vs PLAN's 3 because Plan 02-04 INTERNAL+PRODUCER spans lived in sibling worktree at smoke-time (resolves post-merge — verified by post-merge build success).
- [Phase 02-06]: Followed the plan's verbatim Edit-1/Edit-2/Edit-3 README instructions character-for-character (no abbreviation, no rewording). Did NOT apply the tag — orchestrator/user gate per `<checkpoint_handling>`. Did NOT pre-flip Phase 2 SHIPPED status in STATE/ROADMAP/REQUIREMENTS — the tag is the load-bearing artifact, so SHIPPED state lands atomically with the orchestrator's tag-apply commit.
- [Phase 02-06]: All 6 Phase 2 success criteria verified end-to-end against live infra at HEAD 0f6c99e: producer trace = SERVER+INTERNAL+PRODUCER (3 spans), consumer trace = CONSUMER+INTERNAL (2 spans, empty parentSpanId proves D-10 Context.root() honored at runtime), Ctrl-C flushed last batch (1→2 traces), 0 unknown_service:java traces, 137/131 comment lines in OtelSdkConfiguration files, README DOC-05 callout present, clean tree, mise verify:bom green.
- [Phase 03 discuss]: 13 implementation decisions captured in 03-CONTEXT.md across 4 areas: (Bootstrap wiring) plain Java classes + per-service @Bean wiring + 4 separate top-level classes (TracingMessagePostProcessor / TracingMessageListenerAdvice / MessagePropertiesSetter / MessagePropertiesGetter) + explicit RabbitTemplate @Bean + per-service Tracer scope; (Producer span ownership) post-processor owns the PRODUCER span lifecycle (Phase 2 D-09 hand-off honored), inject-only span lifetime (matches OTel Kafka/JMS convention), exchange-based destination naming (semconv-correct, corrects Phase 2's queue-as-destination); (APP-04 failure design) AtomicInteger counter modulus, custom ProcessingFailedException, setDefaultRequeueRejected(false) safety; (Listener factory wiring) Configurer-aided SimpleRabbitListenerContainerFactory @Bean, OrderListener.onOrder simplified to 3-line pass-through (Tracer param removed), CONSUMER span mirrors producer's semconv-correct shape with .setParent(extracted).
- [Phase ?]: [Phase 05-06] README Step 5 section + checkpoint marker move + obsolete bullet removal landed cleanly (commit ea7b1dd). Section length 80 lines vs 35-70 advisory band — paste-verbatim directive took precedence.
- [Phase ?]: [Phase 05-06] Smoke surfaced a Spring circular-reference cycle on otelSdkConfiguration bean — @Autowired field on the same @Configuration that produces the bean (Plans 05-02/05-03). mvn compile passes; cycle is runtime-only. Recommended fix: assign this.openTelemetry = sdk inside @Bean factory body and drop @Autowired field. Per SCOPE BOUNDARY rule, not auto-fixed in 05-06; routed to orchestrator for revision against 05-02/05-03.
- [Phase ?]: [Phase 05-06] step-05-logs annotated tag NOT applied — orchestrator-owned per WORK-01 / D-21 / Phase 2-06 precedent. Tag MUST NOT be applied until SC #1 + SC #2 verify green at the live stack, which requires the bean-cycle defect to be revised first.
- [Phase ?]: [Phase 06-01] Followed plan verbatim; reactor build deliberately broken between 06-01 and 06-02 (plan invariant)
- [Phase ?]: Plan 06-02: Followed RESEARCH §3.4 verbatim. Created integration-tests/pom.xml with explicit maven-failsafe-plugin 3.5.5 binding (integration-test + verify goals) because the parent does NOT inherit from spring-boot-starter-parent (Phase 1 BOM-ordering invariant). All 8 deps inherit BOM-managed pins; opentelemetry-exporter-otlp deliberately excluded (D-13). Reactor build restored.
- [Phase 06]: Plan 06-04: TestOtelConfiguration created (131 lines, 83 comment lines). Single Rule 1/3 deviation: rephrased JavaDoc divergence table to drop literal Batch*/PeriodicMetricReader tokens so NO-BATCH-PROCESSORS grep gate passes; pedagogical content preserved (paraphrased). 17/17 must_haves green; mvn -B -pl integration-tests -am test-compile BUILD SUCCESS (2 source files). Commit baedb99.
- [Phase ?]: 06-05: AssertJ alias collision resolved per Option A (FQCN at AssertJ sites; static-import OTel assertThat)
- [Phase ?]: 06-05: PRODUCER span asserts MessagingOperationTypeIncubatingValues.SEND not literal 'publish'; CONSUMER asserts .PROCESS — semconv-version-aware
- [Phase 06-06]: README "Step 6: Verification Tests" section landed (commit 5a1b5c1) with verbatim Edit-1/Edit-2/Edit-3 from plan; smoke verified 4/4 IT green with host RabbitMQ stopped (random port 32780, BUILD SUCCESS). All 5 ROADMAP Phase-6 success criteria passed (SC #5 satisfied by orchestrator's tag-apply). Annotated tag step-06-tests applied to status-flip commit per WORK-01 / D-21 / Phase 2-06 precedent; ROADMAP/REQUIREMENTS/STATE flipped atomically with the tag. infra:up restarted post-gate (rabbitmq + otel-lgtm both healthy).
- [Phase 07-01]: Authoring strategy = option-b (hand-author JSON). Resolved otel-lgtm in-container provisioning path = `/otel-lgtm/grafana/conf/provisioning/dashboards`. Live-verified zero-click auto-provisioning: top row 4 panels (Tempo trace search, service graph, RED metrics, Loki logs); second row `Deeper-dive (post-workshop)` collapsed by default. Dashboard JSON portable (uid `ose-otel-demo`, no top-level version/id/iteration, default datasource UIDs). `traceid` Loki var = textbox default `.+` (vs spec's preferred `query`-derived shape — chosen for empty-data robustness). Rule 4 follow-up logged but accepted: bind mount onto `/otel-lgtm/grafana/conf/provisioning/dashboards` shadows bundled `grafana-dashboards.yaml` (RED-classic / RED-native / JVM-metrics) — bundled dashboards no longer auto-load; load-bearing `ose-otel-demo` dashboard does. Future plan can sibling-mount + sidecar-manifest if bundled dashboards wanted back.

### Pending Todos

None yet.

### Blockers/Concerns

- **Phase 1 research flag**: RESOLVED 2026-04-29 — RESEARCH.md (commit `d3bbf32`) confirms OTel BOM precedes Spring Boot BOM; mise pin is `corretto-17.0.13.11.1` (exact patch, not floating).
- **Phase 3 research flags** (3 open, expanded by 03-CONTEXT.md):
  1. (carried from SUMMARY.md) `MethodInterceptor` advice on `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` composes correctly with Spring AMQP 3.2.8's `@RabbitListener` lifecycle — no thread-context loss between advice and listener body (PITFALLS #7).
  2. (new from 03-CONTEXT D-07) Spring AMQP 3.2.8 supports the `MessagePostProcessor.postProcessMessage(Message, Correlation, exchange, routingKey)` 4-arg overload AND `RabbitTemplate.processBeforePublishMessageProcessors(...)` invokes it when registered via `setBeforePublishPostProcessors(...)`.
  3. (new from 03-CONTEXT D-10) `MethodInvocation.getArguments()` index for `Message` arg when wrapping a `@RabbitListener` (arg[0] vs arg[1] vs typed scan) — ARCHITECTURE.md Pattern 2 used `[1]` with comment "(channel, message)" but that may be `ChannelAwareMessageListener`-specific.
  All three resolve in `/gsd-research-phase 3` before `/gsd-plan-phase 3`.

- **Phase 5 research flag**: Confirm Maven coordinate for MDC injector (`opentelemetry-logback-mdc-1.0` artifact vs `<captureMdcAttributes>` on appender).
- **Phase 6 research flag**: Validate `@ServiceConnection` + `RabbitMQContainer` on Spring Boot 3.4.13.
- [Phase 5] Spring circular-reference cycle on otelSdkConfiguration bean (producer + consumer) blocks Phase 5 exit-gate smoke verification. Plans 05-02/05-03 added @Autowired field on the @Configuration class that produces the bean. mvn compile clean; cycle runtime-only. Recommended fix: assign this.openTelemetry = sdk inside @Bean factory body, drop @Autowired field. Surfaced by Plan 05-06 smoke; orchestrator should route a revision plan against 05-02/05-03 before tag application.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-05-02T07:15:30.000Z
Stopped at: Phase 7 plan 01 (grafana-dashboard-provisioning) SHIPPED — next plan 07-02 load-script
Resume file: .planning/phases/07-polish-differentiators/07-02-load-script-PLAN.md
