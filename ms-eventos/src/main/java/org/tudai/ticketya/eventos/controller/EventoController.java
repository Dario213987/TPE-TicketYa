package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.dto.evento.CrearEventoRequestDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.service.EventoService;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService eventoService;
    private final UserContext userContext;

    public EventoController(EventoService eventoService, UserContext userContext) {
        this.eventoService = eventoService;
        this.userContext = userContext;
    }

    @PostMapping
    @Authorize("hasRole('ORGANIZADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponseDTO crear(@Valid @RequestBody CrearEventoRequestDTO request) {
        return eventoService.crearEvento(request, organizadorIdActual());
    }

    private Long organizadorIdActual() {
        String raw = userContext.userId();
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestException("El header X-User-Id debe ser un número");
        }
    }
}
