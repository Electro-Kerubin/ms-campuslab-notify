package org.campuslab.notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String CMD_DIRECT_EXCHANGE = "cmd.direct";
    private static final String CMD_TOPIC_EXCHANGE = "cmd.topic";
    private static final String CMD_DEAD_EXCHANGE = "cmd.dead.dlx";

    private static final String EMAIL_QUEUE = "q.cmd.email";
    private static final String PREP_QUEUE = "q.cmd.prep";
    private static final String VOUCHER_QUEUE = "q.cmd.voucher";

    private static final String EMAIL_DLQ = "q.cmd.email.dlq";
    private static final String PREP_DLQ = "q.cmd.prep.dlq";
    private static final String VOUCHER_DLQ = "q.cmd.voucher.dlq";

    private static final String EMAIL_DLQ_ROUTING_KEY = "email.dlq";
    private static final String PREP_DLQ_ROUTING_KEY = "prep.dlq";
    private static final String VOUCHER_DLQ_ROUTING_KEY = "voucher.dlq";

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
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", CMD_DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue prepQueue() {
        return QueueBuilder.durable(PREP_QUEUE)
                .withArgument("x-dead-letter-exchange", CMD_DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PREP_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue voucherQueue() {
        return QueueBuilder.durable(VOUCHER_QUEUE)
                .withArgument("x-dead-letter-exchange", CMD_DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", VOUCHER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(EMAIL_DLQ).build();
    }

    @Bean
    public Queue prepDeadLetterQueue() {
        return QueueBuilder.durable(PREP_DLQ).build();
    }

    @Bean
    public Queue voucherDeadLetterQueue() {
        return QueueBuilder.durable(VOUCHER_DLQ).build();
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
}