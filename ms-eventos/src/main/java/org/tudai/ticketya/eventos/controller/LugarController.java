package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.service.LugarService;

@RestController
@RequestMapping("/api/lugares")
@RequiredArgsConstructor
public class LugarController {

    private final LugarService lugarService;

    @GetMapping
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public ResponseEntity<?> listarLugares(
            @RequestParam(required = false) String ciudad,
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable) {

        return ResponseEntity.ok(lugarService.listarLugares(ciudad, pageable));
    }

    @GetMapping("/{id}")
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public ResponseEntity<?> obtenerLugar(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long id) {
        return ResponseEntity.ok(lugarService.obtenerLugar(id));
    }

    @PostMapping
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearLugar(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @Valid @RequestBody LugarCreateDTO dto) {
        LugarResponseDTO lugarCreado = lugarService.crearLugar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(lugarCreado);
    }
}