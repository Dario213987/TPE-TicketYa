package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.tickets.dto.EntradaReintegroDTO;
import org.tudai.ticketya.tickets.service.EntradaReintegroService;
import org.tudai.ticketya.tickets.service.ReservaVencidaService;

import java.time.Instant;
import java.util.List;

/**
 * Endpoints internos de reintegro masivo, consumidos por {@code ms-devoluciones}.
 * <p>
 * No llevan {@code @Authorize}: el proceso nace de la cancelación de un evento y no hay
 * usuario autenticado. El gateway bloquea {@code /api/tickets/internal/**}.
 */
@RestController
@RequestMapping("/api/tickets/internal")
@RequiredArgsConstructor
public class ReintegroInternalController {

    private static final int TAMANIO_MAXIMO = 1000;

    private final EntradaReintegroService entradaReintegroService;
    private final ReservaVencidaService reservaVencidaService;

    @GetMapping("/eventos/{eventoId}/entradas")
    public List<EntradaReintegroDTO> listarEntradasVendidas(
            @PathVariable Long eventoId,
            @RequestParam(defaultValue = "0") long desdeId,
            @RequestParam(defaultValue = "100") int size) {
        return entradaReintegroService.listarVendidas(eventoId, desdeId, Math.min(size, TAMANIO_MAXIMO));
    }

    @PutMapping("/eventos/{eventoId}/reservas/liberar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void liberarReservas(@PathVariable Long eventoId) {
        reservaVencidaService.liberarReservasVigentes(eventoId, Instant.now());
    }

    @PutMapping("/entradas/{ticketId}/devolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarDevuelto(@PathVariable Long ticketId) {
        entradaReintegroService.marcarDevuelto(ticketId);
    }
}
