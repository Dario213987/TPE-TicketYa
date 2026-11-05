package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.contract.eventos.model.EventoBorradorCreateRequest;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResumen;
import org.tudai.ticketya.contract.eventos.model.EventoUpdateRequest;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorRequest;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorResponse;
import org.tudai.ticketya.eventos.service.EventoService;

import java.util.Set;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private static final Set<String> ORDENABLES = Set.of("id", "nombre", "fecha", "tipo");

    private final EventoService eventoService;
    private final ModelMapper modelMapper;
    private final UserContext userContext;

    @GetMapping
    public PagedModel<EventoResumen> listarEventos(
            @PageableDefault(size = 20, sort = "fecha") Pageable pageable) {

        return new PagedModel<>(eventoService.listarPublicados(ordenable(pageable))
                .map(evento -> modelMapper.map(evento, EventoResumen.class)));
    }

    @PutMapping("/{eventoId}")
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    public EventoResponse actualizarEvento(
            @PathVariable Long eventoId,
            @Valid @RequestBody EventoUpdateRequest dto) {

        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;
        return eventoService.actualizarEvento(eventoId, dto, userContext.userId(), esAdmin);
    }

    @DeleteMapping("/{eventoId}")
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarEvento(@PathVariable Long eventoId) {
        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;
        eventoService.eliminarEvento(eventoId, userContext.userId(), esAdmin);
    }

    @PutMapping("/{eventoId}/sectores/{sectorId}/precio")
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    public PrecioEventoSectorResponse definirPrecioSector(
            @PathVariable Long eventoId,
            @PathVariable Long sectorId,
            @Valid @RequestBody PrecioEventoSectorRequest dto) {

        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;
        return eventoService.definirPrecioSector(eventoId, sectorId, dto.getPrecio(),
                userContext.userId(), esAdmin);
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

    @PostMapping
    @Authorize("hasRole('ORGANIZADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse crear(@Valid @RequestBody EventoBorradorCreateRequest request) {
        return eventoService.crearEventoBorrador(request, userContext.userId());
    }

}