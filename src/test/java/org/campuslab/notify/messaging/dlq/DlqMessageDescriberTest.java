package org.campuslab.notify.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;

/** Qué queda en el log cuando un mensaje llega a una DLQ: origen, motivo, identificación y contenido. */
class DlqMessageDescriberTest {

    private static final String DLQ = "q.cmd.email.dlq";
    private static final String BODY =
            "{\"type\":\"email.send\",\"eventId\":\"ev-1\",\"traceId\":\"tr-9\",\"payload\":{\"to\":\"no-es-email\"}}";

    private static GetResponse message(String body, Map<String, Object> headers) {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
        return new GetResponse(new Envelope(1L, false, "cmd.dead.dlx", "email.dlq"), props,
                body.getBytes(StandardCharsets.UTF_8), 0);
    }

    private static Map<String, Object> death(String reason) {
        return death(reason, 1_761_600_000_000L);
    }

    private static Map<String, Object> death(String reason, long diedAtMillis) {
        return Map.of("x-death", List.of(Map.of(
                "queue", "q.cmd.email", "reason", reason, "count", 1L, "exchange", "cmd.direct",
                "routing-keys", List.of("email.send"), "time", new Date(diedAtMillis))));
    }

    @Test
    void laLineaIncluyeOrigenMotivoIdentificacionYContenido() {
        String line = DlqMessageDescriber.describe(DLQ, message(BODY, death("rejected")));

        assertThat(line)
                .startsWith("[DLQ] mensaje fallido")
                .contains("dlq=q.cmd.email.dlq")
                .contains("origen=q.cmd.email")
                .contains("motivo=rejected")
                .contains("veces=1")
                .contains("exchange=cmd.direct")
                .contains("routingKey=[email.send]")
                .contains("eventId=ev-1")
                .contains("type=email.send")
                .contains("traceId=tr-9")
                .contains("encoladoEn=2025-10-27T")
                .contains("no-es-email");
    }

    @Test
    void distingueLosTresMotivosPorLosQueUnMensajeMuere() {
        for (String reason : List.of("rejected", "expired", "maxlen")) {
            assertThat(DlqMessageDescriber.describe(DLQ, message(BODY, death(reason)))).contains("motivo=" + reason);
        }
    }

    @Test
    void unJsonIlegibleTambienSeDescribeSinFallar() {
        String line = DlqMessageDescriber.describe(DLQ, message("{esto no es json", death("rejected")));

        assertThat(line).contains("motivo=rejected").contains("eventId=?").contains("{esto no es json");
    }

    @Test
    void sinCabeceraXDeathLosCamposDesconocidosSalenComoInterrogacion() {
        String line = DlqMessageDescriber.describe(DLQ, message(BODY, Map.of()));

        assertThat(line).contains("origen=?").contains("motivo=?").contains("eventId=ev-1");
    }

    @Test
    void unPayloadMuyLargoSeAcortaYSeDejaEnUnaSolaLinea() {
        String largo = "{\"eventId\":\"ev-2\",\"payload\":\"" + "x".repeat(1000) + "\"}\n";
        String line = DlqMessageDescriber.describe(DLQ, message(largo, death("expired")));

        assertThat(line).doesNotContain("\n").endsWith("…");
        assertThat(line.length()).isLessThan(700);
    }

    @Test
    void laIdentidadUsaElEventIdYEsEstableEntreLecturas() {
        GetResponse first = message(BODY, death("rejected"));
        GetResponse again = message(BODY, death("rejected"));

        assertThat(DlqMessageDescriber.identity(DLQ, first)).startsWith("q.cmd.email.dlq|ev-1|")
                .isEqualTo(DlqMessageDescriber.identity(DLQ, again));
        // el mismo eventId en otra DLQ es otro mensaje
        assertThat(DlqMessageDescriber.identity("q.cmd.prep.dlq", first)).isNotEqualTo(DlqMessageDescriber.identity(DLQ, first));
    }

    @Test
    void sinEventIdLaIdentidadSaleDelCuerpo() {
        GetResponse a = message("{esto no es json", death("rejected"));
        GetResponse b = message("{otro ilegible", death("rejected"));

        assertThat(DlqMessageDescriber.identity(DLQ, a)).contains("body#")
                .isEqualTo(DlqMessageDescriber.identity(DLQ, message("{esto no es json", death("rejected"))))
                .isNotEqualTo(DlqMessageDescriber.identity(DLQ, b));
    }

    @Test
    void dosMensajesIlegiblesIgualesPeroQueMurieronEnMomentosDistintosSonMensajesDistintos() {
        // Defecto encontrado en AWS: el segundo JSON ilegible, idéntico al primero, no se registraba
        GetResponse primero = message("{esto no es json", death("expired", 1_761_600_000_000L));
        GetResponse segundo = message("{esto no es json", death("rejected", 1_761_600_300_000L));

        assertThat(DlqMessageDescriber.identity(DLQ, primero)).isNotEqualTo(DlqMessageDescriber.identity(DLQ, segundo));
    }

    @Test
    void elMismoEventIdQueVuelveAFallarTrasReprocesarloSeRegistraDeNuevo() {
        GetResponse antes = message(BODY, death("rejected", 1_761_600_000_000L));
        GetResponse despues = message(BODY, death("rejected", 1_761_605_000_000L));

        assertThat(DlqMessageDescriber.identity(DLQ, antes)).isNotEqualTo(DlqMessageDescriber.identity(DLQ, despues));
    }
}
