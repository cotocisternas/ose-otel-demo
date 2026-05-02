---
phase: 05-logs-correlation
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - producer-service/pom.xml
  - consumer-service/pom.xml
autonomous: true
requirements:
  - LOG-02
tags:
  - logback
  - opentelemetry
  - maven
  - bom
must_haves:
  truths:
    - id: LOG-02
      description: "opentelemetry-logback-appender-1.0 artifact on classpath in BOTH services (BOM-managed by opentelemetry-instrumentation-bom-alpha:2.27.0-alpha)"
      verify: "grep -q 'opentelemetry-logback-appender-1.0' producer-service/pom.xml && grep -q 'opentelemetry-logback-appender-1.0' consumer-service/pom.xml"
    - id: LOG-02-mdc
      description: "opentelemetry-logback-mdc-1.0 artifact on classpath in BOTH services (separate artifact — verified non-transitive per RESEARCH §1)"
      verify: "grep -q 'opentelemetry-logback-mdc-1.0' producer-service/pom.xml && grep -q 'opentelemetry-logback-mdc-1.0' consumer-service/pom.xml"
    - id: BOM-managed
      description: "Both new deps have NO <version> tag (BOM-managed)"
      verify: "awk '/opentelemetry-logback-(appender|mdc)-1\\.0/{flag=1; next} flag && /<\\/dependency>/{flag=0; next} flag && /<version>/{print FILENAME\":\"NR\" UNEXPECTED VERSION TAG\"; exit 1}' producer-service/pom.xml consumer-service/pom.xml"
    - id: dep-convergence
      description: "mvn validate (which binds dependencyConvergence rule) passes from repo root"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -q validate"
    - id: dep-tree-resolves
      description: "mvn dependency:tree resolves both new artifacts at 2.27.0-alpha (proves BOM management)"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service dependency:tree -Dincludes=io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0 | grep -q '2.27.0-alpha' && mvn -B -pl producer-service dependency:tree -Dincludes=io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0 | grep -q '2.27.0-alpha'"
  artifacts:
    - path: producer-service/pom.xml
      provides: "BOM-managed dependency declarations for the two Logback bridge artifacts"
    - path: consumer-service/pom.xml
      provides: "BOM-managed dependency declarations for the two Logback bridge artifacts (byte-identical block to producer)"
  key_links:
    - from: producer-service/pom.xml
      to: parent pom.xml opentelemetry-instrumentation-bom-alpha import (lines 65-77)
      via: BOM-managed version resolution
      pattern: "<groupId>io.opentelemetry.instrumentation</groupId>"
    - from: consumer-service/pom.xml
      to: parent pom.xml opentelemetry-instrumentation-bom-alpha import (lines 65-77)
      via: BOM-managed version resolution
      pattern: "<groupId>io.opentelemetry.instrumentation</groupId>"
---

<objective>
Add the two new BOM-managed Maven dependencies — `opentelemetry-logback-appender-1.0` (the OTLP log-record export appender) and `opentelemetry-logback-mdc-1.0` (the MDC injector wrapper appender) — to BOTH `producer-service/pom.xml` and `consumer-service/pom.xml`. Both artifacts are managed by the `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` already imported in the parent pom.xml (lines 65-77). NO `<version>` tags. The two artifacts establish the precedent for instrumentation-bom-alpha pulls into per-service POMs (zero such pulls existed before Phase 5).

Purpose: Make the Logback bridge classes available on the classpath in both services so Plans 05-02 and 05-03 can import `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender` (for the `@PostConstruct install()` call) and reference `io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender` (in the `logback-spring.xml` MDC wrapper).

Output: Two pom.xml files modified with byte-identical Phase 5 dependency blocks added between the existing OTel SDK runtime block (lines 64-83) and the semconv block (lines 85-114). `mvn validate` clean.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/05-logs-correlation/05-CONTEXT.md
@.planning/phases/05-logs-correlation/05-RESEARCH.md
@.planning/phases/05-logs-correlation/05-PATTERNS.md
@pom.xml
@producer-service/pom.xml
@consumer-service/pom.xml

<interfaces>
<!-- Two new artifacts being added — both BOM-managed at 2.27.0-alpha. -->
<!-- Source: 05-RESEARCH.md Finding #1 (Maven Central POM verification). -->

Artifact 1: io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0
  - Class shipped: io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
  - Job: OTLP export appender. Has `public static void install(OpenTelemetry)` static method.
  - Used by: Plans 05-02 / 05-03 in @PostConstruct install method.

Artifact 2: io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0
  - Class shipped: io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
  - Job: MDC injector wrapper appender. Reads Span.current() and writes trace_id/span_id/trace_flags into MDC.
  - Used by: Plans 05-02 / 05-03 in logback-spring.xml as `MDC_CONSOLE` appender wrapping CONSOLE.
  - VERIFIED non-transitive: opentelemetry-logback-appender-1.0 does NOT transitively pull this artifact (per 05-RESEARCH.md Finding #1, verified against the 2.27.0-alpha POM).

Parent pom.xml import (lines 65-77 — already wired in Phase 1 for forward-compat):
```xml
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>
  <version>${opentelemetry-instrumentation.version}</version>  <!-- 2.27.0-alpha -->
  <type>pom</type>
  <scope>import</scope>
</dependency>
```
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Add Phase 5 logback bridge dependencies to producer-service/pom.xml</name>
  <files>producer-service/pom.xml</files>
  <read_first>
    - producer-service/pom.xml (full file — verify the existing OTel SDK deps block at lines 72-83 and the semconv block at lines 93-114; insertion point is between them)
    - pom.xml (parent — verify lines 65-77 confirm the instrumentation-bom-alpha is already imported with version 2.27.0-alpha resolved from `${opentelemetry-instrumentation.version}` property)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §E — the exact verbatim XML block to paste)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§F — the analog pattern from existing OTel deps block)
    - consumer-service/pom.xml (mirror — Task 2 inserts the IDENTICAL block here for D-02 byte-identical-mirror property)
  </read_first>
  <action>
Insert the following block in `producer-service/pom.xml` BETWEEN the existing `opentelemetry-exporter-otlp` `</dependency>` close tag at line 83 and the comment block opening at line 85 (`<!-- Stable semantic conventions ... -->`). This places the new "OTel logback bridges" block AFTER "OTel SDK runtime" and BEFORE "stable semconv" so the file reads top-to-bottom as: starter-amqp/web/actuator → OTel SDK → OTel logback bridges (NEW) → semconv → starter-test.

The block to paste, BYTE-FOR-BYTE (this is per 05-RESEARCH.md §Code Excerpts §E):

```xml

    <!--
      Phase 5: OTel Logback bridges (BOM-managed by
      opentelemetry-instrumentation-bom-alpha:2.27.0-alpha imported in parent pom.xml
      lines 65-77). These are the FIRST per-service artifacts pulled from the
      instrumentation BOM in v1 — Phase 1 declared the BOM forward-compat for this
      exact moment.

      Both sit in the same `instrumentation.logback` namespace but in DIFFERENT
      packages and serve DIFFERENT jobs — both are required, neither pulls the
      other transitively (verified against 2.27.0-alpha POM per 05-RESEARCH.md
      Finding #1):
        - opentelemetry-logback-appender-1.0 → OTLP EXPORT appender (sends log
          records to the SDK's SdkLoggerProvider). FQCN:
          io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
          Has the static `install(OpenTelemetry)` method called from
          OtelSdkConfiguration's @PostConstruct (LOG-03 / D-08).
        - opentelemetry-logback-mdc-1.0      → MDC INJECTOR wrapper appender
          (reads Span.current() and stamps trace_id/span_id into MDC for the
          console pattern). FQCN:
          io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
          Wired in logback-spring.xml as the MDC_CONSOLE appender wrapping
          CONSOLE (D-13 / D-14 — corrected per 05-RESEARCH.md §1: this is an
          appender wrapper, NOT a TurboFilter as CONTEXT.md D-13 originally said).

      No <version> tags — both BOM-managed.
    -->
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
    </dependency>
```

Indentation: 4 spaces (matches the existing dependency entries in the file at lines 72-83). Leading blank line preserved.

DO NOT:
- Add a `<version>` tag on either dependency (would break BOM-management contract from D-04).
- Add a `<scope>` tag (matches existing OTel deps lines 72-83 which have neither).
- Move the existing `opentelemetry-exporter-otlp` block (verifies LOG-01 dependency story is preserved — exporter ships log exporter from same artifact).
- Add either dependency to the parent pom.xml `<dependencyManagement>` (per-service duplication is the lesson, mirrors D-02 / DOC-05).
- Reorder the existing dependency blocks.
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'opentelemetry-logback-appender-1.0' producer-service/pom.xml && grep -q 'opentelemetry-logback-mdc-1.0' producer-service/pom.xml && mvn -B -pl producer-service -am dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0' 2>&1 | grep -q '2.27.0-alpha' && mvn -B -pl producer-service -am dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0' 2>&1 | grep -q '2.27.0-alpha'</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q 'opentelemetry-logback-appender-1.0' producer-service/pom.xml` returns 0
    - `grep -q 'opentelemetry-logback-mdc-1.0' producer-service/pom.xml` returns 0
    - `awk '/opentelemetry-logback-appender-1.0/{flag=1; next} flag && /<\/dependency>/{exit 0} flag && /<version>/{exit 1}' producer-service/pom.xml` exits 0 (proves NO <version> tag inside the appender dependency block)
    - `awk '/opentelemetry-logback-mdc-1.0/{flag=1; next} flag && /<\/dependency>/{exit 0} flag && /<version>/{exit 1}' producer-service/pom.xml` exits 0 (proves NO <version> tag inside the mdc dependency block)
    - `mvn -B -pl producer-service -am dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0'` output contains the substring `2.27.0-alpha`
    - `mvn -B -pl producer-service -am dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0'` output contains the substring `2.27.0-alpha`
    - The new block appears AFTER line containing `<artifactId>opentelemetry-exporter-otlp</artifactId>` and BEFORE line containing `Stable semantic conventions`
  </acceptance_criteria>
  <done>
producer-service/pom.xml contains the two new dependency entries, BOM-managed (no <version>), positioned between the OTel SDK block and the semconv block. mvn dependency:tree confirms both artifacts resolve to 2.27.0-alpha from the parent's BOM import.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Add the byte-identical block to consumer-service/pom.xml + run mvn validate from repo root</name>
  <files>consumer-service/pom.xml</files>
  <read_first>
    - consumer-service/pom.xml (full file — verify the existing OTel SDK deps block at lines 72-83 and the semconv block at lines 93-114; insertion point is between them — same line numbers as producer)
    - producer-service/pom.xml (post-Task-1 state — copy the new block VERBATIM from there for byte-identical mirror per D-02)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-02 — per-service duplication preserved; the new dependency block must be byte-identical between the two pom files)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (Risk #5 — `dependencyConvergence` rule binds to validate phase; this Task is the smoke gate that catches drift)
  </read_first>
  <action>
Insert the SAME block as in Task 1 into `consumer-service/pom.xml`, BETWEEN the existing `opentelemetry-exporter-otlp` `</dependency>` close tag at line 83 and the comment block opening at line 85 (`<!-- Stable semantic conventions ... -->`).

The block to paste is byte-identical to Task 1's block — copy it from `producer-service/pom.xml` after Task 1 commits, OR re-paste the same XML below for safety:

```xml

    <!--
      Phase 5: OTel Logback bridges (BOM-managed by
      opentelemetry-instrumentation-bom-alpha:2.27.0-alpha imported in parent pom.xml
      lines 65-77). These are the FIRST per-service artifacts pulled from the
      instrumentation BOM in v1 — Phase 1 declared the BOM forward-compat for this
      exact moment.

      Both sit in the same `instrumentation.logback` namespace but in DIFFERENT
      packages and serve DIFFERENT jobs — both are required, neither pulls the
      other transitively (verified against 2.27.0-alpha POM per 05-RESEARCH.md
      Finding #1):
        - opentelemetry-logback-appender-1.0 → OTLP EXPORT appender (sends log
          records to the SDK's SdkLoggerProvider). FQCN:
          io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
          Has the static `install(OpenTelemetry)` method called from
          OtelSdkConfiguration's @PostConstruct (LOG-03 / D-08).
        - opentelemetry-logback-mdc-1.0      → MDC INJECTOR wrapper appender
          (reads Span.current() and stamps trace_id/span_id into MDC for the
          console pattern). FQCN:
          io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
          Wired in logback-spring.xml as the MDC_CONSOLE appender wrapping
          CONSOLE (D-13 / D-14 — corrected per 05-RESEARCH.md §1: this is an
          appender wrapper, NOT a TurboFilter as CONTEXT.md D-13 originally said).

      No <version> tags — both BOM-managed.
    -->
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
    </dependency>
```

Then run `mvn -B validate` from the repo root. The parent pom's `<dependencyConvergence/>` rule (parent pom.xml line 127, bound to validate phase) MUST pass for both services. RESEARCH Risk #5 calls out this is the smoke gate that catches any version drift introduced by the new BOM artifacts. If validate fails:
- Read the convergence error output to find the conflicting artifact
- The expected outcome is convergence is clean (both new artifacts pull `opentelemetry-instrumentation-api:2.27.0` and `opentelemetry-api:1.61.0` — the latter matches the SDK BOM's pin and the former is brand-new transitive dep, no convergence conflict possible)
- If a real convergence error surfaces, surface it via the Bash output — do NOT add `<exclusions>` blocks unless absolutely necessary (would break BOM-management contract)

Verify the two pom files are mirror-identical for the new block via `diff` (output must be empty for the new block).

DO NOT:
- Use `cp` to overwrite consumer-service/pom.xml from producer's; only the new dependency block is mirrored, the rest of the file has consumer-specific descriptions/comments that must remain untouched.
- Run `mvn -B install` or `mvn -B test` here — `mvn validate` is the targeted gate per RESEARCH Risk #5; full build runs in Plan 05-02/05-03 verify steps.
- Add the dependencies in a different position (must mirror Task 1 exactly).
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'opentelemetry-logback-appender-1.0' consumer-service/pom.xml && grep -q 'opentelemetry-logback-mdc-1.0' consumer-service/pom.xml && diff <(awk '/Phase 5: OTel Logback bridges/,/<\/dependency>$/' producer-service/pom.xml | tail -n +1) <(awk '/Phase 5: OTel Logback bridges/,/<\/dependency>$/' consumer-service/pom.xml | tail -n +1) && mvn -B -q validate</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q 'opentelemetry-logback-appender-1.0' consumer-service/pom.xml` returns 0
    - `grep -q 'opentelemetry-logback-mdc-1.0' consumer-service/pom.xml` returns 0
    - `awk '/opentelemetry-logback-appender-1.0/{flag=1; next} flag && /<\/dependency>/{exit 0} flag && /<version>/{exit 1}' consumer-service/pom.xml` exits 0 (NO version tag in appender dep)
    - `awk '/opentelemetry-logback-mdc-1.0/{flag=1; next} flag && /<\/dependency>/{exit 0} flag && /<version>/{exit 1}' consumer-service/pom.xml` exits 0 (NO version tag in mdc dep)
    - `diff` between the producer and consumer "Phase 5: OTel Logback bridges" blocks (the comment + 2 dependency entries) produces empty output (proves byte-identical mirror per D-02)
    - `mvn -B -q validate` from repo root exits 0 (proves dependencyConvergence rule passes — Risk #5 mitigated)
    - `mvn -B -pl consumer-service -am dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0'` output contains `2.27.0-alpha`
    - `mvn -B -pl consumer-service -am dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0'` output contains `2.27.0-alpha`
  </acceptance_criteria>
  <done>
consumer-service/pom.xml has the byte-identical Phase 5 block; `mvn validate` from repo root passes; both new artifacts resolve to 2.27.0-alpha from the parent BOM in both services.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Maven dependency resolution → Local classpath | Adding two new `instrumentation` artifacts shipped from Maven Central; supply-chain risk is BOM-managed (version pinned in parent pom.xml line 74 to `2.27.0-alpha`). The artifacts are signed/published by the OpenTelemetry Java Instrumentation project — same publisher as the existing `opentelemetry-exporter-otlp`. No new trust boundary introduced. |

## STRIDE Threat Register (ASVS L1, security_enforcement: enabled, block-on: high)

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-01-01 | Tampering | Maven dependency resolution | mitigate | BOM-pinned version (`opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` — parent pom.xml line 74). NO floating versions; reproducible builds via Maven's resolution. `mvn validate` (Task 2) runs `dependencyConvergence` rule that catches version drift. |
| T-05-01-02 | Information Disclosure | Logback bridge artifacts | accept | The artifacts ship Logback bridge code; they read MDC and Span context but do NOT read or log secrets/PII directly. No credentials, tokens, or sensitive data are touched at the dependency-add level. The Plan 05-02/05-03 logback-spring.xml is where logging behavior is configured (separate threat-model assessment in those plans). |
| T-05-01-03 | Denial of Service | Maven build pipeline | accept | Adding 2 BOM-managed artifacts adds no significant build-time or runtime overhead; transitive deps already overlap with classpath (verified by RESEARCH Finding #7). `mvn validate` runs in <30s in this project (Phase 4 baseline). |
| T-05-01-04 | Elevation of Privilege | Maven plugin execution | accept | No new Maven plugins added; existing `maven-enforcer-plugin` (parent pom.xml line 113) bound to validate phase is unchanged. New deps are runtime-scope library code, not build plugins. |
</threat_model>

<verification>
- `grep -q 'opentelemetry-logback-appender-1.0' producer-service/pom.xml consumer-service/pom.xml` (returns 0 for BOTH files)
- `grep -q 'opentelemetry-logback-mdc-1.0' producer-service/pom.xml consumer-service/pom.xml` (returns 0 for BOTH files)
- Both new dep blocks have NO `<version>` tag (BOM-managed contract from D-04)
- New blocks appear AFTER the OTel SDK runtime block and BEFORE the semconv block in both pom files
- `mvn -B -q validate` from repo root exits 0 (dependencyConvergence rule passes)
- `mvn -B dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0'` resolves to `2.27.0-alpha` for both `producer-service` and `consumer-service`
- `mvn -B dependency:tree -Dincludes='io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0'` resolves to `2.27.0-alpha` for both `producer-service` and `consumer-service`
- The Phase 5 dependency block in producer-service/pom.xml is byte-identical to the block in consumer-service/pom.xml (proves D-02 mirror property)
</verification>

<success_criteria>
1. Both pom.xml files contain the two new dependency declarations (both grep checks pass).
2. Neither dependency has a `<version>` tag (BOM-management preserved).
3. The two pom files contain byte-identical Phase 5 dependency blocks (D-02 / DOC-05 mirror property).
4. `mvn validate` from repo root exits 0 (dependencyConvergence rule clean).
5. `mvn dependency:tree` resolves both artifacts to `2.27.0-alpha` in both services.
6. No `<exclusions>` were needed (clean BOM resolution).
</success_criteria>

<output>
After completion, create `.planning/phases/05-logs-correlation/05-01-SUMMARY.md` with:
- Files modified (producer-service/pom.xml, consumer-service/pom.xml)
- The exact location (line range) of the new block in each file
- `mvn dependency:tree` output excerpt confirming `2.27.0-alpha` resolution for both artifacts
- `mvn validate` exit code (must be 0)
- Confirmation that no `<version>` or `<exclusions>` tags were added
- Forward-link: Plans 05-02 and 05-03 will import the classes shipped by these artifacts
</output>
