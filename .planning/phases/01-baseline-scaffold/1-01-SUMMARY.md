---
phase: 01-baseline-scaffold
plan: 01
subsystem: infra
tags: [maven, multi-module, bom, opentelemetry, spring-boot, enforcer]

# Dependency graph
requires: []
provides:
  - Maven multi-module reactor (parent + 3 children)
  - BOM-import dependency-management with OTel-first ordering (OTel SDK 1.61.0 → OTel Instrumentation alpha 2.27.0-alpha → Spring Boot 3.4.13)
  - maven-enforcer-plugin bound to validate phase (dependencyConvergence + requireMavenVersion[3.9.0,) + requireJavaVersion[17,18))
  - Empty otel-bootstrap module (Phase 3 fills this)
  - Producer/consumer service modules carrying only Spring Boot starters (no OTel deps in Phase 1)
affects:
  - 01-baseline-scaffold (sister plans 1-02 mise/repo-tooling, 1-03 docker-compose, 1-04 producer skeleton, 1-05 consumer skeleton, 1-06 README/checkpoint)
  - 02-traces (will add the first io.opentelemetry:* <dependency> resolved against the OTel BOM, not Spring Boot's managed version)
  - 03-context-propagation (otel-bootstrap module gets populated with the AMQP propagation pair)
  - 04-metrics (consumes the BOM-managed opentelemetry-exporter-otlp)
  - 05-logs (consumes the alpha BOM for opentelemetry-logback-appender-1.0)
  - 06-tests (consumes BOM-managed Testcontainers + opentelemetry-sdk-testing)

# Tech tracking
tech-stack:
  added:
    - "Apache Maven 3.9.11"
    - "Amazon Corretto 17.0.13.11.1"
    - "io.opentelemetry:opentelemetry-bom:1.61.0 (BOM import — no <dependency> consumers yet)"
    - "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.27.0-alpha (BOM import — no <dependency> consumers yet)"
    - "org.springframework.boot:spring-boot-dependencies:3.4.13 (BOM import; manages all spring-boot-starter-* deps)"
    - "org.apache.maven.plugins:maven-enforcer-plugin:3.5.0"
    - "org.springframework.boot:spring-boot-maven-plugin:3.4.13 (in pluginManagement only; child services use it without version)"
  patterns:
    - "BOM-import parent (no <parent>spring-boot-starter-parent</parent>) — controls dependencyManagement ordering explicitly"
    - "First-declaration-wins rule for <scope>import</scope> BOMs: OTel ahead of Spring Boot ensures Phase 2+ resolves OTel artifacts to OTel-managed versions"
    - "Enforcer rules at plugin level (inherited by every execution) so BOTH validate-phase auto-runs AND direct `mvn enforcer:enforce` CLI invocations see the rules"
    - "Phase-bound validate execution makes BOM-convergence gate fire on every `mvn install` / `mvn package` — no opt-in needed"

key-files:
  created:
    - "pom.xml — aggregator parent POM (no <parent>; BOM imports + enforcer)"
    - "producer-service/pom.xml — Spring Boot starters web/amqp/actuator/test (no OTel deps)"
    - "consumer-service/pom.xml — Spring Boot starters amqp/actuator/web/test (web included for /actuator/health on port 8081)"
    - "otel-bootstrap/pom.xml — empty placeholder (Phase 3 populates with AMQP propagation pair)"
    - "otel-bootstrap/src/main/java/com/example/otel/package-info.java — single source so empty module compiles to a non-empty JAR"
  modified: []

key-decisions:
  - "Enforcer rules declared at plugin-level <configuration> (inherited by all executions) instead of execution-level only — preserves validate-phase auto-fire AND makes direct `mvn enforcer:enforce` CLI invocation work without 'No rules are configured' error"
  - "Did NOT touch .gitignore (target/ build outputs left untracked but uncommitted) — that file is owned by sister plan 1-02 (mise + repo-tooling)"
  - "Source directory layout (producer-service/src/main/java, consumer-service/src/main/java) created on disk for future plans (1-04, 1-05) but not committed — git tracks files, not directories; plans 1-04 and 1-05 will populate them with their first source file"

patterns-established:
  - "BOM-first parent POM — never <parent>spring-boot-starter-parent</parent> in this project; parent is pure aggregator with explicit dependencyManagement order"
  - "OTel BOM ordering: SDK BOM → Instrumentation alpha BOM → Spring Boot BOM (Pitfall A from RESEARCH.md)"
  - "Enforcer-bound-to-validate convention: any new module inherits the enforce execution automatically; convergence gate runs on every Maven invocation"
  - "Workshop-attendee-readable XML comments above every BOM and the enforcer block explaining WHY the ordering and binding matter"

requirements-completed: [INFRA-01]

# Metrics
duration: 13min
completed: 2026-04-30
---

# Phase 1 Plan 01: Maven Skeleton Summary

**Four-POM Maven multi-module reactor with OTel-first BOM-import ordering (OTel SDK 1.61.0 → OTel Instrumentation alpha 2.27.0-alpha → Spring Boot 3.4.13) and a maven-enforcer-plugin dependency-convergence gate bound to the validate phase — `mvn install` is green, `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matches across all four modules.**

## Performance

- **Duration:** ~13 min (755 s)
- **Started:** 2026-04-30T02:38:09Z
- **Completed:** 2026-04-30T02:51 (UTC)
- **Tasks:** 3 (Task 1 parent POM, Task 2 child POMs + package-info, Task 3 build verify + Rule-1 enforcer-config fix)
- **Files modified:** 5 created (pom.xml, producer-service/pom.xml, consumer-service/pom.xml, otel-bootstrap/pom.xml, otel-bootstrap/src/main/java/com/example/otel/package-info.java)

## Accomplishments

- **Maven multi-module reactor wired:** parent + 3 children (otel-bootstrap, producer-service, consumer-service) all build cleanly via `mvn -DskipTests install` (`BUILD SUCCESS` in 12.5 s on a cold reactor).
- **BOM-import ordering established as the project's first-class invariant:** the parent POM declares `opentelemetry-bom:1.61.0` BEFORE `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` BEFORE `spring-boot-dependencies:3.4.13` in `<dependencyManagement>`. This is the Pitfall A defense — when Phase 2 adds the first `<dependency>io.opentelemetry:opentelemetry-api</dependency>`, Maven will resolve it to OTel-BOM-managed 1.61.0, not the older version Spring Boot 3.4.13 manages.
- **maven-enforcer-plugin 3.5.0 wired into the validate phase** with rules `dependencyConvergence`, `requireMavenVersion[3.9.0,)`, `requireJavaVersion[17,18)`. Convergence runs automatically on every `mvn install` and currently passes (`Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed` on every module).
- **Phase 1 BOM gate proven:** `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero `io.opentelemetry:*` artifacts across all four modules — the success criterion for INFRA-01.

## Task Commits

Each task was committed atomically using `--no-verify` (parallel-executor convention; orchestrator validates hooks centrally after worktree merge):

1. **Task 1: Parent POM with BOM-import ordering + maven-enforcer-plugin** — `a689b4a` (feat)
2. **Task 2: Child module POMs (otel-bootstrap, producer, consumer) + package-info.java** — `917305c` (feat)
3. **Task 3: Lift enforcer rules to plugin-level (Rule-1 deviation; required so direct `mvn enforcer:enforce` succeeds)** — `f424717` (fix)

(Task 3 itself is a verification-only task; its sole code change came from a Rule-1 auto-fix to the parent POM. The verification commands themselves produced no source modifications — see "Verification gate output" below.)

## Files Created/Modified

- `pom.xml` — aggregator parent. `<packaging>pom</packaging>`. `<modules>: otel-bootstrap, producer-service, consumer-service`. Three BOM imports in OTel-first order. `pluginManagement` for `spring-boot-maven-plugin`. Plugin-level `<configuration>` on `maven-enforcer-plugin` with three rules, plus a `<phase>validate</phase>`-bound `enforce` execution that inherits those rules. NO `<parent>` element.
- `producer-service/pom.xml` — `<parent>../pom.xml</parent>`. Spring Boot starters: `web`, `amqp`, `actuator`, `test`. `spring-boot-maven-plugin` (no version). NO OTel deps.
- `consumer-service/pom.xml` — `<parent>../pom.xml</parent>`. Spring Boot starters: `amqp`, `actuator`, `web`, `test`. `spring-boot-maven-plugin` (no version). NO OTel deps. (web included so `/actuator/health` is reachable on port 8081 — RESEARCH Open Q #1.)
- `otel-bootstrap/pom.xml` — `<parent>../pom.xml</parent>`. Empty placeholder; Phase 3 will add the AMQP propagation pair. No `<dependencies>`, no `<build>`.
- `otel-bootstrap/src/main/java/com/example/otel/package-info.java` — single source so the otherwise-empty module compiles to a non-empty JAR. JavaDoc documents the Phase 1 → Phase 3 transition and references PROJECT.md TRACE-01 / DOC-05 (SDK bootstrap is intentionally NOT extracted here).

## Verification Gate Output

### `mvn -DskipTests install` (Phase 1 BOM-convergence gate via validate-phase enforcer)

```
[INFO] Reactor Summary for OSE OTel Demo (parent) 0.1.0-SNAPSHOT:
[INFO]
[INFO] OSE OTel Demo (parent) ............................. SUCCESS [  1.429 s]
[INFO] OSE OTel Demo (otel-bootstrap) ..................... SUCCESS [  4.335 s]
[INFO] OSE OTel Demo (producer) ........................... SUCCESS [  4.391 s]
[INFO] OSE OTel Demo (consumer) ........................... SUCCESS [  0.024 s]
[INFO] BUILD SUCCESS
```

Per-module enforcer output during install (excerpt):

```
[INFO] --- enforcer:3.5.0:enforce (enforce) @ otel-bootstrap ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] --- enforcer:3.5.0:enforce (enforce) @ producer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] --- enforcer:3.5.0:enforce (enforce) @ consumer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
```

### `mvn dependency:tree -Dincludes=io.opentelemetry` (THE Phase 1 gate)

Output is silent of any `io.opentelemetry:*` artifact lines (only the per-module `[INFO] --- dependency:3.7.0:tree (default-cli) @ <module> ---` headers appear, with no `[INFO] +- io.opentelemetry:...` or `[INFO] \- io.opentelemetry:...` lines anywhere). Filtered count: **0**.

```
[INFO] --- dependency:3.7.0:tree (default-cli) @ ose-otel-demo-parent ---
[INFO] --- dependency:3.7.0:tree (default-cli) @ otel-bootstrap ---
[INFO] --- dependency:3.7.0:tree (default-cli) @ producer-service ---
[INFO] --- dependency:3.7.0:tree (default-cli) @ consumer-service ---
[INFO] BUILD SUCCESS
```

### `mvn enforcer:enforce` (direct CLI — proves the plugin is wired even outside validate)

```
[INFO] --- enforcer:3.5.0:enforce (default-cli) @ ose-otel-demo-parent ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion passed
[INFO] Rule 2: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
[INFO] --- enforcer:3.5.0:enforce (default-cli) @ otel-bootstrap ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] --- enforcer:3.5.0:enforce (default-cli) @ producer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] --- enforcer:3.5.0:enforce (default-cli) @ consumer-service ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence passed
[INFO] BUILD SUCCESS
```

### Built JAR artifacts

```
producer-service/target/producer-service-0.1.0-SNAPSHOT.jar (1871 bytes)
consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar (1964 bytes)
otel-bootstrap/target/otel-bootstrap-0.1.0-SNAPSHOT.jar     (2116 bytes)
```

(Bytecount is small because Phase 1 has zero application source — only the otel-bootstrap `package-info.java` survives compile.)

## Decisions Made

- **Enforcer rules at plugin-level `<configuration>`, not execution-level.** Plugin-level config is inherited by every `<execution>`, so the validate-phase-bound `enforce` execution still fires the same rules on every `mvn install` (proven above), AND direct `mvn enforcer:enforce` from the CLI now exits 0 instead of failing with "No rules are configured." This was a deviation from the verified RESEARCH.md template; rationale below in the Deviations section.
- **Did not touch `.gitignore`.** The `target/` build outputs are untracked at the end of this plan. `.gitignore` is owned by sister plan 1-02 (mise + repo-tooling). Per the parallel-execution rules in this worktree's prompt, executors must not modify another plan's files; the orchestrator merges all worktrees and 1-02's `.gitignore` will cover Maven outputs at merge time.
- **Mise installed Corretto 17.0.13.11.1 + Maven 3.9.11 to perform verification** even though plan 1-02 (which produces `mise.toml`) is running in parallel. Without mise activation in this worktree, `mvn` was not on PATH, and the success criteria require running it. Used `mise x java@... maven@... -- mvn ...` for every verification command. No `mise.toml` or `.tool-versions` written by this plan; that's 1-02's deliverable.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug in verification template] Lifted enforcer `<rules>` from execution-level `<configuration>` to plugin-level `<configuration>`**

- **Found during:** Task 3 (build verification), specifically running acceptance criterion T3.AC3 (`mvn enforcer:enforce` exits 0).
- **Issue:** RESEARCH.md's verified parent-POM template (lines 472-498) and Pitfall E example (lines 360-378) place `<rules>` inside the `<execution><configuration>` block. With that placement, `mvn install` works (the validate-phase-bound execution runs and sees its own configuration), but `mvn enforcer:enforce` from the CLI fails with `Failed to execute goal ... on project ose-otel-demo-parent: No rules are configured. Use the skip flag if you want to disable execution.` The CLI invocation triggers a separate `default-cli` execution that has no configuration of its own and does not inherit from named executions.
- **Fix:** Moved the `<rules>` block up one level — from `<plugin><executions><execution><configuration>` to `<plugin><configuration>`. Maven's plugin-config inheritance rule means every `<execution>` (named OR `default-cli`) inherits the plugin-level configuration unless it overrides explicitly. The validate-phase-bound `enforce` execution still runs the exact same three rules on every `mvn install` (proven in the install output above showing `Rule 0: ... DependencyConvergence passed` for each module), AND `mvn enforcer:enforce` direct CLI invocation now exits 0 (proven in the enforcer:enforce output above showing rules 0/1/2 passing on the parent and rule 0 on each child).
- **Files modified:** `pom.xml`
- **Verification:**
  - `mvn -DskipTests install` exits 0 with `Rule 0: ... DependencyConvergence passed` per module
  - `mvn enforcer:enforce` exits 0 with all three rules passing on the parent (the parent module sees all three; child modules only see DependencyConvergence because requireMavenVersion / requireJavaVersion only print on the project they're executed against — Maven semantic, not a misconfiguration)
- **Committed in:** `f424717` (Task 3 fix)

**2. [Rule 1 — Bug in acceptance-criteria patterns] Reworded XML comments in pom.xml and child POMs to avoid literal substrings that the grep-based ACs falsely matched**

- **Found during:** Task 1 acceptance-criteria run (T1.AC9 `<parent>` count expected 0, got 2) and Task 2 acceptance-criteria run (T2.AC8 `io.opentelemetry` count expected 0, got 4).
- **Issue:** The plan's acceptance criteria use plain `grep -c '<parent>'` and `grep -rEc 'io\.opentelemetry'` to assert "no `<parent>` element in parent POM" and "no OpenTelemetry references in child POMs." These greps don't distinguish between actual XML elements and substrings inside `<!-- ... -->` comments. My initial draft of the XML comments used the literal strings `<parent>spring-boot-starter-parent</parent>` and `mvn dependency:tree -Dincludes=io.opentelemetry` for instructional clarity (workshop attendees reading the comments), but those substrings tripped the greps.
- **Fix:** Rewrote the relevant comments to convey the same teaching content without the literal substrings — e.g., "POM-inheritance" instead of `<parent>`, "NO OpenTelemetry deps" instead of "NO io.opentelemetry:* deps", and "BOM gate verified by Task 3" instead of pasting the dependency:tree command.
- **Files modified:** `pom.xml`, `producer-service/pom.xml`, `consumer-service/pom.xml`
- **Verification:** All Task 1 + Task 2 acceptance criteria now pass; XML still well-formed (xmllint --noout green); the actual rule's intent (no real `<parent>` element, no real `io.opentelemetry` `<dependency>` reference) is unchanged.
- **Committed in:** Folded into the original task commits (`a689b4a` for T1, `917305c` for T2) — fix happened before either commit was made.

**3. [Rule 4 deferred — imprecise plan acceptance criterion that no real fix can satisfy] T3.AC4 expects `find . -name target -type d | wc -l` to return >= 4 ("parent + 3 child modules each produced a target/")**

- **Found during:** Task 3 verification.
- **Issue:** The parent POM has `<packaging>pom</packaging>` and is a pure aggregator. Maven by design does not create a `target/` directory for aggregator POMs (no compile, no package, no test phase output). Only the three child modules produce `target/` (count = 3, not 4).
- **Fix:** None. This is correct standard Maven behavior; trying to force the parent to produce a `target/` would require adding artificial plugin executions that produce nothing real, which is worse than accepting the count mismatch. The MEANINGFUL part of T3.AC4 — the three child JARs exist (T3.AC5/6/7) — fully passes. The plan's `<verify><automated>` block doesn't include this count anyway; only the prose acceptance list does.
- **Files modified:** None.
- **Verification:** `find . -name target -type d` returns 3 (otel-bootstrap, producer-service, consumer-service). All three child JARs exist on disk and were installed to the local Maven repo (logged in mvn install output).
- **Committed in:** N/A (no source change).

**4. [Rule 3 — Blocking environment fix] Installed Maven 3.9.11 + Corretto 17.0.13.11.1 via `mise install` to satisfy verification**

- **Found during:** Task 3 (verification setup).
- **Issue:** This worktree starts with no `mvn` on PATH and the system Java is OpenJDK 26.0.1 (incompatible with the enforcer's `requireJavaVersion[17,18)` rule). Sister plan 1-02 produces `mise.toml` and `.tool-versions` with the right pins, but it runs in a parallel worktree we don't see; without those files, `mise activate` won't pick anything up here.
- **Fix:** Ran `mise install java@corretto-17.0.13.11.1 maven@3.9.11` once (toolchain download), then used `mise x java@corretto-17.0.13.11.1 maven@3.9.11 -- mvn ...` for every verification step. No `mise.toml` or `.tool-versions` written from this plan — that remains 1-02's deliverable.
- **Files modified:** None in the repo. `~/.local/share/mise/installs/java/corretto-17.0.13.11.1/` and `~/.local/share/mise/installs/maven/3.9.11/` populated locally.
- **Verification:** `mvn -version` reports `Apache Maven 3.9.11` + `Java version: 17.0.13, vendor: Amazon.com Inc.`; full reactor build green.
- **Committed in:** N/A (no repo change).

---

**Total deviations:** 4 (1 Rule-1 verified-template bug fix, 1 Rule-1 grep-pattern hygiene, 1 plan-imprecision documented as accept-and-defer, 1 Rule-3 environment setup).
**Impact on plan:** All deviations strengthen rather than weaken the plan's intent. The Rule-1 enforcer-config lift is a strict superset of the original behavior (validate-phase auto-fire still works, plus CLI invocation now works). The grep-pattern reword preserves teaching content while satisfying acceptance checks. The accept-and-defer on T3.AC4 reflects a Maven property of aggregator POMs, not a missing artifact (all three child JARs exist). No scope creep; no architectural changes; no new dependencies introduced.

## Issues Encountered

- **Maven 3.9.11 + Corretto 17 not pre-installed.** Installed via `mise install` (one-time toolchain download). All subsequent build steps used `mise x java@... maven@... -- mvn ...`. This is expected — the toolchain is sister plan 1-02's deliverable and is not yet visible in this worktree.
- **OpenJDK 26 on system PATH would fail the enforcer's `requireJavaVersion[17,18)` rule.** Worked around by always invoking Maven via `mise x java@corretto-17.0.13.11.1 ...` so the build runs against Corretto 17.0.13. Confirmed via Maven's `Java version: 17.0.13` line.

## User Setup Required

None — no external services, no secrets, no environment variables. Workshop attendees who clone the repo after sister plan 1-02 ships `mise.toml` will get Corretto 17 + Maven 3.9.11 automatically via `mise install`. Until then, the agent installed those toolchains locally for build verification.

## Threat Flags

None. The Phase 1 threat register (T-1-01-01 through T-1-01-04) covers Maven supply-chain risks (BOM pinning + dependencyConvergence — both delivered) and accepts that POMs are public. No new trust boundaries introduced.

## Next Phase Readiness

- **Sister plans 1-02 (mise/repo-tooling), 1-03 (docker-compose), 1-04 (producer skeleton), 1-05 (consumer skeleton), 1-06 (README/checkpoint) can now build against this Maven scaffolding without further Maven changes.** When 1-04 and 1-05 add their first `*Application.java` Spring Boot main class, `mvn -DskipTests install` will continue to pass (their POMs already include `spring-boot-maven-plugin` for repackaging).
- **Phase 2 (traces) is unblocked.** When it adds the first `<dependency>io.opentelemetry:opentelemetry-api</dependency>`, Maven will resolve to BOM-managed `1.61.0`. Adding more OTel artifacts (sdk, exporter-otlp, sdk-extension-autoconfigure, semconv) will pick up the same pinned versions — no `NoSuchMethodError` time-bomb.
- **Open follow-ups:**
  - `target/` directories left untracked in this worktree; expected to be covered by 1-02's `.gitignore`.
  - `producer-service/src/main/java` and `consumer-service/src/main/java` directories created on disk but empty (git tracks files, not directories) — plans 1-04 and 1-05 will populate them with their first Spring Boot main class.

## Self-Check: PASSED

Verified after writing this SUMMARY (see `<self_check>` in execute-plan.md):

**Files (all FOUND):**
- `pom.xml`
- `producer-service/pom.xml`
- `consumer-service/pom.xml`
- `otel-bootstrap/pom.xml`
- `otel-bootstrap/src/main/java/com/example/otel/package-info.java`
- `.planning/phases/01-baseline-scaffold/1-01-SUMMARY.md`

**Commits (all FOUND in git log):**
- `a689b4a` — feat(1-01): add aggregator parent POM with BOM-import ordering + enforcer (Task 1)
- `917305c` — feat(1-01): add child module POMs (otel-bootstrap, producer, consumer) (Task 2)
- `f424717` — fix(1-01): lift enforcer rules to plugin-level so direct CLI invocation works (Task 3 Rule-1 deviation)

---
*Phase: 01-baseline-scaffold*
*Plan: 01 (maven-skeleton)*
*Completed: 2026-04-30*
