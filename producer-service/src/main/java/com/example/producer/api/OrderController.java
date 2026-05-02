package com.example.producer.api;

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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
        LOG.info("received POST /orders payload={}", payload);
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
