package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.tickets.dto.HabilitacionRequestDTO;
import org.tudai.ticketya.tickets.service.HabilitacionTandaService;

/**
 * Configuracion interna de la habilitacion por tandas. El gateway bloquea
 * {@code /api/tickets/internal/**}, por lo que no queda expuesto externamente.
 */
@RestController
@RequestMapping("/api/tickets/internal/eventos")
@RequiredArgsConstructor
public class HabilitacionInternalController {

    private final HabilitacionTandaService habilitacionTandaService;

    @PutMapping("/{eventoId}/habilitacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void configurar(@PathVariable Long eventoId,
                           @Valid @RequestBody HabilitacionRequestDTO request) {
        habilitacionTandaService.configurar(eventoId, request);
    }
}
