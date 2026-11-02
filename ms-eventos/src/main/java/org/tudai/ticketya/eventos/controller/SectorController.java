package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public SectorResponseDTO crearSectorNumerado(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNumeradoRequestDTO dto) {
        return sectorService.crearSectorNumerado(lugarId, dto);
    }

    @PostMapping("/no-numerados")
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public SectorResponseDTO crearSectorNoNumerado(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNoNumeradoRequestDTO dto) {
        return sectorService.crearSectorNoNumerado(lugarId, dto);
    }
}
