---
phase: 12-exemplars-metrics-to-trace-click-through
reviewed: 2026-05-03T22:30:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - grafana/dashboards/ose-otel-demo.json
  - infra/observability/mimir-config.yaml
  - infra/observability/otelcol-config.yaml
  - mise.toml
  - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - README.md
findings:
  critical: 2
  warning: 4
  info: 5
  total: 11
status: issues_found
---

# Phase 12: Code Review Report

**Reviewed:** 2026-05-03T22:30:00Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Phase 12 adds `ExemplarFilter.traceBased()` to both `SdkMeterProvider` builders, enables Mimir exemplar storage via `max_global_exemplars_per_user: 100000`, adds an Exemplars dashboard row with `exemplar: true` on histogram queries, and restructures `HttpServerSpanFilter.doFilterInternal()` to call `scope.close()` after `requestDuration.record()` so the span is still current when the SDK samples an exemplar.

The scope-ordering fix in `HttpServerSpanFilter` is the most semantically load-bearing change and it is correct. The `ExemplarFilter.traceBased()` addition is correctly placed in both `OtelSdkConfiguration` files and is symmetric. The Mimir limits block is correctly spelled and will work.

Two **BLOCKER** defects require attention before this phase ships:

1. `HttpServerSpanFilter` calls `span.end()` before `scope.close()`. The OTel specification requires the scope to close before the span ends, and violating this ordering can cause the SDK to orphan the span context on the current thread — leading to incorrect exemplar sampling on high-concurrency Tomcat threads (the exact failure the scope-reorder was meant to prevent).
2. The `verify:exemplars` task uses GNU `date -d` syntax, which is Linux-only and silently produces garbage date strings on macOS workshop laptops, causing the Mimir exemplar query to always return empty data.

Four **WARNING** items cover the hardcoded `guest` RabbitMQ password in `mise.toml`, a dashboard `traceid` variable regex that silently matches nothing on first load, a missing `node` version pin (violating the workshop's pinned-version invariant), and the `Exemplars (Phase 12)` row being permanently `collapsed: false` which pushes older rows off the initial viewport.

---

## Critical Issues

### CR-01: `span.end()` fires before `scope.close()` — violates OTel context contract; exemplar context orphaned on error path

**File:** `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java:217-219`

**Issue:** The `finally` block closes resources in the wrong order:

```java
finally {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    requestDuration.record(seconds, Attributes.of(...));
    span.end();
    // close scope AFTER record() so ExemplarFilter.traceBased() sees active span
    scope.close();   // <-- scope closed AFTER span.end()
}
```

The comment acknowledges that `scope.close()` must happen after `record()` — but it must also happen BEFORE `span.end()`. The OTel specification (and the Java SDK implementation) requires that a `Scope` be closed before its associated span is ended. Closing the scope after `span.end()` leaves the span context installed on the thread's `Context` stack after the span has moved to a terminal state. On a shared Tomcat thread pool this means the next request on the same thread inherits the dead span as its current context until that thread processes another request that makes a new span current, producing incorrect parent-span linkage on subsequent spans and defeating the very exemplar fix Phase 12 is introducing.

The `record()` call correctly fires while the span is still active (before either `span.end()` or `scope.close()`). The fix is simply to move `scope.close()` before `span.end()`:

**Fix:**
```java
finally {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

    // record() fires while span is current — ExemplarFilter.traceBased()
    // sees an active span and attaches trace_id / span_id.
    requestDuration.record(seconds, Attributes.of(
        HttpAttributes.HTTP_REQUEST_METHOD, method,
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (long) response.getStatus()));

    // Close scope BEFORE span.end() — OTel spec requirement.
    // scope.close() pops this span from the thread's Context stack;
    // span.end() moves the span to terminal state.
    // Reversing the order leaves a dead span on the Context stack.
    scope.close();
    span.end();
}
```

---

### CR-02: `verify:exemplars` uses GNU `date -d` — silent failure on macOS produces wrong timestamps; task always reports no exemplars

**File:** `mise.toml:575-576`

**Issue:** The `verify:exemplars` task computes the query window with:

```bash
START=$(date -u -d '10 minutes ago' '+%Y-%m-%dT%H:%M:%SZ')
END=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
```

`date -d '10 minutes ago'` is a GNU coreutils extension. On macOS (BSD `date`), `-d` is not the date-string flag; BSD `date -d` takes a Unix timestamp in seconds. The result on macOS is either an error message or a garbage date string assigned to `START`. When Mimir receives an unparseable `start=` parameter it either rejects the request (400) or interprets it as epoch 0, returning an empty data set.

The workshop README lists macOS as a supported host platform (the port table uses `brew install git`; `mise.toml` tasks reference `open` as the macOS fallback for `xdg-open`). An attendee on macOS who runs `mise run verify:exemplars` after generating load will always see the "no exemplars" error path even when the pipeline is fully functional — a false negative that wastes debugging time.

**Fix:** Use portable relative-time arithmetic or use `date` in a cross-platform manner:

```bash
# Portable: compute epoch seconds with arithmetic, then format.
NOW=$(date -u '+%s')
START_EPOCH=$(( NOW - 600 ))

# GNU date:   date -u -d "@${START_EPOCH}" '+%Y-%m-%dT%H:%M:%SZ'
# BSD date:   date -u -r "${START_EPOCH}"  '+%Y-%m-%dT%H:%M:%SZ'
# Cross-platform via Python (always available if mise installs JDK):
START=$(python3 -c "import datetime; print((datetime.datetime.utcnow() - datetime.timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
END=$(python3   -c "import datetime; print(datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ'))")
```

Alternatively, use Mimir's `relative` time shorthand which is always supported: `?start=now-10m&end=now`, replacing the `${START}` and `${END}` variables in the curl call entirely.

---

## Warnings

### WR-01: Hardcoded `guest` password in `mise.toml` env block — captured in shell history and `.env`-style exports

**File:** `mise.toml:23`

**Issue:**

```toml
SPRING_RABBITMQ_PASSWORD = "guest"
```

`mise.toml` is committed to source control. The `[env]` block is exported into every shell session that activates this mise project. Tooling that evaluates project env vars (shell history, IDE debuggers, secrets scanners) will capture `SPRING_RABBITMQ_PASSWORD=guest` as a literal string. While `guest` is a well-known default, the pattern teaches attendees that hardcoding service passwords in committed TOML is acceptable — a habit that causes production incidents when this template is copied. The CLAUDE.md security rules explicitly require "NEVER hardcode secrets in source code" and "ALWAYS use environment variables or a secret manager."

**Fix:** Move the password to a local `.env.local` or `mise.local.toml` (git-ignored) and document the pattern in the README setup steps. If a committed default is required for workshop convenience, add an explicit comment marking it as a workshop-only non-secret:

```toml
# WORKSHOP-ONLY: RabbitMQ default credentials. Never use in production.
# Override via SPRING_RABBITMQ_PASSWORD env var or a gitignored mise.local.toml.
SPRING_RABBITMQ_PASSWORD = "guest"
```

At minimum, document that this pattern must not be copied to production deployments.

---

### WR-02: Dashboard `traceid` variable default `^$` matches zero log lines silently — no visible error to attendee

**File:** `grafana/dashboards/ose-otel-demo.json:613`

**Issue:** The `traceid` template variable has `"query": "^$"` and `"value": "^$"` as the default. The Loki panel in the top row uses:

```
{service_name=~"order-.*"} | trace_id=~`$traceid`
```

When `$traceid` is `^$`, Loki evaluates `trace_id=~"^$"` — a regex that matches only log lines where `trace_id` is the empty string. OTel-stamped log lines from the Logback appender always carry a 32-hex trace_id, so the panel always returns zero results without any error message. An attendee who opens the dashboard and sees an empty Logs panel has no way to distinguish "pipeline not working" from "template variable needs a trace ID."

The dashboard description (line 620) documents this behavior: "the panel renders empty until you paste a 32-hex trace_id." But this explanation is only visible if the attendee reads the variable's description tooltip — not visible on the panel face itself.

**Fix:** Add a panel-level description or empty-state label that explains the behavior. A stronger fix is to change the default regex so that without a value the query degrades gracefully to "show recent logs" rather than "show nothing":

```json
"expr": "{service_name=~\"order-.*\"} | trace_id=~`${traceid:pipe}`"
```

Or, if the intent is strictly to require a trace ID before showing anything, replace the empty Logs panel with a placeholder text panel that says "Paste a trace_id from the Recent Traces panel above."

---

### WR-03: `node = "lts"` in `mise.toml` is a floating version — violates the workshop's pinned-version invariant (`verify:images`, `verify:bom`)

**File:** `mise.toml:13`

**Issue:**

```toml
node  = "lts"
```

`lts` is a moving alias. The workshop's version-pinning invariant is established explicitly in `verify:images` ("Floating tags break clones across cohorts") and enforced for Docker image tags. The same invariant applies to mise-managed toolchain versions — `java = "corretto-17.0.13.11.1"` and `maven = "3.9.11"` and `oha = "1.14.0"` are all exact versions. `node = "lts"` is not. A workshop cohort that clones the repo six months later may resolve `lts` to a different Node major version than the current cohort, breaking the `scripts/screenshots/` Playwright capture if a new Node major has a breaking change.

**Fix:** Pin to an exact Node version matching what is currently resolved:

```bash
mise current node  # prints the current resolved version, e.g. 22.14.0
```

Then update `mise.toml`:

```toml
node = "22.14.0"   # or whichever version mise resolves as lts today
```

---

### WR-04: `Exemplars (Phase 12)` row is `collapsed: false` — pushes "Tail Sampling diagnostics" and other rows off the initial viewport

**File:** `grafana/dashboards/ose-otel-demo.json:547-555`

**Issue:** The Exemplars row is defined as:

```json
{
  "collapsed": false,
  "id": 15,
  "title": "Exemplars (Phase 12)",
  "type": "row"
}
```

The row at `y: 40` with `collapsed: false` expands and pushes every row below it further down the page. Phase 12 is the newest feature and is placed below the Tail Sampling diagnostics rows (which are `collapsed: true` at `y: 21`). The Exemplars row being open by default means anyone loading the dashboard after a `mise run infra:reset` will see a large empty panel area (exemplar dots only appear when load is actively running), while the always-useful top strip (Recent Traces, RED Metrics, Logs) remains visible.

More practically: the Tail Sampling diagnostics row at `id: 9` is `collapsed: true` but now sits visually above the Exemplars row in the panel list (lower `y`). With the Exemplars row open the page length increases and projector-size workshop demos require extra scrolling.

**Fix:** Change the Exemplars row's default to `"collapsed": true` so it matches the convention established by the Tail Sampling diagnostics row:

```json
{
  "collapsed": true,
  "id": 15,
  "title": "Exemplars (Phase 12)",
  "type": "row"
}
```

The README Step 12 "What to look for" section already says "Look for the 'Exemplars (Phase 12)' row — it is open by default" — update that line if the default changes.

---

## Info

### IN-01: `OtelSdkConfiguration` — mutable instance field `openTelemetry` without `volatile` is not thread-safe in the Spring `@Configuration` lifetime

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:120` (identical in consumer)

**Issue:**

```java
@SuppressWarnings("unused")
private OpenTelemetry openTelemetry;
```

This field is assigned inside `openTelemetry()` (`this.openTelemetry = sdk`) and is not declared `volatile`. While Spring's `@Configuration` beans are singletons and the assignment occurs during context initialization (single-threaded), the field is explicitly described as "forward-looking for any future @EventListener" use on concurrent threads. Any such future use would encounter a missing memory-visibility guarantee without `volatile`. The comment's own rationale for why the field exists makes the missing `volatile` a latent defect.

**Fix:** Declare the field `volatile` or `final` (the latter requires constructor assignment, which is incompatible with the current `@Bean` factory shape). `volatile` is the minimal fix:

```java
@SuppressWarnings("unused")
private volatile OpenTelemetry openTelemetry;
```

---

### IN-02: `CloseableOpenTelemetrySdk` cast `(OpenTelemetrySdk) openTelemetry` is unchecked — fails at runtime if any future code provides a non-SDK `OpenTelemetry` bean

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:323` (identical in consumer)

**Issue:**

```java
@Bean(destroyMethod = "close")
CloseableOpenTelemetrySdk openTelemetryShutdownGuard(OpenTelemetry openTelemetry) {
    return new CloseableOpenTelemetrySdk((OpenTelemetrySdk) openTelemetry);
}
```

The `(OpenTelemetrySdk)` cast is unchecked and will throw a `ClassCastException` at application startup if any future test configuration or autoconfigure override provides an `OpenTelemetry` implementation that is not an `OpenTelemetrySdk`. This pattern is fragile for a workshop that demonstrates how to swap the SDK in tests (`TestOtelConfiguration` as described in the README step 6 section).

**Fix:** Accept `OpenTelemetrySdk` directly as the parameter type, which Spring will resolve to the same bean since the `openTelemetry()` @Bean method's return type is `OpenTelemetry` but the underlying object is `OpenTelemetrySdk`:

```java
@Bean(destroyMethod = "close")
CloseableOpenTelemetrySdk openTelemetryShutdownGuard(OpenTelemetrySdk sdk) {
    return new CloseableOpenTelemetrySdk(sdk);
}
```

If Spring cannot satisfy `OpenTelemetrySdk` by type (because the bean is declared as `OpenTelemetry`), use a qualifier or rename the `openTelemetry()` method's return type to `OpenTelemetrySdk`.

---

### IN-03: `otelcol-config.yaml` metrics pipeline missing `transform/copy_recordpolicy` — only trace pipeline gets it

**File:** `infra/observability/otelcol-config.yaml:377-381`

**Issue:** The `transform/copy_recordpolicy` processor is defined and wired into the `traces` pipeline but not into the `metrics` or `logs` pipelines:

```yaml
traces:
  processors: [memory_limiter, tail_sampling, transform/copy_recordpolicy, batch]

metrics:
  processors: [memory_limiter, batch]  # no transform/copy_recordpolicy

logs:
  processors: [memory_limiter, batch]  # no transform/copy_recordpolicy
```

This is almost certainly intentional (the processor copies tail-sampling scope attributes onto spans — metrics and logs do not go through `tail_sampling`), but the processor's name and placement create a readability trap: a reader unfamiliar with Phase 11 might wonder whether metrics and logs also need the copy. A short comment at the metrics/logs processor lists would resolve the ambiguity.

**Fix:** Add an inline comment:

```yaml
metrics:
  # NOTE: transform/copy_recordpolicy is traces-only (copies tail_sampling scope
  # attribute onto spans); metrics and logs bypass tail_sampling entirely.
  processors: [memory_limiter, batch]
```

---

### IN-04: `README.md` port table at "Required free ports" (Step 1) is incomplete relative to `mise run preflight`

**File:** `README.md:28-37`

**Issue:** The "Required free ports" table in the Prerequisites section lists 7 ports (3000, 4317, 4318, 5672, 15672, 8080, 8081). The `preflight` task and the Step 10 host-port table enumerate 14 ports (adds 15692, 6379, 5432, 3200, 9009, 3100, 13133, 8888, 8889). The discrepancy means a Step-1 attendee following the README table who has, for example, a local Postgres on port 5432 or Mimir-compatible service on 9009 will see a confusing `mise run preflight` error that contradicts the port table they just read.

**Fix:** Update the "Required free ports" table in the Prerequisites section to match the full 14-port set checked by `mise run preflight`, or add a note that additional ports are required from Step 10 onwards.

---

### IN-05: `verify:exemplars` jq assertion silently passes if Mimir returns no data with unexpected JSON shape

**File:** `mise.toml:583`

**Issue:**

```bash
if ! printf '%s' "$RESULT" | jq -e '.data | length > 0 and (.[0].exemplars | length > 0) and (.[0].exemplars[0].labels.trace_id != null)' >/dev/null 2>&1; then
```

The `2>&1` redirect swallows jq parse errors. If Mimir returns an unexpected JSON shape (e.g., an error envelope `{"status":"error","error":"..."}`) jq will produce a type error on `.data | length > 0`, exit non-zero, and the `if !` branch will print the "no exemplars" error — which is the correct behavior. However, if Mimir returns valid JSON that does not match the expected schema at a deeper path (e.g., `.data[0].exemplars` is absent rather than empty), `jq -e` returns false (because the path resolves to null, and `null and ...` is false in jq), the error branch fires with a "no exemplars" message, and the actual cause (unexpected schema) is hidden.

This is a diagnostic quality issue: the error message says "no exemplars" when the real issue might be a changed Mimir API shape. Separating the "got valid data?" check from the "has trace_id?" check would produce a more actionable error.

**Fix:** Split the assertion:

```bash
# Step 1: verify Mimir responded with a parseable exemplar response.
if ! printf '%s' "$RESULT" | jq -e 'type == "object" and .status == "success" and (.data | type) == "array"' >/dev/null 2>&1; then
  echo "ERROR: Mimir returned an unexpected response shape (not the exemplar API envelope)"
  echo "Mimir response: $RESULT"
  exit 1
fi

# Step 2: verify exemplars are present with trace_id.
if ! printf '%s' "$RESULT" | jq -e '.data | length > 0 and (.[0].exemplars | length > 0) and (.[0].exemplars[0].labels.trace_id != null)' >/dev/null 2>&1; then
  echo "ERROR: no exemplars with trace_id found..."
  ...
fi
```

---

_Reviewed: 2026-05-03T22:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
