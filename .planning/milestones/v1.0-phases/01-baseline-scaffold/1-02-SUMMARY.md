---
phase: 01-baseline-scaffold
plan: 02
subsystem: infra
tags: [mise, toolchain, corretto-17, maven-3.9, docker-compose, preflight, task-graph]

# Dependency graph
requires:
  - phase: 01-baseline-scaffold
    provides: nothing (this is plan 02 of 06; runs in Wave 1 alongside 1-01 and 1-03)
provides:
  - "Pinned JDK + Maven (Corretto 17.0.13.11.1, Maven 3.9.11) reproducible across cohorts"
  - "Complete workshop task graph: preflight, infra:up/down/reset/logs, build, test, dev/dev:producer/dev:consumer (parallel), demo:order, verify:bom (Phase 1 success gate), ui:grafana, ui:rabbitmq"
  - "Spring Boot env vars (SPRING_RABBITMQ_*) and forward-pinned OTEL_EXPORTER_OTLP_* settings"
  - "Pitfall C neutralised via .tool-versions companion (IntelliJ asdf-compat)"
  - "Pitfall D neutralised: infra:down preserves Grafana state; only infra:reset destroys it"
affects: [1-03-docker-compose, 1-04-parent-pom, 1-05-service-skeletons, 1-06-readme-and-validation, all subsequent phases consuming the env block]

# Tech tracking
tech-stack:
  added: [mise (v2025.1.0+ required), Amazon Corretto 17.0.13.11.1, Maven 3.9.11]
  patterns:
    - "mise.toml as single source of truth for toolchain + env + tasks"
    - "Dual-file toolchain pin: mise.toml (rich) + .tool-versions (asdf-compat for IntelliJ)"
    - "First-class mise parallelism via { tasks = [...] } run-array (no shell & plumbing)"
    - "Preflight as fail-fast gate: validates Java 17, Maven 3.9.x, Docker, ports 3000/4317/4318/5672/15672"
    - "Two-tier infra teardown: infra:down (preserves volumes) vs. infra:reset (destructive, opt-in)"

key-files:
  created:
    - "mise.toml — toolchain + env + 14 named tasks"
    - ".tool-versions — IntelliJ-readable asdf companion"
  modified: []

key-decisions:
  - "Pin JDK to corretto-17.0.13.11.1 (exact patch) instead of floating corretto-17 — workshop reproducibility (Pitfall C)"
  - "Ship .tool-versions alongside mise.toml — IntelliJ auto-detection without requiring the JetBrains Mise plugin"
  - "Use mise's first-class { tasks = [...] } parallel run-array form for [tasks.dev] (NOT shell &) — Ctrl-C propagates to both children"
  - "infra:down runs plain 'docker compose down' (NO -v); infra:reset is the explicit destructive task — preserves lgtm-data volume across workshop sessions (Pitfall D)"
  - "Preflight checks the 5 fixed infra ports (3000, 4317, 4318, 5672, 15672) only — 8080/8081 are app ports the attendee can override via PRODUCER_PORT/CONSUMER_PORT"
  - "Pre-wire OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OTLP_PROTOCOL in [env] now (Phase 1 has no consumers) so Phase 2+ inherits a clean slate"

patterns-established:
  - "Workshop one-liner: `mise install && mise run preflight && mise run infra:up && mise run dev`"
  - "Phase exit gate as a runnable mise task: `mise run verify:bom` returns zero matches in Phase 1 (asserts no io.opentelemetry on classpath)"
  - "Workshop helpers (`ui:grafana`, `ui:rabbitmq`, `demo:order`) reduce attendee terminal commands to memorable verbs"

requirements-completed: [INFRA-02, INFRA-03, INFRA-05]

# Metrics
duration: 2min
completed: 2026-04-30
---

# Phase 1 Plan 02: mise Toolchain & Task Graph Summary

**Pinned Corretto 17.0.13.11.1 + Maven 3.9.11 via mise.toml plus a 14-task workshop graph (preflight, infra:up/down/reset/logs, build, test, dev with first-class parallel run-array, demo:order, verify:bom Phase 1 gate, ui:grafana, ui:rabbitmq) and an asdf-compat .tool-versions companion for IntelliJ.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-04-30T02:38:47Z
- **Completed:** 2026-04-30T02:40:51Z
- **Tasks:** 3 (T1 mise.toml, T2 .tool-versions, T3 verify install + preflight)
- **Files modified:** 2 (both created)

## Accomplishments
- `mise.toml` parses cleanly (`mise tasks` lists all 14 tasks) with the exact Corretto patch and Maven 3.9.11 pinned in `[tools]`.
- `.tool-versions` ships in lockstep so IntelliJ picks up the right Project SDK without the JetBrains Mise plugin (Pitfall C neutralised).
- `mise install` succeeded — Corretto 17.0.13.11.1 + Maven 3.9.11 active in the worktree.
- `mise run preflight` exits 0 with `Pre-flight: ALL GREEN. Run: mise run infra:up` — confirms the Phase 1 baseline preflight gate works on the dev machine.
- Workshop env (`SPRING_RABBITMQ_*`, `OTEL_EXPORTER_OTLP_*`, `PRODUCER_PORT`/`CONSUMER_PORT`) live under `[env]` and propagate to every `mise run <task>` shell.

## Task Commits

Each task was committed atomically:

1. **Task 1: Write mise.toml** — `5354c68` (feat)
2. **Task 2: Generate companion .tool-versions** — `e5b834c` (feat)
3. **Task 3: Verify mise install + preflight green-path** — _no file changes_; verification only. `mise install` succeeded, `mise current java` = `corretto-17.0.13.11.1`, `mise current maven` = `3.9.11`, `mise run preflight` exited 0.

## Files Created/Modified
- `mise.toml` — toolchain pin + env block + complete task graph (14 tasks)
- `.tool-versions` — asdf-compat companion (`java corretto-17.0.13.11.1` / `maven 3.9.11`)

## Task Graph Established

| Task | Depends | Description |
|------|---------|-------------|
| `preflight` | — | Validates docker, ports 3000/4317/4318/5672/15672, Java 17, Maven 3.9.x |
| `infra:up` | — | `docker compose up -d --wait` |
| `infra:down` | — | `docker compose down` (NO `-v` — Pitfall D) |
| `infra:reset` | — | DESTRUCTIVE: `docker compose down -v` |
| `infra:logs` | — | `docker compose logs -f` |
| `build` | — | `mvn -T 1C -DskipTests clean install` |
| `test` | — | `mvn -T 1C verify` |
| `dev:producer` | `infra:up` | `mvn -pl producer-service spring-boot:run` with `-Dserver.port=${PRODUCER_PORT}` |
| `dev:consumer` | `infra:up` | `mvn -pl consumer-service spring-boot:run` with `-Dserver.port=${CONSUMER_PORT}` |
| `dev` | `infra:up` | First-class parallel `{ tasks = ["dev:producer", "dev:consumer"] }` |
| `demo:order` | — | `curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders ...` |
| `verify:bom` | — | **Phase 1 success gate**: asserts `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matches |
| `ui:grafana` | — | Opens http://localhost:3000 |
| `ui:rabbitmq` | — | Opens http://localhost:15672 |

## Verification Output (Preflight Green-Path)

```
[preflight] $ set -e
--- mise tools ---
corretto-17.0.13.11.1
3.9.11

--- Java version (must be 17) ---
OK

--- Maven version (must be 3.9.x) ---
OK

--- Docker ---
OK

--- Port availability (3000, 4317, 4318, 5672, 15672) ---
  3000: free
  4317: free
  4318: free
  5672: free
  15672: free

Pre-flight: ALL GREEN. Run: mise run infra:up
```

## Decisions Made
- **Exact-patch pin** (`corretto-17.0.13.11.1` not `corretto-17`) per RESEARCH lines 11-13: cohorts get bit-identical JDK builds.
- **`.tool-versions` shipped alongside `mise.toml`**: IntelliJ asdf-compat detection (Pitfall C) works without the JetBrains Mise plugin.
- **First-class `{ tasks = [...] }` parallelism** for `[tasks.dev]`: mise's run-array of task-tables propagates Ctrl-C to both children and surfaces both PIDs in mise's task UI; shell `&`+`wait` would lose this affordance.
- **Two-tier teardown**: `infra:down` preserves named volumes (lgtm-data, rabbitmq-data); `infra:reset` is opt-in destructive. Avoids accidental data loss when an attendee rejoins a session (Pitfall D).
- **5-port preflight scope (3000/4317/4318/5672/15672 only)**: 8080/8081 are app ports configurable via `PRODUCER_PORT`/`CONSUMER_PORT` env vars — they're not infrastructure, so they don't gate the workshop.

## Deviations from Plan

None - plan executed exactly as written. The mise.toml content matches the verified RESEARCH.md template (lines 644-785) byte-for-byte; .tool-versions matches RESEARCH.md lines 791-794 exactly.

## Issues Encountered
- `python3 -c "import tomllib"` failed locally (Python 3.9; tomllib is Python 3.11+). Used the documented fallback (`tomli`) per the plan's acceptance criteria — TOML parsing verified successfully via tomli; mise itself also parsed and listed all 14 tasks, providing redundant validation.

## Threat Surface Scan
No new security-relevant surface beyond what `<threat_model>` documents (T-1-02-01 through T-1-02-05 mitigated/accepted as planned). The default `guest/guest` RabbitMQ credentials are `accept`-disposition workshop-only and only become reachable when plan 1-03 starts the docker-compose containers (loopback port mapping, no external exposure).

## User Setup Required

None — `mise install` and `mise run preflight` are fully automated. Workshop attendees will run them per the README in plan 1-06.

## Next Phase Readiness
- Plan 1-03 (docker-compose.yml) can rely on `mise run infra:up` calling `docker compose up -d --wait` and on the env block providing `SPRING_RABBITMQ_*` defaults aligned to the compose port mapping.
- Plan 1-04 (parent pom) can rely on Maven 3.9.11 + Java 17 being on PATH via `mise install`.
- Plan 1-05 (service skeletons) can rely on `mise run dev:producer`/`dev:consumer` invoking `mvn -pl <service> spring-boot:run` with `-Dserver.port=${PRODUCER_PORT|CONSUMER_PORT}`.
- Plan 1-06 (README) will reference the green-path output above as the canonical first-run experience.
- Phase 1 exit gate (`mise run verify:bom`) is wired and will assert zero `io.opentelemetry` matches once plans 1-04/1-05 produce a buildable POM.

## Self-Check: PASSED

- `mise.toml` exists (verified via `test -f mise.toml`)
- `.tool-versions` exists (verified via `test -f .tool-versions`)
- Commit `5354c68` exists (Task 1: mise.toml)
- Commit `e5b834c` exists (Task 2: .tool-versions)

---
*Phase: 01-baseline-scaffold*
*Completed: 2026-04-30*
