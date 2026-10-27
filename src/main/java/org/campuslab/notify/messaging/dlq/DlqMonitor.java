package org.campuslab.notify.messaging.dlq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.rabbitmq.client.GetResponse;
import org.campuslab.notify.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Procesa los mensajes que llegan a las DLQ (email, preparación y voucher): cada cierto tiempo los lee y deja en el log,
 * una sola vez por mensaje, la información para analizar la falla (ver {@link DlqMessageDescriber}).
 *
 * Los inspecciona SIN consumirlos: cada mensaje leído se devuelve a la cola ({@code basicNack} con requeue). Así
 * siguen disponibles para analizarlos o reprocesarlos desde la Management UI y {@code DlqStatusService} los sigue
 * contando para la alerta del frontend, hasta que el TTL de la DLQ (24 h) los elimina. Un listener que los
 * confirmara los borraría y se perdería esa evidencia.
 *
 * Los nombres de las DLQ salen de {@link MessagingProperties}; la frecuencia, de campuslab.notify.dlq-monitor.
 */
@Component
@ConditionalOnProperty(prefix = "campuslab.notify.dlq-monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DlqMonitor {

    private static final Logger log = LoggerFactory.getLogger(DlqMonitor.class);
    private static final int MAX_REMEMBERED = 5_000;

    private final RabbitTemplate rabbitTemplate;
    private final List<String> dlqs;
    private final int maxPerPoll;

    // Mensajes ya registrados (acotado: al llenarse se olvidan los más antiguos)
    private final Set<String> alreadyLogged = Collections.synchronizedSet(new LinkedHashSet<>());

    public DlqMonitor(RabbitTemplate rabbitTemplate, MessagingProperties properties,
                      @Value("${campuslab.notify.dlq-monitor.max-per-poll:200}") int maxPerPoll) {
        this.rabbitTemplate = rabbitTemplate;
        this.maxPerPoll = maxPerPoll;
        MessagingProperties.Queues queues = properties.queues();
        this.dlqs = List.of(queues.email().dlq(), queues.prep().dlq(), queues.voucher().dlq());
    }

    @Scheduled(
            initialDelayString = "${campuslab.notify.dlq-monitor.initial-delay-ms:15000}",
            fixedDelayString = "${campuslab.notify.dlq-monitor.interval-ms:30000}")
    public void poll() {
        for (String dlq : dlqs) {
            try {
                inspect(dlq);
            } catch (Exception ex) {
                // El monitor nunca debe tumbar la aplicación: si el broker no responde, lo intenta en la próxima vuelta
                log.warn("[DLQ] no se pudo revisar {}: {}", dlq, ex.getMessage());
            }
        }
    }

    /** Revisa una DLQ y devuelve cuántos mensajes NUEVOS registró. */
    int inspect(String dlq) {
        Integer newMessages = rabbitTemplate.execute(channel -> {
            List<GetResponse> fetched = new ArrayList<>();
            GetResponse message;
            while (fetched.size() < maxPerPoll && (message = channel.basicGet(dlq, false)) != null) {
                fetched.add(message);
            }
            if (fetched.isEmpty()) {
                return 0;
            }

            int logged = 0;
            for (GetResponse failed : fetched) {
                if (remember(DlqMessageDescriber.identity(dlq, failed))) {
                    log.warn(DlqMessageDescriber.describe(dlq, failed));
                    logged++;
                }
            }
            // Devuelve TODOS los mensajes leídos a la cola: la DLQ queda exactamente como estaba
            long lastTag = fetched.get(fetched.size() - 1).getEnvelope().getDeliveryTag();
            channel.basicNack(lastTag, true, true);

            if (logged > 0) {
                log.info("[DLQ] {}: {} mensaje(s) nuevo(s) registrado(s); {} en total esperan análisis{}",
                        dlq, logged, fetched.size(),
                        fetched.size() >= maxPerPoll ? " (se revisaron los primeros " + maxPerPoll + ")" : "");
            }
            return logged;
        });
        return newMessages == null ? 0 : newMessages;
    }

    // true si el mensaje no se había registrado antes
    private boolean remember(String identity) {
        synchronized (alreadyLogged) {
            if (!alreadyLogged.add(identity)) {
                return false;
            }
            if (alreadyLogged.size() > MAX_REMEMBERED) {
                Iterator<String> oldest = alreadyLogged.iterator();
                oldest.next();
                oldest.remove();
            }
            return true;
        }
    }
}
