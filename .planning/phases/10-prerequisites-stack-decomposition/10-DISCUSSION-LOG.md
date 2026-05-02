# Phase 10: Prerequisites & Stack Decomposition - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 10-prerequisites-stack-decomposition
**Areas discussed:** Datasource UID strategy, YAML config style/depth, docker-compose layout, Backend admin port exposure

---

## Datasource UID strategy

### Q1 — How do we keep the v1.0 dashboard working after decomposition?

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse old UIDs verbatim | Inspect `grafana/otel-lgtm:0.26.0`'s internal datasource provisioning, copy each `uid:` value into the new `grafana/datasources.yaml`. Zero diff to `ose-otel-demo.json`. PITFALLS.md F1-1 explicit recommendation. | ✓ |
| Patch dashboard JSON to semantic UIDs | Provision new datasources with semantic UIDs (`tempo`, `mimir`, `loki`); search-and-replace the old UIDs in `ose-otel-demo.json`. Bigger diff but more readable in source. | |
| Both — provision new UIDs AND aliases for old | Provision the new datasources with semantic names AND a second alias entry carrying the old UID. Doubles the YAML surface; needs Grafana 10+ alias support. | |

**User's choice:** Reuse old UIDs verbatim
**Notes:** Aligned with PITFALLS.md F1-1's explicit recommendation. Teaching message: "the dashboard is the artifact, the UIDs are the contract."

### Q2 — How much cross-signal wiring belongs in Phase 10?

| Option | Description | Selected |
|--------|-------------|----------|
| Match v1.0 surface only | Wire only what otel-lgtm provided implicitly: `tracesToLogsV2` and Loki `derivedFields`. NO `exemplarTraceIdDestinations` (defer to Phase 12). | |
| Full cross-signal wiring now | Wire `tracesToLogsV2`, `tracesToMetrics`, Loki `derivedFields`, AND `exemplarTraceIdDestinations` placeholder. One YAML, no Phase 12 follow-up edit. | ✓ |
| Minimal — only what dashboard panels reference today | Audit `ose-otel-demo.json` for actual datalink references; wire only those. Cleanest YAML; biggest follow-up wiring debt. | |

**User's choice:** Full cross-signal wiring now
**Notes:** Eliminates a Phase 12 datasource edit. Phase 12 then only flips `send_exemplars: true` on Collector and `ExemplarFilter.traceBased()` on SDK.

### Q3 — What gate proves datasources are wired correctly?

| Option | Description | Selected |
|--------|-------------|----------|
| Manual eye-test only | Phase 10 SC#4 already says "dashboard loads, no Datasource not found". Workshop attendees verify visually. No new task. | |
| New `mise run verify:datasources` task | curl Grafana's `/api/datasources` and assert UIDs. Mirrors v1.0 `verify:bom`. ~20 lines of bash. | ✓ |
| Integration test in `integration-tests/` | Spring Boot Testcontainers IT hitting Grafana's API. Heaviest; depends on Grafana being up during `mvn verify`. | |

**User's choice:** New `mise run verify:datasources` task
**Notes:** Mirrors v1.0 `verify:bom` pattern. Future phases catch UID drift as fast-fail.

---

## YAML config style/depth

### Q1 — What's the verbosity baseline for the new YAML configs?

| Option | Description | Selected |
|--------|-------------|----------|
| Teaching-grade with WHY comments | Every block has a short comment explaining WHY. ~30% of each YAML is comments. Mirrors v1.0's `OtelSdkConfiguration` JavaDoc density. | ✓ |
| Minimal — only what runs | Each YAML contains only keys needed to make the demo work. No explanatory comments. README does the teaching. | |
| Production-realistic — full prod-shaped configs | Each YAML includes production sections (auth headers, TLS, retry blocks). Reads like sanitized prod config. | |

**User's choice:** Teaching-grade with WHY comments
**Notes:** Workshop attendees should understand each YAML cold without flipping to README.

### Q2 — Where does the Prometheus scrape config live after decomposition?

| Option | Description | Selected |
|--------|-------------|----------|
| Collector `prometheus` receiver | Scrape jobs go into `otelcol-config.yaml`. All metrics flow through single Collector pipeline. | ✓ |
| Mimir scrape config | Mimir's single-binary mode has a Prometheus-compatible scraper. Splits ingress: OTLP via Collector → Mimir, scrape direct in Mimir. | |
| Drop the exporters — simplify v2.0 | Remove `redis-exporter`, `postgres-exporter`, `rabbitmq_prometheus` plugin. Risks STACK-04 if NOC panels depend on them. | |

**User's choice:** Collector `prometheus` receiver
**Notes:** Teaching message: "the Collector is the single mouth feeding the backends."

### Q3 — Where does Tempo `metrics_generator` send its synthetic metrics?

| Option | Description | Selected |
|--------|-------------|----------|
| Direct to Mimir | Tempo writes to `http://mimir:9009/api/v1/push`. Canonical Tempo + Mimir doc pattern. F1-2 mitigation copies cleanly. | ✓ |
| Through Collector (consistent ingress) | Tempo writes to Collector's `prometheusremotewrite` receiver; Collector forwards to Mimir. Holds the "single ingress" principle but adds a hop. | |
| Disable metrics_generator entirely | Drop the service-graph panel from `ose-otel-demo.json`. Loses teaching value; breaks STACK-04. | |

**User's choice:** Direct to Mimir
**Notes:** Honest about polyglot writers — README addendum explains that not all metric paths go through the Collector.

### Q4 — Pre-enable Loki ruler in Phase 10?

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-enable, empty rules dir | `loki-config.yaml` has `ruler:` block enabled with `remote_write → mimir:9009`, empty rules dir mounted. Phase 13 only adds `order-errors.yaml`. F4-3 mitigation lands now. | ✓ |
| Leave off; Phase 13 enables | Phase 10 stays strictly about decomposition + parity. Phase 13 enables `ruler:` and adds the rule together. | |

**User's choice:** Pre-enable, empty rules dir
**Notes:** Symmetric with Phase 12 prep. Phase 13 becomes a single rule-file addition.

---

## docker-compose layout

### Q1 — How is the compose source organized after we add the 5 new services?

| Option | Description | Selected |
|--------|-------------|----------|
| Single file, all 10 services | One `docker-compose.yml`. `mise run infra:up` keeps the same command. Use `# ===== Observability stack =====` divider. ~250 lines. | ✓ |
| Single file with profiles | All services in one file; tag observability with `profiles: [observability]` and apps/data with `profiles: [apps]`. | |
| Compose override files | Split into `docker-compose.yml` (data) + `docker-compose.observability.yml` (backends). Cleanest source split; bare `docker compose up` breaks. | |

**User's choice:** Single file, all 10 services
**Notes:** Simplest mental model; matches v1.0; bare `docker compose up` works.

### Q2 — How should the 5 new backends persist state across `infra:down` / `infra:up`?

| Option | Description | Selected |
|--------|-------------|----------|
| Named volume per backend, `infra:reset` wipes | 5 named volumes (`tempo-data`, `tempo-wal`, `mimir-data`, `loki-data`, `grafana-data`) replacing v1.0's `lgtm-data`. State survives `infra:down`/`up`. F1-2 mitigation built in. | ✓ |
| All ephemeral (no volumes) | Each backend in-memory or `/tmp`-backed. Predictable demos but F1-2 hits every cold start. | |
| Bind-mount data dirs to `./infra/observability/data/` | Bind-mount data to project paths. Strongest "what does the backend store on disk?" teaching surface. Permission/UID issues. | |

**User's choice:** Named volume per backend, `infra:reset` wipes
**Notes:** Matches v1.0 pattern exactly; F1-2 (Tempo WAL persistence) addressed by named volume.

---

## Backend admin port exposure

### Q1 — Which backend admin/API ports get exposed to host?

| Option | Description | Selected |
|--------|-------------|----------|
| All admin ports exposed | Map: Grafana :3000, Collector :4317/:4318/:13133/:8889, Tempo :3200, Mimir :9009, Loki :3100. Workshop attendees can `curl` each backend's API directly. | ✓ |
| Grafana + OTLP only (production-shape) | Same surface as v1.0 lgtm. All backend access goes through Grafana's datasource proxy. | |
| Grafana + OTLP + Collector health/metrics | Middle ground: add Collector :13133/:8888/:8889 only. Backends stay container-internal. | |

**User's choice:** All admin ports exposed
**Notes:** Matches workshop's hands-on philosophy. Backend HTTP APIs are themselves OTel-ecosystem teaching surface.

### Q2 — How does `mise run preflight` handle the new admin ports?

| Option | Description | Selected |
|--------|-------------|----------|
| Block on all exposed ports | Extend preflight to check all exposed ports. Hard fail if any in use. Predictable; matches v1.0 strict mode. | ✓ |
| Block on critical ports only, advisory on rest | Block load-bearing ports (Grafana, OTLP, RabbitMQ); print warning-but-don't-fail for new admin ports. | |
| Keep v1.0 list; add separate `mise run preflight:full` | Backwards-compat preflight + a new comprehensive task. Two tasks for attendees to remember. | |

**User's choice:** Block on all exposed ports
**Notes:** Matches v1.0 strict-mode preflight. Attendees with conflicts see the offending port immediately.

---

## Wrap-up gate

### Which gray areas remain unclear?

| Option | Description | Selected |
|--------|-------------|----------|
| I'm ready for context | Decisions clear enough for researcher and planner. Write CONTEXT.md and commit. | ✓ |
| Explore PREREQ-01 / PREREQ-02 / verify:images / README | A few more turns on prereq carryovers, floating-tag guardrail, README narrative. | |
| Explore something else | A gray area was missed; surface in plain text. | |

**User's choice:** I'm ready for context
**Notes:** PREREQ-01 minimal-fix shape, PREREQ-02 manual capture, and `verify:images` mechanism captured under Claude's Discretion (D-12, D-13, D-14) per STATE.md precedent.

---

## Claude's Discretion

The following decisions were captured under Claude's Discretion (CONTEXT.md §Claude's Discretion + D-12 through D-16) without explicit user gate, on the basis of strong v1.0 precedent or pitfall research:

- **D-12** (PREREQ-01 minimal fix shape) — STATE.md already documented the recommended LOG-03-mirror pattern at Phase 5-06; no fork in road.
- **D-13** (PREREQ-02 manual screenshot) — v1.0 Phase 7-07 already approved the deferral with specific rationale; v2.0 closes it the simplest way.
- **D-14** (`verify:images` is a mise task, not a pre-commit hook) — workshop attendees rarely set up pre-commit; mise tasks are the established v1.0 verification surface (see `verify:bom`).
- **D-15** (`grafana/prometheus.yaml` deleted) — D-05 makes it orphan; deletion is the simplest end state.
- **D-16** (depends_on shape + per-service healthchecks) — implementation-level, but locked here so the planner doesn't re-litigate.

Per-service healthcheck tuning, exact YAML key shapes, Tempo `service_graphs` config, Collector batch/memory_limiter settings, and the `verify:images` regex specifics are explicitly deferred to plan-phase research.

## Deferred Ideas

(See CONTEXT.md §Deferred for the full list — repeated here for audit completeness.)

- Healthcheck `interval`/`retries`/`start_period` tuning per backend
- Mimir blocks-storage on local filesystem vs S3-compatible (deferred to future "infra deepdive" milestone)
- README port-map curation (which 2–3 backend curl examples land in README Step 10)
- `mise run verify:datasources` / `verify:images` regex specifics
- Collector exporter component naming verification (`otlp_http` vs `otlphttp` per F1-4)
- Loki ruler `evaluation_interval` and rate-window tuning (deferred to Phase 13)
- Service-graph re-priming README note (5-minute warm-up window after `infra:reset`)
