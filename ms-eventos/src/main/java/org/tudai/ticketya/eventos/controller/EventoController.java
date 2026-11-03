package org.tudai.ticketya.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.eventos.dto.evento.EventoResumenDTO;
import org.tudai.ticketya.eventos.service.EventoService;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final ModelMapper modelMapper;

    @GetMapping
    public PagedModel<EventoResumenDTO> listarPublicados(@PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(eventoService.listarPublicados(pageable)
                .map(evento -> modelMapper.map(evento, EventoResumenDTO.class)));
    }
}
