---
phase: 02-manual-sdk-bootstrap-first-traces
reviewed: 2026-05-01T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - producer-service/pom.xml
  - consumer-service/pom.xml
  - mise.toml
  - producer-service/src/main/java/com/example/producer/config/OtelSdkConfiguration.java
  - producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java
  - consumer-service/src/main/java/com/example/consumer/config/OtelSdkConfiguration.java
  - producer-service/src/main/java/com/example/producer/domain/OrderService.java
  - producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java
  - consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java
  - consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java
  - README.md
findings:
  blocker: 1
  warning: 8
  info: 4
  total: 13
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-05-01
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Phase 2 implements manual OpenTelemetry SDK bootstrap across two Spring Boot services with five business-code span sites. I cross-checked Phase 2 plans 02-01 through 02-06 (locked decisions D-01..D-16 and DOC-03/05) before flagging anything that resembles intentional pedagogy:

- **Per-service duplication of `OtelSdkConfiguration.java`** (D-01/DOC-05) — NOT flagged.
- **Inline span template at every call site** (D-01) — NOT flagged.
- **`OrderListener` CONSUMER span using `Context.root()`** (D-10) — NOT flagged; this is the deliberate broken-then-fixed state.
- **Heavy comment density (≥40 lines per OtelSdkConfiguration)** (DOC-03) — NOT flagged.
- **Filter producer-only, no equivalent consumer SDK config wiring** (D-07) — NOT flagged.

What I **did** find: one BLOCKER concerning user-facing documentation that is factually incorrect at the `step-02-traces` workshop checkpoint, plus eight WARNINGS spanning robustness gaps (NPE risks, env-var edge cases, semconv-compliance ambiguity, error-class scope), and four INFO-level inconsistencies.

The compiled code itself is functionally sound — APIs are used correctly against `opentelemetry-api 1.61.0` (verified by inspecting `~/.m2` jars: `Resource.merge`, `OpenTelemetrySdk.close()`, `SpanBuilder.setAttribute(AttributeKey<T>, T)`, `MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE`, and the `MessagingOperationTypeIncubatingValues` / `MessagingSystemIncubatingValues` nested enums all resolve and have the expected types). The Phase 2 invariant (one version per OTel artifact) is structurally enforced by the `dependencyConvergence` rule in the parent pom.

## Critical Issues

### CR-01: README contradicts the actual codebase at `step-02-traces` (BLOCKER)

**File:** `README.md:5,66,96,98`
**Issue:** Plan 02-06 explicitly leaves the introductory paragraph (line 5), the "First run" closing note (line 66), and the "What's NOT here yet" list (lines 96-101) untouched. As a result, with all of Phase 2 applied and `step-02-traces` marked **Current** in the checkpoints list, the same README also tells the reader:

- Line 5: "The current `main` branch as of `step-01-baseline` shows the **uninstrumented baseline** — both Spring Boot apps run end-to-end with `POST /orders` flowing through RabbitMQ, but with **zero OpenTelemetry libraries on the classpath**."
- Line 66: "In Phase 1 there is **no telemetry** — ... `mise run verify:bom` should report zero OpenTelemetry libraries on the classpath."
- Line 98: `- No \`OtelSdkConfiguration.java\` (Phase 2)` (under "What's NOT here yet").

These statements are now false: the SDK is on the classpath, both `OtelSdkConfiguration.java` files exist, and `mise run verify:bom` (rewritten in Plan 02-01) asserts ONE version per artifact, not zero. A workshop attendee who runs `git checkout step-02-traces` (the immutable WORK-01 tag this phase ships) and follows the README will be told their environment is uninstrumented when it is not — and `mise run verify:bom` will succeed with the OPPOSITE message ("Phase 2 baseline confirmed: one version per OpenTelemetry artifact across all modules") to what line 66 promises. This contradicts the clearly-stated Phase 2 success criterion ("README explains the per-service duplication") in spirit and breaks DOC-04's broken-vs-fixed pedagogical premise: the attendee can no longer trust the README to describe their current state.

Plan 02-06 defers the larger README rewrite to Phase 7 / DOC-01 — but Phase 7 is not a Phase 2 deliverable and the immutable annotated tag `step-02-traces` will preserve this contradiction forever. At minimum, the three outdated sentences must be neutralised at this tag.

**Fix:** Apply the minimal three-line correction the deferral implicitly assumed already happened. In `README.md`, reword:

```markdown
# Line 5 — replace
The workshop progresses through six annotated git tags: `step-01-baseline` → `step-02-traces` → ...
You can `git checkout` any tag to time-travel through the workshop. The current `main`
branch is at `step-02-traces` — Phase 2 has wired the manual OpenTelemetry SDK and both
services emit DISCONNECTED traces (intentional setup for Phase 3's propagation lesson).
For the uninstrumented Phase 1 baseline, `git checkout step-01-baseline`.

# Line 66 — replace
In Phase 1 there was no telemetry; Phase 2 introduced the OpenTelemetry SDK and traces
are now flowing. From `step-02-traces` onward, `mise run verify:bom` asserts that every
io.opentelemetry* artifact converges to a single version across the reactor (not the
zero-libs check the Phase 1 baseline used).

# Lines 96-98 — drop the "No OtelSdkConfiguration.java (Phase 2)" bullet entirely; the
#   file now exists. Reword the "deliberate Phase 1 omissions" preamble to clarify the
#   list refers to anything beyond the CURRENT step-02-traces checkpoint, e.g.:
The following are deliberate omissions at `step-02-traces` — each later phase has
something concrete to add:
- (drop) No `OtelSdkConfiguration.java` (Phase 2)
- No `traceparent` header injection on AMQP (Phase 3)
- ... (remaining bullets unchanged)
```

These are surgical edits — no full README rewrite, just three correctness fixes that keep Phase 7's larger documentation work intact.

## Warnings

### WR-01: `HttpServerSpanFilter` swallows `Error` and skips `recordException`

**File:** `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java:110-121`
**Issue:** The catch clause is `catch (RuntimeException | ServletException | IOException e)`. If `chain.doFilter()` raises an `Error` (e.g., `OutOfMemoryError`, `StackOverflowError`, `NoClassDefFoundError` thrown lazily) the catch is bypassed; control falls through `finally`, where `span.end()` runs WITHOUT `recordException(e)` or `setStatus(StatusCode.ERROR)`. Tempo will display the request as a successful SERVER span — an outright observability lie at the moment operators most need a true signal. This is also asymmetric with `OrderListener.onOrder` etc., which all share the same shape, so the same defect repeats five times across the codebase. The same logic gap exists at `OrderService.java:52`, `OrderPublisher.java:73`, `OrderListener.java:69`, `ProcessingService.java:43`.

**Fix:** Catch a wider type while preserving the rethrow contract. The clearest option is `Throwable`, with a precise rethrow that the compiler accepts under multi-catch:

```java
} catch (Throwable t) {
    span.recordException(t);
    span.setStatus(StatusCode.ERROR);
    if (t instanceof RuntimeException re) throw re;
    if (t instanceof IOException io)      throw io;
    if (t instanceof ServletException se) throw se;
    if (t instanceof Error e)             throw e;
    throw new RuntimeException(t); // unreachable for Throwable, but completes the type proof
} finally {
    span.end();
}
```

If broadening to `Throwable` clashes with workshop-pedagogy goals, the minimum acceptable fix is to add a second `catch (Error e) { span.recordException(e); span.setStatus(StatusCode.ERROR); throw e; }` block. The same edit needs to be applied to all five span sites or none of them — partial coverage is worse than uniform RuntimeException-only.

### WR-02: NPE risk in `HttpServerSpanFilter.shouldNotFilter` and request-attribute reads

**File:** `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java:66,84,99-101`
**Issue:**
1. `request.getRequestURI()` (line 66, line 84) can return `null` per the Servlet 5 specification in unusual dispatcher contexts (e.g., container error dispatch with a malformed request line). Calling `.startsWith("/actuator/")` on null is an unchecked NPE that terminates the filter chain before the SERVER span is created — and before any catch block can record it.
2. `request.getServerName()` (line 100) is documented to return null in the same edge cases. Passing null into `.setAttribute(ServerAttributes.SERVER_ADDRESS, null)` does not throw on the OTel API, but it logs an SDK warning per attribute and pollutes the resource bag.
3. `getRequestURI()` includes the servlet context path. Although Spring Boot defaults to no context path, anyone deploying this demo behind a Tomcat valve or under `server.servlet.context-path=/svc` will silently bypass the actuator exclusion (e.g., `/svc/actuator/health` does not start with `/actuator/`), reintroducing the health-check noise the filter is supposed to keep out.

**Fix:** Use `getServletPath()` (which is context-path-relative) and short-circuit on null:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path != null && path.startsWith("/actuator/");
}

@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
    String method = request.getMethod() != null ? request.getMethod() : "UNKNOWN";
    String path   = request.getServletPath() != null ? request.getServletPath() : "/";
    String addr   = request.getServerName() != null ? request.getServerName() : "unknown";
    // ...
}
```

If `getRequestURI()` is required for a workshop reason, at minimum guard the prefix check: `String uri = request.getRequestURI(); return uri != null && uri.startsWith("/actuator/");`.

### WR-03: Empty `OTEL_EXPORTER_OTLP_ENDPOINT` env var crashes startup

**File:** `producer-service/.../config/OtelSdkConfiguration.java:114-118`; `consumer-service/.../config/OtelSdkConfiguration.java:121-125`
**Issue:** `Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse(DEFAULT_OTLP_ENDPOINT)` only falls back when the env var is *absent*. If a workshop attendee unsets it interactively (`unset OTEL_EXPORTER_OTLP_ENDPOINT`) it falls back correctly; but if they shadow it with an empty value (`OTEL_EXPORTER_OTLP_ENDPOINT= mvn spring-boot:run`, or accidentally clear it in their shell rc with `export OTEL_EXPORTER_OTLP_ENDPOINT=""`), `getenv` returns `""`, the `Optional` is non-empty, and `OtlpGrpcSpanExporter.builder().setEndpoint("")` throws `IllegalArgumentException: endpoint must start with http:// or https://, but was: ` — Spring fails the bean factory and the whole app aborts. The Javadoc cited in the comment (line 113) is accurate; the code does not implement the same guarantee.

**Fix:** Treat empty as absent. Two equivalent forms:

```java
String endpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
    .filter(s -> !s.isBlank())
    .orElse(DEFAULT_OTLP_ENDPOINT);
```

or:

```java
String raw = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
String endpoint = (raw == null || raw.isBlank()) ? DEFAULT_OTLP_ENDPOINT : raw;
```

The fix applies identically in both producer and consumer files (per-service duplication ethos already accepted).

### WR-04: PRODUCER span `messaging.destination.name` set to queue, not exchange

**File:** `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java:55-56`
**Issue:** Per OTel messaging semconv (RabbitMQ profile), the producer publishes to an *exchange + routing key* and the `messaging.destination.name` on a PRODUCER span should be the destination the producer actually sends to — that is, the **exchange name** (`"orders"` from `RabbitConfig.EXCHANGE`), with the queue captured via the consumer-side binding. The code uses `RabbitConfig.QUEUE = "orders.created"` (a queue) for the producer side, which is what the consumer reads from but not what the producer publishes to. With anonymous queues or fan-outs the producer cannot even know which queues will receive — so this attribute will mislead operators reading Tempo when this codebase is used as a template.

The plan acknowledges this as an intentional pedagogical simplification (D-04, D-11 — "span-name uses queue; routing-key is a separate attribute below") for symmetry with the CONSUMER span. But the workshop's stated value is teaching OTel semconv correctly; flagging deprecated keys in the same file (the `MESSAGING_OPERATION_TYPE` rename comment) and then setting the standard key incorrectly muddies the lesson. The span name `"orders.created publish"` is fine as a low-cardinality identifier, but the *attribute* is the canonical one operators query on.

**Fix:** Either (a) use the exchange for `messaging.destination.name` on the producer side and document the AMQP asymmetry:

```java
.setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
    RabbitConfig.EXCHANGE)        // producer publishes to the exchange
.setAttribute(MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
    RabbitConfig.ROUTING_KEY)     // routing key — already correct
```

or (b) keep the queue but add a teaching comment in `OrderPublisher.java` explaining that for direct-exchange RabbitMQ, the workshop deliberately uses the queue name on both ends for symmetry, and this should NOT be copied verbatim into a production codebase. (a) is the lower-risk fix because it leaves the consumer side correct as-is and aligns with `MESSAGING_DESTINATION_PUBLISH_NAME` (which exists in the same artifact and is precisely the publish-side override for this case).

### WR-05: `HttpServerSpanFilter` uses request URI as `http.route` and as part of the span name

**File:** `producer-service/src/main/java/com/example/producer/config/HttpServerSpanFilter.java:91,98,102`
**Issue:** `path = request.getRequestURI()` is the literal request URI (e.g., `/orders/123`), set verbatim as both `UrlAttributes.URL_PATH` (correct) AND as `HttpAttributes.HTTP_ROUTE` (incorrect — `http.route` is defined as the matched route template like `/orders/{id}`) AND used as the span name (`method + " " + path`). When workshop attendees later add path-variable endpoints, every distinct path will produce a unique span name and a unique `http.route` value — both unbounded-cardinality dimensions that Tempo and Mimir treat as labels. This blows up the cardinality control that span names exist to enforce.

The code comment at line 76 acknowledges Spring's actual `@RequestMapping` route is unavailable pre-dispatch from a generic Filter — true. But the canonical OTel pattern is to enrich `http.route` *after* dispatch, by reading `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` from the request after `chain.doFilter()` returns. This file already has a post-chain hook for the status code; adding the route in the same place is one extra line and removes the cardinality bomb.

**Fix:** Stop setting `http.route` pre-dispatch (or set it to a placeholder), and read the matched pattern after the chain returns. Also update the span name to the matched route once known:

```java
try (Scope scope = span.makeCurrent()) {
    chain.doFilter(request, response);
    String matched = (String) request.getAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (matched != null) {
        span.setAttribute(HttpAttributes.HTTP_ROUTE, matched);
        span.updateName(method + " " + matched);
    }
    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        (long) response.getStatus());
}
```

If updating the name post-dispatch is rejected as workshop-out-of-scope, at minimum drop the `HTTP_ROUTE = path` line on line 102 — setting a wrong value is worse than not setting it.

### WR-06: `OrderListener` does not validate AMQP message shape

**File:** `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java:46-68`
**Issue:** `Map<String, Object> message` is the deserialized AMQP body. The listener calls `message.get("orderId")` and feeds the (possibly null) value into `LOG.info(...)`, then unconditionally invokes `processingService.process(message)`. There is no check that `message != null`, that "orderId" is present, or that it is a String. A producer-side bug (or any badly-typed message landing on the queue) leads to one of:
- `LOG.info("OrderCreated received: orderId=null", ...)` and silent processing of a malformed payload
- `NullPointerException` inside `processingService.process` if the implementation later dereferences a missing key (the body is empty in Phase 2 but Phase 3 wires real work — D-03 noted)

The Spring AMQP listener container will then re-deliver the message in a tight loop unless a DLQ is configured. With a CONSUMER span recorded on each retry, Tempo will fill with errored CONSUMER spans for one bad message — exactly the "thundering herd of trace noise" the actuator filter exclusion exists to prevent on the producer side.

**Fix:** Validate at the boundary and short-circuit cleanly:

```java
public void onOrder(Map<String, Object> message) {
    if (message == null) {
        LOG.warn("OrderCreated received with null body — discarding");
        return;
    }
    Object orderId = message.get("orderId");
    if (!(orderId instanceof String s) || s.isBlank()) {
        LOG.warn("OrderCreated received without valid orderId: keys={} — discarding",
            message.keySet());
        return;
    }
    // ... continue with the existing span shape, using `s` as the orderId string
}
```

This also matches the global coding-style rule "validate at system boundaries". For workshop pedagogy, the validation block is two extra lines and visibly demonstrates the pattern that real-world AMQP listeners need.

### WR-07: `Resource.getDefault()` is not protected against subsequent mutation by other services

**File:** Both `OtelSdkConfiguration.java` files (producer line 95, consumer line 102)
**Issue:** Minor maintenance trap: the textbook comment on lines 89-94 (producer) / 96-101 (consumer) says `Resource.merge(other)` puts `other` last so OUR overrides win. That is correct as documented, but `Resource.getDefault()` is *also* affected by `OTEL_RESOURCE_ATTRIBUTES` / `OTEL_SERVICE_NAME` env vars at SDK init time. If a workshop attendee sets `OTEL_SERVICE_NAME=my-laptop` (the OTel-specified override path, common from the autoconfigure docs they may read elsewhere), `Resource.getDefault()` will already include `service.name=my-laptop`, our `.merge()` puts our `"order-producer"` on top — so things appear to work. But if a future version of the SDK changes default-resource semantics, the merge order behavior the comment claims is locked is in practice tied to internal SDK ordering. This is not a bug today; it is a brittle assumption to commit to writing as a "textbook" lesson.

**Fix:** No code change required. Tighten the comment to say: "Whenever OUR `Attributes` and the default-resource keys collide, the call site `merge(other).putXyz(...)` ensures our keys win because `merge(other)` returns a NEW resource where `other`'s entries override `this`'s. The default resource itself can be tuned via `OTEL_RESOURCE_ATTRIBUTES`; this code intentionally hardcodes service.name to keep the workshop reproducible regardless of environment-supplied defaults." This documents the env-var override path workshop attendees will encounter when they later look up Resource semantics.

### WR-08: `service.instance.id` regenerated per JVM start, not per pod/container

**File:** Both `OtelSdkConfiguration.java` files (producer line 99, consumer line 106)
**Issue:** `UUID.randomUUID().toString()` is set as `service.instance.id` on every bean creation. The comment on line 91-93 (producer) explicitly defends this for workshop use. That defense is fine — but on a *workshop laptop running with the Spring DevTools restart classloader or under `mvn spring-boot:run` with auto-restart enabled*, every restart produces a new instance ID, which makes Tempo's "service overview" panel show a fanout of N short-lived instances per attendee. For a 30-attendee workshop running for 8 hours with attendees iterating, this is hundreds of cardinality-1 instance IDs in Mimir.

This is mitigated by mise.toml not enabling devtools and the workshop scope being explicit — but the supporting comment ("a real deployment would prefer a stable host/pod identifier") understates the workshop downside. Cardinality blowup on the workshop's *own* Mimir instance is the very problem the sampler comment teaches.

**Fix:** Either (a) document the limitation more strongly in the existing comment, or (b) seed the UUID from a stable source available on the workshop laptop:

```java
// Workshop-stable instance ID: use the JVM's process ID + hostname.
// Restarting the JVM yields a new id (intentional — a fresh JVM IS a new instance);
// devtools-driven hot reloads keep the SAME id within one parent JVM.
.put(ServiceAttributes.SERVICE_INSTANCE_ID,
    ManagementFactory.getRuntimeMXBean().getName())
```

`getRuntimeMXBean().getName()` returns `"<pid>@<hostname>"`. Workshop-correct, demonstrates a real-world choice, and removes the devtools fanout failure mode.

## Info

### IN-01: Inconsistent arrow style between the two `OtelSdkConfiguration.java` files

**File:** `producer-service/.../config/OtelSdkConfiguration.java:35` (`->`); `consumer-service/.../config/OtelSdkConfiguration.java:35` (`→`)
**Issue:** The per-service-duplication ethos in DOC-05 promises that the two files "differ in only five small ways (package, JavaDoc cross-reference, the service.name string, the tracer scope name, plus the producer-only `HttpServerSpanFilter` bean)." The diff actually adds a sixth, unintended difference: the producer file uses ASCII `->` in its JavaDoc cross-reference paragraph (`"order-producer" -> "order-consumer"`) while the consumer file uses Unicode `→` (`"order-consumer" → "order-producer"`). Workshop attendees running `diff` on the two files (the README explicitly invites this in the DOC-05 paragraph) will see a noisy character-level diff that the README does not call out. Trivial to fix; meaningful for the workshop's symmetry promise.

**Fix:** Normalise both files to the same arrow. Either both ASCII `->` or both Unicode `→`. Pick one style and apply uniformly; matching the JavaDoc paragraph in producer's file (`->`) is the smaller change.

### IN-02: `RabbitConfig.QUEUE` symbolised in span name vs. inline literal

**File:** `producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java:51,55-56`; `consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java:55,60-61`
**Issue:** `tracer.spanBuilder("orders.created publish")` and `tracer.spanBuilder("orders.created process")` hardcode the queue name `"orders.created"`, while the same files reference the constant `RabbitConfig.QUEUE` for the `MESSAGING_DESTINATION_NAME` attribute three lines down. If the queue is ever renamed (Phase 5 may rename for a logs lesson, or a future cohort variant), the span name and the attribute will diverge silently. The plan calls out this asymmetry implicitly by saying "span-name uses queue; routing-key is a separate attribute below" but the implementation could symbolise the span name with a constant just as easily.

**Fix:** Either inline `RabbitConfig.QUEUE` into the span-name builder string (`tracer.spanBuilder(RabbitConfig.QUEUE + " publish")`), or define `public static final String PRODUCE_SPAN_NAME = QUEUE + " publish";` in `RabbitConfig`. The first form is a one-character change per span site and keeps the literal in source for grep-ability while still binding to the constant.

### IN-03: `Tracer` `@Bean` is package-private; class is public

**File:** Both `OtelSdkConfiguration.java` files (line 194 producer, line 200 consumer)
**Issue:** `Tracer tracer(OpenTelemetry openTelemetry)` (no `public` modifier) is package-private. Spring resolves it via reflection regardless, and `RabbitConfig.java` follows the same package-private style for `@Bean` methods, so the inconsistency is internal-to-this-module. But the class itself is `public class OtelSdkConfiguration` and the `openTelemetry()` factory at line 84 is also package-private — meaning anything reading the public API surface of this class sees no factory methods at all, which is mildly disorienting for IDE-driven attendees clicking into the class definition. Spring docs accept either; a workshop lesson benefits from explicit `public` (or explicit annotation in the JavaDoc that package-private is intentional).

**Fix:** Either add `public` to all `@Bean` factory methods in both `OtelSdkConfiguration.java` files (and to `RabbitConfig.java` for consistency), or add a one-line JavaDoc note above the class explaining the package-private convention. The first is a 5-character change × 4 sites; the second is one comment block.

### IN-04: Forward-pointer in `OtelSdkConfiguration.java` references `TracingMessagePostProcessor` / `TracingMessageListenerAdvice` not yet existing

**File:** `producer-service/.../OtelSdkConfiguration.java:166-167`; `consumer-service/.../OtelSdkConfiguration.java:173`
**Issue:** The propagator-section comments end with a forward-pointing reference to Phase 3 classes (`TracingMessagePostProcessor`, `TracingMessageListenerAdvice`) that do not yet exist. The empty `otel-bootstrap` module is documented as a Phase 3 deliverable. A workshop attendee reading the comment in their IDE at `step-02-traces` and following the named class will see no such symbol — IntelliJ shows the bare name in red. Minor friction for the "code is the textbook" promise.

**Fix:** Reword the forward-pointer to call out the deferral explicitly: "Phase 3 will add `TracingMessagePostProcessor` (a class that does not exist at this checkpoint — see `otel-bootstrap/` placeholder)" or drop the class name from the comment and keep the conceptual description ("Phase 3 reuses what's already wired by reading `openTelemetry.getPropagators().getTextMapPropagator()` to build the inject side of an AMQP propagation pair").

---

_Reviewed: 2026-05-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
