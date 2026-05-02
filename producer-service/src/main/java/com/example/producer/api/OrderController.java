package com.example.producer.api;

import com.example.producer.cache.IdempotencyService;
import com.example.producer.domain.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader) {
        LOG.info("received POST /orders payload={}", payload);

        // Phase 8: idempotency gate (DB-CACHE-02).
        // Prefer the X-Idempotency-Key header; fall back to body "orderId" field;
        // skip check entirely if neither is present (backwards-compat with mise.toml demo:order).
        String idempotencyKey = idempotencyKeyHeader != null
            ? idempotencyKeyHeader
            : (payload.get("orderId") instanceof String s ? s : null);

        if (idempotencyKey != null) {
            IdempotencyService.Result result = idempotencyService.checkAndMark(idempotencyKey);
            if (result == IdempotencyService.Result.SEEN) {
                LOG.info("idempotency duplicate detected: key={}", idempotencyKey);
                return ResponseEntity.status(409)
                    .body(Map.of("status", "duplicate", "idempotencyKey", idempotencyKey));
            }
        }

        String orderId = orderService.place(payload);
        // Log the minted orderId on the controller path so workshop
        // attendees can pivot from the inbound HTTP log line to the
        // matching publish/consume log lines via Loki — the trace_id
        // and the orderId both surface here, on the SERVER span (IN-08).
        LOG.info("accepted orderId={}", orderId);
        // 202 Accepted: order accepted for async processing via AMQP.
        return ResponseEntity.accepted().body(Map.of("orderId", orderId));
    }
}
