package org.campuslab.notify.dto;

import java.time.ZonedDateTime;
import lombok.Data;

@Data
public class MessageEnvelope<T> {
    private String type;
    private String eventId;
    private ZonedDateTime timestamp;
    private String traceId;
    private String correlationId;
    private T payload;
}