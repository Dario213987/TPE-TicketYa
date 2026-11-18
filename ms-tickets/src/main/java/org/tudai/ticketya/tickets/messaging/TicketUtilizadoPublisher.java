package org.tudai.ticketya.tickets.messaging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.tickets.config.PreferenciasProperties;
import org.tudai.ticketya.tickets.domain.Ticket;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica en Redis Streams el evento {@code ticket.utilizado} que consume
 * ms-preferencias para el historial de asistencias (US-13.02).
 */
@Component
@Slf4j
public class TicketUtilizadoPublisher {

    private final StringRedisTemplate preferenciasRedisTemplate;
    private final PreferenciasProperties properties;

    public TicketUtilizadoPublisher(
            @Qualifier("preferenciasRedisTemplate") StringRedisTemplate preferenciasRedisTemplate,
            PreferenciasProperties properties) {
        this.preferenciasRedisTemplate = preferenciasRedisTemplate;
        this.properties = properties;
    }

    public void publicar(Ticket ticket) {
        if (!properties.isEnabled()) {
            return;
        }
        if (ticket == null || ticket.getClienteId() == null) {
            log.warn("No se publica ticket.utilizado: la entrada no tiene cliente asociado");
            return;
        }
        if (ticket.getEvento() == null || ticket.getEvento().getEventoId() == null) {
            log.warn("No se publica ticket.utilizado: la entrada no tiene evento asociado");
            return;
        }

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("clienteId", String.valueOf(ticket.getClienteId()));
        campos.put("eventoId", String.valueOf(ticket.getEvento().getEventoId()));
        if (ticket.getId() != null) {
            campos.put("ticketId", String.valueOf(ticket.getId()));
        }
        campos.put("fechaUso", Instant.now().toString());

        String traceId = MDC.get(MdcKeys.CORRELATION_ID);
        if (traceId != null && !traceId.isBlank()) {
            campos.put("traceId", traceId);
        }

        try {
            preferenciasRedisTemplate.opsForStream()
                    .add(properties.getStreamTicketUtilizado(), campos);
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar ticket.utilizado para el ticket {}: {}",
                    ticket.getId(), e.getMessage());
        }
    }
}
