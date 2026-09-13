package com.campuslab.notify.domain.model;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherPayload {

    private Long bookingId;
    private String studentEmail;
    private String resourceName;
    private String actionType;
    private ZonedDateTime date;
}