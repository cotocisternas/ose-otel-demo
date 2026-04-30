---
id: 1-02-mise-toolchain
phase: 1-baseline-scaffold
plan: 02
type: execute
wave: 1
depends_on: []
requirements: [INFRA-02, INFRA-03, INFRA-05]
files_modified:
  - mise.toml
  - .tool-versions
autonomous: true
must_haves:
  truths:
    - "mise.toml pins java to corretto-17.0.13.11.1 (exact patch — not floating corretto-17) and maven to 3.9.11"
    - ".tool-versions companion file kept in sync with mise.toml (read by IntelliJ asdf-compat auto-detection per Pitfall C)"
    - "mise run preflight exits 0 on a clean machine when Docker is up, ports 3000/4317/4318/5672/15672 are free, Java 17 is active, Maven 3.9.x is active"
    - "mise run preflight exits non-zero with a clear error message when ANY of the five ports is occupied or Docker is down"
    - "mise run dev launches dev:producer and dev:consumer in PARALLEL using mise's first-class { tasks = [...] } syntax (NOT shell & plumbing) and Ctrl-C stops both"
    - "mise run infra:up uses 'docker compose up -d --wait'; mise run infra:down uses plain 'docker compose down' (NO -v flag — preserves lgtm-data volume per Pitfall D)"
    - "mise run infra:reset is the explicit DESTRUCTIVE task that runs 'docker compose down -v' (separate from infra:down)"
    - "mise run verify:bom encodes the Phase 1 success gate: mvn dependency:tree -Dincludes=io.opentelemetry returns zero matches"
  artifacts:
    - path: "mise.toml"
      provides: "Toolchain pin + env vars + task graph (preflight, infra:up/down/reset/logs, build, test, dev/dev:producer/dev:consumer, demo:order, verify:bom, ui:grafana, ui:rabbitmq)"
      contains: "corretto-17.0.13.11.1"
    - path: ".tool-versions"
      provides: "asdf-compatible companion to mise.toml; IntelliJ auto-detects this for Project SDK"
      contains: "java corretto-17.0.13.11.1"
  key_links:
    - from: "mise.toml [tasks.dev]"
      to: "mise.toml [tasks.\"dev:producer\"] + [tasks.\"dev:consumer\"]"
      via: "{ tasks = [\"dev:producer\", \"dev:consumer\"] } parallel run-array entry"
      pattern: "tasks = \\[\"dev:producer\""
    - from: "mise.toml [tasks.\"dev:producer\"] + [tasks.\"dev:consumer\"]"
      to: "docker-compose.yml services rabbitmq + lgtm"
      via: "depends = [\"infra:up\"] which runs 'docker compose up -d --wait'"
      pattern: "depends = \\[\"infra:up\"\\]"
    - from: "mise.toml [env]"
      to: "Spring Boot autoconfiguration"
      via: "SPRING_RABBITMQ_* env vars consumed by Spring Boot's RabbitAutoConfiguration"
      pattern: "SPRING_RABBITMQ_HOST"
---

<objective>
Pin the toolchain (Amazon Corretto 17.0.13.11.1 + Maven 3.9.11) via mise and define the complete Phase 1 task graph: preflight (port + tool checks), infra:up/down/reset/logs, build/test, dev/dev:producer/dev:consumer with parallel execution, demo:order, verify:bom (Phase 1 exit gate), ui:grafana, ui:rabbitmq. Commit BOTH `mise.toml` (mise primary) and `.tool-versions` (IntelliJ asdf-compat companion) — Pitfall C requires the dual-file approach so attendees who don't install the JetBrains Mise plugin still get JDK auto-detection.

Purpose: One-command operation for the workshop attendee. INFRA-02 (toolchain), INFRA-03 (preflight), INFRA-05 (parallel dev tasks). Pre-emptively neutralises pitfalls B (port collisions surface in preflight), C (mise+IDE mismatch via .tool-versions), D (no -v in infra:down).
Output: 2 files (mise.toml + .tool-versions). After `mise install` and `mise run preflight`, the attendee's machine is workshop-ready.
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

<task id="1-02-T1" type="auto">
  <name>Task 1: Write mise.toml with [tools], [env], and complete task graph</name>
  <files>mise.toml</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 642-786 — complete mise.toml verified template)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 11-13 — flagged-question resolution: corretto-17.0.13.11.1 exact patch pin)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 265-279 — Pattern 2: mise parallel `:::` separator + `{ tasks = [...] }` array form)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 325-352 — Pitfalls B (port collisions) + D (no -v on infra:down))
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 763-776 — verify:bom is THE Phase 1 success gate)
  </read_first>
  <action>
    Create `mise.toml` at the repo root EXACTLY matching RESEARCH.md lines 644-785. Concrete required content, top-to-bottom:

    1. **Header comment** (workshop-style — readers will see this): briefly explain "this file is the source of truth for JDK + Maven versions; .tool-versions is a generated companion for IntelliJ".

    2. **`min_version = "2025.1.0"`** — guards against attendees on ancient mise.

    3. **`[tools]`** — pinned EXACT patches:
       - `java  = "corretto-17.0.13.11.1"` (NOT `corretto-17` — Pitfall C reproducibility)
       - `maven = "3.9.11"`

    4. **`[env]`** block:
       - `SPRING_RABBITMQ_HOST     = "localhost"`
       - `SPRING_RABBITMQ_PORT     = "5672"`
       - `SPRING_RABBITMQ_USERNAME = "guest"`
       - `SPRING_RABBITMQ_PASSWORD = "guest"`
       - `OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"` (set now even though Phase 1 has no OTel libs to consume it — Phase 2 inherits a clean slate)
       - `OTEL_EXPORTER_OTLP_PROTOCOL = "grpc"`
       - `PRODUCER_PORT = "8080"`
       - `CONSUMER_PORT = "8081"`

    5. **`[tasks.preflight]`** — multi-line `run = """..."""` shell script that performs in this order, with `set -e` so any failure aborts:
       - `mise current java` and `mise current maven` (informational)
       - `java -version 2>&1 | head -1 | grep -q '"17'` — fails with `ERROR: Java 17 not active` if regex misses
       - `mvn -version 2>&1 | head -1 | grep -q "Apache Maven 3.9"` — fails with `ERROR: Maven 3.9.x not active` if regex misses
       - `docker info > /dev/null 2>&1` — fails with `ERROR: Docker not running`
       - **Port loop** over `3000 4317 4318 5672 15672`: `if ss -tln 2>/dev/null | grep -q ":${port} "; then echo "ERROR: Port ${port} is in use. Run: lsof -i:${port} to find the process."; exit 1; fi` — green path prints `  ${port}: free`
       - Final line: `echo "Pre-flight: ALL GREEN. Run: mise run infra:up"`

       The exact ports list is **3000, 4317, 4318, 5672, 15672** (NOT including 8080/8081 — those are app ports, attendees can change them; the five fixed ports are infrastructure).

    6. **`[tasks."infra:up"]`** — `description = "Start RabbitMQ + grafana/otel-lgtm in docker-compose"`, `run = "docker compose up -d --wait"`. The `--wait` flag blocks until healthchecks pass (depends on plan 03's docker-compose.yml).

    7. **`[tasks."infra:down"]`** — `description = "Stop infra (preserves Grafana state via lgtm-data volume)"`, `run = "docker compose down"`. NO `-v` flag (Pitfall D).

    8. **`[tasks."infra:reset"]`** — `description = "DESTRUCTIVE: stop infra AND wipe Grafana state"`, `run = "docker compose down -v"`. Separate task name so attendees can't fat-finger it.

    9. **`[tasks."infra:logs"]`** — `description = "Tail infra logs"`, `run = "docker compose logs -f"`.

    10. **`[tasks.build]`** — `run = "mvn -T 1C -DskipTests clean install"`.

    11. **`[tasks.test]`** — `run = "mvn -T 1C verify"`.

    12. **`[tasks."dev:producer"]`** — `depends = ["infra:up"]`, `run = "mvn -pl producer-service spring-boot:run -Dspring-boot.run.jvmArguments=\"-Dserver.port=${PRODUCER_PORT}\""`. The `-Dspring-boot.run.jvmArguments` syntax is mandatory — Spring Boot's plugin forks the JVM and `-Dserver.port` must reach the forked process.

    13. **`[tasks."dev:consumer"]`** — `depends = ["infra:up"]`, `run = "mvn -pl consumer-service spring-boot:run -Dspring-boot.run.jvmArguments=\"-Dserver.port=${CONSUMER_PORT}\""`.

    14. **`[tasks.dev]`** — `description = "Run producer + consumer in parallel (Ctrl-C stops both)"`, `depends = ["infra:up"]`, `run = [ { tasks = ["dev:producer", "dev:consumer"] } ]`. **This is the key parallelism construct**: a run-array containing one entry that is itself a `{ tasks = [...] }` table. mise interprets this as "execute dev:producer and dev:consumer simultaneously"; Ctrl-C propagates to both. DO NOT use shell `&` or `wait` — RESEARCH.md is explicit (lines 269-279) that `:::` and `{ tasks = [...] }` are mise's first-class parallelism primitives.

    15. **`[tasks."demo:order"]`** — `run = "curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders -H 'Content-Type: application/json' -d '{\"sku\":\"WIDGET-1\",\"quantity\":3}' && echo"`.

    16. **`[tasks."verify:bom"]`** — multi-line `run = """..."""` script that asserts zero OTel libs:
        ```
        set -e
        COUNT=$(mvn -q dependency:tree -Dincludes=io.opentelemetry 2>&1 | grep -c "io.opentelemetry" || true)
        if [ "$COUNT" -gt 0 ]; then
          echo "ERROR: OpenTelemetry libraries detected on classpath:"
          mvn dependency:tree -Dincludes=io.opentelemetry
          exit 1
        fi
        echo "Phase 1 baseline confirmed: zero OpenTelemetry libraries on classpath."
        ```

    17. **`[tasks."ui:grafana"]`** — `run = "xdg-open http://localhost:3000 2>/dev/null || open http://localhost:3000"`.

    18. **`[tasks."ui:rabbitmq"]`** — `run = "xdg-open http://localhost:15672 2>/dev/null || open http://localhost:15672"`.

    DO NOT include: any reference to OTel libraries (Phase 1 baseline must be uninstrumented), shell `&` or `wait` plumbing in `[tasks.dev]`, port remapping for Grafana (3000→3001 is broken — Pitfall B). Do NOT pre-create `lgtm-data` directly; the docker-compose.yml will declare it as a named volume.
  </action>
  <acceptance_criteria>
    - `test -f mise.toml` exits 0
    - `grep -c 'corretto-17.0.13.11.1' mise.toml` returns >= 1
    - `grep -c 'maven = "3.9.11"' mise.toml` returns 1
    - `grep -c 'OTEL_EXPORTER_OTLP_ENDPOINT' mise.toml` returns 1
    - `grep -c 'SPRING_RABBITMQ_HOST' mise.toml` returns 1
    - `grep -c '\[tasks.preflight\]' mise.toml` returns 1
    - `grep -c '3000 4317 4318 5672 15672' mise.toml` returns 1 (the exact preflight port list)
    - `grep -c 'docker compose down' mise.toml` returns >= 2 (one for infra:down, one for infra:reset)
    - `grep -c 'docker compose down -v' mise.toml` returns 1 (only infra:reset uses -v)
    - `grep -E '^\s*run\s*=\s*"docker compose down"\s*$' mise.toml | wc -l` returns 1 (infra:down is plain — no -v)
    - `grep -c 'tasks = \["dev:producer", "dev:consumer"\]' mise.toml` returns 1 (parallel syntax, not shell &)
    - `! grep -E 'dev:producer\s*&' mise.toml` exits 0 (no shell-& plumbing for parallelism)
    - `grep -c '\[tasks."verify:bom"\]' mise.toml` returns 1
    - `grep -c 'depends = \["infra:up"\]' mise.toml` returns >= 3 (dev:producer, dev:consumer, dev all depend on infra:up)
    - `grep -c '${PRODUCER_PORT}' mise.toml` returns >= 2
    - `grep -c '${CONSUMER_PORT}' mise.toml` returns >= 1
    - File parses as valid TOML: `python3 -c "import tomllib; tomllib.loads(open('mise.toml').read())"` exits 0 (or `python3 -c "import tomli; tomli.loads(open('mise.toml').read())"` on Python &lt; 3.11)
  </acceptance_criteria>
  <verify>
    <automated>python3 -c "import tomllib,sys; tomllib.loads(open('mise.toml').read())" &amp;&amp; grep -q 'corretto-17.0.13.11.1' mise.toml &amp;&amp; grep -q 'tasks = \["dev:producer", "dev:consumer"\]' mise.toml &amp;&amp; grep -E '^run\s*=\s*"docker compose down"$' mise.toml | grep -q '.'</automated>
  </verify>
  <done>mise.toml exists, valid TOML, pins exact Corretto patch + Maven 3.9.11, declares all 13 named tasks, parallel dev uses { tasks = [...] } not shell &amp;, infra:down has no -v, infra:reset has -v, preflight checks the exact 5 infrastructure ports.</done>
</task>

<task id="1-02-T2" type="auto">
  <name>Task 2: Generate companion .tool-versions (asdf-compat for IntelliJ)</name>
  <files>.tool-versions</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 789-802 — .tool-versions content + IntelliJ rationale)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 336-345 — Pitfall C: mise + IntelliJ JDK detection mismatch)
    - mise.toml (just created — must keep .tool-versions in sync)
  </read_first>
  <action>
    Create `.tool-versions` at the repo root with EXACTLY two lines:
    ```
    java corretto-17.0.13.11.1
    maven 3.9.11
    ```

    No comments, no blank trailing line beyond a single newline at EOF (this is the asdf format — line-by-line `<plugin> <version>`). The versions MUST match `mise.toml` `[tools]` exactly. mise can regenerate this with `mise generate tool-versions > .tool-versions` — the executor MAY use that command if available, but writing the two lines directly is equally valid for Phase 1.

    Why both files: per RESEARCH Pitfall C, `mise.toml` is mise's preferred source of truth (richer with tasks + env), but IntelliJ IDEA's built-in SDK auto-detection reads `.tool-versions` natively (asdf-compatible). Without the second file, attendees who don't install the JetBrains Mise plugin see IntelliJ pick the system JDK (often Java 21 or 26 on a fresh machine) and class files target the wrong version silently. README's "Prerequisites" section (plan 06) will mention both files.
  </action>
  <acceptance_criteria>
    - `test -f .tool-versions` exits 0
    - `wc -l < .tool-versions` returns 2 (exactly two lines)
    - `grep -c '^java corretto-17.0.13.11.1$' .tool-versions` returns 1
    - `grep -c '^maven 3.9.11$' .tool-versions` returns 1
    - Versions match mise.toml: `awk '/^java /{print $2}' .tool-versions` outputs `corretto-17.0.13.11.1` AND `grep -oE 'corretto-17\.0\.13\.11\.1' mise.toml | head -1` outputs `corretto-17.0.13.11.1`
    - Versions match mise.toml: `awk '/^maven /{print $2}' .tool-versions` outputs `3.9.11` AND `grep -oE 'maven = "3\.9\.11"' mise.toml | head -1` outputs `maven = "3.9.11"`
  </acceptance_criteria>
  <verify>
    <automated>test "$(awk '/^java /{print $2}' .tool-versions)" = "corretto-17.0.13.11.1" &amp;&amp; test "$(awk '/^maven /{print $2}' .tool-versions)" = "3.9.11" &amp;&amp; test "$(wc -l &lt; .tool-versions)" = "2"</automated>
  </verify>
  <done>.tool-versions has exactly two lines (`java corretto-17.0.13.11.1` and `maven 3.9.11`), values match mise.toml's [tools] block.</done>
</task>

<task id="1-02-T3" type="auto">
  <name>Task 3: Verify mise install + preflight green-path on the developer machine</name>
  <files>(none — verification only)</files>
  <read_first>
    - mise.toml + .tool-versions (just created)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 1208-1224 — Environment Availability table; mise + docker confirmed present, host Maven absent — `mise install` provides it)
  </read_first>
  <action>
    Run two verifications. This task creates no files — it confirms the prior two tasks shipped a working toolchain pin.

    Step 1: `mise install`. This MUST succeed and download/activate Corretto 17.0.13.11.1 and Maven 3.9.11. Common failure modes:
    - Network failure → retry once; if persistent, mise's mirror is unreachable and the attendee needs investigation (out of plan scope).
    - Plugin ID drift (`corretto-17.0.13.11.1` no longer published) → escalate; the version must be repinned (low risk per RESEARCH Assumption A2).

    Step 2: `mise run preflight`. **This requires Docker to be up and the five ports (3000/4317/4318/5672/15672) to be free.** Plan 03 has not yet started the docker-compose containers, so on first run the ports SHOULD all be free (preflight green) AND `docker info` exits 0 (Docker daemon running on the dev machine — confirmed in RESEARCH Environment Availability).

    Expected green-path output ends with `Pre-flight: ALL GREEN. Run: mise run infra:up`.

    If preflight fails on a port, the executor should NOT mutate `mise.toml` to skip that port — instead, document which port collided and stop. The plan's success criterion is that preflight CAN go green; if the developer machine has port 3000 occupied (e.g., a stray Grafana), they need to free it. The plan exits successfully if preflight is green; if a port is occupied by an unrelated process, the executor should free it manually before declaring done.

    DO NOT run `mise run dev` in this task — that depends on plans 03/04/05 (docker-compose + service skeletons) which haven't shipped yet. This task only proves toolchain + preflight.
  </action>
  <acceptance_criteria>
    - `mise install` exits 0
    - `mise current java | tr -d ' \n'` outputs `corretto-17.0.13.11.1`
    - `mise current maven | tr -d ' \n'` outputs `3.9.11`
    - `mise exec -- java -version 2>&amp;1 | head -1 | grep -q '"17'` exits 0 (active Java is 17, regardless of host system Java version)
    - `mise exec -- mvn -version 2>&amp;1 | head -1 | grep -q 'Apache Maven 3.9'` exits 0
    - `mise run preflight` exits 0 (assumes Docker is up and ports 3000/4317/4318/5672/15672 are free on the dev machine — RESEARCH Environment Availability confirms both)
    - `mise run preflight 2>&amp;1 | tail -1` outputs a line containing `Pre-flight: ALL GREEN`
  </acceptance_criteria>
  <verify>
    <automated>mise install &amp;&amp; mise current java | grep -q corretto-17.0.13.11.1 &amp;&amp; mise current maven | grep -q '3.9.11' &amp;&amp; mise run preflight</automated>
  </verify>
  <done>`mise install` succeeded; `mise current java` reports the exact pinned patch; `mise run preflight` exits green with the "ALL GREEN" footer line.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| External mise registry → local installs | First-run downloads of JDK + Maven binaries to `~/.local/share/mise/installs/` |
| `[env]` block → child processes | Env vars injected into all `mise run <task>` shells; would propagate to mvn/spring-boot/curl |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-1-02-01 | Tampering | Floating mise plugin tag drifts (corretto-17 → corretto-17.0.19.10.1 silently) | mitigate | Pin exact patch `corretto-17.0.13.11.1`; verified via `mise ls-remote java` per RESEARCH lines 11-13 |
| T-1-02-02 | Information Disclosure | RabbitMQ credentials (guest/guest) committed in mise.toml [env] | accept | Default RabbitMQ creds are loopback-only; PROJECT.md scopes auth out; `5672`/`15672` exposed only to localhost via docker-compose port mapping (plan 03). Documented as workshop-only |
| T-1-02-03 | Tampering | mise.toml shell tasks (preflight, verify:bom) executing arbitrary commands on attendee machine | accept | Tasks are reviewable plain text; no curl-pipe-bash; no remote-fetched scripts; commands are standard Linux utilities (ss, grep, docker, mvn) |
| T-1-02-04 | Spoofing | Attendee fooled by stale `.tool-versions` while mise.toml advances | mitigate | Acceptance criteria assert `.tool-versions` matches `mise.toml [tools]` exactly; `mise generate tool-versions` regenerates if drift |
| T-1-02-05 | Denial of Service | preflight's port-loop blocks workshop start if a developer's machine has port 3000 in use | mitigate | preflight's error message tells the user `lsof -i:<port>` to find the conflicting process — actionable, not a soft-fail |

**Phase scope:** Workshop scaffold — no Internet exposure, no persistence, no secrets beyond the documented loopback RabbitMQ default. mise tasks run with the attendee's user shell privileges; no escalation surface introduced. Out of scope: TLS, signed task definitions, signed JDK provenance.
</threat_model>

<verification>
- `mise install` exits 0 and provisions Corretto 17.0.13.11.1 + Maven 3.9.11.
- `mise current java` outputs `corretto-17.0.13.11.1`; `mise current maven` outputs `3.9.11`.
- `mise run preflight` exits 0 on a clean dev machine (Docker up; ports free).
- `mise.toml [tasks.dev]` uses `{ tasks = ["dev:producer", "dev:consumer"] }` — verified by `grep -c 'tasks = \["dev:producer"' mise.toml` returning 1.
- `mise.toml [tasks."infra:down"]` runs `docker compose down` (no -v) — verified by `grep -E '^run\s*=\s*"docker compose down"$' mise.toml | wc -l` returning 1.
- `.tool-versions` content matches `mise.toml [tools]` line-for-line.
</verification>

<success_criteria>
- INFRA-02 satisfied: committed `mise.toml` and `.tool-versions` provision Corretto 17 + Maven 3.9 automatically; both files in sync.
- INFRA-03 satisfied: `mise run preflight` validates Docker, the 5 infrastructure ports, Java 17, Maven 3.9 — exits non-zero on any failure with actionable error text.
- INFRA-05 satisfied: `mise run dev` (parallel via `{ tasks = [...] }`) launches dev:producer + dev:consumer simultaneously, both with `SPRING_RABBITMQ_*` and `OTEL_EXPORTER_OTLP_*` env vars pre-wired, both depending on `infra:up`.
- Pitfall C neutralised: `.tool-versions` makes IntelliJ auto-detect Corretto 17 even without the JetBrains Mise plugin.
- Pitfall D neutralised: `infra:down` does NOT pass `-v`; `infra:reset` is the explicit destructive task.
</success_criteria>

<output>
After completion, create `.planning/phases/1-baseline-scaffold/1-02-SUMMARY.md` documenting:
- Pinned versions (java + maven) and rationale (one sentence about exact-patch reproducibility)
- Confirmed `mise current` output
- Confirmed `mise run preflight` green-path output (paste the final 6-8 lines including "ALL GREEN")
- Task graph established (list the 13 named tasks + their dependencies)
- Files created: 2
</output>
