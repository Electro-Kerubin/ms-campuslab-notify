package org.campuslab.notify.domain.model;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comando de generación de PDF (vale de retiro / acta de devolución).
 *
 * ms-campuslab-bookings publica el tipo de documento como "tipo"; aquí se llama
 * actionType, de ahí el alias (sin él, todos los vouchers llegarían sin tipo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherPayload {

    @NotNull(message = "bookingId es obligatorio")
    private Long bookingId;

    private String studentEmail;
    private String resourceName;

    @NotBlank(message = "actionType (tipo) es obligatorio")
    @JsonAlias("tipo")
    private String actionType;

    private ZonedDateTime date;
}
