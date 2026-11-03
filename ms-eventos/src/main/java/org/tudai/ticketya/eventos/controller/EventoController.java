package org.tudai.ticketya.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.dto.evento.EventoResumenDTO;
import org.tudai.ticketya.eventos.service.EventoService;

import java.util.Set;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private static final Set<String> ORDENABLES = Set.of("id", "nombre", "fecha", "tipo");

    private final EventoService eventoService;
    private final ModelMapper modelMapper;

    @GetMapping
    public PagedModel<EventoResumenDTO> listarEventos(
            @PageableDefault(size = 20, sort = "fecha") Pageable pageable) {

        return new PagedModel<>(eventoService.listarPublicados(ordenable(pageable))
                .map(evento -> modelMapper.map(evento, EventoResumenDTO.class)));
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
