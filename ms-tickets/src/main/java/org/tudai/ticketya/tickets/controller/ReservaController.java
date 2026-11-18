package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.tickets.dto.ReservaRequestDTO;
import org.tudai.ticketya.tickets.dto.ReservaResponseDTO;
import org.tudai.ticketya.tickets.service.ReservaService;

@RestController
@RequestMapping("/api/tickets/eventos")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;
    private final UserContext userContext;

    @PostMapping("/{eventoId}/reservas")
    @Authorize("hasRole('CLIENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponseDTO reservar(@PathVariable Long eventoId,
                                       @Valid @RequestBody ReservaRequestDTO request) {
        return reservaService.reservar(eventoId, clienteIdActual(), request);
    }

    private Long clienteIdActual() {
        try {
            return Long.valueOf(userContext.userId());
        } catch (NumberFormatException e) {
            throw new BadRequestException("El header X-User-Id debe ser un número");
        }
    }
}
