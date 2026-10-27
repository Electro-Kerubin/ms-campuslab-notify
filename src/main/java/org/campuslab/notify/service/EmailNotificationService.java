package org.campuslab.notify.service;

import org.campuslab.notify.domain.model.EmailPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lógica de negocio del envío de emails al estudiante. No sabe nada de RabbitMQ. */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    public void send(EmailPayload payload) {
        // bookings no manda asunto: se usa el motivo del cambio de estado
        String subject = payload.getSubject() != null ? payload.getSubject() : payload.getMotivo();
        log.info("Procesando email para {}, asunto: {}", payload.getTo(), subject);
    }
}
