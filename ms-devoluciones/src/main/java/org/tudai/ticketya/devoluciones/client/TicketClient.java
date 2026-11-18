package org.tudai.ticketya.devoluciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "${tickets.service.name:ms-tickets}")
public interface TicketClient {

    @GetMapping("${tickets.internal.entrada-path:/api/tickets/internal/entradas/{id}}")
    TicketDTO obtenerTicket(@PathVariable("id") Long id);

    @GetMapping("${tickets.internal.reintegro-entradas-path:/api/tickets/internal/eventos/{eventoId}/entradas}")
    List<EntradaReintegroDTO> listarEntradasVendidas(
            @PathVariable("eventoId") Long eventoId,
            @RequestParam("desdeId") long desdeId,
            @RequestParam("size") int size);

    @PutMapping("${tickets.internal.reintegro-reservas-path:/api/tickets/internal/eventos/{eventoId}/reservas/liberar}")
    void liberarReservas(@PathVariable("eventoId") Long eventoId);

    @PutMapping("${tickets.internal.reintegro-devuelto-path:/api/tickets/internal/entradas/{ticketId}/devolver}")
    void marcarDevuelto(@PathVariable("ticketId") Long ticketId);
}
