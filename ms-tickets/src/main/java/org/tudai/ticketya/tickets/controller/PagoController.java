package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.service.ReservaService;

@RestController
@RequestMapping("/api/tickets/reservas")
@RequiredArgsConstructor
public class PagoController {

    private final ReservaService reservaService;
    private final UserContext userContext;

    @PostMapping({"/{reservaId}/pagos", "/{reservaId}/pago"})
    @Authorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReservaResponseDTO iniciarPago(@PathVariable Long reservaId) {
        return reservaService.iniciarPago(reservaId, clienteIdActual());
    }

    @PostMapping({"/{reservaId}/pagos/confirmacion", "/{reservaId}/pago/confirmacion"})
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmarPago(@PathVariable Long reservaId) {
        reservaService.confirmarPago(reservaId);
    }

    @PostMapping({"/{reservaId}/pagos/rechazo", "/{reservaId}/pago/rechazo"})
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazarPago(@PathVariable Long reservaId) {
        reservaService.rechazarPago(reservaId);
    }

    private Long clienteIdActual() {
        try {
            return Long.valueOf(userContext.userId());
        } catch (NumberFormatException e) {
            throw new BadRequestException("El header X-User-Id debe ser un numero");
        }
    }
}
