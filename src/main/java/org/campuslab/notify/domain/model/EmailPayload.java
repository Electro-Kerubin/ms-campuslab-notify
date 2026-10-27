package org.campuslab.notify.domain.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comando de email. Hoy lo publica ms-campuslab-bookings con bookingId, to, status y
 * motivo; subject y body son opcionales (si no vienen, el asunto sale del motivo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailPayload {

    private Long bookingId;

    @NotBlank(message = "to es obligatorio")
    @Email(message = "to debe ser un email válido")
    private String to;

    private String status;
    private String motivo;
    private String subject;
    private String body;
}
