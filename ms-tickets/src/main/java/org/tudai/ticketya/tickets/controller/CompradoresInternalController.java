package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.tickets.service.CompradoresService;

import java.util.List;

/**
 * Endpoints internos de compradores consumidos servicio-a-servicio.
 * <p>
 * El gateway bloquea {@code /api/tickets/internal/**}, por lo que estas rutas no
 * quedan expuestas externamente. Se usan para armar avisos masivos a los compradores
 * de un evento.
 */
@RestController
@RequestMapping("/api/tickets/internal/eventos")
@RequiredArgsConstructor
public class CompradoresInternalController {

    private final CompradoresService compradoresService;

    /**
     * Lista los ids de usuario distintos que compraron entradas para el evento.
     * <p>
     * Devuelve {@code 200 OK} con una lista vacia cuando el evento no tiene compradores.
     *
     * @param eventoId id del evento
     * @return ids de usuario distintos, ordenados de menor a mayor
     */
    @GetMapping("/{eventoId}/compradores")
    public List<Long> listarCompradores(@PathVariable Long eventoId) {
        return compradoresService.listarCompradores(eventoId);
    }
}
