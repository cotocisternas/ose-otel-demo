---
phase: 04-metrics
plan: "05"
subsystem: tooling + documentation
tags: [mise, readme, workshop-checkpoint, phase-exit-gate, metrics]
dependency_graph:
  requires: [04-01-SUMMARY, 04-02-SUMMARY, 04-03-SUMMARY, 04-04-SUMMARY]
  provides: [demo:order two-payload task, Step 4 README section, human-verify gate]
  affects: [mise.toml, README.md]
tech_stack:
  added: []
  patterns: [multi-line mise run block with set -e, verbatim README edit protocol]
key_files:
  modified:
    - mise.toml (demo:order task — two priority payloads)
    - README.md (Step 4: Metrics section + Current marker + What's NOT here yet)
decisions:
  - "T3 live verification deferred: stack not running during execution; documents the green criteria steps for human verification in T4"
  - "Tag step-04-metrics deferred to post-human-approval per WORK-01 git-safety convention"
metrics:
  duration: ~4 minutes
  completed_date: "2026-05-01T22:42:00Z"
  tasks_completed: 2 of 4 (T1+T2 automated; T3 documented-pending; T4 checkpoint gate)
  files_modified: 2
---

# Phase 04 Plan 05: mise + README + Tag (Exit Gate) Summary

One-liner: Updated mise.toml demo:order to alternate express/standard priority payloads (D-10) and added ## Step 4: Metrics README section with Counter/Histogram/ObservableGauge documentation (D-21); live verification pending stack startup; git tag deferred to post-human-approval.

## Tasks Executed

| Task | Name | Commit | Files | Status |
|------|------|--------|-------|--------|
| T1 | Update mise.toml demo:order (D-10) | b00681a | mise.toml | DONE |
| T2 | Update README.md (D-21) | ca52455 | README.md | DONE |
| T3 | Live verification | (none — stack not running) | — | PENDING |
| T4 | Human-verify gate + git tag | — | — | CHECKPOINT |

## T1: mise.toml demo:order Update

**Final shape of the `demo:order` block:**

```toml
[tasks."demo:order"]
description = "POST sample orders to the producer; expect 202. Phase 4: alternates priority=express and priority=standard so Mimir shows two orders_created_total series."
run = """
set -e
curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"WIDGET-1","quantity":3,"priority":"express"}' && echo
curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"WIDGET-2","quantity":1,"priority":"standard"}' && echo
"""
```

**Verification results:**
- TOML parses: PASS (`mise tasks ls` shows `demo:order` with updated description)
- priority=express payload: PASS
- priority=standard payload: PASS
- set -e discipline: PASS
- PRODUCER_PORT preserved: PASS
- 2 curl invocations: PASS
- All other tasks unchanged: PASS (preflight, infra:up, infra:down, verify:bom, dev:producer, dev:consumer)
- [env] block preserved (OTEL_EXPORTER_OTLP_ENDPOINT, PROTOCOL): PASS
- [tools] block preserved (corretto-17.0.13.11.1, maven 3.9.11): PASS

## T2: README.md Update

**Final README section structure (H2 headers in order):**

1. # OSE OTel Demo (H1)
2. ## Prerequisites
3. ### Required tools
4. ### Required free ports
5. ### IDE setup
6. ### One-time setup
7. ### First run
8. ## Workshop checkpoints  ← step-04-metrics now **Current.**
9. ## Step 4: Metrics  ← NEW SECTION (line 79)
10. ## Reading the code
11. ## Why is OtelSdkConfiguration.java duplicated?
12. ## Why is the propagation pair shared?
13. ## What's NOT here yet  ← "No log correlation (Phase 5)" updated

**Verification results (all PASS):**
- `## Step 4: Metrics` section present (count=1)
- Three instrument shapes named: LongCounter, DoubleHistogram, ObservableGauge
- Three instrument names: orders.created, http.server.request.duration, orders.queue.depth.estimate
- Seconds-not-millis trap called out (D-13)
- Cardinality lesson mentioned (D-10/D-14)
- OTel→Prometheus name mapping: orders_created_total, http_server_request_duration_seconds, "name mangling"
- Links to three Phase 4 source files: OrderService.java, HttpServerSpanFilter.java, QueueDepthGauge.java
- 10-second PeriodicMetricReader interval documented (METRIC-01)
- Current marker: step-03 count=0, step-04 count=1
- step-04-metrics expanded description, no "(Phase 4)" prefix
- What's NOT here yet: old combined bullet gone, "No log correlation (Phase 5)" present
- Section order: w=68 s=79 r=127 d=138 ps=142 n=156 (w<s<r<d<ps<n = correct)
- All 6 step tags present: step-01-baseline through step-06-tests
- Code fences balanced (even count)

## T3: Live Verification — PENDING (Stack Not Running)

The producer service was not reachable at `http://localhost:8080/actuator/health` during execution
(`curl` exit code 7 = connection refused). The live stack was not running at execution time.

**Live verification steps (for human to run after starting the stack):**

```sh
# 1. Start infrastructure and services
mise run infra:up
mise run dev    # parallel producer + consumer

# 2. Confirm both services healthy
curl -s http://localhost:8080/actuator/health | grep '"status":"UP"'
curl -s http://localhost:8081/actuator/health | grep '"status":"UP"'

# 3. Criterion 1 — orders_created_total with two labels
mise run demo:order
sleep 12
# In Grafana → Explore → Prometheus/Mimir: orders_created_total
# Expect: two series with order_priority="express" and order_priority="standard"

# 4. Criterion 2 — http_server_request_duration_seconds histogram
for i in $(seq 1 20); do
  curl -s -o /dev/null -X POST http://localhost:8080/orders \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"SKU-$i\",\"quantity\":1,\"priority\":\"express\"}"
  sleep 1
done
sleep 12
# In Grafana: http_server_request_duration_seconds_count, _sum, _bucket{le="+Inf"}
# Expect: non-zero counts; _bucket carries http_request_method + http_response_status_code labels

# 5. Criterion 3 — orders_queue_depth_estimate ticks every 10s
# Run query: orders_queue_depth_estimate
# Wait 12s, re-run: timestamp delta should be 8-15s (proves 10s PeriodicMetricReader)

# 6. Criterion 4 — clean tree + BOM invariant
mise run verify:bom && test -z "$(git status --porcelain)" && echo "ALL CRITERIA GREEN"
```

**Working tree status at T3 execution time:** CLEAN (verified via `git status --porcelain`)

## T4: Human-Verify Checkpoint (GATE — BLOCKING)

**Status:** AWAITING HUMAN VERIFICATION

The human-verify gate requires the workshop attendee to:
1. Confirm README renders correctly (Step 4 section, Current marker, What's NOT here yet)
2. Start the live stack and verify all 4 Phase 4 success criteria in Grafana/Mimir
3. Approve for git tag creation

**After approval, run:**
```sh
git tag -a step-04-metrics -m "Workshop checkpoint: Phase 4 — Metrics. SdkMeterProvider added to both services as a sibling pipeline next to the SdkTracerProvider; three OTel instrument shapes flow to Mimir.

Phase 4 extends each service's OtelSdkConfiguration.java by extracting Phase 2's inline tracer pipeline into private SdkTracerProvider buildTracerProvider(Resource) (verbatim lift-and-shift) and adding a sibling private SdkMeterProvider buildMeterProvider(Resource) that wires OtlpGrpcMetricExporter + PeriodicMetricReader.setInterval(Duration.ofSeconds(10)) (METRIC-01 — overrides OTel's 60s default). The opentelemetry-exporter-otlp artifact already on the classpath since Phase 2 ships span + metric + log exporters from a single jar — Phase 4 adds zero new pom dependencies. Resource is built once in the @Bean orchestrator and passed to both helpers, so traces and metrics share identical service.name / service.instance.id for cross-signal correlation in Grafana.

Three instrument shapes (METRIC-02..04):
  - orders.created (LongCounter, producer-side) — fires after each successful publish, INSIDE the existing INTERNAL span scope, with order.priority business attribute from the request payload (fallback 'standard'). Counter does NOT fire on the failure path (counter is orders.created, not orders.attempted; failure is visible via the trace's ERROR status).
  - http.server.request.duration (DoubleHistogram, producer-side) — recorded from inside the existing HttpServerSpanFilter finally block, in SECONDS (semconv 1.40.0; the seconds-not-millis trap is one of the most common OTel-porting mistakes). Attributes follow HTTP semconv: HTTP_REQUEST_METHOD + HTTP_RESPONSE_STATUS_CODE only — url.path intentionally excluded to keep cardinality bounded. SDK-default explicit bucket aggregation.
  - orders.queue.depth.estimate (ObservableLongGauge, consumer-side) — registered in a small @Component QueueDepthGauge that constructor-injects Meter; callback returns ThreadLocalRandom.current().nextInt(0, 50) on every 10s collection interval. Synthetic by spec (METRIC-04); a real implementation would poll the RabbitMQ Management API.

All four Phase 4 success criteria simultaneously green at this commit:
  1. orders_created_total visible in Mimir within 15s of POST /orders with both order_priority='express' and order_priority='standard' labels (mise demo:order alternates the two values per D-10).
  2. http_server_request_duration_seconds populated with count + sum + bucket histograms after ~30s of traffic; bucket carries http_request_method + http_response_status_code labels.
  3. orders_queue_depth_estimate produces a fresh sample every 10s (proves PeriodicMetricReader interval override per METRIC-01 — without this, the gauge would only refresh every 60s).
  4. step-04-metrics annotated tag exists on main; reproduces the green-Phase-4 state in a temp clone."
```

**DO NOT push automatically.** Run `git push origin step-04-metrics` when ready to publish.

## Deviations from Plan

### T3 Live Verification Deferred

**Rule:** Documentation deviation (not a bug — the stack is simply not running in the worktree execution environment)
**Found during:** T3
**Issue:** `curl http://localhost:8080/actuator/health` returned exit code 7 (connection refused); the Spring Boot producer service was not started during plan execution.
**Resolution:** Per objective instructions: "If `curl http://localhost:8080/actuator/health` fails, document the live verification steps in SUMMARY.md as 'pending — stack not running during execution' and proceed to T4 without blocking." Documented all four criterion verification steps above for the human reviewer.
**Impact:** T4 checkpoint presents the human verification steps to the user; tag creation deferred to after human approval.

## Known Stubs

None — mise.toml and README.md are complete documentation artifacts. No stubs or TODOs in the modified content.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced. The README adds intra-repo markdown links only (no new external URLs). The mise.toml update adds no new network-accessible surfaces beyond what Phase 1 established.

## Self-Check: PASSED

Files created/modified:
- mise.toml → EXISTS
- README.md → EXISTS
- .planning/phases/04-metrics/04-05-SUMMARY.md → EXISTS

Commits:
- b00681a: chore(04-05): update demo:order to send two priority payloads (D-10)
- ca52455: docs(04-05): add Step 4: Metrics section and update README checkpoints (D-21)
