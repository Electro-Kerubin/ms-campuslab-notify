package org.campuslab.notify.domain.model;

import java.time.ZonedDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope común de todos los mensajes (requisito del proyecto). eventId y payload
 * son obligatorios: sin eventId no hay idempotencia posible y sin payload no hay qué procesar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEnvelope<T> {

    private String type;

    @NotBlank(message = "eventId es obligatorio")
    private String eventId;

    private ZonedDateTime timestamp;
    private String traceId;
    private String correlationId;

    @NotNull(message = "payload es obligatorio")
    @Valid
    private T payload;
}
