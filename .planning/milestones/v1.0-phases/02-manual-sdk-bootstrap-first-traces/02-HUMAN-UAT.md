---
status: partial
phase: 02-manual-sdk-bootstrap-first-traces
source: [02-VERIFICATION.md]
started: 2026-05-01T17:50:00Z
updated: 2026-05-01T17:50:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. TWO distinct trace IDs in Tempo per POST /orders
expected: Open Grafana → Tempo Explore (http://localhost:3000, admin/admin); search service.name=order-producer and service.name=order-consumer; both show traces, but the producer's traceID differs from the consumer's traceID
result: [pending]

### 2. Ctrl-C flushes the last span batch
expected: Send POST /orders, immediately Ctrl-C the producer; the trace from that POST appears in Tempo within 12 seconds — proving destroyMethod=close cascades close→shutdown(10s)→BSP.flush
result: [pending]

### 3. Producer span attributes correctly populated in Tempo
expected: Open the producer's PRODUCER span in Tempo and verify 4 attribute keys — messaging.system=rabbitmq, messaging.destination.name=orders.created, messaging.operation.type=send (NOT bare messaging.operation), messaging.rabbitmq.destination.routing_key=order.created. Open the SERVER span and verify 7 HTTP attrs (method/path/scheme/server.address/server.port/route + status_code)
result: [pending]

### 4. Consumer's CONSUMER span has empty parentSpanId (Context.root() honored)
expected: Inspect the consumer trace; the root CONSUMER span ('orders.created process') has no parentSpanId (Context.root() forces a fresh root) — this is the broken-then-fixed pedagogical state Phase 3 will fix
result: [pending]

### 5. RabbitMQ message lacks traceparent header (broken-state proof)
expected: RabbitMQ Mgmt UI (http://localhost:15672, guest/guest) → Queues → orders.created → Get messages with Reject requeue=true; verify the message properties contain JSON payload but NO 'traceparent' header. This proves PROP-01 is correctly NOT yet implemented
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
