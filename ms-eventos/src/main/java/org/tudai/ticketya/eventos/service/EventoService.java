package org.tudai.ticketya.eventos.service; // (Ajustá el package si es distinto)

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.dto.evento.EventoBorradorCreateDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteRequestDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteResponseDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;

    private final LugarService lugarService;
    private final ParticipanteService participanteService;
    private final ModelMapper modelMapper;

    @Transactional
    public EventoResponseDTO crearEventoBorrador(EventoBorradorCreateDTO request, Long organizadorId) {

        Lugar lugar = lugarService.obtenerPorId(request.getLugarId());

        LocalDate fecha = request.getFecha();

        Evento evento = Evento.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .tipo(request.getTipo())
                .organizadorId(organizadorId)
                .lugar(lugar)
                .fecha(fecha)
                .estado(EstadoEvento.BORRADOR)
                .build();

        if (request.getParticipantes() != null && !request.getParticipantes().isEmpty()) {
            Set<Participante> participantesResueltos = request.getParticipantes().stream()
                    .map(this::resolverParticipante)
                    .collect(Collectors.toSet());
            evento.getArtistas().addAll(participantesResueltos);
        }

        Evento guardado = eventoRepository.save(evento);
        return toResponse(guardado);
    }

    private Participante resolverParticipante(ParticipanteRequestDTO request) {

        if (request.getId() != null) {
            return participanteService.obtenerPorId(request.getId());
        }

        return participanteService.crearParticipante(request);
    }

    private EventoResponseDTO toResponse(Evento evento) {
        return EventoResponseDTO.builder()
                .id(evento.getId())
                .nombre(evento.getNombre())
                .descripcion(evento.getDescripcion())
                .tipo(evento.getTipo())
                .estado(evento.getEstado())
                .organizadorId(evento.getOrganizadorId())
                .lugar(mapearLugar(evento.getLugar()))
                .fecha(evento.getFecha())
                .participantes(toParticipanteResponses(evento.getArtistas()))
                .build();
    }

    public LugarResponseDTO mapearLugar(Lugar lugar) {
        return modelMapper.map(lugar, LugarResponseDTO.class);
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