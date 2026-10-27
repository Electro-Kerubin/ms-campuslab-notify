package org.campuslab.notify.config;

import org.campuslab.notify.config.MessagingProperties.Flow;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de notify: exchanges, colas y bindings de cada caso de uso.
 * Los nombres NO están aquí: vienen de application.yml (campuslab.messaging.*),
 * ver {@link MessagingProperties}.
 *
 * Rutas de mensajería (productor: ms-campuslab-bookings):
 *
 * <pre>
 *  caso de uso   exchange        routing key   cola principal    --(falla/expira)--> DLX --> DLQ
 *  email         cmd.direct      email.send    q.cmd.email                       cmd.dead.dlx  q.cmd.email.dlq   (email.dlq)
 *                cmd.topic       email.*
 *  prep          cmd.direct      prep.ticket   q.cmd.prep                        cmd.dead.dlx  q.cmd.prep.dlq    (prep.dlq)
 *                cmd.topic       prep.#
 *  voucher       cmd.direct      voucher.gen   q.cmd.voucher                     cmd.dead.dlx  q.cmd.voucher.dlq (voucher.dlq)
 *                cmd.topic       voucher.*
 * </pre>
 *
 * Cuándo se usa cada exchange:
 * <ul>
 *   <li><b>cmd.direct</b>: comandos punto a punto (email, preparación). Un solo destino; la routing key
 *       tiene que ser exactamente la del binding (email.send, prep.ticket).</li>
 *   <li><b>cmd.topic</b>: familias con subtipos (voucher). Los bindings son patrones (voucher.* recibe
 *       voucher.retiro y voucher.devolucion); otra cola puede enlazarse solo a voucher.devolucion y recibir
 *       esa misma publicación, algo que direct no permite.</li>
 * </ul>
 *
 * IMPORTANTE: ms-campuslab-bookings declara las mismas colas (ver su
 * RabbitTopologyConfig). RabbitMQ rechaza (PRECONDITION_FAILED) redeclarar una
 * cola con argumentos distintos, así que el bloque campuslab.messaging del
 * application.yml debe ser idéntico en los dos servicios.
 *
 * Aquí solo se declara la topología. El consumo está en messaging.consumer y la
 * política de reintentos en {@link ConsumerRetryConfig}.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMQConfig {

    private final MessagingProperties properties;

    public RabbitMQConfig(MessagingProperties properties) {
        this.properties = properties;
    }

    // ---------- Exchanges ----------

    @Bean
    public DirectExchange commandDirectExchange() {
        return new DirectExchange(properties.exchanges().direct());
    }

    @Bean
    public TopicExchange commandTopicExchange() {
        return new TopicExchange(properties.exchanges().topic());
    }

    // Recibe los mensajes que "mueren" en las colas principales y los reparte a cada DLQ
    @Bean
    public DirectExchange commandDeadLetterExchange() {
        return new DirectExchange(properties.exchanges().deadLetter());
    }

    // ---------- Colas principales (durables, con TTL, tope de largo y DLX) ----------

    @Bean
    public Queue emailQueue() {
        return mainQueue(properties.queues().email());
    }

    @Bean
    public Queue prepQueue() {
        return mainQueue(properties.queues().prep());
    }

    @Bean
    public Queue voucherQueue() {
        return mainQueue(properties.queues().voucher());
    }

    // ---------- DLQ: destino final de los mensajes fallidos ----------

    @Bean
    public Queue emailDeadLetterQueue() {
        return deadLetterQueue(properties.queues().email());
    }

    @Bean
    public Queue prepDeadLetterQueue() {
        return deadLetterQueue(properties.queues().prep());
    }

    @Bean
    public Queue voucherDeadLetterQueue() {
        return deadLetterQueue(properties.queues().voucher());
    }

    // ---------- Bindings: email ----------

    @Bean
    public Binding emailDirectBinding() {
        return BindingBuilder.bind(emailQueue())
                .to(commandDirectExchange())
                .with(properties.queues().email().directRoutingKey());
    }

    @Bean
    public Binding emailTopicBinding() {
        return BindingBuilder.bind(emailQueue())
                .to(commandTopicExchange())
                .with(properties.queues().email().topicRoutingKey());
    }

    @Bean
    public Binding emailDeadLetterBinding() {
        return BindingBuilder.bind(emailDeadLetterQueue())
                .to(commandDeadLetterExchange())
                .with(properties.queues().email().dlqRoutingKey());
    }

    // ---------- Bindings: preparación de sala ----------

    @Bean
    public Binding prepDirectBinding() {
        return BindingBuilder.bind(prepQueue())
                .to(commandDirectExchange())
                .with(properties.queues().prep().directRoutingKey());
    }

    @Bean
    public Binding prepTopicBinding() {
        return BindingBuilder.bind(prepQueue())
                .to(commandTopicExchange())
                .with(properties.queues().prep().topicRoutingKey());
    }

    @Bean
    public Binding prepDeadLetterBinding() {
        return BindingBuilder.bind(prepDeadLetterQueue())
                .to(commandDeadLetterExchange())
                .with(properties.queues().prep().dlqRoutingKey());
    }

    // ---------- Bindings: voucher PDF ----------

    @Bean
    public Binding voucherDirectBinding() {
        return BindingBuilder.bind(voucherQueue())
                .to(commandDirectExchange())
                .with(properties.queues().voucher().directRoutingKey());
    }

    @Bean
    public Binding voucherTopicBinding() {
        return BindingBuilder.bind(voucherQueue())
                .to(commandTopicExchange())
                .with(properties.queues().voucher().topicRoutingKey());
    }

    @Bean
    public Binding voucherDeadLetterBinding() {
        return BindingBuilder.bind(voucherDeadLetterQueue())
                .to(commandDeadLetterExchange())
                .with(properties.queues().voucher().dlqRoutingKey());
    }

    // ---------- Serialización ----------

    // Mensajes como JSON en vez del formato binario por defecto de Java
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------- Construcción de colas ----------

    // Cola principal: durable, con TTL, tope de largo y DLX para los mensajes que fallan o expiran.
    // maxLength(int) está deprecado, pero se mantiene a propósito: RabbitMQ compara también el
    // TIPO de cada argumento al redeclarar, y las colas ya existentes se declararon con enteros.
    @SuppressWarnings("deprecation")
    private Queue mainQueue(Flow flow) {
        return durable(flow.name())
                .ttl(properties.retention().queueTtlMs())
                .maxLength(properties.retention().queueMaxLength())
                .deadLetterExchange(properties.exchanges().deadLetter())
                .deadLetterRoutingKey(flow.dlqRoutingKey())
                .build();
    }

    // DLQ: durable, con retención para poder analizar los fallidos antes de que el TTL los borre
    private Queue deadLetterQueue(Flow flow) {
        return durable(flow.dlq())
                .ttl(properties.retention().dlqTtlMs())
                .build();
    }

    // Cola durable del tipo configurado (campuslab.messaging.queue-type): classic o quorum
    private QueueBuilder durable(String name) {
        QueueBuilder builder = QueueBuilder.durable(name);
        return properties.quorum() ? builder.quorum() : builder;
    }
}
