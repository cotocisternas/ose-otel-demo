---
phase: 05-logs-correlation
plan: 04
subsystem: producer-service
tags:
  - slf4j
  - logging
  - producer
  - business-code
  - logs-correlation
dependency-graph:
  requires:
    - 05-02
  provides:
    - producer-side LOG.info hooks emitted inside SERVER and INTERNAL spans
    - stable Loki/Grafana query target for the producer service (`logger=com.example.producer.api.OrderController`, `logger=com.example.producer.messaging.OrderPublisher`)
  affects:
    - producer-service application logs (console + OTLP export via the Plan 05-02 logback-spring.xml chain)
tech-stack:
  added: []
  patterns:
    - "PATTERNS §S-1 / §D: private static final Logger LOG = LoggerFactory.getLogger(<Class>.class) — no Lombok, uppercase LOG, `{}` placeholders"
    - "D-15: producer business log lines (entry of OrderController.create + pre-publish in OrderPublisher.publish)"
    - "D-15 typo correction: method name is `create`, not `handle` (CONTEXT.md said `handle`; actual source is `create`)"
key-files:
  created: []
  modified:
    - producer-service/src/main/java/com/example/producer/api/OrderController.java
    - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
decisions:
  - "Logger field placed as the FIRST class member (before existing `private final OrderService` / `RabbitTemplate` field). Mirrors OrderListener (Plan 05-? Phase 1) line 41."
  - "OrderPublisher.LOG.info placed BETWEEN `message.put(...)` and `rabbitTemplate.convertAndSend(...)` — the application-level publish event. At this point the active span is OrderService.place's INTERNAL span (Phase 2); the PRODUCER span only opens once convertAndSend enters TracingMessagePostProcessor (Phase 3). Same trace_id either way (Phase 3 propagation), per threat-model T-05-04-05."
  - "Exchange name in the publisher LOG.info references RabbitConfig.EXCHANGE constant (`grep -c RabbitConfig.EXCHANGE` = 2 in OrderPublisher.java) — not a hardcoded `\"orders\"` string."
  - "OrderController logs full payload (`payload={}`) — accepted for the workshop demo (synthetic data, threat-model T-05-04-01); production callout deferred to Plan 05-06 README."
metrics:
  tasks-completed: 2
  files-modified: 2
  lines-added: 8
  lines-removed: 0
  duration-minutes: ~3
  completed-date: 2026-05-01
---

# Phase 5 Plan 4: Producer Business Logs Summary

Add two SLF4J-based business log statements to producer-service: an entry log inside `OrderController.create()` and a pre-publish log inside `OrderPublisher.publish()`. Both use the canonical `private static final Logger LOG = LoggerFactory.getLogger(<Class>.class)` pattern with `{}` placeholders (no Lombok, no string concatenation), matching the Phase 1 OrderListener precedent. Producer compiles cleanly.

## Outcome

Producer service now has two stable, structured log lines that fire INSIDE active spans, ensuring the OTEL appender (configured in Plan 05-02's `logback-spring.xml`) stamps each emitted OTLP `LogRecord` with the correct `trace_id` / `span_id` from `Span.current()`. The MDC_CONSOLE wrapper stamps the same trace_id into MDC for terminal output (D-11 console pattern). This gives Plan 05-06 a stable, query-able producer log surface for the Loki ↔ Tempo correlation lesson.

## Files Modified

### 1. `producer-service/src/main/java/com/example/producer/api/OrderController.java`

**Diff:**

```diff
 package com.example.producer.api;

 import com.example.producer.domain.OrderService;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
 import org.springframework.http.ResponseEntity;
 import org.springframework.web.bind.annotation.*;

 import java.util.Map;

 @RestController
 @RequestMapping("/orders")
 public class OrderController {
+    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
     private final OrderService orderService;

     public OrderController(OrderService orderService) {
         this.orderService = orderService;
     }

     @PostMapping
     public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
+        LOG.info("received POST /orders payload={}", payload);
         String orderId = orderService.place(payload);
         // 202 Accepted: order accepted for async processing via AMQP.
         return ResponseEntity.accepted().body(Map.of("orderId", orderId));
     }
 }
```

**Span context at LOG.info time:** SERVER span (Phase 2's `HttpServerSpanFilter` wraps every non-`/actuator` request). The OTEL appender reads `Span.current()` and emits an OTLP LogRecord with the SERVER span's `trace_id` / `span_id`.

**Position note:** `LOG.info` is the FIRST statement in `create()` — before `orderService.place(payload)` — so workshop attendees see the trace begin with this log line in console output as soon as `POST /orders` lands.

**Method name:** `create` (NOT `handle`). CONTEXT.md D-15 typo'd this as `handle`; actual source has always been `create`. Plan acceptance criteria explicitly verifies `grep -q 'public ResponseEntity<Map<String, String>> create'` returns 0.

**Commit:** `7ad4274` — `feat(05-04): add SLF4J LOG.info entry log to OrderController.create`

### 2. `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`

**Diff:**

```diff
 package com.example.producer.messaging;

 import java.util.HashMap;
 import java.util.Map;

+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
 import org.springframework.amqp.rabbit.core.RabbitTemplate;
 import org.springframework.stereotype.Component;

 import com.example.producer.config.RabbitConfig;

 /* ... existing class JavaDoc unchanged ... */
 @Component
 public class OrderPublisher {
+    private static final Logger LOG = LoggerFactory.getLogger(OrderPublisher.class);
     private final RabbitTemplate rabbitTemplate;

     public OrderPublisher(RabbitTemplate rabbitTemplate) {
         this.rabbitTemplate = rabbitTemplate;
     }

     public void publish(String orderId, Map<String, Object> payload) {
         Map<String, Object> message = new HashMap<>(payload);
         message.put("orderId", orderId);
+        LOG.info("publishing orderId={} to exchange={}", orderId, RabbitConfig.EXCHANGE);
         rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
     }
 }
```

**Span context at LOG.info time:** the INTERNAL span of `OrderService.place(...)` (Phase 2, plan 02-04). The PRODUCER span only opens once `convertAndSend` enters `TracingMessagePostProcessor` (Phase 3 — registered on `RabbitTemplate.setBeforePublishPostProcessors(...)` in `RabbitConfig.rabbitTemplate`). Both spans share the same `trace_id` via Phase 3 propagation, so workshop attendees see the same trace tying the controller, INTERNAL, PRODUCER, and (downstream) CONSUMER spans together. (Threat-model T-05-04-05 — repudiation mitigation.)

**Position is exact:** the `LOG.info` is BETWEEN `message.put("orderId", orderId);` (line 44) and `rabbitTemplate.convertAndSend(...)` (line 46). This is the application-level "publish event" — pedagogically what an attendee thinks of as "the publish", and the correct moment to log the routing identity (orderId + exchange name).

**No hardcoded exchange:** the LOG.info references `RabbitConfig.EXCHANGE`, identical to the constant used in the very next line's `convertAndSend`. `grep -c 'RabbitConfig.EXCHANGE' OrderPublisher.java` = 2.

**Commit:** `7e4659e` — `feat(05-04): add SLF4J LOG.info pre-publish log to OrderPublisher.publish`

## LOG.info Position Confirmation

The plan's hard requirement is:

> LOG.info call inside OrderPublisher.publish must be JUST BEFORE rabbitTemplate.convertAndSend(...) — verifies that the log statement runs in the INTERNAL span scope (not the not-yet-opened PRODUCER span)

Verified by the awk script in the plan's `<verify>` block (run during execution):

```text
$ awk '/public void publish\(/,/^    }$/' OrderPublisher.java | \
    awk '/LOG.info\(/{found=1; next} found && /rabbitTemplate.convertAndSend/{print "OK"; exit 0} END{exit !found}'
OK
```

Likewise for OrderController:

```text
$ awk '/public ResponseEntity.*create\(/,/^    }$/' OrderController.java | \
    awk '/LOG.info/{found=1; next} found && /orderService.place/{print "OK"; exit 0} END{exit !found}'
OK
```

## Build Verification

`mvn -B -pl producer-service clean compile` exits **0** (BUILD SUCCESS). 7 source files compile cleanly with `javac [debug target 17]`. No new warnings.

## Pedagogical Note for Plan 05-06 README Task

Producer LOG.info lines fire inside two different active span contexts, both stamped with the same trace_id:

| Log line | Source file | Active span at log time | trace_id source |
|---|---|---|---|
| `received POST /orders payload=...` | `OrderController.create` | SERVER (Phase 2 HttpServerSpanFilter) | This trace's root |
| `publishing orderId=... to exchange=...` | `OrderPublisher.publish` (pre-`convertAndSend`) | INTERNAL (Phase 2 OrderService.place) | Inherited from SERVER; PRODUCER span (opened by Phase 3 TracingMessagePostProcessor moments later) shares it via context propagation |

For the workshop's Loki ↔ Tempo correlation lesson, this means: **opening the SERVER span's trace in Tempo and pivoting to Loki (filtered by `service.name=producer-service` and that `trace_id`) reveals exactly TWO producer log lines in the canonical happy path** — one from each line above. The consumer side's matching log line (Plan 05-05's `LOG.error` inside `ProcessingService.process`) lives in the CONSUMER → INTERNAL chain on the same trace.

## Forward Links

- **Plan 05-05 (consumer-side LOG.error):** adds the consumer's intentional-error log line (`ProcessingService.process` → `LOG.error("processing failed", e)`) inside the CONSUMER span (Phase 3 `TracingMessageListenerAdvice`). After 05-05, the entire trace will have THREE business log lines (controller → publisher → processor) all sharing one `trace_id`.
- **Plan 05-06 (smoke test + exit tag):** end-to-end Loki ↔ Tempo correlation walk-through using the log lines wired in 05-04 + 05-05. Applies the `step-05-logs-correlation` git tag.

## Deviations from Plan

None — plan executed exactly as written.

The plan's CONTEXT.md called out one upstream typo (`OrderController.handle` → `create`). That correction was already encoded in PATTERNS §D and the plan's `<action>` body, so it required no deviation handling at execution time — the implementation matched the plan's instructions verbatim.

No auto-fixes applied (Rules 1-3 not triggered). No architectural decisions surfaced (Rule 4 not triggered).

## Threat Surface Scan

The plan's threat-model rows T-05-04-01 through T-05-04-05 were addressed in-line:
- **T-05-04-01** (Information Disclosure: full payload logged in controller): accepted for workshop demo (synthetic data); production callout deferred to Plan 05-06 README.
- **T-05-04-02** (Information Disclosure: orderId + exchange in publisher log): accepted (UUID + routing constant, no PII).
- **T-05-04-03** (CRLF tampering via payload.toString): accepted (workshop runs on developer laptop; no log aggregator that would parse split lines).
- **T-05-04-04** (DoS via formatting cost): accepted (workshop payloads <10 fields).
- **T-05-04-05** (Repudiation: trace_id correlation): mitigated — both LOG.info calls fire INSIDE active spans, OTEL appender reads `Span.current()` correctly. Smoke-tested in Plan 05-06.

No NEW security-relevant surface introduced beyond what the plan's `<threat_model>` covered. No threat flags raised.

## Self-Check: PASSED

**Files exist:**
- `producer-service/src/main/java/com/example/producer/api/OrderController.java` — FOUND
- `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` — FOUND
- `.planning/phases/05-logs-correlation/05-04-SUMMARY.md` — FOUND (this file)

**Commits exist:**
- `7ad4274` (Task 1) — FOUND in `git log --oneline`
- `7e4659e` (Task 2) — FOUND in `git log --oneline`

**Acceptance criteria (re-verified post-commit):**
- All `must_haves.truths` rows verified true: `LOG-04-producer-controller`, `LOG-04-producer-publisher`, `D-15-method-name`, `D-15-publish-position`, `slf4j-imports`, `no-lombok`, `build-clean`.
- All `<verification>` block grep checks pass.
- `mvn -B -pl producer-service clean compile` exits 0.
