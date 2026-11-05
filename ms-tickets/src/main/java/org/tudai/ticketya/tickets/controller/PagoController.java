package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.service.PagoService;

@RestController
@RequestMapping("/api/tickets/reservas")
@RequiredArgsConstructor
public class PagoController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PagoService pagoService;
    private final UserContext userContext;

    @PostMapping("/{reservaId}/pago")
    @Authorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponseDTO pagar(@PathVariable Long reservaId,
                                   @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                   @Valid @RequestBody PagoRequestDTO request) {
        return pagoService.pagar(reservaId, userContext.userId(), idempotencyKey, request);
    }
}
