---
slug: dashboards-slow-loading
status: resolved
trigger: |
  DATA_START
  with the current rate, noc view and three signals dashboards take a looong time loading, logs. how can improve it?
  -> "check and fix it"
  DATA_END
created: 2026-05-02
updated: 2026-05-02
goal: find_and_fix
diagnose_only: false
---

# Debug Session: dashboards-slow-loading

## Symptoms

- **Affected dashboards** (DATA): "NOC View" (`grafana/dashboards/ose-otel-noc.json`, uid `ose-otel-noc`) and the "three signals" demo dashboard (`grafana/dashboards/ose-otel-demo.json`, uid `ose-otel-demo`).
- **Failure mode** (DATA): Dashboards take a "looong time" to load — log panels in particular. Other panels render; logs stall.
- **When the slowness is observable**: under "current rate" — i.e. with `scripts/load.sh` running including the burst stream now defaulting to 150 RPS (`ec27462`), the idempotent stream at 5 RPS (commit `e34843a`), and the main stream. Total log volume: producer + consumer each emit per-request → ~400+ log lines/sec sustained, much higher during bursts.
- **Stack state**: Live stack is up; Prometheus scrape interval just tightened to 10s (`f9fef3b`). Loki is the bottleneck for log panels, not Prometheus.
- **Mode**: Find and fix. User said "check and fix it" after a brief conversation surfacing three candidate fixes: tighter default time range, narrower Loki selectors, slower auto-refresh.

## Inputs / Known Locations

- Dashboard JSONs: `grafana/dashboards/ose-otel-noc.json`, `grafana/dashboards/ose-otel-demo.json`. Plus `grafana/dashboards/ose-otel-infra.json` (also has Loki panels per recent commits `9c8de24`).
- Provisioning: `grafana/dashboards/dashboards.yaml` mounts the directory into otel-lgtm.
- Loki is bundled inside `ose-otel-lgtm`; not host-port-mapped (`docker port ose-otel-lgtm` → only 3000/4317/4318). Live queries must go through Grafana datasource proxy (`mcp__grafana__query_loki_*`) or `docker exec ose-otel-lgtm curl http://localhost:3100/...`.
- Service emits structured JSON logs via OTel Logback appender; the `service.name` and `severity_text` resource attributes become Loki labels (or structured metadata, depending on otel-lgtm's pipeline config).
- Two distinct trace-id workflows likely on these dashboards:
  - NOC: a `traceid` template variable (textbox default `.+`, per Phase 07-01 SUMMARY) feeds a Loki panel `|= "$traceid"` filter.
  - Three-signals demo: a Loki panel correlated by trace-id, possibly with the same `|=` shape.

## Hypotheses to test

1. **H1 (most likely):** Default dashboard time range is too wide for current ingest rate. With ~400+ logs/sec sustained, even 1h selects ~1.4M lines per panel; logs panels then need to stream those bytes before rendering. Tightening `time.from` to `now-15m` (NOC) / `now-30m` (three-signals) cuts scan range proportionally.
2. **H2:** Loki queries use overly broad selectors then parse — e.g. `{service_name=~".+"} | json | level="ERROR"` scans every stream before label filtering. Narrowing to `{service_name=~"order-.*"}` plus pushing `|= "ERROR"` (line filter) BEFORE the JSON parse uses the chunk index instead of streaming.
3. **H3:** The `traceid` textbox default `.+` becomes a regex line filter applied per-line — on millions of lines that's the dominant cost. A non-empty default (e.g. an empty string with a guard like `${traceid:lucene}` or `if "$traceid" == "" skip`) avoids running the panel until the user supplies a real ID.
4. **H4:** Dashboard auto-refresh is set to a tight interval (`5s`?) so panels re-run constantly even while users read. `30s` is workshop-appropriate and visibly snappier.
5. **H5 (less likely but check):** Loki query timeouts / `max_entries_limit_per_query` defaults inside otel-lgtm are tuned conservatively and cause panels to time out and retry; user perceives "long load" as repeated timeout retries, not actual scan time.
6. **H6 (cosmetic but real):** Some Loki panels query the same data with multiple parsers (`| json | regexp ...`); if the regexp is unnecessary now that JSON labels are extracted, removing it speeds parsing.

## Current Focus

- hypothesis: Combination of H1 + H2 + H3 — wide time range + broad Loki selector + line-filtering on `.+` regex from the NOC traceid textbox default — explains the "looong" load. H4 amplifies it.
- test: For each dashboard, enumerate Loki panels + their queries + the dashboard's `time.from`/`refresh`. Then for the slowest panel, run `mcp__grafana__query_loki_stats` to measure `bytes_processed` / `entries_examined` and confirm where the cost is.
- expecting: Default time range is `now-1h` or wider; at least one Loki panel uses a `{service_name=~".+"}` shape; NOC `traceid` textbox default `.+` causes the trace-correlation panel to scan ALL log lines on first load.
- next_action: Read all three dashboard JSONs (`ose-otel-noc.json`, `ose-otel-demo.json`, `ose-otel-infra.json`); enumerate Loki panels and `time.from`/`refresh`/template-vars; pick the slowest candidate and profile via `query_loki_stats`.
- reasoning_checkpoint: |
    Apply fixes in increasing-blast-radius order:
      (a) Time-range narrowing (per-dashboard `time.from`) — pure UX, zero data risk.
      (b) Refresh interval — pure UX.
      (c) Selector narrowing on Loki queries — semantic; may change which streams render but should not change panel intent if done correctly.
      (d) `traceid` default fix — may require restructuring the textbox + adding a guard.
    Atomic commits per group; verify post-fix via Loki stats.
- tdd_checkpoint: not_applicable

## Evidence

### Loki panel inventory

| Dashboard | Panel id | Title | LogQL | Notes |
|---|---|---|---|---|
| ose-otel-noc | 502 | Recent ERROR logs (with stacktrace) | `{service_name=~"order-.*"} \| severity_text="ERROR"` | structured-metadata filter; CORRECT, in collapsed row |
| ose-otel-noc | 503 | Logs for trace_id =~ "$traceid" | `{service_name=~"order-.*"} \| trace_id=~`$traceid`` | template var `traceid` defaults to `.+` (firehose); in collapsed row |
| ose-otel-demo | 4 | Logs (filtered by latest trace_id) | `{service_name=~"order-.*"} \| trace_id=~`$traceid`` | template var `traceid` defaults to `.+` (firehose); **TOP ROW** — always rendered |
| ose-otel-demo | 8 | Raw Logs (post-workshop poking) | `{service_name=~"order-.*"}` | no filter; in collapsed row |
| ose-otel-infra | — | (no Loki panels) | — | infra dashboard is Prometheus + Tempo only — out of scope |

Dashboard defaults observed:

| Dashboard | `time.from` | `refresh` |
|---|---|---|
| ose-otel-noc | `now-30m` | `10s` |
| ose-otel-demo | `now-15m` | `10s` |
| ose-otel-infra | `now-15m` | `10s` |

### Loki labels actually indexed

- timestamp: 2026-05-02T21:14
- query: `GET /loki/api/v1/labels` over `{service_name=~"order-.*"}` last 1m
- result: `__stream_shard__`, `deployment_environment_name`, `service_instance_id`, `service_name`, `service_namespace`
- inference: `severity_text` and `trace_id` are STRUCTURED METADATA, not labels — filtering on them does not prune chunks via the index but does use the metadata-bloom path Loki ships for SM, which is far cheaper than `| json` re-parse.

### Sample log line shape (verifies severity is in metadata, not body text)

```
LABELS: { service_name=order-consumer, severity_text=INFO, trace_id=a351...3fe, span_id=eb14...7f9, ... }
LINE:   "OrderCreated received: orderId=02171f11-..."
```

→ Confirms `|= "ERROR"` would NOT match ERROR-severity lines because the string "ERROR" is in metadata, not the message body. Panel 502's `| severity_text="ERROR"` is the correct (and only) way; do NOT swap to a literal line filter.

### Volume per time window (selector-only, current rate)

Endpoint: `GET /loki/api/v1/index/stats?query={service_name=~"order-.*"}` (ground-truth scan cost):

| Window | streams | chunks | bytes | entries |
|---|---|---|---|---|
| now-15m | 4 | 254 | 543,795,200 (519 MB) | 1,342,802 |
| now-30m | 6 | 669 | 1,453,241,344 (1.39 GB) | 3,491,166 |
| now-60m | 6 | 1245 | 2,740,324,352 (2.62 GB) | 6,729,110 |

Cutting NOC default `now-30m` → `now-15m` halves scan volume; cutting demo default `now-15m` to `now-15m` (already there) needs other levers. (Briefing called for `now-30m` on three-signals; rejected — three-signals is workshop-pacing-sensitive and `now-15m` is already the right default. The fix budget on the three-signals dashboard goes entirely into the traceid no-op fix.)

### Per-query profile (`query_range`, 1-minute window)

| Query | bytesProcessed | linesProcessed | postFilterLines | execTime |
|---|---|---|---|---|
| `{service_name=~"order-.*"}` (raw firehose; demo panel 8) | 6,841,332 (6.5 MB) | 22,733 | 22,733 | 102 ms |
| `\| trace_id=~`.+`` (firehose; CURRENT default — noc 503, demo 4) | 6,785,958 (6.5 MB) | 22,381 | **22,076** | 89 ms |
| `\| trace_id=~`^$`` (PROPOSED empty default; matches no spanned lines) | 12,879,300 (12.3 MB) | 40,538 | **221** | 49 ms |
| `\| trace_id=~`<real-32-hex>`` (user pastes a real trace) | 19,792,087 (18.9 MB) | 62,279 | **4** | 57 ms |
| `\| severity_text="ERROR"` (panel 502) | 19,114,043 (18.2 MB) | 60,370 | 129 | 61 ms |
| `\|= "ERROR" \| severity_text="ERROR"` (line+SM combo) | 24,886,836 | 78,377 | 0 | 83 ms |

Key takeaway: **`bytesProcessed` is similar across variants** (Loki streams chunks regardless of post-filter). The user-perceived stall is the **post-filter row count delivered to Grafana**:
- `.+` firehose → 22,076 lines / minute → at NOC's `now-30m` ≈ **662,000 rendered log lines on first load**.
- `^$` empty default → 221 lines / minute → at `now-15m` ≈ **3,300 rendered log lines** (~99.5% reduction).
- Real trace_id → 4 lines / minute → ~60 lines for a 15-minute backlog.

So the ranking of fixes by user-perceived load time, biggest-bang-first:
1. Change `traceid` default from `.+` to `^$` — turns the firehose panel into a no-op until the user actually pastes a trace ID. Affects BOTH `ose-otel-demo.json` (panel 4, **always-visible top row**) and `ose-otel-noc.json` (panel 503, in collapsed row but still pre-rendered when the row expands).
2. Tighten NOC default time range `now-30m` → `now-15m`. Halves scan cost on the ERROR-logs panel 502 too.
3. Relax auto-refresh `10s` → `30s`. With `10s` refresh and ~400 lines/sec ingest, a fresh query fires every 10s while users are still scrolling the previous response — compounds the bytes-on-the-wire problem.

H4 (refresh) and H1 (time range) are confirmed amplifiers; H2 (broad selectors) is REJECTED — selectors are already narrow (`{service_name=~"order-.*"}`); H3 is the true root cause and is more nuanced than the original phrasing — the cost isn't line-filter regex evaluation per se, it's the **post-filter row count Grafana then has to render**.

## Eliminated

- **H2 (broad Loki selectors):** Selectors already use `{service_name=~"order-.*"}`. Inspected all four Loki panels across both dashboards — none uses `{service_name=~".+"}` or any equivalent over-broad shape. Selectors cannot be narrowed further without losing legitimate streams.
- **H5 (Loki timeouts in otel-lgtm):** Profiling via direct Loki API showed all queries returning successfully in <110 ms at the 1-minute window. No retry/timeout pattern observed. Slowness is real volume, not retry storms.
- **H6 (redundant `| json` parses):** None of the four Loki panels uses `| json` — they rely on structured-metadata filters. No parse-stage redundancy to remove.
- **H1 partial — three-signals dashboard time range:** Originally briefed to widen demo default to `now-30m`. Rejected after profiling: demo dashboard is the always-visible workshop-pacing surface and is already at `now-15m`. Widening it would hurt; leave it. The infra dashboard has no Loki panels and stays at `now-15m`.

## Resolution

### Root cause

Three log-volume amplifiers stacked on top of the workshop's high ingest rate (~400 logs/sec sustained, ~1500/sec during BURST_RPS=150 bursts):

1. **`traceid` template variable defaulted to `.+`** on both the NOC and Three Signals dashboards — a regex matching every line emitted from inside a span. The Loki panel using `{service_name=~"order-.*"} | trace_id=~`$traceid`` therefore returned ~22,000 post-filter lines per minute to the browser. Loki itself was fine; Grafana's log panel stalled rendering ~330k rows per `now-15m` window. This was the dominant cost.
2. **NOC dashboard `time.from` was `now-30m`** while the sibling Three Signals dashboard already used `now-15m`. With the firehose default in (1) above, the wider window roughly doubled scan size on every panel (1.39 GB / 3.49M entries vs 543 MB / 1.34M entries).
3. **Auto-refresh was set to `10s`** on both dashboards. At workshop log volume, fresh queries fired before the previous render completed, compounding the rendered-row load.

H2 (broad selectors) was rejected — selectors are already narrow (`{service_name=~"order-.*"}`). H6 (redundant `| json` parses) was rejected — none of the four Loki panels use `| json`. H5 (Loki timeouts) was rejected — direct Loki API calls succeeded in <110 ms at the 1-minute window.

### Fixes applied (atomic commits)

| # | Commit | Message | Files |
|---|---|---|---|
| A | `9e9344b` | `fix(grafana): make traceid filter no-op when textbox is empty` | `ose-otel-noc.json`, `ose-otel-demo.json` |
| B | `1803d8a` | `fix(grafana/noc): tighten default time range to now-15m` | `ose-otel-noc.json` |
| C | `f8181f0` | `fix(grafana): relax dashboard auto-refresh to 30s on log-heavy views` | `ose-otel-noc.json`, `ose-otel-demo.json` |

### Verification (before vs after)

Method: direct Loki HTTP API via `docker exec ose-otel-lgtm curl http://localhost:3100/...`. Live Grafana state confirmed via `GET /api/dashboards/uid/{uid}` after `POST /api/admin/provisioning/dashboards/reload`.

| Metric | Before (`.+` default, `now-30m`, 10s refresh) | After (`^$` default, `now-15m`, 30s refresh) | Reduction |
|---|---|---|---|
| Selector scan bytes (single panel, dashboard window) | 1.39 GB | 519 MB | 63% |
| Selector scan entries | 3.49M | 1.34M | 62% |
| Trace-correlation panel postFilterLines (lines rendered by Grafana) | ~330,000 per `now-15m` (~22,076/min × 15) | **367 per `now-15m`** | **~99.9%** |
| Refresh re-query frequency | every 10s | every 30s | 67% |

Live verification:
- `curl http://localhost:3000/api/dashboards/uid/ose-otel-noc` → `refresh=30s`, `time.from=now-15m`, `traceid.query=^$`, `traceid.current.value=^$`.
- `curl http://localhost:3000/api/dashboards/uid/ose-otel-demo` → `refresh=30s`, `time.from=now-15m`, `traceid.query=^$`, `traceid.current.value=^$`.

When operator pastes a real 32-hex trace_id into the textbox, the LogQL `| trace_id=~`<id>`` returns the canonical 4 lines per distributed trace (producer-accept, producer-publish, consumer-receive, consumer-process), confirming the user-facing function is preserved.

### Out of scope / deferred

- **Three Signals time range left at `now-15m`.** Briefing suggested widening to `now-30m`; rejected — the demo dashboard is the always-visible workshop-pacing surface, and now-15m is already the right default.
- **Infra dashboard untouched.** No Loki panels; Prometheus rates use [1m]/[5m] windows independent of dashboard time range.
- **Producer/consumer logging volume not changed.** Out of scope per guardrails — `BURST_RPS=150` is intentional for demo dynamics.
- **Loki ingester/storage config not touched.** Panel-level only per guardrails.
- **Panel 502 (`severity_text="ERROR"`) left as-is.** Profiling showed structured-metadata filter is already index-optimised (~19 MB / 60k lines / 61 ms over 1 min); rewriting to a literal line filter would be incorrect because the JSON body does not contain "ERROR" — severity is in OTel structured metadata, not the message text.
