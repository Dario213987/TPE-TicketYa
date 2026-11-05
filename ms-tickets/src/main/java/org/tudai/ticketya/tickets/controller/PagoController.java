package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.domain.EstadoPago;
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

    /**
     * Responde 201 con la compra aprobada, o 202 si el pago quedó PENDIENTE_PAGO (timeout de
     * ms-transacciones): el cliente reintenta con la misma Idempotency-Key.
     */
    @PostMapping("/{reservaId}/pago")
    @Authorize("hasRole('CLIENTE')")
    public ResponseEntity<CompraResponseDTO> pagar(@PathVariable Long reservaId,
                                                   @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                                   @Valid @RequestBody PagoRequestDTO request) {
        CompraResponseDTO compra = pagoService.pagar(reservaId, userContext.userId(), idempotencyKey, request);
        HttpStatus estado = compra.getEstadoPago() == EstadoPago.PENDIENTE_PAGO
                ? HttpStatus.ACCEPTED
                : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(compra);
    }
}
