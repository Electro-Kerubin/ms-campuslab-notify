package org.campuslab.notify.service;

import org.campuslab.notify.domain.model.PrepPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lógica de negocio del ticket de preparación para el técnico. No sabe nada de RabbitMQ. */
@Service
public class PrepTicketService {

    private static final Logger log = LoggerFactory.getLogger(PrepTicketService.class);

    public void createTicket(PrepPayload payload) {
        log.info("Procesando preparación de la reserva {} (recurso {}) para técnico {} en laboratorio {}. Requisitos: {}",
                payload.getBookingId(), payload.getResourceId(), payload.getTechnicianId(),
                payload.getLabId(), payload.getRequirements());
    }
}
