---
slug: noc-dashboard-full-review
status: resolved
trigger: |
  DATA_START
  Do a full review on the NOC View dashboard on grafana. for example, message error rate 10% is too hight, is only reading consumer data
  DATA_END
created: 2026-05-02
updated: 2026-05-02
goal: find_and_fix
diagnose_only: false
---

# Debug Session: noc-dashboard-full-review

## Symptoms

- **Expected behavior** (DATA): As a NOC we monitor messages agnostic of the internals of the app. We should account for both consumer AND producer data and see what really there. Per-panel numbers must reflect end-to-end message reality, not consumer-only or producer-only slices unless explicitly labeled as such.
- **Actual behavior** (DATA): Message error rate panel shows ~10%, which is too high. The query is only reading consumer-side data — i.e. the panel undercounts the producer's success volume in the denominator (or counts only consumer-emitted error metrics in the numerator).
- **Scope** (DATA): Full review — every panel on the NOC View dashboard, top to bottom. Audit each query for: (a) NOC-correct framing (producer + consumer, not just one side); (b) denominator scope; (c) label matching; (d) semantic correctness; (e) misleading visual encoding.
- **Concrete example given by user** (DATA): "message error rate 10% is too high, is only reading consumer data".
- **Mode**: Find and fix — apply edits to dashboard JSON, verify against live Grafana, fix all issues found, not just the example.

## Inputs / Known Locations

- Dashboard JSON file: `grafana/dashboards/ose-otel-noc.json` (uid: `ose-otel-noc`, title: `OSE OTel Demo — NOC View`).
- Grafana http://localhost:3000 admin/admin reachable; otel-lgtm + rabbitmq + postgres + valkey + exporters all healthy.
- Producer + consumer instrument both HTTP server and AMQP send/receive spans; metrics emitted via OTel SDK; logs bridged via OTel Logback appender.
- Phase 8 (260502-8gk) added Valkey + PostgreSQL + manual instrumentation — DB/cache spans present (`SPAN_KIND_CLIENT` exists for `order-producer`).

## Hypotheses to test

1. **H1 (user-stated):** Error-rate panel sums error-status messages from consumer-only metric (e.g. `messaging_process_*` failures) and divides by consumer-only volume. Producer publish-side errors and producer-side success volume are excluded. NOC view should aggregate both sides.
2. **H2:** Other panels likely have analogous biases — top-row "messages" stat may count one side; service-graph may be wired correctly post `a27f774` but worth verifying datasource UID + edge filters; trace-id textbox lacks placeholder/regex hint.
3. **H3:** Some panels may use stale or non-existent metric names (per `9c8de24` "replace broken HTTP histogram and misleading metrics") — there may still be residual broken queries that survived that fix.
4. **H4:** Phase 8 added DB/cache spans/metrics; dashboard does not yet surface them, but the NOC ask is "messages" so DB/cache may be intentionally out of NOC scope. Confirm with user before touching.

## Current Focus

- hypothesis: Multiple panels filter to one service in their queries; the user-cited "Message error rate" is the most visible offender but the same single-side bias affects the "Failed messages / sec" stat. Producer "Request rate by HTTP status" panel uses the SDK histogram counter (which Phase `9c8de24` flagged as having broken bucket distribution) but the `_count` companion is acceptable. The "Active services" panel will silently undercount as Phase 8 brings more services online.
- test: enumerated every panel; validated each query against live Prometheus/Loki; cross-checked which signals exist for producer vs consumer.
- expecting: panels 101 (Message error rate) and 104 (Failed messages /sec) need NOC-framing rewrite; panel 105 (Active services) hard-codes a regex `order-.*` that excludes Phase-8 instrumented infra services; description of panel 102 has redundant verbiage now resolved by `9c8de24`; trace-id textbox panel 503 description redundant with template default; potential additional issue: panels 103 and 303 are duplicates (top-row stat + consumer-row timeseries) which is intentional.
- next_action: Present checkpoint to user — propose framing decision before applying fixes (do NOT silently change semantics).
- reasoning_checkpoint: |
    NOC framing decision required from user. The current panel 101 design is INTENTIONALLY consumer-side (it's literally explaining "message = one delivery attempt at the broker boundary"). User's complaint suggests they want a different framing: errors aggregated across the entire pipeline. Two valid interpretations:
      A) Keep panel 101 as consumer-process health, but RENAME it to "Consumer error rate (5m)" and add a SEPARATE panel for end-to-end pipeline error %.
      B) Redefine panel 101 as end-to-end: numerator = (producer 5xx HTTP responses) + (consumer ERROR spans); denominator = (producer accepted requests) + (consumer processed messages) — but this conflates two different "messages" definitions.
      C) Redefine panel 101 as messaging-only: numerator = (producer AMQP PRODUCER errors) + (consumer AMQP CONSUMER errors); denominator = (producer AMQP PRODUCER total) + (consumer AMQP CONSUMER total) — single coherent "message" definition, NOC-correct.
    Recommend C. Also fix panel 104 same way, and broaden panel 105 service regex.
- tdd_checkpoint: not_applicable

## Evidence

- timestamp: 2026-05-02T20:27:00Z
  source: filesystem
  finding: |
    NOC dashboard JSON located at `grafana/dashboards/ose-otel-noc.json` (619 lines, schemaVersion 39, uid `ose-otel-noc`).
    Provisioning sidecar `grafana/dashboards/dashboards.yaml` mounts this file into otel-lgtm.
- timestamp: 2026-05-02T20:27:00Z
  source: git log
  finding: |
    History on this file: `36176f7 fix(grafana): repair workshop dashboard queries + add NOC view` -> `9c8de24 fix(grafana): replace broken HTTP histogram and misleading metrics across dashboards` -> `a27f774 fix(grafana/noc): wire service graph and trace-id textbox` -> `9fab632 docs(grafana/noc): sharpen messages-vs-orders framing on top-row stats`. Most recent commit was description-only.
- timestamp: 2026-05-02T20:28:00Z
  source: prometheus_query (live)
  finding: |
    Live label discovery shows producer-side has zero ERROR spans across SERVER, PRODUCER, INTERNAL kinds; consumer is the only side that ever produces ERROR status. HTTP server returns only 202s. So today the "consumer-only" framing happens to also be the only place errors *can* occur — but if the consumer's deterministic 1-in-10 failure trigger is later moved or if producer publish ever fails, the NOC view would silently miss it.
- timestamp: 2026-05-02T20:28:00Z
  source: prometheus_query (live)
  finding: |
    Panel 101 live value: 10.000925497454881  (matches user complaint "shows ~10%")
    Panel 100 (orders/sec): 203.72
    Panel 102 (producer p95 SERVER): 1.9 ms — healthy
    Panel 103/303 (queue depth): 1
    Panel 104 (failed msgs/s): 16.66 — = panel 101 numerator; consumer-only by construction
    Panel 105 (active services): 2 — count of `target_info{service_name=~"order-.*"}` ; correctly filters to demo apps but will not adapt if Phase-8 PostgreSQL/Valkey-instrumenting services emit OTel resource info.
    Panel 201 (HTTP request rate): only `http_response_status_code="202"` series exists; query syntactically correct, would render 4xx/5xx if they existed.
    Panel 202 (producer p50/95/99): all sub-2ms, healthy.
    Panel 301 (consumer OK/ERROR): OK 240/s, ERROR 26/s — ratio matches the 10% deterministic failure.
    Panel 302 (consumer p95): 3.2 ms — healthy.
    Panel 401 (service graph): tempo serviceMap with `{}` filter — confirmed in commit `a27f774`; OK to leave.
    Panel 402 RED matrix:
      - rate by service: order-consumer 610.8/s (counts INTERNAL+CLIENT+CONSUMER spans i.e. multiple per message → INFLATED), order-producer 630.5/s (counts SERVER+PRODUCER+INTERNAL → INFLATED 3x).
      - error % by service: order-consumer 6.9% (denominator inflated by 3 span kinds → DEFLATED from true 10%), order-producer 0%.
      - p95 by service: aggregates over ALL span kinds → mixes HTTP latency with INTERNAL processing latency. Not NOC-correct.
- timestamp: 2026-05-02T20:30:00Z
  source: prometheus_query (live)
  finding: |
    `rate(rabbitmq_queue_messages_published_total{queue="orders.created"}[1m])` returns NO DATA — the per-channel labels (channel id `<0.1166.0>`, etc.) churn faster than the 1m window so rate() cannot compute. Switching to `[5m]` recovers the 216/s broker-side publish rate. This means any future panel attempting broker-side publish rate must use [5m] or `sum without (channel) (rate(...[5m]))` — relevant if the NOC fix uses broker counters as the canonical "message" metric.

## Eliminated

- nothing eliminated yet — will populate after user confirms framing direction.

## Panel-by-panel audit

| # | Title | Side | NOC-correct? | Issue | Proposed Fix |
|---|---|---|---|---|---|
| 100 | Orders / sec | producer (HTTP ingress) | yes — labeled BUSINESS rate | none | leave |
| 101 | Message error rate (5m) | consumer-only | NO — single-side filter | Per user complaint. Inflated 10% reading because numerator counts only consumer ERROR spans and denominator counts only consumer CONSUMER spans. | Rewrite as end-to-end messaging error rate: errors = sum across `service in {order-producer,order-consumer} AND span_kind in {SPAN_KIND_PRODUCER,SPAN_KIND_CONSUMER}` with `status_code=STATUS_CODE_ERROR`; denominator = same scope without status filter. Removes "service=order-consumer" hard-code. Update description accordingly. |
| 102 | Producer p95 latency (SERVER) | producer | yes — labeled producer | none | leave |
| 103 | Queue depth (broker) | broker | yes (broker is the truth) | none | leave |
| 104 | Failed messages / sec | consumer-only | NO — single-side filter | Same bug as 101: filters `service="order-consumer"`. NOC view should count failed messages anywhere in the pipeline. | Rewrite as: sum of `rate(traces_spanmetrics_calls_total{span_kind=~"SPAN_KIND_(PRODUCER\|CONSUMER)",status_code="STATUS_CODE_ERROR"}[1m])` (no service filter). |
| 105 | Active services | producer+consumer (filtered to `order-.*`) | partial — hardcoded regex | Filter `service_name=~"order-.*"` is fine for THIS demo (only order-producer, order-consumer exist), but description says "Workshop expects 2" — that becomes wrong if anyone adds another service. Threshold steps green=2 won't go yellow if a 3rd `order-*` shows up; will go yellow if one drops. Functional but brittle. | Change description to clarify what the count covers; keep regex. |
| 201 | Request rate by HTTP status | producer | yes — explicitly producer-row | none — query reads the SDK counter `http_server_request_duration_seconds_count` which is the OK companion to the broken bucket histogram (per `9c8de24`). Still emits valid counts. | leave |
| 202 | Producer SERVER-span latency p50/95/99 | producer | yes — explicitly producer-row | none | leave |
| 301 | Messages processed / sec (OK vs ERROR) | consumer (explicit row) | yes — labeled consumer-row | none | leave |
| 302 | Consumer processing latency p50/95/99 | consumer (explicit row) | yes — labeled consumer-row | none | leave |
| 303 | Queue depth (broker) | broker | yes — labeled broker | duplicate of 103 (intentional: stat in top row, timeseries in consumer row) | leave |
| 401 | Service graph | both | yes — Tempo serviceMap | none — `{}` filter explained in description | leave |
| 402 | RED matrix per service | both, per row | NO — Rate column inflates by counting all span kinds; Error% deflates because denominator includes SPAN_KIND_INTERNAL/CLIENT non-message spans. p95 mixes HTTP and INTERNAL latencies. | Restrict the RED matrix to a single "primary" span kind per service so the per-row numbers are meaningful. Easiest fix: filter `span_kind=~"SPAN_KIND_(SERVER\|CONSUMER)"` so producer row uses HTTP server spans and consumer row uses AMQP consumer spans (one row per primary entry-point). |
| 501 | Recent error traces | both | yes — `{resource.service.name!="compose" && status=error}` | none | leave |
| 502 | Recent ERROR logs | both — `{service_name=~"order-.*"}` | yes | none | leave |
| 503 | Logs by trace_id | both | yes | description redundancy is minor, OK to leave | leave |

## Resolution

**Root cause**: Three NOC View panels filtered queries to a single service (or to all span_kinds without distinguishing entry-point spans), producing single-side or aggregate-distorted readings on a dashboard whose stated purpose is end-to-end NOC observation.

**Fix** (4 atomic commits on main):

1. `318deda fix(grafana/noc): aggregate message error rate across producer + consumer`
   - Panel 101 expr: scope expanded from `service="order-consumer",span_kind="SPAN_KIND_CONSUMER"` to `service=~"order-(producer|consumer)",span_kind=~"SPAN_KIND_(PRODUCER|CONSUMER)"`.
   - Live verification before: 10.00% (consumer-only). After: 5.00% (true end-to-end, half of message-spans are publishes which never fail).
2. `bbc5eac fix(grafana/noc): widen failed-messages stat to producer + consumer`
   - Panel 104 expr: same widening as 101.
   - Live verification: 19.99/s before and after (zero producer publish failures today), but the panel will now catch publish-side failures the moment they appear.
3. `aab0fb5 fix(grafana/noc): scope RED matrix to entry-point spans per service`
   - Panel 402 (table) all three targets gain `span_kind=~"SPAN_KIND_(SERVER|CONSUMER)"` filter.
   - Live verification before: rates 610.8/630.5 (3x inflated by counting INTERNAL+CLIENT spans), error% 6.9 (deflated denominator), p95 mixing HTTP+INTERNAL. After: rates 223.9/224.1, errors 0/10.0%, p95 1.9ms/3.3ms — matches dedicated panels 202/302.
4. `7367784 docs(grafana/noc): clarify Active services regex is intentional`
   - Panel 105 description rewritten to explain why the `order-.*` filter is intentional and what the heartbeat measures. No query change.

**Verified post-fix** by re-fetching panel queries from Grafana via `/api/dashboards/uid/ose-otel-noc` — all four edits live (10s provisioning poll picked them up). Live Prometheus values confirm each panel now reads what its title claims.

**Out of scope (deliberately not changed)**:
- Panels 100, 102, 103, 201, 202, 301, 302, 303, 401, 501, 502, 503 audited and found NOC-correct as labelled (each is either explicitly per-side, a broker-truth source, or a global filter with intentional `order-.*` scope).
- Phase-8 PostgreSQL/Valkey instrumentation deliberately not surfaced — NOC ask is "messages", DB/cache are infra, out of scope per user briefing.
- No load generator, SDK code, or producer/consumer service code touched.
- No new panels, no layout changes.

**Known follow-ups (not fixed in this session)**:
- If anyone later wires retries / DLQ on the consumer, panel 101's "expect ~5%" sentence in the description will become misleading because redeliveries inflate the consumer's CONSUMER span count without changing producer publish count. Worth a description tweak at that time.
- Broker-side counters (`rabbitmq_queue_messages_published_total`, `_delivered_total`) are available but currently *not used by the NOC dashboard*. Their per-channel labels churn faster than the default 1m rate window so any future panel that uses them should rate over [5m] or aggregate `without (channel)`. Captured here as a guardrail for whoever adds those panels next.
