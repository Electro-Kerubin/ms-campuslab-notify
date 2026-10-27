package org.campuslab.notify.messaging.support;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.campuslab.notify.domain.model.MessageEnvelope;
import org.campuslab.notify.exception.NonRecoverableMessageException;
import org.springframework.stereotype.Component;

/**
 * Valida el envelope y su payload (anotaciones de jakarta.validation en domain.model).
 * Un mensaje inválido no se arregla reintentando, por eso se informa como
 * {@link NonRecoverableMessageException}: el consumidor lo manda directo a la DLQ.
 */
@Component
public class MessageValidator {

    private final Validator validator;

    public MessageValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(MessageEnvelope<?> envelope) {
        if (envelope == null) {
            throw new NonRecoverableMessageException("El mensaje llegó vacío");
        }
        Set<? extends ConstraintViolation<?>> violations = validator.validate(envelope);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new NonRecoverableMessageException("Mensaje inválido (" + detail + ")");
        }
    }
}
