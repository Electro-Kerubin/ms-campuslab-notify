package org.campuslab.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Validation;
import org.campuslab.notify.domain.model.EmailPayload;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.domain.model.PrepPayload;
import org.campuslab.notify.domain.model.VoucherPayload;
import org.campuslab.notify.messaging.support.MessageValidator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Contrato con el productor: ms-campuslab-bookings publica un EventEnvelope con un payload
 * en forma de Map. Aquí se serializa igual (mismo conversor) y se comprueba que notify lo
 * entiende y lo da por válido. Si alguien renombra un campo de un lado, esto lo detecta.
 */
class PayloadContractTest {

    /** Copia de bookings.messaging.producer.EventEnvelope. */
    private record EventEnvelope(String type, String eventId, Instant timestamp, String traceId,
                                 String correlationId, Map<String, Object> payload) {
        static EventEnvelope of(String type, Map<String, Object> payload) {
            String id = UUID.randomUUID().toString();
            return new EventEnvelope(type, id, Instant.now(), id, id, payload);
        }
    }

    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    private final MessageValidator validator =
            new MessageValidator(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void notifyEntiendeElComandoDeEmailDeBookings() {
        Message wire = publish("email.send", Map.of(
                "bookingId", 5, "to", "alumno@duocuc.cl", "status", "APROBADA", "motivo", "Tu reserva fue APROBADA"));

        MessageEnvelope<EmailPayload> envelope = read(wire, new ParameterizedTypeReference<>() { });

        assertThatCode(() -> validator.validate(envelope)).doesNotThrowAnyException();
        assertThat(envelope.getPayload().getTo()).isEqualTo("alumno@duocuc.cl");
        assertThat(envelope.getPayload().getMotivo()).isEqualTo("Tu reserva fue APROBADA");
        assertThat(envelope.getEventId()).isNotBlank();
        assertThat(envelope.getTimestamp()).isNotNull();
    }

    @Test
    void notifyEntiendeElComandoDePreparacionDeBookings() {
        Message wire = publish("prep.ticket", Map.of(
                "bookingId", 5, "resourceId", 12, "startTime", "2026-10-20T10:00", "studentEmail", "alumno@duocuc.cl"));

        MessageEnvelope<PrepPayload> envelope = read(wire, new ParameterizedTypeReference<>() { });

        assertThatCode(() -> validator.validate(envelope)).doesNotThrowAnyException();
        assertThat(envelope.getPayload().getBookingId()).isEqualTo(5L);
        assertThat(envelope.getPayload().getResourceId()).isEqualTo(12L);
    }

    @Test
    void notifyEntiendeElComandoDeVoucherDeBookingsQueLlamaTipoAlTipoDeDocumento() {
        Message wire = publish("voucher.gen", Map.of(
                "bookingId", 5, "tipo", "VALE_RETIRO", "studentEmail", "alumno@duocuc.cl"));

        MessageEnvelope<VoucherPayload> envelope = read(wire, new ParameterizedTypeReference<>() { });

        assertThatCode(() -> validator.validate(envelope)).doesNotThrowAnyException();
        assertThat(envelope.getPayload().getActionType()).isEqualTo("VALE_RETIRO");
    }

    private Message publish(String type, Map<String, Object> payload) {
        return converter.toMessage(EventEnvelope.of(type, payload), new MessageProperties());
    }

    @SuppressWarnings("unchecked")
    private <T> MessageEnvelope<T> read(Message message, ParameterizedTypeReference<MessageEnvelope<T>> type) {
        return (MessageEnvelope<T>) converter.fromMessage(message, type);
    }
}
