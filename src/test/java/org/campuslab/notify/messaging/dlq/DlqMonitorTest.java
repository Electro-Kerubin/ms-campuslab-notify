package org.campuslab.notify.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.campuslab.notify.config.MessagingProperties;
import org.campuslab.notify.config.MessagingProperties.Exchanges;
import org.campuslab.notify.config.MessagingProperties.Flow;
import org.campuslab.notify.config.MessagingProperties.Idempotency;
import org.campuslab.notify.config.MessagingProperties.Queues;
import org.campuslab.notify.config.MessagingProperties.Retention;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** El monitor registra cada mensaje fallido una vez y deja la DLQ exactamente como estaba. */
class DlqMonitorTest {

    private static final String EMAIL_DLQ = "q.email.dlq";
    // En el broker la hora de muerte es fija mientras el mensaje espera en la DLQ: el test la fija igual
    private static final long DIED_AT = 1_761_600_000_000L;

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final Logger monitorLog = (Logger) LoggerFactory.getLogger(DlqMonitor.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        logs = new ListAppender<>();
        logs.start();
        monitorLog.addAppender(logs);
        // El RabbitTemplate simulado ejecuta el callback con el canal simulado
        when(rabbitTemplate.execute(any(ChannelCallback.class)))
                .thenAnswer(invocation -> ((ChannelCallback<?>) invocation.getArgument(0)).doInRabbit(channel));
    }

    @AfterEach
    void tearDown() {
        monitorLog.detachAppender(logs);
    }

    private DlqMonitor monitor(int maxPerPoll) {
        MessagingProperties properties = new MessagingProperties(
                new Exchanges("x.direct", "x.topic", "x.dead"),
                new Queues(
                        new Flow("q.email", EMAIL_DLQ, "a.send", "a.*", "a.dlq"),
                        new Flow("q.prep", "q.prep.dlq", "b.ticket", "b.#", "b.dlq"),
                        new Flow("q.voucher", "q.voucher.dlq", "c.gen", "c.*", "c.dlq")),
                new Retention(1, 1, 1), new Idempotency(1), "classic");
        return new DlqMonitor(rabbitTemplate, properties, maxPerPoll);
    }

    private static GetResponse failed(long tag, String eventId, String reason) {
        String body = "{\"type\":\"email.send\",\"eventId\":\"" + eventId + "\",\"payload\":{}}";
        Map<String, Object> headers = Map.of("x-death", List.of(Map.of(
                "queue", "q.email", "reason", reason, "count", 1L, "exchange", "x.direct",
                "routing-keys", List.of("a.send"), "time", new Date(DIED_AT))));
        return new GetResponse(new Envelope(tag, false, "x.dead", "a.dlq"),
                new AMQP.BasicProperties.Builder().headers(headers).build(), body.getBytes(StandardCharsets.UTF_8), 0);
    }

    private List<String> warnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void registraUnaLineaPorMensajeFallidoConSuMotivo() throws IOException {
        when(channel.basicGet(EMAIL_DLQ, false)).thenReturn(failed(1, "ev-1", "rejected"), failed(2, "ev-2", "expired"), (GetResponse) null);

        int nuevos = monitor(200).inspect(EMAIL_DLQ);

        assertThat(nuevos).isEqualTo(2);
        assertThat(warnings()).hasSize(2);
        assertThat(warnings().get(0)).contains("eventId=ev-1").contains("motivo=rejected").contains("origen=q.email");
        assertThat(warnings().get(1)).contains("eventId=ev-2").contains("motivo=expired");
    }

    @Test
    void devuelveTodosLosMensajesALaColaSinConfirmarlosNiDescartarlos() throws IOException {
        when(channel.basicGet(EMAIL_DLQ, false)).thenReturn(failed(1, "ev-1", "rejected"), failed(2, "ev-2", "rejected"), (GetResponse) null);

        monitor(200).inspect(EMAIL_DLQ);

        // nack múltiple hasta el último, con requeue: la DLQ queda como estaba
        verify(channel).basicNack(2L, true, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicReject(anyLong(), eq(false));
    }

    @Test
    void unMensajeQueSigueEnLaDlqNoSeRegistraDeNuevoEnLasSiguientesRevisiones() throws IOException {
        when(channel.basicGet(EMAIL_DLQ, false)).thenReturn(failed(1, "ev-1", "rejected"), (GetResponse) null)
                .thenReturn(failed(1, "ev-1", "rejected"), (GetResponse) null);
        DlqMonitor monitor = monitor(200);

        assertThat(monitor.inspect(EMAIL_DLQ)).isEqualTo(1);
        assertThat(monitor.inspect(EMAIL_DLQ)).isZero();

        assertThat(warnings()).hasSize(1);
        // aun sin novedades, el mensaje se devolvió a la cola las dos veces
        verify(channel, org.mockito.Mockito.times(2)).basicNack(1L, true, true);
    }

    @Test
    void unMensajeNuevoQueLlegaDespuesSiSeRegistra() throws IOException {
        when(channel.basicGet(EMAIL_DLQ, false)).thenReturn(failed(1, "ev-1", "rejected"), (GetResponse) null)
                .thenReturn(failed(1, "ev-1", "rejected"), failed(2, "ev-2", "expired"), (GetResponse) null);
        DlqMonitor monitor = monitor(200);

        monitor.inspect(EMAIL_DLQ);
        assertThat(monitor.inspect(EMAIL_DLQ)).isEqualTo(1);

        assertThat(warnings()).hasSize(2);
        assertThat(warnings().get(1)).contains("eventId=ev-2");
    }

    @Test
    void unaDlqVaciaNoRegistraNadaNiTocaLaCola() throws IOException {
        when(channel.basicGet(EMAIL_DLQ, false)).thenReturn(null);

        assertThat(monitor(200).inspect(EMAIL_DLQ)).isZero();

        assertThat(warnings()).isEmpty();
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void noLeeMasDelLimiteConfiguradoPorRevision() throws IOException {
        when(channel.basicGet(EMAIL_DLQ, false)).thenReturn(failed(1, "ev-1", "rejected"), failed(2, "ev-2", "rejected"),
                failed(3, "ev-3", "rejected"));

        monitor(2).inspect(EMAIL_DLQ);

        verify(channel, org.mockito.Mockito.times(2)).basicGet(EMAIL_DLQ, false);
        verify(channel).basicNack(2L, true, true);
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage)).anyMatch(m -> m.contains("se revisaron los primeros 2"));
    }

    @Test
    void poll_revisaLasTresDlqDeLosTresCasosDeUso() throws IOException {
        when(channel.basicGet(any(String.class), eq(false))).thenReturn(null);

        monitor(200).poll();

        verify(channel).basicGet("q.email.dlq", false);
        verify(channel).basicGet("q.prep.dlq", false);
        verify(channel).basicGet("q.voucher.dlq", false);
    }

    @Test
    void siElBrokerFallaElMonitorNoLanzaYSigueConLasOtrasDlq() throws IOException {
        when(channel.basicGet("q.email.dlq", false)).thenThrow(new IOException("canal cerrado"));
        when(channel.basicGet("q.prep.dlq", false)).thenReturn(failed(1, "ev-9", "rejected"), (GetResponse) null);

        assertThatCode(() -> monitor(200).poll()).doesNotThrowAnyException();

        assertThat(warnings()).anyMatch(m -> m.contains("no se pudo revisar q.email.dlq"));
        assertThat(warnings()).anyMatch(m -> m.contains("eventId=ev-9"));
    }
}
