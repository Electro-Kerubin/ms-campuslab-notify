package com.campuslab.notify.application;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.campuslab.notify.domain.model.EmailPayload;
import com.campuslab.notify.domain.model.MessageEnvelope;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

/**
 * Reintentos, ack/nack e idempotencia del consumidor de emails
 * (los de preparación y voucher usan exactamente el mismo esquema).
 */
class NotificationConsumerTest {

    private static final long TAG = 7L;

    private final Channel channel = mock(Channel.class);
    private final FailureSimulator failureSimulator = mock(FailureSimulator.class);
    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        // 3 intentos como en application.yml, pero casi sin espera para que el test sea rápido
        RetryTemplate retryTemplate = RetryTemplate.builder().maxAttempts(3).fixedBackoff(1).build();
        consumer = new NotificationConsumer(new RetryRunner(retryTemplate), failureSimulator);
    }

    @Test
    void confirmaElMensajeCuandoSeProcesaBien() throws Exception {
        consumer.consumeEmail(envelope("evt-1"), channel, TAG);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void reintentaYConfirmaSiUnIntentoPosteriorFunciona() throws Exception {
        doThrow(new IllegalStateException("falla 1"))
                .doThrow(new IllegalStateException("falla 2"))
                .doNothing()
                .when(failureSimulator).maybeFail(anyString());

        consumer.consumeEmail(envelope("evt-2"), channel, TAG);

        verify(failureSimulator, times(3)).maybeFail(anyString());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void agotaLosReintentosYEnviaElMensajeALaDlqSinReencolarlo() throws Exception {
        doThrow(new IllegalStateException("siempre falla")).when(failureSimulator).maybeFail(anyString());

        consumer.consumeEmail(envelope("evt-3"), channel, TAG);

        verify(failureSimulator, times(3)).maybeFail(anyString());
        // nack con requeue=false: RabbitMQ lo manda al DLX y de ahí a la DLQ
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void ignoraUnMensajeYaProcesadoPeroLoConfirma() throws Exception {
        consumer.consumeEmail(envelope("evt-4"), channel, TAG);
        consumer.consumeEmail(envelope("evt-4"), channel, 8L);

        verify(failureSimulator, times(1)).maybeFail(anyString());
        verify(channel).basicAck(TAG, false);
        verify(channel).basicAck(8L, false);
    }

    @Test
    void unMensajeQueFallaNoQuedaMarcadoComoProcesado() throws Exception {
        doThrow(new IllegalStateException("falla")).doThrow(new IllegalStateException("falla"))
                .doThrow(new IllegalStateException("falla")).doNothing()
                .when(failureSimulator).maybeFail(anyString());

        consumer.consumeEmail(envelope("evt-5"), channel, TAG);   // agota reintentos -> DLQ
        consumer.consumeEmail(envelope("evt-5"), channel, 8L);    // reentrega: se procesa de verdad

        verify(channel).basicNack(TAG, false, false);
        verify(channel).basicAck(8L, false);
    }

    private static MessageEnvelope<EmailPayload> envelope(String eventId) {
        return MessageEnvelope.<EmailPayload>builder()
                .type("email.send")
                .eventId(eventId)
                .payload(EmailPayload.builder().to("alumno@duocuc.cl").subject("Reserva").body("hola").build())
                .build();
    }
}
