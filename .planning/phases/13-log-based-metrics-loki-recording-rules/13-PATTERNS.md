# Phase 13: Log-Based Metrics (Loki Recording Rules) - Pattern Map

**Mapped:** 2026-05-03
**Files analyzed:** 4 (1 new, 3 modified)
**Analogs found:** 4 / 4

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `infra/observability/loki-rules/fake/order-errors.yaml` | config | event-driven (ruler evaluates LogQL on schedule) | `infra/observability/loki-config.yaml` | role-match |
| `grafana/dashboards/ose-otel-demo.json` | config | request-response (Grafana renders panel) | Same file, Exemplars row (lines 547-598) | exact |
| `mise.toml` | utility (task runner) | request-response (CLI verification) | Same file, `verify:exemplars` (lines 464-499) | exact |
| `README.md` | docs | N/A | Same file, Step 12 section (lines 624-714) | exact |

## Pattern Assignments

### `infra/observability/loki-rules/fake/order-errors.yaml` (config, event-driven)

**Analog:** `infra/observability/loki-config.yaml`

This is a NEW file. No exact analog exists (no other Loki recording rule YAML in the codebase). The closest structural analog is the `loki-config.yaml` file itself -- both are teaching-grade YAML with inline `# WHY:` comments.

**Teaching-grade YAML comment style** (loki-config.yaml, lines 1-24):
```yaml
# Grafana Loki 3.7.1 — single-binary log backend with ruler pre-enabled.
#
# THIS FILE IS THE WORKSHOP'S TEXTBOOK FOR LOKI.
# Every block below carries an inline `# WHY:` comment.
#
# WHY this file exists: grafana/loki:3.7.1 ships a default config at
# /etc/loki/local-config.yaml. We OVERRIDE that file (Plan 04 bind-mounts ours
# at the same path) so the ruler is enabled from day one — D-07. Phase 13 lands
# loki-rules/order-errors.yaml into the bind-mounted /loki/rules dir; no
# loki-config.yaml change required at that point.
```

**Inline WHY comments on config values** (loki-config.yaml, lines 59-81):
```yaml
ruler:
  storage:
    type: local
    local:
      directory: /loki/rules    # F4-3 MITIGATION: this path matches Plan 04's bind-mount
                                # `./infra/observability/loki-rules:/loki/rules:ro`.
                                # Loki scans this dir on (re)start; SIGHUP triggers reload.
  rule_path: /tmp/loki/scratch  # WHY: temp scratch for in-flight rule evaluations; not persistent
  enable_api: true              # WHY: enables /loki/api/v1/rules read-back endpoint
  evaluation_interval: 1m       # D-07 — every minute (F4-2 mitigation: Phase 13 uses [2m] window = 2x interval)
  remote_write:
    enabled: true
    clients:
      mimir:                    # WHY: client name is the map key. Loki uses `clients:` MAP
                                # (NOT Prometheus's flat `remote_write:` array — RESEARCH Pitfall 7).
        url: http://mimir:9009/api/v1/push   # D-06 spirit: same Mimir endpoint as Tempo + Collector
```

**Pattern to follow for the new recording rule file:**
- File-level header comment block: what the file is, why it exists, the teaching point
- Inline `# WHY:` comments on every non-obvious value
- Reference pitfall mitigations by ID (e.g., F4-2, F4-3)
- Reference CONTEXT.md decision IDs (e.g., D-07)

---

### `grafana/dashboards/ose-otel-demo.json` (config, request-response)

**Analog:** Same file, Exemplars row (Phase 12) -- lines 547-598

**Open row structure** (lines 547-554):
```json
{
  "collapsed": false,
  "gridPos": { "h": 1, "w": 24, "x": 0, "y": 40 },
  "id": 15,
  "title": "Exemplars (Phase 12)",
  "type": "row",
  "description": "Phase 12 — ExemplarFilter.traceBased() on both SdkMeterProviders attaches trace_id/span_id to histogram data points when a span is active at record() time. Grafana requests exemplar data from Mimir when panel query targets include exemplar: true. Click any exemplar dot to navigate to the originating trace in Tempo.",
  "panels": []
}
```

**Timeseries panel with Prometheus datasource** (lines 556-598):
```json
{
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "gridPos": { "h": 9, "w": 24, "x": 0, "y": 41 },
  "id": 16,
  "title": "HTTP Request Duration (with Exemplars)",
  "type": "timeseries",
  "description": "p50/p95/p99 latency percentiles for the producer service's HTTP server. Exemplar dots appear when load is running and the ExemplarFilter.traceBased() pipeline is active — click any dot to open the originating trace in Tempo. Both producer and consumer OtelSdkConfiguration.buildMeterProvider() calls have .setExemplarFilter(ExemplarFilter.traceBased()) (EXMP-01).",
  "fieldConfig": {
    "defaults": {
      "color": { "mode": "palette-classic" },
      "custom": { "drawStyle": "line", "lineInterpolation": "linear", "lineWidth": 1, "fillOpacity": 10 },
      "unit": "s"
    }
  },
  "options": {
    "legend": { "displayMode": "list", "placement": "bottom", "showLegend": true },
    "tooltip": { "mode": "single" }
  },
  "targets": [
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "expr": "histogram_quantile(0.50, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))",
      "legendFormat": "p50",
      "refId": "A",
      "exemplar": true
    }
  ]
}
```

**Pattern to follow for Phase 13 row + panel:**
- Row: `collapsed: false`, `"panels": []`, `type: "row"`, teaching-grade `description`
- Panel: `type: "timeseries"`, datasource `{"type": "prometheus", "uid": "prometheus"}`
- Next IDs: 17 (row), 18 (panel) -- after existing max ID 16
- gridPos: row at y=50 (after exemplars panel at y=41, h=9), panel at y=51
- Panel uses `fieldConfig.defaults.color.mode: "palette-classic"`, `custom.drawStyle: "line"`
- Unit field: use `"ops"` (events/sec) instead of `"s"` (seconds) since D-L1 specifies rate
- Targets: two entries (A = SDK counter rate, B = log-derived metric), each with own `legendFormat`
- Insert new row+panel BEFORE the closing `]` of the `panels` array (line 599) -- after the exemplars panel

---

### `mise.toml` (utility, request-response)

**Analog:** Same file, `verify:exemplars` task -- lines 464-499

**Task header pattern** (lines 464-466):
```toml
# Phase 12 — verify:exemplars (D-E7)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:exemplars"]
description = "Phase 12 invariant: Mimir stores at least one exemplar with trace_id for http_server_request_duration_seconds_bucket — proves SDK ExemplarFilter + Mimir limits config are both active."
```

**Task body pattern (simpler two-tier check)** (lines 468-499):
```bash
set -e

# Query the last 10 minutes of exemplars from Mimir's Prometheus-compatible API.
# Prerequisites: mise run load (generate traffic), wait ~30s for metrics export, then run this task.
# Uses epoch arithmetic (POSIX-portable) instead of GNU-only date -d.
EPOCH_NOW=$(date +%s)
EPOCH_START=$((EPOCH_NOW - 600))

echo "verify:exemplars: querying Mimir exemplar API for http_server_request_duration_seconds_bucket..."
RESULT=$(curl -fsS "http://localhost:9009/prometheus/api/v1/query_exemplars?query=http_server_request_duration_seconds_bucket&start=${EPOCH_START}&end=${EPOCH_NOW}")

# Assert: .data array non-empty AND first series has exemplars AND first exemplar has a trace_id label.
# An empty .data array means the exemplar pipeline is not yet active (check all three layers).
if ! printf '%s' "$RESULT" | jq -e '.data | length > 0 and (.[0].exemplars | length > 0) and (.[0].exemplars[0].labels.trace_id != null)' >/dev/null 2>&1; then
  echo "ERROR: verify:exemplars — no exemplars with trace_id found in Mimir for the last 10 minutes"
  echo "Diagnose in order:"
  echo "  1. SDK layer: grep 'setExemplarFilter' ..."
  echo "  2. Scope fix: grep -c 'try (Scope' ..."
  echo "  3. Mimir limits: grep 'max_global_exemplars_per_user' ..."
  echo "  4. Mimir restart: docker compose restart mimir && sleep 10"
  echo "  5. Generate load: mise run load && sleep 30 && mise run verify:exemplars"
  echo "Mimir API response: $RESULT"
  exit 1
fi

echo "verify:exemplars: GREEN — exemplars with trace_id are present in Mimir. Click any dot on the"
echo "  'HTTP Request Duration (with Exemplars)' panel in the ose-otel-demo dashboard to navigate to Tempo."
```

**Retry pattern from verify:tail-sampling** (lines 376-411):
```bash
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""

# --- Tier 1: self-metrics ---
for i in $(seq 1 $ATTEMPTS); do
  ACTUAL=$(curl -fsS http://localhost:8888/metrics 2>&1) || {
    LAST_ERR="curl :8888 failed: $ACTUAL"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "verify:tail-sampling tier-1 attempt $i/$ATTEMPTS — Collector self-metrics not ready ($LAST_ERR); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:tail-sampling tier-1 timed out after $((ATTEMPTS * SLEEP_SECS))s — Collector :8888 not reachable."
    echo "Last error: $LAST_ERR"
    echo "Run: mise run infra:up"
    exit 1
  }
  # ... assertion on response ...
  break
done
echo "verify:tail-sampling tier-1: composite-policy registered and emitting (self-metrics)."
```

**Pattern to follow for `verify:log-metrics`:**
- Comment header: `# Phase 13 — verify:log-metrics (D-L4)` + separator line
- TOML key: `[tasks."verify:log-metrics"]`
- `description` = one sentence mentioning invariant name and what it proves
- Body: `set -e`, WHY comments, two-tier check
- Tier 1: retry loop (6x5s = 30s) curling Loki `/loki/api/v1/rules`, grep or jq for rule group name
- Tier 2: curl Mimir `/prometheus/api/v1/query?query=log:order_errors:rate2m`, jq assert non-empty
- Diagnostic echo with numbered causes on failure
- GREEN message on success with guidance text

---

### `README.md` (docs, N/A)

**Analog:** Same file, Step 12 section -- lines 624-714

**Section heading format** (line 624):
```markdown
## Step 12: Exemplars — three lines that make histogram charts clickable
```

**Subsection structure** (lines 626-714):
```markdown
### What you'll learn
[bulleted list of teaching points]

### Checkpoint
[git tag info, diff command]

### Run
[fenced bash block with commands]

### What to look for
[explanation of what attendees should observe in Grafana UI]

### Why it matters
[2-3 paragraphs on the conceptual significance]
```

**Screenshot placeholder pattern** (lines 709-714):
```markdown
**Screenshot placeholder:**

<img src="docs/screenshots/step-12-exemplars.png"
     alt="Exemplar dots on http.server.request.duration — click any dot to land on the originating trace in Tempo."
     width="900" />
<!-- Screenshot captured by Phase 18 Playwright automation -->
```

**Pattern to follow for README section 13:**
- Heading: `## Step 13: Log-Based Metrics — an error rate from logs, zero Java code`
- Subsections: What you'll learn, Checkpoint, Run, What to look for, Why it matters
- Lead with zero-code angle per D-L7
- Include "when to use which" comparison table per D-L8
- Include annotated YAML excerpt (the recording rule)
- Include `mise run verify:log-metrics` invocation
- Screenshot placeholder matching Phase 12 pattern (D-L9)
- Target ~100-150 lines depth (D-L6)

## Shared Patterns

### Teaching-Grade YAML (Phase 10 D-04)
**Source:** `infra/observability/loki-config.yaml` (entire file)
**Apply to:** `infra/observability/loki-rules/fake/order-errors.yaml`
```yaml
# File-level header: what the file is, why it exists
# Per-value inline: # WHY: reason
# Per-value inline: # D-XX: decision reference
# Per-value inline: # F4-N MITIGATION: pitfall reference
```

### Grafana Dashboard Additive Row Pattern
**Source:** `grafana/dashboards/ose-otel-demo.json` lines 547-598
**Apply to:** Same file (new row + panel appended before closing bracket)
```json
// Open row (collapsed: false) with "panels": []
// Followed by standalone panel object in the top-level panels array
// gridPos.y increments: row y=N, panel y=N+1
// IDs increment: next free after max existing
```

### Mise Verify Task Pattern
**Source:** `mise.toml` lines 464-499 (verify:exemplars) and lines 353-462 (verify:tail-sampling)
**Apply to:** New `[tasks."verify:log-metrics"]` block
```toml
# Phase N — verify:task-name (D-XX)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:task-name"]
description = "Phase N invariant: ..."
run = """
set -e
# WHY + retry loop + tier-1 + tier-2 + diagnostic echo + GREEN echo
"""
```

### README Step Section Pattern
**Source:** `README.md` lines 624-714 (Step 12)
**Apply to:** New Step 13 section appended after Step 12, before "Concepts & FAQ"
```markdown
## Step N: Title — subtitle
### What you'll learn
### Checkpoint
### Run
### What to look for
### Why it matters
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| (none) | -- | -- | All files have analogs in the existing codebase |

Note: `infra/observability/loki-rules/fake/order-errors.yaml` is a new file type (Loki recording rule YAML), but the `loki-config.yaml` file provides a strong style analog (teaching-grade YAML with inline WHY comments), and the RESEARCH.md provides the exact YAML structure verified against Loki 3.7.1 docs.

## Metadata

**Analog search scope:** `infra/observability/`, `grafana/dashboards/`, `mise.toml`, `README.md`
**Files scanned:** 8 (4 YAML configs, 1 JSON dashboard, 1 TOML, 1 markdown, 1 .gitkeep)
**Pattern extraction date:** 2026-05-03
