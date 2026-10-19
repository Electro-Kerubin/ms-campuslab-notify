package org.campuslab.notify.messaging.consumer;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.domain.model.PrepPayload;
import org.campuslab.notify.messaging.support.ConsumerTestSupport;
import org.campuslab.notify.messaging.support.FailureSimulator;
import org.campuslab.notify.service.PrepTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Dominio preparación: usa la misma política de ACK que email, aquí se prueba su cableado. */
class PrepTicketConsumerTest {

    private static final long TAG = 3L;

    private final Channel channel = mock(Channel.class);
    private final PrepTicketService prepTicketService = mock(PrepTicketService.class);
    private PrepTicketConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PrepTicketConsumer(
                ConsumerTestSupport.support(ConsumerTestSupport.fastRetry(3), mock(FailureSimulator.class)),
                prepTicketService);
    }

    @Test
    void creaElTicketYConfirma() throws Exception {
        PrepPayload payload = PrepPayload.builder().bookingId(9L).resourceId(2L).build();

        consumer.consume(envelope("prep-1", payload), channel, TAG);

        verify(prepTicketService).createTicket(payload);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void unTicketSinReservaVaALaDlq() throws Exception {
        consumer.consume(envelope("prep-2", PrepPayload.builder().resourceId(2L).build()), channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(prepTicketService);
    }

    private static MessageEnvelope<PrepPayload> envelope(String eventId, PrepPayload payload) {
        return MessageEnvelope.<PrepPayload>builder().type("prep.ticket").eventId(eventId).payload(payload).build();
    }
}
