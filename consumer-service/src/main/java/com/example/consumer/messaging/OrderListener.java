package com.example.consumer.messaging;

import com.example.consumer.config.RabbitConfig;
import com.example.consumer.domain.ProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;

    public OrderListener(ProcessingService processingService) {
        this.processingService = processingService;
    }

    // APP-03: receive OrderCreated and simulate downstream domain work.
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrder(Map<String, Object> message) {
        Object orderId = message.get("orderId");
        LOG.info("OrderCreated received: orderId={}", orderId);
        processingService.process(message);
    }
}
