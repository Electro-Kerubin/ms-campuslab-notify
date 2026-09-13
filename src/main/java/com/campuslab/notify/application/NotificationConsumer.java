package com.campuslab.notify.application;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.campuslab.notify.domain.model.EmailPayload;
import com.campuslab.notify.domain.model.MessageEnvelope;
import com.campuslab.notify.domain.model.PrepPayload;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationConsumer {

    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    @RabbitListener(queues = "q.cmd.email")
    public void consumeEmail(
            MessageEnvelope<EmailPayload> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            String eventId = envelope.getEventId();
            if (processedEvents.contains(eventId)) {
                channel.basicAck(tag, false);
                return;
            }

            EmailPayload payload = envelope.getPayload();
            log.info("Procesando email para {}, asunto: {}", payload.getTo(), payload.getSubject());
            processedEvents.add(eventId);
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            log.error("Error procesando mensaje de email", exception);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = "q.cmd.prep")
    public void consumePreparation(
            MessageEnvelope<PrepPayload> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            String eventId = envelope.getEventId();
            if (processedEvents.contains(eventId)) {
                channel.basicAck(tag, false);
                return;
            }

            PrepPayload payload = envelope.getPayload();
            log.info("Procesando preparación para técnico {} en laboratorio {}. Requisitos: {}",
                    payload.getTechnicianId(), payload.getLabId(), payload.getRequirements());
            processedEvents.add(eventId);
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            log.error("Error procesando mensaje de preparación", exception);
            channel.basicNack(tag, false, false);
        }
    }
}