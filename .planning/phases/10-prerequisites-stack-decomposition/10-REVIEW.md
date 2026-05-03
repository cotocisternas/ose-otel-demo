---
phase: 10-prerequisites-stack-decomposition
reviewed: 2026-05-02T21:30:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - grafana/dashboards/dashboards.yaml
  - grafana/datasources.yaml
  - infra/observability/loki-config.yaml
  - infra/observability/mimir-config.yaml
  - infra/observability/otelcol-config.yaml
  - infra/observability/tempo-config.yaml
  - mise.toml
  - docker-compose.yml
  - README.md
findings:
  critical: 0
  warning: 5
  info: 6
  total: 11
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-05-02T21:30:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 10 decomposes the single `grafana/otel-lgtm:0.26.0` container into five production-shape
containers and introduces the PREREQ-01 cycle fix in both `OtelSdkConfiguration.java` files.
The Java code is structurally sound; the five backend YAML configs are well-formed and
heavily commented for workshop value.

Five warnings surfaced: a `@PreDestroy` ordering bug that silently voids the shutdown race
protection it was designed to provide; two genuinely floating image tags that bypass the
`verify:images` guardrail; a dead port mapping with no backing listener config; a macOS
silent-pass gap in the `preflight` port checker; and a missing screenshot that README
claims exists. Six informational items cover stale otel-lgtm references in README prose,
a dead code field, absent app-port checks in preflight, a misleading comment in
`verify:images`, a missing Collector self-metrics scrape job, and a volume comment
inconsistency.

No critical (security, data loss, incorrect behavior) issues were found. The workshop-safe
intentional settings (anonymous Grafana Admin, `tls.insecure: true`, `multitenancy_enabled:
false`, always-on sampler) are appropriately documented and excluded from this review per
the phase context.

---

## Warnings

### WR-01: `@PreDestroy` ordering is reversed — shutdown race protection is absent

**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:311-314`
**Also:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:303-306`

**Issue:** The comment on `uninstallLogbackAppender()` states that `@PreDestroy` runs
**before** the SDK bean's `destroyMethod="close"`. This is incorrect. In Spring,
`@Bean`-produced beans are destroyed in reverse initialization order; the
`@Configuration` class bean is initialized first (before its `@Bean` factory methods
are invoked) and therefore destroyed **last**. The call sequence during shutdown is:

1. `openTelemetry` bean → `destroyMethod="close"` → SDK shuts down, exporters closed
2. `OtelSdkConfiguration` bean → `@PreDestroy` → `install(noop)` — **too late**

The protective intent — swap the appender's reference to a no-op SDK *before* the
real SDK closes so racing log events are safely discarded rather than hitting a closed
exporter — is not achieved. Any log emitted by an AMQP listener or thread pool between
`close()` completing (step 1) and `install(noop)` running (step 2) may hit a closed
exporter and either silently drop or generate a `BatchLogRecordProcessor` error log.

In practice the window is very short on graceful JVM shutdown, but the stated guarantee
in the comment is wrong and the protection is absent.

**Fix:** Move `install(noop)` into the `openTelemetry()` `@Bean`'s own teardown path,
not a `@PreDestroy` on the configuration class. One pattern:

```java
// In openTelemetry() @Bean factory, after building the sdk:
OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
    // ...
    .build();

// Register a JVM shutdown hook that runs install(noop) immediately when
// Spring calls close() on the SDK, before the processors drain.
// Alternatively: switch to a custom @Bean destroyMethod on a wrapper that
// calls install(noop) first then sdk.close().
```

A simpler structural fix is to wrap the SDK in a tiny holder class whose
`close()` calls `OpenTelemetryAppender.install(OpenTelemetry.noop())` then
delegates to `sdk.close()`. That holder becomes the `@Bean(destroyMethod="close")`
target, so install(noop) is guaranteed to precede sdk.close() in a single call stack.

---

### WR-02: `postgres:17-alpine` and `valkey/valkey:8.1-alpine` are floating tags that bypass `verify:images`

**File:** `docker-compose.yml:48` (postgres), `docker-compose.yml:34` (valkey)

**Issue:** The `verify:images` script's floating-tag regex only rejects `:latest`,
`:NN` (major-only at end of line), and `:NN.NN` (minor-only at end of line). It does
**not** reject `:NN-suffix` or `:NN.NN-suffix` patterns where a flavor string follows.

- `postgres:17-alpine` matches `:NN-suffix` — this is a **major-only** tag. Docker
  Hub resolves it to the latest PostgreSQL 17.x patch. Workshop cohorts running at
  different times may pull 17.4, 17.5, etc. The STACK-02 / D-14 invariant is violated
  silently.
- `valkey/valkey:8.1-alpine` matches `:NN.NN-suffix` — minor-only tag. Valkey 8.1.0
  → 8.1.1 etc. will silently drift.

The `verify:images` acceptable-patterns comment also has a typographic error: it lists
`- :NN.NN.NN-suffix (e.g., :17-alpine)` where `:17-alpine` is `NN-suffix`, not
`NN.NN.NN-suffix`. This misleads both readers and future regex maintainers.

**Fix:**

```yaml
# docker-compose.yml
valkey:
  image: valkey/valkey:8.1.3-alpine   # pin to exact patch

postgres:
  image: postgres:17.5-alpine         # pin to major.minor.patch
```

Extend the `verify:images` floating-tag regex to also reject `:NN-suffix` patterns:

```bash
FLOATING=$(printf '%s\n' "$LINES" | grep -E \
  '(image:[[:space:]]*[^:[:space:]]+[[:space:]]*$|:latest[[:space:]]*$|:[0-9]+[[:space:]]*$|:[0-9]+\.[0-9]+[[:space:]]*$|:[0-9]+-[^[:space:]]+[[:space:]]*$)' \
  || true)
```

Also fix the misleading comment: `- :NN.NN.NN-suffix (e.g., :17-alpine)` should read
`- :NN-suffix (e.g., :17-alpine)  # flavor suffix; NOT floating only if already pinned to patch`.

---

### WR-03: Port 8889 mapped in docker-compose but no listener configured in otelcol-config.yaml

**File:** `docker-compose.yml:111`

**Issue:** The otel-collector service maps `"8889:8889"` with the comment
`# prometheus_exporter receiver (Phase 11 use)`. However, `otelcol-config.yaml`
contains no `prometheus` exporter component and no pipeline that exposes port 8889.
The only Prometheus-format endpoint configured is the **telemetry self-metrics** pull
reader at port 8888.

Any attendee following the README Step 10 port table, which documents port 8889 as
`otel-collector prometheus exporter scrape target`, who tries
`curl localhost:8889/metrics` will get a connection refused. If `preflight` checks
port 8889 as "must be free", a legitimate service already on 8889 will cause a
spurious `infra:up` failure that the user cannot resolve by stopping a real process.

**Fix:** Either:
1. Remove the `"8889:8889"` port mapping from docker-compose until Phase 11 actually
   adds the `prometheusexporter` pipeline to `otelcol-config.yaml`.
2. Or add a code comment to docker-compose that explicitly flags this as a
   forward-only placeholder not yet wired: `# PLACEHOLDER: no listener until Phase 11 adds prometheusexporter pipeline`.

The README Step 10 port table entry for 8889 should be marked `(Phase 11 only — not active in Phase 10)`.

---

### WR-04: `preflight` port check silently skips all validation on macOS

**File:** `mise.toml:66`

**Issue:** The port availability check uses `ss -tln 2>/dev/null` which is Linux-only.
On macOS, `ss` does not exist; the command fails silently (output suppressed by
`2>/dev/null`) and produces empty output. `grep -q ":${port} "` then returns false
(port not found = no output to match), so the check always reports every port as
free regardless of actual state.

The CLAUDE.md specifies JDK distribution as Amazon Corretto (workshop default), but
the workshop targets developers on laptops where macOS is a common platform. The README
port table is explicitly aimed at helping attendees avoid collisions with React/Next.js
dev servers — a problem that is more common on macOS than Linux workshop machines.

**Fix:** Add a macOS fallback:

```bash
for port in 3000 4317 4318 5672 15672 15692 6379 5432 3200 9009 3100 13133 8888 8889; do
  if ss -tln 2>/dev/null | grep -q ":${port} " || \
     lsof -iTCP:"${port}" -sTCP:LISTEN -n -P 2>/dev/null | grep -q LISTEN; then
    echo "ERROR: Port ${port} is in use. Run: lsof -i:${port} to find the process."
    exit 1
  fi
  echo "  ${port}: free"
done
```

`lsof -iTCP:PORT -sTCP:LISTEN` works on both macOS and Linux and is already cited in
the error message as the diagnostic tool for a found collision.

---

### WR-05: `docs/screenshots/step-04-metrics.png` is missing but README claims it exists

**File:** `README.md:548`

**Issue:** Line 548 states:
> **Verification screenshot:** `docs/screenshots/step-04-metrics.png` is the post-decomposition Grafana metrics-panel capture (PREREQ-02 closure)

The file does not exist in `docs/screenshots/` (confirmed by directory listing). A
separate TODO comment at line 222 correctly acknowledges the screenshot is deferred.
The line 548 claim contradicts both the actual filesystem state and the TODO at line 222.

Any reader clicking on the link or relying on the text as evidence that PREREQ-02 is
visually validated will be misled.

**Fix:** Either:
1. Capture and commit the screenshot, resolving the line-222 TODO.
2. Replace line 548 with: `Verification screenshot pending (PREREQ-02 / DOC-04 deferred — see TODO at Step 4).`

---

## Info

### IN-01: Multiple stale `otel-lgtm` references in README prerequisites section

**File:** `README.md:3,9,58,93`

**Issue:** The README headline (line 3), prerequisites narrative (line 9), and "First
run" code blocks (lines 58, 93) still describe the infrastructure as
`"RabbitMQ + grafana/otel-lgtm"` and claim "two infrastructure containers". Phase 10
replaces lgtm with seven containers (RabbitMQ + Valkey + Postgres + redis-exporter +
postgres-exporter + otel-collector + tempo + mimir + loki + grafana = 10 total; 5 of
those are the observability stack). The Step 1 and Step 2 "Run" sections have the same
stale comment on `mise run infra:up`.

This is the first text a workshop attendee reads; the mismatch between the description
and what `docker compose up` actually starts will cause immediate confusion.

**Fix:** Update line 3 to `"...to a five-component Grafana observability stack"` or
similar. Update line 9 and the inline comments on lines 58/93 to reflect the current
container count (e.g., `# starts RabbitMQ + observability stack (5 containers)`).

---

### IN-02: `infra:up` and `infra:down` task descriptions are stale

**File:** `mise.toml:80,84`

**Issue:**
- `infra:up` description: `"Start RabbitMQ + grafana/otel-lgtm in docker-compose"` — lgtm no longer in use.
- `infra:down` description: `"Stop infra (preserves Grafana state via lgtm-data volume)"` — `lgtm-data` volume no longer exists; the new Grafana state volume is `grafana-data`.

Running `mise run --list-all` shows these descriptions to every attendee.

**Fix:**
```toml
[tasks."infra:up"]
description = "Start all infra containers (RabbitMQ, Valkey, Postgres, OTel stack)"

[tasks."infra:down"]
description = "Stop infra (preserves Grafana state via grafana-data volume)"
```

---

### IN-03: App ports 8080/8081 not checked in `preflight` despite being listed as required in README

**File:** `mise.toml:65` (preflight port loop)

**Issue:** The README "Required free ports" table lists ports 8080 (producer) and 8081
(consumer) as required. The `preflight` port loop does not include them. If 8080 is
occupied (e.g., by an existing dev server), `mise run preflight` reports `ALL GREEN`
and `infra:up` succeeds, but `dev:producer` fails with a `Port in use` Tomcat error
that is confusing without the preflight context.

The comment in the preflight task explains 14 ports covering infrastructure (D-11), but
does not address the application ports at all.

**Fix:** Add 8080 and 8081 to the preflight port loop and update the comment from "14
ports — 5 v1.0 + 1 oversight + 8 v2.0 additions" to "16 ports — ... + 2 app ports".

---

### IN-04: `this.openTelemetry` field is never read — dead code with documented intent

**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:120`
**Also:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:112`

**Issue:** The `private OpenTelemetry openTelemetry` field is assigned in the `@Bean`
factory body (`this.openTelemetry = sdk`) but is never read anywhere in the class.
The comment explicitly acknowledges this ("NOT consumed elsewhere on this class today").
This is intentional dead code introduced by PREREQ-01 as a forward-looking placeholder.

As workshop source code read by attendees, the dead field is slightly confusing alongside
the highly-commented codebase style. Future maintainers who do not read the Javadoc
carefully may delete it, breaking whatever future use it was reserved for.

**Fix:** Either use it in `uninstallLogbackAppender()` (see WR-01 fix, which would
make the field load-bearing), or keep the comment but add a `@SuppressWarnings("unused")`
annotation so IDEs do not flag it and so the intent is explicit at the call site.

---

### IN-05: `verify:images` comment has a typographic error in the acceptable-patterns list

**File:** `mise.toml:400`

**Issue:** The third acceptable-pattern entry reads:
```
#   - :NN.NN.NN-suffix (e.g., :17-alpine)
```
The example `:17-alpine` is a `NN-suffix` pattern (major + flavor), not `NN.NN.NN-suffix`
(full-patch + flavor). The mismatch makes the comment misleading and also masks the real
issue: the script does not catch `NN-suffix` as floating (see WR-02).

**Fix:** Correct the comment to:
```
#   - :NN.NN.NN-suffix (e.g., :3.7.1-alpine)   # full patch + flavor — not floating
#   - :NN-suffix       (e.g., :17-alpine)       # WARNING: major-only + flavor = still floating
```

---

### IN-06: Collector self-metrics (port 8888) not scraped by any Prometheus job

**File:** `infra/observability/otelcol-config.yaml:145-162`

**Issue:** The `telemetry.metrics` block configures the Collector's own Prometheus-format
self-metrics to be exposed at `:8888`. The README Step 10 notes that attendees can
`curl localhost:8888/metrics`. However, the `prometheus` receiver's `scrape_configs`
in `otelcol-config.yaml` does not include a scrape job for `localhost:8888` (or
`otel-collector:8888`). The Collector's self-metrics are therefore exposed as a raw
Prometheus endpoint that workshop attendees can curl manually but are **not ingested
into Mimir** and will not appear in any dashboard.

This may be intentional (Collector self-observability is out of scope for Phase 10),
but it is inconsistent with the stated rationale: "Workshop attendees can `curl
localhost:8888/metrics` to see how the Collector profiles its own pipelines." If the
intent is to display these metrics in Grafana, a scrape job is missing.

**Fix:** If ingestion into Mimir is desired, add a scrape job:

```yaml
# otelcol-config.yaml — inside prometheus.config.scrape_configs:
- job_name: otelcol
  static_configs:
    - targets: ['localhost:8888']
      labels:
        source: infra-exporter
```

If the intent is purely curl-for-learning (not Grafana-visible), add a comment in
`otelcol-config.yaml` clarifying that port 8888 is for manual inspection only and
is not scraped.

---

_Reviewed: 2026-05-02T21:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
