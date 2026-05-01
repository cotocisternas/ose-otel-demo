---
id: 04-04-consumer-gauge
phase: 04-metrics
plan: 04
type: execute
wave: 2
depends_on: [04-01-meter-pipeline-refactor]
requirements: [METRIC-04]
requirements_addressed: [METRIC-04]
files_modified:
  - consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java
autonomous: true
objective: "Create a NEW @Component class `QueueDepthGauge` (D-18b — planner choice over D-18a's inline @PostConstruct, justified by the per-service-mirror cleanliness argument in PATTERNS.md) that constructor-injects the consumer-side Meter @Bean (created in Plan 04-01, scope `com.example.consumer`) and registers an ObservableLongGauge named `orders.queue.depth.estimate` (METRIC-04). The callback returns `ThreadLocalRandom.current().nextInt(0, 50)` — a synthetic value (D-17). Instrument flavor is `.ofLongs()` (D-19 — int values, integer queue-depth convention). PreDestroy explicitly closes the gauge handle so the SDK stops invoking the callback at shutdown (defensive — the SdkMeterProvider close cascade handles this transitively, but explicit close gives a single readable cleanup point)."
must_haves:
  truths:
    - "New file consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java exists; package is com.example.consumer.observability"
    - "Class is @Component (Spring stereotype); constructor takes a single Meter parameter — hosting choice (D-18) resolved as D-18b (separate @Component) per PATTERNS.md recommendation; D-18a (inline @PostConstruct in OtelSdkConfiguration) explicitly rejected because it would break the producer/consumer mirror property D-02"
    - "Class holds a `private final ObservableLongGauge gauge` field initialized in the constructor (NOT in @PostConstruct — constructor-only initialization keeps the @Component focused)"
    - "Gauge name is `orders.queue.depth.estimate` (METRIC-04 — locked)"
    - "Gauge instrument flavor is `.ofLongs()` (D-19 — long-valued, integer queue depths convention)"
    - "Gauge has setDescription (workshop-readable, names the synthetic-value caveat) and setUnit (`{messages}` is conventional for queue depth)"
    - "Callback uses `measurement.record(ThreadLocalRandom.current().nextInt(0, 50))` (D-17 — synthetic value, METRIC-04 spec'd as synthetic; ThreadLocalRandom for thread-safety on the PeriodicMetricReader callback thread)"
    - "Callback is registered via `.buildWithCallback(measurement -> measurement.record(...))` lambda (NOT a separate Runnable / register-then-attach pattern — buildWithCallback is the OTel-idiomatic shape)"
    - "Class JavaDoc explains: (a) why a separate @Component vs inline @PostConstruct on OtelSdkConfiguration (D-18b — keeps producer/consumer OtelSdkConfiguration mirror clean); (b) why .ofLongs() vs default double (D-19); (c) why ThreadLocalRandom (D-17 — thread-safe, no shared state, no coupling to APP-04 AtomicInteger); (d) callback discipline (cheap, side-effect-free, runs on PeriodicMetricReader interval per D-03's 10s); (e) why the value is synthetic (METRIC-04 spec'd; real queue depth = RabbitMQ Mgmt API polling at :15672/api/queues/..., out of scope)"
    - "@PreDestroy method calls `gauge.close()` defensively — the SdkMeterProvider close cascade (Plan 04-01 D-07 / Phase 2 D-15) handles this transitively, but explicit close gives a single readable cleanup point and prevents stale callback invocations during a slow shutdown"
    - "Imports: java.util.concurrent.ThreadLocalRandom, io.opentelemetry.api.metrics.Meter, io.opentelemetry.api.metrics.ObservableLongGauge, jakarta.annotation.PreDestroy, org.springframework.stereotype.Component"
    - "NO modification to consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (D-18b — gauge registration lives in the new @Component, not in the SDK config; this preserves the producer/consumer mirror property D-02)"
    - "mvn -pl consumer-service compile exits 0"
  artifacts:
    - path: "consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java"
      provides: "New @Component that registers the orders.queue.depth.estimate ObservableLongGauge at consumer startup; callback returns ThreadLocalRandom.nextInt(0, 50) on every PeriodicMetricReader interval (METRIC-04 / D-16 / D-17 / D-18b / D-19); @PreDestroy closes the gauge handle cleanly"
      contains: "gaugeBuilder(\"orders.queue.depth.estimate\")"
  key_links:
    - from: "QueueDepthGauge constructor"
      to: "Meter @Bean (created in Plan 04-01, scope com.example.consumer)"
      via: "Spring constructor injection (single @Component, single Meter dependency)"
      pattern: "QueueDepthGauge\\(Meter meter\\)"
    - from: "buildWithCallback(measurement -> ...)"
      to: "PeriodicMetricReader (10s interval per Plan 04-01 D-03)"
      via: "SDK invokes the callback on each metric collection cycle on the reader's worker thread"
      pattern: "buildWithCallback\\("
    - from: "ThreadLocalRandom.current().nextInt(0, 50)"
      to: "Synthetic gauge value in Mimir as orders_queue_depth_estimate"
      via: "OTel-to-Prometheus name mangling: dots → underscores; gauges emit instantaneous values per scrape"
      pattern: "ThreadLocalRandom\\.current\\(\\)\\.nextInt\\(0, 50\\)"
---

<objective>
Create a single new file: `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`. This is a Spring `@Component` that constructor-injects the consumer-side `Meter` @Bean (produced by Plan 04-01) and registers an `ObservableLongGauge` named `orders.queue.depth.estimate` (METRIC-04). The callback returns a synthetic `ThreadLocalRandom.current().nextInt(0, 50)` value on every `PeriodicMetricReader` collection interval (10s — Plan 04-01 D-03).

**D-18 hosting choice (PATTERNS.md recommends D-18b — adopted here):** Hosting the gauge in a dedicated `@Component` (rather than inline `@PostConstruct` in `OtelSdkConfiguration.java`) keeps the producer/consumer mirror property clean — Plan 04-01's `OtelSdkConfiguration.java` files differ by exactly the documented per-service identity lines (D-02). Adding a consumer-only `@PostConstruct` in `OtelSdkConfiguration.java` would introduce an asymmetry that the duplication-pedagogy depends on NOT having. Constructor-injection in a small standalone `@Component` is also more idiomatic than `@PostConstruct` (which doesn't accept parameters and would require a setter or field-level `@Autowired` on the `@Configuration` class).

**Synthetic value rationale (D-17 / METRIC-04):** The pedagogical point of `ObservableGauge` is the **callback-on-collection-interval mechanic**, not the value semantics. A real queue-depth measurement would require polling the RabbitMQ Management API at `http://localhost:15672/api/queues/%2F/orders.created` and parsing JSON — non-trivial integration that distracts from the SDK lesson. METRIC-04 explicitly spec's "synthetic queue-depth value" so workshop attendees see all three instrument shapes (Counter / Histogram / ObservableGauge) without infrastructure-glue overhead. The README delta (Plan 04-05) names this explicitly so attendees know the synthetic shortcut is intentional.

**Long flavor (D-19):** `gaugeBuilder(...).ofLongs().buildWithCallback(...)`. The synthetic value is `int`, the gauge name carries `.estimate` (whole-number connotation), and integer queue depths are the conventional shape for messaging gauges. `ObservableLongGauge` + `ObservableLongMeasurement.record(long)` keeps the type honest — the default-flavor double would silently coerce `int → double` without communicating intent.

**Thread-safety (D-17):** `ThreadLocalRandom.current()` is documented thread-safe and the canonical choice for callback contexts that fire on potentially-different threads each time. There is no shared mutable state, no race, and no coupling to Phase 3's `AtomicInteger` (APP-04's deterministic-failure counter on `ProcessingService`).

Purpose: METRIC-04 satisfied. Plan 04-05 verifies live (a fresh `orders_queue_depth_estimate` sample lands in Mimir every 10s — ROADMAP SC #3, which proves the `PeriodicMetricReader` interval override from Plan 04-01).

Output: 1 new file at `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`. Zero modifications to other files.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/04-metrics/04-CONTEXT.md
@.planning/phases/04-metrics/04-PATTERNS.md
@.planning/phases/04-metrics/04-01-meter-pipeline-refactor-PLAN.md
@CLAUDE.md
@consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
@consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java

<interfaces>
<!-- Key types this plan uses. All BOM-managed via opentelemetry-bom:1.61.0; -->
<!-- opentelemetry-sdk-metrics is already on classpath (transitive of opentelemetry-sdk). -->

From io.opentelemetry.api.metrics:
```java
public interface Meter {
    LongGaugeBuilder gaugeBuilder(String name);   // returns DoubleGaugeBuilder by spec, but
                                                  // .ofLongs() switches to LongGaugeBuilder
}
public interface DoubleGaugeBuilder {            // default-flavor (we DON'T use this)
    DoubleGaugeBuilder setDescription(String description);
    DoubleGaugeBuilder setUnit(String unit);
    LongGaugeBuilder ofLongs();                  // <-- D-19 switch
    ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement>);
}
public interface LongGaugeBuilder {              // long-flavor (D-19)
    LongGaugeBuilder setDescription(String description);
    LongGaugeBuilder setUnit(String unit);
    ObservableLongGauge buildWithCallback(Consumer<ObservableLongMeasurement>);
}
public interface ObservableLongMeasurement {
    void record(long value);
    void record(long value, Attributes attributes);
}
public interface ObservableLongGauge extends AutoCloseable {
    @Override void close();
}
```

From java.util.concurrent (already available — JDK 17):
```java
public final class ThreadLocalRandom extends Random {
    public static ThreadLocalRandom current();   // thread-safe, no contention
    public int nextInt(int origin, int bound);   // [origin, bound) — D-17 uses (0, 50)
}
```

From jakarta.annotation (Spring Boot 3.x — Jakarta EE 10):
```java
public @interface PreDestroy {}                  // bean cleanup hook
```

From org.springframework.stereotype:
```java
public @interface Component {}                   // Spring stereotype, picked up by @ComponentScan
```

OrderListener current shape (existing @Component analog for the file structure):
```java
package com.example.consumer.messaging;

import org.springframework.stereotype.Component;
// ...

@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;

    public OrderListener(ProcessingService processingService) {
        this.processingService = processingService;
    }
    // ...
}
// QueueDepthGauge follows the same pattern: @Component + constructor-injection + final field.
```
</interfaces>
</context>

<tasks>

<task id="04-04-T1" type="auto">
  <name>Task 1: Create the new QueueDepthGauge.java @Component file with the orders.queue.depth.estimate ObservableLongGauge registered in the constructor + @PreDestroy cleanup</name>
  <files>consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java</files>
  <read_first>
    - .planning/phases/04-metrics/04-PATTERNS.md (lines 421-525 — full target shape including the @Component class skeleton at lines 461-522 and the recommendation paragraph at lines 524-525 favoring D-18b)
    - .planning/phases/04-metrics/04-CONTEXT.md (D-16 consumer-side gauge, D-17 ThreadLocalRandom synthetic value, D-18 hosting choice — D-18b adopted here, D-19 ofLongs)
    - .planning/phases/04-metrics/04-01-meter-pipeline-refactor-PLAN.md (Plan 04-01 produces the consumer Meter @Bean with scope com.example.consumer that this gauge constructor-injects)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (the in-repo @Component analog for the file structure: package + @Component + constructor injection of a single dependency + final field — this is the pattern to mirror)
    - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java (the file we EXPLICITLY do NOT modify — D-18b adoption keeps OtelSdkConfiguration symmetric with the producer's; verify after editing that the producer/consumer SdkConfig diff is unchanged from Plan 04-01's output)
  </read_first>
  <action>
    Create a NEW file at `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` with the EXACT content below. The directory `consumer-service/src/main/java/com/example/consumer/observability/` does not yet exist — Maven and Spring's `@ComponentScan` (rooted at the `com.example.consumer` package on the `@SpringBootApplication` class) will pick up the new package automatically, no application-class change needed.

    Use the Write tool. Do NOT use heredoc. Verify the parent directory `consumer-service/src/main/java/com/example/consumer/` exists before writing — it does (multiple existing packages: `config`, `messaging`, `domain`, etc.). Spring will create the new `observability` subpackage on first compile.

    ```java
    package com.example.consumer.observability;

    import java.util.concurrent.ThreadLocalRandom;

    import io.opentelemetry.api.metrics.Meter;
    import io.opentelemetry.api.metrics.ObservableLongGauge;

    import jakarta.annotation.PreDestroy;
    import org.springframework.stereotype.Component;

    /**
     * Registers the {@code orders.queue.depth.estimate} ObservableGauge
     * (METRIC-04) at consumer startup.
     *
     * <p><b>Why a separate {@link Component} (D-18b)?</b> The gauge registration
     * is one-shot side-effect boilerplate, not part of the SDK pipeline build.
     * Hosting it here keeps {@code OtelSdkConfiguration.java} symmetrical with
     * the producer's file (D-02 mirror property) — no consumer-only
     * {@code @PostConstruct} that doesn't exist on the producer side. The
     * planner choice between D-18a (inline {@code @PostConstruct} in
     * OtelSdkConfiguration) and D-18b (this class) is documented in
     * 04-CONTEXT.md and 04-PATTERNS.md; the recommendation favors this shape
     * because it preserves the duplication-pedagogy of Phase 2 D-01 / DOC-05.
     *
     * <p><b>Why {@code ofLongs()} (D-19)?</b> The synthetic value is an
     * {@code int} and the gauge name carries {@code .estimate} (whole-number
     * connotation); integer queue depths are the conventional shape for
     * messaging gauges. The default-flavor {@code DoubleGaugeBuilder} would
     * silently coerce {@code int -> double} without communicating intent.
     * {@link ObservableLongGauge} + {@code ObservableLongMeasurement.record(long)}
     * keeps the type honest.
     *
     * <p><b>Why {@link ThreadLocalRandom} (D-17)?</b> The callback fires on
     * the {@code PeriodicMetricReader} worker thread once per 10-second
     * interval (Plan 04-01 D-03). {@code ThreadLocalRandom} is documented
     * thread-safe, has no shared state, and creates no coupling to Phase 3's
     * {@code AtomicInteger} on {@code ProcessingService} (APP-04's
     * deterministic-failure counter). A real queue-depth measurement would
     * require polling the RabbitMQ Management API at
     * {@code http://localhost:15672/api/queues/%2F/orders.created} and
     * parsing JSON — non-trivial integration that distracts from the SDK
     * lesson. METRIC-04 explicitly spec's "synthetic queue-depth value" so
     * attendees see all three instrument shapes (Counter / Histogram /
     * ObservableGauge) without infrastructure-glue overhead.
     *
     * <p><b>Callback discipline.</b> The callback should be cheap and
     * side-effect-free. The SDK invokes it on every collection cycle whether
     * or not anyone is querying the value (push model — push to OTLP every
     * 10s). Long-running work in the callback would block the meter reader
     * thread and delay metric exports for ALL instruments registered on this
     * {@code SdkMeterProvider}, not just this gauge.
     *
     * <p><b>Why is this consumer-side, not producer-side (D-16)?</b>
     * Semantically, "queue depth" is what the consumer sees draining; the
     * producer publishes to an exchange, not directly to a queue. This also
     * gives the consumer's {@code SdkMeterProvider} a real business-level
     * instrument to emit (it has no Counter and no Histogram from Phase 4),
     * giving symmetric pedagogical surface across producer (Counter +
     * Histogram) and consumer (ObservableGauge).
     */
    @Component
    public class QueueDepthGauge {

        private final ObservableLongGauge gauge;

        public QueueDepthGauge(Meter meter) {
            // Build + register the gauge in the constructor. The gauge handle
            // returned by buildWithCallback is what we hold onto so we can
            // close() it explicitly in @PreDestroy below — closing the handle
            // is what unregisters the callback from the SdkMeterProvider.
            //
            // Name "orders.queue.depth.estimate" — METRIC-04 locked. The
            // OTel-to-Prometheus exporter mangles dots to underscores; this
            // surfaces in Mimir as `orders_queue_depth_estimate`.
            //
            // Unit "{messages}" follows OTel's curly-brace convention for
            // dimensionless or non-SI units. Some backends prefer "1" or
            // empty; "{messages}" is the most readable for a workshop.
            //
            // .ofLongs() — see D-19 in the class JavaDoc above.
            //
            // The lambda body is the WHOLE callback. ThreadLocalRandom.current()
            // is allocation-free (TLS-cached); nextInt(0, 50) returns int in
            // [0, 50). measurement.record(long) accepts the int via widening.
            this.gauge = meter.gaugeBuilder("orders.queue.depth.estimate")
                .setDescription("Synthetic queue-depth estimate (workshop demo, not a real measurement)")
                .setUnit("{messages}")
                .ofLongs()
                .buildWithCallback(measurement ->
                    measurement.record(ThreadLocalRandom.current().nextInt(0, 50)));
        }

        /**
         * Closes the gauge so the SDK stops invoking the callback at shutdown.
         *
         * <p>The {@code SdkMeterProvider}'s {@code destroyMethod="close"}
         * cascade (Phase 2 D-15 / Plan 04-01 D-07) handles this transitively
         * — when Spring closes the {@code OpenTelemetry} bean, it cascades to
         * {@code SdkMeterProvider.shutdown()}, which closes all registered
         * instruments including this one. So strictly speaking this method
         * is defensive: closing the gauge handle here gives us a single,
         * readable cleanup point and prevents stale callback invocations
         * during the brief window between Spring tearing down beans (which
         * happens in DI-graph order) and the SDK actually shutting down.
         */
        @PreDestroy
        public void close() {
            gauge.close();
        }
    }
    ```

    **Constraints to verify after writing:**
    - File path: `consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` (new package — Maven creates the directory on first compile).
    - Package declaration: `package com.example.consumer.observability;`
    - `@Component` on the class.
    - Constructor takes a single `Meter` parameter; gauge built INSIDE the constructor as a final field.
    - Gauge name `orders.queue.depth.estimate` (METRIC-04).
    - `.ofLongs()` flavor switch (D-19).
    - Callback uses `ThreadLocalRandom.current().nextInt(0, 50)` (D-17 — synthetic, range [0, 50)).
    - `@PreDestroy` method calls `gauge.close()`.
    - **NO modification to `consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java`** — D-18b adoption is the whole point.

    Then run `mvn -pl consumer-service compile` to confirm consumer-service builds cleanly. The new package `com.example.consumer.observability` is automatically scanned by Spring's `@SpringBootApplication` (which rooted `@ComponentScan` at `com.example.consumer`), so the gauge will register at consumer startup with no config change.
  </action>
  <acceptance_criteria>
    - File exists at the correct path: `test -f consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Package declaration correct: `grep -qE '^package com\.example\.consumer\.observability;$' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - All required imports present: `for i in 'import java.util.concurrent.ThreadLocalRandom;' 'import io.opentelemetry.api.metrics.Meter;' 'import io.opentelemetry.api.metrics.ObservableLongGauge;' 'import jakarta.annotation.PreDestroy;' 'import org.springframework.stereotype.Component;'; do grep -qF "$i" consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java || exit 1; done`
    - @Component annotation on class: `grep -qE '^@Component$' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Class declaration correct: `grep -qE '^public class QueueDepthGauge \{$' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Field is final ObservableLongGauge: `grep -qE 'private final ObservableLongGauge gauge;' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Constructor signature is exactly (Meter meter): `grep -qE 'public QueueDepthGauge\(Meter meter\) \{' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Gauge name "orders.queue.depth.estimate" (METRIC-04): `grep -qF 'gaugeBuilder("orders.queue.depth.estimate")' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Gauge has setDescription and setUnit: `grep -qF '.setDescription(' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF '.setUnit("{messages}")' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - .ofLongs() present (D-19): `grep -qF '.ofLongs()' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - buildWithCallback used (D-17): `grep -qF '.buildWithCallback(' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - ThreadLocalRandom synthetic value with range [0, 50) (D-17): `grep -qF 'ThreadLocalRandom.current().nextInt(0, 50)' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - @PreDestroy method calls gauge.close() defensively: `grep -qE '^\s*@PreDestroy$' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'gauge.close();' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - Single gauge built (no second gauge): `grep -c 'gaugeBuilder' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java` returns exactly 1
    - JavaDoc references all 4 D-decisions cited (D-16, D-17, D-18b, D-19): `grep -qF 'D-16' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'D-17' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'D-18b' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'D-19' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - JavaDoc names METRIC-04 + the synthetic-value caveat: `grep -qF 'METRIC-04' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qE 'synthetic|Synthetic' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java`
    - **Crucially: NO modification to consumer's OtelSdkConfiguration.java** — `git diff --quiet HEAD -- consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java` exits 0 against the post-Plan-04-01 baseline (i.e. since the start of this Wave-2 task, OtelSdkConfiguration.java is unchanged)
    - consumer-service compiles cleanly: `mvn -pl consumer-service compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]' || mvn -pl consumer-service compile 2>&1 | tail -10 | grep -qE 'BUILD SUCCESS'`
    - Spring component-scan picks up the new package — confirmed by package layout (no @ComponentScan basePackages override needed, the existing @SpringBootApplication on com.example.consumer roots scan): `grep -qE '@SpringBootApplication' consumer-service/src/main/java/com/example/consumer/*.java || find consumer-service/src/main/java/com/example/consumer -maxdepth 2 -name '*Application.java' -exec grep -l '@SpringBootApplication' {} \; | head -1`
  </acceptance_criteria>
  <verify>
    <automated>test -f consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qE '^package com\.example\.consumer\.observability;$' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'gaugeBuilder("orders.queue.depth.estimate")' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF '.ofLongs()' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'ThreadLocalRandom.current().nextInt(0, 50)' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qE '^\s*@PreDestroy$' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && grep -qF 'gauge.close();' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java && [ "$(grep -c 'gaugeBuilder' consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java)" -eq 1 ] && mvn -pl consumer-service compile -q 2>&1 | tail -3 | grep -qE 'BUILD SUCCESS|^\[INFO\]'</automated>
  </verify>
  <done>QueueDepthGauge.java created at consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java: @Component constructor-injects Meter, builds ObservableLongGauge "orders.queue.depth.estimate" with .ofLongs() and buildWithCallback(measurement -> measurement.record(ThreadLocalRandom.current().nextInt(0, 50))) in the constructor, holds the gauge as a final field, and closes it defensively in @PreDestroy. JavaDoc cites D-16/D-17/D-18b/D-19 and METRIC-04. consumer-service compiles cleanly. OtelSdkConfiguration.java NOT modified — D-02 producer/consumer mirror property preserved.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 04-04 — consumer ObservableGauge)

| Boundary | Description |
|----------|-------------|
| Spring DI container → QueueDepthGauge constructor | Standard in-process DI; no new boundary. |
| QueueDepthGauge callback → SdkMeterProvider → OTLP gRPC :4317 | Already crossed by Plan 04-01's metric pipeline; the gauge's callback runs on the `PeriodicMetricReader` worker thread on every 10s interval. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-04-01 | Information Disclosure | The synthetic gauge value (random 0-49) reveals nothing — but a future PR could replace `ThreadLocalRandom` with a real RabbitMQ Management API call, which WOULD require the `:15672` credentials | n/a (workshop scope) | METRIC-04 is locked as "synthetic" by REQUIREMENTS. A future migration to a real measurement would need its own threat-model review (RabbitMQ Mgmt API call adds an HTTP egress, an auth dependency, and a possible PII-leak risk if queue names embed identifiers). Out of scope here. Severity: n/a. |
| T-04-04-02 | Denial of Service | A misimplementation of the callback (e.g., calling a slow / blocking I/O) could starve the meter reader thread, delaying ALL metric exports from this `SdkMeterProvider` (not just this gauge) | mitigate | The callback is `ThreadLocalRandom.current().nextInt(0, 50)` — allocation-free, CPU-only, sub-microsecond. The class-level JavaDoc explicitly documents "callback discipline (cheap, side-effect-free)". A future PR violating this would be caught by code review and (if missed) by latency monitoring on the meter export pipeline. Severity: low. |
| T-04-04-03 | Repudiation | If `gauge.close()` is not called at shutdown, stale callback invocations could continue briefly during teardown | mitigate | `@PreDestroy gauge.close()` is the explicit cleanup point. Spring guarantees PreDestroy invocation in reverse-DI-graph order before the `OpenTelemetry` bean's `destroyMethod="close"` cascade fires (Plan 04-01 D-07). Severity: low. |

**Phase scope:** One small new file with one synthetic random value. No new threat surface; the only non-trivial item is callback hygiene (T-04-04-02), mitigated in source.
</threat_model>

<verification>
- File created at consumer-service/src/main/java/com/example/consumer/observability/QueueDepthGauge.java with package com.example.consumer.observability.
- @Component annotation; constructor takes single Meter parameter; gauge built once as a final field via meter.gaugeBuilder("orders.queue.depth.estimate").setDescription(...).setUnit("{messages}").ofLongs().buildWithCallback(measurement -> measurement.record(ThreadLocalRandom.current().nextInt(0, 50))).
- @PreDestroy method calls gauge.close() defensively.
- All 5 imports present (ThreadLocalRandom, Meter, ObservableLongGauge, PreDestroy, Component).
- JavaDoc references D-16, D-17, D-18b, D-19, METRIC-04, and the synthetic-value caveat.
- Single gaugeBuilder call (instrument built once).
- consumer-service mvn compile exits 0.
- consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java is UNMODIFIED since Plan 04-01 — the D-18b choice preserves the producer/consumer mirror property.
</verification>

<success_criteria>
- METRIC-04 (ObservableGauge `orders.queue.depth.estimate` with synthetic value) satisfied at the source level. Live verification (fresh sample lands in Mimir every 10s, proving the PeriodicMetricReader interval override) is Plan 04-05's gate.
- D-16 (consumer-side gauge), D-17 (ThreadLocalRandom synthetic), D-18b (separate @Component, NOT inline in OtelSdkConfiguration — preserves D-02 mirror), D-19 (.ofLongs() flavor) honored.
- Phase 2 D-01 (per-service duplication of OtelSdkConfiguration) preserved — Plan 04-04 does NOT touch OtelSdkConfiguration; the D-18b choice is what makes this preservation work.
</success_criteria>

<output>
After completion, create `.planning/phases/04-metrics/04-04-SUMMARY.md` documenting:
- The full content of the new QueueDepthGauge.java file (paste once for the SUMMARY record).
- Confirmation that consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java is byte-identical to its Plan 04-01 output (paste git diff against the post-04-01 baseline showing zero changes).
- mvn -pl consumer-service compile output (last 3 lines — should be BUILD SUCCESS).
- Note on the D-18a vs D-18b choice — adopted D-18b per PATTERNS.md recommendation (preserves producer/consumer mirror).
- Files modified: 0; new files: 1 (QueueDepthGauge.java); 0 new pom dependencies.
</output>
</content>
</invoke>