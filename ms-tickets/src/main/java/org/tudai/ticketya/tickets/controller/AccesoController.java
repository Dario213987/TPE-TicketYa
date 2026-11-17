package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Operation;
import org.tudai.ticketya.core.OperationLogger;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.dto.ValidarAccesoRequestDTO;
import org.tudai.ticketya.tickets.service.AccesoService;

@RestController
@RequestMapping("/api/tickets/eventos")
@RequiredArgsConstructor
public class AccesoController {

    private final AccesoService accesoService;
    private final UserContext userContext;

    @PostMapping("/{eventoId}/accesos/validar")
    @Authorize("hasRole('PERSONAL_ACCESO')")
    public ResultadoAccesoDTO validar(@PathVariable Long eventoId,
                                      @Valid @RequestBody ValidarAccesoRequestDTO request) {
        Long userId = userContext.userId();
        try {
            ResultadoAccesoDTO resultado = accesoService.validar(eventoId, request.getCodigoQr());
            OperationLogger.ok(Operation.ACCESS, userId, "Ticket", request.getCodigoQr());
            return resultado;
        } catch (RuntimeException e) {
            OperationLogger.failed(Operation.ACCESS, userId, "Ticket", request.getCodigoQr(), e.getMessage());
            throw e;
        }
    }
}
