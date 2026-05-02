---
phase: 06-verification-tests
plan: 06
type: execute
wave: 6
depends_on:
  - 06-05
files_modified:
  - README.md
autonomous: false
requirements:
  - TEST-06
tags:
  - readme
  - documentation
  - exit-gate
  - human-checkpoint
  - phase-6
must_haves:
  truths:
    - id: D-20-step-6-section
      description: "README has a '## Step 6: Verification Tests' H2 heading after the Step 5 section and before 'Reading the code'"
      verify: "grep -q '^## Step 6: Verification Tests' README.md && awk '/^## Step 5: Logs Correlation/{step5=NR} /^## Step 6: Verification Tests/{step6=NR} /^## Reading the code/{reading=NR} END{exit !(step5 < step6 && step6 < reading)}' README.md"
    - id: D-20-integration-tests-module
      description: "Step 6 section names the new integration-tests Maven module"
      verify: "awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'integration-tests'"
    - id: D-20-rabbitmq-container-callout
      description: "Step 6 section calls out RabbitMQContainer + random-port property (TEST-01 SC #2)"
      verify: "awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -E 'RabbitMQContainer|random.*port'"
    - id: D-20-simple-span-processor-callout
      description: "Step 6 section calls out SimpleSpanProcessor + InMemorySpanExporter swap as the test-determinism lesson (PITFALLS #4/#11)"
      verify: "awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -E 'SimpleSpanProcessor|InMemorySpanExporter'"
    - id: D-20-classifier-callout
      description: "Step 6 section briefly mentions the <classifier>exec</classifier> mechanism on spring-boot-maven-plugin (D-04)"
      verify: "awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'classifier'"
    - id: D-20-four-tests-callout
      description: "Step 6 section names the four @Test methods (or the four signal categories: traces / logs / metrics / failure path)"
      verify: "awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'failure' && awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -E '4 |four '"
    - id: D-20-mise-run-test-block
      description: "Step 6 section ends with a code block showing `mise run test`"
      verify: "awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'mise run test'"
    - id: D-20-current-marker-moved
      description: "Workshop checkpoints table has '**Current.**' on step-06-tests (NOT on step-05-logs). Backup check is structure-independent."
      verify: "awk '/^## Workshop checkpoints/,/^## Step /' README.md | grep -E 'step-06-tests.*\\*\\*Current\\.\\*\\*|\\*\\*Current\\.\\*\\*.*step-06-tests' && ! awk '/^## Workshop checkpoints/,/^## Step /' README.md | grep -E 'step-05-logs.*\\*\\*Current\\.\\*\\*' && grep -F 'step-06-tests' README.md | grep -F '**Current.**'"
    - id: D-20-not-here-yet-updated
      description: "'No verification tests (Phase 6)' or similar bullet REMOVED from 'What's NOT here yet' block (if it existed in pre-phase-6 README)"
      verify: "! grep -E 'No verification tests \\(Phase 6\\)|No CI tests' README.md"
    - id: SC1-tests-pass-with-host-down
      description: "ROADMAP SC #1 verified — mise run test (mvn verify) passes with host RabbitMQ stopped (smoke test)"
      verify: "echo 'manual-verify — see human-checkpoint task'"
    - id: SC2-random-port-visible
      description: "ROADMAP SC #2 verified — test logs show non-default random RabbitMQ port (manual smoke; programmatic check exists in 06-05 acceptance gate but reproduced here as part of the exit-gate verification)"
      verify: "echo 'manual-verify — see human-checkpoint task'"
    - id: SC3-cross-service-assertions-deterministic
      description: "ROADMAP SC #3 verified — cross-service IT asserts traceId shared, parentSpanId linkage, SpanKind, messaging semconv; uses SimpleSpanProcessor + forceFlush; deterministic (manual review of OrderFlowIT.java + SUMMARY)"
      verify: "echo 'manual-verify — see human-checkpoint task'"
    - id: SC4-exits-nonzero-on-failure
      description: "ROADMAP SC #4 verified — mise run test exits non-zero on assertion failure (TEST-06 — Failsafe binding from 06-02)"
      verify: "echo 'manual-verify — see human-checkpoint task'"
    - id: SC5-tag-applied-by-orchestrator
      description: "ROADMAP SC #5: annotated tag step-06-tests applied by orchestrator AFTER human gate (NOT by this plan; same pattern as Phases 1/2/3/4/5)"
      verify: "echo 'tag application is orchestrator-owned per WORK-01 / D-21 / Phase 5-06 / Phase 2-06 precedent'"
    - id: D-21-tag-deferred
      description: "The annotated tag step-06-tests is applied by orchestrator AFTER human gate approves all 5 ROADMAP SCs (do NOT apply tag in this plan; the executor surfaces SC results then waits for orchestrator). Phase 5 Plan 05-06 set the precedent (STATE.md line 90)."
      verify: "echo 'tag application is orchestrator-owned per WORK-01 / D-21 / Phase 2-06 / Phase 5-06 precedent'"
  artifacts:
    - path: README.md
      provides: "Step 6: Verification Tests section + workshop checkpoints update"
      contains: "## Step 6: Verification Tests"
      contains: "step-06-tests"
      contains: "integration-tests"
      contains: "RabbitMQContainer"
      contains: "mise run test"
  key_links:
    - from: README.md "Step 6: Verification Tests" section
      to: integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java + TestOtelConfiguration.java + TestOtelHolder.java
      via: "Markdown link with `./path/to/file` syntax (matches Step 5 section style)"
      pattern: "\\[.*OrderFlowIT|\\[.*TestOtelConfiguration|\\[.*TestOtelHolder"
    - from: README.md "Step 6" workshop checkpoints `**Current.**` marker
      to: orchestrator-applied annotated git tag step-06-tests
      via: "WORK-01 / D-21 atomic-commit workflow"
      pattern: "step-06-tests.*\\*\\*Current\\.\\*\\*"
---

<objective>
Add a Markdown "## Step 6: Verification Tests" section to `README.md` mirroring the existing "## Step 5: Logs Correlation" section's shape (D-20 / PATTERNS.md File 8). Move the `**Current.**` marker in the Workshop checkpoints table from `step-05-logs` to `step-06-tests`. Remove any stale "No verification tests (Phase 6)" bullet from the "What's NOT here yet" block (if present in the pre-phase-6 README). Then run an empirical smoke verification of all 5 ROADMAP success criteria (HUMAN CHECKPOINT — pause for user approval before suggesting tag application; the orchestrator applies the annotated tag `step-06-tests` per WORK-01 / D-21 after the human gate).

Purpose: Phase 6's exit gate. Three deliverables: documentation (the Step 6 README section), live verification (5 ROADMAP success criteria run end-to-end), and the annotated tag application (WORK-01). The tag itself is orchestrator-applied per the Phase 2/3/4/5 precedent — this plan delivers the source artifacts and the verified-green state, then surfaces results for the human gate.

Output: README.md modified (~70-90 lines added for the Step 6 section, 1 line marker move, possibly 1 line bullet removal). Smoke-test outputs documented in the SUMMARY. Tag is NOT applied by this plan.

Why this is wave 6: requires Plan 06-05 to be live (so the smoke test can verify the full pipeline end-to-end with a real `mvn verify` run).

Why this plan has a checkpoint and `autonomous: false`: the smoke test verifies behavior against live infrastructure (host docker-compose RabbitMQ stopped, real Testcontainers spin-up, real Spring contexts, real RabbitMQ broker traffic). The human checkpoint is a programmatic-result-review gate — the executor presents the 5 SC results, the user approves, and the orchestrator commits the README delta atomically with the annotated tag in a single step.

**Rule-1 deviation note:** the annotated tag `step-06-tests` is NOT applied by this plan or its executor. Same pattern as Phase 2 Plan 02-06 and Phase 5 Plan 05-06 (STATE.md line 90 records the precedent). Tag application is the orchestrator's responsibility AFTER the human gate. This plan ends with a Status flip section that the orchestrator commits atomically with `git tag -a step-06-tests`.
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
@.planning/phases/06-verification-tests/06-02-SUMMARY.md
@.planning/phases/06-verification-tests/06-03-SUMMARY.md
@.planning/phases/06-verification-tests/06-04-SUMMARY.md
@.planning/phases/06-verification-tests/06-05-SUMMARY.md
@README.md

<interfaces>
<!-- Structural template — analog from Phase 5 README "Step 5: Logs Correlation" section (PATTERNS.md File 8). -->

Phase 5's "Step 5" section (README.md lines 127-210, ~80 lines) provides the structural template:
1. Opening paragraph keyed to the tag name (e.g., "`step-05-logs` adds the third OTel signal — logs — to both services").
2. Bulleted list (3-5 items) of SDK / config touch points with backtick code references and Markdown file links.
3. Idiomatic command code-block (e.g., `mise run demo:order` / `mise run test`).
4. Optional Grafana / Loki / observability query block showing the assertion the attendee can verify.
5. Closing paragraph that calls out the headline insight.

Phase 6 Step 6 target structure (CONTEXT D-20 + PATTERNS §File 8 §Target):
- Opening paragraph: `step-06-tests` adds a CI-grade proof of the three-signal chain via a new `integration-tests` Maven module + a single cross-service `OrderFlowIT.java`.
- Bullet 1: `RabbitMQContainer` random-port property visible in test logs (TEST-01 SC #2 — link to OrderFlowIT.java).
- Bullet 2: `SimpleSpanProcessor` + `InMemorySpanExporter` swap as the test-determinism lesson (PITFALLS #4/#11 — link to TestOtelHolder.java).
- Bullet 3: `<classifier>exec</classifier>` mechanism on `spring-boot-maven-plugin` (D-04) — ~2-line callout with link to producer-service POM.
- Bullet 4: the four `@Test` methods (traces / logs / metrics / failure path) named explicitly.
- Bullet 5: production-vs-test SDK divergence as a deliberate pedagogical contrast (Phase 2 D-01 per-service duplication is a PRODUCTION rule; test infra shares one TestOtelConfiguration per D-07).
- Closing paragraph: `mise run test` exits non-zero on failure (TEST-06 / D-21 tag gate). One-line `mise run test` example.

Workshop checkpoints table (existing in README — Phase 5 Plan 05-06 left it pointing at step-05-logs):
- Move `**Current.**` from `step-05-logs` row to `step-06-tests` row.
- Verify ordering invariant: `awk '/^## Workshop checkpoints/,/^## Step/' README.md` should match step-06-tests as the current line.

Smoke verification commands (run end-to-end, document outputs in SUMMARY):
1. `docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true` — ensure host RabbitMQ is stopped (TEST-01 SC #1)
2. `mvn -B -pl integration-tests -am verify 2>&1 | tee /tmp/06-06-smoke.log` — full verify run
3. Verify exit code 0
4. `grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-06-smoke.log` — random port log line (TEST-01 SC #2)
5. `grep -E 'Tests run:.*Failures: 0.*Errors: 0' /tmp/06-06-smoke.log` — Failsafe summary shows all tests pass
6. Optional sabotage check: introduce a deliberate assertion failure, run `mvn verify`, confirm exit non-zero, revert (TEST-06 contract — verify mvn fails loud).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Add "## Step 6: Verification Tests" section to README.md mirroring Phase 5 Step 5 structure; move Current marker to step-06-tests; remove any stale "Phase 6 not here yet" bullet</name>
  <files>README.md</files>
  <read_first>
    - README.md (full file — pay attention to: existing "## Step 5: Logs Correlation" section as the structural template; "## Workshop checkpoints" table with the `**Current.**` marker on step-05-logs; "What's NOT here yet" block if it exists; "## Reading the code" section as the boundary AFTER which Step 6 must NOT be inserted)
    - .planning/phases/06-verification-tests/06-PATTERNS.md (§File 8 — Step 6 section structural template; lines 519-558 give the bullet structure target)
    - .planning/phases/06-verification-tests/06-CONTEXT.md (D-20 — README delta scope; D-21 — orchestrator-applied tag)
    - .planning/phases/06-verification-tests/06-05-SUMMARY.md (the test-class details + smoke log evidence to reference in the README)
    - integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java (the test class the README points at — verify file path + the four test method names match D-14)
    - integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java (the test config the README points at)
    - integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java (link target for the SimpleSpanProcessor lesson bullet)
    - .planning/phases/05-logs-correlation/05-06-readme-and-tag-PLAN.md (the Phase 5 plan — tag-deferred-to-orchestrator pattern, Status flip section, smoke verification commands)
  </read_first>
  <action>
Apply the following edits to `README.md`. Each edit cites a structural anchor; the executor uses the Edit tool to apply them.

**EDIT 1 — Insert the new "## Step 6: Verification Tests" section.**

Locate the existing "## Step 5: Logs Correlation" section. Step 5 ends just before the next H2 heading (likely "## Reading the code"). Insert the new "## Step 6" section AFTER Step 5 and BEFORE "## Reading the code". Use this template (paste verbatim, adjusting only the file paths/links to reflect actual repo layout if needed):

```markdown
## Step 6: Verification Tests

`step-06-tests` adds a CI-grade proof of the three-signal instrumentation chain. A new top-level [`integration-tests`](./integration-tests) Maven module hosts a single cross-service [`OrderFlowIT.java`](./integration-tests/src/test/java/com/example/e2e/OrderFlowIT.java) that starts a real `RabbitMQContainer` on a random port, launches both `ProducerApplication` and `ConsumerApplication` as two `SpringApplicationBuilder` contexts in one JVM, exercises the full `POST /orders` → publish → consume flow through real broker traffic, and asserts on traces + logs + metrics captured in-memory. Run it with `mise run test`; the build exits non-zero on any assertion failure (TEST-06).

The four `@Test` methods cover the workshop's four signal areas:

- **`RabbitMQContainer` on a random port** — the `@BeforeAll` method emits an explicit `LOG.info("RabbitMQ test container available at {}:{}", ...)` line that prints something like `RabbitMQ test container available at localhost:54321` in the test log (NOT the default `:5672`). With your host `docker compose` RabbitMQ stopped, the tests still pass — proof that Testcontainers is genuinely used (TEST-01).

- **`SimpleSpanProcessor` + `InMemorySpanExporter` swap** — production wires `BatchSpanProcessor` + `OtlpGrpcSpanExporter`, but tests use [`TestOtelHolder`](./integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java) which builds the SDK with the synchronous `SimpleSpanProcessor` and the in-memory exporter from `opentelemetry-sdk-testing`. Every `span.end()` exports immediately — no `Thread.sleep` needed in tests. PITFALLS.md #4 / #11 made manifest in code.

- **`<classifier>exec</classifier>` on the service POMs** — the producer and consumer service POMs publish TWO artifacts: the plain classes jar (default — exposes `ProducerApplication.class` directly on the classpath) and a separate `-exec` repackaged executable fat jar (runnable with `java -jar`). The integration-tests module depends on the plain jars so `new SpringApplicationBuilder(ProducerApplication.class, ...)` works. See [`producer-service/pom.xml`](./producer-service/pom.xml) for the canonical Spring Boot 3.4.13 syntax — a useful pattern for any multi-module Spring Boot codebase.

- **The four `@Test` methods**:
  1. **traces** — producer + consumer spans share `traceId`; consumer's `parentSpanId == producer.spanId`; SpanKind set covers SERVER + INTERNAL + PRODUCER + CONSUMER + INTERNAL; messaging semconv attributes (`messaging.system=rabbitmq`, `messaging.operation_type=publish/process`).
  2. **logs** — producer-side `LOG.info` records carry the producer trace's `trace_id` (proves Phase 5's `OpenTelemetryAppender.install(...)` wiring still works through the test SDK).
  3. **metrics** — `orders.created` counter increments to 1 with `order.priority="express"`; `http.server.request.duration` histogram records the POST with `http.request.method=POST` + `http.response.status_code=202`.
  4. **failure path** — the 10th order's CONSUMER span has `Status.ERROR` + a recorded exception event; a `LOG.error` record carries the same trace_id (triple-signal correlation — the workshop's strongest single statement of "all three signals work together").

- **Production-vs-test SDK divergence as a deliberate pedagogical contrast** — Phase 2's per-service duplication of `OtelSdkConfiguration.java` is a PRODUCTION rule (D-01 / DOC-05). Test infrastructure is exempt: [`TestOtelConfiguration.java`](./integration-tests/src/test/java/com/example/e2e/TestOtelConfiguration.java) is a single `@TestConfiguration` shared by both Spring contexts because the in-memory exporter must see ALL spans across both services in one queue. The contrast itself is the lesson — duplicate when readers benefit from reading the same setup twice; share when the test fixture's purpose requires one shared instance.

Run the suite (with your host `docker compose` RabbitMQ stopped, to prove Testcontainers is genuinely used):

```bash
docker compose stop rabbitmq        # if currently running
mise run test                       # → mvn -T 1C verify
```

You should see four green tests in the Failsafe summary plus a `RabbitMQ test container available at localhost:<random-port>` line in the output. The test exits non-zero on any assertion failure — suitable for any CI runner with Docker available.
```

**EDIT 2 — Move `**Current.**` marker from step-05-logs to step-06-tests in the Workshop checkpoints table.**

Locate the "## Workshop checkpoints" table. Find the row for `step-05-logs` — it currently has `**Current.**` somewhere on the line. REMOVE the `**Current.**` token from that line. Find the row for `step-06-tests` — APPEND `**Current.**` to that line in the same column position the previous `**Current.**` occupied (the existing pattern is consistent across phases; mirror it byte-for-byte from how Plan 05-06 placed it).

If the step-06-tests row does not exist yet (because earlier phases left it as a placeholder), the executor MUST add the row in the table maintaining the same Markdown table column shape as the step-05-logs / step-04-metrics / step-03-context-propagation rows. Likely shape:

```markdown
| `step-06-tests` | Cross-service Testcontainers IT proves the full instrumentation chain in CI | **Current.** |
```

(Column count and exact wording must match the existing table — read it carefully.)

**EDIT 3 — Remove any stale "Phase 6 not here yet" bullet (if present).**

Locate the "## What's NOT here yet" block (or similarly-named "Coming next" / "Future" section). If a bullet of the form `No verification tests (Phase 6)` or `No CI tests` exists, REMOVE THE LINE entirely. If no such bullet exists, this edit is a no-op (acceptance criterion `D-20-not-here-yet-updated` is satisfied trivially via the absent grep match).

DO NOT:
- Apply the annotated tag `step-06-tests` (orchestrator-owned per WORK-01 / D-21 / Phase 5-06 precedent — this plan's executor MUST NOT run `git tag` for `step-06-tests`).
- Pre-flip Phase 6 SHIPPED status in STATE.md / ROADMAP.md / REQUIREMENTS.md (atomic with the orchestrator's tag-apply commit; same as Phase 5).
- Edit any Phase 1-5 README sections (those are frozen by their own annotated tags; modifying them retroactively breaks `git checkout step-NN-...` reproducibility).
- Add a screenshot reference (DOC-04 = Phase 7 deliverable; out of scope).
- Use `cat << EOF` heredoc — use the Edit tool.

After applying the 3 edits, run the smoke verification sequence (acceptance criteria below cover this):

```bash
cd $(git rev-parse --show-toplevel)
docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true
mvn -B -pl integration-tests -am verify 2>&1 | tee /tmp/06-06-smoke.log
echo "Exit code: $?"
grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-06-smoke.log
grep -E 'Tests run:.*Failures: 0.*Errors: 0' /tmp/06-06-smoke.log
```

Capture the outputs verbatim in the SUMMARY for the human gate to review.
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q '^## Step 6: Verification Tests' README.md && awk '/^## Step 5: Logs Correlation/{step5=NR} /^## Step 6: Verification Tests/{step6=NR} /^## Reading the code/{reading=NR} END{exit !(step5 < step6 && step6 < reading)}' README.md && awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'integration-tests' && awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -E 'RabbitMQContainer|random.*port' && awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -E 'SimpleSpanProcessor|InMemorySpanExporter' && awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'classifier' && awk '/^## Step 6: Verification Tests/,/^## Reading the code/' README.md | grep -F 'mise run test' && grep -F 'step-06-tests' README.md | grep -F '**Current.**' && ! grep -E 'No verification tests \(Phase 6\)|No CI tests' README.md</automated>
  </verify>
  <acceptance_criteria>
    - `## Step 6: Verification Tests` H2 exists in README
    - Step 6 appears AFTER Step 5 and BEFORE "Reading the code": `awk '/^## Step 5: Logs Correlation/{step5=NR} /^## Step 6: Verification Tests/{step6=NR} /^## Reading the code/{reading=NR} END{exit !(step5 < step6 && step6 < reading)}' README.md` exits 0
    - Step 6 section contains: `integration-tests`, `RabbitMQContainer` OR `random.*port`, `SimpleSpanProcessor` OR `InMemorySpanExporter`, `classifier`, `failure`, `4 ` or `four `, `mise run test` (each verified by grep within the awk-bounded section)
    - Workshop checkpoints table: `step-06-tests` line carries `**Current.**`; `step-05-logs` line does NOT carry `**Current.**`; only ONE row in the table has `**Current.**`
    - Stale "No verification tests (Phase 6)" or "No CI tests" bullet absent (or never existed)
    - Smoke test (run as part of acceptance):
      - `docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true` succeeds (or no-op if not running)
      - `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am verify` exits 0
      - Test log contains: `grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-06-smoke.log` returns 0
      - Test log contains a Failsafe summary showing all 4 tests passed: `grep -E 'Tests run: 4.*Failures: 0.*Errors: 0' /tmp/06-06-smoke.log` returns 0
    - NO new annotated git tag `step-06-tests` created in this plan: `git tag -l 'step-06-tests' | grep -q .` returns 1 (NOT found — orchestrator applies)
    - NO STATE.md / ROADMAP.md / REQUIREMENTS.md edits in this plan (those are atomic with the orchestrator's tag-apply commit)
  </acceptance_criteria>
  <done>
README.md has the new Step 6 section; Workshop checkpoints table marker moved to step-06-tests; stale Phase-6-not-here bullet removed if any. All 5 ROADMAP success criteria verified empirically (smoke test). Tag NOT applied (orchestrator-owned). Plan presents results to the human gate.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Checkpoint: Human reviews Phase 6 ROADMAP success criteria + approves orchestrator-applied tag</name>
  <what-built>
Plan 06-06 added the README "Step 6: Verification Tests" section, moved the workshop checkpoint marker to step-06-tests, and ran an empirical smoke verification of all 5 ROADMAP success criteria. The annotated tag `step-06-tests` is NOT yet applied — the orchestrator applies it AFTER this gate per WORK-01 / D-21 / Phase 5-06 precedent.
  </what-built>
  <how-to-verify>
1. Read the new "## Step 6: Verification Tests" section in README.md. Confirm structure mirrors Step 5 (opening paragraph + ~5 bullets + code block).
2. Inspect the Workshop checkpoints table. Confirm `**Current.**` is on `step-06-tests` (and NOT on any other row).
3. Run the smoke verification yourself:
   ```bash
   cd /home/coto/dev/demo/ose-otel-demo
   docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true
   mvn -B -pl integration-tests -am verify
   ```
   Confirm:
   - Exit code is 0.
   - Failsafe summary shows: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.
   - Test output contains: `RabbitMQ test container available at localhost:<random-port>` (random port, NOT 5672).
4. Verify the four ROADMAP success criteria empirically:
   - **SC #1:** `mise run test` (or `mvn -pl integration-tests -am verify`) passes with host docker-compose RabbitMQ stopped — confirmed by step 3 above.
   - **SC #2:** Test logs show non-default random RabbitMQ port — confirmed by the explicit `LOG.info` line.
   - **SC #3:** Cross-service IT asserts traceId shared, parentSpanId == producer.spanId, SpanKind covers all 5, messaging semconv present, deterministic via SimpleSpanProcessor + forceFlush — confirmed by reading `OrderFlowIT.java` test 1.
   - **SC #4:** `mise run test` exits non-zero on any assertion failure — verifiable by introducing a deliberate sabotage (e.g., comment out `LOG.info` in OrderController, run `mvn verify`, confirm non-zero exit, revert).
5. Optionally restart docker-compose RabbitMQ for subsequent local development:
   ```bash
   docker compose -f docker-compose.yml start rabbitmq
   ```
  </how-to-verify>
  <resume-signal>
After gate approval:
- Type **"approved"** to signal the orchestrator to apply the annotated tag and atomically flip Phase 6 to SHIPPED in STATE.md / ROADMAP.md / REQUIREMENTS.md.
- Type **"deferred — see notes"** if any SC is not satisfactorily green (the orchestrator routes to a revision plan against the offending Plan 06-XX before tag application — same pattern as Phase 5's bean-cycle defer documented in STATE.md line 89).
- Describe specific issues to fix if any SC fails.

**The orchestrator (NOT the executor) is responsible for:**
- `git tag -a step-06-tests -m "Phase 6: Verification Tests — Testcontainers + cross-service IT proves the three-signal chain"`
- Updating `.planning/STATE.md` (`completed_phases: 6`, position update, Phase 6 entry)
- Updating `.planning/ROADMAP.md` (Phase 6 `[ ]` → `[x]`, "Plans" status update)
- Updating `.planning/REQUIREMENTS.md` (TEST-01..TEST-06 Pending → Complete)
- One atomic commit landing the README delta from this plan + the three planning-doc updates + the tag.
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| README.md → workshop attendee mental model | Documentation is the workshop's canonical narrative; misleading docs break the broken-then-fixed pedagogical sequence |
| Workshop checkpoints table → annotated git tags | The `**Current.**` marker is the single-source-of-truth for which tag attendees should `git checkout` |
| `mvn verify` smoke run → 5 ROADMAP success criteria | The smoke is the pre-tag empirical proof; bypassing it risks shipping a broken workshop checkpoint |
| Tag application boundary → orchestrator vs executor | This plan's executor MUST NOT run `git tag step-06-tests`; same pattern as Phase 2/3/4/5 |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-06-01 | Tampering | README's `**Current.**` marker on wrong row | mitigate | Acceptance criterion `D-20-current-marker-moved` uses awk-bounded grep with explicit positive (`step-06-tests.*\*\*Current\.\*\*`) AND negative (`! ... step-05-logs.*\*\*Current\.\*\*`) checks. Phase 5 Plan 05-06 set this discipline. |
| T-06-06-02 | Spoofing | Executor accidentally applies the annotated tag despite Rule-1 deviation note | mitigate | Acceptance criterion `! git tag -l 'step-06-tests'` verifies the tag is NOT applied. Phase 2/3/4/5 precedent + STATE.md line 90 + this plan's explicit DO-NOT. The orchestrator's `/gsd-execute-phase` workflow knows to skip tag application for Plans flagged with `autonomous: false` + `human-checkpoint`. |
| T-06-06-03 | Tampering | Future Phase 7 README walkthrough (DOC-01) overwrites the Step 6 section | accept | DOC-01 is Phase 7 scope. Phase 7 ROADMAP entry instructs the planner to PRESERVE existing per-step sections — they become the building blocks of the full walkthrough. Risk acknowledged; mitigation lives in Phase 7's plan. |
| T-06-06-04 | Information Disclosure | Smoke logs leak random ports / Testcontainers state to stderr | accept | Random ports change per run; no persistent attack surface. The leak is the FEATURE per TEST-01 SC #2. Logs go to /tmp/ which is workshop-attendee-laptop scoped. |
| T-06-06-05 | Repudiation | Smoke "passed" but bug present (false negative) | mitigate | Acceptance criterion `Tests run: 4.*Failures: 0.*Errors: 0` greps the EXACT Failsafe summary line — any `Tests run: 3` (skipped test) or `Failures: 1` would fail the gate. Plan 06-05's per-test acceptance criteria are the deeper guard. |
| T-06-06-06 | Denial of Service | docker compose stop rabbitmq fails because user has no host stack | accept | The `2>/dev/null || true` swallows the error — TEST-01 SC #1 is satisfied either way (no host RabbitMQ to fall back to). |
| T-06-06-07 | Elevation of Privilege | Markdown injection in README via test-output text | accept | The README content is paste-verbatim from this plan; no test-output text is interpolated into the README. Workshop attendees read the README in a Markdown renderer that doesn't execute code. |
| T-06-06-08 | Tampering | The executor pre-flips STATE/ROADMAP/REQUIREMENTS Phase 6 to SHIPPED | mitigate | Acceptance criterion explicitly: "NO STATE.md / ROADMAP.md / REQUIREMENTS.md edits in this plan". Phase 2 Plan 02-06 (STATE.md commit log) + Phase 5 Plan 05-06 set the precedent: status flips are ATOMIC with the orchestrator's tag-apply commit. |
| T-06-06-09 | Repudiation | Human gate approved without actually running the smoke | accept | The checkpoint task explicitly instructs the user to RUN the smoke command in step 3. Trust the user. The 4-step verification ladder (read README → inspect table → run smoke → optionally sabotage-test) is the canonical pre-tag review pattern. |
</threat_model>

<verification>
- `grep -q '^## Step 6: Verification Tests' README.md`
- Section ordering: `awk '/^## Step 5: Logs Correlation/{step5=NR} /^## Step 6: Verification Tests/{step6=NR} /^## Reading the code/{reading=NR} END{exit !(step5 < step6 && step6 < reading)}' README.md` exits 0
- All 7 D-20 callouts present in the Step 6 section: integration-tests, RabbitMQContainer/random port, SimpleSpanProcessor/InMemorySpanExporter, classifier, four/4 (tests), failure, mise run test
- Workshop checkpoints `**Current.**` marker on step-06-tests, NOT on step-05-logs
- `! grep -E 'No verification tests \(Phase 6\)|No CI tests' README.md`
- Smoke verification:
  - `docker compose -f docker-compose.yml stop rabbitmq 2>/dev/null || true` returns 0 (or no-op)
  - `cd $(git rev-parse --show-toplevel) && mvn -B -pl integration-tests -am verify` exits 0
  - `grep -E 'RabbitMQ test container available at .*:[0-9]+' /tmp/06-06-smoke.log` returns 0
  - `grep -E 'Tests run: 4.*Failures: 0.*Errors: 0' /tmp/06-06-smoke.log` returns 0
- NO annotated tag created in this plan: `! git tag -l 'step-06-tests' | grep -q .`
- NO planning-doc edits to STATE/ROADMAP/REQUIREMENTS in this plan (they land atomically with the orchestrator tag-apply commit)
</verification>

<success_criteria>
1. README.md has new "## Step 6: Verification Tests" H2 section between Step 5 and "Reading the code".
2. Step 6 section structurally mirrors Step 5 (opening paragraph + 5 bullets + code block).
3. Step 6 section calls out: integration-tests module, RabbitMQContainer random-port, SimpleSpanProcessor + InMemorySpanExporter, `<classifier>exec</classifier>`, four `@Test` methods (or four signal categories), `mise run test`.
4. Workshop checkpoints table: `**Current.**` on step-06-tests (NOT step-05-logs).
5. Stale "No verification tests (Phase 6)" bullet removed (or never existed).
6. ROADMAP SC #1 verified: `mvn -pl integration-tests -am verify` passes with host docker-compose RabbitMQ stopped.
7. ROADMAP SC #2 verified: test log contains the random-port line (NOT default 5672).
8. ROADMAP SC #3 verified: OrderFlowIT.java test 1 covers traceId / parentSpanId / SpanKind / messaging semconv (read-confirmed).
9. ROADMAP SC #4 verified: `mvn verify` exits non-zero on assertion failure (TEST-06 — Failsafe binding from 06-02).
10. ROADMAP SC #5 verified: annotated tag `step-06-tests` is NOT applied by this plan (orchestrator-owned per WORK-01 / D-21).
11. NO STATE.md / ROADMAP.md / REQUIREMENTS.md edits in this plan (atomic with orchestrator's tag-apply commit).
12. Human gate passes — user reviews the README + smoke output and approves the tag.
</success_criteria>

<output>
After completion, create `.planning/phases/06-verification-tests/06-06-SUMMARY.md` with:
- Files modified (1: README.md) and verbatim diff of the 3 edits (Step 6 section insertion, Current marker move, optional bullet removal)
- Smoke verification evidence:
  - `docker compose stop rabbitmq` output
  - `mvn -pl integration-tests -am verify` last 30 lines + exit code (0)
  - `grep` outputs for the random-port line and Failsafe summary
- Per-SC verification table:
  - SC #1: PASS / FAIL / DEFERRED + evidence link
  - SC #2: PASS / FAIL / DEFERRED + log line
  - SC #3: PASS (review) — link to OrderFlowIT test 1 + line numbers
  - SC #4: PASS (structural — Failsafe + assertions) / optional sabotage proof
  - SC #5: PENDING ORCHESTRATOR — tag NOT applied by this plan
- Rule-1 deviation note: "Annotated tag `step-06-tests` NOT applied by executor — orchestrator-owned per WORK-01 / D-21 / Phase 2-06 / Phase 5-06 precedent (STATE.md line 90)."
- **Status flip section (for orchestrator's atomic commit, NOT applied here):**
  - .planning/ROADMAP.md: `[ ] **Phase 6: Verification Tests**` → `[x] **Phase 6: Verification Tests** *(shipped YYYY-MM-DD; tag step-06-tests)*`
  - .planning/REQUIREMENTS.md traceability table: TEST-01..TEST-06 status `Pending` → `Complete`
  - .planning/STATE.md: `completed_phases: 5` → `6`; Phase 6 entry under "Recent decisions"; status `Ready for Phase 06` → `Ready for Phase 07`; last_activity update
- Forward-link: orchestrator runs `git tag -a step-06-tests -m "..."` and creates ONE atomic commit including: README delta + 3 planning-doc updates + tag application. After that, `/gsd-plan-phase 7` (Polish & Differentiators) is unblocked.
</output>
