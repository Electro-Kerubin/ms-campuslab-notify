package org.campuslab.notify.messaging.support;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simula fallos aleatorios al procesar mensajes, solo para demostrar los
 * reintentos y la DLQ. Está apagado por defecto: se activa con
 * campuslab.notify.simulated-failure-rate (0.0 a 1.0), p. ej. NOTIFY_SIMULATED_FAILURE_RATE=0.5.
 */
@Component
public class FailureSimulator {

    private final double failureRate;

    public FailureSimulator(@Value("${campuslab.notify.simulated-failure-rate:0}") double failureRate) {
        this.failureRate = failureRate;
    }

    public void maybeFail(String description) {
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new IllegalStateException("Error simulado al procesar " + description);
        }
    }
}
