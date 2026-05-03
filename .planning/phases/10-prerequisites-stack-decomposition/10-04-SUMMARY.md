---
phase: 10-prerequisites-stack-decomposition
plan: "04"
subsystem: infra
tags:
  - docker-compose
  - mise
  - infrastructure-decomposition
  - otel-collector
  - tempo
  - mimir
  - loki
  - grafana

requires:
  - phase: 10-prerequisites-stack-decomposition/plan-02
    provides: "infra/observability/*.yaml config files that docker-compose mounts"
  - phase: 10-prerequisites-stack-decomposition/plan-03
    provides: "grafana/datasources.yaml and dashboards.yaml path correction"

provides:
  - "docker-compose.yml with 10-service stack: 5 data (rabbitmq/valkey/postgres/redis-exporter/postgres-exporter) + 5 observability (otel-collector/tempo/mimir/loki/grafana)"
  - "5 named volumes: tempo-data, tempo-wal, mimir-data, loki-data, grafana-data (replacing lgtm-data)"
  - "mise.toml extended: preflight 14 ports (D-11), verify:datasources (D-03), verify:images (D-14)"
  - "grafana/prometheus.yaml deleted (D-15 — scrape config migrated to otelcol-config.yaml)"

affects:
  - 10-05 (smoke test plan — depends on 10-service stack being up)
  - phase-11 (tail sampling — Collector otel-collector service name used in config)
  - phase-12 (exemplar wiring — Grafana datasource UIDs preserved from Plan 03)

tech-stack:
  added:
    - otel/opentelemetry-collector-contrib:0.151.0 (as separate service, replacing embedded in lgtm)
    - grafana/tempo:2.10.5 (as separate service)
    - grafana/mimir:3.0.6 (as separate service)
    - grafana/loki:3.7.1 (as separate service)
    - grafana/grafana:13.0.1 (as separate service)
  patterns:
    - "user=0:0 on Tempo for distroless named-volume permission fix (workshop pattern)"
    - "test=[NONE] healthcheck for distroless images (Collector/Tempo/Mimir/Loki have no wget/curl/sh)"
    - "verify:datasources retry loop (6×5s) tolerates Grafana provisioning startup race"
    - "TOML basic string escaping: [[:space:]] instead of \\s, \\\\. instead of \\. for regex in grep -E"

key-files:
  created: []
  modified:
    - docker-compose.yml (122 lines → 280 lines: lgtm replaced by 5 observability services + 5 named volumes)
    - mise.toml (299 lines → 432 lines: preflight 14 ports + 2 new verify tasks)
  deleted:
    - grafana/prometheus.yaml (D-15: scrape config migrated to otelcol-config.yaml in Plan 02)

key-decisions:
  - "test=[NONE] for distroless container healthchecks (Collector/Tempo/Mimir/Loki): otel-collector-contrib, tempo, mimir, loki images are all distroless/minimal with no wget/curl/sh — Docker HEALTHCHECK cannot use wget inside the container. External readiness via :13133, :3200/ready, :9009/ready, :3100/ready is used by Plan 05 smoke instead."
  - "user=0:0 on Tempo service: fresh Docker named volumes are root-owned; Tempo runs as uid 10001 and cannot mkdir under them (mkdir /var/tempo/wal/blocks: permission denied). Running as root is the standard workshop/development workaround. Production deployments require a volume init container."
  - "Grafana healthcheck preserved (wget /api/health): grafana/grafana:13.0.1 is a full Debian-based image with wget available — Docker healthcheck works and is retained."
  - "TOML escape discipline: \\s is not a valid TOML escape; replaced with [[:space:]] in grep -E patterns. \\. becomes \\\\. in TOML basic strings."

patterns-established:
  - "Pattern: distroless image healthcheck pattern — use test=[NONE] for distroless OTel backends; note external readiness endpoint in WHY comment"
  - "Pattern: mise task retry loop for external service readiness — 6×5s bounded retry in task body so all callers (smoke, README, CI) get tolerance for free"

requirements-completed:
  - STACK-01
  - STACK-02
  - STACK-03
  - STACK-04
  - STACK-05

duration: 15min
completed: "2026-05-03"
---

# Phase 10 Plan 04: docker-compose Decomposition + mise Extension Summary

**lgtm all-in-one replaced by 5 pinned production-shape containers (otel-collector:0.151.0, tempo:2.10.5, mimir:3.0.6, loki:3.7.1, grafana:13.0.1) with 5 named volumes, depends_on chains, and two new mise verify tasks**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-03T00:46:14Z
- **Completed:** 2026-05-03T01:01:10Z
- **Tasks:** 2
- **Files modified:** 2 (1 modified, 1 deleted)

## Accomplishments

- Replaced the monolithic `lgtm:` service block with 5 separate observability services in docker-compose.yml; all 10 services reach `running` state via `docker compose up -d --wait` in < 180s
- Deleted `grafana/prometheus.yaml` (D-15: its scrape jobs live in `otelcol-config.yaml` since Plan 02; orphan removed)
- Extended `mise.toml`: preflight grew from 5 to 14 ports (D-11); added `verify:datasources` (D-03, with 6×5s retry loop) and `verify:images` (D-14, floating-tag guardrail); both pass against live stack

## docker-compose.yml Line-Count Delta

| Version | Lines | Services |
|---------|-------|----------|
| v1.0 (before Plan 04) | 122 | 6 (data×5 + lgtm×1) |
| v2.0 (after Plan 04) | 280 | 10 (data×5 + obs×5) |

## mise.toml Line-Count Delta

| Version | Lines | New Tasks |
|---------|-------|-----------|
| Before Plan 04 | 299 | — |
| After Plan 04 | 432 | verify:datasources, verify:images |

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite docker-compose.yml** - `6d12af7` (feat)
2. **Task 2: Delete prometheus.yaml + extend mise.toml** - `f27ef90` (feat)

## Live Stack Verification

```
docker compose ps:
otel-collector  running           (NONE healthcheck — distroless)
grafana         running healthy   (wget /api/health)
loki            running           (NONE healthcheck — distroless)
mimir           running           (NONE healthcheck — distroless)
postgres        running healthy
postgres-exporter running
rabbitmq        running healthy
redis-exporter  running
tempo           running           (NONE healthcheck — distroless)
valkey          running healthy
```

```
mise run verify:images:
Phase 10 image-pin contract: 10 image:s, all pinned to exact patch versions.

mise run verify:datasources:
Phase 10 datasource contract: 3 UIDs match (loki, prometheus, tempo).
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Tempo named-volume permission denied**

- **Found during:** Task 1 (docker compose up --wait)
- **Issue:** `grafana/tempo:2.10.5` runs as uid 10001. Fresh Docker named volumes are owned by root. Tempo could not create `/var/tempo/wal/blocks`: `permission denied`. Container crash-looped.
- **Fix:** Added `user: "0:0"` to the tempo service in docker-compose.yml. Running as root is the standard workshop/development workaround for distroless containers with named volumes. WHY comment added inline.
- **Files modified:** `docker-compose.yml`
- **Committed in:** `6d12af7` (Task 1 commit)

**2. [Rule 1 - Bug] Distroless images do not have wget (Collector, Tempo, Mimir, Loki)**

- **Found during:** Task 1 (docker compose up --wait — all four containers showed healthcheck failure)
- **Issue:** The plan's healthchecks used `["CMD", "wget", "--spider", "--quiet", "http://localhost:XXXX/ready"]`. `otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, and `grafana/loki:3.7.1` are all distroless images with NO shell, NO wget, NO curl. Docker `exec` fails with `"wget": executable file not found in $PATH`.
- **Fix:** Replaced healthcheck `test:` with `["NONE"]` for all four distroless containers. Grafana (Debian-based with wget) retains its `wget /api/health` healthcheck. Added WHY comments explaining external readiness endpoints (`:13133/`, `:3200/ready`, `:9009/ready`, `:3100/ready`) that Plan 05 smoke uses instead.
- **Files modified:** `docker-compose.yml`
- **Committed in:** `6d12af7` (Task 1 commit)

**3. [Rule 1 - Bug] TOML escape error: `\s` invalid in TOML basic strings**

- **Found during:** Task 2 (mise run verify:images parsing error)
- **Issue:** `verify:images` task body used `grep -E '^\s*image:'` — `\s` is not a valid TOML escape sequence in basic strings (valid escapes: `\b`, `\t`, `\n`, `\f`, `\r`, `\"`, `\\`, `\uXXXX`). TOML parser rejected the file.
- **Fix:** Replaced `\s` with `[[:space:]]` (POSIX character class, no backslash), and `\.` with `\\.` (double-backslash in TOML produces single backslash in the shell string) throughout the verify:images grep patterns.
- **Files modified:** `mise.toml`
- **Committed in:** `f27ef90` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1 — bugs found during live validation)
**Impact on plan:** All auto-fixes required for correct startup and TOML parse. Plan's WHY-comment documentation rationale preserved for all fixes. No scope creep.

## Known Stubs

None — all services start and respond to their readiness endpoints. All 3 datasource UIDs provisioned. Dashboard JSON files untouched (UIDs match).

## Threat Flags

No new security surface beyond the plan's threat model. All D-10 ports are loopback-accessible for workshop debugging. The `user: "0:0"` on Tempo is documented in the threat model (T-10.04-04 — accepted for workshop context) with a README callout deferred to Plan 05.

## What Plan 05 Needs to Know

- **Observed cold-start times on this machine:**
  - Mimir: ~5-7s to reach `/ready` (within start_period: 30s — comfortable margin)
  - Tempo: ~3-5s to reach `/ready` (within start_period: 20s)
  - Loki: ~1-2s to reach `/ready` (within start_period: 20s)
  - Grafana: ~20s to provision datasources and dashboards (within start_period: 20s — tight on slower machines; verify:datasources 6×5s retry loop covers the gap)
  - OTel Collector: instant health_check extension readiness after startup
- **start_period: 30s for Mimir appears sufficient on this machine** — on slower workshop laptops with spinning disks or contested RAM, consider bumping to 45s if Plan 05 smoke tests hit a race.
- **Distroless healthchecks**: Collector/Tempo/Mimir/Loki show `running` (no health status) in `docker compose ps` — this is correct behavior with `test: ["NONE"]`. Plan 05 smoke must use external curl checks, not `docker compose ps --status healthy`.
- **verify:datasources retry loop**: 6×5s is sufficient on this machine. The task exits on first successful Grafana response (typically attempt 1-2 once the stack is fully up). The retry only kicks in right after a fresh `docker compose up --wait`.

## Self-Check: PASSED

Files exist:
- `docker-compose.yml` — FOUND (280 lines)
- `mise.toml` — FOUND (432 lines)
- `grafana/prometheus.yaml` — CORRECTLY ABSENT (deleted D-15)
- `.planning/phases/10-prerequisites-stack-decomposition/10-04-SUMMARY.md` — FOUND (this file)

Commits exist:
- `6d12af7` — FOUND (feat(10-04): replace lgtm with 5 observability services)
- `f27ef90` — FOUND (feat(10-04): delete prometheus.yaml and extend mise.toml)

Live verification:
- `docker compose ps --status running | count` = 10 ✓
- `mise run verify:images` passes ✓
- `mise run verify:datasources` passes ✓
