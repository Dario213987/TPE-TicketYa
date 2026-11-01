package org.tudai.ticketya.eventos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.dto.evento.CrearEventoRequestDTO;
import org.tudai.ticketya.eventos.dto.participante.CrearParticipanteRequestDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteRequestDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteResponseDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.ParticipanteRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class EventoService {

    private final EventoRepository eventoRepository;
    private final ParticipanteRepository participanteRepository;

    public EventoService(EventoRepository eventoRepository,
                         ParticipanteRepository participanteRepository) {
        this.eventoRepository = eventoRepository;
        this.participanteRepository = participanteRepository;
    }

    @Transactional
    public EventoResponseDTO crearEvento(CrearEventoRequestDTO request, Long organizadorId) {
        Lugar lugar = new Lugar();
        lugar.setId(request.getLugarId());

        Evento evento = Evento.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .tipo(request.getTipo())
                .organizadorId(organizadorId)
                .lugar(lugar)
                .fecha(request.getFecha())
                .hora(request.getHora())
                .limiteEntradasPorCliente(request.getLimiteEntradasPorCliente())
                .estado(EstadoEvento.BORRADOR)
                .build();

        if (request.getParticipantes() != null) {
            for (ParticipanteRequestDTO participanteRequest : request.getParticipantes()) {
                evento.getParticipantes().add(resolverParticipante(participanteRequest));
            }
        }

        Evento guardado = eventoRepository.save(evento);
        return toResponse(guardado);
    }

    @Transactional
    public ParticipanteResponseDTO crearParticipante(CrearParticipanteRequestDTO request) {
        Participante participante = participanteRepository.save(Participante.builder()
                .nombre(request.getNombre())
                .build());
        return toParticipanteResponse(participante);
    }

    private Participante resolverParticipante(ParticipanteRequestDTO request) {
        if (request.getId() != null) {
            return participanteRepository.findById(request.getId())
                    .orElseThrow(() -> new NotFoundException(
                            "No existe el participante con id " + request.getId()));
        }
        return participanteRepository.save(Participante.builder()
                .nombre(request.getNombre())
                .build());
    }

    private EventoResponseDTO toResponse(Evento evento) {
        return EventoResponseDTO.builder()
                .nombre(evento.getNombre())
                .descripcion(evento.getDescripcion())
                .tipo(evento.getTipo())
                .estado(evento.getEstado())
                .organizadorId(evento.getOrganizadorId())
                .nombreLugar(evento.getLugar().getNombre())
                .fecha(evento.getFecha())
                .hora(evento.getHora())
                .limiteEntradasPorCliente(evento.getLimiteEntradasPorCliente())
                .participantes(toParticipanteResponses(evento.getParticipantes()))
                .build();
    }

    private List<ParticipanteResponseDTO> toParticipanteResponses(Set<Participante> participantes) {
        return participantes.stream()
                .sorted(Comparator.comparing(Participante::getId))
                .map(this::toParticipanteResponse)
                .toList();
    }

    private ParticipanteResponseDTO toParticipanteResponse(Participante participante) {
        return ParticipanteResponseDTO.builder()
                .id(participante.getId())
                .nombre(participante.getNombre())
                .build();
    }
}
