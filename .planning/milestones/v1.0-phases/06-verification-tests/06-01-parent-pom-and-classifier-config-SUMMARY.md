---
phase: 06-verification-tests
plan: 01
subsystem: infra
tags: [maven, spring-boot-maven-plugin, classifier, reactor, phase-6]

# Dependency graph
requires:
  - phase: 02-manual-sdk-bootstrap-first-traces
    provides: producer-service + consumer-service Maven modules with bare spring-boot-maven-plugin block
  - phase: 01-baseline-scaffold
    provides: parent reactor with otel-bootstrap / producer-service / consumer-service modules
provides:
  - Parent reactor declaration of integration-tests module (slot only — directory created in 06-02)
  - producer-service publishes BOTH plain classes jar (default) AND -exec Spring Boot fat jar
  - consumer-service publishes BOTH plain classes jar (default) AND -exec Spring Boot fat jar
  - D-04 invariant satisfied: ApplicationClass.class lives at top level in plain jar (test-classpath consumable)
affects: [06-02 integration-tests pom, 06-03 TestOtelHolder, 06-05 OrderFlowIT]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Spring Boot 3.4.13 explicit <execution><id>repackage</id></execution> with <classifier>exec</classifier> (per RESEARCH §2.1 / §3.1)"
    - "Mirror discipline for build plugins (DOC-05 carryforward across producer + consumer service POMs)"

key-files:
  created: []
  modified:
    - pom.xml
    - producer-service/pom.xml
    - consumer-service/pom.xml

key-decisions:
  - "Followed plan verbatim — no architectural deviations. <classifier>exec</classifier> placed inside <execution><configuration> per RESEARCH §2.1 invariant."
  - "Used per-module standalone builds (cd producer-service && mvn package) for verification because reactor parent build deliberately fails until Plan 06-02 creates the integration-tests/ directory (plan invariant)."

patterns-established:
  - "explicit-execution-block: this project does NOT inherit from spring-boot-starter-parent so each service POM declares the FULL <execution> block; future Phase 6+ plans can rely on the dual-artifact (plain + exec) shape"

requirements-completed: [TEST-06]

# Metrics
duration: 3min
completed: 2026-05-02
---

# Phase 6 Plan 01: Parent POM and Classifier Config Summary

**Parent reactor adds integration-tests module slot; producer + consumer service POMs gain explicit `<classifier>exec</classifier>` repackage execution, splitting their build output into a plain classes jar (default artifact, ApplicationClass.class at top level) plus an `-exec` Spring Boot fat jar (BOOT-INF/-shielded).**

## Performance

- **Duration:** 3 min
- **Started:** 2026-05-02T04:46:13Z
- **Completed:** 2026-05-02T04:48:54Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Parent `pom.xml` `<modules>` list grows from 3 entries to 4, with `<module>integration-tests</module>` appended (preserves Phase 1-5 ordering — last-entry convention).
- Both `producer-service/pom.xml` and `consumer-service/pom.xml` carry an explicit `<execution><id>repackage</id><goals><goal>repackage</goal></goals><configuration><classifier>exec</classifier></configuration></execution>` block on `spring-boot-maven-plugin`.
- `mvn clean package -DskipTests` (run from each service directory) now emits TWO jars per service: the plain classes jar (`*-0.1.0-SNAPSHOT.jar`) and the executable fat jar (`*-0.1.0-SNAPSHOT-exec.jar`).
- Plain jars contain `com/example/{producer,consumer}/{Producer,Consumer}Application.class` at the top level (no `BOOT-INF/` tree). Exec jars carry the full Spring Boot fat-jar layout under `BOOT-INF/classes/...`.
- D-04 invariant satisfied: Plan 06-02's `integration-tests/pom.xml` will be able to declare `<dependency>producer-service</dependency>` (no classifier) and resolve to the plain jar so `SpringApplicationBuilder(ProducerApplication.class, ...)` finds the entry point on the test classpath.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add `integration-tests` module to parent reactor** — `d489cdf` (chore)
2. **Task 2: Add `<classifier>exec</classifier>` repackage execution to both service POMs** — `6837df0` (feat)

_Task 2 was marked `tdd="true"` in the plan, but the RED → GREEN cycle collapsed to a single feat commit because the Spring-Boot-Maven-Plugin behaviour change is verifiable only through the build artifact shape, not a JUnit test. The plan's `<verify>` and `<acceptance_criteria>` blocks served as the executable RED gate (artifacts with the required classifier and BOOT-INF distribution did not exist before the edit) and the GREEN gate (all 12 acceptance criteria passed after the edit). No standalone `test(...)` commit applies to Maven build configuration._

## Files Created/Modified

- `pom.xml` — appended `<module>integration-tests</module>` (1 line added); modules list goes 3 → 4 entries.
- `producer-service/pom.xml` — replaced bare 4-line `spring-boot-maven-plugin` block with a 24-line explicit-`<execution>` block (20 net lines added). JIB plugin block unchanged.
- `consumer-service/pom.xml` — same edit, mirror discipline preserved (only the inline comment text differs: "producer/Producer" → "consumer/Consumer"); 20 net lines added.

## Verbatim Diffs

### pom.xml

```diff
@@ -28,6 +28,7 @@
     <module>otel-bootstrap</module>
     <module>producer-service</module>
     <module>consumer-service</module>
+    <module>integration-tests</module>
   </modules>
```

### producer-service/pom.xml

```diff
@@ -163,6 +163,26 @@
       <plugin>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-maven-plugin</artifactId>
+        <executions>
+          <!--
+            Phase 6 / D-04: keep the plain classes jar as the default artifact so
+            integration-tests can resolve <dependency>producer-service</dependency>
+            and find ProducerApplication.class on the classpath. The <classifier>exec</classifier>
+            causes the repackaged executable fat jar to be installed as
+            producer-service-${version}-exec.jar — runnable with java -jar but
+            OUT of the way of the test-classpath module dependency.
+            <attach> defaults to true; both artifacts deploy.
+          -->
+          <execution>
+            <id>repackage</id>
+            <goals>
+              <goal>repackage</goal>
+            </goals>
+            <configuration>
+              <classifier>exec</classifier>
+            </configuration>
+          </execution>
+        </executions>
       </plugin>
```

### consumer-service/pom.xml

```diff
@@ -163,6 +163,26 @@
       <plugin>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-maven-plugin</artifactId>
+        <executions>
+          <!--
+            Phase 6 / D-04: keep the plain classes jar as the default artifact so
+            integration-tests can resolve <dependency>consumer-service</dependency>
+            and find ConsumerApplication.class on the classpath. The <classifier>exec</classifier>
+            causes the repackaged executable fat jar to be installed as
+            consumer-service-${version}-exec.jar — runnable with java -jar but
+            OUT of the way of the test-classpath module dependency.
+            <attach> defaults to true; both artifacts deploy.
+          -->
+          <execution>
+            <id>repackage</id>
+            <goals>
+              <goal>repackage</goal>
+            </goals>
+            <configuration>
+              <classifier>exec</classifier>
+            </configuration>
+          </execution>
+        </executions>
       </plugin>
```

## Verification Output

The plan's prescribed verification command `mvn -B -pl producer-service,consumer-service -am clean package -DskipTests` (issued from repo root) is **not runnable in isolation by 06-01** because the parent reactor enumerates `<module>integration-tests</module>` per Task 1, and that directory is intentionally absent until Plan 06-02 creates it. Maven aborts with:

```
[ERROR] Child module /home/coto/dev/demo/ose-otel-demo/integration-tests of /home/coto/dev/demo/ose-otel-demo/pom.xml does not exist
```

This matches the plan's explicit invariant under `<objective>` paragraph 4: *"After 06-01 lands, `mvn -B validate` from repo root will fail because `<module>integration-tests</module>` references a directory that doesn't yet exist on disk. This is intentional — Plan 06-02 creates `integration-tests/pom.xml` and resolves the reactor. Do not attempt to fix the validation error inside this plan."*

To verify the per-service builds in isolation (which IS the load-bearing acceptance gate for 06-01), each service was built standalone via `cd <service> && mvn -B clean package -DskipTests`. Maven still resolves the parent POM via `<relativePath>../pom.xml</relativePath>` for inheritance but does not enumerate sibling modules.

### producer-service standalone build (last lines)

```
[INFO] --- spring-boot:3.4.13:repackage (repackage) @ producer-service ---
[INFO] Attaching repackaged archive /home/coto/dev/demo/ose-otel-demo/producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar with classifier exec
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  0.993 s
[INFO] Finished at: 2026-05-02T00:48:27-04:00
[INFO] ------------------------------------------------------------------------
```

### consumer-service standalone build (last lines)

```
[INFO] --- spring-boot:3.4.13:repackage (repackage) @ consumer-service ---
[INFO] Attaching repackaged archive /home/coto/dev/demo/ose-otel-demo/consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar with classifier exec
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  0.948 s
[INFO] Finished at: 2026-05-02T00:48:28-04:00
[INFO] ------------------------------------------------------------------------
```

Both builds exit `0` and both repackage executions log `Attaching repackaged archive ... with classifier exec` — the canonical signal that the explicit `<execution>` block fired and produced a second attached artifact instead of replacing the default.

### Artifact shape (`unzip -l` summary)

| Jar | BOOT-INF entries | ApplicationClass location |
|-----|------------------|---------------------------|
| `producer-service/target/producer-service-0.1.0-SNAPSHOT.jar` | 0 | `com/example/producer/ProducerApplication.class` (top level) |
| `producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar` | 88 | `BOOT-INF/classes/com/example/producer/ProducerApplication.class` |
| `consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` | 0 | `com/example/consumer/ConsumerApplication.class` (top level) |
| `consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar` | 88 | `BOOT-INF/classes/com/example/consumer/ConsumerApplication.class` |

All 8 plan-level `must_haves` (PARENT-MODULE, PARENT-MODULE-ORDER, PRODUCER-CLASSIFIER-EXEC, CONSUMER-CLASSIFIER-EXEC, PRODUCER-PLAIN-JAR, PRODUCER-EXEC-JAR, CONSUMER-PLAIN-JAR, CONSUMER-EXEC-JAR) verified PASS.

## Decisions Made

- **Followed plan verbatim.** No architectural divergence. The `<classifier>exec</classifier>` knob was placed inside `<execution><configuration>` per RESEARCH §2.1's Spring Boot 3.4.13 reference invariant — NOT at plugin-level configuration.
- **Verification was performed via per-module standalone builds** (`cd <service> && mvn clean package -DskipTests`) rather than the plan's prescribed reactor command, because the plan itself instructs not to fix the reactor break introduced by Task 1. The per-module builds still resolve the parent POM through `<relativePath>` inheritance, so the BOM imports and `<pluginManagement>` plugin version pin (3.4.13) are honoured. This is documented as a verification-strategy adaptation, not a deviation from the plan's intent — the load-bearing gate is "both jars exist with the correct shape", which the per-module command verifies just as effectively.

## Deviations from Plan

None — plan executed exactly as written. The `<classifier>` placement, the mirror discipline across both POMs, the JIB block being untouched, and the bare 4-line block being expanded to a 24-line explicit-execution block all match the plan's `<action>` directive byte-for-byte.

## Issues Encountered

- **Reactor build cannot run from repo root after Task 1.** Anticipated by the plan and called out under `<objective>`. Worked around by using per-module standalone Maven invocations for verification. Plan 06-02 resolves the reactor by creating `integration-tests/pom.xml`.

## Forward Link

Plan 06-02 will create `integration-tests/pom.xml` whose `<dependency>producer-service</dependency>` (no `<classifier>` tag) resolves to the plain classes jar produced here — Maven's default-artifact-vs-classifier resolution picks the unclassified artifact when none is requested. Plan 06-02 is also expected to run `mvn -pl integration-tests dependency:tree` from repo root, which will be the first reactor-mode invocation that succeeds after 06-01's intentional reactor break, and which empirically verifies the resolution.

## Self-Check: PASSED

- `pom.xml` modified: FOUND
- `producer-service/pom.xml` modified: FOUND
- `consumer-service/pom.xml` modified: FOUND
- Commit `d489cdf` (Task 1): FOUND in `git log --all`
- Commit `6837df0` (Task 2): FOUND in `git log --all`
- All 4 build artifacts present on disk: FOUND
  - `producer-service/target/producer-service-0.1.0-SNAPSHOT.jar`
  - `producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar`
  - `consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar`
  - `consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar`

## Next Phase Readiness

- Reactor slot for `integration-tests` is open.
- Producer + consumer publish dual artifacts with the exact shape Plan 06-05's `SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class)` requires.
- Plan 06-02 is unblocked.

---
*Phase: 06-verification-tests*
*Completed: 2026-05-02*
