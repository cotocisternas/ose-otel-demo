# Phase 12: Exemplars: Metrics to Trace Click-Through — Pattern Map

**Mapped:** 2026-05-03
**Files analyzed:** 7 files to edit (no new files)
**Analogs found:** 7 / 7

---

## File Classification

| Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` | config (SDK bootstrap) | request-response | `consumer-service/.../config/OtelSdkConfiguration.java` | exact (mirror file) |
| `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` | config (SDK bootstrap) | request-response | `producer-service/.../config/OtelSdkConfiguration.java` | exact (mirror file) |
| `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` | middleware (servlet filter) | request-response | self (restructure of existing doFilterInternal) | exact |
| `infra/observability/mimir-config.yaml` | config (infra) | batch | `infra/observability/otelcol-config.yaml` | role-match (YAML config, WHY-comment style) |
| `grafana/dashboards/ose-otel-demo.json` | config (dashboard) | event-driven | self (additive new row to existing JSON) | exact |
| `mise.toml` | config (task runner) | batch | self (additive task mirroring verify:tail-sampling block) | exact |
| `README.md` | docs | — | self (additive §12 after §11) | exact |

---

## Pattern Assignments

### `producer-service/.../config/OtelSdkConfiguration.java` (config, request-response)

**Analog:** `consumer-service/.../config/OtelSdkConfiguration.java` (identical structure, different service.name and scope string)

**Imports pattern** (producer lines 1-34):
```java
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
// Phase 12 adds ONE import here, after the SdkMeterProvider import:
import io.opentelemetry.sdk.metrics.ExemplarFilter;
```

**Core pattern — buildMeterProvider insertion point** (producer lines 452-498):
```java
private SdkMeterProvider buildMeterProvider(Resource resource) {
    String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
        .orElse(DEFAULT_OTLP_ENDPOINT);
    OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
        .setEndpoint(endpoint)
        .build();

    PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
        .setInterval(Duration.ofSeconds(10))
        .build();

    // Phase 12: add .setExemplarFilter(ExemplarFilter.traceBased()) HERE,
    // between .setResource(resource) and .registerMetricReader(metricReader):
    return SdkMeterProvider.builder()
        .setResource(resource)
        // NEW LINE (Phase 12 — EXMP-01):
        // .setExemplarFilter(ExemplarFilter.traceBased())
        .registerMetricReader(metricReader)
        .build();
}
```

**WHY-comment style** (follows Phase 10 D-04 one-line density — from producer lines 494-497):
```java
// ----- MeterProvider: assembles resource + reader -----
//
// No custom View / ExplicitBucketHistogramAggregation (D-15) — ...
return SdkMeterProvider.builder()
```
Phase 12 adds a single inline comment block for the new call, following the established "// ----- section name -----" header + blank + reason body convention.

**Import package** (from RESEARCH.md, verified against SDK 1.61.0):
```java
import io.opentelemetry.sdk.metrics.ExemplarFilter;
// NOT io.opentelemetry.api.metrics.ExemplarFilter — package is sdk.metrics, not api.metrics
```

---

### `consumer-service/.../config/OtelSdkConfiguration.java` (config, request-response)

**Analog:** `producer-service/.../config/OtelSdkConfiguration.java` — byte-for-byte mirror (TRACE-01/DOC-05 duplication pattern)

**Same edit as producer** (consumer lines 460-506):
```java
private SdkMeterProvider buildMeterProvider(Resource resource) {
    // ... identical to producer except "order-consumer" scope name ...
    return SdkMeterProvider.builder()
        .setResource(resource)
        // NEW LINE (Phase 12 — EXMP-01):
        // .setExemplarFilter(ExemplarFilter.traceBased())
        .registerMetricReader(metricReader)
        .build();
}
```

**Duplication contract** (from consumer lines 48-52):
```
// <p><b>Why duplicated per service?</b> The IDENTICAL file lives in
// producer-service/.../config/OtelSdkConfiguration.java with two changes
// only: the service.name string ("order-consumer" → "order-producer") and
// the tracer scope name ("com.example.consumer" → "com.example.producer").
```
Phase 12 adds the SAME `.setExemplarFilter(ExemplarFilter.traceBased())` line in BOTH files independently. The import goes in BOTH files' import blocks. This preserves the "two readings" teaching surface.

---

### `producer-service/.../config/HttpServerSpanFilter.java` (middleware, request-response)

**Analog:** self — restructure of `doFilterInternal()` (lines 128-219)

**Current pattern — try-with-resources** (lines 170-217):
```java
try (Scope scope = span.makeCurrent()) {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        (long) response.getStatus());
} catch (RuntimeException | ServletException | IOException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    requestDuration.record(seconds, Attributes.of(
        HttpAttributes.HTTP_REQUEST_METHOD, method,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus()));
    span.end();
}
```

**Target pattern — manual scope management** (D-E1, per RESEARCH.md Pitfall 3):
```java
Scope scope = span.makeCurrent();
try {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        (long) response.getStatus());
} catch (RuntimeException | ServletException | IOException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    requestDuration.record(seconds, Attributes.of(
        HttpAttributes.HTTP_REQUEST_METHOD, method,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus()));
    span.end();
    // close scope AFTER record() so ExemplarFilter.traceBased() sees active span
    scope.close();
}
```

**WHY-comment style** (D-E3 — one-line comment directly above `scope.close()`):
```java
    // close scope AFTER record() so ExemplarFilter.traceBased() sees active span
    scope.close();
```
Matches Phase 10 D-04 density: single `//` comment, no Javadoc, no multi-line block, placed immediately above the line it explains.

**Structural note:** The only change is:
1. Replace `try (Scope scope = span.makeCurrent()) {` with `Scope scope = span.makeCurrent();` + `try {`
2. Move `scope.close()` to the bottom of the `finally` block, after `span.end()`
3. Add the one-line WHY comment above `scope.close()`

No import changes needed — `Scope` is already imported (line 12: `import io.opentelemetry.context.Scope;`).

---

### `infra/observability/mimir-config.yaml` (config, batch)

**Analog:** `infra/observability/otelcol-config.yaml` for WHY-comment style; `infra/observability/mimir-config.yaml` itself for the existing block structure.

**Existing mimir-config.yaml style** (lines 1-70):
```yaml
# Grafana Mimir 3.0.6 — single-binary single-tenant metric backend.
#
# THIS FILE IS THE WORKSHOP'S TEXTBOOK FOR MIMIR.
# Every block below carries an inline `# WHY:` comment.

multitenancy_enabled: false   # WHY: STACK-05 — single-tenant workshop; ...
usage_stats:
  enabled: false              # WHY: workshop-only; opt out of analytics
```

**Target addition** (after `usage_stats:` block at line 69-70, as a new top-level block):
```yaml
limits:
  max_global_exemplars_per_user: 100000  # WHY: 0 (default) = exemplar ingestion disabled;
                                          # 100000 is the Grafana-recommended starting value
                                          # (grafana.com/docs/mimir/latest/manage/use-exemplars/store-exemplars/)
```

**WHY-comment placement** (follows the inline `# WHY:` convention from mimir-config.yaml lines 18, 23, 30 — NOT a preceding line comment):
- Multi-line notes use continuation lines starting with `#` at the same indent level as the `# WHY:` line

**Critical note:** Do NOT add `send_exemplars: true` to `infra/observability/otelcol-config.yaml`. This key does not exist in collector-contrib v0.151.0's `prometheusremotewrite` exporter config schema. Exemplar forwarding is unconditional in the PRW v1 translator. See RESEARCH.md §Critical Collector Finding for verification details.

---

### `grafana/dashboards/ose-otel-demo.json` (config, event-driven)

**Analog:** The Tail Sampling diagnostics row block (lines 388-546) — the most recently added row.

**Collapsed row pattern** (lines 388-394, the Phase 11 tail-sampling row):
```json
{
  "collapsed": true,
  "gridPos": { "h": 1, "w": 24, "x": 0, "y": 21 },
  "id": 9,
  "title": "Tail Sampling diagnostics (Phase 11)",
  "type": "row",
  "description": "...",
  "panels": [ ... ]
}
```

**Open row pattern** (D-E4 — `"collapsed": false`):
```json
{
  "collapsed": false,
  "gridPos": { "h": 1, "w": 24, "x": 0, "y": 40 },
  "id": 15,
  "title": "Exemplars (Phase 12)",
  "type": "row",
  "panels": []
}
```

**gridPos.y coordinate** (from codebase verification):
- Tail Sampling row header: `y=21`
- Last panel in Tail Sampling row: `gridPos: { "h": 9, "w": 8, "x": 16, "y": 31 }` (id=14, lines 520-521)
- Bottom edge of last panel: `31 + 9 = 40`
- Therefore new Exemplars row header: `y = 40` (immediately follows)
- Note: RESEARCH.md stated y=41 — the actual codebase confirms y=40 is correct (the last panel bottom edge IS the next row's y)

**Panel ID sequence** (from codebase): last used ID is 14 (line 521). New row ID = 15. Histogram panel ID = 16.

**Timeseries panel pattern** (from existing panels, e.g. id=10 at lines 396-421):
```json
{
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "gridPos": { "h": 9, "w": 12, "x": 0, "y": 22 },
  "id": 10,
  "title": "...",
  "type": "timeseries",
  "description": "...",
  "fieldConfig": {
    "defaults": {
      "color": { "mode": "palette-classic" },
      "custom": { "drawStyle": "line", "lineInterpolation": "linear", "lineWidth": 1, "fillOpacity": 10 }
    }
  },
  "options": {
    "legend": { "displayMode": "list", "placement": "bottom", "showLegend": true },
    "tooltip": { "mode": "single" }
  },
  "targets": [
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "expr": "...",
      "legendFormat": "...",
      "refId": "A"
    }
  ]
}
```

**Exemplar panel deviation** — adds `"exemplar": true` to each target and `"options": { "exemplarLabel": "trace_id" }` per RESEARCH.md:
```json
{
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "gridPos": { "h": 9, "w": 24, "x": 0, "y": 41 },
  "id": 16,
  "title": "HTTP Request Duration (with Exemplars)",
  "type": "timeseries",
  "options": {
    "exemplarLabel": "trace_id",
    "legend": { "displayMode": "list", "placement": "bottom", "showLegend": true },
    "tooltip": { "mode": "single" }
  },
  "fieldConfig": {
    "defaults": {
      "color": { "mode": "palette-classic" },
      "custom": { "drawStyle": "line", "lineInterpolation": "linear", "lineWidth": 1, "fillOpacity": 10 }
    }
  },
  "targets": [
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "expr": "histogram_quantile(0.50, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))",
      "legendFormat": "p50",
      "refId": "A",
      "exemplar": true
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "expr": "histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))",
      "legendFormat": "p95",
      "refId": "B",
      "exemplar": true
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "expr": "histogram_quantile(0.99, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))",
      "legendFormat": "p99",
      "refId": "C",
      "exemplar": true
    }
  ]
}
```

**Insertion point in JSON:** The new row object and its histogram panel object go inside the top-level `"panels": [...]` array, AFTER the closing `}` of the Tail Sampling row block (after line 546 `}`) and BEFORE the closing `]` at line 547.

**Dashboard UID invariant** (line 579): `"uid": "ose-otel-demo"` — must not change.

---

### `mise.toml` (config/task, batch)

**Analog:** `[tasks."verify:tail-sampling"]` block (lines 455-564) — the most recently added verify task.

**Task header pattern** (lines 453-456):
```toml
# Phase 11 — verify:tail-sampling (D-T14)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:tail-sampling"]
description = "Phase 11 invariant: ..."
run = """
```

**Phase 12 task header** (D-E7):
```toml
# Phase 12 — verify:exemplars (D-E7)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:exemplars"]
description = "Phase 12 invariant: Mimir stores at least one exemplar with trace_id for http_server_request_duration_seconds_bucket — proves SDK filter + Mimir limits config are active."
run = """
set -e
```

**verify:exemplars bash body** (from RESEARCH.md §verify:exemplars mise task):
```bash
START=$(date -u -d '10 minutes ago' '+%Y-%m-%dT%H:%M:%SZ')
END=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
RESULT=$(curl -fsS "http://localhost:9009/prometheus/api/v1/query_exemplars?query=http_server_request_duration_seconds_bucket&start=${START}&end=${END}")
if ! printf '%s' "$RESULT" | jq -e '.data | length > 0 and (.[0].exemplars | length > 0) and (.[0].exemplars[0].labels.trace_id != null)' >/dev/null 2>&1; then
  echo "ERROR: verify:exemplars — no exemplars with trace_id in Mimir"
  echo "Response: $RESULT"
  exit 1
fi
echo "verify:exemplars: exemplars with trace_id present in Mimir."
```

**Retry loop pattern** — the verify:datasources and verify:tail-sampling tasks use a retry loop (6×5s = 30s). For verify:exemplars, the RESEARCH.md shows a simpler single-pass check (generate load first, then verify). Use the simpler shape for this task — Mimir exemplars are persistent once ingested, unlike Collector startup timing.

**Placement in mise.toml:** Add the new `[tasks."verify:exemplars"]` block AFTER the `[tasks."verify:tail-sampling"]` block (after line 564).

---

### `README.md` (docs)

**Analog:** README §11 section (Phase-11-equivalent depth, D-E8).

**Section heading pattern** (follow existing README heading style — planner reads the README to confirm exact format):
- Phase sections are `## Step N` or `## Phase N` — planner confirms actual heading level from existing README
- README §12 is additive: append after the §11 section

**Content structure per D-E8** (seven subsections, ~100-150 lines):
1. What exemplars are (concept paragraph)
2. The three layers explained (SDK filter, Collector auto-forwarding, Grafana datasource)
3. Annotated config excerpts: `buildMeterProvider()` one-liner + `mimir-config.yaml` limits block + `datasources.yaml` exemplarTraceIdDestinations (already wired)
4. Cardinality safety note (SC#4: only trace_id/span_id, ~60 bytes)
5. `mise run verify:exemplars` invocation + expected output
6. Click-through UX description (dots on histogram panel → Tempo trace waterfall)
7. Placeholder screenshot with HTML img tag + caption per D-E10

**Screenshot placeholder pattern** (D-E9/D-E10):
```html
<img src="docs/screenshots/step-12-exemplars.png"
     alt="Exemplar dots on http.server.request.duration — click any dot to land on the originating trace in Tempo."
     width="900" />
<!-- Screenshot captured by Phase 18 Playwright automation -->
```

**Consumer exemplar note** (D-E11, one sentence):
```
The consumer's counter exemplars exist in Mimir but are only visible via the API, not as chart dots — histograms are the natural exemplar surface.
```

---

## Shared Patterns

### WHY-comment density (Phase 10 D-04)
**Source:** `infra/observability/mimir-config.yaml` lines 18, 23, 30; `infra/observability/otelcol-config.yaml` lines 34-44
**Apply to:** All YAML changes (mimir-config.yaml `limits:` block)
```yaml
max_global_exemplars_per_user: 100000  # WHY: 0 (default) = exemplar ingestion disabled; ...
```
Pattern: inline `# WHY:` comment on the same line for single-value settings; continuation lines for multi-sentence explanations.

### Additive JSON pattern (no existing-panel edits, no UID changes)
**Source:** `grafana/dashboards/ose-otel-demo.json` — Phase 11 tail-sampling row was appended without modifying any pre-existing panel
**Apply to:** `ose-otel-demo.json` Phase 12 exemplar row
- New row object appended to `"panels"` array
- New panel inside the new row's `"panels"` array
- Existing panel IDs 1-14 unchanged
- Dashboard UID `"ose-otel-demo"` unchanged
- `"schemaVersion": 39` unchanged

### Additive task pattern (mirror verify:tail-sampling structure)
**Source:** `mise.toml` lines 453-564 (`verify:tail-sampling` task)
**Apply to:** `mise.toml` `verify:exemplars` task
- `[tasks."verify:exemplars"]` header with phase comment + dashes
- `description = "..."` single-line string
- `run = """ ... """` triple-quoted bash block
- `set -e` at top of bash block
- Informational comments before each logical step
- Clear `echo "ERROR: ..."` + `exit 1` on failure
- Clear `echo "verify:exemplars: ..."` on success

### TRACE-01/DOC-05 per-service duplication
**Source:** `producer-service/.../OtelSdkConfiguration.java` class Javadoc (lines 41-84); `consumer-service/.../OtelSdkConfiguration.java` class Javadoc (lines 41-93)
**Apply to:** Both `OtelSdkConfiguration.java` files for the `ExemplarFilter` change
- Both files get the SAME new import and the SAME new `.setExemplarFilter()` call
- No extraction to `otel-bootstrap` — the per-service duplication is intentional (workshop teaching surface)
- Comment in each file references "Phase 12 — EXMP-01" for traceability

---

## No Analog Found

None — all seven modified files have strong analogs or are self-edits with clear before/after patterns.

---

## Pre-Edit Verification Notes

Before planning the dashboard JSON edit, the planner MUST confirm the actual last `gridPos.y` value in `grafana/dashboards/ose-otel-demo.json`:
- Current last panel: id=14, `"gridPos": { "h": 9, "w": 8, "x": 16, "y": 31 }` (line 520)
- Bottom edge: 31 + 9 = **40**
- New Exemplars row header: `"y": 40`
- New histogram panel (inside row panels array): `"y": 41`
- RESEARCH.md stated y=41 for the row header — the codebase shows y=40 is correct for the header; y=41 for the panel inside it

---

## Metadata

**Analog search scope:** `producer-service/`, `consumer-service/`, `infra/observability/`, `grafana/dashboards/`, `grafana/datasources.yaml`, `mise.toml`
**Files scanned:** 7 directly read
**Pattern extraction date:** 2026-05-03
