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
import org.tudai.ticketya.contract.eventos.model.EventoEstadoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResponse;
import org.tudai.ticketya.contract.eventos.model.EventoResumen;
import org.tudai.ticketya.contract.eventos.model.EventoUpdateRequest;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorRequest;
import org.tudai.ticketya.contract.eventos.model.PrecioEventoSectorResponse;
import org.tudai.ticketya.eventos.dto.evento.EventoFiltroRequestDTO;
import org.tudai.ticketya.eventos.service.EventoService;

import java.util.Set;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private static final Set<String> ORDENABLES = Set.of("id", "nombre", "fecha", "tipo");
    private static final int MAX_SIZE = 50;

    private final EventoService eventoService;
    private final ModelMapper modelMapper;
    private final UserContext userContext;

    @GetMapping
    public PagedModel<EventoResumen> listarEventos(
            //Vincula los parámetros del request con los campos de EventoFiltroRequestDTO para filtrado
            @ModelAttribute EventoFiltroRequestDTO filtro,
            @PageableDefault(size = 20, sort = "fecha") Pageable pageable) {

        Pageable pageableValidado = ordenable(pageable);
        validarSize(pageableValidado);
        return new PagedModel<>(eventoService.listarPublicados(filtro, pageableValidado)
                .map(evento -> modelMapper.map(evento, EventoResumen.class)));
    }

    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    @PutMapping("/{id}/publicar")
    public EventoEstadoResponse publicarEvento(@PathVariable Long id) {
        boolean esAdmin = userContext.role() == Role.ADMINISTRADOR;
        return eventoService.publicarEvento(id, organizadorIdActual(), esAdmin);
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

    private Long organizadorIdActual() {
        return userContext.userId();
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

    private static void validarSize(Pageable pageable) {
        if (pageable.getPageSize() > MAX_SIZE) {
            throw new BadRequestException("El tamaño de página no puede superar " + MAX_SIZE);
        }
    }

    @PostMapping
    @Authorize("hasRole('ORGANIZADOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public EventoResponse crear(@Valid @RequestBody EventoBorradorCreateRequest request) {
        return eventoService.crearEventoBorrador(request, userContext.userId());
    }

    @Authorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventoEstadoResponse cancelarEvento(@PathVariable Long id) {
        return eventoService.cancelarEvento(id);
    }

    // Endpoints internos: los consume ms-tickets servicio-a-servicio.
    // No llevan @Authorize porque no hay usuario en esas llamadas; el gateway
    // bloquea /api/eventos/internal/** para que no queden expuestos externamente.
    @PutMapping("/internal/{id}/estado/agotado")
    public EventoEstadoResponse marcarAgotado(@PathVariable Long id){
        return eventoService.marcarAgotado(id);
    }

    @PutMapping("/internal/{id}/estado/publicar")
    public EventoEstadoResponse marcarPublicado(@PathVariable Long id){
        return eventoService.marcarPublicado(id);
    }

    @PutMapping("/internal/finalizar-vencidos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizarEventosVencidos() {
        eventoService.finalizarEventosVencidos();
    }

    @Authorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}/suspender")
    public EventoEstadoResponse suspenderEvento(@PathVariable Long id) {
        return eventoService.suspenderEvento(id);
    }

    @Authorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/{id}/reactivar")
    public EventoEstadoResponse reactivarEvento(@PathVariable Long id) {
        return eventoService.reactivarEvento(id);
    }
}
