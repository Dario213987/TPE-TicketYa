package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarDetalleResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.service.LugarService;

@RestController
@RequestMapping("/api/lugares")
@RequiredArgsConstructor
public class LugarController {

    private final LugarService lugarService;

    @GetMapping
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public Page<LugarDetalleResponseDTO> listarLugares(
            @RequestParam(required = false) String ciudad,
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable) {

        return lugarService.listarLugares(ciudad, pageable);
    }

    @GetMapping("/{id}")
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public LugarDetalleResponseDTO obtenerLugar(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long id) {
        return lugarService.obtenerLugar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public LugarResponseDTO crearLugar(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @Valid @RequestBody LugarCreateDTO dto) {
        return lugarService.crearLugar(dto);
    }
}