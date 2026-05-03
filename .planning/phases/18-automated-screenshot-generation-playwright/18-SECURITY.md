---
phase: 18
slug: automated-screenshot-generation-playwright
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-03
---

# Phase 18 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| env vars → script | WARMUP_MS, FORCE, GRAFANA_URL, PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH are user-controlled strings | String values used in fetch() URLs and spawn() env; none interpolated into execSync shell commands |
| script → docker compose | execSync('docker compose restart otel-collector') executes a shell command | Fixed string — no variable interpolation |
| otelcol-config.yaml.bak | backup file contains full Collector config; should be deleted after restore | YAML config with processor pipeline definitions |
| git tag → main | annotated tag applied to HEAD of main | Tag metadata only |
| mise task → shell | run = "node scripts/capture-screenshots.mjs" executes a fixed script path | No env var interpolation in the exec call |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-18-01 | Tampering | env var injection into execSync | mitigate | GRAFANA_URL used only in fetch() URL construction (L159, L290, L301, L307); all execSync calls use fixed strings — 'docker compose restart otel-collector' (L236), 'mvn ...' (L409), 'git worktree ...' (L405/408/441). No env var interpolation in shell exec paths. | closed |
| T-18-02 | Information Disclosure | otelcol-config.yaml.bak left on disk | mitigate | restoreConfigSync() at L195 calls unlinkSync(OTELCOL_CONFIG_BAK) at L199 after copyFileSync restore; .bak file deleted before process exits. | closed |
| T-18-03 | Tampering | tail_sampling left disabled on interrupted run | mitigate | P18-3: synchronous copyFileSync in finally block (L272-275) + process.on('exit', restoreConfigSync) at L207; config always restored even on SIGINT via process.exit(1) → exit handler chain. | closed |
| T-18-04 | Information Disclosure | scripts/screenshots/ deleted without backup | accept | Directory tracked in git — fully recoverable via `git checkout` if needed; intentional deletion per D-S1. | closed |
| T-18-05 | Tampering | blank or spinner PNG committed to repo | mitigate | Human-verify checkpoint in Plan 03 reviewed all 4 PNGs before commit; non-blank size check (>10KB) enforced in Task 1 acceptance criteria. All 4 PNGs verified: step-04-metrics (71KB), step-11-OFF (102KB), step-11-ON (101KB), step-12-exemplars (64KB). | closed |
| T-18-06 | Denial of Service | mise run screenshots leaves Collector in broken state | mitigate | restoreConfigSync() (L195) restores config via copyFileSync; restartCollector() (L279) restarts Collector after restore. Both called in withTailSamplingDisabled wrapper (L260-280). process.on('exit') safety net at L207. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-18-01 | T-18-04 | scripts/screenshots/ deletion is intentional (D-S1); fully recoverable from git history. No sensitive data lost. | Phase 18 executor | 2026-05-03 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-03 | 6 | 6 | 0 | /gsd-secure-phase 18 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-03
