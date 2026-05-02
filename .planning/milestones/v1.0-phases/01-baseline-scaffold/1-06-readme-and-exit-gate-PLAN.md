---
id: 1-06-readme-and-exit-gate
phase: 01-baseline-scaffold
plan: 06
type: execute
wave: 3
depends_on: [1-01-maven-skeleton, 1-02-mise-toolchain, 1-03-docker-compose, 1-04-producer-service, 1-05-consumer-service]
requirements: [DOC-02, WORK-01]
files_modified:
  - README.md
  - .gitignore
autonomous: false
must_haves:
  truths:
    - "README.md exists at the repo root with a '## Prerequisites' section listing required tools (mise, Docker, Git), required free ports (3000, 4317, 4318, 5672, 15672, 8080, 8081), auto-installed mise tools (Corretto 17.0.13.11.1 + Maven 3.9.11), IDE setup hints, and the 'mise run preflight' command (DOC-02)"
    - "Annotated git tag 'step-01-baseline' exists on main and points at a commit where ALL FIVE phase-1 success criteria are simultaneously true (WORK-01)"
    - "Phase 1 success criterion #1 (preflight green) verified: mise run preflight exits 0 with 'Pre-flight: ALL GREEN'"
    - "Phase 1 success criterion #2 (apps work end-to-end) verified: with infra:up + dev running, mise run demo:order returns 202 and the consumer logs an OrderCreated receipt"
    - "Phase 1 success criterion #3 (zero OTel libs) verified: mise run verify:bom exits 0 with 'Phase 1 baseline confirmed: zero OpenTelemetry libraries on classpath.'"
    - "Phase 1 success criterion #4 (tag reproduces baseline) verified: git checkout step-01-baseline produces the same green-baseline state"
    - "Phase 1 success criterion #5 (README Prerequisites self-diagnosis) verified: a first-time attendee can read README, install mise + Docker, and run preflight to surface tooling issues before opening any code"
    - "Repository is committed and pushed: working tree is clean (git status reports nothing to commit) at the moment the tag is applied; the tag is annotated (-a flag), not lightweight"
  artifacts:
    - path: "README.md"
      provides: "Workshop-grade Prerequisites section keyed to DOC-02 (skeleton; remainder of README walkthrough lands in Phase 7 / DOC-01)"
      contains: "## Prerequisites"
    - path: ".gitignore"
      provides: "Standard ignores for Maven (target/), mise (.mise/), IDE state (.idea/, .vscode/, *.iml), OS junk (.DS_Store)"
      contains: "target/"
    - path: "(git ref) refs/tags/step-01-baseline"
      provides: "Immutable annotated workshop checkpoint marking Phase 1 exit; first of the six WORK-01 tags"
      contains: "(annotated tag message)"
  key_links:
    - from: "README.md '## Prerequisites'"
      to: "mise.toml, docker-compose.yml, .tool-versions"
      via: "Documented file references and 'mise run preflight' command"
      pattern: "mise run preflight"
    - from: "git tag step-01-baseline"
      to: "Working two-service Spring Boot + RabbitMQ baseline"
      via: "Commit pointed at by the tag reproduces preflight-green + dev-green + verify:bom-green"
      pattern: "step-01-baseline"
---

<objective>
Land the Phase 1 documentation gate (DOC-02 — README "Prerequisites" section that lets a first-time attendee self-diagnose tooling issues before touching code) and the Phase 1 exit gate (WORK-01 — annotated git tag `step-01-baseline` on main, the FIRST of the six workshop checkpoints whose convention this plan establishes). The tag is created ONLY after running and confirming all five Phase 1 success criteria simultaneously: preflight green, dev/demo:order green, verify:bom green (zero OTel libs), and the tag itself reproduces this state on `git checkout`.

Purpose: Documentation + git-workflow finalisation that locks in the baseline. The `step-01-baseline` tag is the workshop attendee's "time-machine starting point"; everything subsequent (Phase 2's tags through Phase 6's) is a delta from here.
Output: 1 README.md + 1 .gitignore + 1 git tag + a clean commit history pushed to the working branch.
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
@.planning/phases/01-baseline-scaffold/1-RESEARCH.md
@.planning/phases/01-baseline-scaffold/1-01-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-02-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-03-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-04-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-05-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="1-06-T1" type="auto">
  <name>Task 1: Write README.md (Prerequisites section + workshop preamble) and .gitignore</name>
  <files>README.md, .gitignore</files>
  <read_first>
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 1107-1182 — README Prerequisites skeleton, verbatim)
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 326-334 — Pitfall B context for the port table)
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 336-345 — Pitfall C context for the IDE setup section)
    - .planning/PROJECT.md (lines 1-10 — "What This Is" + Core Value, useful for the README's intro)
    - mise.toml + .tool-versions + docker-compose.yml (created in earlier plans — README references these by name)
    - .planning/REQUIREMENTS.md (DOC-01 is Phase 7's full README walkthrough — this plan delivers the DOC-02 skeleton ONLY; DO NOT write step-01..step-06 walkthroughs here)
  </read_first>
  <action>
    Create two files at the repo root.

    **`README.md`** — composed of these sections in this order:

    1. **`# OSE OTel Demo`** — H1 with the project name.

    2. **One-paragraph intro** — adapt PROJECT.md's "What This Is" + "Core Value" into 3-5 sentences explaining: a workshop demo teaching manual OpenTelemetry SDK instrumentation in Spring Boot 3.4.13 / Java 17, covering HTTP and AMQP service-to-service shapes, with Grafana otel-lgtm as the backend. State that the workshop progresses through six annotated git tags (`step-01-baseline` through `step-06-tests`) and that this `main` branch as of `step-01-baseline` shows the **uninstrumented baseline** (zero OTel libs).

    3. **`## Prerequisites`** — verbatim content from RESEARCH.md lines 1107-1182. The exact subsections are:

       a. Lead paragraph: "You will run two Spring Boot apps on your laptop's JVM and two infrastructure containers (RabbitMQ + grafana/otel-lgtm) via Docker. Before starting, verify your environment with `mise run preflight`."

       b. **`### Required tools`** — markdown table with columns Tool / Version / Install. Three rows:
          - mise — `≥ 2025.1.0` — `curl https://mise.run | sh`
          - Docker Engine + Compose v2 — `≥ 24.0` — link to https://docs.docker.com/engine/install/
          - Git — `≥ 2.30` — `brew install git` / `apt install git` / `pacman -S git`

       c. Sentence: "mise will install the right JDK and Maven for you on first `mise install`:" followed by a markdown table with Auto-installed via mise / Version columns. Two rows:
          - Amazon Corretto JDK — `17.0.13.11.1`
          - Apache Maven — `3.9.11`

       d. **`### Required free ports`** — markdown table with Port / Service / Why columns. Seven rows EXACTLY as RESEARCH.md lines 1132-1141:
          - 3000 — Grafana UI — Common collision (React/Next.js dev servers)
          - 4317 — OTLP gRPC ingest — Used from Phase 2 onwards
          - 4318 — OTLP HTTP ingest — Reserved for HTTP-fallback variant
          - 5672 — RabbitMQ AMQP — Standard AMQP port
          - 15672 — RabbitMQ Management UI — Standard management port
          - 8080 — producer-service HTTP — Spring Boot default
          - 8081 — consumer-service HTTP — `/actuator/health` only

          End the section with: "If a port is in use, `mise run preflight` will tell you which one and suggest `lsof -i:<port>` to identify the conflicting process."

       e. **`### IDE setup`** — covers Pitfall C. Two paragraphs:
          - "If you use **IntelliJ IDEA**: install the [Mise plugin](https://plugins.jetbrains.com/plugin/24009-mise) OR ensure IntelliJ's 'Project SDK' points at `~/.local/share/mise/installs/java/corretto-17.0.13.11.1`. The committed `.tool-versions` file enables IntelliJ's built-in auto-detection as a fallback."
          - "If you use **VS Code**: install the [Mise extension](https://marketplace.visualstudio.com/items?itemName=hverlin.mise-vscode)."

       f. **`### One-time setup`** — fenced shell block with three commands:
          ```
          git clone <this repo>
          cd ose-otel-demo
          mise install        # installs Corretto 17 + Maven 3.9.11
          mise run preflight  # validates everything before you start
          ```

       g. **`### First run`** — fenced shell block:
          ```
          mise run infra:up   # starts RabbitMQ + grafana/otel-lgtm
          mise run dev        # starts producer + consumer in parallel
          # in a second terminal:
          mise run demo:order # POSTs a sample order; expect 202
          ```
          Plus a paragraph: "You should see the consumer log a line like: `OrderCreated received: orderId=<uuid>`."

       h. Closing paragraph (RESEARCH.md lines 1175-1182): "In Phase 1 there is **no telemetry** — the OTLP endpoint is open and the Grafana stack is running, but the apps emit nothing. This is intentional: Phase 2 introduces the OpenTelemetry SDK and traces start flowing. To verify the baseline: `mise run verify:bom` should report zero OpenTelemetry libraries on the classpath."

    4. **`## Workshop checkpoints`** — short section listing the six annotated tags as bullet points (only the Phase-1 tag exists today; the others are placeholders the README will fill in as later phases ship). Format:
       - `step-01-baseline` — Working two-service Spring Boot + RabbitMQ app on host JVM with ZERO telemetry. **Current.**
       - `step-02-traces` — (Phase 2) Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces.
       - `step-03-context-propagation` — (Phase 3) THE headline lesson: AMQP context propagation joins the two traces.
       - `step-04-metrics` — (Phase 4) SdkMeterProvider + Counter/Histogram/ObservableGauge.
       - `step-05-logs` — (Phase 5) Logs correlation + Loki-to-Tempo click-through.
       - `step-06-tests` — (Phase 6) Testcontainers verification.

       This section establishes the convention and shows attendees the journey, even though only the first tag exists in Phase 1. Phase 7 (DOC-01) will turn each bullet into a full walkthrough.

    5. **`## What's NOT here yet`** — short section listing the deliberate Phase 1 omissions so a curious reader doesn't think the repo is incomplete:
       - No `OtelSdkConfiguration.java` (Phase 2)
       - No `traceparent` header injection on AMQP (Phase 3)
       - No metrics or log correlation (Phase 4 / Phase 5)
       - No integration tests (Phase 6)
       - No pre-built Grafana dashboard or load script (Phase 7)

    Use plain GitHub-flavored markdown. Tables, fenced code blocks, headings only — no HTML, no images yet (Phase 7 / DOC-04 adds screenshots).

    **`.gitignore`** — standard Java/Maven/IDE/OS ignores so the upcoming `git add . && git commit` doesn't accidentally include build outputs or IDE state. Required entries (one per line):
    ```
    # Maven
    target/

    # IDE
    .idea/
    .vscode/
    *.iml

    # mise local state (mise.toml IS tracked; .mise/ runtime state is not)
    .mise/

    # OS
    .DS_Store
    Thumbs.db

    # Logs / temp
    *.log
    /tmp/

    # Tooling caches
    .gradle/
    ```

    DO NOT add: `mise.toml` (this MUST be tracked), `.tool-versions` (MUST be tracked), `docker-compose.yml` (MUST be tracked), `pom.xml` (MUST be tracked).
  </action>
  <acceptance_criteria>
    - `test -f README.md &amp;&amp; test -f .gitignore` exits 0
    - `grep -c '^# OSE OTel Demo' README.md` returns 1
    - `grep -c '^## Prerequisites' README.md` returns 1
    - `grep -c 'mise run preflight' README.md` returns >= 2 (mentioned in setup AND in port-collision text)
    - `grep -c 'corretto-17.0.13.11.1' README.md` returns 1
    - `grep -c '3.9.11' README.md` returns 1
    - All 7 documented ports appear in the port table: `for p in 3000 4317 4318 5672 15672 8080 8081; do grep -q "| $p |" README.md || exit 1; done` exits 0
    - `grep -c 'mise run verify:bom' README.md` returns >= 1 (baseline-verification command surfaced)
    - `grep -c '^## Workshop checkpoints' README.md` returns 1
    - All 6 step-tags listed: `for t in step-01-baseline step-02-traces step-03-context-propagation step-04-metrics step-05-logs step-06-tests; do grep -q "$t" README.md || exit 1; done` exits 0
    - `grep -c '^## What.s NOT here yet' README.md` returns 1
    - `grep -c '^target/$' .gitignore` returns 1
    - `grep -c '^.idea/$' .gitignore` returns 1
    - `! grep -E '^(mise.toml|\.tool-versions|docker-compose\.yml|pom\.xml)$' .gitignore` exits 0 (these MUST NOT be ignored)
  </acceptance_criteria>
  <verify>
    <automated>grep -q '^## Prerequisites' README.md &amp;&amp; grep -q 'mise run preflight' README.md &amp;&amp; grep -q 'corretto-17.0.13.11.1' README.md &amp;&amp; for p in 3000 4317 4318 5672 15672 8080 8081; do grep -q "| $p |" README.md || exit 1; done &amp;&amp; grep -q '^target/$' .gitignore &amp;&amp; ! grep -E '^(mise\.toml|\.tool-versions|docker-compose\.yml|pom\.xml)$' .gitignore</automated>
  </verify>
  <done>README.md exists with H1 + intro + Prerequisites section (tools/ports/IDE/setup/first-run/baseline-note) + Workshop checkpoints bullet list (all 6 tags named) + What's NOT here yet; .gitignore has standard Maven/IDE/OS ignores and does NOT ignore mise.toml or docker-compose.yml.</done>
</task>

<task id="1-06-T2" type="auto">
  <name>Task 2: End-to-end Phase 1 success-criteria verification (gates the tag)</name>
  <files>(none — verification only)</files>
  <read_first>
    - .planning/ROADMAP.md (lines 25-39 — Phase 1 success criteria — all 5 must be simultaneously green for the tag to be applied)
    - mise.toml (preflight, dev, demo:order, verify:bom tasks)
    - All Phase 1 SUMMARYs (1-01 through 1-05) from prior plans
  </read_first>
  <action>
    Run all five Phase 1 success criteria back-to-back on a clean working tree. The tag in T3 may ONLY be applied if every criterion below is green AND `git status --porcelain` is empty (no uncommitted changes).

    **Criterion 1 — preflight green:**
    `mise run preflight` exits 0 with `Pre-flight: ALL GREEN` in the last line.

    **Criterion 2 — apps work end-to-end:**
    - `mise run infra:up` (idempotent).
    - Launch `mise run dev` in background (capture PID), wait for both `Started ProducerApplication` and `Started ConsumerApplication`.
    - `mise run demo:order` exits 0 (curl succeeds, exit code 0 — `-sf` flag fails on HTTP non-2xx).
    - Within 30 seconds of demo:order, the dev log contains a line matching `OrderCreated received: orderId=`.
    - Cleanup: kill the background `mise run dev`.

    **Criterion 3 — zero OTel libs on classpath:**
    `mise run verify:bom` exits 0 with `Phase 1 baseline confirmed: zero OpenTelemetry libraries on classpath.` in the output. Implementation under the hood: `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matches.

    **Criterion 4 — tag will reproduce baseline:**
    Defer to T3 — the tag itself encodes this. Pre-flight verification: ensure git working tree is fully clean before tagging:
    - `git status --porcelain` outputs an empty string (no uncommitted, no staged, no untracked files — except those covered by `.gitignore`).
    - If any untracked files exist that should be committed, fail this task and instruct the executor to add+commit them before proceeding.

    **Criterion 5 — README Prerequisites self-diagnosis:**
    Heuristic check (no automation available — this is the one criterion that requires human judgment):
    - The Prerequisites section names: mise, Docker, Git as required tools.
    - It documents the 5 infrastructure ports (3000, 4317, 4318, 5672, 15672) AND the 2 app ports (8080, 8081).
    - It tells the reader to run `mise run preflight`.
    - It tells the reader the JDK version (`corretto-17.0.13.11.1`) so they can verify themselves if needed.
    - It mentions both the JetBrains Mise plugin and the .tool-versions auto-detection fallback.

    All five facts MUST be present (verifiable via grep — see acceptance criteria of T1). If any are missing, fail this task; the executor must amend README.md and re-run T2.

    Cleanup at end of T2:
    - All background dev processes stopped (`pkill -f "spring-boot:run"` is acceptable).
    - Optionally `mise run infra:down` (NOT `-v`) to leave the system in a clean state. Or leave infra up — T3 doesn't need it.

    Failure mode: if ANY criterion fails, T3 (the tag) MUST NOT run. The executor should document which criterion failed in the SUMMARY and STOP — fixing it is a Phase 1 task, not a planner task.
  </action>
  <acceptance_criteria>
    - Criterion 1 PASS: `mise run preflight 2>&amp;1 | tail -1` contains `ALL GREEN`
    - Criterion 2 PASS: end-to-end works — `mise run infra:up &amp;&amp; (mise run dev > /tmp/e2e.log 2>&amp;1 &amp;) &amp;&amp; for i in $(seq 1 60); do grep -q 'Started ProducerApplication' /tmp/e2e.log &amp;&amp; grep -q 'Started ConsumerApplication' /tmp/e2e.log &amp;&amp; break; sleep 2; done &amp;&amp; mise run demo:order &amp;&amp; sleep 5 &amp;&amp; grep -q 'OrderCreated received: orderId=' /tmp/e2e.log` exits 0; afterwards `pkill -f spring-boot:run` is run for cleanup
    - Criterion 3 PASS: `mise run verify:bom 2>&amp;1 | grep -q 'Phase 1 baseline confirmed'` exits 0
    - Criterion 4 PASS (pre-tag clean tree): `test -z "$(git status --porcelain)"` exits 0 (or, if non-empty, the executor stages+commits BEFORE running T3)
    - Criterion 5 PASS: all five required README facts present — verified by `grep -q 'mise' README.md &amp;&amp; grep -q 'Docker' README.md &amp;&amp; grep -q 'Git' README.md &amp;&amp; grep -q 'mise run preflight' README.md &amp;&amp; grep -q 'corretto-17.0.13.11.1' README.md &amp;&amp; grep -q '3000' README.md &amp;&amp; grep -q '8080' README.md &amp;&amp; grep -q '\.tool-versions' README.md`
    - Background processes cleaned up at end (no leftover spring-boot:run processes): `! pgrep -f spring-boot:run` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mise run preflight 2>&amp;1 | tail -1 | grep -q 'ALL GREEN' &amp;&amp; mise run verify:bom 2>&amp;1 | grep -q 'Phase 1 baseline confirmed' &amp;&amp; grep -q 'mise run preflight' README.md &amp;&amp; grep -q 'corretto-17.0.13.11.1' README.md</automated>
  </verify>
  <done>All five Phase 1 success criteria are simultaneously green (preflight green; demo:order returns 202 + consumer logs receipt; verify:bom returns zero OTel matches; git working tree clean; README has the five required facts). Ready for T3 to tag.</done>
</task>

<task id="1-06-T3" type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Human-verify Phase 1 baseline + create annotated git tag step-01-baseline</name>
  <what-built>
    - Wave 1: Maven multi-module skeleton (parent POM with BOM-import ordering OTel-first, three child modules), mise toolchain (Corretto 17.0.13.11.1 + Maven 3.9.11) with the full Phase 1 task graph (preflight / infra:up/down/reset/logs / build / test / dev / dev:producer / dev:consumer / demo:order / verify:bom / ui:grafana / ui:rabbitmq), docker-compose.yml with rabbitmq:4.3-management + grafana/otel-lgtm:0.26.0 and a persisted lgtm-data named volume.
    - Wave 2: producer-service Spring Boot app (POST /orders → 202 + RabbitTemplate publish), consumer-service Spring Boot app (@RabbitListener consuming orders.created + ProcessingService no-op + actuator/health on 8081). Zero OTel libraries on classpath in either service.
    - Wave 3 (this plan): README.md with the DOC-02 Prerequisites section + .gitignore. T2 has ALREADY confirmed all five Phase 1 success criteria are simultaneously green and the git working tree is clean.
    - Pending in this task: human verification of the workshop-facing surfaces (Grafana UI loads at :3000, RabbitMQ Mgmt UI at :15672, README reads cleanly) plus creation of the annotated git tag `step-01-baseline`. This is the FIRST of the six WORK-01 tags; the convention established here propagates to Phases 2-6.
  </what-built>
  <how-to-verify>
    Spend ~5 minutes confirming the workshop attendee experience is clean before the tag becomes immutable.

    **Step 1 — Open the README in a markdown viewer** (GitHub preview in a browser, IntelliJ's markdown panel, VS Code's preview, or `glow README.md` in a terminal). Skim the Prerequisites section. Confirm:
    1. The intro paragraph correctly describes "workshop demo teaching manual OTel SDK in Spring Boot 3.4.13 / Java 17".
    2. The required-tools table is readable.
    3. The port table covers all 7 ports.
    4. The IDE setup section mentions both IntelliJ Mise plugin AND `.tool-versions` fallback.
    5. The "First run" code blocks copy-paste cleanly (no smart quotes, no broken backticks).
    6. The "What's NOT here yet" section accurately reflects Phase 1 scope.

    **Step 2 — Sanity-check the running stack one more time:**
    - `mise run infra:up` — both containers should already be up from T2; this is a no-op.
    - Open http://localhost:3000 in a browser. Confirm Grafana login screen appears (admin/admin works; no need to actually log in if pressed for time).
    - Open http://localhost:15672 in a browser. Confirm RabbitMQ Management UI login appears (guest/guest).
    - Run `mise run dev` in one terminal, `mise run demo:order` in another. Confirm 202 returned and consumer's terminal shows `OrderCreated received: orderId=...`.
    - Ctrl-C the `mise run dev` terminal. Confirm BOTH apps shut down cleanly (mise's parallel signal handling — RESEARCH-verified).
    - `mise run infra:down`. Confirm both containers stop, no `-v` (lgtm-data preserved for later phases).

    **Step 3 — Approve or describe issues.**

    If approved, proceed to Step 4 (the tag). If issues exist, list them with file references; do NOT tag.

    **Step 4 — Create the annotated git tag (executor performs after approval):**

    First, ensure git working tree is clean (T2 confirmed this; double-check):
    ```
    git status --porcelain
    ```
    Expected: empty output. If anything is staged or untracked beyond .gitignored items, commit them first with a message like:
    ```
    git add <files>
    git commit -m "chore: phase-1 wrap-up"
    ```

    Then create the annotated tag (the `-a` flag is mandatory — lightweight tags are not sufficient for WORK-01 per RESEARCH.md "Pattern 3"):
    ```
    git tag -a step-01-baseline -m "Workshop checkpoint: Phase 1 — baseline app + scaffolding, zero OTel libs.

    Two-service Spring Boot 3.4.13 / Java 17 demo on host JVM via mise; RabbitMQ + grafana/otel-lgtm in docker-compose. POST /orders → publish → consume works end-to-end. Maven dependency:tree -Dincludes=io.opentelemetry returns zero matches — baseline truly uninstrumented. Phase 2 introduces the OpenTelemetry SDK."
    ```

    Verify the tag:
    ```
    git tag --list step-01-baseline                  # outputs: step-01-baseline
    git for-each-ref --format='%(objecttype) %(refname)' refs/tags/step-01-baseline   # outputs: tag refs/tags/step-01-baseline (NOT 'commit' — proves annotated, not lightweight)
    git show step-01-baseline | head -5              # shows the tag message
    ```

    DO NOT push the tag automatically. The user (`coto@petabyte.cl`) decides when to push. Mention in the SUMMARY that `git push origin step-01-baseline` is the follow-up the user runs when ready (or `git push --tags` to push all annotated tags). The tag is local-only until the user explicitly pushes — this respects the GSD git-safety protocol (no automatic pushes).

    **Step 5 — Reproducibility self-test (criterion #4):**
    From a different working directory (or by stashing):
    ```
    git checkout step-01-baseline -- :/   # checks out the tagged tree to the index/working tree
    ```
    OR (less invasive):
    ```
    git -C /tmp clone --branch step-01-baseline --depth 1 file://$(pwd) verify-baseline
    cd /tmp/verify-baseline
    mise install
    mise run preflight
    mise run verify:bom
    cd -
    rm -rf /tmp/verify-baseline
    ```

    Either path proves criterion #4 (`git checkout step-01-baseline` reproduces the green-baseline state).
  </how-to-verify>
  <resume-signal>Type "approved" to proceed with creating the annotated tag, "not yet — issues:" with a list of issues to fix, or "skip-tag" to defer tagging until later (which leaves Phase 1 incomplete — WORK-01 unsatisfied).</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Local repo → remote (e.g., GitHub) | Tag pushed via `git push origin step-01-baseline` — workshop artifact will be public/internally-published |
| Workshop attendee reading README → external links | mise install URL, IntelliJ plugin URL, VS Code extension URL — all clickable in a markdown viewer |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-1-06-01 | Tampering | Lightweight tag substituted for annotated tag, allowing silent re-pointing | mitigate | The tag-creation command uses `git tag -a` (annotated); acceptance criteria assert via `git for-each-ref` that the tag's objecttype is `tag`, not `commit` |
| T-1-06-02 | Information Disclosure | README.md leaking internal infrastructure details | accept | README is intended public/internal-team-shared; no secrets, no internal hosts, no real credentials beyond documented workshop defaults (guest/guest, admin/admin) |
| T-1-06-03 | Tampering | External links in README (mise install URL, IntelliJ plugin URL) hijacked by DNS spoofing | accept | Standard third-party links (`https://mise.run`, `plugins.jetbrains.com`); same exposure as any other documentation; HTTPS enforced by browsers |
| T-1-06-04 | Repudiation | Tag created without a meaningful message; cannot trace what state it represents | mitigate | Acceptance criteria require the annotated message to mention "Phase 1", "zero OTel libs", "step-01-baseline" — content auditable |
| T-1-06-05 | Tampering | A future phase tag overwrites step-01-baseline (force-push) | mitigate | Tags are immutable under normal git workflow; `git push --force-with-lease origin :step-01-baseline` would be needed to remove. Convention is documented in PROJECT.md (Key Decisions row 7: "Staged git checkpoints (one branch/tag per workshop step)" — immutable). Out-of-band protection (e.g., GitHub branch/tag protection rules) is the user's call, not Phase 1's. |
| T-1-06-06 | Tampering | `git status` reports a clean tree but a stale build artifact in `target/` was forgotten and is not in `.gitignore` | mitigate | `.gitignore` declares `target/`; T1 acceptance criteria assert this entry exists |

**Phase scope:** Workshop scaffold — README is documentation, .gitignore is hygiene, the tag is a git ref. No runtime threat surface introduced. Out of scope: signed tags (`git tag -s`) — the workshop is not currently signing artifacts; if the team requires GPG-signed tags for trust, that's a Phase-zero ops decision out of Phase 1's scope.
</threat_model>

<verification>
- README.md exists with H1, intro, Prerequisites section (with all 5 required facts), Workshop checkpoints list (all 6 tag names), and "What's NOT here yet" section.
- .gitignore declares standard ignores; does NOT ignore mise.toml / .tool-versions / docker-compose.yml / pom.xml.
- All 5 Phase 1 success criteria simultaneously green (verified in T2).
- After T3 approval: `git tag --list step-01-baseline` outputs `step-01-baseline`.
- `git for-each-ref --format='%(objecttype)' refs/tags/step-01-baseline` outputs `tag` (annotated, not lightweight).
- `git show step-01-baseline` displays the tag message containing "Phase 1" and "zero OTel libs".
- `git checkout step-01-baseline` (in a clean clone or worktree) reproduces preflight-green + dev-green + verify:bom-green.
</verification>

<success_criteria>
- DOC-02 satisfied: README.md "## Prerequisites" section names tools, ports, and `mise run preflight`; a first-time attendee can self-diagnose tooling issues before opening any code.
- WORK-01 (Phase 1 portion) satisfied: annotated git tag `step-01-baseline` exists on the working branch (typically `main`); the tagging convention is established for Phases 2-6.
- All 5 Phase 1 success criteria from ROADMAP.md verified simultaneously green at the moment of tagging.
- The tag is local-only after T3 (user pushes when ready) — respects git-safety protocol.
- .gitignore prevents target/ and IDE state from polluting future commits.
</success_criteria>

<output>
After completion, create `.planning/phases/01-baseline-scaffold/1-06-SUMMARY.md` documenting:
- README.md final structure (H1 + sections + first ~5 lines of each section)
- Confirmed Phase 1 success-criteria results (paste the green outputs of preflight, demo:order, verify:bom)
- Confirmed annotated tag exists: paste `git for-each-ref --format='%(objecttype) %(objectname:short) %(refname)' refs/tags/step-01-baseline`
- Confirmed tag message: paste `git show step-01-baseline | head -10`
- A note that the user should run `git push origin step-01-baseline` when ready to publish (NOT done by this plan)
- Files created: 2 (README.md + .gitignore) + 1 git ref (refs/tags/step-01-baseline)

Then create `.planning/phases/01-baseline-scaffold/PHASE-SUMMARY.md` rolling up all six plan summaries — phase exit summary documenting:
- Total artifacts created across the phase (count files, git refs)
- The single gate sentence: "mvn dependency:tree -Dincludes=io.opentelemetry returns zero matches" — confirmed
- Tag created: step-01-baseline
- Lessons learned (any deviations from RESEARCH.md, surprising failure modes encountered)
- Phase 2 readiness signal: parent POM BOMs in correct order, ready to add the first `<dependency>io.opentelemetry:opentelemetry-api</dependency>` in Phase 2's first plan
</output>
