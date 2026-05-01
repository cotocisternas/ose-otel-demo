package com.example.consumer.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.consumer.config.RabbitConfig;
import com.example.consumer.domain.ProcessingService;

/**
 * AMQP listener — processes OrderCreated messages from the
 * {@code orders.created} queue.
 *
 * <p>Phase 3 made this method a thin 3-line pass-through. The CONSUMER
 * span and W3C trace context extraction are owned by
 * {@code com.example.otel.amqp.TracingMessageListenerAdvice}, which is
 * registered on the {@link RabbitListener}'s container factory via
 * {@code SimpleRabbitListenerContainerFactory.setAdviceChain(...)} —
 * see {@link com.example.consumer.config.RabbitConfig#rabbitListenerContainerFactory}.
 *
 * <p><strong>Why moved (CONTEXT.md D-09).</strong> Phase 2's inline
 * CONSUMER span body here was a deliberate pedagogical preview of
 * Phase 3's {@code propagator.extract(...)} line — but a span built
 * inline inside the listener body cannot capture deserialization or
 * framework-level errors that happen BEFORE the body runs. Lifting the
 * span into the advice chain catches the entire listener invocation
 * (RESEARCH.md FLAG #1).
 *
 * <p><strong>Scope context (CONTEXT.md D-09 + RESEARCH FLAG #1).</strong>
 * The advice's {@code Scope.makeCurrent()} is active when this method
 * body runs (synchronous, same-thread). So {@code Span.current()} here
 * IS the CONSUMER span — Phase 5's MDC injector will pick up the
 * correct {@code trace_id} / {@code span_id} for the LOG.info line
 * below without any additional code.
 */
@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;

    public OrderListener(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrder(Map<String, Object> message) {
        Object orderId = message.get("orderId");
        LOG.info("OrderCreated received: orderId={}", orderId);
        processingService.process(message);
    }
}
