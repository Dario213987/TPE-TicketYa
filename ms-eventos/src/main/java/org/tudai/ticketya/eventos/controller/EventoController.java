package org.tudai.ticketya.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.dto.evento.EventoBorradorCreateDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResumenDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoUpdateDTO;
import org.tudai.ticketya.eventos.dto.evento.PrecioEventoSectorRequestDTO;
import org.tudai.ticketya.eventos.dto.evento.PrecioEventoSectorResponseDTO;
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
    public PagedModel<EventoResumenDTO> listarEventos(
            @PageableDefault(size = 20, sort = "fecha") Pageable pageable) {

        return new PagedModel<>(eventoService.listarPublicados(ordenable(pageable))
                .map(evento -> modelMapper.map(evento, EventoResumenDTO.class)));
    }

    @PutMapping("/{eventoId}")
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    public EventoResponseDTO actualizarEvento(
            @PathVariable Long eventoId,
            @Valid @RequestBody EventoUpdateDTO dto) {

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
    public PrecioEventoSectorResponseDTO definirPrecioSector(
            @PathVariable Long eventoId,
            @PathVariable Long sectorId,
            @Valid @RequestBody PrecioEventoSectorRequestDTO dto) {

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
    public EventoResponseDTO crear(@Valid @RequestBody EventoBorradorCreateDTO request) {
        return eventoService.crearEventoBorrador(request, organizadorIdActual());
    }

    private Long organizadorIdActual() {

        try {
            return userContext.userId();
        } catch (NumberFormatException e) {
            throw new BadRequestException("El header X-User-Id debe ser un número");
        }
    }

}