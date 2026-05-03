---
phase: 11-tail-sampling-at-the-collector
reviewed: 2026-05-03T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - infra/observability/otelcol-config.yaml
  - docker-compose.yml
  - producer-service/src/main/java/com/example/producer/domain/OrderService.java
  - scripts/load.sh
  - mise.toml
  - grafana/dashboards/ose-otel-demo.json
  - README.md
findings:
  critical: 1
  warning: 4
  info: 3
  total: 8
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-05-03T00:00:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 11 adds a `tail_sampling` processor to the OTel Collector, a `WIDGET-SLOW` SKU branch in `OrderService.java`, a fourth slow-stream in `scripts/load.sh`, a `verify:tail-sampling` mise task, and a Grafana diagnostics dashboard row. The Collector configuration, Java branch, and dashboard JSON are correct and internally consistent. The critical and blocking issue is a `BURST_RPS` default value that contradicts its own comment and will flood workshop machines with ~150 rps burst load unexpectedly. Several documentation defects degrade the workshop experience for attendees following the README.

---

## Critical Issues

### CR-01: `BURST_RPS` default is 150 (active), comment says "disabled by default"

**File:** `scripts/load.sh:83-84`
**Issue:** The comment at line 83 reads `# Burst stream — disabled by default.` The very next line sets `BURST_RPS="${BURST_RPS:-150}"`. 150 is non-zero, so the `if [[ "$BURST_RPS" -gt 0 ]]; then` gate at line 205 is satisfied and the burst loop runs immediately when a workshop attendee executes `mise run load`. The burst loop fires a 60-second oha burst at 150 rps every 300 seconds on top of the 200 rps steady load — a 350 rps spike that the README never warns the attendee about. This directly contradicts the comment, the `--help` output for the task, and the script's own header comment (`BURST_RPS (0=off)`).

The header at lines 34–35 lists `BURST_RPS (0=off)` as an override variable, which reads as if 0 is the default. The conflict means any attendee who reads only the comment or the header (the most likely reading path) will be surprised by a burst storm they did not ask for. Under typical workshop hardware (laptop class), an unexpected 350 rps peak risks Tomcat thread starvation (max 200 threads), consumer queue saturation, and misleading tail-sampling metric spikes — all of which make the Phase 11 dashboards harder to read and may derail the workshop demo.

**Fix:** Either disable burst by default (the semantically correct fix given the comment and the `0=off` convention) or correct the comment and the header. Recommended:

```bash
# Burst stream — disabled by default (set BURST_RPS>0 to enable).
BURST_RPS="${BURST_RPS:-0}"
```

If burst at 150 rps is intentional as the default, update the header comment and the task description in `mise.toml` to reflect this.

---

## Warnings

### WR-01: README instruction `mise run load SLOW_RPS=2` does not pass the env var

**File:** `README.md:571, 597`
**Issue:** The Phase 11 workshop instruction in the Step 11 "Run" block reads:
```
mise run load SLOW_RPS=2
```
Mise does not accept positional `KEY=VALUE` arguments to shell tasks in this form. Running this command passes the literal string `SLOW_RPS=2` as a positional argument `$1` to `scripts/load.sh`. The script does not parse positional arguments — it uses `${SLOW_RPS:-2}`, so `SLOW_RPS` remains its default value of `2`. The command accidentally produces the correct result only because the default already matches the intended override. If an attendee tries `mise run load SLOW_RPS=5` (mentioned as the workshop-safe maximum in the javadoc), the slow stream still runs at 2 rps without any error.

Verified: `mise run load --help` explicitly states "This task does not accept any arguments." The correct mise syntax for passing environment variables to a task is `SLOW_RPS=2 mise run load` (shell env-prefix) or `mise run -e SLOW_RPS=2 load` (mise `-e` flag).

**Fix:**
```markdown
# In another terminal — the SLOW_RPS=2 stream drives the latency policy:
SLOW_RPS=2 mise run load
```

Update both occurrences (lines 571 and 597).

### WR-02: `verify:tail-sampling` Tier 2 (Tempo search) has no retry loop

**File:** `mise.toml:520-541`
**Issue:** Tier 1 of `verify:tail-sampling` has a 6-attempt × 5s retry loop (30s tolerance) to handle Collector cold-start. Tier 2 makes a single blocking `curl` to Tempo and exits with an error if the attribute search returns no results. Tier 2 depends on:

1. At least one trace having been sampled and ingested by Tempo (`decision_wait: 10s`).
2. The `recordpolicy` alpha gate having stamped the `tailsampling.composite_policy` attribute on spans.
3. Tempo's `/api/search` default time window (30 minutes) covering the sampled spans.

When the task is run immediately after `mise run infra:up` (as the README Step 11 "Run" block instructs — "Wait ~60s"), Tier 1 can pass (the Collector registers its metric before any traces are sampled), but Tier 2 will fail if no spans have been exported and ingested into Tempo yet. The single-shot curl will fail and exit 1, sending a misleading error that suggests a misconfiguration when the real cause is a timing race. A retry loop here mirrors the Tier 1 pattern and tolerates the `decision_wait` + Tempo ingestion lag.

**Fix:** Wrap the Tier 2 curl in the same retry pattern used by Tier 1:

```bash
# --- Tier 2: Tempo span attribute (recordpolicy feature gate) ---
TEMPO_SEARCH='http://localhost:3200/api/search?tags=tailsampling.composite_policy'
LAST_ERR=""
for i in $(seq 1 $ATTEMPTS); do
  TEMPO_OUT=$(curl -fsS "$TEMPO_SEARCH" 2>&1) || {
    LAST_ERR="curl :3200 failed: $TEMPO_OUT"
    [ "$i" -lt "$ATTEMPTS" ] && { echo "verify:tail-sampling tier-2 attempt $i/$ATTEMPTS — $LAST_ERR; retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
    echo "ERROR: verify:tail-sampling tier-2 timed out — $LAST_ERR"; exit 1
  }
  MISSING=""
  for sub in $EXPECTED_SUBS; do
    printf '%s' "$TEMPO_OUT" | grep -F "$sub" >/dev/null 2>&1 || MISSING="$MISSING $sub"
  done
  [ -z "$MISSING" ] && break
  [ "$i" -lt "$ATTEMPTS" ] && { echo "verify:tail-sampling tier-2 attempt $i/$ATTEMPTS — sub-policies not yet in Tempo ($MISSING); retrying in ${SLEEP_SECS}s..."; sleep $SLEEP_SECS; continue; }
  echo "ERROR: verify:tail-sampling tier-2 — sub-policy names missing:$MISSING"; exit 1
done
```

### WR-03: Phase 11 screenshots referenced in README do not exist

**File:** `README.md:592-593`
**Issue:** The Step 11 "What to look for" section embeds two images in a comparison table:

```html
<td><img src="docs/screenshots/step-11-tail-sampling-OFF.png" ...></td>
<td><img src="docs/screenshots/step-11-tail-sampling-ON.png" ...></td>
```

Neither file exists in `docs/screenshots/`. The directory contains only steps 01–06. Workshop attendees (and anyone reading the README on GitHub) will see broken image placeholders where the before/after comparison should be. This is the most visually prominent teaching moment in §11 — the before/after Tempo search count delta that proves the sampler is active.

The `11-06-SUMMARY.md` notes "screenshots deferred", confirming this is a known gap but it is shipped in the public README. The REVIEW.md from Phase 11 planning indicates the screenshots were planned for Phase 18.

**Fix:** Either:
- Add a TODO comment around the `<table>` block to prevent broken image rendering:
  ```html
  <!-- TODO(Phase 18): step-11-tail-sampling-OFF.png and -ON.png pending automated screenshot pipeline -->
  ```
  And remove or comment out the `<img>` tags until the images exist.
- OR capture the screenshots manually before shipping the README.

### WR-04: `mise.toml` load task description is stale — claims ~1 req/sec, actual default is ~205 rps

**File:** `mise.toml:154`
**Issue:** The task description reads:

```
description = "Continuous POST /orders load (~1 req/sec, ~50/50 express/standard). Run alongside `mise run dev`. Ctrl-C stops both child loaders."
```

The actual defaults in `scripts/load.sh` are `QUERY_PER_SECOND=100` for each of two steady streams (express + standard) = 200 rps, plus `IDEMPOTENT_RPS=5` = 5 rps, plus `SLOW_RPS=2` = 2 rps, plus (per CR-01) `BURST_RPS=150` bursts every 5 minutes. Even ignoring the burst issue, the baseline throughput is 207 rps, not ~1 rps. The README Step 1 and Step 9 sections also describe `mise run load` as generating "~1 req/sec" and "~1 req/sec for 60s" respectively (lines 96, 437), which are identically wrong.

An attendee who reads the description and expects ~1 rps for a gentle live-demo experience will instead hit their laptop with 200+ rps and immediately see Tomcat + RabbitMQ under real load — surprising, and potentially destabilizing if running on a constrained laptop alongside the observability stack.

**Fix:**
```toml
[tasks.load]
description = "Continuous POST /orders load (~200 rps steady + burst mode). Default: 100 rps express + 100 rps standard + 5 rps idempotent + 2 rps slow (Phase 11). Run alongside `mise run dev`. Ctrl-C stops all child processes."
run = "./scripts/load.sh"
```

Also update the `~1 req/sec` references on README lines 96 and 437.

---

## Info

### IN-01: `step-06-tests` is marked `**Current.**` but Phase 11 is the current checkpoint

**File:** `README.md:75`
**Issue:** The "Workshop checkpoints" section marks `step-06-tests` with `**Current.**`. The repository is at Phase 11 (`main` HEAD is `step-11-tail-sampling` per the Phase 11 checkpoint description at line 562). The six-step checkpoint list does not include steps 08, 09, 10, or 11, so the "Current." label points to an obsolete checkpoint four phases behind the actual current state.

**Fix:** Add the missing checkpoints and update the `**Current.**` marker:

```markdown
- `step-06-tests` — Cross-service Testcontainers IT proves the full instrumentation chain in CI.
- `step-08-db-cache` — CLIENT spans for Valkey (SET) and PostgreSQL (INSERT). Two-lens telemetry model.
- `step-09-dashboards` — Infrastructure exporter telemetry (RabbitMQ, Redis, Postgres exporters).
- `step-10-collector-decompose` — Production observability stack (5 containers replace lgtm).
- `step-11-tail-sampling` — Tail sampling at the Collector. **Current.**
```

### IN-02: Step 9 "Run" block contains a `docker exec ose-otel-lgtm` command that fails at Phase 10+ HEAD

**File:** `README.md:434`
**Issue:** The Step 9 "Run" block instructs attendees to run:

```bash
docker exec ose-otel-lgtm wget -qO- 'http://localhost:9090/api/v1/query?...'
```

The `ose-otel-lgtm` container was removed in Phase 10. At `main` HEAD (Phase 11), this command produces `Error: No such container: ose-otel-lgtm`. The Step 9 checkpoint description notes "Workshop is at main HEAD past the most recent step-NN tag" — meaning attendees are expected to run this at Phase 10+ HEAD, not at a pre-Phase-10 tag.

The equivalent query on the decomposed stack would use Mimir directly: `curl http://localhost:9009/prometheus/api/v1/query?query=up{job=~"rabbitmq|redis_exporter|postgres_exporter"}`.

**Fix:** Replace the stale `docker exec ose-otel-lgtm` line with the equivalent Mimir query documented in the Step 10 "look behind the curtain" section:

```bash
# Validate the new exporters are scrapable:
curl -sS http://localhost:15692/metrics | head -5
curl -s 'http://localhost:9009/prometheus/api/v1/query?query=up{job=~"rabbitmq|redis_exporter|postgres_exporter"}' | python3 -m json.tool
```

### IN-03: Dashboard "Sampling decision-loop latency" panel has an acknowledged metric name uncertainty

**File:** `grafana/dashboards/ose-otel-demo.json:472, 486-498`
**Issue:** The panel description for "Sampling decision-loop latency (p50/p95/p99)" contains:

> NOTE: the `_milliseconds` unit suffix may or may not be present per RESEARCH §3.2.1; if panel data is empty, drop the `_milliseconds` infix and reload.

The panel queries use `otelcol_processor_tail_sampling_sampling_decision_timer_latency_milliseconds_bucket`. If the Collector exposes this metric without the `_milliseconds` infix (e.g., `sampling_decision_timer_latency_bucket`), all three panel targets return no data and the panel is silently empty. The acknowledgment is in the description field, visible only when hovering over the `(i)` icon — workshop attendees who see an empty panel will likely assume the Collector is not generating decisions, not that the metric name changed.

This was verified in a follow-up commit (`4ab84ef`: "histogram-suffix verification PASS — _milliseconds suffix present"), so the suffix IS present at v0.151.0. However, the commit message indicates this was uncertain enough to require runtime verification, and the dashboard ships with a warning comment rather than a tested fallback.

**Fix:** If the `_milliseconds` suffix is confirmed present at v0.151.0 (per the verification commit), remove the hedged NOTE from the panel description and replace it with an unambiguous statement: "Verified present at collector-contrib v0.151.0. If upgrading to v0.152+, re-verify." This removes a false alarm from the workshop UX.

---

_Reviewed: 2026-05-03T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
