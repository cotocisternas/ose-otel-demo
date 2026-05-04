# Phase 15: Outbound HTTP-Client Spans - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-04
**Phase:** 15-outbound-http-client-spans
**Areas discussed:** HTTP call timing in OrderService, NotificationStubController design, RestClient @Bean wiring

---

## HTTP Call Timing in OrderService

### Q1: Where should the outbound HTTP call sit relative to the AMQP publish?

| Option | Description | Selected |
|--------|-------------|----------|
| After AMQP publish | publish → notify → counter. Attendees see familiar PRODUCER span first, then NEW CLIENT span. | ✓ |
| Before AMQP publish | notify → publish → counter. CLIENT span appears first in the waterfall — maximizes visual impact. | |
| Claude decides | Let the planner pick whichever ordering produces the best teaching waterfall. | |

**User's choice:** After AMQP publish
**Notes:** Waterfall reads top-down as the existing flow + the new HTTP hop. Narrative: "order is queued, now notify."

### Q2: If the notification HTTP call fails, should it block the order?

| Option | Description | Selected |
|--------|-------------|----------|
| Fire-and-forget | Catch exception, log warning, continue to counter. Order succeeds regardless. | ✓ |
| Propagate failure | Let exception bubble up. Order fails if notification fails. | |
| Claude decides | Let the planner choose based on what produces the best teaching trace shapes. | |

**User's choice:** Fire-and-forget
**Notes:** The interceptor captures status=ERROR on the CLIENT span. Attendees see both happy and unhappy HTTP paths.

### Q3: Should the notification call be a separate injected service, or inline in OrderService?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline in OrderService | RestClient.Builder injected in constructor, RestClient built once. No separate class. | ✓ |
| Separate NotificationClient class | New class wrapping the RestClient call. More separation but adds indirection. | |
| Claude decides | Let the planner choose the shape that best serves the teaching narrative. | |

**User's choice:** Inline in OrderService
**Notes:** Matches "boilerplate is the lesson" ethos. Attendees read HTTP call right next to AMQP publish.

---

## NotificationStubController Design

### Q4: What HTTP method and path should the notification stub use?

| Option | Description | Selected |
|--------|-------------|----------|
| POST /notifications | Semantically correct (side effect). Distinct from existing POST /orders. | ✓ |
| GET /notifications/health | Simpler (no body). Less realistic but lower noise. | |
| Claude decides | Let the planner pick the simplest path that proves traceparent propagation. | |

**User's choice:** POST /notifications
**Notes:** Two different POST endpoints in the same service — one inbound, one outbound target.

### Q5: What should the stub controller DO with the incoming request?

| Option | Description | Selected |
|--------|-------------|----------|
| Log traceparent + return 200 | Log header at INFO, return empty 200. Minimal for HCLI-04. | ✓ |
| Log + return orderId echo | Log traceparent AND echo orderId. Richer but adds noise. | |
| Claude decides | Minimize to whatever proves HCLI-04. | |

**User's choice:** Log traceparent + return 200
**Notes:** Attendees grep producer log for "traceparent" to verify propagation.

### Q6: What should OrderService send as the request body?

| Option | Description | Selected |
|--------|-------------|----------|
| Just orderId | {"orderId": "<uuid>"}. Minimal, mirrors AMQP routing identity. | ✓ |
| Full order payload | Forward entire Map. More realistic but more than stub needs. | |
| Claude decides | Choose minimal body for clear teaching. | |

**User's choice:** Just orderId
**Notes:** Same orderId visible in both AMQP and HTTP paths.

### Q7: Should the notification URL be configurable via application.yaml?

| Option | Description | Selected |
|--------|-------------|----------|
| Configurable | app.notification-url in application.yaml. @Value injection. | ✓ |
| Hardcoded localhost | Inline URL in OrderService. Simpler, one less config line. | |
| Claude decides | Pick the minimal approach. | |

**User's choice:** Configurable
**Notes:** Consistent with how other connection strings surface in application.yaml.

---

## RestClient @Bean Wiring

### Q8: Where should the RestClient.Builder @Bean and interceptor registration live?

| Option | Description | Selected |
|--------|-------------|----------|
| New HttpClientConfig | Mirrors RabbitConfig exactly. Creates interceptor @Bean, then RestClient.Builder @Bean. | ✓ |
| In OtelSdkConfiguration | All OTel-related beans together. Mixes SDK bootstrap with instrumentation wiring. | |
| Claude decides | Pick based on RabbitConfig precedent. | |

**User's choice:** New HttpClientConfig
**Notes:** Structural parallel: RabbitConfig wires AMQP tracing, HttpClientConfig wires HTTP tracing.

### Q9: What value should service.peer.name carry on CLIENT spans?

| Option | Description | Selected |
|--------|-------------|----------|
| "notification-service" | Static, descriptive. Teaches service graph dependency naming. | ✓ |
| Derived from URL host | Parse hostname at runtime. Less meaningful for service graph. | |
| Claude decides | Pick for best Tempo service graph teaching. | |

**User's choice:** "notification-service"
**Notes:** Even though stub is in-process, attribute demonstrates production pattern.

### Q10: How should the interceptor get the peer service name?

| Option | Description | Selected |
|--------|-------------|----------|
| Constructor parameter | Interceptor takes peerServiceName arg. @Bean factory configures target. | ✓ |
| Derive from request URL | Parse URL hostname per request. No constructor param. | |
| Claude decides | Decide the wiring shape. | |

**User's choice:** Constructor parameter
**Notes:** Interceptor stays reusable in otel-bootstrap; HttpClientConfig passes "notification-service".

---

## Claude's Discretion

- Exact semconv attribute constants for client-side HTTP spans
- `service.peer.name` typed constant vs string literal
- `HttpHeadersSetter` implementation details
- CLIENT span naming convention
- HttpServerSpanFilter automatic behavior on stub requests
- README §15 wording and structure
- Screenshot deferral to Phase 18
- `verify:http-client-spans` implementation
- Integration test assertions
- Interceptor error handling pattern

## Deferred Ideas

None — discussion stayed within phase scope.
