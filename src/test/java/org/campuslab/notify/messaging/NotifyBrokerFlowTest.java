package org.campuslab.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.campuslab.notify.service.EmailNotificationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.campuslab.notify.messaging.dlq.DlqMonitor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Arranca notify COMPLETO contra un RabbitMQ real y comprueba los flujos de ACK / errores de punta a punta:
 * entrega y confirmación, reintentos, errores no recuperables, mensajes ilegibles e idempotencia.
 *
 * Como los nombres de la topología vienen de application.yml, el test los sobrescribe con un sufijo
 * único: la aplicación entera corre sobre exchanges y colas aislados, sin tocar los reales ni sus DLQ,
 * y al terminar los elimina.
 *
 * Se omite automáticamente si no hay broker en RABBITMQ_HOST:RABBITMQ_PORT (por defecto localhost:5672);
 * para correrlo: docker compose up -d en campuslab-infra/mq.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotifyBrokerFlowTest {

    private static final String HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String USER = System.getenv().getOrDefault("RABBITMQ_USER", "campuslab");
    private static final String PASS = System.getenv().getOrDefault("RABBITMQ_PASS", "campuslab");

    private static final String SUFFIX = "it-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String DIRECT = "cmd.direct." + SUFFIX;
    private static final String TOPIC = "cmd.topic." + SUFFIX;
    private static final String DEAD = "cmd.dead." + SUFFIX;
    private static final String EMAIL_QUEUE = "q.cmd.email." + SUFFIX;
    private static final String EMAIL_DLQ = EMAIL_QUEUE + ".dlq";

    @DynamicPropertySource
    static void isolatedTopology(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> HOST);
        registry.add("spring.rabbitmq.port", () -> PORT);
        registry.add("spring.rabbitmq.username", () -> USER);
        registry.add("spring.rabbitmq.password", () -> PASS);

        registry.add("campuslab.messaging.exchanges.direct", () -> DIRECT);
        registry.add("campuslab.messaging.exchanges.topic", () -> TOPIC);
        registry.add("campuslab.messaging.exchanges.dead-letter", () -> DEAD);
        for (String flow : List.of("email", "prep", "voucher")) {
            String queue = "q.cmd." + flow + "." + SUFFIX;
            registry.add("campuslab.messaging.queues." + flow + ".name", () -> queue);
            registry.add("campuslab.messaging.queues." + flow + ".dlq", () -> queue + ".dlq");
            registry.add("campuslab.messaging.queues." + flow + ".dlq-routing-key", () -> flow + ".dlq." + SUFFIX);
        }
        // reintentos rápidos para que el test no dure segundos de más (3 intentos igual que en producción)
        // el monitor de DLQ solo corre cuando el test llama a poll(): sin ejecuciones programadas de por medio
        registry.add("campuslab.notify.dlq-monitor.initial-delay-ms", () -> 3_600_000);
        registry.add("campuslab.notify.dlq-monitor.interval-ms", () -> 3_600_000);
        registry.add("campuslab.notify.retry.initial-interval", () -> 100);
        registry.add("campuslab.notify.retry.max-interval", () -> 200);
    }

    @MockBean
    private EmailNotificationService emailService;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;
    @Autowired
    private DlqMonitor dlqMonitor;

    // El contexto de Spring sigue vivo al terminar la clase; se guarda el registro para detener sus listeners antes de limpiar
    private static RabbitListenerEndpointRegistry registry;
    @Autowired
    private MockMvc mockMvc;

    private static boolean brokerReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 1000);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @BeforeAll
    static void requireBroker() {
        assumeThat(brokerReachable())
                .as("No hay RabbitMQ en %s:%d, se omiten los tests contra broker real", HOST, PORT).isTrue();
    }

    @AfterAll
    static void dropIsolatedTopology() {
        if (!brokerReachable()) {
            return;   // la clase se omitió en @BeforeAll: no hay nada que limpiar (y @AfterAll corre igual)
        }
        // Sin esto, al borrar las colas los listeners (que siguen conectados) reconectan y el RabbitAdmin de la
        // aplicación las vuelve a declarar: la topología de prueba quedaba en el broker después de cada ejecución.
        if (registry != null) {
            registry.stop();
        }
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(HOST, PORT);
        connectionFactory.setUsername(USER);
        connectionFactory.setPassword(PASS);
        try {
            RabbitAdmin admin = new RabbitAdmin(connectionFactory);
            for (String flow : List.of("email", "prep", "voucher")) {
                admin.deleteQueue("q.cmd." + flow + "." + SUFFIX);
                admin.deleteQueue("q.cmd." + flow + "." + SUFFIX + ".dlq");
            }
            admin.deleteExchange(DIRECT);
            admin.deleteExchange(TOPIC);
            admin.deleteExchange(DEAD);
        } finally {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void resetMock() {
        registry = listenerRegistry;
        reset(emailService);
        amqpAdmin.purgeQueue(EMAIL_QUEUE);
        amqpAdmin.purgeQueue(EMAIL_DLQ);
    }

    // ---------- ACK ----------

    @Test
    void cadaMensajeValidoSeConfirmaYElSiguienteSigueFluyendoAunConPrefetchUno() {
        // prefetch=1 + ack manual: si el consumidor no confirmara un mensaje, los siguientes nunca llegarían
        for (int i = 1; i <= 3; i++) {
            send("email.send", envelope("evt-ok-" + i, Map.of("bookingId", i, "to", "alumno@duocuc.cl", "motivo", "Aprobada")));
        }

        verify(emailService, timeout(10_000).times(3)).send(any());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(messages(EMAIL_QUEUE)).isZero();
            assertThat(messages(EMAIL_DLQ)).isZero();
        });
    }

    @Test
    void unaReentregaDuplicadaSeConfirmaPeroNoRepiteElEfecto() {
        Map<String, Object> message = envelope("evt-dup", Map.of("bookingId", 9, "to", "alumno@duocuc.cl"));
        send("email.send", message);
        send("email.send", message);

        verify(emailService, timeout(10_000).times(1)).send(any());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(messages(EMAIL_QUEUE)).isZero());
        // se dio tiempo suficiente al segundo mensaje para ser procesado: sigue habiendo un solo efecto
        verify(emailService, times(1)).send(any());
        assertThat(messages(EMAIL_DLQ)).isZero();
    }

    // ---------- Errores ----------

    @Test
    void unPayloadInvalidoVaDirectoALaDlqSinReintentosNiProcesamiento() throws Exception {
        send("email.send", envelope("evt-bad", Map.of("bookingId", 1, "to", "esto-no-es-un-email")));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(messages(EMAIL_DLQ)).isEqualTo(1));
        verify(emailService, never()).send(any());
        assertThat(messages(EMAIL_QUEUE)).isZero();

        // y la alerta que consume el BFF lo refleja
        mockMvc.perform(get("/api/notify/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALERT"));
    }

    @Test
    void unaFallaRecuperableSeReintentaTresVecesYLuegoVaALaDlq() {
        doThrow(new IllegalStateException("SMTP caído")).when(emailService).send(any());

        send("email.send", envelope("evt-retry", Map.of("bookingId", 2, "to", "alumno@duocuc.cl")));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(messages(EMAIL_DLQ)).isEqualTo(1));
        verify(emailService, times(3)).send(any());
        assertThat(messages(EMAIL_QUEUE)).isZero();
    }

    @Test
    void unMensajeIlegibleVaALaDlqAunConAckManual() {
        // JSON roto: falla antes de llegar al listener; lo rechaza el contenedor (rejectManual por defecto)
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(DIRECT, "email.send", new Message("{esto no es json".getBytes(StandardCharsets.UTF_8), properties));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(messages(EMAIL_DLQ)).isEqualTo(1));
        verify(emailService, never()).send(any());
        assertThat(messages(EMAIL_QUEUE)).isZero();
    }

    // ---------- Procesamiento de la DLQ ----------

    @Test
    void elMonitorDeDlqRegistraElMensajeFallidoUnaSolaVezYLoDejaEnLaDlq() {
        Logger monitorLog = (Logger) LoggerFactory.getLogger(DlqMonitor.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        monitorLog.addAppender(logs);
        try {
            send("email.send", envelope("evt-dlq-log", Map.of("bookingId", 3, "to", "esto-no-es-un-email")));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(messages(EMAIL_DLQ)).isEqualTo(1));

            dlqMonitor.poll();
            assertThat(linesAbout(logs, "evt-dlq-log")).hasSize(1).first().asString()
                    .contains("dlq=" + EMAIL_DLQ, "origen=" + EMAIL_QUEUE, "motivo=rejected", "eventId=evt-dlq-log",
                            "routingKey=[email.send]", "esto-no-es-un-email");

            // una segunda revisión no repite la línea...
            dlqMonitor.poll();
            assertThat(linesAbout(logs, "evt-dlq-log")).hasSize(1);
            // ...y el mensaje sigue en la DLQ: se inspeccionó, no se consumió
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(messages(EMAIL_DLQ)).isEqualTo(1));
        } finally {
            monitorLog.detachAppender(logs);
        }
    }

    // ---------- helpers ----------

    private static List<String> linesAbout(ListAppender<ILoggingEvent> logs, String eventId) {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains(eventId)).toList();
    }

    private void send(String routingKey, Map<String, Object> envelope) {
        rabbitTemplate.convertAndSend(DIRECT, routingKey, envelope);
    }

    private static Map<String, Object> envelope(String eventId, Map<String, Object> payload) {
        return Map.of("type", "email.send", "eventId", eventId, "timestamp", Instant.now().toString(),
                "traceId", eventId, "correlationId", eventId, "payload", payload);
    }

    private int messages(String queue) {
        var info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }
}
