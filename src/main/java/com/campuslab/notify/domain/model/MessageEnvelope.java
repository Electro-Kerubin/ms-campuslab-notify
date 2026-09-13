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
public class MessageEnvelope<T> {

    private String type;
    private String eventId;
    private ZonedDateTime timestamp;
    private String traceId;
    private String correlationId;
    private T payload;
}