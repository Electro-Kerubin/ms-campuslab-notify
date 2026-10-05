package org.campuslab.notify.dto;

import java.time.ZonedDateTime;
import java.util.List;
import lombok.Data;

@Data
public class PrepPayload {
    private String technicianId;
    private String technicianEmail;
    private String technicianName;
    private Long labId;
    private String labName;
    private Long bookingId;
    private ZonedDateTime startAt;
    private ZonedDateTime endAt;
    private List<String> requirements;
}