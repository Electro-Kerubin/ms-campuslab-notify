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
    private final RetryRunner retryRunner;
    private final FailureSimulator failureSimulator;
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    public VoucherConsumer(
            PdfGeneratorService pdfGeneratorService,
            RetryRunner retryRunner,
            FailureSimulator failureSimulator) {
        this.pdfGeneratorService = pdfGeneratorService;
        this.retryRunner = retryRunner;
        this.failureSimulator = failureSimulator;
    }

    @RabbitListener(queues = "${campuslab.rabbit.voucher.queue}")
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

            retryRunner.run(() -> {
                failureSimulator.maybeFail("voucher " + eventId);
                pdfGeneratorService.generateVoucher(envelope.getPayload());
            });
            processedEvents.add(eventId);
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            log.error("[→ DLX] No se pudo procesar el voucher, se envía a la DLQ", exception);
            channel.basicNack(tag, false, false);
        }
    }
}
