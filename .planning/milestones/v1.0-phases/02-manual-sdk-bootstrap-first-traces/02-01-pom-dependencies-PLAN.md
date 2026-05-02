---
id: 02-01-pom-dependencies
phase: 02-manual-sdk-bootstrap-first-traces
plan: 01
type: execute
wave: 1
depends_on: []
requirements: [TRACE-01]
files_modified:
  - producer-service/pom.xml
  - consumer-service/pom.xml
  - mise.toml
autonomous: true
must_haves:
  truths:
    - "producer-service/pom.xml and consumer-service/pom.xml each declare exactly 5 new io.opentelemetry* dependencies (api, sdk, exporter-otlp, semconv stable, semconv-incubating) with no <version> tags on the BOM-managed three"
    - "mvn validate succeeds across all modules — maven-enforcer-plugin's dependencyConvergence rule passes (no duplicate OTel artifact versions)"
    - "mvn dependency:tree -Dincludes=io.opentelemetry shows opentelemetry-api, -sdk, -exporter-otlp, -semconv (1.40.0), and -semconv-incubating (1.40.0-alpha) on EXACTLY ONE line each per service module — no duplicates, no version conflicts (Phase 2 INVARIANT, inverts the Phase 1 zero-libs gate)"
    - "mise run verify:bom is rewritten to assert the Phase 2 invariant (one version per OTel artifact) and exits 0 with 'Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.'"
    - "Forbidden artifacts ABSENT: opentelemetry-sdk-extension-autoconfigure (D-12), opentelemetry-logback-appender-1.0 (Phase 5), legacy io.opentelemetry:opentelemetry-semconv (D-13)"
    - "mvn -DskipTests install exits 0 from repo root — both service JARs build cleanly"
  artifacts:
    - path: "producer-service/pom.xml"
      provides: "Producer module with OTel SDK + semconv dependencies (BOM-managed for the SDK three; pinned 1.40.0 / 1.40.0-alpha for the two semconv coords)"
      contains: "<artifactId>opentelemetry-sdk</artifactId>"
    - path: "consumer-service/pom.xml"
      provides: "Consumer module with the IDENTICAL 5 OTel deps (per-service-duplication ethos extends to the POM)"
      contains: "<artifactId>opentelemetry-semconv-incubating</artifactId>"
    - path: "mise.toml"
      provides: "Inverted verify:bom task asserting one-version-per-OTel-artifact (was: zero OTel libs)"
      contains: "Phase 2 baseline confirmed"
  key_links:
    - from: "producer-service/pom.xml + consumer-service/pom.xml"
      to: "parent pom.xml <dependencyManagement>"
      via: "BOM-managed versions for opentelemetry-api, -sdk, -exporter-otlp (no <version> tag in service POMs)"
      pattern: "io.opentelemetry"
    - from: "mise.toml verify:bom task"
      to: "Phase 2 invariant"
      via: "mvn dependency:tree -Dincludes=io.opentelemetry; awk-grouped count of duplicate (artifactId, version) pairs"
      pattern: "Phase 2 baseline confirmed"
---

<objective>
Add the OpenTelemetry SDK + semconv (stable + incubating) Maven dependencies to BOTH `producer-service/pom.xml` and `consumer-service/pom.xml`, and invert `mise.toml`'s `verify:bom` task to assert the Phase 2 invariant (ONE version per `io.opentelemetry*` artifact, no duplicates) instead of Phase 1's "zero OTel libs" gate. Three POM-level files in a single wave because Maven's `dependencyConvergence` enforcer runs at the `validate` phase across the reactor — partial changes break the build for everyone.

Purpose: TRACE-01 foundation. Phase 2's `OtelSdkConfiguration.java` (Plans 02 + 03) cannot compile until the SDK and semconv constants are on the classpath; the per-service-duplication ethos (DOC-05) extends to the POMs (the same 5 deps appear identically in both service POMs).
Output: 3 modified files; `mvn -DskipTests install` succeeds; `mise run verify:bom` exits 0 against the new Phase 2 invariant.
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
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md
@.planning/phases/01-baseline-scaffold/1-01-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="02-01-T1" type="auto">
  <name>Task 1: Add 5 OTel dependencies to producer-service/pom.xml + consumer-service/pom.xml (identical block, per-service-duplication)</name>
  <files>producer-service/pom.xml, consumer-service/pom.xml</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-PATTERNS.md (lines 161-218 — POM Modifications section: current dependencies block, additions, and forbidden artifacts list)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 128-141 — "Two coordinates required" XML snippet for semconv stable + incubating)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 9-12 — Executive Summary: incubating semconv MANDATORY, not optional, because MessagingIncubatingAttributes + DeploymentIncubatingAttributes are required)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 217-220 — Open Q #2: incubating semconv -alpha is MANDATORY, document the rationale in a POM XML comment)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md (lines 175-178 — D-12 forbids opentelemetry-sdk-extension-autoconfigure; lines 222-224 — also forbid legacy io.opentelemetry:opentelemetry-semconv and Phase-5-only logback appender)
    - producer-service/pom.xml (current Phase 1 state — Spring Boot starters only, has the "NO OpenTelemetry deps in Phase 1" XML comment at lines 21-25 that MUST be replaced)
    - consumer-service/pom.xml (current Phase 1 state — same shape; same XML comment at lines 21-29 must be replaced)
    - pom.xml (parent — confirms the three BOMs in <dependencyManagement> at lines 50-92 already manage opentelemetry-api / -sdk / -exporter-otlp via opentelemetry-bom:1.61.0; semconv coords are NOT BOM-managed and need <version> tags)
  </read_first>
  <action>
    Modify both `producer-service/pom.xml` and `consumer-service/pom.xml`. The `<dependencies>` block in each file must end up structurally IDENTICAL with respect to the OTel additions (per-service-duplication ethos — Plans 02 + 03 will mirror this in the Java SDK config files). Apply the same change to BOTH files in this single task; do not split.

    **Step 1 — Replace the Phase 1 invariant XML comment (lines 21-25 in producer-service/pom.xml; lines 21-29 in consumer-service/pom.xml):**

    Old comment to delete:
    ```xml
    <!--
      Spring Boot starters only. NO OpenTelemetry deps in Phase 1 —
      that is the Phase 1 invariant verified by Task 3 of this plan
      (BOM gate: zero matches in dependency:tree across all modules).
      ...
    -->
    ```

    New comment to insert in its place (Phase 2 invariant — per RESEARCH §"Wave 0 Gaps" + Open Q #3):
    ```xml
    <!--
      Phase 2 onward: ONE version per OpenTelemetry artifact across the reactor,
      enforced by maven-enforcer-plugin's <dependencyConvergence/> rule
      (parent pom.xml lines 113-145, bound to the validate phase).

      The first three OTel deps below are BOM-managed by opentelemetry-bom:1.61.0
      (parent pom.xml lines 57-63) — no <version> tags here. The two semconv
      coords are NOT BOM-managed and pin 1.40.0 (stable) / 1.40.0-alpha
      (incubating) directly per .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md §B.

      Per-service duplication of these dependencies is intentional (DOC-05): the
      consumer-service/pom.xml carries the IDENTICAL block. Refactoring this
      into parent <dependencyManagement> would hide one of the two readings
      that Phase 2's `OtelSdkConfiguration.java` is built around.
    -->
    ```

    **Step 2 — Insert the 5 new `<dependency>` blocks AFTER the existing spring-boot-starter-actuator dependency and BEFORE the spring-boot-starter-test dependency** in BOTH POMs. Use this EXACT XML (verbatim, character-for-character, in BOTH files):

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

    **Step 3 — Verify forbidden artifacts are NOT present:**

    Do NOT add (per D-12 + D-13 + Phase-5 scope):
    - `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure` — D-12 forbids; the OtelSdkConfiguration uses `System.getenv` directly
    - `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` — Phase 5 only
    - `io.opentelemetry:opentelemetry-semconv` (LEGACY coordinate, in the SDK BOM with no `.semconv.` group prefix) — D-13 forbids; deprecated in favor of the standalone `io.opentelemetry.semconv:*` coords above

    **Step 4 — Update the `<description>` element in BOTH POMs** (currently says "Phase 1: Spring Boot starters only — no OTel deps yet"):
    - producer-service/pom.xml: change to `Producer service: HTTP POST /orders → publish OrderCreated to RabbitMQ. Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-02).`
    - consumer-service/pom.xml: change to `Consumer service: @RabbitListener processes OrderCreated. Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-03).`

    **Step 5 — Verify the change builds:**
    - `xmllint --noout producer-service/pom.xml consumer-service/pom.xml` exits 0
    - `mvn -DskipTests -q install` exits 0 (this triggers maven-enforcer-plugin's `dependencyConvergence` at the validate phase across the entire reactor)

    No other changes to either POM. Do NOT touch parent `pom.xml` (BOMs are already correct per Phase 1 — verified in 1-01-SUMMARY.md).
  </action>
  <acceptance_criteria>
    - `xmllint --noout producer-service/pom.xml consumer-service/pom.xml` exits 0
    - `grep -c '<artifactId>opentelemetry-api</artifactId>' producer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-api</artifactId>' consumer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-sdk</artifactId>' producer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-sdk</artifactId>' consumer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-exporter-otlp</artifactId>' producer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-exporter-otlp</artifactId>' consumer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-semconv</artifactId>' producer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-semconv</artifactId>' consumer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-semconv-incubating</artifactId>' producer-service/pom.xml` returns 1
    - `grep -c '<artifactId>opentelemetry-semconv-incubating</artifactId>' consumer-service/pom.xml` returns 1
    - Stable semconv pinned to 1.40.0: `grep -A1 '<artifactId>opentelemetry-semconv</artifactId>' producer-service/pom.xml | grep -c '<version>1.40.0</version>'` returns 1 (and same for consumer)
    - Incubating semconv pinned to 1.40.0-alpha: `grep -A1 '<artifactId>opentelemetry-semconv-incubating</artifactId>' producer-service/pom.xml | grep -c '<version>1.40.0-alpha</version>'` returns 1 (and same for consumer)
    - SDK three deps DO NOT have <version> tags (BOM-managed): `awk '/<artifactId>opentelemetry-(api|sdk|exporter-otlp)</{p=NR} /<version>/{if(NR-p<=2) print "BAD: version on BOM-managed artifact at line " NR; p=0}' producer-service/pom.xml | grep -c BAD` returns 0 (and same for consumer)
    - Forbidden artifacts ABSENT: `! grep -E '(opentelemetry-sdk-extension-autoconfigure|opentelemetry-logback-appender)' producer-service/pom.xml consumer-service/pom.xml` exits 0
    - Legacy semconv coord ABSENT: `! grep -E '<groupId>io\.opentelemetry</groupId>\s*<artifactId>opentelemetry-semconv</artifactId>' producer-service/pom.xml consumer-service/pom.xml` exits 0 (the new coord uses io.opentelemetry.semconv groupId)
    - Phase 2 description text present: `grep -c 'Phase 2: manual OTel SDK' producer-service/pom.xml consumer-service/pom.xml | awk -F: '{s+=$2} END{exit (s==2)?0:1}'` exits 0
    - The Phase 1 "NO OpenTelemetry deps in Phase 1" XML comment is REMOVED: `! grep -F 'NO OpenTelemetry deps in Phase 1' producer-service/pom.xml consumer-service/pom.xml` exits 0
    - `mvn -DskipTests -q install` exits 0 (full reactor build with dependencyConvergence enforcer firing at validate)
    - dependencyConvergence proves one-version-per-artifact: `mvn -pl producer-service -q dependency:tree -Dincludes=io.opentelemetry 2>&1 | grep -vE '^\[INFO\] (Scanning|Reactor|Build|---|BUILD|Total time|Finished at|----)' | grep -oE 'io\.opentelemetry[a-z.-]*:[a-z-]+:(jar|pom):[0-9a-z.-]+' | sort -u | awk -F: '{key=$1":"$2":"$3; if(seen[key]++) print "DUP: "$0}' | grep -c DUP | awk '{exit ($1==0)?0:1}'` exits 0 (no duplicate artifacts at differing versions)
  </acceptance_criteria>
  <verify>
    <automated>xmllint --noout producer-service/pom.xml consumer-service/pom.xml &amp;&amp; mvn -DskipTests -q install &amp;&amp; grep -q '<artifactId>opentelemetry-semconv-incubating</artifactId>' producer-service/pom.xml &amp;&amp; grep -q '<artifactId>opentelemetry-semconv-incubating</artifactId>' consumer-service/pom.xml &amp;&amp; ! grep -E 'opentelemetry-sdk-extension-autoconfigure|opentelemetry-logback-appender' producer-service/pom.xml consumer-service/pom.xml</automated>
  </verify>
  <done>Both producer-service/pom.xml and consumer-service/pom.xml carry the identical 5-dependency OTel block (api, sdk, exporter-otlp BOM-managed; semconv 1.40.0 + semconv-incubating 1.40.0-alpha pinned); Phase 1 invariant XML comment replaced with Phase 2 invariant text; `mvn -DskipTests install` exits 0 across the reactor; no forbidden artifacts present.</done>
</task>

<task id="02-01-T2" type="auto">
  <name>Task 2: Invert mise.toml verify:bom task — assert one-version-per-OTel-artifact (Phase 2 invariant)</name>
  <files>mise.toml</files>
  <read_first>
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 196-204 — Validation Architecture: CI-verifiable invariants and "Wave 0 Gaps" recommending the verify:bom inversion)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-RESEARCH.md (lines 221-223 — Open Q #3: low severity, tooling-only; the maven-enforcer rule remains the load-bearing check)
    - mise.toml (lines 121-132 — current verify:bom task asserting ZERO OTel libs; this is the Phase 1 success gate that becomes a Phase 2 failure gate after T1 lands)
    - producer-service/pom.xml + consumer-service/pom.xml (just modified in T1 — five OTel deps each)
  </read_first>
  <action>
    Replace the existing `[tasks."verify:bom"]` block (mise.toml lines 121-132) with a new implementation that asserts the Phase 2 invariant: every `io.opentelemetry*` artifact appears at exactly ONE version across the entire Maven reactor.

    Delete this block:
    ```toml
    [tasks."verify:bom"]
    description = "Phase 1 success gate: zero OTel libs on the classpath"
    run = """
    set -e
    COUNT=$(mvn -q dependency:tree -Dincludes=io.opentelemetry 2>&1 | grep -c "io.opentelemetry" || true)
    if [ "$COUNT" -gt 0 ]; then
      echo "ERROR: OpenTelemetry libraries detected on classpath:"
      mvn dependency:tree -Dincludes=io.opentelemetry
      exit 1
    fi
    echo "Phase 1 baseline confirmed: zero OpenTelemetry libraries on classpath."
    """
    ```

    Insert this replacement (same task name `verify:bom`, inverted semantics — the task name keeps its meaning of "verify the BOM-managed dependency state is correct"):
    ```toml
    [tasks."verify:bom"]
    description = "Phase 2 invariant: one version per io.opentelemetry* artifact across the reactor"
    run = """
    set -e

    # Run dependency:tree across the full reactor, filter to OTel artifacts only,
    # extract groupId:artifactId:type (NOT version) per occurrence, then look for
    # any artifact appearing under MULTIPLE distinct versions across modules.
    #
    # Layout of the relevant dependency:tree lines:
    #   [INFO] +- io.opentelemetry:opentelemetry-api:jar:1.61.0:compile
    #   [INFO] |  \\- io.opentelemetry:opentelemetry-context:jar:1.61.0:compile
    #
    # We split on ':' to get groupId, artifactId, type, version.
    OUTPUT=$(mvn -q dependency:tree -Dincludes=io.opentelemetry 2>&1)
    VIOLATIONS=$(printf '%s\\n' "$OUTPUT" \\
      | grep -oE 'io\\.opentelemetry[a-z.-]*:[a-z0-9-]+:(jar|pom):[0-9a-zA-Z.-]+' \\
      | sort -u \\
      | awk -F: '{ key=$1":"$2":"$3; ver=$4; if (seen[key] && seen[key] != ver) print key" appears at versions "seen[key]" AND "ver; seen[key]=ver }')

    if [ -n "$VIOLATIONS" ]; then
      echo "ERROR: Phase 2 invariant violated — the following OTel artifacts appear at multiple versions across the reactor:"
      printf '%s\\n' "$VIOLATIONS"
      echo
      echo "Full dependency:tree output for diagnosis:"
      printf '%s\\n' "$OUTPUT"
      exit 1
    fi

    # Also confirm we're not in the (impossible-after-Plan-02-01) state of zero
    # OTel libs — a freshly cloned repo at step-02-traces MUST have artifacts.
    COUNT=$(printf '%s\\n' "$OUTPUT" | grep -cE 'io\\.opentelemetry[a-z.-]*:[a-z0-9-]+:(jar|pom):' || true)
    if [ "$COUNT" -eq 0 ]; then
      echo "ERROR: zero OpenTelemetry artifacts on the classpath — Phase 2 expects the SDK to be present."
      echo "If you want the Phase 1 baseline state, check out tag step-01-baseline."
      exit 1
    fi

    echo "Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules."
    """
    ```

    No other changes to mise.toml. Do NOT remove or modify any other task (preflight, infra:up/down, dev, demo:order, ui:grafana, ui:rabbitmq).
  </action>
  <acceptance_criteria>
    - `test -f mise.toml` exits 0
    - `grep -c '\[tasks."verify:bom"\]' mise.toml` returns 1 (single declaration, not duplicated)
    - `grep -c 'Phase 1 success gate' mise.toml` returns 0 (old description removed)
    - `grep -c 'Phase 2 invariant' mise.toml` returns 1 (new description present)
    - `grep -c 'Phase 2 baseline confirmed' mise.toml` returns 1 (new success message present)
    - `grep -c 'Phase 1 baseline confirmed' mise.toml` returns 0 (old success message removed)
    - `mise run verify:bom 2>&1 | tail -1 | grep -q 'Phase 2 baseline confirmed'` exits 0 (the new task RUNS to green against the post-T1 reactor)
    - `mise run verify:bom` exits 0
    - Other mise tasks NOT broken: `mise tasks 2>&1 | grep -cE '^(preflight|infra:up|infra:down|infra:reset|infra:logs|build|test|dev|dev:producer|dev:consumer|demo:order|verify:bom|ui:grafana|ui:rabbitmq)\\s' | awk '{exit ($1==14)?0:1}'` exits 0 (all 14 task names still present)
  </acceptance_criteria>
  <verify>
    <automated>mise run verify:bom 2>&amp;1 | tail -1 | grep -q 'Phase 2 baseline confirmed' &amp;&amp; ! grep -F 'Phase 1 baseline confirmed' mise.toml</automated>
  </verify>
  <done>mise.toml's verify:bom task is rewritten in place: same task name, inverted semantics; asserts one version per OTel artifact across the reactor and exits 0 with "Phase 2 baseline confirmed" against the post-T1 dependency tree. All other mise tasks untouched.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 02-01 — POM + tooling only)

| Boundary | Description |
|----------|-------------|
| External Maven Central → local repo | First-run downloads of opentelemetry-api/sdk/exporter-otlp/semconv (+ -incubating) JARs and POMs |
| Local Maven repo → build classpath | All deps come from `~/.m2`; no curl/script-side fetching |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2-01-01 | Tampering | Maven dependency confusion (typo'd group ID pulls a malicious package masquerading as `io.opentelemetry.semconv`) | mitigate | Both new semconv coords explicitly use the `io.opentelemetry.semconv` group (NOT `io.opentelemetry`); BOM-managed deps inherit pinned versions from opentelemetry-bom:1.61.0 declared in parent pom.xml; dependencyConvergence enforcer fails the build on any version conflict (verified by T1 acceptance + T2's verify:bom output) |
| T-2-01-02 | Tampering | Floating version tags introducing supply-chain drift | mitigate | All five new dependencies pin exact versions (BOM-managed for the SDK three; literal `1.40.0` / `1.40.0-alpha` for the two semconv coords); no `LATEST`, no version ranges; matches the Phase 1 pinning pattern |
| T-2-01-03 | Tampering | Legacy `io.opentelemetry:opentelemetry-semconv` (deprecated, still published) silently shadowing the new coord and hiding the deprecation | mitigate | Acceptance criteria explicitly assert ABSENCE of the legacy `<groupId>io.opentelemetry</groupId><artifactId>opentelemetry-semconv</artifactId>` declaration; D-13 + RESEARCH §"Sources" call out the deprecation |
| T-2-01-04 | Information Disclosure | POM XML comments leaking internal infrastructure details | accept | All comments are workshop pedagogy (BOM ordering, per-service-duplication rationale, Phase-2-vs-Phase-5 hints); no secrets, no internal hosts, no credentials |
| T-2-01-05 | Spoofing / Tampering | Untrusted Maven plugin loaded from external repository | accept | No new `<pluginRepositories>` declared; no new plugins added in this plan; the existing `maven-enforcer-plugin` (parent pom.xml) is the only enforcement surface and was vetted in 1-01 |

**Phase 2 plan-01 scope:** POM dependency additions + one mise task rewrite. No runtime code, no network endpoints, no secrets. All risk lives in the Maven supply chain — mitigated by BOM-managed pinning + dependencyConvergence enforcer + explicit semconv coord pinning. Phase-level threats T1 (env-var endpoint hijack) and T3 (actuator filter exclusion) do NOT apply at this layer; they are introduced by Plans 02 + 03 + 04 + 05 and tracked there.
</threat_model>

<verification>
- `xmllint --noout producer-service/pom.xml consumer-service/pom.xml` exits 0
- `mvn -DskipTests install` exits 0 from repo root
- `mvn dependency:tree -pl producer-service -Dincludes=io.opentelemetry` shows `opentelemetry-api`, `opentelemetry-sdk`, `opentelemetry-exporter-otlp`, `opentelemetry-context` (transitive) at version 1.61.0; `opentelemetry-semconv` at 1.40.0; `opentelemetry-semconv-incubating` at 1.40.0-alpha. Same shape for `-pl consumer-service`.
- `mise run verify:bom` exits 0 with `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`
- No legacy `io.opentelemetry:opentelemetry-semconv` (the deprecated coord), no `opentelemetry-sdk-extension-autoconfigure`, no `opentelemetry-logback-appender-1.0` in any module.
</verification>

<success_criteria>
- TRACE-01 foundation laid: both service POMs carry the SDK + semconv coords needed for Plans 02 + 03's `OtelSdkConfiguration.java`.
- Per-service duplication ethos (DOC-05) extends to the POMs: producer-service/pom.xml and consumer-service/pom.xml carry the IDENTICAL 5-dependency block.
- Maven enforcer's `dependencyConvergence` rule continues to fire green on every `mvn install` (Phase 1 invariant preserved across the BOM-import boundary).
- `mise run verify:bom` task is now Phase-2-aware: asserts one-version-per-artifact (the actual Phase 2 invariant) instead of the impossible-after-Plan-02-01 zero-libs assertion.
- Plans 02 + 03 (Wave 2) can now compile against `io.opentelemetry.api.*`, `io.opentelemetry.sdk.*`, `io.opentelemetry.exporter.otlp.trace.*`, and the two `io.opentelemetry.semconv.*` packages.
</success_criteria>

<output>
After completion, create `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-01-SUMMARY.md` documenting:
- Final OTel dependency block in producer-service/pom.xml (paste the 5 `<dependency>` entries with comments)
- Confirmation that consumer-service/pom.xml carries the IDENTICAL block (per-service-duplication, paste a diff or the relevant block)
- Confirmed `mvn -DskipTests install` BUILD SUCCESS (paste the BUILD SUCCESS line)
- Confirmed `mvn dependency:tree -pl producer-service -Dincludes=io.opentelemetry` shows: opentelemetry-api / -sdk / -exporter-otlp at 1.61.0; opentelemetry-context (transitive) at 1.61.0; opentelemetry-semconv at 1.40.0; opentelemetry-semconv-incubating at 1.40.0-alpha (paste the relevant lines)
- Confirmed mise run verify:bom output: `Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules.`
- Files modified: 2 POMs + 1 mise.toml
- Phase 2 invariant established for the rest of the wave
</output>
