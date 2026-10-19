package org.campuslab.notify.messaging.consumer;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.domain.model.PrepPayload;
import org.campuslab.notify.messaging.support.ConsumerSupport;
import org.campuslab.notify.service.PrepTicketService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Dominio preparación: tickets de preparación de sala/equipo para el técnico (cola prep). */
@Component
public class PrepTicketConsumer extends AbstractCommandConsumer<PrepPayload> {

    private final PrepTicketService prepTicketService;

    public PrepTicketConsumer(ConsumerSupport support, PrepTicketService prepTicketService) {
        super("prep", support);
        this.prepTicketService = prepTicketService;
    }

    @RabbitListener(queues = "${campuslab.messaging.queues.prep.name}", ackMode = "MANUAL")
    public void consume(
            MessageEnvelope<PrepPayload> envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(envelope, channel, deliveryTag);
    }

    @Override
    protected void process(PrepPayload payload) {
        prepTicketService.createTicket(payload);
    }
}
