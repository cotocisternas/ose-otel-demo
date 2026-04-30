---
id: 1-04-producer-service
phase: 1-baseline-scaffold
plan: 04
type: execute
wave: 2
depends_on: [1-01-maven-skeleton, 1-02-mise-toolchain, 1-03-docker-compose]
requirements: [APP-01, APP-02, APP-05]
files_modified:
  - producer-service/src/main/java/com/example/producer/ProducerApplication.java
  - producer-service/src/main/java/com/example/producer/api/OrderController.java
  - producer-service/src/main/java/com/example/producer/domain/OrderService.java
  - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
  - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java
  - producer-service/src/main/resources/application.yaml
autonomous: true
must_haves:
  truths:
    - "ProducerApplication starts on port 8080 (set via PRODUCER_PORT env var) and logs a 'Started ProducerApplication' line"
    - "POST /orders with JSON body returns 202 Accepted with body {\"orderId\":\"<uuid>\"}"
    - "Each successful POST publishes an OrderCreated message via RabbitTemplate.convertAndSend(EXCHANGE='orders', ROUTING_KEY='order.created') — verifiable by inspecting the RabbitMQ Management UI's exchange-publish counter"
    - "GET /actuator/health returns 200 with body {\"status\":\"UP\"} (APP-05)"
    - "application.yaml is minimal: spring.application.name=order-producer, management.endpoints.web.exposure.include=health, NO OTel-related properties (Pitfall F)"
    - "mvn dependency:tree -pl producer-service -Dincludes=io.opentelemetry returns zero matches (Phase 1 baseline preserved)"
  artifacts:
    - path: "producer-service/src/main/java/com/example/producer/ProducerApplication.java"
      provides: "@SpringBootApplication main class"
      contains: "@SpringBootApplication"
    - path: "producer-service/src/main/java/com/example/producer/api/OrderController.java"
      provides: "POST /orders endpoint returning 202 + orderId (APP-01)"
      contains: "ResponseEntity.accepted()"
    - path: "producer-service/src/main/java/com/example/producer/domain/OrderService.java"
      provides: "Domain service delegating to OrderPublisher (sets up Phase 2's INTERNAL span site)"
      contains: "public String place"
    - path: "producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java"
      provides: "AMQP publish via RabbitTemplate.convertAndSend (APP-02)"
      contains: "rabbitTemplate.convertAndSend"
    - path: "producer-service/src/main/java/com/example/producer/config/RabbitConfig.java"
      provides: "Direct exchange + queue + binding bean declarations + Jackson2JsonMessageConverter"
      contains: "DirectExchange"
    - path: "producer-service/src/main/resources/application.yaml"
      provides: "Minimal Spring Boot config: app name + actuator health exposure"
      contains: "name: order-producer"
  key_links:
    - from: "producer-service/.../api/OrderController.java"
      to: "producer-service/.../domain/OrderService.java"
      via: "constructor injection"
      pattern: "private final OrderService orderService"
    - from: "producer-service/.../domain/OrderService.java"
      to: "producer-service/.../messaging/OrderPublisher.java"
      via: "constructor injection + publisher.publish(...)"
      pattern: "publisher.publish"
    - from: "producer-service/.../messaging/OrderPublisher.java"
      to: "RabbitMQ exchange 'orders'"
      via: "RabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message)"
      pattern: "convertAndSend"
    - from: "producer-service/.../config/RabbitConfig.java"
      to: "RabbitMQ broker (docker-compose)"
      via: "Spring Boot autoconfigured ConnectionFactory from SPRING_RABBITMQ_* env vars (set in mise.toml [env] from plan 02)"
      pattern: "DirectExchange ordersExchange"
---

<objective>
Build the producer-service skeleton: a Spring Boot 3.4.13 app exposing `POST /orders` (returns 202 + orderId) that publishes an OrderCreated message via Spring AMQP `RabbitTemplate.convertAndSend(...)` to a direct exchange `orders` with routing key `order.created`, exposes `/actuator/health`, and runs on the host JVM via `mise run dev:producer`. Zero OTel libraries, zero OTel config — the service is intentionally uninstrumented in Phase 1; Phase 2 wires `OtelSdkConfiguration.java`.

Purpose: APP-01 (POST /orders → 202), APP-02 (RabbitTemplate publish), APP-05 (actuator/health on producer). Sets up the internal-span site (`OrderService.place`) and PRODUCER-span site (`OrderPublisher.publish`) that Phase 2 will instrument.
Output: 5 Java files + 1 application.yaml. After `mise run dev:producer`, the app starts on port 8080 and `mise run demo:order` returns 202.
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
@.planning/phases/1-baseline-scaffold/1-RESEARCH.md
@.planning/phases/1-baseline-scaffold/1-01-SUMMARY.md
@.planning/phases/1-baseline-scaffold/1-02-SUMMARY.md
@.planning/phases/1-baseline-scaffold/1-03-SUMMARY.md
@CLAUDE.md
</context>

<tasks>

<task id="1-04-T1" type="auto">
  <name>Task 1: Write ProducerApplication + RabbitConfig (Spring Boot bootstrap + AMQP topology)</name>
  <files>producer-service/src/main/java/com/example/producer/ProducerApplication.java, producer-service/src/main/java/com/example/producer/config/RabbitConfig.java</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 856-901 — ProducerApplication.java + RabbitConfig.java verified skeletons)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 383-388 — Pitfall F: application.yaml must NOT add OTel properties or pull in micrometer-tracing)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 304-306 — Don't Hand-Roll: ConnectionFactory autoconfigured from SPRING_RABBITMQ_* env vars; DO NOT declare a manual @Bean ConnectionFactory)
    - producer-service/pom.xml (created in plan 01 — confirms starter-web/amqp/actuator coordinates)
  </read_first>
  <action>
    Create two Java files under `producer-service/src/main/java/com/example/producer/`.

    **`ProducerApplication.java`** — verbatim from RESEARCH.md lines 858-870:
    - Package: `com.example.producer`
    - Imports: `org.springframework.boot.SpringApplication`, `org.springframework.boot.autoconfigure.SpringBootApplication`
    - Class annotated `@SpringBootApplication`
    - `public static void main(String[] args)` calling `SpringApplication.run(ProducerApplication.class, args)`

    NO custom env-binding beans, NO `@EnableRabbit` (Spring Boot's `RabbitAutoConfiguration` covers it via the `spring-boot-starter-amqp` dependency), NO main-method customisation beyond the standard one-liner.

    **`config/RabbitConfig.java`** — verbatim from RESEARCH.md lines 874-901:
    - Package: `com.example.producer.config`
    - `@Configuration`-annotated class.
    - Three `public static final String` constants:
      - `EXCHANGE = "orders"`
      - `QUEUE = "orders.created"`
      - `ROUTING_KEY = "order.created"`
    - Four `@Bean`s:
      - `DirectExchange ordersExchange()` returning `new DirectExchange(EXCHANGE)`
      - `Queue ordersCreatedQueue()` returning `new Queue(QUEUE, true)` (durable=true)
      - `Binding ordersBinding(Queue q, DirectExchange ex)` returning `BindingBuilder.bind(q).to(ex).with(ROUTING_KEY)`
      - `MessageConverter jsonMessageConverter()` returning `new Jackson2JsonMessageConverter()`

    **DO NOT** declare a `@Bean ConnectionFactory` — Spring Boot's autoconfiguration builds it from the `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` env vars set in `mise.toml [env]` (plan 02). Adding a manual ConnectionFactory bean is one of the Don't-Hand-Roll items in RESEARCH.md.

    Required imports for RabbitConfig:
    - `org.springframework.amqp.core.{Binding, BindingBuilder, DirectExchange, Queue}`
    - `org.springframework.amqp.support.converter.{Jackson2JsonMessageConverter, MessageConverter}`
    - `org.springframework.context.annotation.{Bean, Configuration}`

    Both files use 4-space Java indentation (matching RESEARCH.md examples). No JavaDoc beyond what's in the RESEARCH skeleton (Phase 1 is uninstrumented and the heavy commenting comes in Phase 2 per DOC-03).
  </action>
  <acceptance_criteria>
    - `test -f producer-service/src/main/java/com/example/producer/ProducerApplication.java` exits 0
    - `test -f producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0
    - `grep -c '@SpringBootApplication' producer-service/src/main/java/com/example/producer/ProducerApplication.java` returns 1
    - `grep -c 'package com.example.producer;' producer-service/src/main/java/com/example/producer/ProducerApplication.java` returns 1
    - `grep -c 'public static final String EXCHANGE = "orders";' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 1
    - `grep -c 'public static final String QUEUE = "orders.created";' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 1
    - `grep -c 'public static final String ROUTING_KEY = "order.created";' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 1
    - `grep -c 'new DirectExchange' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 1
    - `grep -c 'Jackson2JsonMessageConverter' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 1
    - `grep -c '@Bean' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` returns 4 (exchange, queue, binding, converter)
    - `! grep -c 'ConnectionFactory' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java` exits 0 (zero manual ConnectionFactory beans — autoconfigured)
    - `mvn -pl producer-service compile` exits 0
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl producer-service -q compile &amp;&amp; grep -q '@SpringBootApplication' producer-service/src/main/java/com/example/producer/ProducerApplication.java &amp;&amp; grep -q 'new DirectExchange(EXCHANGE)' producer-service/src/main/java/com/example/producer/config/RabbitConfig.java</automated>
  </verify>
  <done>ProducerApplication compiles and is annotated @SpringBootApplication; RabbitConfig declares the 4 beans (exchange, queue, binding, converter) with the correct constants; no manual ConnectionFactory bean.</done>
</task>

<task id="1-04-T2" type="auto">
  <name>Task 2: Write OrderController + OrderService + OrderPublisher (POST /orders publishing path)</name>
  <files>producer-service/src/main/java/com/example/producer/api/OrderController.java, producer-service/src/main/java/com/example/producer/domain/OrderService.java, producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java, producer-service/src/main/resources/application.yaml</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 903-986 — OrderController.java + OrderService.java + OrderPublisher.java verified skeletons)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 988-1000 — application.yaml minimal content)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 383-388 — Pitfall F: don't add OTel properties to application.yaml)
    - producer-service/src/main/java/com/example/producer/config/RabbitConfig.java (just created — the constants `EXCHANGE` / `ROUTING_KEY` are referenced by OrderPublisher)
  </read_first>
  <action>
    Create three Java files plus one resources file.

    **`api/OrderController.java`** — verbatim from RESEARCH.md lines 905-929:
    - Package: `com.example.producer.api`
    - Imports: `com.example.producer.domain.OrderService`, `org.springframework.http.ResponseEntity`, `org.springframework.web.bind.annotation.*`, `java.util.Map`
    - Annotated `@RestController` + `@RequestMapping("/orders")`
    - Constructor-injects `OrderService orderService`
    - One `@PostMapping` method `create(@RequestBody Map<String, Object> payload)` returning `ResponseEntity<Map<String, String>>`:
      - Calls `String orderId = orderService.place(payload);`
      - Returns `ResponseEntity.accepted().body(Map.of("orderId", orderId));` — 202 Accepted (NOT 200 OK — async semantics per APP-01)

    Add an inline comment above the return: `// 202 Accepted: order accepted for async processing via AMQP.`

    **`domain/OrderService.java`** — verbatim from RESEARCH.md lines 933-957:
    - Package: `com.example.producer.domain`
    - `@Service`-annotated class
    - Constructor-injects `OrderPublisher publisher`
    - One method `public String place(Map<String, Object> payload)`:
      - `String orderId = UUID.randomUUID().toString();`
      - `publisher.publish(orderId, payload);`
      - `return orderId;`

    This is deliberately three lines — Phase 2 wraps this method in an INTERNAL span (TRACE-06) and the workshop attendees need the call site to be obvious.

    **`messaging/OrderPublisher.java`** — verbatim from RESEARCH.md lines 961-986:
    - Package: `com.example.producer.messaging`
    - Imports: `com.example.producer.config.RabbitConfig` (uses `EXCHANGE` and `ROUTING_KEY`), `org.springframework.amqp.rabbit.core.RabbitTemplate`, `org.springframework.stereotype.Component`, `java.util.{HashMap, Map}`
    - `@Component`-annotated class
    - Constructor-injects `RabbitTemplate rabbitTemplate` (autoconfigured by Spring Boot from `MessageConverter` + `ConnectionFactory` beans)
    - One method `public void publish(String orderId, Map<String, Object> payload)`:
      - Build a `Map<String, Object> message = new HashMap<>(payload);` then `message.put("orderId", orderId);`
      - Call `rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);` — APP-02

    Add an inline comment above the convertAndSend line: `// APP-02: publish via direct exchange + routing key.`

    **`src/main/resources/application.yaml`** — verbatim from RESEARCH.md lines 990-1000. EXACTLY this content (no additions — Pitfall F):
    ```yaml
    spring:
      application:
        name: order-producer
    # RabbitMQ connection picked up from SPRING_RABBITMQ_* env vars (mise.toml)
    management:
      endpoints:
        web:
          exposure:
            include: health
    ```

    Critically: NO `management.tracing.*`, NO `otel.*`, NO `logging.pattern.*` (Phase 5 adds the trace_id pattern), NO `server.port` (the dev:producer task passes `-Dserver.port=${PRODUCER_PORT}` via JVM args — overriding here would be redundant/conflicting). The `management.endpoints.web.exposure.include=health` whitelist (NOT `*`) is a security control: Spring Boot Actuator otherwise exposes `/actuator/env`, `/actuator/beans`, `/actuator/configprops` which can leak credentials and class structure (RESEARCH security domain row "Spring Boot Actuator exposing sensitive endpoints").
  </action>
  <acceptance_criteria>
    - `test -f producer-service/src/main/java/com/example/producer/api/OrderController.java` exits 0
    - `test -f producer-service/src/main/java/com/example/producer/domain/OrderService.java` exits 0
    - `test -f producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` exits 0
    - `test -f producer-service/src/main/resources/application.yaml` exits 0
    - `grep -c '@RestController' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 1
    - `grep -c '@RequestMapping("/orders")' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 1
    - `grep -c '@PostMapping' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 1
    - `grep -c 'ResponseEntity.accepted()' producer-service/src/main/java/com/example/producer/api/OrderController.java` returns 1 (202 status, not 200)
    - `grep -c 'UUID.randomUUID()' producer-service/src/main/java/com/example/producer/domain/OrderService.java` returns 1
    - `grep -c 'rabbitTemplate.convertAndSend' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - `grep -c 'RabbitConfig.EXCHANGE' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - `grep -c 'RabbitConfig.ROUTING_KEY' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java` returns 1
    - `grep -c 'name: order-producer' producer-service/src/main/resources/application.yaml` returns 1
    - `grep -c 'include: health' producer-service/src/main/resources/application.yaml` returns 1
    - `! grep -E '(otel|opentelemetry|tracing)' producer-service/src/main/resources/application.yaml` exits 0 (Pitfall F: no OTel properties)
    - `! grep -E 'include:.*[*\"]\\*\"' producer-service/src/main/resources/application.yaml` exits 0 (no wildcard actuator exposure)
    - `mvn -pl producer-service -DskipTests package` exits 0 (full build with all 5 Java files)
  </acceptance_criteria>
  <verify>
    <automated>mvn -pl producer-service -q -DskipTests package &amp;&amp; grep -q 'ResponseEntity.accepted()' producer-service/src/main/java/com/example/producer/api/OrderController.java &amp;&amp; grep -q 'rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE' producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java &amp;&amp; ! grep -E 'otel|opentelemetry' producer-service/src/main/resources/application.yaml</automated>
  </verify>
  <done>OrderController, OrderService, OrderPublisher compile and form the POST /orders → publish chain; application.yaml minimal with name + actuator health whitelist; no OTel-related config; full Maven package succeeds.</done>
</task>

<task id="1-04-T3" type="auto">
  <name>Task 3: Smoke-test the running producer (start app, POST /orders → 202, check actuator/health)</name>
  <files>(none — verification only)</files>
  <read_first>
    - mise.toml (created in plan 02 — has `dev:producer`, `demo:order` tasks; sets PRODUCER_PORT=8080)
    - docker-compose.yml (created in plan 03 — RabbitMQ must be up for the producer to start cleanly)
    - producer-service/src/main/java/com/example/producer/api/OrderController.java (just created — POST /orders contract)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 1280-1281 — Open Q #1 confirms producer port 8080)
  </read_first>
  <action>
    Start RabbitMQ + lgtm (if not already running), launch the producer in the background, send a POST, verify 202 + JSON body, verify /actuator/health, then shut down. This task creates no files — it proves T1+T2 ship a working APP-01/02/05 surface.

    **Step 1 — Ensure infra is up:**
    `mise run infra:up` (idempotent — `up -d --wait` is a no-op if containers are already healthy).

    **Step 2 — Start producer in background:**
    Launch `mise run dev:producer` in a background shell. Capture its PID for cleanup. Wait for `Started ProducerApplication` line in stdout (poll up to 60 seconds; if it doesn't appear, the app failed to start — common cause: port 8080 collision or RabbitMQ not yet healthy).

    Acceptable launch idioms (executor's choice):
    - `mise run dev:producer > /tmp/producer.log 2>&amp;1 &amp;` then `until grep -q "Started ProducerApplication" /tmp/producer.log; do sleep 2; [ $((SECONDS)) -gt 60 ] && break; done`
    - Or use `nohup` / a temp file watcher. The point is: get the app to "Started" reliably before issuing the POST.

    **Step 3 — Smoke test the contract:**
    - `curl -s -o /tmp/order_resp.json -w "%{http_code}" -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}'` MUST output exactly `202`.
    - `cat /tmp/order_resp.json | python3 -c "import json,sys; d=json.load(sys.stdin); assert 'orderId' in d, 'no orderId'; print(d['orderId'])"` MUST exit 0 and print a UUID.
    - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health` MUST output `200`.
    - `curl -s http://localhost:8080/actuator/health` MUST contain the substring `"status":"UP"`.

    **Step 4 — Verify AMQP publish actually happened:**
    Query RabbitMQ Management API (default credentials guest/guest) for the `orders` exchange's published message count:
    `curl -s -u guest:guest http://localhost:15672/api/exchanges/%2F/orders | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('message_stats',{}).get('publish_in', 0))"`

    The number printed should be >= 1 (one POST → one publish). If 0, the producer's `rabbitTemplate.convertAndSend` is not reaching the broker — investigate (typical causes: SPRING_RABBITMQ_HOST not picked up, exchange name mismatch).

    **Step 5 — Verify Phase 1 baseline preserved:**
    `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry` MUST output zero `io.opentelemetry:*` artifact lines (only INFO headers). The first time the producer module is built with all sources, ensure no OTel libs leaked in via transitive deps.

    **Step 6 — Cleanup:**
    Kill the producer background process (`kill $PID` where PID is whatever the executor captured). Optionally `mise run infra:down` if the executor wants a clean state for the next plan; otherwise leave infra up for plan 05.
  </action>
  <acceptance_criteria>
    - Background `mise run dev:producer` reaches `Started ProducerApplication` line within 60 seconds (extracted from app logs)
    - `curl -s -o /tmp/order_resp.json -w "%{http_code}" -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}'` outputs `202`
    - Response body parses as JSON with `orderId` key set to a non-empty string: `python3 -c "import json; d=json.load(open('/tmp/order_resp.json')); assert d.get('orderId') and len(d['orderId']) > 0"` exits 0
    - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health` outputs `200`
    - `curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'` exits 0
    - RabbitMQ exchange `orders` has at least 1 publish_in: `curl -s -u guest:guest http://localhost:15672/api/exchanges/%2F/orders | python3 -c "import json,sys; d=json.load(sys.stdin); ps=d.get('message_stats',{}).get('publish_in',0); exit(0 if ps&gt;=1 else 1)"` exits 0
    - `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry 2>&amp;1 | grep -cE '^\[INFO\] [+\\][- ]+io\.opentelemetry:' | awk '{exit ($1==0)?0:1}'` exits 0 (zero OTel artifacts on classpath — Phase 1 baseline preserved)
    - Producer process is cleanly stopped at end of task (not left running)
  </acceptance_criteria>
  <verify>
    <automated>code=$(curl -s -o /tmp/order_resp.json -w "%{http_code}" -X POST http://localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"WIDGET-1","quantity":3}'); test "$code" = "202" &amp;&amp; python3 -c "import json; assert json.load(open('/tmp/order_resp.json')).get('orderId')" &amp;&amp; test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health)" = "200"</automated>
  </verify>
  <done>Producer starts cleanly on port 8080; POST /orders returns 202 with a JSON body containing orderId; /actuator/health returns 200 + status UP; RabbitMQ Management API confirms at least one publish on the `orders` exchange; producer-service dependency tree contains zero OTel artifacts.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| External HTTP client → producer | Untrusted JSON crosses `POST /orders` boundary |
| Producer → RabbitMQ broker | Loopback AMQP; auth via guest/guest |
| Producer → Spring Boot Actuator | `/actuator/health` exposed on port 8080 (whitelisted endpoint only) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-1-04-01 | Tampering | OrderController accepts `Map<String, Object>` (no schema validation) | accept | Workshop scope per RESEARCH security V5 (partial). No business logic depends on payload shape. JavaDoc note in OrderController would help future readers; not gating Phase 1. |
| T-1-04-02 | Information Disclosure | Spring Boot Actuator exposing sensitive endpoints (`/actuator/env`, `/actuator/beans`) | mitigate | `application.yaml` sets `management.endpoints.web.exposure.include=health` (whitelist, not `*`); only `/health` is reachable |
| T-1-04-03 | DoS | Unauthenticated `POST /orders` is open to any caller on the laptop's network interface | accept | Workshop runs on attendee laptop; no Internet exposure; PROJECT.md scopes auth out |
| T-1-04-04 | Tampering / EoP | Jackson polymorphic deserialization (CVE patterns) | mitigate | Plain `Map<String, Object>` (not polymorphic types); `Jackson2JsonMessageConverter` default config does NOT enable `@class` headers; no `@JsonTypeInfo` anywhere |
| T-1-04-05 | DoS | Large request body exhausting heap | accept | Spring Boot default `spring.servlet.multipart.max-request-size=10MB` and Tomcat's default request body limit cap exposure; workshop-scope acceptable |
| T-1-04-06 | Information Disclosure | Stack traces leaked via Spring Boot's default error attributes | accept | Standard Spring Boot error handling does NOT include stack traces in responses by default; sufficient for workshop |
| T-1-04-07 | Tampering | Producer reaches a non-localhost RabbitMQ if `SPRING_RABBITMQ_HOST` env var is overridden post-deploy | accept | mise.toml `[env]` pins `localhost`; out-of-band override is the workshop attendee's deliberate choice |

**Phase scope:** Workshop scaffold — no Internet exposure, no PII, no real auth surface. Out of scope: TLS, authn/authz, rate-limiting, request-body size hardening, JSON schema validation, fuzz testing.
</threat_model>

<verification>
- `mvn -pl producer-service -DskipTests package` exits 0.
- Background `mise run dev:producer` reaches `Started ProducerApplication` log line within 60s.
- `curl -X POST http://localhost:8080/orders ...` returns 202 with `{"orderId":"<uuid>"}`.
- `GET /actuator/health` returns 200 + `{"status":"UP"}`.
- RabbitMQ Management API shows publish_in >= 1 on the `orders` exchange.
- `mvn -pl producer-service dependency:tree -Dincludes=io.opentelemetry` returns zero matching artifacts.
</verification>

<success_criteria>
- APP-01 satisfied: POST /orders returns 202 Accepted with JSON body containing the assigned orderId.
- APP-02 satisfied: every successful POST publishes an OrderCreated message via RabbitTemplate.convertAndSend to exchange=`orders`, routing-key=`order.created`.
- APP-05 (producer half) satisfied: GET /actuator/health returns 200 + status UP.
- Pitfall F neutralised: application.yaml minimal — only `spring.application.name` + actuator health whitelist; no OTel-related properties.
- Phase 1 baseline preserved: producer's dependency tree contains zero `io.opentelemetry:*` artifacts.
</success_criteria>

<output>
After completion, create `.planning/phases/1-baseline-scaffold/1-04-SUMMARY.md` documenting:
- File tree of producer-service/src/main (5 Java files + 1 yaml)
- Confirmed startup line (`Started ProducerApplication in X seconds`)
- Confirmed POST /orders → 202 + orderId (paste curl response body)
- Confirmed RabbitMQ publish counter (paste publish_in value from Mgmt API)
- Confirmed actuator health (paste status UP body)
- Confirmed dependency:tree shows zero OTel artifacts (paste relevant tail)
</output>
