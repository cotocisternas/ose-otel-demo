package com.example.producer.domain;

import com.example.producer.messaging.OrderPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderPublisher publisher;

    public OrderService(OrderPublisher publisher) {
        this.publisher = publisher;
    }

    public String place(Map<String, Object> payload) {
        String orderId = UUID.randomUUID().toString();
        publisher.publish(orderId, payload);
        return orderId;
    }
}
