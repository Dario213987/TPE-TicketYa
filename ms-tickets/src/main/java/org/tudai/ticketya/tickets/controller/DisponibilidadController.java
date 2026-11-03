package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.tickets.dto.DisponibilidadResponseDTO;
import org.tudai.ticketya.tickets.dto.PublicarEventoRequestDTO;
import org.tudai.ticketya.tickets.service.DisponibilidadService;

@RestController
@RequestMapping("/api/tickets/eventos")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    @PostMapping("/{eventoId}/disponibilidad")
    public ResponseEntity<DisponibilidadResponseDTO> crearDisponibilidad(
            @PathVariable Long eventoId,
            @Valid @RequestBody PublicarEventoRequestDTO dto) {

        DisponibilidadResponseDTO resultado = disponibilidadService.publicar(eventoId, dto);
        HttpStatus estado = resultado.isYaExistia() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(resultado);
    }
}
