package org.campuslab.notify.dto;

import lombok.Data;

@Data
public class EmailPayload {
    private String to;
    private String subject;
    private String body;
    private String studentName;
    private String pushToken;
}