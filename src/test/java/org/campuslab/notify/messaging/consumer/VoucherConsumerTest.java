package org.campuslab.notify.messaging.consumer;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.domain.model.VoucherPayload;
import org.campuslab.notify.messaging.support.ConsumerTestSupport;
import org.campuslab.notify.messaging.support.FailureSimulator;
import org.campuslab.notify.service.PdfGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Dominio voucher: usa la misma política de ACK que email, aquí se prueba su cableado. */
class VoucherConsumerTest {

    private static final long TAG = 5L;

    private final Channel channel = mock(Channel.class);
    private final PdfGeneratorService pdfGeneratorService = mock(PdfGeneratorService.class);
    private VoucherConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new VoucherConsumer(
                ConsumerTestSupport.support(ConsumerTestSupport.fastRetry(3), mock(FailureSimulator.class)),
                pdfGeneratorService);
    }

    @Test
    void generaElPdfYConfirma() throws Exception {
        VoucherPayload payload = VoucherPayload.builder().bookingId(9L).actionType("VALE_RETIRO").build();

        consumer.consume(envelope("v-1", payload), channel, TAG);

        verify(pdfGeneratorService).generateVoucher(payload);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void unVoucherSinTipoDeDocumentoVaALaDlq() throws Exception {
        consumer.consume(envelope("v-2", VoucherPayload.builder().bookingId(9L).build()), channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(pdfGeneratorService);
    }

    private static MessageEnvelope<VoucherPayload> envelope(String eventId, VoucherPayload payload) {
        return MessageEnvelope.<VoucherPayload>builder().type("voucher.gen").eventId(eventId).payload(payload).build();
    }
}
