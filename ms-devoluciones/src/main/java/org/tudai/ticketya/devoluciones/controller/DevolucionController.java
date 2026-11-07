package org.tudai.ticketya.devoluciones.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.contract.devoluciones.model.DevolucionResponse;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.devoluciones.model.SolicitudDevolucionRequest;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.service.DevolucionService;

import java.util.Set;

@RestController
@RequestMapping("/api/devoluciones")
@RequiredArgsConstructor
@Authorize("hasAnyRole('ADMINISTRADOR', 'CLIENTE')")
public class DevolucionController {

    private static final Set<String> ORDENABLES = Set.of(
            "id", "fecha", "estado", "ticketId", "clienteId", "creadoEl", "actualizadoEl"
    );

    private final DevolucionService devolucionService;
    private final ModelMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Authorize("hasRole('CLIENTE')")
    public DevolucionResponse solicitarDevolucion(@Valid @RequestBody SolicitudDevolucionRequest request) {
        Devolucion devolucion = devolucionService.solicitarDevolucion(request);
        return toResponseDTO(devolucion);
    }

    @GetMapping
    @Authorize("hasAnyRole('ADMINISTRADOR', 'CLIENTE')")
    public PagedModel<DevolucionResponse> listarDevoluciones(
            @RequestParam(required = false) EstadoDevolucion estado,
            @RequestParam(required = false) Long clienteId,
            @PageableDefault(sort = {"id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return toPage(devolucionService.listarDevoluciones(estado, clienteId, ordenable(pageable)));
    }

    @GetMapping("/{id}")
    @Authorize("hasAnyRole('ADMINISTRADOR', 'CLIENTE')")
    public DevolucionResponse obtenerDevolucion(@PathVariable Long id) {
        return toResponseDTO(devolucionService.findById(id));
    }

    @GetMapping("/ticket/{ticketId}")
    @Authorize("hasAnyRole('ADMINISTRADOR', 'CLIENTE')")
    public DevolucionResponse obtenerDevolucionPorTicket(@PathVariable Long ticketId) {
        return toResponseDTO(devolucionService.findByTicketId(ticketId));
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

    private DevolucionResponse toResponseDTO(Devolucion devolucion) {
        return mapper.map(devolucion, DevolucionResponse.class);
    }

    private PagedModel<DevolucionResponse> toPage(Page<Devolucion> page) {
        return new PagedModel<>(page.map(this::toResponseDTO));
    }
}
