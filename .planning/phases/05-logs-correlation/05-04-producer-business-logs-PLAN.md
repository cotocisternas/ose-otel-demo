---
phase: 05-logs-correlation
plan: 04
type: execute
wave: 3
depends_on:
  - 05-02
files_modified:
  - producer-service/src/main/java/com/example/producer/api/OrderController.java
  - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
autonomous: true
requirements:
  - LOG-04
tags:
  - slf4j
  - logging
  - producer
  - business-code
must_haves:
  truths:
    - id: LOG-04-producer-controller
      description: "OrderController has a private static final Logger LOG field and a LOG.info call inside create()"
      verify: "grep -q 'private static final Logger LOG' producer-service/src/main/java/com/example/producer/api/OrderController.java && grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/api/OrderController.java"
    - id: LOG-04-producer-publisher
      description: "OrderPublisher has a private static final Logger LOG field and a LOG.info call inside publish() before convertAndSend"
      verify: "grep -q 'private static final Logger LOG' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java"
    - id: D-15-method-name
      description: "LOG.info added inside OrderController.create() (NOT 'handle' — CONTEXT.md typo corrected per PATTERNS.md §D)"
      verify: "awk '/public ResponseEntity.*create\\(/,/^    }$/' producer-service/src/main/java/com/example/producer/api/OrderController.java | grep -q 'LOG.info('"
    - id: D-15-publish-position
      description: "LOG.info appears inside OrderPublisher.publish() BEFORE the rabbitTemplate.convertAndSend call"
      verify: "awk '/public void publish\\(/,/^    }$/' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java | awk '/LOG.info\\(/{found=1; next} found && /rabbitTemplate.convertAndSend/{print \"OK\"; exit 0} END{exit !found}'"
    - id: slf4j-imports
      description: "Both files import org.slf4j.Logger and org.slf4j.LoggerFactory"
      verify: "grep -q 'import org.slf4j.Logger;' producer-service/src/main/java/com/example/producer/api/OrderController.java && grep -q 'import org.slf4j.LoggerFactory;' producer-service/src/main/java/com/example/producer/api/OrderController.java && grep -q 'import org.slf4j.Logger;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && grep -q 'import org.slf4j.LoggerFactory;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java"
    - id: no-lombok
      description: "No @Slf4j annotation (D-15 — consistent with OrderListener pattern)"
      verify: "! grep -q '@Slf4j' producer-service/src/main/java/com/example/producer/api/OrderController.java && ! grep -q '@Slf4j' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java"
    - id: build-clean
      description: "Producer compiles cleanly with the new logger fields"
      verify: "cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile"
  artifacts:
    - path: producer-service/src/main/java/com/example/producer/api/OrderController.java
      provides: "Static SLF4J Logger field + LOG.info entry log inside create() method"
      contains: "private static final Logger LOG"
      contains: "LOG.info("
    - path: producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
      provides: "Static SLF4J Logger field + LOG.info pre-publish log inside publish() method"
      contains: "private static final Logger LOG"
      contains: "LOG.info("
  key_links:
    - from: producer-service/src/main/java/com/example/producer/api/OrderController.java
      to: SLF4J → Logback → MDC_CONSOLE wrapper → CONSOLE / OTEL appender (declared in logback-spring.xml from Plan 05-02)
      via: "LOG.info call dispatches through Logback's appender chain"
      pattern: "LOG.info\\("
    - from: producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
      to: SLF4J → Logback → MDC_CONSOLE wrapper → CONSOLE / OTEL appender
      via: "LOG.info call inside INTERNAL span (OrderService.place's INTERNAL span is active here — same trace_id as the PRODUCER span via Phase 3 TracingMessagePostProcessor propagation; the PRODUCER span opens only when convertAndSend enters the post-processor chain, AFTER this LOG.info)"
      pattern: "LOG.info\\("
---

<objective>
Add two SLF4J-based business log statements to producer-service: one `LOG.info(...)` near the entry of `OrderController.create(@RequestBody Map<String, Object> payload)` (inside the SERVER span — Phase 2 HttpServerSpanFilter wraps every non-actuator request), and one `LOG.info(...)` inside `OrderPublisher.publish(orderId, payload)` JUST BEFORE the `rabbitTemplate.convertAndSend(...)` call (inside the INTERNAL span of `OrderService.place(...)` — same trace_id as the PRODUCER span via Phase 3 TracingMessagePostProcessor propagation; the PRODUCER span itself only opens when `convertAndSend` enters the post-processor chain, AFTER this LOG.info — see threat-model row T-05-04-05 and the action body of Task 2 for the full span-context discussion). Both use the established SLF4J pattern from `OrderListener` (per PATTERNS §S-1): `private static final Logger LOG = LoggerFactory.getLogger(<Class>.class)`, with `LoggerFactory.getLogger(<This>.class)` and `{}` placeholders.

Purpose: Phase 5 D-15 — give the workshop a stable Loki-query target on the producer side. Both log lines fire INSIDE active spans, so the OTEL appender (declared in Plan 05-02's `logback-spring.xml`) picks up the active `Span.current()` and emits OTLP `LogRecord` with `trace_id`/`span_id` matching the SERVER span (controller) and the INTERNAL span of `OrderService.place(...)` (publisher's pre-`convertAndSend` position) respectively — both share the same `trace_id` as the downstream PRODUCER span via Phase 3 propagation. The MDC_CONSOLE wrapper stamps the same trace_id/span_id into the terminal output via the D-11 console pattern.

Output: 2 files modified — each gains 2 imports (`org.slf4j.Logger` / `org.slf4j.LoggerFactory`), 1 static final Logger field, 1 LOG.info call. Producer compiles cleanly.

Why these two log lines: The first (controller) is the FIRST line of every trace — workshop attendees see it in console immediately on `POST /orders`. The second (publisher) marks the boundary just before the AMQP hop — useful for the Loki-vs-Tempo correlation lesson because the same orderId appears in both producer and consumer log streams.

Note on CONTEXT.md correction: D-15 mentions `OrderController.handle(payload)` — the actual method name is `create(@RequestBody Map<String, Object> payload)` per the existing source code (read first). Use `create`.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/05-logs-correlation/05-CONTEXT.md
@.planning/phases/05-logs-correlation/05-RESEARCH.md
@.planning/phases/05-logs-correlation/05-PATTERNS.md
@.planning/phases/05-logs-correlation/05-02-SUMMARY.md
@producer-service/src/main/java/com/example/producer/api/OrderController.java
@producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
@producer-service/src/main/java/com/example/producer/config/RabbitConfig.java
@consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java

<interfaces>
<!-- Existing analog: OrderListener.java already has the SLF4J Logger field + LOG.info shape. -->
<!-- See PATTERNS §D + §S-1 for full detail. -->

OrderListener.java (consumer-side, Phase 1) — the pattern source:
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

Producer files to modify:

**OrderController.java (current state):**
```java
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
        String orderId = orderService.place(payload);
        // 202 Accepted: order accepted for async processing via AMQP.
        return ResponseEntity.accepted().body(Map.of("orderId", orderId));
    }
}
```
Method name is `create`, NOT `handle` (CONTEXT.md typo). Add LOG.info BEFORE `String orderId = orderService.place(payload);`.

**OrderPublisher.java (current state):**
```java
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
Add LOG.info BETWEEN `message.put("orderId", orderId);` and `rabbitTemplate.convertAndSend(...)` — inside the INTERNAL span of `OrderService.place(...)`. The PRODUCER span only opens once `convertAndSend` enters the post-processor chain (AFTER this LOG.info), so the active span here is the caller's INTERNAL — same trace_id as the PRODUCER span via Phase 3 propagation.

RabbitConfig.EXCHANGE is the constant the publisher uses; reference it in the log message for "publishing to which exchange" visibility.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Add SLF4J Logger field + LOG.info to OrderController.create()</name>
  <files>producer-service/src/main/java/com/example/producer/api/OrderController.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/api/OrderController.java (full file, current state — note method is `create` not `handle`)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (the SLF4J pattern source — lines 5-6 imports, line 41 field declaration, line 51 LOG.info call)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-15 producer-side log lines; planner discretion on wording)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§D — analog from OrderListener; §S-1 — SLF4J Logger field shape)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §F — illustrative shape; §Risk #7 — Span.current() valid inside the SERVER span set by Phase 2's HttpServerSpanFilter)
  </read_first>
  <action>
Edit `producer-service/src/main/java/com/example/producer/api/OrderController.java`. Two changes:

**EDIT 1 — Add SLF4J imports.** The current file has only 4 imports (lines 3-7):
```java
import com.example.producer.domain.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
```

Add the two SLF4J imports. Insert in alphabetical order: `org.slf4j.*` falls between `com.example.*` and `org.springframework.*`. The expected resulting import block:
```java
import com.example.producer.domain.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
```

**EDIT 2 — Add Logger field + LOG.info call.**

Inside the `OrderController` class body, add the Logger field as the FIRST member (BEFORE the existing `private final OrderService orderService;` field at line 12). Match the exact field declaration shape from OrderListener line 41:
```java
    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
```

Then add a LOG.info call as the FIRST statement inside `create(@RequestBody Map<String, Object> payload)` (currently the method body has `String orderId = orderService.place(payload);` at line 20). The new first line:
```java
        LOG.info("received POST /orders payload={}", payload);
```

**Final expected method body shape:**
```java
    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
        LOG.info("received POST /orders payload={}", payload);
        String orderId = orderService.place(payload);
        // 202 Accepted: order accepted for async processing via AMQP.
        return ResponseEntity.accepted().body(Map.of("orderId", orderId));
    }
```

**On the security threat T-05-04-01 (logging full payload):** the `payload` parameter is a `Map<String, Object>` of order fields (workshop synthetic data — fields like `orderId`, `priority`, possibly `items`). For the workshop demo this is acceptable per CONTEXT.md `<security_threat_model>` ("low-value target, synthetic data"). However the threat model below flags this as a production-deployment concern; if a workshop attendee asks "should I log the whole payload in production?" the answer is "no — log a sanitized projection". This is an acceptable workshop teaching surface (`payload={}` IS the trace-data demonstration; production teams will rightly trim it).

DO NOT:
- Use `@Slf4j` Lombok annotation (D-15: consistent with OrderListener — Phase 1 set the no-Lombok precedent)
- Use lowercase `log` field name (must be uppercase `LOG` — matches OrderListener line 41)
- Use string concatenation in the LOG.info (must use `{}` placeholders — SLF4J idiom from OrderListener line 51)
- Place the LOG.info AFTER `orderService.place(payload)` — must be BEFORE so it logs the receipt-of-request (workshop attendees see the trace begin with this log line)
- Log secrets — payload may contain a `customerEmail` field in some test scenarios; for the workshop's synthetic data this is benign, but document this in the threat-model below
- Add a JavaDoc comment block to the new method body line — keeps the class minimal (matches OrderListener's brevity at line 51)
- Rename the `create` method to `handle` (CONTEXT.md D-15 typo'd this — actual method is `create`, keep it)
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'import org.slf4j.Logger;' producer-service/src/main/java/com/example/producer/api/OrderController.java && grep -q 'import org.slf4j.LoggerFactory;' producer-service/src/main/java/com/example/producer/api/OrderController.java && grep -q 'private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);' producer-service/src/main/java/com/example/producer/api/OrderController.java && grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/api/OrderController.java && awk '/public ResponseEntity.*create\(/,/^    }$/' producer-service/src/main/java/com/example/producer/api/OrderController.java | grep -q 'LOG.info(' && awk '/public ResponseEntity.*create\(/,/^    }$/' producer-service/src/main/java/com/example/producer/api/OrderController.java | awk '/LOG.info/{found=1; next} found && /orderService.place/{print \"OK\"; exit 0} END{exit !found}' && ! grep -q '@Slf4j' producer-service/src/main/java/com/example/producer/api/OrderController.java && mvn -B -pl producer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q 'import org.slf4j.Logger;' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0
    - `grep -q 'import org.slf4j.LoggerFactory;' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0
    - `grep -q 'private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0 (exact field shape)
    - `grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0 (LOG.info call exists)
    - LOG.info is INSIDE the create() method: `awk '/public ResponseEntity.*create\(/,/^    }$/' producer-service/src/main/java/com/example/producer/api/OrderController.java | grep -q 'LOG.info('`
    - LOG.info appears BEFORE `orderService.place`: an awk script that matches LOG.info first then orderService.place succeeds
    - NO `@Slf4j` annotation: `! grep -q '@Slf4j' producer-service/src/main/java/com/example/producer/api/OrderController.java`
    - NO lowercase `log` field: `! grep -E 'private (static )?final (org\.slf4j\.)?Logger log[^A-Za-z_]' producer-service/src/main/java/com/example/producer/api/OrderController.java`
    - NO string concatenation in LOG.info: `! grep -E 'LOG\.info\("[^"]*\" \+' producer-service/src/main/java/com/example/producer/api/OrderController.java` (must use `{}` placeholder pattern)
    - Method name is still `create` (D-15 / PATTERNS correction): `grep -q 'public ResponseEntity<Map<String, String>> create' producer-service/src/main/java/com/example/producer/api/OrderController.java`
    - Producer compiles: `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile` exits 0
  </acceptance_criteria>
  <done>
OrderController.java has Logger field and LOG.info call inside create(). The new field is private static final Logger LOG. The new LOG.info uses `{}` placeholders, fires inside the SERVER span (Phase 2 HttpServerSpanFilter wraps every non-/actuator request). Producer compiles cleanly.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Add SLF4J Logger field + LOG.info to OrderPublisher.publish() (before convertAndSend)</name>
  <files>producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java</files>
  <read_first>
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java (full file, current state)
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (verify EXCHANGE constant exists — should be `RabbitConfig.EXCHANGE` based on producer's existing convertAndSend call at line 42)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (the SLF4J pattern source — same as Task 1)
    - .planning/phases/05-logs-correlation/05-CONTEXT.md (D-15 producer-side log lines part 2)
    - .planning/phases/05-logs-correlation/05-PATTERNS.md (§D — analog; §S-1 — SLF4J Logger field shape)
    - .planning/phases/05-logs-correlation/05-RESEARCH.md (§Code Excerpts §F — illustrative shape)
  </read_first>
  <action>
Edit `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`. Two changes:

**EDIT 1 — Add SLF4J imports.** The current file has 5 imports (lines 1-9):
```java
import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.producer.config.RabbitConfig;
```

Add `org.slf4j.Logger` + `org.slf4j.LoggerFactory`. The current file separates `java.*` from `org.springframework.*` from `com.example.*` with blank lines. Add `org.slf4j.*` in its own group (alphabetically: `org.slf4j` < `org.springframework`), so the imports become:
```java
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.producer.config.RabbitConfig;
```

**EDIT 2 — Add Logger field + LOG.info call.**

Inside the `OrderPublisher` class body, add the Logger field as the FIRST member (BEFORE the existing `private final RabbitTemplate rabbitTemplate;` field at line 33):
```java
    private static final Logger LOG = LoggerFactory.getLogger(OrderPublisher.class);
```

Then add a LOG.info call inside `publish(String orderId, Map<String, Object> payload)` — JUST BEFORE the `rabbitTemplate.convertAndSend(...)` call. Currently the method body is (line 39-43):
```java
    public void publish(String orderId, Map<String, Object> payload) {
        Map<String, Object> message = new HashMap<>(payload);
        message.put("orderId", orderId);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
    }
```

Insert the new LOG.info BETWEEN `message.put("orderId", orderId);` and `rabbitTemplate.convertAndSend(...)`. New body:
```java
    public void publish(String orderId, Map<String, Object> payload) {
        Map<String, Object> message = new HashMap<>(payload);
        message.put("orderId", orderId);
        LOG.info("publishing orderId={} to exchange={}", orderId, RabbitConfig.EXCHANGE);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
    }
```

**Why this position is exact:** the Phase 3 `TracingMessagePostProcessor` (registered on `RabbitTemplate.setBeforePublishPostProcessors(...)` per the JavaDoc on the existing OrderPublisher class lines 11-30) opens the PRODUCER span when `convertAndSend` enters the post-processor chain. Placing the LOG.info BEFORE `convertAndSend` puts it OUTSIDE the PRODUCER span — the active span at the time of the LOG.info is the INTERNAL span of `OrderService.place(...)` (Phase 2 plan 02-04). That's still valid for trace correlation (same trace_id). For the workshop's "publishing event" semantics this is more pedagogically correct than logging from inside the PostProcessor — the application-level publish event is what the workshop attendee thinks of as "the publish".

(Alternative: place the LOG.info AFTER convertAndSend — would log AFTER the PRODUCER span has ended. Active span at that point reverts back to INTERNAL. Same trace_id, slightly different teaching shape. Use the BEFORE position per the CONTEXT.md D-15 wording "just before the AMQP publish call".)

DO NOT:
- Use `@Slf4j` Lombok annotation
- Use lowercase `log` field name
- Use string concatenation in the LOG.info (must use `{}` placeholders for both arguments)
- Place the LOG.info AFTER `rabbitTemplate.convertAndSend(...)` (D-15 says "just before" the publish call)
- Hardcode the exchange string `"orders"` in the log message — must reference `RabbitConfig.EXCHANGE` (matches the actual exchange used in convertAndSend; if RabbitConfig is later refactored, the log stays correct)
- Log the full `payload` map (the controller already does that — duplicate log is noise; here we log the routing identity: orderId + exchange name)
- Modify the existing JavaDoc class-level block (lines 11-30) — out of scope
- Rename `publish` or change its signature
  </action>
  <verify>
    <automated>cd $(git rev-parse --show-toplevel) && grep -q 'import org.slf4j.Logger;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && grep -q 'import org.slf4j.LoggerFactory;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && grep -q 'private static final Logger LOG = LoggerFactory.getLogger(OrderPublisher.class);' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && awk '/public void publish\(/,/^    }$/' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java | awk '/LOG.info\(/{found=1; next} found && /rabbitTemplate.convertAndSend/{print \"OK\"; exit 0} END{exit !found}' && grep -q 'RabbitConfig.EXCHANGE' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && ! grep -q '@Slf4j' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java && mvn -B -pl producer-service -am compile</automated>
  </verify>
  <acceptance_criteria>
    - `grep -q 'import org.slf4j.Logger;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - `grep -q 'import org.slf4j.LoggerFactory;' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - `grep -q 'private static final Logger LOG = LoggerFactory.getLogger(OrderPublisher.class);' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - `grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
    - LOG.info appears BEFORE rabbitTemplate.convertAndSend (in source order): the awk script that matches LOG.info first then convertAndSend succeeds
    - LOG.info references the exchange constant (not a hardcoded string): `grep -q 'RabbitConfig.EXCHANGE' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0 (this constant is referenced in BOTH the LOG.info AND the convertAndSend — which is fine; the count must be ≥ 2)
    - Count check: `grep -c 'RabbitConfig.EXCHANGE' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` >= 2
    - NO `@Slf4j` annotation
    - NO string concatenation in LOG.info: `! grep -E 'LOG\.info\("[^"]*\" \+' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`
    - NO logging of full payload (D-15 — would be redundant with controller log): `! grep -E 'LOG\.info\(.*payload[,)]' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`
    - Producer compiles: `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile` exits 0
  </acceptance_criteria>
  <done>
OrderPublisher.java has Logger field and LOG.info call inside publish() before convertAndSend. The log references RabbitConfig.EXCHANGE (not hardcoded). Producer compiles cleanly.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| HTTP request → application code | `payload` parameter on `OrderController.create` is untrusted input from the HTTP client. Passes through Spring MVC's request body deserialization (Jackson) to a `Map<String, Object>`. |
| application log call → Logback | `LOG.info(..., payload)` toString()s the Map for the `{}` placeholder. Logback writes the rendered string to MDC_CONSOLE (terminal) and OTEL (OTLP). |

## STRIDE Threat Register (ASVS L1, security_enforcement: enabled, block-on: high)

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-04-01 | Information Disclosure | OrderController LOG.info logs full payload | accept (workshop) / mitigate (production callout) | The workshop demo uses synthetic POSTs with non-sensitive fields (`priority`, `items`). Logging the full payload is acceptable for the workshop and IS the demo (workshop attendees see exactly what flowed in). For production: a real team would log a sanitized projection (`payload.keySet()` or specific safe fields). The class JavaDoc / README is the natural place to call this out — Plan 05-06 README task can include a one-line "in production, log a sanitized projection" callout. The workshop's `mise run demo:order` task sends only synthetic data, so this is bounded. |
| T-05-04-02 | Information Disclosure | OrderPublisher LOG.info logs orderId + exchange | accept | `orderId` is a workshop-generated UUID (not PII); `RabbitConfig.EXCHANGE` is a routing constant. No sensitive data. |
| T-05-04-03 | Tampering | LOG.info argument injection (CRLF) | accept | `payload.toString()` could contain newlines if the attacker controls field values, but Logback's PatternLayout escapes nothing — a CRLF would split the log line. For the workshop running on a developer laptop, log injection is not a real attack vector (no log-aggregation that would parse split lines as separate events). For production: defense in depth = JSON-encode log messages instead of plain pattern, or sanitize payload fields. Out of scope for v1. |
| T-05-04-04 | Denial of Service | LOG.info argument formatting cost | accept | `payload.toString()` on a Map is O(n) where n = number of fields. Workshop payloads are <10 fields. SLF4J's `{}` placeholder defers `toString()` until the appender renders the message — at INFO level, both appenders DO render, so the cost is paid every call. Acceptable. |
| T-05-04-05 | Repudiation | LOG.info trace_id correlation | mitigate | Both LOG.info calls fire INSIDE active spans (SERVER for controller, INTERNAL for publisher's pre-publish position). The OTEL appender reads `Span.current()` at log time, so the OTLP LogRecord carries the correct trace_id. The MDC_CONSOLE wrapper stamps the same trace_id into MDC for terminal output. Verified by Plan 05-06 smoke test. |
</threat_model>

<verification>
- `grep -q 'import org.slf4j.Logger' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0
- `grep -q 'import org.slf4j.LoggerFactory' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0
- `grep -q 'private static final Logger LOG' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0
- `grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 0
- `grep -q 'import org.slf4j.Logger' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
- `grep -q 'import org.slf4j.LoggerFactory' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
- `grep -q 'private static final Logger LOG' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
- `grep -q 'LOG.info(' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 0
- LOG.info in OrderPublisher appears BEFORE rabbitTemplate.convertAndSend in source order (awk script verifies)
- LOG.info in OrderController appears BEFORE orderService.place in source order
- NO @Slf4j annotation in either file
- Method name in OrderController is `create` (NOT `handle`)
- `cd $(git rev-parse --show-toplevel) && mvn -B -pl producer-service -am compile` exits 0
</verification>

<success_criteria>
1. OrderController.java has SLF4J imports, Logger field, and LOG.info call inside create() that fires BEFORE orderService.place(...).
2. OrderPublisher.java has SLF4J imports, Logger field, and LOG.info call inside publish() that fires BEFORE rabbitTemplate.convertAndSend(...).
3. Both files use the canonical SLF4J pattern (private static final Logger LOG, `{}` placeholders, no Lombok).
4. The OrderController's `create` method name is preserved (D-15 typo'd it as `handle` in CONTEXT.md — actual is `create`).
5. The OrderPublisher LOG.info references `RabbitConfig.EXCHANGE` (not a hardcoded string).
6. Producer compiles: `mvn -pl producer-service -am compile` exits 0.
7. No log line logs secrets / PII (workshop synthetic data only).
</success_criteria>

<output>
After completion, create `.planning/phases/05-logs-correlation/05-04-SUMMARY.md` with:
- Files modified (2 files)
- The exact diff for each file
- Confirmation that LOG.info calls are positioned correctly (BEFORE the downstream call in each method)
- `mvn -pl producer-service -am compile` exit code (0)
- Pedagogical note for Plan 05-06 README task: producer LOG.info lines fire inside SERVER and INTERNAL spans, both stamped with the same trace_id; consumer side will gain a LOG.error line in Plan 05-05 inside the CONSUMER → INTERNAL span chain
- Forward-link: Plan 05-05 adds the consumer-side LOG.error; Plan 05-06 smoke-tests the entire pipeline + applies the exit tag
</output>
