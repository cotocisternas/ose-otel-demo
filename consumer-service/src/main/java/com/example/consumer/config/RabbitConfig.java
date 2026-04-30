package com.example.consumer.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String QUEUE = "orders.created";

    @Bean Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }

    @Bean MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
