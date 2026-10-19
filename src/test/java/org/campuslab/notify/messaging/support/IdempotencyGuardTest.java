package org.campuslab.notify.messaging.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyGuardTest {

    @Test
    void recuerdaLosEventosProcesados() {
        IdempotencyGuard guard = new IdempotencyGuard(10);

        assertThat(guard.alreadyProcessed("email", "evt-1")).isFalse();
        guard.markProcessed("email", "evt-1");
        assertThat(guard.alreadyProcessed("email", "evt-1")).isTrue();
    }

    @Test
    void elMismoEventIdEnOtroFlujoNoCuentaComoDuplicado() {
        IdempotencyGuard guard = new IdempotencyGuard(10);
        guard.markProcessed("email", "evt-1");

        assertThat(guard.alreadyProcessed("voucher", "evt-1")).isFalse();
    }

    @Test
    void laMemoriaEstaAcotadaYOlvidaLosMasAntiguos() {
        IdempotencyGuard guard = new IdempotencyGuard(2);

        guard.markProcessed("email", "evt-1");
        guard.markProcessed("email", "evt-2");
        guard.markProcessed("email", "evt-3");

        assertThat(guard.alreadyProcessed("email", "evt-1")).isFalse();   // desalojado
        assertThat(guard.alreadyProcessed("email", "evt-2")).isTrue();
        assertThat(guard.alreadyProcessed("email", "evt-3")).isTrue();
    }
}
