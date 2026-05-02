# Phase 10: Prerequisites & Stack Decomposition - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Reset the v1.0 workshop's all-in-one `grafana/otel-lgtm:0.26.0` to a production-shaped 5-container stack (`otel/opentelemetry-collector-contrib:0.151.0`, `grafana/tempo:2.10.5`, `grafana/mimir:3.0.6`, `grafana/loki:3.7.1`, `grafana/grafana:13.0.1`), clear the v1.0 carryover circular-reference bug on `OtelSdkConfiguration`, and capture the deferred `step-04-metrics.png` — without changing a single Java SDK config line so attendees see "production decomposition is a Collector-config exercise, not an SDK exercise."

Phase boundaries (locked by REQUIREMENTS.md PREREQ-01, PREREQ-02, STACK-01..05):
- Five exact-patch-pinned container images replace `lgtm:` (no floating tags)
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` unchanged in both `OtelSdkConfiguration` files
- `lgtm:` service removed entirely from `docker-compose.yml`
- All v1.0 `ose-otel-demo` dashboard panels render without "Datasource not found"
- Mimir runs `auth_enabled: false` (no `X-Scope-OrgID` 401 errors)
- `BeanCurrentlyInCreationException` on `OtelSdkConfiguration` eliminated in both services
- `docs/screenshots/step-04-metrics.png` captured and committed

Out of scope for this phase (per REQUIREMENTS.md and ARCHITECTURE.md):
- Tail sampling configuration → Phase 11
- `ExemplarFilter.traceBased()` activation → Phase 12 (only the *placeholder* `exemplarTraceIdDestinations` datasource wiring lands here)
- Loki recording rules → Phase 13 (only the *enabled* `ruler:` block + empty rules dir land here)
- JDBC/JPA / HTTP client / sampling / baggage / AMQP topology → Phases 14–17

</domain>

<decisions>
## Implementation Decisions

### Datasource UID strategy

- **D-01:** Reuse `grafana/otel-lgtm:0.26.0`'s internal datasource UIDs verbatim in the new `grafana/datasources.yaml`. Inspection step (mandatory before authoring): `docker run --rm grafana/otel-lgtm:0.26.0 cat /otel-lgtm/grafana/conf/provisioning/datasources/grafana-datasources.yaml`. Note the `uid:` field for each of `tempo`/`mimir`/`loki` and copy verbatim. Rationale: zero diff to `grafana/dashboards/ose-otel-demo.json` — F1-1's explicit recommendation. Teaching message: *"the dashboard is the artifact, the UIDs are the contract — preserve the contract on infra changes."*

- **D-02:** Wire ALL cross-signal datalinks in Phase 10 (not phase-by-phase). The new `grafana/datasources.yaml` includes:
  - Tempo datasource: `tracesToLogsV2` (Tempo span → Loki) and `tracesToMetrics` (Tempo span → Mimir)
  - Loki datasource: `derivedFields` to extract `trace_id` and link to the Tempo datasource UID
  - Mimir/Prometheus datasource: `exemplarTraceIdDestinations` placeholder pointing at the Tempo datasource UID (Phase 12 flips on `send_exemplars` and `ExemplarFilter.traceBased()` — datasource surface is already wired)
  - Rationale: a single Phase 10 datasource edit removes the need for Phase 12 to revisit datasources.yaml. Rationale extends to Loki ruler (D-12) for symmetry.

- **D-03:** Add a `mise run verify:datasources` task that does `curl -s localhost:3000/api/datasources | jq '.[].uid'` and asserts the expected UIDs are present. Mirrors the v1.0 `verify:bom` pattern. Catches UID drift in future phases as a fast-fail rather than a silent blank-panel symptom.

### YAML config style/depth

- **D-04:** Teaching-grade with WHY comments on every block in all five new YAMLs (`infra/observability/{otelcol,tempo,mimir,loki}-config.yaml` + `grafana/datasources.yaml`). Style precedent: v1.0's `OtelSdkConfiguration.java` (137/131 comment lines) and the existing `docker-compose.yml` `lgtm:` / `redis-exporter:` / `postgres-exporter:` blocks. Each YAML key gets a one-line WHY comment; production-only sections (auth headers, TLS, retry policy) are NOT included. Workshop attendees should be able to read each YAML cold and understand why each block is there.

- **D-05:** Collector `prometheus` receiver becomes the **single ingress** for exporter scrapes. The current `grafana/prometheus.yaml` (mounted into `lgtm` for `rabbitmq:15692`, `redis-exporter:9121`, `postgres-exporter:9187` scraping) is migrated into `infra/observability/otelcol-config.yaml` under `receivers.prometheus.config.scrape_configs`. The orphan `grafana/prometheus.yaml` is deleted. Teaching message: *"the Collector is the single mouth feeding the backends."*

- **D-06:** Tempo `metrics_generator` writes **directly to Mimir** via `metrics_generator.storage.remote_write: [{url: http://mimir:9009/api/v1/push}]` — the canonical Tempo + Mimir doc pattern. F1-2 mitigation copies cleanly: use Docker service name `mimir` (NOT `localhost`); named-volume `tempo-wal:/var/tempo/wal` per D-09 mount keeps service-graph alive across restarts. README acknowledges *"not all metric paths go through the Collector — backends can be polyglot, that's the production reality."*

- **D-07:** Pre-enable Loki `ruler` component in Phase 10 with an EMPTY rules dir. `loki-config.yaml` ruler block:
  - `ruler.storage.type: local`
  - `ruler_storage.local.directory: /loki/rules`
  - `ruler.remote_write.url: http://mimir:9009/api/v1/push` (matches D-06's Mimir URL)
  - `evaluation_interval: 1m`
  - Volume mount `./infra/observability/loki-rules:/loki/rules:ro` (rules dir created empty; tracked via `.gitkeep`)
  - F4-3 mitigation lands now (volume mount path matches `ruler_storage.local.directory`)
  - Phase 13 adds only `infra/observability/loki-rules/order-errors.yaml` — no `loki-config.yaml` change required

### docker-compose layout

- **D-08:** Single `docker-compose.yml` with all 10 services (5 existing + 5 new). `mise run infra:up` keeps `docker compose up -d --wait`. Use `# ===== Observability stack =====` comment dividers between data services and observability services for navigability. Reference: existing v1.0 file uses comment-block dividers between `Phase 8` services and the `lgtm:` block. NO compose profiles, NO override files.

- **D-09:** Five new named volumes — `tempo-data`, `tempo-wal`, `mimir-data`, `loki-data`, `grafana-data` — replacing v1.0's single `lgtm-data`. State survives `infra:down` / `infra:up` (workshop attendees can take a break, restart, traces still in Tempo). `infra:reset` (`docker compose down -v`) wipes all five. F1-2 mitigation built in (Tempo WAL has its own named volume).

### Backend admin port exposure

- **D-10:** All backend admin/API ports exposed to host:
  - Grafana: `3000`
  - Collector: `4317` (OTLP gRPC), `4318` (OTLP HTTP), `13133` (health), `8888` (Collector self-metrics), `8889` (prometheus exporter receiver)
  - Tempo: `3200` (HTTP API)
  - Mimir: `9009` (HTTP API)
  - Loki: `3100` (HTTP API)
  - Each port gets a `# debug: curl localhost:NNNN/...` comment in compose
  - Rationale: matches the workshop's hands-on "attendees can attach a debugger and step through" philosophy. Backend HTTP APIs are themselves part of OTel-ecosystem fluency.

- **D-11:** `mise run preflight` extends its port-list check to ALL exposed ports, including the existing v1.0 ports plus all D-10 additions:
  ```
  [3000, 4317, 4318, 5672, 15672, 6379, 5432, 3200, 9009, 3100, 13133, 8888, 8889]
  ```
  Hard-fail if any in use (matches v1.0 strict-mode preflight). Existing ports `5672`/`15672` (RabbitMQ), `6379` (Valkey), `5432` (Postgres), `15692` (RabbitMQ Prometheus) remain in scope; `15692` was already in compose port mapping but absent from preflight v1.0 list — Phase 10 corrects this oversight too.

### Other phase-scoped decisions (load-bearing but not gray-area)

- **D-12:** PREREQ-01 fix scope is MINIMAL — match v1.0 LOG-03 pattern verbatim. In each `OtelSdkConfiguration.java` (`producer-service` + `consumer-service`):
  1. Drop the `@Autowired private OpenTelemetry openTelemetry;` field
  2. Inside the `@Bean OpenTelemetry openTelemetry()` factory body, after building `sdk`, add `this.openTelemetry = sdk;` BEFORE `OpenTelemetryAppender.install(sdk)` is called and BEFORE `return sdk`
  3. Keep the field declaration (without `@Autowired`) as a non-injected instance field
  No SDK refactor; no extraction to a non-`@Configuration` helper class. TRACE-01 / DOC-05 (per-service duplicate `OtelSdkConfiguration`) is preserved.

- **D-13:** PREREQ-02 (`docs/screenshots/step-04-metrics.png`) — capture **manually one-shot** on `main` HEAD after Phase 10 lands. The automated `mise run docs:screenshots` task is NOT modified. Rationale: v1.0 Phase 7-07 already documented why the automated pipeline can't render polish-layer artifacts at older `step-NN-*` tags ("Scope Cut" rationale in `07-04-SUMMARY.md`); fixing the automation is bigger than the artifact warrants. After Phase 10 lands, the dashboard renders correctly on the new decomposed stack at `main` HEAD, so the metrics panel can be screenshot'd directly. The deferred-PNG status is closed when the file lands in `docs/screenshots/`.

- **D-14:** STACK-02 floating-tag guardrail — `mise run verify:images` task that greps `docker-compose.yml` for any `image:` line whose tag matches a floating pattern (`:latest`, `:[0-9]+$`, `:[0-9]+\.[0-9]+$`, no tag at all). Hard-fail if any match. NOT a pre-commit hook (workshop attendees rarely set up hooks); just a mise task. Documented in README Step 10.

- **D-15:** Removed `grafana/prometheus.yaml` (D-05 migrates its scrape config into the Collector). Removed from `docker-compose.yml` mount of `lgtm:` (which is itself removed). The file is `git rm`-deleted, not moved.

- **D-16:** `docker-compose.yml` ordering — observability services come AFTER data services. `depends_on` semantics:
  - `otel-collector` depends on `tempo` + `mimir` + `loki` (all `service_started` — these don't ship Docker HEALTHCHECKs by default; rely on Collector's own retry-on-export to handle backend warmup)
  - `tempo` depends on `mimir` (`service_started` — Tempo's `metrics_generator.remote_write` will retry until Mimir is up)
  - `loki` depends on `mimir` (`service_started` — Loki's `ruler.remote_write` will retry until Mimir is up)
  - `grafana` depends on `tempo` + `mimir` + `loki` (`service_started` — datasource provisioning retries on healthcheck loop in Grafana itself)
  - Each new service gets an explicit `healthcheck:` block (Tempo `/ready`, Mimir `/ready`, Loki `/ready`, Grafana `/api/health`, Collector `:13133/`). `mise run infra:up` `--wait` honors these.

### Claude's Discretion

The following are not asked of the user; planner/researcher decides based on conventional best practices and pitfall research:
- Exact YAML key shape for each backend's storage block (Tempo `wal_path`, Mimir `blocks_storage`, Loki `schema_config`) — pull from upstream single-binary examples; Pitfalls research already confirms the canonical paths.
- Tempo `metrics_generator.processor.service_graphs` config (which span attributes contribute to graph edges) — match upstream Grafana service-graph defaults.
- Collector `processors.batch` and `processors.memory_limiter` settings — workshop-grade defaults from upstream Collector contrib examples.
- Healthcheck `interval`/`timeout`/`start_period` tuning per service — mirror existing v1.0 `rabbitmq` healthcheck cadence (`interval: 10s`, `timeout: 5s`, `retries: 10`, `start_period: 30s`) unless upstream docs prescribe otherwise.
- `mise run verify:images` regex specifics — planner picks a defensible pattern.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project planning sources of truth (load-bearing for ALL planning)

- `.planning/PROJECT.md` — milestone v2.0 charter; Key Decisions table including TRACE-01/DOC-05 (per-service duplication of `OtelSdkConfiguration`), WORK-01 (annotated git tags on `main`)
- `.planning/REQUIREMENTS.md` — v2.0 REQ-IDs PREREQ-01/02 + STACK-01..05; Out-of-Scope boundaries (no Alloy, no multi-collector LB, no Prometheus pull, etc.)
- `.planning/ROADMAP.md` Phase 10 section — pedagogical headline, Success Criteria #1–5, pitfall mitigations (X-1, F1-1, F1-3, X-3), git tag `step-10-collector-decompose`
- `.planning/STATE.md` — recent decisions including the recommended PREREQ-01 fix shape ("assign `this.openTelemetry = sdk` inside `@Bean` factory body and drop `@Autowired` field"); v1.0 Phase 5-06 documented this pattern via LOG-03

### v2.0 research artifacts (load-bearing for plan-phase)

- `.planning/research/SUMMARY.md` — v2.0 Production Shapes research summary; Operational Arc rationale (Phase 10 unblocks 11/12/13)
- `.planning/research/STACK.md` — exact-pinned image tags + complete config-file templates for `otelcol-config.yaml`, `tempo-config.yaml`, `mimir-config.yaml`, `loki-config.yaml`, `grafana-datasources.yaml`, `docker-compose.yml`
- `.planning/research/ARCHITECTURE.md` §Feature 1 — file paths under `infra/observability/`, modified vs new components, dependency-vs-prerequisite phase graph
- `.planning/research/PITFALLS.md` §F1 (F1-1 UID mismatch, F1-2 Tempo WAL + remote_write, F1-3 Mimir multitenancy, F1-4 Collector exporter naming, F1-5 port collision) and §X (X-1 circular ref, X-3 image pinning) — concrete mitigation steps
- `.planning/research/FEATURES.md` §V2-TS-1 — Decomposed Collector table-stake core; differentiators V2-DF-* deferred to phases 11+

### v1.0 carryover (must read before touching `OtelSdkConfiguration`)

- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java` — current state of the circular-ref bug; LOG-03 fix shape lives in this file's git history (Phase 5-06 inline-assign-in-`@Bean`-body precedent)
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` — same shape, same fix
- `.planning/phases/05-logs-correlation/05-06-SUMMARY.md` — documents the LOG-03 inline-assign pattern that PREREQ-01 mirrors

### v1.0 infrastructure (must read before editing `docker-compose.yml`)

- `docker-compose.yml` — current 6-service file (`rabbitmq`, `valkey`, `postgres`, `redis-exporter`, `postgres-exporter`, `lgtm`); the `lgtm:` block is the one being replaced
- `mise.toml` — `[tasks."infra:up"]`, `[tasks."infra:down"]`, `[tasks."infra:reset"]`, `[tasks.preflight]`, `[tasks."verify:bom"]` — the verify task pattern PREREQ extends; the preflight task whose port list D-11 grows
- `grafana/dashboards/ose-otel-demo.json` — the load-bearing dashboard whose panels must keep rendering after Phase 10 (STACK-04 acceptance gate)
- `grafana/dashboards/dashboards.yaml` — existing dashboard provisioning manifest; remains, mounted into the new `grafana:` service (path may shift from `/otel-lgtm/grafana/conf/provisioning/dashboards` to a path canonical for the standalone Grafana image — research must confirm the new in-container path)
- `grafana/prometheus.yaml` — to be deleted (D-05 + D-15)
- `scripts/screenshots/capture.mjs` — read-only reference for D-13 (NOT modified)

### v2.0 phase prep referenced from this phase

- `.planning/phases/12-exemplars-metrics-trace-click-through/` — destination of the `exemplarTraceIdDestinations` placeholder D-02 pre-wires (directory may not exist yet; phase plan creates it)
- `.planning/phases/13-log-based-metrics-loki-recording-rules/` — destination of the empty `loki-rules/` dir D-07 pre-creates

### Upstream documentation references (research must consult; bookmarked here so planner doesn't re-discover)

- Grafana Tempo Docker-Compose local example — single-binary tempo with `metrics_generator` (canonical reference for D-06)
- Grafana Mimir `auth_enabled: false` for single-binary single-tenant deployments (F1-3)
- Grafana Loki Recording Rules — ruler config + remote_write pattern (D-07)
- OTel Collector contrib `prometheus` receiver — scrape job config (D-05)
- OTel Collector contrib `otlphttp` exporter naming (NOT `otlp_http`) — F1-4 mitigation
- Grafana 13.0.1 datasource provisioning schema for `tracesToLogsV2`, `tracesToMetrics`, `derivedFields`, `exemplarTraceIdDestinations` (D-02)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`mise.toml` task scaffolding** — `[tasks.preflight]`, `[tasks."verify:bom"]`, `[tasks."infra:up"]` are the templates D-03, D-11, D-14 extend. The `verify:bom` task's pattern (mvn output → grep → sort → awk → fail-on-violation) is the structural template for `verify:datasources` and `verify:images`.
- **`docker-compose.yml` healthcheck blocks** — `rabbitmq`, `valkey`, `postgres` each have explicit `healthcheck:` (`interval: 10s`, `timeout: 5s`, `retries: 10`, with `start_period` 10/20/30s). New observability services should mirror this style.
- **Comment-density precedent in `docker-compose.yml`** — `lgtm:` block has 8 comment lines explaining D-01 spirit, anonymous-access reasons, and the do-NOT-override-healthcheck rule. New services should match this annotation density (not exceed it).
- **`OpenTelemetryAppender.install(sdk)` pattern** — already lives inside the `@Bean openTelemetry()` factory body (Phase 5 LOG-03 fix). The PREREQ-01 fix slots `this.openTelemetry = sdk;` immediately before `OpenTelemetryAppender.install(sdk)` — same shape, same scope, no new pattern.
- **`grafana/dashboards/dashboards.yaml`** — existing auto-provisioning manifest. The `grafana:` service's volume mount changes path (in-container Grafana 13 path differs from `otel-lgtm`'s `/otel-lgtm/grafana/...`), but the manifest content stays intact.

### Established Patterns

- **TRACE-01 / DOC-05** — `OtelSdkConfiguration` is intentionally per-service duplicated. PREREQ-01 fix MUST be applied independently in BOTH services with identical shape (catch: linting/IDE may suggest extraction to `otel-bootstrap` — REJECT, this is the workshop's load-bearing teaching surface).
- **WORK-01** — Workshop checkpoints are annotated tags on `main`, applied atomically with the phase-completion commit. Phase 10's tag is `step-10-collector-decompose`. Tag is NOT applied during phase execution; it lands with the orchestrator's atomic merge commit (per Phase 2-06 / 6-06 / 7-07 precedent).
- **`mise run verify:*` family** — naming convention for fast-fail validation tasks. `verify:bom` (Phase 2 invariant), new `verify:datasources` (D-03), new `verify:images` (D-14). Pattern: bash one-liner with grep + assertion; non-zero exit on violation.
- **Pinned image tag policy** — every `image:` in compose has an exact patch version (no `:latest`, no `:0.x` prefix-only tags). Existing examples: `rabbitmq:4.3-management-alpine`, `postgres:17-alpine`, `valkey/valkey:8.1-alpine`, `oliver006/redis_exporter:v1.83.0`, `prometheuscommunity/postgres-exporter:v0.19.1`, `grafana/otel-lgtm:0.26.0`. Five new images follow same convention.
- **Comment-block dividers in YAML** — `mise.toml` uses `# ──────────────────────────────────────────────────────────────────` dividers; `docker-compose.yml` uses `# Phase 8: ...` and `# Step 9 (...)` block headers. New observability stack section uses similar visual divider.

### Integration Points

- **`docker-compose.yml`** is the single integration surface — the existing 5 data services stay verbatim; the `lgtm:` block is replaced by 5 new service blocks; one section of named-volumes section grows.
- **`mise.toml`** — `[tasks.preflight]` port list grows (D-11); two new `[tasks."verify:datasources"]` and `[tasks."verify:images"]` blocks land. `[tasks."infra:up"]` body unchanged.
- **`grafana/datasources.yaml`** — NEW file, replaces `otel-lgtm`'s built-in datasource provisioning. Mounted into the new `grafana:` service.
- **`infra/observability/`** — NEW directory containing `otelcol-config.yaml`, `tempo-config.yaml`, `mimir-config.yaml`, `loki-config.yaml`, `loki-rules/.gitkeep`. Mounted as bind volumes into respective services.
- **`producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`** — minimal edit (3-line change): drop `@Autowired`, add `this.openTelemetry = sdk;` inline, optionally adjust JavaDoc to note the LOG-03/PREREQ-01 lineage.
- **`consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`** — same shape, same minimal edit.
- **`grafana/dashboards/ose-otel-demo.json`** — UNTOUCHED (D-01 reuse-old-UIDs makes this true). STACK-04 success gate reads this file's panel queries and confirms each datasource UID resolves.
- **`grafana/prometheus.yaml`** — DELETED (D-05 + D-15).
- **`docs/screenshots/step-04-metrics.png`** — NEW file, captured manually post-decomposition (D-13).
- **`README.md`** — Step 10 section added with `step-10-collector-decompose` tag callout, paired before/after compose-service-count narrative, port-map table, and the `mise run verify:images` / `verify:datasources` commands.

</code_context>

<specifics>
## Specific Ideas

- The user's pattern across all four areas was **"do prep now, not phase-by-phase"** — full cross-signal datalink wiring (D-02), Loki ruler pre-enable (D-07), all admin ports exposed (D-10). Downstream phases (11/12/13) should expect the datasource and Loki surfaces already prepped, with their work being the SDK and Collector-config hookups, not datasource edits.
- The user's pattern is also **"matches v1.0 verbatim where v1.0 is established"** — D-01 (UID reuse), D-08 (single compose file), D-09 (named volumes following lgtm-data lineage), D-12 (PREREQ-01 minimal LOG-03 mirror), D-13 (PREREQ-02 manual one-shot, no automation rework). Planner should default to v1.0 mechanics on any silent question.
- D-04 teaching-grade YAML style is the explicit teaching surface for this phase — researcher should pull example WHY-comment density from `OtelSdkConfiguration.java` and v1.0's `docker-compose.yml` `lgtm:` block, not from upstream Tempo/Mimir/Loki examples (which are minimal).
- D-13's manual screenshot is the proper closure of the v1.0 Phase 7-07 deferred PNG — not a regression, not a re-attempt of the automation. The phase is complete when the file lands in `docs/screenshots/`.

</specifics>

<deferred>
## Deferred Ideas

- **Healthcheck-strategy refinement** — the v1.0 lgtm pattern relied on the image's bundled healthcheck. Decomposed services don't have bundled healthchecks of equivalent quality; Phase 10 adds explicit blocks (D-16). Tuning `interval`/`retries`/`start_period` per backend's actual cold-start time is implementation-level — defer to plan-phase research.
- **Mimir blocks-storage on local disk vs filesystem-shared** — single-binary single-tenant uses local `filesystem` storage; production-realistic would use S3-compatible. Phase 10 stays single-binary local — production realism is intentionally deferred to a future "infra deepdive" milestone (not v2.0 scope).
- **Backend admin port exposure pedagogy in README** — not all 10+ ports need a teaching paragraph. README Step 10 likely picks 2–3 "interesting" debug commands (e.g., `curl localhost:3200/api/search?tags=service.name=order-producer`) and lists the rest in a port-map table. Exact selection deferred to plan-phase.
- **`mise run verify:datasources` / `verify:images` regex tightness** — defer the regex shape to plan-phase. D-03 and D-14 lock the WHAT (validation gate exists, fast-fail mise task); the HOW (exact bash pattern) is research/planning detail.
- **Collector exporter component naming** — F1-4 is a known pitfall (Collector contrib renamed `otlphttp` → `otlp_http` then back); planner should verify the canonical name in `otel/opentelemetry-collector-contrib:0.151.0` via `otelcol components` at plan time, not resolve it now.
- **Loki ruler config interval choices** — `evaluation_interval: 1m` (D-07) is a starting point; F4-2 (rate-window aliasing) gets fully addressed in Phase 13. Phase 10 just enables the ruler; Phase 13 picks the rule's rate window.
- **Service-graph dashboard panel data quality** — Tempo `metrics_generator` re-priming after a `infra:down`/`up` cycle takes 1–5 minutes (F1-2 even with WAL volume). README may want to note this so attendees don't think the service-graph is broken on first POST. Defer wording to README Step 10 authoring.

### Reviewed Todos (not folded)

None — `cross_reference_todos` step did not surface matches for Phase 10 scope.

</deferred>

---

*Phase: 10-prerequisites-stack-decomposition*
*Context gathered: 2026-05-02*
