package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.eventos.dto.sector.SectorNoNumeradoRequestDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorNumeradoRequestDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;
import org.tudai.ticketya.eventos.service.SectorService;

@RestController
@RequestMapping("/api/lugares/{lugarId}/sectores")
@RequiredArgsConstructor
public class SectorController {

    private final SectorService sectorService;

    @PostMapping("/numerados")
    public ResponseEntity<?> crearSectorNumerado(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNumeradoRequestDTO dto) {
        if (!esAdministrador(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso denegado: Se requiere rol de Administrador");
        }
        SectorResponseDTO sectorCreado = sectorService.crearSectorNumerado(lugarId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sectorCreado);
    }

    @PostMapping("/no-numerados")
    public ResponseEntity<?> crearSectorNoNumerado(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNoNumeradoRequestDTO dto) {
        if (!esAdministrador(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso denegado: Se requiere rol de Administrador");
        }
        SectorResponseDTO sectorCreado = sectorService.crearSectorNoNumerado(lugarId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sectorCreado);
    }

    private boolean esAdministrador(String userRole) {
        return userRole != null && userRole.equalsIgnoreCase("ADMINISTRADOR");
    }
}
