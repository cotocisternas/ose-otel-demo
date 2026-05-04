# Phase 13: Log-Based Metrics (Loki Recording Rules) - Research

**Researched:** 2026-05-03
**Domain:** Grafana Loki 3.7.1 ruler / recording rules / remote-write to Mimir / Grafana dashboard
**Confidence:** HIGH

## Summary

Phase 13 is a pure infrastructure phase -- zero Java code changes. It delivers a single Loki recording rule YAML file that derives an error-rate metric from application log patterns, remote-writes it to Mimir via the already-configured ruler pipeline (Phase 10 D-07), and visualizes the log-derived metric alongside the SDK-emitted `orders_created_total` counter in a new Grafana dashboard panel.

The critical discovery from this research is the **`fake/` tenant subdirectory requirement**: Loki's local ruler storage expects rule files at `ruler_storage.local.directory/<tenant_id>/<rule_file>.yaml`. With `auth_enabled: false`, the implicit tenant ID is `"fake"`. This means the file must live at `infra/observability/loki-rules/fake/order-errors.yaml` on the host (which maps to `/loki/rules/fake/order-errors.yaml` inside the container). The ARCHITECTURE.md example showed the file at the root of `loki-rules/` -- that would silently fail. The CONTEXT.md reference to `infra/observability/loki-rules/order-errors.yaml` must be corrected to include the `fake/` subdirectory.

**Primary recommendation:** Create `infra/observability/loki-rules/fake/order-errors.yaml` (not at the root of `loki-rules/`), use a `[2m]` rate window to satisfy F4-2 (2x the 1m evaluation interval), and query the metric in Grafana as `log:order_errors:rate2m` from the Mimir datasource.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-L1: Single timeseries panel overlaying `rate(orders_created_total[2m])` (SDK-emitted) alongside `log:order_errors:rate2m` (Loki-derived). Both events/sec, same Y-axis.
- D-L2: New open row "Log-Based Metrics (Phase 13)" placed AFTER Exemplars row (Phase 12), BEFORE collapsed diagnostic rows. Open by default.
- D-L3: Teaching callout in panel description field.
- D-L4: `mise run verify:log-metrics` -- two-tier check (Tier 1: Loki /loki/api/v1/rules, Tier 2: Mimir /api/v1/query).
- D-L5: Task name `verify:log-metrics`.
- D-L6: README section13 ~100-150 lines depth.
- D-L7: Lead with zero-code angle narrative hook.
- D-L8: Short "when to use which" comparison table (SDK vs log-derived).
- D-L9: Placeholder screenshot deferred to Phase 18 Playwright.

### Claude's Discretion
- Exact LogQL expression syntax (verified below)
- Exact Loki ruler YAML structure (verified below)
- PromQL expressions for dashboard panel
- Panel gridPos, ID numbering, JSON structure
- verify:log-metrics bash implementation details
- README phrasing
- Whether recording rule needs a `labels:` block
- Rule group name
- Color assignment for panel series

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LMET-01 | Loki runs with ruler component enabled, configured to remote-write recording rule outputs to Mimir on same `auth_enabled: false` tenant | Already in place (Phase 10 D-07). loki-config.yaml ruler block pre-enabled. No changes needed. |
| LMET-02 | Recording rule at `infra/observability/loki-rules/order-errors.yaml` defining `log:order_errors:rate2m` as `sum by (service_name) (rate({service_name=~"order-.+"} |= "ERROR" [2m]))` | CRITICAL CORRECTION: file must be at `fake/order-errors.yaml` subdirectory. LogQL syntax verified valid. Rate window [2m] = 2x eval interval. |
| LMET-03 | Grafana panel plotting log-derived `log:order_errors:rate2m` alongside SDK-emitted `orders.created` counter; both share `service_name` axis | SDK counter is `orders_created_total` in Mimir (OTel dot-to-underscore + _total suffix). Both queryable from Mimir datasource. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Recording rule evaluation | Loki ruler (container) | -- | Loki's ruler evaluates LogQL on schedule; this is a Loki-native capability |
| Metric storage | Mimir (container) | -- | Ruler remote-writes the derived metric to Mimir's /api/v1/push |
| Visualization | Grafana (container) | -- | Dashboard panel queries Mimir's Prometheus-compatible API |
| Rule file storage | Host filesystem (bind-mount) | -- | YAML file lives in git, bind-mounted read-only into container |
| Verification | Host CLI (mise task) | -- | Bash script curls Loki + Mimir APIs from host |

## Standard Stack

This phase uses NO new libraries or packages. It is purely YAML configuration and Grafana dashboard JSON.

### Components Used (existing infrastructure)

| Component | Version | Purpose | Already In Place |
|-----------|---------|---------|-----------------|
| Grafana Loki | 3.7.1 | Ruler evaluates LogQL recording rules | Yes (Phase 10) |
| Grafana Mimir | 3.0.6 | Receives and stores derived metrics via remote-write | Yes (Phase 10) |
| Grafana | 13.0.1 | Visualizes both SDK and log-derived metrics | Yes (Phase 10) |
| Docker Compose | v2 | Infra orchestration | Yes (Phase 10) |

**No installation commands required.** All infrastructure is pre-wired by Phase 10.

## Architecture Patterns

### System Architecture Diagram

```
Host filesystem (git-tracked)
  infra/observability/loki-rules/fake/order-errors.yaml
       |
       | (docker-compose bind-mount: ./infra/observability/loki-rules:/loki/rules:ro)
       v
Loki container (/loki/rules/fake/order-errors.yaml)
       |
       | ruler evaluates LogQL every 1m (evaluation_interval)
       | expr: sum by (service_name)(rate({service_name=~"order-.+"} |= "ERROR" [2m]))
       v
Loki ruler ---> remote_write ---> Mimir :9009/api/v1/push
                                        |
                                        | stores as time series: log:order_errors:rate2m{service_name="..."}
                                        v
                                  Grafana queries Mimir (datasource uid="prometheus")
                                        |
                                        | PromQL: log:order_errors:rate2m
                                        | PromQL: rate(orders_created_total[2m])
                                        v
                                  Dashboard panel: both series overlaid
```

### CRITICAL: Tenant Subdirectory Requirement

**[VERIFIED: Grafana Loki docs + GitHub issues #5459, #7589 + multiple community sources]**

When `auth_enabled: false`, Loki's ruler uses the implicit tenant ID `"fake"`. The local ruler storage scans `ruler_storage.local.directory/<tenant_id>/<rule_file>.yaml`. This means:

- `ruler_storage.local.directory` = `/loki/rules` (from loki-config.yaml)
- Tenant ID = `fake` (implicit when `auth_enabled: false`)
- Required file path inside container: `/loki/rules/fake/order-errors.yaml`
- Required file path on host: `infra/observability/loki-rules/fake/order-errors.yaml`

**The ARCHITECTURE.md example that showed the file at the root of `loki-rules/` is INCORRECT for runtime behavior.** The file would be ignored by the ruler without the `fake/` subdirectory.

### Recording Rule YAML Format

**[VERIFIED: Grafana Loki recording rules documentation]**

```yaml
# infra/observability/loki-rules/fake/order-errors.yaml
#
# WHY this file: Loki recording rules derive metrics FROM LOGS using LogQL.
# The attendee lesson: "you can compute an error rate from existing logs
# without touching a single line of Java code."
#
# WHY the `fake/` directory: Loki's ruler scans ruler_storage.local.directory
# for <tenant_id>/<rule_file>.yaml. With auth_enabled: false, the implicit
# tenant ID is "fake". Without this subdirectory, the ruler silently ignores
# the rule file. (See F4-3 mitigation in PITFALLS.md.)
#
# WHY [2m] window: ruler evaluation_interval is 1m (loki-config.yaml D-07).
# F4-2 mitigation: rate() window MUST be >= 2x evaluation_interval to avoid
# aliasing (rate returning 0 even when logs exist).
#
# Result metric: log:order_errors:rate2m{service_name="order-consumer"}
# Lands in Mimir via ruler.remote_write.clients.mimir.url

groups:
  - name: order-error-rules
    interval: 1m
    rules:
      - record: log:order_errors:rate2m
        expr: |
          sum by (service_name) (
            rate({service_name=~"order-.+"} |= "ERROR" [2m])
          )
```

Key format details:
- Top-level key is `groups:` (array of rule groups) [VERIFIED: Grafana Loki docs]
- Each group has `name`, `interval` (optional, overrides global), `rules` array
- Each recording rule has `record:` (metric name) and `expr:` (LogQL expression)
- Optional `labels:` block for extra labels -- NOT needed here because `sum by (service_name)` already carries the grouping label through

### LogQL Expression Verification

**[VERIFIED: Grafana Loki LogQL documentation]**

The expression `sum by (service_name) (rate({service_name=~"order-.+"} |= "ERROR" [2m]))` is valid LogQL:

1. `{service_name=~"order-.+"}` -- label matcher with regex (matches `order-producer`, `order-consumer`)
2. `|= "ERROR"` -- line filter: substring match (keep lines containing "ERROR") [VERIFIED: `|=` is the correct substring match operator]
3. `[2m]` -- range selector for `rate()` function
4. `rate(...)` -- computes per-second rate of matched log lines over the window [VERIFIED: works on filtered log streams, not just metric queries]
5. `sum by (service_name)(...)` -- aggregates and groups by service_name label

The `service_name` label is **automatically present** on all log streams ingested via OTLP because Loki maps the OTel resource attribute `service.name` to the Loki label `service_name` (dots converted to underscores). [VERIFIED: Grafana Loki OTLP ingestion docs]

### OTel Metric Name Translation

**[VERIFIED: OTel Prometheus exporter specification + existing codebase grep]**

The SDK counter `orders.created` (defined in `OrderService.java` via `meter.counterBuilder("orders.created")`) is translated to `orders_created_total` in Mimir:
- Dots (`.`) are converted to underscores (`_`) -- OTel-to-Prometheus name translation
- `_total` suffix is appended -- Prometheus convention for Counter type metrics

This is confirmed by existing dashboard panels that already query `orders_created_total` (see `ose-otel-demo.json` and `ose-otel-noc.json`).

### Dashboard Panel PromQL

For the D-L1 panel, the two queries are:

| Series | PromQL | Legend | Purpose |
|--------|--------|--------|---------|
| SDK-emitted creation rate | `sum by (service_name) (rate(orders_created_total[2m]))` | `SDK: orders created ({{service_name}})` | Source-of-truth counter from app code |
| Log-derived error rate | `log:order_errors:rate2m` | `Logs: errors ({{service_name}})` | Ops-derived metric from log patterns |

Notes:
- The SDK counter query uses `[2m]` to match the recording rule's window (D-L1 requirement: fair comparison)
- `log:order_errors:rate2m` needs no `rate()` wrapper -- it IS already a rate (the recording rule computes it)
- Both return events/sec on the same Y-axis scale

### Metric Labels After Remote-Write

When Loki's ruler remote-writes the recording rule output to Mimir, the metric carries:
- `__name__` = `log:order_errors:rate2m` (the `record:` name)
- `service_name` = value from `sum by (service_name)` aggregation (e.g., `"order-consumer"`)
- No automatic `job` or `instance` label is guaranteed without `write_relabel_configs` [ASSUMED]

The current `loki-config.yaml` does NOT have `write_relabel_configs`. This is acceptable for the workshop because:
1. The metric name prefix `log:` already prevents collision with SDK metrics (F4-1 mitigation)
2. No dashboard query depends on a `job` label for this metric
3. Adding `write_relabel_configs` would be a `loki-config.yaml` change, which violates the Phase 10 "no config changes needed" contract

### Dashboard Panel Structure

Following the Exemplars row (Phase 12) pattern:
- Next panel IDs: 17 (row), 18 (timeseries panel)
- Row at y=50 (after Exemplars panel at y=41, h=9 -- so y=50)
- Panel at y=51 (row height = 1)
- Row `collapsed: false` (open by default -- D-L2)
- Panel uses datasource `{"type": "prometheus", "uid": "prometheus"}` (Mimir via the Prometheus-compatible datasource)

### Recommended File Layout

```
infra/observability/loki-rules/
  .gitkeep                     # existing (keep or remove)
  fake/                        # NEW: tenant subdirectory (required!)
    order-errors.yaml          # NEW: the recording rule
```

### Anti-Patterns to Avoid

- **Placing rule file at root of loki-rules/ (no `fake/` subdir):** Ruler silently ignores it. No error in logs. The `/loki/api/v1/rules` endpoint returns empty.
- **Using [1m] rate window with 1m evaluation_interval:** Produces intermittent zero values due to aliasing (F4-2).
- **Naming the metric `orders_errors_total`:** Collides with potential SDK metrics in Mimir (F4-1). Use `log:` prefix convention.
- **Grouping by high-cardinality labels (orderId, spanId):** Cardinality bomb in Mimir (F4-4). Only group by `service_name`.
- **Editing loki-config.yaml:** Already pre-wired by Phase 10 D-07. No changes needed.
- **Editing docker-compose.yml:** Bind-mount already in place. No changes needed.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Error rate from logs | Custom log parser + counter service | Loki recording rule | Ruler handles scheduling, windowing, remote-write, retries |
| Metric storage | Write to a custom DB | Mimir (already running) | Mimir provides the Prometheus-compatible query API Grafana expects |
| Verification script | Manual curl commands | `mise run verify:log-metrics` | Reproducible, self-documenting, retry-tolerant |

## Common Pitfalls

### Pitfall 1: Missing `fake/` Tenant Subdirectory (F4-3 variant)

**What goes wrong:** Rule file placed at `/loki/rules/order-errors.yaml` instead of `/loki/rules/fake/order-errors.yaml`. Loki ruler finds zero rules. `/loki/api/v1/rules` returns empty. No error in Loki container logs.
**Why it happens:** Loki scans `ruler_storage.local.directory/<tenant_id>/` not the root. With `auth_enabled: false` the tenant is `"fake"` (underdocumented in official docs).
**How to avoid:** Always place rule files in `fake/` subdirectory when `auth_enabled: false`. Verify via `curl localhost:3100/loki/api/v1/rules` -- response must contain the rule group name.
**Warning signs:** Empty rules API response; `log:order_errors:rate2m` never appears in Mimir.

### Pitfall 2: Rate Window Aliasing (F4-2)

**What goes wrong:** Using `rate({...}[1m])` with `evaluation_interval: 1m` produces metrics that are intermittently 0 even when logs are flowing.
**Why it happens:** The rate() window aligns exactly with the evaluation boundary. If all logs in a window landed at T-61s (outside the [T-60s, T] window), rate is 0.
**How to avoid:** Set rate window >= 2x evaluation_interval. Our config: eval=1m, window=[2m].
**Warning signs:** Metric shows spiky zero-to-value oscillation; same LogQL in Explore returns data.

### Pitfall 3: Metric Name Collision (F4-1)

**What goes wrong:** Naming the recording rule output `orders_created_total` or `http_server_request_duration_seconds` -- collides with SDK-emitted metrics already in Mimir.
**Why it happens:** Recording rules and OTel SDK both write to the same Mimir instance; identical names create ambiguous queries.
**How to avoid:** Use `log:<metric>:<aggregation>` naming convention. Example: `log:order_errors:rate2m`.
**Warning signs:** PromQL `sum(rate(orders_created_total[5m]))` returns double the expected value.

### Pitfall 4: Cardinality Explosion (F4-4)

**What goes wrong:** Recording rule groups by `order_id`, `span_id`, or `trace_id` -- creates unbounded series in Mimir.
**Why it happens:** Every unique value creates a new time series. At workshop load of 1000 orders, that is 1000 series for one metric.
**How to avoid:** Only group by low-cardinality labels: `service_name`, `level`. Our rule uses `sum by (service_name)` -- 2 values max (order-producer, order-consumer).
**Warning signs:** `cortex_ingester_active_series` grows without bound.

### Pitfall 5: Container Restart Required After Adding Rule File

**What goes wrong:** Rule file added but Loki not restarted/reloaded. Rules not picked up.
**Why it happens:** Loki's local ruler storage is read at startup. Changes to mounted files require `SIGHUP` or container restart. (Unlike Prometheus which watches file globs.)
**How to avoid:** After adding the rule file, run `docker compose restart loki` or `docker compose kill -s HUP loki`. The verify task should include a note about this.
**Warning signs:** `/loki/api/v1/rules` returns empty after file creation.

## Code Examples

### Recording Rule File (complete)

```yaml
# infra/observability/loki-rules/fake/order-errors.yaml
# Source: Verified against Grafana Loki 3.7.1 recording rules documentation
#
# WHY this file: derives an error-rate metric FROM LOGS using LogQL.
# Teaching point: "you can compute a metric from log patterns without
# touching a single line of Java code."
#
# WHY the fake/ directory: Loki's ruler requires rule files at
# <ruler_storage.local.directory>/<tenant_id>/<file>.yaml.
# With auth_enabled: false, the implicit tenant ID is "fake".
#
# WHY [2m] window: evaluation_interval is 1m (loki-config.yaml).
# The rate() window must be >= 2x the eval interval to avoid aliasing
# (F4-2 mitigation — prevents intermittent zero-value readings).
#
# Result metric: log:order_errors:rate2m{service_name="order-consumer"}
# Delivered to Mimir via ruler.remote_write.clients.mimir

groups:
  - name: order-error-rules
    # WHY 1m: matches ruler.evaluation_interval in loki-config.yaml.
    # Override here makes the rule self-documenting even if global changes.
    interval: 1m
    rules:
      # WHY log: prefix: F4-1 mitigation — prevents collision with
      # SDK-emitted metric names in Mimir (e.g., orders_created_total).
      # Convention: log:<thing>:<aggregation_window>
      - record: log:order_errors:rate2m
        expr: |
          sum by (service_name) (
            rate(
              {service_name=~"order-.+"} |= "ERROR" [2m]
            )
          )
```

### verify:log-metrics Task (structural pattern)

```bash
# Tier 1: Loki rules API — confirm rule is loaded
curl -fsS http://localhost:3100/loki/api/v1/rules | jq -e '.data.groups[] | select(.name == "order-error-rules") | .rules[] | select(.name == "log:order_errors:rate2m")'

# Tier 2: Mimir query API — confirm metric has data
curl -fsS "http://localhost:9009/prometheus/api/v1/query?query=log:order_errors:rate2m" | jq -e '.data.result | length > 0'
```

### Dashboard Panel JSON (structural pattern)

```json
{
  "datasource": {"type": "prometheus", "uid": "prometheus"},
  "gridPos": {"h": 9, "w": 24, "x": 0, "y": 51},
  "id": 18,
  "title": "Log-Based Error Rate vs SDK Counter",
  "type": "timeseries",
  "description": "The gap between the SDK-emitted creation rate and the log-derived error rate is your success rate. The SDK counter is the source of truth; the log-derived metric is an ops-team approximation that requires no code change.",
  "targets": [
    {
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "expr": "sum by (service_name) (rate(orders_created_total[2m]))",
      "legendFormat": "SDK: orders created ({{service_name}})",
      "refId": "A"
    },
    {
      "datasource": {"type": "prometheus", "uid": "prometheus"},
      "expr": "log:order_errors:rate2m",
      "legendFormat": "Logs: errors ({{service_name}})",
      "refId": "B"
    }
  ]
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Loki ruler with Cortex-style rulestore API | Local filesystem rules + remote-write to Mimir | Loki 2.x+ | File-based rules are git-trackable; no API management needed |
| `lokiexporter` in OTel Collector | OTLP-native ingestion on Loki `/otlp` | Loki 3.0+ (2024) | `service_name` label auto-populated from OTel resource |
| Prometheus-style `--rule-files` hot-reload | `SIGHUP` or restart for local ruler storage | Stable behavior | Must restart/signal after rule file changes |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Loki ruler does not add a default `job` label to remote-written recording rule metrics without explicit `write_relabel_configs` | Architecture Patterns / Metric Labels | LOW -- dashboard queries do not depend on a job label; metric name prefix `log:` prevents collision regardless |
| A2 | The `/loki/api/v1/rules` endpoint returns JSON (not YAML) when queried from curl without Accept headers | Code Examples / verify task | LOW -- if YAML, jq will fail and the verify script needs yaml-to-json conversion; easily caught in testing |

## Open Questions

1. **Exact /loki/api/v1/rules JSON response structure**
   - What we know: It returns rule groups organized by tenant/namespace. The `jq` path `.data.groups[].rules[].name` is the likely shape (Prometheus-compatible rules API format).
   - What's unclear: Whether the response wraps in `{"status":"success","data":{"groups":[...]}}` (Prometheus style) or uses a different envelope.
   - Recommendation: The verify task should use a liberal grep/contains check first (`grep -F "log:order_errors:rate2m"`), then refine with jq if the structure is confirmed at runtime.

2. **Whether .gitkeep should be preserved alongside the fake/ directory**
   - What we know: `infra/observability/loki-rules/.gitkeep` exists as a Phase 10 placeholder.
   - What's unclear: Whether git tracks the `fake/` directory via the file inside it (it does -- git tracks files, not empty dirs).
   - Recommendation: Remove `.gitkeep` since `fake/order-errors.yaml` makes it redundant. But keeping it is harmless.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies identified). All infrastructure is pre-existing from Phase 10; this phase adds only configuration files.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | N/A -- no auth changes |
| V3 Session Management | No | N/A |
| V4 Access Control | No | N/A -- all services run with auth disabled (workshop context) |
| V5 Input Validation | No | YAML is static config, not user input |
| V6 Cryptography | No | N/A |

### Known Threat Patterns for This Phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious LogQL in rule file | Tampering | Rule files are git-tracked, read-only mount (:ro), no API-based rule creation enabled |
| Cardinality bomb via high-cardinality grouping | Denial of Service | F4-4 -- only group by low-cardinality labels; Mimir has series limits |
| Remote-write credential exposure | Information Disclosure | No credentials -- workshop uses auth_enabled: false on all services (documented anti-pattern for production) |

**Risk assessment:** MINIMAL. This phase adds a static YAML file (read-only bind-mount, git-tracked). No user-facing inputs, no auth changes, no secrets. The only security surface is the recording rule expression itself, which is authored by the developer and reviewed in git.

## Project Constraints (from CLAUDE.md)

- Pure infrastructure phase: zero Java code changes
- Workshop-grade configuration: everything runs offline on laptop
- Teaching-grade YAML: every block gets a WHY comment (Phase 10 D-04)
- `mise` is the task runner; verification lives in `mise.toml`
- Git-tracked configuration only; no UI-created rules
- Pinned versions; no floating tags
- Additive dashboard changes only (no edits to existing panels/UIDs)

## Sources

### Primary (HIGH confidence)
- [Grafana Loki recording rules documentation](https://grafana.com/docs/loki/latest/operations/recording-rules/) -- ruler YAML format, remote-write mechanics
- [Grafana Loki alerting/recording rules](https://grafana.com/docs/loki/latest/alert/) -- rule file structure, groups/rules schema
- [Grafana Loki OTLP ingestion](https://grafana.com/docs/loki/latest/send-data/otel/) -- service_name label auto-mapping from service.name
- [OTel Prometheus exporter spec](https://opentelemetry.io/docs/specs/otel/metrics/sdk_exporters/prometheus/) -- dot-to-underscore + _total suffix translation
- Codebase: `infra/observability/loki-config.yaml` (ruler block, lines 59-81) -- live config confirming D-07
- Codebase: `docker-compose.yml` (line 223) -- bind-mount `./infra/observability/loki-rules:/loki/rules:ro`
- Codebase: `producer-service/.../OrderService.java` -- `meter.counterBuilder("orders.created")` definition
- Codebase: `grafana/dashboards/ose-otel-demo.json` -- existing `orders_created_total` queries

### Secondary (MEDIUM confidence)
- [GitHub grafana/loki#5459](https://github.com/grafana/loki/issues/5459) -- "fake" tenant ID documentation request; confirms the `fake/` subdirectory requirement
- [GitHub grafana/loki#7589](https://github.com/grafana/loki/issues/7589) -- user confusion about rule directory structure; confirms `/loki/rules/fake/rules.yml` path
- [Grafana Loki configuration parameters](https://grafana.com/docs/loki/latest/configure/) -- ruler.storage.local.directory semantics
- [OneUptime: Loki Recording Rules guide (2026-01)](https://oneuptime.com/blog/post/2026-01-21-loki-recording-rules/view) -- confirmed `fake/` directory, `groups:` format, JSON rules API

### Tertiary (LOW confidence)
- Default labels on remote-written metrics (job/instance presence) -- no authoritative source found; marked as assumption A1

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all components pre-existing, versions pinned, no new dependencies
- Architecture: HIGH -- LogQL syntax verified, tenant directory requirement confirmed from multiple sources, metric name translation confirmed from codebase
- Pitfalls: HIGH -- F4-1 through F4-4 documented in PITFALLS.md, all mitigations verified against actual config

**Research date:** 2026-05-03
**Valid until:** 2026-06-03 (stable -- no moving parts; Loki 3.7.1 is pinned)
