package org.campuslab.notify.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class RabbitMQConfigTest {
    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void declaresExactDeadLetterRoutingKeys() {
        Queue email = config.emailQueue();
        Queue prep = config.prepQueue();
        Queue voucher = config.voucherQueue();

        assertEquals("q.cmd.email.dlq", email.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("q.cmd.prep.dlq", prep.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("q.cmd.voucher.dlq", voucher.getArguments().get("x-dead-letter-routing-key"));
    }
}