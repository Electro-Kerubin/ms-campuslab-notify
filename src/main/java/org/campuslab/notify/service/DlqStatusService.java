package org.campuslab.notify.service;

import java.time.Instant;
import java.util.List;

import org.campuslab.notify.config.MessagingProperties;
import org.campuslab.notify.dto.DlqStatus;
import org.campuslab.notify.dto.DlqStatus.QueueStatus;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Service;

/**
 * Consulta a RabbitMQ cuántos mensajes hay en cada cola principal y en su DLQ.
 * Los mensajes de la DLQ no se consumen: quedan ahí 24 h para poder analizarlos
 * (por ejemplo desde la Management UI) hasta que los borra el TTL. Cada uno se
 * registra en el log una vez,
 * con su origen y motivo, por {@code messaging.dlq.DlqMonitor}.
 */
@Service
public class DlqStatusService {

    private record Flow(String label, String queue, String dlq) {
    }

    private final List<Flow> flows;
    private final List<Flow> flows;
    private final AmqpAdmin amqpAdmin;

    public DlqStatusService(AmqpAdmin amqpAdmin, MessagingProperties properties) {
        this.amqpAdmin = amqpAdmin;
        MessagingProperties.Queues queues = properties.queues();
        this.flows = List.of(
                new Flow("Email", queues.email().name(), queues.email().dlq()),
                new Flow("Preparación", queues.prep().name(), queues.prep().dlq()),
                new Flow("Voucher", queues.voucher().name(), queues.voucher().dlq()));
    }

    public DlqStatus getStatus() {
        List<QueueStatus> queues = flows.stream()
        List<QueueStatus> queues = flows.stream()
                .map(flow -> new QueueStatus(
                        flow.label(), flow.queue(), flow.dlq(),
                        messageCount(flow.queue()), messageCount(flow.dlq())))
                .toList();

        int totalInDlq = queues.stream().mapToInt(QueueStatus::failed).sum();
        return new DlqStatus(totalInDlq > 0 ? "ALERT" : "OK", totalInDlq, queues, Instant.now());
    }

    // getQueueInfo devuelve null si la cola no existe
    private int messageCount(String queue) {
        QueueInformation info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }
}
