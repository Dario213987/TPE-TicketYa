package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.contract.eventos.model.SectorNoNumeradoRequest;
import org.tudai.ticketya.contract.eventos.model.SectorNumeradoRequest;
import org.tudai.ticketya.contract.eventos.model.SectorResponse;
import org.tudai.ticketya.eventos.service.SectorService;

@RestController
@RequestMapping("/api/lugares/{lugarId}/sectores")
@RequiredArgsConstructor
public class SectorController {

    private final SectorService sectorService;

    @PostMapping("/numerados")
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public SectorResponse crearSectorNumerado(
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNumeradoRequest dto) {
        return sectorService.crearSectorNumerado(lugarId, dto);
    }

    @PostMapping("/no-numerados")
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public SectorResponse crearSectorNoNumerado(
            @PathVariable Long lugarId,
            @Valid @RequestBody SectorNoNumeradoRequest dto) {
        return sectorService.crearSectorNoNumerado(lugarId, dto);
    }
}
