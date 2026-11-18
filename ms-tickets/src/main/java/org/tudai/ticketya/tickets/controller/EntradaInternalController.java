package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.tickets.dto.DevolucionTicketResponseDTO;
import org.tudai.ticketya.tickets.dto.EntradaDetalleDTO;
import org.tudai.ticketya.tickets.service.TicketDevolucionService;

/**
 * Endpoints internos servicio-a-servicio consumidos por {@code ms-devoluciones}.
 * El gateway bloquea {@code /api/tickets/internal/**}.
 */
@RestController
@RequestMapping("/api/tickets/internal/entradas")
@RequiredArgsConstructor
public class EntradaInternalController {

    private final TicketDevolucionService ticketDevolucionService;

    @GetMapping("/{id}")
    public EntradaDetalleDTO obtenerDetalleEntrada(@PathVariable Long id) {
        return ticketDevolucionService.obtenerDetalle(id);
    }

    @PostMapping("/{id}/devolver")
    @ResponseStatus(HttpStatus.OK)
    public DevolucionTicketResponseDTO devolverTicket(@PathVariable Long id) {
        return ticketDevolucionService.devolverTicket(id);
    }
}
