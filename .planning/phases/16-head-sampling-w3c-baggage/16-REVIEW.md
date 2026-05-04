---
phase: 16-head-sampling-w3c-baggage
reviewed: 2026-05-04T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - integration-tests/src/test/java/com/example/e2e/TestOtelHolder.java
  - mise.toml
  - otel-bootstrap/pom.xml
  - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
  - otel-bootstrap/src/main/java/com/example/otel/context/BaggageSpanAttributeProcessor.java
  - producer-service/src/main/java/com/example/producer/api/OrderController.java
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - README.md
  - scripts/load.sh
findings:
  critical: 2
  warning: 3
  info: 3
  total: 8
status: issues_found
---

# Phase 16: Code Review Report

**Reviewed:** 2026-05-04
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

This review covers Phase 16's head sampling (`traceIdRatioBased(0.5)`) and W3C baggage
(`customer-tier`) implementation. The OTel SDK wiring in both `OtelSdkConfiguration.java`
files and the `BaggageSpanAttributeProcessor` are structurally correct. The `TracingMessageListenerAdvice`
baggage propagation shape (outer `ctxScope` before the span scope) is correct per the W3C
baggage spec.

Two blockers were found: an unsanitized HTTP request header written verbatim into W3C baggage
(security), and the baggage stream's background process leaking on Ctrl-C because `PID_BAGGAGE`
is omitted from the `cleanup()` function. Three warnings cover a stale comment/default
contradiction in `load.sh`, null-dereference risk on AMQP span naming, and a misleading
`verify:head-sampling` gate description. Three info items address documentation and naming
drift introduced in Phase 16.

---

## Critical Issues

### CR-01: Unsanitized HTTP header value written directly into W3C baggage

**File:** `producer-service/src/main/java/com/example/producer/api/OrderController.java:31-56`

**Issue:** `X-Customer-Tier` is a caller-supplied HTTP request header. Its value is accepted
with `defaultValue = "standard"` but is otherwise unvalidated and written verbatim into
W3C baggage via `Baggage.builder().put("customer-tier", customerTier)`. The W3C Baggage
spec allows arbitrary byte sequences in values (subject only to percent-encoding). An attacker
can send `X-Customer-Tier: gold, injected-key=injected-value` and the `W3CBaggagePropagator`
will forward the injected key to all downstream services — including the AMQP consumer
and any HTTP notification endpoint reached via `TracingClientHttpRequestInterceptor`. If any
downstream reads baggage by key and acts on it, the attacker controls that value. Even without
a downstream reader, the injected baggage entry becomes a span attribute (`baggage.injected-key`)
on every span in the trace because `BaggageSpanAttributeProcessor` uses an allowlist on **key**
names but does NOT validate the **value** of the allowed key — a value containing newline
characters (`\r\n`) can corrupt structured log or trace attribute storage.

The README's "Production-readiness callout" at Step 5 acknowledges unvalidated payload logging
but does not extend the same warning to the baggage-header injection path that Phase 16 added.

**Fix:** Validate `customerTier` against an explicit allowlist of known values before placing
it into baggage. Reject or default any value not in the list:

```java
private static final Set<String> ALLOWED_TIERS = Set.of("gold", "silver", "standard");

@PostMapping
public ResponseEntity<Map<String, String>> create(
        @RequestBody Map<String, Object> payload,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
        @RequestHeader(value = "X-Customer-Tier", required = false, defaultValue = "standard")
            String customerTierRaw) {

    // Allowlist validation — reject unrecognized tier values silently by normalizing to "standard".
    String customerTier = ALLOWED_TIERS.contains(customerTierRaw) ? customerTierRaw : "standard";

    // ... rest of method unchanged
    Baggage baggage = Baggage.builder().put("customer-tier", customerTier).build();
```

---

### CR-02: PID_BAGGAGE process not included in cleanup — leaks on Ctrl-C / SIGTERM

**File:** `scripts/load.sh:90-100,208-226`

**Issue:** Phase 16 adds a "baggage stream" background subshell that is assigned to
`PID_BAGGAGE` at line 226. The `cleanup()` function (lines 90-100) kills children by
iterating over a hard-coded list: `PID_EXPRESS`, `PID_STANDARD`, `PID_IDEMPOTENT`,
`PID_BURST_LOOP`, `PID_HEARTBEAT`, `PID_SLOW`. `PID_BAGGAGE` is absent from that list.

Consequences when the user presses Ctrl-C (SIGINT) or when the process receives SIGTERM:

1. The baggage curl loop (`while :; do curl ...; sleep ...; done`) is orphaned — it
   continues sending requests to `localhost:8080/orders` until the terminal is closed or
   the process is manually killed.
2. The `wait 2>/dev/null` at the end of `cleanup()` does not include `PID_BAGGAGE`, so the
   cleanup function returns while the baggage subshell is still alive.
3. When `BAGGAGE_RPS` is at its default of `3`, each missed kill means 3 extra requests
   per second persist invisibly after the user believes they stopped the load.

This is a behavioral bug that will reliably appear every time a workshop attendee presses
Ctrl-C during a live demo.

**Fix:** Add `PID_BAGGAGE` to both the `pkill -P` block (for the loop's children) and
the `kill` list in `cleanup()`, and add it to the final `wait` call if long-running:

```bash
cleanup() {
  [[ -n "${PID_BURST_LOOP:-}" ]] && pkill -P "$PID_BURST_LOOP" 2>/dev/null || true
  [[ -n "${PID_IDEMPOTENT:-}" ]]  && pkill -P "$PID_IDEMPOTENT"  2>/dev/null || true
  [[ -n "${PID_BAGGAGE:-}" ]]     && pkill -P "$PID_BAGGAGE"     2>/dev/null || true  # <-- add
  for pid in "${PID_EXPRESS:-}" "${PID_STANDARD:-}" "${PID_IDEMPOTENT:-}" \
             "${PID_BURST_LOOP:-}" "${PID_HEARTBEAT:-}" "${PID_SLOW:-}" \
             "${PID_BAGGAGE:-}"; do   # <-- add PID_BAGGAGE here
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
```

---

## Warnings

### WR-01: Null dereference on AMQP span name when exchange is null

**File:** `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java:113-121`

**Issue:** `exchange` is obtained from `props.getReceivedExchange()` (line 113), which
returns `null` when the message was published to the default exchange (empty string `""`)
or when the message properties have not been fully populated (e.g., in certain test or
dead-letter scenarios). At line 121, `exchange + " process"` performs a Java string
concatenation on a potentially null reference. While `String + null` in Java produces the
literal string `"null process"` (not a `NullPointerException`), this is a silent data
corruption: Tempo will index the span under the name `"null process"` with
`messaging.destination.name = null` (line 127, `setAttribute` with a null String is a
no-op in OTel SDK, so the attribute is dropped entirely). Workshop attendees debugging a
dead-letter or default-exchange scenario will see a span with no destination name and a
misleading `"null process"` operation name.

The same issue applies to `routingKey` at line 131: `setAttribute(..., null)` is silently
dropped. For the workshop's happy path both fields are populated, but the silent failure
mode is a trap for anyone extending the demo.

**Fix:** Guard with empty-string fallback before span construction:

```java
String exchange   = Optional.ofNullable(props.getReceivedExchange())
                            .filter(s -> !s.isEmpty())
                            .orElse("(default)");
String routingKey = Optional.ofNullable(props.getReceivedRoutingKey())
                            .orElse("(unknown)");
```

---

### WR-02: BURST_RPS default contradicts header comment — burst fires by default

**File:** `scripts/load.sh:83-84`

**Issue:** Line 83 carries the comment `# Burst stream — disabled by default.` but
line 84 sets `BURST_RPS="${BURST_RPS:-50}"`. Since `50 > 0`, the `if [[ "$BURST_RPS" -gt 0 ]]`
guard at line 231 evaluates as true, and the burst loop IS active by default. The header
comment at line 37 also states `Defaults: ~200 rps total (100 per priority) + 5 rps idempotent
+ no burst.` and the override list at line 34 shows `BURST_RPS (0=off)` — both confirming the
intent was for burst to be off by default.

The consequence: every `mise run load` without explicit environment overrides fires a 50 rps
burst every 100 seconds against `POST /orders`. For a workshop laptop running a single-instance
producer and consumer this is unexpected background load. The Phase 16 `load.sh` header and
the in-script comment state "disabled by default" but the code contradicts that.

**Fix:** Change the default to 0 to match the documented intent:

```bash
# Burst stream — disabled by default. Set BURST_RPS>0 to enable.
BURST_RPS="${BURST_RPS:-0}"
```

---

### WR-03: verify:head-sampling task description claims 50% invariant but only asserts ≥1 trace

**File:** `mise.toml:665-700`

**Issue:** The task `[tasks."verify:head-sampling"]` carries the description: `"Phase 16
invariant: ~50% of traces reach Tempo under head sampling (traceIdRatioBased(0.5))"`. The
actual assertion in the task body is `jq -e '.traces | length > 0'` — it passes if Tempo
contains at least ONE trace from `order-producer`. This succeeds even if head sampling is
misconfigured as `alwaysOn()` (100% sampling) or if the sampler ratio is set to 0.99.

The task description misleads workshop attendees and CI maintainers into believing the gate
enforces the 50% constraint. A broken sampler (e.g., accidentally reverted to `alwaysOn()`)
will produce a GREEN result from `mise run verify:head-sampling` and give false confidence.

The comment inside the task acknowledges the limitation: "The ~50% ratio is a probabilistic
property verified by the README demo ... not by this automated gate." But the task
`description` field — which is what `mise tasks` lists and what PRs reference — still claims
the 50% invariant is verified.

**Fix:** Update the task description to accurately reflect what is tested:

```toml
[tasks."verify:head-sampling"]
description = "Phase 16 smoke check: at least 1 trace from order-producer reaches Tempo (proves sampler is not dropping 100%). ~50% ratio requires manual verification (see README §16a)."
```

---

## Info

### IN-01: README Step 2 describes alwaysOn sampler — stale after Phase 16 swap

**File:** `README.md:119`

**Issue:** The Step 2 description reads: `explicit Sampler.parentBased(Sampler.alwaysOn())`.
Phase 16 changed both `OtelSdkConfiguration.java` files to use
`Sampler.parentBased(Sampler.traceIdRatioBased(0.5))`. The description of what Step 2
introduces is now inconsistent with what the reader finds in the current `main` branch code.
A workshop attendee reading the README alongside the current source code will see
`traceIdRatioBased(0.5)` in `buildTracerProvider()` but the README says `alwaysOn()` was
the "smallest possible" setup. The confusion could lead attendees to believe Phase 16's
sampler swap was already present at Step 2.

**Fix:** The README Step 2 section should note that `main` reflects the post-Phase-16
sampler. Add a parenthetical or note:

```
...`Sampler.parentBased(Sampler.alwaysOn())` (later changed to 50% ratio in Phase 16;
`main` shows the post-Phase-16 code)...
```

---

### IN-02: BAGGAGE_RPS not documented in load.sh header override list

**File:** `scripts/load.sh:32-36`

**Issue:** The header comment's override list (lines 32-36) documents `TARGET`, `DURATION`,
`QUERY_PER_SECOND`, `N_CONNECTIONS`, `IDEMPOTENT_RPS`, `BURST_RPS`, `BURST_DURATION`,
`BURST_INTERVAL`, `BURST_CONNECTIONS`, `BURST_PRIORITY`. The Phase 16 `BAGGAGE_RPS` variable
(default 3) is absent from this list. A workshop attendee who wants to disable or tune the
baggage stream must read through the script body to discover the variable. The `Defaults:`
summary line at line 37 also omits the baggage stream from its accounting.

**Fix:** Add `BAGGAGE_RPS` to the override list and update the Defaults summary:

```bash
#   IDEMPOTENT_RPS (0=off; default 5),
#   BAGGAGE_RPS (0=off; default 3),  # <-- add
#   BURST_RPS (0=off), ...
#
# Defaults: ~200 rps total (100 per priority) + 5 rps idempotent + 3 rps baggage + no burst.
```

---

### IN-03: openTelemetryShutdownGuard cast to OpenTelemetrySdk is unchecked

**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:333-334`
**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:325-326`

**Issue:** `openTelemetryShutdownGuard(OpenTelemetry openTelemetry)` casts its parameter to
`OpenTelemetrySdk` with a bare `(OpenTelemetrySdk) openTelemetry`. This cast is safe today
because the only `OpenTelemetry` bean on the context is the `OpenTelemetrySdk` built in
`openTelemetry()`. However, if a future phase introduces a second `OpenTelemetry` bean (e.g.,
a `@TestConfiguration` override, or an `@Primary`-qualified noop bean for a specific profile),
Spring could resolve the parameter to a non-`OpenTelemetrySdk` implementation and the cast
would throw `ClassCastException` at context startup — a bean creation failure with a
non-obvious error message. Since this is a workshop artifact and future phases are planned,
the fragility is worth flagging.

This appears in both `OtelSdkConfiguration.java` copies identically — the duplication is
intentional per design, but the issue is present in both.

**Fix:** Narrow the parameter type to `OpenTelemetrySdk` to make the dependency explicit
and fail at compile time if the type ever changes:

```java
@Bean(destroyMethod = "close")
CloseableOpenTelemetrySdk openTelemetryShutdownGuard(OpenTelemetrySdk openTelemetry) {
    return new CloseableOpenTelemetrySdk(openTelemetry);
}
```

Note: Spring resolves `@Bean` parameters by type, and `OpenTelemetrySdk` is a concrete class
— specifying it as the parameter type is safe and more precise.

---

_Reviewed: 2026-05-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
