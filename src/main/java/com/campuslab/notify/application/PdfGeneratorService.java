package com.campuslab.notify.application;

import com.campuslab.notify.domain.model.VoucherPayload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PdfGeneratorService {

    public void generateVoucher(VoucherPayload payload) {
        log.info("Generando PDF de {} para la reserva {}",
                payload.getActionType(), payload.getBookingId());
    }
}