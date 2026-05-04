---
phase: 13-log-based-metrics-loki-recording-rules
reviewed: 2026-05-03T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - grafana/dashboards/ose-otel-demo.json
  - infra/observability/loki-rules/fake/order-errors.yaml
  - mise.toml
  - README.md
findings:
  critical: 2
  warning: 4
  info: 2
  total: 8
status: issues_found
---

# Phase 13: Code Review Report

**Reviewed:** 2026-05-03T00:00:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Phase 13 adds a Loki recording rule that derives `log:order_errors:rate2m` from logs, a Grafana dashboard panel displaying the metric alongside the SDK counter, a `mise run verify:log-metrics` task, and a README walkthrough. The YAML structure, JSON validity, task retry logic, and most documentation are sound. One correctness bug will cause the recording rule to produce zero values at runtime and silently break the phase's primary teaching demonstration.

---

## Critical Issues

### CR-01: Recording rule uses `|= "ERROR"` — matches zero OTLP-ingested log lines, metric always 0

**File:** `infra/observability/loki-rules/fake/order-errors.yaml:31-36`

**Issue:** The LogQL expression filters log lines with `|= "ERROR"` (a substring match against the log body). When logs are shipped via the OTel Logback appender (`opentelemetry-logback-appender-1.0`), the formatted message body contains only the raw message string (e.g., `order processing failed: orderId=<uuid>`). The log severity is carried in the OTLP `severity_text` field, which Loki indexes as structured metadata — it does NOT appear in the body. This is explicitly documented in the same README at line 254: *"`severity_text="ERROR"` (not `|= "ERROR"`) — ... A substring filter against the message body returns zero results because the formatted body is just `order processing failed: orderId=<uuid>`."*

The result: `log:order_errors:rate2m` will be zero for all time regardless of error log volume. The `verify:log-metrics` tier-2 check (`'.data.result | length > 0'`) will always fail after the retry window because there is no data, making the phase unverifiable.

**Fix:**
```yaml
      - record: log:order_errors:rate2m
        expr: |
          sum by (service_name) (
            rate(
              {service_name=~"order-.+"} | severity_text = "ERROR" [2m]
            )
          )
```

### CR-02: README annotated recording rule example repeats the broken filter

**File:** `README.md:780-785`

**Issue:** The "Annotated recording rule" code block in Step 13's "What to look for" section shows `|= "ERROR"` — the same incorrect filter as the YAML file. Workshop attendees who copy this example when writing their own rules, or who compare it to the YAML file to understand the rule, will be taught the wrong filter. This compounds CR-01 and perpetuates the bug in any downstream copy.

**Fix:** Replace the code block's filter expression to match the corrected YAML:
```yaml
      - record: log:order_errors:rate2m
        expr: |
          sum by (service_name) (
            rate(
              {service_name=~"order-.+"} | severity_text = "ERROR" [2m]
            )
          )
```

---

## Warnings

### WR-01: README Step 11 references a "Traces in memory (sanity gauge)" panel that does not exist in the dashboard

**File:** `README.md:606`

**Issue:** Step 11's "What to look for" section lists five numbered panels in the "Tail Sampling diagnostics (Phase 11)" row. Bullet 5 reads: *"Traces in memory (sanity gauge) — should oscillate around `decision_wait × incoming_rps` ≈ 2000."* A second reference appears at line 614: *"The 'Traces in memory' panel is the sanity check."* The dashboard JSON contains exactly five panels in row 9 (IDs 10–14), but the fifth panel (ID 14) is titled "Traces dropped before decision (buffer pressure)" — a rate metric for data loss events, not a gauge for in-flight trace count. No panel for in-memory trace count exists anywhere in the dashboard JSON.

Workshop attendees who expand the row and count panels will find the fifth panel does not match the description, undermining confidence in the documentation.

**Fix:** Either (a) add the missing sanity-gauge panel to row 9 using `otelcol_processor_tail_sampling_sampling_traces_on_memory` (with a note that this metric counts decided traces in GC retention, not pending work), or (b) remove/correct bullet 5 and the line 614 reference to match the actual fifth panel ("Traces dropped before decision").

### WR-02: Row 17 dashboard description misrepresents the recording rule's aggregation

**File:** `grafana/dashboards/ose-otel-demo.json:605`

**Issue:** The `description` field of the "Log-Based Metrics (Phase 13)" row reads: *"The ruler evaluates `sum(rate({service_name=~\"order-.+\"} |= \"ERROR\" [2m]))` every 1m …"* This is inaccurate in two ways: (1) the actual expression is `sum by (service_name) (rate(...))` — a labelled aggregation, not a bare `sum()` that collapses all series into one; (2) it also repeats the incorrect `|= "ERROR"` filter from CR-01.

The bare `sum()` in the description conflicts with the `legendFormat: "Logs: errors ({{service_name}})"` on panel 18's refId B — attendees reading both the description and the legend format will see an apparent contradiction (where does `service_name` come from if the sum collapsed it?).

**Fix:**
```json
"description": "Phase 13 — Loki recording rules derive metrics FROM LOGS using LogQL. The ruler evaluates sum by (service_name) (rate({service_name=~\"order-.+\"} | severity_text = \"ERROR\" [2m])) every 1m and remote-writes the result to Mimir as log:order_errors:rate2m. No Java code changes — pure YAML. The panel below overlays this log-derived error rate against the SDK-emitted orders.created counter to demonstrate the equivalence (and divergence) of metrics-from-code vs metrics-from-logs."
```

### WR-03: `node = "lts"` in `mise.toml` is a floating version — violates workshop reproducibility contract

**File:** `mise.toml:12`

**Issue:** The comment at line 8 states: *"Pinned to exact patch for workshop reproducibility. Floating `corretto-17` would drift across cohorts."* Java is pinned to `corretto-17.0.13.11.1`, Maven to `3.9.11`, and `oha` to `1.14.0`. Node is pinned to `"lts"` — a floating alias that resolves to different major versions as LTS lines advance (e.g., currently resolves to Node 22 LTS; will silently jump to Node 24 LTS when that is promoted). The `screenshots` task runs `node scripts/capture-screenshots.mjs`; a Node major-version change could break the script between workshop cohorts. The `verify:images` task explicitly enforces exact patch pins for Docker images; the same principle applies to mise tools.

**Fix:**
```toml
node  = "22.15.0"   # pin to exact LTS patch for workshop reproducibility
```
Run `mise ls-remote node | grep "^22"` to confirm the latest 22.x patch.

### WR-04: README `jq` path for Loki `/loki/api/v1/rules` is wrong for Loki's namespace-scoped response format

**File:** `README.md:753`

**Issue:** Step 13's "What to look for" section shows:
```bash
curl -s http://localhost:3100/loki/api/v1/rules | jq '.data.groups[].rules[].record'
```
Loki 3.x's `/loki/api/v1/rules` endpoint returns a namespace-keyed map, not a flat `.data.groups[]` array. The actual response shape is:
```json
{"status":"success","data":{"fake":{"groups":[{"name":"order-error-rules","rules":[{"record":"log:order_errors:rate2m","expr":"..."}]}]}}}
```
The correct `jq` path for this structure is `.data.fake.groups[].rules[].record` (tenant name `fake` is the key). The path `.data.groups[]` does not exist; `jq` will return `null` or an error, misleading attendees into thinking the ruler did not load the rule.

The `verify:log-metrics` tier-1 check uses `grep -q "log:order_errors:rate2m"` which is format-agnostic and works correctly regardless of response structure — the bug is documentation-only but it is on the command attendees are explicitly directed to run for verification.

**Fix:**
```bash
curl -s http://localhost:3100/loki/api/v1/rules | jq '.data.fake.groups[].rules[].record'
# Expected: "log:order_errors:rate2m"
```

---

## Info

### IN-01: Recording rule comment overpromises — hardcodes `order-consumer` when both services match

**File:** `infra/observability/loki-rules/fake/order-errors.yaml:17`

**Issue:** The comment reads: *"Result metric: `log:order_errors:rate2m{service_name="order-consumer"}`"*. The LogQL expression filters `{service_name=~"order-.+"}` which matches both `order-producer` and `order-consumer`. If `order-producer` ever logs at ERROR level (e.g., if a future phase adds producer-side error handling), the metric will produce two series. The comment is technically incorrect and will confuse attendees whose metric returns a different label set than promised.

**Fix:**
```yaml
# Result metric: log:order_errors:rate2m{service_name=<service>}
# One series per service that logs at severity_text=ERROR.
# Under default workshop load the only ERROR-logging service is order-consumer
# (deterministic 10% failure path in ProcessingService).
```

### IN-02: README "Required free ports" table in Prerequisites is outdated (7 of 16 ports listed)

**File:** `README.md:28-36`

**Issue:** The Prerequisites section's "Required free ports" table lists 7 ports (`3000`, `4317`, `4318`, `5672`, `15672`, `8080`, `8081`) — the v1.0 single-container set. The current stack (Step 10+) uses 16 ports; the preflight task checks all 16, and Step 10's "What to look for" section includes a complete 14-entry table. A new attendee reading Prerequisites, who has a process on port `3100` (Loki) or `9009` (Mimir), will not be warned, and `mise run preflight` failure output may be surprising.

**Fix:** Update the Prerequisites "Required free ports" table to match the full list checked by the preflight task (or cross-reference the Step 10 port table explicitly).

---

_Reviewed: 2026-05-03T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
