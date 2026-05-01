---
id: 03-02-producer-wiring
phase: 03-amqp-context-propagation
plan: 02
type: execute
wave: 2
depends_on: [03-01-otel-bootstrap-amqp-classes]
requirements: [PROP-01, PROP-04]
files_modified:
  - producer-service/pom.xml
  - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java
  - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
autonomous: true
objective: "Wire the producer side of AMQP context propagation: add com.example:otel-bootstrap dependency to producer-service/pom.xml; declare @Bean TracingMessagePostProcessor + an explicit @Bean RabbitTemplate that calls setBeforePublishPostProcessors(mpp) (per D-05); DELETE Phase 2's inline PRODUCER span body from OrderPublisher.publish (lines 39-83) and DELETE the Tracer constructor parameter."
must_haves:
  truths:
    - "producer-service/pom.xml has a new <dependency> on com.example:otel-bootstrap with version=${project.version} (resolves to 0.1.0-SNAPSHOT via parent POM inheritance)"
    - "producer-service/.../config/RabbitConfig.java declares @Bean TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry, Tracer) — pure constructor wrapper per D-01"
    - "producer-service/.../config/RabbitConfig.java declares an explicit @Bean RabbitTemplate that calls template.setBeforePublishPostProcessors(tracingMpp) — replaces Spring Boot's auto-created RabbitTemplate per D-05 (RabbitAutoConfiguration backs off via @ConditionalOnMissingBean(RabbitOperations.class))"
    - "Existing 4 beans in producer's RabbitConfig (ordersExchange, ordersCreatedQueue, ordersBinding, jsonMessageConverter) AND the EXCHANGE/QUEUE/ROUTING_KEY constants are PRESERVED unchanged"
    - "OrderPublisher.publish(...) becomes a thin 3-line body: build message map, set orderId, call rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message) — per D-05 (Phase 2's inline PRODUCER span body lines 39-83 is DELETED)"
    - "OrderPublisher constructor takes only RabbitTemplate (Tracer parameter is REMOVED per D-05); the Tracer field is DELETED"
    - "OrderPublisher imports for io.opentelemetry.* + MessagingIncubatingAttributes + value enums are DELETED; only HashMap, Map, RabbitTemplate, Component, RabbitConfig imports remain"
    - "Producer-service still compiles after the dependency add + the source edits; mvn -pl producer-service compile exits 0"
    - "OrderPublisher.publish file size shrinks from 85 lines to ~30 lines (net -33 lines per CONTEXT.md <specifics>)"
  artifacts:
    - path: "producer-service/pom.xml"
      provides: "Adds <dependency>com.example:otel-bootstrap:${project.version}</dependency> at top of <dependencies> block"
      contains: "<artifactId>otel-bootstrap</artifactId>"
    - path: "producer-service/src/main/java/com/example/producer/config/RabbitConfig.java"
      provides: "Adds @Bean TracingMessagePostProcessor + explicit @Bean RabbitTemplate(ConnectionFactory, MessageConverter, TracingMessagePostProcessor) calling setBeforePublishPostProcessors"
      contains: "setBeforePublishPostProcessors"
    - path: "producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java"
      provides: "Thin pass-through publish() — 3 lines (build message + put orderId + convertAndSend); Tracer constructor param + field DELETED; all OTel imports DELETED"
      contains: "rabbitTemplate.convertAndSend"
  key_links:
    - from: "producer-service/RabbitConfig.@Bean RabbitTemplate"
      to: "TracingMessagePostProcessor (otel-bootstrap)"
      via: "template.setBeforePublishPostProcessors(tracingMpp) — invoked by RabbitTemplate.doSend on every convertAndSend (RESEARCH FLAG #2: 4-arg postProcessMessage is what Spring AMQP 3.2.8 actually calls)"
      pattern: "setBeforePublishPostProcessors"
    - from: "OrderPublisher.publish"
      to: "TracingMessagePostProcessor.postProcessMessage(4-arg)"
      via: "rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message) → MessageConverter produces body → setBeforePublishPostProcessors hook fires → PRODUCER span + traceparent header injected"
      pattern: "convertAndSend"
    - from: "Spring Boot RabbitAutoConfiguration's auto-RabbitTemplate"
      to: "BACKED OFF by user-defined @Bean RabbitTemplate via @ConditionalOnMissingBean(RabbitOperations.class)"
      via: "RabbitTemplate implements RabbitOperations → conditional matches → auto-bean not created"
      pattern: "@Bean.*RabbitTemplate"
---

<objective>
Wire the producer-service side of AMQP context propagation: add the `com.example:otel-bootstrap` Maven dependency, declare two new `@Bean` methods in `RabbitConfig.java` (`TracingMessagePostProcessor` + an explicit `RabbitTemplate` that registers it via `setBeforePublishPostProcessors`), and DELETE Phase 2's inline PRODUCER span body from `OrderPublisher.publish(...)` along with the `Tracer` constructor parameter.

This plan delivers PROP-01 (the producer-side inject is now actually wired into the publish path) and the producer-side half of PROP-04 (the `@Bean` wiring is explicit per D-01 — attendees see ONE `@Bean` declaration per service, but ONE class file in `otel-bootstrap`). The DELETION half of the load-bearing `step-02-traces..step-03-context-propagation` git diff lands here (~33 deleted lines from `OrderPublisher.java`).

Purpose: PROP-01 (writer side wired into the publish path), PROP-04 (per-service explicit `@Bean` wiring of the shared module), and the producer-side contribution to the smallest-readable-diff property (ROADMAP SC #5).

Output: 1 modified `pom.xml` (4 added lines), 1 modified `RabbitConfig.java` (~30 added lines for two new `@Bean` methods + JavaDoc + imports), 1 modified `OrderPublisher.java` (~33 deleted lines + 8 deleted imports + 1 changed constructor signature + updated class-level JavaDoc).
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
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-producer-instrumentation-PLAN.md
@.planning/phases/02-manual-sdk-bootstrap-first-traces/02-CONTEXT.md
@CLAUDE.md
@producer-service/pom.xml
@producer-service/src/main/java/com/example/producer/config/RabbitConfig.java
@producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
@producer-service/src/main/java/com/example/producer/domain/OrderService.java

<interfaces>
<!-- The producer-side contracts the executor needs. -->

From `otel-bootstrap` (Plan 03-01 just produced this):
```java
package com.example.otel.amqp;
public class TracingMessagePostProcessor implements org.springframework.amqp.core.MessagePostProcessor {
    public TracingMessagePostProcessor(io.opentelemetry.api.OpenTelemetry openTelemetry,
                                       io.opentelemetry.api.trace.Tracer tracer);
    @Override public Message postProcessMessage(Message m, Correlation c, String exchange, String routingKey);
    @Override public Message postProcessMessage(Message m);
}
```

Already wired in producer-service/.../config/OtelSdkConfiguration.java (Phase 2):
```java
@Bean(destroyMethod = "close")
OpenTelemetrySdk openTelemetrySdk() { /* ... */ }   // exposed as OpenTelemetry interface to consumers

@Bean
Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("com.example.producer");
}
```

Existing producer-service/.../config/RabbitConfig.java (Phase 2 — 38 lines):
```java
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "orders";
    public static final String QUEUE = "orders.created";
    public static final String ROUTING_KEY = "order.created";

    @Bean DirectExchange ordersExchange();
    @Bean Queue ordersCreatedQueue();
    @Bean Binding ordersBinding(Queue q, DirectExchange ex);
    @Bean MessageConverter jsonMessageConverter();
}
```

Existing OrderPublisher.java (Phase 2 — 85 lines):
```java
@Component
public class OrderPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Tracer tracer;                                  // <-- DELETE in T3
    public OrderPublisher(RabbitTemplate rabbitTemplate, Tracer tracer);  // <-- (Tracer) DELETE in T3
    public void publish(String orderId, Map<String, Object> payload) {
        // ~44 lines of inline PRODUCER span: spanBuilder + 4 attrs + try/Scope/catch/finally
        // ALL DELETED in T3 — replaced by 3-line thin body
    }
}
```

Spring Boot auto-config behavior (RESEARCH.md "RabbitAutoConfiguration Backoff"):
- `RabbitAutoConfiguration.@Bean RabbitTemplate` is annotated with `@ConditionalOnMissingBean(RabbitOperations.class)`.
- `RabbitTemplate implements RabbitOperations`, so a user-defined `@Bean RabbitTemplate` causes the auto-bean to back off.
- No name match needed — type match is sufficient.
</interfaces>
</context>

<tasks>

<task id="03-02-T1" type="auto">
  <name>Task 1: Add com.example:otel-bootstrap dependency to producer-service/pom.xml</name>
  <files>producer-service/pom.xml</files>
  <read_first>
    - producer-service/pom.xml (current state — 117 lines, 8 dependencies starting at line 36; per RESEARCH.md "Existing Code Confirmation" line 840, currently has NO dependency on otel-bootstrap)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 600-629 — exact transformation: add ONE dep at top of <dependencies> block; coordinate, version, scope guidance)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (line 119-120 — "Both producer-service/pom.xml and consumer-service/pom.xml must add a <dependency> block on com.example:otel-bootstrap:${project.version}")
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (line 209 — "otel-bootstrap becomes a real Maven dependency of producer-service and consumer-service")
    - otel-bootstrap/pom.xml (just modified by Plan 03-01 — confirms artifactId is "otel-bootstrap" and groupId inherits "com.example" from parent)
  </read_first>
  <action>
    Open `producer-service/pom.xml`. Find the `<dependencies>` block (starts at line 20).

    INSERT a new `<dependency>` block at the TOP of `<dependencies>` (BEFORE the existing `spring-boot-starter-web` dep at line 36). This places the intra-reactor dep visually distinct from external deps — matches the convention 03-PATTERNS.md describes at line 615.

    Add this exact block (with one blank line after for visual separation):

    ```xml
        <!--
          Phase 3: shared otel-bootstrap module. Provides the AMQP context
          propagation pair — TracingMessagePostProcessor (PRODUCER inject)
          and TracingMessageListenerAdvice (CONSUMER extract). Producer uses
          only the post-processor (registered on RabbitTemplate via
          setBeforePublishPostProcessors in RabbitConfig). The version
          ${project.version} resolves to 0.1.0-SNAPSHOT via parent POM
          inheritance (multi-module reactor convention).
        -->
        <dependency>
          <groupId>com.example</groupId>
          <artifactId>otel-bootstrap</artifactId>
          <version>${project.version}</version>
        </dependency>

    ```

    (insert it BETWEEN the `<dependencies>` opening tag and the existing first dep — `spring-boot-starter-web`).

    DO NOT modify any existing dependency. DO NOT change the order of existing deps. DO NOT change `<scope>` on any existing dep.

    Verify with `mvn -pl producer-service dependency:tree -Dincludes=com.example:otel-bootstrap`. Expect to see `com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT:compile` in the output. Then confirm the reactor builds: `mvn -pl otel-bootstrap,producer-service -am compile` exits 0.
  </action>
  <acceptance_criteria>
    - otel-bootstrap dep present: `grep -A2 '<artifactId>otel-bootstrap</artifactId>' producer-service/pom.xml | grep -q '\${project.version}'` exits 0
    - Dep groupId is com.example: `grep -B1 '<artifactId>otel-bootstrap</artifactId>' producer-service/pom.xml | grep -q '<groupId>com.example</groupId>'` exits 0
    - Dep version uses ${project.version}: `grep -A2 '<artifactId>otel-bootstrap</artifactId>' producer-service/pom.xml | grep -q '<version>\${project.version}</version>'` exits 0
    - Dep is BEFORE spring-boot-starter-web (visual distinction): `awk '/<artifactId>otel-bootstrap</{ob=NR} /<artifactId>spring-boot-starter-web</{sw=NR} END{exit (ob<sw)?0:1}' producer-service/pom.xml` exits 0
    - No <scope> tag (defaults to compile): `awk '/<artifactId>otel-bootstrap</,/<\/dependency>/' producer-service/pom.xml | grep -c '<scope>'` returns 0
    - Existing 8 deps still present (regression check): `for d in spring-boot-starter-web spring-boot-starter-amqp spring-boot-starter-actuator opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp opentelemetry-semconv opentelemetry-semconv-incubating; do grep -q "<artifactId>$d</artifactId>" producer-service/pom.xml || exit 1; done` exits 0
    - Maven reactor compiles: `mvn -q -pl otel-bootstrap,producer-service -am compile` exits 0
    - Dependency tree shows otel-bootstrap: `mvn -pl producer-service dependency:tree -Dincludes=com.example:otel-bootstrap 2>&amp;1 | grep -q 'com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT'` exits 0
  </acceptance_criteria>
  <verify>
    <automated>grep -q '<artifactId>otel-bootstrap</artifactId>' producer-service/pom.xml &amp;&amp; grep -A2 '<artifactId>otel-bootstrap</artifactId>' producer-service/pom.xml | grep -q '\${project.version}' &amp;&amp; mvn -q -pl otel-bootstrap,producer-service -am compile</automated>
  </verify>
  <done>producer-service/pom.xml gains one <dependency> on com.example:otel-bootstrap:${project.version} placed BEFORE spring-boot-starter-web; default compile scope; existing 8 deps unchanged; mvn compile passes for the producer + otel-bootstrap reactor pair.</done>
</task>

<task id="03-02-T2" type="auto">
  <name>Task 2: Add @Bean TracingMessagePostProcessor + explicit @Bean RabbitTemplate to producer's RabbitConfig.java (D-05)</name>
  <files>producer-service/src/main/java/com/example/producer/config/RabbitConfig.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (current state — 38 lines, 4 @Bean methods, 3 public-static-final constants per RESEARCH.md "Existing Code Confirmation" lines 824-826)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 599-668 — exact code shape for the new beans; lines 488-496 — RabbitAutoConfiguration backoff confirmed)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 366-419 — RabbitConfig pattern + 6-item transformation notes; lines 401-413 — explicit RabbitTemplate body)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 38-46 — D-01 pure-Java + per-service @Bean wiring; lines 60-69 — D-05 post-processor owns PRODUCER span)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java (just produced by Plan 03-01 — confirms constructor signature is (OpenTelemetry, Tracer))
  </read_first>
  <action>
    Open `producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` (currently 38 lines).

    KEEP unchanged:
    - The package declaration, the 3 constants (`EXCHANGE`, `QUEUE`, `ROUTING_KEY`), and the 4 existing `@Bean` methods (`ordersExchange`, `ordersCreatedQueue`, `ordersBinding`, `jsonMessageConverter`).

    ADD imports (at the top of the file, after existing imports). Required new imports:
    ```java
    import com.example.otel.amqp.TracingMessagePostProcessor;
    import io.opentelemetry.api.OpenTelemetry;
    import io.opentelemetry.api.trace.Tracer;
    import org.springframework.amqp.rabbit.connection.ConnectionFactory;
    import org.springframework.amqp.rabbit.core.RabbitTemplate;
    ```

    ADD two new `@Bean` methods at the END of the class body (before the closing `}` brace), AFTER the existing `jsonMessageConverter()` bean. Use the EXACT shape below — match the existing file's formatting (4-space indentation, JavaDoc above each bean):

    ```java

        // ---- Phase 3 NEW: AMQP context propagation wiring ----

        /**
         * Phase 3: registers the producer side of W3C AMQP context
         * propagation. {@link TracingMessagePostProcessor} owns the entire
         * PRODUCER span lifecycle (CONTEXT.md D-05) — Phase 2's inline
         * PRODUCER span in {@code OrderPublisher.publish(...)} is deleted and
         * replaced by this hook on the {@link RabbitTemplate} below.
         *
         * <p>Pure constructor wrapper per D-01: the propagation classes carry
         * NO Spring annotations themselves; the wiring is explicit here.
         */
        @Bean
        TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry openTelemetry,
                                                                 Tracer tracer) {
            return new TracingMessagePostProcessor(openTelemetry, tracer);
        }

        /**
         * Phase 3: explicit {@link RabbitTemplate} bean overriding Spring
         * Boot's auto-created template. Required so we can register
         * {@link #tracingMessagePostProcessor} on
         * {@link RabbitTemplate#setBeforePublishPostProcessors} — the hook
         * that fires AFTER {@link Jackson2JsonMessageConverter} has produced
         * the message body but BEFORE the AMQP wire write, which is exactly
         * where W3C trace headers are injected (PITFALLS.md #12: never
         * subclass RabbitTemplate; never replace the converter).
         *
         * <p>Spring Boot's {@code RabbitAutoConfiguration} backs off because
         * {@code @ConditionalOnMissingBean(RabbitOperations.class)} matches
         * — {@link RabbitTemplate} implements {@code RabbitOperations}.
         *
         * <p>Bean order: {@link #jsonMessageConverter()} must be set BEFORE
         * the post-processor registers, otherwise the byte body Jackson
         * produces wouldn't carry the JSON content_type header that the
         * consumer's {@code Jackson2JsonMessageConverter} needs to
         * deserialise.
         */
        @Bean
        RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                       MessageConverter messageConverter,
                                       TracingMessagePostProcessor tracingMpp) {
            RabbitTemplate template = new RabbitTemplate(connectionFactory);
            template.setMessageConverter(messageConverter);
            template.setBeforePublishPostProcessors(tracingMpp);
            return template;
        }
    ```

    Verify the final state:
    - File now has 6 `@Bean` methods (4 existing + 2 new).
    - The 3 `public static final` constants are unchanged.
    - The package declaration is unchanged.
    - The class is still annotated `@Configuration` (only).
    - `mvn -pl producer-service compile` exits 0.

    DO NOT modify the existing 4 beans. DO NOT change the constants. DO NOT add `@AutoConfiguration` or any conditional annotation.
  </action>
  <acceptance_criteria>
    - File compiles: `mvn -q -pl producer-service compile` exits 0
    - @Bean TracingMessagePostProcessor present: `grep -q '@Bean' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java &amp;&amp; grep -q 'TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0
    - @Bean RabbitTemplate present: `grep -q 'RabbitTemplate rabbitTemplate(ConnectionFactory' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0
    - setBeforePublishPostProcessors called: `grep -q 'template.setBeforePublishPostProcessors(tracingMpp)' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0
    - setMessageConverter called: `grep -q 'template.setMessageConverter(messageConverter)' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0
    - 5 expected new imports added: `for i in 'com.example.otel.amqp.TracingMessagePostProcessor' 'io.opentelemetry.api.OpenTelemetry' 'io.opentelemetry.api.trace.Tracer' 'org.springframework.amqp.rabbit.connection.ConnectionFactory' 'org.springframework.amqp.rabbit.core.RabbitTemplate'; do grep -q "^import $i;" producer-service/src/main/java/com/example/producer/config/RabbitConfig.java || exit 1; done` exits 0
    - Existing 4 beans preserved (regression check): `for b in 'DirectExchange ordersExchange' 'Queue ordersCreatedQueue' 'Binding ordersBinding' 'MessageConverter jsonMessageConverter'; do grep -q "$b" producer-service/src/main/java/com/example/producer/config/RabbitConfig.java || exit 1; done` exits 0
    - Constants preserved: `for c in 'EXCHANGE = "orders"' 'QUEUE = "orders.created"' 'ROUTING_KEY = "order.created"'; do grep -qF "$c" producer-service/src/main/java/com/example/producer/config/RabbitConfig.java || exit 1; done` exits 0
    - Total @Bean count is 6: `grep -c '^    @Bean$' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 6
    - JavaDoc references D-05: `grep -q 'D-05' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0
    - No @AutoConfiguration / no @Conditional* annotations: `grep -cE '@(AutoConfiguration|ConditionalOn)' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl producer-service compile &amp;&amp; grep -q 'TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java &amp;&amp; grep -q 'template.setBeforePublishPostProcessors(tracingMpp)' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java &amp;&amp; [ "$(grep -c '^    @Bean$' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java)" = "6" ]</automated>
  </verify>
  <done>RabbitConfig.java gains 2 new @Bean methods (TracingMessagePostProcessor + explicit RabbitTemplate) and 5 new imports; existing 4 beans + 3 constants preserved unchanged; setBeforePublishPostProcessors wired; producer compiles cleanly.</done>
</task>

<task id="03-02-T3" type="auto">
  <name>Task 3: DELETE Phase 2 inline PRODUCER span body from OrderPublisher.publish; remove Tracer constructor param + field; clean imports (D-05)</name>
  <files>producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (current state — 85 lines; the inline PRODUCER span body lives at lines 39-83 inside the publish() method)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 794-806 — exact deletion target with line numbers; final post-Phase-3 method shape)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 235-296 — OrderPublisher transformation: 6-item transformation notes including which imports to delete)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 60-68 — D-05: thin convertAndSend body; Tracer ctor param removed)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-producer-instrumentation-PLAN.md (the plan that originally CREATED the inline PRODUCER span — confirms what's being deleted)
  </read_first>
  <action>
    Open `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` (currently 85 lines).

    REPLACE THE ENTIRE FILE with the EXACT content below. This is a small enough file that a full rewrite is cleaner than 12 separate edits. The result is a thin pass-through `publish(...)` per D-05.

    ```java
    package com.example.producer.messaging;

    import java.util.HashMap;
    import java.util.Map;

    import org.springframework.amqp.rabbit.core.RabbitTemplate;
    import org.springframework.stereotype.Component;

    import com.example.producer.config.RabbitConfig;

    /**
     * Publishes OrderCreated to the {@code orders} direct exchange via Spring
     * AMQP {@link RabbitTemplate#convertAndSend}.
     *
     * <p>Phase 3 made this method a thin 3-line pass-through. The PRODUCER
     * span and W3C {@code traceparent} header injection are owned by
     * {@code com.example.otel.amqp.TracingMessagePostProcessor}, which is
     * registered on the {@link RabbitTemplate} bean via
     * {@code setBeforePublishPostProcessors(...)} — see
     * {@link com.example.producer.config.RabbitConfig#rabbitTemplate}.
     *
     * <p>Why moved (CONTEXT.md D-05): Phase 2's inline PRODUCER span body
     * here was structurally clean but conflated two concerns — span lifecycle
     * AND the publish call. Phase 3 separates them: the post-processor
     * handles the span + header inject (the cross-service propagation lesson
     * the workshop is built around); this file handles the business call
     * (build the message and convertAndSend). The smallest possible
     * step-02-traces → step-03-context-propagation git diff (ROADMAP SC #5)
     * deletes ~33 lines from this file.
     */
    @Component
    public class OrderPublisher {
        private final RabbitTemplate rabbitTemplate;

        public OrderPublisher(RabbitTemplate rabbitTemplate) {
            this.rabbitTemplate = rabbitTemplate;
        }

        public void publish(String orderId, Map<String, Object> payload) {
            Map<String, Object> message = new HashMap<>(payload);
            message.put("orderId", orderId);
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        }
    }
    ```

    Then verify with `mvn -pl producer-service compile`. Expect exit 0.

    Verify the deletion was clean — file should be ~30 lines (down from 85):
    ```
    wc -l producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
    ```
    Expect: ~30-40 lines (the JavaDoc is intentionally generous; raw class body is 13 lines).

    Verify NO remaining OTel imports / references:
    ```
    grep -c 'io.opentelemetry\|MessagingIncubatingAttributes\|MessagingSystemIncubatingValues\|MessagingOperationTypeIncubatingValues\|Span\|Tracer\|StatusCode\|Scope\b' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
    # Expect 0
    ```

    Verify the constructor is now single-arg:
    ```
    grep -c 'public OrderPublisher(RabbitTemplate rabbitTemplate)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
    # Expect 1
    grep -c 'Tracer tracer' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
    # Expect 0 (Tracer param + field deleted)
    ```

    Spring auto-wires the single-arg constructor without `@Autowired` (Spring 4.3+ behavior). The `@Component` annotation stays, ensuring `OrderService.place(...)` can constructor-inject this bean unchanged.
  </action>
  <acceptance_criteria>
    - File compiles: `mvn -q -pl producer-service compile` exits 0
    - File line count between 25 and 50 (down from 85): `LINES=$(wc -l < producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java); python3 -c "l=$LINES; assert 25<=l<=50, l; print(f'OK: {l} lines')"` exits 0
    - Constructor is single-arg (Tracer param removed): `grep -q 'public OrderPublisher(RabbitTemplate rabbitTemplate)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - Constructor does NOT take Tracer: `grep -c 'Tracer tracer' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - publish() body calls convertAndSend with RabbitConfig.EXCHANGE: `grep -q 'rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - Builds the message map: `grep -q 'new HashMap<>(payload)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; grep -q 'message.put("orderId", orderId)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - NO OTel imports: `grep -cE 'import io\.opentelemetry|MessagingIncubatingAttributes|MessagingSystemIncubatingValues|MessagingOperationTypeIncubatingValues' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - NO Span / Tracer / StatusCode / Scope references in body: `grep -cE '\bSpan\b|\bTracer\b|\bStatusCode\b|\bScope\b' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - NO inline span builder calls: `grep -cE 'spanBuilder|setSpanKind|recordException|setStatus|makeCurrent|startSpan' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - @Component annotation preserved: `grep -q '@Component' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - JavaDoc references D-05 + ROADMAP SC #5: `grep -q 'D-05' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; grep -q 'SC #5' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - Imports list is minimal — only HashMap, Map, RabbitTemplate, Component, RabbitConfig: `grep -c '^import ' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 5
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl producer-service compile &amp;&amp; grep -q 'rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message)' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; ! grep -qE 'import io\.opentelemetry|MessagingIncubatingAttributes|spanBuilder' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; [ "$(grep -c 'Tracer tracer' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java)" = "0" ]</automated>
  </verify>
  <done>OrderPublisher.java is the thin pass-through shape: ~30 lines, single-arg constructor (no Tracer), no OTel imports, no inline span scaffolding, just builds message + convertAndSend. @Component annotation preserved. JavaDoc explains the move per D-05. Producer compiles cleanly.</done>
</task>

<task id="03-02-T4" type="auto">
  <name>Task 4: Producer-side compile + smoke verification — full producer build, RabbitTemplate bean wiring resolves, no Spring DI errors</name>
  <files>(none — verification only)</files>
  <read_first>
    - producer-service/pom.xml (just modified by T1)
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (just modified by T2)
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (just modified by T3)
    - .planning/phases/02-manual-sdk-bootstrap-first-traces/02-04-SUMMARY.md (Phase 2 producer smoke baseline)
  </read_first>
  <action>
    Run a clean build on the producer-service module + its upstream (otel-bootstrap) and confirm Spring's bean wiring resolves correctly. NO live broker / runtime tests in this plan — that's the human-verify gate in Plan 03-05.

    Commands to run, in order:

    ```sh
    # 1. Clean compile of the producer + its upstream module
    mvn -pl otel-bootstrap,producer-service -am clean compile

    # 2. Run producer-service unit tests (currently zero domain tests, but the test classpath should resolve)
    mvn -pl producer-service test

    # 3. Quick Spring context-load smoke — package the JAR so we exercise spring-boot-maven-plugin's repackage
    mvn -pl producer-service -am package -DskipTests

    # 4. Verify the dependency resolution shows otel-bootstrap once
    mvn -pl producer-service dependency:tree -Dincludes=com.example:otel-bootstrap | grep -c 'com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT'
    # Expect: 1
    ```

    Optional Spring context-load test (only if a test exists that bootstraps Spring context):
    The producer-service has `@SpringBootTest`-style test stubs from Phase 1 (`ProducerApplicationTests` if it exists). If that test exists, running it will bootstrap the application context including the new RabbitConfig — any Spring DI failure (e.g., a missing `OpenTelemetry` or `Tracer` bean reference, a circular dep, a duplicate `RabbitTemplate` bean) will surface here at test time without needing a live broker.

    Check for the test:
    ```sh
    find producer-service/src/test -name 'ProducerApplicationTests.java' 2>/dev/null
    ```
    If found, the `mvn -pl producer-service test` command in step 2 already exercised it. If the test fails with a bean-wiring error, this is a CRITICAL signal — debug before proceeding to plan 03-03 / 03-04. Common bean-wiring failure modes:
    - "No qualifying bean of type RabbitOperations" → producer's RabbitTemplate bean isn't being created (check `@Bean` annotation present + return type + constructor params resolvable).
    - "Could not autowire field: TracingMessagePostProcessor" in OrderPublisher → impossible (we deleted Tracer; constructor only takes RabbitTemplate now).
    - "BeanDefinitionConflictException" → unlikely; the auto-config back-off relies on `@ConditionalOnMissingBean(RabbitOperations.class)` which is checked at context init.

    NOTE: in Phase 2 the producer test was a `ProducerApplicationTests.contextLoads()` test that started without RabbitMQ available (Spring AMQP doesn't fail context-load if the connection isn't established; it lazily connects). So this `mvn test` should pass even with no RabbitMQ container running. Phase 2 verified this empirically.

    The goal of this task is to catch Spring wiring regressions BEFORE the human-verify gate in 03-05. If `mvn test` reports a failure related to `OrderPublisher` or `RabbitTemplate`, the wiring in T2 is wrong and must be debugged here.
  </action>
  <acceptance_criteria>
    - Clean compile passes: `mvn -q -pl otel-bootstrap,producer-service -am clean compile` exits 0
    - Producer unit tests pass (or are absent and skipped silently): `mvn -q -pl producer-service test` exits 0
    - Producer JAR repackages: `mvn -q -pl producer-service -am package -DskipTests` exits 0; `test -f producer-service/target/producer-service-0.1.0-SNAPSHOT.jar` exits 0
    - otel-bootstrap appears exactly ONCE in producer's dependency tree: `[ "$(mvn -pl producer-service dependency:tree -Dincludes=com.example:otel-bootstrap 2>&amp;1 | grep -c 'com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT')" = "1" ]` exits 0
    - mise verify:bom still passes (Phase 1 + Phase 2 invariant): `mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'` exits 0
    - No producer source files outside scope changed: `git status --porcelain producer-service/ | grep -v '^??' | grep -v 'pom.xml\|RabbitConfig.java\|OrderPublisher.java' | head -1 | wc -l` returns 0 (only the 3 expected files modified)
  </acceptance_criteria>
  <verify>
    <automated>mvn -q -pl otel-bootstrap,producer-service -am clean compile &amp;&amp; mvn -q -pl producer-service test &amp;&amp; mvn -q -pl producer-service -am package -DskipTests &amp;&amp; test -f producer-service/target/producer-service-0.1.0-SNAPSHOT.jar &amp;&amp; mise run verify:bom 2>&amp;1 | tail -5 | grep -qE 'Phase 2 baseline confirmed|one version per OpenTelemetry artifact'</automated>
  </verify>
  <done>Clean compile + unit test + package all pass for the producer module; otel-bootstrap appears exactly once in dependency tree; mise verify:bom invariant preserved; only the 3 expected files (pom.xml + RabbitConfig.java + OrderPublisher.java) show as modified.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 03-02 — producer wiring)

| Boundary | Description |
|----------|-------------|
| Producer JVM → AMQP wire | `setBeforePublishPostProcessors` runs in-JVM before any wire write; no remote trust crossed inside this plan |
| Spring auto-config → user-defined beans | RabbitAutoConfiguration must back off cleanly; bean uniqueness is the gate |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-3-02-01 | Tampering | A future plan reorders the RabbitTemplate bean definition such that `setBeforePublishPostProcessors` is called BEFORE `setMessageConverter` — the message body is then produced AFTER the post-processor runs (against the design) | mitigate | The bean method body is explicit: `template.setMessageConverter(...)` THEN `template.setBeforePublishPostProcessors(...)`. JavaDoc on the bean explains the order requirement (PITFALLS.md #12 — never disturb the converter ordering). The grep gate in T2 acceptance asserts both lines present. |
| T-3-02-02 | DoS | An infinite-loop / leaked Span on the inject path under load | accept | TracingMessagePostProcessor (built in plan 03-01) uses try/finally — span.end() always runs. No catch on the producer side because W3C inject + String setter is infallible; broker-level errors propagate up to OrderService.place's INTERNAL span (Phase 2 D-03). |
| T-3-02-03 | Information Disclosure | The constructor injection log line might leak the bean's identity at debug log level | accept | Spring's debug-level bean creation logs are workshop-internal; no sensitive data; same posture as Phase 2. |

**No CRITICAL/HIGH security blockers.**
</threat_model>

<verification>
- producer-service/pom.xml has new `<dependency>com.example:otel-bootstrap:${project.version}</dependency>` placed before spring-boot-starter-web.
- producer-service/.../config/RabbitConfig.java has 6 `@Bean` methods (4 existing + 2 new); the new RabbitTemplate calls `setBeforePublishPostProcessors(tracingMpp)` AFTER `setMessageConverter`.
- producer-service/.../messaging/OrderPublisher.java is ~30 lines: thin `publish(...)` body (3 lines), single-arg constructor, no OTel imports, no Tracer field.
- `mvn -pl otel-bootstrap,producer-service -am clean compile` exits 0.
- `mvn -pl producer-service test` exits 0 (Phase 1's `ProducerApplicationTests.contextLoads()` confirms Spring context bootstraps with the new wiring).
- `mvn dependency:tree` shows `com.example:otel-bootstrap:jar:0.1.0-SNAPSHOT` exactly once on the producer.
- `mise run verify:bom` exits 0 — Phase 2 BOM invariant preserved.
- File modification list (git status) shows exactly: 1 modified pom.xml + 1 modified RabbitConfig.java + 1 modified OrderPublisher.java.
</verification>

<success_criteria>
- PROP-01 (writer side wired): `TracingMessagePostProcessor` is registered on the producer's `RabbitTemplate.setBeforePublishPostProcessors(...)` — every `convertAndSend(...)` invocation triggers the W3C inject. End-to-end runtime verification deferred to plan 03-05's human-verify gate (RabbitMQ Mgmt UI shows `traceparent` header).
- PROP-04 (per-service explicit `@Bean` wiring): producer's `RabbitConfig.java` has the explicit `@Bean TracingMessagePostProcessor` declaration — readers see ONE class file in `otel-bootstrap` mapped to ONE `@Bean` declaration here (parallel-symmetric to consumer's plan 03-03).
- D-05 honored: Phase 2's inline PRODUCER span is DELETED; the post-processor owns the entire span lifecycle; OrderPublisher.publish is a thin pass-through.
- Smallest-readable-diff property (ROADMAP SC #5): producer-side contribution is +6 added lines (RabbitConfig.java new beans) - 33 deleted lines (OrderPublisher.java span body) = net -27 lines on the producer side.
- Phase 2 functionality intact: producer module compiles, tests pass, BOM invariant holds.
</success_criteria>

<output>
After completion, create `.planning/phases/03-amqp-context-propagation/03-02-SUMMARY.md` documenting:
- The 3 modified files (paste their final paths)
- The line count delta on OrderPublisher.java (was 85 → now ~30; -55 lines including JavaDoc reduction; -33 lines on the publish() body alone)
- The dependency tree confirmation: paste `mvn -pl producer-service dependency:tree -Dincludes=com.example:otel-bootstrap | head -10`
- The new RabbitConfig.java @Bean count (4 → 6)
- mise verify:bom result
- Wave 2 hand-off: producer-side wiring is complete; consumer-side wiring (plan 03-03) and APP-04 failure path (plan 03-04) can proceed in parallel; the human-verify smoke gate (plan 03-05) is deferred until consumer wiring lands
- Note any auto-config quirks observed (e.g., did Spring log a "RabbitTemplate auto-config backed off" message? — useful for the workshop README)
</output>