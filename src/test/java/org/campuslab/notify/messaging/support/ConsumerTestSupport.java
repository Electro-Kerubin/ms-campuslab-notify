package org.campuslab.notify.messaging.support;

import jakarta.validation.Validation;
import org.campuslab.notify.config.ConsumerRetryConfig;
import org.springframework.retry.support.RetryTemplate;

/** Arma los colaboradores reales de los consumidores (solo el simulador de fallos se mockea). */
public final class ConsumerTestSupport {

    private ConsumerTestSupport() {
    }

    /** La política de reintentos de producción (incluida la exclusión de errores no recuperables), pero casi sin espera. */
    public static RetryTemplate fastRetry(int maxAttempts) {
        return new ConsumerRetryConfig().consumerRetryTemplate(maxAttempts, 1, 2.0, 5);
    }

    public static ConsumerSupport support(RetryTemplate retryTemplate, FailureSimulator failureSimulator) {
        return new ConsumerSupport(
                new MessageValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                new IdempotencyGuard(100),
                new RetryRunner(retryTemplate),
                failureSimulator);
    }
}
