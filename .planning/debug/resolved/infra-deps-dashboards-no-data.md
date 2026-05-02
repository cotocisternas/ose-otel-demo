---
slug: infra-deps-dashboards-no-data
status: resolved
trigger: |
  DATA_START
  Infra and Deps dashboards don't show data on cache valkey and database
  DATA_END
created: 2026-05-02
updated: 2026-05-02
goal: find_and_fix
diagnose_only: false
---

# Debug Session: infra-deps-dashboards-no-data

## Symptoms

- **Affected panels** (DATA): All four panel groups missing data simultaneously:
  1. Infra dashboard — Valkey panels (cache hit rate, ops/sec, memory, connected clients, etc.)
  2. Infra dashboard — PostgreSQL panels (connections, query rate, locks, etc.)
  3. Deps dashboard — cache call panels (app → Valkey DB-span rate / latency / errors)
  4. Deps dashboard — DB call panels (app → PostgreSQL DB-span rate / latency / errors)
- **Failure mode** (DATA): "No data" with empty graphs — Grafana renders the panel chrome but the query returns zero series. NOT a query error / parse error. NOT a partial render.
- **Timeline** (DATA): Never rendered. Phase 8 (quick task 260502-8gk) added Valkey + PostgreSQL + manual OTel instrumentation, but the pre-existing Infra/Deps dashboards predate that work and were never updated to surface the new signals.
- **Stack state** (DATA): Live stack is up with load running.
- **Mode**: Find and fix.

## Inputs / Known Locations

- Dashboard JSON files: `grafana/dashboards/ose-otel-infra.json`, `grafana/dashboards/ose-otel-demo.json`, `grafana/dashboards/ose-otel-noc.json`. There is **no** separate `ose-otel-deps.json` — the Infra dashboard's description says "Step 9 Infra & Deps dashboard"; the user's "Deps" reference is to the same file.
- Provisioning sidecar: `grafana/dashboards/dashboards.yaml` mounts the directory.
- Phase 8 instrumentation: `producer-service/.../InstrumentedJedisPool.java` (CLIENT span name `"SET"`), `consumer-service/.../OrderRepository.java` (CLIENT span name `"INSERT processed_orders"`), `consumer-service/.../HikariCpConnectionGauge.java` (`db.client.connection.count` ObservableGauge → `db_client_connection_count` metric).
- Prometheus config: `grafana/prometheus.yaml` overrides the bundled lgtm config to add three scrape jobs (`rabbitmq`, `redis_exporter`, `postgres_exporter`). Does NOT override `global.scrape_interval` → falls back to Prometheus default `1m`.
- docker-compose: `redis-exporter` (oliver006/redis_exporter:v1.83.0) and `postgres-exporter` (prometheuscommunity/postgres-exporter:v0.19.1) are present and healthy.

## Hypotheses

1. ~~H1: Exporter metrics not present (no `redis_exporter` / `postgres_exporter`).~~ **ELIMINATED** — both exporters exist, are running, and emit metrics.
2. ~~H2: Deps panels query OTel DB-span metrics with wrong attribute key.~~ **NOT APPLICABLE** — there are no separate Deps panels; the Infra dashboard's DB/Cache rows query exporter metrics, not OTel DB-span metrics.
3. ~~H3: Datasource UID mismatch.~~ **ELIMINATED** — datasource UIDs `prometheus`, `tempo`, `loki` match what otel-lgtm provisions; the dashboard `meta.provisioned=true` and Grafana renders the chrome correctly.
4. ~~H4: Phase 8 emitted spans but not metrics.~~ **PARTIALLY ELIMINATED** — Phase 8 emits both spans (Tempo) and metrics (`db_client_connection_count`, `idempotency_cache_hits_total`, `idempotency_cache_misses_total`). The HikariCP gauge panel (id=12) renders correctly with 3 series.
5. ~~H5: Semconv attribute key migration mismatch.~~ **NOT APPLICABLE** — no panel filters by `db.system` or `db.system.name`; the affected panels query exporter metrics with their native exporter labels.
6. **H6 (NEW, CONFIRMED): `rate(...[1m])` window equals `global.scrape_interval=1m`, so Prometheus has at most 1 sample per series in the window and `rate()` returns empty.**

## Evidence

- timestamp: 2026-05-02 ~ 20:15 UTC; check: `find grafana -name '*.json'`; result: `ose-otel-infra.json`, `ose-otel-demo.json`, `ose-otel-noc.json` (no `ose-otel-deps.json`).
- timestamp: 2026-05-02; check: `docker ps`; result: `redis-exporter`, `postgres-exporter`, `valkey`, `postgres`, `rabbitmq`, `lgtm` all up and healthy. So infrastructure-side is wired correctly.
- timestamp: 2026-05-02; check: Prometheus `/api/v1/label/__name__/values`; result: `redis_*` (60+ names), `pg_*` (40+ names), `rabbitmq_*` (30+ names) all present. So `H1` (metrics missing) is wrong.
- timestamp: 2026-05-02; check: instant queries against every panel's PromQL expression in the Infra dashboard (28 queries); result: ALL non-`rate(...)` queries return ≥1 series; ALL `rate(...[1m])` queries against scraped exporter metrics return 0 series; `rate(...[1m])` queries against OTLP-ingested metrics (`orders_created_total`, `traces_spanmetrics_*`) return data.
- timestamp: 2026-05-02; check: `query_range` for `redis_keyspace_hits_total` over 5min; result: 21 points at step=15s, every value `'0'`. Counter exists but is constant.
- timestamp: 2026-05-02; check: `query_range` for `pg_stat_database_xact_commit{datname="orders"}` and `rabbitmq_global_messages_received_total`; result: counters increase from `1236154` to `1298348` and from `1371829` to `1440830` over 5 min. So the data is moving — they're not flat-zero.
- timestamp: 2026-05-02; check: `rate(redis_keyspace_hits_total[Xm])` for X∈{30s,1m,2m,5m,10m}; result: empty for `[30s]`, `[1m]`; non-empty starting at `[2m]`. **Smoking gun.**
- timestamp: 2026-05-02; check: `rate(rabbitmq_global_messages_received_total[2m])`; result: `285.3` ops/sec — the panel WOULD render with a 2m window.
- timestamp: 2026-05-02; check: Prometheus `/api/v1/status/config`; result: `global.scrape_interval: 1m`, `evaluation_interval: 1m`, all three exporter jobs inherit `scrape_interval: 1m`.
- timestamp: 2026-05-02; check: `query_range` for `orders_created_total` (OTLP-pushed) and `traces_spanmetrics_calls_total` (Tempo metrics-generator OTLP-pushed); result: native interval 15s for both. So `rate([1m])` over OTLP-pushed metrics has ≥4 samples and works.
- timestamp: 2026-05-02; check: Tempo `/api/v2/search/tag/name/values`; result: 6 span names — `orders process`, `orders publish`, `OrderService.place`, `POST /orders`, `INSERT processed_orders`, `ProcessingService.process`. **`SET` is missing**, so panel id=11 ("Recent SET spans") returns no data — but that is a separate, secondary issue: the load script (`scripts/load.sh`) sends no `X-Idempotency-Key` header and no `orderId` field, so the controller's idempotency gate is bypassed and `InstrumentedJedisPool.setIfAbsent` is never called. The panel's TraceQL query is correct; the data legitimately doesn't exist under current load.

## Eliminated

- H1 (exporter metrics missing) — exporters running, metrics present.
- H2 (Deps attribute key mismatch) — no such panels exist.
- H3 (datasource UID mismatch) — UIDs match.
- H4 (Phase 8 metrics not emitted) — `db_client_connection_count` panel renders correctly.
- H5 (semconv migration) — affected panels use exporter labels, not OTel attribute keys.

## Root Cause (CONFIRMED)

**The Infra dashboard's `rate(...[1m])` queries against scraped exporter metrics fail because Prometheus's effective `global.scrape_interval` is `1m`. With one sample at most inside the rate window, `rate()` returns no result.**

Specifically, every panel that queries an exporter metric inside a `rate(...)` is broken:

| Panel | id | Expression (broken) | Series count |
|---|---|---|---|
| Cache → Commands processed | 20 | `rate(redis_commands_processed_total[1m])` | 0 |
| Cache → Hits/misses | 9 | `rate(redis_keyspace_hits_total[1m])`, `rate(redis_keyspace_misses_total[1m])` | 0 / 0 |
| Cache → Evictions | 10 | `rate(redis_evicted_keys_total[1m])` | 0 |
| DB → Tuple activity | 14 | `rate(pg_stat_database_tup_inserted/updated/fetched{datname="orders"}[1m])` | 0 / 0 / 0 |
| DB → Transactions & deadlocks | 21 | `rate(pg_stat_database_xact_commit/rollback/deadlocks{datname="orders"}[1m])` | 0 / 0 / 0 |
| Broker → Global publish/deliver | 18 | `rate(rabbitmq_global_messages_received/delivered_total[1m])` | 0 / 0 |
| Broker → Acks & redeliveries | 22 | `rate(rabbitmq_queue_messages_acked/redelivered_total{queue!=""}[1m])` | 0 / 0 |

All return non-empty results when the window is widened to `[2m]` or longer. Best practice (Prometheus rule of 4): rate window ≥ 4× scrape interval, i.e. `[5m]` for a 1m scrape. Since the Tempo metrics-generator and OTLP-pushed app metrics push at 15s, those are unaffected (`rate([1m])` against them has 4 samples and works).

The HikariCP panel (id=12) and the static `up`/`numbackends`/`memory` gauges work because they don't use `rate()` — they read instantaneous values.

## Secondary findings (not the user's reported symptom)

- Panel id=11 "Recent SET spans" returns no data because Phase 8's `InstrumentedJedisPool.setIfAbsent` is never invoked under the current load: `scripts/load.sh` posts payloads with neither `X-Idempotency-Key` header nor `orderId` field, so the controller's idempotency gate is skipped (by design — `idempotencyKey == null`). Span name discipline is correct (`"SET"`); query is correct. **Not a dashboard bug.** Possible follow-up: load script could send `X-Idempotency-Key: $(uuidgen)` to exercise the SET path.

- Panel id=15 "Recent INSERT spans" works (5 traces returned for `INSERT processed_orders`).

## Fix Options (CHECKPOINT — user direction needed)

This is a directional decision because there are three valid fixes with different trade-offs:

**Option A — Widen `rate()` windows in dashboard JSON only (RECOMMENDED).**
- Edit `ose-otel-infra.json`: change every `rate(<exporter_metric>[1m])` to `rate(<exporter_metric>[5m])` (Prometheus rule of 4 against the 1m scrape interval). Leave OTLP-pushed metric `rate()` calls alone (they work at `[1m]`).
- Pros: Single-file fix; pure dashboard change; aligns with PromQL best practice; doesn't touch infra config.
- Cons: 5m smoothing is heavier than 1m on a fast-moving demo — peak/trough features get rounded off compared to a hypothetical lower-scrape-interval setup.
- Atomic commit: `fix(grafana/infra): widen rate() windows to 5m to match scrape interval`.

**Option B — Lower `global.scrape_interval` in `grafana/prometheus.yaml` to 15s.**
- Add `global.scrape_interval: 15s` (and `evaluation_interval: 15s`) to the custom `prometheus.yaml`.
- Pros: existing `rate(...[1m])` queries start working everywhere, including any future panels that copy the same idiom.
- Cons: 4× more scrape load on every exporter, persistent storage growth, divergence from otel-lgtm's bundled defaults. Workshop teaching narrative around "this is the bundled prometheus config + 3 scrape jobs" gets a footnote.
- Atomic commit: `fix(grafana/prometheus): lower scrape_interval to 15s for working rate() panels`.

**Option C — Per-job `scrape_interval: 15s` in the three exporter jobs only.**
- Hybrid: leave global at 1m (preserves bundled-defaults narrative), override per-job for the three Step-9 exporters.
- Pros: surgical; lgtm's own internal scraping (rules, metrics about itself) keeps 1m; only the workshop's exporters tighten up.
- Cons: two configuration surfaces to keep in sync if a future scrape job is added; same 4× load on exporters.
- Atomic commit: `fix(grafana/prometheus): tighten exporter scrape_interval to 15s for usable rate() granularity`.

Recommendation: **Option A**. It's the smallest, most defensible change and aligns with the existing NOC dashboard's `[5m]` choice for spanmetrics rate panels (precedent already in this repo). Workshop attendees will see consistent rate-window discipline across dashboards.

## Current Focus

- hypothesis: H6 (rate window < scrape interval) — CONFIRMED.
- test: panel-by-panel instant queries + `query_range` interval inspection + Prometheus config inspection.
- expecting: all `rate(<scraped_metric>[1m])` queries return zero; widening to `[2m]+` returns data — CONFIRMED.
- next_action: AWAITING USER DECISION on Option A / B / C before applying any fix. Once chosen, apply atomically (one commit), verify each formerly-broken query post-fix, and update Resolution section.
- reasoning_checkpoint: |
    The user's task briefing emphasized "rewriting Infra panels in a way that changes their semantic meaning" warrants a checkpoint, and this fix WOULD change semantic meaning slightly (5m smoothing vs hypothetical 1m). It also rules out the user's mental model that this would be a Phase-8-specific data-mismatch fix — the actual bug is dashboard-wide and orthogonal to Phase 8 (the same bug would have existed before Phase 8 if any rate-of-scraped-metric panel had been authored). User should explicitly choose A / B / C since each implies different workshop teaching narratives.
- tdd_checkpoint: not_applicable

## Resolution

**Status:** RESOLVED. User chose Option A (widen `rate()` windows in dashboard JSON only) plus a separate SET-spans bonus fix to `scripts/load.sh`.

### Commits

- **`d6cf738`** — `fix(grafana/infra): widen rate windows to [5m] for scraped exporter metrics`
  - File: `grafana/dashboards/ose-otel-infra.json` (+14 / -14 lines)
  - 14 expressions changed from `rate(<scraped>[1m])` → `rate(<scraped>[5m])` across redis_*, pg_stat_database_*, and rabbitmq_* counters. OTLP-pushed metrics (`http_server_request_duration_seconds_count`, `traces_spanmetrics_*`) intentionally left at `[1m]` because they push at 15s native interval.
- **`e34843a`** — `fix(load): send idempotency key so SET cache writes occur`
  - File: `scripts/load.sh` (+64 / -2 lines)
  - Adds a third lightweight stream (default `IDEMPOTENT_RPS=5` rps) that fires `curl POST` requests with a fresh `X-Idempotency-Key` per request, sourced from `/proc/sys/kernel/random/uuid`. Each unique key is a cache miss within the 1h Phase 8 TTL window, so every request opens a `SET` CLIENT span on the producer, increments `idempotency_cache_miss_total`, and propagates downstream like the existing streams. `oha` can't template headers per-request; bash+curl is the simplest mechanism that produces a fresh key per request.

### Post-fix verification (live Prometheus + Tempo, ~20 s after commit `d6cf738`)

| Panel | id | Expression (post-fix) | Pre-fix series | Post-fix series | Post-fix sample value |
|---|---|---|---|---|---|
| Cache → Commands processed | 20 | `rate(redis_commands_processed_total[5m])` | 0 | 1 | 0.217 ops/s |
| Cache → Hits | 9A | `rate(redis_keyspace_hits_total[5m])` | 0 | 1 | 0 (legit — see causal note below) |
| Cache → Misses | 9B | `rate(redis_keyspace_misses_total[5m])` | 0 | 1 | 0 (legit — see causal note below) |
| Cache → Evictions | 10B | `rate(redis_evicted_keys_total[5m])` | 0 | 1 | 0 (legit — no maxmemory pressure) |
| DB → Inserts | 14A | `rate(pg_stat_database_tup_inserted{datname="orders"}[5m])` | 0 | 1 | 213.5 ops/s |
| DB → Updates | 14B | `rate(pg_stat_database_tup_updated{datname="orders"}[5m])` | 0 | 1 | 0 (legit — INSERT-only workload) |
| DB → Fetched | 14C | `rate(pg_stat_database_tup_fetched{datname="orders"}[5m])` | 0 | 1 | 18.9 ops/s |
| DB → Commits | 21A | `rate(pg_stat_database_xact_commit{datname="orders"}[5m])` | 0 | 1 | 214.1 ops/s |
| DB → Rollbacks | 21B | `rate(pg_stat_database_xact_rollback{datname="orders"}[5m])` | 0 | 1 | 0 (legit — clean run) |
| DB → Deadlocks | 21C | `rate(pg_stat_database_deadlocks{datname="orders"}[5m])` | 0 | 1 | 0 (legit — no contention) |
| Broker → Published | 18A | `rate(rabbitmq_global_messages_received_total[5m])` | 0 | 2 | 237.5 ops/s |
| Broker → Delivered | 18B | `rate(rabbitmq_global_messages_delivered_total[5m])` | 0 | 8 | 237.5 ops/s |
| Broker → Acked | 22A | `sum by (queue) (rate(rabbitmq_queue_messages_acked_total{queue!=""}[5m]))` | 0 | 1 | 213.8 ops/s |
| Broker → Redelivered | 22B | `sum by (queue) (rate(rabbitmq_queue_messages_redelivered_total{queue!=""}[5m]))` | 0 | 1 | 0 (legit — no NACKs) |

All 14 formerly-broken `rate()` queries now return data. Panels showing `0 ops/s` aren't broken — the underlying counters are legitimately flat under the current load (no DB updates, no rollbacks, no deadlocks, no queue redeliveries, no Valkey evictions).

### SET-spans verification (Tempo, post-commit `e34843a`)

- Pre-commit: `tempo /api/v2/search/tag/name/values` returned 6 span names — `orders process`, `orders publish`, `OrderService.place`, `POST /orders`, `INSERT processed_orders`, `ProcessingService.process`. **No `SET`.**
- Post-commit: a single manual `curl -X POST -H 'X-Idempotency-Key: <uuid>' http://localhost:8080/orders` returned `202 Accepted` and ~5s later Tempo's tag-values endpoint returned 7 span names — same six PLUS **`SET`**.
- Once the user restarts `scripts/load.sh` (the running instance is still the pre-commit version), the new idempotency stream will fire 5 fresh-UUID requests per second, generating ~5 SET CLIENT spans/sec into Tempo and increments to `redis_commands_processed_total` (driving panel 20's value above 0.217 toward ~5 ops/s).
- **User action required:** Restart `scripts/load.sh` to pick up the SET-spans stream. Until then, panel 11 ("Recent SET spans") will only show the single span from the manual smoke-test `curl`.

### Causal chain — Cache → Hits/Misses panel

Panel id=9 `redis_keyspace_hits_total` and `redis_keyspace_misses_total` will continue to read 0 ops/s even with the new load until something issues a `GET` against Valkey. The Phase 8 idempotency path uses `SET key value NX EX ttl` only — never a `GET`. So `redis_commands_processed_total` will rise (visible in panel 20 once the new load runs) but `redis_keyspace_hits/misses` remain a "what would change this" teaching moment, not a data-flow sign of demo health. This is documented behavior of the redis_exporter's keyspace counters — they only count `GET`-shaped lookups, not `SET NX` outcomes. The Phase 8 hit/miss outcomes are surfaced via the app's own `idempotency_cache_hit_total` / `idempotency_cache_miss_total` counters (separate panels would be needed to show those, currently not on the Infra dashboard — possible follow-up).

### Out-of-scope items (not addressed; documented for the user)

- The user's pre-existing local edits to `scripts/load.sh` (a `backgrounded` → `background` docstring fix and the `BURST_RPS` default `0 → 150`) were preserved and are now in the working tree as uncommitted changes after this debug session ended. They are visible in `git status` and the user can commit them separately.
- Panel 11 "Recent SET spans" TraceQL query and span name (`"SET"`) were already correct; no code change to the producer was needed (and out of scope per the task briefing).

### Files touched
- `grafana/dashboards/ose-otel-infra.json` (committed `d6cf738`)
- `scripts/load.sh` (committed `e34843a`; pre-existing user edits also in working tree)
- `.planning/debug/infra-deps-dashboards-no-data.md` (this file; new — debug-session record)
