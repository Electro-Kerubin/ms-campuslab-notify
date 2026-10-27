package org.campuslab.notify.service;

import org.campuslab.notify.domain.model.VoucherPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lógica de negocio de la generación de vouchers PDF. No sabe nada de RabbitMQ. */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public void generateVoucher(VoucherPayload payload) {
        log.info("Generando PDF de {} para la reserva {}",
                payload.getActionType(), payload.getBookingId());
    }
}
