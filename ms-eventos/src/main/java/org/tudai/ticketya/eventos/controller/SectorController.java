package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
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
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearSectorNumerado(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNumeradoRequestDTO dto) {
        SectorResponseDTO sectorCreado = sectorService.crearSectorNumerado(lugarId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sectorCreado);
    }

    @PostMapping("/no-numerados")
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearSectorNoNumerado(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNoNumeradoRequestDTO dto) {
        SectorResponseDTO sectorCreado = sectorService.crearSectorNoNumerado(lugarId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sectorCreado);
    }
}
