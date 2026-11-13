package org.tudai.ticketya.tickets.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.tudai.ticketya.tickets.client.dto.EventoResponseDto;

@FeignClient(name = "ms-eventos")
public interface EventoClient {

    @GetMapping("/api/eventos/{id}")
    public EventoResponseDto obtenerEvento(@PathVariable("id") long id);




}
