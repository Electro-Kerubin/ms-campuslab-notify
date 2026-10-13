package org.campuslab.notify.dto;

import java.time.Instant;
import java.util.List;

/**
 * Estado de las colas de mensajes fallidos (DLQ), pensado para alertas en el frontend.
 *
 * status: "OK" si no hay mensajes en ninguna DLQ, "ALERT" si hay al menos uno.
 */
public record DlqStatus(
        String status,
        int totalInDlq,
        List<QueueStatus> queues,
        Instant checkedAt
) {
    /** pending = mensajes esperando en la cola principal; failed = mensajes en su DLQ. */
    public record QueueStatus(String label, String queue, String dlq, int pending, int failed) {
    }
}
