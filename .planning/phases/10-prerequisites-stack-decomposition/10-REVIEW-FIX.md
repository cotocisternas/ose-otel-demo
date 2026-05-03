---
phase: 10-prerequisites-stack-decomposition
fix_run: 2026-05-02T22:00:00Z
iteration: 1
fix_scope: all
findings_in_scope: 11
fixed: 11
skipped: 0
status: all_fixed
---

# Phase 10: Code Review Fix Report

**Fixed at:** 2026-05-02T22:00:00Z
**Source review:** `.planning/phases/10-prerequisites-stack-decomposition/10-REVIEW.md`
**Iteration:** 1
**Scope:** all (Critical + Warning + Info)

**Summary:**
- Findings in scope: 11 (0 critical, 5 warnings, 6 info)
- Fixed: 11
- Skipped: 0
- Status: all_fixed

All 11 in-scope findings were addressed. Nine got their own atomic commit; IN-05 was naturally resolved by the comment rewrite that accompanied the WR-02 commit (the regex extension and the misleading-comment fix touch the same lines and were applied together to keep the comment accurate to the new regex).

Verification performed:
- Both `OtelSdkConfiguration.java` files compile cleanly (`mvn -pl consumer-service,producer-service -DskipTests compile`).
- `mise run verify:images` passes with the new pins and extended regex (10 images, all pinned).
- The new `otelcol` scrape job validated against the live collector — config parses, container restarts cleanly, "Scrape job added jobName=otelcol" appears in startup logs alongside the three pre-existing jobs, and Mimir returns `up` series for the Collector self-metrics target after a single scrape interval.
- README and `mise.toml` text edits visually re-read after each Edit.

## Fixed Issues

### WR-01: `@PreDestroy` ordering reversed — shutdown race protection absent

**Files modified:**
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`

**Commit:** `1554eb3`

**Applied fix:** Adopted the structural "tiny holder class" variant the reviewer recommended. Introduced a static inner class `CloseableOpenTelemetrySdk implements AutoCloseable` whose `close()` runs `OpenTelemetryAppender.install(OpenTelemetry.noop())` BEFORE delegating to `sdk.close()` — both calls in a single, ordered call stack. Registered the holder as a new `@Bean(destroyMethod = "close")` factory `openTelemetryShutdownGuard(OpenTelemetry)` that depends on the SDK bean (so Spring creates it AFTER and destroys it BEFORE the SDK). Changed the existing `openTelemetry()` `@Bean` annotation from `destroyMethod = "close"` to `destroyMethod = ""` so Spring's autodetection does not call `close()` directly on the SDK bean — the holder owns the lifecycle now. Deleted the broken `@PreDestroy uninstallLogbackAppender()` method and its now-unused `import jakarta.annotation.PreDestroy`. Updated the JavaDoc on the `openTelemetry()` `@Bean` to explain why the previous shape was wrong (Spring destroys the @Configuration bean LAST, not first) and how the holder fixes it. Both services received identical edits — the workshop's "duplicated per service" invariant (DOC-05) is preserved.

**Status:** fixed: requires human verification — the structural change is sound (holder created later → destroyed first → install(noop) precedes sdk.close() in one call stack) and both services compile cleanly, but the actual shutdown ordering only manifests at runtime during JVM termination. Recommend a one-time manual smoke: `mise run dev`, POST a few orders, Ctrl-C the producer, and confirm in logs that `OpenTelemetryAppender.install(OpenTelemetry.noop())` runs before any `BatchLogRecordProcessor` shutdown line.

### WR-02: `postgres:17-alpine` and `valkey/valkey:8.1-alpine` floating tags bypass `verify:images`

**Files modified:**
- `docker-compose.yml`
- `mise.toml`

**Commit:** `791fc79`

**Applied fix:** Pinned `valkey/valkey:8.1-alpine` → `valkey/valkey:8.1.3-alpine` (verified existence on Docker Hub) and `postgres:17-alpine` → `postgres:17.5-alpine` (verified). Extended the `verify:images` floating-tag regex to additionally reject `:NN-suffix` (the major-only-with-flavor pattern that caught `postgres:17-alpine`) by appending `|:[0-9]+-[^[:space:]]+[[:space:]]*$` to the existing alternation. Rewrote the misleading "Acceptable patterns" comment block — fixing IN-05 in the same commit — so each entry now has its own annotated example, the `:NN.NN.NN-suffix` example uses a real triple-patch tag instead of the wrong `:17-alpine`, and the previously-uncategorized `:NN-suffix` is moved to a new "Floating patterns rejected" section pointing at the regex. `mise run verify:images` reports `10 image:s, all pinned`.

**Deviation note:** The review also flagged `valkey/valkey:8.1-alpine` as a `:NN.NN-suffix` floating pattern that should be rejected, but its proposed regex extension only added `:NN-suffix` rejection. Pinning valkey to 8.1.3-alpine resolves the immediate drift, but the regex still does not catch new `:NN.NN-suffix` tags. Catching them would require also pinning `rabbitmq:4.3-management-alpine` (currently `:NN.NN-suffix`) to a full patch — the comment block now documents this as a known limitation. The review's stated regex was applied verbatim; the broader regex tightening is left as a follow-up.

### WR-03: Port 8889 mapped without listener config

**Files modified:**
- `docker-compose.yml`
- `README.md`

**Commit:** `3d4d1ef`

**Applied fix:** Took option 2 from the review (placeholder annotation, not removal) — preserves the README port table contract and the preflight check across the Phase 10 → 11 transition. Added a multi-line `# PLACEHOLDER (WR-03)` comment block on the docker-compose `8889:8889` line explaining that no listener is wired until Phase 11 adds the prometheusexporter pipeline, and that `curl localhost:8889/metrics` returns connection refused in Phase 10 by design. Updated the README port table entry to include `(Phase 11 only — not active in Phase 10; ...)`.

### WR-04: `preflight` port check silently skips on macOS

**Files modified:**
- `mise.toml`

**Commit:** `d231c82`

**Applied fix:** Added `lsof -iTCP:"${port}" -sTCP:LISTEN -n -P 2>/dev/null | grep -q LISTEN` as an OR fallback to the existing `ss -tln 2>/dev/null | grep -q ":${port} "` check, exactly as the review prescribed. Added an inline comment explaining the macOS-silent-pass bug. `mise run preflight` confirmed the port check now fires on a real in-use port (3000 was occupied by a leftover Grafana container from a previous infra run); the fail-fast message correctly identifies the busy port.

### WR-05: Missing `step-04-metrics.png` screenshot referenced as if it exists

**Files modified:**
- `README.md`

**Commit:** `8e54b24`

**Applied fix:** Took option 2 from the review (no screenshot fabrication). Replaced the misleading line-548 claim with a `**Verification screenshot pending** (PREREQ-02 / DOC-04 deferred — see TODO at Step 4)` notice that defers to the existing TODO at line 222. Preserved the descriptive sentence about what the future screenshot will show, framed as forward-looking rather than as evidence-already-captured.

### IN-01: Stale `otel-lgtm` references in README prerequisites

**Files modified:**
- `README.md`

**Commit:** `a2a9f57`

**Applied fix:** Updated the four explicitly cited lines (3, 9, 58, 93). Line 3 now reads "...to a five-component Grafana observability stack (`otel-collector`, `tempo`, `mimir`, `loki`, `grafana`). Phase 10 decomposed the v1.0 single-container `grafana/otel-lgtm` into these five containers; the SDK side is unchanged (STACK-03 invariant)." Line 9 acknowledges the full container count (RabbitMQ + Valkey + Postgres + 2 exporters + 5-container observability stack = 10). Lines 58 and 93 inline-comments now read `# starts RabbitMQ + Valkey + Postgres + observability stack (10 containers)`. Did NOT touch later README sections (Step 4 / Step 9 / Concepts narrative) where `otel-lgtm` is referenced intentionally as historical context for the decomposition lesson — that scope was outside IN-01.

### IN-02: Stale `infra:up` and `infra:down` task descriptions

**Files modified:**
- `mise.toml`

**Commit:** `c69b243`

**Applied fix:** Applied the review's exact replacement strings — `infra:up` description now reads `"Start all infra containers (RabbitMQ, Valkey, Postgres, OTel stack)"` and `infra:down` reads `"Stop infra (preserves Grafana state via grafana-data volume)"`. Verified via `mise tasks` that the new descriptions appear in the listing.

### IN-03: App ports 8080/8081 absent from preflight

**Files modified:**
- `mise.toml`

**Commit:** `85987af`

**Applied fix:** Appended `8080 8081` to the preflight port loop. Updated the section header from "14 ports — 5 v1.0 + 1 oversight + 8 v2.0 additions" to "16 ports — 5 v1.0 + 1 v1.0 oversight (15692) + 8 v2.0 additions + 2 app ports (IN-03)". Added an inline comment block explaining why these belong in preflight — surfaces the "port already in use" failure cleanly at preflight time instead of as a confusing Tomcat error several steps later.

### IN-04: Dead `this.openTelemetry` field

**Files modified:**
- `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`
- `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java`

**Commit:** `fe369b1`

**Applied fix:** WR-01's holder pattern takes the SDK as a constructor parameter and does NOT read `this.openTelemetry`, so the field remains genuinely unused — IN-04 needed a standalone fix (per orchestrator instructions). Added `@SuppressWarnings("unused")` to both fields. Updated the field's JavaDoc to (a) acknowledge that the WR-01 shutdown sequence does not use this field either, (b) explain why the field is still kept (forward-looking phase-internal use, e.g., a future @EventListener that needs the SDK without going through Spring autowiring), and (c) point at IN-04 by name so future readers can trace the choice. Both services received identical edits.

### IN-05: `verify:images` comment typographic error

**Files modified:**
- `mise.toml`

**Commit:** `791fc79` (resolved as part of WR-02 — single commit because the regex change and the comment correction touch the same comment block; splitting them would have left the comment temporarily inaccurate to the regex).

**Applied fix:** Rewrote the "Acceptable patterns" comment block as part of the WR-02 commit. The misleading `:NN.NN.NN-suffix (e.g., :17-alpine)` line was replaced with a properly-classified `:NN.NN.NN-suffix (e.g., :3.7.1-alpine) # full patch + flavor — not floating`. The pattern that ACTUALLY matches `:17-alpine` (i.e., `:NN-suffix`) was moved to a new "Floating patterns rejected by the regex below" section that points at the WR-02 regex extension. The acceptable-but-still-floating-shaped `:NN.NN-suffix` (e.g., `:4.3-management-alpine`) carries an explicit `# WARNING:` annotation explaining why it is currently accepted and what would be required to start rejecting it.

### IN-06: Collector self-metrics not scraped by any Prometheus job

**Files modified:**
- `infra/observability/otelcol-config.yaml`

**Commit:** `d0a8118`

**Applied fix:** Took option 1 from the review (add the scrape job — aligns with the production-shape intent of phase 10). Added a fourth scrape job `otelcol` to the existing `prometheus.config.scrape_configs` list with `targets: ['localhost:8888']` and the same `source: infra-exporter` static label as its three siblings (rabbitmq / redis_exporter / postgres_exporter). `localhost` resolves inside the Collector container — receiver and scrape target are the same process. Added an inline `# WHY (IN-06):` comment explaining what changes for the workshop (Collector self-observability now lands in Mimir alongside app + infra-exporter series rather than being curl-only).

**Verification performed:** Validated the new YAML by running `otel/opentelemetry-collector-contrib:0.151.0 validate --config=...` (clean exit). Restarted the running collector container; logs show "Scrape job added jobName=otelcol" alongside the three pre-existing jobs and "Everything is ready" — clean startup. Queried Mimir at `http://localhost:9009/prometheus/api/v1/query?query=up` and confirmed the new target appears as a successful scrape (instance label is the Collector's own `service.instance.id` UUID, job label is `otelcol-contrib` because the Collector's emitted self-metrics carry their own resource attributes that override our static `job: otelcol` config name in the OTLP→PRW translation — the data lands in Mimir cleanly, which is the goal).

---

_Fixed: 2026-05-02T22:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
