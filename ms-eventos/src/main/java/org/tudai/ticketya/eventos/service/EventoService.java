package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.*;
import org.tudai.ticketya.eventos.dto.evento.EventoBorradorCreateDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteRequestDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteResponseDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;
import org.tudai.ticketya.eventos.repository.ParticipanteRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final LugarRepository lugarRepository;
    private final ParticipanteRepository participanteRepository;

    @Transactional
    public EventoResponseDTO crearEventoBorrador(EventoBorradorCreateDTO request, Long organizadorId) {

        if (request.getFechaHora().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("La fecha y hora del evento deben ser en el futuro");
        }

        Lugar lugar = lugarRepository.findById(request.getLugarId())
                .orElseThrow(() -> new NotFoundException("LUGAR_NO_ENCONTRADO"));

        // SEPARO LA FECHA DE LA HORA PORQUE LA ENTIDAD NO TIENE HORA COMO ATRIBUTO
        LocalDate fecha = request.getFechaHora().toLocalDate();
        LocalTime hora = request.getFechaHora().toLocalTime();

        Evento evento = Evento.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .tipo(request.getTipo())
                .organizadorId(organizadorId)
                .lugar(lugar)
                .fecha(fecha)
                .estado(EstadoEvento.BORRADOR)
                .build();

        if (request.getParticipantes() != null) {
            for (ParticipanteRequestDTO pReq : request.getParticipantes()) {
                evento.getArtistas().add(resolverParticipante(pReq));
            }
        }

        Evento guardado = eventoRepository.save(evento);
        return toResponse(guardado);
    }

    private Participante resolverParticipante(ParticipanteRequestDTO request) {
        if (request.getId() != null) {
            return participanteRepository.findById(request.getId())
                    .orElseThrow(() -> new NotFoundException("No existe el participante con id " + request.getId()));
        }

        if (request.getNombre() != null) {
            return participanteRepository.save(Participante.builder()
                    .nombre(request.getNombre())
                    .build());
        }

        throw new BadRequestException("El participante debe tener un id existente o un nombre nuevo");
    }

    private EventoResponseDTO toResponse(Evento evento) {
        return EventoResponseDTO.builder()
                .id(evento.getId())
                .nombre(evento.getNombre())
                .descripcion(evento.getDescripcion())
                .tipo(evento.getTipo())
                .estado(evento.getEstado().name())
                .organizadorId(evento.getOrganizadorId())
                .lugarId(evento.getLugar().getId())
                .fecha(evento.getFecha())
                .participantes(toParticipanteResponses(evento.getArtistas()))
                .build();
    }

    private List<ParticipanteResponseDTO> toParticipanteResponses(Set<Participante> participantes) {
        return participantes.stream()
                .sorted(Comparator.comparing(Participante::getId))
                .map(this::toParticipanteResponseDTO)
                .toList();
    }

    private ParticipanteResponseDTO toParticipanteResponseDTO(Participante participante) {
        return ParticipanteResponseDTO.builder()
                .id(participante.getId())
                .nombre(participante.getNombre())
                .build();
    }
}