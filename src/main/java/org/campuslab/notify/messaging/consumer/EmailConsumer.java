package org.campuslab.notify.messaging.consumer;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.EmailPayload;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.messaging.support.ConsumerSupport;
import org.campuslab.notify.service.EmailNotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Dominio email: comandos de notificación al estudiante (cola email). */
@Component
public class EmailConsumer extends AbstractCommandConsumer<EmailPayload> {

    private final EmailNotificationService emailService;

    public EmailConsumer(ConsumerSupport support, EmailNotificationService emailService) {
        super("email", support);
        this.emailService = emailService;
    }

    @RabbitListener(queues = "${campuslab.messaging.queues.email.name}", ackMode = "MANUAL")
    public void consume(
            MessageEnvelope<EmailPayload> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(envelope, channel, deliveryTag);
    }

    @Override
    protected void process(EmailPayload payload) {
        emailService.send(payload);
    }
}
