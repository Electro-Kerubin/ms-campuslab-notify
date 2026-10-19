package org.campuslab.notify.domain.model;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comando de ticket de preparación de sala/equipo. Hoy lo publica ms-campuslab-bookings
 * con bookingId, resourceId, startTime y studentEmail; el resto (técnico, laboratorio,
 * requisitos) es opcional y lo completará quien asigne la preparación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepPayload {

    @NotNull(message = "bookingId es obligatorio")
    private Long bookingId;

    private Long resourceId;
    private String startTime;
    private String studentEmail;

    private String technicianId;
    private Long labId;
    private List<String> requirements;
}
