package org.campuslab.notify.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.campuslab.notify.dto.PrepPayload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreparationTicketService {
    private final EmailNotificationService emailNotificationService;

    public boolean send(PrepPayload payload) {
        if (payload.getTechnicianEmail() == null || payload.getTechnicianEmail().isBlank()) {
            return true;
        }
        String requirements = payload.getRequirements() == null ? "ninguno" : String.join(", ", payload.getRequirements());
        org.campuslab.notify.dto.EmailPayload email = new org.campuslab.notify.dto.EmailPayload();
        email.setTo(payload.getTechnicianEmail());
        email.setSubject("Ticket de preparación " + payload.getLabName());
        email.setBody("Reserva " + payload.getBookingId() + " | Requisitos: " + requirements);
        return emailNotificationService.send(email);
    }
}