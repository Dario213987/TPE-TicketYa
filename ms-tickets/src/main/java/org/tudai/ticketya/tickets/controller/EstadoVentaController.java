package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.tickets.domain.EstadoVenta;
import org.tudai.ticketya.tickets.service.DisponibilidadService;

/**
 * Endpoints internos de estado de venta, consumidos por {@code ms-eventos}
 * servicio-a-servicio.
 * <p>
 * No llevan {@code @Authorize} porque las llamadas de finalización nacen de un
 * proceso programado, sin usuario. El gateway bloquea {@code /api/tickets/internal/**}
 * para que no queden expuestos externamente.
 */
@RestController
@RequestMapping("/api/tickets/internal/eventos")
@RequiredArgsConstructor
public class EstadoVentaController {

    private final DisponibilidadService disponibilidadService;

    @PutMapping("/{eventoId}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long eventoId) {
        disponibilidadService.actualizarEstadoVenta(eventoId, EstadoVenta.CANCELADA);
    }

    @PutMapping("/{eventoId}/suspender")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspender(@PathVariable Long eventoId) {
        disponibilidadService.actualizarEstadoVenta(eventoId, EstadoVenta.SUSPENDIDA);
    }

    @PutMapping("/{eventoId}/agotar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void agotar(@PathVariable Long eventoId) {
        disponibilidadService.actualizarEstadoVenta(eventoId, EstadoVenta.AGOTADA);
    }

    @PutMapping("/{eventoId}/reactivar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivar(@PathVariable Long eventoId) {
        disponibilidadService.actualizarEstadoVenta(eventoId, EstadoVenta.ABIERTA);
    }

    @PutMapping("/{eventoId}/finalizar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizar(@PathVariable Long eventoId) {
        disponibilidadService.actualizarEstadoVenta(eventoId, EstadoVenta.FINALIZADA);
    }
}
