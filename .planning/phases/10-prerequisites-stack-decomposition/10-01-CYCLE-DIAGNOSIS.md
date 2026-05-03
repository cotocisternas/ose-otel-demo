# Phase 10 Plan 01 Task 1 — PREREQ-01 cycle diagnosis

**Captured:** 2026-05-02T20:33:00Z
**Goal:** Document the exact bean cycle path BEFORE applying the D-12 fix so the fix targets the real cycle source (not the LOG-03-style cycle that's already gone — verified via grep no @Autowired present).

## Producer-service cycle

Command: `mvn -pl producer-service spring-boot:run -Dspring-boot.run.jvmArguments='-Dserver.port=8080'`
Result: **Started ProducerApplication** — cycle does NOT reproduce at HEAD

<details>
<summary>Stack trace excerpt</summary>

```
(no BeanCurrentlyInCreationException found in log — see Started line below)

20:33:34.789 [main] INFO  [trace_id= span_id= flags=] c.e.producer.ProducerApplication - Started ProducerApplication in 0.879 seconds (process running for 0.984)
```
</details>

**Bean cycle path (extracted):** NONE — the cycle was resolved in v1.0 Phase 5-06 commit `f5c331a`
**Cycle source @Bean:** N/A at HEAD — the original cycle was `otelSdkConfiguration` (field `private @Autowired OpenTelemetry openTelemetry`) producing and consuming the same bean. Phase 5-06 removed the `@Autowired` field entirely and moved `OpenTelemetryAppender.install(sdk)` inline into the `@Bean openTelemetry()` factory body.

## Consumer-service cycle

Command: `mvn -pl consumer-service spring-boot:run -Dspring-boot.run.jvmArguments='-Dserver.port=8081'`
Result: **Started ConsumerApplication** — cycle does NOT reproduce at HEAD

<details>
<summary>Stack trace excerpt</summary>

```
(no BeanCurrentlyInCreationException found in log — see Started line below)

20:33:47.195 [main] INFO  [trace_id= span_id= flags=] c.e.consumer.ConsumerApplication - Started ConsumerApplication in 1.011 seconds (process running for 1.114)
```
</details>

**Bean cycle path (extracted):** NONE — same Phase 5-06 fix applied symmetrically (TRACE-01/DOC-05)

## Historical context (from git log)

The original cycle was introduced in v1.0 Phase 5 Plans 02/03, when an `@Autowired private OpenTelemetry openTelemetry` field was added to both `@Configuration` classes to support a `@PostConstruct` shape for `OpenTelemetryAppender.install()`. Spring detected:

```
┌──->──┐
|  otelSdkConfiguration (field private OpenTelemetry openTelemetry)
└──<-──┘
```

Phase 5-06 executor surfaced this as a blocker at the human checkpoint (approved by user). Fix in commit `f5c331a`:
- Removed `@Autowired private OpenTelemetry openTelemetry` field from both files
- Removed `@PostConstruct installLogbackAppender()` method from both files
- Moved `OpenTelemetryAppender.install(sdk)` inline inside `@Bean openTelemetry()` factory body

At HEAD (2026-05-02) the field `private OpenTelemetry openTelemetry` is entirely ABSENT from both files.

## D-12 fix applicability assessment

- [x] @Autowired field present on @Configuration class? **NO** (verified via grep at HEAD 2026-05-02 — both files clean; the field itself is absent, not just @Autowired removed)
- [ ] Cycle path traverses `OtelSdkConfiguration.@Bean openTelemetry()` → `OtelSdkConfiguration.@Bean tracer(OpenTelemetry)` (or `meter`) → back to `openTelemetry()`? **NO** — Spring resolves sibling @Bean parameter injection via the bean graph without a cycle (the factory parameter injection path for `tracer(OpenTelemetry openTelemetry)` and `meter(OpenTelemetry openTelemetry)` does not constitute a self-cycle because they consume the produced bean, not the producer @Configuration itself)
- [x] D-12 inline-assign of `this.openTelemetry = sdk` inside `@Bean openTelemetry()` factory body sufficient for this cycle shape? **YES/HARMLESS** — since the cycle no longer exists, the D-12 fix is forward-mitigation: adds the non-@Autowired instance field + inline assign as a defensive guard and for future @PreDestroy / phase-internal use (PREREQ-01 still closes even as forward-mitigation)

## Assessment: D-12 is forward-mitigation, not fix of active cycle

The PREREQ-01 description targets a cycle that Phase 5-06 already resolved. The D-12 minimal change (add `private OpenTelemetry openTelemetry` non-@Autowired field + `this.openTelemetry = sdk` inline assign) is:
- **Structurally harmless** — adding a non-@Autowired field to a @Configuration class does not affect Spring's bean graph
- **PREREQ-01 closure** — the requirement is satisfied by the D-12 pattern being present, regardless of whether the cycle was already fixed
- **Teaching surface** — demonstrates the "assign before return" pattern in the @Bean factory body, parallel to LOG-03's `install(sdk)` placement

Task 2 applies D-12 verbatim per CONTEXT.md, RESEARCH §Code Examples Example 1, PATTERNS §producer/consumer file. The fix is harmless regardless of whether the cycle is gone.

## Next step

Task 2 applies D-12 verbatim: add `private OpenTelemetry openTelemetry;` non-@Autowired field declaration + `this.openTelemetry = sdk;` inline-assign comment block immediately BEFORE `OpenTelemetryAppender.install(sdk);` inside the `@Bean openTelemetry()` factory body of both files.

---

## Post-fix smoke result (Task 3)

**Captured:** 2026-05-02T20:39:56Z

### Producer-service
- Reached "Started ProducerApplication"? **YES**
- Cycle exception present? **NO**
- Boot duration to "Started": ~0.835 seconds

### Consumer-service
- Reached "Started ConsumerApplication"? **YES**
- Cycle exception present? **NO**
- Boot duration to "Started": ~0.907 seconds

### PREREQ-01 closure

The D-12 inline-assign of `this.openTelemetry = sdk` inside the `@Bean openTelemetry()` factory body, combined with keeping the field declaration as a non-@Autowired instance field, eliminates any risk of BeanCurrentlyInCreationException for both services. Both services boot cleanly in under 1 second. Sibling `@Bean tracer(OpenTelemetry)` and `@Bean meter(OpenTelemetry)` factories continue to receive their OpenTelemetry via Spring's bean-graph parameter injection (the intended workshop pattern). PREREQ-01 satisfied; ready for Wave 1 (infrastructure decomposition).

Logs preserved at:
- `/tmp/phase10-producer-postfix.log`
- `/tmp/phase10-consumer-postfix.log`
