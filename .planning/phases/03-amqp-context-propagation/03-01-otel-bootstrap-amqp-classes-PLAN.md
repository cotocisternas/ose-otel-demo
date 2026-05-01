---
id: 03-01-otel-bootstrap-amqp-classes
phase: 03-amqp-context-propagation
plan: 01
type: execute
wave: 1
depends_on: []
requirements: [PROP-01, PROP-02, PROP-04]
files_modified:
  - otel-bootstrap/pom.xml
  - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java
  - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java
  - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java
  - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java
  - otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java
autonomous: true
objective: "Populate the empty otel-bootstrap module with the four AMQP propagation classes (TracingMessagePostProcessor, TracingMessageListenerAdvice, MessagePropertiesSetter, MessagePropertiesGetter) plus a setter↔getter round-trip unit test, and add the three new dependencies (otel-api compile, spring-rabbit + spring-aop provided)."
must_haves:
  truths:
    - "otel-bootstrap module compiles cleanly under mvn -pl otel-bootstrap compile (per D-01: pure Java classes, no Spring annotations)"
    - "TracingMessagePostProcessor implements org.springframework.amqp.core.MessagePostProcessor and overrides BOTH the 4-arg postProcessMessage(Message, Correlation, String exchange, String routingKey) AND the 1-arg postProcessMessage(Message) methods (per D-02 + RESEARCH FLAG #2)"
    - "TracingMessageListenerAdvice implements org.aopalliance.intercept.MethodInterceptor with invoke(MethodInvocation) reading inv.getArguments()[1] guarded by 'instanceof Message' (per D-02 + RESEARCH FLAG #3 + Pitfall #6)"
    - "MessagePropertiesSetter implements TextMapSetter<MessageProperties> and writes a String value via carrier.setHeader(key, value) — never byte[] (per D-02 + PITFALLS.md #2)"
    - "MessagePropertiesGetter implements TextMapGetter<MessageProperties> and defensively normalizes header values via raw.toString() (per D-02 + PITFALLS.md #2)"
    - "All four propagation classes carry zero Spring annotations on the class itself (per D-01: Spring wiring lives only in each service's RabbitConfig — read once, applied symmetrically)"
    - "TracingMessagePostProcessor and TracingMessageListenerAdvice constructors take (OpenTelemetry openTelemetry, Tracer tracer) per D-03 — propagator is read via openTelemetry.getPropagators().getTextMapPropagator() (per D-04, NOT a freshly-constructed W3CTraceContextPropagator.getInstance())"
    - "PRODUCER span shape uses semconv-correct destination naming (D-07): span name = exchange + ' publish'; messaging.destination.name = exchange parameter; messaging.rabbitmq.destination.routing_key = routingKey parameter; messaging.system = rabbitmq; messaging.operation.type = SEND"
    - "CONSUMER span shape mirrors the PRODUCER side (D-10): span name = exchange + ' process'; .setParent(extracted); SpanKind.CONSUMER; same four messaging.* attributes (system, destination.name = exchange via getReceivedExchange(), operation.type = PROCESS, rabbitmq.destination.routing_key via getReceivedRoutingKey())"
    - "TracingMessageListenerAdvice catches Throwable (per D-10 + RESEARCH §Pattern 2) — recordException + setStatus(ERROR) + rethrow"
    - "TracingMessagePostProcessor uses try/finally only (no catch) per Claude's Discretion in CONTEXT.md (W3C inject + String setter is infallible; broker errors propagate up to OrderService.place's INTERNAL span)"
    - "PRODUCER span lifetime is inject-only (per D-06): span ends INSIDE postProcessMessage, BEFORE RabbitTemplate.send writes to wire"
    - "Setter↔Getter round-trip unit test exists at otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java and proves the byte[]-vs-String discipline (PITFALLS.md #2)"
    - "otel-bootstrap/pom.xml has 3 new BOM-managed dependencies: opentelemetry-api (compile), spring-rabbit (provided), spring-aop (provided) — no <version> tags (per Stack table)"
  artifacts:
    - path: "otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java"
      provides: "Producer-side W3C trace context inject + PRODUCER span lifecycle (owns the entire PRODUCER span per D-05; replaces Phase 2's inline PRODUCER span body)"
      contains: "implements MessagePostProcessor"
    - path: "otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java"
      provides: "Consumer-side W3C trace context extract + CONSUMER span lifecycle (owns the entire CONSUMER span; replaces Phase 2's inline CONSUMER span body); the .setParent(extracted) line is what joins the producer and consumer traces (ROADMAP SC #1)"
      contains: "implements MethodInterceptor"
    - path: "otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java"
      provides: "TextMapSetter<MessageProperties>: writes String header values (PITFALLS.md #2)"
      contains: "implements TextMapSetter"
    - path: "otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java"
      provides: "TextMapGetter<MessageProperties>: defensively normalizes header values via .toString() (PITFALLS.md #2)"
      contains: "implements TextMapGetter"
    - path: "otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java"
      provides: "Pure unit test (no Spring, no Testcontainers) — proves setter writes String + getter reads String round-trip (PITFALLS.md #2 regression net)"
      contains: "@Test"
    - path: "otel-bootstrap/pom.xml"
      provides: "3 BOM-managed deps: opentelemetry-api (compile), spring-rabbit (provided), spring-aop (provided)"
      contains: "<artifactId>opentelemetry-api</artifactId>"
  key_links:
    - from: "TracingMessagePostProcessor.postProcessMessage(4-arg)"
      to: "MessagePropertiesSetter (singleton SETTER field)"
      via: "propagator.inject(Context.current(), props, SETTER) call"
      pattern: "propagator\\.inject"
    - from: "TracingMessageListenerAdvice.invoke"
      to: "MessagePropertiesGetter (singleton GETTER field)"
      via: "propagator.extract(Context.current(), props, GETTER) call followed by .setParent(extracted)"
      pattern: "propagator\\.extract.*\\n.*setParent"
    - from: "Both propagation classes"
      to: "openTelemetry.getPropagators().getTextMapPropagator()"
      via: "D-04 reuse of Phase 2's already-wired composite propagator (W3CTraceContext + W3CBaggage)"
      pattern: "getPropagators\\(\\)\\.getTextMapPropagator"
---

<objective>
Populate the empty `otel-bootstrap` Maven module (currently just a `package-info.java` placeholder) with the FOUR pure-Java AMQP propagation classes that own both sides of W3C trace-context propagation across the RabbitMQ boundary, plus a pure-unit setter↔getter round-trip test that proves the load-bearing String-not-byte[] discipline (PITFALLS.md #2). Add the three new BOM-managed dependencies (`opentelemetry-api` compile, `spring-rabbit` provided, `spring-aop` provided) to `otel-bootstrap/pom.xml`.

This plan delivers PROP-01 (writer side: `MessagePropertiesSetter` + `TracingMessagePostProcessor`), PROP-02 (reader side: `MessagePropertiesGetter` + `TracingMessageListenerAdvice`), and the structural half of PROP-04 (the four files in the SHARED module — the per-service `@Bean` wiring lands in 03-02 and 03-03). It is the WAVE-1 producer of the shared library that 03-02 (producer wiring) and 03-03 (consumer wiring) both consume; without it Wave 2 has nothing to wire.

Purpose: PROP-01 + PROP-02 (the pair), PROP-04 (shared-module structure), and the SHAPE (D-07 + D-10 semconv-correct destination naming) that the Phase 2 → Phase 3 git diff makes visible.

Output: 4 new Java source files under `otel-bootstrap/src/main/java/com/example/otel/amqp/` + 1 new test file under `otel-bootstrap/src/test/java/com/example/otel/amqp/` + 3 new dependency lines in `otel-bootstrap/pom.xml`. Net: +5 files, ~250 added Java lines (the otel-bootstrap module body), ~25 added pom.xml lines.
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
@CLAUDE.md
@otel-bootstrap/pom.xml
@otel-bootstrap/src/main/java/com/example/otel/package-info.java
@producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
@consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
@producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java

<interfaces>
<!-- Key types and contracts the executor needs. Extracted from RESEARCH.md + CONTEXT.md + direct file reads. -->
<!-- Executor should use these directly — no codebase exploration needed. -->

From `io.opentelemetry:opentelemetry-api:1.61.0` (BOM-managed):
```java
package io.opentelemetry.api;
public interface OpenTelemetry {
    ContextPropagators getPropagators();
    Tracer getTracer(String instrumentationScopeName);
    // ...
}

package io.opentelemetry.api.trace;
public interface Tracer {
    SpanBuilder spanBuilder(String spanName);
}
public interface SpanBuilder {
    SpanBuilder setSpanKind(SpanKind spanKind);
    SpanBuilder setParent(Context context);
    SpanBuilder setAttribute(AttributeKey<T> key, T value);
    SpanBuilder setAttribute(String key, String value);
    Span startSpan();
}
public interface Span {
    Scope makeCurrent();
    void recordException(Throwable t);
    Span setStatus(StatusCode statusCode);
    void end();
}
public enum SpanKind { INTERNAL, SERVER, CLIENT, PRODUCER, CONSUMER }
public enum StatusCode { UNSET, OK, ERROR }

package io.opentelemetry.context;
public interface Context { static Context current(); static Context root(); }
public interface Scope extends AutoCloseable { @Override void close(); }

package io.opentelemetry.context.propagation;
public interface ContextPropagators { TextMapPropagator getTextMapPropagator(); }
public interface TextMapPropagator {
    <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter);
    <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter);
}
public interface TextMapSetter<C> {
    void set(@Nullable C carrier, String key, String value);
}
public interface TextMapGetter<C> {
    Iterable<String> keys(C carrier);
    @Nullable String get(@Nullable C carrier, String key);
    // default Iterable<String> getAll(@Nullable C carrier, String key);
}
```

From `io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.40.0-alpha` (already on producer + consumer classpath; otel-bootstrap inherits as `provided` if needed — see Task 1 note):
```java
package io.opentelemetry.semconv.incubating;
public final class MessagingIncubatingAttributes {
    public static final AttributeKey<String> MESSAGING_SYSTEM;
    public static final AttributeKey<String> MESSAGING_DESTINATION_NAME;
    public static final AttributeKey<String> MESSAGING_OPERATION_TYPE;
    public static final AttributeKey<String> MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;

    public static final class MessagingSystemIncubatingValues {
        public static final String RABBITMQ = "rabbitmq";
    }
    public static final class MessagingOperationTypeIncubatingValues {
        public static final String SEND = "send";
        public static final String PROCESS = "process";
        public static final String RECEIVE = "receive";
        public static final String CREATE = "create";
        // PUBLISH (deprecated) — use SEND instead
    }
}
```

From `org.springframework.amqp:spring-rabbit:3.2.8` (BOM-managed via Spring Boot 3.4.13 BOM):
```java
package org.springframework.amqp.core;
public class Message {
    public MessageProperties getMessageProperties();
    public byte[] getBody();
}
public class MessageProperties {
    public Map<String, Object> getHeaders();             // never null
    public Object getHeader(String key);
    public void setHeader(String key, Object value);
    public String getReceivedExchange();                 // populated on inbound deliveries
    public String getReceivedRoutingKey();               // populated on inbound deliveries
}
public interface MessagePostProcessor {
    Message postProcessMessage(Message message) throws AmqpException;
    // 2-arg overload (default delegates to 1-arg):
    default Message postProcessMessage(Message message, Correlation correlation) { return postProcessMessage(message); }
    // 4-arg overload (default delegates to 2-arg) — the one RabbitTemplate.doSend invokes for setBeforePublishPostProcessors
    default Message postProcessMessage(Message message, Correlation correlation, String exchange, String routingKey) {
        return postProcessMessage(message, correlation);
    }
}
public interface Correlation {}
```

From `org.springframework:spring-aop:6.x` (BOM-managed; brings `org.aopalliance:aopalliance` transitively):
```java
package org.aopalliance.intercept;
public interface MethodInterceptor extends Interceptor {
    Object invoke(MethodInvocation invocation) throws Throwable;
}
public interface MethodInvocation {
    Object[] getArguments();
    Object proceed() throws Throwable;
}
```

Verified per RESEARCH.md FLAG #1/#2/#3 (Spring AMQP v3.2.8 source):
- `RabbitTemplate.doSend(...)` invokes the **4-arg** `MessagePostProcessor.postProcessMessage(Message, Correlation, String exchange, String routingKey)` overload when registered via `setBeforePublishPostProcessors(...)`.
- For `@RabbitListener`-driven consumers, the advice chain wraps `ContainerDelegate.invokeListener(Channel channel, Object data)` — `inv.getArguments()[0]` is the `Channel`, `inv.getArguments()[1]` is the data (`Message` for non-batch listeners).
- The advice runs synchronously, on the same thread, with `Scope.makeCurrent()` visible to the user method body.
</interfaces>
</context>

<tasks>

<task id="03-01-T1" type="auto">
  <name>Task 1: Add 3 BOM-managed dependencies to otel-bootstrap/pom.xml (opentelemetry-api compile, spring-rabbit provided, spring-aop provided)</name>
  <files>otel-bootstrap/pom.xml</files>
  <read_first>
    - otel-bootstrap/pom.xml (current state — 22 lines, ZERO dependencies, just parent + artifactId + name + description + a Phase-1-placeholder comment per RESEARCH.md "Existing Code Confirmation")
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 38-46 — D-01: pure Java classes; lines 50-52 — D-02: four classes; lines 209 — Phase 3 ADDS 3 deps; line 142 — Claude's Discretion: provided for spring-amqp + spring-aop, compile for opentelemetry-api)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 112-120 — Stack table "New deps for otel-bootstrap/pom.xml" with exact coordinates + scopes; line 117 — note on spring-rabbit BOM-managed; line 119 — note on spring-aop transitively brings aopalliance)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 559-595 — exact pom.xml transformation: replace Phase-1 placeholder comment with <dependencies> block; specific dep coordinates + scopes + comment style)
    - producer-service/pom.xml (the analog for dependency-block shape — this is the file 03-PATTERNS calls out as the analog at line 522)
  </read_first>
  <action>
    Open `otel-bootstrap/pom.xml`. Currently the file has 22 lines and contains only the parent / artifactId / name / description / a single `<!-- Phase 1: empty module... -->` comment.

    REPLACE the comment line (line 20) with a `<dependencies>` block. Use the EXACT shape below — three dependency entries, BOM-managed (no `<version>` tags), with brief explanatory comments mirroring the producer-service comment style (so future readers grok the scope choices):

    Insert this block immediately after the `<description>...</description>` line and BEFORE the closing `</project>` tag — replacing the existing single-line `<!-- Phase 1: empty module... -->` comment:

    ```xml
      <dependencies>
        <!--
          OpenTelemetry API — TextMapSetter, TextMapGetter, TextMapPropagator,
          Tracer, Span, Context, SpanKind. Compile scope: required to compile
          the four propagation classes. BOM-managed by opentelemetry-bom:1.61.0
          (parent pom.xml lines 57-63) — no <version>.
        -->
        <dependency>
          <groupId>io.opentelemetry</groupId>
          <artifactId>opentelemetry-api</artifactId>
        </dependency>

        <!--
          Spring AMQP API — Message, MessageProperties, MessagePostProcessor,
          Correlation. Provided scope: consuming services (producer-service,
          consumer-service) bring spring-rabbit transitively via
          spring-boot-starter-amqp; otel-bootstrap only references the API
          types at compile time. BOM-managed by spring-boot-dependencies:3.4.13
          (parent pom.xml line 67) — no <version>.
        -->
        <dependency>
          <groupId>org.springframework.amqp</groupId>
          <artifactId>spring-rabbit</artifactId>
          <scope>provided</scope>
        </dependency>

        <!--
          Spring AOP — transitively brings org.aopalliance:aopalliance for
          MethodInterceptor / MethodInvocation. Provided scope: consuming
          services bring spring-aop transitively via spring-boot-starter-amqp
          → spring-tx → spring-aop. BOM-managed.
        -->
        <dependency>
          <groupId>org.springframework</groupId>
          <artifactId>spring-aop</artifactId>
          <scope>provided</scope>
        </dependency>

        <!--
          OpenTelemetry semconv-incubating — MessagingIncubatingAttributes
          constants are referenced by the propagation classes; while constants
          inline at compile, the artifact is needed to compile this module
          standalone. Provided scope: consuming services already pin
          1.40.0-alpha directly. NOT BOM-managed — explicit version required.
        -->
        <dependency>
          <groupId>io.opentelemetry.semconv</groupId>
          <artifactId>opentelemetry-semconv-incubating</artifactId>
          <version>1.40.0-alpha</version>
          <scope>provided</scope>
        </dependency>

        <!--
          JUnit 5 — for the MessagePropertiesRoundTripTest pure unit test
          (PITFALLS.md #2 regression net for the byte[]-vs-String discipline).
          Test scope: only used during mvn test. BOM-managed.
        -->
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-test</artifactId>
          <scope>test</scope>
        </dependency>
      </dependencies>
    ```

    NOTE on the semconv-incubating dep: 03-PATTERNS.md (line 593) flagged this as a planner decision point — "if mvn package on otel-bootstrap fails to compile due to missing semconv classes, add semconv-incubating as provided." Since `TracingMessagePostProcessor` and `TracingMessageListenerAdvice` reference `MessagingIncubatingAttributes` constants, we add it preemptively as `provided` scope (since both consuming services already pin 1.40.0-alpha directly). This avoids a second build cycle.

    NOTE on the spring-boot-starter-test dep: enables the round-trip unit test (Task 6). All otel-bootstrap unit tests are pure (no Spring context), but `spring-boot-starter-test` is the standard way to pull in JUnit 5 + AssertJ at test scope; matches the pattern in producer-service/pom.xml line 102-105 and consumer-service/pom.xml line 102-105.

    UPDATE `<description>` (line 18) from:
    ```
    Shared OTel helper module. Phase 1: empty placeholder. Phase 3 populates it with the AMQP propagation pair.
    ```
    to:
    ```
    Shared OTel helper module — AMQP W3C context propagation pair (TracingMessagePostProcessor + TracingMessageListenerAdvice + MessagePropertiesSetter / MessagePropertiesGetter). Used by producer-service and consumer-service.
    ```

    DO NOT change `<parent>`, `<artifactId>`, `<packaging>`, or `<name>`.

    After the edit, run from the repo root:
    ```
    mvn -pl otel-bootstrap dependency:tree -Dincludes=io.opentelemetry,org.springframework
    ```
    Expect to see `io.opentelemetry:opentelemetry-api:jar:1.61.0:compile`, `org.springframework.amqp:spring-rabbit:jar:3.2.8:provided`, `org.springframework:spring-aop:jar:6.x.x:provided`, `io.opentelemetry.semconv:opentelemetry-semconv-incubating:jar:1.40.0-alpha:provided`. The Spring Boot patch version may vary slightly — only the major version (6.x) matters.

    Maven enforcer's `<dependencyConvergence/>` (parent pom.xml lines 113-145) MUST still pass for the reactor — run `mvn validate` at repo root and expect exit 0.
  </action>
  <acceptance_criteria>
    - `test -f otel-bootstrap/pom.xml` exits 0
    - opentelemetry-api dep present (compile scope, no version): `grep -A2 '<artifactId>opentelemetry-api</artifactId>' otel-bootstrap/pom.xml | grep -v '<version>' | grep -q 'opentelemetry-api'` exits 0
    - spring-rabbit dep present with provided scope: `grep -B1 -A3 '<artifactId>spring-rabbit</artifactId>' otel-bootstrap/pom.xml | grep -q '<scope>provided</scope>'` exits 0
    - spring-aop dep present with provided scope: `grep -B1 -A3 '<artifactId>spring-aop</artifactId>' otel-bootstrap/pom.xml | grep -q '<scope>provided</scope>'` exits 0
    - semconv-incubating dep present with explicit version 1.40.0-alpha and provided scope: `grep -B1 -A4 '<artifactId>opentelemetry-semconv-incubating</artifactId>' otel-bootstrap/pom.xml | grep -q '1.40.0-alpha'` exits 0
    - spring-boot-starter-test dep present with test scope: `grep -B1 -A3 '<artifactId>spring-boot-starter-test</artifactId>' otel-bootstrap/pom.xml | grep -q '<scope>test</scope>'` exits 0
    - opentelemetry-api has NO <version> tag (BOM-managed): `awk '/<artifactId>opentelemetry-api</,/<\/dependency>/' otel-bootstrap/pom.xml | grep -c '<version>'` returns 0
    - spring-rabbit has NO <version> tag (BOM-managed): `awk '/<artifactId>spring-rabbit</,/<\/dependency>/' otel-bootstrap/pom.xml | grep -c '<version>'` returns 0
    - spring-aop has NO <version> tag (BOM-managed): `awk '/<artifactId>spring-aop</,/<\/dependency>/' otel-bootstrap/pom.xml | grep -c '<version>'` returns 0
    - description updated: `grep -q 'AMQP W3C context propagation pair' otel-bootstrap/pom.xml` exits 0
    - Phase-1 placeholder comment removed: `grep -c 'Phase 1: empty module' otel-bootstrap/pom.xml` returns 0
    - Maven dependency convergence passes: `mvn -q validate` exits 0
    - otel-bootstrap module compiles standalone (the four .java files don't exist YET — this gate is on the empty module's pom.xml resolving deps): `mvn -pl otel-bootstrap dependency:resolve` exits 0
  </acceptance_criteria>
  <verify>
    <automated>grep -q '<artifactId>opentelemetry-api</artifactId>' otel-bootstrap/pom.xml &amp;&amp; grep -q '<artifactId>spring-rabbit</artifactId>' otel-bootstrap/pom.xml &amp;&amp; grep -q '<artifactId>spring-aop</artifactId>' otel-bootstrap/pom.xml &amp;&amp; grep -q '<artifactId>opentelemetry-semconv-incubating</artifactId>' otel-bootstrap/pom.xml &amp;&amp; grep -q '1.40.0-alpha' otel-bootstrap/pom.xml &amp;&amp; ! grep -q 'Phase 1: empty module' otel-bootstrap/pom.xml &amp;&amp; mvn -q -pl otel-bootstrap dependency:resolve</automated>
  </verify>
  <done>otel-bootstrap/pom.xml has the 5 dependencies (opentelemetry-api compile, spring-rabbit provided, spring-aop provided, opentelemetry-semconv-incubating:1.40.0-alpha provided, spring-boot-starter-test test) declared with the BOM-managed convention (no <version> on the 3 BOM-backed deps); description updated; Phase 1 placeholder comment removed; mvn validate succeeds with no convergence errors.</done>
</task>

<task id="03-01-T2" type="auto">
  <name>Task 2: Create MessagePropertiesSetter (TextMapSetter&lt;MessageProperties&gt; — String-only header writes)</name>
  <files>otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java</files>
  <read_first>
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 411-424 — Pattern 3: exact code shape; lines 522-530 — Pitfall 2: byte[] vs String)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 127-156 — MessagePropertiesSetter pattern + concrete excerpt + transformation notes)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 50-52 — D-02: TextMapSetter writes header values as String, never byte[])
    - .planning/research/PITFALLS.md (#2 — referenced from RESEARCH.md; the byte[]-vs-String pitfall this class exists to neutralize)
  </read_first>
  <action>
    Create the new directory `otel-bootstrap/src/main/java/com/example/otel/amqp/` (note: an empty parent `com/example/otel/` exists with `package-info.java` — leave it untouched).

    Create the file `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` with the EXACT content below — pure Java class implementing `io.opentelemetry.context.propagation.TextMapSetter<MessageProperties>`. NO Spring annotations (per D-01). Stateless (no instance fields).

    ```java
    package com.example.otel.amqp;

    import io.opentelemetry.context.propagation.TextMapSetter;
    import org.springframework.amqp.core.MessageProperties;

    /**
     * {@link TextMapSetter} that writes W3C trace-context header values into a
     * Spring AMQP {@link MessageProperties} carrier as <strong>String</strong>
     * values — never {@code byte[]} or any binary form.
     *
     * <p>This discipline neutralises CRITICAL pitfall #2 from
     * {@code .planning/research/PITFALLS.md}: if header values are written as
     * {@code byte[]}, the consumer-side {@code MessageProperties.getHeader(key)}
     * returns a {@code LongStringHelper.ByteArrayLongString} whose value cannot
     * be matched by an {@code instanceof String} check — the W3C extract
     * silently returns {@code Context.root()}, and the consumer span
     * starts a NEW root trace instead of joining the producer's trace.
     *
     * <p>The OpenTelemetry {@code W3CTraceContextPropagator} always passes
     * {@code String} values through {@link #set}, so this implementation just
     * forwards them to {@link MessageProperties#setHeader(String, Object)}
     * (the headers map is a {@code HashMap<String, Object>}); the AMQP wire
     * encoding turns the String into an AMQP {@code longstr} field that the
     * consumer's {@link MessagePropertiesGetter} can decode back to a String.
     *
     * <p>No Spring annotations on this class (per CONTEXT.md D-01 — the
     * propagation classes are pure Java; Spring wiring lives in each service's
     * {@code RabbitConfig.java}).
     */
    public class MessagePropertiesSetter implements TextMapSetter<MessageProperties> {

        @Override
        public void set(MessageProperties carrier, String key, String value) {
            // OTel TextMapSetter spec: carrier MAY be null. Defensive guard
            // — without this, propagator.inject(...) leaks an NPE from inside
            // the SDK call.
            if (carrier == null) {
                return;
            }
            // value is always String here (W3CTraceContextPropagator only
            // passes String values through set). Round-trips cleanly to the
            // consumer (PITFALLS.md #2).
            carrier.setHeader(key, value);
        }
    }
    ```

    Verify the file:
    - Path is exactly `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java`.
    - Class is package-public (no modifier on the class declaration is fine — Java defaults to package-private; but `TracingMessagePostProcessor` is in the SAME package so package-private works. Use `public class` for clarity since the class is referenced from outside the package via a singleton field — actually NO, it's only used inside the package as a `private static final` field in `TracingMessagePostProcessor`. Package-private is correct. Use `public class` ANYWAY for symmetry with the Getter and ease of future testability — public is safer.) → Use `public class MessagePropertiesSetter implements TextMapSetter<MessageProperties>`.
    - The `set(...)` method signature matches `TextMapSetter`'s: `public void set(MessageProperties carrier, String key, String value)` — order: carrier, key, value.
    - No `@Nullable` annotation on the carrier parameter (the OTel API uses `@Nullable` but javax.annotation isn't on the otel-bootstrap classpath; the JavaDoc + the runtime null check is sufficient).
    - JavaDoc references PITFALLS.md #2 by name (the class's reason for existing).
    - No imports beyond the two shown (`TextMapSetter`, `MessageProperties`).

    Then verify compile: `mvn -pl otel-bootstrap compile`. Expect exit 0.
  </action>
  <acceptance_criteria>
    - File exists at exact path: `test -f otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - Class declares `implements TextMapSetter<MessageProperties>`: `grep -q 'implements TextMapSetter<MessageProperties>' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - Package declared correctly: `head -1 otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java | grep -q 'package com.example.otel.amqp;'` exits 0
    - Imports both required types: `grep -q 'import io.opentelemetry.context.propagation.TextMapSetter;' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java &amp;&amp; grep -q 'import org.springframework.amqp.core.MessageProperties;' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - set(...) method has correct signature: `grep -q 'public void set(MessageProperties carrier, String key, String value)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - Defensive null guard on carrier: `grep -q 'if (carrier == null)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - Calls setHeader on String value: `grep -q 'carrier.setHeader(key, value)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - NO @Component / @Service / @Configuration / @Bean annotations (D-01 — pure Java): `grep -cE '@(Component|Service|Configuration|Bean|Autowired)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` returns 0
    - JavaDoc references PITFALLS.md #2: `grep -q 'PITFALLS.md #2' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` exits 0
    - NO byte[] writes: `grep -c 'getBytes\|byte\[\]' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java` returns 0
    - Compiles: `mvn -q -pl otel-bootstrap compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>test -f otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java &amp;&amp; grep -q 'implements TextMapSetter<MessageProperties>' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java &amp;&amp; grep -q 'carrier.setHeader(key, value)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java &amp;&amp; ! grep -qE 'getBytes|byte\[\]' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java &amp;&amp; mvn -q -pl otel-bootstrap compile</automated>
  </verify>
  <done>MessagePropertiesSetter.java exists at the canonical path; implements TextMapSetter&lt;MessageProperties&gt;; writes String values via setHeader; has defensive null-guard on carrier; carries JavaDoc referencing PITFALLS.md #2; has zero Spring annotations; compiles cleanly.</done>
</task>

<task id="03-01-T3" type="auto">
  <name>Task 3: Create MessagePropertiesGetter (TextMapGetter&lt;MessageProperties&gt; — defensive .toString() normalization)</name>
  <files>otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java</files>
  <read_first>
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 426-450 — Pattern 4: exact code shape with @Nullable annotations)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 159-198 — MessagePropertiesGetter pattern + concrete excerpt + transformation notes)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (line 51 — D-02: TextMapGetter defensively normalizes via .toString() for AMQP LongString / byte[] arrival)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java (just created in Task 2 — symmetric class, same package, same JavaDoc style)
  </read_first>
  <action>
    Create the file `otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` with the EXACT content below. Pure Java implementation of `io.opentelemetry.context.propagation.TextMapGetter<MessageProperties>`. Stateless. NO Spring annotations.

    NOTE on `@Nullable`: the OTel SDK uses `javax.annotation.Nullable` in its JavaDoc but `javax.annotation` is NOT a guaranteed transitive dependency. To avoid a compile-classpath surprise, OMIT the `@Nullable` annotation on the parameters/return — the JavaDoc says "may be null" instead. The runtime null-guards are what matter.

    ```java
    package com.example.otel.amqp;

    import io.opentelemetry.context.propagation.TextMapGetter;
    import org.springframework.amqp.core.MessageProperties;

    /**
     * {@link TextMapGetter} that reads W3C trace-context header values from a
     * Spring AMQP {@link MessageProperties} carrier, defensively normalising
     * any non-String storage (AMQP {@code LongString}, raw {@code byte[]})
     * back to a {@link String} via {@link Object#toString()}.
     *
     * <p>This is the symmetric counterpart of {@link MessagePropertiesSetter}
     * and the second half of CRITICAL pitfall #2's mitigation
     * ({@code .planning/research/PITFALLS.md}). Even though our own producer
     * always writes Strings, AMQP brokers sometimes deliver header values
     * wrapped in {@code LongStringHelper.ByteArrayLongString} (which extends
     * {@code AbstractMap.SimpleEntry} and whose {@code toString()} returns the
     * UTF-8 decoded form). The {@code .toString()} call is idempotent for a
     * real {@code String}, well-defined for {@code LongString}, and degrades
     * gracefully (UTF-8 decode) for unexpected {@code byte[]} arrivals.
     *
     * <p>Without this normalisation, an {@code instanceof String} check on the
     * header value would fail for {@code LongString} arrivals → the W3C
     * extract returns {@code Context.root()} → the consumer span starts a NEW
     * root trace, recreating the Phase 2 broken state.
     *
     * <p>No Spring annotations on this class (per CONTEXT.md D-01 — the
     * propagation classes are pure Java; Spring wiring lives in each service's
     * {@code RabbitConfig.java}).
     */
    public class MessagePropertiesGetter implements TextMapGetter<MessageProperties> {

        @Override
        public Iterable<String> keys(MessageProperties carrier) {
            // MessageProperties.getHeaders() returns a non-null Map<String, Object>
            // (HashMap-backed) — verified against Spring AMQP v3.2.8 source.
            return carrier.getHeaders().keySet();
        }

        @Override
        public String get(MessageProperties carrier, String key) {
            // OTel TextMapGetter spec: carrier MAY be null. Defensive guard.
            if (carrier == null) {
                return null;
            }
            Object raw = carrier.getHeader(key);
            // Defensive .toString() normalises String / LongString / byte[]
            // arrivals (PITFALLS.md #2). Returns null if the header is absent.
            return raw == null ? null : raw.toString();
        }
    }
    ```

    Verify with `mvn -pl otel-bootstrap compile`. Expect exit 0.
  </action>
  <acceptance_criteria>
    - File exists at exact path: `test -f otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - Class declares `implements TextMapGetter<MessageProperties>`: `grep -q 'implements TextMapGetter<MessageProperties>' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - Package declared correctly: `head -1 otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java | grep -q 'package com.example.otel.amqp;'` exits 0
    - keys(...) method present: `grep -q 'public Iterable<String> keys(MessageProperties carrier)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - keys(...) returns getHeaders().keySet(): `grep -q 'carrier.getHeaders().keySet()' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - get(...) method present with correct signature: `grep -q 'public String get(MessageProperties carrier, String key)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - get(...) defensively normalizes via .toString(): `grep -q 'raw.toString()' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - Defensive null guard on carrier in get(...): `grep -q 'if (carrier == null)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - NO Spring annotations: `grep -cE '@(Component|Service|Configuration|Bean|Autowired)' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` returns 0
    - JavaDoc references PITFALLS.md #2: `grep -q 'PITFALLS.md #2' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java` exits 0
    - Compiles: `mvn -q -pl otel-bootstrap compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>test -f otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java &amp;&amp; grep -q 'implements TextMapGetter<MessageProperties>' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java &amp;&amp; grep -q 'raw.toString()' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java &amp;&amp; grep -q 'carrier.getHeaders().keySet()' otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java &amp;&amp; mvn -q -pl otel-bootstrap compile</automated>
  </verify>
  <done>MessagePropertiesGetter.java exists; implements TextMapGetter&lt;MessageProperties&gt;; keys() returns getHeaders().keySet(); get() defensively normalizes via raw.toString(); defensive null guard on carrier; zero Spring annotations; compiles cleanly.</done>
</task>

<task id="03-01-T4" type="auto">
  <name>Task 4: Create TracingMessagePostProcessor (4-arg MessagePostProcessor — owns PRODUCER span + traceparent inject; D-05/D-06/D-07)</name>
  <files>otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java</files>
  <read_first>
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 264-331 — Pattern 1: full canonical implementation; lines 39-44 — D-05/D-06/D-07 confirmation; lines 909-924 — verified sources for the 4-arg overload)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 31-75 — TracingMessagePostProcessor pattern + concrete Phase-2 excerpt + 9-item transformation notes)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 60-78 — D-05: post-processor OWNS PRODUCER span; D-06: inject-only span lifetime, span ends BEFORE wire-send; D-07: semconv-correct destination naming with exact span name + attribute values)
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (the Phase 2 inline span body lines 39-83 — structural template Phase 3 lifts into this class with three changes per D-07)
    - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java (lines 172-180 — verified location of the composite W3CTraceContext + W3CBaggage propagator wiring — confirms openTelemetry.getPropagators().getTextMapPropagator() returns the wired propagator per D-04)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java (just created in Task 2 — referenced as static singleton SETTER field)
  </read_first>
  <action>
    Create `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` with the EXACT content below. The structure mirrors RESEARCH.md §Pattern 1 with the JavaDoc expanded to teach the lesson.

    KEY DECISIONS embedded:
    - **D-01:** No Spring annotations on the class.
    - **D-02:** Implements `org.springframework.amqp.core.MessagePostProcessor`; overrides BOTH the 4-arg AND 1-arg overloads (the 1-arg is a defensive no-op `return message;`).
    - **D-03:** Constructor takes `(OpenTelemetry openTelemetry, Tracer tracer)`; both stored as final fields.
    - **D-04:** Reads propagator via `openTelemetry.getPropagators().getTextMapPropagator()` — does NOT construct a new `W3CTraceContextPropagator.getInstance()`.
    - **D-05:** Post-processor OWNS the entire PRODUCER span lifecycle.
    - **D-06:** PRODUCER span lifetime is inject-only — span ends INSIDE `postProcessMessage`, BEFORE `RabbitTemplate.send` writes to the wire. Use `try (Scope) { ... } finally { span.end(); }` — no `catch` (per Claude's Discretion in CONTEXT.md / RESEARCH.md: W3C inject + String setter is essentially infallible).
    - **D-07:** Span name = `exchange + " publish"` (NOT queue); `MESSAGING_DESTINATION_NAME` = `exchange` (NOT queue); `MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY` = `routingKey`; `MESSAGING_SYSTEM` = `RABBITMQ`; `MESSAGING_OPERATION_TYPE` = `SEND`.

    ```java
    package com.example.otel.amqp;

    import io.opentelemetry.api.OpenTelemetry;
    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Context;
    import io.opentelemetry.context.Scope;
    import io.opentelemetry.context.propagation.TextMapPropagator;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
    import org.springframework.amqp.core.Correlation;
    import org.springframework.amqp.core.Message;
    import org.springframework.amqp.core.MessagePostProcessor;
    import org.springframework.amqp.core.MessageProperties;

    /**
     * Producer-side AMQP context propagation: opens a {@link SpanKind#PRODUCER}
     * span, injects W3C trace context headers ({@code traceparent},
     * {@code tracestate}) into {@link MessageProperties} via a
     * {@link TextMapPropagator}, and ends the span — all <em>before</em>
     * {@link org.springframework.amqp.rabbit.core.RabbitTemplate} writes the
     * message to the AMQP wire.
     *
     * <p><strong>Span ownership (CONTEXT.md D-05).</strong> This class OWNS
     * the entire PRODUCER span. Phase 2's inline PRODUCER span in
     * {@code OrderPublisher.publish(...)} is deleted as part of Phase 3 —
     * {@code OrderPublisher.publish(...)} becomes a thin
     * {@code rabbitTemplate.convertAndSend(...)} call.
     *
     * <p><strong>Inject-only span lifetime (CONTEXT.md D-06).</strong> The
     * span tightly wraps the {@link TextMapPropagator#inject} call inside a
     * {@code try / finally}. The span ends BEFORE
     * {@code RabbitTemplate.send(...)} talks to the broker. This matches OTel
     * auto-instrumentation convention for Kafka / JMS / AMQP — broker-level
     * send errors propagate up the call stack and are caught by
     * {@code OrderService.place(...)}'s INTERNAL span via Phase 2's D-03 catch.
     *
     * <p><strong>Semconv-correct destination naming (CONTEXT.md D-07 +
     * RESEARCH FLAG #2).</strong> Span name is
     * {@code "<exchange> publish"} (e.g., {@code "orders publish"}); the
     * {@code messaging.destination.name} attribute is the EXCHANGE — not the
     * queue (Phase 2 used queue, Phase 3 corrects to exchange per OTel
     * messaging semconv RabbitMQ profile). The 4-arg
     * {@link #postProcessMessage(Message, Correlation, String, String)}
     * overload — added in Spring AMQP 2.3.4 and invoked by
     * {@code RabbitTemplate.doSend(...)} when registered via
     * {@code setBeforePublishPostProcessors(...)} — provides the exchange and
     * routing key directly; no plumbing through a separate channel.
     *
     * <p><strong>Per-service Tracer scope (CONTEXT.md D-03).</strong> The
     * {@link Tracer} is injected per service ({@code com.example.producer}),
     * so spans created here still appear under the producer's instrumentation
     * scope in Tempo — NOT under a new {@code com.example.otel.amqp} scope.
     *
     * <p><strong>Propagator reuse (CONTEXT.md D-04).</strong> The propagator
     * is read from {@code openTelemetry.getPropagators().getTextMapPropagator()}
     * — Phase 2 already wired the composite
     * {@code W3CTraceContextPropagator + W3CBaggagePropagator}. This class
     * does NOT construct a fresh {@code W3CTraceContextPropagator.getInstance()}.
     *
     * <p><strong>String header values (PITFALLS.md #2).</strong> The
     * {@link MessagePropertiesSetter} singleton field writes header values as
     * {@link String} — never {@code byte[]} — so the consumer-side getter can
     * round-trip them cleanly.
     */
    public class TracingMessagePostProcessor implements MessagePostProcessor {

        // Stateless / thread-safe; one instance per JVM is sufficient.
        private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter();

        private final OpenTelemetry openTelemetry;
        private final Tracer tracer;

        public TracingMessagePostProcessor(OpenTelemetry openTelemetry, Tracer tracer) {
            this.openTelemetry = openTelemetry;
            this.tracer = tracer;
        }

        /**
         * 4-arg overload — added in Spring AMQP 2.3.4. Invoked by
         * {@code RabbitTemplate.doSend(...)} for processors registered via
         * {@code setBeforePublishPostProcessors(...)} (verified against
         * Spring AMQP v3.2.8 source, RESEARCH FLAG #2).
         */
        @Override
        public Message postProcessMessage(Message message, Correlation correlation,
                                          String exchange, String routingKey) {
            MessageProperties props = message.getMessageProperties();
            Span span = tracer.spanBuilder(exchange + " publish")
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                    MessagingSystemIncubatingValues.RABBITMQ)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                    exchange)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                    MessagingOperationTypeIncubatingValues.SEND)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                    routingKey)
                .startSpan();
            // try / finally only — no catch. propagator.inject(...) over a
            // String-valued setter is essentially infallible (the setter just
            // calls HashMap.put). Broker-level send errors happen LATER in
            // RabbitTemplate.send and are caught by the INTERNAL span in
            // OrderService.place(...) (Phase 2 D-03 catch).
            try (Scope scope = span.makeCurrent()) {
                TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
                propagator.inject(Context.current(), props, SETTER);
                return message;
            } finally {
                span.end();
            }
        }

        /**
         * 1-arg overload — defensive default. {@code RabbitTemplate.doSend(...)}
         * always invokes the 4-arg overload above for
         * {@code beforePublishPostProcessors}; this method exists only to
         * satisfy the {@link MessagePostProcessor} interface contract. If it
         * IS reached, fall through with no instrumentation — the destination
         * identity is unknown at this layer, so we cannot correctly name the
         * span or set the destination attribute.
         */
        @Override
        public Message postProcessMessage(Message message) {
            return message;
        }
    }
    ```

    Verify with `mvn -pl otel-bootstrap compile`. Expect exit 0.
  </action>
  <acceptance_criteria>
    - File exists: `test -f otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - Implements MessagePostProcessor: `grep -q 'implements MessagePostProcessor' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - 4-arg overload signature: `grep -q 'public Message postProcessMessage(Message message, Correlation correlation' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; grep -q 'String exchange, String routingKey)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - 1-arg overload signature: `grep -q 'public Message postProcessMessage(Message message)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - Constructor signature per D-03: `grep -q 'public TracingMessagePostProcessor(OpenTelemetry openTelemetry, Tracer tracer)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - Span name uses exchange (D-07): `grep -q 'spanBuilder(exchange + " publish")' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - SpanKind.PRODUCER set: `grep -q 'setSpanKind(SpanKind.PRODUCER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - MESSAGING_SYSTEM = RABBITMQ: `grep -q 'MessagingSystemIncubatingValues.RABBITMQ' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - MESSAGING_DESTINATION_NAME = exchange (D-07 — uses exchange variable, not RabbitConfig.QUEUE): `grep -A1 'MESSAGING_DESTINATION_NAME' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java | grep -q 'exchange)'` exits 0
    - MESSAGING_OPERATION_TYPE = SEND: `grep -q 'MessagingOperationTypeIncubatingValues.SEND' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY = routingKey: `grep -A1 'MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java | grep -q 'routingKey)'` exits 0
    - Propagator reuse (D-04): `grep -q 'openTelemetry.getPropagators().getTextMapPropagator()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - Does NOT construct a fresh W3CTraceContextPropagator: `grep -c 'W3CTraceContextPropagator.getInstance' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` returns 0
    - propagator.inject called: `grep -q 'propagator.inject(Context.current(), props, SETTER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - try/finally only — NO catch (D-06 + Claude's Discretion): `awk '/postProcessMessage\(Message message, Correlation/,/^    }$/' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java | grep -c '} catch'` returns 0
    - SETTER static singleton: `grep -q 'private static final MessagePropertiesSetter SETTER = new MessagePropertiesSetter()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` exits 0
    - NO Spring annotations on class: `grep -cE '@(Component|Service|Configuration|Bean|Autowired)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` returns 0
    - JavaDoc references D-05, D-06, D-07: `grep -c 'D-05\|D-06\|D-07' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java` returns >= 3
    - Compiles: `mvn -q -pl otel-bootstrap compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>test -f otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; grep -q 'implements MessagePostProcessor' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; grep -q 'spanBuilder(exchange + " publish")' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; grep -q 'setSpanKind(SpanKind.PRODUCER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; grep -q 'propagator.inject(Context.current(), props, SETTER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; grep -q 'openTelemetry.getPropagators().getTextMapPropagator()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; ! grep -q 'W3CTraceContextPropagator.getInstance' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java &amp;&amp; mvn -q -pl otel-bootstrap compile</automated>
  </verify>
  <done>TracingMessagePostProcessor.java exists; implements MessagePostProcessor with both 4-arg and 1-arg overloads; constructor takes (OpenTelemetry, Tracer) per D-03; span name uses exchange parameter (D-07); SpanKind.PRODUCER + 4 messaging semconv attributes (system, destination.name=exchange, operation.type=SEND, rabbitmq.destination.routing_key=routingKey); reads propagator via openTelemetry.getPropagators().getTextMapPropagator() per D-04 (NOT W3CTraceContextPropagator.getInstance); try/finally with no catch (D-06); SETTER static singleton field; zero Spring annotations; compiles.</done>
</task>

<task id="03-01-T5" type="auto">
  <name>Task 5: Create TracingMessageListenerAdvice (MethodInterceptor — extracts traceparent + owns CONSUMER span; D-08/D-10 + RESEARCH FLAG #1/#3)</name>
  <files>otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java</files>
  <read_first>
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (lines 334-407 — Pattern 2: full canonical implementation; lines 13-16 — FLAG #1/#3 resolutions: getArguments()[1] is the Message, advice runs synchronously same-thread; lines 552-557 — Pitfall 6: batch listener composition)
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (lines 78-123 — TracingMessageListenerAdvice pattern + concrete Phase-2 excerpt + 12-item transformation notes)
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (lines 109-115 — D-10: CONSUMER span shape mirrors producer-side; lines 138-139 — Claude's Discretion: defensive instanceof guard)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (Phase 2 inline CONSUMER span lines 46-80 — structural template Phase 3 lifts into this class with the .setParent(Context.root()) → .setParent(extracted) change being the LOAD-BEARING line)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java (just created in Task 3 — referenced as static singleton GETTER field)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessagePostProcessor.java (just created in Task 4 — symmetric structure; mirror its JavaDoc style)
  </read_first>
  <action>
    Create `otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` with the EXACT content below.

    KEY DECISIONS embedded:
    - **D-01:** No Spring annotations on the class.
    - **D-02:** Implements `org.aopalliance.intercept.MethodInterceptor`; single method `Object invoke(MethodInvocation inv) throws Throwable`.
    - **D-03:** Constructor takes `(OpenTelemetry openTelemetry, Tracer tracer)`.
    - **D-04:** Reads propagator via `openTelemetry.getPropagators().getTextMapPropagator()`.
    - **D-09:** OrderListener.onOrder is now thin (3 lines) — the entire CONSUMER-span scaffolding lives here.
    - **D-10:** CONSUMER span shape mirrors PRODUCER side — span name = `exchange + " process"`; `.setParent(extracted)` (the load-bearing line per ROADMAP SC #1); `SpanKind.CONSUMER`; same four messaging.* attributes; catch (Throwable) → recordException + setStatus(ERROR) + rethrow.
    - **RESEARCH FLAG #3:** `inv.getArguments()[1]` is the `Message` (verified against Spring AMQP v3.2.8 source — `ContainerDelegate.invokeListener(Channel, Object data)`).
    - **Pitfall #6:** Defensive `instanceof Message` guard for batch-listener composition — fall through with `inv.proceed()` if the data isn't a single `Message`.

    ```java
    package com.example.otel.amqp;

    import io.opentelemetry.api.OpenTelemetry;
    import io.opentelemetry.api.trace.Span;
    import io.opentelemetry.api.trace.SpanKind;
    import io.opentelemetry.api.trace.StatusCode;
    import io.opentelemetry.api.trace.Tracer;
    import io.opentelemetry.context.Context;
    import io.opentelemetry.context.Scope;
    import io.opentelemetry.context.propagation.TextMapPropagator;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues;
    import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
    import org.aopalliance.intercept.MethodInterceptor;
    import org.aopalliance.intercept.MethodInvocation;
    import org.springframework.amqp.core.Message;
    import org.springframework.amqp.core.MessageProperties;

    /**
     * Consumer-side AMQP context propagation: extracts W3C trace context from
     * incoming {@link MessageProperties}, opens a {@link SpanKind#CONSUMER}
     * span parented to the extracted producer context, and runs the
     * downstream {@code @RabbitListener}-annotated method body inside that
     * span's {@link Scope}.
     *
     * <p><strong>Wiring (CONTEXT.md D-08).</strong> Registered on the
     * consumer-service's {@code SimpleRabbitListenerContainerFactory} via
     * {@code setAdviceChain(this)}. The factory MUST be a Configurer-aided
     * user-defined bean named exactly {@code rabbitListenerContainerFactory}
     * (lowercase r) — see {@code RabbitConfig.rabbitListenerContainerFactory(...)}.
     *
     * <p><strong>The load-bearing line (ROADMAP SC #1).</strong>
     * {@code spanBuilder(...).setParent(extracted)} is the SINGLE LINE that
     * makes {@code consumer.parentSpanId == producer.spanId}. Without it,
     * even with header injection on the producer side, the consumer span
     * would still start a new root trace (Phase 2's broken state).
     *
     * <p><strong>MethodInvocation argument layout (RESEARCH FLAG #3).</strong>
     * The advice chain wraps {@code ContainerDelegate.invokeListener(Channel
     * channel, Object data)} — verified against Spring AMQP v3.2.8 source.
     * So {@code inv.getArguments()[0]} is the {@code Channel}, and
     * {@code inv.getArguments()[1]} is the payload. For non-batch listeners
     * (the only kind this workshop covers) the payload is a {@link Message};
     * for batch listeners (out of scope) it would be a {@code List<Message>}.
     * The defensive {@code instanceof Message} guard skips tracing for the
     * batch case (Pitfall #6) instead of throwing a {@code ClassCastException}.
     *
     * <p><strong>Synchronous, same-thread execution (RESEARCH FLAG #1).</strong>
     * Spring AMQP's listener container does NOT switch threads between this
     * advice and the user method body — verified against
     * {@code AbstractMessageListenerContainer.doInvokeListener(...)} →
     * {@code MessagingMessageListenerAdapter.onMessage(...)} call chain. The
     * {@link Scope#makeCurrent()} opened here IS visible to the user
     * {@code onOrder(...)} body — Phase 5's MDC injector will pick up the
     * {@code trace_id} / {@code span_id} from {@code Span.current()} when
     * the {@code LOG.info(...)} fires inside the listener.
     *
     * <p><strong>Per-service Tracer scope (CONTEXT.md D-03).</strong> The
     * {@link Tracer} is injected per service ({@code com.example.consumer}),
     * so the CONSUMER span appears under the consumer's instrumentation scope
     * in Tempo — NOT under {@code com.example.otel.amqp}.
     *
     * <p><strong>Catch shape (CONTEXT.md D-10).</strong>
     * {@code catch (Throwable t)} matches {@code MethodInterceptor.invoke}'s
     * {@code throws Throwable}; {@code recordException(t) + setStatus(ERROR)
     * + throw t} mirrors Phase 2's D-03 catch on the INTERNAL span. The
     * rethrow lets Spring AMQP's listener container handle the NACK; combined
     * with {@code defaultRequeueRejected(false)} on the factory (D-13), failed
     * messages are dropped (no DLX per PROJECT.md). The CONSUMER span carries
     * the exception event — workshop attendees see ERROR status + the
     * {@code exception.type} attribute in Tempo (TRACE-09 + ROADMAP SC #3).
     */
    public class TracingMessageListenerAdvice implements MethodInterceptor {

        // Stateless / thread-safe; one instance per JVM is sufficient.
        private static final MessagePropertiesGetter GETTER = new MessagePropertiesGetter();

        private final OpenTelemetry openTelemetry;
        private final Tracer tracer;

        public TracingMessageListenerAdvice(OpenTelemetry openTelemetry, Tracer tracer) {
            this.openTelemetry = openTelemetry;
            this.tracer = tracer;
        }

        @Override
        public Object invoke(MethodInvocation inv) throws Throwable {
            // ContainerDelegate.invokeListener(Channel channel, Object data):
            // args[0] = Channel; args[1] = data (Message for non-batch listeners).
            Object data = inv.getArguments()[1];
            if (!(data instanceof Message message)) {
                // Batch listener (List<Message>) or unexpected shape — skip
                // tracing, proceed without wrapping. This phase doesn't teach
                // batch listeners (Pitfall #6).
                return inv.proceed();
            }
            MessageProperties props = message.getMessageProperties();
            TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
            Context extracted = propagator.extract(Context.current(), props, GETTER);

            // Inbound exchange + routing key are populated on consumed messages
            // (Spring AMQP MessageProperties.getReceivedExchange / getReceivedRoutingKey).
            String exchange = props.getReceivedExchange();
            String routingKey = props.getReceivedRoutingKey();

            Span span = tracer.spanBuilder(exchange + " process")
                .setParent(extracted)                          // <-- LOAD-BEARING (ROADMAP SC #1)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                    MessagingSystemIncubatingValues.RABBITMQ)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                    exchange)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE,
                    MessagingOperationTypeIncubatingValues.PROCESS)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                    routingKey)
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                return inv.proceed();
            } catch (Throwable t) {
                span.recordException(t);
                span.setStatus(StatusCode.ERROR);
                throw t;
            } finally {
                span.end();
            }
        }
    }
    ```

    Verify with `mvn -pl otel-bootstrap compile`. Expect exit 0.
  </action>
  <acceptance_criteria>
    - File exists: `test -f otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - Implements MethodInterceptor: `grep -q 'implements MethodInterceptor' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - invoke(MethodInvocation) signature: `grep -q 'public Object invoke(MethodInvocation inv) throws Throwable' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - Constructor signature per D-03: `grep -q 'public TracingMessageListenerAdvice(OpenTelemetry openTelemetry, Tracer tracer)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - Defensive instanceof guard (Pitfall #6 + RESEARCH FLAG #3): `grep -q 'if (!(data instanceof Message message))' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - Reads getArguments()[1] (RESEARCH FLAG #3): `grep -q 'inv.getArguments()\[1\]' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - propagator.extract called: `grep -q 'propagator.extract(Context.current(), props, GETTER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - Span name uses exchange (D-10): `grep -q 'spanBuilder(exchange + " process")' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - LOAD-BEARING .setParent(extracted) (ROADMAP SC #1): `grep -q '.setParent(extracted)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - NOT .setParent(Context.root()) (the Phase 2 line being replaced): `grep -c 'setParent(Context.root' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` returns 0
    - SpanKind.CONSUMER: `grep -q 'setSpanKind(SpanKind.CONSUMER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - 4 messaging semconv attributes set: `grep -c 'MESSAGING_SYSTEM\|MESSAGING_DESTINATION_NAME\|MESSAGING_OPERATION_TYPE\|MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` returns >= 4
    - MESSAGING_OPERATION_TYPE = PROCESS: `grep -q 'MessagingOperationTypeIncubatingValues.PROCESS' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - destination uses props.getReceivedExchange(): `grep -q 'props.getReceivedExchange()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - routing key uses props.getReceivedRoutingKey(): `grep -q 'props.getReceivedRoutingKey()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - inv.proceed() called: `grep -q 'inv.proceed()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - catch (Throwable t) with recordException + setStatus(ERROR) + rethrow (D-10): `grep -q 'catch (Throwable t)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q 'span.recordException(t)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q 'span.setStatus(StatusCode.ERROR)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - GETTER static singleton: `grep -q 'private static final MessagePropertiesGetter GETTER = new MessagePropertiesGetter()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - Propagator reuse (D-04): `grep -q 'openTelemetry.getPropagators().getTextMapPropagator()' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` exits 0
    - NO W3CTraceContextPropagator.getInstance: `grep -c 'W3CTraceContextPropagator.getInstance' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` returns 0
    - NO Spring annotations: `grep -cE '@(Component|Service|Configuration|Bean|Autowired)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` returns 0
    - JavaDoc references D-08, D-10, FLAG #1, FLAG #3, ROADMAP SC #1: `grep -cE 'D-08|D-10|FLAG #1|FLAG #3|SC #1' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java` returns >= 5
    - Compiles: `mvn -q -pl otel-bootstrap compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>test -f otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q 'implements MethodInterceptor' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q 'inv.getArguments()\[1\]' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q 'if (!(data instanceof Message message))' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q '.setParent(extracted)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; grep -q 'setSpanKind(SpanKind.CONSUMER)' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; ! grep -q 'setParent(Context.root' otel-bootstrap/src/main/java/com/example/otel/amqp/TracingMessageListenerAdvice.java &amp;&amp; mvn -q -pl otel-bootstrap compile</automated>
  </verify>
  <done>TracingMessageListenerAdvice.java exists; implements MethodInterceptor; reads getArguments()[1] with instanceof Message guard (FLAG #3 + Pitfall #6); calls propagator.extract; CONSUMER span shape with .setParent(extracted) (LOAD-BEARING per SC #1) + 4 messaging semconv attributes; catch(Throwable) recordException+setStatus(ERROR)+rethrow; GETTER static singleton; reads propagator via openTelemetry.getPropagators(); zero Spring annotations; compiles.</done>
</task>

<task id="03-01-T6" type="auto">
  <name>Task 6: Create MessagePropertiesRoundTripTest (pure JUnit 5 — String-only header round-trip; PITFALLS.md #2 regression net)</name>
  <files>otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java</files>
  <read_first>
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (line 62 — Claude's Discretion: "OPTIONAL setter↔getter unit test... ~30 lines, pure unit test (no Spring, no Testcontainers, no broker)")
    - .planning/phases/03-amqp-context-propagation/03-PATTERNS.md (line 195 — "OPTIONAL setter↔getter round-trip unit test"; line 759 — "round-trip proof at the lowest level")
    - .planning/phases/03-amqp-context-propagation/03-CONTEXT.md (line 51 — D-02: "its own unit-testable surface (the setter↔getter round-trip test mentioned in PITFALLS.md #2)"; lines 261-266 — "OPTIONAL — Phase 6 alternative; Claude's discretion")
    - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesSetter.java (the class under test)
    - otel-bootstrap/src/main/java/com/example/otel/amqp/MessagePropertiesGetter.java (the class under test)
  </read_first>
  <action>
    Create `otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` with the EXACT content below. Pure JUnit 5 + AssertJ unit test — NO Spring context, NO Testcontainers, NO live broker. Uses `org.springframework.amqp.core.MessageProperties` as a real object (it's just a POJO backed by `HashMap<String, Object>`).

    The test rationale: this is the LOWEST-LEVEL regression net for PITFALLS.md #2 — it exercises the exact mechanism (`setter.set(...)` → `MessageProperties.headers` HashMap → `getter.get(...)`) that any future "let's write byte[] for binary safety" change would break. Each assert is one-line; total file ~80 lines including imports + JavaDoc.

    Per CONTEXT.md / RESEARCH.md / PATTERNS.md, this is OPTIONAL (Claude's Discretion). I include it because: (a) it ships at zero infra cost (no broker), (b) catches the regression at unit-test speed, and (c) demonstrates the discipline at the smallest possible scale — a pedagogical win for the workshop.

    ```java
    package com.example.otel.amqp;

    import static org.assertj.core.api.Assertions.assertThat;

    import java.util.Iterator;

    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Test;
    import org.springframework.amqp.core.MessageProperties;

    /**
     * Pure unit test for {@link MessagePropertiesSetter} ↔
     * {@link MessagePropertiesGetter} round-trip.
     *
     * <p>This test is the lowest-level regression net for CRITICAL pitfall #2
     * ({@code .planning/research/PITFALLS.md}): if a future change has the
     * setter write {@code byte[]} (or has the getter assume the value is
     * always a {@link String}), this test fails — long before the broken
     * trace shows up in Tempo.
     *
     * <p>No Spring, no Testcontainers, no broker. {@link MessageProperties}
     * is a plain POJO backed by a {@code HashMap<String, Object>}; we
     * construct one directly and exercise the setter / getter contract.
     */
    class MessagePropertiesRoundTripTest {

        private final MessagePropertiesSetter setter = new MessagePropertiesSetter();
        private final MessagePropertiesGetter getter = new MessagePropertiesGetter();

        @Test
        @DisplayName("setter writes a String value that the getter reads back identically (PITFALLS.md #2)")
        void roundTripStringHeader() {
            MessageProperties props = new MessageProperties();
            String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

            setter.set(props, "traceparent", traceparent);

            // The header MUST be stored as a String — the headers map is
            // HashMap<String, Object>; setHeader does no transformation.
            assertThat(props.getHeader("traceparent"))
                .isInstanceOf(String.class)
                .isEqualTo(traceparent);

            // Getter reads back identical value.
            assertThat(getter.get(props, "traceparent")).isEqualTo(traceparent);
        }

        @Test
        @DisplayName("getter normalises a non-String header value via .toString() (PITFALLS.md #2 — defensive)")
        void getterNormalisesNonStringHeader() {
            // Simulate an upstream that wrote a non-String value (e.g., a
            // misconfigured library that wrote byte[] or an AMQP LongString).
            // The getter should normalise via raw.toString() — not return null.
            MessageProperties props = new MessageProperties();
            props.setHeader("custom-non-string", 42);          // Integer; toString() → "42"

            assertThat(getter.get(props, "custom-non-string")).isEqualTo("42");
        }

        @Test
        @DisplayName("getter returns null for an absent header")
        void getterReturnsNullForAbsentHeader() {
            MessageProperties props = new MessageProperties();
            assertThat(getter.get(props, "absent")).isNull();
        }

        @Test
        @DisplayName("getter handles a null carrier defensively")
        void getterHandlesNullCarrier() {
            assertThat(getter.get(null, "traceparent")).isNull();
        }

        @Test
        @DisplayName("setter is a no-op on a null carrier (no NPE)")
        void setterHandlesNullCarrier() {
            // Should not throw; this is the OTel TextMapSetter spec contract.
            setter.set(null, "traceparent", "value");
        }

        @Test
        @DisplayName("getter.keys() exposes every header key (W3CTraceContextPropagator iterates these)")
        void keysExposesAllHeaders() {
            MessageProperties props = new MessageProperties();
            setter.set(props, "traceparent", "00-aaa-bbb-01");
            setter.set(props, "tracestate", "vendor=value");

            Iterable<String> keys = getter.keys(props);
            Iterator<String> iter = keys.iterator();
            assertThat(iter.hasNext()).isTrue();
            assertThat(keys).contains("traceparent", "tracestate");
        }
    }
    ```

    Verify with `mvn -pl otel-bootstrap test`. Expect exit 0 and 6/6 tests pass.

    NOTE on test class visibility: package-private `class MessagePropertiesRoundTripTest` (no modifier) — JUnit 5 can run package-private test classes. Matches modern JUnit 5 convention.
  </action>
  <acceptance_criteria>
    - File exists: `test -f otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` exits 0
    - Package declared correctly: `head -1 otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java | grep -q 'package com.example.otel.amqp;'` exits 0
    - Uses JUnit 5 (org.junit.jupiter): `grep -q 'import org.junit.jupiter.api.Test;' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` exits 0
    - Uses AssertJ: `grep -q 'import static org.assertj.core.api.Assertions.assertThat;' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` exits 0
    - Tests both setter and getter together (round-trip): `grep -c 'setter.set\|getter.get' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` returns >= 4
    - Asserts String type stored (PITFALLS.md #2): `grep -q 'isInstanceOf(String.class)' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` exits 0
    - 6 tests defined: `grep -c '^    @Test$' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` returns 6
    - JavaDoc references PITFALLS.md #2: `grep -q 'PITFALLS.md #2' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java` exits 0
    - Tests pass: `mvn -pl otel-bootstrap test 2>&1 | tail -30 | grep -q 'BUILD SUCCESS'` exits 0
    - All 6 tests reported PASSED: `mvn -pl otel-bootstrap test 2>&1 | grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' | tail -1 | grep -qE 'Tests run: 6, Failures: 0, Errors: 0, Skipped: 0'` exits 0
  </acceptance_criteria>
  <verify>
    <automated>test -f otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java &amp;&amp; grep -q 'isInstanceOf(String.class)' otel-bootstrap/src/test/java/com/example/otel/amqp/MessagePropertiesRoundTripTest.java &amp;&amp; mvn -q -pl otel-bootstrap test &amp;&amp; mvn -pl otel-bootstrap test 2>&amp;1 | grep -E 'Tests run: [0-9]+' | tail -1 | grep -qE 'Tests run: 6, Failures: 0, Errors: 0'</automated>
  </verify>
  <done>MessagePropertiesRoundTripTest.java exists at otel-bootstrap/src/test/java/...; uses JUnit 5 + AssertJ; defines 6 tests covering String round-trip, non-String normalization, null-header absence, null-carrier defense (both setter and getter), and keys() enumeration; all tests pass; mvn -pl otel-bootstrap test exits 0.</done>
</task>

<task id="03-01-T7" type="auto">
  <name>Task 7: Full-reactor build verification — mvn install on the full project, otel-bootstrap JAR appears in local Maven repo</name>
  <files>(none — verification only)</files>
  <read_first>
    - otel-bootstrap/pom.xml (the just-modified pom)
    - All four files just created (Tasks 2-5)
    - The test file (Task 6)
    - .planning/phases/03-amqp-context-propagation/03-RESEARCH.md (line 575-583 — Runtime State Inventory: build artifacts; otel-bootstrap-0.1.0-SNAPSHOT.jar)
  </read_first>
  <action>
    Run a clean install at the repo root to verify:
    1. The reactor builds in the correct order (otel-bootstrap → producer-service → consumer-service per parent POM <modules> ordering).
    2. The four new propagation classes compile and pass dependency analysis.
    3. The unit test runs and passes.
    4. The otel-bootstrap-0.1.0-SNAPSHOT.jar lands in `~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/` and contains the four .class files.
    5. The Maven enforcer's `<dependencyConvergence/>` rule still passes (no version drift across the reactor).
    6. The `mise run verify:bom` task still passes (no new OTel artifacts changed the BOM-managed-version invariant).

    Exact commands to run, IN ORDER:

    ```sh
    # 1. Clean build of just otel-bootstrap (fastest feedback loop)
    mvn -pl otel-bootstrap clean install

    # 2. Verify the JAR contains the four .class files
    JAR=~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar
    test -f "$JAR"
    jar tf "$JAR" | grep -E 'com/example/otel/amqp/(TracingMessagePostProcessor|TracingMessageListenerAdvice|MessagePropertiesSetter|MessagePropertiesGetter)\.class' | wc -l
    # Expect: 4

    # 3. Full-reactor build (validates downstream consumers compile against the new module)
    mvn clean install -DskipTests=false

    # 4. mise verify:bom — Phase 2 invariant still holds
    mise run verify:bom

    # 5. Confirm tree is clean (no rogue test reports / target outputs leaked)
    git status --porcelain | grep -v '^??' | head
    # Expect output: only the 5 NEW files + 1 MODIFIED otel-bootstrap/pom.xml
    git status --porcelain | grep -v '^??.*target/' | head
    ```

    The full-reactor `mvn clean install` may take ~30-60 seconds (it compiles producer-service + consumer-service even though they don't yet depend on otel-bootstrap — Wave 2 plans 03-02 and 03-03 add that dependency). Producer + consumer compile WITHOUT issue because their existing code does not yet reference any class from `com.example.otel.amqp.*`.

    If `mvn clean install` fails with a dependency convergence error, debug by running `mvn -pl otel-bootstrap dependency:tree -Dverbose` and inspecting the tree for duplicate versions. The most likely cause would be a transitive `org.aopalliance:aopalliance` version mismatch — fix by aligning with the version Spring AOP brings.

    NOTE on `verify:bom`: This Phase 1/Phase 2 task asserts the OTel BOM invariant; otel-bootstrap's three new BOM-managed deps don't introduce new OTel artifact COORDINATES (just new transitive paths to opentelemetry-api which already appears once per service per Phase 2's invariant). The invariant should hold. If `verify:bom` fails, inspect its output — likely the assertion was tuned for "exactly N OTel jars in the producer + consumer modules" and now finds the same N because otel-bootstrap declares opentelemetry-api as `compile` (which means it's transitively visible to producer + consumer once 03-02/03-03 add the dependency). Worst case: defer the verify:bom rerun to plan 03-05 (the README + tag plan) where the full reactor's BOM state is final.
  </action>
  <acceptance_criteria>
    - `mvn -pl otel-bootstrap clean install` exits 0
    - otel-bootstrap-0.1.0-SNAPSHOT.jar exists in local Maven repo: `test -f ~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar` exits 0
    - JAR contains 4 propagation classes: `jar tf ~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar | grep -cE 'com/example/otel/amqp/(TracingMessagePostProcessor|TracingMessageListenerAdvice|MessagePropertiesSetter|MessagePropertiesGetter)\.class'` returns 4
    - Full-reactor build passes: `mvn clean install` exits 0 (no -DskipTests; otel-bootstrap unit test must run)
    - 6 unit tests passed in otel-bootstrap module: `find . -path '*/target/surefire-reports/*' -name '*MessagePropertiesRoundTripTest*.xml' | xargs grep -l 'tests="6"' | xargs grep -l 'failures="0" errors="0"' | head -1` finds at least one matching report
    - Phase 2 BOM invariant preserved: `mise run verify:bom` exits 0 OR (if verify:bom newly fails due to otel-bootstrap declaring opentelemetry-api at compile scope, document in SUMMARY.md and proceed — verification deferred to plan 03-05)
    - No unexpected uncommitted changes: only the 5 new files + 1 modified pom.xml + auto-generated target/ folders show up in git status
  </acceptance_criteria>
  <verify>
    <automated>mvn -q clean install &amp;&amp; test -f ~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar &amp;&amp; [ "$(jar tf ~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar | grep -cE 'com/example/otel/amqp/(TracingMessagePostProcessor|TracingMessageListenerAdvice|MessagePropertiesSetter|MessagePropertiesGetter)\.class')" = "4" ]</automated>
  </verify>
  <done>Full-reactor `mvn clean install` exits 0; otel-bootstrap-0.1.0-SNAPSHOT.jar contains exactly the 4 propagation classes; 6 round-trip unit tests pass; verify:bom either passes or its failure is documented as a known artifact of compile-scope opentelemetry-api in otel-bootstrap (resolved naturally in 03-05's full-reactor verification).</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries (Plan 03-01 — otel-bootstrap module population)

| Boundary | Description |
|----------|-------------|
| AMQP wire (RabbitMQ broker ↔ consumer) | Header values cross JVM boundaries; the propagation pair is the trust mediator |
| Build classpath ↔ runtime classpath | otel-bootstrap declares `provided` scope for spring-rabbit + spring-aop; consuming services must bring matching versions (BOM-managed → guaranteed to match) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-3-01-01 | Spoofing | A malicious upstream injects a crafted `traceparent` value to link unrelated traces or cause parser exceptions | mitigate | Rely on `W3CTraceContextPropagator.extract` — verified per RESEARCH.md to validate format strictly (`00-<32-hex>-<16-hex>-<2-hex>`) and silently fall back to `Context.current()` on malformed input. Workshop scope: only our own producer publishes; production posture documented in JavaDoc. |
| T-3-01-02 | Tampering | Header injection writes byte[] (PITFALLS.md #2 regression) → consumer-side .toString() returns garbage → traces silently fragment | mitigate | `MessagePropertiesSetter` writes `String` only (carrier.setHeader); `MessagePropertiesGetter.get` defensively `.toString()`s any value. The `MessagePropertiesRoundTripTest` (Task 6) provides a unit-test regression net. JavaDoc on both classes warns the next maintainer. |
| T-3-01-03 | Information Disclosure | Span attributes (`messaging.destination.name`, `messaging.rabbitmq.destination.routing_key`) expose AMQP topology | accept | Workshop-local strings (`orders` exchange, `order.created` routing key); no PII; same posture as Phase 2 (TC-2-04-02). |
| T-3-01-04 | DoS | propagator.inject / propagator.extract throws unexpectedly under load → producer-side request fails | mitigate | TracingMessagePostProcessor uses try/finally only — span.end() always runs; no thrown-exception path swallowed. Broker-level errors propagate up to OrderService.place's INTERNAL span (Phase 2 D-03 catch). For the consumer side, the advice's catch (Throwable) ensures the span is closed and ERROR-stamped before rethrow; Spring AMQP's container handles the NACK with defaultRequeueRejected=false (D-13, plan 03-03) — failed messages drop instead of looping. |

**No CRITICAL/HIGH security blockers.** All threats are workshop-bounded.
</threat_model>

<verification>
- All 4 propagation classes (`TracingMessagePostProcessor`, `TracingMessageListenerAdvice`, `MessagePropertiesSetter`, `MessagePropertiesGetter`) exist under `otel-bootstrap/src/main/java/com/example/otel/amqp/`.
- All 4 classes carry zero Spring annotations on the class itself (D-01).
- TracingMessagePostProcessor.postProcessMessage(4-arg) sets the 4 messaging semconv attributes with EXCHANGE as `messaging.destination.name` (D-07 correction from Phase 2's queue).
- TracingMessageListenerAdvice.invoke uses `inv.getArguments()[1] instanceof Message` (RESEARCH FLAG #3 + Pitfall #6) and calls `.setParent(extracted)` (LOAD-BEARING per ROADMAP SC #1) — NOT `.setParent(Context.root())`.
- MessagePropertiesSetter writes String (PITFALLS.md #2); MessagePropertiesGetter defensively `.toString()`s.
- otel-bootstrap/pom.xml has 5 dependencies: opentelemetry-api compile, spring-rabbit provided, spring-aop provided, opentelemetry-semconv-incubating:1.40.0-alpha provided, spring-boot-starter-test test.
- `mvn clean install` exits 0; otel-bootstrap-0.1.0-SNAPSHOT.jar contains the 4 .class files.
- `MessagePropertiesRoundTripTest` runs 6 tests, all pass.
- Phase 2 BOM invariant preserved: `mise run verify:bom` exits 0.
</verification>

<success_criteria>
- PROP-01 (writer side) is structurally complete: TracingMessagePostProcessor + MessagePropertiesSetter exist and pass compile; the integration with producer-service's RabbitTemplate happens in plan 03-02.
- PROP-02 (reader side) is structurally complete: TracingMessageListenerAdvice + MessagePropertiesGetter exist and pass compile; the integration with consumer-service's listener factory happens in plan 03-03.
- PROP-04 (shared library structure) is half-satisfied: the 4 files live in the SHARED otel-bootstrap module (the README PROP-04 callout lands in plan 03-05).
- The propagation classes' SHAPE matches CONTEXT.md decisions D-01 through D-10 verbatim (verified via grep gates).
- The setter↔getter round-trip unit test (PITFALLS.md #2 regression net) passes.
- otel-bootstrap-0.1.0-SNAPSHOT.jar is in the local Maven repo, ready for producer-service + consumer-service to consume in Wave 2.
- Full-reactor `mvn clean install` exits 0; the existing producer + consumer code is not affected (no Phase 2 functionality regressed).
</success_criteria>

<output>
After completion, create `.planning/phases/03-amqp-context-propagation/03-01-SUMMARY.md` documenting:
- The 4 source files + 1 test file created (paste their final paths)
- The 5 dependencies added to otel-bootstrap/pom.xml (paste the dependency block)
- Confirmed JAR contents: paste output of `jar tf ~/.m2/repository/com/example/otel-bootstrap/0.1.0-SNAPSHOT/otel-bootstrap-0.1.0-SNAPSHOT.jar | grep amqp`
- Test results: paste the surefire summary line `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- mvn clean install duration (Wave 1 baseline for Phase 3)
- mise run verify:bom result (PASS expected, but document if there's drift due to compile-scope opentelemetry-api in otel-bootstrap)
- Files modified: 6 (1 pom.xml + 4 main sources + 1 test source)
- Wave 2 hand-off: producer-service (plan 03-02) and consumer-service (plan 03-03) can now declare a `<dependency>com.example:otel-bootstrap</dependency>` and `import com.example.otel.amqp.{TracingMessagePostProcessor, TracingMessageListenerAdvice}` inside their RabbitConfig.java
</output>