---
id: 1-01-maven-skeleton
phase: 1-baseline-scaffold
plan: 01
type: execute
wave: 1
depends_on: []
requirements: [INFRA-01]
files_modified:
  - pom.xml
  - otel-bootstrap/pom.xml
  - otel-bootstrap/src/main/java/com/example/otel/package-info.java
  - producer-service/pom.xml
  - consumer-service/pom.xml
autonomous: true
must_haves:
  truths:
    - "Parent POM imports OTel SDK BOM (1.61.0) BEFORE OTel Instrumentation alpha BOM (2.27.0-alpha) BEFORE Spring Boot BOM (3.4.13) in <dependencyManagement>"
    - "mvn dependency:tree -Dincludes=io.opentelemetry returns zero matches across all modules"
    - "mvn validate succeeds — maven-enforcer-plugin's dependencyConvergence + requireMavenVersion[3.9.0,) + requireJavaVersion[17,18) all pass"
    - "All four modules (parent + otel-bootstrap + producer-service + consumer-service) compile cleanly via mvn -DskipTests install"
  artifacts:
    - path: "pom.xml"
      provides: "Aggregator parent POM with BOM-import scaffolding (no <parent> element) and maven-enforcer-plugin bound to validate phase"
      contains: "<artifactId>opentelemetry-bom</artifactId>"
    - path: "producer-service/pom.xml"
      provides: "Producer Maven module with Spring Boot starters (web/amqp/actuator/test) — NO OTel dependencies"
      contains: "<artifactId>spring-boot-starter-web</artifactId>"
    - path: "consumer-service/pom.xml"
      provides: "Consumer Maven module with Spring Boot starters (amqp/actuator/web/test) — NO OTel dependencies"
      contains: "<artifactId>spring-boot-starter-amqp</artifactId>"
    - path: "otel-bootstrap/pom.xml"
      provides: "Empty placeholder Maven module (Phase 3 populates with AMQP propagation pair)"
      contains: "<artifactId>otel-bootstrap</artifactId>"
    - path: "otel-bootstrap/src/main/java/com/example/otel/package-info.java"
      provides: "Single source file so the empty module compiles to a non-empty JAR"
      contains: "package com.example.otel"
  key_links:
    - from: "producer-service/pom.xml"
      to: "pom.xml"
      via: "<parent><relativePath>../pom.xml</relativePath></parent>"
      pattern: "ose-otel-demo-parent"
    - from: "consumer-service/pom.xml"
      to: "pom.xml"
      via: "<parent><relativePath>../pom.xml</relativePath></parent>"
      pattern: "ose-otel-demo-parent"
    - from: "pom.xml"
      to: "Maven Central"
      via: "BOM imports in <dependencyManagement>"
      pattern: "io.opentelemetry:opentelemetry-bom:1.61.0"
---

<objective>
Scaffold the four-POM Maven multi-module build with BOM-import ordering that puts OpenTelemetry FIRST and Spring Boot LAST in `<dependencyManagement>`. This is INFRA-01: when Phase 2 adds its first `<dependency>io.opentelemetry:*</dependency>`, Maven must resolve to OTel-BOM-managed versions, not Spring-Boot-BOM-managed ones (Pitfall A — Maven's "first declaration wins" rule). Phase 1 declares the BOMs but adds zero OTel `<dependency>` references — verified by `mvn dependency:tree -Dincludes=io.opentelemetry` returning zero matches.

Purpose: Foundation for every later phase. Get the BOM ordering wrong here and Phase 2+ hits NoSuchMethodError at runtime.
Output: 4 POMs + 1 placeholder `package-info.java`; `mvn -DskipTests install` succeeds.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/1-baseline-scaffold/1-RESEARCH.md
@CLAUDE.md
</context>

<tasks>

<task id="1-01-T1" type="auto">
  <name>Task 1: Write parent POM with BOM-import ordering + maven-enforcer-plugin</name>
  <files>pom.xml</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 390-502 — "Parent POM" code example with verified BOM ordering)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 313-323 — Pitfall A: BOM ordering rule)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 354-381 — Pitfall E: enforcer must bind to validate phase)
    - CLAUDE.md (lines covering "Tech stack" constraints — Spring Boot 3.4.13, Java 17, Maven 3.9.x pinned by user)
  </read_first>
  <action>
    Create `pom.xml` at the repo root EXACTLY matching the verified template in 1-RESEARCH.md lines 394-500. Concrete values to include verbatim:

    1. `<modelVersion>4.0.0</modelVersion>`, NO `<parent>` element (this is critical — `spring-boot-starter-parent` would inject the Spring Boot BOM before our `<dependencyManagement>` block runs, breaking BOM ordering).
    2. Coordinates: `<groupId>com.example</groupId>`, `<artifactId>ose-otel-demo-parent</artifactId>`, `<version>0.1.0-SNAPSHOT</version>`, `<packaging>pom</packaging>`.
    3. `<modules>` listing in this order: `otel-bootstrap`, `producer-service`, `consumer-service`.
    4. `<properties>`:
       - `project.build.sourceEncoding=UTF-8`
       - `java.version=17`, `maven.compiler.source=17`, `maven.compiler.target=17`
       - `opentelemetry.version=1.61.0`
       - `opentelemetry-instrumentation.version=2.27.0-alpha`
       - `spring-boot.version=3.4.13`
    5. `<dependencyManagement><dependencies>` with EXACTLY these three `<scope>import</scope>` BOMs IN THIS ORDER:
       - **1st** `io.opentelemetry:opentelemetry-bom:${opentelemetry.version}` (`<type>pom</type>`)
       - **2nd** `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:${opentelemetry-instrumentation.version}` (`<type>pom</type>`)
       - **3rd** `org.springframework.boot:spring-boot-dependencies:${spring-boot.version}` (`<type>pom</type>`)
    6. `<build><pluginManagement><plugins>` declares `org.springframework.boot:spring-boot-maven-plugin:${spring-boot.version}` (so child service modules can omit the version).
    7. `<build><plugins>` declares `org.apache.maven.plugins:maven-enforcer-plugin:3.5.0` with execution id `enforce`, `<phase>validate</phase>`, goal `enforce`, and three rules: `<dependencyConvergence/>`, `<requireMavenVersion><version>[3.9.0,)</version></requireMavenVersion>`, `<requireJavaVersion><version>[17,18)</version></requireJavaVersion>`. The `validate` phase binding is mandatory (Pitfall E) — `mvn install` and `mvn package` both run `validate`, so the BOM convergence gate fires on every build.

    Add inline XML comments above each BOM explaining ordering ("1st: OTel SDK BOM — must precede Spring Boot to win on shared transitive artifacts when Phase 2 adds OTel deps"). Workshop attendees read these comments.

    DO NOT add: `<parent>spring-boot-starter-parent</parent>`, any `io.opentelemetry:*` `<dependency>` (BOM declaration only — Phase 2 adds the first dep), `<scm>`, `<url>`, `<developers>`, `<licenses>` (out of scope for workshop).
  </action>
  <acceptance_criteria>
    - `test -f pom.xml` exits 0
    - `grep -c '<artifactId>opentelemetry-bom</artifactId>' pom.xml` returns >= 1
    - `grep -c '<artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>' pom.xml` returns >= 1
    - `grep -c '<artifactId>spring-boot-dependencies</artifactId>' pom.xml` returns >= 1
    - BOM ordering verified: `awk '/opentelemetry-bom/{a=NR} /opentelemetry-instrumentation-bom-alpha/{b=NR} /spring-boot-dependencies/{c=NR} END{ if(a<b && b<c) print "OK"; else print "FAIL "a" "b" "c }' pom.xml` outputs `OK`
    - `grep -c '<artifactId>maven-enforcer-plugin</artifactId>' pom.xml` returns >= 1
    - `grep -c '<phase>validate</phase>' pom.xml` returns >= 1
    - `grep -c '<dependencyConvergence/>' pom.xml` returns 1
    - `grep -c '<parent>' pom.xml` returns 0 (NO `<parent>` in parent POM — BOM-import pattern)
    - `grep -c 'opentelemetry.*<dependency>' pom.xml` returns 0 (no `<dependency>` element on an OTel artifact — BOM only)
    - `xmllint --noout pom.xml` exits 0 (well-formed XML)
  </acceptance_criteria>
  <verify>
    <automated>xmllint --noout pom.xml &amp;&amp; awk '/opentelemetry-bom/{a=NR} /opentelemetry-instrumentation-bom-alpha/{b=NR} /spring-boot-dependencies/{c=NR} END{ if(a&lt;b &amp;&amp; b&lt;c) exit 0; else exit 1 }' pom.xml</automated>
  </verify>
  <done>Parent POM exists, well-formed XML, three BOMs imported in OTel→Instrumentation→Spring-Boot order, enforcer plugin bound to validate phase, no `<parent>` element, no OTel `<dependency>` references.</done>
</task>

<task id="1-01-T2" type="auto">
  <name>Task 2: Write child module POMs (otel-bootstrap, producer-service, consumer-service) + placeholder package-info</name>
  <files>otel-bootstrap/pom.xml, otel-bootstrap/src/main/java/com/example/otel/package-info.java, producer-service/pom.xml, consumer-service/pom.xml</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 504-554 — producer-service/pom.xml example)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 556-605 — consumer-service/pom.xml example, includes spring-boot-starter-web for actuator HTTP)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 607-640 — otel-bootstrap/pom.xml + package-info.java)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 1278-1281 — Open Question #1 resolution: consumer DOES include starter-web for /actuator/health on port 8081)
    - pom.xml (just created in T1 — shows the exact `<parent>` coordinates child modules must reference)
  </read_first>
  <action>
    Create three module POMs and one Java placeholder file. All three module POMs share these structural facts:
    - `<parent><groupId>com.example</groupId><artifactId>ose-otel-demo-parent</artifactId><version>0.1.0-SNAPSHOT</version><relativePath>../pom.xml</relativePath></parent>`
    - `<packaging>jar</packaging>`
    - NO `<dependencyManagement>` (inherited from parent — the BOM imports cover everything)
    - NO `io.opentelemetry:*` dependency anywhere

    **`producer-service/pom.xml`:** `<artifactId>producer-service</artifactId>`. Dependencies (no `<version>` on any — BOM-managed):
    - `org.springframework.boot:spring-boot-starter-web`
    - `org.springframework.boot:spring-boot-starter-amqp`
    - `org.springframework.boot:spring-boot-starter-actuator`
    - `org.springframework.boot:spring-boot-starter-test` (`<scope>test</scope>`)

    `<build><plugins>` includes `org.springframework.boot:spring-boot-maven-plugin` (no version — pluginManagement in parent supplies it).

    **`consumer-service/pom.xml`:** `<artifactId>consumer-service</artifactId>`. Same dependencies as producer (web + amqp + actuator + test). Per RESEARCH.md Open Q #1, consumer DOES include `spring-boot-starter-web` so `/actuator/health` is reachable on port 8081 (Tomcat embedded server). Same `spring-boot-maven-plugin` block.

    **`otel-bootstrap/pom.xml`:** `<artifactId>otel-bootstrap</artifactId>`. NO `<dependencies>` block (or empty `<dependencies/>`). NO `<build>` block. Add an XML comment: `<!-- Phase 1: empty module. Phase 3 populates it with the AMQP propagation pair. -->`

    **`otel-bootstrap/src/main/java/com/example/otel/package-info.java`:** Single Java file with package declaration `package com.example.otel;` and a JavaDoc block above it (matching RESEARCH.md lines 631-640) explaining the module is intentionally empty in Phase 1, populated in Phase 3 (AMQP propagation pair), and that SDK bootstrap is intentionally NOT extracted here per PROJECT.md (TRACE-01 / DOC-05). This file's existence prevents some Maven plugins from treating an empty `src/main/java` as a build error.

    Create directory structure: `mkdir -p otel-bootstrap/src/main/java/com/example/otel producer-service/src/main/java consumer-service/src/main/java` (the producer/consumer source roots are populated in plans 04 and 05; create the dirs here so `mvn` recognises the layout).
  </action>
  <acceptance_criteria>
    - `test -f producer-service/pom.xml &amp;&amp; test -f consumer-service/pom.xml &amp;&amp; test -f otel-bootstrap/pom.xml` exits 0
    - `test -f otel-bootstrap/src/main/java/com/example/otel/package-info.java` exits 0
    - `grep -c 'ose-otel-demo-parent' producer-service/pom.xml consumer-service/pom.xml otel-bootstrap/pom.xml | awk -F: 'BEGIN{ok=1} {if($2&lt;1)ok=0} END{exit !ok}'` exits 0 (every child references the parent)
    - `grep -c '<artifactId>spring-boot-starter-web</artifactId>' producer-service/pom.xml` returns 1
    - `grep -c '<artifactId>spring-boot-starter-amqp</artifactId>' producer-service/pom.xml consumer-service/pom.xml | awk -F: '{s+=$2} END{exit (s==2)?0:1}'` exits 0 (both producer + consumer have amqp)
    - `grep -c '<artifactId>spring-boot-starter-web</artifactId>' consumer-service/pom.xml` returns 1 (consumer DOES include web for actuator HTTP per RESEARCH Open Q #1)
    - `grep -c '<artifactId>spring-boot-starter-actuator</artifactId>' producer-service/pom.xml consumer-service/pom.xml | awk -F: '{s+=$2} END{exit (s==2)?0:1}'` exits 0
    - `grep -rEc 'io\.opentelemetry' producer-service/pom.xml consumer-service/pom.xml otel-bootstrap/pom.xml | awk -F: '{s+=$2} END{exit (s==0)?0:1}'` exits 0 (zero OTel references in any child POM)
    - `grep -c 'package com.example.otel' otel-bootstrap/src/main/java/com/example/otel/package-info.java` returns 1
    - `xmllint --noout producer-service/pom.xml consumer-service/pom.xml otel-bootstrap/pom.xml` exits 0
  </acceptance_criteria>
  <verify>
    <automated>xmllint --noout pom.xml producer-service/pom.xml consumer-service/pom.xml otel-bootstrap/pom.xml &amp;&amp; ! grep -rE 'io\.opentelemetry' producer-service/pom.xml consumer-service/pom.xml otel-bootstrap/pom.xml</automated>
  </verify>
  <done>Four POMs (parent + three children) plus one `package-info.java`; every child references parent via relativePath; producer + consumer carry only Spring Boot starters; otel-bootstrap is empty/placeholder; zero OTel `<dependency>` references anywhere.</done>
</task>

<task id="1-01-T3" type="auto">
  <name>Task 3: Verify the build — Phase 1 BOM gate (zero OTel libs on classpath)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 765-776 — verify:bom task definition; the gate is `mvn dependency:tree -Dincludes=io.opentelemetry` returning zero matches)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 26-39 — phase exit criteria: criterion #3 is THE Phase 1 gate)
    - pom.xml, producer-service/pom.xml, consumer-service/pom.xml, otel-bootstrap/pom.xml (just created)
  </read_first>
  <action>
    Run the canonical Phase 1 BOM gate verifications. This task creates no files — it proves the previous two tasks shipped a green baseline.

    Step 1: `mvn -DskipTests install` from the repo root. This MUST succeed. The maven-enforcer-plugin's `dependencyConvergence` rule runs in the `validate` phase (which fires on `install`), so a green `install` proves BOM convergence.

    Step 2: `mvn dependency:tree -Dincludes=io.opentelemetry`. This MUST output zero matching artifacts (the per-module trees print headers but no `io.opentelemetry:*` lines). RESEARCH.md's `verify:bom` task encodes this exact assertion. The relevant lines in dependency:tree output that we filter against are NOT the diagnostic header lines but actual coordinate lines like `[INFO] +- io.opentelemetry:opentelemetry-api:jar:1.x.x:compile`. Use a filtered grep that excludes the `[INFO] -` separator lines and the coordinate-list-empty header.

    Step 3: `mvn enforcer:enforce`. This MUST succeed independently (proves the plugin is wired even outside the validate phase).

    If any step fails, the build is broken and the planner cannot proceed to Wave 2. Common failure modes (document for executor): conflicting transitive versions of `slf4j-api`, `jackson-*`, `netty-*` between starters → enforcer flags them. The fix is almost always to read the `dependencyConvergence` error message and add a single `<dependency>` to `<dependencyManagement>` pinning the version (Spring Boot's BOM should already cover all of these — if it doesn't, something in our POM is wrong).
  </action>
  <acceptance_criteria>
    - `mvn -DskipTests install` exits 0
    - `mvn dependency:tree -Dincludes=io.opentelemetry 2>&amp;1 | grep -vE '^\[INFO\] (Scanning|Reactor|Build|---|BUILD|Total time|Finished at|----)' | grep -v '^\[INFO\] $' | grep -cE 'io\.opentelemetry:[a-z-]+:(jar|pom)' | awk '{exit ($1==0)?0:1}'` exits 0 (zero OTel artifacts on classpath — THE Phase 1 gate)
    - `mvn enforcer:enforce` exits 0
    - `find . -name 'target' -type d | wc -l` returns >= 4 (parent + 3 child modules each produced a target/)
    - `test -f producer-service/target/producer-service-0.1.0-SNAPSHOT.jar` exits 0
    - `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` exits 0
    - `test -f otel-bootstrap/target/otel-bootstrap-0.1.0-SNAPSHOT.jar` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -DskipTests -q install &amp;&amp; ! mvn -q dependency:tree -Dincludes=io.opentelemetry 2>&amp;1 | grep -E '^\[INFO\] [+\\][- ]+io\.opentelemetry:'</automated>
  </verify>
  <done>`mvn install` is green, `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matching artifacts, enforcer plugin passes. Phase 1 INFRA-01 success gate proven.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Phase 1 — minimal scaffold)

| Boundary | Description |
|----------|-------------|
| External Maven Central → local repo | First-run downloads of POMs, JARs, and BOMs |
| Local Maven repo → build classpath | All deps come from `~/.m2`; no curl/script-side fetching |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-1-01-01 | Tampering | Maven dependency confusion (typo'd group ID pulls a malicious package) | mitigate | Use BOMs (no version drift); pin all three BOM versions to known-good releases (`opentelemetry-bom:1.61.0`, `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`, `spring-boot-dependencies:3.4.13`); maven-enforcer-plugin's `dependencyConvergence` rule fails the build on any version conflict |
| T-1-01-02 | Tampering | Floating image / version tags introducing supply-chain drift | mitigate | All artifact versions referenced in this plan are pinned to exact releases; no `LATEST`, no version ranges in `<dependency>` declarations |
| T-1-01-03 | Information Disclosure | POM leaking internal infrastructure details | accept | `pom.xml` is intended to be public (workshop artifact); no secrets, no internal hosts referenced |
| T-1-01-04 | Spoofing / Tampering | Untrusted Maven plugin loaded from external repository | accept | Standard Maven Central source (`org.apache.maven.plugins`, `org.springframework.boot`, `io.opentelemetry.*`); no custom `<pluginRepositories>` declared |

**Phase scope:** Workshop scaffold — no Internet exposure, no persistence, no secrets. Phase 1 surface limited to: localhost POST /orders accepting JSON (added in plan 04), AMQP publish to localhost broker. Spring Boot's default request size limits + Jackson default deserialization apply. No authentication intentional (workshop demo). Out of scope: TLS, authn/authz, rate-limiting, input fuzzing.
</threat_model>

<verification>
- `mvn -DskipTests install` exits 0 from repo root.
- `mvn dependency:tree -Dincludes=io.opentelemetry` lists no `io.opentelemetry:*` artifact (only INFO headers).
- `mvn enforcer:enforce` exits 0.
- `xmllint --noout pom.xml producer-service/pom.xml consumer-service/pom.xml otel-bootstrap/pom.xml` exits 0.
- BOM ordering check: in `pom.xml`, line number of `opentelemetry-bom` < line number of `opentelemetry-instrumentation-bom-alpha` < line number of `spring-boot-dependencies`.
- All four `target/*.jar` outputs exist after `install`.
</verification>

<success_criteria>
- INFRA-01 satisfied: parent POM imports OTel BOM before Spring Boot BOM; `mvn dependency:tree` shows one version per `io.opentelemetry` artifact (today: zero artifacts — vacuously true).
- Phase 1 exit gate THE most important: `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matching artifacts.
- maven-enforcer-plugin bound to `validate` phase passes on every `mvn install`.
- Wave 2 (plans 04 + 05) can build their service skeletons against this scaffolding without further Maven changes.
</success_criteria>

<output>
After completion, create `.planning/phases/1-baseline-scaffold/1-01-SUMMARY.md` documenting:
- Parent POM final BOM ordering and rationale (single sentence: "OTel BOMs before Spring Boot BOM because Maven uses first-declaration-wins for `<scope>import</scope>`")
- Confirmed `mvn dependency:tree -Dincludes=io.opentelemetry` output (paste relevant lines)
- Confirmed `mvn enforcer:enforce` output (paste BUILD SUCCESS line)
- Any version drift handled (e.g., if a transitive Jackson version forced a `<dependencyManagement>` pin)
- Files created: 4 POMs + 1 `package-info.java`
</output>
