package com.example.consumer.config;

import com.example.otel.amqp.TracingMessageListenerAdvice;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String QUEUE = "orders.created";

    @Bean
    Queue ordersCreatedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---- Phase 3 NEW: AMQP context propagation wiring ----

    /**
     * Phase 3: registers the consumer side of W3C AMQP context
     * propagation. {@link TracingMessageListenerAdvice} owns the entire
     * CONSUMER span lifecycle (CONTEXT.md D-09) — Phase 2's inline
     * CONSUMER span in {@code OrderListener.onOrder(...)} is deleted and
     * replaced by this advice that fires AROUND every
     * {@code @RabbitListener}-annotated method invocation.
     *
     * <p>Pure constructor wrapper per D-01 — the propagation classes
     * carry NO Spring annotations themselves; the wiring is explicit
     * here.
     */
    @Bean
    TracingMessageListenerAdvice tracingMessageListenerAdvice(OpenTelemetry openTelemetry,
                                                               Tracer tracer) {
        return new TracingMessageListenerAdvice(openTelemetry, tracer);
    }

    /**
     * Phase 3: Configurer-aided {@link SimpleRabbitListenerContainerFactory}
     * bean overriding Spring Boot's auto-created factory. The bean method
     * name MUST be exactly {@code rabbitListenerContainerFactory}
     * (lowercase r) — Pitfall #7. Spring Boot's
     * {@code RabbitAnnotationDrivenConfiguration.simpleRabbitListenerContainerFactory(...)}
     * uses {@code @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")},
     * which is an EXACT name match; renaming this bean would create two
     * factories and {@code @RabbitListener} would resolve the wrong one.
     *
     * <p><strong>Order matters (CONTEXT.md D-08 + Pitfall #5).</strong>
     * The Configurer applies any {@code spring.rabbitmq.listener.simple.*}
     * properties (concurrency, prefetch, etc.) FIRST. THEN we set the
     * tracing advice chain. FINALLY we flip {@code defaultRequeueRejected}
     * to {@code false}.
     *
     * <p><strong>setAdviceChain wiring (CONTEXT.md D-08, RESEARCH FLAG
     * #1 + #3).</strong> Spring AOP wraps
     * {@code ContainerDelegate.invokeListener(Channel, Object)} — the
     * advice's {@code MethodInvocation.getArguments()[1]} is the
     * {@code Message}. The advice chain runs SYNCHRONOUSLY on the same
     * thread as the user {@code @RabbitListener} method body, so the
     * {@code Scope.makeCurrent()} the advice opens IS visible to the
     * listener body.
     *
     * <p><strong>setDefaultRequeueRejected(false) (CONTEXT.md D-13).</strong>
     * On listener exception (e.g., the deterministic 10%-failure
     * {@code ProcessingFailedException} from APP-04 — see plan 03-04),
     * Spring AMQP NACKs without requeue. With NO Dead-Letter Exchange
     * configured (PROJECT.md excludes DLX), the broker drops the message.
     * This breaks the otherwise-infinite NACK-requeue loop that would
     * spam Tempo with error spans for the same poison message.
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            TracingMessageListenerAdvice tracingAdvice) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // 1. Apply Spring Boot defaults + spring.rabbitmq.listener.simple.* properties.
        configurer.configure(factory, connectionFactory);
        // 2. Wrap every listener invocation with the tracing advice.
        factory.setAdviceChain(tracingAdvice);
        // 3. APP-04 safety: drop failed messages instead of requeue
        //    (no DLX per PROJECT.md).
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
