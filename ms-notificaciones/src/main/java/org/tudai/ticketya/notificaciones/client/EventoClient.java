package org.tudai.ticketya.notificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;

@FeignClient(name = "ms-eventos")
public interface EventoClient {

    @GetMapping("/api/eventos/{eventoId}")
    EventoClientDTO obtenerEvento(@PathVariable("eventoId") Long eventoId);
}
