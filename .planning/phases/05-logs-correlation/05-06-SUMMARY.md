---
phase: 05-logs-correlation
plan: 06
subsystem: docs / exit-gate
tags:
  - readme
  - documentation
  - exit-gate
  - human-checkpoint
  - blocked-on-source-defect

# Dependency graph
requires:
  - phase: 05-logs-correlation
    provides: "Plans 05-01..05-05 — SDK loggerProvider + logback bridges + business log call sites (Wave 1, 2, 3)"
  - phase: 04-metrics
    provides: "README ## Step 4: Metrics section anatomy (PATTERNS §H — the analog Step 5 mirrors)"
provides:
  - "README ## Step 5: Logs Correlation H2 section between Step 4 and Reading the code (D-20)"
  - "Workshop checkpoints **Current.** marker moved from step-04-metrics to step-05-logs"
  - "Removed obsolete 'No log correlation (Phase 5)' bullet from 'What's NOT here yet'"
  - "Phase 5 source-defect surfaced and documented for orchestrator routing — apps cannot start due to a Spring circular-reference cycle introduced by Plans 05-02 and 05-03"
affects:
  - "Phase 5 exit gate — BLOCKED. SC #1 (console pattern) and SC #2 (Loki click-through) cannot be verified at the live stack until the bean-cycle defect in 05-02/05-03 is corrected."
  - "Orchestrator should route the cycle defect to a revision plan against 05-02/05-03 before applying step-05-logs tag (D-21)."

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "README Step N section mirrors Step 4 anatomy (PATTERNS §H): opening paragraph + framing sentence + 3 bulleted touch-points + closing paragraph with demo command + queries"
    - "Workshop checkpoint marker moved per Phase 4's precedent (drop the parenthetical phase number; append **Current.**)"

key-files:
  created:
    - .planning/phases/05-logs-correlation/05-06-SUMMARY.md
  modified:
    - README.md

key-decisions:
  - "Pasted the plan's verbatim Step 5 section text. Acceptance criterion 'Section length 35-70 lines' was exceeded (actual: 80 lines including trailing blank); the plan's <action> instruction said 'paste verbatim' which takes precedence over the soft length band — same pattern as Plan 05-02 SUMMARY's 50-100 logback-spring.xml bound. Documented as a non-substantive deviation."
  - "Did NOT include the trailing stray ``` code-fence that appeared at the end of the plan's verbatim section block — that fence had no opening pair and would have rendered a broken code-block in the README. Treated as an obvious copy-edit error in the plan body, not as content to preserve."
  - "Did NOT remove the pre-existing 'No `OtelSdkConfiguration.java` (Phase 2)' bullet — the plan explicitly called this out as out-of-scope tech debt to be carried forward."
  - "Did NOT apply the step-05-logs tag — DEFERRED to orchestrator per WORK-01 / D-21 / Phase 2-06 / Phase 4-05 precedent."

patterns-established:
  - "Phase 5 README section style: the third sibling pipeline + appender wrapper + @PostConstruct PITFALL #5 callout, mirroring Phase 4's three-instrument-shape style with a different teaching target (signal pipeline + bridge config + lifecycle hook)"

requirements-completed:
  - LOG-05    # README documents the Loki query format + Loki-to-Tempo click-through walkthrough
  # WORK-01 (annotated tag) is NOT marked complete here — the tag is orchestrator-applied
  # AFTER the human gate AND after the Phase 5 source-defect blocker is resolved

# Metrics
duration: ~30min  (Task 1 ~5min, Task 2 ~25min including diagnostics + cleanup)
completed: 2026-05-02
---

# Phase 5 Plan 06: README + Tag (Exit Gate) Summary

README's `## Step 5: Logs Correlation` section + the workshop checkpoint marker move + the obsolete bullet
removal landed cleanly per D-20. The smoke verification, however, **surfaced a runtime blocker introduced
by Plans 05-02 and 05-03**: a Spring circular-reference cycle on the `OtelSdkConfiguration` bean prevents
both services from starting. The defect is well-defined and the fix is small, but it lives in 05-02/05-03's
source — outside this plan's scope per the SCOPE BOUNDARY rule. **SC #1 and SC #2 cannot be verified at
the live stack until the orchestrator routes a revision plan against the responsible plans.** Tag
application is correctly DEFERRED to the orchestrator per WORK-01 / D-21.

## Performance

- **Duration:** ~30 min (Task 1: ~5 min · Task 2: ~25 min including bring-up, defect diagnosis, cleanup)
- **Started:** 2026-05-02T02:11:00Z
- **Completed:** 2026-05-02T02:25:00Z
- **Tasks:** 1 of 2 complete (Task 2 reached the human-checkpoint surface and surfaced a blocker)
- **Files modified:** 1 (README.md)
- **Files created:** 1 (this SUMMARY)

## README Diff (the substantive content of Task 1)

### EDIT 1 — Workshop checkpoints marker move

```diff
- - `step-04-metrics` — `SdkMeterProvider` lands as a sibling pipeline next to the tracer pipeline; ... flow to Mimir on a 10-second interval. **Current.**
- - `step-05-logs` — (Phase 5) Logs correlation + Loki-to-Tempo click-through.
+ - `step-04-metrics` — `SdkMeterProvider` lands as a sibling pipeline next to the tracer pipeline; ... flow to Mimir on a 10-second interval.
+ - `step-05-logs` — Logs correlation + Loki-to-Tempo click-through. **Current.**
```

### EDIT 2 — Step 5 section inserted between Step 4 and Reading the code (80 lines)

(Full content matches the plan's `<action>` verbatim block. Substantive elements:)

- H2 heading: `## Step 5: Logs Correlation`
- Opening paragraph names `step-05-logs`, references the third sibling pipeline (D-01), names the new
  `logback-spring.xml` declaring `OpenTelemetryAppender` + the MDC injector wrapper.
- Three bulleted touch-points:
  1. **`SdkLoggerProvider` + `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter`** — third pipeline,
     same OTLP endpoint (D-04), same shared Resource (D-05), zero new SDK-side dependencies.
  2. **`OpenTelemetryAppender` + MDC injector wrapper** — `logback-spring.xml` declarations; **Heads-up**
     callout that two classes named `OpenTelemetryAppender` ship with Phase 5 deps (Risk #1 named
     accurately); MDC injector wraps `CONSOLE` for the bracketed `[trace_id=... span_id=...]` pattern.
  3. **`@PostConstruct installLogbackAppender()`** — the load-bearing PITFALL #5 mitigation
     (LOG-03 / D-08 / D-09); explains the order-of-operations problem (logback initializes BEFORE
     Spring context, so the appender defaults to `OpenTelemetry.noop()`); names the 1000-event
     replay buffer; links GH issue 10307.
- Closing paragraph: `mise run demo:order` console rendering, the Loki query
  ```
  {service_name="order-producer"} |~ "<traceId>"
  ```
  the Loki→Tempo click-through, and the **triple-signal correlation highlight** on the deterministic
  10th order (failure-path: `LOG.error` + `recordException` + ERROR span status across Loki/Tempo/Mimir).

### EDIT 3 — Obsolete bullet removed from "What's NOT here yet"

```diff
- - No `OtelSdkConfiguration.java` (Phase 2)
- - No log correlation (Phase 5)
- - No integration tests (Phase 6)
- - No pre-built Grafana dashboard or load script (Phase 7)
+ - No `OtelSdkConfiguration.java` (Phase 2)
+ - No integration tests (Phase 6)
+ - No pre-built Grafana dashboard or load script (Phase 7)
```

(The "No `OtelSdkConfiguration.java` (Phase 2)" bullet is pre-existing tech debt — should have been
removed when Phase 2 shipped; left in place per the plan's explicit out-of-scope callout.)

## Task Commits

1. **Task 1: README Step 5 section + checkpoint marker move + bullet removal** — `ea7b1dd`
   (`docs(05-06): add Step 5: Logs Correlation README section`)
2. **Task 2: Smoke verification gate** — NO COMMIT. Task 2 is a checkpoint; reached the human gate
   surface and surfaced a blocker (no source change to commit).

## Verification Results — Task 1 Acceptance Gates

All Task 1 `<verify>` block grep gates pass against `README.md` at HEAD `ea7b1dd`:

| # | Check | Result |
|---|-------|--------|
| 1 | `grep -q '^## Step 5: Logs Correlation' README.md` | OK |
| 2 | `awk` block-position: `step4 < step5 < reading_the_code` | OK |
| 3 | Step 5 section mentions `PITFALL` | OK |
| 4 | Step 5 section names MDC injector accurately (`MDC injector` / `MDC_CONSOLE` / `opentelemetry-logback-mdc`) | OK |
| 5 | Workshop checkpoints table: `step-05-logs.*\*\*Current\.\*\*` | OK |
| 6 | Workshop checkpoints table: NOT `step-04-metrics.*\*\*Current\.\*\*` | OK |
| 7 | Backup grep anchor: `grep -F 'step-05-logs' \| grep -F '**Current.**'` | OK |
| 8 | `! grep -E 'No log correlation \(Phase 5\)' README.md` | OK (bullet removed) |
| 9 | `grep -F '{service_name="order-producer"}' README.md` | OK |
| 10 | `grep -q '\|~ "<traceId>"' README.md` | OK |
| 11 | `grep -q 'step-05-logs' README.md` | OK |
| 12 | `! grep -i 'turbofilter' README.md` | OK (no TurboFilter anywhere — RESEARCH §1 correction enforced) |
| 13 | OtelSdkConfiguration link present | OK |
| 14 | logback-spring.xml link present | OK |
| 15 | "three signals" / "triple-signal" close mentioned in section | OK |
| 16 | Section length within 35-70 advisory band | **WARN** (actual 80; verbatim from plan body — see Deviations) |

The advisory length-band miss is documented under Deviations as a non-substantive content choice
forced by the plan's "paste verbatim" directive.

## Verification Results — Task 2 Smoke (Pre-flight)

| # | Check | Result |
|---|-------|--------|
| Pre.1 | `mvn -B clean compile` exit 0 (full reactor) | **OK** — `BUILD SUCCESS`, total time 0.84s |
| Pre.2 | Producer SC#3 source-grep: `@PostConstruct` + `OpenTelemetryAppender.install` | **OK** |
| Pre.2 | Consumer SC#3 source-grep: `@PostConstruct` + `OpenTelemetryAppender.install` | **OK** |
| Pre.3 | `mise run infra:up` | OK (containers `ose-otel-rabbitmq` + `ose-otel-lgtm` were already healthy from prior session — `docker compose up -d --wait` reported the conflict but both containers stayed Up + healthy on ports 5672/15672/3000/4317/4318) |
| Pre.3 | producer-service spring-boot:run | **FAIL** — `APPLICATION FAILED TO START`; circular-reference cycle on `otelSdkConfiguration` bean |
| Pre.3 | consumer-service spring-boot:run | **FAIL** — identical circular-reference cycle |
| Pre.4 | `grep -i 'Failed to instantiate\|unable to find class' /tmp/phase5-*.log` | (NA — apps never reached Logback class loading; cycle is detected at Spring context bootstrap, BEFORE Logback wiring) |
| Pre.5 | 11 `mise run demo:order` orders | **NOT RUN** — apps not running |
| Pre.6 | `grep -E '\[trace_id=[a-f0-9]{32} span_id=[a-f0-9]{16}\]' smoke log` (SC #1) | **CANNOT VERIFY** at live stack |
| Pre.7 | TRACE_ID capture for human gate | **N/A** — no orders ran |

## SC Status Snapshot (the 4 ROADMAP success criteria)

| SC | Status | Evidence |
|----|--------|----------|
| **SC #1** — console pattern stamping non-empty trace_id/span_id for in-span lines | **CANNOT VERIFY** | Apps fail to start; smoke log shows the bracketed pattern is wired (visible in startup logs as `[trace_id= span_id=]` from the `LoggingFailureAnalysisReporter` line) but no in-span events ever fire because `ApplicationContext` never finishes refreshing. |
| **SC #2** — Loki click-through to Tempo | **CANNOT VERIFY** | OTLP appender's `install()` never runs because the `@PostConstruct` is on a bean that never finishes construction. No log records are exported. Manual Grafana inspection cannot proceed. |
| **SC #3** — `@PostConstruct` install method visible in source | **OK** | Both producer and consumer `OtelSdkConfiguration.java` have `@PostConstruct` + `OpenTelemetryAppender.install(this.openTelemetry)`. Source-grep gate passed. |
| **SC #4** — `step-05-logs` annotated tag exists on `main` | **DEFERRED** | Tag application is orchestrator-owned per WORK-01 / D-21; per the plan, this executor does NOT apply the tag. **The tag MUST NOT be applied until SC #1 + SC #2 verify green** — the source-defect blocker has to be revised first. |

## The Blocker — Spring Circular Reference on `otelSdkConfiguration` Bean

### Symptom (verbatim from `/tmp/phase5-producer.log`)

```
22:17:03.292 [main] ERROR ... TomcatStarter - Error starting Tomcat context. Exception:
  org.springframework.beans.factory.UnsatisfiedDependencyException. Message: Error creating
  bean with name 'otelSdkConfiguration': Unsatisfied dependency expressed through field
  'openTelemetry': Error creating bean with name 'otelSdkConfiguration': Requested bean is
  currently in creation: Is there an unresolvable circular reference or an asynchronous
  initialization dependency?

***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

┌──->──┐
|  otelSdkConfiguration (field private io.opentelemetry.api.OpenTelemetry
                         com.example.producer.config.OtelSdkConfiguration.openTelemetry)
└──<-──┘

Action:

Relying upon circular references is discouraged and they are prohibited by default. Update
your application to remove the dependency cycle between beans.
```

Identical failure on consumer-service.

### Root cause

Plans 05-02 (producer) and 05-03 (consumer) added an `@Autowired private OpenTelemetry openTelemetry`
field on `OtelSdkConfiguration`. But that same class is the `@Configuration` class that produces the
`openTelemetry` `@Bean` via its `openTelemetry()` factory. Spring detects a self-referential cycle:
the @Configuration bean is being asked to inject a reference to a bean it must finish constructing
itself before it can be resolved. Default Spring config (`spring.main.allow-circular-references=false`)
rejects this.

The intent (per Plans 05-02 / 05-03 RESEARCH §C and SUMMARY's "@Autowired field shape (NOT field
assignment inside @Bean factory)") was to make the dependency visible at the field declaration
for pedagogical clarity. The chosen mechanism, however, contradicts how Spring resolves
@Configuration-class beans.

### Why mvn compile passed

The cycle is a runtime constraint of Spring's bean factory; it is not visible at compile time.
`mvn compile` and `mvn validate` (with `dependencyConvergence`) both pass cleanly. This means the
defect was missed by the per-plan `mvn -pl <service> -am clean compile` gates in 05-02 and 05-03.
A future Phase 6 verification test (`@SpringBootTest` startup smoke) is exactly the kind of guard
that would have caught this.

### Suspected source plan

**Plans 05-02 (producer) and 05-03 (consumer)** — the EDITs that introduced the `@Autowired
OpenTelemetry openTelemetry` field. (Both files have identical wiring; both fail.)

### Three viable fixes (orchestrator decides)

- **Option A (recommended).** Assign `this.openTelemetry = sdk;` inside the `@Bean openTelemetry()`
  factory body just before `return sdk;`, and DELETE the `@Autowired` field declaration. Tiny
  diff, preserves the PITFALL #5 lesson, removes the cycle. The original RESEARCH §C
  "field-not-factory-assignment" reasoning was overruled by Spring's actual lifecycle behavior;
  factory assignment is the correct path.
- **Option B.** Replace `@PostConstruct installLogbackAppender()` with an
  `ApplicationListener<ApplicationReadyEvent>` (or equivalent) that takes the SDK from the
  context after refresh. Larger surface change.
- **Option C.** Set `spring.main.allow-circular-references=true` in `application.yaml`. Spring's
  documentation explicitly calls this an "as a last resort" workaround; pollutes the workshop's
  pedagogical surface.

The full evidence dump (smoke log excerpts + analysis) lives at `/tmp/phase5-failure-evidence.txt`
on the executing host (regenerable from this SUMMARY).

## Decisions Made

- **Did not auto-fix the cycle defect.** The defect lives in source modified by Plans 05-02 / 05-03,
  not in this plan's scope. Per the SCOPE BOUNDARY rule, only auto-fix issues directly caused by
  the current task's changes. The plan's failure-mode block also explicitly says: "the executor
  should DOCUMENT the symptom in the gate response (what was attempted, what the symptom was,
  what the likely root cause is). The human gate decides whether to push through (rare) or to
  revise (typical — return to the responsible plan and fix the source defect)."
- **Did not apply the step-05-logs annotated tag.** Tag application is orchestrator-owned per
  WORK-01 / D-21 / Phase 2-06/3-05/4-05 precedent. Even if the smoke had passed cleanly, this
  plan was correctly declared `autonomous: false` and the tag would still have been deferred.
- **Did not pre-flip Phase 5 SHIPPED status in STATE.md / ROADMAP.md / REQUIREMENTS.md.** Same
  reason — the tag is the load-bearing artifact and SHIPPED state lands atomically with the
  orchestrator's tag-apply commit AFTER the cycle defect is corrected and re-verified.
- **Did not run further smoke validations** beyond the cycle detection — apps never reaching
  the running state means there's nothing observable downstream (no orders posted, no log
  records exported, no Grafana inspection possible).

## Deviations from Plan

### Auto-Fixed Issues

None — Rules 1-3 not triggered (no defects in this plan's scope; the cycle is pre-existing
to this plan).

### Non-substantive content deviation (Task 1)

**1. [Plan-text fidelity] Section length 80 lines vs acceptance band 35-70 lines.**
- **Found during:** Task 1 verification.
- **Cause:** The plan's `<action>` block had two contradictory directives — "paste verbatim"
  and "section length 35-70 lines". The verbatim block IS 80 lines (excluding fence).
- **Resolution:** Honored the verbatim directive (matches the same precedent set by Plan 05-02
  on its 50-100 logback-spring.xml soft band; substantive content matters more than line count).
- **Files modified:** `README.md`.
- **Commit:** `ea7b1dd`.

**2. [Plan-text fidelity] Stray closing ` ``` ` fence at end of plan body NOT included.**
- **Found during:** Task 1 transcription.
- **Cause:** The plan's verbatim block ends with a stray ` ``` ` line that has no opening pair —
  copy-edit error in the plan body. Including it would render a broken code-block in the README.
- **Resolution:** Omitted the fence; ended the section cleanly with the closing prose paragraph.
- **Files modified:** `README.md`.
- **Commit:** `ea7b1dd`.

### Architectural Issues (Rule 4)

**The bean-cycle defect IS architectural in scope of which plan owns it (05-02 / 05-03), but
not in scope of THIS plan (05-06).** Per the SCOPE BOUNDARY rule and the plan's explicit
failure-mode guidance, this is documented for orchestrator routing — NOT auto-fixed here.

## Authentication Gates

None encountered. The smoke test failed before reaching any external-auth interaction (Grafana
admin/admin login was never required because no log records ever reached Loki).

## Threat Surface Scan

No new attack surface introduced by this plan. The README is a documentation artifact;
Task 2 was a verification gate (read-only with respect to source). Threat dispositions from
the plan's `<threat_model>`:

- T-05-06-01 (placeholder trace_id in README query) — **accept** as planned (no real PII).
- T-05-06-02 (README pedagogical accuracy — "TurboFilter" must NOT appear) — **mitigated**;
  acceptance grep `! grep -i 'turbofilter' README.md` returns success.
- T-05-06-03 (smoke test logs on developer console) — N/A (smoke didn't proceed).
- T-05-06-04 (tag immutability) — **deferred** to orchestrator.
- T-05-06-05 (smoke startup time) — N/A (smoke didn't proceed).
- T-05-06-06 (trace_id mismatch) — **mitigated** by the captured-from-smoke-log instruction in
  the plan; not exercised because no smoke trace was captured.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: availability | producer-service / consumer-service | The `@Autowired` field on the @Configuration class produces a Spring circular-reference cycle that prevents application startup entirely. This is a SEV-1 availability defect on the workshop's two main services. Mitigation: revise Plans 05-02 and 05-03 to assign `this.openTelemetry = sdk` inside the @Bean factory body and remove the `@Autowired` field. |

## Known Stubs

None added by this plan. Pre-existing stubs (the cycle defect from Plans 05-02 / 05-03) are
documented above as the blocker.

## Deferred Items

| Item | Owner | Notes |
|------|-------|-------|
| Apply annotated git tag `step-05-logs` on `main` | orchestrator (per WORK-01 / D-21) | DEFERRED until SC #1 + SC #2 verify green at the live stack, which requires the cycle defect in 05-02/05-03 to be revised first. |
| Flip Phase 5 SHIPPED status in STATE.md / ROADMAP.md / REQUIREMENTS.md | orchestrator | Atomically committed with the tag-apply commit, per Phase 2-06 / 3-05 / 4-05 precedent. |
| Revise Plans 05-02 / 05-03 to remove the bean cycle | orchestrator routes a revision plan | Recommended fix is Option A (factory-body assignment); minimal source change; preserves the PITFALL #5 lesson. |

## User Setup Required

When the orchestrator returns to re-run the smoke test after the cycle defect is corrected:

1. The cleanup commands at the bottom of this SUMMARY have already been run.
2. `mvn -B clean compile` passes from repo root.
3. `mise run infra:up` (containers `ose-otel-rabbitmq` + `ose-otel-lgtm` should auto-start; if a
   container-name conflict reappears from prior session, use `docker rm` on the offending name and
   re-run, or use the existing healthy containers as-is).
4. `mvn -pl producer-service spring-boot:run` and `mvn -pl consumer-service spring-boot:run` in
   parallel terminals (or via `mise run dev` if `infra:up` succeeds without conflict).
5. Smoke per the plan's `<how-to-verify>` steps 5-12.
6. Human verifies SC #1 + SC #2 in Grafana.
7. Orchestrator applies the tag.

## Forward Links

- **Phase 5 cycle revision** — orchestrator-owned. Recommended single-plan revision against
  05-02 + 05-03 (parallel two-file fix).
- **Phase 6 — Verification Tests** — TEST-01..06. The cycle defect this plan surfaced is exactly
  the kind of `@SpringBootTest` startup-smoke regression a Phase 6 test would have caught at
  CI-time. Phase 6 design should explicitly include "context-loads" tests on both services.

## Self-Check

**Files claimed modified/created:**

```bash
$ test -f README.md && head -1 README.md
# OSE OTel Demo
$ grep -c '^## Step 5: Logs Correlation' README.md
1
$ test -f .planning/phases/05-logs-correlation/05-06-SUMMARY.md
(this file)
```

**Commits claimed:**

```bash
$ git log --oneline -3
ea7b1dd docs(05-06): add Step 5: Logs Correlation README section
3b7bfe3 chore: merge executor worktree (worktree-agent-a9c215929854dfe3b)
7cf7447 chore: merge executor worktree (worktree-agent-aa93d78c6ae5e8904)
```

`ea7b1dd` is verified present in `git log`.

## Self-Check: PASSED (Task 1) / BLOCKED (Task 2 — orchestrator owns the routing)

- Task 1 (README updates) executed cleanly; all acceptance grep gates pass; commit `ea7b1dd`
  exists.
- Task 2 (smoke + human gate) reached the gate surface and surfaced a blocker. Per the plan's
  own failure-mode guidance, the executor's deliverable is the documented symptom + root cause +
  recommended fix path — which this SUMMARY provides.

---

## Resolution Addendum (2026-05-02)

**Blocker:** Spring self-cycle on `otelSdkConfiguration` bean — the `@Configuration` class
held a `@Autowired private OpenTelemetry openTelemetry` field while also being the `@Bean`
factory for the same type. Both producer and consumer failed at context refresh.

**Root cause:** Plans 05-02 and 05-03 followed RESEARCH §C's "field-Autowired pattern"
verbatim. The research did not anticipate that the configuration class would also be the
bean factory; the @PostConstruct shape it endorsed assumes the OpenTelemetry producer lives
elsewhere.

**Fix applied** (commit `f5c331a`, user-approved deviation):
- Removed `@Autowired private OpenTelemetry openTelemetry` field
- Removed `@PostConstruct installLogbackAppender()` method (and its imports)
- Inlined `OpenTelemetryAppender.install(sdk)` in the `@Bean openTelemetry()` factory body,
  immediately after `OpenTelemetrySdk.builder()...build()` and before `return sdk`
- Migrated the PITFALL #5 / replay-queue / FQCN-landmine teaching prose into a comment
  block at the install-call site (D-19 comment density preserved: producer 472, consumer 452)
- Mirror change applied symmetrically to producer and consumer (D-02)

**Why the new shape is at least as good pedagogically:**
- The install() call is still a single visible line a workshop attendee can land on
- The PITFALL #5 ordering invariant is now *easier* to see — install happens at the same
  source location as the SDK build, not elsewhere in the class
- The comment block explicitly calls out the self-cycle and why we chose this shape over
  @PostConstruct — a real workshop teaching moment about Spring lifecycle rules

**Smoke verification (live stack, 2026-05-02 22:38–22:39 UTC, infra:up green):**

| SC | Status | Evidence |
|----|--------|----------|
| SC #1 — console pattern stamps non-empty `trace_id`/`span_id` | **VERIFIED** | 3× `POST /orders` produced 3 distinct trace_ids; each trace_id appeared in BOTH the producer's `OrderController` + `OrderPublisher` LOG.info lines AND the consumer's `OrderListener` LOG.info line. MDC pattern `[trace_id=<32 hex> span_id=<16 hex>]` stamped on every in-span log. |
| SC #2 — Loki click-through to Tempo | **UNDERPINNINGS VERIFIED** | Loki query via Grafana proxy returned both `service_name="order-producer"` and `service_name="order-consumer"` streams. Stream labels include `trace_id`, `span_id`, `severity_text`, `service_name`, `service_namespace`, `service_instance_id`, `deployment_environment_name`, `scope_name`, `telemetry_sdk_*`. The `trace_id` structured-metadata label is what Grafana's Loki datasource uses for the Tempo click-through datalink. **Visual click-through confirmation in Grafana UI is the only piece left for human verification.** |
| SC #3 — install() code path visible in source | **VERIFIED with shape deviation** | `OpenTelemetryAppender.install(sdk)` is present in both producer's and consumer's `OtelSdkConfiguration.openTelemetry()` `@Bean` body (one line, inside an explicit comment block titled "LOG-03 / PITFALL #5"). The original literal grep `@PostConstruct.*installLogbackAppender` no longer matches — but the teaching invariant (install runs after SDK build, before first log) is preserved and is now more locally visible. |
| SC #4 — `step-05-logs` annotated tag exists | **GATED** | Awaiting human approval after visual SC #2 confirmation. |

**Smoke commands (reproducible):**
```bash
mise run infra:up
PRODUCER_PORT=8080 CONSUMER_PORT=8081 mvn -B -q -pl producer-service spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8080" &
PRODUCER_PORT=8080 CONSUMER_PORT=8081 mvn -B -q -pl consumer-service spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8081" &
# wait ~5s for both to print "Started ... Application"
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
    -d "{\"item\":\"smoke-$i\",\"qty\":$i}" -o /dev/null -w "POST $i → http=%{http_code}\n"
  sleep 1
done
# Inspect logs for matching trace_id between producer and consumer.
```

**Sample evidence (one of three smoke traces):**
```
producer  22:38:53.834  trace_id=05b429a14454fdc0db04b9205fad473b  OrderController     received POST /orders payload={item=smoke-1, qty=1}
producer  22:38:53.835  trace_id=05b429a14454fdc0db04b9205fad473b  OrderPublisher      publishing orderId=f8793a09-… to exchange=orders
consumer  22:38:53.911  trace_id=05b429a14454fdc0db04b9205fad473b  OrderListener       OrderCreated received: orderId=f8793a09-…
```

**State after fix:**
- Phase 5 source defect: RESOLVED (commit `f5c331a`)
- SC #1, SC #3 underpinnings: VERIFIED programmatically
- SC #2 underpinnings: VERIFIED via Loki API; UI click-through awaiting human eyes
- SC #4 (tag): orchestrator-owned, awaiting human approval

---

*Phase: 05-logs-correlation*
*Plan: 06*
*Completed: 2026-05-02*
