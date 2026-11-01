package org.tudai.ticketya.eventos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.dto.request.CrearEventoRequest;
import org.tudai.ticketya.eventos.dto.request.CrearParticipanteRequest;
import org.tudai.ticketya.eventos.dto.request.ParticipanteRequest;
import org.tudai.ticketya.eventos.dto.response.EventoResponse;
import org.tudai.ticketya.eventos.dto.response.ParticipanteResponse;
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
    public EventoResponse crearEvento(CrearEventoRequest request, Long organizadorId) {
        Evento evento = Evento.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .tipo(request.getTipo())
                .organizadorId(organizadorId)
                .lugarId(request.getLugarId())
                .fechaHora(request.getFechaHora())
                .estado(EstadoEvento.BORRADOR)
                .build();

        if (request.getParticipantes() != null) {
            for (ParticipanteRequest participanteRequest : request.getParticipantes()) {
                evento.getParticipantes().add(resolverParticipante(participanteRequest));
            }
        }

        Evento guardado = eventoRepository.save(evento);
        return toResponse(guardado);
    }

    @Transactional
    public ParticipanteResponse crearParticipante(CrearParticipanteRequest request) {
        Participante participante = participanteRepository.save(Participante.builder()
                .nombre(request.getNombre())
                .tipo(request.getTipo())
                .build());
        return toParticipanteResponse(participante);
    }

    private Participante resolverParticipante(ParticipanteRequest request) {
        if (request.getId() != null) {
            return participanteRepository.findById(request.getId())
                    .orElseThrow(() -> new NotFoundException(
                            "No existe el participante con id " + request.getId()));
        }
        return participanteRepository.save(Participante.builder()
                .nombre(request.getNombre())
                .tipo(request.getTipo())
                .build());
    }

    private EventoResponse toResponse(Evento evento) {
        return EventoResponse.builder()
                .id(evento.getId())
                .nombre(evento.getNombre())
                .descripcion(evento.getDescripcion())
                .tipo(evento.getTipo())
                .estado(evento.getEstado())
                .organizadorId(evento.getOrganizadorId())
                .lugarId(evento.getLugarId())
                .fechaHora(evento.getFechaHora())
                .limiteEntradasPorCliente(null)
                .participantes(toParticipanteResponses(evento.getParticipantes()))
                .build();
    }

    private List<ParticipanteResponse> toParticipanteResponses(Set<Participante> participantes) {
        return participantes.stream()
                .sorted(Comparator.comparing(Participante::getId))
                .map(this::toParticipanteResponse)
                .toList();
    }

    private ParticipanteResponse toParticipanteResponse(Participante participante) {
        return ParticipanteResponse.builder()
                .id(participante.getId())
                .nombre(participante.getNombre())
                .tipo(participante.getTipo())
                .build();
    }
}
