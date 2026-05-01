---
id: 03-03-consumer-wiring
phase: 03-amqp-context-propagation
plan: 03
type: execute
wave: 2
depends_on: [03-01-otel-bootstrap-amqp-classes]
requirements: [PROP-02, PROP-03, PROP-04]
files_modified:
  - consumer-service/pom.xml
  - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java
  - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
autonomous: true
objective: "Wire the consumer side of AMQP context propagation: add com.example:otel-bootstrap dependency to consumer-service/pom.xml; declare @Bean TracingMessageListenerAdvice + Configurer-aided @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...) with setAdviceChain + setDefaultRequeueRejected(false) (per D-08); DELETE Phase 2's inline CONSUMER span body from OrderListener.onOrder (lines 46-79) and DELETE the Tracer constructor parameter (per D-09)."
must_haves:
  truths:
    - "consumer-service/pom.xml has new <dependency> on com.example:otel-bootstrap with version=${project.version}"
    - "consumer-service/.../config/RabbitConfig.java declares @Bean TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry, Tracer) — pure constructor wrapper per D-01"
    - "consumer-service/.../config/RabbitConfig.java declares @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...) — bean method name MUST be exactly 'rabbitListenerContainerFactory' (lowercase r) per Pitfall #7 — Spring Boot's RabbitAnnotationDrivenConfiguration backs off via @ConditionalOnMissingBean(name=\"rabbitListenerContainerFactory\")"
    - "Inside the listener factory bean: configurer.configure(factory, connectionFactory) runs FIRST, THEN factory.setAdviceChain(tracingAdvice), THEN factory.setDefaultRequeueRejected(false) — order is critical per Pitfall #5 (D-08)"
    - "factory.setDefaultRequeueRejected(false) is set per D-13 — failed messages NACK without requeue and (with no DLX per PROJECT.md) the broker drops them"
    - "Existing 2 beans in consumer's RabbitConfig (ordersCreatedQueue, jsonMessageConverter) AND the QUEUE constant are PRESERVED unchanged"
    - "OrderListener.onOrder(Map) becomes a thin 3-line body: extract orderId from map, LOG.info, call processingService.process(message) — per D-09 (Phase 2's inline CONSUMER span body lines 46-79 is DELETED)"
    - "OrderListener constructor takes only ProcessingService (Tracer parameter is REMOVED per D-09); the Tracer field is DELETED"
    - "OrderListener imports for io.opentelemetry.* + MessagingIncubatingAttributes + Context + Scope are DELETED; Map, Logger, LoggerFactory, RabbitListener, Component, RabbitConfig, ProcessingService imports remain"
    - "@RabbitListener(queues = RabbitConfig.QUEUE) annotation is preserved — its handler is now wrapped by the user-defined SimpleRabbitListenerContainerFactory whose advice chain includes TracingMessageListenerAdvice"
    - "Consumer-service still compiles: mvn -pl consumer-service compile exits 0; existing context-loads test passes"
    - "OrderListener.onOrder file size shrinks from 81 lines to ~30-50 lines (net -27 lines per CONTEXT.md <specifics>)"
  artifacts:
    - path: "consumer-service/pom.xml"
      provides: "Adds <dependency>com.example:otel-bootstrap:${project.version}</dependency> at top of <dependencies> block"
      contains: "<artifactId>otel-bootstrap</artifactId>"
    - path: "consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java"
      provides: "Adds @Bean TracingMessageListenerAdvice + Configurer-aided @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...) with setAdviceChain + setDefaultRequeueRejected(false)"
      contains: "rabbitListenerContainerFactory"
    - path: "consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java"
      provides: "Thin pass-through onOrder() — 3 lines (extract orderId + LOG.info + processingService.process); Tracer constructor param + field DELETED; all OTel imports DELETED; @RabbitListener annotation preserved"
      contains: "processingService.process(message)"
  key_links:
    - from: "consumer-service/RabbitConfig.@Bean rabbitListenerContainerFactory"
      to: "TracingMessageListenerAdvice (otel-bootstrap)"
      via: "factory.setAdviceChain(tracingAdvice) — Spring AOP ProxyFactory wraps ContainerDelegate.invokeListener(Channel, Object) so the advice runs BEFORE the user @RabbitListener method body"
      pattern: "setAdviceChain"
    - from: "TracingMessageListenerAdvice.invoke"
      to: "OrderListener.onOrder (via inv.proceed())"
      via: "Synchronous, same-thread call chain (RESEARCH FLAG #1 verified); Scope.makeCurrent() opened by advice IS visible to onOrder body"
      pattern: "inv.proceed"
    - from: "Spring Boot RabbitAnnotationDrivenConfiguration's auto-listener-factory"
      to: "BACKED OFF by @ConditionalOnMissingBean(name=\"rabbitListenerContainerFactory\")"
      via: "Bean method name = bean name — MUST match exactly 'rabbitListenerContainerFactory' (Pitfall #7)"
      pattern: "rabbitListenerContainerFactory"
    - from: "ProcessingFailedException thrown deep in ProcessingService.process (plan 03-04)"
      to: "TracingMessageListenerAdvice catch (Throwable) → recordException + setStatus(ERROR) on CONSUMER span"
      via: "Exception propagates through thin onOrder (no catch) → caught by advice; combined with defaultRequeueRejected=false the broker drops the failed message (no DLX per PROJECT.md)"
      pattern: "catch \\(Throwable"
---

<objective>
Wire the consumer-service side of AMQP context propagation: add the `com.example:otel-bootstrap` Maven dependency, declare two new `@Bean` methods in `RabbitConfig.java` (`TracingMessageListenerAdvice` + a Configurer-aided `SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...)` that wires the advice chain AND sets `defaultRequeueRejected=false` for APP-04 safety), and DELETE Phase 2's inline CONSUMER span body from `OrderListener.onOrder(...)` along with the `Tracer` constructor parameter.

This plan delivers PROP-02 (consumer-side extract is now wired into the listener path), PROP-03 (the consumer span's `parentSpanId` will equal the producer's `spanId` after this plan + 03-02 are both deployed — the LOAD-BEARING `.setParent(extracted)` line is in plan 03-01's `TracingMessageListenerAdvice`, but it only takes effect once the advice is registered HERE), and the consumer-side half of PROP-04. The DELETION half of the load-bearing `step-02-traces..step-03-context-propagation` git diff lands here (~27 deleted lines from `OrderListener.java`).

Purpose: PROP-02 (reader side wired into listener path), PROP-03 (joined trace materializes — runtime-observable in plan 03-05), PROP-04 (per-service explicit `@Bean` wiring), and the consumer-side contribution to the smallest-readable-diff property (ROADMAP SC #5).

Output: 1 modified `pom.xml` (~10 added lines), 1 modified `RabbitConfig.java` (~70 added lines for two new `@Bean` methods + JavaDoc + 6 new imports), 1 modified `OrderListener.java` (~33 deleted lines + 9 deleted imports + 1 changed constructor signature + updated class-level JavaDoc).
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
@.planning/phases/03-amqp-context-propagation/03-CONTEXT.md
@.planning/phases/03-amqp-context-propagation/03-RESEARCH.md
@.planning/phases/03-amqp-context-propagation/03-PATTERNS.md
@.planning/phases/03-amqp-context-propagation/03-01-otel-bootstrap-amqp-classes-PLAN.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-consumer-instrumentation-PLAN.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@CLAUDE.md
@consumer-service/pom.xml
@consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java
@consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
@consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java

<interfaces>
<!-- The consumer-side contracts the executor needs. -->

From `otel-bootstrap` (Plan 03-01 just produced this):
```java
package com.example.otel.amqp;
public class TracingMessageListenerAdvice implements org.aopalliance.intercept.MethodInterceptor {
    public TracingMessageListenerAdvice(io.opentelemetry.api.OpenTelemetry openTelemetry,
                                        io.opentelemetry.api.trace.Tracer tracer);
    @Override public Object invoke(MethodInvocation inv) throws Throwable;
}
```

Already wired in consumer-service/.../config/OtelSdkConfiguration.java (Phase 2):
```java
@Bean(destroyMethod = "close")
OpenTelemetrySdk openTelemetrySdk() { /* ... */ }   // exposed as OpenTelemetry interface

@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.consumer");
}
```

Existing consumer-service/.../config/RabbitConfig.java (Phase 2 — 18 lines):
```java
@Configuration
public class RabbitConfig {
    public static final String QUEUE = "orders.created";
    @Bean Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }
    @Bean MessageConverter jsonMessageConverter() { return new Jackson2JsonMessageConverter(); }
}
```

Existing consumer-service/.../messaging/OrderListener.java (Phase 2 — 81 lines):
```java
@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;
    private final Tracer tracer;                                  // <-- DELETE in T3
    public OrderListener(ProcessingService, Tracer);              // <-- (Tracer) DELETE in T3

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrder(Map<String, Object> message) {
        // ~33 lines of inline CONSUMER span: setParent(Context.root()) +
        // 3 messaging attrs + try/Scope/catch/finally — ALL DELETED in T3
    }
}
```

Spring Boot listener factory backoff (RESEARCH.md "RabbitAutoConfiguration Backoff"):
- `RabbitAnnotationDrivenConfiguration.@Bean(name = "rabbitListenerContainerFactory")` is annotated with `@ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")` + `@ConditionalOnProperty(prefix = "spring.rabbitmq.listener", name = "type", havingValue = "simple", matchIfMissing = true)`.
- A user-defined `@Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...)` (matching by EXACT bean name — bean method name = bean name) backs off the auto-bean.
- `SimpleRabbitListenerContainerFactoryConfigurer` is exposed as a bean (named `simpleRabbitListenerContainerFactoryConfigurer`); injectable by type.

Configurer behavior (RESEARCH.md "Configurer Order Semantics" lines 461-486):
- `configurer.configure(factory, connectionFactory)` sets ~13 properties on the factory in a fixed order.
- It does NOT touch `adviceChain` UNLESS `spring.rabbitmq.listener.simple.retry.enabled=true` (default `false`).
- It does NOT touch `defaultRequeueRejected` UNLESS the property `spring.rabbitmq.listener.simple.default-requeue-rejected` is set (default unset).
- Calling `setAdviceChain(tracingAdvice)` and `setDefaultRequeueRejected(false)` AFTER `configure(...)` is safe and overwrites whatever the Configurer set (which in the demo's default config is "nothing").
</interfaces>
</context>

<tasks>

<task id="03-03-T1" type="auto">
  <name>Task 1: Add com.example:otel-bootstrap dependency to consumer-service/pom.xml (mirrors plan 03-02 T1)</name>
  <files>consumer-service/pom.xml</files>
  <read_first>
    - consumer-service/pom.xml (current state — 117 lines, 8 dependencies, mirrors producer-service's pom; per RESEARCH.md "Existing Code Confirmation" line 840, currently has NO dependency on otel-bootstrap)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 631-647 — IDENTICAL EDIT to producer-service/pom.xml; same coordinate, same location)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (line 119-120 — both pom.xml files must add this dep)
    - .planning/phases/03-amqp-context-propagation/03-02-producer-wiring-PLAN.md Task 1 (the analog edit on producer-service — this plan's edit is symmetric)
  </read_first>
  <action>
    Open `consumer-service/pom.xml`. Find the `<dependencies>` block (starts at line 20).

    INSERT a new `<dependency>` block at the TOP of `<dependencies>` (BEFORE the existing `spring-boot-starter-amqp` dep at line 36). This places the intra-reactor dep visually distinct from external deps.

    Add this exact block (with one blank line after for visual separation):

    ```xml
        <!--
          Phase 3: shared otel-bootstrap module. Provides the AMQP context
          propagation pair — TracingMessagePostProcessor (PRODUCER inject)
          and TracingMessageListenerAdvice (CONSUMER extract). Consumer uses
          only the listener advice (registered on
          SimpleRabbitListenerContainerFactory.setAdviceChain in RabbitConfig).
          The version ${project.version} resolves to 0.1.0-SNAPSHOT via
          parent POM inheritance (multi-module reactor convention).
        -->
        <dependency>
          <groupId>com.example</groupId>
          <artifactId>otel-bootstrap</artifactId>
          <version>${project.version}</version>
        </dependency>

    ```

    (insert it BETWEEN the `<dependencies>` opening tag and the existing first dep — `spring-boot-starter-amqp`).

    DO NOT modify any existing dependency. DO NOT change scope on any existing dep.

    Verify with `mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap`. Expect to see `com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT:compile`. Then confirm the reactor builds: `mvn -pl otel-bootstrap,consumer-service -am compile` exits 0.
  </action>
  <acceptance_criteria>
    - otel-bootstrap dep present: `grep -A2 '<artifactId>otel-bootstrap</artifactId>' consumer-service/pom.xml | grep -q '\${project.version}'` exits 0
    - Dep groupId is com.example: `grep -B1 '<artifactId>otel-bootstrap</artifactId>' consumer-service/pom.xml | grep -q '<groupId>com.example</groupId>'` exits 0
    - Dep is BEFORE spring-boot-starter-amqp: `awk '/<artifactId>otel-bootstrap</{ob=NR} /<artifactId>spring-boot-starter-amqp</{sa=NR} END{exit (ob<sa)?0:1}' consumer-service/pom.xml` exits 0
    - Default scope (no explicit <scope> tag): `awk '/<artifactId>otel-bootstrap</,/<\/dependency>/' consumer-service/pom.xml | grep -c '<scope>'` returns 0
    - Existing 8 deps still present: `for d in spring-boot-starter-amqp spring-boot-starter-actuator spring-boot-starter-web opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp opentelemetry-semconv opentelemetry-semconv-incubating; do grep -q "<artifactId>$d</artifactId>" consumer-service/pom.xml || exit 1; done` exits 0
    - Maven reactor compiles: `mvn -q -pl otel-bootstrap,consumer-service -am compile` exits 0
    - Dependency tree shows otel-bootstrap: `mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap 2>&amp;1 | grep -q 'com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT'` exits 0
  </acceptance_criteria>
  <verify>
    <automated>grep -q '<artifactId>otel-bootstrap</artifactId>' consumer-service/pom.xml &amp;&amp; grep -A2 '<artifactId>otel-bootstrap</artifactId>' consumer-service/pom.xml | grep -q '\${project.version}' &amp;&amp; mvn -q -pl otel-bootstrap,consumer-service -am compile</automated>
  </verify>
  <done>consumer-service/pom.xml gains one <dependency> on com.example:otel-bootstrap:${project.version} placed BEFORE spring-boot-starter-amqp; default compile scope; existing 8 deps unchanged; mvn compile passes for the consumer + otel-bootstrap reactor pair.</done>
</task>

<task id="03-03-T2" type="auto">
  <name>Task 2: Add @Bean TracingMessageListenerAdvice + Configurer-aided @Bean SimpleRabbitListenerContainerFactory to consumer's RabbitConfig.java (D-08 + D-13)</name>
  <files>consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java (current state — 18 lines, 2 @Bean methods, 1 public-static-final QUEUE constant per RESEARCH.md "Existing Code Confirmation" lines 828-830)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 670-740 — exact code shape for the new beans; lines 460-486 — Configurer Order Semantics; lines 488-496 — Backoff via @ConditionalOnMissingBean(name=\"rabbitListenerContainerFactory\"); lines 545-564 — Pitfall #5 (advice chain order) + Pitfall #6 (batch listener) + Pitfall #7 (bean name match))
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 421-457 — RabbitConfig pattern + 7-item transformation notes; lines 444-453 — exact factory bean body)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 82-95 — D-08 Configurer-aided shape; lines 117-132 — D-13 setDefaultRequeueRejected(false) safety)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java (just produced by Plan 03-01 — confirms constructor signature is (OpenTelemetry, Tracer))
  </read_first>
  <action>
    Open `consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` (currently 18 lines).

    REPLACE THE ENTIRE FILE with the EXACT content below. The result preserves the existing 2 beans + QUEUE constant and adds 2 new beans + 6 new imports + JavaDoc.

    ```java
    package com.example.consumer.config;

    import com.example.otel.amqp.TracingMessageListenerAdvice;
    import io.opentelemetry.api.OpenTelemetry;
    import io.opentelemetry.api.trace.Tracer;

    import org.springframework.amqp.core.Queue;
    import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
    import org.springframework.amqp.rabbit.connection.ConnectionFactory;
    import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
    import org.springframework.amqp.support.converter.MessageConverter;
    import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;

    @Configuration
    public class RabbitConfig {
        public static final String QUEUE = "orders.created";

        @Bean
        Queue ordersCreatedQueue() {
            return new Queue(QUEUE, true);
        }

        @Bean
        MessageConverter jsonMessageConverter() {
            return new Jackson2JsonMessageConverter();
        }

        // ---- Phase 3 NEW: AMQP context propagation wiring ----

        /**
         * Phase 3: registers the consumer side of W3C AMQP context
         * propagation. {@link TracingMessageListenerAdvice} owns the entire
         * CONSUMER span lifecycle (CONTEXT.md D-09) — Phase 2's inline
         * CONSUMER span in {@code OrderListener.onOrder(...)} is deleted and
         * replaced by this advice that fires AROUND every
         * {@code @RabbitListener}-annotated method invocation.
         *
         * <p>Pure constructor wrapper per D-01 — the propagation classes
         * carry NO Spring annotations themselves; the wiring is explicit
         * here.
         */
        @Bean
        TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry openTelemetry,
                                                                   Tracer tracer) {
            return new TracingMessageListenerAdvice(openTelemetry, tracer);
        }

        /**
         * Phase 3: Configurer-aided {@link SimpleRabbitListenerContainerFactory}
         * bean overriding Spring Boot's auto-created factory. The bean method
         * name MUST be exactly {@code rabbitListenerContainerFactory}
         * (lowercase r) — Pitfall #7. Spring Boot's
         * {@code RabbitAnnotationDrivenConfiguration.simpleRabbitListenerContainerFactory(...)}
         * uses {@code @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")},
         * which is an EXACT name match; renaming this bean would create two
         * factories and {@code @RabbitListener} would resolve the wrong one.
         *
         * <p><strong>Order matters (CONTEXT.md D-08 + Pitfall #5).</strong>
         * The Configurer applies any {@code spring.rabbitmq.listener.simple.*}
         * properties (concurrency, prefetch, etc.) FIRST. THEN we set the
         * tracing advice chain. FINALLY we flip {@code defaultRequeueRejected}
         * to {@code false}.
         *
         * <p><strong>setAdviceChain wiring (CONTEXT.md D-08, RESEARCH FLAG
         * #1 + #3).</strong> Spring AOP wraps
         * {@code ContainerDelegate.invokeListener(Channel, Object)} — the
         * advice's {@code MethodInvocation.getArguments()[1]} is the
         * {@code Message}. The advice chain runs SYNCHRONOUSLY on the same
         * thread as the user {@code @RabbitListener} method body, so the
         * {@code Scope.makeCurrent()} the advice opens IS visible to the
         * listener body.
         *
         * <p><strong>setDefaultRequeueRejected(false) (CONTEXT.md D-13).</strong>
         * On listener exception (e.g., the deterministic 10%-failure
         * {@code ProcessingFailedException} from APP-04 — see plan 03-04),
         * Spring AMQP NACKs without requeue. With NO Dead-Letter Exchange
         * configured (PROJECT.md excludes DLX), the broker drops the message.
         * This breaks the otherwise-infinite NACK-requeue loop that would
         * spam Tempo with error spans for the same poison message.
         */
        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
                ConnectionFactory connectionFactory,
                SimpleRabbitListenerContainerFactoryConfigurer configurer,
                TracingMessageListenerAdvice tracingAdvice) {
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            // 1. Apply Spring Boot defaults + spring.rabbitmq.listener.simple.* properties.
            configurer.configure(factory, connectionFactory);
            // 2. Wrap every listener invocation with the tracing advice.
            factory.setAdviceChain(tracingAdvice);
            // 3. APP-04 safety: drop failed messages instead of requeue
            //    (no DLX per PROJECT.md).
            factory.setDefaultRequeueRejected(false);
            return factory;
        }
    }
    ```

    Verify the final state:
    - File now has 4 `@Bean` methods (2 existing + 2 new).
    - The QUEUE constant is unchanged.
    - The class is still annotated `@Configuration` only.
    - `mvn -pl consumer-service compile` exits 0.

    CRITICAL constraints:
    1. Bean method name MUST be exactly `rabbitListenerContainerFactory` (lowercase 'r') — Spring Boot's auto-config matches this exact string (Pitfall #7).
    2. Order: `configurer.configure(...)` MUST come BEFORE `setAdviceChain(...)` and `setDefaultRequeueRejected(...)` (Pitfall #5).
    3. The `setDefaultRequeueRejected(false)` argument is `false` literal (boolean).
  </action>
  <acceptance_criteria>
    - File compiles: `mvn -q -pl consumer-service compile` exits 0
    - @Bean TracingMessageListenerAdvice present: `grep -q 'TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - @Bean factory has EXACT name 'rabbitListenerContainerFactory' (lowercase r, Pitfall #7): `grep -q 'SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - Configurer.configure called: `grep -q 'configurer.configure(factory, connectionFactory)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - setAdviceChain called: `grep -q 'factory.setAdviceChain(tracingAdvice)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - setDefaultRequeueRejected(false) called (D-13): `grep -q 'factory.setDefaultRequeueRejected(false)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - Order check (Pitfall #5): configure() comes BEFORE setAdviceChain() in source order: `awk '/configurer.configure/{c=NR} /setAdviceChain/{a=NR} END{exit (c<a)?0:1}' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - Order check: setAdviceChain comes BEFORE setDefaultRequeueRejected: `awk '/setAdviceChain/{a=NR} /setDefaultRequeueRejected/{r=NR} END{exit (a<r)?0:1}' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - Required new imports added: `for i in 'com.example.otel.amqp.TracingMessageListenerAdvice' 'io.opentelemetry.api.OpenTelemetry' 'io.opentelemetry.api.trace.Tracer' 'org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory' 'org.springframework.amqp.rabbit.connection.ConnectionFactory' 'org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer'; do grep -q "^import $i;" consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java || exit 1; done` exits 0
    - Existing 2 beans preserved: `grep -q 'Queue ordersCreatedQueue' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java &amp;&amp; grep -q 'MessageConverter jsonMessageConverter' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - QUEUE constant preserved: `grep -qF 'QUEUE = "orders.created"' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - Total @Bean count is 4: `grep -c '^    @Bean$' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` returns 4
    - JavaDoc references D-08, D-13, Pitfall #5, Pitfall #7: `grep -cE 'D-08|D-13|Pitfall #5|Pitfall #7' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` returns >= 4
    - No @AutoConfiguration / @Conditional* annotations: `grep -cE '@(AutoConfiguration|ConditionalOn)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` returns 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl consumer-service compile &amp;&amp; grep -q 'SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java &amp;&amp; grep -q 'configurer.configure(factory, connectionFactory)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java &amp;&amp; grep -q 'factory.setAdviceChain(tracingAdvice)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java &amp;&amp; grep -q 'factory.setDefaultRequeueRejected(false)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java &amp;&amp; awk '/configurer.configure/{c=NR} /setAdviceChain/{a=NR} END{exit (c<a)?0:1}' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java &amp;&amp; [ "$(grep -c '^    @Bean$' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java)" = "4" ]</automated>
  </verify>
  <done>consumer-service's RabbitConfig.java has 4 @Bean methods (2 existing + 2 new); the listener factory bean is named EXACTLY 'rabbitListenerContainerFactory'; configurer.configure runs first, then setAdviceChain, then setDefaultRequeueRejected(false); existing 2 beans and QUEUE constant preserved; consumer compiles cleanly.</done>
</task>

<task id="03-03-T3" type="auto">
  <name>Task 3: DELETE Phase 2 inline CONSUMER span body from OrderListener.onOrder; remove Tracer constructor param + field; clean imports (D-09)</name>
  <files>consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java</files>
  <read_first>
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (current state — 81 lines; the inline CONSUMER span body lives at lines 47-79 inside the onOrder() method including the verbatim D-10 multi-line teaching comment that previewed Phase 3's replacement)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 808-822 — exact deletion target with line numbers; final post-Phase-3 method shape)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 299-362 — OrderListener transformation: 7-item transformation notes including imports to delete)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 98-107 — D-09: thin pass-through; LOG.info runs INSIDE CONSUMER span scope)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-consumer-instrumentation-PLAN.md (the plan that originally CREATED the inline CONSUMER span — confirms the verbatim D-10 multi-line teaching comment that's being DELETED)
  </read_first>
  <action>
    Open `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` (currently 81 lines).

    REPLACE THE ENTIRE FILE with the EXACT content below. The result is a thin pass-through `onOrder(...)` per D-09. The `@RabbitListener(queues = RabbitConfig.QUEUE)` annotation is preserved — Spring AMQP picks the user-defined `rabbitListenerContainerFactory` (registered in plan 03-03 T2) automatically.

    ```java
    package com.example.consumer.messaging;

    import java.util.Map;

    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.amqp.rabbit.annotation.RabbitListener;
    import org.springframework.stereotype.Component;

    import com.example.consumer.config.RabbitConfig;
    import com.example.consumer.domain.ProcessingService;

    /**
     * AMQP listener — processes OrderCreated messages from the
     * {@code orders.created} queue.
     *
     * <p>Phase 3 made this method a thin 3-line pass-through. The CONSUMER
     * span and W3C trace context extraction are owned by
     * {@code com.example.otel.amqp.TracingMessageListenerAdvice}, which is
     * registered on the {@link RabbitListener}'s container factory via
     * {@code SimpleRabbitListenerContainerFactory.setAdviceChain(...)} —
     * see {@link com.example.consumer.config.RabbitConfig#rabbitListenerContainerFactory}.
     *
     * <p><strong>Why moved (CONTEXT.md D-09).</strong> Phase 2's inline
     * CONSUMER span body here was a deliberate pedagogical preview of
     * Phase 3's {@code propagator.extract(...)} line — but a span built
     * inline inside the listener body cannot capture deserialization or
     * framework-level errors that happen BEFORE the body runs. Lifting the
     * span into the advice chain catches the entire listener invocation
     * (RESEARCH.md FLAG #1).
     *
     * <p><strong>Scope context (CONTEXT.md D-09 + RESEARCH FLAG #1).</strong>
     * The advice's {@code Scope.makeCurrent()} is active when this method
     * body runs (synchronous, same-thread). So {@code Span.current()} here
     * IS the CONSUMER span — Phase 5's MDC injector will pick up the
     * correct {@code trace_id} / {@code span_id} for the LOG.info line
     * below without any additional code.
     */
    @Component
    public class OrderListener {
        private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
        private final ProcessingService processingService;

        public OrderListener(ProcessingService processingService) {
            this.processingService = processingService;
        }

        @RabbitListener(queues = RabbitConfig.QUEUE)
        public void onOrder(Map<String, Object> message) {
            Object orderId = message.get("orderId");
            LOG.info("OrderCreated received: orderId={}", orderId);
            processingService.process(message);
        }
    }
    ```

    Then verify with `mvn -pl consumer-service compile`. Expect exit 0.
  </action>
  <acceptance_criteria>
    - File compiles: `mvn -q -pl consumer-service compile` exits 0
    - File line count between 25 and 60 (down from 81): `LINES=$(wc -l < consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java); python3 -c "l=$LINES; assert 25<=l<=60, l; print(f'OK: {l} lines')"` exits 0
    - Constructor is single-arg (Tracer param removed): `grep -q 'public OrderListener(ProcessingService processingService)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - Constructor does NOT take Tracer: `grep -c 'Tracer tracer' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 0
    - Tracer field deleted: `grep -c 'private final Tracer' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 0
    - @RabbitListener annotation preserved: `grep -q '@RabbitListener(queues = RabbitConfig.QUEUE)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - onOrder body has the 3 expected lines: `grep -q 'message.get("orderId")' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'LOG.info("OrderCreated received: orderId={}", orderId)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'processingService.process(message)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - NO OTel imports: `grep -cE 'import io\.opentelemetry|MessagingIncubatingAttributes|MessagingSystemIncubatingValues|MessagingOperationTypeIncubatingValues' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 0
    - NO inline span builder calls: `grep -cE 'spanBuilder|setSpanKind|recordException|setStatus|makeCurrent|startSpan|setParent' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 0
    - NO Context.root() reference: `grep -c 'Context.root' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 0
    - @Component annotation preserved: `grep -q '@Component' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - LOG/Logger preserved: `grep -q 'private static final Logger LOG' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - JavaDoc references D-09: `grep -q 'D-09' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl consumer-service compile &amp;&amp; grep -q 'processingService.process(message)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; ! grep -qE 'import io\.opentelemetry|spanBuilder|Context.root' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; [ "$(grep -c 'Tracer tracer' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java)" = "0" ] &amp;&amp; grep -q '@RabbitListener(queues = RabbitConfig.QUEUE)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java</automated>
  </verify>
  <done>OrderListener.java is the thin pass-through shape: ~30-50 lines, single-arg constructor (no Tracer), no OTel imports, no inline span scaffolding; just extracts orderId + LOG.info + processingService.process. @Component + @RabbitListener annotations preserved. JavaDoc explains the move per D-09. Consumer compiles cleanly.</done>
</task>

<task id="03-03-T4" type="auto">
  <name>Task 4: Consumer-side compile + smoke verification — full consumer build, listener factory bean wiring resolves, no Spring DI errors</name>
  <files>(none — verification only)</files>
  <read_first>
    - consumer-service/pom.xml (just modified by T1)
    - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java (just modified by T2)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (just modified by T3)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-05-SUMMARY.md (Phase 2 consumer smoke baseline)
  </read_first>
  <action>
    Run a clean build on the consumer-service module + its upstream (otel-bootstrap) and confirm Spring's bean wiring resolves correctly. NO live broker / runtime tests in this plan — that's the human-verify gate in Plan 03-05.

    Commands to run, in order:

    ```sh
    # 1. Clean compile of the consumer + its upstream module
    mvn -pl otel-bootstrap,consumer-service -am clean compile

    # 2. Run consumer-service unit tests (Phase 1's ConsumerApplicationTests.contextLoads bootstraps Spring)
    mvn -pl consumer-service test

    # 3. Quick Spring context-load smoke — package the JAR
    mvn -pl consumer-service -am package -DskipTests

    # 4. Verify the dependency resolution shows otel-bootstrap once
    mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap | grep -c 'com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT'
    # Expect: 1
    ```

    The `mvn test` step is critical — it bootstraps the Spring application context including the new RabbitConfig. Any of the following will surface as test failures:
    - "No qualifying bean of type SimpleRabbitListenerContainerFactoryConfigurer" → unlikely (Spring Boot exposes it; one of the autoconfig variants based on @ConditionalOnThreading)
    - "BeanDefinitionConflictException: 'rabbitListenerContainerFactory' already defined" → the auto-config didn't back off; investigate the bean method NAME (must be exactly `rabbitListenerContainerFactory`, lowercase r)
    - "Could not autowire field: ProcessingService" in OrderListener → impossible (we deleted Tracer; constructor only takes ProcessingService)
    - "ApplicationContextException" with no specific cause → typically a circular dep; check the bean's parameter list

    Note from Phase 2: the consumer's `ConsumerApplicationTests.contextLoads()` test uses `@SpringBootTest` which starts the full context but lazily connects to RabbitMQ. The test passes WITHOUT a live RabbitMQ container. Phase 2 verified this empirically.

    The goal of this task is to catch Spring wiring regressions BEFORE the human-verify gate in 03-05. If `mvn test` reports a wiring error related to the listener factory or OrderListener, T2 or T3 needs a fix.
  </action>
  <acceptance_criteria>
    - Clean compile passes: `mvn -q -pl otel-bootstrap,consumer-service -am clean compile` exits 0
    - Consumer unit tests pass (or are absent and skipped silently): `mvn -q -pl consumer-service test` exits 0
    - Consumer JAR repackages: `mvn -q -pl consumer-service -am package -DskipTests` exits 0; `test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar` exits 0
    - otel-bootstrap appears exactly ONCE in consumer's dependency tree: `[ "$(mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap 2>&amp;1 | grep -c 'com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT')" = "1" ]` exits 0
    - mise verify:bom still passes: `mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'` exits 0
    - No consumer source files outside scope changed: `git status --porcelain consumer-service/ | grep -v '^??' | grep -v 'pom.xml\|RabbitConfig.java\|OrderListener.java' | head -1 | wc -l` returns 0 (only the 3 expected files modified)
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl otel-bootstrap,consumer-service -am clean compile &amp;&amp; mvn -q -pl consumer-service test &amp;&amp; mvn -q -pl consumer-service -am package -DskipTests &amp;&amp; test -f consumer-service/target/consumer-service-0.1.0-SNAPSHOT.jar &amp;&amp; mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'</automated>
  </verify>
  <done>Clean compile + unit test + package all pass for the consumer module; otel-bootstrap appears exactly once in dependency tree; mise verify:bom invariant preserved; only the 3 expected files (pom.xml + RabbitConfig.java + OrderListener.java) show as modified.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 03-03 — consumer wiring)

| Boundary | Description |
|----------|-------------|
| AMQP wire (broker → consumer JVM) | `traceparent` header arrives on a wire-controlled channel; the advice's defensive `instanceof Message` guard (Pitfall #6) and the getter's `.toString()` normalization (Pitfall #2) are the trust mediators |
| Spring auto-config → user-defined listener factory | Bean-name match is the gate; failure mode is silent (auto-config doesn't back off → `@RabbitListener` resolves auto-bean → tracing advice never runs) |
| Listener thread → user method body | Verified per RESEARCH FLAG #1 to be SAME thread (no Reactor switch); `Scope.makeCurrent()` propagates correctly |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-3-03-01 | Spoofing | Malicious upstream injects a crafted `traceparent` to link unrelated traces or DoS via parser exception | mitigate | `W3CTraceContextPropagator.extract` (used inside `TracingMessageListenerAdvice` from plan 03-01) validates format strictly — invalid traceparent silently falls back to `Context.current()` (which is `Context.root()` on the listener thread). Workshop scope: only our own producer publishes; production posture documented in JavaDoc on the advice class. |
| T-3-03-02 | DoS | Failed-message storm: if `defaultRequeueRejected=true` AND a poison message persists, NACK-requeue loops forever, spamming Tempo and pinning CPU | mitigate | D-13 sets `defaultRequeueRejected=false`. The 10th-order failure path is deterministic, but each failed message NACKs without requeue; the broker drops it (no DLX per PROJECT.md). Counter-loop avoided. |
| T-3-03-03 | Repudiation | Listener thread silently swallows an exception | mitigate | The advice's `catch (Throwable t) { recordException + setStatus(ERROR); throw t; }` (in plan 03-01's `TracingMessageListenerAdvice`) rethrows after recording. Spring AMQP container then NACKs. Fail-closed by default. |
| T-3-03-04 | Information Disclosure | Span attributes (`messaging.destination.name`, `messaging.rabbitmq.destination.routing_key`) expose AMQP topology | accept | Workshop-local strings; no PII; same posture as Phase 2 (TC-2-04-02). |
| T-3-03-05 | Tampering | Future change adds `spring.rabbitmq.listener.simple.retry.enabled=true` and the Configurer overwrites the user's `setAdviceChain` (Pitfall #5) | mitigate | The bean method invokes `configurer.configure(...)` BEFORE `setAdviceChain(...)`. Even if a future user enables retry, the user's `setAdviceChain(tracingAdvice)` runs LAST and overwrites whatever the Configurer set. JavaDoc documents the order requirement explicitly. |
| T-3-03-06 | Spoofing via baggage | A future change adds `W3CBaggagePropagator` reading and propagates malicious baggage | accept | `W3CBaggagePropagator` IS already wired in OtelSdkConfiguration (Phase 2 D-16) but Phase 3 doesn't read baggage. Production hardening: validate baggage attribute names against an allow-list before reading. Documented in OtelSdkConfiguration JavaDoc. |

**No CRITICAL/HIGH security blockers.**
</threat_model>

<verification>
- consumer-service/pom.xml has new `<dependency>com.example:otel-bootstrap:${project.version}</dependency>` placed before spring-boot-starter-amqp.
- consumer-service/.../config/RabbitConfig.java has 4 `@Bean` methods (2 existing + 2 new); the listener factory bean is named EXACTLY `rabbitListenerContainerFactory`; configurer.configure runs first, then setAdviceChain, then setDefaultRequeueRejected(false).
- consumer-service/.../messaging/OrderListener.java is ~30-50 lines: thin `onOrder(...)` body (3 lines), single-arg constructor, no OTel imports, no Tracer field, @RabbitListener preserved.
- `mvn -pl otel-bootstrap,consumer-service -am clean compile` exits 0.
- `mvn -pl consumer-service test` exits 0 (Phase 1's `ConsumerApplicationTests.contextLoads()` confirms Spring context bootstraps with the new wiring).
- `mvn dependency:tree` shows `com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT` exactly once on the consumer.
- `mise run verify:bom` exits 0 — Phase 2 BOM invariant preserved.
- File modification list (git status) shows exactly: 1 modified pom.xml + 1 modified RabbitConfig.java + 1 modified OrderListener.java.
</verification>

<success_criteria>
- PROP-02 (reader side wired): `TracingMessageListenerAdvice` is registered on the consumer's `SimpleRabbitListenerContainerFactory.setAdviceChain(...)` — every `@RabbitListener` invocation triggers the W3C extract + CONSUMER span + `.setParent(extracted)`. End-to-end runtime verification deferred to plan 03-05's human-verify gate (Tempo shows joined trace).
- PROP-03 (joined trace materializes): once both 03-02 and 03-03 are deployed, `consumer.parentSpanId == producer.spanId` will be true at runtime. Verification deferred to 03-05.
- PROP-04 (per-service explicit `@Bean` wiring): consumer's `RabbitConfig.java` has 2 explicit `@Bean` declarations (advice + factory) — readers see ONE class file in `otel-bootstrap` mapped to ONE `@Bean` declaration here (parallel-symmetric to producer's plan 03-02).
- D-09 honored: Phase 2's inline CONSUMER span is DELETED; the advice owns the entire span lifecycle; OrderListener.onOrder is a thin pass-through.
- D-13 honored: `defaultRequeueRejected=false` set on the listener factory — APP-04 failures (plan 03-04) won't trigger NACK-requeue loops.
- Smallest-readable-diff property (ROADMAP SC #5): consumer-side contribution is +~70 added lines (RabbitConfig.java new beans with rich JavaDoc) + 4 added lines (pom.xml) - 33 deleted lines (OrderListener.java span body) - 6 deleted JavaDoc lines = net ~+35 lines on the consumer side.
- Phase 2 functionality intact: consumer module compiles, tests pass, BOM invariant holds.
</success_criteria>

<output>
After completion, create `.planning/phases/03-amqp-context-propagation/03-03-SUMMARY.md` documenting:
- The 3 modified files (paste their final paths)
- The line count delta on OrderListener.java (was 81 → now ~30-50; document exact line count)
- The dependency tree confirmation: paste `mvn -pl consumer-service dependency:tree -Dincludes=com.example:otel-bootstrap | head -10`
- The new RabbitConfig.java @Bean count (2 → 4) and confirmed bean name `rabbitListenerContainerFactory`
- The order verification: that `configurer.configure` precedes `setAdviceChain` precedes `setDefaultRequeueRejected` in source code
- mise verify:bom result
- Wave 2 hand-off: consumer-side wiring is complete; APP-04 failure path (plan 03-04) can now proceed; the human-verify smoke gate (plan 03-05) is deferred until APP-04 lands too
- Note any auto-config quirks observed (e.g., did Spring log a "rabbitListenerContainerFactory auto-config backed off" message?)
- Phase 3 hand-off chain: producer (03-02) is wired AND consumer (03-03) is wired → next, plan 03-04 adds the deterministic-10% throw site; plan 03-05 lands README delta + git tag at the user-approved gate
</output>
