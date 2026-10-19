package org.campuslab.notify.messaging.consumer;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.exception.NonRecoverableMessageException;
import org.campuslab.notify.messaging.support.ConsumerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.backoff.BackOffInterruptedException;

/**
 * Política de confirmación (ACK) y manejo de errores de TODOS los consumidores de comandos.
 * Cada mensaje termina siempre en una confirmación explícita al broker (ack o nack): con
 * ack manual, un mensaje sin confirmar quedaría bloqueado hasta que se cierre el canal.
 *
 * <pre>
 *  Situación                                         Decisión                          Destino
 *  ------------------------------------------------  --------------------------------  ----------------------
 *  Procesado correctamente                           basicAck                          se elimina de la cola
 *  eventId ya procesado (re-entrega)                 basicAck, sin reprocesar          se elimina de la cola
 *  Mensaje vacío / payload inválido / error de       basicNack(requeue=false),         DLX -> DLQ
 *  negocio (NonRecoverableMessageException)          SIN reintentos
 *  Falla recuperable (timeout, servicio externo)     reintentos con backoff exponencial;
 *                                                    si se agotan: basicNack(requeue=false)   DLX -> DLQ
 *  Hilo interrumpido durante un reintento            basicNack(requeue=true)           vuelve a la cola
 *  (el servicio se está apagando)                    (no es culpa del mensaje)
 *  JSON ilegible (falla antes de llegar aquí)        lo rechaza el contenedor de Spring (ConditionalRejectingErrorHandler,
 *                                                    rejectManual=true por defecto)    DLX -> DLQ
 * </pre>
 *
 * Los mensajes que llegan a la DLQ no se consumen: se conservan 24 h para analizarlos y
 * {@code DlqStatusService} los cuenta para la alerta del frontend.
 *
 * Cada subclase corresponde a un dominio (email, preparación, voucher): declara su
 * {@code @RabbitListener} y delega el trabajo a su servicio de negocio en {@link #process}.
 */
public abstract class AbstractCommandConsumer<P> {

    private enum Outcome { ACK, DEAD_LETTER, REQUEUE }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String flow;
    private final ConsumerSupport support;

    protected AbstractCommandConsumer(String flow, ConsumerSupport support) {
        this.flow = flow;
        this.support = support;
    }

    /** Trabajo de negocio sobre un payload ya validado. Puede lanzar cualquier excepción. */
    protected abstract void process(P payload) throws Exception;

    /**
     * Procesa el mensaje y lo confirma al broker. Si la propia confirmación falla (canal
     * cerrado), la excepción sube al contenedor: RabbitMQ re-entrega el mensaje y la
     * idempotencia evita repetir el efecto.
     */
    protected final void handle(MessageEnvelope<P> envelope, Channel channel, long deliveryTag)
            throws IOException {
        switch (decide(envelope)) {
            case ACK -> channel.basicAck(deliveryTag, false);
            case DEAD_LETTER -> channel.basicNack(deliveryTag, false, false);
            case REQUEUE -> channel.basicNack(deliveryTag, false, true);
        }
    }

    private Outcome decide(MessageEnvelope<P> envelope) {
        try {
            support.validator().validate(envelope);

            String eventId = envelope.getEventId();
            if (support.idempotencyGuard().alreadyProcessed(flow, eventId)) {
                log.info("[{}] eventId {} ya procesado, se confirma sin reprocesar", flow, eventId);
                return Outcome.ACK;
            }

            support.retryRunner().run(() -> {
                support.failureSimulator().maybeFail(flow + " " + eventId);
                process(envelope.getPayload());
            });
            support.idempotencyGuard().markProcessed(flow, eventId);
            return Outcome.ACK;
        } catch (NonRecoverableMessageException exception) {
            log.error("[→ DLQ] [{}] error no recuperable, sin reintentos: {}", flow, exception.getMessage());
            return Outcome.DEAD_LETTER;
        } catch (BackOffInterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("[REQUEUE] [{}] interrumpido durante un reintento (apagado), el mensaje vuelve a la cola", flow);
            return Outcome.REQUEUE;
        } catch (Exception exception) {
            log.error("[→ DLQ] [{}] reintentos agotados, se envía a la DLQ", flow, exception);
            return Outcome.DEAD_LETTER;
        }
    }
}
