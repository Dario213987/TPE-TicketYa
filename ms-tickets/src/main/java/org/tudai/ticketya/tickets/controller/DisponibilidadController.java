package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.tickets.dto.DisponibilidadEventoResponseDTO;
import org.tudai.ticketya.tickets.dto.DisponibilidadResponseDTO;
import org.tudai.ticketya.tickets.dto.PublicarEventoRequestDTO;
import org.tudai.ticketya.tickets.service.DisponibilidadService;

@RestController
@RequestMapping("/api/tickets/eventos")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;
    private final UserContext userContext;

    @PostMapping("/{eventoId}/disponibilidad")
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    public ResponseEntity<DisponibilidadResponseDTO> crearDisponibilidad(
            @PathVariable Long eventoId,
            @Valid @RequestBody PublicarEventoRequestDTO dto) {

        if (!eventoId.equals(dto.getEventoId())) {
            throw new BadRequestException("El id del evento del path no coincide con el del cuerpo");
        }

        // Un organizador solo puede publicar a su nombre; el administrador informa el organizador en el cuerpo.
        if (userContext.role() == Role.ORGANIZADOR) {
            Long organizadorId = userContext.userId();
            if (dto.getOrganizadorId() != null && !dto.getOrganizadorId().equals(organizadorId)) {
                throw new ForbiddenException("Un organizador solo puede publicar la disponibilidad de sus eventos");
            }
            dto.setOrganizadorId(organizadorId);
        }

        DisponibilidadResponseDTO resultado = disponibilidadService.publicar(dto);
        HttpStatus estado = resultado.isYaExistia() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(resultado);
    }

    @GetMapping("/{eventoId}/disponibilidad")
    public ResponseEntity<DisponibilidadEventoResponseDTO> consultarDisponibilidad(
            @PathVariable Long eventoId) {
        return ResponseEntity.ok(disponibilidadService.consultar(eventoId));
    }
}
