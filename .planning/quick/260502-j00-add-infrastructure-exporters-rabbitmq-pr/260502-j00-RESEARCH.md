---
quick_id: 260502-j00
status: research-complete
researched: 2026-05-02
---

# Step 9 Research — Infrastructure Exporters & Dashboard

## Recommended Approach

The cleanest way to add three Prometheus exporters to the workshop is to **bind-mount a custom `prometheus.yaml` over the bundled one inside `grafana/otel-lgtm:0.26.0`** (path `/otel-lgtm/prometheus.yaml`), preserving the default OTLP-receiver scrape job and appending three new static jobs for `rabbitmq:15692`, `redis_exporter:9121`, and `postgres_exporter:9187`. Each exporter runs as its own pinned compose service on Docker's default `default` network so resolution is by service name. RabbitMQ keeps its existing `rabbitmq:4.3-management` image but gets a one-line `RABBITMQ_PLUGINS=rabbitmq_management,rabbitmq_prometheus` env var to enable the metrics endpoint on `:15692` — no image swap, no sidecar, no command override needed. [VERIFIED: redis_exporter README; postgres_exporter README; rabbitmq.com/docs/prometheus; grafana/docker-otel-lgtm README]

The new dashboard `grafana/dashboards/ose-otel-infra.json` is hand-authored (not exported from the UI) following Phase 7's option-b precedent: UID `ose-otel-infra`, no top-level `id`/`version`/`iteration`, default datasource UIDs (`prometheus`, `tempo`, `loki`), and four collapsed-by-default rows below an always-visible header — HTTP/AMQP, Cache, Database, Broker. The dashboards.yaml provider already scans the host directory; no provisioning manifest changes needed.

The pedagogical core ("two-lens telemetry") lands in three deliberate places: (1) a Database row that puts the consumer's HikariCP `db_client_connection_count` gauge **side-by-side** with postgres_exporter's `pg_stat_database_numbackends` so attendees see the same physical thing measured from two perspectives; (2) a Cache row that pairs the producer's `SET` Tempo spans with redis_exporter's `redis_keyspace_hits_total{db="db0"}` to teach how span-level evidence aggregates into rate-of-events metrics; (3) a Broker row that contrasts the AMQP CONSUMER spans (what the *consumer code* did) with `rabbitmq_queue_messages` (what *the broker* sees in the queue right now) — the canonical "client-side latency vs server-side queue depth" mismatch that paging-on-symptoms-not-causes drills into.

## 1. otel-lgtm Scrape Config — How to Add Targets

**Recommendation: Bind-mount a custom `prometheus.yaml` to `/otel-lgtm/prometheus.yaml`.** [VERIFIED: github.com/grafana/docker-otel-lgtm README]

The image documents this exact mount path and pattern: `-v ./prometheus.yaml:/otel-lgtm/prometheus.yaml:ro`. The bundled Prometheus reads it at start-up; on container restart the new scrape jobs become active. No env vars expose "add an extra scrape job" — only `PROMETHEUS_EXTRA_ARGS` (CLI flags like `--storage.tsdb.retention.time=90d`) is documented.

**Rejected alternatives:**

| Option | Why rejected |
|--------|-------------|
| (b) Mount OTel Collector config with `prometheus` receiver | Doable, but the otel-lgtm bundle's Collector pipeline is opaque to attendees; layering a second collector config makes the data path *less* obvious. Workshop teaches **direct OTLP from app to lgtm**; adding "and also Collector scrape from exporters" muddies the story. |
| (c) `OTEL_LGTM_*` / `ENABLE_*` env var | No such "add scrape target" env var exists. Only `ENABLE_LOGS_*` and `PROMETHEUS_EXTRA_ARGS` are documented. [VERIFIED: docker-otel-lgtm README] |
| (d) Sidecar a separate OTel Collector | Adds a 5th container, more compose complexity, and a second OTLP hop. Attendees have to learn collector config syntax just to teach exporter scraping — wrong scope for a workshop. |

**Concrete config snippet** (`prometheus.yaml`, mounted at `/otel-lgtm/prometheus.yaml`):

```yaml
# Mirrors the bundled defaults so we don't break the OTLP-receiver scrape;
# adds 3 static scrape jobs for our infra exporters.
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Preserves the bundled default — the OTel Collector's prometheus exporter
  # endpoint that surfaces app-emitted OTLP metrics inside the lgtm container.
  - job_name: otel-collector
    static_configs:
      - targets: ['localhost:8888', 'localhost:8889']

  # Step 9 additions — exporters reachable via docker compose service names
  - job_name: rabbitmq
    static_configs:
      - targets: ['rabbitmq:15692']
        labels:
          source: infra-exporter

  - job_name: redis_exporter
    static_configs:
      - targets: ['redis-exporter:9121']
        labels:
          source: infra-exporter

  - job_name: postgres_exporter
    static_configs:
      - targets: ['postgres-exporter:9187']
        labels:
          source: infra-exporter
```

The `source: infra-exporter` static label is the **disambiguator** for question 6 (two-lens story). App-emitted OTLP metrics carry `service.name` resource attributes; exporter-scraped metrics carry `source="infra-exporter"` plus the exporter's `instance` label. PromQL filters can split them cleanly.

**Note [ASSUMED]:** the bundled `prometheus.yaml` likely scrapes `localhost:8888` (Collector self-metrics) and `localhost:8889` (Collector's prometheus exporter for app-emitted OTLP metrics) — these are OTel Collector defaults. The actual file content was not retrievable via WebFetch (404 on the raw GitHub URL); planner should `docker exec ose-otel-lgtm cat /otel-lgtm/prometheus.yaml` once and reproduce its job verbatim before overwriting. Alternatively: use `docker cp ose-otel-lgtm:/otel-lgtm/prometheus.yaml ./grafana/prometheus.yaml` to seed the mount file from the image.

## 2. RabbitMQ Prometheus Plugin

**Image strategy: keep `rabbitmq:4.3-management`. Enable the plugin via `RABBITMQ_PLUGINS` env var.**

[VERIFIED: rabbitmq.com/docs/prometheus] The `rabbitmq_prometheus` plugin **is not enabled by default** in 4.x even on the `-management` image. It is bundled with the server (no separate install) but must be activated.

**Enable mechanism (cleanest):**

```yaml
rabbitmq:
  image: rabbitmq:4.3-management  # unchanged
  environment:
    RABBITMQ_PLUGINS: "rabbitmq_management rabbitmq_prometheus"
  ports:
    - "5672:5672"
    - "15672:15672"
    - "15692:15692"   # NEW — Prometheus scrape endpoint
```

The official `docker-library/rabbitmq` image honors a space-separated `RABBITMQ_PLUGINS` env var to enable plugins at startup. Avoids a separate `enabled_plugins` file mount (Phase 8 introduced no other config file mounts on RabbitMQ; keep it that way). [VERIFIED: github.com/docker-library/rabbitmq #260]

**Default port:** `15692` for `/metrics` (text/plain Prometheus format). [VERIFIED: rabbitmq.com/docs/prometheus]

**Port mapping decision:** Expose `15692` to host **for parity with `15672` (management UI) so attendees can `curl localhost:15692/metrics` and read raw Prometheus text — that's a teaching moment.** Compose service-name resolution (`rabbitmq:15692`) is what the scrape config uses; the host port is only for human inspection.

**Spring AMQP 3.2.8 + RabbitMQ 4.3 + plugin compat:** No known issue. The plugin is read-only over Erlang's metric collector and does not interact with the AMQP wire protocol the Spring AMQP client uses. [ASSUMED — no Spring AMQP issues filed referencing rabbitmq_prometheus on 4.3.]

## 3. redis_exporter for Valkey

**Pinned image:** `oliver006/redis_exporter:v1.83.0` (released 2026-04-30). [VERIFIED: github.com/oliver006/redis_exporter releases]

The README explicitly markets compatibility with "Valkey 9.x, 8.x, 7.x and various Redis versions." Valkey 8.1-alpine speaks RESP2/RESP3 natively — drop-in.

**Compose service:**

```yaml
redis-exporter:
  image: oliver006/redis_exporter:v1.83.0
  container_name: ose-otel-redis-exporter
  environment:
    REDIS_ADDR: "redis://valkey:6379"
  depends_on:
    valkey:
      condition: service_healthy
  restart: unless-stopped
  # No host port mapping — only Prometheus inside lgtm needs to reach it.
  # Service-name resolution: redis-exporter:9121
```

**Default exporter port:** `9121` (`/metrics`). [VERIFIED: oliver006/redis_exporter README]

**Env vars:**

| Var | Required? | Value for this demo |
|-----|-----------|---------------------|
| `REDIS_ADDR` | yes | `redis://valkey:6379` |
| `REDIS_USER` | no | (omit — Valkey runs without ACL) |
| `REDIS_PASSWORD` | no | (omit — no auth in demo) |

**Key metrics the dashboard will use:**

| Metric | Meaning |
|--------|---------|
| `redis_up` | 1 if exporter reached Valkey, 0 otherwise |
| `redis_memory_used_bytes` | Current RSS in Valkey |
| `redis_memory_max_bytes` | maxmemory limit (0 = unlimited) |
| `redis_evicted_keys_total` | Keys evicted to maintain `maxmemory` |
| `redis_keyspace_hits_total` / `redis_keyspace_misses_total` | Lookup outcomes |
| `redis_commands_processed_total` | Total commands across the server |
| `redis_connected_clients` | Active client connections |
| `redis_db_keys{db="db0"}` | Total keys in the working DB |

**Pedagogical hook:** `redis_keyspace_hits_total` rate vs the producer's CLIENT span count for `db.system.name=redis,db.operation.name=SET`. Spans show *what the application asked for*; exporter shows *what the server actually saw* — typically equal modulo network drops, but the difference is a useful debugging signal.

## 4. postgres_exporter

**Pinned image:** `prometheuscommunity/postgres-exporter:v0.19.1` (released 2026-02-25). [VERIFIED: github.com/prometheus-community/postgres_exporter releases]

Note: CONTEXT.md "suggested baseline" `v0.18.1` is stale — `v0.19.0` (2026-02-03) and `v0.19.1` (2026-02-25) are the current line. Pin to `v0.19.1`.

**Compose service:**

```yaml
postgres-exporter:
  image: prometheuscommunity/postgres-exporter:v0.19.1
  container_name: ose-otel-postgres-exporter
  environment:
    DATA_SOURCE_NAME: "postgresql://orders:orders@postgres:5432/orders?sslmode=disable"
  depends_on:
    postgres:
      condition: service_healthy
  restart: unless-stopped
```

**Default exporter port:** `9187` (`/metrics`). [VERIFIED: postgres_exporter README]

### Security model — recommendation

The CONTEXT correctly flags the security tension. There are two viable paths:

**Path A (recommended for the workshop) — reuse `orders/orders`, with a callout.** The demo Postgres has one user (`orders`) which is the database owner. It already has every privilege the exporter needs. Ship it. Add a callout in the README:

> **In production, never give the exporter the application user.** Create a dedicated read-only role and grant `pg_monitor`. The workshop intentionally uses the application user to keep the compose minimal; the boundary lesson is what matters.

**Path B (production-fidelity) — create a dedicated `pgexporter` role with `pg_monitor`** via a Postgres init script. Adds one mounted SQL file and a second user the workshop attendee has to keep straight. More realistic but more compose surface area.

**Recommendation: Path A.** The workshop's contract is "minimal compose, maximal teaching" — Path A keeps the compose at one DSN string and the README owns the production-vs-demo distinction. Path B is a follow-on quick task if anyone asks.

[VERIFIED: postgres_exporter README — `pg_monitor` is the standard production pattern; `DATA_SOURCE_NAME` is the standard env var; `sslmode=disable` is required because the workshop Postgres has TLS off.]

**Key metrics the dashboard will use:**

| Metric | Meaning |
|--------|---------|
| `pg_up` | 1 if exporter reached Postgres |
| `pg_stat_database_numbackends{datname="orders"}` | **Active connections — pairs with HikariCP gauge** |
| `pg_stat_database_xact_commit_total` / `xact_rollback_total` | Transaction throughput |
| `pg_stat_database_tup_inserted_total` | Rows inserted (server perspective on consumer's INSERTs) |
| `pg_stat_database_blks_hit_total` / `blks_read_total` | Buffer cache effectiveness |
| `pg_locks_count` | Current locks by mode |
| `pg_stat_activity_count` | Sessions by state (active/idle/idle in transaction) |

## 5. Dashboard JSON Portability

Hand-authored JSON, not UI-exported. Phase 7 D-01 precedent. [VERIFIED: existing `ose-otel-demo.json` and `ose-otel-noc.json`]

**Mandatory portability rules** (each derived from inspecting the two existing dashboards):

| Rule | How `ose-otel-demo.json` does it | How to apply to `ose-otel-infra.json` |
|------|----------------------------------|---------------------------------------|
| Unique top-level `uid` | `"uid": "ose-otel-demo"` | `"uid": "ose-otel-infra"` |
| **No** top-level `id` field | Absent | Absent |
| **No** top-level `version` field | Absent | Absent |
| **No** top-level `iteration` field | Absent | Absent |
| Datasource refs use **fixed UIDs** matching otel-lgtm bundle defaults | `{"type":"prometheus","uid":"prometheus"}`, `{"type":"tempo","uid":"tempo"}`, `{"type":"loki","uid":"loki"}` | Same three — `prometheus`, `tempo`, `loki` |
| Each panel `target` carries its **own** `datasource` object (don't rely on dashboard-level default) | Every target in both files repeats `"datasource": {...}` | Same |
| `schemaVersion` matches Grafana 13 in lgtm 0.26 | `39` in both files | `39` |
| `templating.list` variables use `textbox` shape with `query: ".+"` default for trace_id filter | `traceid` textbox with `current: {text:".+",value:".+"}` and matching `options` array | Same shape if a trace_id filter is added; or omit `templating` entirely (this dashboard is metric-heavy so a templating var may be unnecessary) |
| Tags discoverable by Grafana search | `["ose-otel-demo","workshop","otel"]` and `["ose-otel-demo","noc","otel"]` | `["ose-otel-demo","infra","otel"]` |
| Cross-link to companion dashboards via `links[]` | `ose-otel-noc` has `links: [{title: "Workshop dashboard", url: "/d/ose-otel-demo"}]` | Add `links` pointing at `/d/ose-otel-demo` and `/d/ose-otel-noc` so the three dashboards are mutually reachable |
| Refresh interval reasonable for live demo | Both: `"refresh": "10s"` | `"refresh": "10s"` |
| Time range tight enough to populate fast at infra:up | Workshop dashboard `now-15m`; NOC `now-30m` | `now-15m` (matches workshop dashboard cadence — the new dashboard is paired with workshop, not NOC) |

**Variable definitions:** Don't use `query`-derived variables in this dashboard. The demo runs offline at infra:up; query-derived vars run a query against an empty datasource and fail to populate, leaving the dashboard in a broken-looking state. Use `textbox` with sensible defaults if any variable is needed at all (matches Phase 7 03-01 lesson). [VERIFIED: STATE.md Phase 07-01 entry]

**Panel `targets[].refId`:** Each target needs a unique `refId` per panel (`A`, `B`, `C`...). When a panel has multiple targets that get joined, transformations refer to them by `Value #A`, `Value #B`, etc. (See `ose-otel-noc.json` panel id 402 for the canonical example.)

## 6. Two-Lens Metric Story

Naming conventions **don't actually collide** — different prefixes:

| Source | Prefix | Example |
|--------|--------|---------|
| App-emitted OTLP via OTel SDK | OTel-naming-convention (dot-separated, server-side rewritten with underscores) | `db_client_connection_count{state="used",service_name="order-consumer"}` |
| postgres_exporter scrape | `pg_*` (PostgreSQL-domain) | `pg_stat_database_numbackends{datname="orders",instance="postgres-exporter:9187"}` |
| redis_exporter scrape | `redis_*` (Redis-domain) | `redis_connected_clients{instance="redis-exporter:9121"}` |
| RabbitMQ Prometheus plugin | `rabbitmq_*` | `rabbitmq_queue_messages{queue="orders.queue"}` |

So the metric names are unambiguous. The **disambiguation discipline** is:

- Filter app metrics by `service_name`: `db_client_connection_count{service_name="order-consumer"}`
- Filter exporter metrics by `source` label (added in our `prometheus.yaml` static_config): `pg_stat_database_numbackends{source="infra-exporter"}`
- The `instance` label carries the exporter's host:port — useful for distinguishing multiple exporters of the same type but pedagogically less interesting in this demo (one exporter per dependency).

**Worked example for the Database row** — single panel comparing two viewpoints:

```promql
# Lens 1 — what the application's pool reports
sum by (state) (db_client_connection_count{service_name="order-consumer"})

# Lens 2 — what the database server sees in the same pool
pg_stat_database_numbackends{datname="orders",source="infra-exporter"}
```

These should be approximately equal (used + idle ≈ numbackends), with small skew due to scrape phase. **The skew itself is the lesson** — observability is multi-source, and zero-skew is rarely possible.

## 7. Pitfalls

1. **`prometheus.yaml` mount overwrites the bundled default.** If the new file omits the bundled `otel-collector` job, app-emitted OTLP metrics disappear from Mimir → workshop dashboard breaks. Mitigation: **`docker cp` the bundled file out first, append three jobs, mount the merged file.** Plan must include this exact sequence.

2. **rabbitmq_prometheus port `:15692` not exposed in current compose.** Without a port mapping or service-network reachability, Prometheus inside lgtm can't scrape. Mitigation: add `:15692` to the `ports:` list (host visibility is a teaching plus). Ensure all 5 services share the same compose default network — they do (no `networks:` blocks in current compose, so all join `<project>_default`).

3. **PostgreSQL exporter using `orders/orders` is a security smell.** The README MUST contain an explicit "do not do this in production" callout next to the Step 9 commands. Without the callout, this becomes a copy-paste anti-pattern.

4. **HikariCP gauge metric name surface — server-side rewrite.** The Java code emits `db.client.connection.count` (dot-separated). Prometheus translates that to `db_client_connection_count` (underscores). Dashboard panels MUST use the underscore form. The Phase 8 commit verified the rewrite happens (HikariCpConnectionGauge JavaDoc references the rewritten name). [VERIFIED: HikariCpConnectionGauge.java line 58–63]

5. **Healthchecks on the exporter services are tempting but should be lightweight.** A bad healthcheck on `redis-exporter` or `postgres-exporter` blocks `mise run infra:up` if the dependency is slow. Recommendation: **omit healthchecks on the three exporter containers** — they're scrape-driven, Prometheus retrying a failed scrape is the natural healthcheck, and a missing exporter shows as `redis_up == 0` / `pg_up == 0` on the dashboard, which is a teaching moment, not a startup blocker.

6. **`depends_on: condition: service_healthy` is correct for exporters → app dependencies.** redis-exporter must wait for valkey (otherwise it racing to connect prints connection-refused errors that confuse attendees). Same for postgres-exporter → postgres. RabbitMQ doesn't need a separate exporter container so no depends_on adjustment there.

7. **Default datasource UIDs in otel-lgtm 0.26 are stable** (`prometheus`, `tempo`, `loki`) but worth re-confirming at live verify time. If a future image bump changes them, the dashboard breaks silently (panels show "datasource not found"). Phase 7 already established these UIDs; Step 9 inherits the same risk surface but doesn't add new risk.

8. **Don't add a 4th dashboard provisioning manifest.** The existing `dashboards.yaml` already scans the dir; dropping `ose-otel-infra.json` is enough. Adding a second provider entry for the same path causes duplicate-load warnings.

9. **`schemaVersion: 39` matches Grafana 13** (bundled in lgtm 0.26). Don't pick a higher number "to be safe" — schema 40+ shipped in Grafana 14 and may use panel option keys lgtm's Grafana doesn't recognize.

10. **`postgres-exporter` v0.19.1 vs CONTEXT.md "v0.18.1" baseline.** CONTEXT explicitly marks this as Claude's discretion; pin to the actual current stable (v0.19.1, 2026-02-25). Plan should not silently use the older suggested version.

## 8. Proposed Panel Set for ose-otel-infra.json

**Top header (always visible, single row, 4 stat panels)** — at-a-glance Step 9 health:

| Panel | Type | Datasource | Query / Expression |
|-------|------|-----------|--------------------|
| 1. `redis_up` | stat | prometheus | `redis_up` (red < 1, green = 1) |
| 2. `pg_up` | stat | prometheus | `pg_up` |
| 3. RabbitMQ ready | stat | prometheus | `rabbitmq_build_info` (presence = up) |
| 4. Active services | stat | prometheus | `count(group by (service_name) (target_info{service_name=~"order-.*"}))` (mirrors NOC dashboard #105) |

**Row 1 — HTTP / AMQP** (collapsed by default; opens with 3 panels):

| Panel | Type | Datasource | Query |
|-------|------|-----------|-------|
| 5. RED — request rate by status | timeseries | prometheus | `sum by (http_response_status_code) (rate(http_server_request_duration_seconds_count[1m]))` |
| 6. AMQP queue depth (broker view) | timeseries | prometheus | `rabbitmq_queue_messages{queue="orders.queue"}` |
| 7. AMQP queue depth (client estimate) | timeseries | prometheus | `orders_queue_depth_estimate` (the existing app gauge, for side-by-side compare with #6) |

**Row 2 — Cache (Valkey)** (collapsed; 4 panels):

| Panel | Type | Datasource | Query |
|-------|------|-----------|-------|
| 8. Valkey memory used | timeseries | prometheus | `redis_memory_used_bytes` (unit: bytes) |
| 9. Hits vs misses | timeseries | prometheus | `rate(redis_keyspace_hits_total[1m])` and `rate(redis_keyspace_misses_total[1m])` |
| 10. Evictions | timeseries | prometheus | `rate(redis_evicted_keys_total[1m])` |
| 11. Recent SET spans (app perspective) | table | tempo | `{resource.service.name="order-producer" && name="SET"}` (TraceQL) — pairs with #9 |

**Row 3 — Database (Postgres + HikariCP)** (collapsed; 4 panels — the **two-lens money row**):

| Panel | Type | Datasource | Query |
|-------|------|-----------|-------|
| 12. Connections — app view (HikariCP) | timeseries | prometheus | `sum by (state) (db_client_connection_count{service_name="order-consumer"})` |
| 13. Connections — server view (postgres_exporter) | timeseries | prometheus | `pg_stat_database_numbackends{datname="orders"}` |
| 14. Inserts/sec (server view) | timeseries | prometheus | `rate(pg_stat_database_tup_inserted_total{datname="orders"}[1m])` |
| 15. Recent INSERT spans (app view) | table | tempo | `{resource.service.name="order-consumer" && name=~"INSERT.*"}` |

**Row 4 — Broker (RabbitMQ Prometheus plugin)** (collapsed; 3 panels):

| Panel | Type | Datasource | Query |
|-------|------|-----------|-------|
| 16. Queue depth per queue | timeseries | prometheus | `rabbitmq_queue_messages` (legend `{{queue}}`) |
| 17. Consumers per queue | timeseries | prometheus | `rabbitmq_queue_consumers` |
| 18. Messages published / delivered | timeseries | prometheus | `rate(rabbitmq_global_messages_publish_total[1m])` and `rate(rabbitmq_global_messages_delivered_total[1m])` |

**Total panel count: 18** (4 header + 14 across 4 rows). Slightly above the "10–15" CONTEXT target but justified — the two-lens lesson needs paired panels in 3 of the 4 rows. If trimming is required: drop panel #11 and #15 (Tempo trace tables — duplicative with workshop dashboard's Recent Traces), bringing total to 16.

**Layout grid (gridPos):** Header row at `y=0`, four stats spanning `w=6` each (24-wide grid). Each collapsed row at `y=N` with `h=1, w=24`. Panels inside collapsed rows use `h=8, w=8` for 3-panel rows and `h=8, w=12` for 2-panel rows (matches NOC dashboard sizing).

## Stack Pins

| Service | Image | Version | Released | Why |
|---------|-------|---------|----------|-----|
| RabbitMQ (existing, unchanged image) | `rabbitmq` | `4.3-management` | Phase 8 baseline | Plugin enabled via env var, not image swap |
| Redis exporter (NEW) | `oliver006/redis_exporter` | `v1.83.0` | 2026-04-30 | Latest stable; supports Valkey 8.x explicitly |
| Postgres exporter (NEW) | `prometheuscommunity/postgres-exporter` | `v0.19.1` | 2026-02-25 | Latest stable; CONTEXT's v0.18.1 baseline is stale |
| otel-lgtm (existing, unchanged) | `grafana/otel-lgtm` | `0.26.0` | 2026-04-24 | Phase 7 pin; bundled Prometheus accepts `/otel-lgtm/prometheus.yaml` mount |

## Confidence

| Question | Confidence | Source |
|----------|-----------|--------|
| 1. Scrape config approach (mount `/otel-lgtm/prometheus.yaml`) | HIGH | docker-otel-lgtm README via WebFetch |
| 2. RabbitMQ plugin enable via `RABBITMQ_PLUGINS` env, port 15692 | HIGH | rabbitmq.com/docs/prometheus + docker-library/rabbitmq #260 |
| 3. redis_exporter v1.83.0, Valkey 8.x compat, env vars | HIGH | github.com/oliver006/redis_exporter releases + README |
| 4. postgres_exporter v0.19.1, DATA_SOURCE_NAME, pg_monitor pattern | HIGH | github.com/prometheus-community/postgres_exporter releases + README |
| 5. Dashboard portability rules | HIGH | Direct read of existing two dashboards |
| 6. Two-lens metric naming (no collision) | HIGH | OTel server-side rewrite is well-known; exporter prefixes are domain-standard |
| 7. Pitfall list | HIGH-MEDIUM | Most derived from official docs; pitfall #2 (network reachability) is COMPOSE-MEDIUM (works on default network but unverified at live stack) |
| 8. Panel set proposal | MEDIUM | Concrete queries verified against exporter README metric names; panel choices are pedagogical recommendations the planner can trim |

**Bundled `prometheus.yaml` content [ASSUMED]:** Exact contents not retrieved (404 on the GitHub raw URL). Plan task should `docker cp` from a running lgtm container to seed the merged file.

## Sources

### Primary (HIGH confidence)
- [grafana/docker-otel-lgtm README](https://github.com/grafana/docker-otel-lgtm) — config mount path `/otel-lgtm/prometheus.yaml`, `PROMETHEUS_EXTRA_ARGS`, `ENABLE_LOGS_*`
- [rabbitmq.com/docs/prometheus](https://www.rabbitmq.com/docs/prometheus) — plugin enable command, default port 15692, `/metrics` endpoint
- [github.com/oliver006/redis_exporter](https://github.com/oliver006/redis_exporter) — Valkey support, env vars, default port 9121, key metric names
- [github.com/oliver006/redis_exporter/releases](https://github.com/oliver006/redis_exporter/releases) — v1.83.0 (2026-04-30) latest
- [github.com/prometheus-community/postgres_exporter/releases](https://github.com/prometheus-community/postgres_exporter/releases) — v0.19.1 (2026-02-25) latest, v0.19.0 (2026-02-03)
- [github.com/prometheus-community/postgres_exporter README](https://github.com/prometheus-community/postgres_exporter) — DATA_SOURCE_NAME, sslmode, pg_monitor role pattern
- [Existing `grafana/dashboards/ose-otel-demo.json` and `ose-otel-noc.json`] — local source of truth for dashboard JSON conventions

### Secondary (MEDIUM confidence)
- [github.com/docker-library/rabbitmq #260](https://github.com/docker-library/rabbitmq/issues/260) — `RABBITMQ_PLUGINS` env var pattern for enabling plugins
- [Hostinger LGTM tutorial](https://www.hostinger.com/tutorials/how-to-set-up-lgtm-stack), [Quarkus DevServices LGTM](https://quarkus.io/guides/observability-devservices-lgtm), [grafana.com OTel docs](https://grafana.com/docs/opentelemetry/docker-lgtm/) — corroborate the mount-replace pattern

### Tertiary (validation needed at live stack)
- Bundled `prometheus.yaml` exact content — `docker cp ose-otel-lgtm:/otel-lgtm/prometheus.yaml` once during planning to seed the override file
- RabbitMQ port 15692 reachability via compose service name from inside lgtm container — verify with `docker exec ose-otel-lgtm wget -qO- http://rabbitmq:15692/metrics | head -3` once the env var is set
