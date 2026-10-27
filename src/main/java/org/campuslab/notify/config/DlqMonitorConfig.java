package org.campuslab.notify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas de la aplicación. Hoy solo las usa el monitor de las DLQ
 * ({@code messaging.dlq.DlqMonitor}), que revisa cada cierto tiempo los mensajes fallidos y los registra en el log.
 * La frecuencia se configura en application.yml, bajo campuslab.notify.dlq-monitor.
 */
@Configuration
@EnableScheduling
public class DlqMonitorConfig {
}
