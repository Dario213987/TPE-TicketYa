package org.tudai.ticketya.devoluciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "${tickets.service.name:ms-tickets}")
public interface TicketClient {

    @GetMapping("/api/tickets/{id}")
    TicketDTO obtenerTicket(@PathVariable("id") Long id);
}
