# Roadmap: OSE OTel Demo

## Milestones

- ✅ **v1.0 Workshop** — Phases 1–7 (shipped 2026-05-02; tag `v1.0`) — see `milestones/v1.0-ROADMAP.md` for the full archived roadmap
- 📋 **v1.x — TBD** — next milestone not yet scoped (likely workshop-feedback iteration after first cohort delivery)

## Phases

<details>
<summary>✅ v1.0 Workshop (Phases 1–7) — SHIPPED 2026-05-02</summary>

- [x] Phase 1: Baseline & Scaffold (6/6 plans) — completed 2026-04-29 — tag `step-01-baseline`
- [x] Phase 2: Manual SDK Bootstrap & First Traces (6/6 plans) — completed 2026-05-01 — tag `step-02-traces`
- [x] Phase 3: AMQP Context Propagation (5/5 plans) — completed 2026-05-01 — tag `step-03-context-propagation`
- [x] Phase 4: Metrics (5/5 plans) — completed 2026-05-01 — tag `step-04-metrics`
- [x] Phase 5: Logs Correlation (6/6 plans) — completed 2026-05-01 — tag `step-05-logs`
- [x] Phase 6: Verification Tests (6/6 plans) — completed 2026-05-02 — tag `step-06-tests`
- [x] Phase 7: Polish & Differentiators (7/7 plans) — completed 2026-05-02 — *no tag (D-09)*

Plus the post-Phase-7 quick-task workshop polish on `main`:
- `step-08-db-cache` — Phase 8 Valkey + PostgreSQL manual instrumentation
- Workshop NOC dashboard, load-script burst/idempotency streams, scrape-interval / rate-window tuning, slow-loading-dashboard fixes

</details>

### 🚧 v1.x — Next Milestone (Not Yet Scoped)

To be defined via `/gsd-new-milestone` after first cohort delivery feedback. Candidate themes (out-of-scope notes from `PROJECT.md`):

- Multi-AMQP-pattern coverage (fanout, topic, RPC) — adds surface area; deferred from v1
- Baggage propagation checkpoint (`W3CBaggagePropagator` carrying business attributes across the AMQP boundary)
- Native-image build path
- Per-service `application-otel.yaml` profile demonstrating env-var-only OTel SDK autoconfigure path

## Progress

| Phase | Milestone | Plans | Status | Completed | Tag |
|---|---|---|---|---|---|
| 1. Baseline & Scaffold | v1.0 | 6/6 | Shipped | 2026-04-29 | step-01-baseline |
| 2. Manual SDK Bootstrap & First Traces | v1.0 | 6/6 | Shipped | 2026-05-01 | step-02-traces |
| 3. AMQP Context Propagation | v1.0 | 5/5 | Shipped | 2026-05-01 | step-03-context-propagation |
| 4. Metrics | v1.0 | 5/5 | Shipped | 2026-05-01 | step-04-metrics |
| 5. Logs Correlation | v1.0 | 6/6 | Shipped | 2026-05-01 | step-05-logs |
| 6. Verification Tests | v1.0 | 6/6 | Shipped | 2026-05-02 | step-06-tests |
| 7. Polish & Differentiators | v1.0 | 7/7 | Shipped | 2026-05-02 | *(no tag — D-09)* |

## Backlog

*(empty — no items deferred from v1.0; future ideas to be captured against `v1.x` once scoped)*
