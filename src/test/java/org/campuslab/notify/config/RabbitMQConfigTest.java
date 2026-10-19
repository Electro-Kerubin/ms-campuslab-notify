package org.campuslab.notify.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.campuslab.notify.config.MessagingProperties.Exchanges;
import org.campuslab.notify.config.MessagingProperties.Flow;
import org.campuslab.notify.config.MessagingProperties.Idempotency;
import org.campuslab.notify.config.MessagingProperties.Queues;
import org.campuslab.notify.config.MessagingProperties.Retention;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Queue;

/** La topología se arma solo con lo que dicen las propiedades: ningún nombre está escrito en la clase. */
class RabbitMQConfigTest {

    private static final Flow EMAIL = new Flow("q.a", "q.a.dlq", "a.direct", "a.*", "a.dlq");
    private static final Flow PREP = new Flow("q.b", "q.b.dlq", "b.direct", "b.#", "b.dlq");
    private static final Flow VOUCHER = new Flow("q.c", "q.c.dlq", "c.direct", "c.*", "c.dlq");

    private final RabbitMQConfig config = new RabbitMQConfig(new MessagingProperties(
            new Exchanges("x.direct", "x.topic", "x.dead"),
            new Queues(EMAIL, PREP, VOUCHER),
            new Retention(1234, 99, 5678),
            new Idempotency(10)));

    @Test
    void losExchangesTomanSuNombreDeLasPropiedades() {
        assertThat(config.commandDirectExchange().getName()).isEqualTo("x.direct");
        assertThat(config.commandTopicExchange().getName()).isEqualTo("x.topic");
        assertThat(config.commandDeadLetterExchange().getName()).isEqualTo("x.dead");
    }

    @Test
    void cadaColaPrincipalEsDurableYEnviaSusFallidosALaDlq() {
        assertMainQueue(config.emailQueue(), EMAIL);
        assertMainQueue(config.prepQueue(), PREP);
        assertMainQueue(config.voucherQueue(), VOUCHER);
    }

    @Test
    void cadaDlqEsDurableYConservaLosMensajesElTiempoConfigurado() {
        assertDeadLetterQueue(config.emailDeadLetterQueue(), "q.a.dlq");
        assertDeadLetterQueue(config.prepDeadLetterQueue(), "q.b.dlq");
        assertDeadLetterQueue(config.voucherDeadLetterQueue(), "q.c.dlq");
    }

    @Test
    void cadaCasoDeUsoTieneBindingDirectTopicYDeDlq() {
        assertBinding(config.emailDirectBinding(), "q.a", "x.direct", "a.direct");
        assertBinding(config.emailTopicBinding(), "q.a", "x.topic", "a.*");
        assertBinding(config.emailDeadLetterBinding(), "q.a.dlq", "x.dead", "a.dlq");

        assertBinding(config.prepDirectBinding(), "q.b", "x.direct", "b.direct");
        assertBinding(config.prepTopicBinding(), "q.b", "x.topic", "b.#");
        assertBinding(config.prepDeadLetterBinding(), "q.b.dlq", "x.dead", "b.dlq");

        assertBinding(config.voucherDirectBinding(), "q.c", "x.direct", "c.direct");
        assertBinding(config.voucherTopicBinding(), "q.c", "x.topic", "c.*");
        assertBinding(config.voucherDeadLetterBinding(), "q.c.dlq", "x.dead", "c.dlq");
    }

    private static void assertMainQueue(Queue queue, Flow flow) {
        assertThat(queue.getName()).isEqualTo(flow.name());
        assertThat(queue.isDurable()).isTrue();
        Map<String, Object> args = queue.getArguments();
        assertThat(args)
                .containsEntry("x-message-ttl", 1234)
                .containsEntry("x-max-length", 99)
                .containsEntry("x-dead-letter-exchange", "x.dead")
                .containsEntry("x-dead-letter-routing-key", flow.dlqRoutingKey());
    }

    private static void assertDeadLetterQueue(Queue queue, String name) {
        assertThat(queue.getName()).isEqualTo(name);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5678);
    }

    private static void assertBinding(Binding binding, String queue, String exchange, String routingKey) {
        assertThat(binding.getDestinationType()).isEqualTo(DestinationType.QUEUE);
        assertThat(binding.getDestination()).isEqualTo(queue);
        assertThat(binding.getExchange()).isEqualTo(exchange);
        assertThat(binding.getRoutingKey()).isEqualTo(routingKey);
    }
}
