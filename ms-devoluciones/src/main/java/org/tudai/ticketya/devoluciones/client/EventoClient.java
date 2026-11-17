package org.tudai.ticketya.devoluciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "${eventos.service.name:ms-eventos}")
public interface EventoClient {

    @GetMapping("/api/eventos/{id}")
    EventoDTO obtenerEvento(@PathVariable("id") Long id);
}
