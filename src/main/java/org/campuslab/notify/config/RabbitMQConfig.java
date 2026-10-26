package org.campuslab.notify.config;

import org.campuslab.notify.config.RabbitNamesProperties.Flow;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

/**
 * Topología RabbitMQ de notify. Los nombres de exchanges, colas y routing keys
 * vienen de {@code campuslab.rabbit} en application.yml ({@link RabbitNamesProperties}).
 *
 * IMPORTANTE: ms-campuslab-bookings declara las mismas colas (ver su
 * RabbitTopologyConfig). RabbitMQ rechaza (PRECONDITION_FAILED) redeclarar una
 * cola con argumentos distintos, así que cualquier cambio en los argumentos de
 * las colas se debe hacer idéntico en los dos servicios.
 */
@Configuration
@EnableConfigurationProperties(RabbitNamesProperties.class)
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    // ---------- Políticas de retención ----------

    // Un comando que nadie consume en 30 s expira y se manda a la DLQ
    private static final int MAIN_QUEUE_TTL_MS = 30_000;
    // Si la cola supera los 1000 mensajes, los más antiguos se mandan a la DLQ
    private static final int MAIN_QUEUE_MAX_LENGTH = 1000;
    // La DLQ conserva los mensajes fallidos 24 h para análisis y luego los borra
    private static final int DLQ_TTL_MS = 86_400_000;

    private final RabbitNamesProperties names;

    public RabbitMQConfig(RabbitNamesProperties names) {
        this.names = names;
    }

    // ---------- Exchanges ----------

    @Bean
    public DirectExchange commandDirectExchange() {
        return new DirectExchange(names.exchanges().direct());
    }

    @Bean
    public TopicExchange commandTopicExchange() {
        return new TopicExchange(names.exchanges().topic());
    }

    @Bean
    public DirectExchange commandDeadLetterExchange() {
        return new DirectExchange(names.exchanges().deadLetter());
    }

    // ---------- Colas principales ----------

    @Bean
    public Queue emailQueue() {
        return mainQueue(names.email());
    }

    @Bean
    public Queue prepQueue() {
        return mainQueue(names.prep());
    }

    @Bean
    public Queue voucherQueue() {
        return mainQueue(names.voucher());
    }

    // ---------- DLQ ----------

    @Bean
    public Queue emailDeadLetterQueue() {
        return deadLetterQueue(names.email());
    }

    @Bean
    public Queue prepDeadLetterQueue() {
        return deadLetterQueue(names.prep());
    }

    @Bean
    public Queue voucherDeadLetterQueue() {
        return deadLetterQueue(names.voucher());
    }

    // ---------- Bindings: cada cola se une al exchange direct y al topic ----------

    @Bean
    public Binding emailDirectBinding(Queue emailQueue, DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(commandDirectExchange)
                .with(names.email().routingKey());
    }

    @Bean
    public Binding emailTopicBinding(Queue emailQueue, TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(commandTopicExchange)
                .with(names.email().topicPattern());
    }

    @Bean
    public Binding prepDirectBinding(Queue prepQueue, DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(prepQueue)
                .to(commandDirectExchange)
                .with(names.prep().routingKey());
    }

    @Bean
    public Binding prepTopicBinding(Queue prepQueue, TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(prepQueue)
                .to(commandTopicExchange)
                .with(names.prep().topicPattern());
    }

    @Bean
    public Binding voucherDirectBinding(Queue voucherQueue, DirectExchange commandDirectExchange) {
        return BindingBuilder.bind(voucherQueue)
                .to(commandDirectExchange)
                .with(names.voucher().routingKey());
    }

    @Bean
    public Binding voucherTopicBinding(Queue voucherQueue, TopicExchange commandTopicExchange) {
        return BindingBuilder.bind(voucherQueue)
                .to(commandTopicExchange)
                .with(names.voucher().topicPattern());
    }

    // ---------- Bindings de las DLQ al exchange de dead letters ----------

    @Bean
    public Binding emailDeadLetterBinding(
            Queue emailDeadLetterQueue,
            DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue)
                .to(commandDeadLetterExchange)
                .with(names.email().deadLetterRoutingKey());
    }

    @Bean
    public Binding prepDeadLetterBinding(
            Queue prepDeadLetterQueue,
            DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(prepDeadLetterQueue)
                .to(commandDeadLetterExchange)
                .with(names.prep().deadLetterRoutingKey());
    }

    @Bean
    public Binding voucherDeadLetterBinding(
            Queue voucherDeadLetterQueue,
            DirectExchange commandDeadLetterExchange) {
        return BindingBuilder.bind(voucherDeadLetterQueue)
                .to(commandDeadLetterExchange)
                .with(names.voucher().deadLetterRoutingKey());
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
    private Queue mainQueue(Flow flow) {
        return QueueBuilder.durable(flow.queue())
                .ttl(MAIN_QUEUE_TTL_MS)
                .maxLength(MAIN_QUEUE_MAX_LENGTH)
                .deadLetterExchange(names.exchanges().deadLetter())
                .deadLetterRoutingKey(flow.deadLetterRoutingKey())
                .build();
    }

    // DLQ: destino final de los mensajes fallidos, con retención de 24 h
    private Queue deadLetterQueue(Flow flow) {
        return QueueBuilder.durable(flow.deadLetterQueue())
                .ttl(DLQ_TTL_MS)
                .build();
    }
}
