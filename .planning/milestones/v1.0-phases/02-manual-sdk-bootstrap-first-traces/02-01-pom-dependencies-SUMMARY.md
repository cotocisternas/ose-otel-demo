---
phase: 02-manual-sdk-bootstrap-first-traces
plan: 01
subsystem: infra
tags: [maven, pom, opentelemetry, semconv, bom, dependencyConvergence, mise]

# Dependency graph
requires:
  - phase: 01-baseline-scaffold
    provides: "BOM-import parent POM with OTel-first ordering (opentelemetry-bom:1.61.0 → opentelemetry-instrumentation-bom-alpha:2.27.0-alpha → spring-boot-dependencies:3.4.13) and maven-enforcer-plugin's dependencyConvergence rule bound to validate phase"
provides:
  - "Producer + consumer service POMs each declaring 5 io.opentelemetry* dependencies (opentelemetry-api, opentelemetry-sdk, opentelemetry-exporter-otlp via opentelemetry-bom:1.61.0; opentelemetry-semconv:1.40.0 + opentelemetry-semconv-incubating:1.40.0-alpha pinned directly)"
  - "Phase 2 invariant on the classpath: every io.opentelemetry* artifact appears at exactly ONE version across the reactor (verified by mvn dependency:tree + maven-enforcer dependencyConvergence + the rewritten mise verify:bom task)"
  - "Per-service-duplication ethos extended to POMs: producer-service/pom.xml and consumer-service/pom.xml carry IDENTICAL OTel dependency blocks (DOC-05 forward compat)"
  - "Inverted mise verify:bom task — same task name, Phase 2 semantics (one-version-per-OTel-artifact, exits 0 with 'Phase 2 baseline confirmed')"
affects:
  - "02-02-producer-sdk-config (compiles against io.opentelemetry.api.*, io.opentelemetry.sdk.*, io.opentelemetry.exporter.otlp.trace.*, io.opentelemetry.semconv.* — all five new deps)"
  - "02-03-consumer-sdk-config (same compile-time surface as producer)"
  - "02-04-producer-instrumentation (consumes MessagingIncubatingAttributes from semconv-incubating)"
  - "02-05-consumer-instrumentation (same)"
  - "02-06-readme-and-exit-gate (asserts the 6 ROADMAP success criteria are green; invariant cross-check at tag time)"
  - "Phase 4 (metrics) — opentelemetry-exporter-otlp artifact already present, will reuse for OtlpGrpcMetricExporter"
  - "Phase 5 (logs correlation) — opentelemetry-exporter-otlp covers OtlpGrpcLogRecordExporter; opentelemetry-logback-appender-1.0 still deferred to Phase 5 plan"

# Tech tracking
tech-stack:
  added:
    - "io.opentelemetry:opentelemetry-api:1.61.0 (BOM-managed)"
    - "io.opentelemetry:opentelemetry-sdk:1.61.0 (BOM-managed; brings opentelemetry-sdk-{common,trace,metrics,logs} transitively)"
    - "io.opentelemetry:opentelemetry-exporter-otlp:1.61.0 (BOM-managed; brings opentelemetry-exporter-{otlp-common,common,sender-okhttp} + opentelemetry-sdk-extension-autoconfigure-spi as runtime transitives)"
    - "io.opentelemetry.semconv:opentelemetry-semconv:1.40.0 (NOT BOM-managed; pinned literal version)"
    - "io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha (NOT BOM-managed; pinned literal version; REQUIRED for messaging.* + deployment.* attribute keys per RESEARCH FLAG #2)"
  patterns:
    - "BOM-managed-where-possible / pinned-where-not — the SDK three (api/sdk/exporter-otlp) carry no <version> tag because they live in opentelemetry-bom:1.61.0; the two semconv coords are NOT in any BOM the parent imports and pin 1.40.0 / 1.40.0-alpha directly"
    - "Per-service-duplication of OTel dependency blocks — producer-service/pom.xml and consumer-service/pom.xml carry the IDENTICAL 5-dep block (DOC-05 forward compat for the SDK config classes that follow)"
    - "Phase-aware mise verify:bom — task name keeps its meaning ('verify the BOM-managed dependency state is correct'); semantics evolve with the phase (Phase 1: zero libs; Phase 2 onward: one version per artifact)"

key-files:
  created: []
  modified:
    - "producer-service/pom.xml — added 5 OTel <dependency> entries (api/sdk/exporter-otlp BOM-managed; semconv 1.40.0 + semconv-incubating 1.40.0-alpha pinned); replaced Phase 1 invariant XML comment with Phase 2 invariant text; updated <description> to 'Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-02).'"
    - "consumer-service/pom.xml — added the IDENTICAL 5 OTel <dependency> entries; same comment + description update (Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-03).)"
    - "mise.toml — rewrote [tasks.\"verify:bom\"] block: description, run script (replaced 'zero OTel libs' check with 'one version per OTel artifact' check), and success message ('Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.')"

key-decisions:
  - "Inverted mise verify:bom task in place rather than introducing a new verify:phase-2 task — RESEARCH §'Wave 0 Gaps' recommendation; preserves task-name continuity ('verify the BOM-managed dependency state is correct') while flipping the assertion to match the new phase"
  - "Removed `-q` flag from `mvn dependency:tree` invocation in the verify:bom script (Rule 1 deviation): the maven-dependency-plugin emits its tree via [INFO] logs, which `-q` (quiet) suppresses; verbatim plan script returned empty OUTPUT and tripped the COUNT==0 false-alarm branch"
  - "Did NOT touch parent pom.xml — BOM imports + maven-enforcer rules already correct from Phase 1 (verified in 1-01-SUMMARY.md); Phase 2 is purely service-POM additions"

patterns-established:
  - "Phase-aware invariant XML comments at the top of each <dependencies> block — replace per phase; current text references parent pom.xml lines for the maven-enforcer rule, which is the load-bearing check"
  - "5-dep OTel block ordering: api → sdk → exporter-otlp (BOM-managed trio) → semconv (stable, 1.40.0) → semconv-incubating (1.40.0-alpha) — matches the order in 02-PATTERNS.md and is duplicated identically in both service POMs"
  - "verify:bom evolves with the phase but keeps its name — workshop attendees running `mise run verify:bom` get a meaningful, phase-appropriate gate at every checkpoint"

requirements-completed: [TRACE-01]

# Metrics
duration: 5min
completed: 2026-05-01
---

# Phase 2 Plan 01: POM Dependencies Summary

**Five OpenTelemetry Maven dependencies (api/sdk/exporter-otlp BOM-managed via opentelemetry-bom:1.61.0; opentelemetry-semconv 1.40.0 + opentelemetry-semconv-incubating 1.40.0-alpha pinned) added identically to producer-service/pom.xml and consumer-service/pom.xml; mise verify:bom inverted to assert the Phase 2 invariant of one version per OTel artifact across the reactor.**

## Performance

- **Duration:** ~5 min (266 s)
- **Started:** 2026-05-01T16:42:56Z
- **Completed:** 2026-05-01T16:47:22Z
- **Tasks:** 2 (Task 1 POM dependency additions across both service modules; Task 2 mise verify:bom rewrite)
- **Files modified:** 3 (`producer-service/pom.xml`, `consumer-service/pom.xml`, `mise.toml`)

## Accomplishments

- **Producer + consumer POMs carry the IDENTICAL 5-dep OTel block.** The Phase 1 "NO OpenTelemetry deps in Phase 1" XML comment is replaced with a multi-paragraph Phase 2 invariant comment that points workshop attendees at parent pom.xml's maven-enforcer rule (lines 113-145) and at .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md §B.
- **Phase 2 INVARIANT proven across the reactor.** `mvn dependency:tree -Dincludes=io.opentelemetry` shows one version per artifact in BOTH service modules: `opentelemetry-api`/`-sdk`/`-exporter-otlp` (and their transitives `-context`, `-common`, `-sdk-{common,trace,metrics,logs}`, `-exporter-{otlp-common,common,sender-okhttp}`, `-sdk-extension-autoconfigure-spi`) at 1.61.0; `opentelemetry-semconv` at 1.40.0; `opentelemetry-semconv-incubating` at 1.40.0-alpha. ZERO duplicate `(groupId:artifactId)` pairs at differing versions across either module.
- **Forbidden artifacts ABSENT** — no `opentelemetry-sdk-extension-autoconfigure` (D-12 forbids), no `opentelemetry-logback-appender-1.0` (Phase 5 only), no legacy `io.opentelemetry:opentelemetry-semconv` (D-13 forbids; the deprecated coord that ships in the SDK BOM).
- **`mvn -DskipTests install` BUILD SUCCESS across the reactor** with maven-enforcer's `dependencyConvergence` rule firing green on every module: `Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed`.
- **`mise run verify:bom` exits 0** with the new Phase 2 success message: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.` All 14 other mise tasks (preflight, infra:up/down/reset/logs, build, test, dev, dev:{producer,consumer}, demo:order, ui:{grafana,rabbitmq}) remain intact.

## Task Commits

Each task was committed atomically using normal commits (sequential mode on main; hooks active):

1. **Task 1: Add 5 OTel deps to producer-service/pom.xml + consumer-service/pom.xml** — `f836d12` (feat)
2. **Task 2: Invert mise verify:bom task — assert one-version-per-OTel-artifact** — `cf7de72` (fix; carries the Rule 1 `mvn -q` deviation)

**Plan metadata commit:** _(produced after this SUMMARY.md is written; will commit SUMMARY + STATE + ROADMAP + REQUIREMENTS as `docs(02-01): complete pom-dependencies plan`)_

## Files Created/Modified

- **`producer-service/pom.xml`** (modified) — `<description>` updated to `Producer service: HTTP POST /orders → publish OrderCreated to RabbitMQ. Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-02).`. Phase 1 invariant XML comment replaced with the Phase 2 invariant block (15 lines explaining BOM management vs. direct pinning vs. per-service-duplication ethos). Five `<dependency>` entries inserted between `spring-boot-starter-actuator` and `spring-boot-starter-test` in this exact order: `opentelemetry-api`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp` (no version tags — BOM-managed), `opentelemetry-semconv` (version `1.40.0`), `opentelemetry-semconv-incubating` (version `1.40.0-alpha`).
- **`consumer-service/pom.xml`** (modified) — same 5 deps in the same order; same XML invariant comment. `<description>` updated to `Consumer service: @RabbitListener processes OrderCreated. Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-03).`. The Phase 1 "RESEARCH.md Open Question #1" sub-paragraph (about including `spring-boot-starter-web` so `/actuator/health` is reachable on port 8081 — APP-05) was retired alongside the rest of the Phase 1 comment block; the actual `spring-boot-starter-web` dependency itself was NOT removed — it remains in place because it's still required by APP-05.
- **`mise.toml`** (modified) — `[tasks."verify:bom"]` block fully rewritten in place: same task name, new description (`Phase 2 invariant: one version per io.opentelemetry* artifact across the reactor`), new run script (extracts `groupId:artifactId:type` per dependency:tree line, sorts unique, looks for the same key appearing at multiple distinct versions; emits `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.` on success). One bug fix vs. the verbatim plan: removed `-q` from the `mvn dependency:tree` call (see Deviations section below). All other mise tasks (preflight, infra:up/down/reset/logs, build, test, dev, dev:{producer,consumer}, demo:order, ui:{grafana,rabbitmq}) untouched.

## POM Diff Highlights

### Producer's final OTel dependency block (verbatim, lines 38-103 in producer-service/pom.xml)

```xml
<!--
  OpenTelemetry SDK runtime — versions managed by opentelemetry-bom:1.61.0
  in parent pom.xml. Three artifacts cover the API surface, the SDK
  implementation, and the OTLP gRPC exporter (the single artifact that
  ships exporters for traces, metrics, and logs — Phase 2 only uses the
  trace exporter; Phase 4 + Phase 5 reuse the same artifact for the
  meter and logger pipelines).
-->
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-api</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-sdk</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!--
  Stable semantic conventions — the new io.opentelemetry.semconv coordinate
  maintained in semantic-conventions-java (NOT the deprecated
  io.opentelemetry:opentelemetry-semconv that ships in the SDK BOM).
  Carries ServiceAttributes, HttpAttributes, UrlAttributes,
  ServerAttributes — everything the SERVER span needs.
  Pin to 1.40.0 directly: this artifact is NOT BOM-managed.
-->
<dependency>
  <groupId>io.opentelemetry.semconv</groupId>
  <artifactId>opentelemetry-semconv</artifactId>
  <version>1.40.0</version>
</dependency>

<!--
  Incubating semantic conventions — REQUIRED in Phase 2, not optional.
  Carries MessagingIncubatingAttributes (for PRODUCER + CONSUMER spans)
  and DeploymentIncubatingAttributes (for the Resource's
  deployment.environment.name). Without this artifact the SDK config
  and the messaging spans both fail to compile.

  Carries the -alpha qualifier because messaging conventions are still
  evolving in the OTel spec; no shipping demo can use stable-only
  constants for AMQP semconv yet.
-->
<dependency>
  <groupId>io.opentelemetry.semconv</groupId>
  <artifactId>opentelemetry-semconv-incubating</artifactId>
  <version>1.40.0-alpha</version>
</dependency>
```

### Consumer carries the IDENTICAL block

`diff producer-service/pom.xml consumer-service/pom.xml | grep -E '<(dependency|version|artifactId|groupId)>' | grep opentelemetry` returns no rows for the OTel block — the artifactIds and versions are character-for-character identical between the two POMs, exactly as the per-service-duplication ethos (DOC-05) requires. The full diff between the two POMs comes from (a) `<artifactId>producer-service</artifactId>` vs `<artifactId>consumer-service</artifactId>`, (b) `<name>` and `<description>` strings, (c) starter-ordering noise (the consumer puts `amqp` before `actuator` before `web`; the producer puts `web` before `amqp` before `actuator`), and (d) the absence of `spring-boot-starter-web` in the producer's Phase 1 history — none of which touches the Phase 2 OTel block.

## Verification Gate Output

### `mvn -DskipTests install` — full reactor with `dependencyConvergence` enforcer

```
[INFO] --- enforcer:3.5.0:enforce (enforce) @ producer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
...
[INFO] --- enforcer:3.5.0:enforce (enforce) @ consumer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
...
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for OSE OTel Demo (parent) 0.1.0-SNAPSHOT:
[INFO]
[INFO] OSE OTel Demo (parent) ............................. SUCCESS [  0.121 s]
[INFO] OSE OTel Demo (otel-bootstrap) ..................... SUCCESS [  0.241 s]
[INFO] OSE OTel Demo (producer) ........................... SUCCESS [  0.096 s]
[INFO] OSE OTel Demo (consumer) ........................... SUCCESS [  0.026 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### `mvn -pl producer-service dependency:tree` — Phase 2 INVARIANT

```
[INFO] +- io.opentelemetry:opentelemetry-api:jar:1.61.0:compile
[INFO] |  \- io.opentelemetry:opentelemetry-context:jar:1.61.0:compile
[INFO] |     \- io.opentelemetry:opentelemetry-common:jar:1.61.0:compile
[INFO] +- io.opentelemetry:opentelemetry-sdk:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-sdk-common:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-sdk-trace:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-sdk-metrics:jar:1.61.0:compile
[INFO] |  \- io.opentelemetry:opentelemetry-sdk-logs:jar:1.61.0:compile
[INFO] +- io.opentelemetry:opentelemetry-exporter-otlp:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-exporter-otlp-common:jar:1.61.0:runtime
[INFO] |  |  \- io.opentelemetry:opentelemetry-exporter-common:jar:1.61.0:runtime
[INFO] |  +- io.opentelemetry:opentelemetry-exporter-sender-okhttp:jar:1.61.0:runtime
[INFO] |  \- io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi:jar:1.61.0:runtime
[INFO] +- io.opentelemetry.semconv:opentelemetry-semconv:jar:1.40.0:compile
[INFO] +- io.opentelemetry.semconv:opentelemetry-semconv-incubating:jar:1.40.0-alpha:compile
```

(The `-sdk-extension-autoconfigure-spi` artifact is pulled as a TRANSITIVE runtime dependency of `opentelemetry-exporter-otlp` — this is the SPI side of autoconfigure, NOT the autoconfigure runtime itself. D-12 forbids `opentelemetry-sdk-extension-autoconfigure` which is a DIFFERENT artifact: the runtime at `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure`. Compare: `opentelemetry-sdk-extension-autoconfigure-spi` IS present (transitive, fine — it's just the SPI interfaces); `opentelemetry-sdk-extension-autoconfigure` (the runtime) is NOT present. D-12 honored.)

### `mvn -pl consumer-service dependency:tree` — IDENTICAL shape

```
[INFO] +- io.opentelemetry:opentelemetry-api:jar:1.61.0:compile
[INFO] |  \- io.opentelemetry:opentelemetry-context:jar:1.61.0:compile
[INFO] |     \- io.opentelemetry:opentelemetry-common:jar:1.61.0:compile
[INFO] +- io.opentelemetry:opentelemetry-sdk:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-sdk-common:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-sdk-trace:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-sdk-metrics:jar:1.61.0:compile
[INFO] |  \- io.opentelemetry:opentelemetry-sdk-logs:jar:1.61.0:compile
[INFO] +- io.opentelemetry:opentelemetry-exporter-otlp:jar:1.61.0:compile
[INFO] |  +- io.opentelemetry:opentelemetry-exporter-otlp-common:jar:1.61.0:runtime
[INFO] |  |  \- io.opentelemetry:opentelemetry-exporter-common:jar:1.61.0:runtime
[INFO] |  +- io.opentelemetry:opentelemetry-exporter-sender-okhttp:jar:1.61.0:runtime
[INFO] |  \- io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi:jar:1.61.0:runtime
[INFO] +- io.opentelemetry.semconv:opentelemetry-semconv:jar:1.40.0:compile
[INFO] +- io.opentelemetry.semconv:opentelemetry-semconv-incubating:jar:1.40.0-alpha:compile
```

### `mise run verify:bom`

```
[verify:bom] $ set -e
Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.
```

## Decisions Made

- **Inverted `verify:bom` IN PLACE** rather than introducing a parallel `verify:phase-2` task. Per RESEARCH §"Wave 0 Gaps" recommendation: the task name "verify:bom" keeps its meaning (verify the BOM-managed dependency state is correct) while the SEMANTICS evolve with the phase. Workshop attendees running `mise run verify:bom` always get a meaningful gate, regardless of which checkpoint they've checked out.
- **Did NOT touch `parent pom.xml`.** The BOM imports (`opentelemetry-bom:1.61.0`, `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`, `spring-boot-dependencies:3.4.13` in OTel-first order) plus the `maven-enforcer-plugin`'s `dependencyConvergence` rule are exactly what Phase 2 needs — they were established in 1-01 and verified in 1-01-SUMMARY.md. Phase 2 Plan 01 is purely service-POM additions plus one mise task rewrite.
- **Followed the plan's ordering constraint** that the 5 new `<dependency>` blocks be inserted between `spring-boot-starter-actuator` and `spring-boot-starter-test` in BOTH POMs. This puts the OTel deps in scope `compile` BEFORE the test scope sweep, which is the standard ordering convention for production-then-test.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed `-q` flag from `mvn dependency:tree` invocation in mise verify:bom script**

- **Found during:** Task 2 (verification of `mise run verify:bom` exit code).
- **Issue:** The plan's verbatim script for the new `verify:bom` task captured `OUTPUT=$(mvn -q dependency:tree -Dincludes=io.opentelemetry 2>&1)`. The `maven-dependency-plugin`'s `tree` goal emits its output via `[INFO]` log lines, which `mvn -q` (quiet mode) suppresses. The result: `OUTPUT` came back empty, the regex extraction below it produced zero matches, and the COUNT==0 fallback branch fired, exiting non-zero with `ERROR: zero OpenTelemetry artifacts on the classpath — Phase 2 expects the SDK to be present.` (a false alarm — the artifacts ARE on the classpath, as proven independently by `mvn -pl producer-service dependency:tree` which doesn't use `-q`).
- **Fix:** Removed the `-q` flag — `OUTPUT=$(mvn dependency:tree -Dincludes=io.opentelemetry 2>&1)`. Added a multi-line comment block above the assignment explaining why `-q` is omitted. Downstream grep+awk filtering keeps the output focused; the verify:bom task's stdout still emits only the success message on success and the violation list + diagnostic dump on failure.
- **Files modified:** `mise.toml`
- **Verification:** `mise run verify:bom` now exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.` Same script, with one artificial duplicate injected, would emit the violation list and exit 1 (the failure path was inspected by reading the script — not exercised because injecting a duplicate would require either tampering with parent BOM order or staging a parallel module that overrides a managed version, both of which are out of scope for Plan 01).
- **Committed in:** `cf7de72` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug — `-q` flag suppressing the maven-dependency-plugin's [INFO] output that the script then tried to parse).
**Impact on plan:** No scope change, no architectural change, no new dependencies. The fix tightens the plan: the verbatim script returned a false-alarm error against a perfectly valid Phase 2 reactor; the patched script returns the expected success message. Documented in mise.toml comments above the changed line so future readers see the trap.

## Issues Encountered

- **`mise x -- mvn ...` is the right invocation pattern** for this worktree. Plain `mvn` is on PATH (mise activated automatically on `cd`), and `mise run verify:bom` activates the toolchain via mise. Both worked. No environment surprises.

## User Setup Required

None — no external services, no secrets, no environment variables. The new OTel artifacts are pulled by `mvn install` from Maven Central on first run; the `~/.m2/repository` cache is now populated with `io.opentelemetry/*/1.61.0` and `io.opentelemetry/semconv/{opentelemetry-semconv/1.40.0,opentelemetry-semconv-incubating/1.40.0-alpha}` JARs and POMs.

## Threat Flags

None. Plan 01's threat register (T-2-01-01 through T-2-01-05) covered Maven supply-chain risks (group-ID typo, floating versions, deprecated coord shadowing, comment leakage, untrusted plugin repos). All `mitigate` dispositions are honored:
- Both new semconv coords explicitly use the `io.opentelemetry.semconv` group prefix (NOT `io.opentelemetry`) — verified by `grep` in acceptance criteria.
- All five new dependencies pin exact versions (BOM-managed for the SDK trio; literal `1.40.0` / `1.40.0-alpha` for the semconv pair).
- The deprecated `<groupId>io.opentelemetry</groupId><artifactId>opentelemetry-semconv</artifactId>` declaration is ABSENT in both POMs.
- POM XML comments are workshop pedagogy only; no internal hosts or credentials.
- No new `<pluginRepositories>` declared; no new plugins added.

`accept` dispositions (XML comment content, existing maven-enforcer-plugin) remain unchanged from 1-01.

## Self-Check: PASSED

Verified after writing this SUMMARY (see `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `producer-service/pom.xml`
- `consumer-service/pom.xml`
- `mise.toml`
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-01-pom-dependencies-SUMMARY.md`

**Commits (all FOUND in git log):**
- `f836d12` — feat(02-01): add 5 OTel deps to both service POMs (Phase 2 baseline)
- `cf7de72` — fix(02-01): invert mise verify:bom — assert Phase 2 invariant (one version per OTel artifact)

## Next Phase Readiness

- **Wave 2 plans (02-02-producer-sdk-config and 02-03-consumer-sdk-config) are unblocked.** Both can now compile against `io.opentelemetry.api.*`, `io.opentelemetry.sdk.*`, `io.opentelemetry.exporter.otlp.trace.*`, `io.opentelemetry.semconv.*`, and `io.opentelemetry.semconv.incubating.*` packages — every import the SDK config + HttpServerSpanFilter classes will need.
- **Wave 3 plans (02-04-producer-instrumentation and 02-05-consumer-instrumentation) inherit the `MessagingIncubatingAttributes` constants** from `opentelemetry-semconv-incubating:1.40.0-alpha` — the deprecated `MESSAGING_OPERATION` constant is available for reference (avoid; use `MESSAGING_OPERATION_TYPE` per RESEARCH FLAG #1) and the current `MessagingOperationTypeIncubatingValues.{SEND,PROCESS}` enum is on the classpath.
- **Phase 4 (metrics) and Phase 5 (logs) get a head start.** `opentelemetry-exporter-otlp` is a single artifact that ships exporters for all three signals; Phase 4's `OtlpGrpcMetricExporter` and Phase 5's `OtlpGrpcLogRecordExporter` will both resolve from the JAR already on the classpath.
- **The `verify:bom` task is now phase-aware forever.** Each future phase that adds a new OTel artifact will continue to satisfy "one version per artifact" automatically (BOM-managed) or explicitly (pinned), and the existing task will keep validating that invariant.

---
*Phase: 02-manual-sdk-bootstrap-first-traces*
*Plan: 01 (pom-dependencies)*
*Completed: 2026-05-01*
