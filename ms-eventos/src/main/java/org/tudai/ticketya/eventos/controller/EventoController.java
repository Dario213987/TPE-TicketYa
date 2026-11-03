package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.dto.evento.EventoBorradorCreateDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.service.EventoService;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final UserContext userContext;

    @PostMapping
    @Authorize("hasRole('ORGANIZADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponseDTO crear(@Valid @RequestBody EventoBorradorCreateDTO request) {
        return eventoService.crearEventoBorrador(request, organizadorIdActual());
    }

    private Long organizadorIdActual() {

        try {
            return userContext.userId();
        } catch (NumberFormatException e) {
            throw new BadRequestException("El header X-User-Id debe ser un número");
        }
    }
}