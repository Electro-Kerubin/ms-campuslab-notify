package org.campuslab.notify.dto;

import java.time.ZonedDateTime;
import lombok.Data;

@Data
public class VoucherPayload {
    private Long bookingId;
    private String studentEmail;
    private String studentName;
    private String resourceName;
    private String actionType;
    private ZonedDateTime date;
}