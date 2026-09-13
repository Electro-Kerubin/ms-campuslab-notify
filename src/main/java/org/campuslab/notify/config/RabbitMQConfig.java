package com.campuslab.notify.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 1. Definición de Exchanges[cite: 5]
    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange("cmd.direct");
    }

    @Bean
    public TopicExchange cmdTopicExchange() {
        return new TopicExchange("cmd.topic");
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("cmd.dead.dlx");
    }

    // 2. Definición de Colas Principal con argumentos para enrutar a DLQ en caso de NACK[cite: 5]
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable("q.cmd.email")
                .withArgument("x-dead-letter-exchange", "cmd.dead.dlx")
                .withArgument("x-dead-letter-routing-key", "email.dlq")
                .build();
    }

    @Bean
    public Queue prepQueue() {
        return QueueBuilder.durable("q.cmd.prep")
                .withArgument("x-dead-letter-exchange", "cmd.dead.dlx")
                .withArgument("x-dead-letter-routing-key", "prep.dlq")
                .build();
    }

    @Bean
    public Queue voucherQueue() {
        return QueueBuilder.durable("q.cmd.voucher")
                .withArgument("x-dead-letter-exchange", "cmd.dead.dlx")
                .withArgument("x-dead-letter-routing-key", "voucher.dlq")
                .build();
    }

    // 3. Definición de las Dead Letter Queues (DLQ)[cite: 5]
    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable("q.cmd.email.dlq").build();
    }

    @Bean
    public Queue prepDlq() {
        return QueueBuilder.durable("q.cmd.prep.dlq").build();
    }

    @Bean
    public Queue voucherDlq() {
        return QueueBuilder.durable("q.cmd.voucher.dlq").build();
    }

    // 4. Bindings para la cola Email[cite: 5]
    @Bean public Binding emailDirectBinding() { return BindingBuilder.bind(emailQueue()).to(cmdDirectExchange()).with("email.send"); }
    @Bean public Binding emailTopicBinding() { return BindingBuilder.bind(emailQueue()).to(cmdTopicExchange()).with("email.*"); }
    @Bean public Binding emailDlqBinding() { return BindingBuilder.bind(emailDlq()).to(deadLetterExchange()).with("email.dlq"); }

    // 5. Bindings para la cola de Preparación (Prep)[cite: 5]
    @Bean public Binding prepDirectBinding() { return BindingBuilder.bind(prepQueue()).to(cmdDirectExchange()).with("prep.ticket"); }
    @Bean public Binding prepTopicBinding() { return BindingBuilder.bind(prepQueue()).to(cmdTopicExchange()).with("prep.#"); }
    @Bean public Binding prepDlqBinding() { return BindingBuilder.bind(prepDlq()).to(deadLetterExchange()).with("prep.dlq"); }

    // 6. Bindings para la cola Voucher[cite: 5]
    @Bean public Binding voucherDirectBinding() { return BindingBuilder.bind(voucherQueue()).to(cmdDirectExchange()).with("voucher.gen"); }
    @Bean public Binding voucherTopicBinding() { return BindingBuilder.bind(voucherQueue()).to(cmdTopicExchange()).with("voucher.*"); }
    @Bean public Binding voucherDlqBinding() { return BindingBuilder.bind(voucherDlq()).to(deadLetterExchange()).with("voucher.dlq"); }

    // 7. Conversor de mensajes JSON
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}