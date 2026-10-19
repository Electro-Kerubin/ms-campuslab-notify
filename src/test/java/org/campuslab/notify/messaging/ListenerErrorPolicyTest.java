package org.campuslab.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.listener.support.ContainerUtils;
import org.springframework.amqp.support.converter.MessageConversionException;

/**
 * Documenta (y fija) cómo reacciona el contenedor de Spring AMQP en modo de ack MANUAL,
 * porque de eso depende el diseño de los consumidores:
 *
 *  - Un mensaje ilegible (JSON roto) NUNCA llega al listener: el manejador de errores por
 *    defecto lo rechaza sin reencolar y con rejectManual=true, así que va a la DLQ.
 *  - Cualquier otra excepción que escape del listener NO produce ack ni nack: el mensaje
 *    quedaría sin confirmar. Por eso AbstractCommandConsumer captura todo y decide siempre.
 */
class ListenerErrorPolicyTest {

    private static final Log LOG = LogFactory.getLog(ListenerErrorPolicyTest.class);

    private final ConditionalRejectingErrorHandler errorHandler = new ConditionalRejectingErrorHandler();
    private final Message message = new Message(new byte[0]);

    @Test
    void unMensajeIlegibleSeRechazaSinReencolarAunConAckManual() {
        var illegible = new ListenerExecutionFailedException(
                "falla", new MessageConversionException("JSON ilegible"), message);

        assertThatThrownBy(() -> errorHandler.handleError(illegible))
                .isInstanceOfSatisfying(AmqpRejectAndDontRequeueException.class, rejection -> {
                    assertThat(rejection.isRejectManual()).isTrue();
                    // es lo que el contenedor consulta en modo MANUAL para hacer el nack
                    assertThat(ContainerUtils.isRejectManual(rejection)).isTrue();
                    assertThat(ContainerUtils.shouldRequeue(false, rejection, LOG)).isFalse();
                });
    }

    @Test
    void unaExcepcionCualquieraQueEscapaDelListenerNoGeneraNackAutomatico() {
        var inesperada = new ListenerExecutionFailedException("falla", new IllegalStateException("boom"), message);

        assertThatCode(() -> errorHandler.handleError(inesperada)).doesNotThrowAnyException();
        assertThat(ContainerUtils.isRejectManual(inesperada)).isFalse();
    }
}
