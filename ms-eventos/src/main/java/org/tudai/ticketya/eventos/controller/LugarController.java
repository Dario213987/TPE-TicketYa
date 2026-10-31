package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.service.LugarService;

@RestController
@RequestMapping("/api/lugares")
@RequiredArgsConstructor
public class LugarController {

    private final LugarService lugarService;

    @PostMapping
    public ResponseEntity<?> crearLugar(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @Valid @RequestBody LugarCreateDTO dto) {
        if (userRole == null || !userRole.equalsIgnoreCase("ADMINISTRADOR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso denegado: Se requiere rol de Administrador");
        }
        LugarResponseDTO lugarCreado = lugarService.crearLugar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(lugarCreado);
    }
}