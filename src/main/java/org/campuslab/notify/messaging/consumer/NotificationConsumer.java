package org.campuslab.notify.messaging.consumer;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.campuslab.notify.dto.EmailPayload;
import org.campuslab.notify.dto.MessageEnvelope;
import org.campuslab.notify.dto.PrepPayload;
import org.campuslab.notify.service.EmailNotificationService;
import org.campuslab.notify.service.NotificationProcessingService;
import org.campuslab.notify.service.PreparationTicketService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final NotificationProcessingService processingService;
    private final EmailNotificationService emailNotificationService;
    private final PreparationTicketService preparationTicketService;

    @RabbitListener(queues = "q.cmd.email", containerFactory = "rabbitListenerContainerFactory")
    public void consumeEmail(MessageEnvelope<EmailPayload> envelope, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        process(envelope, channel, tag, () -> emailNotificationService.send(envelope.getPayload()));
    }

    @RabbitListener(queues = "q.cmd.prep", containerFactory = "rabbitListenerContainerFactory")
    public void consumePreparation(MessageEnvelope<PrepPayload> envelope, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        process(envelope, channel, tag, () -> preparationTicketService.send(envelope.getPayload()));
    }

    private void process(MessageEnvelope<?> envelope, Channel channel, long tag, java.util.function.BooleanSupplier action)
            throws IOException {
        try {
            processingService.process(envelope, action);
            channel.basicAck(tag, false);
        } catch (RuntimeException exception) {
            log.error("Error procesando notificación; se reintentará o irá a DLQ", exception);
            throw exception;
        }
    }
}