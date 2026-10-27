package org.campuslab.notify.messaging.consumer;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.EmailPayload;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.exception.NonRecoverableMessageException;
import org.campuslab.notify.messaging.support.ConsumerTestSupport;
import org.campuslab.notify.messaging.support.FailureSimulator;
import org.campuslab.notify.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Política de ACK / errores compartida por todos los consumidores (AbstractCommandConsumer),
 * ejercitada a través del consumidor de email. Cada test corresponde a una fila de la tabla
 * de decisiones de la clase base.
 */
class EmailConsumerTest {

    private static final long TAG = 7L;

    private final Channel channel = mock(Channel.class);
    private final FailureSimulator failureSimulator = mock(FailureSimulator.class);
    private final EmailNotificationService emailService = mock(EmailNotificationService.class);
    private EmailConsumer consumer;

    @BeforeEach
    void setUp() {
        // 3 intentos como en application.yml, pero casi sin espera para que el test sea rápido
        consumer = new EmailConsumer(
                ConsumerTestSupport.support(ConsumerTestSupport.fastRetry(3), failureSimulator),
                emailService);
    }

    // ---------- ACK ----------

    @Test
    void confirmaElMensajeCuandoSeProcesaBien() throws Exception {
        consumer.consume(envelope("evt-1"), channel, TAG);

        verify(emailService).send(payload());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void ignoraUnMensajeYaProcesadoPeroLoConfirma() throws Exception {
        consumer.consume(envelope("evt-4"), channel, TAG);
        consumer.consume(envelope("evt-4"), channel, 8L);

        verify(emailService, times(1)).send(payload());
        verify(channel).basicAck(TAG, false);
        verify(channel).basicAck(8L, false);
    }

    // ---------- Error recuperable: reintentos, luego DLQ ----------

    @Test
    void reintentaYConfirmaSiUnIntentoPosteriorFunciona() throws Exception {
        doThrow(new IllegalStateException("falla 1"))
                .doThrow(new IllegalStateException("falla 2"))
                .doNothing()
                .when(failureSimulator).maybeFail(anyString());

        consumer.consume(envelope("evt-2"), channel, TAG);

        verify(failureSimulator, times(3)).maybeFail(anyString());
        verify(emailService, times(1)).send(payload());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void agotaLosReintentosYEnviaElMensajeALaDlqSinReencolarlo() throws Exception {
        doThrow(new IllegalStateException("siempre falla")).when(failureSimulator).maybeFail(anyString());

        consumer.consume(envelope("evt-3"), channel, TAG);

        verify(failureSimulator, times(3)).maybeFail(anyString());
        // nack con requeue=false: RabbitMQ lo manda al DLX y de ahí a la DLQ
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void unaFallaRecuperableDelServicioDeNegocioTambienSeReintenta() throws Exception {
        doThrow(new IllegalStateException("SMTP caído")).when(emailService).send(payload());

        consumer.consume(envelope("evt-6"), channel, TAG);

        verify(emailService, times(3)).send(payload());
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void unMensajeQueFallaNoQuedaMarcadoComoProcesado() throws Exception {
        doThrow(new IllegalStateException("falla")).doThrow(new IllegalStateException("falla"))
                .doThrow(new IllegalStateException("falla")).doNothing()
                .when(failureSimulator).maybeFail(anyString());

        consumer.consume(envelope("evt-5"), channel, TAG);   // agota reintentos -> DLQ
        consumer.consume(envelope("evt-5"), channel, 8L);    // reentrega: se procesa de verdad

        verify(channel).basicNack(TAG, false, false);
        verify(channel).basicAck(8L, false);
    }

    // ---------- Error NO recuperable: directo a la DLQ, sin reintentos ----------

    @Test
    void unPayloadInvalidoVaDirectoALaDlqSinReintentosNiProcesamiento() throws Exception {
        MessageEnvelope<EmailPayload> sinDestinatario = envelope("evt-7");
        sinDestinatario.getPayload().setTo(" ");

        consumer.consume(sinDestinatario, channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verifyNoInteractions(emailService, failureSimulator);
    }

    @Test
    void unEmailMalFormadoEsUnErrorNoRecuperable() throws Exception {
        MessageEnvelope<EmailPayload> malFormado = envelope("evt-8");
        malFormado.getPayload().setTo("esto-no-es-un-email");

        consumer.consume(malFormado, channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(emailService);
    }

    @Test
    void unMensajeSinEventIdNoSePuedeProcesarYVaALaDlq() throws Exception {
        consumer.consume(envelope(null), channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(emailService);
    }

    @Test
    void unMensajeSinPayloadVaALaDlq() throws Exception {
        MessageEnvelope<EmailPayload> sinPayload = envelope("evt-9");
        sinPayload.setPayload(null);

        consumer.consume(sinPayload, channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(emailService);
    }

    @Test
    void unMensajeVacioVaALaDlq() throws Exception {
        consumer.consume(null, channel, TAG);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(emailService);
    }

    @Test
    void unErrorDeNegocioNoRecuperableNoSeReintenta() throws Exception {
        doThrow(new NonRecoverableMessageException("destinatario dado de baja"))
                .when(emailService).send(payload());

        consumer.consume(envelope("evt-10"), channel, TAG);

        verify(emailService, times(1)).send(payload());   // un solo intento, no 3
        verify(channel).basicNack(TAG, false, false);
    }

    // ---------- Apagado del servicio: el mensaje no es el culpable ----------

    @Test
    void siSeInterrumpeDuranteUnReintentoElMensajeVuelveALaCola() throws Exception {
        doThrow(new IllegalStateException("falla")).when(failureSimulator).maybeFail(anyString());
        Thread.currentThread().interrupt();   // simula el apagado del contenedor durante el backoff
        try {
            consumer.consume(envelope("evt-11"), channel, TAG);
        } finally {
            Thread.interrupted();   // no contaminar a los demás tests
        }

        // requeue=true: no va a la DLQ porque el mensaje no es el que falló
        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    // ---------- datos de prueba ----------

    private static EmailPayload payload() {
        return EmailPayload.builder().bookingId(5L).to("alumno@duocuc.cl").motivo("Tu reserva fue APROBADA").build();
    }

    private static MessageEnvelope<EmailPayload> envelope(String eventId) {
        return MessageEnvelope.<EmailPayload>builder()
                .type("email.send")
                .eventId(eventId)
                .payload(payload())
                .build();
    }
}
