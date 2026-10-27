package org.campuslab.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.campuslab.notify.config.MessagingProperties;
import org.campuslab.notify.config.MessagingProperties.Exchanges;
import org.campuslab.notify.config.MessagingProperties.Flow;
import org.campuslab.notify.config.MessagingProperties.Idempotency;
import org.campuslab.notify.config.MessagingProperties.Queues;
import org.campuslab.notify.config.MessagingProperties.Retention;
import org.campuslab.notify.dto.DlqStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

class DlqStatusServiceTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private DlqStatusService service;

    @BeforeEach
    void setUp() {
        MessagingProperties properties = new MessagingProperties(
                new Exchanges("d", "t", "x"),
                new Queues(
                        new Flow("q.mail", "q.mail.dlq", "k", "k", "k"),
                        new Flow("q.prep", "q.prep.dlq", "k", "k", "k"),
                        new Flow("q.pdf", "q.pdf.dlq", "k", "k", "k")),
                new Retention(1, 1, 1),
                new Idempotency(1), "classic");
        service = new DlqStatusService(amqpAdmin, properties);
    }

    @Test
    void estadoOkSiNingunaDlqTieneMensajes() {
        // una cola que no existe (getQueueInfo = null) cuenta como vacía
        DlqStatus status = service.getStatus();

        assertThat(status.status()).isEqualTo("OK");
        assertThat(status.totalInDlq()).isZero();
        assertThat(status.queues()).hasSize(3);
    }

    @Test
    void alertaSiAlgunaDlqTieneMensajesYLeeLosNombresDeLaConfiguracion() {
        when(amqpAdmin.getQueueInfo("q.mail")).thenReturn(new QueueInformation("q.mail", 4, 1));
        when(amqpAdmin.getQueueInfo("q.prep.dlq")).thenReturn(new QueueInformation("q.prep.dlq", 2, 0));

        DlqStatus status = service.getStatus();

        assertThat(status.status()).isEqualTo("ALERT");
        assertThat(status.totalInDlq()).isEqualTo(2);
        assertThat(status.queues()).anySatisfy(q -> {
            assertThat(q.queue()).isEqualTo("q.mail");
            assertThat(q.pending()).isEqualTo(4);
            assertThat(q.failed()).isZero();
        });
        assertThat(status.queues()).anySatisfy(q -> {
            assertThat(q.dlq()).isEqualTo("q.prep.dlq");
            assertThat(q.failed()).isEqualTo(2);
        });
    }
}
