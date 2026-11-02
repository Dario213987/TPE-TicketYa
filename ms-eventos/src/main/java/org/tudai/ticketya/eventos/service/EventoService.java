package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.dto.evento.EventoResumenDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventoService {

    private static final Set<EstadoEvento> ESTADOS_VISIBLES = Set.of(EstadoEvento.PUBLICADO, EstadoEvento.AGOTADO);

    private final EventoRepository eventoRepository;
    private final ModelMapper modelMapper;

    @Transactional(readOnly = true)
    public Page<EventoResumenDTO> listarPublicados(Pageable pageable) {
        return eventoRepository.findByEstadoIn(ESTADOS_VISIBLES, pageable)
                .map(evento -> modelMapper.map(evento, EventoResumenDTO.class));
    }
}
