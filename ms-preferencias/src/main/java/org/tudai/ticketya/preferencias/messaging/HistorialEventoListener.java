package org.tudai.ticketya.preferencias.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.event.EventoTicketUtilizado;
import org.tudai.ticketya.preferencias.event.EventoVentaPagada;
import org.tudai.ticketya.preferencias.service.HistorialService;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Consumidor de los streams que alimentan el historial del cliente (US-09.02).
 * <p>
 * La idempotencia se resuelve en dos capas:
 * <ul>
 *     <li>por <b>id de mensaje</b>, con una clave en Redis que recuerda los ya procesados
 *     ({@code preferencias:historial:procesados:<stream>:<id>});</li>
 *     <li>por <b>evento</b>, porque {@link HistorialService} hace upsert de la entrada
 *     del historial.</li>
 * </ul>
 * Los mensajes inválidos se registran y se descartan para no bloquear el stream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HistorialEventoListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final HistorialService historialService;
    private final StringRedisTemplate redisTemplate;
    private final HistorialProperties properties;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String mensajeId = record.getId().getValue();
        String claveDedupe = properties.getDedupePrefix() + record.getStream() + ":" + mensajeId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(claveDedupe))) {
            log.info("Historial: mensaje {} de {} ya procesado, se ignora", mensajeId, record.getStream());
            return;
        }

        try {
            if (properties.getStreamVentaPagada().equals(record.getStream())) {
                procesarVentaPagada(record);
            } else if (properties.getStreamTicketUtilizado().equals(record.getStream())) {
                procesarTicketUtilizado(record);
            } else {
                log.warn("Historial: stream desconocido {} (mensaje {})", record.getStream(), mensajeId);
                return;
            }
            redisTemplate.opsForValue().set(claveDedupe, "1", properties.getDedupeTtl());
        } catch (RuntimeException e) {
            log.error("Historial: no se pudo procesar el mensaje {} de {}: {}",
                    mensajeId, record.getStream(), e.getMessage(), e);
        }
    }

    private void procesarVentaPagada(MapRecord<String, String, String> record) {
        Map<String, String> campos = record.getValue();
        EventoVentaPagada evento = new EventoVentaPagada(
                requeridoLong(campos, "clienteId"),
                requeridoLong(campos, "eventoId"),
                tipo(campos.get("tipo")),
                listaIds(campos.get("participanteIds")),
                vacioANull(campos.get("ciudad")),
                instante(campos.get("fechaEvento")),
                vacioANull(campos.get("traceId")));

        historialService.registrarCompra(evento);
        log.info("Historial: compra registrada cliente={} evento={} traceId={}",
                evento.clienteId(), evento.eventoId(), evento.traceId());
    }

    private void procesarTicketUtilizado(MapRecord<String, String, String> record) {
        Map<String, String> campos = record.getValue();
        EventoTicketUtilizado evento = new EventoTicketUtilizado(
                requeridoLong(campos, "clienteId"),
                requeridoLong(campos, "eventoId"),
                opcionalLong(campos.get("ticketId")),
                instante(campos.get("fechaUso")),
                vacioANull(campos.get("traceId")));

        historialService.registrarUso(evento);
        log.info("Historial: uso registrado cliente={} evento={} ticket={} traceId={}",
                evento.clienteId(), evento.eventoId(), evento.ticketId(), evento.traceId());
    }

    private static Long requeridoLong(Map<String, String> campos, String campo) {
        String valor = campos.get(campo);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta el campo obligatorio '" + campo + "'");
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El campo '" + campo + "' no es numérico: " + valor, e);
        }
    }

    private static Long opcionalLong(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static TipoEvento tipo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return TipoEvento.valueOf(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de evento desconocido: " + valor, e);
        }
    }

    private static List<Long> listaIds(String valor) {
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    private static Instant instante(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(valor.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Fecha inválida: " + valor, e);
        }
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
