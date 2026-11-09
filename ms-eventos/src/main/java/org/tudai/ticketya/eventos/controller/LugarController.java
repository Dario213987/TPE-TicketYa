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
import org.tudai.ticketya.contract.eventos.model.LugarCreateRequest;
import org.tudai.ticketya.contract.eventos.model.LugarDetalleResponse;
import org.tudai.ticketya.contract.eventos.model.LugarResponse;
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
    public PagedModel<LugarDetalleResponse> listarLugares(
            @RequestParam(required = false) String ciudad,
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable) {

        return new PagedModel<>(lugarService.listarLugares(ciudad, ordenable(pageable)));
    }

    @GetMapping("/{id}")
    @Authorize("hasAnyRole('ORGANIZADOR')")
    public LugarDetalleResponse obtenerLugar(@PathVariable Long id) {
        return lugarService.obtenerLugar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasAnyRole('ADMINISTRADOR')")
    public LugarResponse crearLugar(@Valid @RequestBody LugarCreateRequest dto) {
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
