package org.campuslab.notify.messaging.dlq;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;

/**
 * Convierte un mensaje que llegó a una DLQ en una línea de log con lo relevante para analizarlo:
 * de qué cola venía, por qué murió, cuántas veces, qué mensaje era (eventId, type, traceId) y su contenido.
 *
 * RabbitMQ deja el motivo en la cabecera {@code x-death} al enviar el mensaje al dead letter exchange:
 * <ul>
 *   <li>{@code rejected}: el consumidor lo rechazó (payload inválido, reintentos agotados, JSON ilegible)</li>
 *   <li>{@code expired}: nadie lo consumió dentro del TTL de la cola principal</li>
 *   <li>{@code maxlen}: la cola principal superó su largo máximo</li>
 * </ul>
 *
 * No toca el mensaje ni el broker: es solo formato, por eso se prueba sin RabbitMQ.
 */
final class DlqMessageDescriber {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_PAYLOAD_CHARS = 300;

    private DlqMessageDescriber() {
    }

    /**
     * Identifica el mensaje para no registrarlo dos veces: DLQ + su eventId (o un hash del cuerpo si no lo trae) + la hora
     * en que murió. Esa hora no cambia mientras el mensaje espera en la DLQ, pero distingue a dos mensajes distintos con el
     * mismo contenido (por ejemplo dos JSON ilegibles iguales) y a un mismo eventId que vuelve a fallar tras reprocesarlo.
     */
    static String identity(String dlq, GetResponse message) {
        String eventId = field(parse(message.getBody()), "eventId");
        String who = eventId != null ? eventId : "body#" + Arrays.hashCode(message.getBody());
        return dlq + "|" + who + "|" + time(firstDeath(message).get("time"));
    }

    static String describe(String dlq, GetResponse message) {
        Map<String, Object> death = firstDeath(message);
        JsonNode body = parse(message.getBody());

        StringBuilder line = new StringBuilder("[DLQ] mensaje fallido");
        append(line, "dlq", dlq);
        append(line, "origen", text(death.get("queue")));
        append(line, "motivo", text(death.get("reason")));
        append(line, "veces", text(death.get("count")));
        append(line, "exchange", text(death.get("exchange")));
        append(line, "routingKey", routingKeys(death.get("routing-keys")));
        append(line, "eventId", field(body, "eventId"));
        append(line, "type", field(body, "type"));
        append(line, "traceId", field(body, "traceId"));
        append(line, "encoladoEn", time(death.get("time")));
        append(line, "payload", abbreviate(new String(message.getBody(), StandardCharsets.UTF_8)));
        return line.toString();
    }

    // La primera entrada de x-death es la más reciente: la cola donde murió el mensaje por última vez
    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstDeath(GetResponse message) {
        Map<String, Object> headers = message.getProps() == null ? null : message.getProps().getHeaders();
        Object deaths = headers == null ? null : headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            return (Map<String, Object>) first;
        }
        return Map.of();
    }

    private static JsonNode parse(byte[] body) {
        try {
            return JSON.readTree(body);
        } catch (Exception ex) {
            return null;   // JSON ilegible: justamente un caso que termina en la DLQ
        }
    }

    private static String field(JsonNode node, String name) {
        return node != null && node.hasNonNull(name) ? node.get(name).asText() : null;
    }

    // Los valores de las cabeceras pueden llegar como LongString o Date: se pasan a texto
    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String routingKeys(Object value) {
        return value instanceof List<?> keys ? keys.stream().map(String::valueOf).toList().toString() : text(value);
    }

    private static String time(Object value) {
        return value instanceof java.util.Date date ? Instant.ofEpochMilli(date.getTime()).toString() : text(value);
    }

    private static String abbreviate(String value) {
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_PAYLOAD_CHARS ? oneLine : oneLine.substring(0, MAX_PAYLOAD_CHARS) + "…";
    }

    private static void append(StringBuilder line, String key, String value) {
        line.append(' ').append(key).append('=').append(value == null ? "?" : value);
    }
}
