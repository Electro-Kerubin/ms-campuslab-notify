package org.campuslab.notify.messaging.support;

import org.springframework.stereotype.Component;

/**
 * Colaboradores comunes de todos los consumidores de comandos: validación del
 * mensaje, idempotencia, reintentos y simulador de fallos (solo demo).
 */
@Component
public record ConsumerSupport(
        MessageValidator validator,
        IdempotencyGuard idempotencyGuard,
        RetryRunner retryRunner,
        FailureSimulator failureSimulator
) {
}
