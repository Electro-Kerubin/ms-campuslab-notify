package org.campuslab.notify.messaging.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.campuslab.notify.config.MessagingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Recuerda los eventId ya procesados para que una re-entrega (RabbitMQ entrega
 * "al menos una vez") no repita el efecto: el consumidor solo vuelve a confirmar.
 *
 * Guarda como máximo {@code max-tracked-events} ids (los más antiguos se olvidan),
 * así la memoria no crece sin límite. Es por instancia: con varias réplicas de notify
 * habría que moverlo a un almacén compartido (Redis, base de datos).
 */
@Component
public class IdempotencyGuard {

    private final Set<String> processed;

    // @Autowired: hay dos constructores y Spring necesita saber cuál usar
    @Autowired
    public IdempotencyGuard(MessagingProperties properties) {
        this(properties.idempotency().maxTrackedEvents());
    }

    IdempotencyGuard(int maxTrackedEvents) {
        Map<String, Boolean> lru = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > maxTrackedEvents;
            }
        };
        this.processed = Collections.synchronizedSet(Collections.newSetFromMap(lru));
    }

    /** flow separa los ids por caso de uso (email, prep, voucher). */
    public boolean alreadyProcessed(String flow, String eventId) {
        return processed.contains(key(flow, eventId));
    }

    public void markProcessed(String flow, String eventId) {
        processed.add(key(flow, eventId));
    }

    private static String key(String flow, String eventId) {
        return flow + ":" + eventId;
    }
}
