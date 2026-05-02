---
phase: 06-verification-tests
plan: 02
subsystem: build
tags: [maven, integration-tests, testcontainers, failsafe, opentelemetry-sdk-testing, awaitility, phase-6]

# Dependency graph
requires:
  - phase: 06-verification-tests
    plan: 01
    provides: parent reactor declaration of integration-tests module slot + producer/consumer dual-artifact (plain + exec) classifier publish
  - phase: 01-baseline-scaffold
    provides: parent <dependencyManagement> with opentelemetry-bom 1.61.0 + opentelemetry-instrumentation-bom-alpha 2.27.0-alpha + spring-boot-dependencies 3.4.13 (BOM order locked OTel-first)
provides:
  - integration-tests Maven module with classpath ready for cross-service IT
  - Explicit maven-failsafe-plugin 3.5.5 binding to integration-test + verify goals
  - Reactor invariant restored — `mvn -B verify` from repo root succeeds again after Plan 06-01's intentional reactor break
  - opentelemetry-sdk-testing 1.61.0 on test classpath (InMemorySpanExporter / InMemoryMetricReader / InMemoryLogRecordExporter available to Plans 06-03 / 06-04 / 06-05)
affects: [06-03 TestOtelHolder, 06-04 TestOtelConfiguration, 06-05 OrderFlowIT]

# Tech tracking
tech-stack:
  added:
    - "maven-failsafe-plugin 3.5.5 (explicit binding — parent has no <pluginManagement> for Failsafe; project does NOT inherit from spring-boot-starter-parent)"
    - "opentelemetry-sdk-testing 1.61.0 (test scope, BOM-managed)"
    - "org.testcontainers:junit-jupiter 1.20.6 (test scope, BOM-managed)"
    - "org.testcontainers:rabbitmq 1.20.6 (test scope, BOM-managed)"
    - "org.springframework.boot:spring-boot-testcontainers 3.4.13 (test scope; defensive carry per CONTEXT.md canonical_refs even though D-10's manual two-context flow does not use @ServiceConnection)"
    - "org.awaitility:awaitility 4.2.2 (test scope, BOM-managed)"
  patterns:
    - "Plain-jar reactor dependency (no <classifier>) — depends on producer-service / consumer-service default artifact (plain classes jar from 06-01) so SpringApplicationBuilder(ProducerApplication.class, ...) finds the entry point at top level"
    - "Test-only InMemory exporter classpath — opentelemetry-exporter-otlp deliberately ABSENT (D-13 / PITFALLS #4 #11) so test SDK can never accidentally hit a real backend; opentelemetry-sdk transitively pulled by sdk-testing carries SimpleSpanProcessor and BatchSpanProcessor on the classpath, but the gate here is the POM (no exporter-otlp) not the SDK"
    - "BOM inheritance via parent only — this POM declares ZERO <dependencyManagement> entries; all 8 deps inherit version pins from parent's BOM imports"

key-files:
  created:
    - integration-tests/pom.xml
  modified: []

key-decisions:
  - "Followed plan verbatim — no architectural deviations. POM body is the byte-for-byte RESEARCH §3.4 block (lines 779-889 of 06-RESEARCH.md)."
  - "maven-failsafe-plugin 3.5.5 declared with explicit <execution><goals><goal>integration-test</goal><goal>verify</goal></goals></execution> binding because the project parent does NOT inherit from spring-boot-starter-parent (deliberate Phase 1 BOM-ordering choice — parent pom.xml lines 7-17). Without this explicit binding Failsafe's mojos would never reach the integration-test or verify Maven phases."
  - "Producer + consumer service deps are <scope>compile</scope> (Maven default), not <scope>test</scope>. The IT classes (added in Plan 06-05) need ProducerApplication.class on the compile-time classpath so SpringApplicationBuilder(ProducerApplication.class) compiles."

patterns-established:
  - "explicit-failsafe-binding: any future test-only Maven module in this reactor MUST mirror this <execution><goals> block — Failsafe auto-binding does not reach this project"
  - "no-otlp-exporter-in-test-classpath: test modules MUST NOT declare opentelemetry-exporter-otlp; they consume opentelemetry-sdk-testing only (D-13)"

requirements-completed: [TEST-01, TEST-02, TEST-06]

# Metrics
duration: 2min
completed: 2026-05-02
---

# Phase 6 Plan 02: Integration-Tests POM Summary

**Created `integration-tests/pom.xml` — a 110-line Maven POM that declares the 8 dependencies (producer + consumer plain-jar reactor refs, Spring Boot test infra, Testcontainers + Awaitility + OTel sdk-testing) and the explicit Failsafe 3.5.5 binding required to run `*IT.java` tests in the `integration-test` Maven phase. Reactor build is restored: `mvn -B verify` from repo root now exits 0 again.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-05-02T04:52:52Z
- **Completed:** 2026-05-02T04:54:17Z
- **Tasks:** 1
- **Files created:** 1
- **Files modified:** 0

## Accomplishments

- `integration-tests/pom.xml` exists with `<packaging>jar</packaging>` inheriting from `ose-otel-demo-parent` (NOT `spring-boot-starter-parent` — preserves Phase 1 BOM-ordering invariant).
- Eight `<dependency>` blocks declared:
  - `com.example:producer-service` (compile, no classifier — resolves to plain jar from 06-01)
  - `com.example:consumer-service` (compile, no classifier — resolves to plain jar from 06-01)
  - `org.springframework.boot:spring-boot-starter-test` (test)
  - `org.springframework.boot:spring-boot-testcontainers` (test, defensive carry)
  - `org.testcontainers:junit-jupiter` (test)
  - `org.testcontainers:rabbitmq` (test)
  - `io.opentelemetry:opentelemetry-sdk-testing` (test)
  - `org.awaitility:awaitility` (test)
- All 6 BOM-managed test deps inherit version pins from the parent's `<dependencyManagement>` BOM imports — this POM declares zero `<dependencyManagement>` entries (no shadowing risk).
- `maven-failsafe-plugin:3.5.5` declared explicitly with `<goal>integration-test</goal>` + `<goal>verify</goal>` bindings.
- `opentelemetry-exporter-otlp` deliberately ABSENT — test SDK uses InMemory* exporters only (D-13 / PITFALLS #4 #11).
- `maven-surefire-plugin` deliberately ABSENT — module has no `*Test.java` files; only `*IT.java` (added in 06-05).
- Reactor build restored: `mvn -B -pl integration-tests -am dependency:tree` exits 0; `mvn -B -pl integration-tests -am verify -DfailIfNoTests=false` exits 0.

## Task Commits

1. **Task 1: Create `integration-tests/pom.xml` verbatim from RESEARCH §3.4 with project-specific Failsafe binding** — `12d646a` (feat)

## Files Created/Modified

- `integration-tests/pom.xml` — **created** (110 lines). Verbatim RESEARCH §3.4 block; no edits.

## Verbatim file contents

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>ose-otel-demo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>integration-tests</artifactId>
  <packaging>jar</packaging>

  <name>OSE OTel Demo (integration tests)</name>
  <description>Cross-service IT proving the full instrumentation chain (Phase 6).</description>

  <dependencies>
    <!--
      Phase 6 / D-04: depend on the PLAIN classes jars (no classifier).
      The producer/consumer service POMs publish the executable repackage
      with classifier=exec; the default artifact is the plain classes jar
      that exposes ProducerApplication.class / ConsumerApplication.class
      directly on the classpath. SpringApplicationBuilder(ProducerApplication.class)
      requires this.
    -->
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>producer-service</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>consumer-service</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- otel-bootstrap is transitive via producer/consumer; no explicit dep. -->

    <!-- Spring Boot test infra. -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <!--
      Defensive carry per CONTEXT.md canonical_refs — kept on the deps list
      even though D-10's two-context flow doesn't use @ServiceConnection.
      Keeps the option open if planner reverses D-10.
    -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Testcontainers (BOM-managed by Spring Boot 3.4.13). -->
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>rabbitmq</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- OTel in-memory exporters (BOM-managed by opentelemetry-bom:1.61.0). -->
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-sdk-testing</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Awaitility (BOM-managed by Spring Boot 3.4.13). -->
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!--
        Phase 6 / D-03: EXPLICIT Failsafe binding. The project's parent does
        NOT inherit from spring-boot-starter-parent (deliberate Phase 1 BOM-
        ordering choice — see parent pom.xml lines 7-17), so Spring's auto-
        Failsafe-binding does not reach this module. Bind goals manually.
        Version 3.5.5 = latest stable as of 2026-04.
      -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
        <version>3.5.5</version>
        <executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

## Verification Output

### `mvn -B -pl integration-tests -am dependency:tree -DskipTests` (exit 0)

Top-level deps of `com.example:integration-tests:jar:0.1.0-SNAPSHOT`:

```
[INFO] --- dependency:3.7.0:tree (default-cli) @ integration-tests ---
[INFO] com.example:integration-tests:jar:0.1.0-SNAPSHOT
[INFO] +- com.example:producer-service:jar:0.1.0-SNAPSHOT:compile
[INFO] +- com.example:consumer-service:jar:0.1.0-SNAPSHOT:compile
[INFO] +- org.springframework.boot:spring-boot-starter-test:jar:3.4.13:test
[INFO] +- org.springframework.boot:spring-boot-testcontainers:jar:3.4.13:test
[INFO] +- org.testcontainers:junit-jupiter:jar:1.20.6:test
[INFO] +- org.testcontainers:rabbitmq:jar:1.20.6:test
[INFO] +- io.opentelemetry:opentelemetry-sdk-testing:jar:1.61.0:test
[INFO] \- org.awaitility:awaitility:jar:4.2.2:test
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for OSE OTel Demo (parent) 0.1.0-SNAPSHOT:
[INFO]
[INFO] OSE OTel Demo (parent) ............................. SUCCESS [  0.225 s]
[INFO] OSE OTel Demo (otel-bootstrap) ..................... SUCCESS [  0.053 s]
[INFO] OSE OTel Demo (producer) ........................... SUCCESS [  0.049 s]
[INFO] OSE OTel Demo (consumer) ........................... SUCCESS [  0.007 s]
[INFO] OSE OTel Demo (integration tests) .................. SUCCESS [  0.020 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  0.495 s
```

All 8 deps resolve to BOM-managed pins:

| Dep                                              | Resolved version | Source BOM                                        |
|--------------------------------------------------|------------------|---------------------------------------------------|
| `com.example:producer-service`                   | `0.1.0-SNAPSHOT` | reactor                                           |
| `com.example:consumer-service`                   | `0.1.0-SNAPSHOT` | reactor                                           |
| `org.springframework.boot:spring-boot-starter-test`     | `3.4.13`  | `spring-boot-dependencies:3.4.13`                 |
| `org.springframework.boot:spring-boot-testcontainers`   | `3.4.13`  | `spring-boot-dependencies:3.4.13`                 |
| `org.testcontainers:junit-jupiter`               | `1.20.6`         | `spring-boot-dependencies:3.4.13`                 |
| `org.testcontainers:rabbitmq`                    | `1.20.6`         | `spring-boot-dependencies:3.4.13`                 |
| `io.opentelemetry:opentelemetry-sdk-testing`     | `1.61.0`         | `opentelemetry-bom:1.61.0`                        |
| `org.awaitility:awaitility`                      | `4.2.2`          | `spring-boot-dependencies:3.4.13`                 |

### `mvn -B -pl integration-tests -am verify -DfailIfNoTests=false` (exit 0)

```
[INFO] --- failsafe:3.5.5:integration-test (default) @ integration-tests ---
[INFO] No tests to run.
[INFO]
[INFO] --- failsafe:3.5.5:verify (default) @ integration-tests ---
[INFO] No tests to run.
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for OSE OTel Demo (parent) 0.1.0-SNAPSHOT:
[INFO]
[INFO] OSE OTel Demo (parent) ............................. SUCCESS [  0.091 s]
[INFO] OSE OTel Demo (otel-bootstrap) ..................... SUCCESS [  0.560 s]
[INFO] OSE OTel Demo (producer) ........................... SUCCESS [  0.198 s]
[INFO] OSE OTel Demo (consumer) ........................... SUCCESS [  0.073 s]
[INFO] OSE OTel Demo (integration tests) .................. SUCCESS [  0.058 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  1.080 s
```

Both Failsafe goals (`integration-test` and `verify`) executed under plugin version `3.5.5` — proving the explicit binding is wired. Both goals report "No tests to run" — Failsafe is a **no-op success** because no `*IT.java` files exist yet. Plan 06-05 will add `OrderFlowIT.java`, which Failsafe's default `**/*IT.java` test-class match pattern will pick up automatically (no plugin configuration changes needed).

## Acceptance Criteria — all 22 PASS

| # | Gate | Result |
|---|------|--------|
| 1 | `test -f integration-tests/pom.xml` | PASS |
| 2 | `<modelVersion>4.0.0</modelVersion>` present | PASS |
| 3 | `<artifactId>integration-tests</artifactId>` present | PASS |
| 4 | `<packaging>jar</packaging>` present | PASS |
| 5 | Inherits from `ose-otel-demo-parent` | PASS |
| 6 | Does NOT inherit from `spring-boot-starter-parent` | PASS |
| 7 | producer-service dep present with `${project.version}` | PASS |
| 8 | consumer-service dep present with `${project.version}` | PASS |
| 9 | spring-boot-starter-test dep present | PASS |
| 10 | spring-boot-testcontainers dep present | PASS |
| 11 | testcontainers junit-jupiter dep present | PASS |
| 12 | testcontainers rabbitmq dep present | PASS |
| 13 | opentelemetry-sdk-testing dep present | PASS |
| 14 | awaitility dep present | PASS |
| 15 | Producer dep has NO `<classifier>` | PASS |
| 16 | Consumer dep has NO `<classifier>` | PASS |
| 17 | Failsafe `<version>3.5.5</version>` | PASS |
| 18 | Failsafe `<goal>integration-test</goal>` bound | PASS |
| 19 | Failsafe `<goal>verify</goal>` bound | PASS |
| 20 | NO `opentelemetry-exporter-otlp` dep | PASS |
| 21 | NO `maven-surefire-plugin` block | PASS |
| 22 | `mvn dependency:tree` and `mvn verify` both exit 0 | PASS |

## Decisions Made

- **Followed plan verbatim — no architectural deviations.** The POM body is the byte-for-byte RESEARCH §3.4 block (lines 779-889 of 06-RESEARCH.md).
- **Producer + consumer service deps left at default `compile` scope** (not `test`). Test classes added in Plan 06-05 must `import com.example.producer.ProducerApplication` at compile time; `<scope>test</scope>` would have hidden the classes from the IT compile path.
- **Reactor build re-validated from repo root** (`mvn -B -pl integration-tests -am verify`) rather than per-module, because the load-bearing assertion is "the reactor break introduced by 06-01 is now resolved". Both `dependency:tree` and `verify` exit 0 from the parent POM directory.

## Deviations from Plan

None — plan executed exactly as written. The POM is the verbatim RESEARCH §3.4 block; the Failsafe binding is the project-specific invariant the plan flagged; both verification commands exit 0.

## Issues Encountered

None. The plan correctly anticipated that Failsafe would download surefire-api / surefire-booter on first run (visible in the `verify` log) — this is normal Maven plugin resolution and does not affect the build outcome.

## Forward Link

- Plan 06-03 will create `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` — the SDK-singleton holder class, whose `InMemorySpanExporter` / `InMemoryMetricReader` / `InMemoryLogRecordExporter` instances live on the test classpath thanks to the `opentelemetry-sdk-testing` dep added here.
- Plan 06-04 will create `integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java` — a `@TestConfiguration` facade that wires those holder instances into the per-test Spring context (`SimpleSpanProcessor` / `SimpleLogRecordProcessor` only — Batch processors forbidden in tests per D-13 / PITFALLS #4 #11).
- Plan 06-05 will create `integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java` — the actual `*IT.java` test class that Failsafe (bound here) will discover via its default `**/*IT.java` match pattern.

## Self-Check: PASSED

- `integration-tests/pom.xml` exists on disk (`test -f integration-tests/pom.xml` → 0)
- Commit `12d646a` exists in `git log --all` (verified post-commit; HEAD on `main` is `12d646a feat(06-02): add integration-tests Maven module POM`)
- `mvn -B -pl integration-tests -am dependency:tree -DskipTests` exits 0 (captured at `/tmp/06-02/deptree.txt`)
- `mvn -B -pl integration-tests -am verify -DfailIfNoTests=false` exits 0 (captured at `/tmp/06-02/verify.txt`)
- No accidental file deletions in commit `12d646a` (`git diff --diff-filter=D --name-only HEAD~1 HEAD` returned empty)

---
*Phase: 06-verification-tests*
*Completed: 2026-05-02*
