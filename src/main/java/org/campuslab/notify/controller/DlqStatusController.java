package org.campuslab.notify.controller;

import java.util.Map;

import org.campuslab.notify.dto.DlqStatus;
import org.campuslab.notify.service.DlqStatusService;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno (lo consume el BFF, no el frontend directamente) con el
 * estado de las DLQ. Solo expone conteos de mensajes, nunca su contenido.
 */
@RestController
@RequestMapping("/api/notify")
public class DlqStatusController {

    private final DlqStatusService dlqStatusService;

    public DlqStatusController(DlqStatusService dlqStatusService) {
        this.dlqStatusService = dlqStatusService;
    }

    @GetMapping("/dlq")
    public DlqStatus getDlqStatus() {
        return dlqStatusService.getStatus();
    }

    // RabbitMQ no responde: 503 para que el BFF lo trate como "servicio no disponible"
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, String>> brokerUnavailable(AmqpException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "RabbitMQ no disponible"));
    }
}
