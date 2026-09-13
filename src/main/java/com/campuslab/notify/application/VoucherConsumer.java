package com.campuslab.notify.application;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.campuslab.notify.domain.model.MessageEnvelope;
import com.campuslab.notify.domain.model.VoucherPayload;
import com.rabbitmq.client.Channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VoucherConsumer {

    private final PdfGeneratorService pdfGeneratorService;
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    public VoucherConsumer(PdfGeneratorService pdfGeneratorService) {
        this.pdfGeneratorService = pdfGeneratorService;
    }

    @RabbitListener(queues = "q.cmd.voucher")
    public void consumeVoucher(
            MessageEnvelope<VoucherPayload> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            String eventId = envelope.getEventId();
            if (processedEvents.contains(eventId)) {
                channel.basicAck(tag, false);
                return;
            }

            pdfGeneratorService.generateVoucher(envelope.getPayload());
            processedEvents.add(eventId);
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            log.error("Error procesando mensaje de voucher", exception);
            channel.basicNack(tag, false, false);
        }
    }
}