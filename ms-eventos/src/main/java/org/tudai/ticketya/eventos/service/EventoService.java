package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.dto.evento.EventoBorradorCreateDTO;
import org.tudai.ticketya.eventos.dto.evento.EventoResponseDTO;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteRequestDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteResponseDTO;
import org.tudai.ticketya.eventos.domain.Sector;
import org.tudai.ticketya.eventos.dto.evento.EventoUpdateDTO;
import org.tudai.ticketya.eventos.dto.evento.PrecioEventoSectorResponseDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final LugarRepository lugarRepository;
    private final ModelMapper modelMapper;
    private final LugarService lugarService;
    private final ParticipanteService participanteService;


    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(Pageable pageable) {
        return eventoRepository.findByEstadoIn(EstadoEvento.VISIBLES, pageable);
    }

    @Transactional
    public EventoResponseDTO actualizarEvento(Long eventoId, EventoUpdateDTO dto, Long organizadorId,
                                              boolean esAdmin) {
        Evento evento = buscarEvento(eventoId);
        verificarPropiedad(evento, organizadorId, esAdmin, "modificar");

        validarCamposEstructurales(evento, dto);
        aplicarCambios(evento, dto);

        return toResponse(eventoRepository.save(evento));
    }

    @Transactional
    public void eliminarEvento(Long eventoId, Long organizadorId, boolean esAdmin) {
        Evento evento = buscarEvento(eventoId);
        verificarPropiedad(evento, organizadorId, esAdmin, "eliminar");

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden eliminar eventos en estado BORRADOR");
        }

        eventoRepository.delete(evento);
    }

    private Evento buscarEvento(Long eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento no encontrado con id: " + eventoId));
    }

    private void verificarPropiedad(Evento evento, Long organizadorId, boolean esAdmin, String accion) {
        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés " + accion + " un evento que no te pertenece");
        }
    }

    private void validarCamposEstructurales(Evento evento, EventoUpdateDTO dto) {
        boolean esBorrador = evento.getEstado() == EstadoEvento.BORRADOR;

        if (dto.getFecha() != null && !Objects.equals(dto.getFecha(), evento.getFecha())) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se puede modificar la fecha de un evento ya publicado. Cancelalo o reprogramalo.");
            }
            if (!dto.getFecha().isAfter(LocalDate.now())) {
                throw new BadRequestException("La fecha del evento debe ser posterior a hoy");
            }
        }

        if (dto.getLugarId() != null && (evento.getLugar() == null
                || !Objects.equals(dto.getLugarId(), evento.getLugar().getId()))) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se puede modificar el lugar de un evento ya publicado. Cancelalo o reprogramalo.");
            }
        }

        if (dto.getSectores() != null && !Objects.equals(dto.getSectores(), sectoresDelEvento(evento))) {
            if (!esBorrador) {
                throw new ConflictException(
                        "No se pueden modificar los sectores de un evento ya publicado. Cancelalo o reprogramalo.");
            }
            throw new BadRequestException(
                    "Los sectores de un evento se administran desde el lugar asociado");
        }
    }

    private void aplicarCambios(Evento evento, EventoUpdateDTO dto) {
        if (dto.getNombre() != null) {
            if (dto.getNombre().isBlank()) {
                throw new BadRequestException("El nombre del evento no puede estar vacío");
            }
            evento.setNombre(dto.getNombre().trim());
        }

        if (dto.getDescripcion() != null) {
            if (dto.getDescripcion().isBlank()) {
                throw new BadRequestException("La descripción del evento no puede estar vacía");
            }
            evento.setDescripcion(dto.getDescripcion().trim());
        }

        if (dto.getTipo() != null) {
            if (dto.getTipo().isBlank()) {
                throw new BadRequestException("El tipo del evento no puede estar vacío");
            }
            evento.setTipo(dto.getTipo().trim());
        }

        if (dto.getFecha() != null) {
            evento.setFecha(dto.getFecha());
        }

        if (dto.getLugarId() != null && (evento.getLugar() == null
                || !Objects.equals(dto.getLugarId(), evento.getLugar().getId()))) {
            Lugar lugar = lugarRepository.findById(dto.getLugarId())
                    .orElseThrow(() -> new NotFoundException(
                            "Lugar no encontrado con id: " + dto.getLugarId()));
            evento.setLugar(lugar);
        }
    }

    private Set<Long> sectoresDelEvento(Evento evento) {
        Lugar lugar = evento.getLugar();
        if (lugar == null || lugar.getSectores() == null) {
            return Set.of();
        }
        return lugar.getSectores().stream()
                .map(Sector::getId)
                .collect(Collectors.toSet());
    }

    private EventoResponseDTO toResponse(Evento evento) {
        return modelMapper.map(evento, EventoResponseDTO.class);
    }

    @Transactional
    public PrecioEventoSectorResponseDTO definirPrecioSector(Long eventoId, Long sectorId, Double precio,
                                                             Long organizadorId, boolean esAdmin) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento no encontrado con id: " + eventoId));

        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), organizadorId)) {
            throw new ForbiddenException("No podés definir precios en un evento que no te pertenece");
        }

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new ConflictException("Solo se pueden definir precios en eventos en estado BORRADOR");
        }

        if (precio == null || precio <= 0) {
            throw new BadRequestException("El precio debe ser mayor a 0");
        }

        if (!sectorPerteneceAlLugar(evento, sectorId)) {
            throw new BadRequestException("El sector " + sectorId + " no pertenece al lugar del evento");
        }

        evento.definirPrecioSector(sectorId, precio);
        eventoRepository.save(evento);

        return PrecioEventoSectorResponseDTO.builder()
                .eventoId(eventoId)
                .sectorId(sectorId)
                .precio(precio)
                .build();
    }

    private boolean sectorPerteneceAlLugar(Evento evento, Long sectorId) {
        Lugar lugar = evento.getLugar();
        if (lugar == null || lugar.getSectores() == null) {
            return false;
        }
        return lugar.getSectores().stream()
                .anyMatch(sector -> Objects.equals(sector.getId(), sectorId));
    }

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
            evento.getParticipantes().addAll(participantesResueltos);
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

    public LugarResponseDTO mapearLugar(Lugar lugar) {
        return modelMapper.map(lugar, LugarResponseDTO.class);
    }

    private ParticipanteResponseDTO toParticipanteResponseDTO(Participante participante) {
        return ParticipanteResponseDTO.builder()
                .id(participante.getId())
                .nombre(participante.getNombre())
                .build();
    }
}

