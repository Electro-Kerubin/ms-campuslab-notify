package org.campuslab.notify.messaging.consumer;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.campuslab.notify.dto.MessageEnvelope;
import org.campuslab.notify.dto.VoucherPayload;
import org.campuslab.notify.service.NotificationProcessingService;
import org.campuslab.notify.service.PdfGeneratorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoucherConsumer {
    private final NotificationProcessingService processingService;
    private final PdfGeneratorService pdfGeneratorService;

    @RabbitListener(queues = "q.cmd.voucher", containerFactory = "rabbitListenerContainerFactory")
    public void consumeVoucher(MessageEnvelope<VoucherPayload> envelope, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        processingService.process(envelope, () -> {
            pdfGeneratorService.generateVoucher(envelope.getPayload());
            return true;
        });
        channel.basicAck(tag, false);
    }
}