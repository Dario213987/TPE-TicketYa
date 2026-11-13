package org.tudai.ticketya.devoluciones.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.contract.devoluciones.model.DevolucionResponse;
import org.tudai.ticketya.contract.devoluciones.model.SolicitudDevolucionRequest;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.service.DevolucionService;

@RestController
@RequestMapping("/api/devoluciones")
@RequiredArgsConstructor
@Authorize("hasRole('CLIENTE')")
public class DevolucionController {

    private final DevolucionService devolucionService;
    private final ModelMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DevolucionResponse solicitarDevolucion(@Valid @RequestBody SolicitudDevolucionRequest request) {
        Devolucion devolucion = devolucionService.solicitarDevolucion(request);
        return toResponseDTO(devolucion);
    }

    private DevolucionResponse toResponseDTO(Devolucion devolucion) {
        return mapper.map(devolucion, DevolucionResponse.class);
    }
}
