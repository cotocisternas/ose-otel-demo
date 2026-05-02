---
phase: 05-logs-correlation
reviewed: 2026-05-01T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - producer-service/pom.xml
  - consumer-service/pom.xml
  - producer-service/src/main/resources/logback-spring.xml
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - consumer-service/src/main/resources/logback-spring.xml
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - producer-service/src/main/java/com/example/producer/api/OrderController.java
  - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
  - README.md
findings:
  critical: 0
  warning: 5
  info: 8
  total: 13
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-05-01
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Phase 5 wires the third OTel signal (logs) through `SdkLoggerProvider` + `BatchLogRecordProcessor` + `OtlpGrpcLogRecordExporter`, plus the Logback bridge (`opentelemetry-logback-appender-1.0` for OTLP export and `opentelemetry-logback-mdc-1.0` for MDC injection). The bean-cycle fix in commit `f5c331a` correctly relocates `OpenTelemetryAppender.install(sdk)` inline into the `@Bean` factory — that mitigation is sound and PITFALL #5 is properly addressed in code.

The implementation is functionally correct, but **the documentation surface around the late install-relocation has not been swept**. Three separate places (the README's Phase 5 section, both `pom.xml` Phase 5 comments, and both `logback-spring.xml` headers) still describe a `@PostConstruct installLogbackAppender()` method that no longer exists — the README even says "The `@PostConstruct` IS the lesson", which is now false. For a workshop project where the comments and README ARE the teaching surface, this drift is a real defect.

The most concrete bug is the Loki query suggested in the README for the failure-correlation walkthrough: `{service_name="order-consumer"} |= "ERROR"` — this filters log lines whose message body contains the literal string "ERROR", but the OTLP appender exports the message body without any level prefix, and the LOG.error message in `ProcessingService` is `"order processing failed: orderId=..."` (no "ERROR" substring). The query as written will return zero results for the very flow the Phase 5 success-criterion lesson is built around.

Two Phase 5 LOG.* call sites also have minor security/quality concerns: `OrderController.create(...)` logs the entire request payload map, and `ProcessingService.process(...)` logs an attacker-controlled `orderId` field — both create a small log-injection / PII-leak surface that is worth surfacing in a workshop's log-correlation lesson.

## Warnings

### WR-01: Loki query in README will not match the LOG.error line

**File:** `README.md:200`
**Issue:** The README's headline triple-signal-on-failure walkthrough tells attendees to run:
```
{service_name="order-consumer"} |= "ERROR"
```
This matches the literal substring "ERROR" anywhere in the log line body. But the OTLP `OpenTelemetryAppender` exports the message body WITHOUT a level prefix — only the formatted `%msg` content is shipped, and the LOG.error in `ProcessingService.process(...)` is `"order processing failed: orderId=..."` (line 96), which contains no "ERROR" substring. The level lands on the OTLP record as `severity_text`, NOT in the message body. Attendees following the README will get an empty Loki result and conclude the pipeline is broken.

**Fix:** Switch to the OTLP severity field (which Loki maps as a label or detected field via the Loki OTLP receiver):
```
{service_name="order-consumer"} | severity_text="ERROR"
```
or, to keep a substring match, change the LOG.error message itself to include the level (less clean):
```java
LOG.error("ERROR: order processing failed: orderId={}", orderId, e);
```
The first option is the OTel-idiomatic fix and matches how Loki's OTLP receiver indexes severity.

---

### WR-02: README still describes a `@PostConstruct installLogbackAppender()` that no longer exists

**File:** `README.md:169-183`
**Issue:** The `Step 5: Logs Correlation` section dedicates an entire bullet to `@PostConstruct installLogbackAppender()`, complete with the assertion **"The `@PostConstruct` IS the lesson"**. Commit `f5c331a` (the bean-cycle fix referenced in the phase context) moved `OpenTelemetryAppender.install(sdk)` INLINE into the `@Bean` factory and deleted the `@PostConstruct`. The README is now teaching a method that doesn't exist; an attendee reading the README and grep'ing the codebase for `@PostConstruct` will find nothing in `OtelSdkConfiguration.java`. The lesson is now "install inline in the @Bean factory to break the Spring self-cycle", which is a different (and more interesting) lesson that the README doesn't tell.

**Fix:** Rewrite the third bullet in the §Step 5 list to match the implementation. Suggested replacement:
```markdown
- **Inline `OpenTelemetryAppender.install(sdk)` in the `@Bean` factory** —
  the load-bearing PITFALL #5 mitigation (LOG-03 / D-08 / D-09). **The
  order-of-operations problem:** Logback initializes BEFORE the Spring
  ApplicationContext is built, so the `OpenTelemetryAppender` constructed
  at startup defaults to `OpenTelemetry.noop()`. We call `install(sdk)`
  inside the `@Bean` factory, immediately after `OpenTelemetrySdk.builder()...build()`
  returns and before `return sdk` — this avoids the Spring self-cycle that a
  `@PostConstruct` shape would create (the `@Configuration` bean would have
  to autowire the `OpenTelemetry` it itself produces) AND tightens the
  window in which logs land in the noop replay queue. Logs emitted before
  this line are buffered (1000-event default) and replayed on install.
  See https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10307
```

---

### WR-03: Producer logs entire untrusted request payload at INFO

**File:** `producer-service/src/main/java/com/example/producer/api/OrderController.java:23`
**Issue:** `LOG.info("received POST /orders payload={}", payload)` writes the full `Map<String, Object>` from a public POST endpoint to logs. Two concrete defects:
1. **PII / secret leakage:** the workshop instrumentation explicitly does not validate the payload schema (the Map is `@RequestBody Map<String, Object>`), so anything an attendee POSTs — including credit-card-shaped strings, free-form text, or accidentally-pasted secrets — lands in Loki and the console. In a workshop where attendees curl arbitrary JSON, this is a footgun.
2. **Log injection (CRLF):** Map values are stringified via `AbstractMap.toString()`, which calls `String.valueOf(value)` on each entry value — no CRLF escaping. A POST with `{"note":"hi\r\n[INFO] fake log line"}` injects a forged log line into the file/console that downstream log parsers may treat as a real entry.

**Fix:** Log only known-safe, low-cardinality fields. The `payload.get("priority")` key is already used by `OrderService` for the metric attribute; mirror that:
```java
LOG.info("received POST /orders priority={}", payload.get("priority"));
```
If attendees need the orderId in the log (for trace-correlation walkthroughs), add an INFO line in `OrderService.place(...)` AFTER the orderId is generated.

---

### WR-04: Consumer logs untrusted `orderId` field on the failure path

**File:** `consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java:95-96`
**Issue:** `Object orderId = order.get("orderId"); LOG.error("order processing failed: orderId={}", orderId, e);` — the `orderId` value is whatever the producer put into the message payload. In this codebase the producer always sets it to a UUID via `OrderService.place(...)`, so the field is currently safe in practice, but the consumer has no validation: a workshop attendee who POSTs `{"orderId":"\r\n[ERROR] forged"}` directly (the controller passes the payload through, and `OrderPublisher.publish` does `message.put("orderId", orderId)` where the second `orderId` is the controller-generated UUID — but `payload` already had a key `orderId` that survives the `new HashMap<>(payload)` copy in `OrderPublisher` line 43, then gets overwritten on line 44, so currently the producer overwrite cleans the value). The defense is shallow — one line in `OrderPublisher` away from a CRLF log-injection on the consumer.

Note also: `Object` is logged via SLF4J `{}`-formatting, which calls `String.valueOf(...)` — that's fine for null but does no escaping.

**Fix:** Two options:
1. Cast to String and validate at the consumer boundary:
```java
Object raw = order.get("orderId");
String orderId = raw instanceof String s && s.matches("[0-9a-fA-F-]{36}") ? s : "<invalid>";
LOG.error("order processing failed: orderId={}", orderId, e);
```
2. Less defensive but pedagogically aligned: omit the `orderId` from the log entirely — the trace_id stamped on the OTLP record (and the recordException event on the CONSUMER span) is the better correlation key for this failure walkthrough, and the README's success criterion is "correlate via trace_id", not "via orderId".

---

### WR-05: `OpenTelemetryAppender.install(sdk)` is global state — second SDK build clobbers the first

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:224`
**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:233`
**Issue:** `OpenTelemetryAppender.install(...)` walks the global Logback `LoggerContext` and reassigns the appender's `volatile OpenTelemetry` field. The implementation comment correctly notes this is idempotent, but it does NOT note the consequence in a multi-context JVM: if any code (Phase 6 Testcontainers tests, a DEV restart inside the same JVM via `spring-boot:run` reload, or a future shared test harness) builds a second `OpenTelemetrySdk`, it will silently replace the first SDK on the appender — and any spans/logs emitted to the OLD SDK reference become orphans that never flush. This is the textbook "double-install" pitfall on the static install pattern.

The current code also does not call `install(OpenTelemetry.noop())` from a Spring shutdown hook, so on graceful Spring shutdown the appender keeps a reference to the closed SDK (`OpenTelemetrySdk.close()` is invoked via `destroyMethod="close"`, but the appender's reference is not cleared). Logs that race the shutdown will hit a closed exporter.

**Fix:** Two improvements, neither blocking but worth a workshop comment:
1. Add a `@PreDestroy` (or hook into the same bean's destroy phase) that calls `OpenTelemetryAppender.install(OpenTelemetry.noop())` — this matches the comment on line 215 ("Calling it with `OpenTelemetry.noop()` effectively 'uninstalls' exporting").
2. Add an inline comment near `install(sdk)` explicitly flagging that this writes to global state — important for Phase 6 test isolation.

The comment on lines 216-218 (consumer 225-227) about Phase 6 idempotency hints at this, but does not state the shutdown-cleanup gap.

## Info

### IN-01: Stale `@PostConstruct` reference in pom.xml Phase 5 dependency comment

**File:** `producer-service/pom.xml:99-101`
**File:** `consumer-service/pom.xml:99-101`
**Issue:** The comment block describing `opentelemetry-logback-appender-1.0` says:
```
Has the static `install(OpenTelemetry)` method called from
OtelSdkConfiguration's @PostConstruct (LOG-03 / D-08).
```
After commit `f5c331a` the install moved into the `@Bean` factory (no `@PostConstruct` left in `OtelSdkConfiguration.java`). The pom comment is the place workshop attendees read first when they cd into a service module — accuracy here matters.

**Fix:** Replace "from OtelSdkConfiguration's @PostConstruct" with "inline in OtelSdkConfiguration's @Bean factory (PITFALL #5 — see comment on the install line)".

---

### IN-02: Stale `@PostConstruct` reference in logback-spring.xml header

**File:** `producer-service/src/main/resources/logback-spring.xml:74-78`
**File:** `consumer-service/src/main/resources/logback-spring.xml:74-78`
**Issue:** Same drift as IN-01:
```
OpenTelemetryAppender.install(openTelemetry) is called from a @PostConstruct
method on OtelSdkConfiguration AFTER the SDK bean is built (D-08, D-09 —
PITFALL #5 mitigation).
```
The actual mitigation is inline in the @Bean factory.

**Fix:** Update both XML headers to match — these files are read from the IDE during the Phase 5 walkthrough and will mislead attendees.

---

### IN-03: "BOTH pipeline helpers" comment is stale (now THREE pipelines)

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:117-118`
**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:126-127`
**Issue:**
```
// Built ONCE in the orchestrator and passed to BOTH pipeline helpers
// (D-05) so traces and metrics share an identical Resource — service.name,
```
Phase 5 added `buildLoggerProvider`; now there are THREE helpers and the Resource is shared across traces+metrics+logs (Loki). The comment should mention Loki for the cross-signal correlation rationale.

**Fix:**
```
// Built ONCE in the orchestrator and passed to ALL THREE pipeline helpers
// (D-05) so traces, metrics, AND logs share an identical Resource — service.name,
// service.namespace, service.instance.id, and deployment.environment.name
// are byte-for-byte the same in Tempo, Mimir, AND Loki.
```

---

### IN-04: Comment on destroyMethod cascade describes only the trace path

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:103-107`
**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:111-115`
**Issue:** The comment explains the shutdown cascade only for the `SdkTracerProvider`/`BatchSpanProcessor` chain. Phase 5 adds the LoggerProvider/BatchLogRecordProcessor chain, which has a DIFFERENT default flush behavior (1s schedule delay vs 5s for spans — RESEARCH Finding #4 acknowledges this elsewhere). The shutdown comment doesn't say what happens to in-flight logs.

**Fix:** Extend the cascade description:
```
which cascades to the SdkTracerProvider, SdkMeterProvider, AND SdkLoggerProvider —
each calls shutdown() on its respective Batch{Span,Metric,LogRecord}Processor,
which forces a final flush of in-flight items in each pipeline.
```

---

### IN-05: Producer/consumer pom `<description>` still says "Phase 2"

**File:** `producer-service/pom.xml:18`
**File:** `consumer-service/pom.xml:18`
**Issue:** Description: `Phase 2: manual OTel SDK + semconv dependencies wired (SDK config in 02-02).` — no mention of Phase 4 (metrics) or Phase 5 (logs) deps that have since landed in the same file.

**Fix:** Roll the description forward each phase, or generalize to "Manual OTel SDK + semconv + Logback bridge (Phases 2/4/5)".

---

### IN-06: `Optional.ofNullable(System.getenv(...))` triplicated within one class

**File:** `producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java:247-248,327-328,398-399`
**File:** `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java:256-257,336-337,406-407`
**Issue:** The same six lines (`String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse(DEFAULT_OTLP_ENDPOINT);`) appear in `buildTracerProvider`, `buildMeterProvider`, and `buildLoggerProvider`. Per `coding-style.md` ("No hardcoded values (use constants or config)" / DRY), this is a small smell — but the per-service-duplication ethos for pedagogical reasons is documented (DOC-05), so the call may be intentional.

**Fix:** Either (a) leave as-is and add a one-liner comment at each site stating "duplicated for parallel-pipeline readability — the three helpers are intentionally byte-symmetric", or (b) extract `private String otlpEndpoint()` and lose three lines of pedagogical parallelism. Workshop pedagogy probably wins here — recommend (a).

---

### IN-07: Pattern `[trace_id=%mdc{trace_id:-} span_id=%mdc{span_id:-}]` lacks `trace_flags`

**File:** `producer-service/src/main/resources/logback-spring.xml:49`
**File:** `consumer-service/src/main/resources/logback-spring.xml:49`
**Issue:** The MDC injector populates `trace_id`, `span_id`, AND `trace_flags`. The console pattern only renders the first two. This is fine functionally, but `trace_flags` distinguishes sampled from un-sampled spans (`01` vs `00`) and is a small teaching surface that's currently invisible. Not a defect — flagging because the comment block above the pattern enumerates "Default MDC keys (trace_id / span_id / trace_flags) match D-11's pattern" which suggests all three were intended.

**Fix:** Optional. If you want full transparency: append `flags=%mdc{trace_flags:-}` to the bracketed prefix.

---

### IN-08: `OrderController.create(...)` log line has no `orderId` reference

**File:** `producer-service/src/main/java/com/example/producer/api/OrderController.java:21-27`
**Issue:** The controller logs at request entry (`received POST /orders payload={}`) but NOT after the orderId is generated. The `LOG.info("publishing orderId={}...", orderId, ...)` in `OrderPublisher` covers the publish hand-off, but there is no producer-side log that ties the inbound HTTP request to the generated orderId — for the workshop's "follow one trace through the system" lesson, an `orderId=<uuid>` log line on the controller path would make the trace easier to follow in Loki.

**Fix:** Either log after `orderService.place(...)`:
```java
String orderId = orderService.place(payload);
LOG.info("accepted orderId={}", orderId);
```
or move the entry log to `OrderService.place(...)` after the UUID is minted (and drop the payload-leak in WR-03 at the same time).

---

_Reviewed: 2026-05-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
