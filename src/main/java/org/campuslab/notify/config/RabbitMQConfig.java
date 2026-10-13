package org.campuslab.notify.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

/**
 * Topología RabbitMQ de notify.
 *
 * IMPORTANTE: ms-campuslab-bookings declara las mismas colas (ver su
 * RabbitTopologyConfig). RabbitMQ rechaza (PRECONDITION_FAILED) redeclarar una
 * cola con argumentos distintos, así que cualquier cambio en los argumentos de
 * las colas se debe hacer idéntico en los dos servicios.
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    private static final String CMD_DIRECT_EXCHANGE = "cmd.direct";
    private static final String CMD_TOPIC_EXCHANGE = "cmd.topic";
    private static final String CMD_DEAD_EXCHANGE = "cmd.dead.dlx";

    public static final String EMAIL_QUEUE = "q.cmd.email";
    public static final String PREP_QUEUE = "q.cmd.prep";
    public static final String VOUCHER_QUEUE = "q.cmd.voucher";

    public static final String EMAIL_DLQ = "q.cmd.email.dlq";
    public static final String PREP_DLQ = "q.cmd.prep.dlq";
    public static final String VOUCHER_DLQ = "q.cmd.voucher.dlq";

    private static final String EMAIL_DLQ_ROUTING_KEY = "email.dlq";
    private static final String PREP_DLQ_ROUTING_KEY = "prep.dlq";
    private static final String VOUCHER_DLQ_ROUTING_KEY = "voucher.dlq";

    // ---------- Políticas de retención ----------

    // Un comando que nadie consume en 30 s expira y se manda a la DLQ
    private static final int MAIN_QUEUE_TTL_MS = 30_000;
    // Si la cola supera los 1000 mensajes, los más antiguos se mandan a la DLQ
    private static final int MAIN_QUEUE_MAX_LENGTH = 1000;
    // La DLQ conserva los mensajes fallidos 24 h para análisis y luego los borra
    private static final int DLQ_TTL_MS = 86_400_000;

    @Bean
    public DirectExchange commandDirectExchange() {
        return new DirectExchange(CMD_DIRECT_EXCHANGE);
    }

    @Bean
    public TopicExchange commandTopicExchange() {
        return new TopicExchange(CMD_TOPIC_EXCHANGE);
    }

    @Bean
    public DirectExchange commandDeadLetterExchange() {
        return new DirectExchange(CMD_DEAD_EXCHANGE);
    }

    @Bean
    public Queue emailQueue() {
        return mainQueue(EMAIL_QUEUE, EMAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue prepQueue() {
        return mainQueue(PREP_QUEUE, PREP_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue voucherQueue() {
        return mainQueue(VOUCHER_QUEUE, VOUCHER_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return deadLetterQueue(EMAIL_DLQ);
    }

    @Bean
    public Queue prepDeadLetterQueue() {
        return deadLetterQueue(PREP_DLQ);
    }

    @Bean
    public Queue voucherDeadLetterQueue() {
        return deadLetterQueue(VOUCHER_DLQ);
    }

    @Bean
    public Binding emailDirectBinding(Queue emailQueue, DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(commandDirectExchange)
                .with("email.send");
    }

    @Bean
    public Binding emailTopicBinding(Queue emailQueue, TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(commandTopicExchange)
                .with("email.*");
    }

    @Bean
    public Binding prepDirectBinding(Queue prepQueue, DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(prepQueue)
                .to(commandDirectExchange)
                .with("prep.ticket");
    }

    @Bean
    public Binding prepTopicBinding(Queue prepQueue, TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(prepQueue)
                .to(commandTopicExchange)
                .with("prep.#");
    }

    @Bean
    public Binding voucherDirectBinding(Queue voucherQueue, DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(voucherQueue)
                .to(commandDirectExchange)
                .with("voucher.gen");
    }

    @Bean
    public Binding voucherTopicBinding(Queue voucherQueue, TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(voucherQueue)
                .to(commandTopicExchange)
                .with("voucher.*");
    }

    @Bean
    public Binding emailDeadLetterBinding(
            Queue emailDeadLetterQueue,
            DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue)
                .to(commandDeadLetterExchange)
                .with(EMAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding prepDeadLetterBinding(
            Queue prepDeadLetterQueue,
            DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(prepDeadLetterQueue)
                .to(commandDeadLetterExchange)
                .with(PREP_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding voucherDeadLetterBinding(
            Queue voucherDeadLetterQueue,
            DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(voucherDeadLetterQueue)
                .to(commandDeadLetterExchange)
                .with(VOUCHER_DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Reintentos con backoff exponencial para los consumidores (1 s, 2 s, ... hasta max-interval).
     *
     * Se hacen a mano en vez de con spring.rabbitmq.listener.simple.retry porque los
     * consumidores usan ack manual: en ese modo Spring no hace nack cuando se agotan
     * los reintentos, y el mensaje quedaría sin confirmar. Aquí, al agotarse, el
     * consumidor hace basicNack(requeue=false) y el mensaje pasa a la DLQ.
     */
    @Bean
    public RetryTemplate consumerRetryTemplate(
            @Value("${campuslab.notify.retry.max-attempts:3}") int maxAttempts,
            @Value("${campuslab.notify.retry.initial-interval:1000}") long initialInterval,
            @Value("${campuslab.notify.retry.multiplier:2.0}") double multiplier,
            @Value("${campuslab.notify.retry.max-interval:10000}") long maxInterval) {
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .exponentialBackoff(initialInterval, multiplier, maxInterval)
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(
                            RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                        log.warn("[REINTENTO] intento {}/{} fallido: {}",
                                context.getRetryCount(), maxAttempts, throwable.getMessage());
                    }
                })
                .build();
    }

    // Cola principal: durable, con TTL, tope de largo y DLX para los mensajes que fallan o expiran
    private Queue mainQueue(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .ttl(MAIN_QUEUE_TTL_MS)
                .maxLength(MAIN_QUEUE_MAX_LENGTH)
                .deadLetterExchange(CMD_DEAD_EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    // DLQ: destino final de los mensajes fallidos, con retención de 24 h
    private Queue deadLetterQueue(String name) {
        return QueueBuilder.durable(name)
                .ttl(DLQ_TTL_MS)
                .build();
    }
}
