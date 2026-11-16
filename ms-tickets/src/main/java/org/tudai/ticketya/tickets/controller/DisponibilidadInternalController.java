package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.tickets.service.DisponibilidadService;

/**
 * Endpoints internos de disponibilidad consumidos por {@code ms-eventos}
 * servicio-a-servicio. El gateway bloquea {@code /api/tickets/internal/**}.
 */
@RestController
@RequestMapping("/api/tickets/internal/eventos")
@RequiredArgsConstructor
public class DisponibilidadInternalController {

    private final DisponibilidadService disponibilidadService;

    @PutMapping("/{eventoId}/alta-demanda")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void actualizarAltaDemanda(@PathVariable Long eventoId,
                                      @RequestParam boolean altaDemanda) {
        disponibilidadService.actualizarAltaDemanda(eventoId, altaDemanda);
    }
}
