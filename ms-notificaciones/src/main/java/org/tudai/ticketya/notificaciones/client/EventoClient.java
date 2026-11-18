package org.tudai.ticketya.notificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;

/**
 * Cliente Feign para obtener los datos publicos de un evento desde {@code ms-eventos}.
 */
@FeignClient(name = "ms-eventos")
public interface EventoClient {

    /**
     * Obtiene el evento usado para completar nombre, fecha, lugar y sectores del email.
     *
     * @param eventoId id del evento
     * @return datos del evento
     */
    @GetMapping("/api/eventos/{eventoId}")
    EventoClientDTO obtenerEvento(@PathVariable("eventoId") Long eventoId);
}
