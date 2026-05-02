---
phase: 06-verification-tests
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - pom.xml
  - producer-service/pom.xml
  - consumer-service/pom.xml
autonomous: true
requirements:
  - TEST-06
tags:
  - maven
  - spring-boot-maven-plugin
  - classifier
  - reactor
  - phase-6
must_haves:
  truths:
    - id: PARENT-MODULE
      description: "Parent pom.xml <modules> list includes integration-tests"
      verify: "awk '/<modules>/,/<\\/modules>/' pom.xml | grep -q '<module>integration-tests</module>'"
    - id: PARENT-MODULE-ORDER
      description: "integration-tests is listed AFTER consumer-service (preserves Phase 1/2/3/4/5 alphabetical-ish ordering)"
      verify: "awk '/<modules>/,/<\\/modules>/' pom.xml | grep -oE '<module>[^<]+</module>' | tail -1 | grep -q 'integration-tests'"
    - id: PRODUCER-CLASSIFIER-EXEC
      description: "producer-service spring-boot-maven-plugin has explicit <execution> with <id>repackage</id> and <classifier>exec</classifier>"
      verify: "awk '/<groupId>org.springframework.boot<\\/groupId>/,/<\\/plugin>/' producer-service/pom.xml | grep -q '<classifier>exec</classifier>' && awk '/<groupId>org.springframework.boot<\\/groupId>/,/<\\/plugin>/' producer-service/pom.xml | grep -q '<id>repackage</id>'"
    - id: CONSUMER-CLASSIFIER-EXEC
      description: "consumer-service spring-boot-maven-plugin has explicit <execution> with <id>repackage</id> and <classifier>exec</classifier> (mirrors producer per Phase 2 DOC-05)"
      verify: "awk '/<groupId>org.springframework.boot<\\/groupId>/,/<\\/plugin>/' consumer-service/pom.xml | grep -q '<classifier>exec</classifier>' && awk '/<groupId>org.springframework.boot<\\/groupId>/,/<\\/plugin>/' consumer-service/pom.xml | grep -q '<id>repackage</id>'"
    - id: PRODUCER-PLAIN-JAR
      description: "After mvn package, producer plain classes jar contains ProducerApplication.class at top level (NOT under BOOT-INF/)"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service,consumer-service -am package -DskipTests >/dev/null && unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/producer/ProducerApplication\\.class' | grep -v 'BOOT-INF'"
    - id: PRODUCER-EXEC-JAR
      description: "Producer ALSO produces an exec-classifier executable fat jar"
      verify: "test -f producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar"
    - id: CONSUMER-PLAIN-JAR
      description: "Consumer plain classes jar contains ConsumerApplication.class at top level"
      verify: "unzip -l consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/consumer/ConsumerApplication\\.class' | grep -v 'BOOT-INF'"
    - id: CONSUMER-EXEC-JAR
      description: "Consumer ALSO produces an exec-classifier executable fat jar"
      verify: "test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar"
  artifacts:
    - path: pom.xml
      provides: "Parent reactor with integration-tests module added"
      contains: "<module>integration-tests</module>"
    - path: producer-service/pom.xml
      provides: "spring-boot-maven-plugin <execution> with classifier=exec"
      contains: "<classifier>exec</classifier>"
    - path: consumer-service/pom.xml
      provides: "spring-boot-maven-plugin <execution> with classifier=exec (mirror)"
      contains: "<classifier>exec</classifier>"
  key_links:
    - from: pom.xml
      to: integration-tests/pom.xml (created in 06-02)
      via: "<module>integration-tests</module> reactor entry"
      pattern: "<module>integration-tests</module>"
    - from: producer-service/pom.xml `<classifier>exec</classifier>`
      to: integration-tests/pom.xml producer-service <dependency> (no classifier — resolves to plain jar)
      via: "Maven default-artifact-vs-classifier resolution"
      pattern: "<classifier>exec</classifier>"
    - from: consumer-service/pom.xml `<classifier>exec</classifier>`
      to: integration-tests/pom.xml consumer-service <dependency>
      via: "Same Maven mechanism"
      pattern: "<classifier>exec</classifier>"
---

<objective>
Wire the Maven build infrastructure for Phase 6's new `integration-tests` module by (a) adding `<module>integration-tests</module>` to the parent reactor's `<modules>` list, and (b) adding an explicit `<execution><id>repackage</id></execution>` block with `<classifier>exec</classifier>` to BOTH `producer-service/pom.xml` and `consumer-service/pom.xml` `spring-boot-maven-plugin` declarations. This makes each service publish TWO artifacts: the plain classes jar (default — exposes `ProducerApplication.class` / `ConsumerApplication.class` at the top level for `SpringApplicationBuilder` use) and the `-exec` repackaged fat jar (runnable with `java -jar`).

Purpose: Without `<classifier>exec</classifier>`, Spring Boot 3.4's `spring-boot-maven-plugin` REPLACES the default artifact with the repackaged executable jar. `integration-tests` needs the plain classes jar to put `ProducerApplication.class` directly on the test classpath so `new SpringApplicationBuilder(ProducerApplication.class, TestOtelConfiguration.class).run()` (Plan 06-05) works. The `<classifier>exec</classifier>` knob is the canonical Spring Boot 3.4.13 mechanism per RESEARCH §2.1 / §3.1 (verified against Spring Boot 3.4.13 reference docs). Honors D-04 (CONTEXT.md).

Output: 3 files modified — parent `pom.xml` (~1 line added), `producer-service/pom.xml` (~14 lines added: replaces a 4-line bare plugin block with a 14-line explicit-execution block), `consumer-service/pom.xml` (mirror, byte-identical insertion). After running `mvn -pl producer-service,consumer-service -am package -DskipTests`, both target/ directories contain BOTH the plain `*.jar` AND a `*-exec.jar` artifact.

Why this is wave 1: this plan has zero dependencies. It must land FIRST so Plan 06-02's `integration-tests/pom.xml` resolves the producer/consumer service deps to the plain classes jar (no classifier → default artifact). Without 06-01, the parent reactor with the new module declaration would still reference a non-existent integration-tests directory in 06-02's wave; this plan creates the slot.

Why these three files in ONE plan: shared concern (Maven reactor + classifier mechanism); same conceptual change across both service POMs (DOC-05 mirror discipline carryforward); single Maven build invocation verifies all three (`mvn -pl producer-service,consumer-service -am package -DskipTests`). Splitting would force two waves where one suffices.

Note: After 06-01 lands, `mvn -B validate` from repo root will fail because `<module>integration-tests</module>` references a directory that doesn't yet exist on disk. This is intentional — Plan 06-02 creates `integration-tests/pom.xml` and resolves the reactor. Do not attempt to fix the validation error inside this plan.
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
@pom.xml
@producer-service/pom.xml
@consumer-service/pom.xml

<interfaces>
<!-- The Maven knobs this plan touches; sourced from RESEARCH §2.1 + §3.1 + PATTERNS §F-G. -->

Parent pom.xml current `<modules>` block (lines 27-31):
```xml
<modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
</modules>
```

producer-service/pom.xml current spring-boot-maven-plugin block (lines 162-166):
```xml
<!-- Version comes from parent <pluginManagement>. -->
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

consumer-service/pom.xml has the IDENTICAL bare 4-line block at the same approximate line range. Apply the same edit byte-for-byte.

CRITICAL Spring Boot 3.4.13 reference invariant (RESEARCH §2.1):
- `<classifier>` MUST live inside `<execution><configuration>`, NOT at plugin-level `<configuration>`.
- The execution must be named `<id>repackage</id>` and bind `<goal>repackage</goal>`.
- `<attach>` defaults to true → BOTH artifacts are installed/deployed.
- This project does NOT inherit from `spring-boot-starter-parent` (parent pom.xml lines 7-17), so there is NO pre-configured execution to extend — the producer/consumer POMs MUST declare the FULL execution block.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Add integration-tests module to parent pom.xml reactor</name>
  <files>pom.xml</files>
  <read_first>
    - pom.xml (full file — pay attention to lines 27-31 `<modules>` block)
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 5 — exact analog: itself; the existing 4-line `<modules>` block becomes 5 lines with `<module>integration-tests</module>` appended)
    - .planning/phases/06-verification-tests/06-RESEARCH.md (§3.4 final paragraph: "Parent POM update: Add `<module>integration-tests</module>` to the parent `pom.xml` `<modules>` list (existing list: `otel-bootstrap`, `producer-service`, `consumer-service`).")
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-01 — new top-level integration-tests Maven module)
  </read_first>
  <action>
Use Edit to modify `pom.xml`. Locate the existing `<modules>` block (lines 27-31):

```xml
<modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
</modules>
```

Replace it with the 6-line version that appends `<module>integration-tests</module>` as the LAST entry (matches PATTERNS.md File 5 § "Target after edit"):

```xml
<modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
    <module>integration-tests</module>
</modules>
```

DO NOT:
- Sort alphabetically (otel-bootstrap is conventionally first per Phase 1 even though `consumer-service` would precede it alphabetically — preserve existing order).
- Add a comment or trailing whitespace inside the new line.
- Touch any other section of pom.xml (no `<dependencyManagement>`, `<properties>`, `<build>` edits — that's Plan 06-02's job for the new module's POM).
- Use `cat << EOF` heredoc — use the Edit tool.
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && awk '/<modules>/,/<\/modules>/' pom.xml | grep -q '<module>integration-tests</module>' && awk '/<modules>/,/<\/modules>/' pom.xml | wc -l | grep -q '^6$'</automated>
  </verify>
  <acceptance_criteria>
    - `awk '/<modules>/,/<\/modules>/' pom.xml | grep -q '<module>integration-tests</module>'` returns 0
    - `awk '/<modules>/,/<\/modules>/' pom.xml | grep -c '<module>'` outputs `4` (otel-bootstrap, producer-service, consumer-service, integration-tests)
    - The four `<module>` entries appear in this exact order: `otel-bootstrap`, `producer-service`, `consumer-service`, `integration-tests` — verify with `awk '/<modules>/,/<\/modules>/' pom.xml | grep -oE '<module>[^<]+</module>' | tr '\n' ' ' | grep -q '<module>otel-bootstrap</module> <module>producer-service</module> <module>consumer-service</module> <module>integration-tests</module>'`
    - File ends without extra blank lines: `tail -1 pom.xml | grep -q '</project>'`
  </acceptance_criteria>
  <done>
pom.xml's `<modules>` list contains 4 modules ending in `<module>integration-tests</module>`. No other section of pom.xml modified.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Add explicit <classifier>exec</classifier> repackage execution to producer-service/pom.xml AND consumer-service/pom.xml spring-boot-maven-plugin blocks</name>
  <files>producer-service/pom.xml, consumer-service/pom.xml</files>
  <read_first>
    - producer-service/pom.xml (full file — pay attention to lines 160-188 `<build>` block; the bare 4-line plugin block at lines 162-166)
    - consumer-service/pom.xml (full file — locate the matching spring-boot-maven-plugin block at the same approximate range)
    - .planning/phases/06-verification-tests/06-RESEARCH.md (§2.1 — Spring Boot 3.4.13 `<classifier>` syntax verified against the official docs; §3.1 — paste-ready XML block lines 209-233)
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 6 / §File 7 — both POMs receive byte-identical insertion; verification command shows `BOOT-INF/` is absent from the plain jar)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-04 — integration-tests depends on plain classes jars; D-02 / DOC-05 mirror discipline preserved across both service POMs)
    - pom.xml lines 7-17 (the explicit comment about why this project does NOT inherit from `spring-boot-starter-parent` — explains why we must declare the full execution rather than just override config)
  </read_first>
  <behavior>
    - After this task: `mvn -pl producer-service,consumer-service -am package -DskipTests` produces BOTH `producer-service-0.1.0-SNAPSHOT.jar` (plain classes, ProducerApplication.class at top level) AND `producer-service-0.1.0-SNAPSHOT-exec.jar` (Spring Boot fat jar, ProducerApplication.class under BOOT-INF/classes/).
    - Mirror behavior for consumer-service.
    - Edge case: re-running `mvn package` is idempotent — both artifacts present, no errors about duplicate executions.
    - Negative test: `unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -F 'BOOT-INF/'` returns NOTHING (the plain jar has no BOOT-INF/ tree).
  </behavior>
  <action>
Apply the SAME edit to BOTH `producer-service/pom.xml` and `consumer-service/pom.xml`. Each currently contains a bare 4-line plugin block:

```xml
<!-- Version comes from parent <pluginManagement>. -->
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

Replace with this 19-line explicit-execution block (paste verbatim from RESEARCH §3.1 lines 209-233 — applies to BOTH service POMs identically per CONTEXT D-02 / DOC-05 mirror discipline):

```xml
<!-- Version comes from parent <pluginManagement>. -->
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <executions>
    <!--
      Phase 6 / D-04: keep the plain classes jar as the default artifact so
      integration-tests can resolve <dependency>producer-service</dependency>
      and find ProducerApplication.class on the classpath. The <classifier>exec</classifier>
      causes the repackaged executable fat jar to be installed as
      producer-service-${version}-exec.jar — runnable with java -jar but
      OUT of the way of the test-classpath module dependency.
      <attach> defaults to true; both artifacts deploy.
    -->
    <execution>
      <id>repackage</id>
      <goals>
        <goal>repackage</goal>
      </goals>
      <configuration>
        <classifier>exec</classifier>
      </configuration>
    </execution>
  </executions>
</plugin>
```

For `consumer-service/pom.xml` use the same block but adjust the inline comment text "producer-service" → "consumer-service" and "ProducerApplication.class" → "ConsumerApplication.class" so the comment accurately describes the artifact this POM produces. The `<classifier>exec</classifier>` and `<id>repackage</id>` and `<goal>repackage</goal>` are byte-identical between the two files — those are the load-bearing tokens.

DO NOT:
- Place `<classifier>` at the top level `<plugin><configuration>` (RESEARCH §2.1 explicitly verified against Spring Boot 3.4.13 docs: classifier MUST be inside `<execution><configuration>`, not plugin-level).
- Add `<attach>true</attach>` (default; redundant verbosity).
- Add `<phase>package</phase>` (the `repackage` goal binds to `package` phase by Spring Boot defaults).
- Touch the JIB plugin block (lines 169-186) — JIB builds an image from the project classes; it doesn't depend on the repackaged jar and is unaffected by `<classifier>`.
- Add a SEPARATE second execution to disable repackaging (we WANT both artifacts).
- Use a heredoc to write — use the Edit tool.

After applying the edits, run the verification command from PATTERNS.md File 6:
```bash
cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service,consumer-service -am clean package -DskipTests
```

Then verify the artifact shape (acceptance criteria below cover this).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service,consumer-service -am clean package -DskipTests >/dev/null 2>&1 && test -f producer-service/target/producer-service-0.1.0-SNAPSHOT.jar && test -f producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar && test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar && test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar && unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/producer/ProducerApplication\.class' | grep -v 'BOOT-INF' | grep -q ProducerApplication && unzip -l consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/consumer/ConsumerApplication\.class' | grep -v 'BOOT-INF' | grep -q ConsumerApplication && ! unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -q '^.*BOOT-INF/' && unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar | grep -q 'BOOT-INF/'</automated>
  </verify>
  <acceptance_criteria>
    - `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' producer-service/pom.xml | grep -q '<id>repackage</id>'` returns 0
    - `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' producer-service/pom.xml | grep -q '<classifier>exec</classifier>'` returns 0
    - `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' producer-service/pom.xml | grep -q '<goal>repackage</goal>'` returns 0
    - `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' consumer-service/pom.xml | grep -q '<id>repackage</id>'` returns 0
    - `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' consumer-service/pom.xml | grep -q '<classifier>exec</classifier>'` returns 0
    - `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' consumer-service/pom.xml | grep -q '<goal>repackage</goal>'` returns 0
    - `! grep -E '^\s*<configuration>\s*$' producer-service/pom.xml | head -2 | grep -q '<classifier>'` (classifier NOT at plugin-level configuration — RESEARCH §2.1 invariant)
    - `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service,consumer-service -am clean package -DskipTests` exits 0
    - `test -f producer-service/target/producer-service-0.1.0-SNAPSHOT.jar` (plain jar)
    - `test -f producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar` (executable fat jar)
    - `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` (plain jar)
    - `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar` (executable fat jar)
    - `unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/producer/ProducerApplication\.class'` shows ONE line and the path does NOT start with `BOOT-INF/`
    - `unzip -l consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/consumer/ConsumerApplication\.class'` shows ONE line and the path does NOT start with `BOOT-INF/`
    - `unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar | grep -q '^.*BOOT-INF/'` returns 0 (exec jar IS the Spring Boot fat jar — has BOOT-INF/)
    - `! unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -qE '^.*BOOT-INF/'` (plain jar has NO BOOT-INF/ tree)
  </acceptance_criteria>
  <done>
Both `producer-service/pom.xml` and `consumer-service/pom.xml` carry an explicit `<execution><id>repackage</id>...<configuration><classifier>exec</classifier></configuration></execution>` inside their `spring-boot-maven-plugin` block. `mvn -pl producer-service,consumer-service -am clean package -DskipTests` exits 0 and produces 4 jars total: 2 plain (with ApplicationClass.class at top level) + 2 exec (Spring Boot fat jars with BOOT-INF/).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Maven reactor → installed artifacts (~/.m2/repository) | `mvn install` writes to local Maven cache; subsequent reactor builds and the integration-tests module read from there |
| Service POM → spring-boot-maven-plugin invocation | Plugin runs `repackage` goal during `package` phase; produces both classified and unclassified artifacts |
| Reactor parent → child modules (build order) | Parent's `<modules>` list determines reactor topological order; integration-tests builds last (no children depend on it) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-01-01 | Tampering | spring-boot-maven-plugin `<execution>` block | mitigate | RESEARCH §2.1 verified the canonical Spring Boot 3.4.13 `<classifier>` syntax. Acceptance criteria use grep against EXACT XML token strings (`<classifier>exec</classifier>`, `<id>repackage</id>`, `<goal>repackage</goal>`) — typos surface immediately at acceptance gate. Smoke verification (`mvn package` produces both jars) confirms the configuration works at runtime. |
| T-06-01-02 | Information Disclosure | Plain classes jar exposes ProducerApplication.class at top level | accept | The plain jar is a build artifact published only to the local Maven repository (and the integration-tests module's classpath at test-time). It does NOT ship to attendees or production runtime. The exec jar (with BOOT-INF/ shielding) is the workshop's actual runnable artifact. Risk to attendees: zero — plain jar is internal build output. |
| T-06-01-03 | Tampering | Parent reactor `<modules>` ordering | accept | Maven topological-sort is deterministic; `integration-tests` declared last has no children depending on it, so reordering would not break correctness — only readability. The acceptance criterion verifies the precise textual ordering for review-friendliness, not for build correctness. |
| T-06-01-04 | Denial of Service | `mvn package` doubles the artifact count per service | accept | Two jars (~30 MB exec + ~50 KB plain per service) is a workshop-laptop-trivial 60 MB total disk overhead. Build time delta is the cost of one repackage execution, ~3-5 seconds — irrelevant for a workshop. |
| T-06-01-05 | Elevation of Privilege | spring-boot-maven-plugin runs at build time | accept | Plugin runs in the same JVM/user as `mvn` invocation. No privilege boundary crossed. Standard Maven plugin trust model — the plugin is BOM-managed (Spring Boot 3.4.13 release artifact, signed by Spring team). |
| T-06-01-06 | Spoofing | Wrong artifact resolved on test classpath (e.g., exec jar by accident) | mitigate | Acceptance criteria EXPLICITLY verify both that the plain jar contains `ProducerApplication.class` at top level AND that the exec jar contains `BOOT-INF/`. Plan 06-02 will reference `<dependency>` with NO `<classifier>` — Maven's default-artifact-resolution picks the plain jar (D-04 invariant); reversing this would silently break Plan 06-05's `SpringApplicationBuilder(ProducerApplication.class)` call at test runtime. |
| T-06-01-07 | Repudiation | Build reproducibility across re-runs | mitigate | `mvn -B` (batch mode) flag in acceptance criteria + the deterministic reactor + the pinned Spring Boot 3.4.13 BOM (parent pom.xml) ensures byte-identical artifacts on re-run. The plain jar's manifest contains no timestamps that would cause hash drift at the workshop checkpoint. |
</threat_model>

<verification>
- `awk '/<modules>/,/<\/modules>/' pom.xml | grep -q '<module>integration-tests</module>'` returns 0
- `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' producer-service/pom.xml | grep -q '<classifier>exec</classifier>'` returns 0
- `awk '/<groupId>org.springframework.boot<\/groupId>/,/<\/plugin>/' consumer-service/pom.xml | grep -q '<classifier>exec</classifier>'` returns 0
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service,consumer-service -am clean package -DskipTests` exits 0
- `test -f producer-service/target/producer-service-0.1.0-SNAPSHOT.jar` (plain)
- `test -f producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar` (exec)
- `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` (plain)
- `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT-exec.jar` (exec)
- `unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/producer/ProducerApplication\.class' | grep -v 'BOOT-INF'` returns ProducerApplication.class line at the top level
- `unzip -l consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar | grep -E 'com/example/consumer/ConsumerApplication\.class' | grep -v 'BOOT-INF'` returns ConsumerApplication.class at top level
- `! unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT.jar | grep -qE '^.*BOOT-INF/'` (plain jar has no BOOT-INF/ tree)
- `unzip -l producer-service/target/producer-service-0.1.0-SNAPSHOT-exec.jar | grep -q 'BOOT-INF/'` (exec jar IS the Spring Boot repackaged fat jar)
</verification>

<success_criteria>
1. Parent `pom.xml` `<modules>` list ends with `<module>integration-tests</module>` (4 entries total).
2. `producer-service/pom.xml` and `consumer-service/pom.xml` each declare an explicit `<execution><id>repackage</id></execution>` with `<classifier>exec</classifier>` inside their `spring-boot-maven-plugin` block.
3. `mvn -pl producer-service,consumer-service -am clean package -DskipTests` exits 0.
4. Each service publishes BOTH the plain classes jar (ApplicationClass.class at top level) AND the `-exec` repackaged executable fat jar (BOOT-INF/-shielded).
5. JIB plugin block in each service POM is unchanged.
6. Mirror discipline preserved: the spring-boot-maven-plugin XML block is byte-identical between producer and consumer (modulo the inline comment's "producer" vs "consumer" wording).
</success_criteria>

<output>
After completion, create `.planning/phases/06-verification-tests/06-01-SUMMARY.md` with:
- Files modified (3 — pom.xml, producer-service/pom.xml, consumer-service/pom.xml) and their byte-deltas
- Verbatim diff of the 3 edits
- Output of `mvn -B -pl producer-service,consumer-service -am clean package -DskipTests` (last 5 lines + exit code)
- `unzip -l` output for the four resulting jars (plain + exec for each service), highlighting:
  - ProducerApplication.class at top level in producer-service-0.1.0-SNAPSHOT.jar
  - ConsumerApplication.class at top level in consumer-service-0.1.0-SNAPSHOT.jar
  - BOOT-INF/ tree present in both `*-exec.jar` artifacts
- Forward-link: Plan 06-02 will create `integration-tests/pom.xml` whose `<dependency>producer-service</dependency>` (no classifier) resolves to the plain jar produced here
- Note that Plan 06-02 will run `mvn -pl integration-tests dependency:tree` which empirically verifies the resolution
</output>
