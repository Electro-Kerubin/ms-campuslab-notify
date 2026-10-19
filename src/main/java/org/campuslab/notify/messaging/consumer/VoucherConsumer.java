package org.campuslab.notify.messaging.consumer;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.domain.model.VoucherPayload;
import org.campuslab.notify.messaging.support.ConsumerSupport;
import org.campuslab.notify.service.PdfGeneratorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Dominio voucher: generación de PDF de vale de retiro / acta de devolución (cola voucher). */
@Component
public class VoucherConsumer extends AbstractCommandConsumer<VoucherPayload> {

    private final PdfGeneratorService pdfGeneratorService;

    public VoucherConsumer(ConsumerSupport support, PdfGeneratorService pdfGeneratorService) {
        super("voucher", support);
        this.pdfGeneratorService = pdfGeneratorService;
    }

    @RabbitListener(queues = "${campuslab.messaging.queues.voucher.name}", ackMode = "MANUAL")
    public void consume(
            MessageEnvelope<VoucherPayload> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(envelope, channel, deliveryTag);
    }

    @Override
    protected void process(VoucherPayload payload) {
        pdfGeneratorService.generateVoucher(payload);
    }
}
