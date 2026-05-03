# Phase 11: Tail Sampling at the Collector — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 9 (7 EDIT + 2 CREATE)
**Analogs found:** 9 / 9 (100% — every file has a strong in-repo analog)

---

## File Classification

| New/Edited File | Role | Data Flow | Closest Analog | Match Quality | Lines |
|---|---|---|---|---|---|
| `infra/observability/otelcol-config.yaml` | infra-config (YAML processor block) | telemetry-routing (Collector pipeline) | Same file — existing `processors.batch:` and `processors.memory_limiter:` blocks (`infra/observability/otelcol-config.yaml:96-112`) | EXACT (same file, same processor-block shape) | adds ~70 lines (50 YAML + 20 comment) + edits line 180 forecast comment |
| `producer-service/.../OrderService.java` | domain code (Java service method) | request-response (HTTP handler → AMQP publish) | Same method — existing inline span template + Phase 4 metric block in `OrderService.place()` (`producer-service/.../OrderService.java:74-136`) | EXACT (same method, additive branch inside existing INTERNAL span scope) | adds ~14 lines code + ~7 lines class JavaDoc delta |
| `scripts/load.sh` | workshop tooling (bash load generator) | request-emitter (oha → producer HTTP) | Same file — existing IDEMPOTENT_RPS env-var (line 73-74) + steady oha streams (lines 118-136) + heartbeat (lines 191-201) | EXACT (same file, mirrors WIDGET-EXPRESS/STANDARD steady-stream shape because payload is fixed; rejects IDEMPOTENT curl-loop shape per RESEARCH §5.1 because no per-request templating needed) | adds ~25 lines |
| `mise.toml` | workshop tooling (mise task) | infra-orchestration (curl + grep + assertion gate) | Same file — `[tasks."verify:datasources"]` (lines 319-389) and `[tasks."verify:images"]` (lines 394-450) | EXACT (same file, `verify:*` family pattern: bash + curl + grep + non-zero exit on drift; retry loop mirrors `verify:datasources`) | adds ~90 lines (Route A two-tier task) |
| `grafana/dashboards/ose-otel-demo.json` | dashboard JSON (Grafana panels) | telemetry-consuming (PromQL → Mimir read path) | Same file — collapsed `Deeper-dive (post-workshop)` row (`grafana/dashboards/ose-otel-demo.json:221-373`) | EXACT (same file, collapsed-row idiom; same `gridPos` math; same `datasource.uid: prometheus` contract per Phase 10 D-01) | adds ~150-200 lines (1 row header + 4-5 panels) |
| `docker-compose.yml` | infra-orchestration (compose service `command:`) | infra-orchestration (Collector startup args) | Same file — existing `otel-collector` service `command:` array (line 109) — sibling shape: tempo/mimir/loki services (lines 155, 187, 210) all use a string-array `command:` | EXACT (same file, same field, additive arg into existing array) | edits 1 line (extends array) + adds ~3 lines comment |
| `README.md` | workshop documentation (markdown) | doc/narrative | Same file — `## Step 10: Stack Decomposition` (lines 457-548) — same six sub-section shape (What you'll learn / Checkpoint / Run / What to look for / Why it matters); paired-screenshot HTML `<table>` from Step 2/3 (lines 143-152) | EXACT (same file, same Phase-10 narrative depth per D-T11; same HTML `<table>` paired-screenshot shape per Phase 7 D-04) | adds ~100-150 lines (new §11) |
| `docs/screenshots/step-11-tail-sampling-OFF.png` | binary asset (PNG) | doc/narrative | `docs/screenshots/step-02-disconnected-traces.png` and `docs/screenshots/step-03-joined-trace.png` (paired before/after capture) | EXACT (same `step-NN-*` naming convention; same paired-screenshot intent; same manual-one-shot capture per D-T9 / D-13 lineage) | binary, manually captured BEFORE Phase 11 lands (Wave 0 sequencing constraint) |
| `docs/screenshots/step-11-tail-sampling-ON.png` | binary asset (PNG) | doc/narrative | Same as above (paired with OFF) | EXACT | binary, manually captured AFTER Phase 11 lands |

---

## Pattern Assignments

### 1. `infra/observability/otelcol-config.yaml` — add `tail_sampling` processor block + edit line-180 comment

**Analog:** Same file, existing `processors:` blocks (`memory_limiter:` lines 97-105, `batch:` lines 107-112) and existing `pipelines.traces:` block (lines 178-184).

**Imports / file-header pattern (lines 1-27):** Already established — file-level "WHY this file exists" + "Live-verified key shapes" preamble. Phase 11 changes the body; the preamble already covers the file's role.

**Processor-block shape pattern (lines 96-112 — `memory_limiter:` and `batch:` are the structural template):**

```yaml
processors:
  memory_limiter:
    # WHY: workshop-grade safety valve — stop accepting new spans/metrics/logs
    # when the Collector heap exceeds the soft limit. Not production-tuned;
    # production deployments would profile actual load and tune `limit_mib`
    # to ~80% of container memory. Phase 11 may revisit when tail_sampling
    # buffers entire traces in memory.
    check_interval: 1s
    limit_percentage: 75
    spike_limit_percentage: 15

  batch:
    # WHY: production-shape batching pipeline. timeout=5s + send_batch_size=512
    # are the canonical workshop defaults — small enough that attendees see
    # data within ~5-10s of POSTing, large enough to avoid pummeling the backends.
    timeout: 5s
    send_batch_size: 512
```

**Convention:** Each block carries a single `# WHY:` line (or short paragraph) above the keys explaining the choice — Phase 10 D-04 "teaching-grade YAML" idiom. Blocks are separated by a single blank line. ASCII-bar comment headers (`# ────────────────────────────────────────`) section the file (lines 28, 93, 114, 144, 153). The Phase 11 `tail_sampling:` block sits inside the existing `processors:` block (D-T1 places it between `memory_limiter` and `batch`). Per D-T4, Phase 11's block is denser (~25-line comment block above `composite:` per RESEARCH §2.2) — that density matches the file's existing per-block annotation tax.

**Pipeline insertion pattern (line 183):** existing line:
```yaml
      processors: [memory_limiter, batch]
```
becomes:
```yaml
      processors: [memory_limiter, tail_sampling, batch]
```

**Forecast-comment fix (lines 179-180 — current state to be REPLACED per D-T1):**

```yaml
    # WHY: traces — apps emit OTLP → memory_limiter → batch → Tempo via OTLP HTTP.
    # Phase 11 inserts tail_sampling between batch and the exporter.
```

Replacement text is verbatim in `11-RESEARCH.md` §1 (8-line block).

**Verbatim YAML to paste:** `11-RESEARCH.md` §2.2 (118 lines as printed; ~50 effective YAML lines + ~70 comment lines).

---

### 2. `producer-service/src/main/java/com/example/producer/domain/OrderService.java` — add WIDGET-SLOW SKU branch

**Analog:** Same file, `place()` method body (lines 74-136). The existing `try (Scope scope = span.makeCurrent())` block already contains a Phase 2 inline span + Phase 4 metric-recording branch (lines 93-123). The WIDGET-SLOW branch is a third additive section inside the same scope.

**Imports pattern (lines 1-19):** No new imports needed — `Thread.sleep` and `RuntimeException` are JDK; `Map` and `Optional` are already present.

**Class JavaDoc pattern (lines 21-46):** Existing convention is **phase-attribution paragraphs**. Verbatim excerpt of the Phase 4 phase-attribution paragraph (lines 30-46) showing the shape Phase 11 mirrors:

```java
 * <p><b>Phase 4 adds the {@code orders.created} {@link LongCounter}
 * (METRIC-02).</b> The counter increments ONCE per successful order,
 * AFTER {@code publisher.publish(...)} returns and BEFORE
 * {@code return orderId} — inside the existing INTERNAL span scope so
 * the trace and the metric are emitted from adjacent SDK calls. The
 * {@code catch (RuntimeException)} block does NOT fire the counter:
 * METRIC-02 is {@code orders.created}, not {@code orders.attempted}.
 * Failure is visible via the trace's ERROR status, not as a metric.
```

**Convention:** the Phase 11 JavaDoc paragraph (verbatim in `11-RESEARCH.md` §4.2 lines 831-838) follows the same `<p><b>Phase N adds ...</b>` opening, references decision IDs (`D-T5/D-T6`), cross-links to README §11, and explains the exact code-mechanic.

**Inline-comment pattern inside `place()` (lines 75-105):** Verbatim excerpt of the Phase 4 D-08/D-09 inline block showing the comment density Phase 11 inherits:

```java
            // ---- Phase 4 D-08 / D-09: orders.created Counter (METRIC-02) ----
            //
            // Fires AFTER publisher.publish returns successfully. Inside the
            // INTERNAL span scope so the trace and the metric increment are
            // emitted from adjacent SDK calls — workshop attendees read both
            // signals being produced in one spot.
            //
            // The catch block below does NOT fire the counter: METRIC-02 is
            // `orders.created`, not `orders.attempted`. Failures are visible
            // via the trace's ERROR status (recordException + setStatus(ERROR)
            // in the catch), not as a metric increment. ...
```

**Convention:** every additive block inside `place()` is bracketed by `// ---- Phase N D-XX: <one-line description> ----` followed by a multi-line JavaDoc-style rationale block. Phase 11's branch follows the identical shape (verbatim in `11-RESEARCH.md` §4.2 lines 797-822).

**Insertion site:** immediately after `String orderId = UUID.randomUUID().toString();` (current line 90) and BEFORE `publisher.publish(orderId, payload);` (current line 91). Rationale per RESEARCH §4.2: the publish's PRODUCER span must be parented under the slow INTERNAL span AND the publish itself must be delayed (so trace MAX-span duration includes the sleep).

**Try/catch pattern for `Thread.sleep`:** The existing `catch (RuntimeException e)` block at lines 126-132 records the exception on the span and rethrows. RESEARCH §4.2 establishes the wrap-and-rethrow-as-RuntimeException pattern so the existing catch is reused unchanged:

```java
            if ("WIDGET-SLOW".equals(payload.get("sku"))) {
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("WIDGET-SLOW sleep interrupted", ie);
                }
            }
```

**Convention:** matches Phase 2 D-03 catch shape (Phase 3 APP-04 reused it; Phase 11 does the same — the catch is a stable workshop interface).

---

### 3. `scripts/load.sh` — add SLOW_RPS env var + fourth oha stream + heartbeat extension

**Analog:** Same file. RESEARCH §5.1 establishes that the **steady oha streams** (lines 118-136) are the structural template for SLOW_RPS — NOT the IDEMPOTENT_RPS curl-loop block (lines 138-166) — because the SLOW payload is fixed (no per-request UUID needed). This corrects CONTEXT.md's claim that IDEMPOTENT_RPS is the template.

**Env-var declaration pattern (lines 73-74 — IDEMPOTENT_RPS):**

```bash
# Idempotency stream — set IDEMPOTENT_RPS=0 to disable.
IDEMPOTENT_RPS="${IDEMPOTENT_RPS:-5}"
```

**Convention:** one-line `# <stream-name> stream — set XXX_RPS=0 to disable.` comment + bash `XXX_RPS="${XXX_RPS:-N}"` parameter-expansion default. Phase 11 adds `SLOW_RPS="${SLOW_RPS:-2}"` immediately after IDEMPOTENT_RPS line.

**Steady oha stream pattern (lines 118-136 — express + standard):**

```bash
oha -z "${DURATION}" \
    -q "${QUERY_PER_SECOND}" \
    -c "${N_CONNECTIONS}" \
    -m POST \
    -T application/json \
    -d '{"sku":"WIDGET-EXPRESS","quantity":3,"priority":"express"}' \
    --no-tui \
    "${TARGET}" >/dev/null 2>&1 &
PID_EXPRESS=$!
```

**Convention:** 8-line oha invocation + `PID_<NAME>=$!` capture. SLOW stream uses identical shape with `-q "${SLOW_RPS}"`, `-c 1` (per RESEARCH §5.1 — slow requests sit on Tomcat thread ~1.5s each so connection-pool footprint stays minimal), and `-d '{"sku":"WIDGET-SLOW","quantity":1,"priority":"standard"}'`. Wrapped in `if [[ "$SLOW_RPS" -gt 0 ]]; then ... fi` per the SLOW_RPS=0-to-disable contract (mirrors burst stream lines 170-189).

**Cleanup trap pattern (lines 83-94):**

```bash
cleanup() {
  [[ -n "${PID_BURST_LOOP:-}" ]] && pkill -P "$PID_BURST_LOOP" 2>/dev/null || true
  [[ -n "${PID_IDEMPOTENT:-}" ]] && pkill -P "$PID_IDEMPOTENT" 2>/dev/null || true
  for pid in "${PID_EXPRESS:-}" "${PID_STANDARD:-}" "${PID_IDEMPOTENT:-}" "${PID_BURST_LOOP:-}" "${PID_HEARTBEAT:-}"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
```

**Convention:** add `${PID_SLOW:-}` to the for-loop PID list. No `pkill -P` needed (slow stream is a single oha process, not a subshell-wrapped loop).

**Heartbeat banner pattern (lines 96-113) — mirror the burst-stream banner block (lines 106-112):**

```bash
if [[ "$BURST_RPS" -gt 0 ]]; then
  echo "Burst stream:"
  echo "  priority=${BURST_PRIORITY} @ ${BURST_RPS} rps (c=${BURST_CONNECTIONS})"
  echo "  duration ${BURST_DURATION}, idle ${BURST_INTERVAL} between bursts"
else
  echo "Burst: disabled (set BURST_RPS>0 to enable)"
fi
```

**Convention:** if/else with stream descriptor + `disabled (set XXX_RPS>0 to enable)` fallback. Phase 11 adds an analogous block for SLOW_RPS (verbatim in RESEARCH §5.1 lines 860-865).

**Heartbeat printf pattern (lines 193-200):**

```bash
(
  start=$(date +%s)
  while sleep 30; do
    elapsed=$(( $(date +%s) - start ))
    printf '[load] alive — elapsed=%ds (express=%d, standard=%d)\n' \
      "$elapsed" "$PID_EXPRESS" "$PID_STANDARD"
  done
) &
```

**Convention:** extend the printf format string and arg list to include `slow=%d` and `${PID_SLOW:-0}` (verbatim in RESEARCH §5.1 lines 894-896).

**Insertion order (RESEARCH §5.1):** steady → SLOW → idempotent → burst. SLOW block lands between line 136 (`PID_STANDARD=$!`) and line 138 (idempotency stream comment header).

---

### 4. `mise.toml` — add `[tasks."verify:tail-sampling"]` block (Route A — two-tier)

**Analog:** Same file, `[tasks."verify:datasources"]` (lines 319-389) and `[tasks."verify:images"]` (lines 394-450). RESEARCH §3.4 confirms verify:datasources is the closest structural template — both use bash + curl + retry loop + diff/grep + non-zero exit on drift.

**Section-header pattern (lines 316-319 + 391-394):**

```toml
# ──────────────────────────────────────────────────────────────────
# Phase 10 — verify:datasources (D-03)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:datasources"]
description = "Phase 10 invariant: Grafana provisioned datasource UIDs match the dashboard JSON contract"
```

**Convention:** ASCII-bar comment header naming the phase + decision ID. Phase 11's block opens with:
```toml
# ──────────────────────────────────────────────────────────────────
# Phase 11 — verify:tail-sampling (D-T14)
# ──────────────────────────────────────────────────────────────────
[tasks."verify:tail-sampling"]
description = "Phase 11 invariant: tail_sampling processor is loaded AND emitting decisions for the composite envelope (Route A). Sub-policy names verified via Tempo span attribute (alpha recordpolicy gate)."
```

**Retry-loop pattern (lines 343-368 — verify:datasources):**

```bash
ATTEMPTS=6
SLEEP_SECS=5
LAST_ERR=""
for i in $(seq 1 $ATTEMPTS); do
  ACTUAL=$(curl -fsS http://localhost:3000/api/datasources 2>&1) || {
    LAST_ERR="curl failed: $ACTUAL"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "verify:datasources: attempt $i/$ATTEMPTS — Grafana not ready yet ($LAST_ERR); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:datasources timed out after $((ATTEMPTS * SLEEP_SECS))s — Grafana not reachable on :3000."
    echo "Last error: $LAST_ERR"
    echo "Run: mise run infra:up"
    exit 1
  }
  # ... validate body shape, break on success ...
done
```

**Convention:** 6 attempts × 5s = 30s tolerance for backend cold-start. RESEARCH §3.4 inherits this verbatim (Tier 1 self-metrics check uses the same shape; Tier 2 Tempo check is one-shot). Phase 11 endpoint = `http://localhost:8888/metrics` (Collector self-metrics) for Tier 1, `http://localhost:3200/api/search` (Tempo) for Tier 2.

**Diff-on-drift assertion pattern (lines 370-386 — verify:datasources):**

```bash
ACTUAL_UIDS=$(printf '%s\n' "$ACTUAL" | jq -r '.[].uid' | sort -u)

if ! diff <(printf '%s\n' "$EXPECTED") <(printf '%s\n' "$ACTUAL_UIDS") > /tmp/ds-diff 2>&1; then
  echo "ERROR: Grafana datasource UIDs drifted from the dashboard contract."
  echo "Expected (3 UIDs):"
  printf '  %s\n' "$EXPECTED"
  echo "Actual (provisioned by grafana/datasources.yaml):"
  printf '  %s\n' "$ACTUAL_UIDS"
  echo
  echo "Diff:"
  cat /tmp/ds-diff
  echo
  echo "If you renamed a datasource, the ose-otel-demo dashboard JSON must be updated to match (NOT recommended — keep the contract)."
  exit 1
fi
```

**Convention:** `EXPECTED` heredoc-string + `ACTUAL` extraction via `grep -oE | sort -u` + `diff <(...) <(...)` with full diagnostic dump on failure. Phase 11 Tier 1 uses a single grep-match-or-fail (RESEARCH §3.4 lines 665-677) instead of a full diff because Tier 1 only asserts a single composite-policy name; Tier 2 is per-name iteration with `MISSING` accumulator. Both shapes already exist in the file (`verify:images` lines 421-441 uses the iteration-and-accumulate approach via `FLOATING=$(... | grep -E ...)`).

**Insertion site:** AFTER the `[tasks."verify:images"]` block (after current line 450). Per RESEARCH §5.2 — no new env vars needed in `mise.toml` (`SLOW_RPS` is load.sh-local).

**Verbatim task body:** RESEARCH §3.4 Route A (lines 617-711, ~95 lines).

---

### 5. `grafana/dashboards/ose-otel-demo.json` — add new collapsed `Tail Sampling diagnostics` row

**Analog:** Same file, the existing collapsed `Deeper-dive (post-workshop)` row (`grafana/dashboards/ose-otel-demo.json:221-373`). This is the single in-repo example of a collapsed multi-panel row — exact structural template per CONTEXT.md `<code_context>` and RESEARCH §6.

**Row-shell pattern (lines 221-232 — `Deeper-dive (post-workshop)` row header):**

```json
{
  "collapsed": true,
  "gridPos": {
    "h": 1,
    "w": 24,
    "x": 0,
    "y": 10
  },
  "id": 5,
  "title": "Deeper-dive (post-workshop)",
  "type": "row",
  "panels": [
    /* nested panels here */
  ]
}
```

**Convention:** `collapsed: true`, `gridPos.h: 1` (header is 1 row tall), `gridPos.w: 24` (full width), `gridPos.x: 0`, `gridPos.y` placed below the previous row (existing row at `y: 0` with `h: 10` → next row at `y: 10`; Phase 11's row at `y: 21` per RESEARCH §6 because existing row's header `y: 10` + nested panels `y: 11` + `h: 9` = `y: 20`, +1 buffer = `y: 21`). `type: "row"`. `panels` array contains nested panel objects.

**Nested-panel pattern (lines 233-283 — `Orders Created by Priority`):**

```json
{
  "datasource": {
    "type": "prometheus",
    "uid": "prometheus"
  },
  "gridPos": {
    "h": 9,
    "w": 8,
    "x": 0,
    "y": 11
  },
  "id": 6,
  "title": "Orders Created by Priority",
  "type": "timeseries",
  "fieldConfig": { /* defaults: drawStyle line, lineInterpolation linear, lineWidth 1, fillOpacity 10, palette-classic */ },
  "options": {
    "legend": { "displayMode": "list", "placement": "bottom", "showLegend": true },
    "tooltip": { "mode": "single" }
  },
  "targets": [
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "expr": "sum by (order_priority) (rate(orders_created_total[1m]))",
      "legendFormat": "{{order_priority}}",
      "refId": "A"
    }
  ]
}
```

**Convention:** every panel carries `datasource.uid: "prometheus"` (or `"tempo"` / `"loki"`) — the Phase 10 D-01 contract; Phase 11 panels all use `prometheus` (Mimir-via-collector-self-metrics). `legendFormat: "{{policy}}"` mirrors the `{{order_priority}}` template-variable idiom (CONTEXT.md `<discretion>` confirms `{{policy}}` for Phase 11). `refId: "A"` per target. `gridPos.h: 9` for nested panels.

**Panel ID convention:** existing IDs are `1, 2, 3, 4` (top row), `5` (collapsed-row header), `6, 7, 8` (collapsed-row panels). Phase 11 adds row-header `id: 9` then nested panel ids `10, 11, 12, 13` (+ `14` if bonus panel from RESEARCH §3.3 added). Per RESEARCH §6 — verify no collisions.

**`description` field for D-T16 contract (D-T16 + RESEARCH §6):** the row-header `description:` carries the policy-names contract reminder verbatim:

```json
"description": "POLICY-NAMES CONTRACT (Phase 11 D-T16): the policy= labels referenced in this row's queries are a contract with infra/observability/otelcol-config.yaml's tail_sampling.policies block. Renaming any policy in the YAML requires updating these queries. Verified at runtime by `mise run verify:tail-sampling` (D-T14). Also depends on the alpha feature gate `processor.tailsamplingprocessor.recordpolicy` enabled on the otel-collector container (Route A)."
```

**Convention:** the existing top-row `RED Metrics` panel (lines 107-110) uses `description:` for a longer narrative — Phase 11 follows the same pattern but with the explicit contract callout.

**PromQL bodies:** verbatim from RESEARCH §3.3 (Route A variants). Panel layout per RESEARCH §6 row-skeleton (panels 1-2 in row 1 at `w: 12`; panels 3-4 in row 2 at `w: 8` + bonus panel 5 at `w: 8`).

**Additive-only constraint (CONTEXT.md `<integration_points>`):** zero edits to existing panels. The new row is appended to the `panels` array — sibling to the existing `Deeper-dive (post-workshop)` row.

---

### 6. `docker-compose.yml` — add `--feature-gates=processor.tailsamplingprocessor.recordpolicy` to `otel-collector` service `command:` array

**Analog:** Same file, existing `otel-collector` service `command:` line (`docker-compose.yml:109`):

```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.151.0
    container_name: ose-otel-collector
    command: ["--config=/etc/otelcol-contrib/config.yaml"]
```

Sibling shape — tempo/mimir/loki services all use single-element string-array `command:` (lines 155, 187, 210). Phase 11 extends the otel-collector array to two elements:

```yaml
    command: [
      "--config=/etc/otelcol-contrib/config.yaml",
      # WHY (Phase 11 / DISCUSSION-LOG Route A): alpha feature gate that enables
      # sub-policy attribution on sampled spans via the `tailsampling.composite_policy`
      # span attribute. Without this gate, composite envelope collapses sub-policy
      # names at the metric layer (only "composite-policy" appears as policy= label).
      # Gate is alpha at v0.151.0 — could rename or default-on in v0.152+; revisit
      # at the next collector-contrib bump. See `mise run verify:tail-sampling`
      # (Tier 2) for the runtime contract this gate satisfies.
      "--feature-gates=processor.tailsamplingprocessor.recordpolicy"
    ]
```

**Convention:** the file is heavily commented (`# WHY:` blocks throughout). The new array element gets its own `# WHY:` block flagging:
- The Phase 11 / Route A lineage
- The alpha-gate fragility
- The cross-link to `verify:tail-sampling` Tier 2

**Note:** This file edit is a Route A consequence not present in CONTEXT.md (CONTEXT.md `<integration_points>` line 176 says "docker-compose.yml UNTOUCHED" pre-Route-A confirmation). DISCUSSION-LOG.md Plan-time amendment (lines 235-256) authoritatively flips this — docker-compose.yml IS edited as part of Phase 11.

---

### 7. `README.md` — add §11 section (~100-150 lines)

**Analog:** Same file, `## Step 10: Stack Decomposition` (lines 457-548). Per D-T11, Phase 11 mirrors Step 10's shape and density verbatim.

**Section header + sub-section pattern (lines 457-484):**

```markdown
## Step 10: Stack Decomposition — from one container to five

### What you'll learn

- A **production observability** stack is not a single container ...
- Decomposing the all-in-one `grafana/otel-lgtm:0.26.0` ...
- ... [4-5 bullet points; one per major lesson]

### Checkpoint

Workshop is at `step-10-collector-decompose` — the orchestrator applies this annotated tag atomically with the phase-completion merge per WORK-01 / D-21. `git checkout step-10-collector-decompose` jumps to this point.

### Run

\`\`\`bash
mise run preflight
mise run infra:reset
mise run infra:up
mise run verify:images
mise run verify:datasources
mise run dev
mise run demo:order
sleep 15
\`\`\`

### What to look for
... [tables, code blocks, prose]

### Why it matters
... [pedagogical narrative paragraph]
```

**Convention:** five-sub-section shape. Phase 11 narrative depth = D-T11 ("Phase-10-equivalent depth"; RESEARCH §7). The `### Run` block mirrors Step 10's `mise run` callout pattern — Phase 11 adds `mise run load SLOW_RPS=2` and `mise run verify:tail-sampling` per CONTEXT.md decisions D-T7/D-T14.

**Phase-tag callout pattern (line 469):**

```markdown
Workshop is at `step-10-collector-decompose` — the orchestrator applies this annotated tag atomically with the phase-completion merge per WORK-01 / D-21. `git checkout step-10-collector-decompose` jumps to this point.
```

**Convention:** Phase 11's tag is `step-11-tail-sampling` per CONTEXT.md `<canonical_refs>` ROADMAP reference — lift the line verbatim with the tag substituted.

**Paired-screenshot HTML `<table>` pattern (lines 143-152 — Phase 7 D-04 precedent):**

```markdown
<table>
  <tr>
    <th align="center">Step 2 — broken (TWO disconnected traces)</th>
    <th align="center">Step 3 — fixed (ONE joined trace)</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/step-02-disconnected-traces.png" alt="Step 2 — TWO disconnected traces in Tempo for one POST"></td>
    <td><img src="docs/screenshots/step-03-joined-trace.png" alt="Step 3 — ONE joined trace in Tempo for one POST"></td>
  </tr>
</table>
```

**Convention:** two-column HTML `<table>` with `<th align="center">` headers labeling broken-vs-fixed (or OFF-vs-ON). Each `<td>` carries a single `<img>` with descriptive `alt` text. Phase 11's variant per RESEARCH §7 (lines 949-960):

```markdown
<table>
<tr>
<td><b>Pre-Phase-11 (OFF) — all traces reach Tempo</b></td>
<td><b>Post-Phase-11 (ON) — composite policy in effect</b></td>
</tr>
<tr>
<td><img src="docs/screenshots/step-11-tail-sampling-OFF.png" alt="Tempo Search showing N traces in last 5 min, no tail sampling"></td>
<td><img src="docs/screenshots/step-11-tail-sampling-ON.png"  alt="Tempo Search showing M traces in last 5 min — every error trace, every slow trace, ~20% of the rest"></td>
</tr>
</table>
```

**Workshop guardrails / production callout pattern (lines 534-546 — Step 10's blockquote idiom):**

```markdown
> **Workshop guardrails introduced in Step 10:**
>
> - `mise run verify:images` — fast-fails if `docker-compose.yml` contains a floating image tag ...
> - `mise run verify:datasources` — fast-fails if Grafana's provisioned datasource UIDs ...
```

**Convention:** `> **<bold heading>:**` blockquote with bullet list inside. D-T12's F2-3 head-vs-tail callout uses this exact shape at the END of §11 (per CONTEXT.md D-T12). Verbatim verbiage paraphrases ROADMAP.md Phase-16 callout per `<discretion>`.

**Insertion site:** new `## Step 11: Tail Sampling at the Collector` section between current `## Step 10: Stack Decomposition` (ends line 548) and `## Concepts & FAQ` (starts line 550).

---

### 8. `docs/screenshots/step-11-tail-sampling-OFF.png` (NEW, manually captured BEFORE Phase 11 lands)

**Analog:** `docs/screenshots/step-02-disconnected-traces.png` and `docs/screenshots/step-03-joined-trace.png` (existing paired before/after capture in `docs/screenshots/`).

**Naming convention:** `step-NN-<short-name>.png`. Existing files: `step-01-empty-tempo.png`, `step-02-disconnected-traces.png`, `step-03-joined-trace.png`, `step-03-waterfall.png`, `step-05-logs-trace-jump.png`, `step-06-test-output.png`. Phase 11 uses `step-11-tail-sampling-OFF.png` and `step-11-tail-sampling-ON.png` (D-T9).

**Capture mechanism:** manual one-shot per D-T9 / D-13 lineage. NO modification to `scripts/screenshots/capture.mjs` (per RESEARCH §7). Operator captures via Grafana UI manually:

- URL: `http://localhost:3000/explore` → Tempo datasource → Search tab → `Service Name = order-producer`, time range `Last 5 min`
- Capture: full Tempo Search results panel (trace count is the visible delta — ~1500 OFF vs ~600 ON)

**Sequencing constraint:** the OFF screenshot MUST be captured BEFORE Phase 11's first plan lands (per CONTEXT.md `<specifics>` bullet 4 — once `tail_sampling` is in the YAML, the OFF state is unreachable on `main`). Planner per RESEARCH §7 either makes this a Wave 0 task OR a separate quick-task that lands on `main` first.

---

### 9. `docs/screenshots/step-11-tail-sampling-ON.png` (NEW, manually captured AFTER Phase 11 lands)

**Analog:** Same as above (paired with OFF).

**Capture mechanism:** identical to OFF — same URL, same query, same time range. Captured AFTER all Phase 11 plans land on `main`. Operator-driven, manual one-shot.

**Sequencing constraint:** captured AFTER the Phase 11 PR merges — final commit on the Phase 11 PR can include this PNG, OR a follow-up commit on `main` lands it.

---

## Shared Patterns

### Shared Pattern A: D-04 Teaching-Grade `# WHY:` YAML Comment Density

**Source:** `infra/observability/otelcol-config.yaml` (every block in the file carries a `# WHY:` annotation — established by Phase 10 D-04).

**Apply to:** `infra/observability/otelcol-config.yaml` (the new `tail_sampling:` block per D-T4 — ~25-line comment block above `composite:` + per-branch sub-comments per sub-policy), `docker-compose.yml` (the new feature-gate array element per Route A — `# WHY:` block flagging alpha-gate fragility).

**Sample excerpt (otelcol-config.yaml lines 50-55):**
```yaml
        # WHY: rabbitmq_prometheus plugin exposes /metrics on :15692 (compose port);
        # /metrics/per-object yields per-queue/per-connection/per-channel labels
        # (preserved verbatim from grafana/prometheus.yaml).
        - job_name: rabbitmq
          metrics_path: /metrics/per-object
```

### Shared Pattern B: Phase-Attribution JavaDoc + Inline-Comment Headers

**Source:** `producer-service/.../OrderService.java` lines 30-46 (class JavaDoc paragraph: `<p><b>Phase N adds ...</b>`) AND line 93 (inline-comment header: `// ---- Phase 4 D-08 / D-09: orders.created Counter (METRIC-02) ----`). Same shape in `consumer-service/.../ProcessingService.java` lines 27-46 (consumer-side phase-attribution paragraphs).

**Apply to:** `producer-service/.../OrderService.java` (Phase 11 D-T5/D-T6 adds a class-JavaDoc paragraph + inline-comment header for the WIDGET-SLOW branch).

**Convention:** every additive code section gets:
1. A `<p><b>Phase N adds <X> (D-XX/decision-ID).</b>` paragraph in the class JavaDoc.
2. A `// ---- Phase N D-XX: <one-line summary> ----` header above the code block.
3. A multi-line rationale comment (~10-20 lines) explaining the WHY, with cross-links to README sections and adjacent SDK patterns.

### Shared Pattern C: `mise run verify:*` Family — Bash + Curl + Retry-Loop + Diff-on-Drift

**Source:** `mise.toml` `[tasks."verify:bom"]` (lines 263-306), `[tasks."verify:datasources"]` (lines 319-389), `[tasks."verify:images"]` (lines 394-450).

**Apply to:** `mise.toml` (Phase 11 D-T14 adds `[tasks."verify:tail-sampling"]` per RESEARCH §3.4).

**Pattern:**
- ASCII-bar comment header naming the phase + decision ID
- `description = "...invariant: <what is being asserted>"`
- `run = """ set -e ... """`
- For HTTP-dependent gates: `ATTEMPTS=6 / SLEEP_SECS=5` retry loop with informative error messages
- `EXPECTED` heredoc string + `ACTUAL` extraction via curl + grep/jq + sort -u
- `diff <(printf ...) <(printf ...)` or per-element grep iteration with `MISSING` accumulator
- Non-zero exit on drift with full diagnostic dump

### Shared Pattern D: Phase 10 D-01 Datasource UID Contract

**Source:** `grafana/dashboards/ose-otel-demo.json` — every panel hardcodes `"datasource": { "type": "prometheus", "uid": "prometheus" }` (or `"tempo"` / `"loki"`). Validated at runtime by `mise run verify:datasources`.

**Apply to:** `grafana/dashboards/ose-otel-demo.json` — every new Phase 11 panel uses `"uid": "prometheus"` (Mimir read path; Collector self-metrics arrive in Mimir via the IN-06 `otelcol` scrape job per RESEARCH §3.2 + Phase 10 commit 430131b).

### Shared Pattern E: Manual One-Shot Screenshot Pairing (D-13 Lineage)

**Source:** `docs/screenshots/step-02-disconnected-traces.png` + `docs/screenshots/step-03-joined-trace.png` (Phase 7 D-04 precedent — two paired PNGs showing a broken-then-fixed delta, rendered side-by-side in README via HTML `<table>`).

**Apply to:** `docs/screenshots/step-11-tail-sampling-OFF.png` + `docs/screenshots/step-11-tail-sampling-ON.png` (paired OFF/ON delta showing trace count drop from ~1500 to ~600 in Tempo Search). README `<table>` rendering per Shared Pattern F.

### Shared Pattern F: README Step-N Section Shape (Phase-10-Equivalent Density)

**Source:** `README.md` `## Step 10: Stack Decomposition` (lines 457-548). Five sub-sections: `### What you'll learn`, `### Checkpoint`, `### Run`, `### What to look for`, `### Why it matters`. Plus optional appendix-style blockquotes (`> **Workshop guardrails:**`, `> **Workshop-vs-production callouts:**`, `> **A note on infra:reset vs infra:down/up:**`).

**Apply to:** `README.md` Phase 11's new §11 section per D-T11. Six sub-sections + final F2-3 blockquote per D-T12.

### Shared Pattern G: Policy-Names Contract Across Three Artifacts

**Stable contract per D-T15:** `keep-errors`, `keep-slow`, `probabilistic-fallback` (composite_sub_policy `name:` values).

**Apply to:** Three files MUST agree verbatim — `infra/observability/otelcol-config.yaml` (the YAML composite_sub_policy `name:` values), `grafana/dashboards/ose-otel-demo.json` (referenced in panel descriptions + Tempo cross-links), and `mise.toml` (the `EXPECTED_SUBS='keep-errors keep-slow probabilistic-fallback'` literal in `verify:tail-sampling`). README §11 also quotes them verbatim per D-T11.

**Drift guard:** runtime gate via `mise run verify:tail-sampling` (Tier 2 Tempo span-attribute assertion under Route A) + dashboard JSDoc-style `description:` on the row header per D-T16.

---

## No Analog Found

None — every Phase 11 file has a strong in-repo analog. The tail_sampling YAML key shape itself is novel to the repo (no prior tail_sampling block exists), but the **block-shape pattern** (collector-contrib `processors:` block with `# WHY:` annotation density) is well-established by Phase 10's `memory_limiter:` and `batch:` blocks. The composite policy YAML keys themselves are sourced from upstream collector-contrib documentation per RESEARCH §2.1 + §2.2 (verbatim YAML in §2.2).

---

## Naming & Positioning Conventions Summary

| File | Convention | Source |
|---|---|---|
| `otelcol-config.yaml` | Each `processors.*:` block gets a `# WHY:` annotation; ASCII-bar comment headers section the file; pipeline order `[memory_limiter, tail_sampling, batch]` per D-T1 | Phase 10 D-04 |
| `OrderService.java` | Phase-attribution `<p><b>Phase N ...</b>` JavaDoc paragraphs; `// ---- Phase N D-XX: <summary> ----` inline-comment headers; existing inline-span template inside `place()` | Phase 2 D-01 + Phase 4 D-08/D-09 |
| `load.sh` | `XXX_RPS="${XXX_RPS:-N}"` env-var idiom; PID-capture into `PID_<NAME>` variables; cleanup trap covers all PIDs; heartbeat `printf` extends format string + arg list per stream | WORK-03 / Phase 7 D-04 |
| `mise.toml` | ASCII-bar comment header naming phase + decision ID; `verify:*` family naming; 6×5s retry loop; `diff <(...) <(...)` or grep-and-iterate drift detection | Phase 10 D-03 / D-14 |
| `ose-otel-demo.json` | Collapsed-row idiom (`collapsed: true`, `gridPos.h: 1`); panel IDs sequential without collision; `datasource.uid` hardcoded per Phase 10 D-01 contract; `legendFormat` uses `{{label}}` template variable; row-header `description:` carries D-T16 contract reminder | Phase 7 D-04 + Phase 10 D-01 |
| `docker-compose.yml` | String-array `command:` field; every additive arg gets a `# WHY:` block; pinned image tags only (validated by `verify:images`) | Phase 10 D-08 / D-14 |
| `README.md` | Five sub-section Step-N shape; phase-tag callout in `### Checkpoint`; HTML `<table>` for paired before/after screenshots; `> **<heading>:**` blockquotes for guardrails / production callouts | Phase 7 D-04 + Phase 10 D-21 |
| `docs/screenshots/step-NN-*.png` | Naming convention `step-NN-<short-name>.png`; manual one-shot capture (no automation modification per D-13); paired OFF/ON for ON/OFF demo deliveries | Phase 7 D-04 / Phase 10 D-13 |

---

## Metadata

**Analog search scope:** entire repo via direct Read of all 9 analog files; cross-checked against 11-CONTEXT.md `<canonical_refs>` and 11-RESEARCH.md §1-§7 for verbatim YAML/PromQL/bash bodies.

**Files scanned:** 9 (otelcol-config.yaml, OrderService.java, load.sh, mise.toml, ose-otel-demo.json, docker-compose.yml, README.md, ProcessingService.java, plus directory listing of docs/screenshots/) + 3 planning docs (CONTEXT.md, RESEARCH.md, DISCUSSION-LOG.md)

**Pattern extraction date:** 2026-05-02

---

## PATTERN MAPPING COMPLETE

All 9 Phase 11 files (7 EDIT + 2 CREATE) have an exact in-repo structural analog; planner can lift verbatim YAML/Java/bash/JSON/markdown bodies from RESEARCH §1-§7 and graft them onto the documented analog shapes — no new architecture, no novel patterns.
