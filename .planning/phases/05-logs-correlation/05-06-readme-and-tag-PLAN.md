---
phase: 05-logs-correlation
plan: 06
type: execute
wave: 4
depends_on:
  - 05-04
  - 05-05
files_modified:
  - README.md
autonomous: false
requirements:
  - LOG-05
  - WORK-01
tags:
  - readme
  - documentation
  - exit-gate
  - human-checkpoint
must_haves:
  truths:
    - id: LOG-05
      description: "README includes the Loki query that demonstrates Loki-to-Tempo click-through"
      verify: "grep -q 'service_name=\"order-producer\"' README.md && grep -q 'Loki' README.md && grep -q 'Tempo' README.md"
    - id: D-20-step-5-section
      description: "README has a '## Step 5: Logs Correlation' H2 heading after the Step 4 section and before 'Reading the code'"
      verify: "grep -q '^## Step 5: Logs Correlation' README.md && awk '/^## Step 4: Metrics/{step4=NR} /^## Step 5: Logs Correlation/{step5=NR} /^## Reading the code/{reading=NR} END{exit !(step4 < step5 && step5 < reading)}' README.md"
    - id: D-20-pitfall-callout
      description: "Step 5 section calls out PITFALL #5 (the @PostConstruct install order)"
      verify: "awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -i 'PITFALL'"
    - id: D-20-mdc-injector-callout
      description: "Step 5 section names the MDC injector wrapper appender (not TurboFilter — RESEARCH §1 correction)"
      verify: "awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -E '(MDC injector|MDC_CONSOLE|opentelemetry-logback-mdc)'"
    - id: D-20-current-marker-moved
      description: "Workshop checkpoints table has '**Current.**' on step-05-logs (not on step-04-metrics). Primary check uses awk-range over the Workshop-checkpoints..Step-4-Metrics block (precise but brittle if README is reorganized in Phase 7); backup check is a structure-independent grep anchor that fails loudly if the README layout changes."
      verify: "awk '/^## Workshop checkpoints/,/^## Step 4: Metrics/' README.md | grep -E 'step-05-logs.*\\*\\*Current\\.\\*\\*|\\*\\*Current\\.\\*\\*.*step-05-logs' && ! awk '/^## Workshop checkpoints/,/^## Step 4: Metrics/' README.md | grep -E 'step-04-metrics.*\\*\\*Current\\.\\*\\*' && grep -F 'step-05-logs' README.md | grep -F '**Current.**'"
    - id: D-20-not-here-yet-updated
      description: "'No log correlation (Phase 5)' bullet REMOVED from 'What's NOT here yet' block"
      verify: "! grep -E 'No log correlation \\(Phase 5\\)' README.md"
    - id: SC1-console-pattern-stamping
      description: "ROADMAP SC #1 verified — console output during POST /orders shows trace_id stamping (smoke test)"
      verify: "echo 'manual-verify — see human-checkpoint task'"
    - id: SC2-loki-click-through
      description: "ROADMAP SC #2 verified — Loki query returns matching log lines, trace_id click-through opens trace in Tempo (manual smoke)"
      verify: "echo 'manual-verify — see human-checkpoint task'"
    - id: SC3-postconstruct-visible
      description: "ROADMAP SC #3 verified — @PostConstruct install method visible in OtelSdkConfiguration.java source"
      verify: "grep -A2 '@PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | grep -q 'OpenTelemetryAppender.install' && grep -A2 '@PostConstruct' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java | grep -q 'OpenTelemetryAppender.install'"
    - id: D-21-tag-deferred-to-orchestrator
      description: "The annotated tag step-05-logs is applied by orchestrator AFTER human gate approves all 4 ROADMAP SCs (do NOT apply tag in this plan; the executor surfaces SC results then waits for orchestrator)"
      verify: "echo 'tag application is orchestrator-owned per WORK-01 / D-21 / Phase 2-06 precedent'"
    - id: D-18-loki-trace-id-driven
      description: "README's Loki query uses the trace_id-driven `|~ \"<traceId>\"` regex match against the OTLP attribute (auto-populated by the OpenTelemetryAppender from Span.current()) — NOT a phrase-driven match. This means log-line wording can vary across services without breaking the query (D-18)."
      verify: "grep -F '|~ \"<traceId>\"' README.md && grep -F 'service_name=\"order-producer\"' README.md"
  artifacts:
    - path: README.md
      provides: "Step 5: Logs Correlation section + workshop checkpoints update + removed 'No log correlation' bullet"
      contains: "## Step 5: Logs Correlation"
      contains: "step-05-logs"
  key_links:
    - from: README.md "Step 5: Logs Correlation" section
      to: producer-service/.../OtelSdkConfiguration.java + producer-service/src/main/resources/logback-spring.xml
      via: "Markdown link with `./path/to/file` syntax (matches Step 4 section style)"
      pattern: "\\[`OtelSdkConfiguration"
    - from: README.md
      to: Loki query that workshop attendee runs in Grafana
      via: "Code-fenced query block + step-by-step instructions"
      pattern: '\\{service_name="order-producer"\\}'
---

<objective>
Add a Markdown "## Step 5: Logs Correlation" section to README.md mirroring the existing "## Step 4: Metrics" section's shape (D-20). Update the workshop checkpoints table to mark step-05-logs as **Current.** (and remove the same marker from step-04-metrics). Remove the "No log correlation (Phase 5)" bullet from the "What's NOT here yet" block. Then run a smoke verification of all 4 ROADMAP success criteria against the live stack (HUMAN CHECKPOINT — pause for user approval before suggesting tag application; the orchestrator applies the annotated tag `step-05-logs` per WORK-01 / D-21 after the human gate).

Purpose: Phase 5's exit gate. Three deliverables: documentation (LOG-05 README walkthrough), live verification (4 ROADMAP SCs), and the annotated tag (WORK-01). The tag itself is orchestrator-applied per the Phase 2/3/4 precedent — this plan delivers the source artifacts and the verified-green state, then surfaces results for the human gate.

Output: README.md modified (~50 lines added for the Step 5 section, 1 line marker move, 1 line bullet removal). Smoke-test outputs documented in the SUMMARY. Tag is NOT applied by this plan.

Why this is wave 4: requires Plans 05-02..05-05 to all be live (so the smoke test can verify the full pipeline end-to-end).

Why this plan has a checkpoint: the smoke test verifies behavior against a live infra stack (otel-lgtm + RabbitMQ from docker-compose); failures could indicate bugs not visible from the source diff alone (logback config typo, FQCN mismatch, install ordering issue per PITFALL #5). A human must look at Grafana to verify the Loki click-through to Tempo (SC #2 explicitly requires manual UI inspection — there is no programmatic substitute for "user sees Tempo trace open with matching trace_id").
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
@.planning/phases/05-logs-correlation/05-01-SUMMARY.md
@.planning/phases/05-logs-correlation/05-02-SUMMARY.md
@.planning/phases/05-logs-correlation/05-03-SUMMARY.md
@.planning/phases/05-logs-correlation/05-04-SUMMARY.md
@.planning/phases/05-logs-correlation/05-05-SUMMARY.md
@README.md
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
@consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
@producer-service/src/main/resources/logback-spring.xml
@consumer-service/src/main/resources/logback-spring.xml

<interfaces>
<!-- Existing README "Step 4: Metrics" section (lines 79-125) — the analog to mirror per PATTERNS §H. -->
<!-- The Phase 5 section's structure must parallel Step 4 closely: opening paragraph + framing sentence + bulleted list of features + closing paragraph with demo command + queries. -->

README anchor points (current state):
- Line 73: `- step-04-metrics — ... **Current.**` (Workshop checkpoints table — `**Current.**` marker is on step-04-metrics; must move to step-05-logs)
- Line 74: `- step-05-logs — (Phase 5) Logs correlation + Loki-to-Tempo click-through.` (currently has "(Phase 5)" parenthetical; should keep this line but update marker per Step 4 precedent)
- Line 79-125: Step 4 section to mirror
- Line 127: `## Reading the code` (insertion point — Step 5 section lands BEFORE this)
- Line 156-163: "What's NOT here yet" block
- Line 161: `- No log correlation (Phase 5)` — REMOVE this line

Loki query format (D-18 / SC #2): `{service_name="order-producer"} |~ "<traceId>"` — the `|~` is regex match against any field containing the trace_id substring.

Mise tasks available for smoke (verify with `mise tasks` — should include): `infra:up`, `dev`, `dev:producer`, `dev:consumer`, `demo:order`.

Grafana access: http://localhost:3000 (admin/admin per docker-compose.yml; otel-lgtm bundles Loki + Tempo + Mimir + Grafana).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Add the Step 5: Logs Correlation README section + update checkpoints marker + remove obsolete bullet</name>
  <files>README.md</files>
  <read_first>
    - README.md (full file — pay attention to lines 70-75 workshop checkpoints, lines 79-125 Step 4 analog, lines 127-onwards Reading the code, lines 156-163 What's NOT here yet)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-20 — README delta scope: name third pipeline + appender + MDC injector, PITFALL #5 callout, Loki query for happy + error path, three-signals close)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§H — existing Step 4 section's exact anatomy and shape conventions)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (Finding #1 — MDC injector is appender wrapper not TurboFilter; the README must use accurate language; Finding #2 — 1000-event replay buffer is a teaching nuance)
  </read_first>
  <action>
Apply three edits to README.md.

**EDIT 1 — Update the workshop checkpoints table marker.** Currently lines 73-74:
```
- step-04-metrics — ... `orders.queue.depth.estimate` (ObservableGauge) flow to Mimir on a 10-second interval. **Current.**
- step-05-logs — (Phase 5) Logs correlation + Loki-to-Tempo click-through.
```

Change to:
```
- step-04-metrics — ... `orders.queue.depth.estimate` (ObservableGauge) flow to Mimir on a 10-second interval.
- step-05-logs — Logs correlation + Loki-to-Tempo click-through. **Current.**
```

(Move `**Current.**` from end of line 73 to end of line 74; remove the `(Phase 5)` parenthetical from line 74 — matches how Phase 4 turned `(Phase 4)` into Current per PATTERNS §H final bullet.)

**EDIT 2 — Remove the obsolete bullet from "What's NOT here yet".** Currently line 161 is:
```
- No log correlation (Phase 5)
```
Delete this entire line.

After deletion, the "What's NOT here yet" block should read (lines 156-163, now 7 lines instead of 8):
```
## What's NOT here yet

The following are deliberate Phase 1 omissions — the repo isn't incomplete, it's **uninstrumented on purpose** so each later phase has something concrete to add:

- No `OtelSdkConfiguration.java` (Phase 2)
- No integration tests (Phase 6)
- No pre-built Grafana dashboard or load script (Phase 7)
```

(Note: the "No `OtelSdkConfiguration.java` (Phase 2)" bullet should ALSO have been removed when Phase 2 shipped — but it wasn't, per current state. That's pre-existing tech debt; do NOT remove it in this plan as it's out of scope. Only remove the "No log correlation" line per D-20 / Phase 5 scope.)

**EDIT 3 — Insert the Step 5: Logs Correlation section.** Insert AFTER the Step 4 section's closing paragraph (currently ends at line 125 with `correlation in Grafana).`) and BEFORE `## Reading the code` (currently line 127). Add a blank line above and below the new section.

The new section (paste verbatim):

```markdown

## Step 5: Logs Correlation

`step-05-logs` adds the **third** OTel signal — logs — to both services, closing
the three-signals loop. The two `OtelSdkConfiguration.java` files now build a
`SdkLoggerProvider` next to the existing `SdkTracerProvider` and `SdkMeterProvider`
(D-01 in `05-CONTEXT.md` lands a third sibling helper `buildLoggerProvider(Resource)`,
parallel to the Phase 2 and Phase 4 helpers, so the diff against `step-04-metrics`
reads as "we added a sibling pipeline next to the trace and metric pipelines"). A
new `logback-spring.xml` per service declares the `OpenTelemetryAppender` for OTLP
export plus the MDC injector wrapper appender that stamps `trace_id`/`span_id`
into the console pattern.

The three Phase 5 SDK + Logback touch points cover the two ways trace context
flows into a log line:

- **`SdkLoggerProvider` + `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter`** —
  the third pipeline added to
  [`OtelSdkConfiguration.java`](./producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java)
  (and its consumer mirror at
  [`consumer-service/.../OtelSdkConfiguration.java`](./consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java)).
  Same OTLP endpoint as traces and metrics (`:4317`, `OTEL_EXPORTER_OTLP_ENDPOINT`
  env var with fallback — D-04 carryforward); same shared `Resource` for cross-signal
  correlation (D-05 — same `service.name` / `service.namespace` / `service.instance.id`
  attributes appear on every log record, every span, every metric data point).
  The `opentelemetry-exporter-otlp` artifact already on classpath since Phase 2
  ships log + metric + span exporters from one jar — Phase 5 adds **zero** new
  SDK-side dependencies.
- **`OpenTelemetryAppender` (OTLP export) + the MDC injector wrapper** — declared
  in [`producer-service/src/main/resources/logback-spring.xml`](./producer-service/src/main/resources/logback-spring.xml)
  (and its byte-identical consumer mirror at
  [`consumer-service/src/main/resources/logback-spring.xml`](./consumer-service/src/main/resources/logback-spring.xml)).
  Two artifacts pulled from the `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha`
  BOM that Phase 1 declared forward-compat for this exact moment:
  `opentelemetry-logback-appender-1.0` (the OTLP export appender) and
  `opentelemetry-logback-mdc-1.0` (the MDC injector). **Heads-up — both ship a
  class named `OpenTelemetryAppender` in different packages**: the
  `appender.v1_0.OpenTelemetryAppender` is the OTLP exporter (has the `install()`
  static); the `mdc.v1_0.OpenTelemetryAppender` is an appender WRAPPER that reads
  `Span.current()` and stamps `trace_id`/`span_id` into MDC before forwarding to
  its child appender. The MDC injector is wrapped around `CONSOLE` so the
  bracketed pattern `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]`
  resolves correctly for in-span events.
- **`@PostConstruct installLogbackAppender()`** — the load-bearing PITFALL #5
  mitigation (LOG-03 / D-08 / D-09). **The order-of-operations problem:** Logback
  initializes BEFORE the Spring `ApplicationContext` is built, so the
  `OpenTelemetryAppender` constructed at startup defaults to `OpenTelemetry.noop()`.
  The `@PostConstruct` method on `OtelSdkConfiguration` runs AFTER the `@Bean`
  factory returns, giving Spring a guaranteed point to call
  `OpenTelemetryAppender.install(this.openTelemetry)` — which walks the global
  `LoggerContext`, finds the OTEL appender, and swaps the noop reference for the
  real SDK. Logs emitted before this method runs are buffered in the appender's
  replay queue (1000-event default) and replayed on install — so nothing is lost
  in normal Spring Boot startup, but if `install()` is never called, log records
  beyond the buffer are silently dropped. **The `@PostConstruct` IS the lesson** —
  the silent-no-op trap is a textbook OTel logback gotcha (see
  [opentelemetry-java-instrumentation#10307](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307)).

`mise run demo:order` now produces a console line per service with `trace_id` /
`span_id` stamped in brackets — pre-`POST` startup logs render
`[trace_id= span_id=]` (empty defaults via Logback's `%mdc{key:-}` syntax),
in-span logs render `[trace_id=4b2e... span_id=ad12...]`. The same trace_id
flows to Loki via the OTLP appender. In Grafana → Explore → Loki, run:

```
{service_name="order-producer"} |~ "<traceId>"
```

(replace `<traceId>` with the 32-hex value you copied from the console). Click
the `trace_id` field on a returned log line and Grafana opens the matching trace
in Tempo's Explore tab. **The triple-signal correlation highlight** lands on the
deterministic 10th order (Phase 3 APP-04): the consumer's `LOG.error` in
[`ProcessingService`](./consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java)
fires alongside the existing `span.recordException(e)` — the Loki query
`{service_name="order-consumer"} |= "ERROR"` returns the failure log; click
its trace_id and Tempo shows the trace whose CONSUMER span carries the
recordException event AND a metric data point in Mimir for the same priority/method.
All three signals share the trace_id; the resource attributes (D-05) make the
identity match across Loki / Tempo / Mimir.
```

**Important formatting checks for the new section:**
- The H2 heading text is exactly `## Step 5: Logs Correlation`
- The opening paragraph references `step-05-logs` (the tag name)
- The bulleted list has 3 items (one per touch point)
- The Loki query is in a code-fenced block (no language tag, matching Step 4's plain-text fence style)
- The closing paragraph names "triple-signal correlation highlight" as the failure-path teaching moment
- All file links use `./path/to/file` repo-relative form (per PATTERNS §H)
- `**bold**` emphasis on key teaching moments: `**third**`, `**zero**`, `**Heads-up**`, `**The order-of-operations problem:**`, `**The `@PostConstruct` IS the lesson**`, `**The triple-signal correlation highlight**`

DO NOT:
- Reference a Logback "TurboFilter" (RESEARCH §1 correction — the MDC injector is an appender WRAPPER)
- Promise screenshots / Grafana panel data-link configuration (Phase 7 owns DOC-04 + WORK-02)
- Add a new H2 outside the Step 5 section (single section delivery per D-20)
- Write the full README walkthrough body (Phase 7 owns DOC-01)
- Modify Step 4's section content (out of scope — only the **Current.** marker moves OFF Step 4)
- Add per-service application.properties / mise.toml change instructions (no application config changed in Phase 5; only the new logback-spring.xml file and the SDK code)
- Reference the SDK Logger API directly (D-07 — workshop pattern is SLF4J via the appender; "no Logger @Bean" is the explicit choice)
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q '^## Step 5: Logs Correlation' README.md && awk '/^## Step 4: Metrics/{step4=NR} /^## Step 5: Logs Correlation/{step5=NR} /^## Reading the code/{reading=NR} END{exit !(step4 < step5 && step5 < reading)}' README.md && awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -i 'PITFALL' && awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -E '(MDC injector|MDC_CONSOLE|opentelemetry-logback-mdc)' && awk '/^## Workshop checkpoints/,/^## Step 4: Metrics/' README.md | grep -E 'step-05-logs.*\\*\\*Current\\.\\*\\*' && (! awk '/^## Workshop checkpoints/,/^## Step 4: Metrics/' README.md | grep -E 'step-04-metrics.*\\*\\*Current\\.\\*\\*') && (! grep -E 'No log correlation \\(Phase 5\\)' README.md) && grep -F '{service_name="order-producer"}' README.md && grep -q '|~ "<traceId>"' README.md && grep -q 'step-05-logs' README.md && (! grep -i 'turboFilter' README.md)</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q '^## Step 5: Logs Correlation' README.md` returns 0
    - Step 5 section is positioned between Step 4 and Reading the code: awk script verifies `step4 < step5 < reading_the_code`
    - Step 5 section mentions PITFALL: `awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -i 'PITFALL'` returns 0
    - Step 5 section names the MDC injector accurately: `awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -E '(MDC injector|MDC_CONSOLE|opentelemetry-logback-mdc)'` returns 0
    - Step 5 section does NOT use the word "TurboFilter" (RESEARCH §1 correction): `! grep -i 'turboFilter' README.md` (anywhere in README — the entire repo should use accurate language)
    - Workshop checkpoints table has `**Current.**` on step-05-logs line (not step-04-metrics):
      - `awk '/^## Workshop checkpoints/,/^## Step 4: Metrics/' README.md | grep -E 'step-05-logs.*\*\*Current\.\*\*'` returns 0 (precise primary check — anchored to current README layout)
      - `! awk '/^## Workshop checkpoints/,/^## Step 4: Metrics/' README.md | grep -E 'step-04-metrics.*\*\*Current\.\*\*'`
      - Structure-independent backup anchor: `grep -F 'step-05-logs' README.md | grep -F '**Current.**'` returns 0 (catches the case where Phase 7 reorganises the README and the awk-range loses its endpoint — the backup grep fails loudly if step-05-logs ever loses its **Current.** marker, regardless of section structure)
    - "No log correlation (Phase 5)" bullet REMOVED: `! grep -E 'No log correlation \(Phase 5\)' README.md`
    - Loki query present: `grep -F '{service_name="order-producer"}' README.md` returns 0
    - Loki regex syntax visible: `grep -q '|~' README.md` returns 0
    - File links to source code with correct paths:
      - `grep -q '\[\`OtelSdkConfiguration\`.*\.java\]' README.md` returns 0
      - `grep -q '\[\`producer-service/src/main/resources/logback-spring\.xml\`\]' README.md` returns 0
    - Three-signals close mentioned: `awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | grep -E '(three.signal|triple.signal|three signals)'` returns 0
    - Section length is between 35 and 70 lines (mirrors Step 4's 47 lines): `awk '/^## Step 5: Logs Correlation/,/^## Reading the code/' README.md | wc -l` falls within range
  </acceptance_criteria>
  <done>
README.md has the new Step 5: Logs Correlation section in the correct position, with PITFALL #5 callout, MDC injector accurate naming (NOT TurboFilter), and the Loki query. The Workshop checkpoints **Current.** marker is moved from step-04-metrics to step-05-logs. The "No log correlation (Phase 5)" bullet is removed.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 2: Smoke verification of all 4 ROADMAP success criteria + tag application gate</name>
  <what-built>
The full Phase 5 source delta from Plans 05-01..05-05 + the README update from Plan 05-06 Task 1:
- `producer-service/pom.xml` and `consumer-service/pom.xml` have the two new BOM-managed deps (`opentelemetry-logback-appender-1.0` + `opentelemetry-logback-mdc-1.0`)
- Both `OtelSdkConfiguration.java` files have `buildLoggerProvider`, `setLoggerProvider` chain, `@PostConstruct installLogbackAppender()` calling `OpenTelemetryAppender.install(this.openTelemetry)`, and JavaDoc fixed for D-07 (no Logger @Bean)
- Both `logback-spring.xml` files exist (byte-identical) with CONSOLE + MDC_CONSOLE wrapper + OTEL appender
- `OrderController.java` has LOG.info inside `create(...)`; `OrderPublisher.java` has LOG.info inside `publish(...)` before convertAndSend
- `ProcessingService.java` has LOG.error in the catch block before recordException
- README.md has the Step 5 section + Current. marker on step-05-logs + log-correlation bullet removed
  </what-built>
  <how-to-verify>
**Pre-flight (executor runs this):**

1. From repo root:
   ```bash
   mvn -B -q clean compile
   # Must exit 0 — full reactor compiles
   ```

2. Verify the 4 ROADMAP SC #3 source-grep gate (passes from grep alone, no app start needed):
   ```bash
   grep -A2 '@PostConstruct' producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java | grep 'OpenTelemetryAppender.install'
   grep -A2 '@PostConstruct' consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java | grep 'OpenTelemetryAppender.install'
   # Both must match — proves SC #3 (`@PostConstruct` install order visible in code)
   ```

3. Bring up infra and apps (executor runs these and pauses for the human gate):
   ```bash
   mise run infra:up           # starts rabbitmq + grafana/otel-lgtm
   # Wait for healthchecks (~15s) — verify with `docker compose ps` until both healthy
   mise run dev > /tmp/phase5-smoke.log 2>&1 &
   # Wait for both Spring Boot apps to log "Started" (~20s)
   ```

4. Smoke check the startup output for Logback class-loading errors (RESEARCH Risk #2):
   ```bash
   grep -i 'Failed to instantiate\|unable to find class' /tmp/phase5-smoke.log
   # Must return NOTHING — proves no FQCN typos in logback-spring.xml
   ```

5. Issue 11 orders to trigger the deterministic-failure path on the 10th:
   ```bash
   for i in $(seq 1 11); do mise run demo:order; sleep 1; done
   ```

6. Verify SC #1 (console pattern stamping) by grepping the smoke log:
   ```bash
   grep -E '\[trace_id=[a-f0-9]{32} span_id=[a-f0-9]{16}\]' /tmp/phase5-smoke.log
   # Must match at least 4 lines (1 controller + 1 publisher + 1 listener happy + 1 consumer error)
   # Each must show a NON-EMPTY 32-hex trace_id and 16-hex span_id
   ```

7. Capture a sample trace_id from the smoke log for the manual verification:
   ```bash
   TRACE_ID=$(grep -oE '\[trace_id=[a-f0-9]{32}' /tmp/phase5-smoke.log | head -1 | sed 's/\[trace_id=//')
   echo "TRACE_ID=$TRACE_ID"
   # The user will paste this into Grafana
   ```

**Human verification (user runs these in browser):**

8. Open Grafana: http://localhost:3000 (admin/admin)

9. **SC #2 step 1 — Loki query returns matches:**
   - Click the Explore icon in the left sidebar
   - Select the **Loki** data source from the dropdown at the top
   - In the query bar paste:
     ```
     {service_name="order-producer"} |~ "$TRACE_ID"
     ```
     (replace `$TRACE_ID` with the actual hex value from step 7)
   - Set the time range to "Last 5 minutes"
   - Click **Run query**
   - Expected: at least 2 log lines (controller + publisher) for that trace_id

10. **SC #2 step 2 — Click trace_id, land in Tempo:**
    - Click on a returned log line to expand it
    - Find the `trace_id` field in the expanded JSON
    - Click the trace_id link (Grafana provisions a Loki→Tempo data link in otel-lgtm)
    - Expected: a Tempo span panel opens in the right pane showing the matching trace
    - The trace should contain SERVER + INTERNAL + PRODUCER spans (producer side) + CONSUMER + INTERNAL spans (consumer side, from Phase 3 propagation)

11. **SC #2 step 3 — Failure-path correlation (Phase 5 highlight):**
    - In Loki query bar paste:
      ```
      {service_name="order-consumer"} |= "ERROR"
      ```
    - Run query
    - Expected: at least 1 log line on the deterministic 10th-order failure (the LOG.error from Plan 05-05)
    - Click that line, click the trace_id field
    - Expected: Tempo opens the trace; the CONSUMER span shows ERROR status with the recordException event attached (Phase 3 TRACE-09 + Phase 5 D-16 working together)

12. **SC #4 (tag exists) — DEFERRED to orchestrator** per WORK-01 / D-21 / Phase 2-06 precedent. Do NOT apply the tag in this plan. After human approves SCs #1-3 (and notes SC #2 manual click-through worked), the orchestrator's exit-gate flow will:
    - Apply the annotated git tag `step-05-logs` on `main`
    - Update `.planning/STATE.md` to mark Phase 5 SHIPPED
    - Update `.planning/ROADMAP.md` to flip the [ ] checkbox on Phase 5

**Cleanup (executor runs after human approval):**

13. Tear down (regardless of outcome):
    ```bash
    pkill -f 'spring-boot:run' || true
    mise run infra:down
    rm -f /tmp/phase5-smoke.log
    ```

**Failure modes — if the human verifies SC #2 fails:**

- Console shows `[trace_id= span_id=]` (empty) for in-span events: MDC_CONSOLE wrapper isn't running OR doesn't wrap CONSOLE — re-check Plan 05-02/05-03 logback-spring.xml `<appender-ref>` wiring (MDC_CONSOLE must wrap CONSOLE; root must reference MDC_CONSOLE not bare CONSOLE).
- Loki query returns no matches: OTEL appender isn't exporting — likely PITFALL #5 (`install()` ran but the appender FQCN is wrong, or `install()` never ran). Check `grep -A2 '@PostConstruct' OtelSdkConfiguration.java` shows the install call.
- Loki returns matches but Tempo trace doesn't open: Grafana data link config issue (out of scope — Phase 7 owns DOC-04). For now: copy the trace_id manually and paste into the Tempo Explore tab.
- LOG.error on the 10th order doesn't appear: counter ordering issue or the 10 orders ran across two app restarts (counter resets per JVM run). Re-run `for i in $(seq 1 11); do mise run demo:order; done` in one batch.

If any of the failure modes above surface, the executor should DOCUMENT the symptom in the gate response (what was attempted, what the symptom was, what the likely root cause is). The human gate decides whether to push through (rare — only if the symptom is environmental, e.g. otel-lgtm not yet healthy) or to revise (typical — return to the responsible plan and fix the source defect).
  </how-to-verify>
  <resume-signal>
Type "approved" once all four ROADMAP success criteria are verified green at the live stack:
1. Console pattern stamping (executor's automated grep on smoke log + your eyeball on a few of those lines) — green
2. Loki click-through to Tempo — you confirm by clicking trace_id in Loki and landing on the matching trace in Tempo
3. @PostConstruct install method visible in source — executor's grep already passed
4. step-05-logs tag — DEFERRED, orchestrator applies after this gate

The orchestrator will then:
- Apply the annotated git tag `step-05-logs` (per WORK-01 + D-21 + Phase 2-06/3-05/4-05 precedent)
- Atomically flip Phase 5 status in `.planning/STATE.md` and `.planning/ROADMAP.md` to SHIPPED in the same commit

If any SC fails, describe the symptom and which Plan (05-01..05-06) is the suspected source — the orchestrator routes to revision mode for that plan.
  </resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| README documentation surface | Public-readable artifact. Workshop attendees clone the repo and read the README to understand the demo. Misinformation in the README would teach incorrect patterns. The PITFALL #5 callout + MDC injector accurate naming are load-bearing teaching surface. |
| Smoke test interaction with otel-lgtm + RabbitMQ | Workshop-local, loopback-only. Smoke runs on the developer's laptop. No production-data risk. |
| Annotated git tag (orchestrator-applied, not in this plan) | Immutable workshop checkpoint per WORK-01. Once applied, attendees `git checkout step-05-logs` to time-travel. The tag is the load-bearing reproducibility artifact. |

## STRIDE Threat Register (ASVS L1, security_enforcement: enabled, block-on: high)

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-06-01 | Information Disclosure | README mentions Loki query syntax with placeholder trace_id | accept | The example query uses `<traceId>` as a placeholder — workshop attendees substitute their own value at runtime. No hardcoded credentials or production data. |
| T-05-06-02 | Tampering | README pedagogical accuracy | mitigate | The acceptance criteria explicitly check that "TurboFilter" does NOT appear in README (RESEARCH §1 correction enforced). Workshop attendees who read this section will learn the correct mental model from day one. |
| T-05-06-03 | Information Disclosure | smoke test exposes log lines on developer console | accept | Smoke runs on the developer's laptop with synthetic POSTs (`mise run demo:order`). Logs do NOT contain production data. |
| T-05-06-04 | Repudiation | git tag immutability (orchestrator concern) | accept | Tag application is orchestrator-owned, not this plan's scope. Tag is annotated (not lightweight) — carries a meaningful message that survives `git push --tags`. Tag is on `main` and is immutable per WORK-01 (Phases 1/2/3/4 set this precedent). |
| T-05-06-05 | Denial of Service | smoke startup time | accept | `mise run dev` takes ~20s; otel-lgtm takes ~15s. Smoke gate is a one-time exit check, not an automated CI run. Acceptable. |
| T-05-06-06 | Spoofing | trace_id captured in smoke log might mismatch | mitigate | The smoke instructions explicitly say "copy the trace_id from the SMOKE LOG and paste into Grafana" — eliminates the possibility of pasting an old trace_id from a different run. |
</threat_model>

<verification>
- README.md has the Step 5: Logs Correlation section in the correct position (between Step 4 and Reading the code)
- Section text mentions "PITFALL #5", names the MDC injector accurately, and includes the Loki query with `{service_name="order-producer"}`
- Section does NOT use the word "TurboFilter" anywhere
- Workshop checkpoints table has `**Current.**` marker on step-05-logs (NOT step-04-metrics)
- "What's NOT here yet" block does NOT contain "No log correlation (Phase 5)"
- ROADMAP SC #3 source-grep passes (both OtelSdkConfiguration.java files have @PostConstruct + OpenTelemetryAppender.install)
- ROADMAP SC #1 — console pattern stamping — verified manually by executor grepping smoke log + human eyeball
- ROADMAP SC #2 — Loki click-through to Tempo — verified manually by HUMAN in Grafana UI (executor cannot self-verify; this is the load-bearing checkpoint)
- ROADMAP SC #4 — tag exists — DEFERRED to orchestrator (this plan does NOT apply the tag)
- Reactor compiles cleanly: `mvn clean compile` exits 0
- No "Failed to instantiate" / "unable to find class" warnings in Spring Boot startup logs (Risk #2 mitigated)
</verification>

<success_criteria>
1. README.md Step 5: Logs Correlation section exists between Step 4 and Reading the code, with all D-20 elements (third pipeline naming, MDC injector accurate naming, PITFALL #5 callout, Loki query, three-signals close).
2. Workshop checkpoints `**Current.**` marker moved from step-04-metrics to step-05-logs.
3. "No log correlation (Phase 5)" bullet removed from "What's NOT here yet".
4. README does NOT contain the word "TurboFilter" (RESEARCH §1 correction enforced).
5. Reactor compiles cleanly (`mvn clean compile` exits 0).
6. No Logback class-loading errors at startup (Risk #2: smoke check on `/tmp/phase5-smoke.log` for "Failed to instantiate" / "unable to find class").
7. Console pattern stamps non-empty trace_id/span_id for in-span log lines (SC #1 — at least 4 such lines from one POST /orders).
8. Loki query returns matching log lines and clicking trace_id opens the matching trace in Tempo (SC #2 — HUMAN verifies in Grafana).
9. @PostConstruct install method visible in BOTH OtelSdkConfiguration.java files (SC #3 — automated grep).
10. Tag application is DEFERRED to orchestrator per WORK-01 / D-21 (this plan does NOT apply the tag; orchestrator atomically applies tag + flips STATE/ROADMAP after human approval).
</success_criteria>

<output>
After completion, create `.planning/phases/05-logs-correlation/05-06-SUMMARY.md` with:
- README.md diff (the new Step 5 section + the marker move + the bullet removal)
- Smoke test outputs:
  - `mvn clean compile` exit code (must be 0)
  - `grep "Failed to instantiate" /tmp/phase5-smoke.log` output (must be empty)
  - `grep -cE '\[trace_id=[a-f0-9]{32}' /tmp/phase5-smoke.log` count (must be ≥ 4)
  - The TRACE_ID captured for human verification
- Human gate notes:
  - Whether SC #2 manual verification passed (user click-through Loki → Tempo)
  - Whether the failure-path triple-signal correlation visible (Plan 05-05 highlight)
  - Any unexpected behavior observed
- Tag handover note: "Tag step-05-logs application is DEFERRED to orchestrator per WORK-01 + D-21 + Phase 2-06/3-05/4-05 precedent. STATE.md and ROADMAP.md flips for Phase 5 SHIPPED status will land atomically with the tag-apply commit."
- Forward-link: this is the LAST plan of Phase 5; Phase 6 (Verification Tests) starts after Phase 5 ships.
</output>
