package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteRequestDTO;
import org.tudai.ticketya.eventos.repository.ParticipanteRepository;

@Service
@RequiredArgsConstructor
public class ParticipanteService {

    private final ParticipanteRepository participanteRepository;

    @Transactional(readOnly = true)
    public Participante obtenerPorId(Long id) {
        return participanteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el participante con id " + id));
    }


    public Participante crearParticipante(ParticipanteRequestDTO request) {

        return Participante.builder()
                .nombre(request.getNombre())
                .build();
    }
}