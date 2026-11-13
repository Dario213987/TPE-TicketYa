package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.dto.ValidarAccesoRequestDTO;
import org.tudai.ticketya.tickets.service.AccesoService;

@RestController
@RequestMapping("/api/tickets/eventos")
@RequiredArgsConstructor
public class AccesoController {

    private final AccesoService accesoService;

    @PostMapping("/{eventoId}/accesos/validar")
    @Authorize("hasRole('PERSONAL_ACCESO')")
    public ResultadoAccesoDTO validar(@PathVariable Long eventoId,
                                      @Valid @RequestBody ValidarAccesoRequestDTO request) {
        return accesoService.validar(eventoId, request.getCodigoQr());
    }
}
