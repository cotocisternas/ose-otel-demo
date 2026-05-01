package com.example.producer.messaging;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.producer.config.RabbitConfig;

/**
 * Publishes OrderCreated to the {@code orders} direct exchange via Spring
 * AMQP {@link RabbitTemplate#convertAndSend}.
 *
 * <p>Phase 3 made this method a thin 3-line pass-through. The PRODUCER
 * span and W3C {@code traceparent} header injection are owned by
 * {@code com.example.otel.amqp.TracingMessagePostProcessor}, which is
 * registered on the {@link RabbitTemplate} bean via
 * {@code setBeforePublishPostProcessors(...)} — see
 * {@link com.example.producer.config.RabbitConfig#rabbitTemplate}.
 *
 * <p>Why moved (CONTEXT.md D-05): Phase 2's inline PRODUCER span body
 * here was structurally clean but conflated two concerns — span lifecycle
 * AND the publish call. Phase 3 separates them: the post-processor
 * handles the span + header inject (the cross-service propagation lesson
 * the workshop is built around); this file handles the business call
 * (build the message and convertAndSend). The smallest possible
 * step-02-traces → step-03-context-propagation git diff (ROADMAP SC #5)
 * deletes ~33 lines from this file.
 */
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
