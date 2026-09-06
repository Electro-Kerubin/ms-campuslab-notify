package org.campuslab.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Notify Service Application
 *
 * Punto de entrada para el microservicio de notificaciones.
 *
 * Responsabilidades:
 * - Consumir comandos de RabbitMQ (q.cmd.email, q.cmd.prep, q.cmd.voucher)
 * - Enviar emails a estudiantes (confirmación, aprobación, sala lista, devolución)
 * - Generar y enviar tickets de preparación a técnicos
 * - Generar PDFs de voucher/acta de devolución
 * - Implementar idempotencia y manejo de DLQ
 *
 * Nota: Este servicio no tiene BD propia ni expone endpoints públicos
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.campuslab.notify")
public class NotifyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyServiceApplication.class, args);
    }

}
