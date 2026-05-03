# Phase 10 Plan 05 Task 1 — End-to-end smoke results

**Captured:** 2026-05-03T01:26:00Z
**Stack state:** post-decomposition; 10 services running.

## SC #1 — PREREQ-01 (cycle absent)

- producer boot: `Started ProducerApplication` ✓ (line: `21:06:06.426 [main] INFO  c.e.producer.ProducerApplication - Started ProducerApplication in 0.889 seconds (process running for 0.993)`)
- consumer boot: `Started ConsumerApplication` ✓ (line: `21:06:10.873 [main] INFO  c.e.consumer.ConsumerApplication - Started ConsumerApplication in 1.016 seconds (process running for 1.118)`)
- BeanCurrentlyInCreationException: ABSENT in both logs ✓

## SC #2 — STACK-01 + STACK-02 (5 obs containers, exact-patch pinned)

`docker compose ps` (extract):

```
SERVICE             STATE     HEALTH
otel-collector      running   (NONE healthcheck — distroless)
grafana             running   healthy
loki                running   (NONE healthcheck — distroless)
mimir               running   (NONE healthcheck — distroless)
postgres            running   healthy
postgres-exporter   running
rabbitmq            running   healthy
redis-exporter      running
tempo               running   (NONE healthcheck — distroless)
valkey              running   healthy
```

`mise run verify:images`:

```
[verify:images] $ set -e
Phase 10 image-pin contract: 10 image:s, all pinned to exact patch versions.
```

## SC #3 — STACK-03 (3-signal end-to-end with unchanged OTLP endpoint)

- OTLP endpoint contract grep: HIT in producer + consumer (`http://localhost:4317`) ✓
- Tempo trace search (service.name=order-producer): 2 traces returned ✓
  - traceID: `a520ddd6792d4c3d9b5635d5c60d1808`, rootServiceName: `order-producer`, rootTraceName: `POST /orders`
- Mimir query (`orders_created_total`): 2 series returned ✓
  - Series includes label `order_priority=express` and `order_priority=standard`
- Loki log_range (service_name=order-producer): 10 log lines returned ✓
  - trace_id label on log entries: PRESENT (`96cacc844f636f263b700f222cd3d4aa` — confirms cross-signal correlation)

## SC #4 — STACK-04 + PREREQ-02 (dashboard panels resolve; screenshot captured)

`mise run verify:datasources`:

```
[verify:datasources] $ set -e
Phase 10 datasource contract: 3 UIDs match (loki, prometheus, tempo).
```

- Dashboard panel render check (manual UI step in Task 2): <DEFERRED to Task 2>
- step-04-metrics.png capture (manual step in Task 2): <DEFERRED to Task 2>

## SC #5 — STACK-05 (Mimir multitenancy_enabled: false)

- Mimir 401 / no-org-id hits in container logs: 0 ✓

## Captured logs (artifacts)

- /tmp/phase10-smoke-services.log
- /tmp/phase10-smoke-verify-images.log
- /tmp/phase10-smoke-verify-datasources.log
- /tmp/phase10-smoke-producer.log
- /tmp/phase10-smoke-consumer.log
- /tmp/phase10-smoke-demo-order.log
- /tmp/phase10-smoke-tempo-search.json
- /tmp/phase10-smoke-mimir-query.json
- /tmp/phase10-smoke-loki-query.json

## SC #4 verification + PREREQ-02 closure (Task 2)

- Dashboard URL: `http://localhost:3000/d/ose-otel-demo/ose-otel-demo` (anonymous Admin)
- All panels rendered without "Datasource not found": **DEFERRED** — verified indirectly via `mise run verify:datasources` (3 UIDs match: `loki`, `prometheus`, `tempo`); the dashboard JSON is unchanged from v1.0 Phase 7 and queries the verified-present UIDs, so panel render correctness is contractually implied. Direct UI inspection deferred to a follow-up workshop dry-run.
- step-04-metrics.png captured: **DEFERRED** — orchestrator-checkpoint decision: PREREQ-02 manual screenshot capture deferred to a follow-up session. The dashboard's metrics panel queries `prometheus` UID with `orders_created_total` series, both confirmed live in Task 1 (Mimir reports 2 series for express + standard priority). PREREQ-02 closure depends on the captured PNG; this remains an open verification gap surfaced in `/gsd-audit-uat`.
- File size: N/A (screenshot pending capture)
- `file docs/screenshots/step-04-metrics.png` output: N/A — file does not yet exist

**Verification gap log (for follow-up):**

| Gap | Closure path |
|-----|--------------|
| docs/screenshots/step-04-metrics.png missing | Boot stack, run `mise run dev` + `mise run demo:order` + `mise run load`, open `http://localhost:3000/d/ose-otel-demo`, screenshot the metrics row, save under `docs/screenshots/`. PNG sibling-naming convention. |
| Direct dashboard panel render eyeball | Same session as screenshot — confirm no panel shows "Datasource not found" banner. |

## Status

3-signal end-to-end: GREEN.
Phase 10 SC #1, #2, #3, #5 satisfied via automated checks. SC #4 satisfied indirectly via `verify:datasources` UID contract; direct panel render + PREREQ-02 screenshot capture deferred to a follow-up session (verification gap recorded above).
