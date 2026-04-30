---
id: 1-05-consumer-service
phase: 01-baseline-scaffold
plan: 05
type: execute
wave: 2
depends_on: [1-01-maven-skeleton, 1-02-mise-toolchain, 1-03-docker-compose]
requirements: [APP-03, APP-05]
files_modified:
  - consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java
  - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
  - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java
  - consumer-service/src/main/resources/application.yaml
autonomous: true
must_haves:
  truths:
    - "ConsumerApplication starts on port 8081 (set via CONSUMER_PORT env var) and logs a 'Started ConsumerApplication' line"
    - "An @RabbitListener on queue 'orders.created' receives every message published to the 'orders' direct exchange with routing key 'order.created'"
    - "Each received message logs an 'OrderCreated received: orderId=<uuid>' line at INFO level via the standard Logback configuration (visible to mise run dev tail)"
    - "ProcessingService.process(...) is called for every received message — the no-op method body marks the future Phase-2 INTERNAL span site (TRACE-06) and Phase-3 deterministic-failure site (APP-04)"
    - "GET /actuator/health on port 8081 returns 200 + {\"status\":\"UP\"} (APP-05)"
    - "application.yaml is minimal: spring.application.name=order-consumer, management.endpoints.web.exposure.include=health, NO OTel-related properties (Pitfall F)"
    - "mvn dependency:tree -pl consumer-service -Dincludes=io.opentelemetry returns zero matches (Phase 1 baseline preserved)"
  artifacts:
    - path: "consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java"
      provides: "@SpringBootApplication main class"
      contains: "@SpringBootApplication"
    - path: "consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java"
      provides: "@RabbitListener consuming orders.created and logging OrderCreated receipt (APP-03)"
      contains: "@RabbitListener"
    - path: "consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java"
      provides: "Domain service simulating downstream work (in-memory; no real side-effects in Phase 1)"
      contains: "public void process"
    - path: "consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java"
      provides: "Queue declaration (idempotent — same name as producer's queue) + Jackson2JsonMessageConverter"
      contains: "Queue ordersCreatedQueue"
    - path: "consumer-service/src/main/resources/application.yaml"
      provides: "Minimal Spring Boot config: app name + actuator health exposure"
      contains: "name: order-consumer"
  key_links:
    - from: "consumer-service/.../messaging/OrderListener.java"
      to: "RabbitMQ queue 'orders.created'"
      via: "@RabbitListener(queues = RabbitConfig.QUEUE)"
      pattern: "@RabbitListener\\(queues = RabbitConfig.QUEUE\\)"
    - from: "consumer-service/.../messaging/OrderListener.java"
      to: "consumer-service/.../domain/ProcessingService.java"
      via: "constructor injection + processingService.process(message)"
      pattern: "processingService.process"
    - from: "consumer-service/.../config/RabbitConfig.java"
      to: "RabbitMQ broker (docker-compose)"
      via: "Spring Boot autoconfigured ConnectionFactory from SPRING_RABBITMQ_* env vars (set in mise.toml [env] from plan 02)"
      pattern: "Queue ordersCreatedQueue"
---

<objective>
Build the consumer-service skeleton: a Spring Boot 3.4.13 app with an `@RabbitListener` on queue `orders.created` that logs each received message and delegates to `ProcessingService.process(...)`. Exposes `/actuator/health` on port 8081. Zero OTel libraries, zero OTel config — the service is intentionally uninstrumented in Phase 1; Phase 2 wires `OtelSdkConfiguration.java` and Phase 3 adds the deterministic-10%-failure path inside `ProcessingService.process` (APP-04 + TRACE-09).

Purpose: APP-03 (consumer @RabbitListener simulates downstream work), APP-05 (actuator/health on consumer). Sets up the CONSUMER-span site (`OrderListener.onOrder`) and INTERNAL-span site (`ProcessingService.process`) that Phase 2 will instrument.
Output: 4 Java files + 1 application.yaml. After `mise run dev:consumer`, the app starts on port 8081 and logs `OrderCreated received: orderId=...` for every message published by the producer.
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
@.planning/phases/01-baseline-scaffold/1-RESEARCH.md
@.planning/phases/01-baseline-scaffold/1-01-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-02-SUMMARY.md
@.planning/phases/01-baseline-scaffold/1-03-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="1-05-T1" type="auto">
  <name>Task 1: Write ConsumerApplication + RabbitConfig (Spring Boot bootstrap + queue declaration)</name>
  <files>consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java, consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java</files>
  <read_first>
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 1004-1041 — ConsumerApplication.java + consumer RabbitConfig.java verified skeletons)
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 304-306 — Don't Hand-Roll: ConnectionFactory autoconfigured; do not declare a manual @Bean ConnectionFactory)
    - consumer-service/pom.xml (created in plan 01 — confirms starter-amqp/actuator/web/test coordinates)
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (created in plan 04 — for QUEUE name parity; both sides must agree on `orders.created`)
  </read_first>
  <action>
    Create two Java files under `consumer-service/src/main/java/com/example/consumer/`.

    **`ConsumerApplication.java`** — verbatim from RESEARCH.md lines 1006-1018:
    - Package: `com.example.consumer`
    - Imports: `org.springframework.boot.SpringApplication`, `org.springframework.boot.autoconfigure.SpringBootApplication`
    - Class annotated `@SpringBootApplication`
    - `public static void main(String[] args)` calling `SpringApplication.run(ConsumerApplication.class, args)`

    NO custom env-binding beans, NO `@EnableRabbit` (Spring Boot's `RabbitAutoConfiguration` covers it).

    **`config/RabbitConfig.java`** — verbatim from RESEARCH.md lines 1022-1041:
    - Package: `com.example.consumer.config`
    - `@Configuration`-annotated class.
    - One `public static final String` constant: `QUEUE = "orders.created"` — MUST match producer's `RabbitConfig.QUEUE` exactly. The two services agree on the queue name; they don't share a Maven module yet (otel-bootstrap is empty in Phase 1; even later, the propagation pair lives there, not the constants).
    - Two `@Bean`s:
      - `Queue ordersCreatedQueue()` returning `new Queue(QUEUE, true)` — durable=true. Idempotent: declaring the same queue from two services is fine; Spring AMQP/RabbitMQ ignores the second declaration if it matches the first.
      - `MessageConverter jsonMessageConverter()` returning `new Jackson2JsonMessageConverter()` — must match the producer's converter so JSON round-trips correctly.

    **DO NOT declare** the `DirectExchange` bean or the `Binding` bean — the consumer side does not own the topology. The producer's `RabbitConfig` owns `orders` exchange + binding; the consumer just declares the queue it listens to. (RabbitMQ accepts both ends declaring the queue idempotently.)

    **DO NOT declare** a `@Bean ConnectionFactory` (autoconfigured from `SPRING_RABBITMQ_*` env vars).

    Required imports for RabbitConfig:
    - `org.springframework.amqp.core.Queue`
    - `org.springframework.amqp.support.converter.{Jackson2JsonMessageConverter, MessageConverter}`
    - `org.springframework.context.annotation.{Bean, Configuration}`
  </action>
  <acceptance_criteria>
    - `test -f consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java` exits 0
    - `test -f consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0
    - `grep -c '@SpringBootApplication' consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java` returns 1
    - `grep -c 'package com.example.consumer;' consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java` returns 1
    - `grep -c 'public static final String QUEUE = "orders.created";' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` returns 1
    - Queue name parity with producer: `grep 'QUEUE = "orders.created"' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java | wc -l` returns 2
    - `grep -c '@Bean' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` returns 2 (queue + converter — NOT exchange or binding)
    - `! grep -c 'DirectExchange' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0 (consumer does NOT declare the exchange — producer owns the topology)
    - `! grep -c 'ConnectionFactory' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java` exits 0 (no manual ConnectionFactory bean)
    - `mvn -pl consumer-service compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl consumer-service -q compile &amp;&amp; grep -q '@SpringBootApplication' consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java &amp;&amp; grep -q 'new Queue(QUEUE, true)' consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java</automated>
  </verify>
  <done>ConsumerApplication compiles and is annotated @SpringBootApplication; RabbitConfig declares only the queue + converter beans (no exchange/binding ownership on consumer side); QUEUE constant matches producer's exactly.</done>
</task>

<task id="1-05-T2" type="auto">
  <name>Task 2: Write OrderListener + ProcessingService + application.yaml (consume + simulate)</name>
  <files>consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java, consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java, consumer-service/src/main/resources/application.yaml</files>
  <read_first>
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 1043-1092 — OrderListener.java + ProcessingService.java verified skeletons)
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 1094-1106 — consumer application.yaml minimal content)
    - .planning/phases/01-baseline-scaffold/1-RESEARCH.md (lines 383-388 — Pitfall F: don't add OTel properties to application.yaml)
    - consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java (just created — `RabbitConfig.QUEUE` is referenced by the listener)
    - .planning/ROADMAP.md (Phase 3 scope — APP-04 deterministic-10%-failure lands later; ProcessingService.process must be a no-op site in Phase 1)
  </read_first>
  <action>
    Create two Java files plus one resources file.

    **`messaging/OrderListener.java`** — verbatim from RESEARCH.md lines 1045-1073:
    - Package: `com.example.consumer.messaging`
    - Imports:
      - `com.example.consumer.config.RabbitConfig` (for `RabbitConfig.QUEUE`)
      - `com.example.consumer.domain.ProcessingService`
      - `org.slf4j.{Logger, LoggerFactory}`
      - `org.springframework.amqp.rabbit.annotation.RabbitListener`
      - `org.springframework.stereotype.Component`
      - `java.util.Map`
    - `@Component`-annotated class
    - Static `Logger LOG = LoggerFactory.getLogger(OrderListener.class);`
    - Constructor-injects `ProcessingService processingService`
    - One method `public void onOrder(Map<String, Object> message)`:
      - Annotated `@RabbitListener(queues = RabbitConfig.QUEUE)`
      - Body:
        - `Object orderId = message.get("orderId");`
        - `LOG.info("OrderCreated received: orderId={}", orderId);` — exact string format from RESEARCH; the success-criterion check greps for "OrderCreated received: orderId=" so the string must match.
        - `processingService.process(message);`

    Add an inline comment above `@RabbitListener`: `// APP-03: receive OrderCreated and simulate downstream domain work.`

    **`domain/ProcessingService.java`** — verbatim from RESEARCH.md lines 1077-1091:
    - Package: `com.example.consumer.domain`
    - `@Service`-annotated class
    - One method `public void process(Map<String, Object> order)` with an EMPTY body containing two comment lines:
      - `// Phase 1: simulated domain work, in-memory only.`
      - `// Phase 3 wires up the deterministic 10% failure path (APP-04).`

    Empty body is intentional — Phase 2 wraps this in an INTERNAL span (TRACE-06), Phase 3 adds the modulo-10 failure (APP-04). Phase 1 just needs the call site to exist.

    **`src/main/resources/application.yaml`** — verbatim from RESEARCH.md lines 1096-1106:
    ```yaml
    spring:
      application:
        name: order-consumer
    management:
      endpoints:
        web:
          exposure:
            include: health
    ```

    Same Pitfall-F discipline as producer: NO `management.tracing.*`, NO `otel.*`, NO `logging.pattern.*`, NO `server.port` (`-Dserver.port=${CONSUMER_PORT}` flows from `mise run dev:consumer`). The actuator exposure is whitelisted to `health` only — no `/actuator/env` leakage.
  </action>
  <acceptance_criteria>
    - `test -f consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` exits 0
    - `test -f consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` exits 0
    - `test -f consumer-service/src/main/resources/application.yaml` exits 0
    - `grep -c '@RabbitListener(queues = RabbitConfig.QUEUE)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - `grep -c 'OrderCreated received: orderId={}' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1 (exact log format)
    - `grep -c 'processingService.process(message);' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java` returns 1
    - `grep -c '@Service' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - `grep -c 'public void process(Map<String, Object> order)' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java` returns 1
    - ProcessingService.process body is empty (no statements, only comments): `awk '/public void process/,/^    }/' consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java | grep -vE '^\s*(//|public void|\}|\s*$)' | wc -l | awk '{exit ($1==0)?0:1}'` exits 0
    - `grep -c 'name: order-consumer' consumer-service/src/main/resources/application.yaml` returns 1
    - `grep -c 'include: health' consumer-service/src/main/resources/application.yaml` returns 1
    - `! grep -E '(otel|opentelemetry|tracing)' consumer-service/src/main/resources/application.yaml` exits 0 (Pitfall F: no OTel properties)
    - `mvn -pl consumer-service -DskipTests package` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl consumer-service -q -DskipTests package &amp;&amp; grep -q '@RabbitListener(queues = RabbitConfig.QUEUE)' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; grep -q 'OrderCreated received: orderId={}' consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java &amp;&amp; ! grep -E 'otel|opentelemetry' consumer-service/src/main/resources/application.yaml</automated>
  </verify>
  <done>OrderListener compiles, declares @RabbitListener on RabbitConfig.QUEUE, logs the exact OrderCreated string, calls processingService.process(message); ProcessingService.process is an empty no-op marked with Phase-2/3 future-work comments; application.yaml is minimal with no OTel properties; full Maven package succeeds.</done>
</task>

<task id="1-05-T3" type="auto">
  <name>Task 3: End-to-end smoke test — POST → consume → log; consumer actuator/health</name>
  <files>(none — verification only)</files>
  <read_first>
    - mise.toml (created in plan 02 — has dev:consumer, dev, demo:order tasks; CONSUMER_PORT=8081)
    - docker-compose.yml (created in plan 03)
    - producer-service/* (created in plan 04 — must be running for the consumer to receive a message)
    - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java (just created — log format)
  </read_first>
  <action>
    Start RabbitMQ + lgtm + producer + consumer, send a POST, verify the consumer logs an OrderCreated receipt and that /actuator/health on the consumer responds. This task creates no files — it proves T1+T2 ship a working APP-03/05 surface AND that the producer-and-consumer end-to-end shape works.

    **Step 1 — Ensure infra is up:**
    `mise run infra:up` (idempotent).

    **Step 2 — Start BOTH apps:**
    Use `mise run dev` (which the parallel `{ tasks = ["dev:producer", "dev:consumer"] }` syntax launches both simultaneously) in the background. Capture the parent PID.

    Acceptable launch idiom:
    `mise run dev > /tmp/dev.log 2>&amp;1 &amp; PARENT_PID=$!`
    Then poll `/tmp/dev.log` until BOTH `Started ProducerApplication` AND `Started ConsumerApplication` appear (up to 90s — both apps cold-start in parallel; the slower one bounds the total).

    Alternatively, executor MAY run `dev:producer` and `dev:consumer` separately in two background shells — the goal is just to have both apps up.

    **Step 3 — Send a POST and watch for the consumer log line:**
    `curl -sf -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}'`
    The response body has `{"orderId":"<UUID>"}` — extract the orderId.
    Then poll `/tmp/dev.log` for a line matching `OrderCreated received: orderId=<the_extracted_uuid>` within 30 seconds. Exact-string match: the log line MUST contain both the literal `OrderCreated received: orderId=` AND the same UUID returned in the POST response.

    **Step 4 — Verify consumer actuator/health:**
    - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health` MUST output `200`.
    - `curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'` MUST exit 0.

    **Step 5 — Verify Phase 1 baseline preserved on consumer:**
    `mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry` MUST output zero `io.opentelemetry:*` artifact lines.

    **Step 6 — Verify queue depth is zero (consumer drained the message):**
    `curl -s -u guest:guest "http://localhost:15672/api/queues/%2F/orders.created" | python3 -c "import json,sys; d=json.load(sys.stdin); m=d.get('messages',-1); print(m); exit(0 if m==0 else 1)"` — should output `0` (no backlog; consumer kept up).

    **Step 7 — Cleanup:**
    `kill $PARENT_PID` (and any leftover dev:producer / dev:consumer child processes — `pkill -f "spring-boot:run"` is acceptable). Optionally `mise run infra:down`. Leave the system clean for plan 06.

    Common failure modes:
    - Consumer log line never appears → check that producer and consumer agree on queue name (`orders.created`); check that producer's POST returned 202 (so the publish actually happened); check the RabbitMQ Mgmt UI (http://localhost:15672/#/queues) shows the queue exists and is bound.
    - Both apps fail to start because port 8080 OR 8081 is already in use → run `mise run preflight` (will catch 8080/8081 if they're in the configured port list — they are not in Phase 1's 5 infrastructure ports, so the executor must check separately if needed).
  </action>
  <acceptance_criteria>
    - Both `Started ProducerApplication` and `Started ConsumerApplication` appear in /tmp/dev.log within 90 seconds
    - `curl -sf -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}'` exits 0 and the response body contains `orderId`
    - The consumer log file contains a line matching `OrderCreated received: orderId=<the same UUID returned by the POST>` within 30 seconds of the POST: `grep "OrderCreated received: orderId=$EXTRACTED_UUID" /tmp/dev.log` exits 0
    - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health` outputs `200`
    - `curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'` exits 0
    - Queue depth zero after consume: `curl -s -u guest:guest "http://localhost:15672/api/queues/%2F/orders.created" | python3 -c "import json,sys; assert json.load(sys.stdin).get('messages',-1)==0"` exits 0
    - `mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry 2>&amp;1 | grep -cE '^\[INFO\] [+\\][- ]+io\.opentelemetry:' | awk '{exit ($1==0)?0:1}'` exits 0
    - Both producer and consumer processes are cleanly stopped at end of task
  </acceptance_criteria>
  <verify>
    <automated>code=$(curl -s -o /tmp/order_resp.json -w "%{http_code}" -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}'); test "$code" = "202" &amp;&amp; OID=$(python3 -c "import json; print(json.load(open('/tmp/order_resp.json'))['orderId'])") &amp;&amp; sleep 5 &amp;&amp; grep -q "OrderCreated received: orderId=$OID" /tmp/dev.log &amp;&amp; test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/actuator/health)" = "200"</automated>
  </verify>
  <done>Producer + consumer both start cleanly via `mise run dev`; POST /orders returns 202 with orderId; consumer logs `OrderCreated received: orderId=<same_uuid>` within 30s; consumer /actuator/health returns 200 + UP; orders.created queue depth is 0 after consume; consumer-service dependency tree contains zero OTel artifacts.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| RabbitMQ broker → consumer | Untrusted JSON payload arrives via AMQP; deserialised by Jackson2JsonMessageConverter |
| Consumer → Spring Boot Actuator | `/actuator/health` exposed on port 8081 (whitelist only) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-1-05-01 | Tampering / EoP | Jackson polymorphic deserialization on AMQP message body | mitigate | OrderListener accepts `Map<String, Object>` (not polymorphic); Jackson2JsonMessageConverter default config does NOT enable `@class` headers; no `@JsonTypeInfo` anywhere |
| T-1-05-02 | Information Disclosure | Spring Boot Actuator exposing /actuator/env or /beans on port 8081 | mitigate | `application.yaml` whitelists only `health` |
| T-1-05-03 | Repudiation | Consumer logs do not include trace_id/span_id (no correlatability for forensic) | accept | Phase 1 has no OTel; Phase 5 adds trace_id/span_id MDC injection (LOG-04). Workshop-scope acceptable. |
| T-1-05-04 | DoS | RabbitMQ message backlog overwhelms consumer if listener throws | accept | Phase 1 ProcessingService is a no-op (cannot throw); Phase 3 introduces deterministic-10%-failure path with explicit recordException — workshop-controlled |
| T-1-05-05 | Tampering | A malicious peer publishing to the broker reaches the consumer with arbitrary JSON | accept | Loopback-only AMQP per docker-compose; PROJECT.md scopes auth out |

**Phase scope:** Workshop scaffold — no Internet exposure, no PII, no real auth surface. Out of scope: TLS, AMQP authentication beyond default guest/guest, message-level signing, JSON schema validation.
</threat_model>

<verification>
- `mvn -pl consumer-service -DskipTests package` exits 0.
- `mise run dev` (parallel) brings both apps to "Started" within 90s.
- POST to producer's /orders returns 202 with an orderId; consumer logs `OrderCreated received: orderId=<same_uuid>` within 30s.
- Consumer `/actuator/health` returns 200 + status UP on port 8081.
- `orders.created` queue depth is 0 after consume.
- `mvn -pl consumer-service dependency:tree -Dincludes=io.opentelemetry` returns zero matching artifacts.
</verification>

<success_criteria>
- APP-03 satisfied: consumer's `@RabbitListener` on `orders.created` receives every message and delegates to `ProcessingService.process(...)`.
- APP-05 (consumer half) satisfied: GET /actuator/health on port 8081 returns 200 + status UP.
- End-to-end working: POST /orders → publish → consume → log line within 30 seconds; queue drains to 0.
- Pitfall F neutralised: application.yaml minimal — only app name + actuator health whitelist.
- Phase 1 baseline preserved: consumer's dependency tree contains zero `io.opentelemetry:*` artifacts.
</success_criteria>

<output>
After completion, create `.planning/phases/01-baseline-scaffold/1-05-SUMMARY.md` documenting:
- File tree of consumer-service/src/main (4 Java files + 1 yaml)
- Confirmed startup line (`Started ConsumerApplication in X seconds`)
- Confirmed end-to-end flow: paste the POST response (orderId) and the matching consumer log line (OrderCreated received: orderId=...)
- Confirmed actuator health (paste status UP body)
- Confirmed queue depth = 0 after consume
- Confirmed dependency:tree shows zero OTel artifacts
</output>
