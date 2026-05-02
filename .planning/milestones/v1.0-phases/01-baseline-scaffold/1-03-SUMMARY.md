---
phase: 01-baseline-scaffold
plan: 03
subsystem: infra
tags: [docker-compose, rabbitmq, grafana-otel-lgtm, healthcheck, named-volume]

requires:
  - phase: 01-baseline-scaffold
    provides: "Phase 1 plan + RESEARCH.md (lines 806-850 verified docker-compose.yml template; Pitfall D persistence rationale; Don't-Hand-Roll table for healthchecks)"
provides:
  - "docker-compose.yml declaring two infrastructure services (rabbitmq + grafana/otel-lgtm) with pinned tags, Stage-1 RabbitMQ healthcheck, image-builtin lgtm healthcheck, and a named lgtm-data volume for Grafana state persistence across infra:down/up cycles"
  - "Five host ports exposed for the workshop: 5672 + 15672 (RabbitMQ + Mgmt UI), 3000 + 4317 + 4318 (Grafana UI + OTLP gRPC + OTLP HTTP)"
  - "Inline YAML guard comment above lgtm `image:` line documenting that the image's built-in HEALTHCHECK validates all 5 backends and must not be overridden (T-1-03-06 mitigation)"
affects:
  - "1-02 mise-toml — `infra:up` / `infra:down` / `infra:reset` tasks invoke `docker compose up -d --wait` / `docker compose down` / `docker compose down -v` against this file"
  - "1-06 phase-1-verification — owns wave-3 end-to-end `docker compose up -d --wait` + persistence sentinel test + port-listening assertions"
  - "Phase 2 (otel-bootstrap) — apps will speak OTLP gRPC to localhost:4317 once SDK wiring lands"
  - "Phase 7 (workshop polish) — pre-provisioned Grafana dashboards land in lgtm-data via WORK-02"

tech-stack:
  added:
    - "rabbitmq:4.3-management (pinned tag — RabbitMQ 4.x AMQP broker + Management UI)"
    - "grafana/otel-lgtm:0.26.0 (pinned tag — single-container LGTM stack: Grafana + Loki + Tempo + Mimir + OTel Collector)"
    - "Docker Compose v2 (no `version:` key — modern compose schema)"
    - "Docker named volume `lgtm-data` (default local driver; persists Grafana state)"
  patterns:
    - "Pinned image tags (no `:latest` / no floating tags) for workshop reproducibility"
    - "Stage-1 RabbitMQ healthcheck (`rabbitmq-diagnostics -q ping`) — lightest with lowest false-positive rate per RabbitMQ monitoring docs"
    - "Trust the image's built-in HEALTHCHECK over a custom override when the bundled script validates more components than a hand-rolled curl check (Don't-Hand-Roll #3)"
    - "Named volume mounted at /data — preserved across `docker compose down`, wiped only on `docker compose down -v` (Pitfall D semantics)"

key-files:
  created:
    - "docker-compose.yml — infrastructure declaration: rabbitmq + lgtm services + lgtm-data volume"
  modified: []

key-decisions:
  - "Pin image tags to `rabbitmq:4.3-management` and `grafana/otel-lgtm:0.26.0` (no `:latest`) — workshop screenshots and trace IDs depend on reproducibility across cohorts (RESEARCH Anti-Patterns)"
  - "RabbitMQ healthcheck = Stage 1 `rabbitmq-diagnostics -q ping` — lightest, lowest false-positive (RESEARCH alternatives table line 125)"
  - "lgtm service has NO `healthcheck:` block in compose — image's built-in HEALTHCHECK runs `/otel-lgtm/docker/healthcheck.sh` which validates Grafana + Loki + Tempo + Mimir + OTel Collector. Overriding with curl-on-3000 would only validate Grafana — a regression (Don't-Hand-Roll #3)"
  - "Named volume `lgtm-data` mounted at /data — Grafana state survives `docker compose down`; only `docker compose down -v` (i.e. `mise run infra:reset`) wipes it. Encodes Pitfall D"
  - "Predictable `container_name` values (`ose-otel-rabbitmq`, `ose-otel-lgtm`) — workshop one-liners like `docker logs ose-otel-lgtm` work without per-attendee project-name guessing"
  - "No `version:` field — Compose v2 ignores it and warns; modern compose files omit it"
  - "No top-level `networks:` block — default bridge network is fine for two-service compose; keeps the file minimal for workshop readability"
  - "No persistence volume for RabbitMQ — workshop demo doesn't need broker state across resets; one less mount for attendees to reason about"

patterns-established:
  - "Pattern: Pinned tag + inline guard comment — the `# Image's built-in HEALTHCHECK checks all 5 backends — do NOT override.` line above the lgtm `image:` line is documentation-as-defense-against-future-edits (T-1-03-06 mitigation)"
  - "Pattern: Named volume for state-bearing services only — Grafana writes to /data; RabbitMQ does not get a persistent volume because workshop demos start fresh"
  - "Pattern: Stage-1 healthcheck for application brokers — `rabbitmq-diagnostics -q ping` over heavier `check_running` for fast cold-start"

requirements-completed: [INFRA-04]

duration: ~1min
completed: 2026-04-30
---

# Phase 1 Plan 3: docker-compose.yml Summary

**Two-service `docker-compose.yml` with pinned `rabbitmq:4.3-management` (Stage-1 ping healthcheck) + `grafana/otel-lgtm:0.26.0` (image-built-in HEALTHCHECK only) + named `lgtm-data` volume that persists Grafana state across `docker compose down`/`up` cycles.**

## Performance

- **Duration:** ~1 min (file creation + 16 grep checks + `docker compose config -q` + commit)
- **Started:** 2026-04-30T02:39:29Z
- **Completed:** 2026-04-30T02:40:38Z
- **Tasks:** 1 file-creating task + 1 verification task (Task 2 scoped to `docker compose config` only — see Deviations)
- **Files created:** 1 (`docker-compose.yml`, 44 lines)

## Accomplishments

- Created `docker-compose.yml` at the repo root declaring exactly two services (`rabbitmq`, `lgtm`) and one named volume (`lgtm-data`), matching RESEARCH.md lines 806-850 verbatim.
- Both image tags pinned: `rabbitmq:4.3-management` and `grafana/otel-lgtm:0.26.0`. Grep-verified no `:latest` substring anywhere in the file.
- RabbitMQ healthcheck = Stage 1 (`rabbitmq-diagnostics -q ping`, interval 10s, timeout 5s, retries 10, start_period 30s) — lightest config per the RabbitMQ monitoring docs.
- lgtm service has zero `healthcheck:` block (verified by `awk '/^  lgtm:/,/^  [a-z]/' docker-compose.yml | grep -c '^    healthcheck:'` returning `0`) — defers to the image's built-in `HEALTHCHECK` that validates all 5 embedded backends.
- Inline guard comment above lgtm `image:` line: `# Image's built-in HEALTHCHECK checks all 5 backends — do NOT override.` so a future editor doesn't "fix" a non-issue.
- Named volume `lgtm-data` mounted at `/data` (persists Grafana state across plain `docker compose down`; wiped only on `docker compose down -v`).
- Five infrastructure ports exposed on the host: `5672`, `15672`, `3000`, `4317`, `4318`.
- `docker compose config -q` exits 0 — YAML schema valid against the Compose v2 spec.
- PyYAML `safe_load` succeeds — file is valid YAML.
- `docker compose config --services` lists exactly `lgtm` and `rabbitmq`; `docker compose config --volumes` lists exactly `lgtm-data`.

## Task Commits

Each task was committed atomically with `--no-verify` (parallel-worktree contention avoidance):

1. **Task 1: Write docker-compose.yml** — `8bf9b4f` (feat) — created `docker-compose.yml` (44 lines)
2. **Task 2: Verify infra:up green-path** — verification-only task (no file changes); `docker compose config -q` passed. Full `docker compose up -d --wait` + persistence sentinel + port-listening checks scoped to plan 1-06 per orchestrator scope cap.

**Plan metadata commit:** appended after this SUMMARY.md is staged.

## Files Created/Modified

- `docker-compose.yml` (created) — infrastructure declaration:
  - `services.rabbitmq`: image `rabbitmq:4.3-management`, container_name `ose-otel-rabbitmq`, ports 5672 + 15672, Stage-1 healthcheck, `restart: unless-stopped`
  - `services.lgtm`: image `grafana/otel-lgtm:0.26.0`, container_name `ose-otel-lgtm`, ports 3000 + 4317 + 4318, env `GF_SECURITY_ADMIN_USER`/`GF_SECURITY_ADMIN_PASSWORD=admin`, volume `lgtm-data:/data`, no custom healthcheck, `restart: unless-stopped`
  - `volumes.lgtm-data`: named volume (default local driver)

## Verified Acceptance Criteria

All 16 of Task 1's grep-/test-based acceptance criteria pass:

| # | Check | Result |
|---|-------|--------|
| 1 | `test -f docker-compose.yml` | exit 0 (file exists) |
| 2 | `grep -c 'image: rabbitmq:4.3-management'` | 1 |
| 3 | `grep -c 'image: grafana/otel-lgtm:0.26.0'` | 1 |
| 4 | `! grep -E ':latest'` | exit 0 (no floating tags) |
| 5 | `grep -c 'rabbitmq-diagnostics'` | 1 |
| 6 | `grep -c '"-q", "ping"'` | 1 (Stage 1 form) |
| 7 | `grep -c '"5672:5672"'` | 1 |
| 8 | `grep -c '"15672:15672"'` | 1 |
| 9 | `grep -c '"3000:3000"'` | 1 |
| 10 | `grep -c '"4317:4317"'` | 1 |
| 11 | `grep -c '"4318:4318"'` | 1 |
| 12 | `grep -c 'lgtm-data:/data'` | 1 |
| 13 | `grep -c '^volumes:$'` | 1 |
| 14 | `grep -c '^  lgtm-data:$'` | 1 |
| 15 | lgtm service has NO healthcheck block (awk slice) | 0 |
| 16 | `docker compose config -q` exits 0 + PyYAML parse | PASS |

## Decisions Made

Followed plan as specified — no implementation alternatives required. The plan is highly prescriptive (RESEARCH.md lines 806-850 are a verified template), and the docker-compose.yml is a one-to-one match. The two cosmetic decisions documented in `key-decisions` above (predictable container names, no version key) were already mandated by the plan's `<action>` block.

## Deviations from Plan

### Scope-Adjusted Verification (Task 2)

**1. [Rule 3 — Scope cap from orchestrator] Task 2 verification narrowed to `docker compose config -q` only**
- **Found during:** Task 2 (Verify infra:up green-path)
- **Issue:** The plan's Task 2 calls for a full `docker compose up -d --wait` cycle, sentinel-file persistence test (`docker compose exec lgtm sh -c 'echo persist > /data/p.txt'`), `docker compose down`/`up` to verify the named volume survives, and a `docker compose down -v`/`up` cycle to verify `mise run infra:reset` semantics. This requires pulling `rabbitmq:4.3-management` (~250 MB) and `grafana/otel-lgtm:0.26.0` (~1.2 GB), then waiting up to 120s for both healthchecks — slow on workshop laptop hardware.
- **Adjustment:** The orchestrator's `<plan_specifics>` block in the executor prompt explicitly reduces Task 2 scope to `docker compose config -q` only and defers the end-to-end `up -d --wait` + persistence + port-listening verification to plan 1-06 (wave-3 phase-1-verification owner). Quote from orchestrator: *"Cannot run `docker compose up -d --wait` end-to-end as part of this plan because (a) it's slow (full pulls) and (b) plan 1-06 owns the wave-3 end-to-end verification. Just `config -q` is sufficient here."*
- **What was actually run:** `docker compose config -q` (exit 0), `docker compose config --services` (lgtm + rabbitmq), `docker compose config --volumes` (lgtm-data), PyYAML `safe_load`. All pass.
- **What was NOT run:** `docker compose up -d --wait`, sentinel-file persistence cycle, `down -v` reset cycle, `ss -tln` port-listening assertions. These are the explicit deliverables of plan 1-06.
- **Files modified:** None (Task 2 is verification-only)
- **Verification of correctness:** The schema-level validation (`config -q`) confirms the file is well-formed and resolvable; runtime validation is staged in plan 1-06.
- **Committed in:** No commit — Task 2 produces no file changes.

---

**Total deviations:** 1 scope adjustment (orchestrator-driven, not a quality issue)
**Impact on plan:** Zero — runtime verification is a phase-level concern that plan 1-06 owns. The schema-level checks performed here are sufficient to commit the file with confidence; INFRA-04 satisfaction is structurally guaranteed by the file's content (pinned tags, named volume, healthchecks declared correctly) and will be runtime-verified in wave 3.

## Issues Encountered

- **One minor edit during Task 1:** my initial header comment contained the literal substring `:latest` (in the explanatory text "no :latest — see RESEARCH Anti-Patterns"), which made the `! grep -E ':latest'` acceptance check fail. Reworded the comment to "no floating tags — see RESEARCH Anti-Patterns" — same educational intent, no literal substring. Caught immediately by running the acceptance grep before committing.

## Threat Model Mitigations Implemented

| Threat ID | Mitigation in this plan |
|-----------|-------------------------|
| T-1-03-01 (Tampering — floating image tags) | Both images pinned; grep verifies no `:latest` substring anywhere |
| T-1-03-04 (DoS — RabbitMQ healthcheck false-positive) | Stage-1 `rabbitmq-diagnostics -q ping` (lightest); `start_period: 30s` + `retries: 10` accommodates cold-start; downstream `--wait-timeout 120` lives in plan 1-02's `infra:up` task |
| T-1-03-06 (Tampering — future "helpful" custom healthcheck on lgtm) | Inline YAML comment block above lgtm `image:` line documents the bundled HEALTHCHECK rationale, deterring well-intentioned override |

T-1-03-02 (RabbitMQ default credentials), T-1-03-03 (Grafana admin/admin), and T-1-03-05 (port 3000 collision) are accepted/mitigated elsewhere (plan 1-02's `preflight` task, README docs) per the threat register's dispositions.

## User Setup Required

None — Docker and Docker Compose v2 must be installed on the attendee's laptop, but that is documented in plan 1-02's `preflight` task and the project README (Phase 1 plan 1-04 / 1-06 territory). This plan does not introduce new setup beyond existing project prerequisites.

## Next Phase Readiness

- **Within Phase 1:** plan 1-02 (mise.toml, sibling worktree) provides `infra:up` / `infra:down` / `infra:reset` tasks that drive this compose file. Plan 1-06 owns the wave-3 end-to-end verification (`docker compose up -d --wait` + sentinel persistence + port-listening assertions).
- **Phase 2 readiness:** OTLP gRPC endpoint at `localhost:4317` is reachable once `infra:up` runs; producer/consumer apps can target it with no further compose changes.
- **Phase 7 readiness:** `lgtm-data` volume is ready to receive pre-provisioned dashboards via WORK-02 (mount additional `./grafana-provisioning:/etc/grafana/provisioning:ro` later if needed).

## Self-Check: PASSED

- File `docker-compose.yml` exists at repo root: FOUND.
- Commit `8bf9b4f` (feat(1-03): add docker-compose.yml) is in `git log`: FOUND.
- All 16 of Task 1's acceptance grep checks: PASS.
- `docker compose config -q`: exits 0.
- PyYAML `safe_load`: PASS.

---
*Phase: 01-baseline-scaffold*
*Plan: 03-docker-compose*
*Completed: 2026-04-30*
