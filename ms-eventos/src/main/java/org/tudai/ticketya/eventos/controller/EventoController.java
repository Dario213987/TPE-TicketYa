package org.tudai.ticketya.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.dto.evento.EventoPublicadoDTO;
import org.tudai.ticketya.eventos.service.EventoService;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final UserContext userContext;

    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    @PutMapping("/{id}/publicar")
    public EventoPublicadoDTO publicarEvento(@PathVariable Long id) {
        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;
        return eventoService.publicarEvento(id, organizadorIdActual(), esAdmin);
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
