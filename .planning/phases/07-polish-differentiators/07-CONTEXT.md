# Phase 7: Polish & Differentiators - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning (`/gsd-plan-phase 7` — no `/gsd-research-phase` required per ROADMAP.md "Phases that can plan directly" + SUMMARY.md "Research Flags")

<domain>
## Phase Boundary

Phase 7 turns the working six-step codebase into a delivery-ready workshop artifact. It adds **zero SDK code** to either service — every previous phase already wired the OTel mechanics this phase merely *exposes* to attendees through artifacts that surround the code.

The four deliverables map 1:1 onto REQUIREMENTS.md WORK-02 / WORK-03 / DOC-04 / DOC-01:

1. **WORK-02 — Auto-provisioned Grafana dashboard.** A `grafana/dashboards/ose-otel-demo.json` file mounted into `otel-lgtm` via a new docker-compose volume. Dashboard appears in Grafana automatically the moment the lgtm container is healthy — zero clicks for the workshop attendee. Two-row layout: top row = single-row demo strip (4 panels: Tempo trace search → Tempo service graph → Mimir RED metrics → Loki log panel filtered by trace_id) sized to fit a single projected screen; second row (collapsed by default) = deeper-dive panels (per-priority orders.created breakdown, error-status trace count from APP-04 failure path, raw log table for post-workshop poking). The single-row demo strip carries the live demo; the deeper-dive row is for after-workshop exploration.

2. **WORK-03 — Continuous-load script.** A `scripts/load.sh` that issues a steady stream of `POST /orders` requests so live demos have continuously-flowing telemetry without hand-clicking. Implementation: `oha` installed via mise + a small bash wrapper that launches **two parallel oha invocations** (one with `priority=express`, one with `priority=standard`, ~0.5 req/sec each = ~1 req/sec total) backgrounded with `&` and a SIGINT/SIGTERM trap that kills both children on Ctrl-C. Both priorities populate the dashboard's per-priority panel. Wrapped by a `mise run load` task for discoverability.

3. **DOC-01 — Full README walkthrough refactored to a uniform per-step template.** The existing README has rich author-voice bodies for Steps 4/5/6 only; Steps 1/2/3 appear merely as one-line bullets in the "Workshop checkpoints" header summary. Phase 7 **rewrites all six steps** to fit a single lean per-step template (5 sections: *What you'll learn* / *Checkpoint* / *Run* / *What to look for* / *Why it matters*) so the README reads like a textbook rather than six independently-authored sections. The existing standalone narrative sections at the end of the README (`Reading the code`, `Why is OtelSdkConfiguration.java duplicated?`, `Why is the propagation pair shared?`, `What's NOT here yet`) are preserved as a "Concepts & FAQ" appendix and cross-referenced from each step's *Why it matters* paragraph — no duplicate prose, no lost detail. Per DOC-01, every step's *Run* section MUST contain copy-pasteable curl/`mise run` commands.

4. **DOC-04 — Per-step screenshot set, scripted via Playwright.** A `scripts/capture-screenshots.sh` (or equivalent mise task) drives Grafana via headless Chromium (Playwright on Node) at each git tag in turn, captures one PNG per step plus the broken-vs-fixed pair, and commits the PNGs to `docs/screenshots/`. PNGs are git-tracked and embedded in each step's *What to look for* section. The DOC-04 anchor pair (step-02 = TWO disconnected traces in Tempo's trace search; step-03 = ONE joined trace in the same view) is the load-bearing visual; per-step shots cover step-01 (no telemetry / empty Tempo), step-04 (Mimir metrics for `orders_created_total{order_priority="express"}`), step-05 (Loki log line with `trace_id` MDC stamping, click-through to Tempo highlighted), step-06 (mvn verify output showing the random RabbitMQ port + four green tests). 6–8 PNGs total. Author runs `mise run docs:screenshots` once before completing Phase 7; CI does NOT re-run.

The pedagogical close: a workshop attendee who clones the repo on a fresh laptop runs `mise run preflight` → `mise run infra:up` → `mise run dev` → `mise run load`, opens Grafana on `:3000`, and sees the pre-provisioned dashboard with all three signals updating live; reads the refactored README from top to bottom understanding each step's lesson without running any code; and reaches the end of the README knowing exactly which `git checkout step-NN-*` to run for any moment they want to revisit.

**In scope (Phase 7 delivers):**
- New `grafana/dashboards/ose-otel-demo.json` (the dashboard) + new `grafana/dashboards/dashboards.yaml` (Grafana provisioning manifest pointing at the JSON path inside the container).
- `docker-compose.yml` modification: add volume mount for the `lgtm` service binding `./grafana/dashboards` to the otel-lgtm dashboard-provisioning path. Exact in-container path is research/planner detail (otel-lgtm's bundled Grafana provisioning convention; verify the read-only `:ro` flag is honored).
- New `scripts/load.sh` (executable, bash) with two parallel oha invocations + SIGINT/SIGTERM trap. New `mise run load` task in `mise.toml` invoking the script.
- New `mise.toml` tool entry pinning `oha` (or `hey` if `oha` lacks a mise plugin in 2026-05) — research flag for the planner: confirm mise can pin `oha` directly, otherwise add an explicit install task.
- New `scripts/capture-screenshots.sh` (or `docs:screenshots` mise task) driving Playwright through a fresh `mise run infra:up` + `mise run dev` + `mise run load` cycle at each tag (`step-01-baseline` through `step-06-tests`), capturing 6–8 PNGs deterministically.
- New Node runtime entry in `mise.toml` pinning a Node version (LTS) and a `package.json` (or equivalent) at `scripts/screenshots/` for the Playwright dependency. Scoped to the screenshot tooling — does NOT enter the Spring Boot toolchain.
- New `docs/screenshots/` directory containing the committed PNGs (one per step + the DOC-04 broken/fixed pair).
- README.md full rewrite: Steps 1/2/3 sections written from scratch in the new lean 5-section template; Steps 4/5/6 sections rewritten to fit the same template (preserving every fact, file reference, and pedagogical callout already there — see Decisions D-08 about the rewrite invariants); existing standalone narrative sections retained as a Concepts & FAQ appendix with cross-refs from each step's *Why it matters*; new top-of-README quick-start; new bottom-of-README close ("workshop is at main HEAD past step-06-tests; see Concepts appendix for deep dives").
- Light README additions: dashboard callout in Step 1 (it's running and empty), in Step 2 (traces start populating), Step 3 (joined trace is visible), Step 4 (metrics row populates), Step 5 (logs panel populates with `trace_id` filter); load-script callout in Step 1 (available as `mise run load` once apps are running); screenshot embeds in every step.
- README cross-references to all six existing CONTEXT.md files (the planner finalizes which step's *Why it matters* points where).

**Out of scope (deferred to v2, captured as Deferred Ideas):**
- Phase 7 git tag (decision: NO tag — main HEAD past `step-06-tests` IS the polish state per D-09).
- GitHub Actions / CI YAML for `mise run test` (the TEST-06 contract is "exits non-zero" — sufficient for any CI runner; CI YAML is not required for v1 ship).
- `docs/FACILITATOR.md` (FAC-01 v2 entry in REQUIREMENTS.md; only needed when someone other than the original author delivers the workshop).
- Sampling-variant checkpoint (`step-07-sampling-variant` / SAMP-01 in REQUIREMENTS.md v2).
- Baggage propagation checkpoint (`step-08-baggage` / PROP-V2-01 in REQUIREMENTS.md v2).
- DLX/retry checkpoint (`step-09-dlx-retry` / FAIL-01 in REQUIREMENTS.md v2).
- Per-vendor exporter swap demo (PROJECT.md flagged as "one-line OTLP endpoint change attendees can do themselves").
- Pyroscope / continuous profiling integration (FEATURES.md AF-20; v2 if a fourth-signal lesson is wanted).
- Performance / overhead benchmarking (PROJECT.md out-of-scope; AF-13).
- Multi-pattern AMQP topology (RPC, fanout, topic) — PROJECT.md out-of-scope; AF-5/AF-6.
- Custom histogram bucket boundaries / Views API tutorial (FEATURES.md AF-16).
- DF-5 (custom domain attributes + event), DF-6 (span exception recording — already shipped via Phase 3 APP-04 + TRACE-09), DF-7 (sampling variant checkpoint), DF-8 (baggage), DF-11 (dead-letter simulation) — all "ship if first cohort exposes a gap" per FEATURES.md.

</domain>

<decisions>
## Implementation Decisions

### Dashboard packaging (WORK-02)

- **D-01: Auto-provision the Grafana dashboard via a docker-compose volume mount into `otel-lgtm`.** Add a new directory `grafana/dashboards/` at repo root containing `ose-otel-demo.json` (the dashboard) plus `dashboards.yaml` (Grafana's dashboard-provisioning manifest pointing at the JSON file). `docker-compose.yml`'s `lgtm` service gains a `./grafana/dashboards:<otel-lgtm-provisioning-path>:ro` volume mount. Workshop attendees see the dashboard the moment the lgtm container is healthy — zero clicks, zero Grafana navigation. **NOT** a one-click `dashboards/ose-otel-demo.json` import (REQUIREMENTS.md WORK-02 allows EITHER, but auto-provision is workshop-time friction-free and the README still gets to teach the provisioning mechanism in 2-3 lines). **NOT** a "both auto + JSON also at known path" hybrid — adds README-explanation cost without changing what attendees experience. **Open detail — research/planner resolves:** the EXACT in-container path otel-lgtm's bundled Grafana looks at for dashboard provisioning. The grafana/docker-otel-lgtm v0.26.0 README documents this; planner reads the image's documentation or runs `docker exec -it ose-otel-lgtm ls /otel-lgtm/grafana/conf/provisioning/dashboards` (or wherever) to confirm the canonical path before writing the volume line.

- **D-02: Two-row dashboard layout — single-row demo strip on top, collapsed deeper-dive on bottom.** **Top row (always visible):** four panels left-to-right — (a) Tempo trace search panel showing the last 15 min of `service.name=order-producer` traces; (b) Tempo service graph (auto-derived from traces — no extra config because metrics-generator is enabled in otel-lgtm by default); (c) Mimir RED metrics combo (rate of `orders_created_total`, p50/p95/p99 of `http_server_request_duration_seconds`, gauge of `orders_queue_depth_estimate`); (d) Loki log panel filtered by `trace_id` from the most recent trace in panel (a) (uses Loki's `{service_name=~"order-.*"} |~ "trace_id=<…>"` query shape with a Grafana variable). **Second row (collapsed by default):** deeper-dive panels — per-priority `orders_created_total` breakdown (`sum by (order_priority) (rate(orders_created_total[1m]))`), error-status trace count (`tempo_spanmetrics_calls_total{status_code="STATUS_CODE_ERROR"}`), raw Loki log table for post-workshop poking. **NOT** a multi-row "three signals" mega-dashboard (more clicks during demos; harder to project on one screen). **NOT** a single-row only ("loses teaching surface for the per-priority Phase 4 lesson + the APP-04 failure-path Phase 3 lesson"). The single-row demo strip carries the workshop demo time; the collapsed deeper-dive row is there for the curious. **Open detail — planner resolves:** the EXACT panel queries (Mimir/Loki/Tempo query strings, variable definitions, time range defaults) belong in the dashboard JSON authoring step. The planner picks whether to author the JSON by hand or build it interactively in Grafana's UI and export — recommend the latter (build against a running demo with `mise run load` flowing, get layouts visually right, export, hand-tweak the JSON's `uid` + `title` + datasource UID references for portability).

### Load script (WORK-03)

- **D-03: `oha` (or `hey`) installed via mise + bash wrapper at `scripts/load.sh`.** mise pins the load-testing tool (preferred: `oha` for its TUI showing live RPS + p50/p95 latency — itself a tiny instrumentation lesson; fallback: `hey` if `oha` lacks a clean mise plugin in 2026-05). `scripts/load.sh` is an executable bash script that wraps the tool invocation. **NOT** pure curl + sleep loop (loses the live-RPS visualization which makes the load script itself a teaching moment). **NOT** a config-file-driven JMeter/Gatling/k6 harness (PROJECT.md AF-13 / out-of-scope; ~1 req/sec doesn't need a benchmarking harness). **Open detail — research/planner resolves:** confirm `oha` ships a mise plugin (mise.jdx.dev plugin registry as of 2026-05) OR pick `hey` as fallback. If neither has a mise plugin, the planner adds an explicit `[tasks.install:loadgen]` mise task that downloads the binary into `.mise/installs/` (vendor-bypass acceptable for a workshop tool). The chosen tool's exact CLI flags (`-z 0` infinite duration; `-q <rps>` requests-per-second; `--method POST`; `--body <json>`; `-T application/json`) get pasted into `scripts/load.sh`.

- **D-04: Two parallel `oha` invocations behind a SIGINT/SIGTERM trap, alternating express/standard priorities at ~0.5 req/sec each.** `scripts/load.sh` body sketch: declare `cleanup()` function → `kill $PID_EXPRESS $PID_STANDARD 2>/dev/null` + `wait`; `trap cleanup SIGINT SIGTERM EXIT`; `oha -z 0 -q 0.5 -m POST -d '{"priority":"express",...}' -T application/json http://localhost:8080/orders &; PID_EXPRESS=$!`; `oha -z 0 -q 0.5 -m POST -d '{"priority":"standard",...}' -T application/json http://localhost:8080/orders &; PID_STANDARD=$!`; `wait`. Net ~1 req/sec total split 50/50. Both panels in the dashboard's deeper-dive row (per-priority `orders_created_total` breakdown — D-02) populate visibly. **NOT** a single oha with one fixed payload (loses the per-priority dashboard panel). **NOT** a pre-generated payloads file with `oha --body-file` line cycling (works only on specific oha versions; harder to verify across mise's pinned version; trades one cleaner mechanism for one fragile one). **NOT** a bash arg-parser supporting `RPS=N` / `DURATION=N` env-var configuration (Phase 7 v1 ships the workshop default — 1 req/sec, infinite — and that's the FEATURES.md DF-2 contract; per-attendee tweakability is a v1.x ask). **Open detail — planner resolves:** the order JSON payload shape (Phase 1 `mise run demo:order` is the source of truth for what fields the producer accepts; planner copies that payload's schema, parameterizing only `priority`).

- **D-04.1: `mise run load` task wraps `scripts/load.sh` for discoverability.** `mise.toml` gains `[tasks.load] run = "./scripts/load.sh"`. README's Step 1 *Run* section lists it alongside `mise run dev` / `mise run demo:order`. Trivial to wire; carries the convention `mise run X` from Phase 1.

### Screenshots (DOC-04)

- **D-05: Scripted screenshot capture via Playwright (npm) + a Node runtime pinned in mise.** A new `scripts/screenshots/` directory contains `package.json` + `capture.mjs` (or `capture.ts` with `tsx`) using Playwright's `chromium.launch()` + `page.goto(...)` + `page.screenshot(...)` to drive Grafana headlessly. mise pins Node (LTS 22.x at writing) so attendees who want to re-capture screenshots get the right runtime automatically. A new mise task `mise run docs:screenshots` orchestrates: `git checkout step-NN-*` → `mise run infra:up` → `mise run dev` → `mise run load` (started, then killed after capture) → run `capture.mjs` for that step → `git checkout main` → repeat. **NOT** hand-captured + committed one-shot (REQUIREMENTS.md WORK-02 / DOC-04 don't mandate it but the user values reproducibility — Grafana UI shifts every otel-lgtm release; without a script the screenshots silently rot). **NOT** Chrome MCP (claude-in-chrome) — couples re-capture to having Claude Code running, less reproducible across team members. **Open detail — research/planner resolves:** (a) deterministic-enough timing so screenshots aren't flaky (Playwright's `waitForSelector` on Grafana panel-rendered DOM signatures + a fixed `--time-from`/`--time-to` URL parameter on the dashboard URL pins the time window — eliminates "screenshots taken at slightly different moments" drift); (b) auth — otel-lgtm's Grafana defaults to `admin/admin`; capture script POSTs the login form once then takes screenshots; (c) workspace isolation — capture script runs in a `git worktree` so the main checkout isn't disturbed (or it just reads-and-resets explicitly in the script); planner picks. The screenshot tooling is **scoped to `scripts/screenshots/`** — does NOT enter the Spring Boot Maven build, does NOT live in any service's `pom.xml`.

- **D-06: Per-step screenshot set — 6–8 PNGs total, one per step + the DOC-04 broken/fixed anchor pair.** Captures: (1) **`step-01-empty-tempo.png`** — Tempo trace search showing zero traces (proves Phase 1 baseline emits no telemetry — an inversion-lesson screenshot); (2) **`step-02-disconnected-traces.png`** — Tempo trace search showing TWO separate trace IDs for one logical request (the "broken" half of DOC-04); (3) **`step-03-joined-trace.png`** — Tempo trace search showing ONE trace ID for one logical request (the "fixed" half of DOC-04); (4) **`step-03-waterfall.png`** — Tempo waterfall view of the joined trace showing producer span → AMQP boundary → consumer span with the `parentSpanId == producer.spanId` relationship visible (the "you can SEE the propagation" payoff); (5) **`step-04-metrics.png`** — Mimir Explore showing `orders_created_total{order_priority="express"}` rate and `http_server_request_duration_seconds` p50/p95/p99 (proves Phase 4's three instrument shapes); (6) **`step-05-logs-trace-jump.png`** — Loki log panel showing a log line with `trace_id=...` MDC, with the click-through-to-Tempo lens icon highlighted (LOG-05 visualization); (7) **`step-06-test-output.png`** — `mvn verify` terminal output showing the random RabbitMQ port + four green Failsafe tests (TEST-01 SC #2 is "random port visible in test logs"). The DOC-04 broken/fixed pair (#2 + #3) renders side-by-side in README Step 2's *What to look for* and Step 3's *What to look for* sections — that's the load-bearing visual. **NOT** minimum DOC-04 only (loses Phase 4/5/6 visual coverage; FEATURES.md DF-3 explicitly recommends per-step). **NOT** richer than 8 (diminishing pedagogical return; capture-script complexity scales linearly). **Open detail — planner resolves:** whether `step-03-waterfall.png` is worth the extra capture (waterfall views are timing-sensitive and prone to flakiness in Playwright); fall back to 6 PNGs if the waterfall capture proves too brittle.

### README walkthrough (DOC-01)

- **D-07: Full refactor of all six step sections to fit a uniform per-step template.** Steps 4/5/6 have rich author-voice prose bodies today; Steps 1/2/3 only appear in the one-line bullet summary at README:68. Phase 7 rewrites the README's middle so all six steps fit a single template (D-08), making the artifact read like a textbook rather than six independently-authored sections. **NOT** "fill 1/2/3 only, leave 4/5/6 alone" (preserves the existing 4/5/6 prose at the cost of overall readability — six sections in three voices). **NOT** "hybrid: fill 1/2/3 + add a TL;DR strip to all six" (interim solution — leaves the underlying voice inconsistency). **CRITICAL invariants for the rewrite (the planner MUST preserve these — they are pedagogically load-bearing facts the existing 4/5/6 prose carries):**
  - Step 4: 10-second `PeriodicMetricReader` interval (METRIC-01 — overrides 60s default), seconds-not-millis histogram unit, `order.priority` non-semconv attribute key contrast, OTel→Prometheus name mangling (`orders.created` → `orders_created_total`).
  - Step 5: PITFALL #5 / `f5c331a` `OpenTelemetryAppender.install(...)` ordering fix; `appender.v1_0` vs `mdc.v1_0` package collision callout; "Production-readiness callout: do not log untrusted payload fields" subsection MUST stay (security-relevant).
  - Step 6: `<classifier>` Maven trickery callout; two `SpringApplicationBuilder` contexts in one JVM rationale; `TestOtelHolder` static-singleton resolution of D-07 from `06-CONTEXT.md`; SimpleSpanProcessor-not-Batch test determinism lesson; commit `f5c331a` cross-reference.
  - Across all steps: per-service `OtelSdkConfiguration.java` duplication is intentional (DOC-05 / Phase 2 D-01); the propagation pair in `otel-bootstrap` is shared on purpose (PROP-04). These two callouts must NOT be lost.
  - Step 1 (currently absent): Prerequisites section already exists at README:35-57 (DOC-02 covered in Phase 1) — Phase 7 cross-references it, doesn't duplicate.
  - Step 2 (currently absent): the broken-trace screenshot embed (D-06 capture #2) lands here.
  - Step 3 (currently absent): the joined-trace screenshot embed + waterfall embed (D-06 captures #3 + #4) land here. The DOC-04 broken/fixed pair MUST be visually side-by-side (markdown's two-column rendering varies by renderer; planner picks an HTML `<table>` or aligned image rows that work on GitHub-flavored markdown).
  - The existing `## Reading the code`, `## Why is OtelSdkConfiguration.java duplicated?`, `## Why is the propagation pair shared?`, `## What's NOT here yet` sections at the bottom of the README MUST be preserved as a "Concepts & FAQ" appendix (D-08) — every per-step *Why it matters* paragraph cross-references the relevant appendix entry.

- **D-08: Lean 5-section per-step template + standalone Concepts & FAQ appendix.** Each Step N section in the rewritten middle of the README contains EXACTLY these five subsections in this order: (1) **What you'll learn** — 1–2 sentences; (2) **Checkpoint** — annotated git tag name + `git checkout step-NN-* ` one-liner + 1-line "this is what's new since step-(N−1)"; (3) **Run** — copy-pasteable `mise run` / `curl` commands attendees actually paste during the workshop (DOC-01 mandate); (4) **What to look for** — specific Grafana queries / Mimir series names / Loki filters + the screenshot embed for that step; (5) **Why it matters** — 1 paragraph pedagogical close, including a cross-reference to the relevant Concepts & FAQ appendix entry where applicable. The standalone Concepts & FAQ appendix (preserving the existing "Reading the code" / "Why is OtelSdkConfiguration.java duplicated?" / "Why is the propagation pair shared?" / "What's NOT here yet" sections) sits at the bottom of the README. **NOT** a 7-section template adding "Code touched" + "Common questions" per-step (duplicates the appendix; bloats per-step prose). **NOT** the lean 5-section without the appendix (loses the deep-dive prose; some readers want to skip the per-step walkthrough and read the concept narrative directly). The appendix's standalone existence preserves a second reading mode — top-to-bottom step walkthrough OR Concepts-first conceptual read.

### Exit gate

- **D-09: NO Phase 7 git tag — main HEAD past `step-06-tests` IS the polish state.** ROADMAP.md line 5 says "plus the polish state" — *state*, not *tag*. WORK-01 explicitly mandates only `step-01` through `step-06` as annotated tags. Steps 1–6 each correspond to a discrete OTel SDK lesson; Phase 7 is artifact polish, not a new lesson — adding `step-07-polish` would imply a SDK-shaped checkpoint that doesn't exist. **NOT** `step-07-polish` (parallel naming preserves convention but invents a workshop step that has no `git checkout` payoff — there's no "checkout the version BEFORE polish to see what's new", because the polish layer touches README + dashboard JSON + load script + screenshot PNGs, not application code). **NOT** `v1.0` semantic-version tag (sets up the v2 distinction nicely but breaks the `step-NN-*` naming symmetry on `git tag` output; v1.0 can be applied later as a milestone-completion artifact via `/gsd-complete-milestone`, not as a phase exit gate). **NOT** "both" (explanatory cost > naming benefit). The README's final paragraph closes with: "Workshop is at main HEAD past step-06-tests; dashboard, load script, and full walkthrough are here. To revisit any step, `git checkout step-NN-*`." STATE.md / ROADMAP.md flips at Phase 7 completion are atomic-with-the-polish-merge commit — same pattern as Phases 1–6 minus the tag-apply step.

### Claude's Discretion

- **Exact in-container path for otel-lgtm dashboard provisioning** (D-01). Researcher / planner resolves against grafana/docker-otel-lgtm v0.26.0 README + `docker exec` introspection.
- **`oha` mise plugin availability in 2026-05** (D-03). Researcher / planner picks `oha` if a clean mise plugin exists; falls back to `hey` (or a vendor-bypass install task) if not.
- **Exact dashboard panel queries** (D-02). Planner authors the JSON interactively in Grafana's UI against a running demo, exports, hand-tweaks portability fields (`uid`, `title`, datasource UID references).
- **Playwright auth + timing strategy** for capture-screenshots (D-05). Planner picks the deterministic-timing primitives (`waitForSelector`, fixed time-range URL params, login-once-then-cycle).
- **Whether to capture `step-03-waterfall.png`** (D-06 capture #4). Planner picks based on Playwright timing reliability for waterfall views; fallback is 6 PNGs.
- **README appendix cross-reference style** (D-08). Planner picks the link format ("see [Why is OtelSdkConfiguration.java duplicated?](#why-is-otelsdkconfigurationjava-duplicated) in Concepts & FAQ" vs a numbered footnote style).
- **README quick-start placement** at top of file (D-07). Planner picks whether the rewritten README opens with a 5-line "clone this; run `mise run preflight` + `mise run infra:up` + `mise run dev` + `mise run load`; open Grafana on :3000" quick-start before the Prerequisites section, or whether Prerequisites is THE quick-start.
- **Test-only screenshot regression** (D-05). Planner decides whether `mise run docs:screenshots` is a manual one-shot or a CI-checkable contract. Recommendation: manual one-shot (CI re-running Playwright at every PR is overkill for a workshop repo).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap

- `.planning/REQUIREMENTS.md` — WORK-02 (auto-provisioned dashboard OR one-click import; D-01 picks auto-provision); WORK-03 (steady stream of POST /orders for live demos); DOC-01 (README walkthrough keyed to step-01 through step-06 tags with copy-pasteable curl); DOC-04 (screenshots pairing step-02 broken vs step-03 fixed); DOC-02 already shipped in Phase 1 (Prerequisites section — Phase 7 cross-references, does not duplicate).
- `.planning/ROADMAP.md` §"Phase 7: Polish & Differentiators" — all 4 success criteria (auto-provisioned dashboard at known path; load.sh produces continuously-flowing telemetry; README readable end-to-end without running code; every step has a paired README block with copy-pasteable curl). Line 5 mentions "plus the polish state" — D-09 reads this as *state*, not *tag*.
- `.planning/PROJECT.md` — overall constraint set (Spring Boot 3.4.13, Java 17, no Java agent, no Micrometer bridge, no autoconfigure starter, single OTLP endpoint, otel-lgtm backend, workshop runs offline / on laptop). PROJECT.md key-decisions table — per-service `OtelSdkConfiguration.java` duplication is intentional (production rule; do NOT extract in README rewrite).
- `.planning/STATE.md` — Phase 6 completion + tag `step-06-tests` already shipped; Phase 7 starts from main HEAD. STATE.md line 107 (Phase 5 bean-cycle history) is referenced in the Step 5 README rewrite invariants (D-07).
- `.planning/research/FEATURES.md` §"Differentiators" — DF-1 (dashboard, single-row layout default), DF-2 (load script, pure bash + curl default — D-03 diverges to oha for the live-RPS lesson), DF-3 (per-step screenshots — D-06 honors), DF-10 (comment density on SDK setup — already shipped in Phases 2/4/5; not a Phase 7 deliverable but its preservation is a Step 1/2 README invariant).
- `.planning/research/SUMMARY.md` §"Research Flags" — Phase 7 explicitly listed as "can plan directly (no research-phase needed)". Confirms Phase 7 routes straight from `/gsd-discuss-phase` → `/gsd-plan-phase`.

### Carryforward decisions from prior phases (REQUIRED reading before README rewrite)

**The README rewrite (D-07/D-08) MUST preserve every pedagogically-load-bearing fact in these CONTEXT.md files. The planner reads each file to extract the facts each step's prose currently carries:**

- `.planning/phases/01-baseline-scaffold/01-CONTEXT.md` — Phase 1 baseline state (no telemetry, infra running). Step 1 README rewrite synthesizes from here.
- `.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md` — Phase 2 D-01 (per-service `OtelSdkConfiguration` duplication is intentional production rule); D-12 (`System.getenv` + `Optional.ofNullable(...).orElse(...)` for OTLP endpoint); D-15 (`@Bean(destroyMethod="close")` lifecycle); DOC-03 (heavy comments on OtelSdkConfiguration); DOC-05 (deliberate-duplication callout). The "broken propagation visible" framing for Step 2 in the README rewrite synthesizes from here.
- `.planning/phases/03-amqp-context-propagation/03-CONTEXT.md` — Phase 3 PROP-01..PROP-04 (TracingMessagePostProcessor + TracingMessageListenerAdvice; the `MessagePostProcessor.setBeforePublishPostProcessors` injection point + `SimpleRabbitListenerContainerFactory.setAdviceChain` extraction point); APP-04 + TRACE-09 (deterministic 10th-order failure + recordException + setStatus(ERROR)). Step 3 README rewrite (the headline lesson) synthesizes from here. PROP-04 — propagation pair is shared on purpose (the "Why is the propagation pair shared?" appendix entry).
- `.planning/phases/04-metrics/04-CONTEXT.md` — Phase 4 D-08 (counter does NOT increment on failure path); METRIC-01 PeriodicMetricReader 10s interval; METRIC-02 `orders.created` Counter shape with `order.priority` business attribute; METRIC-03 `http.server.request.duration` Histogram in seconds; METRIC-04 `orders.queue.depth.estimate` ObservableGauge; OTel→Prometheus name mangling (dots-to-underscores + `_total` suffix on monotonic counters). Step 4 README rewrite invariants (D-07).
- `.planning/phases/05-logs-correlation/05-CONTEXT.md` — Phase 5 D-08/D-09 (PITFALL #5 / `OpenTelemetryAppender.install(...)` ordering fix in commit `f5c331a`); D-15/D-16 (`OrderController.LOG.info` + `OrderPublisher.LOG.info` + the LOG.error from the failure path); appender.v1_0 vs mdc.v1_0 package-collision callout; "Production-readiness callout: do not log untrusted payload fields" subsection (security-relevant — MUST stay in Step 5 rewrite).
- `.planning/phases/06-verification-tests/06-CONTEXT.md` — Phase 6 D-04 (`<classifier>` Maven mechanism); D-07.1 (TestOtelHolder static-singleton); D-09 (test-side replication of f5c331a); D-13 (Awaitility polling, NO Thread.sleep); D-14 (four `@Test` methods covering traces, logs, metrics, APP-04 failure path); D-17 (triple-signal correlation in failure-path test). Step 6 README rewrite invariants (D-07).
- **`f5c331a`** — Phase 5 fix commit moving `OpenTelemetryAppender.install(...)` into the `@Bean` factory body. Step 5 README rewrite cross-references this commit so attendees can `git show f5c331a` for the bug-fix narrative.

### Files Phase 7 modifies / creates (read first to plan diffs)

- **`README.md`** — full middle-section rewrite (D-07/D-08); existing Steps 4/5/6 prose used as input but rewritten to fit the lean 5-section template; existing Prerequisites + standalone narrative sections preserved; new Steps 1/2/3 written from scratch. Single biggest diff in the phase.
- **`docker-compose.yml`** — add volume mount for `lgtm` service binding `./grafana/dashboards` to otel-lgtm's dashboard-provisioning path (D-01). Existing healthcheck/volume/ports config UNCHANGED.
- **`grafana/dashboards/ose-otel-demo.json`** (NEW) — dashboard JSON, two-row layout (D-02). Authored interactively in Grafana UI against a running demo, exported, hand-tweaked for portability.
- **`grafana/dashboards/dashboards.yaml`** (NEW) — Grafana provisioning manifest pointing at the JSON file's in-container path.
- **`scripts/load.sh`** (NEW, executable) — two parallel oha invocations + SIGINT/SIGTERM trap (D-04).
- **`scripts/screenshots/package.json`** (NEW) — Playwright + minimal deps for the screenshot capture script (D-05). Pinned to a specific Playwright + Chromium version for reproducibility.
- **`scripts/screenshots/capture.mjs`** (NEW) — Playwright capture script driving Grafana headlessly across all six git tags.
- **`docs/screenshots/*.png`** (NEW, 6–8 PNGs) — committed screenshot set (D-06).
- **`mise.toml`** — add `[tools]` entries: `oha` (or `hey` fallback) for the load script (D-03); `node` LTS for the screenshot tooling (D-05). Add `[tasks]` entries: `[tasks.load] run = "./scripts/load.sh"` (D-04.1); `[tasks."docs:screenshots"] run = "..."` (D-05). Existing tasks (`preflight`, `infra:up`, `infra:down`, `infra:reset`, `infra:logs`, `build`, `test`, `dev:producer`, `dev:consumer`, `dev`, `demo:order`, `verify:bom`, `ui:grafana`) UNCHANGED.

### Tooling references (read first to plan)

- [grafana/docker-otel-lgtm v0.26.0 README](https://github.com/grafana/docker-otel-lgtm/blob/main/README.md) — bundled Grafana version, dashboard-provisioning path, datasource pre-wiring, port mappings. Source of truth for D-01's in-container provisioning path.
- [Grafana provisioning docs (dashboards)](https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards) — `dashboards.yaml` manifest schema; `path:` field pointing at the in-container directory containing JSON files.
- [oha GitHub](https://github.com/hatoo/oha) — load-testing tool; CLI flags (`-z`, `-q`, `-m`, `-d`, `-T`); confirms 2026-05 mise plugin availability.
- [Playwright docs](https://playwright.dev/docs/intro) — `chromium.launch()`, `page.goto()`, `page.screenshot()`, `waitForSelector()` for deterministic timing.
- [otel-lgtm metrics-generator config](https://github.com/grafana/docker-otel-lgtm/blob/main/docker-otel-lgtm/configs/tempo.yaml) — confirms service-graph metrics-generation is enabled by default in v0.26.0 (no extra config needed for D-02 panel b).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`docker-compose.yml`** (`/home/coto/dev/demo/ose-otel-demo/docker-compose.yml`) — already declares the `lgtm` service with `lgtm-data:/data` volume; D-01 adds a sibling `./grafana/dashboards:<path>:ro` mount. The existing structure (services + volumes block) is the integration point.
- **`mise.toml`** — already declares `[tools]` block (Corretto 17 + Maven) + `[tasks.*]` for `preflight`, `infra:*`, `dev:*`, `dev`, `demo:order`, `build`, `test`, `verify:bom`, `ui:grafana`. D-03 adds `oha`/`hey` + `node` to `[tools]`; D-04.1 + D-05 add `load` + `docs:screenshots` tasks. Existing entries UNCHANGED.
- **`README.md`** — existing structure: Prerequisites (DOC-02, lines 35–57); Workshop checkpoints summary (lines 68–77); Step 4 / Step 5 / Step 6 rich prose bodies (lines 79+); standalone narrative sections at end ("Reading the code", "Why is OtelSdkConfiguration.java duplicated?", "Why is the propagation pair shared?", "What's NOT here yet"). The middle (Steps 1/2/3 bodies) is missing — Phase 7 fills + refactors. The header + Prerequisites + standalone narrative tail are preserved (D-08).
- **`mise run demo:order`** task (`mise.toml`) — existing single-shot order POST that alternates priority=express/standard. D-04 reuses its payload schema in `scripts/load.sh`.
- **All six existing CONTEXT.md files** (`.planning/phases/0[1-6]-*/0[1-6]-CONTEXT.md`) — input to the README rewrite; each contains the per-step decisions and prose anchors the planner extracts to author Step N's *Why it matters* paragraph.

### Established Patterns

- **`mise run X` is the public command surface** (Phase 1 INFRA-02..INFRA-05). Phase 7's load script + screenshot script are wrapped by mise tasks (`mise run load`, `mise run docs:screenshots`) for consistency. Standalone bash scripts at `scripts/*.sh` are the implementation; mise tasks are the discoverability layer.
- **Pinned image tags for reproducibility, no floating tags** (Phase 1 + docker-compose comments — `rabbitmq:4.3-management`, `grafana/otel-lgtm:0.26.0`). The dashboard JSON authored against `0.26.0` is portable to that exact tag — when the workshop bumps lgtm versions in v2, the dashboard JSON gets re-authored against the new bundled Grafana.
- **Annotated git tag at exit gate** (Phases 1–6 / WORK-01). Phase 7 INTENTIONALLY breaks this convention (D-09) — it is the only phase that does not get its own tag because its deliverables are artifact polish, not a SDK lesson.
- **Heavy comment density on lesson-bearing source files** (DOC-03 / Phases 2/4/5 ≥40 comment lines). `scripts/load.sh` and `scripts/screenshots/capture.mjs` are NOT lesson-bearing source files — they are plumbing for the lesson; comment density bar does NOT apply (a 10-line bash script does not need 40 lines of comments). The dashboard JSON likewise; comments live in the README's Step 1 / Step 4 *Why it matters* paragraphs.
- **Single source-of-truth ordering for setup steps**: `mise run preflight` → `mise run infra:up` → `mise run dev` (Phase 1). Phase 7 extends with `mise run load` (after dev is up) and `mise run docs:screenshots` (one-shot, manual, internal).

### Integration Points

- **otel-lgtm bundled Grafana dashboard provisioning** — the v0.26.0 image's `/etc/grafana/provisioning/dashboards/` (or equivalent — confirmed by planner) reads any `dashboards.yaml` manifest and the JSON files it points at. D-01's volume mount supplies both.
- **Playwright + headless Chromium** — runs against Grafana on `localhost:3000`; uses `admin/admin` (otel-lgtm default per docker-compose `GF_SECURITY_ADMIN_*` env vars). No external network needed (workshop is offline per PROJECT.md).
- **`scripts/load.sh` HTTP target** — `localhost:8080/orders` (matches Phase 1 producer port + `mise run demo:order`'s URL).
- **README screenshot embeds** — relative-path markdown image syntax `![alt](docs/screenshots/step-NN-*.png)` works on GitHub, on `cat README.md`-style local rendering, and in any markdown previewer; no asset hosting required.
- **No new Maven module / pom.xml entry** — Phase 7 changes ZERO Java source files; the producer + consumer + otel-bootstrap + integration-tests modules are UNCHANGED.

</code_context>

<specifics>
## Specific Ideas

- **The two-row dashboard layout (D-02) is itself pedagogically motivated.** Top row = "what an instructor projects during a 60-minute workshop"; bottom row = "what an attendee opens the next morning to poke around." The collapsed-by-default state of the second row IS the message: the demo is small; production is bigger; here's a glimpse of bigger.
- **The `oha` choice over plain curl (D-03) sneaks in a tiny instrumentation lesson.** While `mise run load` is running, attendees see oha's TUI showing live RPS + p50/p95 latency in the same terminal that's pumping load. That latency view PARTIALLY mirrors what `http.server.request.duration` shows in Mimir — a side-by-side "client view vs server view" moment the README's Step 1 *Why it matters* paragraph can call out in 2 sentences.
- **The DOC-04 broken/fixed pair (D-06 captures #2 + #3) is the single most important visual asset in the repo.** Two PNG files render the entire pedagogical core of the workshop — TWO traces become ONE trace because the inject/extract pair was added. The README's Step 2 *Why it matters* + Step 3 *Why it matters* paragraphs synthesize from `03-CONTEXT.md`'s headline-lesson framing.
- **The README's Concepts & FAQ appendix (D-08) preserves a second reading mode.** A skim-the-walkthrough reader gets the per-step blocks; a read-the-narrative reader gets the existing rich prose. Removing the standalone sections to flatten everything into per-step bodies would have lost this.
- **The `step-06-test-output.png` screenshot (D-06 capture #7) doubles as a TEST-01 SC #2 visual proof** — the random RabbitMQ port is visible in the captured terminal output, satisfying TEST-01's "test logs prove this by showing the random port" success criterion in a way that's now visible without running the tests.
- **No Phase 7 git tag (D-09) means the README's final paragraph is shorter and clearer.** A reader reaches the end of the workshop walkthrough at Step 6's *Why it matters*, then the Concepts & FAQ appendix, and a brief "Workshop is at main HEAD past step-06-tests" close. The lack of a "now check out step-07-polish" instruction REMOVES a question ("why would I checkout the polish state?") rather than answering it.

</specifics>

<deferred>
## Deferred Ideas

- **GitHub Actions / CI YAML running `mise run test` on PRs** — mentioned in `06-CONTEXT.md` Deferred Ideas; still deferred for v1. TEST-06 contract is "exits non-zero on failure" — sufficient for any CI runner. CI YAML belongs in v2 if the workshop becomes a maintained shared artifact across cohorts.
- **`docs/FACILITATOR.md` (FAC-01)** — REQUIREMENTS.md v2 entry. Only needed when someone other than the original author delivers the workshop. First-cohort feedback from Phase 7's actual delivery should drive whether v2 wires this in.
- **Sampling-variant checkpoint (`step-07-sampling-variant` / SAMP-01)** — REQUIREMENTS.md v2.
- **Baggage propagation checkpoint (`step-08-baggage` / PROP-V2-01)** — REQUIREMENTS.md v2. Phase 2 D-16 already wired the W3CBaggage propagator; a v2 phase exercises it.
- **DLX/retry checkpoint (`step-09-dlx-retry` / FAIL-01)** — REQUIREMENTS.md v2. Pairs with re-evaluating PROJECT.md AF-5/AF-6 (richer AMQP topology).
- **Per-attendee load-script tweakability (RPS / DURATION env vars)** — D-04 ships only the workshop default (1 req/sec, infinite). v1.x ask if first cohort requests it.
- **CI-based screenshot-regression check** — D-05 ships `mise run docs:screenshots` as a manual one-shot. CI re-running Playwright at every PR is overkill for a workshop repo; v2 ask if Grafana UI shifts cause silent screenshot rot to become a recurring problem.
- **`step-03-waterfall.png` capture** (D-06 capture #4) — planner-discretion to drop if Playwright timing for waterfall views is too brittle. Falls back to 6 PNGs, not 7.
- **Vendor-specific exporter swap demo** (e.g., point at Honeycomb / Datadog) — PROJECT.md flagged as "one-line OTLP endpoint change attendees can do themselves." Out-of-scope for v1; could be a future cookbook entry, not a workshop step.
- **Pyroscope / continuous-profiling integration (DF-20 / fourth-signal lesson)** — out-of-scope per PROJECT.md; v2 follow-up if the workshop wants to extend into a fourth signal.
- **Custom histogram bucket boundaries / Views API tutorial (FEATURES.md AF-16)** — out-of-scope for v1.
- **`step-01-empty-tempo.png` capture** (D-06 capture #1) — minor: if the Playwright capture against the empty Tempo view proves unreliable (Tempo's "no traces found" view rendering is undocumented), planner can drop to 5–6 PNGs without losing the headline DOC-04 pair.
- **README-quick-start placement above Prerequisites** (D-07 Claude's discretion) — planner picks; a v1.x revisit if first-cohort feedback says the README opens too slowly.
- **Phase 7 itself getting an annotated tag** — explicitly declined (D-09). Could be revisited in `/gsd-complete-milestone` as a `v1.0` tag for milestone-completion purposes (separate from phase exit gates).

</deferred>

---

*Phase: 7-polish-differentiators*
*Context gathered: 2026-05-02*
