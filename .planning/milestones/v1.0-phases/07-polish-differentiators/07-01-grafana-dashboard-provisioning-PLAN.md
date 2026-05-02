---
phase: 07-polish-differentiators
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - grafana/dashboards/ose-otel-demo.json
  - grafana/dashboards/dashboards.yaml
  - docker-compose.yml
autonomous: false
requirements: [WORK-02]
risk: medium
tags: [grafana, dashboard, provisioning, otel-lgtm]

must_haves:
  truths:
    - "After mise run infra:up, Grafana auto-loads ose-otel-demo dashboard with zero clicks"
    - "Dashboard top row contains 4 panels (Tempo trace search, service graph, RED metrics, Loki logs filtered by trace_id)"
    - "Dashboard second row exists and is collapsed by default"
    - "Dashboard JSON is portable across team members (no environment-specific UIDs)"
  artifacts:
    - path: "grafana/dashboards/ose-otel-demo.json"
      provides: "Two-row dashboard definition (D-02)"
      contains: "ose-otel-demo"
    - path: "grafana/dashboards/dashboards.yaml"
      provides: "Grafana dashboard provisioning manifest"
      contains: "apiVersion: 1"
    - path: "docker-compose.yml"
      provides: "Volume mount binding ./grafana/dashboards into otel-lgtm provisioning path"
      contains: "./grafana/dashboards:"
  key_links:
    - from: "docker-compose.yml lgtm service volumes block"
      to: "otel-lgtm provisioning path inside container"
      via: ":ro bind mount"
      pattern: "./grafana/dashboards:.*:ro"
    - from: "grafana/dashboards/dashboards.yaml"
      to: "grafana/dashboards/ose-otel-demo.json"
      via: "providers[].options.path field"
      pattern: "path: /otel-lgtm"
---

<objective>
Auto-provision a two-row Grafana dashboard into the otel-lgtm container so workshop attendees see live three-signal telemetry the moment infra is healthy — zero clicks, zero Grafana navigation. Implements WORK-02 per CONTEXT.md D-01 + D-02.

Purpose: Workshop attendees clone the repo, run `mise run infra:up`, open `http://localhost:3000`, and immediately see all three signals on one panel. The dashboard's two-row layout (top = projector-friendly demo strip; bottom = collapsed deeper-dive) IS the pedagogical message — the demo is small, production is bigger, here is a glimpse of bigger.

Output: `grafana/dashboards/ose-otel-demo.json` (the dashboard definition), `grafana/dashboards/dashboards.yaml` (Grafana provisioning manifest), and a single-line docker-compose volume mount for the lgtm service.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/07-polish-differentiators/07-CONTEXT.md
@docker-compose.yml
@.planning/phases/04-metrics/04-CONTEXT.md
@.planning/phases/05-logs-correlation/05-CONTEXT.md
@CLAUDE.md

<interfaces>
<!-- Phase 4 metrics surface the dashboard queries -->

OTel-side metric names (mangled to Prometheus form by otel-lgtm collector):
- `orders.created` -> `orders_created_total` (Counter, with attribute `order_priority`)
- `http.server.request.duration` -> `http_server_request_duration_seconds` (Histogram, attributes `http_request_method`, `http_response_status_code`)
- `orders.queue.depth.estimate` -> `orders_queue_depth_estimate` (ObservableGauge)

Phase 5 logs surface the Loki query:
- Loki labels include `service_name` (from Resource attribute `service.name`)
- Logfmt body contains `trace_id=<32 hex>` from MDC injector wrapper appender

Tempo service names emitted (Phase 2):
- `order-producer` (HTTP entry)
- `order-consumer` (AMQP listener)

otel-lgtm v0.26.0 datasources (auto-wired, default UIDs):
- Tempo (UID: `tempo`)
- Prometheus / Mimir (UID: `prometheus`)
- Loki (UID: `loki`)

Tempo service-graph metrics (metrics-generator on by default per CONTEXT.md tooling-references):
- `tempo_spanmetrics_calls_total{status_code="STATUS_CODE_ERROR"}`
- `traces_service_graph_request_total`
</interfaces>
</context>

<tasks>

<task type="checkpoint:decision" gate="blocking">
  <name>Task 1: Dashboard authoring strategy decision</name>
  <decision>How to author grafana/dashboards/ose-otel-demo.json</decision>
  <context>
    CONTEXT.md D-02 recommends building interactively in Grafana UI then exporting + hand-tweaking
    portability fields (uid, title, datasource UID references). Alternative is hand-authoring the
    JSON directly using documented panel schemas. The interactive route requires a running demo
    with traffic; the hand-authoring route is fully autonomous but riskier (panel-schema drift).
  </context>
  <options>
    <option id="option-a">
      <name>Interactive author + export (CONTEXT.md D-02 recommendation)</name>
      <pros>Visually validated layout; query syntax verified by Grafana itself; faster iteration on panel sizing</pros>
      <cons>Requires user to run mise run infra:up + mise run dev + mise run load and click through Grafana; this plan becomes a checkpoint:human-action</cons>
    </option>
    <option id="option-b">
      <name>Hand-author JSON using documented schemas (autonomous)</name>
      <pros>Fully autonomous; zero human-in-the-loop; deterministic diff</pros>
      <cons>Higher risk of panel-render breakage; harder to verify queries without round-tripping through Grafana</cons>
    </option>
    <option id="option-c">
      <name>Hybrid: hand-author the JSON skeleton (datasource refs, panel grid layout, query strings transcribed from CONTEXT.md D-02), then human verifies panels render against running infra</name>
      <pros>Autonomous skeleton; checkpoint validates real rendering; balances speed and safety</pros>
      <cons>Two-phase work split across one plan</cons>
    </option>
  </options>
  <resume-signal>Select: option-a, option-b, or option-c</resume-signal>
</task>

<task type="auto">
  <name>Task 2: Resolve otel-lgtm dashboard provisioning path + author dashboards.yaml</name>
  <read_first>
    - docker-compose.yml (existing lgtm service block, volumes structure, port mappings)
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-01 verbatim — locked in-container path resolution requirement)
    - https://github.com/grafana/docker-otel-lgtm v0.26.0 README (or `docker exec` introspection)
  </read_first>
  <action>
    Step 1 — Resolve in-container provisioning path. Run:
    ```bash
    docker exec ose-otel-lgtm ls /otel-lgtm/grafana/conf/provisioning/dashboards/ 2>/dev/null \
      || docker exec ose-otel-lgtm find /otel-lgtm -path '*provisioning/dashboards*' -type d 2>/dev/null \
      || docker exec ose-otel-lgtm find / -path '*provisioning/dashboards*' -type d 2>/dev/null
    ```
    Record the canonical path (likely `/otel-lgtm/grafana/conf/provisioning/dashboards`). If infra is
    not running, run `mise run infra:up` first then re-run the lookup. Note the resolved path in the
    SUMMARY.

    Step 2 — Create directory `grafana/dashboards/` at repo root.

    Step 3 — Author `grafana/dashboards/dashboards.yaml` (Grafana provisioning manifest, schema per
    https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards). Exact content
    (substitute `<RESOLVED_PROVISIONING_PATH>` with the path from Step 1; the inner `path:` field
    points at the in-container directory the dashboards/ folder is mounted to):

    ```yaml
    apiVersion: 1
    providers:
      - name: 'ose-otel-demo'
        orgId: 1
        folder: ''
        folderUid: ''
        type: file
        disableDeletion: false
        updateIntervalSeconds: 10
        allowUiUpdates: false
        options:
          path: <RESOLVED_PROVISIONING_PATH>
          foldersFromFilesStructure: false
    ```

    The `options.path` field MUST equal the directory the docker-compose volume mount writes to
    inside the container (Step 4 below). Both the YAML manifest AND the JSON dashboards live in the
    same directory in this project's layout, so the resolved path is the directory itself.

    Step 4 — Modify `docker-compose.yml`. Inside the existing `lgtm:` service `volumes:` list (currently
    only `- lgtm-data:/data`), append exactly one bind-mount line preserving the existing comment style:
    ```yaml
        volumes:
          - lgtm-data:/data  # persists Grafana state across infra:down/up cycles (Pitfall D)
          - ./grafana/dashboards:<RESOLVED_PROVISIONING_PATH>:ro  # WORK-02 / Phase 7 D-01 — auto-provision dashboards
    ```
    The `:ro` flag is mandatory (per CONTEXT.md D-01 — verify it is honored by otel-lgtm's bundled
    Grafana). NO change to the existing `lgtm-data` mount, port mappings, environment block, or
    healthcheck comment.

    Do NOT modify the rabbitmq service or the volumes block at the bottom.
  </action>
  <verify>
    <automated>
      test -d grafana/dashboards \
      && grep -q 'apiVersion: 1' grafana/dashboards/dashboards.yaml \
      && grep -q 'name:.*ose-otel-demo' grafana/dashboards/dashboards.yaml \
      && grep -q '\./grafana/dashboards:' docker-compose.yml \
      && grep -q ':ro' docker-compose.yml \
      && docker compose config 2>&1 | grep -q 'grafana/dashboards' \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `grafana/dashboards/` directory exists
    - `grafana/dashboards/dashboards.yaml` exists and contains `apiVersion: 1` and `name: ose-otel-demo` (or quoted equivalent)
    - `docker-compose.yml` contains the substring `./grafana/dashboards:`
    - `docker-compose.yml` contains the substring `:ro` on the new line
    - `docker compose config` (without `-q`) parses cleanly and shows the new mount in its rendered output
    - Existing `lgtm-data:/data` mount UNCHANGED
    - Existing `volumes:` top-level block UNCHANGED (still declares only `lgtm-data:`)
    - The resolved in-container provisioning path is recorded in the plan SUMMARY
  </acceptance_criteria>
  <done>
    Dashboard provisioning manifest + docker-compose volume mount are in place. Dashboard JSON
    not yet authored — Task 3 lands it.
  </done>
</task>

<task type="auto">
  <name>Task 3: Author grafana/dashboards/ose-otel-demo.json (two-row layout per D-02)</name>
  <read_first>
    - .planning/phases/07-polish-differentiators/07-CONTEXT.md (D-02 — exact two-row layout, panel queries, time range defaults)
    - .planning/phases/04-metrics/04-CONTEXT.md (METRIC-01..04 — exact instrument names + attributes the panels query)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (LOG-04, LOG-05 — Loki query shape `{service_name=~"order-.*"} |~ "trace_id=<...>"`)
    - Existing dashboard from Task 2's resolved provisioning path (if option-a chosen — export from running Grafana)
  </read_first>
  <action>
    Author `grafana/dashboards/ose-otel-demo.json` per CONTEXT.md D-02. Exact structure:

    Top-level fields:
    - `uid`: `"ose-otel-demo"` (stable, portable across team members)
    - `title`: `"OSE OTel Demo — Three Signals"`
    - `tags`: `["ose-otel-demo", "workshop", "otel"]`
    - `schemaVersion`: a value compatible with otel-lgtm v0.26.0's bundled Grafana (typically 39+)
    - `time`: `{ "from": "now-15m", "to": "now" }` (last 15 minutes, per D-02)
    - `refresh`: `"10s"` (matches Phase 4 PeriodicMetricReader interval)

    Top row (always visible) — 4 panels in a single row, each ~6 grid units wide:

    1. **Tempo trace search** (datasource `tempo`):
       - type: `traces` panel showing the last 15m of `service.name=order-producer` traces
       - query: TraceQL `{ resource.service.name = "order-producer" }` (or equivalent search-by-service)
       - title: `"Recent Traces (order-producer)"`

    2. **Tempo service graph** (datasource `tempo`, panel type `nodeGraph`):
       - title: `"Service Graph"`
       - query: service-graph (auto-derived; otel-lgtm metrics-generator emits `traces_service_graph_request_total`)
       - sources: `service-graph` view of Tempo

    3. **Mimir RED metrics combo** (datasource `prometheus`):
       - title: `"RED Metrics"`
       - panel type: `timeseries` with three series via `expr`:
         - `sum(rate(orders_created_total[1m]))` (Rate)
         - `histogram_quantile(0.50, sum by (le) (rate(http_server_request_duration_seconds_bucket[1m])))` p50
         - `histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket[1m])))` p95
         - `histogram_quantile(0.99, sum by (le) (rate(http_server_request_duration_seconds_bucket[1m])))` p99
         - `orders_queue_depth_estimate` gauge
       - legend on each series

    4. **Loki log panel filtered by trace_id** (datasource `loki`):
       - title: `"Logs (filtered by latest trace_id)"`
       - query: `{service_name=~"order-.*"} |~ "trace_id=$traceid"` using a Grafana variable `$traceid`
       - dashboard variable `traceid` of type `query` derived from panel #1's most recent trace
         (acceptable fallback: a `textbox` variable defaulting to `.+` so the panel works without
         live data — D-02 directs the planner to pick a workable shape)

    Second row (collapsed by default — `collapsed: true` on the row panel) — title `"Deeper-dive (post-workshop)"`:

    5. **Per-priority `orders.created` breakdown** (`prometheus`):
       - title: `"Orders Created by Priority"`
       - expr: `sum by (order_priority) (rate(orders_created_total[1m]))`

    6. **Error-status trace count** (`prometheus`):
       - title: `"Error Spans (Tempo metrics-generator)"`
       - expr: `sum(rate(tempo_spanmetrics_calls_total{status_code="STATUS_CODE_ERROR"}[1m]))`

    7. **Raw Loki log table** (`loki`):
       - title: `"Raw Logs (post-workshop poking)"`
       - query: `{service_name=~"order-.*"}` with table mode

    Datasource references — use `{ "type": "tempo", "uid": "tempo" }`, `{ "type": "prometheus", "uid": "prometheus" }`, `{ "type": "loki", "uid": "loki" }` (otel-lgtm v0.26.0 default UIDs). Do NOT embed environment-specific UIDs.

    Strip any `version`, `id`, or `iteration` field that locks the JSON to a specific Grafana
    instance (D-02 — portability).

    If option-a was selected in Task 1: build the dashboard interactively against `mise run infra:up
    + mise run dev + mise run load`, export via Grafana's "Share -> Export -> Save to file (export
    for sharing externally)", then sanitize per the portability rules above.

    If option-b: hand-author the JSON using the structure above; reference grafana.com panel-schema
    docs for any field details not in CONTEXT.md.

    If option-c: hand-author the skeleton; verify rendering once via the human-verify checkpoint
    in the next plan's wave (or here, optionally).
  </action>
  <verify>
    <automated>
      test -f grafana/dashboards/ose-otel-demo.json \
      && python3 -c "import json,sys; d=json.load(open('grafana/dashboards/ose-otel-demo.json')); assert d.get('uid')=='ose-otel-demo'; assert d.get('title','').startswith('OSE'); panels=d.get('panels',[]); assert len(panels) >= 4, f'panel count {len(panels)}'; print('OK panels=' + str(len(panels)))" \
      && grep -q 'orders_created_total' grafana/dashboards/ose-otel-demo.json \
      && grep -q 'http_server_request_duration_seconds' grafana/dashboards/ose-otel-demo.json \
      && grep -q 'order_priority' grafana/dashboards/ose-otel-demo.json \
      && grep -q 'orders_queue_depth_estimate' grafana/dashboards/ose-otel-demo.json \
      && grep -q 'service_name' grafana/dashboards/ose-otel-demo.json \
      || (echo "VERIFY FAILED" && exit 1)
    </automated>
  </verify>
  <acceptance_criteria>
    - `grafana/dashboards/ose-otel-demo.json` is valid JSON (parses with `python3 -c "import json; json.load(open(...))"`)
    - Top-level `uid` field equals exactly the string `"ose-otel-demo"`
    - Top-level `title` field starts with `"OSE"`
    - File contains the literal Phase 4 metric names: `orders_created_total`, `http_server_request_duration_seconds`, `orders_queue_depth_estimate`
    - File contains the per-priority attribute key: `order_priority`
    - File contains the Phase 5 Loki label: `service_name`
    - File contains the Tempo error-span series: `tempo_spanmetrics_calls_total`
    - File contains at least one `collapsed` field set to `true` (the deeper-dive row)
    - Panel datasource references use UIDs `tempo`, `prometheus`, `loki` (NOT random GUIDs from a specific Grafana instance)
    - No `version` field at top level greater than 0 (portability — no instance-locked version)
  </acceptance_criteria>
  <done>
    Dashboard JSON authored, datasource refs portable, panel queries reference all Phase 2/4/5 instrument
    names. Volume mount + provisioning manifest in place. Workshop attendee starting `mise run infra:up`
    will see the dashboard auto-load in Grafana with no manual import.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 4: Live verification — dashboard auto-provisions and renders</name>
  <what-built>
    Dashboard JSON, dashboards.yaml provisioning manifest, docker-compose volume mount.
  </what-built>
  <how-to-verify>
    1. Stop infra if running: `mise run infra:down`
    2. Start infra fresh: `mise run infra:up`
    3. Wait for healthcheck green (~30 sec).
    4. Open Grafana: `mise run ui:grafana` (or `http://localhost:3000`, login admin/admin if prompted).
    5. Navigate to Dashboards. Confirm `OSE OTel Demo — Three Signals` appears WITHOUT manual import.
    6. Open the dashboard. Confirm:
       - Top row shows 4 panels (Tempo trace search, service graph, RED metrics, Loki logs).
       - Second row exists and is collapsed.
       - Panels show "no data" gracefully (we have no traffic yet — that is expected).
    7. Start producer + consumer + load: `mise run dev` (in one terminal), then `mise run load` (in another) — wait until Wave 1 plan 02 lands the load script. If plan 02 has not yet executed, this verification can pass with "no data" state and re-verify in Wave 2.
    8. Once traffic flows (~30s of `mise run load`), confirm at least one panel populates (RED metrics rate panel will show non-zero).
  </how-to-verify>
  <resume-signal>Type "approved" if dashboard auto-provisions and renders with traffic, or describe issues (panel doesn't render / wrong queries / missing in dashboard list)</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Workshop laptop -> Grafana UI (localhost:3000) | Default `admin/admin` creds; intentional for offline workshop demo |
| Grafana UI -> dashboard JSON (file-system read) | Trusted authored content; no user input flows in |
| Volume mount (host -> container) | `:ro` flag prevents container from mutating host files |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-01-01 | Tampering | docker-compose.yml volume mount | mitigate | `:ro` flag enforced on the bind mount; verified by `docker compose config` parsing |
| T-07-01-02 | Information Disclosure | grafana/dashboards/ose-otel-demo.json panel titles | accept | Dashboard JSON is trusted authored content; panel titles + queries do not contain secrets, env paths, or untrusted strings; XSS surface in panel titles applies only to attacker-authored dashboards (low for trusted-content workshop repo) |
| T-07-01-03 | Spoofing | Default Grafana admin/admin creds in docker-compose env | accept | Workshop default for offline laptop usage; CONTEXT.md flags this is a workshop-only convention; production-Grafana variants would use env-var creds (called out in README Concepts & FAQ during plan 06) |
| T-07-01-04 | Denial of Service | Dashboard refresh interval `10s` | accept | Low rate; otel-lgtm bundled Grafana absorbs without issue; matches Phase 4 PeriodicMetricReader cadence |
</threat_model>

<verification>
- `docker compose config` parses cleanly with the new mount visible.
- `grafana/dashboards/ose-otel-demo.json` is valid JSON.
- `dashboards.yaml` apiVersion: 1 with provider `ose-otel-demo`.
- Live verification (Task 4): dashboard appears in Grafana automatically after `mise run infra:up`.
- WORK-02 success criterion 1: workshop attendee runs `mise run infra:up`, opens Grafana, sees dashboard at known path showing live traces/metrics/logs without manual datasource configuration.
</verification>

<success_criteria>
- `grafana/dashboards/ose-otel-demo.json` exists, valid JSON, contains all Phase 2/4/5 instrument names + attributes per D-02.
- `grafana/dashboards/dashboards.yaml` exists with apiVersion 1.
- `docker-compose.yml` lgtm service has `:ro` bind mount to `./grafana/dashboards`.
- Resolved in-container provisioning path documented in SUMMARY.md.
- Live verification (human-verify) confirms zero-click dashboard appearance.
</success_criteria>

<output>
After completion, create `.planning/phases/07-polish-differentiators/07-01-SUMMARY.md` recording:
- Resolved otel-lgtm in-container provisioning path
- Dashboard authoring strategy chosen (option-a/b/c)
- Live-verification result (approved | issues described)
- Any deviations from D-02 (e.g., variable shape fallback if `traceid` query variable proved fragile)
</output>
