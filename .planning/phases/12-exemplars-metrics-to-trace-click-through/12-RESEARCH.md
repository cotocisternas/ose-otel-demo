# Phase 12: Exemplars: Metrics to Trace Click-Through — Research

**Researched:** 2026-05-03
**Domain:** OTel Java SDK ExemplarFilter + Mimir exemplar storage + Grafana dashboard exemplar panel
**Confidence:** HIGH (all critical path items verified against source code and live environment)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-E1: Silent infrastructure fix** — restructure `HttpServerSpanFilter.doFilterInternal()` from try-with-resources (`try (Scope scope = span.makeCurrent()) { ... } finally { record(); }`) to manual scope management (`Scope scope = span.makeCurrent(); try { ... } finally { record(); span.end(); scope.close(); }`). Fix presented as silent infrastructure — no README narrative about it.

**D-E2: Producer only** — consumer has no `HttpServerSpanFilter`. No equivalent scope fix needed on the consumer side.

**D-E3: One-line WHY comment** — single comment above `scope.close()`: `// close scope AFTER record() so ExemplarFilter.traceBased() sees active span`.

**D-E4: New row "Exemplars (Phase 12)" — OPEN by default** — unlike Phase 11's collapsed row.

**D-E5: Single histogram panel** — one timeseries panel showing `histogram_quantile(0.50/0.95/0.99, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))` with `"exemplar": true` query flag.

**D-E6: Row position** — placed AFTER top-level panels, BEFORE collapsed "Deeper-dive" and "Tail Sampling diagnostics" rows.

**D-E7: `mise run verify:exemplars` task** — curl-based task querying Mimir's exemplar API, asserting >= 1 exemplar with trace_id label.

**D-E8: README §12 is Phase-11-equivalent depth (~100-150 lines)**.

**D-E9: No manual screenshots — deferred to Phase 18.**

**D-E10: Placeholder + caption in README.**

**D-E11: Both services get ExemplarFilter, teach the asymmetry** — SC#1 literal compliance: both `OtelSdkConfiguration.buildMeterProvider()` calls add `.setExemplarFilter(ExemplarFilter.traceBased())`. README includes one-line note on counter exemplars (API-only, not visible as chart dots).

### Claude's Discretion

- Exact PromQL expression for the histogram panel — planner picks defensible queries matching RED Metrics panel conventions.
- Exact `verify:exemplars` bash implementation — planner picks a defensible pattern mirroring `verify:tail-sampling`.
- Panel `gridPos` coordinates, ID numbering, and JSON structure.
- README §12 word-for-word phrasing.
- Whether `send_exemplars` or `exemplars.send` is the key — **researcher resolves below (critical finding)**.
- Exact import statement for `ExemplarFilter` — **researcher resolves below**.

### Deferred Ideas (OUT OF SCOPE)

- Head sampling (Phase 16)
- Native histograms / heatmap visualization
- Consumer-side histogram metrics
- Manual screenshot capture (Phase 18)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXMP-01 | Both `buildMeterProvider()` calls add `.setExemplarFilter(ExemplarFilter.traceBased())` | Verified: `io.opentelemetry.sdk.metrics.ExemplarFilter` interface, `traceBased()` factory method; `SdkMeterProviderBuilder.setExemplarFilter()` method — all in SDK 1.61.0 |
| EXMP-02 | Collector's `prometheusremotewrite/mimir` exporter configured with `send_exemplars: true` | **CRITICAL FINDING**: `send_exemplars` is NOT a valid config key in collector-contrib v0.151.0's PRW exporter. Exemplar forwarding is automatic when OTLP data contains exemplars. Mimir must have `max_global_exemplars_per_user` set to a positive value. See §Critical Collector Finding below. |
| EXMP-03 | `grafana/datasources.yaml` includes `exemplarTraceIdDestinations` with `trace_id` mapped to Tempo UID | Already wired in Phase 10 (lines 50–56). Label name `trace_id` is correct — matches `ExemplarTraceIDKey = "trace_id"` constant in `pkg/translator/prometheus/constants.go`. No edits needed. |
| EXMP-04 | Attendee clicks exemplar dot in histogram panel, lands on trace in Tempo | Enabled by: SDK filter + Mimir exemplar storage + Grafana datasource wiring (pre-wired) + panel `"exemplar": true` query flag. Verified Mimir exemplar API returns empty set now — pipeline not yet active. |
</phase_requirements>

---

## Summary

Phase 12 wires the three-layer exemplar plumbing: (1) OTel Java SDK `ExemplarFilter.traceBased()` on both `SdkMeterProvider`s, (2) Mimir exemplar storage enabled via `max_global_exemplars_per_user: 100000`, (3) a new histogram panel in the dashboard with `"exemplar": true` query flag. The Grafana datasource side (layer 3) was pre-wired in Phase 10 and requires no edits.

**Critical finding that invalidates CONTEXT.md D-E and PITFALLS.md F3-1 item #3:** The prometheusremotewrite exporter in opentelemetry-collector-contrib v0.151.0 has **no `send_exemplars` config key**. Exemplar forwarding is unconditional in the PRW v1 translator — when OTLP histogram data points contain exemplars, `pkg/translator/prometheusremotewrite/helper.go::getPromExemplars()` extracts them and populates `ts.Exemplars` automatically. The CONTEXT.md line `send_exemplars: true` will be silently ignored (unknown YAML key warning) or cause a config parse error. The planner must substitute the real blocker: **Mimir defaults `max_global_exemplars_per_user: 0`**, which silently discards all ingested exemplars. The fix is a `limits:` block in `mimir-config.yaml`.

A secondary infrastructure fix (D-E1) restructures `HttpServerSpanFilter.doFilterInternal()` so `requestDuration.record()` executes while the SERVER span is still in active scope. Without this fix the SDK sees `SpanContext.invalid()` at measurement time and attaches no exemplar regardless of filter setting.

The scope fix + Mimir `limits` block + SDK filter together form the complete activation path. Grafana datasource and Tempo are already configured correctly from Phase 10.

**Primary recommendation:** Replace `send_exemplars: true` in otelcol-config.yaml with `max_global_exemplars_per_user: 100000` in `mimir-config.yaml`'s `limits:` block. This is the real gate blocking exemplar storage.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Exemplar attachment at measurement time | Java application (SDK) | — | SDK attaches trace context to histogram measurements when active span is in scope; no infra involvement |
| Exemplar forwarding SDK → Collector | OTel Collector (OTLP receiver) | — | OTLP wire protocol carries exemplars natively in histogram data point proto; PRW translator extracts them automatically |
| Exemplar storage in metric backend | Mimir | — | Mimir must be configured with positive `max_global_exemplars_per_user` to store ingested exemplars |
| Exemplar click-through routing | Grafana datasource config | — | `exemplarTraceIdDestinations` in datasources.yaml routes `trace_id` label value → Tempo; pre-wired in Phase 10 |
| Exemplar dot rendering in panel | Grafana panel | — | Panel query must include `"exemplar": true` flag for Grafana to request exemplar data from Mimir |
| HTTP span scope management | Java application | — | `HttpServerSpanFilter` must close scope AFTER `record()` — this ensures ExemplarFilter sees valid span context |

---

## Standard Stack

### Core (no new dependencies — all already on classpath)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.opentelemetry:opentelemetry-sdk-metrics` | `1.61.0` (BOM-managed) | `SdkMeterProvider.builder().setExemplarFilter(...)` | Already a dependency from Phase 4 |
| `io.opentelemetry.sdk.metrics.ExemplarFilter` | In `opentelemetry-sdk-metrics` | `traceBased()` factory method | In SDK 1.61.0 as `io.opentelemetry.sdk.metrics.ExemplarFilter` |
| `grafana/mimir:3.0.6` | Pinned (STACK-01/STACK-02) | Exemplar storage backend | Running; needs `limits.max_global_exemplars_per_user: 100000` in `mimir-config.yaml` |
| `otel/opentelemetry-collector-contrib:0.151.0` | Pinned (STACK-01/STACK-02) | Exemplar forwarding via PRW translator | Already forwards exemplars unconditionally — no config key needed |

**No new Maven dependencies for Phase 12.** [VERIFIED: codebase grep + Context7]

### Critical: Exact Import Statement

```java
import io.opentelemetry.sdk.metrics.ExemplarFilter;
```

Package: `io.opentelemetry.sdk.metrics` (NOT `io.opentelemetry.api.metrics`). [VERIFIED: Context7 /open-telemetry/opentelemetry-java APIDIFF 1.56.0]

### Version Verification

All packages already on the classpath via `opentelemetry-bom:1.61.0`. No `npm view` equivalent needed — Maven BOM pins these. [VERIFIED: producer/consumer pom.xml imports the BOM]

---

## Architecture Patterns

### System Architecture Diagram

```
Producer Service                    OTel Collector                 Mimir
┌─────────────────────────┐        ┌─────────────────┐           ┌──────────────────────┐
│  HttpServerSpanFilter   │        │  OTLP receiver  │           │  Ingester            │
│  ┌─────────────────────┐│        │                 │  OTLP     │  max_global_         │
│  │ span.makeCurrent()  ││        │  PRW translator │ ─────────>│  exemplars_per_user  │
│  │ chain.doFilter()    ││──────> │  getPromExemplars│           │  = 100000            │
│  │ requestDuration     ││  OTLP  │  populates      │           └──────────┬───────────┘
│  │   .record(seconds)  ││        │  ts.Exemplars[] │                      │
│  │ span.end()          ││        │  automatically  │           Prometheus query API
│  │ scope.close()       ││        └────────────────-┘           /prometheus/api/v1/
│  └─────────────────────┘│                                      query_exemplars
└─────────────────────────┘
         ↓ ExemplarFilter.traceBased()                           ┌──────────────────────┐
         attaches {trace_id, span_id} when                       │  Grafana             │
         Span.current() is valid at record() time                │  datasources.yaml    │
                                                                 │  exemplarTraceId     │
Consumer Service                                                 │  Destinations:       │
┌────────────────────────┐                                       │  trace_id → Tempo    │
│  OtelSdkConfiguration  │                                       └──────────┬───────────┘
│  buildMeterProvider()  │                                                  │
│  .setExemplarFilter(   │                                       ┌──────────▼───────────┐
│    ExemplarFilter       │                                       │  Tempo               │
│    .traceBased())       │                                       │  trace click-through │
└────────────────────────┘                                       └──────────────────────┘
(counter instruments only — no HTTP histogram, but filter is set
 for SDK correctness and as a teaching point: set it everywhere)
```

### Recommended Project Structure (no changes to existing structure)

This phase edits existing files only. No new directories or packages.

```
producer-service/src/main/java/com/example/producer/config/
├── OtelSdkConfiguration.java   ← add ExemplarFilter.traceBased() to buildMeterProvider()
└── HttpServerSpanFilter.java   ← restructure scope: manual try-finally (D-E1)

consumer-service/src/main/java/com/example/consumer/config/
└── OtelSdkConfiguration.java   ← add ExemplarFilter.traceBased() to buildMeterProvider()

infra/observability/
└── mimir-config.yaml           ← add limits.max_global_exemplars_per_user: 100000

grafana/dashboards/
└── ose-otel-demo.json          ← add "Exemplars (Phase 12)" open row with histogram panel

mise.toml                       ← add [tasks."verify:exemplars"] block
README.md                       ← add §12 section
```

---

## Critical Collector Finding

### `send_exemplars: true` does NOT exist in prometheusremotewrite v0.151.0

**Source:** Direct inspection of `exporter/prometheusremotewriteexporter/config.go` at tag `v0.151.0` in opentelemetry-collector-contrib. [VERIFIED: curl to raw GitHub at tag v0.151.0]

The `Config` struct for the PRW exporter has these fields:
- `Namespace`, `RemoteWriteQueue`, `ExternalLabels`, `MaxBatchSizeBytes`, `MaxBatchRequestParallelism`, `ResourceToTelemetrySettings`, `WAL`, `TargetInfo`, `DisableScopeInfo`, `AddMetricSuffixes`, `TranslationStrategy`, `SendMetadata`, `RemoteWriteProtoMsg`

**There is no `send_exemplars` field.** Adding it to otelcol-config.yaml will produce an "unknown field" warning or a config parse error depending on the Collector build.

**How exemplar forwarding actually works in PRW v1:**
`pkg/translator/prometheusremotewrite/helper.go::getPromExemplars[T exemplarType](pt T)` (line 340) is called unconditionally from `addExemplars()` (line 212 in `metrics_to_prw.go`) for every `pmetric.HistogramDataPoint`. When the OTLP data point carries exemplars (set by the SDK when `ExemplarFilter.traceBased()` is active and a span is in scope), they are translated to `prompb.Exemplar` structs and appended to the time series automatically. No config gate required.

**The real blocker is Mimir:** `MaxGlobalExemplarsPerUser` defaults to **0** per `pkg/util/validation/limits.go`:
```
f.IntVar(&l.MaxGlobalExemplarsPerUser, "ingester.max-global-exemplars-per-user", 0,
  "The maximum number of exemplars in memory, across the cluster.
   0 to disable exemplars ingestion.")
```
[VERIFIED: curl to raw GitHub grafana/mimir main branch]

**Fix:** Add a `limits:` block to `mimir-config.yaml`:
```yaml
limits:
  max_global_exemplars_per_user: 100000  # WHY: 0 = exemplar ingestion disabled (default); must be positive to store exemplars
```

**Live verification:** The live Mimir instance at `:9009` returned `{"status":"success","data":[]}` for a direct `GET /prometheus/api/v1/query_exemplars?query=http_server_request_duration_seconds_bucket&start=...&end=...`. Zero exemplars confirmed — pipeline not yet active. [VERIFIED: live curl against running ose-otel-mimir container]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Exemplar extraction from histogram | Custom metric wrapper | `ExemplarFilter.traceBased()` on `SdkMeterProvider` | SDK attaches trace context automatically when filter is set and span is in scope |
| Exemplar forwarding via PRW | Custom PRW serializer | Standard `prometheusremotewrite` exporter | `getPromExemplars()` translator already handles the OTLP → Prometheus exemplar translation |
| Exemplar storage quota | Custom ingest limiter | `mimir-config.yaml` `limits.max_global_exemplars_per_user` | Mimir's built-in limit mechanism; 100000 is the Grafana-recommended starting value |
| Trace click-through URL construction | Custom datalink expression | `exemplarTraceIdDestinations` in datasources.yaml | Grafana handles `trace_id` → Tempo routing automatically when provisioning is correct |

**Key insight:** The entire three-layer exemplar pipeline is configuration-only at the Collector and Grafana layers. The only code change is the one-line `.setExemplarFilter(ExemplarFilter.traceBased())` call per service, plus the scope-fix that makes the active span visible at measurement time.

---

## Common Pitfalls

### Pitfall 1: `send_exemplars: true` in otelcol-config.yaml causes config error

**What goes wrong:** The CONTEXT.md and project PITFALLS.md both reference `send_exemplars: true` as a required Collector config. Adding this key to `prometheusremotewrite/mimir:` in otelcol-config.yaml will produce an unknown-field warning or config parse failure at Collector startup because this field does not exist in v0.151.0's config schema.

**Why it happens:** The planning artifacts inherited this assumption from the original v2.0 research PITFALLS.md (F3-1, item 3) and FEATURES.md (Feature 3 table). The field may have existed in an earlier version, or the research conflated the Prometheus exporter's `enable_open_metrics` flag with PRW behavior.

**How to avoid:** Do NOT add `send_exemplars: true` to otelcol-config.yaml. Remove any such line if present. Exemplar forwarding is unconditional in the PRW v1 path. Verify with: `docker compose logs otel-collector | grep -i "exemplar\|unknown field"` after Collector restart.

**Warning signs:** Collector log line containing `unknown field "send_exemplars"` or config validation failure at startup.

---

### Pitfall 2: Mimir silently discards all exemplars (default `max_global_exemplars_per_user: 0`)

**What goes wrong:** Exemplars flow from SDK → Collector → Mimir with no errors, but `GET /prometheus/api/v1/query_exemplars` always returns `{"data":[]}`. No error logs — Mimir simply discards exemplars ingested when the limit is 0.

**Why it happens:** Mimir's `ingester.max-global-exemplars-per-user` defaults to 0 per the limits.go source. Zero means "disable exemplars ingestion". The mimir-config.yaml in this repo does not set this value.

**How to avoid:** Add to `mimir-config.yaml`:
```yaml
limits:
  max_global_exemplars_per_user: 100000
```
After docker compose restart, run `mise run verify:exemplars` — it will detect the empty response and fail with a clear message.

**Warning signs:** `verify:exemplars` reports zero exemplars even after generating load with `scripts/load.sh`.

---

### Pitfall 3: Exemplar context is invalid at `requestDuration.record()` time (F3-1 scope fix)

**What goes wrong:** `ExemplarFilter.traceBased()` requires `Span.current().getSpanContext().isValid()` to return true at the moment `requestDuration.record(seconds, attrs)` is called. The current `HttpServerSpanFilter.doFilterInternal()` uses try-with-resources:
```java
try (Scope scope = span.makeCurrent()) {
    chain.doFilter(request, response);
} catch (...) {
    ...
} finally {
    requestDuration.record(...);  // ← scope already closed here
    span.end();
}
```
The try-with-resources closes `scope` when the try block exits — BEFORE the finally block. So at `record()` time, the span is no longer the current span and `Span.current()` returns the noop span. The SDK sees `SpanContext.invalid()` and attaches no exemplar.

**Why it happens:** Java's try-with-resources calls `scope.close()` in the implicit `__suppress` cleanup, which happens before `finally`. This is a subtle ordering that affects exemplar attachment but not span correctness (span events and attributes set during the try block are preserved).

**How to avoid:** Restructure to manual scope management (D-E1):
```java
Scope scope = span.makeCurrent();
try {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus());
} catch (RuntimeException | ServletException | IOException e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    throw e;
} finally {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    requestDuration.record(seconds, Attributes.of(...));
    span.end();
    // close scope AFTER record() so ExemplarFilter.traceBased() sees active span
    scope.close();
}
```

**Warning signs:** `verify:exemplars` passes (Mimir has exemplars) but the exemplar's `trace_id` label value is all-zeros (`00000000000000000000000000000000`) — indicating the SDK attached an exemplar from an invalid span context.

---

### Pitfall 4: Dashboard exemplar dots not visible — missing `"exemplar": true` in panel query target

**What goes wrong:** Grafana shows the histogram panel with percentile lines but no exemplar dots. Mimir has exemplars stored and the datasource has `exemplarTraceIdDestinations` configured.

**Why it happens:** Grafana only requests exemplar data from the datasource when the panel query target has `"exemplar": true`. Without this field, Grafana treats the query as a standard range query and never calls the `/api/v1/query_exemplars` endpoint.

**How to avoid:** Each query target in the exemplar histogram panel must include:
```json
{
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "expr": "histogram_quantile(...)",
  "legendFormat": "...",
  "refId": "A",
  "exemplar": true
}
```
[VERIFIED: Grafana GitHub issue #72315 confirms the field name and placement]

**Warning signs:** Panel shows lines but no diamond/dot markers. Inspecting the panel JSON shows `"exemplar"` key absent from targets.

---

### Pitfall 5: Consumer's counter exemplars are invisible in charts (expected, not a bug)

**What goes wrong:** Attendees may be confused when the consumer service's `ExemplarFilter` is set but they see no exemplar dots on counter panels.

**Why it happens:** Grafana's exemplar UI renders dots prominently only on histogram panels (because exemplars appear on the bucket timeseries, which are visual). Counter exemplars exist in Mimir and are queryable via the API, but they don't render as chart dots in Grafana's standard UI.

**How to avoid:** This is the correct behavior — document it in the README as D-E11 directs. The teaching point: "set the filter everywhere for SDK correctness; histograms are the natural exemplar visualization surface."

---

## Code Examples

### ExemplarFilter — producer OtelSdkConfiguration.java

```java
// Source: Context7 /open-telemetry/opentelemetry-java APIDIFF 1.56.0
// Import: io.opentelemetry.sdk.metrics.ExemplarFilter
// Placed in buildMeterProvider() method of SdkMeterProvider.builder() chain:

return SdkMeterProvider.builder()
    .setResource(resource)
    .setExemplarFilter(ExemplarFilter.traceBased())   // Phase 12 — one line per service
    .registerMetricReader(metricReader)
    .build();
```

### HttpServerSpanFilter — manual scope restructure (D-E1)

```java
// Before (try-with-resources — scope closed before finally):
try (Scope scope = span.makeCurrent()) {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus());
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

// After (manual scope — scope.close() is LAST):
Scope scope = span.makeCurrent();
try {
    chain.doFilter(request, response);
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus());
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

### mimir-config.yaml — exemplar storage enablement

```yaml
# Add below the existing usage_stats block:
limits:
  max_global_exemplars_per_user: 100000  # WHY: 0 (default) = exemplar ingestion disabled;
                                          # 100000 is the Grafana-recommended starting value
                                          # (see grafana.com/docs/mimir/latest/manage/use-exemplars/store-exemplars/)
```

### otelcol-config.yaml — DO NOT add send_exemplars

```yaml
# CORRECT — no exemplar-specific config key needed:
prometheusremotewrite/mimir:
  endpoint: http://mimir:9009/api/v1/push
  tls:
    insecure: true
  # WHY: exemplar forwarding is unconditional in the PRW v1 translator
  # (pkg/translator/prometheusremotewrite/helper.go::getPromExemplars).
  # No send_exemplars key exists in collector-contrib v0.151.0's config schema.
```

### Dashboard JSON — exemplar panel query target structure

```json
{
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "expr": "histogram_quantile(0.50, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))",
  "legendFormat": "p50",
  "refId": "A",
  "exemplar": true
}
```

### Dashboard JSON — open row structure (D-E4/D-E6)

Current panel ID ceiling is 14 (Phase 11, "Traces dropped before decision"). The new row uses ID 15, panel IDs 16+.

The Tail Sampling collapsed row is at `gridPos.y = 21` (header) with panels at y=22..40. The new Exemplars row goes at y=41 (after the collapsed tail-sampling row):

```json
{
  "collapsed": false,
  "gridPos": { "h": 1, "w": 24, "x": 0, "y": 41 },
  "id": 15,
  "title": "Exemplars (Phase 12)",
  "type": "row",
  "panels": []
}
```

The histogram panel (ID 16) sits inside the row panels array at y=42, w=24 (full width, to maximize exemplar dot visibility):

```json
{
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "gridPos": { "h": 9, "w": 24, "x": 0, "y": 42 },
  "id": 16,
  "title": "HTTP Request Duration (with Exemplars)",
  "type": "timeseries",
  "options": { "exemplarLabel": "trace_id" },
  "targets": [
    { "datasource": { "type": "prometheus", "uid": "prometheus" }, "expr": "histogram_quantile(0.50, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))", "legendFormat": "p50", "refId": "A", "exemplar": true },
    { "datasource": { "type": "prometheus", "uid": "prometheus" }, "expr": "histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))", "legendFormat": "p95", "refId": "B", "exemplar": true },
    { "datasource": { "type": "prometheus", "uid": "prometheus" }, "expr": "histogram_quantile(0.99, sum by (le) (rate(http_server_request_duration_seconds_bucket[5m])))", "legendFormat": "p99", "refId": "C", "exemplar": true }
  ]
}
```

### verify:exemplars mise task — Mimir exemplar query API

```bash
# Mimir exposes the Prometheus-compatible exemplar API at:
# GET /prometheus/api/v1/query_exemplars?query=<promql>&start=<rfc3339>&end=<rfc3339>
# Response: {"status":"success","data":[]} = no exemplars yet
# Response: {"status":"success","data":[{"seriesLabels":{...},"exemplars":[{"labels":{"trace_id":"abc..."},...}]}]}

START=$(date -u -d '10 minutes ago' '+%Y-%m-%dT%H:%M:%SZ')
END=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
RESULT=$(curl -fsS "http://localhost:9009/prometheus/api/v1/query_exemplars?query=http_server_request_duration_seconds_bucket&start=${START}&end=${END}")
# Assert: .data array non-empty AND at least one exemplar has a trace_id label
if ! printf '%s' "$RESULT" | jq -e '.data | length > 0 and (.[0].exemplars | length > 0) and (.[0].exemplars[0].labels.trace_id != null)' >/dev/null 2>&1; then
  echo "ERROR: verify:exemplars — no exemplars with trace_id in Mimir"
  echo "Response: $RESULT"
  exit 1
fi
echo "verify:exemplars: exemplars with trace_id present in Mimir."
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ExemplarFilter.alwaysOff()` (or no call) | `.setExemplarFilter(ExemplarFilter.traceBased())` explicit | SDK 1.56.0 (interface stabilized) | Explicit is the workshop-correct teaching shape; default may change between versions |
| PRW exporter had experimental `send_exemplars` flag (if it ever existed) | PRW v1 translator forwards exemplars unconditionally; PRW v2 tracks exemplars written via response header parsing | v0.151.0 confirmed no config flag | No user action needed for Collector-side forwarding |
| Mimir < 2.x required explicit exemplar storage enablement | Mimir 3.0.x still requires `max_global_exemplars_per_user > 0` to ingest | Mimir 3.0.6 | mimir-config.yaml needs `limits:` block |
| Grafana `traceID` label (older PITFALLS.md F3-3 example used `traceID`) | Label name is `trace_id` (underscore) per OTel spec — `ExemplarTraceIDKey = "trace_id"` | OTel spec + constants.go | `datasources.yaml` correctly uses `name: trace_id` (not `traceID`). PITFALLS.md F3-3 shows `traceID` — this is a documentation artifact; the live code uses `trace_id`. |

**Deprecated/outdated:**
- `send_exemplars: true` in prometheusremotewrite exporter: Never existed or was removed before v0.151.0. Do not use.
- DESIGN.md statement "Exemplars from a histogram data point are ignored": Outdated. The DESIGN.md's histogram section predates exemplar support. Live source code (`helper.go`) shows `addExemplars()` is called for every histogram data point.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Grafana 13.0.1 renders `"exemplar": true` panel targets correctly (no regression from v10 issue #72315) | Code Examples — dashboard JSON | If regression: exemplar dots do not appear despite correct data in Mimir; mitigation: test against running Grafana at :3000 |
| A2 | `gridPos.y = 41` is available (no row appended between y=21 tail-sampling row header and end of dashboard by prior quick tasks) | Code Examples — dashboard JSON | If wrong: panels overlap; mitigation: planner reads current last y value from ose-otel-demo.json before assigning coordinates |
| A3 | Mimir 3.0.6 does not enforce a maximum value for `max_global_exemplars_per_user` in single-binary mode | mimir-config.yaml example | If wrong: Mimir startup warning; mitigation: use 10000 instead of 100000 |

---

## Open Questions

1. **Does adding `limits:` to mimir-config.yaml require a Mimir container restart, or is it hot-reloaded?**
   - What we know: Mimir supports runtime config reloading for some settings via `/-/reload`, but `limits.*` config changes typically require restart for the ingester to apply the new limit.
   - What's unclear: Whether `max_global_exemplars_per_user` is a hot-reloadable limit or requires container restart.
   - Recommendation: Plan for `docker compose restart mimir` as part of the task. The `verify:exemplars` task will serve as the acceptance gate.

2. **Does the F3-1 scope fix change the catch-block behavior for `STATUS_CODE` on error path?**
   - What we know: Current code sets `HTTP_RESPONSE_STATUS_CODE` inside the try block (line 174), not the catch block. In the manual-scope restructure, the attribute-set stays in the try block.
   - What's unclear: Whether the response status is correctly available when an exception is thrown vs when the chain returns normally.
   - Recommendation: In the restructured code, `span.setAttribute(HTTP_RESPONSE_STATUS_CODE, response.getStatus())` must remain in the try block (after `chain.doFilter`) as before. The histogram in the finally block reads `response.getStatus()` directly (not from the span), so it is unaffected by exception timing.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `grafana/mimir:3.0.6` | EXMP-02 exemplar storage | Yes | 3.0.6 (running) | — |
| `otel/opentelemetry-collector-contrib:0.151.0` | EXMP-02 exemplar forwarding | Yes | 0.151.0 (running) | — |
| `grafana/grafana:13.0.1` | EXMP-04 exemplar panel rendering | Yes | 13.0.1 (running) | — |
| `grafana/tempo:2.10.5` | EXMP-04 trace click-through | Yes | 2.10.5 (running) | — |
| `jq` | `verify:exemplars` task | Yes | 1.8.1 | — |
| `curl` | `verify:exemplars` task | Yes | 8.19.0 | — |

All infrastructure dependencies are live and healthy. No missing dependencies.

---

## Security Domain

Security enforcement is enabled (`security_enforcement: true` in config.json, level 1).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No new auth surface in this phase |
| V3 Session Management | No | No sessions touched |
| V4 Access Control | No | No authorization changes |
| V5 Input Validation | No | No new user inputs; `trace_id` is a 32-hex-char string from SDK, not user-controlled |
| V6 Cryptography | No | No cryptographic changes |

### Known Threat Patterns

| Pattern | STRIDE | Assessment |
|---------|--------|------------|
| Exemplar cardinality explosion | Tampering | Mitigated by design: SDK attaches only `trace_id` + `span_id` (F3-2 prevention) — ~60 bytes, well under 128-byte OpenMetrics limit. No business attributes. |
| Trace ID leakage via metrics API | Information Disclosure | Workshop context: Mimir has no auth (`multitenancy_enabled: false`). Production systems must restrict `/prometheus/api/v1/query_exemplars` to authorized users. Document in README as a production consideration. |

No high or critical security concerns for this phase. Exemplar data contains trace IDs — in a production context these are internal correlation IDs, not secrets, but access to Mimir's query API should be gated.

---

## Sources

### Primary (HIGH confidence)

- Context7 `/open-telemetry/opentelemetry-java` — APIDIFF 1.56.0 `ExemplarFilter` interface definition; `SdkMeterProviderBuilder.setExemplarFilter()` method signature; package `io.opentelemetry.sdk.metrics`
- `github.com/open-telemetry/opentelemetry-collector-contrib/exporter/prometheusremotewriteexporter/config.go` at tag v0.151.0 — direct source inspection confirming NO `send_exemplars` field in Config struct [VERIFIED]
- `github.com/open-telemetry/opentelemetry-collector-contrib/pkg/translator/prometheusremotewrite/helper.go` at main — `getPromExemplars()` and `addExemplars()` functions showing unconditional exemplar extraction [VERIFIED]
- `github.com/open-telemetry/opentelemetry-collector-contrib/pkg/translator/prometheus/constants.go` at v0.151.0 — `ExemplarTraceIDKey = "trace_id"`, `ExemplarSpanIDKey = "span_id"` [VERIFIED]
- `github.com/grafana/mimir/pkg/util/validation/limits.go` main branch — `max_global_exemplars_per_user` default = 0 [VERIFIED]
- Live Mimir at `localhost:9009` — `GET /prometheus/api/v1/query_exemplars` returns `{"status":"success","data":[]}` [VERIFIED: live curl]
- `.planning/phases/12-exemplars-metrics-to-trace-click-through/12-CONTEXT.md` — all locked decisions D-E1..D-E11
- `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java` — current try-with-resources pattern at lines 170-217 confirmed [VERIFIED: Read tool]
- `infra/observability/otelcol-config.yaml` — `prometheusremotewrite/mimir` block at lines 292-298 confirmed no existing exemplar key [VERIFIED: grep + Read tool]
- `grafana/datasources.yaml` — `exemplarTraceIdDestinations` at lines 50-56 confirmed pre-wired, `name: trace_id`, `datasourceUid: tempo` [VERIFIED: Read tool]
- `grafana/dashboards/ose-otel-demo.json` — panel IDs 1-14, tail-sampling row at y=21, last panel at y=31+h=9=40 [VERIFIED: Read tool]
- `infra/observability/mimir-config.yaml` — NO `limits:` block present [VERIFIED: Read tool]

### Secondary (MEDIUM confidence)

- Grafana GitHub issue #72315 — confirms `"exemplar": true` in panel target JSON object is the correct field name and location [WebFetch verified]
- `github.com/grafana/mimir/development/mimir-monolithic-mode/config/mimir.yaml` — shows `max_global_exemplars_per_user: 100000` as the recommended starting value [WebFetch verified]
- `grafana.com/docs/mimir/latest/manage/use-exemplars/store-exemplars/` — confirms `limits.max_global_exemplars_per_user` is the enablement key [WebFetch verified]

### Tertiary (LOW confidence — not relied upon for locked decisions)

- `.planning/research/PITFALLS.md` F3 section — documents F3-1/F3-2/F3-3 pitfalls; note that F3-1 item 3 (`send_exemplars: true`) and F3-3's `traceID` label name are superseded by verified source-code findings in this research
- `.planning/research/FEATURES.md` Feature 3 section — original exemplar rationale; `send_exemplars: true` reference superseded

---

## Metadata

**Confidence breakdown:**
- ExemplarFilter API (import, method name): HIGH — Context7 verified against SDK 1.56.0+ APIDIFF
- `send_exemplars` absence: HIGH — direct source inspection at v0.151.0 tag
- Mimir `max_global_exemplars_per_user` default = 0: HIGH — limits.go source + live query confirmation
- `trace_id` label name: HIGH — `constants.go` source inspection
- Dashboard `"exemplar": true` field: HIGH — GitHub issue confirmation + Grafana docs
- GridPos y coordinates: MEDIUM — derived from reading current JSON; verify at plan time

**Research date:** 2026-05-03
**Valid until:** 90 days (stable APIs; Mimir limits.go defaults are unlikely to change)
