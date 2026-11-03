package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.dto.lugar.LugarCreateDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarDetalleResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.service.LugarService;

import java.util.Set;

@RestController
@RequestMapping("/api/lugares")
@RequiredArgsConstructor
public class LugarController {

    private static final Set<String> ORDENABLES = Set.of("id", "nombre", "tipo", "ciudad");

    private final LugarService lugarService;

    @GetMapping
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public PagedModel<LugarDetalleResponseDTO> listarLugares(
            @RequestParam(required = false) String ciudad,
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable) {

        return new PagedModel<>(lugarService.listarLugares(ciudad, ordenable(pageable)));
    }

    @GetMapping("/{id}")
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public LugarDetalleResponseDTO obtenerLugar(@PathVariable Long id) {
        return lugarService.obtenerLugar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public LugarResponseDTO crearLugar(@Valid @RequestBody LugarCreateDTO dto) {
        return lugarService.crearLugar(dto);
    }

    private static Pageable ordenable(Pageable pageable) {
        pageable.getSort().forEach(orden -> {
            if (!ORDENABLES.contains(orden.getProperty())) {
                throw new BadRequestException(
                        "No se puede ordenar por '" + orden.getProperty() + "'. Campos válidos: " + ORDENABLES);
            }
        });
        return pageable;
    }
}
