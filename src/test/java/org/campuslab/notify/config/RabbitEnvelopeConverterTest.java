package org.campuslab.notify.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.campuslab.notify.dto.MessageEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class RabbitEnvelopeConverterTest {
    @Test
    void convertsEnvelopeWithoutProducerTypeHeaderAndIgnoresUnknownFields() {
        RabbitMQConfig config = new RabbitMQConfig();
        Jackson2JsonMessageConverter converter = config.jackson2JsonMessageConverter();
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setInferredArgumentType(MessageEnvelope.class);
        Message message = new Message(
                "{\"eventId\":\"evt-1\",\"payload\":{\"to\":\"student@example.test\",\"extra\":true}}".getBytes(),
                properties);

        MessageEnvelope<?> envelope = (MessageEnvelope<?>) converter.fromMessage(message, MessageEnvelope.class);

        assertEquals("evt-1", envelope.getEventId());
    }
}