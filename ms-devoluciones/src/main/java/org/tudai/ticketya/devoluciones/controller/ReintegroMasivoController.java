package org.tudai.ticketya.devoluciones.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.devoluciones.service.ReintegroMasivoService;

/**
 * Endpoint consumido por {@code ms-eventos} al cancelar un evento. Solo registra el
 * proceso y devuelve de inmediato: el trabajo se hace por lotes desde el scheduler.
 * <p>
 * No lleva {@code @Authorize}: la cancelación es una llamada servicio-a-servicio sin
 * usuario autenticado.
 */
@RestController
@RequestMapping("/api/devoluciones/eventos")
@RequiredArgsConstructor
public class ReintegroMasivoController {

    private final ReintegroMasivoService reintegroMasivoService;

    @PutMapping("/{eventoId}/reintegro-masivo")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reintegrarEvento(@PathVariable Long eventoId) {
        reintegroMasivoService.iniciar(eventoId);
    }
}
