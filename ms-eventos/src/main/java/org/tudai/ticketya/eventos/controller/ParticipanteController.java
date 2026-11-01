package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.eventos.dto.request.CrearParticipanteRequest;
import org.tudai.ticketya.eventos.dto.response.ParticipanteResponse;
import org.tudai.ticketya.eventos.service.EventoService;

@RestController
@RequestMapping("/api/participantes")
public class ParticipanteController {

    private final EventoService eventoService;

    public ParticipanteController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @PostMapping
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipanteResponse crear(@Valid @RequestBody CrearParticipanteRequest request) {
        return eventoService.crearParticipante(request);
    }
}