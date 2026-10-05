package org.campuslab.notify.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.campuslab.notify.dto.MessageEnvelope;
import org.junit.jupiter.api.Test;

class NotificationProcessingServiceTest {
    private final NotificationProcessingService service =
            new NotificationProcessingService(Duration.ofHours(1), new SimpleMeterRegistry());

    @Test
    void ignoresDuplicateEventId() {
        MessageEnvelope<String> envelope = envelope("event-1", "payload");
        AtomicInteger calls = new AtomicInteger();

        assertTrue(service.process(envelope, () -> {
            calls.incrementAndGet();
            return true;
        }));
        assertTrue(!service.process(envelope, () -> {
            calls.incrementAndGet();
            return true;
        }));

        assertTrue(calls.get() == 1);
    }

    @Test
    void rejectsInvalidEnvelope() {
        MessageEnvelope<String> envelope = envelope(null, null);

        assertThrows(IllegalArgumentException.class, () -> service.process(envelope, () -> true));
    }

    private MessageEnvelope<String> envelope(String eventId, String payload) {
        MessageEnvelope<String> envelope = new MessageEnvelope<>();
        envelope.setEventId(eventId);
        envelope.setPayload(payload);
        return envelope;
    }
}