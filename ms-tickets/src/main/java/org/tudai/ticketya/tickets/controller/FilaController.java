package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.dto.TurnoFilaResponseDTO;
import org.tudai.ticketya.tickets.service.FilaService;

@RestController
@RequestMapping("/api/tickets/eventos")
@RequiredArgsConstructor
public class FilaController {

    private final FilaService filaService;
    private final UserContext userContext;

    @PostMapping("/{eventoId}/fila")
    @Authorize("hasRole('CLIENTE')")
    public ResponseEntity<TurnoFilaResponseDTO> entrarEnFila(@PathVariable Long eventoId) {
        TurnoFilaResponseDTO turno = filaService.entrar(eventoId, userContext.userId());
        HttpStatus estado = turno.isYaEstabaEnFila() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(turno);
    }
}
