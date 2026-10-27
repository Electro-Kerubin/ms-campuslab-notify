package org.campuslab.notify.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.campuslab.notify.messaging.consumer.EmailConsumer;
import org.campuslab.notify.messaging.consumer.PrepTicketConsumer;
import org.campuslab.notify.messaging.consumer.VoucherConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Carga el application.yml REAL y verifica la configuración centralizada de punta a punta:
 * se bindea completa, genera la topología esperada y todos los consumidores escuchan una
 * cola que existe (si alguien cambia un nombre en el yml o en un listener, esto falla).
 * No necesita broker: solo construye los beans de topología.
 */
class MessagingConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RabbitMQConfig.class);

    @Test
    void elApplicationYmlSeBindeaCompletoConLosNombresDelProyecto() {
        runner.run(context -> {
            MessagingProperties props = context.getBean(MessagingProperties.class);

            assertThat(props.exchanges().direct()).isEqualTo("cmd.direct");
            assertThat(props.exchanges().topic()).isEqualTo("cmd.topic");
            assertThat(props.exchanges().deadLetter()).isEqualTo("cmd.dead.dlx");

            assertThat(props.queues().email().name()).isEqualTo("q.cmd.email");
            assertThat(props.queues().email().dlq()).isEqualTo("q.cmd.email.dlq");
            assertThat(props.queues().email().directRoutingKey()).isEqualTo("email.send");
            assertThat(props.queues().email().topicRoutingKey()).isEqualTo("email.*");

            assertThat(props.queues().prep().name()).isEqualTo("q.cmd.prep");
            assertThat(props.queues().prep().topicRoutingKey()).isEqualTo("prep.#");
            assertThat(props.queues().voucher().name()).isEqualTo("q.cmd.voucher");
            assertThat(props.queues().voucher().directRoutingKey()).isEqualTo("voucher.gen");

            assertThat(props.retention().queueTtlMs()).isEqualTo(30_000);
            assertThat(props.retention().queueMaxLength()).isEqualTo(1000);
            assertThat(props.retention().dlqTtlMs()).isEqualTo(86_400_000);
        });
    }

    @Test
    void laTopologiaDeclaraTresExchangesSeisColasYNueveBindings() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(Exchange.class)).hasSize(3);
            assertThat(context.getBeansOfType(Queue.class)).hasSize(6);
            assertThat(context.getBeansOfType(Binding.class)).hasSize(9);
        });
    }

    @Test
    void cadaConsumidorEscuchaUnaColaQueLaTopologiaDeclara() {
        runner.run(context -> {
            Set<String> declaredQueues = context.getBeansOfType(Queue.class).values().stream()
                    .map(Queue::getName).collect(Collectors.toSet());

            List<String> listenedQueues = new ArrayList<>();
            for (Class<?> consumer : List.of(EmailConsumer.class, PrepTicketConsumer.class, VoucherConsumer.class)) {
                for (Method method : consumer.getDeclaredMethods()) {
                    RabbitListener listener = method.getAnnotation(RabbitListener.class);
                    if (listener != null) {
                        for (String queue : listener.queues()) {
                            listenedQueues.add(context.getEnvironment().resolveRequiredPlaceholders(queue));
                        }
                        // ack explícito en el propio listener, sin depender del application.yml
                        assertThat(listener.ackMode()).isEqualTo("MANUAL");
                    }
                }
            }

            assertThat(listenedQueues).containsExactlyInAnyOrder("q.cmd.email", "q.cmd.prep", "q.cmd.voucher");
            assertThat(declaredQueues).containsAll(listenedQueues);
        });
    }

    @Test
    void siFaltaUnNombreLaAplicacionNoArranca() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("campuslab.messaging.queues.email.name=")
                .withUserConfiguration(RabbitMQConfig.class)
                .run(context -> assertThat(context).hasFailed());
    }

    // ---------- Alta disponibilidad: tipo de cola y varios nodos ----------

    @org.springframework.boot.context.properties.EnableConfigurationProperties(
            org.springframework.boot.autoconfigure.amqp.RabbitProperties.class)
    static class RabbitPropertiesConfig {
    }

    @Test
    void porDefectoLasColasSonClassicYSinArgumentoDeTipo() {
        runner.run(context -> {
            assertThat(context.getBean(MessagingProperties.class).quorum()).isFalse();
            context.getBeansOfType(Queue.class).values()
                    .forEach(q -> assertThat(q.getArguments()).doesNotContainKey("x-queue-type"));
        });
    }

    @Test
    void conQueueTypeQuorumTodasLasColasIncluidasLasDlqSeDeclaranQuorum() {
        runner.withPropertyValues("campuslab.messaging.queue-type=quorum").run(context -> {
            assertThat(context.getBeansOfType(Queue.class)).hasSize(6);
            context.getBeansOfType(Queue.class).values()
                    .forEach(q -> assertThat(q.getArguments()).containsEntry("x-queue-type", "quorum"));
            // la retención y el dead letter siguen igual
            Queue email = context.getBean("emailQueue", Queue.class);
            assertThat(email.getArguments()).containsEntry("x-message-ttl", 30_000)
                    .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx");
        });
    }

    @Test
    void unTipoDeColaInvalidoImpideArrancar() {
        runner.withPropertyValues("campuslab.messaging.queue-type=mirrored").run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void sinAddressesElClienteUsaHostYPuertoPorDefecto() {
        runner.withUserConfiguration(RabbitPropertiesConfig.class).run(context ->
                assertThat(context.getBean(org.springframework.boot.autoconfigure.amqp.RabbitProperties.class)
                        .determineAddresses()).isEqualTo("localhost:5672"));
    }

    @Test
    void conRabbitmqAddressesElClienteConoceTodosLosNodosDelClusterEnOrden() {
        runner.withUserConfiguration(RabbitPropertiesConfig.class)
                .withPropertyValues("spring.rabbitmq.addresses=nodo1:5672,nodo2:5672")
                .run(context -> assertThat(context.getBean(org.springframework.boot.autoconfigure.amqp.RabbitProperties.class)
                        .determineAddresses()).isEqualTo("nodo1:5672,nodo2:5672"));
    }
}
