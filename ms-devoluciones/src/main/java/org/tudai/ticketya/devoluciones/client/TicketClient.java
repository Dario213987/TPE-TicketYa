package org.tudai.ticketya.devoluciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "${tickets.service.name:ms-tickets}")
public interface TicketClient {

    @GetMapping("${tickets.internal.entrada-path:/api/tickets/internal/entradas/{id}}")
    TicketDTO obtenerTicket(@PathVariable("id") Long id);

    @PostMapping("${tickets.internal.devolver-path:/api/tickets/internal/entradas/{id}/devolver}")
    DevolucionTicketDTO devolverTicket(@PathVariable("id") Long id);
}
