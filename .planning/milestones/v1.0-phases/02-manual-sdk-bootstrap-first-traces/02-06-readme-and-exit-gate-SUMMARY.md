---
phase: 02-manual-sdk-bootstrap-first-traces
plan: 06
subsystem: docs
tags: [readme, doc-03, doc-05, work-01, workshop-checkpoint, annotated-tag, phase-exit-gate, broken-then-fixed-pedagogy]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plan 02-01 — five OTel deps (api/sdk/exporter-otlp BOM-managed via opentelemetry-bom:1.61.0; semconv 1.40.0 + semconv-incubating 1.40.0-alpha pinned) on both service classpaths; mise verify:bom enforcing one-version-per-OTel-artifact reactor invariant"
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plans 02-02 + 02-03 — per-service OtelSdkConfiguration.java (137 / 131 comment lines) with manual SdkTracerProvider + BatchSpanProcessor + OtlpGrpcSpanExporter + parent-based-always-on Sampler + composite W3C propagators + @Bean(destroyMethod=close); producer adds HttpServerSpanFilter (D-07)"
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: "Plans 02-04 + 02-05 — five inline span sites (SERVER + INTERNAL + PRODUCER on producer; CONSUMER + INTERNAL on consumer) using D-01 pure-inline template with D-03 catch shape; consumer CONSUMER span starts from Context.root() per D-10 with verbatim teaching comment"
  - phase: 01-baseline-scaffold
    provides: "Phase 1 README structure (Prerequisites / Workshop checkpoints / What's NOT here yet) — Phase 2 inserts two new sections in place between Workshop checkpoints and What's NOT here yet, and updates one bullet in Workshop checkpoints"
provides:
  - "README.md gains `## Reading the code` section (DOC-03 — pointer to both OtelSdkConfiguration.java files plus HttpServerSpanFilter.java; reminds readers that every @Bean has an inline comment)"
  - "README.md gains `## Why is OtelSdkConfiguration.java duplicated?` section (DOC-05 — single paragraph naming the per-service duplication intentional, warning against refactoring into a shared @AutoConfiguration bean, listing the five small differences between the two files)"
  - "README.md `## Workshop checkpoints` updated: step-02-traces becomes the **Current** checkpoint with expanded description ('intentional setup for the Phase 3 propagation lesson'); step-01-baseline back-marked as historical"
  - "Verified all 6 Phase 2 ROADMAP success criteria are simultaneously green at commit 0f6c99e on main: TWO distinct traces / Ctrl-C flushes last batch / heavily-commented OtelSdkConfiguration in both services / SERVER+INTERNAL+PRODUCER on producer + CONSUMER+INTERNAL on consumer with empty parentSpanId / DOC-05 callout in README / clean working tree (criterion 6 also requires the tag, which is staged but NOT YET applied — checkpoint pending user approval)"
  - "Annotated tag command staged for orchestrator/user gate (NOT yet executed): `git tag -a step-02-traces -m '<phase-2 message>'` at commit 0f6c99e0c3e0463e9d6c74a9eaad124f6d92c393"
affects:
  - "Phase 3 (`step-03-context-propagation`) — workshop attendees `git checkout step-02-traces` to see the broken-trace baseline; the dramatic delta is Phase 3's headline lesson once the tag is applied"
  - "Phase 7 DOC-04 — the screenshot capture pairing Phase-2's TWO traces with Phase-3's ONE trace will be taken at this tag (deferred to Phase 7); Plan 02-06 establishes the workshop-frozen state Phase 7 will photograph"
  - "STATE.md / ROADMAP.md — Phase 2 marked SHIPPED 6/6 plans complete once user approves the tag (the plan-level deltas land here pre-tag; the phase-completion deltas land alongside the orchestrator's tag-apply step)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DOC-03 / DOC-05 README delivery convention: README sections that reinforce in-source teaching (heavy-commented config classes) rather than duplicating the teaching in prose. The `## Reading the code` section points attendees BACK at the code; it does not re-explain what the code already documents inline."
    - "Workshop-checkpoint rotation pattern: each phase's exit plan flips the **Current** marker from prior tag to its own tag and expands the description with phase-specific context (Phase 2 explicitly names the 'intentional setup for the Phase 3 propagation lesson' so readers don't see step-02 as a partial implementation)."

key-files:
  created:
    - ".planning/phases/02-manual-sdk-bootstrap-first-traces/02-06-readme-and-exit-gate-SUMMARY.md (this file)"
  modified:
    - "README.md (3 edits: 2 new sections inserted between Workshop checkpoints and What's NOT here yet; 1 bullet update in Workshop checkpoints to move **Current.** marker from step-01-baseline to step-02-traces with expanded description)"

key-decisions:
  - "Followed the plan's verbatim Edit-1/Edit-2/Edit-3 instructions character-for-character. The plan provided exact old/new strings for the bullet updates and exact verbatim content for the two new sections; reproduced them with no abbreviation, summarisation, or wording reinterpretation."
  - "Did NOT apply the annotated tag in this plan — per the orchestrator's `<checkpoint_handling>` directive (autonomous: false), the tag application is staged and reported back via the checkpoint return; the user (`coto@petabyte.cl`) gates the immutable workshop checkpoint."
  - "Did NOT update STATE.md / ROADMAP.md / REQUIREMENTS.md beyond the plan-level deltas — the tag is the load-bearing artifact for Phase 2 SHIPPED status; full phase-completion state updates land alongside the orchestrator's tag-apply step. This SUMMARY documents the green-criteria-state at commit 0f6c99e so the orchestrator can apply the tag at the same SHA the criteria were verified against."
  - "Used Grafana datasource proxy (`http://localhost:3000/api/datasources/proxy/uid/tempo/api/...`) for Tempo queries because port :3200 is not exposed externally by `grafana/otel-lgtm:0.26.0` (only :3000 / :4317 / :4318 are bound) — same pattern Plans 02-02 / 02-04 / 02-05 documented."

patterns-established:
  - "Phase-exit checkpoint plan structure: T1 (the source/README delta), T2 (verify all phase-success-criteria simultaneously green), T3 (human-action checkpoint to apply the annotated tag). Same shape Phase 1's Plan 1-06 used; this plan inherits and extends with the verify-all-6-criteria gate."
  - "Workshop-checkpoint description convention: each tag's bullet in `## Workshop checkpoints` carries (1) the substantive what-shipped sentence, (2) the explicit pedagogical purpose (especially when the state is intentionally broken, as Phase 2's), and the **Current.** marker on exactly one bullet at any given commit."

requirements-completed: [DOC-03, DOC-05]
# Note: WORK-01 (Phase 2 portion — annotated tag step-02-traces) is STAGED, NOT YET COMPLETE.
# WORK-01 status flips to Complete (Phase 2 portion) when the orchestrator/user applies the tag.

# Metrics
duration: 8min
completed: 2026-05-01
---

# Phase 2 Plan 06: README and Exit Gate Summary

**README.md gains DOC-03 (`## Reading the code`) and DOC-05 (`## Why is OtelSdkConfiguration.java duplicated?`) sections plus the Workshop-checkpoint pivot to step-02-traces as **Current**; all 6 Phase 2 ROADMAP success criteria verified simultaneously green at commit `0f6c99e`; annotated tag `step-02-traces` STAGED for orchestrator/user approval (NOT yet applied — checkpoint plan, autonomous=false).**

## Performance

- **Duration:** ~8 min (482 s, including the runtime smoke gate)
- **Started:** 2026-05-01T17:30:00Z
- **Completed:** 2026-05-01T17:38:02Z
- **Tasks:** 2 fully executed (T1 README edits committed; T2 verification-only — no source diff). T3 (apply annotated tag) stays staged for the user-checkpoint gate.
- **Files modified:** 1 (`README.md`)
- **Files created:** 1 (this SUMMARY)
- **Commits:** 1 (T1; T2 produces no source diff per the plan's `<files>(none — verification only)</files>`)

## Accomplishments

- **DOC-03 README half delivered (the heavy comments themselves were delivered in Plans 02-02 / 02-03; this plan adds the README pointer).** New `## Reading the code` section explicitly tells workshop attendees to open both `OtelSdkConfiguration.java` files in their IDE and read top-to-bottom — every `@Bean` has an inline comment. Three intra-repo links: producer's `OtelSdkConfiguration.java`, consumer's `OtelSdkConfiguration.java`, and producer-only `HttpServerSpanFilter.java`. Closing paragraph names the five span sites and re-emphasises the D-01 inline template (boilerplate IS the lesson).
- **DOC-05 callout delivered.** New `## Why is OtelSdkConfiguration.java duplicated?` section is a single paragraph that names the duplication intentional, explicitly warns readers AGAINST refactoring into a shared `@AutoConfiguration` bean in `otel-bootstrap`, names the five concrete differences between the two files (package, JavaDoc cross-reference, service.name string, tracer scope name, plus the producer-only `HttpServerSpanFilter` bean), and explicitly contrasts this with the propagation pair Phase 3 ships in `otel-bootstrap` (where the symmetry of inject + extract IS the lesson). Workshop attendees who would otherwise file a "fix the duplication" PR see the rationale at the README level.
- **Workshop-checkpoint pivot.** `## Workshop checkpoints` step-02-traces bullet now reads `Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces (intentional setup for the Phase 3 propagation lesson). **Current.**`. step-01-baseline bullet drops `**Current.**`. Phase 1's "(Phase 2)" forward-pointer prefix is removed because Phase 2 has now landed.
- **All 6 Phase 2 ROADMAP success criteria simultaneously green at commit 0f6c99e.** Each criterion was exercised end-to-end against the live `mise run dev` flow (producer + consumer running on host JVM; RabbitMQ + grafana/otel-lgtm in docker-compose). Outputs captured below.
- **Phase 2 invariant preserved.** `mise run verify:bom` exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`

## Task Commits

Each task was committed atomically using normal `git commit` (sequential mode on main, hooks active):

1. **Task 1: Update README.md — DOC-03 + DOC-05 sections + Workshop-checkpoint pivot** — `0f6c99e` (docs)
2. **Task 2: Verify all 6 Phase 2 ROADMAP success criteria** — verification-only, no commit (T2's `<files>` is `(none — verification only)` per the plan)
3. **Task 3: Apply annotated git tag step-02-traces** — STAGED, NOT EXECUTED — `<checkpoint_handling>` directs the orchestrator/user to gate this; the executor returns the staged tag command + SHA in its checkpoint message

**Plan metadata commit:** _(produced after this SUMMARY.md is written; will commit SUMMARY only — STATE.md / ROADMAP.md / REQUIREMENTS.md updates land alongside the orchestrator's tag-apply step so all of Phase 2 SHIPPED state arrives in one atomic commit at the same SHA the tag points to.)_

## Files Created/Modified

- **`README.md`** (modified — `+19 / -2`, commit `0f6c99e`) — Three precise edits per the plan's verbatim instructions:
  - Edit 1: Workshop checkpoints — drop `**Current.**` from step-01-baseline; rewrite step-02-traces line as `step-02-traces — Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces (intentional setup for the Phase 3 propagation lesson). **Current.**`. Other 4 bullets (step-03 through step-06) unchanged.
  - Edit 2: Insert new `## Reading the code` section after Workshop checkpoints, before What's NOT here yet — three intra-repo links + closing paragraph naming the 5 span sites and the D-01 template.
  - Edit 3: Insert new `## Why is OtelSdkConfiguration.java duplicated?` section after Reading the code, before What's NOT here yet — single paragraph DOC-05 callout naming the duplication intentional and contrasting with `otel-bootstrap`.
- **`.planning/phases/02-manual-sdk-bootstrap-first-traces/02-06-readme-and-exit-gate-SUMMARY.md`** (created — this file).

## README structure after Plan 02-06

```
# OSE OTel Demo
## What This Is               (Phase 1)
## Core Value                 (Phase 1)
## Prerequisites              (Phase 1; DOC-02)
  ### Required tools          (Phase 1)
  ### Required free ports     (Phase 1)
  ### IDE setup               (Phase 1)
  ### One-time setup          (Phase 1)
  ### First run               (Phase 1)
## Workshop checkpoints       (Phase 1; **bullet for step-02-traces updated this plan**)
## Reading the code           (NEW in 02-06; DOC-03)
## Why is OtelSdkConfiguration.java duplicated?   (NEW in 02-06; DOC-05)
## What's NOT here yet        (Phase 1)
```

(The Phase 1 H1 / intro paragraphs / What This Is / Core Value / Prerequisites / IDE / setup / First-run / "What's NOT here yet" / unchanged bullets all remain untouched. Phase 7's DOC-01 will later rewrite these for the full step-by-step walkthrough.)

## Verification Gate Output

### Task 1 — README acceptance criteria (all 13 PASS)

```
test -f README.md: PASS
DOC-05 title (== 1): 1
duplicated per service on purpose (== 1): 1
@AutoConfiguration (== 1): 1
hide one of the two readings (== 1): 1
DOC-03 title (== 1): 1
producer/config/OtelSdkConfiguration.java (>= 1): 1
consumer/config/OtelSdkConfiguration.java (>= 1): 1
HttpServerSpanFilter (>= 1): 2
step-01-baseline lacks Current (== 0): 0
step-02-traces has Current (== 1): 1
intentional setup (== 1): 1
Section ordering (Prerequisites < Workshop < Reading < Why duplicated < What's NOT): PASS
Phase-1 sections still present: PASS
All 6 step tags still listed: PASS
Code fences balanced: PASS
```

### Task 2 — Phase 2 ROADMAP success criteria

**Criterion 1 — TWO distinct traces in Tempo per POST /orders, correct service.name labels, zero unknown_service:java:**

```
POST /orders status: 202 → orderId=1dfb0266-22b2-451c-9563-90bfc2589024
Producer query traces: count=2 (latest f00aab524fbbe2f4f90fa2cd4e6b7a57 root=order-producer/POST /orders)
Consumer query traces: count=2 (latest d7480cc2579a5fc9471330a0c9474228 root=order-consumer/orders.created process)
PT=f00aab524fbbe2f4f90fa2cd4e6b7a57 != CT=d7480cc2579a5fc9471330a0c9474228   OK
unknown_service:java trace count: 0   OK
```

**Criterion 2 — Ctrl-C flushes the last batch (graceful shutdown via @Bean(destroyMethod="close")):**

```
BEFORE_PT trace count: 1
POST /orders status: 202 → orderId=76e183fb-e907-4015-93f6-6bb26f8af57f
Sending SIGTERM to producer PID 2595562...
Producer exited after 1s
AFTER_PT trace count: 2
OK Criterion 2: traces grew 1->2 after Ctrl-C
```

(1s shutdown latency, well inside the 10s `OpenTelemetrySdk.close()` join window. The trace count from `service.name=order-producer` increased AFTER the kill — proving the BSP flushed the just-emitted POST's spans before exit.)

**Criterion 3 — Both OtelSdkConfiguration files heavily commented (>=40 lines, multi-paragraph sampler tradeoff):**

```
Producer comment lines: 137 (>= 40 required)   OK
Consumer comment lines: 131 (>= 40 required)   OK
Producer sampler teaches traceIdRatioBased(0.1):   OK
Consumer sampler teaches traceIdRatioBased(0.1):   OK
```

**Criterion 4 — Producer trace = SERVER+INTERNAL+PRODUCER; Consumer trace = CONSUMER+INTERNAL; CONSUMER root parentSpanId empty:**

(Verified TWICE: once on the original trace pair captured during Criterion 1, once on a fresh trace pair after restarting the producer post-Criterion 2.)

```
producer span count: 3
producer kinds (1=INT,2=SRV,4=PROD): {4: 1, 1: 1, 2: 1}
producer names: ['orders.created publish', 'OrderService.place', 'POST /orders']
OK producer: SERVER+INTERNAL+PRODUCER all present

consumer span count: 2
consumer kinds (1=INT,5=CONS): {1: 1, 5: 1}
consumer names: ['ProcessingService.process', 'orders.created process']
OK consumer: CONSUMER+INTERNAL all present
OK: CONSUMER parentSpanId empty/zero (Context.root() honored at runtime)
```

(Post-restart re-verification used PT=`8f860158ad98d0d47f775829d4bcbf80` and CT=`39b9ec7c929fd0e12b36c697b1a744e1`. Same green output.)

**Criterion 5 — README has the per-service-duplication callout (DOC-05):**

```
^## Why is OtelSdkConfiguration.java duplicated?$    PRESENT
'duplicated per service on purpose'                  PRESENT
'hide one of the two readings'                       PRESENT
```

**Criterion 6 — pre-tag clean tree:**

```
git status --porcelain  →  (empty)  → OK
mise run verify:bom  →  Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
pgrep -af java/mvn  →  (none)  →  OK: no leftover processes
ports 8080 / 8081  →  (free)  →  OK
```

All 6 criteria simultaneously green at commit `0f6c99e` (HEAD on main). The annotated tag `step-02-traces` may be applied here; T2 deliberately stops short of doing so per `<checkpoint_handling>`.

### Phase 2 invariant preserved (cross-check)

```
$ mise run verify:bom
[verify:bom] $ set -e
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

## Decisions Made

- **Followed the plan's verbatim Edit-1/Edit-2/Edit-3 instructions character-for-character.** The plan provides exact old/new strings for the bullet updates and exact verbatim content for the two new sections; reproduced them without abbreviation or rewording.
- **Did NOT apply the annotated tag.** Per the orchestrator's `<checkpoint_handling>` directive, the tag is staged for user gate. The executor returns the SHA + exact tag command in its checkpoint message; the orchestrator applies the tag after user approval.
- **Did NOT update STATE.md / ROADMAP.md / REQUIREMENTS.md** beyond what this plan-level SUMMARY documents. Phase 2 SHIPPED status (6/6 plans, WORK-01 Phase-2-portion complete) is load-bearing on the tag's existence; deferring the state updates to the orchestrator's post-tag commit keeps the SHA the tag points to and the SHA documenting Phase 2 SHIPPED status identical (atomicity).
- **Used Grafana datasource proxy URL** (`http://localhost:3000/api/datasources/proxy/uid/tempo/api/...`) for Tempo queries — port :3200 is not exposed externally by `grafana/otel-lgtm:0.26.0` (only :3000 / :4317 / :4318 are bound). Same pattern Plans 02-02, 02-04, and 02-05 documented.

## Deviations from Plan

None — plan executed exactly as written. The plan's three Edit blocks reproduced character-for-character; the six criteria all PASS on first execution against a clean working tree; Task 3 (the tag) is intentionally NOT executed because the plan explicitly types T3 as `checkpoint:human-verify` with `gate="blocking"`.

## Issues Encountered

- **Initial consumer port conflict.** First attempt at restarting the consumer used `mvn -pl consumer-service spring-boot:run` with no `-Dserver.port` override; consumer defaulted to 8080 and crashed because the producer was already on 8080. Resolved on retry by adding `-Dspring-boot.run.jvmArguments="-Dserver.port=8081"` to the consumer's invocation (matches `mise.toml` line 105's `dev:consumer` task definition). Producer remained healthy throughout — only the consumer was restarted.
- **Tempo trace fetch returned non-JSON when piped through one shell var.** `curl ... | python3 -c '...'` worked for search endpoints but produced empty stdin for `traces/<id>` until I redirected curl output to an intermediate file (`/tmp/pt_tr.json`) and read from disk. The trace JSON itself is well-formed and exactly what the plan's verification snippet expects; only the shell-piping shape changed.
- **Background-task launcher SIGPIPE on cleanup.** The `kill $PID_P $PID_C` command's bash wrapper exits 144 (SIGPIPE) when its own argv matches `pgrep`'s search pattern (false-positive self-match). Recognised as the same trap Plan 02-03's SUMMARY documented; switched to `ps -eo pid,comm,args | grep -E '^\s*[0-9]+\s+(java|mvn)'` for the leftover-process check, which excludes shell wrappers cleanly.

## User Setup Required

**One pending user action: approve and apply the annotated tag `step-02-traces`.**

The plan's T3 is intentionally a `checkpoint:human-verify` gate. The executor has:
- Verified all 6 Phase 2 ROADMAP success criteria simultaneously green at HEAD `0f6c99e`
- Confirmed clean working tree (`git status --porcelain` empty)
- Staged the exact tag command + annotated message body for the orchestrator/user

**The tag is local-only after application** (per the `git push origin step-02-traces` follow-up the user runs when ready — same convention as Phase 1's `step-01-baseline`, which is also still local-only per STATE.md line 6).

No other external services, secrets, or environment variables required.

## Threat Flags

None new. Plan 02-06's threat register (T-2-06-01 through T-2-06-05) covered tag tampering, README content disclosure, force-push tag overwrite, repudiation of meaningless tag messages, and architectural-intent disclosure. All `mitigate` dispositions are honored:
- T-2-06-01 (annotated tag, NOT lightweight): the staged command uses `git tag -a` (not `git tag`); the orchestrator's apply step will re-verify via `git for-each-ref --format='%(objecttype)' refs/tags/step-02-traces` returning `tag` (not `commit`).
- T-2-06-04 (meaningful tag message): the staged annotated message body lists all six Phase 2 success criteria and explicitly names "Phase 2 — manual SDK bootstrap; producer + consumer in DIFFERENT traces" — content auditable.
- `accept` dispositions (T-2-06-02 README content, T-2-06-03 force-push protection at the GitHub level, T-2-06-05 architectural-intent in DOC-05) remain unchanged from the plan's threat model.

No new threat surface introduced by the README delta (no new external URLs, no new dependencies, no new runtime code paths). The git tag introduces a public-readable workshop artifact whose threat surface is the same one Phase 1's `step-01-baseline` already accepted.

## Self-Check: PASSED

Verified after writing this SUMMARY (per `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `README.md`
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-06-readme-and-exit-gate-SUMMARY.md`

**Commit (FOUND in git log):**
- `0f6c99e` — `docs(02-06): add DOC-03 + DOC-05 README sections; mark step-02-traces Current`

## Next Phase Readiness

- **Phase 2 SHIPPED status pending tag application.** Once the orchestrator/user applies `step-02-traces`, all six Phase 2 ROADMAP success criteria are simultaneously satisfied (the tag IS criterion #6's "this tag reproduces the state on git checkout"), Phase 2 SHIPPED status flips to true, and STATE.md / ROADMAP.md / REQUIREMENTS.md update in the same atomic commit the orchestrator applies.
- **Phase 3 input is locked.** The composite `W3CTraceContextPropagator + W3CBaggagePropagator` is wired in both `OpenTelemetrySdk` instances (Phase 3 calls `openTelemetry.getPropagators().getTextMapPropagator()` to use them); the inline PRODUCER + CONSUMER spans in `OrderPublisher.publish` and `OrderListener.onOrder` will be REPLACED in Phase 3 by `otel-bootstrap`'s `TracingMessagePostProcessor` + `TracingMessageListenerAdvice` per CONTEXT.md D-09 / deferred-ideas line 201. The Phase-2→Phase-3 git diff is well-defined (Phase 3's SC #5 mandates it be readable in one viewing).
- **Phase 3 hand-off note (per CONTEXT.md and PLAN.md `<output>`):** APP-04 (deterministic 10% failure) and TRACE-09 (`recordException` + `setStatus(ERROR)` wired to actual failures) land alongside the propagation pair. The D-03 catch shape already in place across all 5 Phase 2 spans means Phase 3 only needs to add the THROW site, not restructure existing methods.
- **Phase 7 DOC-04 hand-off:** the broken-vs-fixed Tempo screenshot pair will be captured at `step-02-traces` (TWO disconnected traces) vs `step-03-context-propagation` (ONE joined trace). Plan 02-06 freezes the Phase-2 baseline state at the tag; Phase 7 facilitator workflow takes the screenshot.
- **Workshop attendees can `git checkout step-02-traces`** (post-tag-apply) and reproduce the green-Phase-2 state: two services boot in <1s each, `POST /orders` returns 202, Tempo shows TWO disconnected traces, RabbitMQ Mgmt UI shows messages WITHOUT a `traceparent` header (proving Phase 3's missing-propagation state), README's `## Reading the code` section names what to read in the IDE.

---
*Phase: 02-manual-sdk-bootstrap-first-traces*
*Plan: 06 (readme-and-exit-gate)*
*Completed: 2026-05-01*
