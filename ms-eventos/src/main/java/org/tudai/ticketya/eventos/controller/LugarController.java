package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarDetalleResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarPageResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.service.LugarService;

@RestController
@RequestMapping("/api/lugares")
@RequiredArgsConstructor
public class LugarController {

    private final LugarService lugarService;

    @GetMapping
    public ResponseEntity<?> listarLugares(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @RequestParam(required = false) String ciudad,
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable) {
        if (!esOrganizador(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso denegado: Se requiere rol de Organizador");
        }
        return ResponseEntity.ok(lugarService.listarLugares(ciudad, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerLugar(
            @RequestHeader(value = "Rol del Usuario", required = false) String userRole,
            @PathVariable Long id) {
        if (!esOrganizador(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso denegado: Se requiere rol de Organizador");
        }
        return ResponseEntity.ok(lugarService.obtenerLugar(id));
    }

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

    private boolean esOrganizador(String userRole) {
        return userRole != null
                && (userRole.equalsIgnoreCase("ORGANIZADOR"));
    }
}