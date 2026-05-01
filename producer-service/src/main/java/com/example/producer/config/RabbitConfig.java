package com.example.producer.config;

import com.example.otel.amqp.TracingMessagePostProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "orders";
    public static final String QUEUE = "orders.created";
    public static final String ROUTING_KEY = "order.created";

    @Bean
    DirectExchange ordersExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue ordersCreatedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding ordersBinding(Queue q, DirectExchange ex) {
        return BindingBuilder.bind(q).to(ex).with(ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---- Phase 3 NEW: AMQP context propagation wiring ----

    /**
     * Phase 3: registers the producer side of W3C AMQP context
     * propagation. {@link TracingMessagePostProcessor} owns the entire
     * PRODUCER span lifecycle (CONTEXT.md D-05) — Phase 2's inline
     * PRODUCER span in {@code OrderPublisher.publish(...)} is deleted and
     * replaced by this hook on the {@link RabbitTemplate} below.
     *
     * <p>Pure constructor wrapper per D-01: the propagation classes carry
     * NO Spring annotations themselves; the wiring is explicit here.
     */
    @Bean
    TracingMessagePostProcessor tracingMessagePostProcessor(OpenTelemetry openTelemetry,
                                                             Tracer tracer) {
        return new TracingMessagePostProcessor(openTelemetry, tracer);
    }

    /**
     * Phase 3: explicit {@link RabbitTemplate} bean overriding Spring
     * Boot's auto-created template. Required so we can register
     * {@link #tracingMessagePostProcessor} on
     * {@link RabbitTemplate#setBeforePublishPostProcessors} — the hook
     * that fires AFTER {@link Jackson2JsonMessageConverter} has produced
     * the message body but BEFORE the AMQP wire write, which is exactly
     * where W3C trace headers are injected (PITFALLS.md #12: never
     * subclass RabbitTemplate; never replace the converter).
     *
     * <p>Spring Boot's {@code RabbitAutoConfiguration} backs off because
     * its {@code ConditionalOnMissingBean(RabbitOperations.class)} guard
     * matches — {@link RabbitTemplate} implements {@code RabbitOperations}.
     *
     * <p>Bean order: {@link #jsonMessageConverter()} must be set BEFORE
     * the post-processor registers, otherwise the byte body Jackson
     * produces wouldn't carry the JSON content_type header that the
     * consumer's {@code Jackson2JsonMessageConverter} needs to
     * deserialise.
     */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter,
                                   TracingMessagePostProcessor tracingMpp) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setBeforePublishPostProcessors(tracingMpp);
        return template;
    }
}
