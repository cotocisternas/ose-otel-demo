---
phase: 06-verification-tests
plan: 02
type: execute
wave: 2
depends_on:
  - 06-01
files_modified:
  - integration-tests/pom.xml
autonomous: true
requirements:
  - TEST-01
  - TEST-02
  - TEST-06
tags:
  - maven
  - integration-tests
  - testcontainers
  - failsafe
  - opentelemetry-sdk-testing
  - awaitility
  - phase-6
must_haves:
  truths:
    - id: POM-EXISTS
      description: "integration-tests/pom.xml exists and declares packaging=jar"
      verify: "test -f integration-tests/pom.xml && grep -q '<packaging>jar</packaging>' integration-tests/pom.xml"
    - id: PARENT-INHERITANCE
      description: "integration-tests inherits from ose-otel-demo-parent (NOT spring-boot-starter-parent — preserves Phase 1 BOM-ordering)"
      verify: "grep -q '<artifactId>ose-otel-demo-parent</artifactId>' integration-tests/pom.xml && ! grep -q 'spring-boot-starter-parent' integration-tests/pom.xml"
    - id: PRODUCER-DEP-NO-CLASSIFIER
      description: "Depends on producer-service WITHOUT <classifier> (resolves to plain classes jar from 06-01)"
      verify: "awk '/<artifactId>producer-service<\\/artifactId>/{found=NR} /<\\/dependency>/{if(found && NR-found < 6) {section=1; print} else found=0} section{print; if(/<\\/dependency>/) section=0}' integration-tests/pom.xml | head -10 && ! awk '/<artifactId>producer-service<\\/artifactId>/,/<\\/dependency>/' integration-tests/pom.xml | grep -q '<classifier>'"
    - id: CONSUMER-DEP-NO-CLASSIFIER
      description: "Depends on consumer-service WITHOUT <classifier>"
      verify: "grep -A2 '<artifactId>consumer-service</artifactId>' integration-tests/pom.xml | grep -q '<version>' && ! awk '/<artifactId>consumer-service<\\/artifactId>/,/<\\/dependency>/' integration-tests/pom.xml | grep -q '<classifier>'"
    - id: SDK-TESTING-DEP
      description: "opentelemetry-sdk-testing test-scoped dep (BOM-managed by opentelemetry-bom:1.61.0)"
      verify: "awk '/<artifactId>opentelemetry-sdk-testing<\\/artifactId>/,/<\\/dependency>/' integration-tests/pom.xml | grep -q '<scope>test</scope>'"
    - id: TESTCONTAINERS-RABBITMQ
      description: "org.testcontainers:rabbitmq test-scoped dep"
      verify: "awk '/<groupId>org.testcontainers<\\/groupId>/,/<\\/dependency>/' integration-tests/pom.xml | grep -q '<artifactId>rabbitmq</artifactId>'"
    - id: TESTCONTAINERS-JUNIT
      description: "org.testcontainers:junit-jupiter test-scoped dep"
      verify: "awk '/<groupId>org.testcontainers<\\/groupId>/,/<\\/dependency>/' integration-tests/pom.xml | grep -q '<artifactId>junit-jupiter</artifactId>'"
    - id: SPRING-BOOT-TESTCONTAINERS
      description: "spring-boot-testcontainers test-scoped dep (defensive carry per CONTEXT canonical_refs even though manual property injection is used)"
      verify: "grep -q '<artifactId>spring-boot-testcontainers</artifactId>' integration-tests/pom.xml"
    - id: SPRING-BOOT-STARTER-TEST
      description: "spring-boot-starter-test test-scoped dep (JUnit 5 + AssertJ + TestRestTemplate)"
      verify: "grep -q '<artifactId>spring-boot-starter-test</artifactId>' integration-tests/pom.xml"
    - id: AWAITILITY-DEP
      description: "org.awaitility:awaitility test-scoped dep (BOM-managed by Spring Boot 3.4.13)"
      verify: "awk '/<groupId>org.awaitility<\\/groupId>/,/<\\/dependency>/' integration-tests/pom.xml | grep -q '<artifactId>awaitility</artifactId>'"
    - id: FAILSAFE-EXPLICIT
      description: "maven-failsafe-plugin declared explicitly with goals integration-test + verify (RESEARCH §2.5: project parent does NOT inherit from spring-boot-starter-parent so Failsafe auto-binding does NOT reach this module)"
      verify: "awk '/<artifactId>maven-failsafe-plugin<\\/artifactId>/,/<\\/plugin>/' integration-tests/pom.xml | grep -q '<goal>integration-test</goal>' && awk '/<artifactId>maven-failsafe-plugin<\\/artifactId>/,/<\\/plugin>/' integration-tests/pom.xml | grep -q '<goal>verify</goal>'"
    - id: FAILSAFE-VERSION
      description: "Failsafe pinned to 3.5.5 (latest stable as of research date) directly in this POM (parent has no <pluginManagement> for Failsafe)"
      verify: "awk '/<artifactId>maven-failsafe-plugin<\\/artifactId>/,/<\\/plugin>/' integration-tests/pom.xml | grep -q '<version>3.5.5</version>'"
    - id: DEPENDENCY-TREE-CLEAN
      description: "mvn -pl integration-tests dependency:tree resolves all deps without convergence errors"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am dependency:tree -DskipTests >/dev/null"
    - id: VERIFY-NOOP-SUCCESS
      description: "mvn -pl integration-tests verify exits 0 (no tests yet — Failsafe runs as a no-op success)"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am verify -DfailIfNoTests=false >/dev/null"
    - id: NO-BATCH-PROCESSORS
      description: "POM does NOT pre-declare any opentelemetry-exporter-otlp dep that would suggest test code uses Batch processors (PITFALLS #4/#11 / D-13 — tests must use Simple processors only). Note: opentelemetry-sdk-testing transitively pulls opentelemetry-sdk which contains BOTH Simple and Batch processors; this gate is on the POM, not the SDK classpath."
      verify: "! grep -q '<artifactId>opentelemetry-exporter-otlp</artifactId>' integration-tests/pom.xml"
  artifacts:
    - path: integration-tests/pom.xml
      provides: "Maven POM declaring producer + consumer plain-jar deps + Testcontainers + opentelemetry-sdk-testing + Awaitility + explicit Failsafe binding"
      contains: "<artifactId>integration-tests</artifactId>"
      contains: "<artifactId>opentelemetry-sdk-testing</artifactId>"
      contains: "<artifactId>maven-failsafe-plugin</artifactId>"
  key_links:
    - from: integration-tests/pom.xml `<dependency>producer-service</dependency>` (no classifier)
      to: producer-service/target/producer-service-0.1.0-SNAPSHOT.jar (plain classes jar from 06-01)
      via: "Maven default-artifact-resolution"
      pattern: "<artifactId>producer-service</artifactId>"
    - from: integration-tests/pom.xml maven-failsafe-plugin <execution>
      to: Plan 06-05's OrderFlowIT.java (matched by `**/*IT.java` pattern)
      via: "Failsafe's default test-class match patterns"
      pattern: "<artifactId>maven-failsafe-plugin</artifactId>"
    - from: integration-tests/pom.xml `<dependency>opentelemetry-sdk-testing</dependency>`
      to: Plan 06-03/06-04's TestOtelHolder.java + TestOtelConfiguration.java (uses InMemorySpanExporter / InMemoryLogRecordExporter / InMemoryMetricReader)
      via: "OTel BOM-managed test artifact"
      pattern: "InMemorySpanExporter"
---

<objective>
Create `integration-tests/pom.xml` declaring the new Maven module's dependencies, build configuration, and Failsafe binding. The POM MUST follow RESEARCH §3.4 verbatim with one critical project-specific invariant: this project does NOT inherit from `spring-boot-starter-parent` (deliberate Phase 1 BOM-ordering choice — see parent `pom.xml` lines 7-17), so Spring's auto-Failsafe-binding does NOT reach this module — `maven-failsafe-plugin` MUST be declared explicitly with `<goal>integration-test</goal>` + `<goal>verify</goal>`.

Purpose: Establish the test module's classpath (producer-service plain classes jar + consumer-service plain classes jar + Testcontainers RabbitMQ + opentelemetry-sdk-testing + Awaitility + Spring Boot test starter) and the Failsafe binding so `mvn -pl integration-tests verify` runs `*IT.java` tests in the `integration-test` Maven phase. Honors CONTEXT D-01 (new module), D-03 (Failsafe + `*IT.java` convention), D-04 (plain classes jar deps, no classifier), D-13 (Awaitility for polling — no Thread.sleep), and the locked stack pins.

Output: ONE new file `integration-tests/pom.xml` (~110 lines including the comment blocks). After this plan: `mvn -pl integration-tests -am dependency:tree` resolves all 8 deps with no convergence errors; `mvn -pl integration-tests -am verify` exits 0 (Failsafe runs but finds no `*IT.java` yet — no-op success).

Why this is wave 2: depends on 06-01's producer/consumer `<classifier>exec</classifier>` knob — without it, the `<dependency>producer-service</dependency>` (no classifier) here would resolve to the repackaged exec fat jar with `ProducerApplication.class` shielded under `BOOT-INF/`, breaking Plan 06-05's `SpringApplicationBuilder(ProducerApplication.class)` call at runtime.

Why this is one focused plan: a single Maven POM file with a tightly-coupled set of dependencies and one Failsafe binding. Splitting deps from the build block would create artificial wave boundaries on a file that's already structurally cohesive.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/06-verification-tests/06-CONTEXT.md
@.planning/phases/06-verification-tests/06-RESEARCH.md
@.planning/phases/06-verification-tests/06-PATTERNS.md
@.planning/phases/06-verification-tests/06-01-SUMMARY.md
@pom.xml
@otel-bootstrap/pom.xml

<interfaces>
<!-- Maven coordinates for every dep in this POM. All BOM-managed except Failsafe (pinned to 3.5.5 directly per RESEARCH §2.5). -->

BOM-managed by `opentelemetry-bom:1.61.0` (declared in parent pom.xml `<dependencyManagement>`):
- `io.opentelemetry:opentelemetry-sdk-testing` → version 1.61.0 (transitive: opentelemetry-sdk, opentelemetry-api)

BOM-managed by `spring-boot-dependencies:3.4.13` (declared in parent pom.xml `<dependencyManagement>`):
- `org.springframework.boot:spring-boot-starter-test` → version 3.4.13 (transitive: JUnit 5, AssertJ, Mockito, TestRestTemplate)
- `org.springframework.boot:spring-boot-testcontainers` → version 3.4.13
- `org.testcontainers:junit-jupiter` → BOM-managed
- `org.testcontainers:rabbitmq` → BOM-managed
- `org.awaitility:awaitility` → BOM-managed (4.2.x)

Reactor-internal:
- `com.example:producer-service:${project.version}` (resolves to `0.1.0-SNAPSHOT` plain classes jar from 06-01)
- `com.example:consumer-service:${project.version}` (resolves to `0.1.0-SNAPSHOT` plain classes jar from 06-01)

Plugin pin (NOT BOM-managed — parent has no <pluginManagement> for Failsafe):
- `org.apache.maven.plugins:maven-failsafe-plugin:3.5.5` (RESEARCH §3.4 / §2.5)

CRITICAL: opentelemetry-exporter-otlp is NOT declared here. The test SDK uses InMemory* exporters only — never OTLP exporters in tests (D-13 / PITFALLS #4 #11).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Create integration-tests/pom.xml verbatim from RESEARCH §3.4 with project-specific Failsafe binding</name>
  <files>integration-tests/pom.xml</files>
  <read_first>
    - .planning/phases/06-verification-tests/06-RESEARCH.md (§3.4 — paste-ready POM block lines 779-889; §2.5 — Failsafe-must-be-explicit rationale; §2.1 — confirms project does NOT inherit from spring-boot-starter-parent)
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 1 — closest analog is otel-bootstrap/pom.xml for the parent + packaging shape ONLY; Failsafe is genuinely net-new)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-01 module structure; D-03 Failsafe + *IT.java; D-04 producer/consumer deps without classifier; D-13 Awaitility)
    - pom.xml lines 7-17 (the explicit "no spring-boot-starter-parent inheritance" comment that drives the Failsafe-must-be-explicit decision)
    - otel-bootstrap/pom.xml (analog for the parent + packaging + child-module shape — but otel-bootstrap has NO Failsafe binding because it uses Surefire's `*Test.java` convention for unit tests, NOT Failsafe's `*IT.java`)
    - pom.xml `<dependencyManagement>` block (verifies opentelemetry-bom:1.61.0 + spring-boot-dependencies:3.4.13 + opentelemetry-instrumentation-bom-alpha:2.27.0-alpha are all imported, so this POM does NOT need to re-import them)
  </read_first>
  <action>
Create the new file `integration-tests/pom.xml` using the Write tool. The content is the verbatim RESEARCH §3.4 block (lines 779-889 of 06-RESEARCH.md) with no modifications:

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

DO NOT:
- Add `<dependencyManagement>` to this POM (BOMs are already imported by the parent — re-importing here is redundant and risks shadowing).
- Add `opentelemetry-exporter-otlp` to deps (test SDK uses InMemory* exporters ONLY — D-13 / PITFALLS #4 #11; would imply tests can use Batch processors which is forbidden).
- Add `<classifier>exec</classifier>` to the producer/consumer service deps (D-04: integration-tests resolves the plain jars, NOT the exec jars).
- Add `<scope>test</scope>` to producer-service / consumer-service deps (those need to be available at compile-time of the test classes — Maven default `compile` scope is correct here; only ADDITIONAL test-only libs get `<scope>test</scope>`).
- Add `<plugin>maven-surefire-plugin</plugin>` — this module has no `*Test.java` files; only `*IT.java`.
- Inherit from `spring-boot-starter-parent` (would re-introduce the BOM-ordering bug Phase 1 deliberately avoided — see parent pom.xml lines 7-17).
- Add a `<properties>` block (no module-specific overrides needed).
- Use `cat << EOF` heredoc — use the Write tool with verbatim content above.

After creating the file, run the two verification builds (acceptance criteria below cover this).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && test -f integration-tests/pom.xml && grep -q '<artifactId>integration-tests</artifactId>' integration-tests/pom.xml && grep -q '<artifactId>opentelemetry-sdk-testing</artifactId>' integration-tests/pom.xml && grep -q '<artifactId>maven-failsafe-plugin</artifactId>' integration-tests/pom.xml && grep -q '<version>3.5.5</version>' integration-tests/pom.xml && ! grep -q 'opentelemetry-exporter-otlp' integration-tests/pom.xml && ! grep -q 'spring-boot-starter-parent' integration-tests/pom.xml && mvn -B -pl integration-tests -am dependency:tree -DskipTests >/dev/null && mvn -B -pl integration-tests -am verify -DfailIfNoTests=false >/dev/null</automated>
  </verify>
  <acceptance_criteria>
    - `test -f integration-tests/pom.xml` (file exists)
    - `grep -q '<modelVersion>4.0.0</modelVersion>' integration-tests/pom.xml`
    - `grep -q '<artifactId>integration-tests</artifactId>' integration-tests/pom.xml`
    - `grep -q '<packaging>jar</packaging>' integration-tests/pom.xml`
    - `grep -q '<artifactId>ose-otel-demo-parent</artifactId>' integration-tests/pom.xml` (correct parent inheritance)
    - `! grep -q 'spring-boot-starter-parent' integration-tests/pom.xml` (does NOT inherit from Spring Boot parent — preserves Phase 1 BOM-ordering)
    - All 8 `<dependency>` blocks present:
      - `awk '/<artifactId>producer-service<\/artifactId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<version>${project.version}</version>'`
      - `awk '/<artifactId>consumer-service<\/artifactId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<version>${project.version}</version>'`
      - `grep -q '<artifactId>spring-boot-starter-test</artifactId>' integration-tests/pom.xml`
      - `grep -q '<artifactId>spring-boot-testcontainers</artifactId>' integration-tests/pom.xml`
      - `awk '/<groupId>org.testcontainers<\/groupId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<artifactId>junit-jupiter</artifactId>'`
      - `awk '/<groupId>org.testcontainers<\/groupId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<artifactId>rabbitmq</artifactId>'`
      - `grep -q '<artifactId>opentelemetry-sdk-testing</artifactId>' integration-tests/pom.xml`
      - `awk '/<groupId>org.awaitility<\/groupId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<artifactId>awaitility</artifactId>'`
    - Producer / consumer deps have NO `<classifier>` element (resolves to plain jar):
      - `! awk '/<artifactId>producer-service<\/artifactId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<classifier>'`
      - `! awk '/<artifactId>consumer-service<\/artifactId>/,/<\/dependency>/' integration-tests/pom.xml | grep -q '<classifier>'`
    - Failsafe declared explicitly with version pinned and both goals bound:
      - `awk '/<artifactId>maven-failsafe-plugin<\/artifactId>/,/<\/plugin>/' integration-tests/pom.xml | grep -q '<version>3.5.5</version>'`
      - `awk '/<artifactId>maven-failsafe-plugin<\/artifactId>/,/<\/plugin>/' integration-tests/pom.xml | grep -q '<goal>integration-test</goal>'`
      - `awk '/<artifactId>maven-failsafe-plugin<\/artifactId>/,/<\/plugin>/' integration-tests/pom.xml | grep -q '<goal>verify</goal>'`
    - `! grep -q '<artifactId>opentelemetry-exporter-otlp</artifactId>' integration-tests/pom.xml` (test SDK uses InMemory exporters only)
    - `! grep -q '<artifactId>maven-surefire-plugin</artifactId>' integration-tests/pom.xml` (no Surefire — this module has no `*Test.java`)
    - `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am dependency:tree -DskipTests` exits 0
    - `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am verify -DfailIfNoTests=false` exits 0 (Failsafe runs as no-op success — no `*IT.java` files yet, that lands in 06-05)
  </acceptance_criteria>
  <done>
`integration-tests/pom.xml` exists with 8 deps + explicit Failsafe binding. Both `mvn dependency:tree` and `mvn verify` exit 0. Module is empty of test classes (Plan 06-03/06-04/06-05 add them).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Local Maven cache (~/.m2/repository) → integration-tests classpath | Test build resolves deps from local cache; `mvn -am` populates cache from reactor + Maven Central |
| BOM-imported version pins → resolved artifact versions | Parent's `<dependencyManagement>` controls every version; this POM omits versions to inherit BOM-managed pins |
| Failsafe plugin → JVM running tests | Failsafe forks a JVM (or runs in-process) executing `*IT.java` test classes; tests have full host JVM access |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-02-01 | Tampering | Failsafe version drift | mitigate | Pin to `3.5.5` directly. RESEARCH §2.5 verified parent has no `<pluginManagement>` for Failsafe. Acceptance criterion grep-asserts the exact version string; CI / re-clone will fail loudly if the version is missing. |
| T-06-02-02 | Tampering | BOM-managed test deps silently bumping versions | mitigate | All 6 BOM-managed deps (Testcontainers + sdk-testing + Awaitility + Spring test starters) inherit from the parent's `<dependencyManagement>` BOM imports. Phase 1's `mise run verify:bom` task asserts one-version-per-OTel-artifact across the reactor. |
| T-06-02-03 | Spoofing | Wrong producer/consumer artifact resolved (exec jar instead of plain) | mitigate | The `<dependency>` blocks for producer-service / consumer-service explicitly OMIT `<classifier>` — Maven's default-artifact-resolution returns the plain jar. Acceptance criterion verifies via `! grep -q '<classifier>'` inside the dep blocks. Plan 06-03/06-04 will fail to compile if the wrong jar is on the classpath (ProducerApplication.class would be unfindable). |
| T-06-02-04 | Information Disclosure | Test SDK accidentally configured to export to a real OTLP backend | mitigate | `opentelemetry-exporter-otlp` is explicitly NOT declared in this POM (acceptance criterion `! grep -q opentelemetry-exporter-otlp`). Plan 06-03/06-04 wires only InMemory* exporters via opentelemetry-sdk-testing. Test SDK can never accidentally hit a real backend. |
| T-06-02-05 | Denial of Service | Testcontainers spinning up containers on a workshop laptop | accept | Workshop runs on attendee laptops with Docker available (preflight gate from Phase 1). Single RabbitMQ container peaks at ~150 MB; well within laptop budget. Test JVM lifecycle is bounded by `mvn verify`. |
| T-06-02-06 | Repudiation | Test results not recorded on CI | accept | TEST-06's contract is "exits non-zero on assertion failure" — runner-agnostic. CI YAML belongs in Phase 7 polish per CONTEXT deferred. |
| T-06-02-07 | Elevation of Privilege | Failsafe plugin runs in build JVM | accept | Plugin runs as the same user as `mvn` invocation. No privilege boundary crossed. Pinned version 3.5.5 from Maven Central is the standard build tooling trust model. |
| T-06-02-08 | Tampering | spring-boot-testcontainers carried defensively even though unused | accept | Per CONTEXT canonical_refs, declared to keep the option of reverting D-10 (manual two-context flow) to `@ServiceConnection` simple. Adds ~50 KB to the test classpath; benefit is forward-flexibility, cost is trivial. |
</threat_model>

<verification>
- `test -f integration-tests/pom.xml`
- `grep -q '<artifactId>integration-tests</artifactId>' integration-tests/pom.xml`
- `grep -q '<artifactId>opentelemetry-sdk-testing</artifactId>' integration-tests/pom.xml`
- `grep -q '<artifactId>maven-failsafe-plugin</artifactId>' integration-tests/pom.xml`
- `grep -q '<version>3.5.5</version>' integration-tests/pom.xml`
- `! grep -q '<artifactId>opentelemetry-exporter-otlp</artifactId>' integration-tests/pom.xml`
- `! grep -q 'spring-boot-starter-parent' integration-tests/pom.xml`
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am dependency:tree -DskipTests` exits 0
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am verify -DfailIfNoTests=false` exits 0 (Failsafe no-op success — no `*IT.java` files yet)
</verification>

<success_criteria>
1. `integration-tests/pom.xml` exists with packaging=jar inheriting from `ose-otel-demo-parent`.
2. 8 dependencies declared: producer-service, consumer-service (both no classifier), spring-boot-starter-test, spring-boot-testcontainers, testcontainers junit-jupiter, testcontainers rabbitmq, opentelemetry-sdk-testing, awaitility — all test-scoped except the two service deps.
3. NO `opentelemetry-exporter-otlp` dep (test SDK uses InMemory* only).
4. NO `spring-boot-starter-parent` inheritance (preserves Phase 1 BOM-ordering invariant).
5. `maven-failsafe-plugin:3.5.5` declared with `<goal>integration-test</goal>` + `<goal>verify</goal>` bindings (parent does NOT auto-bind Failsafe).
6. `mvn -pl integration-tests -am dependency:tree` exits 0.
7. `mvn -pl integration-tests -am verify` exits 0 as a Failsafe no-op success (no `*IT.java` yet).
</success_criteria>

<output>
After completion, create `.planning/phases/06-verification-tests/06-02-SUMMARY.md` with:
- Files created (1: integration-tests/pom.xml)
- Full file content as a code block
- `mvn -pl integration-tests -am dependency:tree` last 30 lines (showing all 8 deps + transitives)
- `mvn -pl integration-tests -am verify` exit code (0) and trailing output (Failsafe no-op success)
- Forward-link: Plan 06-03 will create `integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java` (the SDK singleton); Plan 06-04 creates `TestOtelConfiguration.java` (the @TestConfiguration facade); Plan 06-05 creates `OrderFlowIT.java` (the actual `*IT.java` test class)
</output>
