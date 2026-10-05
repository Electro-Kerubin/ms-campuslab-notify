package org.campuslab.notify.service;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.campuslab.notify.dto.MessageEnvelope;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationProcessingService {
    private final Cache<String, Boolean> processedEvents;
    private final Counter rejectedMessages;

    public NotificationProcessingService(
            @Value("${notify.idempotency-ttl:PT24H}") Duration ttl,
            MeterRegistry meterRegistry) {
        this.processedEvents = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(100_000).build();
        this.rejectedMessages = Counter.builder("notify.dlq.messages")
                .description("Messages rejected and routed to a dead-letter queue")
                .register(meterRegistry);
    }

    public boolean process(MessageEnvelope<?> envelope, BooleanSupplier action) {
        String eventId = envelope == null ? null : envelope.getEventId();
        if (eventId == null || eventId.isBlank() || envelope.getPayload() == null) {
            rejectedMessages.increment();
            throw new IllegalArgumentException("Envelope must contain eventId and payload");
        }
        if (processedEvents.getIfPresent(eventId) != null) {
            return false;
        }
        putMdc(envelope);
        try {
            if (!action.getAsBoolean()) {
                throw new IllegalStateException("Notification action was not completed");
            }
            processedEvents.put(eventId, Boolean.TRUE);
            return true;
        } catch (RuntimeException exception) {
            rejectedMessages.increment();
            throw exception;
        } finally {
            MDC.remove("eventId");
            MDC.remove("traceId");
            MDC.remove("correlationId");
        }
    }

    private void putMdc(MessageEnvelope<?> envelope) {
        MDC.put("eventId", envelope.getEventId());
        if (envelope.getTraceId() != null) MDC.put("traceId", envelope.getTraceId());
        if (envelope.getCorrelationId() != null) MDC.put("correlationId", envelope.getCorrelationId());
    }
}